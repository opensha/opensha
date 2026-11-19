package org.opensha.sha.simulators.stiffness.patches;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.utm.UTM;
import org.opensha.commons.geo.utm.WGS84;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;

/** Cartesian grid snapshot and cell validation. Coordinates are in km, with Z pointing up. */
final class GriddedSurfaceGeometry {
	final int rows, cols;
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
		for (int c = 0; c < cols; c++) {
			boolean collapsed = false;
			boolean noncollapsed = false;
			for (int r = 0; r < rows; r++) {
				Location loc = Preconditions.checkNotNull(surface.getLocation(r, c), "Missing node (%s,%s)", r, c);
				UTM utm = new UTM(new WGS84(loc.getLatitude(), loc.getLongitude()), zone, letter);
				points[r][c] = new Vector3D(utm.getEasting() / 1000d, utm.getNorthing() / 1000d, -loc.getDepth());
				if (r > 0) {
					double distance = points[r][c].distance(points[r - 1][c]);
					Preconditions.checkArgument(distance > 1e-9 || c == 0 || c == cols - 1,
							"Collapsed interior down-dip edge at (%s,%s)", r, c);
					collapsed |= distance <= 1e-9;
					noncollapsed |= distance > 1e-9;
				}
			}
			// A completely collapsed outer column is a valid triangular tip.
			Preconditions.checkArgument(!(collapsed && noncollapsed), "Partly collapsed column %s", c);
		}
		for (int r = 0; r < rows - 1; r++) {
			for (int c = 0; c < cols - 1; c++) {
				Preconditions.checkArgument(cellArea(r, c) > 1e-12, "Collapsed cell (%s,%s)", r, c);
				Vector3D reference = null;
				// Check the original cell's tangents before choosing upward-facing
				// triangle normals. Reversed winding is valid; a folded cell is not.
				for (int v = 0; v <= 1; v++) {
					for (int u = 0; u <= 1; u++) {
						Vector3D along = points[r + v][c + 1].subtract(points[r + v][c]);
						Vector3D down = points[r + 1][c + u].subtract(points[r][c + u]);
						Vector3D normal = down.crossProduct(along);
						if (normal.getNorm() < 1e-12)
							continue;
						normal = normal.normalize();
						if (reference != null)
							Preconditions.checkArgument(reference.dotProduct(normal) > 0, "Folded cell (%s,%s)", r, c);
						else
							reference = normal;
					}
				}
			}
		}
	}

	Vector3D point(int row, int col) {
		return points[row][col];
	}

	Location location(Vector3D point) {
		WGS84 wgs = new WGS84(new UTM(zone, letter, point.getX() * 1000d, point.getY() * 1000d));
		return new Location(wgs.getLatitude(), wgs.getLongitude(), -point.getZ());
	}

	double cellArea(int row, int col) {
		Vector3D a = points[row][col], b = points[row][col + 1];
		Vector3D c = points[row + 1][col + 1], d = points[row + 1][col];
		return 0.5 * (b.subtract(a).crossProduct(c.subtract(a)).getNorm()
				+ c.subtract(a).crossProduct(d.subtract(a)).getNorm());
	}
}
