package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.statistics.distribution.CorrTruncatedNormalDistribution;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionBValues;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionScalingRelationships;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

public class NSHM26_LogicTree {
	
	/*
	 * Subduction interface branch levels (FSS and gridded)
	 * missing/TODO:
	 * 
	 * * sampled b values?
	 * * option to use observed seismicity b-value?
	 */
	public static LogicTreeLevel<NSHM26_InterfaceFaultModels> INTERFACE_FM =
			LogicTreeLevel.forEnum(NSHM26_InterfaceFaultModels.class, "Interface Fault Model", "InterfaceFM");
	public static LogicTreeLevel<NSHM26_InterfaceCouplingDepthModels> INTERFACE_DEPTH_COUPLING =
			LogicTreeLevel.forEnum(NSHM26_InterfaceCouplingDepthModels.class, "Interface Depth Coupling", "InterfaceDepthCoupling");
	public static LogicTreeLevel<NSHM26_InterfaceDeformationModels> INTERFACE_DM =
			LogicTreeLevel.forEnum(NSHM26_InterfaceDeformationModels.class, "Interface Deformation Model", "InterfaceDM");
	public static LogicTreeLevel<PRVI25_SubductionScalingRelationships> INTERFACE_SCALE = 
			LogicTreeLevel.forEnum(PRVI25_SubductionScalingRelationships.class, "Interface Scaling Relationship", "InterfaceScale");
	public static LogicTreeLevel<PRVI25_SubductionBValues> INTERFACE_INVERSION_B =
			LogicTreeLevel.forEnum(PRVI25_SubductionBValues.class, "Interface inversion b-value", "InterfaceB");
	public static LogicTreeLevel<NSHM26_InterfaceObsSeisDMAdjustment> INTERFACE_OBS_SEIS_DM_ADJ =
			LogicTreeLevel.forEnum(NSHM26_InterfaceObsSeisDMAdjustment.class, "Interface Observed Seismicity Adjustment", "InterfaceObsSeisDMAdj");
	public static LogicTreeLevel<NSHM26_InterfaceMinSubSects> INTERFACE_MIN_SUB_SECTS =
			LogicTreeLevel.forEnum(NSHM26_InterfaceMinSubSects.class, "Interface Minimum Subsection Count", "InterfaceMinSubSects");
	public static NSHM26_SeisRateModelSamples GNMI_INTERFACE_RATE_SAMPLES =
			new NSHM26_SeisRateModelSamples(NSHM26_SeismicityRegions.GNMI, TectonicRegionType.SUBDUCTION_INTERFACE);
	public static NSHM26_SeisRateModelSamples AMSAM_INTERFACE_RATE_SAMPLES =
			new NSHM26_SeisRateModelSamples(NSHM26_SeismicityRegions.AMSAM, TectonicRegionType.SUBDUCTION_INTERFACE);
	/*
	 * 
	 */
	
	/*
	 * Subduction interface branch levels (gridded)
	 * missing/TODO:
	 * 
	 * * option to use observed seismicity b-value?
	 */
	
	
	public static List<LogicTreeLevel<? extends LogicTreeNode>> buildLevels(NSHM26_SeismicityRegions seisReg,
			TectonicRegionType trt, boolean sampled) {
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
		
		// inversion
		switch (trt) {
		case SUBDUCTION_INTERFACE:
			levels.add(INTERFACE_FM);
			levels.add(INTERFACE_DEPTH_COUPLING);
			levels.add(INTERFACE_DM);
			levels.add(INTERFACE_SCALE);
			levels.add(INTERFACE_INVERSION_B);
			levels.add(INTERFACE_OBS_SEIS_DM_ADJ);
			levels.add(INTERFACE_MIN_SUB_SECTS);
			break;
		case SUBDUCTION_SLAB:
			levels.add(INTERFACE_FM); // gridded only, add interface for region detection
			break;
		case ACTIVE_SHALLOW:
			if (seisReg == NSHM26_SeismicityRegions.GNMI) {
				// have crustal on-fault
				// TODO
			} else {
				levels.add(INTERFACE_FM); // gridded only, add interface for region detection
			}
			break;
		default:
			throw new IllegalArgumentException("Unexpected TRT: " + trt);
		}
		
		if (sampled)
			levels.add(new NSHM26_SeisRateModelSamples(seisReg, trt));
		else
			levels.add(LogicTreeLevel.forEnum(NSHM26_SeisRateModelBranch.class,
					seisReg.getShortName()+" Rate Model Branch ("+NSHM26_RegionLoader.getNameForTRT(trt)+")",
					seisReg.name()+"-"+NSHM26_RegionLoader.getNameForTRT(trt)+"Branch"));
		levels.add(LogicTreeLevel.forEnum(NSHM26_DeclusteringAlgorithms.class,
				seisReg.getShortName()+" Declustering Algorithm ("+NSHM26_RegionLoader.getNameForTRT(trt)+")",
				seisReg.name()+"-"+NSHM26_RegionLoader.getNameForTRT(trt)+"-Decluster"));
		levels.add(LogicTreeLevel.forEnum(NSHM26_SeisSmoothingAlgorithms.class,
				seisReg.getShortName()+" Smoothing Kernel ("+NSHM26_RegionLoader.getNameForTRT(trt)+")",
				seisReg.name()+"-"+NSHM26_RegionLoader.getNameForTRT(trt)+"-Smooth"));
		
		if (trt == TectonicRegionType.ACTIVE_SHALLOW) {
//			if (sampled) 
//				// TODO
//				levels.add(new NSHM26_OffFaultMmax.CrustalSampledLevel(CorrTruncatedNormalDistribution.of(7.6, 0.134, 7, 8)));
//			else
//				levels.add(new NSHM26_OffFaultMmax.CrustalSampledLevel(CorrTruncatedNormalDistribution.of(7.6, 0.134, 7, 8)));
		}
		return levels;
	}
	
	public static LogicTreeBranch<LogicTreeNode> buildInterfaceDefault(NSHM26_SeismicityRegions seisReg,
			TectonicRegionType trt, boolean rateModelSamples) {
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = buildLevels(seisReg, trt, rateModelSamples);
		LogicTreeBranch<LogicTreeNode> branch = new LogicTreeBranch<>(levels);
		
		// inversion
		switch (trt) {
		case SUBDUCTION_INTERFACE:
			branch.setValue(NSHM26_InterfaceFaultModels.regionDefault(seisReg));
			branch.setValue(NSHM26_InterfaceCouplingDepthModels.DOUBLE_TAPER);
			branch.setValue(NSHM26_InterfaceDeformationModels.PREF_COUPLING);
			branch.setValue(PRVI25_SubductionScalingRelationships.LOGA_C4p0);
			branch.setValue(PRVI25_SubductionBValues.B_0p5); // TODO
			branch.setValue(NSHM26_InterfaceObsSeisDMAdjustment.AVERAGE); // TODO
			branch.setValue(NSHM26_InterfaceMinSubSects.FOUR); // TODO
			break;
		case SUBDUCTION_SLAB:
			branch.setValue(NSHM26_InterfaceFaultModels.regionDefault(seisReg)); // still keep FM for region detection
			break;
		case ACTIVE_SHALLOW:
			break;
		default:
			throw new IllegalArgumentException("Unexpected TRT: " + trt);
		}
		
		// gridded seismicity
		if (!rateModelSamples)
			branch.setValue(NSHM26_SeisRateModelBranch.AVERAGE);
		branch.setValue(NSHM26_DeclusteringAlgorithms.AVERAGE);
		branch.setValue(NSHM26_SeisSmoothingAlgorithms.AVERAGE);
		
		switch (trt) {
		case SUBDUCTION_SLAB:
			
			break;
		case ACTIVE_SHALLOW:
			
			break;

		default:
			break;
		}
		
		return branch;
	}

}
