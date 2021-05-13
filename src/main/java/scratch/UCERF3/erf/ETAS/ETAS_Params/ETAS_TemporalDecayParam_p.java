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
 * This p_ETAS_TemporalDecayParam is used for setting the decay 
 * value (p) in the ETAS temporal decay: (t+c)^-p.  
 * The definition and values are based on Hardebeck 
 * (2013; http://pubs.usgs.gov/of/2013/1165/pdf/ofr2013-1165_appendixS.pdf).
 */
public class ETAS_TemporalDecayParam_p extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "p - ETAS Temporal Decay";
	public final static String INFO = "The ETAS p value in the temporal decay: (t+c)^-p";
	private static final String UNITS = null;
	protected final static Double MIN = new Double(1.00);
	protected final static Double MAX = new Double(1.40);
	public final static Double DEFAULT_VALUE = new Double(1.07);

	/**
	 * This sets the default value as given.
	 */
	public ETAS_TemporalDecayParam_p(double defaultValue) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public ETAS_TemporalDecayParam_p() { this(DEFAULT_VALUE);}
	
	
}
