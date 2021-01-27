package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import com.google.common.base.Preconditions;

/**
 * Range of section IDs, inclusive, for memory efficient tracking of unique ruptures and contains
 * operations
 * 
 * @author kevin
 *
 */
public final class SectIDRange implements Comparable<SectIDRange> {
	
	public final int startID;
	public final int endID;
	
	public SectIDRange(int startID, int endID) {
		Preconditions.checkArgument(startID >= 0, "startID=%s must be >= 0", startID);
		Preconditions.checkArgument(endID >= startID, "startID=%s must be >= endID=%s", startID, endID);
		this.startID = startID;
		this.endID = endID;
	}
	
	public int size() {
		return 1 + endID - startID;
	}
	
	public boolean contains(int id) {
		return id >= startID && id <= endID;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + endID;
		result = prime * result + startID;
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SectIDRange other = (SectIDRange) obj;
		if (endID != other.endID)
			return false;
		if (startID != other.startID)
			return false;
		return true;
	}

	@Override
	public int compareTo(SectIDRange o) {
		int cmp = Integer.compare(startID, o.startID);
		if (cmp != 0)
			return cmp;
		return Integer.compare(endID, o.endID);
	}
	
	@Override
	public String toString() {
		return "["+startID+","+endID+"]";
	}
}