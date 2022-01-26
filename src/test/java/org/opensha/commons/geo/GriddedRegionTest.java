package org.opensha.commons.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static org.opensha.commons.geo.LocationUtils.TOLERANCE;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.commons.math3.util.Precision;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;


public class GriddedRegionTest {
	
	// octagonal region - mercator and great circle variants
	// provided for comparison. See KML output from main()
	static GriddedRegion octRegionML;
	static GriddedRegion octRegionGC;

	static int octRegionNodeCountML = 700;
	static int octRegionNodeCountGC = 691;

	@BeforeClass
	public static void setUp(){
		RegionTest.setUp();
		LocationList ll = createOctLocList();
		octRegionML  = new GriddedRegion(
				ll, null, 0.5, GriddedRegion.ANCHOR_0_0);
		octRegionGC  = new GriddedRegion(
				ll, BorderType.GREAT_CIRCLE, 0.5, GriddedRegion.ANCHOR_0_0);
		
	}
	
	private static Location loc(double lat, double lon) {
		return new Location(lat, lon);
	}
	
	private static LocationList createOctLocList() {
		LocationList ll = new LocationList();
		ll.add(loc(25,-115));
		ll.add(loc(25,-110));
		ll.add(loc(30,-105));
		ll.add(loc(35,-105));
		ll.add(loc(40,-110));
		ll.add(loc(40,-115));
		ll.add(loc(35,-120));
		ll.add(loc(30,-120));
		return ll;
	}

	// Only minimal tests are required for GriddedRegion. All constructors
	// call super() so the bulk of constructor argument error checking is
	// handled by RegionTest. Only private method initGrid() nees to be tested.
	
	@Test
	public final void testGriddedRegionLocationLocationDoubleLocation() {
		Location l1 = loc(10,10);
		Location l2 = loc(10.1,10.1);
		Location l3 = loc(15,15);
		GriddedRegion gr;
		
		// test spacing range
		try {
			gr = new GriddedRegion(l1,l3,-3,null);
			fail("Spacing less than 0 not caught");
		} catch (IllegalArgumentException e) {}
		try {
			gr = new GriddedRegion(l1,l3,0,null);
			fail("Spacing of 0 not caught");
		} catch (IllegalArgumentException e) {}
		try {
			gr = new GriddedRegion(l1,l3,6,null);
			fail("Spacing greater than 5 not caught");
		} catch (IllegalArgumentException e) {}
		
		// test anchor setting by examining nodes
		// TODO the values are currently inset 
		gr = new GriddedRegion(l2, l3, 1, null);
		Location loc = gr.locationForIndex(0);
		assertTrue(loc.getLatitude() == 10.1);
		assertTrue(loc.getLongitude() == 10.1);
		gr = new GriddedRegion(l2, l3, 1, GriddedRegion.ANCHOR_0_0);
		loc = gr.locationForIndex(0);
		assertTrue(loc.getLatitude() % 1 == 0);
		assertTrue(loc.getLongitude() % 1 == 0);
		gr = new GriddedRegion(l2, l3, 1, loc(0.65, 0.65));
		loc = gr.locationForIndex(0);
		assertTrue(loc.getLatitude() == 10.65);
		assertTrue(loc.getLongitude() == 10.65);
		
		// test that there are nodes on east and north borders
		gr = new GriddedRegion(l1, l3, 1, GriddedRegion.ANCHOR_0_0);
		assertTrue(LocationUtils.areSimilar(
				gr.locationForIndex(33),
				loc(15,13)));
		assertTrue(LocationUtils.areSimilar(
				gr.locationForIndex(23),
				loc(13,15)));
	}

	@Test
	public final void testGriddedRegionLocationListBorderTypeDoubleLocation() {

		assertTrue("Covered by Region tests and first constructor", true);

		// test serialization
		try {
			// write it
			File objPersist = new File("test_serilaize.obj");
			ObjectOutputStream out = new ObjectOutputStream(
					new FileOutputStream(objPersist));
	        out.writeObject(octRegionML);
	        out.close();
	        // read it
	        ObjectInputStream in = new ObjectInputStream(
					new FileInputStream(objPersist));
	        GriddedRegion gr_in = (GriddedRegion) in.readObject();
	        in.close();
	        assertTrue(octRegionML.equals(gr_in));
	        
	        objPersist.delete();
		} catch (IOException ioe) {
			fail("Serialization Failed: " + ioe.getMessage());
		} catch (ClassNotFoundException cnfe) {
			fail("Deserialization Failed: " + cnfe.getMessage());
		}

	}

	@Test
	public final void testGriddedRegionLocationDoubleDoubleLocation() {
		assertTrue("Covered by Region tests and first constructor", true);
	}

	@Test
	public final void testGriddedRegionLocationListDoubleDoubleLocation() {
		assertTrue("Covered by Region tests and first constructor", true);
	}

	@Test
	public final void testGriddedRegionRegionDoubleLocation() {
		assertTrue("Covered by Region tests and first constructor", true);
	}

	@Test
	public final void testGetSpacing() {
		assertTrue(octRegionML.getSpacing() == 0.5);
	}

	@Test
	public final void testGetNodeCount() {
		assertTrue(octRegionML.getNodeCount() == octRegionNodeCountML);
		assertTrue(octRegionGC.getNodeCount() == octRegionNodeCountGC);
		Location l1 = loc(10,10);
		Location l2 = loc(15,15);
		GriddedRegion gr = new GriddedRegion(
				l1, l2, 1, GriddedRegion.ANCHOR_0_0);
		assertTrue(gr.getNodeCount() == 36);
	}

	@Test
	public final void testIsEmpty() {
		assertTrue(!octRegionML.isEmpty());
		Location l1 = loc(0.2, 0.2);
		Location l2 = loc(0.3, 0.3);
		GriddedRegion gr = new GriddedRegion(l1,l2,1,GriddedRegion.ANCHOR_0_0);
		assertTrue(gr.isEmpty());
	}

	@Test
	public final void testEqualsRegion() {
		LocationList grLL = createOctLocList();
		GriddedRegion gr1 = new GriddedRegion(
				grLL, null, 0.5, GriddedRegion.ANCHOR_0_0);
		assertTrue(gr1.equalsRegion(octRegionML));
		
		// should pass for name change
		gr1.setName("Renamed Gridded Region");
		assertTrue(gr1.equalsRegion(octRegionML));

		// test different anchors
		GriddedRegion gr2 = new GriddedRegion(
				grLL, null, 0.5, loc(0.1,0.1));
		assertTrue(!gr2.equalsRegion(octRegionML));
		
		// test different grid spacing
		GriddedRegion gr3 = new GriddedRegion(
				grLL, null, 1.0, GriddedRegion.ANCHOR_0_0);
		assertTrue(!gr3.equalsRegion(octRegionML));
		
		// test different areas
		grLL.add(loc(25,-120));
		gr1 = new GriddedRegion(
				grLL, null, 0.5, GriddedRegion.ANCHOR_0_0);
		assertTrue(!gr1.equalsRegion(octRegionML));
	}

	@Test
	public final void testEquals() {
		// test absolute identical
		assertEquals(octRegionGC, octRegionGC);
		// test wrong object type
		assertTrue(!octRegionGC.equals(new Object()));
		
		LocationList grLL = createOctLocList();
		GriddedRegion gr1 = new GriddedRegion(
				grLL, null, 0.5, GriddedRegion.ANCHOR_0_0);
		// should be equal
		assertEquals(gr1, octRegionML);
		// should fail for name change
		gr1.setName("Name Changed");
		assertTrue(!octRegionGC.equals(gr1));
	}

	@Test
	public final void testHashCode() {
		assertEquals(octRegionML.hashCode(), octRegionML.hashCode());
		assertTrue(octRegionML.hashCode() != octRegionGC.hashCode());
		GriddedRegion octCopy = new GriddedRegion(
				octRegionML,
				octRegionML.getSpacing(),
				GriddedRegion.ANCHOR_0_0);
		assertEquals(octCopy.hashCode(), octRegionML.hashCode());
		
		// change name
		octCopy.setName("Name Changed");
		assertTrue(octCopy.hashCode() != octRegionML.hashCode());
	}

	@Test
	public final void testClone() {
		GriddedRegion octRegion2 = octRegionGC.clone();
		assertEquals(octRegion2, octRegionGC);
		// test gridded with interior
		GriddedRegion interiorGR = new GriddedRegion(
				RegionTest.interiorRegion, 1, null);
		GriddedRegion interiorGRclone = interiorGR.clone();
		assertEquals(interiorGRclone, interiorGR);
	}
	
	@Test
	public final void testSubRegion() {
		RegionTest.setUp();
		GriddedRegion gr1 = new GriddedRegion(RegionTest.circRegion, 0.5, null);
		
		// test no overlap returns null
		GriddedRegion gr3 = gr1.subRegion(RegionTest.smRectRegion1);
		assertTrue(gr3 == null);
		
		// test an intersection that yields an ampty region
		Location l1 = loc(27.2, -112.2);
		Location l2 = loc(27.3, -112.1);
		Region r1 = new Region(l1,l2);
		GriddedRegion gr5 = octRegionML.subRegion(r1);
		assertTrue(gr5.isEmpty());
		
		// test sub region that shoul dhave one node -- the center of octRegion
		Location l3 = loc(32.4, -112.6);
		Location l4 = loc(32.6, -112.4);
		Region r2 = new Region(l3,l4);
		GriddedRegion gr6 = octRegionML.subRegion(r2);
		assertTrue(gr6.indexForLocation(loc(32.5, -112.5)) == 0);
	}

	@Test
	public final void testSetInterior() {
		Location l1 = loc(0, 0);
		Location l2 = loc(5, 5);
		GriddedRegion gr = new GriddedRegion(l1, l2, 0.1, null);
		try {
			octRegionML.addInterior(gr);
			fail("Unsupported Operation not caught");
		} catch (UnsupportedOperationException uoe) {}
	}

	@Test
	public final void testIterator() {
		int i = 0;
		for (Location loc : octRegionML) {
			assertTrue(octRegionML.contains(loc));
			i += 1;
		}
		assertTrue(i == octRegionNodeCountML);
		i = 0;
		for (Location loc : octRegionGC) {
			assertTrue(octRegionGC.contains(loc));
			i += 1;
		}
		assertTrue(i == octRegionNodeCountGC);
	}

	@Test
	public final void testGetNodeList() {
		assertEquals("octRegionNodeCountML", octRegionNodeCountML, octRegionML.getNodeList().size());
		assertEquals("octRegionNodeCountGC", octRegionNodeCountGC, octRegionGC.getNodeList().size());
	}

	@Test
	public final void testLocationForIndex() {
		Location l1 = loc(10,10);
		Location l2 = loc(15,15);
		GriddedRegion gr1 = new GriddedRegion(l1,l2,1,null);
		Location loc0 = gr1.locationForIndex(0);
		assertEquals("l1", loc0, l1);
		loc0 = gr1.locationForIndex(35);
		assertEquals("l2", loc0, l2);
		
		l1 = loc(10.1,10.1);
		l2 = loc(15,15);
		GriddedRegion gr2 = new GriddedRegion(l1,l2,1,null);
		loc0 = gr2.locationForIndex(0);
		assertEquals("10.1, 10.1", loc0, loc(10.1,10.1));
		loc0 = gr2.locationForIndex(24);
		assertEquals("14.1, 14.1", loc0, loc(14.1,14.1));
	}

	@Test
	public final void testIndexForLocation() {
		Location l1 = loc(10,10);
		Location l2 = loc(15,15);
		Location l3 = loc(10.9, 10.9);
		Location l4 = loc(11, 11);
		GriddedRegion gr = new GriddedRegion(l1,l2,1,null);
		Location result = gr.locationForIndex(gr.indexForLocation(l3));
		assertTrue(result.equals(l4));
		// test low outside values checking insidedness and bin splitting
		result = gr.locationForIndex(gr.indexForLocation(
			loc(9.5, 10.49)));
		assertTrue(result.equals(l1));
		int idx = gr.indexForLocation(loc(9.5, 10.5));
		assertTrue(idx == 1);
		result = gr.locationForIndex(gr.indexForLocation(
			loc(9.5, 9.5)));
		assertTrue(result.equals(l1));
		result = gr.locationForIndex(gr.indexForLocation(
			loc(10.49, 9.5)));
		assertTrue(result.equals(l1));
		idx = gr.indexForLocation(loc(10.5, 9.5));
		assertTrue(idx == 6);
		idx = gr.indexForLocation(loc(9.5, 9.49));
		assertTrue(idx == -1);
		idx = gr.indexForLocation(loc( 9.49, 9.5));
		assertTrue(idx == -1);
		// test high outside values checking insidedness and bin splitting
		result = gr.locationForIndex(gr.indexForLocation(
			loc(15.49, 15)));
		assertTrue(result.equals(l2));
		idx = gr.indexForLocation(loc(15, 14.49));
		assertTrue(idx == 34);
		result = gr.locationForIndex(gr.indexForLocation(
			loc(15.49, 15.49)));
		assertTrue(result.equals(l2));
		result = gr.locationForIndex(gr.indexForLocation(
			loc(10.49, 9.5)));
		assertTrue(result.equals(l1));
		idx = gr.indexForLocation(loc(15, 14.5));
		assertTrue(idx == 35);
		idx = gr.indexForLocation(loc(15.49, 15.49));
		assertTrue(idx == 35);
		idx = gr.indexForLocation(loc(15.5, 15.5));
		assertTrue(idx == -1);
		idx = gr.indexForLocation(loc(15.5, 15.49));
		assertTrue(idx == -1);
		idx = gr.indexForLocation(loc( 15.49, 15.5));
		assertTrue(idx == -1);
		idx = gr.indexForLocation(loc( 15.49, 15.51));
		assertTrue(idx == -1);
		idx = gr.indexForLocation(loc( 15.51, 15.49));
		assertTrue(idx == -1);
		idx = gr.indexForLocation(loc( 15.51, 15.51));
		assertTrue(idx == -1);
	}

	@Test
	public final void testGetMinGridLat() {
		assertTrue(Precision.equals(
				octRegionML.getMinGridLat(), 25.0, TOLERANCE));
	}

	@Test
	public final void testGetMaxGridLat() {
		assertTrue(Precision.equals(
				octRegionML.getMaxGridLat(), 40.0, TOLERANCE));
	}

	@Test
	public final void testGetMinGridLon() {
		assertTrue(Precision.equals(
				octRegionML.getMinGridLon(), -120.0, TOLERANCE));
		assertTrue(Precision.equals(
				octRegionGC.getMinGridLon(), -120.0, TOLERANCE));
	}

	@Test
	public final void testGetMaxGridLon() {
		assertTrue(Precision.equals(
				octRegionML.getMaxGridLon(), -105.0, TOLERANCE));
		assertTrue(Precision.equals(
				octRegionGC.getMaxGridLon(), -105.0, TOLERANCE));
	}

	
	
	
	public static void main(String[] args) {
		
		RegionTest.setUp();
		GriddedRegionTest.setUp();

		// TODO clean
//		Location l1 = loc(10,10);
//		Location l2 = loc(10.1,10.1);
//		Location l3 = loc(15,15);
//		GriddedRegion gr;
//		// test anchor setting by examining nodes
//		// TODO the values are currently inset 
//		gr = new GriddedRegion(l2, l3, 1, null);
//		System.out.println(gr.getNodeList());
//		RegionUtils.regionToKML(
//				gr,
//				"GriddedRegionLocLoc", 
//				Color.ORANGE);

//		Location loc = gr.locationForIndex(0);
//		System.out.println(loc.getLatitude());
//		System.out.println(loc.getLongitude());
//		//assertTrue(loc.getLatitude() == 11.1);
		//assertTrue(loc.getLongitude() == 11.1);

//		//OCT Mercator vs GreatCircle
//		RegionUtils.regionToKML(
//				octRegionML,
//				"GriddedRegionOctML", 
//				Color.RED);
//		RegionUtils.regionToKML(
//				octRegionGC,
//				"GriddedRegionOctGC", 
//				Color.BLUE);
//
//		System.out.println(octRegion.getBorder());
//		
//		// SMALL RECT - includes N and E border nodes due to added offset
//		GriddedRegion GR = new GriddedRegion(
//				RegionTest.smRectRegion2, 0.2, null);
//		RegionUtils.regionToKML(
//				GR,
//				"GriddedRegionLocLoc", 
//				Color.ORANGE);
//
//		// INTERIOR - created with lg loc loc rect and small loc loc rect 2
//		GriddedRegion interiorGR = new GriddedRegion(
//				RegionTest.interiorRegion, 1, null);
//		RegionUtils.regionToKML(
//				interiorGR,
//				"GriddedRegionInterior", 
//				Color.ORANGE);
				
		
		// =================================================================
		// The code below will generate kml files for visual verification that
		// GriddedRegions are being instantiated correctly. These files were
		// the basis of comparison when performing an overhaul of the region
		// package. See below for code to generate gridded regions prior to
		// package modification.
//
//		GriddedRegion eggr;
//		
//		// nocal
//		eggr = new CaliforniaRegions.RELM_NOCAL_GRIDDED();
//		RegionUtils.regionToKML(eggr, "ver_NoCal_new", Color.ORANGE);
//		// relm
//		eggr = new CaliforniaRegions.RELM_GRIDDED();
//		RegionUtils.regionToKML(eggr, "ver_RELM_new", Color.ORANGE);
//		// relm_testing
//		eggr = new CaliforniaRegions.RELM_TESTING_GRIDDED();
//		RegionUtils.regionToKML(eggr, "ver_RELM_testing_new", Color.ORANGE);
//		// socal
//		eggr = new CaliforniaRegions.RELM_SOCAL_GRIDDED();
//		RegionUtils.regionToKML(eggr, "ver_SoCal_new", Color.ORANGE);
//		// wg02
//		eggr = new CaliforniaRegions.WG02_GRIDDED();
//		RegionUtils.regionToKML(eggr, "ver_WG02_new", Color.ORANGE);
//		// wg07
//		eggr = new CaliforniaRegions.WG07_GRIDDED();
//		RegionUtils.regionToKML(eggr, "ver_WG07_new", Color.ORANGE);
//		// relm_collect
//		eggr = new CaliforniaRegions.RELM_COLLECTION_GRIDDED();
//		RegionUtils.regionToKML(eggr, "ver_RELM_collect_new", Color.ORANGE);
		
		// =================================================================
		// The code below initializes and outputs kml for the now
		// deprecated/deleted gridded region constructors. To rerun, one can
		// check out svn Revision 5832 or earlier and copy 
		// RegionUtils.regionToKML() and accessory methods to the project.
		//
		// eggr = new EvenlyGriddedNoCalRegion();
		// RegionUtils.regionToKML(eggr, "ver_NoCal_old", Color.BLUE);
		// relm
		// eggr = new EvenlyGriddedRELM_Region();
		// RegionUtils.regionToKML(eggr, "ver_RELM_old", Color.BLUE);
		// relm_testing
		// eggr = new EvenlyGriddedRELM_TestingRegion();
		// RegionUtils.regionToKML(eggr, "ver_RELM_testing_old", Color.BLUE);
		// socal
		// eggr = new EvenlyGriddedSoCalRegion();
		// RegionUtils.regionToKML(eggr, "ver_SoCal_old", Color.BLUE);
		// wg02
		// eggr = new EvenlyGriddedWG02_Region();
		// RegionUtils.regionToKML(eggr, "ver_WG02_old", Color.BLUE);
		// wg07
		// eggr = new EvenlyGriddedWG07_LA_Box_Region();
		// RegionUtils.regionToKML(eggr, "ver_WG07_old", Color.BLUE);
		// relm_collect
		// eggr = new RELM_CollectionRegion();
		// RegionUtils.regionToKML(eggr, "ver_RELM_collect_old", Color.BLUE);
	}
}
