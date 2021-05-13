package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.analysis;

import java.text.SimpleDateFormat;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;

/**
 * This class is used for UCERF2 verification with NSHMP.
 *
 * This class creates a hazard curves along various longitudinal profiles (as
 * specified by Mark Petersen via email) using MeanUCERF2.
 *
 * Currently, two profiles are implemented at 34.0 and 37.7 N for 3 Intensity
 * Measure Types(IMT) : [PGA, SA at 0.2 sec, SA at 1 sec]. One result
 * spreadsheet is created for each combination.
 *
 * EXEC NOTE: Profiles are implemented as runnables so several may be computed
 * simultaneously.
 *
 * @author vipingupta, pmpowers
 */
public class HazardCurvesVerificationApp {

	private static SimpleDateFormat sdf = new SimpleDateFormat();

	private final static String OUT_DIR = "tmp/NSHM_UCERF2";

	// First Lat profiling
	private final static double LAT1 = 34.0;
	private final static double MIN_LON1 = -123.0; // -119.0;
	private final static double MAX_LON1 = -115.0;

	// Second Lat profilisng
	private final static double LAT2 = 37.7;
	private final static double MIN_LON2 = -123.0;
	private final static double MAX_LON2 = -115.0; // -118.0;
	
	// 

	/** @param args */
	public static void main(String[] args) {
		
//		// BA vs760 34.0N
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "BA", 0.0, 760.0, get_USGS_PGA_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "BA", 0.2, 760.0, get_USGS_SA_0p2_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "BA", 1.0, 760.0, get_USGS_SA_1p0_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "BA", 3.0, 760.0, get_USGS_SA_3p0_Function(), OUT_DIR);
//
//		// BA vs760 37.7N
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "BA", 0.0, 760.0, get_USGS_PGA_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "BA", 0.2, 760.0, get_USGS_SA_0p2_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "BA", 1.0, 760.0, get_USGS_SA_1p0_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "BA", 3.0, 760.0, get_USGS_SA_3p0_Function(), OUT_DIR);
//		
//		// BA vs259 34.0N
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "BA", 0.0, 259.0, get_USGS_PGA_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "BA", 0.2, 259.0, get_USGS_SA_0p2_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "BA", 1.0, 259.0, get_USGS_SA_1p0_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "BA", 3.0, 259.0, get_USGS_SA_3p0_Function(), OUT_DIR);
//
//		// BA vs259 37.7N
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "BA", 0.0, 259.0, get_USGS_PGA_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "BA", 0.2, 259.0, get_USGS_SA_0p2_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "BA", 1.0, 259.0, get_USGS_SA_1p0_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "BA", 3.0, 259.0, get_USGS_SA_3p0_Function(), OUT_DIR);
//
//		// CB vs760 34.0N
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CB", 0.0, 760.0, get_USGS_PGA_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CB", 0.2, 760.0, get_USGS_SA_0p2_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CB", 1.0, 760.0, get_USGS_SA_1p0_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CB", 3.0, 760.0, get_USGS_SA_3p0_Function(), OUT_DIR);
//		
//		// CB vs760 37.7N
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CB", 0.0, 760.0, get_USGS_PGA_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CB", 0.2, 760.0, get_USGS_SA_0p2_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CB", 1.0, 760.0, get_USGS_SA_1p0_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CB", 3.0, 760.0, get_USGS_SA_3p0_Function(), OUT_DIR);
//		
//		// CB vs259 34.0N
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CB", 0.0, 259.0, get_USGS_PGA_Function(), OUT_DIR);
		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CB", 0.2, 259.0, get_USGS_SA_0p2_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CB", 1.0, 259.0, get_USGS_SA_1p0_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CB", 3.0, 259.0, get_USGS_SA_3p0_Function(), OUT_DIR);
//		
//		// CB vs259 37.7N
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CB", 0.0, 259.0, get_USGS_PGA_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CB", 0.2, 259.0, get_USGS_SA_0p2_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CB", 1.0, 259.0, get_USGS_SA_1p0_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CB", 3.0, 259.0, get_USGS_SA_3p0_Function(), OUT_DIR);
//		
//		// CY vs760 34.0N
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CY", 0.0, 760.0, get_USGS_PGA_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CY", 0.2, 760.0, get_USGS_SA_0p2_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CY", 1.0, 760.0, get_USGS_SA_1p0_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CY", 3.0, 760.0, get_USGS_SA_3p0_Function(), OUT_DIR);
//
//		// CY vs760 37.7N
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CY", 0.0, 760.0, get_USGS_PGA_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CY", 0.2, 760.0, get_USGS_SA_0p2_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CY", 1.0, 760.0, get_USGS_SA_1p0_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CY", 3.0, 760.0, get_USGS_SA_3p0_Function(), OUT_DIR);
//		
//		// CY vs259 34.0N
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CY", 0.0, 259.0, get_USGS_PGA_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CY", 0.2, 259.0, get_USGS_SA_0p2_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CY", 1.0, 259.0, get_USGS_SA_1p0_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT1, MIN_LON1, MAX_LON1, "CY", 3.0, 259.0, get_USGS_SA_3p0_Function(), OUT_DIR);
//		
//		// CY vs259 37.7N
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CY", 0.0, 259.0, get_USGS_PGA_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CY", 0.2, 259.0, get_USGS_SA_0p2_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CY", 1.0, 259.0, get_USGS_SA_1p0_Function(), OUT_DIR);
//		new HazardProfileCalculator(LAT2, MIN_LON2, MAX_LON2, "CY", 3.0, 259.0, get_USGS_SA_3p0_Function(), OUT_DIR);

	}

	// 0.0 sec
	// 0.005,0.007,0.0098,0.0137,0.0192,0.0269,0.0376,0.0527,0.0738,0.103,0.145,0.203,0.284,0.397,0.556,0.778,1.09,1.52,2.13
	private static ArbitrarilyDiscretizedFunc get_USGS_PGA_Function() {
		ArbitrarilyDiscretizedFunc f = new ArbitrarilyDiscretizedFunc();
		f.set(.005, 1);
		f.set(.007, 1);
		f.set(.0098, 1);
		f.set(.0137, 1);
		f.set(.0192, 1);
		f.set(.0269, 1);
		f.set(.0376, 1);
		f.set(.0527, 1);
		f.set(.0738, 1);
		f.set(.103, 1);
		f.set(.145, 1);
		f.set(.203, 1);
		f.set(.284, 1);
		f.set(.397, 1);
		f.set(.556, 1);
		f.set(.778, 1);
		f.set(1.09, 1);
		f.set(1.52, 1);
		f.set(2.13, 1);
		return f;
	}

	// 0.2 sec
	// 0.005,0.0075,0.0113,0.0169,0.0253,0.038,0.057,0.0854,0.128,0.192,0.288,0.432,0.649,0.973,1.46,2.19,3.28,4.92,7.38
	private static ArbitrarilyDiscretizedFunc get_USGS_SA_0p2_Function() {
		ArbitrarilyDiscretizedFunc f = new ArbitrarilyDiscretizedFunc();
		f.set(.005, 1);
		f.set(.0075, 1);
		f.set(.0113, 1);
		f.set(.0169, 1);
		f.set(.0253, 1);
		f.set(.0380, 1);
		f.set(.0570, 1);
		f.set(.0854, 1);
		f.set(.128, 1);
		f.set(.192, 1);
		f.set(.288, 1);
		f.set(.432, 1);
		f.set(.649, 1);
		f.set(.973, 1);
		f.set(1.46, 1);
		f.set(2.19, 1);
		f.set(3.28, 1);
		f.set(4.92, 1);
		f.set(7.38, 1);
		return f;
	}

	// 1.0 sec
	// 0.0025,0.00375,0.00563,0.00844,0.0127,0.019,0.0285,0.0427,0.0641,0.0961,0.144,0.216,0.324,0.487,0.73,1.09,1.64,2.46,3.69,5.54
	private static ArbitrarilyDiscretizedFunc get_USGS_SA_1p0_Function() {
		ArbitrarilyDiscretizedFunc f = new ArbitrarilyDiscretizedFunc();
		f.set(.0025, 1);
		f.set(.00375, 1);
		f.set(.00563, 1);
		f.set(.00844, 1);
		f.set(.0127, 1);
		f.set(.0190, 1);
		f.set(.0285, 1);
		f.set(.0427, 1);
		f.set(.0641, 1);
		f.set(.0961, 1);
		f.set(.144, 1);
		f.set(.216, 1);
		f.set(.324, 1);
		f.set(.487, 1);
		f.set(.730, 1);
		f.set(1.09, 1);
		f.set(1.64, 1);
		f.set(2.46, 1);
		f.set(3.69, 1);
		f.set(5.54, 1);
		return f;
	}
	
	// 3.0 sec
	// 0.0025,0.006,0.0098,0.0137,0.0192,0.0269,0.0376,0.0527,0.0738,0.103,0.145,0.203,0.284,0.397,0.556,0.778,1.09,1.52,2.13,3.3
	private static ArbitrarilyDiscretizedFunc get_USGS_SA_3p0_Function() {
		ArbitrarilyDiscretizedFunc f = new ArbitrarilyDiscretizedFunc();
		f.set(.0025, 1);
		f.set(.006, 1);
		f.set(.0098, 1);
		f.set(.0137, 1);
		f.set(.0192, 1);
		f.set(.0269, 1);
		f.set(.0376, 1);
		f.set(.0527, 1);
		f.set(.0738, 1);
		f.set(.103, 1);
		f.set(.145, 1);
		f.set(.203, 1);
		f.set(.284, 1);
		f.set(.397, 1);
		f.set(.556, 1);
		f.set(.778, 1);
		f.set(1.09, 1);
		f.set(1.52, 1);
		f.set(2.13, 1);
		f.set(3.3, 1);
		return f;
	}

}
