package org.opensha.commons.util;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.Test;

public class TimeUtilsTest {

	@Test
	public void testYearEpochMillisRoundTrip() {
		for (int year : new int[] { 2026, 1970, 1, 0, -1, -1450, -26050 })
			assertEquals(year, TimeUtils.epochMillisToYear(TimeUtils.yearToEpochMillis(year)));
	}

	@Test
	public void testEpochMillisToYearWithinYear() {
		long epochMillis = LocalDate.of(-1450, 7, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
		assertEquals(-1450, TimeUtils.epochMillisToYear(epochMillis));
	}
}
