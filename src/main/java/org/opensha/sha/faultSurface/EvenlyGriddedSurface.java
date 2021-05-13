package org.opensha.sha.faultSurface;

import org.opensha.commons.data.Container2D;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;


public interface EvenlyGriddedSurface extends Container2D<Location>, RuptureSurface {
	
	/**
	 * Returns the grid spacing along strike
	 * @return
	 */
	public double getGridSpacingAlongStrike();

	/**
	 * returns the grid spacing down dip
	 * @return
	 */
	public double getGridSpacingDownDip();
	
	/**
	 * tells whether along-strike and down-dip grid spacings are the same
	 * @return
	 */
	public Boolean isGridSpacingSame();
	
	/**
	 * gets the location from the 2D container
	 * @param row
	 * @param column
	 * @return
	 */
	public Location getLocation(int row, int col);
	
	/**
	 * Gets a specified row as a fault trace
	 * @param row
	 * @return
	 */
	public FaultTrace getRowAsTrace(int row);
	
	@Override
	default public double getAreaInsideRegion(Region region) {
		double gridSpacingDown = getGridSpacingDownDip();
		double gridSpacingAlong = getGridSpacingAlongStrike();
		// this is not simply trivial because we are not grid centered
		double areaInside = 0d;
		for (int row=0; row<getNumRows(); row++) {
			// it's a top or bottom so this point represents a half cell
			double myWidth = row == 0 || row == getNumRows()-1 ? 0.5*gridSpacingDown : gridSpacingDown;
			for (int col=0; col<getNumCols(); col++) {
				// it's a left or right so this point represents a half cell
				double myLen = col == 0 || col == getNumCols()-1 ? 0.5*gridSpacingAlong : gridSpacingAlong;
				if (region.contains(get(row, col)))
					areaInside += myWidth * myLen;
			}
		}
		return areaInside;
	}
	
}
