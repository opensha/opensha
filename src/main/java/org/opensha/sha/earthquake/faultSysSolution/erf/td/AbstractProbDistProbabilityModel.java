package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.data.function.UnmodifiableEvenlyDiscrFunc;
import org.opensha.commons.util.DataUtils;
import org.opensha.sha.earthquake.calc.recurInterval.EqkProbDistCalc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;

abstract class AbstractProbDistProbabilityModel extends AbstractFSS_ProbabilityModel {

	private EnumMap<RenewalModels, ConcurrentMap<Double, EqkProbDistCalc>> probDistsCache = new EnumMap<>(RenewalModels.class);
	private EnumMap<RenewalModels, ConcurrentMap<Double, UnmodifiableEvenlyDiscrFunc>> integrationNormCDFsCache = new EnumMap<>(RenewalModels.class);
	
	// defaults for EqkProbDistCalc instantiation for regular calculations
	// discretization = maxNormalizedTime / (numDiscretizations-1)
	// these defaults imply discretization = 0.00005, which is a factor of 10 finer
	// than that used in UCERF3. This, and the change of INTERPOLATE_DEFAULT to true,
	// led WUS gains to change by up to 4.8% in test calculations by Ned.
	protected static final double MAX_NORMALIZED_TIME_DEFAULT = 10;
	protected static final int NUM_DISCETIZATIONS_DEFAULT = 200001;
	protected static final boolean INTERPOLATE_DEFAULT = true;
	
	// defaults for normalized CDFs used for integration, e.g., for mix of sections with and without DOLE
	// discretization = maxNormalizedTime / (numDiscretizations-1)
	// these defaults imply discretization = 0.01, the same used in UCERF3
	// Increasing discretization by a factor of 10 led to less than 0.8% differences
	// in WUS tests by Ned.
	protected static final double MAX_INTEGRATION_NORMALIZED_TIME_DEFAULT = 5;
	protected static final int NUM_INTEGRATION_DISCETIZATIONS_DEFAULT = 501;
	
	// default number of significant figures to round aperiodicity values for caches
	// 2 here means 0.XX precision
	protected static final int APERIODICITY_SIG_FIGS_DEFAULT = 2;
	
	// discretization parameters
	private double maxNormalizedTime = MAX_NORMALIZED_TIME_DEFAULT;
	private int numDiscretizations = NUM_DISCETIZATIONS_DEFAULT;
	private boolean interpolate = INTERPOLATE_DEFAULT;
	private double maxIntegrationNormalizedTime = MAX_INTEGRATION_NORMALIZED_TIME_DEFAULT;
	private int numIntegrationDiscretizations = NUM_INTEGRATION_DISCETIZATIONS_DEFAULT;
	
	// aperiodicity sig figs
	private int numAperSigFigs = APERIODICITY_SIG_FIGS_DEFAULT;
	
	public AbstractProbDistProbabilityModel(FaultSystemSolution fltSysSol, double[] longTermPartRateForSectArray) {
		super(fltSysSol, longTermPartRateForSectArray);
	}

	public AbstractProbDistProbabilityModel(FaultSystemSolution fltSysSol) {
		super(fltSysSol);
	}
	
	/**
	 * Sets discretization parameters for cached {@link EqkProbDistCalc}s used for normal conditional probability calculations
	 * @param maxNormalizedTime
	 * @param numDiscretizations
	 * @param interpolate
	 */
	public void setProbDistsDiscretization(double maxNormalizedTime, int numDiscretizations, boolean interpolate) {
		this.maxNormalizedTime = maxNormalizedTime;
		this.numDiscretizations = numDiscretizations;
		this.interpolate = interpolate;
		clearProbDistsCache();
	}
	
	/**
	 * Sets discretization parameters for cached normalized CDFs used for numerical integrations, usually coarser than
	 * those for normal calculations
	 * @param maxNormalizedTime
	 * @param numDiscretizations
	 * @param interpolate
	 */
	public void setIntegrationNormCDFsDiscretization(double maxNormalizedTime, int numDiscretizations) {
		this.maxIntegrationNormalizedTime = maxNormalizedTime;
		this.numIntegrationDiscretizations = numDiscretizations;
		clearIntegrationNormCDFsCache();
	}

	public void setNumAperSigFigs(int numAperSigFigs) {
		this.numAperSigFigs = numAperSigFigs;
		clearCaches();
	}

	protected void clearProbDistsCache() {
		synchronized (probDistsCache) {
			probDistsCache.clear();
		}
	}

	protected void clearIntegrationNormCDFsCache() {
		synchronized (integrationNormCDFsCache) {
			integrationNormCDFsCache.clear();
		}
	}

	protected void clearCaches() {
		clearProbDistsCache();
		clearIntegrationNormCDFsCache();
	}
	
	private static double calcDelta(double maxTime, int numDiscretizations) {
		return maxTime/(numDiscretizations-1);
	}
	
	/**
	 * This gets (building, if necessary) a normalized {@link EqkProbDistCalc} for the given {@link RenewalModels},
	 * aperiodicity, and discretization parameters.
	 * <p>
	 * The returned {@link EqkProbDistCalc} is un-modifiable to ensure that its parameters are never accidentally changed.
	 * 
	 * @param renewalModel
	 * @param aperiodicity
	 * @see {@link #setProbDistsDiscretization(double, int, boolean)}
	 * @return un-modifiable and reusable {@link EqkProbDistCalc}
	 */
	protected EqkProbDistCalc getNormProbDistCalc(RenewalModels renewalModel, double aperiodicity) {
		if (!probDistsCache.containsKey(renewalModel)) {
			synchronized (probDistsCache) {
				if (!probDistsCache.containsKey(renewalModel))
					probDistsCache.put(renewalModel, new ConcurrentHashMap<>());
			}
		}
		ConcurrentMap<Double, EqkProbDistCalc> cache = probDistsCache.get(renewalModel);
		// snap it to our minimum discretization to avoid cache blowup if aperiodicity isn't discrete
		aperiodicity = getRoundedAperiodicity(aperiodicity);
		if (!cache.containsKey(aperiodicity)) {
			EqkProbDistCalc distCalc = renewalModel.instance();
			double delta = calcDelta(maxNormalizedTime, numDiscretizations);
			distCalc.setAll(1.0, aperiodicity, delta, numDiscretizations);
			distCalc.setInterpolate(interpolate);
			// ensure it is never changed externally
			distCalc.setUnmodifiable();
			// putIfAbsent for thread-safety
			cache.putIfAbsent(aperiodicity, distCalc);
		}
		return cache.get(aperiodicity);
	}
	
	/**
	 * This gets (building, if necessary) a normalized CDF suitable for integration for the given {@link RenewalModels},
	 * aperiodicity, and discretization parameters.
	 * <p>
	 * The returned function is unmodifiable to ensure that is never accidentally changed
	 * 
	 * @param renewalModel
	 * @param aperiodicity
	 * @see {@link #setIntegrationNormCDFsDiscretization(double, int)}
	 * @return un-modifiable and reusable normalized CDF
	 */
	protected UnmodifiableEvenlyDiscrFunc getIntegrationNormCDF(RenewalModels renewalModel, double aperiodicity) {
		if (!integrationNormCDFsCache.containsKey(renewalModel)) {
			synchronized (integrationNormCDFsCache) {
				if (!integrationNormCDFsCache.containsKey(renewalModel))
					integrationNormCDFsCache.put(renewalModel, new ConcurrentHashMap<>());
			}
		}
		ConcurrentMap<Double, UnmodifiableEvenlyDiscrFunc> cache = integrationNormCDFsCache.get(renewalModel);
		// snap it to our minimum discretization to avoid cache blowup if aperiodicity isn't discrete
		aperiodicity = getRoundedAperiodicity(aperiodicity);
		if (!cache.containsKey(aperiodicity)) {
			EqkProbDistCalc distCalc = renewalModel.instance();
			double delta = calcDelta(maxIntegrationNormalizedTime, numIntegrationDiscretizations);
			distCalc.setAll(1.0, aperiodicity, delta, numIntegrationDiscretizations);
			// calculate norm CDF from it and make it unmodifiable
			UnmodifiableEvenlyDiscrFunc cdf = new UnmodifiableEvenlyDiscrFunc(distCalc.getCDF());
			// putIfAbsent for thread-safety
			cache.putIfAbsent(aperiodicity, cdf);
		}
		return cache.get(aperiodicity);
	}
	
	protected double getRoundedAperiodicity(double aperiodicity) {
		return DataUtils.roundFixed(aperiodicity, numAperSigFigs);
	}

}
