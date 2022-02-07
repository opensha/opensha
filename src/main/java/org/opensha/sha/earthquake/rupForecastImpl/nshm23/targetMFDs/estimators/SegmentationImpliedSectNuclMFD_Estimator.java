package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
 * in each magnitude bin, so if a bin contains ruptures not affected by segmentation, we assume
 * that bin is not affected by segmentation.
 * 
 * For bins that are affected by segmentation, we first compute the model-implied participation
 * rate for each jump in that magnitude bin. That participation rate is the segmentation probability
 * times the lesser of the participation rate on either side of that jump. This is an upper bound:
 * the participation rate of ruptures on our section in this magnitude bin cannot exceed the
 * segmentation rate. We then convert that target participation rate to a nucleation rate,
 * and set the bin-specific rate to the lesser of the segmentation-implied target nucleation rate
 * and the original nucleation rate before adjustment.
 * 
 * There are a couple complications:
 * 
 * First of all, setting the rate on one section relies on the rate of other sections, which could
 * subsequently be updated with their own adjustments. We mitigate this by does the corrections
 * iteratively; 20 iterations is plenty for MFD stability to 4-byte precision.
 * 
 * Second, multiple jumps can affect a single magnitude bin. In that case, we first compute the
 * target nucleation rate for each bin implied by each jump, and then choose the greatest one
 * as our actual target (again assuming that the inversion will use the most-available ruptures).
 * @author kevin
 *
 */
public class SegmentationImpliedSectNuclMFD_Estimator extends SectNucleationMFD_Estimator {
	
	private JumpProbabilityCalc segModel;
	
	private List<IncrementalMagFreqDist> estSectSupraSeisMFDs;
	private SectSegmentationJumpTracker[] sectSegTrackers;

	private double[] targetSectSupraMoRates;
	private double[] targetSectSupraSlipRates;

	public SegmentationImpliedSectNuclMFD_Estimator(JumpProbabilityCalc segModel) {
		this.segModel = segModel;
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
				
				// within a single rupture, the worst jump will control
				Jump worstJump = null;
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
					if (jumpProb < worstProb) {
						// new worst
						worstProb = jumpProb;
						worstJump = jump;
					}
				}

				double mag = rupSet.getMagForRup(r);
				int magIndex = refMFD.getClosestXIndex(mag);
				
				// tell each section about this rupture
				for (FaultSubsectionCluster cluster : rup.getClustersIterable())
					for (FaultSection sect : cluster.subSects)
						sectSegTrackers[sect.getSectionId()].processRupture(worstJump, worstProb, magIndex);
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
				
//				boolean debug = s == 1832;
				boolean debug = false;
				
				// our woking copy of the supra-seis MFD
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
				
				boolean changed = false;
				
				Jump[] assignedJumps = new Jump[1+sectMaxMagIndexes[s]-sectMinMagIndexes[s]];
				double[] assignedJumpRates = new double[assignedJumps.length];
				Map<Jump, List<Integer>> fullJumpBins = tracker.getJumpBinsMap(sectMinMagIndexes[s]);
				Map<Jump, List<Integer>> jumpBinAssignments = null;
				
				/*
				 * Loop through the calculation twice. The first time, we're deciding which jump is the
				 * bottleneck for each bin for the case of multiple jumps affecting a single bin. In the
				 * second time through, we actually modify the rates. 
				 */
				for (boolean assignment : new boolean[] { true, false } ) {
					// map of jumps to affected bins
					Map<Jump, List<Integer>> jumpBins = assignment ? fullJumpBins : jumpBinAssignments;
					
					for (Jump jump : jumpBins.keySet()) {
						List<Integer> bins = jumpBins.get(jump);
						Preconditions.checkState(bins.size() > 0);
						
						// this is our target segmentation implied participation rate for this jump, which
						// we can't exceed
						double segParticTarget = curJumpMaxParticipationRates.get(jump);
						if (segParticTarget == 0d)
							// this jump uses zero-rates sections and can be ignored
							continue;
						
						// calculate the current nucleation and participation rates for these bins
						double curJumpNuclRate = 0d;
						double curJumpParticRate = 0d;
						for (int bin : bins) {
							double binRate = modMFD.getY(bin);
							curJumpNuclRate += binRate;
//							System.out.println("bin="+bin+", minMag="+sectMinMagIndexes[s]+", refMFD.size()="
//									+refMFD.size()+", tracker.binOffset="+tracker.binOffset);
							curJumpParticRate += binRate*nuclToParticScalars[bin-sectMinMagIndexes[s]];
						}
						
//						System.out.println(i+". s="+s+", jump="+jump);
						
						Preconditions.checkState(curJumpNuclRate > 0,
								"Jump nucleation rate is 0! bins=%s,\n\t%s", bins, modMFD);
						
						// convert that participation target to a nucleation rate on this section
						double segNuclTarget = 0d;
						// also keep track of the bin-specific nucleation rate target for this constraint
						List<Double> binnedSegTargetNuclRates = new ArrayList<>();
						for (int bin : bins) {
							double binRate = modMFD.getY(bin);
							double particScalar = nuclToParticScalars[bin-sectMinMagIndexes[s]];
							// fraction of the participation rate this bin is responsible for
							double binParticFract = binRate*particScalar/curJumpParticRate;
							double binTargetParticRate = segParticTarget*binParticFract;
							double binTargetNuclRate = binTargetParticRate/particScalar;
							segNuclTarget += binTargetNuclRate;
							binnedSegTargetNuclRates.add(binTargetNuclRate);
						}
						Preconditions.checkState(segNuclTarget > 0d,
								"Segmentation implied nucleation rate is zero for s=%s, jump=%s, "
								+ "segParticTarget=%s\n\tmodMFD: %s",
								s, jump, segParticTarget, modMFD);
						
						if (assignment) {
							// we're still figuring out what controls what, just note the target nucleation
							// rate for this jump on each involved section
							
							for (int b=0; b<bins.size(); b++) {
								double binTargetNuclRate = binnedSegTargetNuclRates.get(b);
								int index = bins.get(b) - sectMinMagIndexes[s];
								if (binTargetNuclRate > assignedJumpRates[index]) {
									// this jump provides more available nucleation rate to this bin than
									// anything we have encountered thus far
									assignedJumpRates[index] = binTargetNuclRate;
									assignedJumps[index] = jump;
								}
							}
						} else {
							// 2nd round, we're applying it to assigned bins
							
							// calculate the nucleation rate for these sections according to the original G-R,
							// but scaled to match our current nucleation rate
							double origNuclTarget = 0d;
							for (int bin : bins)
								origNuclTarget += origBinContribution[bin]*curNuclRates[s];
							
							Preconditions.checkState(origNuclTarget > 0d,
									"Segmentation implied nucleation rate is zero for s=%s, jump=%s, "
									+ "\n\tmodMFD: %s",
									s, jump,  modMFD);
							
							// segmentation rate for these bins on this section should be the lesser of those two
							double targetNuclRate = Math.min(segNuclTarget, origNuclTarget);
							
							if (debug) {
								List<Integer> sorted = new ArrayList<>(bins);
								Collections.sort(sorted);
								System.out.println("Jump: "+jump+", prob="+segJumpProbsMap.get(jump));
								System.out.print("\t"+sorted.size()+" bins:");
								for (int bin : sorted)
									System.out.print(" "+(float)refMFD.getX(bin));
								System.out.println();
								System.out.println("\tTargets: partic="+segParticTarget+", nucl="+segNuclTarget);
								System.out.println("\tCur jump rate: partic="+curJumpParticRate+", nucl="+curJumpNuclRate);
								System.out.println("\tOrig nucl target="+origNuclTarget);
							}
							
							if ((float)targetNuclRate != (float)curJumpNuclRate) {
								changed = true;
								// now rescale these bins to match
								double scalar = targetNuclRate/curJumpNuclRate;
								if (debug) {
									System.out.println("\tscalar = "+targetNuclRate+" / "+curJumpNuclRate+" = "+scalar);
								}
								Preconditions.checkState(Double.isFinite(scalar),
										"Bad scalar=%s for segNuclTarget=%s, origNuclTarget=%s, curJumpJuclRate=%s",
										scalar, segNuclTarget, origNuclTarget, curJumpNuclRate);
								for (int bin : bins)
									modMFD.set(bin, modMFD.getY(bin)*scalar);
							}
						}
					}
					
					if (assignment) {
						// process final assignments: jump with the most available rate in each bin
						jumpBinAssignments = new HashMap<>();
						for (int b=0; b<assignedJumps.length; b++) {
							if (assignedJumps[b] != null) {
								int index = sectMinMagIndexes[s]+b;
								List<Integer> jumpMapped = jumpBinAssignments.get(assignedJumps[b]);
								if (jumpMapped == null) {
									jumpMapped = new ArrayList<>();
									jumpBinAssignments.put(assignedJumps[b], jumpMapped);
								}
								jumpMapped.add(index);
							}
						}
					}
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
		private Map<Integer, HashSet<Jump>> binJumps;
		private int numControlledBins = 0;
		private Map<Jump, List<Integer>> jumpBinsMap;
		
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
		
		public void processRupture(Jump worstJump, double worstJumpProb, int magIndex) {
			if (unityProbBins.get(magIndex))
				// this bin already has a rupture without segmentation
				return;
			if (worstJumpProb == 1d) {
				// not controlled by segmentation
				HashSet<Jump> prev = binJumps.remove(magIndex);
				if (prev != null)
					// was controlled, now isn't
					numControlledBins--;
				unityProbBins.set(magIndex);
			} else {
				Preconditions.checkNotNull(worstJump);
				HashSet<Jump> myBinJumps = binJumps.get(magIndex);
				if (myBinJumps == null) {
					// under control for the first time
					numControlledBins++;
					myBinJumps = new HashSet<>();
					binJumps.put(magIndex, myBinJumps);
				}
				myBinJumps.add(worstJump);
			}
		}
		
		public Map<Integer, HashSet<Jump>> getBinJumpMappings() {
			return binJumps;
		}
		
		public Map<Jump, List<Integer>> getJumpBinsMap(int sectMinIndex) {
			if (jumpBinsMap != null)
				return jumpBinsMap;
			Preconditions.checkState(controlled());
			Map<Jump, List<Integer>> map = new HashMap<>();
			for (int index : binJumps.keySet()) {
				for (Jump jump : binJumps.get(index)) {
					if (index >= sectMinIndex) {
						List<Integer> list = map.get(jump);
						if (list == null) {
							list = new ArrayList<>();
							map.put(jump, list);
						}
						list.add(index);
					}
				}
			}
			jumpBinsMap = map;
			return jumpBinsMap;
		}
	}

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
