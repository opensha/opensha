package org.opensha.sha.earthquake.faultSysSolution.subduction;

import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.Interpolate;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;

/**
 * Utilities for projecting horizontal deformation at the top of a subduction zone onto subsections
 */
public class InterfaceDeformationProjection {
	
	public static void checkForTraceDirection(FaultTrace trace, LocationList deformationFront,
			double[] deformationFrontSlipRates) {
		double deformationDirection = LocationUtils.azimuth(deformationFront.first(), deformationFront.last());
		double traceDirection = trace.getStrikeDirection();
		if (FaultUtils.getAbsAngleDiff(traceDirection, deformationDirection) > 90d) {
			// reverse
			System.out.println("Deformation trace is reversed, flipping; deformation strike="+deformationDirection+", trace strike="+traceDirection);
			deformationFront.reverse();
			
			int start = 0;
			int end = deformationFrontSlipRates.length - 1;
			double temp;
			while (start < end) {
				// Swap elements at start and end
				temp = deformationFrontSlipRates[start];
				deformationFrontSlipRates[start] = deformationFrontSlipRates[end];
				deformationFrontSlipRates[end] = temp;

				// Move toward the center
				start++;
				end--;
			}
		}
	}
	
	/**
	 * Applies a Gaussian smoothing kernel to the deformation front slip rates with sigma specified in km. Default
	 * behavior uses maxDist=3*sigma.
	 * @param deformationFront
	 * @param deformationFrontSlipRates
	 * @param sigma smoothing kernel sigma (km)
	 * @return
	 */
	public static double[] getSmoothedDeformationFrontSlipRates(
			LocationList deformationFront,
			double[] deformationFrontSlipRates,
			double sigma) {
		return getSmoothedDeformationFrontSlipRates(deformationFront, deformationFrontSlipRates, sigma, sigma*3d);
	}
	
	/**
	 * Applies a Gaussian smoothing kernel to the deformation front slip rates with sigma and maximum distance specified
	 * in km.
	 * @param deformationFront
	 * @param deformationFrontSlipRates
	 * @param sigma smoothing kernel sigma (km)
	 * @param maxDist maximum distance (km)
	 * @return
	 */
	public static double[] getSmoothedDeformationFrontSlipRates(
			LocationList deformationFront,
			double[] deformationFrontSlipRates,
			double sigma,
			double maxDist) {
		Preconditions.checkState(deformationFront.size() == deformationFrontSlipRates.length);
		Preconditions.checkState(sigma > 0d);
		Preconditions.checkState(maxDist > 0d);

		int n = deformationFrontSlipRates.length;

		double[] distsAlong = new double[n];
		for (int i=1; i<n; i++) {
			distsAlong[i] = distsAlong[i-1]
					+ LocationUtils.horzDistanceFast(deformationFront.get(i-1), deformationFront.get(i));
		}

		double[] lengthWeights = new double[n];
		if (n == 1) {
			lengthWeights[0] = 1d;
		} else {
			lengthWeights[0] = 0.5*distsAlong[1];
			lengthWeights[n-1] = 0.5*(distsAlong[n-1] - distsAlong[n-2]);

			for (int i=1; i<n-1; i++) {
				lengthWeights[i] = 0.5*(distsAlong[i+1] - distsAlong[i-1]);
			}
		}

		double[] smoothed = new double[n];
		double twoSigmaSq = 2d*sigma*sigma;

		for (int i=0; i<n; i++) {
			double weightedSum = 0d;
			double weightSum = 0d;

			double min = distsAlong[i] - maxDist;
			double max = distsAlong[i] + maxDist;

			for (int j=0; j<n && distsAlong[j] <= max; j++) {
				if (distsAlong[j] >= min) {
					double dist = distsAlong[j] - distsAlong[i];
					double kernelWeight = Math.exp(-(dist*dist)/twoSigmaSq);
					double weight = kernelWeight*lengthWeights[j];

					weightedSum += deformationFrontSlipRates[j]*weight;
					weightSum += weight;
				}
			}

			Preconditions.checkState(weightSum > 0d);
			smoothed[i] = weightedSum/weightSum;
		}

		return smoothed;
	}
	
	public static void projectSlipRates(List<? extends FaultSection> subSections, LocationList deformationFront,
			double[] deformationFrontSlipRates) {
		projectSlipRates(subSections, deformationFront, deformationFrontSlipRates, null, false);
	}
	
	public static void projectSlipRates(List<? extends FaultSection> subSections, LocationList deformationFront,
			double[] deformationFrontSlipRates, double[] convergenceAngles, boolean includeOblique) {
		Preconditions.checkState(deformationFront.size() > 1);
		Preconditions.checkState(deformationFront.size() == deformationFrontSlipRates.length);
		Preconditions.checkState(convergenceAngles == null || convergenceAngles.length == deformationFront.size());
		
		for (FaultSection sect : subSections) {
			RuptureSurface surf = sect.getFaultSurface(1d);
			// use the middle trace
			FaultTrace middleTrace;
			if (surf instanceof EvenlyGriddedSurface) {
				EvenlyGriddedSurface gridSurf = (EvenlyGriddedSurface)surf;
				middleTrace = gridSurf.getRowAsTrace(gridSurf.getNumRows()/2);
			} else {
				throw new IllegalStateException("Not yet implemented");
			}
			Preconditions.checkState(middleTrace.size() > 1);
			
			double direction = middleTrace.getStrikeDirection();
			double upDipDirection = direction - 90d;
			Location middleLoc;
			if (middleTrace.size() % 2 == 0) {
				// even, average
				Location loc1 = middleTrace.get(middleTrace.size()/2 - 1);
				Location loc2 = middleTrace.get(middleTrace.size()/2);
				middleLoc = new Location(0.5*(loc1.lat + loc2.lat), 0.5*(loc1.lon + loc2.lon), 0.5*(loc1.depth + loc2.depth));
			} else {
				middleLoc = middleTrace.get(middleTrace.size() / 2);
			}
			Location upDipLoc = LocationUtils.location(middleLoc, Math.toRadians(upDipDirection), 10d);
			
			double[] traceDists = new double[deformationFront.size()];
			boolean allPositive = true;
			boolean allNegative = true;
			boolean prevPositive = false;
			int flipIndex = -1;
			for (int i=0; i<traceDists.length; i++) {
				traceDists[i] = LocationUtils.distanceToLineFast(middleLoc, upDipLoc, deformationFront.get(i));
				boolean positive = traceDists[i] >= 0;
				allPositive &= positive;
				allNegative &= !positive;
				if (i > 0) {
					boolean flip = positive != prevPositive;
					if (flip) {
						Preconditions.checkState(flipIndex < 0,
								"Up-dip projection intersects deformation front in multiple places");
						flipIndex = i;
					}
				}
				prevPositive = positive;
			}
			double slip;
			double angle;
			if (allPositive) {
				// all deformation locations are to the right of the line, use the first along strike
				double min = StatUtils.min(traceDists);
				Preconditions.checkState(traceDists[0] == min,
						"All def locs are right, expected first=%s to be min=%s (last is %s);"
						+ "\n\tLoc: %s\n\tFirst trace loc: %s",
						traceDists[0], min, traceDists[traceDists.length-1], middleLoc, deformationFront.first());
				slip = deformationFrontSlipRates[0];
				angle = convergenceAngles == null ? Double.NaN : convergenceAngles[0];
			} else if (allNegative) {
				// all deformation locations are to the left of the line, use the last along strike
				double max = StatUtils.max(traceDists);
				Preconditions.checkState(traceDists[traceDists.length-1] == max,
						"All def locs are left, expected last=%s to be max=%s (first is %s);"
						+ "\n\tLoc: %s\n\tLast trace loc: %s",
						traceDists[traceDists.length-1], max, traceDists[0], middleLoc, deformationFront.last());
				slip = deformationFrontSlipRates[traceDists.length-1];
				angle = convergenceAngles == null ? Double.NaN : convergenceAngles[traceDists.length-1];
			} else {
				Preconditions.checkState(flipIndex > 0); // can't be first (or negative)
				double distBefore = Math.abs(traceDists[flipIndex-1]);
				double distAfter = Math.abs(traceDists[flipIndex]);
				double sumDist = distBefore+distAfter;
				slip = Interpolate.findY(0d, deformationFrontSlipRates[flipIndex-1],
						distBefore+distAfter, deformationFrontSlipRates[flipIndex], distBefore);
				if (convergenceAngles == null)
					angle = Double.NaN;
				else
					angle = Interpolate.findY(0d, convergenceAngles[flipIndex-1],
							distBefore+distAfter, convergenceAngles[flipIndex], distBefore);
			}
			
			// before projecting down-dip, see if we need to correct for oblique 
			double rake;
			if (convergenceAngles != null) {
				// slip is not trench-normal
				
				// angle is in the opposite direction: toward the upper trace from the footwall side
				
				// this is in the up-dip direction; if exactly equal to up upDipDirection, then convergence is
				// perfectly trench-normal
				double reverseAngle = angle + 180d;
				
				double angleDiffFromNormal = FaultUtils.getSignedAngleDiff(reverseAngle, upDipDirection);
				Preconditions.checkState(Math.abs(angleDiffFromNormal) < 90d, "Convergence is more than 90 degrees off of trench-normal; "
						+ "upDip=%s, convergence=%s, flippedConvergence=%s, diff=%s",
						upDipDirection, angle, reverseAngle, angleDiffFromNormal);
				
				if (includeOblique) {
					// keep the full oblique slip, adjust rake angle
					
					// positive angleDiff means that the slip angle is right of the up-dip direction
					rake = 90-angleDiffFromNormal;
				} else {
					// keep only the trench-normal component of slip
					rake = 90d;
					slip *= Math.cos(Math.toRadians(angleDiffFromNormal));
				}
			} else {
				rake = 90; 
			}
			
			// now project for dip
			// cos(dip) = horizontal / on-plane
			// on-plane = horizontal / cos(dip)
			slip *= 1d/Math.cos(Math.toRadians(sect.getAveDip()));
			
			sect.setAveSlipRate(slip);
			sect.setAveRake(rake);
		}
	}

}
