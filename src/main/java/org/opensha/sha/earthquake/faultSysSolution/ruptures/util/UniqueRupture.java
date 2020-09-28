package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

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
		unique.add(sectIDs);
		return unique;
	}
	
	public static UniqueRupture forSects(Collection<? extends FaultSection> sects) {
		UniqueRupture unique = new UniqueRupture();
		List<Integer> ids = new ArrayList<>(sects.size());
		for (FaultSection sect : sects)
			ids.add(sect.getSectionId());
		unique.add(ids);
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
	
	private void add(Collection<Integer> ids) {
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
						add(new SectIDRange(rangeEndID, rangeStartID));
					else
						add(new SectIDRange(rangeStartID, rangeEndID));
				}
				rangeStartID = id;
				rangeEndID = id;
				backwards = false;
			}
		}
		if (rangeStartID != Integer.MIN_VALUE) {
			if (backwards)
				add(new SectIDRange(rangeEndID, rangeStartID));
			else
				add(new SectIDRange(rangeStartID, rangeEndID));
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
			Preconditions.checkState(range.startID > before.endID,
					"Overlappping ID ranges detected: %s %s", before, range);
			if (range.startID == before.endID+1) {
				// combine them
				list.remove(index-1);
				index--;
				range = new SectIDRange(before.startID, range.endID);
			}
		}
		if (index < list.size()-1) {
			SectIDRange after = list.get(index+1);
			Preconditions.checkState(range.endID < after.startID,
					"Overlappping ID ranges detected: %s %s", range, after);
			if (range.endID == after.startID-1) {
				// combine them
				list.remove(index+1);
				range = new SectIDRange(range.startID, after.endID);
			}
		}
		list.add(index, range);
		size += sizeAdd;
	}
	
	private static final Comparator<SectIDRange> containsCompare = new Comparator<SectIDRange>() {

		@Override
		public int compare(SectIDRange o1, SectIDRange o2) {
			if (o1.size() == 1 && o2.contains(o1.startID) || o2.size() == 1 && o1.contains(o2.startID))
				return 0;
			return Integer.compare(o1.startID, o2.startID);
		}
		
	};
	
	public boolean contains(int id) {
		if (list.size() == 0)
			return false;
		if (list.size() == 1)
			return list.get(0).contains(id);
		int index = Collections.binarySearch(list, new SectIDRange(id, id), containsCompare);
		return index >= 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((list == null) ? 0 : list.hashCode());
		result = prime * result + size;
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

}
