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
 * This n_ETAS_BranchingRatioParam is used for setting the fraction (n)
 * of ETAS earthquakes that are triggered (relative to all earthquakes).
 * The fraction of spontaneous earthquakes is therefore 1-n.
 * The definition and values are based on Hardebeck 
 * (2013; http://pubs.usgs.gov/of/2013/1165/pdf/ofr2013-1165_appendixS.pdf)
 */
public class ETAS_FractionSpontaneousParam extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "n - ETAS Fraction Spontaneous";
	public final static String INFO = "The fraction of ETAS earthquakes that are spontaneous (vs triggered)";
	private static final String UNITS = null;
	protected final static Double MIN = new Double(0.1);
	protected final static Double MAX = new Double(0.9);
	public final static Double DEFAULT_VALUE = new Double(0.33);
	

	/**
	 * This sets the default value as given.
	 */
	public ETAS_FractionSpontaneousParam(double defaultValue) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public ETAS_FractionSpontaneousParam() { this(DEFAULT_VALUE);}
	
	
}
