package org.opensha.commons.data.xyz;

import static org.junit.Assert.*;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.FileUtils;

public class TestArbDiscrGeographicDataSet extends TestArbDiscrXYZ_DataSet {

	@Before
	public void setUp() throws Exception {
	}

	@Override
	protected XYZ_DataSet createEmpty() {
		return createEmpty(true);
	}
	
	protected XYZ_DataSet createEmpty(boolean latitudeX) {
		return new ArbDiscrGeoDataSet(latitudeX);
	}
	
	protected ArbDiscrGeoDataSet createTestData(boolean latitudeX) {
		return createTestData(latitudeX, 0d);
	}
	
	protected static ArbDiscrGeoDataSet createTestData(boolean latitudeX, double add) {
		ArbDiscrGeoDataSet data = new ArbDiscrGeoDataSet(latitudeX);
		
		double lat = -90;
		double lon = -180;
		
		int i=0;
		while (lat+add <= 90 && lon+add <= 180) {
			data.set(new Location(lat+add, lon+add), i++);
			
			lat += 0.5;
			lon += 1.0;
		}
		
		return data;
	}
	
	private static void verifySingleLatX(ArbDiscrGeoDataSet data) {
		for (int i=0; i<data.size(); i++) {
			Point2D pt = data.getPoint(i);
			Location loc = data.getLocation(i);
			
			if (data.isLatitudeX()) {
				assertEquals("LatX == true, but x != lat", loc.getLatitude(), pt.getX(), 0d);
				assertEquals("LatX == true, but y != lon", loc.getLongitude(), pt.getY(), 0d);
			} else {
				assertEquals("LatX == false, but y != lat", loc.getLatitude(), pt.getY(), 0d);
				assertEquals("LatX == false, but x != lon", loc.getLongitude(), pt.getX(), 0d);
			}
		}
	}
	
	private static void verifyLatX(ArbDiscrGeoDataSet data1, ArbDiscrGeoDataSet data2) {
		assertEquals("sizes not equal with different latX", data1.size(), data2.size());
		
		verifySingleLatX(data1);
		verifySingleLatX(data2);
		
		boolean opposite = data1.isLatitudeX() != data2.isLatitudeX();
		
		for (int i=0; i<data1.size(); i++) {
			Point2D pt1 = data1.getPoint(i);
			Point2D pt2 = data2.getPoint(i);
			Location loc1 = data1.getLocation(i);
			Location loc2 = data2.getLocation(i);
			assertEquals("locs not equal", loc1, loc2);
			assertEquals("data not identical with diff latX accessed by loc", data1.get(loc1), data2.get(loc2), 0d);
			assertEquals("data not identical with diff latX accessed by point", data1.get(pt1), data2.get(pt2), 0d);
			
			if (opposite) {
				assertEquals("x1 != y2 when opposite!", pt1.getX(), pt2.getY(), 0d);
				assertEquals("y1 != x2 when opposite!", pt1.getY(), pt2.getX(), 0d);
			} else {
				assertEquals("not opposite, but also not equal!", pt1, pt2);
			}
		}
	}
	
	@Test
	public void testLatitudeX() {
		ArbDiscrGeoDataSet data1 = createTestData(true);
		assertTrue("LatitudeX not set correctly in constructor", data1.isLatitudeX());
		ArbDiscrGeoDataSet data2 = createTestData(false);
		assertFalse("LatitudeX not set correctly in constructor", data2.isLatitudeX());
		
		verifyLatX(data1, data2);
		
		data1.setLatitudeX(false);
		assertFalse("LatitudeX not set correctly via method", data1.isLatitudeX());
		verifyLatX(data1, data2);
		
		data2.setLatitudeX(true);
		assertTrue("LatitudeX not set correctly via methodr", data2.isLatitudeX());
		verifyLatX(data1, data2);
		
		data1.setLatitudeX(true);
		assertTrue("LatitudeX not set correctly via method", data1.isLatitudeX());
		verifyLatX(data1, data2);
	}
	
	@Test
	public void testGetGeo() {
		ArbDiscrGeoDataSet data = createTestData(true);
		
		for (int i=0; i<data.size(); i++) {
			Point2D pt = data.getPoint(i);
			Location loc = data.getLocation(i);
			
			assertEquals("get not equal with pt vs loc", data.get(pt), data.get(loc), 0d);
			assertEquals("get not equal with loc vs ind", data.get(i), data.get(loc), 0d);
		}
	}
	
	@Test
	public void testSetDuplicateLocs() {
		ArbDiscrGeoDataSet xyz = createTestData(true);
		
		int origSize = xyz.size();
		
		xyz.setAll(createTestData(true));
		assertEquals("set all still added duplicates", origSize, xyz.size());
		
		ArbDiscrGeoDataSet diffValsDataSet = createTestData(true);
		diffValsDataSet.add(0.1d);
		xyz.setAll(diffValsDataSet);
		assertEquals("set all still added duplicate locs with diff values", origSize, xyz.size());
		
		ArbDiscrGeoDataSet diffPtsDataSet = createTestData(true, 0.1);
		xyz.setAll(diffPtsDataSet);
		assertEquals("set all didn't add new values", origSize+diffPtsDataSet.size(), xyz.size());
	}
	
	@Test
	public void testWriteReadGeo() throws IOException {
		File tempDir = FileUtils.createTempDir();
		String latXFileName = tempDir.getAbsolutePath() + File.separator + "data_lat_x.xyz";
		String latYFileName = tempDir.getAbsolutePath() + File.separator + "data_lat_y.xyz";
		
		GeoDataSet data = createTestData(true);
		ArbDiscrXYZ_DataSet.writeXYZFile(data, latXFileName);
		ArbDiscrXYZ_DataSet.writeXYZFile(createTestData(false), latYFileName);
		
		GeoDataSet loadedLatX = ArbDiscrGeoDataSet.loadXYZFile(latXFileName, true);
		GeoDataSet loadedLatY = ArbDiscrGeoDataSet.loadXYZFile(latYFileName, false);
		
		FileUtils.deleteRecursive(tempDir);
		
		assertEquals("written/loaded data has incorrect size!", data.size(), loadedLatX.size());
		assertEquals("written/loaded data has incorrect size!", data.size(), loadedLatY.size());
		
		for (int i=0; i<data.size(); i++) {
			assertEquals("written/loaded locs doesn't match!", data.getLocation(i), loadedLatX.getLocation(i));
			assertEquals("written/loaded locs doesn't match!", data.getLocation(i), loadedLatY.getLocation(i));
			assertEquals("written/loaded value doesn't match!", data.get(i), loadedLatX.get(i), xThresh);
			assertEquals("written/loaded value doesn't match!", data.get(i), loadedLatY.get(i), xThresh);
		}
	}
	
	@Test (expected=NullPointerException.class)
	public void testSetLocNull() {
		GeoDataSet xyz = (GeoDataSet)createEmpty();
		
		Location loc = null;
		
		xyz.set(loc, 0d);
	}
	
	@Test
	public void testSetLocNullSizeCorrect() {
		GeoDataSet xyz = (GeoDataSet)createEmpty();
		
		Location loc = null;
		
		try {
			xyz.set(loc, 0d);
		} catch (Exception e) {}
		
		assertEquals("called set(null, 0) and size increased!", 0, xyz.size());
	}
	
	@Test (expected=IndexOutOfBoundsException.class)
	public void testGepLocNegInd() {
		GeoDataSet xyz = (GeoDataSet)createTestData(true);
		
		xyz.getLocation(-1);
	}
	
	@Test
	public void testIndOfNull() {
		GeoDataSet xyz = (GeoDataSet)createEmpty();
		
		Location loc = null;
		
		assertEquals("indexOf(null) should be -1)", -1, xyz.indexOf(loc));
	}
	
	@Test
	public void testContainsNull() {
		GeoDataSet xyz = (GeoDataSet)createTestData(true);
		
		Location loc = null;
		
		assertFalse("contains(null) returned true!", xyz.contains(loc));
	}

}

