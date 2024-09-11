package org.opensha.sha.earthquake.rupForecastImpl.prvi25.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.NSHM23_BaseRegion;

public class PRVI25_RegionLoader {
	
	private static final String DIR = "/data/erf/prvi25/regions";
	private static final String SEIS_REG_PATH = DIR+"/collection";
	
	/**
	 * Regions within which seismicity constraints and spatial seismicity PDFs are determined
	 * 
	 * @author kevin
	 * @see NSHM23_RegionalSeismicity
	 * @see NSHM23_SeisSmoothingAlgorithms
	 * 
	 */
	public enum PRVI25_SeismicityRegions implements NSHM23_BaseRegion {
		CAR_INTERFACE("CAR_Interface.geojson"),
		CAR_INTRASLAB("CAR_Intraslab.geojson"),
		CRUSTAL("PRVI_Crustal_Grid.geojson"),
		MUE_INTERFACE("MUE_Interface.geojson"),
		MUE_INTRASLAB("MUE_Intraslab.geojson");
		
		private String fileName;

		private PRVI25_SeismicityRegions(String fileName) {
			this.fileName = fileName;
		}
		
		public String getResourcePath() {
			return SEIS_REG_PATH+"/"+fileName;
		}
	}
	
	public static Region loadPRVI_Tight() throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				PRVI25_RegionLoader.class.getResourceAsStream(DIR+"/prvi-map.geojson")));
		FeatureCollection features = FeatureCollection.read(reader);
		return Region.fromFeature(features.features.get(1));
	}
	
	public static Region loadPRVI_MapExtents() throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				PRVI25_RegionLoader.class.getResourceAsStream(DIR+"/prvi-map.geojson")));
		FeatureCollection features = FeatureCollection.read(reader);
		return Region.fromFeature(features.features.get(0));
	}
	
	public static Region loadPRVI_ModelBroad() throws IOException {
//		return new Region(new Location(16.5d, -70d), new Location(20, -62));
		return PRVI25_SeismicityRegions.CRUSTAL.load();
	}

	public static void main(String[] args) throws IOException {
		for (PRVI25_SeismicityRegions seisReg : PRVI25_SeismicityRegions.values())
			seisReg.load();
	}

}
