package org.opensha.sra.rtgm;

import static com.google.common.primitives.Doubles.asList;
import static com.google.common.primitives.Doubles.toArray;
import static com.google.common.collect.Lists.reverse;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.special.Erf;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.Interpolate;
import org.opensha.nshmp.NEHRP_TestCity;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

/**
 * Utility methods used by RTGM.java
 * 
 * @author Peter Powers
 * @version $Id:$
 */
class RTGM_Util {

	
	/**
	 * For finding prob. of exceedance on a hazard curve. To use standard
	 * interpolation methods requires reversing the supplied x and y values,
	 * and then supplying them as reversed arguments to Interpolate.findLogLogY.
	 * This satisfies the monotonically increasingrequirements of x and y
	 * data in Interpolate.
	 * @return the log-interpolated 
	 */
	static double findLogLogX(double[] xs, double[] ys, double y) {
			double[] revXs = toArray(reverse(asList(xs)));
			double[] revYs = toArray(reverse(asList(ys)));
			return Interpolate.findLogLogY(revYs, revXs, y);
	}
	
	// can't use trapezoidal integration in commons.math as it requires a
	// continuous x function whereas we're working with discretized x fns
	/**
	 * Performs trapezoidal rule integration on the supplied discretized
	 * function.
	 * @param f function to integrate
	 * @return the integral
	 */
	static double trapz(DiscretizedFunc f) {
		Preconditions.checkNotNull(f, "Supplied function is null");
		Preconditions.checkArgument(f.size() > 1,
				"Supplied function is too short");
		double sum = 0;
		Point2D p1, p2;
		for (int i=1; i<f.size(); i++) {
			p1 = f.get(i-1);
			p2 = f.get(i);
			sum += (p2.getX() - p1.getX()) * (p2.getY() + p1.getY());
		}
		return sum * 0.5;
	}
	
	// can't use trapezoidal integration in commons.math as it requires a
	// continuous x function whereas we're working with discretized x fns
	// TODO move to DataUtils?
	/**
	 * Performs trapezoidal rule integration on the supplied discretized
	 * function.
	 * @param f function to integrate
	 * @return the integral
	 */
	static double trapz(double[] xs, double[] ys) {
		Preconditions.checkNotNull(xs, "Supplied x-values are null");
		Preconditions.checkNotNull(ys, "Supplied y-values are null");
		Preconditions.checkArgument(xs.length > 1,
				"Supplied function is too short");
		double sum = 0;
		for (int i=1; i<xs.length; i++) {
			sum += (xs[i] - xs[i-1]) * (ys[i] + ys[i-1]);
		}
		return sum * 0.5;
	}


	// TODO this should ultimately be imported from NSHM dev
	/**
	 * Multiplies the y-values of function 1 in place by those of function 2.
	 * Assumes functions have identical x-values.
	 * @param f1 function to multiply in place
	 * @param f2 function to multiple f1 by
	 */
	static void multiplyFunc(DiscretizedFunc f1, DiscretizedFunc f2) {
		Preconditions.checkNotNull(f1, "Supplied function f1 is null");
		Preconditions.checkNotNull(f2, "Supplied function f2 is null");
		Preconditions.checkArgument(f1.size() == f2.size(),
				"Supplied functions are not the same size");
		for (int i = 0; i < f1.size(); i++) {
			f1.set(i, f1.getY(i) * f2.getY(i));
		}
	}

	private static final double SQRT2PI = Math.sqrt(2 * Math.PI);
	private static final double SQRT2 = Math.sqrt(2.0);

	static double logNormalDensity(double x, double mean, double std) {
		if (x <= 0) return 0;
		final double x0 = Math.log(x) - mean;
		final double x1 = x0 / std;
		return Math.exp(-0.5 * x1 * x1) / (std * SQRT2PI * x);
	}

	static double logNormalCumProb(double x, double mean, double std) {
		if (x <= 0) return 0;
		final double dev = Math.log(x) - mean;
		if (Math.abs(dev) > 40 * std) {
			return dev < 0 ? 0.0d : 1.0d;
		}
		return 0.5 + 0.5 * Erf.erf(dev / (std * SQRT2));
	}

	private static NormalDistribution normDist = new NormalDistribution();

	static double norminv(double p) {
		try {
			return normDist.inverseCumulativeProbability(p);
		} catch (RuntimeException e) {
			e.printStackTrace();
			return Double.NaN;
		}
	}



	// public static void main(String[] args) {
		// buildCityStructForMatlab();
	// }

	// utility to build a struct from the NEHRP_TestCity enum

	private static final Joiner JOIN_LINE = Joiner.on(System.getProperty(
			"line.separator"));
	private static final Joiner JOIN_STR = Joiner.on("','");

	static void buildCityStructForMatlab() {

		List<String> names = Lists.newArrayList();
		List<String> lats = Lists.newArrayList();
		List<String> lons = Lists.newArrayList();

		for (NEHRP_TestCity city : NEHRP_TestCity.values()) {
			names.add(city.toString());
			Location loc = city.location();
			lats.add(String.format("%.2f", loc.getLatitude()));
			lons.add(String.format("%.2f", loc.getLongitude()));
		}

		List<String> lines = Lists.newArrayList();
		lines.add("cities = struct( ...");
		lines.add("\t'name', {'" + JOIN_STR.join(names) + "'}, ...");
		lines.add("\t'lat', {'" + JOIN_STR.join(lats) + "'}, ...");
		lines.add("\t'lon', {'" + JOIN_STR.join(lons) + "'});");

		File f = new File("tmp/Cities.m");
		try {
			Files.write(JOIN_LINE.join(lines), f, Charsets.US_ASCII);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

}
