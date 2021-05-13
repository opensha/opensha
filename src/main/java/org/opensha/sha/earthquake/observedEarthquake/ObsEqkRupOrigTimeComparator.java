/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

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
