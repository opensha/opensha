package org.opensha.commons.geo;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.BeforeClass;
import org.junit.Test;


public class RegionUtilsTest {
	
	private static Region testRegion;
	
	@BeforeClass
	public static void setUpBeforeClass() {
		testRegion = new Region(new Location(0, 0), new Location(1, 1));
	}
	
	@Test (expected=NullPointerException.class)
	public void testGetFractionInside_nullRegion() {
		ArrayList<Location> locs = new ArrayList<Location>();
		locs.add(new Location(34, -118));
		RegionUtils.getFractionInside(null, locs);
	}
	
	@Test (expected=NullPointerException.class)
	public void testGetFractionInside_nullLocs() {
		RegionUtils.getFractionInside(testRegion, null);
	}
	
	@Test (expected=IllegalArgumentException.class)
	public void testGetFractionInside_emptyLocs() {
		RegionUtils.getFractionInside(testRegion, new ArrayList<Location>());
	}
	
	@Test
	public void testGetFractionInside_allInside() {
		ArrayList<Location> locs = new ArrayList<Location>();
		locs.add(new Location(0.5, 0.5));
		
		assertEquals(1d, RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);
		
		// add the same location again, should still be 1
		locs.add(new Location(0.5, 0.5));
		assertEquals(1d, RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);

		locs.add(new Location(0.4, 0.5));
		assertEquals(1d, RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);
		
		locs.add(new Location(0.5, 0.6));
		assertEquals(1d, RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);
		
		locs.add(new Location(0.1, 0.9));
		assertEquals(1d, RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);
	}
	
	@Test
	public void testGetFractionInside_allOutside() {
		ArrayList<Location> locs = new ArrayList<Location>();
		locs.add(new Location(-0.5, -0.5));
		
		assertEquals(0d, RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);
		
		// add the same location again, should still be 0
		locs.add(new Location(-0.5, -0.5));
		assertEquals(0d, RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);

		locs.add(new Location(-0.4, -0.5));
		assertEquals(0d, RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);
		
		locs.add(new Location(-0.5, -0.6));
		assertEquals(0d, RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);
		
		locs.add(new Location(-0.1, -0.9));
		assertEquals(0d, RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);
	}
	
	@Test
	public void testGetFractionInside_someInside() {
		ArrayList<Location> locs = new ArrayList<Location>();
		// first put one inside, all others will be outside
		locs.add(new Location(0.5, 0.5));
		
		assertEquals(1d/(double)locs.size(), RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);
		
		locs.add(new Location(-0.5, -0.5));
		assertEquals(1d/(double)locs.size(), RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);

		locs.add(new Location(-0.4, -0.5));
		assertEquals(1d/(double)locs.size(), RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);
		
		locs.add(new Location(-0.5, -0.6));
		assertEquals(1d/(double)locs.size(), RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);
		
		locs.add(new Location(-0.1, -0.9));
		assertEquals(1d/(double)locs.size(), RegionUtils.getFractionInside(testRegion, locs), Double.MIN_VALUE);
	}
}
