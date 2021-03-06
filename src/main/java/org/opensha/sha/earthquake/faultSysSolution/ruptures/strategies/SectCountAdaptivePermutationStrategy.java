package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class SectCountAdaptivePermutationStrategy implements ClusterPermutationStrategy {
	
	private float minFractSectIncrease;
	private boolean maintainConnectivity;

	public SectCountAdaptivePermutationStrategy(float minFractSectIncrease, boolean maintainConnectivity) {
		this.minFractSectIncrease = minFractSectIncrease;
		this.maintainConnectivity = maintainConnectivity;
	}

	@Override
	public List<FaultSubsectionCluster> getPermutations(FaultSubsectionCluster fullCluster,
			FaultSection firstSection) {
		return getPermutations(null, fullCluster, firstSection);
	}
	
	public static HashSet<Integer> getValidAdditionCounts(int originalRupSize, int newClusterSize,
			float minFractSectIncrease) {
		HashSet<Integer> validAddSizes = new HashSet<>();
		int lastValidSize = originalRupSize;
		for (int i=0; i<newClusterSize; i++) {
			int newSize = originalRupSize + i + 1;
			int newDelta = newSize - lastValidSize;
			double fractAdded = (double)newDelta/(double)lastValidSize;
			if ((float)fractAdded >= minFractSectIncrease) {
				// this is a valid size
				validAddSizes.add(i+1);
				lastValidSize = newSize;
			}
		}
		return validAddSizes;
	}
	
	public float getFractIncrease() {
		return minFractSectIncrease;
	}
	
	public boolean isMaintainConnectivity() {
		return maintainConnectivity;
	}
	
	@Override
	public List<FaultSubsectionCluster> getPermutations(ClusterRupture currentRupture,
			FaultSubsectionCluster fullCluster, FaultSection firstSection) {
		HashSet<Integer> validAddSizes = null;
		if (currentRupture != null)
			validAddSizes = getValidAdditionCounts(currentRupture.getTotalNumSects(),
					fullCluster.subSects.size(), minFractSectIncrease);
//		boolean D = currentRupture != null && currentRupture.getTotalNumClusters() == 4
//				&& fullCluster.parentSectionID == 219 && currentRupture.clusters[0].parentSectionID == 240
//				&& currentRupture.getTotalNumSects() == 10
//				&& currentRupture.clusters[0].subSects.get(0).getSectionId() == 1637;
		boolean D = false;
		if (D) {
			System.out.println("Getting permutations with currentRupture="+currentRupture);
			System.out.println("\tfull cluster: "+fullCluster);
			System.out.println("\tstart section: "+firstSection.getSectionId());
			System.out.println("\tvalid sizes: "+Joiner.on(",").join(validAddSizes));
		}
		
		List<FaultSection> clusterSects = fullCluster.subSects;
		int myInd = fullCluster.subSects.indexOf(firstSection);
		Preconditions.checkState(myInd >= 0, "first section not found in cluster");
		List<FaultSection> newSects = new ArrayList<>();
		newSects.add(firstSection);
		
		Set<FaultSection> exitPoints = fullCluster.getExitPoints();
		
		List<FaultSubsectionCluster> permutations = new ArrayList<>();
		
		// just this section
		if (validAddSizes == null || validAddSizes.contains(1) || exitPoints.contains(firstSection))
			permutations.add(buildCopyJumps(fullCluster, newSects));
		
		// build toward the smallest ID
		if (myInd > 0) {
			for (int i=myInd; --i>=0;) {
				FaultSection nextSection = clusterSects.get(i);
				newSects.add(nextSection);
				// keep section endpoints, and all valid sizes or everything if no rupture supplied
				boolean valid = i == 0 || validAddSizes == null || validAddSizes.contains(newSects.size());
				if (!valid && maintainConnectivity && exitPoints.contains(nextSection))
					// this is a possible jump to another section, keep it anyway
					valid = true;
				if (valid)
					permutations.add(buildCopyJumps(fullCluster, newSects));
			}
		}
		
		if (myInd < clusterSects.size()-1) {
			newSects = new ArrayList<>();
			newSects.add(firstSection);
			// build toward the largest ID
			for (int i=myInd+1; i<clusterSects.size(); i++) {
				FaultSection nextSection = clusterSects.get(i);
				newSects.add(nextSection);
				// keep section endpoints, and all valid sizes or everything if no rupture supplied
				boolean valid = i == clusterSects.size()-1 || validAddSizes == null
						|| validAddSizes.contains(newSects.size());
				if (!valid && maintainConnectivity && exitPoints.contains(nextSection))
					// this is a possible jump to another section, keep it anyway
					valid = true;
				if (valid)
					permutations.add(buildCopyJumps(fullCluster, newSects));
			}
		}
		if (D) {
			System.out.println("\tPermutations:");
			for (FaultSubsectionCluster p : permutations)
				System.out.println("\t\t"+p);
		}
		return permutations;
	}

	private static FaultSubsectionCluster buildCopyJumps(FaultSubsectionCluster fullCluster,
			List<FaultSection> subsetSects) {
		FaultSubsectionCluster permutation = new FaultSubsectionCluster(new ArrayList<>(subsetSects));
		for (FaultSection sect : subsetSects)
			for (Jump jump : fullCluster.getConnections(sect))
				permutation.addConnection(new Jump(sect, permutation,
						jump.toSection, jump.toCluster, jump.distance));
		return permutation;
	}
	
	public ConnPointCleanupFilter buildConnPointCleanupFilter(ClusterConnectionStrategy connStrat) {
		Preconditions.checkState(maintainConnectivity,
				"Connection point cleanup filter only applies if we're maintaining connectivity");
		return new ConnPointCleanupFilter(minFractSectIncrease, connStrat);
	}
	
	private static final DecimalFormat optionalDigitPDF = new DecimalFormat("0.#%");
	
	/**
	 * This will cleanup any ruptures where permutation end points were kept only because they are
	 * potential connection points, but those connection points weren't actually used in the rupture.
	 * 
	 * @author kevin
	 *
	 */
	public static class ConnPointCleanupFilter implements PlausibilityFilter {
		
		private float minFractSectIncrease;
		private ClusterConnectionStrategy connStrat;
		private transient Map<Integer, FaultSubsectionCluster> fullClusters;

		public ConnPointCleanupFilter(float minFractSectIncrease, ClusterConnectionStrategy connStrat) {
			this.minFractSectIncrease = minFractSectIncrease;
			this.connStrat = connStrat;
		}

		@Override
		public String getShortName() {
			return optionalDigitPDF.format(minFractSectIncrease)+"SectCleanup";
		}

		@Override
		public String getName() {
			return optionalDigitPDF.format(minFractSectIncrease)+" Sect Increase Cleanup";
		}
		
		private synchronized FaultSubsectionCluster getFullCluster(int parentSectionID) {
			if (fullClusters == null) {
				fullClusters = new HashMap<>();
				for (FaultSubsectionCluster cluster : connStrat.getClusters())
					fullClusters.put(cluster.parentSectionID, cluster);
			}
			return fullClusters.get(parentSectionID);
		}

		@Override
		public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
			if (rupture.getTotalNumClusters() == 1)
				return PlausibilityResult.PASS;
			// must build out the full rupture (and check all paths)
			FaultSubsectionCluster firstCluster = rupture.clusters[0];
			PlausibilityResult result = PlausibilityResult.PASS;
			RuptureTreeNavigator navigator = rupture.getTreeNavigator();
			for (FaultSubsectionCluster descendant : navigator.getDescendants(firstCluster)) {
				result = result.logicalAnd(
						testPathRecursive(firstCluster.subSects.size(), navigator, descendant, verbose));
				if (!result.isPass())
					return result;
			}
			return result;
		}
		
		private PlausibilityResult testPathRecursive(int sizeSoFar, RuptureTreeNavigator navigator,
				FaultSubsectionCluster nextCluster, boolean verbose) {
			PlausibilityResult result = testCluster(sizeSoFar, nextCluster, navigator, verbose);
			if (!result.isPass())
				return result;
			
			for (FaultSubsectionCluster descendant : navigator.getDescendants(nextCluster)) {
				result = result.logicalAnd(testPathRecursive(
						sizeSoFar+nextCluster.subSects.size(), navigator, descendant, verbose));
				if (!result.isPass())
					return result;
			}
			
			return result;
		}
		
		private PlausibilityResult testCluster(int rupSizeBefore, FaultSubsectionCluster cluster,
				RuptureTreeNavigator navigator, boolean verbose) {
			FaultSubsectionCluster fullCluster = getFullCluster(cluster.parentSectionID);
			int newSize = cluster.subSects.size();
			int fullSize = fullCluster.subSects.size();
			if (verbose)
				System.out.println("Testing rupture of size="+rupSizeBefore+" to new cluster "
						+cluster.parentSectionID+" with size="+newSize);
			if (newSize == fullSize) {
				// this is a full cluster, easy pass
				if (verbose)
					System.out.println(getShortName()+": passes because it is the full cluster");
				return PlausibilityResult.PASS;
			}
			FaultSection lastSect = cluster.subSects.get(newSize-1);
			int lastID = lastSect.getSectionId();
//			if (lastID == fullCluster.subSects.get(0).getSectionId()
//					|| lastID == fullCluster.subSects.get(fullSize-1).getSectionId()) {
				// pass if this cluster ends at either the start or end of the full cluster
			if (lastID == fullCluster.subSects.get(fullSize-1).getSectionId()) {
				if (verbose)
					System.out.println(getShortName()+": passes because "+lastID
							+" is a cluster end (fullCluster="+fullCluster+")");
				return PlausibilityResult.PASS;
			}
			if (navigator != null) {
				// test that it's not a used connection point
				for (FaultSection descendant : navigator.getDescendants(lastSect)) {
					if (descendant.getParentSectionId() != cluster.parentSectionID) {
						// it is a used connection point in this rupture
						if (verbose)
							System.out.println(getShortName()+": passes because it's a used connection to "
									+descendant.getParentSectionId());
						return PlausibilityResult.PASS;
					}
				}
			}
			// check that it's a valid size
			HashSet<Integer> validSizes = getValidAdditionCounts(
					rupSizeBefore, fullCluster.subSects.size(), minFractSectIncrease);
			if (validSizes.contains(newSize)) {
				if (verbose) {
					List<Integer> sizes = new ArrayList<>(validSizes);
					Collections.sort(sizes);
					System.out.println(getShortName()+": passes because it a valid size, "+newSize
							+" is in ("+Joiner.on(",").join(sizes)+")");
				}
				return PlausibilityResult.PASS;
			}
			// future possible becuase a jump could be taken to use this section as a connection point
			if (verbose) {
				List<Integer> sizes = new ArrayList<>(validSizes);
				Collections.sort(sizes);
				System.out.println(getShortName()+": failing because it not a valid size, "+newSize
						+" is not in ("+Joiner.on(",").join(sizes)+")");
			}
			return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return true;
		}
		
	}
	
	public static void main(String[] args) {
		int[] origCounts = { 0, 5, 10, 15, 20, 30, 50, 100 };
		int[] clusterSizes = { 5, 13, 20 };
		float fract = 0.05f;
		
		System.out.println("Fractional increase: "+fract);
		for (int origCount : origCounts) {
			System.out.println("Valid add counts for rupture of size "+origCount);
			for (int clusterSize : clusterSizes) {
				System.out.println("\tNext cluster size: "+clusterSize);
				List<Integer> sizes = new ArrayList<>(getValidAdditionCounts(origCount, clusterSize, fract));
				Collections.sort(sizes);
				System.out.println("\t\tValid additions: "+Joiner.on(",").join(sizes));
			}
		}
	}

	@Override
	public String getName() {
		String ret = "Adaptive, "+optionalDigitPDF.format(minFractSectIncrease)+" Sect Increase";
		if (maintainConnectivity)
			ret += ", Maintain Connectivity";
		return ret;
	}

}
