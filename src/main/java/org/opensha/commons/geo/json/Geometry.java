package org.opensha.commons.geo.json;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * This class represents a GeoJSON Geometry and provides serialization capabilities.
 * <br>
 * See <a href="https://datatracker.ietf.org/doc/html/rfc7946">RFC 7946</a> or the
 * <a href="https://geojson.org/">GeoJSON home page</a> for more information.
 * <br>
 * Actual Geometry instances will be loaded a subclasses of this type.
 * 
 * @author kevin
 *
 */
@JsonAdapter(Geometry.GeometryAdapter.class)
public class Geometry {
	
	public final GeoJSON_Type type;
	private boolean serializeZeroDepth;
	
	private Geometry(GeoJSON_Type type, boolean serializeZeroDepth) {
		Preconditions.checkState(GeoJSON_Type.GEOM_TYPES.contains(type), "Type is not a valid geometry type: %s", type);
		this.type = type;
		this.serializeZeroDepth = serializeZeroDepth;
	}
	
	public boolean isSerializeZeroDepths() {
		return serializeZeroDepth;
	}
	
	public void setSerializeZeroDepths(boolean serializeZeroDepth) {
		this.serializeZeroDepth = serializeZeroDepth;
	}
	
	public static class LineString extends Geometry {
		
		public final LocationList line;
		
		public LineString(Location... locs) {
			super(GeoJSON_Type.LineString, hasNonZeroDepth(locs));
			
			this.line = new LocationList();
			for (Location loc : locs)
				line.add(loc);
		}
		
		public LineString(LocationList line) {
			super(GeoJSON_Type.LineString, hasNonZeroDepth(line));
			
			this.line = line;
		}
		
		private LineString(List<Coordinates> coords) {
			super(GeoJSON_Type.LineString, coordsHaveDepths(coords));
			
			line = new LocationList();
			for (Coordinates coord : coords)
				line.add(coord.toLoc());
		}
	}
	
	public static class MultiLineString extends Geometry {
		
		public final ImmutableList<LocationList> lines;
		
		public MultiLineString(List<LocationList> lines) {
			super(GeoJSON_Type.MultiLineString, hasNonZeroDepths(lines));
			
			this.lines = ImmutableList.copyOf(lines);
		}
		
		private MultiLineString(Coordinates coords) {
			super(GeoJSON_Type.MultiLineString, coordsHaveDepths(coords.coords));
			
			ImmutableList.Builder<LocationList> lines = new ImmutableList.Builder<>();
			for (Coordinates subCoords : coords.coords)
				lines.add(new LineString(subCoords.coords).line);
			this.lines = lines.build();
		} 
	}
	
	public static class Point extends Geometry {
		
		public final Location point;
		
		public Point(Location point) {
			super(GeoJSON_Type.Point, hasNonZeroDepth(point));
			
			this.point = point;
		}
		
		private Point() {
			super(GeoJSON_Type.Point, false);
			
			this.point = null;
		}
		
		public Point(Coordinates coords) {
			super(GeoJSON_Type.Point, coords.location.length > 2);
			
			this.point = coords.toLoc();
		}
	}
	
	public static class MultiPoint extends Geometry {
		
		public final LocationList points;
		
		public MultiPoint(LocationList points) {
			super(GeoJSON_Type.MultiPoint, hasNonZeroDepth(points));
			
			this.points = points;
		}
		
		private MultiPoint(List<Coordinates> coords) {
			super(GeoJSON_Type.MultiPoint, coordsHaveDepths(coords));
			
			points = new LocationList();
			for (Coordinates coord : coords)
				points.add(coord.toLoc());
		}
	}
	
	public static class Polygon extends Geometry {
		
		public final LocationList polygon;
		public final LocationList[] holes;
		
		public Polygon(Region region) {
			super(GeoJSON_Type.Polygon, hasNonZeroDepth(region.getBorder()));
			polygon = getPolygonBorder(region.getBorder(), false);
			
			List<LocationList> interiors = region.getInteriors();
			if (interiors != null) {
				holes = new LocationList[interiors.size()];
				for (int i=0; i<holes.length; i++) {
					holes[i] = getPolygonBorder(interiors.get(i), true);
					if (hasNonZeroDepth(holes[i]))
						setSerializeZeroDepths(true);
				}
			} else {
				holes = null;
			}
		}
		
		public Polygon(LocationList polygon) {
			this(polygon, null);
		}
		
		public Polygon(LocationList polygon, LocationList[] holes) {
			super(GeoJSON_Type.Polygon, hasNonZeroDepth(polygon));
			
			this.polygon = polygon;
			this.holes = holes;
			if (holes != null && !isSerializeZeroDepths()) {
				for (LocationList hole : holes)
					if (hasNonZeroDepth(hole))
						setSerializeZeroDepths(true);
			}
		}
		
		private Polygon(List<Coordinates> coords) {
			super(GeoJSON_Type.Polygon, coordsHaveDepths(coords));
			
			LocationList border = null;
			List<LocationList> holes = new ArrayList<>();
			for (Coordinates subCoords : coords) {
				LocationList points = deserializePoints(subCoords.coords);
				if (border == null)
					border = points;
				else
					holes.add(points);
			}
			
			this.polygon = border;
			this.holes = holes.isEmpty() ? null : holes.toArray(new LocationList[0]);
		}
		
		/**
		 * @return {@link Region} representation
		 * @throws IllegalArgumentException if this polygon is not a valid {@link Region}
		 */
		public Region asRegion() {
			if (polygon == null)
				return null;
			Region region = new Region(polygon, null);
			
			if (holes != null)
				for (LocationList interior : holes)
					region.addInterior(new Region(interior, null));
			
			return region;
		}
	}
	
	public static class MultiPolygon extends Geometry {
		
		public final ImmutableList<Polygon> polygons;
		
		public MultiPolygon(List<Polygon> polygons) { 
			super(GeoJSON_Type.MultiPolygon, geomsHaveDepths(polygons));
			
			this.polygons = ImmutableList.copyOf(polygons);
		}
		
		/**
		 * @return {@link Region} representations
		 * @throws IllegalArgumentException if any polygon is not a valid {@link Region}
		 */
		public List<Region> asRegions() {
			List<Region> ret = new ArrayList<>();
			for (Polygon polygon : polygons)
				ret.add(polygon.asRegion());
			return ret;
		}
	}
	
	public static class GeometryCollection extends Geometry {
		
		public final ImmutableList<Geometry> geometries;
		
		public GeometryCollection(Geometry... geometries) {
			this(List.of(geometries));
		}
		
		public GeometryCollection(List<Geometry> geometries) {
			super(GeoJSON_Type.GeometryCollection, geomsHaveDepths(geometries));
			
			this.geometries = ImmutableList.copyOf(geometries);
		}
	}
	
	private static boolean coordsHaveDepths(List<Coordinates> coords) {
		for (Coordinates coord : coords) {
			if (coord.location == null) {
				if (coordsHaveDepths(coord.coords))
					return true;
			} else if (coord.location.length > 2) {
				return true;
			}
		}
		return false;
	}
	
	private static boolean geomsHaveDepths(List<? extends Geometry> geometries) {
		for (Geometry geom : geometries)
			if (geom.serializeZeroDepth)
				return true;
		return false;
	}
	
	private static boolean hasNonZeroDepth(List<Location> locs) {
		for (Location loc : locs)
			if (hasNonZeroDepth(loc))
				return true;
		return false;
	}
	
	private static boolean hasNonZeroDepths(List<? extends List<Location>> lines) {
		for (List<Location> locs : lines)
			if (hasNonZeroDepth(locs))
				return true;
		return false;
	}
	
	private static boolean hasNonZeroDepth(Location... locs) {
		for (Location loc : locs)
			if (hasNonZeroDepth(loc))
				return true;
		return false;
	}
	
	private static boolean hasNonZeroDepth(Location loc) {
		return loc.getDepth() != 0d;
	}
	
	public static class GeometryAdapter extends AbstractGeoJSON_TypeAdapter<Geometry> {
		
		private DepthSerializationType depthType;
		
		public GeometryAdapter() {
			this(DEPTH_SERIALIZATION_DEFAULT);
		}
		
		public GeometryAdapter(DepthSerializationType depthType) {
			this.depthType = depthType;
		}

		@Override
		public void write(JsonWriter out, Geometry value) throws IOException {
			if (value == null) {
				out.nullValue();
				return;
			}
			out.beginObject();
			
			out.name("type").value(value.type.name());
			
			if (value instanceof GeometryCollection) {
				out.name("geometries").beginArray();
				
				for (Geometry geometry : ((GeometryCollection)value).geometries)
					write(out, geometry);
				
				out.endArray();
			} else {
				out.name("coordinates");
				
				boolean serializeZeros = value.isSerializeZeroDepths();
				
				switch (value.type) {
				case Point:
					serializeLoc(out, ((Point)value).point, depthType, serializeZeros);
					break;
					
				case MultiPoint:
					out.beginArray();
					for (Location point : ((MultiPoint)value).points)
						serializeLoc(out, point, depthType, serializeZeros);
					out.endArray();
					break;
					
				case LineString:
					out.beginArray();
					for (Location point : ((LineString)value).line)
						serializeLoc(out, point, depthType, serializeZeros);
					out.endArray();
					break;
					
				case MultiLineString:
					out.beginArray();
					for (LocationList line : ((MultiLineString)value).lines) {
						out.beginArray();
						for (Location point : line)
							serializeLoc(out, point, depthType, serializeZeros);
						out.endArray();
					}
					out.endArray();
					break;
					
				case Polygon:
					serializePolygon(out, (Polygon)value, depthType, serializeZeros);
					break;
					
				case MultiPolygon:
					out.beginArray();
					for (Polygon polygon : ((MultiPolygon)value).polygons)
						serializePolygon(out, polygon, depthType, serializeZeros);
					out.endArray();
					break;
					
				default:
					throw new IllegalStateException("Unexpected type for a Geometry: "+value.type);
				
				}
			}
			
			out.endObject();
		}

		@Override
		public Geometry read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NULL) {
				in.nextNull();
				return null;
			}
			in.beginObject();
			
			Geometry geometry = innerReadAsType(in, null);
			
			in.endObject();
			return geometry;
		}

		@Override
		public Geometry innerReadAsType(JsonReader in, GeoJSON_Type type) throws IOException {
			Geometry geometry = null;
			Coordinates coords = null;
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "type":
					Preconditions.checkState(type == null, "Type already declared");
					type = GeoJSON_Type.valueOf(in.nextString());
					Preconditions.checkState(GeoJSON_Type.GEOM_TYPES.contains(type),
							"Not a valid geometry type: %s", type.name());
					break;
				case "geometries":
//					Preconditions.checkState(type != null, "Type must be supplied before geometries array");
					Preconditions.checkState(type == null || type == GeoJSON_Type.GeometryCollection,
							"Only GeometryCollections should have a geometries array");
					Preconditions.checkState(geometry == null, "Geometry already defined and geometries array encountered");
					Preconditions.checkState(coords == null, "Coordinates already defined and geometries array encountered");
					
					ImmutableList.Builder<Geometry> geometries = new ImmutableList.Builder<>();
					in.beginArray();
					while (in.hasNext())
						geometries.add(read(in));
					in.endArray();
					geometry = new GeometryCollection(geometries.build());
					break;
				case "coordinates":
//					Preconditions.checkState(type != null, "Type must be supplied before coordinates array");
					Preconditions.checkState(type != GeoJSON_Type.GeometryCollection,
							"GeometryCollections should not have a coordinates array");
					Preconditions.checkState(geometry == null, "Geometry already defined and coordinate array encountered");
					Preconditions.checkState(coords == null, "Coordinates already defined and coordinate array encountered");
					
					coords = new Coordinates(depthType);
					parseCoordsRecursive(in, coords);
					break;

				default:
					in.skipValue();
					break;
				}
			}
			
			Preconditions.checkNotNull(type, "Geometry type required");
			if (geometry != null) {
				Preconditions.checkState(type == GeoJSON_Type.GeometryCollection,
						"Only GeometryCollections should have a geometries array");
			} else if (type == GeoJSON_Type.GeometryCollection) {
				// empty geometry collection
				Preconditions.checkState(coords == null, "GeometryCollection should not supply coordinates array");
				geometry = new GeometryCollection(new ArrayList<>());
			} else {
				// need to parse geometry
				switch (type) {
				case Point:
					if (coords == null) {
						geometry = new Point();
					} else {
						Preconditions.checkState(coords.coords == null);
						geometry = new Point(coords);
					}
					break;
					
				case MultiPoint:
					geometry = new MultiPoint(coords.coords);
					break;
					
				case LineString:
					geometry = new LineString(coords.coords);
					break;
					
				case MultiLineString:
					Preconditions.checkState(coords.location == null);
					if (coords.coords == null) {
						geometry = new MultiLineString(new ArrayList<>());
					} else {
						geometry = new MultiLineString(coords);
					}
					break;
					
				case Polygon:
					geometry = new Polygon(coords.coords);
					break;
					
				case MultiPolygon:
					Preconditions.checkState(coords.location == null);
					if (coords.coords == null) {
						geometry = new MultiPolygon(new ArrayList<>());
					} else {
						ImmutableList.Builder<Polygon> polys = new ImmutableList.Builder<>();
						for (Coordinates subCoords : coords.coords)
							polys.add(new Polygon(subCoords.coords));
						geometry = new MultiPolygon(polys.build());
					}
					break;
					
				default:
					throw new IllegalStateException("Unexpected type for a Geometry: "+type);
				
				}
			}
			Preconditions.checkNotNull(geometry);
			return geometry;
		}
		
	}
	
	private static class Coordinates {
		private double[] location;
		private List<Coordinates> coords;
		private DepthSerializationType depthType;
		
		public Coordinates(DepthSerializationType depthType) {
			this.depthType = depthType;
		}
		
		public Location toLoc() {
			Preconditions.checkNotNull(location);
			if (location.length > 2)
				return new Location(location[1], location[0], depthType.fromGeoJSON(location[2]));
			return new Location(location[1], location[0]);
		}
	}
	
	private static void parseCoordsRecursive(JsonReader in, Coordinates coords) throws IOException {
		doParseCoordsRecursive(in, coords);
		
		// now post process to validate longitudes, shifting if necessary

		// check longitudes
		LonRange range = new LonRange();
		lonRangeRecursive(coords, range);
		double span = range.max - range.min;
		if (range.min < GeoTools.LON_MIN || range.max > GeoTools.LON_MAX) {
			if (span > 360d) {
				System.err.println("WARNING loading GeoJSON coordinates: longitudes range outside of ["
						+GeoTools.LON_MIN+", "+GeoTools.LON_MAX+"] and can't correct because span is > 360: "
								+ "["+(float)range.min+", "+(float)range.max+"]");
			} else {
				double delta = 0d;
				if (range.min < GeoTools.LON_MIN) {
					double min = range.min;
					while (min < GeoTools.LON_MIN) {
						delta += 360;
						min = range.min + delta;
					}
				} else {
					double max = range.max;
					while (max > GeoTools.LON_MAX) {
						delta -= 360;
						max = range.max + delta;
					}
				}
				System.err.println("WARNING loading GeoJSON coordinates: longitudes range outside of ["
						+GeoTools.LON_MIN+", "+GeoTools.LON_MAX+"], shifting by "+(float)delta+" degrees. "
								+ "Original range: ["+(float)range.min+", "+(float)range.max+"]");
				coordLonShiftRecursive(coords, delta);
			}
		}
	}
	
	private static void doParseCoordsRecursive(JsonReader in, Coordinates coords) throws IOException {
		if (in.peek() == JsonToken.NULL)
			return;
		in.beginArray();
		
		JsonToken token = in.peek();
		if (token == JsonToken.BEGIN_ARRAY) {
			// go another level deeper
			coords.coords = new ArrayList<>();
			while (in.hasNext()) {
				Coordinates subCoords = new Coordinates(coords.depthType);
				coords.coords.add(subCoords);
				doParseCoordsRecursive(in, subCoords);
			}
		} else {
			coords.location = deserializeLocWithinArray(in);
		}
		
		in.endArray();
	}
	
	private static void lonRangeRecursive(Coordinates coords, LonRange range) {
		if (coords.location != null) {
			double lon = coords.location[0];
			range.min = Math.min(lon, range.min);
			range.max = Math.max(lon, range.max);
		}
		if (coords.coords != null)
			for (Coordinates subCoords : coords.coords)
				lonRangeRecursive(subCoords, range);
	}
	
	private static class LonRange {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
	}
	
	private static void coordLonShiftRecursive(Coordinates coords, double delta) {
		if (coords.location != null)
			coords.location[0] += delta;
		if (coords.coords != null)
			for (Coordinates subCoords : coords.coords)
				coordLonShiftRecursive(subCoords, delta);
	}
	
	/**
	 * The GeoJSON specification (<a href="https://tools.ietf.org/html/rfc7946">RFC 7946</a>) dictates that
	 * 3D locations store their elevation in meters above the WGS86 ellipsoid. Some web services use different
	 * representations, however, notably the USGS, so this enum allows for using the same serialization code
	 * with different implementations.
	 * 
	 * @author kevin
	 *
	 */
	public static enum DepthSerializationType {
		/**
		 * Elevation in meters, as specified in the GeoJSON specification (RFC 7946)
		 * 
		 * Technically, this should be the elevation above the WGS86 ellipsoid, although here
		 * it is usually relative to the surface of the earth
		 */
		ELEVATION_M,
		/**
		 * Depth below the surface of the earth in meters. This goes against the GeoJSON spec,
		 * but is used by some USGS web services
		 */
		DEPTH_M,
		/**
		 * Depth below the surface of the earth in kilometers. This goes against the GeoJSON spec,
		 * but is used by some USGS web services and makes the most sense for use in OpenSHA.
		 */
		DEPTH_KM;
		
		/**
		 * Converts the given OpenSHA depth (in km) to a JSON depth/elevation, according to the type
		 * 
		 * @param depth
		 * @return
		 */
		public double toGeoJSON(double depth) {
			switch (this) {
			case ELEVATION_M:
				return -depth*1000d; // elevation, in m
			case DEPTH_M:
				return depth*1000d; // depth, in m
			case DEPTH_KM:
				return depth; // depth, in km
			default:
				throw new IllegalStateException();
			}
		}
		
		/**
		 * Converts a JSON depth/elevation to an OpenSHA depth (in km), according to the type
		 * @param depth
		 * @return
		 */
		public double fromGeoJSON(double depth) {
			switch (this) {
			case ELEVATION_M:
				return depth*-1e-3; // elev in m -> depth in km
			case DEPTH_M:
				return depth *1e-3; // depth in m -> depth in km
			case DEPTH_KM:
				return depth; // depth in km
			default:
				throw new IllegalStateException();
			}
		}
	}
	
	public static final DepthSerializationType DEPTH_SERIALIZATION_DEFAULT = DepthSerializationType.DEPTH_KM;
	
	/*
	 * The GeoJSON spec doesn't seem to state anything about the valid range of longitude values.
	 * 
	 * TODO: values not allowed by GeoTools.validate*() will throw exceptions, but could be converted. Consider
	 * converting values outside that range (e.g., longitude of -181 would throw an exception). 
	 */
	
	public static void serializeLoc(JsonWriter out, Location loc, DepthSerializationType depthType,
			boolean serializeZeros) throws IOException {
		if (loc == null) {
			out.nullValue();
			return;
		}
		out.beginArray();
		out.value(loc.getLongitude());
		out.value(loc.getLatitude());
		if (serializeZeros || loc.getDepth() != 0d)
			out.value(depthType.toGeoJSON(loc.getDepth()));
		out.endArray();
	}
	
	private static double[] deserializeLocWithinArray(JsonReader in) throws IOException {
		double lon = in.nextDouble();
		double lat = in.nextDouble();
		if (in.peek() != JsonToken.END_ARRAY)
			return new double[] { lon, lat, in.nextDouble() };
		return new double[] { lon, lat };
	}
	
	private static LocationList deserializePoints(List<Coordinates> coords) {
		LocationList points = new LocationList();
		if (coords == null || coords.isEmpty())
			return points;
		
		for (Coordinates subCoords : coords) {
			Preconditions.checkState(subCoords.coords == null);
			points.add(subCoords.toLoc());
		}
		return points;
	}
	
	private static void serializePolygon(JsonWriter out, Polygon polygon, DepthSerializationType depthType,
			boolean serializeZeros) throws IOException {
		out.beginArray();
		
		out.beginArray();
		for (Location loc : getPolygonBorder(polygon.polygon, false))
			serializeLoc(out, loc, depthType, serializeZeros);
		out.endArray();
		
		if (polygon.holes != null) {
			for (LocationList interior : polygon.holes) {
				out.beginArray();
				for (Location loc : getPolygonBorder(interior, true))
					serializeLoc(out, loc, depthType, serializeZeros);
				out.endArray();
			}
		}
		
		out.endArray();
	}
	
	/**
	 * @param border
	 * @param hold
	 * @return a valid GeoJSON polygon border, closed and following their right-hand rule
	 */
	public static LocationList getPolygonBorder(LocationList border, boolean hole) {
		border = border.clone();
		// close it
		if (!border.first().equals(border.last()))
			border.add(border.first());
		Location prev = null;
		
		// figire out the direction
		double directionTest = 0d;
		for (Location loc : border) {
			if (prev != null)
				directionTest += (loc.getLongitude()-prev.getLongitude())*(loc.getLatitude()+prev.getLatitude());
			prev = loc;
		}
//		System.out.println("Direction test: "+directionTest);
		if ((hole && directionTest < 0) || (!hole && directionTest > 0))
			// directionTest > 0 indicates clockwise, RFC 7946 states that exteriors must be counter-clockwise
			border.reverse();
		
		return border;
	}
	
	/**
	 * @return GeoJSON representation of this Geometry
	 * @throws IOException
	 */
	public String toJSON() throws IOException {
		StringWriter writer = new StringWriter();
		write(this, writer);
		return writer.toString();
	}
	
	/**
	 * Parses a Geometry from GeoJSON
	 * 
	 * @param json
	 * @return
	 * @throws IOException
	 */
	public static Geometry fromJSON(String json) throws IOException {
		StringReader reader = new StringReader(json);
		return read(reader);
	}
	
	/**
	 * Reads a FeatureCollection from the given GeoJSON file
	 * 
	 * @param jsonFile
	 * @return
	 * @throws IOException
	 */
	public static Geometry read(File jsonFile) throws IOException {
		Reader reader = new BufferedReader(new FileReader(jsonFile));
		return read(reader);
	}
	
	/**
	 * Reads a Geometry from the given reader
	 * 
	 * @param jsonFile
	 * @return
	 * @throws IOException
	 */
	public static Geometry read(Reader reader) throws IOException {
		if (!(reader instanceof BufferedReader))
			reader = new BufferedReader(reader);
		Geometry ret;
		synchronized (FeatureCollection.gson_default) {
			ret = FeatureCollection.gson_default.fromJson(reader, Geometry.class);
			reader.close();
		}
		return ret;
	}
	
	/**
	 * Writes a Geometry to the given GeoJSON file
	 * 
	 * @param features
	 * @param jsonFile
	 * @throws IOException
	 */
	public static void write(Geometry geometry, File jsonFile) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(jsonFile));
		write(geometry, writer);
		writer.close();
	}
	
	/**
	 * Writes a Geometry to the given writer
	 * 
	 * @param features
	 * @param writer
	 * @throws IOException
	 */
	public static void write(Geometry geometry, Writer writer) throws IOException {
		if (!(writer instanceof BufferedWriter))
			writer = new BufferedWriter(writer);

		synchronized (FeatureCollection.gson_default) {
			FeatureCollection.gson_default.toJson(geometry, Geometry.class, writer);
			writer.flush();
		}
	}

}
