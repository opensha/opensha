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
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSectionUtils;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_RegionalSeismicity;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_SeisSmoothingAlgorithms;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class NSHM23_RegionLoader {
	
	/**
	 * Regions within which seismicity constraints and spatial seismicity PDFs are determined
	 * 
	 * @author kevin
	 * @see NSHM23_RegionalSeismicity
	 * @see NSHM23_SeisSmoothingAlgorithms
	 * 
	 */
	public enum SeismicityRegions implements BaseRegion {
		ALASKA("alaska.geojson"),
		CONUS_EAST("conus-east.geojson"),
		CONUS_IMW("conus-intermountain-west.geojson"),
		CONUS_PNW("conus-pacific-northwest.geojson"),
		CONUS_U3_RELM("conus-ucerf3-relm.geojson"),
		CONUS_HAWAII("hawaii.geojson");
		
		private String fileName;

		private SeismicityRegions(String fileName) {
			this.fileName = fileName;
		}
		
		public String getResourcePath() {
			return REG_PATH+"/"+fileName;
		}
	}
	
	/**
	 * Location regions used for analysis purposes only
	 * 
	 * @author kevin
	 *
	 */
	public enum LocalRegions implements BaseRegion {
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
	public enum StitchedRegions implements BaseRegion {
		CONUS("conus.geojson"),
		CONUS_WEST("conus-west.geojson");
		
		private String fileName;

		private StitchedRegions(String fileName) {
			this.fileName = fileName;
		}
		
		public String getResourcePath() {
			return STITCHED_REG_PATH+"/"+fileName;
		}
	}
	
	private static final String NSHM23_REG_PATH_PREFIX = "/data/erf/nshm23/seismicity/regions";
	private static final String REG_PATH = NSHM23_REG_PATH_PREFIX+"/nshm-regions";
	private static final String LOCAL_REG_PATH = NSHM23_REG_PATH_PREFIX+"/nshm-regions-local";
	private static final String STITCHED_REG_PATH = NSHM23_REG_PATH_PREFIX+"/nshm-regions-stitched";
	
	public static List<Region> loadPrimaryRegions() throws IOException {
		return loadPrimaryRegions(null);
	}
	
	public static List<Region> loadPrimaryRegions(List<? extends FaultSection> subSects) throws IOException {
		return doLoadRegions(SeismicityRegions.values(), subSects);
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
		ret.addAll(loadPrimaryRegions(subSects));
		ret.addAll(loadLocalRegions(subSects));
		return ret;
	}
	
	private static List<Region> doLoadRegions(BaseRegion[] regions, List<? extends FaultSection> subSects) throws IOException {
		List<Region> ret = new ArrayList<>();
		for (BaseRegion baseReg : regions) {
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
	
	private static Map<BaseRegion, Region> regionCache = new ConcurrentHashMap<>();
	
	interface BaseRegion {
		
		public String getResourcePath();
		
		public default Region load() throws IOException {
			Region cached = regionCache.get(this);
			if (cached != null)
				return cached;
			String path = getResourcePath();
			System.out.println("Reading "+path);
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					NSHM23_RegionLoader.class.getResourceAsStream(path)));
			
			FeatureCollection features = FeatureCollection.read(reader);
			Preconditions.checkState(features.features.size() == 1, "Expected 1 feature in collection");
			Feature feature = features.features.get(0);
			
			reader.close();
			
			Region reg = Region.fromFeature(feature);
			
			if (feature.id == null && feature.properties != null && feature.properties.containsKey("title")) {
				Object title = feature.properties.get("title");
				if (title instanceof String && !((String)title).isBlank())
					reg.setName((String)title);
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
		List<Region> regions = loadPrimaryRegions();
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
			if (reg != SeismicityRegions.ALASKA && reg != SeismicityRegions.CONUS_HAWAII) {
				Feature feature = reg.load().toFeature();
//				feature = new Feature(reg., null, null)
				featureList.add(feature);
			}
		}
		FeatureCollection features = new FeatureCollection(featureList);
		FeatureCollection.write(features, new File("/tmp/nshm23_conus_seismicity_regions.geojson"));
		String url = "https://opensha.usc.edu/ftp/kmilner/nshm23/misc_maps/nshm23_conus_seismicity_regions.geojson";
		System.out.print(RupSetMapMaker.getGeoJSONViewerLink(url));
	}

}
