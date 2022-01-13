package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.List;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

public class NSHM23LogicTreeBranch extends LogicTreeBranch<LogicTreeNode> {
	
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levels;

	public static LogicTreeLevel<FaultModels> FM =
			LogicTreeLevel.forEnum(FaultModels.class, "Fault Model", "FM");
	public static LogicTreeLevel<RupturePlausibilityModels> PLAUSIBILITY =
			LogicTreeLevel.forEnum(RupturePlausibilityModels.class, "Rupture Plausibility Model", "RupSet");
	public static LogicTreeLevel<DeformationModels> DM =
			LogicTreeLevel.forEnum(DeformationModels.class, "Deformation Model", "DM");
	public static LogicTreeLevel<ScalingRelationships> SCALE =
			LogicTreeLevel.forEnum(ScalingRelationships.class, "Scaling Relationship", "Scale");
	public static LogicTreeLevel<SlipAlongRuptureModels> SLIP_ALONG =
			LogicTreeLevel.forEnum(SlipAlongRuptureModels.class, "Slip Along Rupture", "SlipAlong");
	public static LogicTreeLevel<SupraSeisBValues> SUPRA_B =
			LogicTreeLevel.forEnum(SupraSeisBValues.class, "Supra-Seis b-value", "SupraB");
	public static LogicTreeLevel<SubSectConstraintModels> SUB_SECT_CONSTR =
			LogicTreeLevel.forEnum(SubSectConstraintModels.class, "Sub-Sect Constraint Model", "SectConstr");
	public static LogicTreeLevel<SubSeisMoRateReductions> SUB_SEIS_MO =
			LogicTreeLevel.forEnum(SubSeisMoRateReductions.class, "Sub-Sect Moment Rate Reduction", "SectMoRed");
	public static LogicTreeLevel<SegmentationModels> SEG =
			LogicTreeLevel.forEnum(SegmentationModels.class, "Segmentation Model", "SegModel");
	
	static {
		// exhaustive for now, can trim down later
		levels = List.of(FM, PLAUSIBILITY, DM, SCALE, SLIP_ALONG, SUPRA_B, SUB_SECT_CONSTR, SUB_SEIS_MO, SEG);
	}
	
	@SuppressWarnings("unused") // used for deserialization
	public NSHM23LogicTreeBranch() {
		super(levels);
	}
	
	public NSHM23LogicTreeBranch(List<LogicTreeNode> values) {
		super(levels, values);
	}

}
