package org.opensha.commons.geo;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.Location;

//@SuppressWarnings("all")
public class LocationTest {

	private static final double V = 10.0;
	Location location;

	@Before
	public void setUp() throws Exception {
		location = new Location(V,V,V);
	}

	@Test
	public final void testLocationDoubleDouble() {
		Location loc = new Location(V,V);
		assertEquals(loc.getLatitude(), V, 0);
		assertEquals(loc.getLongitude(), V, 0);
		assertEquals(loc.getDepth(), 0, 0);
		
		// exception catching
		try {
			loc = new Location(GeoTools.LAT_MAX + 0.1, 0);
			fail("Illegal argument not caught");
		} catch (IllegalArgumentException ignore) {}
		try {
			loc = new Location(GeoTools.LAT_MIN - 0.1, 0);
			fail("Illegal argument not caught");
		} catch (IllegalArgumentException ignore) {}
		try {
			loc = new Location(0, GeoTools.LON_MAX + 0.1);
			fail("Illegal argument not caught");
		} catch (IllegalArgumentException ignore) {}
		try {
			loc = new Location(0, GeoTools.LON_MIN - 0.1);
			fail("Illegal argument not caught");
		} catch (IllegalArgumentException ignore) {}
	}
	
	@Test
	public final void testLocationDoubleDoubleDouble() {
		Location loc = new Location(V,V,V);
		assertEquals(loc.getLatitude(), V, 0);
		assertEquals(loc.getLongitude(), V, 0);
		assertEquals(loc.getDepth(), V, 0);
		
		// exception catching (lat and lon tested above)
		try {
			loc = new Location(0, 0, GeoTools.DEPTH_MAX + 0.1);
			fail("Illegal argument not caught");
		} catch (IllegalArgumentException ignore) {}
		try {
			loc = new Location(0, 0, GeoTools.DEPTH_MIN - 0.1);
			System.out.println(loc.getDepth());
			fail("Illegal argument not caught");
		} catch (IllegalArgumentException ignore) {}
	}

	@Test
	public final void testGetDepth() {
		assertEquals(location.getDepth(), 10, 0);
	}

	@Test
	public final void testGetLatitude() {
		assertEquals(location.getLatitude(), 10, 0);
	}

	@Test
	public final void testGetLongitude() {
		assertEquals(location.getLongitude(), 10, 0);
	}

	@Test
	public final void testGetLatRad() {
		assertEquals(location.getLatRad(), V * GeoTools.TO_RAD, 0);
	}

	@Test
	public final void testGetLonRad() {
		assertEquals(location.getLonRad(), V * GeoTools.TO_RAD, 0);
	}

	@Test
	public final void testToKML() {
		Location loc = new Location(20,30,10);
		String s = loc.getLongitude() + "," + loc.getLatitude() + 
		   "," + loc.getDepth();
		assertEquals(loc.toKML(), s);
	}

	@Test
	public final void testToString() {
		Location loc = new Location(20, 30, 10);
		String s = String.format("%.5f, %.5f, %.5f", loc.getLatitude(),
			loc.getLongitude(), loc.getDepth());
		assertEquals(loc.toString(), s);
	}

	@Test
	public final void testClone() {
		Location copy = location.clone();
		assertTrue(copy.getLatitude() == location.getLatitude());
		assertTrue(copy.getLongitude() == location.getLongitude());
		assertTrue(copy.getDepth() == location.getDepth());
		assertEquals(copy, location);
	}	

	@Test
	public final void testEquals() {
		// same object
		assertTrue(location.equals(location));
		// different object type
		assertTrue(!location.equals(new String("test")));
		// same values
		Location loc = new Location(V,V,V);
		assertTrue(location.equals(loc));
		// different values
		loc = new Location(V*2,-V*2,0);
		assertTrue(!location.equals(loc));	
	}
	
	@Test
	public final void testHashCode() {
		Location copy = location.clone();
		assertTrue(copy.hashCode() == location.hashCode());
		Location locA = new Location(45, 90, 25);
		Location locB = new Location(90, 45, 25);
		assertTrue(locA.hashCode() != locB.hashCode());
	}	

	@Test
	public final void testCompareTo() {
		ArrayList<Location> locList = new ArrayList<Location>();
		Location l0 = new Location(20, -30);
		locList.add(l0);
		Location l1 = new Location(20, -50);
		locList.add(l1);
		Location l2 = new Location(-10, 10);
		locList.add(l2);
		Location l3 = new Location(-10, 30);
		locList.add(l3);
		Location l4 = new Location(40, 10);
		locList.add(l4);
		
		Collections.sort(locList);
		
		assertTrue(locList.get(0) == l2);
		assertTrue(locList.get(1) == l3);
		assertTrue(locList.get(2) == l1);
		assertTrue(locList.get(3) == l0);
		assertTrue(locList.get(4) == l4);
	}
	
	@Test
	public final void testDegreeSerialization() {
		// test that new Locations created with the result of getL*titude() methods are identical
		Location[] inputLocs = {
				location, // standard test loc
				new Location(37.09084333164281, V, V) // a latitude known to fail this test if values only stored in radians
		};
		for (Location testLoc : inputLocs) {
			Location loc = new Location(testLoc.getLatitude(), testLoc.getLongitude(), testLoc.getDepth());
			assertTrue(testLoc.equals(loc));
			assertTrue(testLoc.lat == loc.lat);
			assertTrue(testLoc.lon == loc.lon);
			assertTrue(testLoc.latRad == loc.latRad);
			assertTrue(testLoc.lonRad == loc.lonRad);
		}
	}

}
