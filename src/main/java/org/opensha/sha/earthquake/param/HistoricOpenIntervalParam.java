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
 * This HistoricOpenIntervalParam is used for setting the the time over which an event is
 * known to have not occurred.
 */
public class HistoricOpenIntervalParam extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Historic Open Interval";
	public final static String INFO = "Historic time interval over which event is known not to have occurred";
	private static final String UNITS = "Years";
	protected final static Double MIN = new Double(0d);
	protected final static Double MAX = new Double(1e6);
	

	/**
	 * This sets the default value as given.
	 */
	public HistoricOpenIntervalParam(double defaultStdDev) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultStdDev);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public HistoricOpenIntervalParam() { this(0.0);}
	
	
}
