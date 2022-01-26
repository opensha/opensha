package org.opensha.sha.earthquake.faultSysSolution.inversion.sa;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.Interpolate;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration.Builder;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionInputGenerator;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.ConstraintWeightingType;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.CompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.IterationsPerVariableCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.ProgressTrackingCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.inversion.sa.completion.TimeCompletionCriteria;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitProgress;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.MisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.Quantity;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfits;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_U3_HybridLogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.RupturePlausibilityModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSectConstraintModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SubSeisMoRateReductions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.U3_UncertAddDeformationModels;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Doubles;

import cern.colt.function.tdouble.IntIntDoubleFunction;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

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
 * After each annealing round, the average misfit quantity is calculated across all uncertainty-weighted constraints.
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
 * fits may very wildly (and change quickly) early on. This feature is currently unused.
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

	/**
	 *  individual adjustments can never be this many times greater or lower than the previous weight
	 */
	public static final double MAX_INDV_ADJUSTMENT_FACTOR = 2d;
	/**
	 *  can never be this many times greater/less than original weight
	 */
	public static final double MAX_ADJUSTMENT_FACTOR = 100d;
	/**
	 *  if true, the adjustment factor only applies on the high and and constraints phase out when their weight is
	 *  below the origWeight/MAX_ADJUSTMENT_FACTOR. A phase out constraint means that it is weighted so low that the
	 *  constraint is fit better than others even when disabled, and thus should not be considered anymore when
	 *  computing relative weight changes. This should help with pathological branches where some constraints just
	 *  cannot be fit. 
	 */
	public static final boolean PHASE_OUT_BELOW_LOWER_BOUND = true;
	/**
	 * if {@link #PHASE_OUT_BELOW_LOWER_BOUND} is true, then a constraint is phased out linearly as it's weight
	 * transitions between origWeight/{@link #PHASE_OUT_START_FACTOR} and origWeight/{@link #PHASE_OUT_END_FACTOR}.
	 */
	public static final double PHASE_OUT_START_FACTOR = 50d;
	/**
	 * if {@link #PHASE_OUT_BELOW_LOWER_BOUND} is true, then a constraint is phased out linearly as it's weight
	 * transitions between origWeight/{@link #PHASE_OUT_START_FACTOR} and origWeight/{@link #PHASE_OUT_END_FACTOR}.
	 */
	public static final double PHASE_OUT_END_FACTOR = 100d;
	
	public static final Quantity QUANTITY_DEFAULT = Quantity.MAD;
//	public static final double AVG_TARGET_TRANSITION_UPPER_DEFAULT = 5d;
//	public static final double AVG_TARGET_TRANSITION_LOWER_DEFAULT = 1d;
	public static final double AVG_TARGET_TRANSITION_UPPER_DEFAULT = Double.POSITIVE_INFINITY;
	public static final double AVG_TARGET_TRANSITION_LOWER_DEFAULT = Double.POSITIVE_INFINITY;
	public static final boolean CONSERVE_TOT_WEIGHT_DEFAULT = true;
	// if true, then the algorithm doesn't know about or use the 'conserved' weights and actual weights can exceed
	// the initial bounds. if false, bounds will be applied to the real weights
	public static final boolean CONSERVE_SEPARATELY = false;
	public static final boolean TARGET_MEDIAN_DEFAULT = false;
	// SQRT seems better, at least for fitting MAD. otherwise, average misfit will appear to oscillate with many
	// small overcorrections
	public static final boolean USE_SQRT_FOR_TARGET_RATIOS_DEFAULT = true;
	public static final boolean USE_VALUE_WEIGHTED_AVERAGE_DEFAULT = false;
	
	// every x rounds, recompute A/d values as scalars from the original values to correct for any floating point error
	// propagated through repeated multiplications
	private static final int floatingPointDriftMod = 100;
	
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
	// if true, target each constraint to the median across all constraints rather than the mean
	private boolean targetMedian = TARGET_MEDIAN_DEFAULT;
	
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
	
	private double prevTarget = Double.NaN;
	private double[] prevConstraintVals = null;
	private double prevWeightConservationScalar = 1d;
	
	private List<Long> iters;
	private List<Long> times;
	private List<Double> targetVals;
	private List<InversionMisfitStats> iterStats;

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
	
	private List<MisfitStats> calcUncertWtStats(List<ConstraintRange> ranges, double[] misfits, double[] misfits_ineq) {
		List<MisfitStats> stats = new ArrayList<>();
		for (ConstraintRange range : ranges) {
			MisfitStats myStats = null;
			if (range.weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
				double[] myMisfits = range.inequality ? misfits_ineq : misfits;
				myMisfits = Arrays.copyOfRange(myMisfits, range.startRow, range.endRow);
				for (int i=0; i<myMisfits.length; i++)
					myMisfits[i] /= range.weight;
				myStats = new MisfitStats(myMisfits, range);
			}
			stats.add(myStats);
		}
		return stats;
	}

	@Override
	protected void beforeRound(InversionState state, int round) {
		beforeRound(state, round, false);
	}

	protected double beforeRound(InversionState state, int round, boolean targetOnly) {
		double target;
		if (round > 0) {
			Stopwatch watch = Stopwatch.createStarted();
			List<ConstraintRange> ranges = getConstraintRanges();
			Preconditions.checkNotNull(ranges, "Constraint ranges must be set for re-weight inversion");
			Preconditions.checkState(ranges.size() == origRanges.size());
			
			Preconditions.checkState(!targetMedian || !useValueWeightedAverage, "Can't have both value weighting and median enabled");
			
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
			
			List<MisfitStats> stats = calcUncertWtStats(ranges, misfits, misfits_ineq);
			int numConstraints = 0;
			int numValues = 0;
			List<Double> constraintTargetVals = new ArrayList<>();
			List<Double> phaseOutStatuses = PHASE_OUT_BELOW_LOWER_BOUND ? new ArrayList<>() : null;
			boolean hasPhaseOut = false;
			for (int r=0; r<ranges.size(); r++) {
				ConstraintRange range = ranges.get(r);
				MisfitStats myStats = stats.get(r);
				if (myStats != null) {
					double myVal = myStats.get(quantity);
					constraintTargetVals.add(myVal);
					numConstraints++;
					int myNumVals = range.endRow - range.startRow;
					numValues += myNumVals;
					if (PHASE_OUT_BELOW_LOWER_BOUND) {
						// see if we're phasing/ed out
						double origWeight = origRanges.get(r).weight;
						double phaseUpperTarget = origWeight/PHASE_OUT_START_FACTOR;
						double phaseLowerTarget = origWeight/PHASE_OUT_END_FACTOR;
						double status;
						if (range.weight < phaseLowerTarget)
							// fully phased out
							status = 0d;
						else if (range.weight < phaseUpperTarget)
							// partially phased out
							status = (range.weight - phaseLowerTarget)/(phaseUpperTarget - phaseLowerTarget);
						else
							// not phased out
							status = 1;
						hasPhaseOut = hasPhaseOut || status != 1d;
						phaseOutStatuses.add(status);
					}
				}
			}
			
			if (PHASE_OUT_BELOW_LOWER_BOUND && hasPhaseOut) {
				// do this in a loop to ensure that nothing is both 'phased out' and above average
				while (true) {
					if (targetMedian) {
						// need to compute a weighted median, more complicated
						
						// sort by value, increasing, keeping track of associated weights
						List<ComparablePairing<Double, Double>> pairings = ComparablePairing.build(constraintTargetVals, phaseOutStatuses);
						Collections.sort(pairings);
						
						// here 'weight' refers to the degree a constraint is phased in and should contribute to the target,
						// not the constraint weights themselves
						double sumWeights = 0d;
						for (double status : phaseOutStatuses)
							sumWeights += status;
						
						Preconditions.checkState(sumWeights >= 1d, "All constraints phased out?");
						
						double weightBelow = 0d;
						// this will store the first index where the weight of all values before it is > half
						int indexAbove = -1;
						for (int i=0; i<pairings.size(); i++) {
							ComparablePairing<Double, Double> pairing = pairings.get(i);
							
							if (weightBelow >= 0.5*sumWeights) {
								indexAbove = i;
								break;
							}
							
							weightBelow += pairing.getData();
						}
						Preconditions.checkState(indexAbove > 0);
						double val1 = pairings.get(indexAbove-1).getComparable();
						double weight1 = weightBelow;
						double val2 = pairings.get(indexAbove).getComparable();
						double weight2 = weightBelow+pairings.get(indexAbove).getData();
						Preconditions.checkState(weight1 <= 0.5d && weight2 >= 0.5d);
						target = Interpolate.findY(weight1, val1, weight2, val2, 0.5d);
					} else {
						double numerator = 0d;
						double denominator = 0d;
						for (int i=0; i<constraintTargetVals.size(); i++) {
							double val = constraintTargetVals.get(i);
							double status = phaseOutStatuses.get(i);
							numerator += val*status;
							denominator += status;
						}
						target = numerator/denominator;
					}
					// check that everything currently phased out is actually below this target, otherwise un-phase it
					boolean allPhasedBelow = true;
					for (int i=0; i<constraintTargetVals.size(); i++) {
						double val = constraintTargetVals.get(i);
						double status = phaseOutStatuses.get(i);
						if (status < 1d && val > target) {
							System.out.println("Un-phasing a constraint with status="+(float)status
									+" but "+targetName+"="+(float)val+" > "+target);
							allPhasedBelow = false;
							phaseOutStatuses.set(i, 1d);
						}
					}
					if (allPhasedBelow)
						break;
				}
			} else {
				if (targetMedian)
					target = DataUtils.median(Doubles.toArray(constraintTargetVals));
				else
					target = StatUtils.mean(Doubles.toArray(constraintTargetVals));;
			}
			
			if (targetOnly) {
				System.out.println("Final target value: "+(float)target);
				return target;
			}
			
			String qStr = "Readjusting weights for "+numValues+" values across "+numConstraints
					+" uncertainty-weighted constraints with "+(targetMedian?"median":"average")
					+" misfit "+targetName+":\t"+(float)target;
			if (round > 1) {
				double diff = target-prevTarget;
				qStr += " (";
				if (diff > 0)
					qStr += "+";
				qStr += pDF.format(diff/prevTarget)+")";
			}
			prevTarget = target;
			System.out.println(qStr);
			Preconditions.checkState(numConstraints > 0,
					"Can't use re-weighted inversion without any uncertainty-weighted constraints!");
			Preconditions.checkState(target > 0d && Double.isFinite(target),
					"Bad avg "+targetName+": %s", target);
//			if (avgTarget > minTargetForPenalty) {
//				System.out.println("\tAverage is above threshold, resetting to: "+(float)minTargetForPenalty);
//				avgTarget = minTargetForPenalty;
//			}
			
			double[] origValScalars = new double[misfits.length];
			double[] origValScalars_ineq = misfits_ineq == null ? null : new double[misfits_ineq.length];
			
			double origTotalWeight = 0d;
			double newTotalWeight = 0d;

			double[] newWeights = new double[ranges.size()];
			double conservableTotalWeight = 0d;
			boolean[] conservableWeights = new boolean[ranges.size()];
			
			boolean scaleToOrig = round > 0 && round % floatingPointDriftMod == 0 && floatingPointDriftMod > 0;
			
			if (prevConstraintVals == null)
				prevConstraintVals = new double[ranges.size()];
			
			for (int i=0; i<ranges.size(); i++) {
				ConstraintRange range = ranges.get(i);
				MisfitStats myStats = stats.get(i);
				double prevWeight = range.weight;
				double origWeight = origRanges.get(i).weight;
				
				if (conserveTotalWeight && CONSERVE_SEPARATELY)
					// do weight calculations units of the "original" weights
					// but we'll apply them in a conserved way
					prevWeight /= prevWeightConservationScalar;
				
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
						misfitRatio = Math.sqrt(myTarget)/Math.sqrt(target);
					else
						misfitRatio = myTarget/target;
					// bound ratio
					misfitRatio = Math.max(misfitRatio, 1d/MAX_INDV_ADJUSTMENT_FACTOR);
					misfitRatio = Math.min(misfitRatio, MAX_INDV_ADJUSTMENT_FACTOR);
					
					double calcWeight = misfitRatio * prevWeight;
					// bound weight
					boolean bounded = false;
					boolean phased = false;
					String phaseStr = null;
					if (PHASE_OUT_BELOW_LOWER_BOUND && calcWeight < origWeight) {
						// see if this calculated weight is in/beyond the phase out range
						double phaseUpperTarget = origWeight/PHASE_OUT_START_FACTOR;
						double phaseLowerTarget = origWeight/PHASE_OUT_END_FACTOR;
						double status;
						if ((float)calcWeight <= (float)phaseLowerTarget) {
							// fully phased out
							status = 0d;
							newWeight = phaseLowerTarget;
						} else if ((float)calcWeight < (float)phaseUpperTarget) {
							// partially phased out
							status = (calcWeight - phaseLowerTarget)/(phaseUpperTarget - phaseLowerTarget);
							newWeight = calcWeight;
						} else {
							// not phased out
							status = 1;
							newWeight = calcWeight;
						}
						phased = status != 1d;
						if (phased) {
							if (status == 0d)
								phaseStr = "FULLY PHASED OUT: "+(float)phaseLowerTarget;
							else
								phaseStr = "phasing out (f="+oDF.format(status)+")";
						}
					} else {
						// apply bounds if applicable
						newWeight = Math.max(calcWeight, origWeight/MAX_ADJUSTMENT_FACTOR);
						newWeight = Math.min(newWeight, origWeight*MAX_ADJUSTMENT_FACTOR);
						bounded = newWeight != calcWeight;
					}
					conservableWeights[i] = !bounded && !phased;
					
					String targetStr = fiveDigits.format(myTarget)+"";
					if (round > 1) {
						double diff = myTarget-prevConstraintVals[i];
						targetStr += " (";
						if (diff > 0)
							targetStr += "+";
						targetStr += pDF.format(diff/prevConstraintVals[i])+")";
					}
					
					System.out.println("\t"+range.shortName+":\t"+targetName+": "+targetStr
							+";\tcalcWeight = "+fiveDigits.format(prevWeight)+" x "+fiveDigits.format(misfitRatio)
							+" = "+fiveDigits.format(calcWeight)
							+(bounded ? ";\tbounded: "+(float)newWeight : "")
							+(phased ? ";\t"+phaseStr : ""));
					
					if (target > avgTargetWeight2) {
						newWeight = origWeight;
						System.out.println("\t\tAbove max target, reverting to original weight: "+(float)origWeight);
					} else if (target > avgTargetWeight1) {
						double fract = (target - avgTargetWeight1)/(avgTargetWeight2 - avgTargetWeight1);
						Preconditions.checkState(fract >= 0d && fract <= 1d);
						newWeight = origWeight*fract + newWeight*(1-fract);
						System.out.println("\t\tTarget value is poorly fit, linearly blending (fract="+(float)fract
								+") calculated weight with orig: "+(float)newWeight);
					}
					
					prevConstraintVals[i] = myTarget;
					
					newTotalWeight += rangeRows*newWeight;
					if (conservableWeights[i])
						conservableTotalWeight += rangeRows*newWeight;
					if (scaleToOrig)
						scalar = newWeight / origWeight;
					else if (conserveTotalWeight && CONSERVE_SEPARATELY)
						scalar = newWeight / (prevWeight*prevWeightConservationScalar);
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
				if (!CONSERVE_SEPARATELY && conservableTotalWeight == 0d) {
					System.out.println("Conservable total weight is zero, "
							+ "everything is bounded or phased, skipping conserve step");
					prevWeightConservationScalar = 1d;
				} else {
					double fixedWeight = newTotalWeight-conservableTotalWeight;
					double weightScalar;
					if (CONSERVE_SEPARATELY) {
						weightScalar = origTotalWeight/newTotalWeight;
						System.out.println("Re-scaling weights by "+(float)origTotalWeight+" / "+(float)newTotalWeight
								+" = "+(float)weightScalar+" to conserve original total weight");
					} else {
						weightScalar = (origTotalWeight - fixedWeight)/conservableTotalWeight;
						System.out.println("Re-scaling weights by "+(float)(origTotalWeight - fixedWeight)+" / "+(float)newTotalWeight
								+" = "+(float)weightScalar+" to conserve original total weight");
					}
					String weightsStr = null;
					for (int i=0; i<newWeights.length; i++) {
						if (Double.isFinite(newWeights[i])) {
							if (CONSERVE_SEPARATELY || conservableWeights[i]) {
								newWeights[i] *= weightScalar;
								ConstraintRange range = ranges.get(i);
								double[] myScalars = range.inequality ? origValScalars_ineq : origValScalars;
								for (int r=range.startRow; r<range.endRow; r++)
									myScalars[r] *= weightScalar;
							}
							if (weightsStr == null)
								weightsStr = (float)newWeights[i]+"";
							else
								weightsStr += ", "+(float)newWeights[i];
						}
					}
					System.out.println("\tAdjusted weights: "+weightsStr);
					
					prevWeightConservationScalar = weightScalar;
				}
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
			SerialSimulatedAnnealing.calculateMisfit(modA, modD, xbest, misfit);
			double[] misfit_ineq = null;
			int nRow = modA.rows();
			int nCol = modA.columns();
			int ineqRows = 0;
			ColumnOrganizedAnnealingData modEqualityData = new ColumnOrganizedAnnealingData(modA, modD);
			ColumnOrganizedAnnealingData modInqualityData = null;
			if (modA_ineq != null) {
				modInqualityData = new ColumnOrganizedAnnealingData(modA_ineq, modD_ineq);
				misfit_ineq = new double[modD_ineq.length];
				ineqRows = misfit_ineq.length;
				SerialSimulatedAnnealing.calculateMisfit(modA_ineq, modD_ineq, xbest, misfit_ineq);
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
			
			setAll(modEqualityData, modInqualityData, Ebest, xbest, misfit, misfit_ineq, getNumNonZero());
			setConstraintRanges(modRanges);
			
			watch.stop();
			
			Preconditions.checkNotNull(iters);
			Preconditions.checkNotNull(times);
			Preconditions.checkNotNull(iterStats);
			List<MisfitStats> uncertMisfits = new ArrayList<>();
			for (MisfitStats mstats : stats)
				if (mstats != null)
					uncertMisfits.add(mstats);
			iters.add(state.iterations);
			times.add(state.elapsedTimeMillis);
			targetVals.add(target);
			iterStats.add(new InversionMisfitStats(uncertMisfits));
			
			System.out.println("Took "+timeStr(watch.elapsed(TimeUnit.MILLISECONDS))+" to re-weight");
		} else {
			target = Double.NaN;
		}
		super.beforeRound(state, round);
		return target;
	}
	
	public InversionMisfitProgress getMisfitProgress() {
		Preconditions.checkNotNull(iters);
		return new InversionMisfitProgress(iters, times, iterStats, quantity, targetVals);
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
	public InversionState iterate(InversionState startState, CompletionCriteria criteria) {
		this.origA = getA();
		this.origA_ineq = getA_ineq();
		this.origD = getD();
		this.origD_ineq = getD_ineq();
		
		this.origRanges = getConstraintRanges();
		this.prevWeightConservationScalar = 1d;
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
		
		iters = new ArrayList<>();
		times = new ArrayList<>();
		targetVals = new ArrayList<>();
		iterStats = new ArrayList<>();
		
		InversionState ret = super.iterate(startState, criteria);
		
		List<MisfitStats> stats = calcUncertWtStats(getConstraintRanges(), getBestMisfit(), getBestInequalityMisfit());
		List<MisfitStats> uncertMisfits = new ArrayList<>();
		
		double avgMisfit = 0;
		for (MisfitStats mstats : stats) {
			if (mstats != null) {
				uncertMisfits.add(mstats);
				avgMisfit += mstats.get(quantity);
			}
		}
		avgMisfit /= uncertMisfits.size();
		iters.add(ret.iterations);
		times.add(ret.elapsedTimeMillis);
		targetVals.add(beforeRound(ret, iters.size()+1, true));
		iterStats.add(new InversionMisfitStats(uncertMisfits));
		
		System.out.println("Final constraint weights with average misfit "+targetName+": "+(float)avgMisfit);
		List<ConstraintRange> modRanges = getConstraintRanges();
		for (int i=0; i<modRanges.size(); i++) {
			ConstraintRange orig = origRanges.get(i);
			ConstraintRange mod = modRanges.get(i);
			if (orig.weightingType == ConstraintWeightingType.NORMALIZED_BY_UNCERTAINTY) {
				double ratio = mod.weight/orig.weight;
				System.out.println("\t"+orig.shortName+"\t"+targetName+": "+(float)stats.get(i).get(quantity)
					+";\tWeight = "+(float)orig.weight+" x "+(float)ratio+" = "+(float)mod.weight);
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

		NSHM23_InvConfigFactory factory = new NSHM23_InvConfigFactory();
		LogicTreeBranch<LogicTreeNode> branch = new NSHM23_U3_HybridLogicTreeBranch();
		branch.setValue(FaultModels.FM3_1);
//		branch.setValue(RupturePlausibilityModels.COULOMB);
		branch.setValue(RupturePlausibilityModels.UCERF3);
		
		// good fitting
		branch.setValue(U3_UncertAddDeformationModels.U3_ZENG);
		branch.setValue(ScalingRelationships.SHAW_2009_MOD);
		branch.setValue(SupraSeisBValues.B_0p8);
		branch.setValue(SlipAlongRuptureModels.UNIFORM);
		
		// poor fitting
//		branch.setValue(U3_UncertAddDeformationModels.U3_NEOK);
//		branch.setValue(ScalingRelationships.HANKS_BAKUN_08);
//		branch.setValue(SupraSeisBValues.B_0p0);
//		branch.setValue(SlipAlongRuptureModels.TAPERED);
		
		// constant
		branch.setValue(SubSeisMoRateReductions.SUB_B_1);
		
		// inv model
//		branch.setValue(SubSectConstraintModels.TOT_NUCL_RATE);
		branch.setValue(SubSectConstraintModels.NUCL_MFD);
		
		branch.setValue(SegmentationModels.SHAW_R0_3);
		
		dirName += "-"+branch.getValue(U3_UncertAddDeformationModels.class).getFilePrefix();
		dirName += "-"+branch.getValue(ScalingRelationships.class).getFilePrefix();
		dirName += "-"+branch.getValue(SlipAlongRuptureModels.class).getFilePrefix();
		dirName += "-"+branch.getValue(SupraSeisBValues.class).getFilePrefix();
		dirName += "-"+branch.getValue(SubSectConstraintModels.class).getFilePrefix();
		if (branch.hasValue(SegmentationModels.class))
			dirName += "-"+branch.getValue(SegmentationModels.class).getFilePrefix();
		
		FaultSystemRupSet rupSet;
		try {
			rupSet = factory.buildRuptureSet(branch, 32);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		System.out.println("Slip along: "+rupSet.getModule(SlipAlongRuptureModel.class).getName());
		
		InversionConfiguration config = factory.buildInversionConfig(rupSet, branch, 16);
		
//		boolean reweight = false;
		
		boolean reweight = true;
		dirName += "-reweight_"+QUANTITY_DEFAULT.name();
		
		if (reweight && CONSERVE_TOT_WEIGHT_DEFAULT)
			dirName += "-conserve";
		
//		boolean reweight = false;
		
//		CompletionCriteria completion = TimeCompletionCriteria.getInMinutes(10); dirName += "-10m-x1m";
//		CompletionCriteria avgCompletion = TimeCompletionCriteria.getInMinutes(1);

//		CompletionCriteria completion = TimeCompletionCriteria.getInMinutes(5); dirName += "-5m";
//		CompletionCriteria completion = TimeCompletionCriteria.getInHours(2); dirName += "-2h";
//		CompletionCriteria completion = TimeCompletionCriteria.getInHours(1); dirName += "-1h";
		CompletionCriteria completion = TimeCompletionCriteria.getInMinutes(20); dirName += "-20m";
//		CompletionCriteria completion = TimeCompletionCriteria.getInMinutes(30); dirName += "-30m";
//		CompletionCriteria avgCompletion = new IterationsPerVariableCompletionCriteria(20);
		CompletionCriteria avgCompletion = new IterationsPerVariableCompletionCriteria(50);

//		CompletionCriteria completion = TimeCompletionCriteria.getInMinutes(30); dirName += "-30m-x5m";
//		CompletionCriteria avgCompletion = TimeCompletionCriteria.getInMinutes(5);
		
//		CompletionCriteria completion = TimeCompletionCriteria.getInHours(1); dirName += "-1h-x1m";
//		CompletionCriteria avgCompletion = TimeCompletionCriteria.getInMinutes(1);
		
//		CompletionCriteria completion = TimeCompletionCriteria.getInHours(2); dirName += "-2h-x5m";
//		CompletionCriteria avgCompletion = TimeCompletionCriteria.getInMinutes(5);
		
//		CompletionCriteria completion = TimeCompletionCriteria.getInHours(2); dirName += "-2h-x1m";
//		CompletionCriteria avgCompletion = TimeCompletionCriteria.getInMinutes(1);
		
//		CompletionCriteria completion = TimeCompletionCriteria.getInHours(5); dirName += "-5h-x5m";
//		CompletionCriteria avgCompletion = TimeCompletionCriteria.getInMinutes(5);
		
		Builder builder = InversionConfiguration.builder(config);
		builder.completion(completion);
		if (avgCompletion != null)
			builder.avgThreads(4, avgCompletion);
		
//		builder.except(PaleoSlipInversionConstraint.class).except(PaleoRateInversionConstraint.class)
//			.except(ParkfieldInversionConstraint.class);
//		dirName += "-no_paleo_parkfield";
		
//		builder.except(SectionTotalRateConstraint.class);
//		dirName += "-no_sect";
		
//		builder.threads(6).noAvg();

//		builder.subCompletion(new IterationsPerVariableCompletionCriteria(1d));
		
		config = builder.build();
		
		InversionInputGenerator inputs = new InversionInputGenerator(rupSet, config);
		inputs.generateInputs(true);
		inputs.columnCompress();
		
		ProgressTrackingCompletionCriteria progress = new ProgressTrackingCompletionCriteria(completion);
		
		SimulatedAnnealing sa = config.buildSA(inputs);
		if (reweight) {
			Preconditions.checkState(sa instanceof ThreadedSimulatedAnnealing);
			sa = new ReweightEvenFitSimulatedAnnealing((ThreadedSimulatedAnnealing)sa);
		} else if (sa instanceof SerialSimulatedAnnealing) {
			sa.setConstraintRanges(null);
		}
		sa.setRandom(new Random(1234l));
		
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
		if (reweight)
			sol.addModule(((ReweightEvenFitSimulatedAnnealing)sa).getMisfitProgress());
		
		File outputDir = new File(parentDir, dirName);
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		try {
			sol.write(new File(outputDir, "solution.zip"));
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}

	private static final DecimalFormat fiveDigits = new DecimalFormat("0.00000");
	private static final DecimalFormat oDF = new DecimalFormat("0.##");

}
