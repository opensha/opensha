package org.opensha.sha.faultSurface.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.MagScalingRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.FiniteApproxPointSurface;
import org.opensha.sha.faultSurface.FrankelGriddedSurface;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.QuadSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy.CacheTypes;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

/**
 * Utility class for building rupture surfaces for point sources based on all available finite surface information.
 * <p>
 * This can be used to build truly finite sources (using the {@link QuadSurface} representation) if the strike
 * direction has been set (or is randomly sampled), otherwise it returns {@link FiniteApproxPointSurface} instances.
 */
public class PointSurfaceBuilder {
	
	// required/set on input
	private Location loc;
	private double zTop;
	private double zBot;
	
	// optional
	private Region sampleFromCell = null;
	private Random rand;
	private double mag = Double.NaN; 
	private double strike = Double.NaN;
	private Range<Double> strikeRange = null;
	private double dip = 90d;
	private double length = Double.NaN;
	private Boolean footwall = null;
	private double rake = Double.NaN; // only used for scaling relationships
	
	private double zHyp = Double.NaN;
	private double zHypFract = 0.5;
	private boolean zHypSample = false;
	private EvenlyDiscretizedFunc zHypCDF = null;
	private EvenlyDiscretizedFunc zHypFractCDF = null;
	
	private double das = Double.NaN;
	private double dasFract = 0.5;
	private boolean dasSample = false;
	private EvenlyDiscretizedFunc dasCDF = null;
	private EvenlyDiscretizedFunc dasFractCDF = null;
	
	private MagScalingRelationship scale = WC94;
	private double gridSpacing = 1d;
	
	// calculated on the fly
	private double width = Double.NaN;
	
	private static final WC1994_MagLengthRelationship WC94 = new WC1994_MagLengthRelationship();

	/**
	 * Initialized the point surface with the given location. The depth of the location is initially used as both 
	 * zTop and zBot (true point source).
	 * @param loc
	 */
	public PointSurfaceBuilder(Location loc) {
		this.loc = loc;
		zTop = loc.getDepth();
		zBot = loc.getDepth();
	}
	
	/**
	 * If cell is non-null, locations will be be sampled from within the given region rather than always a single
	 * fixed location
	 * @param cell
	 * @return
	 */
	public PointSurfaceBuilder sampleFromCell(Region cell) {
		this.sampleFromCell = cell;
		return this;
	}
	
	/**
	 * Sets the random number generator for any randomly-sampled operations. The default implementation uses a unique
	 * seed based on all information that was set at the time the first randomly-sampled operation was performed.
	 * @param rand
	 * @return
	 */
	public PointSurfaceBuilder random(Random rand) {
		this.rand = rand;
		return this;
	}
	
	/**
	 * Sets the random number generator for any randomly-sampled operations. The default implementation uses a unique
	 * seed based on all information that was set at the time the first randomly-sampled operation was performed.
	 * @param seed
	 * @return
	 */
	public PointSurfaceBuilder random(long seed) {
		this.rand = new Random(seed);
		return this;
	}
	
	/**
	 * Sets the random number generator for any randomly-sampled operations. A seed for this builder will be generated
	 * that is unique and repeatable for the given global seed as well as all information that has been set in this
	 * rupture builder (including the location and depths).
	 * @param globalSeed
	 * @return
	 */
	public PointSurfaceBuilder randomGlobalSeed(long globalSeed) {
		List<Long> seeds = getRandSeedElements();
		seeds.add(globalSeed);
		return random(new Random(uniqueSeedCombination(seeds)));
	}
	
	private List<Long> getRandSeedElements() {
		List<Long> seeds = new ArrayList<>();
		seeds.add(Double.doubleToLongBits(loc.lat));
		seeds.add(Double.doubleToLongBits(loc.lon));
		seeds.add(Double.doubleToLongBits(loc.depth));
		seeds.add(Double.doubleToLongBits(zTop));
		seeds.add(Double.doubleToLongBits(zBot));
		if (sampleFromCell != null)
			seeds.add((long)sampleFromCell.hashCode());
		if (Double.isFinite(mag))
			seeds.add(Double.doubleToLongBits(mag));
		if (Double.isFinite(strike))
			seeds.add(Double.doubleToLongBits(strike));
		if (strikeRange != null) {
			seeds.add(Double.doubleToLongBits(strikeRange.lowerEndpoint()));
			seeds.add(Double.doubleToLongBits(strikeRange.upperEndpoint()));
		}
		seeds.add(Double.doubleToLongBits(dip));
		if (Double.isFinite(length))
			seeds.add(Double.doubleToLongBits(length));
		if (footwall != null) {
			if (footwall)
				seeds.add(1l);
			else
				seeds.add(2l);
		}
		if (Double.isFinite(zHyp))
			seeds.add(Double.doubleToLongBits(zHyp));
		if (Double.isFinite(zHypFract))
			seeds.add(Double.doubleToLongBits(zHypFract));
		if (scale != WC94 && scale != null && scale.getName() != null)
			seeds.add((long)scale.getName().hashCode());
		if (Double.isFinite(gridSpacing))
			seeds.add(Double.doubleToLongBits(gridSpacing));
		return seeds;
	}
	
	private Random getRand() {
		if (rand == null) {
			List<Long> seeds = getRandSeedElements();
			rand = new Random(uniqueSeedCombination(seeds));
		}
		return rand;
	}
	
	/**
	 * Generates a 64-bit random seed that is a repeatable and psuedo-unique combination of the input seeds.
	 * 
	 * This is based on {@link Arrays#hashCode(int[])}, but modified for longs.
	 * 
	 * @param seeds
	 * @return
	 */
	private static long uniqueSeedCombination(List<Long> seeds) {
		Preconditions.checkState(!seeds.isEmpty());
		
		long result = 1;
		for (long element : seeds)
			result = 31l * result + element;
		return result;
	}
	
	private Location getLoc() {
		if (sampleFromCell != null) {
			double minLat = sampleFromCell.getMinLat();
			double maxLat = sampleFromCell.getMaxLat();
			double latSpan = maxLat - minLat;
			Preconditions.checkState(latSpan > 0d);
			double minLon = sampleFromCell.getMinLon();
			double maxLon = sampleFromCell.getMaxLon();
			double lonSpan = maxLon - minLon;
			Preconditions.checkState(lonSpan > 0d);
			Random rand = getRand();
			boolean rectangular = sampleFromCell.isRectangular();
			int maxNumTries = 100;
			int tries = 0;
			while (true) {
				double lat = minLat + rand.nextDouble()*latSpan;
				double lon = minLon + rand.nextDouble()*latSpan;
				Location randLoc = new Location(lat, lon, loc.depth);
				if (rectangular || sampleFromCell.contains(randLoc))
					return randLoc;
				// outside
				tries++;
				Preconditions.checkState(tries <= maxNumTries,
						"Couldn't randomly sample a location in the grid cell after %s tries", tries);
			}
		}
		return loc;
	}
	
	public PointSurfaceBuilder dip(double dip) {
		FaultUtils.assertValidDip(dip);
		this.dip = dip;
		this.width = Double.NaN;
		return this;
	}
	
	/**
	 * Set the depth (both upper and lower to the same) in km
	 * @param depth
	 * @return
	 */
	public PointSurfaceBuilder singleDepth(double depth) {
		FaultUtils.assertValidDepth(depth);
		this.zTop = depth;
		this.zBot = depth;
		this.width = Double.NaN;
		return this;
	}
	
	/**
	 * Set the upper depth in km
	 * @param zTop
	 * @return
	 */
	public PointSurfaceBuilder upperDepth(double zTop) {
		FaultUtils.assertValidDepth(zTop);
		this.zTop = zTop;
		this.width = Double.NaN;
		return this;
	}
	
	/**
	 * Set the lower depth in km
	 * @param zBot
	 * @return
	 */
	public PointSurfaceBuilder lowerDepth(double zBot) {
		FaultUtils.assertValidDepth(zBot);
		this.zBot = zBot;
		this.width = Double.NaN;
		return this;
	}
	
	/**
	 * Convenience method to set the upper depth, width, and dip. This calculates the lower depth automatically.
	 * @param zTop
	 * @param width
	 * @param dip
	 * @return
	 */
	public PointSurfaceBuilder upperDepthWidthAndDip(double zTop, double width, double dip) {
		FaultUtils.assertValidDepth(zTop);
		FaultUtils.assertValidDip(dip);
		Preconditions.checkState(width > 0d);
		
		double zBot;
		if (dip == 90d)
			zBot = zTop + width;
		else
			zBot = zTop + width * Math.sin(Math.toRadians(dip));
		FaultUtils.assertValidDepth(zBot);
		
		this.zTop = zTop;
		this.zBot = zBot;
		this.width = width;
		this.dip = dip;
		return this;
	}
	
	/**
	 * Sets the fractional hypocentral depth to this value, such that {@code zHyp = zTop + zHypFract*(zBot-zTop)}. Values of
	 * zero are trace-centered (the trace is coincident with the cell location), and values of 0.5 are surface-centered.
	 * 
	 * Setting this will clear any already-set fixed hypocentral depth value (see {@link #hypocentralDepth(double)} or
	 * distribution.
	 * @param zHyp
	 * @return
	 */
	public PointSurfaceBuilder fractionalHypocentralDepth(double zHypFract) {
		Preconditions.checkArgument(zHypFract >= 0d && zHypFract <= 1d, "zHypFract=%s is not within the range [0,1]", zHypFract);
		this.zHypFract = zHypFract;
		this.zHyp = Double.NaN;
		this.zHypSample = false;
		this.zHypCDF = null;
		this.zHypFractCDF = null;
		return this;
	}
	
	/**
	 * Sets the hypocentral depth to this value in km. If this value is outside the range of the upper and lower depths,
	 * an exception will be thrown when finite ruptures are built.
	 * 
	 * Setting this will clear any already-set fractional hypocentral depth value (see {@link #fractionalHypocentralDepth(double)}
	 * or distribution.
	 * @param zHyp
	 * @return
	 */
	public PointSurfaceBuilder hypocentralDepth(double zHyp) {
		FaultUtils.assertValidDepth(zHyp);
		this.zHyp = zHyp;
		this.zHypFract = Double.NaN;
		this.zHypSample = false;
		this.zHypCDF = null;
		this.zHypFractCDF = null;
		return this;
	}
	
	/**
	 * If set, hypocentral depths will be sampled from a uniform distribution between zTop and zBot.
	 * 
	 * Setting this will clear any already-set fractional or absolute hypocentral depth values or distributions.
	 * @return
	 */
	public PointSurfaceBuilder sampleHypocentralDepths() {
		this.zHypFract = Double.NaN;
		this.zHyp = Double.NaN;
		this.zHypSample = true;
		this.zHypCDF = null;
		this.zHypFractCDF = null;
		return this;
	}
	
	/**
	 * If set, hypocentral depths will be sampled from this distribution of fractional values.
	 * 
	 * Setting this will clear any already-set fractional or absolute hypocentral depth values or distributions.
	 * @param zHypFractDistribution distribution of fractional zHyp values; y values must sum to 1, and x values must
	 * not exceed the range [0,1]
	 * @return
	 */
	public PointSurfaceBuilder sampleFractionalHypocentralDepths(EvenlyDiscretizedFunc zHypFractDistribution) {
		Preconditions.checkNotNull(zHypFractDistribution, "Cannot set the distribution to null. Clear it by setting "
				+ "zHyp another way.");
		Preconditions.checkState((float)zHypFractDistribution.getMinX() >= 0f && (float)zHypFractDistribution.getMaxX() <= 1f,
				"Distribution sum of x values must be in the range [0,1]: [%s,%s]",
				(float)zHypFractDistribution.getMinX(), (float)zHypFractDistribution.getMaxX());
		this.zHypFract = Double.NaN;
		this.zHyp = Double.NaN;
		this.zHypSample = true;
		this.zHypCDF = null;
		this.zHypFractCDF = toCDF(zHypFractDistribution);
		return this;
	}
	
	/**
	 * If set, hypocentral depths will be sampled from this distribution. If this distribution contains values is
	 * outside the range of the upper and lower depths, an exception will be thrown when finite ruptures are built.
	 * 
	 * Setting this will clear any already-set fractional or absolute hypocentral depth values or distributions.
	 * @param zHypFractDistribution distribution of absolute zHyp values; y values must sum to 1.
	 * @return
	 */
	public PointSurfaceBuilder sampleHypocentralDepths(EvenlyDiscretizedFunc zHypDistribution) {
		Preconditions.checkNotNull(zHypDistribution, "Cannot set the distribution to null. Clear it by setting "
				+ "zHyp another way.");
		this.zHypFract = Double.NaN;
		this.zHyp = Double.NaN;
		this.zHypSample = true;
		this.zHypCDF = toCDF(zHypDistribution);
		this.zHypFractCDF = null;
		return this;
	}
	
	/**
	 * Sets the distance along strike to this value, such that {@code das = dasFract*length}. Values of 0.5 are surface-centered.
	 * 
	 * Setting this will clear any already-set fixed DAS value (see {@link #hypocentralDepth(double)} or
	 * distribution.
	 * @param zHyp
	 * @return
	 */
	public PointSurfaceBuilder fractionalDAS(double dasFract) {
		Preconditions.checkArgument(dasFract >= 0d && dasFract <= 1d, "dasFract=%s is not within the range [0,1]", dasFract);
		this.das = Double.NaN;
		this.dasFract = dasFract;
		this.dasSample = false;
		this.dasCDF = null;
		this.dasFractCDF = null;
		return this;
	}
	
	/**
	 * Sets the distance along strike to this value in km. If this value is outside the range of [0, length],
	 * an exception will be thrown when finite ruptures are built.
	 * 
	 * Setting this will clear any already-set fractional DAS value (see {@link #fractionalHypocentralDepth(double)}
	 * or distribution.
	 * @param zHyp
	 * @return
	 */
	public PointSurfaceBuilder das(double das) {
		Preconditions.checkState(das >= 0d);
		this.das = das;
		this.dasFract = Double.NaN;
		this.dasSample = false;
		this.dasCDF = null;
		this.dasFractCDF = null;
		return this;
	}
	
	/**
	 * If set, distances along strike will be sampled from a uniform distribution between 0 and length.
	 * 
	 * Setting this will clear any already-set fractional or absolute DAS values or distributions.
	 * @return
	 */
	public PointSurfaceBuilder sampleDASs() {
		this.dasFract = Double.NaN;
		this.das = Double.NaN;
		this.dasSample = true;
		this.dasCDF = null;
		this.dasFractCDF = null;
		return this;
	}
	
	/**
	 * If set, distances along strike will be sampled from this distribution of fractional values.
	 * 
	 * Setting this will clear any already-set fractional or absolute DAS values or distributions.
	 * @param dasFractDistribution distribution of fractional DAS values; y values must sum to 1, and x values must
	 * not exceed the range [0,1]
	 * @return
	 */
	public PointSurfaceBuilder sampleFractionalDASs(EvenlyDiscretizedFunc dasFractDistribution) {
		Preconditions.checkNotNull(dasFractDistribution, "Cannot set the distribution to null. Clear it by setting "
				+ "DAS another way.");
		Preconditions.checkState((float)dasFractDistribution.getMinX() >= 0f && (float)dasFractDistribution.getMaxX() <= 1f,
				"Distribution sum of x values must be in the range [0,1]: [%s,%s]",
				(float)dasFractDistribution.getMinX(), (float)dasFractDistribution.getMaxX());
		this.zHypFract = Double.NaN;
		this.zHyp = Double.NaN;
		this.zHypSample = true;
		this.zHypCDF = null;
		this.zHypFractCDF = toCDF(dasFractDistribution);
		return this;
	}
	
	/**
	 * If set, distances along strike will be sampled from this distribution. If this distribution contains values is
	 * outside the range of the [0, length], an exception will be thrown when finite ruptures are built.
	 * 
	 * Setting this will clear any already-set fractional or absolute DAS values or distributions.
	 * @param dasDistribution distribution of fractional DAS values; y values must sum to 1.
	 * @return
	 */
	public PointSurfaceBuilder sampleDASs(EvenlyDiscretizedFunc dasDistribution) {
		Preconditions.checkNotNull(dasDistribution, "Cannot set the distribution to null. Clear it by setting "
				+ "DAS another way.");
		this.dasFract = Double.NaN;
		this.das = Double.NaN;
		this.dasSample = true;
		this.dasCDF = toCDF(dasDistribution);
		this.dasFractCDF = null;
		return this;
	}
	
	private static EvenlyDiscretizedFunc toCDF(EvenlyDiscretizedFunc dist) {
		Preconditions.checkState((float)dist.calcSumOfY_Vals() == 1f, "Distribution sum of y values must "
				+ "sum to 1: %s", (float)dist.calcSumOfY_Vals());
		EvenlyDiscretizedFunc cdf = new EvenlyDiscretizedFunc(dist.getMinX(), dist.getMaxX(), dist.size());
		double sumY = 0d;
		for (int i=0; i<dist.size(); i++) {
			sumY += dist.getY(i);
			cdf.set(i, sumY);
		}
		return cdf;
	}
	
	private static double sampleFromCDF(EvenlyDiscretizedFunc cdf, double randDouble) {
		for (int i=0; i<cdf.size(); i++) {
			double cmlProb = cdf.getY(i);
			if ((float)randDouble <= (float)cmlProb)
				return cdf.getX(i);
		}
		throw new IllegalStateException("Couldn't sample from CDF with randDouble="+(float)randDouble+"\nCDF:\n"+cdf);
	}
	
	private double getAbsolutelValue(double lowerBound, double upperBound, double fractValue, double absValue,
			boolean sample, EvenlyDiscretizedFunc fractDist, EvenlyDiscretizedFunc absDist) {
		fractValue = getFractionalValue(lowerBound, upperBound, fractValue, absValue, sample, fractDist, absDist);
		return lowerBound + fractValue*(upperBound-lowerBound);
	}
	
	private double getFractionalValue(double lowerBound, double upperBound, double fractValue, double absValue,
			boolean sample, EvenlyDiscretizedFunc fractDist, EvenlyDiscretizedFunc absDist) {
		Preconditions.checkState(Double.isFinite(fractValue) || Double.isFinite(absValue) || sample,
				"Must specify exactly 1 of absolute, fractional, or sampled values");
		Preconditions.checkState(sample || (absDist == null && fractDist == null),
				"Samplign is disabled, but a distribution was provided");
		if (Double.isFinite(fractValue)) {
			Preconditions.checkState(!Double.isFinite(absValue) && !sample,
					"Must specify exactly 1 of absolute, fractional, or sampled values");
			return fractValue;
		}
		if (Double.isFinite(absValue)) {
			Preconditions.checkState(!Double.isFinite(fractValue) && !sample,
					"Must specify exactly 1 of absolute, fractional, or sampled values");
			Preconditions.checkState((float)absValue >= (float)lowerBound && (float)absValue <= (float)upperBound,
					"Supplied absolute value %s is not in the allowed range: [%s, %s]",
					(float)absValue, (float)lowerBound, (float)upperBound);
			return (absValue-lowerBound)/(upperBound-lowerBound);
		}
		Preconditions.checkState(sample);
		double randDouble = getRand().nextDouble();
		if (fractDist != null)
			return sampleFromCDF(fractDist, randDouble);
		if (absDist != null) {
			absValue = sampleFromCDF(absDist, randDouble);
			Preconditions.checkState((float)absValue >= (float)lowerBound && (float)absValue <= (float)upperBound,
					"Supplied absolute value %s is not in the allowed range: [%s, %s]",
					(float)absValue, (float)lowerBound, (float)upperBound);
			return (absValue-lowerBound)/(upperBound-lowerBound);
		}
		// uniform distribution
		return randDouble;
	}
	
	/**
	 * Sets the magnitude, used to infer the length if the length is not explicitly set
	 * @param mag
	 * @return
	 */
	public PointSurfaceBuilder magnitude(double mag) {
		this.mag = mag;
		return this;
	}
	
	/**
	 * Sets the magnitude scaling relationship used to infer lengths (if length is not explicitly set)
	 * @param scale
	 * @return
	 */
	public PointSurfaceBuilder scaling(MagScalingRelationship scale) {
		Preconditions.checkNotNull(scale);
		this.scale = scale;
		return this;
	}
	
	/**
	 * Set the length in km, or NaN to infer from magnitude and scaling
	 * @param length
	 * @return
	 */
	public PointSurfaceBuilder length(double length) {
		this.length = length;
		return this;
	}
	
	/**
	 * Sets the strike and dip from the given {@link FocalMechanism}. The rake will be stored and only used if
	 * properties are set from a scaling relationships that is rake-dependent
	 * @param mech
	 * @return
	 */
	public PointSurfaceBuilder mechanism(FocalMechanism mech) {
		strike(mech.getStrike());
		dip(mech.getDip());
		return this;
	}
	
	/**
	 * Draws a and sets a random strike; note that this same strike will be used until this method is called again,
	 * or the strike is otherwise set
	 * @return
	 */
	public PointSurfaceBuilder randomStrike() {
		strike(getRandStrikes(1, strikeRange)[0]);
		return this;
	}
	
	/**
	 * Sets the strike direction in decimal degrees, or NaN for no direction. This clears any previously set strike
	 * range.
	 * 
	 * @param strike
	 * @return
	 */
	public PointSurfaceBuilder strike(double strike) {
		this.strike = strike;
		this.strikeRange = null;
		return this;
	}
	
	/**
	 * Sets the strike direction range in decimal degrees, or null for no direction. Strikes will be sampled from a
	 * uniform distribution in this range. This clears any previously set fixed strike angle. 
	 * @param minStrike
	 * @param maxStrike
	 * @return
	 */
	public PointSurfaceBuilder strikeRange(double minStrike, double maxStrike) {
		Preconditions.checkState(maxStrike > minStrike, "maxStrike=%s must be greater than minStrike=%s", maxStrike, minStrike);
		return strikeRange(Range.closed(minStrike, maxStrike));
	}
	
	/**
	 * Sets the strike direction range in decimal degrees, or null for no direction. Strikes will be sampled from a
	 * uniform distribution in this range. This clears any previously set fixed strike angle.
	 * @param strikeRange
	 * @return
	 */
	public PointSurfaceBuilder strikeRange(Range<Double> strikeRange) {
		this.strikeRange = strikeRange;
		this.strike = Double.NaN;
		return this;
	}
	
	/**
	 * Sets the footwall parameter, used with point representations
	 * @param footwall
	 * @return
	 */
	public PointSurfaceBuilder footwall(boolean footwall) {
		this.footwall = footwall;
		return this;
	}
	
	/**
	 * Sets the grid spacing to be used when a gridded surface is built
	 * @param gridSpacing
	 * @return
	 */
	public PointSurfaceBuilder gridSpacing(double gridSpacing) {
		this.gridSpacing = gridSpacing;
		return this;
	}
	
	private double getCalcLength() {
		if (Double.isFinite(length))
			return length;
		if (Double.isFinite(mag)) {
			// calculate from scaling relationship
			if (Double.isFinite(rake)) {
				if (scale == WC94)
					// we have a custom rake, don't set it in the static WC94 instance
					scale = new WC1994_MagLengthRelationship();
				scale.setRake(rake);
			} else if (scale != WC94) {
				// custom scaling relationship, clear the rake param
				scale.setRake(rake);
			}
			if (scale instanceof MagLengthRelationship) {
				return ((MagLengthRelationship)scale).getMedianLength(mag);
			} else {
				Preconditions.checkState(scale instanceof MagAreaRelationship);
				double area = ((MagAreaRelationship)scale).getMedianArea(mag);
				double width = getCalcWidth();
				if (width > 0)
					return area/width;
				else
					// zero width, return zero
					return 0d;
			}
		} else {
			// can't calculate, set to zero
			return 0d;
		}
	}
	
	private double getCalcWidth() {
		Preconditions.checkState(zBot >= zTop, "zBOT must be >= zTOR");
		if (Double.isNaN(width)) {
			if (dip == 90d)
				width = zBot-zTop;
			else
				width = (zBot-zTop)/Math.sin(Math.toRadians(dip));
		}
		return width;
	}
	
	/**
	 * Builds a true point surface representation without any finite rupture parameters
	 * @return
	 */
	public PointSurface buildTruePointSurface() {
		return buildTruePointSurface(null);
	}
	
	/**
	 * Builds a true point surface representation without any finite rupture parameters
	 * @return
	 */
	public PointSurface buildTruePointSurface(PointSourceDistanceCorrection corr) {
		PointSurface surf;
		if (loc.depth == zTop)
			surf = new PointSurface(loc);
		else
			surf = new PointSurface(loc.lat, loc.lon, zTop);
		surf.setAveDip(dip);
		surf.setDistanceCorrection(corr, mag);
		return surf;
	}
	
	/**
	 * Builds true point surface representations without any finite rupture parameters and for the given
	 * {@link PointSourceDistanceCorrections}.
	 * @return
	 */
	public WeightedList<PointSurface> buildTruePointSurfaces(PointSourceDistanceCorrections distCorrType) {
		WeightedList<PointSourceDistanceCorrection> distCorrs;
		if (distCorrType == null || distCorrType == PointSourceDistanceCorrections.NONE)
			distCorrs = null;
		else
			distCorrs = distCorrType.get();
		return buildTruePointSurfaces(distCorrs);
	}
	
	/**
	 * Builds true point surface representations without any finite rupture parameters and for the given
	 * {@link WeightedList} of {@link PointSourceDistanceCorrection}s.
	 * @return
	 */
	public WeightedList<PointSurface> buildTruePointSurfaces(WeightedList<PointSourceDistanceCorrection> distCorrs) {
		if (distCorrs == null)
			return WeightedList.evenlyWeighted(buildTruePointSurface(null));
		else if (distCorrs.size() == 1)
			return WeightedList.evenlyWeighted(buildTruePointSurface(distCorrs.getValue(0)));
		WeightedList<PointSurface> ret = new WeightedList<>(distCorrs.size());
		for (int i=0; i<distCorrs.size(); i++) {
			PointSurface surf = buildTruePointSurface(distCorrs.getValue(i));
			ret.add(surf, distCorrs.getWeight(i));
		}
		return ret;
	}
	
	/**
	 * Builds a point surface representation without any {@link PointSourceDistanceCorrection} (rJB == rEpi, although
	 * you can set a {@link PointSourceDistanceCorrection} after bulding), and other distances are calculated using the
	 * rJB, the footwall setting, and zTop/zBot/dip.
	 * @return
	 */
	public FiniteApproxPointSurface buildFiniteApproxPointSurface() {
		return buildFiniteApproxPointSurface(null);
	}
	
	/**
	 * Builds a point surface representation where rJB is calculated according to the given {@link PointSourceDistanceCorrection},
	 * and other distances are calculated using the (possibly corrected) rJB, the footwall setting, and zTop/zBot/dip. 
	 * @return
	 */
	public FiniteApproxPointSurface buildFiniteApproxPointSurface(PointSourceDistanceCorrection corr) {
		Preconditions.checkState(footwall != null || dip == 90, "Footwall boolean must be specified if dip != 90");
		boolean footwall = this.footwall == null ? true : this.footwall;
		return buildFiniteApproxPointSurface(corr, footwall);
	}
	
	/**
	 * Builds a point surface representation where rJB is calculated according to the given {@link PointSourceDistanceCorrection},
	 * and other distances are calculated using the (possibly corrected) rJB, the footwall setting, and zTop/zBot/dip. 
	 * @return
	 */
	public FiniteApproxPointSurface buildFiniteApproxPointSurface(PointSourceDistanceCorrection corr, boolean footwall) {
		Preconditions.checkState(zBot >= zTop, "zBOT must be >= zTOR"); 
		
		double length = getCalcLength();
		
		FiniteApproxPointSurface surf = new FiniteApproxPointSurface(getLoc(), dip, zTop, zBot, footwall, length);
		surf.setDistanceCorrection(corr, mag);
		return surf;
	}
	
	/**
	 * Builds point surface representations where rJB is calculated according to the given {@link PointSourceDistanceCorrections},
	 * and other distances are calculated using the (possibly corrected) rJB, the footwall setting, and zTop/zBot/dip. 
	 * @return
	 */
	public WeightedList<FiniteApproxPointSurface> buildFiniteApproxPointSurfaces(
			PointSourceDistanceCorrections distCorrType) {
		WeightedList<PointSourceDistanceCorrection> distCorrs;
		if (distCorrType == null || distCorrType == PointSourceDistanceCorrections.NONE)
			distCorrs = null;
		else
			distCorrs = distCorrType.get();
		return buildFiniteApproxPointSurfaces(distCorrs);
	}
	
	/**
	 * Builds point surface representations where rJB is calculated according to the given {@link PointSourceDistanceCorrections},
	 * and other distances are calculated using the (possibly corrected) rJB, the footwall setting, and zTop/zBot/dip. 
	 * @return
	 */
	public WeightedList<FiniteApproxPointSurface> buildFiniteApproxPointSurfaces(
			WeightedList<PointSourceDistanceCorrection> distCorrs) {
		PointSourceDistanceCorrection singleCorr = distCorrs != null && distCorrs.size() == 1 ? distCorrs.getValue(0) : null;
		FiniteApproxPointSurface[] surfs;
		if (dip == 90d || footwall != null)
			surfs = new FiniteApproxPointSurface[] {
					buildFiniteApproxPointSurface(singleCorr, footwall == null ? true : footwall) };
		else
			surfs = new FiniteApproxPointSurface[] {
					buildFiniteApproxPointSurface(singleCorr, true),
					buildFiniteApproxPointSurface(singleCorr, false) };
		if (distCorrs != null && distCorrs.size() > 1) {
			// need multiple copies
			double weightEach = 1d/(double)surfs.length;
			WeightedList<FiniteApproxPointSurface> ret = new WeightedList<>(distCorrs.size()*surfs.length);
			for (FiniteApproxPointSurface surf : surfs) {
				for (int i=0; i<distCorrs.size(); i++) {
					FiniteApproxPointSurface surfCopy = surf.copyShallow();
					surfCopy.setDistanceCorrection(singleCorr, mag);
					ret.add(surfCopy, distCorrs.getWeight(i)*weightEach);
				}
			}
			return ret;
		} else {
			return WeightedList.evenlyWeighted(surfs);
		}
	}
	
	private FaultTrace buildTrace(double strike) {
		Preconditions.checkState(Double.isFinite(strike), "Can't build finite surface because strike=%s", strike);
		double length = getCalcLength();
		Preconditions.checkState(length > 0, "Can't build finite surface because length=%s; "
				+ "set magnitude to infer length from scaling relationship", length);
		double dasFract = getFractionalValue(0d, length, this.dasFract, das, dasSample, dasFractCDF, dasCDF);
		double strikeRad = Math.toRadians(strike);
		Location loc = getLoc();
		Location l0 = LocationUtils.location(loc, strikeRad-Math.PI, length*dasFract);
		Location l1 = LocationUtils.location(loc, strikeRad, length*(1d-dasFract));
		if (zBot > zTop && dip < 90) {
			// translate it for the given zHyp
			double horzFract = getFractionalValue(zTop, zBot, zHypFract, zHyp, zHypSample, zHypFractCDF, zHypCDF);
			double horzWidth = (zBot-zTop)/Math.tan(Math.toRadians(dip));
			// move to the left (so that it follows the RHR and dips to the right)
			double transAz = strikeRad - 0.5*Math.PI;
			l0 = LocationUtils.location(l0, transAz, horzFract*horzWidth);
			l1 = LocationUtils.location(l1, transAz, horzFract*horzWidth);
		}
		l0 = new Location(l0.lat, l0.lon, zTop);
		l1 = new Location(l1.lat, l1.lon, zTop);
		FaultTrace trace = new FaultTrace(null);
		trace.add(l0);
		trace.add(l1);
		return trace;
	}
	
	private double[] getRandStrikes(int num, Range<Double> strikeRange) {
		double[] strikes = new double[num];
		if (strikeRange == null) {
			// pick a random strike as the initial orientation, then evenly space relatively to that
			double origStrike = Double.isFinite(strike) ? strike : getRand().nextDouble()*360d;
			double delta = 360d/(double)num;
			for (int i=0; i<num; i++)
				strikes[i] = origStrike + i*delta;
		} else {
			// randomly sample within the given range
			double lower = strikeRange.lowerEndpoint();
			double upper = strikeRange.upperEndpoint();
			double span = upper - lower;
			Preconditions.checkState(span > 0d);
			Random rand = getRand();
			for (int i=0; i<num; i++)
				strikes[i] = lower + rand.nextDouble()*span;
		}
		return strikes;
	}
	
	/**
	 * Builds a {@link QuadSurface} representation of this point surface. The strike direction must be set. This
	 * representation is very efficient with distance calculations, regardless of fault size. Even for very small
	 * surfaces (e.g., M5), it still performs slightly better than a 1km gridded surface (and it is much faster for larger
	 * surfaces).
	 * @return
	 */
	public QuadSurface buildQuadSurface()  {
		return buildQuadSurface(strike);
	}
	
	/**
	 * Builds a {@link QuadSurface} representation of this point surface using the passed in strike direction. This
	 * representation is very efficient with distance calculations, regardless of fault size. Even for very small
	 * surfaces (e.g., M5), it still performs slightly better than a 1km gridded surface (and it is much faster for larger
	 * surfaces).
	 * @param strike
	 * @return
	 */
	public QuadSurface buildQuadSurface(double strike)  {
		FaultTrace trace = buildTrace(strike);
		
		// a single cache is appropriate for point sources, they're not reused by multiple sources
		// and not worth the memory and caching overhead of storing distances for multiple sites
		return new QuadSurface(trace, dip, getCalcWidth(), CacheTypes.DISABLED);
	}
	
	/**
	 * Builds the given number of random strike quad surfaces.
	 * 
	 * If a fixed strike angle has previously been set, then that strike angle will be used for the first surface
	 * and any additional surfaces will be evenly distributed.
	 * 
	 * If a strike range has been previously set then orientations will be randomly sampled within that range.
	 * 
	 * If neither a fixed strike nor a strike range has been set, then the initial orientation will be randomly sampled
	 * and any additional strikes will be evenly distributed.
	 * @param num
	 * @return
	 */
	public QuadSurface[] buildRandQuadSurfaces(int num) {
		return buildRandQuadSurfaces(num, strikeRange);
	}
	
	/**
	 * Builds the given number of random strike quad surfaces. If strikeRange is non null, orientations will be randomly
	 * sampled from the given range.
	 * @param num
	 * @param strikeRange
	 * @return
	 */
	public QuadSurface[] buildRandQuadSurfaces(int num, Range<Double> strikeRange) {
		QuadSurface[] ret = new QuadSurface[num];
		double[] strikes = getRandStrikes(num, strikeRange);
		for (int i=0; i<num; i++)
			ret[i] = buildQuadSurface(strikes[i]);
		return ret;
	}
	
	/**
	 * Builds a gridded surface representation with only one row (at the upper depth). Distance calculations will
	 * always performs worse (both in accuracy and speed) than {@link #buildQuadSurface()}, so use this only if you
	 * actually need a gridded line surface.
	 * @return
	 */
	public EvenlyGriddedSurface buildLineSurface() {
		return buildLineSurface(strike);
	}
	
	/**
	 * Builds a gridded surface representation with only one row (at the upper depth). Distance calculations will
	 * always performs worse (both in accuracy and speed) than {@link #buildQuadSurface()}, so use this only if you
	 * actually need a gridded line surface.
	 * @return
	 */
	public EvenlyGriddedSurface buildLineSurface(double strike) {
		FaultTrace trace = buildTrace(strike);
		
		return new FrankelGriddedSurface(trace, dip, zTop, zTop, gridSpacing);
	}
	
	/**
	 * Builds the given number of random strike gridded line surfaces.
	 * 
	 * If a fixed strike angle has previously been set, then that strike angle will be used for the first surface
	 * and any additional surfaces will be evenly distributed.
	 * 
	 * If a strike range has been previously set then orientations will be randomly sampled within that range.
	 * 
	 * If neither a fixed strike nor a strike range has been set, then the initial orientation will be randomly sampled
	 * and any additional strikes will be evenly distributed.
	 * @param num
	 * @return
	 */
	public EvenlyGriddedSurface[] buildRandLineSurfaces(int num) {
		return buildRandLineSurfaces(num, null);
	}
	
	/**
	 * Builds the given number of random strike gridded line surfaces. If strikeRange is non null, orientations will be randomly
	 * sampled from the given range.
	 * @param num
	 * @param strikeRange
	 * @return
	 */
	public EvenlyGriddedSurface[] buildRandLineSurfaces(int num, Range<Double> strikeRange) {
		EvenlyGriddedSurface[] ret = new EvenlyGriddedSurface[num];
		double[] strikes = getRandStrikes(num, strikeRange);
		for (int i=0; i<num; i++)
			ret[i] = buildLineSurface(strikes[i]);
		return ret;
	}
	
	/**
	 * Builds a gridded surface representation. Distance calculations will always performs worse (both in accuracy and
	 * speed) than {@link #buildQuadSurface()}, so use this only if you actually need a gridded surface.
	 * @return
	 */
	public EvenlyGriddedSurface buildGriddedSurface() {
		return buildGriddedSurface(strike);
	}
	
	/**
	 * Builds a gridded surface representation. Distance calculations will always performs worse (both in accuracy and speed) than
	 * {@link #buildQuadSurface()}, so use this only if you actually need a gridded surface.
	 * @return
	 */
	public EvenlyGriddedSurface buildGriddedSurface(double strike) {
		Preconditions.checkState(zBot >= zTop, "zBOT must be >= zTOR"); 
		FaultTrace trace = buildTrace(strike);
		
		return new FrankelGriddedSurface(trace, dip, zTop, zBot, gridSpacing);
	}
	
	/**
	 * Builds the given number of random strike gridded surfaces.
	 * 
	 * If a fixed strike angle has previously been set, then that strike angle will be used for the first surface
	 * and any additional surfaces will be evenly distributed.
	 * 
	 * If a strike range has been previously set then orientations will be randomly sampled within that range.
	 * 
	 * If neither a fixed strike nor a strike range has been set, then the initial orientation will be randomly sampled
	 * and any additional strikes will be evenly distributed.
	 * @param num
	 * @return
	 */
	public EvenlyGriddedSurface[] buildRandGriddedSurfaces(int num) {
		return buildRandGriddedSurfaces(num, null);
	}
	
	/**
	 * Builds the given number of random strike gridded surfaces. If strikeRange is non null, orientations will be randomly
	 * sampled from the given range.
	 * @param num
	 * @param strikeRange
	 * @return
	 */
	public EvenlyGriddedSurface[] buildRandGriddedSurfaces(int num, Range<Double> strikeRange) {
		EvenlyGriddedSurface[] ret = new EvenlyGriddedSurface[num];
		double[] strikes = getRandStrikes(num, strikeRange);
		for (int i=0; i<num; i++)
			ret[i] = buildGriddedSurface(strikes[i]);
		return ret;
	}
	
	/**
	 * Builds surfaces for the given {@link BackgroundRupType} and {@link PointSourceDistanceCorrections}.
	 * If a finite option has been chosen and the strike direction has not been set, then random a strike (or random
	 * strikes for crosshair) will be chosen.
	 * 
	 * <p><b>Special cases:</b>
	 * 
	 * <p>If the strike has been set and length>0, a single finite surface will be returned even if
	 * {@link BackgroundRupType#POINT} or {@link BackgroundRupType#CROSSHAIR} is chosen.
	 * 
	 * <p>If the length is zero, then a point source will be returned regardless of the {@link BackgroundRupType} setting.
	 * If <code>zTop ==  zBot && footwall == null</code>, then a true point source will be returned, otherwise an
	 * {@link FiniteApproxPointSurface} will be returned.
	 * @return
	 */
	public WeightedList<? extends RuptureSurface> build(BackgroundRupType bgRupType, PointSourceDistanceCorrections distCorrType) {
		// special cases
		if ((float)length == 0f) {
			// zero length; still return a finite aprox because we want to make sure to bypass any distance corrections,
			// which would be applied as rJB. It's not safe to only set distance corrections to NONE because they could
			// be attached downstream of here.
			distCorrType = PointSourceDistanceCorrections.NONE;
			// zero length but some other finite information, return FiniteApproxPointSurface
			return buildFiniteApproxPointSurfaces(distCorrType);
		} else if (Double.isFinite(strike) && (float)length > 0f) {
			// we have a finite surface
			return WeightedList.evenlyWeighted(buildQuadSurface());
		}
		switch (bgRupType) {
		case POINT:
			return buildFiniteApproxPointSurfaces(distCorrType);
		case FINITE:
			// this will use the given strike or strikeRange if previously supplied
			return WeightedList.evenlyWeighted(buildRandQuadSurfaces(1));
		case CROSSHAIR:
			// this will use the given strike or strikeRange if previously supplied
			return WeightedList.evenlyWeighted(buildRandQuadSurfaces(2));
		default:
			throw new IllegalStateException("Unsupported BackgroundRupType: "+bgRupType);
		}
	}
	
	public static void main(String[] args) {
		Location center = new Location(0d, 0d);
		PointSurfaceBuilder builder = new PointSurfaceBuilder(center);
		builder.magnitude(7.05d);
		builder.upperDepth(1d);
		builder.lowerDepth(14d);
		builder.dip(90d);
		builder.strike(0d);
		QuadSurface surf = builder.buildQuadSurface();
		
		System.out.println("Quad rJB at colocated point: "+surf.getDistanceJB(center));
		System.out.println("Trace:\t"+surf.getUpperEdge());
	}
}