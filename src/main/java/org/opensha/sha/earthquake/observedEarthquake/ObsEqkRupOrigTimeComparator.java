package org.opensha.sha.earthquake.observedEarthquake;

import java.util.Comparator;

/**
 * <p>Title: ObsEqkRupEventOriginTimeComparator</p>
 *
 * <p>Description: This compares 2 observed ObsEqkRupture objects
 * based on their origin time.
 * </p>
 *
 *
 * @author Nitin Gupta, rewritten by Ned Field
 * @version 1.0
 */
public class ObsEqkRupOrigTimeComparator
implements Comparator<ObsEqkRupture>, java.io.Serializable {

	/**
	 * Compares the origin times of the two arguments. Returns negative one, zero, or
	 * positive one depending on whether the first origin time is less than, 
	 * equal to, or greater than the second, respectively.
	 */
	public int compare(ObsEqkRupture rupEvent1, ObsEqkRupture rupEvent2) {
		return new Long(rupEvent1.getOriginTime()).compareTo(rupEvent2.getOriginTime());
	}

}
