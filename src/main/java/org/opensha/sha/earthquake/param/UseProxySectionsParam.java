package org.opensha.sha.earthquake.param;

import org.opensha.commons.param.impl.BooleanParameter;

public class UseProxySectionsParam extends BooleanParameter {

	public static final String NAME = "Use Proxy Rupture Realizations";
	public static final String INFO = "If selected and proxy fault instances have been attached to this solution, "
			+ "multiple proxy rupture realizations will be used for every rupture for which they are available (instead "
			+ "of using a single proxy fault surface).";
	public UseProxySectionsParam(boolean value) {
		super(NAME, value);
		setInfo(INFO);
	}

}
