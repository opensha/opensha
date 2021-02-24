package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.text.DecimalFormat;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.collect.Lists;

public class DistCutoffClosestSectClusterConnectionStrategy extends ClusterConnectionStrategy {

	private SectionDistanceAzimuthCalculator distCalc;
	private double maxJumpDist;

	public DistCutoffClosestSectClusterConnectionStrategy(List<? extends FaultSection> subSects,
			SectionDistanceAzimuthCalculator distCalc, double maxJumpDist) {
		super(subSects);
		this.maxJumpDist = maxJumpDist;
		this.distCalc = distCalc;
	}

	public DistCutoffClosestSectClusterConnectionStrategy(List<? extends FaultSection> subSects,
			List<FaultSubsectionCluster> clusters, SectionDistanceAzimuthCalculator distCalc,
			double maxJumpDist) {
		super(subSects, clusters);
		this.maxJumpDist = maxJumpDist;
		this.distCalc = distCalc;
	}

	@Override
	protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		Jump jump = null;
		for (FaultSection s1 : from.subSects) {
			for (FaultSection s2 : to.subSects) {
				double dist = distCalc.getDistance(s1, s2);
				// do everything to float precision to avoid system/OS dependent results
				if ((float)dist <= (float)maxJumpDist && (jump == null || (float)dist < (float)jump.distance))
					jump = new Jump(s1, from, s2, to, dist);
			}
		}
		if (jump == null)
			return null;
		return Lists.newArrayList(jump);
	}

	@Override
	public String getName() {
		return "ClosestSectPair: maxDist="+new DecimalFormat("0.#").format(maxJumpDist)+" km";
	}

	@Override
	public double getMaxJumpDist() {
		return maxJumpDist;
	}

}
