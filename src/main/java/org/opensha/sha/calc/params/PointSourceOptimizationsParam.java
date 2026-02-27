package org.opensha.sha.calc.params;

import org.opensha.commons.param.impl.BooleanParameter;

public class PointSourceOptimizationsParam extends BooleanParameter {

	public final static String NAME = "Enable point source optimizations?";
	public final static String INFO = "When enabled, exceedance calculations for point-source ruptures without finite "
			+ "surfaces (i.e., true point-sources or distance-corrected) will be efficiently calculated at fixed "
			+ "distances and interpolated.";
	public final static Boolean DEFAULT = true;
	
	public PointSourceOptimizationsParam() {
		super(NAME, DEFAULT);
		setDefaultValue(DEFAULT);
		setInfo(INFO);
	}
}
