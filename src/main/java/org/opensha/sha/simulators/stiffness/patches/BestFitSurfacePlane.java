package org.opensha.sha.simulators.stiffness.patches;

import com.google.common.base.Preconditions;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;

/** Area-weighted plane fit, independent of grid-node density. All coordinates are km. */
final class BestFitSurfacePlane {
	private static final double EPS=1e-7;
	final Vector3D origin, along, down;
	private final Vector3D normal;
	final double rmsDistance, maxDistance;

	BestFitSurfacePlane(GriddedSurfaceGeometry geometry) {
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
		Vector3D fittedNormal=new Vector3D(eigen.getEigenvector(smallest).toArray()).normalize();
		normal=fittedNormal.dotProduct(meanNormal) < 0 ? fittedNormal.negate() : fittedNormal;
		origin=base.add(mean);
		rmsDistance=Math.sqrt(Math.max(0,eigen.getRealEigenvalue(smallest)));
		Vector3D direction=geometry.point((geometry.rows-1)*0.5,geometry.cols-1)
				.subtract(geometry.point((geometry.rows-1)*0.5,0));
		Vector3D strike=new Vector3D(-normal.getY(),normal.getX(),0);
		if (strike.getNorm() < EPS) strike=direction.subtract(normal.scalarMultiply(direction.dotProduct(normal)));
		Preconditions.checkArgument(strike.getNorm() > EPS,"Undefined reference strike");
		strike=strike.normalize();
		if (strike.dotProduct(direction) < 0) strike=strike.negate();
		along=strike;
		down=along.crossProduct(normal).normalize();
		double maximum=0;
		for (int r=0; r<geometry.rows; r++) for (int c=0; c<geometry.cols; c++)
			maximum=Math.max(maximum,Math.abs(geometry.point(r,c).subtract(origin).dotProduct(normal)));
		maxDistance=maximum;
	}

	double[] project(Vector3D point) {
		Vector3D p=point.subtract(origin);
		return new double[] {p.dotProduct(along),p.dotProduct(down)};
	}

	double normalCoordinate(Vector3D point) {
		return point.subtract(origin).dotProduct(normal);
	}
}
