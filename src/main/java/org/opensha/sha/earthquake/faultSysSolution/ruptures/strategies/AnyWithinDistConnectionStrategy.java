package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * This includes all connections up to the maximum jump distance, which will often mean many possible connections for a
 * pair of fault sections.
 * 
 * @author kevin
 *
 */
public class AnyWithinDistConnectionStrategy extends ClusterConnectionStrategy {
	
	private SectionDistanceAzimuthCalculator distCalc;
	private double maxJumpDist;

	public AnyWithinDistConnectionStrategy(List<? extends FaultSection> subSects,
			SectionDistanceAzimuthCalculator distCalc, double maxJumpDist) {
		super(subSects, distCalc);
		this.maxJumpDist = maxJumpDist;
		this.distCalc = distCalc;
	}

	public AnyWithinDistConnectionStrategy(List<? extends FaultSection> subSects,
			List<FaultSubsectionCluster> clusters, SectionDistanceAzimuthCalculator distCalc,
			double maxJumpDist) {
		super(subSects, clusters, distCalc);
		this.maxJumpDist = maxJumpDist;
		this.distCalc = distCalc;
	}

	@Override
	protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		List<Jump> jumps = new ArrayList<>();
		for (FaultSection s1 : from.subSects) {
			for (FaultSection s2 : to.subSects) {
				double dist = distCalc.getDistance(s1, s2);
				// do everything to float precision to avoid system/OS dependent results
				if ((float)dist <= (float)maxJumpDist)
					jumps.add(new Jump(s1, from, s2, to, dist));
			}
		}
		if (jumps.isEmpty())
			return null;
		return jumps;
	}

	@Override
	public String getName() {
		return "AnyWihinDist: "+new DecimalFormat("0.#").format(maxJumpDist)+" km";
	}

	@Override
	public double getMaxJumpDist() {
		return maxJumpDist;
	}

}
