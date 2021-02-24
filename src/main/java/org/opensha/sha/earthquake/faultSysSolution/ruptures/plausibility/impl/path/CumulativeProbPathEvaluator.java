package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This class uses a (or set of) RuptureProbabilityCalc instance to test each nucleation cluster. Ruptures are reorganized
 * soas to start at each cluster (with splays as necessary to represent bilateral spread) and then passed to the
 * RuptureProbabilityCalc
 * 
 * @author kevin
 *
 */
public class CumulativeProbPathEvaluator implements NucleationClusterEvaluator.Scalar<Float> {

	RuptureProbabilityCalc[] calcs;
	float minProbability;
	PlausibilityResult failureType;

	public CumulativeProbPathEvaluator(float minProbability, PlausibilityResult failureType, RuptureProbabilityCalc... calcs) {
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

	public RuptureProbabilityCalc[] getCalcs() {
		return calcs;
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