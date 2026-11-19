package org.opensha.sha.simulators.stiffness;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.Test;
import org.opensha.sha.simulators.stiffness.patches.RectangularPatch;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchGenerationStrategy;
import org.opensha.sha.simulators.stiffness.patches.StiffnessPatch;
import org.opensha.sha.simulators.stiffness.patches.TriangularPatch;

public class TriangularPrecisionTest {
	/** Tensor order xx, yy, zz, xy, xz, yz; mu=30 GPa, lambda follows Poisson ratio. */
	private static double[] stress(double[] strain, double poisson) {
		double[] stress = strain.clone();
		double lambda = 60000 * poisson / (1 - 2 * poisson), trace = strain[0] + strain[1] + strain[2];
		for (int i = 0; i < 6; i++)
			stress[i] *= 60000;
		for (int i = 0; i < 3; i++)
			stress[i] += lambda * trace;
		return stress;
	}

	private static double cff(double[] stress, StiffnessPatch receiver) {
		double[][] tensor = { { stress[0], stress[3], stress[4] },
				{ stress[3], stress[1], stress[5] }, { stress[4], stress[5], stress[2] } };
		double normal = 0, shear = 0;
		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 3; j++) {
				normal += receiver.nu[i] * tensor[i][j] * receiver.nu[j];
				shear += receiver.u[i] * tensor[i][j] * receiver.nu[j];
			}
		return shear + .5 * normal; // tensor normal stress is extension-positive
	}

	@Test
	public void extendedPrecisionMatches60DigitReferenceAcrossDifficultGeometries() {
		Random random = new Random(20261119L);
		int comparisons = 0;
		double[] angles = { .001, .005, .01, .02, .03, .04, .05, .057, .06, .075, .1, .11, .15, .2, .3, .5, .57 };
		for (double angle : angles)
			for (int shape = 0; shape < 24; shape++) {
				double length = new double[] { 100, 2000, 10000 }[shape % 3];
				double width = length * new double[] { .03, .3, 1, 5 }[shape % 4];
				double top = new double[] { 0, 1, 100, 2000, 10000 }[shape % 5];
				double rotation = random.nextDouble() * Math.PI * 2;
				double other = rotation + Math.PI / 2 + (random.nextDouble() - .5) * .8;
				double horizontal = length * Math.tan(Math.toRadians(angle));
				Vector3D a = new Vector3D(500000, 3800000, -top);
				Vector3D edge = new Vector3D(horizontal * Math.cos(rotation), horizontal * Math.sin(rotation), -length);
				Vector3D b = a.add(edge);
				Vector3D c = a.add(edge.scalarMultiply(.1 + .8 * random.nextDouble()))
						.add(new Vector3D(width * Math.cos(other), width * Math.sin(other), 0));
				Vector3D normal = b.subtract(a).crossProduct(c.subtract(a)).normalize();
				if (normal.getZ() < 0) {
					Vector3D swap = b;
					b = c;
					c = swap;
					normal = normal.negate();
				}
				Vector3D center = new Vector3D(1d / 3, a, 1d / 3, b, 1d / 3, c),
						edgeCenter = a.add(edge.scalarMultiply(.5));
				List<Vector3D> sites = List.of(center, center.add(normal.scalarMultiply(.001 * length)),
						center.add(normal.scalarMultiply(length)), center.add(normal.scalarMultiply(100 * length)),
						edgeCenter.add(normal.scalarMultiply(.0005 * length)),
						edgeCenter.add(normal.scalarMultiply(.05 * length)),
						a.add(normal.scalarMultiply(.0005 * length)),
						new Vector3D(a.getX() + width * .1, a.getY() - width * .05, 0));
				double[][] vertices = { a.toArray(), b.toArray(), c.toArray() };
				double[] slip = new double[3];
				slip[shape % 3] = 1;
				double poisson = new double[] { .15, .25, .35, .45 }[shape % 4];
				for (Vector3D point : sites) {
					if (point.getZ() > 0)
						point = new Vector3D(point.getX(), point.getY(), -point.getZ());
					double[] expected = stress(
							TriangularDislocationCalc.strain(vertices, point.toArray(), slip, poisson, true), poisson);
					double[] actual = stress(TriangularDislocationCalc.strain(vertices, point.toArray(), slip, poisson),
							poisson);
					double scale = Arrays.stream(expected).map(Math::abs).max().orElseThrow();
					// 1e-8 fractional tensor accuracy, with a 0.001 Pa floor for weak
					// signals where main/image cancellation makes relative error misleading.
					for (int i = 0; i < 6; i++)
						assertEquals("angle=" + angle + ", shape=" + shape + ", site=" + point,
								expected[i], actual[i], 1e-9 + 1e-8 * scale);
					comparisons++;
				}
			}
		assertEquals(3264, comparisons);
	}

	@Test
	public void retainsCoulombSignThatOrdinaryDoublesReverse() {
		double[][] vertices = { { 500000, 3800000, -100 },
				{ 500000.2047799329, 3799999.8368914113, -200 },
				{ 500002.6681231938, 3800001.454552909, -139.69295838481332 } };
		double[] point = { 500000.3, 3799999.85, 0 }, slip = { 1, 0, 0 };
		var receiver = new RectangularPatch(point, 2000, 2000, new FocalMechanism(30, 60, 90));
		double expected = cff(stress(TriangularDislocationCalc.strain(vertices, point, slip, .15, true), .15),
				receiver);
		double actual = cff(stress(TriangularDislocationCalc.strain(vertices, point, slip, .15), .15), receiver);
		assertEquals(-.004466158524934464, expected, 1e-12);
		assertEquals(expected, actual, 1e-9);
		assertTrue(actual < 0);
	}

	@Test
	public void skinnyNearVerticalTrianglesMatchReference() {
		Vector3D origin = new Vector3D(500000, 3800000, 0);
		double[] slip = { .7, -.3, 0 };
		for (double angle : new double[] { .006, .01, .1, .5 })
			for (double widthRatio : new double[] { 1e-4, 1e-3, 1e-2 })
				for (double depth : new double[] { 0, 100, 10000 }) {
					Vector3D a = origin.add(new Vector3D(0, 0, -depth));
					Vector3D edge = new Vector3D(2000 * Math.tan(Math.toRadians(angle)), 0, -2000);
					Vector3D b = a.add(edge),
							c = a.add(edge.scalarMultiply(.3)).add(new Vector3D(0, 2000 * widthRatio, 0));
					Vector3D normal = edge.crossProduct(c.subtract(a)).normalize();
					Vector3D center = new Vector3D(1d / 3, a, 1d / 3, b, 1d / 3, c);
					for (Vector3D point : List.of(center, center.add(normal), a.add(normal),
							new Vector3D(a.getX() + .3, a.getY() + .1, 0))) {
						if (point.getZ() > 0)
							point = new Vector3D(point.getX(), point.getY(), -point.getZ());
						double[][] vertices = { a.toArray(), b.toArray(), c.toArray() };
						double[] reference = stress(
								TriangularDislocationCalc.strain(vertices, point.toArray(), slip, .25, true), .25);
						double[] actual = stress(TriangularDislocationCalc.strain(vertices, point.toArray(), slip, .25),
								.25);
						double scale = Arrays.stream(reference).map(Math::abs).max().orElseThrow();
						for (int i = 0; i < 6; i++)
							assertEquals("skinny near-vertical angle=" + angle + ", width=" + widthRatio,
									reference[i], actual[i], 1e-9 + 1e-8 * scale);
					}
				}
	}

	@Test
	public void realNearVerticalPairPreservesInteractionsAndAreaFractions() throws Exception {
		String fixture = "patches/bennett_valley_279_282.geojson";
		try (var reader = new InputStreamReader(getClass().getResourceAsStream(fixture), StandardCharsets.UTF_8)) {
			var sections = GeoJSONFaultReader.readFaultSections(reader);
			var calc = new SubSectStiffnessCalculator(sections, 2, 30000, 30000, .5,
					PatchGenerationStrategy.TRIANGULAR, 0);
			var sources = calc.getPatches(sections.get(0));
			var receivers = calc.getPatches(sections.get(1));
			double referencePositive = 0, actualPositive = 0, totalArea = 0;
			for (var receiver : receivers) {
				double referenceNet = 0, actualNet = 0;
				for (var location : sources) {
					TriangularPatch source = (TriangularPatch) location.patch;
					double[] point = receiver.patch.center, slip = source.getKernelSlip();
					double reference = cff(
							stress(TriangularDislocationCalc.strain(source.corner, point, slip, .25, true), .25),
							receiver.patch);
					double actual = cff(stress(TriangularDislocationCalc.strain(source.corner, point, slip, .25), .25),
							receiver.patch);
					assertEquals(reference, actual, 1e-10 + 1e-8 * Math.abs(reference));
					assertEquals(reference >= 0, actual >= 0);
					referenceNet += reference;
					actualNet += actual;
				}
				assertEquals(referenceNet, actualNet, 1e-9 + 1e-8 * Math.abs(referenceNet));
				double area = receiver.patch.area;
				if (referenceNet >= 0)
					referencePositive += area;
				if (actualNet >= 0)
					actualPositive += area;
				totalArea += area;
			}
			assertEquals(referencePositive / totalArea, actualPositive / totalArea, 0);
		}
	}
}
