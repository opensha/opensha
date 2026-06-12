package org.opensha.commons.logicTree;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.numbers.core.Precision;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnableLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.BinnedLevel;

import com.google.common.base.Preconditions;

public class LogicTreePairwiseLHSIteration<E extends LogicTreeNode> {
	
	private List<LogicTreeLevel<? extends E>> levels;
	private List<LogicTreeBranch<E>> branches;
	private List<double[]> levelFixedWeights;
	private int numLevelsToIterate;
	private List<LogicTreeLevel<?>> pairwiseLevels;
	private List<Integer> binnedLevelIndexes;
	
	private boolean trackSwaps = false;
	private List<int[]> originalBranchIndexes;
	
	// DEADBAND of 0.5 means no penalty for doing the impossible: matching a fractional count
	public static final double DEADBAND = 0.5;
	
	public enum ObjectiveFunction {
		RAW_MISFIT {
			@Override
			public double calcMisfit(double expected, int actual) {
				return Math.abs(expected - actual);
			}
		},
		L1_DEADBAND {
			@Override
			public double calcMisfit(double expected, int actual) {
				return Math.max(0, Math.abs(expected - actual) - DEADBAND);
			}
		},
		L2_DEADBAND {
			@Override
			public double calcMisfit(double expected, int actual) {
				double l1 = L1_DEADBAND.calcMisfit(expected, actual);
				return l1*l1;
			}
		};
		
		public abstract double calcMisfit(double expected, int actual);
	}

	// this seems to do the best
	public static ObjectiveFunction OBJECTIVE_FUNCTION_DEFAULT = ObjectiveFunction.RAW_MISFIT;
//	public static ObjectiveFunction OBJECTIVE_FUNCTION_DEFAULT = ObjectiveFunction.L1_DEADBAND;
//	public static ObjectiveFunction OBJECTIVE_FUNCTION_DEFAULT = ObjectiveFunction.L2_DEADBAND;

	public LogicTreePairwiseLHSIteration(List<LogicTreeLevel<? extends E>> levels, List<LogicTreeBranch<E>> branches,
			List<double[]> levelFixedWeights) {
		Preconditions.checkState(levelFixedWeights.size() == levels.size());
		Preconditions.checkState(levels.size() > 1);
		Preconditions.checkState(branches.size() > 1);
		this.levels = levels;
		this.branches = branches;
		this.levelFixedWeights = levelFixedWeights;
		
		pairwiseLevels = new ArrayList<>(levels);
		numLevelsToIterate = 0;
		binnedLevelIndexes = new ArrayList<>(levels.size());
		for (int l=0; l<levels.size(); l++) {
			LogicTreeLevel<? extends E> level = levels.get(l);
			double[] weights = levelFixedWeights.get(l);
			if (weights != null) {
				double sum = 0d;
				int numNonZero = 0;
				for (double weight : weights) {
					if (weight > 0)
						numNonZero++;
					sum += weight;
				}
				if (numNonZero == 1) {
					levelFixedWeights.set(l, null);
					continue;
				}
				if ((float)sum != 1f) {
					for (int i=0; i<weights.length; i++)
						weights[i] /= sum;
				}
				numLevelsToIterate++;
			} else if (level instanceof BinnableLevel<?,?,?>) {
				LogicTreeLevel<? extends LogicTreeNode> binnedLevel = ((BinnableLevel<?,?,?>)level).toBinnedLevel();
				binnedLevelIndexes.add(l);
				List<? extends LogicTreeNode> binnedNodes = binnedLevel.getNodes();
				weights = new double[binnedNodes.size()];
				double sum = 0d;
				int numNonZero = 0;
				for (int n=0; n<binnedNodes.size(); n++) {
					weights[n] = binnedNodes.get(n).getNodeWeight(null);
					sum += weights[n];
					if (weights[n] > 0)
						numNonZero++;
				}
				if (numNonZero == 1)
					continue;
				if ((float)sum != 1f) {
					for (int i=0; i<weights.length; i++)
						weights[i] /= sum;
				}
				levelFixedWeights.set(l, weights);
				pairwiseLevels.set(l, binnedLevel);
				numLevelsToIterate++;
			}
		}
	}
	
	public void iterate(int numIterations, Random r, boolean verbose) {
		iterate(numIterations, OBJECTIVE_FUNCTION_DEFAULT, r, verbose);
	}
	
	public void iterate(int numIterations, ObjectiveFunction of, Random r, boolean verbose) {
		if (numLevelsToIterate < 2) {
			System.err.println("WARNING: won't pairwise-iterate LHS sampling because we only have "+numLevelsToIterate+" candidate level");
			return;
		}
		
		int numSamples = branches.size();
		
		System.out.println("Pairwise iterating "+numSamples+" LHS samples with "+numIterations+" iterations");
		
		// remap each branch instead onto a set of indexes of their (possibly-binned) values
		List<int[]> branchNodeIndexes = new ArrayList<>(numSamples);
		if (trackSwaps)
			originalBranchIndexes = new ArrayList<>(numSamples);
		for (int b=0; b<numSamples; b++) {
			int[] indexes = new int[levels.size()];
			for (int i=0; i<indexes.length; i++)
				indexes[i] = -1;
			branchNodeIndexes.add(indexes);
			if (trackSwaps) {
				int[] branchIndexes = new int[levels.size()];
				for (int i=0; i<branchIndexes.length; i++)
					branchIndexes[i] = b;
				originalBranchIndexes.add(branchIndexes);
			}
		}
		for (int l=0; l<levels.size(); l++) {
			double[] weights = levelFixedWeights.get(l);
			if (weights == null)
				continue;
			LogicTreeLevel<?> level = pairwiseLevels.get(l);
			BinnedLevel<?, ? extends LogicTreeNode> binnedLevel = null;
			if (binnedLevelIndexes.contains(l)) {
				binnedLevel = (BinnedLevel<?, ? extends LogicTreeNode>)level;
			}
			List<? extends LogicTreeNode> nodes = pairwiseLevels.get(l).getNodes();
			Preconditions.checkState(nodes.size() == weights.length);
			for (int b=0; b<numSamples; b++) {
				LogicTreeNode node = branches.get(b).getValue(l);
				if (binnedLevel != null) {
					node = binnedLevel.getBinUnchecked(node);
					Preconditions.checkNotNull(node);
				}
				int index = nodes.indexOf(node);
				Preconditions.checkState(index >= 0 && index < weights.length, "bad index=%s for %s with %s weights",
						index, node.getName(), weights.length);
				branchNodeIndexes.get(b)[l] = index;
			}
		}
		
		
		PairwiseMisfits[][] misfits = calcPairwiseMisfits(branchNodeIndexes, levelFixedWeights, of);
		if (verbose) {
			System.out.println("===============================");
			System.out.println("Initial misfits:");
			printPairwiseMisfitStats(pairwiseLevels, branchNodeIndexes, levelFixedWeights);
			System.out.println("===============================");
		}
		double misfit = calcMisfitSum(misfits);
		double misfit0 = misfit;
		DecimalFormat pDF = new DecimalFormat("0.00%");
		for (int n=0; n<numIterations; n++) {
			if (verbose && n % 1000 == 0) {
				System.out.println("Pairwise misfit iteration "+n+"; misfit="+(float)misfit+"; reduction="+pDF.format((misfit0-misfit)/misfit0));
			}
			// branch indexes for which we will (possibly) swap a level
			int branchIndex1 = r.nextInt(numSamples);
			int branchIndex2 = r.nextInt(numSamples);
			while (branchIndex1 == branchIndex2)
				branchIndex2 = r.nextInt(numSamples);
			
			// level index which we will (possibly) swap values
			int levelIndex = r.nextInt(levels.size());
			while (levelFixedWeights.get(levelIndex) == null)
				levelIndex = r.nextInt(levels.size());
			
			int[] branchIndexes1 = branchNodeIndexes.get(branchIndex1);
			int[] branchIndexes2 = branchNodeIndexes.get(branchIndex2);
			if (branchIndexes1[levelIndex] == branchIndexes2[levelIndex])
				// already the same, no swap possible
				continue;
			
			// do the swap
			double deltaMisfit = swap(misfits, branchIndexes1, branchIndexes2, levelIndex);
			if (deltaMisfit > 0 || ((float)deltaMisfit == 0f && r.nextBoolean())) {
				// we made things worse, reverse it
				swap(misfits, branchIndexes1, branchIndexes2, levelIndex);
			} else {
				// we improved things, keep it
				misfit += deltaMisfit;
				// apply the swap to the actual branch values
				E value1 = branches.get(branchIndex1).getValue(levelIndex);
				E value2 = branches.get(branchIndex2).getValue(levelIndex);
				branches.get(branchIndex1).setValue(levelIndex, value2);
				branches.get(branchIndex2).setValue(levelIndex, value1);
				if (trackSwaps) {
					int prevIndex1 = originalBranchIndexes.get(branchIndex1)[levelIndex];
					int prevIndex2 = originalBranchIndexes.get(branchIndex2)[levelIndex];
					originalBranchIndexes.get(branchIndex1)[levelIndex] = prevIndex2;
					originalBranchIndexes.get(branchIndex2)[levelIndex] = prevIndex1;
				}
			}
		}
		// recalculate it to check against any drift
		misfits = calcPairwiseMisfits(branchNodeIndexes, levelFixedWeights, of);
		double finalMisfit = calcMisfitSum(misfits);
		Preconditions.checkState(Precision.equals(misfit, finalMisfit, 1e-3),
				"Misfit drift! Calculated final=%s, iterated=%s, diff=%s", finalMisfit, misfit, misfit-finalMisfit);
		if (verbose)
			System.out.println("===============================");
		System.out.println("Final misfit after "+numIterations+" iterations: "+(float)finalMisfit+"; reduction="+pDF.format((misfit0-finalMisfit)/misfit0));
		if (verbose) {
			printPairwiseMisfitStats(pairwiseLevels, branchNodeIndexes, levelFixedWeights);
			System.out.println("Misfits for each possible objective function (we used "+of.name()+"):");
			for (ObjectiveFunction of2 : ObjectiveFunction.values())
				System.out.println("\t"+of2.name()+":\t"+(float)calcMisfitSum(calcPairwiseMisfits(branchNodeIndexes, levelFixedWeights, of2)));
			System.out.println("===============================");
		}
	}
	
	public void setTrackSwaps(boolean trackSwaps) {
		this.trackSwaps = trackSwaps;
		this.originalBranchIndexes = null;
	}
	
	public List<int[]> getOriginalBranchIndexes() {
		Preconditions.checkNotNull(originalBranchIndexes, "trackSwaps must be true and set before iterate");
		return originalBranchIndexes;
	}
	
	private static class PairwiseMisfits {
		final int level1;
		final int level2;
		final double[][] expectations;
		int[][] counts;
		final ObjectiveFunction of;
		
		public PairwiseMisfits(ObjectiveFunction of, int level1, int level2, double[] weights1, double[] weights2, int numSamples) {
			this.of = of;
			this.level1 = level1;
			this.level2 = level2;
			// initialize with full misfits (full expected values, no pairings encountered)
			expectations = new double[weights1.length][weights2.length];
			for (int i1=0; i1<weights1.length; i1++)
				for (int i2=0; i2<weights2.length; i2++)
					expectations[i1][i2] = weights1[i1] * weights2[i2] * numSamples;
			counts = new int[weights1.length][weights2.length];
		}
		
		public double addPairing(int l1, int l2, int i1, int i2) {
			return deltaPairing(l1, l2, i1, i2, 1);
		}
		
		public double removePairing(int l1, int l2, int i1, int i2) {
			return deltaPairing(l1, l2, i1, i2, -1);
		}
		
		private double deltaPairing(int l1, int l2, int i1, int i2, int delta) {
			double prev, updated;
			if (l1 == level1) {
				Preconditions.checkState(l2 == level2);
				prev = getMisfit(i1, i2);
				counts[i1][i2] += delta;
				updated = getMisfit(i1, i2);
			} else {
				Preconditions.checkState(l1 == level2);
				Preconditions.checkState(l2 == level1);
				// indexes are swapped
				prev = getMisfit(i2, i1);
				counts[i2][i1] += delta;
				updated = getMisfit(i2, i1);
			}
			// return the change in absolute misfit
			return updated - prev;
		}
		
		public double getMisfit(int l1, int l2, int i1, int i2) {
			if (l1 == level1) {
				Preconditions.checkState(l2 == level2);
				return getMisfit(i1, i2);
			} else {
				Preconditions.checkState(l1 == level2);
				Preconditions.checkState(l2 == level1);
				return getMisfit(i2, i1);
			}
		}
		
		public double getMisfit(int i1, int i2) {
			return of.calcMisfit(expectations[i1][i2], counts[i1][i2]);
		}
		
		public double getMisfitSum() {
			double sum = 0d;
			for (int i1=0; i1<expectations.length; i1++)
				for (int i2=0; i2<expectations[i1].length; i2++)
					sum += getMisfit(i1, i2);
			return sum;
		}
	}
	
	private static double swap(PairwiseMisfits[][] misfits, int[] branchIndexes1, int[] branchIndexes2, int levelIndex) {
		// do the swap
		int orig1 = branchIndexes1[levelIndex];
		int orig2 = branchIndexes2[levelIndex];
		branchIndexes1[levelIndex] = orig2;
		branchIndexes2[levelIndex] = orig1;
		// update misfits
		double deltaMisfit = 0d;
		int numLevels = branchIndexes1.length;
		for (int oLevel=0; oLevel<numLevels; oLevel++) {
			if (branchIndexes1[oLevel] == -1 || oLevel == levelIndex)
				// not a level we're tracking
				continue;
			
			int l1, l2;
			if (oLevel < levelIndex) {
				l1 = oLevel;
				l2 = levelIndex;
			} else {
				l1 = levelIndex;
				l2 = oLevel;
			}
			int n = l2 - l1 - 1;
			deltaMisfit += misfits[l1][n].removePairing(oLevel, levelIndex, branchIndexes1[oLevel], orig1);
			deltaMisfit += misfits[l1][n].removePairing(oLevel, levelIndex, branchIndexes2[oLevel], orig2);
			deltaMisfit += misfits[l1][n].addPairing(oLevel, levelIndex, branchIndexes1[oLevel], branchIndexes1[levelIndex]);
			deltaMisfit += misfits[l1][n].addPairing(oLevel, levelIndex, branchIndexes2[oLevel], branchIndexes2[levelIndex]);
		}
		// return the change in misfit
		return deltaMisfit;
	}
	
	private static double calcMisfitSum(PairwiseMisfits[][] misfits) {
		double ret = 0d;
		for (PairwiseMisfits[] outerPairwise : misfits) {
			if (outerPairwise == null)
				continue;
			for (PairwiseMisfits innerPairwise : outerPairwise) {
				if (innerPairwise == null)
					continue;
				ret += innerPairwise.getMisfitSum();
			}
		}
		return ret;
	}
	
	private static void printPairwiseMisfitStats(List<? extends LogicTreeLevel<? extends LogicTreeNode>> levels,
			List<int[]> branchNodeIndexes, List<double[]> levelFixedWeights) {
		double sum = 0d;
		int numLevels = levels.size();
		System.out.println("Pairwise misfit stats with "+branchNodeIndexes.size()+" branches");
		DecimalFormat countDF = new DecimalFormat("0.000");
		for (int l1=0; l1<numLevels-1; l1++) {
			double[] weights1 = levelFixedWeights.get(l1);
			if (weights1 == null)
				continue;
			
			System.out.println("Level "+l1+": "+levels.get(l1).getName());
			
			List<? extends LogicTreeNode> nodes1 = levels.get(l1).getNodes();
			
			int numLeft = numLevels - l1 - 1;
			for (int n=0; n<numLeft; n++) {
				int l2 = l1 + 1 + n;
				double[] weights2 = levelFixedWeights.get(l2);
				if (weights2 == null)
					continue;
				
				System.out.println("\tPaired with level "+l2+": "+levels.get(l2).getName());
				List<? extends LogicTreeNode> nodes2 = levels.get(l2).getNodes();
				for (int i1=0; i1<nodes1.size(); i1++) {
					if (weights1[i1] == 0d)
						continue;
					System.out.print("\t\t"+nodes1.get(i1).getShortName()+":");
					for (int i2=0; i2<nodes2.size(); i2++) {
						if (weights2[i2] == 0d)
							continue;
						System.out.print("\t"+nodes2.get(i2).getShortName());
						int samples = 0;
						double expected = weights1[i1] * weights2[i2] * branchNodeIndexes.size();
						for (int[] branch : branchNodeIndexes)
							if (branch[l1] == i1 && branch[l2] == i2)
								samples++;
						sum += Math.abs(samples - expected);
						System.out.print(" [n="+samples+", e="+countDF.format(expected)+", delta="+countDF.format(samples-expected)+"];");
					}
					System.out.println();
				}
			}
		}
		System.out.println("Total pairwise misfit: "+(float)sum);
	}
	
	private static PairwiseMisfits[][] calcPairwiseMisfits(List<int[]> branchNodeIndexes,
			List<double[]> levelFixedWeights, ObjectiveFunction of) {
		int numSamples = branchNodeIndexes.size();
		int numLevels = branchNodeIndexes.get(0).length;
		
		PairwiseMisfits[][] ret = new PairwiseMisfits[numLevels-1][];
		for (int l1=0; l1<numLevels-1; l1++) {
			double[] weights1 = levelFixedWeights.get(l1);
			if (weights1 == null)
				continue;
			
			int numLeft = numLevels - l1 - 1;
			ret[l1] = new PairwiseMisfits[numLeft];
			for (int n=0; n<numLeft; n++) {
				int l2 = l1 + 1 + n;
				double[] weights2 = levelFixedWeights.get(l2);
				if (weights2 == null)
					continue;
				ret[l1][n] = new PairwiseMisfits(of, l1, l2, weights1, weights2, numSamples);
				
				for (int b=0; b<numSamples; b++) {
					int[] branchIndexes = branchNodeIndexes.get(b);
					ret[l1][n].addPairing(l1, l2, branchIndexes[l1], branchIndexes[l2]);
				}
			}
		}
		
		return ret;
	}

}
