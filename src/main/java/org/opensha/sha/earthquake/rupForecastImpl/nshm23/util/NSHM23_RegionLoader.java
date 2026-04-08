package org.opensha.sha.earthquake.rupForecastImpl.nshm23.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.GeoJSON_Type;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_RegionalSeismicity;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SeisSmoothingAlgorithms;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class NSHM23_RegionLoader {
	
	private static final String NSHM23_REG_PATH_PREFIX = "/data/erf/nshm23/seismicity/regions";
	private static final String ANALYSIS_REG_PATH = NSHM23_REG_PATH_PREFIX+"/analysis";
	private static final String SEIS_REG_PATH = NSHM23_REG_PATH_PREFIX+"/collection";
	private static final String LOCAL_REG_PATH = NSHM23_REG_PATH_PREFIX+"/local";
	private static final String STITCHED_REG_PATH = NSHM23_REG_PATH_PREFIX+"/stitched";
	private static final String FAULT_STYLE_REG_PATH = NSHM23_REG_PATH_PREFIX+"/fault-style";
	private static final String GRID_SYSTEM_REG_PATH = NSHM23_REG_PATH_PREFIX+"/grid-system";
	
	private static String SEIS_REG_SUFFIX = "";
	
	public synchronized static void setSeismicityRegionVersion(int version) {
		SEIS_REG_SUFFIX = "-v"+version;
		regionCache.remove(SeismicityRegions.CONUS_WEST);
		regionCache.remove(SeismicityRegions.CONUS_EAST);
	}
	
	/**
	 * Regions within which seismicity constraints and spatial seismicity PDFs are determined
	 * 
	 * @author kevin
	 * @see NSHM23_RegionalSeismicity
	 * @see NSHM23_SeisSmoothingAlgorithms
	 * 
	 */
	public enum SeismicityRegions implements NSHM23_BaseRegion {
		CONUS_EAST("collection-ceus"+SEIS_REG_SUFFIX+".geojson"),
		CONUS_WEST("collection-wus"+SEIS_REG_SUFFIX+".geojson");
		
		private String fileName;

		private SeismicityRegions(String fileName) {
			this.fileName = fileName;
		}
		
		public String getResourcePath() {
			return SEIS_REG_PATH+"/"+fileName;
		}
	}
	
	/**
	 * Large regions for analysis
	 * 
	 * @author kevin
	 * @see NSHM23_RegionalSeismicity
	 * @see NSHM23_SeisSmoothingAlgorithms
	 * 
	 */
	public enum AnalysisRegions implements NSHM23_BaseRegion {
		ALASKA("alaska.geojson"),
		CONUS_EAST("ceus.geojson"),
		CONUS_IMW("intermountain-west.geojson"),
//		CONUS_IMW_ACTIVE("intermountain-west-active.geojson"),
//		CONUS_IMW_STABLE("intermountain-west-stable.geojson"),
		CONUS_PNW("pacific-northwest.geojson"),
		CONUS_U3_RELM("ucerf.geojson");
//		CONUS_HAWAII("hawaii.geojson");
		
		private String fileName;

		private AnalysisRegions(String fileName) {
			this.fileName = fileName;
		}
		
		public String getResourcePath() {
			return ANALYSIS_REG_PATH+"/"+fileName;
		}
	}
	
	/**
	 * Location regions used for analysis purposes only
	 * 
	 * @author kevin
	 *
	 */
	public enum LocalRegions implements NSHM23_BaseRegion {
		CONUS_LA_BASIN("conus-la-basin.geojson"),
		CONUS_NEW_MADRID("conus-new-madrid.geojson"),
		CONUS_PUGET("conus-puget.geojson"),
		CONUS_SF_BAY("conus-sf-bay.geojson"),
		CONUS_WASATCH("conus-wasatch.geojson");
		
		private String fileName;

		private LocalRegions(String fileName) {
			this.fileName = fileName;
		}
		
		public String getResourcePath() {
			return LOCAL_REG_PATH+"/"+fileName;
		}
	}
	
	/**
	 * Larger model regions that span multiple {@link SeismicityRegions}
	 * 
	 * @author kevin
	 *
	 */
	public enum StitchedRegions implements NSHM23_BaseRegion {
		CONUS("conus.geojson"),
		CONUS_WEST("conus-wus.geojson");
		
		private String fileName;

		private StitchedRegions(String fileName) {
			this.fileName = fileName;
		}
		
		public String getResourcePath() {
			return STITCHED_REG_PATH+"/"+fileName;
		}
	}
	
	/**
	 * Larger model regions that span multiple {@link SeismicityRegions}
	 * 
	 * @author kevin
	 *
	 */
	public enum FaultStyleRegions implements NSHM23_BaseRegion {
		CEUS_STABLE("focal-mech-ceus-stable.geojson"),
		WUS_COMPRESSIONAL("focal-mech-wus-compressional.geojson"),
		WUS_EXTENSIONAL("focal-mech-wus-extensional.geojson");
		
		private String fileName;

		private FaultStyleRegions(String fileName) {
			this.fileName = fileName;
		}
		
		public String getResourcePath() {
			return FAULT_STYLE_REG_PATH+"/"+fileName;
		}
	}
	
	/**
	 * Regions that are used for determining active vs stable faulting
	 * 
	 * @author kevin
	 *
	 */
	public enum GridSystemRegions implements NSHM23_BaseRegion {
		WUS_ACTIVE("grid-system-active.geojson"),
		CEUS_STABLE("grid-system-stable.geojson");
		
		private String fileName;

		private GridSystemRegions(String fileName) {
			this.fileName = fileName;
		}
		
		public String getResourcePath() {
			return GRID_SYSTEM_REG_PATH+"/"+fileName;
		}
	}
	
	public static List<Region> loadAnalysisRegions() throws IOException {
		return loadAnalysisRegions(null);
	}
	
	public static List<Region> loadAnalysisRegions(List<? extends FaultSection> subSects) throws IOException {
		return doLoadRegions(AnalysisRegions.values(), subSects);
	}
	
	public static List<Region> loadLocalRegions() throws IOException {
		return loadLocalRegions(null);
	}
	
	public static List<Region> loadLocalRegions(List<? extends FaultSection> subSects) throws IOException {
		return doLoadRegions(LocalRegions.values(), subSects);
	}
	
	public static List<Region> loadAllRegions() throws IOException {
		return loadAllRegions(null);
	}
	
	public static List<Region> loadAllRegions(List<? extends FaultSection> subSects) throws IOException {
		List<Region> ret = new ArrayList<>();
		ret.addAll(loadAnalysisRegions(subSects));
		ret.addAll(loadLocalRegions(subSects));
		return ret;
	}
	
	private static List<Region> doLoadRegions(NSHM23_BaseRegion[] regions, List<? extends FaultSection> subSects) throws IOException {
		List<Region> ret = new ArrayList<>();
		for (NSHM23_BaseRegion baseReg : regions) {
			Region region = baseReg.load();
			if (subSects != null && !FaultSectionUtils.anySectInRegion(region, subSects, true))
				// skip this region
				continue;
			ret.add(region);
		}
		return ret;
	}
	
	public static Region loadFullConterminousWUS() throws IOException {
		return StitchedRegions.CONUS_WEST.load();
	}
	
	public static Region loadFullConterminousUS() throws IOException {
		return StitchedRegions.CONUS.load();
	}
	
	private static Map<NSHM23_BaseRegion, Region> regionCache = new ConcurrentHashMap<>();
	
	public static NSHM23_BaseRegion CATCH_ALL_REGION = new NSHM23_BaseRegion() {
		
		Region region;
		
		@Override
		public String name() {
			return "FULL";
		}
		
		@Override
		public String getResourcePath() {
			return null;
		}
		
		public synchronized Region load() throws IOException {
			if (region == null) {
				region = new Region(new Location(10d, -180d), new Location(80d, 0d));
				region.setName("Full");
			}
			return region;
		}
	};
	
	public interface NSHM23_BaseRegion {
		
		public String getResourcePath();
		
		public String name();
		
		public default Region load() throws IOException {
			Region cached = regionCache.get(this);
			if (cached != null)
				return cached;
			String path = getResourcePath();
			System.out.println("Reading "+path);
			
			// could be either a Feature or FeatureCollection (with 1 feature)
			GeoJSON_Type type = GeoJSON_Type.detect(new BufferedReader(new InputStreamReader(
					NSHM23_RegionLoader.class.getResourceAsStream(path))));
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					NSHM23_RegionLoader.class.getResourceAsStream(path)));
			
			Feature feature;
			if (type == GeoJSON_Type.Feature) {
				feature = Feature.read(reader);
			} else if (type == GeoJSON_Type.FeatureCollection) {
				FeatureCollection features = FeatureCollection.read(reader);
				Preconditions.checkState(features.features.size() == 1, "Expected 1 feature in collection");
				feature = features.features.get(0);
			} else {
				throw new IllegalStateException("Expected Feature or FeatureCollection, have "+type);
			}
			
			reader.close();
			
			Region reg = Region.fromFeature(feature);
			
			if (feature.id == null && feature.properties != null) {
				if (feature.properties.containsKey("title")) {
					Object title = feature.properties.get("title");
					if (title instanceof String && !((String)title).isBlank())
						reg.setName((String)title);
				} else if (feature.properties.containsKey("name")) {
					Object name = feature.properties.get("name");
					if (name instanceof String && !((String)name).isBlank())
						reg.setName((String)name);
				}
			}
			
			synchronized (regionCache) {
				regionCache.putIfAbsent(this, reg);
			}
			return reg;
		}
	}
	
	public static void main(String[] args) throws IOException {
//		List<? extends FaultSection> sects = null;
//		List<? extends FaultSection> sects = GeoJSONFaultReader.readFaultSections(new File("/tmp/GEOLOGIC_sub_sects.geojson"));
//		for (Region reg : loadAllRegions(sects))
//			System.out.println(reg.getName());
		loadFullConterminousUS();
		loadFullConterminousWUS();
		
		double gridSpacing = 0.1;
		GriddedRegion fullGriddedWUS = new GriddedRegion(loadFullConterminousWUS(), gridSpacing, GriddedRegion.ANCHOR_0_0);
		int numMapped = 0;
		int numMultiplyMapped = 0;
		List<Region> regions = loadAnalysisRegions();
		for (int i=0; i<fullGriddedWUS.getNodeCount(); i++) {
			Location loc = fullGriddedWUS.getLocation(i);
			int matches = 0;
			for (Region region : regions)
				if (region.contains(loc))
					matches++;
			if (matches > 0)
				numMapped++;
			if (matches > 1)
				numMultiplyMapped++;
		}
		
		System.out.println(numMapped+"/"+fullGriddedWUS.getNodeCount()+" grid nodes are mapped to at least 1 sub-region");
		System.out.println(numMultiplyMapped+"/"+fullGriddedWUS.getNodeCount()+" grid nodes are mapped to multiple sub-regions");
		int countAcrossIndv = 0;
		for (Region region : regions)
			countAcrossIndv += new GriddedRegion(region, gridSpacing, GriddedRegion.ANCHOR_0_0).getNodeCount();
		System.out.println(countAcrossIndv+" vs "+fullGriddedWUS.getNodeCount()+" if you sum individual gridded regions");
		
		// write out CONUS regions to single geo json
		List<Feature> featureList = new ArrayList<>();
		for (SeismicityRegions reg : SeismicityRegions.values()) {
//			if (reg != SeismicityRegions.ALASKA && reg != SeismicityRegions.CONUS_HAWAII) {
				Feature feature = reg.load().toFeature();
//				feature = new Feature(reg., null, null)
				featureList.add(feature);
//			}
		}
		FeatureCollection features = new FeatureCollection(featureList);
		FeatureCollection.write(features, new File("/tmp/nshm23_conus_seismicity_regions.geojson"));
		String url = "https://opensha.usc.edu/ftp/kmilner/nshm23/misc_maps/nshm23_conus_seismicity_regions.geojson";
		System.out.print(RupSetMapMaker.getGeoJSONViewerLink(url));
	}

}
