package org.opensha.commons.data.xyzw;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;

/**
 * Interface for a geographic XYZW dataset backed by {@link Location} objects.
 */
public interface GeoXYZW_DataSet extends XYZW_DataSet {

	public double getMinLat();

	public double getMaxLat();

	public double getMinLon();

	public double getMaxLon();

	public void set(Location loc, double value);

	public void add(Location loc, double value);

	public double get(Location loc);

	public int indexOf(Location loc);

	public Location getLocation(int index);

	public boolean contains(Location loc);

	public LocationList getLocationList();

	@Override
	public GeoXYZW_DataSet copy();
}
