package org.opensha.sha.simulators.stiffness.patches;

import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchLocation;

/** Actual meter-based tangent rectangle projected into the reference plane in km. */
final class ProjectedPatchFootprint {
	private static final double EDGE_TOLERANCE_KM = 1e-6; // 1 mm contact tolerance
	private final double[][] corners=new double[4][];
	private final Path2D.Double polygon=new Path2D.Double();
	double minX=Double.POSITIVE_INFINITY, minY=minX, maxX=-minX, maxY=-minX;
	ProjectedPatchFootprint(SurfaceReferenceFrame frame, PatchLocation location) {
		for (int i=0; i<4; i++) {
			double[] p=frame.project(new Vector3D(location.patch.corner[i]).scalarMultiply(0.001));
			corners[i]=p;
			if (i == 0) polygon.moveTo(p[0], p[1]); else polygon.lineTo(p[0], p[1]);
			minX=Math.min(minX, p[0]); maxX=Math.max(maxX, p[0]);
			minY=Math.min(minY, p[1]); maxY=Math.max(maxY, p[1]);
		}
		polygon.closePath();
	}
	/** Include contacts on the polygon edge; Path2D.contains alone excludes them. */
	boolean contains(double x, double y) {
		if (polygon.contains(x, y)) return true;
		for (int i=0; i<4; i++) {
			double[] a=corners[i], b=corners[(i+1)%4];
			if (Line2D.ptSegDist(a[0], a[1], b[0], b[1], x, y) <= EDGE_TOLERANCE_KM) return true;
		}
		return false;
	}

	/** A fresh area because callers perform destructive Boolean operations. */
	Area area() { return new Area(polygon); }
}
