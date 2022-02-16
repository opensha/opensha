package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.ImprobabilityImpliedSectNuclMFD_Estimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SectNucleationMFD_Estimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SegmentationImpliedSectNuclMFD_Estimator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators.SegmentationImpliedSectNuclMFD_Estimator.MultiBinDistributionMethod;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum SegmentationMFD_Adjustment implements LogicTreeNode {
	FRACT_JUMP_PROB("Fractional Jump Prob", "JumpProb", 1d) {
		@Override
		public SectNucleationMFD_Estimator getAdjustment(JumpProbabilityCalc segModel) {
			return new ImprobabilityImpliedSectNuclMFD_Estimator.WorstJumpProb(segModel);
		}
	},
	FRACT_RUP_PROB("Fractional Rup Prob", "RupProb", 1d) {
		@Override
		public SectNucleationMFD_Estimator getAdjustment(JumpProbabilityCalc segModel) {
			return new ImprobabilityImpliedSectNuclMFD_Estimator.WorstJumpProb(segModel);
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
	NONE("None", "None", 0.0d) {
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
