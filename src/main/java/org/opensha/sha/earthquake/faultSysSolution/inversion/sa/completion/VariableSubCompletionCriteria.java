package org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion;

import org.apache.commons.lang3.time.StopWatch;

public interface VariableSubCompletionCriteria extends CompletionCriteria {
	
	/**
	 * 
	 * @param watch
	 * @param iter
	 * @param energy
	 * @param numPerturbsKept
	 */
	public void setGlobalState(StopWatch watch, long iter, double[] energy, long numPerturbsKept);
	
	/**
	 * 
	 * @param criteria
	 */
	public void setGlobalCriteria(CompletionCriteria criteria);

}
