package org.opensha.sha.faultSurface;

import org.opensha.commons.data.Container2D;
import org.opensha.commons.geo.Location;


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
	
}
