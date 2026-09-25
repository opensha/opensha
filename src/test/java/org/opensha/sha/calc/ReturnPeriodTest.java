package org.opensha.sha.calc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.TimeSpan.DurationUnits;

public class ReturnPeriodTest {

	@Test
	public void testTwoInFiftyConversions() {
		assertEquals(0.02, ReturnPeriod.TWO_IN_50.getProbability(50d), 1e-15);
		assertEquals(ReturnPeriodUtils.calcExceedanceProb(0.02, 50d, 1d),
				ReturnPeriod.TWO_IN_50.getProbability(1d), 0d);
		assertEquals(50d/-Math.log1p(-0.02), ReturnPeriod.TWO_IN_50.getReturnPeriodYears(), 1e-10);
	}

	@Test
	public void testTimeSpanOverloadAndArbitraryDurationDefaults() {
		TimeSpan timeSpan = new TimeSpan(DurationUnits.DAYS);
		timeSpan.setDuration(30d*365.25d);
		ReturnPeriod[] defaults = ReturnPeriod.defaultsForCurveDuration(timeSpan);
		assertEquals(2, defaults.length);
		assertEquals("2% in 30 years", defaults[0].getLabel());
		assertEquals(0.02, defaults[0].getProbability(timeSpan), 1e-15);
		assertEquals("10% in 30 years", defaults[1].getLabel());
		assertEquals(0.10, defaults[1].getProbability(timeSpan), 1e-15);
	}

	@Test
	public void testConventionalDefaultsForOneAndFiftyYears() {
		TimeSpan oneYear = new TimeSpan(DurationUnits.YEARS);
		oneYear.setDuration(1d);
		assertEquals(ReturnPeriod.TWO_IN_50, ReturnPeriod.defaultsForCurveDuration(oneYear)[0]);

		TimeSpan fiftyYears = new TimeSpan(DurationUnits.YEARS);
		fiftyYears.setDuration(50d);
		assertEquals(ReturnPeriod.TWO_IN_50, ReturnPeriod.defaultsForCurveDuration(fiftyYears)[0]);
		assertEquals(0.02, ReturnPeriod.TWO_IN_50.getProbability(fiftyYears), 1e-15);
	}
}
