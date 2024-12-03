package org.opensha.sha.earthquake.param;

import java.util.EnumSet;

import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;

import com.google.common.base.Preconditions;

/**
 * 
 * 
 * @author kevin
 */
public class PointSourceDistanceCorrectionParam extends EnumParameter<PointSourceDistanceCorrections> {
	
	public static final String NAME = "Point Source Distance Correction";

	public PointSourceDistanceCorrectionParam(EnumSet<PointSourceDistanceCorrections> choices,
			PointSourceDistanceCorrections defaultValue) {
		super(NAME, choices, defaultValue, null);
		Preconditions.checkState(defaultValue == null || choices.contains(defaultValue),
				"Default value (%s) not contined in allowed choices", defaultValue);
	}

	/**
	 * Initializes the param, initially set to {@link PointSourceDistanceCorrections#DEFAULT}
	 */
	public PointSourceDistanceCorrectionParam() {
		this (PointSourceDistanceCorrections.DEFAULT);
	}

	public PointSourceDistanceCorrectionParam(PointSourceDistanceCorrections defaultValue) {
		super(NAME, EnumSet.allOf(PointSourceDistanceCorrections.class), defaultValue, null);
	}

}
