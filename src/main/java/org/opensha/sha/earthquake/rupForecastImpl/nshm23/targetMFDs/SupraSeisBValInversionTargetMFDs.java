package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs;

import static org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools.*;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleUnaryOperator;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.Uncertainty;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SectBValuePlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SectionSupraSeisBValues;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SectNucleationMFD_Estimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SegmentationImpliedSectNuclMFD_Estimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.ThresholdAveragingSectNuclMFD_Estimator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SparseGutenbergRichterSolver;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Ints;

/**
 * {@link InversionTargetMFDs} implementation where targets are determined from deformation model slip rates and a target
 * supra-seismogenic b-value.
 * 
 * @author kevin
 *
 */
public class SupraSeisBValInversionTargetMFDs extends InversionTargetMFDs.Precomputed {
	
	public static final boolean D = false;
	
	/**
	 * Method for reduction target slip rates to account for sub-seismogenic ruptures.
	 * 
	 * @author kevin
	 *
	 */
	public enum SubSeisMoRateReduction {
		/**
		 * Fault-specific implied sub-seismogenic moment rate reduction. First, a target G-R is created with the
		 * supplied b-value and total deformation model moment rate (after any creep reductions). That target G-R
		 * is then partitioned into sub- and supra-seismogenic portions at the section minimum magnitude.
		 * 
		 * Target section slip rates in the rupture set will be overridden with these new targets.
		 */
		FAULT_SPECIFIC_IMPLIED_FROM_SUPRA_B,
		/**
		 * Same as FAULT_SPECIFIC_IMPLIED_FROM_SUPRA_B, except that the sub-seismogenic portion of the MFD will always
		 * have B=1
		 * 
		 * Target section slip rates in the rupture set will be overridden with these new targets.
		 */
		SUB_SEIS_B_1,
		/**
		 * Computes a system-wide average implied sub-seismogenic moment rate reduction, and applies it to each fault.
		 * 
		 * Target section slip rates in the rupture set will be overridden with these new targets.
		 */
		SYSTEM_AVG_IMPLIED_FROM_SUPRA_B,
		/**
		 * Computes a system-wide average implied sub-seismogenic moment rate reduction assuming sub-seis b=1, and applies it to each fault.
		 * 
		 * Target section slip rates in the rupture set will be overridden with these new targets.
		 */
		SYSTEM_AVG_SUB_B_1,
		/**
		 * Uses the target slip rates already attached to the rupture set as the final supra-seismogenic target
		 * slip rates.
		 */
		FROM_INPUT_SLIP_RATES,
		/**
		 * For faults with minimum magnitudes above M6.5, extend the supra-seismogenic b-value to M6.5 and reduce slip
		 * rates accordingly. The sub-seismogenic MFD down to M6.5 will be stored and should be added back in with the
		 * gridded seismicity model. No reduction for faults with minMag <= 6.5.
		 */
		SUPRA_B_TO_M6p5,
		/**
		 * Don't reduce slip rates for sub-seismogenic ruptures. In other words, assume that the creep-reduced
		 * deformation model slip rate is satisfied only by supra-seismogenic ruptures.
		 */
		NONE
	}
	
	public static SubSeisMoRateReduction SUB_SEIS_MO_RATE_REDUCTION_DEFAULT = SubSeisMoRateReduction.NONE;
	
	/**
	 * Default relative standard deviation as a function of magnitude: constant (10%)
	 */
	public static final DoubleUnaryOperator MAG_DEP_REL_STD_DEV_DEFAULT = M->0.1;
	
	/**
	 * Default choice for if we should propagate deformation model uncertainties to MFD uncertainties
	 */
	public static final boolean APPLY_DEF_MODEL_UNCERTAINTIES_DEFAULT = true;
	
	/**
	 * Default choice for if we should re-assign G-R rates in empty magnitude bins to neighboring bins with ruptures
	 */
	public static final boolean SPARSE_GR_DEFAULT = true;
	
	/**
	 * If true, then don't assign any moment from empty single-fault bins to multi-fault bins.
	 */
	public static boolean SPARSE_GR_DONT_SPREAD_SINGLE_TO_MULTI = true;
	
	/**
	 * Default choice for if we should artificially inflate uncertainties for magnitude bins for which few sections
	 * participate
	 */
	public static final boolean ADD_SECT_COUNT_UNCERTAINTIES_DEFAULT = false;
	
	/**
	 * Default choice for if we should use creep reduced (otherwise unreduced) slip rate standard deviations
	 */
	public static final boolean USE_CREEP_REDUCED_SLIP_STD_DEVS_DEFAULT = false;
	
	/**
	 * Maximum number of zero slip-rate fault subsections allowed per rupture. Excepting special cases for 2-subsection
	 * fault clusters, these zero slip-rate subsections must be in the interior of the rupture and each fault involved
	 * must have at least one nonzero slip-rate subsection.
	 */
	public static final int MAX_NUM_ZERO_SLIP_SECTS_PER_RUP = 1;
	
	private static final DecimalFormat twoDigits = new DecimalFormat("0.00");
	
	public static class Builder {
		
		private FaultSystemRupSet rupSet;
		private double supraSeisBValue;
		private double[] sectSpecificBValues;
		
		private IncrementalMagFreqDist totalTargetMFD;

		private SubSeisMoRateReduction subSeisMoRateReduction = SUB_SEIS_MO_RATE_REDUCTION_DEFAULT;
		private DoubleUnaryOperator magDepRelStdDev = MAG_DEP_REL_STD_DEV_DEFAULT;
		private boolean applyDefModelUncertainties = APPLY_DEF_MODEL_UNCERTAINTIES_DEFAULT;
		private boolean sparseGR = SPARSE_GR_DEFAULT;
		private boolean addSectCountUncertainties = ADD_SECT_COUNT_UNCERTAINTIES_DEFAULT;
		private boolean useCreepReducedSlipStdDevs = USE_CREEP_REDUCED_SLIP_STD_DEVS_DEFAULT;
		private int maxNumZeroSlipSectsPerRup = MAX_NUM_ZERO_SLIP_SECTS_PER_RUP;
		
		private double slipStdDevFloor = 0d;
		
		// if non-null, subset of ruptures that we're allowed to use
		private BitSet rupSubSet;
		
		// adjustments that affect the actual target MFD values themselves
		private List<SectNucleationMFD_Estimator> targetAdjDataConstraints;
		
		// adjustments that affect the target MFD uncertainties
		private List<? extends SectNucleationMFD_Estimator> uncertAdjDataConstraints;
		private UncertaintyBoundType uncertAdjDataTargetBound;
		
		private List<Region> constrainedRegions;

		public Builder(FaultSystemRupSet rupSet, SectionSupraSeisBValues bValues) {
			this.rupSet = rupSet;
			this.sectSpecificBValues = bValues.getSectBValues(rupSet);
			if (sectSpecificBValues == null)
				this.supraSeisBValue = bValues.getB();
			else
				this.supraSeisBValue = Double.NaN;
		}

		public Builder(FaultSystemRupSet rupSet, double supraSeisBValue) {
			this.rupSet = rupSet;
			this.supraSeisBValue = supraSeisBValue;
		}

		public Builder(FaultSystemRupSet rupSet, double[] sectSpecificBValues) {
			this.rupSet = rupSet;
			this.supraSeisBValue = Double.NaN;
			this.sectSpecificBValues = sectSpecificBValues;
		}
		
		/**
		 * Sets the sub-seismogenic moment rate reduction method.
		 * 
		 * @param subSeisMoRateReduction
		 * @return
		 */
		public Builder subSeisMoRateReduction(SubSeisMoRateReduction subSeisMoRateReduction) {
			this.subSeisMoRateReduction = subSeisMoRateReduction;
			return this;
		}
		
		/**
		 * Sets the default relative standard deviation as a constant relative fraction (no magnitude-dependence)
		 * 
		 * @param relStdDev
		 * @return
		 */
		public Builder constantDefaultRelStdDev(double relStdDev) {
			this.magDepRelStdDev = M->relStdDev;
			return this;
		}
		
		/**
		 * Sets the default relative standard deviation as a relative fraction of magnitude
		 * 
		 * @param magDepRelStdDevv
		 * @return
		 */
		public Builder magDepDefaultRelStdDev(DoubleUnaryOperator magDepRelStdDevv) {
			this.magDepRelStdDev = magDepRelStdDevv;
			return this;
		}
		
		/**
		 * Sets whether or not deformation model (slip rate) uncertainties should be used to determine target MFD
		 * uncertainties. The default uncertainty model will always be used, if true those defaults can be exceeded
		 * by the deformation model uncertainties, if applicable.
		 * 
		 * @param applyDefModelUncertainties
		 * @return
		 */
		public Builder applyDefModelUncertainties(boolean applyDefModelUncertainties) {
			this.applyDefModelUncertainties = applyDefModelUncertainties;
			return this;
		}
		
		/**
		 * If true, regional MFDs uncertainties will be increased in bins where few fault sections contain ruptures.
		 * That increase is a multiplicative scalar: binUncert = binUncert*sqrt(numSects)/sqrt(numSectsInBin)
		 * 
		 * @param applyDefModelUncertainties
		 * @return
		 */
		public Builder addSectCountUncertainties(boolean addSectCountUncertainties) {
			this.addSectCountUncertainties = addSectCountUncertainties;
			return this;
		}
		
		/**
		 * Sets whether or not supra-seismogenic G-R MFDs should be adjusted to account for magnitude bins without any
		 * ruptures available (rates redistributed to neighboring bins, preserving total moment)
		 * 
		 * @param sparseGR
		 * @return
		 */
		public Builder sparseGR(boolean sparseGR) {
			this.sparseGR = sparseGR;
			return this;
		}
		
		/**
		 * Sets whether or not slip rate standard deviations should be multiplied by the slip rate coupling-coefficient
		 * to reduce them for creep.
		 * @param useCreepReducedSlipStdDevs
		 * @return
		 */
		public Builder useCreepReducedSlipStdDevs(boolean useCreepReducedSlipStdDevs) {
			this.useCreepReducedSlipStdDevs = useCreepReducedSlipStdDevs;
			return this;
		}
		
		/**
		 * Sets the maximum number of zero slip-rate fault subsections allowed per rupture. Excepting special cases for
		 * 2-subsection fault clusters, these zero slip-rate subsections must be in the interior of the rupture and each
		 * fault involved must have at least one nonzero slip-rate subsection.
		 * 
		 * Set to {@link Integer#MAX_VALUE} to allow all ruptures regardless of the number of single fault sections
		 * 
		 * @param useRupsOnZeroSlipSects
		 * @return
		 */
		public Builder maxNumZeroSlipSectsPerRup(int maxNumZeroSlipSectsPerRup) {
			Preconditions.checkArgument(maxNumZeroSlipSectsPerRup >= 0);
			this.maxNumZeroSlipSectsPerRup = maxNumZeroSlipSectsPerRup;
			return this;
		}
		
		/**
		 * Sets a minimum (floor) value for slip rate standard deviations in mm/yr
		 * @param slipStdDevFloor minimum slip rate standard deviation, in mm/yr
		 * @return
		 */
		public Builder slipStdDevFloor(double slipStdDevFloor) {
			this.slipStdDevFloor = slipStdDevFloor;
			return this;
		}
		
		public Builder forBinaryRupProbModel(BinaryRuptureProbabilityCalc binaryRupProb) {
			return forBinaryRupProbModel(binaryRupProb, true);
		}
		
		public Builder forBinaryRupProbModel(BinaryRuptureProbabilityCalc binaryRupProb, boolean replaceExisting) {
			BitSet rupSubSet = new BitSet(rupSet.getNumRuptures());
			ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
			System.out.println("Processing "+rupSet.getNumRuptures()+" ruptures for binary rupture probability calculation");
			for (int r=0; r<rupSet.getNumRuptures(); r++) {
				ClusterRupture rup = cRups.get(r);
				if (binaryRupProb.isRupAllowed(rup, false))
					rupSubSet.set(r);
			}
			return forRupSubSet(rupSubSet, replaceExisting);
		}
		
		/**
		 * It non-null, specifies a subset of ruptures that we are allowed to use when determining MFD targets. Will
		 * replace any existing rupture subset.
		 * 
		 * @param rupSubSet
		 * @return
		 */
		public Builder forRupSubSet(BitSet rupSubSet) {
			return forRupSubSet(rupSubSet, true);
		}
		
		/**
		 * It non-null, specifies a subset of ruptures that we are allowed to use when determining MFD targets.
		 * 
		 * @param rupSubSet
		 * @param replaceExisting if true and a previous rupture subset has been set, then only ruptures common to both
		 * will be retained, unless the given subset is null in which case everything will be cleared
		 * @return
		 */
		public Builder forRupSubSet(BitSet rupSubSet, boolean replaceExisting) {
			if (!replaceExisting && rupSubSet != null && this.rupSubSet != null)
				this.rupSubSet.and(rupSubSet);
			else
				this.rupSubSet = rupSubSet;
			return this;
		}
		
		/**
		 * Adjust target subsection nucleation MFDs to for the given data constraints.
		 * 
		 * @param dataConstraint
		 * @return
		 */
		public Builder adjustTargetsForData(SectNucleationMFD_Estimator dataConstraint) {
			if (this.targetAdjDataConstraints == null)
				this.targetAdjDataConstraints = new ArrayList<>();
			targetAdjDataConstraints.add(dataConstraint);
			return this;
		}
		
		public Builder clearTargetAdjustments() {
			this.targetAdjDataConstraints = null;
			return this;
		}
		
		/**
		 * Expand uncertainties (both on individual sections and on regional targets) such that the given data constraints
		 * are no more than boundType away from the assumed supra-seismogenic MFD.
		 * 
		 * @param dataConstraints
		 * @param boundType
		 * @return
		 */
		public Builder expandUncertaintiesForData(List<? extends SectNucleationMFD_Estimator> dataConstraints,
				UncertaintyBoundType boundType) {
			this.uncertAdjDataConstraints = dataConstraints;
			this.uncertAdjDataTargetBound = boundType;
			return this;
		}
		
		/**
		 * Sets the total regional target MFD, currently only used in plots, it does not affect calculated target
		 * supra-seismogenic MFDs.
		 * 
		 * @param totalTargetMFD
		 * @return
		 */
		public Builder totalTargetMFD(IncrementalMagFreqDist totalTargetMFD) {
			this.totalTargetMFD = totalTargetMFD;
			return this;
		}
		
		/**
		 * Build target slip rates only
		 * 
		 * @return
		 */
		public SectSlipRates buildSlipRatesOnly() {
			return build(true).sectSlipRates;
		}
		
		/**
		 * Build target MFDs with the supplied settings
		 * 
		 * @return
		 */
		public SupraSeisBValInversionTargetMFDs build() {
			return build(false);
		}
		
		/**
		 * Build target MFDs with the supplied settings
		 * 
		 * @return
		 */
		private SupraSeisBValInversionTargetMFDs build(boolean slipOnly) {
			EvenlyDiscretizedFunc refMFD = initEmptyMFD(rupSet);
			int NUM_MAG = refMFD.size();
			String bString;
			if (sectSpecificBValues == null) {
				bString = "b="+(float)supraSeisBValue;
			} else {
				MinMaxAveTracker bStats = new MinMaxAveTracker();
				for (double b : sectSpecificBValues)
					bStats.addValue(b);
				bString = "section-specific b (avg="+twoDigits.format(bStats.getAverage())
					+", range=["+twoDigits.format(bStats.getMin())+", "+twoDigits.format(bStats.getMax())+"]"
					+ ", N="+sectSpecificBValues.length+")";
			}
			System.out.println("Building SupraSeisBValInversionTargetMFDs with "+bString
					+", slipOnly="+slipOnly+", total MFD range: ["+(float)MIN_MAG+","+(float)refMFD.getMaxX()
					+"] for maxMag="+rupSet.getMaxMag());
			
			ModSectMinMags minMags = rupSet.getModule(ModSectMinMags.class);
			
			int numSects = rupSet.getNumSections();
			
			BitSet zeroRateAllowed = null;
			BitSet highDMZeroRateAllowed = null;
			if (maxNumZeroSlipSectsPerRup < Integer.MAX_VALUE) {
				boolean[] zeroRateSects = new boolean[rupSet.getNumSections()];
				boolean[] highDMZeroRateSects = applyDefModelUncertainties ? new boolean[zeroRateSects.length] : null;
				int numZeroSects = 0;
				for (int s=0; s<zeroRateSects.length; s++) {
					FaultSection sect = rupSet.getFaultSectionData(s);
					double slipRate = sect.getReducedAveSlipRate();
					double slipSD = useCreepReducedSlipStdDevs ? sect.getReducedSlipRateStdDev() : sect.getOrigSlipRateStdDev();
					if (slipRate == 0d) {
						// it's a zero rate section
						numZeroSects++;
						zeroRateSects[s] = true;
						if (applyDefModelUncertainties && Double.isNaN(slipSD) || slipSD == 0)
							highDMZeroRateSects[s] = true;
					}
				}
				
				zeroRateAllowed = SlipRateAllowedRupsFinder.calcAllowedRuptures(
						rupSet, zeroRateSects, rupSubSet, maxNumZeroSlipSectsPerRup);
				if (applyDefModelUncertainties)
					highDMZeroRateAllowed = SlipRateAllowedRupsFinder.calcAllowedRuptures(
							rupSet, highDMZeroRateSects, rupSubSet, maxNumZeroSlipSectsPerRup);
			}
			
			ExecutorService exec = null;
			if (targetAdjDataConstraints != null || uncertAdjDataConstraints != null)
				exec = Executors.newFixedThreadPool(defaultNumThreads());
			
			SectMFDCalculator calc = new SectMFDCalculator();
			calc.calc(exec, refMFD, minMags, zeroRateAllowed, slipOnly, 0d);
			// give the newly computed target slip rates to the rupture set for use in inversions
			if (subSeisMoRateReduction != SubSeisMoRateReduction.FROM_INPUT_SLIP_RATES)
				rupSet.addModule(calc.sectSlipRates);
			if (slipOnly)
				return new SupraSeisBValInversionTargetMFDs(rupSet, supraSeisBValue, sectSpecificBValues,
						null, null, null, null, null, null, calc.sectSlipRates, null);
			
			SectMFDCalculator dmLowerCalc = null;
			SectMFDCalculator dmUpperCalc = null;
			if (applyDefModelUncertainties) {
				// redo the clculation using high/low slip rates
				System.out.println("Re-calculating MFDs using low slip rates");
				dmLowerCalc = new SectMFDCalculator();
				// don't zero out any sections where slip - sd is zero
				dmLowerCalc.calc(exec, refMFD, minMags, zeroRateAllowed, false, -1d);
				// for upper, use rups on zero rate+sd sections
				System.out.println("Re-calculating MFDs using high slip rates");
				dmUpperCalc = new SectMFDCalculator();
				// do add back in section wher slip=0 but sd > 0
				dmUpperCalc.calc(exec, refMFD, minMags, highDMZeroRateAllowed, false, 1d);
				System.out.println("Done with DM high/low MFD estimation");
			}
			
			List<EvenlyDiscretizedFunc> defModMFDStdDevs = null;
			if (applyDefModelUncertainties) {
				defModMFDStdDevs = new ArrayList<>();
				for (int s=0; s<numSects; s++) {
					EvenlyDiscretizedFunc defModStdDevs = null;
					if (calc.slipRateStdDevs[s] > 0d) {
						IncrementalMagFreqDist upperMFD = dmUpperCalc.sectSupraSeisMFDs.get(s);
						IncrementalMagFreqDist lowerMFD = dmLowerCalc.sectSupraSeisMFDs.get(s);
						
						defModStdDevs = new EvenlyDiscretizedFunc(MIN_MAG, NUM_MAG, DELTA_MAG);
						for (int i=0; i<NUM_MAG; i++) {
							double upper = upperMFD.getY(i);
							if (upper == 0d)
								continue;
							double lower = lowerMFD.getY(i);
							
							// these are +/- 1 sigma bounds, so sigma is (upper - lower)/2
							double sd = (upper - lower) / 2d;
							defModStdDevs.set(i, sd);
						}
					}
					defModMFDStdDevs.add(defModStdDevs);
				}
			}
			
			List<IncrementalMagFreqDist> dataImpliedSectSupraSeisMFDs = null;
			if (uncertAdjDataConstraints != null) {
				Preconditions.checkNotNull(uncertAdjDataTargetBound);
				
				dataImpliedSectSupraSeisMFDs = new ArrayList<>();
				
				for (int s=0; s<numSects; s++)
					dataImpliedSectSupraSeisMFDs.add(null);
				
				for (SectNucleationMFD_Estimator estimator : uncertAdjDataConstraints)
					estimator.init(rupSet, calc.sectSupraSeisMFDs, calc.targetSupraMoRates, calc.slipRates, calc.slipRateStdDevs,
							calc.sectRupUtilizations, calc.sectMinMagIndexes, calc.sectMaxMagIndexes, calc.sectRupInBinCounts, refMFD);
				
				List<Future<DataEstCallable>> futures = estimateSectNuclMFDs(uncertAdjDataConstraints, calc.slipRates,
						calc.slipRateStdDevs, calc.targetSupraMoRates, calc.sectRupUtilizations, calc.sectSupraSeisMFDs, exec);
				
				for (Future<DataEstCallable> future : futures) {
					DataEstCallable call;
					try {
						call = future.get();
					} catch (InterruptedException | ExecutionException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					int sectID = call.sect.getSectionId();
					Preconditions.checkState(dataImpliedSectSupraSeisMFDs.get(sectID) == null);
					dataImpliedSectSupraSeisMFDs.set(sectID, call.impliedMFD);
				}
				
				System.out.println("Done with data estimation.");
			}
			
			UncertainIncrMagFreqDist totalOnFaultSupra = calcRegionalSupraTarget(refMFD, null, calc.sectSupraSeisMFDs,
					defModMFDStdDevs, dataImpliedSectSupraSeisMFDs);
			
			List<UncertainIncrMagFreqDist> mfdConstraints;
			if (constrainedRegions != null) {
				Preconditions.checkState(!constrainedRegions.isEmpty(), "Empty region list supplied");
				mfdConstraints = new ArrayList<>();
				for (Region region : constrainedRegions)
					mfdConstraints.add(calcRegionalSupraTarget(refMFD, region, calc.sectSupraSeisMFDs,
							defModMFDStdDevs, dataImpliedSectSupraSeisMFDs));
			} else {
				mfdConstraints = List.of(totalOnFaultSupra);
			}
			
			// now set individual supra-seis MFD uncertainties
			List<UncertainIncrMagFreqDist> uncertSectSupraSeisMFDs = new ArrayList<>();
			for (int s=0; s<numSects; s++) {
				IncrementalMagFreqDist sectSupraMFD = calc.sectSupraSeisMFDs.get(s);
				
				EvenlyDiscretizedFunc stdDevs = new EvenlyDiscretizedFunc(MIN_MAG, sectSupraMFD.size(), DELTA_MAG);
				for (int i=0; i<stdDevs.size(); i++)
					stdDevs.set(i, sectSupraMFD.getY(i)*magDepRelStdDev.applyAsDouble(stdDevs.getX(i)));
				
				if (applyDefModelUncertainties) {
					EvenlyDiscretizedFunc defModStdDevs = defModMFDStdDevs.get(s);
					if (defModStdDevs != null) {
						for (int i=0; i<stdDevs.size(); i++)
							stdDevs.set(i, Math.max(stdDevs.getY(i), defModStdDevs.getY(i)));
					}
				}
				
				UncertainIncrMagFreqDist uncertainSectSupraMFD = new UncertainIncrMagFreqDist(sectSupraMFD, stdDevs);
				
				if (dataImpliedSectSupraSeisMFDs != null) {
					IncrementalMagFreqDist impliedMFD = dataImpliedSectSupraSeisMFDs.get(s);
					if (impliedMFD != null) {
						if (D) System.out.println("Adjusting MFD for s="+s+" to account for data constraints");
						uncertainSectSupraMFD = adjustForDataImpliedBounds(uncertainSectSupraMFD, impliedMFD, false);
					}
				}
				uncertSectSupraSeisMFDs.add(uncertainSectSupraMFD);
			}
			
			SummedMagFreqDist totalOnFaultSub;
			SubSeismoOnFaultMFDs subSeismoMFDs;
			if (subSeisMoRateReduction == SubSeisMoRateReduction.NONE) {
				totalOnFaultSub = null;
				subSeismoMFDs = null;
			} else {
				// sum of sub-seismo target
				totalOnFaultSub = new SummedMagFreqDist(MIN_MAG, refMFD.size(), DELTA_MAG);
				for (IncrementalMagFreqDist subSeisMFD : calc.sectSubSeisMFDs)
					totalOnFaultSub.addIncrementalMagFreqDist(subSeisMFD);
				
				subSeismoMFDs = new SubSeismoOnFaultMFDs(calc.sectSubSeisMFDs);
			}
			
			// standard deviations of those MFDs inferred from deformation model uncertainties
//			List<EvenlyDiscretizedFunc> defModMFDStdDevs = applyDefModelUncertainties ? new ArrayList<>() : null;
//			
			/*
			 * private SupraSeisBValInversionTargetMFDs(FaultSystemRupSet rupSet, double supraSeisBValue,
			IncrementalMagFreqDist totalRegionalMFD, UncertainIncrMagFreqDist onFaultSupraSeisMFD,
			IncrementalMagFreqDist onFaultSubSeisMFD, List<UncertainIncrMagFreqDist> mfdConstraints,
			SubSeismoOnFaultMFDs subSeismoOnFaultMFDs, List<UncertainIncrMagFreqDist> supraSeismoOnFaultMFDs) {
			 */
			
			if (exec != null)
				exec.shutdown();
			
			return new SupraSeisBValInversionTargetMFDs(rupSet, supraSeisBValue, sectSpecificBValues, totalTargetMFD, totalOnFaultSupra,
					totalOnFaultSub, mfdConstraints, subSeismoMFDs, uncertSectSupraSeisMFDs, calc.sectSlipRates, calc.sectRupUtilizations);
		}
		
		private class SectMFDCalculator {
			
			private double[] slipRates;
			private double[] slipRateStdDevs;
			private double[] targetMoRates;
			private double[] targetSupraMoRates;
			private double[] sectFractSupras;
			private List<BitSet> sectRupUtilizations;
			private MinMaxAveTracker fractSuprasTrack;
			private List<IncrementalMagFreqDist> sectSubSeisMFDs;
			private List<IncrementalMagFreqDist> sectSupraSeisMFDs;
			private int[][] sectRupInBinCounts;
			private int[] sectMinMagIndexes;
			private int[] sectMaxMagIndexes;
			
			SectSlipRates sectSlipRates;

			public void calc(ExecutorService exec, EvenlyDiscretizedFunc refMFD, ModSectMinMags minMags,
					BitSet zeroRateAllowed, boolean slipOnly, double slipAddStdScalar) {
				int numSects = rupSet.getNumSections();
				int NUM_MAG = refMFD.size();
				
				slipRates = new double[numSects];
				slipRateStdDevs = new double[numSects];
				targetMoRates = new double[numSects];
				targetSupraMoRates = new double[numSects];
				sectFractSupras = new double[numSects];
				
				sectRupUtilizations = new ArrayList<>();
				
				fractSuprasTrack = new MinMaxAveTracker();
				
				sectSubSeisMFDs = new ArrayList<>();
				
				sectSupraSeisMFDs = new ArrayList<>();
				
				sectRupInBinCounts = new int[numSects][refMFD.size()];
				sectMinMagIndexes = new int[numSects];
				sectMaxMagIndexes = new int[numSects];
				
				if (sectSpecificBValues != null)
					Preconditions.checkState(sectSpecificBValues.length == rupSet.getNumSections());
				
				// first, calculate sub and supra-seismogenic G-R MFDs
				for (int s=0; s<numSects; s++) {
					FaultSection sect = rupSet.getFaultSectionData(s);
					int parentID = sect.getParentSectionId();
					
					double supraSeisBValue;
					if (sectSpecificBValues == null)
						supraSeisBValue = Builder.this.supraSeisBValue;
					else
						supraSeisBValue = sectSpecificBValues[s];
					Preconditions.checkState(Double.isFinite(supraSeisBValue), "Bad b=%s for section %s. %s",
							supraSeisBValue, s, sect.getSectionName());

					double creepReducedSlipRate = sect.getReducedAveSlipRate()*1e-3; // mm/yr -> m/yr
					double creepReducedSlipRateStdDev;
					if (useCreepReducedSlipStdDevs)
						creepReducedSlipRateStdDev = sect.getReducedSlipRateStdDev()*1e-3; // mm/yr -> m/yr
					else
						creepReducedSlipRateStdDev = sect.getOrigSlipRateStdDev()*1e-3; // mm/yr -> m/yr
					
					Preconditions.checkState(Double.isFinite(creepReducedSlipRate), "Bad slip rate for %s. %s: %s",
							s, sect.getSectionName(), creepReducedSlipRate);
					
					if (slipAddStdScalar != 0d)
						// for calculating mfds +/- std dev
						creepReducedSlipRate += slipAddStdScalar*creepReducedSlipRateStdDev;

					double area = rupSet.getAreaForSection(s); // m^2

					// convert it to a moment rate
					double targetMoRate = FaultMomentCalc.getMoment(area, creepReducedSlipRate);

					// supra-seismogenic minimum magnitude
					double sectMinMag = rupSet.getMinMagForSection(s);

					if (minMags != null) {
						// not below the section minimum magnitude
						sectMinMag = Math.max(sectMinMag, minMags.getMinMagForSection(s));
					}

					List<Double> mags = new ArrayList<>();
					List<Integer> rups = new ArrayList<>();
					// section minimum magnitude may be below the actual minimum magnitude, check that here
					double minAbove = Double.POSITIVE_INFINITY;
					// also, if we have a segmentation model, use the max mag that has a nonzero probability
					double sectMaxMag = Double.NEGATIVE_INFINITY;
					// max single-fault mag
					double sectMaxSingleFaultMag = Double.NEGATIVE_INFINITY;
					BitSet utilization = new BitSet(rupSet.getNumRuptures());
					int numMinMagExcluded = 0;
					int numSubsetExcluded = 0;
					int numZeroRateExcluded = 0;
					for (int r : rupSet.getRupturesForSection(s)) {
						double mag = rupSet.getMagForRup(r);
						if (minMags == null || !minMags.isBelowSectMinMag(s, mag, refMFD)) {
							if (rupSubSet != null && !rupSubSet.get(r)) {
								// not allowed to use this rupture, skip
								numSubsetExcluded++;
								continue;
							}
							if (zeroRateAllowed != null && !zeroRateAllowed.get(r)) {
								numZeroRateExcluded++;
								continue;
							}
							minAbove = Math.min(mag, minAbove);
							sectMaxMag = Math.max(sectMaxMag, mag);
							mags.add(mag);
							rups.add(r);
							utilization.set(r);
							sectRupInBinCounts[s][refMFD.getClosestXIndex(mag)]++;
							boolean singleFault = true;
							for (int rupSect : rupSet.getSectionsIndicesForRup(r)) {
								if (rupSet.getFaultSectionData(rupSect).getParentSectionId() != parentID) {
									singleFault = false;
									break;
								}
							}
							if (singleFault)
								sectMaxSingleFaultMag = Math.max(sectMaxSingleFaultMag, mag);
						} else {
							numMinMagExcluded++;
						}
					}
					sectRupUtilizations.add(utilization);
					
//					if (s == 1745) {
//						System.out.println("DEBUG FOR "+s+". "+sect.getSectionName());
//						System.out.println("Creep-reduced slip rate: "+creepReducedSlipRate);
//						System.out.println("Rups: "+rups.size());
//						System.out.println("Min mags? "+(minMags == null ? "null" : (float)minMags.getMinMagForSection(s)));
//						System.out.println("rupsOnZeroRateParents == null ? "+(rupsOnZeroRateParents == null));
//					}

					// this will contain the shape of the supra-seismogenic G-R distribution for this section, but the
					// actual a value of this MFD will be set later
					IncrementalMagFreqDist supraGR_shape;
					int minMagIndex, maxMagIndex;
					if (rups.isEmpty()) {
						if (creepReducedSlipRate > 0d && slipAddStdScalar == 0d)
							// only print warning if we have a slip rate on this section
							System.err.println("WARNING: Section "+s+" has no ruptures: "+sect.getName()
									+"; exclusions: "+numMinMagExcluded+" for minMag="+(float)sectMinMag
									+", "+numSubsetExcluded+" for rupture subset, "
									+numZeroRateExcluded+" for using zero-rate subsections");
						minMagIndex = refMFD.getClosestXIndex(sectMinMag);
						maxMagIndex = minMagIndex;
						supraGR_shape = new IncrementalMagFreqDist(minMagIndex, 1, refMFD.getDelta());
					} else {
						sectMinMag = minAbove;

						minMagIndex = refMFD.getClosestXIndex(sectMinMag);
						maxMagIndex = refMFD.getClosestXIndex(sectMaxMag);

						// create a supra-seismogenic MFD between these two mags
						// we won't bother setting the a-value here, this is only the relative shape consistent with:
						// * chosen b-value
						// * if sparseGR == true, deals with intermediate bins with no ruptures
						// 1e16 below is a placeholder moment and doesn't matter, we're only using the shape
						// 
						// the first bin in the MFD is the min section supra-seis mag
						supraGR_shape = new GutenbergRichterMagFreqDist(refMFD.getX(minMagIndex),
								1+maxMagIndex-minMagIndex, refMFD.getDelta(), 1e16, supraSeisBValue);

						if (sparseGR) {
							// re-distribute to only bins that actually have ruptures available
							List<Double> groupBinEdges = null;
							if (SPARSE_GR_DONT_SPREAD_SINGLE_TO_MULTI && Double.isFinite(sectMaxSingleFaultMag)) {
								int singleBin = refMFD.getClosestXIndex(sectMaxSingleFaultMag);
								if (singleBin < maxMagIndex) {
									double binEdge = refMFD.getX(singleBin)+0.5*refMFD.getDelta();
//									if (s == 1261) {
//										System.out.println("Bin edge is "+(float)binEdge+" for maxSingle="+(float)sectMaxSingleFaultMag);
//										SparseGutenbergRichterSolver.D = true;
//									}
									groupBinEdges = List.of(binEdge);
								}
							}
							supraGR_shape = SparseGutenbergRichterSolver.getEquivGR(supraGR_shape, mags,
									groupBinEdges, true, supraGR_shape.getTotalMomentRate(), supraSeisBValue);
							SparseGutenbergRichterSolver.D = false;
						}
					}

					sectMinMagIndexes[s] = minMagIndex;
					sectMaxMagIndexes[s] = maxMagIndex;

					IncrementalMagFreqDist subSeisMFD, supraSeisMFD;
					double supraMoRate, subMoRate, fractSupra;

					// actual method for determining the MFDs depends on our sub-seismogenic moment rate reduction method
					if (targetMoRate == 0d) {
						supraMoRate = 0d;
						subMoRate = 0d;
						fractSupra = 1d;
						supraSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
						subSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);

						slipRates[s] = creepReducedSlipRate;
						slipRateStdDevs[s] = creepReducedSlipRateStdDev;
					} else if (rups.isEmpty()) {
						// no supra-seismogenic ruptures available, assign everything to sub-seismo
						supraMoRate = 0d;
						subMoRate = targetMoRate;
						fractSupra = 0d;
						supraSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);

						double subB = subSeisMoRateReduction == SubSeisMoRateReduction.SUB_SEIS_B_1 ? 1d : supraSeisBValue;

						GutenbergRichterMagFreqDist grToMin = new GutenbergRichterMagFreqDist(
								refMFD.getMinX(), minMagIndex, refMFD.getDelta(), targetMoRate, subB);
						subSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
						for (int i=0; i<grToMin.size(); i++)
							subSeisMFD.set(i, grToMin.getY(i));

						slipRates[s] = 0d; // supra portion is zero
						slipRateStdDevs[s] = creepReducedSlipRateStdDev;
					} else if (subSeisMoRateReduction == SubSeisMoRateReduction.FROM_INPUT_SLIP_RATES) {
						// this one just uses the input target slip rates as the supra-seismogenic
						// anything leftover from the fault section's slip rate is given to sub-seismogenic
						SectSlipRates inputSlipRates = rupSet.requireModule(SectSlipRates.class);

						double supraSlipRate = inputSlipRates.getSlipRate(s); // m/yr
						double supraSlipStdDev = inputSlipRates.getSlipRateStdDev(s); // m/yr

						fractSupra = supraSlipRate/creepReducedSlipRate;
						Preconditions.checkState(fractSupra > 0d && fractSupra <= 1d);

						supraMoRate = targetMoRate*fractSupra;
						subMoRate = targetMoRate-supraMoRate;

						// use supra-seis MFD shape from above
						supraGR_shape.scaleToTotalMomentRate(supraMoRate);

						supraSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
						for (int i=0; i<supraGR_shape.size(); i++)
							supraSeisMFD.set(i+minMagIndex, supraGR_shape.getY(i));

						GutenbergRichterMagFreqDist subGR = new GutenbergRichterMagFreqDist(
								refMFD.getX(0), minMagIndex, DELTA_MAG, subMoRate, supraSeisBValue);
						subSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
						for (int i=0; i<subGR.size(); i++)
							subSeisMFD.set(i, subGR.getY(i));
						// scale target slip rates by the fraction that is supra-seismognic
						slipRates[s] = supraSlipRate;
						slipRateStdDevs[s] = supraSlipStdDev;
					} else if (subSeisMoRateReduction == SubSeisMoRateReduction.NONE) {
						// apply everything to supra-seismogenic, no sub-seismogenic

						supraMoRate = targetMoRate;
						subMoRate = 0d;
						fractSupra = 1d;

						// only supra-seis MFD
						subSeisMFD = null;

						// use supra-seis MFD shape from above
						supraGR_shape.scaleToTotalMomentRate(supraMoRate);

						supraSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
						for (int i=0; i<supraGR_shape.size(); i++)
							supraSeisMFD.set(i+minMagIndex, supraGR_shape.getY(i));

						// scale target slip rates by the fraction that is supra-seismognic
						slipRates[s] = creepReducedSlipRate*fractSupra;
						slipRateStdDevs[s] = creepReducedSlipRateStdDev*fractSupra;
					} else if (subSeisMoRateReduction == SubSeisMoRateReduction.SUB_SEIS_B_1
							|| subSeisMoRateReduction == SubSeisMoRateReduction.SYSTEM_AVG_SUB_B_1) {
						// start with a full G-R with the supra b-value up to the maximum magnitude
						GutenbergRichterMagFreqDist fullSupraB = new GutenbergRichterMagFreqDist(
								MIN_MAG, maxMagIndex+1, DELTA_MAG, targetMoRate, supraSeisBValue);

						// copy it to a regular MFD:
						IncrementalMagFreqDist sectFullMFD = new IncrementalMagFreqDist(MIN_MAG, maxMagIndex+1, DELTA_MAG);
						for (int i=0; i<fullSupraB.size(); i++)
							sectFullMFD.set(i, fullSupraB.getY(i));

						// now correct the sub-seis portion to have the sub-seis b-value

						// first create a full MFD with the sub b-value. this will only be used in a relative sense
						GutenbergRichterMagFreqDist fullSubB = new GutenbergRichterMagFreqDist(
								MIN_MAG, maxMagIndex+1, DELTA_MAG, targetMoRate, 1d); // b=1

						double targetFirstSupra = fullSupraB.getY(minMagIndex);
						double subFirstSupra = fullSubB.getY(minMagIndex);
						for (int i=0; i<minMagIndex; i++) {
							double targetRatio = fullSubB.getY(i)/subFirstSupra;
							sectFullMFD.set(i, targetFirstSupra*targetRatio);
						}

						// rescale to match the original moment rate
						sectFullMFD.scaleToTotalMomentRate(targetMoRate);

						// split the target G-R into sub-seismo and supra-seismo parts
						subSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
						for (int i=0; i<minMagIndex; i++)
							subSeisMFD.set(i, sectFullMFD.getY(i));

						subMoRate = subSeisMFD.getTotalMomentRate();
						supraMoRate = targetMoRate - subMoRate;

						// use supra-seis MFD shape from above
						supraGR_shape.scaleToTotalMomentRate(supraMoRate);

						supraSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
						for (int i=0; i<supraGR_shape.size(); i++)
							supraSeisMFD.set(i+minMagIndex, supraGR_shape.getY(i));

						fractSupra = supraMoRate/targetMoRate;

						// scale target slip rates by the fraction that is supra-seismognic
						slipRates[s] = creepReducedSlipRate*fractSupra;
						slipRateStdDevs[s] = creepReducedSlipRateStdDev*fractSupra;
					} else if (subSeisMoRateReduction == SubSeisMoRateReduction.SUPRA_B_TO_M6p5) {
						int sixFiveIndex = refMFD.getClosestXIndex(6.501); // want it to round up
						if (minMagIndex <= sixFiveIndex) {
							// no reduction
							supraMoRate = targetMoRate;
							subMoRate = 0d;
							fractSupra = 1d;

							// only supra-seis MFD
							subSeisMFD = null;

							// use supra-seis MFD shape from above
							supraGR_shape.scaleToTotalMomentRate(supraMoRate);

							supraSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
							for (int i=0; i<supraGR_shape.size(); i++)
								supraSeisMFD.set(i+minMagIndex, supraGR_shape.getY(i));

							// scale target slip rates by the fraction that is supra-seismognic
							slipRates[s] = creepReducedSlipRate*fractSupra;
							slipRateStdDevs[s] = creepReducedSlipRateStdDev*fractSupra;
						} else {
							// GR from 6.5 to Mmax with supra-seis b-value
							GutenbergRichterMagFreqDist sectFullMFD = new GutenbergRichterMagFreqDist(
									MIN_MAG, maxMagIndex+1, DELTA_MAG);
							sectFullMFD.setAllButTotCumRate(refMFD.getX(sixFiveIndex),
									refMFD.getX(maxMagIndex), targetMoRate, supraSeisBValue);
							
							// split the target G-R into sub-seismo and supra-seismo parts
							subSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
							for (int i=sixFiveIndex; i<minMagIndex; i++)
								subSeisMFD.set(i, sectFullMFD.getY(i));

							subMoRate = subSeisMFD.getTotalMomentRate();
							supraMoRate = targetMoRate - subMoRate;
							fractSupra = supraMoRate/targetMoRate;

							// use supra-seis MFD shape from above
							supraGR_shape.scaleToTotalMomentRate(supraMoRate);

							supraSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
							for (int i=0; i<supraGR_shape.size(); i++)
								supraSeisMFD.set(i+minMagIndex, supraGR_shape.getY(i));

							// scale target slip rates by the fraction that is supra-seismognic
							slipRates[s] = creepReducedSlipRate*fractSupra;
							slipRateStdDevs[s] = creepReducedSlipRateStdDev*fractSupra;
						}
					} else {
						// use that implied by faults maximum magnitude
						// will adjust later if system-wide avg is selected
						Preconditions.checkState(
								subSeisMoRateReduction == SubSeisMoRateReduction.FAULT_SPECIFIC_IMPLIED_FROM_SUPRA_B
								|| subSeisMoRateReduction == SubSeisMoRateReduction.SYSTEM_AVG_IMPLIED_FROM_SUPRA_B);

						IncrementalMagFreqDist sectFullMFD = new GutenbergRichterMagFreqDist(
								MIN_MAG, maxMagIndex+1, DELTA_MAG, targetMoRate, supraSeisBValue);

						// split the target G-R into sub-seismo and supra-seismo parts
						subSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
						for (int i=0; i<minMagIndex; i++)
							subSeisMFD.set(i, sectFullMFD.getY(i));

						subMoRate = subSeisMFD.getTotalMomentRate();
						supraMoRate = targetMoRate - subMoRate;
						fractSupra = supraMoRate/targetMoRate;

						// use supra-seis MFD shape from above
						supraGR_shape.scaleToTotalMomentRate(supraMoRate);

						supraSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
						for (int i=0; i<supraGR_shape.size(); i++)
							supraSeisMFD.set(i+minMagIndex, supraGR_shape.getY(i));

						// scale target slip rates by the fraction that is supra-seismognic
						slipRates[s] = creepReducedSlipRate*fractSupra;
						slipRateStdDevs[s] = creepReducedSlipRateStdDev*fractSupra;
					}

					slipRateStdDevs[s] = Math.max(slipRateStdDevs[s], slipStdDevFloor*1e-3); // mm/yr -> m/yr

					double targetMoRateTest = supraMoRate + subMoRate;

					Preconditions.checkState((float)targetMoRateTest == (float)targetMoRate,
							"Partitioned moment rate doesn't equal input: %s != %s",
							(float)targetMoRate, (float)targetMoRateTest);

					targetMoRates[s] = targetMoRate;
					targetSupraMoRates[s] = supraMoRate;

					fractSuprasTrack.addValue(fractSupra);
					sectFractSupras[s] = fractSupra;

					sectSubSeisMFDs.add(subSeisMFD);
					sectSupraSeisMFDs.add(supraSeisMFD);
				}

				if (slipAddStdScalar == 0d) {
					System.out.println("Fraction supra-seismogenic stats: "+fractSuprasTrack);
					System.out.println("Fault moments: total="+(float)StatUtils.sum(targetMoRates)
						+"\tsupra="+(float)StatUtils.sum(targetSupraMoRates));
				}

				if (subSeisMoRateReduction == SubSeisMoRateReduction.SYSTEM_AVG_IMPLIED_FROM_SUPRA_B
						|| subSeisMoRateReduction == SubSeisMoRateReduction.SYSTEM_AVG_SUB_B_1) {
					// need to re-balance to use average supra-seis fract
					double sumSupraMo = StatUtils.sum(targetSupraMoRates);
					double sumTotMo = StatUtils.sum(targetMoRates);
					double avgSupraSeis = sumSupraMo/sumTotMo;

					if (slipAddStdScalar == 0d)
						System.out.println("Re-scaling section MFDs to match system-wide supra-seismogenic fract="+(float)avgSupraSeis);

					for (int s=0; s<numSects; s++) {
						if (slipRates[s] == 0d || sectFractSupras[s] == 0d || targetMoRates[s] == 0d || targetSupraMoRates[s] == 0d)
							continue;
						slipRates[s] = slipRates[s]*avgSupraSeis/sectFractSupras[s];
						slipRateStdDevs[s] = slipRateStdDevs[s]*avgSupraSeis/sectFractSupras[s];

						targetSupraMoRates[s] = targetMoRates[s]*avgSupraSeis;
						double targetSubSeis = targetMoRates[s] - targetSupraMoRates[s];
						sectFractSupras[s] = avgSupraSeis;

						sectSubSeisMFDs.get(s).scaleToTotalMomentRate(targetSubSeis);
						sectSupraSeisMFDs.get(s).scaleToTotalMomentRate(targetSupraMoRates[s]);
					}
				}

				if (subSeisMoRateReduction == SubSeisMoRateReduction.FROM_INPUT_SLIP_RATES) {
					sectSlipRates = rupSet.requireModule(SectSlipRates.class);
				} else {
					sectSlipRates = SectSlipRates.precomputed(rupSet, slipRates, slipRateStdDevs);
				}
				if (slipOnly)
					// shortcut for if we only need slip rates
					return;

				if (targetAdjDataConstraints != null && !targetAdjDataConstraints.isEmpty()) {
//					System.out.println("Adjusting target MFDs with "+targetAdjDataConstraints.size()+" estimators");

					// do these 1 at a time as each one adjusts the actual targets
					for (SectNucleationMFD_Estimator estimator : targetAdjDataConstraints) {
						Stopwatch watch = Stopwatch.createStarted();
						estimator.init(rupSet, sectSupraSeisMFDs, targetSupraMoRates, slipRates, slipRateStdDevs,
								sectRupUtilizations, sectMinMagIndexes, sectMaxMagIndexes, sectRupInBinCounts, refMFD);

						List<Future<DataEstCallable>> futures = estimateSectNuclMFDs(List.of(estimator), slipRates,
								slipRateStdDevs, targetSupraMoRates, sectRupUtilizations, sectSupraSeisMFDs, exec);

						for (Future<DataEstCallable> future : futures) {
							DataEstCallable call;
							try {
								call = future.get();
							} catch (InterruptedException | ExecutionException e) {
								e.printStackTrace();
								throw ExceptionUtils.asRuntimeException(e);
							}
							sectSupraSeisMFDs.set(call.sect.getSectionId(), call.impliedMFD);
							//									System.out.println("Done with future for sect "+call.sect.getSectionId());
						}
						System.out.println("Done with "+futures.size()+" estimation futures in "+
								(float)(watch.elapsed(TimeUnit.MILLISECONDS)/1000d)+" s");
					}
				}
			}
		}

		public List<Future<DataEstCallable>> estimateSectNuclMFDs(List<? extends SectNucleationMFD_Estimator> estimators,
				double[] slipRates, double[] slipRateStdDevs, double[] targetSupraMoRates, List<BitSet> sectRupUtilizations,
				List<IncrementalMagFreqDist> sectSupraSeisMFDs,ExecutorService exec) {
			List<Future<DataEstCallable>> futures = new ArrayList<>();
			
			for (int s=0; s<slipRates.length; s++) {
				FaultSection sect = rupSet.getFaultSectionData(s);
				
				if (targetSupraMoRates[s] == 0d || slipRates[s] == 0d)
					continue;
				
				// see if we have any data constraints that imply a different MFD for this section,
				// we will later adjust uncertainties accordingly
				List<SectNucleationMFD_Estimator> constraints = new ArrayList<>();
				for (SectNucleationMFD_Estimator constraint : estimators)
					if (constraint.appliesTo(sect))
						constraints.add(constraint);
				
				if (!constraints.isEmpty()) {
//					System.out.println("\tCalculating MFDs implied by "+constraints.size()
//						+" data constraints for sect "+s+". "+sect.getSectionName());
					
					double relDefModStdDev = slipRateStdDevs[s]/slipRates[s];
					double supraMoRate = targetSupraMoRates[s];
					
					IncrementalMagFreqDist supraSeisMFD = sectSupraSeisMFDs.get(s);
					
					List<Integer> rups = new ArrayList<>();
					List<Double> mags = new ArrayList<>();
					fillInRupsAndMags(sectRupUtilizations.get(s), rups, mags);
					
					UncertainDataConstraint moRateBounds = new UncertainDataConstraint(null, supraMoRate,
							new Uncertainty(supraMoRate*relDefModStdDev));
					
					futures.add(exec.submit(new DataEstCallable(sect, constraints, supraSeisMFD, mags, rups, moRateBounds)));
				}
			}
			
			System.out.println("Waiting on "+futures.size()+" data estimation futures...");
			return futures;
		}
		
		/**
		 * Fills in the given rupture index and magnitude lists of utilized ruptures for a given section
		 * 
		 * @param utilization
		 * @param rups
		 * @param mags
		 */
		private void fillInRupsAndMags(BitSet utilization, List<Integer> rups, List<Double> mags) {
			 for (int r = utilization.nextSetBit(0); r >= 0; r = utilization.nextSetBit(r+1)) {
			    if (rups != null)
			    	rups.add(r);
			    if (mags != null)
			    	mags.add(rupSet.getMagForRup(r));
			 }
		}
		
		private class DataEstCallable implements Callable<DataEstCallable> {
			
			private FaultSection sect;
			private List<SectNucleationMFD_Estimator> constraints;
			private IncrementalMagFreqDist supraSeisMFD;
			private List<Double> mags;
			private List<Integer> rups;
			private UncertainDataConstraint moRateBounds;
			
			private IncrementalMagFreqDist impliedMFD;

			public DataEstCallable(FaultSection sect, List<SectNucleationMFD_Estimator> constraints,
					IncrementalMagFreqDist supraSeisMFD, List<Double> mags, List<Integer> rups,
					UncertainDataConstraint moRateBounds) {
				this.sect = sect;
				this.constraints = constraints;
				this.supraSeisMFD = supraSeisMFD;
				this.mags = mags;
				this.rups = rups;
				this.moRateBounds = moRateBounds;
			}

			@Override
			public DataEstCallable call() throws Exception {
				List<IncrementalMagFreqDist> impliedMFDs = new ArrayList<>();
				
				for (SectNucleationMFD_Estimator constraint : constraints) {
					// nucleation mfd implied by this constraint
					IncrementalMagFreqDist implied = constraint.estimateNuclMFD(
							sect, supraSeisMFD, rups, mags, moRateBounds, sparseGR);
					Preconditions.checkState((float)implied.getMinX() == (float)MIN_MAG);
					Preconditions.checkState((float)implied.getDelta() == (float)DELTA_MAG);
					impliedMFDs.add(implied);
				}
				
				if (impliedMFDs.size() > 1) {
					// need to combine them
					IncrementalMagFreqDist meanMFD = new IncrementalMagFreqDist(MIN_MAG, impliedMFDs.get(0).size(), DELTA_MAG);
					IncrementalMagFreqDist upperMFD = null;
					IncrementalMagFreqDist lowerMFD = null;
					// we use one sigma later when calculating the implie std dev, so just do that now
					UncertaintyBoundType boundType = UncertaintyBoundType.ONE_SIGMA;
					int numBounded = 0;
					for (IncrementalMagFreqDist mfd : impliedMFDs) {
						Preconditions.checkState(mfd.size() == meanMFD.size());
						for (int i=0; i<meanMFD.size(); i++)
							if (mfd.getY(i) > 0)
								meanMFD.add(i, mfd.getY(i));
						if (mfd instanceof UncertainIncrMagFreqDist) {
							UncertainBoundedIncrMagFreqDist bounded;
							if (upperMFD == null) {
								upperMFD = new IncrementalMagFreqDist(MIN_MAG, meanMFD.size(), DELTA_MAG);
								lowerMFD = new IncrementalMagFreqDist(MIN_MAG, meanMFD.size(), DELTA_MAG);
							}
							bounded = ((UncertainIncrMagFreqDist)mfd).estimateBounds(boundType);
							numBounded++;
							for (int i=0; i<meanMFD.size(); i++) {
								upperMFD.add(i, bounded.getUpperY(i));
								lowerMFD.add(i, bounded.getLowerY(i));
							}
						}
					}
					meanMFD.scale(1d/(double)impliedMFDs.size());
					if (upperMFD != null) {
						upperMFD.scale(1d/(double)numBounded);
						lowerMFD.scale(1d/(double)numBounded);
						for (int i=0; i<meanMFD.size(); i++) {
							upperMFD.set(i, Math.max(upperMFD.getY(i), meanMFD.getY(i)));
							lowerMFD.set(i, Math.max(0d, Math.min(lowerMFD.getY(i), meanMFD.getY(i))));
						}
						meanMFD = new UncertainBoundedIncrMagFreqDist(meanMFD, lowerMFD, upperMFD, boundType);
					}
					impliedMFD = meanMFD;
				} else {
					impliedMFD = impliedMFDs.get(0);
				}
				return this;
			}
			
		}
		
		/*
		 *  It seems that you are theoretically supposed to sum a true standard deviation in variance space, then take the
		 *  sqrt to get the summed standard deviation,
		 *  e.g. https://en.wikipedia.org/wiki/Variance#Basic_properties
		 *  	https://www.geol.lsu.edu/jlorenzo/geophysics/uncertainties/Uncertaintiespart2.html
		 *  
		 *  This will typically lead to smaller values than just summing each standard deviation. This sort of makes sense
		 *  in that, on a summed regional basis, evenly sampling from the deformation model uncertainties on individual
		 *  faults should cancel each other out. Just straight adding standard deviations would allow for a systematic
		 *  bias.
		 *  
		 *  Set this to true to sum deformation model uncertainties in variance space, or false to sum standard deviations
		 */
		private static final boolean DEF_MOD_SUM_VARIANCES = true;
		
		private UncertainIncrMagFreqDist calcRegionalSupraTarget(EvenlyDiscretizedFunc refMFD, Region region,
				List<IncrementalMagFreqDist> sectSupraSeisMFDs, List<EvenlyDiscretizedFunc> defModMFDStdDevs,
				List<IncrementalMagFreqDist> dataImpliedSectSupraSeisMFDs) {
			IncrementalMagFreqDist sumMFD = new IncrementalMagFreqDist(MIN_MAG, refMFD.size(), DELTA_MAG);
			
			double[] binCounts = new double[refMFD.size()];
			sumMFD.setRegion(region);
			double[] fractSectsInRegion = null;
			if (region != null)
				fractSectsInRegion = rupSet.getFractSectsInsideRegion(
					region, MFDInversionConstraint.MFD_FRACT_IN_REGION_TRACE_ONLY);
			
			// first just sum the section supra-seismogenic MFDs
			for (int s=0; s<sectSupraSeisMFDs.size(); s++) {
				double scale = fractSectsInRegion == null ? 1d : fractSectsInRegion[s];
				if (scale > 0d) {
					IncrementalMagFreqDist supraMFD = sectSupraSeisMFDs.get(s);
					// make sure we have the same gridding
					Preconditions.checkState((float)supraMFD.getMinX() == (float)MIN_MAG);
					Preconditions.checkState((float)supraMFD.getDelta() == (float)DELTA_MAG);
					for (int i=0; i<supraMFD.size(); i++) {
						double rate = supraMFD.getY(i);
						if (rate > 0d) {
							binCounts[i]++;
							sumMFD.add(i, rate);
						}
					}
				}
			}
			
			EvenlyDiscretizedFunc stdDevs = new EvenlyDiscretizedFunc(MIN_MAG, refMFD.size(), DELTA_MAG);
			if (defModMFDStdDevs != null) {
				// sum up deformation model implied std devs
				Preconditions.checkState(defModMFDStdDevs.size() == sectSupraSeisMFDs.size());
				for (int s=0; s<defModMFDStdDevs.size(); s++) {
					EvenlyDiscretizedFunc dmStdDevs = defModMFDStdDevs.get(s);
					double scale = fractSectsInRegion == null ? 1d : fractSectsInRegion[s];
					if (scale > 0d && dmStdDevs != null) {
						for (int i=0; i<dmStdDevs.size(); i++) {
							if (DEF_MOD_SUM_VARIANCES)
								// will take sqrt later
								stdDevs.add(i, Math.pow(dmStdDevs.getY(i), 2));
							else
								stdDevs.add(i, dmStdDevs.getY(i));
						}
					}
				}
				if (DEF_MOD_SUM_VARIANCES)
					for (int i=0; i<refMFD.size(); i++)
						stdDevs.set(i, Math.sqrt(stdDevs.getY(i)));
			}
			
			// now make sure that we're at least at the default uncertainty everywhere
			for (int i=0; i<refMFD.size(); i++)
				stdDevs.set(i, Math.max(stdDevs.getY(i), sumMFD.getY(i)*magDepRelStdDev.applyAsDouble(stdDevs.getX(i))));
			
			if (addSectCountUncertainties) {
				double refNum = fractSectsInRegion == null ? sectSupraSeisMFDs.size() : StatUtils.sum(fractSectsInRegion);
				System.out.println("Re-weighting target MFD to account for section participation uncertainties.");
				double max = StatUtils.max(binCounts);
				System.out.println("\tMax section participation: "+(float)max);
				System.out.println("\tReference section participation: "+(float)refNum);
				for (int i=0; i<binCounts.length; i++) {
					double rate = sumMFD.getY(i);
					if (binCounts[i] == 0d || rate == 0d)
						continue;
//					double relStdDev = refNum/binCounts[i];
					double relStdDev = Math.sqrt(refNum)/Math.sqrt(binCounts[i]);
					double origStdDev = stdDevs.getY(i);
					double origRel = origStdDev/rate;
					if (D) System.out.println("\t\tRelative particpation std dev for M="
							+(float)sumMFD.getX(i)+", binCount="+(float)binCounts[i]+": "+(float)relStdDev
							+"\torigStdDev="+(float)origStdDev+"\torigRelStdDev="+(float)origRel+"\ttotRel="+(float)(origRel*relStdDev));
					// scale existing std dev
					stdDevs.set(i, origStdDev*relStdDev);
				}
			}
			
			UncertainIncrMagFreqDist uncertainMFD = new UncertainIncrMagFreqDist(sumMFD, stdDevs);
			if (dataImpliedSectSupraSeisMFDs != null) {
				// compute data implied std devs
				Preconditions.checkState(dataImpliedSectSupraSeisMFDs.size() == sectSupraSeisMFDs.size());
				IncrementalMagFreqDist sumImpliedMFD = new IncrementalMagFreqDist(MIN_MAG, refMFD.size(), DELTA_MAG);
				IncrementalMagFreqDist sumLowerMFD = new IncrementalMagFreqDist(MIN_MAG, refMFD.size(), DELTA_MAG);
				IncrementalMagFreqDist sumUpperMFD = new IncrementalMagFreqDist(MIN_MAG, refMFD.size(), DELTA_MAG);
				
				for (int s=0; s<dataImpliedSectSupraSeisMFDs.size(); s++) {
					double scale = fractSectsInRegion == null ? 1d : fractSectsInRegion[s];
					if (scale > 0d) {
						IncrementalMagFreqDist impliedMFD = dataImpliedSectSupraSeisMFDs.get(s);
						if (impliedMFD == null)
							// no data constraint for this section, use the regular MFD
							impliedMFD = sectSupraSeisMFDs.get(s);
						// make sure we have the same gridding
						Preconditions.checkState((float)impliedMFD.getMinX() == (float)MIN_MAG,
								"Min mag mismatch: %s != %s", (float)impliedMFD.getMinX(), (float)MIN_MAG);
						Preconditions.checkState((float)impliedMFD.getDelta() == (float)DELTA_MAG,
								"Delta mismatch: %s != %s", (float)impliedMFD.getDelta(), (float)DELTA_MAG);
						for (int i=0; i<impliedMFD.size(); i++)
							sumImpliedMFD.add(i, impliedMFD.getY(i));
						if (impliedMFD instanceof UncertainBoundedIncrMagFreqDist) {
							UncertainBoundedIncrMagFreqDist bounded = (UncertainBoundedIncrMagFreqDist)impliedMFD;
							for (int i=0; i<impliedMFD.size(); i++) {
								sumLowerMFD.add(i, bounded.getLowerY(i));
								sumUpperMFD.add(i, bounded.getUpperY(i));
							}
						} else {
							for (int i=0; i<impliedMFD.size(); i++) {
								double rate = impliedMFD.getY(i);
								sumLowerMFD.add(i, rate);
								sumUpperMFD.add(i, rate);
							}
						}
					}
				}
				
				UncertainBoundedIncrMagFreqDist impliedMFD = new UncertainBoundedIncrMagFreqDist(
						sumImpliedMFD, sumLowerMFD, sumUpperMFD, uncertAdjDataTargetBound);
				
				// adjust the standard deviations of the regional mfd to reach the implied MFD
				System.out.println("Adjusting regional MFD to match data bounds");
				uncertainMFD = adjustForDataImpliedBounds(uncertainMFD, impliedMFD, D);
			}
			
			if (D) {
				System.out.println("Final regional MFD:");
				boolean first = true;
				for (int i=0; i<uncertainMFD.size(); i++) {
					double x = uncertainMFD.getX(i);
					double y = uncertainMFD.getY(i);
					if (first && y == 0d)
						continue;
					first = false;
					double sd = uncertainMFD.getStdDev(i);
					System.out.println("\tM="+(float)x+"\tRate="+(float)y+"\tStdDev="+(float)sd+"\tRelStdDev="+(float)(sd/y));
				}
			}
			return uncertainMFD;
		}
		
		/**
		 * This adjusts (expanding, if necessary) the uncertainties on the given MFD such that the implied MFD is at
		 * least within {@link #uncertAdjDataTargetBound}. If the implied data MFD is an {@link UncertainIncrMagFreqDist},
		 * then the adjustment will instead ensure that the nearer bound of the implied MFD is within
		 * {@link #uncertAdjDataTargetBound} of the given MFD.
		 * 
		 * @param mfd
		 * @param impliedMFD
		 * @param verbose
		 * @return
		 */
		private UncertainBoundedIncrMagFreqDist adjustForDataImpliedBounds(UncertainIncrMagFreqDist mfd,
				IncrementalMagFreqDist impliedMFD, boolean verbose) {
			Preconditions.checkState(mfd.size() == impliedMFD.size());
			Preconditions.checkState((float)mfd.getMinX() == (float)impliedMFD.getMinX());
			Preconditions.checkState((float)mfd.getDelta() == (float)impliedMFD.getDelta());
			
			UncertainBoundedIncrMagFreqDist boundedImplied = null;
			if (impliedMFD instanceof UncertainBoundedIncrMagFreqDist)
				boundedImplied = (UncertainBoundedIncrMagFreqDist)impliedMFD;
			else if (impliedMFD instanceof UncertainIncrMagFreqDist)
				boundedImplied = ((UncertainIncrMagFreqDist)impliedMFD).estimateBounds(uncertAdjDataTargetBound);

			
			EvenlyDiscretizedFunc stdDevs = new EvenlyDiscretizedFunc(mfd.getMinX(), mfd.size(), mfd.getDelta());

			UncertainBoundedIncrMagFreqDist boundedInput = mfd.estimateBounds(UncertaintyBoundType.ONE_SIGMA);
			IncrementalMagFreqDist lower = boundedInput.getLower();
			IncrementalMagFreqDist upper = boundedInput.getUpper();
			EvenlyDiscretizedFunc inputStdDevs = boundedInput.getStdDevs();
			
			MinMaxAveTracker stdDevTrack = new MinMaxAveTracker();
			MinMaxAveTracker relStdDevTrack = new MinMaxAveTracker();
			for (int i=0; i<mfd.size(); i++) {
				if (mfd.getY(i) == 0)
					continue;
				double mag = mfd.getX(i);
				double rate = mfd.getY(i);
				double[] dataRates;
				if (boundedImplied == null)
					dataRates = new double[] { impliedMFD.getY(i) };
				else
					dataRates = new double[] { impliedMFD.getY(i), boundedImplied.getLowerY(i), boundedImplied.getUpperY(i) };
				double minImpliedStdDev = Double.POSITIVE_INFINITY;
				double closestData = Double.NaN;
				for (double dataRate : dataRates) {
					double diff = Math.abs(dataRate - rate);
					double impliedStdDev = uncertAdjDataTargetBound.estimateStdDev(rate, rate-diff, rate+diff);
					if (impliedStdDev < minImpliedStdDev) {
						minImpliedStdDev = impliedStdDev;
						closestData = dataRate;
					}
				}
				if (closestData > rate) {
					// adjust the upper bound
					upper.set(i, Math.max(upper.getY(i), rate+minImpliedStdDev));
				} else {
					lower.set(i, Math.max(0, Math.min(lower.getY(i), rate-minImpliedStdDev)));
				}
				double relStdDev = minImpliedStdDev/rate;
				if (verbose) System.out.println("\tM="+(float)mag+"\trate="+(float)rate+"\tdataRate="+(float)dataRates[0]
						+"\tdataBounds=["+(float)dataRates[1]+","+(float)dataRates[2]+"]\timplStdDev="+minImpliedStdDev
						+"\timplRelStdDev="+(float)relStdDev);
				stdDevTrack.addValue(minImpliedStdDev);
				relStdDevTrack.addValue(relStdDev);
				stdDevs.set(i, Math.max(minImpliedStdDev, inputStdDevs.getY(i)));
			}
			if (D) System.out.println("\tImplied std dev range:\t["+(float)stdDevTrack.getMin()+","+(float)stdDevTrack.getMax()
					+"], avg="+(float)stdDevTrack.getAverage()+"\t\trel range:\t["+(float)relStdDevTrack.getMin()
					+","+(float)relStdDevTrack.getMax()+"], avg="+(float)relStdDevTrack.getAverage());
			// this is what was used pre 2/14/23, and resulted in lower std devs
			// basically, sd = 0.5*(origSD + max(origSD, inputSD))
//			return new UncertainBoundedIncrMagFreqDist(mfd, lower, upper, UncertaintyBoundType.ONE_SIGMA);
			// this actually uses the max(origSD, inputSD)
			return new UncertainBoundedIncrMagFreqDist(mfd, lower, upper, UncertaintyBoundType.ONE_SIGMA, stdDevs);
		}
	}
	
	private double supraSeisBValue;
	private double[] sectSpecificBValues;
	private List<UncertainIncrMagFreqDist> supraSeismoOnFaultMFDs;

	private SectSlipRates sectSlipRates;

	private List<BitSet> sectRupUtilizations;

	private SupraSeisBValInversionTargetMFDs(FaultSystemRupSet rupSet, double supraSeisBValue, double[] sectSpecificBValues,
			IncrementalMagFreqDist totalRegionalMFD, UncertainIncrMagFreqDist onFaultSupraSeisMFD,
			IncrementalMagFreqDist onFaultSubSeisMFD, List<UncertainIncrMagFreqDist> mfdConstraints,
			SubSeismoOnFaultMFDs subSeismoOnFaultMFDs, List<UncertainIncrMagFreqDist> supraSeismoOnFaultMFDs,
			SectSlipRates sectSlipRates, List<BitSet> sectRupUtilizations) {
		super(rupSet, totalRegionalMFD, onFaultSupraSeisMFD, onFaultSubSeisMFD, null, mfdConstraints,
				subSeismoOnFaultMFDs, supraSeismoOnFaultMFDs);
		if (sectSpecificBValues == null) {
			this.supraSeisBValue = supraSeisBValue;
		} else {
			this.sectSpecificBValues = sectSpecificBValues;
			Preconditions.checkState(sectSpecificBValues.length == rupSet.getNumSections());
			if (Double.isFinite(supraSeisBValue))
				this.supraSeisBValue = supraSeisBValue;
			else
				this.supraSeisBValue = StatUtils.mean(sectSpecificBValues);
		}
		this.supraSeismoOnFaultMFDs = supraSeismoOnFaultMFDs;
		this.sectSlipRates = sectSlipRates;
		this.sectRupUtilizations = sectRupUtilizations;
	}

	public SectSlipRates getSectSlipRates() {
		return sectSlipRates;
	}

	public double getSupraSeisBValue() {
		return supraSeisBValue;
	}
	
	public double[] getSectSpecificBValues() {
		return sectSpecificBValues;
	}

	@Override
	public List<UncertainIncrMagFreqDist> getOnFaultSupraSeisNucleationMFDs() {
		return supraSeismoOnFaultMFDs;
	}

	@Override
	public Class<? extends ArchivableModule> getLoadingClass() {
		// load this back in as simple Precomputed
		return InversionTargetMFDs.Precomputed.class;
	}
	
	public List<Integer> getRupturesForSect(int sectIndex) {
		BitSet utilization = sectRupUtilizations.get(sectIndex);
		List<Integer> rups = new ArrayList<>();
		for (int r = utilization.nextSetBit(0); r >= 0; r = utilization.nextSetBit(r+1))
		   rups.add(r);
		return rups;
	}

	@Override
	public AveragingAccumulator<InversionTargetMFDs> averagingAccumulator() {
//		return new Averager();
		return null;
	}
	
	public static class SupraBAverager extends InversionTargetMFDs.Averager {
		
		private boolean allSupra = true;

		private double weightedBValSum = 0d;
		private double[] weightedSectSpecificBValsSum;
		private AveragingAccumulator<SectSlipRates> slipAvg;
		private List<BitSet> sectRupUtilizations;

		@Override
		public void process(InversionTargetMFDs module, double relWeight) {
			boolean first = totWeight == 0d;
			super.process(module, relWeight);
			allSupra = allSupra && module instanceof SupraSeisBValInversionTargetMFDs;
			if (allSupra) {
				SupraSeisBValInversionTargetMFDs mfds = (SupraSeisBValInversionTargetMFDs)module;
				weightedBValSum += relWeight*mfds.supraSeisBValue;
				if (mfds.sectSpecificBValues != null) {
					if (weightedSectSpecificBValsSum == null) {
						if (first) {
							weightedSectSpecificBValsSum = new double[mfds.sectSpecificBValues.length];
						} else {
							// not all have sect-specific, bail
							allSupra = false;
							return;
						}
					}
					Preconditions.checkState(weightedSectSpecificBValsSum.length == mfds.sectSpecificBValues.length);
					for (int s=0; s<weightedSectSpecificBValsSum.length; s++)
						weightedSectSpecificBValsSum[s] += relWeight*mfds.sectSpecificBValues[s];
				} else if (weightedSectSpecificBValsSum != null) {
					// not all have sect-specific, bail
					allSupra = false;
					return;
				}
				if (slipAvg == null) {
					slipAvg = mfds.sectSlipRates.averagingAccumulator();
					Preconditions.checkNotNull(slipAvg);
					sectRupUtilizations = new ArrayList<>();
					FaultSystemRupSet rupSet = mfds.getParent();
					for (int s=0; s<rupSet.getNumSections(); s++)
						sectRupUtilizations.add(new BitSet(rupSet.getNumRuptures()));
				}
				slipAvg.process(mfds.sectSlipRates, relWeight);
				for (int s=0; s<sectRupUtilizations.size(); s++)
					sectRupUtilizations.get(s).or(mfds.sectRupUtilizations.get(s));
			}
		}

		@Override
		public InversionTargetMFDs getAverage() {
			InversionTargetMFDs average = super.getAverage();
			if (allSupra)
				average = doGetSupraSeisAverageInstance(average);
			return average;
		}
		
		public SupraSeisBValInversionTargetMFDs getSupraSeisAverageInstance() {
			Preconditions.checkState(allSupra, "Not all processed target MFDs were averagable SupraSeisBValInversionTargetMFDs instances");
			return doGetSupraSeisAverageInstance(super.getAverage());
		}
		
		private SupraSeisBValInversionTargetMFDs doGetSupraSeisAverageInstance(InversionTargetMFDs average) {
			double supraSeisBValue = weightedBValSum/totWeight;
			IncrementalMagFreqDist totalRegionalMFD = average.getTotalRegionalMFD();
			UncertainIncrMagFreqDist onFaultSupraSeisMFD = (UncertainIncrMagFreqDist) average.getTotalOnFaultSupraSeisMFD();
			IncrementalMagFreqDist onFaultSubSeisMFD = average.getTotalOnFaultSubSeisMFD();
			List<UncertainIncrMagFreqDist> mfdConstraints = new ArrayList<>();
			for (IncrementalMagFreqDist mfd : average.getMFD_Constraints()) {
				Preconditions.checkState(mfd instanceof UncertainIncrMagFreqDist);
				mfdConstraints.add((UncertainIncrMagFreqDist)mfd);
			}
			SubSeismoOnFaultMFDs subSeismoOnFaultMFDs = average.getOnFaultSubSeisMFDs();
			List<UncertainIncrMagFreqDist> supraSeismoOnFaultMFDs = new ArrayList<>();
			for (IncrementalMagFreqDist mfd : average.getOnFaultSupraSeisNucleationMFDs()) {
				Preconditions.checkState(mfd instanceof UncertainIncrMagFreqDist);
				supraSeismoOnFaultMFDs.add((UncertainIncrMagFreqDist)mfd);
			}
			SectSlipRates sectSlipRates = slipAvg.getAverage();
			double[] sectSpecificBValues = null;
			if (weightedSectSpecificBValsSum != null) {
				sectSpecificBValues = new double[weightedSectSpecificBValsSum.length];
				for (int i=0; i<sectSpecificBValues.length; i++)
					sectSpecificBValues[i] = weightedSectSpecificBValsSum[i]/totWeight;
			}
			return new SupraSeisBValInversionTargetMFDs(
					null, supraSeisBValue, sectSpecificBValues, totalRegionalMFD, onFaultSupraSeisMFD, onFaultSubSeisMFD, mfdConstraints,
					subSeismoOnFaultMFDs, supraSeismoOnFaultMFDs, sectSlipRates, sectRupUtilizations);
		}
		
	}
	
	public static void main(String[] args) throws IOException {
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(
//				new File("/data/kevin/markdown/inversions/fm3_1_u3ref_uniform_reproduce_ucerf3.zip"));
//				new File("/data/kevin/markdown/inversions/fm3_1_u3ref_uniform_coulomb.zip"));
				new File("/data/kevin/nshm23/def_models/NSHM23_v1p4/GEOLOGIC.zip"));
		
		InversionTargetMFDs origTargets = rupSet.getModule(InversionTargetMFDs.class);
		SectSlipRates origSlips = rupSet.getModule(SectSlipRates.class);
		
		GeographicMapMaker mapMaker = new RupSetMapMaker(rupSet, new CaliforniaRegions.RELM_TESTING());
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, 1d);
		
		rupSet = FaultSystemRupSet.buildFromExisting(rupSet)
//				.replaceFaultSections(DeformationModels.MEAN_UCERF3.build(FaultModels.FM3_1))
//				.replaceFaultSections(U3_UncertAddDeformationModels.U3_MEAN.build(FaultModels.FM3_1))
//				.forScalingRelationship(ScalingRelationships.MEAN_UCERF3)
				.forScalingRelationship(NSHM23_ScalingRelationships.AVERAGE)
				.build();
		
		double b = 0.5d;
//		double b = 0d;
//		double b = -1d;
		Builder builder = new Builder(rupSet, b);
		
		builder.sparseGR(true);
//		builder.magDepDefaultRelStdDev(M->0.1);
//		builder.magDepDefaultRelStdDev(M->0.1*Math.pow(10, b*0.5*(M-6)));
//		builder.magDepDefaultRelStdDev(M->0.001);
//		builder.magDepDefaultRelStdDev(M->0.05);
		builder.magDepDefaultRelStdDev(M->0.05*Math.max(1d, Math.pow(10, b*0.5*(M-6))));
//		builder.magDepDefaultRelStdDev(M->0.05*Math.max(1d, Math.pow(10, 0.5*(M-6))));
		builder.applyDefModelUncertainties(true);
		builder.addSectCountUncertainties(false);
		builder.totalTargetMFD(rupSet.requireModule(InversionTargetMFDs.class).getTotalRegionalMFD());
		builder.subSeisMoRateReduction(SubSeisMoRateReduction.SUB_SEIS_B_1);
//		Shaw07JumpDistProb segModel = Shaw07JumpDistProb.forHorzOffset(1d, 3d, 2d);
		JumpProbabilityCalc segModel = NSHM23_SegmentationModels.HIGH.getModel(rupSet, rupSet.getModule(LogicTreeBranch.class));
//		builder.adjustTargetsForData(new SegmentationImpliedSectNuclMFD_Estimator(segModel,
////				MultiBinDistributionMethod.GREEDY, false));
////				MultiBinDistributionMethod.GREEDY, true));
////				MultiBinDistributionMethod.FULLY_DISTRIBUTED, false));
////				MultiBinDistributionMethod.FULLY_DISTRIBUTED, true));
////				MultiBinDistributionMethod.CAPPED_DISTRIBUTED, false));
//				MultiBinDistributionMethod.CAPPED_DISTRIBUTED, true));
//		builder.adjustTargetsForData(new ImprobabilityImpliedSectNuclMFD_Estimator(segModel));
//		builder.adjustTargetsForData(new ImprobModelThresholdAveragingSectNuclMFD_Estimator.WorstJumpProb(segModel));
		builder.adjustTargetsForData(new ThresholdAveragingSectNuclMFD_Estimator.RelGRWorstJumpProb(segModel, 100, true));
//		builder.forBinaryRupProbModel(MaxJumpDistModels.FIVE.getModel(rupSet));
//		builder.forSegmentationModel(segModel);
//		builder.forSegmentationModel(new JumpProbabilityCalc() {
//			
//			@Override
//			public String getName() {
//				return null;
//			}
//			
//			@Override
//			public boolean isDirectional(boolean splayed) {
//				return false;
//			}
//			
//			@Override
//			public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
//				return 0.9999999999999;
//			}
//		});
//		builder.forImprobModel(MaxJumpDistModels.ONE.getModel(rupSet));
//		builder.adjustForActualRupSlips(true, false);
//		builder.adjustForActualRupSlips(false, false);
//		builder.adjustTargetsForData(new ScalingRelSlipRateMFD_Estimator(false));
		
//		builder.forBinaryRupProbModel(new NSHM23_ConstraintBuilder(rupSet, b).excludeRupturesThroughCreeping().getRupExclusionModel());
		
//		List<SectNucleationMFD_Estimator> dataConstraints = new ArrayList<>();
//		dataConstraints.add(new APrioriSectNuclEstimator(
//				rupSet, UCERF3InversionInputGenerator.findParkfieldRups(rupSet), 1d/25d, 0.1d/25d));
//		dataConstraints.addAll(PaleoSectNuclEstimator.buildPaleoEstimates(rupSet, true));
//		UncertaintyBoundType expandUncertToDataBound = UncertaintyBoundType.ONE_SIGMA;
//		builder.expandUncertaintiesForData(dataConstraints, expandUncertToDataBound);
		
		SupraSeisBValInversionTargetMFDs target = builder.build();
		rupSet.addModule(target);
		
		SectSlipRates modSlipRates = rupSet.getModule(SectSlipRates.class);
		double[] sectFractSupras = new double[rupSet.getNumSections()];
		double[] u3FractSupra = new double[rupSet.getNumSections()];
		MinMaxAveTracker track = new MinMaxAveTracker();
		MinMaxAveTracker u3track = new MinMaxAveTracker();
		for (int s=0; s<u3FractSupra.length; s++) {
			double creepReduced = rupSet.getFaultSectionData(s).getReducedAveSlipRate()*1e-3;
			u3FractSupra[s] = origSlips.getSlipRate(s)/creepReduced;
			sectFractSupras[s] = modSlipRates.getSlipRate(s)/creepReduced;
			track.addValue(sectFractSupras[s]);
			u3track.addValue(u3FractSupra[s]);
		}
		System.out.println("My fracts: "+track);
		System.out.println("U3 fracts: "+u3track);
		
		mapMaker.plotSectScalars(sectFractSupras, cpt, "New Fraction of Moment Supra-Seismogenic");
		
		File outputDir = new File("/tmp");
		mapMaker.plot(outputDir, "supra_seis_fracts", " ");
		
		mapMaker.plotSectScalars(u3FractSupra, cpt, "U3 Fraction of Moment Supra-Seismogenic");

		mapMaker.plot(outputDir, "supra_seis_fracts_u3", " ");
		
		SolMFDPlot plot = new SolMFDPlot();
		File mfdOutput = new File(outputDir, "test_target_mfds");
		Preconditions.checkState(mfdOutput.exists() || mfdOutput.mkdir());
		plot.writePlot(rupSet, null, "Test Model", mfdOutput);
		
		if (builder.targetAdjDataConstraints != null && !builder.targetAdjDataConstraints.isEmpty()
				&& (builder.targetAdjDataConstraints.get(0) instanceof SegmentationImpliedSectNuclMFD_Estimator
						|| builder.targetAdjDataConstraints.get(0) instanceof ThresholdAveragingSectNuclMFD_Estimator)
				|| builder.rupSubSet != null) {
			// debug
			int[] debugSects = {
					// U3 FM3.1
//					100, // Bicycle Lake
//					1330, // Mono Lake
//					1832, // Mojave N
//					2063, // San Diego Trough
//					129, // Big Pine (East)
//					639, // Gillem - Big Crack
//					315, // Chino alt 1
//					159, // Brawley
					
					// NSHM23 FM 1.4
					
					4412, // Santa Rita (south)
			};

			builder.clearTargetAdjustments();
			SupraSeisBValInversionTargetMFDs targetNoSeg = builder.build();
			
			SupraSeisBValInversionTargetMFDs targetOrigGR = null;
			if (builder.sparseGR) {
				// rebuild without sparse GR
				builder.sparseGR(false);
				targetOrigGR = builder.build();
			}
			for (int debugSect : debugSects) {
				IncrementalMagFreqDist improbSupraMFD = target.supraSeismoOnFaultMFDs.get(debugSect);
				
				IncrementalMagFreqDist origSupraMFD = targetNoSeg.supraSeismoOnFaultMFDs.get(debugSect);
				
				List<IncrementalMagFreqDist> funcs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				
				if (targetOrigGR != null) {
					// rebuild without sparse GR
					builder.sparseGR(false);
					IncrementalMagFreqDist origGR = targetOrigGR.supraSeismoOnFaultMFDs.get(debugSect);
					funcs.add(origGR);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GREEN));
				}
				
				annotateNuclMFD(origSupraMFD, debugSect, "Original Supra-Seis", rupSet);
				funcs.add(origSupraMFD);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
				
				annotateNuclMFD(improbSupraMFD, debugSect, "Modified Supra-Seis", rupSet);
				funcs.add(improbSupraMFD);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE));
				
				double minX = Double.POSITIVE_INFINITY;
				double maxX = 0d;
				double minY = Double.POSITIVE_INFINITY;
				double maxY = 0d;
				for (IncrementalMagFreqDist func : funcs) {
					for (Point2D pt : func) {
						if (pt.getY() > 0d) {
							minX = Math.min(minX, pt.getX());
							minY = Math.min(minY, pt.getY());
							maxX = Math.max(maxX, pt.getX());
							maxY = Math.max(maxY, pt.getY());
						}
					}
				}
				minX -= 0.5*origSupraMFD.getDelta();
				maxX += 0.5*origSupraMFD.getDelta();
				minY = Math.pow(10, Math.floor(Math.log10(minY)));
				maxY = Math.pow(10, Math.ceil(Math.log10(maxY)));
				
				PlotSpec spec = new PlotSpec(funcs, chars, rupSet.getFaultSectionData(debugSect).getName(), "Mag", "Supra Rate");
				GraphWindow gw = new GraphWindow(spec);
				gw.setYLog(true);
				gw.setAxisRange(minX, maxX, minY, maxY);
				gw.setDefaultCloseOperation(GraphWindow.EXIT_ON_CLOSE);
			}
		}
	}
	
	private static void annotateNuclMFD(IncrementalMagFreqDist mfd, int sectIndex, String name, FaultSystemRupSet rupSet) {
		mfd.setName(name);
		
		double totRate = mfd.calcSumOfY_Vals();
		double minMag = Double.POSITIVE_INFINITY;
		double maxMag = Double.NEGATIVE_INFINITY;
		for (Point2D pt : mfd) {
			if (pt.getY() > 0) {
				minMag = Math.min(minMag, pt.getX());
				maxMag = Math.max(maxMag, pt.getX());
			}
		}
		double equivB = SectBValuePlot.estBValue(minMag, maxMag, totRate, mfd.getTotalMomentRate());
		String info = "Total Nucleation Rate: "+(float)totRate+"\n\tb-value: "+(float)equivB;
		// estimate participation
		int[] rupCounts = new int[mfd.size()];
		double[] avgBinAreas = new double[mfd.size()];
		for (int rupIndex : rupSet.getRupturesForSection(sectIndex)) {
			int magIndex = mfd.getClosestXIndex(rupSet.getMagForRup(rupIndex));
			if (mfd.getY(magIndex) > 0d) {
				rupCounts[magIndex]++;
				avgBinAreas[magIndex] += rupSet.getAreaForRup(rupIndex);
			}
		}
		int minMagIndex = mfd.getClosestXIndex(minMag);
		int maxMagIndex = mfd.getClosestXIndex(maxMag);
		double[] nuclToParticScalars = new double[mfd.size()];
		
		double sectArea = rupSet.getAreaForSection(sectIndex);
		for (int m=0; m<rupCounts.length; m++) {
			if (rupCounts[m] > 0) {
				avgBinAreas[m] /= rupCounts[m];
				nuclToParticScalars[m] = avgBinAreas[m]/sectArea;
			}
		}
		
		IncrementalMagFreqDist particMFD = new IncrementalMagFreqDist(mfd.getMinX(), mfd.size(), mfd.getDelta());
		for (int m=0; m<rupCounts.length; m++)
			if (rupCounts[m] > 0)
				particMFD.set(m, mfd.getY(m)*nuclToParticScalars[m]);
		totRate = particMFD.calcSumOfY_Vals();
		equivB = SectBValuePlot.estBValue(minMag, maxMag, totRate, particMFD.getTotalMomentRate());
		info += "\nTotal Participation Rate: "+(float)totRate+"\n\tb-value: "+(float)equivB;
		mfd.setInfo(info);
	}

}
