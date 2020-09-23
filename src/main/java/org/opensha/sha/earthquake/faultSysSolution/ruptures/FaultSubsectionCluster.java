package org.opensha.sha.earthquake.faultSysSolution.ruptures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

/**
 * This class represents a cluster of contiguous subsections. It can either be a full parent
 * fault section, or a smaller permutation of a parent section
 * @author kevin
 *
 */
public class FaultSubsectionCluster implements Comparable<FaultSubsectionCluster> {

	/**
	 * Parent fault section ID
	 */
	public final int parentSectionID;
	/**
	 * Parent section name
	 */
	public final String parentSectionName;
	/**
	 * Immutable list of subsections that constitute this cluster
	 */
	public final ImmutableList<FaultSection> subSects;
	/**
	 * The starting section (entry point to) this cluster
	 */
	public final FaultSection startSect;
	/**
	 * The end point(s) of this cluster, from which a jump can happen without it being considered
	 * a splay jump. For crustal faults this will typically be only the last section, but for faults
	 * with down-dip subsections this could be an entire edge on the far and/or upper/lower ends of the
	 * cluster
	 */
	public final ImmutableSet<FaultSection> endSects;
	
	/**
	 * Internal HashSet of sections included in this cluster, for fast contains checks
	 */
	private final HashSet<FaultSection> sectsSet;
	/**
	 * Internal (mutable) list of allowed jumps. Set via constructor, or via addConnection(Jump) method
	 */
	private final List<Jump> possibleJumps;
	/**
	 * Mapping of possible jumps from the given section on this cluster
	 */
	private final Multimap<FaultSection, Jump> jumpsForSection;
	/**
	 * Mapping of possible jumps to the given cluster 
	 */
	private final Multimap<FaultSubsectionCluster, Jump> jumpsToCluster;
	
	private final ClusterJumpDistComparator clusterDistComparator;
	
	public FaultSubsectionCluster(List<? extends FaultSection> subSects) {
		this(subSects, null);
	}
	
	public FaultSubsectionCluster(List<? extends FaultSection> subSects, Collection<FaultSection> endSects) {
		Preconditions.checkArgument(!subSects.isEmpty(), "Must supply at least 1 subsection");
		this.subSects = ImmutableList.copyOf(subSects);
		this.startSect = subSects.get(0);
		if (endSects == null) {
			// default behaviour: last section
			this.endSects = ImmutableSet.of(subSects.get(subSects.size()-1));
		} else {
			ImmutableSet.Builder<FaultSection> endBuilder = ImmutableSet.builderWithExpectedSize(endSects.size());
			endBuilder.addAll(endSects);
			this.endSects = endBuilder.build();
		}
		sectsSet = new HashSet<>(subSects);
		int parentSectionID = -1;
		String parentSectionName = null;
		for (FaultSection subSect : subSects) {
			Preconditions.checkNotNull(subSect);
			if (parentSectionID < 0) {
				parentSectionID = subSect.getParentSectionId();
				parentSectionName = subSect.getParentSectionName();
			} else {
				Preconditions.checkState(subSect.getParentSectionId() == parentSectionID,
						"Cluster subSects list has sedctios with multiple parentSectionIDs");
			}
		}
		this.parentSectionID = parentSectionID;
		this.parentSectionName = parentSectionName;
		jumpsForSection = HashMultimap.create();
		jumpsToCluster = HashMultimap.create();
		this.possibleJumps = new ArrayList<>();
		this.clusterDistComparator = new ClusterJumpDistComparator();
	}
	
	public void addConnection(Jump jump) {
		Preconditions.checkState(jump.fromCluster == this);
		Preconditions.checkState(sectsSet.contains(jump.fromSection));
		possibleJumps.add(jump);
		jumpsForSection.put(jump.fromSection, jump);
		jumpsToCluster.put(jump.toCluster, jump);
	}
	
	public Set<FaultSection> getExitPoints() {
		return jumpsForSection.keySet();
	}
	
	public Set<FaultSubsectionCluster> getConnectedClusters() {
		return jumpsToCluster.keySet();
	}
	
	public List<FaultSubsectionCluster> getDistSortedConnectedClusters() {
		List<FaultSubsectionCluster> sorted = new ArrayList<>(jumpsToCluster.keySet());
		Collections.sort(sorted, clusterDistComparator);
		Preconditions.checkState(sorted.size() == jumpsToCluster.keySet().size());
		return sorted;
	}
	
	private class ClusterJumpDistComparator implements Comparator<FaultSubsectionCluster> {

		@Override
		public int compare(FaultSubsectionCluster o1, FaultSubsectionCluster o2) {
			double minDist1 = Double.POSITIVE_INFINITY;
			double minDist2 = Double.POSITIVE_INFINITY;
			for (Jump jump : jumpsToCluster.get(o1))
				minDist1 = Math.min(minDist1, jump.distance);
			for (Jump jump : jumpsToCluster.get(o2))
				minDist2 = Math.min(minDist2, jump.distance);
			return Double.compare(minDist1, minDist2);
		}
		
	}
	
	public Collection<Jump> getConnectionsTo(FaultSubsectionCluster cluster) {
		return jumpsToCluster.get(cluster);
	}
	
	public List<Jump> getConnections() {
		return ImmutableList.copyOf(possibleJumps);
	}
	
	public Collection<Jump> getConnections(FaultSection exitPoint) {
		Preconditions.checkState(sectsSet.contains(exitPoint), "Given section is not part of this cluster");
		return jumpsForSection.get(exitPoint);
	}
	@Override
	public String toString() {
		StringBuilder str = null;
		for (FaultSection sect : subSects) {
			if (str == null)
				str = new StringBuilder("[").append(parentSectionID).append(":");
			else
				str.append(",");
			str.append(sect.getSectionId());
		}
		return str.append("]").toString();
	}
	
	public FaultSubsectionCluster reversed() {
		List<FaultSection> sects = new ArrayList<>(subSects);
		Collections.reverse(sects);
		FaultSubsectionCluster reversed = new FaultSubsectionCluster(sects);
		for (Jump jump : possibleJumps)
			reversed.addConnection(new Jump(jump.fromSection, reversed,
					jump.toSection, jump.toCluster, jump.distance));
		return reversed;
	}

	@Override
	public int compareTo(FaultSubsectionCluster o) {
		int cmp = Integer.compare(parentSectionID, o.parentSectionID);
		if (cmp != 0)
			return cmp;
		cmp = Integer.compare(subSects.size(), o.subSects.size());
		if (cmp != 0)
			return cmp;
		for (int i=0; i<subSects.size(); i++) {
			cmp = Integer.compare(subSects.get(i).getSectionId(), o.subSects.get(i).getSectionId());
			if (cmp != 0)
				return cmp;
		}
		return 0;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((endSects == null) ? 0 : endSects.hashCode());
		result = prime * result + parentSectionID;
		result = prime * result + ((subSects == null) ? 0 : subSects.hashCode());
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
		FaultSubsectionCluster other = (FaultSubsectionCluster) obj;
		if (endSects == null) {
			if (other.endSects != null)
				return false;
		} else if (!endSects.equals(other.endSects))
			return false;
		if (parentSectionID != other.parentSectionID)
			return false;
		if (subSects == null) {
			if (other.subSects != null)
				return false;
		} else if (!subSects.equals(other.subSects))
			return false;
		return true;
	}

}
