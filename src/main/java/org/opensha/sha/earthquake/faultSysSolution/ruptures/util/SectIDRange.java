package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import com.google.common.base.Preconditions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Range of section IDs, inclusive, for memory efficient tracking of unique ruptures and contains
 * operations
 * 
 * @author kevin
 *
 */
public abstract class SectIDRange implements Comparable<SectIDRange> {

	private final static Map<SectIDRange, SectIDRange> cache = new ConcurrentHashMap<>();

	public static SectIDRange build(int startID, int endID) {
		SectIDRange range = construct(startID, endID);
		// try to use non-blocking get operation before using blocking putIfAbsent
		SectIDRange cached = cache.get(range);
		if (cached == null) {
			cached = cache.putIfAbsent(range, range);
		}
		return cached == null ? range : cached;
	}

	private static SectIDRange construct(int startID, int endID) {
		if (endID < Short.MAX_VALUE) {
			if (startID == endID) {
				return new ShortSingleID(endID);
			}
			return new ShortIDRange(startID, endID);
		}
		if (startID == endID) {
			return new SingleID(endID);
		}
		return new IntIDRange(startID, endID);
	}

	private static class ShortIDRange extends SectIDRange {

		private final short startID;
		private final short endID;

		private ShortIDRange(int startID, int endID) {
			super(startID, endID);
			this.startID = (short) startID;
			this.endID = (short) endID;
		}

		@Override
		int getStartID() {
			return startID;
		}

		@Override
		int getEndID() {
			return endID;
		}

	}

	private static class IntIDRange extends SectIDRange {
		
		private final int startID;
		private final int endID;
		
		private IntIDRange(int startID, int endID) {
			super(startID, endID);
			this.startID = startID;
			this.endID = endID;
		}

		@Override
		int getStartID() {
			return startID;
		}

		@Override
		int getEndID() {
			return endID;
		}
		
	}
	
	private static class SingleID extends SectIDRange {
		private int id;

		private SingleID(int id) {
			super(id, id);
			this.id = id;
		}

		@Override
		int getStartID() {
			return id;
		}

		@Override
		int getEndID() {
			return id;
		}
	}

	private static class ShortSingleID extends SectIDRange {
		private final short id;

		private ShortSingleID(int id) {
			super(id, id);
			this.id = (short) id;
		}

		@Override
		int getStartID() {
			return id;
		}

		@Override
		int getEndID() {
			return id;
		}
	}
	
	private SectIDRange(int startID, int endID) {
		Preconditions.checkArgument(startID >= 0, "startID=%s must be >= 0", startID);
		Preconditions.checkArgument(endID >= startID, "startID=%s must be >= endID=%s", startID, endID);
	}
	
	abstract int getStartID();
	abstract int getEndID();
	
	public int size() {
		return 1 + getEndID() - getStartID();
	}
	
	public boolean contains(int id) {
		return id >= getStartID() && id <= getEndID();
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + getEndID();
		result = prime * result + getStartID();
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
		if (getEndID() != other.getEndID())
			return false;
		if (getStartID() != other.getStartID())
			return false;
		return true;
	}

	@Override
	public int compareTo(SectIDRange o) {
		int cmp = Integer.compare(getStartID(), o.getStartID());
		if (cmp != 0)
			return cmp;
		return Integer.compare(getEndID(), o.getEndID());
	}
	
	@Override
	public String toString() {
		return "["+getStartID()+","+getEndID()+"]";
	}
}