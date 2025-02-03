package org.opensha.sha.earthquake.rupForecastImpl.prvi25.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.NSHM23_BaseRegion;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.logicTree.PRVI25_CrustalFaultModels;

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
	
	public static Region loadPRVI_IntermediateModelMapExtents() throws IOException {
		return new Region(new Location(16.4,-70), new Location(20.2,-62));
	}
	
	public static Region loadPRVI_ModelBroad() throws IOException {
//		return new Region(new Location(16.5d, -70d), new Location(20, -62));
		Region reg = PRVI25_SeismicityRegions.CRUSTAL.load();
		reg = reg.clone();
		reg.setName("PRVI - Model Region");
		return reg;
	}
	
	public static List<Site> loadHazardSites() throws IOException {
		CSVFile<String> csv = CSVFile.readStream(PRVI25_CrustalFaultModels.class.getResourceAsStream("/data/erf/prvi25/sites/prvi_sites.csv"), true);
		List<Site> sites = new ArrayList<>();
		for (int row=1; row<csv.getNumRows(); row++) {
			String name = csv.get(row, 0);
			Location loc = new Location(csv.getDouble(row, 1), csv.getDouble(row, 2));
			sites.add(new Site(loc, name));
		}
		return sites;
	}

	public static void main(String[] args) throws IOException {
		for (PRVI25_SeismicityRegions seisReg : PRVI25_SeismicityRegions.values())
			seisReg.load();
	}

}
