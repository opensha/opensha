package org.opensha.sha.earthquake.rupForecastImpl.prvi25.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.FeatureCollection;

public class PRVI25_RegionLoader {
	
	private static final String DIR = "/data/erf/prvi25/regions";
	
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
	
	public static Region loadPRVI_ModelBroad() {
		return new Region(new Location(16.5d, -70d), new Location(20, -62));
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
