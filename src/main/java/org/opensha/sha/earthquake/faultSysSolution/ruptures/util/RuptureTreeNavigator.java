package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;

public interface RuptureTreeNavigator {

	/**
	 * @param cluster
	 * @return the predecessor (up a level in the tree) to this cluster, or null if this is the
	 * top level cluster
	 */
	public FaultSubsectionCluster getPredecessor(FaultSubsectionCluster cluster);

	/**
	 * @param cluster
	 * @return a list of all descendants (down a level in the tree) from this cluster (empty list is
	 * returned if no descendants exist)
	 */
	public Collection<FaultSubsectionCluster> getDescendants(FaultSubsectionCluster cluster);
	
	/**
	 * 
	 * @param sect
	 * @return list of all neighbors (predecessor and descendants) of this cluster, or an empty list if none exist
	 */
	public default Collection<FaultSubsectionCluster> getNeighbors(FaultSubsectionCluster cluster) {
		FaultSubsectionCluster predecessor = getPredecessor(cluster);
		Collection<FaultSubsectionCluster> descendants = getDescendants(cluster);
		
		if (descendants.isEmpty()) {
			if (predecessor == null)
				return List.of();
			else
				return List.of(predecessor);
		} else {
			if (predecessor == null) {
				return descendants;
			} else {
				List<FaultSubsectionCluster> ret = new ArrayList<>(1+descendants.size());
				ret.add(predecessor);
				ret.addAll(descendants);
				return ret;
			}
		}
	}

	/**
	 * Locates the given jump. If the jump occurs in the rupture backwards, i.e., from toCluster to
	 * fromCluster, then the jump returned will be reversed such that it matches the supplied to/from clusters
	 * 
	 * @param fromCluster
	 * @param toCluster
	 * @return the jump from fromCluster to toCluster
	 * @throws IllegalStateException if the rupture does not use a direct jump between these clusters
	 */
	public Jump getJump(FaultSubsectionCluster fromCluster, FaultSubsectionCluster toCluster);

	/**
	 * @param fromCluster
	 * @param toCluster
	 * @return true if a direct jump exists between fromCluster and toCluster (regardless of order), false otherwise
	 */
	public boolean hasJump(FaultSubsectionCluster fromCluster, FaultSubsectionCluster toCluster);
	
	/**
	 * Locate the cluster containing the given section
	 * @param section
	 * @return cluster containing this section
	 * @throws IllegalStateException if the rupture does not contain this section
	 */
	public FaultSubsectionCluster locateCluster(FaultSection section);
	
	/**
	 * Locate the jump to the given cluster, or null if this is the first cluster in a rupture
	 * @param cluster
	 * @return the jump to the given cluster (or null if no such jump)
	 * @throws IllegalStateException if the rupture does not contain this cluster
	 */
	public Jump getJumpTo(FaultSubsectionCluster cluster);

	/**
	 * Locates the given jump. If the jump occurs in the rupture backwards, i.e., from toSection to
	 * fromSection, then the jump returned will be reversed such that it matches the supplied to/from sections
	 * 
	 * @param fromSection
	 * @param toSection
	 * @return the jump from fromSection to toSection
	 * @throws IllegalStateException if the rupture does not use a direct jump between these sections
	 */
	public Jump getJump(FaultSection fromSection, FaultSection toSection);

	/**
	 * @param fromSection
	 * @param toSection
	 * @return true if a direct jump exists between fromSection and toSection (regardless of order), false otherwise
	 * @throws IllegalStateException if the rupture does not use a direct jump between these sections
	 */
	public boolean hasJump(FaultSection fromSection, FaultSection toSection);

	/**
	 * @param sect
	 * @return the direct predecessor of this section (either within its cluster or in the previous cluster)
	 * null if this is the first section of the first cluster
	 */
	public FaultSection getPredecessor(FaultSection sect);

	/**
	 * 
	 * @param sect
	 * @return a list of all descendants from this subsection, which could be in the same
	 * cluster or on the other side of a jump (empty list is returned if no descendants exist)
	 */
	public Collection<FaultSection> getDescendants(FaultSection sect);
	
	/**
	 * 
	 * @param sect
	 * @return list of all neighbors (predecessor and descendants) of this subsection, or an empty list if none exist
	 */
	public default Collection<FaultSection> getNeighbors(FaultSection sect) {
		FaultSection predecessor = getPredecessor(sect);
		Collection<FaultSection> descendants = getDescendants(sect);
		
		if (descendants.isEmpty()) {
			if (predecessor == null)
				return List.of();
			else
				return List.of(predecessor);
		} else {
			if (predecessor == null) {
				return descendants;
			} else {
				List<FaultSection> ret = new ArrayList<>(1+descendants.size());
				ret.add(predecessor);
				ret.addAll(descendants);
				return ret;
			}
		}
	}

}