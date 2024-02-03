package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * NSHM23 Logic Tree Branch implementation and levels
 * 
 * @author kevin
 *
 */
public class NSHM23_LogicTreeBranch extends LogicTreeBranch<LogicTreeNode> {

	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsOnFault;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsOffFault;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsCombined;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsMaxDist;
	
	/*
	 * Core FSS branch levels
	 */
	public static LogicTreeLevel<NSHM23_FaultModels> FM =
			LogicTreeLevel.forEnum(NSHM23_FaultModels.class, "Fault Model", "FM");
	public static LogicTreeLevel<NSHM23_DeformationModels> DM =
			LogicTreeLevel.forEnum(NSHM23_DeformationModels.class, "Deformation Model", "DM");
	public static LogicTreeLevel<NSHM23_ScalingRelationships> SCALE =
			LogicTreeLevel.forEnum(NSHM23_ScalingRelationships.class, "Scaling Relationship", "Scale");
	public static LogicTreeLevel<SupraSeisBValues> SUPRA_B =
			LogicTreeLevel.forEnum(SupraSeisBValues.class, "Supra-Seismogenic b-value", "SupraB");
	public static LogicTreeLevel<NSHM23_SegmentationModels> SEG =
			LogicTreeLevel.forEnum(NSHM23_SegmentationModels.class, "Segmentation Model", "SegModel");
	public static LogicTreeLevel<NSHM23_PaleoUncertainties> PALEO_UNCERT =
			LogicTreeLevel.forEnum(NSHM23_PaleoUncertainties.class, "Paleoseismic Data Uncertainties", "PaleoUncert");
	
	/*
	 * Gridded seismicity branch levels
	 */
	public static LogicTreeLevel<NSHM23_RegionalSeismicity> SEIS_RATE =
			LogicTreeLevel.forEnum(NSHM23_RegionalSeismicity.class, "Regional Seismicity Rate", "SeisRate");
	public static LogicTreeLevel<NSHM23_DeclusteringAlgorithms> SEIS_DECLUSTER =
			LogicTreeLevel.forEnum(NSHM23_DeclusteringAlgorithms.class, "Seismicity Declustering Algorithm", "SeisDecluster");
	public static LogicTreeLevel<NSHM23_SeisSmoothingAlgorithms> SEIS_SMOOTH =
			LogicTreeLevel.forEnum(NSHM23_SeisSmoothingAlgorithms.class, "Seismicity Smoothing Kernel", "SeisSmooth");
	public static LogicTreeLevel<NSHM23_MaxMagOffFault> MMAX_OFF =
			LogicTreeLevel.forEnum(NSHM23_MaxMagOffFault.class, "Off Fault Mmax", "MmaxOff");
	
	/*
	 * Optional levels
	 */
	public static LogicTreeLevel<RupturePlausibilityModels> PLAUSIBILITY =
			LogicTreeLevel.forEnum(RupturePlausibilityModels.class, "Rupture Plausibility Model", "RupSet");
	public static LogicTreeLevel<NSHM23_SlipAlongRuptureModels> SLIP_ALONG =
			LogicTreeLevel.forEnum(NSHM23_SlipAlongRuptureModels.class, "Slip Along Rupture", "SlipAlong");
	public static LogicTreeLevel<SubSectConstraintModels> SUB_SECT_CONSTR =
			LogicTreeLevel.forEnum(SubSectConstraintModels.class, "Sub-Sect Constraint Model", "SectConstr");
	public static LogicTreeLevel<SubSeisMoRateReductions> SUB_SEIS_MO =
			LogicTreeLevel.forEnum(SubSeisMoRateReductions.class, "Sub-Sect Moment Rate Reduction", "SectMoRed");
	public static LogicTreeLevel<SegmentationMFD_Adjustment> SEG_ADJ =
			LogicTreeLevel.forEnum(SegmentationMFD_Adjustment.class, "Segmentation MFD Adjustment", "SegAdj");
	public static LogicTreeLevel<MaxJumpDistModels> MAX_DIST =
			LogicTreeLevel.forEnum(MaxJumpDistModels.class, "Maximum Jump Distance", "MaxJumpDist");
	public static LogicTreeLevel<RupsThroughCreepingSect> RUPS_THROUGH_CREEPING =
			LogicTreeLevel.forEnum(RupsThroughCreepingSect.class, "Ruptures Through Creeping Section", "RupsThruCreep");
	public static LogicTreeLevel<NSHM23_SingleStates> SINGLE_STATES =
			LogicTreeLevel.forEnum(NSHM23_SingleStates.class, "Single State Inversion", "SingleState");
	
	static {
		// exhaustive for now, can trim down later
		levelsOnFault = List.of(FM, DM, SCALE, SUPRA_B, PALEO_UNCERT, SEG);
		levelsOffFault = List.of(SEIS_RATE, SEIS_DECLUSTER, SEIS_SMOOTH, MMAX_OFF);
		
		ImmutableList.Builder<LogicTreeLevel<?>> combLevelBuilder = ImmutableList.builder();
		combLevelBuilder.addAll(levelsOnFault);
		combLevelBuilder.addAll(levelsOffFault);
		levelsCombined = combLevelBuilder.build();
		
		levelsMaxDist = List.of(FM, PLAUSIBILITY, DM, SCALE, SLIP_ALONG, SUPRA_B,
				SUB_SECT_CONSTR, SUB_SEIS_MO, PALEO_UNCERT, MAX_DIST, RUPS_THROUGH_CREEPING);
	}
	
	/**
	 * This is the default on-fault reference branch
	 */
	public static final NSHM23_LogicTreeBranch DEFAULT_ON_FAULT = fromValues(levelsOnFault,
			NSHM23_FaultModels.NSHM23_v3,
			NSHM23_DeformationModels.GEOLOGIC,
			NSHM23_ScalingRelationships.LOGA_C4p2,
			SupraSeisBValues.B_0p5,
			NSHM23_PaleoUncertainties.EVEN_FIT,
			NSHM23_SegmentationModels.MID);
	/**
	 * This is the average on-fault reference branch
	 */
	public static final NSHM23_LogicTreeBranch AVERAGE_ON_FAULT = fromValues(levelsOnFault,
			NSHM23_FaultModels.NSHM23_v3,
			NSHM23_DeformationModels.AVERAGE,
			NSHM23_ScalingRelationships.AVERAGE,
			SupraSeisBValues.AVERAGE,
			NSHM23_PaleoUncertainties.AVERAGE,
			NSHM23_SegmentationModels.AVERAGE);

	/**
	 * This is the default off-fault reference branch
	 */
	public static final NSHM23_LogicTreeBranch DEFAULT_OFF_FAULT = fromValues(levelsOffFault,
			NSHM23_RegionalSeismicity.PREFFERRED,
			NSHM23_DeclusteringAlgorithms.GK,
			NSHM23_SeisSmoothingAlgorithms.ADAPTIVE,
			NSHM23_MaxMagOffFault.MAG_7p6);
	/**
	 * This is the default off-fault reference branch
	 */
	public static final NSHM23_LogicTreeBranch AVERAGE_OFF_FAULT = fromValues(levelsOffFault,
			NSHM23_RegionalSeismicity.PREFFERRED,
			NSHM23_DeclusteringAlgorithms.AVERAGE,
			NSHM23_SeisSmoothingAlgorithms.AVERAGE,
			NSHM23_MaxMagOffFault.MAG_7p6);

	public static final NSHM23_LogicTreeBranch DEFAULT_COMBINED;
	public static final NSHM23_LogicTreeBranch AVERAGE_COMBINED;
	
	static {
		DEFAULT_COMBINED = new NSHM23_LogicTreeBranch(levelsCombined, null);
		for (LogicTreeNode node : DEFAULT_ON_FAULT)
			DEFAULT_COMBINED.setValue(node);
		for (LogicTreeNode node : DEFAULT_OFF_FAULT)
			DEFAULT_COMBINED.setValue(node);
		AVERAGE_COMBINED = new NSHM23_LogicTreeBranch(levelsCombined, null);
		for (LogicTreeNode node : AVERAGE_ON_FAULT)
			AVERAGE_COMBINED.setValue(node);
		for (LogicTreeNode node : AVERAGE_OFF_FAULT)
			AVERAGE_COMBINED.setValue(node);
	}
	
	/**
	 * Creates a NSHM23LogicTreeBranch instance from given set of node values. Null or missing values
	 * will be replaced with their default value (from NSHM23LogicTreeBranch.DEFAULT).
	 * 
	 * @param vals
	 * @return
	 */
	public static NSHM23_LogicTreeBranch fromValues(List<LogicTreeLevel<?>> levels, List<LogicTreeNode> vals) {
		LogicTreeNode[] valsArray = new LogicTreeNode[vals.size()];
		
		for (int i=0; i<vals.size(); i++)
			valsArray[i] = vals.get(i);
		
		return fromValues(levels, valsArray);
	}
	
	/**
	 * Creates a NSHM23LogicTreeBranch instance from given set of node values. Null or missing values
	 * will be replaced with their default value (from NSHM23LogicTreeBranch.DEFAULT).
	 * 
	 * @param vals
	 * @return
	 */
	public static NSHM23_LogicTreeBranch fromValues(List<LogicTreeLevel<?>> levels, LogicTreeNode... vals) {
		return fromValues(levels, null, vals);
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
	public static NSHM23_LogicTreeBranch fromValues(List<LogicTreeLevel<?>> levels, LogicTreeBranch<?> defaultValues, LogicTreeNode... vals) {
		
		// initialize branch with null
		List<LogicTreeNode> values = new ArrayList<>();
		for (int i=0; i<levels.size(); i++)
			values.add(null);
		
		// now add each value
		for (LogicTreeNode val : vals) {
			if (val == null)
				continue;
			
			int ind = -1;
			for (int i=0; i<levelsOnFault.size(); i++) {
				LogicTreeLevel<?> level = levels.get(i);
				if (level.isMember(val)) {
					ind = i;
					break;
				}
			}
			Preconditions.checkArgument(ind >= 0, "Value of class '"+val.getClass()+"' does not match any known branch level");
			values.set(ind, val);
		}
		
		NSHM23_LogicTreeBranch branch = new NSHM23_LogicTreeBranch(levels, values);
		
		if (defaultValues != null) {
			Preconditions.checkState(defaultValues.size() == branch.size());
			for (int i=0; i<levels.size(); i++) {
				if (branch.getValue(i) == null)
					branch.setValue(i, defaultValues.getValue(i));
			}
		}
		
		return branch;
	}
	
	@SuppressWarnings("unused") // used for deserialization
	public NSHM23_LogicTreeBranch() {
		super(levelsOnFault);
	}
	
	public NSHM23_LogicTreeBranch(List<LogicTreeNode> values) {
		super(levelsOnFault, values);
	}
	
	public NSHM23_LogicTreeBranch(List<LogicTreeLevel<?>> levels, List<LogicTreeNode> values) {
		super(levels, values);
	}

}
