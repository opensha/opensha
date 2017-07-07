package org.opensha.nshmp2.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.opensha.sha.imr.attenRelImpl.CB_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.CY_2008_AttenRel;
import org.opensha.sha.imr.attenRelImpl.NSHMP_2008_CA;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

/**
 * NSHMP Utilities. These methods are primarily used by {@link NSHMP_2008_CA}.
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class NSHMP_IMR_Util {

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

	private static String rjbDatPath = "/rjbmean.dat";
	private static String cbhwDatPath = "/avghw_cb.dat";
	private static String cyhwDatPath = "/avghw_cy.dat";

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
			URL url = Utils.getResource(rjbDatPath);
			List<String> lines = Resources.readLines(url, Charsets.US_ASCII);
			HashMap<Integer, Double> magMap = null;
			for (String line : lines) {
				if (line.startsWith(magID)) {
					double mag = Double.parseDouble(line.substring(
						magID.length() + 1).trim());
					int magKey = new Double(mag * 100).intValue();
					magMap = new HashMap<Integer, Double>();
					rjb_map.put(magKey, magMap);
					continue;
				}
				if (line.startsWith("#")) continue;
				String[] dVal = StringUtils.split(line);
				if (dVal.length == 0) continue;
				int distKey = new Double(dVal[0]).intValue();
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
			URL url = Utils.getResource(path);
			List<String> lines = Resources.readLines(url, Charsets.US_ASCII);
			Map<Integer, Map<Integer, Double>> periodMap = null;
			for (String line : lines) {
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
	 * distance and magnitude. Magnitude is expected to be greater than 6 and is
	 * moved to the closest 0.05 centered value between 6 and 7.6 (e.g [6.05,
	 * 6.15, ... 7.55]). Distance values should be &lt;1000km. If <code>D</code>
	 * &ge; 1000km, method returns <code>D</code>.
	 * 
	 * @param M magnitude
	 * @param D distance
	 * @return the corrected distance or <code>D</code> if <code>D</code> &ge;
	 *         1000
	 * @throws IllegalArgumentException if <code>M &lt; 6</code>
	 */
	public static double getMeanRJB(double M, double D) {
		checkArgument(M >= 6, "Supplied M is too small [%s]", M);
		int magIdx = Math.max(0, (int) Math.round((M - 6.05) / 0.1));
		magIdx = Math.min(15, magIdx); // limit to 7.55 a la nshmp hazgrid
		// Johnson conversion pushes max mag to 7.625
		int magKey = 605 + magIdx * 10;
		// checkArgument(rjb_map.containsKey(magKey), "Invalid mag value: " +
		// M);
		int distKey = (int) Math.floor(D);
		return (D <= 1000) ? rjb_map.get(magKey).get(distKey) : D;
//		try {
//			
//		} catch (Exception e) {
//			System.out.println("mag: " + M);
//			System.out.println();
//			e.printStackTrace();
//			return -1;
//		}
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
		int magKey = new Double(M * 100).intValue();
		if (!magMap.containsKey(magKey)) return 0;
		int distKey = new Double(Math.floor(D)).intValue();
		return (distKey > 200) ? 0 : magMap.get(magKey).get(distKey);
	}

	public static void main(String[] args) {
	}
}
