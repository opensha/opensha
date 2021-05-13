package scratch.UCERF3.simulatedAnnealing.completion;

import java.util.Collection;

import org.apache.commons.lang3.time.StopWatch;

public class CompoundCompletionCriteria implements CompletionCriteria {
	
	private Collection<CompletionCriteria> criterias;
	
	public CompoundCompletionCriteria(Collection<CompletionCriteria> criterias) {
		this.criterias = criterias;
	}

	@Override
	public boolean isSatisfied(StopWatch watch, long iter, double[] energy, long numPerturbsKept) {
		for (CompletionCriteria criteria : criterias) {
			if (criteria.isSatisfied(watch, iter, energy, numPerturbsKept))
				return true;
		}
		return false;
	}
	
	public Collection<CompletionCriteria> getCriterias() {
		return criterias;
	}

}
