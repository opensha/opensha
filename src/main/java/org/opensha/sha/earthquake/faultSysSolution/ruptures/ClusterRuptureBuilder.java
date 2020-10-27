package org.opensha.sha.earthquake.faultSysSolution.ruptures;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.dom4j.DocumentException;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration.Builder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.ParentCoulombCompatibilityFilter.Directionality;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.DistCutoffClosestSectClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.SectCountAdaptivePermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ConnectionPointsPermutationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.UCERF3ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.UCERF3ClusterPermuationStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.RuptureCoulombResult.RupCoulombQuantity;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessAggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Ints;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.FaultSystemIO;

/**
 * Code to recursively build ClusterRuptures, applying any rupture plausibility filters
 * @author kevin
 *
 */
public class ClusterRuptureBuilder {
	
	private List<FaultSubsectionCluster> clusters;
	private List<PlausibilityFilter> filters;
	private int maxNumSplays = 0;
	
	private RupDebugCriteria debugCriteria;
	private boolean stopAfterDebugMatch;
	
	/**
	 * Constructor which gets everything from the PlausibilityConfiguration
	 * 
	 * @param configuration plausibilty configuration
	 */
	public ClusterRuptureBuilder(PlausibilityConfiguration configuration) {
		this(configuration.getConnectionStrategy().getClusters(), configuration.getFilters(),
				configuration.getMaxNumSplays());
	}
	
	/**
	 * Constructor which uses previously built clusters (with connections added)
	 * 
	 * @param clusters list of clusters (with connections added)
	 * @param filters list of plausibility filters
	 * @param maxNumSplays the maximum number of splays per rupture (use 0 to disable splays)
	 */
	public ClusterRuptureBuilder(List<FaultSubsectionCluster> clusters,
			List<PlausibilityFilter> filters, int maxNumSplays) {
		this.clusters = clusters;
		this.filters = filters;
		this.maxNumSplays = maxNumSplays;
	}
	
	/**
	 * This allows you to debug the rupture building process. It will print out a lot of details
	 * if the given criteria are satisfied.
	 * 
	 * @param debugCriteria criteria for which to print debug information
	 * @param stopAfterMatch if true, building will cease immediately after a match is found 
	 */
	public void setDebugCriteria(RupDebugCriteria debugCriteria, boolean stopAfterMatch) {
		this.debugCriteria = debugCriteria;
		this.stopAfterDebugMatch = stopAfterMatch;
	}

	private int largestRup = 0;
	private int largestRupPrintMod = 10;
	
	/**
	 * This builds ruptures using the given cluster permutation strategy
	 * 
	 * @param permutationStrategy strategy for determining unique & viable subsection permutations 
	 * for each cluster 
	 * @return list of unique ruptures which were build
	 */
	public List<ClusterRupture> build(ClusterPermutationStrategy permutationStrategy) {
		return build(permutationStrategy, 1);
	}
	
	/**
	 * This builds ruptures using the given cluster permutation strategy with the given number of threads
	 * 
	 * @param permutationStrategy strategy for determining unique & viable subsection permutations 
	 * for each cluster 
	 * @param numThreads
	 * @return list of unique ruptures which were build
	 */
	public List<ClusterRupture> build(ClusterPermutationStrategy permutationStrategy, int numThreads) {
		List<ClusterRupture> rups = new ArrayList<>();
		HashSet<UniqueRupture> uniques = new HashSet<>();
		largestRup = 0;
		
		if (numThreads <= 1) {
			for (FaultSubsectionCluster cluster : clusters) {
				ClusterBuildCallable build = new ClusterBuildCallable(permutationStrategy, cluster, uniques);
				try {
					build.call();
				} catch (Exception e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				build.merge(rups);
				if (build.debugStop)
					break;
//				for (FaultSection startSection : cluster.subSects) {
//					for (FaultSubsectionCluster permutation : permutationStrategy.getPermutations(
//							cluster, startSection)) {
//						ClusterRupture rup = new ClusterRupture(permutation);
//						PlausibilityResult result = testRup(rup, false);
//						if (debugCriteria != null && debugCriteria.isMatch(rup)
//								&& debugCriteria.appliesTo(result)) {
//							System.out.println("\tPermutation "+permutation+" result="+result);
//							testRup(rup, true);
//							if (stopAfterDebugMatch) {
//								return rups;
//							}
//						}
//						if (!result.canContinue())
//							// stop building here
//							continue;
//						if (result.isPass()) {
//							// passes as is, add it if it's new
//							if (!uniques.contains(rup.unique)) {
//								rups.add(rup);
//								uniques.add(rup.unique); // will add in merge below
//								int count = rup.getTotalNumSects();
//								if (count > largestRup) {
//									largestRup = count;
//									if (largestRup % largestRupPrintMod == 0)
//										System.out.println("\tNew largest rup has "+largestRup
//												+" subsections with "+rup.getTotalNumJumps()+" jumps and "
//												+rup.splays.size()+" splays. "+rups.size()+" rups in total");
//								}
//							}
//						}
//						// continue to build this rupture
//						boolean canContinue = addRuptures(rups, uniques, rup, rup, 
//								result.isPass(), permutationStrategy);
//						if (!canContinue) {
//							System.out.println("Stopping due to debug criteria match with "+rups.size()+" ruptures");
//							return rups;
//						}
//					}
//				}
			}
		} else {
			// multi threaded
			ExecutorService exec = Executors.newFixedThreadPool(numThreads);
			
			List<Future<ClusterBuildCallable>> futures = new ArrayList<>();
			
			for (FaultSubsectionCluster cluster : clusters) {
				ClusterBuildCallable build = new ClusterBuildCallable(permutationStrategy, cluster, uniques);
				futures.add(exec.submit(build));
			}
			
			System.out.println("Waiting on "+futures.size()+" cluster build futures");
			for (Future<ClusterBuildCallable> future : futures) {
				ClusterBuildCallable build;
				try {
					build = future.get();
				} catch (Exception e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				build.merge(rups);
				if (build.debugStop) {
					exec.shutdownNow();
					break;
				}
			}
			
			exec.shutdown();
		}
		
		
		return rups;
	}
	
	private static DecimalFormat countDF = new DecimalFormat("#");
	static {
		countDF.setGroupingUsed(true);
		countDF.setGroupingSize(3);
	}
	
	private class ClusterBuildCallable implements Callable<ClusterBuildCallable> {
		
		private ClusterPermutationStrategy permutationStrategy;
		private FaultSubsectionCluster cluster;
		private HashSet<UniqueRupture> uniques;
		private List<ClusterRupture> rups;
		private boolean debugStop = false;

		public ClusterBuildCallable(ClusterPermutationStrategy permutationStrategy,
				FaultSubsectionCluster cluster, HashSet<UniqueRupture> uniques) {
			this.permutationStrategy = permutationStrategy;
			this.cluster = cluster;
			this.uniques = uniques;
		}

		@Override
		public ClusterBuildCallable call() throws Exception {
			this.rups = new ArrayList<>();
			for (FaultSection startSection : cluster.subSects) {
				for (FaultSubsectionCluster permutation : permutationStrategy.getPermutations(
						cluster, startSection)) {
					ClusterRupture rup = new ClusterRupture(permutation);
					PlausibilityResult result = testRup(rup, false);
					if (debugCriteria != null && debugCriteria.isMatch(rup)
							&& debugCriteria.appliesTo(result)) {
						System.out.println("\tPermutation "+permutation+" result="+result);
						testRup(rup, true);
						if (stopAfterDebugMatch) {
							debugStop = true;
							return this;
						}
					}
					if (!result.canContinue())
						// stop building here
						continue;
					if (result.isPass()) {
						// passes as is, add it if it's new
						if (!uniques.contains(rup.unique)) {
							rups.add(rup);
//							uniques.add(rup.unique); // will add in merge below
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
						debugStop = true;
						return this;
					}
				}
			}
			return this;
		}
		
		public void merge(List<ClusterRupture> masterRups) {
			int added = 0;
			for (ClusterRupture rup : rups) {
				if (!uniques.contains(rup.unique)) {
					masterRups.add(rup);
					uniques.add(rup.unique);
					// make sure that contains now returns true
					Preconditions.checkState(uniques.contains(rup.unique));
//					Preconditions.checkState(uniques.contains(rup.reversed().unique));
					added++;
				}
			}
			System.out.println("Have "+countDF.format(masterRups.size())+" ruptures after processing cluster "
					+cluster.parentSectionID+": "+cluster.parentSectionName
					+" ("+added+" new, "+rups.size()+" incl. possible duplicates)");
		}
		
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
		FaultSection firstSection = currentStrand.clusters[0].startSect;

		// try to grow this strand first
		for (FaultSection endSection : lastCluster.endSects) {
			for (Jump jump : lastCluster.getConnections(endSection)) {
				if (!currentRupture.contains(jump.toSection)) {
					boolean canContinue = addJumpPermutations(rups, uniques, currentRupture, currentStrand,
							testJumpOnly, permutationStrategy, jump);
					if (!canContinue)
						return false;
				}
			}
		}
		
		// now try to add splays
		if (currentStrand == currentRupture && currentRupture.splays.size() < maxNumSplays) {
			for (FaultSubsectionCluster cluster : currentRupture.clusters) {
				for (FaultSection section : cluster.subSects) {
					if (section.equals(firstSection))
						// can't jump from the first section of the rupture
						continue;
					if (lastCluster.endSects.contains(section))
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
		Preconditions.checkNotNull(jump);
		for (FaultSubsectionCluster permutation : permutationStrategy.getPermutations(
				currentRupture, jump.toCluster, jump.toSection)) {
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
			Preconditions.checkState(permutation.startSect.equals(jump.toSection));
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
			boolean debugMatch = debugCriteria != null && debugCriteria.isMatch(currentRupture, testJump)
					&& debugCriteria.appliesTo(result);
			if (debugMatch) {
				System.out.println("Debug match with result="+result);
				System.out.println("\tMulti "+currentRupture+" => "+testJump.toCluster);
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
				if (debugMatch)
					System.out.println("Can't continue, bailing");
				// stop building this permutation
				continue;
			}
			if (candidateRupture == null)
				candidateRupture = currentRupture.take(testJump);
			if (result.isPass()) {
				// passes as is, add it if it's new
				if (!uniques.contains(candidateRupture.unique)) {
					if (debugMatch)
						System.out.println("We passed and this is potentially new, adding");
					rups.add(candidateRupture);
//					uniques.add(candidateRupture.unique); // now merged in later
					int count = candidateRupture.getTotalNumSects();
					if (count > largestRup) {
						largestRup = count;
						if (largestRup % largestRupPrintMod == 0)
							System.out.println("\tNew largest rup has "+largestRup
									+" subsections with "+candidateRupture.getTotalNumJumps()+" jumps and "
									+candidateRupture.splays.size()+" splays. "+rups.size()+" rups in total");
					}
				} else if (debugMatch)
					System.out.println("We passed but have already processed this rupture, skipping");
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
				FaultSection newLastStart = newCurrentStrand.clusters[newCurrentStrand.clusters.length-1].startSect;
				Preconditions.checkState(newLastStart.equals(permutation.startSect));
			}
			boolean canContinue = addRuptures(rups, uniques, candidateRupture, newCurrentStrand,
					result.isPass(), permutationStrategy);
			if (!canContinue)
				return false;
		}
		return true;
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
			if (startSect >= 0 && !isMatch(rup.clusters[0].startSect, startSect))
				return false;
			FaultSubsectionCluster lastCluster = rup.clusters[rup.clusters.length-1];
			if (endSect >= 0 && !isMatch(
					lastCluster.subSects.get(lastCluster.subSects.size()-1), endSect))
				return false;
			return true;
		}

		@Override
		public boolean isMatch(ClusterRupture rup, Jump newJump) {
			if (startSect >= 0 && !isMatch(rup.clusters[0].startSect, startSect))
				return false;
			if (endSect >= 0 && !isMatch(
					newJump.toCluster.subSects.get(newJump.toCluster.subSects.size()-1), endSect))
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
				uniques.add(UniqueRupture.forIDs(rupSects));
		}

		@Override
		public boolean isMatch(ClusterRupture rup) {
			return !uniques.contains(rup.unique);
		}

		@Override
		public boolean isMatch(ClusterRupture rup, Jump newJump) {
			return !uniques.contains(new UniqueRupture(rup.unique, newJump.toCluster));
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
				uniques.add(UniqueRupture.forIDs(rupSects));
		}

		@Override
		public boolean isMatch(ClusterRupture rup) {
			return uniques.contains(rup.unique);
		}

		@Override
		public boolean isMatch(ClusterRupture rup, Jump newJump) {
			return uniques.contains(new UniqueRupture(rup.unique, newJump.toCluster));
		}

		@Override
		public boolean appliesTo(PlausibilityResult result) {
			return !result.isPass();
		}
		
	}

	@SuppressWarnings("unused")
	public static void main(String[] args) throws IOException, DocumentException {
		File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
		FaultModels fm = FaultModels.FM3_1;
		File distAzCacheFile = new File(rupSetsDir, fm.encodeChoiceString().toLowerCase()
				+"_dist_az_cache.csv");
		DeformationModels dm = fm.getFilterBasis();
		ScalingRelationships scale = ScalingRelationships.MEAN_UCERF3;
		
		DeformationModelFetcher dmFetch = new DeformationModelFetcher(fm, dm,
				null, 0.1);
		
		List<? extends FaultSection> subSects = dmFetch.getSubSectionList();
		
		RupDebugCriteria debugCriteria = null;
		boolean stopAfterDebug = false;
		
//		RupDebugCriteria debugCriteria = new ParentSectsRupDebugCriteria(false, false, 672, 668);
////		RupDebugCriteria debugCriteria = new StartEndSectRupDebugCriteria(672, -1, true, false);
//		boolean stopAfterDebug = true;

//		FaultSystemRupSet compRupSet = FaultSystemIO.loadRupSet(new File(
//				"/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/"
//				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
//		System.out.println("Loaded "+compRupSet.getNumRuptures()+" comparison ruptures");
//		RupDebugCriteria debugCriteria = new CompareRupSetNewInclusionCriteria(compRupSet);
//		boolean stopAfterDebug = true;
		
//		String rupStr = "[219:14,13][220:1217,1216,1215,1214][184:345][108:871,870][199:1404][130:1413,1412]";
//		RupDebugCriteria debugCriteria = new SectsRupDebugCriteria(false, false,
//				loadRupString(rupStr, false));
//		boolean stopAfterDebug = false;
		
//		RupDebugCriteria debugCriteria = new ParentSectsRupDebugCriteria(false, false, 219, 220, 184, 108, 240);
//		boolean stopAfterDebug = false;

		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		if (distAzCacheFile.exists()) {
			System.out.println("Loading dist/az cache from "+distAzCacheFile.getAbsolutePath());
			distAzCalc.loadCacheFile(distAzCacheFile);
		}
		int numAzCached = distAzCalc.getCachedAzimuths().size();
		int numDistCached = distAzCalc.getCachedDistances().size();
		
		/*
		 * To reproduce UCERF3
		 */
//		PlausibilityConfiguration config = PlausibilityConfiguration.getUCERF3(subSects, distAzCalc, fm);
//		ClusterPermutationStrategy permStrat = new UCERF3ClusterPermuationStrategy();
//		String outputName = fm.encodeChoiceString().toLowerCase()+"_reproduce_ucerf3.zip";
//		SubSectStiffnessCalculator stiffnessCalc = null;
//		File stiffnessCacheFile = null;
//		int stiffnessCacheSize = 0;
		
		/*
		 * For other experiements
		 */
		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
				subSects, 2d, 3e4, 3e4, 0.5);
		File stiffnessCacheFile = new File(rupSetsDir, stiffnessCalc.getCacheFileName(StiffnessType.CFF));
		int stiffnessCacheSize = 0;
		if (stiffnessCacheFile.exists())
			stiffnessCacheSize = stiffnessCalc.loadCacheFile(stiffnessCacheFile, StiffnessType.CFF);
		// use this for the exact same connections as UCERF3
		ClusterConnectionStrategy connectionStrategy =
				new UCERF3ClusterConnectionStrategy(subSects,
						distAzCalc, 5d, CoulombRates.loadUCERF3CoulombRates(fm));
		// use this for simpler connection rules
//		ClusterConnectionStrategy connectionStrategy =
//			new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, 5d);
		String outputName = fm.encodeChoiceString().toLowerCase();
		Builder configBuilder = PlausibilityConfiguration.builder(connectionStrategy, subSects);
//		configBuilder.maxNumClusters(2); outputName += "_connOnly";
//		configBuilder.parentCoulomb(stiffnessCalc, StiffnessAggregationMethod.MEDIAN,
//			0f, Directionality.EITHER); outputName += "_cffParentPositive";
		configBuilder.cumulativeAzChange(560f); outputName += "_cmlAz";
//		configBuilder.cumulativeRakeChange(180f); outputName += "_cmlRake";
//		configBuilder.u3Azimuth(); outputName += "_u3Az";
//		configBuilder.clusterCoulomb(
//			stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f); outputName += "_cffClusterPositive";
		configBuilder.clusterPathCoulomb(
				stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f); outputName += "_cffClusterPathPositive";
//		configBuilder.clusterPathCoulomb(stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f, 0.5f);
//		outputName += "_cffClusterHalfPathsPositive";
//		configBuilder.netRupCoulomb(stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f,
//				RupCoulombQuantity.SUM_SECT_CFF); outputName += "_cffRupNetPositive";
//		configBuilder.netClusterCoulomb(
//				stiffnessCalc, StiffnessAggregationMethod.MEDIAN, 0f); outputName += "_cffClusterNetPositive";
		configBuilder.maxSplays(0);
//		configBuilder.maxSplays(1); outputName += "_max1Splays";
//		configBuilder.splayLength(0.2, true, true); outputName += "_splayLenFract0.2";
		configBuilder.minSectsPerParent(2, true, true);
		// regular (UCERF3) permutation strategy
//		ClusterPermutationStrategy permStrat = new UCERF3ClusterPermuationStrategy();
		// only permutate at connection points or subsection end points
//		ClusterPermutationStrategy permStrat = new ConnectionPointsPermutationStrategy();
		// build permutations adaptively (skip over some end points for larger ruptures)
		float sectFract = 0.15f;
		SectCountAdaptivePermutationStrategy permStrat = new SectCountAdaptivePermutationStrategy(sectFract, true);
		configBuilder.add(permStrat.buildConnPointCleanupFilter(connectionStrategy));
		outputName += "_sectFractPerm"+sectFract;
		// build it
		PlausibilityConfiguration config = configBuilder.build();
		outputName += ".zip";
		
		config.getConnectionStrategy().getClusters();
		if (numAzCached < distAzCalc.getCachedAzimuths().size()
				|| numDistCached < distAzCalc.getCachedDistances().size()) {
			System.out.println("Writing dist/az cache to "+distAzCacheFile.getAbsolutePath());
			distAzCalc.writeCacheFile(distAzCacheFile);
			numAzCached = distAzCalc.getCachedAzimuths().size();
			numDistCached = distAzCalc.getCachedDistances().size();
		}
		
		boolean writeRupSet = (debugCriteria == null || !stopAfterDebug)
				&& outputName != null && rupSetsDir != null;
		if (writeRupSet)
			System.out.println("After building, will write to "+new File(rupSetsDir, outputName));
		
		ClusterRuptureBuilder builder = new ClusterRuptureBuilder(config);
		
		if (debugCriteria != null)
			builder.setDebugCriteria(debugCriteria, stopAfterDebug);
		
		int threads = Runtime.getRuntime().availableProcessors()-2;
//		int threads = 1;
		System.out.println("Building ruptures with "+threads+" threads...");
		Stopwatch watch = Stopwatch.createStarted();
		List<ClusterRupture> rups = builder.build(permStrat, threads);
		watch.stop();
		double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		double mins = (secs / 60d);
		DecimalFormat timeDF = new DecimalFormat("0.00");
		System.out.println("Built "+countDF.format(rups.size())+" ruptures in "+timeDF.format(secs)
			+" secs = "+timeDF.format(mins)+" mins");
		
		if (writeRupSet) {
			// write out test rup set
//			double[] mags = new double[rups.size()];
//			double[] lenghts = new double[rups.size()];
//			double[] rakes = new double[rups.size()];
//			double[] rupAreas = new double[rups.size()];
//			List<List<Integer>> sectionForRups = new ArrayList<>();
//			for (int r=0; r<rups.size(); r++) {
//				List<FaultSection> sects = rups.get(r).buildOrderedSectionList();
//				List<Integer> ids = new ArrayList<>();
//				for (FaultSection sect : sects)
//					ids.add(sect.getSectionId());
//				sectionForRups.add(ids);
//				mags[r] = Double.NaN;
//				rakes[r] = Double.NaN;
//				rupAreas[r] = Double.NaN;
//			}
			double[] sectSlipRates = new double[subSects.size()];
			double[] sectAreasReduced = new double[subSects.size()];
			double[] sectAreasOrig = new double[subSects.size()];
			for (int s=0; s<sectSlipRates.length; s++) {
				FaultSection sect = subSects.get(s);
				sectAreasReduced[s] = sect.getArea(true);
				sectAreasOrig[s] = sect.getArea(false);
				sectSlipRates[s] = sect.getReducedAveSlipRate()*1e-3; // mm/yr => m/yr
			}
			double[] rupMags = new double[rups.size()];
			double[] rupRakes = new double[rups.size()];
			double[] rupAreas = new double[rups.size()];
			double[] rupLengths = new double[rups.size()];
			List<List<Integer>> rupsIDsList = new ArrayList<>();
			for (int r=0; r<rups.size(); r++) {
				ClusterRupture rup = rups.get(r);
				List<FaultSection> rupSects = rup.buildOrderedSectionList();
				List<Integer> sectIDs = new ArrayList<>();
				double totLength = 0d;
				double totArea = 0d;
				double totOrigArea = 0d; // not reduced for aseismicity
				List<Double> sectAreas = new ArrayList<>();
				List<Double> sectRakes = new ArrayList<>();
				for (FaultSection sect : rupSects) {
					sectIDs.add(sect.getSectionId());
					double length = sect.getTraceLength()*1e3;	// km --> m
					totLength += length;
					double area = sectAreasReduced[sect.getSectionId()];	// sq-m
					totArea += area;
					totOrigArea += sectAreasOrig[sect.getSectionId()];	// sq-m
					sectAreas.add(area);
					sectRakes.add(sect.getAveRake());
				}
				rupAreas[r] = totArea;
				rupLengths[r] = totLength;
				rupRakes[r] = FaultUtils.getInRakeRange(FaultUtils.getScaledAngleAverage(sectAreas, sectRakes));
				double origDDW = totOrigArea/totLength;
				rupMags[r] = scale.getMag(totArea, origDDW);
				rupsIDsList.add(sectIDs);
			}
			FaultSystemRupSet rupSet = new FaultSystemRupSet(subSects, sectSlipRates, null, sectAreasReduced, 
					rupsIDsList, rupMags, rupRakes, rupAreas, rupLengths, "");
			rupSet.setPlausibilityConfiguration(config);
			rupSet.setClusterRuptures(rups);
			FaultSystemIO.writeRupSet(rupSet, new File(rupSetsDir, outputName));
		}

		if (numAzCached < distAzCalc.getCachedAzimuths().size()
				|| numDistCached < distAzCalc.getCachedDistances().size()) {
			System.out.println("Writing dist/az cache to "+distAzCacheFile.getAbsolutePath());
			distAzCalc.writeCacheFile(distAzCacheFile);
			System.out.println("DONE writing dist/az cache");
		}
		
		if (stiffnessCalc != null && stiffnessCacheFile != null
				&& stiffnessCacheSize < stiffnessCalc.calcCacheSize()) {
			System.out.println("Writing stiffness cache to "+stiffnessCacheFile.getAbsolutePath());
			stiffnessCalc.writeCacheFile(stiffnessCacheFile, StiffnessType.CFF);
			System.out.println("DONE writing stiffness cache");
		}
	}

}
