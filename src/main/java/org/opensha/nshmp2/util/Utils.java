package org.opensha.nshmp2.util;

import static org.opensha.nshmp2.util.GaussTruncation.ONE_SIDED;
import static org.opensha.nshmp2.util.Utils.SQRT_2;
//import static org.opensha.sha.imr.AttenRelRef.AB_2006_140;
//import static org.opensha.sha.imr.AttenRelRef.AB_2006_200;
//import static org.opensha.sha.imr.AttenRelRef.CAMPBELL_2003;
//import static org.opensha.sha.imr.AttenRelRef.FEA_1996;
//import static org.opensha.sha.imr.AttenRelRef.SILVA_2002;
//import static org.opensha.sha.imr.AttenRelRef.SOMERVILLE_2001;
//import static org.opensha.sha.imr.AttenRelRef.TORO_1997;
//import static org.opensha.sha.imr.AttenRelRef.TP_2005;
import static com.google.common.base.Preconditions.*;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.special.Erf;
import org.opensha.commons.calc.GaussianDistCalc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.exceptions.IMRException;
import org.opensha.commons.exceptions.ParameterException;
import org.opensha.commons.gui.plot.jfreechart.DiscretizedFunctionXYDataSet;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.param.OtherParams.SigmaTruncTypeParam;

import com.google.common.collect.BoundType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.primitives.Doubles;

/**
 * Add comments here
 * 
 * 
 * @author Peter Powers
 * @version $Id:$
 */
public class Utils {

	/** Multiplicative conversion from log (base-10) to natural log (base-e). */
	public static final double LOG_BASE_10_TO_E = 1.0 / 0.434294;
	// TODO this should really be more precise Math.log10(Math.E)

	/** Precomputed square-root of 2. */
	public static final double SQRT_2 = Math.sqrt(2);

	/**
	 * Converts mblg magnitude to moment magnitude (M<sub>w<sub>) according to
	 * the conversion required by the supplied fault <code>code</code>.
	 * <code>code</code> argument must be one of {@link FaultCode#M_CONV_BA} or
	 * {@link FaultCode#M_CONV_J}.
	 * @param code for conversion
	 * @param mag to convert
	 * @return the converted magnitude or the input mag if code is not
	 *         applicable or <code>null</code>
	 * @see FaultCode
	 */
	public static double mblgToMw(FaultCode code, double mag) {
		switch (code) {
			case M_CONV_J:
				return 1.14 + 0.24 * mag + 0.0933 * mag * mag;
			case M_CONV_AB:
				return 2.715 - 0.277 * mag + 0.127 * mag * mag;
			default:
				return mag;
		}
	}

	/**
	 * Period dependent ground motion clipping. For PGA (<code>period</code> =
	 * 0.0s), method returns <code>Math.min(ln(1.5g), gnd)</code>; for
	 * <code>0.02s &lt; period &lt; 0.5s</code>, method returns
	 * <code>Math.min(ln(3.0g), gnd)</code>. This is used to clip the upper tail
	 * of the exceedance curve.
	 * @param period of interest
	 * @param gnd log ground motion ln(g)
	 * @return the clipped ground motion if required by the supplied
	 *         <code>period</code>, <code>gnd</code> otherwise
	 */
	public static double ceusMeanClip(double period, double gnd) {
		// ln(1.5) = 0.405; ln(3.0) = 1.099
		if (period == 0.0) return Math.min(0.405, gnd);
		if (period > 0.02 && period < 0.5) return Math.min(gnd, 1.099);
		return gnd;
	}

	// TODO staticify ln vals

	// if Campbell 2003 3g@0.5s is correct, this won't work
	public static double ceusTailClip(double period, double gnd) {
		// ln(3.0) = 1.09861229; ln(6.0) = 1.79175947
		if (period == 0.0) return Math.min(1.09861229, gnd);
		if (period > 0.0 && period < 0.75) return Math.min(gnd, 1.79175947);
		return gnd;
	}

// @formatter:off
	private static double[][] imrUncertVals = {
		{ 0.375, 0.210, 0.245 },
		{ 0.230, 0.225, 0.230 }, 
		{ 0.400, 0.360, 0.310 } };
	// @formatter:on

	/**
	 * Returns the epistemic ground-motion uncertainty for the supplied
	 * magnitude (M) and distance (D).
	 * @param M magnitude
	 * @param D distance
	 * @return the associated uncertainty in units of ln(gm)
	 */
	public static double gmUncertainty(double M, double D) {
		int mi = (M < 6) ? 0 : (M < 7) ? 1 : 2;
		int di = (D < 10) ? 0 : (D < 30) ? 1 : 2;
		return imrUncertVals[di][mi];
	}

	/**
	 * Returns the the This method computed the probability of exceeding the
	 * IM-level given the mean and stdDev, and considering the sigma truncation
	 * type and level.
	 * @param mean
	 * @param std
	 * @param iml
	 * @param trunc
	 * @param level
	 * @return the propbability of exceeding
	 */
	public static double getExceedProbability1(double mean, double std,
			double iml, GaussTruncation trunc, double level) {

		if (std == 0) return (iml > mean) ? 0 : 1;

		double stRndVar = (iml - mean) / std;
		// compute exceedance probability based on truncation type
		switch (trunc) {
			case NONE:
				return GaussianDistCalc.getExceedProb(stRndVar);
			case ONE_SIDED:
				return GaussianDistCalc.getExceedProb(stRndVar, 1, level);
			case TWO_SIDED:
				return GaussianDistCalc.getExceedProb(stRndVar, 2, level);
			default:
				return Double.NaN;
		}
	}

	// public static double gaussProbExceed(double mean, double std, double
	// value,
	// GaussTruncation trunc, double level) throws MathException {
	// double clip = gaussProbExceed(mean, std, mean + std * level, 0);
	// return gaussProbExceed(mean, std, value, clip);
	// }

	// /**
	// * Returns the probability of exceeding the supplied target value in a
	// * Gaussian distribution.
	// * level. This is the NSHMP error function (Erf) based implementation that
	// * imposes one-sided (upper) truncation of the underlying lognormal
	// * distribution.
	// *
	// * TODO needs speed tests
	// *
	// * @param mean ground motion
	// * @param std deviation
	// * @param value to exceed
	// * @param trunc truncation probability
	// * @return the probability of exceeding the supplied iml
	// * @throws MathException (due to internal use of
	// * org.apache.commons.math3.special.Erf)
	// */
	// public static double gaussProbExceed(double mean, double std, double
	// value,
	// double trunc) throws MathException {
	// // checkArgument(trunc >= 0, "Truncation must be a positive value or 0");
	// double P = gaussProbExceedCalc(mean, std, value);
	// P = (P - trunc) / (1.0 - trunc);
	// return (P < 0) ? 0 : P;
	// }

	/**
	 * Returns the probability of exceeding the supplied target value in a
	 * (possibly truncated) Gaussian distribution. This method requires the
	 * probability corresponding to some level of truncation. As it is an often
	 * reused value, it is left to the user to supply it from
	 * {@link #gaussProbExceed(double, double, double)}. The supplied truncation
	 * probability should be the probability at the one-sided upper truncation
	 * bound.
	 * 
	 * @param mean of distribution
	 * @param std deviation
	 * @param value to exceed
	 * @param trunc truncation probability
	 * @param type the truncation type
	 * @return the probability of exceeding the supplied value
	 * @throws MathException (due to internal use of
	 *         org.apache.commons.math3.special.Erf)
	 * @see #gaussProbExceed(double, double, double)
	 */
	public static double gaussProbExceed(double mean, double std, double value,
			double trunc, GaussTruncation type) {
		// checkArgument(trunc >= 0,
		// "Truncation must be a positive value or 0");
		double P = gaussProbExceed(mean, std, value);
		if (type == GaussTruncation.ONE_SIDED) {
			P = (P - trunc) / (1.0 - trunc);
			return probBoundsCheck(P);
		} else if (type == GaussTruncation.TWO_SIDED) {
			P = (P - trunc) / (1 - 2 * trunc);
			return probBoundsCheck(P);
		} else {
			return P;
		}
	}

	private static double probBoundsCheck(double P) {
		return (P < 0) ? 0 : (P > 1) ? 1 : P;
	}

	/**
	 * Returns the probability of exceeding the supplied target value in a
	 * Gaussian distribution asuming no truncation.
	 * @param mean of distribution
	 * @param std deviation
	 * @param value to exceed
	 * @return the probability of exceeding the supplied value
	 * @throws MathException (due to internal use of
	 *         org.apache.commons.math3.special.Erf)
	 */
	public static double gaussProbExceed(double mean, double std, double value) {
		return (Erf.erf((mean - value) / (std * SQRT_2)) + 1.0) * 0.5;
	}

	public static final String NSHMP_DAT_PATH = "/resources/data/nshmp";

	/**
	 * Returns the URL of an NSHMP resource. Path should be specified as
	 * absolute from NSHMP resource directory (e.g. "/sources/WUS/...").
	 * @param path to resource (absolute from resource dir.)
	 * @return the resource URL
	 */
	public static URL getResource(String path) {
		String fullPath = NSHMP_DAT_PATH + path;
		return Utils.class.getResource(fullPath);
	}

	/**
	 * Converts an array of strings to a list of doubles. The returned list is
	 * generated using
	 * <code>com.google.common.primitives.Doubles.asList(double[])</code> and is
	 * very efficient
	 * @param source strings
	 * @return double list
	 */
	public static List<Double> stringsToDoubles(String[] source) {
		double[] values = new double[source.length];
		for (int i = 0; i < source.length; i++) {
			values[i] = Double.valueOf(source[i]);
		}
		return Doubles.asList(values);
	}

	/**
	 * Returns the supplied function populated with exceedance probabilites
	 * calculated per the NSHMP. If <code>clamp</code> is <code>true</code>, the
	 * lesser of <code>3*sigma</code> and <code>clampVal</code> is used to
	 * truncate the upper part of the underlying Gaussian.
	 * 
	 * @param imls function to populate
	 * @param mean log mean ground motion
	 * @param sigma log std deviation of ground motion
	 * @param clamp whether to enable additional truncation
	 * @param clampVal truncation value
	 * @return the populated function (non-log ground motions)
	 */
	public static DiscretizedFunc getExceedProbabilities(DiscretizedFunc imls,
			double mean, double sigma, boolean clamp, double clampVal) {
		// double sigma = getStdDev();
		// double gnd = getMean();
		// System.out.print(mean + " " + sigma);

		double clip = mean + 3 * sigma;
		if (clamp) {
			double clip3s = Math.exp(clip);
			double clipPer = clampVal;
			if (clipPer < clip3s && clipPer > 0) clip = Math.log(clipPer);
		}
		try {
			double Pclip = Utils.gaussProbExceed(mean, sigma, clip);
			for (int i = 0; i < imls.size(); i++) {
				double x = imls.getX(i);
				double y = Utils.gaussProbExceed(mean, sigma, Math.log(x), Pclip, ONE_SIDED);
				imls.set(i, y);
			}
		} catch (RuntimeException me) {
			me.printStackTrace();
		}
		return imls;
	}
	
	public static double getExceedProbability(double iml,
			double mean, double sigma, boolean clamp, double clampVal) {

		double clip = mean + 3 * sigma;
		if (clamp) {
			double clip3s = Math.exp(clip);
			double clipPer = clampVal;
			if (clipPer < clip3s && clipPer > 0) clip = Math.log(clipPer);
		}
		double Pclip = Utils.gaussProbExceed(mean, sigma, clip);
		return Utils.gaussProbExceed(mean, sigma, Math.log(iml), Pclip, ONE_SIDED);
	}


	// public static void main(String[] args) {
	// double x1, x2, y1, y2, y;
	// x1 = 0.556;
	// x2 = 0.778;
	// // y1 = 5.363e-3; // values from curve served up by hazard app
	// // y2 = 1.866e-3;
	// y1 = 5.11310e-3;
	// y2 = 1.71716e-3;
	//
	// // y = 0.001026; // 5%PE50
	// y = 0.002107; // 10%PE50
	//
	// System.out.println("App: " + interp1(x1, x2, y1, y2, y));
	// System.out.println("NSH: " + interp2(x1, x2, y1, y2, y));
	//
	// System.out.println(poissProb(0.5, 1));
	// System.out.println(poissProbInv(poissProb(0.5, 1), 1));
	//
	// // System.out.println(poissProb(y1,1));
	// // System.out.println(poissProb(y2,1));
	// }

	private static double poissProb(double r, double t) {
		return 1 - Math.exp(-r * t);
	}

	private static double poissProbInv(double P, double t) {
		return -Math.log(1 - P) / t;
	}

	private static double interp1(double x1, double x2, double y1, double y2,
			double y) {
		x1 = Math.log10(x1);
		y1 = Math.log10(y1);
		x2 = Math.log10(x2);
		y2 = Math.log10(y2);
		double x = x2 - (x2 - x1) * (y2 - Math.log10(y)) / (y2 - y1);
		return Math.pow(10, x);
	}

	private static double interp2(double x1, double x2, double y1, double y2,
			double y) {
		x1 = Math.log(x1);
		y1 = Math.log(y1);
		x2 = Math.log(x2);
		y2 = Math.log(y2);
		double tmp = y2 - y1;
		double x = x1 + (x2 - x1) * (Math.log(y) - y1) / tmp;
		return Math.exp(x);
	}

	/**
	 * Adds the y-values of function 2 to function 1 in place. Assumes functions
	 * have identical x-values.
	 * @param f1 function to add to
	 * @param f2 function to add
	 */
	public static void addFunc(DiscretizedFunc f1, DiscretizedFunc f2) {
		for (int i = 0; i < f1.size(); i++) {
			f1.set(i, f1.getY(i) + f2.getY(i));
		}
	}

	/**
	 * Multiplies the y-values of function 1 in place by those of function 2.
	 * Assumes functions have identical x-values.
	 * @param f1 function to multiply in place
	 * @param f2 function to multiple f1 by
	 */
	public static void multiplyFunc(DiscretizedFunc f1, DiscretizedFunc f2) {
		for (int i = 0; i < f1.size(); i++) {
			f1.set(i, f1.getY(i) * f2.getY(i));
		}
	}

	/**
	 * Sets the supplied function to its complement in place. Assumes function
	 * is a probability function limited to the domain [0 1].
	 * @param f function to operate on
	 */
	public static void complementFunc(DiscretizedFunc f) {
		for (int i = 0; i < f.size(); i++) {
			f.set(i, 1 - f.getY(i));
		}
	}

	/**
	 * Zeros out the y-values of the supplied <code>function</code>.
	 * @param f function to be modified
	 */
	public static void zeroFunc(DiscretizedFunc f) {
		setFunc(f, 0);
	}
	
	/**
	 * Sets all y-values of the supplied <code>function</code> to one.
	 * @param f function to be modified
	 */
	public static void oneFunc(DiscretizedFunc f) {
		setFunc(f, 1);
	}

	/**
	 * Sets all y-values of the supplied <code>function</code> to the supplied
	 * <code>value</code>.
	 * @param f function to be modified
	 * @param v value to use
	 */
	public static void setFunc(DiscretizedFunc f, double v) {
		for (int i=0; i<f.size(); i++) {
			f.set(i, v);
		}
	}
	
	/**
	 * Generates a random, saturated color.
	 * @return a random color
	 */
	public static Color randomColor() {
		Random rand = new Random();
		float hue = rand.nextFloat();
		float sat = 0.5f + rand.nextFloat() * 0.3f; // 0.5 to 0.8
		float brt = 0.8f;
		return Color.getHSBColor(hue, sat, brt);
	}

	public static void main(String[] args) {
		
		DiscretizedFunc f = Period.GM1P00.getFunction();
		System.out.println(getExceedProbabilities(f, -2.8119645744516117, 0.6924081166479781, false, 0.0));
		
		
//		double iml = 4.0;
//		double mean = 3.0;
//		double std = 0.65;
//		double stdRndVar = (iml - mean) / std;
//
//		double clip = mean + 3 * std;
//		double Pclip;
//		// value check
//		try {
//
//			// double Ptmp = (Erf.erf((mean - iml) / std * SQRT_2) + 1.0) * 0.5;
//			// System.out.println("Ptmp: " + Ptmp);
//
//			// Pclip = (Erf.erf((iml - clip) / (std * SQRT_2)) + 1.0) * 0.5;
//			// System.out.println(Pclip);
//			// Pclip = (Pclip-0)/(1-0);
//			// System.out.println(Pclip);
//			Pclip = gaussProbExceed(mean, std, clip);
//			// System.out.println(Pclip);
//			double gdcP = getExceedProbability1(mean, std, iml,
//				GaussTruncation.TWO_SIDED, 3);
//			// double erfP = gaussProbExceed(mean, std, iml, Pclip);
//			double erfP = gaussProbExceed(mean, std, iml, Pclip,
//				GaussTruncation.TWO_SIDED);
//			System.out.println("GDC: " + gdcP);
//			System.out.println("ERF: " + erfP);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}

//		try {
//			StopWatch sw = new StopWatch();
//			int n = 1000000;
//			sw.start();
//
//			double gdcP = 0;
//			// double rand = Math.random();
//			for (int i = 0; i < n; i++) {
//				gdcP = getExceedProbability1(mean, std, iml,
//					GaussTruncation.TWO_SIDED, 3);
//			}
//			sw.stop();
//			System.out.println("GDC: " + ((double) sw.getTime()) / 1000);
//
//			sw.reset();
//			sw.start();
//			double erfP = 0;
//			Pclip = gaussProbExceed(mean, std, clip);
//			for (int i = 0; i < n; i++) {
//				erfP = gaussProbExceed(mean, std, iml, Pclip,
//					GaussTruncation.TWO_SIDED);
//				// erfP = gaussProbExceed(mean, std, iml, Pclip);
//			}
//			System.out.println("ERF: " + ((double) sw.getTime()) / 1000);
//
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
	}
}

// public static void main(String[] args) {
// Double[] f = { 0.250, 0.000, 0.125, 0.125, 0.125, 0.125, 0.125, 0.125 };
// List<Double> fltWts = Arrays.asList(f);
// System.out.println(fltWts);
//
// }
// }

