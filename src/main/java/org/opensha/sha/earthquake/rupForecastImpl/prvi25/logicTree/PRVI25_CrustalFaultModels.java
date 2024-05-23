package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.Callable;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.modules.ProxyFaultSections;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader;
import org.opensha.sha.faultSurface.FaultSection;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum PRVI25_CrustalFaultModels implements RupSetFaultModel {
	PRVI_FM_INITIAL("Initial Draft FM", "InitialFM", "/data/erf/prvi25/fault_models/crustal/NSHM2025_GeoDefModel_PRVI_v1-1_mod.geojson", 1d);
	
	private String name;
	private String shortName;
	private String jsonPath;
	private double weight;

	private PRVI25_CrustalFaultModels(String name, String shortName, String jsonPath, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.jsonPath = jsonPath;
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
	public List<? extends FaultSection> getFaultSections() throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(PRVI25_CrustalFaultModels.class.getResourceAsStream(jsonPath)));
		return GeoJSONFaultReader.readFaultSections(reader);
	}
	
	public static ModelRegion getDefaultRegion(LogicTreeBranch<?> branch) throws IOException {
		return new ModelRegion(PRVI25_RegionLoader.loadPRVI_ModelBroad());
	}

	@Override
	public void attachDefaultModules(FaultSystemRupSet rupSet) {
		LogicTreeBranch<?> branch = rupSet.getModule(LogicTreeBranch.class);
		
		rupSet.addAvailableModule(new Callable<ModelRegion>() {

			@Override
			public ModelRegion call() throws Exception {
				return getDefaultRegion(branch);
			}
		}, ModelRegion.class);
		
		rupSet.addAvailableModule(new Callable<ProxyFaultSections>() {

			@Override
			public ProxyFaultSections call() throws Exception {
				return ProxyFaultSections.build(rupSet, 10);
			}
		}, ProxyFaultSections.class);
		// TODO: named faults?
		// TODO: regions of interest
		// TODO: tectonic regimes?
	}

	@Override
	public RupSetDeformationModel getDefaultDeformationModel() {
		return PRVI25_CrustalDeformationModels.GEOLOGIC;
	}

}
