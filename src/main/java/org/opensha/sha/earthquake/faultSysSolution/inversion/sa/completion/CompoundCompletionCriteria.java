package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import java.util.Collection;

import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;

public class CompoundCompletionCriteria implements CompletionCriteria {
	
	private Collection<CompletionCriteria> criteria;
	
	public CompoundCompletionCriteria(Collection<CompletionCriteria> criteria) {
		this.criteria = criteria;
	}

	@Override
	public boolean isSatisfied(InversionState state) {
		for (CompletionCriteria criteria : criteria) {
			if (criteria.isSatisfied(state))
				return true;
		}
		return false;
	}
	
	public Collection<CompletionCriteria> getCriteria() {
		return criteria;
	}

}
