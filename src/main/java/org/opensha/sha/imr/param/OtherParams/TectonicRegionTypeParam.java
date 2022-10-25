package org.opensha.sha.imr.param.OtherParams;

import java.util.ArrayList;
import java.util.Collection;

import org.opensha.commons.param.constraint.impl.StringConstraint;
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

public class TectonicRegionTypeParam extends StringParameter {

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
	 * This no-argument constructor defaults to only the given types. The first one given is considered default 
	 * and sets the parameter as non editable.
	 */
	public TectonicRegionTypeParam(TectonicRegionType... types) {
		super(NAME);
		StringConstraint options = new StringConstraint();
		//options.setNullAllowed(true);
		for (TectonicRegionType trt : types)
			options.addString(trt.toString());
		setConstraint(options);
	    setInfo(INFO);
	    setDefaultValue(types[0].toString());
	    //setNonEditable();
	}

	/**
	 * This constructor will throw an exception if the options contain a non-allowed
	 * type (as represented by the TYPE_* fields here).  The parameter is set as non editable 
	 * after creation
	 * @param options
	 * @param defaultValue
	 */
	public TectonicRegionTypeParam(StringConstraint options, String defaultValue) {
		super(NAME, options);
		//options.setNullAllowed(true);
		// check that options are supported
		ArrayList<String> strings = options.getAllowedStrings();
		for(int i=0; i< strings.size();i++) {
			if (!TectonicRegionType.isValidType((String)strings.get(i))) throw new RuntimeException("Constraint type not supported by TectonicRegionTypeParam");
		}
	    setInfo(INFO);
	    setDefaultValue(defaultValue);
	    //setNonEditable();
	}
	
	private static StringConstraint toStringConst(Collection<TectonicRegionType> options) {
		StringConstraint sconst = new StringConstraint();
		for (TectonicRegionType trt : options) {
			sconst.addString(trt.toString());
		}
		//sconst.setNonEditable();
		return sconst;
	}
	
	public TectonicRegionTypeParam(Collection<TectonicRegionType> options, TectonicRegionType defaultValue) {
		this(toStringConst(options), defaultValue.toString());
	}
	
	/**
	 * Convenience method for setting value directly from a <code>TectonicRegionType</code>.
	 * 
	 * Same as calling <code>setValue(trt.toString())</code>
	 * 
	 * @param trt - the <code>TectonicRegionType</code> to be set
	 */
	public void setValue(TectonicRegionType trt) {
		super.setValue(trt.toString());
	}
	
	/**
	 * Convenience method for getting the value as a <code>TectonicRegionType</code>
	 * 
	 * Same as calling <code>TectonicRegionType.getTypeForName(trtParam.getValue())</code>
	 * 
	 * @return
	 */
	public TectonicRegionType getValueAsTRT() {
		return TectonicRegionType.getTypeForName(getValue());
	}
	
	/**
	 * This checks whether a type is potentially supported by this class 
	 * (whether an instance could support it, as opposed to whether an instance
	 * does support it (the latter being controlled by the string constraint).
	 * @param option
	 * @return boolean
	 */
	public static boolean isTypePotentiallySupported(String option) {
		return TectonicRegionType.isValidType(option);
	}

}
