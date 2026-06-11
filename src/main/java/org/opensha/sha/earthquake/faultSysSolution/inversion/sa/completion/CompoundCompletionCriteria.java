package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import java.util.Collection;

import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.InversionState;

import com.google.common.base.Preconditions;

public class CompoundCompletionCriteria implements CompletionCriteria {
	
	private Collection<CompletionCriteria> criteria;
	private boolean logicalAnd;
	
	public CompoundCompletionCriteria(Collection<CompletionCriteria> criteria) {
		this(criteria, false);
	}
	
	public CompoundCompletionCriteria(Collection<CompletionCriteria> criteria, boolean logicalAnd) {
		Preconditions.checkState(!criteria.isEmpty());
		this.criteria = criteria;
		this.logicalAnd = logicalAnd;
	}

	@Override
	public boolean isSatisfied(InversionState state) {
		if (logicalAnd) {
			for (CompletionCriteria criteria : criteria)
				if (!criteria.isSatisfied(state))
					return false;
			return true;
		} else {
			// logical or
			for (CompletionCriteria criteria : criteria)
				if (criteria.isSatisfied(state))
					return true;
			return false;
		}
	}
	
	public Collection<CompletionCriteria> getCriteria() {
		return criteria;
	}
	
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder(logicalAnd ? "CompoundAND[" : "CompoundOR[");
		boolean first = true;
		for (CompletionCriteria crit : criteria) {
			if (first)
				first = false;
			else
				str.append("; ");
			str.append(crit.toString());
		}
		str.append("]");
		return str.toString();
	}

}
