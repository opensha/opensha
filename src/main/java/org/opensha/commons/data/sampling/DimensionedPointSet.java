package org.opensha.commons.data.sampling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A no-copy {@link PointSet} decorator that attaches dimension definitions to another point set.
 */
public final class DimensionedPointSet implements PointSet {

	private final PointSet delegate;
	private final List<SamplingDimension> dimensions;

	public DimensionedPointSet(PointSet delegate, List<? extends SamplingDimension> dimensions) {
		if (delegate == null)
			throw new NullPointerException("Delegate point set cannot be null");
		if (dimensions == null)
			throw new NullPointerException("Dimensions cannot be null");
		if (dimensions.size() != delegate.dimensions())
			throw new IllegalArgumentException("Supplied " + dimensions.size() + " dimension definitions for a point set with "
					+ delegate.dimensions() + " dimensions");
		List<SamplingDimension> copy = new ArrayList<>(dimensions.size());
		for (int d=0; d<dimensions.size(); d++) {
			SamplingDimension dimension = dimensions.get(d);
			if (dimension == null)
				throw new NullPointerException("Dimension " + d + " cannot be null");
			copy.add(dimension);
		}
		this.delegate = delegate;
		this.dimensions = Collections.unmodifiableList(copy);
	}

	@Override
	public int size() {
		return delegate.size();
	}

	@Override
	public int dimensions() {
		return delegate.dimensions();
	}

	@Override
	public double get(int pointIndex, int dimensionIndex) {
		return delegate.get(pointIndex, dimensionIndex);
	}

	@Override
	public SamplingDimension getDimension(int dimensionIndex) {
		return dimensions.get(dimensionIndex);
	}

	/** @return decorated point set supplying the coordinates */
	public PointSet getDelegate() {
		return delegate;
	}

	/** @return immutable dimension-definition list */
	public List<SamplingDimension> getDimensions() {
		return dimensions;
	}
}
