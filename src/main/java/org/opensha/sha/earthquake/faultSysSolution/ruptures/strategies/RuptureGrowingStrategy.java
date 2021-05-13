package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.List;

import org.opensha.commons.data.Named;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Rupture growing strategy which defines how each FaultSubsectionCluster is subdivided, and how ruptures spread onto
 * new fault sections.
 * 
 * @author kevin
 *
 */
public interface RuptureGrowingStrategy extends Named {
	
	/**
	 * Builds a list of all viable variations of the given cluster that start with the given section. Any viable
	 * connections to other sections must be added to each variation.
	 * 
	 * @param fullCluster the full cluster
	 * @param firstSection the starting section from which all permutations should be built
	 * @return list of viable variations of the cluster
	 */
	public List<FaultSubsectionCluster> getVariations(FaultSubsectionCluster fullCluster,
			FaultSection firstSection);
	
	/**
	 * Builds a list of all viable variations of the given cluster which start with the given section. Any viable
	 * connections to other sections must be added to each variation.
	 * 
	 * This method gives a rupture to which this cluster may be added, allowing for the growing
	 * strategy to change as ruptures grow. The default implementation defers to that without a rupture.
	 * 
	 * @param currentRupture the current rupture to which this cluster may be added
	 * @param fullCluster the full cluster
	 * @param firstSection the starting section from which all permutations should be built
	 * @return list of viable variations of the cluster
	 */
	public default List<FaultSubsectionCluster> getVariations(ClusterRupture currentRupture,
			FaultSubsectionCluster fullCluster, FaultSection firstSection) {
		return getVariations(fullCluster, firstSection);
	}
	
	/**
	 * Clears any caches if they exist, which could prevent this growing strategy from being used with other
	 * fault systems or connections strategies.
	 */
	public default void clearCaches() {
		// do nothing
	}
}
