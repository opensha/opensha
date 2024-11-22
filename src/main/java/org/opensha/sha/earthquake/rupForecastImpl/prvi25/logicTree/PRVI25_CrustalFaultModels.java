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
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.FaultUtils;
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
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded.PRVI25_GridSourceBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader.PRVI25_SeismicityRegions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

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
	
	/**
	 * if true, then slip rates are vertical and need to be projected onto the plane
	 */
	public static final boolean PROJECT_TO_PLANE = true;
	
	public static final String HIGH_RATE_PROP_NAME = "HighRate";
	public static final String LOW_RATE_PROP_NAME = "LowRate";

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
		List<GeoJSONFaultSection> sects = GeoJSONFaultReader.readFaultSections(reader);
		
		if (PROJECT_TO_PLANE) {
			// slip rates need to be projected
			for (GeoJSONFaultSection sect : sects) {
				sect.setAveSlipRate(projectSlip(sect.getOrigAveSlipRate(), sect.getAveDip(), sect.getAveRake()));
				FeatureProperties props = sect.getProperties();
				props.set(HIGH_RATE_PROP_NAME, projectSlip(
						props.getDouble(HIGH_RATE_PROP_NAME, Double.NaN), sect.getAveDip(), sect.getAveRake()));
				props.set(LOW_RATE_PROP_NAME, projectSlip(
						props.getDouble(LOW_RATE_PROP_NAME, Double.NaN), sect.getAveDip(), sect.getAveRake()));
			}
		}
		
		return sects;
	}
	
	public static double projectSlip(double origSlipRate, double dip, double rake) {
		FaultUtils.assertValidRake(rake); // -180 to 180
		double absRake = Math.abs(rake);
		boolean oblique = (float)absRake != (float)180
				&& (float)absRake != (float)90
				&& (float)absRake != (float)0;
		// is this closer to SS or thrust? if exactly 45 degrees (or less) from thrust, assume we have a vertical
		// rate that needs to be projected down dip
		boolean origIsHorizontal = (float)absRake > 135f || (float)absRake < 45f;
		double slipRate = origSlipRate;
		if (dip != 90d && !origIsHorizontal)
			// we have a vertical slip rate that needs to be projected down dip
			slipRate /= Math.sin(Math.toRadians(dip));
		if (oblique) {
			// we have oblique slip
			// if it's closer to thrust, we'll assume that we have vertical slip rates and we need to add the horizontal component
			// if it's closer to SS, we'll assume that we have horizontal slip rates and we need to add the vertical component
			double obliqueAngle;
			if (origIsHorizontal)
				// difference between the rake and pure SS
				obliqueAngle = Math.min(absRake, Math.abs(absRake - 180));
			else
				// difference between the rake and pure thrust
				obliqueAngle = Math.abs(absRake - 90);
			Preconditions.checkState((float)obliqueAngle <= 45f,
					"Oblique angle should never be >45: %s", (float)obliqueAngle);
			if (obliqueAngle > 0d)
				slipRate /= Math.cos(Math.toRadians(obliqueAngle));
		}
		return slipRate;
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
				List<TectonicRegionType> regionTRTs = new ArrayList<>();
				List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
				
				// overall seismicity regions
//				for (SeismicityRegions seisReg : SeismicityRegions.values()) {
				for (PRVI25_SeismicityRegions seisReg : new PRVI25_SeismicityRegions[] {PRVI25_SeismicityRegions.CRUSTAL}) {
					Region region = seisReg.load();
					if (!FaultSectionUtils.anySectInRegion(region, subSects, true))
						continue;
					
					regionMFDs.add(getRegionalMFD(seisReg, branch));
					regions.add(region);
					regionTRTs.add(TectonicRegionType.ACTIVE_SHALLOW);
				}
				
				// smaller map map region
				Region mapRegion = PRVI25_RegionLoader.loadPRVI_MapExtents();
				if (FaultSectionUtils.anySectInRegion(mapRegion, subSects, true)) {
					mapRegion.setName("PRVI - NSHMP Map Region");
					regions.add(mapRegion);
					regionMFDs.add(null);
					regionTRTs.add(null);
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
//				return new RegionsOfInterest(regions, regionMFDs);
				return new RegionsOfInterest(regions, regionMFDs, regionTRTs);
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
	
	private static UncertainBoundedIncrMagFreqDist getRegionalMFD(PRVI25_SeismicityRegions seisRegion,
			LogicTreeBranch<?> branch) throws IOException {
//		PRVI25_DeclusteringAlgorithms declustering = PRVI25_DeclusteringAlgorithms.AVERAGE;
//		if (branch != null && branch.hasValue(PRVI25_DeclusteringAlgorithms.class))
//			declustering = branch.requireValue(PRVI25_DeclusteringAlgorithms.class);
//		
//		PRVI25_SeisSmoothingAlgorithms smooth = PRVI25_SeisSmoothingAlgorithms.AVERAGE;
//		if (branch != null && branch.hasValue(PRVI25_SeisSmoothingAlgorithms.class))
//			smooth = branch.requireValue(PRVI25_SeisSmoothingAlgorithms.class);
		
		// this is just for plots, we want the "data" portion to extend past the right of the MFD plots
		double mMax = 9.01;
		EvenlyDiscretizedFunc refMFD = FaultSysTools.initEmptyMFD(PRVI25_GridSourceBuilder.OVERALL_MMIN, mMax);
//		if (region != null)
//			return PRVI25_RegionalSeismicity.getRemapped(region, seisRegion, declustering, smooth, refMFD, mMax);
//		else
		return PRVI25_RegionalSeismicity.getBounded(seisRegion, refMFD, mMax);
	}

	@Override
	public RupSetDeformationModel getDefaultDeformationModel() {
		return PRVI25_CrustalDeformationModels.GEOLOGIC;
	}

}
