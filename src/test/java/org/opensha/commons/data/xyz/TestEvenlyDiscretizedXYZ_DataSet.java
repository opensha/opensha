package org.opensha.commons.data.xyz;

import static org.junit.Assert.*;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Test;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.commons.exceptions.InvalidRangeException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.FileUtils;

public class TestEvenlyDiscretizedXYZ_DataSet {

	protected static final int ncols = 10;
	protected static final int nrows = 5;
	protected static final double minX = 0.5;
	protected static final double minY = 1.5;
	protected static final double gridSpacing = 0.15;
	
	private EvenlyDiscrXYZ_DataSet buildTestData() {
		EvenlyDiscrXYZ_DataSet data = new EvenlyDiscrXYZ_DataSet(ncols, nrows, minX, minY, gridSpacing);
		
		for (int i=0; i<data.size(); i++) {
			data.set(i, (double)i * 0.01);
		}
		
		return data;
	}
	
	@Test
	public void testConstructors() {
		EvenlyDiscrXYZ_DataSet data = buildTestData();
		
		assertTrue("ncols not set correctly", ncols == data.getNumX());
		assertTrue("nrows not set correctly", nrows == data.getNumY());

		assertEquals("gridSpacing not set correctly", gridSpacing, data.getGridSpacingX(), 0.00000001);
		assertEquals("gridSpacing not set correctly", gridSpacing, data.getGridSpacingY(), 0.00000001);
		
//		double maxX = minX + gridSpacing * (ncols-1);
		
	}
	
	@Test
	public void testMinMax() {
		EvenlyDiscrXYZ_DataSet data = buildTestData();
		
		double maxX = 1.85;
		double maxY = 2.1;
		
		double minZ = 0.0;
		double maxZ = (data.size()-1) * 0.01;
		
		assertEquals("minX not set correctly", minX, data.getMinX(), 0.00000001);
		assertEquals("minY not set correctly", minY, data.getMinY(), 0.00000001);
		assertEquals("minZ not set correctly", minZ, data.getMinZ(), 0.00000001);
		assertEquals("maxX not set correctly", maxX, data.getMaxX(), 0.00000001);
		assertEquals("maxY not set correctly", maxY, data.getMaxY(), 0.00000001);
		assertEquals("maxZ not set correctly", maxZ, data.getMaxZ(), 0.00000001);
	}
	
	@Test
	public void testGet() {
		EvenlyDiscrXYZ_DataSet data = buildTestData();
		
		for (int i=0; i<data.size(); i++) {
			double valByIndex = data.get(i);
			double valByIndexPt = data.get(data.getPoint(i));
			double valByIndexDoubleDouble = data.get(data.getPoint(i).getX(), data.getPoint(i).getY());
			
			assertEquals("val by index point isn't equal", valByIndex, valByIndexPt, 0d);
			assertEquals("val by index doubles isn't equal", valByIndex, valByIndexDoubleDouble, 0d);
		}
	}
	
	@Test
	public void testSet() {
		EvenlyDiscrXYZ_DataSet data = buildTestData();
		try {
			data.set(0, minY, 0);
			fail("Should throw InvalidRangeException because x less than minX");
		} catch (InvalidRangeException e) {}
		
		try {
			data.set(minX, 0, 0);
			fail("Should throw InvalidRangeException because y less than minY");
		} catch (InvalidRangeException e) {}
		
		try {
			data.set(data.getMaxX() + 1, minY, 0);
			fail("Should throw InvalidRangeException because x greater than maxX");
		} catch (InvalidRangeException e) {}
		
		try {
			data.set(minX, data.getMaxY() + 1, 0);
			fail("Should throw InvalidRangeException because y greater than maxY");
		} catch (InvalidRangeException e) {}
		
		data.set(minX, minY, 0.35);
		assertEquals("set didn't work", 0.35, data.get(minX, minY), 0.0000001);
		assertEquals("set didn't work", 0.35, data.get(0, 0), 0.0000001);
		
		data.set(minX + 0.06, minY + 0.06, 0.35);
		assertEquals("set didn't work", 0.35, data.get(minX, minY), 0.0000001);
		assertEquals("set didn't work", 0.35, data.get(0, 0), 0.0000001);
		
		data.set(minX + gridSpacing - 0.06, minY + gridSpacing - 0.06, 0.35);
		assertEquals("set didn't work", 0.35, data.get(minX + gridSpacing, minY + gridSpacing), 0.0000001);
		assertEquals("set didn't work", 0.35, data.get(1, 1), 0.0000001);
		
		data.set(data.getPoint(0), 5.5);
		assertEquals("set by point didn't work", 5.5, data.get(0), 0d);
		data.set(data.indexOf(data.getPoint(0)), 5.6);
		assertEquals("set by index didn't work", 5.6, data.get(0), 0d);
	}
	
	@Test
	public void testBinaryIO() throws IOException {
		File tempDir = FileUtils.createTempDir();
		String fileNamePrefix = tempDir.getAbsolutePath() + File.separator + "data";
		EvenlyDiscrXYZ_DataSet data = buildTestData();
		
		for (int row=0; row<nrows; row++) {
			for (int col=0; col<ncols; col++) {
				data.set(col, row, Math.random());
			}
		}
		
		data.writeXYZBinFile(fileNamePrefix);
		
		EvenlyDiscrXYZ_DataSet newData = EvenlyDiscrXYZ_DataSet.readXYZBinFile(fileNamePrefix);
		
		for (int row=0; row<nrows; row++) {
			for (int col=0; col<ncols; col++) {
				double origVal = data.get(col, row);
				double newVal = newData.get(col, row);
				
				assertEquals("", origVal, newVal, 0.00001);
			}
		}
		
		FileUtils.deleteRecursive(tempDir);
	}
	
	@Test
	public void testContains() {
		EvenlyDiscrXYZ_DataSet data = buildTestData();
		
		assertTrue("data doesn't contain origin!", data.contains(new Point2D.Double(minX, minY)));
		assertTrue("data doesn't contain last point!",
				data.contains(new Point2D.Double(data.getMaxX(), data.getMaxY())));
		assertTrue("data doesn't contain origin plus a little!",
				data.contains(new Point2D.Double(minX+gridSpacing*0.0001, minY+gridSpacing*0.0001)));
		assertFalse("data contains vals before origin!", data.contains(new Point2D.Double(minX-0.001, minY)));
		assertFalse("data contains vals before origin!", data.contains(new Point2D.Double(minX, minY-0.001)));
		assertFalse("data contains vals before origin!", data.contains(new Point2D.Double(minX-0.001, minY-0.001)));
		
		assertFalse("data contains vals after last point!",
				data.contains(new Point2D.Double(data.getMaxX()+0.001, data.getMaxY())));
		assertFalse("data contains vals after last point!",
				data.contains(new Point2D.Double(data.getMaxX(), data.getMaxY()+0.001)));
		assertFalse("data contains vals after last point!",
				data.contains(new Point2D.Double(data.getMaxX()+0.001, data.getMaxY()+0.001)));
	}
	
	@Test
	public void testIndexOf() {
		EvenlyDiscrXYZ_DataSet data = buildTestData();
		
		assertEquals("origin should be index 0", 0, data.indexOf(new Point2D.Double(minX, minY)));
		assertEquals("lat point should be last index", data.size()-1,
				data.indexOf(new Point2D.Double(data.getMaxX(), data.getMaxY())));
	}
	
	@Test
	public void testSetAll() {
		EvenlyDiscrXYZ_DataSet origData = buildTestData();
		EvenlyDiscrXYZ_DataSet data = (EvenlyDiscrXYZ_DataSet)origData.clone();
		EvenlyDiscrXYZ_DataSet constData = buildTestData();
		double constVal = 1.2345;
		for (int i=0; i<constData.size(); i++)
			constData.set(i, constVal);
		
		data.setAll(constData);
		
		for (int i=0; i<data.size(); i++)
			assertEquals("setAll didn't work!", constVal, data.get(i), 0.0000001);
		
		data.setAll(origData);
		for (int i=0; i<data.size(); i++)
			assertEquals("setAll didn't work!", origData.get(i), data.get(i), 0.0000001);
	}
	
	@Test
	public void testGetLists() {
		EvenlyDiscrXYZ_DataSet data = buildTestData();
		List<Point2D> pointList = data.getPointList();
		List<Double> valList = data.getValueList();
		
		assertEquals("point list size incorrect", data.size(), pointList.size());
		assertEquals("value list size incorrect", data.size(), valList.size());
		
		for (int i=0; i<data.size(); i++) {
			assertEquals("point from list doesn't equal point at index", data.getPoint(i), pointList.get(i));
			assertEquals("value from list doesn't equal value at index", data.get(i), valList.get(i), 0d);
		}
	}
	
	@Test
	public void testInterpolation() {
		EvenlyDiscrXYZ_DataSet data = buildTestData();
		
		// test each node point
		for (int x=0; x<ncols; x++) {
			for (int y=0; y<nrows; y++) {
				assertEquals("interpolation doesn't work at data points",
						data.get(x, y), data.bilinearInterpolation(data.getX(x), data.getY(y)), 0.00001);
			}
		}
		
		// text values along each row
		for (int xInd=1; xInd<ncols; xInd++) {
			for (int yInd=0; yInd<nrows; yInd++) {
				double x0 = data.getX(xInd-1);
				double x1 = data.getX(xInd);
				double x = x0 + 0.5*(x1 - x0);
				double y = data.getY(yInd);
				
				double x0Val = data.get(xInd-1, yInd);
				double x1Val = data.get(xInd, yInd);
				double midXVal = 0.5*(x0Val + x1Val);
				assertEquals("interpolation doesn't work at data mid points points along rows",
						midXVal, data.bilinearInterpolation(x, y), 0.00001);
			}
		}
		
		// text values along each row
		for (int xInd=0; xInd<ncols; xInd++) {
			for (int yInd=1; yInd<nrows; yInd++) {
				double y0 = data.getY(yInd-1);
				double y1 = data.getY(yInd);
				double y = y0 + 0.5*(y1 - y0);
				double x = data.getX(xInd);
				
				double y0Val = data.get(xInd, yInd-1);
				double y1Val = data.get(xInd, yInd);
				double midYVal = 0.5*(y0Val + y1Val);
				assertEquals("interpolation doesn't work at data mid points points along columns",
						midYVal, data.bilinearInterpolation(x, y), 0.00001);
			}
		}
	}

}
