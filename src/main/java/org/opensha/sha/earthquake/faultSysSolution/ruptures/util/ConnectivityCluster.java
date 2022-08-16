package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * This keeps track of all sections that are connected through ruptures in distinct clusters. Not all sections in a
 * cluster will necessarily corupture with all other sections, but rather are connected through chains of ruptures.
 * <p>
 * For example, consider 3 sections, A, B, and C, with ruptures AB and BC. All 3 sections would be in the same cluster,
 * as A connects to C through B. 
 * 
 * @author kevin
 *
 */
public class ConnectivityCluster implements Comparable<ConnectivityCluster> {
	
	private int numSections;
	private int numRuptures;
	private HashSet<Integer> sectIDs;
	private HashSet<Integer> parentSectIDs;
	
	public ConnectivityCluster(int numSections, int numRuptures, HashSet<Integer> sectIDs,
			HashSet<Integer> parentSectIDs) {
		this.numSections = numSections;
		this.numRuptures = numRuptures;
		this.sectIDs = sectIDs;
		this.parentSectIDs = parentSectIDs;
	}
	
	private ConnectivityCluster() {
		sectIDs = new HashSet<>();
		parentSectIDs = new HashSet<>();
	}
	
	/**
	 * 
	 * @return the number of sections in this cluster
	 */
	public int getNumSections() {
		return numSections;
	}

	/**
	 * @return the number of ruptures in this cluster
	 */
	public int getNumRuptures() {
		return numRuptures;
	}
	
	/**
	 * @param sect
	 * @return true if the given section is part of this cluster, false otherwise
	 */
	public boolean containsSect(FaultSection sect) {
		return containsSect(sect.getSectionId());
	}
	
	/**
	 * @param sectID
	 * @return true if the given section ID is part of this cluster, false otherwise
	 */
	public boolean containsSect(int sectID) {
		return sectIDs.contains(sectID);
	}

	/**
	 * @return unmodifiable view of the sections IDs associated with this cluster
	 */
	public Set<Integer> getSectIDs() {
		return Collections.unmodifiableSet(sectIDs);
	}

	/**
	 * @return unmodifiable view of the parent section IDs associated with this cluster, or an empty set if this
	 * rupture set does not have parent section IDs associated with sections.
	 */
	public Set<Integer> getParentSectIDs() {
		return Collections.unmodifiableSet(parentSectIDs);
	}

	/**
	 * Builds a list of {@link ConnectivityCluster} instances for the given rupture set. This list will not be
	 * initially sorted
	 * @param rupSet
	 * @return
	 */
	public static List<ConnectivityCluster> build(FaultSystemRupSet rupSet) {
		return build(rupSet, null);
	}

	/**
	 * Builds a list of {@link ConnectivityCluster} instances for the given rupture set. This list will not be
	 * initially sorted
	 * @param rupSet
	 * @param rupExclusionModel if not null, ruptures excluded by this model will be ignored when determining connectivity
	 * @return
	 */
	public static List<ConnectivityCluster> build(FaultSystemRupSet rupSet, BinaryRuptureProbabilityCalc rupExclusionModel) {
		List<ConnectivityCluster> clusters = new ArrayList<>();
		boolean[] sectsAssigned = new boolean[rupSet.getNumSections()];
		
		boolean[][] sectCorups = new boolean[rupSet.getNumSections()][rupSet.getNumSections()];
		
		ClusterRuptures cRups = null;
		HashSet<Integer> exclusionIndexes = null;
		if (rupExclusionModel != null) {
			cRups = rupSet.requireModule(ClusterRuptures.class);
			exclusionIndexes = new HashSet<>();
		}
		
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			if (rupExclusionModel != null && !rupExclusionModel.isRupAllowed(cRups.get(r), false)) {
				exclusionIndexes.add(r);
				continue;
			}
			List<Integer> sects = rupSet.getSectionsIndicesForRup(r);
			for (int i=0; i<sects.size(); i++) {
				int s1 = sects.get(i);
				for (int j=i; j<sects.size(); j++) {
					int s2 = sects.get(j);
					sectCorups[s1][s2] = true;
					sectCorups[s2][s1] = true;
				}
			}
		}
		
		for (int s=0; s<sectCorups.length; s++)
			if (!sectsAssigned[s])
				processClusterRecursive(rupSet, s, clusters.size(), clusters, sectsAssigned, sectCorups);
		
		// now fill in rupture counts and parent IDs
		for (ConnectivityCluster cluster : clusters) {
			BitSet rups = new BitSet(rupSet.getNumRuptures());
			for (int sectID : cluster.sectIDs) {
				FaultSection sect = rupSet.getFaultSectionData(sectID);
				int parentID = sect.getParentSectionId();
				if (parentID >= 0)
					cluster.parentSectIDs.add(parentID);
				for (int r : rupSet.getRupturesForSection(sectID))
					if (exclusionIndexes == null || !exclusionIndexes.contains(r))
						rups.set(r);
			}
			cluster.numRuptures = rups.cardinality();
		}
		
		return clusters;
	}
	
	private static void processClusterRecursive(FaultSystemRupSet rupSet, int sect, int clusterIndex,
			List<ConnectivityCluster> clusters, boolean[] sectsAssigned, boolean[][] sectCorups) {
		if (sectsAssigned[sect])
			// we've already done this one
			return;
		if (clusters.size() == clusterIndex)
			clusters.add(new ConnectivityCluster());
		ConnectivityCluster curCluster = clusters.get(clusterIndex);
		curCluster.numSections++;
		curCluster.sectIDs.add(sect);
		sectsAssigned[sect] = true;

		for (int sect2=0; sect2<sectCorups.length; sect2++)
			if (sectCorups[sect][sect2])
				processClusterRecursive(rupSet, sect2, clusterIndex, clusters, sectsAssigned, sectCorups);
	}

	@Override
	public int compareTo(ConnectivityCluster o) {
		return sectCountComparator.compare(this, o);
	}
	
	@Override
	public String toString() {
		return "ConnectivityCluster["+numSections+" sects, "+numRuptures+" rups]";
	}

	public static final Comparator<ConnectivityCluster> sectCountComparator = new Comparator<ConnectivityCluster>() {
		
		@Override
		public int compare(ConnectivityCluster o1, ConnectivityCluster o2) {
			int cmp = Integer.compare(o1.numSections, o2.numSections);
			if (cmp == 0)
				cmp = Integer.compare(o1.numRuptures, o2.numRuptures);
			return cmp;
		}
	};
	
	public static final Comparator<ConnectivityCluster> rupCountComparator = new Comparator<ConnectivityCluster>() {
		
		@Override
		public int compare(ConnectivityCluster o1, ConnectivityCluster o2) {
			int cmp = Integer.compare(o1.numRuptures, o2.numRuptures);
			if (cmp == 0)
				cmp = Integer.compare(o1.numSections, o2.numSections);
			return cmp;
		}
	};

}
