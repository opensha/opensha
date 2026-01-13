package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.commons.param.impl.ParameterizedEnumParameter;
import org.opensha.sha.earthquake.calc.recurInterval.EqkProbDistCalc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;

import com.google.common.base.Preconditions;

public class UCERF3_ProbabilityModel extends AbstractFSS_ProbabilityModel implements ParameterChangeListener {
	
	private ParameterizedEnumParameter<AperiodicityModels, AperiodicityModel> aperiodicityParam;
	
	private EnumParameter<RenewalModels> renewalModelParam;
	
	private ParameterizedEnumParameter<HistoricalOpenIntervals, HistoricalOpenInterval> histOpenIntervalParam;
	
	// TODO: refactor to remove BPT from the name of this and the corresponding enum
	private BPTAveragingTypeParam averagingTypeParam;
	
	// implementation note: if we ever add AperiodicityModels that return continuous values, this cache will explode and
	// will need to be rethought
	private ConcurrentMap<Double, EvenlyDiscretizedFunc> normCDFsCache;
	
	static double max_time_for_normBPT_CDF=5;
	static int num_for_normBPT_CDF=501;
	
	public static final String RENEWAL_MODEL_PARAM_NAME = "Renewal Model";
	
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
		
		renewalModelParam = initModelParam(RENEWAL_MODEL_PARAM_NAME, renewalModel, supportedRenewalModels);
		renewalModelParam.addParameterChangeListener(this);
		if (renewalModelParam.getConstraint().size() > 1)
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
	
	private static <E extends Enum<E>> EnumParameter<E> initModelParam(String name, E defaultValue, EnumSet<E> options) {
		Preconditions.checkNotNull(defaultValue, "Default value is null");
		if (options == null)
			options = EnumSet.of(defaultValue);
		else
			Preconditions.checkState(options.contains(defaultValue),
					"Allowed set doesn't contain default value: "+defaultValue);
		return new EnumParameter<E>(name, options, defaultValue, null);
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
		double prob = TimeDepUtils.rateToPoissonProb(ruptureRate, durationYears);
		return prob * getProbabilityGain(ruptureIndex, forecastStartTimeMillis, durationYears);
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
			long sectDateLast = sectDatesOfLastEvent[sectIndex];
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

}
