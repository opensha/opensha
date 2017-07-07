/**
 * 
 */
package org.opensha.sha.faultSurface;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;

/**
 * This class represents an evenly gridded surface composed of four Locations.
 * 
 * @author field
 */
public class FourPointEvenlyGriddedSurface extends AbstractEvenlyGriddedSurface {

	// for debugging
	private final static boolean D = false;

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The constructs the surface from the Locations given (counter clockwise 
	 * when looking at surface from positive side), where no sub-discretization
	 * is applied.  This gridSpacingAlong is the average between the top two and bottom
	 * two points, and likewise for gridSpacingDown.
	 * @param upperLeft
	 * @param lowerLeft
	 * @param lowerRight
	 * @param upperRight
	 */
	public FourPointEvenlyGriddedSurface(Location upperLeft,  Location lowerLeft, 
										 Location lowerRight, Location upperRight) {
		setNumRowsAndNumCols(2, 2);
		
		set(0, 0, upperLeft);
		set(0, 1, upperRight);
		set(1, 0, lowerLeft);
		set(1, 1, lowerRight);
		
		gridSpacingAlong = (LocationUtils.linearDistanceFast(getLocation(0, 0), getLocation(0, 1)) +
							LocationUtils.linearDistanceFast(getLocation(1, 0), getLocation(1, 1)))/2;
		gridSpacingDown = (LocationUtils.linearDistanceFast(getLocation(0, 0), getLocation(1, 0))+
						   LocationUtils.linearDistanceFast(getLocation(0, 1), getLocation(1, 1)))/2;

		if(gridSpacingAlong == gridSpacingDown)
			sameGridSpacing = true;
		else
			sameGridSpacing = false;
	}
	
	private FourPointEvenlyGriddedSurface(int numRows, int numCols, double gridSpacingAlong, double gridSpacingDown) {
		setNumRowsAndNumCols(numRows, numCols);
		this.gridSpacingAlong = gridSpacingAlong;
		this.gridSpacingDown = gridSpacingDown;
		
		if(gridSpacingAlong == gridSpacingDown)
			sameGridSpacing = true;
		else
			sameGridSpacing = false;
	}

	@Override
	/**
	 * This returns the average dip implied by the two end-point pairs
	 */
	public double getAveDip() {
		
		double dip1 = LocationUtils.plunge(get(0,0), get(1,0));
		double dip2 = LocationUtils.plunge(get(0,1), get(1,1));
		double aveDip = (dip1+dip2)/2d;
		if(aveDip < 0.0)
			throw new RuntimeException("aveDip must be positive; the value = "+aveDip);
		
		return aveDip;
	}

	@Override
	/**
	 * This returns the average implied by the two end-point pairs
	 */
	public double getAveDipDirection() {
		double az1 = LocationUtils.azimuth(get(0,0), get(1,0));
		double az2 = LocationUtils.azimuth(get(0,1), get(1,1));
		return (az1+az2)/2d;
	}

	@Override
	public double getAveRupTopDepth() {
		return (get(0,0).getDepth()+get(0,1).getDepth())/2;
	}

	@Override
	public double getAveStrike() {
		return getUpperEdge().getAveStrike();
	}

	@Override
	protected AbstractEvenlyGriddedSurface getNewInstance() {
		return new FourPointEvenlyGriddedSurface(numRows, numCols, gridSpacingAlong, gridSpacingDown);
	}

}
