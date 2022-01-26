package org.opensha.commons.geo;

import static com.google.common.base.Preconditions.*;

import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.math3.util.Precision;
import org.dom4j.Element;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.commons.geo.json.Geometry.GeometryCollection;
import org.opensha.commons.geo.json.Geometry.MultiPoint;
import org.opensha.commons.geo.json.Geometry.MultiPolygon;
import org.opensha.commons.geo.json.Geometry.Point;
import org.opensha.commons.geo.json.Geometry.Polygon;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * A <code>GriddedRegion</code> is a <code>Region</code> that has been
 * discretized in latitude and longitude. Each node in a gridded region
 * represents a small area that is some number of degrees in width and height
 * and is identified by a unique {@link Location} at the geographic (lat-lon)
 * center of the node. <img style="padding: 30px 40px; float: right;"
 * src="{@docRoot} /img/gridded_regions_border.jpg"/> In the adjacent figure,
 * the heavy black line marks the border of the <code>Region</code> . The light
 * gray dots mark the <code>Location</code>s of nodes outside the region, and
 * black dots those inside the region. The dashed grey line marks the border,
 * inside which, a <code>Location</code> will be associated with a grid node.
 * See {@link GriddedRegion#indexForLocation(Location)} for more details on
 * rules governing whether a grid node is inside a region and whether a
 * <code>Location</code> will be associated with a grid node.<br/>
 * <br/>
 * A <code>GriddedRegion</code> may be initialized several ways (e.g. as a
 * circle, an area of uniform degree-width and -height, or a buffer around a
 * linear feature). See constructor documentation for illustrative examples.
 * Each constructor comes in two formats, one that takes a single 'spacing'
 * value, and one that takes two spacing values, one each for latitude and
 * longitude.<br/>
 * <br/>
 * The <code>Location</code>s of the grid nodes are indexed internally in order
 * of increasing longitude then latitude starting with the node at the lowest
 * latitude and longitude in the region. <code>GriddedRegion</code>s are
 * iterable as a shorthand for <code>getNodeList().iterator()</code>. <br/>
 * <br/>
 * To ensure grid nodes fall on specific lat-lon values, all constructors take
 * an anchor <code>Location</code> argument. This location can be anywhere in-
 * or outside the region to be gridded. If the region contains the anchor
 * location, the anchor will coincide with a grid node. For example, given a
 * grid spacing of 1&deg; and an anchor <code>Location</code> of 22.1&deg;N
 * -134.7&deg;W, grid nodes within any region will fall at whole valued
 * latitudes + 0.1&deg; and longitudes - 0.7&deg;. If an anchor
 * <code>Location</code> is <code>null</code>, it is automatically set as the
 * Location defined by the minimum latitude and longitude of the region's
 * border.<br/>
 * <br/>
 * <a name="note"></a> <b><i>NOTE:</i></b> Due to rounding errors and the use of
 * an {@link Area} internally to define a <code>Region</code>'s border,
 * {@link Region#contains(Location)} may not always return the expected result
 * near a border. See {@link Region#contains(Location)} for further details. For
 * a <code>GriddedRegion</code>, this results in values returned by calls
 * {@link #getMinGridLat()} etc. for which there may not be any grid nodes. To
 * guarantee node coverage for a <code>GriddedRegion</code>, say for eventual
 * map output, 'best-practice' dictates expanding a region slightly.
 * 
 * @author Nitin Gupta
 * @author Vipin Gupta
 * @author Peter Powers
 * @version $Id: GriddedRegion.java 11540 2017-07-05 16:45:49Z kmilner $
 * @see Region
 */
@JsonAdapter(GriddedRegion.Adapter.class)
public class GriddedRegion extends Region implements Iterable<Location> {

	private static final long serialVersionUID = 1L;

	public final static String XML_METADATA_NAME = "evenlyGriddedGeographicRegion";
	public final static String XML_METADATA_GRID_SPACING_NAME = "spacing";
	public final static String XML_METADATA_ANCHOR_NAME = "anchor";
	public final static String XML_METADATA_NUM_POINTS_NAME = "numPoints";

	/** Convenience reference for an anchor at (0&#176;, 0&#176;). */
	public final static Location ANCHOR_0_0 = new Location(0, 0);

	// grid range data
	private double minGridLat, minGridLon, maxGridLat, maxGridLon;
	private int numLonNodes, numLatNodes;

	// the lat-lon arrays of node centers
	private double[] lonNodeCenters;
	private double[] latNodeCenters;

	// Location at lower left corner of region bounding rect
	private Location anchor;

	// lookup array for grid nodes; has length of master grid spanning
	// region bounding box; all nodes outside region have values of -1;
	// all valid nodes point to position in nodeList; gridIndices increase
	// across and then up
	private int[] gridIndices;

	// list of nodes
	private LocationList nodeList;

	// grid data
	private double latSpacing;
	private double lonSpacing;
	private int nodeCount;

	// private int gridSize;

	/**
	 * Initializes a <code>GriddedRegion</code> from a pair of <code>
	 * Location</code>s. When viewed in a Mercator projection, the region will
	 * be a rectangle. If either both latitude or both longitude values are the
	 * same, an exception is thrown.<br/>
	 * <br/>
	 * <b>Note:</b> In an exception to the rules of insidedness defined in the
	 * {@link Shape} interface, <code>Location</code>s that fall on northern or
	 * eastern borders of this region are considered inside. See
	 * {@link Region#Region(Location, Location)} for implementation details.
	 * 
	 * @param loc1 the first <code>Location</code>
	 * @param loc2 the second <code>Location</code>
	 * @param latSpacing of grid nodes
	 * @param lonSpacing of grid nodes
	 * @param anchor <code>Location</code> for grid; may be <code>null</code>
	 * @throws IllegalArgumentException if the latitude or longitude values in
	 *         the <code>Location</code>s provided are the same or
	 *         <code>spacing</code> is outside the range 0&deg; &lt;
	 *         <code>spacing
	 * 		</code> &le; 5&deg;
	 * @throws NullPointerException if either <code>Location</code> argument is
	 *         <code>null</code>
	 * @see Region#Region(Location, Location)
	 */
	public GriddedRegion(Location loc1, Location loc2, double latSpacing,
		double lonSpacing, Location anchor) {
		super(loc1, loc2);
		initGrid(latSpacing, lonSpacing, anchor);
	}

	/**
	 * Initializes a <code>GriddedRegion</code> from a pair of <code>
	 * Location</code>s. When viewed in a Mercator projection, the region will
	 * be a rectangle. If either both latitude or both longitude values are the
	 * same, an exception is thrown.<br/>
	 * <br/>
	 * <b>Note:</b> In an exception to the rules of insidedness defined in the
	 * {@link Shape} interface, <code>Location</code>s that fall on northern or
	 * eastern borders of this region are considered inside. See
	 * {@link Region#Region(Location, Location)} for implementation details.
	 * 
	 * @param loc1 the first <code>Location</code>
	 * @param loc2 the second <code>Location</code>
	 * @param spacing of grid nodes
	 * @param anchor <code>Location</code> for grid; may be <code>null</code>
	 * @throws IllegalArgumentException if the latitude or longitude values in
	 *         the <code>Location</code>s provided are the same or
	 *         <code>spacing</code> is outside the range 0&deg; &lt;
	 *         <code>spacing
	 * 		</code> &le; 5&deg;
	 * @throws NullPointerException if either <code>Location</code> argument is
	 *         <code>null</code>
	 * @see Region#Region(Location, Location)
	 */
	public GriddedRegion(Location loc1, Location loc2, double spacing,
		Location anchor) {
		this(loc1, loc2, spacing, spacing, anchor);
	}

	/**
	 * Initializes a <code>GriddedRegion</code> from a list of border locations.
	 * The border type specifies whether lat-lon values are treated as points in
	 * an orthogonal coordinate system or as connecting great circles.
	 * 
	 * @param border Locations
	 * @param type the {@link BorderType} to use when initializing; a
	 *        <code>null</code> value defaults to
	 *        <code>BorderType.MERCATOR_LINEAR</code>
	 * @param latSpacing of grid nodes
	 * @param lonSpacing of grid nodes
	 * @param anchor <code>Location</code> for grid; may be <code>null</code>
	 * @throws IllegalArgumentException if the <code>border</code> does not have
	 *         at least 3 points or <code>spacing</code> is outside the range
	 *         0&deg; &lt; <code>spacing</code> &le; 5&deg;
	 * @throws NullPointerException if the <code>border</code> is
	 *         <code>null</code>
	 * @see Region#Region(LocationList, BorderType)
	 */
	public GriddedRegion(LocationList border, BorderType type,
		double latSpacing, double lonSpacing, Location anchor) {
		super(border, type);
		initGrid(latSpacing, lonSpacing, anchor);
	}

	/**
	 * Initializes a <code>GriddedRegion</code> from a list of border locations.
	 * The border type specifies whether lat-lon values are treated as points in
	 * an orthogonal coordinate system or as connecting great circles.
	 * 
	 * @param border Locations
	 * @param type the {@link BorderType} to use when initializing; a
	 *        <code>null</code> value defaults to
	 *        <code>BorderType.MERCATOR_LINEAR</code>
	 * @param spacing of grid nodes
	 * @param anchor <code>Location</code> for grid; may be <code>null</code>
	 * @throws IllegalArgumentException if the <code>border</code> does not have
	 *         at least 3 points or <code>spacing</code> is outside the range
	 *         0&deg; &lt; <code>spacing</code> &le; 5&deg;
	 * @throws NullPointerException if the <code>border</code> is
	 *         <code>null</code>
	 * @see Region#Region(LocationList, BorderType)
	 */
	public GriddedRegion(LocationList border, BorderType type, double spacing,
		Location anchor) {
		this(border, type, spacing, spacing, anchor);
	}

	/**
	 * Initializes a circular <code>GriddedRegion</code>. Internally, the
	 * centerpoint and radius are used to create a circular region composed of
	 * straight line segments that span 10&deg; wedges. <img
	 * style="padding: 30px 40px; float: right;" src="{@docRoot}
	 * /img/gridded_regions_circle.jpg"/> In the adjacent figure, the heavy
	 * black line marks the border of the <code>Region</code>. The light gray
	 * dots mark the <code>Location</code>s of nodes outside the region, and
	 * black dots those inside the region. The dashed grey line marks the
	 * border, inside which, a <code>Location</code> will be associated with a
	 * grid node. See {@link GriddedRegion#indexForLocation(Location)} for more
	 * details on rules governing whether a grid node is inside a region and
	 * whether a <code>Location</code> will be associated with a grid node.<br/>
	 * <br/>
	 * 
	 * @param center of the circle
	 * @param radius of the circle
	 * @param latSpacing of grid nodes
	 * @param lonSpacing of grid nodes
	 * @param anchor <code>Location</code> for grid; may be <code>null</code>
	 * @throws IllegalArgumentException if <code>radius</code> is outside the
	 *         range 0 km &lt; <code>radius</code> &le; 1000 km or <code>spacing
	 * 		</code> is outside the range 0&deg; &lt; <code>spacing</code> &le;
	 *         5&deg;
	 * @throws NullPointerException if <code>center</code> is null
	 * @see Region#Region(Location, double)
	 */
	public GriddedRegion(Location center, double radius, double latSpacing,
		double lonSpacing, Location anchor) {
		super(center, radius);
		initGrid(latSpacing, lonSpacing, anchor);
	}

	/**
	 * Initializes a circular <code>GriddedRegion</code>. Internally, the
	 * centerpoint and radius are used to create a circular region composed of
	 * straight line segments that span 10&deg; wedges. <img
	 * style="padding: 30px 40px; float: right;" src="{@docRoot}
	 * /img/gridded_regions_circle.jpg"/> In the adjacent figure, the heavy
	 * black line marks the border of the <code>Region</code>. The light gray
	 * dots mark the <code>Location</code>s of nodes outside the region, and
	 * black dots those inside the region. The dashed grey line marks the
	 * border, inside which, a <code>Location</code> will be associated with a
	 * grid node. See {@link GriddedRegion#indexForLocation(Location)} for more
	 * details on rules governing whether a grid node is inside a region and
	 * whether a <code>Location</code> will be associated with a grid node.<br/>
	 * <br/>
	 * 
	 * @param center of the circle
	 * @param radius of the circle
	 * @param spacing of grid nodes
	 * @param anchor <code>Location</code> for grid; may be <code>null</code>
	 * @throws IllegalArgumentException if <code>radius</code> is outside the
	 *         range 0 km &lt; <code>radius</code> &le; 1000 km or <code>spacing
	 * 		</code> is outside the range 0&deg; &lt; <code>spacing</code> &le;
	 *         5&deg;
	 * @throws NullPointerException if <code>center</code> is null
	 * @see Region#Region(Location, double)
	 */
	public GriddedRegion(Location center, double radius, double spacing,
		Location anchor) {
		this(center, radius, spacing, spacing, anchor);
	}

	/**
	 * Initializes a <code>GriddedRegion</code> as a buffered area around a
	 * line. In the adjacent figure, the heavy black line marks the border of
	 * the <code>Region</code>. <img style="padding: 30px 40px; float: right;"
	 * src="{@docRoot} /img/gridded_regions_buffer.jpg"/> The light gray dots
	 * mark the <code>Location</code>s of nodes outside the region, and black
	 * dots those inside the region. The dashed grey line marks the border,
	 * inside which, a <code>Location</code> will be associated with a grid
	 * node. See {@link GriddedRegion#indexForLocation(Location)} for more
	 * details on rules governing whether a grid node is inside a region and
	 * whether a <code>Location</code> will be associated with a grid node.<br/>
	 * <br/>
	 * <br/>
	 * 
	 * @param line at center of buffered region
	 * @param buffer distance from line
	 * @param latSpacing of grid nodes
	 * @param lonSpacing of grid nodes
	 * @param anchor <code>Location</code> for grid; may be <code>null</code>
	 * @throws NullPointerException if <code>line</code> is null
	 * @throws IllegalArgumentException if <code>buffer</code> is outside the
	 *         range 0 km &lt; <code>buffer</code> &le; 500 km or <code>spacing
	 * 		</code> is outside the range 0&deg; &lt; <code>spacing</code> &le;
	 *         5&deg;
	 * @see Region#Region(LocationList, double)
	 */
	public GriddedRegion(LocationList line, double buffer, double latSpacing,
		double lonSpacing, Location anchor) {
		super(line, buffer);
		initGrid(latSpacing, lonSpacing, anchor);
	}

	/**
	 * Initializes a <code>GriddedRegion</code> as a buffered area around a
	 * line. In the adjacent figure, the heavy black line marks the border of
	 * the <code>Region</code>. <img style="padding: 30px 40px; float: right;"
	 * src="{@docRoot} /img/gridded_regions_buffer.jpg"/> The light gray dots
	 * mark the <code>Location</code>s of nodes outside the region, and black
	 * dots those inside the region. The dashed grey line marks the border,
	 * inside which, a <code>Location</code> will be associated with a grid
	 * node. See {@link GriddedRegion#indexForLocation(Location)} for more
	 * details on rules governing whether a grid node is inside a region and
	 * whether a <code>Location</code> will be associated with a grid node.<br/>
	 * <br/>
	 * <br/>
	 * 
	 * @param line at center of buffered region
	 * @param buffer distance from line
	 * @param spacing of grid nodes
	 * @param anchor <code>Location</code> for grid; may be <code>null</code>
	 * @throws NullPointerException if <code>line</code> is null
	 * @throws IllegalArgumentException if <code>buffer</code> is outside the
	 *         range 0 km &lt; <code>buffer</code> &le; 500 km or <code>spacing
	 * 		</code> is outside the range 0&deg; &lt; <code>spacing</code> &le;
	 *         5&deg;
	 * @see Region#Region(LocationList, double)
	 */
	public GriddedRegion(LocationList line, double buffer, double spacing,
		Location anchor) {
		this(line, buffer, spacing, spacing, anchor);
	}

	/**
	 * Initializes a <code>GriddedRegion</code> with a <code>Region</code>.
	 * 
	 * @param region to use as border for new <code>GriddedRegion</code>
	 * @param latSpacing of grid nodes
	 * @param lonSpacing of grid nodes
	 * @param anchor <code>Location</code> for grid; may be <code>null</code>
	 * @throws IllegalArgumentException if <code>spacing
	 * 		</code> is outside the range 0&deg; &lt; <code>spacing</code> &le;
	 *         5&deg;
	 * @throws NullPointerException if <code>region</code> is <code>null</code>
	 * @see Region#Region(Region)
	 */
	public GriddedRegion(Region region, double latSpacing, double lonSpacing,
		Location anchor) {
		super(region);
		initGrid(latSpacing, lonSpacing, anchor);
	}

	/**
	 * Initializes a <code>GriddedRegion</code> with a <code>Region</code>.
	 * 
	 * @param region to use as border for new <code>GriddedRegion</code>
	 * @param spacing of grid nodes
	 * @param anchor <code>Location</code> for grid; may be <code>null</code>
	 * @throws IllegalArgumentException if <code>spacing
	 * 		</code> is outside the range 0&deg; &lt; <code>spacing</code> &le;
	 *         5&deg;
	 * @throws NullPointerException if <code>region</code> is <code>null</code>
	 * @see Region#Region(Region)
	 */
	public GriddedRegion(Region region, double spacing, Location anchor) {
		this(region, spacing, spacing, anchor);
	}
	
	/**
	 * Initializes a <code>GriddedRegion</code> as a copy of another <code>GriddedRegion</code>.
	 * 
	 * @param gridRegion
	 * @throws NullPointerException if <code>gridRegion</code> is <code>null</code>
	 */
	public GriddedRegion(GriddedRegion gridRegion) {
		this(gridRegion, gridRegion.latNodeCenters, gridRegion.lonNodeCenters,
				gridRegion.latSpacing, gridRegion.lonSpacing, gridRegion.anchor, gridRegion.nodeList);
	}
	
	/**
	 * Private constructor for deserialization or cloning
	 * 
	 * @param region
	 * @param latNodeCenters
	 * @param lonNodeCenters
	 * @param latSpacing
	 * @param lonSpacing
	 * @param anchor
	 * @param nodeList
	 */
	private GriddedRegion(Region region, double[] latNodeCenters, double[] lonNodeCenters,
			double latSpacing, double lonSpacing, Location anchor, LocationList nodeList) {
		super(region);
		this.latNodeCenters = latNodeCenters;
		this.lonNodeCenters = lonNodeCenters;
		setSpacing(latSpacing, lonSpacing);
		if (anchor == null)
			setAnchor(null);
		else
			// keep exact anchor
			this.anchor = anchor;
		initGridRange();
		int gridSize = numLonNodes * numLatNodes;
		this.nodeList = nodeList;
		nodeCount = nodeList.size();
		// figure out gridIndexes
		gridIndices = new int[gridSize];
		// initialize to -1
		for (int i=0; i<gridSize; i++)
			gridIndices[i] = -1;
		// now associate nodes to gridIndexes
		for (int node_idx=0; node_idx<nodeCount; node_idx++) {
			Location node = nodeList.get(node_idx);
			int latIndex = getLatIndex(node);
			Preconditions.checkState(latIndex >= 0, "Couldn't find latitude index for grid node %s: %s", node_idx, node);
			int lonIndex = getLonIndex(node);
			Preconditions.checkState(lonIndex >= 0, "Couldn't find longitude index for grid node %s: %s", node_idx, node);
			
			int grid_idx = ((latIndex) * numLonNodes) + lonIndex;
			Preconditions.checkState(gridIndices[grid_idx] == -1, "Multiple nodes map to the same grid index");
			gridIndices[grid_idx] = node_idx;
		}
	}

	/**
	 * Returns the longitudinal grid node spacing for this region.
	 * @return the longitudinal grid node spacing (in degrees)
	 */
	public double getLatSpacing() {
		return latSpacing;
	}

	/**
	 * Returns the latitudinal grid node spacing for this region.
	 * @return the latitudinal grid node spacing (in degrees)
	 */
	public double getLonSpacing() {
		return lonSpacing;
	}

	/**
	 * Returns the grid node spacing for this region. If the lat and lon node
	 * spacing differs, method defaults to {@link #getLatSpacing()}.
	 * @return the grid node spacing (in degrees)
	 */
	public double getSpacing() {
		return latSpacing;
	}

	/**
	 * Returns whether the lat and lon spacing are the same.
	 * @return <code>true</code> if lat and lon spacing are the same;
	 *         <code>false</code> otherwise.
	 */
	public boolean isSpacingUniform() {
		return latSpacing == lonSpacing;
	}

	/**
	 * Returns the total number of grid nodes in this region.
	 * @return the number of grid nodes
	 */
	public int getNodeCount() {
		return nodeCount;
	}

	/**
	 * Alternative to getNodeCount().
	 * @return
	 */
	public int getNumLocations() {
		return getNodeCount();
	}

	/**
	 * Returns whether this region contains any grid nodes. If a regions
	 * dimensions are smaller than the grid spacing, it may be empty.
	 * @return <code>true</code> if region has no grid nodes; <code>false</code>
	 *         otherwise
	 */
	public boolean isEmpty() {
		return nodeCount == 0;
	}

	/**
	 * Returns the index of the node at the supplied <code>Direction</code> from
	 * the node at the supplied index.
	 * @param idx to move from
	 * @param dir to move
	 * @return index at <code>Direction</code> or -1 if no node exists
	 * @throws NullPointerException if supplied index is not a valid grid index
	 */
	public int move(int idx, Direction dir) {
		Location start = locationForIndex(idx);
		checkNotNull(start, "Invalid start index");
		Location end = new Location(start.getLatitude() + latSpacing *
			dir.signLatMove(), start.getLongitude() + lonSpacing *
			dir.signLonMove());
		return indexForLocation(end);
	}
	
	/**
	 * Compares this <code>GriddedRegion</code> to another and returns
	 * <code>true</code> if they are the same with respect to aerial extent
	 * (both exterior and interior borders), grid node spacing, and location.
	 * This method ignores the names of the <code>GriddedRegion</code>s. Use
	 * <code>GriddedRegion.equals(Object)</code> to include name comparison.
	 * 
	 * @param gr the <code>Regions</code> to compare
	 * @return <code>true</code> if this <code>Region</code> has the same
	 *         geometry as the supplied <code>Region</code>, <code>false</code>
	 *         otherwise
	 * @see GriddedRegion#equals(Object)
	 */
	public boolean equalsRegion(GriddedRegion gr) {
		if (!super.equalsRegion(gr)) return false;
		if (!gr.anchor.equals(anchor)) return false;
		if (gr.latSpacing != latSpacing) return false;
		if (gr.lonSpacing != lonSpacing) return false;
		return true;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof GriddedRegion)) return false;
		GriddedRegion gr = (GriddedRegion) obj;
		if (!getName().equals(gr.getName())) return false;
		return equalsRegion(gr);
	}

	@Override
	public int hashCode() {
		return super.hashCode() ^ anchor.hashCode() ^
			Double.valueOf(latSpacing).hashCode() ^
			Double.valueOf(lonSpacing).hashCode();
	}

	/**
	 * Returns an exact, independent copy of this <code>GriddedRegion</code>.
	 * @return a copy of this <code>Region</code>
	 */
	@Override
	public GriddedRegion clone() {
		// TODO
//		return new GriddedRegion(this, latSpacing, lonSpacing, anchor);
		return new GriddedRegion(this);
	}

	/**
	 * Creates a new <code>GriddedRegion</code> from this (the parent) and
	 * another <code>Region</code>. The border of the new region is the
	 * intersection of the borders of the parent and the passed-in region. <img
	 * style="padding: 30px 40px; float: right;" src="{@docRoot}
	 * /img/gridded_regions_sub.jpg"/> The new region also inherits the grid
	 * spacing and node-alignment of the parent. The method returns
	 * <code>null</code> if the two regions do not overlap.<br/>
	 * <br/>
	 * Note that the returned <code>GriddedRegion</code> may be devoid of grid
	 * nodes, e.g. in cases where the sub-region is too small to contain any
	 * nodes of the parent grid. Such a situation may arise if the sub-region
	 * represents the area of influence of a small magnitude earthquake or
	 * aftershock. If the closest point to the sub-region in the parent grid is
	 * desired, then compute the subRegionCentroid and use:
	 * 
	 * <pre>
	 * if (newGriddedRegion.isEmpty()) {
	 * 	int idx = indexForLocation(subRegionCentroid);
	 * 	if (idx != -1) {
	 * 		Location loc = locationForIndex(idx);
	 * 	}
	 * }
	 * </pre>
	 * 
	 * <br/>
	 * @param region to use as border for sub-region
	 * @return a new GriddedRegion or <code>null</code> if the the sub-region
	 *         does not intersect its parent (<code>this</code>)
	 * @see GriddedRegion#isEmpty()
	 */
	public GriddedRegion subRegion(Region region) {
		Region newRegion = Region.intersect(this, region);
		if (newRegion == null) return null;
		return new GriddedRegion(newRegion, latSpacing, lonSpacing, anchor);
	}

	/**
	 * Overridden to throw an <code>UnsupportedOperationException</code> when
	 * called. The border of a <code>GriddedRegion</code> may only be set on
	 * initialization. To create a <code>GriddedRegion</code> that has interiors
	 * (donut-holes), first create a <code>Region</code> with the required
	 * border and interiors using {@link Region#addInterior(Region)} and then
	 * use it to initialize a <code>GriddedRegion</code>.
	 * 
	 * @throws UnsupportedOperationException
	 * @see Region#addInterior(Region)
	 */
	@Override
	public void addInterior(Region region) {
		throw new UnsupportedOperationException(
			"A GriddedRegion may not have an interior Region set");
	}

	@Override
	public Iterator<Location> iterator() {
		return nodeList.iterator();
	}

	/**
	 * Returns the locations of all the nodes in the region as a
	 * <code>LocationList</code>.
	 * @return a list of all the node locations in the region.
	 */
	public LocationList getNodeList() {
		return nodeList;
	}

	/**
	 * Returns the <code>Location</code> at a given grid index. This method is
	 * intended for random access of nodes in this gridded region; to cycle over
	 * all nodes, iterate over the region.
	 * 
	 * @param index of location to retrieve
	 * @return the <code>Location</code> or <code>null</code> if index is out of
	 *         range
	 */
	public Location locationForIndex(int index) {
		try {
			return nodeList.get(index);
		} catch (IndexOutOfBoundsException e) {
			return null;
		}
	}

	/**
	 * Returns the list of grid indices spanned by the bounds of the supplied
	 * region. Bounds refers to the rectangle that completely encloses a region.
	 * Note that this is a list of all nodes for which any part, however small,
	 * overlaps the supplied rectangle and is not restricted to grid centers.
	 * @param rect to process
	 * @return the list of grid indices that intersect the bounding box of the
	 *         supplied region
	 * @throws IllegalArgumentException if the supplied rectangle is not
	 *         completely enclosed by thei {@code Region}
	 */
	public List<Integer> indicesForBounds(Rectangle2D rect) {
		try {
			// fast lookups MAY fail as intial contains(rect) test may
			// pass when a corner vertix is in fact absent
			return indexLookupFast(rect);
		} catch (Exception e) {
			return indexLookupSlow(rect);
		}
	}

	/* Efficiently finds relevant indicis without intersection testing */
	private List<Integer> indexLookupFast(Rectangle2D rect) {
		checkArgument(area.contains(rect));
		Location pLL = new Location(rect.getMinY(), rect.getMinX());
		Location pUL = new Location(rect.getMaxY(), rect.getMinX());
		Location pLR = new Location(rect.getMinY(), rect.getMaxX());
		int idxLL = indexForLocation(pLL);
		int idxUL = indexForLocation(pUL);
		int idxLR = indexForLocation(pLR);

		// indices of row starts
		List<Integer> rowStarts = Lists.newArrayList();
		int rowStartIdx = idxLL;
		int lastRowStartIdx = idxUL;
		while (rowStartIdx <= lastRowStartIdx) {
			rowStarts.add(rowStartIdx);
			Location currLoc = locationForIndex(rowStartIdx);
			Location nextLoc = new Location(currLoc.getLatitude() + latSpacing,
				currLoc.getLongitude());
			rowStartIdx = indexForLocation(nextLoc);
		}

		// row length
		int len = idxLR - idxLL + 1;

		// build list
		List<Integer> indices = Lists.newArrayList();
		for (Integer idx : rowStarts) {
			addRange(indices, idx, len);
		}
		return indices;

	}

	/* Brute force approach does intersect test for all region node polys */
	private List<Integer> indexLookupSlow(Rectangle2D rect) {
//		System.out.println("Sloooooooow");
		List<Integer> indices = Lists.newArrayList();
		for (int i = 0; i < nodeCount; i++) {
			Area area = areaForIndex(i);
			if (area.intersects(rect)) indices.add(i);
		}
		return indices;
	}

	/* Adds an inclusive range of ints to a list */
	private static void addRange(List<Integer> ints, int start, int num) {
		for (int i = start; i < start + num; i++) {
			ints.add(i);
		}
	}

	/**
	 * Returns the {@code Region} that bounds a node
	 * @param index of the node of interest
	 * @return the bounding region of the specified node
	 */
	public Area areaForIndex(int index) {
		Location p = locationForIndex(index);
		return RegionUtils.getNodeShape(p, lonSpacing, latSpacing);
	}

	/**
	 * Alternative to locationForIndex(int index)
	 * @param index
	 * @return
	 */
	// TODO not needed; only used 1 place
	public Location getLocation(int index) {
		return locationForIndex(index);
	}

	/**
	 * Returns the index of the grid node associated with a given
	 * <code>Location</code> or -1 if the associated grid node is ouside this
	 * gridded region. For a <code>Location</code> to be associated with a node,
	 * it must fall within the square region on which the node is centered. Note
	 * that this allows for some <code>Location</code>s that are outside the
	 * region border to still be associated with a node. Conversely, a
	 * {@link Region#contains(Location)} may return <code>true</code> while this
	 * method returns -1. Users interested in node association should always use
	 * this method alone and test for -1 return value.
	 * {@link Region#contains(Location)} should <i>NOT</i> be used a as a test
	 * prior to calling this method. <br/>
	 * <br/>
	 * The figure and table below indicate the results produced by calling
	 * <code>contains()</code> or <code>indexForLocation()</code>. The arrows in
	 * the figure point towards the interior of the <code>Region</code>. The
	 * dots mark the centered <code>Location</code> of each grid node and the
	 * numbers indicate the index value of each. Remember that both methods test
	 * for insidedness according to the rules defined in the {@link Shape}
	 * interface. <br/>
	 * <img style="padding: 20px; display: block; margin-left:auto;
	 * margin-right:auto;" src="{@docRoot} /img/node_association.jpg"/> <br/>
	 * <table id="table-a">
	 * <thead>
	 * <tr>
	 * <th>Location</th>
	 * <th><code>contains(Location)</code></th>
	 * <th><code>indexForLocation(Location)</code></th>
	 * </tr>
	 * <thead> <tbody>
	 * <tr>
	 * <td><b>A</b></td>
	 * <td><code>true</code></td>
	 * <td>-1</td>
	 * </tr>
	 * <tr>
	 * <td><b>B</b></td>
	 * <td><code>false</code></td>
	 * <td>3</td>
	 * </tr>
	 * <tr>
	 * <td><b>C</b></td>
	 * <td><code>false</code></td>
	 * <td>3</td>
	 * </tr>
	 * <tr>
	 * <td><b>D</b></td>
	 * <td><code>false</code></td>
	 * <td>-1</td>
	 * </tr>
	 * <tr>
	 * <td><b>E</b></td>
	 * <td><code>true</code></td>
	 * <td>3</td>
	 * </tr>
	 * <tr>
	 * <td><b>F</b></td>
	 * <td><code>true</code></td>
	 * <td>3</td>
	 * </tr>
	 * <tr>
	 * <td><b>G</b></td>
	 * <td><code>true</code></td>
	 * <td>4</td>
	 * </tr>
	 * </tbody>
	 * </table>
	 * 
	 * @param loc the <code>Location</code> to match to a grid node index
	 * @return the index of the associated node or -1 if no such node exists
	 */
	public int indexForLocation(Location loc) {
//		int lonIndex = getNodeIndex(lonNodeEdges, loc.getLongitude());
		int lonIndex = getNodeIndex(lonNodeCenters, loc.getLongitude(), lonSpacing);
		if (lonIndex == -1) return -1;
//		int latIndex = getNodeIndex(latNodeEdges, loc.getLatitude());
		int latIndex = getNodeIndex(latNodeCenters, loc.getLatitude(), latSpacing);
		if (latIndex == -1) return -1;
		int gridIndex = ((latIndex) * numLonNodes) + lonIndex;
		return gridIndices[gridIndex];
	}
	
	/**
	 * Returns the latitude node index of the given location
	 * @param loc
	 * @return latitude index
	 */
	public int getLatIndex(Location loc) {
		return getNodeIndex(latNodeCenters, loc.getLatitude(), latSpacing);
	}
	
	/**
	 * Returns the longitude node index of the given location
	 * @param loc
	 * @return latitude index
	 */
	public int getLonIndex(Location loc) {
		return getNodeIndex(lonNodeCenters, loc.getLongitude(), lonSpacing);
	}
	
	/**
	 * Gets the node index for the given lat/lon indices
	 * 
	 * @param latIndex
	 * @param lonIndex
	 * @return the node index for the given latitude/longitude index
	 */
	public int getNodeIndex(int latIndex, int lonIndex) {
		if (latIndex == -1 || latIndex == -1)
			return -1;
		return ((latIndex) * numLonNodes) + lonIndex;
	}
	
	/**
	 * Returns the total number of latitude nodes. For rectangular regions, nodeCount = numLatNodes * numLonNodes
	 * @return total number of latitude nodes
	 */
	public int getNumLatNodes() {
		return numLatNodes;
	}
	
	/**
	 * Returns the total number of longitude nodes. For rectangular regions, nodeCount = numLatNodes * numLonNodes
	 * @return total number of longitude nodes
	 */
	public int getNumLonNodes() {
		return numLonNodes;
	}

	/**
	 * Returns the minimum grid latitude. Note that there may not actually be
	 * any nodes at this latitude. See class <a href="#note">note</a>. If the
	 * region is devoid of nodes, method will return <code>Double.NaN</code>.
	 * 
	 * @return the minimum grid latitude
	 * @see Region#contains(Location)
	 */
	public double getMinGridLat() {
		return minGridLat;
	}

	/**
	 * Returns the maximum grid latitude. Note that there may not actually be
	 * any nodes at this latitude. See class <a href="#note">note</a>. If the
	 * region is devoid of nodes, method will return <code>Double.NaN</code>.
	 * 
	 * @return the maximum grid latitude
	 * @see Region#contains(Location)
	 */
	public double getMaxGridLat() {
		return maxGridLat;
	}

	/**
	 * Returns the minimum grid longitude. Note that there may not actually be
	 * any nodes at this longitude. See class <a href="#note">note</a>. If the
	 * region is devoid of nodes, method will return <code>Double.NaN</code>.
	 * 
	 * @return the minimum grid longitude
	 * @see Region#contains(Location)
	 */
	public double getMinGridLon() {
		return minGridLon;
	}

	/**
	 * Returns the maximum grid longitude. Note that there may not actually be
	 * any nodes at this longitude. See class <a href="#note">note</a>. If the
	 * region is devoid of nodes, method will return <code>Double.NaN</code>.
	 * 
	 * @return the maximum grid longitude
	 * @see Region#contains(Location)
	 */
	public double getMaxGridLon() {
		return maxGridLon;
	}

	@Override
	public Element toXMLMetadata(Element root) {
		Element xml = root.addElement(GriddedRegion.XML_METADATA_NAME);
		xml.addAttribute(GriddedRegion.XML_METADATA_GRID_SPACING_NAME,
			this.getSpacing() + "");
		Element xml_anchor = xml
			.addElement(GriddedRegion.XML_METADATA_ANCHOR_NAME);
		anchor.toXMLMetadata(xml_anchor);
		xml.addAttribute(GriddedRegion.XML_METADATA_NUM_POINTS_NAME,
			this.getNodeCount() + "");
		super.toXMLMetadata(xml);

		return root;
	}

	/**
	 * Initializes a new <code>Region</code> from stored metadata.
	 * @param root metadata element
	 * @return a <code>GriddedRegion</code>
	 */
	public static GriddedRegion fromXMLMetadata(Element root) {
		double gridSpacing = Double.parseDouble(root.attribute(
			GriddedRegion.XML_METADATA_GRID_SPACING_NAME).getValue());
		Region geoRegion = Region.fromXMLMetadata(root
			.element(Region.XML_METADATA_NAME));
		LocationList outline = geoRegion.getBorder();
		Location xml_anchor = Location.fromXMLMetadata(root.element(
			XML_METADATA_ANCHOR_NAME).element(Location.XML_METADATA_NAME));
		return new GriddedRegion(outline, BorderType.MERCATOR_LINEAR,
			gridSpacing, gridSpacing, xml_anchor);
	}

//	/*
//	 * Returns the node index of the value or -1 if the value is out of range.
//	 * Expects the array of edge values.
//	 */
//	private static int getNodeIndex(double[] edgeVals, double value) {
//		// If a value exists in an array, binary search returns the index
//		// of the value. If the value is less than the lowest array value,
//		// binary search returns -1. If the value is within range or
//		// greater than the highest array value, binary search returns
//		// (-insert_point-1). The SHA rule of thumb follows the java rules
//		// of insidedness, so any exact node edge value is associated with
//		// the node above. Therefore, the negative within range values are
//		// adjusted to the correct node index with (-idx-2). Below range
//		// values are already -1; above range values are corrected to -1.
//		int idx = Arrays.binarySearch(edgeVals, value);
//		idx = (idx < -1) ? (-idx - 2) : idx;
//		return (idx == edgeVals.length - 1) ? -1 : idx;
//	}
	
	private static final double PRECISION_SCALE = 1 + 1e-14;
	private static int getNodeIndex(double[] nodes, double value, double spacing) {
		double iVal = PRECISION_SCALE * (value - nodes[0]) / spacing;
		int i = (int) Math.round(iVal);
		// special cases
		if (i == -1 && (float)(nodes[0]-0.5*spacing) == (float)value)
			return 0;
		return (i<0) ? -1 : (i>=nodes.length) ? -1 : i;
	}

	/* grid setup */
	private void initGrid(double latSpacing, double lonSpacing, Location anchor) {
		setSpacing(latSpacing, lonSpacing);
		setAnchor(anchor);
		initNodes();
	}

	/* Sets the gid node spacing. */
	private void setSpacing(double latSpacing, double lonSpacing) {
		String mssg = "spacing [%s] must be 0\u00B0 \u003E S \u2265 5\u00B0";
		checkArgument((latSpacing > 0 && latSpacing <= 5), "Latitude" + mssg,
			latSpacing);
		checkArgument((lonSpacing > 0 && lonSpacing <= 5), "Longitude" + mssg,
			lonSpacing);
		this.latSpacing = latSpacing;
		this.lonSpacing = lonSpacing;
	}

	/*
	 * Sets the grid anchor value. The Location provided is adjusted to be the
	 * lower left corner (min lat-lon) of the region bounding grid. If the
	 * region grid extended infinitely, both the input and adjusted anchor
	 * Locations would coincide with grid nodes.
	 */
	private void setAnchor(Location anchor) {
		if (anchor == null) anchor = new Location(getMinLat(), getMinLon());
		double newLat = computeAnchor(getMinLat(), anchor.getLatitude(),
			latSpacing);
		double newLon = computeAnchor(getMinLon(), anchor.getLongitude(),
			lonSpacing);
		this.anchor = new Location(newLat, newLon);
	}

	/* Computes adjusted anchor values. */
	private static double computeAnchor(double min, double anchor,
			double spacing) {
		double delta = anchor - min;
		double num_div = Math.floor(delta / spacing);
		double offset = delta - num_div * spacing;
		double newAnchor = min + offset;
		newAnchor = (newAnchor < min) ? newAnchor + spacing : newAnchor;
		// round to cleaner values: e.g. 1.0 vs. 0.999999999997
		return Precision.round(newAnchor, 8);
	}

	/* Initilize the grid index, node edge, and Location arrays */
	private void initNodes() {

		// temp node center arrays
		lonNodeCenters = initNodeCenters(anchor.getLongitude(), getMaxLon(),
			lonSpacing);
		latNodeCenters = initNodeCenters(anchor.getLatitude(), getMaxLat(),
			latSpacing);

		// node edge arrays
//		lonNodeEdges = initNodeEdges(anchor.getLongitude(), getMaxLon(),
//			lonSpacing);
//		latNodeEdges = initNodeEdges(anchor.getLatitude(), getMaxLat(),
//			latSpacing);

		// range data
		initGridRange();
		int gridSize = numLonNodes * numLatNodes;

		// node data
		gridIndices = new int[gridSize];
		nodeList = new LocationList();
		int node_idx = 0;
		int grid_idx = 0;
		Location loc;
		for (double lat : latNodeCenters) {
			for (double lon : lonNodeCenters) {
				loc = new Location(lat, lon, 0d);
				if (contains(loc)) {
					nodeList.add(loc);
					gridIndices[grid_idx] = node_idx++;
				} else {
					gridIndices[grid_idx] = -1;
				}
				grid_idx++;
			}
		}
		nodeCount = node_idx;
	}
	
	private void initGridRange() {
		numLatNodes = latNodeCenters.length;
		numLonNodes = lonNodeCenters.length;
//		System.out.println("numLat="+numLatNodes+", numLon="+numLonNodes);
		minGridLat = (numLatNodes != 0) ? latNodeCenters[0] : Double.NaN;
		maxGridLat = (numLatNodes != 0) ? latNodeCenters[numLatNodes - 1]
			: Double.NaN;
		minGridLon = (numLonNodes != 0) ? lonNodeCenters[0] : Double.NaN;
		maxGridLon = (numLonNodes != 0) ? lonNodeCenters[numLonNodes - 1]
			: Double.NaN;
	}

	/*
	 * Initializes an array of node centers. The first (lowest) bin is centered
	 * on the min value.
	 */
	private static double[] initNodeCenters(double min, double max, double width) {
		// nodeCount is num intervals between min and max + 1
		int nodeCount = (int) Math.floor((max - min) / width) + 1;
		double firstCenterVal = min;
		return buildArray(firstCenterVal, nodeCount, width);
	}

//	/*
//	 * Initializes an array of node edges which can be used to associate a value
//	 * with a particular node using binary search.
//	 */
//	private static double[] initNodeEdges(double min, double max, double width) {
//		// edges is binCount + 1
//		int edgeCount = (int) Math.floor((max - min) / width) + 2;
//		// offset first bin edge half a binWidth
//		double firstEdgeVal = min - (width / 2);
//		return buildArray(firstEdgeVal, edgeCount, width);
//	}

	/* Node edge and center array builder. */
	private static double[] buildArray(double startVal, int count,
			double interval) {

		double[] values = new double[count];
		double val = startVal;
		for (int i = 0; i < count; i++) {
			// round to cleaner values: e.g. 1.0 vs. 0.999999999997
			values[i] = Precision.round(val, 8);
			val += interval;
		}
		return values;
	}
	
	/*
	 * GeoJSON related methods
	 */
	
	/**
	 * Converts this gridded region to a GeoJSON feature object for serialization
	 * 
	 * @return
	 */
	public Feature toFeature() {
		List<Geometry> geometries = new ArrayList<>();
		geometries.add(new Polygon(this)); // polygon border
		geometries.add(new MultiPoint(nodeList)); // nodes
		GeometryCollection geometry = new GeometryCollection(geometries);
		FeatureProperties properties = new FeatureProperties();
		
		properties.put(JSON_LAT_NODES, latNodeCenters);
		properties.put(JSON_LON_NODES, lonNodeCenters);
		properties.put(JSON_LAT_SPACING, latSpacing);
		properties.put(JSON_LON_SPACING, lonSpacing);
		properties.put(JSON_ANCHOR, anchor);
		
		String name = getName();
		if (name != null && name.equals(NAME_DEFAULT))
			name = null;
		return new Feature(name, geometry, properties);
	}
	
	/**
	 * Converts GeoJSON feature object back to a region.
	 * 
	 * @return
	 */
	public static GriddedRegion fromFeature(Feature feature) {
		Preconditions.checkNotNull(feature.geometry, "Feature is missing geometry");
		Preconditions.checkState(feature.geometry instanceof GeometryCollection,
				"Unexpected geometry type for GriddedRegion, should be GeometryCollection: %s", feature.geometry.type);
		GeometryCollection geometries = (GeometryCollection)feature.geometry;
		Region region = null;
		LocationList nodeList = null;
		for (Geometry geometry : geometries.geometries) {
			if (geometry instanceof Polygon) {
				Preconditions.checkState(region == null, "Multiple region polygons found for GriddedRegion");
				region = ((Polygon)geometry).asRegion();
			} else if (geometry instanceof MultiPolygon) {
				Preconditions.checkState(region == null, "Multiple region polygons found for GriddedRegion");
				List<Region> list = ((MultiPolygon)feature.geometry).asRegions();
				Preconditions.checkState(list.size() == 1, "Must have exactly 1 polygon, have %s", list.size());
				region = list.get(0);
			} else if (geometry instanceof MultiPoint) {
				Preconditions.checkState(nodeList == null, "Multiple node lists found");
				nodeList = ((MultiPoint)geometry).points;
			} else if (geometry instanceof Point) {
				Preconditions.checkState(nodeList == null, "Multiple node lists found");
				nodeList = new LocationList();
				nodeList.add(((Point)geometry).point);
			} else {
				System.err.println("Warning: skipping unexpected geometry type when loading GriddedRegion: "+geometry.type);
			}
		}
		Preconditions.checkNotNull(region, "Region polygon not found in GriddedRegion feature");
		Preconditions.checkNotNull(nodeList, "Node list (MultiPoint geometry) not found in GriddedRegion feature");
		
		FeatureProperties properties = feature.properties;
		// determine lat/lon nodes and spacing
		double[] latNodeCenters = null;
		double[] lonNodeCenters = null;
		double latSpacing = Double.NaN;
		double lonSpacing = Double.NaN;
		Location anchor = null;
		
		if (properties != null) {
			latNodeCenters = properties.getDoubleArray(JSON_LAT_NODES, null);
			lonNodeCenters = properties.getDoubleArray(JSON_LON_NODES, null);
			latSpacing = properties.getDouble(JSON_LAT_SPACING, Double.NaN);
			lonSpacing = properties.getDouble(JSON_LON_SPACING, Double.NaN);
			anchor = properties.getLocation(JSON_ANCHOR, null);
		}

		if (latNodeCenters == null) {
			System.err.println("Warning: "+JSON_LAT_NODES+" not specified in GriddedRegion GeoJSON properties, "
					+ "inferring from node list");
			latNodeCenters = inferNodeCenters(nodeList, true);
		}
		if (lonNodeCenters == null) {
			System.err.println("Warning: "+JSON_LON_NODES+" not specified in GriddedRegion GeoJSON properties, "
					+ "inferring from node list");
			lonNodeCenters = inferNodeCenters(nodeList, false);
		}
		if (Double.isNaN(latSpacing)) {
			System.err.println("Warning: "+JSON_LAT_SPACING+" not specified in GriddedRegion GeoJSON properties, "
					+ "inferring from nodes");
			latSpacing = inferSpacing(latNodeCenters);
		}
		if (Double.isNaN(lonSpacing)) {
			System.err.println("Warning: "+JSON_LON_SPACING+" not specified in GriddedRegion GeoJSON properties, "
					+ "inferring from nodes");
			lonSpacing = inferSpacing(lonNodeCenters);
		}
		GriddedRegion gridRegion = new GriddedRegion(
				region, latNodeCenters, lonNodeCenters, latSpacing, lonSpacing, anchor, nodeList);
		if (feature.id != null)
			gridRegion.setName(feature.id.toString());
		return gridRegion;
	}
	
	private static double[] inferNodeCenters(LocationList gridNodes, boolean latitude) {
		if (gridNodes.isEmpty())
			return new double[0];
		List<Double> values = new ArrayList<>();
		HashSet<Float> uniques = new HashSet<>();
		for (Location loc : gridNodes) {
			double val = latitude ? loc.getLatitude() : loc.getLongitude();
			if (!uniques.contains((float)val)) {
				uniques.add((float)val);
				values.add(val);
			}
		}
		Collections.sort(values);
		double[] array = Doubles.toArray(values);
		
		// verify that it is evenly spaced
		inferSpacing(array);
		
		return array;
	}
	
	private static double inferSpacing(double[] values) {
		if (values.length < 1)
			return 0d;
		double spacing = Math.abs(values[values.length-1] - values[0])/(values.length-1);
		for (int i=1; i<values.length; i++) {
			float calcSpacing = (float)Math.abs(values[i] - values[i-1]);
			Preconditions.checkState(calcSpacing == (float)spacing, 
					"Cannot infer spacing. Implied spacing from whole node array is %s, "
					+ "but spacing between elements %s and %s is %s", (float)spacing, i-1, i, calcSpacing);
		}
		return spacing;
	}
	
	public static class Adapter extends TypeAdapter<GriddedRegion> {
		
		private Feature.FeatureAdapter featureAdapter;
		
		public Adapter() {
			featureAdapter = new Feature.FeatureAdapter();
		}

		@Override
		public void write(JsonWriter out, GriddedRegion value) throws IOException {
			if (value == null)
				out.nullValue();
			else
				featureAdapter.write(out, value.toFeature());
		}

		@Override
		public GriddedRegion read(JsonReader in) throws IOException {
			Feature feature = featureAdapter.read(in);
			return fromFeature(feature);
		}
		
	}
	
	private static final String JSON_LAT_NODES = "LatNodes";
	private static final String JSON_LON_NODES = "LonNodes";
	private static final String JSON_LAT_SPACING = "LatSpacing";
	private static final String JSON_LON_SPACING = "LonSpacing";
	private static final String JSON_ANCHOR = "Anchor";
	
	/**
	 * Infers a gridded region from a node list. The node list *must* be evenly spaced (to 4-byte floating point
	 * precision) in latitude and longitude, and can be irregularly shaped, but must not contain any holes.
	 * 
	 * If any of these criteria are not met, an {@link IllegalStateException} will be thrown.
	 * 
	 * @param nodeList
	 * @return inferred gridded region for the given node list
	 * @throws IllegalStateException if the node list is not evenly spaced in both latitude and longitude
	 */
	public static GriddedRegion inferRegion(LocationList nodeList) throws IllegalStateException {
		double[] latNodes = inferNodeCenters(nodeList, true);
		double[] lonNodes = inferNodeCenters(nodeList, false);
		double latSpacing = inferSpacing(latNodes);
		double lonSpacing = inferSpacing(lonNodes);
		
		double latBuffer = latSpacing*0.25;
		double lonBuffer = lonSpacing*0.25;
		
		Region region;
		if (latNodes.length * lonNodes.length == nodeList.size()) {
			// simple, rectangular
			region = new Region(new Location(latNodes[0]-latBuffer, lonNodes[0]-lonBuffer),
					new Location(latNodes[latNodes.length-1]+latBuffer, lonNodes[lonNodes.length-1]+lonBuffer));
		} else {
			// build region that surrounds the nodes
			LocationList border = new LocationList();

			// move up the left edge of the region
			for (int i=0; i<latNodes.length; i++) {
				double lat = latNodes[i];
				Range<Double> lonRange = lonRangeAtLat(nodeList, (float)lat);
				double lon = lonRange.lowerEndpoint() - lonBuffer;
				if (i == 0)
					border.add(new Location(lat-latBuffer, lon));
				else if (i == latNodes.length-1)
					border.add(new Location(lat+latBuffer, lon));
				else
					border.add(new Location(lat, lon));
			}
			
//			// move along the upper edge
//			for (int i=0; i<lonNodes.length; i++) {
//				double lon = lonNodes[i];
//				// move up the left edge of the region
//				Range<Double> latRange = latRangeAtLon(nodeList, (float)lon);
//				double latBuffer = latRange.upperEndpoint() + halfLatSpacing;
//				border.add(new Location(latBuffer, lon));
//			}
			
			// move down right side
			for (int i=latNodes.length; --i>=0;) {
				double lat = latNodes[i];
				Range<Double> lonRange = lonRangeAtLat(nodeList, (float)lat);
				double lon = lonRange.upperEndpoint()+lonBuffer;
				if (i == latNodes.length-1)
					border.add(new Location(lat+latBuffer, lon));
				else if (i == 0)
					border.add(new Location(lat-latBuffer, lon));
				else
					border.add(new Location(lat, lon));
			}
			
//			// move back along the bottom edge
//			for (int i=lonNodes.length; --i>=0;) {
//				double lon = lonNodes[i];
//				// move up the left edge of the region
//				Range<Double> latRange = latRangeAtLon(nodeList, (float)lon);
//				double latBuffer = latRange.lowerEndpoint() - halfLatSpacing;
//				border.add(new Location(latBuffer, lon));
//			}
			
			region = new Region(border, BorderType.MERCATOR_LINEAR);
		}
		
		return new GriddedRegion(region, latNodes, lonNodes, latSpacing, lonSpacing, nodeList.get(0), nodeList);
	}
	
	private static Range<Double> lonRangeAtLat(LocationList nodeList, float lat) {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		for (Location loc : nodeList) {
			if ((float)loc.getLatitude() == lat) {
				double lon = loc.getLongitude();
				max = Double.max(max, lon);
				min = Double.min(min, lon);
			}
		}
		Preconditions.checkState(Double.isFinite(min) && Double.isFinite(max), "No nodes found at lat=%s", lat);
		return Range.closed(min, max);
	}
	
	public static void main(String[] args) throws IOException {
		List<GriddedRegion> inputRegions = new ArrayList<>();
		
		GriddedRegion simple = new GriddedRegion(new Region(new Location(34, -118), new Location(36, -120)), 0.25, null);
		simple.setName("rectangular");
		inputRegions.add(simple);
		
		GriddedRegion circular = new GriddedRegion(new Region(new Location(35, -119), 100d), 0.25, null);
		circular.setName("circular");
		inputRegions.add(circular);
		
		GriddedRegion relm = new CaliforniaRegions.RELM_TESTING_GRIDDED(0.25d);
		relm.setName("relm");
		inputRegions.add(relm);
		
		File outputDir = new File("/tmp/grid_reg_test");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		for (GriddedRegion input : inputRegions) {
			System.out.println("Processing "+input.getName());
			Feature.write(input.toFeature(), new File(outputDir, input.getName()+"_input.geojson"));
			GriddedRegion inferred = inferRegion(input.getNodeList());
			Feature.write(inferred.toFeature(), new File(outputDir, input.getName()+"_inferred.geojson"));
		}
	}

}
