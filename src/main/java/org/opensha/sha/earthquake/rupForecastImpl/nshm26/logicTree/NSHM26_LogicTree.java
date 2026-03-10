package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionBValues;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionScalingRelationships;

import com.google.common.base.Preconditions;

public class NSHM26_LogicTree {
	
	/*
	 * Core subduction FSS branch levels
	 */
	public static LogicTreeLevel<NSHM26_InterfaceFaultModels> SUB_FM =
			LogicTreeLevel.forEnum(NSHM26_InterfaceFaultModels.class, "Interface Fault Model", "InterfaceFM");
	public static LogicTreeLevel<NSHM26_InterfaceCouplingDepthModels> SUB_DEPTH_COUPLING =
			LogicTreeLevel.forEnum(NSHM26_InterfaceCouplingDepthModels.class, "Interface Coupling", "InterfaceCoupling");
	public static LogicTreeLevel<NSHM26_InterfaceDeformationModels> SUB_DM =
			LogicTreeLevel.forEnum(NSHM26_InterfaceDeformationModels.class, "Interface Slip Partitioning", "InterfaceSlipPartic");
	public static LogicTreeLevel<PRVI25_SubductionScalingRelationships> SUB_SCALE = 
			LogicTreeLevel.forEnum(PRVI25_SubductionScalingRelationships.class, "Interface Scaling Relationship", "InterfaceScale");
	public static LogicTreeLevel<PRVI25_SubductionBValues> SUB_SUPRA_B =
			LogicTreeLevel.forEnum(PRVI25_SubductionBValues.class, "Interface b-value", "InterfaceB");
	
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsSubduction;
	
	static {
		levelsSubduction = List.of(SUB_FM, SUB_DEPTH_COUPLING, SUB_DM, SUB_SCALE, SUB_SUPRA_B);
	}
	
	/**
	 * This is the default GNMI subduction interface reference branch
	 */
	public static final LogicTreeBranch<LogicTreeNode> DEFAULT_GNMI_SUBDUCTION_INTERFACE = fromValues(levelsSubduction,
			NSHM26_InterfaceFaultModels.GNMI_V1,
			NSHM26_InterfaceCouplingDepthModels.DOUBLE_TAPER,
			NSHM26_InterfaceDeformationModels.PREF_COUPLING,
			PRVI25_SubductionScalingRelationships.LOGA_C4p0,
			PRVI25_SubductionBValues.B_0p5);
	
	/**
	 * This is the default AmSam subduction interface reference branch
	 */
	public static final LogicTreeBranch<LogicTreeNode> DEFAULT_AMSAM_SUBDUCTION_INTERFACE = fromValues(levelsSubduction,
			NSHM26_InterfaceFaultModels.AMSAM_V1,
			NSHM26_InterfaceCouplingDepthModels.DOUBLE_TAPER,
			NSHM26_InterfaceDeformationModels.PREF_COUPLING,
			PRVI25_SubductionScalingRelationships.LOGA_C4p0,
			PRVI25_SubductionBValues.B_0p5);
	
	public static LogicTreeBranch<LogicTreeNode> fromValues(List<LogicTreeLevel<? extends LogicTreeNode>> levels, LogicTreeNode... vals) {
		Preconditions.checkState(levels.size() == vals.length);
		
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

		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels, values);

		return branch;
	}

}
