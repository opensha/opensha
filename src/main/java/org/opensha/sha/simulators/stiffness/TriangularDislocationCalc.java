package org.opensha.sha.simulators.stiffness;

import static java.lang.Math.*;

import com.google.common.base.Preconditions;

/**
 * Analytical triangular dislocations in an isotropic elastic half-space, following
 * Nikkhoo & Walter (2015), doi:10.1093/gji/ggv035. Coordinates are East/North/Up in
 * meters; the free surface is z=0. Ported from cutde 26.3.6's common.cu strain
 * routines (https://github.com/cutde-org/cutde). No native or Python runtime is used.
 *
 * <p>The angular-dislocation equations retain the reference implementation's names
 * and evaluation order so they can be audited against it. Tensor components are
 * xx, yy, zz, xy, xz, yz; shear components are tensor strains, not engineering strains.
 * Real singularities at dislocation edges are intentionally not regularized.
 */
public final class TriangularDislocationCalc {
	private TriangularDislocationCalc() {
	}

	private static final double EPS = Math.ulp(1d);
	// Near-vertical cotangent terms need more than ordinary doubles. Keep the
	// geometry unchanged: use about 31 decimal digits below 0.57 degrees, and
	// the original 60-digit evaluation below 0.0057 degrees. The latter protects
	// the remaining precision as cotangent powers grow. Exact vertical edges
	// have an analytical zero correction and bypass both paths.
	private static final double MIN_EDGE_SINE_FOR_DOUBLE = 0.01;
	private static final double MIN_EDGE_SINE_FOR_EXTENDED = 1e-4;

	/**
	 * Calculates strain at a point for uniform slip on a triangle. Slip components
	 * are strike slip, up-dip slip, and opening, in meters (cutde convention).
	 * Vertex winding sets the normal; vertices are not silently reordered.
	 * Degenerate triangles and inputs above the free surface are rejected.
	 */
	public static double[] strain(double[][] vertices, double[] observation, double[] slip, double poisson) {
		return strain(vertices, observation, slip, poisson, false);
	}

	/** Original 60-digit correction remains available internally as a regression oracle. */
	static double[] strain(double[][] vertices, double[] observation, double[] slip, double poisson,
			boolean conservativePrecision) {
		Preconditions.checkArgument(vertices.length == 3 && slip.length == 3,
				"Expected three vertices/slip components");
		Preconditions.checkArgument(Double.isFinite(poisson) && poisson > -1 && poisson < 0.5,
				"Invalid Poisson ratio: %s", poisson);
		Vec3[] tri = new Vec3[3];
		for (int i = 0; i < 3; i++)
			tri[i] = point(vertices[i]);
		Vec3 obs = point(observation);
		for (double value : slip)
			Preconditions.checkArgument(Double.isFinite(value), "Nonfinite slip");
		Preconditions.checkArgument(length3(cross3(sub3(tri[1], tri[0]), sub3(tri[2], tri[0]))) > 0,
				"Degenerate triangle");
		// Internally the TD coordinate axes are normal, strike, and up-dip.
		Vec3 localSlip = make3(slip[2], slip[0], slip[1]);
		Basis basis = new Basis(tri, false);
		Tensor main = fullSpace(basis, obs, localSlip, poisson);
		Vec3 globalSlip = inv_transform3(basis.normal, basis.strike, basis.dip, localSlip);
		Tensor correction = add6(add6(AngSetupFSC_S(obs, globalSlip, tri[0], tri[1], poisson, conservativePrecision),
				AngSetupFSC_S(obs, globalSlip, tri[1], tri[2], poisson, conservativePrecision)),
				AngSetupFSC_S(obs, globalSlip, tri[2], tri[0], poisson, conservativePrecision));
		Vec3[] image = new Vec3[3];
		for (int i = 0; i < 3; i++)
			image[i] = make3(tri[i].x, tri[i].y, -tri[i].z);
		Tensor total = add6(add6(main, correction), fullSpace(new Basis(image, true), obs, localSlip, poisson));
		return new double[] { total.x, total.y, total.z, total.a, total.b, total.c };
	}

	private static Vec3 point(double[] xyz) {
		Preconditions.checkArgument(xyz.length == 3, "Expected three coordinates");
		for (double value : xyz)
			Preconditions.checkArgument(Double.isFinite(value), "Nonfinite coordinate");
		Preconditions.checkArgument(xyz[2] <= 0, "Point above free surface: z=%s", xyz[2]);
		return make3(xyz[0], xyz[1], xyz[2]);
	}

	/** Reusable triangle-local geometry; image normals follow the reference convention. */
	private static final class Basis {
		final Vec3 normal, strike, dip, origin, p0, p1, p2, e12, e13, e23;
		final double angleA, angleB, angleC;

		Basis(Vec3[] tri, boolean image) {
			normal = normalize3(cross3(sub3(tri[1], tri[0]), sub3(tri[2], tri[0])));
			Vec3 s = cross3(make3(0, 0, 1), normal);
			if (length3(s) == 0) {
				s = make3(0, normal.z, 0);
				if (image && tri[0].z > 0)
					s = negate3(s);
			}
			strike = normalize3(s);
			dip = cross3(normal, strike);
			origin = tri[1];
			p0 = transform3(normal, strike, dip, sub3(tri[0], origin));
			p1 = make3(0, 0, 0);
			p2 = transform3(normal, strike, dip, sub3(tri[2], origin));
			e12 = normalize3(sub3(p1, p0));
			e13 = normalize3(sub3(p2, p0));
			e23 = normalize3(sub3(p2, p1));
			angleA = angle(dot3(e12, e13));
			angleB = angle(dot3(negate3(e12), e23));
			angleC = angle(dot3(e23, e13));
		}
	}

	private static double angle(double cosine) {
		return acos(max(-1d, min(1d, cosine)));
	}

	private static Tensor fullSpace(Basis b, Vec3 obs, Vec3 slip, double nu) {
		Vec3 p = transform3(b.normal, b.strike, b.dip, sub3(obs, b.origin));
		int mode = trimodefinder(p, b.p0, b.p1, b.p2);
		Tensor out;
		if (mode == 1) {
			out = add6(add6(TDSetupS(p, b.angleA, slip, nu, b.p0, negate3(b.e13)),
					TDSetupS(p, b.angleB, slip, nu, b.p1, b.e12)),
					TDSetupS(p, b.angleC, slip, nu, b.p2, b.e23));
		} else if (mode == -1) {
			out = add6(add6(TDSetupS(p, b.angleA, slip, nu, b.p0, b.e13),
					TDSetupS(p, b.angleB, slip, nu, b.p1, negate3(b.e12))),
					TDSetupS(p, b.angleC, slip, nu, b.p2, negate3(b.e23)));
		} else {
			out = make6(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
		}
		return tensor_transform3(b.normal, b.strike, b.dip, out);
	}

	private static final class Vec2 {
		double x, y;
	}

	private static final class Vec3 {
		double x, y, z;
	}

	private static final class Tensor {
		double x, y, z, a, b, c;
	}

	/*
	MIT License
	
	Copyright (c) 2021 T. Ben Thompson
	
	Permission is hereby granted, free of charge, to any person obtaining a copy
	of this software and associated documentation files (the "Software"), to deal
	in the Software without restriction, including without limitation the rights
	to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
	copies of the Software, and to permit persons to whom the Software is
	furnished to do so, subject to the following conditions:
	
	The above copyright notice and this permission notice shall be included in all
	copies or substantial portions of the Software.
	
	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
	OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
	SOFTWARE.
	 */

	private static Vec2 mul2(Vec2 a, Vec2 b) {
		Vec2 out = new Vec2();

		out.x = a.x * b.x;

		out.y = a.y * b.y;
		return out;
	}

	private static double sum2(Vec2 x) {
		double out = 0.0;
		out += x.x;
		out += x.y;
		return out;
	}

	private static double dot2(Vec2 x, Vec2 y) {
		return sum2(mul2(x, y));
	}

	private static Vec2 make2(
			double x,
			double y) {
		Vec2 out = new Vec2();
		out.x = x;
		out.y = y;
		return out;
	}

	private static Vec3 sub3(Vec3 a, Vec3 b) {
		Vec3 out = new Vec3();

		out.x = a.x - b.x;

		out.y = a.y - b.y;

		out.z = a.z - b.z;
		return out;
	}

	private static Vec3 mul3(Vec3 a, Vec3 b) {
		Vec3 out = new Vec3();

		out.x = a.x * b.x;

		out.y = a.y * b.y;

		out.z = a.z * b.z;
		return out;
	}

	private static Vec3 mul_scalar3(Vec3 a, double b) {
		Vec3 out = new Vec3();

		out.x = a.x * b;

		out.y = a.y * b;

		out.z = a.z * b;
		return out;
	}

	private static Vec3 div_scalar3(Vec3 a, double b) {
		Vec3 out = new Vec3();

		out.x = a.x / b;

		out.y = a.y / b;

		out.z = a.z / b;
		return out;
	}

	private static double sum3(Vec3 x) {
		double out = 0.0;
		out += x.x;
		out += x.y;
		out += x.z;
		return out;
	}

	private static double dot3(Vec3 x, Vec3 y) {
		return sum3(mul3(x, y));
	}

	private static double length3(Vec3 x) {
		return sqrt(dot3(x, x));
	}

	private static Vec3 negate3(Vec3 x) {
		return mul_scalar3(x, -1);
	}

	private static Vec3 normalize3(Vec3 x) {
		return div_scalar3(x, length3(x));
	}

	private static Vec3 make3(
			double x,
			double y,
			double z) {
		Vec3 out = new Vec3();
		out.x = x;
		out.y = y;
		out.z = z;
		return out;
	}

	private static Tensor add6(Tensor a, Tensor b) {
		Tensor out = new Tensor();

		out.x = a.x + b.x;

		out.y = a.y + b.y;

		out.z = a.z + b.z;

		out.a = a.a + b.a;

		out.b = a.b + b.b;

		out.c = a.c + b.c;
		return out;
	}

	private static Tensor sub6(Tensor a, Tensor b) {
		Tensor out = new Tensor();

		out.x = a.x - b.x;

		out.y = a.y - b.y;

		out.z = a.z - b.z;

		out.a = a.a - b.a;

		out.b = a.b - b.b;

		out.c = a.c - b.c;
		return out;
	}

	private static Tensor make6(
			double x,
			double y,
			double z,
			double a,
			double b,
			double c) {
		Tensor out = new Tensor();
		out.x = x;
		out.y = y;
		out.z = z;
		out.a = a;
		out.b = b;
		out.c = c;
		return out;
	}

	private static Vec2 transform2(Vec2 a, Vec2 b, Vec2 v) {
		Vec2 out = new Vec2();
		out.x = dot2(a, v);
		out.y = dot2(b, v);
		return out;
	}

	private static Vec3 transform3(Vec3 a, Vec3 b, Vec3 c, Vec3 v) {
		Vec3 out = new Vec3();
		out.x = dot3(a, v);
		out.y = dot3(b, v);
		out.z = dot3(c, v);
		return out;
	}

	private static Vec3 inv_transform3(Vec3 a, Vec3 b, Vec3 c, Vec3 v) {
		Vec3 out = new Vec3();
		out.x = a.x * v.x + b.x * v.y + c.x * v.z;
		out.y = a.y * v.x + b.y * v.y + c.y * v.z;
		out.z = a.z * v.x + b.z * v.y + c.z * v.z;
		return out;
	}

	private static Vec3 cross3(Vec3 x, Vec3 y) {
		Vec3 out = new Vec3();
		out.x = x.y * y.z - x.z * y.y;
		out.y = x.z * y.x - x.x * y.z;
		out.z = x.x * y.y - x.y * y.x;
		return out;
	}

	private static Tensor tensor_transform3(Vec3 a, Vec3 b, Vec3 c, Tensor tensor) {
		double[] A = new double[9];
		A[0] = a.x;
		A[1] = a.y;
		A[2] = a.z;
		A[3] = b.x;
		A[4] = b.y;
		A[5] = b.z;
		A[6] = c.x;
		A[7] = c.y;
		A[8] = c.z;
		Tensor out = new Tensor();
		out.x = A[0] * A[0] * tensor.x + 2 * A[0] * A[3] * tensor.a + 2 * A[0] * A[6] * tensor.b +
				2 * A[3] * A[6] * tensor.c + A[3] * A[3] * tensor.y + A[6] * A[6] * tensor.z;
		out.y = A[1] * A[1] * tensor.x + 2 * A[1] * A[4] * tensor.a + 2 * A[1] * A[7] * tensor.b +
				2 * A[4] * A[7] * tensor.c + A[4] * A[4] * tensor.y + A[7] * A[7] * tensor.z;
		out.z = A[2] * A[2] * tensor.x + 2 * A[2] * A[5] * tensor.a + 2 * A[2] * A[8] * tensor.b +
				2 * A[5] * A[8] * tensor.c + A[5] * A[5] * tensor.y + A[8] * A[8] * tensor.z;
		out.a = A[0] * A[1] * tensor.x + (A[0] * A[4] + A[1] * A[3]) * tensor.a + (A[0] * A[7] +
				A[1] * A[6]) * tensor.b + (A[7] * A[3] + A[6] * A[4]) * tensor.c + A[4] * A[3] * tensor.y +
				A[6] * A[7] * tensor.z;
		out.b = A[0] * A[2] * tensor.x + (A[0] * A[5] + A[2] * A[3]) * tensor.a + (A[0] * A[8] +
				A[2] * A[6]) * tensor.b + (A[8] * A[3] + A[6] * A[5]) * tensor.c + A[5] * A[3] * tensor.y +
				A[6] * A[8] * tensor.z;
		out.c = A[1] * A[2] * tensor.x + (A[2] * A[4] + A[1] * A[5]) * tensor.a + (A[2] * A[7] +
				A[1] * A[8]) * tensor.b + (A[7] * A[5] + A[8] * A[4]) * tensor.c + A[4] * A[5] * tensor.y +
				A[7] * A[8] * tensor.z;
		return out;
	}

	private static int trimodefinder(Vec3 obs, Vec3 tri0, Vec3 tri1, Vec3 tri2) {
		// trimodefinder calculates the normalized barycentric coordinates of
		// the points with respect to the TD vertices and specifies the appropriate
		// artefact-free configuration of the angular dislocations for the
		// calculations. The input matrices x, y and z share the same size and
		// correspond to the y, z and x coordinates in the TDCS, respectively. p1,
		// p2 and p3 are two-component matrices representing the y and z coordinates
		// of the TD vertices in the TDCS, respectively.
		// The components of the output (trimode) corresponding to each calculation
		// points, are 1 for the first configuration, -1 for the second
		// configuration and 0 for the calculation point that lie on the TD sides.

		double a = ((tri1.z - tri2.z) * (obs.y - tri2.y) + (tri2.y - tri1.y) * (obs.z - tri2.z)) /
				((tri1.z - tri2.z) * (tri0.y - tri2.y) + (tri2.y - tri1.y) * (tri0.z - tri2.z));
		double b = ((tri2.z - tri0.z) * (obs.y - tri2.y) + (tri0.y - tri2.y) * (obs.z - tri2.z)) /
				((tri1.z - tri2.z) * (tri0.y - tri2.y) + (tri2.y - tri1.y) * (tri0.z - tri2.z));
		double c = 1 - a - b;

		int result = 1;
		if ((a <= 0 && b > c && c > a) ||
				(b <= 0 && c > a && a > b) ||
				(c <= 0 && a > b && b > c)) {
			result = -1;
		}

		if ((a == 0 && b >= 0 && c >= 0) ||
				(a >= 0 && b == 0 && c >= 0) ||
				(a >= 0 && b >= 0 && c == 0)) {
			result = 0;
		}
		if (result == 0 && obs.x != 0) {
			result = 1;
		}

		return result;
	}

	private static Tensor AngDisStrain(double x, double y, double z, double alpha, double bx, double by, double bz,
			double nu) {
		// AngDisStrain calculates the strains associated with an angular
		// dislocation in an elastic full-space.

		double cosA = cos(alpha);
		double sinA = sin(alpha);
		double eta = y * cosA - z * sinA;
		double zeta = y * sinA + z * cosA;

		double x2 = x * x;
		double y2 = y * y;
		double z2 = z * z;
		double r2 = x2 + y2 + z2;
		double r = sqrt(r2);
		double r3 = r * r2;
		double rz = r * (r - z);
		double rmz = (r - z);
		double r2z2 = r2 * rmz * rmz;
		double r3z = r3 * rmz;

		double W = zeta - r;
		double W2 = W * W;
		double Wr = W * r;
		double W2r = W2 * r;
		double Wr3 = W * r3;
		double W2r2 = W2 * r2;

		double C = (r * cosA - z) / Wr;
		double S = (r * sinA - y) / Wr;

		// Partial derivatives of the Burgers' function
		double rFi_rx = (eta / r / (r - zeta) - y / r / (r - z)) / 4 / PI;
		double rFi_ry = (x / r / (r - z) - cosA * x / r / (r - zeta)) / 4 / PI;
		double rFi_rz = (sinA * x / r / (r - zeta)) / 4 / PI;

		Tensor out = new Tensor();
		out.x = bx * (rFi_rx) +
				bx / 8 / PI / (1 - nu) * (eta / Wr + eta * x2 / W2r2 - eta * x2 / Wr3 + y / rz -
						x2 * y / r2z2 - x2 * y / r3z)
				-
				by * x / 8 / PI / (1 - nu) * (((2 * nu + 1) / Wr + x2 / W2r2 - x2 / Wr3) * cosA +
						(2 * nu + 1) / rz - x2 / r2z2 - x2 / r3z)
				+
				bz * x * sinA / 8 / PI / (1 - nu) * ((2 * nu + 1) / Wr + x2 / W2r2 - x2 / Wr3);

		out.y = by * (rFi_ry) +
				bx / 8 / PI / (1 - nu) * ((1 / Wr + S * S - y2 / Wr3) * eta + (2 * nu + 1) * y / rz - y * y * y / r2z2 -
						y * y * y / r3z - 2 * nu * cosA * S)
				-
				by * x / 8 / PI / (1 - nu) * (1 / rz - y2 / r2z2 - y2 / r3z +
						(1 / Wr + S * S - y2 / Wr3) * cosA)
				+
				bz * x * sinA / 8 / PI / (1 - nu) * (1 / Wr + S * S - y2 / Wr3);

		out.z = bz * (rFi_rz) +
				bx / 8 / PI / (1 - nu) * (eta / W / r + eta * C * C - eta * z2 / Wr3 + y * z / r3 +
						2 * nu * sinA * C)
				-
				by * x / 8 / PI / (1 - nu) * ((1 / Wr + C * C - z2 / Wr3) * cosA + z / r3) +
				bz * x * sinA / 8 / PI / (1 - nu) * (1 / Wr + C * C - z2 / Wr3);

		out.a = bx * (rFi_ry) / 2 + by * (rFi_rx) / 2 -
				bx / 8 / PI / (1 - nu) * (x * y2 / r2z2 - nu * x / rz + x * y2 / r3z - nu * x * cosA / Wr +
						eta * x * S / Wr + eta * x * y / Wr3)
				+
				by / 8 / PI / (1 - nu) * (x2 * y / r2z2 - nu * y / rz + x2 * y / r3z + nu * cosA * S +
						x2 * y * cosA / Wr3 + x2 * cosA * S / Wr)
				-
				bz * sinA / 8 / PI / (1 - nu) * (nu * S + x2 * S / Wr + x2 * y / Wr3);

		out.b = bx * (rFi_rz) / 2 + bz * (rFi_rx) / 2 -
				bx / 8 / PI / (1 - nu) * (-x * y / r3 + nu * x * sinA / Wr + eta * x * C / Wr +
						eta * x * z / Wr3)
				+
				by / 8 / PI / (1 - nu) * (-x2 / r3 + nu / r + nu * cosA * C + x2 * z * cosA / Wr3 +
						x2 * cosA * C / Wr)
				-
				bz * sinA / 8 / PI / (1 - nu) * (nu * C + x2 * C / Wr + x2 * z / Wr3);

		out.c = by * (rFi_rz) / 2 + bz * (rFi_ry) / 2 +
				bx / 8 / PI / (1 - nu) * (y2 / r3 - nu / r - nu * cosA * C + nu * sinA * S + eta * sinA * cosA / W2 -
						eta * (y * cosA + z * sinA) / W2r + eta * y * z / W2r2 - eta * y * z / Wr3)
				-
				by * x / 8 / PI / (1 - nu) * (y / r3 + sinA * cosA * cosA / W2 - cosA * (y * cosA + z * sinA) /
						W2r + y * z * cosA / W2r2 - y * z * cosA / Wr3)
				-
				bz * x * sinA / 8 / PI / (1 - nu) * (y * z / Wr3 - sinA * cosA / W2 + (y * cosA + z * sinA) /
						W2r - y * z / W2r2);
		return out;
	}

	private static Tensor TDSetupS(Vec3 obs, double alpha, Vec3 slip, double nu,
			Vec3 TriVertex, Vec3 SideVec) {
		// TDSetupS transforms coordinates of the calculation points as well as
		// slip vector components from ADCS into TDCS. It then calculates the
		// strains in ADCS and transforms them into TDCS.

		// Transformation matrix
		Vec2 A1 = make2(SideVec.z, -SideVec.y);
		Vec2 A2 = make2(SideVec.y, SideVec.z);

		// Transform coordinates of the calculation points from TDCS into ADCS
		Vec2 r1 = transform2(A1, A2, make2(obs.y - TriVertex.y, obs.z - TriVertex.z));
		double y1 = r1.x;
		double z1 = r1.y;

		// Transform the in-plane slip vector components from TDCS into ADCS
		Vec2 r2 = transform2(A1, A2, make2(slip.y, slip.z));
		double by1 = r2.x;
		double bz1 = r2.y;

		// Calculate strains associated with an angular dislocation in ADCS
		Tensor out_adcs = AngDisStrain(obs.x, y1, z1, -PI + alpha, slip.x, by1, bz1, nu);

		// Transform strains from ADCS into TDCS
		Vec3 B0 = make3(1.0, 0.0, 0.0);
		Vec3 B1 = make3(0.0, A1.x, A1.y);
		Vec3 B2 = make3(0.0, A2.x, A2.y);
		return tensor_transform3(B0, B1, B2, out_adcs);
	}

	private static Tensor AngDisStrainFSC(double y1, double y2, double y3, double beta,
			double b1, double b2, double b3, double nu, double a) {
		FreeSurfaceTerms terms = new FreeSurfaceTerms(y1, y2, y3, beta, b1, b2, b3, nu, a);
		return make6(correctionXX(terms), correctionYY(terms), correctionZZ(terms),
				correctionXY(terms), correctionXZ(terms), correctionYZ(terms));
	}

	// Each tensor component is a separate method so the JVM can compile it. The
	// combined upstream expression exceeds HotSpot's default 8 KB method limit.
	// Shared terms are computed once; the scalar equations retain their exact order.
	private static final class FreeSurfaceTerms {
		double y1, y2, y3, b1, b2, b3, nu, a, sinB, cosB, cotB, y3b, z1b, z3b, rb2, rb, W1, W2, W3, W4, W5, W6, W7, W8,
				W9, N1, rFib_ry2, rFib_ry1, rFib_ry3;

		FreeSurfaceTerms(double y1, double y2, double y3, double beta,
				double b1, double b2, double b3, double nu, double a) {
			this.y1 = y1;
			this.y2 = y2;
			this.y3 = y3;
			this.b1 = b1;
			this.b2 = b2;
			this.b3 = b3;
			this.nu = nu;
			this.a = a;
			sinB = sin(beta);
			cosB = cos(beta);
			if (cosB == 0.) {
				cosB = EPS;
			}
			cotB = cosB / sinB;
			y3b = y3 + 2 * a;
			z1b = y1 * cosB + y3b * sinB;
			z3b = -y1 * sinB + y3b * cosB;
			rb2 = y1 * y1 + y2 * y2 + y3b * y3b;
			rb = sqrt(rb2);

			W1 = rb * cosB + y3b;
			W2 = cosB + a / rb;
			W3 = cosB + y3b / rb;
			W4 = nu + a / rb;
			W5 = 2 * nu + a / rb;
			W6 = rb + y3b;
			W7 = rb + z3b;
			W8 = y3 + a;
			W9 = 1 + a / rb / cosB;

			N1 = 1 - 2 * nu;

			rFib_ry2 = z1b / rb / (rb + z3b) - y1 / rb / (rb + y3b);
			rFib_ry1 = y2 / rb / (rb + y3b) - cosB * y2 / rb / (rb + z3b);
			rFib_ry3 = -sinB * y2 / rb / (rb + z3b);

		}
	}

	// @formatter:off — fixed continuation indentation for the reference equations.
	private static double correctionXX(FreeSurfaceTerms t) {
		return t.b1 * (1.0 / 4.0 * ((-2 + 2 * t.nu) * t.N1 * t.rFib_ry1 * (t.cotB * t.cotB) - t.N1 * t.y2 /
				(t.W6 * t.W6) * ((1 - t.W5) * t.cotB - t.y1 / t.W6 * t.W4) / t.rb * t.y1 + t.N1 * t.y2 / t.W6 * (t.a /
				(t.rb * t.rb * t.rb) * t.y1 * t.cotB - 1 / t.W6 * t.W4 + (t.y1 * t.y1) / (t.W6 * t.W6) * t.W4 / t.rb +
				(t.y1 * t.y1) / t.W6 * t.a / (t.rb * t.rb * t.rb)) - t.N1 * t.y2 * t.cosB * t.cotB / (t.W7 * t.W7) *
				t.W2 * (t.y1 / t.rb - t.sinB) - t.N1 * t.y2 * t.cosB * t.cotB / t.W7 * t.a / (t.rb * t.rb * t.rb) * t.y1
				- 3 * t.a * t.y2 * t.W8 * t.cotB / (t.rb * t.rb2 * t.rb2) * t.y1 - t.y2 * t.W8 / (t.rb * t.rb * t.rb) /
				t.W6 * (-t.N1 * t.cotB + t.y1 / t.W6 * t.W5 + t.a * t.y1 / t.rb2) * t.y1 - t.y2 * t.W8 / t.rb2 / (t.W6 *
				t.W6) * (-t.N1 * t.cotB + t.y1 / t.W6 * t.W5 + t.a * t.y1 / t.rb2) * t.y1 + t.y2 * t.W8 / t.rb / t.W6 *
				(1 / t.W6 * t.W5 - (t.y1 * t.y1) / (t.W6 * t.W6) * t.W5 / t.rb - (t.y1 * t.y1) / t.W6 * t.a / (t.rb *
				t.rb * t.rb) + t.a / t.rb2 - 2 * t.a * (t.y1 * t.y1) / (t.rb2 * t.rb2)) - t.y2 * t.W8 / (t.rb * t.rb *
				t.rb) / t.W7 * (t.cosB / t.W7 * (t.W1 * (t.N1 * t.cosB - t.a / t.rb) * t.cotB + (2 - 2 * t.nu) * (t.rb *
				t.sinB - t.y1) * t.cosB) - t.a * t.y3b * t.cosB * t.cotB / t.rb2) * t.y1 - t.y2 * t.W8 / t.rb / (t.W7 *
				t.W7) * (t.cosB / t.W7 * (t.W1 * (t.N1 * t.cosB - t.a / t.rb) * t.cotB + (2 - 2 * t.nu) * (t.rb * t.sinB
				- t.y1) * t.cosB) - t.a * t.y3b * t.cosB * t.cotB / t.rb2) * (t.y1 / t.rb - t.sinB) + t.y2 * t.W8 / t.rb
				/ t.W7 * (-t.cosB / (t.W7 * t.W7) * (t.W1 * (t.N1 * t.cosB - t.a / t.rb) * t.cotB + (2 - 2 * t.nu) *
				(t.rb * t.sinB - t.y1) * t.cosB) * (t.y1 / t.rb - t.sinB) + t.cosB / t.W7 * (1 / t.rb * t.cosB * t.y1 *
				(t.N1 * t.cosB - t.a / t.rb) * t.cotB + t.W1 * t.a / (t.rb * t.rb2) * t.y1 * t.cotB + (2 - 2 * t.nu) *
				(1 / t.rb * t.sinB * t.y1 - 1) * t.cosB) + 2 * t.a * t.y3b * t.cosB * t.cotB / (t.rb2 * t.rb2) * t.y1))
				/ PI / (1 - t.nu)) + t.b2 * (1.0 / 4.0 * (t.N1 * (((2 - 2 * t.nu) * (t.cotB * t.cotB) + t.nu) / t.rb *
				t.y1 / t.W6 - ((2 - 2 * t.nu) * (t.cotB * t.cotB) + 1) * t.cosB * (t.y1 / t.rb - t.sinB) / t.W7) - t.N1
				/ (t.W6 * t.W6) * (-t.N1 * t.y1 * t.cotB + t.nu * t.y3b - t.a + t.a * t.y1 * t.cotB / t.rb + (t.y1 *
				t.y1) / t.W6 * t.W4) / t.rb * t.y1 + t.N1 / t.W6 * (-t.N1 * t.cotB + t.a * t.cotB / t.rb - t.a * (t.y1 *
				t.y1) * t.cotB / (t.rb * t.rb * t.rb) + 2 * t.y1 / t.W6 * t.W4 - (t.y1 * t.y1 * t.y1) / (t.W6 * t.W6) *
				t.W4 / t.rb - (t.y1 * t.y1 * t.y1) / t.W6 * t.a / (t.rb * t.rb * t.rb)) + t.N1 * t.cotB / (t.W7 * t.W7)
				* (t.z1b * t.cosB - t.a * (t.rb * t.sinB - t.y1) / t.rb / t.cosB) * (t.y1 / t.rb - t.sinB) - t.N1 *
				t.cotB / t.W7 * ((t.cosB * t.cosB) - t.a * (1 / t.rb * t.sinB * t.y1 - 1) / t.rb / t.cosB + t.a * (t.rb
				* t.sinB - t.y1) / (t.rb * t.rb * t.rb) / t.cosB * t.y1) - t.a * t.W8 * t.cotB / (t.rb * t.rb * t.rb) +
				3 * t.a * (t.y1 * t.y1) * t.W8 * t.cotB / (t.rb * t.rb2 * t.rb2) - t.W8 / (t.W6 * t.W6) * (2 * t.nu + 1
				/ t.rb * (t.N1 * t.y1 * t.cotB + t.a) - (t.y1 * t.y1) / t.rb / t.W6 * t.W5 - t.a * (t.y1 * t.y1) / (t.rb
				* t.rb * t.rb)) / t.rb * t.y1 + t.W8 / t.W6 * (-1 / (t.rb * t.rb * t.rb) * (t.N1 * t.y1 * t.cotB + t.a)
				* t.y1 + 1 / t.rb * t.N1 * t.cotB - 2 * t.y1 / t.rb / t.W6 * t.W5 + (t.y1 * t.y1 * t.y1) / (t.rb * t.rb
				* t.rb) / t.W6 * t.W5 + (t.y1 * t.y1 * t.y1) / t.rb2 / (t.W6 * t.W6) * t.W5 + (t.y1 * t.y1 * t.y1) /
				(t.rb2 * t.rb2) / t.W6 * t.a - 2 * t.a / (t.rb * t.rb * t.rb) * t.y1 + 3 * t.a * (t.y1 * t.y1 * t.y1) /
				(t.rb * t.rb2 * t.rb2)) - t.W8 * t.cotB / (t.W7 * t.W7) * (-t.cosB * t.sinB + t.a * t.y1 * t.y3b / (t.rb
				* t.rb * t.rb) / t.cosB + (t.rb * t.sinB - t.y1) / t.rb * ((2 - 2 * t.nu) * t.cosB - t.W1 / t.W7 *
				t.W9)) * (t.y1 / t.rb - t.sinB) + t.W8 * t.cotB / t.W7 * (t.a * t.y3b / (t.rb * t.rb * t.rb) / t.cosB -
				3 * t.a * (t.y1 * t.y1) * t.y3b / (t.rb * t.rb2 * t.rb2) / t.cosB + (1 / t.rb * t.sinB * t.y1 - 1) /
				t.rb * ((2 - 2 * t.nu) * t.cosB - t.W1 / t.W7 * t.W9) - (t.rb * t.sinB - t.y1) / (t.rb * t.rb * t.rb) *
				((2 - 2 * t.nu) * t.cosB - t.W1 / t.W7 * t.W9) * t.y1 + (t.rb * t.sinB - t.y1) / t.rb * (-1 / t.rb *
				t.cosB * t.y1 / t.W7 * t.W9 + t.W1 / (t.W7 * t.W7) * t.W9 * (t.y1 / t.rb - t.sinB) + t.W1 / t.W7 * t.a /
				(t.rb * t.rb * t.rb) / t.cosB * t.y1))) / PI / (1 - t.nu)) + t.b3 * (1.0 / 4.0 * (t.N1 * (-t.y2 / (t.W6
				* t.W6) * (1 + t.a / t.rb) / t.rb * t.y1 - t.y2 / t.W6 * t.a / (t.rb * t.rb * t.rb) * t.y1 + t.y2 *
				t.cosB / (t.W7 * t.W7) * t.W2 * (t.y1 / t.rb - t.sinB) + t.y2 * t.cosB / t.W7 * t.a / (t.rb * t.rb *
				t.rb) * t.y1) + t.y2 * t.W8 / (t.rb * t.rb * t.rb) * (t.a / t.rb2 + 1 / t.W6) * t.y1 - t.y2 * t.W8 /
				t.rb * (-2 * t.a / (t.rb2 * t.rb2) * t.y1 - 1 / (t.W6 * t.W6) / t.rb * t.y1) - t.y2 * t.W8 * t.cosB /
				(t.rb * t.rb * t.rb) / t.W7 * (t.W1 / t.W7 * t.W2 + t.a * t.y3b / t.rb2) * t.y1 - t.y2 * t.W8 * t.cosB /
				t.rb / (t.W7 * t.W7) * (t.W1 / t.W7 * t.W2 + t.a * t.y3b / t.rb2) * (t.y1 / t.rb - t.sinB) + t.y2 * t.W8
				* t.cosB / t.rb / t.W7 * (1 / t.rb * t.cosB * t.y1 / t.W7 * t.W2 - t.W1 / (t.W7 * t.W7) * t.W2 * (t.y1 /
				t.rb - t.sinB) - t.W1 / t.W7 * t.a / (t.rb * t.rb * t.rb) * t.y1 - 2 * t.a * t.y3b / (t.rb2 * t.rb2) *
				t.y1)) / PI / (1 - t.nu));
	}

	private static double correctionYY(FreeSurfaceTerms t) {
		return t.b1 * (1.0 / 4.0 * (t.N1 * (((2 - 2 * t.nu) * (t.cotB * t.cotB) - t.nu) / t.rb * t.y2 / t.W6 -
				((2 - 2 * t.nu) * (t.cotB * t.cotB) + 1 - 2 * t.nu) * t.cosB / t.rb * t.y2 / t.W7) + t.N1 / (t.W6 *
				t.W6) * (t.y1 * t.cotB * (1 - t.W5) + t.nu * t.y3b - t.a + (t.y2 * t.y2) / t.W6 * t.W4) / t.rb * t.y2 -
				t.N1 / t.W6 * (t.a * t.y1 * t.cotB / (t.rb * t.rb * t.rb) * t.y2 + 2 * t.y2 / t.W6 * t.W4 - (t.y2 * t.y2
				* t.y2) / (t.W6 * t.W6) * t.W4 / t.rb - (t.y2 * t.y2 * t.y2) / t.W6 * t.a / (t.rb * t.rb * t.rb)) + t.N1
				* t.z1b * t.cotB / (t.W7 * t.W7) * t.W2 / t.rb * t.y2 + t.N1 * t.z1b * t.cotB / t.W7 * t.a / (t.rb *
				t.rb * t.rb) * t.y2 + 3 * t.a * t.y2 * t.W8 * t.cotB / (t.rb * t.rb2 * t.rb2) * t.y1 - t.W8 / (t.W6 *
				t.W6) * (-2 * t.nu + 1 / t.rb * (t.N1 * t.y1 * t.cotB - t.a) + (t.y2 * t.y2) / t.rb / t.W6 * t.W5 + t.a
				* (t.y2 * t.y2) / (t.rb * t.rb * t.rb)) / t.rb * t.y2 + t.W8 / t.W6 * (-1 / (t.rb * t.rb * t.rb) * (t.N1
				* t.y1 * t.cotB - t.a) * t.y2 + 2 * t.y2 / t.rb / t.W6 * t.W5 - (t.y2 * t.y2 * t.y2) / (t.rb * t.rb *
				t.rb) / t.W6 * t.W5 - (t.y2 * t.y2 * t.y2) / t.rb2 / (t.W6 * t.W6) * t.W5 - (t.y2 * t.y2 * t.y2) /
				(t.rb2 * t.rb2) / t.W6 * t.a + 2 * t.a / (t.rb * t.rb * t.rb) * t.y2 - 3 * t.a * (t.y2 * t.y2 * t.y2) /
				(t.rb * t.rb2 * t.rb2)) - t.W8 / (t.W7 * t.W7) * ((t.cosB * t.cosB) - 1 / t.rb * (t.N1 * t.z1b * t.cotB
				+ t.a * t.cosB) + t.a * t.y3b * t.z1b * t.cotB / (t.rb * t.rb * t.rb) - 1 / t.rb / t.W7 * ((t.y2 * t.y2)
				* (t.cosB * t.cosB) - t.a * t.z1b * t.cotB / t.rb * t.W1)) / t.rb * t.y2 + t.W8 / t.W7 * (1 / (t.rb *
				t.rb * t.rb) * (t.N1 * t.z1b * t.cotB + t.a * t.cosB) * t.y2 - 3 * t.a * t.y3b * t.z1b * t.cotB / (t.rb
				* t.rb2 * t.rb2) * t.y2 + 1 / (t.rb * t.rb * t.rb) / t.W7 * ((t.y2 * t.y2) * (t.cosB * t.cosB) - t.a *
				t.z1b * t.cotB / t.rb * t.W1) * t.y2 + 1 / t.rb2 / (t.W7 * t.W7) * ((t.y2 * t.y2) * (t.cosB * t.cosB) -
				t.a * t.z1b * t.cotB / t.rb * t.W1) * t.y2 - 1 / t.rb / t.W7 * (2 * t.y2 * (t.cosB * t.cosB) + t.a *
				t.z1b * t.cotB / (t.rb * t.rb * t.rb) * t.W1 * t.y2 - t.a * t.z1b * t.cotB / t.rb2 * t.cosB * t.y2))) /
				PI / (1 - t.nu)) + t.b2 * (1.0 / 4.0 * ((2 - 2 * t.nu) * t.N1 * t.rFib_ry2 * (t.cotB * t.cotB) + t.N1 /
				t.W6 * ((t.W5 - 1) * t.cotB + t.y1 / t.W6 * t.W4) - t.N1 * (t.y2 * t.y2) / (t.W6 * t.W6) * ((t.W5 - 1) *
				t.cotB + t.y1 / t.W6 * t.W4) / t.rb + t.N1 * t.y2 / t.W6 * (-t.a / (t.rb * t.rb * t.rb) * t.y2 * t.cotB
				- t.y1 / (t.W6 * t.W6) * t.W4 / t.rb * t.y2 - t.y2 / t.W6 * t.a / (t.rb * t.rb * t.rb) * t.y1) - t.N1 *
				t.cotB / t.W7 * t.W9 + t.N1 * (t.y2 * t.y2) * t.cotB / (t.W7 * t.W7) * t.W9 / t.rb + t.N1 * (t.y2 *
				t.y2) * t.cotB / t.W7 * t.a / (t.rb * t.rb * t.rb) / t.cosB - t.a * t.W8 * t.cotB / (t.rb * t.rb * t.rb)
				+ 3 * t.a * (t.y2 * t.y2) * t.W8 * t.cotB / (t.rb * t.rb2 * t.rb2) + t.W8 / t.rb / t.W6 * (t.N1 * t.cotB
				- 2 * t.nu * t.y1 / t.W6 - t.a * t.y1 / t.rb * (1 / t.rb + 1 / t.W6)) - (t.y2 * t.y2) * t.W8 / (t.rb *
				t.rb * t.rb) / t.W6 * (t.N1 * t.cotB - 2 * t.nu * t.y1 / t.W6 - t.a * t.y1 / t.rb * (1 / t.rb + 1 /
				t.W6)) - (t.y2 * t.y2) * t.W8 / t.rb2 / (t.W6 * t.W6) * (t.N1 * t.cotB - 2 * t.nu * t.y1 / t.W6 - t.a *
				t.y1 / t.rb * (1 / t.rb + 1 / t.W6)) + t.y2 * t.W8 / t.rb / t.W6 * (2 * t.nu * t.y1 / (t.W6 * t.W6) /
				t.rb * t.y2 + t.a * t.y1 / (t.rb * t.rb * t.rb) * (1 / t.rb + 1 / t.W6) * t.y2 - t.a * t.y1 / t.rb * (-1
				/ (t.rb * t.rb * t.rb) * t.y2 - 1 / (t.W6 * t.W6) / t.rb * t.y2)) + t.W8 * t.cotB / t.rb / t.W7 * ((-2 +
				2 * t.nu) * t.cosB + t.W1 / t.W7 * t.W9 + t.a * t.y3b / t.rb2 / t.cosB) - (t.y2 * t.y2) * t.W8 * t.cotB
				/ (t.rb * t.rb * t.rb) / t.W7 * ((-2 + 2 * t.nu) * t.cosB + t.W1 / t.W7 * t.W9 + t.a * t.y3b / t.rb2 /
				t.cosB) - (t.y2 * t.y2) * t.W8 * t.cotB / t.rb2 / (t.W7 * t.W7) * ((-2 + 2 * t.nu) * t.cosB + t.W1 /
				t.W7 * t.W9 + t.a * t.y3b / t.rb2 / t.cosB) + t.y2 * t.W8 * t.cotB / t.rb / t.W7 * (1 / t.rb * t.cosB *
				t.y2 / t.W7 * t.W9 - t.W1 / (t.W7 * t.W7) * t.W9 / t.rb * t.y2 - t.W1 / t.W7 * t.a / (t.rb * t.rb *
				t.rb) / t.cosB * t.y2 - 2 * t.a * t.y3b / (t.rb2 * t.rb2) / t.cosB * t.y2)) / PI / (1 - t.nu)) + t.b3 *
				(1.0 / 4.0 * (t.N1 * (-t.sinB / t.rb * t.y2 / t.W7 + t.y2 / (t.W6 * t.W6) * (1 + t.a / t.rb) / t.rb *
				t.y1 + t.y2 / t.W6 * t.a / (t.rb * t.rb * t.rb) * t.y1 - t.z1b / (t.W7 * t.W7) * t.W2 / t.rb * t.y2 -
				t.z1b / t.W7 * t.a / (t.rb * t.rb * t.rb) * t.y2) - t.y2 * t.W8 / (t.rb * t.rb * t.rb) * (t.a / t.rb2 +
				1 / t.W6) * t.y1 + t.y1 * t.W8 / t.rb * (-2 * t.a / (t.rb2 * t.rb2) * t.y2 - 1 / (t.W6 * t.W6) / t.rb *
				t.y2) + t.W8 / (t.W7 * t.W7) * (t.sinB * (t.cosB - t.a / t.rb) + t.z1b / t.rb * (1 + t.a * t.y3b /
				t.rb2) - 1 / t.rb / t.W7 * ((t.y2 * t.y2) * t.cosB * t.sinB - t.a * t.z1b / t.rb * t.W1)) / t.rb * t.y2
				- t.W8 / t.W7 * (t.sinB * t.a / (t.rb * t.rb * t.rb) * t.y2 - t.z1b / (t.rb * t.rb * t.rb) * (1 + t.a *
				t.y3b / t.rb2) * t.y2 - 2 * t.z1b / (t.rb * t.rb2 * t.rb2) * t.a * t.y3b * t.y2 + 1 / (t.rb * t.rb *
				t.rb) / t.W7 * ((t.y2 * t.y2) * t.cosB * t.sinB - t.a * t.z1b / t.rb * t.W1) * t.y2 + 1 / t.rb2 / (t.W7
				* t.W7) * ((t.y2 * t.y2) * t.cosB * t.sinB - t.a * t.z1b / t.rb * t.W1) * t.y2 - 1 / t.rb / t.W7 * (2 *
				t.y2 * t.cosB * t.sinB + t.a * t.z1b / (t.rb * t.rb * t.rb) * t.W1 * t.y2 - t.a * t.z1b / t.rb2 * t.cosB
				* t.y2))) / PI / (1 - t.nu));
	}

	private static double correctionZZ(FreeSurfaceTerms t) {
		return t.b1 * (1.0 / 4.0 * ((2 - 2 * t.nu) * (t.N1 * t.rFib_ry3 * t.cotB - t.y2 / (t.W6 * t.W6) * t.W5 *
				(t.y3b / t.rb + 1) - 1.0 / 2.0 * t.y2 / t.W6 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b + t.y2 * t.cosB /
				(t.W7 * t.W7) * t.W2 * t.W3 + 1.0 / 2.0 * t.y2 * t.cosB / t.W7 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b)
				+ t.y2 / t.rb * (2 * t.nu / t.W6 + t.a / t.rb2) - 1.0 / 2.0 * t.y2 * t.W8 / (t.rb * t.rb * t.rb) * (2 *
				t.nu / t.W6 + t.a / t.rb2) * 2 * t.y3b + t.y2 * t.W8 / t.rb * (-2 * t.nu / (t.W6 * t.W6) * (t.y3b / t.rb
				+ 1) - t.a / (t.rb2 * t.rb2) * 2 * t.y3b) + t.y2 * t.cosB / t.rb / t.W7 * (1 - 2 * t.nu - t.W1 / t.W7 *
				t.W2 - t.a * t.y3b / t.rb2) - 1.0 / 2.0 * t.y2 * t.W8 * t.cosB / (t.rb * t.rb * t.rb) / t.W7 * (1 - 2 *
				t.nu - t.W1 / t.W7 * t.W2 - t.a * t.y3b / t.rb2) * 2 * t.y3b - t.y2 * t.W8 * t.cosB / t.rb / (t.W7 *
				t.W7) * (1 - 2 * t.nu - t.W1 / t.W7 * t.W2 - t.a * t.y3b / t.rb2) * t.W3 + t.y2 * t.W8 * t.cosB / t.rb /
				t.W7 * (-(t.cosB * t.y3b / t.rb + 1) / t.W7 * t.W2 + t.W1 / (t.W7 * t.W7) * t.W2 * t.W3 + 1.0 / 2.0 *
				t.W1 / t.W7 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b - t.a / t.rb2 + t.a * t.y3b / (t.rb2 * t.rb2) * 2 *
				t.y3b)) / PI / (1 - t.nu)) + t.b2 * (1.0 / 4.0 * ((-2 + 2 * t.nu) * t.N1 * t.cotB * ((t.y3b / t.rb + 1)
				/ t.W6 - t.cosB * t.W3 / t.W7) + (2 - 2 * t.nu) * t.y1 / (t.W6 * t.W6) * t.W5 * (t.y3b / t.rb + 1) + 1.0
				/ 2.0 * (2 - 2 * t.nu) * t.y1 / t.W6 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b + (2 - 2 * t.nu) * t.sinB
				/ t.W7 * t.W2 - (2 - 2 * t.nu) * t.z1b / (t.W7 * t.W7) * t.W2 * t.W3 - 1.0 / 2.0 * (2 - 2 * t.nu) *
				t.z1b / t.W7 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b + 1 / t.rb * (t.N1 * t.cotB - 2 * t.nu * t.y1 /
				t.W6 - t.a * t.y1 / t.rb2) - 1.0 / 2.0 * t.W8 / (t.rb * t.rb * t.rb) * (t.N1 * t.cotB - 2 * t.nu * t.y1
				/ t.W6 - t.a * t.y1 / t.rb2) * 2 * t.y3b + t.W8 / t.rb * (2 * t.nu * t.y1 / (t.W6 * t.W6) * (t.y3b /
				t.rb + 1) + t.a * t.y1 / (t.rb2 * t.rb2) * 2 * t.y3b) - 1 / t.W7 * (t.cosB * t.sinB + t.W1 * t.cotB /
				t.rb * ((2 - 2 * t.nu) * t.cosB - t.W1 / t.W7) + t.a / t.rb * (t.sinB - t.y3b * t.z1b / t.rb2 - t.z1b *
				t.W1 / t.rb / t.W7)) + t.W8 / (t.W7 * t.W7) * (t.cosB * t.sinB + t.W1 * t.cotB / t.rb * ((2 - 2 * t.nu)
				* t.cosB - t.W1 / t.W7) + t.a / t.rb * (t.sinB - t.y3b * t.z1b / t.rb2 - t.z1b * t.W1 / t.rb / t.W7)) *
				t.W3 - t.W8 / t.W7 * ((t.cosB * t.y3b / t.rb + 1) * t.cotB / t.rb * ((2 - 2 * t.nu) * t.cosB - t.W1 /
				t.W7) - 1.0 / 2.0 * t.W1 * t.cotB / (t.rb * t.rb * t.rb) * ((2 - 2 * t.nu) * t.cosB - t.W1 / t.W7) * 2 *
				t.y3b + t.W1 * t.cotB / t.rb * (-(t.cosB * t.y3b / t.rb + 1) / t.W7 + t.W1 / (t.W7 * t.W7) * t.W3) - 1.0
				/ 2.0 * t.a / (t.rb * t.rb * t.rb) * (t.sinB - t.y3b * t.z1b / t.rb2 - t.z1b * t.W1 / t.rb / t.W7) * 2 *
				t.y3b + t.a / t.rb * (-t.z1b / t.rb2 - t.y3b * t.sinB / t.rb2 + t.y3b * t.z1b / (t.rb2 * t.rb2) * 2 *
				t.y3b - t.sinB * t.W1 / t.rb / t.W7 - t.z1b * (t.cosB * t.y3b / t.rb + 1) / t.rb / t.W7 + 1.0 / 2.0 *
				t.z1b * t.W1 / (t.rb * t.rb * t.rb) / t.W7 * 2 * t.y3b + t.z1b * t.W1 / t.rb / (t.W7 * t.W7) * t.W3))) /
				PI / (1 - t.nu)) + t.b3 * (1.0 / 4.0 * ((2 - 2 * t.nu) * t.rFib_ry3 - (2 - 2 * t.nu) * t.y2 * t.sinB /
				(t.W7 * t.W7) * t.W2 * t.W3 - 1.0 / 2.0 * (2 - 2 * t.nu) * t.y2 * t.sinB / t.W7 * t.a / (t.rb * t.rb *
				t.rb) * 2 * t.y3b + t.y2 * t.sinB / t.rb / t.W7 * (1 + t.W1 / t.W7 * t.W2 + t.a * t.y3b / t.rb2) - 1.0 /
				2.0 * t.y2 * t.W8 * t.sinB / (t.rb * t.rb * t.rb) / t.W7 * (1 + t.W1 / t.W7 * t.W2 + t.a * t.y3b /
				t.rb2) * 2 * t.y3b - t.y2 * t.W8 * t.sinB / t.rb / (t.W7 * t.W7) * (1 + t.W1 / t.W7 * t.W2 + t.a * t.y3b
				/ t.rb2) * t.W3 + t.y2 * t.W8 * t.sinB / t.rb / t.W7 * ((t.cosB * t.y3b / t.rb + 1) / t.W7 * t.W2 - t.W1
				/ (t.W7 * t.W7) * t.W2 * t.W3 - 1.0 / 2.0 * t.W1 / t.W7 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b + t.a /
				t.rb2 - t.a * t.y3b / (t.rb2 * t.rb2) * 2 * t.y3b)) / PI / (1 - t.nu));
	}

	private static double correctionXY(FreeSurfaceTerms t) {
		return t.b1 / 2 * (1.0 / 4.0 * ((-2 + 2 * t.nu) * t.N1 * t.rFib_ry2 * (t.cotB * t.cotB) + t.N1 / t.W6 *
				((1 - t.W5) * t.cotB - t.y1 / t.W6 * t.W4) - t.N1 * (t.y2 * t.y2) / (t.W6 * t.W6) * ((1 - t.W5) * t.cotB
				- t.y1 / t.W6 * t.W4) / t.rb + t.N1 * t.y2 / t.W6 * (t.a / (t.rb * t.rb * t.rb) * t.y2 * t.cotB + t.y1 /
				(t.W6 * t.W6) * t.W4 / t.rb * t.y2 + t.y2 / t.W6 * t.a / (t.rb * t.rb * t.rb) * t.y1) + t.N1 * t.cosB *
				t.cotB / t.W7 * t.W2 - t.N1 * (t.y2 * t.y2) * t.cosB * t.cotB / (t.W7 * t.W7) * t.W2 / t.rb - t.N1 *
				(t.y2 * t.y2) * t.cosB * t.cotB / t.W7 * t.a / (t.rb * t.rb * t.rb) + t.a * t.W8 * t.cotB / (t.rb * t.rb
				* t.rb) - 3 * t.a * (t.y2 * t.y2) * t.W8 * t.cotB / (t.rb * t.rb2 * t.rb2) + t.W8 / t.rb / t.W6 * (-t.N1
				* t.cotB + t.y1 / t.W6 * t.W5 + t.a * t.y1 / t.rb2) - (t.y2 * t.y2) * t.W8 / (t.rb * t.rb * t.rb) / t.W6
				* (-t.N1 * t.cotB + t.y1 / t.W6 * t.W5 + t.a * t.y1 / t.rb2) - (t.y2 * t.y2) * t.W8 / t.rb2 / (t.W6 *
				t.W6) * (-t.N1 * t.cotB + t.y1 / t.W6 * t.W5 + t.a * t.y1 / t.rb2) + t.y2 * t.W8 / t.rb / t.W6 * (-t.y1
				/ (t.W6 * t.W6) * t.W5 / t.rb * t.y2 - t.y2 / t.W6 * t.a / (t.rb * t.rb * t.rb) * t.y1 - 2 * t.a * t.y1
				/ (t.rb2 * t.rb2) * t.y2) + t.W8 / t.rb / t.W7 * (t.cosB / t.W7 * (t.W1 * (t.N1 * t.cosB - t.a / t.rb) *
				t.cotB + (2 - 2 * t.nu) * (t.rb * t.sinB - t.y1) * t.cosB) - t.a * t.y3b * t.cosB * t.cotB / t.rb2) -
				(t.y2 * t.y2) * t.W8 / (t.rb * t.rb * t.rb) / t.W7 * (t.cosB / t.W7 * (t.W1 * (t.N1 * t.cosB - t.a /
				t.rb) * t.cotB + (2 - 2 * t.nu) * (t.rb * t.sinB - t.y1) * t.cosB) - t.a * t.y3b * t.cosB * t.cotB /
				t.rb2) - (t.y2 * t.y2) * t.W8 / t.rb2 / (t.W7 * t.W7) * (t.cosB / t.W7 * (t.W1 * (t.N1 * t.cosB - t.a /
				t.rb) * t.cotB + (2 - 2 * t.nu) * (t.rb * t.sinB - t.y1) * t.cosB) - t.a * t.y3b * t.cosB * t.cotB /
				t.rb2) + t.y2 * t.W8 / t.rb / t.W7 * (-t.cosB / (t.W7 * t.W7) * (t.W1 * (t.N1 * t.cosB - t.a / t.rb) *
				t.cotB + (2 - 2 * t.nu) * (t.rb * t.sinB - t.y1) * t.cosB) / t.rb * t.y2 + t.cosB / t.W7 * (1 / t.rb *
				t.cosB * t.y2 * (t.N1 * t.cosB - t.a / t.rb) * t.cotB + t.W1 * t.a / (t.rb * t.rb * t.rb) * t.y2 *
				t.cotB + (2 - 2 * t.nu) / t.rb * t.sinB * t.y2 * t.cosB) + 2 * t.a * t.y3b * t.cosB * t.cotB / (t.rb2 *
				t.rb2) * t.y2)) / PI / (1 - t.nu)) + t.b2 / 2 * (1.0 / 4.0 * (t.N1 * (((2 - 2 * t.nu) * (t.cotB *
				t.cotB) + t.nu) / t.rb * t.y2 / t.W6 - ((2 - 2 * t.nu) * (t.cotB * t.cotB) + 1) * t.cosB / t.rb * t.y2 /
				t.W7) - t.N1 / (t.W6 * t.W6) * (-t.N1 * t.y1 * t.cotB + t.nu * t.y3b - t.a + t.a * t.y1 * t.cotB / t.rb
				+ (t.y1 * t.y1) / t.W6 * t.W4) / t.rb * t.y2 + t.N1 / t.W6 * (-t.a * t.y1 * t.cotB / (t.rb * t.rb *
				t.rb) * t.y2 - (t.y1 * t.y1) / (t.W6 * t.W6) * t.W4 / t.rb * t.y2 - (t.y1 * t.y1) / t.W6 * t.a / (t.rb *
				t.rb * t.rb) * t.y2) + t.N1 * t.cotB / (t.W7 * t.W7) * (t.z1b * t.cosB - t.a * (t.rb * t.sinB - t.y1) /
				t.rb / t.cosB) / t.rb * t.y2 - t.N1 * t.cotB / t.W7 * (-t.a / t.rb2 * t.sinB * t.y2 / t.cosB + t.a *
				(t.rb * t.sinB - t.y1) / (t.rb * t.rb * t.rb) / t.cosB * t.y2) + 3 * t.a * t.y2 * t.W8 * t.cotB / (t.rb
				* t.rb2 * t.rb2) * t.y1 - t.W8 / (t.W6 * t.W6) * (2 * t.nu + 1 / t.rb * (t.N1 * t.y1 * t.cotB + t.a) -
				(t.y1 * t.y1) / t.rb / t.W6 * t.W5 - t.a * (t.y1 * t.y1) / (t.rb * t.rb * t.rb)) / t.rb * t.y2 + t.W8 /
				t.W6 * (-1 / (t.rb * t.rb * t.rb) * (t.N1 * t.y1 * t.cotB + t.a) * t.y2 + (t.y1 * t.y1) / (t.rb * t.rb2)
				/ t.W6 * t.W5 * t.y2 + (t.y1 * t.y1) / t.rb2 / (t.W6 * t.W6) * t.W5 * t.y2 + (t.y1 * t.y1) / (t.rb2 *
				t.rb2) / t.W6 * t.a * t.y2 + 3 * t.a * (t.y1 * t.y1) / (t.rb * t.rb2 * t.rb2) * t.y2) - t.W8 * t.cotB /
				(t.W7 * t.W7) * (-t.cosB * t.sinB + t.a * t.y1 * t.y3b / (t.rb * t.rb * t.rb) / t.cosB + (t.rb * t.sinB
				- t.y1) / t.rb * ((2 - 2 * t.nu) * t.cosB - t.W1 / t.W7 * t.W9)) / t.rb * t.y2 + t.W8 * t.cotB / t.W7 *
				(-3 * t.a * t.y1 * t.y3b / (t.rb * t.rb2 * t.rb2) / t.cosB * t.y2 + 1 / t.rb2 * t.sinB * t.y2 * ((2 - 2
				* t.nu) * t.cosB - t.W1 / t.W7 * t.W9) - (t.rb * t.sinB - t.y1) / (t.rb * t.rb * t.rb) * ((2 - 2 * t.nu)
				* t.cosB - t.W1 / t.W7 * t.W9) * t.y2 + (t.rb * t.sinB - t.y1) / t.rb * (-1 / t.rb * t.cosB * t.y2 /
				t.W7 * t.W9 + t.W1 / (t.W7 * t.W7) * t.W9 / t.rb * t.y2 + t.W1 / t.W7 * t.a / (t.rb * t.rb * t.rb) /
				t.cosB * t.y2))) / PI / (1 - t.nu)) + t.b3 / 2 * (1.0 / 4.0 * (t.N1 * (1 / t.W6 * (1 + t.a / t.rb) -
				(t.y2 * t.y2) / (t.W6 * t.W6) * (1 + t.a / t.rb) / t.rb - (t.y2 * t.y2) / t.W6 * t.a / (t.rb * t.rb *
				t.rb) - t.cosB / t.W7 * t.W2 + (t.y2 * t.y2) * t.cosB / (t.W7 * t.W7) * t.W2 / t.rb + (t.y2 * t.y2) *
				t.cosB / t.W7 * t.a / (t.rb * t.rb * t.rb)) - t.W8 / t.rb * (t.a / t.rb2 + 1 / t.W6) + (t.y2 * t.y2) *
				t.W8 / (t.rb * t.rb * t.rb) * (t.a / t.rb2 + 1 / t.W6) - t.y2 * t.W8 / t.rb * (-2 * t.a / (t.rb2 *
				t.rb2) * t.y2 - 1 / (t.W6 * t.W6) / t.rb * t.y2) + t.W8 * t.cosB / t.rb / t.W7 * (t.W1 / t.W7 * t.W2 +
				t.a * t.y3b / t.rb2) - (t.y2 * t.y2) * t.W8 * t.cosB / (t.rb * t.rb * t.rb) / t.W7 * (t.W1 / t.W7 * t.W2
				+ t.a * t.y3b / t.rb2) - (t.y2 * t.y2) * t.W8 * t.cosB / t.rb2 / (t.W7 * t.W7) * (t.W1 / t.W7 * t.W2 +
				t.a * t.y3b / t.rb2) + t.y2 * t.W8 * t.cosB / t.rb / t.W7 * (1 / t.rb * t.cosB * t.y2 / t.W7 * t.W2 -
				t.W1 / (t.W7 * t.W7) * t.W2 / t.rb * t.y2 - t.W1 / t.W7 * t.a / (t.rb * t.rb * t.rb) * t.y2 - 2 * t.a *
				t.y3b / (t.rb2 * t.rb2) * t.y2)) / PI / (1 - t.nu)) + t.b1 / 2 * (1.0 / 4.0 * (t.N1 * (((2 - 2 * t.nu) *
				(t.cotB * t.cotB) - t.nu) / t.rb * t.y1 / t.W6 - ((2 - 2 * t.nu) * (t.cotB * t.cotB) + 1 - 2 * t.nu) *
				t.cosB * (t.y1 / t.rb - t.sinB) / t.W7) + t.N1 / (t.W6 * t.W6) * (t.y1 * t.cotB * (1 - t.W5) + t.nu *
				t.y3b - t.a + (t.y2 * t.y2) / t.W6 * t.W4) / t.rb * t.y1 - t.N1 / t.W6 * ((1 - t.W5) * t.cotB + t.a *
				(t.y1 * t.y1) * t.cotB / (t.rb * t.rb * t.rb) - (t.y2 * t.y2) / (t.W6 * t.W6) * t.W4 / t.rb * t.y1 -
				(t.y2 * t.y2) / t.W6 * t.a / (t.rb * t.rb * t.rb) * t.y1) - t.N1 * t.cosB * t.cotB / t.W7 * t.W2 + t.N1
				* t.z1b * t.cotB / (t.W7 * t.W7) * t.W2 * (t.y1 / t.rb - t.sinB) + t.N1 * t.z1b * t.cotB / t.W7 * t.a /
				(t.rb * t.rb2) * t.y1 - t.a * t.W8 * t.cotB / (t.rb * t.rb * t.rb) + 3 * t.a * (t.y1 * t.y1) * t.W8 *
				t.cotB / (t.rb * t.rb2 * t.rb2) - t.W8 / (t.W6 * t.W6) * (-2 * t.nu + 1 / t.rb * (t.N1 * t.y1 * t.cotB -
				t.a) + (t.y2 * t.y2) / t.rb / t.W6 * t.W5 + t.a * (t.y2 * t.y2) / (t.rb * t.rb * t.rb)) / t.rb * t.y1 +
				t.W8 / t.W6 * (-1 / (t.rb * t.rb * t.rb) * (t.N1 * t.y1 * t.cotB - t.a) * t.y1 + 1 / t.rb * t.N1 *
				t.cotB - (t.y2 * t.y2) / (t.rb * t.rb * t.rb) / t.W6 * t.W5 * t.y1 - (t.y2 * t.y2) / t.rb2 / (t.W6 *
				t.W6) * t.W5 * t.y1 - (t.y2 * t.y2) / (t.rb2 * t.rb2) / t.W6 * t.a * t.y1 - 3 * t.a * (t.y2 * t.y2) /
				(t.rb * t.rb2 * t.rb2) * t.y1) - t.W8 / (t.W7 * t.W7) * ((t.cosB * t.cosB) - 1 / t.rb * (t.N1 * t.z1b *
				t.cotB + t.a * t.cosB) + t.a * t.y3b * t.z1b * t.cotB / (t.rb * t.rb * t.rb) - 1 / t.rb / t.W7 * ((t.y2
				* t.y2) * (t.cosB * t.cosB) - t.a * t.z1b * t.cotB / t.rb * t.W1)) * (t.y1 / t.rb - t.sinB) + t.W8 /
				t.W7 * (1 / (t.rb * t.rb * t.rb) * (t.N1 * t.z1b * t.cotB + t.a * t.cosB) * t.y1 - 1 / t.rb * t.N1 *
				t.cosB * t.cotB + t.a * t.y3b * t.cosB * t.cotB / (t.rb * t.rb * t.rb) - 3 * t.a * t.y3b * t.z1b *
				t.cotB / (t.rb * t.rb2 * t.rb2) * t.y1 + 1 / (t.rb * t.rb * t.rb) / t.W7 * ((t.y2 * t.y2) * (t.cosB *
				t.cosB) - t.a * t.z1b * t.cotB / t.rb * t.W1) * t.y1 + 1 / t.rb / (t.W7 * t.W7) * ((t.y2 * t.y2) *
				(t.cosB * t.cosB) - t.a * t.z1b * t.cotB / t.rb * t.W1) * (t.y1 / t.rb - t.sinB) - 1 / t.rb / t.W7 *
				(-t.a * t.cosB * t.cotB / t.rb * t.W1 + t.a * t.z1b * t.cotB / (t.rb * t.rb * t.rb) * t.W1 * t.y1 - t.a
				* t.z1b * t.cotB / t.rb2 * t.cosB * t.y1))) / PI / (1 - t.nu)) + t.b2 / 2 * (1.0 / 4.0 * ((2 - 2 * t.nu)
				* t.N1 * t.rFib_ry1 * (t.cotB * t.cotB) - t.N1 * t.y2 / (t.W6 * t.W6) * ((t.W5 - 1) * t.cotB + t.y1 /
				t.W6 * t.W4) / t.rb * t.y1 + t.N1 * t.y2 / t.W6 * (-t.a / (t.rb * t.rb * t.rb) * t.y1 * t.cotB + 1 /
				t.W6 * t.W4 - (t.y1 * t.y1) / (t.W6 * t.W6) * t.W4 / t.rb - (t.y1 * t.y1) / t.W6 * t.a / (t.rb * t.rb *
				t.rb)) + t.N1 * t.y2 * t.cotB / (t.W7 * t.W7) * t.W9 * (t.y1 / t.rb - t.sinB) + t.N1 * t.y2 * t.cotB /
				t.W7 * t.a / (t.rb * t.rb * t.rb) / t.cosB * t.y1 + 3 * t.a * t.y2 * t.W8 * t.cotB / (t.rb * t.rb2 *
				t.rb2) * t.y1 - t.y2 * t.W8 / (t.rb * t.rb * t.rb) / t.W6 * (t.N1 * t.cotB - 2 * t.nu * t.y1 / t.W6 -
				t.a * t.y1 / t.rb * (1 / t.rb + 1 / t.W6)) * t.y1 - t.y2 * t.W8 / t.rb2 / (t.W6 * t.W6) * (t.N1 * t.cotB
				- 2 * t.nu * t.y1 / t.W6 - t.a * t.y1 / t.rb * (1 / t.rb + 1 / t.W6)) * t.y1 + t.y2 * t.W8 / t.rb / t.W6
				* (-2 * t.nu / t.W6 + 2 * t.nu * (t.y1 * t.y1) / (t.W6 * t.W6) / t.rb - t.a / t.rb * (1 / t.rb + 1 /
				t.W6) + t.a * (t.y1 * t.y1) / (t.rb * t.rb * t.rb) * (1 / t.rb + 1 / t.W6) - t.a * t.y1 / t.rb * (-1 /
				(t.rb * t.rb * t.rb) * t.y1 - 1 / (t.W6 * t.W6) / t.rb * t.y1)) - t.y2 * t.W8 * t.cotB / (t.rb * t.rb *
				t.rb) / t.W7 * ((-2 + 2 * t.nu) * t.cosB + t.W1 / t.W7 * t.W9 + t.a * t.y3b / t.rb2 / t.cosB) * t.y1 -
				t.y2 * t.W8 * t.cotB / t.rb / (t.W7 * t.W7) * ((-2 + 2 * t.nu) * t.cosB + t.W1 / t.W7 * t.W9 + t.a *
				t.y3b / t.rb2 / t.cosB) * (t.y1 / t.rb - t.sinB) + t.y2 * t.W8 * t.cotB / t.rb / t.W7 * (1 / t.rb *
				t.cosB * t.y1 / t.W7 * t.W9 - t.W1 / (t.W7 * t.W7) * t.W9 * (t.y1 / t.rb - t.sinB) - t.W1 / t.W7 * t.a /
				(t.rb * t.rb * t.rb) / t.cosB * t.y1 - 2 * t.a * t.y3b / (t.rb2 * t.rb2) / t.cosB * t.y1)) / PI / (1 -
				t.nu)) + t.b3 / 2 * (1.0 / 4.0 * (t.N1 * (-t.sinB * (t.y1 / t.rb - t.sinB) / t.W7 - 1 / t.W6 * (1 + t.a
				/ t.rb) + (t.y1 * t.y1) / (t.W6 * t.W6) * (1 + t.a / t.rb) / t.rb + (t.y1 * t.y1) / t.W6 * t.a / (t.rb *
				t.rb * t.rb) + t.cosB / t.W7 * t.W2 - t.z1b / (t.W7 * t.W7) * t.W2 * (t.y1 / t.rb - t.sinB) - t.z1b /
				t.W7 * t.a / (t.rb * t.rb * t.rb) * t.y1) + t.W8 / t.rb * (t.a / t.rb2 + 1 / t.W6) - (t.y1 * t.y1) *
				t.W8 / (t.rb * t.rb * t.rb) * (t.a / t.rb2 + 1 / t.W6) + t.y1 * t.W8 / t.rb * (-2 * t.a / (t.rb2 *
				t.rb2) * t.y1 - 1 / (t.W6 * t.W6) / t.rb * t.y1) + t.W8 / (t.W7 * t.W7) * (t.sinB * (t.cosB - t.a /
				t.rb) + t.z1b / t.rb * (1 + t.a * t.y3b / t.rb2) - 1 / t.rb / t.W7 * ((t.y2 * t.y2) * t.cosB * t.sinB -
				t.a * t.z1b / t.rb * t.W1)) * (t.y1 / t.rb - t.sinB) - t.W8 / t.W7 * (t.sinB * t.a / (t.rb * t.rb *
				t.rb) * t.y1 + t.cosB / t.rb * (1 + t.a * t.y3b / t.rb2) - t.z1b / (t.rb * t.rb * t.rb) * (1 + t.a *
				t.y3b / t.rb2) * t.y1 - 2 * t.z1b / (t.rb * t.rb2 * t.rb2) * t.a * t.y3b * t.y1 + 1 / (t.rb * t.rb *
				t.rb) / t.W7 * ((t.y2 * t.y2) * t.cosB * t.sinB - t.a * t.z1b / t.rb * t.W1) * t.y1 + 1 / t.rb / (t.W7 *
				t.W7) * ((t.y2 * t.y2) * t.cosB * t.sinB - t.a * t.z1b / t.rb * t.W1) * (t.y1 / t.rb - t.sinB) - 1 /
				t.rb / t.W7 * (-t.a * t.cosB / t.rb * t.W1 + t.a * t.z1b / (t.rb * t.rb * t.rb) * t.W1 * t.y1 - t.a *
				t.z1b / t.rb2 * t.cosB * t.y1))) / PI / (1 - t.nu));
	}

	private static double correctionXZ(FreeSurfaceTerms t) {
		return t.b1 / 2 * (1.0 / 4.0 * ((-2 + 2 * t.nu) * t.N1 * t.rFib_ry3 * (t.cotB * t.cotB) - t.N1 * t.y2 /
				(t.W6 * t.W6) * ((1 - t.W5) * t.cotB - t.y1 / t.W6 * t.W4) * (t.y3b / t.rb + 1) + t.N1 * t.y2 / t.W6 *
				(1.0 / 2.0 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b * t.cotB + t.y1 / (t.W6 * t.W6) * t.W4 * (t.y3b /
				t.rb + 1) + 1.0 / 2.0 * t.y1 / t.W6 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b) - t.N1 * t.y2 * t.cosB *
				t.cotB / (t.W7 * t.W7) * t.W2 * t.W3 - 1.0 / 2.0 * t.N1 * t.y2 * t.cosB * t.cotB / t.W7 * t.a / (t.rb *
				t.rb * t.rb) * 2 * t.y3b + t.a / (t.rb * t.rb * t.rb) * t.y2 * t.cotB - 3.0 / 2.0 * t.a * t.y2 * t.W8 *
				t.cotB / (t.rb * t.rb2 * t.rb2) * 2 * t.y3b + t.y2 / t.rb / t.W6 * (-t.N1 * t.cotB + t.y1 / t.W6 * t.W5
				+ t.a * t.y1 / t.rb2) - 1.0 / 2.0 * t.y2 * t.W8 / (t.rb * t.rb * t.rb) / t.W6 * (-t.N1 * t.cotB + t.y1 /
				t.W6 * t.W5 + t.a * t.y1 / t.rb2) * 2 * t.y3b - t.y2 * t.W8 / t.rb / (t.W6 * t.W6) * (-t.N1 * t.cotB +
				t.y1 / t.W6 * t.W5 + t.a * t.y1 / t.rb2) * (t.y3b / t.rb + 1) + t.y2 * t.W8 / t.rb / t.W6 * (-t.y1 /
				(t.W6 * t.W6) * t.W5 * (t.y3b / t.rb + 1) - 1.0 / 2.0 * t.y1 / t.W6 * t.a / (t.rb * t.rb * t.rb) * 2 *
				t.y3b - t.a * t.y1 / (t.rb2 * t.rb2) * 2 * t.y3b) + t.y2 / t.rb / t.W7 * (t.cosB / t.W7 * (t.W1 * (t.N1
				* t.cosB - t.a / t.rb) * t.cotB + (2 - 2 * t.nu) * (t.rb * t.sinB - t.y1) * t.cosB) - t.a * t.y3b *
				t.cosB * t.cotB / t.rb2) - 1.0 / 2.0 * t.y2 * t.W8 / (t.rb * t.rb * t.rb) / t.W7 * (t.cosB / t.W7 *
				(t.W1 * (t.N1 * t.cosB - t.a / t.rb) * t.cotB + (2 - 2 * t.nu) * (t.rb * t.sinB - t.y1) * t.cosB) - t.a
				* t.y3b * t.cosB * t.cotB / t.rb2) * 2 * t.y3b - t.y2 * t.W8 / t.rb / (t.W7 * t.W7) * (t.cosB / t.W7 *
				(t.W1 * (t.N1 * t.cosB - t.a / t.rb) * t.cotB + (2 - 2 * t.nu) * (t.rb * t.sinB - t.y1) * t.cosB) - t.a
				* t.y3b * t.cosB * t.cotB / t.rb2) * t.W3 + t.y2 * t.W8 / t.rb / t.W7 * (-t.cosB / (t.W7 * t.W7) * (t.W1
				* (t.N1 * t.cosB - t.a / t.rb) * t.cotB + (2 - 2 * t.nu) * (t.rb * t.sinB - t.y1) * t.cosB) * t.W3 +
				t.cosB / t.W7 * ((t.cosB * t.y3b / t.rb + 1) * (t.N1 * t.cosB - t.a / t.rb) * t.cotB + 1.0 / 2.0 * t.W1
				* t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b * t.cotB + 1.0 / 2.0 * (2 - 2 * t.nu) / t.rb * t.sinB * 2 *
				t.y3b * t.cosB) - t.a * t.cosB * t.cotB / t.rb2 + t.a * t.y3b * t.cosB * t.cotB / (t.rb2 * t.rb2) * 2 *
				t.y3b)) / PI / (1 - t.nu)) + t.b2 / 2 * (1.0 / 4.0 * (t.N1 * (((2 - 2 * t.nu) * (t.cotB * t.cotB) +
				t.nu) * (t.y3b / t.rb + 1) / t.W6 - ((2 - 2 * t.nu) * (t.cotB * t.cotB) + 1) * t.cosB * t.W3 / t.W7) -
				t.N1 / (t.W6 * t.W6) * (-t.N1 * t.y1 * t.cotB + t.nu * t.y3b - t.a + t.a * t.y1 * t.cotB / t.rb + (t.y1
				* t.y1) / t.W6 * t.W4) * (t.y3b / t.rb + 1) + t.N1 / t.W6 * (t.nu - 1.0 / 2.0 * t.a * t.y1 * t.cotB /
				(t.rb * t.rb * t.rb) * 2 * t.y3b - (t.y1 * t.y1) / (t.W6 * t.W6) * t.W4 * (t.y3b / t.rb + 1) - 1.0 / 2.0
				* (t.y1 * t.y1) / t.W6 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b) + t.N1 * t.cotB / (t.W7 * t.W7) *
				(t.z1b * t.cosB - t.a * (t.rb * t.sinB - t.y1) / t.rb / t.cosB) * t.W3 - t.N1 * t.cotB / t.W7 * (t.cosB
				* t.sinB - 1.0 / 2.0 * t.a / t.rb2 * t.sinB * 2 * t.y3b / t.cosB + 1.0 / 2.0 * t.a * (t.rb * t.sinB -
				t.y1) / (t.rb * t.rb * t.rb) / t.cosB * 2 * t.y3b) - t.a / (t.rb * t.rb * t.rb) * t.y1 * t.cotB + 3.0 /
				2.0 * t.a * t.y1 * t.W8 * t.cotB / (t.rb * t.rb2 * t.rb2) * 2 * t.y3b + 1 / t.W6 * (2 * t.nu + 1 / t.rb
				* (t.N1 * t.y1 * t.cotB + t.a) - (t.y1 * t.y1) / t.rb / t.W6 * t.W5 - t.a * (t.y1 * t.y1) / (t.rb * t.rb
				* t.rb)) - t.W8 / (t.W6 * t.W6) * (2 * t.nu + 1 / t.rb * (t.N1 * t.y1 * t.cotB + t.a) - (t.y1 * t.y1) /
				t.rb / t.W6 * t.W5 - t.a * (t.y1 * t.y1) / (t.rb * t.rb * t.rb)) * (t.y3b / t.rb + 1) + t.W8 / t.W6 *
				(-1.0 / 2.0 / (t.rb * t.rb * t.rb) * (t.N1 * t.y1 * t.cotB + t.a) * 2 * t.y3b + 1.0 / 2.0 * (t.y1 *
				t.y1) / (t.rb * t.rb * t.rb) / t.W6 * t.W5 * 2 * t.y3b + (t.y1 * t.y1) / t.rb / (t.W6 * t.W6) * t.W5 *
				(t.y3b / t.rb + 1) + 1.0 / 2.0 * (t.y1 * t.y1) / (t.rb2 * t.rb2) / t.W6 * t.a * 2 * t.y3b + 3.0 / 2.0 *
				t.a * (t.y1 * t.y1) / (t.rb * t.rb2 * t.rb2) * 2 * t.y3b) + t.cotB / t.W7 * (-t.cosB * t.sinB + t.a *
				t.y1 * t.y3b / (t.rb * t.rb * t.rb) / t.cosB + (t.rb * t.sinB - t.y1) / t.rb * ((2 - 2 * t.nu) * t.cosB
				- t.W1 / t.W7 * t.W9)) - t.W8 * t.cotB / (t.W7 * t.W7) * (-t.cosB * t.sinB + t.a * t.y1 * t.y3b / (t.rb
				* t.rb2) / t.cosB + (t.rb * t.sinB - t.y1) / t.rb * ((2 - 2 * t.nu) * t.cosB - t.W1 / t.W7 * t.W9)) *
				t.W3 + t.W8 * t.cotB / t.W7 * (t.a / (t.rb * t.rb * t.rb) / t.cosB * t.y1 - 3.0 / 2.0 * t.a * t.y1 *
				t.y3b / (t.rb * t.rb2 * t.rb2) / t.cosB * 2 * t.y3b + 1.0 / 2.0 / t.rb2 * t.sinB * 2 * t.y3b * ((2 - 2 *
				t.nu) * t.cosB - t.W1 / t.W7 * t.W9) - 1.0 / 2.0 * (t.rb * t.sinB - t.y1) / (t.rb * t.rb2) * ((2 - 2 *
				t.nu) * t.cosB - t.W1 / t.W7 * t.W9) * 2 * t.y3b + (t.rb * t.sinB - t.y1) / t.rb * (-(t.cosB * t.y3b /
				t.rb + 1) / t.W7 * t.W9 + t.W1 / (t.W7 * t.W7) * t.W9 * t.W3 + 1.0 / 2.0 * t.W1 / t.W7 * t.a / (t.rb *
				t.rb * t.rb) / t.cosB * 2 * t.y3b))) / PI / (1 - t.nu)) + t.b3 / 2 * (1.0 / 4.0 * (t.N1 * (-t.y2 / (t.W6
				* t.W6) * (1 + t.a / t.rb) * (t.y3b / t.rb + 1) - 1.0 / 2.0 * t.y2 / t.W6 * t.a / (t.rb * t.rb * t.rb) *
				2 * t.y3b + t.y2 * t.cosB / (t.W7 * t.W7) * t.W2 * t.W3 + 1.0 / 2.0 * t.y2 * t.cosB / t.W7 * t.a / (t.rb
				* t.rb * t.rb) * 2 * t.y3b) - t.y2 / t.rb * (t.a / t.rb2 + 1 / t.W6) + 1.0 / 2.0 * t.y2 * t.W8 / (t.rb *
				t.rb * t.rb) * (t.a / t.rb2 + 1 / t.W6) * 2 * t.y3b - t.y2 * t.W8 / t.rb * (-t.a / (t.rb2 * t.rb2) * 2 *
				t.y3b - 1 / (t.W6 * t.W6) * (t.y3b / t.rb + 1)) + t.y2 * t.cosB / t.rb / t.W7 * (t.W1 / t.W7 * t.W2 +
				t.a * t.y3b / t.rb2) - 1.0 / 2.0 * t.y2 * t.W8 * t.cosB / (t.rb * t.rb * t.rb) / t.W7 * (t.W1 / t.W7 *
				t.W2 + t.a * t.y3b / t.rb2) * 2 * t.y3b - t.y2 * t.W8 * t.cosB / t.rb / (t.W7 * t.W7) * (t.W1 / t.W7 *
				t.W2 + t.a * t.y3b / t.rb2) * t.W3 + t.y2 * t.W8 * t.cosB / t.rb / t.W7 * ((t.cosB * t.y3b / t.rb + 1) /
				t.W7 * t.W2 - t.W1 / (t.W7 * t.W7) * t.W2 * t.W3 - 1.0 / 2.0 * t.W1 / t.W7 * t.a / (t.rb * t.rb * t.rb)
				* 2 * t.y3b + t.a / t.rb2 - t.a * t.y3b / (t.rb2 * t.rb2) * 2 * t.y3b)) / PI / (1 - t.nu)) + t.b1 / 2.0
				* (1.0 / 4.0 * ((2 - 2 * t.nu) * (t.N1 * t.rFib_ry1 * t.cotB - t.y1 / (t.W6 * t.W6) * t.W5 / t.rb * t.y2
				- t.y2 / t.W6 * t.a / (t.rb * t.rb * t.rb) * t.y1 + t.y2 * t.cosB / (t.W7 * t.W7) * t.W2 * (t.y1 / t.rb
				- t.sinB) + t.y2 * t.cosB / t.W7 * t.a / (t.rb * t.rb2) * t.y1) - t.y2 * t.W8 / (t.rb * t.rb * t.rb) *
				(2 * t.nu / t.W6 + t.a / t.rb2) * t.y1 + t.y2 * t.W8 / t.rb * (-2 * t.nu / (t.W6 * t.W6) / t.rb * t.y1 -
				2 * t.a / (t.rb2 * t.rb2) * t.y1) - t.y2 * t.W8 * t.cosB / (t.rb * t.rb * t.rb) / t.W7 * (1 - 2 * t.nu -
				t.W1 / t.W7 * t.W2 - t.a * t.y3b / t.rb2) * t.y1 - t.y2 * t.W8 * t.cosB / t.rb / (t.W7 * t.W7) * (1 - 2
				* t.nu - t.W1 / t.W7 * t.W2 - t.a * t.y3b / t.rb2) * (t.y1 / t.rb - t.sinB) + t.y2 * t.W8 * t.cosB /
				t.rb / t.W7 * (-1 / t.rb * t.cosB * t.y1 / t.W7 * t.W2 + t.W1 / (t.W7 * t.W7) * t.W2 * (t.y1 / t.rb -
				t.sinB) + t.W1 / t.W7 * t.a / (t.rb * t.rb * t.rb) * t.y1 + 2 * t.a * t.y3b / (t.rb2 * t.rb2) * t.y1)) /
				PI / (1 - t.nu)) + t.b2 / 2 * (1.0 / 4.0 * ((-2 + 2 * t.nu) * t.N1 * t.cotB * (1 / t.rb * t.y1 / t.W6 -
				t.cosB * (t.y1 / t.rb - t.sinB) / t.W7) - (2 - 2 * t.nu) / t.W6 * t.W5 + (2 - 2 * t.nu) * (t.y1 * t.y1)
				/ (t.W6 * t.W6) * t.W5 / t.rb + (2 - 2 * t.nu) * (t.y1 * t.y1) / t.W6 * t.a / (t.rb * t.rb * t.rb) + (2
				- 2 * t.nu) * t.cosB / t.W7 * t.W2 - (2 - 2 * t.nu) * t.z1b / (t.W7 * t.W7) * t.W2 * (t.y1 / t.rb -
				t.sinB) - (2 - 2 * t.nu) * t.z1b / t.W7 * t.a / (t.rb * t.rb * t.rb) * t.y1 - t.W8 / (t.rb * t.rb *
				t.rb) * (t.N1 * t.cotB - 2 * t.nu * t.y1 / t.W6 - t.a * t.y1 / t.rb2) * t.y1 + t.W8 / t.rb * (-2 * t.nu
				/ t.W6 + 2 * t.nu * (t.y1 * t.y1) / (t.W6 * t.W6) / t.rb - t.a / t.rb2 + 2 * t.a * (t.y1 * t.y1) /
				(t.rb2 * t.rb2)) + t.W8 / (t.W7 * t.W7) * (t.cosB * t.sinB + t.W1 * t.cotB / t.rb * ((2 - 2 * t.nu) *
				t.cosB - t.W1 / t.W7) + t.a / t.rb * (t.sinB - t.y3b * t.z1b / t.rb2 - t.z1b * t.W1 / t.rb / t.W7)) *
				(t.y1 / t.rb - t.sinB) - t.W8 / t.W7 * (1 / t.rb2 * t.cosB * t.y1 * t.cotB * ((2 - 2 * t.nu) * t.cosB -
				t.W1 / t.W7) - t.W1 * t.cotB / (t.rb * t.rb * t.rb) * ((2 - 2 * t.nu) * t.cosB - t.W1 / t.W7) * t.y1 +
				t.W1 * t.cotB / t.rb * (-1 / t.rb * t.cosB * t.y1 / t.W7 + t.W1 / (t.W7 * t.W7) * (t.y1 / t.rb -
				t.sinB)) - t.a / (t.rb * t.rb * t.rb) * (t.sinB - t.y3b * t.z1b / t.rb2 - t.z1b * t.W1 / t.rb / t.W7) *
				t.y1 + t.a / t.rb * (-t.y3b * t.cosB / t.rb2 + 2 * t.y3b * t.z1b / (t.rb2 * t.rb2) * t.y1 - t.cosB *
				t.W1 / t.rb / t.W7 - t.z1b / t.rb2 * t.cosB * t.y1 / t.W7 + t.z1b * t.W1 / (t.rb * t.rb * t.rb) / t.W7 *
				t.y1 + t.z1b * t.W1 / t.rb / (t.W7 * t.W7) * (t.y1 / t.rb - t.sinB)))) / PI / (1 - t.nu)) + t.b3 / 2 *
				(1.0 / 4.0 * ((2 - 2 * t.nu) * t.rFib_ry1 - (2 - 2 * t.nu) * t.y2 * t.sinB / (t.W7 * t.W7) * t.W2 *
				(t.y1 / t.rb - t.sinB) - (2 - 2 * t.nu) * t.y2 * t.sinB / t.W7 * t.a / (t.rb * t.rb * t.rb) * t.y1 -
				t.y2 * t.W8 * t.sinB / (t.rb * t.rb * t.rb) / t.W7 * (1 + t.W1 / t.W7 * t.W2 + t.a * t.y3b / t.rb2) *
				t.y1 - t.y2 * t.W8 * t.sinB / t.rb / (t.W7 * t.W7) * (1 + t.W1 / t.W7 * t.W2 + t.a * t.y3b / t.rb2) *
				(t.y1 / t.rb - t.sinB) + t.y2 * t.W8 * t.sinB / t.rb / t.W7 * (1 / t.rb * t.cosB * t.y1 / t.W7 * t.W2 -
				t.W1 / (t.W7 * t.W7) * t.W2 * (t.y1 / t.rb - t.sinB) - t.W1 / t.W7 * t.a / (t.rb * t.rb * t.rb) * t.y1 -
				2 * t.a * t.y3b / (t.rb2 * t.rb2) * t.y1)) / PI / (1 - t.nu));
	}

	private static double correctionYZ(FreeSurfaceTerms t) {
		return t.b1 / 2 * (1.0 / 4.0 * (t.N1 * (((2 - 2 * t.nu) * (t.cotB * t.cotB) - t.nu) * (t.y3b / t.rb + 1)
				/ t.W6 - ((2 - 2 * t.nu) * (t.cotB * t.cotB) + 1 - 2 * t.nu) * t.cosB * t.W3 / t.W7) + t.N1 / (t.W6 *
				t.W6) * (t.y1 * t.cotB * (1 - t.W5) + t.nu * t.y3b - t.a + (t.y2 * t.y2) / t.W6 * t.W4) * (t.y3b / t.rb
				+ 1) - t.N1 / t.W6 * (1.0 / 2.0 * t.a * t.y1 * t.cotB / (t.rb * t.rb * t.rb) * 2 * t.y3b + t.nu - (t.y2
				* t.y2) / (t.W6 * t.W6) * t.W4 * (t.y3b / t.rb + 1) - 1.0 / 2.0 * (t.y2 * t.y2) / t.W6 * t.a / (t.rb *
				t.rb * t.rb) * 2 * t.y3b) - t.N1 * t.sinB * t.cotB / t.W7 * t.W2 + t.N1 * t.z1b * t.cotB / (t.W7 * t.W7)
				* t.W2 * t.W3 + 1.0 / 2.0 * t.N1 * t.z1b * t.cotB / t.W7 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b - t.a
				/ (t.rb * t.rb * t.rb) * t.y1 * t.cotB + 3.0 / 2.0 * t.a * t.y1 * t.W8 * t.cotB / (t.rb * t.rb2 * t.rb2)
				* 2 * t.y3b + 1 / t.W6 * (-2 * t.nu + 1 / t.rb * (t.N1 * t.y1 * t.cotB - t.a) + (t.y2 * t.y2) / t.rb /
				t.W6 * t.W5 + t.a * (t.y2 * t.y2) / (t.rb * t.rb * t.rb)) - t.W8 / (t.W6 * t.W6) * (-2 * t.nu + 1 / t.rb
				* (t.N1 * t.y1 * t.cotB - t.a) + (t.y2 * t.y2) / t.rb / t.W6 * t.W5 + t.a * (t.y2 * t.y2) / (t.rb * t.rb
				* t.rb)) * (t.y3b / t.rb + 1) + t.W8 / t.W6 * (-1.0 / 2.0 / (t.rb * t.rb * t.rb) * (t.N1 * t.y1 * t.cotB
				- t.a) * 2 * t.y3b - 1.0 / 2.0 * (t.y2 * t.y2) / (t.rb * t.rb * t.rb) / t.W6 * t.W5 * 2 * t.y3b - (t.y2
				* t.y2) / t.rb / (t.W6 * t.W6) * t.W5 * (t.y3b / t.rb + 1) - 1.0 / 2.0 * (t.y2 * t.y2) / (t.rb2 * t.rb2)
				/ t.W6 * t.a * 2 * t.y3b - 3.0 / 2.0 * t.a * (t.y2 * t.y2) / (t.rb * t.rb2 * t.rb2) * 2 * t.y3b) + 1 /
				t.W7 * ((t.cosB * t.cosB) - 1 / t.rb * (t.N1 * t.z1b * t.cotB + t.a * t.cosB) + t.a * t.y3b * t.z1b *
				t.cotB / (t.rb * t.rb2) - 1 / t.rb / t.W7 * ((t.y2 * t.y2) * (t.cosB * t.cosB) - t.a * t.z1b * t.cotB /
				t.rb * t.W1)) - t.W8 / (t.W7 * t.W7) * ((t.cosB * t.cosB) - 1 / t.rb * (t.N1 * t.z1b * t.cotB + t.a *
				t.cosB) + t.a * t.y3b * t.z1b * t.cotB / (t.rb * t.rb * t.rb) - 1 / t.rb / t.W7 * ((t.y2 * t.y2) *
				(t.cosB * t.cosB) - t.a * t.z1b * t.cotB / t.rb * t.W1)) * t.W3 + t.W8 / t.W7 * (1.0 / 2.0 / (t.rb *
				t.rb * t.rb) * (t.N1 * t.z1b * t.cotB + t.a * t.cosB) * 2 * t.y3b - 1 / t.rb * t.N1 * t.sinB * t.cotB +
				t.a * t.z1b * t.cotB / (t.rb * t.rb * t.rb) + t.a * t.y3b * t.sinB * t.cotB / (t.rb * t.rb * t.rb) - 3.0
				/ 2.0 * t.a * t.y3b * t.z1b * t.cotB / (t.rb * t.rb2 * t.rb2) * 2 * t.y3b + 1.0 / 2.0 / (t.rb * t.rb2) /
				t.W7 * ((t.y2 * t.y2) * (t.cosB * t.cosB) - t.a * t.z1b * t.cotB / t.rb * t.W1) * 2 * t.y3b + 1 / t.rb /
				(t.W7 * t.W7) * ((t.y2 * t.y2) * (t.cosB * t.cosB) - t.a * t.z1b * t.cotB / t.rb * t.W1) * t.W3 - 1 /
				t.rb / t.W7 * (-t.a * t.sinB * t.cotB / t.rb * t.W1 + 1.0 / 2.0 * t.a * t.z1b * t.cotB / (t.rb * t.rb *
				t.rb) * t.W1 * 2 * t.y3b - t.a * t.z1b * t.cotB / t.rb * (t.cosB * t.y3b / t.rb + 1)))) / PI / (1 -
				t.nu)) + t.b2 / 2 * (1.0 / 4.0 * ((2 - 2 * t.nu) * t.N1 * t.rFib_ry3 * (t.cotB * t.cotB) - t.N1 * t.y2 /
				(t.W6 * t.W6) * ((t.W5 - 1) * t.cotB + t.y1 / t.W6 * t.W4) * (t.y3b / t.rb + 1) + t.N1 * t.y2 / t.W6 *
				(-1.0 / 2.0 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b * t.cotB - t.y1 / (t.W6 * t.W6) * t.W4 * (t.y3b /
				t.rb + 1) - 1.0 / 2.0 * t.y1 / t.W6 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b) + t.N1 * t.y2 * t.cotB /
				(t.W7 * t.W7) * t.W9 * t.W3 + 1.0 / 2.0 * t.N1 * t.y2 * t.cotB / t.W7 * t.a / (t.rb * t.rb * t.rb) /
				t.cosB * 2 * t.y3b - t.a / (t.rb * t.rb * t.rb) * t.y2 * t.cotB + 3.0 / 2.0 * t.a * t.y2 * t.W8 * t.cotB
				/ (t.rb * t.rb2 * t.rb2) * 2 * t.y3b + t.y2 / t.rb / t.W6 * (t.N1 * t.cotB - 2 * t.nu * t.y1 / t.W6 -
				t.a * t.y1 / t.rb * (1 / t.rb + 1 / t.W6)) - 1.0 / 2.0 * t.y2 * t.W8 / (t.rb * t.rb * t.rb) / t.W6 *
				(t.N1 * t.cotB - 2 * t.nu * t.y1 / t.W6 - t.a * t.y1 / t.rb * (1 / t.rb + 1 / t.W6)) * 2 * t.y3b - t.y2
				* t.W8 / t.rb / (t.W6 * t.W6) * (t.N1 * t.cotB - 2 * t.nu * t.y1 / t.W6 - t.a * t.y1 / t.rb * (1 / t.rb
				+ 1 / t.W6)) * (t.y3b / t.rb + 1) + t.y2 * t.W8 / t.rb / t.W6 * (2 * t.nu * t.y1 / (t.W6 * t.W6) *
				(t.y3b / t.rb + 1) + 1.0 / 2.0 * t.a * t.y1 / (t.rb * t.rb * t.rb) * (1 / t.rb + 1 / t.W6) * 2 * t.y3b -
				t.a * t.y1 / t.rb * (-1.0 / 2.0 / (t.rb * t.rb * t.rb) * 2 * t.y3b - 1 / (t.W6 * t.W6) * (t.y3b / t.rb +
				1))) + t.y2 * t.cotB / t.rb / t.W7 * ((-2 + 2 * t.nu) * t.cosB + t.W1 / t.W7 * t.W9 + t.a * t.y3b /
				t.rb2 / t.cosB) - 1.0 / 2.0 * t.y2 * t.W8 * t.cotB / (t.rb * t.rb * t.rb) / t.W7 * ((-2 + 2 * t.nu) *
				t.cosB + t.W1 / t.W7 * t.W9 + t.a * t.y3b / t.rb2 / t.cosB) * 2 * t.y3b - t.y2 * t.W8 * t.cotB / t.rb /
				(t.W7 * t.W7) * ((-2 + 2 * t.nu) * t.cosB + t.W1 / t.W7 * t.W9 + t.a * t.y3b / t.rb2 / t.cosB) * t.W3 +
				t.y2 * t.W8 * t.cotB / t.rb / t.W7 * ((t.cosB * t.y3b / t.rb + 1) / t.W7 * t.W9 - t.W1 / (t.W7 * t.W7) *
				t.W9 * t.W3 - 1.0 / 2.0 * t.W1 / t.W7 * t.a / (t.rb * t.rb * t.rb) / t.cosB * 2 * t.y3b + t.a / t.rb2 /
				t.cosB - t.a * t.y3b / (t.rb2 * t.rb2) / t.cosB * 2 * t.y3b)) / PI / (1 - t.nu)) + t.b3 / 2 * (1.0 / 4.0
				* (t.N1 * (-t.sinB * t.W3 / t.W7 + t.y1 / (t.W6 * t.W6) * (1 + t.a / t.rb) * (t.y3b / t.rb + 1) + 1.0 /
				2.0 * t.y1 / t.W6 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b + t.sinB / t.W7 * t.W2 - t.z1b / (t.W7 *
				t.W7) * t.W2 * t.W3 - 1.0 / 2.0 * t.z1b / t.W7 * t.a / (t.rb * t.rb * t.rb) * 2 * t.y3b) + t.y1 / t.rb *
				(t.a / t.rb2 + 1 / t.W6) - 1.0 / 2.0 * t.y1 * t.W8 / (t.rb * t.rb2) * (t.a / t.rb2 + 1 / t.W6) * 2 *
				t.y3b + t.y1 * t.W8 / t.rb * (-t.a / (t.rb2 * t.rb2) * 2 * t.y3b - 1 / (t.W6 * t.W6) * (t.y3b / t.rb +
				1)) - 1 / t.W7 * (t.sinB * (t.cosB - t.a / t.rb) + t.z1b / t.rb * (1 + t.a * t.y3b / t.rb2) - 1 / t.rb /
				t.W7 * ((t.y2 * t.y2) * t.cosB * t.sinB - t.a * t.z1b / t.rb * t.W1)) + t.W8 / (t.W7 * t.W7) * (t.sinB *
				(t.cosB - t.a / t.rb) + t.z1b / t.rb * (1 + t.a * t.y3b / t.rb2) - 1 / t.rb / t.W7 * ((t.y2 * t.y2) *
				t.cosB * t.sinB - t.a * t.z1b / t.rb * t.W1)) * t.W3 - t.W8 / t.W7 * (1.0 / 2.0 * t.sinB * t.a / (t.rb *
				t.rb * t.rb) * 2 * t.y3b + t.sinB / t.rb * (1 + t.a * t.y3b / t.rb2) - 1.0 / 2.0 * t.z1b / (t.rb * t.rb
				* t.rb) * (1 + t.a * t.y3b / t.rb2) * 2 * t.y3b + t.z1b / t.rb * (t.a / t.rb2 - t.a * t.y3b / (t.rb2 *
				t.rb2) * 2 * t.y3b) + 1.0 / 2.0 / (t.rb * t.rb * t.rb) / t.W7 * ((t.y2 * t.y2) * t.cosB * t.sinB - t.a *
				t.z1b / t.rb * t.W1) * 2 * t.y3b + 1 / t.rb / (t.W7 * t.W7) * ((t.y2 * t.y2) * t.cosB * t.sinB - t.a *
				t.z1b / t.rb * t.W1) * t.W3 - 1 / t.rb / t.W7 * (-t.a * t.sinB / t.rb * t.W1 + 1.0 / 2.0 * t.a * t.z1b /
				(t.rb * t.rb * t.rb) * t.W1 * 2 * t.y3b - t.a * t.z1b / t.rb * (t.cosB * t.y3b / t.rb + 1)))) / PI / (1
				- t.nu)) + t.b1 / 2.0 * (1.0 / 4.0 * ((2 - 2 * t.nu) * (t.N1 * t.rFib_ry2 * t.cotB + 1 / t.W6 * t.W5 -
				(t.y2 * t.y2) / (t.W6 * t.W6) * t.W5 / t.rb - (t.y2 * t.y2) / t.W6 * t.a / (t.rb * t.rb * t.rb) - t.cosB
				/ t.W7 * t.W2 + (t.y2 * t.y2) * t.cosB / (t.W7 * t.W7) * t.W2 / t.rb + (t.y2 * t.y2) * t.cosB / t.W7 *
				t.a / (t.rb * t.rb * t.rb)) + t.W8 / t.rb * (2 * t.nu / t.W6 + t.a / t.rb2) - (t.y2 * t.y2) * t.W8 /
				(t.rb * t.rb * t.rb) * (2 * t.nu / t.W6 + t.a / t.rb2) + t.y2 * t.W8 / t.rb * (-2 * t.nu / (t.W6 * t.W6)
				/ t.rb * t.y2 - 2 * t.a / (t.rb2 * t.rb2) * t.y2) + t.W8 * t.cosB / t.rb / t.W7 * (1 - 2 * t.nu - t.W1 /
				t.W7 * t.W2 - t.a * t.y3b / t.rb2) - (t.y2 * t.y2) * t.W8 * t.cosB / (t.rb * t.rb * t.rb) / t.W7 * (1 -
				2 * t.nu - t.W1 / t.W7 * t.W2 - t.a * t.y3b / t.rb2) - (t.y2 * t.y2) * t.W8 * t.cosB / t.rb2 / (t.W7 *
				t.W7) * (1 - 2 * t.nu - t.W1 / t.W7 * t.W2 - t.a * t.y3b / t.rb2) + t.y2 * t.W8 * t.cosB / t.rb / t.W7 *
				(-1 / t.rb * t.cosB * t.y2 / t.W7 * t.W2 + t.W1 / (t.W7 * t.W7) * t.W2 / t.rb * t.y2 + t.W1 / t.W7 * t.a
				/ (t.rb * t.rb * t.rb) * t.y2 + 2 * t.a * t.y3b / (t.rb2 * t.rb2) * t.y2)) / PI / (1 - t.nu)) + t.b2 / 2
				* (1.0 / 4.0 * ((-2 + 2 * t.nu) * t.N1 * t.cotB * (1 / t.rb * t.y2 / t.W6 - t.cosB / t.rb * t.y2 / t.W7)
				+ (2 - 2 * t.nu) * t.y1 / (t.W6 * t.W6) * t.W5 / t.rb * t.y2 + (2 - 2 * t.nu) * t.y1 / t.W6 * t.a /
				(t.rb * t.rb * t.rb) * t.y2 - (2 - 2 * t.nu) * t.z1b / (t.W7 * t.W7) * t.W2 / t.rb * t.y2 - (2 - 2 *
				t.nu) * t.z1b / t.W7 * t.a / (t.rb * t.rb * t.rb) * t.y2 - t.W8 / (t.rb * t.rb2) * (t.N1 * t.cotB - 2 *
				t.nu * t.y1 / t.W6 - t.a * t.y1 / t.rb2) * t.y2 + t.W8 / t.rb * (2 * t.nu * t.y1 / (t.W6 * t.W6) / t.rb
				* t.y2 + 2 * t.a * t.y1 / (t.rb2 * t.rb2) * t.y2) + t.W8 / (t.W7 * t.W7) * (t.cosB * t.sinB + t.W1 *
				t.cotB / t.rb * ((2 - 2 * t.nu) * t.cosB - t.W1 / t.W7) + t.a / t.rb * (t.sinB - t.y3b * t.z1b / t.rb2 -
				t.z1b * t.W1 / t.rb / t.W7)) / t.rb * t.y2 - t.W8 / t.W7 * (1 / t.rb2 * t.cosB * t.y2 * t.cotB * ((2 - 2
				* t.nu) * t.cosB - t.W1 / t.W7) - t.W1 * t.cotB / (t.rb * t.rb * t.rb) * ((2 - 2 * t.nu) * t.cosB - t.W1
				/ t.W7) * t.y2 + t.W1 * t.cotB / t.rb * (-t.cosB / t.rb * t.y2 / t.W7 + t.W1 / (t.W7 * t.W7) / t.rb *
				t.y2) - t.a / (t.rb * t.rb * t.rb) * (t.sinB - t.y3b * t.z1b / t.rb2 - t.z1b * t.W1 / t.rb / t.W7) *
				t.y2 + t.a / t.rb * (2 * t.y3b * t.z1b / (t.rb2 * t.rb2) * t.y2 - t.z1b / t.rb2 * t.cosB * t.y2 / t.W7 +
				t.z1b * t.W1 / (t.rb * t.rb * t.rb) / t.W7 * t.y2 + t.z1b * t.W1 / t.rb2 / (t.W7 * t.W7) * t.y2))) / PI
				/ (1 - t.nu)) + t.b3 / 2 * (1.0 / 4.0 * ((2 - 2 * t.nu) * t.rFib_ry2 + (2 - 2 * t.nu) * t.sinB / t.W7 *
				t.W2 - (2 - 2 * t.nu) * (t.y2 * t.y2) * t.sinB / (t.W7 * t.W7) * t.W2 / t.rb - (2 - 2 * t.nu) * (t.y2 *
				t.y2) * t.sinB / t.W7 * t.a / (t.rb * t.rb * t.rb) + t.W8 * t.sinB / t.rb / t.W7 * (1 + t.W1 / t.W7 *
				t.W2 + t.a * t.y3b / t.rb2) - (t.y2 * t.y2) * t.W8 * t.sinB / (t.rb * t.rb * t.rb) / t.W7 * (1 + t.W1 /
				t.W7 * t.W2 + t.a * t.y3b / t.rb2) - (t.y2 * t.y2) * t.W8 * t.sinB / t.rb2 / (t.W7 * t.W7) * (1 + t.W1 /
				t.W7 * t.W2 + t.a * t.y3b / t.rb2) + t.y2 * t.W8 * t.sinB / t.rb / t.W7 * (1 / t.rb * t.cosB * t.y2 /
				t.W7 * t.W2 - t.W1 / (t.W7 * t.W7) * t.W2 / t.rb * t.y2 - t.W1 / t.W7 * t.a / (t.rb * t.rb * t.rb) *
				t.y2 - 2 * t.a * t.y3b / (t.rb2 * t.rb2) * t.y2)) / PI / (1 - t.nu));
	}

	// @formatter:on
	private static Tensor AngSetupFSC_S(Vec3 obs, Vec3 slip, Vec3 PA, Vec3 PB, double nu,
			boolean conservativePrecision) {
		Vec3 SideVec = sub3(PB, PA);
		Vec3 eZ = make3(0.0f, 0.0f, 1.0f);
		double beta = atan2(hypot(SideVec.x, SideVec.y), -SideVec.z);
		if (abs(beta) < EPS || abs(PI - beta) < EPS) {
			return make6(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		Vec3 ey1 = make3(SideVec.x, SideVec.y, 0);
		ey1.z = 0;
		ey1 = normalize3(ey1);

		Vec3 ey3 = negate3(eZ);
		Vec3 ey2 = cross3(ey3, ey1);

		double edgeSine = abs(sin(beta));
		if (edgeSine < MIN_EDGE_SINE_FOR_DOUBLE) {
			double[] point = { obs.x, obs.y, obs.z }, slipVector = { slip.x, slip.y, slip.z };
			double[] start = { PA.x, PA.y, PA.z }, end = { PB.x, PB.y, PB.z };
			double[] correction = conservativePrecision || edgeSine < MIN_EDGE_SINE_FOR_EXTENDED
					? TriangularDislocationPrecision.edge(point, slipVector, start, end, nu)
					: TriangularDislocationExtended.edge(point, slipVector, start, end, nu);
			return tensor_transform3(ey1, ey2, ey3, make6(correction[0], correction[1], correction[2],
					correction[3], correction[4], correction[5]));
		}

		Vec3 yA = transform3(ey1, ey2, ey3, sub3(obs, PA));
		Vec3 yAB = transform3(ey1, ey2, ey3, SideVec);
		Vec3 yB = sub3(yA, yAB);

		Vec3 slip_adcs = transform3(ey1, ey2, ey3, slip);

		double configuration = beta;
		if (beta * yA.x >= 0) {
			configuration = -PI + beta;
		}

		Tensor vA = AngDisStrainFSC(
				yA.x, yA.y, yA.z, configuration,
				slip_adcs.x, slip_adcs.y, slip_adcs.z,
				nu, -PA.z);
		Tensor vB = AngDisStrainFSC(
				yB.x, yB.y, yB.z, configuration,
				slip_adcs.x, slip_adcs.y, slip_adcs.z,
				nu, -PB.z);
		Tensor v = sub6(vB, vA);
		return tensor_transform3(ey1, ey2, ey3, v);
	}
}
