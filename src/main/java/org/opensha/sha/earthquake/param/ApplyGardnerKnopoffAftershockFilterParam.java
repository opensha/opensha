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

import org.opensha.commons.param.impl.BooleanParameter;

/**
 * 
 */
public class ApplyGardnerKnopoffAftershockFilterParam extends BooleanParameter {
	

	private static final long serialVersionUID = 1L;
	public final static String NAME = "Apply Aftershock Filter";
	public final static String INFO = "Applies the Gardner Knopoff aftershock filter";

	/**
	 * This sets the default value as given, and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public ApplyGardnerKnopoffAftershockFilterParam(boolean defaultValue) {
		super(NAME);
		setInfo(INFO);
		setDefaultValue(defaultValue);
	}
	
	/**
	 * This sets the default value as "false", and leaves the parameter
	 * non editable.
	 * @param defaultValue
	 */
	public ApplyGardnerKnopoffAftershockFilterParam() {this(false);}

}
