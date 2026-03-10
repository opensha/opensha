package org.opensha.sha.earthquake.rupForecastImpl.nshm26.logicTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
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
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm26.util.NSHM26_RegionLoader.NSHM26_SeismicityRegions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum NSHM26_SubductionInterfaceFaultModels implements RupSetFaultModel, RupSetSubsectioningModel {
	TONGA("Tonga Trench", "/data/erf/nshm26/amsam/fault_models/subduction/", 0d),
	MARIANA("Mariana Trench", "/data/erf/nshm26/gnmi/fault_models/subduction/", 100d);

	private String name;
	private String dataPrefix;
	private double slipSmoothingDist;

	NSHM26_SubductionInterfaceFaultModels(String name, String dataPrefix, double slipSmoothingDist) {
		this.name = name;
		this.dataPrefix = dataPrefix;
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
		return name;
	}

	@Override
	public String getName() {
		return name;
	}
	
	public double getSlipSmoothingDistance() {
		return slipSmoothingDist;
	}

	@Override
	public List<? extends FaultSection> buildSubSects(RupSetFaultModel faultModel) throws IOException {
		String path = dataPrefix+"sub_sections.geojson";
		InputStream is = NSHM26_SubductionInterfaceFaultModels.class.getResourceAsStream(path);
		Preconditions.checkNotNull(is, "Could not load %s", path);
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		return GeoJSONFaultReader.readFaultSections(reader);
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
		InputStream is = NSHM26_SubductionInterfaceFaultModels.class.getResourceAsStream(path);
		Preconditions.checkNotNull(is, "Could not load %s", path);
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		FeatureCollection fullSectCollection = FeatureCollection.read(reader);
		Preconditions.checkState(fullSectCollection.features.size() == 1);
		return List.of(GeoJSONFaultSection.fromFeature(fullSectCollection.features.get(0)));
	}

	@Override
	public RupSetDeformationModel getDefaultDeformationModel() {
		return NSHM26_SubductionInterfaceDeformationModels.PREF_COUPLING;
	}

	@Override
	public void attachDefaultModules(FaultSystemRupSet rupSet) {
		try {
			if (this == TONGA) {
				rupSet.addModule(new ModelRegion(NSHM26_SeismicityRegions.AMSAM.load()));
			} else if (this == MARIANA) {
				rupSet.addModule(new ModelRegion(NSHM26_SeismicityRegions.GNMI.load()));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
