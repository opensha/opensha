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

package org.opensha.sha.earthquake.param;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * Aperiodicity for BPT renewal model.
 */
public class MaximumMagnitudeParam extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Maximum Magnitude";
	public final static String INFO = "The maximum magnitude for the region";
	private static final String UNITS = null;
	protected final static Double MIN = new Double(5.0);
	protected final static Double MAX = new Double(10.0);
	

	/**
	 * This sets the default value as given.
	 */
	public MaximumMagnitudeParam(double defaultMaxMag) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultMaxMag);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 8.3.
	 */
	public MaximumMagnitudeParam() { this(8.3);}
	
	
}
