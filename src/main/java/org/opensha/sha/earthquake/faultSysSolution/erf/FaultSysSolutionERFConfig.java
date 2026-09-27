package org.opensha.sha.earthquake.faultSysSolution.erf;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.data.TimeSpan;
import org.opensha.commons.data.TimeSpan.DurationUnits;
import org.opensha.commons.data.TimeSpan.StartTimePrecision;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.FSS_ProbabilityModels;
import org.opensha.sha.earthquake.faultSysSolution.erf.td.TimeDepFaultSystemSolutionERF;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.FaultSystemSolutionERF;

/** Temporal configuration shared by fault-system hazard calculators and script writers. */
public final class FaultSysSolutionERFConfig {

	public static final String DURATION_OPTION = "duration";
	public static final String PROB_MODEL_OPTION = "prob-model";
	public static final String START_YEAR_OPTION = "start-year";

	private final double durationYears;
	private final FSS_ProbabilityModels probabilityModel;
	private final Integer startYear;

	private FaultSysSolutionERFConfig(double durationYears, FSS_ProbabilityModels probabilityModel,
			Integer startYear) {
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
	}

	public static FaultSysSolutionERFConfig timeIndependent(double durationYears) {
		return new FaultSysSolutionERFConfig(durationYears, null, null);
	}

	public static FaultSysSolutionERFConfig forProbabilityModel(FSS_ProbabilityModels probabilityModel,
			double durationYears) {
		Preconditions.checkArgument(probabilityModel == FSS_ProbabilityModels.POISSON,
				"A start year is required for non-Poisson probability model %s", probabilityModel);
		return new FaultSysSolutionERFConfig(durationYears, probabilityModel, null);
	}

	public static FaultSysSolutionERFConfig timeDependent(FSS_ProbabilityModels probabilityModel, int startYear,
			double durationYears) {
		Preconditions.checkArgument(probabilityModel != FSS_ProbabilityModels.POISSON,
				"Use forProbabilityModel for the Poisson model");
		return new FaultSysSolutionERFConfig(durationYears,
				Preconditions.checkNotNull(probabilityModel), startYear);
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
			erf = new FaultSystemSolutionERF(solution);
		} else {
			TimeDepFaultSystemSolutionERF tdERF = new TimeDepFaultSystemSolutionERF(solution);
			tdERF.setProbabilityModelChoice(probabilityModel);
			if (startYear != null)
				tdERF.getTimeSpan().setStartTime(startYear);
			erf = tdERF;
		}
		erf.getTimeSpan().setDuration(durationYears);
		return erf;
	}

	public static void addOptions(Options options) {
		options.addOption(null, DURATION_OPTION, true,
				"Forecast duration in years. Default: 1");
		options.addOption(null, PROB_MODEL_OPTION, true,
				"Fault-system probability model. One of: "+enumOptions());
		options.addOption(null, START_YEAR_OPTION, true,
				"Forecast start year; required for a non-Poisson probability model");
	}

	public static FaultSysSolutionERFConfig fromCommandLine(CommandLine commandLine) {
		double duration = commandLine.hasOption(DURATION_OPTION)
				? Double.parseDouble(commandLine.getOptionValue(DURATION_OPTION)) : 1d;
		FSS_ProbabilityModels model = commandLine.hasOption(PROB_MODEL_OPTION)
				? FSS_ProbabilityModels.valueOf(commandLine.getOptionValue(PROB_MODEL_OPTION).trim().toUpperCase()) : null;
		Integer startYear = commandLine.hasOption(START_YEAR_OPTION)
				? Integer.valueOf(commandLine.getOptionValue(START_YEAR_OPTION)) : null;
		return new FaultSysSolutionERFConfig(duration, model, startYear);
	}

	private static String enumOptions() {
		StringBuilder options = new StringBuilder();
		for (FSS_ProbabilityModels model : FSS_ProbabilityModels.values()) {
			if (options.length() > 0)
				options.append(", ");
			options.append(model.name());
		}
		return options.toString();
	}
}
