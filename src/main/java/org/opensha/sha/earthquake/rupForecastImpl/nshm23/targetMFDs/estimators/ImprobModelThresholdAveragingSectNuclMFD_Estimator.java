package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.estimators;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl.UncertainDataConstraint;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.JumpProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

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
		if (sectMomentRate.bestEstimate == 00d || availableRupIndexes.isEmpty() || curSectSupraSeisMFD.calcSumOfY_Vals() == 0d)
			// this is a zero rate section, do nothing
			return curSectSupraSeisMFD;
		
		boolean debug = sect.getSectionId() == DEBUG_SECT;
		
		// figure out each unique probability level, do so at slightly coarser floating-point resolution
		HashSet<Float> uniqueProbs = new HashSet<>();
		double minNonZeroProb = 1d;
		double maxProb = 0d;
		for (int rupIndex : availableRupIndexes) {
			if ((float)rupProbs[rupIndex] > 0f) {
				uniqueProbs.add((float)rupProbs[rupIndex]);
				minNonZeroProb = Math.min(rupProbs[rupIndex], minNonZeroProb);
				maxProb = Math.max(rupProbs[rupIndex], maxProb);
			}
		}
		if (debug) {
			System.out.println("Debug for "+sect.getSectionId()+". "+sect.getSectionName());
			System.out.println("Rupture prob range: ["+(float)minNonZeroProb+", "+(float)maxProb+"]");
		}
		if (maxProb == 0d)
			// all zero probability
			return curSectSupraSeisMFD;
		Preconditions.checkState(uniqueProbs.size() >= 1);
		
		List<Float> sortedProbs;
		if (fixedBinEdges == null) {
			// make sure our first bin starts at 1
			if (!uniqueProbs.contains(1f))
				uniqueProbs.add(1f);
			// sort by increasing probability
			sortedProbs = new ArrayList<>(uniqueProbs);
			Collections.sort(sortedProbs);
			
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
		List<Integer> unusedRups = new ArrayList<>(availableRupIndexes);
		
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
			for (int j=unusedRups.size(); --j>=0;) {
				int rupIndex = unusedRups.get(j);
				if ((float)rupProbs[rupIndex] > nextProb) {
					// this rupture lies within the current probability bin
					if (!availBins[rupMagIndexes[rupIndex]]) {
						// first one available for this magnitude
						availBins[rupMagIndexes[rupIndex]] = true;
						changed = true;
					}
					// this rupture has now been processed, can remove from further processing
					unusedRups.remove(j);
				}
			}
			
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

}
