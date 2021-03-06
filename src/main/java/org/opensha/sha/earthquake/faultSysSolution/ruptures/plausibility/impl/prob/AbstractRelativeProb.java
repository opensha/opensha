package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathEvaluator.ClusterPathNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathEvaluator.PathAddition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathEvaluator.PathNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathEvaluator.SectionPathNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

/**
 * Abstract base class for relative probabilities
 * 
 * @author kevin
 *
 */
public abstract class AbstractRelativeProb implements RuptureProbabilityCalc {

	protected transient ClusterConnectionStrategy connStrat;
	protected boolean allowNegative;
	protected boolean relativeToBest = true;

	protected transient Map<Integer, FaultSubsectionCluster> fullClustersMap;

	public AbstractRelativeProb(ClusterConnectionStrategy connStrat, boolean allowNegative, boolean relativeToBest) {
		this.connStrat = connStrat;
		this.allowNegative = allowNegative;
		this.relativeToBest = relativeToBest;
	}

	public ClusterConnectionStrategy getConnStrat() {
		return connStrat;
	}

	public boolean isAllowNegative() {
		return allowNegative;
	}

	public boolean isRelativeToBest() {
		return relativeToBest;
	}

	@Override
	public void init(ClusterConnectionStrategy connStrat, SectionDistanceAzimuthCalculator distAzCalc) {
		this.connStrat = connStrat;
	}

	private void checkInitFullClusters() {
		if (fullClustersMap == null) {
			synchronized (this) {
				if (fullClustersMap == null) {
					Map<Integer, FaultSubsectionCluster> map = new HashMap<>();
					for (FaultSubsectionCluster cluster : connStrat.getClusters())
						map.put(cluster.parentSectionID, cluster);
					this.fullClustersMap = map;
				}
			}
		}
		return;
	}

	/**
	 * Calculates the value associated with adding the given section(s) to a rupture consisting of currentSects
	 * @param fullRupture the complete rupture
	 * @param currentSects the sections considered so far
	 * @param addition the addition to test
	 * @return value associated with adding this addition
	 */
	public abstract double calcAdditionValue(ClusterRupture fullRupture, Collection<? extends FaultSection> currentSects,
			PathAddition addition);

	/**
	 * 
	 * @return true if paths & tests should be taken cluster-by-cluster, false if they should be section-by-section
	 */
	public abstract boolean isAddFullClusters();

	public PathNavigator getPathNav(ClusterRupture rupture, FaultSubsectionCluster nucleationCluster) {
		if (isAddFullClusters())
			return new ClusterPathNavigator(nucleationCluster, rupture.getTreeNavigator());
		return new SectionPathNavigator(nucleationCluster.subSects, rupture.getTreeNavigator());
	}

	public HashSet<FaultSubsectionCluster> getSkipToClusters(ClusterRupture rupture) {
		return null;
	}

	public PathAddition targetJumpToAddition(Collection<? extends FaultSection> curSects,
			PathAddition testAddition, Jump alternateJump) {
		Collection<FaultSection> toSects = isAddFullClusters() ?
				alternateJump.toCluster.subSects : Collections.singleton(alternateJump.toSection);
		return new PathAddition(testAddition.fromSect, testAddition.fromCluster, toSects, alternateJump.toCluster);
	}

	@Override
	public double calcRuptureProb(ClusterRupture rupture, boolean verbose) {
		HashSet<FaultSubsectionCluster> skipToClusters = getSkipToClusters(rupture);
		double prob = 1d;

		RuptureTreeNavigator rupNav = rupture.getTreeNavigator();
		PathNavigator pathNav = getPathNav(rupture, rupture.clusters[0]);
		pathNav.setVerbose(verbose);

		if (verbose)
			System.out.println(getName()+": testing with start="+rupture.clusters[0]);

		List<FaultSection> curSects = pathNav.getCurrentSects();
		Set<PathAddition> nextAdds = pathNav.getNextAdditions();
		if (verbose)
			System.out.println("Have "+nextAdds.size()+" nextAdds");

		boolean addFullClusters = isAddFullClusters();

		while (!nextAdds.isEmpty()) {
			for (PathAddition add : nextAdds) {
				if (skipToClusters != null && skipToClusters.contains(rupNav.locateCluster(add.toSects.iterator().next()))) {
					if (verbose)
						System.out.println(getName()+": skipping addition: "+add);
					continue;
				}
				prob *= calcAdditionProb(rupture, curSects, add, verbose);
				if (prob == 0d && !verbose)
					break;
			}

			curSects = pathNav.getCurrentSects();
			nextAdds = pathNav.getNextAdditions();
			if (verbose)
				System.out.println("Have "+nextAdds.size()+" nextAdds");
		}
		Preconditions.checkState(pathNav.getCurrentSects().size() == rupture.getTotalNumSects(),
				"Processed %s sects but rupture has %s:\n\t%s", pathNav.getCurrentSects().size(), rupture.getTotalNumSects(), rupture);

		return prob;
	}
	
	protected double calcAdditionProb(ClusterRupture rupture, List<FaultSection> curSects, PathAddition add, boolean verbose) {
		double myVal = calcAdditionValue(rupture, curSects, add);
		if (verbose)
			System.out.println("\tAddition taken value ("+add+"): "+myVal);
		if (!allowNegative && myVal < 0)
			return 0d;

		checkInitFullClusters();
		FaultSubsectionCluster fullFrom = fullClustersMap.get(add.fromCluster.parentSectionID);
		Preconditions.checkNotNull(fullFrom);
		List<PathAddition> targetAdditions = new ArrayList<>();
		if (fullFrom.subSects.size() > add.fromCluster.subSects.size()
				&& !fullFrom.endSects.contains(add.fromSect)) {
			// need to add continuing on this cluster as a possible "jump"
			int fromSectIndex = fullFrom.subSects.indexOf(add.fromSect);
			Preconditions.checkState(fromSectIndex >=0, "From section not found in full cluster?");
			if (fromSectIndex < fullFrom.subSects.size()-1) {
				// try going forward in list
				List<FaultSection> possibleSects = new ArrayList<>();
				for (int i=fromSectIndex+1; i<fullFrom.subSects.size(); i++) {
					FaultSection sect = fullFrom.subSects.get(i);
					// don't include rupture in the check here: even if we go to that section later, we still
					// want to penalize for not taking the better path now
					//								if (add.fromCluster.contains(sect) || rupture.contains(sect))
					if (add.fromCluster.contains(sect))
						break;
					possibleSects.add(sect);
					if (!isAddFullClusters())
						break;
				}
				if (!possibleSects.isEmpty())
					targetAdditions.add(new PathAddition(add.fromSect, add.fromCluster, possibleSects, fullFrom));
			}

			if (fromSectIndex > 0) {
				// try going backward in list
				List<FaultSection> possibleSects = new ArrayList<>();
				//							for (int i=fromSectIndex+1; i<fullFrom.subSects.size(); i++) {
				for (int i=fromSectIndex; --i>=0;) {
					FaultSection sect = fullFrom.subSects.get(i);
					// don't include rupture in the check here: even if we go to that section later, we still
					// want to penalize for not taking the better path now
					//								if (add.fromCluster.contains(sect) || rupture.contains(sect))
					if (add.fromCluster.contains(sect))
						break;
					possibleSects.add(sect);
					if (!isAddFullClusters())
						break;
				}
				if (!possibleSects.isEmpty())
					targetAdditions.add(new PathAddition(add.fromSect, add.fromCluster, possibleSects, fullFrom));
			}
		}
		// now add possible jumps to other clusters

		for (Jump possible : fullFrom.getConnections(add.fromSect)) {
			if (possible.toCluster.parentSectionID != add.toCluster.parentSectionID
					&& !rupture.contains(possible.toSection))
				targetAdditions.add(targetJumpToAddition(curSects, add, possible));
		}
		List<Double> targetVals = new ArrayList<>();
		for (PathAddition targetAdd : targetAdditions) {
			double val = calcAdditionValue(rupture, curSects, targetAdd);
			if (verbose)
				System.out.println("\tAlternative dest value ("+targetAdd+"): "+val);
			targetVals.add(val);
		}
		double myProb = calcProb(myVal, targetVals, verbose);
		return myProb;
	}

	private double calcProb(double myVal, List<Double> targetVals, boolean verbose) {
		if (targetVals.isEmpty()) {
			// no alternatives
			if (verbose)
				System.out.println("\tno alternatives!");
			return 1d;
		}
		double normalization = Math.min(myVal, 0d);
		if (allowNegative && myVal < 0)
			for (double val : targetVals)
				normalization = Math.min(val, normalization);
		if (normalization != 0d) {
			if (verbose)
				System.out.println("\tNormalizing by min value: "+normalization);
			myVal -= normalization;
			for (int i=0; i<targetVals.size(); i++)
				targetVals.set(i, targetVals.get(i) - normalization);
		}
		double divisor = myVal;
		if (relativeToBest) {
			for (double val : targetVals)
				divisor = Double.max(val, divisor);
		} else {
			for (double val : targetVals)
				divisor += val;
		}
		if (verbose) {
			if (relativeToBest)
				System.out.println("\tBest: "+divisor);
			else
				System.out.println("\tSum: "+divisor);
		}
		Preconditions.checkState((float)divisor >= 0f,
				"Bad relative divisor = %s.\n\tnormalization: %s\n\tmyVal: %s\n\tallVals (after norm): %s",
				divisor, normalization, myVal, targetVals);
		if ((float)divisor == 0f)
			return 0d;

		double prob = myVal / divisor;
		if (verbose)
			System.out.println("\tP = "+myVal+" / "+divisor+" = "+prob);
		Preconditions.checkState(prob >= 0d && prob <= 1d,
				"Bad relative prob! P = %s / %s = %s.\n\tnormalization: %s\n\tallVals (after norm): %s",
				myVal, divisor, prob, normalization, targetVals);
		return prob;
	}

}