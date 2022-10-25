package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators;

import java.util.BitSet;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

public class ScalingRelSlipRateMFD_Estimator extends SectNucleationMFD_Estimator {
	
	private boolean adjustForSlipAlong;
	private double[] calcSectSlips;
	private double[] targetSectSupraMoRates;
	private double[] targetSectSupraSlipRates;

	public ScalingRelSlipRateMFD_Estimator(boolean adjustForSlipAlong) {
		this.adjustForSlipAlong = adjustForSlipAlong;
	}

	@Override
	public void init(FaultSystemRupSet rupSet, List<IncrementalMagFreqDist> origSectSupraSeisMFDs,
			double[] targetSectSupraMoRates, double[] targetSectSupraSlipRates, double[] sectSupraSlipRateStdDevs,
			List<BitSet> sectRupUtilizations, int[] sectMinMagIndexes, int[] sectMaxMagIndexes,
			int[][] sectRupInBinCounts, EvenlyDiscretizedFunc refMFD) {
		this.targetSectSupraMoRates = targetSectSupraMoRates;
		this.targetSectSupraSlipRates = targetSectSupraSlipRates;
		super.init(rupSet, origSectSupraSeisMFDs, targetSectSupraMoRates, targetSectSupraSlipRates, sectSupraSlipRateStdDevs,
				sectRupUtilizations, sectMinMagIndexes, sectMaxMagIndexes, sectRupInBinCounts, refMFD);
		
		System.out.println("Adjusting targets to match actual rupture slips");
		AveSlipModule aveSlips = rupSet.requireModule(AveSlipModule.class);
		SlipAlongRuptureModel slipAlong = adjustForSlipAlong ? rupSet.requireModule(SlipAlongRuptureModel.class) : null;
		if (slipAlong != null && slipAlong.isUniform())
			// it's a uniform model, don't bother calculating slip along
			slipAlong = null;
		
		calcSectSlips = new double[rupSet.getNumSections()];
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
					double sectBinNuclRate = origSectSupraSeisMFDs.get(s).getY(magIndex);
					if (sectBinNuclRate > 0d) {
						double particRate = sectBinNuclRate*rupArea/rupSet.getAreaForSection(s);
						calcSectSlips[s] += slips[i]*particRate/(double)sectRupInBinCounts[s][magIndex];
					}
				}
			}
		}
	}

	@Override
	public boolean appliesTo(FaultSection sect) {
		return true;
	}

	@Override
	public IncrementalMagFreqDist estimateNuclMFD(FaultSection sect, IncrementalMagFreqDist curSectSupraSeisMFD,
			List<Integer> availableRupIndexes, List<Double> availableRupMags, UncertainDataConstraint sectMomentRate,
			boolean sparseGR) {
		Preconditions.checkState(sparseGR, "Scaling relationship adjustments only work with sparseGR=true");
		Preconditions.checkNotNull(calcSectSlips, "Not initialized");
		int s = sect.getSectionId();
		if (targetSectSupraSlipRates[s] > 0 && targetSectSupraMoRates[s] > 0) {
			double slipRatio = targetSectSupraSlipRates[s] / calcSectSlips[s];
//			slipAdjustTrack.addValue(slipRatio);
			
			curSectSupraSeisMFD = curSectSupraSeisMFD.deepClone();
			curSectSupraSeisMFD.scale(slipRatio);
		}
		return curSectSupraSeisMFD;
	}

}
