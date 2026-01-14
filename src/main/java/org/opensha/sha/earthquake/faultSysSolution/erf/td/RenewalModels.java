package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumSet;

import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.sha.earthquake.calc.recurInterval.BPT_DistCalc;
import org.opensha.sha.earthquake.calc.recurInterval.EqkProbDistCalc;
import org.opensha.sha.earthquake.calc.recurInterval.LognormalDistCalc;
import org.opensha.sha.earthquake.calc.recurInterval.WeibullDistCalc;

public enum RenewalModels {
	BPT("BPT") {
		@Override
		public EqkProbDistCalc instance() {
			return new BPT_DistCalc();
		}
	},
	LOGNORMAL("Log-Normal") {
		@Override
		public EqkProbDistCalc instance() {
			return new LognormalDistCalc();
		}
	},
	WEIBULL("Weibull") {
		@Override
		public EqkProbDistCalc instance() {
			return new WeibullDistCalc();
		}
	};
	
	private String name;

	private RenewalModels(String name) {
		this.name = name;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public abstract EqkProbDistCalc instance();
	
	public static final String PARAM_NAME = "Renewal Model";
	private static final String PARAM_INFO = "Renewal model distribution for use in elastic-rebound probability calculations.";
	
	public static EnumParameter<RenewalModels> buildParameter(EnumSet<RenewalModels> choices, RenewalModels defaultValue) {
		EnumParameter<RenewalModels> param = new EnumParameter<>(PARAM_NAME, choices, defaultValue, null);
		param.setInfo(PARAM_INFO);
		return param;
	}
}