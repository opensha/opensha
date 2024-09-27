package org.opensha.sha.earthquake.faultSysSolution.util.minisections;

import org.opensha.commons.geo.Location;

public class MinisectionCreepRecord extends AbstractMinisectionDataRecord {
	public final double creepRate; // mm/yr
	
	public MinisectionCreepRecord(int parentID, int minisectionID, Location startLoc, Location endLoc, double creepRate) {
		super(parentID, minisectionID, startLoc, endLoc);
		this.creepRate = creepRate;
	}
}