package org.opensha.commons.data.xyz;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.util.FileUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.primitives.Doubles;

/**
 * This class represents an arbitrarily discretized geographic dataset. It is backed by Locations
 * (in a HashMap). This should be used for scattered XYZ data or maps where it is impractical or
 * unnecessary to use the evenly discretized version.
 * 
 * @author kevin
 *
 */
public class FixedLocationArbDiscrGeoDataSet extends AbstractGeoDataSet {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	// list of points
	private ImmutableList<Location> points;
	// mapping of points to location
	private ImmutableMap<Location, Integer> locIndexMap;
	private double[] values;
	
	public FixedLocationArbDiscrGeoDataSet(FixedLocationArbDiscrGeoDataSet other) {
		this(other.points, other.locIndexMap, Arrays.copyOf(other.values, other.values.length));
	}
	
	public FixedLocationArbDiscrGeoDataSet(GeoDataSet other) {
		this(ImmutableList.copyOf(other.getLocationList()), null, Doubles.toArray(other.getValueList()));
	}
	
	public FixedLocationArbDiscrGeoDataSet(ImmutableList<Location> locs) {
		this(locs, null);
	}
	
	public FixedLocationArbDiscrGeoDataSet(ImmutableList<Location> locs, ImmutableMap<Location, Integer> locIndexMap) {
		this(locs, locIndexMap, null);
	}
	
	public FixedLocationArbDiscrGeoDataSet(ImmutableList<Location> locs, ImmutableMap<Location, Integer> locIndexMap, double[] values) {
		this(locs, locIndexMap, values, false);
	}
	
	public FixedLocationArbDiscrGeoDataSet(ImmutableList<Location> locs, ImmutableMap<Location, Integer> locIndexMap, double[] values, boolean latitudeX) {
		super(latitudeX);
		this.points = locs;
		if (locIndexMap == null) {
			Builder<Location, Integer> builder = ImmutableMap.builder();
			for (int i=0; i<points.size(); i++)
				builder.put(points.get(i), i);
			locIndexMap = builder.build();
		} else {
			Preconditions.checkState(locIndexMap.size() == points.size(), "supplied locIndexMap is of wrong size");
			for (int i=0; i<points.size(); i++) {
				Location loc = points.get(i);
				Integer index = locIndexMap.get(loc);
				Preconditions.checkNotNull(index, "supplied locIndexMap is missing location %s: %s", i, loc);
				Preconditions.checkState(index == i,
						"supplied locIndexMap is wrong, says location %s is %s but should be %s", loc, index, i);
			}
		}
		this.locIndexMap = locIndexMap;
		if (values == null) {
			this.values = new double[locs.size()];
		} else {
			Preconditions.checkState(values.length == locs.size());
			this.values = values;
		}
	}
	
	@Override
	public void set(Location loc, double value) {
		if (loc == null)
			throw new NullPointerException("Location cannot be null");
		Integer index = locIndexMap.get(loc);
		Preconditions.checkState(index != null, "Location doesn't exist in map and cannot add to fixed map: %s", loc);
		values[index] = value;
	}
	
	@Override
	public void set(int index, double value) {
		values[index] = value;
	}

	@Override
	public double get(Location loc) {
		Integer index = locIndexMap.get(loc);
		Preconditions.checkState(index != null, "Location doesn't exist in map: %s", loc);
		return values[index];
	}

	@Override
	public int indexOf(Location loc) {
		return locIndexMap.get(loc);
	}

	@Override
	public Location getLocation(int index) {
		return points.get(index);
	}

	@Override
	public boolean contains(Location loc) {
		return locIndexMap.containsKey(loc);
	}
	
	public static FixedLocationArbDiscrGeoDataSet loadXYZFile(String fileName, boolean latitudeX)
	throws FileNotFoundException, IOException {
		ArbDiscrGeoDataSet xyz = ArbDiscrGeoDataSet.loadXYZFile(fileName, latitudeX);
		
		return new FixedLocationArbDiscrGeoDataSet(xyz);
	}
	
	public static void writeXYZFile(XYZ_DataSet xyz, String fileName) throws IOException {
		ArbDiscrXYZ_DataSet.writeXYZFile(xyz, fileName);
	}

	@Override
	public Object clone() {
		return copy();
	}
	
	@Override
	public FixedLocationArbDiscrGeoDataSet copy() {
		double[] valCopy = Arrays.copyOf(values, values.length);
		return new FixedLocationArbDiscrGeoDataSet(points, locIndexMap, valCopy, isLatitudeX());
	}

	@Override
	public LocationList getLocationList() {
		LocationList locList = new LocationList();
		locList.addAll(points);
		return locList;
	}

	@Override
	public int size() {
		return points.size();
	}

}
