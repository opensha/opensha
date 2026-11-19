package org.opensha.sha.simulators.stiffness.patches;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.opensha.sha.earthquake.FocalMechanism;

/** A finite, planar triangle. Winding is canonicalized to the upward-facing side. */
public final class TriangularPatch extends StiffnessPatch {
	public TriangularPatch(double[][] vertices, double rake) {
		this(vertices, rake, Double.NaN);
	}

	/** Strike hint resolves the otherwise ambiguous horizontal/vertical mechanisms. */
	public TriangularPatch(double[][] vertices, double rake, double strikeHint) {
		this(new Geometry(vertices, strikeHint), rake);
	}

	private TriangularPatch(Geometry geometry, double rake) {
		super(geometry.center, geometry.area, geometry.vertices,
				new FocalMechanism(geometry.strike, geometry.dip, rake));
		Preconditions.checkArgument(Double.isFinite(rake), "Nonfinite rake");
	}

	/**
	 * Project physical slip into cutde's strike/up-dip/opening axes. In particular, its arbitrary northward strike for
	 * a horizontal triangle need not equal the trace-based strike retained by a different receiver or source
	 * representation.
	 */
	public double[] getKernelSlip() {
		Vector3D a = new Vector3D(corner[0]), b = new Vector3D(corner[1]), c = new Vector3D(corner[2]);
		Vector3D normal = b.subtract(a).crossProduct(c.subtract(a)).normalize();
		Vector3D strike = Vector3D.PLUS_K.crossProduct(normal);
		if (strike.getNorm() == 0)
			strike = Vector3D.PLUS_J.scalarMultiply(normal.getZ());
		strike = strike.normalize();
		Vector3D upDip = normal.crossProduct(strike), slip = new Vector3D(u);
		return new double[] { slip.dotProduct(strike), slip.dotProduct(upDip), 0 };
	}

	private static final class Geometry {
		final double[][] vertices = new double[3][];
		final double[] center;
		final double area, strike, dip;

		Geometry(double[][] input, double strikeHint) {
			Preconditions.checkArgument(input.length == 3, "Triangle needs three vertices");
			for (int i = 0; i < 3; i++) {
				Preconditions.checkArgument(input[i].length == 3, "Vertex needs three coordinates");
				vertices[i] = input[i].clone();
				for (double v : vertices[i])
					Preconditions.checkArgument(Double.isFinite(v), "Nonfinite vertex");
				Preconditions.checkArgument(vertices[i][2] <= 0, "Triangle extends above free surface");
			}
			Vector3D a = new Vector3D(vertices[0]), b = new Vector3D(vertices[1]), c = new Vector3D(vertices[2]);
			Vector3D n = b.subtract(a).crossProduct(c.subtract(a));
			area = 0.5 * n.getNorm();
			Preconditions.checkArgument(Double.isFinite(area) && area > 0, "Degenerate triangle");
			// The lexicographic rule makes exactly vertical triangles independent
			// of input winding too; reversing a trace must not reverse physical slip.
			boolean reverseVertical = Double.isFinite(strikeHint)
					? n.getX() * Math.cos(Math.toRadians(strikeHint))
							- n.getY() * Math.sin(Math.toRadians(strikeHint)) < 0
					: n.getX() < 0 || (n.getX() == 0 && n.getY() < 0);
			if (n.getZ() < 0 || (n.getZ() == 0 && reverseVertical)) {
				double[] temp = vertices[1];
				vertices[1] = vertices[2];
				vertices[2] = temp;
				n = n.negate();
			}
			n = n.normalize();
			center = new Vector3D(1d / 3, a, 1d / 3, b, 1d / 3, c).toArray();
			dip = Math.toDegrees(Math.atan2(Math.hypot(n.getX(), n.getY()), n.getZ()));
			// A horizontal triangle has no geometrically preferred strike. Use
			// its first edge; the kernel slip projection handles its own convention.
			Vector3D along = new Vector3D(vertices[1]).subtract(a);
			strike = Math.hypot(n.getX(), n.getY()) == 0
					? (Double.isFinite(strikeHint)
							? strikeHint
							: (Math.toDegrees(Math.atan2(along.getX(), along.getY())) + 360) % 360)
					: (Math.toDegrees(Math.atan2(-n.getY(), n.getX())) + 360) % 360;
		}
	}
}
