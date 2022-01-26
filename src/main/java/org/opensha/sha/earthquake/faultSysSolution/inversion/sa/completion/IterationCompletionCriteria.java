package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria.EstimationCompletionCriteria;

public class IterationCompletionCriteria implements EstimationCompletionCriteria {
	
	private long minIterations;
	
	/**
	 * Creates an IterationCompletionCriteria that will be satisfied when the number of iterations
	 * equals or exceeds the given amount.
	 * 
	 * @param minIterations
	 */
	public IterationCompletionCriteria(long minIterations) {
		this.minIterations = minIterations;
	}

	@Override
	public boolean isSatisfied(InversionState state) {
		return state.iterations >= minIterations;
	}
	
	@Override
	public String toString() {
		return "IterationCompletionCriteria(minIterations: "+minIterations+")";
	}
	
	public long getMinIterations() {
		return minIterations;
	}

	@Override
	public double estimateFractCompleted(InversionState state) {
		return Math.min(1d, (double)state.iterations/(double)minIterations);
	}

}
