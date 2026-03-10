package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalDeformationModels;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public enum NSHM26_CrustalAggregatedDeformationModels implements RupSetDeformationModel {
	UPPER("Upper Bounds", "Upper", F-> {
		for (int i=F.size(); --i>=0;)
			if (F.getY(i) > 0)
				return F.getX(i);
		throw new IllegalStateException("all zero");
	}),
	AVERAGE("Average", "Average", F-> {
		double wtSum = 0d;
		double sum = 0d;
		for (Point2D pt : F) {
			sum += pt.getX()*pt.getY();
			wtSum += pt.getY();
		}
		return sum/wtSum;
	}),
	LOWER("Lower Bounds", "Lower", F-> {
		for (int i=0; i<F.size(); i++)
			if (F.getY(i) > 0)
				return F.getX(i);
		throw new IllegalStateException("all zero");
	});
	
	private String name;
	private String shortName;
	private Function<DiscretizedFunc, Double> pdfFunc;

	private NSHM26_CrustalAggregatedDeformationModels(String name, String shortName,
			Function<DiscretizedFunc, Double> pdfFunc) {
		this.name = name;
		this.shortName = shortName;
		this.pdfFunc = pdfFunc;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return 0; // only for plots
	}

	@Override
	public String getFilePrefix() {
		return name();
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
	public boolean isApplicableTo(RupSetFaultModel faultModel) {
		return faultModel instanceof NSHM26_CrustalFaultModels;
	}

	@Override
	public List<? extends FaultSection> apply(RupSetFaultModel faultModel,
			LogicTreeBranch<? extends LogicTreeNode> branch, List<? extends FaultSection> fullSects,
			List<? extends FaultSection> subSects) throws IOException {
		Map<Integer, DiscretizedFunc> pdfs = NSHM26_CrustalRandomlySampledDeformationModels.getPDFs(fullSects);
		Map<Integer, Double> sectVals = new HashMap<>(pdfs.size());
		for (Integer index : pdfs.keySet()) {
			double val = pdfFunc.apply(pdfs.get(index));
			sectVals.put(index, val);
		}
		for (FaultSection sect : subSects) {
			int parentID = sect.getParentSectionId();
			Double slipRate = sectVals.get(parentID);
			Preconditions.checkNotNull(slipRate, "No PDF found for parent=%s, %s", parentID, sect);
			sect.setAveSlipRate(slipRate);
		}
		PRVI25_CrustalDeformationModels.applyStdDevDefaults(subSects);
		PRVI25_CrustalDeformationModels.applyCreepDefaults(subSects);
		return subSects;
	}

}
