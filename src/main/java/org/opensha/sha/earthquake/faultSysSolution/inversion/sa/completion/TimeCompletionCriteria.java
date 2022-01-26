package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria.EstimationCompletionCriteria;

public class TimeCompletionCriteria implements EstimationCompletionCriteria {
	
	private long millis;
	
	/**
	 * Creates a TimeCompletionCriteria that will be statisfied after the given number of miliseconds.
	 * 
	 * @param milis
	 */
	public TimeCompletionCriteria(long millis) {
		this.millis = millis;
	}

	@Override
	public boolean isSatisfied(InversionState state) {
		return state.elapsedTimeMillis >= millis;
	}
	
	@Override
	public String toString() {
		return "TimeCompletionCriteria(milis: "+millis+" = "+(float)(millis / 1000d)+" seconds)";
	}
	
	public long getMillis() {
		return millis;
	}
	
	public static TimeCompletionCriteria getInSeconds(long secs) {
		return new TimeCompletionCriteria(secs * 1000);
	}
	
	public static TimeCompletionCriteria getInMinutes(long mins) {
		return getInSeconds(mins * 60);
	}
	
	public static TimeCompletionCriteria getInHours(long hours) {
		return getInMinutes(hours * 60);
	}
	
	public String getTimeStr() {
		return getTimeStr(millis);
	}
	
	public static String getTimeStr(long millis) {
		if (millis % 1000 != 0)
			return millis+"mi";
		long secs = millis / 1000;
		if (secs % 60 != 0)
			return secs+"s";
		long mins = secs / 60;
		if (mins % 60 != 0)
			return mins+"m";
		long hours = mins / 60;
		return hours+"h";
	}
	
	public static TimeCompletionCriteria fromTimeString(String str) {
		return new TimeCompletionCriteria(parseTimeString(str));
	}
	
	public static long parseTimeString(String str) {
		if (str.endsWith("h")) {
			str = str.substring(0, str.length()-1);
			return Long.parseLong(str) * 60l * 60l * 1000l;
		} else if (str.endsWith("m")) {
			str = str.substring(0, str.length()-1);
			return Long.parseLong(str) * 60l * 1000l;
		} else if (str.endsWith("s")) {
			str = str.substring(0, str.length()-1);
			return Long.parseLong(str) * 1000l;
		}
		// just do millis
		if (str.endsWith("mi"))
			str = str.substring(0, str.length()-2);
		return Long.parseLong(str);
	}

	@Override
	public double estimateFractCompleted(InversionState state) {
		return Math.min(1d, (double)state.elapsedTimeMillis/(double)millis);
	}

	@Override
	public long estimateTimeLeft(InversionState state) {
		return Long.max(0, millis-state.elapsedTimeMillis);
	}

}
