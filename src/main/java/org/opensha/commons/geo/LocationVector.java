package org.opensha.commons.geo;

/**
 * This class encapsulates information describing a vector between two
 * <code>Location</code>s. This vector is defined by the azimuth (bearing) from
 * a point p1 to a point p2, and also by the horizontal and vertical separation
 * between the points. Note that a <code>LocationVector</code> from point A to
 * point B is not the complement of that from point B to A. Although the
 * horizontal and vertical components will be the same, the azimuth will likely
 * change by some value other than 180&#176;.<br/>
 * <br/>
 * <b>Note:</b> Although a <code>LocationVector</code> will function in any
 * reference frame, the convention in seismology and that adopted in OpenSHA is
 * for depth to be positive down.<br/>
 * 
 * @author Peter Powers
 * @author Sid Hellman
 * @author Steven W. Rock
 * @version $Id: LocationVector.java 9760 2012-12-05 18:32:55Z pmpowers $
 */
// TODO refactor horx vert method names
// TODO should store azimuth in radians
// <b>Note:</b> Azimuth is stored internally in radians for computational
// convenience. Be sure to use the {@link #getAzimuth()} (decimal-degrees) or
// {@link #getAzimuthRad()} (radians) where appropriate.
public class LocationVector {

	/*
	 * Developer Notes: The previous version of this class as 'LocationVector'
	 * included back azimuth. There are (were) instances in OpenSHA where this
	 * was taken to be the azimuth from point B to A (for an azimuth from point
	 * A to B). As Back azimuth is generally defined, this interp is incorrect.
	 * Back azimuth is the 180 degree complement of the azimuth at an origin
	 * point. Under the assumed interpretation, each LocationVector was
	 * implicitely Location dependent, but that information was never stored as
	 * part of this class. Furthermore, the onus was on the user to provide the
	 * correct value for back azimuth. This property of the class has been
	 * removed and users are directed in LocationUtils.azimuth() to simply
	 * reverse the points of interest if the bearing from B to A is required.
	 */

	private double azimuth;
	private double vertical;
	private double horizontal;

	/**
	 * Initializes a new <code>LocationVector</code> with azimuth and horizontal
	 * and vertical components all initialized to 0.
	 */
	public LocationVector() {}

	/**
	 * Initializes a new <code>LocationVector</code> with the supplied values.
	 * Note that <code>azimuth</code> is expected in <i>decimal degrees</i>.
	 * 
	 * @param azimuth value to set in <i>decimal degrees</i>
	 * @param horizontal component value to set
	 * @param vertical component value to set
	 */
	public LocationVector(double azimuth, double horizontal, double vertical) {
		set(azimuth, horizontal, vertical);
	}

	/**
	 * Sets this <code>LocationVector</code>'s internal fields to the supplied
	 * values. Note that <code>azimuth</code> is expected in <i>decimal
	 * degrees</i>.
	 * 
	 * @param azimuth value to set in <i>decimal degrees</i>
	 * @param horizontal component value to set
	 * @param vertical component value to set
	 */
	public void set(double azimuth, double horizontal, double vertical) {
		this.azimuth = azimuth;
		this.horizontal = horizontal;
		this.vertical = vertical;
	}

	/**
	 * Reverses the azimuth (to 'back-azimuth') and flips the sign of the
	 * vertical component of this <code>LocationVector</code>.
	 */
	public void reverse() {
		azimuth = (azimuth + 180) % 360;
		vertical = -vertical;
	}

	/**
	 * Returns the azimuth of this <code>LocationVector</code> in decimal
	 * degrees.
	 * @return the azimuth value in decimal degrees
	 * @see #getAzimuthRad()
	 */
	public double getAzimuth() {
		return azimuth;
		// return azimuth * LocationUtils.TO_DEG;
	}

	/**
	 * Returns the azimuth of this <code>LocationVector</code> in radians.
	 * @return the azimuth value in radians
	 * @see #getAzimuth()
	 */
	public double getAzimuthRad() {
		return azimuth * GeoTools.TO_RAD;
	}

	/**
	 * Sets the azimuth of this <code>LocationVector</code>.
	 * @param azimuth value to set in <i>decimal degrees</i>
	 */
	public void setAzimuth(double azimuth) {
		this.azimuth = azimuth;
	}
	
	/**
	 * Returns the angle (in decimal degrees) between this vector and the
	 * horizontal based on the current internal vertical and horizontal
	 * separation values. This method is intended for use at relatively short
	 * separations ( e.g. &lteq; 200km) as it degrades at large distances where
	 * curvature is not considered. Note that positive angles are down, negative
	 * angles are up.
	 * @return the plunge of this vector
	 */
	public double getPlunge() {
		return Math.atan(vertical / horizontal) * GeoTools.TO_DEG;
	}

	/**
	 * Gets the vertical component of this <code>LocationVector</code>.
	 * @return the vertical component value in km
	 */
	public double getVertDistance() {
		return vertical;
	}

	/**
	 * Sets the vertical component of this <code>LocationVector</code>.
	 * @param vertical component value to set in km
	 */
	public void setVertDistance(double vertical) {
		this.vertical = vertical;
	}

	/**
	 * Gets the horizontal component of this <code>LocationVector</code>.
	 * @return the horizontal component value in km
	 */
	public double getHorzDistance() {
		return horizontal;
	}

	/**
	 * Sets the horizontal component of this <code>LocationVector</code>.
	 * @param horizontal component value to set in km
	 */
	public void setHorzDistance(double horizontal) {
		this.horizontal = horizontal;
	}

	@Override
	public String toString() {
		StringBuffer b = new StringBuffer();
		b.append(this.getClass().getSimpleName());
		b.append(":  az = ");
		b.append(getAzimuth());
		b.append("  dH = ");
		b.append(horizontal);
		b.append("  dV = ");
		b.append(vertical);
		return b.toString();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof LocationVector) {
			LocationVector dir = (LocationVector) obj;
			if (horizontal != dir.horizontal) return false;
			if (vertical != dir.vertical) return false;
			if (azimuth != dir.azimuth) return false;
			return true;
		}
		return false;
	}

}
