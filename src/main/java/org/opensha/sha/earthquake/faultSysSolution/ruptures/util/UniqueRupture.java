package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.ArrayList;
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
	
	private final List<SectIDRange> list;
	private int size;
	
	public static UniqueRupture forIDs(Collection<Integer> sectIDs) {
		UniqueRupture unique = new UniqueRupture();
		unique.buildFrom(sectIDs);
		return unique;
	}
	
	public static UniqueRupture forSects(Collection<? extends FaultSection> sects) {
		UniqueRupture unique = new UniqueRupture();
		List<Integer> ids = new ArrayList<>(sects.size());
		for (FaultSection sect : sects)
			ids.add(sect.getSectionId());
		unique.buildFrom(ids);
		return unique;
	}
	
	public static UniqueRupture forClusters(FaultSubsectionCluster... clusters) {
		UniqueRupture unique = new UniqueRupture();
		for (FaultSubsectionCluster cluster : clusters)
			for (SectIDRange range : cluster.unique.list)
				unique.add(range);
		return unique;
	}

	private UniqueRupture() {
		list = new ArrayList<>();
		size = 0;
	}
	
	public UniqueRupture(UniqueRupture list, SectIDRange addition) {
		this.list = new ArrayList<>(list.list);
		this.size = list.size;
		add(addition);
	}
	
	public UniqueRupture(UniqueRupture list, FaultSubsectionCluster addition) {
		this(list, addition.unique);
	}
	
	public UniqueRupture(UniqueRupture list, UniqueRupture additions) {
		this.list = new ArrayList<>(list.list);
		this.size = list.size;
		for (SectIDRange addition : additions.list)
			add(addition);
	}
	
	public int size() {
		return size;
	}
	
	private void buildFrom(Collection<Integer> ids) {
		Preconditions.checkState(size == 0,
				"buildFrom can only be called when empty, have %s already", size);
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
	}
	
	private int insertionIndex(SectIDRange range) {
		int index = Collections.binarySearch(list, range);
		if (index < 0)
			index = -(index + 1);
		return index;
	}
	
	private void add(SectIDRange range) {
		if (list.isEmpty()) {
			list.add(range);
			size += range.size();
			return;
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
		if (list.size() == 0)
			return false;
		if (list.size() == 1)
			return list.get(0).contains(id);
		int index = Collections.binarySearch(list, SectIDRange.build(id, id), containsCompare);
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
					result = prime * result + ((list == null) ? 0 : list.hashCode());
					result = prime * result + size;
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
		if (list == null) {
			if (other.list != null)
				return false;
		} else if (!list.equals(other.list))
			return false;
		return true;
	}
	
	/**
	 * @return immutable view of the list of ID ranges
	 */
	public ImmutableList<SectIDRange> getRanges() {
		return ImmutableList.copyOf(list);
	}
	
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append("UniqueRupture(size="+size+"): ");
		for (int i=0; i<list.size(); i++) {
			if (i > 0)
				str.append(",");
			str.append(list.get(i));
		}
		return str.toString();
	}

}
