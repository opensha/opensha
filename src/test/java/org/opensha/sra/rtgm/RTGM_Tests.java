package org.opensha.sra.rtgm;

import static org.junit.Assert.*;
import static org.opensha.sra.rtgm.RTGM.Frequency.*;

import java.net.URL;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.nshmp.NEHRP_TestCity;
import org.opensha.sra.rtgm.RTGM;
import org.opensha.sra.rtgm.RTGM.Frequency;

import com.google.common.base.Charsets;
import com.google.common.base.Enums;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;

@SuppressWarnings("javadoc")
public class RTGM_Tests {

	private static Splitter valSplit = Splitter.on(' ').omitEmptyStrings();
	private static Splitter numSplit = Splitter.on(',');
	private static Function<String, NEHRP_TestCity> findCity = Enums
		.stringConverter(NEHRP_TestCity.class);
	
	
	private static List<ResultSet> results;
	
	private static final double TOL = 0.0001;
	
	// value used in USGS design maps
	private static final double BETA = 0.8;
	
	/**
	 * @throws java.lang.Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		// load results
		results = Lists.newArrayList();
		URL url = Resources.getResource(RTGM_Tests.class, "m/resultsFINAL.txt");
		List<String> lines = Resources.readLines(url, Charsets.US_ASCII);
		ResultSet result = null;
		for (String line : lines) {
			if (line.startsWith("city:")) {
				result = new ResultSet();
				result.city = findCity.apply(readString(line));
			}
			if (line.startsWith("period:")) {
				result.freq = (readString(line).equals("1p00")) ? SA_1P00 : SA_0P20;
			}
			if (line.startsWith("sa:")) {
				result.saVals = readValues(line);
			}
			if (line.startsWith("afe:")) {
				result.afeVals = readValues(line);
			}
			if (line.startsWith("rtgm:")) {
				result.rtgm = readValue(line);
			}
			if (line.startsWith("rc:")) {
				result.risk = readValue(line);
				results.add(result);
			}
		}
		
		// build hazard curves for each result
		for (ResultSet r : results) {
			ArbitrarilyDiscretizedFunc f = new ArbitrarilyDiscretizedFunc();
			for (int i=0; i<r.saVals.size(); i++) {
				f.set(r.saVals.get(i), r.afeVals.get(i));
			}
			r.hc = f;
		}
		
	}

	@Test
	public final void test() {
		for (ResultSet result : results) {
			RTGM rtgm = RTGM.create(result.hc, result.freq, BETA).call();
			double rtgmDiff = pDiff(result.rtgm, rtgm.get());
			assertTrue("rtgm diff % is: " + rtgmDiff + " " + result.rtgm + " " +rtgm.call() , rtgmDiff < TOL);
			double riskDiff = pDiff(result.risk, rtgm.riskCoeff());
			assertTrue("risk diff % is: " + riskDiff, riskDiff < TOL);
		}
	}
	
	private static double pDiff(double v1, double v2) {
//		System.out.println(v1 + " " + v2);
		return 100 * (Math.abs(v2-v1) / v1);
	}

	private static String readString(String line) {
		return Iterables.get(valSplit.split(line), 1);
	}
	
	private static double readValue(String line) {
		String valStr = readString(line);
		return Double.valueOf(valStr);
	}
	
	private static List<Double> readValues(String line) {
		String valStr = readString(line);
		Iterable<String> valStrs = numSplit.split(valStr);
		List<Double> valNums = Lists.newArrayList();
		for (String val : valStrs) {
			valNums.add(Double.valueOf(val));
		}
		return valNums;
	}
	
	private static class ResultSet {
		NEHRP_TestCity city;
		Frequency freq;
		DiscretizedFunc hc;
		List<Double> saVals;
		List<Double> afeVals;
		double rtgm;
		double risk;
	}
}
