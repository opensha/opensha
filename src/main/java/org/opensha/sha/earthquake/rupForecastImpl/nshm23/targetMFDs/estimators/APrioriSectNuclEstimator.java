package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.Uncertainty;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.inversion.UCERF3InversionInputGenerator;

/**
 * Estimates nucleation MFDs that satisfy a given a priori rupture constraint, e.g., Parkfield. The given ruptures
 * are assigned their target rate, and any leftover moment is distributed to remaining ruptures following the input
 * MFD.
 * 
 * @author kevin
 *
 */
public class APrioriSectNuclEstimator extends DataSectNucleationRateEstimator {
	
	private FaultSystemRupSet rupSet;
	private HashSet<Integer> rupIndexes;
	private UncertainDataConstraint totalRate;
	
	private HashSet<Integer> sectIDs;

	public APrioriSectNuclEstimator(FaultSystemRupSet rupSet, Collection<Integer> rupIndexes, double totalRate, double rateStdDev) {
		this(rupSet, rupIndexes, new UncertainDataConstraint("APriori", totalRate, new Uncertainty(rateStdDev)));
	}

	public APrioriSectNuclEstimator(FaultSystemRupSet rupSet, Collection<Integer> rupIndexes, UncertainDataConstraint totalRate) {
		this.rupSet = rupSet;
		this.rupIndexes = new HashSet<>(rupIndexes);
		this.totalRate = totalRate;
		
		sectIDs = new HashSet<>();
		for (int rupIndex : rupIndexes)
			sectIDs.addAll(rupSet.getSectionsIndicesForRup(rupIndex));
	}

	@Override
	public boolean appliesTo(FaultSection sect) {
		return sectIDs.contains(sect.getSectionId());
	}

	@Override
	public IncrementalMagFreqDist estimateNuclMFD(FaultSection sect, IncrementalMagFreqDist curSectSupraSeisMFD,
			List<Integer> availableRupIndexes, List<Double> availableRupMags, UncertainDataConstraint sectMomentRate,
			boolean sparseGR) {
		Preconditions.checkState(appliesTo(sect));
		// assume that the nucleation rate is only from the a-priori ruptures, good enough for parkfield at least
		
		IncrementalMagFreqDist bestEst = new IncrementalMagFreqDist(curSectSupraSeisMFD.getMinX(),
				curSectSupraSeisMFD.size(), curSectSupraSeisMFD.getDelta());
		IncrementalMagFreqDist lowerEst = new IncrementalMagFreqDist(curSectSupraSeisMFD.getMinX(),
				curSectSupraSeisMFD.size(), curSectSupraSeisMFD.getDelta());
		IncrementalMagFreqDist upperEst = new IncrementalMagFreqDist(curSectSupraSeisMFD.getMinX(),
				curSectSupraSeisMFD.size(), curSectSupraSeisMFD.getDelta());

		double sectArea = rupSet.getAreaForSection(sect.getSectionId());
		
		BoundedUncertainty oneSigma = totalRate.estimateUncertaintyBounds(UncertaintyBoundType.ONE_SIGMA);
		
		double inputMoRate = curSectSupraSeisMFD.getTotalMomentRate();
		
		List<Integer> myLeftoverRups = new ArrayList<>();
		List<Double> myLeftoverMags = new ArrayList<>();
		int[] origRupsPerBin = new int[curSectSupraSeisMFD.size()];
		int[] leftoverRupsPerBin = new int[curSectSupraSeisMFD.size()];
		for (int r=0; r<availableRupIndexes.size(); r++) {
			int rupIndex = availableRupIndexes.get(r);
			double mag = availableRupMags.get(r);
			int index = curSectSupraSeisMFD.getClosestXIndex(mag);
			origRupsPerBin[index]++;
			if (!this.rupIndexes.contains(rupIndex)) {
				myLeftoverRups.add(rupIndex);
				myLeftoverMags.add(mag);
				leftoverRupsPerBin[index]++;
			}
		}
		
		for (int i=0; i<3; i++) {
			double totalRate;
			IncrementalMagFreqDist mfd;
			if (i == 0) {
				totalRate = oneSigma.lowerBound;
				mfd = lowerEst;
			} else if (i == 1) {
				totalRate = this.totalRate.bestEstimate;
				mfd = bestEst;
			} else {
				totalRate = oneSigma.upperBound;
				mfd = upperEst;
			}
			
			double rateEach = totalRate / this.rupIndexes.size();
			
			for (int rupIndex : this.rupIndexes) {
				if (rupSet.getSectionsIndicesForRup(rupIndex).contains(sect.getSectionId())) {
					double nuclRate = rateEach*sectArea/rupSet.getAreaForRup(rupIndex);
					mfd.add(mfd.getClosestXIndex(rupSet.getMagForRup(rupIndex)), nuclRate);
				}
			}
			
			double impliedMoRate = mfd.getTotalMomentRate();
			if (impliedMoRate < inputMoRate) {
				// we have extra moment leftover, fill in the rest of the MFD
				double extraMoment = inputMoRate - impliedMoRate;
				IncrementalMagFreqDist extraMFD = curSectSupraSeisMFD.deepClone();
				// zero out any bins that only belonged to our rupture
				for (int j=0; j<extraMFD.size(); j++) {
					if (leftoverRupsPerBin[j] == 0)
						extraMFD.set(j, 0d);
					else if (leftoverRupsPerBin[j] < origRupsPerBin[j])
						// assume this bin is distributed equally
						extraMFD.set(j, extraMFD.getY(j)*(double)leftoverRupsPerBin[j]/(double)origRupsPerBin[j]);
				}
				// scale to match our extra moment
				extraMFD.scaleToTotalMomentRate(extraMoment);
				for (int j=0; j<extraMFD.size(); j++)
					mfd.add(j, extraMFD.getY(j));
			}
		}
		return getBounded(UncertaintyBoundType.ONE_SIGMA, bestEst, lowerEst, upperEst);
	}
	
	public static void main(String[] args) throws IOException {
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(
				new File("/data/kevin/markdown/inversions/fm3_1_u3ref_uniform_reproduce_ucerf3.zip"));
		APrioriSectNuclEstimator estimator = new APrioriSectNuclEstimator(
				rupSet, UCERF3InversionInputGenerator.findParkfieldRups(rupSet), 1d/25d, 0.1/25d);
//		double sumNuclRate = 0d;
//		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
//			if (estimator.appliesTo(sect)) {
//				estimator.estimateNuclMFD(sect, null, null, null, null, false)
//			}
//		}
//		System.out.println("Sum nucleation rate: "+sumNuclRate);
//		System.out.println("Sum rupture rate: "+StatUtils.sum(estimator.rates));
	}
	
}