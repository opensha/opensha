package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc.BinaryJumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc.BinaryRuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_ConstraintBuilder;
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
public enum NSHM23_SegmentationModels implements SegmentationModelBranchNode, RupsThroughCreepingSectBranchNode {
	NONE("None", "None", 1.0d) {
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			// no segmentation:
			//	* no distance dependent
			//	* anything through creeping
			// 	* anything through wasatch
			return null;
		}

		@Override
		public boolean isExcludeRupturesThroughCreepingSect() {
			return false;
		}
	},
	LOW("Low Segmentation", "LowSeg",
			1d, // weight
			4d, // R0
			3d) { // shift
		@Override
		public JumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			// if we don't have a creeping section branch explicitly on the logic tree, include it here
			double creepingProb = 1d;
			if (APPLY_TO_CREEPING_SECT && !branch.hasValue(RupsThroughCreepingSect.class))
				// Creeping P=0.75
				creepingProb = 0.75;
			// Wasatch P=0.75
			double wasatchProb = 0.75;
			return buildModel(rupSet, shawR0, shawShift, wasatchProb, creepingProb, Double.NaN);
		}

		@Override
		public boolean isExcludeRupturesThroughCreepingSect() {
			return false;
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

		@Override
		public boolean isExcludeRupturesThroughCreepingSect() {
			return false;
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
				// Creeping P=0.25
				creepingProb = 0.25;
			// Wasatch P=0.25
			double wasatchProb = 0.25;
			return buildModel(rupSet, shawR0, shawShift, wasatchProb, creepingProb, Double.NaN);
		}

		@Override
		public boolean isExcludeRupturesThroughCreepingSect() {
			// highly limited above, will be excluded in the classic branch
			// TODO should be limit it here as well?
			return false;
		}
	},
	CLASSIC("Classic ('A' faults)", "Classic", 1d) {
		@Override
		public BinaryJumpProbabilityCalc getModel(FaultSystemRupSet rupSet, LogicTreeBranch<?> branch) {
			// jumps within named faults only
			List<BinaryJumpProbabilityCalc> models = new ArrayList<>();
			if (rupSet.hasModule(NamedFaults.class))
				models.add(new NamedFaultSegmentationModel(rupSet.requireModule(NamedFaults.class)));
			else
				models.add(new JumpProbabilityCalc.NoJumps());
			
			// creeping section. this limits not just through, but anything corupturing with the creeping section
			int creepingParentID = NSHM23_ConstraintBuilder.findCreepingSection(rupSet);
			if (creepingParentID >= 0)
				models.add(new CreepingSectionBinaryJumpExcludeModel(creepingParentID));
			
			// wasatch
			BinaryJumpProbabilityCalc wasatch = NSHM23_WasatchSegmentationData.buildFullExclusion(rupSet.getFaultSectionDataList(), null);
			if (wasatch != null)
				models.add(wasatch);
			
			Preconditions.checkState(!models.isEmpty());
			if (models.size() == 1)
				return models.get(0);
			return new JumpProbabilityCalc.LogicalAnd(models.toArray(new BinaryJumpProbabilityCalc[0]));
		}

		@Override
		public boolean isExcludeRupturesThroughCreepingSect() {
			return true;
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

		@Override
		public boolean isExcludeRupturesThroughCreepingSect() {
			double weightInclude = 0d;
			double weightExclude = 0d;
			for (NSHM23_SegmentationModels segModel : values()) {
				if (segModel != this && segModel.weight > 0d) {
					if (segModel.isExcludeRupturesThroughCreepingSect())
						weightExclude += segModel.weight;
					else
						weightInclude += segModel.weight;
				}
			}
			return weightExclude > weightInclude;
		}
	};
	
	public static boolean APPLY_TO_CREEPING_SECT = true;
	
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
				int creepingParentID = NSHM23_ConstraintBuilder.findCreepingSection(rupSet);
				if (creepingParentID >= 0) {
					CreepingSectionJumpSegModel creepModel = new CreepingSectionJumpSegModel(creepingParentID, creepingProb);
					if (model == null)
						model = creepModel;
					else
						model = new JumpProbabilityCalc.Minimum(model, creepModel);
				}
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
	
	public static class CreepingSectionBinaryJumpExcludeModel implements BinaryJumpProbabilityCalc {
		
		private int creepingParentID;

		public CreepingSectionBinaryJumpExcludeModel(int creepingParentID) {
			this.creepingParentID = creepingParentID;
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "Exclude Creeping Section Jumps";
		}

		@Override
		public boolean isJumpAllowed(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			if (jump.fromCluster.parentSectionID == creepingParentID || jump.toCluster.parentSectionID == creepingParentID)
				return false;
			return true;
		}
		
	}
	
	public static class NamedFaultSegmentationModel implements BinaryJumpProbabilityCalc {
		
		private NamedFaults namedFaults;

		public NamedFaultSegmentationModel(NamedFaults namedFaults) {
			this.namedFaults = namedFaults;
		}

		@Override
		public boolean isDirectional(boolean splayed) {
			return false;
		}

		@Override
		public String getName() {
			return "Named Fault Segmentation";
		}

		@Override
		public boolean isJumpAllowed(ClusterRupture fullRupture, Jump jump, boolean verbose) {
			String faultName1 = namedFaults.getFaultName(jump.fromCluster.parentSectionID);
			String faultName2 = namedFaults.getFaultName(jump.toCluster.parentSectionID);
			if (faultName1 == null || faultName2 == null)
				// at least one of these sections is not part of a named fault
				return false;
			// both sections are part of a named fault, lets see if they're the same one
			return faultName1.equals(faultName2);
		}
		
	}
	
	public static class ExcludeRupsThroughCreepingSegmentationModel implements BinaryRuptureProbabilityCalc {
		
		private int creepingParentID;

		public ExcludeRupsThroughCreepingSegmentationModel(int creepingParentID) {
			this.creepingParentID = creepingParentID;
		}

		@Override
		public String getName() {
			return "Exclude Ruptures Through Creeping";
		}
		
		@Override
		public boolean isDirectional(boolean splayed) {
			return splayed;
		}
		
		@Override
		public boolean isRupAllowed(ClusterRupture fullRupture, boolean verbose) {
			return !NSHM23_ConstraintBuilder.isRupThroughCreeping(creepingParentID, fullRupture);
		}
		
	}

}
