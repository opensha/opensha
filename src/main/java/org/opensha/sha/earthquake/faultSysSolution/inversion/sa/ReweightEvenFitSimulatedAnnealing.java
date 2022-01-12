package org.opensha.sha.earthquake.faultSysSolution.inversion.sa;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration.Builder;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoRateInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.ParkfieldInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.SectionTotalRateConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.ProgressTrackingCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.TimeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.MisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.Quantity;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfits;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.DraftModelConstraintBuilder;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import cern.colt.function.tdouble.IntIntDoubleFunction;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;

/**
 * Extension of {@link ThreadedSimulatedAnnealing} that dynamically re-weights uncertainty-weighted inversion
 * constraints after each annealing round, trying to fit each constraint equally.
 * <p>
 * This only applies to uncertainty-weighted constraints, as only misfits from those those can be compared across
 * constraints. First you must choose a target quantity that you want to even-fit across all constraints. MAD is a good
 * choice for this quantity, as it is less susceptible to outliers. Standard deviation is a poor choice as it is both
 * sensitive to outliers and doesn't account for net bias; if you want to control the total standard deviation (noting
 * that it may be outlier-dominated), use RMSE instead.
 * <p>
 * After each annealing round, the an average misfit quantity is calculated across all uncertainty-weighted constraints.
 * Then, each uncertainty-weighted constraint is re-weighted such that:
 * <p>
 * newWeight = prevRate * constrMisfit / avgMisfit
 * <o>
 * or, if {@link #USE_SQRT_FOR_TARGET_RATIOS_DEFAULT} is true,
 * <p>
 * newWeight = prevRate * sqrt(constrMisfit) / sqrt(avgMisfit)
 * <o>
 * This value is bounded to not be more than {@link #MAX_ADJUSTMENT_FACTOR} times greater or smaller than the original
 * weight, and individual adjustments are bounded to not exceed {@link #MAX_INDV_ADJUSTMENT_FACTOR}. The latter ensures
 * that weights are adjusted slowly, both to avoid over-reactions to brief deviations, and to slowly transition weight
 * changes in the early part of the inversion where fit changes may happen rapidly.
 * <p>
 * A few more control parameters exist. First of all, weights are defaulted to their original values if the data are
 * poorly fit on average, above {@link #AVG_TARGET_TRANSITION_UPPER_DEFAULT}. If the average fit is between
 * {@link #AVG_TARGET_TRANSITION_UPPER_DEFAULT} and {@link #AVG_TARGET_TRANSITION_LOWER_DEFAULT}, then calculated
 * weights are blended with original weights. This allows the inversion to slowly transition to even fitting as data
 * fits may very wildly (and change quickly) early on.
 * <p>
 * Setting {@link #USE_SQRT_FOR_TARGET_RATIOS_DEFAULT} to true will more conservatively (slowly) adjust weights, which
 * can also prevent wild swings in weighting.
 * 
 * @author kevin
 *
 */
public class ReweightEvenFitSimulatedAnnealing extends ThreadedSimulatedAnnealing {
	
	/*
	 * DEFAULT VALUES
	 */

//	// individual adjustments can never be this many times greater or lower than the previous weight
//	public static final double MAX_INDV_ADJUSTMENT_FACTOR = 1.5d;
//	// can never be this many times greater/less than original weight
//	public static final double MAX_ADJUSTMENT_FACTOR = 20d;
	// individual adjustments can never be this many times greater or lower than the previous weight
	public static final double MAX_INDV_ADJUSTMENT_FACTOR = 2d;
	// can never be this many times greater/less than original weight
	public static final double MAX_ADJUSTMENT_FACTOR = 50d;
	
	public static final Quantity QUANTITY_DEFAULT = Quantity.MAD;
//	public static final double AVG_TARGET_TRANSITION_UPPER_DEFAULT = 5d;
//	public static final double AVG_TARGET_TRANSITION_LOWER_DEFAULT = 1d;
	public static final double AVG_TARGET_TRANSITION_UPPER_DEFAULT = Double.POSITIVE_INFINITY;
	public static final double AVG_TARGET_TRANSITION_LOWER_DEFAULT = Double.POSITIVE_INFINITY;
	public static final boolean CONSERVE_TOT_WEIGHT_DEFAULT = true;
	public static final boolean USE_SQRT_FOR_TARGET_RATIOS_DEFAULT = true;
	public static final boolean USE_VALUE_WEIGHTED_AVERAGE_DEFAULT = false;
	
	// every x rounds, recompute A/d values as scalars from the original values to correct for any floating point error
	// propagated through repeated multiplications
	private static final int floatingPointDriftMod = 10;
	
	/*
	 * RE-WEIGHT PARAMETERS
	 */
	
	// quantity that we are targeting
	private Quantity quantity = QUANTITY_DEFAULT;
	// if true, ensure that the total weight (scaled by row count for each constraint) is constant. this ensures
	// that the relative weight of all uncertainty-weighted constraints does not change relative to any other
	// constraints that are not adjusted
	private boolean conserveTotalWeight = CONSERVE_TOT_WEIGHT_DEFAULT;
	// if true, weight every row equally when computing average values. if false, weight each constraint equally
	private boolean useValueWeightedAverage = USE_VALUE_WEIGHTED_AVERAGE_DEFAULT;
	
	// if true, calculate ratios as sqrt(misfit)/sqrt(avgMisfit)
	private boolean useSqrtForTargetRatios = USE_SQRT_FOR_TARGET_RATIOS_DEFAULT;
	
	private String targetName = QUANTITY_DEFAULT.name();
//	private static final double minTargetForPenalty = 1d; // don't penalize anything for being "better" than this value
	
	// don't mess with anything if the average is above this value
	private double avgTargetWeight2 = AVG_TARGET_TRANSITION_UPPER_DEFAULT;
	// linearly transition to targeted weights up to this average target
	private double avgTargetWeight1 = AVG_TARGET_TRANSITION_LOWER_DEFAULT;
	
	/*
	 * Internal data
	 */
	private DoubleMatrix2D origA, origA_ineq, modA, modA_ineq;
	private double[] origD, origD_ineq, modD, modD_ineq;
	private List<ConstraintRange> origRanges;
	
	private double prevAvgQuantity = Double.NaN;
	private double[] prevConstraintVals = null;

	public ReweightEvenFitSimulatedAnnealing(SimulatedAnnealing sa, CompletionCriteria subCompetionCriteria) {
		this(List.of(sa), subCompetionCriteria, false, QUANTITY_DEFAULT);
	}

	public ReweightEvenFitSimulatedAnnealing(SimulatedAnnealing sa, CompletionCriteria subCompetionCriteria,
			Quantity quantity) {
		this(List.of(sa), subCompetionCriteria, false, quantity);
	}

	public ReweightEvenFitSimulatedAnnealing(List<? extends SimulatedAnnealing> sas, CompletionCriteria subCompetionCriteria,
			boolean average) {
		this(sas, subCompetionCriteria, average, QUANTITY_DEFAULT);
	}

	public ReweightEvenFitSimulatedAnnealing(List<? extends SimulatedAnnealing> sas, CompletionCriteria subCompetionCriteria,
			boolean average, Quantity quantity) {
		super(sas, subCompetionCriteria, average);
		setConstraintRanges(sas.get(0).getConstraintRanges());
		setTargetQuantity(quantity);
	}

	public ReweightEvenFitSimulatedAnnealing(ThreadedSimulatedAnnealing tsa) {
		this(tsa, QUANTITY_DEFAULT);
	}

	public ReweightEvenFitSimulatedAnnealing(ThreadedSimulatedAnnealing tsa, Quantity quantity) {
		super(tsa.getSAs(), tsa.getSubCompetionCriteria(), tsa.isAverage());
		setConstraintRanges(tsa.getConstraintRanges());
		setTargetQuantity(quantity);
	}
	
	public void setTargetQuantity(Quantity quantity) {
		Preconditions.checkNotNull(quantity);
		this.quantity = quantity;
		this.targetName = quantity.name();
	}

	@Override
	protected void beforeRound(long curIter, int round) {
		if (round > 0) {
			Stopwatch watch = Stopwatch.createStarted();
			List<ConstraintRange> ranges = getConstraintRanges();
			Preconditions.checkNotNull(ranges, "Constraint ranges must be set for re-weight inversion");
			Preconditions.checkState(ranges.size() == origRanges.size());
			
			if (modA == null) {
				modA = origA.copy();
				modD = Arrays.copyOf(origD, origD.length);
				if (origA_ineq != null) {
					modA_ineq = origA_ineq.copy();
					modD_ineq = Arrays.copyOf(origD_ineq, origD_ineq.length);
				}
			}
			
			double[] misfits = getBestMisfit();
			double[] misfits_ineq = getBestInequalityMisfit();
			
			List<MisfitStats> stats = new ArrayList<>();
			double avgConstraintQuantity = 0d;
			double avgValueQuantity = 0d;
			int numConstraints = 0;
			int numValues = 0;
			for (ConstraintRange range : ranges) {
				if (range.weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
					double[] myMisfits = range.inequality ? misfits_ineq : misfits;
					myMisfits = Arrays.copyOfRange(myMisfits, range.startRow, range.endRow);
					for (int i=0; i<myMisfits.length; i++)
						myMisfits[i] /= range.weight;
					MisfitStats myStats = new MisfitStats(myMisfits, range);
					double myVal = myStats.get(quantity);
					stats.add(myStats);
					numConstraints++;
					avgConstraintQuantity += myVal;
					int myNumVals = range.endRow - range.startRow;
					numValues += myNumVals;
					avgValueQuantity += myVal * (double)myNumVals;
				} else {
					stats.add(null);
				}
			}
			avgConstraintQuantity /= (double)numConstraints;
			avgValueQuantity /= (double)numValues;
			
			double avgQuantity = useValueWeightedAverage ? avgValueQuantity : avgConstraintQuantity;
			
			String qStr = "Readjusting weights for "+numValues+" values across "+numConstraints
					+" uncertainty-weighted constraints with average misfit "+targetName+":\t"+(float)avgQuantity;
			if (round > 1) {
				double diff = avgQuantity-prevAvgQuantity;
				qStr += " (";
				if (diff > 0)
					qStr += "+";
				qStr += pDF.format(diff/prevAvgQuantity)+")";
			}
			prevAvgQuantity = avgQuantity;
			System.out.println(qStr);
			Preconditions.checkState(numConstraints > 0,
					"Can't use re-weighted inversion without any uncertainty-weighted constraints!");
			Preconditions.checkState(avgQuantity > 0d && Double.isFinite(avgQuantity),
					"Bad avg "+targetName+": %s", avgQuantity);
//			if (avgTarget > minTargetForPenalty) {
//				System.out.println("\tAverage is above threshold, resetting to: "+(float)minTargetForPenalty);
//				avgTarget = minTargetForPenalty;
//			}
			
			double[] origValScalars = new double[misfits.length];
			double[] origValScalars_ineq = misfits_ineq == null ? null : new double[misfits_ineq.length];
			
			double origTotalWeight = 0d;
			double newTotalWeight = 0d;
			
			double[] newWeights = new double[ranges.size()];
			
			boolean scaleToOrig = round > 0 && round % floatingPointDriftMod == 0 && floatingPointDriftMod > 0;
			
			if (prevConstraintVals == null)
				prevConstraintVals = new double[ranges.size()];
			
			for (int i=0; i<ranges.size(); i++) {
				ConstraintRange range = ranges.get(i);
				MisfitStats myStats = stats.get(i);
				double prevWeight = range.weight;
				double origWeight = origRanges.get(i).weight;
				
				double newWeight, scalar;
				if (myStats == null) {
					newWeight = Double.NaN;
					scalar = 1d;
				} else {
					double myTarget = myStats.get(quantity);
					
					int rangeRows = range.endRow-range.startRow;
					origTotalWeight += rangeRows*origWeight;
					
					double misfitRatio;
					if (useSqrtForTargetRatios)
						misfitRatio = Math.sqrt(myTarget)/Math.sqrt(avgQuantity);
					else
						misfitRatio = myTarget/avgQuantity;
					// bound ratio
					misfitRatio = Math.max(misfitRatio, 1d/MAX_INDV_ADJUSTMENT_FACTOR);
					misfitRatio = Math.min(misfitRatio, MAX_INDV_ADJUSTMENT_FACTOR);
					
					double calcWeight = misfitRatio * prevWeight;
					// bound weight
					newWeight = Math.max(calcWeight, origWeight/MAX_ADJUSTMENT_FACTOR);
					newWeight = Math.min(newWeight, origWeight*MAX_ADJUSTMENT_FACTOR);
					
					String targetStr = (float)myTarget+"";
					if (round > 1) {
						double diff = myTarget-prevConstraintVals[i];
						targetStr += " (";
						if (diff > 0)
							targetStr += "+";
						targetStr += pDF.format(diff/prevConstraintVals[i])+")";
					}
					
					System.out.println("\t"+range.shortName+":\t"+targetName+": "+targetStr
							+";\tcalcWeight = "+(float)prevWeight+" x "+(float)misfitRatio+" = "+(float)calcWeight
							+";\tboundedWeight: "+(float)newWeight);
					
					if (avgQuantity > avgTargetWeight2) {
						newWeight = origWeight;
						System.out.println("\t\tAbove max avg target, reverting to original weight: "+(float)origWeight);
					} else if (avgQuantity > avgTargetWeight1) {
						double fract = (avgQuantity - avgTargetWeight1)/(avgTargetWeight2 - avgTargetWeight1);
						Preconditions.checkState(fract >= 0d && fract <= 1d);
						newWeight = origWeight*fract + newWeight*(1-fract);
						System.out.println("\t\tAvg value is poorly fit, linearly blending (fract="+(float)fract
								+") calculated weight with orig: "+(float)newWeight);
					}
					
					prevConstraintVals[i] = myTarget;
					
					newTotalWeight += rangeRows*newWeight;
					if (scaleToOrig)
						scalar = newWeight / origWeight;
					else
						scalar = newWeight / prevWeight;
				}

				double[] myScalars = range.inequality ? origValScalars_ineq : origValScalars;
				for (int r=range.startRow; r<range.endRow; r++)
					myScalars[r] = scalar;
				newWeights[i] = newWeight;
			}
			
			if (conserveTotalWeight) {
				// rescale weights
				double weightScalar = origTotalWeight/newTotalWeight;
				System.out.println("Re-scaling weights by "+(float)origTotalWeight+" / "+(float)newTotalWeight
						+" = "+(float)weightScalar+" to conserve original total weight");
				String weightsStr = null;
				for (int i=0; i<newWeights.length; i++) {
					newWeights[i] *= weightScalar;
					if (Double.isFinite(newWeights[i])) {
						if (weightsStr == null)
							weightsStr = (float)newWeights[i]+"";
						else
							weightsStr += ", "+(float)newWeights[i];
					}
				}
				System.out.println("\tAdjusted weights: "+weightsStr);
				for (int r=0; r<origValScalars.length; r++)
					origValScalars[r] *= weightScalar;
				if (origValScalars_ineq != null)
					for (int r=0; r<origValScalars_ineq.length; r++)
						origValScalars_ineq[r] *= weightScalar;
			}
			
			System.out.println("Updating matrices");
			if (scaleToOrig)
				System.out.println("\tRescaling relative to original values in order to correct any floating-point "
						+ "drift, may take slighly longer (this is done every "+floatingPointDriftMod+" rounds)");
			reweight(origValScalars, scaleToOrig, origA, modA, origD, modD);
			if (misfits_ineq != null)
				reweight(origValScalars_ineq, scaleToOrig, origA_ineq, modA_ineq, origD_ineq, modD_ineq);

			// update the constraint range weights
			List<ConstraintRange> modRanges = new ArrayList<>();
			for (int i=0; i<ranges.size(); i++) {
				ConstraintRange range = ranges.get(i);
				if (stats.get(i) == null)
					modRanges.add(range);
				else
					modRanges.add(new ConstraintRange(range.name, range.shortName, range.startRow, range.endRow,
							range.inequality, newWeights[i], range.weightingType));
			}
			
			System.out.println("Re-calculating misfits");
			double[] xbest = getBestSolution();
			double[] misfit = new double[modD.length];
			SerialSimulatedAnnealing.calculateMisfit(modA, modD, null, xbest, -1, Double.NaN, misfit);
			double[] misfit_ineq = null;
			int nRow = modA.rows();
			int nCol = modA.columns();
			int ineqRows = 0;
			if (modA_ineq != null) {
				misfit_ineq = new double[modD_ineq.length];
				ineqRows = misfit_ineq.length;
				SerialSimulatedAnnealing.calculateMisfit(modA_ineq, modD_ineq, null, xbest, -1, Double.NaN, misfit_ineq);
			}

			System.out.println("Re-calculating energies");
			double[] Ebest = SerialSimulatedAnnealing.calculateEnergy(xbest, misfit, misfit_ineq,
					nRow, nCol, ineqRows, ranges, 0d);
			double prevE = getBestEnergy()[0];
			double newE = Ebest[0];
			String eStr = "\torigE="+(float)prevE+", newE="+newE;
			double diffE = newE-prevE;
			eStr += " (";
			if (diffE > 0)
				eStr += "+";
			eStr += pDF.format(diffE/prevE)+")";
			System.out.println(eStr);
			
			setAll(modA, modD, modA_ineq, modD_ineq, Ebest, xbest, misfit, misfit_ineq, getNumNonZero());
			setConstraintRanges(modRanges);
			
			watch.stop();
			
			System.out.println("Took "+timeStr(watch.elapsed(TimeUnit.MILLISECONDS))+" to re-weight");
		}
		super.beforeRound(curIter, round);
	}
	
	private static void reweight(double[] scalars, boolean scaleToOrig, DoubleMatrix2D origA, DoubleMatrix2D modA,
			double[] origD, double[] modD) {
		for (int r=0; r<scalars.length; r++) {
			if (scaleToOrig)
				modD[r] = origD[r]*scalars[r];
			else
				modD[r] = modD[r]*scalars[r];
		}
		modA.forEachNonZero(new IntIntDoubleFunction() {

			@Override
			public double apply(int row, int col, double val) {
				if (scaleToOrig)
					return origA.get(row, col)*scalars[row];
				return val*scalars[row];
			}
		});
	}

	@Override
	public long[] iterate(long startIter, long startPerturbs, CompletionCriteria criteria) {
		this.origA = getA();
		this.origA_ineq = getA_ineq();
		this.origD = getD();
		this.origD_ineq = getD_ineq();
		
		this.origRanges = getConstraintRanges();
		Preconditions.checkNotNull(origRanges, "Re-weigted inversion needs constraint ranges");
		this.origRanges = new ArrayList<>(origRanges);
		// make sure at least one uncert weighted
		boolean found = false;
		for (ConstraintRange range : origRanges) {
			if (range.weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
				found = true;
				break;
			}
		}
		Preconditions.checkState(found, "Must supply at least 1 uncertainty-weighted constraint for re-weighted inversion");
		
		long[] ret = super.iterate(startIter, startPerturbs, criteria);
		
		System.out.println("Final constraint weights:");
		List<ConstraintRange> modRanges = getConstraintRanges();
		for (int i=0; i<modRanges.size(); i++) {
			ConstraintRange orig = origRanges.get(i);
			ConstraintRange mod = modRanges.get(i);
			if (orig.weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
				double ratio = mod.weight/orig.weight;
				System.out.println("\t"+orig.shortName+":\t"+(float)orig.weight+" x "+(float)ratio+" = "+(float)mod.weight);
			}
		}
		
		return ret;
	}
	
	public static void main(String[] args) {
		File parentDir = new File("/home/kevin/markdown/inversions");
		
		// run this if I need to attach UCERF3 modules after rebuilding default rupture sets
//		reprocessDefaultRupSets(parentDir);
//		System.exit(0);

		String dirName = new SimpleDateFormat("yyyy_MM_dd").format(new Date());

		dirName += "-coulomb-u3";
		File origRupSetFile = new File(parentDir, "fm3_1_u3ref_uniform_coulomb.zip");
		
		FaultSystemRupSet rupSet;
		try {
			rupSet = FaultSystemRupSet.load(origRupSetFile);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
//		rupSet = FaultSystemRupSet.buildFromExisting(rupSet)
//				.u3BranchModules(rupSet.getModule(U3LogicTreeBranch.class)).build();
		
//		U3LogicTreeBranch branch = U3LogicTreeBranch.DEFAULT.copy();
//		branch.setValue(DeformationModels.ABM);
//		branch.setValue(ScalingRelationships.HANKS_BAKUN_08);
//		rupSet = FaultSystemRupSet.buildFromExisting(rupSet).forU3Branch(branch).build();
//		dirName += "-abm-hb08";
		
		double supraBVal = 0.0;
		dirName += "-nshm23_draft-supra_b_"+oDF.format(supraBVal);
		
		boolean applyDefModelUncertaintiesToNucl = true;
		boolean addSectCountUncertaintiesToMFD = false;
		boolean adjustForIncompatibleData = true;

		DraftModelConstraintBuilder constrBuilder = new DraftModelConstraintBuilder(rupSet, supraBVal,
				applyDefModelUncertaintiesToNucl, addSectCountUncertaintiesToMFD, adjustForIncompatibleData);
		constrBuilder.defaultConstraints();
		
		constrBuilder.except(SectionTotalRateConstraint.class);
		constrBuilder.sectSupraNuclMFDs().weight(0.1d);
		dirName += "-nucl_mfd";
		
		boolean reweight = true;
		dirName += "-reweight_"+QUANTITY_DEFAULT.name();
		
		if (reweight && CONSERVE_TOT_WEIGHT_DEFAULT)
			dirName += "-conserve";
		
//		boolean reweight = false;

//		CompletionCriteria completion = TimeCompletionCriteria.getInMinutes(30); dirName += "-30m-x1m";
//		CompletionCriteria avgCompletion = TimeCompletionCriteria.getInMinutes(1);

//		CompletionCriteria completion = TimeCompletionCriteria.getInMinutes(30); dirName += "-30m-x5m";
//		CompletionCriteria avgCompletion = TimeCompletionCriteria.getInMinutes(5);
		
//		CompletionCriteria completion = TimeCompletionCriteria.getInHours(1); dirName += "-1h-x1m";
//		CompletionCriteria avgCompletion = TimeCompletionCriteria.getInMinutes(1);
		
//		CompletionCriteria completion = TimeCompletionCriteria.getInHours(2); dirName += "-2h-x5m";
//		CompletionCriteria avgCompletion = TimeCompletionCriteria.getInMinutes(5);
		
//		CompletionCriteria completion = TimeCompletionCriteria.getInHours(2); dirName += "-2h-x1m";
//		CompletionCriteria avgCompletion = TimeCompletionCriteria.getInMinutes(1);
		
		CompletionCriteria completion = TimeCompletionCriteria.getInHours(5); dirName += "-5h-x5m";
		CompletionCriteria avgCompletion = TimeCompletionCriteria.getInMinutes(5);
		
		Builder builder = InversionConfiguration.builder(constrBuilder.build(), completion);
		builder.sampler(constrBuilder.getSkipBelowMinSampler());
		builder.avgThreads(4, avgCompletion).threads(16);
		
//		builder.except(PaleoSlipInversionConstraint.class).except(PaleoRateInversionConstraint.class)
//			.except(ParkfieldInversionConstraint.class);
//		dirName += "-no_paleo_parkfield";
		
//		builder.except(SectionTotalRateConstraint.class);
//		dirName += "-no_sect";
		
		InversionConfiguration config = builder.build();
		
		InversionInputGenerator inputs = new InversionInputGenerator(rupSet, config);
		inputs.generateInputs(true);
		inputs.columnCompress();
		
		ProgressTrackingCompletionCriteria progress = new ProgressTrackingCompletionCriteria(completion);
		
		SimulatedAnnealing sa = config.buildSA(inputs);
		Preconditions.checkState(sa instanceof ThreadedSimulatedAnnealing);
		if (reweight)
			sa = new ReweightEvenFitSimulatedAnnealing((ThreadedSimulatedAnnealing)sa);
		
		System.out.println("SA Parameters:");
		System.out.println("\tImplementation: "+sa.getClass().getName());
		System.out.println("\tCompletion Criteria: "+completion);
		System.out.println("\tPerturbation Function: "+sa.getPerturbationFunc());
		System.out.println("\tNon-Negativity Constraint: "+sa.getNonnegativeityConstraintAlgorithm());
		System.out.println("\tCooling Schedule: "+sa.getCoolingFunc());
		if (sa instanceof ThreadedSimulatedAnnealing) {
			ThreadedSimulatedAnnealing tsa = (ThreadedSimulatedAnnealing)sa;
			System.out.println("\tTop-Level Threads: "+tsa.getNumThreads());
			System.out.println("\tSub-Completion Criteria: "+tsa.getSubCompetionCriteria());
			System.out.println("\tAveraging? "+tsa.isAverage());
		}
		
		System.out.println("Annealing!");
		sa.iterate(progress);
		
		System.out.println("DONE. Building solution...");
		double[] rawSol = sa.getBestSolution();
		double[] rates = inputs.adjustSolutionForWaterLevel(rawSol);
		
		FaultSystemSolution sol = new FaultSystemSolution(rupSet, rates);
		// add inversion progress
		sol.addModule(progress.getProgress());
		sol.addModule(config);
		InversionMisfits misfits = new InversionMisfits(sa);
		sol.addModule(misfits);
		sol.addModule(misfits.getMisfitStats());
		
		File outputDir = new File(parentDir, dirName);
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		try {
			sol.write(new File(outputDir, "solution.zip"));
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}

	private static final DecimalFormat oDF = new DecimalFormat("0.##");

}
