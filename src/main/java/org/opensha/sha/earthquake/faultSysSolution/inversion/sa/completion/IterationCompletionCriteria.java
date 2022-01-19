package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;

public class IterationCompletionCriteria implements CompletionCriteria {
	
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

}
