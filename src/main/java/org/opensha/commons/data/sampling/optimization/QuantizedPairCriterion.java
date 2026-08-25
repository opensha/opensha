package org.opensha.commons.data.sampling.optimization;

import java.util.Arrays;

import org.opensha.commons.data.sampling.scoring.DiscretizedDiscrepancyKernel;
import org.opensha.commons.data.sampling.scoring.PointSetProjection;
import org.opensha.commons.data.sampling.scoring.ProjectionScore;

/**
 * Retained state and exact swap deltas for one quantized two-dimensional projection. The {@link #counts} table is a
 * compact replacement for iterating over all sampled points: a swap moves at most two observations from their old
 * joint bins into two crossed bins, regardless of the point-set size.
 */
final class QuantizedPairCriterion implements ProjectionScoreState {

	private static final double NEGATIVE_TOLERANCE = 1e-11;

	final PointSetProjection projection;
	final int dimension1;
	final int dimension2;
	final int leftDimension;
	final int rightDimension;
	final int numPoints;
	final DiscretizedDiscrepancyKernel leftKernel;
	final DiscretizedDiscrepancyKernel rightKernel;
	final int[][] states;
	final int[][] counts;
	// For each possible left state and occupied right state, this is its kernel similarity to all current left states.
	// It lets a proposal account for interactions with every sample by looping only over the right-hand state count.
	final double[][] leftKernelTimesCounts;
	final double expectedRandomScore;

	private final int[] changeRows = new int[4];
	private final int[] changeColumns = new int[4];
	private final int[] changeDeltas = new int[4];
	private int changeCount;
	private double targetSum;
	private double pairSum;
	private double rawScore;
	private double pendingTargetDelta;
	private double pendingPairDelta;
	private double pendingRawDelta;
	private boolean pending;

	QuantizedPairCriterion(PointSetProjection projection, DiscretizedDiscrepancyKernel[] kernels,
			int[][] states, int numPoints) {
		if (projection.order() != 2)
			throw new IllegalArgumentException("Pair criterion requires an order-2 projection, have " + projection);
		this.projection = projection;
		this.dimension1 = projection.dimension(0);
		this.dimension2 = projection.dimension(1);
		this.states = states;
		this.numPoints = numPoints;
		// Proposed-delta work scales with right states; accepted cache updates scale with left states.
		if (kernels[dimension1].stateCount() >= kernels[dimension2].stateCount()) {
			leftDimension = dimension1;
			rightDimension = dimension2;
		} else {
			leftDimension = dimension2;
			rightDimension = dimension1;
		}
		leftKernel = kernels[leftDimension];
		rightKernel = kernels[rightDimension];
		counts = new int[leftKernel.stateCount()][rightKernel.stateCount()];
		leftKernelTimesCounts = new double[leftKernel.stateCount()][rightKernel.stateCount()];
		expectedRandomScore = (leftKernel.targetDiagonalMean()*rightKernel.targetDiagonalMean()
				-leftKernel.targetGrandMean()*rightKernel.targetGrandMean())/numPoints;
		if (!Double.isFinite(expectedRandomScore) || expectedRandomScore <= 0d)
			throw new IllegalStateException("Expected random pair score must be finite and positive, have "
					+ expectedRandomScore + " for " + projection);
		rebuild();
	}

	double calculateSwapDelta(int point1, int point2) {
		if (pending)
			throw new IllegalStateException("Previous pair swap proposal has not been resolved");
		int left1 = states[leftDimension][point1];
		int left2 = states[leftDimension][point2];
		int right1 = states[rightDimension][point1];
		int right2 = states[rightDimension][point2];
		changeCount = 0;
		addChange(left1, right1, -1);
		addChange(left2, right2, -1);
		addChange(left2, right1, 1);
		addChange(left1, right2, 1);

		pendingTargetDelta = 0d;
		for (int i=0; i<changeCount; i++)
			pendingTargetDelta += changeDeltas[i]*leftKernel.targetMean(changeRows[i])
					*rightKernel.targetMean(changeColumns[i]);

		// The pair term compares every occupied joint bin with every other occupied joint bin. Only four bins change.
		// "cross" measures those changed bins against the current table through the cache above; "quadratic" corrects
		// for comparing the changes with themselves. This is the expanded difference Q(C+dC)-Q(C).
		double cross = 0d;
		for (int i=0; i<changeCount; i++) {
			double similarityToCurrent = 0d;
			for (int rightState=0; rightState<rightKernel.stateCount(); rightState++)
				similarityToCurrent += rightKernel.value(changeColumns[i], rightState)
						*leftKernelTimesCounts[changeRows[i]][rightState];
			cross += changeDeltas[i]*similarityToCurrent;
		}
		double quadratic = 0d;
		for (int i=0; i<changeCount; i++)
			for (int j=0; j<changeCount; j++)
				quadratic += changeDeltas[i]*changeDeltas[j]
						*leftKernel.value(changeRows[i], changeRows[j])
						*rightKernel.value(changeColumns[i], changeColumns[j]);
		pendingPairDelta = 2d*cross+quadratic;
		pendingRawDelta = -2d*pendingTargetDelta/numPoints
				+pendingPairDelta/((double)numPoints*numPoints);
		pending = true;
		return pendingRawDelta;
	}

	void applySwap() {
		checkPending();
		for (int i=0; i<changeCount; i++) {
			int row = changeRows[i];
			int column = changeColumns[i];
			int delta = changeDeltas[i];
			counts[row][column] += delta;
			for (int queryState=0; queryState<leftKernel.stateCount(); queryState++)
				leftKernelTimesCounts[queryState][column] += leftKernel.value(queryState, row)*delta;
		}
		targetSum += pendingTargetDelta;
		pairSum += pendingPairDelta;
		rawScore += pendingRawDelta;
		pending = false;
	}

	void discardSwap() {
		checkPending();
		pending = false;
	}

	void rebuild() {
		pending = false;
		for (int[] row : counts)
			Arrays.fill(row, 0);
		for (int p=0; p<numPoints; p++)
			counts[states[leftDimension][p]][states[rightDimension][p]]++;
		for (double[] row : leftKernelTimesCounts)
			Arrays.fill(row, 0d);
		for (int queryState=0; queryState<leftKernel.stateCount(); queryState++)
			for (int countState=0; countState<leftKernel.stateCount(); countState++) {
				double kernelValue = leftKernel.value(queryState, countState);
				for (int rightState=0; rightState<rightKernel.stateCount(); rightState++)
					leftKernelTimesCounts[queryState][rightState] += kernelValue*counts[countState][rightState];
			}

		targetSum = 0d;
		pairSum = 0d;
		for (int leftState=0; leftState<leftKernel.stateCount(); leftState++) {
			for (int rightState=0; rightState<rightKernel.stateCount(); rightState++) {
				int count = counts[leftState][rightState];
				targetSum += count*leftKernel.targetMean(leftState)*rightKernel.targetMean(rightState);
				if (count == 0)
					continue;
				for (int otherRight=0; otherRight<rightKernel.stateCount(); otherRight++)
					pairSum += count*rightKernel.value(rightState, otherRight)
							*leftKernelTimesCounts[leftState][otherRight];
			}
		}
		rawScore = leftKernel.targetGrandMean()*rightKernel.targetGrandMean()
				-2d*targetSum/numPoints+pairSum/((double)numPoints*numPoints);
		if (rawScore < 0d && rawScore >= -NEGATIVE_TOLERANCE)
			rawScore = 0d;
		if (!Double.isFinite(rawScore) || rawScore < 0d)
			throw new IllegalStateException("Invalid pair score " + rawScore + " for " + projection);
	}

	double expectedRandomScore() {
		return expectedRandomScore;
	}

	@Override
	public ProjectionScore score() {
		double snapshotRaw = rawScore;
		if (snapshotRaw < 0d && snapshotRaw >= -NEGATIVE_TOLERANCE)
			snapshotRaw = 0d;
		return ProjectionScore.of(projection, snapshotRaw, expectedRandomScore);
	}

	private void addChange(int row, int column, int delta) {
		for (int i=0; i<changeCount; i++) {
			if (changeRows[i] == row && changeColumns[i] == column) {
				changeDeltas[i] += delta;
				return;
			}
		}
		changeRows[changeCount] = row;
		changeColumns[changeCount] = column;
		changeDeltas[changeCount] = delta;
		changeCount++;
	}

	private void checkPending() {
		if (!pending)
			throw new IllegalStateException("No pair swap proposal is pending");
	}
}
