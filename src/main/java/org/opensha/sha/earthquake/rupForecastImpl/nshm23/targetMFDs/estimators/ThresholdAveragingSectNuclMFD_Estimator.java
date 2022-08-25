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
 * for a segmentation model, see {@link WorstJumpProb}. If you want that penalty to take into account the G-R probability
 * of such a jump soas to avoid overcorrection, see {@link RelGRWorstJumpProb}.
 * 
 * @author kevin
 *
 */
public abstract class ThresholdAveragingSectNuclMFD_Estimator extends SectNucleationMFD_Estimator {
	
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
	protected List<Float> fixedBinEdges;
	private FaultSystemRupSet rupSet;
	
	/**
	 * This version uses the net "probability" for each rupture (not that from the worst jump)
	 * 
	 * @author kevin
	 *
	 */
	public static class RupProbModel extends ThresholdAveragingSectNuclMFD_Estimator {
		
		private RuptureProbabilityCalc model;

		public RupProbModel(RuptureProbabilityCalc model) {
			this.model = model;
		}

		@Override
		protected double calcRupProb(ClusterRupture rup) {
			return model.calcRuptureProb(rup, false);
		}
	}
	
	/**
	 * This uses the worst probability from any single jump as the "controlling" probability for a rupture, not the
	 * product across all jumps. Might be useful if you only want to penalize individual jumps for a segmentation
	 * constraint.
	 * 
	 * @author kevin
	 *
	 */
	public static abstract class AbstractWorstJumpProb extends ThresholdAveragingSectNuclMFD_Estimator {
		
		public AbstractWorstJumpProb() {
			this(null);
		}

		public AbstractWorstJumpProb(List<? extends Number> fixedBinEdges) {
			super(fixedBinEdges);
		}
		
		protected abstract double calcJumpProb(ClusterRupture rup, Jump jump);

		@Override
		protected double calcRupProb(ClusterRupture rup) {
			double worstProb = 1d;
			for (Jump jump : rup.getJumpsIterable())
				worstProb = Math.min(worstProb, calcJumpProb(rup, jump));
			return worstProb;
		}
		
	}
	
	/**
	 * This uses the worst probability from any single jump as the "controlling" probability for a rupture, not the
	 * product across all jumps. Might be useful if you only want to penalize individual jumps for a segmentation
	 * constraint.
	 * 
	 * @author kevin
	 *
	 */
	public static class WorstJumpProb extends AbstractWorstJumpProb {
		
		private JumpProbabilityCalc jumpModel;

		public WorstJumpProb(JumpProbabilityCalc improbModel) {
			this(improbModel, null);
		}

		public WorstJumpProb(JumpProbabilityCalc improbModel, List<? extends Number> fixedBinEdges) {
			super(fixedBinEdges);
			this.jumpModel = improbModel;
		}

		@Override
		protected double calcJumpProb(ClusterRupture rup, Jump jump) {
			return jumpModel.calcJumpProbability(rup, jump, false);
		}
		
	}
	
	/**
	 * Same as {@link WorstJumpProb}, except that the average probability is computed for each jump across all ruptures
	 * that utilize it. This will only make a difference if the chosen {@link JumpProbabilityCalc} returns different
	 * probabilities for the same jump. In practice, this occurs with distance-dependent models where the jump
	 * occurs at subsections that aren't actually the closest point between two faults, so the controlling distance
	 * can depend on which other subsections on those faults are included.
	 * 
	 * @author kevin
	 *
	 */
	public static class WorstAvgJumpProb extends AbstractWorstJumpProb {
		
		private JumpProbabilityCalc jumpModel;
		
		private Map<Jump, Double> avgJumpProbs;

		public WorstAvgJumpProb(JumpProbabilityCalc improbModel) {
			this(improbModel, null);
		}

		public WorstAvgJumpProb(JumpProbabilityCalc improbModel, List<? extends Number> fixedBinEdges) {
			super(fixedBinEdges);
			this.jumpModel = improbModel;
		}

		@Override
		public void init(FaultSystemRupSet rupSet, List<IncrementalMagFreqDist> origSectSupraSeisMFDs,
				double[] targetSectSupraMoRates, double[] targetSectSupraSlipRates, double[] sectSupraSlipRateStdDevs,
				List<BitSet> sectRupUtilizations, int[] sectMinMagIndexes, int[] sectMaxMagIndexes,
				int[][] sectRupInBinCounts, EvenlyDiscretizedFunc refMFD) {
			avgJumpProbs = calcAverageJumpProbs(rupSet.requireModule(ClusterRuptures.class), jumpModel);
			
			super.init(rupSet, origSectSupraSeisMFDs, targetSectSupraMoRates, targetSectSupraSlipRates, sectSupraSlipRateStdDevs,
					sectRupUtilizations, sectMinMagIndexes, sectMaxMagIndexes, sectRupInBinCounts, refMFD);
		}

		@Override
		protected double calcJumpProb(ClusterRupture rup, Jump jump) {
			Double prob = avgJumpProbs.get(jump);
			Preconditions.checkNotNull(prob, "No precomputed prob for jump %s in rupture %s", jump, rup);
			return prob;
		}
		
	}
	
	/**
	 * Same as {@link WorstPrecomputedJumpProb}, except that the jump probabilities are passed in
	 * 
	 * @author kevin
	 *
	 */
	public static class WorstPrecomputedJumpProb extends AbstractWorstJumpProb {
		
		private Map<Jump, Double> jumpProbs;

		public WorstPrecomputedJumpProb(JumpProbabilityCalc improbModel, Map<Jump, Double> jumpProbs) {
			this(improbModel, jumpProbs, null);
		}

		public WorstPrecomputedJumpProb(JumpProbabilityCalc improbModel, Map<Jump, Double> jumpProbs, List<? extends Number> fixedBinEdges) {
			super(fixedBinEdges);
			this.jumpProbs = jumpProbs;
		}

		@Override
		protected double calcJumpProb(ClusterRupture rup, Jump jump) {
			Double prob = jumpProbs.get(jump);
			Preconditions.checkNotNull(prob, "No precomputed prob for jump %s in rupture %s", jump, rup);
			return prob;
		}
		
	}
	
	private static Map<Jump, Double> calcAverageJumpProbs(ClusterRuptures cRups, JumpProbabilityCalc jumpModel) {
		// calculate average probabilities for each jump. jump probabilities can vary by rupture, especially
		// if one rupture has a difference distance (distance is defined between the departing and landing clusters,
		// rather than the section, and the clusters can vary)
		Map<Jump, QuickAvgTrack> origJumpProbsMap = new HashMap<>();
		for (ClusterRupture rup : cRups) {
			for (Jump jump : rup.getJumpsIterable()) {
				if (jump.fromSection.getSectionId() > jump.toSection.getSectionId())
					jump = jump.reverse();
				QuickAvgTrack jumpProbs = origJumpProbsMap.get(jump);
				if (jumpProbs == null) {
					jumpProbs = new QuickAvgTrack();
					origJumpProbsMap.put(jump, jumpProbs);
				}
				jumpProbs.add(jumpModel.calcJumpProbability(rup, jump, false));
			}
		}
		
		Map<Jump, Double> avgOrigJumpProbs = new HashMap<>();
		for (Jump jump : origJumpProbsMap.keySet()) {
			double prob = origJumpProbsMap.get(jump).getAverage();
			avgOrigJumpProbs.put(jump, prob);
			avgOrigJumpProbs.put(jump.reverse(), prob);
		}
		
		return avgOrigJumpProbs;
	}
	
	/**
	 * For each jump, this calculates a G-R adjusted probability such that the participation rate of ruptures
	 * using the jump shall not exceed the segmentation model implied fraction of the total participation rate. This
	 * fraction is calculated separately for the departing and landing subsection for each jump, and the lesser of the
	 * two is retained.
	 * 
	 * Like {@link WorstAvgJumpProb}, this uses the average calculated probability for each jump.
	 * 
	 * For purposes of threshold averaging, the "probability" assigned to a rupture is that of the jump with the lowest
	 * probability in that rupture.
	 * 
	 * @author kevin
	 *
	 */
	public static class RelGRWorstJumpProb extends AbstractWorstJumpProb {
		
		public static boolean D = false;
		
		private JumpProbabilityCalc jumpModel;
		private int iterations;
		
		private Map<Jump, Double> jumpProbs;
		
		private boolean applyOrigProbFloor;

		/**
		 * @param improbModel jump segmentation model
		 * @param iterations number of iterations to determine the correct adjustment to match the G-R, ~50 works well.
		 * Should be >1 as the first iteration will usually be an overcorrection.
		 * @param applyOrigProbFloor if true, the G-R calculated probability will never be allowed to drop below the
		 * jump probability, which could occur in certain cases where the jump uses most magnitude bins and the
		 * participation-to-nucleation conversion is high in those bins
		 */
		public RelGRWorstJumpProb(JumpProbabilityCalc improbModel, int iterations, boolean applyOrigProbFloor) {
			super(null);
			this.jumpModel = improbModel;
			this.iterations = iterations;
			this.applyOrigProbFloor = applyOrigProbFloor;
		}

		@Override
		public void init(FaultSystemRupSet rupSet, List<IncrementalMagFreqDist> origSectSupraSeisMFDs,
				double[] targetSectSupraMoRates, double[] targetSectSupraSlipRates, double[] sectSupraSlipRateStdDevs,
				List<BitSet> sectRupUtilizations, int[] sectMinMagIndexes, int[] sectMaxMagIndexes,
				int[][] sectRupInBinCounts, EvenlyDiscretizedFunc refMFD) {
			ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
			
			Map<Jump, Double> avgOrigJumpProbs = calcAverageJumpProbs(cRups, jumpModel);
			
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
			ExecutorService exec = Executors.newFixedThreadPool(D ? 1 : FaultSysTools.defaultNumThreads());
			
			Map<Jump, Future<Double>> jumpFutures = new HashMap<>();
			for (Jump jump : jumpMagBins.keySet()) {
				BitSet jumpBitSet = jumpMagBins.get(jump);
				double jumpProb = avgOrigJumpProbs.get(jump);
				RelGRJumpProbCalc calc = new RelGRJumpProbCalc(jump, jumpProb, jumpBitSet, refMFD,
						origSectSupraSeisMFDs, sectNuclToParticScalars, sectMinMagIndexes, iterations);
				jumpFutures.put(jump, exec.submit(calc));
			}
			
			jumpProbs = new HashMap<>();
			for (Jump jump : jumpFutures.keySet()) {
				double relGRProb;
				try {
					relGRProb = jumpFutures.get(jump).get();
				} catch (InterruptedException | ExecutionException e) {
					exec.shutdown();
					throw ExceptionUtils.asRuntimeException(e);
				}
				if (applyOrigProbFloor)
					relGRProb = Math.max(relGRProb, avgOrigJumpProbs.get(jump));
				jumpProbs.put(jump, relGRProb);
				jumpProbs.put(jump.reverse(), relGRProb);
			}
			
			exec.shutdown();
			
			super.init(rupSet, origSectSupraSeisMFDs, targetSectSupraMoRates, targetSectSupraSlipRates, sectSupraSlipRateStdDevs,
					sectRupUtilizations, sectMinMagIndexes, sectMaxMagIndexes, sectRupInBinCounts, refMFD);
		}

		@Override
		protected double calcJumpProb(ClusterRupture rup, Jump jump) {
			Double jumpProb = jumpProbs.get(jump);
			Preconditions.checkNotNull(jumpProb, "No jump probability for %s (have %s in total)", jump, jumpProbs.size());
			return jumpProb;
		}
		
	}
	
	private static class RelGRJumpProbCalc implements Callable<Double> {
		
		private Jump jump;
		private double jumpProb;
		private BitSet jumpBitSet;
		private EvenlyDiscretizedFunc refMFD;
		private List<? extends IncrementalMagFreqDist> sectMFDs;
		private double[][] sectNuclToParticScalars;
		private int[] sectMinMagIndexes;
		private int iterations;

		RelGRJumpProbCalc(Jump jump, double jumpProb, BitSet jumpBitSet, EvenlyDiscretizedFunc refMFD,
				List<? extends IncrementalMagFreqDist> sectMFDs, double[][] sectNuclToParticScalars,
				int[] sectMinMagIndexes, int iterations) {
			this.jump = jump;
			this.jumpProb = jumpProb;
			this.jumpBitSet = jumpBitSet;
			this.refMFD = refMFD;
			this.sectMFDs = sectMFDs;
			this.sectNuclToParticScalars = sectNuclToParticScalars;
			this.sectMinMagIndexes = sectMinMagIndexes;
			Preconditions.checkState(iterations >= 1);
			this.iterations = iterations;
		}
		
		public Double call() {
			final boolean D = RelGRWorstJumpProb.D;
			
			double minProb = 1d;
			if (D) {
				System.out.println("Jump from "+jump.fromSection.getSectionId()+". "+jump.fromSection.getSectionName()
					+" to "+jump.toSection.getSectionId()+". "+jump.toSection.getSectionName());
				System.out.println("\tDist: "+(float)jump.distance);
				System.out.println("\tProb: "+(float)jumpProb);
				String magBinStr = null;
				for (int m=0; m<refMFD.size(); m++) {
					if (jumpBitSet.get(m)) {
						if (magBinStr == null)
							magBinStr = "";
						else
							magBinStr += ",";
						magBinStr += (float)refMFD.getX(m);
					}
				}
				System.out.println("\tMags: "+magBinStr);
			}
			
			if (jumpProb == 0d) {
				minProb = 0d;
			} else {
				for (int sectIndex : new int[] {jump.fromSection.getSectionId(), jump.toSection.getSectionId()}) {
					IncrementalMagFreqDist sectMFD = sectMFDs.get(sectIndex);
					if (sectMFD == null || sectMFD.calcSumOfY_Vals() == 0d) {
						minProb = 0d;
						break;
					}
					// total participation rate for this section assuming the original supra-seis target MFD is honored
					double totParticRate = 0d;
					// total participation rate for magnitudes bins that use this jump
					double jumpParticRate = 0d;
					double[] nuclToParticScalars = sectNuclToParticScalars[sectIndex];
					// number of magnitude bins for this rupture that don't use this jump
					int binsNotUsing = 0;
					for (int m=0; m<nuclToParticScalars.length; m++) {
						int binIndex = m + sectMinMagIndexes[sectIndex];
						double binRate = sectMFD.getY(binIndex);
						double particRate = binRate*nuclToParticScalars[m];
						totParticRate += particRate;
						if (jumpBitSet.get(binIndex))
							jumpParticRate += particRate;
						else if (binRate > 0d)
							binsNotUsing++;
					}
					if (D) {
						String name = jump.fromSection.getSectionId() == sectIndex ?
								jump.fromSection.getSectionName() : jump.toSection.getSectionName();
						System.out.println("\tSection "+sectIndex+". "+name);
						System.out.println("\t\tOrig total participation rate: "+(float)totParticRate);
						System.out.println("\t\tOrig jump participation rate: "+(float)jumpParticRate);
						
					}
					double myProb;
					if (binsNotUsing == 0) {
						// all magnitude bins use this jump, default to just using the jump probability
						if (D) System.out.println("\t\tAll mag bins affected, using jumpProb="+(float)jumpProb);
						myProb = jumpProb;
					} else {
						// this is the segmentation-implied participation rate allotment for this jump
						double segJumpParticRate = totParticRate*jumpProb;
						// what fraction of the jump participation rate is allowed by the segmentation model?
						// this will be >1 if the segmentation constraint is more permissive than the input G-R
						double segFractOfAllotment = segJumpParticRate/jumpParticRate;
						if (D) {
							System.out.println("\t\tSeg-implied participation rate allotment: "+(float)segJumpParticRate);
							System.out.println("\t\tSeg allotment fract: "+(float)segFractOfAllotment);
						}
						if (segFractOfAllotment < 1) {
							// we need to make an adjustment
							// figure out the adjustment that results in the final MFD having the given probability
							// assigned to segmentation-affected bins
							double otherParticRate = totParticRate - jumpParticRate;
							Preconditions.checkState(otherParticRate > 0d,
									"Expected otherParticRate > 0 with %s bins not using this jump, but is %s. "
									+ "totParticRate=%s, jumpParticRate=%s",
									binsNotUsing, otherParticRate, totParticRate, jumpParticRate);
							double origMoRate = sectMFD.getTotalMomentRate();
							double curCorrProb = segFractOfAllotment;
							// MFD with bins for this jump removed
							IncrementalMagFreqDist ratesWithout = sectMFD.deepClone(); 
							for (int m=0; m<ratesWithout.size(); m++)
								if (jumpBitSet.get(m))
									ratesWithout.set(m, 0d);
							ratesWithout.scaleToTotalMomentRate(origMoRate);
							for (int corrIters=0; corrIters<iterations; corrIters++) {
								// assume that these bins are only affected by this jump
								// do this iteratively as the first one is likely to be an over-correction
								double corrJumpParticRate = 0d;
								double corrTotalParticRate = 0d;
								double oneMinus = 1d - curCorrProb;
								for (int m=0; m<nuclToParticScalars.length; m++) {
									int binIndex = m + sectMinMagIndexes[sectIndex];
									// bin rate were we to average the MFD with & without with the given probabilities
									double binRate = oneMinus*ratesWithout.getY(binIndex) + curCorrProb*sectMFD.getY(binIndex);
									double particRate = binRate*nuclToParticScalars[m];
									corrTotalParticRate += particRate;
									if (jumpBitSet.get(binIndex))
										corrJumpParticRate += particRate;
								}
								double effectiveProb = corrJumpParticRate/corrTotalParticRate;
								if (D) System.out.println("\t\t\titer "+corrIters+" curCorProb="+(float)curCorrProb+", eff = "
										+(float)corrJumpParticRate+" / "+(float)corrTotalParticRate+" = "+(float)effectiveProb);
								double delta = Math.abs(effectiveProb - jumpProb);
								double newProb;
								if (effectiveProb < jumpProb) {
									// over-correction
									newProb = Math.min(1d, curCorrProb + 0.5*delta);
								} else {
									// under-correction
									newProb = Math.max(0d, curCorrProb - 0.5*delta);
								}
								if ((float)newProb == (float)curCorrProb) {
									curCorrProb = newProb;
									break;
								} else {
									curCorrProb = newProb;
								}
							}
							myProb = curCorrProb;
						} else {
							// we don't need to make any adjustment, the allocated G-R share for these magnitude bins
							// already implies a lower participation rate than the segmentation model
							myProb = 1d;
						}
					}
					if (D) System.out.println("\t\tUpdated jump probability: "+(float)myProb);
					minProb = Math.min(minProb, myProb);
				}
			}
			if (D) System.out.println("\tRel-GR jumpProb="+(float)minProb+" (was "+(float)jumpProb+")");
			return minProb;
		}
	}
	
	private static class QuickAvgTrack {
		
		private double sum;
		private double first;
		private boolean allSame;
		private int count;
		
		public QuickAvgTrack() {
			sum = 0d;
			first = Double.NaN;
			allSame = true;
			count = 0;
		}
		
		public void add(double value) {
			Preconditions.checkState(value >= 0d);
			sum += value;
			if (count == 0)
				first = value;
			count++;
			allSame = allSame && value == first;
		}
		
		public double getAverage() {
			Preconditions.checkState(count > 0);
			if (allSame)
				return first;
			return sum/(double)count;
		}
	}

	public ThresholdAveragingSectNuclMFD_Estimator() {
		this(null);
	}

	public ThresholdAveragingSectNuclMFD_Estimator(List<? extends Number> fixedBinEdges) {
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
	
	protected abstract double calcRupProb(ClusterRupture rup);

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
//		FaultSystemRupSet rupSet = FaultSystemRupSet.load(
////				new File("/data/kevin/markdown/inversions/fm3_1_u3ref_uniform_reproduce_ucerf3.zip"));
//				new File("/data/kevin/markdown/inversions/fm3_1_u3ref_uniform_coulomb.zip"));
//		Region reg = new CaliforniaRegions.RELM_TESTING();
//		
//		rupSet = FaultSystemRupSet.buildFromExisting(rupSet)
////				.replaceFaultSections(DeformationModels.MEAN_UCERF3.build(FaultModels.FM3_1))
//				.replaceFaultSections(U3_UncertAddDeformationModels.U3_MEAN.build(FaultModels.FM3_1))
//				.forScalingRelationship(ScalingRelationships.MEAN_UCERF3)
//				.build();
		
		FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2022_07_29-nshm23_branches-NSHM23_v1p4-CoulombRupSet-DsrUni-TotNuclRate-SubB1-ThreshAvgIterRelGR/"
				+ "results_NSHM23_v1p4_CoulombRupSet_branch_averaged.zip"));
		Region reg = RupSetMapMaker.buildBufferedRegion(rupSet.getFaultSectionDataList());
		
		double bVal = 0.5d;
		Shaw07JumpDistProb segModel = Shaw07JumpDistProb.forHorzOffset(1d, 3d, 2d);
		
		File outputDir = new File("/tmp");

//		ThresholdAveragingSectNuclMFD_Estimator improb1 = new ThresholdAveragingSectNuclMFD_Estimator.WorstJumpProb(segModel);
//		String name1 = "Seg-Prob";
//		ThresholdAveragingSectNuclMFD_Estimator improb2 = new ThresholdAveragingSectNuclMFD_Estimator.RelGRWorstJumpProb(segModel);
//		String name2 = "Rel-GR";
//		String prefix = "thresh_avg_vs_rel_gr";

		ThresholdAveragingSectNuclMFD_Estimator improb1 =
//				new ThresholdAveragingSectNuclMFD_Estimator.WorstJumpProb(segModel);
				new ThresholdAveragingSectNuclMFD_Estimator.WorstAvgJumpProb(segModel);
		String name1 = "Seg-Prob";
		ThresholdAveragingSectNuclMFD_Estimator improb2 =
				new ThresholdAveragingSectNuclMFD_Estimator.RelGRWorstJumpProb(segModel, 100, true);
		String name2 = "Rel-GR-100-Iters";
		String prefix = "thresh_avg_vs_rel_gr_iter";

//		ThresholdAveragingSectNuclMFD_Estimator improb1 =
//				new ThresholdAveragingSectNuclMFD_Estimator.WorstAvgJumpProb(segModel);
//		String name1 = "Seg-Avg-Jump-Prob";
//		ThresholdAveragingSectNuclMFD_Estimator improb2 =
//				new ThresholdAveragingSectNuclMFD_Estimator.RelGRWorstJumpProb(segModel, 50);
//		String name2 = "Rel-GR-50-Iters";
//		String prefix = "thresh_avg_avg_jump_vs_rel_gr_iter";
		
		
//		ThresholdAveragingSectNuclMFD_Estimator improb1 =
//				new ThresholdAveragingSectNuclMFD_Estimator.RelGRWorstJumpProb(segModel, false, 50);
//		String name1 = "Rel-GR-100-Iter";
//		ThresholdAveragingSectNuclMFD_Estimator improb2 =
//				new ThresholdAveragingSectNuclMFD_Estimator.RelGRWorstJumpProb(segModel, true, 50);
//		String name2 = "Rel-GR-Floored-50-Iters";
//		String prefix = "thresh_avg_rel_gr_vs_floored";
		
		
//		ThresholdAveragingSectNuclMFD_Estimator improb1 = new ThresholdAveragingSectNuclMFD_Estimator.RelGRWorstJumpProb(segModel, 1, true);
//		String name1 = "Rel-GR-1-Iter";
//		ThresholdAveragingSectNuclMFD_Estimator improb2 = new ThresholdAveragingSectNuclMFD_Estimator.RelGRWorstJumpProb(segModel, 100, true);
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
		
		if (improb1 instanceof AbstractWorstJumpProb && improb2 instanceof AbstractWorstJumpProb) {
			DefaultXY_DataSet jumpProbScatter = new DefaultXY_DataSet();
			
			minNonZero = 1d;
			HashSet<Jump> prevJumps = new HashSet<>();
			for (ClusterRupture rup : rupSet.requireModule(ClusterRuptures.class)) {
				for (Jump jump : rup.getJumpsIterable()) {
					if (jump.fromSection.getSectionId() > jump.toSection.getSectionId())
						jump = jump.reverse();
					if (!prevJumps.contains(jump)) {
						double prob1 = ((AbstractWorstJumpProb)improb1).calcJumpProb(rup, jump);
						double prob2 = ((AbstractWorstJumpProb)improb2).calcJumpProb(rup, jump);
						
						if ((prob1 != 1d || prob2 != 1d) && (prob1 != 0d || prob2 != 0d)) {
							probScatter.set(prob1, prob2);
							if (prob1 > 0d)
								minNonZero = Math.min(minNonZero, prob1);
							if (prob2 > 0d)
								minNonZero = Math.min(minNonZero, prob2);
						}
						prevJumps.add(jump);
					}
				}
			}
			
			funcs = new ArrayList<>();
			chars = new ArrayList<>();
			
			range = new Range(Math.pow(10, Math.floor(Math.log10(minNonZero))), 1d);
			
			oneToOne = new DefaultXY_DataSet();
			oneToOne.set(range.getLowerBound(), range.getLowerBound());
			oneToOne.set(range.getUpperBound(), range.getUpperBound());
			
			funcs.add(oneToOne);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
			
			funcs.add(probScatter);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLACK));
			
			spec = new PlotSpec(funcs, chars, "Thresh-Avg Jump Probabilities", name1, name2);
			
			gp = PlotUtils.initHeadless();
			
			gp.drawGraphPanel(spec, true, true, range, range);
			
			PlotUtils.writePlots(outputDir, prefix+"_jump_probs", gp, 1000, false, true, false, false);
		}
		
		// now seg rates
		double[] rates1 = new double[rupSet.getNumSections()];
		double[] rates2 = new double[rupSet.getNumSections()];
		
		DefaultXY_DataSet rateScatter = new DefaultXY_DataSet();
		minNonZero = 1d;
		for (int s=0; s<rates1.length; s++) {
			rates1[s] = mfds1.get(s).calcSumOfY_Vals();
			rates2[s] = mfds2.get(s).calcSumOfY_Vals();
			rateScatter.set(rates1[s], rates2[s]);
			if (rates1[s] > 0d)
				minNonZero = Math.min(minNonZero, rates1[s]);
			if (rates2[s] > 0d)
				minNonZero = Math.min(minNonZero, rates2[s]);
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
