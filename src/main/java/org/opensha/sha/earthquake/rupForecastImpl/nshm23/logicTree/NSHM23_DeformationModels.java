package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.DoesNotAffect;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@DoesNotAffect(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM23_DeformationModels implements RupSetDeformationModel {
	@Deprecated
	GEOL_V1p2("NSHM23 Geologic Deformation Model v1.2", "Geologic V1.2", 0d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			return buildGeol(faultModel, "v1p2");
		}
	},
	GEOL_V1p3("NSHM23 Geologic Deformation Model v1.3", "Geologic V1.3", 1d) {
		@Override
		public List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException {
			return buildGeol(faultModel, "v1p3");
		}
	};
	
	private static final String NSHM23_DM_PATH_PREFIX = "/data/erf/nshm23/def_models/";

	private String name;
	private String shortName;
	private double weight;

	private NSHM23_DeformationModels(String name, String shortName, double weight) {
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
		return faultModel instanceof NSHM23_FaultModels;
	}
	
	@Override
	public abstract List<? extends FaultSection> build(RupSetFaultModel faultModel) throws IOException;

	public List<? extends FaultSection> buildGeolFullSects(RupSetFaultModel faultModel, String version) throws IOException {
		Preconditions.checkState(isApplicableTo(faultModel), "DM/FM mismatch");
		String dmPath = NSHM23_DM_PATH_PREFIX+"geologic/"+version+"/NSHM23_GeolDefMod_"+version+".geojson";
		Reader dmReader = new BufferedReader(new InputStreamReader(
				GeoJSONFaultReader.class.getResourceAsStream(dmPath)));
		Preconditions.checkNotNull(dmReader, "Deformation model file not found: %s", dmPath);
		FeatureCollection defModel = FeatureCollection.read(dmReader);
		
		List<GeoJSONFaultSection> geoSects = new ArrayList<>();
		for (FaultSection sect : faultModel.getFaultSections()) {
			if (sect instanceof GeoJSONFaultSection)
				geoSects.add((GeoJSONFaultSection)sect);
			else
				geoSects.add(new GeoJSONFaultSection(sect));
		}
		GeoJSONFaultReader.attachGeoDefModel(geoSects, defModel);
		
		return geoSects;
	}

	public List<? extends FaultSection> buildGeol(RupSetFaultModel faultModel, String version) throws IOException {
		List<? extends FaultSection> geoSects = buildGeolFullSects(faultModel, version);
		
		return GeoJSONFaultReader.buildSubSects(geoSects);
	}

}
