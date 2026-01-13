package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

public class WG02_ProbabilityModel extends AbstractFSS_ProbabilityModel {

	public WG02_ProbabilityModel(FaultSystemSolution fltSysSol, double[] longTermPartRateForSectArray) {
		super(fltSysSol, longTermPartRateForSectArray);
	}

	public WG02_ProbabilityModel(FaultSystemSolution fltSysSol) {
		super(fltSysSol);
	}

	@Override
	public double getProbability(int ruptureIndex, double ruptureRate, long forecastStartTimeMillis, double durationYears) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getProbabilityGain(int ruptureIndex, long forecastStartTimeMillis, double durationYears) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

}
