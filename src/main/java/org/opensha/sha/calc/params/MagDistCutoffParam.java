package org.opensha.sha.calc.params;

import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.param.impl.ArbitrarilyDiscretizedFuncParameter;

public class MagDistCutoffParam extends ArbitrarilyDiscretizedFuncParameter {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public final static String NAME = "Mag-Dist Cutoff Function";
	public final String INFO = "Distance cutoff is a function of mag (the function here, linearly interpolated)";
	private final static double[] defaultCutoffMags =  {0, 5.25,  5.75, 6.25,  6.75, 7.25,  9};
	private final static double[] defaultCutoffDists = {0, 25,    40,   60,    80,   100,   500};
	public static ArbitrarilyDiscretizedFunc DEFAULT;
	static {
		DEFAULT = new ArbitrarilyDiscretizedFunc();
		DEFAULT.setName("mag-dist function");
		for(int i=0; i<defaultCutoffMags.length;i++)
			DEFAULT.set(defaultCutoffDists[i], defaultCutoffMags[i]);
	}

	public MagDistCutoffParam() {
		super(NAME, DEFAULT);
		setDefaultValue(DEFAULT);
		setInfo(INFO);
	}

}
