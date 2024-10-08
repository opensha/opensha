package org.opensha.sha.earthquake.param;

import java.util.EnumSet;

import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;

/**
 * 
 * 
 * @author kevin
 */
public class PointSourceDistanceCorrectionParam extends EnumParameter<PointSourceDistanceCorrections> {
	
	public static final String NAME = "Point Source Distance Correction";

	public PointSourceDistanceCorrectionParam(PointSourceDistanceCorrections defaultValue) {
		super(NAME, EnumSet
			.allOf(PointSourceDistanceCorrections.class), defaultValue, null);
	}

}
