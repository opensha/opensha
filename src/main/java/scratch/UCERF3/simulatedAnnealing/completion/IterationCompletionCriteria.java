package scratch.UCERF3.simulatedAnnealing.completion;

import org.apache.commons.lang3.time.StopWatch;

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
	public boolean isSatisfied(StopWatch watch, long iter, double[] energy, long numPerturbsKept) {
		return iter >= minIterations;
	}
	
	@Override
	public String toString() {
		return "IterationCompletionCriteria(minIterations: "+minIterations+")";
	}
	
	public long getMinIterations() {
		return minIterations;
	}

}
