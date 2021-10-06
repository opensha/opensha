package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import java.util.Collection;

import org.apache.commons.lang3.time.StopWatch;

public class CompoundCompletionCriteria implements CompletionCriteria {
	
	private Collection<CompletionCriteria> criteria;
	
	public CompoundCompletionCriteria(Collection<CompletionCriteria> criteria) {
		this.criteria = criteria;
	}

	@Override
	public boolean isSatisfied(StopWatch watch, long iter, double[] energy, long numPerturbsKept, int numNonZero) {
		for (CompletionCriteria criteria : criteria) {
			if (criteria.isSatisfied(watch, iter, energy, numPerturbsKept, numNonZero))
				return true;
		}
		return false;
	}
	
	public Collection<CompletionCriteria> getCriteria() {
		return criteria;
	}

}
