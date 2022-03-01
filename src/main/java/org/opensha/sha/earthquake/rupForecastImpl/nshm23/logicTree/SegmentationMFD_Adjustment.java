package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.DistDependentJumpProbabilityCalc;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.ImprobModelThresholdAveragingSectNuclMFD_Estimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.ImprobModelRupMultiplyingSectNuclMFD_Estimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SectNucleationMFD_Estimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SegmentationImpliedSectNuclMFD_Estimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SegmentationImpliedSectNuclMFD_Estimator.MultiBinDistributionMethod;

import com.google.common.base.Preconditions;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum SegmentationMFD_Adjustment implements LogicTreeNode {
	JUMP_PROB_THRESHOLD_AVG("Jump Prob Threshold Averaging", "JumpProb", 1d) {
		@Override
		public SectNucleationMFD_Estimator getAdjustment(JumpProbabilityCalc segModel) {
			return new ImprobModelThresholdAveragingSectNuclMFD_Estimator.WorstJumpProb(segModel);
		}
	},
	JUMP_PROB_THRESHOLD_AVG_MATCH_STRICT("Strict-Seg Equiv Fractional Jump Prob", "StrictEquivJumpProb", 1d) {
		@Override
		public SectNucleationMFD_Estimator getAdjustment(JumpProbabilityCalc segModel) {
			Preconditions.checkState(segModel instanceof DistDependentJumpProbabilityCalc,
					"Only works with dist-dependent models");
			DistDependentJumpProbabilityCalc distModel = (DistDependentJumpProbabilityCalc)segModel;
			List<Double> probs = new ArrayList<>();
			for (MaxJumpDistModels model : MaxJumpDistModels.values())
				probs.add(distModel.calcJumpProbability(model.getMaxDist()));
			return new ImprobModelThresholdAveragingSectNuclMFD_Estimator.WorstJumpProb(segModel, probs);
		}
	},
	JUMP_PROB_THRESHOLD_AVG_ABOVE_1KM("Fractional Jump Prob >1km", "JumpProbGt1km", 1d) {
		@Override
		public SectNucleationMFD_Estimator getAdjustment(JumpProbabilityCalc segModel) {
			Preconditions.checkState(segModel instanceof DistDependentJumpProbabilityCalc,
					"Only works with dist-dependent models");
			DistDependentJumpProbabilityCalc distModel = (DistDependentJumpProbabilityCalc)segModel;
			double maxProb = distModel.calcJumpProbability(1d);
			double minProb = distModel.calcJumpProbability(20d);
			EvenlyDiscretizedFunc lnProbFunc = new EvenlyDiscretizedFunc(Math.log(minProb), Math.log(maxProb), 100);
			List<Double> probs = new ArrayList<>();
			probs.add(1d);
			for (int i=lnProbFunc.size(); --i>=0;)
				probs.add(Math.exp(lnProbFunc.getX(i)));
			return new ImprobModelThresholdAveragingSectNuclMFD_Estimator.WorstJumpProb(segModel, probs);
		}
	},
	RUP_PROB_THRESHOLD_AVG("Rup Prob Threshold Averaging", "RupProb", 1d) {
		@Override
		public SectNucleationMFD_Estimator getAdjustment(JumpProbabilityCalc segModel) {
			return new ImprobModelThresholdAveragingSectNuclMFD_Estimator.WorstJumpProb(segModel);
		}
	},
	RUP_MULTIPLY_WORST_JUMP_PROB("Rup Multyplied By Worst Jump Prob", "RupMultiplyWorstJumpProb", 1d) {
		@Override
		public SectNucleationMFD_Estimator getAdjustment(JumpProbabilityCalc segModel) {
			return new ImprobModelRupMultiplyingSectNuclMFD_Estimator.WorstJumpProb(segModel);
		}
	},
	GREEDY("Greedy", "Greedy", 1d) {
		@Override
		public SectNucleationMFD_Estimator getAdjustment(JumpProbabilityCalc segModel) {
			return new SegmentationImpliedSectNuclMFD_Estimator(segModel, MultiBinDistributionMethod.GREEDY, false);
		}
	},
	GREEDY_SELF_CONTAINED("Greedy Self-Contained", "GreedySlfCont", 1d) {
		@Override
		public SectNucleationMFD_Estimator getAdjustment(JumpProbabilityCalc segModel) {
			return new SegmentationImpliedSectNuclMFD_Estimator(segModel, MultiBinDistributionMethod.GREEDY, true);
		}
	},
	CAPPED_REDIST("Capped Redistributed", "CappedRdst", 1d) {
		@Override
		public SectNucleationMFD_Estimator getAdjustment(JumpProbabilityCalc segModel) {
			return new SegmentationImpliedSectNuclMFD_Estimator(segModel, MultiBinDistributionMethod.CAPPED_DISTRIBUTED, false);
		}
	},
	CAPPED_REDIST_SELF_CONTAINED("Capped Redistributed Self-Contained", "CappedRdstSlfCont", 1d) {
		@Override
		public SectNucleationMFD_Estimator getAdjustment(JumpProbabilityCalc segModel) {
			return new SegmentationImpliedSectNuclMFD_Estimator(segModel, MultiBinDistributionMethod.CAPPED_DISTRIBUTED, true);
		}
	},
	NONE("No Adjustment", "NoAdj", 1.0d) {
		@Override
		public SectNucleationMFD_Estimator getAdjustment(JumpProbabilityCalc segModel) {
			return null;
		}
	};
	
	private String name;
	private String shortName;
	private double weight;

	private SegmentationMFD_Adjustment(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}
	
	public abstract SectNucleationMFD_Estimator getAdjustment(JumpProbabilityCalc segModel);

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return shortName.replace("R₀=", "R0_");
	}

}
