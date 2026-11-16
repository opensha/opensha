package org.opensha.sha.simulators.stiffness;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.utm.UTM;
import org.opensha.commons.geo.utm.WGS84;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import com.google.common.base.Preconditions;

/**
 * Snapshot of an interface-only surface in the calculator's shared Cartesian frame.
 * Coordinates and cumulative distances are in km; Z points up. Nominal grid
 * spacings are deliberately unused. Bilinear interpolation includes all four nodes.
 */
final class GriddedSurfaceGeometry {
	private static final double EDGE_NORMAL_TOLERANCE_KM = 1e-5; // 1 cm UTM rounding
	final int rows, cols;
	final double minDepth, maxDepth;
	final double[][] distances;
	private final Vector3D[][] points;
	private final int zone;
	private final char letter;

	GriddedSurfaceGeometry(EvenlyGriddedSurface surface, int zone, char letter) {
		rows = surface.getNumRows();
		cols = surface.getNumCols();
		Preconditions.checkArgument(rows > 1 && cols > 1, "Surface needs at least two rows and columns");
		this.zone = zone;
		this.letter = letter;
		points = new Vector3D[rows][cols];
		distances = new double[rows][cols];
		double shallowest=Double.POSITIVE_INFINITY, deepest=Double.NEGATIVE_INFINITY;
		for (int c=0; c<cols; c++) {
			for (int r=0; r<rows; r++) {
				Location loc = Preconditions.checkNotNull(surface.getLocation(r, c), "Missing node (%s,%s)", r, c);
				shallowest=Math.min(shallowest,loc.getDepth());
				deepest=Math.max(deepest,loc.getDepth());
				UTM utm = new UTM(new WGS84(loc.getLatitude(), loc.getLongitude()), zone, letter);
				points[r][c] = new Vector3D(utm.getEasting()/1000d, utm.getNorthing()/1000d, -loc.getDepth());
				if (r > 0) {
					double distance = points[r][c].distance(points[r-1][c]);
					Preconditions.checkArgument(distance > 1e-9 || c == 0 || c == cols-1,
							"Collapsed interior down-dip edge at (%s,%s)", r, c);
					distances[r][c] = distances[r-1][c] + distance;
				}
			}
			if (distances[rows-1][c] > 1e-9) {
				for (int r=1; r<rows; r++)
					Preconditions.checkArgument(distances[r][c]-distances[r-1][c] > 1e-9,
							"Partly collapsed column at (%s,%s)", r, c);
			}
		}
		minDepth=shallowest; maxDepth=deepest;
		for (int r=0; r<rows-1; r++) {
			for (int c=0; c<cols-1; c++) {
				Preconditions.checkArgument(cellArea(r, c) > 1e-12, "Collapsed cell (%s,%s)", r, c);
				Vector3D reference = null;
				// Bilinear tangent normals vary linearly over the cell. Check all
				// corners, tolerating the zero tangent at a collapsed boundary tip.
				for (int v=0; v<=1; v++) {
					for (int u=0; u<=1; u++) {
						Vector3D along = points[r+v][c+1].subtract(points[r+v][c]);
						Vector3D down = points[r+1][c+u].subtract(points[r][c+u]);
						Vector3D normal = down.crossProduct(along);
						if (normal.getNorm() < 1e-12) continue;
						normal = normal.normalize();
						Preconditions.checkArgument(normal.getZ() >= -1e-5,
								"Overturned or reversed cell (%s,%s)", r, c);
						if (reference != null)
							Preconditions.checkArgument(reference.dotProduct(normal) > 0,
									"Folded cell (%s,%s)", r, c);
						else reference = normal;
					}
				}
			}
		}
	}

	Vector3D point(double row, double col) {
		int r = before(row, rows), c = before(col, cols);
		double v = row-r, u = col-c;
		return new Vector3D((1-v)*(1-u), points[r][c], (1-v)*u, points[r][c+1],
				v*(1-u), points[r+1][c], v*u, points[r+1][c+1]);
	}

	SurfacePoint interpolate(double row, double col) {
		int r = before(row, rows), c = before(col, cols);
		double v = row-r, u = col-c;
		Vector3D along = new Vector3D(1-v, points[r][c+1].subtract(points[r][c]),
				v, points[r+1][c+1].subtract(points[r+1][c]));
		Vector3D normal = sharedEdgeNormal(row, col, cellNormal(r, c, u, v));
		Preconditions.checkState(normal.getNorm() > 1e-12, "Undefined tangent at (%s,%s)", row, col);
		normal = normal.normalize();
		Preconditions.checkState(normal.getZ() >= -1e-5,
				"Overturned or inconsistently ordered cell at (%s,%s)", row, col);
		// atan2 retains small dips that acos loses when Z rounds near one.
		double dip = Math.toDegrees(Math.atan2(Math.hypot(normal.getX(),normal.getY()), Math.max(0d,normal.getZ())));
		// Horizontal planes have no unique strike; retain grid ordering there.
		double strike = normal.getX()*normal.getX()+normal.getY()*normal.getY() < 1e-16
				? Math.toDegrees(Math.atan2(along.getX(), along.getY()))
				: Math.toDegrees(Math.atan2(-normal.getY(), normal.getX()));
		return new SurfacePoint(point(row, col), (strike+360d)%360d, dip);
	}

	/** Raw bilinear tangents: u is fractional column, v is fractional row. */
	private Vector3D cellNormal(int r, int c, double u, double v) {
		Vector3D along = new Vector3D(1-v, points[r][c+1].subtract(points[r][c]),
				v, points[r+1][c+1].subtract(points[r+1][c]));
		Vector3D down = new Vector3D(1-u, points[r+1][c].subtract(points[r][c]),
				u, points[r+1][c+1].subtract(points[r][c+1]));
		return down.crossProduct(along);
	}

	/** Equal unit-normal weights avoid making the edge mechanism depend on cell area. */
	private Vector3D sharedEdgeNormal(double row, double col, Vector3D normal) {
		int r = before(row, rows), c = before(col, cols);
		double v = row-r, u = col-c;
		// A shared edge has two one-sided tangents. Average unit normals only
		// within the centimeter precision of the Cartesian conversion; do not
		// smooth genuine curvature over the patch size.
		Vector3D sum=Vector3D.ZERO;
		int nearRow=(int)Math.rint(row), nearCol=(int)Math.rint(col);
		boolean rowEdge=nearRow > 0 && nearRow < rows-1
				&& point(row,col).distance(point(nearRow,col)) <= EDGE_NORMAL_TOLERANCE_KM;
		boolean colEdge=nearCol > 0 && nearCol < cols-1
				&& point(row,col).distance(point(row,nearCol)) <= EDGE_NORMAL_TOLERANCE_KM;
		if (rowEdge || colEdge) {
			int firstRow=rowEdge ? nearRow-1 : r, lastRow=rowEdge ? nearRow : r;
			int firstCol=colEdge ? nearCol-1 : c, lastCol=colEdge ? nearCol : c;
			for (int rr=firstRow; rr<=lastRow; rr++) for (int cc=firstCol; cc<=lastCol; cc++) {
				double vv=rowEdge ? nearRow-rr : v, uu=colEdge ? nearCol-cc : u;
				Vector3D n=cellNormal(rr, cc, uu, vv);
				if (n.getNorm() > 1e-12) sum=sum.add(n.normalize());
			}
			if (sum.getNorm() > 1e-12) normal=sum;
		}
		return normal;
	}

	double rowAtDistance(int col, double distance) {
		double length = distances[rows-1][col];
		if (length < 1e-9) return (rows-1)*0.5;
		double d = Math.max(0, Math.min(length, distance));
		int low=0, high=rows-1;
		while (high-low > 1) {
			int mid = (low+high)/2;
			if (distances[mid][col] <= d) low=mid; else high=mid;
		}
		return low+(d-distances[low][col])/(distances[low+1][col]-distances[low][col]);
	}

	Location location(Vector3D point) {
		WGS84 wgs = new WGS84(new UTM(zone, letter, point.getX()*1000d, point.getY()*1000d));
		return new Location(wgs.getLatitude(), wgs.getLongitude(), -point.getZ());
	}

	double cellArea(int row, int col) {
		Vector3D a=points[row][col], b=points[row][col+1];
		Vector3D c=points[row+1][col+1], d=points[row+1][col];
		return 0.5*(b.subtract(a).crossProduct(c.subtract(a)).getNorm()
				+c.subtract(a).crossProduct(d.subtract(a)).getNorm());
	}

	private static int before(double index, int size) {
		Preconditions.checkArgument(Double.isFinite(index) && index >= 0 && index <= size-1,
				"Index %s outside [0,%s]", (Object)index, (Object)(size-1));
		return Math.min(size-2, (int)index);
	}

	/** Cartesian center in km (Z up); strike and dip in degrees. */
	static final class SurfacePoint {
		final Vector3D center;
		final double strike, dip;
		SurfacePoint(Vector3D center, double strike, double dip) {
			this.center=center;
			this.strike=strike;
			this.dip=dip;
		}
	}
}
