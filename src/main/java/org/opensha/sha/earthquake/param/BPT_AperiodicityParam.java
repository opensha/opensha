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
public class BPT_AperiodicityParam extends DoubleParameter {
	
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "BPT Aperiodicity";
	public final static String INFO = "Aperiodicity for BPT renewal model";
	private static final String UNITS = null;
	protected final static Double MIN = new Double(0.01);
	protected final static Double MAX = new Double(1.79);
	

	/**
	 * This sets the default value as given.
	 */
	public BPT_AperiodicityParam(double defaultAperiodicity) {
		super(NAME, MIN, MAX, UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    setDefaultValue(defaultAperiodicity);
	    setValueAsDefault();
	}

	/**
	 * This sets the default value as 0.
	 */
	public BPT_AperiodicityParam() { this(0.2);}
	
	
}
