package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

/**
 * Segmentation constraints are applied in the inversion as a fraction of the total participation
 * rate on either side of a jump. The segmentation model will often imply rates less than the G-R
 * budget for those ruptures, in which case we adjust the targets to account for the segmentation
 * model. We make the simplifying assumption that the inversion will use the most-available rupture
 * in each magnitude bin, so if a bin contains even a single rupture not affected by segmentation,
 * we assume that bin is not affected by segmentation.
 * <p>
 * For bins that are affected by segmentation, we first compute the model-implied participation
 * rate for each jump in that magnitude bin. That participation rate is the segmentation probability
 * times the lesser of the participation rate on either side of that jump. This is an upper bound:
 * the participation rate of ruptures on our section in this magnitude bin cannot exceed the
 * segmentation rate. We then convert that target participation rate to a nucleation rate,
 * and set the bin-specific rate to the lesser of the segmentation-implied target nucleation rate
 * and the original G-R nucleation rate before adjustment.
 * <p>
 * There are a few complications:
 * <p>
 * First of all, setting the rate on one segmentation-constrained section is dependent on the rate
 * of the sections on either side of the controlling jump, which could subsequently be updated with
 * their own adjustments. We mitigate this by does the corrections iteratively; 20 iterations is
 * plenty for MFD stability to 4-byte precision.
 * <p>
 * Second, multiple jumps often affect a single magnitude bin. In that case, we determine each
 * independent rupture path for that bin, such that there exist ruptures that use each path
 * and use no jumps from any other independent path. By maintaining independence, we can sum
 * the implied segmentation rate across independent paths.
 * <p>
 * Finally, individual jumps are often used by multiple magnitude bins. We have multiple ways
 * of dealing with this, see {@link MultiBinDistributionMethod}. The simplest method is
 * {@link MultiBinDistributionMethod#GREEDY}, in which each magnitude bin assumes that it can
 * fully utilize the available segmentation rate. Other methods are probably more plausible,
 * but also more complicated, so for now the greedy approach might be a good balance between
 * simplicity and complexity in order to the the minimum viable segmentation adjustment.
 * 
 * @author kevin
 *
 */
public class SegmentationImpliedSectNuclMFD_Estimator extends SectNucleationMFD_Estimator {
	
	private JumpProbabilityCalc segModel;
	
	private List<IncrementalMagFreqDist> estSectSupraSeisMFDs;
	private SectSegmentationJumpTracker[] sectSegTrackers;

	private double[] targetSectSupraMoRates;
	private double[] targetSectSupraSlipRates;

	private static MultiBinDistributionMethod BIN_DIST_METHOD_DEFAULT = MultiBinDistributionMethod.GREEDY;
	private MultiBinDistributionMethod binDistMethod;
	
	private static final int DEBUG_SECT = -1; // disabled
//	private static final int DEBUG_SECT = 1832; // Mojave N
//	private static final int DEBUG_SECT = 100; // Bicycle Lake
	private static final DecimalFormat eDF = new DecimalFormat("0.000E0");
	
	// how should we handle jumps that use multiple bins?
	public enum MultiBinDistributionMethod {
		/**
		 * Simplest method where each magnitude bin assumes that it has access to the entire segmentation constraint
		 * implied rate, even when multiple bins use the same jump
		 */
		GREEDY,
		/**
		 * Most conservative (probably overly so) approach where the rate associated with each jump is distributed
		 * across all bins utilizing that jump according to the original G-R proportions
		 */
		FULLY_DISTRIBUTED,
		/**
		 * Same as {@link #FULLY_DISTRIBUTED}, but fancier in that it saves any leftover segmentation implied rate
		 * that is above the G-R ceiling and redistributes it to larger magnitude bins that are deficient
		 */
		CAPPED_DISTRIBUTED,
	}

	public SegmentationImpliedSectNuclMFD_Estimator(JumpProbabilityCalc segModel) {
		this(segModel, BIN_DIST_METHOD_DEFAULT);
	}

	public SegmentationImpliedSectNuclMFD_Estimator(JumpProbabilityCalc segModel,
			MultiBinDistributionMethod binDistMethod) {
		this.segModel = segModel;
		this.binDistMethod = binDistMethod;
	}

	@Override
	public void init(FaultSystemRupSet rupSet, List<IncrementalMagFreqDist> origSectSupraSeisMFDs,
			double[] targetSectSupraMoRates, double[] targetSectSupraSlipRates, double[] sectSupraSlipRateStdDevs,
			List<BitSet> sectRupUtilizations, int[] sectMinMagIndexes, int[] sectMaxMagIndexes,
			int[][] sectRupInBinCounts, EvenlyDiscretizedFunc refMFD) {
		super.init(rupSet, origSectSupraSeisMFDs, targetSectSupraMoRates, targetSectSupraSlipRates,
				sectSupraSlipRateStdDevs, sectRupUtilizations, sectMinMagIndexes, sectMaxMagIndexes, sectRupInBinCounts, refMFD);
		
		this.targetSectSupraMoRates = targetSectSupraMoRates;
		this.targetSectSupraSlipRates = targetSectSupraSlipRates;
		int numSects = rupSet.getNumSections();
		
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);

		System.out.println("Pre-processing "+cRups.size()+" ruptures for segmentation calculation");
		
		// map from unique jumps to their segmentation probabilities
		Map<Jump, Double> segJumpProbsMap = new HashMap<>();
		
		// this will track what jumps control what magnitude bins on each section
		sectSegTrackers = new SectSegmentationJumpTracker[numSects];
		for (int s=0; s<numSects; s++)
			sectSegTrackers[s] = new SectSegmentationJumpTracker(refMFD.size());
		rupLoop:
			for (int r=0; r<cRups.size(); r++) {
				ClusterRupture rup = cRups.get(r);
				
				HashSet<Jump> jumps = new HashSet<>();
				double worstProb = 1d;
				for (Jump origJump : rup.getJumpsIterable()) {
					Jump jump;
					if (origJump.toSection.getSectionId() < origJump.fromSection.getSectionId())
						// always store jumps with fromID < toID
						jump = origJump.reverse();
					else
						jump = origJump;
					double jumpProb;
					if (segJumpProbsMap.containsKey(jump)) {
						jumpProb = segJumpProbsMap.get(jump);
					} else {
						jumpProb = segModel.calcJumpProbability(rup, origJump, false);
						Preconditions.checkState(jumpProb >= 0d && jumpProb <= 1d);
						segJumpProbsMap.put(jump, jumpProb);
					}
					if (jumpProb == 1d) {
						// ignore it
						continue;
					} else if (jumpProb == 0d) {
						// zero probability, can skip this rupture entirely
						continue rupLoop;
					}
					// we'll need to track this jump, it's between 0 and 1
					jumps.add(jump);
					worstProb = Math.min(worstProb, jumpProb);
				}

				double mag = rupSet.getMagForRup(r);
				int magIndex = refMFD.getClosestXIndex(mag);
				
				// tell each section about this rupture
				for (FaultSubsectionCluster cluster : rup.getClustersIterable())
					for (FaultSection sect : cluster.subSects)
						sectSegTrackers[sect.getSectionId()].processRupture(jumps, worstProb, magIndex);
			}
		
		System.out.println("Estimating section nucleation MFDs implied by "+segModel.getName());
		int segBins = 0;
		int segBinsAvail = 0;
		int segSects = 0; 
		
		// we're going to need section participation rates, which we will determine by multiplying
		// bin rates by the average rupture area in that bin, and then dividing by the section area.
		// 
		// these are section-sepecific scale factors
		double[][] sectNuclToParticScalars = new double[numSects][];
		for (int s=0; s<numSects; s++) {
			int minMagIndex = sectMinMagIndexes[s];
			int maxMagIndex = sectMaxMagIndexes[s];
			
			double[] nuclToParticScalars = new double[1+maxMagIndex-minMagIndex];
			double[] avgBinAreas = new double[nuclToParticScalars.length];
			int[] avgCounts = new int[avgBinAreas.length];
			
			BitSet utilization = sectRupUtilizations.get(s);
			// loop over ruptures for which this section participates
			for (int r = utilization.nextSetBit(0); r >= 0; r = utilization.nextSetBit(r+1)) {
				int index = refMFD.getClosestXIndex(rupSet.getMagForRup(r))-minMagIndex;
				avgCounts[index]++;
				avgBinAreas[index] += rupSet.getAreaForRup(r);
			}
			double sectArea = rupSet.getAreaForSection(s);
			for (int m=0; m<nuclToParticScalars.length; m++) {
				if (avgCounts[m] > 0) {
					avgBinAreas[m] /= avgCounts[m];
					nuclToParticScalars[m] = avgBinAreas[m]/sectArea;
				}
			}
			sectNuclToParticScalars[s] = nuclToParticScalars;
		}
		
		// create working copy of supra-seis mfds
		List<IncrementalMagFreqDist> modSupraSeisMFDs = new ArrayList<>();
		// also keep track of the original fraction that each bin contributes to the total nucleation rate
		List<double[]> origBinContributions = new ArrayList<>();
		for (int s=0; s<numSects; s++) {
			if (appliesTo(rupSet.getFaultSectionData(s))) {
				IncrementalMagFreqDist mfd = origSectSupraSeisMFDs.get(s).deepClone();
				modSupraSeisMFDs.add(mfd);
				double[] contribFracts = new double[mfd.size()];
				double totRate = mfd.calcSumOfY_Vals();
				for (int i=0; i<contribFracts.length; i++)
					contribFracts[i] = mfd.getY(i)/totRate;
				origBinContributions.add(contribFracts);
			} else {
				// section is not controlled by any seg-affected ruptures, or is zero rate
				modSupraSeisMFDs.add(null);
				origBinContributions.add(null);
			}
		}
		
		Map<Jump, Double> curJumpMaxParticipationRates = new HashMap<>();
		
		// iteratively solve for modified MFDs. 20 iterations is plenty to match nucleation rates with high
		// iteration counts (e.g., 200) to floating point precision, and is still nearly instantaneous
		for (int i=0; i<20; i++) {
			// calculate current nucleation and participation rates for each section at the start of
			// each iteration
			double[] curNuclRates = new double[numSects];
			double[] curParticRates = new double[numSects];
			for (int s=0; s<numSects; s++) {
				double[] nuclToParticScalars = sectNuclToParticScalars[s];
				IncrementalMagFreqDist supraMFD = origSectSupraSeisMFDs.get(s);
				for (int m=0; m<nuclToParticScalars.length; m++) {
					double binRate = supraMFD.getY(m + sectMinMagIndexes[s]);
					curNuclRates[s] += binRate;
					curParticRates[s] += binRate*nuclToParticScalars[m];
				}
				Preconditions.checkState(curNuclRates[s] > 0,
						"Bad curNuclRate=%s, iteration %s, s=%s, curParticRate=%s",
						curNuclRates[s], i, s, curParticRates[s]);
				Preconditions.checkState(curNuclRates[s] > 0,
						"Bad curParticRates=%s, iteration %s, s=%s, curNuclRates=%s",
						curParticRates[s], i, s, curNuclRates[s]);
			}
			
			// calculate the current jump participation rate, which is it's probability times the lower of
			// the participation rates on either side of the jump
			for (Jump jump : segJumpProbsMap.keySet()) {
				double jumpProb = segJumpProbsMap.get(jump);
				Preconditions.checkState(jumpProb > 0d);
				// lesser of the participation rate on either side of the jump
				double sectRate = Math.min(curParticRates[jump.fromSection.getSectionId()],
						curParticRates[jump.toSection.getSectionId()]);
				curJumpMaxParticipationRates.put(jump, jumpProb*sectRate);
			}
			
			// now apply segmentation adjustments
			for (int s=0; s<numSects; s++) {
				SectSegmentationJumpTracker tracker = sectSegTrackers[s];
				if (!appliesTo(rupSet.getFaultSectionData(s)))
					// section is not controlled by any seg-affected ruptures
					continue;
				
				boolean debug = s == DEBUG_SECT;
				
				// the original (unmodified) supra-seis MFD for this section
				IncrementalMagFreqDist origMFD = origSectSupraSeisMFDs.get(s);
				// our working copy of the supra-seis MFD
				IncrementalMagFreqDist modMFD = modSupraSeisMFDs.get(s);
				// original bin rates expressed as a fraction of total nucleation rate
				double[] origBinContribution = origBinContributions.get(s);
				// bin-specific scalars to go from nucleation rates to participation rates
				double[] nuclToParticScalars = sectNuclToParticScalars[s];
				
				if (debug) {
					System.out.println("Debug "+s+" iteration "+i);
					System.out.println("Pre-loop MFD");
					for (int j=sectMinMagIndexes[s]; j<=sectMaxMagIndexes[s]; j++)
						System.out.println("\t"+(float)modMFD.getX(j)+"\t"+(float)modMFD.getY(j));
				}

				Map<Jump, List<Integer>> fullJumpBins = tracker.getIndependentControllingJumpBinsMapping(
						curJumpMaxParticipationRates, sectMinMagIndexes[s]);
				
				// don't allow the rate in any bin to exceed its original G-R share of the total nucleation rate
				double[] ceilRates = new double[modMFD.size()];
				for (int m=0; m<modMFD.size(); m++)
					ceilRates[m] = origBinContribution[m]*curNuclRates[s];
				
				if (debug) {
					System.out.println("Ceiling rates:");
					for (int m=sectMinMagIndexes[s]; m<=sectMaxMagIndexes[s]; m++)
						System.out.print("\t"+(float)refMFD.getX(m)+"="+eDF.format(ceilRates[m]));
					System.out.println();
				}
				
				// available segmentation rate in each bin, summed over independent jumps operating in that bin
				double[] availSegRates = new double[ceilRates.length];
				
				Collection<Jump> jumps = fullJumpBins.keySet();
				
				if (binDistMethod == MultiBinDistributionMethod.CAPPED_DISTRIBUTED || debug) {
					// need to sort them such that jumps using larger magnitude bins are processed later
					jumps = new ArrayList<>(jumps);
					Collections.sort((List<Jump>)jumps, new Comparator<Jump>() {

						@Override
						public int compare(Jump o1, Jump o2) {
							List<Integer> bins1 = fullJumpBins.get(o1);
							List<Integer> bins2 = fullJumpBins.get(o2);
							int cmp = Integer.compare(bins1.get(bins1.size()-1), bins2.get(bins2.size()-1));
							if (cmp == 0)
								cmp = Integer.compare(bins1.get(0), bins2.get(0));
							return cmp;
						}
					});
				}
				
				boolean[] affectedBins = new boolean[ceilRates.length];
				
				for (Jump jump : jumps) {
					List<Integer> bins = fullJumpBins.get(jump);
					Preconditions.checkState(bins.size() > 0);
					for (int bin : bins)
						affectedBins[bin] = true;
					
					// this is our target segmentation implied participation rate for this jump, which we can't exceed
					double segParticTarget = curJumpMaxParticipationRates.get(jump);
					if (segParticTarget == 0d)
						// this jump uses zero-rates sections and can be ignored
						continue;
					
					if (debug)
						System.out.println("Jump: "+jump+"\tsegParticTarget="+segParticTarget);
					
					if (binDistMethod == MultiBinDistributionMethod.GREEDY) {
						// simplest method, but may not actually be consistent with the segmentation constraint
						
						// for each bin calculation, assume the best case scenario in which that bin is allowed to use
						// the entire segmentation rate (in order to make the minimum viable adjustment)
						for (int bin : bins)
							availSegRates[bin] += segParticTarget/nuclToParticScalars[bin-sectMinMagIndexes[s]];
					} else if (binDistMethod == MultiBinDistributionMethod.FULLY_DISTRIBUTED
							|| binDistMethod == MultiBinDistributionMethod.CAPPED_DISTRIBUTED) {
						// according to the original GR, figure out the fraction of the participation rate associated
						// with each bin for this jump
						double origTotJumpPartic = 0d;
						double[] origJumpBinPartics = new double[bins.size()];
						for (int b=0; b<origJumpBinPartics.length; b++) {
							int bin = bins.get(b);
							double origBinNuclRate = origMFD.getY(bin);
							double origBinParticRate = origBinNuclRate*nuclToParticScalars[bin-sectMinMagIndexes[s]];
							origTotJumpPartic += origBinParticRate;
							origJumpBinPartics[b] = origBinParticRate;
						}
						
						double excessPartic = 0d;
						for (int b=0; b<origJumpBinPartics.length; b++) {
							int bin = bins.get(b);
							// original bin fraction of the participation rate associated with this jump
							double origJumpBinParticFract = origJumpBinPartics[b]/origTotJumpPartic;
							// use that as a proportion to divide up the segmentation implied participation rate:
							// 		current fraction * total budget = budgeted amount in this bin
							double binTargetParticRate = segParticTarget*origJumpBinParticFract;
							// convert that budget back to a nucleation rate
							double binTargetNuclRate = binTargetParticRate/nuclToParticScalars[bin-sectMinMagIndexes[s]];
							
							if (binDistMethod == MultiBinDistributionMethod.CAPPED_DISTRIBUTED) {
								// check cap
								double newRate = availSegRates[bin] + binTargetNuclRate;
								if (newRate > ceilRates[bin]) {
									// we have some excess that we can redistribute at the end
									double excessNucl = newRate - ceilRates[bin];
									excessPartic += excessNucl * nuclToParticScalars[bin-sectMinMagIndexes[s]];
									newRate = ceilRates[bin];
								}
								availSegRates[bin] = newRate;
							} else {
								availSegRates[bin] += binTargetNuclRate;
							}
						}
						
						if (binDistMethod == MultiBinDistributionMethod.CAPPED_DISTRIBUTED && excessPartic > 0) {
							// we have excess rate to redistribute
							for (int bin : bins) {
								if (availSegRates[bin] < ceilRates[bin]) {
									// this bin has not yet hit the ceiling, fill it
									// the amount of nucleation rate to be filled
									double binAvailNucl = ceilRates[bin] - availSegRates[bin];
									// convert that to a participation rate
									double particScalar = nuclToParticScalars[bin-sectMinMagIndexes[s]];
									double binAvailPartic = binAvailNucl * particScalar;
									// take the lesser of the needed participation rate and that which we have available
									double utilizedPartic = Math.min(excessPartic, binAvailPartic);
									// convert back to a nucleation rate
									double utilizedNucl = utilizedPartic / particScalar;
									// apply it
									availSegRates[bin] += utilizedNucl;
									excessPartic -= utilizedPartic;
									if (excessPartic <= 0d)
										// <= here in case we end up with a tiny negative floating point error, e.g., -1e16
										break;
								}
							}
						}
					} else {
						throw new IllegalStateException("Unsupported bin distribution method: "+binDistMethod);
					}
					
					if (debug) {
						System.out.print("Avail mag bins:");
						for (int bin : bins)
							System.out.print(" "+(float)refMFD.getX(bin));
						System.out.println();
						System.out.println("Avail seg rates after jump:");
						for (int m=sectMinMagIndexes[s]; m<=sectMaxMagIndexes[s]; m++)
							System.out.print("\t"+(float)refMFD.getX(m)+"="+eDF.format(availSegRates[m])
									+(bins.contains(m) ? "*" : ""));
						System.out.println();
					}
				}
				
				boolean changed = false;
				
				for (int m=0; m<modMFD.size(); m++) {
					double prevRate = modMFD.getY(m);
					if (prevRate == 0d || !affectedBins[m])
						// empty or non-affected bin
						continue;
					
					double modRate = Math.min(ceilRates[m], availSegRates[m]);
					
//					Preconditions.checkState(modRate > 0, "Bad modRate=%s for bin %s, ceilRate=%s, sumImpliedRate=%s",
//							modRate, m, ceilRates[m], availSegRates[m]);
					
					changed = changed || modRate != prevRate;
					modMFD.set(m, modRate);
				}
				
				if (changed) {
					if (debug) {
						System.out.println("Post-loop MFD");
						for (int j=sectMinMagIndexes[s]; j<=sectMaxMagIndexes[s]; j++)
							System.out.println("\t"+(float)modMFD.getX(j)+"\t"+(float)modMFD.getY(j));
						System.out.println("\tWill scale mooment to match "+targetSectSupraMoRates[s]
								+" (cur="+modMFD.getTotalMomentRate()+")");
					}
					// now rescale the MFD to match the original target moment rate
					modMFD.scaleToTotalMomentRate(targetSectSupraMoRates[s]);
				}
			} // end section loop
		} // end iteration loop

		double sumOrigRate = 0d;
		double sumModRate = 0d;
		estSectSupraSeisMFDs = new ArrayList<>();
		for (int s=0; s<numSects; s++) {
			IncrementalMagFreqDist origMFD = origSectSupraSeisMFDs.get(s);
			sumOrigRate += origMFD.calcSumOfY_Vals();
			IncrementalMagFreqDist modMFD = modSupraSeisMFDs.get(s);
			if (modMFD == null) {
				sumModRate += origMFD.calcSumOfY_Vals();
				estSectSupraSeisMFDs.add(origMFD);
			} else {
				sumModRate += modMFD.calcSumOfY_Vals();
				int numChanged = 0;
				for (int i=0; i<modMFD.size(); i++)
					if ((float)modMFD.getY(i) != (float)origMFD.getY(i))
						numChanged++;
				segBins += numChanged;
				if (numChanged > 0)
					segSects++;
//				for (int[] bins : sectSegTrackers[s].getControllingJumpBinsMap(sectMinMagIndexes[s]).values())
//					segBinsAvail += bins.length;
				estSectSupraSeisMFDs.add(modMFD);
				segBinsAvail += sectSegTrackers[s].numControlledBins;
			}
		}
		
		System.out.println("Segmentation constraint affected "+segSects+"/"+numSects
				+" sections and "+segBins+"/"+segBinsAvail+" bins");
		double fractDiff = (sumModRate-sumOrigRate)/sumOrigRate;
		String pDiffStr = new DecimalFormat("0.00%").format(fractDiff);
		if (fractDiff > 0)
			pDiffStr = "+"+pDiffStr;
		System.out.println("\tTotal supra-seis rate change: "
				+(float)sumOrigRate+" -> "+(float)sumModRate+" ("+pDiffStr+")");
	}
	
	private static class SectSegmentationJumpTracker {
		
		private BitSet unityProbBins;
		private Map<Integer, Set<Set<Jump>>> binJumps;
		private int numControlledBins = 0;
		
		public SectSegmentationJumpTracker(int numBins) {
			this.unityProbBins = new BitSet(numBins);
			binJumps = new HashMap<>();
		}
		
		public boolean controlled() {
			return numControlledBins > 0;
		}
		
		public boolean notControlled() {
			return numControlledBins == 0;
		}
		
		/**
		 * Process the given rupture. If this bin already has segmentation-free ruptures, we can simply ignore it.
		 * If this is a segmentation-free rupture, we can discard anything else we were tracking for this magnitude
		 * bin. Otherwise, we want to keep track of the simplest paths in each magnitude bin. So if, for example,
		 * we have multiple paths in a bin but one is a subset of the other, keep the smaller one only.
		 * 
		 * @param jumps
		 * @param worstJumpProb
		 * @param magIndex
		 */
		public void processRupture(HashSet<Jump> jumps, double worstJumpProb, int magIndex) {
			if (unityProbBins.get(magIndex))
				// this bin already has a rupture without segmentation
				return;
			if (worstJumpProb == 1d) {
				// this rupture is not controlled by segmentation
				Set<Set<Jump>> prev = binJumps.remove(magIndex);
				if (prev != null)
					// this bin was controlled, now isn't
					numControlledBins--;
				unityProbBins.set(magIndex);
			} else {
				Preconditions.checkState(jumps != null && !jumps.isEmpty());
				Set<Set<Jump>> myBinJumps = binJumps.get(magIndex);
				if (myBinJumps == null) {
					// this bin is under control for the first time
					numControlledBins++;
					myBinJumps = new HashSet<>();
					binJumps.put(magIndex, myBinJumps);
				} else {
					// see if we can eliminate any at this stage that we know we will never use
					
					if (myBinJumps.contains(jumps))
						// duplicate, can skip
						return;
					
					// if this is a superset of any already existing path, then we can ignore it as we assume that the
					// inversion will always use the easier (smaller) set
					
					int mySize = jumps.size();
					for (Set<Jump> otherPath : myBinJumps) {
						if (mySize > otherPath.size() && jumps.containsAll(otherPath)) {
							// this is a superset of a previous path in this magnitude bin, skip it and keep the old one
							return;
						}
					}
					
					// if this is a subset of any other already existing path, then we should keep this one and evict
					// the more complicated longer path, again assuming that the inversion will always use the easier
					// path (with fewer jumps)
					
					List<Set<Jump>> supersets = new ArrayList<>();
					for (Set<Jump> otherPath : myBinJumps) {
						if (otherPath.size() > mySize && otherPath.containsAll(jumps))
							// we are a new easier subset of this previous path, remove that path
							supersets.add(otherPath);
					}
					for (Set<Jump> superset : supersets)
						myBinJumps.remove(superset);
				}
				
				myBinJumps.add(jumps);
			}
		}
		
		/**
		 * This calculates, for each magnitude bin, the set of independent jumps that control segmentation.
		 * 
		 * Independent here means that there are ruptures in a given magnitude bin that utilize a given jump, and those
		 * ruptures don't utilize the same jumps as those from any other independent jump.
		 * 
		 * Controlling here means the jump with the lowest available rate that can be consumed for a given rupture.
		 * 
		 * @param curJumpMaxParticipationRates
		 * @param sectMinIndex
		 * @return
		 */
		public Map<Jump, List<Integer>> getIndependentControllingJumpBinsMapping(
				Map<Jump, Double> curJumpMaxParticipationRates, int sectMinIndex) {
			Preconditions.checkState(controlled());
			Map<Jump, List<Integer>> map = new HashMap<>();
			for (int index : binJumps.keySet()) {
				if (index < sectMinIndex)
					continue;
				Set<Set<Jump>> myBinJumps = binJumps.get(index);
				
				// reduce each set to the worst jump based on the current participation rates, keeping a set of
				// independent jumps that control this bin
				
				// this is where we'll keep a set of truly independent jumps that control segmentation for this section
				Map<Jump, Set<Jump>> indepControllingJumps = new HashMap<>();
				
				// process them in increasing size order, which will ensure that we keep any smaller isolated paths
				List<Set<Jump>> sortedBinJumps = new ArrayList<>(myBinJumps);
				Collections.sort(sortedBinJumps, collectionSizeComparator);
				
				for (Set<Jump> jumps : sortedBinJumps) {
					// each set here represents a unique path: ruptures that use this particular set of jumps and lie 
					// in this mag bin
					Preconditions.checkState(!jumps.isEmpty());
					
					// jump with the lowest available rate controls within any path
					Jump controllingJump = null;
					double minRate = Double.POSITIVE_INFINITY;
					for (Jump jump : jumps) {
						double rate = curJumpMaxParticipationRates.get(jump);
						if (rate < minRate) {
							minRate = rate;
							controllingJump = jump;
						}
					}
					
					if (indepControllingJumps.containsKey(controllingJump)) {
						// we have already processed this jump, and can skip it as these paths are sorted and we only
						// want to keep the smallest one (in hopes that we might later find another independent path)
						
						// do nothing
					} else {
						// this is a new one, see if it intersects any previous ones
						List<Jump> evictions = new ArrayList<>();
						boolean superceded = false;
						for (Jump prevControlling : indepControllingJumps.keySet()) {
							Set<Jump> prevDependent = indepControllingJumps.get(prevControlling);
							boolean intersects = false;
							for (Jump jump : jumps) {
								if (prevDependent.contains(jump)) {
									// these paths intersect
									intersects = true;
									break;
								}
							}
							if (intersects) {
								// we intersect a previous path
								if (minRate > curJumpMaxParticipationRates.get(prevControlling)) {
									// our new path is better, evict this one
									evictions.add(prevControlling);
								} else {
									// the old path was better, skip this one
									superceded = true;
									break;
								}
							}
						}
						if (superceded) {
							// we're superseded by a prior path, skip this one. also don't process any of the planned
							// evictions, as those paths are independent to the one that supersedes us.
							continue;
						}
						for (Jump jump : evictions)
							Preconditions.checkNotNull(indepControllingJumps.remove(jump));
						// we're independent or a new best path
						indepControllingJumps.put(controllingJump, jumps);
					}
				}
				
				for (Jump jump : indepControllingJumps.keySet()) {
					List<Integer> list = map.get(jump);
					if (list == null) {
						list = new ArrayList<>();
						map.put(jump, list);
					}
					list.add(index);
				}
			}
			for (List<Integer> list : map.values())
				Collections.sort(list);
			return map;
		}
	}
	
	private static final Comparator<Collection<?>> collectionSizeComparator = new Comparator<>() {

		@Override
		public int compare(Collection<?> o1, Collection<?> o2) {
			return Integer.compare(o1.size(), o2.size());
		}
		
	};

	@Override
	public boolean appliesTo(FaultSection sect) {
		Preconditions.checkNotNull(sectSegTrackers, "Not initialized");
		int s = sect.getSectionId();
		return !(sectSegTrackers[s].notControlled() || targetSectSupraMoRates[s] == 0d
				|| targetSectSupraSlipRates[s] == 0d);
	}

	@Override
	public IncrementalMagFreqDist estimateNuclMFD(FaultSection sect, IncrementalMagFreqDist curSectSupraSeisMFD,
			List<Integer> availableRupIndexes, List<Double> availableRupMags, UncertainDataConstraint sectMomentRate,
			boolean sparseGR) {
		Preconditions.checkNotNull(estSectSupraSeisMFDs, "Not initialized");
		return estSectSupraSeisMFDs.get(sect.getSectionId());
	}

}
