package org.opensha.sha.simulators.stiffness;

import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.opensha.sha.simulators.stiffness.GriddedSurfaceGeometry.SurfacePoint;

import com.google.common.base.Preconditions;

/**
 * Orthogonal projection into an area-weighted best-fit plane, in km. Planar
 * geometry is preserved regardless of the input grid's skew or boundary shape.
 * Curved surfaces retain their actual 3D geometry through inverse cell lookup;
 * projection distortion is measured, and folded/ambiguous projections rejected.
 */
final class SurfaceReferenceFrame {
	private static final double EPS=1e-7;
	final GriddedSurfaceGeometry geometry;
	final double[][] nodeX, nodeY;
	final double minX=0, minY=0, maxX, maxY;
	final double minimumEdgeRatio, minimumAreaRatio, rmsPlaneDistance;
	private final Vector3D origin, along, down;
	private final double offsetX, offsetY;
	private final List<double[]> boundary=new ArrayList<>();
	private final Path2D.Double outline=new Path2D.Double();
	private final Area domain;
	private final Map<Long,List<int[]>> buckets=new HashMap<>();
	private final double bucketSize;

	SurfaceReferenceFrame(GriddedSurfaceGeometry geometry) {
		this.geometry=geometry;
		// Accumulate exact first/second moments of uniformly weighted triangles.
		// Work relative to a nearby origin to avoid cancellation in UTM coordinates.
		Vector3D base=geometry.point(0,0), sum=Vector3D.ZERO, meanNormal=Vector3D.ZERO;
		double[][] moment=new double[3][3];
		double area=0;
		for (int r=0; r<geometry.rows-1; r++) for (int c=0; c<geometry.cols-1; c++) {
			Vector3D a=geometry.point(r,c).subtract(base), b=geometry.point(r,c+1).subtract(base);
			Vector3D d=geometry.point(r+1,c).subtract(base), e=geometry.point(r+1,c+1).subtract(base);
			for (Vector3D[] triangle : new Vector3D[][] {{a,b,e},{a,e,d}}) {
				Vector3D normal=triangle[2].subtract(triangle[0]).crossProduct(triangle[1].subtract(triangle[0]));
				double weight=normal.getNorm()*0.5;
				Vector3D total=triangle[0].add(triangle[1]).add(triangle[2]);
				sum=sum.add(total.scalarMultiply(weight/3));
				meanNormal=meanNormal.add(normal);
				area+=weight;
				double[] t=total.toArray();
				double[][] vertices={triangle[0].toArray(),triangle[1].toArray(),triangle[2].toArray()};
				for (int i=0; i<3; i++) for (int j=0; j<3; j++) {
					double value=t[i]*t[j];
					for (double[] vertex : vertices) value+=vertex[i]*vertex[j];
					moment[i][j]+=weight*value/12;
				}
			}
		}
		Preconditions.checkArgument(area > EPS*EPS,"Surface has no area");
		Vector3D mean=sum.scalarMultiply(1/area);
		double[] meanCoordinates=mean.toArray();
		for (int i=0; i<3; i++) for (int j=0; j<3; j++)
			moment[i][j]=moment[i][j]/area-meanCoordinates[i]*meanCoordinates[j];
		EigenDecomposition eigen=new EigenDecomposition(new Array2DRowRealMatrix(moment));
		int smallest=0;
		for (int i=1; i<3; i++) if (eigen.getRealEigenvalue(i) < eigen.getRealEigenvalue(smallest)) smallest=i;
		Vector3D normal=new Vector3D(eigen.getEigenvector(smallest).toArray()).normalize();
		if (normal.dotProduct(meanNormal) < 0) normal=normal.negate();
		origin=base.add(mean);
		rmsPlaneDistance=Math.sqrt(Math.max(0,eigen.getRealEigenvalue(smallest)));
		Vector3D direction=geometry.point((geometry.rows-1)*0.5,geometry.cols-1)
				.subtract(geometry.point((geometry.rows-1)*0.5,0));
		Vector3D strike=new Vector3D(-normal.getY(),normal.getX(),0);
		if (strike.getNorm() < 1e-8) strike=direction.subtract(normal.scalarMultiply(direction.dotProduct(normal)));
		Preconditions.checkArgument(strike.getNorm() > EPS,"Undefined reference strike");
		strike=strike.normalize();
		if (strike.dotProduct(direction) < 0) strike=strike.negate();
		along=strike;
		down=along.crossProduct(normal).normalize();
		nodeX=new double[geometry.rows][geometry.cols];
		nodeY=new double[geometry.rows][geometry.cols];
		double lowX=Double.POSITIVE_INFINITY, lowY=lowX, highX=-lowX, highY=-lowX;
		for (int r=0; r<geometry.rows; r++) for (int c=0; c<geometry.cols; c++) {
			Vector3D p=geometry.point(r,c).subtract(origin);
			nodeX[r][c]=p.dotProduct(along); nodeY[r][c]=p.dotProduct(down);
			lowX=Math.min(lowX,nodeX[r][c]); highX=Math.max(highX,nodeX[r][c]);
			lowY=Math.min(lowY,nodeY[r][c]); highY=Math.max(highY,nodeY[r][c]);
		}
		offsetX=lowX; offsetY=lowY;
		maxX=highX-lowX; maxY=highY-lowY;
		for (int r=0; r<geometry.rows; r++) for (int c=0; c<geometry.cols; c++) {
			nodeX[r][c]-=lowX; nodeY[r][c]-=lowY;
		}
		for (int c=0; c<geometry.cols; c++) addBoundary(0,c);
		for (int r=1; r<geometry.rows; r++) addBoundary(r,geometry.cols-1);
		for (int c=geometry.cols-2; c>=0; c--) addBoundary(geometry.rows-1,c);
		for (int r=geometry.rows-2; r>0; r--) addBoundary(r,0);
		if (distance(boundary.get(0),boundary.get(boundary.size()-1)) < EPS) boundary.remove(boundary.size()-1);
		for (int i=0; i<boundary.size(); i++) {
			double[] a=boundary.get(i), b=boundary.get((i+1)%boundary.size());
			if (i == 0) outline.moveTo(a[0],a[1]); else outline.lineTo(a[0],a[1]);
			for (int j=i+2; j<boundary.size(); j++) {
				if (i == 0 && j == boundary.size()-1) continue;
				double[] c=boundary.get(j), d=boundary.get((j+1)%boundary.size());
				Preconditions.checkArgument(!Line2D.linesIntersect(a[0],a[1],b[0],b[1],c[0],c[1],d[0],d[1]),
						"Reference-plane projection has a self-intersecting boundary; use an intrinsic unwrapping");
			}
		}
		outline.closePath();
		domain=new Area(outline);
		bucketSize=Math.max(EPS,Math.max(maxX,maxY)/Math.max(1,Math.sqrt((geometry.rows-1d)*(geometry.cols-1d))));
		double edgeRatio=1, areaRatio=1;
		for (int r=0; r<geometry.rows; r++) for (int c=0; c<geometry.cols; c++) {
			if (r > 0) edgeRatio=Math.min(edgeRatio,edgeRatio(r-1,c,r,c));
			if (c > 0) edgeRatio=Math.min(edgeRatio,edgeRatio(r,c-1,r,c));
			if (r == geometry.rows-1 || c == geometry.cols-1) continue;
			double projectedArea=0;
			for (int v=0; v<=1; v++) for (int u=0; u<=1; u++) {
				double ax=nodeX[r+v][c+1]-nodeX[r+v][c], ay=nodeY[r+v][c+1]-nodeY[r+v][c];
				double dx=nodeX[r+1][c+u]-nodeX[r][c+u], dy=nodeY[r+1][c+u]-nodeY[r][c+u];
				double det=ax*dy-ay*dx;
				Preconditions.checkArgument(det >= -EPS*EPS,
						"Reference-plane projection folds cell (%s,%s); use an intrinsic unwrapping",r,c);
				projectedArea+=det*0.25;
			}
			Preconditions.checkArgument(projectedArea > EPS*EPS,"Reference-plane projection collapses cell (%s,%s)",r,c);
			areaRatio=Math.min(areaRatio,projectedArea/geometry.cellArea(r,c));
			double x0=Math.min(Math.min(nodeX[r][c],nodeX[r+1][c]),Math.min(nodeX[r][c+1],nodeX[r+1][c+1]));
			double x1=Math.max(Math.max(nodeX[r][c],nodeX[r+1][c]),Math.max(nodeX[r][c+1],nodeX[r+1][c+1]));
			double y0=Math.min(Math.min(nodeY[r][c],nodeY[r+1][c]),Math.min(nodeY[r][c+1],nodeY[r+1][c+1]));
			double y1=Math.max(Math.max(nodeY[r][c],nodeY[r+1][c]),Math.max(nodeY[r][c+1],nodeY[r+1][c+1]));
			for (int bx=bucket(x0); bx<=bucket(x1); bx++) for (int by=bucket(y0); by<=bucket(y1); by++)
				buckets.computeIfAbsent(key(bx,by),k -> new ArrayList<>()).add(new int[] {r,c});
		}
		minimumEdgeRatio=edgeRatio;
		minimumAreaRatio=areaRatio;
	}

	double[] project(Vector3D point) {
		Vector3D p=point.subtract(origin);
		return new double[] {p.dotProduct(along)-offsetX,p.dotProduct(down)-offsetY};
	}

	boolean contains(double x,double y) {
		return outline.contains(x,y) || distanceToBoundary(x,y) <= EPS;
	}

	double distanceToBoundary(double x,double y) {
		double distance=Double.POSITIVE_INFINITY;
		for (int i=0; i<boundary.size(); i++) {
			double[] a=boundary.get(i),b=boundary.get((i+1)%boundary.size());
			distance=Math.min(distance,Line2D.ptSegDist(a[0],a[1],b[0],b[1],x,y));
		}
		return distance;
	}

	/**
	 * Initial center constraint from the depth planes in the representative frame.
	 * Exact for planar faults; the builder subsequently checks every physical
	 * corner against the actual depth bounds for curved faults as well.
	 */
	private Area depthCenterDomain(double spacing) {
		Area centers=new Area(domain);
		if (Math.abs(down.getZ()) < 1e-8) return centers;
		double shallow=Math.max(0,geometry.minDepth), deep=geometry.maxDepth;
		double halfDepth=0.5*spacing*Math.abs(down.getZ());
		double a=(-shallow-halfDepth-origin.getZ())/down.getZ()-offsetY;
		double b=(-deep+halfDepth-origin.getZ())/down.getZ()-offsetY;
		double low=down.getZ() < 0 ? a : b, high=down.getZ() < 0 ? b : a;
		// An undersized depth range is explicitly allowed to spill vertically.
		if (high-low < -EPS) return centers;
		if (high-low < 2*EPS) { double middle=(high+low)*0.5; low=middle-EPS; high=middle+EPS; }
		centers.intersect(new Area(new Rectangle2D.Double(-EPS,low,maxX+2*EPS,high-low)));
		if (centers.isEmpty()) return new Area(domain); // physical per-patch check remains authoritative
		return centers;
	}

	/** Initial joint bounds, exact for rectangular patches on a planar surface. */
	Area centerDomain(double spacing) {
		return centerDomain(spacing,spacing*0.5);
	}

	/** Refine the along-strike margin using the actual projected half-extent. */
	Area centerDomain(double spacing,double halfWidth) {
		Area centers=depthCenterDomain(spacing);
		if (2*halfWidth > maxX-minX+1e-9) return centers; // undersized surface exception
		double low=minX+halfWidth,high=maxX-halfWidth;
		if (high-low < 2*EPS) { double middle=(high+low)*0.5; low=middle-EPS; high=middle+EPS; }
		centers.intersect(new Area(new Rectangle2D.Double(low,-EPS,high-low,maxY+2*EPS)));
		Preconditions.checkArgument(!centers.isEmpty(),"No patch centers satisfy joint along-strike and depth bounds");
		return centers;
	}

	/** Nearest point in a constrained center domain, potentially with multiple contours. */
	double[] inside(double x,double y,Area centers) {
		if (centers.contains(x,y)) return new double[] {x,y};
		PathIterator iterator=centers.getPathIterator(null);
		double[] coordinates=new double[6];
		double firstX=0,firstY=0,lastX=0,lastY=0,best=Double.POSITIVE_INFINITY,px=x,py=y;
		while (!iterator.isDone()) {
			int type=iterator.currentSegment(coordinates);
			if (type == PathIterator.SEG_MOVETO) {
				firstX=lastX=coordinates[0]; firstY=lastY=coordinates[1];
			} else {
				double endX=type == PathIterator.SEG_CLOSE ? firstX : coordinates[0];
				double endY=type == PathIterator.SEG_CLOSE ? firstY : coordinates[1];
				double dx=endX-lastX,dy=endY-lastY;
				if (dx*dx+dy*dy > EPS*EPS) {
					double t=Math.max(0,Math.min(1,((x-lastX)*dx+(y-lastY)*dy)/(dx*dx+dy*dy)));
					double xx=lastX+t*dx,yy=lastY+t*dy,d=Math.hypot(xx-x,yy-y);
					if (d < best) { best=d; px=xx; py=yy; }
				}
				lastX=endX; lastY=endY;
			}
			iterator.next();
		}
		return new double[] {px,py};
	}

	/** Intersections of a horizontal or vertical line, including disconnected runs. */
	List<double[]> intervals(double transverse,boolean horizontal) {
		List<Double> crossings=new ArrayList<>();
		for (int i=0; i<boundary.size(); i++) {
			double[] a=boundary.get(i),b=boundary.get((i+1)%boundary.size());
			double at=horizontal ? a[1] : a[0], bt=horizontal ? b[1] : b[0];
			if ((at <= transverse && bt > transverse) || (bt <= transverse && at > transverse)) {
				double aa=horizontal ? a[0] : a[1], ba=horizontal ? b[0] : b[1];
				crossings.add(aa+(ba-aa)*(transverse-at)/(bt-at));
			}
		}
		crossings.sort(Comparator.naturalOrder());
		List<double[]> intervals=new ArrayList<>();
		for (int i=0; i+1<crossings.size(); i+=2)
			if (crossings.get(i+1)-crossings.get(i) > EPS) intervals.add(new double[] {crossings.get(i),crossings.get(i+1)});
		return intervals;
	}

	SurfacePoint sample(double x,double y) {
		double[] indices=indices(x,y);
		return geometry.interpolate(indices[0],indices[1]);
	}

	Vector3D point(double x,double y) {
		double[] indices=indices(x,y);
		return geometry.point(indices[0],indices[1]);
	}

	private double[] indices(double x,double y) {
		List<int[]> candidates=buckets.get(key(bucket(Math.max(0,x)),bucket(Math.max(0,y))));
		if (candidates != null) for (int[] cell : candidates) {
			int r=cell[0], c=cell[1];
			double u=0.5,v=0.5;
			for (int iter=0; iter<25; iter++) {
				double px=bilinear(nodeX,r,c,u,v), py=bilinear(nodeY,r,c,u,v);
				double ex=px-x,ey=py-y;
				if (Math.hypot(ex,ey) < 1e-9 && u >= -EPS && u <= 1+EPS && v >= -EPS && v <= 1+EPS)
					return new double[] {r+Math.max(0,Math.min(1,v)),c+Math.max(0,Math.min(1,u))};
				double ax=(1-v)*(nodeX[r][c+1]-nodeX[r][c])+v*(nodeX[r+1][c+1]-nodeX[r+1][c]);
				double ay=(1-v)*(nodeY[r][c+1]-nodeY[r][c])+v*(nodeY[r+1][c+1]-nodeY[r+1][c]);
				double dx=(1-u)*(nodeX[r+1][c]-nodeX[r][c])+u*(nodeX[r+1][c+1]-nodeX[r][c+1]);
				double dy=(1-u)*(nodeY[r+1][c]-nodeY[r][c])+u*(nodeY[r+1][c+1]-nodeY[r][c+1]);
				double det=ax*dy-ay*dx;
				if (Math.abs(det) < 1e-15) break;
				u-=(ex*dy-ey*dx)/det;
				v-=(ax*ey-ay*ex)/det;
				if (Math.abs(u) > 10 || Math.abs(v) > 10) break;
			}
		}
		throw new IllegalArgumentException("Cannot invert reference-plane point ("+x+","+y+")");
	}

	private void addBoundary(int r,int c) {
		double[] point={nodeX[r][c],nodeY[r][c]};
		if (boundary.isEmpty() || distance(boundary.get(boundary.size()-1),point) > EPS) boundary.add(point);
	}

	private double edgeRatio(int r,int c,int rr,int cc) {
		double length=geometry.point(r,c).distance(geometry.point(rr,cc));
		return length < EPS ? 1 : Math.hypot(nodeX[r][c]-nodeX[rr][cc],nodeY[r][c]-nodeY[rr][cc])/length;
	}

	private static double bilinear(double[][] values,int r,int c,double u,double v) {
		return (1-v)*((1-u)*values[r][c]+u*values[r][c+1])+v*((1-u)*values[r+1][c]+u*values[r+1][c+1]);
	}

	private int bucket(double value) { return (int)Math.floor(value/bucketSize); }
	private static long key(int x,int y) { return ((long)x << 32) ^ (y & 0xffffffffL); }
	private static double distance(double[] a,double[] b) { return Math.hypot(a[0]-b[0],a[1]-b[1]); }
}
