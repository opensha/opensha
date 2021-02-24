package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.List;

import org.opensha.commons.data.Named;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Cluster permutation strategy which defines all viable unique combinations of subsections within
 * a FaultSubsectionCluster
 * 
 * @author kevin
 *
 */
public interface ClusterPermutationStrategy extends Named {
	
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
	
	/**
	 * Builds a list of all viable subsection permutations of the given cluster which start with
	 * the given section. Any viable connections to other sections must be added to each permutation.
	 * 
	 * This method gives a rupture to which this cluster may be added, allowing for the permutation
	 * strategy to change as ruptures grow. The default implementation defers to that without a rupture.
	 * 
	 * @param currentRupture the current rupture to which this cluster may be added
	 * @param fullCluster the full cluster
	 * @param firstSection the starting section from which all permutations should be built
	 * @return list of viable permutations
	 */
	public default List<FaultSubsectionCluster> getPermutations(ClusterRupture currentRupture,
			FaultSubsectionCluster fullCluster, FaultSection firstSection) {
		return getPermutations(fullCluster, firstSection);
	}
}
