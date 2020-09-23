package org.opensha.sha.simulators.stiffness;

import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.data.Range;
import org.jfree.ui.TextAnchor;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.utm.UTM;
import org.opensha.commons.geo.utm.WGS84;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.simulators.stiffness.OkadaDisplaceStrainCalc.Displacement;

/**
 * This implements the RSQSim stiffness calculations for rectangular patches, translated form
 * C to Java by Kevin Milner in 9/2020. It uses the Okada (1992) displacement calculations.
 * 
 * Patches must be rectangular and in Cartesian coordinates in meters (e.g., UTM). The coordinate
 * system is East, North, Up.
 * 
 * @author kevin
 *
 */
public class StiffnessCalc {

	// coord system is East, North, Up
	public static class Patch {
		/** corner[i][j] is the x_j coord. of the i^th corner
	    for type == OKADA:
	    corner[0] should be the lower corner in the
		away-from-strike direction, corner[1] the lower
		corner in the along-strike direction, corner[2]
		the upper corner in the along-strike direction and
		corner[3] the upper corner in the away-from-strike
		direction */
		public final double[][] corner;
		/**
		 * center point
		 */
		public final double[] center;
		/**
		 * length in the along-strike direction
		 */
		public final double L;
		/**
		 * width in the along-dip direction
		 */
		public final double W;
		public final double area;
		/** as in Aki and Richards; in radians */
		public final double strike;
		/** as in Aki and Richards; in radians */
		public final double dip;
		/** as in Aki and Richards; in radians */
		public final double rake;
		/** upward pointing unit normal vector, i.e. unit
        vector pointing into hanging wall */
		public final double[] nu;
		/** unit slip vector (of hanging wall relative to footwall).  u.nu should probably be zero */
		public final double[] u;
		
		public Patch(Location centerLoc, int utmZone, char utmLetter,
				double length, double width, FocalMechanism mech) {
			this(center(centerLoc, utmZone, utmLetter), length, width, mech);
		}
		
		public Patch(double[] center, double length, double width, FocalMechanism mech) {
			this.center = center;
			this.L = length;
			this.W = width;
			this.strike = Math.toRadians(mech.getStrike());
			this.dip= Math.toRadians(mech.getDip());
			this.rake = Math.toRadians(mech.getRake());
			this.area = L*W;
			
			// calculate corners (from findCorners.c)
			double[] st = new double[3]; /* unit vector in strike direction */
			double[] dd = new double[3]; /* unit vector in down-dip direction */
			int i;

			st[0] = Math.sin(strike);  st[1] = Math.cos(strike); st[2] = 0.0;
			dd[0] = Math.cos(dip)*Math.cos(strike);  
			dd[1] = -Math.cos(dip)*Math.sin(strike);
			dd[2] = -Math.sin(dip);
			
			this.corner = new double[4][3];
			
			double halfL = 0.5*L;
			double halfW = 0.5*W;

			for (i=0; i<3; i++) 
			{
				corner[0][i] = center[i] - (halfL)*st[i] + (halfW)*dd[i];
				corner[1][i] = center[i] + (halfL)*st[i] + (halfW)*dd[i];
				corner[2][i] = center[i] + (halfL)*st[i] - (halfW)*dd[i];
				corner[3][i] = center[i] - (halfL)*st[i] - (halfW)*dd[i];
			}
			
			// calculate unit vectors (from findNormalAndSlip.c)
			double[] ud = new double[3]; /* unit vector in up-dip direction */
			double cosr, sinr;  /* cosine and sine of rake angle */
			int ic;
			
			ud[0] = -Math.cos(dip)*Math.cos(strike);  
			ud[1] = Math.cos(dip)*Math.sin(strike);
			ud[2] = Math.sin(dip);

			nu = cross(st, ud);

			cosr = Math.cos(rake);  sinr = Math.sin(rake);
			u = new double[3];
			for (ic=0; ic<3; ic++)
				u[ic] = cosr*st[ic] + sinr*ud[ic];
		}
	}
	
	private static double[] center(Location loc, int zone, char letter) {
		WGS84 wgs = new WGS84(loc.getLatitude(), loc.getLongitude());
		UTM utm = new UTM(wgs, zone, letter);
		return new double[] { utm.getEasting(), utm.getNorthing(), -1000d*loc.getDepth() };
	}
	
	private static double[] cross(double[] x, double[] y) {
		double[] z = new double[3];
		z[0] = x[1]*y[2] - x[2]*y[1];
		z[1] = x[2]*y[0] - x[0]*y[2];
		z[2] = x[0]*y[1] - x[1]*y[0];

		return z;
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
		 * 2 reverse faults which dip toward each other.
		 * 
		 * (in these drawings, || is the top of the fault and | is the buried bottom,
		 * and hanging wall motion is drawn with an arrow)
		 *   source       receiver
		 * ||-------|    |-------||
		 * ||       |    |       ||
		 * ||  <--  |    |  -->  ||
		 * ||       |    |       ||
		 * ||-------|    |-------||
		 * 
		 * sigma: -333.9
		 * tau: -283.1
		 * cff: --116.2
		 */
		double[] sourceCenter = { 0d, 0d, -1d };
		FocalMechanism sourceMech = new FocalMechanism(0d, 45, 90d);
		Patch source = new Patch(sourceCenter, 2d, 2d, sourceMech);
		
		double[] receiverCenter = { 5d, 0d, -1d };
		FocalMechanism receiverMech = new FocalMechanism(180d, 45, 90d);
		Patch receiver = new Patch(receiverCenter, 2d, 2d, receiverMech);
		plot(source, receiver, lambda, mu, coeffOfFriction);
		
		/*
		 * 2 reverse faults which dip toward each other. Motion on either will
		 * unclamp (sigma <0) the other
		 * 
		 * (in these drawings, || is the top of the fault and | is the buried bottom,
		 * and hanging wall motion is drawn with an arrow)
		 *   source       receiver
		 * ||-------|    |-------||
		 * ||       |    |       ||
		 * ||  -->  |    |  <--  ||
		 * ||       |    |       ||
		 * ||-------|    |-------||
		 * 
		 * sigma: 
		 * tau: 
		 * cff: 
		 */
		sourceCenter = new double[] { 0d, 0d, -1d };
		sourceMech = new FocalMechanism(0d, 45, -90d);
		source = new Patch(sourceCenter, 2d, 2d, sourceMech);
		
		receiverCenter = new double[] { 5d, 0d, -1d };
		receiverMech = new FocalMechanism(180d, 45, -90d);
		receiver = new Patch(receiverCenter, 2d, 2d, receiverMech);
		plot(source, receiver, lambda, mu, coeffOfFriction);
		
		/*
		 * 2 reverse faults which dip toward each other. Motion on either will
		 * unclamp (sigma <0) the other
		 * 
		 * (in these drawings, || is the top of the fault and | is the buried bottom,
		 * and hanging wall motion is drawn with an arrow)
		 *   source       receiver
		 * ||-------|    |-------||
		 * ||       |    |       ||
		 * ||  -->  |    |  -->  ||
		 * ||       |    |       ||
		 * ||-------|    |-------||
		 * 
		 * sigma: 
		 * tau: 
		 * cff: 
		 */
		sourceCenter = new double[] { 0d, 0d, -1d };
		sourceMech = new FocalMechanism(0d, 45, -90d);
		source = new Patch(sourceCenter, 2d, 2d, sourceMech);
		
		receiverCenter = new double[] { 5d, 0d, -1d };
		receiverMech = new FocalMechanism(180d, 45, 90d);
		receiver = new Patch(receiverCenter, 2d, 2d, receiverMech);
		plot(source, receiver, lambda, mu, coeffOfFriction);
		
		/*
		 * 2 reverse faults which dip toward each other. Motion on either will
		 * unclamp (sigma <0) the other
		 * 
		 * (in these drawings, || is the top of the fault and | is the buried bottom,
		 * and hanging wall motion is drawn with an arrow)
		 * 
		 *           receiver
		 *        ===============
		 *             -->
		 * 
		 * 
		 *       ||
		 *       || ^
		 *       || |
		 *       || |
		 *       ||
		 *     source
		 * 
		 * sigma: 
		 * tau: 
		 * cff: 
		 */
		sourceCenter = new double[] { 0d, 0d, -1d };
		sourceMech = new FocalMechanism(0d, 90, 0d);
		source = new Patch(sourceCenter, 2d, 2d, sourceMech);
		
		receiverCenter = new double[] { 1d, 2d, -1d };
		receiverMech = new FocalMechanism(90d, 90, 0d);
		receiver = new Patch(receiverCenter, 2d, 2d, receiverMech);
		plot(source, receiver, lambda, mu, coeffOfFriction);
		
		receiverCenter = new double[] { 0.5*Math.sqrt(2), 2d+0.5*Math.sqrt(2), -1d };
		receiverMech = new FocalMechanism(45d, 90, 0d);
		receiver = new Patch(receiverCenter, 2d, 2d, receiverMech);
		plot(source, receiver, lambda, mu, coeffOfFriction);
		
		receiverCenter = new double[] { 0.5*Math.sqrt(2), 2d-0.5*Math.sqrt(2), -1d };
		receiverMech = new FocalMechanism(135d, 90, 0d);
		receiver = new Patch(receiverCenter, 2d, 2d, receiverMech);
		plot(source, receiver, lambda, mu, coeffOfFriction);
	}

}
