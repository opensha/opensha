package org.opensha.sha.faultSurface.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.sha.faultSurface.PointSurface;

import com.google.common.base.Preconditions;

public class AnalyticalPointSourceDistanceCorrection implements PointSourceDistanceCorrection {
	
	private static double MIN_NONZERO_DIST_DEFAULT = 0.1;
	private static double INITIAL_MAX_DIST_DEFAULT = 300d;
	private static double LOG_DIST_SAMPLE_DISCR_DEFAULT = 0.05d;
	private static int NUM_ALPHA_SAMPLES_DEFAULT = 20;
	private static int NUM_SAMPLES_ALONG_DEFAULT = 10;
	private static int NUM_SAMPLES_DOWN_DIP_DEFAULT = 5;
	
	public static InvCDFCache initDefaultCache(double[] fractiles, boolean sampleAlong, boolean sampleDownDip) {
		return new InvCDFCache(sampleAlong, sampleDownDip, MIN_NONZERO_DIST_DEFAULT, INITIAL_MAX_DIST_DEFAULT,
				LOG_DIST_SAMPLE_DISCR_DEFAULT, NUM_ALPHA_SAMPLES_DEFAULT, NUM_SAMPLES_ALONG_DEFAULT, NUM_SAMPLES_DOWN_DIP_DEFAULT, fractiles);
	}
	
	public static WeightedList<AnalyticalPointSourceDistanceCorrection> getEvenlyWeightedFractiles(
			int numFractiles, boolean sampleAlong, boolean sampleDownDip) {
		Preconditions.checkState(numFractiles > 1);
		double[] fractiles = buildSpacedSamples(0d, 1d, numFractiles);
		InvCDFCache cache = initDefaultCache(fractiles, sampleAlong, sampleDownDip);
		AnalyticalPointSourceDistanceCorrection[] corrs = new AnalyticalPointSourceDistanceCorrection[numFractiles];
		for (int i=0; i<numFractiles; i++)
			corrs[i] = new AnalyticalPointSourceDistanceCorrection(fractiles[i], cache);
		return WeightedList.evenlyWeighted(corrs);
	}
	
	public static WeightedList<AnalyticalPointSourceDistanceCorrection> getImportanceSampledFractiles(
			double[] fractileBoundaries, boolean sampleAlong, boolean sampleDownDip) {
		Preconditions.checkState(fractileBoundaries.length > 2);
		Preconditions.checkState(fractileBoundaries[0] == 0d, "First boundary must start at 0");
		Preconditions.checkState(fractileBoundaries[fractileBoundaries.length-1] == 1d, "Last boundary must end at 1");
		double[] fractiles = new double[fractileBoundaries.length -1];
		double[] weights = new double[fractileBoundaries.length -1];
		double weightSum = 0d;
		for (int i=0; i<fractiles.length; i++) {
			double lower = fractileBoundaries[i];
			double upper = fractileBoundaries[i+1];
			weights[i] = upper - lower;
			weightSum += weights[i];
			Preconditions.checkState(weights[i] > 0d);
			fractiles[i] = 0.5*(lower + upper); // center
		}
		Preconditions.checkState(Precision.equals(weightSum, 1d, 0.001), "Weights don't sum to 1: %s", weightSum);
		WeightedList<AnalyticalPointSourceDistanceCorrection> ret = new WeightedList<>(fractiles.length);
		InvCDFCache cache = initDefaultCache(fractiles, sampleAlong, sampleDownDip);
		for (int i=0; i<fractiles.length; i++)
			ret.add(new AnalyticalPointSourceDistanceCorrection(fractiles[i], cache), weights[i]);
		
		return ret;
	}
	
	private double fractile;
	private int indexInCache;
	private InvCDFCache cache;

	public AnalyticalPointSourceDistanceCorrection(double fractile, boolean sampleAlong, boolean sampleDownDip) {
		this(Double.NaN, initDefaultCache(new double[] {Double.NaN}, sampleAlong, sampleDownDip));
	}

	public AnalyticalPointSourceDistanceCorrection(double fractile, InvCDFCache cache) {
		this.fractile = fractile;
		this.cache = cache;
		indexInCache = -1;
		for (int i=0; i<cache.fractiles.length; i++) {
			// use Double.compare because it handles NaNs
			if (Double.compare(fractile, cache.fractiles[i]) == 0) {
				indexInCache = i;
				break;
			}
		}
		Preconditions.checkState(indexInCache >= 0, "Fractile %s not found in cache", fractile);
	}
	
	@Override
	public String toString() {
		String str = "Analytical ";
		if (Double.isNaN(fractile))
			str += "(mean)";
		else
			str += "p"+(float)(fractile*100d);
		return str + ", sampleAlong="+(cache.alongSamples.length > 1)+", sampleDownDip="+(cache.downDipSamples.length > 1);
	}

	@Override
	public double getCorrectedDistanceJB(double mag, PointSurface surf, double horzDist) {
		if (horzDist < cache.minNonzeroDist)
			return 0d;
		EvenlyDiscretizedFunc logDistFunc = cache.getFractileFuncs(
				surf.getAveLength(), surf.getAveWidth(), surf.getAveDip(), horzDist)[indexInCache];
		double logDist = Math.log10(horzDist);
		return logDistFunc.getInterpolatedY(logDist);
	}
	
	/**
	 * Calculates an analytical Joyner-Boore distance (rJB) for a rectangular fault with the given parameters
	 * 
	 * @param rEpi epicentral distance from the site to the grid node
	 * @param rupLength length of the fault that intersects that grid node
	 * @param rupWidth down-dip width (3D) of the fault that intersects that grid node
	 * @param dipRad dip of the fault (radians)
	 * @param gridNodeFractDAS fractional distance along-strike of the rupture where the grid node lies. A value of 0.5
	 * indicates that the rupture is centered along-strike and extends 0.5*rupLen in either direction. A value of 0
	 * indicates that the rupture begins at the grid node and extends rupLength in the alpha direction  
	 * @param gridNodeFractDepth fractional depth of the fault below the grid node. A value of 0.5 indicates that the
	 * rupture is centered down-dip about the grid node. A value of 0 indicates that the upper edge of the rupture is
	 * directly below the grid node.
	 * @param alphaRad strike angle of the fault relative to the site (radians). A value of 0 indicates that the strike
	 * direction is from the grid node directly toward the site (parallel); a value of PI/2 indicates that the strike
	 * direction is perpendicular to the site.
	 * @return
	 */
	public static double calcRJB(double rEpi, double rupLength, double rupWidth, double dipRad,
			double gridNodeFractDAS, double gridNodeFractDepth, double alphaRad) {
		// 1) Horizontal dimension of the down-dip direction
		//    (the fault extends rupWidth in 3D, so horizontally it's rupWidth*cos(dip))
		double wHorz = rupWidth * Math.cos(dipRad);

		// 2) Fault rectangle in local (strike,dip) coords is:
		//       X in [Xmin, Xmax], with total length = rupLength
		//       Y in [Ymin, Ymax], with total width = wHorz
		//    where (0,0) is the grid node in local coordinates.
		double xMin = -gridNodeFractDAS * rupLength;
		double xMax = xMin + rupLength; // = (1 - gridNodeFractDAS)*rupLength
		double yMin = -gridNodeFractDepth * wHorz;
		double yMax = yMin + wHorz;     // = (1 - gridNodeFractDepth)*wHorz

		// 3) Convert the site's global coords (rEpi, 0) -> local (xLoc, yLoc)
		//    local X-axis = strike = (cos(alpha), sin(alpha))
		//    local Y-axis = dip in map = (-sin(alpha), cos(alpha))
		//    node is at (0,0), site is at (rEpi, 0).
		double cosA = Math.cos(alphaRad);
		double sinA = Math.sin(alphaRad);
		double xLoc = rEpi * cosA;   // = x*cosA + y*sinA, but site y=0
		double yLoc = -rEpi * sinA;  // = -x*sinA + y*cosA

		// 4) Distance from (xLoc, yLoc) to that axis-aligned bounding box
		double dx = 0.0;
		if (xLoc < xMin) {
			dx = xMin - xLoc;
		} else if (xLoc > xMax) {
			dx = xLoc - xMax;
		}

		double dy = 0.0;
		if (yLoc < yMin) {
			dy = yMin - yLoc;
		} else if (yLoc > yMax) {
			dy = yLoc - yMax;
		}

		return Math.sqrt(dx * dx + dy * dy);
	}
	
	private static double[] buildSpacedSamples(double min, double max, int num) {
		double delta = (max-min)/(double)(num+1d);
		double[] ret = new double[num];
		for (int i=0; i<num; i++)
			ret[i] = (i+1)*delta;
		return ret;
	}
	
	private static final double[] single_sample_0p5 = {0.5};
	
	public static class InvCDFCache {
		
		private final double minNonzeroDist;
		private final double logDistSampleDiscr;
		private final double[] alphaSamples;
		private final double[] alongSamples;
		private final double[] downDipSamples;
		private final double[] fractiles;

		private double maxDistBin;
		private EvenlyDiscretizedFunc logDistBins;
		
		private ConcurrentMap<RuptureKey, EvenlyDiscretizedFunc[]> valueFuncs = new ConcurrentHashMap<>();

		public InvCDFCache(boolean sampleAlongStrike, boolean sampleDownDip, double minNonzeroDist,
				double initialMaxDist, double logDistSampleDiscr,
				int numAlphaSamples, int numSamplesAlong, int numSamplesDownDip,
				double[] fractiles) {
			Preconditions.checkState(minNonzeroDist > 0d);
			this.minNonzeroDist = minNonzeroDist;
			Preconditions.checkState(logDistSampleDiscr > 0d);
			this.logDistSampleDiscr = logDistSampleDiscr;
			Preconditions.checkState(numAlphaSamples > 1);
			if (numSamplesAlong == 1 && numSamplesDownDip == 1)
				// no sampling along-strike nor down-dip, can just sample over 0-90
				alphaSamples = buildSpacedSamples(0d, 90d, numSamplesAlong);
			else
				// need to do the full circle
				alphaSamples = buildSpacedSamples(0d, 360d, numSamplesAlong);
			if (sampleAlongStrike) {
				Preconditions.checkState(numSamplesAlong > 1);
				alongSamples = buildSpacedSamples(0d, 1d, numSamplesAlong);
			} else {
				alongSamples = single_sample_0p5;
			}
			if (sampleDownDip) {
				Preconditions.checkState(numSamplesDownDip > 1);
				downDipSamples = buildSpacedSamples(0d, 1d, numSamplesDownDip);
			} else {
				downDipSamples = single_sample_0p5;
			}
			this.fractiles = fractiles;
			
			initLogDistBins(initialMaxDist);
		}
		
		private void initLogDistBins(double maxDist) {
			Preconditions.checkState(maxDist > minNonzeroDist);
			double logMaxDist = Math.log10(maxDist);
			double logMinDist = Math.log10(minNonzeroDist);
			int numAway = (int)Math.ceil((logMaxDist-logMinDist)/logDistSampleDiscr);
			logDistBins = new EvenlyDiscretizedFunc(logMinDist, numAway+1, logDistSampleDiscr);
			Preconditions.checkState((float)logDistBins.getMaxX() >= (float)logMaxDist,
					"logDistBins.getMaxX()=%s but logMaxDist=%s", (float)logDistBins.getMaxX(), (float)logMaxDist);
			maxDistBin = Math.pow(10, logDistBins.getMaxX());
		}
		
		public EvenlyDiscretizedFunc[] getFractileFuncs(double rupLen, double rupWidth, double dip, double horzDist) {
			RuptureKey key = new RuptureKey(rupLen, rupWidth, dip);
			EvenlyDiscretizedFunc[] ret = valueFuncs.get(key);
			
			if (horzDist > maxDistBin) {
				// largest we've ever seen
				synchronized (this) {
					if (horzDist > maxDistBin)
						initLogDistBins(horzDist);
				}
			}
			EvenlyDiscretizedFunc logDistBins = this.logDistBins;
			
			if (ret == null) {
				// need to build it from scratch
				List<CompletableFuture<double[]>> futures = new ArrayList<>(logDistBins.size());
				for (int r=0; r<logDistBins.size(); r++) {
					double rEpi = Math.pow(10, logDistBins.getX(r));
					futures.add(CompletableFuture.supplyAsync(new SampleFractileCalculator(
							rEpi, rupLen, rupWidth, dip, alphaSamples, alongSamples, downDipSamples, fractiles)));
				}
				ret = new EvenlyDiscretizedFunc[fractiles.length];
				for (int i=0; i<fractiles.length; i++)
					ret[i] = new EvenlyDiscretizedFunc(logDistBins.getMinX(), logDistBins.size(), logDistBins.getDelta());
				for (int r=0; r<futures.size(); r++) {
					double[] values = futures.get(r).join();
					for (int i=0; i<ret.length; i++)
						ret[i].set(r, values[i]);
				}
				synchronized (valueFuncs) {
					valueFuncs.put(key, ret);
				}
			} else {
				if (horzDist > 0d) {
					// we already have one, see if we need to expand it
					double logDist = Math.log10(horzDist);
					if (logDist > ret[0].getMaxX()) {
						// we need to expand it
						
						// grow them
						// first copy into larger functions
						int origNum = ret[0].size();
						for (int i=0; i<ret.length; i++) {
							EvenlyDiscretizedFunc expanded = new EvenlyDiscretizedFunc(logDistBins.getMinX(), logDistBins.size(), logDistBins.getDelta());
							for (int r=0; r<ret[i].size(); r++) {
								Preconditions.checkState(i > 0 || Precision.equals(expanded.getX(r), ret[i].getX(r), 0.0001));
								expanded.set(r, ret[i].getY(r));
							}
							ret[i] = expanded;
						}
						// now calculate for the new distances
						List<CompletableFuture<double[]>> futures = new ArrayList<>(logDistBins.size()-origNum);
						for (int r=origNum; r<logDistBins.size(); r++) {
							double rEpi = Math.pow(10, logDistBins.getX(r));
							futures.add(CompletableFuture.supplyAsync(new SampleFractileCalculator(
									rEpi, rupLen, rupWidth, dip, alphaSamples, alongSamples, downDipSamples, fractiles)));
						}
						for (int f=0; f<futures.size(); f++) {
							double[] values = futures.get(f).join();
							int r = origNum+f;
							for (int i=0; i<ret.length; i++)
								ret[i].set(r, values[i]);
						}
					}
				}
			}
			return ret;
		}
	}
	
	private static class RuptureKey implements Comparable<RuptureKey> {
		
		private final double rupLength, rupWidth, dip;
		
		public RuptureKey(double rupLength, double rupWidth, double dip) {
			// snap values
			
			if (rupLength < 0.1)
				this.rupLength = 0.1;
			else if (rupLength < 1d)
				this.rupLength = roundValueToDiscr(rupLength, 0.1, 0.1);
			else if (rupLength < 10d)
				this.rupLength = roundValueToDiscr(rupLength, 0.5, 0.25);
			else if (rupLength < 20d)
				this.rupLength = roundValueToDiscr(rupLength, 1d, 0.5);
			else if (rupLength < 50d)
				this.rupLength = roundValueToDiscr(rupLength, 2d, 1);
			else
				this.rupLength = roundValueToDiscr(rupLength, 5d, 1);
			
			if (rupWidth < 1)
				this.rupWidth = 1;
			else if (rupWidth < 10)
				this.rupWidth = roundValueToDiscr(rupWidth, 1d, 1d);
			else if (rupWidth < 20)
				this.rupWidth = roundValueToDiscr(rupWidth, 2d, 1d);
			else
				this.rupWidth = roundValueToDiscr(rupWidth, 5d, 1d);
			
			this.dip = roundValueToDiscr(dip, 10d, 10d);
		}
		
		private static double roundValueToDiscr(double value, double discr, double min) {
			return Math.max(min, Math.round(value/discr)*discr);
		}

		@Override
		public int hashCode() {
			return Objects.hash(dip, rupLength, rupWidth);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			RuptureKey other = (RuptureKey) obj;
			return Double.doubleToLongBits(dip) == Double.doubleToLongBits(other.dip)
					&& Double.doubleToLongBits(rupLength) == Double.doubleToLongBits(other.rupLength)
					&& Double.doubleToLongBits(rupWidth) == Double.doubleToLongBits(other.rupWidth);
		}

		@Override
		public int compareTo(RuptureKey o) {
			// compare length first, then dip, then finally width
			int ret = Double.compare(rupLength, o.rupLength);
			if (ret != 0)
				return ret;
			ret = Double.compare(dip, o.dip);
			if (ret != 0)
				return ret;
			return Double.compare(rupWidth, o.rupWidth);
		}
	}
	
	private static class SampleFractileCalculator implements Supplier<double[]> {

		private double rEpi;
		private double rupLength;
		private double rupWidth;
		private double dip;
		private double[] alphaSamples;
		private double[] alongSamples;
		private double[] downDipSamples;
		private double[] fractiles;

		public SampleFractileCalculator(double rEpi, double rupLength, double rupWidth, double dip,
				double[] alphaSamples, double[] alongSamples, double[] downDipSamples, double[] fractiles) {
			this.rEpi = rEpi;
			this.rupLength = rupLength;
			this.rupWidth = rupWidth;
			this.dip = dip;
			this.alphaSamples = alphaSamples;
			this.fractiles = fractiles;
			if (rupLength > 1d)
				this.alongSamples = alongSamples;
			else
				// negligible length, don't bother sampling along-strike
				this.alongSamples = single_sample_0p5;
			if (dip < 85d && rupWidth > 1d)
				this.downDipSamples = downDipSamples;
			else
				// not dipping or negligible width, don't bother sampling down-dip
				this.downDipSamples = single_sample_0p5;
		}

		@Override
		public double[] get() {
			double[] samples = new double[alphaSamples.length*alongSamples.length*downDipSamples.length];
			Preconditions.checkState(samples.length > 1);
			int index = 0;
			double dipRad = Math.toRadians(dip);
			for (double alpha : alphaSamples)
				for (double fractDAS : alongSamples)
					for (double fractDD : downDipSamples)
						samples[index++] = calcRJB(rEpi, rupLength, rupWidth, dipRad, fractDAS, fractDD, alpha);
			Preconditions.checkState(index == samples.length);
			
			if (fractiles.length == 1 && Double.isNaN(fractiles[0])) {
				// special case for mean only, don't need inv CDF
				return new double[] {StatUtils.mean(samples)};
			}
			
			// calc normCDF
			LightFixedXFunc normCDF = ArbDiscrEmpiricalDistFunc.calcQuickNormCDF(samples, null);
			double minY = normCDF.getMinY();
			double maxY = normCDF.getMaxY();
			double[] ret = new double[fractiles.length];
			for (int i=0; i<fractiles.length; i++) {
				if (Double.isNaN(fractiles[i])) {
					// special case for mean
					ret[i] = StatUtils.mean(samples);
				} else {
					if (fractiles[i] <= minY)
						ret[i] = normCDF.getX(0);
					else if (fractiles[i] >= maxY)
						ret[i] = normCDF.getX(normCDF.size()-1);
					else
						ret[i] = normCDF.getFirstInterpolatedX(fractiles[i]);
				}
			}
			return ret;
		}
		
	}

}
