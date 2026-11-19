package org.opensha.sha.simulators.stiffness.patches;

import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.analysis.interpolation.AkimaSplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.univariate.BrentOptimizer;
import org.apache.commons.math3.optim.univariate.SearchInterval;
import org.apache.commons.math3.optim.univariate.UnivariateObjectiveFunction;


/**
 * A reference sheet curved only along strike, with parallel straight down-dip
 * lines. Its cross-section is the middle trace viewed along the best-fit plane's
 * down-dip axis. Projection onto that curve retains along-strike shear; original
 * grid columns are not pinned to a single reference station. Coordinates are km.
 */
final class RibbonSurfaceReferenceFrame extends AbstractSurfaceReferenceFrame {
	private static final double FOOTPRINT_TOLERANCE=1e-4; // 10 cm in reference coordinates
	private final double edgeStep;
	private final boolean curved;

	RibbonSurfaceReferenceFrame(GriddedSurfaceGeometry geometry,double smoothingWindow) {
		this(geometry,new BestFitSurfacePlane(geometry),smoothingWindow);
	}

	private RibbonSurfaceReferenceFrame(GriddedSurfaceGeometry geometry,BestFitSurfacePlane plane,double window) {
		this(geometry,plane,new CrossSection(geometry,plane,window),window);
	}

	private RibbonSurfaceReferenceFrame(GriddedSurfaceGeometry geometry,BestFitSurfacePlane plane,
			CrossSection section,double window) {
		super(geometry,plane,section::project);
		edgeStep=window/4;
		curved=section.profile != null;
	}

	@Override
	public double[][] projectPatchBoundary(StiffnessPatch patch) {
		if (!curved) return super.projectPatchBoundary(patch);
		List<double[]> points=new ArrayList<>();
		for (int i=0; i<patch.corner.length; i++) {
			Vector3D a=new Vector3D(patch.corner[i]).scalarMultiply(0.001);
			Vector3D b=new Vector3D(patch.corner[(i+1)%patch.corner.length]).scalarMultiply(0.001);
			double[] pa=project(a),pb=project(b);
			points.add(pa);
			subdivideEdge(a,b,pa,pb,points,0);
		}
		return points.toArray(new double[0][]);
	}

	/** Append intermediate samples; the next edge contributes the endpoint. */
	private void subdivideEdge(Vector3D a,Vector3D b,double[] pa,double[] pb,List<double[]> points,int depth) {
		Vector3D middle=new Vector3D(0.5,a,0.5,b);
		double[] pm=project(middle);
		if (depth >= 12 || a.distance(b) <= edgeStep
				&& Line2D.ptSegDist(pa[0],pa[1],pb[0],pb[1],pm[0],pm[1]) <= FOOTPRINT_TOLERANCE) return;
		subdivideEdge(a,middle,pa,pm,points,depth+1);
		points.add(pm);
		subdivideEdge(middle,b,pm,pb,points,depth+1);
	}

	/** A smoothed graph in the plane's along/normal cross-section; no down-dip rotation. */
	private static final class CrossSection {
		private final BestFitSurfacePlane plane;
		private final PolynomialSplineFunction profile;
		private final PolynomialFunction[] pieces,slopes;
		private final double[] knots,arc,minHeight,maxHeight;

		CrossSection(GriddedSurfaceGeometry geometry,BestFitSurfacePlane plane,double window) {
			Preconditions.checkArgument(Double.isFinite(window) && window > 0,"Invalid ribbon smoothing window: %s",window);
			this.plane=plane;
			// Exact planar behavior is important for skew, triangles and boundary
			// cutouts. The tolerance is the existing 1 cm Cartesian-plane tolerance.
			if (plane.maxDistance <= 1e-5) {
				profile=null; pieces=null; slopes=null; knots=null; arc=null; minHeight=null; maxHeight=null;
				return;
			}
			double[] x=new double[geometry.cols],z=new double[geometry.cols],weights=new double[geometry.cols];
			for (int c=0; c<geometry.cols; c++) {
				Vector3D middle=geometry.point((geometry.rows-1)*0.5,c);
				x[c]=plane.project(middle)[0]; z[c]=plane.normalCoordinate(middle);
				ReferenceFrameException.check(c == 0 || x[c]-x[c-1] > 1e-9,
						"Ribbon middle-trace cross-section doubles back at column %s; a single along-strike graph is required",c);
				if (c > 0) {
					double length=Math.hypot(x[c]-x[c-1],z[c]-z[c-1]);
					weights[c-1]+=length/2; weights[c]+=length/2;
				}
			}
			int count=Math.max(5,(int)Math.ceil((x[x.length-1]-x[0])/(window/4))+1);
			knots=new double[count]; double[] height=new double[count];
			for (int i=0; i<count; i++) {
				knots[i]=x[0]+(x[x.length-1]-x[0])*i/(count-1d);
				height[i]=smoothHeight(x,z,weights,knots[i],window/2);
			}
			profile=new AkimaSplineInterpolator().interpolate(knots,height);
			pieces=profile.getPolynomials(); slopes=new PolynomialFunction[pieces.length];
			arc=new double[count]; minHeight=new double[pieces.length]; maxHeight=new double[pieces.length];
			for (int i=0; i<pieces.length; i++) {
				slopes[i]=pieces[i].polynomialDerivative();
				double width=knots[i+1]-knots[i];
				arc[i+1]=arc[i]+arcLength(i,width);
				minHeight[i]=Math.min(pieces[i].value(0),pieces[i].value(width));
				maxHeight[i]=Math.max(pieces[i].value(0),pieces[i].value(width));
				// Include cubic extrema in the closest-point search bounds.
				double[] d=slopes[i].getCoefficients();
				double a=d.length > 2 ? d[2] : 0,b=d.length > 1 ? d[1] : 0,c=d[0];
				if (Math.abs(a) < 1e-15) {
					if (Math.abs(b) > 1e-15) includeExtremum(i,-c/b,width);
				} else if (b*b-4*a*c >= 0) {
					double disc=Math.sqrt(b*b-4*a*c);
					includeExtremum(i,(-b-disc)/(2*a),width); includeExtremum(i,(-b+disc)/(2*a),width);
				}
			}
		}

		private void includeExtremum(int i,double t,double width) {
			if (t > 0 && t < width) {
				double z=pieces[i].value(t);
				minHeight[i]=Math.min(minHeight[i],z); maxHeight[i]=Math.max(maxHeight[i],z);
			}
		}

		/** Local linear fit, weighted by trace length so dense vertices do not dominate. */
		private static double smoothHeight(double[] x,double[] z,double[] weights,double at,double radius) {
			double w=0,wx=0,wxx=0,wz=0,wxz=0;
			for (int i=0; i<x.length; i++) {
				double dx=x[i]-at,q=dx/radius;
				if (Math.abs(q) >= 1) continue;
				double weight=weights[i]*Math.pow(1-q*q,2);
				w+=weight; wx+=weight*dx; wxx+=weight*dx*dx; wz+=weight*z[i]; wxz+=weight*dx*z[i];
			}
			double det=w*wxx-wx*wx;
			if (det > 1e-15*w*w) return (wz*wxx-wxz*wx)/det;
			// A coarse trace may have fewer than two points in this window.
			int found=Arrays.binarySearch(x,at);
			int i=Math.max(0,Math.min(x.length-2,found >= 0 ? found : -found-2));
			return z[i]+(z[i+1]-z[i])*(at-x[i])/(x[i+1]-x[i]);
		}

		double[] project(Vector3D point) {
			double[] coordinates=plane.project(point);
			if (profile == null) return coordinates;
			double x=coordinates[0],z=plane.normalCoordinate(point);
			// Tangent extensions retain skewed ends and protruding patch corners.
			double leftSlope=slopes[0].value(0),rightWidth=knots[knots.length-1]-knots[knots.length-2];
			int last=pieces.length-1;
			double rightSlope=slopes[last].value(rightWidth);
			double left=Math.min(0,(x-knots[0]+leftSlope*(z-pieces[0].value(0)))/(1+leftSlope*leftSlope));
			double right=Math.max(0,(x-knots[knots.length-1]+rightSlope*(z-pieces[last].value(rightWidth)))/(1+rightSlope*rightSlope));
			double best=squared(x-(knots[0]+left))+squared(z-(pieces[0].value(0)+leftSlope*left));
			double station=left*Math.sqrt(1+leftSlope*leftSlope);
			double rightDistance=squared(x-(knots[knots.length-1]+right))+squared(z-(pieces[last].value(rightWidth)+rightSlope*right));
			if (rightDistance < best) { best=rightDistance; station=arc[arc.length-1]+right*Math.sqrt(1+rightSlope*rightSlope); }
			for (int i=0; i<pieces.length; i++) {
				double dx=Math.max(0,Math.max(knots[i]-x,x-knots[i+1]));
				double dz=Math.max(0,Math.max(minHeight[i]-z,z-maxHeight[i]));
				if (dx*dx+dz*dz > best+1e-14) continue;
				final int segment=i;
				var result=new BrentOptimizer(1e-10,1e-12).optimize(new MaxEval(100),
						new UnivariateObjectiveFunction(t -> squared(knots[segment]+t-x)+squared(pieces[segment].value(t)-z)),
						GoalType.MINIMIZE,new SearchInterval(0,knots[i+1]-knots[i]));
				if (result.getValue() < best) { best=result.getValue(); station=arc[i]+arcLength(i,result.getPoint()); }
			}
			// The down-dip coordinate retains ONE global origin; middle-trace
			// depth variations and cutouts therefore do not get straightened away.
			return new double[] {station,coordinates[1]};
		}

		/** Composite Simpson quadrature of sqrt(1 + slope^2) over a spline piece. */
		private double arcLength(int i,double end) {
			double sum=0;
			for (int k=0; k<=16; k++) {
				double slope=slopes[i].value(end*k/16);
				sum+=(k == 0 || k == 16 ? 1 : k%2 == 0 ? 2 : 4)*Math.sqrt(1+slope*slope);
			}
			return sum*end/48;
		}

		private static double squared(double x) { return x*x; }
	}
}
