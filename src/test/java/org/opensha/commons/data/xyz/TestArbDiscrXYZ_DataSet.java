package org.opensha.commons.data.xyz;

import static org.junit.Assert.*;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.util.FileUtils;

public class TestArbDiscrXYZ_DataSet {
	
	protected static final double xThresh = 0.000001d;
	
	private static double maxI = 89;

	@Before
	public void setUp() throws Exception {
	}
	
	protected XYZ_DataSet createEmpty() {
		return new ArbDiscrXYZ_DataSet();
	}
	
	@Test
	public void testSetDuplicate() {
		XYZ_DataSet xyz = createEmpty();
		
		assertEquals("initial size should be 0", 0, xyz.size());
		
		xyz.set(0d, 0d, 5d);
		assertEquals(1, xyz.size());
		
		xyz.set(0.1d, 0d, 5d);
		assertEquals(2, xyz.size());
		
		xyz.set(0.1d, 0d, 7d);
		assertEquals(2, xyz.size());
		
		assertEquals("index 0 not set correctly", 5d, xyz.get(0), xThresh);
		assertEquals("replace doesn't work", 7d, xyz.get(1), xThresh);
	}
	
	private XYZ_DataSet getTestData() {
		return getTestData(0d);
	}
	
	private XYZ_DataSet getTestData(double iAdd) {
		XYZ_DataSet xyz = createEmpty();
		
		for (double i=0; i<=maxI; i++) {
			double realI = i+iAdd;
			xyz.set(realI, -realI, realI);
		}
		
		return xyz;
	}
	
	@Test
	public void testGet() {
		XYZ_DataSet xyz = getTestData();
		
		for (int i=0; i<xyz.size(); i++) {
			Point2D pt = xyz.getPoint(i);
			double expected = i;
			double getByIndex = xyz.get(i);
			double getByPt = xyz.get(pt);
			double getByDoubles = xyz.get(pt.getX(), pt.getY());
			
			assertEquals("get by index is incorrect", expected, getByIndex, xThresh);
			assertEquals("get by point is incorrect", expected, getByPt, xThresh);
			assertEquals("get by doubles is incorrect", expected, getByDoubles, xThresh);
		}
	}
	
	@Test
	public void testGetPoint() {
		XYZ_DataSet xyz = getTestData();
		
		for (int i=0; i<xyz.size(); i++) {
			assertEquals("get x by index doesn't work", (double)i, xyz.getPoint(i).getX(), xThresh);
			assertEquals("get y by index doesn't work", (double)-i, xyz.getPoint(i).getY(), xThresh);
			assertEquals("get z by index doesn't work", (double)i, xyz.get(i), xThresh);
			
			assertEquals("indexOf doesn't work", i, xyz.indexOf((double)i, (double)-i));
		}
	}
	
	@Test
	public void testContains() {
		XYZ_DataSet xyz = getTestData();
		
		for (int i=0; i<xyz.size(); i++) {
			Point2D pt = xyz.getPoint(i);
			
			assertTrue("contains by point doesn't work", xyz.contains(pt));
			assertTrue("contains by doubles doesn't work", xyz.contains(pt.getX(), pt.getY()));
		}
		
		assertFalse("contains returning true when it should be false", xyz.contains(xyz.getMinX()-0.1d, xyz.getMinY()-0.1));
		assertFalse("contains returning true when it should be false", xyz.contains(xyz.getMaxX()+0.1d, xyz.getMaxY()+0.1));
	}
	
	@Test
	public void testSetAll() {
		XYZ_DataSet xyz = getTestData();
		
		int origSize = xyz.size();
		
		xyz.setAll(getTestData());
		assertEquals("set all still added duplicates", origSize, xyz.size());
		
		XYZ_DataSet diffValsDataSet = getTestData();
		diffValsDataSet.add(0.1d);
		xyz.setAll(diffValsDataSet);
		assertEquals("set all still added duplicate locs with diff values", origSize, xyz.size());
		
		XYZ_DataSet diffPtsDataSet = getTestData(0.1);
		xyz.setAll(diffPtsDataSet);
		assertEquals("set all didn't add new values", origSize*2, xyz.size());
	}
	
	@Test (expected=IndexOutOfBoundsException.class)
	public void testSetNegInd() {
		XYZ_DataSet xyz = createEmpty();
		
		xyz.set(-1, 0d);
	}
	
	@Test (expected=IndexOutOfBoundsException.class)
	public void testSetEqualSize() {
		XYZ_DataSet xyz = getTestData();
		
		xyz.set(xyz.size(), 0d);
	}
	
	@Test (expected=IndexOutOfBoundsException.class)
	public void testSetTooBig() {
		XYZ_DataSet xyz = getTestData();
		
		xyz.set(xyz.size()+1, 0d);
	}
	
	@Test
	public void testSetNegIndSizeCorrect() {
		XYZ_DataSet xyz = createEmpty();
		
		try {
			xyz.set(-1, 0d);
		} catch (Exception e) {}
		
		assertEquals("called set(-1, 0) and size increased!", 0, xyz.size());
	}
	
	@Test (expected=NullPointerException.class)
	public void testSetNull() {
		XYZ_DataSet xyz = createEmpty();
		
		xyz.set(null, 0d);
	}
	
	@Test
	public void testSetNullSizeCorrect() {
		XYZ_DataSet xyz = createEmpty();
		
		try {
			xyz.set(null, 0d);
		} catch (Exception e) {}
		
		assertEquals("called set(null, 0) and size increased!", 0, xyz.size());
	}
	
	@Test (expected=NullPointerException.class)
	public void testSetAllNull() {
		XYZ_DataSet xyz = createEmpty();
		
		xyz.setAll(null);
	}
	
	@Test
	public void testSetAllNullSizeCorrect() {
		XYZ_DataSet xyz = createEmpty();
		
		try {
			xyz.setAll(null);
		} catch (Exception e) {}
		
		assertEquals("called set(null, 0) and size increased!", 0, xyz.size());
	}
	
	@Test (expected=IndexOutOfBoundsException.class)
	public void testGepPointNegInd() {
		XYZ_DataSet xyz = createEmpty();
		
		xyz.getPoint(-1);
	}
	
	@Test
	public void testIndOfNull() {
		XYZ_DataSet xyz = createEmpty();
		
		assertEquals("indexOf(null) should be -1)", -1, xyz.indexOf(null));
	}
	
	@Test
	public void testContainsNull() {
		XYZ_DataSet xyz = getTestData();
		
		assertFalse("contains(null) returned true!", xyz.contains(null));
	}
	
	@Test
	public void testSet() {
		XYZ_DataSet xyz = getTestData();
		double constVal1 = 1.2345432;
		double constVal2 = 6.78765;
		double constVal3 = 12.25253;
		for (int i=0; i<xyz.size(); i++) {
			Point2D pt = xyz.getPoint(i);
			xyz.set(i, constVal1);
			assertEquals("set by index didn't work", constVal1, xyz.get(i), 0d);
			xyz.set(pt, constVal2);
			assertEquals("set by point didn't work", constVal2, xyz.get(i), 0d);
			xyz.set(pt.getX(), pt.getY(), constVal3);
			assertEquals("set by doubles didn't work", constVal3, xyz.get(i), 0d);
		}
	}
	
	@Test
	public void testClone() {
		XYZ_DataSet xyz = getTestData();
		XYZ_DataSet cloned = xyz.copy();
		
		assertEquals("cloned size incorrect", xyz.size(), cloned.size());
		
		for (int i=0; i<xyz.size(); i++) {
			assertEquals("cloned points not equal", xyz.getPoint(i), cloned.getPoint(i));
			assertEquals("cloned values not equal", xyz.get(i), cloned.get(i), xThresh);
		}
		
		// change the cloned values
		cloned.add(0.1);
		
		for (int i=0; i<xyz.size(); i++) {
			assertTrue("cloned operations are affecting original", xyz.get(i) != cloned.get(i));
		}
	}
	
	@Test
	public void testMinMax() {
		XYZ_DataSet xyz = createEmpty();
		
		assertTrue("getMax* calls should be -infinity when empty", Double.NEGATIVE_INFINITY == xyz.getMaxX());
		assertTrue("getMax* calls should be -infinity when empty", Double.NEGATIVE_INFINITY == xyz.getMaxY());
		assertTrue("getMax* calls should be -infinity when empty", Double.NEGATIVE_INFINITY == xyz.getMaxZ());
		
		assertTrue("getMin* calls should be -infinity when empty", Double.POSITIVE_INFINITY == xyz.getMinX());
		assertTrue("getMin* calls should be -infinity when empty", Double.POSITIVE_INFINITY == xyz.getMinY());
		assertTrue("getMin* calls should be -infinity when empty", Double.POSITIVE_INFINITY == xyz.getMinZ());
		
		xyz = getTestData();
		
		assertEquals("x min is wrong", 0d, xyz.getMinX(), xThresh);
		assertEquals("x max is wrong", maxI, xyz.getMaxX(), xThresh);
		assertEquals("y min is wrong", -maxI, xyz.getMinY(), xThresh);
		assertEquals("y max is wrong", 0d, xyz.getMaxY(), xThresh);
		assertEquals("z min is wrong", 0d, xyz.getMinZ(), xThresh);
		assertEquals("z max is wrong", maxI, xyz.getMaxZ(), xThresh);
	}
	
	@Test
	public void testGetLists() {
		XYZ_DataSet data = getTestData();
		List<Point2D> pointList = data.getPointList();
		List<Double> valList = data.getValueList();
		
		assertEquals("point list size incorrect", data.size(), pointList.size());
		assertEquals("value list size incorrect", data.size(), valList.size());
		
		for (int i=0; i<data.size(); i++) {
			assertEquals("point from list at "+i+" doesn't equal point at index", data.getPoint(i), pointList.get(i));
			assertEquals("value from list at "+i+" doesn't equal value at index", data.get(i), valList.get(i), 0d);
		}
	}
	
	private static void testLoaded(XYZ_DataSet data, XYZ_DataSet loaded) {
		assertEquals("written/loaded data has incorrect size!", data.size(), loaded.size());
		
		for (int i=0; i<data.size(); i++) {
			assertEquals("written/loaded point doesn't match!", data.getPoint(i), loaded.getPoint(i));
			assertEquals("written/loaded value doesn't match!", data.get(i), loaded.get(i), xThresh);
		}
	}
	
	@Test
	public void testWriteReadXYZ() throws IOException {
		File tempDir = FileUtils.createTempDir();
		String fileName = tempDir.getAbsolutePath() + File.separator + "data.xyz";
		
		XYZ_DataSet data = getTestData();
		ArbDiscrXYZ_DataSet.writeXYZFile(data, fileName);
		
		XYZ_DataSet loaded = ArbDiscrXYZ_DataSet.loadXYZFile(fileName);
		
		FileUtils.deleteRecursive(tempDir);
		
		testLoaded(data, loaded);
	}
	
	@Test
	public void testSerialize() throws IOException {
		XYZ_DataSet data = getTestData();
		File tempFile = File.createTempFile("openSHA", "xyz.ser");
		
		FileUtils.saveObjectInFile(tempFile.getAbsolutePath(), data);
		
		XYZ_DataSet loaded = (XYZ_DataSet)FileUtils.loadObject(tempFile.getAbsolutePath());
		
		testLoaded(data, loaded);
		
		tempFile.delete();
	}

}
