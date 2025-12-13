package org.opensha.sha.calc.IM_EventSet;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ListIterator;

import static org.junit.Assert.*;

import org.jfree.chart.ChartUtils;
import org.jfree.data.Range;
import org.junit.Test;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.siteData.OrderedSiteDataProviderList;
import org.opensha.commons.data.siteData.SiteDataValue;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.IM_EventSet.outputImpl.HAZ01Writer;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;

/**
 * Tests for consistent outputs between direct hazard curve calculation and 
 * HAZ01A-based calculation.
 * <p>
 * Expected tolerance: differences up to 5% are acceptable due to:
 * <ul>
 * <li>HAZ01A format limited precision for mean/stddev storage</li>
 * <li>Source filtering (only sources within ~200km are included)</li>
 * <li>Numerical precision differences in calculation order</li>
 * </ul>
 * </p>
 */
public class HazardCurveConsistencyTest implements IMEventSetCalcAPI {
	
	/**
	 * Maximum acceptable percent difference between direct calculation and HAZ01A-based calculation.
	 * Set to 5% to account for HAZ01A format limitations and numerical precision.
	 */
	public static final double TOL_PERCENT = 5.0;
	
	File outputDir;
	ERF erf;
	ScalarIMR imr;
	Site site;
	ArrayList<Site> sites;
	ArrayList<ParameterList> sitesData;
	HeadlessGraphPanel gp;
	
	String imt = "SA 1.0";
//    String imt = "PGA";

	public HazardCurveConsistencyTest() {
		outputDir = getTempDir();
		erf = new Frankel96_AdjustableEqkRupForecast();
		erf.getAdjustableParameterList()
				.getParameter(Frankel96_AdjustableEqkRupForecast.BACK_SEIS_NAME)
				.setValue(Frankel96_AdjustableEqkRupForecast.BACK_SEIS_EXCLUDE);
		erf.updateForecast();
		imr = new CB_2008_AttenRel(null);
		imr.setParamDefaults();
		IM_EventSetOutputWriter.setIMTFromString(imt, imr);
		site = new Site(new Location(34d, -118d), "Los Angeles");

		ListIterator<Parameter<?>> it = imr.getSiteParamsIterator();
		while (it.hasNext()) {
			Parameter<?> param = it.next();
			site.addParameter(param);
		}
		sites = new ArrayList<Site>();
		sites.add(site);
		sitesData = new ArrayList<ParameterList>();
		sitesData.add(new ParameterList());
		
		gp = new HeadlessGraphPanel();
	}

    private static File getTempDir() {
        File tempDir;
        try {
            tempDir = File.createTempFile("asdf", "fdsa").getParentFile();
        } catch (IOException e) {
            e.printStackTrace();
            tempDir = new File("/tmp");
        }
        tempDir = new File(tempDir.getAbsolutePath() + File.separator + "imEventSetTest");
        if (!tempDir.exists())
            tempDir.mkdir();
        return tempDir;
    }
	
	private void runHAZ01A() throws IOException {
		HAZ01Writer writer = new HAZ01Writer(this);
		
//		ArrayList<ScalarIntensityMeasureRelationshipAPI> attenRels = new
		System.out.println("Writing HAZ01A files.");
		writer.writeFiles(erf, imr, imt);
		System.out.println("done.");
	}
	
	@Test
	public void testHazardCurve() throws IOException {
		HazardCurveCalculator calc = new HazardCurveCalculator();
		
		ArbitrarilyDiscretizedFunc realCurve = IMT_Info.getUSGS_SA_Function();
		ArbitrarilyDiscretizedFunc rLogHazFunction = getLogFunction(realCurve);
		System.out.println("IMR Params: " + imr.getAllParamMetadata());
		System.out.println("Calculating regular curve");
		calc.getHazardCurve(rLogHazFunction, site, imr, erf);
		realCurve = unLogFunction(realCurve, rLogHazFunction);
		
		runHAZ01A();
		String fileName = outputDir.getAbsolutePath() + File.separator + HAZ01Writer.HAZ01A_FILE_NAME;
		ScalarIMR hIMR = new HAZ01A_FakeAttenRel(fileName);
		ERF hERF = new HAZ01A_FakeERF(erf, fileName); // Pass filename so it knows which sources to include
		hERF.updateForecast();
		
		ArbitrarilyDiscretizedFunc hCurve = IMT_Info.getUSGS_SA_Function();
		System.out.println("Calculating IM based curve");
		ArbitrarilyDiscretizedFunc hLogHazFunction = getLogFunction(hCurve);
        System.out.println("hLogHazFunction: " + hLogHazFunction.getName() + " site:" + site + " hIMR:" + hIMR + " hERF:" + hERF);
		calc.getHazardCurve(hLogHazFunction, site, hIMR, hERF);
		hCurve = unLogFunction(hCurve, hLogHazFunction);
//		ArbitrarilyDiscretizedFunc realCurve =
//			ArbitrarilyDiscretizedFunc.loadFuncFromSimpleFile("/tmp/imEventSetTest/curve.txt");
		
		ArrayList<ArbitrarilyDiscretizedFunc> curves = new ArrayList<ArbitrarilyDiscretizedFunc>();
		curves.add(realCurve);
		curves.add(hCurve);
		
		boolean xLog = false;
		boolean yLog = false;
		boolean customAxis = true;
		Range xRange = new Range(0, 1);
		Range yRange = new Range(0, 1);
		this.gp.drawGraphPanel(imt, "", curves, null, xLog, yLog, "Curves", xRange, yRange);
		this.gp.setVisible(true);
		
		this.gp.validate();
		this.gp.repaint();
		
		ChartUtils.saveChartAsPNG(new File(outputDir.getAbsolutePath() + File.separator + "curves.png"),
				gp.getChartPanel().getChart(), 800, 600);
		
		double maxDiff = 0;
		double maxPDiff = 0;
		
		for (int i=0; i<hCurve.size(); i++) {
			Point2D hPt = hCurve.get(i);
			Point2D rPt = realCurve.get(i);
			
			assertEquals(hPt.getX(), rPt.getX(), 0);
			
			if (hPt.getX() >= 10.)
				continue;
			
			System.out.println("Comparing point: " + i);
			
			System.out.println("\"Real\" point:\t" + rPt.getX() + ", " + rPt.getY());
			System.out.println("HAZ01A point:\t" + hPt.getX() + ", " + hPt.getY());
			
			if (hPt.getY() == 0 && rPt.getY() == 0)
				continue;
			
			double absDiff = Math.abs(hPt.getY() - rPt.getY());
			if (absDiff > maxDiff)
				maxDiff = absDiff;
			double absPDiff = absDiff / rPt.getY() * 100d;
			if (absPDiff > maxPDiff)
				maxPDiff = absPDiff;
			
			System.out.println("absDiff: " + absDiff + ", abs % diff: " + absPDiff);
			
			boolean success = absPDiff < TOL_PERCENT;
			if (!success) {
				System.out.println("FAIL!");
			}
			assertTrue("Point " + i + " exceeds tolerance: " + absPDiff + "% > " + TOL_PERCENT + "%", success);
		}
		
		System.out.println("Max Diff: " + maxDiff);
		System.out.println("Max Diff %: " + maxPDiff);
	}
	
	private static ArbitrarilyDiscretizedFunc getLogFunction(DiscretizedFunc arb) {
		ArbitrarilyDiscretizedFunc new_func = new ArbitrarilyDiscretizedFunc();
		for (int i = 0; i < arb.size(); ++i)
			new_func.set(Math.log(arb.getX(i)), 1);
		return new_func;
	}
	
	private static ArbitrarilyDiscretizedFunc unLogFunction(
			DiscretizedFunc oldHazFunc, DiscretizedFunc logHazFunction) {
		int numPoints = oldHazFunc.size();
		ArbitrarilyDiscretizedFunc hazFunc = new ArbitrarilyDiscretizedFunc();
		for (int i = 0; i < numPoints; ++i) {
			hazFunc.set(oldHazFunc.getX(i), logHazFunction.getY(i));
		}
		return hazFunc;
	}

	public int getNumSites() {
		return sites.size();
	}

	public File getOutputDir() {
		return outputDir;
	}

	public OrderedSiteDataProviderList getSiteDataProviders() {
		return null;
	}

	public Location getSiteLocation(int i) {
		return site.getLocation();
	}

	public ArrayList<Site> getSites() {
		return sites;
	}

	public ArrayList<ParameterList> getSitesData() {
		return sitesData;
	}

	public ParameterList getUserSiteData(int i) {
		return sitesData.get(i);
	}

}
