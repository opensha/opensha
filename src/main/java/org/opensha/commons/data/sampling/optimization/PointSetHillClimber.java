package org.opensha.commons.data.sampling.optimization;

import java.util.Arrays;
import java.util.Locale;
import java.util.random.RandomGenerator;

import org.opensha.commons.data.sampling.DimensionSwapGroup;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.SamplingDimension;
import org.opensha.commons.data.sampling.SwappablePointSet;
import org.opensha.commons.data.sampling.optimization.PointSetObjective.SwapSession;

/**
 * Generic strict hill climber for a {@link PointSetObjective}. Each iteration chooses a swap group uniformly, chooses
 * two distinct points uniformly, and commits the proposal only when it lowers the objective. Swap groups define which
 * dimensions move together; the optimizer does not need to know their scoring semantics.
 */
public final class PointSetHillClimber {

	private PointSetHillClimber() {}

	/** Prepares the objective against {@code pointSet} and performs the requested optimization. */
	public static Result optimize(SwappablePointSet pointSet, PointSetObjective objective,
			long iterations, RandomGenerator random) {
		if (objective == null)
			throw new NullPointerException("Point-set objective cannot be null");
		return optimize(objective.prepare(pointSet), iterations, random);
	}

	/** Performs optimization using an already prepared objective session. */
	public static Result optimize(SwapSession session, long iterations, RandomGenerator random) {
		if (session == null)
			throw new NullPointerException("Swap session cannot be null");
		if (random == null)
			throw new NullPointerException("Random generator cannot be null");
		if (iterations < 0L)
			throw new IllegalArgumentException("Iteration count cannot be negative, have " + iterations);
		if (session.hasPendingSwap())
			throw new IllegalStateException("Cannot start optimization with an unresolved swap proposal");
		SwappablePointSet pointSet = session.getPointSet();
		if (pointSet.swapGroupCount() == 0)
			throw new IllegalArgumentException("Point set has no swappable dimension groups");
		if (pointSet.size() < 2)
			throw new IllegalArgumentException("Point set must contain at least two points to optimize");

		double initialValue = session.getCurrentValue();
		long accepted = 0L;
		for (long i=0L; i<iterations; i++) {
			int group = random.nextInt(pointSet.swapGroupCount());
			int point1 = random.nextInt(pointSet.size());
			int point2 = random.nextInt(pointSet.size()-1);
			if (point2 >= point1)
				point2++;
			double delta = session.evaluateSwap(group, point1, point2);
			if (delta < 0d) {
				session.applySwap();
				accepted++;
			} else {
				session.discardSwap();
			}
		}
		return new Result(iterations, accepted, initialValue, session.getCurrentValue());
	}

	static SwapSession recalculatingSession(PointSetObjective objective, SwappablePointSet pointSet) {
		return new RecalculatingSwapSession(objective, pointSet);
	}

	private static final class RecalculatingSwapSession implements SwapSession {

		private final PointSetObjective objective;
		private final SwappablePointSet pointSet;
		private final int[] dimensionGroups;
		private long expectedModificationCount;
		private double currentValue;
		private boolean pending;
		private int pendingGroup;
		private int pendingPoint1;
		private int pendingPoint2;
		private double pendingValue;

		RecalculatingSwapSession(PointSetObjective objective, SwappablePointSet pointSet) {
			if (pointSet == null)
				throw new NullPointerException("Swappable point set cannot be null");
			this.objective = objective;
			this.pointSet = pointSet;
			this.dimensionGroups = buildDimensionGroups(pointSet);
			this.currentValue = requireFinite(objective.evaluate(pointSet));
			this.expectedModificationCount = pointSet.modificationCount();
		}

		@Override
		public SwappablePointSet getPointSet() {
			return pointSet;
		}

		@Override
		public double getCurrentValue() {
			checkSynchronized();
			return currentValue;
		}

		@Override
		public double evaluateSwap(int groupIndex, int point1, int point2) {
			checkSynchronized();
			if (pending)
				throw new IllegalStateException("Previous swap proposal has not been resolved");
			validateSwap(pointSet, groupIndex, point1, point2);
			PointSet proposed = new ProposedSwapPointSet(pointSet, dimensionGroups, groupIndex, point1, point2);
			pendingValue = requireFinite(objective.evaluate(proposed));
			pending = true;
			pendingGroup = groupIndex;
			pendingPoint1 = point1;
			pendingPoint2 = point2;
			return pendingValue-currentValue;
		}

		@Override
		public void applySwap() {
			checkSynchronized();
			checkPending();
			pointSet.swap(pendingGroup, pendingPoint1, pendingPoint2);
			expectedModificationCount = pointSet.modificationCount();
			currentValue = pendingValue;
			pending = false;
		}

		@Override
		public void discardSwap() {
			checkSynchronized();
			checkPending();
			pending = false;
		}

		@Override
		public boolean hasPendingSwap() {
			return pending;
		}

		@Override
		public double recalculate() {
			checkSynchronized();
			if (pending)
				throw new IllegalStateException("Cannot recalculate while a swap proposal is pending");
			currentValue = requireFinite(objective.evaluate(pointSet));
			return currentValue;
		}

		private void checkSynchronized() {
			if (pointSet.modificationCount() != expectedModificationCount)
				throw new IllegalStateException("Point set was modified outside this objective session; expected modification "
						+ expectedModificationCount + " but found " + pointSet.modificationCount());
		}

		private void checkPending() {
			if (!pending)
				throw new IllegalStateException("No swap proposal is pending");
		}
	}

	/** Read-only view that redirects the two proposed points only for dimensions in the selected swap group. */
	private static final class ProposedSwapPointSet implements PointSet {

		private final PointSet source;
		private final int[] dimensionGroups;
		private final int groupIndex;
		private final int point1;
		private final int point2;

		ProposedSwapPointSet(PointSet source, int[] dimensionGroups, int groupIndex, int point1, int point2) {
			this.source = source;
			this.dimensionGroups = dimensionGroups;
			this.groupIndex = groupIndex;
			this.point1 = point1;
			this.point2 = point2;
		}

		@Override
		public int size() {
			return source.size();
		}

		@Override
		public int dimensions() {
			return source.dimensions();
		}

		@Override
		public double get(int pointIndex, int dimensionIndex) {
			if (dimensionGroups[dimensionIndex] == groupIndex) {
				if (pointIndex == point1)
					pointIndex = point2;
				else if (pointIndex == point2)
					pointIndex = point1;
			}
			return source.get(pointIndex, dimensionIndex);
		}

		@Override
		public SamplingDimension getDimension(int dimensionIndex) {
			return source.getDimension(dimensionIndex);
		}
	}

	private static int[] buildDimensionGroups(SwappablePointSet pointSet) {
		int[] groups = new int[pointSet.dimensions()];
		Arrays.fill(groups, -1);
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

	private static void validateSwap(SwappablePointSet pointSet, int groupIndex, int point1, int point2) {
		if (groupIndex < 0 || groupIndex >= pointSet.swapGroupCount())
			throw new IndexOutOfBoundsException("Swap-group index out of range: " + groupIndex);
		if (point1 < 0 || point1 >= pointSet.size() || point2 < 0 || point2 >= pointSet.size())
			throw new IndexOutOfBoundsException("Swap point indexes out of range: " + point1 + ", " + point2);
		if (point1 == point2)
			throw new IllegalArgumentException("Swap points must be distinct");
	}

	private static double requireFinite(double value) {
		if (!Double.isFinite(value))
			throw new IllegalStateException("Point-set objective must return a finite value, have " + value);
		return value;
	}

	/** Summary of a completed point-set optimization run. */
	public static final class Result {

		private final long iterations;
		private final long acceptedSwaps;
		private final double initialValue;
		private final double finalValue;

		private Result(long iterations, long acceptedSwaps, double initialValue, double finalValue) {
			this.iterations = iterations;
			this.acceptedSwaps = acceptedSwaps;
			this.initialValue = initialValue;
			this.finalValue = finalValue;
		}

		public long getIterations() {
			return iterations;
		}

		public long getAcceptedSwaps() {
			return acceptedSwaps;
		}

		public double getInitialValue() {
			return initialValue;
		}

		public double getFinalValue() {
			return finalValue;
		}

		public double getValueReduction() {
			return initialValue-finalValue;
		}

		@Override
		public String toString() {
			return String.format(Locale.US,
					"PointSetHillClimber.Result[iterations=%d, accepted=%d, value=%.5f -> %.5f]",
					iterations, acceptedSwaps, initialValue, finalValue);
		}
	}
}
