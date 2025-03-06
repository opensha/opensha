package org.opensha.sha.magdist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.eq.MagUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.common.primitives.Ints;

/**
 * Utility class that solves for a G-R equivalent MFD accounting for holes in available ruptures. Input is a
 * target b-value, total moment rate, and list of available rupture magnitudes. Output is a G-R equivalent using
 * only available magnitude bins.
 * <p>
 * The {@link SpreadingMethod} can be specified in order to control how rates are spread from bins without magnitudes
 * to bins with them, but NEAREST_GROUP seems to work well. 
 * @author kevin
 *
 */
public class SparseGutenbergRichterSolver {
	
	public enum SpreadingMethod {
		/**
		 * Rates for all empty bins are assigned to the nearest non-empty bin of lesser magnitude
		 */
		PREV,
		/**
		 * Rates for all empty bins are assigned to the nearest non-empty bin(s) on either side. If two bins are
		 * equally near (as would be the case for a single empty bin), rates are distributed equally above and below.
		 */
		NEAREST,
		/**
		 * Rates for all empty bins are evenly distributed to all non-empty bins
		 */
		ALL,
		/**
		 * Rates for all empty bins are assigned to the nearest contiguous group(s) of non-empty bins on either side.
		 * This conserves G-R behavior within groups of non-zero bins
		 */
		NEAREST_GROUP
	}
	
	public static boolean D = false;
	
	public static SpreadingMethod METHOD_DEFAULT = SpreadingMethod.NEAREST_GROUP;
	
	/**
	 * Calculates a G-R distribution for the given total moment rate and target b-value, only using magnitude bins
	 * that contain at least 1 rupture.
	 * 
	 * @param refFunc reference function (determines binning, returned G-R will have these x-values)
	 * @param mags available magnitudes for this G-R
	 * @param totMoRate total moment rate to fit
	 * @param targetBValue target b-value to fit
	 * @param magCorner corner magnitude for tapered GR if > 0
	 * @return G-R only using the given magnitude bins, fitting the total moment rate and b-value
	 */
	public static IncrementalMagFreqDist getEquivGR(EvenlyDiscretizedFunc refFunc, Collection<Double> mags,
			double totMoRate, double targetBValue, double magCorner) {
		return getEquivGR(refFunc, mags, null, false, totMoRate, targetBValue, magCorner);
	}
	
	/**
	 * Calculates a G-R distribution for the given total moment rate and target b-value, only using magnitude bins
	 * that contain at least 1 rupture.
	 * 
	 * @param refFunc reference function (determines binning, returned G-R will have these x-values)
	 * @param mags available magnitudes for this G-R
	 * @param groupBinEdges boundaries to not spread across
	 * @param groupEdgesIncreasingOnly if true, boundaries supplied will only be enforced when spreading to larger magnitudes
	 * @param totMoRate total moment rate to fit
	 * @param targetBValue target b-value to fit
	 * @param magCorner corner magnitude for tapered GR if > 0
	 * @return G-R only using the given magnitude bins, fitting the total moment rate and b-value
	 */
	public static IncrementalMagFreqDist getEquivGR(EvenlyDiscretizedFunc refFunc, Collection<Double> mags,
			Collection<Double> groupBinEdges, boolean groupEdgesIncreasingOnly, double totMoRate, double targetBValue, double magCorner) {
		return getEquivGR(refFunc, mags, groupBinEdges, groupEdgesIncreasingOnly, totMoRate, targetBValue, magCorner, 0d, METHOD_DEFAULT, false);
	}
	
	/**
	 * Calculates a G-R distribution for the given total moment rate and target b-value, only using magnitude bins
	 * that contain at least 1 rupture.
	 * 
	 * @param refFunc reference function (determines binning, returned G-R will have these x-values)
	 * @param mags available magnitudes for this G-R
	 * @param totMoRate total moment rate to fit
	 * @param targetBValue target b-value to fit
	 * @param magCorner corner magnitude for tapered GR if > 0
	 * @param sampleDiscr sampling to use when distributing rates. Small values here (compared to the reference gridding)
	 * will super-sample the distribution, which may be more accurate but doesn't seem necessary
	 * @param method method used to spread ruptures from empty bins to neighboring bins
	 * @param preserveRates if true, total event rate will be preserved (default preserves moment-rate)
	 * @return G-R only using the given magnitude bins, fitting the total moment rate and b-value
	 */
	public static IncrementalMagFreqDist getEquivGR(EvenlyDiscretizedFunc refFunc, Collection<Double> mags,
			double totMoRate, double targetBValue, double magCorner, double sampleDiscr, SpreadingMethod method, boolean preserveRates) {
		return getEquivGR(refFunc, mags, null, false, totMoRate, targetBValue, magCorner, sampleDiscr, method, preserveRates);
	}
	
	/**
	 * Calculates a G-R distribution for the given total moment rate and target b-value, only using magnitude bins
	 * that contain at least 1 rupture.
	 * 
	 * @param refFunc reference function (determines binning, returned G-R will have these x-values)
	 * @param mags available magnitudes for this G-R
	 * @param groupBinEdges boundaries to not spread across when NEAREST_GROUP is chosen
	 * @param groupEdgesIncreasingOnly if true, boundaries supplied will only be enforced when spreading to larger magnitudes
	 * @param totMoRate total moment rate to fit
	 * @param targetBValue target b-value to fit
	 * @param sampleDiscr sampling to use when distributing rates. Small values here (compared to the reference gridding)
	 * will super-sample the distribution, which may be more accurate but doesn't seem necessary
	 * @param method method used to spread ruptures from empty bins to neighboring bins
	 * @param preserveRates if true, total event rate will be preserved (default preserves moment-rate)
	 * @return G-R only using the given magnitude bins, fitting the total moment rate and b-value
	 */
	public static IncrementalMagFreqDist getEquivGR(EvenlyDiscretizedFunc refFunc, Collection<Double> mags,
			Collection<Double> groupBinEdges, boolean groupEdgesIncreasingOnly, double totMoRate, double targetBValue,
			double magCorner, double sampleDiscr, SpreadingMethod method, boolean preserveRates) {
		double minMag = Double.POSITIVE_INFINITY;
		double maxMag = Double.NEGATIVE_INFINITY;
		for (double mag : mags) {
			minMag = Math.min(minMag, mag);
			maxMag = Math.max(maxMag, mag);
		}
		int minIndex = refFunc.getClosestXIndex(minMag);
		int maxIndex = refFunc.getClosestXIndex(maxMag);
		
		if (minIndex == maxIndex) {
			// single bin, just populate that bin
			IncrementalMagFreqDist ret = new IncrementalMagFreqDist(refFunc.getMinX(), refFunc.size(), refFunc.getDelta());
			ret.set(minIndex, 1d);
			ret.scaleToTotalMomentRate(totMoRate);
			return ret;
		}
		
		// gridding for super-sampled function from min to max actual magnitude
		EvenlyDiscretizedFunc superSampledDiscretization;
		if (sampleDiscr >= refFunc.getDelta() || sampleDiscr == 0d)
			// don't super sample
			superSampledDiscretization = new EvenlyDiscretizedFunc(
					refFunc.getX(minIndex), 1+maxIndex-minIndex, refFunc.getDelta());
		else
			superSampledDiscretization = HistogramFunction.getEncompassingHistogram(
					minMag, maxMag, sampleDiscr);
		// true for each sampled mag bin if there is at least 1 rupture;
		boolean[] superSampledParticipation = new boolean[superSampledDiscretization.size()];
		for (double mag : mags)
			superSampledParticipation[superSampledDiscretization.getClosestXIndex(mag)] = true;
		
		IncrementalMagFreqDist ret = new IncrementalMagFreqDist(refFunc.getMinX(), refFunc.size(), refFunc.getDelta());
		
		IncrementalMagFreqDist superSampledGR;
		if (magCorner > 0d) {
			TaperedGR_MagFreqDist tapered = new TaperedGR_MagFreqDist(superSampledDiscretization.getMinX(), superSampledDiscretization.size(),
				superSampledDiscretization.getDelta());
			tapered.setAllButTotCumRate(superSampledDiscretization.getMinX(), magCorner, totMoRate, targetBValue);
			superSampledGR = tapered;
		} else {
			superSampledGR = new GutenbergRichterMagFreqDist(
					superSampledDiscretization.getMinX(), superSampledDiscretization.size(),
					superSampledDiscretization.getDelta(), totMoRate, targetBValue);
		}
		
		// first fill in all bins that have participation
		boolean allFilled = true;
		for (int i=0; i<superSampledGR.size(); i++) {
			if (superSampledParticipation[i])
				ret.add(ret.getClosestXIndex(superSampledGR.getX(i)), superSampledGR.getY(i));
			else
				allFilled = false;
		}
		
		double targetRate = superSampledGR.calcSumOfY_Vals();
		
		if (!allFilled) {
			// need to fill in the holes
			int prevNonEmptyIndex = -1;
			for (int i=0; i<superSampledGR.size(); i++) {
				if (superSampledParticipation[i]) {
					prevNonEmptyIndex = i;
				} else {
					Preconditions.checkState(i > 0 && i < superSampledGR.size()-1,
							"First and last bins of super-sampled should always have a rupture");
					List<int[]> assignmentGroups;
					
					if (method == SpreadingMethod.PREV) {
						assignmentGroups = List.of(new int[] {prevNonEmptyIndex});
					} else if (method == SpreadingMethod.NEAREST || method == SpreadingMethod.NEAREST_GROUP) {
						int numAway = 1;
						int[] assignedBins;
						while (true) {
							int upperIndex = i+numAway;
							int lowerIndex = i-numAway;
							boolean upper = upperIndex < superSampledParticipation.length && superSampledParticipation[upperIndex];
							boolean lower = lowerIndex >= 0 && superSampledParticipation[lowerIndex];
							if (upper || lower) {
								if (upper && lower)
									assignedBins = new int[] { lowerIndex, upperIndex};
								else if (upper)
									assignedBins = new int[] { upperIndex};
								else
									assignedBins = new int[] { lowerIndex};
								break;
							}
							numAway++;
						}
						if (method == SpreadingMethod.NEAREST_GROUP) {
							// expand to include the full contiguous group(s) of nonzero bins
							assignmentGroups = new ArrayList<>();
							for (int startBin : assignedBins) {
								int direction = startBin > i ? 1 : -1;
								List<Integer> group = new ArrayList<>();
								int prevBin = i;
								for (int bin=startBin;
										bin>=0 && bin <superSampledParticipation.length && superSampledParticipation[bin];
										bin+=direction) {
									if (groupBinEdges != null && !groupBinEdges.isEmpty()
											&& (direction > 0 || !groupEdgesIncreasingOnly)) {
										// see if we crossed any prescribed bin edges
										double magBefore = superSampledDiscretization.getX(prevBin);
										double magAfter = superSampledDiscretization.getX(bin);
										Range<Double> magRange;
										if (magBefore > magAfter)
											magRange = Range.closed(magAfter, magBefore);
										else
											magRange = Range.closed(magBefore, magAfter);
										boolean crosses = false;
										for (Double edge : groupBinEdges) {
											if (magRange.contains(edge)) {
												if (D) System.out.println("Crossed a bin edge "+edge+" from "+magBefore+" to "+magAfter);
												crosses = true;
												break;
											}
										}
										if (crosses) {
											// we have crossed a bin edge, break
											break;
										}
									}
									group.add(bin);
									prevBin = bin;
								}
								if (!group.isEmpty())
									assignmentGroups.add(Ints.toArray(group));
							}
						} else {
							assignmentGroups = List.of(assignedBins);
						}
					} else if (method == SpreadingMethod.ALL) {
						// dealt with externally
						assignmentGroups = List.of();
					} else {
						throw new IllegalStateException("Unsupported method: "+method);
					}
					
					double valPerGroup = preserveRates ? superSampledGR.getIncrRate(i) : superSampledGR.getMomentRate(i);
					valPerGroup /= (double)assignmentGroups.size();
					if (D)
						System.out.println("Spreading to "+assignmentGroups.size()+" groups");
					for (int[] assignedBins : assignmentGroups) {
						if (D) {
							System.out.print("Assignment group:");
							for (int bin : assignedBins)
								System.out.print(" "+bin+". "+(float)superSampledDiscretization.getX(bin));
							System.out.println();
						}
						if (preserveRates) {
							// simple
							double rateEachBin = valPerGroup/assignedBins.length;
							if (D)
								System.out.println("Adding "+rateEachBin+" to each bin");
							for (int assignedBin : assignedBins)
								ret.add(ret.getClosestXIndex(superSampledGR.getX(assignedBin)), rateEachBin);
						} else {
							if (assignedBins.length == 1) {
								// still simple, single bin
								int retIndex = ret.getClosestXIndex(superSampledGR.getX(assignedBins[0]));
								double targetMag = ret.getX(retIndex);
								double targetMo = MagUtils.magToMoment(targetMag);
								double myTargetRate = valPerGroup/targetMo;
								if (D)
									System.out.println("Adding "+myTargetRate+" to single bin");
								ret.add(retIndex, myTargetRate);
							} else {
								// more complicated, we want to shift the whole group up, preserving the slope
								int[] binIndexes = new int[assignedBins.length];
								double curMomentRateInBins = 0d;
								for (int j=0; j<assignedBins.length; j++) {
									binIndexes[j] = ret.getClosestXIndex(superSampledGR.getX(assignedBins[j]));
									double binMoment = ret.getMomentRate(binIndexes[j]);
									Preconditions.checkState(binMoment > 0d,
											"Bin %s has zero moment but should have had matching ruptures", assignedBins[j]);
									curMomentRateInBins += binMoment;
								}
								double newMomentRate = curMomentRateInBins + valPerGroup;
								double scalar = newMomentRate / curMomentRateInBins;
								for (int index : binIndexes) {
									if (D)
										System.out.println("Scaling bin "+index+". "+(float)ret.getX(index)
											+" by "+(float)newMomentRate+" / "+(float)curMomentRateInBins+" = "+(float)scalar);
									ret.set(index, ret.getY(index)*scalar);
								}
							}
						}
//						for (int assignedBin : assignedBins) {
//							if (preserveRates) {
//								// assign the rate to the given
//								double myTargetRate = ratePerAssignment;
//								ret.add(ret.getClosestXIndex(superSampledGR.getX(assignedBin)), myTargetRate);
//							} else {
//								// assign the moment to the given bin
////								double targetMag = superSampledGR.getX(assignedBin);
//								int retIndex = ret.getClosestXIndex(superSampledGR.getX(assignedBin));
//								double targetMag = ret.getX(retIndex);
//								double targetMo = MagUtils.magToMoment(targetMag);
//								double myTargetRate = moRatePerAssignment/targetMo;
//								ret.add(retIndex, myTargetRate);
//							}
//						}
					}
				}
			}
		}
		
		if (method == SpreadingMethod.ALL) {
			if (preserveRates)
				ret.scaleToCumRate(0, targetRate);
			else
				ret.scaleToTotalMomentRate(totMoRate);
		}
		
		if (preserveRates) {
			Preconditions.checkState((float)targetRate == (float)ret.calcSumOfY_Vals(), "Target rate mismatch: %s != %s",
				(float)targetRate, (float)ret.calcSumOfY_Vals());
		} else if (sampleDiscr >= refFunc.getDelta() || sampleDiscr == 0d) {
			// we didn't super-sample, so we can compare moment
			// we don't do this comparison if super-sampling is enabled because it integrates across the bin and is
			// thus more accurate, so they shouldn't match
			double retMoRate = ret.getTotalMomentRate();
			double pDiff = 100d*Math.abs(retMoRate - totMoRate)/totMoRate;
			// pass if either equal to floating point precision, or differences less than 0.001 % 
			Preconditions.checkState(pDiff < 0.001 || (float)targetRate == (float)retMoRate,
				"Target moment rate mismatch: %s != %s, pDiff=%s %, sampleDiscr=%s, refDelta=%s",
				(float)totMoRate, (float)retMoRate, (float)pDiff, sampleDiscr, refFunc.getDelta());
		}
		
		return ret;
	}

}
