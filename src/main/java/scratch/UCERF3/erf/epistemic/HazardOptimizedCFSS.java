package scratch.UCERF3.erf.epistemic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.common.base.Preconditions;

import scratch.UCERF3.CompoundFaultSystemSolution;
import scratch.UCERF3.FaultSystemSolutionFetcher;
import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.enumTreeBranches.ScalingRelationships;
import scratch.UCERF3.inversion.InversionFaultSystemRupSet;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;

public class HazardOptimizedCFSS extends FaultSystemSolutionFetcher {
	
	private CompoundFaultSystemSolution cfss;
	private List<LogicTreeBranch> branches;
	
	private InversionFaultSystemRupSet prevRupSet = null;

	public HazardOptimizedCFSS(CompoundFaultSystemSolution cfss) {
		this.cfss = cfss;
		this.branches = new ArrayList<>(cfss.getBranches());
		Collections.sort(branches, new ReadOptimizedBranchComparator());
		setCacheCopying(false);
	}

	@Override
	public Collection<LogicTreeBranch> getBranches() {
		return branches;
	}

	@Override
	protected synchronized InversionFaultSystemSolution fetchSolution(LogicTreeBranch branch) {
		if (prevRupSet == null) {
			// do a full load
			System.out.println("Loading first rupture set");
			InversionFaultSystemSolution sol = cfss.getSolution(branch);
			prevRupSet = sol.getRupSet();
			return sol;
		}
		FaultModels fm = branch.getValue(FaultModels.class);
		DeformationModels dm = branch.getValue(DeformationModels.class);
		ScalingRelationships scale = branch.getValue(ScalingRelationships.class);
		LogicTreeBranch prevBranch = prevRupSet.getLogicTreeBranch();
		if (fm != prevBranch.getValue(FaultModels.class) || dm != prevBranch.getValue(DeformationModels.class)
				|| scale != prevBranch.getValue(ScalingRelationships.class)) {
			System.out.println("New FM, DM, or Scaling Relationship, must load full new rupture set");
			InversionFaultSystemSolution sol = cfss.getSolution(branch);
			prevRupSet = sol.getRupSet();
			return sol;
		}
		
		// if we made it this far, we can just load in the rates and reuse the rupture set
		InversionFaultSystemRupSet invRupSet = new InversionFaultSystemRupSet(
				prevRupSet, branch, null, null, null, null, null);
		double[] rates = cfss.getRates(branch);
		return new InversionFaultSystemSolution(invRupSet, rates);
	}
	
	private class ReadOptimizedBranchComparator implements Comparator<LogicTreeBranch> {
		
		List<Class<? extends LogicTreeBranchNode<?>>> sortOrderClasses;
		
		public ReadOptimizedBranchComparator() {
			sortOrderClasses = new ArrayList<>();
			// default order is ideal
			sortOrderClasses.addAll(LogicTreeBranch.getLogicTreeNodeClasses());
		}

		@Override
		public int compare(LogicTreeBranch b1, LogicTreeBranch b2) {
			Preconditions.checkState(b1.size() == sortOrderClasses.size());
			Preconditions.checkState(b2.size() == sortOrderClasses.size());
			for (Class<? extends LogicTreeBranchNode<?>> clazz : sortOrderClasses) {
				LogicTreeBranchNode<?> val = b1.getValueUnchecked(clazz);
				LogicTreeBranchNode<?> oval = b2.getValueUnchecked(clazz);
				int cmp = val.getShortName().compareTo(oval.getShortName());
				if (cmp != 0)
					return cmp;
			}
			return 0;
		}
		
	}

}
