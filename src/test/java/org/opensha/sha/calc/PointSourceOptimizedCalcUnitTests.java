package org.opensha.sha.calc;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Objects;

import org.junit.Assume;
import org.junit.Test;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.impl.DoubleParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.commons.util.interp.DistanceInterpolator;
import org.opensha.sha.calc.AbstractPointSourceOptimizedCalc.IMRPointSourceDistanceCache;
import org.opensha.sha.calc.AbstractPointSourceOptimizedCalc.UniqueIMR;
import org.opensha.sha.calc.AbstractPointSourceOptimizedCalc.UniqueIMT;
import org.opensha.sha.calc.AbstractPointSourceOptimizedCalc.UniquePointRupture;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.PointSource;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.PointSource.PoissonPointSource;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.util.FocalMech;
import org.opensha.sha.util.TectonicRegionType;

public class PointSourceOptimizedCalcUnitTests {

	private static final class TestCalc extends AbstractPointSourceOptimizedCalc {
		public UniqueIMR uniqueIMR(ScalarIMR imr) {
			return getUniqueIMR(imr);
		}
	}

	private static PoissonPointSource buildSimplePointSource(Location gridLoc, FocalMech mech) {
		GutenbergRichterMagFreqDist mfd = new GutenbergRichterMagFreqDist(5.05, 29, 0.1);
		mfd.setAllButTotMoRate(mfd.getMinX(), mfd.getMaxX(), 1d, 1d);
		return PointSource.poissonBuilder(gridLoc)
				.surfaceBuilder(org.opensha.sha.earthquake.rupForecastImpl.PointSourceNshm.SURF_BUILDER_DEFAULT)
				.forMFDAndFocalMech(mfd, mech.mechanism, TectonicRegionType.ACTIVE_SHALLOW)
				.duration(1d)
				.build();
	}

	private static ScalarIMR newASK_IMR(Site site) {
		ScalarIMR imr = AttenRelRef.WRAPPED_ASK_2014.get();
		imr.setIntensityMeasure(PGA_Param.NAME);
		site.addParameterList(imr.getSiteParams());
		imr.setSite(site);
		return imr;
	}

	private static ScalarIMR newBSSA_IMR(Site site) {
		ScalarIMR imr = AttenRelRef.WRAPPED_BSSA_2014.get();
		imr.setIntensityMeasure(PGA_Param.NAME);
		site.addParameterList(imr.getSiteParams());
		imr.setSite(site);
		return imr;
	}

	private static EvenlyDiscretizedFunc newLogXVals() {
		return new EvenlyDiscretizedFunc(-5d, 1d, 50);
	}

	@Test
	public void testUniquePointRuptureChangesWithMagDipDepthsAndLength() throws Exception {
		Location gridLoc = new Location(0d, 0d);
		ProbEqkRupture pr = buildSimplePointSource(gridLoc, FocalMech.STRIKE_SLIP).getRupture(0);

		EqkRupture rup = pr;
		UniquePointRupture u0 = new UniquePointRupture(rup);

		// Copy the point surface and perturb rupture parameters
		assertTrue("Expected a PointSurface for point sources", rup.getRuptureSurface() instanceof PointSurface);
		PointSurface ps = ((PointSurface)rup.getRuptureSurface()).copyShallow();

		// 1) dip
		double dip0 = ps.getAveDip();
		double dip1 = Double.isFinite(dip0) ? dip0 - 1d : 45d;
		ps.setAveDip(dip1);

		// 2) depths (top/bottom)
		double zTop0 = ps.getAveRupTopDepth();
		double zBot0 = ps.getAveRupBottomDepth();
		// ensure valid: lower >= upper
		double zTop1 = zTop0 + 0.1;
		double zBot1 = Math.max(zBot0 + 0.1, zTop1);
		ps.setDepths(zTop1, zBot1);

		// 3) length
		double len0 = ps.getAveLength();
		ps.setAveLength(len0 + 0.5);

		// 4) magnitude
		double mag1 = rup.getMag() + 0.01;

		EqkRupture changed = new EqkRupture(mag1, rup.getAveRake(), ps, rup.getHypocenterLocation());
		UniquePointRupture u1 = new UniquePointRupture(changed);

		assertNotEquals("UniquePointRupture should change when mag/dip/depths/length change", u0, u1);
	}

	@Test
	public void testUniquePointRuptureChangesWithHypocenterDepth() {
		Location gridLoc = new Location(0d, 0d);
		ProbEqkRupture pr = buildSimplePointSource(gridLoc, FocalMech.STRIKE_SLIP).getRupture(0);

		EqkRupture rup = pr;
		Location hypo = rup.getHypocenterLocation();
		Assume.assumeTrue("Need a hypocenter to test zHyp differences", hypo != null);

		UniquePointRupture u0 = new UniquePointRupture(rup);

		Location deeperHypo = new Location(hypo.getLatitude(), hypo.getLongitude(), hypo.getDepth() + 0.5);
		EqkRupture hypoChanged = new EqkRupture(rup.getMag(), rup.getAveRake(), rup.getRuptureSurface(), deeperHypo);
		UniquePointRupture u1 = new UniquePointRupture(hypoChanged);

		assertNotEquals("UniquePointRupture should change when hypocenter depth changes", u0, u1);
	}

	@Test
	public void testCacheNotSharedBetweenDifferentIMRs_ASKvsBSSA() {
		TestCalc calc = new TestCalc();

		Location gridLoc = new Location(0d, 0d);
		ProbEqkRupture rup = buildSimplePointSource(gridLoc, FocalMech.STRIKE_SLIP).getRupture(0);
		UniquePointRupture uRup = new UniquePointRupture(rup);

		Site site = new Site(new Location(0d, 0d));
		ScalarIMR ask = newASK_IMR(site);
		ScalarIMR bssa = newBSSA_IMR(site);

		UniqueIMR uASK = calc.uniqueIMR(ask);
		UniqueIMR uBSSA = calc.uniqueIMR(bssa);
		assertNotEquals(uASK, uBSSA);

		UniqueIMT imtASK = new UniqueIMT(ask, true);
		UniqueIMT imtBSSA = new UniqueIMT(bssa, true);

		IMRPointSourceDistanceCache<Object> cache = calc.new IMRPointSourceDistanceCache<>(size -> new Object[size]);

		Object[] a1 = cache.getCached(uASK, imtASK, uRup);
		Object[] b1 = cache.getCached(uBSSA, imtBSSA, uRup);

		assertNotSame("Distance caches should not be shared across different IMRs", a1, b1);
	}

	@Test
	public void testSingleCacheSharedAcrossMultipleInstancesOfSameIMRWhenParamsMatch() {
		TestCalc calc = new TestCalc();

		Location gridLoc = new Location(0d, 0d);
		ProbEqkRupture rup = buildSimplePointSource(gridLoc, FocalMech.STRIKE_SLIP).getRupture(0);
		UniquePointRupture uRup = new UniquePointRupture(rup);

		Site site = new Site(new Location(0d, 0d));
		ScalarIMR ask1 = newASK_IMR(site);
		ScalarIMR ask2 = newASK_IMR(site);

		UniqueIMR u1 = calc.uniqueIMR(ask1);
		UniqueIMR u2 = calc.uniqueIMR(ask2);
		assertEquals("UniqueIMR should match for same IMR type/name", u1, u2);

		UniqueIMT imt1 = new UniqueIMT(ask1, true);
		UniqueIMT imt2 = new UniqueIMT(ask2, true);
		assertEquals("UniqueIMT should match for same IMT setup", imt1, imt2);

		IMRPointSourceDistanceCache<Object> cache = calc.new IMRPointSourceDistanceCache<>(size -> new Object[size]);
		Object[] arr1 = cache.getCached(u1, imt1, uRup);
		Object[] arr2 = cache.getCached(u2, imt2, uRup);

		assertSame("Cache array should be shared for identical UniqueIMR/UniqueIMT/UniqueRupture keys", arr1, arr2);
	}

	@Test
	public void testNewIMRInstanceWithDifferentParamsThrows() {
		TestCalc calc = new TestCalc();

		Site site = new Site(new Location(0d, 0d));
		ScalarIMR askRef = newASK_IMR(site);

		// establish reference parameterization
		calc.uniqueIMR(askRef);

		// new instance of same IMR type/name but with at least one changed tracked parameter
		ScalarIMR askOther = newASK_IMR(site);
		boolean changed = ParamMutators.mutateOneTrackedParam(askOther);
		Assume.assumeTrue("Could not mutate a tracked parameter for this IMR; skipping", changed);

		try {
			calc.uniqueIMR(askOther);
//			System.out.println("Exception exptected, params:");
//			for (Parameter<?> param : askRef.getOtherParams()) {
//				Parameter<?> other = askOther.getParameter(param.getName());
//				System.out.println(param.getName()+":\t'"+param.getValue()+"'\t'"+other.getValue()+"'");
//			}
//			for (Parameter<?> param : askRef.getSiteParams()) {
//				Parameter<?> other = askOther.getParameter(param.getName());
//				System.out.println(param.getName()+":\t'"+param.getValue()+"'\t'"+other.getValue()+"'");
//			}
			fail("Expected IllegalStateException due to mismatched IMR parameterization across instances");
		} catch (IllegalStateException expected) {
			// ok
		}
	}

	@Test
	public void testOptimizedEqualsBasicAtExactDistanceBins_TightTolerance() {
		Location gridLoc = new Location(0d, 0d);
		PoissonPointSource ps = buildSimplePointSource(gridLoc, FocalMech.STRIKE_SLIP);
		ProbEqkRupture rup = ps.getRupture(0);

		DistanceInterpolator di = DistanceInterpolator.get();
		int n = di.size();
		assertTrue("DistanceInterpolator should have at least a few bins", n > 5);

		RuptureExceedProbCalculator basicCalc = RuptureExceedProbCalculator.BASIC_IMPLEMENTATION;
		RuptureExceedProbCalculator optCalc = new PointSourceOptimizedExceedProbCalc();

		EvenlyDiscretizedFunc xVals = newLogXVals();

		// test a few bins across the range
		int[] idxs = new int[] { 0, n/4, n/2, (3*n)/4, n-1 };

		for (int idx : idxs) {
			double dist = di.getDistance(idx);

			Site site = new Site(LocationUtils.location(gridLoc, 0d, dist));
			ScalarIMR ask = newASK_IMR(site);

			EvenlyDiscretizedFunc basic = xVals.deepClone();
			EvenlyDiscretizedFunc opt = xVals.deepClone();

			basicCalc.getExceedProbabilities(ask, rup, basic);
			optCalc.getExceedProbabilities(ask, rup, opt);

			for (int i=0; i<basic.size(); i++)
				assertEquals("Bin idx=" + idx + " i=" + i, basic.getY(i), opt.getY(i), 1e-12);
		}
	}

	private static final class ParamMutators {

		static boolean mutateOneTrackedParam(ScalarIMR imr) {
			// Tracked params per UniqueIMR_Parameterization: other + site params.
			// Try other params first (more likely to exist on all IMRs), then site params.
			if (mutateOneParamInList(imr.getOtherParams()))
				return true;
			return mutateOneParamInList(imr.getSiteParams());
		}

		private static boolean mutateOneParamInList(ParameterList list) {
			for (Parameter<?> p : list) {
				if (p == null || p.getValue() == null)
					continue;

				// DoubleParameter with DoubleConstraint
				if (p instanceof DoubleParameter) {
					DoubleParameter dp = (DoubleParameter)p;
					if (dp.getConstraint() instanceof DoubleConstraint) {
						DoubleConstraint dc = (DoubleConstraint)dp.getConstraint();
						Double min = dc.getMin();
						Double max = dc.getMax();
						if (min == null || max == null)
							continue;
						double oldVal = dp.getValue();
						double newVal = pickDifferentDouble(oldVal, min, max);
						try {
							dp.setValue(newVal);
							return !Objects.equals(oldVal, dp.getValue());
						} catch (Exception e) {
							// not settable
						}
					}
				}

				// StringParameter with StringConstraint
				if (p instanceof StringParameter) {
					StringParameter sp = (StringParameter)p;
					if (sp.getConstraint() instanceof StringConstraint) {
						StringConstraint sc = (StringConstraint)sp.getConstraint();
						List<String> allowed = sc.getAllowedStrings();
						if (allowed == null || allowed.size() < 2)
							continue;
						String oldVal = sp.getValue();
						String newVal = pickDifferentString(oldVal, allowed);
						try {
							sp.setValue(newVal);
							return !Objects.equals(oldVal, sp.getValue());
						} catch (Exception e) {
							// not settable
						}
					}
				}
			}
			return false;
		}

		private static double pickDifferentDouble(double oldVal, double min, double max) {
			// Choose a deterministic alternate in-range value, avoiding endpoints.
			double mid = 0.5 * (min + max);
			double alt = mid;

			// If old is already (nearly) mid, move 25% toward max (or min if degenerate)
			if (Double.compare(oldVal, alt) == 0) {
				alt = min + 0.75 * (max - min);
				if (Double.compare(oldVal, alt) == 0)
					alt = min + 0.25 * (max - min);
			}

			// Clamp just in case
			if (alt < min) alt = min;
			if (alt > max) alt = max;
			return alt;
		}

		private static String pickDifferentString(String oldVal, List<String> allowed) {
			// Deterministic: choose first allowed string that differs.
			for (String s : allowed)
				if (!Objects.equals(s, oldVal))
					return s;
			// Should be unreachable if allowed.size() >= 2
			return allowed.get(0);
		}
	}
}
