package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.uncertainty.UncertainBoundedIncrMagFreqDist;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.modules.RegionsOfInterest;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
import org.opensha.sha.earthquake.faultSysSolution.util.MaxMagOffFaultBranchNode;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.SeismicityRegions;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.GutenbergRichterMagFreqDist;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM23_FaultModels implements LogicTreeNode, RupSetFaultModel {
	
	NSHM23_v1p4("NSHM23 WUS Fault Model v1.4", "NSHM23 WUS v1.4", NSHM23_DeformationModels.GEOLOGIC, 0d) {
		@Override
		protected List<? extends FaultSection> loadFaultSections() throws IOException {
			String sectPath = NSHM23_SECTS_PATH_PREFIX+"v1p4/NSHM23_FaultSections_v1p4.geojson";
			Reader sectsReader = new BufferedReader(new InputStreamReader(
					GeoJSONFaultReader.class.getResourceAsStream(sectPath)));
			Preconditions.checkNotNull(sectsReader, "Fault model file not found: %s", sectPath);
			return GeoJSONFaultReader.readFaultSections(sectsReader);
		}
	},
	NSHM23_v2("NSHM23 WUS Fault Model v2", "NSHM23 WUS v2", NSHM23_DeformationModels.GEOLOGIC, 1d) {
		@Override
		protected List<? extends FaultSection> loadFaultSections() throws IOException {
			String sectPath = NSHM23_SECTS_PATH_PREFIX+"v2/NSHM23_FSD_v2.geojson";
			Reader sectsReader = new BufferedReader(new InputStreamReader(
					GeoJSONFaultReader.class.getResourceAsStream(sectPath)));
			Preconditions.checkNotNull(sectsReader, "Fault model file not found: %s", sectPath);
			return GeoJSONFaultReader.readFaultSections(sectsReader);
		}
	};
	
	private static final ConcurrentMap<NSHM23_FaultModels, List<? extends FaultSection>> sectsCache = new ConcurrentHashMap<>();
	
	public static final String NSHM23_SECTS_PATH_PREFIX = "/data/erf/nshm23/fault_models/";
	
	public static final String NAMED_FAULTS_FILE = NSHM23_SECTS_PATH_PREFIX+"v2/special_faults.json";
	
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
	
	public static ModelRegion getDefaultRegion(LogicTreeBranch<?> branch) throws IOException {
		if (branch != null && branch.hasValue(NSHM23_SingleStates.class)) {
			NSHM23_SingleStates state = branch.getValue(NSHM23_SingleStates.class);
			if (state == NSHM23_SingleStates.CA) {
				return new ModelRegion(SeismicityRegions.CONUS_U3_RELM.load());
			} else if (state == null) {
				return new ModelRegion(NSHM23_RegionLoader.loadFullConterminousWUS());
			} else {
				XY_DataSet[] outlines = PoliticalBoundariesData.loadUSState(state.getStateName());
				Region[] regions = new Region[outlines.length];
				for (int i=0; i<outlines.length; i++) {
					XY_DataSet outline = outlines[i];
					LocationList border = new LocationList();
					for (Point2D pt : outline)
						border.add(new Location(pt.getY(), pt.getX()));
					regions[i] = new Region(border, BorderType.MERCATOR_LINEAR);
				}
				Region largest = null;
				if (outlines.length == 1) {
					largest = regions[0];
				} else {
					double largestArea = 0;
					for (Region region : regions) {
						double area = region.getExtent();
						if (area > largestArea) {
							largestArea = area;
							largest = region;
						}
					}
				}
				return new ModelRegion(largest);
			}
		} else {
			// no single state, full WUS
			return new ModelRegion(NSHM23_RegionLoader.loadFullConterminousWUS());
		}
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
		rupSet.addAvailableModule(new Callable<NamedFaults>() {

			@Override
			public NamedFaults call() throws Exception {
				Gson gson = new GsonBuilder().create();
				
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(NSHM23_FaultModels.class.getResourceAsStream(NAMED_FAULTS_FILE)));
				Type type = TypeToken.getParameterized(Map.class, String.class,
						TypeToken.getParameterized(List.class, Integer.class).getType()).getType();
				Map<String, List<Integer>> namedFaults = gson.fromJson(reader, type);
				
				Preconditions.checkState(!namedFaults.isEmpty(), "No named faults found");
				return new NamedFaults(rupSet, namedFaults);
			}
		}, NamedFaults.class);
		rupSet.addAvailableModule(new Callable<RegionsOfInterest>() {

			@Override
			public RegionsOfInterest call() throws Exception {
				List<Region> regions = new ArrayList<>();
				List<IncrementalMagFreqDist> regionMFDs = new ArrayList<>();
				List<? extends FaultSection> subSects = rupSet.getFaultSectionDataList();
				
				MaxMagOffFaultBranchNode offFaultMMax = null;
				if (branch != null)
					offFaultMMax = branch.getValue(MaxMagOffFaultBranchNode.class);
				for (SeismicityRegions pReg : SeismicityRegions.values()) {
					Region region = pReg.load();
					if (!FaultSectionUtils.anySectInRegion(region, subSects, true))
						continue;
					
					// TODO: revisit Mmax
					double faultSysMmax = 0d;
					double[] fracts = rupSet.getFractRupsInsideRegion(region, true);
					for (int rupIndex=0; rupIndex<fracts.length; rupIndex++)
						if (fracts[rupIndex] > 0)
							faultSysMmax = Math.max(faultSysMmax, rupSet.getMagForRup(rupIndex));
					
					double mMax = faultSysMmax;
					if (offFaultMMax != null)
						mMax = Math.max(offFaultMMax.getMaxMagOffFault(), mMax);
					EvenlyDiscretizedFunc refMFD = SupraSeisBValInversionTargetMFDs.buildRefXValues(Math.max(mMax, rupSet.getMaxMag()));
					regionMFDs.add(NSHM23_RegionalSeismicity.getBounded(pReg, refMFD, mMax));
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
