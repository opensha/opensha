package org.opensha.commons.mapping.gmt.elements;

import org.opensha.commons.geo.Region;
import org.opensha.commons.mapping.gmt.GMT_Map;

public enum TopographicSlopeFile {
	CA_THREE		(3, "ca_dem_3sec_inten.grd", GMT_Map.ca_topo_region),
	US_SIX			(6, "us_dem_6sec_inten.grd", GMT_Map.us_topo_region),
	US_EIGHTEEN		(18, "us_dem_18sec_inten.grd", GMT_Map.us_topo_region),
	US_THIRTY		(30, "us_dem_30sec_inten.grd", GMT_Map.us_topo_region),
	SRTM_30_PLUS	(30, "srtm30_plus_v5.0_inten.grd", Region.getGlobalRegion());
	
	private final int resolution;
	private final String fileName;
	private final Region region;
	TopographicSlopeFile(int resolution, String fileName, Region region) {
		this.resolution = resolution;
		this.fileName = fileName;
		this.region = region;
	}
	
	public int resolution() { return resolution; }
	public String fileName() { return fileName; }
	public Region region() { return region; }
}
