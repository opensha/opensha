package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.PaleoSlipProbabilityModel;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint.SectMappedUncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.PaleoseismicConstraintData;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

/**
 * Estimates G-R nucleation MFDs for sections that satisfy paleoseismic constraints. Sweeps over b-values from -3 to 3
 * and returns the G-R that best fits the paleo-observable event rate implied by the data constraint.
 * 
 * @author kevin
 *
 */
public abstract class PaleoSectNuclEstimator extends SectNucleationMFD_Estimator {
	
	protected FaultSystemRupSet rupSet;
	private SectMappedUncertainDataConstraint paleoConstraint;
	private boolean applyToParent;
	private int parentID = -1;

	public PaleoSectNuclEstimator(FaultSystemRupSet rupSet, SectMappedUncertainDataConstraint paleoConstraint,
			boolean applyToParent) {
		this.rupSet = rupSet;
		this.paleoConstraint = paleoConstraint;
		this.applyToParent = applyToParent;
		if (applyToParent)
			parentID = rupSet.getFaultSectionData(paleoConstraint.sectionIndex).getParentSectionId();
	}

	@Override
	public boolean appliesTo(FaultSection sect) {
		if (applyToParent)
			return sect.getParentSectionId() == parentID;
		return sect.getSectionId() == paleoConstraint.sectionIndex;
	}

	@Override
	public IncrementalMagFreqDist estimateNuclMFD(FaultSection sect, IncrementalMagFreqDist curSectSupraSeisMFD,
			List<Integer> availableRupIndexes, List<Double> availableRupMags,
			UncertainDataConstraint sectMomentRate, boolean sparseGR) {
		Preconditions.checkState(appliesTo(sect));
		Preconditions.checkState(!availableRupIndexes.isEmpty());
		
		List<Double> probObvs = new ArrayList<>();
		
		int[] rupsPerBin = new int[curSectSupraSeisMFD.size()];
		for (int r=0; r<availableRupIndexes.size(); r++) {
			int rupIndex = availableRupIndexes.get(r);
			double mag = availableRupMags.get(r);
			rupsPerBin[curSectSupraSeisMFD.getClosestXIndex(mag)]++;
			probObvs.add(getProbObservation(rupIndex, sect));
		}
		
		double sectArea = rupSet.getAreaForSection(sect.getSectionId());
		
		BoundedUncertainty moRateBounds = sectMomentRate.estimateUncertaintyBounds(UncertaintyBoundType.ONE_SIGMA);
		BoundedUncertainty paleoBounds = paleoConstraint.estimateUncertaintyBounds(UncertaintyBoundType.CONF_68);
		
		// first find the moment rate that agrees most with this constraint with the current b-value
		double totMoRate = Double.NaN;
		double closestOrigDiff = Double.POSITIVE_INFINITY;
		for (int i=0; i<3; i++) {
			IncrementalMagFreqDist testMFD;
			if (i == 0) {
				testMFD = curSectSupraSeisMFD.deepClone();
				testMFD.scaleToTotalMomentRate(moRateBounds.lowerBound);
			} else if (i == 1) {
				testMFD = curSectSupraSeisMFD;
			} else {
				testMFD = curSectSupraSeisMFD.deepClone();
				testMFD.scaleToTotalMomentRate(moRateBounds.upperBound);
			}
			double calcRate = estPaleoRate(availableRupMags, availableRupIndexes, probObvs, rupsPerBin, sectArea, testMFD);
			double diff = Math.abs(calcRate - paleoConstraint.bestEstimate);
			if (diff < closestOrigDiff) {
				closestOrigDiff = diff;
				totMoRate = testMFD.getTotalMomentRate();
			}
		}
		
		IncrementalMagFreqDist[] closest = new IncrementalMagFreqDist[3];
		double[] closestDiff = new double[3];
		for (int i=0; i<closestDiff.length; i++)
			closestDiff[i] = Double.POSITIVE_INFINITY;
		
		boolean D = false;
		if (D) System.out.println("Paleo constr fits for "+paleoConstraint);
		
		for (double b=-3d; b<=3d; b+=0.2) {
			IncrementalMagFreqDist mfd = buildGRFromBVal(curSectSupraSeisMFD, availableRupMags, b, totMoRate, sparseGR);
			
			// distribute rates to ruptures evenly within each bin
			double calcRate = estPaleoRate(availableRupMags, availableRupIndexes, probObvs, rupsPerBin, sectArea, mfd);
			
			for (int i=0; i<closest.length; i++) {
				double testVal;
				if (i == 0)
					testVal = paleoBounds.lowerBound;
				else if (i == 1)
					testVal = paleoConstraint.bestEstimate;
				else
					testVal = paleoBounds.upperBound;
				double diff = Math.abs(calcRate - testVal);
				if (diff < closestDiff[i]) {
					closestDiff[i] = diff;
					closest[i] = mfd;
				}
				if (D) System.out.println("\tbTest="+(float)b+"\tcalcRate="+(float)calcRate+"\tdiff="+(float)diff
						+"\tincrRate="+(float)mfd.getTotalIncrRate());
			}
			
		}
		return getBounded(UncertaintyBoundType.ONE_SIGMA, closest);
	}

	public double estPaleoRate(List<Double> mags, List<Integer> rups, List<Double> probObvs, int[] rupsPerBin,
			double sectArea, IncrementalMagFreqDist mfd) {
		double calcRate = 0d;
		for (int r=0; r<rups.size(); r++) {
			int bin = mfd.getClosestXIndex(mags.get(r));
			/// this is a nucleation rate
			double nuclRate = mfd.getY(bin)/(double)rupsPerBin[bin];
			// turn back into participation rate
			double particRate = nuclRate*rupSet.getAreaForRup(rups.get(r))/sectArea;
			// adjust for visibility
			calcRate += particRate*probObvs.get(r);
		}
		return calcRate;
	}
	
	protected abstract double getProbObservation(int rupIndex, FaultSection sect);
	
	public static class PaleoRateEstimator extends PaleoSectNuclEstimator {

		private PaleoProbabilityModel probModel;

		public PaleoRateEstimator(FaultSystemRupSet rupSet, SectMappedUncertainDataConstraint paleoConstraint,
				boolean applyToParent, PaleoProbabilityModel probModel) {
			super(rupSet, paleoConstraint, applyToParent);
			this.probModel = probModel;
		}

		@Override
		protected double getProbObservation(int rupIndex, FaultSection sect) {
			return probModel.getProbPaleoVisible(rupSet, rupIndex, sect.getSectionId());
		}
		
	}
	
	public static class PaleoSlipRateEstimator extends PaleoSectNuclEstimator {

		private PaleoSlipProbabilityModel probModel;
		private AveSlipModule aveSlipModule;
		private SlipAlongRuptureModel slipAlongModule;

		public PaleoSlipRateEstimator(FaultSystemRupSet rupSet, SectMappedUncertainDataConstraint paleoConstraint,
				boolean applyToParent, PaleoSlipProbabilityModel probModel) {
			super(rupSet, paleoConstraint, applyToParent);
			this.probModel = probModel;
			this.aveSlipModule = rupSet.requireModule(AveSlipModule.class);
			this.slipAlongModule = rupSet.requireModule(SlipAlongRuptureModel.class);
		}

		@Override
		protected double getProbObservation(int rupIndex, FaultSection sect) {
			int sectIndexInRup = rupSet.getSectionsIndicesForRup(rupIndex).indexOf(sect.getSectionId());
			double slipOnSect = slipAlongModule.calcSlipOnSectionsForRup(rupSet, aveSlipModule, rupIndex)[sectIndexInRup]; 
			return probModel.getProbabilityOfObservedSlip(slipOnSect);
		}
		
	}
	
	public static List<PaleoSectNuclEstimator> buildPaleoEstimates(FaultSystemRupSet rupSet, boolean applyToParent) {
		PaleoseismicConstraintData data = rupSet.requireModule(PaleoseismicConstraintData.class);
		List<PaleoSectNuclEstimator> ret = new ArrayList<>();
		
		List<? extends SectMappedUncertainDataConstraint> rateConstraints = data.getPaleoRateConstraints();
		
		if (rateConstraints != null)
			for (SectMappedUncertainDataConstraint constr : rateConstraints)
				ret.add(new PaleoRateEstimator(rupSet, constr, applyToParent, data.getPaleoProbModel()));
		
		List<? extends SectMappedUncertainDataConstraint> slipConstraints = data.getPaleoSlipConstraints();
		if (slipConstraints != null) {
			for (SectMappedUncertainDataConstraint constr : PaleoseismicConstraintData.inferRatesFromSlipConstraints(
					rupSet.requireModule(SectSlipRates.class), slipConstraints, true)) {
				ret.add(new PaleoSlipRateEstimator(rupSet, constr, applyToParent, data.getPaleoSlipProbModel()));
			}
		}
		
		return ret;
	}

}
