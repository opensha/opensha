package org.opensha.commons.mapping.gmt.elements;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;

public class PSXYPolygon extends PSXYElement {
	
	/**
	 * default serial version UID
	 */
	private static final long serialVersionUID = 1l;
	
	private List<Point2D> points = new ArrayList<Point2D>();
	
	/**
	 * Constructor for a simple line
	 * 
	 * @param point1
	 * @param point2
	 */
	public PSXYPolygon(Point2D point1, Point2D point2) {
		points.add(point1);
		points.add(point2);
	}
	
	public PSXYPolygon(Location loc1, Location loc2) {
		points.add(new Point2D.Double(loc1.getLongitude(), loc1.getLatitude()));
		points.add(new Point2D.Double(loc2.getLongitude(), loc2.getLatitude()));
	}
	
	public PSXYPolygon(List<Point2D> points) {
		this.points = points;
	}
	
	public PSXYPolygon(LocationList locs) {
		for (Location loc : locs) {
			points.add(new Point2D.Double(loc.getLongitude(), loc.getLatitude()));
		}
	}
	
	public PSXYPolygon() {
		
	}
	
	public List<Point2D> getPoints() {
		return points;
	}
	
	public void addPoint(Point2D point) {
		points.add(point);
	}
	
	/**
	 * Returns true if polygon has at least 2 points
	 * @return
	 */
	public boolean isValid() {
		return points != null && points.size() >= 2;
	}
	
	public int size() {
		return points.size();
	}
}
