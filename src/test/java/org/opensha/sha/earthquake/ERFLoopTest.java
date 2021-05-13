package org.opensha.sha.earthquake;

import static org.junit.Assert.*;

import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;

public class ERFLoopTest {
	
	private ERF erf;

	@Before
	public void setUp() throws Exception {
		erf = new Frankel96_AdjustableEqkRupForecast();
		erf.updateForecast();
	}
	
	@Test
	public void testSourceLooping() {
		Iterator<ProbEqkSource> sourceIt = erf.iterator();
		
		for (int sourceID=0; sourceID<erf.getNumSources(); sourceID++) {
			assertTrue(sourceIt.hasNext());
			
			ProbEqkSource src1 = sourceIt.next();
			ProbEqkSource src2 = erf.getSource(sourceID);
			
			assertEquals(src1.getName(), src2.getName());
			assertEquals(src1.getNumRuptures(), src2.getNumRuptures());
			assertEquals(src1, src2);
			
			Iterator<ProbEqkRupture> rupIt = src1.iterator();
			
			for (int rupID=0; rupID<src1.getNumRuptures(); rupID++) {
				assertTrue(rupIt.hasNext());
				
				ProbEqkRupture rup1 = rupIt.next();
				ProbEqkRupture rup2 = src1.getRupture(rupID);
				
				assertEquals(rup1.getMag(), rup2.getMag(), 1e-10);
				assertEquals(rup1.getProbability(), rup2.getProbability(), 1e-10);
				// surface will not be the same object if surface is generated on the fly each time
				assertEquals(rup1.getRuptureSurface().toString(), rup2.getRuptureSurface().toString());
				assertEquals(rup1.getInfo(), rup2.getInfo());
			}
			assertFalse(rupIt.hasNext());
		}
		assertFalse(sourceIt.hasNext());
	}

}
