package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

import static org.junit.Assert.*;

import org.junit.Test;

// tested against a quick MATLAB codes

public class TestSimplified2021MagAreaRel {

    @Test
    public void testGetMedianMag() {
    	
    	Double [] Areas = {1.,50., 100., 200., 500., 1000., 10000., 100000., 1000000.};
    	Stirling_2021_SimplifiedNZ_MagAreaRel ssc = new Stirling_2021_SimplifiedNZ_MagAreaRel();
    	
    	/**
    	 * rake and regime is not set
    	 */
    	assertTrue(Double.isNaN(ssc.getMedianMag(1, Double.NaN)));
    	
    	/**
    	 * strike-slip 
    	 */
    	ssc.setEpistemicBound("lower");
    	Double [] Mags;
    	Mags = new Double[]{3.650, 5.349, 5.65, 5.951, 6.349, 6.65, 7.65, 8.65, 9.65};
    	for (int i=0; i<9; i++) {
    		assertEquals(Mags[i], ssc.getMedianMag(Areas[i], 0), 0.01);
    	}
    	
    	ssc.setEpistemicBound("upper");
    	Mags = new Double[]{4.300, 5.999, 6.300, 6.601, 6.999, 7.300, 8.300, 9.300, 10.300};
    	for (int i=0; i<9; i++) {
    		assertEquals(Mags[i], ssc.getMedianMag(Areas[i], 0), 0.01);
    	}
    	
    	/**
    	 * reverse
    	 */
    	ssc.setEpistemicBound("lower");
    	Mags = new Double[]{3.95, 5.649, 5.95, 6.251, 6.649, 6.95, 7.95, 8.95, 9.95};
    			
    	for (int i=0; i<9; i++) {
    		assertEquals(Mags[i], ssc.getMedianMag(Areas[i], 90), 0.01);
    	}
    	
    	ssc.setEpistemicBound("upper");
    	Mags = new Double[]{4.300, 5.999, 6.300, 6.601, 6.999, 7.300, 8.300, 9.300, 10.300};
    	for (int i=0; i<9; i++) {
    		assertEquals(Mags[i], ssc.getMedianMag(Areas[i], 90), 0.01);
    	}
    	
    	/**
    	 * normal
    	 */
    	ssc.setEpistemicBound("lower");
    	Mags = new Double[]{3.95, 5.649, 5.95, 6.251, 6.649, 6.95, 7.95, 8.95, 9.95};
    			
    	for (int i=0; i<9; i++) {
    		assertEquals(Mags[i], ssc.getMedianMag(Areas[i], -90), 0.01);
    	}
    	
    	ssc.setEpistemicBound("upper");
    	Mags = new Double[]{4.300, 5.999, 6.300, 6.601, 6.999, 7.300, 8.300, 9.300, 10.300};
    	for (int i=0; i<9; i++) {
    		assertEquals(Mags[i], ssc.getMedianMag(Areas[i], -90), 0.01);
    	}
    	
    	/**
    	 * interface, rake does not mater, regime set to interface
    	 */
    	ssc.setRegime("interface");
    	ssc.setEpistemicBound("lower");
    	Mags = new Double[]{3.60, 5.299, 5.60, 5.901, 6.299, 6.600, 7.600, 8.600, 9.600};
    			
    	for (int i=0; i<9; i++) {
    		assertEquals(Mags[i], ssc.getMedianMag(Areas[i]), 0.01);
    	}
    	
    	ssc.setEpistemicBound("upper");
    	Mags = new Double[] {4.100, 5.799, 6.100, 6.401, 6.799, 7.100, 8.100, 9.100, 10.100};
    	for (int i=0; i<9; i++) {
    		assertEquals(Mags[i], ssc.getMedianMag(Areas[i]), 0.01);
    	}
    
    }

    @Test
    public void testGetMedianArea() {

    	Double [] Mags = {4.0, 5., 5.5, 6., 6.5, 7., 7.5, 8., 8.5, 9.0, 9.5};
    	Stirling_2021_SimplifiedNZ_MagAreaRel ssc = new Stirling_2021_SimplifiedNZ_MagAreaRel();
    	
    	/**
    	 * rake and regime is not set
    	 */
    	assertTrue(Double.isNaN(ssc.getMedianArea(1, Double.NaN)));
    	
    	/**
    	 * strike-slip 
    	 */
    	ssc.setEpistemicBound("lower");
    	Double [] Areas;
    	Areas = new Double[]{2.24, 22.39, 70.80, 223.87, 707.95, 2238.72, 7079.46, 22387.21, 70794.58, 223872.11, 707945.78};
    	for (int i=0; i<9; i++) {
    		assertEquals(Areas[i], ssc.getMedianArea(Mags[i], 0), 0.1);
    	}
    	
    	ssc.setEpistemicBound("upper");
    	Areas = new Double[]{0.50, 5.01, 15.85, 50.12, 158.50, 501.19, 1584.89, 5011.87, 15848.93, 50118.72, 158489.32};
    	for (int i=0; i<9; i++) {
    		assertEquals(Areas[i], ssc.getMedianArea(Mags[i], 0), 0.1);
    	}
    	
    	/**
    	 * reverse
    	 */
    	ssc.setEpistemicBound("lower");
    	Areas = new Double[]{1.12, 11.22, 35.48, 112.20, 354.81, 1122.02, 3548.13, 11220.18, 35481.34, 112201.85, 354813.39};
    			
    	for (int i=0; i<9; i++) {
    		assertEquals(Areas[i], ssc.getMedianArea(Mags[i], 90), 0.1);
    	}
    	
    	ssc.setEpistemicBound("upper");
    	Areas = new Double[]{0.50, 5.01, 15.85, 50.12, 158.49, 501.19, 1584.89, 5011.87, 15848.93, 50118.72, 158489.32};
    	for (int i=0; i<9; i++) {
    		assertEquals(Areas[i], ssc.getMedianArea(Mags[i], 90), 0.1);
    	}
    	
    	/**
    	 * normal
    	 */
    	ssc.setEpistemicBound("lower");
    	Areas = new Double[]{1.12, 11.22, 35.48, 112.20, 354.81, 1122.02, 3548.13, 11220.18, 35481.34, 112201.85, 354813.39};
    			
    	for (int i=0; i<9; i++) {
    		assertEquals(Areas[i], ssc.getMedianArea(Mags[i], -90), 0.1);
    	}
    	
    	ssc.setEpistemicBound("upper");
    	Areas = new Double[]{0.50, 5.01, 15.85, 50.12, 158.49, 501.19, 1584.89, 5011.87, 15848.93, 50118.72, 158489.32};
    	for (int i=0; i<9; i++) {
    		assertEquals(Areas[i], ssc.getMedianArea(Mags[i], -90), 0.1);
    	}
    	
    	/**
    	 * interface, rake does not mater, regime set to interface
    	 */
    	ssc.setRegime("interface");
    	ssc.setEpistemicBound("lower");
    	Areas = new Double[]{2.51, 25.12, 79.43, 251.19, 794.33, 2511.89, 7943.28, 25118.86, 79432.82, 251188.64, 794328.23};
    			
    	for (int i=0; i<9; i++) {
    		assertEquals(Areas[i], ssc.getMedianArea(Mags[i]), 0.1);
    	}
    	
    	ssc.setEpistemicBound("upper");
    	Areas = new Double[] {0.79, 7.94, 25.12, 79.43, 251.19, 794.33, 2511.89, 7943.28, 25118.86, 79432.82, 251188.64};
    	for (int i=0; i<9; i++) {
    		assertEquals(Areas[i], ssc.getMedianArea(Mags[i]), 0.1);
    	}
    	
    }
}
