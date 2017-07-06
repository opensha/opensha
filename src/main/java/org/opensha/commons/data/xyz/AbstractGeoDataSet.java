package org.opensha.commons.data.xyz;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.geo.Location;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;

/**
 * This is an abstract class for handling the translation from Location to Points for Geographic 
 * 
 * @author kevin
 *
 */
public abstract class AbstractGeoDataSet extends AbstractXYZ_DataSet implements GeoDataSet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private boolean latitudeX;
	
	/**
	 * 
	 * @param latitudeX - specifies whether X is Latitude or X is Longitude (supports either one)
	 */
	protected AbstractGeoDataSet(boolean latitudeX) {
		this.latitudeX = latitudeX;
	}
	
	private MinMaxAveTracker getLatTracker() {
		MinMaxAveTracker tracker = new MinMaxAveTracker();
		
		for (Location loc : getLocationList()) {
			tracker.addValue(loc.getLatitude());
		}
		
		return tracker;
	}
	
	private MinMaxAveTracker getLonTracker() {
		MinMaxAveTracker tracker = new MinMaxAveTracker();
		
		for (Location loc : getLocationList()) {
			tracker.addValue(loc.getLongitude());
		}
		
		return tracker;
	}
	
	@Override
	public double getMinLat() {
		return getLatTracker().getMin();
	}
	
	@Override
	public double getMaxLat() {
		return getLatTracker().getMax();
	}
	
	@Override
	public double getMinLon() {
		return getLonTracker().getMin();
	}
	
	@Override
	public double getMaxLon() {
		return getLonTracker().getMax();
	}

	@Override
	public double getMinX() {
		if (latitudeX)
			return getMinLat();
		else
			return getMinLon();
	}

	@Override
	public double getMaxX() {
		if (latitudeX)
			return getMaxLat();
		else
			return getMaxLon();
	}

	@Override
	public double getMinY() {
		if (latitudeX)
			return getMinLon();
		else
			return getMinLat();
	}

	@Override
	public double getMaxY() {
		if (latitudeX)
			return getMaxLon();
		else
			return getMaxLat();
	}

	@Override
	public double getMinZ() {
		return getZTracker().getMin();
	}

	@Override
	public double getMaxZ() {
		return getZTracker().getMax();
	}
	
	protected MinMaxAveTracker getZTracker() {
		MinMaxAveTracker tracker = new MinMaxAveTracker();
		
		for (double val : getValueList()) {
			tracker.addValue(val);
		}
		
		return tracker;
	}
	
	protected Location ptToLoc(Point2D point) {
		if (latitudeX)
			return new Location(point.getX(), point.getY());
		else
			return new Location(point.getY(), point.getX());
	}

	protected Point2D locToPoint(Location loc) {
		if (latitudeX)
			return new Point2D.Double(loc.getLatitude(), loc.getLongitude());
		else
			return new Point2D.Double(loc.getLongitude(), loc.getLatitude());
	}

	@Override
	public void set(Point2D point, double z) {
		set(ptToLoc(point), z);
	}

	@Override
	public void set(double x, double y, double z) {
		set(new Point2D.Double(x, y), z);
	}

	@Override
	public void set(int index, double z) {
		set(getLocation(index), z);
	}

	@Override
	public double get(Point2D point) {
		return get(ptToLoc(point));
	}

	@Override
	public double get(double x, double y) {
		return get(new Point2D.Double(x, y));
	}

	@Override
	public double get(int index) {
		return get(getLocation(index));
	}

	@Override
	public Point2D getPoint(int index) {
		return locToPoint(getLocation(index));
	}

	@Override
	public int indexOf(Point2D point) {
		if (point == null)
			return -1;
		return indexOf(ptToLoc(point));
	}

	@Override
	public int indexOf(double x, double y) {
		return indexOf(new Point2D.Double(x, y));
	}

	@Override
	public boolean contains(Point2D point) {
		if (point == null)
			return false;
		return contains(ptToLoc(point));
	}

	@Override
	public boolean contains(double x, double y) {
		return contains(new Point2D.Double(x, y));
	}

	@Override
	public void setAll(XYZ_DataSet dataset) {
		if (dataset instanceof GeoDataSet) {
			GeoDataSet geo = (GeoDataSet)dataset;
			for (int i=0; i<dataset.size(); i++)
				set(geo.getLocation(i), geo.get(i));
		} else {
			for (int i=0; i<dataset.size(); i++)
				set(dataset.getPoint(i), dataset.get(i));
		}
	}

	@Override
	public void setLatitudeX(boolean latitudeX) {
		this.latitudeX = latitudeX;
	}

	@Override
	public boolean isLatitudeX() {
		return latitudeX;
	}
	
	@Override
	public List<Point2D> getPointList() {
		ArrayList<Point2D> points = new ArrayList<Point2D>();
		for (Location loc : getLocationList())
			points.add(locToPoint(loc));
		return points;
	}
	
	@Override
	public List<Double> getValueList() {
		ArrayList<Double> vals = new ArrayList<Double>();
		for (int i=0; i<size(); i++)
			vals.add(get(i));
		return vals;
	}

}
