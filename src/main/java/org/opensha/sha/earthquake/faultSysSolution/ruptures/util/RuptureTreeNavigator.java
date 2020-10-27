package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

/**
 * Class for navigating the tree structure of ruptures. Each FaultSection or FaultSubsectionCluster
 * can have one (or no) predecessors (above in the tree), and one or more (or no) descendants
 * (below in the tree)
 *  
 * @author kevin
 *
 */
public class RuptureTreeNavigator {
	
	private Map<Integer, FaultSubsectionCluster[]> parentClusterMap;
	private Map<FaultSubsectionCluster, ClusterConnections> clusterConnectionsMap;
	private ClusterRupture rupture;
	
	private class ClusterConnections {
		final FaultSubsectionCluster predecessor;
		final List<FaultSubsectionCluster> descendants;
		final Jump jumpTo;
		final List<Jump> jumpsFrom;
		
		public ClusterConnections(Jump jumpTo) {
			this.predecessor = jumpTo == null ? null : jumpTo.fromCluster;
			this.descendants = new ArrayList<>();
			this.jumpTo = jumpTo;
			this.jumpsFrom = new ArrayList<>();
		}
		
		private void addJumpFrom(Jump jump) {
			jumpsFrom.add(jump);
			descendants.add(jump.toCluster);
		}
	}

	public RuptureTreeNavigator(ClusterRupture rupture) {
		this.rupture = rupture;
		this.parentClusterMap = new HashMap<>();
		this.clusterConnectionsMap = new HashMap<>();
		// add the start cluster
		clusterConnectionsMap.put(rupture.clusters[0], new ClusterConnections(null));
		// build parent relationships
		for (Jump jump : rupture.getJumpsIterable()) {
			ClusterConnections connections = clusterConnectionsMap.get(jump.toCluster);
			if (connections == null) {
				connections = new ClusterConnections(jump);
				clusterConnectionsMap.put(jump.toCluster, connections);
			}
		}
		// build children relationships
		for (Jump jump : rupture.getJumpsIterable()) {
			ClusterConnections connections = clusterConnectionsMap.get(jump.fromCluster);
			Preconditions.checkNotNull(connections);
			connections.addJumpFrom(jump);
		}
		for (FaultSubsectionCluster cluster : clusterConnectionsMap.keySet()) {
			int parentID = cluster.parentSectionID;
			FaultSubsectionCluster[] matches = parentClusterMap.get(parentID);
			if (matches == null) {
				parentClusterMap.put(parentID, new FaultSubsectionCluster[] { cluster });
			} else {
				matches = Arrays.copyOf(matches, matches.length+1);
				matches[matches.length-1] = cluster;
				parentClusterMap.put(parentID, matches);
			}
		}
	}

	/**
	 * @param cluster
	 * @return the predecessor (up a level in the tree) to this cluster, or null if this is the
	 * top level cluster
	 */
	public FaultSubsectionCluster getPredecessor(FaultSubsectionCluster cluster) {
		return clusterConnectionsMap.get(cluster).predecessor;
	}
	
	/**
	 * @param cluster
	 * @return a list of all descendants (down a level in the tree) from this cluster (empty list is
	 * returned if no descendants exist)
	 */
	public List<FaultSubsectionCluster> getDescendants(FaultSubsectionCluster cluster) {
		return clusterConnectionsMap.get(cluster).descendants;
	}
	
	private FaultSubsectionCluster locateCluster(FaultSection sect) {
		Preconditions.checkState(sect.getParentSectionId() >= 0, "parent section IDs must be populated");
		FaultSubsectionCluster[] clusters = parentClusterMap.get(sect.getParentSectionId());
		Preconditions.checkNotNull(clusters,
				"Couldn't locate cluster with parent %s in rupture:\n%s", sect.getParentSectionId(), rupture);
		if (clusters.length == 1)
			return clusters[0];
//		System.out.println("Locate cluster with len="+clusters.length);
		// need to search, more expensive
		for (FaultSubsectionCluster cluster : clusters) {
			if (cluster.contains(sect)) {
//				System.out.println("Cluster "+cluster+" contains "+sect.getSectionId());
				return cluster;
			}
		}
		throw new IllegalStateException("Section "+sect.getSectionName()+" not found in any clusters");
	}
	
	private int indexWithinCluster(FaultSection sect, FaultSubsectionCluster cluster) {
		int targetID = sect.getSectionId();
		for (int i = 0; i < cluster.subSects.size(); i++) {
			FaultSection test = cluster.subSects.get(i);
			if (test.getSectionId() == targetID)
				return i;
		}
		throw new IllegalStateException("Couldn't locate section "+sect.getParentSectionId()+":"
				+targetID+" ("+sect.getSectionName()+") in cluster "+cluster+".\nFull rupture: "+rupture);
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
	public Jump getJump(FaultSubsectionCluster fromCluster, FaultSubsectionCluster toCluster) {
		ClusterConnections connections = clusterConnectionsMap.get(toCluster);
		Preconditions.checkNotNull(connections, "toCluster not found: %s", toCluster);
		if (connections.jumpTo != null && connections.jumpTo.fromCluster == fromCluster)
			return connections.jumpTo;
		// check the other direction (backwards jump)
		connections = clusterConnectionsMap.get(fromCluster);
		Preconditions.checkNotNull(connections, "fromCluster not found: %s", fromCluster);
		if (connections.jumpTo != null && connections.jumpTo.fromCluster == toCluster)
			return connections.jumpTo.reverse();
		throw new IllegalStateException(
				"Rupture does not use a direct jump between "+fromCluster+" to "+toCluster);
	}
	
	/**
	 * @param sect
	 * @return the direct predecessor of this section (either within its cluster or in the previous cluster)
	 * null if this is the first section of the first cluster
	 */
	public FaultSection getPredecessor(FaultSection sect) {
		FaultSubsectionCluster cluster = locateCluster(sect);
		int indexInCluster = indexWithinCluster(sect, cluster);
		if (indexInCluster > 0)
			return cluster.subSects.get(indexInCluster-1);
		// need upstream cluster
		ClusterConnections connections = clusterConnectionsMap.get(cluster);
		if (connections.jumpTo == null)
			// start cluster
			return null;
		return connections.jumpTo.fromSection;
	}
	
	/**
	 * 
	 * @param sect
	 * @return a list of all descendants from this subsection, which could be in the same
	 * cluster or on the other side of a jump (empty list is returned if no descendants exist)
	 */
	public List<FaultSection> getDescendants(FaultSection sect) {
		FaultSubsectionCluster cluster = locateCluster(sect);
		int indexInCluster = indexWithinCluster(sect, cluster);
		List<FaultSection> descendants = new ArrayList<>();
		if (indexInCluster < cluster.subSects.size()-1)
			descendants.add(cluster.subSects.get(indexInCluster+1));
		for (Jump jump : clusterConnectionsMap.get(cluster).jumpsFrom)
			if (jump.fromSection.getSectionId() == sect.getSectionId())
				descendants.add(jump.toSection);
		return descendants;
	}
}
