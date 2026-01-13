package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.Arrays;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import com.google.common.base.Preconditions;

public abstract class AbstractFSS_ProbabilityModel implements FSS_ProbabilityModel {

	protected final FaultSystemSolution fltSysSol;
	// TODO: we could detect multiple section instances to correct this array so that it also worked for branch-averaged
	// solutions across multiple fault models
	protected final double[] longTermPartRateForSectArray;
	
	protected long[] sectDatesOfLastEvent;

	/**
	 * Constructor that takes only a {@link FaultSystemSolution} and computes section participation rates directly from
	 * it.
	 * @param fltSysSol
	 */
	public AbstractFSS_ProbabilityModel(FaultSystemSolution fltSysSol) {
		this(fltSysSol, fltSysSol.calcTotParticRateForAllSects());
	}
	
	/**
	 * Constructor that takes in precomputed sectino participation rates that may be different than those computed
	 * by the passed in {@link FaultSystemSolution} (e.g., if some ruptures are skipped).
	 * @param fltSysSol
	 * @param longTermPartRateForSectArray
	 */
	public AbstractFSS_ProbabilityModel(FaultSystemSolution fltSysSol, double[] longTermPartRateForSectArray) {
		this.fltSysSol = fltSysSol;
		this.longTermPartRateForSectArray = longTermPartRateForSectArray;
	}

	@Override
	public FaultSystemSolution getFaultSystemSolution() {
		return fltSysSol;
	}

	@Override
	public long[] getSectDatesOfLastEvent() {
		return Arrays.copyOf(sectDatesOfLastEvent, sectDatesOfLastEvent.length);
	}

	@Override
	public long getSectDateOfLastEvent(int sectIndex) {
		return sectDatesOfLastEvent[sectIndex];
	}

	@Override
	public void setSectDatesOfLastEvent(long[] sectDatesOfLastEvent) {
		int numSects = fltSysSol.getRupSet().getNumSections();
		Preconditions.checkState(sectDatesOfLastEvent.length == numSects,
				"Passed in sectDatesOfLastEvent is of size %s but we have %s sections.", sectDatesOfLastEvent.length, numSects);
		this.sectDatesOfLastEvent = sectDatesOfLastEvent;
	}

	@Override
	public void setSectDateOfLastEvent(int sectIndex, long sectDateOfLastEvent) {
		sectDatesOfLastEvent[sectIndex] = sectDateOfLastEvent;
	}
	
	static long[] getOriginalDatesOfLastEvent(FaultSystemRupSet rupSet) {
		long[] sectDatesOfLastEvent = new long[rupSet.getNumSections()];
		for (int s=0; s<sectDatesOfLastEvent.length; s++)
			sectDatesOfLastEvent[s] = rupSet.getFaultSectionData(s).getDateOfLastEvent();
		return sectDatesOfLastEvent;
	}

	@Override
	public void resetSectDatesOfLastEvent() {
		this.sectDatesOfLastEvent = getOriginalDatesOfLastEvent(fltSysSol.getRupSet());
	}

}
