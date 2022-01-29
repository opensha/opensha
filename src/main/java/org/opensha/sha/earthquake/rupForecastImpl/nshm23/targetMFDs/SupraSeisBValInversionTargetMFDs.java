package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.BinaryJumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.MaxJumpDistModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.APrioriSectNuclEstimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.DataSectNucleationRateEstimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.PaleoSectNuclEstimator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SparseGutenbergRichterSolver;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.inversion.UCERF3InversionInputGenerator;

/**
 * {@link InversionTargetMFDs} implementation where targets are determined from deformation model slip rates and a target
 * supra-seismogenic b-value.
 * 
 * @author kevin
 *
 */
public class SupraSeisBValInversionTargetMFDs extends InversionTargetMFDs.Precomputed {
	
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
		 * Uses the target slip rates already attached to the rupture set as the final supra-seismogenic target
		 * slip rates.
		 */
		FROM_INPUT_SLIP_RATES,
		/**
		 * Don't reduce slip rates for sub-seismogenic ruptures. In other words, assume that the creep-reduced
		 * deformation model slip rate is satisfied only by supra-seismogenic ruptures.
		 */
		NONE
	}
	
	public static SubSeisMoRateReduction SUB_SEIS_MO_RATE_REDUCTION_DEFAULT = SubSeisMoRateReduction.SUB_SEIS_B_1;
	
	/**
	 * Default relative standard deviation as a function of magnitude: constant (10%)
	 */
	public static final DoubleUnaryOperator MAG_DEP_REL_STD_DEV_DEFAULT = M->0.1;
	
	public static final boolean APPLY_DEF_MODEL_UNCERTAINTIES_DEFAULT = true;
	
	public static boolean SPARSE_GR_DEFAULT = true;
	
	public static final boolean ADD_SECT_COUNT_UNCERTAINTIES_DEFAULT = false;
	
	public static final boolean ADJ_FOR_ACTUAL_RUP_SLIPS_DEFAULT = true;
	public static final boolean ADJ_FOR_SLIP_ALONG_DEFAULT = false;
	
	// discretization parameters for MFDs
	public final static double MIN_MAG = 0.05;
	public final static double DELTA_MAG = 0.1;
	
	/**
	 * Figures out MFD bounds for the given rupture set. It will start at 0.05 and and include at least the rupture set
	 * maximum magnitude, expanded to the ceiling of that rupture set maximum magnitude.
	 * 
	 * @param rupSet
	 * @return
	 */
	public static EvenlyDiscretizedFunc buildRefXValues(FaultSystemRupSet rupSet) {
		// figure out MFD bounds
		// go up to at least ceil(maxMag)
		double maxRupSetMag = Math.ceil(rupSet.getMaxMag());
		int NUM_MAG = 0;
		while (true) {
			double nextMag = MIN_MAG + (NUM_MAG+1)*DELTA_MAG;
			double nextMagLower = nextMag - 0.5*DELTA_MAG;
			if ((float)nextMagLower > (float)maxRupSetMag)
				// done
				break;
			NUM_MAG++;
		}
		
		return new EvenlyDiscretizedFunc(MIN_MAG, NUM_MAG, DELTA_MAG);
	}
	
	public static class Builder {
		
		private FaultSystemRupSet rupSet;
		private double supraSeisBValue;
		
		private IncrementalMagFreqDist totalTargetMFD;

		private SubSeisMoRateReduction subSeisMoRateReduction = SUB_SEIS_MO_RATE_REDUCTION_DEFAULT;
		private DoubleUnaryOperator magDepRelStdDev = MAG_DEP_REL_STD_DEV_DEFAULT;
		private boolean applyDefModelUncertainties = APPLY_DEF_MODEL_UNCERTAINTIES_DEFAULT;
		private boolean sparseGR = SPARSE_GR_DEFAULT;
		private boolean addSectCountUncertainties = ADD_SECT_COUNT_UNCERTAINTIES_DEFAULT;
		private boolean adjustForActualRupSlips = ADJ_FOR_ACTUAL_RUP_SLIPS_DEFAULT;
		private boolean adjustForSlipAlong = ADJ_FOR_SLIP_ALONG_DEFAULT;
		
		private List<? extends DataSectNucleationRateEstimator> dataConstraints;
		private UncertaintyBoundType dataConstraintExpandToBound;
		
		private List<Region> constrainedRegions;
		
		private JumpProbabilityCalc segModel;

		public Builder(FaultSystemRupSet rupSet, double supraSeisBValue) {
			this.rupSet = rupSet;
			this.supraSeisBValue = supraSeisBValue;
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
			if (segModel != null && !sparseGR)
				System.err.println("WARNING: Segmentation models will be matched best if sparseGR == true");
			this.sparseGR = sparseGR;
			return this;
		}
		
		/**
		 * Adds a segmentation model, for which the target MFD will modified to accommodate. This will result in
		 * subsection supra-seismogenic MFDs that aren't perfectly G-R, but are more consistent with the imposed
		 * segmentation constraint.
		 * <p>
		 * We make a couple assumptions when adjusting for a segmentation constraint. First, we assume that the inversion
		 * will use the lease-penalized ruptures in any magnitude bin, so we only make adjustments to the target MFD
		 * when all ruptures in that magnitude bin for that section are subject to segmentation. Second, we adjust for
		 * the natural fall off with magnitude for a G-R, and don't penalize for probabilities below that natural
		 * fall off amount.
		 * 
		 * @param segModel
		 * @return
		 */
		public Builder forSegmentationModel(JumpProbabilityCalc segModel) {
			if (segModel != null && !sparseGR)
				System.err.println("WARNING: Segmentation models will be matched best if sparseGR == true");
			this.segModel = segModel;
			return this;
		}
		
		/**
		 * Some scaling relationships are not be consistent with the way we calculate the total moment required to
		 * satisfy the target slip rate. If enabled, the target MFDs will be modified to account for any discrepancy
		 * between calculated slip rates and the G-R targets 
		 * 
		 * @return
		 */
		public Builder adjustForActualRupSlips(boolean adjustForActualRupSlips, boolean adjustForSlipAlong) {
			if (adjustForSlipAlong)
				Preconditions.checkState(adjustForActualRupSlips,
						"Cannot adjust for slip along without adjusting for average slips as well");
			this.adjustForActualRupSlips = adjustForActualRupSlips;
			this.adjustForSlipAlong = adjustForSlipAlong;
			return this;
		}
		
		/**
		 * Expand uncertainties (both on individual sections and on regional targets) such that the given data constraints
		 * are at least boundType away from the assumed supra-seismogenic MFD.
		 * 
		 * @param dataConstraints
		 * @param boundType
		 * @return
		 */
		public Builder expandUncertaintiesForData(List<? extends DataSectNucleationRateEstimator> dataConstraints,
				UncertaintyBoundType boundType) {
			this.dataConstraints = dataConstraints;
			this.dataConstraintExpandToBound = boundType;
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
			EvenlyDiscretizedFunc refMFD = buildRefXValues(rupSet);
			int NUM_MAG = refMFD.size();
			System.out.println("SupraSeisBValInversionTargetMFDs total MFD range: ["
					+(float)MIN_MAG+","+(float)refMFD.getMaxX()+"]");
			
			ModSectMinMags minMags = rupSet.getModule(ModSectMinMags.class);
			
			int numSects = rupSet.getNumSections();
			
			double[] slipRates = new double[numSects];
			double[] slipRateStdDevs = new double[numSects];
			double[] targetMoRates = new double[numSects];
			double[] targetSupraMoRates = new double[numSects];
			double[] sectFractSupras = new double[numSects];
			
			List<BitSet> sectRupUtilizations = new ArrayList<>();
			
			MinMaxAveTracker fractSuprasTrack = new MinMaxAveTracker();
			
			// sub-seismogenic MFDs for the given b-value
			List<IncrementalMagFreqDist> sectSubSeisMFDs = new ArrayList<>();
			
			// supra-seismogenic MFDs for the given b-value
			List<IncrementalMagFreqDist> sectSupraSeisMFDs = new ArrayList<>();
			
			/*
			 * these are used if we have a segmentation model
			 */
			// map from unique jumps to their segmentation probabilities
			Map<Jump, Double> segJumpProbsMap = null;
			// set of ruptures that can be ignored when building MFDs because they have a zero rate
			BitSet zeroProbRups = new BitSet(rupSet.getNumRuptures());
			// this will track what jumps control what magnitude bins on each section
			SectSegmentationJumpTracker[] sectSegTrackers = null;
			if (segModel != null) {
				ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);

				System.out.println("Pre-processing "+cRups.size()+" ruptures for segmentation calculation");
				if (segModel instanceof BinaryJumpProbabilityCalc) {
					// simple case, hard cutoff
					BinaryJumpProbabilityCalc binary = (BinaryJumpProbabilityCalc)segModel;
					for (int r=0; r<cRups.size(); r++) {
						ClusterRupture rup = cRups.get(r);
						for (Jump jump : rup.getJumpsIterable()) {
							if (!binary.isJumpAllowed(rup, jump, false)) {
								zeroProbRups.set(r);
								break;
							}
						}
					}
				} else {
					// complex case that can have probabilities between 0 and 1
					segJumpProbsMap = new HashMap<>();
					
					sectSegTrackers = new SectSegmentationJumpTracker[numSects];
					for (int s=0; s<numSects; s++)
						sectSegTrackers[s] = new SectSegmentationJumpTracker(refMFD.size());
					rupLoop:
						for (int r=0; r<cRups.size(); r++) {
							ClusterRupture rup = cRups.get(r);
							
							// within a single rupture, the worst jump will control
							Jump worstJump = null;
							double worstProb = 1d;
							for (Jump origJump : rup.getJumpsIterable()) {
								Jump jump;
								if (origJump.toSection.getSectionId() < origJump.fromSection.getSectionId())
									// always store jumps with fromID < toID
									jump = origJump.reverse();
								else
									jump = origJump;
								double jumpProb;
								if (segJumpProbsMap.containsKey(jump)) {
									jumpProb = segJumpProbsMap.get(jump);
								} else {
									jumpProb = segModel.calcJumpProbability(rup, origJump, false);
									Preconditions.checkState(jumpProb >= 0d && jumpProb <= 1d);
									segJumpProbsMap.put(jump, jumpProb);
								}
								if (jumpProb == 1d) {
									// ignore it
									continue;
								} else if (jumpProb == 0d) {
									// zero probability, can skip this rupture entirely
									zeroProbRups.set(r);
									continue rupLoop;
								}
								// we'll need to track this jump, it's between 0 and 1
								if (jumpProb < worstProb) {
									// new worst
									worstProb = jumpProb;
									worstJump = jump;
								}
							}

							double mag = rupSet.getMagForRup(r);
							int magIndex = refMFD.getClosestXIndex(mag);
							
							// tell each section about this rupture
							for (FaultSubsectionCluster cluster : rup.getClustersIterable())
								for (FaultSection sect : cluster.subSects)
									sectSegTrackers[sect.getSectionId()].processRupture(worstJump, worstProb, magIndex);
						}
				}
			}
			
			int[][] sectRupInBinCounts = new int[numSects][refMFD.size()];
			int[] sectMinMagIndexes = new int[numSects];
			int[] sectMaxMagIndexes = new int[numSects];
			
			// first, calculate sub and supra-seismogenic G-R MFDs
			for (int s=0; s<numSects; s++) {
				FaultSection sect = rupSet.getFaultSectionData(s);
				
				double creepReducedSlipRate = sect.getReducedAveSlipRate()*1e-3; // mm/yr -> m/yr
				double creepReducedSlipRateStdDev = sect.getReducedSlipRateStdDev()*1e-3; // mm/yr -> m/yr
				
				double area = rupSet.getAreaForSection(s); // m
				
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
				BitSet utilization = new BitSet();
				for (int r : rupSet.getRupturesForSection(s)) {
					double mag = rupSet.getMagForRup(r);
					if ((float)mag >= (float)sectMinMag) {
						if (zeroProbRups != null && zeroProbRups.get(r))
							// segmentation model precludes this rupture from ever occurring, ignore
							continue;
						minAbove = Math.min(mag, minAbove);
						sectMaxMag = Math.max(sectMaxMag, mag);
						mags.add(mag);
						rups.add(r);
						utilization.set(r);
						sectRupInBinCounts[s][refMFD.getClosestXIndex(mag)]++;
					}
				}
				sectRupUtilizations.add(utilization);
				
				// this will contain the shape of the supra-seismogenic G-R distribution for this section, but the
				// actual a value of this MFD will be set later
				IncrementalMagFreqDist supraGR_shape;
				int minMagIndex, maxMagIndex;
				if (rups.isEmpty()) {
					System.err.println("WARNING: Section "+s+" has no ruptures above the minimum magnitude ("
							+sectMinMag+"): "+sect.getName());
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
						supraGR_shape = SparseGutenbergRichterSolver.getEquivGR(supraGR_shape, mags,
								supraGR_shape.getTotalMomentRate(), supraSeisBValue);
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
				} else if (subSeisMoRateReduction == SubSeisMoRateReduction.SUB_SEIS_B_1) {
					// start with a full G-R with the supra b-value
					GutenbergRichterMagFreqDist fullSupraB = new GutenbergRichterMagFreqDist(
							MIN_MAG, NUM_MAG, DELTA_MAG, targetMoRate, supraSeisBValue);
					
					// copy it to a regular MFD:
					IncrementalMagFreqDist sectFullMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
					for (int i=0; i<fullSupraB.size(); i++)
						sectFullMFD.set(i, fullSupraB.getY(i));
					
					// now correct the sub-seis portion to have the sub-seis b-value
					
					// first create a full MFD with the sub b-value. this will only be used in a relative sense
					GutenbergRichterMagFreqDist fullSubB = new GutenbergRichterMagFreqDist(
							MIN_MAG, NUM_MAG, DELTA_MAG, targetMoRate, 1d); // b=1
					
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
					supraMoRate = sectFullMFD.getTotalMomentRate() - subMoRate;
					
					// use supra-seis MFD shape from above
					supraGR_shape.scaleToTotalMomentRate(supraMoRate);
					
					supraSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
					for (int i=0; i<supraGR_shape.size(); i++)
						supraSeisMFD.set(i+minMagIndex, supraGR_shape.getY(i));
					
					fractSupra = supraMoRate/targetMoRate;
					
					// scale target slip rates by the fraction that is supra-seismognic
					slipRates[s] = creepReducedSlipRate*fractSupra;
					slipRateStdDevs[s] = creepReducedSlipRateStdDev*fractSupra;
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
			
			if (segModel != null) {
				if (segModel instanceof BinaryJumpProbabilityCalc) {
					System.out.println("Segmentation model is simple (hard cutoff) and was"
							+ " already applied at MFD creation.");
				} else {
					System.out.println("Adjusting section mfds for segmentation");
					
					/*
					 * Segmentation constraints are applied in the inversion as a fraction of the total participation
					 * rate on either side of a jump. The segmentation model will often imply rates less than the G-R
					 * budget for those ruptures, in which case we adjust the targets to account for the segmentation
					 * model. We make the simplifying assumption that the inversion will use the most-available rupture
					 * in each magnitude bin, so if a bin contains ruptures not affected by segmentation, we assume
					 * that bin is not affected by segmentation.
					 * 
					 * For bins that are affected by segmentation, we first compute the model-implied participation
					 * rate for each jump in that magnitude bin. That participation rate is the segmentation probability
					 * times the lesser of the participation rate on either side of that jump. This is an upper bound:
					 * the participation rate of ruptures on our section in this magnitude bin cannot exceed the
					 * segmentation rate. We then convert that target participation rate to a nucleation rate,
					 * and set the bin-specific rate to the lesser of the segmentation-implied target nucleation rate
					 * and the original nucleation rate before adjustment.
					 * 
					 * There are a couple complications:
					 * 
					 * First of all, setting the rate on one section relies on the rate of other sections, which could
					 * subsequently be updated with their own adjustments. We mitigate this by does the corrections
					 * iteratively; 20 iterations is plenty for MFD stability to 4-byte precision.
					 * 
					 * Second, multiple jumps can affect a single magnitude bin. In that case, we first compute the
					 * target nucleation rate for each bin implied by each jump, and then choose the greatest one
					 * as our actual target (again assuming that the inversion will use the most-available ruptures).
					 */

					int segBins = 0;
					int segBinsAvail = 0;
					int segSects = 0; 
					
					// we're going to need section participation rates, which we will determine by multiplying
					// bin rates by the average rupture area in that bin, and then dividing by the section area.
					// 
					// these are section-sepecific scale factors
					double[][] sectNuclToParticScalars = new double[numSects][];
					for (int s=0; s<numSects; s++) {
						int minMagIndex = sectMinMagIndexes[s];
						int maxMagIndex = sectMaxMagIndexes[s];
						
						double[] nuclToParticScalars = new double[1+maxMagIndex-minMagIndex];
						double[] avgBinAreas = new double[nuclToParticScalars.length];
						int[] avgCounts = new int[avgBinAreas.length];
						
						BitSet utilization = sectRupUtilizations.get(s);
						// loop over ruptures for which this section participates
						for (int r = utilization.nextSetBit(0); r >= 0; r = utilization.nextSetBit(r+1)) {
							int index = refMFD.getClosestXIndex(rupSet.getMagForRup(r))-minMagIndex;
							avgCounts[index]++;
							avgBinAreas[index] += rupSet.getAreaForRup(r);
						}
						double sectArea = rupSet.getAreaForSection(s);
						for (int m=0; m<nuclToParticScalars.length; m++) {
							if (avgCounts[m] > 0) {
								avgBinAreas[m] /= avgCounts[m];
								nuclToParticScalars[m] = avgBinAreas[m]/sectArea;
							}
						}
						sectNuclToParticScalars[s] = nuclToParticScalars;
					}
					
					// create working copy of supra-seis mfds
					List<IncrementalMagFreqDist> modSupraSeisMFDs = new ArrayList<>();
					// also keep track of the original fraction that each bin contributes to the total nucleation rate
					List<double[]> origBinContributions = new ArrayList<>();
					for (int s=0; s<numSects; s++) {
						if (sectSegTrackers[s].notConntrolled() || targetMoRates[s] == 0d || slipRates[s] == 0d) {
							// section is not controlled by any seg-affected ruptures, or is zero rate
							modSupraSeisMFDs.add(null);
							origBinContributions.add(null);
						} else {
							IncrementalMagFreqDist mfd = sectSupraSeisMFDs.get(s).deepClone();
							modSupraSeisMFDs.add(mfd);
							double[] contribFracts = new double[mfd.size()];
							double totRate = mfd.calcSumOfY_Vals();
							for (int i=0; i<contribFracts.length; i++)
								contribFracts[i] = mfd.getY(i)/totRate;
							origBinContributions.add(contribFracts);
						}
					}
					
					Map<Jump, Double> curJumpMaxParticipationRates = new HashMap<>();
					
					// iteratively solve for modified MFDs. 20 iterations is plenty to match nucleation rates with high
					// iteration counts (e.g., 200) to floating point precision, and is still nearly instantaneous
					for (int i=0; i<20; i++) {
						// calculate current nucleation and participation rates for each section at the start of
						// each iteration
						double[] curNuclRates = new double[numSects];
						double[] curParticRates = new double[numSects];
						for (int s=0; s<numSects; s++) {
							double[] nuclToParticScalars = sectNuclToParticScalars[s];
							IncrementalMagFreqDist supraMFD = sectSupraSeisMFDs.get(s);
							for (int m=0; m<nuclToParticScalars.length; m++) {
								double binRate = supraMFD.getY(m + sectMinMagIndexes[s]);
								curNuclRates[s] += binRate;
								curParticRates[s] += binRate*nuclToParticScalars[m];
							}
							Preconditions.checkState(curNuclRates[s] > 0,
									"Bad curNuclRate=%s, iteration %s, s=%s, curParticRate=%s",
									curNuclRates[s], i, s, curParticRates[s]);
							Preconditions.checkState(curNuclRates[s] > 0,
									"Bad curParticRates=%s, iteration %s, s=%s, curNuclRates=%s",
									curParticRates[s], i, s, curNuclRates[s]);
						}
						
						// calculate the current jump participation rate, which is it's probability times the lower of
						// the participation rates on either side of the jump
						for (Jump jump : segJumpProbsMap.keySet()) {
							double jumpProb = segJumpProbsMap.get(jump);
							Preconditions.checkState(jumpProb > 0d);
							// lesser of the participation rate on either side of the jump
							double sectRate = Math.min(curParticRates[jump.fromSection.getSectionId()],
									curParticRates[jump.toSection.getSectionId()]);
							curJumpMaxParticipationRates.put(jump, jumpProb*sectRate);
						}
						
						// now apply segmentation adjustments
						for (int s=0; s<numSects; s++) {
							SectSegmentationJumpTracker tracker = sectSegTrackers[s];
							if (tracker.notConntrolled() || targetMoRates[s] == 0d || slipRates[s] == 0d)
								// section is not controlled by any seg-affected ruptures
								continue;
							
//							boolean debug = s == 1832;
							boolean debug = false;
							
							// our woking copy of the supra-seis MFD
							IncrementalMagFreqDist modMFD = modSupraSeisMFDs.get(s);
							// original bin rates expressed as a fraction of total nucleation rate
							double[] origBinContribution = origBinContributions.get(s);
							// bin-specific scalars to go from nucleation rates to participation rates
							double[] nuclToParticScalars = sectNuclToParticScalars[s];
							
							if (debug) {
								System.out.println("Debug "+s+" iteration "+i);
								System.out.println("Pre-loop MFD");
								for (int j=sectMinMagIndexes[s]; j<=sectMaxMagIndexes[s]; j++)
									System.out.println("\t"+(float)modMFD.getX(j)+"\t"+(float)modMFD.getY(j));
							}
							
							boolean changed = false;
							
							Jump[] assignedJumps = new Jump[1+sectMaxMagIndexes[s]-sectMinMagIndexes[s]];
							double[] assignedJumpRates = new double[assignedJumps.length];
							Map<Jump, List<Integer>> fullJumpBins = tracker.getJumpBinsMap(sectMinMagIndexes[s]);
							Map<Jump, List<Integer>> jumpBinAssignments = null;
							
							/*
							 * Loop through the calculation twice. The first time, we're deciding which jump is the
							 * bottleneck for each bin for the case of multiple jumps affecting a single bin. In the
							 * second time through, we actually modify the rates. 
							 */
							for (boolean assignment : new boolean[] { true, false } ) {
								// map of jumps to affected bins
								Map<Jump, List<Integer>> jumpBins = assignment ? fullJumpBins : jumpBinAssignments;
								
								for (Jump jump : jumpBins.keySet()) {
									List<Integer> bins = jumpBins.get(jump);
									Preconditions.checkState(bins.size() > 0);
									
									// this is our target segmentation implied participation rate for this jump, which
									// we can't exceed
									double segParticTarget = curJumpMaxParticipationRates.get(jump);
									if (segParticTarget == 0d)
										// this jump uses zero-rates sections and can be ignored
										continue;
									
									// calculate the current nucleation and participation rates for these bins
									double curJumpNuclRate = 0d;
									double curJumpParticRate = 0d;
									for (int bin : bins) {
										double binRate = modMFD.getY(bin);
										curJumpNuclRate += binRate;
//										System.out.println("bin="+bin+", minMag="+sectMinMagIndexes[s]+", refMFD.size()="
//												+refMFD.size()+", tracker.binOffset="+tracker.binOffset);
										curJumpParticRate += binRate*nuclToParticScalars[bin-sectMinMagIndexes[s]];
									}
									
//									System.out.println(i+". s="+s+", jump="+jump);
									
									Preconditions.checkState(curJumpNuclRate > 0,
											"Jump nucleation rate is 0! bins=%s,\n\t%s", bins, modMFD);
									
									// convert that participation target to a nucleation rate on this section
									double segNuclTarget = 0d;
									// also keep track of the bin-specific nucleation rate target for this constraint
									List<Double> binnedSegTargetNuclRates = new ArrayList<>();
									for (int bin : bins) {
										double binRate = modMFD.getY(bin);
										double particScalar = nuclToParticScalars[bin-sectMinMagIndexes[s]];
										// fraction of the participation rate this bin is responsible for
										double binParticFract = binRate*particScalar/curJumpParticRate;
										double binTargetParticRate = segParticTarget*binParticFract;
										double binTargetNuclRate = binTargetParticRate/particScalar;
										segNuclTarget += binTargetNuclRate;
										binnedSegTargetNuclRates.add(binTargetNuclRate);
									}
									Preconditions.checkState(segNuclTarget > 0d,
											"Segmentation implied nucleation rate is zero for s=%s, jump=%s, "
											+ "segParticTarget=%s\n\tmodMFD: %s",
											s, jump, segParticTarget, modMFD);
									
									if (assignment) {
										// we're still figuring out what controls what, just note the target nucleation
										// rate for this jump on each involved section
										
										for (int b=0; b<bins.size(); b++) {
											double binTargetNuclRate = binnedSegTargetNuclRates.get(b);
											int index = bins.get(b) - sectMinMagIndexes[s];
											if (binTargetNuclRate > assignedJumpRates[index]) {
												// this jump provides more available nucleation rate to this bin than
												// anything we have encountered thus far
												assignedJumpRates[index] = binTargetNuclRate;
												assignedJumps[index] = jump;
											}
										}
									} else {
										// 2nd round, we're applying it to assigned bins
										
										// calculate the nucleation rate for these sections according to the original G-R,
										// but scaled to match our current nucleation rate
										double origNuclTarget = 0d;
										for (int bin : bins)
											origNuclTarget += origBinContribution[bin]*curNuclRates[s];
										
										Preconditions.checkState(origNuclTarget > 0d,
												"Segmentation implied nucleation rate is zero for s=%s, jump=%s, "
												+ "\n\tmodMFD: %s",
												s, jump,  modMFD);
										
										// segmentation rate for these bins on this section should be the lesser of those two
										double targetNuclRate = Math.min(segNuclTarget, origNuclTarget);
										
										if (debug) {
											List<Integer> sorted = new ArrayList<>(bins);
											Collections.sort(sorted);
											System.out.println("Jump: "+jump+", prob="+segJumpProbsMap.get(jump));
											System.out.print("\t"+sorted.size()+" bins:");
											for (int bin : sorted)
												System.out.print(" "+(float)refMFD.getX(bin));
											System.out.println();
											System.out.println("\tTargets: partic="+segParticTarget+", nucl="+segNuclTarget);
											System.out.println("\tCur jump rate: partic="+curJumpParticRate+", nucl="+curJumpNuclRate);
											System.out.println("\tOrig nucl target="+origNuclTarget);
										}
										
										if ((float)targetNuclRate != (float)curJumpNuclRate) {
											changed = true;
											// now rescale these bins to match
											double scalar = targetNuclRate/curJumpNuclRate;
											if (debug) {
												System.out.println("\tscalar = "+targetNuclRate+" / "+curJumpNuclRate+" = "+scalar);
											}
											Preconditions.checkState(Double.isFinite(scalar),
													"Bad scalar=%s for segNuclTarget=%s, origNuclTarget=%s, curJumpJuclRate=%s",
													scalar, segNuclTarget, origNuclTarget, curJumpNuclRate);
											for (int bin : bins)
												modMFD.set(bin, modMFD.getY(bin)*scalar);
										}
									}
								}
								
								if (assignment) {
									// process final assignments: jump with the most available rate in each bin
									jumpBinAssignments = new HashMap<>();
									for (int b=0; b<assignedJumps.length; b++) {
										if (assignedJumps[b] != null) {
											int index = sectMinMagIndexes[s]+b;
											List<Integer> jumpMapped = jumpBinAssignments.get(assignedJumps[b]);
											if (jumpMapped == null) {
												jumpMapped = new ArrayList<>();
												jumpBinAssignments.put(assignedJumps[b], jumpMapped);
											}
											jumpMapped.add(index);
										}
									}
								}
							}
							
							if (changed) {
								if (debug) {
									System.out.println("Post-loop MFD");
									for (int j=sectMinMagIndexes[s]; j<=sectMaxMagIndexes[s]; j++)
										System.out.println("\t"+(float)modMFD.getX(j)+"\t"+(float)modMFD.getY(j));
									System.out.println("\tWill scale mooment to match "+targetSupraMoRates[s]
											+" (cur="+modMFD.getTotalMomentRate()+")");
								}
								// now rescale the MFD to match the original target moment rate
								modMFD.scaleToTotalMomentRate(targetSupraMoRates[s]);
							}
						} // end section loop
					} // end iteration loop

					double sumOrigRate = 0d;
					double sumModRate = 0d;
					for (int s=0; s<numSects; s++) {
						IncrementalMagFreqDist origMFD = sectSupraSeisMFDs.get(s);
						sumOrigRate += origMFD.calcSumOfY_Vals();
						IncrementalMagFreqDist modMFD = modSupraSeisMFDs.get(s);
						if (modMFD == null) {
							sumModRate += origMFD.calcSumOfY_Vals();
						} else {
							sumModRate += modMFD.calcSumOfY_Vals();
							int numChanged = 0;
							for (int i=0; i<modMFD.size(); i++)
								if ((float)modMFD.getY(i) != (float)origMFD.getY(i))
									numChanged++;
							segBins += numChanged;
							if (numChanged > 0)
								segSects++;
//							for (int[] bins : sectSegTrackers[s].getControllingJumpBinsMap(sectMinMagIndexes[s]).values())
//								segBinsAvail += bins.length;
							sectSupraSeisMFDs.set(s, modMFD);
							segBinsAvail += sectSegTrackers[s].numControlledBins;
						}
					}
					
					System.out.println("Segmentation constraint affected "+segSects+"/"+numSects
							+" sections and "+segBins+"/"+segBinsAvail+" bins");
					double fractDiff = (sumModRate-sumOrigRate)/sumOrigRate;
					String pDiffStr = new DecimalFormat("0.00%").format(fractDiff);
					if (fractDiff > 0)
						pDiffStr = "+"+pDiffStr;
					System.out.println("\tTotal supra-seis rate change: "
							+(float)sumOrigRate+" -> "+(float)sumModRate+" ("+pDiffStr+")");
				}
			}
			
			System.out.println("Fraction supra-seismogenic stats: "+fractSuprasTrack);
			
			if (adjustForActualRupSlips) {
				System.out.println("Adjusting targets to match actual rupture slips");
				Preconditions.checkState(sparseGR, "Scaling relationship adjustments only work with sparseGR=true");
				AveSlipModule aveSlips = rupSet.requireModule(AveSlipModule.class);
				SlipAlongRuptureModel slipAlong = adjustForSlipAlong ? rupSet.requireModule(SlipAlongRuptureModel.class) : null;
				if (slipAlong != null && slipAlong.isUniform())
					// it's a uniform model, don't bother calculating slip along
					slipAlong = null;
				
				double[] calcSectSlips = new double[numSects];
				for (int r=0; r<rupSet.getNumRuptures(); r++) {
					List<Integer> sectIDs = rupSet.getSectionsIndicesForRup(r);
					double[] slips;
					if (slipAlong == null) {
						slips = new double[sectIDs.size()];
						double aveSlip = aveSlips.getAveSlip(r);
						for (int i=0; i<slips.length; i++)
							slips[i] = aveSlip;
					} else {
						slips = slipAlong.calcSlipOnSectionsForRup(rupSet, aveSlips, r);
						Preconditions.checkState(slips.length == sectIDs.size());
					}
					int magIndex = refMFD.getClosestXIndex(rupSet.getMagForRup(r));
					double rupArea = rupSet.getAreaForRup(r);
					for (int i=0; i<slips.length; i++) {
						int s = sectIDs.get(i);
						if (sectRupUtilizations.get(s).get(r)) {
							// this section did end up using this rupture
							double sectBinNuclRate = sectSupraSeisMFDs.get(s).getY(magIndex);
							if (sectBinNuclRate > 0d) {
								double particRate = sectBinNuclRate*rupArea/rupSet.getAreaForSection(s);
								calcSectSlips[s] += slips[i]*particRate/(double)sectRupInBinCounts[s][magIndex];
							}
						}
					}
				}
				MinMaxAveTracker slipAdjustTrack = new MinMaxAveTracker();
				for (int s=0; s<calcSectSlips.length; s++) {
					if (slipRates[s] > 0 && targetMoRates[s] > 0) {
						double slipRatio = slipRates[s] / calcSectSlips[s];
						slipAdjustTrack.addValue(slipRatio);
						
						sectSupraSeisMFDs.get(s).scale(slipRatio);
						double origSubMoRate = targetMoRates[s] - targetSupraMoRates[s];
						targetSupraMoRates[s] *= slipRatio;
						targetSupraMoRates[s] = origSubMoRate + targetSupraMoRates[s];
						sectFractSupras[s] = targetSupraMoRates[s]/targetSupraMoRates[s];
					}
				}
				System.out.println("Actual slip model adjustment stats: "+slipAdjustTrack);
			}
			
			if (subSeisMoRateReduction == SubSeisMoRateReduction.SYSTEM_AVG_IMPLIED_FROM_SUPRA_B) {
				// need to re-balance to use average supra-seis fract
				double sumSupraMo = StatUtils.sum(targetSupraMoRates);
				double sumTotMo = StatUtils.sum(targetMoRates);
				double avgSupraSeis = sumSupraMo/sumTotMo;
				
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
			
			SectSlipRates sectSlipRates;
			if (subSeisMoRateReduction == SubSeisMoRateReduction.FROM_INPUT_SLIP_RATES) {
				sectSlipRates = rupSet.requireModule(SectSlipRates.class);
			} else {
				sectSlipRates = SectSlipRates.precomputed(rupSet, slipRates, slipRateStdDevs);
				// give the newly computed target slip rates to the rupture set for use in inversions
				rupSet.addModule(sectSlipRates);
			}
			if (slipOnly)
				// shortcut for if we only need slip rates
				return new SupraSeisBValInversionTargetMFDs(rupSet, supraSeisBValue, totalTargetMFD, null,
						null, null, null, null, sectSlipRates);
			
			List<EvenlyDiscretizedFunc> defModMFDStdDevs = null;
			if (applyDefModelUncertainties) {
				defModMFDStdDevs = new ArrayList<>();
				for (int s=0; s<numSects; s++) {
					EvenlyDiscretizedFunc defModStdDevs = null;
					if (slipRateStdDevs[s]  > 0d && targetSupraMoRates[s] > 0d) {
						if (slipRates[s] > 0d) {
							// simple case for nonzero slip rates
							IncrementalMagFreqDist supraSeisMFD = sectSupraSeisMFDs.get(s);
							
							double relDefModStdDev = slipRateStdDevs[s]/slipRates[s];
							
							// use the slip rate standard deviation. this simple treatment is confirmed to be the exact same as if
							// we were to construct new GR distributions plus and minus one standard deviation and then calculate
							// a standard deviation from those bounds
							defModStdDevs = new EvenlyDiscretizedFunc(MIN_MAG, NUM_MAG, DELTA_MAG);
							for (int i=0; i<supraSeisMFD.size(); i++)
								defModStdDevs.set(i, supraSeisMFD.getY(i)*relDefModStdDev);
						} else {
							// slip rate is zero, more complicated, calculate a GR at + 1 sigma and set that as
							// the standard deviation. can just do the supra-seismogenic portion
							double area = rupSet.getAreaForSection(s); // m
							
							// convert it to a moment rate
							double targetMoRate = FaultMomentCalc.getMoment(area, slipRateStdDevs[s]);
							List<Double> mags = new ArrayList<>();
							fillInRupsAndMags(sectRupUtilizations.get(s), null, mags);
							defModStdDevs = SparseGutenbergRichterSolver.getEquivGR(
									refMFD, mags, targetMoRate, supraSeisBValue);
						}
					}
					defModMFDStdDevs.add(defModStdDevs);
				}
			}
			
			List<IncrementalMagFreqDist> dataImpliedSectSupraSeisMFDs = null;
			if (dataConstraints != null) {
				Preconditions.checkNotNull(dataConstraintExpandToBound);
				
				dataImpliedSectSupraSeisMFDs = new ArrayList<>();
				
				ExecutorService exec = Executors.newFixedThreadPool(FaultSysTools.defaultNumThreads());
				
				List<Future<DataEstCallable>> futures = new ArrayList<>();
				
				for (int s=0; s<numSects; s++) {
					FaultSection sect = rupSet.getFaultSectionData(s);
					
					dataImpliedSectSupraSeisMFDs.add(null);
					
					if (targetSupraMoRates[s] == 0d || slipRates[s] == 0d)
						continue;
					
					// see if we have any data constraints that imply a different MFD for this section,
					// we will later adjust uncertainties accordingly
					List<DataSectNucleationRateEstimator> constraints = new ArrayList<>();
					for (DataSectNucleationRateEstimator constraint : dataConstraints)
						if (constraint.appliesTo(sect))
							constraints.add(constraint);
					
					if (!constraints.isEmpty()) {
						System.out.println("\tCalculating MFDs implied by "+constraints.size()
							+" data constraints for sect "+s+". "+sect.getSectionName());
						
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
				
				for (Future<DataEstCallable> future : futures) {
					DataEstCallable call;
					try {
						call = future.get();
					} catch (InterruptedException | ExecutionException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					dataImpliedSectSupraSeisMFDs.set(call.sect.getSectionId(), call.impliedMFD);
				}
				
				System.out.println("Done with data estimation.");
				
				exec.shutdown();
			}
			
			UncertainIncrMagFreqDist totalOnFaultSupra = calcRegionalSupraTarget(refMFD, null, sectSupraSeisMFDs,
					defModMFDStdDevs, dataImpliedSectSupraSeisMFDs);
			
			List<UncertainIncrMagFreqDist> mfdConstraints;
			if (constrainedRegions != null) {
				Preconditions.checkState(!constrainedRegions.isEmpty(), "Empty region list supplied");
				mfdConstraints = new ArrayList<>();
				for (Region region : constrainedRegions)
					mfdConstraints.add(calcRegionalSupraTarget(refMFD, region, sectSupraSeisMFDs,
							defModMFDStdDevs, dataImpliedSectSupraSeisMFDs));
			} else {
				mfdConstraints = List.of(totalOnFaultSupra);
			}
			
			// now set individual supra-seis MFD uncertainties
			List<UncertainIncrMagFreqDist> uncertSectSupraSeisMFDs = new ArrayList<>();
			for (int s=0; s<numSects; s++) {
				IncrementalMagFreqDist sectSupraMFD = sectSupraSeisMFDs.get(s);
				
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
						System.out.println("Adjusting MFD for s="+s+" to account for data constraints");
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
				for (IncrementalMagFreqDist subSeisMFD : sectSubSeisMFDs)
					totalOnFaultSub.addIncrementalMagFreqDist(subSeisMFD);
				
				subSeismoMFDs = new SubSeismoOnFaultMFDs(sectSubSeisMFDs);
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
			
			return new SupraSeisBValInversionTargetMFDs(rupSet, supraSeisBValue, totalTargetMFD, totalOnFaultSupra,
					totalOnFaultSub, mfdConstraints, subSeismoMFDs, uncertSectSupraSeisMFDs, sectSlipRates);
		}
		
		private static class SectSegmentationJumpTracker {
			
			private BitSet unityProbBins;
			private Map<Integer, HashSet<Jump>> binJumps;
			private int numControlledBins = 0;
			private Map<Jump, List<Integer>> jumpBinsMap;
			
			public SectSegmentationJumpTracker(int numBins) {
				this.unityProbBins = new BitSet(numBins);
				binJumps = new HashMap<>();
			}
			
			public boolean controlled() {
				return numControlledBins > 0;
			}
			
			public boolean notConntrolled() {
				return numControlledBins == 0;
			}
			
			public void processRupture(Jump worstJump, double worstJumpProb, int magIndex) {
				if (unityProbBins.get(magIndex))
					// this bin already has a rupture without segmentation
					return;
				if (worstJumpProb == 1d) {
					// not controlled by segmentation
					HashSet<Jump> prev = binJumps.remove(magIndex);
					if (prev != null)
						// was controlled, now isn't
						numControlledBins--;
					unityProbBins.set(magIndex);
				} else {
					Preconditions.checkNotNull(worstJump);
					HashSet<Jump> myBinJumps = binJumps.get(magIndex);
					if (myBinJumps == null) {
						// under control for the first time
						numControlledBins++;
						myBinJumps = new HashSet<>();
						binJumps.put(magIndex, myBinJumps);
					}
					myBinJumps.add(worstJump);
				}
			}
			
			public Map<Integer, HashSet<Jump>> getBinJumpMappings() {
				return binJumps;
			}
			
			public Map<Jump, List<Integer>> getJumpBinsMap(int sectMinIndex) {
				if (jumpBinsMap != null)
					return jumpBinsMap;
				Preconditions.checkState(controlled());
				Map<Jump, List<Integer>> map = new HashMap<>();
				for (int index : binJumps.keySet()) {
					for (Jump jump : binJumps.get(index)) {
						if (index >= sectMinIndex) {
							List<Integer> list = map.get(jump);
							if (list == null) {
								list = new ArrayList<>();
								map.put(jump, list);
							}
							list.add(index);
						}
					}
				}
				jumpBinsMap = map;
				return jumpBinsMap;
			}
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
			private List<DataSectNucleationRateEstimator> constraints;
			private IncrementalMagFreqDist supraSeisMFD;
			private List<Double> mags;
			private List<Integer> rups;
			private UncertainDataConstraint moRateBounds;
			
			private IncrementalMagFreqDist impliedMFD;

			public DataEstCallable(FaultSection sect, List<DataSectNucleationRateEstimator> constraints,
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
				
				for (DataSectNucleationRateEstimator constraint : constraints) {
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
					UncertaintyBoundType boundType = null;
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
								if (mfd instanceof UncertainBoundedIncrMagFreqDist) {
									bounded = (UncertainBoundedIncrMagFreqDist)mfd;
									boundType = bounded.getBoundType();
								} else {
									boundType = UncertaintyBoundType.ONE_SIGMA;
									bounded = ((UncertainIncrMagFreqDist)mfd).estimateBounds(boundType);
								}
							} else {
								bounded = ((UncertainIncrMagFreqDist)mfd).estimateBounds(boundType);
							}
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
		 *  Set this to true to sum deformation model uncertainties in variance space, or false to sum stanard deviations
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
					Preconditions.checkState(supraMFD.getMinX() == MIN_MAG);
					Preconditions.checkState(supraMFD.getDelta() == DELTA_MAG);
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
					System.out.println("\t\tRelative particpation std dev for M="
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
						Preconditions.checkState(impliedMFD.getMinX() == MIN_MAG);
						Preconditions.checkState(impliedMFD.getDelta() == DELTA_MAG);
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
						sumImpliedMFD, sumLowerMFD, sumUpperMFD, dataConstraintExpandToBound);
				
				// adjust the standard deviations of the regional mfd to reach the implied MFD
				System.out.println("Adjusting regional MFD to match data bounds");
				uncertainMFD = adjustForDataImpliedBounds(uncertainMFD, impliedMFD, true);
			}
			
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
			return uncertainMFD;
		}
		
		private UncertainBoundedIncrMagFreqDist adjustForDataImpliedBounds(UncertainIncrMagFreqDist mfd,
				IncrementalMagFreqDist impliedMFD, boolean verbose) {
			Preconditions.checkState(mfd.size() == impliedMFD.size());
			Preconditions.checkState((float)mfd.getMinX() == (float)impliedMFD.getMinX());
			Preconditions.checkState((float)mfd.getDelta() == (float)impliedMFD.getDelta());
			
			UncertainBoundedIncrMagFreqDist boundedImplied = null;
			if (impliedMFD instanceof UncertainBoundedIncrMagFreqDist)
				boundedImplied = (UncertainBoundedIncrMagFreqDist)impliedMFD;
			else if (impliedMFD instanceof UncertainIncrMagFreqDist)
				boundedImplied = ((UncertainIncrMagFreqDist)impliedMFD).estimateBounds(dataConstraintExpandToBound);

			
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
					double impliedStdDev = dataConstraintExpandToBound.estimateStdDev(rate, rate-diff, rate+diff);
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
			System.out.println("\tImplied std dev range:\t["+(float)stdDevTrack.getMin()+","+(float)stdDevTrack.getMax()
					+"], avg="+(float)stdDevTrack.getAverage()+"\t\trel range:\t["+(float)relStdDevTrack.getMin()
					+","+(float)relStdDevTrack.getMax()+"], avg="+(float)relStdDevTrack.getAverage());
			return new UncertainBoundedIncrMagFreqDist(mfd, lower, upper, UncertaintyBoundType.ONE_SIGMA);
		}
	}
	
	private double supraSeisBValue;
	private List<UncertainIncrMagFreqDist> supraSeismoOnFaultMFDs;

	private SectSlipRates sectSlipRates;

	private SupraSeisBValInversionTargetMFDs(FaultSystemRupSet rupSet, double supraSeisBValue,
			IncrementalMagFreqDist totalRegionalMFD, UncertainIncrMagFreqDist onFaultSupraSeisMFD,
			IncrementalMagFreqDist onFaultSubSeisMFD, List<UncertainIncrMagFreqDist> mfdConstraints,
			SubSeismoOnFaultMFDs subSeismoOnFaultMFDs, List<UncertainIncrMagFreqDist> supraSeismoOnFaultMFDs,
			SectSlipRates sectSlipRates) {
		super(rupSet, totalRegionalMFD, onFaultSupraSeisMFD, onFaultSubSeisMFD, null, mfdConstraints,
				subSeismoOnFaultMFDs);
		this.supraSeisBValue = supraSeisBValue;
		this.supraSeismoOnFaultMFDs = supraSeismoOnFaultMFDs;
		this.sectSlipRates = sectSlipRates;
	}

	public SectSlipRates getSectSlipRates() {
		return sectSlipRates;
	}

	public double getSupraSeisBValue() {
		return supraSeisBValue;
	}

	public List<UncertainIncrMagFreqDist> getSectSupraSeisNuclMFDs() {
		return supraSeismoOnFaultMFDs;
	}

	@Override
	public Class<? extends ArchivableModule> getLoadingClass() {
		// load this back in as simple Precomputed
		return InversionTargetMFDs.Precomputed.class;
	}
	
	public static void main(String[] args) throws IOException {
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(
//				new File("/data/kevin/markdown/inversions/fm3_1_u3ref_uniform_reproduce_ucerf3.zip"));
				new File("/data/kevin/markdown/inversions/fm3_1_u3ref_uniform_coulomb.zip"));
		
		InversionTargetMFDs origTargets = rupSet.getModule(InversionTargetMFDs.class);
		SectSlipRates origSlips = rupSet.getModule(SectSlipRates.class);
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(rupSet, new CaliforniaRegions.RELM_TESTING());
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, 1d);
		
//		rupSet = FaultSystemRupSet.buildFromExisting(rupSet).forScalingRelationship(
//				ScalingRelationships.SHAW_2009_MOD).build();
		
		double b = 0.8d;
		Builder builder = new Builder(rupSet, b);
		
		builder.sparseGR(true);
//		builder.magDepDefaultRelStdDev(M->0.1);
		builder.magDepDefaultRelStdDev(M->0.1*Math.pow(10, b*0.5*(M-6)));
		builder.applyDefModelUncertainties(true);
		builder.addSectCountUncertainties(false);
		builder.totalTargetMFD(rupSet.requireModule(InversionTargetMFDs.class).getTotalRegionalMFD());
		builder.subSeisMoRateReduction(SubSeisMoRateReduction.SUB_SEIS_B_1);
		builder.forSegmentationModel(new Shaw07JumpDistProb(1, 3));
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
		builder.adjustForActualRupSlips(false, false);
		
		List<DataSectNucleationRateEstimator> dataConstraints = new ArrayList<>();
		dataConstraints.add(new APrioriSectNuclEstimator(
				rupSet, UCERF3InversionInputGenerator.findParkfieldRups(rupSet), 1d/25d, 0.1d/25d));
		dataConstraints.addAll(PaleoSectNuclEstimator.buildPaleoEstimates(rupSet, true));
		UncertaintyBoundType expandUncertToDataBound = UncertaintyBoundType.ONE_SIGMA;
		builder.expandUncertaintiesForData(dataConstraints, expandUncertToDataBound);
		
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
		
		if (builder.segModel != null) {
			// debug
			int[] debugSects = {
					100, // Bicycle Lake
					1330, // Mono Lake
					1832, // Mojave S
					2063, // San Diego Trough
			};

			builder.forSegmentationModel(null);
			SupraSeisBValInversionTargetMFDs targetNoSeg = builder.build();
			
			SupraSeisBValInversionTargetMFDs targetOrigGR = null;
			if (builder.sparseGR && !builder.adjustForActualRupSlips) {
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
				
				funcs.add(origSupraMFD);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
				
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

}
