package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.subduction.InterfaceDeformationProjection;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM26_SubductionInterfaceDeformationModels implements RupSetDeformationModel {
	LOW_COUPLING("Low Interface Coupling", "Low", 1d/3d),
	PREF_COUPLING("Preferred Interface Coupling", "Preferred", 1d/3d),
	HIGH_COUPLING("High Interface Coupling", "High", 1d/3d);
	
	private String name;
	private String shortName;
	private double weight;

	private NSHM26_SubductionInterfaceDeformationModels(String name, String shortName, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.weight = weight;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
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
		return faultModel instanceof NSHM26_SubductionInterfaceFaultModels;
	}

	@Override
	public List<? extends FaultSection> apply(RupSetFaultModel faultModel,
			LogicTreeBranch<? extends LogicTreeNode> branch, List<? extends FaultSection> fullSects,
			List<? extends FaultSection> subSects) throws IOException {
		Preconditions.checkState(faultModel instanceof NSHM26_SubductionInterfaceFaultModels);
		NSHM26_SubductionInterfaceFaultModels fm = (NSHM26_SubductionInterfaceFaultModels)faultModel;
		String csvPath;
		if (faultModel == NSHM26_SubductionInterfaceFaultModels.TONGA) {
			csvPath = "/data/erf/nshm26/amsam/deformation_models/subduction/ker_trace_dm.csv";
		} else if (faultModel == NSHM26_SubductionInterfaceFaultModels.MARIANA) {
			csvPath = "/data/erf/nshm26/gnmi/deformation_models/subduction/izu_trace_dm.csv";
		} else {
			throw new IllegalStateException("Unexpected FM: "+faultModel);
		}
		InputStream is = NSHM26_SubductionInterfaceDeformationModels.class.getResourceAsStream(csvPath);
		Preconditions.checkNotNull(is, "Couldn't load CSV: %s", csvPath);
		CSVFile<String> csv = CSVFile.readStream(is, true);
		FaultTrace trace = new FaultTrace(faultModel.getName(), csv.getNumRows()-1);
		double[] slips = new double[csv.getNumRows()-1];
		int column;
		switch (this) {
		case LOW_COUPLING:
			column = 6;
			break;
		case PREF_COUPLING:
			column = 7;
			break;
		case HIGH_COUPLING:
			column = 8;
			break;

		default:
			throw new IllegalStateException("Unexpected model: "+this);
		}
		
		Preconditions.checkState(fullSects.size() == 1);
		FaultSection fullSect = fullSects.get(0);
		
		for (int i=0; i<slips.length; i++) {
			int row = i+1;
			Location loc = new Location(csv.getDouble(row, 1), csv.getDouble(row, 0));
			if (loc.lon < 0)
				loc = new Location(loc.lat, loc.lon+360d);
			trace.add(loc);
			slips[i] = csv.getDouble(row, column);
			Preconditions.checkState(Double.isFinite(slips[i]) && slips[i] >= 0, "Bad slip rate: %s", slips[i]);
		}
		
		InterfaceDeformationProjection.checkForTraceDirection(fullSect.getFaultTrace(), trace, slips);
		
		if (fm.getSlipSmoothingDistance() > 0)
			slips = InterfaceDeformationProjection.getSmoothedDeformationFrontSlipRates(trace, slips, fm.getSlipSmoothingDistance());
		
		InterfaceDeformationProjection.projectSlipRates(subSects, trace, slips);
		
		NSHM26_SubductionInterfaceCouplingDepthModels depthCoupling = branch.getValue(
				NSHM26_SubductionInterfaceCouplingDepthModels.class);
		if (depthCoupling != null)
			depthCoupling.apply(subSects);
		
		for (FaultSection sect : subSects)
			sect.setSlipRateStdDev(sect.getOrigAveSlipRate()*0.1);
		
		return subSects;
	}
}
