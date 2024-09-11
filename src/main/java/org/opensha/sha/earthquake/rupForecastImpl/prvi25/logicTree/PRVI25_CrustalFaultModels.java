package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.modules.ProxyFaultSectionInstances;
import org.opensha.sha.earthquake.faultSysSolution.modules.RegionsOfInterest;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader.PRVI25_SeismicityRegions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum PRVI25_CrustalFaultModels implements RupSetFaultModel {
	PRVI_CRUSTAL_FM_V1p1("PRVI25 Crustal FM v1.1", "Crustal FM v1.1",
			"/data/erf/prvi25/fault_models/crustal/NSHM2025_GeoDefModel_PRVI_v1-1_mod.geojson", 1d);
	
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
		
		rupSet.addAvailableModule(new Callable<ProxyFaultSectionInstances>() {

			@Override
			public ProxyFaultSectionInstances call() throws Exception {
				return ProxyFaultSectionInstances.build(rupSet, 5, 5d, 0.25, 5, 10);
			}
		}, ProxyFaultSectionInstances.class);
		
		rupSet.addAvailableModule(new Callable<RegionsOfInterest>() {

			@Override
			public RegionsOfInterest call() throws Exception {
				List<Region> regions = new ArrayList<>();
				List<IncrementalMagFreqDist> regionMFDs = new ArrayList<>();
				List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
				
				// overall seismicity regions
//				for (SeismicityRegions seisReg : SeismicityRegions.values()) {
				for (PRVI25_SeismicityRegions seisReg : new PRVI25_SeismicityRegions[] {PRVI25_SeismicityRegions.CRUSTAL}) {
					Region region = seisReg.load();
					if (!FaultSectionUtils.anySectInRegion(region, subSects, true))
						continue;
					
					regionMFDs.add(getRegionalMFD(region, seisReg, branch));
					regions.add(region);
				}
				
//				// analysis regions
//				for (Region region : NSHM23_RegionLoader.loadAnalysisRegions(subSects)) {
//					if (!FaultSectionUtils.anySectInRegion(region, subSects, true))
//						continue;
//					
//					regionMFDs.add(getRegionalMFD(region, null, branch));
//					regions.add(region);
//				}
//				
//				// local regions
//				for (Region region : NSHM23_RegionLoader.loadLocalRegions(subSects)) {
//					if (!FaultSectionUtils.anySectInRegion(region, subSects, true))
//						continue;
//					
//					regionMFDs.add(getRegionalMFD(region, null, branch));
//					regions.add(region);
//				}
				for (int i=0; i<regions.size(); i++) {
					String regName = regions.get(i).getName();
					System.out.println(regName);
					IncrementalMagFreqDist mfd = regionMFDs.get(i);
					if (mfd != null) {
						System.out.println("\t"+mfd.getName());
						if (mfd instanceof UncertainBoundedDiscretizedFunc)
							System.out.println("\t"+((UncertainBoundedDiscretizedFunc)mfd).getBoundName());
					}
				}
//				System.exit(0);
				return new RegionsOfInterest(regions, regionMFDs);
			}
		}, RegionsOfInterest.class);
		rupSet.addAvailableModule(new Callable<RupSetTectonicRegimes>() {

			@Override
			public RupSetTectonicRegimes call() throws Exception {
				return RupSetTectonicRegimes.constant(rupSet, TectonicRegionType.ACTIVE_SHALLOW);
			}
		}, RupSetTectonicRegimes.class);
		// TODO: named faults?
	}
	
	private static UncertainBoundedIncrMagFreqDist getRegionalMFD(Region region, PRVI25_SeismicityRegions seisRegion,
			LogicTreeBranch<?> branch) throws IOException {
		PRVI25_DeclusteringAlgorithms declustering = PRVI25_DeclusteringAlgorithms.AVERAGE;
		if (branch != null && branch.hasValue(PRVI25_DeclusteringAlgorithms.class))
			declustering = branch.requireValue(PRVI25_DeclusteringAlgorithms.class);
		
		PRVI25_SeisSmoothingAlgorithms smooth = PRVI25_SeisSmoothingAlgorithms.AVERAGE;
		if (branch != null && branch.hasValue(PRVI25_SeisSmoothingAlgorithms.class))
			smooth = branch.requireValue(PRVI25_SeisSmoothingAlgorithms.class);
		
		// double hardcode mMax
		double mMax = 7.99;
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(mMax);
		if (region != null)
			return PRVI25_RegionalSeismicity.getRemapped(region, seisRegion, declustering, smooth, refMFD, mMax);
		else
			return PRVI25_RegionalSeismicity.getBounded(seisRegion, refMFD, mMax);
	}

	@Override
	public RupSetDeformationModel getDefaultDeformationModel() {
		return PRVI25_CrustalDeformationModels.GEOLOGIC;
	}

}
