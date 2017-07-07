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

package org.opensha.sha.gcim.imr.param.IntensityMeasureParams;

import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.commons.param.impl.WarningDoubleParameter;

/**
 * This constitutes the natural-log 5-75% significant duration intensity measure
 * parameter, defined as the time interval over which 5% to 75% of the total arias intensity
 * of the ground motion is accumulated.  Formally, 
 * 			Ds575 = t75 - t5; where
 * 			ta is defined so that I=integral(a(t)^2) from t=0 to t=ta is equal to a% of the total 
 * 			arias intensity Ia = integral(a(t)^2) from t=0 to t=tmax.
 * 
 * Unlike the 5-95% Significant duration (see Ds595) this definition is intended to primarily capture
 * only the duration of the body waves (and not surface waves).
 *
 * See constructors for info on editability and default values.
 * @author Brendon Bradley Oct 2010
 *
 */
public class Ds575_Param extends WarningDoubleParameter {

    public final static String NAME = "Ds575"; 
    public final static String UNITS = "s"; 
    public final static String INFO = "Significant Duration (5-75%)"; 
    public final static Double MIN = new Double(Math.log(Double.MIN_VALUE)); 
    public final static Double MAX = new Double(Double.MAX_VALUE); 
    public final static Double DEFAULT_WARN_MIN = new Double(Math.log(Double.MIN_VALUE)); 
    public final static Double DEFAULT_WARN_MAX = new Double(Math.log(4.0));


	/**
	 * This uses the supplied warning constraint and default (both in natural-log space).
	 * The parameter is left as non editable
	 * @param warningConstraint
	 * @param defaultDs575
	 */
	public Ds575_Param(DoubleConstraint warningConstraint, double defaultDs575) {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    this.setInfo(INFO);
	    setWarningConstraint(warningConstraint);
	    setDefaultValue(defaultDs575);
	    setNonEditable();
	}
	
	/**
	 * This uses the DEFAULT_WARN_MIN and DEFAULT_WARN_MAX fields to set the
	 * warning constraint, and sets the default as Math.log(4.0) (the natural
	 * log of 4.0).
	 * The parameter is left as non editable
	 */
	public Ds575_Param() {
		super(NAME, new DoubleConstraint(MIN, MAX), UNITS);
		getConstraint().setNonEditable();
	    setInfo(INFO);
	    DoubleConstraint warn2 = new DoubleConstraint(DEFAULT_WARN_MIN, DEFAULT_WARN_MAX);
	    warn2.setNonEditable();
	    setWarningConstraint(warn2);
	    setDefaultValue(Math.log(4.0));
	    setNonEditable();
	}
}
