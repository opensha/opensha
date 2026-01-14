package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.Arrays;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import com.google.common.base.Preconditions;

public abstract class AbstractFSS_ProbabilityModel implements FSS_ProbabilityModel {

	protected final FaultSystemSolution fltSysSol;
	protected final FaultSystemRupSet fltSysRupSet;
	// TODO: we could detect multiple section instances to correct this array so that it also worked for branch-averaged
	// solutions across multiple fault models
	protected final double[] sectlongTermPartRates;
	
	/**
	 * Section dates of last event (DOLE). This can be accessed directly in subclasses but should never be modified
	 */
	protected long[] sectDOLE;

	/**
	 * Constructor that takes only a {@link FaultSystemSolution} and computes section participation rates directly from
	 * it.
	 * @param fltSysSol
	 */
	public AbstractFSS_ProbabilityModel(FaultSystemSolution fltSysSol) {
		this(fltSysSol, fltSysSol.calcTotParticRateForAllSects());
	}
	
	/**
	 * Constructor that takes in precomputed section participation rates that may be different than those computed
	 * by the passed in {@link FaultSystemSolution} (e.g., if some ruptures are skipped).
	 * @param fltSysSol
	 * @param sectlongTermPartRates
	 */
	public AbstractFSS_ProbabilityModel(FaultSystemSolution fltSysSol, double[] sectlongTermPartRates) {
		this.fltSysSol = fltSysSol;
		this.fltSysRupSet = fltSysSol.getRupSet();
		Preconditions.checkState(sectlongTermPartRates.length == fltSysRupSet.getNumSections());
		this.sectlongTermPartRates = sectlongTermPartRates;
		resetSectDOLE();
	}

	@Override
	public FaultSystemSolution getFaultSystemSolution() {
		return fltSysSol;
	}

	@Override
	public long[] getSectDOLE() {
		return Arrays.copyOf(sectDOLE, sectDOLE.length);
	}

	@Override
	public long getSectDOLE(int sectIndex) {
		return sectDOLE[sectIndex];
	}

	@Override
	public void setSectDOLE(long[] sectDatesOfLastEvent) {
		int numSects = fltSysRupSet.getNumSections();
		Preconditions.checkState(sectDatesOfLastEvent.length == numSects,
				"Passed in sectDatesOfLastEvent is of size %s but we have %s sections.", sectDatesOfLastEvent.length, numSects);
		this.sectDOLE = sectDatesOfLastEvent;
		sectDOLE_Changed();
	}

	@Override
	public void setSectDOLE(int sectIndex, long sectDateOfLastEvent) {
		sectDOLE[sectIndex] = sectDateOfLastEvent;
		sectDOLE_Changed();
	}
	
	static long[] getOriginalDOLE(FaultSystemRupSet rupSet) {
		long[] sectDatesOfLastEvent = new long[rupSet.getNumSections()];
		for (int s=0; s<sectDatesOfLastEvent.length; s++)
			sectDatesOfLastEvent[s] = rupSet.getFaultSectionData(s).getDateOfLastEvent();
		return sectDatesOfLastEvent;
	}

	@Override
	public void resetSectDOLE() {
		this.sectDOLE = getOriginalDOLE(fltSysRupSet);
		sectDOLE_Changed();
	}

	@Override
	public int getNumSectsWithDOLE() {
		int count = 0;
		for (long dole : sectDOLE)
			if (dole > Long.MIN_VALUE)
				count++;
		return count;
	}

	/**
	 * Called whenever the section date-of-last-event (DOLE) is changed. Does nothing but can be overridden to clear
	 * out any cached data in subclasses.
	 */
	protected void sectDOLE_Changed() {
		
	}

	@Override
	public double[] getSectLongTermPartRates() {
		return Arrays.copyOf(sectlongTermPartRates, sectlongTermPartRates.length);
	}

	@Override
	public double getSectLongTermPartRate(int sectIndex) {
		return sectlongTermPartRates[sectIndex];
	}
	
	@Override
	public String toString() {
		return getMetadataString();
	}

}
