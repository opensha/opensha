package org.opensha.sha.imr.param.OtherParams;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;

import org.opensha.commons.param.constraint.impl.EnumConstraint;
import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.sha.util.TectonicRegionType;

/**
 *  This Tectonic Region Type Param is a string representation of our TectonicRegionType enum
 *  (located in org.opensha.sha.util).  The options to be supported in any given instance are 
 *  supplied via the string constraint supplied in the constructor.  However, no options other
 *  than what's defined by the TectonicRegionType enum are allowed.
 *  See constructors for info on editability and default values.
 *  Note that this is not in the EqkRuptureParams directory because it will not
 *  be set from information in and EqkRupture object (the latter does not carry this info).
 */

public class TectonicRegionTypeParam extends EnumParameter<TectonicRegionType> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Tectonic Region";
	public final static String INFO = "Applicable Tectonic Region(s) - not strictly enforced";
	
	/**
	 * This no-argument constructor defaults to only Active Shallow Crust, 
	 * and sets the parameter as non editable.
	 */
	public TectonicRegionTypeParam() {
		this(TectonicRegionType.ACTIVE_SHALLOW);
	}
	
	/**
	 * This no-argument constructor sets the value to and allows only the supplied TRT
	 */
	public TectonicRegionTypeParam(TectonicRegionType trt) {
		this(EnumSet.of(trt), trt);
	}
	
	/**
	 * This no-argument constructor defaults to only the given types. The first one given is considered default 
	 * and sets the parameter as non editable.
	 */
	public TectonicRegionTypeParam(EnumSet<TectonicRegionType> choices, TectonicRegionType defaultValue) {
		super(NAME, choices, defaultValue, null);
	    setInfo(INFO);
	}
	
	public void setOptions(EnumSet<TectonicRegionType> trts) {
		setConstraint(new EnumConstraint<>(trts, false));
	}

}
