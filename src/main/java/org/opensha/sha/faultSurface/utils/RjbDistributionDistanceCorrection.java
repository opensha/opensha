package org.opensha.sha.faultSurface.utils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.Interpolate;
import org.opensha.sha.faultSurface.PointSurface;

import com.google.common.base.Preconditions;

public class RjbDistributionDistanceCorrection implements PointSourceDistanceCorrection {
	
	static final double MIN_NONZERO_DIST_DEFAULT = 0.1;
	static final double INITIAL_MAX_DIST_DEFAULT = 300d;
	static final double LOG_DIST_SAMPLE_DISCR_DEFAULT = 0.05d;
	static final int NUM_ALPHA_SAMPLES_DEFAULT = 200;
	static final int NUM_SAMPLES_ALONG_DEFAULT = 51;
	static final int NUM_SAMPLES_DOWN_DIP_DEFAULT = 5;
	
	public static InvCDFCache initDefaultCache(double[] fractiles, boolean sampleAlong, boolean sampleDownDip) {
		InvCDFCache ret = new InvCDFCache(sampleAlong, sampleDownDip, MIN_NONZERO_DIST_DEFAULT, INITIAL_MAX_DIST_DEFAULT,
				LOG_DIST_SAMPLE_DISCR_DEFAULT, NUM_ALPHA_SAMPLES_DEFAULT, NUM_SAMPLES_ALONG_DEFAULT, NUM_SAMPLES_DOWN_DIP_DEFAULT, fractiles);
//		ret.initLogDistBins(INITIAL_MAX_DIST_DEFAULT);
//		double[] logDistBins = new double[ret.logDistBins.size()];
//		double[] linearDistBins = new double[ret.logDistBins.size()];
//		for (int i=0; i<logDistBins.length; i++) {
//			logDistBins[i] = ret.logDistBins.getX(i);
//			linearDistBins[i] = Math.pow(10, logDistBins[i]);
//		}
//		System.out.println("Distance bins:");
//		System.out.println("\tLog:\t"+getSampleStr(logDistBins));
//		System.out.println("\tLin:\t"+getSampleStr(linearDistBins));
		return ret;
	}
	
	public static InvCDFCache initSupersampledCache(double[] fractiles, double latitude, double beta, double gridSpacingDegrees,
			int numCellSamples, boolean sampleAlong, boolean sampleDownDip) {
		double cellWidth = LocationUtils.horzDistanceFast(new Location(latitude, 0d), new Location(latitude, gridSpacingDegrees));
		double cellHeight = LocationUtils.horzDistanceFast(new Location(latitude-0.5*gridSpacingDegrees, 0d),
				new Location(latitude+0.5*gridSpacingDegrees, 0d));
		InvCDFCache ret = new InvCDFCache(sampleAlong, sampleDownDip, MIN_NONZERO_DIST_DEFAULT, INITIAL_MAX_DIST_DEFAULT,
				LOG_DIST_SAMPLE_DISCR_DEFAULT, NUM_ALPHA_SAMPLES_DEFAULT,
				numCellSamples, beta, cellWidth, cellHeight,
				NUM_SAMPLES_ALONG_DEFAULT, NUM_SAMPLES_DOWN_DIP_DEFAULT, fractiles);
		
//		System.out.println("Initiated supersampling cache for lat="+(float)latitude);
//		System.out.println("\tx samples: "+getSampleStr(ret.cellSamplesX));
//		System.out.println("\ty samples: "+getSampleStr(ret.cellSamplesY));
//		System.out.println("\talpha samples: "+getSampleStr(ret.alphaSamples));
		
		return ret;
	}
	
	private static final DecimalFormat sampleDF = new DecimalFormat("0.##");
	static String getSampleStr(double[] samples) {
		return Arrays.stream(samples)
	    		.mapToObj(sampleDF::format)
//				.mapToObj(v->Double.valueOf(v).floatValue()+"")
	    		.reduce((a, b) -> a + ", " + b)
	    		.orElse("");
	}
	
	public static WeightedList<RjbDistributionDistanceCorrection> getEvenlyWeightedFractiles(
			int numFractiles, boolean sampleAlong, boolean sampleDownDip) {
		Preconditions.checkState(numFractiles > 1);
		double[] fractiles = buildSpacedSamples(0d, 1d, numFractiles);
		InvCDFCache cache = initDefaultCache(fractiles, sampleAlong, sampleDownDip);
		RjbDistributionDistanceCorrection[] corrs = new RjbDistributionDistanceCorrection[numFractiles];
		for (int i=0; i<numFractiles; i++)
			corrs[i] = new RjbDistributionDistanceCorrection(fractiles[i], cache);
		return WeightedList.evenlyWeighted(corrs);
	}
	
	public static WeightedList<RjbDistributionDistanceCorrection> getImportanceSampledFractiles(
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
		WeightedList<RjbDistributionDistanceCorrection> ret = new WeightedList<>(fractiles.length);
		InvCDFCache cache = initDefaultCache(fractiles, sampleAlong, sampleDownDip);
		for (int i=0; i<fractiles.length; i++)
			ret.add(new RjbDistributionDistanceCorrection(fractiles[i], cache), weights[i]);
		
		return ret;
	}
	
	private double fractile;
	private int indexInCache;
	private InvCDFCache cache;

	public RjbDistributionDistanceCorrection(double fractile, boolean sampleAlong, boolean sampleDownDip) {
		this(fractile, initDefaultCache(new double[] {fractile}, sampleAlong, sampleDownDip));
	}

	public RjbDistributionDistanceCorrection(double fractile, InvCDFCache cache) {
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
		String str;
		if (Double.isNaN(fractile))
			str = "mean";
		else
			str = "p"+(float)(fractile*100d);
		if (Double.compare(fractile, cache.fractiles[0]) == 0)
			// first
			str += " (sampleAlong="+(cache.alongSamples.length > 1)+", sampleDownDip="+(cache.downDipSamples.length > 1)+")";
		return str;
	}

	@Override
	public double getCorrectedDistanceJB(Location siteLoc, double mag, PointSurface surf, double horzDist) {
		return getCachedDist(surf, horzDist, cache, indexInCache);
	}
	
	static double getCachedDist(PointSurface surf, double horzDist, InvCDFCache cache, int indexInCache) {
		double ret;
		if (horzDist < cache.minNonzeroDist) {
			double zeroVal = cache.getZeroDistVals(surf.getAveLength(), surf.getAveWidth(), surf.getAveDip())[indexInCache];
			if (horzDist < 1e-10)
				// zero
				return zeroVal;
			// nonzero, interpolate
			double firstNonzeroVal = cache.getFractileFuncs(surf.getAveLength(), surf.getAveWidth(), surf.getAveDip(),
					horzDist)[indexInCache].getY(0);
			ret = Interpolate.findY(0d, zeroVal, cache.minNonzeroDist, firstNonzeroVal, horzDist);
		} else {
			EvenlyDiscretizedFunc logDistFunc = cache.getFractileFuncs(
					surf.getAveLength(), surf.getAveWidth(), surf.getAveDip(), horzDist)[indexInCache];
			double logDist = Math.log10(horzDist);
			// distances are discretized in log space, but do the actual interpolation in linear space
			int xIndBelow = logDistFunc.getClosestXIndex(logDist);
			if (logDist < logDistFunc.getX(xIndBelow))
				xIndBelow--;
			double distBelow = Math.pow(10, logDistFunc.getX(xIndBelow));
			double distAbove = Math.pow(10, logDistFunc.getX(xIndBelow+1));
			ret = Interpolate.findY(distBelow, logDistFunc.getY(xIndBelow), distAbove, logDistFunc.getY(xIndBelow+1), horzDist);
		}
		if (ret < 0d) {
			// sometimes the interpolation leads to tiny negative values, force positive
			Preconditions.checkState(ret > -1e-10);
			return 0d;
		}
		Preconditions.checkState(ret >= 0d, "Bad distance=%s for surf=%s, horzDist=%s, cache=%s",
				ret, surf, horzDist, cache);
		return ret;
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
		double rupHorzWidth = rupWidth * Math.cos(dipRad);
		double sinA = Math.sin(alphaRad);
		double cosA = Math.cos(alphaRad);
		
		return doCalcRJB(rEpi, rupLength, rupHorzWidth, gridNodeFractDAS, gridNodeFractDepth, sinA, cosA);
	}
	
	/**
	 * Quicker method for use in inner loops with pre-computed rupHorzWidth, sin(alpha), and cos(alpha)
	 * 
	 * @param rEpi
	 * @param rupLength
	 * @param rupHorzWidth
	 * @param gridNodeFractDAS
	 * @param gridNodeFractDepth
	 * @param sinAlpha
	 * @param cosAlpha
	 * @return
	 */
	private static double doCalcRJB(double rEpi, double rupLength, double rupHorzWidth,
			double gridNodeFractDAS, double gridNodeFractDepth, double sinAlpha, double cosAlpha) {
		// Fault rectangle in local (strike,dip) coords is:
		//       X in [Xmin, Xmax], with total length = rupLength
		//       Y in [Ymin, Ymax], with total width = rupHorzWidth
		//    where (0,0) is the grid node in local coordinates.
		double xMin = -gridNodeFractDAS * rupLength;
		double xMax = xMin + rupLength; // = (1 - gridNodeFractDAS)*rupLength
		double yMin = -gridNodeFractDepth * rupHorzWidth;
		double yMax = yMin + rupHorzWidth;     // = (1 - gridNodeFractDepth)*wHorz

		// Convert the site's global coords (rEpi, 0) -> local (xLoc, yLoc)
		//    local X-axis = strike = (cos(alpha), sin(alpha))
		//    local Y-axis = dip in map = (-sin(alpha), cos(alpha))
		//    node is at (0,0), site is at (rEpi, 0).
		double xLoc = rEpi * cosAlpha;   // = x*cosA + y*sinA, but site y=0
		double yLoc = -rEpi * sinAlpha;  // = -x*sinA + y*cosA

		// Distance from (xLoc, yLoc) to that axis-aligned bounding box
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

		double ret = Math.sqrt(dx * dx + dy * dy);
		Preconditions.checkState(ret>=0d, "ret=%s, dx=%x, dy=%s", ret, dx, dy);
		return ret;
	}
	
	static double[] buildSpacedSamples(double min, double max, int num) {
		return buildSpacedSamples(min, max, num, false);
	}
	
	static double[] buildSpacedSamples(double min, double max, int num, boolean sampleEdges) {
		double delta = (max-min)/(double)(sampleEdges ? num-1 : num);
		double ret0 = sampleEdges ? 0d : min + 0.5*delta;
		double[] ret = new double[num];
		for (int i=0; i<num; i++)
			ret[i] = ret0 + i*delta;
		return ret;
	}

	private static final double[] single_sample_0p5 = {0.5};
	private static final double[] single_sample_0 = {0};
	
	public static class InvCDFCache {
		
		public final double minNonzeroDist;
		final double logDistSampleDiscr;
		final double[] alphaRadSamples;
		final double betaRad;
		final double[] cellSamplesX;
		final double[] cellSamplesY;
		final double[] alongSamples;
		final double[] downDipSamples;
		public final double[] fractiles;

		private double maxDistBin;
		private EvenlyDiscretizedFunc logDistBins;

		private ConcurrentMap<RuptureKey, EvenlyDiscretizedFunc[]> valueFuncs = new ConcurrentHashMap<>();
		private ConcurrentMap<RuptureKey, double[]> zeroDistValues = new ConcurrentHashMap<>();

		public InvCDFCache(boolean sampleAlongStrike, boolean sampleDownDip, double minNonzeroDist,
				double initialMaxDist, double logDistSampleDiscr,
				int numAlphaSamples, int numSamplesAlong, int numSamplesDownDip,
				double[] fractiles) {
			this(sampleAlongStrike, sampleDownDip, minNonzeroDist, initialMaxDist, logDistSampleDiscr,
					numAlphaSamples, 1, 0d, 0d, 0d,
					numSamplesAlong, numSamplesDownDip, fractiles);
		}
		


		public InvCDFCache(boolean sampleAlongStrike, boolean sampleDownDip, double minNonzeroDist,
				double initialMaxDist, double logDistSampleDiscr,
				int numAlphaSamples,
				int numCellSamples, double beta, double cellWidth, double cellHeight,
				int numSamplesAlong, int numSamplesDownDip,
				double[] fractiles) {
			Preconditions.checkState(minNonzeroDist > 0d);
			this.minNonzeroDist = minNonzeroDist;
			Preconditions.checkState(logDistSampleDiscr > 0d);
			this.logDistSampleDiscr = logDistSampleDiscr;
			Preconditions.checkState(numAlphaSamples > 1);
//			if (!sampleAlongStrike && !sampleDownDip && numCellSamples == 1)
				// no sampling along-strike nor down-dip, can just sample over 0-180
//				alphaRadSamples = buildSpacedSamples(0d, Math.PI*0.5, numAlphaSamples, ALPHA_SAMPLE_EDGES);
				alphaRadSamples = Arrays.copyOf(buildSpacedSamples(0d, Math.PI, numAlphaSamples+1), numAlphaSamples);
//			else
//				// need to do the full circle
////				alphaRadSamples = buildSpacedSamples(0d, 2d*Math.PI, numAlphaSamples, ALPHA_SAMPLE_EDGES);
//				alphaRadSamples = Arrays.copyOf(buildSpacedSamples(0d, 2d*Math.PI, numAlphaSamples+1), numAlphaSamples);
			if (numCellSamples > 1) {
				Preconditions.checkState(cellWidth > 0 || cellHeight > 0);
				if (cellWidth > 0)
					cellSamplesX = buildSpacedSamples(-0.5*cellWidth, 0.5*cellWidth, numCellSamples);
				else
					cellSamplesX = single_sample_0;
				if (cellHeight > 0)
					cellSamplesY = buildSpacedSamples(-0.5*cellHeight, 0.5*cellHeight, numCellSamples);
				else
					cellSamplesY = single_sample_0;
				betaRad = Math.toRadians(beta);
			} else {
				cellSamplesX = single_sample_0;
				cellSamplesY = single_sample_0;
				betaRad = 0d;
			}
//			System.out.println("Cache for beta="+(float)beta+" (rad="+(float)betaRad+"), cellW="+(float)cellWidth+", cellH="+(float)cellHeight);
			if (sampleAlongStrike) {
				Preconditions.checkState(numSamplesAlong > 1);
				alongSamples = buildSpacedSamples(0d, 1d, numSamplesAlong, true);
			} else {
				alongSamples = single_sample_0p5;
			}
			if (sampleDownDip) {
				Preconditions.checkState(numSamplesDownDip > 1);
				downDipSamples = buildSpacedSamples(0d, 1d, numSamplesDownDip, true);
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
							rEpi, rupLen, rupWidth, dip, alphaRadSamples, betaRad, cellSamplesX, cellSamplesY,
							alongSamples, downDipSamples, fractiles)));
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
						
						// do it with a local variable because there could be thread contention
						EvenlyDiscretizedFunc[] expandedArray = new EvenlyDiscretizedFunc[ret.length];
						
						// grow them
						// first copy into larger functions
						int origNum = ret[0].size();
						for (int i=0; i<ret.length; i++) {
							EvenlyDiscretizedFunc expanded = new EvenlyDiscretizedFunc(logDistBins.getMinX(), logDistBins.size(), logDistBins.getDelta());
							for (int r=0; r<ret[i].size(); r++) {
								Preconditions.checkState(i > 0 || Precision.equals(expanded.getX(r), ret[i].getX(r), 0.0001));
								expanded.set(r, ret[i].getY(r));
							}
							expandedArray[i] = expanded;
						}
						// now calculate for the new distances
						List<CompletableFuture<double[]>> futures = new ArrayList<>(logDistBins.size()-origNum);
						for (int r=origNum; r<logDistBins.size(); r++) {
							double rEpi = Math.pow(10, logDistBins.getX(r));
							futures.add(CompletableFuture.supplyAsync(new SampleFractileCalculator(
									rEpi, rupLen, rupWidth, dip, alphaRadSamples, betaRad, cellSamplesX, cellSamplesY,
									alongSamples, downDipSamples, fractiles)));
						}
						for (int f=0; f<futures.size(); f++) {
							double[] values = futures.get(f).join();
							int r = origNum+f;
							for (int i=0; i<ret.length; i++)
								expandedArray[i].set(r, values[i]);
						}
						ret = expandedArray;
						synchronized (valueFuncs) {
							valueFuncs.put(key, ret);
						}
					}
				}
			}
			return ret;
		}
		
		public double[] getZeroDistVals(double rupLen, double rupWidth, double dip) {
			RuptureKey key = new RuptureKey(rupLen, rupWidth, dip);
			double[] ret = zeroDistValues.get(key);
			
			if (ret == null) {
				// need to build it from scratch
				ret = new SampleFractileCalculator(
						0d, rupLen, rupWidth, dip, alphaRadSamples, betaRad, cellSamplesX, cellSamplesY,
						alongSamples, downDipSamples, fractiles).get();
				synchronized (valueFuncs) {
					zeroDistValues.put(key, ret);
				}
			}
			return ret;
		}
	}
	
	private static class RuptureKey implements Comparable<RuptureKey> {
		
		private final double rupLength, rupWidth, dip;
		
		public RuptureKey(double rupLength, double rupWidth, double dip) {
			// snap values
			
//			this.rupLength = roundValueToDiscr(rupLength, 0.01, 0.05);
			if (rupLength < 0.1)
				this.rupLength = 0.1;
			else if (rupLength < 1d)
				this.rupLength = roundValueToDiscr(rupLength, 0.05, 0.1);
			else if (rupLength < 10d)
				this.rupLength = roundValueToDiscr(rupLength, 0.25, 0.25);
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

		private final double rEpi;
		private final double rupLength;
		private final double rupWidth;
		private final double dip;
		private final double[] alphaRadSamples;
		private final double[] cellSamplesX;
		private final double[] cellSamplesY;
		private final double[] alongSamples;
		private final double[] downDipSamples;
		private final double[] fractiles;
		private final double betaRad;
		
		private final boolean haveGeomSamples;
		private final boolean haveCellSamples;

		public SampleFractileCalculator(double rEpi, double rupLength, double rupWidth, double dip,
				double[] alphaRadSamples, double betaRad, double[] cellSamplesX, double[] cellSamplesY,
				double[] alongSamples, double[] downDipSamples, double[] fractiles) {
			this.rEpi = rEpi;
			this.rupLength = rupLength;
			this.rupWidth = rupWidth;
			this.dip = dip;
			this.alphaRadSamples = alphaRadSamples;
			this.fractiles = fractiles;
			if (cellSamplesX == null)
				this.cellSamplesX = single_sample_0;
			else
				this.cellSamplesX = cellSamplesX;
			if (cellSamplesY == null)
				this.cellSamplesY = single_sample_0;
			else
				this.cellSamplesY = cellSamplesY;
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
			
			this.haveGeomSamples = this.downDipSamples.length > 1 || this.alongSamples.length > 1;
			this.haveCellSamples = this.cellSamplesX.length > 1 || this.cellSamplesY.length > 1;
			if (haveCellSamples)
				this.betaRad = betaRad;
			else
				this.betaRad = 0d;
		}

		@Override
		public double[] get() {
			double[] samples = new double[alphaRadSamples.length*cellSamplesX.length*cellSamplesY.length*alongSamples.length*downDipSamples.length];
			Preconditions.checkState(samples.length > 1);
			int index = 0;
			double rupHorzWidth = rupWidth * Math.cos(Math.toRadians(dip));
			
			double rEpiSinB, rEpiCosB;
			if (haveCellSamples) {
				rEpiSinB = rEpi*Math.sin(betaRad);
				rEpiCosB = rEpi*Math.cos(betaRad);
			} else {
				rEpiSinB = Double.NaN;
				rEpiCosB = Double.NaN;
			}
			
			// alpha is the direction of the fault relative to the direction to the site
			// alpha=0 means the site is perfectly in the along-strike direction
			
			// split these cases up to avoid unnecessary loops if we can
			if (haveCellSamples || haveGeomSamples) {
				// sample different intermediate alpha values in the inner loops
				int numPerAlpha = samples.length / alphaRadSamples.length;
				double[] alphaRadOffsets = buildSpacedSamples(0d, alphaRadSamples[1]-alphaRadSamples[0], numPerAlpha+1, true);
				// randomize in a repeatable fashion
//				ArrayUtils.shuffle(alphaRadOffsets, new Random(numPerAlpha + Double.doubleToLongBits(rEpi)));
				ArrayUtils.shuffle(alphaRadOffsets, new Random(numPerAlpha));
				int alphaRadOffsetStartIndex = 0;
				double[] sinAs = new double[numPerAlpha];
				double[] cosAs = new double[numPerAlpha];
				for (double alphaRad : alphaRadSamples) {
					for (int i=0; i<numPerAlpha; i++) {
						double offsetAlphaRad = alphaRad + alphaRadOffsets[(i+alphaRadOffsetStartIndex) % numPerAlpha];
						sinAs[i] = Math.sin(offsetAlphaRad);
						cosAs[i] = Math.cos(offsetAlphaRad);
					}
					// rotate the offsets so that the inner loops don't always have the same offset
					alphaRadOffsetStartIndex++;
					
					int i = 0;
					
					if (haveCellSamples) {
						// we have cell sqmples
						for (double cellSampleX : cellSamplesX) {
							for (double cellSampleY : cellSamplesY) {
								// calculate revised rEpi based on the sample location within the cell
								// beta is 0 in the y direction
								double dx = rEpiSinB - cellSampleX;
							    double dy = rEpiCosB - cellSampleY;

							    double rEpiSample = Math.sqrt(dx*dx + dy*dy);
							    if (haveGeomSamples) {
							    	// we also have geometry samples
							    	for (double fractDAS : alongSamples) {
										for (double fractDD : downDipSamples) {
											samples[index++] = doCalcRJB(rEpiSample, rupLength, rupHorzWidth, fractDAS, fractDD, sinAs[i], cosAs[i]);
											i++;
										}
							    	}
							    } else {
							    	// no geometry samples, just 1 for this cell sample
							    	samples[index++] = doCalcRJB(rEpiSample, rupLength, rupHorzWidth, 0.5d, 0.5d, sinAs[i], cosAs[i]);
							    	i++;
							    }
							}
						}
					} else {
						// we have geometry samples (but no cell samples)
						for (double fractDAS : alongSamples) {
							for (double fractDD : downDipSamples) {
								samples[index++] = doCalcRJB(rEpi, rupLength, rupHorzWidth, fractDAS, fractDD, sinAs[i], cosAs[i]);
								i++;
							}
						}
					}
				}
			} else {
				// simple (and most common?) case, avoid any nested loops
				for (double alphaRad : alphaRadSamples) {
					
					double sinA = Math.sin(alphaRad);
					double cosA = Math.cos(alphaRad);
					samples[index++] = doCalcRJB(rEpi, rupLength, rupHorzWidth, 0.5d, 0.5d, sinA, cosA);
				}
			}
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
