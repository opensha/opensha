package scratch.UCERF3;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemSolution;
import scratch.UCERF3.logicTree.LogicTreeBranch;
import scratch.UCERF3.logicTree.LogicTreeBranchNode;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * This abstract class is used to fetch solutions for a set of logic tree branches. Implementations can
 * load solutions on demand to reduce memory consumption.
 * @author kevin
 *
 */
public abstract class FaultSystemSolutionFetcher implements Iterable<InversionFaultSystemSolution> {
	
	private boolean cacheCopying = true;
	// this is for copying caches from previous rup sets of the same fault model
	private Map<FaultModels, FaultSystemRupSet> rupSetCacheMap = Maps.newHashMap();
	
	public abstract Collection<LogicTreeBranch> getBranches();
	
	protected abstract InversionFaultSystemSolution fetchSolution(LogicTreeBranch branch);
	
	public InversionFaultSystemSolution getSolution(LogicTreeBranch branch) {
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
	
	public double[] getRates(LogicTreeBranch branch) {
		return getSolution(branch).getRateForAllRups();
	}
	
	public double[] getMags(LogicTreeBranch branch) {
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
			
			private Iterator<LogicTreeBranch> branchIt = getBranches().iterator();

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

	public static FaultSystemSolutionFetcher getRandomSample(
			final FaultSystemSolutionFetcher fetch, int num) {
		return getRandomSample(fetch, num, null);
	}
	
	public static FaultSystemSolutionFetcher getRandomSample(
			final FaultSystemSolutionFetcher fetch, int num, LogicTreeBranchNode<?>... branchNodes) {
		List<LogicTreeBranch> origBranches = Lists.newArrayList();
		origBranches.addAll(fetch.getBranches());
		final List<LogicTreeBranch> branches = Lists.newArrayList();
		Random r = new Random();
		LogicTreeBranch testBranch = null;
		if (branchNodes != null && branchNodes.length > 0)
			testBranch = LogicTreeBranch.fromValues(false, branchNodes);
		for (int i=0; i<num; i++) {
			if (testBranch == null) {
				branches.add(origBranches.get(r.nextInt(origBranches.size())));
			} else {
				LogicTreeBranch branch = null;
				while (branch == null || !testBranch.matchesNonNulls(branch))
					branch = origBranches.get(r.nextInt(origBranches.size()));
				branches.add(branch);
			}
		}
		return new FaultSystemSolutionFetcher() {
			
			@Override
			public Collection<LogicTreeBranch> getBranches() {
				return branches;
			}
			
			@Override
			protected InversionFaultSystemSolution fetchSolution(LogicTreeBranch branch) {
				return fetch.fetchSolution(branch);
			}
		};
	}
	
	public static FaultSystemSolutionFetcher getSubset(
			final FaultSystemSolutionFetcher fetch, final LogicTreeBranchNode<?>... nodes) {
		final List<LogicTreeBranch> branches = Lists.newArrayList();
		LogicTreeBranch testBranch = LogicTreeBranch.fromValues(false, nodes);
		for (LogicTreeBranch branch : fetch.getBranches()) {
			if (testBranch.matchesNonNulls(branch))
				branches.add(branch);
		}
		return getSubsetSample(fetch, branches);
	}
	
	public static FaultSystemSolutionFetcher getSubsetSample(
			final FaultSystemSolutionFetcher fetch, final List<LogicTreeBranch> branches) {
		return new FaultSystemSolutionFetcher() {
			
			@Override
			public Collection<LogicTreeBranch> getBranches() {
				return branches;
			}
			
			@Override
			protected InversionFaultSystemSolution fetchSolution(LogicTreeBranch branch) {
				return fetch.fetchSolution(branch);
			}
		};
	}

}
