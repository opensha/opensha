package org.opensha.nshmp2.imr.impl;

import static junit.framework.Assert.*;
import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.nshmp2.imr.impl.FrankelEtAl_1996_AttenRel.GM_Table;
import org.opensha.nshmp2.util.Utils;

/**
 * Add comments here
 *
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class FrankelEtAl_1996_Tests {

	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {}

	
	
	private static final double TABLE_TOL = 0.00000000001;
	/* 
	 * Test ground motion table and parser; simply tests that indexing and
	 * lookup is working satisfactorily by examining a few values including
	 * some extrema above and below the min and max mag and distance. Table
	 * implementation returns the natural log of ground motion, however,
	 * original values are base-10 so we convert back
	 */
	@Test
	public void testGroundMotionTable() {
		try {
			GM_Table gmt = new GM_Table(Utils.getResource("/imr/pgak01l.tbl"));
			double testVal;
			
			// extrema
			testVal = gmt.get(2, 0.1) / Utils.LOG_BASE_10_TO_E;
			assertEquals(-0.71, testVal, TABLE_TOL);

			testVal = gmt.get(5.8, 0.1) / Utils.LOG_BASE_10_TO_E;
			assertEquals(-0.14, testVal, TABLE_TOL);

			testVal = gmt.get(8.3, 0.1) / Utils.LOG_BASE_10_TO_E;
			assertEquals(0.60, testVal, TABLE_TOL);

			
			testVal = gmt.get(2, 100) / Utils.LOG_BASE_10_TO_E;
			assertEquals(-2.17, testVal, TABLE_TOL);

			testVal = gmt.get(5.8, 100) / Utils.LOG_BASE_10_TO_E;
			assertEquals(-1.43, testVal, TABLE_TOL);

			testVal = gmt.get(8.3, 100) / Utils.LOG_BASE_10_TO_E;
			assertEquals(-0.52, testVal, TABLE_TOL);


			testVal = gmt.get(2, 1001) / Utils.LOG_BASE_10_TO_E;
			assertEquals(-4.48, testVal, TABLE_TOL);

			testVal = gmt.get(5.8, 1001) / Utils.LOG_BASE_10_TO_E;
			assertEquals(-3.35, testVal, TABLE_TOL);

			testVal = gmt.get(8.3, 1001) / Utils.LOG_BASE_10_TO_E;
			assertEquals(-2.05, testVal, TABLE_TOL);
			
			
			// interpolation
			testVal = gmt.get(5.1, Math.pow(10,1.55)) / Utils.LOG_BASE_10_TO_E;
			assertEquals(-1.1575, testVal, TABLE_TOL);
			
			testVal = gmt.get(7.1, Math.pow(10,1.55)) / Utils.LOG_BASE_10_TO_E;
			assertEquals(-0.3775, testVal, TABLE_TOL);

			testVal = gmt.get(5.1, Math.pow(10,2.55)) / Utils.LOG_BASE_10_TO_E;
			assertEquals(-2.7075, testVal, TABLE_TOL);

			testVal = gmt.get(7.1, Math.pow(10,2.55)) / Utils.LOG_BASE_10_TO_E;
			assertEquals(-1.665, testVal, TABLE_TOL);

		} catch (IOException ioe) {
			ioe.printStackTrace();
			fail("Error reading file");
		}

	}
}
