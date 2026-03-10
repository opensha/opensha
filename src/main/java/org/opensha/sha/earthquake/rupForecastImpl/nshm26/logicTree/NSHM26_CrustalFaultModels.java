package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetSubsectioningModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM26_CrustalFaultModels implements RupSetFaultModel, RupSetSubsectioningModel {
	GNMI_V1("Guam & Northern Mariana Islands (Crustal FM v1)", "GNMI-Crust-V1", NSHM26_SeismicityRegions.GNMI, 1d,
			"/data/erf/nshm26/gnmi/fault_models/crustal/NSHM26_GNMI_FaultSections_v1.geojson");
	
	private String name;
	private String shortName;
	private NSHM26_SeismicityRegions seisReg;
	private double weight;
	private String jsonPath;


	private NSHM26_CrustalFaultModels(String name, String shortName, NSHM26_SeismicityRegions seisReg, double weight, String jsonPath) {
		this.name = name;
		this.shortName = shortName;
		this.seisReg = seisReg;
		this.weight = weight;
		this.jsonPath = jsonPath;
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
	public List<? extends FaultSection> buildSubSects(RupSetFaultModel faultModel,
			List<? extends FaultSection> fullSections) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<? extends FaultSection> getFaultSections() throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(NSHM26_CrustalFaultModels.class.getResourceAsStream(jsonPath)));
		List<GeoJSONFaultSection> sects = GeoJSONFaultReader.readFaultSections(reader);
		return sects;
	}

	@Override
	public RupSetDeformationModel getDefaultDeformationModel() {
		// TODO Auto-generated method stub
		return null;
	}

}
