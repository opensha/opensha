package gov.usgs.earthquake.nshmp.erf.nshm27.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.NSHM23_BaseRegion;
import org.opensha.sha.util.TectonicRegionType;

public class NSHM27_RegionLoader {
	
	private static final String GNMI_DIR = "/data/erf/nshm27/gnmi/regions/";
	private static final String AMSAM_DIR = "/data/erf/nshm27/amsam/regions/";
	
	/**
	 * Regions within which seismicity constraints and spatial seismicity PDFs are determined
	 * 
	 * @author kevin
	 * @see NSHM23_RegionalSeismicity
	 * @see NSHM23_SeisSmoothingAlgorithms
	 * 
	 */
	public enum NSHM27_SeismicityRegions implements NSHM23_BaseRegion, ShortNamed {
		GNMI(GNMI_DIR+"gnmi-catalog-extent.geojson", "Guam & Northern Mariana Islands Seismicity", "GNMI Seismicity"),
		AMSAM(AMSAM_DIR+"amsam-catalog-extent.geojson", "American Samoa Seismicity", "AmSam Seismicity");
		
		private String path;
		private String name;
		private String shortName;

		private NSHM27_SeismicityRegions(String path, String name, String shortName) {
			this.path = path;
			this.name = name;
			this.shortName = shortName;
		}
		
		public String getResourcePath() {
			return path;
		}
		
		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getShortName() {
			return shortName;
		}

		@Override
		public Region load() throws IOException {
			Region reg = checkConvertLon(NSHM23_BaseRegion.super.load());
			reg.setName(name);
			return reg;
		}
	}
	
	/**
	 * Regions within which seismicity constraints and spatial seismicity PDFs are determined
	 * 
	 * @author kevin
	 * @see NSHM23_RegionalSeismicity
	 * @see NSHM23_SeisSmoothingAlgorithms
	 * 
	 */
	public enum NSHM27_MapRegions implements NSHM23_BaseRegion, ShortNamed {
		GNMI(GNMI_DIR+"gnmi-map.geojson", "Guam & Northern Mariana Islands Map Region", "GNMI Map Region"),
		AMSAM(AMSAM_DIR+"amsam-map.geojson", "American Samoa Map Region", "AmSam Map Region");
		
		private String path;
		private String name;
		private String shortName;

		private NSHM27_MapRegions(String path, String name, String shortName) {
			this.path = path;
			this.name = name;
			this.shortName = shortName;
		}
		
		public String getResourcePath() {
			return path;
		}
		
		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getShortName() {
			return shortName;
		}

		@Override
		public Region load() throws IOException {
			Region reg = checkConvertLon(NSHM23_BaseRegion.super.load());
			reg.setName(name);
			return reg;
		}
	}
	
	private static Region checkConvertLon(Region region) {
		if (region.getMinLon() < 0) {
			LocationList modBorder = new LocationList();
			for (Location loc : region.getBorder())
				modBorder.add(new Location(loc.lat, loc.lon+360d));
			return new Region(modBorder, region.getBorderType());
		}
		return region;
	}

	public static String getNameForTRT(TectonicRegionType trt) {
		return switch(trt) {
		case ACTIVE_SHALLOW:
			yield "Crustal";
		case SUBDUCTION_INTERFACE:
			yield "Interface";
		case SUBDUCTION_SLAB:
			yield "Intraslab";
		default:
			throw new IllegalStateException("Unexpected TRT: "+trt);
		};
	}
	
	public static List<Site> loadHazardSites(NSHM27_SeismicityRegions reg) throws IOException {
		String regPrefix = reg.name().toLowerCase();
		CSVFile<String> csv = CSVFile.readStream(NSHM27_RegionLoader.class.getResourceAsStream(
				"/data/erf/nshm27/"+regPrefix+"/regions/sites-"+regPrefix+".csv"), true);
		List<Site> sites = new ArrayList<>(csv.getNumRows()-1);
		for (int row=1; row<csv.getNumRows(); row++) {
			String name = csv.get(row, 0);
			Location loc = new Location(csv.getDouble(row, 2), csv.getDouble(row, 1));
			if (loc.lon < 0)
				loc = new Location(loc.lat, loc.lon+360);
			sites.add(new Site(loc, name));
		}
		return sites;
	}

}
