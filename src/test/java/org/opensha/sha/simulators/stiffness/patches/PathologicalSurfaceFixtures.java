package org.opensha.sha.simulators.stiffness.patches;

import java.util.LinkedHashMap;
import java.util.Map;

import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;

/** Analytic shapes sampled independently of the production surface helpers. */
final class PathologicalSurfaceFixtures {
	@FunctionalInterface
	interface Shape {
		/** East, north, and positive-down depth, in km. */
		double[] point(double u, double v);
	}

	static EvenlyGriddedSurface sample(int rows, int cols, Shape shape) {
		Location[][] nodes = new Location[rows][cols];
		for (int r = 0; r < rows; r++)
			for (int c = 0; c < cols; c++) {
				double[] p = shape.point((double) c / (cols - 1), (double) r / (rows - 1));
				nodes[r][c] = SurfaceTestFixtures.location(p[0], p[1], p[2]);
			}
		return SurfaceTestFixtures.nodes(nodes);
	}

	static double[] twistedPoint(double u, double v) {
		double dip = Math.toRadians(38 + 22 * u);
		return new double[] { 1.2 * Math.sin(2 * Math.PI * u) + 8.6 * v * Math.cos(dip) + 0.3 * v * v,
				13.7 * u + 0.3 * v * Math.sin(Math.PI * u), 5 + 8.6 * v * Math.sin(dip) + 0.8 * v * v };
	}

	static EvenlyGriddedSurface twisted() {
		return sample(11, 17, PathologicalSurfaceFixtures::twistedPoint);
	}

	static EvenlyGriddedSurface unevenTraces() {
		return sample(9, 25, (u, v) -> {
			double upper = 1.1 * Math.sin(2 * Math.PI * u);
			double lower = 6.7 + Math.sin(4 * Math.PI * u + 0.4);
			double down = upper + (lower - upper) * v;
			return new double[] { down * Math.cos(Math.PI / 4), 14 * u, 5 + down * Math.sin(Math.PI / 4) };
		});
	}

	static EvenlyGriddedSurface narrowNecks() {
		double[] widths = { 8, 8, 1, 1, 8, 8, 0.4, 0.4, 6, 6 };
		Location[][] nodes = new Location[9][widths.length];
		for (int r = 0; r < nodes.length; r++)
			for (int c = 0; c < widths.length; c++) {
				double down = 4 + widths[c] * ((double) r / (nodes.length - 1) - 0.5);
				nodes[r][c] = SurfaceTestFixtures.location(down * Math.cos(Math.PI / 4), 2.4 * c,
						5 + down * Math.sin(Math.PI / 4));
			}
		return SurfaceTestFixtures.nodes(nodes);
	}

	static EvenlyGriddedSurface thinTongue() {
		// A long tongue only 0.4 km wide lies between the main grid's row centers.
		return sample(9, 19, (u, v) -> {
			double width = u <= 0.4 ? 8 : u >= 0.6 ? 0.4 : 8 + (0.4 - 8) * (u - 0.4) / 0.2;
			double down = 4 + width * (v - 0.5);
			return new double[] { down * Math.cos(Math.PI / 4), 18 * u, 5 + down * Math.sin(Math.PI / 4) };
		});
	}

	static EvenlyGriddedSurface planar(double length, double width, double dip, double topDepth) {
		double radians = Math.toRadians(dip);
		return sample(5, 7, (u, v) -> new double[] { width * v * Math.cos(radians), length * u,
				topDepth + width * v * Math.sin(radians) });
	}

	static Map<String, EvenlyGriddedSurface> examples() {
		Map<String, EvenlyGriddedSurface> examples = new LinkedHashMap<>();
		examples.put("twisted", twisted());
		examples.put("listric",
				SurfaceTestFixtures.surface(12, new double[] { 8, 8, 8, 8, 8 }, new double[] { 80, 60, 40, 20 }, 5));
		examples.put("triangle",
				SurfaceTestFixtures.surface(12, new double[] { 0, 2, 4, 6, 8 }, new double[] { 45, 45, 45, 45 }, 5));
		examples.put("inverted_triangle",
				SurfaceTestFixtures.surface(12, new double[] { 8, 6, 4, 2, 0 }, new double[] { 45, 45, 45, 45 }, 5));
		examples.put("skewed", sample(5, 7, (u, v) -> new double[] { 8 * v * Math.cos(Math.PI / 4), 12 * u + 4 * v,
				5 + 8 * v * Math.sin(Math.PI / 4) }));
		examples.put("arch_cutout", sample(9, 25, (u, v) -> {
			double upper = 5 * Math.sin(Math.PI * u), down = upper + (8 - upper) * v;
			return new double[] { down * Math.cos(Math.PI / 4), 16 * u, 5 + down * Math.sin(Math.PI / 4) };
		}));
		examples.put("uneven_traces", unevenTraces());
		examples.put("narrow_necks", narrowNecks());
		examples.put("thin_tongue", thinTongue());
		examples.put("near_horizontal", planar(9.7, 6.3, 0.01, 5));
		examples.put("near_vertical", planar(9.7, 6.3, 89.99, 5));
		return examples;
	}
}
