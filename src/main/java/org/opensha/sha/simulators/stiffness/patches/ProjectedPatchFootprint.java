package org.opensha.sha.simulators.stiffness.patches;

import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;

/** Physical tangent rectangle mapped to a reference polygon/polyline, in km. */
final class ProjectedPatchFootprint {
	private static final double EDGE_TOLERANCE_KM = 1e-6; // 1 mm contact tolerance
	private final double[][] corners;
	private final Path2D.Double polygon=new Path2D.Double();
	double minX=Double.POSITIVE_INFINITY, minY=minX, maxX=-minX, maxY=-minX;
	ProjectedPatchFootprint(SurfaceReferenceFrame frame, PatchLocation location) {
		corners=frame.projectPatchBoundary(location.patch);
		for (int i=0; i<corners.length; i++) {
			double[] p=corners[i];
			if (i == 0) polygon.moveTo(p[0], p[1]); else polygon.lineTo(p[0], p[1]);
			minX=Math.min(minX, p[0]); maxX=Math.max(maxX, p[0]);
			minY=Math.min(minY, p[1]); maxY=Math.max(maxY, p[1]);
		}
		polygon.closePath();
	}
	/** Include contacts on the polygon edge; Path2D.contains alone excludes them. */
	boolean contains(double x, double y) {
		if (polygon.contains(x, y)) return true;
		for (int i=0; i<corners.length; i++) {
			double[] a=corners[i], b=corners[(i+1)%corners.length];
			if (Line2D.ptSegDist(a[0], a[1], b[0], b[1], x, y) <= EDGE_TOLERANCE_KM) return true;
		}
		return false;
	}

	/** A fresh area because callers perform destructive Boolean operations. */
	Area area() { return new Area(polygon); }
}
