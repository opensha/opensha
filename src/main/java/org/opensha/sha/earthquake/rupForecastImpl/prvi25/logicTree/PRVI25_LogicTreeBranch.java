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

import com.google.common.base.Preconditions;

public class PRVI25_LogicTreeBranch {

	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsOnFault;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsCrustalOffFault;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsCrustalCombined;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsSubduction;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsSubductionGridded;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsSubductionCombined;

	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsCrustalGMM;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsInterfaceGMM;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsSlabGMM;
	public static List<LogicTreeLevel<? extends LogicTreeNode>> levelsCombinedGMM;
	
	/*
	 * Core crustal FSS branch levels
	 */
	public static LogicTreeLevel<PRVI25_CrustalFaultModels> CRUSTAL_FM =
			LogicTreeLevel.forEnum(PRVI25_CrustalFaultModels.class, "Crustal Fault Model", "CrustalFM");
	public static LogicTreeLevel<PRVI25_CrustalDeformationModels> CRUSTAL_DM =
			LogicTreeLevel.forEnum(PRVI25_CrustalDeformationModels.class, "Crustal Deformation Model", "CrustalDM");
	public static LogicTreeLevel<NSHM23_ScalingRelationships> CRUSTAL_SCALE = NSHM23_LogicTreeBranch.SCALE;
	public static LogicTreeLevel<PRVI25_CrustalBValues> SUPRA_B = 
			LogicTreeLevel.forEnum(PRVI25_CrustalBValues.class, "Crustal Supra-Seismogenic b-value", "CrustalB");
	public static LogicTreeLevel<NSHM23_SegmentationModels> SEG = NSHM23_LogicTreeBranch.SEG;
	
	/*
	 * Crustal Gridded seismicity branch levels
	 */
	public static LogicTreeLevel<PRVI25_CrustalSeismicityRate> CRUSTAL_SEIS_RATE =
			LogicTreeLevel.forEnum(PRVI25_CrustalSeismicityRate.class, "Crustal Regional Seismicity Rate", "CrustalSeisRate");
	public static LogicTreeLevel<PRVI25_DeclusteringAlgorithms> SEIS_DECLUSTER =
			LogicTreeLevel.forEnum(PRVI25_DeclusteringAlgorithms.class, "Seismicity Declustering Algorithm", "SeisDecluster");
	public static LogicTreeLevel<PRVI25_SeisSmoothingAlgorithms> SEIS_SMOOTH =
			LogicTreeLevel.forEnum(PRVI25_SeisSmoothingAlgorithms.class, "Seismicity Smoothing Kernel", "SeisSmooth");
	public static LogicTreeLevel<NSHM23_MaxMagOffFault> MMAX_OFF = // use NSHM23 for now
			LogicTreeLevel.forEnum(NSHM23_MaxMagOffFault.class, "Crustal Off Fault Mmax", "MmaxOff");
	
	/*
	 * Core subduction FSS branch levels
	 */
	public static LogicTreeLevel<PRVI25_SubductionFaultModels> SUB_FM =
			LogicTreeLevel.forEnum(PRVI25_SubductionFaultModels.class, "Interface Fault Model", "InterfaceFM");
	public static LogicTreeLevel<PRVI25_SubductionCouplingModels> SUB_COUPLING =
			LogicTreeLevel.forEnum(PRVI25_SubductionCouplingModels.class, "Interface Coupling", "InterfaceCoupling");
	public static LogicTreeLevel<PRVI25_SubductionDeformationModels> SUB_DM =
			LogicTreeLevel.forEnum(PRVI25_SubductionDeformationModels.class, "Interface Slip Partitioning", "InterfaceSlipPartic");
	public static LogicTreeLevel<PRVI25_SubductionScalingRelationships> SUB_SCALE = 
			LogicTreeLevel.forEnum(PRVI25_SubductionScalingRelationships.class, "Interface Scaling Relationship", "InterfaceScale");
	public static LogicTreeLevel<PRVI25_SubductionBValues> SUB_SUPRA_B =
			LogicTreeLevel.forEnum(PRVI25_SubductionBValues.class, "Interface b-value", "InterfaceB");
	
	/*
	 * Subduction Regional Gridded seismicity branch levels
	 */
	public static LogicTreeLevel<PRVI25_SubductionCaribbeanSeismicityRate> CAR_SEIS_RATE =
			LogicTreeLevel.forEnum(PRVI25_SubductionCaribbeanSeismicityRate.class, "Caribbean Trench Regional Seismicity Rate", "CarSeisRate");
	public static LogicTreeLevel<PRVI25_SubductionMuertosSeismicityRate> MUE_SEIS_RATE =
			LogicTreeLevel.forEnum(PRVI25_SubductionMuertosSeismicityRate.class, "Muertos Trough Regional Seismicity Rate", "MueSeisRate");
	
	/**
	 * GMM branch levels
	 */
	public static LogicTreeLevel<PRVI25_CrustalGMMs> CRUSTAL_GMM =
			LogicTreeLevel.forEnum(PRVI25_CrustalGMMs.class, "Crustal GMM", "CrustalGMM");
	public static LogicTreeLevel<PRVI25_GMM_CrustalEpistemicModel> CRUSTAL_GMM_EPISTEMIC =
			LogicTreeLevel.forEnum(PRVI25_GMM_CrustalEpistemicModel.class, "Crustal GMM Epistemic Model", "CrustalEpi");
	public static LogicTreeLevel<PRVI25_GMM_CrustalSigmaModel> CRUSTAL_GMM_SIGMA =
			LogicTreeLevel.forEnum(PRVI25_GMM_CrustalSigmaModel.class, "Crustal GMM Sigma Model", "CrustalSigma");
	
	public static LogicTreeLevel<PRVI25_SubductionInterfaceGMMs> INTERFACE_GMM =
			LogicTreeLevel.forEnum(PRVI25_SubductionInterfaceGMMs.class, "Interface GMM", "InterfaceGMM");
	public static LogicTreeLevel<PRVI25_GMM_InterfaceEpistemicModel> INTERFACE_GMM_EPISTEMIC =
			LogicTreeLevel.forEnum(PRVI25_GMM_InterfaceEpistemicModel.class, "Interface GMM Epistemic Model", "InterfaceEpi");
	public static LogicTreeLevel<PRVI25_GMM_InterfaceSigmaModel> INTERFACE_GMM_SIGMA =
			LogicTreeLevel.forEnum(PRVI25_GMM_InterfaceSigmaModel.class, "Interface GMM Sigma Model", "InterfaceSigma");
	
	public static LogicTreeLevel<PRVI25_SubductionSlabGMMs> SLAB_GMM =
			LogicTreeLevel.forEnum(PRVI25_SubductionSlabGMMs.class, "Intraslab GMM", "IntraslabGMM");
	public static LogicTreeLevel<PRVI25_GMM_SlabEpistemicModel> SLAB_GMM_EPISTEMIC =
			LogicTreeLevel.forEnum(PRVI25_GMM_SlabEpistemicModel.class, "Intraslab GMM Epistemic Model", "IntraslabEpi");
	public static LogicTreeLevel<PRVI25_GMM_SlabSigmaModel> SLAB_GMM_SIGMA =
			LogicTreeLevel.forEnum(PRVI25_GMM_SlabSigmaModel.class, "Intraslab GMM Sigma Model", "IntraslabSigma");
	
	static {
		// exhaustive for now, can trim down later
		levelsOnFault = List.of(CRUSTAL_FM, CRUSTAL_DM, CRUSTAL_SCALE, SUPRA_B, SEG);
		levelsCrustalOffFault = List.of(CRUSTAL_SEIS_RATE, SEIS_DECLUSTER, SEIS_SMOOTH, MMAX_OFF);
		levelsCrustalCombined = new ArrayList<>();
		levelsCrustalCombined.addAll(levelsOnFault);
		levelsCrustalCombined.addAll(levelsCrustalOffFault);
		
		levelsSubduction = List.of(SUB_FM, SUB_COUPLING, SUB_DM, SUB_SCALE, SUB_SUPRA_B);
		levelsSubductionGridded = List.of(CAR_SEIS_RATE, MUE_SEIS_RATE, SEIS_DECLUSTER, SEIS_SMOOTH);
		levelsSubductionCombined = new ArrayList<>();
		levelsSubductionCombined.addAll(levelsSubduction);
		levelsSubductionCombined.addAll(levelsSubductionGridded);
		
		levelsCrustalGMM = List.of(CRUSTAL_GMM, CRUSTAL_GMM_EPISTEMIC, CRUSTAL_GMM_SIGMA);
		levelsInterfaceGMM = List.of(INTERFACE_GMM, INTERFACE_GMM_EPISTEMIC, INTERFACE_GMM_SIGMA);
		levelsSlabGMM = List.of(SLAB_GMM, SLAB_GMM_EPISTEMIC, SLAB_GMM_SIGMA);
		levelsCombinedGMM = List.of(
				CRUSTAL_GMM, CRUSTAL_GMM_EPISTEMIC, CRUSTAL_GMM_SIGMA,
				INTERFACE_GMM, INTERFACE_GMM_EPISTEMIC, INTERFACE_GMM_SIGMA,
				SLAB_GMM, SLAB_GMM_EPISTEMIC, SLAB_GMM_SIGMA);
	}
	
	/**
	 * This is the default crustal on-fault reference branch
	 */
	public static final LogicTreeBranch<LogicTreeNode> DEFAULT_CRUSTAL_ON_FAULT = fromValues(levelsOnFault,
			PRVI25_CrustalFaultModels.PRVI_CRUSTAL_FM_V1p1,
			PRVI25_CrustalDeformationModels.GEOLOGIC,
			NSHM23_ScalingRelationships.LOGA_C4p2,
			PRVI25_CrustalBValues.B_0p5,
			NSHM23_SegmentationModels.MID);
	
	/**
	 * This is the default crustal off-fault reference branch
	 */
	public static final LogicTreeBranch<LogicTreeNode> DEFAULT_CRUSTAL_GRIDDED = fromValues(levelsCrustalOffFault,
			PRVI25_CrustalSeismicityRate.PREFFERRED,
			PRVI25_DeclusteringAlgorithms.AVERAGE,
			PRVI25_SeisSmoothingAlgorithms.AVERAGE,
			NSHM23_MaxMagOffFault.MAG_7p6);
	
	/**
	 * This is the default subduction interface reference branch
	 */
	public static final LogicTreeBranch<LogicTreeNode> DEFAULT_SUBDUCTION_INTERFACE = fromValues(levelsSubduction,
			PRVI25_SubductionFaultModels.PRVI_SUB_FM_LARGE,
			PRVI25_SubductionCouplingModels.PREFERRED,
			PRVI25_SubductionDeformationModels.FULL,
			PRVI25_SubductionScalingRelationships.LOGA_C4p0,
			PRVI25_SubductionBValues.B_0p5);
	
	/**
	 * This is the default subduction gridded reference branch
	 */
	public static final LogicTreeBranch<LogicTreeNode> DEFAULT_SUBDUCTION_GRIDDED = fromValues(levelsSubductionGridded,
			PRVI25_SubductionCaribbeanSeismicityRate.PREFFERRED,
			PRVI25_SubductionMuertosSeismicityRate.PREFFERRED,
			PRVI25_DeclusteringAlgorithms.AVERAGE,
			PRVI25_SeisSmoothingAlgorithms.AVERAGE);
	
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
