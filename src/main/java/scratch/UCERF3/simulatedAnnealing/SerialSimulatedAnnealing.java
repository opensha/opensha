package scratch.UCERF3.simulatedAnnealing;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.time.StopWatch;
import org.opensha.commons.util.DataUtils;

import scratch.UCERF3.simulatedAnnealing.completion.CompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.completion.IterationCompletionCriteria;
import scratch.UCERF3.simulatedAnnealing.params.CoolingScheduleType;
import scratch.UCERF3.simulatedAnnealing.params.GenerationFunctionType;
import scratch.UCERF3.simulatedAnnealing.params.NonnegativityConstraintType;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
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
	private final static boolean COLUMN_MULT_SPEEDUP_DEBUG = false;
	private final static boolean XBEST_ACCURACY_CHECK = false;
	private double[] xbest_check_storage;

	private static CoolingScheduleType COOLING_FUNC_DEFAULT = CoolingScheduleType.FAST_SA;
	private CoolingScheduleType coolingFunc = COOLING_FUNC_DEFAULT;
	private static double coolingFuncSlowdown = 1; // Increase this to slow down annealing process (allowing for more time at high temp)
	
	private static NonnegativityConstraintType NONNEGATIVITY_CONST_DEFAULT =
		NonnegativityConstraintType.LIMIT_ZERO_RATES;
	private NonnegativityConstraintType nonnegativityConstraintAlgorithm = NONNEGATIVITY_CONST_DEFAULT;
	
	private static GenerationFunctionType PERTURB_FUNC_DEFAULT = GenerationFunctionType.UNIFORM_NO_TEMP_DEPENDENCE;
	private GenerationFunctionType perturbationFunc = PERTURB_FUNC_DEFAULT;
	
	/**
	 * If true, the current model will always be kept as the best model instead of the best model seen. This allows
	 * the SA algorithm to avoid local minimums in threaded mode where only the "best" solution is passed between threads.
	 */
	
	private double energyScaleFactor = 1; // This effectively makes changes in energies smaller (increasing the prob a jump will be taken to higher E).  Increase to take more jumps early in annealing
	
	private boolean keepCurrentAsBest = false;
	
	private double[] variablePerturbBasis;
	
	private DoubleMatrix2D A, A_MFD;
	private double[] d, d_MFD;
	private double relativeSmoothnessWt;
	private boolean hasInequalityConstraint;
	
	private int nCol;
	private int nRow;
	
	private double[] xbest;  // best model seen so far
	private double[] perturb; // perturbation to current model
	private double[] misfit_best, misfit_ineq_best; // misfit between data and synthetics
	
	private double[] Ebest; // [total, from A, from entropy, from A_ineq]
	private List<Integer> equality_range_ends;
	
	private Random r = new Random();

	public SerialSimulatedAnnealing(DoubleMatrix2D A, double[] d, double[] initialState) {
		this(A, d, initialState, 0, null, null);
	}
	
	public SerialSimulatedAnnealing(DoubleMatrix2D A, double[] d, double[] initialState, double relativeSmoothnessWt, 
			DoubleMatrix2D A_MFD,  double[] d_MFD) {
		this.relativeSmoothnessWt=relativeSmoothnessWt;
		this.hasInequalityConstraint = A_MFD != null;
		if (hasInequalityConstraint)
			Preconditions.checkArgument(d_MFD != null, "we have an A_MFD matrix but no d_MFD vector!");
		else
			Preconditions.checkArgument(d_MFD == null, "we have a d_MFD vector but no A_MFD matrix!");
		this.A_MFD=A_MFD;
		this.d_MFD=d_MFD;
		
		setup(A, d, initialState);
	}
	
	private void setup(DoubleMatrix2D A, double[] d, double[] initialState) {
		Preconditions.checkNotNull(A, "A matrix cannot be null");
		Preconditions.checkNotNull(d, "d matrix cannot be null");
		Preconditions.checkNotNull(initialState, "initial state cannot be null");
		
		nRow = A.rows();
		nCol = A.columns();
		Preconditions.checkArgument(nRow > 0, "nRow of A must be > 0");
		Preconditions.checkArgument(nCol > 0, "nCol of A must be > 0");
		
		Preconditions.checkArgument(d.length == nRow, "d matrix must be same lenth as nRow of A");
		Preconditions.checkArgument(initialState.length == nCol, "initial state must be same lenth as nCol of A");
		
		this.A = A;
		this.d = d;
		

		xbest = Arrays.copyOf(initialState, nCol);  // best model seen so far
		perturb = new double[nCol]; // perturbation to current model
		
		misfit_best = new double[nRow];
		calculateMisfit(A, d, null, xbest, -1, Double.NaN, misfit_best);
		if (hasInequalityConstraint) {
			misfit_ineq_best = new double[d_MFD.length];
			calculateMisfit(A_MFD, d_MFD, null, xbest, -1, Double.NaN, misfit_ineq_best);
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
	
	public void setVariablePerturbationBasis(double[] variablePerturbBasis) {
		Preconditions.checkArgument(variablePerturbBasis == null
				|| variablePerturbBasis.length == xbest.length,
				"variablePerturbBasis must be either null of the same length as xbest");
		this.variablePerturbBasis = variablePerturbBasis;
		if (variablePerturbBasis != null)
			this.perturbationFunc = GenerationFunctionType.VARIABLE_NO_TEMP_DEPENDENCE;
	}

	@Override
	public double[] getBestSolution() {
		return xbest;
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
	public void setResults(double[] Ebest, double[] xbest, double[] misfit_best, double[] misfit_ineq_best) {
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
	}
	
	@Override
	public void setResults(double[] Ebest, double[] xbest) {
		setResults(Ebest, xbest, null, null);
	}
	
	public void setEqualityRangeEnds(List<Integer> rangeEnds) {
		this.equality_range_ends = rangeEnds;
	}
	
	private static void calculateMisfit(DoubleMatrix2D mat, double[] data, double[] prev_misfit,
			double[] solution, int perturbCol, double perturbation, double[] misfit) {
		if (mat instanceof SparseCCDoubleMatrix2D && perturbCol >= 0 && prev_misfit != null) {
//			misfit = Arrays.copyOf(prev_misfit, prev_misfit.length);
			System.arraycopy(prev_misfit, 0, misfit, 0, prev_misfit.length);
			Dcs dcs = ((SparseCCDoubleMatrix2D)mat).elements();
			final int[] rowIndexesA = dcs.i;
			final int[] columnPointersA = dcs.p;
			final double[] valuesA = dcs.x;
			
			int low = columnPointersA[perturbCol];
			for (int k = columnPointersA[perturbCol + 1]; --k >= low;) {
				int row = rowIndexesA[k];
				double value = valuesA[k];
				misfit[row] += value * perturbation;
			}
		} else {
			DoubleMatrix1D sol_clone = new DenseDoubleMatrix1D(solution);
			
			DenseDoubleMatrix1D syn = new DenseDoubleMatrix1D(mat.rows());
			mat.zMult(sol_clone, syn);
			
			for (int i = 0; i < mat.rows(); i++) {
				misfit[i] = syn.get(i) - data[i];  // misfit between synthetics and data
			}
		}
	}
	
	protected double[] calculateEnergy(double[] solution, double[] misfit, double[] misfit_ineq) {
		
		// Do forward problem for new perturbed model (calculate synthetics)
		
		double Eequality = 0;
		double[] ret;
		if (equality_range_ends == null) {
			for (int i = 0; i < nRow; i++) {
				// NOTE: it is important that we loop over nRow and not the actual misfit array
				// as it may be larger than nRow (for efficiency and less array copies)
				Eequality += Math.pow(misfit[i], 2);  // L2 norm of misfit vector
			}
			ret = new double[4];
			ret[1] = Eequality;
		} else {
			ret = new double[4+equality_range_ends.size()];
			int curIndex = 4;
			int rowEnd = equality_range_ends.get(0);
			for (int i = 0; i < nRow; i++) {
				// NOTE: it is important that we loop over nRow and not the actual misfit array
				// as it may be larger than nRow (for efficiency and less array copies)
				
				while (i>rowEnd) {
					curIndex += 1;
					rowEnd = equality_range_ends.get(curIndex-4);
				}
				
				double val = Math.pow(misfit[i], 2);  // L2 norm of misfit vector
				Eequality += val;
				ret[curIndex] += val;
			}
			ret[1] = Eequality;
		}
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
		if (hasInequalityConstraint) {
			for (int i = 0; i < d_MFD.length; i++) {
				// NOTE: it is important that we loop over d_MFD.length and not the actual misfit array
				// as it may be larger than nRow (for efficiency and less array copies)
				if (misfit_ineq[i] > 0.0) // This makes it an INEQUALITY constraint (Target MFD is an UPPER bound)
					Einequality += Math.pow(misfit_ineq[i], 2);  // L2 norm of misfit vector
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
	public synchronized long iterate(long numIterations) {
		return iterate(new IterationCompletionCriteria(numIterations));
	}
	
	@Override
	public synchronized long iterate(CompletionCriteria completion) {
		return iterate(0, 0, completion)[0];
	}

	@Override
	public synchronized long[] iterate(long startIter, long startPerturbs, CompletionCriteria criteria) {
		StopWatch watch = new StopWatch();
		watch.start();
		
		if(D) System.out.println("Solving inverse problem with simulated annealing ... \n");
		if(D) System.out.println("Cooling Function: " + coolingFunc.name());
		if(D) System.out.println("Perturbation Function: " + perturbationFunc.name());
		if(D) System.out.println("Nonnegativity Constraint: " + nonnegativityConstraintAlgorithm.name());
		if(D) System.out.println("Completion Criteria: " + criteria);
		
		double[] Enew;
		double P;
		long iter=startIter+1;
		long perturbs = startPerturbs;
		int index;
		double[] x = Arrays.copyOf(xbest, xbest.length);
		double[] E = Ebest;
		double T;
		// this is where we store previous misfits
		double[] misfit;
		if (misfit_best == null)
			misfit = null;
		else
			misfit = Arrays.copyOf(misfit_best, misfit_best.length);
		// this is where we store new candidate misfits
		double[] misfit_new1 = new double[nRow];
		double[] misfit_new2 = new double[nRow];
		double[] misfit_cur_purtub = misfit_new1;
		double[] misfit_ineq = null;
		double[] misfit_ineq_new1 = null;
		double[] misfit_ineq_new2 = null;
		double[] misfit_ineq_cur_purtub = null;
		if (hasInequalityConstraint) {
			if (misfit_ineq_best != null) {
				misfit_ineq = Arrays.copyOf(misfit_ineq_best, misfit_ineq_best.length);
			}
			misfit_ineq_new1 = new double[A_MFD.rows()];
			misfit_ineq_new2 = new double[A_MFD.rows()];
			misfit_ineq_cur_purtub = misfit_ineq_new1;
		}

		// we do iter-1 because iter here is 1-based, not 0-based
		while (!criteria.isSatisfied(watch, iter-1, Ebest, perturbs)) {

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
			index = (int)(r.nextDouble() * (double)nCol); // casting as int takes the floor


			// How much to perturb index (some perturbation functions are a function of T)	
			perturb[index] = getPerturbation(perturbationFunc, T, index);

			// Apply then nonnegativity constraint -- make sure perturbation doesn't make the rate negative
			switch (nonnegativityConstraintAlgorithm) {
			case TRY_ZERO_RATES_OFTEN: // sets rate to zero if they are perturbed to negative values 
				// This way will result in many zeros in the solution, 
				// which may be desirable since global minimum is likely near a boundary
				if (x[index] == 0) { // if that rate was already zero do not keep it at zero
					while (x[index] + perturb[index] < 0) 
						perturb[index] = getPerturbation(perturbationFunc,T, index);
				} else { // if that rate was not already zero, and it goes negative, set it equal to zero
					if (x[index] + perturb[index] < 0) 
						perturb[index] = -x[index];
				}
				break;
			case LIMIT_ZERO_RATES:    // re-perturb rates if they are perturbed to negative values 
				// This way will result in not a lot of zero rates (none if numIterations >> length(x)),
				// which may be desirable if we don't want a lot of zero rates
				while (x[index] + perturb[index] < 0) {
					perturb[index] = getPerturbation(perturbationFunc,T, index);	
				}
				break;
			case PREVENT_ZERO_RATES:    // Only perturb rates to positive values; any perturbations of zero rates MUST be accepted.
				// Final model will only have zero rates if rate was never selected to be perturbed AND starting model contains zero rates.
				if (x[index]!=0) {
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
			calculateMisfit(A, d, misfit, x, index, perturb[index], misfit_cur_purtub);
			if (hasInequalityConstraint)
				calculateMisfit(A_MFD, d_MFD, misfit_ineq, x, index, perturb[index], misfit_ineq_cur_purtub);

			// Calculate "energy" of new model (high misfit -> high energy)
//			Enew = calculateMisfit(xnew);
			Enew = calculateEnergy(x, misfit_cur_purtub, misfit_ineq_cur_purtub);
			
			if (D) {
				if (COLUMN_MULT_SPEEDUP_DEBUG && (iter-1) % 10000 == 0 && iter > 1) {
					// lets make sure that the energy calculation was correct with the column speedup
					// only do this if debug is enabled, and do it every 100 iterations
					
					// calculate it the "slow" way
					double[] comp_misfit_new = new double[misfit.length];
					calculateMisfit(A, d, null, x, -1, Double.NaN, comp_misfit_new);
					double[] comp_misfit_ineq_new = null;
					if (hasInequalityConstraint) {
						comp_misfit_ineq_new = new double[misfit_ineq.length];
						calculateMisfit(A_MFD, d_MFD, null, x, -1, Double.NaN, comp_misfit_ineq_new);
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
			
			// Use transition probability to determine (via random number draw) if solution is kept
			if (P > r.nextDouble()) {
				/* 
				 * I know this is confusing...let me explain what I'm doing here. The most costly operation
				 * in this inner loop are the array copies. I now use multiple buffers to store perturbations
				 * and misfits such that I only have to do an array copy in the worst case scenarios.
				 * 
				 * The only scenario now where an array copy needs to happen is if we're keeping a solution
				 * that is not in fact a new best for the 2nd time. I'll spare the details, but it works (and
				 * is tested).
				 * * 
				 */
				E = Enew;
				misfit = misfit_cur_purtub;
				misfit_ineq = misfit_ineq_cur_purtub;
				perturbs++;
				
				// Is this a new best?
				if (Enew[0] < Ebest[0] || keepCurrentAsBest) {
					// we don't want to array copy as it's slow. instead we fix special cases only when
					// needed with an array copy (see below)
					xbest = x;
					if (XBEST_ACCURACY_CHECK) xbest_check_storage = Arrays.copyOf(x, x.length);
					misfit_best = misfit;
					if (hasInequalityConstraint) {
						misfit_ineq_best = misfit_ineq;
					}
					Ebest = Enew;
				} else {
					if (xbest == x) {
						// in this case we're keeping an x that is not in fact best. we need
						// to permanently store xbest
						xbest = Arrays.copyOf(x, nCol);
						// now roll back xbest
						xbest[index] -= perturb[index];
					}
				}
				
				// now switch buffers so that we're not overwriting a kept solution
				if (misfit_cur_purtub == misfit_new1) {
					misfit_cur_purtub = misfit_new2;
					misfit_ineq_cur_purtub = misfit_ineq_new2;
				} else {
					misfit_cur_purtub = misfit_new1;
					misfit_ineq_cur_purtub = misfit_ineq_new1;
				}
				if (misfit_best == misfit_cur_purtub) {
					// this one is being kept randomly even though it's not the best. make sure to
					// save the best as we may now purtub what was previously set to misfit_best
					misfit_best = Arrays.copyOf(misfit_best, misfit_best.length);
					if (hasInequalityConstraint)
						misfit_ineq_best = Arrays.copyOf(misfit_ineq_best, misfit_ineq_best.length);
				}
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
					calculateMisfit(A, d, null, xbest, -1, Double.NaN, comp_misfit_new);
					double[] comp_misfit_ineq_new = null;
					if (hasInequalityConstraint) {
						comp_misfit_ineq_new = new double[misfit_ineq.length];
						calculateMisfit(A_MFD, d_MFD, null, xbest, -1, Double.NaN, comp_misfit_ineq_new);
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
			System.out.println("Annealing schedule completed.");
			double runSecs = watch.getTime() / 1000d;
			System.out.println("Done with Inversion after " + runSecs + " seconds.");
		}
		
		// we added one to it before, remove it to make it zero-based
		long[] ret = { iter-1, perturbs };
		return ret;
	}

	private double getPerturbation(GenerationFunctionType perturbationFunc, double T, int index) {

		double perturbation;
		double r2;

		switch (perturbationFunc) {
		case UNIFORM_NO_TEMP_DEPENDENCE:
			perturbation = (r.nextDouble()-0.5)* 0.001;
			break;
		case VARIABLE_NO_TEMP_DEPENDENCE:
			double basis = variablePerturbBasis[index];
			if (basis == 0)
				basis = 0.00000001;
			perturbation = (r.nextDouble()-0.5) * basis * 1000d;
			break;
		case GAUSSIAN:
			perturbation =  (1/Math.sqrt(T)) * r.nextGaussian() * 0.0001 * Math.exp(1/(2*T)); 
			break;
		case TANGENT:
			perturbation = T * 0.001 * Math.tan(Math.PI * r.nextDouble() - Math.PI/2);	
			break;
		case POWER_LAW:
			r2 = r.nextDouble();  
			perturbation = Math.signum(r2-0.5) * T * 0.001 * (Math.pow(1+1/T,Math.abs(2*r2-1))-1);
			break;
		case EXPONENTIAL:
			r2 = r.nextDouble();  
			perturbation = Math.pow(10, r2) * T * 0.001;
			break;
		default:
			throw new IllegalStateException("Oh dear.  You missed a Generation Function type.");
		}

		return perturbation;

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
				"Cooling schedule. One of: "+enumOptionsStr(NonnegativityConstraintType.values())
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

}