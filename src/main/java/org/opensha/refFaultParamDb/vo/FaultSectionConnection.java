package org.opensha.refFaultParamDb.vo;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;

import com.google.common.base.Preconditions;

public class FaultSectionConnection {
	
	private int id1, id2;
	private Location loc1, loc2;
	
	public FaultSectionConnection(int id1, int id2, Location loc1, Location loc2) {
		Preconditions.checkArgument(id1 != id2, "IDs cannot be equal.");
		this.id1 = id1;
		this.id2 = id2;
		Preconditions.checkNotNull(loc1, "Connection locations cannot be null");
		Preconditions.checkNotNull(loc2, "Connection locations cannot be null");
		this.loc1 = loc1;
		this.loc2 = loc2;
	}

	public int getId1() {
		return id1;
	}

	public int getId2() {
		return id2;
	}

	public Location getLoc1() {
		return loc1;
	}

	public Location getLoc2() {
		return loc2;
	}
	
	public boolean involvesSection(int id) {
		return id1 == id || id2 == id;
	}
	
	public boolean involvesSectionAtLocation(int id, Location loc) {
		if (!involvesSection(id))
			return false;
		Location locForID = getLocationForID(id);
		// we have to strip out the depth of the incoming location
		loc = new Location(loc.getLatitude(), loc.getLongitude(), locForID.getDepth());
		return loc.equals(locForID);
	}
	
	public Location getLocationForID(int id) {
		if (id == id1)
			return loc1;
		if (id == id2)
			return loc2;
		throw new IllegalArgumentException("The specified id ("+id+") isn't part of this connection.");
	}
	
	/**
	 * @return the horizontal distance between the two locations
	 */
	public double calcDistance() {
		return LocationUtils.horzDistanceFast(loc1, loc2);
	}

	@Override
	public String toString() {
		return "FaultSectionConnection [id1=" + id1 + ", id2=" + id2
				+ ", loc1=" + loc1 + ", loc2=" + loc2 + "]";
	}

}
