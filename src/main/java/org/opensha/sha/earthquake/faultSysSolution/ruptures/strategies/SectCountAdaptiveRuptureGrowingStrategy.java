package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.io.IOException;
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
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter.PlausibilityFilterTypeAdapter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * Adaptive rupture growing strategy that requires that each subsequent variant increase the total subsection count
 * by at least the given fraction. The ends of fault sections will never be skipped, and if maintainConnectivity == true
 * then neither will connection points to other faults.
 * 
 * @author kevin
 *
 */
public class SectCountAdaptiveRuptureGrowingStrategy implements RuptureGrowingStrategy {
	
	private float minFractSectIncrease;
	private boolean maintainConnectivity;
	private RuptureGrowingStrategy exhaustiveStrategy;
	private int minSectsPerParent = 1;

	public SectCountAdaptiveRuptureGrowingStrategy(float minFractSectIncrease, boolean maintainConnectivity, int minSectsPerParent) {
		this(new ExhaustiveUnilateralRuptureGrowingStrategy(), minFractSectIncrease, maintainConnectivity, minSectsPerParent);
	}

	/**
	 * 
	 * @param exhaustiveStrategy exhaustive growing strategy that we will filter
	 * @param minFractSectIncrease dictates that variants must increase the section count over the prior rupture by at least
	 * this percent
	 * @param maintainConnectivity if true, connection points will not be skipped
	 * @param minSectsPerParent knowledge of the minimum sections per parents will help this strategy to return variants
	 * that will pass plausibility filters. For example, if minSectsPerParent=2, minFractSectIncrease=0.1, and you are
	 * adding sections to a 10 section rupture, the variant sizes returned would be [1,3,...]. But that first single
	 * subsection variant would be filtered out, so a better set would be [2,4,...]
	 */
	public SectCountAdaptiveRuptureGrowingStrategy(RuptureGrowingStrategy exhaustiveStrategy,
			float minFractSectIncrease, boolean maintainConnectivity, int minSectsPerParent) {
		this.exhaustiveStrategy = exhaustiveStrategy;
		this.minFractSectIncrease = minFractSectIncrease;
		this.maintainConnectivity = maintainConnectivity;
		this.minSectsPerParent = minSectsPerParent;
	}

	@Override
	public List<FaultSubsectionCluster> getVariations(FaultSubsectionCluster fullCluster,
			FaultSection firstSection) {
		return exhaustiveStrategy.getVariations(fullCluster, firstSection);
//		return getPermutations(null, fullCluster, firstSection);
	}
	
	public static HashSet<Integer> getValidAdditionCounts(int originalRupSize, int newClusterSize,
			float minFractSectIncrease, int minSectsPerParent) {
		HashSet<Integer> validAddSizes = new HashSet<>();
		int lastValidSize = originalRupSize;
		for (int i=minSectsPerParent-1; i<newClusterSize; i++) {
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
	public List<FaultSubsectionCluster> getVariations(ClusterRupture currentRupture,
			FaultSubsectionCluster fullCluster, FaultSection firstSection) {
		List<FaultSubsectionCluster> exhaustivePerms = exhaustiveStrategy.getVariations(currentRupture, fullCluster, firstSection);
		if (currentRupture == null)
			return exhaustivePerms;
		
		HashSet<Integer> validAddSizes = getValidAdditionCounts(currentRupture.getTotalNumSects(),
				fullCluster.subSects.size(), minFractSectIncrease, minSectsPerParent);
//		boolean D = currentRupture != null && currentRupture.getTotalNumClusters() == 4
//				&& fullCluster.parentSectionID == 219 && currentRupture.clusters[0].parentSectionID == 240
//				&& currentRupture.getTotalNumSects() == 10
//				&& currentRupture.clusters[0].subSects.get(0).getSectionId() == 1637;
		final boolean D = false;
		if (D) {
			System.out.println("Getting permutations with currentRupture="+currentRupture);
			System.out.println("\tfull cluster: "+fullCluster);
			System.out.println("\tstart section: "+firstSection.getSectionId());
			System.out.println("\tvalid sizes: "+Joiner.on(",").join(validAddSizes));
		}
		
		List<FaultSubsectionCluster> permutations = new ArrayList<>();
		List<FaultSection> fullSects = fullCluster.subSects;
		Set<FaultSection> exitPoints = fullCluster.getExitPoints();
		
		for (FaultSubsectionCluster permutation : exhaustivePerms) {
			if (validAddSizes.contains(permutation.subSects.size())) {
				// simplest case, it's a valid size
				if (D) System.out.println("\t\tperm PASSES, valid size ("+permutation.subSects.size()+") "+permutation);
				permutations.add(permutation);
				continue;
			}
			// now see if it ruptures to an end
			List<FaultSection> myEnds = new ArrayList<>();
			myEnds.add(permutation.subSects.get(permutation.subSects.size()-1));
			if (!permutation.subSects.get(0).equals(firstSection))
				// this is a T jump, so the "start" section after the jump isn't first in the cluster, also check that end
				myEnds.add(permutation.subSects.get(0));
			boolean rupturesToEnds = true;
			boolean rupturesToConnPoints = true;
			for (FaultSection endSect : myEnds) {
				rupturesToEnds = rupturesToEnds && (endSect.equals(fullSects.get(0)) || endSect.equals(fullSects.get(fullSects.size()-1)));
				rupturesToConnPoints = rupturesToConnPoints && exitPoints.contains(endSect);
			}
			if (rupturesToEnds || (maintainConnectivity && rupturesToConnPoints)) {
				if (D) System.out.println("\t\tperm PASSES. toEnds="+rupturesToEnds+", toConnPoints="
						+rupturesToConnPoints+", size="+permutation.subSects.size()+" "+permutation);
				permutations.add(permutation);
			} else if (D) {
				System.out.println("\t\tperm FAILS. toEnds="+rupturesToEnds+", toConnPoints="
						+rupturesToConnPoints+", size="+permutation.subSects.size()+" "+permutation);
			}
		}
		return permutations;
	}
	
	public ConnPointCleanupFilter buildConnPointCleanupFilter(ClusterConnectionStrategy connStrat) {
		Preconditions.checkState(maintainConnectivity,
				"Connection point cleanup filter only applies if we're maintaining connectivity");
		return new ConnPointCleanupFilter(minFractSectIncrease, minSectsPerParent, connStrat);
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
		private int minSectsPerParent = 1;
		private ClusterConnectionStrategy connStrat;
		private transient Map<Integer, FaultSubsectionCluster> fullClusters;

		public ConnPointCleanupFilter(float minFractSectIncrease, int minSectsPerParent, ClusterConnectionStrategy connStrat) {
			this.minFractSectIncrease = minFractSectIncrease;
			this.minSectsPerParent = minSectsPerParent;
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
						+cluster.parentSectionID+" with size="+newSize+": "+cluster);
			if (newSize == fullSize) {
				// this is a full cluster, easy pass
				if (verbose)
					System.out.println(getShortName()+": passes because it is the full cluster");
				return PlausibilityResult.PASS;
			}
			FaultSection lastSect = cluster.subSects.get(newSize-1);
			int lastID = lastSect.getSectionId();
			if (lastID == fullCluster.subSects.get(0).getSectionId()
					|| lastID == fullCluster.subSects.get(fullSize-1).getSectionId()) {
				// pass if this cluster ends at either the start or end of the full cluster
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
					rupSizeBefore, fullCluster.subSects.size(), minFractSectIncrease, minSectsPerParent);
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

		@Override
		public TypeAdapter<PlausibilityFilter> getTypeAdapter() {
			return new ConnPointsAdapter();
		}
		
	}
	
	public static class ConnPointsAdapter extends PlausibilityFilterTypeAdapter {

		private ClusterConnectionStrategy connStrategy;

		@Override
		public void init(ClusterConnectionStrategy connStrategy, SectionDistanceAzimuthCalculator distAzCalc,
				Gson gson) {
			this.connStrategy = connStrategy;
		}

		@Override
		public void write(JsonWriter out, PlausibilityFilter value) throws IOException {
			Preconditions.checkState(value instanceof ConnPointCleanupFilter);
			ConnPointCleanupFilter filter = (ConnPointCleanupFilter)value;
			out.beginObject();
			out.name("minFractSectIncrease").value(filter.minFractSectIncrease);
			out.name("minSectsPerParent").value(filter.minSectsPerParent);
			out.endObject();
		}

		@Override
		public PlausibilityFilter read(JsonReader in) throws IOException {
			Preconditions.checkNotNull(connStrategy);
			in.beginObject();
			Float minFractSectIncrease = null;
			int minSectsPerParent = 1;
			while (in.hasNext()) {
				String name = in.nextName();
				switch (name) {
				case "minFractSectIncrease":
					minFractSectIncrease = (float)in.nextDouble();
					break;
				case "minSectsPerParent":
					minSectsPerParent = in.nextInt();
					break;

				default:
					throw new IllegalStateException("Unexpected name: "+name);
				}
			}
			Preconditions.checkNotNull(minFractSectIncrease);
			in.endObject();
			return new ConnPointCleanupFilter(minFractSectIncrease, minSectsPerParent, connStrategy);
		}
		
	}
	
	public static void main(String[] args) {
		int[] origCounts = { 0, 5, 10, 15, 20, 30, 50, 100 };
		int[] clusterSizes = { 5, 13, 20 };
		float fract = 0.1f;
		int minSectsPerParent = 2;
		
		System.out.println("Fractional increase: "+fract);
		for (int origCount : origCounts) {
			System.out.println("Valid add counts for rupture of size "+origCount);
			for (int clusterSize : clusterSizes) {
				System.out.println("\tNext cluster size: "+clusterSize);
				List<Integer> sizes = new ArrayList<>(getValidAdditionCounts(origCount, clusterSize, fract, minSectsPerParent));
				Collections.sort(sizes);
				System.out.println("\t\tValid additions: "+Joiner.on(",").join(sizes));
			}
		}
	}

	@Override
	public String getName() {
		String ret = exhaustiveStrategy.getName().replace("Exhaustive", "").trim();
//		if (exhaustiveStrategy instanceof ExhaustiveBilateralClusterPermuationStrategy)
//			ret = "Bilateral, ";
//		else if (exhaustiveStrategy instanceof ExhaustiveUnilateralClusterPermuationStrategy)
//			ret = "Unilateral, ";
//		else if (exhaustiveStrategy.getName() != null)
//			ret = exhaustiveStrategy.getName()+", ";
//		else
//			ret = "";
		while (ret.startsWith(",") || ret.startsWith(";"))
			ret = ret.substring(1).trim();
		while (ret.endsWith(",") || ret.endsWith(";"))
			ret = ret.substring(0, ret.length()-1).trim();
		if (!ret.isEmpty())
			ret += ", ";
		ret += "Adaptive, "+optionalDigitPDF.format(minFractSectIncrease)+" Sect Increase";
		if (maintainConnectivity)
			ret += ", Maintain Connectivity";
		return ret;
	}
	
	@Override
	public void clearCaches() {
		exhaustiveStrategy.clearCaches();
	}

}
