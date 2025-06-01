package org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
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
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SubSectionBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.gridded.PRVI25_GridSourceBuilder;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader.PRVI25_SeismicityRegions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
@DoesNotAffect(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME)
@DoesNotAffect(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME)
@Affects(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME)
public enum PRVI25_SubductionFaultModels implements RupSetFaultModel, RupSetSubsectioningModel {
	PRVI_SUB_FM_LARGE("Subduction FM, Large", "Large", 0.5d),
	PRVI_SUB_FM_SMALL("Subduction FM, Small", "Small", 0.5d);
	
	private static final String VERSION = "v4";
	private static final String PREFIX = "/data/erf/prvi25/fault_models/subduction/"+VERSION+"/";
	
	private String name;
	private String shortName;
	private String jsonPath;
	private double weight;

	private PRVI25_SubductionFaultModels(String name, String shortName,  double weight) {
		this.name = name;
		this.shortName = shortName;
		this.jsonPath = PREFIX+"PRVI_sub_"+VERSION+"_fault_model_"+name()+".geojson";
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
		BufferedReader reader = new BufferedReader(new InputStreamReader(PRVI25_SubductionFaultModels.class.getResourceAsStream(jsonPath)));
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
		rupSet.addAvailableModule(new Callable<RupSetTectonicRegimes>() {

			@Override
			public RupSetTectonicRegimes call() throws Exception {
				TectonicRegionType[] regimes = new TectonicRegionType[rupSet.getNumRuptures()];
				for (int r=0; r<regimes.length; r++)
					regimes[r] = TectonicRegionType.SUBDUCTION_INTERFACE;
				return new RupSetTectonicRegimes(rupSet, regimes);
			}
		}, RupSetTectonicRegimes.class);
		// TODO: named faults?
		rupSet.addAvailableModule(new Callable<RegionsOfInterest>() {

			@Override
			public RegionsOfInterest call() throws Exception {
				List<Region> regions = new ArrayList<>();
				List<IncrementalMagFreqDist> regionMFDs = new ArrayList<>();
				List<TectonicRegionType> regionTRTs = new ArrayList<>();
				
				// overall seismicity regions
				
				regions.add(PRVI25_RegionLoader.loadPRVI_ModelBroad());
				regionMFDs.add(null);
				regionTRTs.add(null);
				
				// smaller map map region
				Region mapRegion = PRVI25_RegionLoader.loadPRVI_MapExtents();
				mapRegion.setName("PRVI - NSHMP Map Region");
				regions.add(mapRegion);
				regionMFDs.add(null);
				regionTRTs.add(null);
				
				PRVI25_SeismicityRegions[] interfaceRegions = {
						PRVI25_SeismicityRegions.CAR_INTERFACE,
						PRVI25_SeismicityRegions.MUE_INTERFACE,
				};
//				double maxMinMag = 0d;
//				for (int s=0; s<rupSet.getNumSections(); s++)
//					maxMinMag = Math.max(maxMinMag, rupSet.getMinMagForSection(s));
				// this is just for plots, we want the "data" portion to extend past the right of the rupture data
				double mMax = 0d;
				for (int s=0; s<rupSet.getNumSections(); s++)
					mMax = Math.max(mMax, rupSet.getMaxMagForSection(s));
				IncrementalMagFreqDist interfaceRefMFD = FaultSysTools.initEmptyMFD(PRVI25_GridSourceBuilder.OVERALL_MMIN, mMax+0.1);
				for (PRVI25_SeismicityRegions seisReg : interfaceRegions) {
					if (seisReg == PRVI25_SeismicityRegions.MUE_INTERFACE && PRVI25_GridSourceBuilder.MUERTOS_AS_CRUSTAL)
						continue;
					List<Double> minMags = new ArrayList<>();
					List<Double> maxMags = new ArrayList<>();
					Region reg = seisReg.load();
					for (FaultSection sect : rupSet.getFaultSectionDataList()) {
						boolean contained = false;
						for (Location loc : sect.getFaultSurface(10d).getPerimeter()) {
							if (reg.contains(loc)) {
								contained = true;
								break;
							}
						}
						if (contained) {
							minMags.add(rupSet.getMinMagForSection(sect.getSectionId()));
							maxMags.add(rupSet.getMaxMagForSection(sect.getSectionId()));
						}
					}
					Preconditions.checkState(!minMags.isEmpty());
//					double avgMinMag = minMags.stream().mapToDouble(D->D).average().getAsDouble();
					double dataMmax = maxMags.stream().mapToDouble(D->D).max().getAsDouble();
					regions.add(reg);
					if (seisReg == PRVI25_SeismicityRegions.CAR_INTERFACE)
						regionMFDs.add(PRVI25_SubductionCaribbeanSeismicityRate.loadRateModel(false).getBounded(
								interfaceRefMFD, interfaceRefMFD.getX(interfaceRefMFD.getClosestXIndex(dataMmax))));
					else
						regionMFDs.add(PRVI25_SubductionMuertosSeismicityRate.loadRateModel(false).getBounded(
								interfaceRefMFD, interfaceRefMFD.getX(interfaceRefMFD.getClosestXIndex(dataMmax))));
					regionTRTs.add(TectonicRegionType.SUBDUCTION_INTERFACE);
				}
				
				PRVI25_SeismicityRegions[] slabRegions = {
						PRVI25_SeismicityRegions.CAR_INTRASLAB,
						PRVI25_SeismicityRegions.MUE_INTRASLAB,
				};
				IncrementalMagFreqDist slabRefMFD = FaultSysTools.initEmptyMFD(PRVI25_GridSourceBuilder.OVERALL_MMIN, PRVI25_GridSourceBuilder.SLAB_MMAX);
				for (PRVI25_SeismicityRegions seisReg : slabRegions) {
					regions.add(seisReg.load());
					UncertainBoundedIncrMagFreqDist mfd;
					if (seisReg == PRVI25_SeismicityRegions.CAR_INTRASLAB)
						mfd = PRVI25_SubductionCaribbeanSeismicityRate.loadRateModel(true).getBounded(
								slabRefMFD, slabRefMFD.getX(slabRefMFD.getClosestXIndex(PRVI25_GridSourceBuilder.SLAB_MMAX)));
					else
						mfd = PRVI25_SubductionMuertosSeismicityRate.loadRateModel(true).getBounded(
								slabRefMFD, slabRefMFD.getX(slabRefMFD.getClosestXIndex(PRVI25_GridSourceBuilder.SLAB_MMAX)));
//					System.out.println("MFD for "+seisReg
//							+"; lowM5="+(float)mfd.getLower().getCumRateDistWithOffset().getY(5d)
//							+"; prefM5="+(float)mfd.getCumRateDistWithOffset().getY(5d)
//							+"; highM5="+(float)mfd.getUpper().getCumRateDistWithOffset().getY(5d));
					regionMFDs.add(mfd);
					regionTRTs.add(TectonicRegionType.SUBDUCTION_SLAB);
				}
				return new RegionsOfInterest(regions, regionMFDs, regionTRTs);
			}
		}, RegionsOfInterest.class);
		
	}

	@Override
	public RupSetDeformationModel getDefaultDeformationModel() {
		return PRVI25_SubductionDeformationModels.FULL;
	}

	@Override
	public List<? extends FaultSection> buildSubSects(RupSetFaultModel faultModel,
			List<? extends FaultSection> fullSections) {
		return SubSectionBuilder.buildSubSects(fullSections, 2, 0.5, 30d);
	}

}
