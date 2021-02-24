package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
		List<FaultSection> clusterSects = fullCluster.subSects;
		int myInd = fullCluster.subSects.indexOf(firstSection);
		Preconditions.checkState(myInd >= 0, "first section not found in cluster");
		List<FaultSection> newSects = new ArrayList<>();
		newSects.add(firstSection);
		
		Set<FaultSection> exitPoints = fullCluster.getExitPoints();
		
		List<FaultSubsectionCluster> permuations = new ArrayList<>();
		
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
