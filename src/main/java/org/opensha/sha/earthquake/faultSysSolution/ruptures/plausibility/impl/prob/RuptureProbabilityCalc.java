package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import org.opensha.commons.data.Named;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

/**
 * Inteface for a rupture probability calculator
 * 
 * @author kevin
 *
 */
public interface RuptureProbabilityCalc extends Named {
	
	/**
	 * This computes the probability of this rupture occurring as defined, conditioned on
	 * it beginning at the first cluster in this rupture
	 * 
	 * @param rupture
	 * @param verbose
	 * @return conditional probability of this rupture
	 */
	public double calcRuptureProb(ClusterRupture rupture, boolean verbose);
	
	public default void init(ClusterConnectionStrategy connStrat, SectionDistanceAzimuthCalculator distAzCalc) {
		// do nothing
	}
	
	public boolean isDirectional(boolean splayed);
}