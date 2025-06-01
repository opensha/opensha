package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetSubsectioningModel;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.utils.DeformationModelFetcher;

/**
 * UCERF3 deformation models with uncertainties added (inferred from geologic bounds) for use in the NSHM23 constraint
 * framework
 * 
 * @author kevin
 *
 */
@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum U3_UncertAddDeformationModels implements LogicTreeNode, RupSetDeformationModel, RupSetSubsectioningModel {
	U3_ABM("UCERF3 Average Block Deformation Model", "U3 ABM", DeformationModels.ABM),
	U3_GEOL("UCERF3 Geologic Deformation Model", "U3 Geol", DeformationModels.GEOLOGIC),
	U3_NEOK("UCERF3 Neokinema Deformation Model", "U3 Neok", DeformationModels.NEOKINEMA),
	U3_ZENG("UCERF3 Zeng Deformation Model", "U3 Zeng", DeformationModels.ZENGBB),
	U3_MEAN("UCERF3 Mean Deformation Model", "U3 Mean", DeformationModels.MEAN_UCERF3);
	
	private String name;
	private String shortName;
	private DeformationModels u3dm;

	private U3_UncertAddDeformationModels(String name, String shortName, DeformationModels u3dm) {
		this.name = name;
		this.shortName = shortName;
		this.u3dm = u3dm;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return u3dm.getNodeWeight(fullBranch);
	}

	@Override
	public String getFilePrefix() {
		return name();
	}

	@Override
	public boolean isApplicableTo(RupSetFaultModel faultModel) {
		return u3dm.isApplicableTo(faultModel);
	}

	@Override
	public List<? extends FaultSection> build(RupSetFaultModel faultModel, RupSetSubsectioningModel subSectionModel,
			LogicTreeBranch<? extends LogicTreeNode> branch) throws IOException {
		Preconditions.checkState(faultModel instanceof FaultModels, "%s is not a UCERF3 fault model", faultModel.getName());
		Preconditions.checkState(subSectionModel == null || subSectionModel == this, "UCERF3 DMs build their own subsections");
		FaultModels fm = (FaultModels)faultModel;
		DeformationModelFetcher dmFetch = new DeformationModelFetcher(fm, u3dm, null, 0.1);
		List<? extends FaultSection> subSects = dmFetch.getSubSectionList();
		
		if (!NSHM23_DeformationModels.isHardcodedFractionalStdDev()) {
			// infer standard deviations from geologic bounds
			// assume bounds are +/- 2 sigma
			System.out.println("Inferring slip-rate standard deviations from geologic bounds...");
			List<? extends FaultSection> lowerSects = new DeformationModelFetcher(
					fm, DeformationModels.GEOLOGIC_LOWER, null, 0.1).getSubSectionList();
			List<? extends FaultSection> upperSects = new DeformationModelFetcher(
					fm, DeformationModels.GEOLOGIC_UPPER, null, 0.1).getSubSectionList();
			
			for (int s=0; s<subSects.size(); s++) {
				double upper = upperSects.get(s).getOrigAveSlipRate();
				double lower = lowerSects.get(s).getOrigAveSlipRate();
				subSects.get(s).setSlipRateStdDev((upper-lower)/4d);
			}
		}
		
		NSHM23_DeformationModels.applyStdDevDefaults(subSects);
		
		return subSects;
	}

	@Override
	public List<? extends FaultSection> apply(RupSetFaultModel faultModel,
			LogicTreeBranch<? extends LogicTreeNode> branch, List<? extends FaultSection> subSects) throws IOException {
		throw new UnsupportedOperationException("Not supported, UCERF3 must build the subsections");
	}

	@Override
	public List<? extends FaultSection> apply(RupSetFaultModel faultModel,
			LogicTreeBranch<? extends LogicTreeNode> branch, List<? extends FaultSection> fullSects,
			List<? extends FaultSection> subSects) throws IOException {
		throw new UnsupportedOperationException("Not supported, UCERF3 must build the subsections");
	}

	@Override
	public List<? extends FaultSection> buildSubSects(RupSetFaultModel faultModel,
			List<? extends FaultSection> fullSections) {
		try {
			return build(faultModel, this, null);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	public static void main(String[] args) throws IOException {
		File outputDir = new File("/tmp");
		FaultModels fm = FaultModels.FM3_1;
		for (U3_UncertAddDeformationModels dm : values()) {
			String fileName = fm.getFilePrefix()+"-"+dm.getFilePrefix()+"-sub_sects.geojson";
			
			List<? extends FaultSection> subSects = dm.build(fm, null, null);
			GeoJSONFaultReader.writeFaultSections(new File(outputDir, fileName), subSects);
		}
	}

}
