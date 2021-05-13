package org.opensha.sha.faultSurface;

import static org.junit.Assert.*;

import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.jfree.data.Range;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotWindow;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class TestQuadSurface {
	
	private static final boolean GRAPHICAL_DEBUG = false;
	private static final boolean GRAPHICAL_DEBUG_WAIT = true;
	private static final boolean GRAPHICAL_DEBUG_ACT_DISTS = true;
	
	private static FaultTrace straight_trace;
	private static FaultTrace straight_trace_gridded;
	private static FaultTrace jagged_trace;
	private static FaultTrace jagged_trace_gridded;
	
	private static Location start_loc = new Location(34, -119);
	private static Location end_loc = new Location(35, -117);
	
	private static final double grid_disc = 0.2d;
	private static final double test_trace_radius = 200d;
	
	private static Random r = new Random();
	
	@BeforeClass
	public static void setUpBeforeClass() {
		straight_trace = new FaultTrace("stright");
		straight_trace.add(start_loc);
		straight_trace.add(end_loc);
		
		jagged_trace = new FaultTrace("jagged");
		jagged_trace.add(start_loc);
		jagged_trace.add(new Location(34.3, -118.8));
		jagged_trace.add(new Location(34.4, -118.0));
		jagged_trace.add(new Location(34.8, -117.7));
		jagged_trace.add(new Location(34.6, -117.3));
		jagged_trace.add(end_loc);
		
		straight_trace_gridded = FaultUtils.resampleTrace(straight_trace, (int)Math.round(straight_trace.getTraceLength()/grid_disc));
		jagged_trace_gridded = FaultUtils.resampleTrace(jagged_trace, (int)Math.round(jagged_trace.getTraceLength()/grid_disc));
	}
	
	/**
	 * this returns a location that is a random distance up to test_trace_radius from a random
	 * point in this gridded fault trace.
	 * @param gridded
	 * @return
	 */
	private static Location getRandomTestLoc(FaultTrace gridded) {
		Location p = gridded.get(r.nextInt(gridded.size()));
		p = new Location(p.getLatitude(), p.getLongitude());
		return LocationUtils.location(p, 2d*Math.PI*r.nextDouble(), test_trace_radius*r.nextDouble());
	}
	
	private static FaultSectionPrefData buildFSD(FaultTrace trace, double upper, double lower, double dip) {
		FaultSectionPrefData fsd = new FaultSectionPrefData();
		fsd.setFaultTrace(trace);
		fsd.setAveUpperDepth(upper);
		fsd.setAveLowerDepth(lower);
		fsd.setAveDip(dip);
		fsd.setDipDirection((float) trace.getDipDirection());
		return fsd;
	}
	
	private enum Dist {
		RUP,
		JB,
		SEIS,
		X;
	}
	
	private static double getDist(RuptureSurface surf, Location loc, Dist dist) {
		switch (dist) {
		case RUP:
			return surf.getDistanceRup(loc);
		case JB:
			return surf.getDistanceJB(loc);
		case SEIS:
			return surf.getDistanceSeis(loc);
		case X:
			return surf.getDistanceX(loc);

		default:
			throw new IllegalStateException();
		}
	}
	
	private static void runTest(FaultSectionPrefData fsd, FaultTrace gridded_trace,
			Dist dist, int num, double tol) {
		if (GRAPHICAL_DEBUG) {
//			runTestScatterPlot(fsd, gridded_trace, dist, num, tol);
			runTestGriddedPlot(fsd, gridded_trace, dist, num, tol);
			return;
		}
		// tolerance is in percents
		RuptureSurface gridded = fsd.getStirlingGriddedSurface(grid_disc, false, false);
		RuptureSurface quad = fsd.getQuadSurface(false, grid_disc);
		
		for (int i=0; i<num; i++) {
			Location testLoc = getRandomTestLoc(gridded_trace);
			double dist_gridded = getDist(gridded, testLoc, dist);
			double dist_quad = getDist(quad, testLoc, dist);
			
			double diff = Math.abs(dist_quad-dist_gridded);
			double pDiff = DataUtils.getPercentDiff(dist_quad, dist_gridded);
			
			if (dist_gridded < 3d*grid_disc || diff < 0.5*grid_disc)
				// too close to the points for the test to be accurate
				continue;
			
			assertTrue(fsd.getFaultTrace().getName()+" "+dist+" calc outside tolerance:\tgrd="
					+dist_gridded+"\tquad="+dist_quad+"\tdiff="+diff+"\tpDiff="+pDiff+"%", diff <= tol);
		}
	}
	
	private static void runTestScatterPlot(FaultSectionPrefData fsd, FaultTrace gridded_trace,
			Dist dist, int num, double tol) {
		// tolerance is in percents
		RuptureSurface gridded = fsd.getStirlingGriddedSurface(grid_disc, false, false);
		RuptureSurface quad = fsd.getQuadSurface(false, grid_disc);
		
		List<XY_DataSet> funcs = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		
		MinMaxAveTracker diffTrack = new MinMaxAveTracker();
		List<Double> diffs = Lists.newArrayList();
		List<Location> locs = Lists.newArrayList();
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		
		for (int i=0; i<num; i++) {
			Location testLoc = getRandomTestLoc(gridded_trace);
			double dist_gridded = getDist(gridded, testLoc, dist);
			double dist_quad = getDist(quad, testLoc, dist);
			
			double diff = Math.abs(dist_quad-dist_gridded);
			double pDiff = DataUtils.getPercentDiff(dist_quad, dist_gridded);
			
//			if (dist_gridded < 3d*grid_disc || diff < 0.5*grid_disc)
//				// too close to the points for the test to be accurate
//				continue;
			
			diffTrack.addValue(pDiff);
			diffs.add(pDiff);
			locs.add(testLoc);
			
//			assertTrue(fsd.getFaultTrace().getName()+" "+dist+" calc outside tolerance:\tgrd="
//					+dist_gridded+"\tquad="+dist_quad+"\tdiff="+diff+"\tpDiff="+pDiff+"%", pDiff <= tol);
		}
		
		CPT cpt;
		try {
//			cpt = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(0, diffTrack.getMax());
			cpt = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(0, 1d);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		cpt.setAboveMaxColor(cpt.getAboveMaxColor());
		
		// now discretize
		EvenlyDiscretizedFunc func = new EvenlyDiscretizedFunc(0d, (double)cpt.getMaxValue(), 50);
		for (int i=0; i<func.size(); i++) {
			funcs.add(new DefaultXY_DataSet());
			double val = func.getX(i);
			PlotSymbol sym;
			if (val <= tol)
				sym = PlotSymbol.CIRCLE;
			else
				sym = PlotSymbol.X;
			chars.add(new PlotCurveCharacterstics(sym, 1f, cpt.getColor((float)val)));
		}

		for (int i=0; i<diffs.size(); i++) {
			int index = func.getClosestXIndex(diffs.get(i));
			Location loc = locs.get(i);
			funcs.get(index).set(loc.getLongitude(), loc.getLatitude());
			latTrack.addValue(loc.getLatitude());
			lonTrack.addValue(loc.getLongitude());
		}
		
		DefaultXY_DataSet grid_outline = new DefaultXY_DataSet();
		for (Location loc : gridded.getPerimeter())
			grid_outline.set(loc.getLongitude(), loc.getLatitude());
		DefaultXY_DataSet quad_outline = new DefaultXY_DataSet();
		for (Location loc : quad.getPerimeter())
			quad_outline.set(loc.getLongitude(), loc.getLatitude());
		
		funcs.add(grid_outline);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		funcs.add(quad_outline);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY));
		
		GraphWindow gw = new GraphWindow(funcs, "Debug for Dist"+dist+" (max="+diffTrack.getMax()+" %)", chars);
		gw.setX_AxisLabel("Longitude");
		gw.setX_AxisLabel("Latitude");
		gw.setAxisRange(lonTrack.getMin()-0.1, lonTrack.getMax()+0.1, latTrack.getMin()-0.1, latTrack.getMax()+0.1);
		while (GRAPHICAL_DEBUG_WAIT && gw.isVisible()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
	}
	
	private static void runTestGriddedPlot(FaultSectionPrefData fsd, FaultTrace gridded_trace,
			Dist dist, int num, double tol) {
		GriddedRegion grid_reg = new GriddedRegion(fsd.getFaultTrace(), test_trace_radius, 0.05, null);
		GriddedGeoDataSet pDiffXYZ = new GriddedGeoDataSet(grid_reg, false);
		GriddedGeoDataSet diffXYZ = new GriddedGeoDataSet(grid_reg, false);
		GriddedGeoDataSet gridDistsXYZ = new GriddedGeoDataSet(grid_reg, false);
		GriddedGeoDataSet quadDistsXYZ = new GriddedGeoDataSet(grid_reg, false);
		// tolerance is in percents
		RuptureSurface gridded = fsd.getStirlingGriddedSurface(grid_disc, false, false);
		RuptureSurface quad = fsd.getQuadSurface(false, grid_disc);
		
		LocationList nodeList = grid_reg.getNodeList();
		for (int i = 0; i < nodeList.size(); i++) {
			Location loc = nodeList.get(i);
			double dist_gridded = getDist(gridded, loc, dist);
			double dist_quad = getDist(quad, loc, dist);
			
			double diff = Math.abs(dist_quad-dist_gridded);
			double pDiff = DataUtils.getPercentDiff(dist_quad, dist_gridded);
			
			pDiffXYZ.set(i, pDiff);
			diffXYZ.set(i, diff);
			gridDistsXYZ.set(i, dist_gridded);
			quadDistsXYZ.set(i, dist_quad);
		}
		
		CPT cpt;
		try {
//			cpt = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(0, diffTrack.getMax());
			cpt = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(0, 1d);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		cpt.setAboveMaxColor(cpt.getAboveMaxColor());
		
		CPT pDiffCPT = cpt.rescale(0, 1d); // 0% => 1%
		CPT diffCPT = cpt.rescale(0, 0.4); // 0m => 400m
		
		String title = "Dist"+dist+" discrepancies. Max: "+(float)diffXYZ.getMaxZ()+" km, "+(float)pDiffXYZ.getMaxZ()+" %";
		
		XYZPlotSpec pDiffSpec = new XYZPlotSpec(pDiffXYZ, pDiffCPT, title,
				"Latitude", "Longitude", "Percent Difference (%)");
		XYZPlotSpec diffSpec = new XYZPlotSpec(diffXYZ, diffCPT, title,
				"Latitude", "Longitude", "Absolute Difference (km)");
		
		DefaultXY_DataSet grid_outline = new DefaultXY_DataSet();
		for (Location loc : gridded.getPerimeter())
			grid_outline.set(loc.getLongitude(), loc.getLatitude());
		DefaultXY_DataSet quad_outline = new DefaultXY_DataSet();
		for (Location loc : quad.getPerimeter())
			quad_outline.set(loc.getLongitude(), loc.getLatitude());
		
		List<XY_DataSet> xyFuncs = Lists.newArrayList();
		List<PlotCurveCharacterstics> xyChars = Lists.newArrayList();
		
		xyFuncs.add(grid_outline);
		xyChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.BLACK));
		xyFuncs.add(quad_outline);
		xyChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, Color.GRAY));
		
		pDiffSpec.setXYChars(xyChars);
		pDiffSpec.setXYElems(xyFuncs);
		diffSpec.setXYChars(xyChars);
		diffSpec.setXYElems(xyFuncs);
		
		List<XYZPlotSpec> specs = Lists.newArrayList();
		specs.add(pDiffSpec);
		specs.add(diffSpec);
		
		Range xRange = new Range(grid_reg.getMinLon()-0.1, grid_reg.getMaxLon()+0.1);
		Range yRange = new Range(grid_reg.getMinLat()-0.1, grid_reg.getMaxLat()+0.1);
		List<Range> xRanges = Lists.newArrayList(xRange);
		List<Range> yRanges = Lists.newArrayList(yRange, yRange);
		XYZPlotWindow gw = new XYZPlotWindow(specs, xRanges, yRanges);
		
		if (GRAPHICAL_DEBUG_ACT_DISTS) {
			title = "Dist"+dist+" actual dists. Quad: "
					+(float)quadDistsXYZ.getMinZ()+"=>"+(float)quadDistsXYZ.getMaxZ()+" km, Grid:"
					+(float)gridDistsXYZ.getMinZ()+"=>"+(float)gridDistsXYZ.getMaxZ()+" km";
			
			CPT distCPT;
			if (dist == Dist.X)
				distCPT = cpt.rescale(-test_trace_radius, test_trace_radius);
			else
				distCPT = cpt.rescale(0, test_trace_radius);
			
			XYZPlotSpec quadSpec = new XYZPlotSpec(quadDistsXYZ, distCPT, title,
					"Latitude", "Longitude", "Quad Distance");
			XYZPlotSpec gridSpec = new XYZPlotSpec(gridDistsXYZ, distCPT, title,
					"Latitude", "Longitude", "Gridded Distance");
			
			quadSpec.setXYChars(xyChars);
			quadSpec.setXYElems(xyFuncs);
			gridSpec.setXYChars(xyChars);
			gridSpec.setXYElems(xyFuncs);
			
			specs = Lists.newArrayList();
			specs.add(quadSpec);
			specs.add(gridSpec);
			gw = new XYZPlotWindow(specs, xRanges, yRanges);
		}
		
		while (GRAPHICAL_DEBUG_WAIT && gw.isVisible()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
	}

	@Test
	public void testDistanceRup() {
		// note - only tests with site on surface
		
		Dist dist = Dist.RUP;
		int num = 500;
//		double tol = 0.5d;
		double tol = grid_disc;
		
		// first test at the surface
		
		// simple vertical fault
		runTest(buildFSD(straight_trace, 0d, 10d, 90), straight_trace_gridded, dist, num, tol);
		
		// simple dipping fault
		runTest(buildFSD(straight_trace, 0d, 10d, 45), straight_trace_gridded, dist, num, tol);
		
		// complex vertical fault
		runTest(buildFSD(jagged_trace, 0d, 10d, 90), jagged_trace_gridded, dist, num, tol);
		
		// complex dipping fault
		runTest(buildFSD(jagged_trace, 0d, 10d, 45), jagged_trace_gridded, dist, num, tol);
		
		// now test below the surface
		
		// simple vertical fault
		runTest(buildFSD(straight_trace, Math.random()*4d, 10d, 90), straight_trace_gridded, dist, num, tol);
		
		// simple dipping fault
		runTest(buildFSD(straight_trace, Math.random()*4d, 10d, 45), straight_trace_gridded, dist, num, tol);
		
		// complex vertical fault
		runTest(buildFSD(jagged_trace, Math.random()*4d, 10d, 90), jagged_trace_gridded, dist, num, tol);
		
		// complex dipping fault
		runTest(buildFSD(jagged_trace, Math.random()*4d, 10d, 45), jagged_trace_gridded, dist, num, tol);
	}

	@Test
	public void testDistanceSeis() {
		// note - only tests with site on surface
		
		Dist dist = Dist.SEIS;
		int num = 1000;
//		double tol = 0.5d;
		double tol = grid_disc;
		
		// complex vertical fault above
		runTest(buildFSD(jagged_trace, Math.random()*3d, 10d, 90), jagged_trace_gridded, dist, num, tol);
		
		// complex vertical fault below
		runTest(buildFSD(jagged_trace, 4d, 10d, 90), jagged_trace_gridded, dist, num, tol);
		
		// complex dipping fault above
		runTest(buildFSD(jagged_trace, Math.random()*3d, 10d, 45), jagged_trace_gridded, dist, num, tol);
		
		// complex dipping fault below
		runTest(buildFSD(jagged_trace, 4d, 10d, 45), jagged_trace_gridded, dist, num, tol);
	}

	@Test
	public void testDistanceJB() {
		// note - only tests with site on surface
		
		Dist dist = Dist.JB;
		int num = 1000;
//		double tol = 0.5d;
		double tol = grid_disc;
		
		// complex vertical fault
		runTest(buildFSD(jagged_trace, 0d, 10d, 90), jagged_trace_gridded, dist, num, tol);
		
		// complex dipping fault
		runTest(buildFSD(jagged_trace, 0d, 10d, 45), jagged_trace_gridded, dist, num, tol);
		
		// complex dipping deeper fault
		runTest(buildFSD(jagged_trace, 4d, 10d, 45), jagged_trace_gridded, dist, num, tol);
		
		// now test for zeros on the surface. use gridded surface for test locations
		FaultSectionPrefData dipping = buildFSD(straight_trace, 0d, 10d, 30);
		EvenlyGriddedSurface griddedDipping = dipping.getStirlingGriddedSurface(1d, false, false);
		QuadSurface quadDipping = dipping.getQuadSurface(false);
		for (int row=1; row<griddedDipping.getNumRows()-1; row++) {
			for (int col=1; col<griddedDipping.getNumCols()-1; col++) {
				Location loc = griddedDipping.get(row, col);
				loc = new Location(loc.getLatitude(), loc.getLongitude());
				double qDist = quadDipping.getDistanceJB(loc);
				if (qDist > 1e-10) {
					// reset the cache
					quadDipping.getDistanceJB(new Location(Math.random(), Math.random()));
					quadDipping.getDistanceJB(loc);
				}
				Preconditions.checkState((float)griddedDipping.getDistanceJB(loc)==0f);
				assertEquals("Quad distJB isn't zero above surf: "+qDist+"\nloc: "+loc, 0d, qDist, 1e-10);
			}
		}
	}

	@Test
	public void testDistanceX() {
		// note - only tests with site on surface
		Dist dist = Dist.X;
		int num = 1000;
//		double tol = 0.5d;
		double tol = grid_disc;
		
		// we don't expect this to pass, as quad distance X uses cartensian extention of faults instead of
		// great circle.
		
		// simple vertical fault
		runTest(buildFSD(straight_trace, Math.random()*4d, 10d, 90), straight_trace_gridded, dist, num, tol);
		
		// simple dipping fault
		runTest(buildFSD(straight_trace, Math.random()*4d, 10d, 45), straight_trace_gridded, dist, num, tol);
		
		// complex vertical fault
		runTest(buildFSD(jagged_trace, Math.random()*4d, 10d, 90), jagged_trace_gridded, dist, num, tol);
		
		// complex dipping fault
		runTest(buildFSD(jagged_trace, Math.random()*4d, 10d, 45), jagged_trace_gridded, dist, num, tol);
	}
	
	private static void runPlanarZeroZTest(Iterable<Location> locs, QuadSurface surf, double tol_fract) {
		Location startLoc = surf.getFirstLocOnUpperEdge();
		for (Location loc : locs) {
			double horz_dist = LocationUtils.horzDistance(startLoc, loc);
			double tol = tol_fract * horz_dist;
			if (tol < 1e-2)
				tol = 1e-2;
			Vector3D proj = surf.getRupProjectedPoint(0, loc);
			assertEquals(0d, proj.getZ(), tol);
		}
	}
	
	private static void runZeroTest(Iterable<Location> locs, QuadSurface surf, Dist dist, double tol_fract) {
		Location startLoc = surf.getFirstLocOnUpperEdge();
		for (Location loc : locs) {
			double horz_dist = LocationUtils.horzDistance(startLoc, loc);
			double tol = tol_fract * horz_dist;
			if (tol < 1e-2)
				tol = 1e-2;
			double surfDist = getDist(surf, loc, dist);
			assertEquals("Zero dist check fail with origin horz dist of "+horz_dist, 0d, surfDist, tol);
		}
	}
	
	/* These tests ensure that the get*** and getGridded**** methods indeed return Locations
	 * on the surface. Tolerance is tested to within 1 meter. */
	private static final double zero_tol_fract = 1e-2;
	
	@Test
	public void testPerimeter() {
		QuadSurface surf = buildFSD(straight_trace, 0d, 10d, 90).getQuadSurface(false);
		runPlanarZeroZTest(surf.getPerimeter(), surf, zero_tol_fract);
		runZeroTest(surf.getPerimeter(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(straight_trace, 0d, 10d, 45).getQuadSurface(false);
		runPlanarZeroZTest(surf.getPerimeter(), surf, zero_tol_fract);
		runZeroTest(surf.getPerimeter(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(jagged_trace, 0d, 10d, 90).getQuadSurface(false);
		runZeroTest(surf.getPerimeter(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(jagged_trace, 0d, 10d, 45).getQuadSurface(false);
		runZeroTest(surf.getPerimeter(), surf, Dist.RUP, zero_tol_fract);
	}
	
	@Test
	public void testGriddedPerimeter() {
		QuadSurface surf = buildFSD(straight_trace, 0d, 10d, 90).getQuadSurface(false);
		runPlanarZeroZTest(surf.getEvenlyDiscritizedPerimeter(), surf, zero_tol_fract);
		runZeroTest(surf.getEvenlyDiscritizedPerimeter(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(straight_trace, 0d, 10d, 45).getQuadSurface(false);
		runPlanarZeroZTest(surf.getEvenlyDiscritizedPerimeter(), surf, zero_tol_fract);
		runZeroTest(surf.getEvenlyDiscritizedPerimeter(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(jagged_trace, 0d, 10d, 90).getQuadSurface(false);
		runZeroTest(surf.getEvenlyDiscritizedPerimeter(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(jagged_trace, 0d, 10d, 45).getQuadSurface(false);
		runZeroTest(surf.getEvenlyDiscritizedPerimeter(), surf, Dist.RUP, zero_tol_fract);
	}
	
	@Test
	public void testGriddedSurfLocs() {
		QuadSurface surf = buildFSD(straight_trace, 0d, 10d, 90).getQuadSurface(false);
		runPlanarZeroZTest(surf.getEvenlyDiscritizedListOfLocsOnSurface(), surf, zero_tol_fract);
		runZeroTest(surf.getEvenlyDiscritizedListOfLocsOnSurface(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(straight_trace, 0d, 10d, 45).getQuadSurface(false);
		runPlanarZeroZTest(surf.getEvenlyDiscritizedListOfLocsOnSurface(), surf, zero_tol_fract);
		runZeroTest(surf.getEvenlyDiscritizedListOfLocsOnSurface(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(jagged_trace, 0d, 10d, 90).getQuadSurface(false);
		runZeroTest(surf.getEvenlyDiscritizedListOfLocsOnSurface(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(jagged_trace, 0d, 10d, 45).getQuadSurface(false);
		runZeroTest(surf.getEvenlyDiscritizedListOfLocsOnSurface(), surf, Dist.RUP, zero_tol_fract);
	}
	
	@Test
	public void testGriddedUpperLocs() {
		QuadSurface surf = buildFSD(straight_trace, 0d, 10d, 90).getQuadSurface(false);
		runPlanarZeroZTest(surf.getEvenlyDiscritizedUpperEdge(), surf, zero_tol_fract);
		runZeroTest(surf.getEvenlyDiscritizedUpperEdge(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(straight_trace, 0d, 10d, 45).getQuadSurface(false);
		runPlanarZeroZTest(surf.getEvenlyDiscritizedUpperEdge(), surf, zero_tol_fract);
		runZeroTest(surf.getEvenlyDiscritizedUpperEdge(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(jagged_trace, 0d, 10d, 90).getQuadSurface(false);
		runZeroTest(surf.getEvenlyDiscritizedUpperEdge(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(jagged_trace, 0d, 10d, 45).getQuadSurface(false);
		runZeroTest(surf.getEvenlyDiscritizedUpperEdge(), surf, Dist.RUP, zero_tol_fract);
	}
	
	@Test
	public void testGriddedLowerLocs() {
		QuadSurface surf = buildFSD(straight_trace, 0d, 10d, 90).getQuadSurface(false);
		runPlanarZeroZTest(surf.getEvenlyDiscritizedLowerEdge(), surf, zero_tol_fract);
		runZeroTest(surf.getEvenlyDiscritizedLowerEdge(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(straight_trace, 0d, 10d, 45).getQuadSurface(false);
		runPlanarZeroZTest(surf.getEvenlyDiscritizedLowerEdge(), surf, zero_tol_fract);
		runZeroTest(surf.getEvenlyDiscritizedLowerEdge(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(jagged_trace, 0d, 10d, 90).getQuadSurface(false);
		runZeroTest(surf.getEvenlyDiscritizedLowerEdge(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(jagged_trace, 0d, 10d, 45).getQuadSurface(false);
		runZeroTest(surf.getEvenlyDiscritizedLowerEdge(), surf, Dist.RUP, zero_tol_fract);
	}
	
	@Test
	public void testUpperLocs() {
		QuadSurface surf = buildFSD(straight_trace, 0d, 10d, 90).getQuadSurface(false);
		runPlanarZeroZTest(surf.getUpperEdge(), surf, zero_tol_fract);
		runZeroTest(surf.getUpperEdge(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(straight_trace, 0d, 10d, 45).getQuadSurface(false);
		runPlanarZeroZTest(surf.getUpperEdge(), surf, zero_tol_fract);
		runZeroTest(surf.getUpperEdge(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(jagged_trace, 0d, 10d, 90).getQuadSurface(false);
		runZeroTest(surf.getUpperEdge(), surf, Dist.RUP, zero_tol_fract);
		
		surf = buildFSD(jagged_trace, 0d, 10d, 45).getQuadSurface(false);
		runZeroTest(surf.getUpperEdge(), surf, Dist.RUP, zero_tol_fract);
		
		List<Location> firstLast = Lists.newArrayList(surf.getFirstLocOnUpperEdge(), surf.getLastLocOnUpperEdge());
		runZeroTest(firstLast, surf, Dist.RUP, zero_tol_fract);
	}

}

