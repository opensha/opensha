package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.enumTreeBranches.SlipAlongRuptureModels;

/**
 * NSHM23 Logic Tree Branch implementation but using UCERF3 ingredients (FM, DM, Scaling)
 * 
 * @author kevin
 *
 */
public class NSHM23_U3_HybridLogicTreeBranch extends LogicTreeBranch<LogicTreeNode> {

	public static List<LogicTreeLevel<? extends LogicTreeNode>> levels;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsMaxDist;

	// only U3-related ones here 
	public static LogicTreeLevel<FaultModels> U3_FM =
			LogicTreeLevel.forEnum(FaultModels.class, "UCERF3 Fault Model", "FM");
	public static LogicTreeLevel<U3_UncertAddDeformationModels> U3_WRAPPED_DM =
			LogicTreeLevel.forEnum(U3_UncertAddDeformationModels.class, "UCERF3 Deformation Model", "DM");
	public static LogicTreeLevel<ScalingRelationships> SCALE =
			LogicTreeLevel.forEnum(ScalingRelationships.class, "Scaling Relationship", "Scale");
	public static LogicTreeLevel<SlipAlongRuptureModels> SLIP_ALONG =
			LogicTreeLevel.forEnum(SlipAlongRuptureModels.class, "Slip Along Rupture", "SlipAlong");
	
	static {
		// exhaustive for now, can trim down later
		levels = List.of(U3_FM, NSHM23_LogicTreeBranch.PLAUSIBILITY, U3_WRAPPED_DM, SCALE, SLIP_ALONG,
				NSHM23_LogicTreeBranch.SUPRA_B, NSHM23_LogicTreeBranch.SUB_SECT_CONSTR,
				NSHM23_LogicTreeBranch.SUB_SEIS_MO, NSHM23_LogicTreeBranch.SEG);
		levelsMaxDist = List.of(U3_FM, NSHM23_LogicTreeBranch.PLAUSIBILITY, U3_WRAPPED_DM, SCALE, SLIP_ALONG,
				NSHM23_LogicTreeBranch.SUPRA_B, NSHM23_LogicTreeBranch.SUB_SECT_CONSTR,
				NSHM23_LogicTreeBranch.SUB_SEIS_MO, NSHM23_LogicTreeBranch.MAX_DIST);
	}
	
	/**
	 * This is the default reference branch
	 */
	public static final NSHM23_U3_HybridLogicTreeBranch DEFAULT = fromValues(FaultModels.FM3_1,
			RupturePlausibilityModels.COULOMB, U3_UncertAddDeformationModels.U3_ZENG, ScalingRelationships.SHAW_2009_MOD,
			SlipAlongRuptureModels.UNIFORM, SupraSeisBValues.B_0p8, SubSectConstraintModels.TOT_NUCL_RATE,
			SubSeisMoRateReductions.SUB_B_1, SegmentationModels.SHAW_R0_3);
	
	/**
	 * Creates a NSHM23LogicTreeBranch instance from given set of node values. Null or missing values
	 * will be replaced with their default value (from NSHM23LogicTreeBranch.DEFAULT).
	 * 
	 * @param vals
	 * @return
	 */
	public static NSHM23_U3_HybridLogicTreeBranch fromValues(List<LogicTreeNode> vals) {
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
	public static NSHM23_U3_HybridLogicTreeBranch fromValues(LogicTreeNode... vals) {
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
	public static NSHM23_U3_HybridLogicTreeBranch fromValues(boolean setNullToDefault, LogicTreeNode... vals) {
		
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
		
		NSHM23_U3_HybridLogicTreeBranch branch = new NSHM23_U3_HybridLogicTreeBranch(values);
		
		if (setNullToDefault) {
			for (int i=0; i<levels.size(); i++) {
				if (branch.getValue(i) == null)
					branch.setValue(i, DEFAULT.getValue(i));
			}
		}
		
		return branch;
	}
	
	@SuppressWarnings("unused") // used for deserialization
	public NSHM23_U3_HybridLogicTreeBranch() {
		super(levels);
	}
	
	public NSHM23_U3_HybridLogicTreeBranch(List<LogicTreeNode> values) {
		super(levels, values);
	}

}
