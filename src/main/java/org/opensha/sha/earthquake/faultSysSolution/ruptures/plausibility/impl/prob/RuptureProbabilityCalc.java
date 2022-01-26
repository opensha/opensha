package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import org.opensha.commons.data.Named;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

import com.google.common.base.Preconditions;

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
	
	public class MultiProduct implements RuptureProbabilityCalc {
		
		private RuptureProbabilityCalc[] calcs;

		public MultiProduct(RuptureProbabilityCalc... calcs) {
			Preconditions.checkState(calcs.length > 1);
			this.calcs = calcs;
		}

		@Override
		public String getName() {
			return "Product of "+calcs.length+" models";
		}

		@Override
		public double calcRuptureProb(ClusterRupture rupture, boolean verbose) {
			double product = 1;
			for (RuptureProbabilityCalc calc : calcs)
				product *= calc.calcRuptureProb(rupture, verbose);
			return product;
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			for (RuptureProbabilityCalc calc : calcs)
				if (calc.isDirectional(splayed))
					return true;
			return false;
		}
		
	}
}