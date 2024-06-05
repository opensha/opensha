package org.opensha.sha.earthquake.param;

import org.opensha.commons.param.impl.BooleanParameter;

public class UseRupMFDsParam extends BooleanParameter {

	public static final String NAME = "Use Rupture MFDs";
	public static final String INFO = "If selected and rupture MFDs have been attached to this solution, those MFDs will "
			+ "be used instead of the average magnitude and total rate for each ruptures. This is usually only applicable "
			+ "to branch averaged solutions; if enabled, every branch-specific magnitude will be retained, else the "
			+ "branch-averaged magnitude will be used.";
	public UseRupMFDsParam(boolean value) {
		super(NAME, value);
		setInfo(INFO);
	}

}
