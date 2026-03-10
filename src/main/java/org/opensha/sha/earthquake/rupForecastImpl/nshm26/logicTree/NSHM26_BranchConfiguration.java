package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetScalingRelationship;
import org.opensha.sha.earthquake.faultSysSolution.RupSetSubsectioningModel;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_ScalingRelationships;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SegmentationModels;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalBValues;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionBValues;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_SubductionScalingRelationships;

public class NSHM26_BranchConfiguration {
	
	// interface inversion
	private NSHM26_InterfaceFaultModels interfaceFM;
	private NSHM26_InterfaceDeformationModels interfaceDM;
	private PRVI25_SubductionScalingRelationships interfaceScale;
	private double interfaceB;
	private int interfaceMinNumSubSects;
	private NSHM26_InterfaceSubSeisAdjustment interfaceSubSeisAdjust;
	
	// interface gridded
	private NSHM26_SeisRateModel interfaceGridRateModel;
	private NSHM26_DeclusteringAlgorithms interfaceDecluster;
	private NSHM26_SeisSmoothingAlgorithms interfaceSmoothing;
	
	// intraslab gridded
	private double intraslabMmax;
	private NSHM26_SeisRateModel intraslabRateModel;
	private NSHM26_DeclusteringAlgorithms intraslabDecluster;
	private NSHM26_SeisSmoothingAlgorithms intraslabSmoothing;
	
	// crustal inversion
	private NSHM26_CrustalFaultModels crustalFM;
	private NSHM26_CrustalRandomlySampledDeformationModels crustalDM;
	private double crustalB;
	private NSHM23_ScalingRelationships crustalScale;
	private NSHM23_SegmentationModels crustalSeg;
	
	// crustal gridded
	private double crustalMmax;
	private NSHM26_SeisRateModel crustalRateModel;
	private NSHM26_DeclusteringAlgorithms crustalDecluster;
	private NSHM26_SeisSmoothingAlgorithms crustalSmoothing;
}
