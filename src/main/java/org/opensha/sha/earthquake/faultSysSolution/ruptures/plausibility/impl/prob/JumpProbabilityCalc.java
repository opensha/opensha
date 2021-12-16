package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;

/**
 * Probability calculator that occurs independently at each jump
 * 
 * @author kevin
 *
 */
public interface JumpProbabilityCalc extends RuptureProbabilityCalc {
	
	/**
	 * This computes the probability of this jump occurring conditioned on the rupture
	 * up to that point having occurred, and relative to the rupture arresting rather
	 * than taking that jump.
	 * 
	 * @param fullRupture
	 * @param jump
	 * @param verbose
	 * @return conditional jump probability
	 */
	public abstract double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose);

	@Override
	public default double calcRuptureProb(ClusterRupture rupture, boolean verbose) {
		double prob = 1d;
		for (Jump jump : rupture.getJumpsIterable()) {
			double jumpProb = calcJumpProbability(rupture, jump, verbose);
			if (verbose)
				System.out.println(getName()+": "+jump+", P="+jumpProb);
			prob *= jumpProb;
			if (prob == 0d)
				// don't bother continuing
				break;
		}
		return prob;
	}
	
	public static interface DistDependentJumpProbabilityCalc extends JumpProbabilityCalc {
		
		/**
		 * This computes the probability of this jump occurring conditioned on the rupture
		 * up to that point having occurred, and relative to the rupture arresting rather
		 * than taking that jump.
		 * 
		 * @param fullRupture
		 * @param jump
		 * @param verbose
		 * @return conditional jump probability
		 */
		public default double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			return calcJumpProbability(jump.distance);
		}
		
		/**
		 * This computes the probability of this jump occurring conditioned on the rupture
		 * up to that point having occurred, and relative to the rupture arresting rather
		 * than taking that jump, as a function of jump distance only
		 * 
		 * @param distance
		 * @return conditional jump probability
		 */
		public abstract double calcJumpProbability(double distance);
	}
	
	public static interface BinaryJumpProbabilityCalc extends JumpProbabilityCalc {
		
		public boolean isJumpAllowed(ClusterRupture fullRupture, Jump jump, boolean verbose);

		@Override
		default double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			if (isJumpAllowed(fullRupture, jump, verbose))
				return 1d;
			return 0d;
		}
	}
	
}