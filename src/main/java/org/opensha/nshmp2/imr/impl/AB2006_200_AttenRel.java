package org.opensha.nshmp2.imr.impl;

import java.util.EnumSet;

import org.opensha.commons.param.event.ParameterChangeWarningListener;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.nshmp2.imr.impl.AB2006_140_AttenRel.StressDrop;

/**
 * Same as {@link AB2006_140_AttenRel} with the internal stress parameter set to
 * 200 bars.
 * 
 * @author Peter Powers
 * @version $Id:$
 * @see AB2006_200_AttenRel
 */
public class AB2006_200_AttenRel extends AB2006_140_AttenRel {

	public final static String SHORT_NAME = "AB2006_200";
	private static final long serialVersionUID = 1234567890987654353L;
	public final static String NAME = "Atkinson and Boore (2002) 200bar";

	public AB2006_200_AttenRel(ParameterChangeWarningListener listener) {
		super(listener);
	}

//	@Override
//	public void setParamDefaults() {
//		super.setParamDefaults();
//		getParameter("Stress Drop").setValue(StressDrop.SD_200);
//	}
	
	@Override
	protected void initOtherParams() {
		super.initOtherParams();
		stressDropParam = new EnumParameter<StressDrop>("Stress Drop",
				EnumSet.allOf(StressDrop.class), StressDrop.SD_200, null);
	}

	@Override
	public String getName() {
		return NAME;
	}
}
