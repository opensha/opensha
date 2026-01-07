package org.opensha.sha.calc;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.util.interp.DistanceInterpolator;
import org.opensha.commons.util.interp.DistanceInterpolator.QuickInterpolator;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PeriodParam;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

import com.google.common.base.Preconditions;

public class PointSourceOptimizedSpectraCalcTest {

	private RuptureSpectraCalculator basic;
	private RuptureSpectraCalculator optimized;

	private ScalarIMR imr;
	private Location sourceLoc;
	private EqkRupture rup;

	private EvenlyDiscretizedFunc xVals50;
	private PeriodParam periodParam;
	private List<Double> periods;

	@Before
	public void setUp() {
		basic = RuptureSpectraCalculator.BASIC_IMPLEMENTATION;
		optimized = new PointSourceOptimizedSpectraCalc();

		imr = AttenRelRef.WRAPPED_ASK_2014.get();

		sourceLoc = new Location(0d, 0d, 5d);
		PointSurface surf = new PointSurface(sourceLoc);
		rup = new EqkRupture(6.5, 0d, surf, null);

		Site site = new Site(LocationUtils.location(sourceLoc, 0d, 12.34));
		site.addParameterList(imr.getSiteParams());
		imr.setSite(site);

		imr.setIntensityMeasure(SA_Param.NAME);
		periodParam = (PeriodParam)imr.getIntensityMeasure().getIndependentParameter(PeriodParam.NAME);
		periods = new ArrayList<>(periodParam.getAllowedDoubles());

		xVals50 = new EvenlyDiscretizedFunc(-5d, 1d, 50);
	}

	@Test
	public void testSpectrumMatchesBasic_InterpolatedDistance() {
		double iml = -1d;

		DiscretizedFunc basicSpec = basic.getSA_ExceedProbSpectrum(imr, rup, iml);
		DiscretizedFunc optSpec = optimized.getSA_ExceedProbSpectrum(imr, rup, iml);

		PointSourceOptimizedExceedProbCalcTest.assertSameFunc(basicSpec, optSpec, 1e-3, 5e-3);
	}

	@Test
	public void testSpectrumMatchesBasic_DiscreteDistanceBin() {
		// Force site distance to exactly hit a DistanceInterpolator bin, so qi.isDiscrete() is true.
		// The optimized spectra code currently creates ret but only fills it in the non-discrete path,
		// so this test should fail until that branch copies cached1 into ret. :contentReference[oaicite:9]{index=9}
		DistanceInterpolator interp = DistanceInterpolator.get();
		double discreteDist = interp.getDistance(interp.getIndexAtOrBefore(12.34));

		imr.getSite().setLocation(LocationUtils.location(sourceLoc, 0d, discreteDist));

		double iml = -1d;
		DiscretizedFunc basicSpec = basic.getSA_ExceedProbSpectrum(imr, rup, iml);
		DiscretizedFunc optSpec = optimized.getSA_ExceedProbSpectrum(imr, rup, iml);
		
		System.out.println("BASIC\n"+basicSpec+"\nOPTIMIZED\n"+optSpec);

		PointSourceOptimizedExceedProbCalcTest.assertSameFunc(basicSpec, optSpec, 1e-3, 5e-3);
	}

	@Test(expected = IllegalStateException.class)
	public void testMultiPeriodThrowsIfXValsDifferFromCached() {
		// First call caches (for first distance bin touched)
		DiscretizedFunc[] curve50 = buildCurves(xVals50, periods.size());
		optimized.getMultiPeriodExceedProbabilities(imr, periodParam, periods, rup, curve50);

		// Second call: same IMR/IMT/rup but different x values => must throw via quickAssertSameXVals :contentReference[oaicite:10]{index=10}
		EvenlyDiscretizedFunc xVals51 = new EvenlyDiscretizedFunc(-5d, 1d, 51);
		DiscretizedFunc[] curve51 = buildCurves(xVals51, periods.size());
		optimized.getMultiPeriodExceedProbabilities(imr, periodParam, periods, rup, curve51);
	}

	@Test
	public void testRestoresSiteLocationAfterCacheMissComputation_MultiPeriod() {
		Location orig = imr.getSite().getLocation();

		DiscretizedFunc[] curves = buildCurves(xVals50, periods.size());
		optimized.getMultiPeriodExceedProbabilities(imr, periodParam, periods, rup, curves);

		Location after = imr.getSite().getLocation();
		assertTrue("Site location should be restored after calculation",
				locationsEqual(orig, after));
	}

	@Test
	public void testDetectsIMRParameterChangeAndThrowsRatherThanUsingStaleCache_Spectra() {
		// populate cache
		DiscretizedFunc[] curves = buildCurves(xVals50, periods.size());
		optimized.getMultiPeriodExceedProbabilities(imr, periodParam, periods, rup, curves);

		boolean changed = changeOneIMRParamToDifferentValue(imr);
		Preconditions.checkState(changed,
				"Could not find a mutable IMR parameter to change for this IMR in this environment");

		DiscretizedFunc[] curves2 = buildCurves(xVals50, periods.size());
		try {
			optimized.getMultiPeriodExceedProbabilities(imr, periodParam, periods, rup, curves2);
			fail("Expected IllegalStateException due to IMR parameter change detection");
		} catch (IllegalStateException expected) {
			// expected
		}
	}

	private static DiscretizedFunc[] buildCurves(EvenlyDiscretizedFunc x, int n) {
		DiscretizedFunc[] ret = new DiscretizedFunc[n];
		for (int i=0; i<n; i++)
			ret[i] = x.deepClone();
		return ret;
	}

	private static boolean locationsEqual(Location a, Location b) {
		return a != null && b != null
				&& Double.doubleToLongBits(a.getLatitude()) == Double.doubleToLongBits(b.getLatitude())
				&& Double.doubleToLongBits(a.getLongitude()) == Double.doubleToLongBits(b.getLongitude())
				&& Double.doubleToLongBits(a.getDepth()) == Double.doubleToLongBits(b.getDepth());
	}

	private static boolean changeOneIMRParamToDifferentValue(ScalarIMR imr) {
		ParameterList[] lists = { imr.getOtherParams(), imr.getSiteParams() };

		for (ParameterList list : lists) {
			for (Parameter<?> p : list) {
				if (p == null)
					continue;

				Object oldVal = p.getValue();
				ParameterConstraint<?> c = p.getConstraint();
				if (oldVal instanceof Double && c instanceof DoubleConstraint) {
					DoubleConstraint dc = (DoubleConstraint)c;
					Double min = dc.getMin();
					Double max = dc.getMax();
					if (min == null || max == null)
						continue;

					double candidate = (min + max) * 0.5;
					if (Objects.equals(oldVal, candidate)) {
						candidate = min + 0.25*(max - min);
					}
					try {
						@SuppressWarnings("unchecked")
						Parameter<Double> dp = (Parameter<Double>)p;
						dp.setValue(candidate);
						return !Objects.equals(oldVal, dp.getValue());
					} catch (Exception e) {
						// keep searching
					}
				}
			}
		}
		return false;
	}
}
