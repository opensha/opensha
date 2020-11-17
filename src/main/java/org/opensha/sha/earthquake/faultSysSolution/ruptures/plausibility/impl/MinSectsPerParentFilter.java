package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

/**
 * This enforces a minimum number of subsections per parent fault section. A special case can be
 * enabled (allowIfNoDirect) which allows a deficient cluster (i.e., one that has fewer than minPerParent
 * subsections) only if the clusters immediately before and after the deficient cluster can only connect
 * through that cluster. This allows deficient connections only in cases where they are required in order
 * to maintain connectivity.
 * 
 * If that special case is enabled, allowChained will allow multiple deficient clusters in a row, only
 * if each additional deficient cluster is the only way to connect.
 * 
 * @author kevin
 *
 */
public class MinSectsPerParentFilter implements PlausibilityFilter {
	
	private int minPerParent;
	private boolean allowIfNoDirect;
	private boolean allowChained;
	private ClusterConnectionStrategy connStrategy;

	/**
	 * @param minPerParent minimum number of subsections per cluster
	 * @param allowIfNoDirect if true, enable special case to allow violations if it's the only way to
	 * connect two clusters 
	 * @param allowChained if true and allowIfNoDirect, allow multiple chained violations
	 * @param connStrategy connection strategy, needed if allowIfNoDirect in order to test for direct
	 * connections
	 */
	public MinSectsPerParentFilter(int minPerParent, boolean allowIfNoDirect, boolean allowChained,
			ClusterConnectionStrategy connStrategy) {
		this.minPerParent = minPerParent;
		this.allowIfNoDirect = allowIfNoDirect;
		this.allowChained = allowChained;
		this.connStrategy = connStrategy;
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		PlausibilityResult result = apply(rupture.clusters, verbose);
		if (result.canContinue()) {
			for (Jump jump : rupture.splays.keySet()) {
				ClusterRupture splay = rupture.splays.get(jump);
				FaultSubsectionCluster[] strand = splay.clusters;
				if (allowIfNoDirect && strand[0].subSects.size() < minPerParent && strand.length > 1) {
					// add the parent to this splay
					List<FaultSection> beforeSects = new ArrayList<>();
					for (FaultSection sect : jump.fromCluster.subSects) {
						beforeSects.add(sect);
						if (sect.equals(jump.fromSection))
							break;
					}
					FaultSubsectionCluster[] newStrand = new FaultSubsectionCluster[strand.length+1];
					newStrand[0] = new FaultSubsectionCluster(beforeSects);
					System.arraycopy(strand, 0, newStrand, 1, strand.length);
					strand = newStrand;
				}
				result = result.logicalAnd(apply(strand, verbose));
			}
		}
		return result;
	}
	
	private boolean isDirectPossible(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		return connStrategy.areParentSectsConnected(from.parentSectionID, to.parentSectionID);
	}
	
	private PlausibilityResult apply(FaultSubsectionCluster[] clusters, boolean verbose) {
		if (clusters[0].subSects.size() < minPerParent) {
			// never allow on first cluster
			return PlausibilityResult.FAIL_HARD_STOP;
		}
		if (allowIfNoDirect) {
			// complicated case, make sure that we only allow deficient clusters if there is
			// no direct path between the cluster before and the cluster after.
			//
			// also, if !allowChained, ensure that we don't have multiple
			// deficient clusters in a row
			
			int streak = 0; // num deficient clusters in a row (clusters with size<minPerParent))
			for (int i=1; i<clusters.length; i++) {
				if (clusters[i].subSects.size() < minPerParent) {
					streak++;
					if (streak > 1) {
						if (!allowChained)
							// multiple deficient clusters in a row aren't allowed
							return PlausibilityResult.FAIL_HARD_STOP;
						// check that we couldn't have skipped the previous deficient cluster
						if (isDirectPossible(clusters[i-streak], clusters[i]))
							// direct was possible, hard stop
							return PlausibilityResult.FAIL_HARD_STOP;
					}
					if (!allowChained) {
						// ensure that we don't have multiple deficient clusters in a row
						if (streak > 1)
							return PlausibilityResult.FAIL_HARD_STOP;
					} else if (streak > 1) {
						
					}
					if (i == clusters.length-1)
						// last one in this strand, so future permutations/jumps could work
						// but it doesn't work as is (not connected to anything
						return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
					// we're in the middle of the strand, lets see if a direct connection was possible
					if (isDirectPossible(clusters[i-1], clusters[i+1]))
						// direct was possible, hard stop
						return PlausibilityResult.FAIL_HARD_STOP;
					if (streak > 1 && isDirectPossible(clusters[i-streak], clusters[i+1]))
						// we took multiple deficient jumps, but the full clusters on either side
						// are directly connected
						return PlausibilityResult.FAIL_HARD_STOP;
					// if we're here then there was a deficient cluster in the middle of the strand,
					// but it was the only way to make a connection between the previous and next
					// clusters, so it's allowed
				} else {
					// passed, reset the streak
					streak = 0;
				}
			}
			return PlausibilityResult.PASS;
		} else {
			// hard fail if any before the last are deficient
			for (int i=1; i<clusters.length-1; i++)
				if (clusters[i].subSects.size() < minPerParent)
					return PlausibilityResult.FAIL_HARD_STOP;
			// soft fail if just the last one is deficient (other permutations might work)
			if (clusters[clusters.length-1].subSects.size() < minPerParent)
				return PlausibilityResult.FAIL_FUTURE_POSSIBLE;
			return PlausibilityResult.PASS;
		}
	}

	@Override
	public String getShortName() {
		return "SectsPerParent";
	}

	@Override
	public String getName() {
		return "Min Sections Per Parent";
	}

	@Override
	public TypeAdapter<PlausibilityFilter> getTypeAdapter() {
		return new Adapter();
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		// only directional if splayed
		return splayed;
	}
	
	public static class Adapter extends PlausibilityFilterTypeAdapter {

		private ClusterConnectionStrategy connStrategy;

		@Override
		public void init(ClusterConnectionStrategy connStrategy,
				SectionDistanceAzimuthCalculator distAzCalc, Gson gson) {
			this.connStrategy = connStrategy;
		}

		@Override
		public void write(JsonWriter out, PlausibilityFilter value) throws IOException {
			MinSectsPerParentFilter filter = (MinSectsPerParentFilter)value;
			out.beginObject();
			out.name("minPerParent").value(filter.minPerParent);
			out.name("allowIfNoDirect").value(filter.allowIfNoDirect);
			out.name("allowChained").value(filter.allowChained);
			out.endObject();
		}

		@Override
		public PlausibilityFilter read(JsonReader in) throws IOException {
			in.beginObject();
			Integer minPerParent = null;
			Boolean allowIfNoDirect = null;
			boolean allowChained = false;
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "minPerParent":
					minPerParent = in.nextInt();
					break;
				case "allowIfNoDirect":
					allowIfNoDirect = in.nextBoolean();
					break;
				case "allowChained":
					allowChained = in.nextBoolean();
					break;

				default:
					break;
				}
			}
			
			in.endObject();
			return new MinSectsPerParentFilter(minPerParent, allowIfNoDirect, allowChained, connStrategy);
		}
		
	}

}
