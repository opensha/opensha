package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.modules.RegionsOfInterest;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.PrimaryRegions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM23_FaultModels implements LogicTreeNode, RupSetFaultModel {
	
	NSHM23_v1p4("NSHM23 WUS Fault Model v1.4", "NSHM23 WUS v1.4", NSHM23_DeformationModels.GEOLOGIC, 1d) {
		@Override
		protected List<? extends FaultSection> loadFaultSections() throws IOException {
			String sectPath = NSHM23_SECTS_PATH_PREFIX+"v1p4/NSHM23_FaultSections_v1p4.geojson";
			Reader sectsReader = new BufferedReader(new InputStreamReader(
					GeoJSONFaultReader.class.getResourceAsStream(sectPath)));
			Preconditions.checkNotNull(sectsReader, "Fault model file not found: %s", sectPath);
			return GeoJSONFaultReader.readFaultSections(sectsReader);
		}
	},
	NSHM23_v2("NSHM23 WUS Fault Model v2", "NSHM23 WUS v2", NSHM23_DeformationModels.GEOLOGIC, 0d) {
		@Override
		protected List<? extends FaultSection> loadFaultSections() throws IOException {
			String sectPath = NSHM23_SECTS_PATH_PREFIX+"v2/NSHM23_FSD_v2.geojson";
			Reader sectsReader = new BufferedReader(new InputStreamReader(
					GeoJSONFaultReader.class.getResourceAsStream(sectPath)));
			Preconditions.checkNotNull(sectsReader, "Fault model file not found: %s", sectPath);
			return GeoJSONFaultReader.readFaultSections(sectsReader);
		}
	};;
	
	private static final ConcurrentMap<NSHM23_FaultModels, List<? extends FaultSection>> sectsCache = new ConcurrentHashMap<>();
	
	public static final String NSHM23_SECTS_PATH_PREFIX = "/data/erf/nshm23/fault_models/";
	
	private String name;
	private String shortName;
	private RupSetDeformationModel defaultDM;
	private double weight;

	private NSHM23_FaultModels(String name, String shortName, RupSetDeformationModel defaultDM, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.defaultDM = defaultDM;
		this.weight = weight;
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
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return name();
	}

	@Override
	public final List<? extends FaultSection> getFaultSections() throws IOException {
		List<? extends FaultSection> sects = sectsCache.get(this);
		if (sects == null) {
			synchronized (sectsCache) {
				sects = sectsCache.get(this);
				if (sects == null) {
					sects = loadFaultSections();
					sectsCache.put(this, sects);
				}
			}
		}
		// now return a copy
		List<FaultSection> copy = new ArrayList<>();
		for (FaultSection sect : sects)
			copy.add(sect.clone());
		return copy;
	}
	
	protected abstract List<? extends FaultSection> loadFaultSections() throws IOException;

	@Override
	public RupSetDeformationModel getDefaultDeformationModel() {
		return defaultDM;
	}

	@Override
	public void attachDefaultModules(FaultSystemRupSet rupSet) {
		rupSet.addAvailableModule(new Callable<NamedFaults>() {

			@Override
			public NamedFaults call() throws Exception {
				// load geologic sections that have named fault associations in the parent name field
				Map<Integer, GeoJSONFaultSection> geoSects =
						NSHM23_DeformationModels.getGeolFullSects(NSHM23_FaultModels.this);
				
				// find unique set of parent IDs that we have
				HashSet<Integer> myParentIDs = new HashSet<>();
				for (FaultSection sect : rupSet.getFaultSectionDataList())
					myParentIDs.add(sect.getParentSectionId());
				
				Map<String, List<Integer>> namedFaults = new HashMap<>();
				
				for (Integer parentID : myParentIDs) {
					GeoJSONFaultSection sect = geoSects.get(parentID);
					Preconditions.checkNotNull(sect, "Couldn't find geologic full section for parent=%s", parentID);
					if (sect.getParentSectionName() != null) {
						List<Integer> sectIDs = namedFaults.get(sect.getParentSectionName());
						if (sectIDs == null) {
							sectIDs = new ArrayList<>();
							namedFaults.put(sect.getParentSectionName(), sectIDs);
						}
						sectIDs.add(parentID);
					}
				}
				
				Preconditions.checkState(!namedFaults.isEmpty(), "No named faults found");
				return new NamedFaults(rupSet, namedFaults);
			}
		}, NamedFaults.class);
		rupSet.addAvailableModule(new Callable<RegionsOfInterest>() {

			@Override
			public RegionsOfInterest call() throws Exception {
				// TODO get observed MFDs from a better place, don't hardcode here
				double refMag = 5d;
				double deltaMag = 0.1;
				
				List<Region> regions = new ArrayList<>();
				List<IncrementalMagFreqDist> regionMFDs = new ArrayList<>();
				List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
				for (PrimaryRegions pReg : PrimaryRegions.values()) {
					// preliminary values from Andrea/Ned via e-mail, 7/22/2022, subject "very preliminary b-values"
					// TODO: are they 95% confidence?
					
					Region region = pReg.load();
					if (!FaultSectionUtils.anySectInRegion(region, subSects, true))
						continue;
					
					Double b=null, pref=null, low=null, high=null;
					
					if (pReg == PrimaryRegions.CONUS_U3_RELM) {
						b = 1d;
						pref = 6.07;
						low = 3.61;
						high = 8.53;
					} else if (pReg == PrimaryRegions.CONUS_PNW) {
						b = 1.1d;
						pref = 0.38;
						low = 0.03;
						high = 1d;
					} else if (pReg == PrimaryRegions.CONUS_IMW) {
						b = 1.2d;
						pref = 0.73;
						low = 0.08;
						high = 1.58;
					} else if (pReg == PrimaryRegions.CONUS_EAST) {
						b = 1.2d;
						pref = 0.18;
						low = 0.02;
						high = 0.6;
					}
					
					if (b != null) {
						// find max mag in region
						double maxMagInRegion = 7d;
						double[] rupFracts = rupSet.getFractRupsInsideRegion(region, true);
						for (int r=0; r<rupFracts.length; r++)
							if (rupFracts[r] > 0d)
								maxMagInRegion = Math.max(maxMagInRegion, rupSet.getMagForRup(r));
						EvenlyDiscretizedFunc refMFD = new EvenlyDiscretizedFunc(
								refMag+0.5*deltaMag, (int)(5d/deltaMag+0.5), deltaMag);
						// now trim to size
						int maxMagIndex = refMFD.getClosestXIndex(maxMagInRegion);
						refMFD = new EvenlyDiscretizedFunc(refMFD.getMinX(), maxMagIndex+1, deltaMag);
						regionMFDs.add(getUncertGR(refMFD, b, pref, low, high, UncertaintyBoundType.CONF_95));
					} else {
						regionMFDs.add(null);
					}
					
					regions.add(region);
				}
				for (Region region : NSHM23_RegionLoader.loadLocalRegions(subSects)) {
					regions.add(region);
					regionMFDs.add(null);
				}
				return new RegionsOfInterest(regions, regionMFDs);
			}
		}, RegionsOfInterest.class);
	}
	
	private static final DecimalFormat oDF = new DecimalFormat("0.##");
	
	private static UncertainBoundedIncrMagFreqDist getUncertGR(EvenlyDiscretizedFunc refMFD, double b,
			double prefRate, double lowerRate, double upperRate, UncertaintyBoundType type) {
		GutenbergRichterMagFreqDist prefGR = new GutenbergRichterMagFreqDist(
				refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		GutenbergRichterMagFreqDist lowGR = new GutenbergRichterMagFreqDist(
				refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		GutenbergRichterMagFreqDist highGR = new GutenbergRichterMagFreqDist(
				refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
		
		double roundedMinMag = refMFD.getX(0);
		double roundedMaxMag = refMFD.getX(refMFD.size()-1);
		
		prefGR.setAllButTotMoRate(roundedMinMag, roundedMaxMag, prefRate, b);
		lowGR.setAllButTotMoRate(roundedMinMag, roundedMaxMag, lowerRate, b);
		highGR.setAllButTotMoRate(roundedMinMag, roundedMaxMag, upperRate, b);
		
		UncertainBoundedIncrMagFreqDist ret = new UncertainBoundedIncrMagFreqDist(prefGR, lowGR, highGR, type);
		ret.setName("Total Observed [b="+oDF.format(b)+", N5="+oDF.format(prefRate)+"]");
		return ret;
	}

}
