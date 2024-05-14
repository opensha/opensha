package org.opensha.sha.earthquake.faultSysSolution.util.minisections;

import org.opensha.commons.geo.Location;

import com.google.common.base.Preconditions;

public abstract class AbstractMinisectionDataRecord {
	public final int parentID;
	public final int minisectionID;
	public final Location startLoc;
	public final Location endLoc;
	
	public AbstractMinisectionDataRecord(int parentID, int minisectionID, Location startLoc, Location endLoc) {
		super();
		Preconditions.checkState(parentID >= 0);
		this.parentID = parentID;
		Preconditions.checkState(minisectionID >= 0);
		this.minisectionID = minisectionID;
		this.startLoc = startLoc;
		this.endLoc = endLoc;
	}
}