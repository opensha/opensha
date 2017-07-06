package org.opensha.commons.data;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.geo.Location;

public class SiteTests {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {}

	@Test
	public final void testEqualsObject() {
		Site s1 = new Site();
		Site s2 = s1;
		assertTrue(s1.equals(s2));
		assertFalse(s1.equals(new Object()));
		s2 = null;
		assertFalse(s1.equals(s2));
		
		s2 = new Site();
		assertTrue(s1.equals(s2));
		s2.setName("testName");
		assertFalse(s1.equals(s2));
		s1.setName("testName");
		assertTrue(s1.equals(s2));
		s1.setName("failName");
		assertFalse(s1.equals(s2));
		s1.setName("TestnamE");
		assertTrue(s1.equals(s2));
		
		s2.setLocation(new Location(34, -118));
		assertFalse(s1.equals(s2));
		s1.setLocation(new Location(35, -118));
		assertFalse(s1.equals(s2));
		s1.setLocation(new Location(34, -118));
		assertTrue(s1.equals(s2));
		
		// 
//		fail("Not yet implemented"); // TODO needs parameter comparison tests
	}

	@Test
	public final void testClone() {
//		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testToString() {
//		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testSite() {
//		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testSiteLocation() {
//		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testSiteLocationString() {
//		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testSetName() {
//		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testGetName() {
//		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testSetLocation() {
//		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testGetLocation() {
//		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testToXMLMetadata() {
//		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testFromXMLMetadata() {
//		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testWriteSitesToXML() {
//		fail("Not yet implemented"); // TODO
	}

	@Test
	public final void testLoadSitesFromXML() {
//		fail("Not yet implemented"); // TODO
	}

}
