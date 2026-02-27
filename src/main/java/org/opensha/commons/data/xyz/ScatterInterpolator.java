package org.opensha.commons.data.xyz;

import java.awt.geom.Point2D;

import org.opensha.commons.data.function.XY_DataSet;

/**
 * Scatter-to-grid interpolation utilities.
 *
 * Supports:
 *  1) XY_DataSet interpreted as presence/weight samples with implicit z=1 at each (x,y)
 *     (produces a gridded "density"/occupancy field)
 *  2) XYZ_DataSet with explicit z values at each (x,y)
 *
 * Interpolator: Inverse Distance Weighting (IDW), dependency-free.
 *
 * Mask: Any xyz grid node already NaN is preserved (skipped). Any cell that cannot be interpolated
 * due to no neighbors within maxDist will be set to NaN.
 */
public class ScatterInterpolator {

	private ScatterInterpolator() {}
	
	/**
	 * Interpolate XY density samples onto an existing grid.
	 * 
	 * Each sample contributes an implicit z=1 at its (x,y) location.
	 *
	 * @param scatterXY XY samples
	 * @param xyz target grid (non-NaN nodes are overwritten; NaN nodes are preserved as mask)
	 * @param power IDW power parameter p (typical: 1..3; 2 is common)
	 * @param maxDist maximum neighbor distance; use Double.POSITIVE_INFINITY for no limit
	 * @param maxNeighbors maximum neighbors to use; <=0 means use all within maxDist
	 */
	public static void interpolateXYDensity(XY_DataSet scatterXY, EvenlyDiscrXYZ_DataSet xyz,
			double power, double maxDist, int maxNeighbors) {
		if (scatterXY == null)
			throw new NullPointerException("scatterXY");
		if (xyz == null)
			throw new NullPointerException("xyz");
		if (!(power > 0d))
			throw new IllegalArgumentException("power must be > 0; got " + power);
		if (!(maxDist > 0d))
			throw new IllegalArgumentException("maxDist must be > 0; got " + maxDist);

		final int nSamp = scatterXY.size();
		final int nx = xyz.getNumX();
		final int ny = xyz.getNumY();

		final double maxDistSq = Double.isInfinite(maxDist) ? Double.POSITIVE_INFINITY : maxDist * maxDist;
		final boolean limitNeighbors = maxNeighbors > 0;

		double[] bestDistSq = limitNeighbors ? new double[maxNeighbors] : null;
		int[] bestIdx = limitNeighbors ? new int[maxNeighbors] : null;

		for (int ix = 0; ix < nx; ix++) {
			final double gx = xyz.getX(ix);

			for (int iy = 0; iy < ny; iy++) {
				// respect mask: leave NaNs unchanged
				if (Double.isNaN(xyz.get(ix, iy)))
					continue;

				final double gy = xyz.getY(iy);

				double wSum = 0d;

				if (limitNeighbors) {
					for (int k = 0; k < maxNeighbors; k++) {
						bestDistSq[k] = Double.POSITIVE_INFINITY;
						bestIdx[k] = -1;
					}
				}

				for (int i = 0; i < nSamp; i++) {
					double dx = scatterXY.getX(i) - gx;
					double dy = scatterXY.getY(i) - gy;
					double d2 = dx*dx + dy*dy;

					if (d2 == 0d) {
						// A sample lies exactly on this node; treat as very high density.
						// You can pick a convention; here we just set NaN-avoiding large value:
						wSum = Double.POSITIVE_INFINITY;
						break;
					}

					if (d2 > maxDistSq)
						continue;

					if (limitNeighbors) {
						int worstSlot = 0;
						double worst = bestDistSq[0];
						for (int k = 1; k < maxNeighbors; k++) {
							if (bestIdx[k] < 0) {
								worstSlot = k;
								worst = Double.POSITIVE_INFINITY;
								break;
							}
							if (bestDistSq[k] > worst) {
								worstSlot = k;
								worst = bestDistSq[k];
							}
						}
						if (d2 < worst) {
							bestDistSq[worstSlot] = d2;
							bestIdx[worstSlot] = i;
						}
					} else {
						wSum += weightFromDistSq(d2, power);
					}
				}

				if (Double.isInfinite(wSum)) {
					xyz.set(ix, iy, wSum);
					continue;
				}

				if (limitNeighbors) {
					for (int k = 0; k < maxNeighbors; k++) {
						int i = bestIdx[k];
						if (i < 0)
							continue;
						wSum += weightFromDistSq(bestDistSq[k], power);
					}
				}

				// If no neighbors, mark as NaN (or keep existing, your call)
				xyz.set(ix, iy, wSum == 0d ? Double.NaN : wSum);
			}
		}
	}

	/**
	 * Interpolate scattered XYZ samples onto an existing grid.
	 *
	 * @param scatterXYZ scattered samples
	 * @param xyz target grid (non-NaN nodes are overwritten; NaN nodes are preserved as mask)
	 * @param power IDW power parameter p (typical: 1..3; 2 is common)
	 * @param maxDist maximum neighbor distance; use Double.POSITIVE_INFINITY for no limit
	 * @param maxNeighbors maximum neighbors to use; <=0 means use all within maxDist
	 */
	public static void interpolate(XYZ_DataSet scatterXYZ, EvenlyDiscrXYZ_DataSet xyz,
			double power, double maxDist, int maxNeighbors) {
		if (scatterXYZ == null)
			throw new NullPointerException("scatterXYZ");
		if (xyz == null)
			throw new NullPointerException("xyz");

		int n = scatterXYZ.size();
		double[] x = new double[n];
		double[] y = new double[n];
		double[] z = new double[n];

		for (int i = 0; i < n; i++) {
			Point2D pt = scatterXYZ.getPoint(i);
			x[i] = pt.getX();
			y[i] = pt.getY();
			z[i] = scatterXYZ.get(i);
		}

		interpolateIDW(x, y, z, xyz, power, maxDist, maxNeighbors);
	}

	/**
	 * Core IDW routine (O(Ngrid * Nsamp)).
	 *
	 * Behavior:
	 * - If xyz(ix,iy) is NaN, it is treated as masked and skipped (left as NaN).
	 * - If any sample z is NaN/Inf, it is ignored.
	 * - If a grid node coincides exactly with a sample point, it is set to that sample's z.
	 * - If no neighbors found within maxDist, node is set to NaN.
	 */
	private static void interpolateIDW(double[] xSamp, double[] ySamp, double[] zSamp,
			EvenlyDiscrXYZ_DataSet xyz,
			double power, double maxDist, int maxNeighbors) {

		if (xSamp == null || ySamp == null || zSamp == null)
			throw new NullPointerException("Sample arrays cannot be null");
		if (xSamp.length != ySamp.length || xSamp.length != zSamp.length)
			throw new IllegalArgumentException("Sample arrays must have equal length");
		if (!(power > 0d))
			throw new IllegalArgumentException("power must be > 0; got " + power);
		if (!(maxDist > 0d))
			throw new IllegalArgumentException("maxDist must be > 0; got " + maxDist);

		final int nSamp = xSamp.length;
		final int nx = xyz.getNumX();
		final int ny = xyz.getNumY();

		final double maxDistSq = Double.isInfinite(maxDist) ? Double.POSITIVE_INFINITY : maxDist * maxDist;
		final boolean limitNeighbors = maxNeighbors > 0;

		// Small working arrays for neighbor limiting
		double[] bestDistSq = limitNeighbors ? new double[maxNeighbors] : null;
		int[] bestIdx = limitNeighbors ? new int[maxNeighbors] : null;

		for (int ix = 0; ix < nx; ix++) {
			final double gx = xyz.getX(ix);

			for (int iy = 0; iy < ny; iy++) {

				// respect mask: leave NaNs unchanged
				double existing = xyz.get(ix, iy);
				if (Double.isNaN(existing))
					continue;

				final double gy = xyz.getY(iy);

				int exactIdx = -1;
				double wSum = 0d;
				double zwSum = 0d;

				if (limitNeighbors) {
					for (int k = 0; k < maxNeighbors; k++) {
						bestDistSq[k] = Double.POSITIVE_INFINITY;
						bestIdx[k] = -1;
					}
				}

				for (int i = 0; i < nSamp; i++) {
					double zi = zSamp[i];
					if (Double.isNaN(zi) || Double.isInfinite(zi))
						continue;

					double dx = xSamp[i] - gx;
					double dy = ySamp[i] - gy;
					double d2 = dx*dx + dy*dy;

					if (d2 == 0d) {
						exactIdx = i;
						break;
					}

					if (d2 > maxDistSq)
						continue;

					if (limitNeighbors) {
						// Insert if better than current worst slot
						int worstSlot = 0;
						double worst = bestDistSq[0];
						for (int k = 1; k < maxNeighbors; k++) {
							if (bestIdx[k] < 0) {
								worstSlot = k;
								worst = Double.POSITIVE_INFINITY;
								break;
							}
							if (bestDistSq[k] > worst) {
								worstSlot = k;
								worst = bestDistSq[k];
							}
						}

						if (d2 < worst) {
							bestDistSq[worstSlot] = d2;
							bestIdx[worstSlot] = i;
						}
					} else {
						double w = weightFromDistSq(d2, power);
						wSum += w;
						zwSum += w * zi;
					}
				}

				if (exactIdx >= 0) {
					xyz.set(ix, iy, zSamp[exactIdx]);
					continue;
				}

				if (limitNeighbors) {
					for (int k = 0; k < maxNeighbors; k++) {
						int i = bestIdx[k];
						if (i < 0)
							continue;

						double d2 = bestDistSq[k];
						double zi = zSamp[i];
						double w = weightFromDistSq(d2, power);

						wSum += w;
						zwSum += w * zi;
					}
				}

				if (wSum == 0d)
					xyz.set(ix, iy, Double.NaN);
				else
					xyz.set(ix, iy, zwSum / wSum);
			}
		}
	}

	private static double weightFromDistSq(double d2, double power) {
		// w = 1 / d^p, and d = sqrt(d2) => w = 1 / (d2^(p/2))
		return 1d / Math.pow(d2, 0.5 * power);
	}
}