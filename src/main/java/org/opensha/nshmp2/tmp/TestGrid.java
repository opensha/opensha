package org.opensha.nshmp2.tmp;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Set;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;

import com.google.common.primitives.Doubles;

/**
 * Test grids for map based comparisons of OpenSHA and NSHMP fortran codes.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public enum TestGrid {

	// @formatter:off
	NATIONAL(
		new double[] {24.6, 50.0},
		new double[] {-125.0, -65.0 }),
	NATIONAL_POLY(
		getNational()),
	CA(
		new double[] {31.5, 43.0},
		new double[] {-125.4, -113.1}),
	CA_RELM(
		new CaliforniaRegions.RELM_TESTING_GRIDDED()),
	CA_NSHMP(
		getCA_NSHMP()),
	// these permit us to run a 0.1 calcs but with three separate 0.05 anchor shifts
	// which, when combined, make for a complete 0.05 spaced calculation
	CA_NSHMP_N(
		getCA_NSHMP_N()),
	CA_NSHMP_E(
		getCA_NSHMP_E()),
	CA_NSHMP_NE(
		getCA_NSHMP_NE()),
		
	LOS_ANGELES(
		new double[] {35.15,34.23,32.94,33.86},
		new double[] {-119.07,-116.70,-117.42,-119.80}),
	LOS_ANGELES_BIG(
		new double[] {32.0,36.0},
		new double[] {-121.0,-115.0}),
	SAN_FRANCISCO(
		new double[] {37.19,36.43,38.23,39.02},
		new double[] {-120.61,-122.09,-123.61,-122.08}),
	SEATTLE(
		new double[] {46.5,48.5},
		new double[] {-123.5,-121.5}),
	SALT_LAKE_CITY(
		new double[] {39.5,42.0},
		new double[] {-113.0,-111.0}),
	MEMPHIS(
		new double[] {34.0,36.5},
		new double[] {-91.5,-89.0}),
	MEMPHIS_BIG(
		new double[] {33.0,39.0},
		new double[] {-93.0,-88.0}),
	TEST( // small 6 node test grid
		new double[] {33.0, 33.1},
		new double[] {-117.9, -118.1}),
	GRID_TEST(
		new double[] {30.0, 50.0},
		new double[] {-125.0, -100.0}),
	GRID_TEST2(
		new double[] {35.0, 45.0},
		new double[] {-116.0, -111.0}),
	LITTLE_SALMON(
		new double[] {40.0, 41.5},
		new double[] {-125.0, -123.4});
	
	// @formatter:on

	private static final double BOUNDS_OFFSET = 0.0;
	private double[] lats, lons;
	private Region region;
	
	private TestGrid(double[] lats, double[] lons) {
		this.lats = lats;
		this.lons = lons;
	}
	
	private TestGrid(Region region) {
		lats = new double[] {region.getMinLat(), region.getMaxLat()};
		lons = new double[] {region.getMinLon(), region.getMaxLon()};
		this.region = region;
	}

	/**
	 * Initialize and return the associated gridded region.
	 * 
	 * @param spacing
	 * @return the grid
	 */
	public GriddedRegion grid(double spacing) {
		if (region != null) {
//			if (region instanceof GriddedRegion) {
//				return (GriddedRegion) region;
//			}
			return new GriddedRegion(region, spacing, GriddedRegion.ANCHOR_0_0);
		}
		if (lats.length == 2) {
			return new GriddedRegion(new Location(lats[0], lons[0]),
				new Location(lats[1], lons[1]), spacing, GriddedRegion.ANCHOR_0_0);
		}
		LocationList locs = new LocationList();
		for (int i = 0; i < lats.length; i++) {
			locs.add(new Location(lats[i], lons[i]));
		}
		return new GriddedRegion(locs, BorderType.MERCATOR_LINEAR, spacing,
			GriddedRegion.ANCHOR_0_0);
	}

	/**
	 * Returns a bounds array of [minLat, minLon, maxLat, maxLon] that is
	 * encloses the grid. Use for setting gmt map extents.
	 * @return a bounds array
	 */
	public double[] bounds() {
		// @formatter:off
		return new double[] { 
			Doubles.min(lats) - BOUNDS_OFFSET,
			Doubles.max(lats) + BOUNDS_OFFSET,
			Doubles.min(lons) - BOUNDS_OFFSET,
			Doubles.max(lons) + BOUNDS_OFFSET};
		// @formatter:on
	}

	public static void main(String[] args) {
		System.out.println(TestGrid.LOS_ANGELES.grid(0.01).getNodeCount());
		System.out.println(TestGrid.SAN_FRANCISCO.grid(0.01).getNodeCount());
//		System.out.println();
//		RegionUtils.locListToKML(getNationalPoly(), "NationalPoly", Color.ORANGE);
		
//		for (TestGrid tg : TestGrid.values()) {
//			RegionUtils.regionToKML(tg.grid(), "TEST GRID " + tg, Color.ORANGE);
//		}
	}
	
	public static Set<TestGrid> getLocals() {
		return EnumSet.of(LOS_ANGELES, SAN_FRANCISCO, SEATTLE, SALT_LAKE_CITY,
			MEMPHIS);
	}
	
	
	private static GriddedRegion getNational() {
		return new GriddedRegion(getNationalPoly(), BorderType.MERCATOR_LINEAR,
			0.1, GriddedRegion.ANCHOR_0_0);
	}

	private static LocationList getNationalPoly() {
		LocationList locs = new LocationList();
		locs.add(new Location(25.6, -98.2));
		locs.add(new Location(25.6, -96.7));
		locs.add(new Location(27.5, -96.5));
		locs.add(new Location(29.1, -93.5));
		locs.add(new Location(28.7, -90.5));
		locs.add(new Location(28.9, -88.4));
		locs.add(new Location(29.7, -87.7));
		locs.add(new Location(29.2, -83.9));
		locs.add(new Location(25.0, -81.3));
		locs.add(new Location(25.0, -80.0));
		locs.add(new Location(26.7, -79.7));
		locs.add(new Location(30.7, -81.0));
		locs.add(new Location(31.8, -80.6));
		locs.add(new Location(35.3, -75.0));
		locs.add(new Location(37.0, -75.4));
		locs.add(new Location(40.3, -73.3));
		locs.add(new Location(41.2, -69.7));
		locs.add(new Location(43.2, -70.1));
		locs.add(new Location(44.7, -66.5));
		locs.add(new Location(45.9, -67.5));
		locs.add(new Location(46.5, -67.7));
		locs.add(new Location(47.5, -67.7));
		locs.add(new Location(47.5, -69.4));
		locs.add(new Location(45.2, -71.8));
		locs.add(new Location(45.2, -75.4));
		locs.add(new Location(44.0, -77.2));
		locs.add(new Location(43.9, -81.2));
		locs.add(new Location(46.6, -83.6));
		locs.add(new Location(48.4, -88.4));
		locs.add(new Location(49.6, -95.2));
		locs.add(new Location(49.6, -125.8));
		locs.add(new Location(45.6, -124.8));
		locs.add(new Location(40.0, -125.2));
		locs.add(new Location(33.0, -119.9));
		locs.add(new Location(32.2, -117.0));
		locs.add(new Location(32.2, -115.0));
		locs.add(new Location(31.0, -111.0));
		locs.add(new Location(31.0, -106.8));
		locs.add(new Location(28.6, -103.3));
		locs.add(new Location(28.6, -101.3));
		locs.add(new Location(26.2, -99.5));
		locs.add(new Location(25.6, -98.2));
		return locs;
	}
	
	private static GriddedRegion getCA_NSHMP() {
		return new GriddedRegion(getCA_NSHMP_Poly(), BorderType.MERCATOR_LINEAR,
			0.1, GriddedRegion.ANCHOR_0_0);
	}

	private static GriddedRegion getCA_NSHMP_N() {
		return new GriddedRegion(getCA_NSHMP_Poly(), BorderType.MERCATOR_LINEAR,
			0.1, new Location(0.05, 0.0));
	}
	private static GriddedRegion getCA_NSHMP_E() {
		return new GriddedRegion(getCA_NSHMP_Poly(), BorderType.MERCATOR_LINEAR,
			0.1, new Location(0.0, 0.05));
	}
	private static GriddedRegion getCA_NSHMP_NE() {
		return new GriddedRegion(getCA_NSHMP_Poly(), BorderType.MERCATOR_LINEAR,
			0.1, new Location(0.05, 0.05));
	}

	private static LocationList getCA_NSHMP_Poly() {
		LocationList locs = new LocationList();
		locs.add(new Location(45.0,-125.2));
		locs.add(new Location(45.0,-116.5));
		locs.add(new Location(40.5,-116.5));
		locs.add(new Location(36.5,-111.5));
		locs.add(new Location(31.5,-111.5));
		locs.add(new Location(31.5,-117.1));
		locs.add(new Location(31.9,-117.9));
		locs.add(new Location(32.8,-118.4));
		locs.add(new Location(33.7,-121.0));
		locs.add(new Location(34.2,-121.6));
		locs.add(new Location(37.7,-123.8));
		locs.add(new Location(40.2,-125.4));
		locs.add(new Location(40.5,-125.4));	
		return locs;
	}

}
