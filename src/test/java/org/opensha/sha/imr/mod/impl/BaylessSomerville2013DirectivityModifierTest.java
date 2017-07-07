package org.opensha.sha.imr.mod.impl;

import static org.junit.Assert.*;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jfree.data.Range;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.ArbDiscrXYZ_DataSet;
import org.opensha.commons.data.xyz.XYZ_DataSet;
import org.opensha.commons.data.xyz.XYZ_DataSetMath;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotWindow;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.FrankelGriddedSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

@RunWith(Parameterized.class)
public class BaylessSomerville2013DirectivityModifierTest {
	
	private static BaylessSomerville2013DirectivityModifier mod;
	
	private static Map<TestCase, CSVFile<Double>> csvs;
	
	private static final boolean map = true;
	private static CPT cpt = null;
	
	private TestCase test;
	
	@Parameters(name="{0}")
	public static Collection<TestCase[]> data() {
		// parameterized test that will automatically do each enum
		List<TestCase[]> data = Lists.newArrayList();
		for (TestCase test : TestCase.values())
			data.add(new TestCase[] {test});
		return data;
	}

	public BaylessSomerville2013DirectivityModifierTest(TestCase test) {
		this.test = test;
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		mod = new BaylessSomerville2013DirectivityModifier();
		
		// read in test data from input stream
//		byte[] buffer = new byte[2048];
		
		csvs = Maps.newHashMap();
		final ZipInputStream zip = new ZipInputStream(
				BaylessSomerville2013DirectivityModifierTest.class.getResourceAsStream("bay2013_test.zip"));;
		try {
			ZipEntry entry;
			
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();
				if (!name.contains("_bay12") || !name.endsWith(".txt"))
					continue;
				String testCaseName = name.substring(0, name.indexOf("_")).toUpperCase();
				TestCase test;
				try {
					test = TestCase.valueOf(testCaseName);
				} catch (Exception e) {
					System.out.println("Skipping "+testCaseName+", no matching enum constant");
					continue;
				}
				System.out.print("Loading CSV for "+testCaseName+"...");
				// the zip input stream is now aligned for reading this file, but we need to wrap it in
				// its own input stream so that reading the CSV doesn't close it
				InputStream is = new InputStream() {

					@Override
					public int read() throws IOException {
						// TODO Auto-generated method stub
						return zip.read();
					}
					
				};
				CSVFile<Double> csv = CSVFile.readStreamNumeric(is, false, -1, 0);
				System.out.println(csv.getNumRows()+" cases");
				csvs.put(test, csv);
			}
		} finally {
			if (zip != null)
				zip.close();
		}
	}
	
	private static final double az_north = 0d;
	private static final double az_east = Math.PI*0.5;
	private static final Location origin = new Location(0d, 0d);
	
	private static final double period = 5d;
	
	// passes if diff < max_diff || pDiff < max_pDiff
	private static final double max_diff = 0.01;
	private static final double max_pdiff = 1d;
	
	private enum TestCase {
		
		// 		len		width	rake	dip		mag		hypAl	hypD	topDep	bend
		// 									actually zTop=7 for ss1 but not in data files
//		SS1(	6d,		5d,		180,	90,		5.5,	0.2,	2,		0,		0);
//		SS3(	80d,	15d,	180,	90,		7.2,	0.2,	5,		0,		0);
//		SS4(	235d,	15d,	180,	90,		7.8,	0.1,	5,		0,		0);
//		SS7(	400d,	15d,	180,	90,		8.1,	0.1,	5,		0,		0),
		RV4(	32d,	28d,	90,		30,		7,		0.2,	8,		0,		0);
//		RV7(	80d,	30d,	90,		30,		7.5,	0.1,	8,		0,		45),
//		SO6(	80d,	15d,	135,	70,		7.2,	0.1,	5,		0,		0);
		
		private double length, width, rake, dip, mag, fractHypAlong, upDipHypDist, topDepth, bendDegrees;
		
		private TestCase(double length, double width, double rake, double dip,
				double mag, double fractHypAlong, double upDipHypDist, double topDepth, double bendDegrees) {
			this.length = length;
			this.width = width;
			this.rake = rake;
			this.dip = dip;
			this.mag = mag;
			this.fractHypAlong = fractHypAlong;
			this.upDipHypDist = upDipHypDist;
			this.topDepth = topDepth;
			this.bendDegrees = bendDegrees;
		}
		
		public EqkRupture buildRupture() {
			double dipRad = Math.toRadians(dip);
			
			double fractHypDown = (width-upDipHypDist)/width;
			
			FaultSectionPrefData fsd = new FaultSectionPrefData();
			fsd.setAveDip(dip);
			fsd.setAveRake(rake);
			fsd.setAveUpperDepth(topDepth);
			fsd.setAveLowerDepth(topDepth+Math.sin(dipRad)*width);
			System.out.println("Calc lower depth for dip="+dip+", ddw="+width+": "+fsd.getAveLowerDepth());
			FaultTrace trace = new FaultTrace("");
			Location origin = new Location(0d, 0d);
			trace.add(origin);
			if (bendDegrees > 0) {
				// go halfway
				Location halfway = LocationUtils.location(origin, az_north, length*0.5);
				trace.add(halfway);
				// now bend
				trace.add(LocationUtils.location(halfway, Math.toRadians(bendDegrees), length*0.5));
			} else {
				trace.add(LocationUtils.location(origin, az_north, length));
			}
			fsd.setFaultTrace(trace);
			fsd.setDipDirection((float)trace.getDipDirection());
			
			Location hypo = LocationUtils.location(origin, az_north, fractHypAlong*length);
			hypo = LocationUtils.location(hypo, az_east, fractHypDown*width*Math.cos(dipRad));
			hypo = new Location(hypo.getLatitude(), hypo.getLongitude(), fractHypDown*width*Math.sin(dipRad));
//			RuptureSurface surf = fsd.getStirlingGriddedSurface(1d);
			RuptureSurface surf = new FrankelGriddedSurface(fsd.getSimpleFaultData(false), 1d);
			System.out.println("Hypo surface dist: "+surf.getDistanceRup(hypo));
			
			EqkRupture rup = new EqkRupture(mag, rake, surf, hypo);
			return rup;
		}
	}
	
	private ArbDiscrXYZ_DataSet loadData(TestCase test) {
		Preconditions.checkState(csvs.containsKey(test), "No CSV found for "+test.name());
		CSVFile<Double> csv = csvs.get(test);
		
		// first load as is in then reposition
		ArbDiscrXYZ_DataSet dataOrig = new ArbDiscrXYZ_DataSet();
		
		for (int row=0; row<csv.getNumRows(); row++) {
			List<Double> line = csv.getLine(row);
			double x = line.get(0);
			double y = line.get(1);
			double fd = line.get(2);
			
			dataOrig.set(x, y, fd);
		}
		
		ArbDiscrXYZ_DataSet data = new ArbDiscrXYZ_DataSet();
		double minX = dataOrig.getMinX();
		double maxX = dataOrig.getMaxX();
		double xSubtract = (maxX - minX)/2d;
		
		double minY = dataOrig.getMinY();
		double maxY = dataOrig.getMaxY();
		double ySubtract = (maxY - minY)/2d;
		
		for (int index=0; index<dataOrig.size(); index++) {
			Point2D pt = dataOrig.getPoint(index);
			double x = pt.getX() - xSubtract;
			double y = pt.getY() - ySubtract;
			y = -y;
			
			data.set(x, y, dataOrig.get(index));
		}
		
		return data;
	}
	
	@Test
	public void test() {
		System.out.println("Testing "+test.name());
		
		EqkRupture rup = test.buildRupture();
		
		// keep track of non trivial correct answers
		int nonZero = 0;
		int nonZeroCorrect = 0;
		int numFailed = 0;
		
		AssertionError error = null;
		
		ArbDiscrXYZ_DataSet dataXYZ = loadData(test);
		ArbDiscrXYZ_DataSet calcXYZ = null;
		if (map)
			calcXYZ = new ArbDiscrXYZ_DataSet();
		
		double maxDiffEncountered = 0d;
		double maxPDiffEncountered = 0d;
		
		for (int index=0; index<dataXYZ.size(); index++) {
			Point2D pt = dataXYZ.getPoint(index);
			double x = pt.getX();
			double y = pt.getY();
			double expectedFd = dataXYZ.get(index);
			
			Location loc = LocationUtils.location(origin, az_north, y);
			loc = LocationUtils.location(loc, az_east, x);
			
			double actualFd = mod.getFd(rup, loc, period);
			
			if (map)
				calcXYZ.set(x, y, actualFd);
			
			double diff = Math.abs(actualFd - expectedFd);
			double pDiff = Math.abs(DataUtils.getPercentDiff(actualFd, expectedFd));
			
			maxDiffEncountered = Math.max(maxDiffEncountered, diff);
			maxPDiffEncountered = Math.max(maxPDiffEncountered, pDiff);
			
			String message = "Fd mismatch at row="+index+", x="+(float)x+", y="+(float)y
					+" ("+nonZeroCorrect+" non zero correct so far)."
					+" expected="+(float)expectedFd+", actual="+(float)actualFd
					+". |diff|="+(float)diff+", "+(float)pDiff+" %";
			
//			if (diff > max_diff)
//				System.out.println(message);
			
			try {
				assertTrue("Non finiite Fd at row="+index+", x="+x+", y="+y+": "+actualFd,
						Doubles.isFinite(actualFd));
				assertTrue(message, diff < max_diff || pDiff < max_pdiff);
				if (expectedFd != 0d)
					nonZeroCorrect++;
			} catch (AssertionError e) {
				if (!map)
					throw e;
				numFailed++;
				if (e != null)
					error = e;
			}
			if (expectedFd != 0d)
				nonZero++;
		}
		
		System.out.println("Done with "+test+". Max diff: "+maxDiffEncountered+", pDiff: "+maxPDiffEncountered);
		
		if (numFailed > 0)
			System.out.println("Failed "+numFailed+"/"+dataXYZ.size()+" ("+nonZero+" non zero, "
					+nonZeroCorrect+" of which passed)");
		
		if (error != null)
			System.out.println(error.getMessage());
		
//		if (map && numFailed > 0)
			plot(test, rup, dataXYZ, calcXYZ);
		
		if (error != null)
			throw error;
	}
	
	private void plot(TestCase test, EqkRupture rup,
			ArbDiscrXYZ_DataSet dataXYZ, ArbDiscrXYZ_DataSet calcXYZ) {
		double max = Math.max(Math.abs(dataXYZ.getMaxZ()), Math.abs(dataXYZ.getMinZ()));
		if (cpt == null) {
			try {
				cpt = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(-max, max);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		XYZ_DataSet diffXYZ = XYZ_DataSetMath.subtract(calcXYZ, dataXYZ);
		double maxDiff = Math.max(Math.abs(diffXYZ.getMinZ()), Math.abs(diffXYZ.getMaxZ()));
		CPT diffCPT = cpt.rescale(-maxDiff, maxDiff);
		
		XYZ_DataSet[] xyzs = { dataXYZ, calcXYZ, diffXYZ };
		String[] names = { "Expected", "Actual", "Diff" };
		CPT[] cpts = { cpt, cpt, diffCPT };
		
		XYZPlotWindow[] windows = new XYZPlotWindow[xyzs.length];
		
		for (int n=0; n<xyzs.length; n++) {
			XYZ_DataSet xyz = xyzs[n];
			String title = test.name()+" "+names[n];
			
			XYZPlotSpec spec = new XYZPlotSpec(xyz, cpts[n], title, "E/W (km)", "N/S (km)", "fD");
			// add XY elements
			List<XY_DataSet> funcs = Lists.newArrayList();
			List<PlotCurveCharacterstics> chars = Lists.newArrayList();
			// hypocenter
			DefaultXY_DataSet hypoFunc = new DefaultXY_DataSet();
			hypoFunc.set(calcPt(origin, rup.getHypocenterLocation()));
			funcs.add(hypoFunc);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_DIAMOND, 10f, Color.RED));
			// trace
			if (rup.getRuptureSurface().getAveDip() < 90) {
				DefaultXY_DataSet traceFunc = new DefaultXY_DataSet();
				for (Location loc : rup.getRuptureSurface().getEvenlyDiscritizedPerimeter())
					traceFunc.set(calcPt(origin, loc));
				funcs.add(traceFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY));
			}
			DefaultXY_DataSet traceFunc = new DefaultXY_DataSet();
			for (Location loc : rup.getRuptureSurface().getEvenlyDiscritizedUpperEdge())
				traceFunc.set(calcPt(origin, loc));
			funcs.add(traceFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
			spec.setXYElems(funcs);
			spec.setXYChars(chars);
			XYZPlotWindow gw = new XYZPlotWindow(spec, new Range(xyz.getMinX(), xyz.getMaxX()),
					new Range(xyz.getMinY(), xyz.getMaxY()));
			gw.setLocation(gw.getWidth()*n, 0);
//			gw.setDefaultCloseOperation(XYZPlotWindow.EXIT_ON_CLOSE);
			windows[n] = gw;
		}
		
		// keep alive until all plots closed
		while (true) {
			boolean alive = false;
			for (XYZPlotWindow window : windows)
				alive = alive || window.isVisible();
			if (!alive)
				break;
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
	}
	
	private static Point2D calcPt(Location origin, Location loc) {
		LocationVector v = LocationUtils.vector(origin, loc);
		double hDist = v.getHorzDistance();
		double azRad = v.getAzimuthRad();
		double y = Math.cos(azRad)*hDist;
		double x = Math.sin(azRad)*hDist;
//		System.out.println("Built point: x="+x+", y="+y+"\t"+loc+"\taz="+v.getAzimuth());
		return new Point2D.Double(x, y);
	}

}
