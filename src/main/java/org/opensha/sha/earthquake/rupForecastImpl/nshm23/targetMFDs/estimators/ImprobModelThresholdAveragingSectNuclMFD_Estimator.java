package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.uncertainty.UncertainIncrMagFreqDist;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.Shaw07JumpDistProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.U3_UncertAddDeformationModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs.SubSeisMoRateReduction;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;

/**
 * This adjusts target supra-seismogenic nucleation MFDs for an improbability model (likely a segmentation model). It
 * first determines each unique model-based probability for all ruptures used by a given section and then sorts them
 * in descending order. It then steps through each unique probability level and computes a target MFD using only ruptures
 * at or above that probability level (zeroing out magnitude bins with no ruptures and rescaling the MFD to match the
 * original moment rate). Those target MFDs are then averaged with weights equal to the probability change from the
 * current level to the next (lower) probability level.
 * <p>
 * This creates equivalent MFDs to if we filtered the rupture set to only contain ruptures at or above each unique
 * probability level, generated subsection MFDs for that rupture subset, and then weight-averaged them a posteriori.
 * <p>
 * If you wish to only consider the individual probability from the worst jump within a rupture, which might be the case
 * for a segmentation model, see {@link WorstJumpProb}.
 * 
 * @author kevin
 *
 */
public class ImprobModelThresholdAveragingSectNuclMFD_Estimator extends SectNucleationMFD_Estimator {
	
	private RuptureProbabilityCalc improbModel;
	private HashSet<Integer> affectedSects;
	private double[] rupProbs;
	private int[] rupMagIndexes;
	
	private static final int MAX_DISCRETIZATIONS = 1000;

	private static final int DEBUG_SECT = -1; // disabled
//	private static final int DEBUG_SECT = 315; // Chino alt 1
//	private static final int DEBUG_SECT = 1832; // Mojave N
//	private static final int DEBUG_SECT = 100; // Bicycle Lake
//	private static final int DEBUG_SECT = 129; // Big Pine (East)
//	private static final int DEBUG_SECT = 159; // Brawley
	private int[] sectMinMagIndexes;
	private int[] sectMaxMagIndexes;
	private List<Float> fixedBinEdges;
	private FaultSystemRupSet rupSet;
	
	/**
	 * This uses the worst probability from any single jump as the "controlling" probability for a rupture, not the
	 * product across all jumps. Might be useful if you only want to penalize individual jumps for a segmentation
	 * constraint.
	 * 
	 * @author kevin
	 *
	 */
	public static class WorstJumpProb extends ImprobModelThresholdAveragingSectNuclMFD_Estimator {
		
		private JumpProbabilityCalc jumpModel;

		public WorstJumpProb(JumpProbabilityCalc improbModel) {
			this(improbModel, null);
		}

		public WorstJumpProb(JumpProbabilityCalc improbModel, List<? extends Number> fixedBinEdges) {
			super(improbModel, fixedBinEdges);
			this.jumpModel = improbModel;
		}

		@Override
		protected double calcRupProb(ClusterRupture rup) {
			double worstProb = 1d;
			for (Jump jump : rup.getJumpsIterable())
				worstProb = Math.min(worstProb, jumpModel.calcJumpProbability(rup, jump, false));
			return worstProb;
		}
		
	}
	
	/**
	 * This uses the worst probability from any single jump as the "controlling" probability for a rupture, not the
	 * product across all jumps. It then returns a fractional penalty relative to the GR probability of that rupture.
	 * 
	 * @author kevin
	 *
	 */
	public static class RelGRWorstJumpProb extends ImprobModelThresholdAveragingSectNuclMFD_Estimator {
		
		// stop iterating if the maximum gain is less than this
		private static final double ITERATE_STOP_RATIO_THRESHOLD = 1.0001;
		private static final int ITERATE_PRINT_MOD = 10;
		
		private JumpProbabilityCalc jumpModel;
		private int iterations;
		
		private Map<Jump, Double> jumpProbs;

		public RelGRWorstJumpProb(JumpProbabilityCalc improbModel) {
			this(improbModel, 1);
		}

		public RelGRWorstJumpProb(JumpProbabilityCalc improbModel, int iterations) {
			this(improbModel, iterations, null);
		}

		public RelGRWorstJumpProb(JumpProbabilityCalc improbModel, int iterations, List<? extends Number> fixedBinEdges) {
			super(improbModel, fixedBinEdges);
			this.jumpModel = improbModel;
			this.iterations = iterations;
		}
		
		@Override
		public void init(FaultSystemRupSet rupSet, List<IncrementalMagFreqDist> origSectSupraSeisMFDs,
				double[] targetSectSupraMoRates, double[] targetSectSupraSlipRates, double[] sectSupraSlipRateStdDevs,
				List<BitSet> sectRupUtilizations, int[] sectMinMagIndexes, int[] sectMaxMagIndexes,
				int[][] sectRupInBinCounts, EvenlyDiscretizedFunc refMFD) {
			ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
			
			Map<Jump, BitSet> jumpMagBins = new HashMap<>();
			
			// figure out which jumps use which mag bins
			for (int r=0; r<cRups.size(); r++) {
				int magBin = refMFD.getClosestXIndex(rupSet.getMagForRup(r));
				for (Jump jump : cRups.get(r).getJumpsIterable()) {
					if (jump.fromSection.getSectionId() > jump.toSection.getSectionId())
						jump = jump.reverse();
					BitSet bitSet = jumpMagBins.get(jump);
					if (bitSet == null) {
						bitSet = new BitSet(refMFD.size());
						jumpMagBins.put(jump, bitSet);
					}
					bitSet.set(magBin);
				}
			}
			
			// we're going to need section participation rates, which we will determine by multiplying
			// bin rates by the average rupture area in that bin, and then dividing by the section area.
			// 
			// these are section-specific scale factors
			int numSects = rupSet.getNumSections();
			double[][] sectNuclToParticScalars = new double[numSects][];
			List<List<Integer>> sectRupIndexes = new ArrayList<>();
			List<List<Double>> sectMags = new ArrayList<>();
			for (int s=0; s<numSects; s++) {
				int minMagIndex = sectMinMagIndexes[s];
				int maxMagIndex = sectMaxMagIndexes[s];
				
				double[] nuclToParticScalars = new double[1+maxMagIndex-minMagIndex];
				double[] avgBinAreas = new double[nuclToParticScalars.length];
				int[] avgCounts = new int[avgBinAreas.length];
				
				List<Integer> myRupIndexes = new ArrayList<>();
				sectRupIndexes.add(myRupIndexes);
				List<Double> myMags = new ArrayList<>();
				sectMags.add(myMags);
				
				BitSet utilization = sectRupUtilizations.get(s);
				// loop over ruptures for which this section participates
				for (int r = utilization.nextSetBit(0); r >= 0; r = utilization.nextSetBit(r+1)) {
					int index = refMFD.getClosestXIndex(rupSet.getMagForRup(r))-minMagIndex;
					avgCounts[index]++;
					avgBinAreas[index] += rupSet.getAreaForRup(r);
					myRupIndexes.add(r);
					myMags.add(rupSet.getMagForRup(r));
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
			
			Preconditions.checkState(iterations >= 1);
			boolean iterate = iterations > 1;
			ExecutorService exec = null;
			if (iterate)
				exec = Executors.newFixedThreadPool(FaultSysTools.defaultNumThreads());
			List<IncrementalMagFreqDist> workingSectSupraSeisMFDs = new ArrayList<>(origSectSupraSeisMFDs);
			for (int iter=0; iter<iterations; iter++) {
				jumpProbs = new HashMap<>();
				for (Jump jump : jumpMagBins.keySet()) {
					double minProb = 1d;
					BitSet jumpBitSet = jumpMagBins.get(jump);
					double jumpProb = jumpModel.calcJumpProbability(null, jump, false);
					if (jumpProb == 0d) {
						minProb = 0d;
					} else {
						for (int sectIndex : new int[] {jump.fromSection.getSectionId(), jump.toSection.getSectionId()}) {
							IncrementalMagFreqDist sectMFD = workingSectSupraSeisMFDs.get(sectIndex);
							if (sectMFD == null || sectMFD.calcSumOfY_Vals() == 0d) {
								minProb = 0d;
								break;
							}
							// total participation rate for this section assuming the original supra-seis MFD is honored
							double totParticRate = 0d;
							// total participation rate for magnitudes bins that use this jump
							double jumpParticRate = 0d;
							double[] nuclToParticScalars = sectNuclToParticScalars[sectIndex];
							for (int m=0; m<nuclToParticScalars.length; m++) {
								double binRate = sectMFD.getY(m + sectMinMagIndexes[sectIndex]);
								double particRate = binRate*nuclToParticScalars[m];
								totParticRate += particRate;
								if (jumpBitSet.get(m + sectMinMagIndexes[sectIndex]))
									jumpParticRate += particRate;
							}
							// this is the segmentation-implied participation rate allotment for this jump
							double segJumpParticRate = totParticRate*jumpProb;
							// what fraction of the jump participation rate is allowed by the segmentation model?
							// this will be >1 if the segmentation constraint is more permissive than the input G-R
							double segFractOfAllotment = segJumpParticRate/jumpParticRate;
							// minProb starts at 1, so don't need to ensure that it's <1 here
							minProb = Math.min(minProb, segFractOfAllotment);
						}
					}
					jumpProbs.put(jump, minProb);
					jumpProbs.put(jump.reverse(), minProb);
				}
				
				if (iterate) {
					// need to update the working supra-seis MFDs
					super.init(rupSet, origSectSupraSeisMFDs, targetSectSupraMoRates, targetSectSupraSlipRates, sectSupraSlipRateStdDevs,
							sectRupUtilizations, sectMinMagIndexes, sectMaxMagIndexes, sectRupInBinCounts, refMFD);
					List<Future<Double>> mfdEstFutures = new ArrayList<>();
					for (int s=0; s<workingSectSupraSeisMFDs.size(); s++) {
						final int sectIndex = s;
						mfdEstFutures.add(exec.submit(new Callable<Double>() {

							@Override
							public Double call() throws Exception {

								IncrementalMagFreqDist origMFD = workingSectSupraSeisMFDs.get(sectIndex);
								IncrementalMagFreqDist modMFD = estimateNuclMFD(rupSet.getFaultSectionData(sectIndex),
										origMFD, sectRupIndexes.get(sectIndex), sectMags.get(sectIndex), null, true);
								workingSectSupraSeisMFDs.set(sectIndex, modMFD);
								double gain = modMFD.calcSumOfY_Vals()/origMFD.calcSumOfY_Vals();
								return gain;
							}
						}));
					}
					double maxGain = 1d;
					MinMaxAveTracker gainTrack = new MinMaxAveTracker();
					for (Future<Double> future : mfdEstFutures) {
						double gain;
						try {
							gain = future.get();
						} catch (InterruptedException | ExecutionException e) {
							exec.shutdown();
							throw ExceptionUtils.asRuntimeException(e);
						}
						if (Double.isFinite(gain)) {
							gainTrack.addValue(gain);
							if (gain < 1d)
								gain = 1/gain;
							maxGain = Math.max(gain, maxGain);
						}
					}
					boolean print = iter == 0 || (iter+1) % ITERATE_PRINT_MOD == 0
							|| iter == iterations-1 || maxGain < ITERATE_STOP_RATIO_THRESHOLD;
					if (print) {
						System.out.println("Done with rel-GR threshold averaging iteration "+(iter+1)+"/"+iterations);
						System.out.println("\tsect rate gains: "+gainTrack);
					}
					if (maxGain < ITERATE_STOP_RATIO_THRESHOLD) {
						System.out.println("\tstopping early after "+(iter+1)+" iterations (maxGain="+(float)maxGain
								+" < "+(float)ITERATE_STOP_RATIO_THRESHOLD+")");
						break;
					}
				}
			}
			
			if (exec != null)
				exec.shutdown();
			super.init(rupSet, origSectSupraSeisMFDs, targetSectSupraMoRates, targetSectSupraSlipRates, sectSupraSlipRateStdDevs,
					sectRupUtilizations, sectMinMagIndexes, sectMaxMagIndexes, sectRupInBinCounts, refMFD);
		}

		@Override
		protected double calcRupProb(ClusterRupture rup) {
			double worstProb = 1d;
			for (Jump jump : rup.getJumpsIterable()) {
				Double jumpProb = jumpProbs.get(jump);
				Preconditions.checkNotNull(jumpProb, "No jump probability for %s (have %s in total)", jump, jumpProbs.size());
				worstProb = Math.min(worstProb, jumpProb);
			}
			return worstProb;
		}
		
	}

	public ImprobModelThresholdAveragingSectNuclMFD_Estimator(RuptureProbabilityCalc improbModel) {
		this(improbModel, null);
	}

	public ImprobModelThresholdAveragingSectNuclMFD_Estimator(RuptureProbabilityCalc improbModel,
			List<? extends Number> fixedBinEdges) {
		this.improbModel = improbModel;
		
		if (fixedBinEdges != null) {
			Preconditions.checkState(!fixedBinEdges.isEmpty());
			// sort descending
			this.fixedBinEdges = new ArrayList<>();
			for (Number edge : fixedBinEdges)
				this.fixedBinEdges.add(edge.floatValue());
			Collections.sort(this.fixedBinEdges);
			Collections.reverse(this.fixedBinEdges);
			if (this.fixedBinEdges.get(0) != 1f)
				this.fixedBinEdges.add(0, 1f);
		}
	}

	@Override
	public void init(FaultSystemRupSet rupSet, List<IncrementalMagFreqDist> origSectSupraSeisMFDs,
			double[] targetSectSupraMoRates, double[] targetSectSupraSlipRates, double[] sectSupraSlipRateStdDevs,
			List<BitSet> sectRupUtilizations, int[] sectMinMagIndexes, int[] sectMaxMagIndexes,
			int[][] sectRupInBinCounts, EvenlyDiscretizedFunc refMFD) {
		this.rupSet = rupSet;
		this.sectMinMagIndexes = sectMinMagIndexes;
		this.sectMaxMagIndexes = sectMaxMagIndexes;
		super.init(rupSet, origSectSupraSeisMFDs, targetSectSupraMoRates, targetSectSupraSlipRates, sectSupraSlipRateStdDevs,
				sectRupUtilizations, sectMinMagIndexes, sectMaxMagIndexes, sectRupInBinCounts, refMFD);
		
		// figure out what sections are affected by segmentation, and precompute rupture probabilities and magnitude indexes
		affectedSects = new HashSet<>();
		rupProbs = new double[rupSet.getNumRuptures()];
		rupMagIndexes = new int[rupSet.getNumRuptures()];
		
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			ClusterRupture rup = cRups.get(r);
			rupProbs[r] = calcRupProb(rup);
			rupMagIndexes[r] = refMFD.getClosestXIndex(rupSet.getMagForRup(r));
			if (rupProbs[r] < 1)
				for (int sectIndex : rupSet.getSectionsIndicesForRup(r))
					affectedSects.add(sectIndex);
		}
	}
	
	protected double calcRupProb(ClusterRupture rup) {
		return improbModel.calcRuptureProb(rup, false);
	}

	@Override
	public boolean appliesTo(FaultSection sect) {
		return affectedSects.contains(sect.getSectionId());
	}

	@Override
	public IncrementalMagFreqDist estimateNuclMFD(FaultSection sect, IncrementalMagFreqDist curSectSupraSeisMFD,
			List<Integer> availableRupIndexes, List<Double> availableRupMags, UncertainDataConstraint sectMomentRate,
			boolean sparseGR) {
		if ((sectMomentRate != null && sectMomentRate.bestEstimate == 0d) || availableRupIndexes.isEmpty()
				|| curSectSupraSeisMFD.calcSumOfY_Vals() == 0d)
			// this is a zero rate section, do nothing
			return curSectSupraSeisMFD;
		
		boolean debug = sect.getSectionId() == DEBUG_SECT;
		
		// figure out each unique probability level, do so at slightly coarser floating-point resolution
		 
		// initially sorted in increasing order
		List<Float> sortedProbs = new ArrayList<>();
		double minNonZeroProb = 1d;
		double maxProb = 0d;
		for (int rupIndex : availableRupIndexes) {
			Float rupProb = (float)rupProbs[rupIndex];
			if (rupProb > 0f) {
				int insIndex = Collections.binarySearch(sortedProbs, rupProb);
				if (insIndex < 0) {
					// it's a new unique probability level
					insIndex = -(insIndex + 1);
					sortedProbs.add(insIndex, rupProb);
					minNonZeroProb = Math.min(rupProbs[rupIndex], minNonZeroProb);
					maxProb = Math.max(rupProbs[rupIndex], maxProb);
				}
			}
		}
		if (debug) {
			System.out.println("Debug for "+sect.getSectionId()+". "+sect.getSectionName());
			System.out.println("Rupture prob range: ["+(float)minNonZeroProb+", "+(float)maxProb+"]");
			HashSet<Float> singleFaultMags = new HashSet<>();
			int numAvailSingles = 0;
			ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
			for (int i=0; i<availableRupIndexes.size(); i++) {
				int rupIndex = availableRupIndexes.get(i);
				ClusterRupture rup = cRups.get(rupIndex);
				if (rup.getTotalNumClusters() == 1) {
					numAvailSingles++;
					double mag = availableRupMags.get(i);
					int magIndex = curSectSupraSeisMFD.getClosestXIndex(mag);
					singleFaultMags.add((float)curSectSupraSeisMFD.getX(magIndex));
				}
			}
			List<Float> sortedMags = new ArrayList<>(singleFaultMags);
			Collections.sort(sortedMags);
			System.out.println("Single fault mags ("+numAvailSingles+" rups): "+Joiner.on(",").join(sortedMags));
		}
		if (maxProb == 0d)
			// all zero probability
			return curSectSupraSeisMFD;
		Preconditions.checkState(sortedProbs.size() >= 1);
		
		if (fixedBinEdges == null) {
			// make sure our first bin starts at 1
			if (sortedProbs.get(sortedProbs.size()-1) != 1f)
				sortedProbs.add(1f);
			
			if (debug) {
				System.out.print("Unique probs:");
				for (Float prob : sortedProbs)
					System.out.print(" "+prob);
				System.out.println();
			}
			
//			System.out.println("Section "+sect.getSectionId()+" has "+sortedProbs.size()+" unique probs");
			
			if (sortedProbs.size() > MAX_DISCRETIZATIONS) {
				// we have too many, down-sample by removing the value that is closest to the value after it in ln space
				// repeat until we're below the limit
				System.out.println("Decimating "+sortedProbs.size()+" unique probabilities down to "+MAX_DISCRETIZATIONS+" points");
				while (sortedProbs.size() > MAX_DISCRETIZATIONS) {
					// decimate it, removing the nearest points in ln(prob) space
					double minDist = Double.POSITIVE_INFINITY;
					int minDistIndex = -1;
					
					double prevLnProb = Math.log(sortedProbs.get(0));
					// never remove the first or last one1
					for (int i=2; i<sortedProbs.size(); i++) {
						double myLnProb = Math.log(sortedProbs.get(i));
						double dist = myLnProb - prevLnProb;
						Preconditions.checkState(dist > 0d);
						if (dist < minDist) {
							minDist = dist;
							minDistIndex = i-1;
						}
						prevLnProb = myLnProb;
					}
					sortedProbs.remove(minDistIndex);
				}
			}
			
			// re-sort probabilities from high to low. for each iteration, we will include all ruptures up until the next
			// probability level
			Collections.reverse(sortedProbs);
		} else {
			// use the passed in fixed bin edges, not the actual ones available. useful to reproduce the
			// strict-segmentation averaging approach
			sortedProbs = fixedBinEdges;
		}
		
		IncrementalMagFreqDist ret = new IncrementalMagFreqDist(curSectSupraSeisMFD.getMinX(),
				curSectSupraSeisMFD.size(), curSectSupraSeisMFD.getDelta());
		
		// keeps track of magnitude bins with at least one rupture available at or above the current probability level
		boolean[] availBins = new boolean[ret.size()];
//		List<Integer> unusedRups = new ArrayList<>(availableRupIndexes);
		// keeps track of which ruptures have yet to be processed
		BitSet stillAvailableIndexes = new BitSet(availableRupIndexes.size());
		// initialize to all available
		for (int i=0; i<availableRupIndexes.size(); i++)
			stillAvailableIndexes.set(i);
		
		// we'll rescale to match this
		double origMoRate = curSectSupraSeisMFD.getTotalMomentRate();
		
		IncrementalMagFreqDist prevMFD = null;
		double sumWeight = 0d;
		
		if (debug) {
			System.out.print("Prob\tNext\tWeight");
			for (int i=sectMinMagIndexes[sect.getSectionId()]; i<=sectMaxMagIndexes[sect.getSectionId()]; i++)
				System.out.print("\t"+(float)curSectSupraSeisMFD.getX(i));
			System.out.println();
		}
		for (int i=0; i<sortedProbs.size(); i++) {
			// this bin is responsible for all ruptures up to the next bin edge, exclusive
			float prob = sortedProbs.get(i);
			if (prob == 0f)
				break;
			// if this is the last bin, then it's responsible for the rest non-zero ruptures
			float nextProb = i == sortedProbs.size()-1 ? 0f : sortedProbs.get(i+1);
			Preconditions.checkState(prob > nextProb);
			// weight is the difference in probability from my current level to the next level
			double weight = prob - nextProb;
			
			sumWeight += weight;
			
			// see if we have any new magnitude bins available
			boolean changed = false;
			if (stillAvailableIndexes.cardinality() > 0) {
				for (int j=stillAvailableIndexes.nextSetBit(0); j>=0; j=stillAvailableIndexes.nextSetBit(j+1)) {
					int rupIndex = availableRupIndexes.get(j);
					if ((float)rupProbs[rupIndex] > nextProb) {
						// this rupture lies within the current probability bin
						if (!availBins[rupMagIndexes[rupIndex]]) {
							// first one available for this magnitude
							availBins[rupMagIndexes[rupIndex]] = true;
							changed = true;
						}
						// this rupture has now been processed, can remove from further processing
						stillAvailableIndexes.clear(j);
					}
				}
			}
//			for (int j=unusedRups.size(); --j>=0;) {
//				int rupIndex = unusedRups.get(j);
//				if ((float)rupProbs[rupIndex] > nextProb) {
//					// this rupture lies within the current probability bin
//					if (!availBins[rupMagIndexes[rupIndex]]) {
//						// first one available for this magnitude
//						availBins[rupMagIndexes[rupIndex]] = true;
//						changed = true;
//					}
//					// this rupture has now been processed, can remove from further processing
//					unusedRups.remove(j);
//				}
//			}
			
			IncrementalMagFreqDist myMFD;
			if (prevMFD != null && !changed) {
				// nothing changed by including ruptures at this probability level
				myMFD = prevMFD;
			} else {
				myMFD = new IncrementalMagFreqDist(curSectSupraSeisMFD.getMinX(),
						curSectSupraSeisMFD.size(), curSectSupraSeisMFD.getDelta());
				// keep the values for only those bins from the original MFD that we have access to 
				for (int b=0; b<myMFD.size(); b++)
					if (availBins[b])
						myMFD.set(b, curSectSupraSeisMFD.getY(b));
				if (myMFD.calcSumOfY_Vals() > 0d)
					// rescale to match the original moment
					myMFD.scaleToTotalMomentRate(origMoRate);
				prevMFD = myMFD;
			}
			if (debug) {
				System.out.print(eDF.format(prob)+"\t"+eDF.format(nextProb)+"\t"+eDF.format(weight));
				for (int b=sectMinMagIndexes[sect.getSectionId()]; b<=sectMaxMagIndexes[sect.getSectionId()]; b++)
					System.out.print("\t"+(availBins[b] ? "1" : "0"));
				System.out.println();
			}
//			System.out.println("s="+sect.getSectionId()+", prob="+prob+", nextProb="+nextProb+", weight="+weight
//					+", origSumRate="+curSectSupraSeisMFD.calcSumOfY_Vals()+", mySumRate="+myMFD.calcSumOfY_Vals()
//					+", numAvailBins="+availBins.cardinality());
			// add it in to the final MFD with the appropriate weight
			for (int b=0; b<myMFD.size(); b++)
				ret.add(b, myMFD.getY(b)*weight);
		}
		if (sumWeight > 1.02 || sumWeight < 0.98)
			System.err.println("Warning, sumWeight="+sumWeight+" for "+sect.getSectionId()+". "+sect.getSectionName()+", rescaling");
		// rescale to correct for any floating point drift, or for the case that there are no ruptures with P=1
		ret.scaleToTotalMomentRate(origMoRate);
		
		return ret;
	}
	
	private static final DecimalFormat eDF = new DecimalFormat("0.00E0");
	
	public static void main(String[] args) throws IOException {
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(
//				new File("/data/kevin/markdown/inversions/fm3_1_u3ref_uniform_reproduce_ucerf3.zip"));
				new File("/data/kevin/markdown/inversions/fm3_1_u3ref_uniform_coulomb.zip"));
		Region reg = new CaliforniaRegions.RELM_TESTING();
		
		rupSet = FaultSystemRupSet.buildFromExisting(rupSet)
//				.replaceFaultSections(DeformationModels.MEAN_UCERF3.build(FaultModels.FM3_1))
				.replaceFaultSections(U3_UncertAddDeformationModels.U3_MEAN.build(FaultModels.FM3_1))
				.forScalingRelationship(ScalingRelationships.MEAN_UCERF3)
				.build();
		
		double bVal = 0.5d;
		Shaw07JumpDistProb segModel = Shaw07JumpDistProb.forHorzOffset(1d, 3d, 2d);
		
		File outputDir = new File("/tmp");

//		ImprobModelThresholdAveragingSectNuclMFD_Estimator improb1 = new ImprobModelThresholdAveragingSectNuclMFD_Estimator.WorstJumpProb(segModel);
//		String name1 = "Seg-Prob";
//		ImprobModelThresholdAveragingSectNuclMFD_Estimator improb2 = new ImprobModelThresholdAveragingSectNuclMFD_Estimator.RelGRWorstJumpProb(segModel);
//		String name2 = "Rel-GR";
//		String prefix = "thresh_avg_vs_rel_gr";

		ImprobModelThresholdAveragingSectNuclMFD_Estimator improb1 = new ImprobModelThresholdAveragingSectNuclMFD_Estimator.WorstJumpProb(segModel);
		String name1 = "Seg-Prob";
		ImprobModelThresholdAveragingSectNuclMFD_Estimator improb2 = new ImprobModelThresholdAveragingSectNuclMFD_Estimator.RelGRWorstJumpProb(segModel, 100);
		String name2 = "Rel-GR-100-Iters";
		String prefix = "thresh_avg_vs_rel_gr_iter";
		
//		ImprobModelThresholdAveragingSectNuclMFD_Estimator improb1 = new ImprobModelThresholdAveragingSectNuclMFD_Estimator.RelGRWorstJumpProb(segModel, 1);
//		String name1 = "Rel-GR-1-Iter";
//		ImprobModelThresholdAveragingSectNuclMFD_Estimator improb2 = new ImprobModelThresholdAveragingSectNuclMFD_Estimator.RelGRWorstJumpProb(segModel, 100);
//		String name2 = "Rel-GR-100-Iters";
//		String prefix = "thresh_avg_rel_gr_iters";
		
		SupraSeisBValInversionTargetMFDs.Builder builder = new SupraSeisBValInversionTargetMFDs.Builder(rupSet, bVal);
		builder.subSeisMoRateReduction(SubSeisMoRateReduction.SUB_SEIS_B_1);
		builder.adjustTargetsForData(improb1);
		List<UncertainIncrMagFreqDist> mfds1 = builder.build().getOnFaultSupraSeisNucleationMFDs();
		
		builder = new SupraSeisBValInversionTargetMFDs.Builder(rupSet, bVal);
		builder.subSeisMoRateReduction(SubSeisMoRateReduction.SUB_SEIS_B_1);
		builder.adjustTargetsForData(improb2);
		List<UncertainIncrMagFreqDist> mfds2 = builder.build().getOnFaultSupraSeisNucleationMFDs();
		
		DefaultXY_DataSet probScatter = new DefaultXY_DataSet();
		
		double minNonZero = 1d;
		for (ClusterRupture rup : rupSet.requireModule(ClusterRuptures.class)) {
			double prob1 = improb1.calcRupProb(rup);
			double prob2 = improb2.calcRupProb(rup);
			
			if ((prob1 != 1d || prob2 != 1d) && (prob1 != 0d || prob2 != 0d)) {
				probScatter.set(prob1, prob2);
				if (prob1 > 0d)
					minNonZero = Math.min(minNonZero, prob1);
				if (prob2 > 0d)
					minNonZero = Math.min(minNonZero, prob2);
			}
		}
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		Range range = new Range(Math.pow(10, Math.floor(Math.log10(minNonZero))), 1d);
		
		DefaultXY_DataSet oneToOne = new DefaultXY_DataSet();
		oneToOne.set(range.getLowerBound(), range.getLowerBound());
		oneToOne.set(range.getUpperBound(), range.getUpperBound());
		
		funcs.add(oneToOne);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		
		funcs.add(probScatter);
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLACK));
		
		PlotSpec spec = new PlotSpec(funcs, chars, "Thresh-Avg Probabilities", name1, name2);
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		gp.drawGraphPanel(spec, true, true, range, range);
		
		PlotUtils.writePlots(outputDir, prefix+"_probs", gp, 1000, false, true, false, false);
		
		// now seg rates
		double[] rates1 = new double[rupSet.getNumSections()];
		double[] rates2 = new double[rupSet.getNumSections()];
		
		DefaultXY_DataSet rateScatter = new DefaultXY_DataSet();
		minNonZero = 1d;
		for (int s=0; s<rates1.length; s++) {
			rates1[s] = mfds1.get(s).calcSumOfY_Vals();
			rates2[s] = mfds2.get(s).calcSumOfY_Vals();
			if ((rates1[s] != 1d || rates2[s] != 1d) && (rates1[s] != 0d || rates2[s] != 0d)) {
				rateScatter.set(rates1[s], rates2[s]);
				if (rates1[s] > 0d)
					minNonZero = Math.min(minNonZero, rates1[s]);
				if (rates2[s] > 0d)
					minNonZero = Math.min(minNonZero, rates2[s]);
			}
		}
		
		range = new Range(Math.pow(10, Math.floor(Math.log10(minNonZero))), 1d);
		
		oneToOne = new DefaultXY_DataSet();
		oneToOne.set(range.getLowerBound(), range.getLowerBound());
		oneToOne.set(range.getUpperBound(), range.getUpperBound());
		
		funcs.add(oneToOne);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		
		funcs.add(rateScatter);
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLACK));
		
		spec = new PlotSpec(funcs, chars, "Thresh-Avg Sect Nuclation Rates", name1, name2);
		
		gp.drawGraphPanel(spec, true, true, range, range);
		
		PlotUtils.writePlots(outputDir, prefix+"_rates_scatter", gp, 1000, false, true, false, false);
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(rupSet, reg);
		
		CPT pDiffCPT = GMT_CPT_Files.GMT_POLAR.instance().rescale(-100d, 100d);
		
		mapMaker.plotSectScalars(pDiff(rates2, rates1), pDiffCPT, "Nucleation Rate % Difference: "+name2+" - "+name1);
		
		mapMaker.plot(outputDir, prefix+"_rates", "Section Nucleation Rates");
	}
	
	private static double[] pDiff(double[] primary, double[] comparison) {
		double[] ret = new double[primary.length];
		for (int i=0; i<ret.length; i++) {
			double z1 = primary[i];
			double z2 = comparison[i];
			double val;
			if (z1 == 0d && z2 == 0d)
				val = 0d;
			else if (z2 == 0d)
				val = Double.POSITIVE_INFINITY;
			else
				val = 100d*(z1-z2)/z2;
			ret[i] = val;
		}
		return ret;
	}

}
