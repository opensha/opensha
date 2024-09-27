package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_MaxMagOffFault;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.SupraSeisBValues;

import com.google.common.base.Preconditions;

public class PRVI25_LogicTreeBranch {

	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsOnFault;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsCrustalOffFault;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsSubduction;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsSubductionGridded;

	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsCrustalGMM;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsInterfaceGMM;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsSlabGMM;
	
	/*
	 * Core crustal FSS branch levels
	 */
	public static LogicTreeLevel<PRVI25_CrustalFaultModels> CRUSTAL_FM =
			LogicTreeLevel.forEnum(PRVI25_CrustalFaultModels.class, "Fault Model", "FM");
	public static LogicTreeLevel<PRVI25_CrustalDeformationModels> CRUSTAL_DM =
			LogicTreeLevel.forEnum(PRVI25_CrustalDeformationModels.class, "Deformation Model", "DM");
	public static LogicTreeLevel<NSHM23_ScalingRelationships> CRUSTAL_SCALE = NSHM23_LogicTreeBranch.SCALE;
	public static LogicTreeLevel<SupraSeisBValues> SUPRA_B = NSHM23_LogicTreeBranch.SUPRA_B;
	public static LogicTreeLevel<NSHM23_SegmentationModels> SEG = NSHM23_LogicTreeBranch.SEG;
	
	/*
	 * Crustal Gridded seismicity branch levels
	 */
	public static LogicTreeLevel<PRVI25_RegionalSeismicity> SEIS_RATE =
			LogicTreeLevel.forEnum(PRVI25_RegionalSeismicity.class, "Regional Seismicity Rate", "SeisRate");
	public static LogicTreeLevel<PRVI25_DeclusteringAlgorithms> SEIS_DECLUSTER =
			LogicTreeLevel.forEnum(PRVI25_DeclusteringAlgorithms.class, "Seismicity Declustering Algorithm", "SeisDecluster");
	public static LogicTreeLevel<PRVI25_SeisSmoothingAlgorithms> SEIS_SMOOTH =
			LogicTreeLevel.forEnum(PRVI25_SeisSmoothingAlgorithms.class, "Seismicity Smoothing Kernel", "SeisSmooth");
	public static LogicTreeLevel<NSHM23_MaxMagOffFault> MMAX_OFF = // use NSHM23 for now
			LogicTreeLevel.forEnum(NSHM23_MaxMagOffFault.class, "Off Fault Mmax", "MmaxOff");
	
	/*
	 * Core subduction FSS branch levels
	 */
	public static LogicTreeLevel<PRVI25_SubductionFaultModels> SUB_FM =
			LogicTreeLevel.forEnum(PRVI25_SubductionFaultModels.class, "Fault Model", "FM");
	public static LogicTreeLevel<PRVI25_SubductionDeformationModels> SUB_DM =
			LogicTreeLevel.forEnum(PRVI25_SubductionDeformationModels.class, "Deformation Model", "DM");
	public static LogicTreeLevel<PRVI25_SubductionScalingRelationships> SUB_SCALE = 
			LogicTreeLevel.forEnum(PRVI25_SubductionScalingRelationships.class, "Scaling Relationship", "Scale");
	public static LogicTreeLevel<PRVI25_SubductionBValues> SUB_SUPRA_B =
			LogicTreeLevel.forEnum(PRVI25_SubductionBValues.class, "Subduction b-value", "B");
	
	/**
	 * GMM branch levels
	 */
	public static LogicTreeLevel<PRVI25_CrustalGMMs> CRUSTAL_GMM =
			LogicTreeLevel.forEnum(PRVI25_CrustalGMMs.class, "Crustal GMM", "GMM");
	public static LogicTreeLevel<PRVI25_SubductionInterfaceGMMs> INTERFACE_GMM =
			LogicTreeLevel.forEnum(PRVI25_SubductionInterfaceGMMs.class, "Interface GMM", "GMM");
	public static LogicTreeLevel<PRVI25_SubductionSlabGMMs> SLAB_GMM =
			LogicTreeLevel.forEnum(PRVI25_SubductionSlabGMMs.class, "Slab GMM", "GMM");
	public static LogicTreeLevel<PRVI25_GMM_EpistemicModel> GMM_EPISTEMIC =
			LogicTreeLevel.forEnum(PRVI25_GMM_EpistemicModel.class, "GMM Epistemic Model", "Epistemic Model");
	public static LogicTreeLevel<PRVI25_GMM_SigmaModel> GMM_SIGMA =
			LogicTreeLevel.forEnum(PRVI25_GMM_SigmaModel.class, "GMM Sigma Model", "Sigma Model");
	
	static {
		// exhaustive for now, can trim down later
		levelsOnFault = List.of(CRUSTAL_FM, CRUSTAL_DM, CRUSTAL_SCALE, SUPRA_B, SEG);
		levelsCrustalOffFault = List.of(SEIS_RATE, SEIS_DECLUSTER, SEIS_SMOOTH, MMAX_OFF);
		levelsSubduction = List.of(SUB_FM, SUB_DM, SUB_SCALE, SUB_SUPRA_B);
		levelsSubductionGridded = List.of(SEIS_RATE, SEIS_DECLUSTER, SEIS_SMOOTH);
		levelsCrustalGMM = List.of(CRUSTAL_GMM, GMM_EPISTEMIC, GMM_SIGMA);
		levelsInterfaceGMM = List.of(INTERFACE_GMM, GMM_EPISTEMIC, GMM_SIGMA);
		levelsSlabGMM = List.of(SLAB_GMM, GMM_EPISTEMIC, GMM_SIGMA);
	}
	
	/**
	 * This is the default crustal on-fault reference branch
	 */
	public static final LogicTreeBranch<LogicTreeNode> DEFAULT_CRUSTAL_ON_FAULT = fromValues(levelsOnFault,
			PRVI25_CrustalFaultModels.PRVI_CRUSTAL_FM_V1p1,
			PRVI25_CrustalDeformationModels.GEOLOGIC,
			NSHM23_ScalingRelationships.LOGA_C4p2,
			SupraSeisBValues.B_0p5,
			NSHM23_SegmentationModels.MID);
	
	/**
	 * This is the default subduction interface reference branch
	 */
	public static final LogicTreeBranch<LogicTreeNode> DEFAULT_SUBDUCTION_INTERFACE = fromValues(levelsSubduction,
			PRVI25_SubductionFaultModels.PRVI_SUB_FM_LARGE,
			PRVI25_SubductionDeformationModels.FULL,
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

		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels, values);

		return branch;
	}

}
