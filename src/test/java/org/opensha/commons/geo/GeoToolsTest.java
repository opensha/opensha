package org.opensha.commons.geo;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.opensha.commons.geo.GeoTools.*;


public class GeoToolsTest {

	private double[] testLats = {LAT_MIN-0.1, 0.0, LAT_MAX+0.1};
	private double[] testLons = {LON_MIN-0.1, 0.0, LON_MAX+0.1};
	private double[] testDepths = {DEPTH_MIN-0.1, 0.0, DEPTH_MAX+0.1};
	
	private Location p1 = new Location(-90,0);
	private Location p2 = new Location(-45,0);
	private Location p3 = new Location(0,0);
	private Location p4 = new Location(45,0);
	private Location p5 = new Location(90,0);

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	// NPE's for the validation methods are handled by DataUtils tests
	
	@Test (expected = IllegalArgumentException.class)
	public void testValidateLats() { validateLats(testLats); }

	@Test (expected = IllegalArgumentException.class)
	public void testValidateLat1() { validateLat(testLats[0]); }
	
	@Test (expected = IllegalArgumentException.class)
	public void testValidateLat2() { validateLat(testLats[2]); }

	
	@Test (expected = IllegalArgumentException.class)
	public void testValidateLons() { validateLons(testLons); }

	@Test (expected = IllegalArgumentException.class)
	public void testValidateLon1() { validateLon(testLons[0]); }

	@Test (expected = IllegalArgumentException.class)
	public void testValidateLon2() { validateLon(testLons[2]); }

	
	@Test (expected = IllegalArgumentException.class)
	public void testValidateDepths() { validateDepths(testDepths); }

	@Test (expected = IllegalArgumentException.class)
	public void testValidateDepth1() { validateDepth(testDepths[0]); }

	@Test (expected = IllegalArgumentException.class)
	public void testValidateDepth2() { validateDepth(testDepths[2]); }

	
	@Test (expected = NullPointerException.class)
	public void testRadiusAtLocationNPE() { radiusAtLocation(null); }

	public void testRadiusAtLocation() {
		assertEquals(radiusAtLocation(p1), EARTH_RADIUS_POLAR, 0.0001);
		assertEquals(radiusAtLocation(p2), 6367.4895, 0.0001);
		assertEquals(radiusAtLocation(p3), EARTH_RADIUS_EQUATORIAL, 0.0001);
		assertEquals(radiusAtLocation(p4), 6367.4895, 0.0001);
		assertEquals(radiusAtLocation(p5), EARTH_RADIUS_POLAR, 0.0001);
	}

	@Test (expected = NullPointerException.class)
	public void testDegreesLatPerKmNPE() { radiusAtLocation(null); }

	@Test
	public void testDegreesLatPerKm() {
		assertEquals(degreesLatPerKm(p1), 0.009013372994, 0.000000000001);
		assertEquals(degreesLatPerKm(p2), 0.008998174112, 0.000000000001);
		assertEquals(degreesLatPerKm(p3), 0.008983152841, 0.000000000001);
		assertEquals(degreesLatPerKm(p4), 0.008998174112, 0.000000000001);
		assertEquals(degreesLatPerKm(p5), 0.009013372994, 0.000000000001);
	}

	@Test (expected = NullPointerException.class)
	public void testDegreesLonPerKmNPE() { radiusAtLocation(null); }

	@Test
	public void testDegreesLonPerKm() {
		assertEquals(1/degreesLonPerKm(p1), 0.0, 0.000000000001);
		assertEquals(degreesLonPerKm(p2), 0.012704096580, 0.000000000001);
		assertEquals(degreesLonPerKm(p3), 0.008983152841, 0.000000000001);
		assertEquals(degreesLonPerKm(p4), 0.012704096580, 0.000000000001);
		assertEquals(1/degreesLonPerKm(p5), 0.0, 0.000000000001);
	}
	
	@Test
	public void testSecondsToDeg() {
		assertEquals(secondsToDeg(10), (10 / SECONDS_PER_DEGREE), 0);
	}

	@Test
	public void testMinutesToDeg() {
		assertEquals(minutesToDeg(10), (10 / MINUTES_PER_DEGREE), 0);
	}
	
	@Test
	public void testToDecimalDegrees() {
		assertEquals(toDecimalDegrees(1, 30), 1.5, 0);
		assertEquals(toDecimalDegrees(5, 45), 5.75, 0);
		assertEquals(toDecimalDegrees(5, -30), 4.5, 0);
	}
	

}
