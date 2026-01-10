package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class TimeDepUtils {
	
	/*
	 * Constants
	 */
	
	public final static double MILLISEC_PER_YEAR = 1000*60*60*24*365.25;
	public final static long MILLISEC_PER_DAY = 1000*60*60*24;
	
	/*
	 * Helper methods
	 */

	/**
	 * Using this avoids any problems associated with being in different time zones 
	 * (previously, time-since-last calculations could vary by hours depending on where 
	 * you were running the code from)
	 * @param year
	 * @return
	 */
	public static GregorianCalendar utcStartOfYear(int year) {
		TimeZone utc = TimeZone.getTimeZone("UTC");
	
		GregorianCalendar cal = new GregorianCalendar(utc);
		cal.clear(); // clears all fields to avoid locale/time leftovers
	
		cal.set(Calendar.YEAR, year);
		cal.set(Calendar.MONTH, Calendar.JANUARY);
		cal.set(Calendar.DAY_OF_MONTH, 1);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
	
		return cal;
	}
	
	/**
	 * This computes the poisson probability of one or more events for the given annual rate
	 * @param annualRate
	 * @param durationYears
	 * @return
	 */
	public static double rateToPoissonProb(double annualRate, double durationYears) {
		return 1.0-Math.exp(-annualRate*durationYears);
	}
	
	/**
	 * This computes the poisson probability of one or more events for the given recurrence interval in years
	 * @param recurIntevalYears
	 * @param durationYears
	 * @return
	 */
	public static double riToPoissonProb(double recurIntevalYears, double durationYears) {
		return 1.0-Math.exp(-durationYears/recurIntevalYears);
	}

}
