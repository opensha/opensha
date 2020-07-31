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

import scratch.UCERF3.FaultSystemRupSet;
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
import scratch.UCERF3.inversion.ruptures.plausibility.impl.TotalAzimuthChangeFilter;
import scratch.UCERF3.inversion.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import scratch.UCERF3.inversion.ruptures.strategies.ClusterConnectionStrategy;
import scratch.UCERF3.inversion.ruptures.strategies.ClusterPermutationStrategy;
import scratch.UCERF3.inversion.ruptures.strategies.UCERF3ClusterConnectionStrategy;
import scratch.UCERF3.inversion.ruptures.strategies.UCERF3ClusterPermuationStrategy;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;
import scratch.UCERF3.utils.FaultSystemIO;

public class ClusterRuptureBuilder {
	
	private List<FaultSubsectionCluster> clusters;
	private List<PlausibilityFilter> filters;
	private int maxNumSplays = 0;
	
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

	/*
	 * This section has hardcoded variables which enable debug prints for specific ruptures
	 */
	// print debug info for ruptures starting at this section
	private int debugSection = 1333;
	// print debug info for ruptures that also end at this section
	private int debugJumpToSection = 2103;
	// print debug info for ruptures that include all of the below parent sections.
	// if debugSection is specified, must also start there
	private int[] debugSingleRupParents = {646,655,561,657,658,32,285,300,287,286,247};
//	private int[] debugSingleRupParents = null;
	// if true, then no additiona parents are allowed from above for debugging
	private boolean debugSingleRupParentsOnly = true;
	// if true, will stop after debugSection (or compare rupture condition) found
	private boolean stopAfterDebug = true;
	
	private HashSet<UniqueRupture> compRuptures;
	private boolean debugNewInclusions = false;
	private boolean debugExclusions = false;
	
	public void setCompareRupSet(FaultSystemRupSet rupSet, boolean debugNewInclusions,
			boolean debugExclusions) {
		compRuptures = new HashSet<>();
		for (int r=0; r<rupSet.getNumRuptures(); r++)
			compRuptures.add(new UniqueRupture(rupSet.getSectionsIndicesForRup(r)));
		this.debugNewInclusions = debugNewInclusions;
		this.debugExclusions = debugExclusions;
	}
	
	
	
	private int largestRup = 0;
	private int largestRupPrintMod = 10;
	
	public List<ClusterRupture> build(ClusterPermutationStrategy permutationStrategy) {
		List<ClusterRupture> rups = new ArrayList<>();
		HashSet<UniqueRupture> uniques = new HashSet<>();
		largestRup = 0;
		
		for (FaultSubsectionCluster cluster : clusters) {
			for (FaultSection startSection : cluster.subSects) {
				final boolean debug = startSection.getSectionId() == debugSection;
				if (debug)
					System.out.println("Building for section "+debugSection);
				for (FaultSubsectionCluster permutation : permutationStrategy.getPermutations(
						cluster, startSection)) {
					ClusterRupture rup = new ClusterRupture(permutation);
					PlausibilityResult result = testRup(rup, debug);
					if (debug)
						System.out.println("\tPermutation "+permutation+" result="+result);
					if (compRuptures != null) {
						UniqueRupture unique = new UniqueRupture(rup);
						if (debugNewInclusions && result.isPass() && !compRuptures.contains(unique)) {
							System.out.println("\tThis rupture passes but is not in the comparison rup set:");
							System.out.println("\tPermutation "+permutation+" result="+result);
							testRup(rup, true);
							if (stopAfterDebug)
								System.exit(0);
						}
						if (debugExclusions && !result.isPass() && compRuptures.contains(unique)) {
							System.out.println("\tThis rupture fails but is in the comparison rup set:");
							System.out.println("\tPermutation "+permutation+" result="+result);
							testRup(rup, true);
							if (stopAfterDebug)
								System.exit(0);
						}
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
					addRuptures(rups, uniques, rup, rup, result.isPass(), permutationStrategy);
				}
				if (debug) {
					System.out.println("DONE building for section "+debugSection);
					if (stopAfterDebug)
						return rups;
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
	
	private void addRuptures(List<ClusterRupture> rups, HashSet<UniqueRupture> uniques,
			ClusterRupture currentRupture, ClusterRupture currentStrand, boolean testJumpOnly,
			ClusterPermutationStrategy permutationStrategy) {
		FaultSubsectionCluster lastCluster = currentStrand.clusters[currentStrand.clusters.length-1];
		FaultSection firstSection = currentStrand.clusters[0].firstSect;
		FaultSection lastSection = lastCluster.lastSect;

		// try to grow this strand first
		for (Jump jump : lastCluster.getConnections(lastSection)) {
			if (!currentRupture.contains(jump.toSection))
				addJumpPermutations(rups, uniques, currentRupture, currentStrand,
						testJumpOnly, permutationStrategy, jump);
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
						if (!currentRupture.contains(jump.toSection))
							addJumpPermutations(rups, uniques, currentRupture, currentStrand,
									testJumpOnly, permutationStrategy, jump);
					}
				}
			}
		}
	}

	void addJumpPermutations(List<ClusterRupture> rups, HashSet<UniqueRupture> uniques, ClusterRupture currentRupture,
			ClusterRupture currentStrand, boolean testJumpOnly, ClusterPermutationStrategy permutationStrategy, Jump jump) {
		for (FaultSubsectionCluster permutation : permutationStrategy.getPermutations(
				jump.toCluster, jump.toSection)) {
			boolean debug = true;
			if (debugSingleRupParents != null && debugSingleRupParents.length > 0) {
				HashSet<Integer> parents = new HashSet<>();
				parents.add(jump.toCluster.parentSectionID);
				for (FaultSubsectionCluster cluster : currentStrand.clusters)
					parents.add(cluster.parentSectionID);
				debug = debugSingleRupParentsOnly ? parents.size() == debugSingleRupParents.length : true;
				for (int parentID : debugSingleRupParents) {
					if (!parents.contains(parentID)) {
						debug = false;
						break;
					}
				}
				if (debugSection >= 0)
					debug = debug && currentStrand.clusters[0].firstSect.getSectionId() == debugSection;
				if (debugJumpToSection >= 0)
					debug = debug && debugJumpToSection == jump.toSection.getSectionId();
			} else {
				debug = currentStrand.clusters[0].firstSect.getSectionId() == debugSection
						&& (debugJumpToSection < 0 || debugJumpToSection == jump.toSection.getSectionId());
			}
			
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
			if (debug) {
				System.out.println("\tMulti "+currentRupture+" => "+permutation);
			}
			ClusterRupture candidateRupture = null;
			PlausibilityResult result;
			if (testJumpOnly) {
				// previous rupture passed, we can just check this jump (which may be faster)
				result = offerJump(currentRupture, testJump, debug);
			} else {
				// previous rupture failed, so we need to test the whole new thing
				candidateRupture = currentRupture.take(testJump);
				result = testRup(candidateRupture, debug);
			}
			if (debug)
				System.out.println("\t\tresult: "+result);
			if (compRuptures != null) {
				if (candidateRupture == null)
					candidateRupture = currentRupture.take(testJump);
				UniqueRupture unique = new UniqueRupture(candidateRupture);
				if (debugNewInclusions && result.isPass() && !compRuptures.contains(unique)) {
					System.out.println("\tThis rupture passes but is not in the comparison rup set:");
					System.out.println("\tMulti "+currentRupture+" => "+permutation+" result="+result);
					if (testJumpOnly) {
						result = offerJump(currentRupture, testJump, true);
						System.out.println("\tWe did offerJump, testRup result is:");
						testRup(candidateRupture, true);
					} else {
						testRup(candidateRupture, true);
					}
					if (stopAfterDebug)
						System.exit(0);
				}
				if (debugExclusions && !result.isPass() && compRuptures.contains(unique)) {
					if (candidateRupture.splays.isEmpty() && testRup(candidateRupture.reversed(), false).isPass()) {
						// it passes backwards, so skip
						System.out.println("\tThis rupture fails and is in the comparison rup set, but passes when reversed:");
						System.out.println("\tMulti "+currentRupture+" => "+permutation+" result="+result);
					} else {
						System.out.println("\tThis rupture fails but is in the comparison rup set:");
						System.out.println("\tMulti "+currentRupture+" => "+permutation+" result="+result);
						if (testJumpOnly) {
							result = offerJump(currentRupture, testJump, true);
							System.out.println("\tWe did offerJump, but testRup result would be:");
							testRup(candidateRupture, true);
						} else {
							testRup(candidateRupture, true);
						}
						if (stopAfterDebug)
							System.exit(0);
					}
				}
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
			addRuptures(rups, uniques, candidateRupture, newCurrentStrand, result.isPass(), permutationStrategy);
		}
	}
	
	static class UniqueRupture {
		private List<Integer> sectsSorted;
		UniqueRupture(ClusterRupture rup) {
			sectsSorted = new ArrayList<>();
			for (FaultSection sect : rup.buildOrderedSectionList())
				sectsSorted.add(sect.getSectionId());
			Collections.sort(sectsSorted);
		}
		UniqueRupture(List<Integer> rupSects) {
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

	public static void main(String[] args) throws IOException, DocumentException {
		FaultModels fm = FaultModels.FM3_1;
		List<FaultSection> parentSects = fm.fetchFaultSections();
		List<FaultSection> subSects = new ArrayList<>();
		
		FaultSystemRupSet compRupSet = null;
		boolean debugNewInclusions = false;
		boolean debugExclusions = false;
		
//		FaultSystemRupSet compRupSet = FaultSystemIO.loadRupSet(new File(
//				"/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/"
//				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
//		boolean debugNewInclusions = true;
//		boolean debugExclusions = false;
		
//		HashSet<Integer> includeParents = new HashSet<>();
//		includeParents.addAll(fm.getNamedFaultsMapAlt().get("San Andreas"));
//		includeParents.addAll(fm.getNamedFaultsMapAlt().get("Garlock"));
//		includeParents.addAll(fm.getNamedFaultsMapAlt().get("San Jacinto (SB to C)"));
//		includeParents.addAll(fm.getNamedFaultsMapAlt().get("San Jacinto (CC to SM)"));
		HashSet<Integer> includeParents = null;
		
		for (FaultSection sect : parentSects) {
			if (includeParents != null && !includeParents.contains(sect.getSectionId()))
				continue;
			double maxSubSectionLen = 0.5*sect.getOrigDownDipWidth();
			subSects.addAll(sect.getSubSectionsList(maxSubSectionLen, subSects.size(), 2));
		}
		
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
		List<FaultSubsectionCluster> clusters = buildClusters(subSects, connectionStrategy, distAzCalc);
		System.out.println("Writing dist/az cache to "+cacheFile.getAbsolutePath());
		distAzCalc.writeCacheFile(cacheFile);
		
		List<PlausibilityFilter> filters = new ArrayList<>();
		AzimuthCalc u3AzCalc = new JumpAzimuthChangeFilter.UCERF3LeftLateralFlipAzimuthCalc(distAzCalc);
		filters.add(new JumpAzimuthChangeFilter(u3AzCalc, 60f));
		filters.add(new TotalAzimuthChangeFilter(u3AzCalc, 60f, true));
		filters.add(new CumulativeAzimuthChangeFilter(
				new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc), 560f));
		filters.add(new CumulativeRakeChangeFilter(180f));
		filters.add(new MinSectsPerParentFilter(2, true, clusters));
		CoulombRatesTester coulombTester = new CoulombRatesTester(
				TestType.COULOMB_STRESS, 0.04, 0.04, 1.25d, true, true);
		filters.add(new CoulombJunctionFilter(coulombTester, coulombRates));
		
		ClusterRuptureBuilder builder = new ClusterRuptureBuilder(clusters, filters, 0);
		
		if (compRupSet != null)
			builder.setCompareRupSet(compRupSet, debugNewInclusions, debugExclusions);
		
		System.out.println("Building ruptures...");
		List<ClusterRupture> rups = builder.build(new UCERF3ClusterPermuationStrategy());
		System.out.println("Built "+rups.size()+" ruptures");
		
		if (!builder.stopAfterDebug) {
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
		
		System.out.println("Writing dist/az cache to "+cacheFile.getAbsolutePath());
		distAzCalc.writeCacheFile(cacheFile);
	}

}
