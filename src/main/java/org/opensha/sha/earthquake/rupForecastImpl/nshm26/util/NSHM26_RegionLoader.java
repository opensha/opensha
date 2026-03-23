package org.opensha.sha.earthquake.rupForecastImpl.nshm26.util;

import java.io.IOException;

import org.opensha.commons.data.ShortNamed;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.NSHM23_BaseRegion;

public class NSHM26_RegionLoader {
	private static final String GNMI_DIR = "/data/erf/nshm26/gnmi/regions/";
	private static final String AMSAM_DIR = "/data/erf/nshm26/amsam/regions/";
	
	/**
	 * Regions within which seismicity constraints and spatial seismicity PDFs are determined
	 * 
	 * @author kevin
	 * @see NSHM23_RegionalSeismicity
	 * @see NSHM23_SeisSmoothingAlgorithms
	 * 
	 */
	public enum NSHM26_SeismicityRegions implements NSHM23_BaseRegion, ShortNamed {
		GNMI(GNMI_DIR+"gnmi-catalog-extent.geojson", "Guam & Northern Mariana Islands Seismicity", "GNMI Seismicity"),
		AMSAM(AMSAM_DIR+"amsam-catalog-extent.geojson", "American Samoa Seismicity", "AmSam Seismicity");
		
		private String path;
		private String name;
		private String shortName;

		private NSHM26_SeismicityRegions(String path, String name, String shortName) {
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
			return checkConvertLon(NSHM23_BaseRegion.super.load());
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

}
