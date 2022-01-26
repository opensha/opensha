package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.DoubleUnaryOperator;

import org.apache.commons.math3.stat.StatUtils;
import org.checkerframework.checker.units.qual.min;
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
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
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
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
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
		
		private RuptureProbabilityCalc improbabilityModel;

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
			if (improbabilityModel != null && !sparseGR)
				System.err.println("WARNING: Improbability models will be matched best if sparseGR == true");
			this.sparseGR = sparseGR;
			return this;
		}
		
		/**
		 * Adds an improbability model (often will be a segmentation constraint), for which the target MFD will modified
		 * to accommodate. This will result in subsection supra-seismogenic MFDs that aren't perfectly G-R, but are
		 * more consistent with other inversion constraints.
		 * <p>
		 * We make a couple assumptions when adjusting for an improbability constraint. First, we assume that a given
		 * section will use the least-penalized rupture in any magnitude bin, so we only make adjustments to the target
		 * MFD when all ruptures in that magnitude bin for that section are penalized. Second, we adjust for the natural
		 * fall off with magnitude for a G-R, and don't penalize for probabilities below that natural falloff amount.
		 * 
		 * @param improbabilityModel
		 * @return
		 */
		public Builder forImprobModel(RuptureProbabilityCalc improbabilityModel) {
			if (improbabilityModel != null && !sparseGR)
				System.err.println("WARNING: Improbability models will be matched best if sparseGR == true");
			this.improbabilityModel = improbabilityModel;
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
			
			ClusterRuptures cRups = improbabilityModel == null ? null : rupSet.requireModule(ClusterRuptures.class);
			int improbBins = 0;
			int improbSects = 0;
			
			int[][] sectRupInBinCounts = new int[numSects][refMFD.size()];
			
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
				List<Double> probs = improbabilityModel == null ? null : new ArrayList<>();
				// section minimum magnitude may be below the actual minimum magnitude, check that here
				double minAbove = Double.POSITIVE_INFINITY;
				// also, if we have an improbability constraint, check that the max mag has a non-zero probability
				double sectMaxMag = Double.NEGATIVE_INFINITY;
				BitSet utilization = new BitSet();
				for (int r : rupSet.getRupturesForSection(s)) {
					double mag = rupSet.getMagForRup(r);
					if ((float)mag >= (float)sectMinMag) {
						if (improbabilityModel != null) {
							double prob = improbabilityModel.calcRuptureProb(cRups.get(r), false);
							if (prob == 0d)
								continue;
							probs.add(prob);
						}
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
					// * if improbabilityModel != null, attempts to be consistent with improbability constraint
					// 1e16 below is a placeholder moment and doesn't matter, we're only using the shape
					// the first bin in the MFD is the min supra-seis mag
					supraGR_shape = new GutenbergRichterMagFreqDist(
							refMFD.getX(minMagIndex), 1+maxMagIndex-minMagIndex, refMFD.getDelta(), 1e16, supraSeisBValue);
					
					if (sparseGR) {
						// re-distribute to only bins that actually have ruptures available
						supraGR_shape = SparseGutenbergRichterSolver.getEquivGR(supraGR_shape, mags,
								supraGR_shape.getTotalMomentRate(), supraSeisBValue);
					}
					
					if (improbabilityModel != null) {
						// adjust the G-R to account for the supplied rupture improbability model
						
						// first calculate the maximum probability in each magnitude bin, making the assumption that
						// we'll primarily use that rupture in the inversion
						double[] maxBinnedProbs = new double[supraGR_shape.size()];
						double minProb = 1d;
						double maxProb = 0d;
						boolean binary = true;
						for (int i=0; i<rups.size(); i++) {
							double mag = mags.get(i);
							int index = supraGR_shape.getClosestXIndex(mag);
							double prob = probs.get(i);
							binary = binary && (prob == 0d || prob == 1d);
							minProb = Math.min(prob, minProb);
							maxProb = Math.max(prob, maxProb);
							maxBinnedProbs[index] = Math.max(maxBinnedProbs[index], prob);
						}
						if (maxProb < 1d) {
							Preconditions.checkState(maxProb > 0d,
									"Improbability constraint gives zero for all ruptures for section %s", s);
							// rescale relative to max
							double scalar = 1d/maxProb;
							minProb = 1d;
							for (int m=0; m<maxBinnedProbs.length; m++) {
								maxBinnedProbs[m] *= scalar;
								minProb = Math.min(minProb, maxBinnedProbs[m]);
							}
							maxProb = 1d;
						}
						
						if (minProb < 1d) {
							// affects this section, adjust the target supra-seis MFD shape
							// once again, the actual value here doesn't matter, we use it in a relative sense
							IncrementalMagFreqDist modSupraShape = new IncrementalMagFreqDist(
									supraGR_shape.getMinX(), supraGR_shape.size(), supraGR_shape.getDelta());
							
							// first do a simple adjustment that just multiplies the G-R rate in each bin by the prob
							for (int m=0; m<maxBinnedProbs.length; m++) {
								double prob = maxBinnedProbs[m];
								double origRate = supraGR_shape.getY(m);
								modSupraShape.set(m, origRate*prob);
							}
							
							if (!binary) {
								/*
								 * We need to do a more complicated adjustment that accounts for G-R probabilities
								 * 
								 * We don't want to double count penalties from this constraint and those from the
								 * target G-R. For example, if the constraint says that the relative probability for
								 * ruptures in a bin is 0.001, but G-R already says that the probability in that bin
								 * relative to all others is <0.001, then we shouldn't further penalize that bin (G-R
								 * controls here, not the improbability constraint).
								 * 
								 * Things get a little complicated because if we change the rate in one bin, that
								 * effects the relative G-R share in each other bin, so we do this adjustment
								 * iteratively 20 times, which was tested to be virtually identical to >100 iterations
								 * for the most pathological cases. We also need to calculate an estimated total
								 * participation rate from the nucleation MFD, for which we use the ratio between the
								 * average rupture area in each bin and the section area.
								 */
								
								// TODO this todo is here to make it easy to jump back to this part of the code while
								// it's still in progress
								
								// in each iteration we will rescale the total moment to match the original
								double origMomRate = supraGR_shape.getTotalMomentRate();
								
								// store the original rates as a fraction of total nucleation rate. we'll keep the
								// lesser this original fractional rate and the calculated rate
								double[] origFractRates = new double[supraGR_shape.size()];
								double origTotRate = supraGR_shape.calcSumOfY_Vals();
								for (int i=0; i<origFractRates.length; i++)
									origFractRates[i] = supraGR_shape.getY(i)/origTotRate;
								
								// we'll compare original rates to the total participation rate across all magnitude
								// bins, thus we need to calculate the bin-specific scalars to convert from nuclation
								// rates to particpation rates
								double[] nuclToParticScalars = new double[modSupraShape.size()];
								double[] avgBinAreas = new double[nuclToParticScalars.length];
								int[] avgCounts = new int[avgBinAreas.length];
								for (int rupIndex : rups) {
									int magIndex = modSupraShape.getClosestXIndex(rupSet.getMagForRup(rupIndex));
									avgCounts[magIndex]++;
									avgBinAreas[magIndex] += rupSet.getAreaForRup(rupIndex);
								}
								double sectArea = rupSet.getAreaForSection(s);
								for (int m=0; m<nuclToParticScalars.length; m++) {
									if (avgCounts[m] > 0) {
										avgBinAreas[m] /= avgCounts[m];
										nuclToParticScalars[m] = avgBinAreas[m]/sectArea;
									}
								}
								
//								if (s == 1330) {
//									System.out.println("Original shape:");
//									System.out.print(supraGR_shape);
//									System.out.println("Original modified:");
//									System.out.print(modSupraShape);
//								}
								for (int i=0; i<20; i++) {
									// rescale to match the original moment rate
									modSupraShape.scaleToTotalMomentRate(origMomRate);
									double curTotParticRate = 0d;
									for (int m=0; m<maxBinnedProbs.length; m++)
										curTotParticRate += nuclToParticScalars[m]*modSupraShape.getY(m);
									double curTotNuclRate = modSupraShape.calcSumOfY_Vals();
									boolean changed = false;
									for (int m=0; m<maxBinnedProbs.length; m++) {
										double prob = maxBinnedProbs[m];
										if (prob == 0d || origFractRates[m] == 0)
											continue;
//										double origRate = supraGR_shape.getY(m);
										double origRate = origFractRates[m]*curTotNuclRate;
										
										double probImpliedParticRate = prob*curTotParticRate;
										double probImpliedNuclRate = probImpliedParticRate/nuclToParticScalars[m];
										
										double modRate = Math.min(origRate, probImpliedNuclRate);
										if (!changed)
											changed = modRate == modSupraShape.getY(m);
										modSupraShape.set(m, modRate);
									}
									if (!changed)
										break;
								}
//								if (s == 1330) {
//									System.out.println("Final modified:");
//									System.out.print(modSupraShape);
//								}
							}
							
//							// compute a baseline reduction from one magnitude bin to the next just due to G-R; we won't
//							// penalize bins whose probability is less than this already built in reduction
//							double baselineGR_reduction;
//							if (supraSeisBValue == 0d || supraGR_shape.size() == 1)
//								// b == 0, G-R treats every bin equally
//								baselineGR_reduction = 1d;
//							else if (supraSeisBValue > 0d)
//								// b > 0
//								baselineGR_reduction = trueGR_shape.getY(1)/trueGR_shape.getY(0);
//							else
//								// b < 0 (will probably never be used)
//								baselineGR_reduction = trueGR_shape.getY(0)/trueGR_shape.getY(1);
//							Preconditions.checkState(baselineGR_reduction <= 1d);
//							
//							int affectedBins = 0;
//							for (int m=0; m<maxBinnedProbs.length; m++) {
//								double prob = maxBinnedProbs[m];
//								double origRate = supraGR_shape.getY(m);
//								if (origRate > 0d) {
//									// this was a nonzero bin
//									
//									// we don't want to penalize beyond any normal G-R falloff for this bin, so we
//									// instead take the lesser of the original rate and the product of our bin
//									// probability and the value for this bin with the G-R falloff removed
//									// (origRate/baselineGR_reduction)
//									double modRate = Math.min(origRate, prob*origRate/baselineGR_reduction);
//									modSupraShape.set(m, modRate);
//									if (prob > 0 && modRate < origRate) {
//										affectedBins++;
////										System.out.println(s+". "+sect.getName()+" changing gr["+(float)supraGR_shape.getX(m)
////												+"] from "+(float)origRate+" to "+(float)modRate+" with P="+(float)prob);
//									}
//								}
//							}
							
							// count affected bins
							int affectedBins = 0;
							for (int m=0; m<maxBinnedProbs.length; m++) {
								double origRate = supraGR_shape.getY(m);
								double modRate = modSupraShape.getY(m);
								if (origRate > 0 && modRate < origRate) {
									affectedBins++;
//									System.out.println(s+". "+sect.getName()+" changing gr["+(float)supraGR_shape.getX(m)
//										+"] from "+(float)origRate+" to "+(float)modRate+" with P="+(float)maxBinnedProbs[m]);
								}
							}
							if (affectedBins > 0) {
								improbSects++;
								improbBins += affectedBins;
							}
							supraGR_shape = modSupraShape;
						}
					}
				}
				
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
						"Partitioned moment rate doesn't equal input: %s != %s", (float)targetMoRate, (float)targetMoRateTest);
				
				targetMoRates[s] = targetMoRate;
				targetSupraMoRates[s] = supraMoRate;
				
				fractSuprasTrack.addValue(fractSupra);
				sectFractSupras[s] = fractSupra;
				
				sectSubSeisMFDs.add(subSeisMFD);
				sectSupraSeisMFDs.add(supraSeisMFD);
			}
			
			if (improbabilityModel != null)
				System.out.println("Improbability constraint affected "+improbSects+" sections and "+improbBins+" bins");
			
			System.out.println("Fraction supra-seismogenic stats: "+fractSuprasTrack);
			
			if (adjustForActualRupSlips) {
				System.out.println("Adjusting targets to match actual rupture slips");
				Preconditions.checkState(sparseGR, "Scaling relationship adjustments only work with sparseGR=true");
				AveSlipModule aveSlips = rupSet.requireModule(AveSlipModule.class);
				SlipAlongRuptureModel slipAlong = adjustForSlipAlong ? rupSet.requireModule(SlipAlongRuptureModel.class) : null;
				if (slipAlong != null && slipAlong.isUniform())
					// it's a uniform model, don't bother
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
//				ScalingRelationships.ELLB_SQRT_LENGTH).build();
		
		double b = 0.8d;
		Builder builder = new Builder(rupSet, b);
		
		builder.sparseGR(true);
//		builder.magDepDefaultRelStdDev(M->0.1);
		builder.magDepDefaultRelStdDev(M->0.1*Math.pow(10, b*0.5*(M-6)));
		builder.applyDefModelUncertainties(true);
		builder.addSectCountUncertainties(false);
		builder.totalTargetMFD(rupSet.requireModule(InversionTargetMFDs.class).getTotalRegionalMFD());
		builder.subSeisMoRateReduction(SubSeisMoRateReduction.SUB_SEIS_B_1);
		builder.forImprobModel(new Shaw07JumpDistProb(1, 4));
//		builder.forImprobModel(MaxJumpDistModels.ONE.getModel(rupSet));
//		builder.adjustForActualRupSlips(true, false);
		builder.adjustForActualRupSlips(false, false);
		
//		List<DataSectNucleationRateEstimator> dataConstraints = new ArrayList<>();
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
		
		if (builder.improbabilityModel != null) {
			// debug
//			int debugSect = 2063;
			int debugSect = 1330;
			IncrementalMagFreqDist improbSupraMFD = target.supraSeismoOnFaultMFDs.get(debugSect);
			
			builder.forImprobModel(null);
			IncrementalMagFreqDist origSupraMFD = builder.build().supraSeismoOnFaultMFDs.get(debugSect);
			
			List<IncrementalMagFreqDist> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			if (builder.sparseGR && !builder.adjustForActualRupSlips) {
				// rebuild without sparse GR
				builder.sparseGR(false);
				IncrementalMagFreqDist origGR = builder.build().supraSeismoOnFaultMFDs.get(debugSect);
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
