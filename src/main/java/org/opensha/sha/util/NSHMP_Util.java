package org.opensha.sha.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrection;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;

/**
 * NSHMP Utilities. These methods are primarily used by {@link NSHMP_2008_CA}.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class NSHMP_Util {

	// internally values are converted and/or scaled up to
	// integers to eliminate decimal precision errors:
	// Mag = (int) M*100
	// Dist = (int) Math.floor(D)
	// Period = (int) P*1000

	// <Mag, <Dist, val>>
	private static Map<Integer, Map<Integer, Double>> rjb_map;
	// <Period <Mag, <Dist, val>>>
	private static Map<Integer, Map<Integer, Map<Integer, Double>>> cbhw_map;
	private static Map<Integer, Map<Integer, Map<Integer, Double>>> cyhw_map;

	private static String datPath = "/data/nshmp/";
	private static String rjbDatPath = datPath + "rjbmean.dat";
	private static String cbhwDatPath = datPath + "avghw_cb.dat";
	private static String cyhwDatPath = datPath + "avghw_cy.dat";

	static {
		rjb_map = new HashMap<Integer, Map<Integer, Double>>();
		cbhw_map = new HashMap<Integer, Map<Integer, Map<Integer, Double>>>();
		cyhw_map = new HashMap<Integer, Map<Integer, Map<Integer, Double>>>();
		readRjbDat();
		readHwDat(cbhw_map, cbhwDatPath, 6.05);
		readHwDat(cyhw_map, cyhwDatPath, 5.05);
	}

	private static void readRjbDat() {

		String magID = "#Mag";
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(
				NSHMP_Util.class.getResourceAsStream(rjbDatPath)));
			String line;
			HashMap<Integer, Double> magMap = null;
			while ((line = br.readLine()) != null) {
				if (line.startsWith(magID)) {
					double mag = Double.parseDouble(line.substring(
						magID.length() + 1).trim());
//					int magKey = Double.valueOf(mag * 100).intValue(); // this failed for 8.45
					int magKey = (int)Math.round(mag*100d);
//					System.out.println(mag+" -> "+magKey);
					magMap = new HashMap<Integer, Double>();
					rjb_map.put(magKey, magMap);
					continue;
				}
				if (line.startsWith("#")) continue;
				String[] dVal = StringUtils.split(line);
				if (dVal.length == 0) continue;
				int distKey = Double.valueOf(dVal[0]).intValue();
				magMap.put(distKey, Double.parseDouble(dVal[1]));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void readHwDat(
			Map<Integer, Map<Integer, Map<Integer, Double>>> map, String path,
			double startMag) {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(
				NSHMP_Util.class.getResourceAsStream(path)));
			String line;
			Map<Integer, Map<Integer, Double>> periodMap = null;
			while ((line = br.readLine()) != null) {
				if (line.startsWith("C")) {
					// period map
					double per = Double.parseDouble(StringUtils.split(line)[4]);
					int perKey = (int) (per * 1000);
					periodMap = new HashMap<Integer, Map<Integer, Double>>();
					map.put(perKey, periodMap);
					continue;
				}
				String[] values = StringUtils.split(line);
				if (values.length == 0) continue;
				int distKey = Integer.parseInt(values[0]);
				int magIdx = Integer.parseInt(values[1]);
				int magKey = (int) (startMag * 100) + (magIdx - 1) * 10;
				double hwVal = Double.parseDouble(values[2]);
				Map<Integer, Double> magMap = periodMap.get(magKey);
				if (magMap == null) {
					magMap = new HashMap<Integer, Double>();
					periodMap.put(magKey, magMap);
				}
				magMap.put(distKey, hwVal);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns a corrected distance value corresponding to the supplied JB
	 * distance and magnitude. Magnitude is expected to be a 0.05 centered value
	 * between 6 and 7.6 (e.g [6.05, 6.15, ... 7.55]). Distance values should be
	 * &lt;1000km. If <code>D</code> &ge; 1000km, method returns D.
	 * 
	 * @param M magnitude
	 * @param D distance
	 * @return the corrected distance or <code>D</code> if <code>D</code> ≥ 1000
	 * @throws IllegalArgumentException if <code>M</code> is not one of [6.05,
	 *         6.15, ... 8.55]
	 */
	public static double getMeanRJB(double M, double D) {
		int magKey = (int) Math.round(M * 100);
		checkArgument(rjb_map.containsKey(magKey), "Invalid mag value: " + M+" (key="+magKey+")");
		int distKey = (int) Math.floor(D);
		return (D <= 1000) ? rjb_map.get(magKey).get(distKey) : D;
	}
	
	private static final EvenlyDiscretizedFunc magBinFunc = new EvenlyDiscretizedFunc(6.05, 26, 0.1);
	
	public static double getCorrectedDistanceJB(Location siteLoc, double mag, PointSurface surf, double horzDist) {
		if(mag<=6) {
			return horzDist;
		} else if (horzDist == 0d) {
			return 0d;
		} else { //if (mag<=7.6) {
			// NSHMP getMeanRJB is built on the assumption of 0.05 M
			// centered bins. Non-UCERF erf's often do not make
			// this assumption and are 0.1 based so we push
			// the value down to the next closest compatible M
			
			// this was Peter's original correction, but it explodes if it's given say 6.449999999999999 (which converts to 6.39999999999999)
//			double adjMagAlt = ((int) (mag*100) % 10 != 5) ? mag - 0.05 : mag;
			// this doesn't work either for values like 6.0 and 6.1 (only works when close to an 0.x5)
//			double adjMag = ((double)Math.round(mag/0.05))*0.05;
//			if (adjMag > 8.6) adjMag = 8.55;
			// this works
			double nearestTenth = Math.round(mag*10)/10d;
//			System.out.println("Nearest 10th to "+mag+" is "+nearestTenth);
			if ((float)nearestTenth > 6f && (float)nearestTenth == (float)mag)
				// we're right at a 10th and want it to always round down
				// e.g., we don't want 6.449999999999999 to round down, but 6.450000000000001 to round up
				// so subtract a tiny bit from the nearest tenth to force it to always round down
				mag = nearestTenth - 0.0001;
			double adjMag = magBinFunc.getX(magBinFunc.getClosestXIndex(mag));
//			if(adjMagAlt != adjMag)
//				System.out.println("mag,adj,alt:\t"+mag+"\t"+adjMag+"\t"+adjMagAlt);
//			System.out.println("\tadjMag="+(float)adjMag);
			return getMeanRJB(adjMag, horzDist);
		}
	}

	/**
	 * Returns the average hanging-wall factor appropriate for
	 * {@link CB_2008_AttenRel} for a dipping point source at the supplied
	 * distance and magnitude and period of interest. Magnitude is expected to
	 * be a 0.05 centered value between 6 and 7.5 (e.g [6.05, 6.15, ... 7.45]).
	 * Distance values should be &le;200km. If distance value is &gt200km,
	 * method returns 0. Valid periods are those prescribed by
	 * {@link CB_2008_AttenRel}.
	 * 
	 * @param M magnitude
	 * @param D distance
	 * @param P period
	 * @return the hanging wall factor
	 * @throws IllegalArgumentException if <code>M</code> is not one of [6.05,
	 *         6.15, ... 7.45]
	 * @throws IllegalArgumentException if <code>P</code> is not one of [-2.0
	 *         (pgd), -1.0 (pgv), 0.0 (pga), 0.01, 0.02, 0.03, 0.04, 0.05,
	 *         0.075, 0.1, 0.15, 0.2, 0.25, 0.3, 0.4, 0.5, 0.75, 1.0, 1.5, 2.0,
	 *         3.0, 4.0, 5.0, 7.5, 10.0]
	 */
	public static double getAvgHW_CB(double M, double D, double P) {
		return getAvgHW(cbhw_map, M, D, P);
	}

	/**
	 * Returns the average hanging-wall factor appropriate for
	 * {@link CY_2008_AttenRel} for a dipping point source at the supplied
	 * distance and magnitude and period of interest. Magnitude is expected to
	 * be a 0.05 centered value between 5 and 7.5 (e.g [5.05, 6.15, ... 7.45]).
	 * If there is no match for the supplied magnitude, method returns 0.
	 * Distance values should be &le;200km. If distance value is &gt200km,
	 * method returns 0. Valid periods are those prescribed by
	 * {@link CY_2008_AttenRel} (<em>Note:</em>PGV is currently missing).
	 * 
	 * @param M magnitude
	 * @param D distance
	 * @param P period
	 * @return the hanging wall factor
	 * @throws IllegalArgumentException if <code>P</code> is not one of [0.0
	 *         (pga), 0.01, 0.02, 0.03, 0.04, 0.05, 0.075, 0.1, 0.15, 0.2, 0.25,
	 *         0.3, 0.4, 0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0, 7.5, 10.0]
	 */
	public static double getAvgHW_CY(double M, double D, double P) {
		return getAvgHW(cyhw_map, M, D, P);
	}

	private static double getAvgHW(
			Map<Integer, Map<Integer, Map<Integer, Double>>> map, double M,
			double D, double P) {
		int perKey = (int) (P * 1000);
		checkArgument(map.containsKey(perKey), "Invalid period: " + P);
		Map<Integer, Map<Integer, Double>> magMap = map.get(perKey);
		int magKey = Double.valueOf(M * 100).intValue();
		if (!magMap.containsKey(magKey)) return 0;
		int distKey = Double.valueOf(Math.floor(D)).intValue();
		return (distKey > 200) ? 0 : magMap.get(magKey).get(distKey);
	}

}
