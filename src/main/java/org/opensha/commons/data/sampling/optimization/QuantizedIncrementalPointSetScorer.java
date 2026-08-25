package org.opensha.commons.data.sampling.optimization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.sampling.DimensionSwapGroup;
import org.opensha.commons.data.sampling.SwappablePointSet;
import org.opensha.commons.data.sampling.scoring.DiscretizedDiscrepancyKernel;
import org.opensha.commons.data.sampling.scoring.PointSetProjection;
import org.opensha.commons.data.sampling.scoring.PointSetScore;
import org.opensha.commons.data.sampling.scoring.PointSetScoringConfig;
import org.opensha.commons.data.sampling.scoring.ProjectionScore;

/**
 * Incremental quantized scorer for point-set configurations containing only one- and two-dimensional projections.
 * One-dimensional scores are invariant under permutation. Each mutable pair retains joint-state counts and a
 * factorization cache, reducing proposal evaluation to a loop over the smaller pair state space.
 */
public final class QuantizedIncrementalPointSetScorer implements IncrementalPointSetScorer {

	private static final double SCORE_TOLERANCE = 1e-10;

	private final SwappablePointSet pointSet;
	private final PointSetScoringConfig config;
	private final List<PointSetProjection> projections;
	private final DiscretizedDiscrepancyKernel[] kernels;
	private final int[][] states;
	private final int[] dimensionGroups;
	private final List<ProjectionScoreState> projectionStates;
	private final List<List<QuantizedPairCriterion>> criteriaByGroup;
	private final Map<Integer, Integer> projectionCountsByOrder;
	private final double aggregateOrderWeightSum;

	private long expectedModificationCount;
	private double currentNormalizedScore;
	private boolean pending;
	private int pendingGroup;
	private int pendingPoint1;
	private int pendingPoint2;
	private double pendingNormalizedDelta;

	public QuantizedIncrementalPointSetScorer(SwappablePointSet pointSet, int continuousBins) {
		this(pointSet, continuousBins, PointSetScoringConfig.defaults());
	}

	public QuantizedIncrementalPointSetScorer(SwappablePointSet pointSet, int continuousBins,
			PointSetScoringConfig config) {
		if (pointSet == null)
			throw new NullPointerException("Point set cannot be null");
		if (config == null)
			throw new NullPointerException("Scoring configuration cannot be null");
		if (continuousBins < 2)
			throw new IllegalArgumentException("Continuous quantization requires at least 2 bins, have " + continuousBins);
		if (pointSet.size() < 1 || pointSet.dimensions() < 1)
			throw new IllegalArgumentException("Point set must contain at least one point and dimension");
		this.pointSet = pointSet;
		this.config = config;
		this.projections = config.resolveProjections(pointSet);
		for (PointSetProjection projection : projections)
			if (projection.order() > 2)
				throw new IllegalArgumentException("Incremental scoring supports only order-1 and order-2 projections, have "
						+ projection);

		this.dimensionGroups = buildDimensionGroups(pointSet);
		this.kernels = new DiscretizedDiscrepancyKernel[pointSet.dimensions()];
		this.states = new int[pointSet.dimensions()][pointSet.size()];
		prepareStates(continuousBins);
		this.criteriaByGroup = new ArrayList<>(pointSet.swapGroupCount());
		for (int g=0; g<pointSet.swapGroupCount(); g++)
			criteriaByGroup.add(new ArrayList<>());
		this.projectionStates = new ArrayList<>(projections.size());
		this.projectionCountsByOrder = new HashMap<>();
		for (PointSetProjection projection : projections)
			projectionCountsByOrder.merge(projection.order(), 1, Integer::sum);

		double orderWeightSum = 0d;
		for (int order : projectionCountsByOrder.keySet())
			orderWeightSum += config.getOrderWeight(order);
		if (!(orderWeightSum > 0d))
			throw new IllegalArgumentException("At least one included projection order must have positive weight");
		this.aggregateOrderWeightSum = orderWeightSum;
		buildProjectionStates();
		this.currentNormalizedScore = calculateAggregateFromStates();
		this.expectedModificationCount = pointSet.modificationCount();
	}

	@Override
	public SwappablePointSet getPointSet() {
		return pointSet;
	}

	@Override
	public double getCurrentNormalizedScore() {
		checkSynchronized();
		return currentNormalizedScore;
	}

	@Override
	public PointSetScore getCurrentScore() {
		checkSynchronized();
		List<ProjectionScore> scores = snapshotProjectionScores();
		PointSetScore score = PointSetScore.aggregate(scores, config);
		if (Math.abs(score.getNormalizedScore()-currentNormalizedScore) > SCORE_TOLERANCE)
			throw new IllegalStateException("Incremental aggregate drift: cached=" + currentNormalizedScore
					+ ", rebuilt=" + score.getNormalizedScore());
		return score;
	}

	@Override
	public double evaluateSwap(int groupIndex, int point1, int point2) {
		checkSynchronized();
		if (pending)
			throw new IllegalStateException("Previous swap proposal has not been resolved");
		if (groupIndex < 0 || groupIndex >= pointSet.swapGroupCount())
			throw new IndexOutOfBoundsException("Swap-group index out of range: " + groupIndex);
		if (point1 < 0 || point1 >= pointSet.size() || point2 < 0 || point2 >= pointSet.size())
			throw new IndexOutOfBoundsException("Swap point indexes out of range: " + point1 + ", " + point2);
		if (point1 == point2)
			throw new IllegalArgumentException("Swap points must be distinct");

		double delta = 0d;
		for (QuantizedPairCriterion criterion : criteriaByGroup.get(groupIndex)) {
			double rawDelta = criterion.calculateSwapDelta(point1, point2);
			delta += aggregateCoefficient(2, criterion.expectedRandomScore())*rawDelta;
		}
		pending = true;
		pendingGroup = groupIndex;
		pendingPoint1 = point1;
		pendingPoint2 = point2;
		pendingNormalizedDelta = delta;
		return delta;
	}

	@Override
	public void applySwap() {
		checkSynchronized();
		checkPending();
		for (QuantizedPairCriterion criterion : criteriaByGroup.get(pendingGroup))
			criterion.applySwap();
		DimensionSwapGroup group = pointSet.getSwapGroup(pendingGroup);
		for (int i=0; i<group.size(); i++) {
			int[] dimensionStates = states[group.dimension(i)];
			int state = dimensionStates[pendingPoint1];
			dimensionStates[pendingPoint1] = dimensionStates[pendingPoint2];
			dimensionStates[pendingPoint2] = state;
		}
		pointSet.swap(pendingGroup, pendingPoint1, pendingPoint2);
		expectedModificationCount = pointSet.modificationCount();
		currentNormalizedScore += pendingNormalizedDelta;
		pending = false;
	}

	@Override
	public void discardSwap() {
		checkSynchronized();
		checkPending();
		for (QuantizedPairCriterion criterion : criteriaByGroup.get(pendingGroup))
			criterion.discardSwap();
		pending = false;
	}

	@Override
	public boolean hasPendingSwap() {
		return pending;
	}

	@Override
	public PointSetScore recalculate() {
		checkSynchronized();
		if (pending)
			throw new IllegalStateException("Cannot recalculate while a swap proposal is pending");
		for (ProjectionScoreState state : projectionStates)
			if (state instanceof QuantizedPairCriterion)
				((QuantizedPairCriterion)state).rebuild();
		currentNormalizedScore = calculateAggregateFromStates();
		return getCurrentScore();
	}

	private void prepareStates(int continuousBins) {
		for (int d=0; d<pointSet.dimensions(); d++) {
			if (pointSet.getDimension(d) == null)
				throw new NullPointerException("Point-set dimension " + d + " is null");
			if (!pointSet.getDimension(d).isActive())
				continue;
			kernels[d] = pointSet.getDimension(d).getDiscretizedKernel(continuousBins);
			if (kernels[d] == null || kernels[d].stateCount() < 2)
				throw new IllegalStateException("Dimension " + d + " must supply at least two discretized states");
			for (int p=0; p<pointSet.size(); p++) {
				double value = pointSet.get(p, d);
				if (!Double.isFinite(value) || value < 0d || value >= 1d)
					throw new IllegalArgumentException("Coordinate [" + p + "][" + d
							+ "] must be finite and in [0,1), have " + value);
				int state = kernels[d].state(value);
				if (state < 0 || state >= kernels[d].stateCount())
					throw new IllegalStateException("Dimension " + d + " returned invalid state " + state);
				states[d][p] = state;
			}
		}
	}

	private void buildProjectionStates() {
		for (PointSetProjection projection : projections) {
			if (projection.order() == 1) {
				projectionStates.add(fixedOneDimensionalScore(projection));
				continue;
			}
			QuantizedPairCriterion criterion = new QuantizedPairCriterion(projection, kernels, states, pointSet.size());
			int group1 = dimensionGroups[projection.dimension(0)];
			int group2 = dimensionGroups[projection.dimension(1)];
			// Moving both coordinates together only reorders the existing 2D points, so this projection cannot change.
			if (group1 == group2) {
				ProjectionScore fixedScore = criterion.score();
				projectionStates.add(() -> fixedScore);
			} else {
				projectionStates.add(criterion);
				if (group1 >= 0)
					criteriaByGroup.get(group1).add(criterion);
				if (group2 >= 0)
					criteriaByGroup.get(group2).add(criterion);
			}
		}
	}

	private ProjectionScoreState fixedOneDimensionalScore(PointSetProjection projection) {
		int dimension = projection.dimension(0);
		DiscretizedDiscrepancyKernel kernel = kernels[dimension];
		int[] counts = new int[kernel.stateCount()];
		for (int state : states[dimension])
			counts[state]++;
		double targetSum = 0d;
		double pairSum = 0d;
		for (int state1=0; state1<counts.length; state1++) {
			targetSum += counts[state1]*kernel.targetMean(state1);
			for (int state2=0; state2<counts.length; state2++)
				pairSum += (double)counts[state1]*counts[state2]*kernel.value(state1, state2);
		}
		double rawScore = kernel.targetGrandMean()-2d*targetSum/pointSet.size()
				+pairSum/((double)pointSet.size()*pointSet.size());
		if (rawScore < 0d && rawScore > -1e-11)
			rawScore = 0d;
		double expected = (kernel.targetDiagonalMean()-kernel.targetGrandMean())/pointSet.size();
		ProjectionScore fixedScore = ProjectionScore.of(projection, rawScore, expected);
		return () -> fixedScore;
	}

	private double calculateAggregateFromStates() {
		double score = 0d;
		for (ProjectionScoreState state : projectionStates) {
			ProjectionScore projection = state.score();
			score += aggregateCoefficient(projection.getProjection().order(), projection.getExpectedRandomScore())
					*projection.getRawScore();
		}
		return score;
	}

	private double aggregateCoefficient(int order, double expectedRandomScore) {
		return config.getOrderWeight(order)/(aggregateOrderWeightSum*projectionCountsByOrder.get(order)
				*expectedRandomScore);
	}

	private List<ProjectionScore> snapshotProjectionScores() {
		List<ProjectionScore> scores = new ArrayList<>(projectionStates.size());
		for (ProjectionScoreState state : projectionStates)
			scores.add(state.score());
		return scores;
	}

	private void checkSynchronized() {
		if (pointSet.modificationCount() != expectedModificationCount)
			throw new IllegalStateException("Point set was modified outside this scoring session; expected modification "
					+ expectedModificationCount + " but found " + pointSet.modificationCount());
	}

	private void checkPending() {
		if (!pending)
			throw new IllegalStateException("No swap proposal is pending");
	}

	private static int[] buildDimensionGroups(SwappablePointSet pointSet) {
		int[] groups = new int[pointSet.dimensions()];
		java.util.Arrays.fill(groups, -1);
		for (int g=0; g<pointSet.swapGroupCount(); g++) {
			DimensionSwapGroup group = pointSet.getSwapGroup(g);
			for (int i=0; i<group.size(); i++) {
				int dimension = group.dimension(i);
				if (dimension < 0 || dimension >= groups.length)
					throw new IllegalArgumentException("Swap group " + g + " contains invalid dimension " + dimension);
				if (groups[dimension] >= 0)
					throw new IllegalArgumentException("Dimension " + dimension + " belongs to multiple swap groups");
				groups[dimension] = g;
			}
		}
		return groups;
	}
}
