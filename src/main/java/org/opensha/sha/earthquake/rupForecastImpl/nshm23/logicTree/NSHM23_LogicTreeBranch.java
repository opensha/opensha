package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

/**
 * NSHM23 Logic Tree Branch implementation and levels
 * 
 * @author kevin
 *
 */
public class NSHM23_LogicTreeBranch extends LogicTreeBranch<LogicTreeNode> {

	public static List<LogicTreeLevel<? extends LogicTreeNode>> levels;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsMaxDist;

	// TODO move most of these to a real NSHM23 logic tree branch, only keep U3-related ones here 
	public static LogicTreeLevel<NSHM23_FaultModels> U3_FM =
			LogicTreeLevel.forEnum(NSHM23_FaultModels.class, "Fault Model", "FM");
	public static LogicTreeLevel<RupturePlausibilityModels> PLAUSIBILITY =
			LogicTreeLevel.forEnum(RupturePlausibilityModels.class, "Rupture Plausibility Model", "RupSet");
	public static LogicTreeLevel<NSHM23_DeformationModels> U3_WRAPPED_DM =
			LogicTreeLevel.forEnum(NSHM23_DeformationModels.class, "Deformation Model", "DM");
	// TODO implement new
	public static LogicTreeLevel<ScalingRelationships> SCALE =
			LogicTreeLevel.forEnum(ScalingRelationships.class, "Scaling Relationship", "Scale");
	// TODO create enum for our options/weights?
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
	public static LogicTreeLevel<SegmentationMFD_Adjustment> SEG_ADJ =
			LogicTreeLevel.forEnum(SegmentationMFD_Adjustment.class, "Segmentation MFD Adjustment", "SegAdj");
	public static LogicTreeLevel<MaxJumpDistModels> MAX_DIST =
			LogicTreeLevel.forEnum(MaxJumpDistModels.class, "Maximum Jump Distance", "MaxJumpDist");
	
	static {
		// exhaustive for now, can trim down later
		levels = List.of(U3_FM, PLAUSIBILITY, U3_WRAPPED_DM, SCALE, SLIP_ALONG, SUPRA_B, SUB_SECT_CONSTR, SUB_SEIS_MO, SEG, SEG_ADJ);
		levelsMaxDist = List.of(U3_FM, PLAUSIBILITY, U3_WRAPPED_DM, SCALE, SLIP_ALONG, SUPRA_B, SUB_SECT_CONSTR, SUB_SEIS_MO, MAX_DIST);
	}
	
	/**
	 * This is the default reference branch
	 */
	public static final NSHM23_LogicTreeBranch DEFAULT = fromValues(NSHM23_FaultModels.NSHM23_v1p4,
			RupturePlausibilityModels.COULOMB, NSHM23_DeformationModels.GEOL_V1p3, ScalingRelationships.SHAW_2009_MOD,
			SlipAlongRuptureModels.UNIFORM, SupraSeisBValues.B_0p8, SubSectConstraintModels.TOT_NUCL_RATE,
			SubSeisMoRateReductions.SUB_B_1, SegmentationModels.SHAW_R0_3, SegmentationMFD_Adjustment.JUMP_PROB_THRESHOLD_AVG);
	
	/**
	 * Creates a NSHM23LogicTreeBranch instance from given set of node values. Null or missing values
	 * will be replaced with their default value (from NSHM23LogicTreeBranch.DEFAULT).
	 * 
	 * @param vals
	 * @return
	 */
	public static NSHM23_LogicTreeBranch fromValues(List<LogicTreeNode> vals) {
		LogicTreeNode[] valsArray = new LogicTreeNode[vals.size()];
		
		for (int i=0; i<vals.size(); i++)
			valsArray[i] = vals.get(i);
		
		return fromValues(valsArray);
	}
	
	/**
	 * Creates a NSHM23LogicTreeBranch instance from given set of node values. Null or missing values
	 * will be replaced with their default value (from NSHM23LogicTreeBranch.DEFAULT).
	 * 
	 * @param vals
	 * @return
	 */
	public static NSHM23_LogicTreeBranch fromValues(LogicTreeNode... vals) {
		return fromValues(true, vals);
	}
	
	/**
	 * Creates a LogicTreeBranch instance from given set of node values. Null or missing values
	 * will be replaced with their default value (from LogicTreeBranch.DEFAULT) if setNullToDefault
	 * is true.
	 * 
	 * @param setNullToDefault if true, null or missing values will be set to their default value
	 * @param vals
	 * @return
	 */
	public static NSHM23_LogicTreeBranch fromValues(boolean setNullToDefault, LogicTreeNode... vals) {
		
		// initialize branch with null
		List<LogicTreeNode> values = new ArrayList<>();
		for (int i=0; i<levels.size(); i++)
			values.add(null);
		
		// now add each value
		for (LogicTreeNode val : vals) {
			if (val == null)
				continue;
			
			int ind = -1;
			for (int i=0; i<levels.size(); i++) {
				LogicTreeLevel<?> level = levels.get(i);
				if (level.isMember(val)) {
					ind = i;
					break;
				}
			}
			Preconditions.checkArgument(ind >= 0, "Value of class '"+val.getClass()+"' does not match any known branch level");
			values.set(ind, val);
		}
		
		NSHM23_LogicTreeBranch branch = new NSHM23_LogicTreeBranch(values);
		
		if (setNullToDefault) {
			for (int i=0; i<levels.size(); i++) {
				if (branch.getValue(i) == null)
					branch.setValue(i, DEFAULT.getValue(i));
			}
		}
		
		return branch;
	}
	
	@SuppressWarnings("unused") // used for deserialization
	public NSHM23_LogicTreeBranch() {
		super(levels);
	}
	
	public NSHM23_LogicTreeBranch(List<LogicTreeNode> values) {
		super(levels, values);
	}

}
