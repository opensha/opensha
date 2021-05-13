package org.opensha.sha.earthquake.param;

import java.util.EnumSet;

import org.opensha.commons.param.impl.EnumParameter;

/**
 * This is for representing magnitude-dependent aperiodicity options
 *
 * 
 * @author Ned Field
 * @version $Id:$
 */
public class MagDependentAperiodicityParam extends EnumParameter<MagDependentAperiodicityOptions> {
	
	public static final String NAME = "Aperiodicity";
	public static final String INFO = "Magnitude-dependent aperiodicity values for: M≤6.7, 6.7<M≤7.2, 7.2<M≤7.7, and M>7.7";

	public MagDependentAperiodicityParam() {
		super(NAME, EnumSet.allOf(MagDependentAperiodicityOptions.class), MagDependentAperiodicityOptions.ALL_PT3_VALUES, null);
		setInfo(INFO);
	}

}
