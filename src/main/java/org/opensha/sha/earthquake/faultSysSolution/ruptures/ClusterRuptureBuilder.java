package org.opensha.sha.earthquake.faultSysSolution.ruptures;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.XMLUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.RupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets.U3RupSetConfig;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration.Builder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveBilateralRuptureGrowingStrategy.SecondaryVariations;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FilterDataClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader.GeoDBSlipRateRecord;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.UniqueRupture;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Range;
import com.google.common.primitives.Ints;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.U3FaultSystemIO;

/**
 * Code to recursively build ClusterRuptures, applying any rupture plausibility filters
 * @author kevin
 *
 */
public class ClusterRuptureBuilder {
	
	private List<FaultSubsectionCluster> clusters;
	private List<PlausibilityFilter> filters;
	private int maxNumSplays = 0;
	private SectionDistanceAzimuthCalculator distAzCalc;
	
	private RupDebugCriteria debugCriteria;
	private boolean stopAfterDebugMatch;
	
	/**
	 * Constructor which gets everything from the PlausibilityConfiguration
	 * 
	 * @param configuration plausibilty configuration
	 */
	public ClusterRuptureBuilder(PlausibilityConfiguration configuration) {
		this(configuration.getConnectionStrategy().getClusters(), configuration.getFilters(),
				configuration.getMaxNumSplays(), configuration.getDistAzCalc());
	}
	
	/**
	 * Constructor which uses previously built clusters (with connections added)
	 * 
	 * @param clusters list of clusters (with connections added)
	 * @param filters list of plausibility filters
	 * @param maxNumSplays the maximum number of splays per rupture (use 0 to disable splays)
	 */
	public ClusterRuptureBuilder(List<FaultSubsectionCluster> clusters,
			List<PlausibilityFilter> filters, int maxNumSplays, SectionDistanceAzimuthCalculator distAzCalc) {
		this.clusters = clusters;
		this.filters = filters;
		this.maxNumSplays = maxNumSplays;
		this.distAzCalc = distAzCalc;
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
	
	private class ProgressTracker {
		// rupture size & count tracking
		private int largestRup;
		private int largestRupPrintMod = 10;
		private int rupCountPrintMod = 1000;
		private HashSet<UniqueRupture> allPassedUniques;
		
		// start cluster tracking
		private HashSet<Integer> startClusterIDs = new HashSet<>();
		private HashMap<Integer, List<Future<?>>> runningStartClusterFutures = new HashMap<>();
		private HashSet<Integer> completedStartClusters = new HashSet<>();
		
		// rate tracking
		private long startTime;
		private long prevTime;
		private int prevCount;
		
		public ProgressTracker() {
			this.largestRup = 0;
			this.allPassedUniques = new HashSet<>();
			this.startTime = System.currentTimeMillis();
			this.prevTime = startTime;
		}
		
		public synchronized void processPassedRupture(ClusterRupture rup) {
			if (!allPassedUniques.contains(rup.unique)) {
				allPassedUniques.add(rup.unique);
				int count = rup.getTotalNumSects();
				if (count > largestRup) {
					largestRup = count;
					if (largestRup % largestRupPrintMod == 0) {
						System.out.println("New largest rup has "+largestRup
								+" subsections with "+rup.getTotalNumJumps()+" jumps and "
								+rup.splays.size()+" splays.");
						printCountAndStartClusterStatus();
						return;
					}
				}
				int numUnique = allPassedUniques.size();
				if (numUnique % rupCountPrintMod == 0) {
					if (rupCountPrintMod <= 1000000) {
						if (numUnique == 10000)
							rupCountPrintMod = 5000;
						if (numUnique == 50000)
							rupCountPrintMod = 10000;
						else if (numUnique == 100000)
							rupCountPrintMod = 25000;
						else if (numUnique == 500000)
							rupCountPrintMod = 50000;
						else if (numUnique == 1000000)
							rupCountPrintMod = 100000;
					}
					printCountAndStartClusterStatus();
				}
			}
		}
		
		public synchronized int newStartCluster(int parentSectionID) {
			startClusterIDs.add(parentSectionID);
			return startClusterIDs.size();
		}
		
		public synchronized int startClusterCount() {
			return startClusterIDs.size();
		}
		
		public synchronized void addStartClusterFuture(int parentSectionID, Future<?> future) {
			Preconditions.checkNotNull(future);
			List<Future<?>> futures = runningStartClusterFutures.get(parentSectionID);
			if (futures == null) {
				futures = new ArrayList<>();
				runningStartClusterFutures.put(parentSectionID, futures);
			}
			futures.add(future);
		}
		
		public synchronized void printCountAndStartClusterStatus() {
			// see if there are any completed clusters
			long curTime = System.currentTimeMillis();
			List<Integer> newlyCompleted = new ArrayList<>();
			int futuresOutstanding = 0;
			for (Integer parentID : runningStartClusterFutures.keySet()) {
				List<Future<?>> futures = runningStartClusterFutures.get(parentID);
				for (int i=futures.size(); --i>=0;)
					if (futures.get(i).isDone())
						futures.remove(i);
				if (futures.isEmpty())
					newlyCompleted.add(parentID);
				else
					futuresOutstanding += futures.size();
			}
			int numRups = allPassedUniques.size();
			if (numRups == prevCount && newlyCompleted.isEmpty())
				return;
			for (Integer parentID : newlyCompleted) {
				runningStartClusterFutures.remove(parentID);
				completedStartClusters.add(parentID);
			}
			StringBuilder str = new StringBuilder("\t").append(countDF.format(numRups));
			str.append(" total unique passing ruptures found, longest has ").append(largestRup);
			str.append(" subsections.\tClusters: ").append(runningStartClusterFutures.size());
			str.append(" running (").append(futuresOutstanding).append(" futures), ");
			str.append(completedStartClusters.size()).append(" completed, ");
			str.append(startClusterIDs.size()).append(" total. ");
			
			str.append("\tRate: ").append(rupRateStr(numRups, curTime - startTime));
			long recentMillis = curTime - prevTime;
			str.append(" (").append(rupRateStr(numRups - prevCount, recentMillis)).append(" over last ");
			double recentSecs = (double)recentMillis / 1000d;
			if (recentSecs > 60d) {
				double recentMins = recentSecs / 60d;
				if (recentMins > 60d) {
					double recentHours = recentMins / 60d;
					str.append(oneDigitDF.format(recentHours)+"h");
				} else {
					str.append(oneDigitDF.format(recentMins)+"m");
				}
			} else if (recentSecs > 10){
				str.append(countDF.format(recentSecs)+"s");
			} else {
				str.append(oneDigitDF.format(recentSecs)+"s");
			}
			str.append(")");
			
			prevTime = curTime;
			prevCount = numRups;
			
			System.out.println(str.toString());
		}
	}
	
	/**
	 * @param count
	 * @param timeDeltaMillis
	 * @return a human readable representation of the rate of ruptures built in the given number of milliseconds
	 */
	public static String rupRateStr(int count, long timeDeltaMillis) {
		if (timeDeltaMillis == 0)
			return "N/A rups/s";
		double timeDeltaSecs = (double)timeDeltaMillis/1000d;
		
		double perSec = (double)count/timeDeltaSecs;
		if (count == 0 || perSec >= 0.1) {
			if (perSec > 10)
				return countDF.format(perSec)+" rups/s";
			return oneDigitDF.format(perSec)+" rups/s";
		}
		// switch to per minute
		double perMin = perSec*60d;
		if (perMin >= 0.1) {
			if (perMin > 10)
				return countDF.format(perMin)+" rups/m";
			return oneDigitDF.format(perMin)+" rups/m";
		}
		// fallback to per hour
		double perHour = perMin*60d;
		if (perHour > 10)
			return countDF.format(perHour)+" rups/hr";
		return oneDigitDF.format(perHour)+" rups/hr";
	}
	
	/**
	 * This builds ruptures using the given rupture growing strategy
	 * 
	 * @param growingStrategy strategy for determining unique & viable subsection variations 
	 * for each cluster 
	 * @return list of unique ruptures which were build
	 */
	public List<ClusterRupture> build(RuptureGrowingStrategy growingStrategy) {
		return build(growingStrategy, 1);
	}
	
	/**
	 * This builds ruptures using the given growing strategy with the given number of threads
	 * 
	 * @param growingStrategy strategy for determining unique & viable subsection variations 
	 * for each cluster 
	 * @param numThreads
	 * @return list of unique ruptures which were build
	 */
	public List<ClusterRupture> build(RuptureGrowingStrategy growingStrategy, int numThreads) {
		growingStrategy.clearCaches();
		List<ClusterRupture> rups = new ArrayList<>();
		HashSet<UniqueRupture> uniques = new HashSet<>();
		ProgressTracker track = new ProgressTracker();
		
		if (numThreads <= 1) {
			for (FaultSubsectionCluster cluster : clusters) {
				ClusterBuildCallable build = new ClusterBuildCallable(
						growingStrategy, cluster, uniques, track, null);
				try {
					build.call();
				} catch (Exception e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				try {
					build.merge(rups);
				} catch (InterruptedException | ExecutionException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				if (build.debugStop)
					break;
			}
		} else {
			// multi threaded
			ExecutorService exec = Executors.newFixedThreadPool(numThreads);
			
			List<Future<ClusterBuildCallable>> futures = new ArrayList<>();
			
			for (FaultSubsectionCluster cluster : clusters) {
				ClusterBuildCallable build = new ClusterBuildCallable(
						growingStrategy, cluster, uniques, track, exec);
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
				try {
					build.merge(rups);
				} catch (InterruptedException | ExecutionException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				if (build.debugStop) {
					exec.shutdownNow();
					break;
				}
			}
			
			exec.shutdown();
		}
		
		
		return rups;
	}
	
	private static DecimalFormat oneDigitDF = new DecimalFormat("0.0");
	public static DecimalFormat countDF = new DecimalFormat("#");
	static {
		countDF.setGroupingUsed(true);
		countDF.setGroupingSize(3);
	}
	
	private class ClusterBuildCallable implements Callable<ClusterBuildCallable> {
		
		private RuptureGrowingStrategy growingStrategy;
		private FaultSubsectionCluster cluster;
		private HashSet<UniqueRupture> uniques;
		private List<Future<List<ClusterRupture>>> rupListFutures;
		private boolean debugStop = false;
		private ProgressTracker track;
		private ExecutorService exec;
		
		private int clusterIndex;

		public ClusterBuildCallable(RuptureGrowingStrategy growingStrategy,
				FaultSubsectionCluster cluster, HashSet<UniqueRupture> uniques, ProgressTracker track,
				ExecutorService exec) {
			this.growingStrategy = growingStrategy;
			this.cluster = cluster;
			this.uniques = uniques;
			this.track = track;
			this.exec = exec;
			clusterIndex = track.newStartCluster(cluster.parentSectionID);
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public ClusterBuildCallable call() throws Exception {
			FakeFuture<ClusterBuildCallable> primaryFuture = new FakeFuture<>(this, false);
			track.addStartClusterFuture(cluster.parentSectionID, primaryFuture);
			if (this.exec != null)
				rupListFutures = Collections.synchronizedList(new ArrayList<>());
			else
				rupListFutures = new ArrayList<>();
			for (FaultSection startSection : cluster.subSects) {
				for (FaultSubsectionCluster variation : growingStrategy.getVariations(
						cluster, startSection)) {
					ClusterRupture rup = new FilterDataClusterRupture(variation);
					PlausibilityResult result = testRup(rup, false);
					if (debugCriteria != null && debugCriteria.isMatch(rup)
							&& debugCriteria.appliesTo(result)) {
						System.out.println("\tVariation "+variation+" result="+result);
						testRup(rup, true);
						if (stopAfterDebugMatch) {
							debugStop = true;
							primaryFuture.setDone(true);
							return this;
						}
					}
					if (!result.canContinue())
						// stop building here
						continue;
					if (result.isPass()) {
						// passes as is, add it if it's new
						track.processPassedRupture(rup);
						if (!uniques.contains(rup.unique))
							// this means that this rupture passes and has not yet been processed
//							rups.add(rup);
							rupListFutures.add(new FakeFuture(Collections.singletonList(rup), true));
					}
					// continue to build this rupture
					List<ClusterRupture> rups = new ArrayList<>();
					boolean canContinue = addRuptures(rups, uniques, rup, rup, 
							growingStrategy, track, exec, rupListFutures);
					if (!rups.isEmpty())
						rupListFutures.add(new FakeFuture(rups, true));
					if (!canContinue) {
						System.out.println("Stopping due to debug criteria match with "+rups.size()+" ruptures");
						debugStop = true;
						primaryFuture.setDone(true);
						return this;
					}
				}
			}
			primaryFuture.setDone(true);
			return this;
		}
		
		public void merge(List<ClusterRupture> masterRups) throws InterruptedException, ExecutionException {
			int added = 0;
			int raw = 0;
			for (Future<List<ClusterRupture>> future : rupListFutures) {
				for (ClusterRupture rup : future.get()) {
					if (!uniques.contains(rup.unique)) {
						masterRups.add(rup);
						uniques.add(rup.unique);
						// make sure that contains now returns true
						Preconditions.checkState(uniques.contains(rup.unique));
//						Preconditions.checkState(uniques.contains(rup.reversed().unique));
						added++;
						raw++;
					}
				}
			}
			System.out.println("Merged in "+countDF.format(masterRups.size())+" ruptures after processing "
					+ "start cluster "+clusterIndex+"/"+track.startClusterCount()+" (id="+cluster.parentSectionID+"): "
					+cluster.parentSectionName+" ("+added+" new, "+raw+" incl. possible duplicates).");
			track.printCountAndStartClusterStatus();
		}
		
	}
	
	private class FakeFuture<E> implements Future<E> {
		
		private E result;
		private boolean done;

		public FakeFuture(E result, boolean done) {
			this.result = result;
			this.done = done;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return false;
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public boolean isDone() {
			return done;
		}
		
		public void setDone(boolean done) {
			this.done = done;
		}

		@Override
		public E get() throws InterruptedException, ExecutionException {
			return result;
		}

		@Override
		public E get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException {
			return result;
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
	
	private class AddRupturesCallable implements Callable<List<ClusterRupture>> {
		
		private HashSet<UniqueRupture> uniques;
		private ClusterRupture currentRupture;
		private ClusterRupture currentStrand;
		private RuptureGrowingStrategy growingStrategy;
		private ProgressTracker track;
		private Jump jump;

		public AddRupturesCallable(HashSet<UniqueRupture> uniques,
				ClusterRupture currentRupture, ClusterRupture currentStrand,
				RuptureGrowingStrategy growingStrategy, ProgressTracker track, Jump jump) {
			this.uniques = uniques;
			this.currentRupture = currentRupture;
			this.currentStrand = currentStrand;
			this.growingStrategy = growingStrategy;
			this.track = track;
			this.jump = jump;
		}

		@Override
		public List<ClusterRupture> call() {
			List<ClusterRupture> rups = new ArrayList<>();
			addJumpVariations(rups, uniques, currentRupture, currentStrand,
					growingStrategy, jump, track);
			return rups;
		}
		
	}
	
	private boolean addRuptures(List<ClusterRupture> rups, HashSet<UniqueRupture> uniques,
			ClusterRupture currentRupture, ClusterRupture currentStrand,
			RuptureGrowingStrategy growingStrategy, ProgressTracker track,
			ExecutorService exec, List<Future<List<ClusterRupture>>> futures) {
		FaultSubsectionCluster lastCluster = currentStrand.clusters[currentStrand.clusters.length-1];
		FaultSection firstSection = currentStrand.clusters[0].startSect;
		
		if (currentStrand == currentRupture && currentRupture.splays.size() < maxNumSplays) {
			// add splays first, only from the end cluster
			for (FaultSection section : lastCluster.subSects) {
				if (section.equals(firstSection))
					// can't jump from the first section of the rupture
					continue;
				if (lastCluster.endSects.contains(section))
					// this would be a continuation of the main rupture, not a splay
					break;
				for (Jump jump : lastCluster.getConnections(section)) {
					if (!currentRupture.contains(jump.toSection)) {
						boolean canContinue = addJumpVariations(rups, uniques, currentRupture, currentStrand,
								growingStrategy, jump, track);
						if (!canContinue)
							return false;
					}
				}
			}
		}

		// try to grow this strand first
		for (FaultSection endSection : lastCluster.endSects) {
			for (Jump jump : lastCluster.getConnections(endSection)) {
				if (!currentRupture.contains(jump.toSection)) {
					if (exec != null) {
						// fork it
						Future<List<ClusterRupture>> future = exec.submit(new AddRupturesCallable(
								uniques, currentRupture, currentStrand, growingStrategy, track, jump));
						track.addStartClusterFuture(currentRupture.clusters[0].parentSectionID, future);
						futures.add(future);
					} else {
						boolean canContinue = addJumpVariations(rups, uniques, currentRupture, currentStrand,
								growingStrategy, jump, track);
						if (!canContinue)
							return false;
					}
				}
			}
		}
		return true;
	}
	
	private Jump buildJump(Jump jump, FaultSubsectionCluster toVariation) {
		// correct the distance to be the minimum distance between the two clusters, not just the jumping point distance
		double minDist = Double.POSITIVE_INFINITY;
		for (FaultSection s1 : jump.fromCluster.subSects)
			for (FaultSection s2 : toVariation.subSects)
				minDist = Double.min(minDist, distAzCalc.getDistance(s1, s2));
		Preconditions.checkState(toVariation.startSect.equals(jump.toSection));
		Preconditions.checkState(toVariation.contains(jump.toSection));
		return new Jump(jump.fromSection, jump.fromCluster, jump.toSection, toVariation, minDist);
	}

	private boolean addJumpVariations(List<ClusterRupture> rups, HashSet<UniqueRupture> uniques,
			ClusterRupture currentRupture, ClusterRupture currentStrand,
			RuptureGrowingStrategy growingStrategy, Jump jump, ProgressTracker track) {
		Preconditions.checkNotNull(jump);
		for (FaultSubsectionCluster variation : growingStrategy.getVariations(
				currentRupture, jump.toCluster, jump.toSection)) {
			boolean hasLoopback = false;
			for (FaultSection sect : variation.subSects) {
				if (currentRupture.contains(sect)) {
					// this variation contains a section already part of this rupture, stop
					hasLoopback = true;
					break;
				}
			}
			if (hasLoopback)
				continue;
			Jump testJump = buildJump(jump, variation);
			ClusterRupture candidateRupture = currentRupture.take(testJump);
			PlausibilityResult result = testRup(candidateRupture, false);
			boolean debugMatch = debugCriteria != null && debugCriteria.isMatch(currentRupture, testJump)
					&& debugCriteria.appliesTo(result);
			if (debugMatch) {
				System.out.println("Debug match with result="+result);
				System.out.println("\tMulti "+currentRupture+" => "+testJump.toCluster);
				System.out.println("Testing full:");
				testRup(candidateRupture, true);
				if (stopAfterDebugMatch)
					return false;
			}
			if (!result.canContinue()) {
				if (debugMatch)
					System.out.println("Can't continue, bailing");
				// stop building this variation
				continue;
			}
			if (result.isPass()) {
				// passes as is, add it if it's new
				track.processPassedRupture(candidateRupture);
				if (!uniques.contains(candidateRupture.unique)) {
					if (debugMatch)
						System.out.println("We passed and this is potentially new, adding");
					rups.add(candidateRupture);
				} else if (debugMatch)
					System.out.println("We passed but have already processed this rupture, skipping");
			}
			// continue to build this rupture
//			ClusterRupture newCurrentStrand;
			if (currentStrand == currentRupture) {
				// we're on the primary strand
				boolean canContinue = addRuptures(rups, uniques, candidateRupture, candidateRupture,
						growingStrategy, track, null, null);
				if (!canContinue)
					return false;
			} else {
				// we're building a splay, try to continue that one
				ClusterRupture splayStrand = null;
				for (ClusterRupture splay : candidateRupture.splays.values()) {
					if (splay.contains(jump.toSection)) {
						splayStrand = splay;
						break;
					}
				}
				Preconditions.checkNotNull(splayStrand);
				// try to build out the splay
				FaultSection newLastStart = splayStrand.clusters[splayStrand.clusters.length-1].startSect;
				Preconditions.checkState(newLastStart.equals(variation.startSect));
				boolean canContinue = addRuptures(rups, uniques, candidateRupture, splayStrand,
						growingStrategy, track, null, null);
				if (!canContinue)
					return false;
				// now try to build out the primary strand
				canContinue = addRuptures(rups, uniques, candidateRupture, candidateRupture,
						growingStrategy, track, null, null);
				if (!canContinue)
					return false;
			}
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
	
	public static int[] loadRupString(String rupStr, boolean parents) {
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
			return !uniques.contains(UniqueRupture.add(rup.unique, newJump.toCluster.unique));
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
			return uniques.contains(UniqueRupture.add(rup.unique, newJump.toCluster.unique));
		}

		@Override
		public boolean appliesTo(PlausibilityResult result) {
			return !result.isPass();
		}
		
	}
	
	public static class ResultCriteria implements RupDebugCriteria {
		
		private PlausibilityResult[] results;

		public ResultCriteria(PlausibilityResult... results) {
			this.results = results;
			
		}

		@Override
		public boolean isMatch(ClusterRupture rup) {
			return true;
		}

		@Override
		public boolean isMatch(ClusterRupture rup, Jump newJump) {
			return true;
		}

		@Override
		public boolean appliesTo(PlausibilityResult result) {
			for (PlausibilityResult r : results)
				if (r == result)
					return true;
			return false;
		}
		
	}

	public static void main(String[] args) throws IOException, DocumentException {
		File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
		boolean writeRupSet = true;
		int threads = Integer.max(1, Integer.min(31, Runtime.getRuntime().availableProcessors()-2));
//		int threads = 62;
		
//		RupSetConfig rsConfig = new RuptureSets.CoulombRupSetConfig(FaultModels.FM3_1, ScalingRelationships.MEAN_UCERF3);
//		String state = "CA";
		String state = null;
		RupSetConfig rsConfig = new RuptureSets.CoulombRupSetConfig(RuptureSets.getNSHM23SubSects(state),
				"nshm23_geo_dm_"+GeoJSONFaultReader.NSHM23_DM_CUR_VERSION+"_"
						+(state == null ? "all" : state.toLowerCase()), ScalingRelationships.MEAN_UCERF3);
//		RupSetConfig rsConfig = new RuptureSets.U3RupSetConfig(FaultModels.FM3_1, ScalingRelationships.MEAN_UCERF3);
//		((U3RupSetConfig)rsConfig).setAdaptiveSectFract(0.1f);
		
		FaultSystemRupSet rupSet = rsConfig.build(threads);
		
		if (writeRupSet) {
			File outputFile = new File(rupSetsDir, rsConfig.getRupSetFileName());
			rupSet.write(outputFile);
		}
	}

	public static FaultSystemRupSet buildClusterRupSet(RupSetScalingRelationship scale, List<? extends FaultSection> subSects,
			PlausibilityConfiguration config, List<ClusterRupture> rups) {
		FaultSystemRupSet.Builder builder = FaultSystemRupSet.builderForClusterRups(subSects, rups);
		builder.forScalingRelationship(scale);
		if (config != null) {
			builder.addModule(config.getDistAzCalc());
			builder.addModule(config);
		}
		return builder.build();
	}

}
