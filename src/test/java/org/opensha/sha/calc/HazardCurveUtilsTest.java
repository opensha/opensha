package org.opensha.sha.calc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.TimeSpan.DurationUnits;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;

public class HazardCurveUtilsTest {

	private static ArbitrarilyDiscretizedFunc curve() {
		ArbitrarilyDiscretizedFunc curve = new ArbitrarilyDiscretizedFunc();
		curve.set(0.1, 0.20);
		curve.set(0.2, 0.10);
		curve.set(0.4, 0.02);
		curve.set(0.8, 0.005);
		return curve;
	}

	@Test
	public void testIMLInterpolationAndSaturation() {
		ArbitrarilyDiscretizedFunc curve = curve();
		assertEquals(0.4, HazardCurveUtils.getIML(curve, 0.02), 1e-14);
		assertEquals(0d, HazardCurveUtils.getIML(curve, 0.3), 0d);
		assertEquals(0.8, HazardCurveUtils.getIML(curve, 0.001), 0d);
	}

	@Test
	public void testFiftyYearReturnPeriodExtraction() {
		TimeSpan timeSpan = new TimeSpan(DurationUnits.YEARS);
		timeSpan.setDuration(50d);
		assertEquals(0.4, HazardCurveUtils.getIML(curve(), ReturnPeriod.TWO_IN_50, timeSpan), 1e-14);
	}
}
