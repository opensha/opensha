package org.opensha.sha.earthquake.faultSysSolution.inversion.sa;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.time.StopWatch;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
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
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.SparseCCDoubleMatrix2D;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

import edu.emory.mathcs.csparsej.tdouble.Dcs_common.Dcs;

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
	public final static boolean ENERGY_SHORTCUT = true;
	
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
	
	// this provides and alternative way of random sampling ruptures to perturb (i.e., for a non-uniform districtuion)
	private IntegerPDF_FunctionSampler rupSampler = null;
	
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
	
	private DoubleMatrix2D A, A_ineq;
	private double[] d, d_ineq;
	private double relativeSmoothnessWt;
	private boolean hasInequalityConstraint;
	
	private int nCol;
	private int nRow;
	
	private double[] xbest;  // best model seen so far
	private double[] perturb; // perturbation to current model
	private double[] misfit_best, misfit_ineq_best; // misfit between data and synthetics
	private int numNonZero;
	
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
		this.relativeSmoothnessWt=relativeSmoothnessWt;
		this.hasInequalityConstraint = A_ineq != null;
		if (hasInequalityConstraint)
			Preconditions.checkArgument(d_ineq != null, "we have an A_ineq matrix but no d_ineq vector!");
		else
			Preconditions.checkArgument(d_ineq == null, "we have a d_ineq vector but no A_ineq matrix!");
		this.A_ineq=A_ineq;
		this.d_ineq=d_ineq;
		
		setup(A, d, initialState);
	}
	
	private void setup(DoubleMatrix2D A, double[] d, double[] initialState) {
		this.initialState = initialState;
		Preconditions.checkNotNull(A, "A matrix cannot be null");
		Preconditions.checkNotNull(d, "d matrix cannot be null");
		Preconditions.checkNotNull(initialState, "initial state cannot be null");
		
		nRow = A.rows();
		nCol = A.columns();
		Preconditions.checkArgument(nRow > 0, "nRow of A must be > 0");
		Preconditions.checkArgument(nCol > 0, "nCol of A must be > 0");
		
		Preconditions.checkArgument(d.length == nRow, "d matrix must be same lenth as nRow of A");
		Preconditions.checkArgument(initialState.length == nCol, "initial state must be same lenth as nCol of A");
		
		if (!(A instanceof SparseCCDoubleMatrix2D) && !(A instanceof DenseDoubleMatrix2D))
			System.err.println("WARNING: A matrix is not column-compressed, annealing will be SLOW!");
		
		this.A = A;
		this.d = d;
		

		xbest = Arrays.copyOf(initialState, nCol);  // best model seen so far
		for (int i=0; i<xbest.length; i++) {
			Preconditions.checkState(xbest[i] >= 0, "initial solution has negative or NaN value: %s", xbest[i]);
			if (xbest[i] > 0)
				numNonZero++;
		}
		perturb = new double[nCol]; // perturbation to current model
		
		misfit_best = new double[nRow];
		calculateMisfit(A, d, xbest, misfit_best);
		if (hasInequalityConstraint) {
			misfit_ineq_best = new double[d_ineq.length];
			calculateMisfit(A_ineq, d_ineq, xbest, misfit_ineq_best);
		}
		
		Ebest = calculateEnergy(xbest, misfit_best, misfit_ineq_best);
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
	public void setRuptureSampler(IntegerPDF_FunctionSampler rupSampler) {
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
		if (misfit_best == null)
			this.misfit_best = null;
		else
			this.misfit_best = Arrays.copyOf(misfit_best, misfit_best.length);
		if (misfit_ineq_best == null)
			this.misfit_ineq_best = null;
		else
			this.misfit_ineq_best = Arrays.copyOf(misfit_ineq_best, misfit_ineq_best.length);
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
	 * Calculates misfits (synthetics - date) for the given solution
	 * 
	 * @param mat A matrix
	 * @param data data
	 * @param solution solution
	 * @param misfit array where misfits will be stored
	 */
	public static void calculateMisfit(DoubleMatrix2D mat, double[] data, double[] solution, double[] misfit) {
		calculateMisfit(mat, data, null, solution, -1, Double.NaN, misfit, false);
	}
	
	/**
	 * Calculates misfits (synthetics - date) for the given solution, with optional efficiencies
	 * 
	 * @param mat A matrix
	 * @param data data
	 * @param prev_misfit previous misfits
	 * @param solution solution
	 * @param perturbCol column in the A matrix where the corresoponding solution value was perturbed
	 * @param perturbation perturbation amount
	 * @param misfit array where misfits will be stored
	 * @param ineq true if an inequality constraint
	 * @return if {@link SerialSimulatedAnnealing#ENERGY_SHORTCUT} is enabled, energy change due to this perturbation
	 * will be returned, else NaN
	 */
	private static double calculateMisfit(DoubleMatrix2D mat, double[] data, double[] prev_misfit,
			double[] solution, int perturbCol, double perturbation, double[] misfit, boolean ineq) {
		if (mat instanceof SparseCCDoubleMatrix2D && perturbCol >= 0 && prev_misfit != null) {
			// we have previous misfits and a column-compressed A matrix, don't redo the whole matrix multiplication,
			// only update misfut changes due to the perturbed column
			
			if (prev_misfit != misfit)
				// copy over previous misfits to output, which we will update
				System.arraycopy(prev_misfit, 0, misfit, 0, prev_misfit.length);
			
			Dcs dcs = ((SparseCCDoubleMatrix2D)mat).elements();
			// for each non-zero value, gives the row index
			final int[] rowIndexesA = dcs.i;
			// tells us where the rows associated with this column are in the above array
			final int[] columnPointersA = dcs.p;
			// values array
			final double[] valuesA = dcs.x;
			
			int low = columnPointersA[perturbCol];
			
			// if ENERGY_SHORTCUT is true, we'll calculate energy change which can be used instead of recalculating
			// full energy
			double deltaE = ENERGY_SHORTCUT ? 0d : Double.NaN;
			
			int row;
			double value;
			double prevRowMisfit;
			if (ineq) {
				for (int k = columnPointersA[perturbCol + 1]; --k >= low;) {
					row = rowIndexesA[k];
					value = valuesA[k];
					// store it separately as misfit and prev_misfit can be the same array
					prevRowMisfit = prev_misfit[row];
					misfit[row] += value * perturbation;
					if (ENERGY_SHORTCUT) {
						if (prevRowMisfit > 0)
							// we were previously >0, thus we need to remove the old energy
							deltaE -= prevRowMisfit*prevRowMisfit;
						if (misfit[row] > 0)
							// we're now >0, add new energy
							deltaE += misfit[row]*misfit[row];
					}
				}
			} else {
				for (int k = columnPointersA[perturbCol + 1]; --k >= low;) {
					row = rowIndexesA[k];
					value = valuesA[k];
					// store it separately as misfit and prev_misfit can be the same array
					prevRowMisfit = prev_misfit[row];
					misfit[row] += value * perturbation;
					if (ENERGY_SHORTCUT)
						// calculate energy difference due to this perturbation
						deltaE += misfit[row]*misfit[row] - prevRowMisfit*prevRowMisfit;
				}
			}
//			System.out.println("deltaE = "+deltaE);
			return deltaE;
		} else {
			DoubleMatrix1D sol_clone = new DenseDoubleMatrix1D(solution);
			
			DenseDoubleMatrix1D syn = new DenseDoubleMatrix1D(mat.rows());
			mat.zMult(sol_clone, syn);
			
			for (int i = 0; i < mat.rows(); i++) {
				misfit[i] = syn.get(i) - data[i];  // misfit between synthetics and data
			}
			
			return Double.NaN;
		}
	}
	
	public double[] calculateEnergy(double[] solution) {
		double[] misfit = new double[d.length];
		calculateMisfit(A, d, solution, misfit);
		double[] misfit_ineq = null;
		if (hasInequalityConstraint) {
			misfit_ineq = new double[d_ineq.length];
			calculateMisfit(A_ineq, d_ineq, solution, misfit_ineq);
		}
		return calculateEnergy(solution, misfit, misfit_ineq);
	}
	
	public double[] calculateEnergy(double[] solution, double[] misfit, double[] misfit_ineq, List<ConstraintRange> constraintRanges) {
		int ineqRows = hasInequalityConstraint ? d_ineq.length : 0;
		return calculateEnergy(solution, misfit, misfit_ineq, nRow, nCol, ineqRows, constraintRanges, relativeSmoothnessWt);
	}
	
	public double[] calculateEnergy(double[] solution, double[] misfit, double[] misfit_ineq) {
		int ineqRows = hasInequalityConstraint ? d_ineq.length : 0;
		return calculateEnergy(solution, misfit, misfit_ineq, nRow, nCol, ineqRows, constraintRanges, relativeSmoothnessWt);
	}
	
	public static double[] calculateEnergy(final double[] solution, final double[] misfit, final double[] misfit_ineq,
			final int nRow, final int nCol, final int ineqRows, final List<ConstraintRange> constraintRanges,
			final double relativeSmoothnessWt) {
		
		// Do forward problem for new perturbed model (calculate synthetics)
		
		double Eequality = 0;
		double[] ret;
		if (constraintRanges == null) {
			ret = new double[4];
			
			for (int i = 0; i < nRow; i++) {
				// NOTE: it is important that we loop over nRow and not the actual misfit array
				// as it may be larger than nRow (for efficiency and fewer array copies)
				
				Eequality += misfit[i]*misfit[i];  // L2 norm of misfit vector
			}
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
			double totalEntropy=0;
			double entropyConstant=500;
			for (int rup=0; rup<nCol; rup++) {
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
			ret[2] = Eentropy;
		}
		
		
		// Add MFD inequality constraint misfit (nonlinear) to energy 
		double Einequality = 0;
		if (ineqRows > 0) {
			if (constraintRanges == null) {
				for (int i = 0; i < ineqRows; i++) {
					// NOTE: it is important that we loop over nRow and not the actual misfit array
					// as it may be larger than nRow (for efficiency and fewer array copies)
					
					if (misfit_ineq[i] > 0d)
						Einequality += misfit_ineq[i]*misfit_ineq[i];  // L2 norm of misfit vector
				}
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
	
	@Override
	public synchronized InversionState iterate(long numIterations) {
		return iterate(new IterationCompletionCriteria(numIterations));
	}
	
	@Override
	public synchronized InversionState iterate(CompletionCriteria completion) {
		return iterate(null, completion);
	}
	
	private static final int num_buffers = 3;

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
		// this is where we store previous misfits
		double[] misfit;
		if (misfit_best == null) {
			misfit = null;
		} else {
			misfit = Arrays.copyOf(misfit_best, misfit_best.length);
		}
		// this is where we store new candidate misfits
		int cur_buffer = 0;
		double[][] misfit_buffers = new double[num_buffers][nRow];
		double[] misfit_cur_purtub = misfit_buffers[cur_buffer];
		double[] misfit_ineq = null;
		double[][] misfit_ineq_buffers = null;
		double[] misfit_ineq_cur_purtub = null;
		if (hasInequalityConstraint) {
			if (misfit_ineq_best != null) {
				misfit_ineq = Arrays.copyOf(misfit_ineq_best, misfit_ineq_best.length);
			}
			misfit_ineq_buffers = new double[num_buffers][nRow];
			misfit_ineq_cur_purtub = misfit_ineq_buffers[cur_buffer];
		}
		
		long worseValsNotYetSaved = 0l;

		// we do iter-1 because iter here is 1-based, not 0-based
		InversionState state = null;
//		while (!criteria.isSatisfied(watch, iter-1, Ebest, perturbs, numNonZero, misfit_best, misfit_ineq_best, constraintRanges)) {
		while (!criteria.isSatisfied(state = new InversionState(watch.getTime(), iter-1, Ebest, perturbs, worseKept,
				numNonZero, xbest, misfit_best, misfit_ineq_best, constraintRanges))) {

			// Find current simulated annealing "temperature" based on chosen cooling schedule
//			double coolIter = (double)iter / coolingFuncSlowdown;
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
			if(rupSampler == null)
				index = (int)(r.nextDouble() * (double)nCol); // casting as int takes the floor
			else
				index = rupSampler.getRandomInt(r.nextDouble());


			// How much to perturb index (some perturbation functions are a function of T)	
			perturb[index] = perturbationFunc.getPerturbation(r, T, index, variablePerturbBasis);
			
			boolean wasZero = x[index] == 0;

			// Apply then nonnegativity constraint -- make sure perturbation doesn't make the rate negative
			switch (nonnegativityConstraintAlgorithm) {
			case TRY_ZERO_RATES_OFTEN: // sets rate to zero if they are perturbed to negative values 
				// This way will result in many zeros in the solution, 
				// which may be desirable since global minimum is likely near a boundary
				if (wasZero) { // if that rate was already zero do not keep it at zero
					while (x[index] + perturb[index] < 0) 
						perturb[index] =  perturbationFunc.getPerturbation(r, T, index, variablePerturbBasis);
				} else { // if that rate was not already zero, and it goes negative, set it equal to zero
					if (x[index] + perturb[index] < 0) 
						perturb[index] = -x[index];
				}
				break;
			case LIMIT_ZERO_RATES:    // re-perturb rates if they are perturbed to negative values 
				// This way will result in not a lot of zero rates (none if numIterations >> length(x)),
				// which may be desirable if we don't want a lot of zero rates
				while (x[index] + perturb[index] < 0) {
					perturb[index] =  perturbationFunc.getPerturbation(r, T, index, variablePerturbBasis);
				}
				break;
			case PREVENT_ZERO_RATES:    // Only perturb rates to positive values; any perturbations of zero rates MUST be accepted.
				// Final model will only have zero rates if rate was never selected to be perturbed AND starting model contains zero rates.
				if (!wasZero) {
					perturb[index] = (r.nextDouble() -0.5) * 2 * x[index]; 	
					}
				else {
					perturb[index] = (r.nextDouble()) * 0.00000001;
				}
				break;
			default:
				throw new IllegalStateException("You missed a Nonnegativity Constraint Algorithm type.");
			}
			
			x[index] += perturb[index];
			
			// calculate new misfit vectors
			double deltaE = calculateMisfit(A, d, misfit, x, index,
					perturb[index], misfit_cur_purtub, false);
			double deltaE_ineq = Double.NaN;
			if (hasInequalityConstraint)
				deltaE_ineq = calculateMisfit(A_ineq, d_ineq, misfit_ineq, x, index,
						perturb[index], misfit_ineq_cur_purtub, true);

			// Calculate "energy" of new model (high misfit -> high energy)
			double[] Enew;
			if (ENERGY_SHORTCUT && relativeSmoothnessWt == 0d && Double.isFinite(deltaE) &&
					constraintRanges == null && E != null) {
				// shortcut
				Enew = new double[4];
				Enew[0] = E[0] + deltaE;
				Enew[1] = E[1] + deltaE;
				if (hasInequalityConstraint) {
					Preconditions.checkState(Double.isFinite(deltaE_ineq));
					Enew[0] += deltaE_ineq;
					Enew[3] = E[3] + deltaE_ineq;
				}
				if (D) {
					double[] Etest2 = calculateEnergy(x, misfit_cur_purtub, misfit_ineq_cur_purtub);
					double cDelt = Etest2[0] - E[0];
					System.out.println("Energy shortcut with deltaE="+deltaE+", calcDelta="+cDelt+", diff="+(float)(deltaE - cDelt));
				}
				if (ENERGY_SHORTCUT_DEBUG && (iter-1) % 1000 == 0 && iter > 1) {
					// calculate it manually as well
					double[] Etest = calculateEnergy(x, misfit_cur_purtub, misfit_ineq_cur_purtub);
					if (D) System.out.println("ENERGY SHORTCUT DEBUG");
					for (int i=0; i<Enew.length; i++) {
						double myDelta = Enew[i]-E[i];
						double calcDelta = Etest[i]-E[i];
						double diff = myDelta-calcDelta;
						double error = Math.abs(Enew[i]-Etest[i]);
						double fractError = error/Etest[i];
						if (D) System.out.println("\tEtest["+i+"]="+Etest[i]+"\tEnew["+i+"]="
								+Enew[i]+"\tE["+i+"]="+E[i]+"\tmyDelta="+myDelta+"\tcalcDelta="+calcDelta
								+"\tdiff="+diff+"\tfractError="+fractError);
//						Preconditions.checkState((float)Etest[i] == (float)Enew[i],
						Preconditions.checkState((float)Etest[i] == (float)Enew[i] || fractError < 1e-5 || error < 1e-10,
								"Energy[%s] shortcut fail! %s != %s, oldE=%s, deltaE=%s, deltaE_ineq=%s, myDelta=%s, "
								+ "calcDelta=%s, diff=%s, fractError=%s",
								i, Enew[i], Etest[i], E[i], deltaE, deltaE_ineq, myDelta, calcDelta, diff, fractError);
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
				Enew = calculateEnergy(x, misfit_cur_purtub, misfit_ineq_cur_purtub);
			}
			
			if (D) {
				if (COLUMN_MULT_SPEEDUP_DEBUG && (iter-1) % 10000 == 0 && iter > 1) {
					// lets make sure that the energy calculation was correct with the column speedup
					// only do this if debug is enabled, and do it every 100 iterations
					
					// calculate it the "slow" way
					double[] comp_misfit_new = new double[misfit.length];
					calculateMisfit(A, d, x, comp_misfit_new);
					double[] comp_misfit_ineq_new = null;
					if (hasInequalityConstraint) {
						comp_misfit_ineq_new = new double[misfit_ineq.length];
						calculateMisfit(A_ineq, d_ineq, x, comp_misfit_ineq_new);
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
				if (Enew[0] < E[0] || x[index]==0) {
					P = 1; // Always keep new model if better OR if element was originally zero
				} else {
					// Sometimes keep new model if worse (depends on T)
					P = Math.exp(((E[0] - Enew[0])*energyScaleFactor) / (double) T); 
				}
			break;
			default:
				if (Enew[0] < E[0]) {
					P = 1; // Always keep new model if better
				} else {
					// Sometimes keep new model if worse (depends on T)
					P = Math.exp(((E[0] - Enew[0])*energyScaleFactor) / (double) T); 
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
				/* 
				 * The buffers are a bit confusing, let me explain. Arrays.copyOf(...) calls are costly in this inner
				 * loop, so we avoid them by reusing various misfit buffers. With buffers in use, we only need to
				 * do an array copy in the worst case scenario, where there are num_buffers consecutive kept
				 * perturbations that do not lead to a lower energy. With num_buffers >= 3, this should be exceedingly
				 * rare
				 */
				E = Enew;
				misfit = misfit_cur_purtub;
				misfit_ineq = misfit_ineq_cur_purtub;
				perturbs++;
				curNumNonZero = newNonZero;
				
				// Is this a new best?
				if (Enew[0] < Ebest[0] || keepCurrentAsBest) {
					// update xbest with this perturbation
					// we now keep xbest isolated so we never have to do an array copy
					xbest[index] = x[index];
					if (XBEST_ACCURACY_CHECK) xbest_check_storage = Arrays.copyOf(x, x.length);
					misfit_best = misfit;
					if (hasInequalityConstraint) {
						misfit_ineq_best = misfit_ineq;
					}
					Ebest = Enew;
					numNonZero = curNumNonZero;
					worseKept += worseValsNotYetSaved;
					worseValsNotYetSaved = 0;
				} else {
					worseValsNotYetSaved++;
				}
				
				// now switch buffers so that we're not overwriting a kept solution
				int next_buf = (cur_buffer + 1) % num_buffers;
				misfit_cur_purtub = misfit_buffers[next_buf];
				if (hasInequalityConstraint)
					misfit_ineq_cur_purtub = misfit_ineq_buffers[next_buf];
				if (misfit_best == misfit_cur_purtub) {
					// worst case scenario - we've kept num_buffers non-ideal values in a row, and need to store
					// current best misfits so as to not overwrite them
					misfit_best = Arrays.copyOf(misfit_best, misfit_best.length);
					if (hasInequalityConstraint)
						misfit_ineq_best = Arrays.copyOf(misfit_ineq_best, misfit_ineq_best.length);
				}
				cur_buffer = next_buf;
			} else {
				// undo the perturbation
				x[index] -= perturb[index];
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
					double[] comp_misfit_new = new double[misfit.length];
					calculateMisfit(A, d, xbest, comp_misfit_new);
					double[] comp_misfit_ineq_new = null;
					if (hasInequalityConstraint) {
						comp_misfit_ineq_new = new double[misfit_ineq.length];
						calculateMisfit(A_ineq, d_ineq, xbest, comp_misfit_ineq_new);
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
	public DoubleMatrix2D getA_ineq() {
		return A_ineq;
	}

	@Override
	public DoubleMatrix2D getA() {
		return A;
	}

	@Override
	public double[] getD() {
		return d;
	}

	@Override
	public double[] getD_ineq() {
		return d_ineq;
	}

	@Override
	public void setInputs(DoubleMatrix2D A, double[] d, DoubleMatrix2D A_ineq, double[] d_ineq) {
		this.A = A;
		this.d = d;
		this.A_ineq = A_ineq;
		this.d_ineq = d_ineq;
		setup(A_ineq, d, xbest);
	}

	@Override
	public void setAll(DoubleMatrix2D A, double[] d, DoubleMatrix2D A_ineq, double[] d_ineq, double[] Ebest,
			double[] xbest, double[] misfit, double[] misfit_ineq, int numNonZero) {
		this.A = A;
		this.d = d;
		this.A_ineq = A_ineq;
		this.d_ineq = d_ineq;
		setResults(Ebest, xbest, misfit, misfit_ineq, numNonZero);
	}
	
	public static void main(String[] args) {
		int rups = 1;
		int rows = 1;
		double[] initial = new double[rups];
//		DoubleMatrix2D A = new DenseDoubleMatrix2D(rows, rups);
		SparseCCDoubleMatrix2D A = new SparseCCDoubleMatrix2D(rows, rups);
		for (int row=0; row<rows; row++)
			for (int col=0; col<rups; col++)
				A.set(row, col, 1d);
		double[] d = new double[rows];
		for (int row=0; row<rows; row++)
			d[row] = 100;
		
		SerialSimulatedAnnealing sa = new SerialSimulatedAnnealing(A, d, initial);
		
		sa.setRandom(new Random(1234l));
		sa.setPerturbationFunc(GenerationFunctionType.FIXED_DEBUG);
		
		InversionState state = sa.iterate(1000);
		
		System.out.println("Iters: "+state.iterations+"\tperturbs: "+state.numPerturbsKept+"\tworse: "+state.numWorseValuesKept);
		System.out.println("Energy: "+state.energy[0]);
		
		double[] sol = sa.getBestSolution();
		System.out.println(sol[0]);
	}

}