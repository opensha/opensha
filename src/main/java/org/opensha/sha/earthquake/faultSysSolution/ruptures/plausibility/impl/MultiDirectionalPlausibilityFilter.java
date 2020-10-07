package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * Some plausibility filters are directional, e.g., a rupture will pass if built in one direction (from A->B)
 * but fail in another (from B->A). The rupture building algorithm generally gets around this by trying every
 * possible starting point, but if you are doing filter tests against an already build rupture set you can
 * wrap a plausibility filter with this in order to test all directions. 
 * @author kevin
 *
 */
public class MultiDirectionalPlausibilityFilter implements PlausibilityFilter {
	
	private PlausibilityFilter filter;
	private RuptureConnectionSearch connSearch;
	private boolean onlyWhenSplayed;

	public MultiDirectionalPlausibilityFilter(PlausibilityFilter filter,
			RuptureConnectionSearch connSearch, boolean onlyWhenSplayed) {
		this.filter = filter;
		this.connSearch = connSearch;
		this.onlyWhenSplayed = onlyWhenSplayed;
	}

	@Override
	public String getShortName() {
		return filter.getShortName();
	}

	@Override
	public String getName() {
		return filter.getName();
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		PlausibilityResult result;
		RuntimeException error = null;
		try {
			result = filter.apply(rupture, verbose);
			if (result.isPass() || onlyWhenSplayed && rupture.splays.isEmpty())
				return result;
		} catch (RuntimeException e) {
			if (onlyWhenSplayed && rupture.splays.isEmpty())
				throw e;
			result = null;
			error = e;
		}
		// try other paths through the rupture
		for (ClusterRupture altRupture : rupture.getInversions(connSearch)) {
			try {
				result = filter.apply(altRupture, verbose);
				if (result.isPass())
					return result;
			} catch (RuntimeException e) {
				error = e;
			}
		}
		if (result == null && error != null)
			throw error;
		return result;
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		return apply(rupture.take(newJump), verbose);
	}

}
