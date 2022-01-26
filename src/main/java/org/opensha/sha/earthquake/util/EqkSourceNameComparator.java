package org.opensha.sha.earthquake.util;

import java.text.Collator;
import java.util.Comparator;

import org.opensha.sha.earthquake.ProbEqkSource;

public class EqkSourceNameComparator implements Comparator<ProbEqkSource> {
	// A Collator does string comparisons
	private Collator c = Collator.getInstance();
	
	/**
	 * This is called when you do Arrays.sort on an array or Collections.sort on a collection (IE ArrayList).
	 * 
	 * It simply compares their names using a Collator. 
	 */
	public int compare(ProbEqkSource s1, ProbEqkSource s2) {
		// let the Collator do the string comparison, and return the result
		return c.compare(s1.getName(), s2.getName());
	}

}
