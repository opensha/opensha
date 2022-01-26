package org.opensha.sha.earthquake.faultSysSolution.inversion.sa;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.time.StopWatch;
import org.opensha.commons.data.IntegerSampler;
import org.opensha.commons.util.DataUtils;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.ProgressTrackingCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.CoolingScheduleType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.GenerationFunctionType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.params.NonnegativityConstraintType;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

/**
 * 
 * @author Morgan Page and Kevin Milner
 *
 */
public class SerialSimulatedAnnealing implements SimulatedAnnealing {

	protected static final String XML_METADATA_NAME = "SimulatedAnnealing";

	protected final static boolean D = false;  // for debugging
	
	// if true, energy change will be calculated during misfit calculation rather than recalculating the full energy
	// this can lead to tiny floating point drift errors relative to recalculating each time, but it's not clear that
	// it's actually less accurate. In fact, the full energy calculation will likely ignore any tiny energy changes.
	private final static boolean ENERGY_SHORTCUT = true;
	
	// do a full energy recalculation every X iterations in order to correct any floating error accumulation
	// a setting of 100k leads to maximum fractional error of ~1e-14
	private final static long ENERGY_SHORTCUT_DRIFT_MOD = 100000l;
	
	// if true, an alternative energy calculator is used that unrolls the inner loop for additional efficiency.
	// this technique allows the JVM to vectorize the calculations, sometimes performing multiple per clock cycle
	private final static boolean UNROLL_ENERGY_CALCS = true;
	
	private final static boolean ENERGY_SHORTCUT_DEBUG = false;
	private final static boolean COLUMN_MULT_SPEEDUP_DEBUG = false;
	private final static boolean XBEST_ACCURACY_CHECK = false;
	private double[] xbest_check_storage;

	private static CoolingScheduleType COOLING_FUNC_DEFAULT = CoolingScheduleType.FAST_SA;
	private CoolingScheduleType coolingFunc = COOLING_FUNC_DEFAULT;
	private static double coolingFuncSlowdown = 1; // Increase this to slow down annealing process (allowing for more time at high temp)
	
	private static NonnegativityConstraintType NONNEGATIVITY_CONST_DEFAULT =
		NonnegativityConstraintType.LIMIT_ZERO_RATES;
	private NonnegativityConstraintType nonnegativityConstraintAlgorithm = NONNEGATIVITY_CONST_DEFAULT;
	
	private static GenerationFunctionType PERTURB_FUNC_DEFAULT = GenerationFunctionType.UNIFORM_0p0001;
	private GenerationFunctionType perturbationFunc = PERTURB_FUNC_DEFAULT;
	
	// sampler that is used to pick random variables for perturbation. default implementation is uniform sampling,
	// but there are alternatives for non-uniform sampling as well
	private IntegerSampler rupSampler = null;
	
	/*
	 * This effectively makes changes in energies smaller (increasing the prob a jump will be taken to higher E).
	 * Increase to take more jumps early in annealing
	 */
	private double energyScaleFactor = 1;

	/**
	 * If true, the current model will always be kept as the best model instead of the best model seen. This allows
	 * the SA algorithm to avoid local minimums in threaded mode where only the "best" solution is passed between threads.
	 */
	private boolean keepCurrentAsBest = false;
	
	private double[] variablePerturbBasis;
	
	/*
	 * Column organized input data
	 */
	private ColumnOrganizedAnnealingData equalityData;
	private ColumnOrganizedAnnealingData inequalityData;
	
	/*
	 * Other non-equality options
	 */
	private double relativeSmoothnessWt;
	private boolean hasInequalityConstraint;
	
	private double[] xbest;  // best model seen so far
	private double[] misfit_best, misfit_ineq_best; // misfit between data and synthetics
	private int numNonZero; // number of nonzero values in xbest
	
	private double[] Ebest; // [total, from A, from entropy, from A_ineq]

	private List<ConstraintRange> constraintRanges;
	
	private Random r = new Random();

	private double[] initialState;

	/**
	 * @param A A matrix, likely an instance of SparseDoubleMatrix2D
	 * @param d d array
	 * @param initialState initial state
	 */
	public SerialSimulatedAnnealing(DoubleMatrix2D A, double[] d, double[] initialState) {
		this(A, d, initialState, 0, null, null);
	}
	
	/**
	 * 
	 * @param A A matrix, likely an instance of SparseDoubleMatrix2D
	 * @param d d array
	 * @param initialState initial state
	 * @param relativeSmoothnessWt relative weight for smoothness (entropy) constraint, or zero to disable
	 * @param A_ineq A matrix for inequality constraints
	 * @param d_ineq d array for inequality constraints
	 */
	public SerialSimulatedAnnealing(DoubleMatrix2D A, double[] d, double[] initialState, double relativeSmoothnessWt, 
			DoubleMatrix2D A_ineq,  double[] d_ineq) {
		this(new ColumnOrganizedAnnealingData(A, d), A_ineq == null ? null : new ColumnOrganizedAnnealingData(A_ineq, d_ineq),
				initialState, relativeSmoothnessWt);
	}
	
	/**
	 * 
	 * @param equalityData equality constraint data
	 * @param inequalityData inequality constraint data, can be null
	 * @param initialState initial state
	 * @param relativeSmoothnessWt relative weight for smoothness (entropy) constraint, or zero to disable
	 */
	public SerialSimulatedAnnealing(ColumnOrganizedAnnealingData equalityData,
			ColumnOrganizedAnnealingData inequalityData, double[] initialState, double relativeSmoothnessWt) {
		this.relativeSmoothnessWt=relativeSmoothnessWt;
		setup(equalityData, inequalityData, initialState);
	}
	
	private void setup(ColumnOrganizedAnnealingData equalityData,
			ColumnOrganizedAnnealingData inequalityData, double[] initialState) {
		Preconditions.checkNotNull(equalityData, "Equality data cannot be null");
		this.equalityData = equalityData;
		this.inequalityData = inequalityData;
		this.hasInequalityConstraint = inequalityData != null;
		Preconditions.checkNotNull(initialState, "initial state cannot be null");
		Preconditions.checkArgument(initialState.length == equalityData.nCols,
				"initial state must be same lenth as nCol of A");
		if (inequalityData != null)
			Preconditions.checkArgument(initialState.length == inequalityData.nCols,
					"initial state must be same lenth as nCol of A_ineq");
		this.initialState = initialState;
		
		xbest = Arrays.copyOf(initialState, initialState.length);  // best model seen so far
		for (int i=0; i<xbest.length; i++) {
			Preconditions.checkState(xbest[i] >= 0, "initial solution has negative or NaN value: %s", xbest[i]);
			if (xbest[i] > 0)
				numNonZero++;
		}
		
		// initial misfits
		misfit_best = new double[equalityData.nRows];
		calculateMisfit(equalityData, xbest, misfit_best);
		if (hasInequalityConstraint) {
			misfit_ineq_best = new double[inequalityData.nRows];
			calculateMisfit(inequalityData, xbest, misfit_ineq_best);
		}
		
		Ebest = calculateEnergy(xbest, misfit_best, misfit_ineq_best);
		
		rupSampler = new IntegerSampler.ContiguousIntegerSampler(initialState.length);
	}
	
	/**
	 * Sets the random number generator used - helpful for reproducing results for testing purposes
	 * @param r
	 */
	public void setRandom(Random r) {
		this.r = r;
	}
	
	@Override
	public void setCalculationParams(CoolingScheduleType coolingFunc,
			NonnegativityConstraintType nonnegativeityConstraintAlgorithm,
			GenerationFunctionType perturbationFunc) {
		this.coolingFunc = coolingFunc;
		this.nonnegativityConstraintAlgorithm = nonnegativeityConstraintAlgorithm;
		this.perturbationFunc = perturbationFunc;
	}
	
	@Override
	public CoolingScheduleType getCoolingFunc() {
		return coolingFunc;
	}

	@Override
	public void setCoolingFunc(CoolingScheduleType coolingFunc) {
		this.coolingFunc = coolingFunc;
	}

	@Override
	public NonnegativityConstraintType getNonnegativeityConstraintAlgorithm() {
		return nonnegativityConstraintAlgorithm;
	}

	@Override
	public void setNonnegativeityConstraintAlgorithm(
			NonnegativityConstraintType nonnegativeityConstraintAlgorithm) {
		this.nonnegativityConstraintAlgorithm = nonnegativeityConstraintAlgorithm;
	}

	@Override
	public GenerationFunctionType getPerturbationFunc() {
		return perturbationFunc;
	}

	@Override
	public void setPerturbationFunc(GenerationFunctionType perturbationFunc) {
		this.perturbationFunc = perturbationFunc;
	}
	
	@Override
	public void setRuptureSampler(IntegerSampler rupSampler) {
		this.rupSampler = rupSampler;
	}
	
	public void setVariablePerturbationBasis(double[] variablePerturbBasis) {
		Preconditions.checkArgument(variablePerturbBasis == null
				|| variablePerturbBasis.length == xbest.length,
				"variablePerturbBasis must be either null of the same length as xbest");
		this.variablePerturbBasis = variablePerturbBasis;
	}

	@Override
	public double[] getBestSolution() {
		return xbest;
	}
	
	@Override
	public int getNumNonZero() {
		return numNonZero;
	}

	@Override
	public double[] getBestEnergy() {
		return Ebest;
	}
	
	@Override
	public double[] getBestMisfit() {
		return misfit_best;
	}

	@Override
	public double[] getBestInequalityMisfit() {
		return misfit_ineq_best;
	}
	
	@Override
	public void setResults(double[] Ebest, double[] xbest, double[] misfit_best, double[] misfit_ineq_best, int numNonZero) {
		this.Ebest = Arrays.copyOf(Ebest, Ebest.length);
		this.xbest = Arrays.copyOf(xbest, xbest.length);
		Preconditions.checkNotNull(misfit_best, "Misfits must be supplied");
		this.misfit_best = Arrays.copyOf(misfit_best, misfit_best.length);
		if (hasInequalityConstraint) {
			Preconditions.checkNotNull(misfit_ineq_best, "Inequality misfits must be supplied");
			this.misfit_ineq_best = Arrays.copyOf(misfit_ineq_best, misfit_ineq_best.length);
		}
		this.numNonZero = numNonZero;
	}
	
	@Override
	public void setResults(double[] Ebest, double[] xbest) {
		int numNonZero = 0;
		for (double x : xbest)
			if (x > 0)
				numNonZero++;
		setResults(Ebest, xbest, null, null, numNonZero);
	}
	
	public void setConstraintRanges(List<ConstraintRange> constraintRanges) {
		this.constraintRanges = constraintRanges;
	}
	
	public List<ConstraintRange> getConstraintRanges() {
		return constraintRanges;
	}
	
	/**
	 * Calculates misfits (synthetics - date) for the given solution. This is slow as it does a complete matrix
	 * multiplication, and is not used in the inner annealing loops.
	 * 
	 * @param data constraint data
	 * @param solution solution
	 * @param misfit array where misfits will be stored
	 */
	public static void calculateMisfit(ColumnOrganizedAnnealingData data, double[] solution, double[] misfit) {
		calculateMisfit(data.A, data.d, solution, misfit);
	}
	
	/**
	 * Calculates misfits (synthetics - date) for the given solution. This is slow as it does a complete matrix
	 * multiplication, and is not used in the inner annealing loops.
	 * 
	 * @param mat A matrix
	 * @param data data
	 * @param solution solution
	 * @param misfit array where misfits will be stored
	 */
	public static void calculateMisfit(DoubleMatrix2D mat, double[] data, double[] solution, double[] misfit) {
		DoubleMatrix1D sol_clone = new DenseDoubleMatrix1D(solution);
		
		DenseDoubleMatrix1D syn = new DenseDoubleMatrix1D(mat.rows());
		mat.zMult(sol_clone, syn);
		
		for (int i = 0; i < mat.rows(); i++) {
			misfit[i] = syn.get(i) - data[i];  // misfit between synthetics and data
		}
	}
	
	/**
	 * This updates misfits in place due to the given perturbation. It returns the final energy change as a reult
	 * of this perturbation
	 * 
	 * @param inputs column-organized data inputs
	 * @param misfits misfits to be updated
	 * @param perturbCol column of the value being perturbed
	 * @param perturbation perturbation
	 * @param ineq true if this is an inequality constraint (affects energy change calculation)
	 * @param scratch scratch array that is at least the size of the number of rows that depend on this column
	 * @return
	 */
	private static double updateMisfitsCalcDeltaEnergy(final ColumnOrganizedAnnealingData inputs, final double[] misfits,
			final int perturbCol, final double perturbation, final boolean ineq, final double[] scratch) {
		// nonzero values in the A matrix related to the perturbed column
		final double[] As = inputs.colA_values[perturbCol];
		// rows assocated with the values above
		final int[] rows = inputs.colRows[perturbCol];
		
		final int NUM = rows.length;
		// make sure that our scratch array is big enough
		Preconditions.checkState(scratch.length >= NUM);
		// fill the scratch array with misfit values before this perturbation
		for (int i=0; i<NUM; i++)
			scratch[i] = misfits[rows[i]];
		
		double prevE;
		if (ineq)
			prevE = sumSquaresIneq(scratch, NUM);
		else if (UNROLL_ENERGY_CALCS)
			prevE = sumSquaresUnrolled(scratch, NUM);
		else
			prevE = sumSquares(scratch, NUM);
		
		// now update the misfits array with the purturbation
		// this also stores the updated misfits in the scratch array
//		if (UNROLL_ENERGY_CALCS)
//			updateMisfitsUnrolled(misfits, perturbation, scratch, As, rows, NUM);
//		else
			updateMisfits(misfits, perturbation, scratch, As, rows, NUM);
		
		// calculate energy change due to this perturbation
		if (ineq)
			return sumSquaresIneq(scratch, NUM) - prevE;
		if (UNROLL_ENERGY_CALCS)
			return sumSquaresUnrolled(scratch, NUM) - prevE;
		return sumSquares(scratch, NUM) - prevE;
	}

	private static void updateMisfits(final double[] misfits, final double perturbation, final double[] scratch,
			final double[] As, final int[] rows, final int NUM) {
		for (int i=0; i<NUM; i++) {
			misfits[rows[i]] = Math.fma(As[i], perturbation, misfits[rows[i]]);
			scratch[i] = misfits[rows[i]];
		}
	}
	
	/**
	 * Simple method to calculate the sum of squared values
	 * 
	 * @param values
	 * @param NUM
	 * @return
	 */
	private static double sumSquares(final double[] values, final int NUM) {
		double ret = 0d;
		for (int i=0; i<NUM; i++)
			// Math.fma does the multiplication and add in a single floating point operation and is
			// both more efficient and precise
			ret = Math.fma(values[i], values[i], ret);
		return ret;
	}
	
	// DO NOT CHANGE this without also updating the code in the method below
	private final static int ROLLS = 10;
	
	/**
	 * More complicated method to calculate the sum of squared values in a way that the JVM will run more efficiently.
	 * <p>
	 * Basically, I 'unroll' each loop to have many separate sums, which allows the JVM to convert it to scalar
	 * operations. Idea from https://stackoverflow.com/a/51429748, also see https://en.wikipedia.org/wiki/Loop_unrolling
	 * <p>
	 * This is about 15% faster than the regular rolled version
	 * 
	 * @param values
	 * @param NUM
	 * @return
	 */
	private static double sumSquaresUnrolled(final double[] values, final int NUM) {
		int ROUNDS = NUM/ROLLS;
		if (ROUNDS < 2)
			return sumSquares(values, NUM);
		
		double s0 = 0d;
		double s1 = 0d;
		double s2 = 0d;
		double s3 = 0d;
		double s4 = 0d;
		double s5 = 0d;
		double s6 = 0d;
		double s7 = 0d;
		double s8 = 0d;
		double s9 = 0d;
		
		int i;
		for (int r=0; r<ROUNDS; r++) {
			i = r*ROLLS;
			s0 = Math.fma(values[i], values[i], s0);
			s1 = Math.fma(values[i+1], values[i+1], s1);
			s2 = Math.fma(values[i+2], values[i+2], s2);
			s3 = Math.fma(values[i+3], values[i+3], s3);
			s4 = Math.fma(values[i+4], values[i+4], s4);
			s5 = Math.fma(values[i+5], values[i+5], s5);
			s6 = Math.fma(values[i+6], values[i+6], s6);
			s7 = Math.fma(values[i+7], values[i+7], s7);
			s8 = Math.fma(values[i+8], values[i+8], s8);
			s9 = Math.fma(values[i+9], values[i+9], s9);
		}
		
		double ret = s0+s1+s2+s3+s4+s5+s6+s7+s8+s9;
		
		for (i=ROUNDS*ROLLS; i<NUM; i++)
			// now fill in any remainder
			ret = Math.fma(values[i], values[i], ret);
		
		return ret;
	}
	
	private static double sumSquaresIneq(final double[] values, final int NUM) {
		double ret = 0d;
		double val;
		for (int i=0; i<NUM; i++) {
			val = values[i];
			if (val > 0)
				ret = Math.fma(val, val, ret);
		}
		return ret;
	}
	
	public double[] calculateEnergy(double[] solution) {
		double[] misfit = new double[equalityData.nRows];
		calculateMisfit(equalityData, solution, misfit);
		double[] misfit_ineq = null;
		if (hasInequalityConstraint) {
			misfit_ineq = new double[inequalityData.nRows];
			calculateMisfit(inequalityData, solution, misfit_ineq);
		}
		return calculateEnergy(solution, misfit, misfit_ineq);
	}
	
	public double[] calculateEnergy(double[] solution, double[] misfit, double[] misfit_ineq, List<ConstraintRange> constraintRanges) {
		int ineqRows = hasInequalityConstraint ? inequalityData.nRows : 0;
		return calculateEnergy(solution, misfit, misfit_ineq, equalityData.nRows, equalityData.nCols,
				ineqRows, constraintRanges, relativeSmoothnessWt);
	}
	
	public double[] calculateEnergy(double[] solution, double[] misfit, double[] misfit_ineq) {
		int ineqRows = hasInequalityConstraint ? inequalityData.nRows : 0;
		return calculateEnergy(solution, misfit, misfit_ineq, equalityData.nRows, equalityData.nCols,
				ineqRows, constraintRanges, relativeSmoothnessWt);
	}
	
	public static double[] calculateEnergy(final double[] solution, final double[] misfit, final double[] misfit_ineq,
			final int nRow, final int nCol, final int ineqRows, final List<ConstraintRange> constraintRanges,
			final double relativeSmoothnessWt) {
		
		// Do forward problem for new perturbed model (calculate synthetics)
		
		double Eequality = 0;
		double[] ret;
		if (constraintRanges == null) {
			ret = new double[4];
			
			Eequality = UNROLL_ENERGY_CALCS ? sumSquaresUnrolled(misfit, nRow) : sumSquares(misfit, nRow);
		} else {
			ret = new double[4+constraintRanges.size()];
			double val;
			for (int i = 0; i < nRow; i++) {
				// NOTE: it is important that we loop over nRow and not the actual misfit array
				// as it may be larger than nRow (for efficiency and fewer array copies)
				
				val = misfit[i]*misfit[i];
				Eequality += val;  // L2 norm of misfit vector
				for (int j=0; j<constraintRanges.size(); j++)
					if (constraintRanges.get(j).contains(i, false))
						ret[j+4] += val;
			}
		}
		ret[1] = Eequality;
		Preconditions.checkState(!Double.isNaN(Eequality), "energy from equality constraints is NaN!");
		
		// Add smoothness constraint misfit (nonlinear) to energy (this is the entropy-maximization constraint)
		double Eentropy = 0;
		if (relativeSmoothnessWt > 0.0) { 
			Eentropy = calcEntropyEnergy(solution, relativeSmoothnessWt);
			ret[2] = Eentropy;
		}
		
		
		// Add MFD inequality constraint misfit (nonlinear) to energy 
		double Einequality = 0;
		if (ineqRows > 0) {
			if (constraintRanges == null) {
				Einequality = sumSquaresIneq(misfit_ineq, ineqRows);
			} else {
				double val;
				for (int i = 0; i < ineqRows; i++) {
					// NOTE: it is important that we loop over nRow and not the actual misfit array
					// as it may be larger than nRow (for efficiency and fewer array copies)
					
					if (misfit_ineq[i] > 0d) {
						val = misfit_ineq[i]*misfit_ineq[i];
						Einequality += val;  // L2 norm of misfit vector
						for (int j=0; j<constraintRanges.size(); j++)
							if (constraintRanges.get(j).contains(i, true))
								ret[j+4] += val;
					}
				}
			}
			Preconditions.checkState(!Double.isNaN(Einequality), "energy from inequality constraints is NaN!");
			ret[3] = Einequality;
		}
		
		double Enew = Eequality + Eentropy + Einequality;
		Preconditions.checkState(!Double.isNaN(Enew), "Enew is NaN!");
		
		ret[0] = Enew;
		return ret;
	}

	public static double calcEntropyEnergy(final double[] solution, final double relativeSmoothnessWt) {
		double Eentropy = 0d;
		double totalEntropy=0;
		double entropyConstant=500;
		for (int rup=0; rup<solution.length; rup++) {
			if (solution[rup]>0)
				totalEntropy -= entropyConstant*solution[rup]*Math.log(entropyConstant*solution[rup]);
		}
		if (totalEntropy==0) {
			System.out.println("ZERO ENTROPY!");
			totalEntropy=0.0001;
		}
		if (totalEntropy<0) {
			throw new IllegalStateException("NEGATIVE ENTROPY!");
		}
		Eentropy += relativeSmoothnessWt * (1 / totalEntropy); // High entropy => low misfit
		Preconditions.checkState(!Double.isNaN(Eentropy), "energy from entropy constraint is NaN!");
		return Eentropy;
	}
	
	@Override
	public synchronized InversionState iterate(long numIterations) {
		return iterate(new IterationCompletionCriteria(numIterations));
	}
	
	@Override
	public synchronized InversionState iterate(CompletionCriteria completion) {
		return iterate(null, completion);
	}

	@Override
	public synchronized InversionState iterate(InversionState startingState, CompletionCriteria criteria) {
		StopWatch watch = new StopWatch();
		watch.start();
		
		boolean rangeTrack = constraintRanges != null && !constraintRanges.isEmpty();
		if (rangeTrack && criteria instanceof ProgressTrackingCompletionCriteria)
			((ProgressTrackingCompletionCriteria)criteria).setConstraintRanges(constraintRanges);
		
		long startIter = startingState == null ? 0 : startingState.iterations;
		long startPerturbs = startingState == null ? 0 : startingState.numPerturbsKept;
		long startWorseKept = startingState == null ? 0 : startingState.numWorseValuesKept;
		
		if (rangeTrack && startIter == 0l && Ebest.length == 4)
			// constraint ranges were set after we called setup before, recalc
			Ebest = calculateEnergy(xbest, misfit_best, misfit_ineq_best);
		
		if(D) System.out.println("Solving inverse problem with simulated annealing ... \n");
		if(D) System.out.println("Cooling Function: " + coolingFunc.name());
		if(D) System.out.println("Perturbation Function: " + perturbationFunc.name());
		if(D) System.out.println("Nonnegativity Constraint: " + nonnegativityConstraintAlgorithm.name());
		if(D) System.out.println("Completion Criteria: " + criteria);
		
		double P;
		long iter=startIter+1;
		long perturbs = startPerturbs;
		long worseKept = startWorseKept;
		int index;
		double[] x = Arrays.copyOf(xbest, xbest.length);
		int curNumNonZero = numNonZero;
		double[] E = Ebest;
		double T;
		double perturb;
		int nCol = xbest.length;
		
		// keep track of 3 misfit arrays simultaneously to avoid expensive array copy operations in the inner loop
		Preconditions.checkNotNull(misfit_best); // best ever encountered
		double[] misfit_working = Arrays.copyOf(misfit_best, misfit_best.length); // current kept model
		double[] misfit_perturbed = Arrays.copyOf(misfit_best, misfit_best.length); // updated each iteration
		
		double[] misfit_working_ineq = null;
		double[] misfit_perturbed_ineq = null;
		if (hasInequalityConstraint) {
			Preconditions.checkNotNull(misfit_ineq_best); // best ever encountered
			misfit_working_ineq = Arrays.copyOf(misfit_ineq_best, misfit_ineq_best.length); // current kept model
			misfit_perturbed_ineq = Arrays.copyOf(misfit_ineq_best, misfit_ineq_best.length); // updated each iteration
		}
		
		// this array will be used in energy change calculations, and needs to be at least as big as the largest number
		// of rows with a non-zero value in the A matrix for any column
		double[] scratch;
		if (hasInequalityConstraint) {
			int maxCount = Integer.max(equalityData.maxRowsPerCol, inequalityData.maxRowsPerCol);
			scratch = new double[maxCount];
		} else {
			scratch = new double[equalityData.maxRowsPerCol];
		}
		
		// this will keep track of 'worse' perturbations that we kept, but have not yet led to a new 'best' solution
		long worseValsNotYetSaved = 0l;
		
		double worstEnergyShortcutFract = 0;
		double worstEnergyShortcutAbs = 0;

		// we do iter-1 because iter here is 1-based, not 0-based
		InversionState state = null;
		while (!criteria.isSatisfied(state = new InversionState(watch.getTime(), iter-1, Ebest, perturbs, worseKept,
				numNonZero, xbest, misfit_best, misfit_ineq_best, constraintRanges))) {

			// Find current simulated annealing "temperature" based on chosen cooling schedule
			double coolIter = iter;
			if (coolingFuncSlowdown != 1)
				coolIter = ((double)iter - 1) / coolingFuncSlowdown + 1;
			switch (coolingFunc) {
			case CLASSICAL_SA:
				T = 1/Math.log(coolIter + 1); // classical SA cooling schedule (Geman and Geman, 1984) (slow but ensures convergence)
				break;
			case FAST_SA:
				T = 1 / coolIter;  // fast SA cooling schedule (Szu and Hartley, 1987) (recommended)
				break;
			case VERYFAST_SA:
				T = Math.exp(-( coolIter - 1d)); // very fast SA cooling schedule (Ingber, 1989)  (= 0 to machine precision for high iteration #)
				break;
			case LINEAR:
//				T = 1 - (coolIter / numIterations);
				T = 1 - (coolIter / 100000);  // need to fix this -- for now just putting in numIterations by hand
				break;
			default:
				throw new IllegalStateException("It's impossible to get here, as long as all cooling schedule enum cases are stated above!");
			}

			if (D) {  // print out convergence info every so often
				if ((iter-1) % 10000 == 0) { 
					System.out.println("Iteration # " + iter);
					System.out.println("Lowest energy found = "
							+Doubles.join(", ", Ebest));
//					System.out.println("Current energy = " + E);
				}
			}

			// Index of model to randomly perturb
			index = rupSampler.getRandomInt(r);

			// How much to perturb index (some perturbation functions are a function of T)	
			perturb = perturbationFunc.getPerturbation(r, T, index, variablePerturbBasis);
			
			boolean wasZero = x[index] == 0;

			// Apply then nonnegativity constraint -- make sure perturbation doesn't make the rate negative
			switch (nonnegativityConstraintAlgorithm) {
			case TRY_ZERO_RATES_OFTEN: // sets rate to zero if they are perturbed to negative values 
				// This way will result in many zeros in the solution, 
				// which may be desirable since global minimum is likely near a boundary
				if (wasZero) { // if that rate was already zero do not keep it at zero
					while (x[index] + perturb < 0) 
						perturb =  perturbationFunc.getPerturbation(r, T, index, variablePerturbBasis);
				} else { // if that rate was not already zero, and it goes negative, set it equal to zero
					if (x[index] + perturb < 0) 
						perturb = -x[index];
				}
				break;
			case LIMIT_ZERO_RATES:    // re-perturb rates if they are perturbed to negative values 
				// This way will result in not a lot of zero rates (none if numIterations >> length(x)),
				// which may be desirable if we don't want a lot of zero rates
				while (x[index] + perturb < 0) {
					perturb =  perturbationFunc.getPerturbation(r, T, index, variablePerturbBasis);
				}
				break;
			case PREVENT_ZERO_RATES:    // Only perturb rates to positive values; any perturbations of zero rates MUST be accepted.
				// Final model will only have zero rates if rate was never selected to be perturbed AND starting model contains zero rates.
				if (!wasZero) {
					perturb = (r.nextDouble() -0.5) * 2 * x[index]; 	
					}
				else {
					perturb = (r.nextDouble()) * 0.00000001;
				}
				break;
			default:
				throw new IllegalStateException("You missed a Nonnegativity Constraint Algorithm type.");
			}
			
			if (ENERGY_SHORTCUT && iter % ENERGY_SHORTCUT_DRIFT_MOD == 0) {
				// recalculate full energy every once in a while to prevent slow accumulation of floating point errors
				// we'll do this based on the original energy (before this perturbation) as the energy delta calculation
				// is actually more accurate than the full one for determining if we should change states
				E = calculateEnergy(x, misfit_working, misfit_working_ineq);
			}
			
			x[index] += perturb;
			
			// calculate new misfit vectors
			// this will simultaneously calculate misfit change
			double deltaE = updateMisfitsCalcDeltaEnergy(equalityData, misfit_perturbed, index,
					perturb, false, scratch);
			double deltaE_ineq = Double.NaN;
			if (hasInequalityConstraint)
				deltaE_ineq = updateMisfitsCalcDeltaEnergy(inequalityData, misfit_perturbed_ineq, index,
						perturb, true, scratch);

			// Calculate "energy" of new model (high misfit -> high energy)
			double energyChange;
			double[] Enew;
			
			if (ENERGY_SHORTCUT) {
				// use the energy shortcut: add change to previous energy
				// calculated change will be more accurate than comparing full energy sums, so we'll use that when
				// calculating the transition probability as well
				energyChange = deltaE;
				Enew = new double[4];
				Enew[0] = E[0] + deltaE;
				Enew[1] = E[1] + deltaE;
				if (hasInequalityConstraint) {
					Preconditions.checkState(Double.isFinite(deltaE_ineq));
					Enew[0] += deltaE_ineq;
					Enew[3] = E[3] + deltaE_ineq;
					energyChange += deltaE_ineq;
				}
				if (relativeSmoothnessWt != 0d) {
					Enew[2] = calcEntropyEnergy(x, relativeSmoothnessWt);
					Enew[0] += Enew[2];
					energyChange += Enew[2]-E[2];
				}
//				if (D) {
//					double[] Etest2 = calculateEnergy(x, misfit_perturbed, misfit_perturbed_ineq);
//					double cDelt = Etest2[0] - E[0];
//					System.out.println("Energy shortcut with deltaE="+deltaE+", calcDelta="+cDelt+", diff="+(float)(deltaE - cDelt));
//				}
				if (ENERGY_SHORTCUT_DEBUG && (iter-1) % 1000 == 0 && iter > 1) {
					// calculate it manually as well
					double[] Etest = calculateEnergy(x, misfit_perturbed, misfit_perturbed_ineq);
					if (D) System.out.println("ENERGY SHORTCUT DEBUG");
					for (int i=0; i<Enew.length; i++) {
						double myDelta = Enew[i]-E[i];
						double calcDelta = Etest[i]-E[i];
						double diff = myDelta-calcDelta;
						double error = Math.abs(Enew[i]-Etest[i]);
						double fractError = error/Etest[i];
						worstEnergyShortcutAbs = Math.max(error, worstEnergyShortcutAbs);
						if (Etest[i] > 0d)
							worstEnergyShortcutFract = Math.max(fractError, worstEnergyShortcutFract);
						if (D) System.out.println("\tEtest["+i+"]="+Etest[i]+"\tEnew["+i+"]="
								+Enew[i]+"\tE["+i+"]="+E[i]+"\tmyDelta="+myDelta+"\tcalcDelta="+calcDelta
								+"\tdiff="+diff+"\tfractError="+fractError);
//						Preconditions.checkState((float)Etest[i] == (float)Enew[i],
						Preconditions.checkState((float)Etest[i] == (float)Enew[i] || fractError < 1e-4 || error < 1e-10,
								"Energy[%s] shortcut failfor iteration %s! %s != %s, oldE=%s, deltaE=%s, deltaE_ineq=%s,"
								+ "\nmyDelta=%s, calcDelta=%s, diff=%s, fractError=%s",
								i, iter, Enew[i], Etest[i], E[i], deltaE, deltaE_ineq, myDelta, calcDelta, diff, fractError);
						// disabling the sign test, as it's not clear that energy changes are actually less accurate
						// they may be more accurate as we're accumulating small value changes separately rather than
						// accumulating them in large (energy) space where some small changes may be ignored if small
						// relative to energy
//						if (i == 0)
//							Preconditions.checkState(Enew[i] > E[i] == Etest[i] > E[i],
//								"Energy[%s] shortcut fail sign test for! prevE=%s, shortcutDelta=%s, trueDelta=%s, "
//										+ "fractError=%s", i, E[i], myDelta, calcDelta, fractError);
					}
				}
			} else {
				// if we're here, we probably have the entropy constraint enabled, which will be slower
				Enew = calculateEnergy(x, misfit_perturbed, misfit_perturbed_ineq);
				energyChange = Enew[0]-E[0];
			}
			
			if (D) {
				if (COLUMN_MULT_SPEEDUP_DEBUG && (iter-1) % 10000 == 0 && iter > 1) {
					// lets make sure that the energy calculation was correct with the column speedup
					// only do this if debug is enabled, and do it every 100 iterations
					
					// calculate it the "slow" way
					double[] comp_misfit_new = new double[misfit_perturbed.length];
					calculateMisfit(equalityData, x, comp_misfit_new);
					double[] comp_misfit_ineq_new = null;
					if (hasInequalityConstraint) {
						comp_misfit_ineq_new = new double[misfit_perturbed.length];
						calculateMisfit(inequalityData, x, comp_misfit_ineq_new);
					}
					double[] Enew_temp = calculateEnergy(x, comp_misfit_new, comp_misfit_ineq_new);
					double pDiff = DataUtils.getPercentDiff(Enew[0], Enew_temp[0]);
					System.out.println("Pdiff: "+(float)pDiff+" %");
					double pDiffThreshold = 0.0001;
					Preconditions.checkState(pDiff < pDiffThreshold,
							iter+". they don't match within "+pDiffThreshold+"%! "
							+Enew+" != "+Enew_temp+" ("+(float)pDiff+" %)");
				}
			}

			// Change state? Calculate transition probability P
			switch (nonnegativityConstraintAlgorithm) {
			case PREVENT_ZERO_RATES:  
				if (energyChange < 0d || x[index]==0) {
					P = 1; // Always keep new model if better OR if element was originally zero
				} else {
					// Sometimes keep new model if worse (depends on T)
					P = Math.exp(((-energyChange)*energyScaleFactor) / (double) T); 
				}
			break;
			default:
				if (energyChange < 0d) {
					P = 1; // Always keep new model if better
				} else {
					// Sometimes keep new model if worse (depends on T)
					P = Math.exp(((-energyChange)*energyScaleFactor) / (double) T); 
				}
			}
			
			int newNonZero = curNumNonZero;
			if (wasZero) {
				if (x[index] != 0)
					newNonZero++;
			} else {
				if (x[index] == 0)
					newNonZero--;
			}
			
			// Use transition probability to determine (via random number draw) if solution is kept
			if (P == 1 || P > r.nextDouble()) {
				if (energyChange > 0d)
					// we're keeping one that made energy worse
					worseValsNotYetSaved++;
				
				/* 
				 * The buffers are a bit confusing, let me explain. Arrays.copyOf(...) calls are costly in this inner
				 * loop, so we avoid them by reusing 3 misfit buffers. With 3 buffers, there's always one availbe to
				 * store the current best misfit, the current solution misfit, and the misfit to be perturbed in the
				 * next round, without ever doing an array copy.
				 */
				E = Enew;
				// keep these misfits
				for (int row : equalityData.colRows[index])
					misfit_working[row] = misfit_perturbed[row];
				if (hasInequalityConstraint)
					for (int row : inequalityData.colRows[index])
						misfit_working_ineq[row] = misfit_perturbed_ineq[row];
				perturbs++;
				curNumNonZero = newNonZero;
				
				// Is this a new best?
				if (Enew[0] < Ebest[0] || keepCurrentAsBest) {
					// update xbest with this perturbation
					// we now keep xbest isolated so we never have to do an array copy
					xbest[index] = x[index];
					if (XBEST_ACCURACY_CHECK) xbest_check_storage = Arrays.copyOf(x, x.length);
					// keep these misfits permanently
					for (int row : equalityData.colRows[index])
						misfit_best[row] = misfit_perturbed[row];
					if (hasInequalityConstraint)
						for (int row : inequalityData.colRows[index])
							misfit_ineq_best[row] = misfit_perturbed_ineq[row];
					
					if (constraintRanges != null && !constraintRanges.isEmpty()) {
						// we need to recalculate energy the old fashioned way in order to update constraint-specific
						// energy, this will be slower
						Enew = calculateEnergy(x, misfit_perturbed, misfit_perturbed_ineq);
					}
					
					Ebest = Enew;
					numNonZero = curNumNonZero;
					worseKept += worseValsNotYetSaved;
					worseValsNotYetSaved = 0;
					
				}
			} else {
				// undo the perturbation
				x[index] -= perturb;
				for (int row : equalityData.colRows[index])
					misfit_perturbed[row] = misfit_working[row];
				if (hasInequalityConstraint)
					for (int row : inequalityData.colRows[index])
						misfit_perturbed_ineq[row] = misfit_working_ineq[row];
			}
			
			if (D) {
				if (XBEST_ACCURACY_CHECK && (iter-1) % 10000 == 0 && iter > 1 && xbest_check_storage != null) {
					double pDiffThreshold = 0.0001;
					
					// lets make sure that we've stored xbest correctly
					for (int i=0; i<xbest_check_storage.length; i++) {
						double pDiff = DataUtils.getPercentDiff(xbest[i], xbest_check_storage[i]);
						Preconditions.checkState(pDiff < pDiffThreshold,
								"(iter "+iter+") xbest is incorrect at index "+i
								+": "+xbest_check_storage[i]+" != "+xbest[i]+" ("+(float)pDiff+" %)");
					}
					
					
					// lets make sure that the energy calculation was correct with the column speedup
					// only do this if debug is enabled, and do it every 100 iterations
					
					// calculate it the "slow" way
					double[] comp_misfit_new = new double[misfit_working.length];
					calculateMisfit(equalityData, xbest, comp_misfit_new);
					double[] comp_misfit_ineq_new = null;
					if (hasInequalityConstraint) {
						comp_misfit_ineq_new = new double[misfit_working.length];
						calculateMisfit(inequalityData, xbest, comp_misfit_ineq_new);
					}
					double[] Ebest_temp = calculateEnergy(xbest, comp_misfit_new, comp_misfit_ineq_new);
					double pDiff = DataUtils.getPercentDiff(Ebest[0], Ebest_temp[0]);
					System.out.println("Ebest Pdiff: "+(float)pDiff+" %");
					
					Preconditions.checkState(pDiff < pDiffThreshold,
							iter+". ebest: they don't match within "+pDiffThreshold
							+"%! "+Ebest+" != "+Ebest_temp+" ("+(float)pDiff+" %)");
				}
			}
			
			iter++;
		}
		
		watch.stop();
		
		if (ENERGY_SHORTCUT) {
			// do it the slow way for the final energy
			// this will reduce slow drift in threaded applications
			Ebest = calculateEnergy(xbest, misfit_best, misfit_ineq_best);
			
			if (ENERGY_SHORTCUT_DEBUG)
				System.out.println("Worst energy shortcut misfit: abs="+worstEnergyShortcutAbs+"\tfract="+worstEnergyShortcutFract);
		}
		
		// Preferred model is best model seen during annealing process
		if(D) {
			System.out.println("Annealing schedule completed. Ebest = "+Doubles.join(", ", Ebest));
			double runSecs = watch.getTime() / 1000d;
			System.out.println("Done with Inversion after " + runSecs + " seconds.");
		}
		
		return state;
	}
	
	private static String enumOptionsStr(Enum<?>[] values) {
		String str = null;
		
		for (Enum<?> e : values) {
			if (str == null)
				str = "";
			else
				str += ",";
			str += e.name();
		}
		
		return str;
	}
	
	protected static Options createOptions() {
		Options ops = new Options();
		
		Option coolingOption = new Option("cool", "cooling-schedule", true,
				"Cooling schedule. One of: "+enumOptionsStr(CoolingScheduleType.values())
				+". Default: "+COOLING_FUNC_DEFAULT);
		coolingOption.setRequired(false);
		ops.addOption(coolingOption);
		
		Option perturbOption = new Option("perturb", "perturbation-function", true,
				"Cooling schedule. One of: "+enumOptionsStr(GenerationFunctionType.values())
				+". Default: "+PERTURB_FUNC_DEFAULT);
		perturbOption.setRequired(false);
		ops.addOption(perturbOption);
		
		Option nonNegOption = new Option("nonneg", "nonnegativity-const", true,
				"Nonnegativity constraint. One of: "+enumOptionsStr(NonnegativityConstraintType.values())
				+". Default: "+NONNEGATIVITY_CONST_DEFAULT);
		nonNegOption.setRequired(false);
		ops.addOption(nonNegOption);
		
		Option curAsBestOption = new Option("curbest", "cur-as-best", false,
				"Flag for keeping current solution as best, even if it's not the best seen.");
		curAsBestOption.setRequired(false);
		ops.addOption(curAsBestOption);
		
		Option slowerOption = new Option("slow", "slower-cooling", true,
				"If supplied, the iteration count seen by the cooling function will be divided by the given amount");
		slowerOption.setRequired(false);
		ops.addOption(slowerOption);
		
		Option energyScaleOption = new Option("energyscale", "energy-scale", true, "If supplied, this effectively" +
				" makes changes in energies smaller (increasing the prob a jump will be taken to higher E). " +
				"Increase to take more jumps early in annealing");
		energyScaleOption.setRequired(false);
		ops.addOption(energyScaleOption);
		
		return ops;
	}
	
	protected void setCalculationParamsFromOptions(CommandLine cmd) {
		if (cmd.hasOption("cool")) {
			coolingFunc = CoolingScheduleType.valueOf(cmd.getOptionValue("cool"));
		}
		
		if (cmd.hasOption("slow"))
			coolingFuncSlowdown = Double.parseDouble(cmd.getOptionValue("slow"));
		
		if (cmd.hasOption("perturb")) {
			perturbationFunc = GenerationFunctionType.valueOf(cmd.getOptionValue("perturb"));
		}
		
		if (cmd.hasOption("nonneg")) {
			nonnegativityConstraintAlgorithm = NonnegativityConstraintType.valueOf(cmd.getOptionValue("nonneg"));
		}
		
		if (cmd.hasOption("curbest")) {
			keepCurrentAsBest = true;
		}
		if (cmd.hasOption("energyscale"))
			energyScaleFactor = Double.parseDouble(cmd.getOptionValue("energyscale"));
	}

	@Override
	public double[] getInitialSolution() {
		return initialState;
	}

	@Override
	public ColumnOrganizedAnnealingData getEqualityData() {
		return equalityData;
	}

	@Override
	public DoubleMatrix2D getA() {
		return equalityData.A;
	}

	@Override
	public double[] getD() {
		return equalityData.d;
	}

	@Override
	public ColumnOrganizedAnnealingData getInequalityData() {
		return inequalityData;
	}

	@Override
	public DoubleMatrix2D getA_ineq() {
		return inequalityData == null ? null : inequalityData.A;
	}

	@Override
	public double[] getD_ineq() {
		return inequalityData == null ? null : inequalityData.d;
	}

	@Override
	public void setInputs(ColumnOrganizedAnnealingData equalityData, ColumnOrganizedAnnealingData inequalityData) {
		setup(equalityData, inequalityData, xbest);
	}

	@Override
	public void setAll(ColumnOrganizedAnnealingData equalityData, ColumnOrganizedAnnealingData inequalityData,
			double[] Ebest, double[] xbest, double[] misfit, double[] misfit_ineq, int numNonZero) {
		this.equalityData = equalityData;
		this.inequalityData = inequalityData;
		setResults(Ebest, xbest, misfit, misfit_ineq, numNonZero);
	}
	
//	public static void main(String[] args) {
//		int rups = 1;
//		int rows = 1;
//		double[] initial = new double[rups];
//		DoubleMatrix2D A = new DenseDoubleMatrix2D(rows, rups);
////		SparseCCDoubleMatrix2D A = new SparseCCDoubleMatrix2D(rows, rups);
//		for (int row=0; row<rows; row++)
//			for (int col=0; col<rups; col++)
//				A.set(row, col, 1d);
//		double[] d = new double[rows];
//		for (int row=0; row<rows; row++)
//			d[row] = 100;
//		
//		SerialSimulatedAnnealing sa = new SerialSimulatedAnnealing(A, d, initial);
//		
//		sa.setRandom(new Random(1234l));
//		sa.setPerturbationFunc(GenerationFunctionType.FIXED_DEBUG);
//		
//		InversionState state = sa.iterate(1000);
//		
//		System.out.println("Iters: "+state.iterations+"\tperturbs: "+state.numPerturbsKept+"\tworse: "+state.numWorseValuesKept);
//		System.out.println("Energy: "+state.energy[0]);
//		
//		double[] sol = sa.getBestSolution();
//		System.out.println(sol[0]);
//	}

}