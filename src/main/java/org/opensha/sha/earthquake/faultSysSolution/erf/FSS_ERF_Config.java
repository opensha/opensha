package org.opensha.sha.earthquake.faultSysSolution.erf;

import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.TimeSpan.DurationUnits;
import org.opensha.commons.data.TimeSpan.StartTimePrecision;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.TimeDepFaultSystemSolutionERF;
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
	private final boolean aseisReducesArea;
	private final boolean useRupMFDs;
	private final boolean useProxyRuptures;

	private FSS_ERF_Config(Builder builder) {
		double durationYears = builder.durationYears;
		FSS_ProbabilityModels probabilityModel = builder.probabilityModel;
		Integer startYear = builder.startYear;
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
		this.durationYears = durationYears;
		this.probabilityModel = probabilityModel;
		this.startYear = startYear;
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

	public static final class Builder {
		private double durationYears = 1d;
		private FSS_ProbabilityModels probabilityModel;
		private Integer startYear;
		private boolean aseisReducesArea = BaseFaultSystemSolutionERF.ASEIS_REDUCES_AREA_DEAFULT;
		private boolean useRupMFDs = BaseFaultSystemSolutionERF.USE_RUP_MFDS_DEAFULT;
		private boolean useProxyRuptures = BaseFaultSystemSolutionERF.USE_PROXY_RUPS_DEAFULT;

		private Builder() {}

		private Builder(FSS_ERF_Config config) {
			this.durationYears = config.durationYears;
			this.probabilityModel = config.probabilityModel;
			this.startYear = config.startYear;
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
