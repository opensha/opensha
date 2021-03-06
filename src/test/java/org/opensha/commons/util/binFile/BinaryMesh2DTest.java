package org.opensha.commons.util.binFile;

import static org.junit.Assert.*;

import org.junit.Test;
import org.opensha.commons.util.binFile.BinaryMesh2DCalculator;
import org.opensha.commons.util.binFile.BinaryMesh2DCalculator.DataType;
import org.opensha.commons.util.binFile.BinaryMesh2DCalculator.MeshOrder;

public class BinaryMesh2DTest {
	
	BinaryMesh2DCalculator singleRow;
	BinaryMesh2DCalculator singleCol;
	BinaryMesh2DCalculator rect;
	BinaryMesh2DCalculator rect_fast_yx;

	public BinaryMesh2DTest() {
		singleRow = new BinaryMesh2DCalculator(DataType.FLOAT, 10, 1);
		singleCol = new BinaryMesh2DCalculator(DataType.FLOAT, 1, 10);
		rect = new BinaryMesh2DCalculator(DataType.FLOAT, 7, 11);
		rect_fast_yx = new BinaryMesh2DCalculator(DataType.FLOAT, 7, 11);
		rect_fast_yx.setMeshOrder(MeshOrder.FAST_YX);
	}
	
	@Test
	public void testSingleRow() {
		for (int x=0; x<singleRow.getNX(); x++) {
			long ind = singleRow.calcMeshIndex(x, 0);
			assertTrue(ind == x);
			long fInd = singleRow.calcFileIndex(x, 0);
			assertTrue(fInd == (ind * 4));
		}
	}
	
	@Test
	public void testSingleCol() {
		for (int y=0; y<singleCol.getNY(); y++) {
			long ind = singleCol.calcMeshIndex(0, y);
			assertTrue(ind == y);
			long fInd = singleCol.calcFileIndex(0, y);
			assertTrue(fInd == (ind * 4));
		}
	}
	
	@Test
	public void testRect() {
		for (int x=0; x<rect.getNX(); x++) {
			for (int y=0; y<rect.getNY(); y++) {
				long ind = rect.calcMeshIndex(x, y);
				assertTrue(ind == (x + y*rect.getNX()));
				long fInd = rect.calcFileIndex(x, y);
				assertTrue(fInd == (ind * 4));
			}
		}
	}
	
	@Test
	public void testRectYX() {
		for (int x=0; x<rect_fast_yx.getNX(); x++) {
			for (int y=0; y<rect_fast_yx.getNY(); y++) {
				long ind = rect_fast_yx.calcMeshIndex(x, y);
				assertTrue(ind == (y + x*rect.getNY()));
				long fInd = rect_fast_yx.calcFileIndex(x, y);
				assertTrue(fInd == (ind * 4));
			}
		}
	}
	
	@Test
	public void testIndexToPosXY() {
		doCalcXYTest(rect);
	}
	
	@Test
	public void testIndexToPosYX() {
		doCalcXYTest(rect_fast_yx);
	}
	
	private void doCalcXYTest(BinaryMesh2DCalculator calc) {
		String fast;
		if (calc.getMeshOrder() == MeshOrder.FAST_XY)
			fast = "Fast XY";
		else
			fast = "Fast YX";
		for (int x=0; x<calc.getNX(); x++) {
			for (int y=0; y<calc.getNY(); y++) {
				long ind = calc.calcMeshIndex(x, y);
				long pos = calc.calcFileIndex(x, y);
				
				assertEquals("Calc X incorrect, "+fast+", ind="+ind			, x, calc.calcMeshX(ind));
				assertEquals("Calc Y incorrect, "+fast+", ind="+ind			, y, calc.calcMeshY(ind));
				assertEquals("Calc file X incorrect, "+fast+", pos="+pos	, x, calc.calcFileX(pos));
				assertEquals("Calc file Y incorrect, "+fast+", pos="+pos	, y, calc.calcFileY(pos));
			}
		}
	}

}
