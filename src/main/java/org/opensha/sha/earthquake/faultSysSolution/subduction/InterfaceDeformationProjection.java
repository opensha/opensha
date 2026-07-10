package org.opensha.sha.earthquake.faultSysSolution.subduction;

import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.statistics.distribution.ContinuousDistribution;
import org.opensha.commons.data.WeightedContinuousDistribution;
import org.opensha.commons.data.WeightedList;
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
 * Utilities for projecting horizontal convergence rates at the top of a subduction zone onto subsections
 */
public class InterfaceDeformationProjection {
	
	/**
	 * Utility method to detect flipped traces, e.g., if the deformation model was supplied in the opposite order.
	 * This just checks the direction of the supplied deformation front against the strike direction of the supplied
	 * trace and returns true if they are more than 90 degrees apart.
	 * 
	 * @param trace
	 * @param deformationFront
	 * @return true if the traces are flipped, false if they are in the same direction
	 */
	public static boolean areTracesFlipped(FaultTrace trace, LocationList deformationFront) {
		double deformationDirection = LocationUtils.azimuth(deformationFront.first(), deformationFront.last());
		double traceDirection = trace.getStrikeDirection();
		return FaultUtils.getAbsAngleDiff(traceDirection, deformationDirection) > 90d;
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
		int n = deformationFront.size();
		double[][] weights = getGaussianSmoothingWeights(deformationFront, sigma, maxDist);

		double[] smoothed = new double[n];

		for (int i=0; i<n; i++)
			for (int j=0; j<n; j++)
				if (weights[i][j] > 0d)
					smoothed[i] = Math.fma(deformationFrontSlipRates[j], weights[i][j], smoothed[i]);

		return smoothed;
	}
	
	/**
	 * Applies a Gaussian smoothing kernel to the deformation front slip rate distributions with sigma specified in km. Default
	 * behavior uses maxDist=2*sigma.
	 * @param deformationFront
	 * @param deformationFrontDists
	 * @param sigma smoothing kernel sigma (km)
	 * @return
	 */
	public static ContinuousDistribution[] getSmoothedDeformationFrontDistributions(
			LocationList deformationFront,
			ContinuousDistribution[] deformationFrontDists,
			double sigma) {
		return getSmoothedDeformationFrontDistributions(deformationFront, deformationFrontDists, sigma, sigma*2d);
	}
	
	/**
	 * Applies a Gaussian smoothing kernel to the deformation front slip rate distributions with sigma and maximum distance specified
	 * in km.
	 * @param deformationFront
	 * @param deformationFrontDists
	 * @param sigma smoothing kernel sigma (km)
	 * @param maxDist maximum distance (km)
	 * @return
	 */
	public static ContinuousDistribution[] getSmoothedDeformationFrontDistributions(
			LocationList deformationFront,
			ContinuousDistribution[] deformationFrontDists,
			double sigma,
			double maxDist) {
		Preconditions.checkState(deformationFront.size() == deformationFrontDists.length);
		int n = deformationFront.size();
		double[][] weights = getGaussianSmoothingWeights(deformationFront, sigma, maxDist);

		ContinuousDistribution[] smoothed = new ContinuousDistribution[n];

		for (int i=0; i<n; i++) {
			WeightedList<ContinuousDistribution> dists = new WeightedList<>();
			for (int j=0; j<n; j++)
				if (weights[i][j] > 0d)
					dists.add(deformationFrontDists[j], weights[i][j]);
			Preconditions.checkState(!dists.isEmpty());
			if (dists.size() == 1)
				smoothed[i] = dists.getValue(0);
			else
				smoothed[i] = new WeightedContinuousDistribution(dists);
		}

		return smoothed;
	}
	
	private static double[][] getGaussianSmoothingWeights(
			LocationList deformationFront,
			double sigma,
			double maxDist) {
		Preconditions.checkState(deformationFront.size() > 1);
		Preconditions.checkState(sigma > 0d);
		Preconditions.checkState(maxDist > 0d);

		int n = deformationFront.size();

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

		double[][] weights = new double[n][n];
		double twoSigmaSq = 2d*sigma*sigma;

		for (int i=0; i<n; i++) {
			double weightSum = 0d;

			double min = distsAlong[i] - maxDist;
			double max = distsAlong[i] + maxDist;

			for (int j=0; j<n && distsAlong[j] <= max; j++) {
				if (distsAlong[j] >= min) {
					double dist = distsAlong[j] - distsAlong[i];
					double kernelWeight = Math.exp(-(dist*dist)/twoSigmaSq);
					double weight = kernelWeight*lengthWeights[j];

					weights[i][j] = weight;
					weightSum += weight;
				}
			}

			Preconditions.checkState(weightSum > 0d);
			for (int j=0; j<n && distsAlong[j] <= max; j++)
				weights[i][j] /= weightSum;
		}

		return weights;
	}
	
	/**
	 * Projects the supplied deformation front and slip rates onto each subsection. This version assumes that the
	 * deformation front is perfectly trench-normal to each subsection, and the supplied slip rates are applied fully
	 * to each mapped subsection
	 * 
	 * @param subSections
	 * @param deformationFront
	 * @param deformationFrontSlipRates
	 */
	public static void projectSlipRates(List<? extends FaultSection> subSections, LocationList deformationFront,
			double[] deformationFrontSlipRates) {
		projectSlipRates(subSections, deformationFront, deformationFrontSlipRates, null, false, false);
	}
	
	/**
	 * Projects the supplied deformation front and slip rates onto each subsection. This version supports oblique
	 * convergence angles, which can either be reduced to their trench-normal component or retained.
	 * 
	 * This version also supports projection onto a dipping plane assuming a triangular block structure if projectForDip
	 * is true. That is likely not the correct choice for a curved interface, however, and is disabled by default.
	 * 
	 * @param subSections subsections to which slip rates will be added
	 * @param deformationFront the locations of the deformation front data
	 * @param deformationFrontSlipRates slip rates at each deformation front location
	 * @param convergenceAngles convergence angles for projection; if omitted, deformation front slip rates are assumed
	 * to be perfectly trench-normal to the mapped subsection
	 * @param includeObliquePortion if true, the oblique portion of the slip rate vector will be included in subseciton
	 * slip rates and the rake will be adjusted to match
	 * @param projectForDip if true, increase slip with subsections dip assuming a triangular block motion; probably not
	 * the correct choice for a curved interface
	 */
	public static void projectSlipRates(List<? extends FaultSection> subSections, LocationList deformationFront,
			double[] deformationFrontSlipRates, double[] convergenceAngles, boolean includeObliquePortion,
			boolean projectForDip) {
		Preconditions.checkState(deformationFront.size() > 1);
		Preconditions.checkState(deformationFront.size() == deformationFrontSlipRates.length);
		Preconditions.checkState(convergenceAngles == null || convergenceAngles.length == deformationFront.size());
		Preconditions.checkState(!includeObliquePortion || convergenceAngles != null);
		
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
						sumDist, deformationFrontSlipRates[flipIndex], distBefore);
				if (convergenceAngles == null)
					angle = Double.NaN;
				else
					angle = Interpolate.findY(0d, convergenceAngles[flipIndex-1],
							sumDist, convergenceAngles[flipIndex], distBefore);
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
				
				if (includeObliquePortion) {
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
			
			if (projectForDip) {
				double dip = sect.getAveDip();
				Preconditions.checkState(dip < 90d, "Dip must be <90 to project horizontal onto a dipping plane");
				// now project for dip
				// cos(dip) = horizontal / on-plane
				// on-plane = horizontal / cos(dip)
				slip *= 1d/Math.cos(Math.toRadians(dip));
			}
			
			sect.setAveSlipRate(slip);
			sect.setAveRake(rake);
		}
	}

}
