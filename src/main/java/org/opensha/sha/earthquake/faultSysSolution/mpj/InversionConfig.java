package org.opensha.sha.earthquake.faultSysSolution.mpj;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import org.opensha.sha.earthquake.faultSysSolution.inversion.ClusterSpecificInversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfigurationFactory;

import com.google.common.base.Preconditions;

public final class InversionConfig {

	private final Class<? extends InversionConfigurationFactory> factoryClass;
	private final int runsPerBranch;
	private final boolean parallelBranchAverage;
	private final String completionArg;
	private final Integer explicitWallTimeMinutes;
	private final Integer explicitWallTimeMinutesPerRound;
	private final Double estimatedAvgNumRups;
	private final Integer estimatedRounds;
	private final Double estimatedItersPerSec;
	private final List<String> extraArgs;
	private final Map<String, String> systemProperties;

	private InversionConfig(Builder builder) {
		this.factoryClass = builder.factoryClass;
		this.runsPerBranch = builder.runsPerBranch;
		this.parallelBranchAverage = builder.parallelBranchAverage;
		this.completionArg = builder.completionArg;
		this.explicitWallTimeMinutes = builder.explicitWallTimeMinutes;
		this.explicitWallTimeMinutesPerRound = builder.explicitWallTimeMinutesPerRound;
		this.estimatedAvgNumRups = builder.estimatedAvgNumRups;
		this.estimatedRounds = builder.estimatedRounds;
		this.estimatedItersPerSec = builder.estimatedItersPerSec;
		this.extraArgs = List.copyOf(builder.extraArgs);
		this.systemProperties = Collections.unmodifiableMap(new LinkedHashMap<>(builder.systemProperties));
	}

	public static Builder builder() {
		return new Builder();
	}

	public Class<? extends InversionConfigurationFactory> factoryClass() {
		return factoryClass;
	}

	public int runsPerBranch() {
		return runsPerBranch;
	}

	public boolean parallelBranchAverage() {
		return parallelBranchAverage;
	}

	public String completionArg() {
		return completionArg;
	}

	public List<String> extraArgs() {
		return extraArgs;
	}

	public Map<String, String> systemProperties() {
		return systemProperties;
	}

	public static void addOptions(Options ops) {
		ops.addOption(null, "inversion-system-property", true,
				"Java system property to set on every inversion worker, supplied as key=value. Can be repeated.");
	}

	int resolvePerInversionMinutes() {
		if (explicitWallTimeMinutes != null)
			return explicitWallTimeMinutes;
		if (explicitWallTimeMinutesPerRound != null)
			return explicitWallTimeMinutesPerRound;
		double numIters = estimatedAvgNumRups*estimatedRounds;
		double invSecs = numIters/estimatedItersPerSec;
		int invMins = (int)(invSecs/60d + 0.5);
		if (ClusterSpecificInversionConfigurationFactory.class.isAssignableFrom(factoryClass))
			invMins *= 2;
		return invMins;
	}

	int resolveInversionJobMinutes(int nodeRounds) {
		if (explicitWallTimeMinutes != null)
			return explicitWallTimeMinutes;
		int invMins = resolvePerInversionMinutes();
		int mins = (int)(nodeRounds*invMins);
		mins += Integer.max(60, invMins);
		mins += Integer.max(0, nodeRounds-1)*10;
//		System.out.println("Inversion mins="+mins+" for invMins="+invMins+", nodeRounds="+nodeRounds);
		return mins;
	}

	public static final class Builder {
		private Class<? extends InversionConfigurationFactory> factoryClass;
		private int runsPerBranch = 1;
		private boolean parallelBranchAverage;
		private String completionArg;
		private Integer explicitWallTimeMinutes;
		private Integer explicitWallTimeMinutesPerRound;
		private Double estimatedAvgNumRups;
		private Integer estimatedRounds;
		private Double estimatedItersPerSec;
		private final List<String> extraArgs = new ArrayList<>();
		private final Map<String, String> systemProperties = new LinkedHashMap<>();

		public Builder forCMD(CommandLine cmd) {
			if (cmd.hasOption("inversion-system-property"))
				for (String assignment : cmd.getOptionValues("inversion-system-property"))
					MPJScriptUtil.putSystemProperty(systemProperties, assignment);
			return this;
		}

		public Builder factoryClass(Class<? extends InversionConfigurationFactory> factoryClass) {
			this.factoryClass = factoryClass;
			return this;
		}

		public Builder runsPerBranch(int runsPerBranch) {
			this.runsPerBranch = runsPerBranch;
			return this;
		}

		public Builder parallelBranchAverage(boolean parallelBranchAverage) {
			this.parallelBranchAverage = parallelBranchAverage;
			return this;
		}

		public Builder completionArg(String completionArg) {
			this.completionArg = completionArg;
			return this;
		}

		public Builder wallTimeMinutes(int explicitWallTimeMinutes) {
			this.explicitWallTimeMinutes = explicitWallTimeMinutes;
			this.explicitWallTimeMinutesPerRound = null;
			this.estimatedAvgNumRups = null;
			this.estimatedRounds = null;
			this.estimatedItersPerSec = null;
			return this;
		}

		public Builder wallTimeMinutesPerRound(int minsPerRound) {
			this.explicitWallTimeMinutes = null;
			this.explicitWallTimeMinutesPerRound = minsPerRound;
			this.estimatedAvgNumRups = null;
			this.estimatedRounds = null;
			this.estimatedItersPerSec = null;
			return this;
		}

		public Builder estimateWallTimeMinutes(double avgNumRups, int rounds, double itersPerSec) {
			this.explicitWallTimeMinutes = null;
			this.explicitWallTimeMinutesPerRound = null;
			this.estimatedAvgNumRups = avgNumRups;
			this.estimatedRounds = rounds;
			this.estimatedItersPerSec = itersPerSec;
			return this;
		}

		public Builder addExtraArg(String extraArg) {
			if (extraArg != null && !extraArg.isBlank())
				this.extraArgs.add(extraArg);
			return this;
		}

		public Builder addExtraArgs(List<String> extraArgs) {
			if (extraArgs != null)
				this.extraArgs.addAll(extraArgs);
			return this;
		}

		public Builder systemProperty(String key, String value) {
			MPJScriptUtil.putSystemProperty(systemProperties, key, value);
			return this;
		}

		public Builder systemProperties(Map<String, String> systemProperties) {
			if (systemProperties != null)
				for (Map.Entry<String, String> entry : systemProperties.entrySet())
					systemProperty(entry.getKey(), entry.getValue());
			return this;
		}

		public InversionConfig build() {
			Preconditions.checkNotNull(factoryClass, "factoryClass is required");
			Preconditions.checkArgument(runsPerBranch > 0, "runsPerBranch must be > 0");
			Preconditions.checkState(explicitWallTimeMinutes != null || explicitWallTimeMinutesPerRound != null
					|| (estimatedAvgNumRups != null && estimatedRounds != null && estimatedItersPerSec != null),
					"must explicitly set wall time or estimate it from avgNumRups/rounds/itersPerSec");
			return new InversionConfig(this);
		}
	}
}
