package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Exhaustive bilateral rupture growing strategy, which assumes that subsections are listed in order and connections
 * only exist between neighbors in that list. This generates both unilateral and bilateral (T ruptures) variants.
 * @author kevin
 *
 */
public class ExhaustiveBilateralRuptureGrowingStrategy implements RuptureGrowingStrategy {
	
	private static final boolean D = false;
	
	public enum SecondaryVariations {
		ALL("Both Ends Varied") {
			@Override
			public List<Integer> getValidSecondaryLengths(int secondaryEndLength, int primaryEndLength) {
				List<Integer> ret = new ArrayList<>();
				for (int i=1; i<=secondaryEndLength; i++)
					ret.add(i);
				return ret;
			}
		},
		SINGLE_FULL("Seondary End Fully Ruptures") {
			@Override
			public List<Integer> getValidSecondaryLengths(int secondaryEndLength, int primaryEndLength) {
				return Lists.newArrayList(secondaryEndLength);
			}
		},
		EQUAL_LEN("Secondary End Equal") {
			@Override
			public List<Integer> getValidSecondaryLengths(int secondaryEndLength, int primaryEndLength) {
				return Lists.newArrayList(Integer.min(secondaryEndLength, primaryEndLength));
			}
		};
		
		private String name;

		private SecondaryVariations(String name) {
			this.name = name;
		}
		
		public abstract List<Integer> getValidSecondaryLengths(int secondaryEndLength, int primaryEndLength);
		
		public String toString() {
			return name;
		}
	}
	
	private SecondaryVariations secondaryPermutations;
	private boolean allowLongerSecondary;
	
	private RuptureGrowingStrategy firstClusterStrat;
	private RuptureGrowingStrategy additionalClusterStrat;

	public ExhaustiveBilateralRuptureGrowingStrategy(final SecondaryVariations secondaryPermutations, final boolean allowLongerSecondary) {
		this.secondaryPermutations = secondaryPermutations;
		this.allowLongerSecondary = allowLongerSecondary;
		
		firstClusterStrat = new ExhaustiveUnilateralRuptureGrowingStrategy();
		additionalClusterStrat = new CachedRuptureGrowingStrategy() {
			
			@Override
			public String getName() {
				return null;
			}
			
			@Override
			protected List<FaultSubsectionCluster> calcPermutations(FaultSubsectionCluster fullCluster,
					FaultSection firstSection) {
				List<FaultSection> clusterSects = fullCluster.subSects;
				int myInd = fullCluster.subSects.indexOf(firstSection);
				Preconditions.checkState(myInd >= 0, "first section not found in cluster");
				
				List<Integer> endPoints = new ArrayList<>(clusterSects.size());
				endPoints.add(myInd);
				for (int i=myInd; --i>=0;)
					endPoints.add(i);
				for (int i=myInd+1; i<clusterSects.size(); i++)
					endPoints.add(i);
//				List<Integer> startPoints = new ArrayList<>();
//				for (int i=0; i<clusterSects.size(); i++)
//					if (!otherEndConnPointsOnly || i == 0 || i == myInd || i == clusterSects.size()-1 || exitPoints.contains(clusterSects.get(i)))
//						startPoints.add(i);
				
				if (D) System.out.println("Builing perms for cluster "+fullCluster+" starting at "+firstSection.getSectionId()+":");
				
				List<FaultSubsectionCluster> permuations = new ArrayList<>();
				for (int endIndex : endPoints) {
					if (endIndex == myInd) {
						// simple case, 1 section
						if (D) System.out.println("\tend="+firstSection.getSectionId()+", start="+firstSection.getSectionId());
						permuations.add(buildCopyJumps(fullCluster, Lists.newArrayList(firstSection), firstSection));
						continue;
					}
					// find valid start points
					int secondaryEndLength, primaryEndLength;
					if (endIndex < myInd) {
						secondaryEndLength = clusterSects.size() - myInd - 1;
						primaryEndLength = myInd - endIndex;
					} else {
						secondaryEndLength = myInd;
						primaryEndLength = endIndex - myInd;
					}
					List<Integer> startPoints = new ArrayList<>();
					startPoints.add(myInd); // can always start at the jump-to point
					if (secondaryEndLength > 0) {
						List<Integer> validLengths = secondaryPermutations.getValidSecondaryLengths(secondaryEndLength, primaryEndLength);
						for (int len : validLengths) {
							Preconditions.checkState(len > 0 && len <= secondaryEndLength, "Bad len=%s w/ secondaryEndLength=%s", len, secondaryEndLength);
							if (!allowLongerSecondary && len > primaryEndLength)
								continue;
							if (endIndex < myInd)
								// end is closer to the start of the full cluster, so my start should be toward the end
								startPoints.add(myInd + len);
							else
								// end is closer to the end of the full cluster, so my start should be toward the start
								startPoints.add(myInd - len);
						}
					}
					
					for (int startIndex : startPoints) {
						Preconditions.checkState(startIndex != endIndex);
						List<FaultSection> sects;
						if (startIndex < endIndex) {
							sects = clusterSects.subList(startIndex, endIndex+1);
						} else {
							sects = new ArrayList<>();
							sects.add(clusterSects.get(startIndex));
							for (int i=startIndex; --i>=endIndex;)
								sects.add(clusterSects.get(i));
						}
						if (D) System.out.println("\tend="+clusterSects.get(endIndex).getSectionId(
								)+", start="+clusterSects.get(startIndex).getSectionId());
						permuations.add(buildCopyJumps(fullCluster, sects, firstSection));
					}
				}
				return permuations;
			}
		};
	}
	
	private static FaultSubsectionCluster buildCopyJumps(FaultSubsectionCluster fullCluster,
			List<FaultSection> subsetSects, FaultSection startSect) {
		// this version doesn't need to wrap susetSects in a new list, as it's unique each time
		FaultSubsectionCluster permutation = new FaultSubsectionCluster(subsetSects, startSect, null);
		for (FaultSection sect : subsetSects)
			for (Jump jump : fullCluster.getConnections(sect))
				permutation.addConnection(new Jump(sect, permutation,
						jump.toSection, jump.toCluster, jump.distance));
		if (D) System.out.println("\t\t"+permutation);
		return permutation;
	}

	@Override
	public String getName() {
		String ret = "Exhaustive Bilateral, "+secondaryPermutations.toString();
		if (allowLongerSecondary)
			ret += ", Seondary Can Be Longer";
		return ret;
	}

	@Override
	public List<FaultSubsectionCluster> getVariations(FaultSubsectionCluster fullCluster, FaultSection firstSection) {
		return firstClusterStrat.getVariations(fullCluster, firstSection);
	}

	@Override
	public List<FaultSubsectionCluster> getVariations(ClusterRupture currentRupture,
			FaultSubsectionCluster fullCluster, FaultSection firstSection) {
		if (currentRupture == null)
			return firstClusterStrat.getVariations(fullCluster, firstSection);
		return additionalClusterStrat.getVariations(fullCluster, firstSection);
	}
	
	@Override
	public void clearCaches() {
		firstClusterStrat.clearCaches();
		additionalClusterStrat.clearCaches();
	}

}
