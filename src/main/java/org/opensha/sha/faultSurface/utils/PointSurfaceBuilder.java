package org.opensha.sha.faultSurface.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import org.opensha.commons.calc.magScalingRelations.MagAreaRelationship;
import org.opensha.commons.calc.magScalingRelations.MagLengthRelationship;
import org.opensha.commons.calc.magScalingRelations.MagScalingRelationship;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.WeightedValue;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.util.GriddedFiniteRuptureSettings;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.FiniteApproxPointSurface;
import org.opensha.sha.faultSurface.FrankelGriddedSurface;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.QuadSurface;
import org.opensha.sha.faultSurface.RectangularSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy.CacheTypes;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;

/**
 * Utility class for building rupture surfaces for point sources based on all available finite surface information.
 * <p>
 * This can be used to build truly finite sources (using the {@link RectangularSurface} representation) if the strike
 * direction has been set (or is randomly sampled), otherwise it returns {@link FiniteApproxPointSurface} instances.
 * <p>
 * Random seeds are a deterministic function of all input variables, meaning that the same inputs will result in the
 * same random outputs. This is to make hazard calculations reproducible. If you want truly random seeds, call the
 * {@link #random(long)} or {@link #random(Random)} methods. You can also set a custom global random seed in order
 * to get a new (but still deterministic for that global seed) random via {@link #randomGlobalSeed(long)}.
 * <p>
 * You can also control randomness with these java properties:
 * <ul>
 *   <li><b>point.surface.true.random</b> – if set to "true" or "1", enables truly random sampling</li>
 *   <li><b>point.surface.global.seed</b> – if set, uses the given long value as a global seed</li>
 * </ul>
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
	private double horzWidth = Double.NaN;

	public static final String TRUE_RANDOM_PROP_NAME = "point.surface.true.random";
	public static final String GLOBAL_SEED_PROP_NAME = "point.surface.global.seed";
	
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
	
	private static boolean useTrueRandom() {
		String prop = System.getProperty(TRUE_RANDOM_PROP_NAME);
		if (prop == null)
			return false; // default behavior if unset
		prop = prop.trim().toLowerCase();
		switch (prop) {
		case "true":
			return true;
		case "1":
			return true;
		case "false":
			return false;
		case "0":
			return false;
		default:
			throw new IllegalArgumentException("Invalid value for '"+TRUE_RANDOM_PROP_NAME+"': " + prop
					+ ". Expected 'true', 'false', '1', or '0'.");
		}
	}
	
	private static Long getGlobalSeed() {
		String prop = System.getProperty(GLOBAL_SEED_PROP_NAME);
		if (prop == null)
			return null;
		prop = prop.trim();
		try {
			return Long.parseLong(prop);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid value for '"+GLOBAL_SEED_PROP_NAME+"': '" + prop +
					"'. Must be a valid long integer.", e);
		}
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
			if (useTrueRandom()) {
				rand = new Random();
			} else {
				List<Long> seeds = getRandSeedElements();
				Long globalSeed = getGlobalSeed();
				if (globalSeed != null)
					seeds.add(globalSeed);
				rand = new Random(uniqueSeedCombination(seeds));
			}
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
				double lon = minLon + rand.nextDouble()*lonSpan;
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
		this.horzWidth = Double.NaN;
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
		this.horzWidth = Double.NaN;
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
		this.horzWidth = Double.NaN;
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
		this.horzWidth = Double.NaN;
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
		this.horzWidth = Double.NaN;
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
		this.dasFract = Double.NaN;
		this.das = Double.NaN;
		this.dasSample = true;
		this.dasCDF = null;
		this.dasFractCDF = toCDF(dasFractDistribution);
		return this;
	}
	
	/**
	 * If set, distances along strike will be sampled from this distribution. If this distribution contains values is
	 * outside the range of the [0, length], an exception will be thrown when finite ruptures are built.
	 * 
	 * Setting this will clear any already-set fractional or absolute DAS values or distributions.
	 * @param dasDistribution distribution of DAS values; y values must sum to 1.
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
				"Sampling is disabled, but a distribution was provided");
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
	
	private double getCalcHorzWidth() {
		Preconditions.checkState(zBot >= zTop, "zBOT must be >= zTOR");
		if (Double.isNaN(horzWidth)) {
			if (dip == 90d || zBot == zTop)
				horzWidth = 0d;
			else
				horzWidth = (zBot-zTop)/Math.tan(Math.toRadians(dip));
		}
		return horzWidth;
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
		WeightedList<? extends PointSourceDistanceCorrection> distCorrs;
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
	public WeightedList<PointSurface> buildTruePointSurfaces(WeightedList<? extends PointSourceDistanceCorrection> distCorrs) {
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
		return supplyFiniteApproxPointSurface(corr, footwall).get();
	}
	
	/**
	 * Supplies a point surface representation where rJB is calculated according to the given {@link PointSourceDistanceCorrection},
	 * and other distances are calculated using the (possibly corrected) rJB, the footwall setting, and zTop/zBot/dip. 
	 * @return
	 */
	public RuptureSurfaceSupplier<FiniteApproxPointSurface> supplyFiniteApproxPointSurface(PointSourceDistanceCorrection corr, boolean footwall) {
		final double zBot = this.zBot;
		final double zTop = this.zTop;
		final double dip = this.dip;
		final Location loc = this.loc;
		final double mag = this.mag;
		Preconditions.checkState(zBot >= zTop, "zBOT must be >= zTOR"); 
		final double length = getCalcLength();
		
		return new RuptureSurfaceSupplier<FiniteApproxPointSurface>() {

			@Override
			public FiniteApproxPointSurface get() {
				FiniteApproxPointSurface surf = new FiniteApproxPointSurface(loc, dip, zTop, zBot, footwall, length);
				surf.setDistanceCorrection(corr, mag);
				return surf;
			}

			@Override
			public boolean isFinite() {
				// finite here means actually finite, not distance corrected
				return false;
			}
		};
	}
	
	/**
	 * Builds point surface representations where rJB is calculated according to the given {@link PointSourceDistanceCorrections},
	 * and other distances are calculated using the (possibly corrected) rJB, the footwall setting, and zTop/zBot/dip. 
	 * @return
	 */
	public WeightedList<FiniteApproxPointSurface> buildFiniteApproxPointSurfaces(
			PointSourceDistanceCorrections distCorrType) {
		WeightedList<? extends PointSourceDistanceCorrection> distCorrs;
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
			WeightedList<? extends PointSourceDistanceCorrection> distCorrs) {
		return buildConcreteWeightedList(supplyFiniteApproxPointSurfaces(distCorrs));
	}
	
	public WeightedList<RuptureSurfaceSupplier<FiniteApproxPointSurface>> supplyFiniteApproxPointSurfaces(
			PointSourceDistanceCorrections distCorrType) {
		WeightedList<? extends PointSourceDistanceCorrection> distCorrs;
		if (distCorrType == null || distCorrType == PointSourceDistanceCorrections.NONE)
			distCorrs = null;
		else
			distCorrs = distCorrType.get();
		return supplyFiniteApproxPointSurfaces(distCorrs);
	}
	
	public WeightedList<RuptureSurfaceSupplier<FiniteApproxPointSurface>> supplyFiniteApproxPointSurfaces(
			WeightedList<? extends PointSourceDistanceCorrection> distCorrs) {
		PointSourceDistanceCorrection singleCorr = distCorrs != null && distCorrs.size() == 1 ? distCorrs.getValue(0) : null;
		WeightedList<RuptureSurfaceSupplier<FiniteApproxPointSurface>> surfCalls;
		if (dip == 90d || footwall != null) {
			surfCalls = WeightedList.evenlyWeighted(supplyFiniteApproxPointSurface(singleCorr, footwall == null ? true : footwall));
		} else {
			surfCalls = WeightedList.evenlyWeighted(supplyFiniteApproxPointSurface(singleCorr, true),
					supplyFiniteApproxPointSurface(singleCorr, false));
		}
//		} else { // TODO disabled for now, pending further PointSourceDistanceCorrection refactor. see note at org.opensha.sha.faultSurface.FiniteApproxPointSurface.getCorrDistRup
//			// dipping, include versions with and without the hanging wall term enabled
//			// hanging wall only matters close in for NGA-W2 GMMs (<30 km), and at those distances, it's not actually
//			// an even weighting. determine the fraction of azimuths expected to be on the hanging wall at a reference
//			// distance, rJBref.
////			double rJBref = 15; // halfway through the taper
//			double rJBref = 1;
//			/* 
//			 * schematic:
//			 * 
//			 * A is rJBref away from the fault surface
//			 * and theta is the angle to A from the '!' line
//			 *  
//			 * A-----!
//			 *       !
//			 *       !
//			 *   _________.
//			 * ||    !    |
//			 * ||    !    |
//			 * ||    !    |
//			 * ||    !    |
//			 * ||    *    |
//			 * ||         |
//			 * ||         |
//			 * ||         |
//			 * ||_________|
//			 */
//			double theta = Math.atan((0.5*getCalcHorzWidth())/(0.5*getCalcLength() + rJBref));
//			// the fraction on the hanging wall will be PI + 2*theta
//			// we'll simplify for a half circle because it's symmetrical
//			double weightHW = (Math.PI*0.5 + theta)/Math.PI;
//			System.out.println("len="+(float)length+" horzWidth="+(float)horzWidth
//					+", theta="+(float)Math.toDegrees(theta)+", weightHW="+(float)weightHW);
//			List<WeightedValue<RuptureSurfaceSupplier<FiniteApproxPointSurface>>> values = List.of(
//					new WeightedValue<>(supplyFiniteApproxPointSurface(singleCorr, true), 1d - weightHW), // true means footwall
//					new WeightedValue<>(supplyFiniteApproxPointSurface(singleCorr, false), weightHW)); // false means hanging wall
//			surfCalls = new WeightedList.Unmodifiable<>(values, false);
//		}
		if (distCorrs != null && distCorrs.size() > 1) {
			// need multiple copies
			WeightedList<RuptureSurfaceSupplier<FiniteApproxPointSurface>> ret = new WeightedList<>(distCorrs.size()*surfCalls.size());
			for (WeightedValue<RuptureSurfaceSupplier<FiniteApproxPointSurface>> weightedSurfCall : surfCalls) {
				// make it lazy init so that the top level surface won't be regenerated
				LazyRuptureSurfaceSupplier<FiniteApproxPointSurface> lazyCall = new LazyRuptureSurfaceSupplier<>(weightedSurfCall.value);
				for (int i=0; i<distCorrs.size(); i++) {
					PointSourceDistanceCorrection corr = distCorrs.getValue(i);
					ret.add(new RuptureSurfaceSupplier<FiniteApproxPointSurface>() {

						@Override
						public FiniteApproxPointSurface get() {
							FiniteApproxPointSurface surfCopy = lazyCall.get().copyShallow();
							surfCopy.setDistanceCorrection(corr, mag);
							return surfCopy;
						}

						@Override
						public boolean isFinite() {
							// finite here means actually finite, not distance corrected
							return false;
						}
					}, distCorrs.getWeight(i)*weightedSurfCall.weight);
				}
			}
			return ret;
		} else {
			return surfCalls;
		}
	}
	
	private FaultTrace buildTrace(double strike) {
		double length = getCalcLength();
		double dasFract = getFractionalValue(0d, length, this.dasFract, das, dasSample, dasFractCDF, dasCDF);
		double horzWidth = getCalcHorzWidth();
		double horzFract = horzWidth == 0d ?
				Double.NaN : getFractionalValue(zTop, zBot, zHypFract, zHyp, zHypSample, zHypFractCDF, zHypCDF);
		
		return buildTrace(getLoc(), strike, length, horzWidth, zTop, dasFract, horzFract);
	}
	
	private static FaultTrace buildTrace(Location loc, double strike, double length, double horzWidth,
			double zTop, double dasFract, double horzFract) {
		Preconditions.checkState(Double.isFinite(strike), "Can't build finite surface because strike=%s", strike);
		Preconditions.checkState(length > 0, "Can't build finite surface because length=%s; "
				+ "set magnitude to infer length from scaling relationship", length);
		double strikeRad = Math.toRadians(strike);
		Location l0 = LocationUtils.location(loc, strikeRad-Math.PI, length*dasFract);
		Location l1 = LocationUtils.location(loc, strikeRad, length*(1d-dasFract));
		if (horzWidth > 0d) {
			// translate it for the given zHyp
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
		if (strikeRange == null) {
			// pick a random strike as the initial orientation, then evenly space relatively to that
			double origStrike = Double.isFinite(strike) ? strike : getRand().nextDouble()*360d;
			return getEvenlySpanningStrikes(num, origStrike);
		}
		double[] strikes = new double[num];
		// randomly sample within the given range
		double lower = strikeRange.lowerEndpoint();
		double upper = strikeRange.upperEndpoint();
		double span = upper - lower;
		Preconditions.checkState(span > 0d);
		Random rand = getRand();
		for (int i=0; i<num; i++)
			strikes[i] = lower + rand.nextDouble()*span;
		return strikes;
	}
	
	private static double[] getEvenlySpanningStrikes(int num, double origStrike) {
		double[] strikes = new double[num];
		double delta = 360d/(double)num;
		for (int i=0; i<num; i++)
			strikes[i] = origStrike + i*delta;
		return strikes;
	}
	
	/**
	 * Builds a {@link QuadSurface} representation of this point surface. The strike direction must be set. This
	 * representation is decently efficient, but a {@link RectangularSurface} is better.
	 * @return
	 */
	public QuadSurface buildQuadSurface()  {
		return buildQuadSurface(strike);
	}
	
	/**
	 * Builds a {@link QuadSurface} representation of this point surface using the passed in strike direction. This
	 * representation is decently efficient, but a {@link RectangularSurface} is better.
	 * @param strike
	 * @return
	 */
	public QuadSurface buildQuadSurface(double strike)  {
		FaultTrace trace = buildTrace(strike);
		return new QuadSurface(trace, dip, getCalcWidth(), CacheTypes.SINGLE);
	}
	
	/**
	 * Builds a {@link RectangularSurface} representation of this point surface. The strike direction must be set. This
	 * representation is very efficient with distance calculations, regardless of fault size. Even for very small
	 * surfaces (e.g., M5), it still performs slightly better than a 1km gridded surface (and it is much faster for larger
	 * surfaces).
	 * @return
	 */
	public RectangularSurface buildRectSurface()  {
		return buildRectSurface(strike);
	}
	
	/**
	 * Builds a {@link RectangularSurface} representation of this point surface using the passed in strike direction. This
	 * representation is very efficient with distance calculations, regardless of fault size. Even for very small
	 * surfaces (e.g., M5), it still performs slightly better than a 1km gridded surface (and it is much faster for larger
	 * surfaces).
	 * @param strike
	 * @return
	 */
	public RectangularSurface buildRectSurface(double strike)  {
		FaultTrace trace = buildTrace(strike);
		return new RectangularSurface(trace.first(), trace.last(), dip, zBot);
	}
	
	/**
	 * Supplies a {@link RectangularSurface} representation of this point surface using the passed in strike direction. This
	 * representation is very efficient with distance calculations, regardless of fault size. Even for very small
	 * surfaces (e.g., M5), it still performs slightly better than a 1km gridded surface (and it is much faster for larger
	 * surfaces).
	 * @param strike
	 * @return
	 */
	public RuptureSurfaceSupplier<RectangularSurface> supplyRectSurface(double strike)  {
		double length = getCalcLength();
		double dasFract = getFractionalValue(0d, length, this.dasFract, das, dasSample, dasFractCDF, dasCDF);
		double horzWidth = getCalcHorzWidth();
		double horzFract = horzWidth == 0d ?
				Double.NaN : getFractionalValue(zTop, zBot, zHypFract, zHyp, zHypSample, zHypFractCDF, zHypCDF);
		double zTop = this.zTop;
		double dip = this.dip;
		double zBot = this.zBot;
		return new RuptureSurfaceSupplier<RectangularSurface>() {
			
			@Override
			public RectangularSurface get() {
				FaultTrace trace = buildTrace(getLoc(), strike, length, horzWidth, zTop, dasFract, horzFract);
				return new RectangularSurface(trace.first(), trace.last(), dip, zBot);
			}

			@Override
			public boolean isFinite() {
				return true;
			}
		};
	}
	
	/**
	 * Builds the given number of random strike rectangular surfaces.
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
	public RectangularSurface[] buildRandRectSurfaces(int num) {
		if (rand == null)
			randomGlobalSeed(num);
		return buildRandRectSurfaces(num, strikeRange);
	}
	
	/**
	 * Builds the given number of random strike rectangular surfaces. If strikeRange is non null, orientations will be randomly
	 * sampled from the given range.
	 * @param num
	 * @param strikeRange
	 * @return
	 */
	public RectangularSurface[] buildRandRectSurfaces(int num, Range<Double> strikeRange) {
		return buildRandRectSurfaces(getRandStrikes(num, strikeRange));
	}
	
	private RectangularSurface[] buildRandRectSurfaces(double[] strikes) {
		RectangularSurface[] ret = new RectangularSurface[strikes.length];
		for (int i=0; i<strikes.length; i++)
			ret[i] = buildRectSurface(strikes[i]);
		return ret;
	}
	
	private List<RuptureSurfaceSupplier<RectangularSurface>> supplyRandRectSurfaces(double[] strikes) {
		switch (strikes.length) {
		// more memory-efficient common special cases
		case 0:
			return List.of();
		case 1:
			return List.of(supplyRectSurface(strikes[0]));
		case 2:
			return List.of(supplyRectSurface(strikes[0]),
					supplyRectSurface(strikes[1]));

		// default case when size > 2
		default:
			List<RuptureSurfaceSupplier<RectangularSurface>> list = new ArrayList<>(strikes.length);
			for (double strike : strikes)
				list.add(supplyRectSurface(strike));
			return list;
		}
	}
	
	/**
	 * Builds a gridded surface representation with only one row (at the upper depth). Distance calculations will
	 * always performs worse (both in accuracy and speed) than {@link #buildRectSurface()}, so use this only if you
	 * actually need a gridded line surface.
	 * @return
	 */
	public EvenlyGriddedSurface buildLineSurface() {
		return buildLineSurface(strike);
	}
	
	/**
	 * Builds a gridded surface representation with only one row (at the upper depth). Distance calculations will
	 * always performs worse (both in accuracy and speed) than {@link #buildRectSurface()}, so use this only if you
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
	 * speed) than {@link #buildRectSurface()}, so use this only if you actually need a gridded surface.
	 * @return
	 */
	public EvenlyGriddedSurface buildGriddedSurface() {
		return buildGriddedSurface(strike);
	}
	
	/**
	 * Builds a gridded surface representation. Distance calculations will always performs worse (both in accuracy and speed) than
	 * {@link #buildRectSurface()}, so use this only if you actually need a gridded surface.
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
	 * <p>If the length is zero, then a {@link FiniteApproxPointSurface} source will be returned regardless of the
	 * {@link BackgroundRupType} setting.
	 * @return
	 */
	public WeightedList<? extends RuptureSurface> build(BackgroundRupType bgRupType,
			PointSourceDistanceCorrections distCorrType, GriddedFiniteRuptureSettings finiteSettings) {
		return buildConcreteWeightedList(supply(bgRupType, distCorrType, finiteSettings));
	}
	
	public WeightedList<? extends RuptureSurfaceSupplier<? extends RuptureSurface>> supply(BackgroundRupType bgRupType,
			PointSourceDistanceCorrections distCorrType, GriddedFiniteRuptureSettings finiteSettings) {
		// special cases
		if ((float)length == 0f) {
			// zero length; still return a finite approx because we want to make sure to bypass any distance corrections,
			// which would be applied as rJB. It's not safe to only set distance corrections to NONE because they could
			// be attached downstream of here.
			distCorrType = PointSourceDistanceCorrections.NONE;
			bgRupType = BackgroundRupType.POINT;
		} else if (Double.isFinite(strike) && (float)length > 0f) {
			// we have a finite surface, use that even if set to point
			bgRupType = BackgroundRupType.FINITE;
		}
		switch (bgRupType) {
		case POINT:
			return supplyFiniteApproxPointSurfaces(distCorrType);
		case FINITE:
			// this will use the given strike or strikeRange if previously supplied
			if (finiteSettings != null) {
				if (finiteSettings.sampleAlongStrike)
					sampleDASs();
				else
					fractionalDAS(0.5);
				if (finiteSettings.sampleDownDip)
					sampleHypocentralDepths();
				else
					fractionalHypocentralDepth(0.5);
			}
			
			double[] strikes;
			if (Double.isFinite(strike)) {
				// we have a strike that's specific to this source, use it (even if a global default strike was passed in)
				strikes = new double[] {strike};
			} else if (finiteSettings.strike != null) {
				// a default strike was passed in, use that
				strikes = getEvenlySpanningStrikes(finiteSettings.numSurfaces, finiteSettings.strike);
			} else {
				// random
				strikes = getRandStrikes(finiteSettings.numSurfaces, strikeRange);
			}
			return WeightedList.evenlyWeighted(supplyRandRectSurfaces(strikes));
		default:
			throw new IllegalStateException("Unsupported BackgroundRupType: "+bgRupType);
		}
	}
	
	private static <E extends RuptureSurface> WeightedList<E> buildConcreteWeightedList(WeightedList<? extends Supplier<E>> callList) {
		List<WeightedValue<E>> list;
		switch (callList.size()) {
		// more memory-efficient common special cases
		case 0:
			list = List.of();
			break;
		case 1:
			WeightedValue<? extends Supplier<E>> singleCall = callList.get(0);
			list = List.of(
					new WeightedValue<>(singleCall.value.get(), singleCall.weight));
			break;
		case 2:
			WeightedValue<? extends Supplier<E>> call0 = callList.get(0);
			WeightedValue<? extends Supplier<E>> call1 = callList.get(1);
			list = List.of(
					new WeightedValue<>(call0.value.get(), call0.weight),
					new WeightedValue<>(call1.value.get(), call1.weight));
			break;

		// default case when size > 2
		default:
			list = new ArrayList<>(callList.size());
			for (WeightedValue<? extends Supplier<E>> val : callList)
				list.add(new WeightedValue<>(val.value.get(), val.weight));
			list = Collections.unmodifiableList(list);
			break;
		}
		
		return new WeightedList.Unmodifiable<>(list, false);
	}
	
	/**
	 * Interface for a {@link Supplier} of {@link RuptureSurface}s. It adds the capability to determine if the
	 * returned surface will be finite or a point source.
	 * 
	 * @param <E>
	 */
	public static interface RuptureSurfaceSupplier<E extends RuptureSurface> extends Supplier<E> {
		
		/**
		 * 
		 * @return true if this is a finite surface, false if a {@link PointSurface} (including {@link FiniteApproxPointSurface})
		 */
		public boolean isFinite();
	}
	
	/**
	 * Lazy initialization wrapper of a {@link RuptureSurfaceSupplier}. Repeated calls to the {@link #get()} method will
	 * only build the surface once, which will be retained in memory after it is built.
	 * @param <E>
	 */
	public static class LazyRuptureSurfaceSupplier<E extends RuptureSurface> implements RuptureSurfaceSupplier<E> {
		
		private final RuptureSurfaceSupplier<? extends E> supplier;
		private volatile E value;

		public LazyRuptureSurfaceSupplier(RuptureSurfaceSupplier<? extends E> call) {
			this.supplier = call;
		}

		@Override
		public E get() {
			E result = value;
	        if (result == null) {
	            synchronized (this) {
	                if (value == null) {
	                	value = supplier.get();
	                }
	                result = value;
	            }
	        }
			return result;
		}

		@Override
		public boolean isFinite() {
			return supplier.isFinite();
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
		RectangularSurface surf = builder.buildRectSurface();
		
		System.out.println("Quad rJB at colocated point: "+surf.getDistanceJB(center));
		System.out.println("Trace:\t"+surf.getUpperEdge());
	}
}