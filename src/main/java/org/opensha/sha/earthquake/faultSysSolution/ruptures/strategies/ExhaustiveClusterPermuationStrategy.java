package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

/**
 * Exhaustive rupture permutations (as used in UCERF3), which assumes that subsections are listed in order and connections
 * only exist between neighbors in that list
 * @author kevin
 *
 */
public class ExhaustiveClusterPermuationStrategy extends CachedClusterPermutationStrategy {

	@Override
	public List<FaultSubsectionCluster> calcPermutations(FaultSubsectionCluster fullCluster,
			FaultSection firstSection) {
		List<FaultSection> clusterSects = fullCluster.subSects;
		int myInd = fullCluster.subSects.indexOf(firstSection);
		Preconditions.checkState(myInd >= 0, "first section not found in cluster");
		List<FaultSection> newSects = new ArrayList<>();
		newSects.add(firstSection);
		
		List<FaultSubsectionCluster> permuations = new ArrayList<>();
		// just this section
		permuations.add(buildCopyJumps(fullCluster, newSects));
		
		// build toward the smallest ID
		for (int i=myInd; --i>=0;) {
			FaultSection nextSection = clusterSects.get(i);
			newSects.add(nextSection);
			permuations.add(buildCopyJumps(fullCluster, newSects));
		}
		newSects = new ArrayList<>();
		newSects.add(firstSection);
		// build toward the largest ID
		for (int i=myInd+1; i<clusterSects.size(); i++) {
			FaultSection nextSection = clusterSects.get(i);
			newSects.add(nextSection);
			permuations.add(buildCopyJumps(fullCluster, newSects));
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
		return "Exhaustive";
	}

}
