package org.opensha.commons.geo.json;

import java.util.EnumSet;

/**
 * Enum of valid GeoJSON types
 * 
 * @author kevin
 *
 */
public enum GeoJSON_Type {
	
	Feature,
	FeatureCollection,
	GeometryCollection,
	Point,
	MultiPoint,
	LineString,
	MultiLineString,
	Polygon,
	MultiPolygon;
	
	public static final EnumSet<GeoJSON_Type> GEOM_TYPES = EnumSet.range(GeometryCollection, MultiPolygon);

}
