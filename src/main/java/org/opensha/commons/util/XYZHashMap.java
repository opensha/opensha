package org.opensha.commons.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

import org.opensha.commons.geo.Location;

/**
 * HashMap that loads and stores the values in a Generic Mapping Tools stle XYZ files.
 * 
 * @author kevin
 *
 */
public class XYZHashMap extends HashMap<Location, Double> {

	public XYZHashMap(String xyzFile) throws FileNotFoundException, IOException {
		super();
		
		ArrayList<String> lines = FileUtils.loadFile(xyzFile);
		
		for (String line : lines) {
			line = line.trim();
			if (line.length() < 2)
				continue;
			StringTokenizer tok = new StringTokenizer(line);
			double lat = Double.parseDouble(tok.nextToken());
			double lon = Double.parseDouble(tok.nextToken());
			double val = Double.parseDouble(tok.nextToken());
			
			this.put(lat, lon, val);
		}
	}
	
	public double get(double lat, double lon) {
		Location loc = new Location(lat, lon);
		return this.get(loc);
	}
	
	public void put(double lat, double lon, double val) {
		Location loc = new Location(lat, lon);
		this.put(loc, val);
	}
}
