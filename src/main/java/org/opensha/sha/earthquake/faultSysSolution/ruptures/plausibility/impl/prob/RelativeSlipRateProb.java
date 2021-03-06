package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathEvaluator.PathAddition;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

/**
 * Probability calculator that compares the slip rate of each jump taken to all other possible paths not taken.
 * 
 * @author kevin
 *
 */
public class RelativeSlipRateProb extends AbstractRelativeProb {
	
	private boolean onlyAtIncreases;

	public RelativeSlipRateProb(ClusterConnectionStrategy connStrat, boolean onlyAtIncreases) {
		super(connStrat, false, true); // never negative, always relative to best
		this.onlyAtIncreases = onlyAtIncreases;
	}
	
	@Override
	public double calcAdditionValue(ClusterRupture fullRupture, Collection<? extends FaultSection> currentSects,
			PathAddition addition) {
		return calcAveSlipRate(addition.toSects);
	}

	private double calcAveSlipRate(Collection<? extends FaultSection> subSects) {
		double aveVal = 0d;
		for (FaultSection sect : subSects)
			aveVal += sect.getOrigAveSlipRate();
		aveVal /= subSects.size();
		return aveVal;
	}
	
	@Override
	public String getName() {
		if (onlyAtIncreases)
			return "Rel Slip Rate (@increases)";
		return "Rel Slip Rate";
	}

	@Override
	public boolean isAddFullClusters() {
		return true;
	}

	@Override
	public HashSet<FaultSubsectionCluster> getSkipToClusters(ClusterRupture rupture) {
		if (onlyAtIncreases) {
			int totNumClusters = rupture.getTotalNumClusters();
			if (totNumClusters == 1)
				return null;
			// only include jump probabilities that precede an increase in slip rate (can always go down, it's when
			// you try to go back up after going down that you get penalized
			List<FaultSubsectionCluster> endClusters = new ArrayList<>();
			findEndClusters(rupture, endClusters);
			HashSet<FaultSubsectionCluster> skipToClusters = new HashSet<>();
			RuptureTreeNavigator nav = rupture.getTreeNavigator();
			for (FaultSubsectionCluster cluster : endClusters) {
				double clusterVal = calcAveSlipRate(cluster.subSects);
				FaultSubsectionCluster predecessor = nav.getPredecessor(cluster);
				while (predecessor != null) {
					double predecessorVal = calcAveSlipRate(predecessor.subSects);
					if (predecessorVal < clusterVal)
						// going to this cluster was an increase, stop here
						break;
					skipToClusters.add(cluster);
					cluster = predecessor;
					clusterVal = predecessorVal;
					predecessor = nav.getPredecessor(cluster);
				}
			}
			Preconditions.checkState(skipToClusters.size() < totNumClusters);
			return skipToClusters;
		}
		return null;
	}
	
	private void findEndClusters(ClusterRupture rupture, List<FaultSubsectionCluster> endClusters) {
		endClusters.add(rupture.clusters[rupture.clusters.length-1]);
		for (ClusterRupture splay : rupture.splays.values())
			findEndClusters(splay, endClusters);
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		return true;
	}
	
}