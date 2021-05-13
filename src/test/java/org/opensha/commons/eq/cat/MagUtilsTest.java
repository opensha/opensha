package org.opensha.commons.eq.cat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.opensha.commons.eq.MagUtils.*;

import org.junit.Test;
import org.opensha.commons.util.DataUtils;


public class MagUtilsTest {
	
	private static double tol = 1e-12;
	
	// array of mag and moment(Nm) pairs
	private static double[] magDat = {
		0.0, 1.1220184543019652E9,
		1.0, 3.5481338923357605E10,
		2.0, 1.1220184543019653E12,
		3.0, 3.5481338923357600E13,
		3.5, 1.9952623149688828E14,
		4.0, 1.1220184543019652E15,
		4.5, 6.3095734448019430E15,
		5.0, 3.5481338923357604E16,
		5.5, 1.9952623149688829E17,
		6.0, 1.1220184543019653E18,
		6.5, 6.3095734448019425E18,
		7.0, 3.5481338923357600E19,
		7.5, 1.9952623149688830E20,
		8.0, 1.1220184543019653E21,
		8.5, 6.3095734448019430E21,
		9.0, 3.5481338923357603E22 };

	@Test
	public void testMagToMoment() {
		
		// test precalculated values
		for (int i=0; i<magDat.length; i+=2) {
			double calcMoment = magToMoment(magDat[i]);
			double pDiff = DataUtils.getPercentDiff(calcMoment, magDat[i+1]);
			assertTrue(pDiff < tol);
		}
		
		// test values passed through momentToMag first
		for (double moment = 1e10; moment < 1e30; moment *= 5) {
			double calcMoment = magToMoment(momentToMag(moment));
			double pDiff = DataUtils.getPercentDiff(calcMoment, moment);
			assertTrue(pDiff < tol);
		}
	}

	
	@Test
	public void testMomentToMag() {
		
		// test precalculated values
		for (int i=0; i<magDat.length; i+=2) {
			double calcMag = momentToMag(magDat[i+1]);
			double pDiff = DataUtils.getPercentDiff(calcMag, magDat[i]);
			assertTrue(pDiff < tol);
		}

		// test values passed through magToMoment
		for (double mag = -2.0; mag < 10.0; mag += 0.25) {
			double calcMag = momentToMag(magToMoment(mag));
			double pDiff = DataUtils.getPercentDiff(calcMag, mag);
			assertTrue(pDiff < tol);
		}
	}
	
	@Test
	public void testGr_rate() {
		assertEquals(1000, gr_rate(7, 1, 4), 0.0);
		assertEquals(100,  gr_rate(7, 1, 5), 0.0);
		assertEquals(10,   gr_rate(7, 1, 6), 0.0);
	}

}
