package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Adaptive connection strategy that adds an additional feature where the is a distance below which all connections
 * are include (r0), as well as a larger distance (rMax) up to which additional connections can be added, so long as
 * the total connection count from each section is below sectMax. Connections above r0 will be added in order of
 * increasing distance
 * 
 * @author kevin
 *
 */
public class AdaptiveClusterConnectionStrategy extends ClusterConnectionStrategy {

	private ClusterConnectionStrategy fullConnStrat;
	private double r0;
	private int sectMax;
	
	private transient Map<FaultSubsectionCluster, List<Jump>> fromJumpsMap;

	public AdaptiveClusterConnectionStrategy(ClusterConnectionStrategy fullConnStrat, double r0, int sectMax) {
		this(fullConnStrat, fullConnStrat.getRawClusters(), r0, sectMax);
	}
	
	private static List<FaultSubsectionCluster> cloneClusters(List<FaultSubsectionCluster> clusters) {
		List<FaultSubsectionCluster> ret = new ArrayList<>();
		for (FaultSubsectionCluster cluster : clusters)
			ret.add(new FaultSubsectionCluster(cluster.subSects, cluster.startSect, cluster.endSects));
		return ret;
	}

	public AdaptiveClusterConnectionStrategy(ClusterConnectionStrategy fullConnStrat,
			List<FaultSubsectionCluster> clusters, double r0, int sectMax) {
		super(fullConnStrat.getSubSections(), cloneClusters(clusters), fullConnStrat.getDistCalc());
		init(fullConnStrat, r0, sectMax);
	}
	
	private void init(ClusterConnectionStrategy fullConnStrat, double r0, int sectMax) {
		Preconditions.checkState(r0 >= 0, "r0 must be >= 0");
		this.r0 = r0;
		Preconditions.checkArgument(sectMax > 0);
		this.sectMax = sectMax;
		Preconditions.checkNotNull(fullConnStrat);
		this.fullConnStrat = fullConnStrat;
	}
	
	@Override
	public synchronized void checkBuildThreaded(int numThreads) {
		System.out.println("Building full threaded...");
		fullConnStrat.checkBuildThreaded(numThreads);
		System.out.println("Done building full, building me...");
		getJumpsFrom(getRawClusters().get(0));
		System.out.println("Done building");
	}
	
	private synchronized List<Jump> getJumpsFrom(FaultSubsectionCluster cluster) {
		if (fromJumpsMap == null) {
			Map<FaultSubsectionCluster, List<Jump>> possibleMap = new HashMap<>();
			
			List<FaultSubsectionCluster> clusters = getRawClusters();
			for (FaultSubsectionCluster c : clusters)
				possibleMap.put(c, new ArrayList<>());
			
			for (int c1=0; c1<clusters.size(); c1++) {
				FaultSubsectionCluster from = clusters.get(c1);
				for (int c2=c1+1; c2<clusters.size(); c2++) {
					FaultSubsectionCluster to = clusters.get(c2);
					
					List<Jump> possibles = fullConnStrat.buildPossibleConnections(from, to);
					if (possibles == null)
						continue;
					for (Jump jump : possibles) {
						possibleMap.get(from).add(jump);
						possibleMap.get(to).add(jump.reverse());
					}
				}
			}
			
			HashSet<Jump> allowedJumps = new HashSet<>();
			for (FaultSubsectionCluster from : possibleMap.keySet()) {
				List<Jump> jumps = possibleMap.get(from);
				// sort by increasing distance
				Collections.sort(jumps, Jump.dist_comparator);
				Map<Integer, Integer> sectCounts = new HashMap<>();
				Map<Integer, Double> sectMaxAlloweds = new HashMap<>();
				for (Jump jump : jumps) {
					int fromID = jump.fromSection.getSectionId();
					int sectCount = sectCounts.containsKey(fromID) ? sectCounts.get(fromID) : 0;
					double sectMaxAllowed = sectCount == 0 ? 0d : sectMaxAlloweds.get(fromID);
					
					boolean keep;
					if ((float)jump.distance <= (float)r0) {
						keep = true;
					} else {
						keep = sectMax < 0 || sectCount < sectMax;
						if (!keep && !allowedJumps.isEmpty() && (float)jump.distance == (float)sectMaxAllowed)
							// this is the same distance of one we kept, allow it as well
							keep = true;
					}
					if (keep) {
						sectCounts.put(fromID, sectCount+1);
						allowedJumps.add(jump);
						allowedJumps.add(jump.reverse());
						
						sectMaxAlloweds.put(fromID, Math.max(sectMaxAllowed, jump.distance));
					}
				}
			}
			
			Map<FaultSubsectionCluster, List<Jump>> fromJumpsMap = new HashMap<>();
			for (Jump jump : allowedJumps) {
				List<Jump> fromJumps = fromJumpsMap.get(jump.fromCluster);
				if (fromJumps == null) {
					fromJumps = new ArrayList<>();
					fromJumpsMap.put(jump.fromCluster, fromJumps);;
				}
				fromJumps.add(jump);
			}
			this.fromJumpsMap = fromJumpsMap;
		}
		return fromJumpsMap.get(cluster);
	}
	
	private List<Jump> doBuildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		List<Jump> possibleJumpsFrom = getJumpsFrom(from);
		if (possibleJumpsFrom == null)
			return null;
		List<Jump> allowed = new ArrayList<>();
		for (Jump jump : possibleJumpsFrom) {
			Preconditions.checkState(from.equals(jump.fromCluster));
			if (to.equals(jump.toCluster))
				allowed.add(jump);
		}
		if (allowed.isEmpty())
			return null;
		return allowed;
	}

	@Override
	protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		List<Jump> forwardJumps = doBuildPossibleConnections(from, to);
		List<Jump> backwardJumps = doBuildPossibleConnections(to, from);
		
		List<Jump> allowed = new ArrayList<>();
		if (forwardJumps != null)
			allowed.addAll(forwardJumps);
		// now see if there were any backward jumps
		// this can happen if 'from' is not isolated, but 'to' is
		if (backwardJumps != null && !backwardJumps.isEmpty()) {
			HashSet<IDPairing> currentJumps = new HashSet<>();
			for (Jump curJump : allowed)
				currentJumps.add(pair(curJump));
			for (Jump backwardJump : backwardJumps) {
				if (!currentJumps.contains(pair(backwardJump))) {
					// it's a new jump add it
					allowed.add(backwardJump.reverse());
				}
			}
		}
		return allowed;
	}
	
	private static IDPairing pair(Jump jump) {
		IDPairing pair = new IDPairing(jump.fromSection.getSectionId(), jump.toSection.getSectionId());
		if (pair.getID1() > pair.getID2())
			pair = pair.getReversed();
		return pair;
	}

	@Override
	public String getName() {
		if (sectMax != 1)
			return "Adaptive (r₀="+(float)r0+" km, sectMax="+sectMax+") "+fullConnStrat.getName();
		return "Adaptive (r₀="+(float)r0+" km) "+fullConnStrat.getName();
	}

	@Override
	public double getMaxJumpDist() {
		return fullConnStrat.getMaxJumpDist();
	}

}
