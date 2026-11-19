package org.opensha.sha.simulators.stiffness;

import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.data.Range;
import org.jfree.chart.ui.TextAnchor;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.simulators.stiffness.patches.StiffnessPatch;
import org.opensha.sha.simulators.stiffness.patches.RectangularPatch;
import org.opensha.sha.simulators.stiffness.patches.TriangularPatch;
import org.opensha.sha.simulators.stiffness.OkadaDisplaceStrainCalc.Displacement;

/**
 * This implements stiffness calculations for finite rectangular and triangular patches.
 * The rectangular calculation was translated from
 * RSQSim C to Java by Kevin Milner in 9/2020 and uses Okada (1992).
 * Triangles use the analytical Nikkhoo-Walter (2015) half-space solution.
 * 
 * Patches use Cartesian coordinates in meters (e.g., UTM). The coordinate
 * system is East, North, Up.
 * 
 * @author kevin
 *
 */
public class StiffnessCalc {

	/** Compatibility name for the original rectangular patch API. */
	public static class Patch extends RectangularPatch {
		public Patch(Location center, int zone, char letter, double length, double width, FocalMechanism mechanism) {
			super(center, zone, letter, length, width, mechanism);
		}
		public Patch(double[] center, double length, double width, FocalMechanism mechanism) {
			super(center, length, width, mechanism);
		}
	}

	private static final double HALF_PI = 0.5*Math.PI;

	/**
	 * Calculates stiffness between the given source and receiver. Sigma is positive for compression and
	 * negative for extension (as in RSQSim)
	 * 
	 * @param lambda
	 * @param mu
	 * @param source
	 * @param receiver
	 * @return { sigma, tau }
	 */
	public static double[] calcStiffness(double lambda, double mu, Patch source, Patch receiver) {
		return calcStiffness(lambda, mu, (StiffnessPatch)source, receiver);
	}

	/** Dispatches by source geometry; receivers are sampled at their centroids. */
	public static double[] calcStiffness(double lambda, double mu, StiffnessPatch source, StiffnessPatch receiver) {
		if (source instanceof TriangularPatch) {
			double poisson = lambda/(2*(lambda+mu));
			double[] strain = TriangularDislocationCalc.strain(source.corner, receiver.center,
					((TriangularPatch)source).getKernelSlip(), poisson);
			double trace = strain[0]+strain[1]+strain[2];
			double[] stress = {lambda*trace+2*mu*strain[0], 2*mu*strain[3], 2*mu*strain[4],
					lambda*trace+2*mu*strain[1], 2*mu*strain[5], lambda*trace+2*mu*strain[2]};
			double[] result = projectStress(stress, receiver.nu, receiver.u);
			result[0] = -result[0];
			return result;
		}
		if (!(source instanceof RectangularPatch))
			throw new IllegalArgumentException("Unsupported source patch: "+source.getClass().getName());
		return calcRectangularStiffness(lambda, mu, (RectangularPatch)source, receiver);
	}

	private static double[] calcRectangularStiffness(double lambda, double mu,
			RectangularPatch source, StiffnessPatch receiver) {
		double alpha = (lambda + mu)/(lambda + 2*mu);

		double theta = HALF_PI - source.strike;
		double cost = Math.cos(theta);
		double sint = Math.sin(theta);
		double dipDegree = Math.toDegrees(source.dip);
		double c = -source.corner[0][2];

		double u1 = Math.cos(source.rake);
		double u2 = Math.sin(source.rake);

		/* center of j^th patch in coord. system translated so that
        f[i].corner[0] is at x=0, y=0 (depth stays same) */
		double x = receiver.center[0] - source.corner[0][0];
		double y = receiver.center[1] - source.corner[0][1];
		double z = receiver.center[2];

		/* center of j^th patch in coord. system rotated so that
        strike direction (of fault i) is along +x axis */
		double xx = x*cost + y*sint;
		double yy = -x*sint + y*cost;
		double zz = z;

		Displacement disp = OkadaDisplaceStrainCalc.dc3d(alpha, xx, yy, zz, c, dipDegree, 0.0, source.L,
				0.0, source.W, u1, u2, 0.0);
		if (disp == null)
			return null;

		//		dc3d(alpha, xx, yy, zz, c, dip, 0.0, source.L, 0.0, source.W,
		//				u1, u2, 0.0,
		//				&u[0], &u[1], &u[2],
		//				&du[0][0], &du[1][0], &du[2][0],
		//				&du[0][1], &du[1][1], &du[2][1],
		//				&du[0][2], &du[1][2], &du[2][2]);

		double[] Sigma = deformationToStress(disp, lambda, mu);

		/* now need to rotate Sigma back to global coords. and store in f[j].K[i], this
        is just R^T * Sigma * R, which I've multiplied out by hand */
		double[] K = new double[6];
		K[0] = Sigma[0]*cost*cost - 2*Sigma[1]*sint*cost + Sigma[3]*sint*sint;
		K[1] = Sigma[0]*sint*cost + Sigma[1]*(cost*cost - sint*sint) - Sigma[3]*sint*cost;
		K[2] = Sigma[2]*cost - Sigma[4]*sint;
		K[3] = Sigma[0]*sint*sint + 2*Sigma[1]*sint*cost + Sigma[3]*cost*cost;
		K[4] = Sigma[2]*sint + Sigma[4]*cost;
		K[5] = Sigma[5];

		double[] stiffness = projectStress(K, receiver.nu, receiver.u);

		/* up until here, extension has been reckoned positive, but in the simulator
        code, compression is reckoned positive, so need to switch sign of Ksigma */
		//		receiver.Ksigma[i] = -receiver.Ksigma[i];
		stiffness[0] = -stiffness[0];

		return stiffness;
	}

	/**
	 * Calculates Coulomb stress change. Note that sigma should be positive for compression,
	 * as it is in the stiffness calculation code, and this calculation ignores pore pressure effects.
	 * 
	 * CFF = tau - coeffOfFriction*sigma
	 * 
	 * @param tau
	 * @param sigma
	 * @param coeffOfFriction
	 * @return
	 */
	public static double calcCoulombStress(double tau, double sigma, double coeffOfFriction) {
		return tau - coeffOfFriction*sigma;
	}

	/**
	   given a deformation tensor (i.e. du[i][j] = du_i/dx_j) calculates the
	   stress tensor.  The stress tensor is stored as a vector with the components
	   ordered thusly: (0 1 2)
	                   (  3 4)
	                   (    5)
	   lambda and mu are Lame's parameters
	   space needs to already have been allocated for sigma
	 */
	static double[] deformationToStress(Displacement disp, /* deformation tensor, see above */
			double lambda, double mu) /* Lame's constants */
	{

		double[][] ss = new double[3][3]; /* the stress tensor as a 3x3 array */
		double tre; /* the trace of the strain tensor (which is equal to the trace of du */
		int i, j, k;

		/* first just calc the mu part */
		for (i=0; i<3; i++) {
			for (j=i; j<3; j++) /* only need to calc upper triangle */
				ss[i][j] = mu*(disp.du[i][j] + disp.du[j][i]);
		}

		/* find the trace of the strain tensor (= trace of du), needed for
	     lambda part of stress tensor */
		tre = 0.0;
		for (i=0; i<3; i++) tre += disp.du[i][i];

		/* add on lambda part (only affects diagonal) */
		for (i=0; i<3; i++) ss[i][i] += lambda*tre;

		/* store stress as 6-component vector */
		k = 0;
		double[] sigma = new double[6];
		for (i=0; i<3; i++) {
			for (j=i; j<3; j++) /* only need to do upper triangle */
				sigma[k++] = ss[i][j];
		}

		return sigma;
	}

	/**
	   Given a stress tensor Sigma (as a 6-component vector like: (0 1 2)
	                                                              (  3 4)
	                                                              (    5)),
	   and a unit normal vector nu to a fault plane and a unit slip vector u (of the
	   material into which nu points), calculates the normal stress on the
	   plane (tension positive) and the shear stress in the direction of u

	   @return {sigma, tau}
	 */
	static double[] projectStress(double[] Sigma, /* stress tensor as a 6-component vector */
			double[] nu, /* unit normal */
			double[] u) /* slip direction (of material into which nu points) */
	{
		double[][] ss = new double[3][3];  /* stress tensor as a 3x3 array */
		int i, j, k;

		/* expand Sigma to a 3x3 array */
		k = 0;
		for (i=0; i<3; i++) /* upper triangle */
		{
			for (j=i; j<3; j++) ss[i][j] = Sigma[k++];
		}
		for (i=0; i<3; i++) /* lower triangle */
		{
			for (j=0; j<i; j++) ss[i][j] = ss[j][i];
		}

		double sigma = 0.0;
		double tau = 0.0;
		for (i=0; i<3; i++)
		{
			for (j=0; j<3; j++)
			{
				sigma += nu[i]*ss[i][j]*nu[j];
				tau += u[i]*ss[i][j]*nu[j];
			}
		}

		return new double[] { sigma, tau };
	}

	public static void plot(Patch source, Patch receiver, double lambda, double mu, double coeffOfFriction) {
		double[] stiffness = calcStiffness(lambda, mu, source, receiver);
		double sigma = stiffness[0];
		double tau = stiffness[1];
		double cff = calcCoulombStress(tau, sigma, coeffOfFriction);
		System.out.println("sigma="+sigma+", tau="+tau+", cff="+cff);

		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();

		plotPatch(funcs, chars, source, true);
		plotPatch(funcs, chars, receiver, false);

		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (XY_DataSet func : funcs) {
			minX = Math.min(minX, func.getMinX());
			maxX = Math.max(maxX, func.getMaxX());
			minY = Math.min(minY, func.getMinY());
			maxY = Math.max(maxY, func.getMaxY());
		}
		double maxSpan = Math.max(maxX - minX, maxY - minY);
		maxSpan += 2;
		double halfSpan = 0.5*maxSpan;
		double midX = 0.5*(minX + maxX);
		double midY = 0.5*(minY + maxY);
		Range xRange = new Range(midX - halfSpan, midX + halfSpan);
		Range yRange = new Range(midY - halfSpan, midY + halfSpan);

		PlotSpec spec = new PlotSpec(funcs, chars, " ", "X (km)", "Y (km)");
		spec.setLegendVisible(true);

		double annY = yRange.getLowerBound() + 0.95 * yRange.getLength();
		double leftX = xRange.getLowerBound() + 0.05 * xRange.getLength();
		double centerX = xRange.getLowerBound() + 0.5 * xRange.getLength();
		double rightX = xRange.getLowerBound() + 0.95 * xRange.getLength();
		Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, 18);

		DecimalFormat df;
		double minVal = Math.min(Math.abs(sigma), Math.abs(tau));
		if (minVal > 10)
			df = new DecimalFormat("0.0");
		else if (minVal > 1)
			df = new DecimalFormat("0.00");
		else
			df = new DecimalFormat("0.000");

		XYTextAnnotation sigmaAnn = new XYTextAnnotation("Sigma="+df.format(sigma), leftX, annY);
		sigmaAnn.setTextAnchor(TextAnchor.TOP_LEFT);
		sigmaAnn.setFont(annFont);
		spec.addPlotAnnotation(sigmaAnn);

		XYTextAnnotation tauAnn = new XYTextAnnotation("Tau="+df.format(tau), centerX, annY);
		tauAnn.setTextAnchor(TextAnchor.TOP_CENTER);
		tauAnn.setFont(annFont);
		spec.addPlotAnnotation(tauAnn);

		XYTextAnnotation coulombAnn = new XYTextAnnotation("CFF="+df.format(cff), rightX, annY);
		coulombAnn.setTextAnchor(TextAnchor.TOP_RIGHT);
		coulombAnn.setFont(annFont);
		spec.addPlotAnnotation(coulombAnn);

		GraphWindow gw = new GraphWindow(spec);
		gw.setAxisRange(xRange, yRange);
		gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
	}

	private static void plotPatch(List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars,
			Patch patch, boolean isSource) {
		boolean dipping = (float)patch.dip < (float)(Math.PI/2d);
		Color color = isSource ? Color.GREEN.darker() : Color.MAGENTA.darker();
		if (dipping) {
			DefaultXY_DataSet outline = new DefaultXY_DataSet();
			for (int i=0; i<patch.corner.length+1; i++) {
				double[] point = patch.corner[i % patch.corner.length];
				outline.set(point[0], point[1]);
			}
			funcs.add(outline);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, color));
		}

		DefaultXY_DataSet trace = new DefaultXY_DataSet();
		if (isSource)
			trace.setName("Source");
		else
			trace.setName("Receiver");
		trace.set(patch.corner[2][0], patch.corner[2][1]);
		trace.set(patch.corner[3][0], patch.corner[3][1]);

		funcs.add(trace);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, color));

		// slip arrow
		DefaultXY_DataSet arrow = new DefaultXY_DataSet();
		arrow.set(patch.center[0], patch.center[1]);
		double endX = patch.center[0] + 0.4*Math.sin((patch.strike - patch.rake));
		double endY = patch.center[1] + 0.4*Math.cos((patch.strike - patch.rake));
		arrow.set(endX, endY);

		if (!isSource)
			arrow.setName("Rakes");
		funcs.add(arrow);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 3f, Color.BLACK));

		DefaultXY_DataSet center = new DefaultXY_DataSet();
		center.set(patch.center[0], patch.center[1]);
		funcs.add(center);
		chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 6f, color));
	}

	public static void main(String[] args) {
		double lambda = 30000;
		double mu = 30000;
		double coeffOfFriction = 0.5;

		/*
		 * 2 stike-slip end-to-end
		 * 
		 * ||
		 * ||
		 * ||
		 * 
		 * ||
		 * ||
		 * ||
		 */
		double[] sourceCenter = { 0d, 0d, -1000d };
		FocalMechanism sourceMech = new FocalMechanism(0d, 90, 180d);
		Patch source = new Patch(sourceCenter, 2000d, 2000d, sourceMech);

		double[] receiverCenter = { 0d, 5000d, -1d };
		FocalMechanism receiverMech = new FocalMechanism(0d, 90, 180d);
		Patch receiver = new Patch(receiverCenter, 2000d, 2000d, receiverMech);
		//		double[] receiverCenter = sourceCenter;
		//		FocalMechanism receiverMech = sourceMech;
		//		Patch receiver = source;
		plot(source, receiver, lambda, mu, coeffOfFriction);

		/*
		 * 2 stike-slip end-to-end
		 * 
		 * ||
		 * ||
		 * ||
		 * 
		 * ||
		 * ||
		 * ||
		 * ||
		 * ||
		 * ||
		 */
		sourceCenter = new double[] { 0d, -1000d, -1000d };
		sourceMech = new FocalMechanism(0d, 90, 180d);
		source = new Patch(sourceCenter, 4000d, 2000d, sourceMech);

		receiverCenter = new double[] { 0d, 5000d, -1d };
		receiverMech = new FocalMechanism(0d, 90, 180d);
		receiver = new Patch(receiverCenter, 2000d, 2000d, receiverMech);
		//		double[] receiverCenter = sourceCenter;
		//		FocalMechanism receiverMech = sourceMech;
		//		Patch receiver = source;
		plot(source, receiver, lambda, mu, coeffOfFriction);

		//		/*
		//		 * 2 reverse faults which dip toward each other.
		//		 *
		//		 * (in these drawings, || is the top of the fault and | is the buried bottom,
		//		 * and hanging wall motion is drawn with an arrow)
		//		 *   source       receiver
		//		 * ||-------|    |-------||
		//		 * ||       |    |       ||
		//		 * ||  <--  |    |  -->  ||
		//		 * ||       |    |       ||
		//		 * ||-------|    |-------||
		//		 *
		//		 * sigma: -333.9
		//		 * tau: -283.1
		//		 * cff: --116.2
		//		 */
		//		sourceCenter = new double[] { 0d, 0d, -1000d };
		//		sourceMech = new FocalMechanism(0d, 45, 90);
		//		source = new Patch(sourceCenter, 2000d, 2000d, sourceMech);
		//
		//		receiverCenter = new double[] { 5d, 0d, -1000d };
		//		receiverMech = new FocalMechanism(180d, 45, 90d);
		//		receiver = new Patch(receiverCenter, 2000d, 2000d, receiverMech);
		////		double[] receiverCenter = sourceCenter;
		////		FocalMechanism receiverMech = sourceMech;
		////		Patch receiver = source;
		//		plot(source, receiver, lambda, mu, coeffOfFriction);
		//
		//		/*
		//		 * 2 reverse faults which dip toward each other. Motion on either will
		//		 * unclamp (sigma <0) the other
		//		 *
		//		 * (in these drawings, || is the top of the fault and | is the buried bottom,
		//		 * and hanging wall motion is drawn with an arrow)
		//		 *   source       receiver
		//		 * ||-------|    |-------||
		//		 * ||       |    |       ||
		//		 * ||  -->  |    |  <--  ||
		//		 * ||       |    |       ||
		//		 * ||-------|    |-------||
		//		 *
		//		 * sigma:
		//		 * tau:
		//		 * cff:
		//		 */
		//		sourceCenter = new double[] { 0d, 0d, -1d };
		//		sourceMech = new FocalMechanism(0d, 45, -90d);
		//		source = new Patch(sourceCenter, 2d, 2d, sourceMech);
		//
		//		receiverCenter = new double[] { 5d, 0d, -1d };
		//		receiverMech = new FocalMechanism(180d, 45, -90d);
		//		receiver = new Patch(receiverCenter, 2d, 2d, receiverMech);
		//		plot(source, receiver, lambda, mu, coeffOfFriction);
		//
		//		/*
		//		 * 2 reverse faults which dip toward each other. Motion on either will
		//		 * unclamp (sigma <0) the other
		//		 *
		//		 * (in these drawings, || is the top of the fault and | is the buried bottom,
		//		 * and hanging wall motion is drawn with an arrow)
		//		 *   source       receiver
		//		 * ||-------|    |-------||
		//		 * ||       |    |       ||
		//		 * ||  -->  |    |  -->  ||
		//		 * ||       |    |       ||
		//		 * ||-------|    |-------||
		//		 *
		//		 * sigma:
		//		 * tau:
		//		 * cff:
		//		 */
		//		sourceCenter = new double[] { 0d, 0d, -1d };
		//		sourceMech = new FocalMechanism(0d, 45, -90d);
		//		source = new Patch(sourceCenter, 2d, 2d, sourceMech);
		//
		//		receiverCenter = new double[] { 5d, 0d, -1d };
		//		receiverMech = new FocalMechanism(180d, 45, 90d);
		//		receiver = new Patch(receiverCenter, 2d, 2d, receiverMech);
		//		plot(source, receiver, lambda, mu, coeffOfFriction);
		//
		//		/*
		//		 * 2 reverse faults which dip toward each other. Motion on either will
		//		 * unclamp (sigma <0) the other
		//		 *
		//		 * (in these drawings, || is the top of the fault and | is the buried bottom,
		//		 * and hanging wall motion is drawn with an arrow)
		//		 *
		//		 *           receiver
		//		 *        ===============
		//		 *             -->
		//		 *
		//		 *
		//		 *       ||
		//		 *       || ^
		//		 *       || |
		//		 *       || |
		//		 *       ||
		//		 *     source
		//		 *
		//		 * sigma:
		//		 * tau:
		//		 * cff:
		//		 */
		//		sourceCenter = new double[] { 0d, 0d, -1d };
		//		sourceMech = new FocalMechanism(0d, 90, 0d);
		//		source = new Patch(sourceCenter, 2d, 2d, sourceMech);
		//
		//		receiverCenter = new double[] { 1d, 2d, -1d };
		//		receiverMech = new FocalMechanism(90d, 90, 0d);
		//		receiver = new Patch(receiverCenter, 2d, 2d, receiverMech);
		//		plot(source, receiver, lambda, mu, coeffOfFriction);
		//
		//		receiverCenter = new double[] { 0.5*Math.sqrt(2), 2d+0.5*Math.sqrt(2), -1d };
		//		receiverMech = new FocalMechanism(45d, 90, 0d);
		//		receiver = new Patch(receiverCenter, 2d, 2d, receiverMech);
		//		plot(source, receiver, lambda, mu, coeffOfFriction);
		//
		//		receiverCenter = new double[] { 0.5*Math.sqrt(2), 2d-0.5*Math.sqrt(2), -1d };
		//		receiverMech = new FocalMechanism(135d, 90, 0d);
		//		receiver = new Patch(receiverCenter, 2d, 2d, receiverMech);
		//		plot(source, receiver, lambda, mu, coeffOfFriction);
	}

}
