package org.opensha.sha.faultSurface.utils;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.Interpolate;
import org.opensha.sha.faultSurface.FiniteApproxPointSurface;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.utils.RjbDistributionDistanceCorrection.InvCDFCache;

import com.google.common.base.Preconditions;

public class SupersamplingRjbDistributionDistanceCorrection implements PointSourceDistanceCorrection {
	
	private double fractile;
	private int indexInCache;
	private double[] betaRads;
	private InvCDFCache[][] latCaches;
	
	public static WeightedList<SupersamplingRjbDistributionDistanceCorrection> getEvenlyWeightedFractiles(
			int numFractiles, double gridSpacingDegrees, int numCellSamples, int numBetaSamples, boolean sampleAlong, boolean sampleDownDip) {
		Preconditions.checkState(numFractiles > 1);
		double[] fractiles = RjbDistributionDistanceCorrection.buildSpacedSamples(0d, 1d, numFractiles);
		InvCDFCache[][] cache = initDefaultCache(fractiles, gridSpacingDegrees, numBetaSamples, numCellSamples, sampleAlong, sampleDownDip);
		SupersamplingRjbDistributionDistanceCorrection[] corrs = new SupersamplingRjbDistributionDistanceCorrection[numFractiles];
		for (int i=0; i<numFractiles; i++)
			corrs[i] = new SupersamplingRjbDistributionDistanceCorrection(fractiles[i], cache);
		return WeightedList.evenlyWeighted(corrs);
	}
	
	public static WeightedList<SupersamplingRjbDistributionDistanceCorrection> getImportanceSampledFractiles(
			double[] fractileBoundaries, double gridSpacingDegrees, int numCellSamples, int numBetaSamples, boolean sampleAlong, boolean sampleDownDip) {
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
		WeightedList<SupersamplingRjbDistributionDistanceCorrection> ret = new WeightedList<>(fractiles.length);
		InvCDFCache[][] cache = initDefaultCache(fractiles, gridSpacingDegrees, numBetaSamples, numCellSamples, sampleAlong, sampleDownDip);
		for (int i=0; i<fractiles.length; i++)
			ret.add(new SupersamplingRjbDistributionDistanceCorrection(fractiles[i], cache), weights[i]);
		
		return ret;
	}

//	public SupersamplingRjbDistributionDistanceCorrection(double fractile, double gridSpacingDegrees, double beta,
//			int numCellSamples, boolean sampleAlong, boolean sampleDownDip) {
//		this(fractile, initDefaultCache(new double[] {fractile}, gridSpacingDegrees, beta, numCellSamples, sampleAlong, sampleDownDip));
//	}
	
	static InvCDFCache[][] initDefaultCache(double[] fractiles, double gridSpacingDegrees, int numBetaSamples,
			int numCellSamples, boolean sampleAlong, boolean sampleDownDip) {
		InvCDFCache[][] latCaches = new InvCDFCache[90][numBetaSamples]; // one for each degree of latitude, excluding 90 degrees
		double[] betas = RjbDistributionDistanceCorrection.buildSpacedSamples(0d, 90d, numBetaSamples, true);
//		System.out.println("Beta samples: "+RjbDistributionDistanceCorrection.getSampleStr(betas));
		for (int l=0; l<latCaches.length; l++)
			for (int b=0; b<numBetaSamples; b++)
				latCaches[l][b] = RjbDistributionDistanceCorrection.initSupersampledCache(fractiles, (double)l,
						betas[b], gridSpacingDegrees, numCellSamples, sampleAlong, sampleDownDip);
		return latCaches;
	}

	public SupersamplingRjbDistributionDistanceCorrection(double fractile, InvCDFCache[][] latCaches) {
		this.fractile = fractile;
		this.latCaches = latCaches;
		this.betaRads = new double[latCaches[0].length];
		for (int b=0; b<betaRads.length; b++)
			betaRads[b] = latCaches[0][b].betaRad;
		indexInCache = -1;
		for (int i=0; i<latCaches[0][0].fractiles.length; i++) {
			// use Double.compare because it handles NaNs
			if (Double.compare(fractile, latCaches[0][0].fractiles[i]) == 0) {
				indexInCache = i;
				break;
			}
		}
		Preconditions.checkState(indexInCache >= 0, "Fractile %s not found in cache", fractile);
	}
	
	private static final double PI_HALF = 0.5*Math.PI;
	private static final double THREE_PI_HALF = 1.5*Math.PI;
	private static final double TWO_PI = 2d*Math.PI;

	@Override
	public double getCorrectedDistanceJB(Location siteLoc, double mag, PointSurface surf, double horzDist) {
		Location surfLoc = surf.getLocation();
		double azRad = LocationUtils.azimuthRad(surfLoc, siteLoc) % TWO_PI;
		double betaRad = azRad % Math.PI;
		if (betaRad > PI_HALF)
			betaRad = Math.PI-betaRad;
		
		
		int betaIndexBelow, betaIndexAbove;
		if (Precision.equals(betaRad, betaRads[0], 0.0001)) {
			betaIndexBelow = 0;
			betaIndexAbove = 0;
		} else if (Precision.equals(betaRad, betaRads[betaRads.length-1], 0.0001)) {
			betaIndexBelow = betaRads.length-1;
			betaIndexAbove = betaRads.length-1;
		} else {
			betaIndexBelow = 0;
			for (int b=1; b<betaRads.length; b++) {
				if ((float)betaRads[b] < (float)betaRad)
					betaIndexBelow++;
				else
					break;
			}
			Preconditions.checkState(betaIndexBelow < betaRads.length-1,
					"bad betaIndexBelow=%s for azRad=%s, betaRad=%s, and numBeta=%s, maxBetaRad=%s",
					betaIndexBelow, (float)azRad, (float)betaRad, betaRads.length, (float)betaRads[betaRads.length-1]);
			betaIndexAbove = betaIndexBelow+1;
			Preconditions.checkState((float)betaRad >= (float)(betaRads[betaIndexBelow])
					&& (float)betaRad <= (float)(betaRads[betaIndexAbove]));
		}
		
		double latitude = surfLoc.lat;
		// if negative, mirror across the equator
		if (latitude < 0d)
			latitude = -latitude;
		// cap at 89 degrees, things get weird at 90 degrees
		latitude = Math.min(89, latitude);
		int latIndexAbove = (int)Math.ceil(latitude);
		int latIndexBelow = (int)Math.floor(latitude);
		Preconditions.checkState(latIndexAbove < latCaches.length);
		Preconditions.checkState(latIndexBelow >= 0);
		
		if (betaIndexBelow == betaIndexAbove)
			return calcForLatBetaIndex(surf, horzDist, latitude, latIndexBelow, latIndexAbove, betaIndexAbove);
		
		double valBetaAbove = calcForLatBetaIndex(surf, horzDist, latitude, latIndexBelow, latIndexAbove, betaIndexAbove);
		double valBetaBelow = calcForLatBetaIndex(surf, horzDist, latitude, latIndexBelow, latIndexAbove, betaIndexBelow);
		
		double ret = Interpolate.findY(betaRads[betaIndexBelow], valBetaBelow, betaRads[betaIndexAbove], valBetaAbove, betaRad);
		if (ret < 0d) {
			// sometimes the interpolation leads to tiny negative values, force positive
			Preconditions.checkState(ret > -1e-10);
			return 0d;
		}
		Preconditions.checkState(ret >= 0d, "Bad dist=%s for betaRad=%s, valBetaBelow=%s, valBentaAbove=%s",
				ret, betaRad, valBetaBelow, valBetaAbove);
		return ret;
	}
	
	private double calcForLatBetaIndex(PointSurface surf, double horzDist,
			double latitude, int latIndexBelow, int latIndexAbove, int betaIndex) {

		if (latIndexAbove == latIndexBelow) {
			// no lat interpolation needed
			return RjbDistributionDistanceCorrection.getCachedDist(surf, horzDist, latCaches[latIndexAbove][betaIndex], indexInCache);
		}
		
		double valLatAbove = RjbDistributionDistanceCorrection.getCachedDist(surf, horzDist, latCaches[latIndexAbove][betaIndex], indexInCache);
		double valLatBelow = RjbDistributionDistanceCorrection.getCachedDist(surf, horzDist, latCaches[latIndexBelow][betaIndex], indexInCache);
		return Interpolate.findY(latIndexBelow, valLatBelow, latIndexAbove, valLatAbove, latitude);
	}
	
	@Override
	public String toString() {
		String str;
		if (Double.isNaN(fractile))
			str = "mean";
		else
			str = "p"+(float)(fractile*100d);
		if (Double.compare(fractile, latCaches[0][0].fractiles[0]) == 0)
			// first
			str = "Supersampled "+str+" (numBeta="+latCaches[0].length+", sampleAlong="+(latCaches[0][0].alongSamples.length > 1)
					+", sampleDownDip="+(latCaches[0][0].downDipSamples.length > 1)+")";
		return str;
	}
	
	/**
	 * Returns distance from center of a rectangle * to its boundary along direction azimuth (radians),
	 * where azimuth=0 means +Y direction, azimuth=pi/2 means +X direction.
	 *
	 * Rectangle is centered at (0,0).
	 */
	public static double rectBoundaryDistFromCenter_YAxis(
			double width, double height, double azimuthRad) {
		double eps = 1e-14;

		// direction vector = (sin(alpha), cos(alpha))
		double sinA = Math.abs(Math.sin(azimuthRad));
		double cosA = Math.abs(Math.cos(azimuthRad));

		// if cosA=0 => direction is horizontal => +X or -X => distance= dx/2
		if (cosA<eps) {
			// alpha ~ pi/2 or 3pi/2 => purely horizontal
			return 0.5*width;
		}

		// if sinA=0 => direction is vertical => +Y or -Y => distance= dy/2
		if (sinA<eps) {
			// alpha=0 or pi => purely vertical
			return 0.5*height;
		}

		// general case
		double tx = (0.5*width)/sinA;
		double ty = (0.5*height)/cosA;

		return Math.min(tx, ty);
	}
	
	public static void main(String[] args) {
		PointSurface surf = new FiniteApproxPointSurface(new Location(0d, 0d), 90d, 0, 10, false, 0d);
		double[] fractiles = {0d, 0.5d, 1d};
		InvCDFCache[][] caches = initDefaultCache(fractiles, 0.1, 5, 111, false, false);
		SupersamplingRjbDistributionDistanceCorrection[] corrs = new SupersamplingRjbDistributionDistanceCorrection[fractiles.length];
		for (int f=0; f<fractiles.length; f++)
			corrs[f] = new SupersamplingRjbDistributionDistanceCorrection(fractiles[f], caches);
		double cellW = LocationUtils.horzDistance(new Location(0d, -0.05), new Location(0d, 0.05));
		double cellH = LocationUtils.horzDistance(new Location(-0.05, 0d), new Location(0.05, 0d));
		System.out.println("Cell dimensions: "+(float)cellW+" x "+(float)cellH);
		System.out.println("Cell diagonal: "+(float)Math.sqrt(cellW*cellW + cellH*cellH));
		Location siteLoc = new Location(0.05d, 0.05d);
//		Location siteLoc = new Location(0.0d, 0.05d);
//		Location siteLoc = LocationUtils.location(surf.getLocation(), Math.PI/4d,
//				LocationUtils.linearDistance(surf.getLocation(), new Location(0.05d, 0d)));
		double horzDist = LocationUtils.horzDistanceFast(siteLoc, surf.getLocation());
		System.out.println("Dist to center is "+(float)horzDist);
		System.out.println("rJB dist:");
		for (int i=0; i<corrs.length; i++) {
			SupersamplingRjbDistributionDistanceCorrection corr = corrs[i];
			double dist = corr.getCorrectedDistanceJB(siteLoc, 0d, surf, horzDist);
			System.out.println("\tp"+(float)(100d*corr.fractile)+":\t"+(float)dist);
		}
	}

}
