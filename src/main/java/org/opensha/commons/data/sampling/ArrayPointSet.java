package org.opensha.commons.data.sampling;

/**
 * Immutable, array-backed {@link PointSet}. Input coordinates are defensively copied.
 */
public final class ArrayPointSet implements PointSet {

	private final double[][] points;
	private final int dimensions;

	/**
	 * @param set external PointSet of any type
	 */
	public ArrayPointSet(PointSet set) {
		this(points(set));
	}
	
	private static double[][] points(PointSet set) {
		final int size = set.size();
		final int dimensions = set.dimensions();
		double[][] points = new double[size][dimensions];
		for (int p=0; p<size; p++)
			for (int d=0; d<dimensions; d++)
				points[p][d] = set.get(p, d);
		return points;
	}

	/**
	 * @param points coordinates arranged as {@code [point][dimension]}
	 */
	public ArrayPointSet(double[][] points) {
		if (points == null)
			throw new NullPointerException("Points cannot be null");
		if (points.length == 0)
			throw new IllegalArgumentException("Point set must contain at least one point");
		if (points[0] == null)
			throw new NullPointerException("Point 0 cannot be null");
		if (points[0].length == 0)
			throw new IllegalArgumentException("Points must contain at least one dimension");
		this.dimensions = points[0].length;
		this.points = new double[points.length][dimensions];
		for (int p=0; p<points.length; p++) {
			if (points[p] == null)
				throw new NullPointerException("Point " + p + " cannot be null");
			if (points[p].length != dimensions)
				throw new IllegalArgumentException("Point " + p + " has " + points[p].length
						+ " dimensions, expected " + dimensions);
			for (int d=0; d<dimensions; d++) {
				double value = points[p][d];
				if (!Double.isFinite(value) || value < 0d || value >= 1d)
					throw new IllegalArgumentException("Coordinate [" + p + "][" + d
							+ "] must be finite and in [0,1), have " + value);
				this.points[p][d] = value;
			}
		}
	}

	@Override
	public int size() {
		return points.length;
	}

	@Override
	public int dimensions() {
		return dimensions;
	}

	@Override
	public double get(int pointIndex, int dimensionIndex) {
		return points[pointIndex][dimensionIndex];
	}
}
