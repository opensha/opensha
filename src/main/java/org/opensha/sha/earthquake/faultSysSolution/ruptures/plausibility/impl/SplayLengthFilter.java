package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class SplayLengthFilter implements PlausibilityFilter {
	
	private double maxLen;
	private boolean isFractOfMain;
	private boolean totalAcrossSplays;

	/**
	 * 
	 * @param maxLen maximum splay length
	 * @param isFractOfMain if true, maxLen is a fractional length of the primary rupture
	 * @param totalAcrossSplays if true, maxLen is applied as a sum of all splays
	 */
	public SplayLengthFilter(double maxLen, boolean isFractOfMain, boolean totalAcrossSplays) {
		this.maxLen = maxLen;
		this.isFractOfMain = isFractOfMain;
		this.totalAcrossSplays = totalAcrossSplays;
	}

	@Override
	public String getShortName() {
		return "StrandLen";
	}

	@Override
	public String getName() {
		return "Strand Length";
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.splays.isEmpty())
			return PlausibilityResult.PASS;
		double maxLen = isFractOfMain ? this.maxLen*calcLen(rupture, null, false) : this.maxLen;
		if (verbose)
			System.out.println(getShortName()+": maxLen="+maxLen);
		double totSplay = 0d;
		for (ClusterRupture splay : rupture.splays.values()) {
			double splayLen = calcLen(splay, null, true);
			if (verbose)
				System.out.println(getShortName()+": splay with length="+splayLen);
			if (totalAcrossSplays) {
				totSplay += splayLen;
				if ((float)totSplay > (float)maxLen) {
					if (verbose)
						System.out.println(getShortName()+": failing with cumulative length="+totSplay);
					return PlausibilityResult.FAIL_HARD_STOP;
				}
			} else {
				if ((float)splayLen > (float)maxLen) {
					if (verbose)
						System.out.println(getShortName()+": failing");
					return PlausibilityResult.FAIL_HARD_STOP;
				}
			}
		}
		return PlausibilityResult.PASS;
	}
	
	private static double calcLen(ClusterRupture rupture, FaultSubsectionCluster addition, boolean takeSplays) {
		double len = 0d;
		if (rupture != null)
			for (FaultSubsectionCluster cluster : rupture.clusters)
				for (FaultSection sect : cluster.subSects)
					len += sect.getTraceLength();
		if (addition != null)
			for (FaultSection sect : addition.subSects)
				len += sect.getTraceLength();
		if (takeSplays && rupture != null)
			for (ClusterRupture splay : rupture.splays.values())
				len += calcLen(splay, null, true);
		return len;
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		// only directional if splayed
		return splayed;
	}

}
