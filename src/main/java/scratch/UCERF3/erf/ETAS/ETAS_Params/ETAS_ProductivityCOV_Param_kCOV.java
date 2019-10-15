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

package scratch.UCERF3.erf.ETAS.ETAS_Params;

import org.opensha.commons.param.impl.DoubleParameter;

/**
 * This k_ProductivityCOV_Param is used for setting the productivity 
 * COV of the ETAS model, assuming a log-normal distribution with a mean of 1.0
 * (the latter to avoid biased rates).  This allows aleatory variability in productivity.
 * 
 */
public class ETAS_ProductivityCOV_Param_kCOV extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "k - ETAS Productivity COV";
	public final static String INFO = "The ETAS productivity COV parameter (assuming lon-normal distribution)";
	private static final String UNITS = null;
	public final static Double MIN = 0.0;
	public final static Double MAX = 2.0;	
	public final static Double DEFAULT_VALUE = 0.0;	

	/**
	 * This sets the default value as given.
	 */
	public ETAS_ProductivityCOV_Param_kCOV(double defaultValue) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public ETAS_ProductivityCOV_Param_kCOV() { this(DEFAULT_VALUE);}
	
	
}
