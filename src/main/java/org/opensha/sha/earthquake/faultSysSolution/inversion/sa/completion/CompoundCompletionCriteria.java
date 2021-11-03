package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.time.StopWatch;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.ConstraintRange;

public class CompoundCompletionCriteria implements CompletionCriteria {
	
	private Collection<CompletionCriteria> criteria;
	
	public CompoundCompletionCriteria(Collection<CompletionCriteria> criteria) {
		this.criteria = criteria;
	}

	@Override
	public boolean isSatisfied(StopWatch watch, long iter, double[] energy, long numPerturbsKept, int numNonZero, double[] misfits, double[] misfits_ineq, List<ConstraintRange> constraintRanges) {
		for (CompletionCriteria criteria : criteria) {
			if (criteria.isSatisfied(watch, iter, energy, numPerturbsKept, numNonZero, misfits, misfits_ineq, constraintRanges))
				return true;
		}
		return false;
	}
	
	public Collection<CompletionCriteria> getCriteria() {
		return criteria;
	}

}
