package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.exceptions.ConstraintException;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.EnumParameterizedModelarameter;
import org.opensha.sha.earthquake.calc.recurInterval.EqkProbDistCalc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;

import com.google.common.base.Preconditions;

public class UCERF3_ProbabilityModel extends AbstractFSS_ProbabilityModel implements ParameterChangeListener {
	
	private EnumParameterizedModelarameter<AperiodicityModels, AperiodicityModel> aperiodicityParam;
	
	private EnumParameter<RenewalModels> renewalModelParam;
	
	private EnumParameterizedModelarameter<HistoricalOpenIntervals, HistoricalOpenInterval> histOpenIntervalParam;
	
	// TODO: refactor to remove BPT from the name of this and the corresponding enum
	private BPTAveragingTypeParam averagingTypeParam;
	
	// implementation note: if we ever add AperiodicityModels that return continuous values, this cache will explode and
	// will need to be rethought
	private ConcurrentMap<Double, EvenlyDiscretizedFunc> normCDFsCache;
	
	static double max_time_for_normBPT_CDF=5;
	static int num_for_normBPT_CDF=501;
	
	private ParameterList params;
	
	public UCERF3_ProbabilityModel(FaultSystemSolution sol,
			AperiodicityModels aperiodicityModel, RenewalModels renewalModel,
			HistoricalOpenIntervals histOpenInterval, BPTAveragingTypeOptions averagingType) {
		this(sol, sol.calcTotParticRateForAllSects(), aperiodicityModel, renewalModel, histOpenInterval, averagingType);
	}
	
	public UCERF3_ProbabilityModel(FaultSystemSolution sol, double[] longTermPartRateForSectArray,
			AperiodicityModels aperiodicityModel, RenewalModels renewalModel,
			HistoricalOpenIntervals histOpenInterval, BPTAveragingTypeOptions averagingType) {
		this(sol, longTermPartRateForSectArray,
				aperiodicityModel, EnumSet.allOf(AperiodicityModels.class),
				renewalModel, EnumSet.allOf(RenewalModels.class),
				histOpenInterval, EnumSet.allOf(HistoricalOpenIntervals.class),
				averagingType, EnumSet.allOf(BPTAveragingTypeOptions.class));
	}
	
	public UCERF3_ProbabilityModel(FaultSystemSolution sol, double[] longTermPartRateForSectArray,
			AperiodicityModels aperiodicityModel, EnumSet<AperiodicityModels> supportedAperiodicityModels,
			RenewalModels renewalModel, EnumSet<RenewalModels> supportedRenewalModels,
			HistoricalOpenIntervals histOpenInterval, EnumSet<HistoricalOpenIntervals> supportedHistOpenIntervals,
			BPTAveragingTypeOptions averagingType, EnumSet<BPTAveragingTypeOptions> supportedAveragingTypes) {
		super(sol, longTermPartRateForSectArray);
		params = new ParameterList();
		
		aperiodicityParam = AperiodicityModels.buildParameter(sol, supportedAperiodicityModels, aperiodicityModel);
		aperiodicityParam.addParameterChangeListener(this);
		if (supportedAperiodicityModels.size() > 1 || aperiodicityParam.getValue().getAdjustableParameters() != null)
			// display it if we have multiple options, or a single option with its own parameters
			params.addParameter(aperiodicityParam);
		
		renewalModelParam = RenewalModels.buildParameter(supportedRenewalModels, renewalModel);
		renewalModelParam.addParameterChangeListener(this);
		if (supportedRenewalModels.size() > 1)
			// display it if we have multiple options
			params.addParameter(renewalModelParam);
		
		histOpenIntervalParam = HistoricalOpenIntervals.buildParameter(sol, supportedHistOpenIntervals, histOpenInterval);
		histOpenIntervalParam.addParameterChangeListener(this);
		if (supportedHistOpenIntervals.size() > 1 || histOpenIntervalParam.getValue().getAdjustableParameters() != null)
			// display it if we have multiple options, or a single option with its own parameters
			params.addParameter(histOpenIntervalParam);
		
		averagingTypeParam = new BPTAveragingTypeParam(averagingType, supportedAveragingTypes);
		averagingTypeParam.addParameterChangeListener(this);
		if (averagingTypeParam.getConstraint().size() > 1)
			params.addParameter(averagingTypeParam);
		
		normCDFsCache = new ConcurrentHashMap<>();
	}
	
	/**
	 * Sets a custom historical open interval model to something not controlled by the built-in model enum.
	 * 
	 * @param model
	 */
	public void setCustomHistOpenIntervalModel(HistoricalOpenInterval model) {
		Preconditions.checkNotNull(model, "Passed in historical open interval cannot be null");
		histOpenIntervalParam.setValue(model);
	}
	
	private EvenlyDiscretizedFunc getNormCDF(double aperiodicity) {
		if (!normCDFsCache.containsKey(aperiodicity)) {
			EqkProbDistCalc distCalc = renewalModelParam.getValue().instance();
			double delta = max_time_for_normBPT_CDF/(num_for_normBPT_CDF-1);
			distCalc.setAll(1.0, aperiodicity, delta, num_for_normBPT_CDF);
			EvenlyDiscretizedFunc normCDF = distCalc.getCDF();
			// putIfAbsent for thread-safety
			normCDFsCache.putIfAbsent(aperiodicity, normCDF);
		}
		return normCDFsCache.get(aperiodicity);
	}

	@Override
	public double getProbability(int ruptureIndex, double ruptureRate, long forecastStartTimeMillis, double durationYears) {
		// for now just use gain, but could change that if it's more efficient or better in some way to calculate it separately
		double gain = getProbabilityGain(ruptureIndex, forecastStartTimeMillis, durationYears);
		return TimeDepUtils.rateToNonPoissonProb(ruptureRate*gain, durationYears);
	}

	@Override
	public double getProbabilityGain(int ruptureIndex, long forecastStartTimeMillis,
			double durationYears) {
		AperiodicityModel aperiodicityModel = aperiodicityParam.getValue();
		double aperiodicity = aperiodicityModel.getRuptureAperiodicity(ruptureIndex);
		
		EvenlyDiscretizedFunc normCDF = getNormCDF(aperiodicity);
		
		// historical open interval in years, or 0 if no historic open interval
		double histOpenInterval = histOpenIntervalParam.getValue().getRuptureOpenInterval(ruptureIndex, forecastStartTimeMillis);
		
		// TODO: do the actual calculation
		
		// can access dates of last event by section
		for (int sectIndex : fltSysSol.getRupSet().getSectionsIndicesForRup(ruptureIndex)) {
			long sectDateLast = sectDOLE[sectIndex];
			if (sectDateLast > Long.MIN_VALUE && sectDateLast < forecastStartTimeMillis) {
				// we have a valid DOLE
			}
		}
		
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public void parameterChange(ParameterChangeEvent event) {
		if (event.getParameter() == renewalModelParam) {
			normCDFsCache.clear();
		}
	}

	@Override
	public ParameterList getAdjustableParameters() {
		if (params.isEmpty())
			return null;
		return params;
	}

	@Override
	public String getName() {
		return FSS_ProbabilityModels.UCERF3_METHOD.toString();
	}
	
	/*
	 * Aperiodicity convenience getters/setters
	 */
	
	/**
	 * Sets the AperiodicityModel choice via the models enum. Any model parameters can then be set on the underlying
	 * model by calling {@link #getAperiodicityModel()}.
	 * @param modelChoice
	 * @throws ConstraintException if the selected choice is not allowed
	 */
	public void setAperiodicityModelChoice(AperiodicityModels modelChoice) {
		aperiodicityParam.setEnumValue(modelChoice);
	}
	
	/**
	 * @return aperiodicity model choice, or null if it was set to an external custom value via
	 * {@link #setCustomAperiodicityModel(AperiodicityModel)}.
	 */
	public AperiodicityModels getAperiodicityModelChoice() {
		return aperiodicityParam.getEnumValue();
	}
	
	/**
	 * @return current aperiodicity model.
	 */
	public AperiodicityModel getAperiodicityModel() {
		return aperiodicityParam.getValue();
	}
	
	/**
	 * Sets a custom aperiodicity model to something not controlled by the built-in model enum.
	 * 
	 * @param model
	 */
	public void setCustomAperiodicityModel(AperiodicityModel model) {
		Preconditions.checkNotNull(model, "Passed in aperiodicity model cannot be null");
		aperiodicityParam.setValue(model);
	}
	
	/*
	 * Historical open interval convenience getters/setters
	 */
	
	/**
	 * Sets the historical open interval choice via the models enum. Any model parameters can then be set on the underlying
	 * model by calling {@link #getHistOpenInterval()}.
	 * @param modelChoice
	 * @throws ConstraintException if the selected choice is not allowed
	 */
	public void setHistOpenIntervalChoice(HistoricalOpenIntervals modelChoice) {
		histOpenIntervalParam.setEnumValue(modelChoice);
	}
	
	/**
	 * @return historical open interval choice, or null if it was set to an external custom value via
	 * {@link #setCustomHistOpenIntervalModel(HistoricalOpenInterval)}.
	 */
	public HistoricalOpenIntervals getHistOpenIntervalChoice() {
		return histOpenIntervalParam.getEnumValue();
	}
	
	/**
	 * @return current historical open interval model.
	 */
	public HistoricalOpenInterval getHistOpenInterval() {
		return histOpenIntervalParam.getValue();
	}
	
	/*
	 * Renewal model convenience getters/setters
	 */
	
	/**
	 * @return selected renewal model
	 */
	public RenewalModels getRenewalModelChoice() {
		return renewalModelParam.getValue();
	}
	
	/**
	 * Sets the renewal model choice to the passed in value
	 * @param modelChoice
	 */
	public void setRenewalModelChoice(RenewalModels modelChoice) {
		this.renewalModelParam.setValue(modelChoice);
	}
	
	/*
	 * Averaging type convenience getters/setters
	 */
	
	/**
	 * @return selected averaging type
	 */
	public BPTAveragingTypeOptions getAveragingTypeChoice() {
		return averagingTypeParam.getValue();
	}
	
	/**
	 * Sets the averaging type choice to the passed in value
	 * @param modelChoice
	 */
	public void setAveragingTypeChoice(BPTAveragingTypeOptions modelChoice) {
		this.averagingTypeParam.setValue(modelChoice);
	}

}
