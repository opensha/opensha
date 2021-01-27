package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 * are include (r0), as well as a larger
 * 
 * @author kevin
 *
 */
public class AdaptiveDistCutoffClosestSectClusterConnectionStrategy extends ClusterConnectionStrategy {

	private SectionDistanceAzimuthCalculator distCalc;
	private double r0;
	private int nMin;
	private double rMax;
	
	private transient Map<FaultSubsectionCluster, List<Jump>> possibleJumpsRMax;

	public AdaptiveDistCutoffClosestSectClusterConnectionStrategy(List<? extends FaultSection> subSects,
			SectionDistanceAzimuthCalculator distCalc, double r0, int nMin, double rMax) {
		super(subSects);
		this.r0 = r0;
		Preconditions.checkState(r0 >= 0, "r0 must be >= 0");
		this.nMin = nMin;
		Preconditions.checkArgument(nMin >= 0, "nMin must be >=0");
		this.rMax = rMax;
		Preconditions.checkArgument(rMax >= r0, "rMax=%s should be >= r0=%s", rMax, r0);
		this.distCalc = distCalc;
	}

	public AdaptiveDistCutoffClosestSectClusterConnectionStrategy(List<? extends FaultSection> subSects,
			List<FaultSubsectionCluster> clusters, SectionDistanceAzimuthCalculator distCalc,
			double r0, int nMin, double rMax) {
		super(subSects, clusters);
		this.r0 = r0;
		Preconditions.checkState(r0 >= 0, "r0 must be >= 0");
		this.nMin = nMin;
		Preconditions.checkArgument(nMin >= 0, "nMin must be >=0");
		this.rMax = rMax;
		Preconditions.checkArgument(rMax >= r0, "rMax=%s should be >= r0=%s", rMax, r0);
		this.distCalc = distCalc;
	}
	
	private List<Jump> getPossibleJumpsToRMax(FaultSubsectionCluster cluster) {
		if (possibleJumpsRMax == null) {
			Map<FaultSubsectionCluster, List<Jump>> map = new HashMap<>();
			
			List<FaultSubsectionCluster> clusters = getRawClusters();
			
			for (int c1=0; c1<clusters.size(); c1++) {
				FaultSubsectionCluster from = clusters.get(c1);
				List<Jump> jumps = new ArrayList<>();
				for (int c2=c1+1; c2<clusters.size(); c2++) {
					FaultSubsectionCluster to = clusters.get(c2);
					
					Jump jump = null;
					for (FaultSection s1 : from.subSects) {
						for (FaultSection s2 : to.subSects) {
							double dist = distCalc.getDistance(s1, s2);
							// do everything to float precision to avoid system/OS dependent results
							if ((float)dist <= (float)rMax && (jump == null || (float)dist < (float)jump.distance))
								jump = new Jump(s1, from, s2, to, dist);
						}
					}
					
					if (jump != null)
						jumps.add(jump);
				}
				// sort by increasing distance
				Collections.sort(jumps, Jump.dist_comparator);
				map.put(from, jumps);
			}
			
			possibleJumpsRMax = map;
		}
		return possibleJumpsRMax.get(cluster);
	}

	@Override
	protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		List<Jump> possibleJumpsFrom = getPossibleJumpsToRMax(from);
		// these are sorted by increasing distance
		for (int i=0; i<possibleJumpsFrom.size(); i++) {
			Jump jump = possibleJumpsFrom.get(i);
			if (jump.toCluster.equals(to)) {
				// it's to the target cluster
				
				// include it if the distance is <= r0, or if we haven't yet hit nMin
				if ((float)jump.distance <= (float)r0 || i < nMin)
					return Lists.newArrayList(jump);
			}
		}
		return null;
	}

	@Override
	public String getName() {
		return "AdaptiveClosestSectPair: r0="+(float)r0+" km, nMin="+nMin+", rMax="+(float)rMax+" km";
	}

	@Override
	public double getMaxJumpDist() {
		return rMax;
	}

}
