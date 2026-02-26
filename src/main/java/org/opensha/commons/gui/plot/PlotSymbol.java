package org.opensha.commons.gui.plot;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.NoSuchElementException;
import java.util.Random;

import org.jfree.chart.util.ShapeUtils;

import com.google.common.base.Preconditions;

public enum PlotSymbol {
	
	X("X symbols", false),
	CROSS("+ symbols", false),
	BOLD_X("Bold X symbols", true),
	BOLD_CROSS("Bold + symbols", true),
	FILLED_CIRCLE("Filled Circles", true),
	CIRCLE("Circles", false),
	FILLED_SQUARE("Filled Squares", true),
	SQUARE("Squares", false),
	FILLED_TRIANGLE("Filled Triangles", true),
	TRIANGLE("Triangles", false),
	FILLED_INV_TRIANGLE("Filled Inv. Triangles", true),
	INV_TRIANGLE("Inv. Triangles", false),
	FILLED_DIAMOND("Filled Diamonds", true),
	DIAMOND("Diamonds", false),
	DASH("Dash", false),
	BOLD_DASH("Bold Dash", true),
	POLYGON("Polygon", false),
	FILLED_POLYGON("Filled polygon", true);
	
	private String desc;
	private boolean filled;
	
	private PlotSymbol(String desc, boolean filled) {
		this.desc = desc;
		this.filled = filled;
	}
	
	public boolean isFilled() {
		return filled;
	}
	
	@Override
	public String toString() {
		return desc;
	}
	
	public static PlotSymbol forString(String desc) {
		for (PlotSymbol sym : values()) {
			if (sym.desc.equalsIgnoreCase(desc))
				return sym;
		}
		throw new NoSuchElementException("No symbol exists for '"+desc+"'");
	}
	
	private final static double SIZE = 2;
	private final static double DELTA = SIZE / 2.0;
	
	public Shape buildShape(float width) {
		return buildShape(width, PlotPreferences.DEFAULT);
	}
	
	public Shape buildShape(float width, PlotPreferences prefs) {
		Preconditions.checkArgument(width>0, "width must be >0");
		if (prefs.isTrueSymbolSizing()) {
			if (this == X || this == BOLD_X) {
				// supplied length is the "length of each arm"
//				float diagonal = (float)(0.5*Math.sqrt(2d*width*width));
				return ShapeUtils.createDiagonalCross(0.5f*width, this == BOLD_X ?
						Float.max(0.25f, 0.25f*width) : Float.max(0.1f, 0.1f*width));
			}
			if (this == POLYGON || this == FILLED_POLYGON)
				return getDefaultIrregularPolygon(width);
			// will need half width
			float halfW = 0.5f*width;
			if (this == CROSS || this == BOLD_CROSS) {
				// supplied length is the "length of each arm"
				return ShapeUtils.createRegularCross(halfW, this == BOLD_CROSS ?
						Float.max(0.25f, 0.25f*width) : Float.max(0.1f, 0.1f*width));
			}
			if (this == CIRCLE || this == FILLED_CIRCLE)
				return new Ellipse2D.Float(-halfW, -halfW, width, width);
			if (this == SQUARE || this == FILLED_SQUARE)
				return new Rectangle.Float(-halfW, -halfW, width, width);
			if (this == TRIANGLE || this == FILLED_TRIANGLE)
				// supplied length is the "half-height of the triangle"
				return ShapeUtils.createUpTriangle(halfW);
			if (this == INV_TRIANGLE || this == FILLED_INV_TRIANGLE)
				// supplied length is the "half-height of the triangle"
				return ShapeUtils.createDownTriangle(halfW);
			if (this == DIAMOND || this == FILLED_DIAMOND)
				// supplied length is the "half-height of the diamond"
				return ShapeUtils.createDiamond(halfW);
			if (this == DASH)
				return new Line2D.Float(-halfW, 0, halfW, 0);
			if (this == BOLD_DASH)
				return ShapeUtils.createLineRegion(new Line2D.Float(-halfW, 0, halfW, 0), this == BOLD_DASH ?
						Float.max(0.5f, 0.25f*width) : Float.max(0.1f, 0.1f*width));
		} else {
			if (this == CIRCLE || this == FILLED_CIRCLE)
				return new Ellipse2D.Double(-DELTA-width/2,
						-DELTA-width/2, SIZE+width, SIZE+width);
			else if (this == SQUARE || this == FILLED_SQUARE)
				return new Rectangle.Double(-DELTA-width/2,
						-DELTA-width/2, SIZE+width, SIZE+width);
			else if (this == TRIANGLE || this == FILLED_TRIANGLE)
				return ShapeUtils.createUpTriangle(width);
			else if (this == INV_TRIANGLE || this == FILLED_INV_TRIANGLE)
				return ShapeUtils.createDownTriangle(width);
			else if (this == DIAMOND || this == FILLED_DIAMOND)
				return ShapeUtils.createDiamond(width);
			else if (this == X)
				return ShapeUtils.createDiagonalCross(width,0.1f);
			else if (this == DASH)
				return ShapeUtils.createLineRegion(new Line2D.Float(-width/2f, 0, width/2f, 0), 0.1f);
			else if (this == BOLD_DASH)
				return ShapeUtils.createLineRegion(new Line2D.Float(-width/2f, 0, width/2f, 0), 0.5f);
			else if (this == BOLD_X)
				return ShapeUtils.createDiagonalCross(width,width*0.25f);
			else if (this == CROSS)
				return ShapeUtils.createRegularCross(width,0.1f);
			else if (this == BOLD_CROSS)
				return ShapeUtils.createRegularCross(width,width*0.25f);
			else if (this == POLYGON || this == FILLED_POLYGON)
				return getDefaultIrregularPolygon(width);
		}
		throw new UnsupportedOperationException("Can't build shape for symbol: "+toString());
	}
	
	private static Path2D IRREGULAR_POLYGON;
	
	private static Path2D getDefaultIrregularPolygon(float width) {
		if (IRREGULAR_POLYGON == null) {
			synchronized (PlotSymbol.class) {
				if (IRREGULAR_POLYGON == null) {
					IRREGULAR_POLYGON = createIrregularPolygon(10f, POLY_SEED, POLY_VERTICES, POLY_JITTER);
				}
			}
		}
		
		Path2D path = (Path2D) IRREGULAR_POLYGON.clone();
		
		Rectangle2D bounds = path.getBounds2D();

		double scaleX = width / bounds.getWidth();
		double scaleY = width / bounds.getHeight();

		AffineTransform at = new AffineTransform();

		// Translate to origin
		at.translate(-bounds.getCenterX(), -bounds.getCenterY());

		// Scale
		at.scale(scaleX, scaleY);

		path.transform(at);
		
		return path;
	}
	
	private static final int POLY_VERTICES = 5;
	private static final long POLY_SEED = 1234567l;
	private static final double POLY_JITTER = 0.3;
	
	/**
	 * Builds an irregular polygon marker that mostly fills a width×width bounding box.
	 *
	 * @param width total intended extent (Java2D units; pt in PDF, px in PNG)
	 * @param seed deterministic seed (use a constant for consistent styling across plots)
	 * @param vertices number of vertices
	 * @param jitterFrac how irregular: 0.0 = perfect shape, ~0.15-0.35 recommended
	 */
	public static Path2D createIrregularPolygon(float width, long seed, int vertices,
			double jitterFrac) {

		if (width <= 0f)
			throw new IllegalArgumentException("width must be > 0");
		if (vertices < 5)
			throw new IllegalArgumentException("vertices must be >= 5");
		if (jitterFrac < 0.0)
			throw new IllegalArgumentException("jitterFrac must be >= 0");

		Random r = new Random(seed);

		double half = width / 2.0;

		// Start from a rounded-square-like radius profile so it fills the box well.
		// For each angle, pick a radius that tends toward the max, then jitter it.
		double[] xs = new double[vertices];
		double[] ys = new double[vertices];

		for (int i = 0; i < vertices; i++) {
			double a = (2.0 * Math.PI * i) / vertices;

			// "Squircle-ish" base: larger radius near cardinal directions
			double ca = Math.cos(a);
			double sa = Math.sin(a);

			// Use an Lp norm style radius to bias toward filling the box.
			// p=4 gives a rounded-rect feel.
			double p = 4.0;
			double denom = Math.pow(Math.abs(ca), p) + Math.pow(Math.abs(sa), p);
			double base = (denom == 0.0) ? 0.0 : 1.0 / Math.pow(denom, 1.0 / p);

			// Bias toward outer boundary (fills the box), with some random inward variation
			double inward = 0.10 + 0.25 * r.nextDouble(); // 10-35% inward
			double rad = base * (1.0 - inward);

			// Add irregular jitter (both inward and outward, but clamp to [0.55, 1.0] of base)
			double jitter = (r.nextDouble() * 2.0 - 1.0) * jitterFrac;
			rad = rad * (1.0 + jitter);
			double min = base * 0.55;
			double max = base * 1.00;
			if (rad < min) rad = min;
			if (rad > max) rad = max;

			// Scale to the desired half-width
			double x = ca * rad * half;
			double y = sa * rad * half;

			// Small tangential jitter so it looks less radial
			double tang = (r.nextDouble() * 2.0 - 1.0) * jitterFrac * half * 0.20;
			x += -sa * tang;
			y +=  ca * tang;

			xs[i] = x;
			ys[i] = y;
		}

		Path2D path = new Path2D.Double();
		path.moveTo(xs[0], ys[0]);
		for (int i = 1; i < vertices; i++)
			path.lineTo(xs[i], ys[i]);
		path.closePath();

		return path;
	}

}
