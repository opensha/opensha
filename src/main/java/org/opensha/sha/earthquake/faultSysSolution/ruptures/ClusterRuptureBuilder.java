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
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration.Builder;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.coulomb.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.*;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ExhaustiveBilateralRuptureGrowingStrategy.SecondaryVariations;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FilterDataClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader.GeoSlipRateRecord;
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
			
			str.append("\tRate: ").append(rupRate(numRups, curTime - startTime));
			long recentMillis = curTime - prevTime;
			str.append(" (").append(rupRate(numRups - prevCount, recentMillis)).append(" over last ");
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
	
	private static String rupRate(int count, long timeDeltaMillis) {
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
	private static DecimalFormat countDF = new DecimalFormat("#");
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

	@SuppressWarnings("unused")
	public static void main(String[] args) throws IOException, DocumentException {
		File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
		
		// for UCERF3 fault models
		FaultModels fm = FaultModels.FM3_1;
		String fmPrefix = fm.encodeChoiceString().toLowerCase();
		File distAzCacheFile = new File(rupSetsDir, fmPrefix+"_dist_az_cache.csv");
		DeformationModels dm = fm.getFilterBasis();
		ScalingRelationships scale = ScalingRelationships.MEAN_UCERF3;
		DeformationModelFetcher dmFetch = new DeformationModelFetcher(fm, dm, null, 0.1);
		List<? extends FaultSection> subSects = dmFetch.getSubSectionList();
		// END U3
		
		// for NZ tests
//		File xmlFile = new File(rupSetsDir, "DEMO5_SANSTVZ_crustal_opensha.xml");
//		Document fsDoc = XMLUtils.loadDocument(xmlFile);
//		Element fsEl = fsDoc.getRootElement().element("FaultModel");
//		List<FaultSection> sects = FaultSystemIO.fsDataFromXML(fsEl);
//		System.out.println("Loaded "+sects.size()+" sections");
//		List<FaultSection> subSects = new ArrayList<>();
//		for (FaultSection sect : sects)
//			subSects.addAll(sect.getSubSectionsList(0.5*sect.getOrigDownDipWidth(), subSects.size(), 2));
//		System.out.println("Built "+subSects.size()+" subsections");
//		Preconditions.checkState(!subSects.isEmpty());
//		String fmPrefix = "nz_demo5_crustal";
//		File distAzCacheFile = new File(rupSetsDir, fmPrefix+"_dist_az_cache.csv");
//		ScalingRelationships scale = ScalingRelationships.MEAN_UCERF3;
		// END NZ
		
		// NSHM23 tests
//		String state = null;
//		
//		String fmPrefix;
//		File nshmBaseDir = new File("/home/kevin/OpenSHA/UCERF4/fault_models/NSHM2023_FaultSectionsEQGeoDB_v1p2_29March2021");
//		File setctsFile = new File(nshmBaseDir, "NSHM2023_FaultSections_v1p2.geojson");
//		File geoDBFile = new File(nshmBaseDir, "NSHM2023_EQGeoDB_v1p2.geojson");
//		List<FaultSection> sects;
//		if (state == null)
//			fmPrefix = "nshm23_v1p2_all";
//		else
//			fmPrefix = "nshm23_v1p2_"+state.toLowerCase();
//		
//		File distAzCacheFile = new File(rupSetsDir, fmPrefix+"_dist_az_cache.csv");
//		ScalingRelationships scale = ScalingRelationships.MEAN_UCERF3;
//		List<FaultSection> subSects = GeoJSONFaultReader.buildSubSects(setctsFile, geoDBFile, state);
//		System.out.println("Built "+subSects.size()+" subsections");
		// END NSHM23
		
		RupDebugCriteria debugCriteria = null;
		boolean stopAfterDebug = false;
		
//		RupDebugCriteria debugCriteria = new ResultCriteria(PlausibilityResult.PASS);
//		boolean stopAfterDebug = false;
		
//		RupDebugCriteria debugCriteria = new ParentSectsRupDebugCriteria(false, false, 672, 668);
////		RupDebugCriteria debugCriteria = new StartEndSectRupDebugCriteria(672, -1, true, false);
//		boolean stopAfterDebug = true;

//		FaultSystemRupSet compRupSet = FaultSystemIO.loadRupSet(new File(
////				"/home/kevin/workspace/OpenSHA/dev/scratch/UCERF3/data/scratch/InversionSolutions/"
////				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_MEAN_BRANCH_AVG_SOL.zip"));
//				"/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_plausibleMulti10km_direct_cmlRake360_jumpP0.001_slipP0.05incr"
//				+ "_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractPerm0.05_comp/alt_perm_Bilateral"
//				+ "_SecondaryEndEqual_Adaptive_5SectIncrease_MaintainConnectivity.zip"));
//		System.out.println("Loaded "+compRupSet.getNumRuptures()+" comparison ruptures");
//		RupDebugCriteria debugCriteria = new CompareRupSetNewInclusionCriteria(compRupSet);
//		boolean stopAfterDebug = true;
		
//		RupDebugCriteria debugCriteria = new SectsRupDebugCriteria(false, false,
//				1639, 1640, 1641, 1642, 1643);
//		boolean stopAfterDebug = true;
		
//		String rupStr = "[219:14,13][220:1217,1216,1215,1214][184:345][108:871,870][199:1404][130:1413,1412]";
//		RupDebugCriteria debugCriteria = new SectsRupDebugCriteria(false, false,
//				loadRupString(rupStr, false));
//		boolean stopAfterDebug = true;
		
//		RupDebugCriteria debugCriteria = new ParentSectsRupDebugCriteria(false, false, 219, 220, 184, 108, 240);
//		boolean stopAfterDebug = false;

		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		if (distAzCacheFile.exists()) {
			System.out.println("Loading dist/az cache from "+distAzCacheFile.getAbsolutePath());
			distAzCalc.loadCacheFile(distAzCacheFile);
		}
		int numAzCached = distAzCalc.getNumCachedAzimuths();
		int numDistCached = distAzCalc.getNumCachedDistances();
		
		int threads = Integer.max(1, Integer.min(31, Runtime.getRuntime().availableProcessors()-2));
//		int threads = 1;
		
		/*
		 * =============================
		 * To reproduce UCERF3
		 * =============================
		 */
//		PlausibilityConfiguration config = PlausibilityConfiguration.getUCERF3(subSects, distAzCalc, fm);
//		RuptureGrowingStrategy growingStrat = new ExhaustiveUnilateralRuptureGrowingStrategy();
//		String outputName = fmPrefix+"_reproduce_ucerf3.zip";
//		AggregatedStiffnessCache stiffnessCache = null;
//		File stiffnessCacheFile = null;
//		int stiffnessCacheSize = 0;
//		File outputDir = rupSetsDir;
		
		/*
		 * =============================
		 * To reproduce UCERF3 with an alternative distance/conn strategy (and calculate missing Coulomb)
		 * =============================
		 */
//		String outputName = fmPrefix+"_ucerf3";
//		CoulombRates coulombRates = CoulombRates.loadUCERF3CoulombRates(fm);
//		
//		double maxJumpDist = 10d;
//		ClusterConnectionStrategy connectionStrategy =
//				new UCERF3ClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist, coulombRates);
//		outputName += "_"+new DecimalFormat("0.#").format(maxJumpDist)+"km";
//		
////		double r0 = 5d;
////		double rMax = 10d;
////		int cMax = -1;
////		int sMax = 1;
////		ClusterConnectionStrategy connectionStrategy =
////				new AdaptiveDistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, r0, rMax, cMax, sMax);
////		outputName += "_adapt"+new DecimalFormat("0.#").format(r0)+"_"+new DecimalFormat("0.#").format(rMax)+"km";
////		if (cMax >= 0)
////			outputName += "_cMax"+cMax;
////		if (sMax >= 0)
////			outputName += "_sMax"+sMax;
//		
//		Builder configBuilder = PlausibilityConfiguration.builder(connectionStrategy, subSects);
//		
////		configBuilder.u3All(coulombRates); outputName += "_u3All";
//		configBuilder.u3Azimuth();
//		configBuilder.cumulativeAzChange(560f);
//		configBuilder.cumulativeRakeChange(180f);
////		configBuilder.add(new U3CompatibleCumulativeRakeChangeFilter(180d));
////		configBuilder.u3Cumulatives();
//		configBuilder.minSectsPerParent(2, true, true);
////		configBuilder.u3Coulomb(coulombRates);
////		outputName += "_u3NoCmlAz";
////		outputName += "_u3NoSectsPerParent";
////		outputName += "_u3NoAz";
////		outputName += "_u3CorrectedRake";
//		outputName += "_noCoulomb";
//		AggregatedStiffnessCache stiffnessCache = null;
//		File stiffnessCacheFile = null;
//		int stiffnessCacheSize = 0;
//		
////		configBuilder.minSectsPerParent(2, true, true);
////		configBuilder.u3Cumulatives();
//////		configBuilder.cumulativeAzChange(560f);
////		configBuilder.u3Azimuth();
////		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
////				subSects, 1d, 3e4, 3e4, 0.5, PatchAlignment.FILL_OVERLAP, 1d);
////		AggregatedStiffnessCache stiffnessCache = stiffnessCalc.getAggregationCache(StiffnessType.CFF);
////		File stiffnessCacheFile = new File(rupSetsDir, stiffnessCache.getCacheFileName());
////		int stiffnessCacheSize = 0;
////		if (stiffnessCacheFile.exists())
////			stiffnessCacheSize = stiffnessCache.loadCacheFile(stiffnessCacheFile);
//////		configBuilder.u3Coulomb(coulombRates, stiffnessCalc); outputName += "_cffFallback";
//////		outputName += "_noCoulomb";
////		configBuilder.u3Coulomb(new CoulombRates(fm, new HashMap<>()), stiffnessCalc); outputName += "_cffReproduce";
//		PlausibilityConfiguration config = configBuilder.build();
//		RuptureGrowingStrategy growingStrat = new ExhaustiveUnilateralRuptureGrowingStrategy();
////		float sectFract = 0.05f;
////		SectCountAdaptivePermutationStrategy permStrat = new SectCountAdaptivePermutationStrategy(
////				new ExhaustiveUnilateralClusterPermuationStrategy(), sectFract, true);
////		configBuilder.add(permStrat.buildConnPointCleanupFilter(connectionStrategy));
////		outputName += "_sectFractPerm"+sectFract;
//		outputName += ".zip";
//		File outputDir = rupSetsDir;
		
		/*
		 * =============================
		 * For other experiments
		 * =============================
		 */
		/*
		 * Thresholds & params
		 * 
		 * preferred values are listed to the right of each parameter/threshold, and 'MOD' is appended whenever
		 * one is temporarily modified from the preferred value.
		 */
		// PLAUSIBILITY FILTERS
		// minimum subsections per parent
		int minSectsPerParent = 2;				// PREF: 2
		// filters out indirect paths
		boolean noIndirectPaths = true;			// PREF: true
		// relative slip rate probability
		float slipRateProb = 0.05f;				// PREF: 0.05
		// if false, slip rate probabilities only consider alternative jumps up to the distance (+2km) of the taken jump
		boolean slipIncludeLonger = false;		// PREF: false
		// fraction of interactions positive
		float cffFractInts = 0.75f;				// PREF: 0.75
		// number of denominator values for the CFF favorability ratio
		int cffRatioN = 2;						// PREF: 2
		// CFF favorability ratio threshold
		float cffRatioThresh = 0.5f;			// PREF: 0.5
		// relative CFF probability
		float cffRelativeProb = 0.01f;			// PREF: 0.01
		// if true, CFF calculations are computed with the most favorable path (up to max jump distance), which may not
		// use the exact jumping point from the connection strategy
		boolean favorableJumps = true;			// PREF: true
		// cumulative jump probability threshold
		float jumpProbThresh = 0.001f;			// PREF: 0.001 (~21 km)
		// cumulative rake change threshold
		float cmlRakeThresh = 360f;				// PREF: 360
		// CONNECTION STRATEGY
		// maximum individual jump distance
		double maxJumpDist = 15d;				// PREF: 15
		// if true, connections happen at places that actually work and paths are optimized. if false, closest points
		boolean plausibleConnections = true;	// PREF: true
		// if >0 and <maxDist, connections will only be added above this distance when no other connections exist from
		// a given subsection. e.g., if set to 5, you can jump more than 5 km but only if no <= 5km jumps exist
		double adaptiveMinDist = 6d;			// PREF: 6
		// GROWING STRATEGY
		// if nonzero, apply thinning to growing strategy
		float adaptiveSectFract = 0.1f;			// PREF: 0.1
		// if true, allow bilateral rupture growing (using default settings)
		boolean bilateral = false;				// PREF: false
		// if true, allow splays (using default settings)
		boolean splays = false;					// PREF: false
		/*
		 * END Plausibility thresholds & params
		 */
		
		// build stiffness calculator (used for new Coulomb)
		double stiffGridSpacing = 2d;
		double coeffOfFriction = 0.5;
		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
				subSects, stiffGridSpacing, 3e4, 3e4, coeffOfFriction, PatchAlignment.FILL_OVERLAP, 1d);
		AggregatedStiffnessCache stiffnessCache = stiffnessCalc.getAggregationCache(StiffnessType.CFF);
		File stiffnessCacheFile = new File(rupSetsDir, stiffnessCache.getCacheFileName());
		int stiffnessCacheSize = 0;
		if (stiffnessCacheFile.exists())
			stiffnessCacheSize = stiffnessCache.loadCacheFile(stiffnessCacheFile);
		// common aggregators
		AggregatedStiffnessCalculator sumAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.FLATTEN, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.SUM);
		AggregatedStiffnessCalculator fractRpatchPosAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.SUM, AggregationMethod.PASSTHROUGH, AggregationMethod.RECEIVER_SUM, AggregationMethod.FRACT_POSITIVE);
//		AggregatedStiffnessCalculator threeQuarterInts = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
//				AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.SUM, AggregationMethod.THREE_QUARTER_INTERACTIONS);
		AggregatedStiffnessCalculator fractIntsAgg = new AggregatedStiffnessCalculator(StiffnessType.CFF, stiffnessCalc, true,
				AggregationMethod.FLATTEN, AggregationMethod.NUM_POSITIVE, AggregationMethod.SUM, AggregationMethod.NORM_BY_COUNT);
		
		String outputName = fmPrefix;
		
		if (stiffGridSpacing != 2d)
			outputName += "_stiff"+new DecimalFormat("0.#").format(stiffGridSpacing)+"km";
		if (coeffOfFriction != 0.5d)
			outputName += "_coeff"+(float)coeffOfFriction;
		
		/*
		 * Connection strategy: which faults are allowed to connect, and where?
		 */
		// use this for the exact same connections as UCERF3
//		double maxJumpDist = 5d;
//		ClusterConnectionStrategy connectionStrategy =
//				new UCERF3ClusterConnectionStrategy(subSects,
//						distAzCalc, maxJumpDist, CoulombRates.loadUCERF3CoulombRates(fm));
//		if (maxJumpDist != 5d)
//			outputName += "_"+new DecimalFormat("0.#").format(maxJumpDist)+"km";
		ClusterConnectionStrategy connectionStrategy;
		if (plausibleConnections) {
			// use this to pick connections which agree with your plausibility filters
			
			// some filters need a connection strategy, use one that only includes immediate neighbors at this step
			DistCutoffClosestSectClusterConnectionStrategy neighborsConnStrat =
					new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, 0.1d);
			List<PlausibilityFilter> connFilters = new ArrayList<>();
			if (cffRatioThresh > 0f) {
				connFilters.add(new CumulativeProbabilityFilter(cffRatioThresh, new CoulombSectRatioProb(
						sumAgg, cffRatioN, favorableJumps, (float)maxJumpDist, distAzCalc)));
				if (cffRelativeProb > 0f)
					connFilters.add(new PathPlausibilityFilter(
							new CumulativeProbPathEvaluator(cffRatioThresh, PlausibilityResult.FAIL_HARD_STOP,
									new CoulombSectRatioProb(sumAgg, cffRatioN, favorableJumps, (float)maxJumpDist, distAzCalc)),
							new CumulativeProbPathEvaluator(cffRelativeProb, PlausibilityResult.FAIL_HARD_STOP,
									new RelativeCoulombProb(sumAgg, neighborsConnStrat, false, true, favorableJumps, (float)maxJumpDist, distAzCalc))));
			} else if (cffRelativeProb > 0f) {
				connFilters.add(new CumulativeProbabilityFilter(cffRatioThresh, new RelativeCoulombProb(
						sumAgg, neighborsConnStrat, false, true, favorableJumps, (float)maxJumpDist, distAzCalc)));
			}
			if (cffFractInts > 0f)
				connFilters.add(new NetRuptureCoulombFilter(fractIntsAgg, cffFractInts));
			connectionStrategy = new PlausibleClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist,
						PlausibleClusterConnectionStrategy.JUMP_SELECTOR_DEFAULT, connFilters);
			outputName += "_plausibleMulti"+new DecimalFormat("0.#").format(maxJumpDist)+"km";
//						PlausibleClusterConnectionStrategy.JUMP_SELECTOR_DEFAULT_SINGLE, connFilters);
//			outputName += "_plausible"+new DecimalFormat("0.#").format(maxJumpDist)+"km";
			System.out.println("Building plausible connections w/ "+threads+" threads...");
			connectionStrategy.checkBuildThreaded(threads);
			System.out.println("DONE building plausible connections");
		} else {
			// just use closest distance
			connectionStrategy = new DistCutoffClosestSectClusterConnectionStrategy(subSects, distAzCalc, maxJumpDist);
			if (maxJumpDist != 5d)
				outputName += "_"+new DecimalFormat("0.#").format(maxJumpDist)+"km";
		}
		if (adaptiveMinDist > 0d && adaptiveMinDist < maxJumpDist) {
			connectionStrategy = new AdaptiveClusterConnectionStrategy(connectionStrategy, adaptiveMinDist, 1);
			outputName += "_adaptive"+new DecimalFormat("0.#").format(adaptiveMinDist)+"km";
		}
		
		Builder configBuilder = PlausibilityConfiguration.builder(connectionStrategy, subSects);
		
		/*
		 * Plausibility filters: which ruptures (utilizing those connections) are allowed?
		 */
		
		/*
		 *  UCERF3 filters
		 */
//		configBuilder.u3All(CoulombRates.loadUCERF3CoulombRates(fm)); outputName += "_ucerf3";
		if (minSectsPerParent > 1) {
			configBuilder.minSectsPerParent(2, true, true); // always do this one
		}
		if (noIndirectPaths) {
			configBuilder.noIndirectPaths(true);
			outputName += "_direct";
		}
//		configBuilder.u3Cumulatives(); outputName += "_u3Cml"; // cml rake and azimuth
//		configBuilder.cumulativeAzChange(560f); outputName += "_cmlAz"; // cml azimuth only
		if (cmlRakeThresh > 0) {
			configBuilder.cumulativeRakeChange(cmlRakeThresh);
			outputName += "_cmlRake"+(int)cmlRakeThresh; // cml rake only
		}
//		configBuilder.cumulativeRakeChange(270f); outputName += "_cmlRake270"; // cml rake only
//		configBuilder.cumulativeRakeChange(360f); outputName += "_cmlRake360"; // cml rake only
//		configBuilder.u3Azimuth(); outputName += "_u3Az";
//		configBuilder.u3Coulomb(CoulombRates.loadUCERF3CoulombRates(fm)); outputName += "_u3CFF";
		
		/*
		 * Cumulative jump prob
		 */
		// JUMP PROB: only increasing
		if (jumpProbThresh > 0f) {
			configBuilder.cumulativeProbability(jumpProbThresh, new Shaw07JumpDistProb(1d, 3d));
			outputName += "_jumpP"+jumpProbThresh;
		}
		// JUMP RATE PROB
		
		/*
		 * Regular slip prob
		 */
		// SLIP RATE PROB: only increasing
		if (slipRateProb > 0f) {
			configBuilder.cumulativeProbability(slipRateProb,
					new RelativeSlipRateProb(connectionStrategy, true, slipIncludeLonger));
			outputName += "_slipP"+slipRateProb+"incr";
			if (!slipIncludeLonger)
				outputName += "CapDist";
		}
		// END SLIP RATE PROB
		
		/*
		 * Regular CFF prob (not currently used)
		 */
		// CFF prob: allow neg, 0.01
//		configBuilder.cumulativeProbability(0.01f, new RelativeCoulombProb(
//				sumAgg, connectionStrategy, false, true, true));
//		outputName += "_cffP0.01incr";
		// END SLIP RATE PROB
		
		/*
		 *  CFF net rupture filters
		 */
		// FRACT INTERACTIONS POSITIVE
		if (cffFractInts > 0f) {
			configBuilder.netRupCoulomb(fractIntsAgg,
					Range.greaterThan(cffFractInts));
			outputName += "_cff"+cffFractInts+"IntsPos";
		}
		// END MAIN 3/4 INTERACTIONS POSITIVE
		
		/**
		 * Path filters
		 */
		List<NucleationClusterEvaluator> combPathEvals = new ArrayList<>();
		List<String> combPathPrefixes = new ArrayList<>();
		float fractPathsThreshold = 0f; String fractPathsStr = "";
		float favorableDist = Float.max((float)maxJumpDist, 10f);
		String favStr = "";
		if (favorableJumps) {
			favStr = "Fav";
			if (favorableDist != (float)maxJumpDist)
				favStr += (int)favorableDist;
		}
		// SLIP RATE PROB: as a path, only increasing NOT CURRENTLY PREFERRED
//		float pathSlipProb = 0.1f;
//		CumulativeJumpProbPathEvaluator slipEval = new CumulativeJumpProbPathEvaluator(
//				pathSlipProb, PlausibilityResult.FAIL_HARD_STOP, new RelativeSlipRateProb(connectionStrategy, true));
//		combPathEvals.add(slipEval); combPathPrefixes.add("slipP"+pathSlipProb+"incr");
////		configBuilder.path(slipEval); outputName += "_slipPathP"+pathSlipProb+"incr"; // do it separately
		// END SLIP RATE PROB
		// CFF PROB: as a path, allow negative, 0.01
		if (cffRelativeProb > 0f) {
			RelativeCoulombProb cffProbCalc = new RelativeCoulombProb(
					sumAgg, connectionStrategy, false, true, favorableJumps, favorableDist, distAzCalc);
			CumulativeProbPathEvaluator cffProbPathEval = new CumulativeProbPathEvaluator(
					cffRelativeProb, PlausibilityResult.FAIL_HARD_STOP, cffProbCalc);
			combPathEvals.add(cffProbPathEval); combPathPrefixes.add("cff"+favStr+"P"+cffRelativeProb);
		}
//		configBuilder.path(cffProbPathEval); outputName += "_cffPathP0.01"; // do it separately
		// CFF SECT PATH: relBest, 15km
//		SectCoulombPathEvaluator prefCFFSectPathEval = new SectCoulombPathEvaluator(
//				sumAgg, Range.atLeast(0f), PlausibilityResult.FAIL_HARD_STOP, true, 15f, distAzCalc);
//		combPathEvals.add(prefCFFSectPathEval); combPathPrefixes.add("cffSPathFav15");
////		configBuilder.path(prefCFFSectPathEval); outputName += "_cffSPathFav15"; // do it separately
		// END CFF SECT PATH
		// CFF CLUSTER PATH: half RPatches positive
//		ClusterCoulombPathEvaluator prefCFFRPatchEval = new ClusterCoulombPathEvaluator(
//				fractRpatchPosAgg, Range.atLeast(0.5f), PlausibilityResult.FAIL_HARD_STOP);
//		combPathEvals.add(prefCFFRPatchEval); combPathPrefixes.add("cffCPathRPatchHalfPos");
////		configBuilder.path(prefCFFRPatchEval); outputName += "_cffCPathRPatchHalfPos"; // do it separately
		// END CFF CLUSTER PATH
		// CFF RATIO PATH: N=2, relBest, 15km
		if (cffRatioThresh > 0f) {
			CumulativeProbPathEvaluator cffRatioPatchEval = new CumulativeProbPathEvaluator(cffRatioThresh,
					PlausibilityResult.FAIL_HARD_STOP,
					new CoulombSectRatioProb(sumAgg, cffRatioN, favorableJumps, favorableDist, distAzCalc));
			combPathEvals.add(cffRatioPatchEval);
			combPathPrefixes.add("cff"+favStr+"RatioN"+cffRatioN+"P"+cffRatioThresh);
		}
//		configBuilder.path(prefCFFRPatchEval); outputName += "_cffCPathRPatchHalfPos"; // do it separately
		// END CFF RATIO PATH
		// add them
		Preconditions.checkState(combPathEvals.size() == combPathPrefixes.size());
		if (!combPathEvals.isEmpty()) {
			configBuilder.path(fractPathsThreshold, combPathEvals.toArray(new NucleationClusterEvaluator[0]));
			outputName += "_";
			if (combPathEvals.size() > 1)
				outputName += "comb"+combPathEvals.size();
			outputName += fractPathsStr;
			if (fractPathsStr.isEmpty() && combPathEvals.size() == 1) {
				outputName += "path";
			} else {
				outputName += "Path";
				if (combPathEvals.size() > 1)
					outputName += "s";
			}
			outputName += "_"+Joiner.on("_").join(combPathPrefixes);
		}

		// Check connectivity only (maximum 2 clusters per rupture)
//		configBuilder.maxNumClusters(2); outputName += "_connOnly";
		
//		File outputDir = new File(rupSetsDir, "fm3_1_plausible10km_slipP0.05incr_cff3_4_IntsPos_comb2Paths_cffP0.05_cffRatioN2P0.5_sectFractPerm0.05_comp");
//		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		File outputDir = rupSetsDir;
		
		/*
		 * Splay constraints
		 */
		if (splays) {
			configBuilder.maxSplays(1); outputName += "_max1Splays";
			//configBuilder.splayLength(0.1, true, true); outputName += "_splayLenFract0.1";
			//configBuilder.splayLength(100, false, true, true); outputName += "_splayLen100km";
			configBuilder.splayLength(50, false, true, true); outputName += "_splayLen50km";
			configBuilder.splayLength(.5, true, true, true); outputName += "OrHalf";
			configBuilder.addFirst(new SplayConnectionsOnlyFilter(connectionStrategy, true)); outputName += "_splayConn";
		} else {
			configBuilder.maxSplays(0); // default, no splays
		}
		
		/*
		 * Growing strategies: how should ruptures be broken up and spread onto new faults
		 */
		RuptureGrowingStrategy growingStrat;
		if (bilateral) {
			growingStrat = new ExhaustiveBilateralRuptureGrowingStrategy(
					SecondaryVariations.EQUAL_LEN, false);
			outputName += "_bilateral";
		} else {
			growingStrat = new ExhaustiveUnilateralRuptureGrowingStrategy();
		}
		if (adaptiveSectFract > 0f) {
			SectCountAdaptiveRuptureGrowingStrategy adaptiveStrat = new SectCountAdaptiveRuptureGrowingStrategy(
					growingStrat, adaptiveSectFract, true, minSectsPerParent);
			configBuilder.add(adaptiveStrat.buildConnPointCleanupFilter(connectionStrategy));
			outputName += "_sectFractGrow"+adaptiveSectFract;
			growingStrat = adaptiveStrat;
		}
		
		// build our configuration
		PlausibilityConfiguration config = configBuilder.build();
		outputName += ".zip";
		/*
		 * =============================
		 * END other experiments
		 * =============================
		 */
		
		config.getConnectionStrategy().getClusters();
		if (numAzCached < distAzCalc.getNumCachedAzimuths()
				|| numDistCached < distAzCalc.getNumCachedDistances()) {
			System.out.println("Writing dist/az cache to "+distAzCacheFile.getAbsolutePath());
			distAzCalc.writeCacheFile(distAzCacheFile);
			numAzCached = distAzCalc.getNumCachedAzimuths();
			numDistCached = distAzCalc.getNumCachedDistances();
		}
		
		boolean writeRupSet = (debugCriteria == null || !stopAfterDebug)
				&& outputName != null && rupSetsDir != null;
		if (writeRupSet)
			System.out.println("After building, will write to "+new File(rupSetsDir, outputName));
		
		ClusterRuptureBuilder builder = new ClusterRuptureBuilder(config);
		
		if (debugCriteria != null)
			builder.setDebugCriteria(debugCriteria, stopAfterDebug);
		System.out.println("Building ruptures with "+threads+" threads...");
		Stopwatch watch = Stopwatch.createStarted();
		List<ClusterRupture> rups = builder.build(growingStrat, threads);
		watch.stop();
		long millis = watch.elapsed(TimeUnit.MILLISECONDS);
		double secs = millis/1000d;
		double mins = (secs / 60d);
		DecimalFormat timeDF = new DecimalFormat("0.00");
		System.out.println("Built "+countDF.format(rups.size())+" ruptures in "+timeDF.format(secs)
			+" secs = "+timeDF.format(mins)+" mins. Total rate: "+rupRate(rups.size(), millis));
		
		if (writeRupSet) {
			File outputFile = new File(outputDir, outputName);
			FaultSystemRupSet rupSet = buildClusterRupSet(scale, subSects, config, rups);
			FaultSystemIO.writeRupSet(rupSet, outputFile);
		}

		if (numAzCached < distAzCalc.getNumCachedAzimuths()
				|| numDistCached < distAzCalc.getNumCachedDistances()) {
			System.out.println("Writing dist/az cache to "+distAzCacheFile.getAbsolutePath());
			distAzCalc.writeCacheFile(distAzCacheFile);
			System.out.println("DONE writing dist/az cache");
		}
		
		if (stiffnessCache != null && stiffnessCacheFile != null
				&& stiffnessCacheSize < stiffnessCache.calcCacheSize()) {
			System.out.println("Writing stiffness cache to "+stiffnessCacheFile.getAbsolutePath());
			stiffnessCache.writeCacheFile(stiffnessCacheFile);
			System.out.println("DONE writing stiffness cache");
		}
	}

	public static FaultSystemRupSet buildClusterRupSet(ScalingRelationships scale, List<? extends FaultSection> subSects,
			PlausibilityConfiguration config, List<ClusterRupture> rups) {
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
		return rupSet;
	}

}
