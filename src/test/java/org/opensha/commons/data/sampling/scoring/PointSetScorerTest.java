package org.opensha.commons.data.sampling.scoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.opensha.commons.data.sampling.ArrayPointSet;
import org.opensha.commons.data.sampling.CategoricalSamplingDimension;
import org.opensha.commons.data.sampling.ContinuousSamplingDimension;
import org.opensha.commons.data.sampling.DimensionedPointSet;
import org.opensha.commons.data.sampling.InactiveSamplingDimension;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.SamplingDimension;

public class PointSetScorerTest {

	private static final double TOL = 1e-12;
	private final ExactPointSetScorer scorer = new ExactPointSetScorer();

	@Test
	public void testKnownContinuousOneDimensionalScore() {
		PointSet points = new ArrayPointSet(new double[][] { { 0.25 }, { 0.75 } });
		ProjectionScore score = scorer.scoreProjection(points, new PointSetProjection(0));
		assertEquals(1d/48d, score.getRawScore(), TOL);
		assertEquals(1d/12d, score.getExpectedRandomScore(), TOL);
		assertEquals(0.25, score.getNormalizedScore(), TOL);
	}

	@Test
	public void testInactiveDimensionsAreExcluded() {
		PointSet activeOnly = new ArrayPointSet(new double[][] { { 0.2 }, { 0.7 } });
		PointSet withInactive = decorate(new ArrayPointSet(new double[][] { { 0.9, 0.2 }, { 0.1, 0.7 } }),
				InactiveSamplingDimension.INSTANCE, ContinuousSamplingDimension.INSTANCE);
		PointSetScore expected = scorer.score(activeOnly);
		PointSetScore actual = scorer.score(withInactive);
		assertEquals(expected.getNormalizedScore(), actual.getNormalizedScore(), TOL);
		assertEquals(new PointSetProjection(1), actual.getProjectionScores().get(0).getProjection());
	}

	@Test(expected=IllegalArgumentException.class)
	public void testExplicitInactiveProjectionRejected() {
		PointSet points = decorate(new ArrayPointSet(new double[][] { { 0.2, 0.3 } }),
				InactiveSamplingDimension.INSTANCE, ContinuousSamplingDimension.INSTANCE);
		scorer.score(points, PointSetScoringConfig.builder().projections(new PointSetProjection(0)).build());
	}

	@Test(expected=IllegalArgumentException.class)
	public void testAllInactiveRejected() {
		PointSet points = decorate(new ArrayPointSet(new double[][] { { 0.2 } }),
				InactiveSamplingDimension.INSTANCE);
		scorer.score(points);
	}

	@Test
	public void testKnownContinuousTwoDimensionalScore() {
		PointSet points = new ArrayPointSet(new double[][] { { 0.5, 0.5 } });
		ProjectionScore score = scorer.scoreProjection(points, new PointSetProjection(0, 1));
		assertEquals(23d/288d, score.getRawScore(), TOL);
		assertEquals(5d/36d, score.getExpectedRandomScore(), TOL);
		assertEquals(23d/40d, score.getNormalizedScore(), TOL);
	}

	@Test
	public void testCategoricalScoreMatchesProbabilityResiduals() {
		double[][] values = { { 0.1 }, { 0.3 }, { 0.35 }, { 0.6 }, { 0.8 } };
		CategoricalSamplingDimension categorical =
				CategoricalSamplingDimension.forWeights(0.2, 0.3, 0.5);
		PointSet points = decorate(new ArrayPointSet(values), categorical);
		ProjectionScore score = scorer.scoreProjection(points, new PointSetProjection(0));
		double expectedRaw = square(0.2-0.2)+square(0.4-0.3)+square(0.4-0.5);
		assertEquals(expectedRaw, score.getRawScore(), TOL);
		assertEquals((1d-(square(0.2)+square(0.3)+square(0.5)))/values.length,
				score.getExpectedRandomScore(), TOL);
	}

	@Test
	public void testPureCategoricalProjectionMatchesIndependentBruteForce() {
		double[][] values = {
				{ 0.05, 0.10, 0.80 },
				{ 0.15, 0.45, 0.20 },
				{ 0.35, 0.75, 0.55 },
				{ 0.60, 0.20, 0.10 },
				{ 0.90, 0.90, 0.70 },
				{ 0.95, 0.30, 0.40 }
		};
		PointSet points = decorate(new ArrayPointSet(values),
				CategoricalSamplingDimension.forWeights(0.2, 0.3, 0.5),
				CategoricalSamplingDimension.forWeights(0.4, 0.6),
				CategoricalSamplingDimension.forWeights(0.25, 0.25, 0.25, 0.25));
		PointSetProjection projection = new PointSetProjection(0, 1, 2);
		assertEquals(bruteRawScore(points, projection), scorer.scoreProjection(points, projection).getRawScore(), TOL);
	}

	@Test
	public void testMixedProjectionChecksCategoriesBeforeContinuousKernel() {
		AtomicInteger kernelValueCalls = new AtomicInteger();
		DiscrepancyKernel countingKernel = new DiscrepancyKernel() {
			@Override public double value(double value1, double value2) {
				kernelValueCalls.incrementAndGet();
				return 1d-Math.max(value1, value2);
			}
			@Override public double targetMean(double value) { return 0.5d*(1d-value*value); }
			@Override public double targetGrandMean() { return 1d/3d; }
			@Override public double targetDiagonalMean() { return 0.5d; }
		};
		SamplingDimension countingContinuous = new SamplingDimension() {
			@Override public DiscrepancyKernel getDiscrepancyKernel() { return countingKernel; }
			@Override public DiscretizedDiscrepancyKernel getDiscretizedKernel(int preferredBins) {
				return ContinuousSamplingDimension.INSTANCE.getDiscretizedKernel(preferredBins);
			}
		};
		PointSet points = decorate(new ArrayPointSet(new double[][] {
				{ 0.1, 0.1 }, { 0.3, 0.2 }, { 0.6, 0.7 }, { 0.9, 0.8 }
		}), countingContinuous, CategoricalSamplingDimension.forWeights(0.5, 0.5));
		scorer.scoreProjection(points, new PointSetProjection(0, 1));
		// Preparation evaluates four diagonal values. Only the two same-category off-diagonal pairs should reach the
		// continuous kernel; the other four pairs are rejected by categorical equality first.
		assertEquals(6, kernelValueCalls.get());
	}

	@Test
	public void testMixedProjectionMatchesIndependentBruteForce() {
		double[][] values = {
				{ 0.08, 0.12, 0.75 },
				{ 0.31, 0.61, 0.10 },
				{ 0.57, 0.47, 0.42 },
				{ 0.83, 0.91, 0.68 }
		};
		CategoricalSamplingDimension categorical = CategoricalSamplingDimension.forWeights(1d, 2d, 1d);
		PointSet points = decorate(new ArrayPointSet(values),
				ContinuousSamplingDimension.INSTANCE, categorical, ContinuousSamplingDimension.INSTANCE);
		PointSetProjection projection = new PointSetProjection(0, 1, 2);
		ProjectionScore score = scorer.scoreProjection(points, projection);
		assertEquals(bruteRawScore(points, projection), score.getRawScore(), TOL);
	}

	@Test
	public void testProjectionEnumerationAndAggregation() {
		double[][] values = {
				{ 0.1, 0.2, 0.3, 0.4 },
				{ 0.6, 0.7, 0.8, 0.9 }
		};
		PointSetScoringConfig config = PointSetScoringConfig.builder().maxOrder(3).build();
		PointSetScore score = scorer.score(new ArrayPointSet(values), config);
		assertEquals(14, score.getProjectionScores().size());
		assertEquals(3, score.getOrderMeanScores().size());
		assertEquals(4, countOrder(score, 1));
		assertEquals(6, countOrder(score, 2));
		assertEquals(4, countOrder(score, 3));
		double expectedAggregate = (score.getOrderMeanScore(1)+score.getOrderMeanScore(2)
				+0.5*score.getOrderMeanScore(3))/2.5;
		assertEquals(expectedAggregate, score.getNormalizedScore(), TOL);
	}

	@Test
	public void testMaxOrderConvenienceMethod() {
		double[][] values = {
				{ 0.1, 0.2, 0.3, 0.4 },
				{ 0.6, 0.7, 0.8, 0.9 }
		};
		PointSetScore score = scorer.score(new ArrayPointSet(values), 3);
		assertEquals(14, score.getProjectionScores().size());
		assertEquals(3, score.getOrderMeanScores().size());
	}

	@Test
	public void testParallelExactScoringMatchesSerial() {
		double[][] values = new double[48][5];
		Random random = new Random(19384L);
		for (int p=0; p<values.length; p++)
			for (int d=0; d<values[p].length; d++)
				values[p][d] = random.nextDouble();
		PointSet points = decorate(new ArrayPointSet(values), ContinuousSamplingDimension.INSTANCE,
				CategoricalSamplingDimension.forWeights(0.2, 0.3, 0.5), ContinuousSamplingDimension.INSTANCE,
				CategoricalSamplingDimension.forWeights(0.65, 0.35), ContinuousSamplingDimension.INSTANCE);
		PointSetScore serial = new ExactPointSetScorer().score(points, 3);
		ExactPointSetScorer parallelScorer = new ExactPointSetScorer(4);
		PointSetScore parallel = parallelScorer.score(points, 3);
		assertEquals(4, parallelScorer.getParallelism());
		assertEquivalentScores(serial, parallel, TOL);
		assertEquivalentScores(parallel, parallelScorer.score(points, 3), 0d);
	}

	@Test
	public void testOptimizedExactScorerMatchesReference() {
		double[][] values = new double[36][6];
		Random random = new Random(721653L);
		for (int point=0; point<values.length; point++)
			for (int dimension=0; dimension<values[point].length; dimension++)
				values[point][dimension] = random.nextDouble();
		PointSet points = decorate(new ArrayPointSet(values),
				ContinuousSamplingDimension.INSTANCE,
				CategoricalSamplingDimension.forWeights(0.2, 0.3, 0.5),
				ContinuousSamplingDimension.INSTANCE,
				CategoricalSamplingDimension.forWeights(0.65, 0.35),
				ContinuousSamplingDimension.INSTANCE,
				CategoricalSamplingDimension.forWeights(0.1, 0.2, 0.3, 0.4));
		PointSetScoringConfig automatic = PointSetScoringConfig.builder().maxOrder(4).build();
		PointSetScoringConfig explicit = PointSetScoringConfig.builder().projections(
				new PointSetProjection(0),
				new PointSetProjection(1, 3),
				new PointSetProjection(0, 2, 4),
				new PointSetProjection(0, 1, 3, 5),
				new PointSetProjection(1, 2, 4, 5)).build();
		PointSetScorer reference = new ReferenceExactPointSetScorer();
		for (PointSetScoringConfig config : List.of(automatic, explicit)) {
			PointSetScore expected = reference.score(points, config);
			assertEquivalentScores(expected, new ExactPointSetScorer().score(points, config), TOL);
			assertEquivalentScores(expected, new ExactPointSetScorer(4).score(points, config), TOL);
		}
	}

	@Test(expected=IllegalArgumentException.class)
	public void testNonPositiveExactScorerParallelismRejected() {
		new ExactPointSetScorer(0);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testDirectProjectionDimensionValidated() {
		scorer.scoreProjection(new ArrayPointSet(new double[][] { { 0.25 }, { 0.75 } }),
				new PointSetProjection(1));
	}

	@Test(expected=IllegalArgumentException.class)
	public void testDirectInactiveProjectionRejected() {
		PointSet points = decorate(new ArrayPointSet(new double[][] { { 0.25 }, { 0.75 } }),
				InactiveSamplingDimension.INSTANCE);
		scorer.scoreProjection(points, new PointSetProjection(0));
	}

	@Test
	public void testExplicitProjectionsAndWeights() {
		PointSet points = new ArrayPointSet(new double[][] {
				{ 0.1, 0.2, 0.3 }, { 0.4, 0.8, 0.6 }, { 0.9, 0.5, 0.7 }
		});
		PointSetScoringConfig config = PointSetScoringConfig.builder()
				.projections(new PointSetProjection(0), new PointSetProjection(0, 2))
				.orderWeight(1, 2d).orderWeight(2, 1d).build();
		PointSetScore score = scorer.score(points, config);
		assertEquals(2, score.getProjectionScores().size());
		assertEquals((2d*score.getOrderMeanScore(1)+score.getOrderMeanScore(2))/3d,
				score.getNormalizedScore(), TOL);
		assertEquals(String.format(Locale.US, "PointSetScore[normalizedScore=%.5f, orderMeans={1=%.5f, 2=%.5f}]",
				score.getNormalizedScore(), score.getOrderMeanScore(1), score.getOrderMeanScore(2)), score.toString());
	}

	@Test
	public void testCategoryOrderInvariance() {
		PointSet first = decorate(new ArrayPointSet(new double[][] {
				{ 0.1 }, { 0.3 }, { 0.35 }, { 0.6 }, { 0.8 }
		}), CategoricalSamplingDimension.forWeights(0.2, 0.3, 0.5));
		PointSet reordered = decorate(new ArrayPointSet(new double[][] {
				{ 0.1 }, { 0.2 }, { 0.6 }, { 0.7 }, { 0.9 }
		}), CategoricalSamplingDimension.forWeights(0.5, 0.2, 0.3));
		double firstScore = scorer.scoreProjection(first, new PointSetProjection(0)).getRawScore();
		double reorderedScore = scorer.scoreProjection(reordered, new PointSetProjection(0)).getRawScore();
		assertEquals(firstScore, reorderedScore, TOL);
	}

	@Test
	public void testIIDRandomNormalization() {
		int samples = 64;
		int realizations = 300;
		Random random = new Random(87234L);
		double continuousSum = 0d;
		double categoricalSum = 0d;
		double mixedSum = 0d;
		CategoricalSamplingDimension categorical = CategoricalSamplingDimension.forWeights(0.2, 0.3, 0.5);
		PointSetProjection oneD = new PointSetProjection(0);
		PointSetProjection threeD = new PointSetProjection(0, 1, 2);
		for (int r=0; r<realizations; r++) {
			double[][] values = new double[samples][3];
			for (int p=0; p<samples; p++)
				for (int d=0; d<3; d++)
					values[p][d] = random.nextDouble();
			PointSet continuous = new ArrayPointSet(values);
			PointSet mixed = decorate(continuous, ContinuousSamplingDimension.INSTANCE,
					categorical, ContinuousSamplingDimension.INSTANCE);
			continuousSum += scorer.scoreProjection(continuous, oneD).getNormalizedScore();
			categoricalSum += scorer.scoreProjection(decorate(new ArrayPointSet(column(values, 1)), categorical), oneD)
					.getNormalizedScore();
			mixedSum += scorer.scoreProjection(mixed, threeD).getNormalizedScore();
		}
		assertEquals(1d, continuousSum/realizations, 0.1);
		assertEquals(1d, categoricalSum/realizations, 0.1);
		assertEquals(1d, mixedSum/realizations, 0.15);
	}

	@Test
	public void testMaxOrderIsCappedByDimensions() {
		PointSetScore score = scorer.score(new ArrayPointSet(new double[][] { { 0.25 }, { 0.75 } }));
		assertEquals(1, score.getProjectionScores().size());
		assertEquals(1, score.getOrderMeanScores().size());
		assertEquals(0.25, score.getNormalizedScore(), TOL);
	}

	@Test(expected=IllegalArgumentException.class)
	public void testCustomPointSetCoordinatesAreValidated() {
		PointSet invalid = new PointSet() {
			@Override public int size() { return 1; }
			@Override public int dimensions() { return 1; }
			@Override public double get(int pointIndex, int dimensionIndex) { return 1d; }
		};
		scorer.scoreProjection(invalid, new PointSetProjection(0));
	}

	@Test
	public void testExactScorerReadsCoordinatesOnlyDuringValidationAndPreparation() {
		ArrayPointSet delegate = new ArrayPointSet(new double[][] {
				{ 0.1, 0.2, 0.3 }, { 0.4, 0.8, 0.6 }, { 0.9, 0.5, 0.7 }, { 0.25, 0.35, 0.45 }
		});
		int[] coordinateReads = { 0 };
		PointSet counting = new PointSet() {
			@Override public int size() { return delegate.size(); }
			@Override public int dimensions() { return delegate.dimensions(); }
			@Override public double get(int pointIndex, int dimensionIndex) {
				coordinateReads[0]++;
				return delegate.get(pointIndex, dimensionIndex);
			}
		};
		scorer.score(counting, 3);
		// One complete read validates input and a second snapshots it; projection loops must not return to the PointSet.
		assertEquals(2*delegate.size()*delegate.dimensions(), coordinateReads[0]);
	}

	@Test
	public void testQuantizedBalancedStatesHaveZeroScore() {
		PointSet points = new ArrayPointSet(new double[][] { { 0.1 }, { 0.4 }, { 0.6 }, { 0.9 } });
		ProjectionScore score = new QuantizedPointSetScorer(2)
				.scoreProjection(points, new PointSetProjection(0));
		assertEquals(0d, score.getRawScore(), TOL);
		assertEquals((0.5-0.375)/points.size(), score.getExpectedRandomScore(), TOL);
	}

	@Test
	public void testQuantizedSupportsThreeDimensions() {
		double[][] values = {
				{ 0.08, 0.12, 0.75 },
				{ 0.31, 0.61, 0.10 },
				{ 0.57, 0.47, 0.42 },
				{ 0.83, 0.91, 0.68 }
		};
		PointSet points = decorate(new ArrayPointSet(values), ContinuousSamplingDimension.INSTANCE,
				CategoricalSamplingDimension.forWeights(1d, 2d, 1d), ContinuousSamplingDimension.INSTANCE);
		PointSetProjection projection = new PointSetProjection(0, 1, 2);
		ProjectionScore score = new QuantizedPointSetScorer(4).scoreProjection(points, projection);
		assertEquals(bruteDiscretizedRawScore(points, projection, 4), score.getRawScore(), TOL);
	}

	@Test
	public void testQuantizedIIDRandomNormalization() {
		int samples = 64;
		int realizations = 250;
		Random random = new Random(72194L);
		CategoricalSamplingDimension categorical = CategoricalSamplingDimension.forWeights(0.2, 0.3, 0.5);
		PointSetScoringConfig config = PointSetScoringConfig.builder()
				.projections(new PointSetProjection(0, 1, 2)).build();
		PointSetScorer scorer = new QuantizedPointSetScorer(8);
		double sum = 0d;
		for (int r=0; r<realizations; r++) {
			double[][] values = new double[samples][3];
			for (int p=0; p<samples; p++)
				for (int d=0; d<3; d++)
					values[p][d] = random.nextDouble();
			PointSet points = decorate(new ArrayPointSet(values), ContinuousSamplingDimension.INSTANCE,
					categorical, ContinuousSamplingDimension.INSTANCE);
			sum += scorer.score(points, config).getNormalizedScore();
		}
		assertEquals(1d, sum/realizations, 0.15);
	}

	private static PointSet decorate(PointSet pointSet, SamplingDimension... dimensions) {
		return new DimensionedPointSet(pointSet, List.of(dimensions));
	}

	private static double bruteRawScore(PointSet points, PointSetProjection projection) {
		List<DiscrepancyKernel> kernels = new ArrayList<>();
		double grandMean = 1d;
		for (int i=0; i<projection.order(); i++) {
			DiscrepancyKernel kernel = points.getDimension(projection.dimension(i)).getDiscrepancyKernel();
			kernels.add(kernel);
			grandMean *= kernel.targetGrandMean();
		}
		double target = 0d;
		for (int p=0; p<points.size(); p++) {
			double product = 1d;
			for (int i=0; i<projection.order(); i++)
				product *= kernels.get(i).targetMean(points.get(p, projection.dimension(i)));
			target += product;
		}
		double pairs = 0d;
		for (int p1=0; p1<points.size(); p1++) {
			for (int p2=0; p2<points.size(); p2++) {
				double product = 1d;
				for (int i=0; i<projection.order(); i++) {
					int dimension = projection.dimension(i);
					product *= kernels.get(i).value(points.get(p1, dimension), points.get(p2, dimension));
				}
				pairs += product;
			}
		}
		return grandMean-2d*target/points.size()+pairs/((double)points.size()*points.size());
	}

	private static double bruteDiscretizedRawScore(PointSet points, PointSetProjection projection, int bins) {
		List<DiscretizedDiscrepancyKernel> kernels = new ArrayList<>();
		double grandMean = 1d;
		for (int i=0; i<projection.order(); i++) {
			DiscretizedDiscrepancyKernel kernel =
					points.getDimension(projection.dimension(i)).getDiscretizedKernel(bins);
			kernels.add(kernel);
			grandMean *= kernel.targetGrandMean();
		}
		double target = 0d;
		for (int p=0; p<points.size(); p++) {
			double product = 1d;
			for (int i=0; i<projection.order(); i++) {
				int dimension = projection.dimension(i);
				product *= kernels.get(i).targetMean(kernels.get(i).state(points.get(p, dimension)));
			}
			target += product;
		}
		double pairs = 0d;
		for (int p1=0; p1<points.size(); p1++) {
			for (int p2=0; p2<points.size(); p2++) {
				double product = 1d;
				for (int i=0; i<projection.order(); i++) {
					int dimension = projection.dimension(i);
					DiscretizedDiscrepancyKernel kernel = kernels.get(i);
					product *= kernel.value(kernel.state(points.get(p1, dimension)),
							kernel.state(points.get(p2, dimension)));
				}
				pairs += product;
			}
		}
		return grandMean-2d*target/points.size()+pairs/((double)points.size()*points.size());
	}

	private static double[][] column(double[][] values, int column) {
		double[][] ret = new double[values.length][1];
		for (int i=0; i<values.length; i++)
			ret[i][0] = values[i][column];
		return ret;
	}

	private static int countOrder(PointSetScore score, int order) {
		int count = 0;
		for (ProjectionScore projection : score.getProjectionScores())
			if (projection.getProjection().order() == order)
				count++;
		return count;

	}

	private static void assertEquivalentScores(PointSetScore expected, PointSetScore actual, double tolerance) {
		assertEquals(expected.getNormalizedScore(), actual.getNormalizedScore(), tolerance);
		assertEquals(expected.getOrderMeanScores().keySet(), actual.getOrderMeanScores().keySet());
		for (int order : expected.getOrderMeanScores().keySet())
			assertEquals(expected.getOrderMeanScore(order), actual.getOrderMeanScore(order), tolerance);
		assertEquals(expected.getProjectionScores().size(), actual.getProjectionScores().size());
		for (int i=0; i<expected.getProjectionScores().size(); i++) {
			ProjectionScore expectedProjection = expected.getProjectionScores().get(i);
			ProjectionScore actualProjection = actual.getProjectionScores().get(i);
			assertEquals(expectedProjection.getProjection(), actualProjection.getProjection());
			assertEquals(expectedProjection.getRawScore(), actualProjection.getRawScore(), tolerance);
			assertEquals(expectedProjection.getExpectedRandomScore(), actualProjection.getExpectedRandomScore(), 0d);
		}
	}

	private static double square(double value) {
		return value*value;
	}
}
