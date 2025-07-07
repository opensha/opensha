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
import org.opensha.commons.data.WeightedValue;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.Interpolate;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.util.TectonicRegionType;

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
//	static final int NUM_ALPHA_INTERP_SAMPLES = 0; // TODO revert
	static final int NUM_SS_ALPHA_SAMPLES_DEFAULT = 90; // alpha samples when also supersampling
	static final int NUM_SS_AND_ALONG_ALPHA_SAMPLES_DEFAULT = 90; // alpha samples when supersampling and sampling along
	// if we're sampling along-strike, the number of said samples
	static final int NUM_SAMPLES_ALONG_DEFAULT = 21;
	// interpolate between discrete DAS samples
	static final int NUM_ALONG_INTERP_SAMPLES = 10;
	// if we're sampling down-dip, the number of said samples
	static final int NUM_SAMPLES_DOWN_DIP_DEFAULT = 5;
	
	public static InvCDFCache initDefaultCache(WeightedList<Double> fractiles, boolean sampleAlong, boolean sampleDownDip) {
		double[] fractilesArray = new double[fractiles.size()];
		for (int i=0; i<fractiles.size(); i++)
			fractilesArray[i] = fractiles.getValue(i);
		return initDefaultCache(fractilesArray, sampleAlong, sampleDownDip);
	}
	
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
	
	public static RjbDistributionDistanceCorrection getEvenlyWeightedFractiles(
			int numFractiles, boolean sampleAlong, boolean sampleDownDip) {
		Preconditions.checkState(numFractiles > 1);
		WeightedList<Double> fractiles = buildWeightedSpacedSamples(0d, 1d, numFractiles);
		InvCDFCache cache = initDefaultCache(fractiles, sampleAlong, sampleDownDip);
		return new RjbDistributionDistanceCorrection(fractiles, cache);
	}
	
	public static RjbDistributionDistanceCorrection getImportanceSampledFractiles(
			double[] fractileBoundaries, boolean sampleAlong, boolean sampleDownDip) {
		Preconditions.checkState(fractileBoundaries.length > 2);
		Preconditions.checkState(fractileBoundaries[0] == 0d, "First boundary must start at 0");
		Preconditions.checkState(fractileBoundaries[fractileBoundaries.length-1] == 1d, "Last boundary must end at 1");
		int num = fractileBoundaries.length -1;
		List<WeightedValue<Double>> fractileValues = new ArrayList<>(num);
		double weightSum = 0d;
		for (int i=0; i<num; i++) {
			double lower = fractileBoundaries[i];
			double upper = fractileBoundaries[i+1];
			double weight = upper - lower;
			weightSum += weight;
			Preconditions.checkState(weight > 0d);
			double fractile = 0.5*(lower + upper); // center
			fractileValues.add(new WeightedValue<Double>(fractile, weight));
		}
		Preconditions.checkState(Precision.equals(weightSum, 1d, 0.001), "Weights don't sum to 1: %s", weightSum);
		WeightedList<Double> fractiles = WeightedList.of(fractileValues);
		InvCDFCache cache = initDefaultCache(fractiles, sampleAlong, sampleDownDip);
		return new RjbDistributionDistanceCorrection(fractiles, cache);
	}
	
	private WeightedList<Double> fractiles;
	private InvCDFCache cache;

	public RjbDistributionDistanceCorrection(double fractile, boolean sampleAlong, boolean sampleDownDip) {
		this(WeightedList.evenlyWeighted(fractile), initDefaultCache(new double[] {fractile}, sampleAlong, sampleDownDip));
	}

	public RjbDistributionDistanceCorrection(WeightedList<Double> fractiles, InvCDFCache cache) {
		if (!(fractiles instanceof WeightedList.Unmodifiable<?>))
			fractiles = new WeightedList.Unmodifiable<>(fractiles);
		this.fractiles = fractiles;
		this.cache = cache;
		
		Preconditions.checkState(fractiles.size() == cache.fractiles.length,
				"Cache has %s fractiles but we have %s", cache.fractiles.length, fractiles.size());
		for (int f=0; f<fractiles.size(); f++) {
			double fractile = fractiles.getValue(f);
			Preconditions.checkState(Double.compare(fractile, cache.fractiles[f]) == 0,
					"Cache fractile %s is %s, expected %s", f, cache.fractiles[f], fractile);
		}
	}
	
	@Override
	public String toString() {
		String str = null;
		for (int f=0; f<fractiles.size(); f++) {
			double fractile = fractiles.getValue(f);
			if (str == null)
				str = "";
			else
				str += ",";
			if (Double.isNaN(fractile))
				str += "mean";
			else
				str += "p"+(float)(fractile*100d);
		}
		str += " (sampleAlong="+(cache.alongSamples.length > 1)+", sampleDownDip="+(cache.downDipSamples.length > 1)+")";
		return str;
	}

	@Override
	public WeightedList<SurfaceDistances> getCorrectedDistances(Location siteLoc, PointSurface surf,
			TectonicRegionType trt, double mag, double horzDist) {
		double length = surf.getAveLength();
		double zTop = surf.getAveRupTopDepth();
		if (length == 0d || !Double.isFinite(length)) {
			// no length, so no distance correction
			return WeightedList.evenlyWeighted(forZeroLength(siteLoc, horzDist, zTop));
		}
		double zBot = surf.getAveRupBottomDepth();
		double dipRad = Math.toRadians(surf.getAveDip());
		double horzWidth = surf.getAveHorizontalWidth();
		RjbFractileDistances dists = getCachedDist(surf, horzDist, cache);
		return getCorrectedDistances(siteLoc, fractiles, dists, horzDist, zTop, zBot, dipRad, length, horzWidth);
	}
	
	static WeightedList<SurfaceDistances> getCorrectedDistances(Location siteLoc, WeightedList<Double> fractiles,
			RjbFractileDistances dists, double rEpi, double zTop, double zBot, double dipRad, double length, double horzWidth) {
		boolean doFW = dists.fractFootwall > 0d;
		boolean doHW = dists.fractFootwall < 1d;
		List<WeightedValue<SurfaceDistances>> values = new ArrayList<>(doHW && doFW ? fractiles.size()*2 : fractiles.size());
		boolean[] hws;
		if (doFW && doHW)
			hws = new boolean[] {false, true};
		else if (doFW)
			hws = new boolean[] {false};
		else if (doHW)
			hws = new boolean[] {true};
		else
			throw new IllegalStateException("doFW and doHW both false? fract="+dists.fractFootwall);
		for (boolean hw : hws) {
			double weight = hw ? 1d - dists.fractFootwall : dists.fractFootwall;
			double[] rJBs = hw ? dists.hangingWallDists : dists.footwallDists;
			Preconditions.checkState(rJBs.length == fractiles.size());
			for (int f=0; f<rJBs.length; f++) {
				double fractWeight = fractiles.getWeight(f) * weight;
				double rJB = rJBs[f];
				Preconditions.checkState(rJB >= 0d);
				if (f > 0 && rJB == rJBs[f-1]) {
					// duplicate (probably zero), bundle in with previous
					WeightedValue<SurfaceDistances> prev = values.remove(values.size()-1);
					values.add(new WeightedValue<>(prev.value, prev.weight+fractWeight));
				} else {
					values.add(new WeightedValue<>(
							new LazyDistancesFromRjb(siteLoc, rJB, rEpi, zTop, zBot, dipRad, length, horzWidth, !hw), fractWeight));
				}
			}
		}
		return WeightedList.of(values);
	}
	
	static RjbFractileDistances getCachedDist(PointSurface surf, double horzDist, InvCDFCache cache) {
		if (horzDist < cache.minNonzeroDist) {
			RjbFractileDistances zeroVal = cache.getZeroDistVals(surf.getAveLength(), surf.getAveWidth(), surf.getAveDip());
			if (horzDist < 1e-10)
				// zero
				return zeroVal;
			// nonzero, interpolate
			RjbFractileDistances firstNonzeroVal = cache.getFractileFuncs(surf.getAveLength(), surf.getAveWidth(), surf.getAveDip(),
					horzDist).get(0);
			return interpolateFractileDists(zeroVal, 0d, firstNonzeroVal, cache.minNonzeroDist, horzDist);
		}
		return cache.getFractileFuncs(surf.getAveLength(), surf.getAveWidth(), surf.getAveDip(),
				horzDist).interpolate(horzDist);
	}
	
	static SurfaceDistances forZeroLength(Location siteLoc, double horzDist, double zTop) {
		double rRup = hypot2(horzDist, zTop);
		double rSeis = zTop >= GriddedSurfaceUtils.SEIS_DEPTH ? rRup : hypot2(horzDist, GriddedSurfaceUtils.SEIS_DEPTH);
		double rX = horzDist == 0d ? 0 : -horzDist;
		return new SurfaceDistances.Precomputed(siteLoc, rRup, horzDist, rSeis, rX);
	}
	
	public static SurfaceDistances calcDistancesForRjb(PointSurface surf, Location siteLoc, double horzDist, double rJB, boolean footwall) {
		double zTop = surf.getAveRupTopDepth();
		double zBot = surf.getAveRupBottomDepth();
		double dipRad = Math.toRadians(surf.getAveDip());
		double length = surf.getAveLength();
		double horzWidth = surf.getAveHorizontalWidth();
		return new LazyDistancesFromRjb(siteLoc, rJB, horzDist, zTop, zBot, dipRad, length, horzWidth, footwall);
	}
	
	public static class LazyDistancesFromRjb implements SurfaceDistances {
		
		private final Location siteLoc;
		private final double rJB;
		private final double rEpi;
		private final double zTop;
		private final double zBot;
		private final double dipRad;
		private final double length;
		private final double horzWidth;
		private final boolean footwall;
		
		private volatile Double rRup;
		private volatile Double rSeis;

		public LazyDistancesFromRjb(Location siteLoc, double rJB, double rEpi, double zTop, double zBot, double dipRad,
				double length, double horzWidth, boolean footwall) {
			this.siteLoc = siteLoc;
			this.rJB = rJB;
			this.rEpi = rEpi;
			this.zTop = zTop;
			this.zBot = zBot;
			this.dipRad = dipRad;
			this.length = length;
			this.horzWidth = horzWidth;
			this.footwall = footwall;
		}

		@Override
		public Location getSiteLocation() {
			return siteLoc;
		}

		@Override
		public double getDistanceRup() {
			if (rRup == null)
				rRup = getCorrDistRup(rJB, rEpi, zTop, zBot, dipRad, length, horzWidth, footwall);
			return rRup;
		}

		@Override
		public double getDistanceJB() {
			return rJB;
		}

		@Override
		public double getDistanceSeis() {
			if (rSeis == null) {
				if (zTop >= GriddedSurfaceUtils.SEIS_DEPTH)
					rSeis = getDistanceRup();
				else
					rSeis = getCorrDistRup(rJB, rEpi, GriddedSurfaceUtils.SEIS_DEPTH, Math.max(GriddedSurfaceUtils.SEIS_DEPTH, zBot),
							dipRad, length, horzWidth, footwall);
			}
			return rSeis;
		}

		@Override
		public double getDistanceX() {
			if (Precision.equals(rJB,  0d, 0.0001))
				// rJB == 0: inside the surface projection, assume halfway away from trace
				// by definition, this means we're on the hanging wall (even if footwall == true)
				return 0.5*horzWidth;
			return footwall ? -rJB : rJB + horzWidth;
		}
		
	}
	
	public static double getCorrDistRup(double rJB, double rEpi, double zTop, double zBot, double dipRad, double length, double horzWidth, boolean footwall) {
		// special cases
		if (Precision.equals(dipRad,  PI_HALF, 0.0001) || horzWidth < 0.0001) {
			// vertical: the upper edge of the rupture is by definition the closest point to the site
			return hypot2(rJB, zTop);
		} else if (Precision.equals(rJB,  0d, 0.0001)) {
			// special case: site is within the surface projection of (directly above) the rupture
			
			// we don't know if it's directly over the top edge, bottom edge, or somewhere in-between
			// approximate it as if we're directly over the middle of the rupture; that should capture
			// average behavior
			
			LineSegment3D line = new LineSegment3D(0, 0, zTop, horzWidth, 0, zBot);
			return distanceToLineSegment3D(0.5*horzWidth, 0, line);
		} else if (footwall) {
			// special case: on the footwall, meaning the upper edge of the rupture is by definition the closest point
			// to the site
			return hypot2(rJB, zTop);
		}
		// if we're here, we're on the hanging wall and rJB>0
		
////		double f = (2d/Math.PI)*Math.asin(Math.min(1, rJB/horzWidth));
////		double f = Math.min(1, rJB/horzWidth);
//		double f = 1 - Math.exp(-rJB/horzWidth);
//		Preconditions.checkState(f >= 0 && f <= 1, "Unexpected f=%s for rJB=%s, horzWidth=%s", f, rJB, horzWidth);
//		double zPrime = zTop + f*(zBot-zTop);
//		return hypot2(rJB, zPrime);
		
		// define coordinate system where:
		// (0, 0, 0) lies above the middle of the fault.
		// front edge extends from (-w/2, l/2, zTop) to (w/2, l/2, zBot)
		// right (bottom) edge extends from (w/2, l/2, zBot) to (w/2, -l/2, zBot)
		double halfW = 0.5*horzWidth;
		double halfL = 0.5*length;
		LineSegment3D frontEdge = new LineSegment3D(-halfW, halfL, zTop, halfW, halfL, zBot);
		System.out.println("\t\tFront edge is at "+(float)-halfW+", "+(float)halfL+" -> "+(float)halfW+", "+(float)halfL);
		// right (bottom) edge extends from (w/2, l/2, zBot) to (w/2, -l/2, zBot)
		
		double rEpiSq = rEpi*rEpi;
		
		// find possible site locations for this rEpi and rJB. assume that rEpi is to the center of the fault
		
		// first do it for the front edge (can also be off the front bottom corner)
		// valid footprint is for -w/2 <= xSite <= w/2
		double ySite = halfL + rJB;
		double xSite = Math.sqrt(rEpiSq - ySite*ySite);
		
		List<double[]> validLocs = new ArrayList<>(3);
		if (Double.isFinite(xSite)) {
			// there is a potentially valid site in this direction
			// xSite could be negative and or positive, first try positive
			if (xSite >= -halfW && xSite <= halfW) {
//				System.out.println("\t\tAdding front positive: "+(float)xSite+", "+(float)ySite);
				validLocs.add(new double[] {xSite, ySite});
			}
			// now try negative
			xSite = -xSite;
			if (xSite >= -halfW && xSite <= halfW) {
//				System.out.println("\t\tAdding front negative: "+(float)xSite+", "+(float)ySite);
				validLocs.add(new double[] {xSite, ySite});
			}
		}
		
		// now for the right edge (can also be off the front bottom corner
		// valid footprint is for 0 <= ySite <= l/2
		xSite = halfW + rJB;
		ySite = Math.sqrt(rEpiSq - xSite*xSite);
		if (Double.isFinite(ySite)) {
			// there is a potentially valid site in this direction
			// we're only interested in ones with ySite >= 0 (the problem is symmetrical across y)
			if (ySite >= 0 && ySite <= halfL) {
//				System.out.println("\t\tAdding side positive: "+(float)xSite+", "+(float)ySite);
				validLocs.add(new double[] {xSite, ySite});
			}
		}
		
		// now for the off-the-corner
		double halfWsq = halfW*halfW;
		double halfLsq = halfL*halfL;
		double rJBsq = rJB*rJB;
//		if (rJBsq > halfWsq + halfLsq) {
			
//		}
		double C = (rEpiSq - rJBsq + halfW*halfW + halfL*halfL) * 0.5;
		
		// 2) Solve quadratic A x^2 + B x + D = 0
		double A = 1.0 + halfWsq/halfLsq;
		double B = -2.0 * halfW * C / halfLsq;
		double D = (C*C)/halfLsq - rEpiSq;

		// discriminant
		double disc = B*B - 4.0*A*D;
//		System.out.println("disc="+disc+" for C="+C+", A="+A+", B=" +B+", D="+D);
		if (disc >= 0) {
			double sqrtD = Math.sqrt(disc);
			for (double sign : new double[]{+1, -1}) {
				double xCorner = (-B + sign*sqrtD) / (2.0*A);
				double yCorner = (C - halfW*xCorner) / halfL;
				// keep only the quadrant you want
//				System.out.println("Testing "+xCorner+" >= "+halfW+" && "+yCorner+" >= "+halfL);
				if (xCorner >= halfW && yCorner >= halfL && Double.isFinite(yCorner)) {
					validLocs.add(new double[]{ xCorner, yCorner });
//					System.out.printf(
//							"  [corner] x=%.6f, y=%.6f%n", xCorner, yCorner
//							);
				}
			}
		}
		
		if (validLocs.isEmpty()) {
			// must be supersampled or sampled along/dd
			// if we couln't find anything, just put it on the corner
			double rJBprime = ROOT_TWO_OVER_TWO * rJB;
			validLocs.add(new double[] {halfW+rJBprime, halfL+rJBprime});
		}
//		Preconditions.checkState(!validLocs.isEmpty(),
//				"No valid locations found for rJB=%s, rEpi=%s, w=%s, l=%s", rJB, rEpi, horzWidth, length);
		double sum = 0d;
		for (double[] loc : validLocs) {
			double x = loc[0];
			double y = loc[1];
			
			if (y < halfL)
				// this means the site is off of the bottom edge (and not beyond the front edge)
				// in this case, we'll calculate the distance to the dipping edge right at that y
				// we already have frontEdge at a known y, so instead we just move our y here to be at that location
				// off the right and not the corner, move it to exactly off the front edge for the calculation
				y = halfL;
			double rRup = distanceToLineSegment3D(x, y, frontEdge);
//			System.out.println("\t\tFor site location ("+(float)x+", "+(float)loc[1]+"); rRup="+rRup);
			
			// this is what chatGPT wanted me to do, but the above gives the exact same results and is faster
			// because my line segment object precomputes some values for quick calcs 
//			double yFault = y < halfL ? y : halfL;
//			double rRup = distanceToLineSegment3D(x, y, new LineSegment3D(-halfW, yFault, zTop, halfW, yFault, zBot));
//			System.out.println("\t\tFor site location ("+(float)x+", "+(float)y+"); rRup="+rRup+"; yFault="+(float)yFault);
			
			sum += rRup;
		}
		if (validLocs.size() == 1)
			return sum;
		return sum / (double)validLocs.size();
		
//		// approximate the calculation assuming different angles; we'll use three locations:
//		/*
//		 * map view schematic; legend:
//		 * G: grid node center
//		 * ||: rupture upper edge
//		 * |: rupture lower edge
//		 * A: along-strike of the rupture, but shifted to the right because HW flag means we're not left of G (put it halfway between G and .)
//		 * B: site is somewhere off the end of the fault, and also past the bottom edge
//		 * C: site perfectly down-dip of the rupture, and also rJB away from the surface projection of the rupture
//		 * D: site that would improperly get included as footwall and is accounted for above
//		 * *: origin where x=0 and y=0 (upper front corner of the rupture)
//		 * .: lower front corner of the rupture
//		 * 
//		 * 
//		 *     D !  A !     
//		 *       !    !    B
//		 *       !    !
//		 *  *_________.-------C
//		 * ||         |
//		 * ||         |
//		 * ||         |
//		 * ||         |
//		 * ||    G    |--------
//		 * ||         |
//		 * ||         |
//		 * ||         |
//		 * ||_________|
//		 * 
//		 */
//		
//		// TODO: these weights are wrong because it assumes that alpha is sampled fully for any rJB
//		// BUT: there's a single rJB for each alpha
//		
//		// we can compute all distances to the forward edge, from '*' to '.'
//		LineSegment3D line = new LineSegment3D(0, 0, zTop, horzWidth, 0, zBot);
//		
//		// calculate distance from edge [* .] to points A&D 
//		
//		// front edge is along the x axis between:
//		//	(0, 0, zTop) and (horzWidth, 0, zBot)
//		double distA = 0.5*
//						(distanceToLineSegment3D(0.25*horzWidth, rJB, line)
//						+ distanceToLineSegment3D(0.75*horzWidth, rJB, line));
//		
//		// now calculate for site B where we're off the end and past the bottom
//		// define rJB' = rJB*sqrt(2)/2
//		// site B is then at (horzWidth + rJB', rJB')
//		// front edge is along the x axis between:
//		//  (0, 0, zTop) and (horzWidth, 0, zBot)
//		double rJBprime = ROOT_TWO_OVER_TWO * rJB;
//		double distB = distanceToLineSegment3D(horzWidth+rJBprime, rJBprime, line);
//		
//		// now calculate for site C where we're in the perfectly down-dip direction
//		// we'll put site C at (horzWdith+rJB, 0); y doesn't matter here since the distance is the same no matter where we are along-stike
//		double distC = distanceToLineSegment3D(horzWidth+rJB, 0, line);
//		
//		// now compute weights between the three
//		// when we're really close in, distances A and C dominate
//		// when we're far (relive to width and length), distance B dominates
//		
//		double halfLength = 0.5*length;
//		double halfWidth = 0.5*horzWidth;
//
//		// azimuth from G to the rightmost '!' that is rJB away 
//		double theta1 = Math.atan(halfWidth / (halfLength + rJB));
//		// azimuth from G to the upper '-' that is rJB away
//		double theta2 = Math.atan((halfLength+rJB) / halfLength);
//
//		// range from 0 to theta1 belongs to side A
//		// range from theta1 to theta2 belongs to corner B
//		// range from theta2 to PI/2 belongs to side C
//		// sum of the weights is PI/2
//		
////		double weightA =  theta1;
////		double weightB = theta2 - theta1;
////		double weightC = PI_HALF - theta2;
////
////		Preconditions.checkState(Precision.equals(PI_HALF, weightA+weightB+weightC, 1e-4));
////		Preconditions.checkState(weightA >= 0);
////		Preconditions.checkState(weightB >= 0);
////		Preconditions.checkState(weightC >= 0);
////
////		return (weightA*distA + weightB*distB + weightC*distC)/PI_HALF;
//
//		double weightA =  2*theta1;
//		double weightB = theta2 - theta1;
//		double weightC = PI_HALF - theta2;
//		
//		// now correct these weights if we're close in and the rJB distribution is skewed
//		double cornerDist = Math.sqrt(halfWidth*halfWidth + halfLength*halfLength);
//		double cornerRampMax = 2*cornerDist;
//		if (rJB < cornerRampMax) {
//			double bAdd = (cornerRampMax - rJB)/cornerRampMax;
//			distA = bAdd*distB + (1d-bAdd)*distA;
//			distC = bAdd*distB + (1d-bAdd)*distC;
//		}
//
//		Preconditions.checkState(weightA >= 0);
//		Preconditions.checkState(weightB >= 0);
//		Preconditions.checkState(weightC >= 0);
//
//		return (weightA*distA + weightB*distB + weightC*distC)/(weightA + weightB + weightC);
////		return distA;
////		return distB;
////		return distC;
	}
	
	private static final double PI_HALF = Math.PI/2d; // 90 degrees
	
	private static double ROOT_TWO_OVER_TWO = Math.sqrt(2)/2;

	/**
	 * Same as {@code Math.hypot()} without regard to under/over flow.
	 */
	private static final double hypot2(double v1, double v2) {
		return Math.sqrt(v1 * v1 + v2 * v2);
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
		
		return Math.abs(doCalcRJB(rEpi, rupLength, rupHorzWidth, gridNodeFractDAS, gridNodeFractDepth, sinA, cosA));
	}
	
	/**
	 * Fast Joyner–Boore distance (rJB) for a rectangular fault patch.
	 * Returns negative rJB on the footwall side and positive on the hanging wall.
	 *
	 * @param rEpi               Epicentral distance from site to grid node (horizontal map distance).
	 * @param rupLength          Along-strike length of the patch.
	 * @param rupHorzWidth       Down-dip (horizontal) width of the patch.
	 * @param gridNodeFractDAS   Fractional distance along strike where the site projection falls (0…1).
	 * @param gridNodeFractDepth Fractional down-dip position of the patch relative to the site projection (0…1).
	 * @param sinAlpha           Precomputed sin(strike-to-site azimuth).
	 * @param cosAlpha           Precomputed cos(strike-to-site azimuth).
	 * @return rJB signed by wall: <0 ⇒ footwall, 0 ⇒ on-patch, >0 ⇒ hanging wall.
	 */
	private static double doCalcRJB(double rEpi, double rupLength, double rupHorzWidth,
			double gridNodeFractDAS, double gridNodeFractDepth,
			double sinAlpha, double cosAlpha) {
		// 1) Compute the projected patch bounds in local strike (X) and dip (Y) coords
		double xMin = -gridNodeFractDAS * rupLength;      // left edge along strike
		double xMax = xMin + rupLength;                   // right edge along strike
		double yMin = -gridNodeFractDepth * rupHorzWidth; // top edge down-dip
		double yMax = yMin + rupHorzWidth;                 // bottom edge down-dip

		// 2) Rotate the site (rEpi, 0) into the local patch frame:
		//    X-axis = strike, Y-axis = dip direction (positive down-dip)
		double xLoc = rEpi * cosAlpha;
		double yLoc = -rEpi * sinAlpha;

		// 3) Compute the shortest distance components (dx, dy) to the rectangle
		boolean inside = true;
		double dx = 0, dy = 0;
		if (xLoc < xMin) {
			dx = xMin - xLoc;  // site is left of patch
			inside = false;
		} else if (xLoc > xMax) {
			dx = xLoc - xMax;  // site is right of patch
			inside = false;
		}
		if (yLoc < yMin) {
			dy = yMin - yLoc;  // site is above (footwall side of) patch top
			inside = false;
		} else if (yLoc > yMax) {
			dy = yLoc - yMax;  // site is below (hanging-wall side of) patch bottom
			inside = false;
		}

		// 4) If the projection falls inside the patch, rJB = 0
		if (inside) {
			return 0.0;
		}

		// 5) Euclidean distance to the patch edge (always positive)
		double absDist = hypot2(dx, dy);

		// 6) Sign by comparing yLoc to the *top* edge (yMin):
		//    yLoc < yMin ⇒ site sits above the patch top (footwall) ⇒ negative
		//    yLoc ≥ yMin ⇒ site sits on/below patch top (hanging wall) ⇒ positive
		boolean isFootwall = (yLoc < yMin);
		return isFootwall ? -absDist : absDist;
	}

//	private static double doCalcRJB(double rEpi, double rupLength, double rupHorzWidth,
//			double gridNodeFractDAS, double gridNodeFractDepth, double sinAlpha, double cosAlpha) {
//		// Fault rectangle in local (strike,dip) coords is:
//		//       X in [Xmin, Xmax], with total length = rupLength
//		//       Y in [Ymin, Ymax], with total width = rupHorzWidth
//		//    where (0,0) is the grid node in local coordinates.
//		double xMin = -gridNodeFractDAS * rupLength;
//		double xMax = xMin + rupLength; // = (1 - gridNodeFractDAS)*rupLength
//		double yMin = -gridNodeFractDepth * rupHorzWidth;
//		double yMax = yMin + rupHorzWidth;     // = (1 - gridNodeFractDepth)*wHorz
//
//		// Convert the site's global coords (rEpi, 0) -> local (xLoc, yLoc)
//		//    local X-axis = strike = (cos(alpha), sin(alpha))
//		//    local Y-axis = dip in map = (-sin(alpha), cos(alpha))
//		//    node is at (0,0), site is at (rEpi, 0).
//		double xLoc = rEpi * cosAlpha;   // = x*cosA + y*sinA, but site y=0
//		double yLoc = -rEpi * sinAlpha;  // = -x*sinA + y*cosA
//
//		// Distance from (xLoc, yLoc) to that axis-aligned bounding box
//		boolean inside = true;
//		double dx = 0.0;
//		if (xLoc < xMin) {
//			dx = xMin - xLoc;
//			inside = false;
//		} else if (xLoc > xMax) {
//			dx = xLoc - xMax;
//			inside = false;
//		}
//
//		double dy = 0.0;
//		if (yLoc < yMin) {
//			dy = yMin - yLoc;
//			inside = false;
//		} else if (yLoc > yMax) {
//			dy = yLoc - yMax;
//			inside = false;
//		}
//		if (inside)
//			return 0d;
//
//		double ret = Math.sqrt(dx * dx + dy * dy);
//		Preconditions.checkState(ret>=0d, "ret=%s, dx=%x, dy=%s", ret, dx, dy);
//		if (rEpi == FIRST_D_DIST)
//			System.out.println("rJB="+ret+" for rEpi="+rEpi+", rupLength="+rupLength+", rupHorzWidth="+rupHorzWidth
//					+", gridNodeFractDAS="+gridNodeFractDAS+", gridNodeFractDepth="+gridNodeFractDepth+", sinAlpha="+sinAlpha+", cosAlpha="+cosAlpha
//					+"; yLoc="+yLoc+", yMin="+yMin+";\tHW="+(yLoc>yMin));
//		if (yLoc > yMin)
//			// footwall
//			return -ret;
//		return ret;
//	}
	
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

	private static final double[] single_sample_0p5 = {0.5};
	private static final double[] single_sample_0 = {0};
	
	public static class InvCDFCache {
		
		public final double minNonzeroDist;
		final double logDistSampleDiscr;
		final double[] alphaRadSamplesDipping;
		final double[] alphaRadSamplesVertical;
		final double betaRad;
		final double[] cellSamplesX;
		final double[] cellSamplesY;
		final double[] alongSamples;
		final double[] downDipSamples;
		public final double[] fractiles;

		private double maxDistBin;
		private EvenlyDiscretizedFunc logDistBins;

		private ConcurrentMap<RuptureKey, RjbFractileDistanceFuncs> valueFuncs = new ConcurrentHashMap<>();
		private ConcurrentMap<RuptureKey, RjbFractileDistances> zeroDistValues = new ConcurrentHashMap<>();

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
			double maxAlphaVertical = Math.PI;
			double maxAlphaDipping = Math.PI*2;
			if (ALPHA_ALIGN_EDGES) {
				// start at 0 and end one bin before the end (to avoid double counting)
				alphaRadSamplesVertical = Arrays.copyOf(buildSpacedSamples(0d, maxAlphaVertical, numAlphaSamples+1, true), numAlphaSamples);
				alphaRadSamplesDipping = Arrays.copyOf(buildSpacedSamples(0d, maxAlphaDipping, numAlphaSamples+1, true), numAlphaSamples);
			} else {
				// bin centers, can put edges right at 0 and 180/360 without double counting.
				alphaRadSamplesVertical = buildSpacedSamples(0d, maxAlphaVertical, numAlphaSamples, false);
				alphaRadSamplesDipping = buildSpacedSamples(0d, maxAlphaDipping, numAlphaSamples, false);
			}
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
		
		public RjbFractileDistanceFuncs getFractileFuncs(double rupLen, double rupWidth, double dip, double horzDist) {
			RuptureKey key = new RuptureKey(rupLen, rupWidth, dip);
			RjbFractileDistanceFuncs ret = valueFuncs.get(key);
			
			if (horzDist > maxDistBin) {
				// largest we've ever seen
				synchronized (this) {
					if (horzDist > maxDistBin)
						initLogDistBins(horzDist);
				}
			}
			EvenlyDiscretizedFunc logDistBins = this.logDistBins;
			
			boolean doHW = dip < 90d;
			double[] alphaRadSamples = doHW ? alphaRadSamplesDipping : alphaRadSamplesVertical;
			
			if (ret == null) {
				// need to build it from scratch
				List<CompletableFuture<RjbFractileDistances>> futures = new ArrayList<>(logDistBins.size());
				for (int r=0; r<logDistBins.size(); r++) {
					double rEpi = Math.pow(10, logDistBins.getX(r));
					futures.add(CompletableFuture.supplyAsync(new SampleFractileCalculator(
							rEpi, rupLen, rupWidth, dip, alphaRadSamples, betaRad, cellSamplesX, cellSamplesY,
							alongSamples, downDipSamples, fractiles)));
				}
				EvenlyDiscretizedFunc[] fwDists = new EvenlyDiscretizedFunc[fractiles.length];
				EvenlyDiscretizedFunc[] hwDists = doHW ? new EvenlyDiscretizedFunc[fractiles.length] : null;
				double[] fwFracts = new double[logDistBins.size()];
				for (int i=0; i<fractiles.length; i++) {
					fwDists[i] = new EvenlyDiscretizedFunc(logDistBins.getMinX(), logDistBins.size(), logDistBins.getDelta());
					if (doHW)
						hwDists[i] = new EvenlyDiscretizedFunc(logDistBins.getMinX(), logDistBins.size(), logDistBins.getDelta());
				}
				for (int r=0; r<futures.size(); r++) {
					RjbFractileDistances values = futures.get(r).join();
					for (int i=0; i<fractiles.length; i++) {
						fwDists[i].set(r, values.footwallDists == null ? 0d : values.footwallDists[i]);
						if (doHW)
							hwDists[i].set(r, values.hangingWallDists[i]);
					}
					fwFracts[r] = values.fractFootwall;
					if (!doHW)
						Preconditions.checkState(fwFracts[r] == 1d);
				}
				ret = new RjbFractileDistanceFuncs(fwDists, hwDists, fwFracts);
				synchronized (valueFuncs) {
					valueFuncs.put(key, ret);
				}
			} else {
				if (horzDist > 0d) {
					// we already have one, see if we need to expand it
					double logDist = Math.log10(horzDist);
					if (logDist > ret.footwallDistFuncs[0].getMaxX()) {
						// we need to expand it
						
						// do it with a local variable because there could be thread contention
						EvenlyDiscretizedFunc[] expandedFWArray = new EvenlyDiscretizedFunc[fractiles.length];
						EvenlyDiscretizedFunc[] expandedHWArray = doHW ? new EvenlyDiscretizedFunc[fractiles.length] : null;
						double[] expandedFWFracts = new double[logDistBins.size()];
						
						// grow them
						// first copy into larger functions
						int origNum = ret.footwallDistFuncs[0].size();
						for (int i=0; i<fractiles.length; i++) {
							EvenlyDiscretizedFunc expandedFW = new EvenlyDiscretizedFunc(logDistBins.getMinX(), logDistBins.size(), logDistBins.getDelta());
							EvenlyDiscretizedFunc expandedHW = doHW ? new EvenlyDiscretizedFunc(logDistBins.getMinX(), logDistBins.size(), logDistBins.getDelta()) : null;
							for (int r=0; r<origNum; r++) {
								Preconditions.checkState(i > 0 || Precision.equals(expandedFW.getX(r), ret.footwallDistFuncs[i].getX(r), 0.0001));
								expandedFW.set(r, ret.footwallDistFuncs[i].getY(r));
								if (doHW)
									expandedHW.set(r, ret.hangingWallDistFuncs[i].getY(r));
								if (i == 0)
									expandedFWFracts[r] = ret.fractFootwalls[r];
							}
							expandedFWArray[i] = expandedFW;
							if (doHW)
								expandedHWArray[i] = expandedHW;
						}
						// now calculate for the new distances
						List<CompletableFuture<RjbFractileDistances>> futures = new ArrayList<>(logDistBins.size()-origNum);
						for (int r=origNum; r<logDistBins.size(); r++) {
							double rEpi = Math.pow(10, logDistBins.getX(r));
							futures.add(CompletableFuture.supplyAsync(new SampleFractileCalculator(
									rEpi, rupLen, rupWidth, dip, alphaRadSamples, betaRad, cellSamplesX, cellSamplesY,
									alongSamples, downDipSamples, fractiles)));
						}
						for (int f=0; f<futures.size(); f++) {
							RjbFractileDistances values = futures.get(f).join();
							int r = origNum+f;
							for (int i=0; i<fractiles.length; i++) {
								expandedFWArray[i].set(r, values.footwallDists[i]);
								if (doHW)
									expandedHWArray[i].set(r, values.hangingWallDists[i]);
							}
							expandedFWFracts[r] = values.fractFootwall;
						}
						ret = new RjbFractileDistanceFuncs(expandedFWArray, expandedHWArray, expandedFWFracts);
						synchronized (valueFuncs) {
							valueFuncs.put(key, ret);
						}
					}
				}
			}
			return ret;
		}
		
		public RjbFractileDistances getZeroDistVals(double rupLen, double rupWidth, double dip) {
			RuptureKey key = new RuptureKey(rupLen, rupWidth, dip);
			RjbFractileDistances ret = zeroDistValues.get(key);
			
			if (ret == null) {
				// need to build it from scratch
				double[] alphaRadSamples = dip < 90d ? alphaRadSamplesDipping : alphaRadSamplesVertical;
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
	private static double FIRST_D_DIST = 15d;
	
	public static class RjbFractileDistanceFuncs {
		public final EvenlyDiscretizedFunc[] footwallDistFuncs;
		public final EvenlyDiscretizedFunc[] hangingWallDistFuncs;
		public final double[] fractFootwalls;
		
		private RjbFractileDistanceFuncs(EvenlyDiscretizedFunc[] footwallDistFuncs,
				EvenlyDiscretizedFunc[] hangingWallDistFuncs, double[] fractFootwalls) {
			this.footwallDistFuncs = footwallDistFuncs;
			this.hangingWallDistFuncs = hangingWallDistFuncs;
			this.fractFootwalls = fractFootwalls;
		}
		
		public RjbFractileDistances get(int index) {
			double fractFootwall = fractFootwalls[index];
			double[] footwallDists = fractFootwall == 0d ? null : new double[footwallDistFuncs.length];
			double[] hangingWallDists = hangingWallDistFuncs == null ? null : new double[footwallDistFuncs.length];
			int num = footwallDists == null ? hangingWallDists.length : footwallDists.length;
			for (int f=0; f<num; f++) {
				if (footwallDists != null)
					footwallDists[f] = footwallDistFuncs[f].getY(index);
				if (hangingWallDists != null)
					hangingWallDists[f] = hangingWallDistFuncs[f].getY(index);
			}
			return new RjbFractileDistances(footwallDists, hangingWallDists, fractFootwall);
		}
		
		public RjbFractileDistances interpolate(double horzDist) {
			EvenlyDiscretizedFunc logDistFunc = footwallDistFuncs[0];
			double logDist = Math.log10(horzDist);
			// distances are discretized in log space, but do the actual interpolation in linear space
			int xIndBelow = logDistFunc.getClosestXIndex(logDist);
			if (logDist < logDistFunc.getX(xIndBelow))
				xIndBelow--;
			double distBelow = Math.pow(10, logDistFunc.getX(xIndBelow));
			double distAbove = Math.pow(10, logDistFunc.getX(xIndBelow+1));
			
			return interpolateFractileDists(get(xIndBelow), distBelow, get(xIndBelow+1), distAbove, horzDist);
		}
	}
	
	static RjbFractileDistances interpolateFractileDists(RjbFractileDistances dists1, double horzDist1, RjbFractileDistances dists2, double horzDist2, double horzDist) {
//		double[] footwallDists = dists1.footwallDists == null ? null : new double[dists1.footwallDists.length];
//		double[] hangingWallDists = dists1.hangingWallDists == null ? null : new double[dists1.hangingWallDists.length];
		int num = dists1.footwallDists == null ? dists1.hangingWallDists.length : dists1.footwallDists.length;
//		interp: y1 + (x - x1) * (y2 - y1) / (x2 - x1);
		double xFract = (horzDist - horzDist1); // x - x1
		double xBinSize = horzDist2 - horzDist1; // x2 - x1
		double fractFW = dists1.fractFootwall + xFract*(dists2.fractFootwall - dists1.fractFootwall) / xBinSize;
		double[] footwallDists = (float)fractFW > 0f ? new double[num] : null;
		double[] hangingWallDists = (float)fractFW < 1f ? new double[num] : null;
		for (int f=0; f<num; f++) {
			if (footwallDists != null) {
				Preconditions.checkState(dists2.footwallDists != null, "The further one should always have footwall");
				if (dists1.footwallDists == null)
					// nearer one didn't have any footwall, which means on top of the surface, treat it as zero
					footwallDists[f] = xFract*(dists2.footwallDists[f]) / xBinSize;
				else
					footwallDists[f] = dists1.footwallDists[f] + xFract*(dists2.footwallDists[f] - dists1.footwallDists[f]) / xBinSize;
				// interpolation can lead to tiny negative values, clamp to zero
				Preconditions.checkState(footwallDists[f] > -1e-10);
				if (footwallDists[f] < 0)
					footwallDists[f] = 0d;
			}
			if (hangingWallDists != null) {
				hangingWallDists[f] = dists1.hangingWallDists[f] + xFract*(dists2.hangingWallDists[f] - dists1.hangingWallDists[f]) / xBinSize;
				Preconditions.checkState(hangingWallDists[f] > -1e-10);
				if (hangingWallDists[f] < 0)
					hangingWallDists[f] = 0d;
			}
		}
		return new RjbFractileDistances(footwallDists, hangingWallDists, fractFW);
	}
	
	public static class RjbFractileDistances {
		public final double[] footwallDists;
		public final double[] hangingWallDists;
		public final double fractFootwall;
		
		private RjbFractileDistances(double[] footwallDists, double[] hangingWallDists, double fractFootwall) {
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
	
	private static class SampleFractileCalculator implements Supplier<RjbFractileDistances> {

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
		public RjbFractileDistances get() {
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
				return new RjbFractileDistances(ret, null, 1d);
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
				SampleFractileCalculator extD = null;
				synchronized (SampleFractileCalculator.class) {
					if (FIRST_D) {
						if (Double.isFinite(FIRST_D_DIST)) {
							if (rEpi == FIRST_D_DIST) {
								D = true;
							} else {
								extD = new SampleFractileCalculator(FIRST_D_DIST, rupLength, rupWidth, dip,
										alphaRadSamples, betaRad, cellSamplesX, cellSamplesY, alongSamples, downDipSamples, fractiles);
								D = false;
							}
						}  else {
							D = true;
						}
					} else {
						D = false;
					}
				}
				if (extD != null)
					extD.get();
				FIRST_D = false;
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
			
			SampleDistributionTracker sampleDist = new SampleDistributionTracker(closest, farthest, 1000, dip < 90d);
			
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
			boolean[] prevTempFWarray = null;
			boolean[] tempFWarray = null;
			if (dip < 90d) {
				prevTempFWarray = new boolean[numPerAlpha];
				tempFWarray = new boolean[numPerAlpha];
			}
			
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
				boolean[] innerFWs = tempFWarray;
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
				tempFWarray = prevTempFWarray;
				// set ours as previous
				prevAlphaVals = innerSamples;
				prevTempFWarray = innerFWs;
			}
			
			if (NUM_ALPHA_INTERP_SAMPLES > 1)
				// wrap around interpolation
				addInterpolateSamplesBetween(sampleDist, prevAlphaVals, firstAlphaVals, subSampleFracts, oneMinusFracts);

			double fractFW = sampleDist.getFootwallFract();
			if (fractiles.length == 1 && Double.isNaN(fractiles[0])) {
				// special case for mean only, don't need inv CDF
				return new RjbFractileDistances(new double[] {sampleDist.getMean(true)},
						new double[] {sampleDist.getMean(false)}, fractFW);
			}
			
			double[] fwDists = sampleDist.calcFractiles(fractiles, true);
			double[] hwDists = sampleDist.calcFractiles(fractiles, false);
			
			if (D && Double.isFinite(FIRST_D_DIST)) {
				System.out.println("Results for rEpi="+(float)rEpi);
				System.out.println("\tFractFW="+(float)sampleDist.getFootwallFract());
				System.out.println("\tFootwall Count: "+sampleDist.fwCount);
				System.out.println("\t\tMean: "+sampleDist.getMean(true));
				if (fwDists != null)
					for (int f=0; f<fractiles.length; f++)
						System.out.println("\t\tp"+(float)(fractiles[f]*100d)+": "+fwDists[f]);
				System.out.println("\tHangingwall Count: "+sampleDist.hwCount);
				System.out.println("\t\tMean: "+sampleDist.getMean(false));
				if (hwDists != null)
					for (int f=0; f<fractiles.length; f++)
						System.out.println("\t\tp"+(float)(fractiles[f]*100d)+": "+hwDists[f]);
			}
			
			return new RjbFractileDistances(fwDists, hwDists, fractFW);
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
					for (int k=1; k<subSampleFracts.length; k++) {
						boolean footwall = oneMinusFracts[k] >= subSampleFracts[k] ? prevVals[j] < 0 : val < 0;
						destArray[index] = oneMinusFracts[k]*Math.abs(prevVals[j]) + subSampleFracts[k]*Math.abs(val);
						if (footwall)
							destArray[index] = -destArray[index];
						index++;
					}
				}
				prevVals[j] = val;
				destArray[index++] = val;
			}
		}
		return index;
	}
	
	private static class SampleDistributionTracker {
		
		private EvenlyDiscretizedFunc fwDist;
		private double fwSum = 0d;
		private int fwCount = 0;
		
		private EvenlyDiscretizedFunc hwDist;
		private double hwSum = 0d;
		private int hwCount = 0;
		
		private double minVal, maxVal;
		private double minCheck, maxCheck;
		
		public SampleDistributionTracker(double minVal, double maxVal, int numDiscretizations, boolean doHW) {
			fwDist = new EvenlyDiscretizedFunc(minVal, maxVal, numDiscretizations);
			if (doHW)
				hwDist = new EvenlyDiscretizedFunc(minVal, maxVal, numDiscretizations);
			this.minVal = minVal;
			this.maxVal = maxVal;
			minCheck = minVal-1e-2;
			maxCheck = maxVal+1e-2;
		}
		
		public void addValue(double sample) {
			boolean footwall = sample < 0d;
			if (footwall)
				sample = -sample;
			Preconditions.checkState(sample >= minCheck && sample <= maxCheck,
					"sample=%s out of bounds [%s, %s]", sample, minVal, maxVal);
			addValueRaw(sample, footwall);
		}
		
		private void addValueRaw(double sample, boolean footwall) {
			if (footwall || hwDist == null) {
				fwDist.add(fwDist.getClosestXIndex(sample), 1d);
				fwSum += sample;
				fwCount++;
			} else {
				hwDist.add(hwDist.getClosestXIndex(sample), 1d);
				hwSum += sample;
				hwCount++;
			}
		}
		
		public double getMean(boolean footwall) {
			if (footwall)
				return fwSum/fwDist.calcSumOfY_Vals();
			return hwSum/hwDist.calcSumOfY_Vals();
		}
		
		public double getFootwallFract() {
			return (double)fwCount/(double)(fwCount+hwCount);
		}
		
		public double[] calcFractiles(double[] fractiles, boolean footwall) {
			EvenlyDiscretizedFunc dist;
			double sum;
			int count;
			if (footwall) {
				dist = fwDist;
				sum = fwSum;
				count = fwCount;
			} else {
				dist = hwDist;
				sum = hwSum;
				count = hwCount;
			}
			if (count == 0)
				return null;
			EvenlyDiscretizedFunc invCmlDist = new EvenlyDiscretizedFunc(minVal, maxVal, fwDist.size());
			
			// convert to normCDF
			double invSum = 1d/(double)count;
			double runningSumY = 0d;
			for (int i=0; i<dist.size(); i++) {
				runningSumY += dist.getY(i);
				invCmlDist.set(i, runningSumY*invSum);
			}
			
			double minY = invCmlDist.getMinY();
			double maxY = invCmlDist.getMaxY();
			double[] ret = new double[fractiles.length];
			for (int i=0; i<fractiles.length; i++) {
				if (Double.isNaN(fractiles[i])) {
					// special case for mean
					ret[i] = sum/(double)count;
				} else {
					if (fractiles[i] <= minY)
						ret[i] = invCmlDist.getX(0);
					else if (fractiles[i] >= maxY)
						ret[i] = invCmlDist.getX(invCmlDist.size()-1);
					else
						ret[i] = invCmlDist.getFirstInterpolatedX(fractiles[i]);
				}
			}
			return ret;
		}
	}
	
	private static void addInterpolateSamplesBetween(SampleDistributionTracker sampleDist, double[] samples0, double[] samples1,
			double[] subSampleFracts, double[] oneMinusFracts) {
		for (int i=0; i<samples0.length; i++) {
			// j=1 because we're just doing the samples between
			for (int j=1; j<subSampleFracts.length; j++) {
				// simple linear interpolation; I tried cubic and saw no improvement
				boolean footwall;
				if (oneMinusFracts[j] >= subSampleFracts[j])
					footwall = samples0[i] < 0;
				else
					footwall = samples1[i] < 0;
				sampleDist.addValueRaw(oneMinusFracts[j]*Math.abs(samples0[i]) + subSampleFracts[j]*Math.abs(samples1[i]), footwall);
			}
		}
	}

}
