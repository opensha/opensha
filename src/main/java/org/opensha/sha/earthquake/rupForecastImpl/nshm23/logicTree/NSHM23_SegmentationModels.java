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
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
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
	LOW("Low Segmentation", "LowSeg",
			1d, // weight
			4d, // R0
			3d) { // shift
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			// if we don't have a creeping section branch explicitly on the logic tree, include it here
			double creepingProb = 1d;
			if (APPLY_TO_CREEPING_SECT && !branch.hasValue(RupsThroughCreepingSect.class))
				// Creeping P=1 unless wide branches, then 0.75
				creepingProb = WIDE_BRANCHES ? 0.75 : 1d;
			// Wasatch P=1 unless wide branches, then 0.75
			double wasatchProb = WIDE_BRANCHES ? 0.75 : 1d;
			return buildModel(rupSet, shawR0, shawShift, wasatchProb, creepingProb, Double.NaN);
		}
	},
	MID("Middle Segmentation", "MidSeg",
			1d, // weight
			3d, // R0
			2d) { // shift
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			// if we don't have a creeping section branch explicitly on the logic tree, include it here
			// 50% max passthrough rate for any single jump (not all of those ruptures actually go through)
			double creepingProb = 1d;
			if (APPLY_TO_CREEPING_SECT && !branch.hasValue(RupsThroughCreepingSect.class))
				creepingProb = 0.5d;
			// Wasatch P=0.5
			double wasatchProb = 0.5;
			return buildModel(rupSet, shawR0, shawShift, wasatchProb, creepingProb, Double.NaN);
		}
	},
	HIGH("High Segmentation", "HighSeg",
			1d, // weight
			2d, // R0
			1d) { // shift
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			// Still restrict creeping section, but the bulk of the through-creeping-section will be handled
			// externally at the rupture exclusion model stage
			double creepingProb = 1d;
			if (APPLY_TO_CREEPING_SECT && !branch.hasValue(RupsThroughCreepingSect.class))
				// Creeping P=0.5 unless wide branches, then 0.25
				creepingProb = WIDE_BRANCHES ? 0.25 : 0.5d;
			// Wasatch P=0 unless wide branches, then 0.25
			double wasatchProb = WIDE_BRANCHES ? 0.25 : 0d;
			return buildModel(rupSet, shawR0, shawShift, wasatchProb, creepingProb, Double.NaN);
		}
	},
	NONE("None", "None", 1.0d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			return null;
		}
	},
	TWO_KM("2km Hard Cutoff", "Max2km", 1.0d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			// Still restrict creeping section, but the bulk of the through-creeping-section will be handled
			// externally at the rupture exclusion model stage
			double creepingProb = 1d;
			if (APPLY_TO_CREEPING_SECT && !branch.hasValue(RupsThroughCreepingSect.class))
				creepingProb = 0.25;
			// Wasatch P=0
			double wasatchProb = 0d;
			return buildModel(rupSet, Double.NaN, Double.NaN, wasatchProb, creepingProb, 2d);
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
	
	public static boolean APPLY_TO_CREEPING_SECT = true;
	
	public static boolean WIDE_BRANCHES = false;
	
	private String name;
	private String shortName;
	private double weight;

	protected double shawR0;
	protected double shawShift;

	private NSHM23_SegmentationModels(String name, String shortName, double weight) {
		this(name, shortName, weight, Double.NaN, Double.NaN);
	}

	private NSHM23_SegmentationModels(String name, String shortName, double weight, double shawR0, double shawShift) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
		this.shawR0 = shawR0;
		this.shawShift = shawShift;
	}
	
	public abstract JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch);
	
	private static JumpProbabilityCalc buildModel(FaultSystemRupSet rupSet, double shawR0, double shawShift,
			double wasatchProb, double creepingProb, double hardCutoff) {
		// distance dependent model, possibly with a horizontal shift
		JumpProbabilityCalc model = null;
		if (Double.isFinite(hardCutoff))
			model = new HardDistCutoffJumpProbCalc(hardCutoff);
		if (Double.isFinite(shawR0)) {
			JumpProbabilityCalc shawModel = shawShift > 0d ?
					Shaw07JumpDistProb.forHorzOffset(1d, shawR0, shawShift) : new Shaw07JumpDistProb(1d, shawR0);
			if (model == null)
				model = shawModel;
			else
				model = new JumpProbabilityCalc.Minimum(model, shawModel);
		}
		if (rupSet != null) {
			if (creepingProb < 1d) {
				int creepingParentID = FaultSectionUtils.findParentSectionID(rupSet.getFaultSectionDataList(), "San", "Andreas", "Creeping");
				if (creepingParentID >= 0)
					model = new JumpProbabilityCalc.Minimum(model, new CreepingSectionJumpSegModel(creepingParentID, creepingProb));
			}
			// Wasatch model
			JumpProbabilityCalc wasatch = NSHM23_WasatchSegmentationData.build(
					rupSet.getFaultSectionDataList(), wasatchProb, model);
			if (wasatch != null)
				// this rupture set has Wasatch
				model = wasatch;
		}
		return model;
	}
	
	/**
	 * @return r0 for distance-dependent segmentation, or NaN if not applicable
	 */
	public double getShawR0() {
		return shawR0;
	}
	
	/**
	 * @return horizontal distance shift for distance-dependent segmentation, or NaN if not applicable
	 */
	public double getShawShift() {
		return shawShift;
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
	
	public static class CreepingSectionJumpSegModel implements JumpProbabilityCalc {
		
		private int creepingParentID;
		private double prob;

		public CreepingSectionJumpSegModel(int creepingParentID, double prob) {
			this.creepingParentID = creepingParentID;
			this.prob = prob;
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "Creeping Section Jump P≤"+(float)prob;
		}

		@Override
		public double calcJumpProbability(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			if (jump.fromCluster.parentSectionID == creepingParentID || jump.toCluster.parentSectionID == creepingParentID)
				return prob;
			return 1;
		}
		
	}

}
