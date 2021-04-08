package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class SplayConnectionsOnlyFilter implements PlausibilityFilter {
	
	private boolean allowSectionEnd;
	
	private transient Map<Integer, FaultSubsectionCluster> fullClusters;

	public SplayConnectionsOnlyFilter(boolean applyToMainStrand) {
//		this.applyToMainStrand = applyToMainStrand;
	}

	@Override
	public String getShortName() {
		return "SplayConnOnly";
	}

	@Override
	public String getName() {
		return "Splays Equal Length";
	}
	
	private FaultSubsectionCluster getFullCluster(Integer parentID) {
		if (fullClusters == null) {
			synchronized (this) {
//				if (fullClusters == null) {
//					List<FaultSubsectionCluster> clusters = connStrat.getClusters();
//					Map<Integer, FaultSubsectionCluster> fullClusters = new HashMap<>();
//					for (FaultSubsectionCluster cluster : clusters)
//						fullClusters.put(cluster.parentSectionID, cluster);
//					this.fullClusters = fullClusters;
//				}
			}
		}
		return fullClusters.get(parentID);
	}

	@Override
	public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
		if (rupture.splays.isEmpty())
			return PlausibilityResult.PASS;
		RuptureTreeNavigator nav = rupture.getTreeNavigator();
//		if (applyToMainStrand)
//			return doApply(rupture, nav);
		PlausibilityResult result = PlausibilityResult.PASS;
		for (ClusterRupture splay : rupture.splays.values())
			result = result.logicalAnd(doApply(splay, nav));
		return result;
	}
	
	private PlausibilityResult doApply(ClusterRupture rupture, RuptureTreeNavigator nav) {
		PlausibilityResult result = PlausibilityResult.PASS;
		for (FaultSubsectionCluster cluster : rupture.clusters) {
			FaultSubsectionCluster full = getFullCluster(cluster.parentSectionID);
			FaultSection last = cluster.subSects.get(cluster.subSects.size()-1);
			int indexInFull = full.subSects.indexOf(last);
			Preconditions.checkState(indexInFull >= 0);
			boolean end = indexInFull == 0 || indexInFull == full.subSects.size()-1;
			if (end)
				// ends are always allowed
				continue;
			// not an end, see if it ends at a connection point
			if (full.getConnections(last).isEmpty())
				// not a connection point
				return PlausibilityResult.FAIL_HARD_STOP;
			// connection point, make sure it's used
			if (nav.getDescendants(last).isEmpty())
				// unused connection, fail but let it continue
				result = PlausibilityResult.FAIL_FUTURE_POSSIBLE;
		}
		for (ClusterRupture splay : rupture.splays.values())
			result = result.logicalAnd(doApply(splay, nav));
		return result;
	}

	@Override
	public boolean isDirectional(boolean splayed) {
		// only directional if splayed
		return splayed;
	}

}
