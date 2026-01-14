package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.util.DataUtils;
import org.opensha.sha.earthquake.calc.recurInterval.EqkProbDistCalc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

import com.google.common.base.Preconditions;

public abstract class AbstractProbDistProbabilityModel extends AbstractFSS_ProbabilityModel {
	
	private EnumMap<RenewalModels, ConcurrentMap<Double, EqkProbDistCalc>> probDistCache = new EnumMap<>(RenewalModels.class);
	
	protected static final double MAX_NORMALIZED_TIME_DEFAULT = 10;
	protected static final int NUM_DISCETIZATIONS_DEFAULT = 1001;
	protected static final int APERIODICITY_SIG_FIGS_DEFAULT = 2;
	
	// discretization parameters
	private double maxNormalizedTime = MAX_NORMALIZED_TIME_DEFAULT;
	private int numDiscretizations = NUM_DISCETIZATIONS_DEFAULT;
	
	// aperiodicity sig figs
	private int numAperSigFigs = APERIODICITY_SIG_FIGS_DEFAULT;
	
	public AbstractProbDistProbabilityModel(FaultSystemSolution fltSysSol, double[] longTermPartRateForSectArray) {
		super(fltSysSol, longTermPartRateForSectArray);
	}

	public AbstractProbDistProbabilityModel(FaultSystemSolution fltSysSol) {
		super(fltSysSol);
	}
	
	public void setMaxNormalizedTime(double maxNormalizedTime) {
		this.maxNormalizedTime = maxNormalizedTime;
		clearCache();
	}

	public void setNumDiscretizations(int numDiscretizations) {
		this.numDiscretizations = numDiscretizations;
		clearCache();
	}

	public void setNumAperSigFigs(int numAperSigFigs) {
		this.numAperSigFigs = numAperSigFigs;
		clearCache();
	}

	protected void clearCache() {
		synchronized (probDistCache) {
			probDistCache.clear();
		}
	}
	
	protected EqkProbDistCalc getProbDistCalc(RenewalModels renewalModel, double aperiodicity) {
		if (!probDistCache.containsKey(renewalModel)) {
			synchronized (probDistCache) {
				if (!probDistCache.containsKey(renewalModel))
					probDistCache.put(renewalModel, new ConcurrentHashMap<>());
			}
		}
		ConcurrentMap<Double, EqkProbDistCalc> cache = probDistCache.get(renewalModel);
		// snap it to our minimum discretization to avoid cache blowup if aperiodicity isn't discrete
		aperiodicity = DataUtils.roundFixed(aperiodicity, numAperSigFigs);
		if (!cache.containsKey(aperiodicity)) {
			EqkProbDistCalc distCalc = renewalModel.instance();
			double delta = maxNormalizedTime/(numDiscretizations-1);
			distCalc.setAll(1.0, aperiodicity, delta, numDiscretizations);
			// putIfAbsent for thread-safety
			cache.putIfAbsent(aperiodicity, distCalc);
		}
		return cache.get(aperiodicity);
	}

}
