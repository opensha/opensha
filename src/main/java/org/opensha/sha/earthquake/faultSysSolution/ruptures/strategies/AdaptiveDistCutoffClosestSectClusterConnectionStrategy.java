package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * Adaptive connection strategy that connects fault subsection clusters at their closest subsection pair. This adds an
 * additional feature to DistCutoffClosestSectClusterConnectionStrategy, where the is a distance below which all connections
 * are include (r0), as well as a larger distance (rMax) up to which additional connections can be added, so long as
 * the total cluster connection count is below clusterMax and the subsection connection count is below sectMax. Connections
 * above r0 will be added in order of increasing distance
 * 
 * @author kevin
 *
 */
public class AdaptiveDistCutoffClosestSectClusterConnectionStrategy extends ClusterConnectionStrategy {

	private SectionDistanceAzimuthCalculator distCalc;
	private double r0;
	private double rMax;
	private int clusterMax;
	private int sectMax;
	
	private transient Map<FaultSubsectionCluster, List<Jump>> fromJumpsMap;

	public AdaptiveDistCutoffClosestSectClusterConnectionStrategy(List<? extends FaultSection> subSects,
			SectionDistanceAzimuthCalculator distCalc, double r0, double rMax, int clusterMax, int sectMax) {
		super(subSects);
		init(distCalc, r0, rMax, clusterMax, sectMax);
	}

	public AdaptiveDistCutoffClosestSectClusterConnectionStrategy(List<? extends FaultSection> subSects,
			List<FaultSubsectionCluster> clusters, SectionDistanceAzimuthCalculator distCalc,
			double r0, double rMax, int clusterMax, int sectMax) {
		super(subSects, clusters);
		init(distCalc, r0, rMax, clusterMax, sectMax);
	}
	
	private void init(SectionDistanceAzimuthCalculator distCalc, double r0, double rMax, int clusterMax, int sectMax) {
		Preconditions.checkState(r0 >= 0, "r0 must be >= 0");
		Preconditions.checkArgument(rMax >= r0, "rMax=%s should be >= r0=%s", rMax, r0);
		this.r0 = r0;
		this.rMax = rMax;
		Preconditions.checkArgument(clusterMax >= 0 || sectMax >= 0,
				"at least one of clusterMax=%s sectMax=%s must be >=0", clusterMax, sectMax);
		this.clusterMax = clusterMax;
		this.sectMax = sectMax;
		Preconditions.checkNotNull(distCalc);
		this.distCalc = distCalc;
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
					
					Jump jump = null;
					for (FaultSection s1 : from.subSects) {
						for (FaultSection s2 : to.subSects) {
							double dist = distCalc.getDistance(s1, s2);
							// do everything to float precision to avoid system dependent results
							if ((float)dist <= (float)rMax && (jump == null || (float)dist < (float)jump.distance))
								jump = new Jump(s1, from, s2, to, dist);
						}
					}
					
					if (jump != null) {
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
				Map<Integer, Integer> sectCounts = sectMax >= 0 ? new HashMap<>() : null;
				int clusterCount = 0;
				for (Jump jump : jumps) {
					int fromID = jump.fromSection.getSectionId();
					int sectCount = -1;
					if (sectMax >= 0)
						sectCount = sectCounts.containsKey(fromID) ? sectCounts.get(fromID) : 0;
					
					boolean keep;
					if ((float)jump.distance <= (float)r0) {
						keep = true;
					} else {
						// need to check against sect and cluster max
						boolean clusterPass = clusterMax < 0 || clusterCount < clusterMax;
						boolean sectPass = sectMax < 0 || sectCount < sectMax;
						keep = clusterPass && sectPass;
					}
					if (keep) {
						clusterCount++;
						if (sectMax >= 0)
							sectCounts.put(fromID, sectCount+1);
						allowedJumps.add(jump);
						allowedJumps.add(jump.reverse());
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

	@Override
	protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		List<Jump> possibleJumpsFrom = getJumpsFrom(from);
		if (possibleJumpsFrom == null)
			return null;
		for (Jump jump : possibleJumpsFrom) {
			Preconditions.checkState(from.equals(jump.fromCluster));
			if (to.equals(jump.toCluster))
				return Lists.newArrayList(jump);
		}
		return null;
	}

	@Override
	public String getName() {
		return "AdaptiveClosestSectPair: r0="+(float)r0+" km, nMin="+clusterMax+", rMax="+(float)rMax+" km";
	}

	@Override
	public double getMaxJumpDist() {
		return rMax;
	}

}
