package org.opensha.sha.simulators.stiffness.patches;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;

/** Direct triangulation of grid cells, requiring no planar projection or unwrapping. */
public final class TriangularSubSectPatchBuilder implements SubSectPatchBuilder {
	/** Includes the triangle geometry, kernel, and area-weighted aggregation convention. */
	public static final int VERSION = 3;
	private final int zone;
	private final char letter;

	public TriangularSubSectPatchBuilder(int zone, char letter) {
		this.zone = zone;
		this.letter = letter;
	}

	@Override
	public List<PatchLocation> buildPatches(FaultSection section, EvenlyGriddedSurface surface) {
		try {
			return buildPatches(surface, section.getAveRake());
		} catch (IllegalArgumentException | IllegalStateException e) {
			throw new IllegalStateException("Cannot build TRIANGULAR patches for section "
					+ section.getSectionId() + " (" + section.getSectionName() + "): " + e.getMessage(), e);
		}
	}

	public List<PatchLocation> buildPatches(EvenlyGriddedSurface surface, double rake) {
		GriddedSurfaceGeometry geometry = new GriddedSurfaceGeometry(surface, zone, letter);
		List<PatchLocation> result = new ArrayList<>();
		for (int r = 0; r < geometry.rows - 1; r++) {
			for (int c = 0; c < geometry.cols - 1; c++) {
				Vector3D a = geometry.point(r, c).scalarMultiply(1000);
				Vector3D b = geometry.point(r, c + 1).scalarMultiply(1000);
				Vector3D d = geometry.point(r + 1, c).scalarMultiply(1000);
				Vector3D e = geometry.point(r + 1, c + 1).scalarMultiply(1000);
				// The same diagonal preserves topology, including collapsed boundary tips.
				Vector3D along = b.subtract(a).add(e.subtract(d));
				double strikeHint = Math.toDegrees(Math.atan2(along.getX(), along.getY()));
				add(result, geometry, a, b, e, rake, strikeHint);
				add(result, geometry, a, e, d, rake, strikeHint);
			}
		}
		return result;
	}

	private static void add(List<PatchLocation> result, GriddedSurfaceGeometry geometry,
			Vector3D a, Vector3D b, Vector3D c, double rake, double strikeHint) {
		// A collapsed boundary can leave one zero-area half of a valid cell.
		// Skip that half, but retain arbitrarily skinny nonzero triangles.
		if (b.subtract(a).crossProduct(c.subtract(a)).getNorm() == 0)
			return;
		TriangularPatch patch = new TriangularPatch(new double[][] { a.toArray(), b.toArray(), c.toArray() },
				rake, strikeHint);
		Location[] corners = new Location[3];
		for (int i = 0; i < 3; i++)
			corners[i] = geometry.location(new Vector3D(patch.corner[i]).scalarMultiply(.001));
		Location center = geometry.location(new Vector3D(patch.center).scalarMultiply(.001));
		result.add(new PatchLocation(patch, center, corners));
	}
}
