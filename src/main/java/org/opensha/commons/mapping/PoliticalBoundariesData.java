package org.opensha.commons.mapping;

import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.PrimitiveArrayXY_Dataset;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;

import com.google.common.base.Preconditions;

public class PoliticalBoundariesData {

	private static Map<String, XY_DataSet[]> usOutlines;
	private static Map<String, Location> usCenters;
	private static XY_DataSet[] caOutlines;
	private static XY_DataSet[] nzOutlines;
	
	/**
	 * 
	 * @param region
	 * @return default political boundaries for the given region, or null if none available
	 */
	public static XY_DataSet[] loadDefaultOutlines(Region region) {
		// political boundary special cases
		
		if (region.getMaxLat() > 18d && region.getMinLon() < -55d) {
			// could be in the US, load US data
			try {
				initUSOutlines();
				XY_DataSet[] ret = null;
				for (String state : usOutlines.keySet()) {
					Location center = usCenters.get(state);
					boolean match = region.contains(center);
					if (!match && state.equals("California") &&
							(region.contains(new Location(34, -118)) || region.contains(new Location(38, -122))))
						// special case to capture northern/southern CA
						match = true;
					if (region.contains(center)) {
						if (ret == null) {
							ret = usOutlines.get(state);
						} else {
							// expand the returned array
							XY_DataSet[] stateOutlines = usOutlines.get(state);
							int prevLen = ret.length;
							ret = Arrays.copyOf(ret, prevLen+stateOutlines.length);
							System.arraycopy(stateOutlines, 0, ret, prevLen, stateOutlines.length);
						}
					}
				}
				if (ret != null)
					return ret;
			} catch (IOException e) {
				System.err.println("WARNING: couldn't load US outline data: "+e.getMessage());
			}
		}
		if (region.contains(new Location(-42.4, 172.3)) || region.contains(new Location(-38.8, 175.9))) {
			// NZ
			try {
				return loadNZOutlines();
			} catch (IOException e) {
				System.err.println("WARNING: couldn't load NZ outline data: "+e.getMessage());
			}
		}
		return null;
	}
	
	/**
	 * @return array of XY_DataSets that represent California boundaries (plural/array because of islands). X values are longitude
	 * and Y values are latitude.
	 * @throws IOException
	 */
	public synchronized static XY_DataSet[] loadCAOutlines() throws IOException {
		if (caOutlines == null) {
			// CA will be most common, keep as a separate file
			Map<String, XY_DataSet[]> caData = loadOutlinesFile(
					PoliticalBoundariesData.class.getResourceAsStream("/data/boundaries/california.txt"));
			Preconditions.checkState(caData.size() == 1);
			caOutlines = caData.values().iterator().next();
		}
		return caOutlines;
	}
	
	private synchronized static void initUSOutlines() throws IOException {
		if (usOutlines == null) {
			Map<String, XY_DataSet[]> usData = loadOutlinesFile(
					PoliticalBoundariesData.class.getResourceAsStream("/data/boundaries/us_complete.txt"));
			Map<String, Location> usCenters = new HashMap<>();
			for (String state : usData.keySet()) {
				double avgLat = 0d;
				double avgLon = 0d;
				int num = 0;
				for (XY_DataSet xy : usData.get(state)) {
					for (Point2D pt : xy) {
						avgLat += pt.getY();
						avgLon += pt.getX();
						num++;
					}
				}
				avgLat /= (double)num;
				avgLon /= (double)num;
				usCenters.put(state, new Location(avgLat, avgLon));
			}
			PoliticalBoundariesData.usOutlines = usData;
			PoliticalBoundariesData.usCenters = usCenters;
		}
	}
	
	/**
	 * @return array of XY_DataSets that represent New Zealand boundaries (plural/array because of islands). X values are longitude
	 * and Y values are latitude.
	 * @throws IOException
	 */
	public synchronized static XY_DataSet[] loadNZOutlines() throws IOException {
		if (nzOutlines == null) {
			Map<String, XY_DataSet[]> nzData = loadOutlinesFile(
					PoliticalBoundariesData.class.getResourceAsStream("/data/boundaries/new_zealand.txt"));
			Preconditions.checkState(nzData.size() == 1);
			nzOutlines = nzData.values().iterator().next();
		}
		return nzOutlines;
	}
	
//	private static XY_DataSet[] loadOutlinesFile(InputStream is) throws IOException {
//		BufferedReader br = new BufferedReader(new InputStreamReader(is));
//		List<DefaultXY_DataSet> outlines = new ArrayList<>();
//		String[] vals;
//        String line;
//        while ((line = br.readLine()) != null) {
//        	line = line.trim();
//        	if (line.isEmpty() || line.startsWith("#"))
//        		continue;
//        	if (line.startsWith("segment")) {
//        		outlines.add(new DefaultXY_DataSet());
//        		continue;
//        	}
//        	vals = line.trim().split(" ");
//        	double lat = Double.valueOf(vals[0]);
//        	double lon = Double.valueOf(vals[1]);
//        	if (outlines.isEmpty())
//        		outlines.add(new DefaultXY_DataSet());
//        	outlines.get(outlines.size()-1).set(lon, lat);
//        }
//        br.close();
//		
//		return outlines.toArray(new DefaultXY_DataSet[outlines.size()]);
//	}
	
	private static Map<String, XY_DataSet[]> loadOutlinesFile(InputStream is) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		
		Map<String, List<XY_DataSet>> outlines = new HashMap<>();
		
        String line;
        
        String curSegName = null;
        DefaultXY_DataSet curSeg = null;
        
        while ((line = br.readLine()) != null) {
        	line = line.trim();
        	if (line.isEmpty() || line.startsWith("#"))
        		continue;
        	if (line.startsWith("segment")) {
        		curSegName = line.substring("segment".length()).replace("_", " ").trim();
        		curSeg = new DefaultXY_DataSet();
        		List<XY_DataSet> segsForName = outlines.get(curSegName);
        		if (segsForName == null) {
        			segsForName = new ArrayList<>();
        			outlines.put(curSegName, segsForName);
        		}
        		segsForName.add(curSeg);
        		continue;
        	} else if (curSeg == null) {
        		// header
        		continue;
        	}
        	String[] vals = line.trim().split(" ");
        	double lat = Double.valueOf(vals[0]);
        	double lon = Double.valueOf(vals[1]);
        	Preconditions.checkNotNull(curSeg, "value encountered before first segment defined?");
        	curSeg.set(lon, lat);
        }
        br.close();
        
        Preconditions.checkState(!outlines.isEmpty(), "No outlines found");
        
        Map<String, XY_DataSet[]> ret = new HashMap<>();
        for (String name : outlines.keySet()) {
        	List<XY_DataSet> segs = outlines.get(name);
        	
        	XY_DataSet[] segArray = new XY_DataSet[segs.size()];
        	for (int i=0; i<segs.size(); i++)
        		segArray[i] = new PrimitiveArrayXY_Dataset(segs.get(i));
        	
        	ret.put(name, segArray);
        }
		
		return ret;
	}
	
	public static void main(String[] args) throws IOException {
		initUSOutlines();
	}

}
