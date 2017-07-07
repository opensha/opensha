package org.opensha.sha.calc.mcer;

import java.util.List;
import java.util.Map;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class TLDataLoader {
	
	private List<Region> regions;
	private List<Double> values;
	
	private static final double distance_buffer = 10; // buffer in km for really close but just outside
	
	public TLDataLoader(CSVFile<String> polygons, CSVFile<String> attributes) {
		regions = Lists.newArrayList();
		values = Lists.newArrayList();
		
		Map<String, Double> shapeIDtoVals = Maps.newHashMap();
		
		// load in values
		for (int row=1; row<attributes.getNumRows(); row++) {
			String shapeID = attributes.get(row, 0);
			double value = Double.parseDouble(attributes.get(row, 2));
			shapeIDtoVals.put(shapeID, value);
		}
		
		// load in polygons
		Map<String, LocationList> shapeIDtoBoundaries = Maps.newHashMap();
		
		for (int row=1; row<polygons.getNumRows(); row++) {
			String shapeID = polygons.get(row, 0);
			double lon = Double.parseDouble(polygons.get(row, 1));
			double lat = Double.parseDouble(polygons.get(row, 2));
			Location loc = new Location(lat, lon);
			LocationList boundary = shapeIDtoBoundaries.get(shapeID);
			if (boundary == null) {
				boundary = new LocationList();
				shapeIDtoBoundaries.put(shapeID, boundary);
			}
			boundary.add(loc);
		}
		
		for (String shapeID : shapeIDtoVals.keySet()) {
			double val = shapeIDtoVals.get(shapeID);
			LocationList boundary = shapeIDtoBoundaries.get(shapeID);
			Region region = new Region(boundary, BorderType.GREAT_CIRCLE);
			regions.add(region);
			values.add(val);
		}
	}
	
	public double getValue(Location loc) {
		Preconditions.checkNotNull(loc);
		for (int i=0; i<regions.size(); i++)
			if (regions.get(i).contains(loc))
				return values.get(i);
		// ok see if it's a really near miss that we should still include
		double minDistance = Double.POSITIVE_INFINITY;
		int minIndex = -1;
		for (int i=0; i<regions.size(); i++) {
			double dist = regions.get(i).distanceToLocation(loc);
			if (dist < minDistance) {
				minDistance= dist;
				minIndex = i;
			}
		}
		if (minDistance < distance_buffer)
			return values.get(minIndex);
		return Double.NaN;
	}

//	public static void main(String[] args) throws IOException {
////		TLDataLoader tl = new TLDataLoader(
////				CSVFile.readFile(new File("/tmp/temp-nodes.csv"), true),
////				CSVFile.readFile(new File("/tmp/temp-attributes.csv"), true));
//		TLDataLoader tl = new TLDataLoader(
//				CSVFile.readStream(TLDataLoader.class.getResourceAsStream("/resources/data/site/USGS_TL/tl-nodes.csv"), true),
//				CSVFile.readStream(TLDataLoader.class.getResourceAsStream("/resources/data/site/USGS_TL/tl-attributes.csv"), true));
//		
//		CybershakeSiteInfo2DB sites2db = new CybershakeSiteInfo2DB(Cybershake_OpenSHA_DBApplication.getDB());
//		int numWith = 0;
//		int total = 0;
//		for (CybershakeSite site : sites2db.getAllSitesFromDB()) {
//			if (!Double.isNaN(tl.getValue(site.createLocation())))
//				numWith++;
//			else
//				System.out.println(site.name+" doesn't: "+site.createLocation());
//			total++;
//		}
//		System.out.println(numWith+"/"+total+" have TsubL data!");
//		System.exit(0);
//	}

}
