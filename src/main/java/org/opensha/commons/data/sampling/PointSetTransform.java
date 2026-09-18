package org.opensha.commons.data.sampling;

/**
 * A transformation from one finite point set to another. Set-level transformation supports both pointwise transforms
 * and future rank-based transforms that need to inspect an entire sample.
 */
@FunctionalInterface
public interface PointSetTransform {

	PointSet apply(PointSet pointSet);
}
