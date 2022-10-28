package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.util.BranchAverageSolutionCreator;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

public class BranchParentSectParticMFDs extends AbstractBranchMFDs {
	
	public static class Builder extends AbstractBranchMFDs.Builder<BranchParentSectParticMFDs> {

		@Override
		protected SingleBranchMFDs getCachedMFDs(FaultSystemSolution sol) {
			return sol.getModule(SingleBranchParentParticMFDs.class);
		}

		@Override
		protected void cacheMFDs(FaultSystemSolution sol, SingleBranchMFDs branchMFDs) {
			SingleBranchParentParticMFDs module;
			if (branchMFDs instanceof SingleBranchParentParticMFDs)
				module = (SingleBranchParentParticMFDs)branchMFDs;
			else
				module = new SingleBranchParentParticMFDs(branchMFDs);
			sol.addModule(module);
		}

		@Override
		protected BranchParentSectParticMFDs init() {
			return new BranchParentSectParticMFDs();
		}

		@Override
		protected IncrementalMagFreqDist calcBranchSectMFD(FaultSystemSolution sol, int sectID,
				EvenlyDiscretizedFunc refMFD) {
			return sol.calcParticipationMFD_forParentSect(sectID, refMFD.getMinX(), refMFD.getMaxX(), refMFD.size());
		}
	}
	
	/**
	 * Transient module for storing branch-specific section MFDs. this allows them to be reused in the case that
	 * we perform multiple averaging operations, reducing memory requirements
	 * @author kevin
	 *
	 */
	private static class SingleBranchParentParticMFDs extends SingleBranchMFDs implements OpenSHA_Module {

		public SingleBranchParentParticMFDs(short[] sectMinMagIndexes, float[][] sectMFDs, int branchMinIndex,
				int branchMaxIndex) {
			super(sectMinMagIndexes, sectMFDs, branchMinIndex, branchMaxIndex);
		}

		public SingleBranchParentParticMFDs(SingleBranchMFDs branchMFDs) {
			super(branchMFDs);
		}

		@Override
		public String getName() {
			return "Single-Branch Parent Section Participation MFDs";
		}
		
	}
	
	private BranchParentSectParticMFDs() {}

	@Override
	public String getFileName() {
		return "branch_parent_sect_partic_mfds.csv";
	}

	@Override
	public String getName() {
		return "Branch Parent Section Participation MFDs";
	}

	@Override
	public boolean isParentSections() {
		return true;
	}
	
	public IncrementalMagFreqDist[] calcIncrementalSectFractiles(int sectID, double... fractiles) {
		return (IncrementalMagFreqDist[])calcFractiles(sectID, fractiles, false);
	}
	
	public EvenlyDiscretizedFunc[] calcCumulativeSectFractiles(int sectID, double... fractiles) {
		return calcFractiles(sectID, fractiles, true);
	}
	
	private EvenlyDiscretizedFunc[] calcFractiles(int sectID, double[] fractiles, boolean cumulative) {
		EvenlyDiscretizedFunc refMFD = cumulative ? this.refMFD.getCumRateDistWithOffset() : this.refMFD;
		
		int sectIndex = parentIDtoIndexMap.get(sectID);
		
		double[][] branchVals = new double[refMFD.size()][branchSectMFDs.length];
		
		for (int b=0; b<branchSectMFDs.length; b++) {
			IncrementalMagFreqDist branchMFD = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
			
			float[] mfdVals = branchSectMFDs[b][sectIndex];
			
			if (mfdVals == null || mfdVals.length == 0)
				// all zero for this branch
				continue;
			int minIndex = branchSectMinMagIndexes[b][sectIndex];
			Preconditions.checkState(branchMFD.size() >= minIndex+mfdVals.length);
			for (int i=0; i<mfdVals.length; i++)
				if (mfdVals[i] > 0f)
					branchMFD.set(i+minIndex, mfdVals[i]);
			if (cumulative) {
				EvenlyDiscretizedFunc branchCmlMFD = branchMFD.getCumRateDistWithOffset();
				for (int i=0; i<branchCmlMFD.size(); i++)
					branchVals[i][b] = branchCmlMFD.getY(i);
			} else {
				for (int i=0; i<branchMFD.size(); i++)
					branchVals[i][b] = branchMFD.getY(i);
			}
		}
		
		LightFixedXFunc[] normCDFs = new LightFixedXFunc[refMFD.size()];
		
		for (int i=0; i<normCDFs.length; i++)
			normCDFs[i] = ArbDiscrEmpiricalDistFunc.calcQuickNormCDF(branchVals[i], weights);
		
		EvenlyDiscretizedFunc[] ret = cumulative ?
				new EvenlyDiscretizedFunc[fractiles.length] : new IncrementalMagFreqDist[fractiles.length];
		
		for (int f=0; f<ret.length; f++) {
			Preconditions.checkState(fractiles[f] >= 0d && fractiles[f] <= 1d);
			if (cumulative)
				ret[f] = new EvenlyDiscretizedFunc(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
			else
				ret[f] = new IncrementalMagFreqDist(refMFD.getMinX(), refMFD.size(), refMFD.getDelta());
			for (int i=0; i<normCDFs.length; i++) {
				LightFixedXFunc ncdf = normCDFs[i];
				if ((float)fractiles[f] <= (float)ncdf.getMinY())
					ret[f].set(i, ncdf.getX(0));
				else if (fractiles[f] == 1d)
					ret[f].set(i, ncdf.getX(ncdf.size()-1));
				else
					ret[f].set(i, ncdf.getFirstInterpolatedX(fractiles[f]));
			}
		}
		
		return ret;
	}
	
	public static void main(String[] args) throws IOException {
		File dir = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2022_08_22-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR");
		File resultsFile = new File(dir, "results.zip");
		SolutionLogicTree slt = SolutionLogicTree.load(resultsFile);
		LogicTree<?> tree = slt.getLogicTree();
		File outputFile = new File(dir, "results_NSHM23_v2_CoulombRupSet_branch_averaged_sect_mfds.zip");
		BranchAverageSolutionCreator baCreator = new BranchAverageSolutionCreator(tree.getWeightProvider());
		int count = 0;
		for (LogicTreeBranch<?> branch : tree) {
			System.out.println("Loading solution for branch "+count);
			FaultSystemSolution sol = slt.forBranch(branch, false);
			baCreator.addSolution(sol, branch);
			System.out.println("DONE branch "+count);
			count++;
		}
		FaultSystemSolution ba = baCreator.build();
		ba.write(outputFile);
	}

}
