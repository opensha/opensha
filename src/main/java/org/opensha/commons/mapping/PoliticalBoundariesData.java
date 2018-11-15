package org.opensha.commons.mapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;

public class PoliticalBoundariesData {
	
	private static XY_DataSet[] caOutlines;
	
	/**
	 * @return array of XY_DataSets that represent California boundaries (plural/array because of islands). X values are longitude
	 * and Y values are latitude.
	 * @throws IOException
	 */
	public synchronized static XY_DataSet[] loadCAOutlines() throws IOException {
		if (caOutlines == null) {
			List<DefaultXY_DataSet> outlines = new ArrayList<>();
			InputStream is = PoliticalBoundariesData.class.getResourceAsStream("/resources/data/boundaries/california.txt");
			BufferedReader br = new BufferedReader(new InputStreamReader(is));
			String[] vals;
	        String line;
	        while ((line = br.readLine()) != null) {
	        	line = line.trim();
	        	if (line.isEmpty() || line.startsWith("#"))
	        		continue;
	        	if (line.startsWith("segment")) {
	        		outlines.add(new DefaultXY_DataSet());
	        		continue;
	        	}
	        	vals = line.trim().split(" ");
	        	double lat = Double.valueOf(vals[0]);
	        	double lon = Double.valueOf(vals[1]);
	        	if (outlines.isEmpty())
	        		outlines.add(new DefaultXY_DataSet());
	        	outlines.get(outlines.size()-1).set(lon, lat);
	        }
	        br.close();
			
			caOutlines = outlines.toArray(new DefaultXY_DataSet[outlines.size()]);
		}
		return caOutlines;
	}

}
