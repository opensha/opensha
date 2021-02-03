package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.CumulativeProbabilityFilter.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FilterDataClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This is a filter that tests various plausibility rules as paths through a rupture. Each cluster in the rupture is
 * considered as a potential nucleation point, and then the rupture is tested as is grows outward from that nucleation
 * cluster (bi/unilaterally for ruptures with more than 2 clusters). If fractPassThreshold == 0, then a rupture passes
 * if any nucleation clusters pass. Otherwise, at least fractPassThreshold fraction of the nucleation clusters must pass.
 * 
 * @author kevin
 *
 */
public class PathPlausibilityFilter implements PlausibilityFilter {

	public static interface NucleationClusterEvaluator extends ShortNamed {
		
		public PlausibilityResult testNucleationCluster(ClusterRupture rupture,
				FaultSubsectionCluster nucleationCluster, boolean verbose);
		
		public PlausibilityResult getFailureType();
		
		public default List<FaultSubsectionCluster> getDestinations(RuptureTreeNavigator nav,
				FaultSubsectionCluster from, HashSet<FaultSubsectionCluster> strandClusters) {
			List<FaultSubsectionCluster> ret = new ArrayList<>();
			
			FaultSubsectionCluster predecessor = nav.getPredecessor(from);
			if (predecessor != null && (strandClusters == null || !strandClusters.contains(predecessor)))
				ret.add(predecessor);
			
			for (FaultSubsectionCluster descendant : nav.getDescendants(from))
				if (strandClusters == null || !strandClusters.contains(descendant))
					ret.add(descendant);
			
			return ret;
		}
		
		public default void init(ClusterConnectionStrategy connStrat, SectionDistanceAzimuthCalculator distAzCalc) {
			// do nothing
		}
	}
	
	public static interface ScalarNucleationClusterEvaluator<E extends Number & Comparable<E>> extends NucleationClusterEvaluator {
		
		public E getNucleationClusterValue(ClusterRupture rupture,
				FaultSubsectionCluster nucleationCluster, boolean verbose);
		
		/**
		 * @return acceptable range of values
		 */
		public Range<E> getAcceptableRange();
		
		/**
		 * @return name of this scalar value
		 */
		public String getScalarName();
		
		/**
		 * @return units of this scalar value
		 */
		public String getScalarUnits();
		
	}
	
	public static abstract class JumpPathEvaluator implements NucleationClusterEvaluator {
		
		@Override
		public PlausibilityResult testNucleationCluster(ClusterRupture rupture,
				FaultSubsectionCluster nucleationCluster, boolean verbose) {
			RuptureTreeNavigator nav = rupture.getTreeNavigator();

			if (verbose)
				System.out.println(getName()+": testing strand(s) with start="+nucleationCluster);
			
			HashSet<FaultSubsectionCluster> strandClusters = new HashSet<>();
			strandClusters.add(nucleationCluster);
			
			PlausibilityResult result = PlausibilityResult.PASS;
			for (FaultSubsectionCluster destination : getDestinations(nav, nucleationCluster, null)) {
				Jump jump = nav.getJump(nucleationCluster, destination);
				result = result.logicalAnd(testStrand(nav, strandClusters, nucleationCluster.subSects, jump, verbose));
			}
			return result;
		}
		
		protected PlausibilityResult testStrand(RuptureTreeNavigator navigator, HashSet<FaultSubsectionCluster> strandClusters,
				List<FaultSection> strandSects, Jump jump, boolean verbose) {
			if (verbose)
				System.out.println("\tTesting path jump: "+jump);
			
			PlausibilityResult result = testStrandJump(navigator, strandClusters, strandSects, jump, verbose);
			if (verbose)
				System.out.println("\t\tResult: "+result);
			else if (!result.isPass())
				return result;
			
			HashSet<FaultSubsectionCluster> newStrandClusters = new HashSet<>(strandClusters);
			newStrandClusters.add(jump.toCluster);
			List<FaultSection> newStrandSects = new ArrayList<>(strandSects);
			newStrandSects.addAll(jump.toCluster.subSects);
			for (FaultSubsectionCluster destination : getDestinations(navigator, jump.toCluster, newStrandClusters)) {
				Jump nextJump = navigator.getJump(jump.toCluster, destination);
				result = result.logicalAnd(testStrand(navigator, newStrandClusters, newStrandSects, nextJump, verbose));
				if (!result.isPass() && !verbose)
					return result;
			}
			
			return result;
		}

		public abstract PlausibilityResult testStrandJump(RuptureTreeNavigator navigator, HashSet<FaultSubsectionCluster> strandClusters,
				List<FaultSection> strandSects, Jump jump, boolean verbose);
	}
	
	public static abstract class ScalarJumpPathEvaluator<E extends Number & Comparable<E>>
	extends JumpPathEvaluator implements ScalarNucleationClusterEvaluator<E> {
		
		protected final Range<E> acceptableRange;
		protected final PlausibilityResult failureType;

		public ScalarJumpPathEvaluator(Range<E> acceptableRange, PlausibilityResult failureType) {
			this.acceptableRange = acceptableRange;
			Preconditions.checkState(!failureType.isPass());
			this.failureType = failureType;
		}
		
		public abstract E getStrandJumpValue(RuptureTreeNavigator navigator, HashSet<FaultSubsectionCluster> strandClusters,
				List<FaultSection> strandSections, Jump jump, boolean verbose);
		
		public PlausibilityResult testStrandJump(RuptureTreeNavigator navigator, HashSet<FaultSubsectionCluster> strandClusters,
				List<FaultSection> strandSections, Jump jump, boolean verbose) {
			E value = getStrandJumpValue(navigator, strandClusters, strandSections, jump, verbose);
			if (acceptableRange.contains(value))
				return PlausibilityResult.PASS;
			return failureType;
		}
		
		@Override
		public E getNucleationClusterValue(ClusterRupture rupture,
				FaultSubsectionCluster nucleationCluster, boolean verbose) {
			RuptureTreeNavigator nav = rupture.getTreeNavigator();

			if (verbose)
				System.out.println("Testing strand(s) with start="+nucleationCluster);
			
			HashSet<FaultSubsectionCluster> strandClusters = new HashSet<>();
			strandClusters.add(nucleationCluster);
			
			E worstVal = null;
			for (FaultSubsectionCluster destination : getDestinations(nav, nucleationCluster, null)) {
				
				Jump jump = nav.getJump(nucleationCluster, destination);
				E val = getStrandValue(nav, strandClusters, nucleationCluster.subSects, jump, verbose);
				if (worstVal == null || !ScalarValuePlausibiltyFilter.isValueBetter(val, worstVal, acceptableRange))
					worstVal = val;
			}
			return worstVal;
		}
		
		protected E getStrandValue(RuptureTreeNavigator navigator, HashSet<FaultSubsectionCluster> strandClusters,
				List<FaultSection> strandSects, Jump jump, boolean verbose) {
			if (verbose)
				System.out.println("\tTesting path jump: "+jump);
			
			E val = getStrandJumpValue(navigator, strandClusters, strandSects, jump, verbose);
			if (verbose)
				System.out.println("\t\tResult: "+val);
			
			HashSet<FaultSubsectionCluster> newStrandClusters = new HashSet<>(strandClusters);
			newStrandClusters.add(jump.toCluster);
			List<FaultSection> newStrandSects = new ArrayList<>(strandSects);
			newStrandSects.addAll(jump.toCluster.subSects);
			for (FaultSubsectionCluster destination : getDestinations(navigator, jump.toCluster, newStrandClusters)) {
				
				Jump nextJump = navigator.getJump(jump.toCluster, destination);
				E newVal = getStrandValue(navigator, newStrandClusters, newStrandSects, nextJump, verbose);
				if (!ScalarValuePlausibiltyFilter.isValueBetter(newVal, val, acceptableRange))
					val = newVal;
			}
			
			return val;
		}

		@Override
		public Range<E> getAcceptableRange() {
			return acceptableRange;
		}

		@Override
		public PlausibilityResult getFailureType() {
			return failureType;
		}
		
	}
	
	public static class ClusterCoulombPathEvaluator extends ScalarJumpPathEvaluator<Float> {

		private AggregatedStiffnessCalculator aggCalc;

		public ClusterCoulombPathEvaluator(AggregatedStiffnessCalculator aggCalc, Range<Float> acceptableRange,
				PlausibilityResult failureType) {
			super(acceptableRange, failureType);
			this.aggCalc = aggCalc;
		}

		@Override
		public Float getStrandJumpValue(RuptureTreeNavigator navigator, HashSet<FaultSubsectionCluster> strandClusters,
				List<FaultSection> strandSections, Jump jump, boolean verbose) {
			return (float)aggCalc.calc(strandSections, jump.toCluster.subSects);
		}

		@Override
		public String getScalarName() {
			return aggCalc.getScalarName();
		}

		@Override
		public String getScalarUnits() {
			if (aggCalc.hasUnits())
				return aggCalc.getType().getUnits();
			return null;
		}

		@Override
		public String getShortName() {
			return "Cl "+aggCalc.getScalarShortName()+"]"+ScalarValuePlausibiltyFilter.getRangeStr(getAcceptableRange());
		}

		@Override
		public String getName() {
			return "Cluster ["+aggCalc.getScalarName()+"] "+ScalarValuePlausibiltyFilter.getRangeStr(getAcceptableRange());
		}
		
	}
	
	public static class SectCoulombPathEvaluator extends ScalarJumpPathEvaluator<Float> {

		private AggregatedStiffnessCalculator aggCalc;
		private boolean jumpToMostFavorable;
		private float maxJumpDist;
		private transient SectionDistanceAzimuthCalculator distAzCalc;
		
		public SectCoulombPathEvaluator(AggregatedStiffnessCalculator aggCalc, Range<Float> acceptableRange,
				PlausibilityResult failureType) {
			this(aggCalc, acceptableRange, failureType, false, 0f, null);
		}

		public SectCoulombPathEvaluator(AggregatedStiffnessCalculator aggCalc, Range<Float> acceptableRange,
				PlausibilityResult failureType, boolean jumpToMostFavorable, float maxJumpDist,
				SectionDistanceAzimuthCalculator distAzCalc) {
			super(acceptableRange, failureType);
			this.aggCalc = aggCalc;
			this.jumpToMostFavorable = jumpToMostFavorable;
			if (jumpToMostFavorable) {
				Preconditions.checkState(maxJumpDist > 0d);
				Preconditions.checkNotNull(distAzCalc);
				this.maxJumpDist = maxJumpDist;
				this.distAzCalc = distAzCalc;
			} else {
				this.maxJumpDist = 0f;
				this.distAzCalc = null;
			}
		}
		
		public void init(ClusterConnectionStrategy connStrat, SectionDistanceAzimuthCalculator distAzCalc) {
			this.distAzCalc = distAzCalc;
		}
		
		private FaultSection getFavorableJumpingPoint(List<FaultSection> currentSects, FaultSubsectionCluster fromCluster,
				FaultSubsectionCluster toCluster, float maxJumpDist) {
			Preconditions.checkState(jumpToMostFavorable);
			List<FaultSection> allowedJumps = new ArrayList<>();
			for (FaultSection sect : toCluster.subSects) {
				for (FaultSection source : fromCluster.subSects) {
					if ((float)distAzCalc.getDistance(sect, source) <= maxJumpDist) {
						allowedJumps.add(sect);
						break;
					}
				}
			}
			Preconditions.checkState(!allowedJumps.isEmpty(), "No jumps within %s km found between %s and %s",
					maxJumpDist, fromCluster, toCluster);
			if (allowedJumps.size() == 1)
				return allowedJumps.get(0);
			// find the most favorable one
			float bestVal = Float.NaN;
			FaultSection bestSect = null;
			for (FaultSection sect : allowedJumps) {
				float myVal = (float)aggCalc.calc(currentSects, sect);
				if (Double.isNaN(bestVal) || ScalarValuePlausibiltyFilter.isValueBetter(myVal, bestVal, acceptableRange)) {
					bestVal = myVal;
					bestSect = sect;
				}
			}
			Preconditions.checkNotNull(bestSect);
			return bestSect;
		}

		@Override
		public Float getStrandJumpValue(RuptureTreeNavigator navigator, HashSet<FaultSubsectionCluster> strandClusters,
				List<FaultSection> strandSections, Jump jump, boolean verbose) {
			// locate the destination section
			FaultSection destSect;
			if (jumpToMostFavorable)
				destSect = getFavorableJumpingPoint(strandSections, jump.fromCluster, jump.toCluster,
						Float.max(maxJumpDist, (float)jump.distance));
			else
				destSect = jump.toSection;
			
			strandSections = new ArrayList<>(strandSections);
			
			// build out this cluster, one section at a time;
			int jumpIndex = jump.toCluster.subSects.indexOf(destSect);
			Preconditions.checkState(jumpIndex >= 0);
			int maxIndex = jump.toCluster.subSects.size()-1;
			int curOffset = 0;
			
			// keep track of the worst value we have encountered so far
			Float worstVal = null;
			while (true) {
				FaultSection dest1 = null, dest2 = null;
				if (curOffset == 0) {
					dest1 = destSect;
				} else {
					int ind1 = jumpIndex + curOffset;
					int ind2 = jumpIndex - curOffset;
					if (ind1 <= maxIndex)
						dest1 = jump.toCluster.subSects.get(ind1);
					if (ind2 >= 0)
						dest2 = jump.toCluster.subSects.get(ind2);
				}
				if (dest1 == null && dest2 == null)
					break;
				if (dest1 != null) {
					float val = (float)aggCalc.calc(strandSections, dest1);
//					if (verbose)
//						System.out.println("\t\tval="+val+" for "+strandClusters.size()+" clusters ("
//								+strandSects.size()+" sects) to sect "+dest1.getSectionId());
					if (worstVal == null || !ScalarValuePlausibiltyFilter.isValueBetter(val, worstVal, acceptableRange))
						worstVal = val;
					if (!verbose && !acceptableRange.contains(worstVal))
						return worstVal;
				}
				if (dest2 != null) {
					float val = (float)aggCalc.calc(strandSections, dest2);
//					if (verbose)
//						System.out.println("\t\tval="+val+" for "+strandClusters.size()+" clusters ("
//								+strandSects.size()+" sects) to sect "+dest2.getSectionId());
					if (worstVal == null || !ScalarValuePlausibiltyFilter.isValueBetter(val, worstVal, acceptableRange))
						worstVal = val;
					if (!verbose && !acceptableRange.contains(worstVal))
						return worstVal;
				}
				if (dest1 != null)
					strandSections.add(dest1);
				if (dest2 != null)
					strandSections.add(dest2);
				curOffset++;
			}
			if (verbose)
				System.out.println("\t\tworst value after cluster: "+worstVal);
			return worstVal;
		}

		@Override
		public String getScalarName() {
			return aggCalc.getScalarName();
		}

		@Override
		public String getScalarUnits() {
			if (aggCalc.hasUnits())
				return aggCalc.getType().getUnits();
			return null;
		}

		@Override
		public String getShortName() {
			String sectStr = jumpToMostFavorable ? "SectFav"+new DecimalFormat("0.#").format(maxJumpDist) : "Sect";
			return sectStr+"["+aggCalc.getScalarShortName()+"]"+ScalarValuePlausibiltyFilter.getRangeStr(getAcceptableRange());
		}

		@Override
		public String getName() {
			String sectStr = jumpToMostFavorable ? "Sect Favorable ("+new DecimalFormat("0.#").format(maxJumpDist)+"km)" : "Sect";
			return sectStr+" ["+aggCalc.getScalarName()+"] "+ScalarValuePlausibiltyFilter.getRangeStr(getAcceptableRange());
		}
		
	}
	
	public static class CumulativeJumpProbPathEvaluator implements ScalarNucleationClusterEvaluator<Float> {
		
		private RuptureProbabilityCalc[] calcs;
		private float minProbability;
		private PlausibilityResult failureType;

		public CumulativeJumpProbPathEvaluator(float minProbability, PlausibilityResult failureType, RuptureProbabilityCalc... calcs) {
			Preconditions.checkState(calcs.length > 0, "Must supply at least one calculator");
			this.calcs = calcs;
			Preconditions.checkState(minProbability >= 0f && minProbability <= 1f);
			this.minProbability = minProbability;
			Preconditions.checkState(!failureType.isPass());
			this.failureType = failureType;
		}
		
		public void init(ClusterConnectionStrategy connStrat, SectionDistanceAzimuthCalculator distAzCalc) {
			for (RuptureProbabilityCalc calc : calcs)
				calc.init(connStrat, distAzCalc);
		}

		@Override
		public Range<Float> getAcceptableRange() {
			return Range.atLeast(minProbability);
		}

		@Override
		public String getScalarName() {
			return "Conditional Probability";
		}

		@Override
		public String getScalarUnits() {
			return null;
		}
		
		@Override
		public PlausibilityResult getFailureType() {
			return failureType;
		}
		
		private ClusterRupture buildRuptureForwards(ClusterRupture curRup, RuptureTreeNavigator nav,
				FaultSubsectionCluster fromCluster) {
			for (FaultSubsectionCluster toCluster : nav.getDescendants(fromCluster)) {
				Jump jump = nav.getJump(fromCluster, toCluster);
				// take that jump
				curRup = curRup.take(jump);
				// continue down strand
				curRup = buildRuptureForwards(curRup, nav, toCluster);
			}
			return curRup;
		}
		
		private ClusterRupture getNucleationRupture(ClusterRupture rupture, FaultSubsectionCluster nucleationCluster,
				boolean verbose) {
			ClusterRupture nucleationRupture;
			if (rupture.clusters[0] == nucleationCluster) {
				nucleationRupture = rupture;
			} else if (rupture.singleStrand && rupture.clusters[rupture.clusters.length-1] == nucleationCluster) {
				nucleationRupture = rupture.reversed();
			} else {
				// complex case, build out in each direction
				RuptureTreeNavigator nav = rupture.getTreeNavigator();
				// start at nucleation cluster
				nucleationRupture = new ClusterRupture(nucleationCluster);
				// build it out forwards
				nucleationRupture = buildRuptureForwards(nucleationRupture, nav, nucleationCluster);
				// build it out backwards, flipping each cluster
				FaultSubsectionCluster predecessor = nav.getPredecessor(nucleationCluster);
				FaultSubsectionCluster prevOrig = nucleationCluster;
				FaultSubsectionCluster prevReversed;
				if (nucleationRupture.getTotalNumClusters() == 1) {
					// flip it, this was at an endpoint
					prevReversed = nucleationCluster.reversed();
					nucleationRupture = new ClusterRupture(prevReversed);
				} else {
					// don't flip the first one, it's in the middle somewhere
					prevReversed = nucleationCluster;
				}
				while (predecessor != null) {
					Jump origJump = nav.getJump(predecessor, prevOrig);
					FaultSubsectionCluster reversed = predecessor.reversed();
					Jump reverseJump = new Jump(origJump.toSection, prevReversed,
							origJump.fromSection, reversed, origJump.distance);
					nucleationRupture = nucleationRupture.take(reverseJump);
					// see if we need to follow any splays from this cluster
					for (FaultSubsectionCluster descendant : nav.getDescendants(predecessor)) {
						if (!nucleationRupture.contains(descendant.startSect)) {
							// this is forward splay off of the predecessor, we need to follow it
							Jump origSplayJump = nav.getJump(predecessor, descendant);
							Jump newSplayJump = new Jump(origSplayJump.fromSection, reversed,
									origSplayJump.toSection, origSplayJump.toCluster, origSplayJump.distance);
							nucleationRupture = nucleationRupture.take(newSplayJump);
							// go down further if needed
							nucleationRupture = buildRuptureForwards(nucleationRupture, nav, descendant);
						}
					}
					prevOrig = predecessor;
					prevReversed = reversed;
					predecessor = nav.getPredecessor(predecessor);
				}
				Preconditions.checkState(nucleationRupture.getTotalNumSects() == rupture.getTotalNumSects(),
						"Nucleation view of rupture is incomplete!\n\tOriginal: %s\n\tNucleation cluster: %s"
						+ "\n\tNucleation rupture: %s", rupture, nucleationCluster, nucleationRupture);
			}
			if (verbose)
				System.out.println("Nucleation rupture: "+nucleationRupture);
			return nucleationRupture;
		}

		@Override
		public PlausibilityResult testNucleationCluster(ClusterRupture rupture,
				FaultSubsectionCluster nucleationCluster, boolean verbose) {
			// build the rupture such that it starts at the given nucleation cluster
			ClusterRupture nucleationRupture = getNucleationRupture(rupture, nucleationCluster, verbose);
			
			double prob = 1d;
			for (RuptureProbabilityCalc calc : calcs) {
				double myProb = calc.calcRuptureProb(nucleationRupture, verbose);
				prob *= myProb;
				if (verbose)
					System.out.println("\t"+calc.getName()+": P="+myProb);
				else if ((float)prob < minProbability)
					return failureType;
			}
			
			if ((float)prob >= minProbability)
				return PlausibilityResult.PASS;
			return failureType;
		}

		@Override
		public Float getNucleationClusterValue(ClusterRupture rupture,
				FaultSubsectionCluster nucleationCluster, boolean verbose) {
			// build the rupture such that it starts at the given nucleation cluster
			ClusterRupture nucleationRupture = getNucleationRupture(rupture, nucleationCluster, verbose);

			double prob = 1d;
			for (RuptureProbabilityCalc calc : calcs) {
				double myProb = calc.calcRuptureProb(nucleationRupture, verbose);
				prob *= myProb;
				if (verbose)
					System.out.println("\t"+calc.getName()+": P="+myProb);
			}

			return (float)prob;
		}
		
		@Override
		public String getShortName() {
			return "P("
//					+calc.getName().replaceAll(" ", "")
					+Arrays.stream(calcs).map(E -> E.getName().replaceAll(" ", "")).collect(Collectors.joining(", "))
					+")≥"+minProbability;
		}

		@Override
		public String getName() {
			return "P("
//					+calc.getName()
					+Arrays.stream(calcs).map(E -> E.getName()).collect(Collectors.joining(", "))
					+") ≥"+minProbability;
		}
		
	}
	
	public static class ScalarPathPlausibilityFilter<E extends Number & Comparable<E>>
	extends PathPlausibilityFilter implements ScalarValuePlausibiltyFilter<E> {
		
		private ScalarNucleationClusterEvaluator<E> evaluator;
		
		public ScalarPathPlausibilityFilter(ScalarNucleationClusterEvaluator<E> evaluator) {
			this(0f, evaluator);
		}

		public ScalarPathPlausibilityFilter(float fractPassThreshold, ScalarNucleationClusterEvaluator<E> evaluator) {
			super(fractPassThreshold, false, evaluator);
			this.evaluator = evaluator;
		}

		@Override
		public E getValue(ClusterRupture rupture) {
			if (rupture.getTotalNumJumps()  == 0)
				return null;
			RuptureTreeNavigator navigator = rupture.getTreeNavigator();
			List<E> vals = new ArrayList<>();
			for (FaultSubsectionCluster nucleationCluster : rupture.getClustersIterable()) {
//				float val = testNucleationPoint(navigator, nucleationCluster, false, false);
				E val = evaluator.getNucleationClusterValue(rupture, nucleationCluster, false);
				vals.add(val);
			}
			if (fractPassThreshold > 0f) {
				// if we need N paths to pass, return the Nth largest value outside
				// (such that if and only if that value passes, the rupture passes)
				int numPaths = vals.size();
				int numNeeded = Integer.max(1, (int)Math.ceil(fractPassThreshold*numPaths));
				Collections.sort(vals, worstToBestComparator());
				return vals.get(vals.size()-numNeeded);
			}
//			Float bestVal = getWorseValue(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
			E bestVal = null;
			for (E val : vals) {
				if (bestVal == null || isValueBetter(val, bestVal))
					bestVal = val;
			}
			return bestVal;
		}

		@Override
		public Range<E> getAcceptableRange() {
			return evaluator.getAcceptableRange();
		}

		@Override
		public String getScalarName() {
			return evaluator.getScalarName();
		}

		@Override
		public String getScalarUnits() {
			return evaluator.getScalarUnits();
		}
		
	}
	
	protected final float fractPassThreshold;
	private final NucleationClusterEvaluator[] evaluators;
	private final boolean logicalOr;
	
	private transient final PlausibilityResult failureType;
	
	public PathPlausibilityFilter(NucleationClusterEvaluator... evaluators) {
		this(0f, evaluators);
	}

	public PathPlausibilityFilter(float fractPassThreshold, NucleationClusterEvaluator... evaluators) {
		this(fractPassThreshold, false, evaluators);
	}

	public PathPlausibilityFilter(float fractPassThreshold, boolean logicalOr, NucleationClusterEvaluator... evaluators) {
		Preconditions.checkState(fractPassThreshold <= 1f);
		this.fractPassThreshold = fractPassThreshold;
		this.logicalOr = logicalOr;
		Preconditions.checkArgument(evaluators.length > 0, "must supply at least one path evaluator");
		this.evaluators = evaluators;
		PlausibilityResult failureType = null;
		for (NucleationClusterEvaluator eval : evaluators) {
			if (failureType == null)
				failureType = eval.getFailureType();
			else
				failureType = failureType.logicalAnd(eval.getFailureType());
		}
		this.failureType = failureType;
		Preconditions.checkState(!failureType.isPass());
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumJumps() == 0)
			return PlausibilityResult.PASS;
		List<FaultSubsectionCluster> clusters = Lists.newArrayList(rupture.getClustersIterable());
		int numPaths = clusters.size();
		int numPasses = 0;
		int numNeeded = 1;
		if (fractPassThreshold > 0f)
			numNeeded = Integer.max(1, (int)Math.ceil(fractPassThreshold*numPaths));
		HashSet<FaultSubsectionCluster> skipClusters = null;
		if (rupture instanceof FilterDataClusterRupture) {
			FilterDataClusterRupture fdRupture = (FilterDataClusterRupture)rupture;
			Object filterData = fdRupture.getFilterData(this);
			if (filterData != null && filterData instanceof HashSet<?>)
				skipClusters = new HashSet<>((HashSet<FaultSubsectionCluster>)filterData); 
			else
				skipClusters = new HashSet<>();
			fdRupture.addFilterData(this, skipClusters);
		}
		for (FaultSubsectionCluster nucleationCluster : clusters) {
			if (skipClusters != null && skipClusters.contains(nucleationCluster))
				// we can skip this one because it already failed in a subset of this rupture so it will
				// never pass here
				continue;
			
			PlausibilityResult result = PlausibilityResult.PASS;
			if (verbose)
				System.out.println(getShortName()+": Nucleation point "+nucleationCluster);
			for (NucleationClusterEvaluator eval : evaluators) {
				PlausibilityResult subResult = eval.testNucleationCluster(rupture, nucleationCluster, verbose);
				if (verbose)
					System.out.println("\t"+eval.getName()+": "+subResult);
				if (logicalOr)
					result = result.logicalOr(subResult);
				else
					result = result.logicalAnd(subResult);
			}
			if (result.isPass())
				numPasses++;
			else if (skipClusters != null)
				skipClusters.add(nucleationCluster);
			if (!verbose && numPasses >= numNeeded)
				return PlausibilityResult.PASS;
		}
		if (verbose)
			System.out.println(getShortName()+": "+numPasses+"/"+numPaths+" pass, "+numNeeded+" needed");
		if (numPasses >= numNeeded)
			return PlausibilityResult.PASS;
		return failureType;
	}
	
	private String getPathString() {
		if (fractPassThreshold > 0f) {
			if (fractPassThreshold == 0.5f)
				return "Half Paths";
			if (fractPassThreshold == 1f/3f)
				return "1/3 Paths";
			if (fractPassThreshold == 2f/3f)
				return "2/3 Paths";
			if (fractPassThreshold == 0.25f)
				return "1/4 Paths";
			if (fractPassThreshold == 0.75f)
				return "3/4 Paths";
			return fractPassThreshold+"x Paths ";
		}
		return "Path";
	}

	@Override
	public String getShortName() {
		String paths = getPathString().replaceAll(" ", "");
		if (evaluators.length > 1)
			return paths+"["+evaluators.length+" criteria]";
		return paths+evaluators[0].getShortName();
	}

	@Override
	public String getName() {
		if (evaluators.length == 1)
			return getPathString()+" "+evaluators[0].getName();
		return getPathString()+" ["+Arrays.stream(evaluators).map(E -> E.getName()).collect(Collectors.joining(", "))+"]";
	}
	
	@Override
	public boolean isDirectional(boolean splayed) {
		return splayed;
	}

	@Override
	public TypeAdapter<PlausibilityFilter> getTypeAdapter() {
		return new Adapter();
	}
	
	public static class Adapter extends PlausibilityFilterTypeAdapter {

		private Gson gson;
		private ClusterConnectionStrategy connStrategy;
		private SectionDistanceAzimuthCalculator distAzCalc;

		@Override
		public void init(ClusterConnectionStrategy connStrategy, SectionDistanceAzimuthCalculator distAzCalc,
				Gson gson) {
			this.connStrategy = connStrategy;
			this.distAzCalc = distAzCalc;
			this.gson = gson;
		}

		@Override
		public void write(JsonWriter out, PlausibilityFilter value) throws IOException {
			Preconditions.checkState(value instanceof PathPlausibilityFilter);
			PathPlausibilityFilter filter = (PathPlausibilityFilter)value;
			out.beginObject();

			out.name("fractPassThreshold").value(filter.fractPassThreshold);
			out.name("logicalOr").value(filter.logicalOr);
			out.name("evaluators").beginArray();
			for (NucleationClusterEvaluator eval : filter.evaluators) {
				out.beginObject();
				out.name("class").value(eval.getClass().getName());
				out.name("value");
				if (eval instanceof CumulativeJumpProbPathEvaluator) {
					out.beginObject();
					CumulativeJumpProbPathEvaluator pathEval = (CumulativeJumpProbPathEvaluator)eval;
					out.name("minProbability").value(pathEval.minProbability);
					out.name("failureType").value(pathEval.failureType.name());
					out.name("calcs").beginArray();
					for (RuptureProbabilityCalc calc : pathEval.calcs) {
						out.beginObject();
						out.name("class").value(calc.getClass().getName());
						out.name("value");
						gson.toJson(calc, calc.getClass(), out);
						out.endObject();
					}
					out.endArray();
					out.endObject();
				} else {
					gson.toJson(eval, eval.getClass(), out);
				}
				out.endObject();
			}
			out.endArray();
			
			out.endObject();
		}

		@Override
		public PlausibilityFilter read(JsonReader in) throws IOException {
			in.beginObject();
			
			Float fractPassThreshold = null;
			Boolean logicalOr = null;
			NucleationClusterEvaluator[] evaluators = null;
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "fractPassThreshold":
					fractPassThreshold = (float)in.nextDouble();
					break;
				case "logicalOr":
					logicalOr = in.nextBoolean();
					break;
				case "evaluators":
					ArrayList<NucleationClusterEvaluator> list = new ArrayList<>();
					in.beginArray();
					while (in.hasNext()) {
						in.beginObject();
						
						Class<NucleationClusterEvaluator> type = null;
						NucleationClusterEvaluator eval = null;
						
						while (in.hasNext()) {
							switch (in.nextName()) {
							case "class":
								try {
									type = PlausibilityConfiguration.getDeclaredTypeClass(in.nextString());
								} catch (ClassNotFoundException e) {
									throw ExceptionUtils.asRuntimeException(e);
								}
								break;
							case "value":
								Preconditions.checkNotNull(type, "Class must preceed value in PathPlausibility JSON");
								if (type.equals(CumulativeJumpProbPathEvaluator.class)) {
									in.beginObject();
									Float minProbability = null;
									PlausibilityResult failureType = null;
									RuptureProbabilityCalc[] calcs = null;
									while (in.hasNext()) {
										switch (in.nextName()) {
										case "minProbability":
											minProbability = (float)in.nextDouble();
											break;
										case "failureType":
											failureType = PlausibilityResult.valueOf(in.nextString());
											break;
										case "calcs":
											in.beginArray();
											List<RuptureProbabilityCalc> calcList = new ArrayList<>();
											while (in.hasNext()) {
												in.beginObject();
												Class<RuptureProbabilityCalc> calcType = null;
												RuptureProbabilityCalc calc = null;
												while (in.hasNext()) {
													switch (in.nextName()) {
													case "class":
														try {
															calcType = PlausibilityConfiguration.getDeclaredTypeClass(in.nextString());
														} catch (ClassNotFoundException e) {
															throw ExceptionUtils.asRuntimeException(e);
														}
														break;
													case "value":
														Preconditions.checkNotNull(calcType, "Class must preceed value in PathPlausibility JSON");
														calc = gson.fromJson(in, calcType);
														break;

													default:
														throw new IllegalStateException("Unexpected JSON field");
													}
												}
												Preconditions.checkNotNull(calc, "Calculator is null?");
												calcList.add(calc);
												in.endObject();
											}
											in.endArray();
											calcs = calcList.toArray(new RuptureProbabilityCalc[0]);
											break;

										default:
											throw new IllegalStateException("Unexpected JSON field");
										}
									}
									in.endObject();
									eval = new CumulativeJumpProbPathEvaluator(minProbability, failureType, calcs);
								} else {
									eval = gson.fromJson(in, type);
								}
								break;

							default:
								throw new IllegalStateException("Unexpected JSON field");
							}
						}
						Preconditions.checkNotNull(eval, "Evaluator is null?");
						eval.init(connStrategy, distAzCalc);
						list.add(eval);
						
						in.endObject();
					}
					in.endArray();
					Preconditions.checkState(!list.isEmpty(), "No prob calcs?");
					evaluators = list.toArray(new NucleationClusterEvaluator[0]);
					break;

				default:
					throw new IllegalStateException("Unexpected JSON field");
				}
			}
			in.endObject();

			Preconditions.checkNotNull(fractPassThreshold, "fractPassThreshold not supplied");
			Preconditions.checkNotNull(logicalOr, "logicalOr not supplied");
			Preconditions.checkNotNull(evaluators, "evaluators not supplied");
			if (evaluators.length == 1 && evaluators[0] instanceof ScalarNucleationClusterEvaluator<?>)
				return new ScalarPathPlausibilityFilter<>(fractPassThreshold, (ScalarNucleationClusterEvaluator<?>)evaluators[0]);
			return new PathPlausibilityFilter(fractPassThreshold, logicalOr, evaluators);
		}
		
	}
	
//	public static void main(String[] args) throws ZipException, IOException, DocumentException {
//		// for profiling
//		File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
//		FaultSystemRupSet rupSet = FaultSystemIO.loadRupSet(
//				new File(rupSetsDir, "fm3_1_cmlAz_cffClusterPathPositive.zip"));
//		
//		SubSectStiffnessCalculator stiffnessCalc = new SubSectStiffnessCalculator(
//				rupSet.getFaultSectionDataList(), 2d, 3e4, 3e4, 0.5);
//		stiffnessCalc.setPatchAlignment(PatchAlignment.FILL_OVERLAP);
//		AggregatedStiffnessCache stiffnessCache = stiffnessCalc.getAggregationCache(StiffnessType.CFF);
//		File stiffnessCacheFile = new File(rupSetsDir, stiffnessCache.getCacheFileName());
//		if (stiffnessCacheFile.exists())
//			stiffnessCache.loadCacheFile(stiffnessCacheFile);
//		
//		AggregatedStiffnessCalculator aggCalc =
////				AggregatedStiffnessCalculator.buildMedianPatchSumSects(StiffnessType.CFF, stiffnessCalc);
//				AggregatedStiffnessCalculator.builder(StiffnessType.CFF, stiffnessCalc)
//				.flatten()
//				.process(AggregationMethod.MEDIAN)
//				.process(AggregationMethod.SUM)
////				.passthrough()
//				.process(AggregationMethod.SUM).get();
//		System.out.println("Aggregator: "+aggCalc);
//		PathPlausibilityFilter filter = new PathPlausibilityFilter(aggCalc, 0f);
//		
//		ClusterRupture largest = null;
//		for (ClusterRupture rup : rupSet.getClusterRuptures())
//			if (largest == null || rup.getTotalNumSects() > largest.getTotalNumSects())
//				largest = rup;
//		System.out.println("Benchmarking with a largest rupture ("+largest.getTotalNumSects()+" sects):\n\t"+largest);
////		int num = 1000000;
//		int num = 1;
//		boolean verbose = true;
//		Stopwatch watch = Stopwatch.createStarted();
//		for (int i=0; i<num; i++) {
//			if (i % 1000 == 0) {
//				double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
//				double rate = i/secs;
//				System.out.println("processed "+i+" in "+(float)secs+" s:\t"+(float)rate+" per second");
//			}
//			filter.apply(largest, verbose);
//		}
//		watch.stop();
//		double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
//		double rate = num/secs;
//		System.out.println("processed "+num+" in "+(float)secs+" s: "+(float)rate+" per second");
//	}

}
