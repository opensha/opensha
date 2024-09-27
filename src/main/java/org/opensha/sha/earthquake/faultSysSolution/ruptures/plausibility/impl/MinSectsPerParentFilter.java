package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

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
public class MinSectsPerParentFilter extends AbstractClusterSizeFilter {
	
	private int minPerParent;

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
		super(allowIfNoDirect, allowChained, connStrategy);
		this.minPerParent = minPerParent;
	}

	@Override
	boolean isClusterSufficient(FaultSubsectionCluster cluster) {
		return cluster.subSects.size() >= minPerParent;
	}

	@Override
	String getQuantityStr(FaultSubsectionCluster cluster) {
		return "sectsPerParent="+cluster.subSects.size();
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
