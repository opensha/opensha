package org.opensha.sha.earthquake.faultSysSolution.erf.td;

import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.event.ParameterChangeEvent;
import org.opensha.commons.param.event.ParameterChangeListener;
import org.opensha.commons.param.impl.EnumParameter;
import org.opensha.sha.earthquake.calc.recurInterval.EqkProbDistCalc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.param.BPTAveragingTypeOptions;
import org.opensha.sha.earthquake.param.BPTAveragingTypeParam;

import com.google.common.base.Preconditions;

public class UCERF3_ProbabilityModel extends AbstractFSS_ProbabilityModel implements ParameterChangeListener {
	
	private EnumParameter<AperiodicityModels> aperiodicityParam;
	private EnumParameter<RenewalModels> renewalModelParam;
	private EnumParameter<HistoricalOpenIntervals> histOpenIntervalParam;
	// TODO: refactor to remove BPT from the name of this and the corresponding enum
	private BPTAveragingTypeParam averagingTypeParam;
	
	// implementation note: if we ever add AperiodicityModels that return continuous values, this cache will explode and
	// will need to be rethought
	private ConcurrentMap<Double, EvenlyDiscretizedFunc> normCDFsCache;
	
	static double max_time_for_normBPT_CDF=5;
	static int num_for_normBPT_CDF=501;
	
	public static final String APERIODICITY_PARAM_NAME = "Aperiodicity Model";
	public static final String RENEWAL_MODEL_PARAM_NAME = "Renewal Model";
	public static final String HISTORICAL_OPEN_INTERVAL_PARAM_NAME = "Historical Open Interval";
	
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
		
		aperiodicityParam = initModelParam(APERIODICITY_PARAM_NAME, aperiodicityModel, supportedAperiodicityModels);
		aperiodicityParam.addParameterChangeListener(this);
		if (aperiodicityParam.getConstraint().size() > 1)
			params.addParameter(aperiodicityParam);
		
		renewalModelParam = initModelParam(RENEWAL_MODEL_PARAM_NAME, renewalModel, supportedRenewalModels);
		renewalModelParam.addParameterChangeListener(this);
		if (renewalModelParam.getConstraint().size() > 1)
			params.addParameter(renewalModelParam);
		
		histOpenIntervalParam = initModelParam(HISTORICAL_OPEN_INTERVAL_PARAM_NAME, histOpenInterval, supportedHistOpenIntervals);
		histOpenIntervalParam.addParameterChangeListener(this);
		if (histOpenIntervalParam.getConstraint().size() > 1)
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
	public double getProbability(FaultSystemSolution fltSysSolution, int fltSysRupIndex,
			double ruptureRate, long forecastStartTimeMillis, double durationYears) {
		// for now just use gain, but could change that if it's more efficient or better in some way to calculate it separately
		double prob = TimeDepUtils.rateToPoissonProb(ruptureRate, durationYears);
		return prob * getProbabilityGain(fltSysSolution, fltSysRupIndex, forecastStartTimeMillis, durationYears);
	}

	@Override
	public double getProbabilityGain(FaultSystemSolution fltSysSolution, int fltSysRupIndex, long forecastStartTimeMillis,
			double durationYears) {
		AperiodicityModels aperiodicityModel = aperiodicityParam.getValue();
		double aperiodicity = aperiodicityModel.getAperiodicity(fltSysSolution, fltSysRupIndex);
		
		EvenlyDiscretizedFunc normCDF = getNormCDF(aperiodicity);
		
		// historic open interval
		// Kevin note: I hated the way it was done in U3 where you had to manually set the parameter to be start - 1875.
		// Now, the historic open interval provides a start time of that interval, and we compute the interval duration
		// dynamically.
		long histOpenIntervalStartTime = histOpenIntervalParam.getValue()
				.getOpenIntervalStartTime(fltSysSolution, fltSysRupIndex);
		double histOpenInterval = 0d; // 0 means no historic open interval
		if (histOpenIntervalStartTime < forecastStartTimeMillis) {
			// it's before the forecast start time, meaning that we have one for this rupture
			double interval = forecastStartTimeMillis - histOpenIntervalStartTime;
			histOpenInterval = (double)interval / TimeDepUtils.MILLISEC_PER_YEAR;
		}
		
		// so far, no caching of things like fault section dates of last event. it's pretty lightweight to grab them
		// so I don't recommend caching for now; if any of those sorts of things turn into a bottleneck down the road
		// we can add efficiencies then.
		
		// TODO: do the actual calculation
		
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
