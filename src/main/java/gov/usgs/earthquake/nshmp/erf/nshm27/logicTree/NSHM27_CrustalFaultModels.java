package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

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
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.modules.RegionsOfInterest;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.util.SubSectionBuilder;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.util.TectonicRegionType;

import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM27_CrustalFaultModels implements RupSetFaultModel, RupSetSubsectioningModel {
	GNMI_V1("Guam & Northern Mariana Islands (Crustal FM v1)", "GNMI-Crust-V1", NSHM27_SeismicityRegions.GNMI, 1d,
			"/data/erf/nshm27/gnmi/fault_models/crustal/NSHM27_GNMI_FaultSections_v1.geojson");
	
	private String name;
	private String shortName;
	private NSHM27_SeismicityRegions seisReg;
	private double weight;
	private String jsonPath;


	private NSHM27_CrustalFaultModels(String name, String shortName, NSHM27_SeismicityRegions seisReg, double weight, String jsonPath) {
		this.name = name;
		this.shortName = shortName;
		this.seisReg = seisReg;
		this.weight = weight;
		this.jsonPath = jsonPath;
	}
	
	public NSHM27_SeismicityRegions getSeisReg() {
		return seisReg;
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
	
	static final double DOWN_DIP_FRACT_DEFAULT = 0.5;
	static final double MAX_LEN_DEFAULT = Double.NaN;
	static final int MIN_SUB_SECTS_PER_FAULT_DEFAULT = 2;

	@Override
	public List<? extends FaultSection> buildSubSects(RupSetFaultModel faultModel,
			List<? extends FaultSection> fullSections) {
		return SubSectionBuilder.buildSubSects(fullSections,
				MIN_SUB_SECTS_PER_FAULT_DEFAULT, DOWN_DIP_FRACT_DEFAULT, MAX_LEN_DEFAULT);
	}

	@Override
	public List<? extends FaultSection> getFaultSections() throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(NSHM27_CrustalFaultModels.class.getResourceAsStream(jsonPath)));
		List<GeoJSONFaultSection> sects = GeoJSONFaultReader.readFaultSections(reader);
		for (GeoJSONFaultSection sect : sects)
			sect.setTectonicRegionType(TectonicRegionType.ACTIVE_SHALLOW);
		return sects;
	}

	@Override
	public RupSetDeformationModel getDefaultDeformationModel() {
		return NSHM27_CrustalAggregatedDeformationModels.AVERAGE;
	}

	@Override
	public void attachDefaultModules(FaultSystemRupSet rupSet) {
		rupSet.addAvailableModule(() -> {
			return NSHM27_InterfaceFaultModels.buildROI(seisReg);
		}, RegionsOfInterest.class);
		rupSet.addAvailableModule(() -> {
			return new ModelRegion(seisReg.load());
		}, ModelRegion.class);
		rupSet.addAvailableModule(() -> {
			return RupSetTectonicRegimes.constant(rupSet, TectonicRegionType.ACTIVE_SHALLOW);
		}, RupSetTectonicRegimes.class);
	}

}
