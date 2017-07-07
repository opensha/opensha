package org.opensha.sha.magdist;


import java.awt.geom.Point2D;

import org.junit.BeforeClass;
import org.junit.Test;

public class YC_1985_CharMagFreqDistTest {

	private static YC_1985_CharMagFreqDist UOEtest;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		UOEtest = new YC_1985_CharMagFreqDist(4.5, 7.5, 10);
	}

	
	// unsupported set methods
	@Test (expected = UnsupportedOperationException.class)
	public void setUOE1() { UOEtest.set(new Point2D.Double()); }
	@Test (expected = UnsupportedOperationException.class)
	public void setUOE2() { UOEtest.set(1.0,2.0); }
	@Test (expected = UnsupportedOperationException.class)
	public void setUOE3() { UOEtest.set(1, 2.0); }
	
}
