package org.opensha.commons.logicTree.sampling;

import java.util.Random;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.data.sampling.generator.LatinHypercubePointSetGenerator;
import org.opensha.commons.data.sampling.generator.MonteCarloPointSetGenerator;
import org.opensha.commons.data.sampling.generator.OwenScrambledSobolPointSetGenerator;
import org.opensha.commons.data.sampling.generator.PointSetGenerator;
import org.opensha.commons.data.sampling.generator.SobolPointSetGenerator;

/** Point-set generation and optional optimization modes used to sample logic trees. */
public enum SamplingMethod implements ShortNamed {
	MONTE_CARLO("Monte Carlo", "MCS", "mcs"),
	LATIN_HYPERCUBE("Latin Hypercube", "LHS", "lhs"),
	PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE("Pairwise-Optimized Latin Hypercube", "Pairwise-LHS", "lhs_pairwise"),
	SOBOL("Sobol", "Sobol", "sobol"),
	OWEN_SCRAMBLED_SOBOL("Owen-Scrambled Sobol", "Scrambled-Sobol", "sobol_scrambled"),
	EXTERNAL("External Point Set", "External", "external");

	private final String name;
	private final String shortName;
	private final String filePrefix;

	private SamplingMethod(String name, String shortName, String filePrefix) {
		this.name = name;
		this.shortName = shortName;
		this.filePrefix = filePrefix;
	}

	public PointSetGenerator createGenerator(long seed) {
		return switch (this) {
		case MONTE_CARLO -> new MonteCarloPointSetGenerator(new Random(seed));
		case LATIN_HYPERCUBE, PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE ->
			new LatinHypercubePointSetGenerator(new Random(seed));
		case SOBOL -> new SobolPointSetGenerator(1L); // 1 means skip the first point which is all-zeros
		case OWEN_SCRAMBLED_SOBOL -> new OwenScrambledSobolPointSetGenerator(new Random(seed));
		case EXTERNAL -> throw new IllegalStateException("External point sets cannot be generated");
		};
	}

	public boolean isPairwiseOptimized() {
		return this == PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE;
	}

	public boolean isMC() {
		return this == MONTE_CARLO;
	}

	public boolean isLHS() {
		return this == LATIN_HYPERCUBE || this == PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE;
	}

	public boolean usesRandomSeed() {
		return this != SOBOL && this != EXTERNAL;
	}

	@Override public String getName() { return name; }
	@Override public String getShortName() { return shortName; }
	public String getFilePrefix() { return filePrefix; }
}
