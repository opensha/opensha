package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.MFDInversionConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionTargetMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModSectMinMags;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SubSeismoOnFaultMFDs;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.APrioriSectNuclEstimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.DataSectNucleationRateEstimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.PaleoSectNuclEstimator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.magdist.SparseGutenbergRichterSolver;
import org.opensha.sha.magdist.SummedMagFreqDist;

import com.google.common.base.Preconditions;

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
	
	public static final boolean SPARSE_GR_DEFAULT = true;
	
	public static final boolean ADD_SECT_COUNT_UNCERTAINTIES_DEFAULT = false;
	
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
		
		private List<? extends DataSectNucleationRateEstimator> dataConstraints;
		private UncertaintyBoundType dataConstraintExpandToBound;
		
		private List<Region> constrainedRegions;

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
			this.sparseGR = sparseGR;
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
			
			List<List<Integer>> sectRups = new ArrayList<>();
			List<List<Double>> sectMags = new ArrayList<>();
			
			MinMaxAveTracker fractSuprasTrack = new MinMaxAveTracker();
			
			// sub-seismogenic MFDs for the given b-value
			List<IncrementalMagFreqDist> sectSubSeisMFDs = new ArrayList<>();
			
			// supra-seismogenic MFDs for the given b-value
			List<IncrementalMagFreqDist> sectSupraSeisMFDs = new ArrayList<>();
			
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
				// make sure we actually have a rupture at that magnitude, otherwise there can be empty bins without
				// any rupture at/above the section minimum magnitude but below the first rupture's bin
				double minAbove = Double.POSITIVE_INFINITY;
				for (int r : rupSet.getRupturesForSection(s)) {
					double mag = rupSet.getMagForRup(r);
					if ((float)mag >= (float)sectMinMag) {
						minAbove = Math.min(mag, minAbove);
						mags.add(mag);
						rups.add(r);
					}
				}
				sectMinMag = minAbove;
				double sectMaxMag = rupSet.getMaxMagForSection(s);
				
				sectRups.add(rups);
				sectMags.add(mags);
				
				// construct a full G-R including sub-seismogenic ruptures
				int minMagIndex = refMFD.getClosestXIndex(sectMinMag);
				int maxMagIndex = refMFD.getClosestXIndex(sectMaxMag);
				
				IncrementalMagFreqDist subSeisMFD, supraSeisMFD;
				double supraMoRate, subMoRate, fractSupra;
				
				// actual method for determining the MFDs depends on our sub-seismogenic moment rate reduction method
				if (subSeisMoRateReduction == SubSeisMoRateReduction.FROM_INPUT_SLIP_RATES) {
					// this one just uses the input target slip rates asd the supra-seismogenic
					// anything leftover from the fault section's slip rate is given to sub-seismogenic
					SectSlipRates inputSlipRates = rupSet.requireModule(SectSlipRates.class);
					
					double supraSlipRate = inputSlipRates.getSlipRate(s); // m/yr
					double supraSlipStdDev = inputSlipRates.getSlipRateStdDev(s); // m/yr
					
					fractSupra = supraSlipRate/creepReducedSlipRate;
					Preconditions.checkState(fractSupra > 0d && fractSupra <= 1d);
					
					supraMoRate = targetMoRate*fractSupra;
					subMoRate = targetMoRate-supraMoRate;
					
					GutenbergRichterMagFreqDist supraGR = new GutenbergRichterMagFreqDist(
							refMFD.getX(minMagIndex), 1+maxMagIndex-minMagIndex, DELTA_MAG, supraMoRate, supraSeisBValue);
					
					supraSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
					for (int i=0; i<supraGR.size(); i++)
						supraSeisMFD.set(i+minMagIndex, supraGR.getY(i));
					
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
					
					GutenbergRichterMagFreqDist supraGR = new GutenbergRichterMagFreqDist(
							refMFD.getX(minMagIndex), 1+maxMagIndex-minMagIndex, DELTA_MAG, supraMoRate, supraSeisBValue);
					supraSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
					for (int i=minMagIndex; i<=maxMagIndex; i++)
						supraSeisMFD.set(i, supraGR.getY(i-minMagIndex));
					
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
					supraSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
					for (int i=minMagIndex; i<sectFullMFD.size(); i++)
						supraSeisMFD.set(i, sectFullMFD.getY(i));
					
					supraMoRate = supraSeisMFD.getTotalMomentRate();
					subMoRate = subSeisMFD.getTotalMomentRate();
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
					supraSeisMFD = new IncrementalMagFreqDist(MIN_MAG, NUM_MAG, DELTA_MAG);
					for (int i=minMagIndex; i<sectFullMFD.size(); i++)
						supraSeisMFD.set(i, sectFullMFD.getY(i));
					
					supraMoRate = supraSeisMFD.getTotalMomentRate();
					subMoRate = subSeisMFD.getTotalMomentRate();
					fractSupra = supraMoRate/targetMoRate;
					
					// scale target slip rates by the fraction that is supra-seismognic
					slipRates[s] = creepReducedSlipRate*fractSupra;
					slipRateStdDevs[s] = creepReducedSlipRateStdDev*fractSupra;
				}
				
				targetMoRates[s] = targetMoRate;
				targetSupraMoRates[s] = supraMoRate;
				
				if (sparseGR)
					// re-distribute to only bins that actually have ruptures available
					supraSeisMFD = SparseGutenbergRichterSolver.getEquivGR(supraSeisMFD, mags,
							supraSeisMFD.getTotalMomentRate(), supraSeisBValue);
				
				
				double targetMoRateTest = supraMoRate + subMoRate;
				
				Preconditions.checkState((float)targetMoRateTest == (float)targetMoRate,
						"Partitioned moment rate doesn't equal input: %s != %s", (float)targetMoRate, (float)targetMoRateTest);
				
				fractSuprasTrack.addValue(fractSupra);
				sectFractSupras[s] = fractSupra;
				
				sectSubSeisMFDs.add(subSeisMFD);
				sectSupraSeisMFDs.add(supraSeisMFD);
			}
			
			System.out.println("Fraction supra-seismogenic stats: "+fractSuprasTrack);
			
			if (subSeisMoRateReduction == SubSeisMoRateReduction.SYSTEM_AVG_IMPLIED_FROM_SUPRA_B) {
				// need to re-balance to use average supra-seis fract
				double sumSupraMo = StatUtils.sum(targetSupraMoRates);
				double sumTotMo = StatUtils.sum(targetMoRates);
				double avgSupraSeis = sumSupraMo/sumTotMo;
				
				System.out.println("Re-scaling section MFDs to match system-wide supra-seismogenic fract="+(float)avgSupraSeis);
				
				for (int s=0; s<numSects; s++) {
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
					if (slipRateStdDevs[s]  > 0d) {
						IncrementalMagFreqDist supraSeisMFD = sectSupraSeisMFDs.get(s);
						
						double relDefModStdDev = slipRateStdDevs[s]/slipRates[s];
						
						// use the slip rate standard deviation. this simple treatment is confirmed to be the exact same as if
						// we were to construct new GR distributions plus and minus one standard deviation and then calculate
						// a standard deviation from those bounds
						defModStdDevs = new EvenlyDiscretizedFunc(MIN_MAG, NUM_MAG, DELTA_MAG);
						for (int i=0; i<supraSeisMFD.size(); i++)
							defModStdDevs.set(i, supraSeisMFD.getY(i)*relDefModStdDev);
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
						
						List<Double> mags = sectMags.get(s);
						List<Integer> rups = sectRups.get(s);
						
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
				new File("/data/kevin/markdown/inversions/fm3_1_u3ref_uniform_reproduce_ucerf3.zip"));
//				new File("/data/kevin/markdown/inversions/fm3_1_u3ref_uniform_coulomb.zip"));
		
		InversionTargetMFDs origTargets = rupSet.getModule(InversionTargetMFDs.class);
		SectSlipRates origSlips = rupSet.getModule(SectSlipRates.class);
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(rupSet, new CaliforniaRegions.RELM_TESTING());
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, 1d);
		
		double b = 0.3d;
		Builder builder = new Builder(rupSet, b);
		
		builder.sparseGR(true);
//		builder.magDepDefaultRelStdDev(M->0.1);
		builder.magDepDefaultRelStdDev(M->0.1*Math.pow(10, b*0.5*(M-6)));
		builder.applyDefModelUncertainties(true);
		builder.addSectCountUncertainties(false);
		builder.totalTargetMFD(rupSet.requireModule(InversionTargetMFDs.class).getTotalRegionalMFD());
		builder.subSeisMoRateReduction(SubSeisMoRateReduction.NONE);
		
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
	}

}
