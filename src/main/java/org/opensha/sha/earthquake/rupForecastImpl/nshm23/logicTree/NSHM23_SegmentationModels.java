package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.ArrayList;
import java.util.List;

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
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.data.NSHM23_WasatchSegmentationData;

import com.google.common.base.Preconditions;

/**
 * TODO: add Wasatch models
 * 
 * @author kevin
 *
 */
@DoesNotAffect(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM23_SegmentationModels implements SegmentationModelBranchNode {
	LOW("Low Segmentation", "LowSeg", 1d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			// R0=4, shift 3km, Wasatch P=1
			return buildModel(rupSet, 4d, 3d, 1d);
		}
	},
	MID("Middle Segmentation", "MidSeg", 1d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			// R0=3, shift 2km, Wasatch P=0.5
			return buildModel(rupSet, 3d, 2d, 0.5d);
		}
	},
	HIGH("High Segmentation", "HighSeg", 1d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			// R0=2, shift 1km, Wasatch P=0
			return buildModel(rupSet, 2d, 1d, 0d);
		}
	},
	NONE("None", "None", 0.0d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			return null;
		}
	},
	AVERAGE("NSHM23 Average Segmentation", "AvgSeg", 0.0d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			List<JumpProbabilityCalc> models = new ArrayList<>();
			List<Double> weights = new ArrayList<>();
			
			double tempWeight = 0d;
			for (NSHM23_SegmentationModels segModel : values()) {
				if (segModel != this && segModel.weight > 0d) {
					models.add(segModel.getModel(rupSet, branch));
					weights.add(segModel.weight);
					tempWeight += segModel.weight;
				}
			}
			final double totWeight = tempWeight;
			return new JumpProbabilityCalc() {

				@Override
				public boolean isDirectional(boolean splayed) {
					for (JumpProbabilityCalc model : models)
						if (model.isDirectional(splayed))
							return true;
					return false;
				}

				@Override
				public String getName() {
					return "NSHM23 Average";
				}

				@Override
				public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
					double ret = 0d;
					boolean allOne = true;
					for (int i=0; i<models.size(); i++) {
						JumpProbabilityCalc model = models.get(i);
						double weight = weights.get(i);
						if (model == null) {
							ret += 1*weight;
						} else {
							double modelProb = model.calcJumpProbability(fullRupture, jump, false);
							allOne = allOne && modelProb == 1d;
							ret += modelProb*weight;
						}
					}
					if (allOne)
						// avoid any floating point issues by summing and dividing
						return 1d;
					return ret/totWeight;
				}
				
			};
		}
	};
	
	private String name;
	private String shortName;
	private double weight;

	private NSHM23_SegmentationModels(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}
	
	public abstract JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch);
	
	private static JumpProbabilityCalc buildModel(FaultSystemRupSet rupSet, double shawR0, double shawShift,
			double wasatchProb) {
		// R0=4, 3km horizontal shift
		JumpProbabilityCalc distDepend = shawShift > 0d ?
				Shaw07JumpDistProb.forHorzOffset(1d, shawR0, shawShift) : new Shaw07JumpDistProb(1d, shawR0);
		// Wasatch model
		JumpProbabilityCalc wasatch = NSHM23_WasatchSegmentationData.build(
				rupSet.getFaultSectionDataList(), wasatchProb, distDepend);
		if (wasatch != null)
			// this rupture set has Wasatch
			return wasatch;
		return distDepend;
	}

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
