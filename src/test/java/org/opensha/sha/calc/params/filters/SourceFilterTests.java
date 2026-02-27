package org.opensha.sha.calc.params.filters;

import static org.junit.Assert.*;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.calc.sourceFilters.FixedDistanceCutoffFilter;
import org.opensha.sha.calc.sourceFilters.MagDependentDistCutoffFilter;
import org.opensha.sha.calc.sourceFilters.MinMagFilter;
import org.opensha.sha.calc.sourceFilters.SourceFilter;
import org.opensha.sha.calc.sourceFilters.SourceFilterManager;
import org.opensha.sha.calc.sourceFilters.SourceFilters;
import org.opensha.sha.calc.sourceFilters.TectonicRegionDistCutoffFilter;
import org.opensha.sha.calc.sourceFilters.TectonicRegionDistCutoffFilter.TectonicRegionDistanceCutoffs;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.PointSource;
import org.opensha.sha.earthquake.PointSource.PoissonPointSource;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

public class SourceFilterTests {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		calc = new HazardCurveCalculator();
		filterManager = calc.getSourceFilterManager();
		
		testIMR = AttenRelRef.ASK_2014.get();
		testIMR.setParamDefaults();
		
		testSite = new Site(new Location(34, -118));
		testSite.addParameterList(testIMR.getSiteParams());
		
		DiscretizedFunc linearXVals = new IMT_Info().getDefaultHazardCurve(testIMR.getIntensityMeasure());
		xVals = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : linearXVals)
			xVals.set(Math.log(pt.getX()), 0d);
		
		erf = new TestERF();
		System.out.println("erf has "+erf.getNumSources()+" sources");
		int numRups = 0;
		for (ProbEqkSource source : erf)
			numRups += source.getNumRuptures();
		System.out.println("erf has "+numRups+" ruptures");
	}
	
	private static final TectonicRegionType[] TRTs = TectonicRegionType.values();
	
	private static final int NUM_DIST = 51;
	private static final double MIN_DIST = 15d;
	private static final double DELTA_DIST = 20d;
	
	private static final int NUM_MAG = 15;
	private static final double MIN_MAG= 5.05d;
	private static final double DELTA_MAG = 0.25d;
	
	private static Site testSite;
	
	private static TestERF erf;
	
	private static HazardCurveCalculator calc;
	private static ScalarIMR testIMR;
	private static DiscretizedFunc xVals;
	
	private static SourceFilterManager filterManager;
	
	private static class TestERF extends AbstractERF {
		
		private List<AccessTrackingPointEqkSource> sources;
		
		public TestERF() {
			sources = new ArrayList<>(TRTs.length*NUM_DIST);
			
			GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
			mfd.setAllButTotMoRate(mfd.getMinX(), mfd.getMaxX(), 1d, 1d);
			
			for (int t=0; t<TRTs.length; t++) {
				for (int d=0; d<NUM_DIST; d++) {
					double dist = MIN_DIST + d*DELTA_DIST;
					Location loc = LocationUtils.location(testSite.getLocation(), Math.PI*0.5, dist);
					AccessTrackingPointEqkSource source = new AccessTrackingPointEqkSource(loc, mfd, 1d, 0d, 90d);
					source.setTectonicRegionType(TRTs[t]);
					sources.add(source);
				}
			}
		}

		@Override
		public int getNumSources() {
			return sources.size();
		}

		@Override
		public AccessTrackingPointEqkSource getSource(int idx) {
			return sources.get(idx);
		}

		@Override
		public void updateForecast() {}

		@Override
		public String getName() {
			return "Test ERF";
		}
		
	}
	
	private static class AccessTrackingPointEqkSource extends ProbEqkSource {
		
		private PoissonPointSource source;
		private List<AccessTrackingPointEqkRup> rups;
		
		boolean sourceAccessed = false;
		
		public AccessTrackingPointEqkSource(Location loc, IncrementalMagFreqDist magFreqDist,double duration,
				double aveRake, double aveDip){
			super();
//			source = new PointEqkSource(loc, magFreqDist, duration, aveRake, aveDip);
			source = PointSource.poissonBuilder(loc)
					.truePointSources()
					.forMFDAndFocalMech(magFreqDist, new FocalMechanism(Double.NaN, aveDip, aveRake))
					.duration(duration)
					.build();
			rups = new ArrayList<>(source.getNumRuptures());
			for (ProbEqkRupture rup : source)
				rups.add(new AccessTrackingPointEqkRup(rup));
		}

		@Override
		public LocationList getAllSourceLocs() {
			return source.getAllSourceLocs();
		}

		@Override
		public RuptureSurface getSourceSurface() {
			return source.getSourceSurface();
		}

		@Override
		public double getMinDistance(Site site) {
			return source.getMinDistance(site);
		}

		@Override
		public int getNumRuptures() {
			return rups.size();
		}

		@Override
		public AccessTrackingPointEqkRup getRupture(int nRupture) {
			sourceAccessed = true;
			return rups.get(nRupture);
		}
		
		@Override
		public ArrayList<ProbEqkRupture> drawRandomEqkRuptures() {
			sourceAccessed = true;
			return super.drawRandomEqkRuptures();
		}

		public void clearAccess() {
			sourceAccessed = false;
			for (AccessTrackingPointEqkRup rup : rups)
				rup.clearAccess();
		}
		
		public boolean hasSourceBeenAccessed() {
			return sourceAccessed;
		}
	}
	
	private static class AccessTrackingPointEqkRup extends ProbEqkRupture {
		
		boolean rupAccessed = false;
		
		public AccessTrackingPointEqkRup(ProbEqkRupture rup) {
			super(rup.getMag(), rup.getAveRake(), rup.getProbability(), rup.getRuptureSurface(), rup.getHypocenterLocation());
		}
		
		@Override
		public double getAveRake() {
			rupAccessed = true;
			return super.getAveRake();
		}

		public void clearAccess() {
			rupAccessed = false;
		}
		
		public boolean hasRupBeenAccessed() {
			return rupAccessed;
		}

		@Override
		public Object clone() {
			// random event sets clone ruptures, which messes with the tracking, so just return this
			return this;
		}
		
	}
	
	private static void disableAllFilters() {
		for (SourceFilters filterType : SourceFilters.values())
			filterManager.setEnabled(filterType, false);
	}

	@Test
	public void testFilterByTRT() {
		disableAllFilters();
		filterManager.setEnabled(SourceFilters.TRT_DIST_CUTOFFS, true);
		TectonicRegionDistCutoffFilter filter = (TectonicRegionDistCutoffFilter)
				filterManager.getFilterInstance(SourceFilters.TRT_DIST_CUTOFFS);
		doTestTRTFilter(filter);
		checkEnabledFitlers(filter);
		TectonicRegionDistanceCutoffs cutoffs = filter.getCutoffs();
		// set them all to infinity
		for (TectonicRegionType trt : TectonicRegionType.values())
			cutoffs.setCutoffDist(trt, Double.POSITIVE_INFINITY);
		int noExclusion = doTestTRTFilter(filter);
		// set each to almost zero individually
		for (TectonicRegionType trt : TectonicRegionType.values()) {
			cutoffs.setCutoffDist(trt, Double.MIN_VALUE);
			int numRups = doTestTRTFilter(filter);
			assertTrue("We enabled a TRT filter, but the number or ruptures included didn't change?", numRups < noExclusion);
			cutoffs.setCutoffDist(trt, trt.defaultCutoffDist());
			doTestTRTFilter(filter);
			cutoffs.setCutoffDist(trt, Double.POSITIVE_INFINITY);
		}
		
		// reset to defaults for use later
		for (TectonicRegionType trt : TectonicRegionType.values())
			cutoffs.setCutoffDist(trt, trt.defaultCutoffDist());
	}
	
	private int doTestTRTFilter(TectonicRegionDistCutoffFilter filter) {
		TectonicRegionDistanceCutoffs cutoffs = filter.getCutoffs();
		for (ProbEqkSource source : erf.sources) {
			TectonicRegionType trt = source.getTectonicRegionType();
			double dist = source.getMinDistance(testSite);
			double trtDist = cutoffs.getCutoffDist(trt);
			assertEquals(dist > trtDist, filter.canSkipSource(source, testSite, dist));
			for (ProbEqkRupture rup : source)
				assertFalse("TRT filter should never apply to a rupture, just sources", filter.canSkipRupture(rup, testSite));
		}
		return doTestHazardCalc();
	}
	
	@Test
	public void testFilterByDist() {
		disableAllFilters();
		FixedDistanceCutoffFilter filter = (FixedDistanceCutoffFilter)
				filterManager.getFilterInstance(SourceFilters.FIXED_DIST_CUTOFF);
		filterManager.setEnabled(SourceFilters.FIXED_DIST_CUTOFF, true);
		checkEnabledFitlers(filter);
		int rupsWithDefault = doTestDistFilter(filter);
		
		filter.setMaxDistance(Double.MIN_VALUE);
		int rupsWithFull = doTestDistFilter(filter);
		assertTrue("Rupture count should be less with max distance at near-zero", rupsWithFull < rupsWithDefault);
		
		filter.setMaxDistance(10000d);
		int rupsWithNone = doTestDistFilter(filter);
		assertTrue("Rupture count should be more with max distance at near-infinite", rupsWithNone > rupsWithDefault);
		
		filter.setMaxDistance(200d);
		doTestDistFilter(filter);
	}
	
	private int doTestDistFilter(FixedDistanceCutoffFilter filter) {
		double maxDist = filter.getMaxDistance();
		for (ProbEqkSource source : erf.sources) {
			double dist = source.getMinDistance(testSite);
			assertEquals(dist > maxDist, filter.canSkipSource(source, testSite, dist));
			for (ProbEqkRupture rup : source) {
				dist = rup.getRuptureSurface().getQuickDistance(testSite.getLocation());
				assertEquals(dist > maxDist, filter.canSkipRupture(rup, testSite));
			}
		}
		return doTestHazardCalc();
	}
	
	@Test
	public void testFilterByMag() {
		disableAllFilters();
		MinMagFilter filter = (MinMagFilter)
				filterManager.getFilterInstance(SourceFilters.MIN_MAG);
		filterManager.setEnabled(SourceFilters.MIN_MAG, true);
		checkEnabledFitlers(filter);
		doTestMagFilter(filter);
		
		filter.setMinMagnitude(0d);
		int rupsWith0 = doTestMagFilter(filter);
		
		filter.setMinMagnitude(8d);
		int rupsWith8 = doTestMagFilter(filter);
		
		assertTrue("Ruptures include with minMag=8 should be less than with minMag=0", rupsWith8 < rupsWith0);
		
		filter.setMinMagnitude(5d);
		doTestMagFilter(filter);
	}
	
	private int doTestMagFilter(MinMagFilter filter) {
		double minMag = filter.getMinMagnitude();
		for (ProbEqkSource source : erf.sources) {
			double dist = source.getMinDistance(testSite);
			assertFalse("Minimum magnitude filter should never exclude a whole source (applied to ruptures)",
				filter.canSkipSource(source, testSite, dist));
			for (ProbEqkRupture rup : source)
				assertEquals(rup.getMag() < minMag, filter.canSkipRupture(rup, testSite));
		}
		return doTestHazardCalc();
	}
	
	@Test
	public void testFilterByMagDist() {
		disableAllFilters();
		MagDependentDistCutoffFilter filter = (MagDependentDistCutoffFilter)
				filterManager.getFilterInstance(SourceFilters.MAG_DIST_CUTOFFS);
		filterManager.setEnabled(SourceFilters.MAG_DIST_CUTOFFS, true);
		checkEnabledFitlers(filter);
		int numWithDefault = doTestMagDistFilter(filter);
		
		ArbitrarilyDiscretizedFunc func = filter.getMagDistFunc();
		ArbitrarilyDiscretizedFunc origFunc = func.deepClone();
		func.clear();
		for (int i=0; i<origFunc.size(); i++)
			func.set(10000d+i, origFunc.getY(i));
		int numWithLarge = doTestMagDistFilter(filter);
		assertTrue("Fewer ruptures should be include with default mag-dist ("+numWithDefault+") than large ("+numWithLarge+")", numWithDefault < numWithLarge);
		
		func.clear();
		for (int i=0; i<origFunc.size(); i++)
			func.set(0.01d*i, origFunc.getY(i));
		int numWithSmall = doTestMagDistFilter(filter);
		assertTrue("Fewer ruptures should be include with small distance than large", numWithSmall < numWithLarge);
		
		func.clear();
		for (int i=0; i<origFunc.size(); i++)
			func.set(origFunc.getX(i), origFunc.getY(i));
		doTestMagDistFilter(filter);
	}
	
	private int doTestMagDistFilter(MagDependentDistCutoffFilter filter) {
		ArbitrarilyDiscretizedFunc func = filter.getMagDistFunc();
		for (ProbEqkSource source : erf.sources) {
			double dist = source.getMinDistance(testSite);
			assertEquals(dist > func.getMaxX(), filter.canSkipSource(source, testSite, dist));
			for (ProbEqkRupture rup : source) {
				dist = rup.getRuptureSurface().getQuickDistance(testSite.getLocation());
				boolean skip;
				double mag = rup.getMag();
				if (dist < func.getMinX()) {
					// closer than the smallest cutoff, don't skip
					skip = false;
				} else if (dist > func.getMaxX()) {
					// further than the largest cutoff, always skip
					skip = true;
				} else {
					double magThresh = func.getInterpolatedY(dist);
					// skip if the magnitude is less than the distance-dependent threshold
					skip = mag < magThresh;
				}
				assertEquals(skip, filter.canSkipRupture(rup, testSite));
			}
		}
		return doTestHazardCalc();
	}
	
	@Test
	public void testCalcWithoutFilters() {
		disableAllFilters();
		checkEnabledFitlers();
		int num = doTestHazardCalc();
		int numRups = 0;
		for (ProbEqkSource source : erf)
			numRups += source.getNumRuptures();
		assertEquals("All filters were disabled but some ruptures were still skipped?", numRups, num);
	}
	
	private void checkEnabledFitlers(SourceFilter... filters) {
		List<SourceFilter> reportedFitlers = filterManager.getEnabledFilters();
		if (filters == null || filters.length == 0) {
			assertTrue("No filters should be enabled", reportedFitlers == null || reportedFitlers.isEmpty());
		} else {
			assertEquals("Expected "+filters.length+" filters, but "+reportedFitlers.size()+" were enabled",
					filters.length, reportedFitlers.size());
			for (SourceFilter filter : filters)
				assertTrue(reportedFitlers.contains(filter));
		}
	}
	
	private int doTestHazardCalc() {
		int numRups = doTestHazardCurveCalc();
		
		// test event set methods as well
		doTestAverageHazardEventSetCurveCalc();
		doTestHazardEventSetCurveCalc();
		doTestHazardEventSetExpNumCurveCalc();
		
		return numRups;
	}
	
	private int doTestHazardCurveCalc() {
		for (AccessTrackingPointEqkSource source : erf.sources)
			source.clearAccess();
		calc.getHazardCurve(xVals, testSite, testIMR, erf);
		return doCheckTestHazardCurveCalcVisited(true);
	}
	
	private void doTestAverageHazardEventSetCurveCalc() {
		for (AccessTrackingPointEqkSource source : erf.sources)
			source.clearAccess();
		calc.getAverageEventSetHazardCurve(xVals, testSite, testIMR, erf);
		doCheckTestHazardCurveCalcVisited(false);
	}
	
	private void doTestHazardEventSetCurveCalc() {
		for (AccessTrackingPointEqkSource source : erf.sources)
			source.clearAccess();
		List<EqkRupture> events = erf.drawRandomEventSet(testSite, filterManager.getEnabledFilters());
		calc.getEventSetHazardCurve(xVals, testSite, testIMR, events, false);
		doCheckTestHazardCurveCalcVisited(false);
	}
	
	private void doTestHazardEventSetExpNumCurveCalc() {
		for (AccessTrackingPointEqkSource source : erf.sources)
			source.clearAccess();
		List<EqkRupture> events = erf.drawRandomEventSet(testSite, filterManager.getEnabledFilters());
		calc.getEventSetExpNumExceedCurve(xVals, testSite, testIMR, events, false);
		doCheckTestHazardCurveCalcVisited(false);
	}
	
	private int doCheckTestHazardCurveCalcVisited(boolean forceAllIncludcedRups) {
		List<SourceFilter> filters = filterManager.getEnabledFilters();
		int rupsIncluded = 0;
		for (AccessTrackingPointEqkSource source : erf.sources) {
			if (filters == null || filters.isEmpty()) {
				assertTrue("There are no filters enabled, but source wasn't accessed?", source.hasSourceBeenAccessed());
				for (AccessTrackingPointEqkRup rup : source.rups) {
					if (forceAllIncludcedRups) {
						assertTrue("There are no filters enabled, but rupture wasn't accessed?", rup.hasRupBeenAccessed());
						rupsIncluded++;
					} else if (rup.hasRupBeenAccessed()) {
						rupsIncluded++;
					}
				}
			} else {
				boolean canSkipSource = false;
				double dist = source.getMinDistance(testSite);
				for (SourceFilter filter : filters) {
					if (filter.canSkipSource(source, testSite, dist)) {
						canSkipSource = true;
						break;
					}
				}
				if (canSkipSource) {
					assertFalse("Source should have been skipped in hazard calculation, but was accessed", source.hasSourceBeenAccessed());
					for (AccessTrackingPointEqkRup rup : source.rups)
						assertFalse("source was skipped, but a rupture was still accessed", rup.hasRupBeenAccessed());
				} else {
					assertTrue("Source shouldn't have been skipped in hazard calculation, but wasn't accessed", source.hasSourceBeenAccessed());
					for (AccessTrackingPointEqkRup rup : source.rups) {
						boolean canSkipRup = false;
						for (SourceFilter filter : filters) {
							if (filter.canSkipRupture(rup, testSite)) {
								canSkipRup = true;
								break;
							}
						}
						if (canSkipRup) {
							assertFalse("Rupture should have been skipped in hazard calculation, but was accessed", rup.hasRupBeenAccessed());
						} else if (forceAllIncludcedRups) {
							assertTrue("Rupture shouldn't have been skipped in hazard calculation, but wasn't accessed", rup.hasRupBeenAccessed());
							rupsIncluded++;
						} else if (rup.hasRupBeenAccessed()) {
							rupsIncluded++;
						}
					}
				}
			}
		}
		int rawIncluded = 0;
		for (AccessTrackingPointEqkSource source : erf.sources)
			for (AccessTrackingPointEqkRup rup : source.rups)
				if (rup.hasRupBeenAccessed())
					rawIncluded++;
		assertEquals(rawIncluded, rupsIncluded);
		if (rupsIncluded > 0)
			assertTrue("We had "+rupsIncluded+" included rups, but hazard curve is all zero", xVals.calcSumOfY_Vals() > 0d);
		else
			assertTrue("We had no included rups, but hazard curve is nonzero: "+xVals.calcSumOfY_Vals(), xVals.calcSumOfY_Vals() == 0d);
		return rupsIncluded;
	}

}
