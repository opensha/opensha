package org.opensha.sha.faultSurface.utils;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.WeightedList;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.utils.RjbDistributionDistanceCorrection.InvCDFCache;

import com.google.common.base.Preconditions;

public class SupersamplingRjbDistributionDistanceCorrection implements PointSourceDistanceCorrection {
	
	private double fractile;
	private int indexInCache;
	private InvCDFCache[] latCaches;
	
	public static WeightedList<SupersamplingRjbDistributionDistanceCorrection> getEvenlyWeightedFractiles(
			int numFractiles, double gridSpacingDegrees, int numCellSamples, boolean sampleAlong, boolean sampleDownDip) {
		Preconditions.checkState(numFractiles > 1);
		double[] fractiles = RjbDistributionDistanceCorrection.buildSpacedSamples(0d, 1d, numFractiles);
		InvCDFCache[] cache = initDefaultCache(fractiles, gridSpacingDegrees, numCellSamples, sampleAlong, sampleDownDip);
		SupersamplingRjbDistributionDistanceCorrection[] corrs = new SupersamplingRjbDistributionDistanceCorrection[numFractiles];
		for (int i=0; i<numFractiles; i++)
			corrs[i] = new SupersamplingRjbDistributionDistanceCorrection(fractiles[i], cache);
		return WeightedList.evenlyWeighted(corrs);
	}
	
	public static WeightedList<SupersamplingRjbDistributionDistanceCorrection> getImportanceSampledFractiles(
			double[] fractileBoundaries, double gridSpacingDegrees, int numCellSamples, boolean sampleAlong, boolean sampleDownDip) {
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
		InvCDFCache[] cache = initDefaultCache(fractiles, gridSpacingDegrees, numCellSamples, sampleAlong, sampleDownDip);
		for (int i=0; i<fractiles.length; i++)
			ret.add(new SupersamplingRjbDistributionDistanceCorrection(fractiles[i], cache), weights[i]);
		
		return ret;
	}

	public SupersamplingRjbDistributionDistanceCorrection(double fractile, double gridSpacingDegrees,
			int numCellSamples, boolean sampleAlong, boolean sampleDownDip) {
		this(fractile, initDefaultCache(new double[] {fractile}, gridSpacingDegrees, numCellSamples, sampleAlong, sampleDownDip));
	}
	
	static InvCDFCache[] initDefaultCache(double[] fractiles, double gridSpacingDegrees,
			int numCellSamples, boolean sampleAlong, boolean sampleDownDip) {
		InvCDFCache[] latCaches = new InvCDFCache[90]; // one for each degree of latitude, excluding 90 degrees
		for (int l=0; l<latCaches.length; l++)
			latCaches[l] = RjbDistributionDistanceCorrection.initSupersampledCache(fractiles, (double)l,
					gridSpacingDegrees, numCellSamples, sampleAlong, sampleDownDip);
		return latCaches;
	}

	public SupersamplingRjbDistributionDistanceCorrection(double fractile, InvCDFCache[] latCaches) {
		this.fractile = fractile;
		this.latCaches = latCaches;
		indexInCache = -1;
		for (int i=0; i<latCaches[0].fractiles.length; i++) {
			// use Double.compare because it handles NaNs
			if (Double.compare(fractile, latCaches[0].fractiles[i]) == 0) {
				indexInCache = i;
				break;
			}
		}
		Preconditions.checkState(indexInCache >= 0, "Fractile %s not found in cache", fractile);
	}

	@Override
	public double getCorrectedDistanceJB(double mag, PointSurface surf, double horzDist) {
		// round latitude to nearest degree
		double latitude = Math.round(surf.getLocation().lat);
		if (latitude < 0d)
			latitude = -latitude;
		latitude = Math.min(89, latitude); // cap at 89 degrees, things get weird at 0 degrees
		int latIndex = (int)latitude;
		Preconditions.checkState(latIndex < latCaches.length);
		
		if (horzDist < latCaches[latIndex].minNonzeroDist)
			return latCaches[latIndex].getZeroDistVals(surf.getAveLength(), surf.getAveWidth(), surf.getAveDip())[indexInCache];
		EvenlyDiscretizedFunc logDistFunc = latCaches[latIndex].getFractileFuncs(
				surf.getAveLength(), surf.getAveWidth(), surf.getAveDip(), horzDist)[indexInCache];
		double logDist = Math.log10(horzDist);
		return logDistFunc.getInterpolatedY(logDist);
	}
	
	@Override
	public String toString() {
		String str;
		if (Double.isNaN(fractile))
			str = "mean";
		else
			str = "p"+(float)(fractile*100d);
		if (Double.compare(fractile, latCaches[0].fractiles[0]) == 0)
			// first
			str = "Supersampled "+str+" (sampleAlong="+(latCaches[0].alongSamples.length > 1)
					+", sampleDownDip="+(latCaches[0].downDipSamples.length > 1)+")";
		return str;
	}

}
