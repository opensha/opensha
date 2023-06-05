package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.AveSlipModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SlipAlongRuptureModel;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.Builder;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

public class ScalingRelSlipRateMFD_Estimator extends SectNucleationMFD_Estimator {
	
	private boolean adjustForSlipAlong;
	private double[] calcSectSlips;
	private double[] targetSectSupraMoRates;
	private double[] targetSectSupraSlipRates;
	
	private MinMaxAveTracker slipAdjustTrack = new MinMaxAveTracker();
	private MinMaxAveTracker absPercentTrack = new MinMaxAveTracker();

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
			slipAdjustTrack.addValue(slipRatio);
			absPercentTrack.addValue(100d*Math.abs((targetSectSupraSlipRates[s]-calcSectSlips[s])/calcSectSlips[s]));
			
			curSectSupraSeisMFD = curSectSupraSeisMFD.deepClone();
			curSectSupraSeisMFD.scale(slipRatio);
		}
		return curSectSupraSeisMFD;
	}
	
	public void printStats() {
		System.out.println("Scaling relationship MFD adjustment ratios: "+slipAdjustTrack);
		System.out.println("Scaling relationship MFD adjustment abs % changes: "+absPercentTrack);
	}
	
	public static void main(String[] args) throws IOException {
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2023_04_11-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR/"
				+ "results_NSHM23_v2_CoulombRupSet_branch_averaged_gridded.zip"));
		
		List<NSHM23_ScalingRelationships> scales = new ArrayList<>();
		List<ScalingRelSlipRateMFD_Estimator> estimators = new ArrayList<>();
		
		double avgFromMoment = 0d;
		int numFromMoment = 0;
		double avgOther = 0d;
		int numOther = 0;
		for (NSHM23_ScalingRelationships scale : NSHM23_ScalingRelationships.values()) {
			if (scale.getNodeWeight(null) > 0d) {
				scales.add(scale);
				ScalingRelSlipRateMFD_Estimator estimator = new ScalingRelSlipRateMFD_Estimator(false);
				
				rupSet = FaultSystemRupSet.buildFromExisting(rupSet, false).forScalingRelationship(scale).build();
				Builder mfdBuilder = new SupraSeisBValInversionTargetMFDs.Builder(rupSet, 0.5d);
				mfdBuilder.adjustTargetsForData(estimator);
				mfdBuilder.build();
				
				estimators.add(estimator);
				
				if (scale.getName().toLowerCase().contains("from moment")) {
					avgFromMoment += estimator.absPercentTrack.getAverage();
					numFromMoment++;
				} else {
					avgOther += estimator.absPercentTrack.getAverage();
					numOther++;
				}
			}
		}
		
		for (int i=0; i<scales.size(); i++) {
			System.out.println(scales.get(i).getName());
			estimators.get(i).printStats();
			System.out.println();
		}
		
		avgFromMoment /= numFromMoment;
		avgOther /= numOther;
		System.out.println("From-Moment abs % change: "+(float)avgFromMoment+" %");
		System.out.println("Other abs % change: "+(float)avgOther+" %");
	}

}
