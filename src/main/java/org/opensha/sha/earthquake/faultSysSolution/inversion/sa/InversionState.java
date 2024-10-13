package org.opensha.sha.earthquake.faultSysSolution.inversion.sa;

import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;

/**
 * Class that tracks the state of an inversion. Primarily used by {@link CompletionCriteria} to determine if an
 * inversion is finished, and for tracking progress.
 * 
 * @author kevin
 *
 */
public class InversionState {
	
	/**
	 * elapsed total time in milliseconds
	 */
	public final long elapsedTimeMillis;
	/**
	 * total number of iterations completed
	 */
	public final long iterations;
	/**
	 * best energy, with total energy stored in the first array position
	 */
	public final double[] energy;
	/**
	 * the total number of accepted perturbations
	 */
	public final long numPerturbsKept;
	/**
	 * the total number of worse values kept
	 */
	public final long numWorseValuesKept;
	/**
	 * the number of non-zero values in the solution
	 */
	public final int numNonZero;
	/**
	 * the best solution found so far
	 */
	public final double[] bestSolution;
	/**
	 * current data misfits
	 */
	public final double[] misfits;
	/**
	 * current misfits for any inequality constraints
	 */
	public final double[] misfits_ineq;
	/**
	 * constraint ranges for interpreting misfits, if available
	 */
	public final List<ConstraintRange> constraintRanges;
	
	public InversionState(long elapsedTimeMillis, long iterations, double[] energy, long numPerturbsKept,
			long numWorseValuesKept, int numNonZero, double[] bestSolution, double[] misfits, double[] misfits_ineq,
			List<ConstraintRange> constraintRanges) {
		super();
		this.elapsedTimeMillis = elapsedTimeMillis;
		this.iterations = iterations;
		this.energy = energy;
		this.numPerturbsKept = numPerturbsKept;
		this.numWorseValuesKept = numWorseValuesKept;
		this.numNonZero = numNonZero;
		this.bestSolution = bestSolution;
		this.misfits = misfits;
		this.misfits_ineq = misfits_ineq;
		this.constraintRanges = constraintRanges;
	}
	
	@Override
	public String toString() {
		return "InversionState["+elapsedTimeMillis+" ms; "+iterations+" iters; E[0]="+(float)energy[0]
				+"; "+numPerturbsKept+" kept; "+numWorseValuesKept+" worse kept; "+numNonZero+" >0]";
	}
}