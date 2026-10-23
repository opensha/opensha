package org.opensha.commons.logicTree.sampling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.data.sampling.ArrayPointSet;
import org.opensha.commons.data.sampling.DimensionSubsetPointSet;
import org.opensha.commons.data.sampling.DimensionedPointSet;
import org.opensha.commons.data.sampling.InactiveSamplingDimension;
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
import org.opensha.commons.data.sampling.optimization.PointSetObjective;
import org.opensha.commons.data.sampling.optimization.PointSetObjective.SwapSession;
import org.opensha.commons.data.sampling.scoring.CenteredDiscrepancy;
import org.opensha.commons.data.sampling.scoring.ProjectionDiscrepancyScorer;
import org.opensha.commons.util.RandomSeedUtils;

import com.google.common.base.Preconditions;

/** Point-set generation and optional optimization modes used to sample logic trees. */
public enum SamplingMethod implements ShortNamed {
	MONTE_CARLO("Monte Carlo", "MCS", "mcs"),
	LATIN_HYPERCUBE("Latin Hypercube", "LHS", "lhs"),
	PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE("Pairwise-Optimized Latin Hypercube", "PO-LHS", "lhs_pairwise"),
	CENTERED_DISCREPANCY_OPTIMIZED_LATIN_HYPERCUBE("Centered-Discrepancy-Optimized Latin Hypercube", "CDO-LHS", "lhs_centered_discrepancy"),
	SOBOL("Sobol'", "Sobol'", "sobol"),
	OWEN_SCRAMBLED_SOBOL("Owen-Scrambled Sobol'", "Scrambled-Sobol'", "sobol_scrambled", true),
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
		case LATIN_HYPERCUBE, PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE, CENTERED_DISCREPANCY_OPTIMIZED_LATIN_HYPERCUBE ->
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
		return prepare(numPoints, dimensions, dimensions.size(), random).mappedPointSet();
	}

	/**
	 * Generates a point set whose stored width may exceed the number of dimensions currently used for sampling. The
	 * returned mapping identifies the stored coordinate column assigned to each supplied sampling dimension. Any
	 * additional stored dimensions are retained as inactive dimensions for future extensions.
	 */
	public PreparedPointSet prepare(int numPoints, List<? extends SamplingDimension> dimensions,
			int storedDimensions, long seed) {
		return prepare(numPoints, dimensions, storedDimensions, new Random(seed));
	}

	/**
	 * Generates a point set whose stored width may exceed the number of dimensions currently used for sampling. The
	 * supplied random generator is split into the same independent streams used by the standard preparation method.
	 */
	public PreparedPointSet prepare(int numPoints, List<? extends SamplingDimension> dimensions,
			int storedDimensions, RandomGenerator random) {
		Preconditions.checkNotNull(dimensions, "Sampling dimensions cannot be null");
		Preconditions.checkArgument(!dimensions.isEmpty(), "Sampling dimensions cannot be empty");
		Preconditions.checkArgument(storedDimensions >= dimensions.size(),
				"Stored dimension count %s is less than mapped dimension count %s",
				storedDimensions, dimensions.size());
		RandomStreams streams = randomStreams(random);
		PointSet generated = generateRawForPreparation(
				numPoints, dimensions.size(), storedDimensions, streams.generation());
		int[] dimensionIndexes = identity(dimensions.size());
		if (randomizeDimensionAssignments)
			shuffle(dimensionIndexes, streams.dimensionAssignment());

		List<SamplingDimension> storedDefinitions = new ArrayList<>(
				java.util.Collections.nCopies(storedDimensions, InactiveSamplingDimension.INSTANCE));
		for (int d=0; d<dimensionIndexes.length; d++)
			storedDefinitions.set(dimensionIndexes[d], dimensions.get(d));
		PointSet storedPointSet = new DimensionedPointSet(generated, storedDefinitions);
		PointSet mappedPointSet = new DimensionSubsetPointSet(storedPointSet, dimensionIndexes);
		PointSet optimizedPointSet = optimizeIfRequested(mappedPointSet, streams.optimization());
		if (optimizedPointSet != mappedPointSet)
			storedPointSet = replaceMappedPointSet(storedPointSet, optimizedPointSet, dimensionIndexes);
		return new PreparedPointSet(storedPointSet, dimensionIndexes);
	}

	private PointSet generate(int numPoints, int dimensions, RandomStreams streams) {
		PointSet pointSet = generateRaw(numPoints, dimensions, streams.generation());
		if (randomizeDimensionAssignments)
			pointSet = new RandomDimensionPermutationTransform(streams.dimensionAssignment()).apply(pointSet);
		return pointSet;
	}

	private PointSet generateRaw(int numPoints, int dimensions, RandomGenerator random) {
		Preconditions.checkArgument(numPoints > 0, "NumPoints must be positive");
		Preconditions.checkArgument(dimensions > 0, "Dimensions must be positive");
		Preconditions.checkState(this != EXTERNAL, "External point sets cannot be generated");
		return createGenerator(random).generate(numPoints, dimensions);
	}

	private PointSet generateRawForPreparation(int numPoints, int mappedDimensions, int storedDimensions,
			RandomGenerator random) {
		if (this != MONTE_CARLO || storedDimensions == mappedDimensions)
			return generateRaw(numPoints, storedDimensions, random);
		// Monte Carlo generation is point-major, so generating the wider matrix directly would change every point after
		// the first. Generate the mapped prefix first to keep existing samples independent of reserved storage width.
		PointSet mapped = generateRaw(numPoints, mappedDimensions, random);
		PointSet reserved = generateRaw(numPoints, storedDimensions-mappedDimensions, random);
		double[][] values = new double[numPoints][storedDimensions];
		for (int p=0; p<numPoints; p++) {
			for (int d=0; d<mappedDimensions; d++)
				values[p][d] = mapped.get(p, d);
			for (int d=mappedDimensions; d<storedDimensions; d++)
				values[p][d] = reserved.get(p, d-mappedDimensions);
		}
		return new ArrayPointSet(values);
	}

	private static int[] identity(int dimensions) {
		int[] indexes = new int[dimensions];
		for (int d=0; d<dimensions; d++)
			indexes[d] = d;
		return indexes;
	}

	private static void shuffle(int[] indexes, RandomGenerator random) {
		for (int d=indexes.length-1; d>0; d--) {
			int swap = random.nextInt(d+1);
			int index = indexes[d];
			indexes[d] = indexes[swap];
			indexes[swap] = index;
		}
	}

	private static PointSet replaceMappedPointSet(PointSet storedPointSet, PointSet mappedPointSet,
			int[] dimensionIndexes) {
		Preconditions.checkNotNull(mappedPointSet, "Mapped point set cannot be null");
		Preconditions.checkArgument(mappedPointSet.size() == storedPointSet.size(),
				"Mapped point count %s != stored point count %s", mappedPointSet.size(), storedPointSet.size());
		Preconditions.checkArgument(mappedPointSet.dimensions() == dimensionIndexes.length,
				"Mapped dimension count %s != mapping length %s",
				mappedPointSet.dimensions(), dimensionIndexes.length);
		double[][] values = new double[storedPointSet.size()][];
		for (int p=0; p<values.length; p++) {
			values[p] = storedPointSet.getPoint(p);
			for (int d=0; d<dimensionIndexes.length; d++)
				values[p][dimensionIndexes[d]] = mappedPointSet.get(p, d);
		}
		List<SamplingDimension> definitions = new ArrayList<>(storedPointSet.dimensions());
		for (int d=0; d<storedPointSet.dimensions(); d++)
			definitions.add(storedPointSet.getDimension(d));
		for (int d=0; d<dimensionIndexes.length; d++)
			definitions.set(dimensionIndexes[d], mappedPointSet.getDimension(d));
		return new DimensionedPointSet(new ArrayPointSet(values), definitions);
	}

	/** Full stored point set plus the stored-column assignment of each currently mapped dimension. */
	public static final class PreparedPointSet {
		private final PointSet pointSet;
		private final int[] dimensionIndexes;

		private PreparedPointSet(PointSet pointSet, int[] dimensionIndexes) {
			this.pointSet = pointSet;
			this.dimensionIndexes = dimensionIndexes.clone();
		}

		/** @return full stored point set, including inactive reserved dimensions */
		public PointSet pointSet() {
			return pointSet;
		}

		/** @return no-copy logical-order view containing only currently mapped dimensions */
		public PointSet mappedPointSet() {
			return new DimensionSubsetPointSet(pointSet, dimensionIndexes);
		}

		/** @return stored point-set column for each logical sampling dimension */
		public int[] dimensionIndexes() {
			return dimensionIndexes.clone();
		}

		/** Replaces mapped coordinates while retaining all reserved coordinates. */
		public PointSet replaceMappedPointSet(PointSet mappedPointSet) {
			return SamplingMethod.replaceMappedPointSet(pointSet, mappedPointSet, dimensionIndexes);
		}

		@Override public String toString() {
			return "PreparedPointSet[points=" + pointSet.size() + ", storedDimensions=" + pointSet.dimensions()
					+ ", dimensionIndexes=" + Arrays.toString(dimensionIndexes) + "]";
		}
	}

	private PointSet optimizeIfRequested(PointSet pointSet, RandomGenerator random) {
		if (!isOptimized() || pointSet.size() < 2)
			return pointSet;
		PermutedPointSet permuted = PermutedPointSet.independentDimensions(pointSet);
		if (permuted.swapGroupCount() < 2)
			return pointSet;
		long iterations = pairwiseIterations(pointSet.size());
		PointSetObjective objective = getObjective();
		SwapSession session = objective.prepare(permuted);
		System.out.println("Optimizing sample of size "+pointSet.size()+" with "+iterations+" iterations");
		System.out.println("\tInitial objective:\t"+(float)session.getCurrentValue());
		PointSetHillClimber.optimize(session, iterations, random);
		System.out.println("\tDONE; final objective:\t"+(float)session.getCurrentValue());
		return permuted;
	}

	public long pairwiseIterations(int numPoints) {
//		long multiplier = this == CENTERED_DISCREPANCY_OPTIMIZED_LATIN_HYPERCUBE ? 10l : 1000L;
//		long multiplier = this == CENTERED_DISCREPANCY_OPTIMIZED_LATIN_HYPERCUBE ? 100l : 5000L;
//		long multiplier = this == CENTERED_DISCREPANCY_OPTIMIZED_LATIN_HYPERCUBE ? 1000l : 10000L;
		long multiplier = 5000L;
		Preconditions.checkArgument(numPoints > 0, "NumPoints must be positive");
		return Math.min(100_000_000L, Math.max(100_000L, Math.multiplyExact((long)numPoints, multiplier)));
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

	public PointSetObjective getObjective() {
		return switch (this) {
		case PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE -> ProjectionDiscrepancyScorer.quantized(PAIRWISE_CONTINUOUS_BINS).objective();
		case CENTERED_DISCREPANCY_OPTIMIZED_LATIN_HYPERCUBE -> CenteredDiscrepancy.objective();

		default -> null;
		};
	}

	public boolean isOptimized() {
		return this == PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE || this == CENTERED_DISCREPANCY_OPTIMIZED_LATIN_HYPERCUBE;
	}

	public boolean isMC() {
		return this == MONTE_CARLO;
	}

	public boolean isLHS() {
		return this == LATIN_HYPERCUBE || this == PAIRWISE_OPTIMIZED_LATIN_HYPERCUBE || this == CENTERED_DISCREPANCY_OPTIMIZED_LATIN_HYPERCUBE;
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
