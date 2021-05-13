package org.opensha.commons.data.comcat;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;

public class ComcatRegionAdapter implements ComcatRegion {
	
	private Region region;

	public ComcatRegionAdapter(Region region) {
		this.region = region;
	}

	@Override
	public boolean contains(Location loc) {
		return region.contains(loc);
	}

	@Override
	public boolean contains(double lat, double lon) {
		return contains(new Location(lat, lon));
	}

	@Override
	public double getMinLat() {
		return region.getMinLat();
	}

	@Override
	public double getMaxLat() {
		return region.getMaxLat();
	}

	@Override
	public double getMinLon() {
		return region.getMinLon();
	}

	@Override
	public double getMaxLon() {
		return region.getMaxLon();
	}

	@Override
	public boolean isRectangular() {
		return region.isRectangular();
	}

}
