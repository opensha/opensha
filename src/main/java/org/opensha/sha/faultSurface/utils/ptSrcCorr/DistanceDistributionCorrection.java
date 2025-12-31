package org.opensha.sha.faultSurface.utils.ptSrcCorr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.WeightedValue;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.interp.DistanceInterpolator;
import org.opensha.commons.util.interp.DistanceInterpolator.QuickInterpolator;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.cache.SurfaceDistances.Precomputed;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

public class DistanceDistributionCorrection implements PointSourceDistanceCorrection {
	
	// true means there's always a sample exactly pointing at the site
	// number of angle samples
	static final int NUM_ALPHA_SAMPLES = 360; // must be divisible by 4 for quadrant optimizations
	// if we're sampling along-strike, the number of said samples
	static final int NUM_SAMPLES_ALONG = 11;
	// if we're sampling down-dip, the number of said samples
	static final int NUM_SAMPLES_DOWN_DIP = 5;
	
	private final DistanceInterpolator interp = DistanceInterpolator.get();
	
	private final WeightedList<FractileBin> fractiles;
	private final boolean sampleAlong;
	private final boolean sampleDownDip;

	private final ConcurrentMap<RuptureKey, CachedFractileDistanceFuncs> valueFuncs = new ConcurrentHashMap<>();
	
	private boolean cache = true;
	
	private final double[] vertAlphaRad;
	private final double[] vertCosAlpha;
	private final double[] vertSinAlpha;
	
	private final double[] dippingAlphaRad;
	private final double[] dippingCosAlpha;
	private final double[] dippingSinAlpha;
	
	private final double[] samplesAlong;
	private final double[] samplesDownDip;
	
	public static DistanceDistributionCorrection getSingleAverage(boolean sampleAlong, boolean sampleDownDip) {
		return new DistanceDistributionCorrection(WeightedList.evenlyWeighted(new FractileBin(0d, 1d)), sampleAlong, sampleDownDip);
	}
	
	public static DistanceDistributionCorrection getEvenlyWeightedFractiles(int numFractiles, boolean sampleAlong, boolean sampleDownDip) {
		return new DistanceDistributionCorrection(getEvenlySpacedFractiles(numFractiles), sampleAlong, sampleDownDip);
	}
	
	static WeightedList<FractileBin> getEvenlySpacedFractiles(int numFractiles) {
		double[] edges = buildSpacedSamples(0d, 1d, numFractiles+1, true);
		List<FractileBin> fractiles = new ArrayList<>(numFractiles);
		for (int f=0; f<numFractiles; f++)
			fractiles.add(new FractileBin(edges[f], edges[f+1]));
		return WeightedList.evenlyWeighted(fractiles);
	}
	
	public static DistanceDistributionCorrection getImportanceSampledFractiles(
			double[] fractileBoundaries, boolean sampleAlong, boolean sampleDownDip) {
		Preconditions.checkState(fractileBoundaries.length > 2);
		Preconditions.checkState(fractileBoundaries[0] == 0d, "First boundary must start at 0");
		Preconditions.checkState(fractileBoundaries[fractileBoundaries.length-1] == 1d, "Last boundary must end at 1");
		int num = fractileBoundaries.length -1;
		List<WeightedValue<FractileBin>> fractileValues = new ArrayList<>(num);
		double weightSum = 0d;
		for (int i=0; i<num; i++) {
			double lower = fractileBoundaries[i];
			double upper = fractileBoundaries[i+1];
			double weight = upper - lower;
			weightSum += weight;
			Preconditions.checkState(weight > 0d);
			fractileValues.add(new WeightedValue<FractileBin>(new FractileBin(lower, upper), weight));
		}
		Preconditions.checkState(Precision.equals(weightSum, 1d, 0.001), "Weights don't sum to 1: %s", weightSum);
		return new DistanceDistributionCorrection(WeightedList.of(fractileValues), sampleAlong, sampleDownDip);
	}
	
	public DistanceDistributionCorrection(WeightedList<FractileBin> fractiles, boolean sampleAlong, boolean sampleDownDip) {
		Preconditions.checkArgument(!fractiles.isEmpty(), "Must supply at least one fractile");
		if (!(fractiles instanceof WeightedList.Unmodifiable<?>))
			fractiles = new WeightedList.Unmodifiable<>(fractiles);
		FractileBin prevFractile = null;
		for (int i=0; i<fractiles.size(); i++) {
			FractileBin fractile = fractiles.getValue(i);
			if (i == 0) {
				Preconditions.checkState(Precision.equals(fractile.minimum, 0d, 1e-06),
						"The first fractile bin must start at 0: %s", fractile.minimum);
			} else {
				Preconditions.checkState(Precision.equals(fractile.minimum, prevFractile.maximum, 1e-06),
						"Each fractile bin must start at the previous end: %s != %s", fractile.minimum, prevFractile.minimum);
			}
			prevFractile = fractile;
		}
		Preconditions.checkState(Precision.equals(prevFractile.maximum, 1d, 1e-06),
				"The last fractile bin must end at 1: %s", prevFractile.maximum);
		this.fractiles = fractiles;
		this.sampleAlong = sampleAlong;
		this.sampleDownDip = sampleDownDip;
		
		// determine how much of the sphere we need to sample
		// this depends on vertical or dipping, and if we're moving the epicenter location along strike (sampleAlong)
		// or down dip (sampleDownDip)
		
		// the fault is always pointed along the y axis (north)
		
		// for vertical faults, everything is symmetrical across the y axis
		if (sampleAlong) {
			// we need the full right half
			vertAlphaRad = buildSpacedSamples(0d, Math.PI, NUM_ALPHA_SAMPLES/2, false);
		} else {
			// we can get away with only the top right quadrant
			vertAlphaRad = buildSpacedSamples(0d, Math.PI/2d, NUM_ALPHA_SAMPLES/4, false);
		}
		vertCosAlpha = new double[vertAlphaRad.length];
		vertSinAlpha = new double[vertAlphaRad.length];
		for (int i = 0; i < vertAlphaRad.length; i++) {
			vertCosAlpha[i] = Math.cos(vertAlphaRad[i]);
			vertSinAlpha[i] = Math.sin(vertAlphaRad[i]);
		}
		
//		dippingAlphaRad = buildSpacedSamples(0d, 2*Math.PI, NUM_ALPHA_SAMPLES, false);
		if (sampleAlong || sampleDownDip) {
			// we need the full circle
			dippingAlphaRad = buildSpacedSamples(0d, 2*Math.PI, NUM_ALPHA_SAMPLES, false);
		} else {
			// we can do just the top half (symmetrical across x axis)
			dippingAlphaRad = buildSpacedSamples(-Math.PI/2d, Math.PI/2d, NUM_ALPHA_SAMPLES/2, false);
		}
		dippingCosAlpha = new double[dippingAlphaRad.length];
		dippingSinAlpha = new double[dippingAlphaRad.length];
		for (int i = 0; i < dippingAlphaRad.length; i++) {
			dippingCosAlpha[i] = Math.cos(dippingAlphaRad[i]);
			dippingSinAlpha[i] = Math.sin(dippingAlphaRad[i]);
		}
		
		if (sampleAlong)
			// only need to sample in one direction, otherwise would duplicate values due to symmetry
			samplesAlong = buildSpacedSamples(0d, 0.5d, NUM_SAMPLES_ALONG, false);
		else
			samplesAlong = new double[] {0.5};
		
		if (sampleDownDip)
			// needs to go in both directions because of the hanging wall term
			samplesDownDip = buildSpacedSamples(0d, 1d, NUM_SAMPLES_DOWN_DIP, false);
		else
			samplesDownDip = new double[] {0.5};
	}
	
	/**
	 * @return unmodifiable WeightedList of the fractiles used in this distribution 
	 */
	public WeightedList<FractileBin> getFractiles() {
		return fractiles;
	}

	@Override
	public WeightedList<SurfaceDistances> getCorrectedDistances(Location siteLoc, PointSurface surf,
			TectonicRegionType trt, double mag, double horzDist) {
		double length = surf.getAveLength();
		if (length == 0d || !Double.isFinite(length)) {
			// no length, so no distance correction
			double zTop = surf.getAveRupTopDepth();
			double rRup = hypot2(horzDist, zTop);
			double rX = horzDist == 0d ? 0 : -horzDist;
			Precomputed dists = new SurfaceDistances.Precomputed(siteLoc, rRup, horzDist, rX);
//			System.out.println("True pt src dists: "+dists);
			return WeightedList.evenlyWeighted(dists);
		}
		
		FractileDistances fractileDists;
		if (cache)
			fractileDists = getFractileDistances(surf, horzDist);
		else
			fractileDists = calcFractileDistances(new RuptureKey(surf), horzDist);
		
		boolean doFW = fractileDists.fractFootwall > 0d;
		boolean doHW = fractileDists.fractFootwall < 1d;
		List<WeightedValue<SurfaceDistances>> values = new ArrayList<>(doHW && doFW ? fractiles.size()*2 : fractiles.size());
		boolean[] hws;
		if (doFW && doHW)
			hws = new boolean[] {false, true};
		else if (doFW)
			hws = new boolean[] {false};
		else if (doHW)
			hws = new boolean[] {true};
		else
			throw new IllegalStateException("doFW and doHW both false? fract="+fractileDists.fractFootwall);
		for (boolean hw : hws) {
			double weight = hw ? 1d - fractileDists.fractFootwall : fractileDists.fractFootwall;
			PrecomputedComparableDistances[] dists = hw ? fractileDists.hangingWallDists : fractileDists.footwallDists;
			Preconditions.checkState(dists.length == fractiles.size());
			for (int f=0; f<dists.length; f++) {
				double fractWeight = fractiles.getWeight(f) * weight;
				values.add(new WeightedValue<>(dists[f], fractWeight));
			}
		}
		return WeightedList.of(values);
	}

	/**
	 * Same as {@code Math.hypot()} without regard to under/over flow.
	 */
	private static final double hypot2(double v1, double v2) {
		return Math.sqrt(v1 * v1 + v2 * v2);
	}
	
	public static class FractileBin {
		public final double minimum;
		public final double maximum;
		public final double center;
		
		public FractileBin(double minimum, double maximum) {
			Preconditions.checkState(minimum >= 0d, "Minimum must be in the range [0,1): %s", minimum);
			Preconditions.checkState(maximum <= 1d, "Maximum must be in the range (0,1]: %s", maximum);
			Preconditions.checkState(maximum > minimum, "Maximum must be greater than minimum: %s vs %s", maximum,
					minimum);
			this.minimum = minimum;
			this.maximum = maximum;
			this.center = 0.5*(minimum + maximum);
		}

		@Override
		public String toString() {
			return "FractileBin [" + minimum + ", " + maximum + "]";
		}
	}
	
	static WeightedList<Double> buildWeightedSpacedSamples(double min, double max, int num) {
		return buildWeightedSpacedSamples(min, max, num, false);
	}
	
	static WeightedList<Double> buildWeightedSpacedSamples(double min, double max, int num, boolean sampleEdges) {
		double[] fractiles = buildSpacedSamples(min, max, num, sampleEdges);
		List<WeightedValue<Double>> vals = new ArrayList<>(num);
		double weightEach = 1d/(double)num;
		for (int i=0; i<num; i++)
			vals.add(new WeightedValue<Double>(fractiles[i], weightEach));
		return WeightedList.of(vals);
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
	
	static class PrecomputedComparableDistances extends SurfaceDistances.Precomputed implements Comparable<PrecomputedComparableDistances> {
		
		private final double comparable;

		public PrecomputedComparableDistances(double distanceRup, double distanceJB,
				double distanceX) {
			super(null, distanceRup, distanceJB, distanceX);
			Preconditions.checkState(distanceJB >= 0 && Double.isFinite(distanceJB), "Bad rJB=%s", distanceJB);
			Preconditions.checkState(distanceRup >= 0 && Double.isFinite(distanceRup), "Bad rRup=%s", distanceRup);
			Preconditions.checkState(Double.isFinite(distanceX), "Bad rX=%s", distanceX);
			comparable = distanceJB + distanceRup;
		}

		@Override
		public int compareTo(PrecomputedComparableDistances o) {
			return Double.compare(comparable, o.comparable);
		}
		
	}
	
	static class RuptureKey implements Comparable<RuptureKey> {
		
		// used in hashing
		private final double rupLength, rupHorzWidth, dip, zTop;
		
		// otherwise available
		private final double zBot;
		
		public RuptureKey(PointSurface surf) {
			this(surf, true);
		}
		
		public RuptureKey(PointSurface surf, boolean snap) {
			double rupLength = surf.getAveLength();
			double rupHorzWidth = surf.getAveHorizontalWidth();
			double dip = surf.getAveDip();
			double zTop = surf.getAveRupTopDepth();
			zBot = surf.getAveRupBottomDepth();
			
			if (snap) {
				// snap values
				
//				this.rupLength = rupLength;
				if (rupLength < 0.1)
//					this.rupLength = 0.1;
					this.rupLength = roundValueToDiscr(rupLength, 0.01, 0.01);
				else if (rupLength < 1d)
					this.rupLength = roundValueToDiscr(rupLength, 0.05, 0.1);
				else if (rupLength < 10d)
					this.rupLength = roundValueToDiscr(rupLength, 0.1, 0.25);
				else if (rupLength < 50d)
					this.rupLength = roundValueToDiscr(rupLength, 0.5d, 0.5);
				else
					this.rupLength = roundValueToDiscr(rupLength, 1d, 1);
				
				if (rupHorzWidth < 1)
					this.rupHorzWidth = roundValueToDiscr(rupHorzWidth, 0.1d, 0d);
				else if (rupHorzWidth < 10)
					this.rupHorzWidth = roundValueToDiscr(rupHorzWidth, 0.2d, 1d);
				else if (rupHorzWidth < 20)
					this.rupHorzWidth = roundValueToDiscr(rupHorzWidth, 0.5d, 1d);
				else
					this.rupHorzWidth = roundValueToDiscr(rupHorzWidth, 1d, 1d);
				
				this.dip = roundValueToDiscr(dip, 5d, 5d);
				
				if (zTop < 1)
					this.zTop = roundValueToDiscr(zTop, 0.1d, 0);
				else if (zTop < 5)
					this.zTop = roundValueToDiscr(zTop, 0.2d, 0);
				else
					this.zTop = roundValueToDiscr(zTop, 0.5d, 0);
			} else {
				this.rupLength = rupLength;
				this.rupHorzWidth = rupHorzWidth;
				this.dip = dip;
				this.zTop = zTop;
			}
		}
		
		private static double roundValueToDiscr(double value, double discr, double min) {
			return Math.max(min, Math.round(value/discr)*discr);
		}

		@Override
		public int hashCode() {
			return Objects.hash(dip, rupLength, rupHorzWidth, zTop);
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
					&& Double.doubleToLongBits(rupHorzWidth) == Double.doubleToLongBits(other.rupHorzWidth)
					&& Double.doubleToLongBits(zTop) == Double.doubleToLongBits(other.zTop);
		}

		@Override
		public int compareTo(RuptureKey o) {
			// compare length first, then dip, then finally width and zTop
			int ret = Double.compare(rupLength, o.rupLength);
			if (ret != 0)
				return ret;
			ret = Double.compare(dip, o.dip);
			if (ret != 0)
				return ret;
			ret = Double.compare(rupHorzWidth, o.rupHorzWidth);
			if (ret != 0)
				return ret;
			return Double.compare(zTop, o.zTop);
		}

		@Override
		public String toString() {
			return "RuptureKey [rupLength=" + rupLength + ", rupHorzWidth=" + rupHorzWidth + ", dip=" + dip + ", zTop="
					+ zTop + ", zBot=" + zBot + "]";
		}
	}
	
	public static class FractileDistances {
		public final PrecomputedComparableDistances[] footwallDists;
		public final PrecomputedComparableDistances[] hangingWallDists;
		public final double fractFootwall;
		
		private FractileDistances(PrecomputedComparableDistances[] footwallDists,
				PrecomputedComparableDistances[] hangingWallDists, double fractFootwall) {
			Preconditions.checkState(Double.isFinite(fractFootwall) && fractFootwall >= 0d && fractFootwall <= 1d,
					"Bad fractFootwall=%s", fractFootwall);
			Preconditions.checkState(footwallDists != null || fractFootwall == 0d,
					"Footwall distances can only be null if fractFootwall=0, we have %s", fractFootwall);
			this.footwallDists = footwallDists;
			Preconditions.checkState(hangingWallDists != null || fractFootwall == 1d,
					"Hangingwall distances can only be null if fractFootwall=1, we have %s", fractFootwall);
			this.hangingWallDists = hangingWallDists;
			this.fractFootwall = fractFootwall;
		}
	}
	
	public FractileDistances getFractileDistances(PointSurface surf, double horzDist) {
		RuptureKey rupKey = new RuptureKey(surf);
		QuickInterpolator qi = interp.getQuickInterpolator(horzDist, false);
		if (qi.isDiscrete()) {
			// no interpolation required
			return getFractileDistances(rupKey, qi.getIndex1());
		}
		FractileDistances below = getFractileDistances(rupKey, qi.getIndex1());
		FractileDistances above = getFractileDistances(rupKey, qi.getIndex1()+1);
		
		PrecomputedComparableDistances[] hwDists = interpolateFractileDists(below.hangingWallDists, above.hangingWallDists, qi);
		PrecomputedComparableDistances[] fwDists = interpolateFractileDists(below.footwallDists, above.footwallDists, qi);
		double fractFW = qi.interpolate(below.fractFootwall, above.fractFootwall);
		return new FractileDistances(fwDists, hwDists, fractFW);
	}
	
	private static PrecomputedComparableDistances[] interpolateFractileDists(PrecomputedComparableDistances[] below,
			PrecomputedComparableDistances[] above, QuickInterpolator qi) {
		if (below == null && above == null)
			return null;
		else if (below == null)
			return above;
		else if (above == null)
			return below;
		PrecomputedComparableDistances[] ret = new PrecomputedComparableDistances[below.length];
		for (int f=0; f<ret.length; f++) {
			ret[f] = new PrecomputedComparableDistances(
					qi.interpolate(below[f].getDistanceRup(), above[f].getDistanceRup()),
					qi.interpolate(below[f].getDistanceJB(), above[f].getDistanceJB()),
					qi.interpolate(below[f].getDistanceX(), above[f].getDistanceX()));
		}
		return ret;
	}
	
	private static class CachedFractileDistanceFuncs {
		private final RuptureKey rupKey;
		
		private FractileDistances[] dists;
		
		public CachedFractileDistanceFuncs(RuptureKey rupKey, int numBins) {
			this.rupKey = rupKey;
			this.dists = new FractileDistances[numBins];
		}
		
		public FractileDistances get(int distBinIndex) {
			return dists[distBinIndex];
		}
		
		public void put(int distBinIndex, FractileDistances value) {
			dists[distBinIndex] = value;
		}
	}
	
	private FractileDistances getFractileDistances(RuptureKey rupKey, int distBinIndex) {
		// first see if cached
		CachedFractileDistanceFuncs cachedFuncs = valueFuncs.get(rupKey);
		
		if (cachedFuncs == null) {
			valueFuncs.putIfAbsent(rupKey, new CachedFractileDistanceFuncs(rupKey, interp.size()));
			cachedFuncs = valueFuncs.get(rupKey);
			Preconditions.checkNotNull(cachedFuncs);
		}
		
		FractileDistances ret = cachedFuncs.get(distBinIndex);
		if (ret != null)
			// already have it
			return ret;
		
		// need to calculate it
		ret = calcFractileDistances(rupKey, distBinIndex);
		
		// cache it
		cachedFuncs.put(distBinIndex, ret);
		
		return ret;
	}
	
	private FractileDistances calcFractileDistances(RuptureKey rupKey, int distBinIndex) {
		return calcFractileDistances(rupKey, interp.getDistance(distBinIndex));
	}
	
	private FractileDistances calcFractileDistances(RuptureKey rupKey, double rEpi) {
		boolean doHW = rupKey.dip < 90d;
		double[] cosAlpha, sinAlpha;
		if (doHW) {
			cosAlpha = dippingCosAlpha;
			sinAlpha = dippingSinAlpha;
		} else {
			cosAlpha = vertCosAlpha;
			sinAlpha = vertSinAlpha;
		}
		double[] siteX = new double[cosAlpha.length];
		double[] siteY = new double[cosAlpha.length];
		for (int i=0; i<cosAlpha.length; i++) {
			// alpha is relative to the y axis (up) here
			// for alpha = 0 (straight up the y axis):
			// 	siteX = r * cos(0) = 0
			// 	siteY = r * sin(0) = r
			// for alpha = 90 (straight right on the x axis):
			// 	siteX = r * cos(90) = r
			// 	siteY = r * sin(90) = 0
			siteX[i] = rEpi * sinAlpha[i];
			siteY[i] = rEpi * cosAlpha[i];
		}
		
		List<PrecomputedComparableDistances> fwDists = new ArrayList<>();
		List<PrecomputedComparableDistances> hwDists = doHW ? new ArrayList<>() : null;
		
		int numCalls = 1;
		if (sampleAlong)
			numCalls *= samplesAlong.length;
		if (sampleDownDip && doHW)
			numCalls *= samplesDownDip.length;
		
		if (numCalls == 1) {
			PrecomputedComparableDistances[] dists = new DistCalcSupplier(rupKey, 0.5, 0.5, siteX, siteY).get();
			for (PrecomputedComparableDistances dist : dists) {
				if (doHW && dist.getDistanceX() >= 0d)
					hwDists.add(dist);
				else
					fwDists.add(dist);
			}
		} else {
			// do them in parallel
			List<CompletableFuture<PrecomputedComparableDistances[]>> futures = new ArrayList<>(numCalls);
			
			for (double along : samplesAlong)
				for (double down : samplesDownDip)
					futures.add(CompletableFuture.supplyAsync(new DistCalcSupplier(rupKey, along, down, siteX, siteY)));
			
			for (CompletableFuture<PrecomputedComparableDistances[]> future : futures) {
				for (PrecomputedComparableDistances dist : future.join()) {
					if (doHW && dist.getDistanceX() >= 0d)
						hwDists.add(dist);
					else
						fwDists.add(dist);
				}
			}
		}
		
		double fractFW;
		
		PrecomputedComparableDistances[] footwallDists = calcBinAverageDistances(fwDists);
		PrecomputedComparableDistances[] hangingWallDists = null;
		if (hwDists != null && !hwDists.isEmpty()) {
			fractFW = (double)fwDists.size()/(double)(fwDists.size()+hwDists.size());
			hangingWallDists = calcBinAverageDistances(hwDists);
		} else {
			fractFW = 1d;
		}
		
//		System.out.println("FractFW="+(float)fractFW+" with "+(fwDists == null ? 0 : fwDists.size())+" fw and "
//				+(hwDists == null ? 0 : hwDists.size())+" hw");
		
		return new FractileDistances(footwallDists, hangingWallDists, fractFW);
	}
	
	private static boolean D = false;
	private static boolean D_ALL_AZ = false;
	private static boolean FIRST_D = true; // TODO: disable when ready
	
	static class DistCalcSupplier implements Supplier<PrecomputedComparableDistances[]> {

		private RuptureKey rupture;
		private double fractAlong;
		private double fractDownDip;
		private double[] siteX;
		private double[] siteY;

		public DistCalcSupplier(RuptureKey rupture, double fractAlong, double fractDownDip, double[] siteX, double[] siteY) {
			this.rupture = rupture;
			this.fractAlong = fractAlong;
			this.fractDownDip = fractDownDip;
			this.siteX = siteX;
			this.siteY = siteY;
		}

		@Override
		public PrecomputedComparableDistances[] get() {
			double dipRad = Math.toRadians(rupture.dip);
			
			// left side of the fault
			double x0 = -fractDownDip*rupture.rupHorzWidth;
			// right side of the fault
			double x1 = x0 + rupture.rupHorzWidth;
			
			// bottom side of the fault
			double y0 = -fractAlong*rupture.rupLength;
			// top side of the fault
			double y1 = y0 + rupture.rupLength;
			
			double zTopSq = rupture.zTop*rupture.zTop;
			
			boolean D = DistanceDistributionCorrection.D;
			if (!D && FIRST_D) {
				synchronized (DistanceDistributionCorrection.class) {
					D = FIRST_D;
					FIRST_D = false;
				}
			}
			
			boolean doHW = rupture.dip < 90d;
			LineSegment3D dipLineAtY0 = null;
			if (doHW)
				dipLineAtY0 = new LineSegment3D(x0, 0d, rupture.zTop, x1, 0d, rupture.zBot);
			
			PrecomputedComparableDistances[] ret = new PrecomputedComparableDistances[siteX.length];
			
			for (int i=0; i<siteX.length; i++) {
				boolean hw = doHW && siteX[i] >= x0;
				double rX = siteX[i] - x0;
				if (!doHW) {
					// vertical fault, force it to be footwall to short-circuit GMPE calculations
					rX = -Math.abs(rX);
				}
				
				boolean xInside;
				double xDist;
				if (hw) {
					if (siteX[i] > x1) {
						xDist = siteX[i] - x1;
						xInside = false;
					} else {
						xDist = 0d;
						xInside = true;
					}
				} else {
					xDist = Math.abs(rX);
					xInside = false;
				}
				
				boolean yInside;
				double yDist;
				if (siteY[i] >= y0 && siteY[i] <= y1) {
					yDist = 0d;
					yInside = true;
				} else if (siteY[i] > y1) {
					yDist = siteY[i] - y1;
					yInside = false;
				} else {
					yDist = y0 - siteY[i];
					yInside = false;
				}
				
				Preconditions.checkState(xDist >= 0, "bad xDist=%s", xDist);
				Preconditions.checkState(yDist >= 0, "bad yDist=%s", yDist);
				
				double rJB;
				if (xInside)
					rJB = yDist;
				else if (yInside)
					rJB = xDist;
				else
					rJB = Math.sqrt(xDist*xDist + yDist*yDist);
				
				if (D && (D_ALL_AZ || i==0)) {
					System.out.println("site["+i+"]=("+siteX[i]+", "+siteY[i]+"), rup="+rupture
							+ "\n\tx0="+x0+", y0="+y0+", x1="+x1+", y1="+y1
							+ "\n\txInside="+xInside+", xDist="+xDist+", yInside="+yInside+", yDist="+yDist+", rJB="+rJB);
				}
				
				double rRup;
				if (hw) {
					Preconditions.checkState(rX >= 0);
					rRup = distanceToLineSegment3D(siteX[i], yDist, dipLineAtY0);
					if (D && (D_ALL_AZ || i==0)) {
						System.out.println("HW rRup="+rRup+" for siteX="+siteX[i]+" and yDist="+yDist);
						System.out.println("\tdipLineAtY0=("+x0+", 0, "+rupture.zTop+") -> ("+x1+", 0, "+rupture.zBot+")");
					}
				} else {
					// simple
					Preconditions.checkState(!doHW || rX <= 0);
					double rJBsq = rJB*rJB;
					rRup = Math.sqrt(rJBsq + zTopSq);
				}
				ret[i] = new PrecomputedComparableDistances(rRup, rJB, rX);
			}
			return ret;
		}
		
	}
	
	public static double distanceToLineSegment3D(double px, double py, LineSegment3D line) {
		// Vector AP (Pz = 0)
		double apX = px - line.ax;
		double apY = py - line.ay;
		double apZ = -line.az;

		// Projection scalar
		double apDotAb = apX * line.abX + apY * line.abY + apZ * line.abZ;
		double t = apDotAb / line.abDotAb;
		t = Math.max(0, Math.min(1, t));

		// Closest point on segment
		double cx = line.ax + t * line.abX;
		double cy = line.ay + t * line.abY;
		double cz = line.az + t * line.abZ;

		// Distance from P to C
		double dx = px - cx;
		double dy = py - cy;
		double dz = -cz;

		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
	
	public static class LineSegment3D {
		public final double ax, ay, az;
		public final double abX, abY, abZ;
		public final double abDotAb;

		public LineSegment3D(double ax, double ay, double az, double bx, double by, double bz) {
			this.ax = ax;
			this.ay = ay;
			this.az = az;
			this.abX = bx - ax;
			this.abY = by - ay;
			this.abZ = bz - az;
			this.abDotAb = abX * abX + abY * abY + abZ * abZ;
		}
	}
	
	private static final double MAX_BIN_SIZE_FOR_INTERP = 5d;
	
	private PrecomputedComparableDistances[] calcBinAverageDistances(List<PrecomputedComparableDistances> distances) {
		return calcBinAverageDistances(distances, fractiles, MAX_BIN_SIZE_FOR_INTERP);
	}
	
	static PrecomputedComparableDistances[] calcBinAverageDistances(List<PrecomputedComparableDistances> distances,
			WeightedList<FractileBin> fractiles, double maxBinSizeForInterp) {
		if (distances.isEmpty())
			return null;
		int nDists = distances.size();
		if (nDists == 1) {
			// shortcut: only 1 value
			PrecomputedComparableDistances[] ret = new PrecomputedComparableDistances[fractiles.size()];
			for (int i=0; i<ret.length; i++)
				ret[i] = distances.get(0);
			return ret;
		}
		
		if (fractiles.size() == 1) {
			// average all of them
			double sumJB = 0d;
			double sumRup = 0d;
			double sumX = 0d;
			
			for (PrecomputedComparableDistances dist : distances) {
				sumJB += dist.getDistanceJB();
				sumRup += dist.getDistanceRup();
				sumX += dist.getDistanceX();
			}
			
			double scale = 1d/(double)distances.size();
			return new PrecomputedComparableDistances[] {
					new PrecomputedComparableDistances(sumRup*scale, sumJB*scale, sumX*scale)};
		}
		
		Collections.sort(distances);
		
		// inclusive
		double[] startIndexes = new double[fractiles.size()];
		// exclusive
		double[] endIndexes = new double[fractiles.size()];
		double smallestCount = Double.POSITIVE_INFINITY;
		for (int f=0; f<startIndexes.length; f++) {
			FractileBin fractile = fractiles.getValue(f);
			startIndexes[f] = fractile.minimum*nDists;
			endIndexes[f] = fractile.maximum*nDists;
			smallestCount = Math.min(smallestCount, endIndexes[f] - startIndexes[f]);
		}
		
		PrecomputedComparableDistances[] ret = new PrecomputedComparableDistances[fractiles.size()];
		
		if (smallestCount < maxBinSizeForInterp) {
			// we don't have a lot of them (possibly less than 1 per bin!), interpolate

			double[] sumJBs = new double[ret.length];
			double[] sumRups = new double[ret.length];
			double[] sumXs = new double[ret.length];
			double[] sumWeights = new double[ret.length];

			for (int n = 0; n < nDists; n++) {
				// interval of this sample in *index* space: [n , n+1)
				double segStart = n;
				double segEnd   = n + 1;

				PrecomputedComparableDistances d = distances.get(n);

				// test against (at most) two neighboring bins – but the
				// generic loop is still O(n + bins) and clearer
				for (int f = 0; f < ret.length; f++) {
					double overlap =
							Math.min(segEnd,   endIndexes[f]) -
							Math.max(segStart, startIndexes[f]);

					if (overlap <= 0.0)     // no intersection
						continue;

					double w = overlap;     // fractional weight (0‥1)

					sumWeights[f] += w;
					sumJBs[f]   += w * d.getDistanceJB();
					sumRups[f]  += w * d.getDistanceRup();
					sumXs[f]    += w * d.getDistanceX();
				}
			}

			// convert weighted sums to means, guard against empty bins
			for (int f = 0; f < ret.length; f++) {
				Preconditions.checkState(sumWeights[f] > 0d, "No weight for bin %s", f);
				double invW = 1d / sumWeights[f];
				ret[f] = new PrecomputedComparableDistances(
						invW * sumRups[f],
						invW * sumJBs[f],
						invW * sumXs[f]);
			}
		} else {
			// we have many, don't bother interpolating
			for (int f=0; f<ret.length; f++) {
				int startIndex = (int)startIndexes[f];
				if (f == 0)
					Preconditions.checkState(startIndex == 0);
				int endIndex = (int)Math.ceil(endIndexes[f]);
				Preconditions.checkState(endIndex >= startIndex,
						"bad endIndex=%s (fract=%s) for startIndex=%s (fract=%s), nDists=%s",
						endIndex, endIndexes[f], startIndex, startIndexes[f], nDists);
				if (f == ret.length-1)
					Preconditions.checkState(endIndex == nDists);
				double weightEach = 1d/(double)(endIndex - startIndex);
				double sumJB = 0d;
				double sumRup = 0d;
				double sumX = 0d;
				for (int i=startIndex; i<endIndex; i++) {
					PrecomputedComparableDistances dists = distances.get(i);
					sumJB += dists.getDistanceJB();
					sumRup += dists.getDistanceRup();
					sumX += dists.getDistanceX();
				}
				ret[f] = new PrecomputedComparableDistances(weightEach*sumRup, weightEach*sumJB, weightEach*sumX);
			}
		}
		
		return ret;
	}

}
