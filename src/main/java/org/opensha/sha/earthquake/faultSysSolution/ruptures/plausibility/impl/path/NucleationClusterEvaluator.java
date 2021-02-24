package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.opensha.commons.data.ShortNamed;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This is the primary interface for the PathProbabilityFilter. This interface evaluates a given cluster from a rupture
 * as a nucleation location, and will be called for each possible nucleation spot.
 * 
 * @author kevin
 *
 */
public interface NucleationClusterEvaluator extends ShortNamed {
	
	public PlausibilityResult testNucleationCluster(ClusterRupture rupture,
			FaultSubsectionCluster nucleationCluster, boolean verbose);
	
	public PlausibilityResult getFailureType();
	
	public default List<FaultSubsectionCluster> getDestinations(RuptureTreeNavigator nav,
			FaultSubsectionCluster from, HashSet<FaultSubsectionCluster> strandClusters) {
		List<FaultSubsectionCluster> ret = new ArrayList<>();
		
		FaultSubsectionCluster predecessor = nav.getPredecessor(from);
		if (predecessor != null && (strandClusters == null || !strandClusters.contains(predecessor)))
			ret.add(predecessor);
		
		for (FaultSubsectionCluster descendant : nav.getDescendants(from))
			if (strandClusters == null || !strandClusters.contains(descendant))
				ret.add(descendant);
		
		return ret;
	}
	
	public default void init(ClusterConnectionStrategy connStrat, SectionDistanceAzimuthCalculator distAzCalc) {
		// do nothing
	}
	
	public static interface Scalar<E extends Number & Comparable<E>> extends NucleationClusterEvaluator {
		public E getNucleationClusterValue(ClusterRupture rupture,
				FaultSubsectionCluster nucleationCluster, boolean verbose);
		
		/**
		 * @return acceptable range of values
		 */
		public Range<E> getAcceptableRange();
		
		/**
		 * @return name of this scalar value
		 */
		public String getScalarName();
		
		/**
		 * @return units of this scalar value
		 */
		public String getScalarUnits();
	}
}