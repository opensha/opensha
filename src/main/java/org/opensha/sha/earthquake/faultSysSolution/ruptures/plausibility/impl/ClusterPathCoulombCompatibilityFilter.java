package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarCoulombPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.FilterDataClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessAggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessResult;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This filter tests the Coulomb compatibility of each possible path through the given rupture. It tries each
 * cluster as the nucleation point, and builds outwards to each rupture endpoint. Ruptures pass if at least
 * one nucleation point is viable (or if at least the supplied fraction of paths pass).
 * 
 * @author kevin
 *
 */
public class ClusterPathCoulombCompatibilityFilter implements ScalarCoulombPlausibilityFilter {
	
	private SubSectStiffnessCalculator stiffnessCalc;
	private StiffnessAggregationMethod aggMethod;
	private float threshold;
	private float fractPassThreshold = 0; // default pass to if 1 or more paths pass

	public ClusterPathCoulombCompatibilityFilter(SubSectStiffnessCalculator stiffnessCalc,
			StiffnessAggregationMethod aggMethod, float threshold) {
		this(stiffnessCalc, aggMethod, threshold, 0f);
	}

	public ClusterPathCoulombCompatibilityFilter(SubSectStiffnessCalculator stiffnessCalc,
			StiffnessAggregationMethod aggMethod, float threshold, float fractPassThreshold) {
		this.stiffnessCalc = stiffnessCalc;
		this.aggMethod = aggMethod;
		this.threshold = threshold;
		Preconditions.checkState(fractPassThreshold <= 1f);
		this.fractPassThreshold = fractPassThreshold;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.getTotalNumJumps() == 0)
			return PlausibilityResult.PASS;
		RuptureTreeNavigator navigator = rupture.getTreeNavigator();
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
			float val = testNucleationPoint(navigator, nucleationCluster, !verbose, verbose);
			if (verbose)
				System.out.println(getShortName()+": Nucleation point "+nucleationCluster
						+", val="+val+", result: "+(val >= threshold));
			if (val >= threshold)
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
		return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
	}

	@Override
	public Float getValue(ClusterRupture rupture) {
		if (rupture.getTotalNumJumps()  == 0)
			return null;
		RuptureTreeNavigator navigator = rupture.getTreeNavigator();
		List<Float> vals = new ArrayList<>();
		for (FaultSubsectionCluster nucleationCluster : rupture.getClustersIterable()) {
			float val = testNucleationPoint(navigator, nucleationCluster, false, false);
			vals.add(val);
		}
		if (fractPassThreshold > 0f) {
			// if we need N paths to pass, return the Nth largest value outside
			// (such that if and only if that value passes, the rupture passes)
			int numPaths = vals.size();
			int numNeeded = Integer.max(1, (int)Math.ceil(fractPassThreshold*numPaths));
			Collections.sort(vals);
			return vals.get(vals.size()-numNeeded);
		}
		float max = Float.NEGATIVE_INFINITY;
		for (Float val : vals)
			max = Float.max(val, max);
		return max;
	}
	
	private float testNucleationPoint(RuptureTreeNavigator navigator,
			FaultSubsectionCluster nucleationCluster, boolean shortCircuit, boolean verbose) {
		HashSet<FaultSubsectionCluster> curClusters = new HashSet<>();
		curClusters.add(nucleationCluster);
		FaultSubsectionCluster predecessor = navigator.getPredecessor(nucleationCluster);
		Float minVal = Float.POSITIVE_INFINITY;
		if (verbose)
			System.out.println("Testing strand(s) with start="+nucleationCluster);
		if (predecessor != null) {
			if (verbose)
				System.out.println("\tTesting to predecessor="+predecessor);
			List<FaultSection> strandSects = getAddSectsToward(
					navigator, null, null, nucleationCluster, predecessor);
			float strandVal = testStrand(navigator, curClusters, strandSects, nucleationCluster, predecessor,
					shortCircuit, verbose);
			if (verbose)
				System.out.println("\tPredecessor strand val="+strandVal);
			minVal = Float.min(minVal, strandVal);
			if (shortCircuit && minVal < threshold)
				return minVal;
		}
		
		for (FaultSubsectionCluster descendant : navigator.getDescendants(nucleationCluster)) {
			if (verbose)
				System.out.println("\tTesting to descendant="+descendant);
			List<FaultSection> strandSects = getAddSectsToward(
					navigator, null, null, nucleationCluster, descendant);
			float strandVal = testStrand(navigator, curClusters, strandSects, nucleationCluster, descendant,
					shortCircuit, verbose);
			if (verbose)
				System.out.println("\tDescendant strand val="+strandVal);
			minVal = Float.min(minVal, strandVal);
			if (shortCircuit && minVal < threshold)
				return minVal;
		}
		
		return minVal;
	}
	
	private float testStrand(RuptureTreeNavigator navigator, HashSet<FaultSubsectionCluster> strandClusters,
			List<FaultSection> strandSections, FaultSubsectionCluster prevAddition,
			FaultSubsectionCluster addition, boolean shortCircuit,
			boolean verbose) {
		float minVal = Float.POSITIVE_INFINITY;
		if (!strandSections.isEmpty()) {
//			StiffnessResult stiffness = stiffnessCalc.calcAggClustersToClusterStiffness(
//					StiffnessType.CFF, strandClusters, addition);
			StiffnessResult stiffness = stiffnessCalc.calcAggStiffness(StiffnessType.CFF,
					strandSections, addition.subSects, -1, addition.parentSectionID);
			double val = stiffness.getValue(aggMethod);
			if (verbose)
				System.out.println("\t\tval="+val+" for "+strandClusters.size()+" clusters ("
						+strandSections.size()+" sects) to "+addition);
			minVal = Float.min(minVal, (float)val);
			if (shortCircuit && minVal < threshold)
				return (float)val;
		}
		
		// this addition passed, continue downstream
		HashSet<FaultSubsectionCluster> newStrandClusters = new HashSet<>(strandClusters);
		newStrandClusters.add(addition);
		
		// check predecessor of this strand
		FaultSubsectionCluster predecessor = navigator.getPredecessor(addition);
		if (predecessor != null && !strandClusters.contains(predecessor)) {
			// go down that path
			List<FaultSection> newStrandSects = getAddSectsToward(navigator, strandSections,
					prevAddition, addition, predecessor);
			float val = testStrand(navigator, newStrandClusters, newStrandSects, addition, predecessor,
					shortCircuit, verbose);
			minVal = Float.min(minVal, (float)val);
			if (shortCircuit && minVal < threshold)
				return (float)val;
		}
		
		// check descendants of this strand
		for (FaultSubsectionCluster descendant : navigator.getDescendants(addition)) {
			if (strandClusters.contains(descendant))
				continue;
			// go down that path
			List<FaultSection> newStrandSects = getAddSectsToward(navigator, strandSections,
					prevAddition, addition, descendant);
			float val = testStrand(navigator, newStrandClusters, newStrandSects, addition, descendant,
					shortCircuit, verbose);
			minVal = Float.min(minVal, (float)val);
			if (shortCircuit && minVal < threshold)
				return (float)val;
		}
		
		// if we m)ade it here, this either the end of the line or all downstream strand extensions pass
		return minVal;
	}
	
	private List<FaultSection> getAddSectsToward(RuptureTreeNavigator navigator, List<FaultSection> prevSects,
			FaultSubsectionCluster prevLastCluster, FaultSubsectionCluster newLastCluster,
			FaultSubsectionCluster destination) {
		List<FaultSection> sects = prevSects == null ? new ArrayList<>() : new ArrayList<>(prevSects);
		
		// jump to the destination
		Jump nextJump = navigator.getJump(newLastCluster, destination);
		Preconditions.checkState(nextJump.fromCluster == newLastCluster);
		Preconditions.checkState(nextJump.toCluster == destination);
		FaultSection destSect = nextJump.fromSection;
		
		FaultSection startSect;
		if (prevLastCluster == null) {
			// this is the first cluster, use the longest strand of sections up to this jump
			int jumpIndex = newLastCluster.subSects.indexOf(nextJump.fromSection);
			Preconditions.checkState(jumpIndex >= 0);
			int numFromStart = jumpIndex+1;
			int numFromEnd = newLastCluster.subSects.size()-jumpIndex;
			if (numFromEnd > numFromStart)
				startSect = newLastCluster.subSects.get(newLastCluster.subSects.size()-1);
			else
				startSect = newLastCluster.subSects.get(0);
//			System.out.println(newLastCluster.parentSectionID+" is a start, using longest strand to jump "
//					+ "starting at "+startSect.getSectionId());
		} else {
			// just the portion of this cluster on the direct path
			Jump prevJump = navigator.getJump(prevLastCluster, newLastCluster);
			Preconditions.checkState(prevJump.fromCluster == prevLastCluster);
			Preconditions.checkState(prevJump.toCluster == newLastCluster);
			startSect = prevJump.toSection;
//			System.out.println("Using startSect="+startSect.getSectionId()+" via jump from prevLast="+prevJump);
		}
		
		if (newLastCluster.endSects.contains(destSect) && newLastCluster.subSects.get(0).equals(startSect)
				|| newLastCluster.endSects.contains(startSect) && newLastCluster.subSects.get(0).equals(destSect)) {
			// simple case, it's a jump from the end, include all sections
			sects.addAll(newLastCluster.subSects);
//			System.out.println("added full "+newLastCluster.subSects.size()+" sects on "
//					+newLastCluster.parentSectionID+" between "+startSect.getSectionId()
//					+" and "+destSect.getSectionId());
		} else if (destSect.equals(startSect)) {
//			System.out.println("added single sect on "+newLastCluster.parentSectionID
//					+", start=dest="+startSect.getSectionId());
			sects.add(startSect);
		} else {
			// only include sections up to and including fromSection
			boolean inside = false;
			int added = 0;
			for (FaultSection sect : newLastCluster.subSects) {
				if (sect.equals(startSect) || sect.equals(destSect)) {
					inside = !inside;
					if (!inside)
						// we've encountered a start and an end, stop searching
						break;
				}
				if (inside) {
					sects.add(sect);
					added++;
				}
			}
//			System.out.println("added "+added+" sects on "+newLastCluster.parentSectionID+" between "
//					+startSect.getSectionId()+" and "+destSect.getSectionId());
		}
		return sects;
	}

	@Override
	public String getShortName() {
		String threshStr = threshold == 0f ? "0" : threshold+"";
		if (fractPassThreshold > 0f) {
			if (fractPassThreshold == 0.5f)
				return "HalfPathsCFF≥"+threshStr;
			if (fractPassThreshold == 1f/3f)
				return "1/3PathsCFF≥"+threshStr;
			if (fractPassThreshold == 2f/3f)
				return "2/3PathsCFF≥"+threshStr;
			if (fractPassThreshold == 0.25f)
				return "1/4PathsCFF≥"+threshStr;
			if (fractPassThreshold == 0.75f)
				return "3/4PathsCFF≥"+threshStr;
			return fractPassThreshold+"PathsCFF≥"+threshStr;
		}
		return "PathCFF≥"+threshStr;
	}

	@Override
	public String getName() {
		if (fractPassThreshold > 0f) {
			if (fractPassThreshold == 0.5f)
				return "Cluster Half Paths Coulomb  ≥ "+(float)threshold;
			if (fractPassThreshold == 1f/3f)
				return "Cluster 1/3 Paths Coulomb  ≥ "+(float)threshold;
			if (fractPassThreshold == 2f/3f)
				return "Cluster 2/3 Paths Coulomb  ≥ "+(float)threshold;
			if (fractPassThreshold == 0.25f)
				return "Cluster 1/4 Paths Coulomb  ≥ "+(float)threshold;
			if (fractPassThreshold == 0.75f)
				return "Cluster 3/4 Paths Coulomb  ≥ "+(float)threshold;
			return "Cluster "+fractPassThreshold+"x Paths Coulomb  ≥ "+(float)threshold;
		}
		return "Cluster Path Coulomb  ≥ "+(float)threshold;
	}

	@Override
	public Range<Float> getAcceptableRange() {
		return Range.atLeast((float)threshold);
	}

	@Override
	public SubSectStiffnessCalculator getStiffnessCalc() {
		return stiffnessCalc;
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		return splayed;
	}

}
