package org.opensha.sha.earthquake.rupForecastImpl.nshm23.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

public class NSHM23_RegionLoader {
	
	private static final String NSHM23_REG_PATH_PREFIX = "/data/erf/nshm23/seismicity/regions";
	private static final String REG_PATH = NSHM23_REG_PATH_PREFIX+"/nshm-regions";
	private static final String LOCAL_REG_PATH = NSHM23_REG_PATH_PREFIX+"/nshm-regions-local";
	
	public static List<Region> loadPrimaryRegions() throws IOException {
		return loadPrimaryRegions(null);
	}
	
	public static List<Region> loadPrimaryRegions(List<? extends FaultSection> subSects) throws IOException {
		return doLoadRegions(PrimaryRegions.values(), subSects);
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
			if (subSects != null) {
				boolean contains = false;
				for (FaultSection sect : subSects) {
					for (Location loc : sect.getFaultTrace()) {
						if (region.contains(loc)) {
							contains = true;
							break;
						}
					}
				}
				if (!contains)
					// skip this region
					continue;
			}
			ret.add(region);
		}
		return ret;
	}
	
	public enum PrimaryRegions implements BaseRegion {
		ALASKA("alaska.geojson"),
		CONUS_EAST("conus-east.geojson"),
		CONUS_IMW("conus-intermountain-west.geojson"),
		CONUS_PNW("conus-pacific-northwest.geojson"),
		CONUS_U3_RELM("conus-ucerf3-relm.geojson"),
		CONUS_HAWAII("hawaii.geojson");
		
		private String fileName;

		private PrimaryRegions(String fileName) {
			this.fileName = fileName;
		}
		
		public String getResourcePath() {
			return REG_PATH+"/"+fileName;
		}
	}
	
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
	
	interface BaseRegion {
		
		public String getResourcePath();
		
		public default Region load() throws IOException {
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
			
			return reg;
		}
	}
	
	public static void main(String[] args) throws IOException {
//		List<? extends FaultSection> sects = null;
		List<? extends FaultSection> sects = GeoJSONFaultReader.readFaultSections(new File("/tmp/GEOLOGIC_sub_sects.geojson"));
		for (Region reg : loadAllRegions(sects))
			System.out.println(reg.getName());
	}

}
