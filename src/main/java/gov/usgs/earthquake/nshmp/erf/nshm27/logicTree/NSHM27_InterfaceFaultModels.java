package gov.usgs.earthquake.nshmp.erf.nshm27.logicTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.FeatureCollection;
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
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_MapRegions;
import gov.usgs.earthquake.nshmp.erf.nshm27.util.NSHM27_RegionLoader.NSHM27_SeismicityRegions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM27_InterfaceFaultModels implements RupSetFaultModel, RupSetSubsectioningModel {
	AMSAM_V1("Amarican Samoa (Interface FM v1)", "AmSam-Inter-v1",
			"/data/erf/nshm27/amsam/fault_models/subduction/", NSHM27_SeismicityRegions.AMSAM, 0d),
	GNMI_V1("Guam & Northern Mariana Islands (Interface FM v1)", "GNMI-Inter-v1",
			"/data/erf/nshm27/gnmi/fault_models/subduction/", NSHM27_SeismicityRegions.GNMI, 100d);
	
	public static NSHM27_InterfaceFaultModels regionDefault(NSHM27_SeismicityRegions region) {
		for (NSHM27_InterfaceFaultModels fm : values())
			if (fm.seisReg == region && fm.getNodeWeight(null) > 0)
				return fm;
		throw new IllegalStateException("No FMs found for "+region);
	}

	private String name;
	private String shortName;
	private String dataPrefix;
	private double slipSmoothingDist;
	private NSHM27_SeismicityRegions seisReg;

	NSHM27_InterfaceFaultModels(String name, String shortName, String dataPrefix, NSHM27_SeismicityRegions seisReg, double slipSmoothingDist) {
		this.name = name;
		this.shortName = shortName;
		this.dataPrefix = dataPrefix;
		this.seisReg = seisReg;
		this.slipSmoothingDist = slipSmoothingDist;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return 1;
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
	
	public double getSlipSmoothingDistance() {
		return slipSmoothingDist;
	}
	
	public NSHM27_SeismicityRegions getSeisReg() {
		return seisReg;
	}

	@Override
	public List<? extends FaultSection> buildSubSects(RupSetFaultModel faultModel) throws IOException {
		String path = dataPrefix+"sub_sections.geojson";
		InputStream is = NSHM27_InterfaceFaultModels.class.getResourceAsStream(path);
		Preconditions.checkNotNull(is, "Could not load %s", path);
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		List<GeoJSONFaultSection> sects = GeoJSONFaultReader.readFaultSections(reader);
		for (GeoJSONFaultSection sect : sects)
			sect.setTectonicRegionType(TectonicRegionType.SUBDUCTION_INTERFACE);
		return sects;
	}

	@Override
	public List<? extends FaultSection> buildSubSects(RupSetFaultModel faultModel,
			List<? extends FaultSection> fullSections) {
		try {
			return buildSubSects(faultModel);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}

	@Override
	public List<? extends FaultSection> getFaultSections() throws IOException {
		String path = dataPrefix+"full_section.geojson";
		InputStream is = NSHM27_InterfaceFaultModels.class.getResourceAsStream(path);
		Preconditions.checkNotNull(is, "Could not load %s", path);
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		FeatureCollection fullSectCollection = FeatureCollection.read(reader);
		Preconditions.checkState(fullSectCollection.features.size() == 1);
		GeoJSONFaultSection sect = GeoJSONFaultSection.fromFeature(fullSectCollection.features.get(0));
		sect.setTectonicRegionType(TectonicRegionType.SUBDUCTION_INTERFACE);
		return List.of(sect);
	}

	@Override
	public RupSetDeformationModel getDefaultDeformationModel() {
		return NSHM27_InterfaceDeformationModels.PREF_COUPLING;
	}

	@Override
	public void attachDefaultModules(FaultSystemRupSet rupSet) {
		rupSet.addAvailableModule(() -> {
			return buildROI(seisReg);
		}, RegionsOfInterest.class);
		rupSet.addAvailableModule(() -> {
			return new ModelRegion(seisReg.load());
		}, ModelRegion.class);
		rupSet.addAvailableModule(() -> {
			return RupSetTectonicRegimes.constant(rupSet, TectonicRegionType.SUBDUCTION_INTERFACE);
		}, RupSetTectonicRegimes.class);
	}
	
	public static RegionsOfInterest buildROI(NSHM27_SeismicityRegions seisReg) throws IOException {
		TectonicRegionType[] trts = {TectonicRegionType.SUBDUCTION_INTERFACE,
				TectonicRegionType.SUBDUCTION_SLAB,TectonicRegionType.ACTIVE_SHALLOW};
		List<Region> trtRegions = new ArrayList<>(trts.length*2);
		List<IncrementalMagFreqDist> trtMFDs = new ArrayList<>(trts.length*2);
		List<TectonicRegionType> regionTRTs = new ArrayList<>(trts.length*2);
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(5.01, 9.01);
		for (TectonicRegionType trt : trts) {
			trtRegions.add(cloneForTRT(seisReg.load(), trt));
			double mMax = NSHM27_SeisRateModelBranch.getPlotMmax(trt);
			trtMFDs.add(NSHM27_SeisRateModelBranch.loadRateModel(seisReg, trt).getBounded(refMFD, mMax));
			regionTRTs.add(trt);
		}
		NSHM27_MapRegions mapRegion = NSHM27_MapRegions.valueOf(seisReg.name());
		for (TectonicRegionType trt : trts) {
			trtRegions.add(cloneForTRT(mapRegion.load(), trt));
//			double mMax = NSHM27_SeisRateModelBranch.getPlotMmax(trt);
//			trtMFDs.add(NSHM27_SeisRateModelBranch.loadRateModel(seisReg, trt).getBounded(refMFD, mMax));
			// TODO remapped
			trtMFDs.add(null);
			regionTRTs.add(trt);
		}
		RegionsOfInterest roi = new RegionsOfInterest(trtRegions, trtMFDs, regionTRTs);
		return roi;
	}
	
	private static Region cloneForTRT(Region reg, TectonicRegionType trt) {
		reg = reg.clone();
		reg.setName(reg.getName()+" ("+NSHM27_RegionLoader.getNameForTRT(trt)+")");
		return reg;
	}

}
