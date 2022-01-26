package org.opensha.sha.faultSurface;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;

/**
 * <p>Title:EvenlyGridCenteredSurface </p>
 *
 * <p>Description:Creates a Evenly GridCentered Surface.
 * Creates EvenlyGriddedSurface that has one less
 * row and col then the original surface. It averages the 4 corner location
 * on each grid surface to get the grid centered location.
 </p>
 *
 * @author Edward Field, Nitin Gupta
 * @version 1.0
 */
public class EvenlyGridCenteredSurface extends AbstractEvenlyGriddedSurfaceWithSubsets {


	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private EvenlyGriddedSurface origSurface;

	/**
	 * Class constructor that takes in a EvenGriddedSurface and computes a EvenlyGridCentered
	 * Surface.
	 * @param surface EvenlyGriddedSurface
	 */
	public EvenlyGridCenteredSurface(EvenlyGriddedSurface surface) {
		if (surface instanceof FrankelGriddedSurface)
			throw new UnsupportedOperationException(
					"Grid-Centered Surface not defined " +
			"for Frankel surface");
		this.origSurface = surface;
		this.gridSpacingAlong = surface.getGridSpacingAlongStrike();
		this.gridSpacingDown = surface.getGridSpacingDownDip();
		this.sameGridSpacing = surface.isGridSpacingSame();

		getGridCenteredSurface();
	}

	/**
	 * Empty constructor for cloning
	 */
	private EvenlyGridCenteredSurface() {
		
	}

	/**
	 * Returns the grid centered location on each grid surface.
	 * @param surface EvenlyGriddedSurface surface for which grid centered surface
	 * needs to be computed.
	 *

	 */
	private void getGridCenteredSurface() {

		int numRows = origSurface.getNumRows() - 1;
		int numCols = origSurface.getNumCols() - 1;
		setNumRowsAndNumCols(numRows, numCols);
		for (int i = 0; i < numRows; ++i) {
			for (int j = 0; j < numCols; ++j) {
				Location loc;
				Location loc1 = origSurface.getLocation(i, j);
				Location loc2 = origSurface.getLocation(i, j + 1);
				Location loc3 = origSurface.getLocation(i + 1, j);
				Location loc4 = origSurface.getLocation(i + 1, j + 1);
				double locLat = (loc1.getLatitude() + loc2.getLatitude() +
						loc3.getLatitude() +
						loc4.getLatitude()) / 4;
				double locLon = (loc1.getLongitude() + loc2.getLongitude() +
						loc3.getLongitude() +
						loc4.getLongitude()) / 4;
				double locDepth = (loc1.getDepth() + loc2.getDepth() + loc3.getDepth() +
						loc4.getDepth()) / 4;
				loc = new Location(locLat, locLon, locDepth);
				set(i, j, loc);
			}
		}
	}

	/**
	 * This returns the original surface
	 * @return EvenlyGriddedSurfaceAPI
	 */
	public RuptureSurface getOrigSurface() {return origSurface; }

	@Override
	public double getAveStrike() { return origSurface.getAveStrike(); }

	@Override
	public double getAveDip() { return origSurface.getAveDip(); }


	@Override
	public double getAveDipDirection() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getAveRupTopDepth() {
		return origSurface.getAveRupTopDepth()+gridSpacingDown/2;
	}


	@Override
	protected AbstractEvenlyGriddedSurface getNewInstance() {
		EvenlyGridCenteredSurface surf = new EvenlyGridCenteredSurface();
		surf.setNumRowsAndNumCols(numRows, numCols);
		surf.gridSpacingAlong = getGridSpacingAlongStrike();
		surf.gridSpacingDown = getGridSpacingDownDip();
		return surf;
	}

	@Override
	public double getAreaInsideRegion(Region region) {
		double gridSpacingDown = getGridSpacingDownDip();
		double gridSpacingAlong = getGridSpacingAlongStrike();
		double areaEach = gridSpacingAlong * gridSpacingDown;
		// this is not simply trivial because we are not grid centered
		double areaInside = 0d;
		for (int row=0; row<getNumRows(); row++) {
			for (int col=0; col<getNumCols(); col++) {
				if (region.contains(get(row, col)))
					areaInside += areaEach;
			}
		}
		return areaInside;
	}

}
