package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;

public class IterationsPerVariableCompletionCriteria implements CompletionCriteria {
	
	private double itersPerVariable;
	private transient long offset = 0l;

	public IterationsPerVariableCompletionCriteria(double itersPerVariable) {
		this(itersPerVariable, 0l);
	}

	public IterationsPerVariableCompletionCriteria(double itersPerVariable, long offset) {
		this.itersPerVariable = itersPerVariable;
		this.offset = offset;
	}

	public double getItersPerVariable() {
		return itersPerVariable;
	}

	@Override
	public boolean isSatisfied(InversionState state) {
		long iters = state.iterations - offset;
		double itersPer = (double)iters/state.bestSolution.length;
		return itersPer >= itersPerVariable;
	}
	
	@Override
	public String toString() {
		return "IterationsPerVariableCompletionCriteria(itersPerVar: "+(float)itersPerVariable+")";
	}

}
