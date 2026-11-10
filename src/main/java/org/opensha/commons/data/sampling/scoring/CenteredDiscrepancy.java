package org.opensha.commons.data.sampling.scoring;

import java.util.concurrent.atomic.AtomicBoolean;

import org.opensha.commons.data.sampling.ContinuousSamplingDimension;
import org.opensha.commons.data.sampling.DimensionSwapGroup;
import org.opensha.commons.data.sampling.PointSet;
import org.opensha.commons.data.sampling.SwappablePointSet;
import org.opensha.commons.data.sampling.optimization.PointSetObjective;
import org.opensha.commons.data.sampling.optimization.PointSetObjective.SwapSession;

/**
 * Calculates the squared centered discrepancy of continuous points in a unit hypercube. Centered discrepancy is a
 * standard space-filling measure that treats the center and both boundaries of each dimension symmetrically. Lower
 * values indicate a point set whose empirical distribution more closely resembles the continuous uniform target.
 * <p>
 * This implementation follows the usual convention, also used by SciPy's {@code qmc.discrepancy(method="CD")}, of
 * returning the squared quantity without taking a square root. This is intrinsically a continuous-space measure;
 * selected categorical or inactive dimensions are treated as continuous coordinates with a warning.
 */
public final class CenteredDiscrepancy {

	private static final double NEGATIVE_ROUNDOFF_TOLERANCE = 1e-12;
	private static final double ONE_DIMENSIONAL_TARGET_GRAND_MEAN = 13d/12d;
	private static final AtomicBoolean WARNED_NON_CONTINUOUS = new AtomicBoolean();

	private CenteredDiscrepancy() {}

	/**
	 * Calculates centered discrepancy across every dimension.
	 *
	 * @param pointSet continuous point set
	 * @return squared centered discrepancy
	 */
	public static double score(PointSet pointSet) {
		ProjectionDiscrepancyUtils.validatePointSet(pointSet);
		int[] dimensions = new int[pointSet.dimensions()];
		for (int d=0; d<dimensions.length; d++)
			dimensions[d] = d;
		return scoreValidated(pointSet, new PointSetProjection(dimensions));
	}

	/**
	 * Calculates centered discrepancy after retaining only the selected dimensions.
	 *
	 * @param pointSet point set supplying coordinates
	 * @param projection continuous dimensions to retain
	 * @return squared centered discrepancy of the projected points
	 */
	public static double score(PointSet pointSet, PointSetProjection projection) {
		ProjectionDiscrepancyUtils.validatePointSet(pointSet);
		ProjectionDiscrepancyUtils.validateProjection(projection, pointSet.dimensions());
		return scoreValidated(pointSet, projection);
	}

	/** @return optimization objective calculating centered discrepancy across every dimension */
	public static PointSetObjective objective() {
		return createObjective(null);
	}

	/** @return optimization objective calculating centered discrepancy over the selected dimensions */
	public static PointSetObjective objective(PointSetProjection projection) {
		if (projection == null)
			throw new NullPointerException("Projection cannot be null");
		return createObjective(projection);
	}

	private static PointSetObjective createObjective(PointSetProjection projection) {
		return new PointSetObjective() {
			@Override
			public double evaluate(PointSet pointSet) {
				return projection == null ? score(pointSet) : score(pointSet, projection);
			}

			@Override
			public SwapSession prepare(SwappablePointSet pointSet) {
				return new CenteredDiscrepancySwapSession(pointSet, projection);
			}
		};
	}

	private static double scoreValidated(PointSet pointSet, PointSetProjection projection) {
		warnIfNonContinuous(pointSet, projection);

		int numPoints = pointSet.size();
		double targetSum = 0d;
		double pairSum = 0d;
		for (int p1=0; p1<numPoints; p1++) {
			double targetProduct = 1d;
			double diagonalProduct = 1d;
			for (int j=0; j<projection.order(); j++) {
				double value = pointSet.get(p1, projection.dimension(j));
				targetProduct *= targetMean(value);
				diagonalProduct *= pairKernel(value, value);
			}
			targetSum += targetProduct;
			pairSum += diagonalProduct;
			// The kernel is symmetric, so evaluate each off-diagonal pair once and count both matrix entries.
			for (int p2=0; p2<p1; p2++) {
				double pairProduct = 1d;
				for (int j=0; j<projection.order(); j++) {
					int dimension = projection.dimension(j);
					pairProduct *= pairKernel(pointSet.get(p1, dimension), pointSet.get(p2, dimension));
				}
				pairSum += 2d*pairProduct;
			}
		}

		double targetGrandMean = Math.pow(ONE_DIMENSIONAL_TARGET_GRAND_MEAN, projection.order());
		double targetTerm = 2d*targetSum/numPoints;
		double pairTerm = pairSum/((double)numPoints*numPoints);
		double discrepancy = targetGrandMean-targetTerm+pairTerm;
		if (!Double.isFinite(discrepancy))
			throw new IllegalStateException("Calculated non-finite centered discrepancy for projection "+projection);
		if (discrepancy < 0d) {
			double scale = Math.max(1d, Math.abs(targetGrandMean)+Math.abs(targetTerm)+Math.abs(pairTerm));
			if (discrepancy >= -NEGATIVE_ROUNDOFF_TOLERANCE*scale)
				return 0d;
			throw new IllegalStateException("Calculated materially negative centered discrepancy "+discrepancy
					+" for projection "+projection);
		}
		return discrepancy;
	}

	/* Mean kernel similarity between a fixed coordinate and an ideal uniform coordinate. */
	private static double targetMean(double value) {
		double centered = value-0.5d;
		return 1d+0.5d*Math.abs(centered)-0.5d*centered*centered;
	}

	/* One-dimensional centered-discrepancy kernel for two observed coordinates. */
	private static double pairKernel(double value1, double value2) {
		return 1d+0.5d*Math.abs(value1-0.5d)+0.5d*Math.abs(value2-0.5d)
				-0.5d*Math.abs(value1-value2);
	}

	/** Incremental state specialized for repeated grouped swaps during hill climbing. */
	private static final class CenteredDiscrepancySwapSession implements SwapSession {

		private final SwappablePointSet pointSet;
		private final PointSetProjection projection;
		private final int[][] groupPositions;
		private final double[][] values;
		private final double[][] centerDistances;
		private final double[] targetProducts;
		private final double[] diagonalProducts;

		private long expectedModificationCount;
		private double currentValue;
		private boolean pending;
		private int pendingGroup;
		private int pendingPoint1;
		private int pendingPoint2;
		private double pendingValue;
		private double pendingTargetProduct1;
		private double pendingTargetProduct2;
		private double pendingDiagonalProduct1;
		private double pendingDiagonalProduct2;

		CenteredDiscrepancySwapSession(SwappablePointSet pointSet, PointSetProjection requestedProjection) {
			if (pointSet == null)
				throw new NullPointerException("Swappable point set cannot be null");
			ProjectionDiscrepancyUtils.validatePointSet(pointSet);
			this.pointSet = pointSet;
			if (requestedProjection == null) {
				int[] dimensions = new int[pointSet.dimensions()];
				for (int d=0; d<dimensions.length; d++)
					dimensions[d] = d;
				this.projection = new PointSetProjection(dimensions);
			} else {
				ProjectionDiscrepancyUtils.validateProjection(requestedProjection, pointSet.dimensions());
				this.projection = requestedProjection;
			}
			warnIfNonContinuous(pointSet, projection);
			this.groupPositions = buildGroupPositions(pointSet, projection);
			this.values = new double[pointSet.size()][projection.order()];
			this.centerDistances = new double[pointSet.size()][projection.order()];
			this.targetProducts = new double[pointSet.size()];
			this.diagonalProducts = new double[pointSet.size()];
			this.expectedModificationCount = pointSet.modificationCount();
			this.currentValue = rebuild();
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
			validateSwap(groupIndex, point1, point2);
			int[] positions = groupPositions[groupIndex];
			pending = true;
			pendingGroup = groupIndex;
			pendingPoint1 = point1;
			pendingPoint2 = point2;

			// Moving no scored coordinates, or moving the entire scored row, cannot change the point set as a set.
			if (positions.length == 0 || positions.length == projection.order()) {
				pendingValue = currentValue;
				pendingTargetProduct1 = positions.length == 0 ? targetProducts[point1] : targetProducts[point2];
				pendingTargetProduct2 = positions.length == 0 ? targetProducts[point2] : targetProducts[point1];
				pendingDiagonalProduct1 = positions.length == 0 ? diagonalProducts[point1] : diagonalProducts[point2];
				pendingDiagonalProduct2 = positions.length == 0 ? diagonalProducts[point2] : diagonalProducts[point1];
				return 0d;
			}

			pendingTargetProduct1 = swappedRowProduct(targetProducts[point1], point1, point2, positions, true);
			pendingTargetProduct2 = swappedRowProduct(targetProducts[point2], point2, point1, positions, true);
			pendingDiagonalProduct1 = swappedRowProduct(diagonalProducts[point1], point1, point2, positions, false);
			pendingDiagonalProduct2 = swappedRowProduct(diagonalProducts[point2], point2, point1, positions, false);

			double targetDelta = pendingTargetProduct1+pendingTargetProduct2
					-targetProducts[point1]-targetProducts[point2];
			double pairDelta = pendingDiagonalProduct1+pendingDiagonalProduct2
					-diagonalProducts[point1]-diagonalProducts[point2];
			for (int other=0; other<values.length; other++) {
				if (other == point1 || other == point2)
					continue;
				double oldProduct1 = pairProduct(point1, other);
				double oldProduct2 = pairProduct(point2, other);
				double ratio1 = 1d;
				double ratio2 = 1d;
				for (int position : positions) {
					double oldFactor1 = pairFactor(point1, other, position);
					double oldFactor2 = pairFactor(point2, other, position);
					ratio1 *= pairFactor(point2, other, position)/oldFactor1;
					ratio2 *= pairFactor(point1, other, position)/oldFactor2;
				}
				pairDelta += 2d*(oldProduct1*(ratio1-1d)+oldProduct2*(ratio2-1d));
			}
			double delta = -2d*targetDelta/values.length
					+pairDelta/((double)values.length*values.length);
			pendingValue = currentValue+delta;
			if (!Double.isFinite(pendingValue))
				throw new IllegalStateException("Calculated non-finite centered discrepancy after proposed swap");
			return delta;
		}

		@Override
		public void applySwap() {
			checkSynchronized();
			checkPending();
			pointSet.swap(pendingGroup, pendingPoint1, pendingPoint2);
			for (int position : groupPositions[pendingGroup]) {
				double value = values[pendingPoint1][position];
				values[pendingPoint1][position] = values[pendingPoint2][position];
				values[pendingPoint2][position] = value;
				double distance = centerDistances[pendingPoint1][position];
				centerDistances[pendingPoint1][position] = centerDistances[pendingPoint2][position];
				centerDistances[pendingPoint2][position] = distance;
			}
			targetProducts[pendingPoint1] = pendingTargetProduct1;
			targetProducts[pendingPoint2] = pendingTargetProduct2;
			diagonalProducts[pendingPoint1] = pendingDiagonalProduct1;
			diagonalProducts[pendingPoint2] = pendingDiagonalProduct2;
			currentValue = pendingValue;
			expectedModificationCount = pointSet.modificationCount();
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
			currentValue = rebuild();
			return currentValue;
		}

		private double rebuild() {
			for (int point=0; point<values.length; point++) {
				double targetProduct = 1d;
				double diagonalProduct = 1d;
				for (int position=0; position<projection.order(); position++) {
					double value = pointSet.get(point, projection.dimension(position));
					double distance = Math.abs(value-0.5d);
					values[point][position] = value;
					centerDistances[point][position] = distance;
					targetProduct *= targetFactor(distance);
					diagonalProduct *= diagonalFactor(distance);
				}
				targetProducts[point] = targetProduct;
				diagonalProducts[point] = diagonalProduct;
			}
			return scorePrepared();
		}

		private double scorePrepared() {
			double targetSum = 0d;
			double pairSum = 0d;
			for (int point1=0; point1<values.length; point1++) {
				targetSum += targetProducts[point1];
				pairSum += diagonalProducts[point1];
				for (int point2=0; point2<point1; point2++)
					pairSum += 2d*pairProduct(point1, point2);
			}
			return Math.pow(ONE_DIMENSIONAL_TARGET_GRAND_MEAN, projection.order())
					-2d*targetSum/values.length+pairSum/((double)values.length*values.length);
		}

		private double swappedRowProduct(double oldProduct, int destination, int source,
				int[] positions, boolean target) {
			double ratio = 1d;
			for (int position : positions) {
				double oldDistance = centerDistances[destination][position];
				double newDistance = centerDistances[source][position];
				ratio *= (target ? targetFactor(newDistance) : diagonalFactor(newDistance))
						/(target ? targetFactor(oldDistance) : diagonalFactor(oldDistance));
			}
			return oldProduct*ratio;
		}

		private double pairProduct(int point1, int point2) {
			double product = 1d;
			for (int position=0; position<projection.order(); position++)
				product *= pairFactor(point1, point2, position);
			return product;
		}

		private double pairFactor(int point1, int point2, int position) {
			return 1d+0.5d*centerDistances[point1][position]+0.5d*centerDistances[point2][position]
					-0.5d*Math.abs(values[point1][position]-values[point2][position]);
		}

		private void validateSwap(int groupIndex, int point1, int point2) {
			if (groupIndex < 0 || groupIndex >= groupPositions.length)
				throw new IndexOutOfBoundsException("Swap-group index out of range: "+groupIndex);
			if (point1 < 0 || point1 >= values.length || point2 < 0 || point2 >= values.length)
				throw new IndexOutOfBoundsException("Swap point indexes out of range: "+point1+", "+point2);
			if (point1 == point2)
				throw new IllegalArgumentException("Swap points must be distinct");
		}

		private void checkSynchronized() {
			if (pointSet.modificationCount() != expectedModificationCount)
				throw new IllegalStateException("Point set was modified outside this objective session; expected modification "
						+expectedModificationCount+" but found "+pointSet.modificationCount());
		}

		private void checkPending() {
			if (!pending)
				throw new IllegalStateException("No swap proposal is pending");
		}
	}

	private static void warnIfNonContinuous(PointSet pointSet, PointSetProjection projection) {
		for (int position=0; position<projection.order(); position++) {
			int dimension = projection.dimension(position);
			if (pointSet.getDimension(dimension) != ContinuousSamplingDimension.INSTANCE) {
				if (WARNED_NON_CONTINUOUS.compareAndSet(false, true))
					System.err.println("WARNING: Centered discrepancy encountered non-continuous dimension metadata; "
							+"selected coordinates will be treated as continuous unit-hypercube dimensions");
				return;
			}
		}
	}

	private static int[][] buildGroupPositions(SwappablePointSet pointSet, PointSetProjection projection) {
		int[][] positions = new int[pointSet.swapGroupCount()][];
		boolean[] claimedDimensions = new boolean[pointSet.dimensions()];
		for (int groupIndex=0; groupIndex<positions.length; groupIndex++) {
			DimensionSwapGroup group = pointSet.getSwapGroup(groupIndex);
			int count = 0;
			for (int i=0; i<group.size(); i++) {
				int dimension = group.dimension(i);
				if (dimension < 0 || dimension >= pointSet.dimensions())
					throw new IllegalArgumentException("Swap group "+groupIndex+" contains invalid dimension "+dimension);
				if (claimedDimensions[dimension])
					throw new IllegalArgumentException("Dimension "+dimension+" belongs to multiple swap groups");
				claimedDimensions[dimension] = true;
				for (int position=0; position<projection.order(); position++)
					if (projection.dimension(position) == dimension)
						count++;
			}
			positions[groupIndex] = new int[count];
			int index = 0;
			for (int position=0; position<projection.order(); position++) {
				int dimension = projection.dimension(position);
				for (int i=0; i<group.size(); i++)
					if (group.dimension(i) == dimension)
						positions[groupIndex][index++] = position;
			}
		}
		return positions;
	}

	private static double targetFactor(double centerDistance) {
		return 1d+0.5d*centerDistance-0.5d*centerDistance*centerDistance;
	}

	private static double diagonalFactor(double centerDistance) {
		return 1d+centerDistance;
	}
}
