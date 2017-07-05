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
 * This c_ETAS_MinTimeParam is used for setting the minimum time value
 * (c) in the ETAS temporal decay: (t+c)^-p.  
 * The definition and values are based on Hardebeck 
 * (2013; http://pubs.usgs.gov/of/2013/1165/pdf/ofr2013-1165_appendixS.pdf), except
 * units are converted from years to days here.
 */
public class ETAS_MinTimeParam_c extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "c - ETAS Min Time";
	public final static String INFO = "The ETAS c value in the temporal decay: (t+c)^-p";
	private static final String UNITS = "days";
	protected final static Double MIN = new Double(1.00E-6*365.25);
	protected final static Double MAX = new Double(3.16E-4*365.25);
	public final static Double DEFAULT_VALUE = new Double(1.78E-05*365.25);
	

	/**
	 * This sets the default value as given.
	 */
	public ETAS_MinTimeParam_c(double defaultValue) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public ETAS_MinTimeParam_c() { this(DEFAULT_VALUE);}
	
	
}
