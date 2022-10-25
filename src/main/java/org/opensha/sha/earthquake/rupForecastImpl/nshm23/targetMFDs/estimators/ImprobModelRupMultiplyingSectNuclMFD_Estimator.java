package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

/**
 * This adjusts target supra-seismogenic nucleation MFDs for an improbability model (likely a segmentation model). It
 * assumes that all ruptures contribute equally with a magnitude bin of the target MFD, differing from (and generally
 * making larger adjustments than) other models that assume the inversion will use the least penalized rupture(s) in
 * a magnitude bins. First, it distributes the original target rate for a magnitude bin evenly to each rupture. Then it
 * multiplies the rate of each rupture by the probability from the supplied improbability model. Finally it sums those
 * adjusted rates back up into a MFD which is rescaled to match the original total moment rate.
 * <p>
 * If you wish to only consider the individual probability from the worst jump within a rupture, which might be the case
 * for a segmentation model, see {@link WorstJumpProb}.
 * 
 * @author kevin
 *
 */
public class ImprobModelRupMultiplyingSectNuclMFD_Estimator extends SectNucleationMFD_Estimator {
	
	private RuptureProbabilityCalc improbModel;
	private HashSet<Integer> affectedSects;
	private double[] rupProbs;
	private int[] rupMagIndexes;
	
	/**
	 * This uses the worst probability from any single jump as the "controlling" probability for a rupture, not the
	 * product across all jumps. Might be useful if you only want to penalize individual jumps for a segmentation
	 * constraint.
	 * 
	 * @author kevin
	 *
	 */
	public static class WorstJumpProb extends ImprobModelRupMultiplyingSectNuclMFD_Estimator {
		
		private JumpProbabilityCalc jumpModel;

		public WorstJumpProb(JumpProbabilityCalc improbModel) {
			super(improbModel);
			this.jumpModel = improbModel;
		}

		@Override
		protected double calcRupProb(ClusterRupture rup) {
			double worstProb = 1d;
			for (Jump jump : rup.getJumpsIterable())
				worstProb = Math.min(worstProb, jumpModel.calcJumpProbability(rup, jump, false));
			return worstProb;
		}
		
	}

	public ImprobModelRupMultiplyingSectNuclMFD_Estimator(RuptureProbabilityCalc improbModel) {
		this.improbModel = improbModel;
	}

	@Override
	public void init(FaultSystemRupSet rupSet, List<IncrementalMagFreqDist> origSectSupraSeisMFDs,
			double[] targetSectSupraMoRates, double[] targetSectSupraSlipRates, double[] sectSupraSlipRateStdDevs,
			List<BitSet> sectRupUtilizations, int[] sectMinMagIndexes, int[] sectMaxMagIndexes,
			int[][] sectRupInBinCounts, EvenlyDiscretizedFunc refMFD) {
		super.init(rupSet, origSectSupraSeisMFDs, targetSectSupraMoRates, targetSectSupraSlipRates, sectSupraSlipRateStdDevs,
				sectRupUtilizations, sectMinMagIndexes, sectMaxMagIndexes, sectRupInBinCounts, refMFD);
		
		// figure out what sections are affected by segmentation, and precompute rupture probabilities and magnitude indexes
		affectedSects = new HashSet<>();
		rupProbs = new double[rupSet.getNumRuptures()];
		rupMagIndexes = new int[rupSet.getNumRuptures()];
		
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			ClusterRupture rup = cRups.get(r);
			rupProbs[r] = calcRupProb(rup);
			rupMagIndexes[r] = refMFD.getClosestXIndex(rupSet.getMagForRup(r));
			if (rupProbs[r] < 1)
				for (int sectIndex : rupSet.getSectionsIndicesForRup(r))
					affectedSects.add(sectIndex);
		}
	}
	
	protected double calcRupProb(ClusterRupture rup) {
		return improbModel.calcRuptureProb(rup, false);
	}

	@Override
	public boolean appliesTo(FaultSection sect) {
		return affectedSects.contains(sect.getSectionId());
	}

	@Override
	public IncrementalMagFreqDist estimateNuclMFD(FaultSection sect, IncrementalMagFreqDist curSectSupraSeisMFD,
			List<Integer> availableRupIndexes, List<Double> availableRupMags, UncertainDataConstraint sectMomentRate,
			boolean sparseGR) {
		if (sectMomentRate.bestEstimate == 00d || availableRupIndexes.isEmpty() || curSectSupraSeisMFD.calcSumOfY_Vals() == 0d)
			// this is a zero rate section, do nothing
			return curSectSupraSeisMFD;
		
		// figure out the nucleation rate of each rupture, evenly distributing the nucleation rate from the input MFD
		// across all ruptures of a given bin, then multiplying by the model improbability rate
		
		// number of ruptures in each bin
		int[] binCounts = new int[curSectSupraSeisMFD.size()];
		for (int rupIndex : availableRupIndexes)
			if ((float)rupProbs[rupIndex] > 0f)
				binCounts[rupMagIndexes[rupIndex]]++;
		
		IncrementalMagFreqDist ret = new IncrementalMagFreqDist(curSectSupraSeisMFD.getMinX(),
				curSectSupraSeisMFD.size(), curSectSupraSeisMFD.getDelta());
		for (int rupIndex : availableRupIndexes) {
			if ((float)rupProbs[rupIndex] > 0f) {
				// original nulceation share
				double rupNuclProb = curSectSupraSeisMFD.getY(rupMagIndexes[rupIndex])/(double)binCounts[rupMagIndexes[rupIndex]];
				// apply improbability model
				rupNuclProb *= rupProbs[rupIndex];
				ret.add(rupMagIndexes[rupIndex], rupNuclProb);
			}
		}
		
		// rescale to match the original moment rate
		ret.scaleToTotalMomentRate(curSectSupraSeisMFD.getTotalMomentRate());
		
		return ret;
	}

}
