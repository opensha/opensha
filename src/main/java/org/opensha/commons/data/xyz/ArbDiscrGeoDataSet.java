package org.opensha.commons.data.xyz;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.util.FileUtils;

/**
 * This class represents an arbitrarily discretized geographic dataset. It is backed by Locations
 * (in a HashMap). This should be used for scattered XYZ data or maps where it is impractical or
 * unnecessary to use the evenly discretized version.
 * 
 * @author kevin
 *
 */
public class ArbDiscrGeoDataSet extends AbstractGeoDataSet {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	// we need a separate list of points (vs just using map for everything) to ensure order
	private LocationList points;
	// mapping of poitns to values
	private HashMap<Location, Double> map;
	
	public ArbDiscrGeoDataSet(boolean latitudeX) {
		super(latitudeX);
		points = new LocationList();
		map = new HashMap<Location, Double>();
	}
	
	@Override
	public void set(Location loc, double value) {
		if (loc == null)
			throw new NullPointerException("Location cannot be null");
		if (!contains(loc))
			points.add(loc);
		map.put(loc, value);
	}

	@Override
	public double get(Location loc) {
		return map.get(loc);
	}

	@Override
	public int indexOf(Location loc) {
		return points.indexOf(loc);
	}

	@Override
	public Location getLocation(int index) {
		return points.get(index);
	}

	@Override
	public boolean contains(Location loc) {
		return map.containsKey(loc);
	}
	
	public static ArbDiscrGeoDataSet loadXYZFile(String fileName, boolean latitudeX)
	throws FileNotFoundException, IOException {
		ArrayList<String> lines = FileUtils.loadFile(fileName);
		
		ArbDiscrGeoDataSet xyz = new ArbDiscrGeoDataSet(latitudeX);
		
		for (String line : lines) {
			if (line.startsWith("#"))
				continue;
			if (line.length() < 2)
				continue;
			StringTokenizer tok = new StringTokenizer(line);
			if (tok.countTokens() < 3)
				continue;
			
			double lat, lon;
			
			if (latitudeX) {
				lat = Double.parseDouble(tok.nextToken());
				lon = Double.parseDouble(tok.nextToken());
			} else {
				lon = Double.parseDouble(tok.nextToken());
				lat = Double.parseDouble(tok.nextToken());
			}
			double val = Double.parseDouble(tok.nextToken());
			
			xyz.set(new Location(lat, lon), val);
		}
		
		return xyz;
	}
	
	public static void writeXYZFile(XYZ_DataSet xyz, String fileName) throws IOException {
		ArbDiscrXYZ_DataSet.writeXYZFile(xyz, fileName);
	}

	@Override
	public Object clone() {
		return copy();
	}
	
	@Override
	public ArbDiscrGeoDataSet copy() {
		ArbDiscrGeoDataSet data = new ArbDiscrGeoDataSet(isLatitudeX());
		
		for (int i=0; i<size(); i++) {
			data.set(getPoint(i), get(i));
		}
		
		return data;
	}

	@Override
	public LocationList getLocationList() {
		return points;
	}

	@Override
	public int size() {
		return points.size();
	}

}
