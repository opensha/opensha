package org.opensha.sha.simulators.stiffness.patches;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.utm.UTM;
import org.opensha.commons.geo.utm.WGS84;
import org.opensha.sha.earthquake.FocalMechanism;

/**
 * Shared source/receiver geometry. Cartesian coordinates are East/North/Up in
 * meters, areas are square meters, and mechanism angles are radians. Instances
 * and their coordinate arrays must be treated as read-only after construction.
 */
public abstract class StiffnessPatch {
	public final double[] center;
	public final double[][] corner;
	public final double area, strike, dip, rake;
	/** Unit normal and hanging-wall slip direction. */
	public final double[] nu, u;

	protected StiffnessPatch(double[] center, double area, double[][] corners, FocalMechanism mechanism) {
		this.center = center;
		this.area = area;
		this.corner = corners;
		strike = Math.toRadians(mechanism.getStrike());
		dip = Math.toRadians(mechanism.getDip());
		rake = Math.toRadians(mechanism.getRake());
		double[] along = {Math.sin(strike),Math.cos(strike),0};
		double[] upDip = {-Math.cos(dip)*Math.cos(strike),Math.cos(dip)*Math.sin(strike),Math.sin(dip)};
		nu = cross(along,upDip);
		u = new double[3];
		for (int i=0; i<3; i++) u[i] = Math.cos(rake)*along[i]+Math.sin(rake)*upDip[i];
	}

	public static double[] cartesian(Location location, int zone, char letter) {
		UTM utm = new UTM(new WGS84(location.getLatitude(),location.getLongitude()),zone,letter);
		return new double[] {utm.getEasting(),utm.getNorthing(),-1000*location.getDepth()};
	}

	protected static double[] cross(double[] a, double[] b) {
		return new double[] {a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};
	}
}
