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
 * This U3ETAS_ApplyGridSeisCorrectionParam tells whether or not to correct 
 * gridded-seismicity rates to not be less than the expected rate of aftershocks
 * from supra-seismogenic events
 */
public class U3ETAS_ApplySubSeisRatesForSupraNucleationRatesParam extends BooleanParameter {
	

	private static final long serialVersionUID = 1L;
	public final static String NAME = "Apply Gridded Seis Correction";
	public final static String INFO = "This tells whether to correct gridded seismicity rates soas not to be less than the expected rate of aftershocks from supraseismogenic events";
	public static final boolean DEFAULT = true;

	/**
	 * This sets the default value as given, and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public U3ETAS_ApplySubSeisRatesForSupraNucleationRatesParam(boolean defaultValue) {
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
	public U3ETAS_ApplySubSeisRatesForSupraNucleationRatesParam() {this(DEFAULT);}

}
