package org.opensha.sha.faultSurface.utils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.Interpolate;
import org.opensha.sha.faultSurface.PointSurface;

import com.google.common.base.Preconditions;

public class RjbDistributionDistanceCorrection implements PointSourceDistanceCorrection {
	
	// for rEpi below this, we'll just interpolate linearly between the value at this value and the value at rEpi=0 
	static final double MIN_NONZERO_DIST_DEFAULT = 0.1;
	// initial max rEpi to calculate for; will go out further if/as needed
	static final double INITIAL_MAX_DIST_DEFAULT = 300d;
	// we cache at fixed rEpis, spaced with this discretization (in log10 units)
//	static final double LOG_DIST_SAMPLE_DISCR_DEFAULT = 0.05d;
	static final double LOG_DIST_SAMPLE_DISCR_DEFAULT = 0.02d;
	// true means there's always a sample exactly pointing at the site
	// with a good nAlpha choice (e.g., 180), there will also be one perfectly perpendicular
	static final boolean ALPHA_ALIGN_EDGES = true;
	// number of angle samples
	static final int NUM_ALPHA_SAMPLES_DEFAULT = 180; // span is 0-180, 90 means one every 2 degrees
	// better performance is achieved by further interpolating distances between alpha values before calculating the CDF
	static final int NUM_ALPHA_INTERP_SAMPLES = 3;
	static final int NUM_SS_ALPHA_SAMPLES_DEFAULT = 90; // alpha samples when also supersampling
	static final int NUM_SS_AND_ALONG_ALPHA_SAMPLES_DEFAULT = 90; // alpha samples when supersampling and sampling along
	// if we're sampling along-strike, the number of said samples
	static final int NUM_SAMPLES_ALONG_DEFAULT = 21;
	// interpolate between discrete DAS samples
	static final int NUM_ALONG_INTERP_SAMPLES = 10;
	// if we're sampling down-dip, the number of said samples
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
		// reduce the sample count for supersampling because we're already increasing it a ton, and also add
		// sub-sampling in this mode
		int numAlpha = sampleAlong ? NUM_SS_AND_ALONG_ALPHA_SAMPLES_DEFAULT : NUM_SS_ALPHA_SAMPLES_DEFAULT;
		int numAlong = NUM_SAMPLES_ALONG_DEFAULT/2;
		if (numAlong % 2 == 0)
			// keep it odd;
			numAlong++;
		InvCDFCache ret = new InvCDFCache(sampleAlong, sampleDownDip, MIN_NONZERO_DIST_DEFAULT, INITIAL_MAX_DIST_DEFAULT,
				LOG_DIST_SAMPLE_DISCR_DEFAULT, numAlpha,
				numCellSamples, beta, cellWidth, cellHeight,
				numAlong, NUM_SAMPLES_DOWN_DIP_DEFAULT, fractiles);
		
//		System.out.println("Initiated supersampling cache for lat="+(float)latitude);
//		System.out.println("\tx samples: "+getSampleStr(ret.cellSamplesX));
//		System.out.println("\ty samples: "+getSampleStr(ret.cellSamplesY));
//		System.out.println("\talpha samples: "+getSampleStr(ret.alphaSamples));
		
		return ret;
	}
	
	private static final DecimalFormat sampleDF = new DecimalFormat("0.##");
	static String getSampleStr(double[] samples) {
		return getSampleStr(samples, false);
	}
	
	static String getSampleStr(double[] samples, boolean radToDeb) {
		return Arrays.stream(samples)
				.map(d->radToDeb ? Math.toDegrees(d) : d)
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
		boolean inside = true;
		double dx = 0.0;
		if (xLoc < xMin) {
			dx = xMin - xLoc;
			inside = false;
		} else if (xLoc > xMax) {
			dx = xLoc - xMax;
			inside = false;
		}

		double dy = 0.0;
		if (yLoc < yMin) {
			dy = yMin - yLoc;
			inside = false;
		} else if (yLoc > yMax) {
			dy = yLoc - yMax;
			inside = false;
		}
		if (inside)
			return 0d;

		double ret = Math.sqrt(dx * dx + dy * dy);
		Preconditions.checkState(ret>=0d, "ret=%s, dx=%x, dy=%s", ret, dx, dy);
		return ret;
	}
	
	static double[] buildSpacedSamples(double min, double max, int num) {
		return buildSpacedSamples(min, max, num, false);
	}
	
	static double[] buildSpacedSamples(double min, double max, int num, boolean sampleEdges) {
		double delta = (max-min)/(double)(sampleEdges ? num-1 : num);
		double ret0 = sampleEdges ? min : min + 0.5*delta;
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
			// can just do 0->PI because we're doing rJB and the surface projection is symmetrical across the axis
			double maxAlpha = Math.PI;
			if (ALPHA_ALIGN_EDGES)
				// start at 0 and end one bin before the end (to avoid double counting)
				alphaRadSamples = Arrays.copyOf(buildSpacedSamples(0d, maxAlpha, numAlphaSamples+1, true), numAlphaSamples);
			else
				// bin centers, can put edges right at 0 and 180 without double counting.
				alphaRadSamples = buildSpacedSamples(0d, maxAlpha, numAlphaSamples, false);
			if (numCellSamples > 1) {
				boolean cellSamplesAtEdges = false; // we don't want to colocate with the next cell over
				Preconditions.checkState(cellWidth > 0 || cellHeight > 0);
				if (cellWidth > 0)
					cellSamplesX = buildSpacedSamples(-0.5*cellWidth, 0.5*cellWidth, numCellSamples, cellSamplesAtEdges);
				else
					cellSamplesX = single_sample_0;
				if (cellHeight > 0)
					cellSamplesY = buildSpacedSamples(-0.5*cellHeight, 0.5*cellHeight, numCellSamples, cellSamplesAtEdges);
				else
					cellSamplesY = single_sample_0;
				betaRad = Math.toRadians(beta);
			} else {
				cellSamplesX = single_sample_0;
				cellSamplesY = single_sample_0;
				betaRad = 0d;
			}
//			System.out.println("Cache for beta="+(float)beta+" (rad="+(float)betaRad+"), cellW="+(float)cellWidth+", cellH="+(float)cellHeight);
			boolean geomSamplesAtEdges = true;
			if (sampleAlongStrike) {
				Preconditions.checkState(numSamplesAlong > 1);
				alongSamples = buildSpacedSamples(0d, 1d, numSamplesAlong, geomSamplesAtEdges);
			} else {
				alongSamples = single_sample_0p5;
			}
			if (sampleDownDip) {
				Preconditions.checkState(numSamplesDownDip > 1);
				downDipSamples = buildSpacedSamples(0d, 1d, numSamplesDownDip, geomSamplesAtEdges);
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
				synchronized (zeroDistValues) {
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
	
	private static boolean FIRST_D = true;
	
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
			double rupHorzWidth = rupWidth * Math.cos(Math.toRadians(dip));
			
			// figure out the closest and farthest it could possibly be
			// we'll then discretize between closest and furthest and map samples to those discrete values; this will
			// allow us to quickly create a CDF, and also avoid storing all samples in memory at once
			double farthest = rEpi;
			// diagonal of the surface projection of the rupture
			double horzDiag = Math.sqrt(rupLength*rupLength + rupHorzWidth*rupHorzWidth);
			double closest;
			if (haveGeomSamples) {
				// full thing could be in our direction
				closest = Math.max(0d, rEpi-horzDiag);
			} else {
				// only half can be in our direction
				closest = Math.max(0d, rEpi-0.5*horzDiag);
			}
			if (haveCellSamples) {
				// even if perfectly aligned, can be at the corner of the cell
				double maxX = 0d;
				for (double x : cellSamplesX)
					maxX = Math.max(maxX, Math.abs(x));
				double maxY = 0d;
				for (double y : cellSamplesY)
					maxY = Math.max(maxY, Math.abs(y));
				double cornerDist = Math.sqrt(maxX*maxX + maxY*maxY);
				// could be even further than rEpi
				farthest += cornerDist;
				// or could be even closer
				closest = Math.max(0, closest-cornerDist);
			}
			
			if (Precision.equals(farthest, closest, 1e-4)) {
				// happns if rEpi=0 and no cell sampling
				Preconditions.checkState(Precision.equals(0d, rEpi, 1e-3));
				double[] ret = new double[fractiles.length];
				for (int i=0; i<ret.length; i++)
					ret[i] = closest;
				return ret;
			}
			
			double rEpiSinB, rEpiCosB;
			if (haveCellSamples) {
				rEpiSinB = rEpi*Math.sin(betaRad);
				rEpiCosB = rEpi*Math.cos(betaRad);
			} else {
				rEpiSinB = Double.NaN;
				rEpiCosB = Double.NaN;
			}
			
			final boolean D;
			if (FIRST_D) {
				synchronized (SampleFractileCalculator.class) {
					D = FIRST_D;
					FIRST_D = false;
				}
			} else {
				D = false;
			}
			
			// alpha is the direction of the fault relative to the direction to the site
			// alpha=0 means the site is perfectly in the along-strike direction
			
			if (D) System.out.println("haveCellSamples="+haveCellSamples+", haveGeomSamples="+haveGeomSamples
					+", nAlpha="+alphaRadSamples.length+", nAlphaInterp="+NUM_ALPHA_INTERP_SAMPLES
					+"\n\talpha: "+getSampleStr(alphaRadSamples, true));
			
			if (D) {
				if (haveGeomSamples) {
					System.out.println("nAlong="+alongSamples.length+", along: "+getSampleStr(alongSamples));
					System.out.println("nDD="+downDipSamples.length+", DD: "+getSampleStr(downDipSamples));
				}
				if (haveCellSamples) {
					System.out.println("nCellX="+cellSamplesX.length+", x: "+getSampleStr(cellSamplesX));
					System.out.println("nCellY="+cellSamplesY.length+", y: "+getSampleStr(cellSamplesY));
				}
			}
			
			SampleDistributionTracker sampleDist = new SampleDistributionTracker(closest, farthest, 1000); 
			
			int numSamples = alphaRadSamples.length*cellSamplesX.length*cellSamplesY.length;
			double[] alongSubSampleFracts = null;
			double[] alongOneMinusSubSampleFracts = null;
			if (haveGeomSamples) {
				int numAlongSamples = alongSamples.length;
				if (alongSamples.length > 1 && NUM_ALONG_INTERP_SAMPLES > 1) {
					alongSubSampleFracts = Arrays.copyOf(buildSpacedSamples(0d, 1d, NUM_ALONG_INTERP_SAMPLES+1), NUM_ALONG_INTERP_SAMPLES);
					alongOneMinusSubSampleFracts = new double[alongSubSampleFracts.length];
					for (int i=0; i<alongOneMinusSubSampleFracts.length; i++)
						alongOneMinusSubSampleFracts[i] = 1d-alongSubSampleFracts[i];
					numAlongSamples = (numAlongSamples-1)*NUM_ALONG_INTERP_SAMPLES+1;
				}
				numSamples *= numAlongSamples*downDipSamples.length;
			}
			Preconditions.checkState(numSamples>1);
			int numPerAlpha = numSamples/alphaRadSamples.length;
			Preconditions.checkState(alphaRadSamples.length > 1);
			
			double[] firstAlphaVals = null;
			double[] prevAlphaVals = new double[numPerAlpha];
			double[] tempArray = new double[numPerAlpha];
			
			double[] subSampleFracts = null;
			double[] oneMinusFracts = null;
			if (NUM_ALPHA_INTERP_SAMPLES > 1) {
				subSampleFracts = Arrays.copyOf(buildSpacedSamples(0d, 1d, NUM_ALPHA_INTERP_SAMPLES+1, true), NUM_ALPHA_INTERP_SAMPLES);
				if (D) System.out.println("alpha subSampleFracts: "+getSampleStr(subSampleFracts));
				oneMinusFracts = new double[subSampleFracts.length];
				for (int i=0; i<oneMinusFracts.length; i++)
					oneMinusFracts[i] = 1d - subSampleFracts[i];
			}
			
			for (int a=0; a<alphaRadSamples.length; a++) {
				double alphaRad = alphaRadSamples[a];
				double sinA = Math.sin(alphaRad);
				double cosA = Math.cos(alphaRad);
				
				Preconditions.checkState(tempArray != prevAlphaVals); // check to make sure the array rolling worked
				double[] innerSamples = tempArray;
				int index = 0;
				
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
						    	if (NUM_ALONG_INTERP_SAMPLES > 1) {
						    		index = calcQuickGeomSamples(alongSamples, downDipSamples, alongSubSampleFracts,
						    				alongOneMinusSubSampleFracts, innerSamples, index, rEpiSample, rupLength, rupHorzWidth, sinA, cosA);
						    	} else {
						    		for (double fractDAS : alongSamples) {
						    			for (double fractDD : downDipSamples) {
						    				innerSamples[index++] = doCalcRJB(rEpiSample, rupLength, rupHorzWidth,
						    						fractDAS, fractDD, sinA, cosA);
						    			}
						    		}
						    	}
						    } else {
						    	// no geometry samples, just 1 for this cell sample
						    	innerSamples[index++] = doCalcRJB(rEpiSample, rupLength, rupHorzWidth, 0.5d, 0.5d, sinA, cosA);
						    }
						}
					}
				} else if (haveGeomSamples) {
					// we have geometry samples (but no cell samples)
					if (NUM_ALONG_INTERP_SAMPLES > 1) {
						index = calcQuickGeomSamples(alongSamples, downDipSamples, alongSubSampleFracts,
								alongOneMinusSubSampleFracts, innerSamples, index, rEpi, rupLength, rupHorzWidth, sinA, cosA);
					} else {
						for (double fractDAS : alongSamples) {
							for (double fractDD : downDipSamples) {
								innerSamples[index++] = doCalcRJB(rEpi, rupLength, rupHorzWidth, fractDAS, fractDD, sinA, cosA);
							}
						}
					}
				} else {
					// neither geom nor cell samples
					innerSamples[index++] = doCalcRJB(rEpi, rupLength, rupHorzWidth, 0.5d, 0.5d, sinA, cosA);
				}
				
				Preconditions.checkState(innerSamples.length == index);
				
				if (a == 0)
					// stash values at first alpha for wraparound interpolation
					firstAlphaVals = Arrays.copyOf(innerSamples, numPerAlpha);
				
				if (NUM_ALPHA_INTERP_SAMPLES > 1 && a > 0) {
					// interpolate from the previous alpha value
					addInterpolateSamplesBetween(sampleDist, prevAlphaVals, innerSamples, subSampleFracts, oneMinusFracts);
				}
				
				// add them
				for (double sample : innerSamples)
					sampleDist.addValue(sample);
				
				// no longer need the previous, but reuse the array
				tempArray = prevAlphaVals;
				// set ours as previous
				prevAlphaVals = innerSamples;
			}
			
			if (NUM_ALPHA_INTERP_SAMPLES > 1)
				// wrap around interpolation
				addInterpolateSamplesBetween(sampleDist, prevAlphaVals, firstAlphaVals, subSampleFracts, oneMinusFracts);
			
			if (fractiles.length == 1 && Double.isNaN(fractiles[0])) {
				// special case for mean only, don't need inv CDF
				return new double[] {sampleDist.sum/(double)sampleDist.count};
			}
			
			// convert to normCDF
			double invSum = 1d/(double)sampleDist.count;
			double runningSumY = 0d;
			EvenlyDiscretizedFunc dist = sampleDist.dist;
			for (int i=0; i<dist.size(); i++) {
				runningSumY += dist.getY(i);
				dist.set(i, runningSumY*invSum);
			}
			
			// calc normCDF
//			LightFixedXFunc normCDF = ArbDiscrEmpiricalDistFunc.calcQuickNormCDF(samples, null);
			double minY = dist.getMinY();
			double maxY = dist.getMaxY();
			double[] ret = new double[fractiles.length];
			for (int i=0; i<fractiles.length; i++) {
				if (Double.isNaN(fractiles[i])) {
					// special case for mean
					ret[i] = sampleDist.sum/(double)sampleDist.count;
				} else {
					if (fractiles[i] <= minY)
						ret[i] = dist.getX(0);
					else if (fractiles[i] >= maxY)
						ret[i] = dist.getX(dist.size()-1);
					else
						ret[i] = dist.getFirstInterpolatedX(fractiles[i]);
				}
			}
			return ret;
		}
		
	}
	
	private static int calcQuickGeomSamples(double[] alongSamples, double[] downDipSamples,
			double[] subSampleFracts, double[] oneMinusFracts, double[] destArray, int index,
			double rEpi, double rupLength, double rupHorzWidth, double sinA, double cosA) {
		double[] prevVals = new double[downDipSamples.length];
		for (int i=0; i<alongSamples.length; i++) {
			double fractDAS = alongSamples[i];
			for (int j=0; j<downDipSamples.length; j++) {
				double fractDD = downDipSamples[j];
				double val = doCalcRJB(rEpi, rupLength, rupHorzWidth, fractDAS, fractDD, sinA, cosA);
				if (i > 0) {
					// interpolate from prior val
					for (int k=1; k<subSampleFracts.length; k++)
						destArray[index++] = oneMinusFracts[k]*prevVals[j] + subSampleFracts[k]*val;
				}
				prevVals[j] = val;
				destArray[index++] = val;
			}
		}
		return index;
	}
	
	private static class SampleDistributionTracker {
		
		private EvenlyDiscretizedFunc dist;
		private int count;
		private double sum;
		
		private double minVal, maxVal;
		private double minCheck, maxCheck;
		
		public SampleDistributionTracker(double minVal, double maxVal, int numDiscretizations) {
			dist = new EvenlyDiscretizedFunc(minVal, maxVal, numDiscretizations);
			count = 0;
			sum = 0d;
			this.minVal = minVal;
			this.maxVal = maxVal;
			minCheck = minVal-1e-2;
			maxCheck = maxVal+1e-2;
		}
		
		public void addValue(double sample) {
			Preconditions.checkState(sample >= minCheck && sample <= maxCheck,
					"sample=%s out of bounds [%s, %s]", sample, minVal, maxVal);
			addValueRaw(sample);
		}
		
		private void addValueRaw(double sample) {
			dist.add(dist.getClosestXIndex(sample), 1d);
			count++;
			sum += sample;
		}
	}
	
	private static void addInterpolateSamplesBetween(SampleDistributionTracker sampleDist, double[] samples0, double[] samples1,
			double[] subSampleFracts, double[] oneMinusFracts) {
		for (int i=0; i<samples0.length; i++) {
			// j=1 because we're just doing the samples between
			for (int j=1; j<subSampleFracts.length; j++) {
				// simple linear interpolation; I tried cubic and saw no improvement
				sampleDist.addValueRaw(oneMinusFracts[j]*samples0[i] + subSampleFracts[j]*samples1[i]);
			}
		}
	}

}
