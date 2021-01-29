package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Utility class to cache viable cluster permutations for faster rupture building
 * 
 * @author kevin
 *
 */
public abstract class CachedClusterPermutationStrategy implements ClusterPermutationStrategy {
	
	private Table<FaultSubsectionCluster, FaultSection, List<FaultSubsectionCluster>> cacheTable;
	
	public CachedClusterPermutationStrategy() {
		cacheTable = HashBasedTable.create();
	}

	@Override
	public final synchronized List<FaultSubsectionCluster> getPermutations(FaultSubsectionCluster fullCluster,
			FaultSection firstSection) {
		List<FaultSubsectionCluster> permutations = cacheTable.get(fullCluster, firstSection);
		if (permutations == null) {
			permutations = calcPermutations(fullCluster, firstSection);
			cacheTable.put(fullCluster, firstSection, permutations);
		}
		return permutations;
	}
	
	protected abstract List<FaultSubsectionCluster> calcPermutations(FaultSubsectionCluster fullCluster,
			FaultSection firstSection);

}
