package org.opensha.commons.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Utilities for converting between UTC calendar years and epoch times. Years are numbered continuously across the
 * BCE/CE boundary: year 1 is 1 CE, year 0 is 1 BCE, year -1 is 2 BCE, and so on.
 */
public final class TimeUtils {

	private TimeUtils() {}

	/**
	 * Returns midnight UTC on January 1 of the supplied year. Year zero is 1 BCE, year -1 is 2 BCE, and so on.
	 */
	public static long yearToEpochMillis(int year) {
		return LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
	}

	/**
	 * Returns the calendar year containing the supplied UTC epoch-millisecond instant, using year zero for 1 BCE and
	 * negative values for earlier years.
	 */
	public static int epochMillisToYear(long epochMillis) {
		return Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).getYear();
	}
}
