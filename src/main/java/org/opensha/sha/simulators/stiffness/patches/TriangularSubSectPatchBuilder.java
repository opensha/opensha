package org.opensha.sha.simulators.stiffness.patches;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;

/** Direct triangulation of grid cells, requiring no planar projection or unwrapping. */
public final class TriangularSubSectPatchBuilder implements SubSectPatchBuilder {
	/** Includes the triangle geometry, kernel, and area-weighted aggregation convention. */
	public static final int VERSION = 3;
	private final int zone;
	private final char letter;

	public TriangularSubSectPatchBuilder(int zone, char letter) {
		this.zone=zone;
		this.letter=letter;
	}

	@Override
	public List<PatchLocation> buildPatches(FaultSection section, EvenlyGriddedSurface surface) {
		return buildPatches(surface,section.getAveRake());
	}

	/** Alignment only affects rectangular layouts; triangles always tile the supplied cells. */
	@Override
	public List<PatchLocation> buildPatches(FaultSection section, EvenlyGriddedSurface surface, PatchAlignment alignment) {
		return buildPatches(section,surface);
	}

	public List<PatchLocation> buildPatches(EvenlyGriddedSurface surface, double rake) {
		// Validate the original 3-D cells, without any reference-plane requirements.
		GriddedSurfaceGeometry geometry=new GriddedSurfaceGeometry(surface,zone,letter);
		List<PatchLocation> result=new ArrayList<>();
		for (int r=0; r<geometry.rows-1; r++) for (int c=0; c<geometry.cols-1; c++) {
			Vector3D a=geometry.point(r,c).scalarMultiply(1000);
			Vector3D b=geometry.point(r,c+1).scalarMultiply(1000);
			Vector3D d=geometry.point(r+1,c).scalarMultiply(1000);
			Vector3D e=geometry.point(r+1,c+1).scalarMultiply(1000);
			// Use the same diagonal for every cell. This deterministic choice
			// preserves topology, including collapsed edges at triangular tips.
			Vector3D along=b.subtract(a).add(e.subtract(d));
			double strikeHint=Math.toDegrees(Math.atan2(along.getX(),along.getY()));
			add(result,geometry,a,b,e,rake,strikeHint);
			add(result,geometry,a,e,d,rake,strikeHint);
		}
		return result;
	}

	private static void add(List<PatchLocation> result, GriddedSurfaceGeometry geometry,
			Vector3D a, Vector3D b, Vector3D c, double rake, double strikeHint) {
		// A collapsed boundary can leave one zero-area half of an otherwise valid
		// cell. Skip that half, but retain arbitrarily skinny nonzero triangles.
		if (b.subtract(a).crossProduct(c.subtract(a)).getNorm() == 0) return;
		TriangularPatch patch=new TriangularPatch(new double[][] {a.toArray(),b.toArray(),c.toArray()},rake,strikeHint);
		Location[] corners=new Location[3];
		for (int i=0; i<3; i++) corners[i]=geometry.location(new Vector3D(patch.corner[i]).scalarMultiply(.001));
		Location center=geometry.location(new Vector3D(patch.center).scalarMultiply(.001));
		result.add(new PatchLocation(patch,center,corners));
	}
}
