/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.sha.calc.IM_EventSet.v03.test;

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
import org.opensha.commons.gui.plot.GraphPanel;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.IM_EventSet.v03.IM_EventSetCalc_v3_0_API;
import org.opensha.sha.calc.IM_EventSet.v03.IM_EventSetOutputWriter;
import org.opensha.sha.calc.IM_EventSet.v03.outputImpl.HAZ01Writer;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;

public class IM_EventSetHazardCurveTest implements IM_EventSetCalc_v3_0_API {
	
	public static final double TOL_PERCENT = 0.05;
	
	File outputDir;
	ERF erf;
	ScalarIMR imr;
	Site site;
	ArrayList<Site> sites;
	ArrayList<ArrayList<SiteDataValue<?>>> sitesData;
	HeadlessGraphPanel gp;
	
	String imt = "SA 1.0";

	public IM_EventSetHazardCurveTest() {
		
		outputDir = IM_EventSetTest.getTempDir();
		erf = new Frankel96_AdjustableEqkRupForecast();
		erf.getAdjustableParameterList()
				.getParameter(Frankel96_AdjustableEqkRupForecast.BACK_SEIS_NAME)
				.setValue(Frankel96_AdjustableEqkRupForecast.BACK_SEIS_EXCLUDE);
		erf.updateForecast();
		imr = new CB_2008_AttenRel(null);
		imr.setParamDefaults();
		IM_EventSetOutputWriter.setIMTFromString(imt, imr);
		site = new Site(new Location(34d, -118d));
		
		ListIterator<Parameter<?>> it = imr.getSiteParamsIterator();
		while (it.hasNext()) {
			Parameter<?> param = it.next();
			site.addParameter(param);
		}
		sites = new ArrayList<Site>();
		sites.add(site);
		sitesData = new ArrayList<ArrayList<SiteDataValue<?>>>();
		sitesData.add(new ArrayList<SiteDataValue<?>>());
		
		gp = new HeadlessGraphPanel();
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
		
		ArbitrarilyDiscretizedFunc realCurve = IMT_Info.getUSGS_PGA_Function();
		ArbitrarilyDiscretizedFunc rLogHazFunction = getLogFunction(realCurve);
		System.out.println("IMR Params: " + imr.getAllParamMetadata());
		System.out.println("Calculating regular curve");
		calc.getHazardCurve(rLogHazFunction, site, imr, erf);
		realCurve = unLogFunction(realCurve, rLogHazFunction);
		
		runHAZ01A();
		String fileName = outputDir.getAbsolutePath() + File.separator + HAZ01Writer.HAZ01A_FILE_NAME;
		ScalarIMR hIMR = new HAZ01A_FakeAttenRel(fileName);
		ERF hERF = new HAZ01A_FakeERF(erf);
		hERF.updateForecast();
		
		ArbitrarilyDiscretizedFunc hCurve = IMT_Info.getUSGS_PGA_Function();
		System.out.println("Calculating IM based curve");
		ArbitrarilyDiscretizedFunc hLogHazFunction = getLogFunction(hCurve);
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
			assertTrue(success);
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

	public ArrayList<ArrayList<SiteDataValue<?>>> getSitesData() {
		return sitesData;
	}

	public ArrayList<SiteDataValue<?>> getUserSiteDataValues(int i) {
		return sitesData.get(i);
	}

}
