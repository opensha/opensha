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

package org.opensha.sha.imr.param.PropagationEffectParams;

import java.util.ListIterator;

import org.dom4j.Element;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.param.WarningParameter;
import org.opensha.commons.param.constraint.ParameterConstraint;
import org.opensha.commons.param.constraint.impl.DoubleConstraint;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

/**
 * <b>Title:</b> DistanceJBParameter<p>
 *
 * <b>Description:</b> Special subclass of PropagationEffectParameter.
 * This finds the shortest distance to the surface projection of the fault.
 * <p>
 *
 * @see DistanceRupParameter
 * @see DistanceSeisParameter
 * @author Steven W. Rock
 * @version 1.0
 */
public class DistanceJBParameter extends AbstractDoublePropEffectParam {

	/** Class name used in debug strings */
	protected final static String C = "DistanceJBParameter";
	/** If true debug statements are printed out */
	protected final static boolean D = false;

	/** Hardcoded name */
	public final static String NAME = "DistanceJB";
	/** Hardcoded units string */
	private final static String UNITS = "km";
	/** Hardcoded info string */
	private final static String INFO = "Joyner-Boore Distance (closest distance to surface projection of fault)";
	/** Hardcoded min allowed value */
	private final static Double MIN = new Double(0.0);
	/** Hardcoded max allowed value */
	private final static Double MAX = new Double(Double.MAX_VALUE);


	/**
	 * No-Arg constructor that just calls init() with null constraints.
	 * All value are allowed.
	 */
	public DistanceJBParameter() {
		super(NAME);
		init();
	}
	
	/** This constructor sets the default value.  */
	public DistanceJBParameter(double defaultValue) { 
		super(NAME);
		init(); 
		this.setDefaultValue(defaultValue);
	}
	
	public DistanceJBParameter(DoubleConstraint warningConstraint, double defaultValue) { 
		super(NAME);
		init(warningConstraint); 
		this.setDefaultValue(defaultValue);
	}


	/** Initializes the constraints, name, etc. for this parameter */
	protected void init( DoubleConstraint warningConstraint){
		this.warningConstraint = warningConstraint;
		this.constraint = new DoubleConstraint(MIN,MAX);
		this.constraint.setNullAllowed(false);
		this.name = NAME;
		this.constraint.setName( this.name );
		this.units = UNITS;
		this.info = INFO;
		//setNonEditable();
	}

	/** Initializes the constraints, name, etc. for this parameter */
	protected void init() { init(null); }

	/**
	 * Note that this doesn't not throw a warning
	 */
	protected void calcValueFromSiteAndEqkRup(){
		if( ( site != null ) && ( eqkRupture != null ))
			setValueIgnoreWarning(eqkRupture.getRuptureSurface().getDistanceJB(site.getLocation()));
		else
			value = null;
	}

	
	/** This is used to determine what widget editor to use in GUI Applets.  */
	public String getType() {
		String type = "DoubleParameter";
		// Modify if constrained
		ParameterConstraint constraint = this.constraint;
		if (constraint != null) type = "Constrained" + type;
		return type;
	}


	/**
	 *  Returns a copy so you can't edit or damage the origial.<P>
	 *
	 * Note: this is not a true clone. I did not clone Site or ProbEqkRupture.
	 * PE could potentially have a million points, way to expensive to clone. Should
	 * not be a problem though because once the PE and Site are set, they can not
	 * be modified by this class. The clone has null Site and PE parameters.<p>
	 *
	 * This will probably have to be changed in the future once the use of a clone is
	 * needed and we see the best way to implement this.
	 *
	 * @return    Exact copy of this object's state
	 */
	public Object clone() {

		DoubleConstraint c1 = null;
		DoubleConstraint c2 = null;

		if( constraint != null ) c1 = ( DoubleConstraint ) constraint.clone();
		if( warningConstraint != null ) c2 = ( DoubleConstraint ) warningConstraint.clone();

		Double val = null, val2 = null;
		if( value != null ) {
			val = ( Double ) this.value;
			val2 = new Double( val.doubleValue() );
		}

		DistanceJBParameter param = new DistanceJBParameter(  );
		param.info = info;
		param.value = val2;
		param.constraint = c1;
		param.warningConstraint = c2;
		param.name = name;
		param.info = info;
		param.site = site;
		param.eqkRupture = eqkRupture;
		if( !this.editable ) param.setNonEditable();

		return param;

	}


	public boolean setIndividualParamValueFromXML(Element el) {
		// TODO Auto-generated method stub
		return false;
	}

}
