package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.text.DecimalFormat;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.BinaryJumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.DistDependentJumpProbabilityCalc;

@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum MaxJumpDistModels implements LogicTreeNode {
	ONE(1d, 1d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet) {
			return new HardDistCutoffJumpProbCalc(1d);
		}
	},
	TWO(2d, 1d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet) {
			return new HardDistCutoffJumpProbCalc(2d);
		}
	},
	THREE(3d, 1d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet) {
			return new HardDistCutoffJumpProbCalc(3d);
		}
	},
	FIVE(5d, 01d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet) {
			return new HardDistCutoffJumpProbCalc(5d);
		}
	},
	EIGHT(8d, 1d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet) {
			return new HardDistCutoffJumpProbCalc(8d);
		}
	},
	TWELVE(12d, 1d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet) {
			return new HardDistCutoffJumpProbCalc(12d);
		}
	},
	FIFTEEN(15d, 1d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet) {
			return new HardDistCutoffJumpProbCalc(15d);
		}
	};
	
	private double weight;
	private double maxDist;

	private MaxJumpDistModels(double maxDist, double weight) {
		this.maxDist = maxDist;
		this.weight = weight;
	}
	
	public abstract JumpProbabilityCalc getModel(FaultSystemRupSet rupSet);

	@Override
	public String getShortName() {
		return "MaxDist"+oDF.format(maxDist)+"km";
	}

	@Override
	public String getName() {
		return "MaxDist="+oDF.format(maxDist)+"km";
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		if (fullBranch != null) {
			RupturePlausibilityModels model = fullBranch.getValue(RupturePlausibilityModels.class);
			if (maxDist > 5d && model == RupturePlausibilityModels.UCERF3)
				return 0d;
		}
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return getShortName();
	}
	
	private static final DecimalFormat oDF = new DecimalFormat("0.#");
	
	public static class HardDistCutoffJumpProbCalc implements BinaryJumpProbabilityCalc, DistDependentJumpProbabilityCalc {
		
		private double maxDist;

		public HardDistCutoffJumpProbCalc(double maxDist) {
			this.maxDist = maxDist;
			
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "MaxDist="+oDF.format(maxDist)+"km";
		}

		@Override
		public boolean isJumpAllowed(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			return (float)jump.distance <= (float)maxDist;
		}

		@Override
		public double calcJumpProbability(double distance) {
			if ((float)distance < (float)maxDist)
				return 1d;
			return 0;
		}

		@Override
		public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			return calcJumpProbability(jump.distance);
		}
		
	}

}
