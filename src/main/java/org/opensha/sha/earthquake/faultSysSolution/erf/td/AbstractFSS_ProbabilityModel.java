package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

public abstract class AbstractFSS_ProbabilityModel implements FSS_ProbabilityModel {

	protected final FaultSystemSolution fltSysSol;
	protected final double[] longTermPartRateForSectArray;

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

}
