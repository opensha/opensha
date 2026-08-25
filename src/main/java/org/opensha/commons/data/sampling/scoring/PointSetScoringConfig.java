package org.opensha.commons.data.sampling.scoring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
}
