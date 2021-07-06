package org.opensha.commons.geo.json;

import java.io.IOException;
import java.util.List;

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
	
	private Geometry(GeoJSON_Type type) {
		Preconditions.checkState(GeoJSON_Type.GEOM_TYPES.contains(type), "Type is not a valid geometry type: %s", type);
		this.type = type;
	}
	
	public static class LineString extends Geometry {
		
		public final LocationList line;
		
		public LineString(LocationList line) {
			super(GeoJSON_Type.LineString);
			
			this.line = line;
		}
	}
	
	public static class MultiLineString extends Geometry {
		
		public final ImmutableList<LocationList> lines;
		
		public MultiLineString(List<LocationList> lines) {
			super(GeoJSON_Type.MultiLineString);
			
			this.lines = ImmutableList.copyOf(lines);
		}
	}
	
	public static class Point extends Geometry {
		
		public final Location point;
		
		public Point(Location point) {
			super(GeoJSON_Type.Point);
			
			this.point = point;
		}
	}
	
	public static class MultiPoint extends Geometry {
		
		public final LocationList points;
		
		public MultiPoint(LocationList points) {
			super(GeoJSON_Type.MultiPoint);
			
			this.points = points;
		}
	}
	
	public static class Polygon extends Geometry {
		
		public final Region polygon;
		
		public Polygon(Region polygon) {
			super(GeoJSON_Type.Polygon);
			
			this.polygon = polygon;
		}
	}
	
	public static class MultiPolygon extends Geometry {
		
		public final ImmutableList<Region> polygons;
		
		public MultiPolygon(List<Region> polygons) {
			super(GeoJSON_Type.MultiPolygon);
			
			this.polygons = ImmutableList.copyOf(polygons);
		}
	}
	
	public static class GeometryCollection extends Geometry {
		
		public final ImmutableList<Geometry> geometries;
		
		public GeometryCollection(List<Geometry> geometries) {
			super(GeoJSON_Type.GeometryCollection);
			
			this.geometries = ImmutableList.copyOf(geometries);
		}
	}
	
	public static class GeometryAdapter extends TypeAdapter<Geometry> {

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
				
				switch (value.type) {
				case Point:
					serializeLoc(out, ((Point)value).point);
					break;
					
				case MultiPoint:
					out.beginArray();
					for (Location point : ((MultiPoint)value).points)
						serializeLoc(out, point);
					out.endArray();
					break;
					
				case LineString:
					out.beginArray();
					for (Location point : ((LineString)value).line)
						serializeLoc(out, point);
					out.endArray();
					break;
					
				case MultiLineString:
					out.beginArray();
					for (LocationList line : ((MultiLineString)value).lines) {
						out.beginArray();
						for (Location point : line)
							serializeLoc(out, point);
						out.endArray();
					}
					out.endArray();
					break;
					
				case Polygon:
					serializePolygon(out, ((Polygon)value).polygon);
					break;
					
				case MultiPolygon:
					out.beginArray();
					for (Region polygon : ((MultiPolygon)value).polygons)
						serializePolygon(out, polygon);
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
			
			GeoJSON_Type type = null;
			Geometry geometry = null;
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "type":
					type = GeoJSON_Type.valueOf(in.nextString());
					Preconditions.checkState(GeoJSON_Type.GEOM_TYPES.contains(type),
							"Not a valid geometry type: %s", type.name());
					break;
				case "geometries":
					Preconditions.checkState(type != null, "Type must be supplied before geometries array");
					Preconditions.checkState(type == GeoJSON_Type.GeometryCollection,
							"Only GeometryCollections should have a geometries array");
					Preconditions.checkState(geometry == null, "Geometry already defined by geometries array encountered");
					
					ImmutableList.Builder<Geometry> geometries = new ImmutableList.Builder<>();
					in.beginArray();
					while (in.hasNext())
						geometries.add(read(in));
					in.endArray();
					geometry = new GeometryCollection(geometries.build());
					break;
				case "coordinates":
					Preconditions.checkState(type != null, "Type must be supplied before coordinates array");
					Preconditions.checkState(type != GeoJSON_Type.GeometryCollection,
							"GeometryCollections should not have a coordinates array");
					Preconditions.checkState(geometry == null, "Geometry already defined by coordinate array encountered");
					
					switch (type) {
					case Point:
						geometry = new Point(deserializeLoc(in));
						break;
						
					case MultiPoint:
						geometry = new MultiPoint(deserializePoints(in));
						break;
						
					case LineString:
						geometry = new LineString(deserializePoints(in));
						break;
						
					case MultiLineString:
						ImmutableList.Builder<LocationList> lines = new ImmutableList.Builder<>();
						in.beginArray();
						while (in.hasNext())
							lines.add(deserializePoints(in));
						in.endArray();
						geometry = new MultiLineString(lines.build());
						break;
						
					case Polygon:
						geometry = new Polygon(deserializePolygon(in));
						break;
						
					case MultiPolygon:
						ImmutableList.Builder<Region> polys = new ImmutableList.Builder<>();
						in.beginArray();
						while (in.hasNext())
							polys.add(deserializePolygon(in));
						in.endArray();
						geometry = new MultiPolygon(polys.build());
						break;
						
					default:
						throw new IllegalStateException("Unexpected type for a Geometry: "+type);
					
					}
					break;

				default:
					in.skipValue();
					break;
				}
			}
			
			Preconditions.checkNotNull(geometry);
			
			in.endObject();
			return geometry;
		}
		
	}
	
	public static void serializeLoc(JsonWriter out, Location loc) throws IOException {
		if (loc == null) {
			out.nullValue();
			return;
		}
		out.beginArray();
		out.value(loc.getLongitude());
		out.value(loc.getLatitude());
		if (loc.getDepth() != 0d)
			out.value(-loc.getDepth()*1000d); // elevation, in m
		out.endArray();
	}
	
	public static Location deserializeLoc(JsonReader in) throws IOException {
		if (in.peek() == JsonToken.NULL)
			return null;
		in.beginArray();
		double lon = in.nextDouble();
		double lat = in.nextDouble();
		double depth = 0d;
		if (in.peek() != JsonToken.END_ARRAY)
			depth = -in.nextDouble()*1e-3; // elev in m -> depth in km
		in.endArray();
		return new Location(lat, lon, depth);
	}
	
	private static LocationList deserializePoints(JsonReader in) throws IOException {
		LocationList points = new LocationList();
		in.beginArray();
		while (in.hasNext())
			points.add(deserializeLoc(in));
		in.endArray();
		return points;
	}
	
	private static void serializePolygon(JsonWriter out, Region polygon) throws IOException {
		out.beginArray();
		
		out.beginArray();
		for (Location loc : getPolygonBorder(polygon.getBorder(), false))
			serializeLoc(out, loc);
		out.endArray();
		
		List<LocationList> interiors = polygon.getInteriors();
		if (interiors != null) {
			for (LocationList interior : interiors) {
				out.beginArray();
				for (Location loc : getPolygonBorder(interior, true))
					serializeLoc(out, loc);
				out.endArray();
			}
		}
		
		out.endArray();
	}
	
	private static Region deserializePolygon(JsonReader in) throws IOException {
		in.beginArray();
		
		in.beginArray();
		LocationList border = new LocationList();
		while (in.hasNext())
			border.add(deserializeLoc(in));
		in.endArray();
		
		Region region = new Region(border, null);
		
		while (in.hasNext()) {
			// add interiors
			in.beginArray();
			LocationList interior = new LocationList();
			while (in.hasNext())
				interior.add(deserializeLoc(in));
			in.endArray();
			
			region.addInterior(region);
		}
		
		in.endArray();
		
		return region;
	}
	
	/**
	 * @param border
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

}
