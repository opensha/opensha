package org.opensha.commons.data.sampling.scoring;

/**
 * Finite-state representation of a {@link DiscrepancyKernel}. Coordinates map to integer states, and all kernel values
 * and target expectations are evaluated in that same discrete state space. Consistently discretizing both observations
 * and the ideal target avoids treating quantization error as point-set discrepancy.
 * <p>
 * Continuous dimensions typically use equal-width bins represented by their midpoints. Categorical dimensions use
 * their actual categories as states and therefore introduce no approximation.
 */
public interface DiscretizedDiscrepancyKernel {

	/** @return number of finite kernel states */
	int stateCount();

	/** @return state containing the coordinate */
	int state(double value);

	/** @return representative unit-interval coordinate for the state */
	double representativeValue(int state);

	/** @return similarity between two states */
	double value(int state1, int state2);

	/** @return average similarity between this state and a state drawn from the discretized ideal target */
	double targetMean(int state);

	/** @return average similarity between two independent states drawn from the discretized ideal target */
	double targetGrandMean();

	/** @return average self-similarity of a state drawn from the discretized ideal target */
	double targetDiagonalMean();
}
