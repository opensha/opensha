package org.opensha.commons.geo;


/**
 * A <code>BorderType</code> specifies how lines connecting two points on the
 * earth's surface should be represented. A <code>BorderType</code> is required
 * for the initialization of some <code>Region</code>s.<br/>
 * <br/>
 * <img style="padding: 0px 80px; float: right;" 
 * src="{@docRoot}/img/border_differences.jpg"/>The adjacent figure shows that
 * a <code>MERCATOR_LINEAR</code> border between two <code>Location</code>s
 * with the same latitude will follow the corresponding parallel (solid line).
 * The equivalent <code>GREAT_CIRCLE</code> border segment will follow the
 * shortest path between the two <code>Location</code>s (dashed line).<br/>
 * <br/>
 * <br/>
 * 
 * @author Peter Powers
 * @version $Id: BorderType.java 6594 2010-04-15 15:13:06Z pmpowers $
 * @see Region
 * @see Location
 */
public enum BorderType {

	/** 
	 * Defines a {@link Region} border as following a straight
	 * line in a Mercator projection
	 */
	MERCATOR_LINEAR,
	
	/**
	 * Defines a {@link Region} border as following a great circle.
	 */
	GREAT_CIRCLE;
	
}
