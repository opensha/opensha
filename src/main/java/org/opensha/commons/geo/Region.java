/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with the Southern California
 * Earthquake Center (SCEC, http://www.scec.org) at the University of Southern
 * California and the UnitedStates Geological Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package org.opensha.commons.geo;

import static org.opensha.commons.geo.GeoTools.PI_BY_2;
import static org.opensha.commons.geo.GeoTools.TO_RAD;
import static com.google.common.base.Preconditions.*;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.opensha.commons.data.Named;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.Geometry.MultiPolygon;
import org.opensha.commons.geo.json.Geometry.Polygon;
import org.opensha.commons.metadata.XMLSaveable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * A {@code Region} is a polygonal area on the surface of the earth. The
 * vertices comprising the border of each {@code Region} are stored internally
 * as latitude-longitude coordinate pairs in an {@link Area},
 * facilitating operations such as union, intersect, and contains. Insidedness
 * rules follow those defined in the {@link Shape} interface.<br/>
 * <br/>
 * Some constructors require the specification of a {@link BorderType}. If one
 * wishes to define a geographic {@code Region} that represents a rectangle in a
 * Mercator projection, {@link BorderType#MERCATOR_LINEAR} should be used,
 * otherwise, the border will follow a {@link BorderType#GREAT_CIRCLE} between
 * two points. Over small distances, great circle paths are approximately the
 * same as linear, Mercator paths. Over longer distances, a great circle is a
 * better representation of a line on a globe. Internally, great circles are
 * approximated by multiple straight line segments that have a maximum length of
 * 100km.<br/>
 * <br/>
 * A {@code Region} may also have interior (or negative) areas. Any call to
 * {@link Region#contains(Location)} for a {@code Location} within or on the
 * border of such an interior area will return {@code false}.<br/>
 * <br/>
 * <b><i>NOTE:</i></b> The current implementation does not support regions that
 * are intended to span &#177;180&deg;. Any such regions will wrap the long way
 * around the earth and results are undefined. Regions that encircle either pole
 * are not supported either.<br/>
 * <br/>
 * <b><i>NOTE:</i></b> Due to rounding errors and the use of an {@link Area}
 * internally to define a {@code Region}'s border,
 * {@link Region#contains(Location)} may not always return the expected result
 * near a border. See {@link Region#contains(Location)} for further details.<br/>
 * 
 * 
 * @author Peter Powers
 * @version $Id: Region.java 10313 2013-09-24 21:50:21Z kmilner $
 * @see Area
 * @see BorderType
 */
@JsonAdapter(Region.Adapter.class)
public class Region implements Serializable, XMLSaveable, Named {

	private static final long serialVersionUID = 1L;

	// although border vertices can be accessed by path-iterating over
	// area, an immutable list is stored for convenience
	private LocationList border;

	// interior region list; may remain null
	private ArrayList<LocationList> interiors;

	// Internal representation of region
	Area area;

	// Default angle used to subdivide a circular region: 10 deg
	private static final double WEDGE_WIDTH = 10;

	// Default segment length for great circle splitting: 100km
	private static final double GC_SEGMENT = 100;

	public final static String XML_METADATA_NAME = "Region";
	public final static String XML_METADATA_OUTLINE_NAME = "OutlineLocations";

	protected final static String NAME_DEFAULT = "Unnamed Region";
	private String name = NAME_DEFAULT;

	/* empty constructor for internal use */
	private Region() {}

	/**
	 * Initializes a <code>Region</code> from a pair of <code>Location </code>s.
	 * When viewed in a Mercator projection, the <code>Region</code> will be a
	 * rectangle. If either both latitude or both longitude values in the
	 * <code>Location</code>s are the same, an exception is thrown.
	 * 
	 * <p><b>Note:</b> Internally, the size of the region is expanded by a very
	 * small value (~1m) to ensure that calls to
	 * {@link Region#contains(Location)} for any <code>Location</code> on the
	 * north or east border of the region will return <code>true</code> and that
	 * any double precision rounding issues do not clip the south and west
	 * borders (e.g. 45.0 may be interpreted as 44.9999...). See also the rules
	 * governing insidedness in the {@link Shape} interface.</p>
	 * 
	 * @param loc1 the first <code>Location</code>
	 * @param loc2 the second <code>Location</code>
	 * @throws IllegalArgumentException if the latitude or longitude values in
	 *         the <code>Location</code>s provided are the same
	 * @throws NullPointerException if either <code>Location</code> argument is
	 *         <code>null</code>
	 */
	public Region(Location loc1, Location loc2) {
		
		checkNotNull(loc1, "Supplied location (1) is null");
		checkNotNull(loc1, "Supplied location (2) is null");
		
		double lat1 = loc1.getLatitude();
		double lat2 = loc2.getLatitude();
		double lon1 = loc1.getLongitude();
		double lon2 = loc2.getLongitude();
		
		checkArgument(lat1 != lat2, "Input lats cannot be the same");
		checkArgument(lon1 != lon2, "Input lons cannot be the same");

		LocationList ll = new LocationList();
		double minLat = Math.min(lat1,lat2);
		double minLon = Math.min(lon1,lon2);
		double maxLat = Math.max(lat1,lat2);
		double maxLon = Math.max(lon1,lon2);
		double offset = LocationUtils.TOLERANCE;
		// ternaries prevent exceedance of max lat-lon values 
		maxLat += (maxLat <= 90.0-offset) ? offset : 0.0;
		maxLon += (maxLon <= 180.0-offset) ? offset : 0.0;
		minLat -= (minLat >= -90.0+offset) ? offset : 0.0;
		minLon -= (minLon >= -180.0+offset) ? offset : 0.0;
		ll.add(new Location(minLat, minLon));
		ll.add(new Location(minLat, maxLon));
		ll.add(new Location(maxLat, maxLon));
		ll.add(new Location(maxLat, minLon));
		
		initBorderedRegion(ll, BorderType.MERCATOR_LINEAR);
	}

	/**
	 * Initializes a {@code Region} from a list of border locations. The border
	 * type specifies whether lat-lon values are treated as points in an
	 * orthogonal coordinate system or as connecting great circles. The border
	 * {@code LocationList} does not need to repeat the first {@code Location}
	 * at the end of the list.
	 * 
	 * @param border {@code Locations}
	 * @param type the {@link BorderType} to use when initializing; a
	 *        {@code null} value defaults to {@code BorderType.MERCATOR_LINEAR}
	 * @throws IllegalArgumentException if the {@code border} defines a
	 *         {@code Region} that is empty or consists of more than a single
	 *         closed path.
	 * @throws NullPointerException if the {@code border} is {@code null}
	 * @throws IllegalArgumentException if the border
	 */
	public Region(LocationList border, BorderType type) {
		checkNotNull(border, "Supplied border is null");
		checkArgument(border.size() >= 3,
			"Supplied border must have at least 3 vertices");
		if (type == null) type = BorderType.MERCATOR_LINEAR;
		initBorderedRegion(border, type);
	}

	/**
	 * Initializes a circular {@code Region}. Internally, the centerpoint and
	 * radius are used to create a circular region composed of straight line
	 * segments that span 10&deg; wedges.
	 * 
	 * @param center of the circle
	 * @param radius of the circle
	 * @throws IllegalArgumentException if {@code radius} is outside the range 0
	 *         km &lt; {@code radius} &le; 1000 km
	 * @throws NullPointerException if {@code center} is {@code null}
	 */
	public Region(Location center, double radius) {
		checkNotNull(center, "Supplied center Location is null");
		checkArgument((radius > 0 && radius <= 1000),
			"Radius [%s] is out of [0 1000] km range", radius);
		initCircularRegion(center, radius);
	}

	/**
	 * Initializes a {@code Region} as a buffered area around a line.
	 * 
	 * @param line at center of buffered {@code Region}
	 * @param buffer distance from line
	 * @throws NullPointerException if {@code line} is {@code null}
	 * @throws IllegalArgumentException if {@code buffer} is outside the range 0
	 *         km &lt; {@code buffer} &le; 500 km
	 */
	public Region(LocationList line, double buffer) {
		checkNotNull(line, "Supplied LocationList is null");
		checkArgument(!line.isEmpty(), "Supplied LocationList is empty");
		checkArgument((buffer > 0 && buffer <= 500),
			"Buffer [%s] is out of [0 500] km range", buffer);
		initBufferedRegion(line, buffer);
	}

	/**
	 * Initializes a {@code Region} with another {@code Region}. Creates an
	 * exact copy.
	 * 
	 * @param region to use as border for new {@code Region}
	 * @throws NullPointerException if the supplied {@code Region} is null
	 */
	public Region(Region region) {
		// don't use validateRegion() b/c we can accept
		// regions with interiors
		checkNotNull(region, "Supplied Region is null");
		this.name = region.name;
		this.border = region.border.clone();
		this.area = (Area) region.area.clone();
		// internal regions
		if (region.interiors != null) {
			interiors = new ArrayList<LocationList>();
			for (LocationList interior : region.interiors) {
				interiors.add(interior.clone());
			}
		}
	}

	/**
	 * Returns whether the given {@code Location} is inside this {@code Region}.
	 * The determination follows the rules of insidedness defined in the
	 * {@link Shape} interface.<br/>
	 * <br/>
	 * <b><i>NOTE</i></b>: By using an {@link Area} internally to manage this
	 * {@code Region}'s geometry, there are instances where rounding errors may
	 * cause {@code contains(Location)} to yeild unexpected results. For
	 * instance, although a {@code Region}'s southernmost point might be
	 * initially defined as 40.0&#176;, the internal {@code Area} may return
	 * 40.0000000000001 on a call to {@code getMinLat()} and calls to
	 * {@code contains(new Location(40,*))} will return false. <br/>
	 * 
	 * @param loc the {@code Location} to test
	 * @return {@code true} if the {@code Location} is inside the Region,
	 *         {@code false} otherwise
	 * @see java.awt.Shape
	 */
	public boolean contains(Location loc) {
		return area.contains(loc.getLongitude(), loc.getLatitude());
	}

	/**
	 * Tests whether another {@code Region} is entirely contained within this
	 * {@code Region}.
	 * 
	 * @param region to check
	 * @return {@code true} if this contains the {@code Region}; {@code false}
	 *         otherwise
	 */
	public boolean contains(Region region) {
		Area areaUnion = (Area) area.clone();
		areaUnion.add(region.area);
		return area.equals(areaUnion);
	}

	/**
	 * Returns whether this {@code Region} is rectangular in shape when
	 * represented in a Mercator projection.
	 * 
	 * @return {@code true} if rectangular, {@code false} otherwise
	 */
	public boolean isRectangular() {
		return area.isRectangular();
	}

	/**
	 * Adds an interior (donut-hole) to this {@code Region}. Any call to
	 * {@link Region#contains(Location)} for a {@code Location} within this
	 * interior area will return {@code false}. Any interior {@code Region} must
	 * lie entirely inside this {@code Region}. Moreover, any interior may not
	 * overlap or enclose any existing interior region. Internally, the border
	 * of the supplied {@code Region} is copied and stored as an unmodifiable
	 * {@code List}. No reference to the supplied {@code Region} is retained.
	 * 
	 * @param region to use as an interior or negative space
	 * @throws NullPointerException if the supplied {@code Region} is
	 *         {@code null}
	 * @throws IllegalArgumentException if the supplied {@code Region} is not
	 *         entirly contained within this {@code Region}
	 * @throws IllegalArgumentException if the supplied {@code Region} is not
	 *         singular (i.e. already has an interior itself)
	 * @throws IllegalArgumentException if the supplied {@code Region} overlaps
	 *         any existing interior {@code Region}
	 * @see Region#getInteriors()
	 */
	public void addInterior(Region region) {
		validateRegion(region); // test for non-singularity or null
		checkArgument(contains(region),
			"Region must completely contain supplied interior Region");

		LocationList newInterior = region.border.clone();
		// ensure no overlap with existing interiors
		Area newArea = createArea(newInterior);
		if (interiors != null) {
			for (LocationList interior : interiors) {
				Area existing = createArea(interior);
				existing.intersect(newArea);
				checkArgument(existing.isEmpty(),
					"Supplied interior Region overlaps existing interiors");
			}
		} else {
			interiors = new ArrayList<LocationList>();
		}

		interiors.add(newInterior.unmodifiableList());
		area.subtract(region.area);
	}

	/**
	 * Returns an unmodifiable {@link List} view of the internal
	 * {@code LocationList}s (also unmodifiable) of points that decribe the
	 * interiors of this {@code Region}, if such exist. If no interior is
	 * defined, the method returns {@code null}.
	 * 
	 * @return a {@code List} the interior {@code LocationList}s or {@code null}
	 *         if no interiors are defined
	 */
	public List<LocationList> getInteriors() {
		return (interiors != null) ? Collections.unmodifiableList(interiors)
			: null;
	}

	/**
	 * Returns an unmodifiable {@link List} view of the internal
	 * {@code LocationList} of points that decribe the border of this
	 * {@code Region}.
	 * 
	 * @return the immutable border {@code LocationList}
	 */
	public LocationList getBorder() {
		return border.unmodifiableList();
	}

	/**
	 * Returns a deep copy of the internal {@link Area} used to manage this
	 * {@code Region}. The method is named as such to avoid confusion with
	 * {@link #getArea()}.
	 * @return a copy of the {@code Area} used by this {@code Region}
	 */
	public Area getShape() {
		return (Area) area.clone();
	}

	/**
	 * Returns a flat-earth estimate of the area of this region in
	 * km<sup>2</sup>. Method uses the center of this {@code Region}'s bounding
	 * polygon as the origin of an orthogonal coordinate system. This method is
	 * not appropriate for use with very large {@code Region}s where the
	 * curvature of the earth is more significant.
	 * @return the area of this region in km<sup>2</sup>
	 */
	public double getExtent() {
		// set origin as center of region bounds
		Rectangle2D rRect = area.getBounds2D();
		Location origin = new Location(rRect.getCenterY(), rRect.getCenterX());
		// compute orthogonal coordinates in km
		List<Double> xs = new ArrayList<>();
		List<Double> ys = new ArrayList<>();
		for (Location loc : border) {
			LocationVector v = LocationUtils.vector(origin, loc);
			double az = v.getAzimuthRad();
			double d = v.getHorzDistance();
			xs.add(Math.sin(az) * d);
			ys.add(Math.cos(az) * d);
		}
		// repeat first point
		xs.add(xs.get(0));
		ys.add(ys.get(0));
		double area = computeArea(Doubles.toArray(xs), Doubles.toArray(ys));
		if (interiors != null) {
			// subtract interiors
			for (LocationList interiorBorder : interiors) {
				xs.clear();
				ys.clear();
				for (Location loc : interiorBorder) {
					LocationVector v = LocationUtils.vector(origin, loc);
					double az = v.getAzimuthRad();
					double d = v.getHorzDistance();
					xs.add(Math.sin(az) * d);
					ys.add(Math.cos(az) * d);
				}
				// repeat first point
				xs.add(xs.get(0));
				ys.add(ys.get(0));
				area -= computeArea(Doubles.toArray(xs), Doubles.toArray(ys));
			}
		}
		return area;
	}

	public static void main(String[] args) {
		// Region r = new CaliforniaRegions.RELM_TESTING();
//		Region r = new Region(new Location(20, 20), new Location(21, 21));
//		System.out.println(r.getExtent());
		
		LocationList border = new LocationList();
		border.add(new Location(34, -118));
		border.add(new Location(34.5, -118.5));
		border.add(new Location(35, -118));
		border.add(new Location(34.5, -117.5));
		border.add(new Location(34, -118));
		Region reg = new Region(border, BorderType.MERCATOR_LINEAR);
		
		border = border.clone();
		border.reverse();
		Region reg2 = new Region(border, BorderType.MERCATOR_LINEAR);
		
		System.out.println(reg.getBorder().get(1));
		System.out.println(reg2.getBorder().get(1));
		System.out.println();
		System.out.println(reg.getExtent());
		System.out.println(reg2.getExtent());
		System.out.println();
		System.out.println(reg.equalsRegion(reg2));
		System.out.println();
		System.out.println(reg.area.isSingular());
		System.out.println(reg2.area.isSingular());
		System.out.println();
		System.out.println(reg.area.isEmpty());
		System.out.println(reg2.area.isEmpty());
	}

	/*
	 * Computes the area of a simple polygon; no data validation is performed
	 * except ensuring that all coordinates are positive.
	 */
	private static double computeArea(double[] xs, double[] ys) {
		positivize(xs);
		positivize(ys);
		double area = 0;
		for (int i = 0; i < xs.length - 1; i++) {
			area += xs[i] * ys[i + 1] - xs[i + 1] * ys[i];
		}
		return Math.abs(area) / 2;
	}

	/* Ensures positivity of values by adding Math.abs(min) if min < 0. */
	private static void positivize(double[] v) {
		double min = Doubles.min(v);
		if (min >= 0) return;
		min = Math.abs(min);
		for (int i = 0; i < v.length; i++) {
			v[i] += min;
		}
	}

	/**
	 * Compares the geometry of this {@code Region} to another and returns
	 * {@code true} if they are the same, ignoring any differences in name. Use
	 * {@code Region.equals(Object)} to include name comparison.
	 * 
	 * @param r the {@code Regions} to compare
	 * @return {@code true} if this {@code Region} has the same geometry as the
	 *         supplied {@code Region}, {@code false} otherwise
	 * @see Region#equals(Object)
	 */
	public boolean equalsRegion(Region r) {
		// note that Area.equals() does not override Object.equals()
		return area.equals(r.area);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Region)) return false;
		Region r = (Region) obj;
		if (!getName().equals(r.getName())) return false;
		return equalsRegion(r);
	}

	@Override
	public int hashCode() {
		return border.hashCode() ^ name.hashCode();
	}

	/**
	 * Returns an exact, independent copy of this {@code Region}.
	 * @return a copy of this {@code Region}
	 */
	@Override
	public Region clone() {
		return new Region(this);
	}

	/**
	 * Returns the minimum latitude in this {@code Region}'s border.
	 * @return the minimum latitude
	 */
	public double getMinLat() {
		return area.getBounds2D().getMinY();
	}

	/**
	 * Returns the maximum latitude in this {@code Region}'s border.
	 * @return the maximum latitude
	 */
	public double getMaxLat() {
		return area.getBounds2D().getMaxY();
	}

	/**
	 * Returns the minimum longitude in this {@code Region}'s border.
	 * @return the minimum longitude
	 */
	public double getMinLon() {
		return area.getBounds2D().getMinX();
	}

	/**
	 * Returns the maximum longitude in this {@code Region}'s border.
	 * @return the maximum longitude
	 */
	public double getMaxLon() {
		return area.getBounds2D().getMaxX();
	}

	/**
	 * Returns the minimum horizonatal distance (in km) between the border of
	 * this {@code Region} and the {@code Location} specified. If the given
	 * {@code Location} is inside the {@code Region}, the method returns 0. The
	 * distance algorithm used only works well at short distances (e.g. &lteq;
	 * 250 km).
	 * 
	 * @param loc the Location to compute a distance to
	 * @return the minimum distance between this {@code Region} and a point
	 * @throws NullPointerException if supplied location is {@code null}
	 * @see LocationList#minDistToLine(Location)
	 * @see LocationUtils#distanceToLineSegmentFast(Location, Location, Location)
	 */
	public double distanceToLocation(Location loc) {
		checkNotNull(loc, "Supplied location is null");
		if (contains(loc)) return 0;
		double min = border.minDistToLine(loc);
		// check the segment defined by the last and first points
		// take abs because value may be negative; i.e. value to left of line
		double temp = Math.abs(LocationUtils.distanceToLineSegmentFast(
			border.get(border.size() - 1), border.get(0), loc));
		return Math.min(temp, min);
	}

	@Override
	public String getName() {
		return name;
	}

	/**
	 * Set the name for this {@code Region}.
	 * @param name for the {@code Region}
	 */
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		String str = "Region\n" + "\tMinLat: " + this.getMinLat() + "\n" +
			"\tMinLon: " + this.getMinLon() + "\n" + "\tMaxLat: " +
			this.getMaxLat() + "\n" + "\tMaxLon: " + this.getMaxLon();
		return str;
	}

	@Override
	public Element toXMLMetadata(Element root) {
		return toXMLMetadata(root, Region.XML_METADATA_NAME);
	}

	public Element toXMLMetadata(Element root, String elemName) {
		Element xml = root.addElement(elemName);
		xml = border.toXMLMetadata(xml);
		String name = this.name;
		if (name == null) name = "";
		xml.addAttribute("name", name);
		return root;
	}

	/**
	 * Initializes a new {@code Region} from stored metadata.
	 * @param e metadata element
	 * @return a {@code Region}
	 */
	public static Region fromXMLMetadata(Element e) {
		LocationList list = LocationList.fromXMLMetadata(e
			.element(LocationList.XML_METADATA_NAME));
		Region region = new Region(list, BorderType.MERCATOR_LINEAR);
		Attribute nameAtt = e.attribute("name");
		if (nameAtt != null) {
			String name = nameAtt.getValue();
			if (name.length() > 0) region.setName(name);
		}
		return region;
	}

	/**
	 * Convenience method to return a {@code Region} spanning the entire globe.
	 * @return a {@code Region} extending from -180&#176; to +180&#176;
	 *         longitude and -90&#176; to +90&#176; latitude
	 */
	public static Region getGlobalRegion() {
		LocationList gll = new LocationList();
		gll.add(new Location(-90, -180));
		gll.add(new Location(-90, 180));
		gll.add(new Location(90, 180));
		gll.add(new Location(90, -180));
		return new Region(gll, BorderType.MERCATOR_LINEAR);
	}

	/**
	 * Returns the intersection of two {@code Region}s. If the {@code Region}s
	 * do not overlap, the method returns {@code null}.
	 * 
	 * @param r1 the first {@code Region}
	 * @param r2 the second {@code Region}
	 * @return a new {@code Region} defined by the intersection of {@code r1}
	 *         and {@code r2} or {@code null} if they do not overlap
	 * @throws IllegalArgumentException if either supplied {@code Region} is not
	 *         a single closed {@code Region}
	 * @throws NullPointerException if either supplied {@code Region} is
	 *         {@code null}
	 */
	public static Region intersect(Region r1, Region r2) {
		validateRegion(r1);
		validateRegion(r2);
		Area newArea = (Area) r1.area.clone();
		newArea.intersect(r2.area);
		if (newArea.isEmpty()) return null;
		Region newRegion = new Region();
		newRegion.area = newArea;
		newRegion.border = Region.createBorder(newArea, true);
		return newRegion;
	}
	
	// subtraction is tricky, this flag enables easier debugging
	private static final boolean SUBTRACT_DEBUG = false;

	/**
	 * Returns the first {@code Region} subtracted by the second, or null if no they don't intersect.
	 * As subtraction can result in nonsingularity, an array of {@code Region}s is returned.
	 * 
	 * @param minuend the minuend, from which a {@code Region} will be subtracted
	 * @param subtrahend the subtrahend {@code Region} which is to be subtracted
	 * @return array of resultant {@code Region} defined by the subtraction of {@code subtrahend}
	 *         from {@code minuend}, or {@code null} if no intersection. If the subtraction results in an empty area,
	 *         a zero-length array is returned.
	 * @throws NullPointerException if either supplied {@code Region} is
	 *         {@code null}
	 */
	public static Region[] subtract(Region minuend, Region subtrahend) {
		if (SUBTRACT_DEBUG) System.out.println("Subtracting region");
		
		// first test for intersection
		Area newArea = (Area) minuend.area.clone();
		newArea.intersect(subtrahend.area);
		if (SUBTRACT_DEBUG) System.out.println("Intersect? "+!newArea.isEmpty());
		if (newArea.isEmpty()) return null;
		
		// now subtract
		newArea = (Area) minuend.area.clone();
		newArea.subtract(subtrahend.area);
		if (SUBTRACT_DEBUG) System.out.println("Superceded? "+newArea.isEmpty());
		if (newArea.isEmpty()) return new Region[0];
		List<Area> solids = new ArrayList<>();
		List<Area> holes =new ArrayList<>();
		splitArea(newArea, solids, holes);
		if (solids.isEmpty()) {
			// this happens when the the area of the subtracted region is zero
			if (!holes.isEmpty()) {
				// see if these are those trivial numerical precision issue holes
				List<Region> holeRegions = new ArrayList<>();
				for (Area area : holes) {
					Region hole = new Region();
					hole.area = area;
					hole.border = Region.createBorder(area, true);
					holeRegions.add(hole);
				}
				if (!areHolesSignificant(minuend, holeRegions)) {
					if (SUBTRACT_DEBUG) System.out.println("Unmatched hole(s) is sufficiently tiny, ignoring");
					holes.clear();
				} else if (SUBTRACT_DEBUG) System.out.println("Unmatched hole is significant, failing");
			}
			Preconditions.checkState(holes.isEmpty(), "Have 0 solids but %s holes", holes.size());
			return new Region[0];
		}
		if (SUBTRACT_DEBUG) System.out.println("Creating regions for "+solids.size()+" solids and "+holes.size()+" holes");
		List<Region> regions = new ArrayList<>();
		for (Area area : solids) {
			Region region = new Region();
			region.area = area;
			region.border = Region.createBorder(area, true);
			if (region.border.isEmpty()) {
				if (SUBTRACT_DEBUG) System.out.println("That solid was empty, skipping");
			} else {
				if (SUBTRACT_DEBUG) System.out.println("Built solid with extent="+region.getExtent());
				regions.add(region);
			}
		}
		// now process any holes
		for (Area area : holes) {
			Region hole = new Region();
			hole.area = area;
			hole.border = Region.createBorder(area, true);
			if (hole.border.isEmpty()) {
				if (SUBTRACT_DEBUG) System.out.println("That solid was empty, skipping");
				continue;
			} else {
				if (SUBTRACT_DEBUG) System.out.println("Built hole with extent="+hole.getExtent());
			}
			// find the region which contains it
			boolean found = false;
			for (Region region : regions) {
				if (region.contains(hole)) {
					found = true;
					region.addInterior(hole);
					break;
				}
			}
			if (!found) {
				// not found but might be insiginificant, in which case we can skip
				if (!isHoleSignificant(minuend, hole)) {
					if (SUBTRACT_DEBUG) System.out.println("Unmatched hole is sufficiently tiny, skipping");
					found = true;
				} else if (SUBTRACT_DEBUG) System.out.println("Unmatched hole is significant, failing");
			}
			Preconditions.checkState(found, "No solid region found which contains hole!");
		}
		
		Region[] ret = regions.toArray(new Region[0]);
		if (SUBTRACT_DEBUG) System.out.println("Returning "+ret.length+" regions");
		
		return ret;
	}
	
	private static boolean isHoleSignificant(Region minuend, Region hole) {
		return areHolesSignificant(minuend, Lists.newArrayList(hole));
	}
	
	private static boolean areHolesSignificant(Region minuend, List<Region> holes) {
		// sometimes numerical precision issues lead to an extra "hole" with a crazy small area that is not contained within
		// a region.
		
		double minuendExtent = minuend.getExtent();
		double holeExtent = 0d;
		for (Region hole : holes)
			if (!hole.border.isEmpty())
				holeExtent += hole.getExtent();
		double ratio = holeExtent/minuendExtent;
		if (SUBTRACT_DEBUG) System.out.println("We have no solids but "+holes.size()+" hole(s). "
				+ "Hole area ratio = "+holeExtent+"/"+minuendExtent+" = "+ratio);
		// call it significant if it's a significant fraction (1/1000) of the minuend area, or it's smaller than a micrometer
		return ratio > 1e-3 && holeExtent > 1e-0;
	}
	
	/**
	 * Recursively splits an area into solids and holes
	 * @param multiArea area which need not be singular
	 * @param solids list of solids to be populated
	 * @param holes list of holes to be populated
	 */
	private static void splitArea(Area multiArea, List<Area> solids, List<Area> holes) {
		if (SUBTRACT_DEBUG) System.out.println("Splitting an area");
		if (SUBTRACT_DEBUG) System.out.println("Splitting!");
		PathIterator iter = multiArea.getPathIterator(null);
		if (SUBTRACT_DEBUG) System.out.println("Winding rule: "+iter.getWindingRule());
		Path2D.Double poly = new Path2D.Double(Path2D.WIND_EVEN_ODD);
		
		// these are used to determine the direction of the path. if it's clockwise, directionTest will be positive
		// which means that this is a solid. if it's counter-clockwise, directionTest will be negative indicating a hole
		double[] moveToPt = null;
		double[] prevPoint = null;
		double directionTest = 0d;
		int index = 0;
		while(!iter.isDone()) {
			double[] point = new double[6]; //x,y
			int type = iter.currentSegment(point); 
			if(type == PathIterator.SEG_MOVETO) {
				if (SUBTRACT_DEBUG) System.out.println("Area "+index+": SEG_MOVETO\t"+point[0]+"\t"+point[1]);
				poly.moveTo(point[0], point[1]);
				moveToPt = point;
			} else if(type == PathIterator.SEG_CLOSE) {
				if (SUBTRACT_DEBUG) System.out.println("Area "+index+": SEG_CLOSE\t"+point[0]+"\t"+point[1]);
				if (moveToPt != null) {
					// (x2 − x1)(y2 + y1).
					if (SUBTRACT_DEBUG) System.out.println("\t\t"+((moveToPt[0]-prevPoint[0])*(moveToPt[1]+prevPoint[1])));
					directionTest += (moveToPt[0]-prevPoint[0])*(moveToPt[1]+prevPoint[1]);
				}
				boolean hole = directionTest < 0;
				if (SUBTRACT_DEBUG) System.out.println("Direction Test: "+directionTest+"\tHole? "+hole);
				poly.closePath();
				Area area = new Area(poly);
				if (!area.isEmpty()) {
//					System.out.println("Adding area");
					if (!area.isSingular()) {
						// not quite sure why the Area constructor sometimes addes new little patches, but it does
						// this fixes it
						if (SUBTRACT_DEBUG) System.out.println("Re-splitting a split area...");
						splitArea(area, solids, holes);
					} else {
						if (hole)
							holes.add(area);
						else
							solids.add(area);
					}
				} else {
					if (SUBTRACT_DEBUG) System.out.println("Skipping empty area");
				}
				index++;
				poly = new Path2D.Double(Path2D.WIND_EVEN_ODD);
			} else if (type == PathIterator.SEG_LINETO) {
				if (SUBTRACT_DEBUG) System.out.println("Area "+index+": SEG_LINETO\t"+point[0]+"\t"+point[1]);
				poly.lineTo(point[0], point[1]);
				if (prevPoint != null) {
					// (x2 − x1)(y2 + y1).
					if (SUBTRACT_DEBUG) System.out.println("\t\t"+((point[0]-prevPoint[0])*(point[1]+prevPoint[1])));
					directionTest += (point[0]-prevPoint[0])*(point[1]+prevPoint[1]);
				}
			} else {
				throw new IllegalStateException("Unexpected area path type: "+type);
			}
			iter.next();
			prevPoint = point;
		}
	}

	/**
	 * Returns the union of two {@code Region}s. If the {@code Region}s do not
	 * overlap, the method returns {@code null}.
	 * 
	 * @param r1 the first {@code Region}
	 * @param r2 the second {@code Region}
	 * @return a new {@code Region} defined by the union of {@code r1} and
	 *         {@code r2} or {@code null} if they do not overlap
	 * @throws IllegalArgumentException if either supplied {@code Region} is not
	 *         a single closed {@code Region}
	 * @throws NullPointerException if either supplied {@code Region} is
	 *         {@code null}
	 */
	public static Region union(Region r1, Region r2) {
		validateRegion(r1);
		validateRegion(r2);
		Area newArea = (Area) r1.area.clone();
		newArea.add(r2.area);
		if (!newArea.isSingular()) return null;
		Region newRegion = new Region();
		newRegion.area = newArea;
		newRegion.border = Region.createBorder(newArea, true);
		return newRegion;
	}

	/* Validator for geometry operations */
	private static void validateRegion(Region r) {
		checkNotNull(r, "Supplied Region is null");
		checkArgument(r.area.isSingular(), "Region must be singular");
	}

	/*
	 * Creates a java.awt.geom.Area from a LocationList border. This method
	 * throw exceptions if the generated Area is empty or not singular
	 */
	private static Area createArea(LocationList border) {
		Area area = new Area(border.toPath());
		// final checks on area generated, this is redundant for some
		// constructors that perform other checks on inputs
		checkArgument(!area.isEmpty(), "Internally computed Area is empty");
		checkArgument(area.isSingular(),
			"Internally computed Area is not a single closed path");

		return area;
	}

	/*
	 * Initialize a region from a list of border locations. Internal
	 * java.awt.geom.Area is generated from the border.
	 */
	private void initBorderedRegion(LocationList border, BorderType type) {

		// first remove last point in list if it is the same as
		// the first point
		int lastIndex = border.size() - 1;
		if (border.get(lastIndex).equals(border.get(0))) {
			border.remove(lastIndex);
		}

		if (type.equals(BorderType.GREAT_CIRCLE)) {
			LocationList gcBorder = new LocationList();
			// process each border pair [start end]; so that the entire
			// border is traversed, set the first 'start' Location as the
			// last point in the gcBorder
			Location start = border.get(border.size() - 1);
			for (int i = 0; i < border.size(); i++) {
				gcBorder.add(start);
				Location end = border.get(i);
				double distance = LocationUtils.horzDistance(start, end);
				// subdivide as necessary
				while (distance > GC_SEGMENT) {
					// find new Location, GC_SEGMENT km away from start
					double azRad = LocationUtils.azimuthRad(start, end);
					Location segLoc = LocationUtils.location(start, azRad,
						GC_SEGMENT);
					gcBorder.add(segLoc);
					start = segLoc;
					distance = LocationUtils.horzDistance(start, end);
				}
				start = end;
			}
			this.border = gcBorder.clone();
		} else {
			this.border = border.clone();
		}
		area = createArea(this.border);
	}

	/*
	 * Initialize a circular region by creating an circular border of shorter
	 * straight line segments. Internal java.awt.geom.Area is generated from the
	 * border.
	 */
	private void initCircularRegion(Location center, double radius) {
		border = createLocationCircle(center, radius);
		area = createArea(border);
	}

	/*
	 * Initialize a buffered region by creating box areas of 2x buffer width
	 * around each line segment and circle areas around each vertex and union
	 * all of them. The border is then be derived from the Area.
	 */
	private void initBufferedRegion(LocationList line, double buffer) {
		// init an empty Area
		area = new Area();
		// for each point segment, create a circle area
		Location prevLoc = null;
		for (Location loc : line) {
			// starting out only want to create circle area for first point
			if (area.isEmpty()) {
				area.add(createArea(createLocationCircle(loc, buffer)));
				prevLoc = loc;
				continue;
			}
			area.add(createArea(createLocationBox(prevLoc, loc, buffer)));
			area.add(createArea(createLocationCircle(loc, buffer)));
			prevLoc = loc;
		}
		border = createBorder(area, true);
	}

	/*
	 * Creates a LocationList border from a java.awt.geom.Area. The clean flag
	 * is used to post-process list to remove repeated identical locations,
	 * which are common after intersect and union operations.
	 */
	private static LocationList createBorder(Area area, boolean clean) {
		PathIterator pi = area.getPathIterator(null);
		LocationList ll = new LocationList();
		// placeholder vertex for path iteration
		double[] vertex = new double[6];
		while (!pi.isDone()) {
			int type = pi.currentSegment(vertex);
			double lon = vertex[0];
			double lat = vertex[1];
			// skip the final closing segment which just repeats
			// the previous vertex but indicates SEG_CLOSE
			if (type != PathIterator.SEG_CLOSE) {
				ll.add(new Location(lat, lon));
			}
			pi.next();
		}

		if (clean) {
			LocationList llClean = new LocationList();
			Location prev = ll.get(ll.size() - 1);
			for (Location loc : ll) {
				if (loc.equals(prev)) continue;
				llClean.add(loc);
				prev = loc;
			}
			ll = llClean;
		}
		return ll;
	}

	/*
	 * Utility method returns a LocationList that approximates the circle
	 * represented by the center location and radius provided.
	 */
	private static LocationList createLocationCircle(Location center,
			double radius) {

		LocationList ll = new LocationList();
		for (double angle = 0; angle < 360; angle += WEDGE_WIDTH) {
			ll.add(LocationUtils.location(center, angle * TO_RAD, radius));
		}
		return ll;
	}

	/*
	 * Utility method returns a LocationList representing a box that is as long
	 * as the line between p1 and p2 and extends on either side of that line
	 * some 'distance'.
	 */
	private static LocationList createLocationBox(Location p1, Location p2,
			double distance) {

		// get the azimuth and back-azimuth between the points
		double az12 = LocationUtils.azimuthRad(p1, p2);
		double az21 = LocationUtils.azimuthRad(p2, p1); // back azimuth

		// add the four corners
		LocationList ll = new LocationList();
		// corner 1 is azimuth p1 to p2 - 90 from p1
		ll.add(LocationUtils.location(p1, az12 - PI_BY_2, distance));
		// corner 2 is azimuth p1 to p2 + 90 from p1
		ll.add(LocationUtils.location(p1, az12 + PI_BY_2, distance));
		// corner 3 is azimuth p2 to p1 - 90 from p2
		ll.add(LocationUtils.location(p2, az21 - PI_BY_2, distance));
		// corner 4 is azimuth p2 to p1 + 90 from p2
		ll.add(LocationUtils.location(p2, az21 + PI_BY_2, distance));

		return ll;
	}

	// Serialization methods required for non-serializable Area
	private void writeObject(ObjectOutputStream os) throws IOException {
		os.writeObject(name);
		os.writeObject(border);
		os.writeObject(interiors);
	}

	@SuppressWarnings("unchecked")
	private void readObject(ObjectInputStream is) throws IOException,
			ClassNotFoundException {
		name = (String) is.readObject();
		border = (LocationList) is.readObject();
		interiors = (ArrayList<LocationList>) is.readObject();
		area = createArea(border);
		if (interiors != null) {
			for (LocationList interior : interiors) {
				Area intArea = createArea(interior);
				area.subtract(intArea);
			}
		}
	}
	
	/*
	 * GeoJSON related methods
	 */
	
	/**
	 * Converts this region to a GeoJSON feature object for serialization
	 * 
	 * @return
	 */
	public Feature toFeature() {
		String name = getName();
		if (name != null && name.equals(NAME_DEFAULT))
			name = null;
		return new Feature(name, new Polygon(this), null);
	}
	
	/**
	 * Converts GeoJSON feature object back to a region.
	 * 
	 * @return
	 */
	public static Region fromFeature(Feature feature) {
		Preconditions.checkNotNull(feature.geometry, "Feature is missing geometry");
		Preconditions.checkState(feature.geometry instanceof Polygon || feature.geometry instanceof MultiPolygon,
				"Unexpected geometry type for Region: %s", feature.geometry.type);
		if (feature.geometry instanceof MultiPolygon) {
			List<Region> list = ((MultiPolygon)feature.geometry).asRegions();
			Preconditions.checkState(list.size() == 1, "Must have exactly 1 polygon, have %s", list.size());
			return list.get(0);
		}
		Region region = ((Polygon)feature.geometry).asRegion();
		if (feature.id != null)
			region.setName(feature.id.toString());
		return region;
	}
	
	public static class Adapter extends TypeAdapter<Region> {
		
		private Feature.FeatureAdapter featureAdapter;
		
		public Adapter() {
			featureAdapter = new Feature.FeatureAdapter();
		}

		@Override
		public void write(JsonWriter out, Region value) throws IOException {
			if (value == null)
				out.nullValue();
			else
				featureAdapter.write(out, value.toFeature());
		}

		@Override
		public Region read(JsonReader in) throws IOException {
			Feature feature = featureAdapter.read(in);
			return fromFeature(feature);
		}
		
	}

}
