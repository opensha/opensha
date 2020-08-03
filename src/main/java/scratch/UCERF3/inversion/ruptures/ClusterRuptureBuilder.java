package scratch.UCERF3.inversion.ruptures;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.dom4j.DocumentException;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester;
import scratch.UCERF3.inversion.coulomb.CoulombRatesTester.TestType;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.inversion.ruptures.plausibility.PlausibilityFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.CoulombJunctionFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.CumulativeAzimuthChangeFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.CumulativeRakeChangeFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.MinSectsPerParentFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.SplayLengthFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.TotalAzimuthChangeFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.U3CompatibleCumulativeRakeChangeFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.JumpCumulativeRakeChangeFilter;
import scratch.UCERF3.inversion.ruptures.strategies.ClusterConnectionStrategy;
import scratch.UCERF3.inversion.ruptures.strategies.ClusterPermutationStrategy;
import scratch.UCERF3.inversion.ruptures.strategies.UCERF3ClusterConnectionStrategy;
import scratch.UCERF3.inversion.ruptures.strategies.UCERF3ClusterPermuationStrategy;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.FaultSystemIO;

public class ClusterRuptureBuilder {
	
	private List<FaultSubsectionCluster> clusters;
	private List<PlausibilityFilter> filters;
	private int maxNumSplays = 0;
	
	private RupDebugCriteria debugCriteria;
	private boolean stopAfterDebugMatch;
	
	public ClusterRuptureBuilder(List<? extends FaultSection> subSections,
			ClusterConnectionStrategy connectionStrategy, SectionDistanceAzimuthCalculator distAzCalc,
			List<PlausibilityFilter> filters, int maxNumSplays) {
		this(buildClusters(subSections, connectionStrategy, distAzCalc), filters, maxNumSplays);
	}
	
	public static List<FaultSubsectionCluster> buildClusters(List<? extends FaultSection> subSections,
			ClusterConnectionStrategy connectionStrategy, SectionDistanceAzimuthCalculator distAzCalc) {
		List<FaultSubsectionCluster> clusters = new ArrayList<>();
		
		List<FaultSection> curClusterSects = null;
		int curParentID = -1;
		
		for (FaultSection subSect : subSections) {
			int parentID = subSect.getParentSectionId();
			Preconditions.checkState(parentID >= 0,
					"Subsections are required, but this section doesn't have a parent ID set: %s. %s",
					subSect.getSectionId(), subSect.getSectionName());
			if (parentID != curParentID) {
				if (curClusterSects != null)
					clusters.add(new FaultSubsectionCluster(curClusterSects));
				curParentID = parentID;
				curClusterSects = new ArrayList<>();
			}
			curClusterSects.add(subSect);
		}
		clusters.add(new FaultSubsectionCluster(curClusterSects));
		System.out.println("Building connections for "+subSections.size()
			+" subsections on "+clusters.size()+" parent sections");
		int count = connectionStrategy.addConnections(clusters, distAzCalc);
		System.out.println("Found "+count+" possible section connections");
		
		return clusters;
	}
	
	public ClusterRuptureBuilder(List<FaultSubsectionCluster> clusters,
			List<PlausibilityFilter> filters, int maxNumSplays) {
		this.clusters = clusters;
		this.filters = filters;
		this.maxNumSplays = maxNumSplays;
	}
	
	public void setDebugCriteria(RupDebugCriteria debugCriteria, boolean stopAfterMatch) {
		this.debugCriteria = debugCriteria;
		this.stopAfterDebugMatch = stopAfterMatch;
	}

	private int largestRup = 0;
	private int largestRupPrintMod = 10;
	
	public List<ClusterRupture> build(ClusterPermutationStrategy permutationStrategy) {
		List<ClusterRupture> rups = new ArrayList<>();
		HashSet<UniqueRupture> uniques = new HashSet<>();
		largestRup = 0;
		
		for (FaultSubsectionCluster cluster : clusters) {
			for (FaultSection startSection : cluster.subSects) {
				for (FaultSubsectionCluster permutation : permutationStrategy.getPermutations(
						cluster, startSection)) {
					ClusterRupture rup = new ClusterRupture(permutation);
					PlausibilityResult result = testRup(rup, false);
					if (debugCriteria != null && debugCriteria.isMatch(rup)
							&& debugCriteria.appliesTo(result)) {
						System.out.println("\tPermutation "+permutation+" result="+result);
						testRup(rup, true);
						if (stopAfterDebugMatch)
							return rups;
					}
					if (!result.canContinue())
						// stop building here
						continue;
					if (result.isPass()) {
						// passes as is, add it if it's new
						UniqueRupture unique = new UniqueRupture(rup);
						if (!uniques.contains(unique)) {
							rups.add(rup);
							uniques.add(unique);
							int count = rup.getTotalNumSects();
							if (count > largestRup) {
								largestRup = count;
								if (largestRup % largestRupPrintMod == 0)
									System.out.println("\tNew largest rup has "+largestRup
											+" subsections with "+rup.getTotalNumJumps()+" jumps and "
											+rup.splays.size()+" splays. "+rups.size()+" rups in total");
							}
						}
					}
					// continue to build this rupture
					boolean canContinue = addRuptures(rups, uniques, rup, rup, 
							result.isPass(), permutationStrategy);
					if (!canContinue) {
						System.out.println("Stopping due to debug criteria match with "+rups.size()+" ruptures");
						return rups;
					}
				}
			}
			System.out.println("Have "+rups.size()+" ruptures after processing cluster "
					+cluster.parentSectionID+": "+cluster.parentSectionName);
		}
		
		return rups;
	}
	
	private PlausibilityResult testRup(ClusterRupture rupture, final boolean debug) {
		PlausibilityResult result = PlausibilityResult.PASS;
		for (PlausibilityFilter filter : filters) {
			PlausibilityResult filterResult = filter.apply(rupture, debug);
			if (debug)
				System.out.println("\t\t"+filter.getShortName()+": "+filterResult);
			result = result.logicalAnd(filterResult);
			if (!result.canContinue() && !debug)
				break;
		}
		return result;
	}
	
	private PlausibilityResult offerJump(ClusterRupture rupture, Jump jump, final boolean debug) {
		PlausibilityResult result = PlausibilityResult.PASS;
		for (PlausibilityFilter filter : filters) {
			PlausibilityResult filterResult = filter.testJump(rupture, jump, debug);
			if (debug)
				System.out.println("\t\t"+filter.getShortName()+": "+filterResult);
			result = result.logicalAnd(filterResult);
			if (!result.canContinue() && !debug)
				break;
		}
		return result;
	}
	
	private boolean addRuptures(List<ClusterRupture> rups, HashSet<UniqueRupture> uniques,
			ClusterRupture currentRupture, ClusterRupture currentStrand, boolean testJumpOnly,
			ClusterPermutationStrategy permutationStrategy) {
		FaultSubsectionCluster lastCluster = currentStrand.clusters[currentStrand.clusters.length-1];
		FaultSection firstSection = currentStrand.clusters[0].firstSect;
		FaultSection lastSection = lastCluster.lastSect;

		// try to grow this strand first
		for (Jump jump : lastCluster.getConnections(lastSection)) {
			if (!currentRupture.contains(jump.toSection)) {
				boolean canContinue = addJumpPermutations(rups, uniques, currentRupture, currentStrand,
						testJumpOnly, permutationStrategy, jump);
				if (!canContinue)
					return false;
			}
		}
		
		// now try to add splays
		if (currentStrand == currentRupture && currentRupture.splays.size() < maxNumSplays) {
			for (FaultSubsectionCluster cluster : currentRupture.clusters) {
				for (FaultSection section : cluster.subSects) {
					if (section.equals(firstSection))
						// can't jump from the first section of the rupture
						continue;
					if (section.equals(lastSection))
						// this would be a continuation of the main rupture, not a splay
						break;
					for (Jump jump : cluster.getConnections(section)) {
						if (!currentRupture.contains(jump.toSection)) {
							boolean canContinue = addJumpPermutations(rups, uniques, currentRupture,
									currentStrand, testJumpOnly, permutationStrategy, jump);
							if (!canContinue)
								return false;
						}
					}
				}
			}
		}
		return true;
	}

	private boolean addJumpPermutations(List<ClusterRupture> rups, HashSet<UniqueRupture> uniques, ClusterRupture currentRupture,
			ClusterRupture currentStrand, boolean testJumpOnly, ClusterPermutationStrategy permutationStrategy, Jump jump) {
		for (FaultSubsectionCluster permutation : permutationStrategy.getPermutations(
				jump.toCluster, jump.toSection)) {
			boolean hasLoopback = false;
			for (FaultSection sect : permutation.subSects) {
				if (currentRupture.contains(sect)) {
					// this permutation contains a section already part of this rupture, stop
					hasLoopback = true;
					break;
				}
			}
			if (hasLoopback)
				continue;
			Preconditions.checkState(permutation.firstSect.equals(jump.toSection));
			Jump testJump = new Jump(jump.fromSection, jump.fromCluster,
					jump.toSection, permutation, jump.distance);
			ClusterRupture candidateRupture = null;
			PlausibilityResult result;
			if (testJumpOnly) {
				// previous rupture passed, we can just check this jump (which may be faster)
				result = offerJump(currentRupture, testJump, false);
			} else {
				// previous rupture failed, so we need to test the whole new thing
				candidateRupture = currentRupture.take(testJump);
				result = testRup(candidateRupture, false);
			}
			if (debugCriteria != null && debugCriteria.isMatch(currentRupture, testJump)
					&& debugCriteria.appliesTo(result)) {
				System.out.println("\tMulti "+currentRupture+" => "+testJump.toCluster+" result="+result);
				if (testJumpOnly) {
					System.out.println("Testing at jumps only:");
					offerJump(currentRupture, testJump, true);
					candidateRupture = currentRupture.take(testJump);
				}
				System.out.println("Testing full:");
				testRup(candidateRupture, true);
				if (stopAfterDebugMatch)
					return false;
			}
			if (!result.canContinue()) {
				// stop building this permutation
				continue;
			}
			if (candidateRupture == null)
				candidateRupture = currentRupture.take(testJump);
			if (result.isPass()) {
				// passes as is, add it if it's new
				UniqueRupture unique = new UniqueRupture(candidateRupture);
				if (!uniques.contains(unique)) {
					rups.add(candidateRupture);
					uniques.add(unique);
				}
				int count = candidateRupture.getTotalNumSects();
				if (count > largestRup) {
					largestRup = count;
					if (largestRup % largestRupPrintMod == 0)
						System.out.println("\tNew largest rup has "+largestRup
								+" subsections with "+candidateRupture.getTotalNumJumps()+" jumps and "
								+candidateRupture.splays.size()+" splays. "+rups.size()+" rups in total");
				}
			}
			// continue to build this rupture
			ClusterRupture newCurrentStrand;
			if (currentStrand == currentRupture) {
				newCurrentStrand = candidateRupture;
			} else {
				// we're building a splay, try to continue that one
				newCurrentStrand = null;
				for (ClusterRupture splay : candidateRupture.splays.values()) {
					if (splay.contains(jump.toSection)) {
						newCurrentStrand = splay;
						break;
					}
				}
				Preconditions.checkNotNull(newCurrentStrand);
				FaultSection newLast = newCurrentStrand.clusters[newCurrentStrand.clusters.length-1].lastSect;
				Preconditions.checkState(newLast.equals(permutation.lastSect));
			}
			boolean canContinue = addRuptures(rups, uniques, candidateRupture, newCurrentStrand,
					result.isPass(), permutationStrategy);
			if (!canContinue)
				return false;
		}
		return true;
	}
	
	public static class UniqueRupture {
		private List<Integer> sectsSorted;
		public UniqueRupture(ClusterRupture rup) {
			sectsSorted = new ArrayList<>();
			for (FaultSection sect : rup.buildOrderedSectionList())
				sectsSorted.add(sect.getSectionId());
			Collections.sort(sectsSorted);
		}
		public UniqueRupture(ClusterRupture rup, Jump jump) {
			sectsSorted = new ArrayList<>();
			for (FaultSection sect : rup.buildOrderedSectionList())
				sectsSorted.add(sect.getSectionId());
			for (FaultSection sect : jump.toCluster.subSects)
				sectsSorted.add(sect.getSectionId());
			Collections.sort(sectsSorted);
		}
		public UniqueRupture(List<Integer> rupSects) {
			sectsSorted = new ArrayList<>();
			for (int sect : rupSects)
				sectsSorted.add(sect);
			Collections.sort(sectsSorted);
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((sectsSorted == null) ? 0 : sectsSorted.hashCode());
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
			if (sectsSorted == null) {
				if (other.sectsSorted != null)
					return false;
			} else if (!sectsSorted.equals(other.sectsSorted))
				return false;
			return true;
		}
	}
	
	public static interface RupDebugCriteria {
		public boolean isMatch(ClusterRupture rup);
		public boolean isMatch(ClusterRupture rup, Jump newJump);
		public boolean appliesTo(PlausibilityResult result);
	}
	
	public static class StartEndSectRupDebugCriteria implements RupDebugCriteria {
		
		private int startSect;
		private int endSect;
		private boolean parentIDs;
		private boolean failOnly;

		public StartEndSectRupDebugCriteria(int startSect, int endSect, boolean parentIDs, boolean failOnly) {
			this.startSect = startSect;
			this.endSect = endSect;
			this.parentIDs = parentIDs;
			this.failOnly = failOnly;
		}

		@Override
		public boolean isMatch(ClusterRupture rup) {
			if (startSect >= 0 && !isMatch(rup.clusters[0].firstSect, startSect))
				return false;
			if (endSect >= 0 && !isMatch(rup.clusters[rup.clusters.length-1].lastSect, endSect))
				return false;
			return true;
		}

		@Override
		public boolean isMatch(ClusterRupture rup, Jump newJump) {
			if (startSect >= 0 && !isMatch(rup.clusters[0].firstSect, startSect))
				return false;
			if (endSect >= 0 && !isMatch(newJump.toCluster.lastSect, endSect))
				return false;
			return true;
		}
		
		private boolean isMatch(FaultSection sect, int id) {
			if (parentIDs)
				return sect.getParentSectionId() == id;
			return sect.getSectionId() == id;
		}

		@Override
		public boolean appliesTo(PlausibilityResult result) {
			if (failOnly)
				return !result.isPass();
			return true;
		}
		
	}
	
	public static class ParentSectsRupDebugCriteria implements RupDebugCriteria {
		
		private boolean failOnly;
		private boolean allowAdditional;
		private int[] parentIDs;

		public ParentSectsRupDebugCriteria(boolean failOnly, boolean allowAdditional, int... parentIDs) {
			this.failOnly = failOnly;
			this.allowAdditional = allowAdditional;
			this.parentIDs = parentIDs;
		}
		
		private HashSet<Integer> getParents(ClusterRupture rup) {
			HashSet<Integer> parents = new HashSet<>();
			for (FaultSubsectionCluster cluster : rup.clusters)
				parents.add(cluster.parentSectionID);
			for (ClusterRupture splay : rup.splays.values())
				parents.addAll(getParents(splay));
			return parents;
		}
		
		private boolean test(HashSet<Integer> parents) {
			if (!allowAdditional && parents.size() != parentIDs.length)
				return false;
			for (int parentID : parentIDs)
				if (!parents.contains(parentID))
					return false;
			return true;
		}

		@Override
		public boolean isMatch(ClusterRupture rup) {
			return test(getParents(rup));
		}

		@Override
		public boolean isMatch(ClusterRupture rup, Jump newJump) {
			HashSet<Integer> parents = getParents(rup);
			parents.add(newJump.toCluster.parentSectionID);
			return test(parents);
		}

		@Override
		public boolean appliesTo(PlausibilityResult result) {
			if (failOnly)
				return !result.isPass();
			return true;
		}
		
	}
	
	public static class SectsRupDebugCriteria implements RupDebugCriteria {
		
		private boolean failOnly;
		private boolean allowAdditional;
		private int[] sectIDs;

		public SectsRupDebugCriteria(boolean failOnly, boolean allowAdditional, int... sectIDs) {
			this.failOnly = failOnly;
			this.allowAdditional = allowAdditional;
			this.sectIDs = sectIDs;
		}
		
		private HashSet<Integer> getSects(ClusterRupture rup) {
			HashSet<Integer> sects = new HashSet<>();
			for (FaultSubsectionCluster cluster : rup.clusters)
				for (FaultSection sect : cluster.subSects)
					sects.add(sect.getSectionId());
			for (ClusterRupture splay : rup.splays.values())
				sects.addAll(getSects(splay));
			return sects;
		}
		
		private boolean test(HashSet<Integer> sects) {
			if (!allowAdditional && sects.size() != sectIDs.length)
				return false;
			for (int sectID : sectIDs)
				if (!sects.contains(sectID))
					return false;
			return true;
		}

		@Override
		public boolean isMatch(ClusterRupture rup) {
			return test(getSects(rup));
		}

		@Override
		public boolean isMatch(ClusterRupture rup, Jump newJump) {
			HashSet<Integer> sects = getSects(rup);
			for (FaultSection sect : newJump.toCluster.subSects)
				sects.add(sect.getSectionId());
			return test(sects);
		}

		@Override
		public boolean appliesTo(PlausibilityResult result) {
			if (failOnly)
				return !result.isPass();
			return true;
		}
		
	}
	
	private static int[] loadRupString(String rupStr, boolean parents) {
		Preconditions.checkState(rupStr.contains("["));
		List<Integer> ids = new ArrayList<>();
		while (rupStr.contains("[")) {
			rupStr = rupStr.substring(rupStr.indexOf("[")+1);
			Preconditions.checkState(rupStr.contains(":"));
			if (parents) {
				String str = rupStr.substring(0, rupStr.indexOf(":"));
				ids.add(Integer.parseInt(str));
			} else {
				rupStr = rupStr.substring(rupStr.indexOf(":")+1);
				Preconditions.checkState(rupStr.contains("]"));
				String str = rupStr.substring(0, rupStr.indexOf("]"));
				String[] split = str.split(",");
				for (String idStr : split)
					ids.add(Integer.parseInt(idStr));
			}
		}
		return Ints.toArray(ids);
	}
	
	public static class CompareRupSetNewInclusionCriteria implements RupDebugCriteria {
		
		private HashSet<UniqueRupture> uniques;
		
		public CompareRupSetNewInclusionCriteria(FaultSystemRupSet rupSet) {
			uniques = new HashSet<>();
			for (List<Integer> rupSects : rupSet.getSectionIndicesForAllRups())
				uniques.add(new UniqueRupture(rupSects));
		}

		@Override
		public boolean isMatch(ClusterRupture rup) {
			return !uniques.contains(new UniqueRupture(rup));
		}

		@Override
		public boolean isMatch(ClusterRupture rup, Jump newJump) {
			return !uniques.contains(new UniqueRupture(rup, newJump));
		}

		@Override
		public boolean appliesTo(PlausibilityResult result) {
			return result.isPass();
		}
		
	}
	
	public static class CompareRupSetExclusionCriteria implements RupDebugCriteria {
		
		private HashSet<UniqueRupture> uniques;
		
		public CompareRupSetExclusionCriteria(FaultSystemRupSet rupSet) {
			uniques = new HashSet<>();
			for (List<Integer> rupSects : rupSet.getSectionIndicesForAllRups())
				uniques.add(new UniqueRupture(rupSects));
		}

		@Override
		public boolean isMatch(ClusterRupture rup) {
			return uniques.contains(new UniqueRupture(rup));
		}

		@Override
		public boolean isMatch(ClusterRupture rup, Jump newJump) {
			return uniques.contains(new UniqueRupture(rup, newJump));
		}

		@Override
		public boolean appliesTo(PlausibilityResult result) {
			return !result.isPass();
		}
		
	}

	public static void main(String[] args) throws IOException, DocumentException {
		FaultModels fm = FaultModels.FM3_1;
		DeformationModels dm = fm.getFilterBasis();
		
		DeformationModelFetcher dmFetch = new DeformationModelFetcher(fm, dm,
				null, 0.1);
		
		List<FaultSection> parentSects = fm.fetchFaultSections();
		List<? extends FaultSection> subSects = dmFetch.getSubSectionList();
		
		RupDebugCriteria debugCriteria = null;
		boolean stopAfterDebug = false;

//		FaultSystemRupSet compRupSet = FaultSystemIO.loadRupSet(new File(
//				"/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/"
//				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
//		System.out.println("Loaded "+compRupSet.getNumRuptures()+" comparison ruptures");
//		RupDebugCriteria debugCriteria = new CompareRupSetNewInclusionCriteria(compRupSet);
//		boolean stopAfterDebug = true;
		
//		String rupStr = "[248:2106,2107][124:316,317][152:1644,1645,1646][108:869,870,871][226:2285,2286]"
//				+ "[220:1213,1214,1215,1216,1217,1218,1219,1220,1221,1222][221:1204,1205,1206][219:16,17]";
//		RupDebugCriteria debugCriteria = new SectsRupDebugCriteria(false, false,
//				loadRupString(rupStr, false));
//		boolean stopAfterDebug = true;
		
		CoulombRates coulombRates = CoulombRates.loadUCERF3CoulombRates(fm);
		ClusterConnectionStrategy connectionStrategy = new UCERF3ClusterConnectionStrategy(5d, coulombRates);
//		ClusterConnectionStrategy connectionStrategy = new DistCutoffSingleConnectionClusterConnectionStrategy(5d);
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		File cacheFile = new File("/tmp/dist_az_cache_"+fm.encodeChoiceString()+"_"+subSects.size()
			+"_sects_"+parentSects.size()+"_parents.csv");
		if (cacheFile.exists()) {
			System.out.println("Loading dist/az cache from "+cacheFile.getAbsolutePath());
			distAzCalc.loadCacheFile(cacheFile);
		}
		int numAzCached = distAzCalc.getCachedAzimuths().size();
		int numDistCached = distAzCalc.getCachedDistances().size();
		List<FaultSubsectionCluster> clusters = buildClusters(subSects, connectionStrategy, distAzCalc);
		if (numAzCached < distAzCalc.getCachedAzimuths().size()
				|| numDistCached < distAzCalc.getCachedDistances().size()) {
			System.out.println("Writing dist/az cache to "+cacheFile.getAbsolutePath());
			distAzCalc.writeCacheFile(cacheFile);
			numAzCached = distAzCalc.getCachedAzimuths().size();
			numDistCached = distAzCalc.getCachedDistances().size();
		}
		
		List<PlausibilityFilter> filters = new ArrayList<>();
		AzimuthCalc u3AzCalc = new JumpAzimuthChangeFilter.UCERF3LeftLateralFlipAzimuthCalc(distAzCalc);
		filters.add(new JumpAzimuthChangeFilter(u3AzCalc, 60f));
		filters.add(new TotalAzimuthChangeFilter(u3AzCalc, 60f, true, true));
		filters.add(new CumulativeAzimuthChangeFilter(
				new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc), 560f));
//		filters.add(new CumulativeRakeChangeFilter(180f));
//		filters.add(new JumpCumulativeRakeChangeFilter(180f));
		filters.add(new U3CompatibleCumulativeRakeChangeFilter(180d));
		filters.add(new MinSectsPerParentFilter(2, true, clusters));
		CoulombRatesTester coulombTester = new CoulombRatesTester(
				TestType.COULOMB_STRESS, 0.04, 0.04, 1.25d, true, true);
		filters.add(new CoulombJunctionFilter(coulombTester, coulombRates));
		
		int maxNumSplays = 1;
		if (maxNumSplays > 0)
			filters.add(new SplayLengthFilter(0.2, true, maxNumSplays > 1));
		
		ClusterRuptureBuilder builder = new ClusterRuptureBuilder(clusters, filters, maxNumSplays);
		
		if (debugCriteria != null)
			builder.setDebugCriteria(debugCriteria, stopAfterDebug);
		
		System.out.println("Building ruptures...");
		List<ClusterRupture> rups = builder.build(new UCERF3ClusterPermuationStrategy());
		System.out.println("Built "+rups.size()+" ruptures");
		
		if (debugCriteria == null || !stopAfterDebug) {
			// write out test rup set
			double[] mags = new double[rups.size()];
			double[] rakes = new double[rups.size()];
			double[] rupAreas = new double[rups.size()];
			List<List<Integer>> sectionForRups = new ArrayList<>();
			for (int r=0; r<rups.size(); r++) {
				List<FaultSection> sects = rups.get(r).buildOrderedSectionList();
				List<Integer> ids = new ArrayList<>();
				for (FaultSection sect : sects)
					ids.add(sect.getSectionId());
				sectionForRups.add(ids);
				mags[r] = Double.NaN;
				rakes[r] = Double.NaN;
				rupAreas[r] = Double.NaN;
			}
			FaultSystemRupSet rupSet = new FaultSystemRupSet(subSects, null, null, null, 
				sectionForRups, mags, rakes, rupAreas, null, "");
			FaultSystemIO.writeRupSet(rupSet, new File("/tmp/test_rup_set.zip"));
		}

		if (numAzCached < distAzCalc.getCachedAzimuths().size()
				|| numDistCached < distAzCalc.getCachedDistances().size()) {
			System.out.println("Writing dist/az cache to "+cacheFile.getAbsolutePath());
			distAzCalc.writeCacheFile(cacheFile);
		}
	}

}
