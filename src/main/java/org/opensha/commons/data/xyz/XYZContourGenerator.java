package org.opensha.commons.data.xyz;

import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;

/**
 * Lightweight contour (isoline) extraction for gridded data using marching squares.
 *
 * Produces polylines (as XY_DataSet instances) for each requested contour level.
 *
 * Assumptions/behavior:
 * - Operates on an evenly-spaced grid (EvenlyDiscrXYZ_DataSet).
 * - Cells with any NaN/Inf corner are treated as masked out (no segments produced).
 * - Ambiguous marching-squares cases (5 and 10) are resolved using a simple center-value test
 *   (average of corner values).
 * - Optional closedOnly filter returns only closed loops.
 */
public class XYZContourGenerator {

	private XYZContourGenerator() {}

	/**
	 * Extract contour polylines for the supplied contour levels.
	 *
	 * @param grid gridded XYZ values
	 * @param levels contour values to extract
	 * @param closedOnly if true, only closed loops are returned
	 * @return map: level -> list of polylines (each polyline is an XY_DataSet)
	 */
	public static Map<Double, List<XY_DataSet>> contours(EvenlyDiscrXYZ_DataSet grid, double[] levels, boolean closedOnly) {
		Objects.requireNonNull(grid, "grid");
		Objects.requireNonNull(levels, "levels");

		final int nx = grid.getNumX();
		final int ny = grid.getNumY();
		if (nx < 2 || ny < 2)
			throw new IllegalArgumentException("Grid must have at least 2x2 points; have nx=" + nx + ", ny=" + ny);

		final double dx = Math.abs(grid.getGridSpacingX());
		final double dy = Math.abs(grid.getGridSpacingY());

		// Quantization scale for endpoint matching during segment stitching
		final double minSpacing = Math.min(dx, dy);
		final double quant = minSpacing > 0d ? minSpacing * 1e-6 : 0d;

		Map<Double, List<XY_DataSet>> ret = new HashMap<>();

		for (double level : levels) {
			SegmentStitcher stitcher = new SegmentStitcher(quant);

			for (int ix = 0; ix < nx - 1; ix++) {
				final double x0 = grid.getX(ix);
				final double x1 = grid.getX(ix + 1);

				for (int iy = 0; iy < ny - 1; iy++) {
					final double y0 = grid.getY(iy);
					final double y1 = grid.getY(iy + 1);

					// Corner ordering:
					// v00 = (ix, iy)         v10 = (ix+1, iy)
					// v01 = (ix, iy+1)       v11 = (ix+1, iy+1)
					double v00 = grid.get(ix, iy);
					double v10 = grid.get(ix + 1, iy);
					double v01 = grid.get(ix, iy + 1);
					double v11 = grid.get(ix + 1, iy + 1);

					if (!isFinite(v00) || !isFinite(v10) || !isFinite(v01) || !isFinite(v11))
						continue; // masked-out cell

					int c00 = v00 > level ? 1 : 0;
					int c10 = v10 > level ? 1 : 0;
					int c11 = v11 > level ? 1 : 0;
					int c01 = v01 > level ? 1 : 0;

					// case index (bit layout):
					// bit0 = v00, bit1 = v10, bit2 = v11, bit3 = v01
					int idx = (c00) | (c10 << 1) | (c11 << 2) | (c01 << 3);
					if (idx == 0 || idx == 15)
						continue;

					// Resolve ambiguous cases (5 and 10) by center-value test
					if (idx == 5 || idx == 10) {
						double center = 0.25 * (v00 + v10 + v01 + v11);
						boolean centerHigh = center > level;
						emitAmbiguous(idx, centerHigh, level,
								x0, x1, y0, y1,
								v00, v10, v11, v01,
								stitcher);
						continue;
					}

					emitNonAmbiguous(idx, level,
							x0, x1, y0, y1,
							v00, v10, v11, v01,
							stitcher);
				}
			}

			ret.put(level, stitcher.toXYDataSets(closedOnly));
		}

		return ret;
	}

	/* ---------------------------
	 * Marching squares emitters
	 * --------------------------- */

	/**
	 * Emit segments for non-ambiguous marching-squares cases (not 5 or 10).
	 */
	private static void emitNonAmbiguous(int idx, double level,
			double x0, double x1, double y0, double y1,
			double v00, double v10, double v11, double v01,
			SegmentStitcher out) {

		// Edge ids:
		// e0: bottom (v00-v10) at y0
		// e1: right  (v10-v11) at x1
		// e2: top    (v01-v11) at y1
		// e3: left   (v00-v01) at x0

		switch (idx) {
			case 1:  // 0001
			case 14: // 1110
				out.addSegment(edgePoint(3, level, x0, x1, y0, y1, v00, v10, v11, v01),
						edgePoint(0, level, x0, x1, y0, y1, v00, v10, v11, v01));
				break;

			case 2:  // 0010
			case 13: // 1101
				out.addSegment(edgePoint(0, level, x0, x1, y0, y1, v00, v10, v11, v01),
						edgePoint(1, level, x0, x1, y0, y1, v00, v10, v11, v01));
				break;

			case 3:  // 0011
			case 12: // 1100
				out.addSegment(edgePoint(3, level, x0, x1, y0, y1, v00, v10, v11, v01),
						edgePoint(1, level, x0, x1, y0, y1, v00, v10, v11, v01));
				break;

			case 4:  // 0100
			case 11: // 1011
				out.addSegment(edgePoint(1, level, x0, x1, y0, y1, v00, v10, v11, v01),
						edgePoint(2, level, x0, x1, y0, y1, v00, v10, v11, v01));
				break;

			case 6:  // 0110
			case 9:  // 1001
				out.addSegment(edgePoint(0, level, x0, x1, y0, y1, v00, v10, v11, v01),
						edgePoint(2, level, x0, x1, y0, y1, v00, v10, v11, v01));
				break;

			case 7:  // 0111
			case 8:  // 1000
				out.addSegment(edgePoint(3, level, x0, x1, y0, y1, v00, v10, v11, v01),
						edgePoint(2, level, x0, x1, y0, y1, v00, v10, v11, v01));
				break;

			default:
				// Should never happen for non-ambiguous idx != 0,15,5,10
				break;
		}
	}

	/**
	 * Emit segments for ambiguous cases (5 and 10), choosing connectivity via center test.
	 */
	private static void emitAmbiguous(int idx, boolean centerHigh, double level,
			double x0, double x1, double y0, double y1,
			double v00, double v10, double v11, double v01,
			SegmentStitcher out) {

		Point2D pE0 = edgePoint(0, level, x0, x1, y0, y1, v00, v10, v11, v01);
		Point2D pE1 = edgePoint(1, level, x0, x1, y0, y1, v00, v10, v11, v01);
		Point2D pE2 = edgePoint(2, level, x0, x1, y0, y1, v00, v10, v11, v01);
		Point2D pE3 = edgePoint(3, level, x0, x1, y0, y1, v00, v10, v11, v01);

		// Pairing A: (e3-e0) and (e1-e2)
		// Pairing B: (e0-e1) and (e2-e3)
		//
		// centerHigh chooses connectivity that tends to connect "high" regions; centerLow connects "low".
		boolean pairingA = centerHigh;

		if (pairingA) {
			out.addSegment(pE3, pE0);
			out.addSegment(pE1, pE2);
		} else {
			out.addSegment(pE0, pE1);
			out.addSegment(pE2, pE3);
		}
	}

	/**
	 * Returns the intersection point on a cell edge.
	 *
	 * Edge ids:
	 * 0: bottom (v00-v10) at y0
	 * 1: right  (v10-v11) at x1
	 * 2: top    (v01-v11) at y1
	 * 3: left   (v00-v01) at x0
	 */
	private static Point2D edgePoint(int edgeId, double level,
			double x0, double x1, double y0, double y1,
			double v00, double v10, double v11, double v01) {

		switch (edgeId) {
			case 0: {
				double t = interpT(v00, v10, level);
				return new Point2D.Double(lerp(x0, x1, t), y0);
			}
			case 1: {
				double t = interpT(v10, v11, level);
				return new Point2D.Double(x1, lerp(y0, y1, t));
			}
			case 2: {
				double t = interpT(v01, v11, level);
				return new Point2D.Double(lerp(x0, x1, t), y1);
			}
			case 3: {
				double t = interpT(v00, v01, level);
				return new Point2D.Double(x0, lerp(y0, y1, t));
			}
			default:
				throw new IllegalArgumentException("Bad edgeId=" + edgeId);
		}
	}

	private static double interpT(double vA, double vB, double level) {
		double denom = (vB - vA);
		if (denom == 0d)
			return 0.5; // flat edge right at level
		return (level - vA) / denom;
	}

	private static double lerp(double a, double b, double t) {
		return a + t * (b - a);
	}

	private static boolean isFinite(double v) {
		return !Double.isNaN(v) && !Double.isInfinite(v);
	}

	/* ---------------------------
	 * Segment stitching
	 * --------------------------- */

	private static class SegmentStitcher {
		private final double quant;

		// Map endpoint -> polyline that has that endpoint
		private final Map<Key, Polyline> endpointMap = new HashMap<>();
		private final List<Polyline> all = new ArrayList<>();

		SegmentStitcher(double quant) {
			this.quant = quant;
		}

		void addSegment(Point2D a, Point2D b) {
			if (a == null || b == null)
				return;

			Key ka = key(a);
			Key kb = key(b);
			if (ka.equals(kb))
				return;

			Polyline pa = endpointMap.get(ka);
			Polyline pb = endpointMap.get(kb);

			if (pa == null && pb == null) {
				Polyline p = new Polyline();
				p.points.addLast(asDouble(a));
				p.points.addLast(asDouble(b));
				all.add(p);
				putEndpoints(p);
				return;
			}

			if (pa != null && pb == null) {
				extend(pa, a, b);
				return;
			}

			if (pa == null && pb != null) {
				extend(pb, b, a);
				return;
			}

			// both exist
			if (pa == pb) {
				// already part of the same polyline; ignore
				return;
			}

			merge(pa, pb, a, b);
		}

		List<XY_DataSet> toXYDataSets(boolean closedOnly) {
			List<XY_DataSet> ret = new ArrayList<>();
			for (Polyline p : all) {
				if (p.deleted)
					continue;

				if (p.points.size() < 2)
					continue;

				boolean closed = isClosed(p);
				if (closedOnly && !closed)
					continue;

				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				for (Point2D.Double pt : p.points)
					xy.set(pt.x, pt.y);

				// If closed, optionally repeat first point at end
				if (closed) {
					Point2D.Double first = p.points.getFirst();
					Point2D.Double last = p.points.getLast();
					if (!key(first).equals(key(last)))
						xy.set(first.x, first.y);
				}

				ret.add(xy);
			}
			return ret;
		}

		private void extend(Polyline p, Point2D knownEnd, Point2D newEnd) {
			removeEndpoints(p);

			Key kFirst = key(p.points.getFirst());
			Key kLast = key(p.points.getLast());
			Key kKnown = key(knownEnd);

			Point2D.Double add = asDouble(newEnd);

			if (kKnown.equals(kFirst)) {
				p.points.addFirst(add);
			} else if (kKnown.equals(kLast)) {
				p.points.addLast(add);
			} else {
				putEndpoints(p);
				return;
			}

			putEndpoints(p);
		}

		private void merge(Polyline a, Polyline b, Point2D segA, Point2D segB) {
			removeEndpoints(a);
			removeEndpoints(b);

			Key aFirst = key(a.points.getFirst());
			Key aLast = key(a.points.getLast());
			Key bFirst = key(b.points.getFirst());
			Key bLast = key(b.points.getLast());

			Key kA = key(segA);
			Key kB = key(segB);

			boolean aHas = kA.equals(aFirst) || kA.equals(aLast);
			boolean bHas = kB.equals(bFirst) || kB.equals(bLast);
			if (!aHas || !bHas) {
				// Try swapped
				aHas = kB.equals(aFirst) || kB.equals(aLast);
				bHas = kA.equals(bFirst) || kA.equals(bLast);
				if (aHas && bHas) {
					Key tmp = kA;
					kA = kB;
					kB = tmp;
				} else {
					putEndpoints(a);
					putEndpoints(b);
					return;
				}
			}

			// orient a so join end is last
			if (kA.equals(aFirst))
				a.reverse();

			// orient b so join end is first
			if (kB.equals(bLast))
				b.reverse();

			for (Point2D.Double pt : b.points)
				a.points.addLast(pt);

			b.deleted = true;

			putEndpoints(a);
		}

		private boolean isClosed(Polyline p) {
			return key(p.points.getFirst()).equals(key(p.points.getLast()));
		}

		private void putEndpoints(Polyline p) {
			endpointMap.put(key(p.points.getFirst()), p);
			endpointMap.put(key(p.points.getLast()), p);
		}

		private void removeEndpoints(Polyline p) {
			endpointMap.remove(key(p.points.getFirst()));
			endpointMap.remove(key(p.points.getLast()));
		}

		private Key key(Point2D pt) {
			return Key.of(pt.getX(), pt.getY(), quant);
		}

		private Point2D.Double asDouble(Point2D pt) {
			if (pt instanceof Point2D.Double)
				return (Point2D.Double)pt;
			return new Point2D.Double(pt.getX(), pt.getY());
		}
	}

	private static class Polyline {
		final Deque<Point2D.Double> points = new ArrayDeque<>();
		boolean deleted = false;

		void reverse() {
			Deque<Point2D.Double> rev = new ArrayDeque<>(points.size());
			while (!points.isEmpty())
				rev.addLast(points.removeLast());
			points.addAll(rev);
		}
	}

	private static class Key {
		final long xq;
		final long yq;

		private Key(long xq, long yq) {
			this.xq = xq;
			this.yq = yq;
		}

		static Key of(double x, double y, double quant) {
			long xq = quantize(x, quant);
			long yq = quantize(y, quant);
			return new Key(xq, yq);
		}

		private static long quantize(double v, double q) {
			if (q <= 0d)
				return Double.doubleToLongBits(v);
			return Math.round(v / q);
		}

		@Override
		public int hashCode() {
			return (int)(xq * 31L + yq);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Key)) return false;
			Key other = (Key)o;
			return xq == other.xq && yq == other.yq;
		}
	}
}