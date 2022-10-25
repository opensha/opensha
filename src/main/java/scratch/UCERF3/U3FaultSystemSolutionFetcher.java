package scratch.UCERF3;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.logicTree.U3LogicTreeBranch;
import scratch.UCERF3.logicTree.U3LogicTreeBranchNode;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * This abstract class is used to fetch solutions for a set of logic tree branches. Implementations can
 * load solutions on demand to reduce memory consumption.
 * @author kevin
 *
 */
public abstract class U3FaultSystemSolutionFetcher implements Iterable<InversionFaultSystemSolution> {
	
	private boolean cacheCopying = true;
	// this is for copying caches from previous rup sets of the same fault model
	private Map<FaultModels, U3FaultSystemRupSet> rupSetCacheMap = Maps.newHashMap();
	
	public abstract Collection<U3LogicTreeBranch> getBranches();
	
	protected abstract InversionFaultSystemSolution fetchSolution(U3LogicTreeBranch branch);
	
	public InversionFaultSystemSolution getSolution(U3LogicTreeBranch branch) {
		InversionFaultSystemSolution sol = fetchSolution(branch);
		if (cacheCopying) {
			synchronized (this) {
				FaultModels fm = sol.getRupSet().getFaultModel();
				if (rupSetCacheMap.containsKey(fm)) {
					sol.getRupSet().copyCacheFrom(rupSetCacheMap.get(fm));
				} else {
					rupSetCacheMap.put(fm, sol.getRupSet());
				}
			}
		}
		return sol;
	}
	
	public double[] getRates(U3LogicTreeBranch branch) {
		return getSolution(branch).getRateForAllRups();
	}
	
	public double[] getMags(U3LogicTreeBranch branch) {
		return getSolution(branch).getRupSet().getMagForAllRups();
	}

	public boolean isCacheCopyingEnabled() {
		return cacheCopying;
	}

	public void setCacheCopying(boolean cacheCopying) {
		this.cacheCopying = cacheCopying;
	}

	@Override
	public Iterator<InversionFaultSystemSolution> iterator() {
		return new Iterator<InversionFaultSystemSolution>() {
			
			private Iterator<U3LogicTreeBranch> branchIt = getBranches().iterator();

			@Override
			public boolean hasNext() {
				return branchIt.hasNext();
			}

			@Override
			public InversionFaultSystemSolution next() {
				return getSolution(branchIt.next());
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException("Not supported by this iterator");
			}
		};
	}
	
	public static double calcScaledAverage(double[] vals, double[] weights) {
		if (vals.length == 1)
			return vals[0];
		double tot = 0d;
		for (double weight : weights)
			tot += weight;
		
		double scaledAvg = 0;
		for (int i=0; i<vals.length; i++) {
			scaledAvg += vals[i] * (weights[i] / tot);
		}
	
		return scaledAvg;
	}
	
	public static U3FaultSystemSolutionFetcher getRandomSample(
			final U3FaultSystemSolutionFetcher fetch, int num, U3LogicTreeBranchNode<?>... branchNodes) {
		List<U3LogicTreeBranch> origBranches = Lists.newArrayList();
		origBranches.addAll(fetch.getBranches());
		final List<U3LogicTreeBranch> branches = Lists.newArrayList();
		Random r = new Random();
		U3LogicTreeBranch testBranch = null;
		if (branchNodes != null && branchNodes.length > 0)
			testBranch = U3LogicTreeBranch.fromValues(false, branchNodes);
		for (int i=0; i<num; i++) {
			if (testBranch == null) {
				branches.add(origBranches.get(r.nextInt(origBranches.size())));
			} else {
				U3LogicTreeBranch branch = null;
				while (branch == null || !testBranch.matchesNonNulls(branch))
					branch = origBranches.get(r.nextInt(origBranches.size()));
				branches.add(branch);
			}
		}
		return new U3FaultSystemSolutionFetcher() {
			
			@Override
			public Collection<U3LogicTreeBranch> getBranches() {
				return branches;
			}
			
			@Override
			protected InversionFaultSystemSolution fetchSolution(U3LogicTreeBranch branch) {
				return fetch.fetchSolution(branch);
			}
		};
	}
	
	public static U3FaultSystemSolutionFetcher getSubset(
			final U3FaultSystemSolutionFetcher fetch, final U3LogicTreeBranchNode<?>... nodes) {
		final List<U3LogicTreeBranch> branches = Lists.newArrayList();
		U3LogicTreeBranch testBranch = U3LogicTreeBranch.fromValues(false, nodes);
		for (U3LogicTreeBranch branch : fetch.getBranches()) {
			if (testBranch.matchesNonNulls(branch))
				branches.add(branch);
		}
		return getSubsetSample(fetch, branches);
	}
	
	public static U3FaultSystemSolutionFetcher getSubsetSample(
			final U3FaultSystemSolutionFetcher fetch, final List<U3LogicTreeBranch> branches) {
		return new U3FaultSystemSolutionFetcher() {
			
			@Override
			public Collection<U3LogicTreeBranch> getBranches() {
				return branches;
			}
			
			@Override
			protected InversionFaultSystemSolution fetchSolution(U3LogicTreeBranch branch) {
				return fetch.fetchSolution(branch);
			}
		};
	}

}
