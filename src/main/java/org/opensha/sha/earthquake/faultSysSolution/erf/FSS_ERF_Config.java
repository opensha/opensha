package org.opensha.sha.earthquake.faultSysSolution.erf;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.TimeSpan.DurationUnits;
import org.opensha.commons.data.TimeSpan.StartTimePrecision;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.param.ParameterList;
import org.opensha.commons.param.impl.EnumParameterizedModelarameter;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.AperiodicityModel;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.AperiodicityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.HistoricalOpenInterval;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.HistoricalOpenIntervals;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.RenewalModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.TimeDepFaultSystemSolutionERF;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.UCERF3_ProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.WG02_ProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.ProxyFaultSectionInstances;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.param.AseismicityAreaReductionParam;
import org.opensha.sha.earthquake.param.UseProxySectionsParam;
import org.opensha.sha.earthquake.param.UseRupMFDsParam;

import com.google.common.base.Preconditions;

/**
 * ERF configuration shared by fault-system hazard calculators and script writers. It controls the forecast time span,
 * probability model, and fault-system source construction options.
 */
public final class FSS_ERF_Config {

	private final double durationYears;
	private final FSS_ProbabilityModels probabilityModel;
	private final Integer startYear;
	private final RenewalModels renewalModel;
	private final AperiodicityModels aperiodicityModel;
	private final Double aperiodicityValue;
	private final HistoricalOpenIntervals historicalOpenInterval;
	private final Integer historicalOpenIntervalYear;
	private final boolean aseisReducesArea;
	private final boolean useRupMFDs;
	private final boolean useProxyRuptures;

	private FSS_ERF_Config(Builder builder) {
		double durationYears = builder.durationYears;
		FSS_ProbabilityModels probabilityModel = builder.probabilityModel;
		Integer startYear = builder.startYear;
		RenewalModels renewalModel = builder.renewalModel;
		AperiodicityModels aperiodicityModel = builder.aperiodicityModel;
		Double aperiodicityValue = builder.aperiodicityValue;
		HistoricalOpenIntervals historicalOpenInterval = builder.historicalOpenInterval;
		Integer historicalOpenIntervalYear = builder.historicalOpenIntervalYear;
		Preconditions.checkArgument(Double.isFinite(durationYears) && durationYears > 0d,
				"Duration must be finite and > 0: %s", durationYears);
		if (probabilityModel == null) {
			Preconditions.checkArgument(startYear == null,
					"A start year can only be supplied with a non-Poisson probability model");
		} else if (probabilityModel == FSS_ProbabilityModels.POISSON) {
			Preconditions.checkArgument(startYear == null,
					"A start year cannot be supplied with the Poisson probability model");
		} else {
			Preconditions.checkNotNull(startYear,
					"A start year is required for non-Poisson probability model %s", probabilityModel.name());
		}
		boolean hasModelOverrides = renewalModel != null || aperiodicityModel != null || aperiodicityValue != null
				|| historicalOpenInterval != null || historicalOpenIntervalYear != null;
		Preconditions.checkArgument(!hasModelOverrides
				|| probabilityModel != null && probabilityModel != FSS_ProbabilityModels.POISSON,
				"Time-dependent probability-model settings require a non-Poisson probability model");
		if (aperiodicityValue != null) {
			Preconditions.checkArgument(Double.isFinite(aperiodicityValue) && aperiodicityValue >= 0d,
					"Aperiodicity must be finite and >= 0: %s", aperiodicityValue);
			if (aperiodicityModel == null)
				aperiodicityModel = AperiodicityModels.SINGLE_VALUED;
			else
				Preconditions.checkArgument(aperiodicityModel == AperiodicityModels.SINGLE_VALUED,
						"An aperiodicity value can only be supplied with the SINGLE_VALUED model");
		}
		if (historicalOpenIntervalYear != null) {
			if (historicalOpenInterval == null)
				historicalOpenInterval = HistoricalOpenIntervals.SINGLE_YEAR;
			else
				Preconditions.checkArgument(historicalOpenInterval == HistoricalOpenIntervals.SINGLE_YEAR,
						"A historical open interval year can only be supplied with SINGLE_YEAR");
		}
		this.durationYears = durationYears;
		this.probabilityModel = probabilityModel;
		this.startYear = startYear;
		this.renewalModel = renewalModel;
		this.aperiodicityModel = aperiodicityModel;
		this.aperiodicityValue = aperiodicityValue;
		this.historicalOpenInterval = historicalOpenInterval;
		this.historicalOpenIntervalYear = historicalOpenIntervalYear;
		this.aseisReducesArea = builder.aseisReducesArea;
		this.useRupMFDs = builder.useRupMFDs;
		this.useProxyRuptures = builder.useProxyRuptures;
	}

	public static Builder builder() {
		return new Builder();
	}

	public Builder toBuilder() {
		return new Builder(this);
	}

	public static FSS_ERF_Config timeIndependent(double durationYears) {
		return builder().durationYears(durationYears).build();
	}

	public static FSS_ERF_Config forProbabilityModel(FSS_ProbabilityModels probabilityModel,
			double durationYears) {
		Preconditions.checkArgument(probabilityModel == FSS_ProbabilityModels.POISSON,
				"A start year is required for non-Poisson probability model %s", probabilityModel);
		return builder().durationYears(durationYears).probabilityModel(probabilityModel).build();
	}

	public static FSS_ERF_Config timeDependent(FSS_ProbabilityModels probabilityModel, int startYear,
			double durationYears) {
		Preconditions.checkArgument(probabilityModel != FSS_ProbabilityModels.POISSON,
				"Use forProbabilityModel for the Poisson model");
		return builder().durationYears(durationYears)
				.probabilityModel(Preconditions.checkNotNull(probabilityModel)).startYear(startYear).build();
	}

	public double durationYears() {
		return durationYears;
	}

	/** @return the explicitly selected probability model, or {@code null} to use the base ERF */
	public FSS_ProbabilityModels probabilityModel() {
		return probabilityModel;
	}

	public Integer startYear() {
		return startYear;
	}

	public RenewalModels renewalModel() {
		return renewalModel;
	}

	public AperiodicityModels aperiodicityModel() {
		return aperiodicityModel;
	}

	public Double aperiodicityValue() {
		return aperiodicityValue;
	}

	public HistoricalOpenIntervals historicalOpenInterval() {
		return historicalOpenInterval;
	}

	public Integer historicalOpenIntervalYear() {
		return historicalOpenIntervalYear;
	}

	public boolean aseisReducesArea() {
		return aseisReducesArea;
	}

	public boolean useRupMFDs() {
		return useRupMFDs;
	}

	public boolean useProxyRuptures() {
		return useProxyRuptures;
	}

	public boolean isTimeDependent() {
		return probabilityModel != null && probabilityModel != FSS_ProbabilityModels.POISSON;
	}

	/** Builds the time span described by this configuration without constructing an ERF. */
	public TimeSpan buildTimeSpan() {
		TimeSpan timeSpan = new TimeSpan(isTimeDependent() ? StartTimePrecision.YEARS : StartTimePrecision.NONE,
				DurationUnits.YEARS);
		if (isTimeDependent())
			timeSpan.setStartTime(startYear);
		timeSpan.setDuration(durationYears);
		return timeSpan;
	}

	public BaseFaultSystemSolutionERF buildERF(FaultSystemSolution solution) {
		BaseFaultSystemSolutionERF erf;
		if (probabilityModel == null) {
			erf = new BaseFaultSystemSolutionERF(solution);
		} else {
			TimeDepFaultSystemSolutionERF tdERF = new TimeDepFaultSystemSolutionERF(solution);
			tdERF.setProbabilityModelChoice(probabilityModel);
			if (hasProbabilityModelOverrides())
				applyProbabilityModelOverrides(Preconditions.checkNotNull(tdERF.getProbabilityModel(),
						"A fault-system solution is required to apply probability-model overrides"));
			if (startYear != null)
				tdERF.getTimeSpan().setStartTime(startYear);
			erf = tdERF;
		}
		erf.getTimeSpan().setDuration(durationYears);
		erf.setParameter(AseismicityAreaReductionParam.NAME, aseisReducesArea);
		if (solution != null && solution.hasAvailableModule(RupMFDsModule.class))
			erf.setParameter(UseRupMFDsParam.NAME, useRupMFDs);
		if (solution != null && solution.hasAvailableModule(ProxyFaultSectionInstances.class))
			erf.setParameter(UseProxySectionsParam.NAME, useProxyRuptures);
		return erf;
	}

	private boolean hasProbabilityModelOverrides() {
		return renewalModel != null || aperiodicityModel != null || aperiodicityValue != null
				|| historicalOpenInterval != null || historicalOpenIntervalYear != null;
	}

	private void applyProbabilityModelOverrides(FSS_ProbabilityModel model) {
		if (model instanceof UCERF3_ProbabilityModel) {
			UCERF3_ProbabilityModel u3Model = (UCERF3_ProbabilityModel)model;
			if (renewalModel != null)
				u3Model.setRenewalModelChoice(renewalModel);
			if (aperiodicityModel != null)
				u3Model.setAperiodicityModelChoice(aperiodicityModel);
			if (historicalOpenInterval != null)
				u3Model.setHistOpenIntervalChoice(historicalOpenInterval);
		} else if (model instanceof WG02_ProbabilityModel) {
			Preconditions.checkArgument(renewalModel == null,
					"Probability model %s does not support renewal-model overrides", probabilityModel);
			Preconditions.checkArgument(historicalOpenInterval == null,
					"Probability model %s does not support historical open interval overrides", probabilityModel);
			if (aperiodicityModel != null)
				((WG02_ProbabilityModel)model).setAperiodicityModelChoice(aperiodicityModel);
		} else {
			ParameterList params = model.getAdjustableParameters();
			if (renewalModel != null)
				setParameterValue(params, RenewalModels.PARAM_NAME, renewalModel);
			if (aperiodicityModel != null)
				setParameterizedModelChoice(params, AperiodicityModels.PARAM_NAME, aperiodicityModel);
			if (historicalOpenInterval != null)
				setParameterizedModelChoice(params, HistoricalOpenIntervals.PARAM_NAME, historicalOpenInterval);
		}

		if (aperiodicityValue != null) {
			AperiodicityModel modelValue;
			if (model instanceof UCERF3_ProbabilityModel)
				modelValue = ((UCERF3_ProbabilityModel)model).getAperiodicityModel();
			else if (model instanceof WG02_ProbabilityModel)
				modelValue = ((WG02_ProbabilityModel)model).getAperiodicityModel();
			else
				modelValue = getParameterizedModel(params(model), AperiodicityModels.PARAM_NAME,
						AperiodicityModel.class);
			Preconditions.checkState(modelValue instanceof AperiodicityModel.SingleValued,
					"Aperiodicity model did not produce a single-valued model: %s", modelValue.getName());
			((AperiodicityModel.SingleValued)modelValue).setValue(aperiodicityValue);
		}
		if (historicalOpenIntervalYear != null) {
			HistoricalOpenInterval modelValue;
			if (model instanceof UCERF3_ProbabilityModel)
				modelValue = ((UCERF3_ProbabilityModel)model).getHistOpenInterval();
			else
				modelValue = getParameterizedModel(params(model), HistoricalOpenIntervals.PARAM_NAME,
						HistoricalOpenInterval.class);
			Preconditions.checkState(modelValue instanceof HistoricalOpenInterval.SingleYear,
					"Historical open interval did not produce a single-year model: %s", modelValue.getName());
			((HistoricalOpenInterval.SingleYear)modelValue).setYear(historicalOpenIntervalYear);
		}
	}

	private ParameterList params(FSS_ProbabilityModel model) {
		ParameterList params = model.getAdjustableParameters();
		Preconditions.checkArgument(params != null,
				"Probability model %s does not expose the requested override", probabilityModel);
		return params;
	}

	private void setParameterValue(ParameterList params, String name, Object value) {
		Preconditions.checkArgument(params != null && params.containsParameter(name),
				"Probability model %s does not support %s overrides", probabilityModel, name);
		params.setValue(name, value);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void setParameterizedModelChoice(ParameterList params, String name, Enum<?> choice) {
		Preconditions.checkArgument(params != null && params.containsParameter(name),
				"Probability model %s does not support %s overrides", probabilityModel, name);
		Parameter<?> param = params.getParameter(name);
		Preconditions.checkState(param instanceof EnumParameterizedModelarameter,
				"Parameter %s does not support model choices", name);
		((EnumParameterizedModelarameter)param).setEnumValue(choice);
	}

	private <T> T getParameterizedModel(ParameterList params, String name, Class<T> type) {
		Preconditions.checkArgument(params.containsParameter(name),
				"Probability model %s does not support %s overrides", probabilityModel, name);
		return type.cast(params.getParameter(name).getValue());
	}

	public static final class Builder {
		private double durationYears = 1d;
		private FSS_ProbabilityModels probabilityModel;
		private Integer startYear;
		private RenewalModels renewalModel;
		private AperiodicityModels aperiodicityModel;
		private Double aperiodicityValue;
		private HistoricalOpenIntervals historicalOpenInterval;
		private Integer historicalOpenIntervalYear;
		private boolean aseisReducesArea = BaseFaultSystemSolutionERF.ASEIS_REDUCES_AREA_DEAFULT;
		private boolean useRupMFDs = BaseFaultSystemSolutionERF.USE_RUP_MFDS_DEAFULT;
		private boolean useProxyRuptures = BaseFaultSystemSolutionERF.USE_PROXY_RUPS_DEAFULT;

		private Builder() {}

		private Builder(FSS_ERF_Config config) {
			this.durationYears = config.durationYears;
			this.probabilityModel = config.probabilityModel;
			this.startYear = config.startYear;
			this.renewalModel = config.renewalModel;
			this.aperiodicityModel = config.aperiodicityModel;
			this.aperiodicityValue = config.aperiodicityValue;
			this.historicalOpenInterval = config.historicalOpenInterval;
			this.historicalOpenIntervalYear = config.historicalOpenIntervalYear;
			this.aseisReducesArea = config.aseisReducesArea;
			this.useRupMFDs = config.useRupMFDs;
			this.useProxyRuptures = config.useProxyRuptures;
		}

		public Builder durationYears(double durationYears) {
			this.durationYears = durationYears;
			return this;
		}

		public Builder probabilityModel(FSS_ProbabilityModels probabilityModel) {
			this.probabilityModel = probabilityModel;
			return this;
		}

		public Builder startYear(Integer startYear) {
			this.startYear = startYear;
			return this;
		}

		public Builder renewalModel(RenewalModels renewalModel) {
			this.renewalModel = renewalModel;
			return this;
		}

		public Builder aperiodicityModel(AperiodicityModels aperiodicityModel) {
			this.aperiodicityModel = aperiodicityModel;
			return this;
		}

		public Builder aperiodicityValue(Double aperiodicityValue) {
			this.aperiodicityValue = aperiodicityValue;
			return this;
		}

		public Builder historicalOpenInterval(HistoricalOpenIntervals historicalOpenInterval) {
			this.historicalOpenInterval = historicalOpenInterval;
			return this;
		}

		public Builder historicalOpenIntervalYear(Integer historicalOpenIntervalYear) {
			this.historicalOpenIntervalYear = historicalOpenIntervalYear;
			return this;
		}

		public Builder aseisReducesArea(boolean aseisReducesArea) {
			this.aseisReducesArea = aseisReducesArea;
			return this;
		}

		public Builder useRupMFDs(boolean useRupMFDs) {
			this.useRupMFDs = useRupMFDs;
			return this;
		}

		public Builder useProxyRuptures(boolean useProxyRuptures) {
			this.useProxyRuptures = useProxyRuptures;
			return this;
		}

		public FSS_ERF_Config build() {
			return new FSS_ERF_Config(this);
		}
	}

}
