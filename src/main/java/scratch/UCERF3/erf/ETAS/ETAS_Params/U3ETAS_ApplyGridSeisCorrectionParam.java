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

import org.opensha.commons.param.impl.BooleanParameter;

/**
 * This U3ETAS_ApplySubSeisRatesWhenComputingSupraNucleationRatesParam tells whether 
 * supra-seismogenic nucleation rates should be proportional to the sub-seismogenic rate
 * within each fault subsection
  */
public class U3ETAS_ApplyGridSeisCorrectionParam extends BooleanParameter {
	

	private static final long serialVersionUID = 1L;
	public final static String NAME = "Apply SubSeis Rates to Supra Nucleation";
	public final static String INFO = "This tells whether supra-seismogenic nucleation rates should be proportional to sub-seismogenic rates within each fault subsection";
	public static final boolean DEFAULT = true;

	/**
	 * This sets the default value as given, and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public U3ETAS_ApplyGridSeisCorrectionParam(boolean defaultValue) {
		super(NAME);
		setInfo(INFO);
		setDefaultValue(defaultValue);
		setValue(defaultValue);	// this is needed for some reason
	}
	
	/**
	 * This sets the default value as "true", and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public U3ETAS_ApplyGridSeisCorrectionParam() {this(DEFAULT);}

}
