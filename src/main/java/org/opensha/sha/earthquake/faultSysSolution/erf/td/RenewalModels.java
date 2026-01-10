package org.opensha.sha.earthquake.faultSysSolution.erf.td;

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
}