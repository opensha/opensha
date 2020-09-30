package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;

import com.google.common.collect.Range;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * Splay count plausibility filter. Not necessary when building ruptures (just use maxSplays on the builder)
 * but can be useful when testing externally built ruptures
 * 
 * @author kevin
 *
 */
public class SplayCountFilter implements ScalarValuePlausibiltyFilter<Integer> {
	
	private int maxSplays;

	public SplayCountFilter(int maxSplays) {
		this.maxSplays = maxSplays;
	}

	@Override
	public String getShortName() {
		if (maxSplays == 0)
			return "NoSplays";
		else if (maxSplays > 1)
			return "Max"+maxSplays+"Splays";
		return "Max1Splay";
	}

	@Override
	public String getName() {
		if (maxSplays == 0)
			return "No Splays";
		else if (maxSplays > 1)
			return "Max "+maxSplays+" Splays";
		return "Max 1 Splay";
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		int count = countSplays(rupture);
		if (verbose)
			System.out.println("Have "+count+" splays");
		if (count > maxSplays)
			return PlausibilityResult.FAIL_HARD_STOP;
		return PlausibilityResult.PASS;
	}
	
	public int countSplays(ClusterRupture rupture) {
		int tot = rupture.splays.size();
		for (ClusterRupture splay : rupture.splays.values())
			tot += countSplays(splay);
		return tot;
	}
	
	private boolean isContinuationJump(ClusterRupture rupture, Jump newJump) {
		if (rupture.containsInternal(newJump.fromSection)) {
			// it's on the primary strand, check if it's an addition off of the end
			return rupture.clusters[rupture.clusters.length-1].endSects.contains(newJump.fromSection);
		}
		// it's on a splay, see if it's a splay continuation or a splay off of a splay
		for (ClusterRupture splay : rupture.splays.values())
			if (splay.contains(newJump.fromSection))
				return isContinuationJump(splay, newJump);
		throw new IllegalStateException("newJump.fromSection isn't contained by the rupture");
	}

	@Override
	public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
		int count = countSplays(rupture);
		// now check the jump
		if (!isContinuationJump(rupture, newJump))
			count++;
		if (verbose)
			System.out.println("Have "+count+" splays");
		if (count > maxSplays)
			return PlausibilityResult.FAIL_HARD_STOP;
		return PlausibilityResult.PASS;
	}

	@Override
	public Integer getValue(ClusterRupture rupture) {
		return countSplays(rupture);
	}

	@Override
	public Integer getValue(ClusterRupture rupture, Jump newJump) {
		return getValue(rupture.take(newJump));
	}

	@Override
	public Range<Integer> getAcceptableRange() {
		return Range.atMost(maxSplays);
	}
	
	@Override
	public String getScalarName() {
		return "Number of Splays";
	}

	@Override
	public String getScalarUnits() {
		return null;
	}

}
