package org.opensha.commons.data.sampling.scoring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.data.sampling.PointSet;

/**
 * Projection selection and per-order aggregation weights for {@link PointSetScorer}.
 */
public final class PointSetScoringConfig {

	private final int maxOrder;
	private final List<PointSetProjection> projections;
	private final Map<Integer, Double> orderWeights;

	private PointSetScoringConfig(int maxOrder, List<PointSetProjection> projections,
			Map<Integer, Double> orderWeights) {
		this.maxOrder = maxOrder;
		this.projections = projections;
		this.orderWeights = orderWeights;
	}

	/** @return a builder configured to score all 1D and 2D projections */
	public static Builder builder() {
		return new Builder();
	}

	/** @return default configuration scoring all 1D and 2D projections */
	public static PointSetScoringConfig defaults() {
		return builder().build();
	}

	public int getMaxOrder() {
		return maxOrder;
	}

	/**
	 * @return explicit projections, or an empty list when projections should be enumerated through {@link #getMaxOrder()}
	 */
	public List<PointSetProjection> getExplicitProjections() {
		return projections;
	}

	public boolean hasExplicitProjections() {
		return !projections.isEmpty();
	}

	/**
	 * Resolves this configuration to the concrete projections for a point-set dimensionality. Explicit projections are
	 * validated; otherwise all combinations through the configured maximum order are generated.
	 *
	 * @param dimensions point-set dimensionality
	 * @return immutable projection list
	 */
	public List<PointSetProjection> resolveProjections(int dimensions) {
		if (dimensions < 1)
			throw new IllegalArgumentException("Point-set dimensionality must be positive, have " + dimensions);
		if (hasExplicitProjections()) {
			for (PointSetProjection projection : projections)
				PointSetScoringUtils.validateProjection(projection, dimensions);
			return projections;
		}
		List<PointSetProjection> resolved = new ArrayList<>();
		for (int order=1; order<=Math.min(maxOrder, dimensions); order++)
			enumerateProjections(dimensions, new int[order], 0, 0, resolved);
		return Collections.unmodifiableList(resolved);
	}

	/** Resolves projections while omitting dimensions marked inactive by the point set. */
	public List<PointSetProjection> resolveProjections(PointSet pointSet) {
		if (pointSet == null)
			throw new NullPointerException("Point set cannot be null");
		if (hasExplicitProjections()) {
			for (PointSetProjection projection : projections) {
				PointSetScoringUtils.validateProjection(projection, pointSet.dimensions());
				for (int i=0; i<projection.order(); i++) {
					int dimension = projection.dimension(i);
					if (!pointSet.getDimension(dimension).isActive())
						throw new IllegalArgumentException("Explicit projection " + projection
								+ " contains inactive dimension " + dimension);
				}
			}
			return projections;
		}
		int activeCount = 0;
		for (int d=0; d<pointSet.dimensions(); d++)
			if (pointSet.getDimension(d).isActive())
				activeCount++;
		if (activeCount == 0)
			throw new IllegalArgumentException("Point set contains no active dimensions to score");
		int[] active = new int[activeCount];
		for (int d=0, index=0; d<pointSet.dimensions(); d++)
			if (pointSet.getDimension(d).isActive())
				active[index++] = d;
		List<PointSetProjection> resolved = new ArrayList<>();
		for (int order=1; order<=Math.min(maxOrder, active.length); order++)
			enumerateActiveProjections(active, new int[order], 0, 0, resolved);
		return Collections.unmodifiableList(resolved);
	}

	/**
	 * Returns the aggregation weight for a projection order. Unspecified orders use 1 for orders 1 and 2, then halve
	 * with each subsequent order.
	 */
	public double getOrderWeight(int order) {
		if (order < 1)
			throw new IllegalArgumentException("Projection order must be positive, have " + order);
		Double weight = orderWeights.get(order);
		if (weight != null)
			return weight;
		return order <= 2 ? 1d : Math.scalb(1d, 2-order);
	}

	public static final class Builder {
		private int maxOrder = 2;
		private List<PointSetProjection> projections = Collections.emptyList();
		private final Map<Integer, Double> orderWeights = new HashMap<>();

		private Builder() {}

		/**
		 * Scores every projection from order 1 through {@code maxOrder}. Calling this clears explicit projections.
		 */
		public Builder maxOrder(int maxOrder) {
			if (maxOrder < 1)
				throw new IllegalArgumentException("Maximum order must be positive, have " + maxOrder);
			this.maxOrder = maxOrder;
			this.projections = Collections.emptyList();
			return this;
		}

		/**
		 * Scores only the supplied projections. Calling this replaces any previous explicit projection list.
		 */
		public Builder projections(Collection<PointSetProjection> projections) {
			if (projections == null)
				throw new NullPointerException("Projections cannot be null");
			if (projections.isEmpty())
				throw new IllegalArgumentException("Explicit projection list cannot be empty");
			List<PointSetProjection> copy = new ArrayList<>(projections.size());
			Set<PointSetProjection> unique = new HashSet<>();
			for (PointSetProjection projection : projections) {
				if (projection == null)
					throw new NullPointerException("Projection cannot be null");
				if (!unique.add(projection))
					throw new IllegalArgumentException("Duplicate projection: " + projection);
				copy.add(projection);
			}
			this.projections = copy;
			return this;
		}

		public Builder projections(PointSetProjection... projections) {
			if (projections == null)
				throw new NullPointerException("Projections cannot be null");
			return projections(List.of(projections));
		}

		/** Sets a nonnegative aggregation weight for the given projection order. */
		public Builder orderWeight(int order, double weight) {
			if (order < 1)
				throw new IllegalArgumentException("Projection order must be positive, have " + order);
			if (!Double.isFinite(weight) || weight < 0d)
				throw new IllegalArgumentException("Order weight must be finite and nonnegative, have " + weight);
			orderWeights.put(order, weight);
			return this;
		}

		public PointSetScoringConfig build() {
			List<PointSetProjection> projectionCopy = projections.isEmpty() ? Collections.emptyList()
					: Collections.unmodifiableList(new ArrayList<>(projections));
			return new PointSetScoringConfig(maxOrder, projectionCopy,
					Collections.unmodifiableMap(new HashMap<>(orderWeights)));
		}
	}

	private static void enumerateProjections(int dimensions, int[] indexes, int position, int minimum,
			List<PointSetProjection> projections) {
		if (position == indexes.length) {
			projections.add(new PointSetProjection(indexes));
			return;
		}
		int remaining = indexes.length-position-1;
		for (int dimension=minimum; dimension<dimensions-remaining; dimension++) {
			indexes[position] = dimension;
			enumerateProjections(dimensions, indexes, position+1, dimension+1, projections);
		}
	}

	private static void enumerateActiveProjections(int[] active, int[] indexes, int position, int minimum,
			List<PointSetProjection> projections) {
		if (position == indexes.length) {
			projections.add(new PointSetProjection(indexes));
			return;
		}
		int remaining = indexes.length-position-1;
		for (int activeIndex=minimum; activeIndex<active.length-remaining; activeIndex++) {
			indexes[position] = active[activeIndex];
			enumerateActiveProjections(active, indexes, position+1, activeIndex+1, projections);
		}
	}
}
