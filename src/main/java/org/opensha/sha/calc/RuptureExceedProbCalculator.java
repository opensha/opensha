package org.opensha.sha.calc;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.imr.ScalarIMR;

/**
 * Interface for calculating exceedance probabilities. This takes care of any point-source distance corrections,
 * and allows for point-source optimizations (i.e., interpolated exceedance probabilities for point-source ruptures). 
 */
public interface RuptureExceedProbCalculator {
	
	public void getExceedProbabilities(ScalarIMR gmm, EqkRupture eqkRupture, DiscretizedFunc exceedProbs);
	
	public static Basic BASIC_IMPLEMENTATION = new Basic();
	
	public static class Basic implements RuptureExceedProbCalculator {

		@Override
		public void getExceedProbabilities(ScalarIMR gmm, EqkRupture eqkRupture, DiscretizedFunc exceedProbs) {
			gmm.getExceedProbabilities(eqkRupture, exceedProbs);
		}
		
	}

}
