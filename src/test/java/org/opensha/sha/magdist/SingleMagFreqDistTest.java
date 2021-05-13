package org.opensha.sha.magdist;


import org.junit.BeforeClass;
import org.junit.Test;

public class SingleMagFreqDistTest {

//	private static SingleMagFreqDist UOEtest;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
//		UOEtest = new SingleMagFreqDist(4.5, 7.5, 10);
	}
	
	/**
	 * This is basically a dummy test so that there is at least one test while the below
	 * tests are commented out.
	 */
	@Test
	public void testConstructor() {
		new SingleMagFreqDist(4.5, 7.5, 10);
	}

	/*
	 * Commented out for now as the set methods are allowed again (to fix problem with Frankel02).
	// unsupported set methods
	@Test (expected = UnsupportedOperationException.class)
	public void setUOE1() { UOEtest.set(new Point2D.Double()); }
	@Test (expected = UnsupportedOperationException.class)
	public void setUOE2() { UOEtest.set(1.0,2.0); }
	@Test (expected = UnsupportedOperationException.class)
	public void setUOE3() { UOEtest.set(1, 2.0); }
	*/
}
