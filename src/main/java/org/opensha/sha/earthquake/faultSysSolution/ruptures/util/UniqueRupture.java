package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Unique rupture as defined only by the set of subsection IDs included (regardless of order)
 * 
 * @author kevin
 *
 */
public class UniqueRupture {
	
	private final SectIDRange[] ranges;
	private int size;
	
	public static UniqueRupture forIDs(Collection<Integer> sectIDs) {
		return new Builder().add(sectIDs).build();
	}
	
	public static UniqueRupture forSects(Collection<? extends FaultSection> sects) {
		List<Integer> ids = new ArrayList<>(sects.size());
		for (FaultSection sect : sects)
			ids.add(sect.getSectionId());
		return new Builder().add(ids).build();
	}
	
	public static UniqueRupture forClusters(FaultSubsectionCluster... clusters) {
		Builder builder = new Builder();
		for (FaultSubsectionCluster cluster : clusters)
			for (SectIDRange range : cluster.unique.ranges)
				builder.add(range);
		return builder.build();
	}
	
	public static UniqueRupture add(UniqueRupture... uniques) {
		Builder builder = new Builder();
		for (UniqueRupture unique : uniques)
			for (SectIDRange range : unique.ranges)
				builder.add(range);
		return builder.build();
	}
	
	public static Builder builder() {
		return new Builder();
	}
	
	public static class Builder {
		
		private List<SectIDRange> list;
		private int size;
		
		private Builder() {
			list = new ArrayList<>();
			size = 0;
		}
		
		private int insertionIndex(SectIDRange range) {
			int index = Collections.binarySearch(list, range);
			if (index < 0)
				index = -(index + 1);
			return index;
		}
		
		private boolean contiguous(List<Integer> ids, boolean increasing) {
			if (increasing) {
				int prev = ids.get(0);
				for (int i=1; i<ids.size(); i++) {
					int cur = ids.get(i);
					if (cur != prev+1)
						return false;
					prev = cur;
				}
				return true;
			} else {
				int prev = ids.get(0);
				for (int i=1; i<ids.size(); i++) {
					int cur = ids.get(i);
					if (cur != prev-1)
						return false;
					prev = cur;
				}
				return true;
			}
		}
		
		public Builder add(Collection<Integer> ids) {
			if (ids.size() == 1) {
				int id = ids.iterator().next();
				add(SectIDRange.build(id, id));
				return this;
			}
			if (ids instanceof List<?>) {
				// look for special case of contiguous
				List<Integer> list = (List<Integer>)ids;
				int first = list.get(0);
				int lastIndex = list.size()-1;
				int last = list.get(lastIndex);
				if (last == first+lastIndex && contiguous(list, true)) {
					// increasing and contiguous
					add(SectIDRange.build(first, last));
					return this;
				}
				if (first == last+lastIndex && contiguous(list, false)) {
					// decreasing and contiguous
					add(SectIDRange.build(last, first));
					return this;
				}
			}
//			Preconditions.checkState(size == 0, // TODO I don't think this is the case. check?
//					"buildFrom can only be called when empty, have %s already", size);
			int rangeStartID = Integer.MIN_VALUE;
			int rangeEndID = -2;
			boolean backwards = false;
			for (int id : ids) {
				if (id == rangeEndID+1 && !backwards) {
					// next in a forwards series
					rangeEndID++;
				} else if (id == rangeEndID-1 && backwards) {
					// 3rd+ in a backwards series
					rangeEndID--;
				} else if (id == rangeStartID-1 && rangeStartID == rangeEndID) {
					// 2nd in a backwards series
					backwards = true;
					rangeEndID--;
				} else {
					// it's a break in the range
					if (rangeStartID != Integer.MIN_VALUE) {
						if (backwards)
							add(SectIDRange.build(rangeEndID, rangeStartID));
						else
							add(SectIDRange.build(rangeStartID, rangeEndID));
					}
					rangeStartID = id;
					rangeEndID = id;
					backwards = false;
				}
			}
			if (rangeStartID != Integer.MIN_VALUE) {
				if (backwards)
					add(SectIDRange.build(rangeEndID, rangeStartID));
				else
					add(SectIDRange.build(rangeStartID, rangeEndID));
			}
			Preconditions.checkState(ids.size() == size,
					"Size mismatch, duplicates? Expected %s, have %s", ids.size(), size);
			
			return this;
		}
		
		public Builder add(SectIDRange range) {
			if (list.isEmpty()) {
				list.add(range);
				size += range.size();
				return this;
			}
			int index = insertionIndex(range);
			int sizeAdd = range.size();
			if (index > 0) {
				SectIDRange before = list.get(index-1);
				Preconditions.checkState(range.getStartID() > before.getEndID(),
						"Overlappping ID ranges detected: %s %s", before, range);
				if (range.getStartID() == before.getEndID()+1) {
					// combine them
					list.remove(index-1);
					index--;
					range = SectIDRange.build(before.getStartID(), range.getEndID());
				}
			}
			if (index < list.size()) {
				SectIDRange after = list.get(index);
				Preconditions.checkState(range.getEndID() < after.getStartID(),
						"Overlappping ID ranges detected: %s %s", range, after);
				if (range.getEndID() == after.getStartID()-1) {
					// combine them
					list.remove(index);
					range = SectIDRange.build(range.getStartID(), after.getEndID());
				}
			}
			list.add(index, range);
			size += sizeAdd;
			
			return this;
		}
		
		public UniqueRupture build() {
			return new UniqueRupture(list.toArray(new SectIDRange[list.size()]), size);
		}
	}

	private UniqueRupture(SectIDRange[] ranges, int size) {
		this.ranges = ranges;
		this.size = size;
	}
	
	public int size() {
		return size;
	}
	
	private static final Comparator<SectIDRange> containsCompare = new Comparator<SectIDRange>() {

		@Override
		public int compare(SectIDRange o1, SectIDRange o2) {
			if (o1.size() == 1 && o2.contains(o1.getStartID()) || o2.size() == 1 && o1.contains(o2.getStartID()))
				return 0;
			return Integer.compare(o1.getStartID(), o2.getStartID());
		}
		
	};
	
	public boolean contains(int id) {
		if (ranges.length == 0)
			return false;
		if (ranges.length == 1)
			return ranges[0].contains(id);
		int index = Arrays.binarySearch(ranges, SectIDRange.build(id, id), containsCompare);
		return index >= 0;
	}
	
	private int hashCode = -1;

	@Override
	public int hashCode() {
		if (hashCode == -1) {
			synchronized (this) {
				if (hashCode == -1) {
					final int prime = 31;
					int result = 1;
					for (SectIDRange range : ranges)
					     result = prime * result + (range == null ? 0 : range.hashCode());
					hashCode = result;
				}
			}
		}
		return hashCode;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		UniqueRupture other = (UniqueRupture) obj;
		if (size != other.size)
			return false;
		if (ranges == null) {
			if (other.ranges != null)
				return false;
		} else if (!Arrays.equals(ranges, other.ranges))
			return false;
		return true;
	}
	
	/**
	 * @return immutable view of the list of ID ranges
	 */
	public SectIDRange[] getRanges() {
		return Arrays.copyOf(ranges, ranges.length);
	}
	
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append("UniqueRupture(size="+size+"): ");
		for (int i=0; i<ranges.length; i++) {
			if (i > 0)
				str.append(",");
			str.append(ranges[i]);
		}
		return str.toString();
	}

}
