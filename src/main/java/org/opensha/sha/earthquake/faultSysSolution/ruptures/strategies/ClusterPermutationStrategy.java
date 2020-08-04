package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Cluster permutation strategy which defines all viable unique combinations of subsections within
 * a FaultSubsectionCluster
 * 
 * @author kevin
 *
 */
public interface ClusterPermutationStrategy {
	
	/**
	 * Builds a list of all viable subsection permutations of the given cluster which start with
	 * the given section. Any viable connections to other sections must be added to each permutation.
	 * 
	 * @param fullCluster the full cluster
	 * @param firstSection the starting section from which all permutations should be built
	 * @return list of viable permutations
	 */
	public List<FaultSubsectionCluster> getPermutations(FaultSubsectionCluster fullCluster,
			FaultSection firstSection);
}
