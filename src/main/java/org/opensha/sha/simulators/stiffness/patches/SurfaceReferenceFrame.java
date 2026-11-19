package org.opensha.sha.simulators.stiffness.patches;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.List;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;

import org.opensha.sha.simulators.stiffness.patches.GriddedSurfaceGeometry.SurfacePoint;

/**
 * A 2D map of the supplied surface. Reference/Cartesian coordinates are km;
 * stiffness patch coordinates are meters. Implementations must reject folded or
 * ambiguous mapped grids. Physical positions and mechanisms retain the original
 * bilinear surface, independently of the reference-sheet shape.
 */
interface SurfaceReferenceFrame {
	GriddedSurfaceGeometry geometry();
	default double minX() { return 0; }
	default double minY() { return 0; }
	double maxX();
	double maxY();
	double nodeX(int row,int col);
	double nodeY(int row,int col);
	double minimumEdgeRatio();
	double minimumAreaRatio();
	/** Distance from the original best-fit plane, also the baseline for a curved frame. */
	double rmsPlaneDistance();
	/** Project any physical point, including patch corners outside the surface. */
	double[] project(Vector3D point);
	Path2D.Double outlineCopy();
	boolean contains(double x,double y);
	double distanceToBoundary(double x,double y);
	Area centerDomain(double spacing);
	Area centerDomain(double spacing,double halfWidth);
	Area centerDomain(double spacing,double leftMargin,double rightMargin);
	double[] inside(double x,double y,Area centers);
	List<double[]> intervals(double transverse,boolean horizontal);
	SurfacePoint sample(double x,double y);
	Vector3D point(double x,double y);
	/**
	 * Invert the bilinear map of the projected grid nodes. For a curved sheet,
	 * this is a grid approximation to the continuous projection; its accuracy
	 * follows the supplied surface sampling. Physical interpolation stays on
	 * the original surface, rather than on the fitted reference sheet.
	 */
	double[] indices(double x,double y);

	/** Boundary of a physical tangent rectangle in reference coordinates. */
	default double[][] projectPatchBoundary(StiffnessPatch patch) {
		double[][] points=new double[patch.corner.length][];
		for (int i=0; i<patch.corner.length; i++) points[i]=project(new Vector3D(patch.corner[i]).scalarMultiply(0.001));
		return points;
	}
}
