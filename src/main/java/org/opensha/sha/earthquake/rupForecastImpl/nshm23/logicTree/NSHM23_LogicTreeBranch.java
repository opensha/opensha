package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

import com.google.common.base.Preconditions;

/**
 * NSHM23 Logic Tree Branch implementation and levels
 * 
 * @author kevin
 *
 */
public class NSHM23_LogicTreeBranch extends LogicTreeBranch<LogicTreeNode> {

	public static List<LogicTreeLevel<? extends LogicTreeNode>> levels;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsMaxDist;
	
	public static LogicTreeLevel<NSHM23_FaultModels> FM =
			LogicTreeLevel.forEnum(NSHM23_FaultModels.class, "Fault Model", "FM");
	public static LogicTreeLevel<RupturePlausibilityModels> PLAUSIBILITY =
			LogicTreeLevel.forEnum(RupturePlausibilityModels.class, "Rupture Plausibility Model", "RupSet");
	public static LogicTreeLevel<NSHM23_DeformationModels> DM =
			LogicTreeLevel.forEnum(NSHM23_DeformationModels.class, "Deformation Model", "DM");
	public static LogicTreeLevel<NSHM23_ScalingRelationships> SCALE =
			LogicTreeLevel.forEnum(NSHM23_ScalingRelationships.class, "Scaling Relationship", "Scale");
	public static LogicTreeLevel<NSHM23_SlipAlongRuptureModels> SLIP_ALONG =
			LogicTreeLevel.forEnum(NSHM23_SlipAlongRuptureModels.class, "Slip Along Rupture", "SlipAlong");
	public static LogicTreeLevel<SupraSeisBValues> SUPRA_B =
			LogicTreeLevel.forEnum(SupraSeisBValues.class, "Supra-Seis b-value", "SupraB");
	public static LogicTreeLevel<SubSectConstraintModels> SUB_SECT_CONSTR =
			LogicTreeLevel.forEnum(SubSectConstraintModels.class, "Sub-Sect Constraint Model", "SectConstr");
	public static LogicTreeLevel<SubSeisMoRateReductions> SUB_SEIS_MO =
			LogicTreeLevel.forEnum(SubSeisMoRateReductions.class, "Sub-Sect Moment Rate Reduction", "SectMoRed");
	public static LogicTreeLevel<NSHM23_SegmentationModels> SEG =
			LogicTreeLevel.forEnum(NSHM23_SegmentationModels.class, "Segmentation Model", "SegModel");
	public static LogicTreeLevel<SegmentationMFD_Adjustment> SEG_ADJ =
			LogicTreeLevel.forEnum(SegmentationMFD_Adjustment.class, "Segmentation MFD Adjustment", "SegAdj");
	public static LogicTreeLevel<MaxJumpDistModels> MAX_DIST =
			LogicTreeLevel.forEnum(MaxJumpDistModels.class, "Maximum Jump Distance", "MaxJumpDist");
	public static LogicTreeLevel<RupsThroughCreepingSect> RUPS_THROUGH_CREEPING =
			LogicTreeLevel.forEnum(RupsThroughCreepingSect.class, "Ruptures Through Creeping Section", "RupsThruCreep");
	
	static {
		// exhaustive for now, can trim down later
		levels = List.of(FM, PLAUSIBILITY, DM, SCALE, SLIP_ALONG, SUPRA_B,
				SUB_SECT_CONSTR, SUB_SEIS_MO, SEG, SEG_ADJ);
		levelsMaxDist = List.of(FM, PLAUSIBILITY, DM, SCALE, SLIP_ALONG, SUPRA_B,
				SUB_SECT_CONSTR, SUB_SEIS_MO, MAX_DIST, RUPS_THROUGH_CREEPING);
	}
	
	/**
	 * This is the default reference branch
	 */
	public static final NSHM23_LogicTreeBranch DEFAULT = fromValues(NSHM23_FaultModels.NSHM23_v2,
			RupturePlausibilityModels.COULOMB, NSHM23_DeformationModels.GEOLOGIC, NSHM23_ScalingRelationships.LOGA_C4p2,
			NSHM23_SlipAlongRuptureModels.UNIFORM, SupraSeisBValues.B_0p5, SubSectConstraintModels.TOT_NUCL_RATE,
			SubSeisMoRateReductions.SUB_B_1, NSHM23_SegmentationModels.MID,
			SegmentationMFD_Adjustment.REL_GR_THRESHOLD_AVG_ITERATIVE);
	
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
