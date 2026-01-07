package org.opensha.sha.calc;

import static org.junit.Assert.*;

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
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

import com.google.common.base.Preconditions;

public class PointSourceOptimizedExceedProbCalcTest {

	private RuptureExceedProbCalculator basic;
	private RuptureExceedProbCalculator optimized;

	private ScalarIMR imr;
	private Location sourceLoc;
	private EqkRupture rup;

	private EvenlyDiscretizedFunc xVals50;

	@Before
	public void setUp() {
		basic = RuptureExceedProbCalculator.BASIC_IMPLEMENTATION;
		optimized = new PointSourceOptimizedExceedProbCalc();

		imr = AttenRelRef.WRAPPED_ASK_2014.get();

		// Simple point rupture
		sourceLoc = new Location(0d, 0d, 5d);
		PointSurface surf = new PointSurface(sourceLoc);
		rup = new EqkRupture(6.5, 0d, surf, null);

		// Site at an arbitrary distance; tests may overwrite location
		Site site = new Site(LocationUtils.location(sourceLoc, 0d, 12.34));
//		Site site = new Site(LocationUtils.location(sourceLoc, 0d, 13.3));
		site.addParameterList(imr.getSiteParams());
		imr.setSite(site);

		// Default IMT for these tests
		imr.setIntensityMeasure(PGA_Param.NAME);

		// ln(IML) X values
		xVals50 = new EvenlyDiscretizedFunc(-5d, 1d, 50);
	}

	@Test
	public void testMatchesBasicForPointSurface_InterpolatedDistance() {
		EvenlyDiscretizedFunc basicCurve = xVals50.deepClone();
		EvenlyDiscretizedFunc optCurve = xVals50.deepClone();

		basic.getExceedProbabilities(imr, rup, basicCurve);
		optimized.getExceedProbabilities(imr, rup, optCurve);

		assertSameFunc(basicCurve, optCurve, 1e-3, 5e-3);
	}

	@Test
	public void testRestoresSiteLocationAfterCacheMissComputation() {
		Location orig = imr.getSite().getLocation();

		// Force a cache miss by using a fresh calculator instance (done in setUp)
		EvenlyDiscretizedFunc curve = xVals50.deepClone();
		optimized.getExceedProbabilities(imr, rup, curve);

		// When the optimized path calculates, it temporarily changes site location and must restore it
		Location after = imr.getSite().getLocation();
		assertTrue("Site location should be restored after calculation",
				locationsEqual(orig, after));
	}

	@Test(expected = IllegalStateException.class)
	public void testThrowsIfXValsDifferFromCached() {
		EvenlyDiscretizedFunc curve50 = xVals50.deepClone();
		optimized.getExceedProbabilities(imr, rup, curve50);

		// Same rupture + IMR + IMT, but different x values => must throw
		EvenlyDiscretizedFunc curve51 = new EvenlyDiscretizedFunc(-5d, 1d, 51);
		optimized.getExceedProbabilities(imr, rup, curve51);
		// Guard comes from AbstractPointSourceOptimizedCalc.quickAssertSameXVals(...) :contentReference[oaicite:1]{index=1}
	}

	@Test
	public void testChangingSAPeriodIsAllowedAndUsesSeparateIMTKey() {
		imr.setIntensityMeasure(SA_Param.NAME);

		// Set SA period=1.0
		SA_Param.setPeriodInSA_Param(imr.getIntensityMeasure(), 1d);
		EvenlyDiscretizedFunc curve1 = xVals50.deepClone();
		optimized.getExceedProbabilities(imr, rup, curve1);

		// Change to SA period=2.0; for exceed-prob calc, UniqueIMT tracks SA period :contentReference[oaicite:2]{index=2}
		SA_Param.setPeriodInSA_Param(imr.getIntensityMeasure(), 2d);
		EvenlyDiscretizedFunc curve2 = xVals50.deepClone();
		optimized.getExceedProbabilities(imr, rup, curve2);

		// Not asserting they differ (could be close), just that no staleness exception is thrown.
	}

	@Test
	public void testDetectsIMRParameterChangeAndThrowsRatherThanUsingStaleCache() {
		EvenlyDiscretizedFunc curve = xVals50.deepClone();
		optimized.getExceedProbabilities(imr, rup, curve);

		boolean changed = changeOneIMRParamToDifferentValue(imr);
		Preconditions.checkState(changed,
				"Could not find a mutable IMR parameter to change for this IMR in this environment");

		EvenlyDiscretizedFunc curve2 = xVals50.deepClone();
		try {
			optimized.getExceedProbabilities(imr, rup, curve2);
			fail("Expected IllegalStateException due to IMR parameter change detection");
		} catch (IllegalStateException expected) {
			// expected: AbstractPointSourceOptimizedCalc.getUniqueIMR(...) checks param changes :contentReference[oaicite:3]{index=3}
		}
	}

	static void assertSameFunc(DiscretizedFunc a, DiscretizedFunc b, double absTol, double relTol) {
		assertEquals(a.size(), b.size());
		assertEquals(a.getMinX(), b.getMinX(), 0d);
		assertEquals(a.getMaxX(), b.getMaxX(), 0d);
		for (int i=0; i<a.size(); i++) {
			double da = a.getY(i);
			double db = b.getY(i);
			assertEquals("Mismatch at i="+i+" x="+a.getX(i), da, db, absTol);
			if (da > absTol*5) {
				double relDiff = Math.abs(da-db)/db;
				assertTrue("Relative mismatch at i="+i+" x="+a.getX(i)+", a="+da+", b="+db+", relDiff="+relDiff, relDiff <= relTol);
			}
		}
	}

	private static boolean locationsEqual(Location a, Location b) {
		// Location.equals() exists but may be strict; use component compare
		return a != null && b != null
				&& Double.doubleToLongBits(a.getLatitude()) == Double.doubleToLongBits(b.getLatitude())
				&& Double.doubleToLongBits(a.getLongitude()) == Double.doubleToLongBits(b.getLongitude())
				&& Double.doubleToLongBits(a.getDepth()) == Double.doubleToLongBits(b.getDepth());
	}

	/**
	 * Attempts to change one parameter in other/site params to a different allowed value.
	 * Returns true if it successfully changed a parameter.
	 */
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
					// ensure it differs
					if (Objects.equals(oldVal, candidate)) {
						candidate = min + 0.25*(max - min);
					}
					try {
						@SuppressWarnings("unchecked")
						Parameter<Double> dp = (Parameter<Double>)p;
						dp.setValue(candidate);
						return !Objects.equals(oldVal, dp.getValue());
					} catch (Exception e) {
						// not settable; keep searching
					}
				}
			}
		}
		return false;
	}
}
