package org.opensha.commons.logicTree.sampling;

import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.data.sampling.DimensionedPointSet;
import org.opensha.commons.data.sampling.PermutedPointSet;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.RandomDimensionPermutationTransform;
import org.opensha.commons.data.sampling.SamplingDimension;
import org.opensha.commons.data.sampling.generator.LatinHypercubePointSetGenerator;
import org.opensha.commons.data.sampling.generator.MonteCarloPointSetGenerator;
import org.opensha.commons.data.sampling.generator.OwenScrambledSobolPointSetGenerator;
import org.opensha.commons.data.sampling.generator.PointSetGenerator;
import org.opensha.commons.data.sampling.generator.SobolPointSetGenerator;
import org.opensha.commons.data.sampling.optimization.PointSetHillClimber;
import org.opensha.commons.data.sampling.optimization.QuantizedIncrementalPointSetScorer;
import org.opensha.commons.util.RandomSeedUtils;

import com.google.common.base.Preconditions;

/** Point-set generation and optional optimization modes used to sample logic trees. */
public enum SamplingMethod implements ShortNamed {
	MONTE_CARLO("Monte Carlo", "MCS", "mcs"),
	LATIN_HYPERCUBE("Latin Hypercube", "LHS", "lhs"),
	PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE("Pairwise-Optimized Latin Hypercube", "Pairwise-LHS", "lhs_pairwise"),
	SOBOL("Sobol", "Sobol", "sobol"),
	OWEN_SCRAMBLED_SOBOL("Owen-Scrambled Sobol", "Scrambled-Sobol", "sobol_scrambled", true),
	EXTERNAL("External Point Set", "External", "external");

	public static final int PAIRWISE_CONTINUOUS_BINS = 100;

	private final String name;
	private final String shortName;
	private final String filePrefix;
	private final boolean randomizeDimensionAssignments;

	private SamplingMethod(String name, String shortName, String filePrefix) {
		this(name, shortName, filePrefix, false);
	}

	private SamplingMethod(String name, String shortName, String filePrefix,
			boolean randomizeDimensionAssignments) {
		this.name = name;
		this.shortName = shortName;
		this.filePrefix = filePrefix;
		this.randomizeDimensionAssignments = randomizeDimensionAssignments;
	}

	public PointSetGenerator createGenerator(long seed) {
		return createGenerator(new Random(seed));
	}

	public PointSetGenerator createGenerator(RandomGenerator rand) {
		return switch (this) {
		case MONTE_CARLO -> new MonteCarloPointSetGenerator(rand);
		case LATIN_HYPERCUBE, PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE ->
			new LatinHypercubePointSetGenerator(rand);
		// sobol is deterministic; 1 here means skip the first point which is all-zeros
		case SOBOL -> new SobolPointSetGenerator(1L);
		case OWEN_SCRAMBLED_SOBOL -> new OwenScrambledSobolPointSetGenerator(rand);
		case EXTERNAL -> throw new IllegalStateException("External point sets cannot be generated");
		};
	}

	/** Generates an undecorated point set, including any method-specific coordinate-column assignment. */
	public PointSet generate(int numPoints, int dimensions, long seed) {
		return generate(numPoints, dimensions, new Random(seed));
	}

	/** Generates an undecorated point set using independent random streams derived from {@code random}. */
	public PointSet generate(int numPoints, int dimensions, RandomGenerator random) {
		RandomStreams streams = randomStreams(random);
		return generate(numPoints, dimensions, streams);
	}

	/**
	 * Generates and decorates a point set, then applies any dimension-aware processing selected by this method.
	 */
	public PointSet prepare(int numPoints, List<? extends SamplingDimension> dimensions, long seed) {
		return prepare(numPoints, dimensions, new Random(seed));
	}

	/**
	 * Generates and decorates a point set, then applies any dimension-aware processing selected by this method.
	 */
	public PointSet prepare(int numPoints, List<? extends SamplingDimension> dimensions, RandomGenerator random) {
		Preconditions.checkNotNull(dimensions, "Sampling dimensions cannot be null");
		RandomStreams streams = randomStreams(random);
		PointSet pointSet = generate(numPoints, dimensions.size(), streams);
		pointSet = new DimensionedPointSet(pointSet, dimensions);
		return optimizeIfRequested(pointSet, streams.optimization());
	}

	private PointSet generate(int numPoints, int dimensions, RandomStreams streams) {
		Preconditions.checkArgument(numPoints > 0, "NumPoints must be positive");
		Preconditions.checkArgument(dimensions > 0, "Dimensions must be positive");
		Preconditions.checkState(this != EXTERNAL, "External point sets cannot be generated");
		PointSet pointSet = createGenerator(streams.generation()).generate(numPoints, dimensions);
		if (randomizeDimensionAssignments)
			pointSet = new RandomDimensionPermutationTransform(streams.dimensionAssignment()).apply(pointSet);
		return pointSet;
	}

	private PointSet optimizeIfRequested(PointSet pointSet, RandomGenerator random) {
		if (!isPairwiseOptimized() || pointSet.size() < 2)
			return pointSet;
		PermutedPointSet permuted = PermutedPointSet.independentDimensions(pointSet);
		if (permuted.swapGroupCount() < 2)
			return pointSet;
		QuantizedIncrementalPointSetScorer scorer =
				new QuantizedIncrementalPointSetScorer(permuted, PAIRWISE_CONTINUOUS_BINS);
		PointSetHillClimber.optimize(scorer, pairwiseIterations(pointSet.size()), random);
		return permuted;
	}

	public static long pairwiseIterations(int numPoints) {
		Preconditions.checkArgument(numPoints > 0, "NumPoints must be positive");
		return Math.min(10_000_000L, Math.max(100_000L, Math.multiplyExact((long)numPoints, 1000L)));
	}

	private static RandomStreams randomStreams(RandomGenerator random) {
		Preconditions.checkNotNull(random, "Random generator cannot be null");
		return new RandomStreams(randomStream(random), randomStream(random), randomStream(random));
	}

	private static RandomGenerator randomStream(RandomGenerator random) {
		return new SplittableRandom(RandomSeedUtils.mix64(random.nextLong()));
	}

	private record RandomStreams(RandomGenerator generation, RandomGenerator dimensionAssignment,
			RandomGenerator optimization) {}

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

	public boolean randomizesDimensionAssignments() {
		return randomizeDimensionAssignments;
	}

	@Override public String getName() { return name; }
	@Override public String getShortName() { return shortName; }
	public String getFilePrefix() { return filePrefix; }
}
