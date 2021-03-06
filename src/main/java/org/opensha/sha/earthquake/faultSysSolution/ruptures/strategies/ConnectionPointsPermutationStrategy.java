package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

/**
 * This just returns full clusters from the given start section to either end of the cluster or exit points
 * (no permutations in-between);
 * 
 * @author kevin
 *
 */
public class ConnectionPointsPermutationStrategy implements ClusterPermutationStrategy {

	@Override
	public List<FaultSubsectionCluster> getPermutations(FaultSubsectionCluster fullCluster,
			FaultSection firstSection) {
		return getPermutations(null, fullCluster, firstSection);
	}

	@Override
	public List<FaultSubsectionCluster> getPermutations(ClusterRupture currentRupture,
			FaultSubsectionCluster fullCluster, FaultSection firstSection) {
		List<FaultSection> clusterSects = fullCluster.subSects;
		int myInd = fullCluster.subSects.indexOf(firstSection);
		Preconditions.checkState(myInd >= 0, "first section not found in cluster");
		List<FaultSection> newSects = new ArrayList<>();
		newSects.add(firstSection);
		
		Set<FaultSection> exitPoints = fullCluster.getExitPoints();
		if (currentRupture != null && !exitPoints.isEmpty()) {
			// don't include connection points that only go to places already in this rupture
			List<FaultSection> toRemove = new ArrayList<>();
			for (FaultSection exit : exitPoints) {
				boolean externalConnection = false;
				for (Jump jump : fullCluster.getConnections(exit)) {
					if (!currentRupture.contains(jump.toSection)) {
						externalConnection = true;
						break;
					}
				}
				if (!externalConnection)
					toRemove.add(exit);
			}
			if (!toRemove.isEmpty()) {
				exitPoints = new HashSet<>(exitPoints);
				for (FaultSection remove : toRemove)
					exitPoints.remove(remove);
			}
		}
		
		List<FaultSubsectionCluster> permuations = new ArrayList<>();
		if (exitPoints.contains(firstSection))
			permuations.add(buildCopyJumps(fullCluster, newSects));
		
		// build toward the smallest ID
		if (myInd > 0) {
			for (int i=myInd; --i>=0;) {
				FaultSection nextSection = clusterSects.get(i);
				newSects.add(nextSection);
				if (exitPoints.contains(nextSection) || i == 0)
					permuations.add(buildCopyJumps(fullCluster, newSects));
			}
		}
		
		if (myInd < clusterSects.size()-1) {
			newSects = new ArrayList<>();
			newSects.add(firstSection);
			// build toward the largest ID
			for (int i=myInd+1; i<clusterSects.size(); i++) {
				FaultSection nextSection = clusterSects.get(i);
				newSects.add(nextSection);
				if (exitPoints.contains(nextSection) || i == clusterSects.size()-1)
					permuations.add(buildCopyJumps(fullCluster, newSects));
			}
		}
		return permuations;
	}
	
	private static FaultSubsectionCluster buildCopyJumps(FaultSubsectionCluster fullCluster,
			List<FaultSection> subsetSects) {
		FaultSubsectionCluster permutation = new FaultSubsectionCluster(new ArrayList<>(subsetSects));
		for (FaultSection sect : subsetSects)
			for (Jump jump : fullCluster.getConnections(sect))
				permutation.addConnection(new Jump(sect, permutation,
						jump.toSection, jump.toCluster, jump.distance));
		return permutation;
	}

	@Override
	public String getName() {
		return "Connection Points Only";
	}

}
