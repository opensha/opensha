package org.opensha.commons.logicTree.lhs;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;

import com.google.common.base.Preconditions;

final class PairwiseLogicTreeTools {
	
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

	private PairwiseLogicTreeTools() {}
	
	static class PairwiseMisfits {
		final int level1;
		final int level2;
		final double[][] expectations;
		int[][] counts;
		final PairwiseLogicTreeTools.ObjectiveFunction of;
		
		PairwiseMisfits(PairwiseLogicTreeTools.ObjectiveFunction of, int level1, int level2,
				double[] weights1, double[] weights2, int numSamples) {
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
		
		double addPairing(int l1, int l2, int i1, int i2) {
			return deltaPairing(l1, l2, i1, i2, 1);
		}
		
		double removePairing(int l1, int l2, int i1, int i2) {
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
		
		double getMisfit(int i1, int i2) {
			return of.calcMisfit(expectations[i1][i2], counts[i1][i2]);
		}
		
		double getMisfitSum() {
			double sum = 0d;
			for (int i1=0; i1<expectations.length; i1++)
				for (int i2=0; i2<expectations[i1].length; i2++)
					sum += getMisfit(i1, i2);
			return sum;
		}
	}

	static PairwiseMisfits[][] calcPairwiseMisfits(List<int[]> branchNodeIndexes,
			List<double[]> levelWeights, PairwiseLogicTreeTools.ObjectiveFunction of) {
		return calcPairwiseMisfits(branchNodeIndexes, levelWeights, null, of);
	}
	
	static PairwiseMisfits[][] calcPairwiseMisfits(List<int[]> branchNodeIndexes,
			List<double[]> levelWeights, boolean[][] includePairs,
			PairwiseLogicTreeTools.ObjectiveFunction of) {
		int numSamples = branchNodeIndexes.size();
		int numLevels = branchNodeIndexes.get(0).length;
		
		PairwiseMisfits[][] ret = new PairwiseMisfits[numLevels-1][];
		for (int l1=0; l1<numLevels-1; l1++) {
			double[] weights1 = levelWeights.get(l1);
			if (weights1 == null)
				continue;
			
			int numLeft = numLevels - l1 - 1;
			ret[l1] = new PairwiseMisfits[numLeft];
			for (int n=0; n<numLeft; n++) {
				int l2 = l1 + 1 + n;
				if (includePairs != null && !includePairs[l1][l2])
					continue;
				double[] weights2 = levelWeights.get(l2);
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
	
	static double calcMisfitSum(PairwiseMisfits[][] misfits) {
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
	
	static double swap(PairwiseMisfits[][] misfits, int[] branchIndexes1, int[] branchIndexes2, int levelIndex) {
		return swap(misfits, branchIndexes1, branchIndexes2, new int[] { levelIndex });
	}
	
	static double swap(PairwiseMisfits[][] misfits, int[] branchIndexes1, int[] branchIndexes2, int[] levelIndexes) {
		Set<Integer> levelIndexesSet = new HashSet<>();
		for (int levelIndex : levelIndexes)
			if (levelIndex >= 0)
				levelIndexesSet.add(levelIndex);
		
		int[] orig1 = new int[levelIndexes.length];
		int[] orig2 = new int[levelIndexes.length];
		for (int i=0; i<levelIndexes.length; i++) {
			int levelIndex = levelIndexes[i];
			if (levelIndex < 0)
				continue;
			orig1[i] = branchIndexes1[levelIndex];
			orig2[i] = branchIndexes2[levelIndex];
			branchIndexes1[levelIndex] = orig2[i];
			branchIndexes2[levelIndex] = orig1[i];
		}
		
		double deltaMisfit = 0d;
		int numLevels = branchIndexes1.length;
		for (int i=0; i<levelIndexes.length; i++) {
			int levelIndex = levelIndexes[i];
			if (levelIndex < 0)
				continue;
			for (int oLevel=0; oLevel<numLevels; oLevel++) {
				if (branchIndexes1[oLevel] == -1 || oLevel == levelIndex || levelIndexesSet.contains(oLevel))
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
				PairwiseMisfits pairMisfits = misfits[l1] == null ? null : misfits[l1][n];
				if (pairMisfits == null)
					continue;
				deltaMisfit += pairMisfits.removePairing(oLevel, levelIndex, branchIndexes1[oLevel], orig1[i]);
				deltaMisfit += pairMisfits.removePairing(oLevel, levelIndex, branchIndexes2[oLevel], orig2[i]);
				deltaMisfit += pairMisfits.addPairing(oLevel, levelIndex, branchIndexes1[oLevel], branchIndexes1[levelIndex]);
				deltaMisfit += pairMisfits.addPairing(oLevel, levelIndex, branchIndexes2[oLevel], branchIndexes2[levelIndex]);
			}
		}
		
		return deltaMisfit;
	}
	
	static void printPairwiseMisfitStats(List<? extends LogicTreeLevel<? extends LogicTreeNode>> levels,
			List<int[]> branchNodeIndexes, List<double[]> levelWeights) {
		printPairwiseMisfitStats(levels, branchNodeIndexes, levelWeights, null);
	}
	
	static void printPairwiseMisfitStats(List<? extends LogicTreeLevel<? extends LogicTreeNode>> levels,
			List<int[]> branchNodeIndexes, List<double[]> levelWeights, boolean[][] includePairs) {
		double sum = 0d;
		int numLevels = levels.size();
		System.out.println("Pairwise misfit stats with "+branchNodeIndexes.size()+" branches");
		DecimalFormat countDF = new DecimalFormat("0.000");
		for (int l1=0; l1<numLevels-1; l1++) {
			double[] weights1 = levelWeights.get(l1);
			if (weights1 == null)
				continue;
			
			System.out.println("Level "+l1+": "+levels.get(l1).getName());
			
			List<? extends LogicTreeNode> nodes1 = levels.get(l1).getNodes();
			
			int numLeft = numLevels - l1 - 1;
			for (int n=0; n<numLeft; n++) {
				int l2 = l1 + 1 + n;
				if (includePairs != null && !includePairs[l1][l2])
					continue;
				double[] weights2 = levelWeights.get(l2);
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

}
