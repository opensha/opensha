package org.opensha.commons.data.xyz;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

import org.opensha.commons.exceptions.GMT_MapException;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.mapping.gmt.GMT_Map;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

/**
 * This is a Geohgraphic Dataset on a regular grid, as defined by a GriddedRegion. Points
 * not in the given GriddedRegion cannot be set.
 * 
 * @author kevin
 *
 */
public class GriddedGeoDataSet extends AbstractGeoDataSet {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private GriddedRegion region;
	double[] values;
	
	public GriddedGeoDataSet(GriddedRegion region, boolean latitudeX) {
		super(latitudeX);
		this.region = region;
		values = new double[region.getNodeCount()];
	}

	@Override
	public int size() {
		return region.getNodeCount();
	}

	@Override
	public void set(Location loc, double value) {
		int index = indexOf(loc);
		if (index < 0)
			throw new InvalidRangeException("point must exist in the gridded region!");
		values[index] = value;
	}

	@Override
	public double get(Location loc) {
		return values[indexOf(loc)];
	}

	@Override
	public int indexOf(Location loc) {
		return region.indexForLocation(loc);
	}

	@Override
	public Location getLocation(int index) {
		return region.getLocation(index);
	}

	@Override
	public boolean contains(Location loc) {
		return indexOf(loc) >= 0;
	}

	@Override
	public Object clone() {
		return copy();
	}
	
	@Override
	public GriddedGeoDataSet copy() {
		GriddedGeoDataSet data = new GriddedGeoDataSet(region, isLatitudeX());
		
		for (int i=0; i<size(); i++) {
			data.set(getLocation(i), get(i));
		}
		
		return data;
	}

	@Override
	public LocationList getLocationList() {
		return region.getNodeList();
	}
	
	public GriddedRegion getRegion() {
		return region;
	}
	
	/**
	 * Bilinear interpolation. Algorithm taken from:<br>
	 * http://docs.oracle.com/cd/E17802_01/products/products/java-media/jai/forDevelopers/jai-apidocs/javax/media/jai/InterpolationBilinear.html
	 * 
	 * @param loc
	 * @return
	 * @throws IllegalArgumentException if x or y is outside of the allowable range
	 */
	public double bilinearInterpolation(Location loc) {
		if (!region.contains(loc))
			return Double.NaN;
		
		int xInd = region.getLonIndex(loc);
		int yInd = region.getLatIndex(loc);
		Location closestPoint = region.getLocation(region.getNodeIndex(yInd, xInd));
		
		int x0 = closestPoint.getLongitude() <= loc.getLongitude() ? xInd : xInd-1;
		int x1 = x0 + 1;
		// handle edges
		if (x1 >= region.getNumLonNodes())
			x1 = x0;
		int y0 = closestPoint.getLatitude() <= loc.getLatitude() ? yInd : yInd-1;
		int y1 = y0 + 1;
		// handle edges
		if (y1 >= region.getNumLatNodes())
			y1 = y0;
		
		int ind00  = region.getNodeIndex(y0, x0);
		int ind01  = region.getNodeIndex(y0, x1);
		int ind10  = region.getNodeIndex(y1, x0);
		int ind11  = region.getNodeIndex(y1, x1);
		if (ind00 < 0 || ind01 < 0 || ind10 < 0 || ind11 < 0)
			return Double.NaN;
		
		// "central"
		double s00 = get(ind00);
		// to the right
		double s01 = get(ind01);
		// below
		double s10 = get(ind10);
		// below and to the right
		double s11 = get(ind11);
		
//		double xfrac = (x - getX(x0))/gridSpacingX;
//		double yfrac = (y - getY(y0))/gridSpacingY;
		double xfrac = (loc.getLongitude() - region.getLocation(region.getNodeIndex(yInd, x0)).getLongitude())/region.getLonSpacing();
		double yfrac = (loc.getLatitude() - region.getLocation(region.getNodeIndex(y0, xInd)).getLatitude())/region.getLatSpacing();
		
		return (1 - yfrac) * ((1 - xfrac)*s00 + xfrac*s01) + 
			    yfrac * ((1 - xfrac)*s10 + xfrac*s11);
	}
	
	public static GriddedGeoDataSet loadXYZFile(File file, boolean latitudeX)
			throws FileNotFoundException, IOException {
		int latCol, lonCol;
		if (latitudeX) {
			latCol = 0;
			lonCol = 1;
		} else {
			latCol = 1;
			lonCol = 0;
		}
		return loadXYZFile(file, latCol, lonCol, -1, 2);
	}
	
	/**
	 * This will attempt to load in data from a regular grid in either fastXY or fastYX format. Column indexes are zero based
	 * and tell it which field in the file to use for latitude, longitude, depth (optional), and data.
	 * @param file
	 * @param latCol column index for the latitude field
	 * @param lonCol column index for the longitude field
	 * @param depthCol column index for the depth field, or -1 for constant zero depth
	 * @param dataCol column index for the data field
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static GriddedGeoDataSet loadXYZFile(File file, int latCol, int lonCol, int depthCol, int dataCol)
			throws FileNotFoundException, IOException {
		ArrayList<Location> locs = new ArrayList<Location>();
		ArrayList<Double> vals = new ArrayList<Double>();
		double minLat = Double.POSITIVE_INFINITY;
		double maxLat = Double.NEGATIVE_INFINITY;
		double minLon = Double.POSITIVE_INFINITY;
		double maxLon = Double.NEGATIVE_INFINITY;
		
		// first just load in all locations
		for (String line: Files.readLines(file, Charset.defaultCharset())) {
			if (line.startsWith("#"))
				// comment line
				continue;
			
			double lat = Double.NaN;
			double lon = Double.NaN;
			double depth = 0d;
			double data = Double.NaN;
			StringTokenizer tok = new StringTokenizer(line);
			int index = 0;
			while (index <= latCol || index <= lonCol || index <= depthCol || index <= dataCol) {
				String valStr = tok.nextToken();
				if (index == latCol)
					lat = Double.parseDouble(valStr);
				else if (index == lonCol)
					lon = Double.parseDouble(valStr);
				else if (index == depthCol)
					depth = Double.parseDouble(valStr);
				else if (index == dataCol)
					data = Double.parseDouble(valStr);
				index++;
			}
			Location loc = new Location(lat, lon, depth);
			minLat = Math.min(minLat, lat);
			maxLat = Math.max(maxLat, lat);
			minLon = Math.min(minLon, lon);
			maxLon = Math.max(maxLon, lon);
			
			locs.add(loc);
			vals.add(data);
		}
		Preconditions.checkState(locs.size() > 4);
		
		// now determine ordering
		Location loc1 = locs.get(0);
		Location loc2 = locs.get(1);
		boolean fastLongitude;
		if ((float)loc1.getLatitude() != (float)loc2.getLatitude())
			fastLongitude = false;
		else
			fastLongitude = true;
//		System.out.println("Fast longitude: "+fastLongitude);
//		System.out.println(loc1);
//		System.out.println(loc2);
		int numLat, numLon;
		MinMaxAveTracker latSpacingTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonSpacingTrack = new MinMaxAveTracker();
		if (fastLongitude) {
			numLon = 0;
			float prevLat = Float.NaN;
			Location prevLoc = null;
			for (Location loc : locs) {
				float lat = (float)loc.getLatitude();
				if (Float.isNaN(prevLat))
					prevLat = lat;
				else if (prevLat != lat)
					break;
				if (prevLoc != null)
					lonSpacingTrack.addValue(Math.abs(loc.getLongitude() - prevLoc.getLongitude()));
				prevLoc = loc;
				numLon++;
			}
			Preconditions.checkState(locs.size() % numLon == 0, 
					"Couldn't figure out gridding. Fast longitude, numLon=%s, count=%s", numLon, locs.size());
			numLat = locs.size()/numLon;
			prevLoc = locs.get(0);
			for (int i=numLon; i<locs.size(); i+=numLon) {
				Location loc = locs.get(i);
				latSpacingTrack.addValue(Math.abs(loc.getLatitude() - prevLoc.getLatitude()));
				prevLoc = loc;
			}
		} else {
			numLat = 0;
			float prevLon = Float.NaN;
			Location prevLoc = null;
			for (Location loc : locs) {
				float lon = (float)loc.getLongitude();
				if (Float.isNaN(prevLon))
					prevLon = lon;
				else if (prevLon != lon)
					break;
				if (prevLoc != null)
					latSpacingTrack.addValue(Math.abs(loc.getLatitude() - prevLoc.getLatitude()));
				prevLoc = loc;
				numLat++;
			}
			Preconditions.checkState(locs.size() % numLat == 0, 
					"Couldn't figure out gridding. Fast latitude, numLat=%s, count=%s", numLat, locs.size());
			numLon = locs.size()/numLat;
			prevLoc = locs.get(0);
			for (int i=numLat; i<locs.size(); i+=numLat) {
				Location loc = locs.get(i);
				lonSpacingTrack.addValue(Math.abs(loc.getLongitude() - prevLoc.getLongitude()));
				prevLoc = loc;
			}
		}
		// values can be rounded, so use average lat/lon spacing among all columns to hopefully nail real average
		double latSpacing = latSpacingTrack.getAverage();
		double lonSpacing = lonSpacingTrack.getAverage();
//		System.out.println("Lat spacing: "+latSpacing);
//		System.out.println("Lon spacing: "+lonSpacing);
//		System.out.println(latSpacingTrack);
//		System.out.println(lonSpacingTrack);
		Location lowerLeft = new Location(minLat, minLon);
		Location upperRight = new Location(maxLat+0.1*latSpacing, maxLon+0.1*lonSpacing); // pad just a bit
		GriddedRegion reg = new GriddedRegion(lowerLeft, upperRight, latSpacing, lonSpacing, lowerLeft);
//		System.out.println("Data numLat="+numLat+", numLon="+numLon);
		Preconditions.checkState(reg.getNumLocations() == locs.size(),
				"Region size doesn't match! Input has %s (%s x %s), reconstruction has %s",
				locs.size(), numLat, numLon, reg.getNumLocations());
		GriddedGeoDataSet dataset = new GriddedGeoDataSet(reg, latCol == 0);
		for (int i=0; i<locs.size(); i++)
			dataset.set(locs.get(i), vals.get(i));
		return dataset;
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException, GMT_MapException {
//		File file = new File("/home/kevin/workspace/scec_vdo_vtk/data/ShakeMapPlugin/Chino_Hills.txt");
//		GriddedGeoDataSet dataset = loadXYZFile(file, 1, 0, -1, 2);
////		File file = new File("/tmp/grid.xyz");
////		GriddedGeoDataSet dataset = loadXYZFile(file, 1, 0, -1, 2);
//		
//		CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(dataset.getMinZ(), dataset.getMaxZ());
//		GMT_Map map = FaultBasedMapGen.buildMap(
//				cpt, null, null, dataset, dataset.getRegion().getSpacing(), dataset.getRegion(), false, "Test");
//		FaultBasedMapGen.plotMap(null, null, true, map);
	}

}
