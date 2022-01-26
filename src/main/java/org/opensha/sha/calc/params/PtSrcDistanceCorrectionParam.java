package org.opensha.sha.calc.params;

import org.opensha.commons.param.constraint.impl.StringConstraint;
import org.opensha.commons.param.impl.StringParameter;
import org.opensha.sha.faultSurface.utils.PtSrcDistCorr;
import org.opensha.sha.util.NSHMP_Util;

/**
 * This parameter allows one to choose a type of  point source distance correction.
 * 
 * This should really be implemented as an enum parameter.
 */
public class PtSrcDistanceCorrectionParam extends StringParameter {

	private static final long serialVersionUID = 1L;
	public final static String NAME = "Pt Src Dist Corr";
	public final static String INFO = "Type of distance correction for point sources";

	// Options for constraint:
	public final static String PT_SRC_DIST_CORR_NONE = "None";
	public final static String PT_SRC_DIST_CORR_FIELD = "Field (2004)";
	public final static String PT_SRC_DIST_CORR_NSHMP08 = "NSHMP (2008)";
	StringConstraint constraint = new StringConstraint();

	/**
	 * The parameter is set as non editable after creation
	 * @param options
	 * @param defaultValue
	 */
	public PtSrcDistanceCorrectionParam() {
		super(NAME);
		StringConstraint constraint = new StringConstraint();
		constraint.addString(PT_SRC_DIST_CORR_NONE);
		constraint.addString(PT_SRC_DIST_CORR_FIELD);
		constraint.addString(PT_SRC_DIST_CORR_NSHMP08);
		setConstraint(constraint);
	    setInfo(INFO);
	    setDefaultValue(PT_SRC_DIST_CORR_NONE);
	    setValue(PT_SRC_DIST_CORR_NONE);
	    setNonEditable();
	}
	
	/**
	 * This returns the value as PtSrcDistCorr.Type enum
	 * @return
	 */
	public PtSrcDistCorr.Type getValueAsTypePtSrcDistCorr(){
		if(getValue().equals(PT_SRC_DIST_CORR_NONE))
			return PtSrcDistCorr.Type.NONE;
		else if (getValue().equals(PT_SRC_DIST_CORR_FIELD))
			return PtSrcDistCorr.Type.FIELD;
		else
			return PtSrcDistCorr.Type.NSHMP08;
	}
	
	/**
	 * Set the value from a PtSrcDistCorr.Type enum
	 * @return
	 */
	public void setValueFromTypePtSrcDistCorr(PtSrcDistCorr.Type type){
		switch (type) {
		case NONE:
			setValue(PT_SRC_DIST_CORR_NONE);
			break;
		case FIELD: 
			setValue(PT_SRC_DIST_CORR_FIELD);
			break;
		case NSHMP08:
			setValue(PT_SRC_DIST_CORR_NSHMP08);
			break;
		}
	}
}
