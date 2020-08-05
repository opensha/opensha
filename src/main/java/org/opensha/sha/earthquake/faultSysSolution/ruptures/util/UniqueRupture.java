package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.Collection;
import java.util.HashSet;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Unique rupture as defined only by the set of subsection IDs included (regardless of order)
 * 
 * @author kevin
 *
 */
public class UniqueRupture {
	
	private final HashSet<Integer> sectIDs;
	
	public UniqueRupture(Collection<Integer> sectIDs) {
		this.sectIDs = new HashSet<>(sectIDs);
	}
	
	public UniqueRupture(FaultSubsectionCluster cluster) {
		sectIDs = new HashSet<>();
		for (FaultSection sect : cluster.subSects)
			sectIDs.add(sect.getSectionId());
	}
	
	public UniqueRupture(UniqueRupture previous, FaultSubsectionCluster addition) {
		sectIDs = new HashSet<>(previous.sectIDs);
		for (FaultSection sect : addition.subSects)
			sectIDs.add(sect.getSectionId());
	}
	
	public int size() {
		return sectIDs.size();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + sectIDs.hashCode();
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
		if (!sectIDs.equals(other.sectIDs))
			return false;
		return true;
	}

}
