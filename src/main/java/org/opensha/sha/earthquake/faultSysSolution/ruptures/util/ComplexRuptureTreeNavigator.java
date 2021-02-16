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
public class ComplexRuptureTreeNavigator implements RuptureTreeNavigator {
	
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

	public ComplexRuptureTreeNavigator(ClusterRupture rupture) {
		this.rupture = rupture;
		this.parentClusterMap = new HashMap<>();
		this.clusterConnectionsMap = new HashMap<>();
		// add the start cluster
		clusterConnectionsMap.put(rupture.clusters[0], new ClusterConnections(null));
		// build parent relationships
		Iterable<Jump> jumps = rupture.getJumpsIterable();
		for (Jump jump : jumps) {
			ClusterConnections connections = clusterConnectionsMap.get(jump.toCluster);
			if (connections == null) {
				connections = new ClusterConnections(jump);
				clusterConnectionsMap.put(jump.toCluster, connections);
			}
		}
		int numClusters = rupture.getTotalNumClusters();
		Preconditions.checkState(clusterConnectionsMap.size() == numClusters,
				"We have %s clusters but clusterConnectionsMap only has %s entries:\n%s",
				numClusters, clusterConnectionsMap.size(), clusterConnectionsMap);
		// build children relationships
		for (Jump jump : jumps) {
			ClusterConnections connections = clusterConnectionsMap.get(jump.fromCluster);
			Preconditions.checkNotNull(connections,
					"No ClusterConnections instance found for cluster %s (from jump %s).\n\tRupture: %s\n\tConnMap: %s",
					jump.fromCluster, jump, rupture, clusterConnectionsMap);
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

	@Override
	public FaultSubsectionCluster getPredecessor(FaultSubsectionCluster cluster) {
		return clusterConnectionsMap.get(cluster).predecessor;
	}
	
	@Override
	public List<FaultSubsectionCluster> getDescendants(FaultSubsectionCluster cluster) {
		return clusterConnectionsMap.get(cluster).descendants;
	}
	
	@Override
	public FaultSubsectionCluster locateCluster(FaultSection sect) {
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
	
	@Override
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
	
	@Override
	public Jump getJump(FaultSection fromSection, FaultSection toSection) {
		FaultSubsectionCluster fromCluster = locateCluster(fromSection);
		FaultSubsectionCluster toCluster = locateCluster(toSection);
		Preconditions.checkState(fromCluster != toCluster && fromCluster != null && fromCluster.contains(fromSection)
				&& toCluster != null && toCluster.contains(toSection));
		
		ClusterConnections connections = clusterConnectionsMap.get(toCluster);
		Preconditions.checkNotNull(connections, "toCluster not found: %s", toCluster);
		if (connections.jumpTo != null && connections.jumpTo.fromCluster == fromCluster) {
			Jump jump = connections.jumpTo;
			Preconditions.checkState(jump.fromSection.equals(fromSection) && jump.toSection.equals(toSection),
					"Jump doesn't occur between %s and %s: %s",
					fromSection.getSectionId(), toSection.getSectionId(), connections.jumpTo);
			return jump;
		}
		// check the other direction (backwards jump)
		connections = clusterConnectionsMap.get(fromCluster);
		Preconditions.checkNotNull(connections, "fromCluster not found: %s", fromCluster);
		if (connections.jumpTo != null && connections.jumpTo.fromCluster == toCluster) {
			Jump jump = connections.jumpTo.reverse();
			Preconditions.checkState(jump.fromSection.equals(fromSection) && jump.toSection.equals(toSection),
					"Jump doesn't occur between %s and %s: %s\n\tRupture: %s",
					fromSection.getSectionId(), toSection.getSectionId(), connections.jumpTo, rupture);
			return jump;
		}
		throw new IllegalStateException(
				"Rupture does not use a direct jump between "+fromSection.getSectionId()+" to "+toSection.getSectionId());
	}
	
	@Override
	public FaultSection getPredecessor(FaultSection sect) {
		FaultSubsectionCluster cluster = locateCluster(sect);
		ClusterConnections connections = clusterConnectionsMap.get(cluster);
		int indexInCluster = indexWithinCluster(sect, cluster);
		if (connections.jumpTo != null) {
			// we jumped to this section
			if (connections.jumpTo.toSection.equals(sect))
				// we jumped to this section
				return connections.jumpTo.fromSection;
			int jumpToIndex = indexWithinCluster(connections.jumpTo.toSection, cluster);
			if (jumpToIndex > indexInCluster) {
				// we jumped to a section after this, which means that that our predecessor is actually after us
				// (bilateral spread in a T rupture)
				return cluster.subSects.get(indexInCluster+1);
			}
		}
		// no jump to this cluster, see if we're at the start of the cluster
		if (indexInCluster > 0)
			// not at the start, predecessor is the section before
			return cluster.subSects.get(indexInCluster-1);
		// if we're here, then no jumps to us and we're the first section. no predecessor (first sect of first cluster)
		return null;
	}
	
	@Override
	public List<FaultSection> getDescendants(FaultSection sect) {
		FaultSubsectionCluster cluster = locateCluster(sect);
		int indexInCluster = indexWithinCluster(sect, cluster);
		List<FaultSection> descendants = new ArrayList<>();
		ClusterConnections connections = clusterConnectionsMap.get(cluster);
		
		if (connections.jumpTo != null) {
			// there is a jump to this cluster. check for special case where the jump was "later" in this rupture
			// (a T rupture) and it spreads bilaterally
			int jumpIndexInCluster = indexWithinCluster(connections.jumpTo.toSection, cluster);
			if (jumpIndexInCluster == indexInCluster) {
				// jump was to this section, both directions are descendants
				if (indexInCluster > 0)
					descendants.add(cluster.subSects.get(indexInCluster-1));
				if (indexInCluster < cluster.subSects.size()-1)
					descendants.add(cluster.subSects.get(indexInCluster+1));
			} else if (jumpIndexInCluster > indexInCluster) {
				// bilateral spread from a later section, descendant is earlier in list
				if (indexInCluster > 0)
					descendants.add(cluster.subSects.get(indexInCluster-1));
			} else {
				// normal, descendant is later in list
				if (indexInCluster < cluster.subSects.size()-1)
					descendants.add(cluster.subSects.get(indexInCluster+1));
			}
		} else {
			// this is the first cluster, so things are in order & descendants are later
			if (indexInCluster < cluster.subSects.size()-1)
				descendants.add(cluster.subSects.get(indexInCluster+1));
		}
		
//		if (indexInCluster < cluster.subSects.size()-1) {
//			// if this is a T, then the section after can actually be our predecessor. only add if not
//			FaultSection sectAfter = cluster.subSects.get(indexInCluster+1);
//			if (connections.jumpTo == null || !connections.jumpTo.toSection.equals(sectAfter))
//				descendants.add(sectAfter);
//		}
//		if (indexInCluster > 0 && connections.jumpTo != null && connections.jumpTo.toSection.equals(sect)) {
//			// we took a jump to get to this point, but it's not the start, so there are descendants in both
//			// directions (T rupture)
//			descendants.add(cluster.subSects.get(indexInCluster-1));
//		}
		for (Jump jump : connections.jumpsFrom)
			if (jump.fromSection.getSectionId() == sect.getSectionId())
				descendants.add(jump.toSection);
		return descendants;
	}

	@Override
	public Jump getJumpTo(FaultSubsectionCluster cluster) {
		ClusterConnections connections = clusterConnectionsMap.get(cluster);
		Preconditions.checkState(connections != null, "Cluster not found in rupture: %s", cluster);
		return connections.jumpTo;
	}
}
