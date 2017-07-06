package org.opensha.commons.data.xyz;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;

/**
 * Interface for a geographic XYZ dataset. These datasets are backed by <code>Location</code> objects
 * instead of <code>Point2D</code> objects. They also have the capability of storing lat, lon values
 * as x, y or y, x dependent on the latitudeX parameter.
 * 
 * @author kevin
 *
 */
public interface GeoDataSet extends XYZ_DataSet {
	
	/**
	 * Set latitudeX. If true, latitude will be stored as X, otherwise as Y.
	 * 
	 * @param latitudeX
	 */
	public void setLatitudeX(boolean latitudeX);
	
	/**
	 * Returns true if latitude will be stored as X, otherwise false if as Y.
	 * 
	 * @return true if latitude will be stored as X, false otherwise
	 */
	public boolean isLatitudeX();
	
	/**
	 * Returns the minimum latitude in the given dataset.
	 * 
	 * @return the minimum latitude, or positive infinity if the dataset is empty
	 */
	public double getMinLat();
	
	/**
	 * Returns the maximum latitude in the given dataset.
	 * 
	 * @return the maximum latitude, or negative infinity if the dataset is empty
	 */
	public double getMaxLat();
	
	/**
	 * Returns the minimum longitude in the given dataset.
	 * 
	 * @return the minimum longitude, or positive infinity if the dataset is empty
	 */
	public double getMinLon();
	
	/**
	 * Returns the maximum longitude in the given dataset.
	 * 
	 * @return the maximum longitude, or negative infinity if the dataset is empty
	 */
	public double getMaxLon();
	
	/**
	 * Set the value at the given <code>Location</code>. If the location doesn't exist in the
	 * dataset then it will be added.
	 * 
	 * @param loc - the location at which to set
	 * @param value - the value to set
	 * @throws NullPointerException if the <code>loc</code> is null
	 */
	public void set(Location loc, double value);
	
	/**
	 * Get the value at the given <code>Location</code>, or null if it doesn't exist.
	 * 
	 * @param loc - the location at which to get
	 * @return the value at the given location
	 */
	public double get(Location loc);
	
	/**
	 * Returns the index of the given location, or -1 if it doesn't exist.
	 * 
	 * @param loc - the location at which to return the index
	 * @return the index of the given location, or -1 if it isn't in the dataset.
	 */
	public int indexOf(Location loc);
	
	/**
	 * Returns the location at the given index. If index < 0 or index >= size(), an
	 * exception will be thrown.
	 * 
	 * @param index - the index at which to get the location
	 * @return the location at the given index
	 */
	public Location getLocation(int index);
	
	/**
	 * Returns true if the dataset contains the given Location, false otherwise.
	 * 
	 * @param loc - the location to test
	 * @return true if the dataset contains the given location, false otherwise
	 */
	public boolean contains(Location loc);
	
	/**
	 * Returns a list of all locations in the correct order (as defined by indexOf).
	 * 
	 * @return list of all locations in the dataset
	 */
	public LocationList getLocationList();
	
	/**
	 * Returns a shallow copy of this <code>GeoDataSet</code>. Internal points are not cloned.
	 * 
	 * @return shallow copy of this <code>GeoDataSet</code>
	 */
	@Override
	public GeoDataSet copy();

}
