package org.opensha.commons.data.xyz;

import static org.junit.Assert.*;

import java.awt.geom.Point2D;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;

public class TestGriddedRegionDataSet {

	private GriddedRegion grid;
	private GriddedGeoDataSet data;
	
	@Before
	public void setUp() throws Exception {
		grid = new CaliforniaRegions.RELM_TESTING_GRIDDED(0.5);
		buildData(true);
	}
	
	private void buildData(boolean latitudeX) {
		data = new GriddedGeoDataSet(grid, latitudeX);
		
		for (int i=0; i<data.size(); i++) {
			data.set(i, (double)i);
		}
	}

	@Test
	public void testSize() {
		assertEquals("sizes not eual", grid.getNodeCount(), data.size());
	}
	
	@Test
	public void testGetLocation() {
		for (int i=0; i<data.size(); i++) {
			Location gridLoc = grid.getNodeList().get(i);
			assertEquals("locations by index don't match", gridLoc, data.getLocation(i));
		}
	}
	
	@Test
	public void testIndexOf() {
		for (int i=0; i<data.size(); i++) {
			Location gridLoc = grid.getNodeList().get(i);
			assertEquals("index of loc doesn't match", i, data.indexOf(gridLoc));
			assertEquals("index of loc doesn't match", i, data.indexOf(data.getLocation(i)));
			assertEquals("index of point doesn't match", i, data.indexOf(data.getPoint(i)));
		}
	}
	
	@Test
	public void testGet() {
		for (int i=0; i<data.size(); i++) {
			Location gridLoc = grid.getNodeList().get(i);
			double valByIndex = data.get(i);
			double valByLoc = data.get(gridLoc);
			double valByIndexLoc = data.get(data.getLocation(i));
			double valByIndexPt = data.get(data.getPoint(i));
			double valByIndexDoubleDouble = data.get(data.getPoint(i).getX(), data.getPoint(i).getY());
			
			assertEquals("val by index doesn't match counter", (double)i, valByIndex, 0d);
			assertEquals("val by loc isn't equal", valByIndex, valByLoc, 0d);
			assertEquals("val by index loc isn't equal", valByIndexLoc, valByLoc, 0d);
			assertEquals("val by index point isn't equal", valByIndexPt, valByLoc, 0d);
			assertEquals("val by index doubles isn't equal", valByIndexDoubleDouble, valByLoc, 0d);
		}
	}
	
	@Test
	public void testMinMax() {
		data.setLatitudeX(true);
		assertEquals("min lat isn't correct", LocationUtils.calcMinLat(grid.getNodeList()), data.getMinX(), 0d);
		assertEquals("max lat isn't correct", LocationUtils.calcMaxLat(grid.getNodeList()), data.getMaxX(), 0d);
		assertEquals("min lon isn't correct", LocationUtils.calcMinLon(grid.getNodeList()), data.getMinY(), 0d);
		assertEquals("max lon isn't correct", LocationUtils.calcMaxLon(grid.getNodeList()), data.getMaxY(), 0d);
		data.setLatitudeX(false);
		assertEquals("min lat isn't correct", LocationUtils.calcMinLon(grid.getNodeList()), data.getMinX(), 0d);
		assertEquals("max lat isn't correct", LocationUtils.calcMaxLon(grid.getNodeList()), data.getMaxX(), 0d);
		assertEquals("min lon isn't correct", LocationUtils.calcMinLat(grid.getNodeList()), data.getMinY(), 0d);
		assertEquals("max lon isn't correct", LocationUtils.calcMaxLat(grid.getNodeList()), data.getMaxY(), 0d);
		MinMaxAveTracker tracker = new MinMaxAveTracker();
		for (int i=0; i<data.size(); i++) {
			tracker.addValue(data.get(i));
		}
		assertEquals("min val isn't correct", tracker.getMin(), data.getMinZ(), 0d);
		assertEquals("max val isn't correct", tracker.getMax(), data.getMaxZ(), 0d);
	}
	
	private LocationList getBadPointList() {
		LocationList bad = new LocationList();
		
		Location origin = grid.getNodeList().get(0);
		double spacing = grid.getSpacing();
		double halfSpacing = spacing*0.5;
		
		bad.add(new Location(origin.getLatitude() + halfSpacing, origin.getLongitude() + halfSpacing));
//		bad.add(new Location(data.getMi - spacing, origin.getLongitude() + halfSpacing));
		
		return bad;
	}
	
	@Test
	public void testContains() {
		for (int i=0; i<data.size(); i++) {
			Location gridLoc = grid.getNodeList().get(i);
			assertTrue("contains doesn't work from grid loc", data.contains(gridLoc));
			Point2D pt = data.getPoint(i);
			assertTrue("contains doesn't work from point", data.contains(pt));
			assertTrue("contains doesn't work from doubles", data.contains(pt.getX(), pt.getY()));
		}
		
		// test some outside
	}
	
	@Test
	public void testSet() {
		for (int i=0; i<data.size(); i++) {
			Location gridLoc = grid.getNodeList().get(i);
			Point2D pt = data.getPoint(i);
			
			int origSize = data.size();
			data.set(i, 1.1);
			assertEquals("set by index increased size!", origSize, data.size());
			assertEquals("set by index didn't change", 1.1, data.get(i), 0d);
			
			data.set(gridLoc, 1.2);
			assertEquals("set by loc increased size!", origSize, data.size());
			assertEquals("set by loc didn't change", 1.2, data.get(i), 0d);
			
			data.set(pt, 1.3);
			assertEquals("set by pt increased size!", origSize, data.size());
			assertEquals("set by pt didn't change", 1.3, data.get(i), 0d);
			
			data.set(pt.getX(), pt.getY(), 1.4);
			assertEquals("set by doubles increased size!", origSize, data.size());
			assertEquals("set by doubles didn't change", 1.4, data.get(i), 0d);
		}
		
		// now set some that aren't contained
		Location origin = grid.getNodeList().get(0);
		double halfSpacing = grid.getSpacing()*0.5;
		
		try {
			data.set(new Location(data.getMaxLat()+halfSpacing+0.01, data.getMaxLon()+halfSpacing+0.01), 0d);
			fail("Exceptoin should have been thrown setting incorrect point!");
		} catch (InvalidRangeException e) {}
		try {
			data.set(new Location(origin.getLatitude() - grid.getSpacing(), origin.getLongitude() - grid.getSpacing()), 0d);
			fail("Exceptoin should have been thrown setting incorrect point!");
		} catch (InvalidRangeException e) {}
	}
	
	private void doLatXTest(GriddedGeoDataSet data) {
		for (int i=0; i<data.size(); i++) {
			Location gridLoc = grid.getNodeList().get(i);
			Point2D pt = data.getPoint(i);
			if (data.isLatitudeX()) {
				assertEquals("latX == true, but x != lat", gridLoc.getLatitude(), pt.getX(), 0d);
				assertEquals("latX == true, but y != lon", gridLoc.getLongitude(), pt.getY(), 0d);
			} else {
				assertEquals("latX == false, but y != lat", gridLoc.getLatitude(), pt.getY(), 0d);
				assertEquals("latX == false, but x != lon", gridLoc.getLongitude(), pt.getX(), 0d);
			}
		}
	}
	
	@Test
	public void testLatitudeX() {
		buildData(true);
		assertTrue("latX not set correctly in constructor!", data.isLatitudeX());
		doLatXTest(data);
		
		data.setLatitudeX(false);
		assertFalse("latX not set correctly in method!", data.isLatitudeX());
		doLatXTest(data);
		
		buildData(false);
		assertFalse("latX not set correctly in constructor!", data.isLatitudeX());
		doLatXTest(data);
		
		data.setLatitudeX(true);
		assertTrue("latX not set correctly in method!", data.isLatitudeX());
		doLatXTest(data);
	}
	
	@Test
	public void testSetAll() {
		buildData(true);
		GriddedGeoDataSet constData = (GriddedGeoDataSet)data.clone();
		double constVal = 1.2345;
		for (int i=0; i<constData.size(); i++)
			constData.set(i, constVal);
		
		// testing simple case, both latX identical
		data.setAll(constData);
		for (int i=0; i<data.size(); i++)
			assertEquals("data not set correctly in setAll with latX identical", constVal, data.get(i), 0);
		
		buildData(false);
		// testing complex case, both latX identical
		data.setAll(constData);
		for (int i=0; i<data.size(); i++)
			assertEquals("data not set correctly in setAll with latX different", constVal, data.get(i), 0);
		
		buildData(true);
		ArbDiscrXYZ_DataSet nonGeo = new ArbDiscrXYZ_DataSet();
		nonGeo.setAll(constData);
		data.setAll(nonGeo);
		for (int i=0; i<data.size(); i++)
			assertEquals("data not set correctly in setAll with latX equal, non geographic", constVal, data.get(i), 0);
	}

}
