package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * This cluster connection strategy can be used with synthetic catalogs. It starts by including all input jumps,
 * then adds possible connections that weren't used up to a maximum distance.
 * 
 * @author kevin
 *
 */
public class InputJumpsOrDistClusterConnectionStrategy extends ClusterConnectionStrategy {

	private SectionDistanceAzimuthCalculator distCalc;
	private double maxJumpDist;
	private HashSet<IDPairing> allowedSectConnections;

	public InputJumpsOrDistClusterConnectionStrategy(List<? extends FaultSection> subSects,
			SectionDistanceAzimuthCalculator distCalc, double maxJumpDist, Collection<Jump> inputJumps) {
		super(subSects);
		this.maxJumpDist = maxJumpDist;
		this.distCalc = distCalc;
		initAllowedSectConnections(inputJumps);
	}

	public InputJumpsOrDistClusterConnectionStrategy(List<? extends FaultSection> subSects,
			List<FaultSubsectionCluster> clusters, SectionDistanceAzimuthCalculator distCalc,
			double maxJumpDist, Collection<Jump> inputJumps) {
		super(subSects, clusters);
		this.maxJumpDist = maxJumpDist;
		this.distCalc = distCalc;
		initAllowedSectConnections(inputJumps);
	}
	
	private void initAllowedSectConnections(Collection<Jump> jumps) {
		allowedSectConnections = new HashSet<>();
		for (Jump jump : jumps) {
			IDPairing pair = new IDPairing(jump.fromSection.getSectionId(), jump.toSection.getSectionId());
			allowedSectConnections.add(pair);
			allowedSectConnections.add(pair.getReversed());
		}
	}

	@Override
	protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		List<Jump> ret = new ArrayList<>();
		Jump closestJump = null;
		for (FaultSection s1 : from.subSects) {
			for (FaultSection s2 : to.subSects) {
				double dist = distCalc.getDistance(s1, s2);
				if (allowedSectConnections.contains(new IDPairing(s1.getSectionId(), s2.getSectionId())))
					// it's an input jump
					ret.add(new Jump(s1, from, s2, to, dist));
				else if ((float)dist <= (float)maxJumpDist && (closestJump == null || (float)dist < (float)closestJump.distance))
					// do everything to float precision to avoid system/OS dependent results
					closestJump = new Jump(s1, from, s2, to, dist);
			}
		}
		if (ret.isEmpty() && closestJump != null) {
			// add closest jump since no other jumps allowed
//			System.out.println("Added fallback closest from "+from+" to "+to+": "+closestJump);
			ret.add(closestJump);
		}
		if (ret.isEmpty())
			return null;
		return ret;
	}

	@Override
	public String getName() {
		return "InputPlusDist: maxDist="+(float)maxJumpDist+" km";
	}

	@Override
	public double getMaxJumpDist() {
		return maxJumpDist;
	}

}
