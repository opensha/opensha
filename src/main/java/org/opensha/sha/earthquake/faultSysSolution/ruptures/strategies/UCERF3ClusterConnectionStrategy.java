package org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies;

import java.util.List;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.collect.Lists;

import scratch.UCERF3.inversion.coulomb.CoulombRates;

public class UCERF3ClusterConnectionStrategy extends ClusterConnectionStrategy {
	
	private SectionDistanceAzimuthCalculator distCalc;
	private double maxJumpDist;
	private CoulombRates coulombRates;

	public UCERF3ClusterConnectionStrategy(List<? extends FaultSection> subSects,
			SectionDistanceAzimuthCalculator distCalc, double maxJumpDist, CoulombRates coulombRates) {
		super(subSects, distCalc);
		this.distCalc = distCalc;
		this.maxJumpDist = maxJumpDist;
		this.coulombRates = coulombRates;
	}

	@Override
	protected List<Jump> buildPossibleConnections(FaultSubsectionCluster from, FaultSubsectionCluster to) {
		Jump jump = null;
		for (FaultSection s1 : from.subSects) {
			for (FaultSection s2 : to.subSects) {
				double dist = distCalc.getDistance(s1, s2);
				// do everything to float precision to avoid system/OS dependent results
				if ((float)dist <= (float)maxJumpDist && (jump == null || (float)dist <= (float)jump.distance)) {
					if (jump != null && (float)dist == (float)jump.distance) {
						// this 2nd check in floating point precision gets around an issue where the distance is identical
						// within floating point precision for 2 sub sections on the same section. if we don't do this check
						// than the actual "closer" section could vary depending on the machine/os used. in this case, just
						// use the one that's in coulomb. If neither are in coulomb, then keep the first occurrence (lower ID)
						
						boolean prevValCoulomb = false;
						boolean curValCoulomb = false;
						if (coulombRates != null) {
							prevValCoulomb = coulombRates.containsKey(new IDPairing(
									jump.fromSection.getSectionId(), jump.toSection.getSectionId()));
							curValCoulomb = coulombRates.containsKey(new IDPairing(
									s1.getSectionId(), s2.getSectionId()));
						}
//						System.out.println("IT HAPPENED!!!!!! "+subSectIndex1+"=>"+subSectIndex2+" vs "
//										+data1.getSectionId()+"=>"+data2.getSectionId());
//						System.out.println("prevValCoulomb="+prevValCoulomb+"\tcurValCoulomb="+curValCoulomb);
						if (prevValCoulomb)
							// this means that the previous value is in coulomb, use that!
							continue;
						if (!curValCoulomb) {
							// this means that either no coulomb values were supplied, or neither choice is in coulomb. lets use
							// the first one for consistency
							continue;
						}
					}
					jump = new Jump(s1, from, s2, to, dist);
				}
			}
		}
		if (jump == null)
			return null;
		return Lists.newArrayList(jump);
	}

	@Override
	public String getName() {
		String name = "UCERF3: maxDist="+(float)maxJumpDist+" km";
		if (coulombRates != null)
			name += ", precomputed Coulomb pairings";
		return name;
	}

	@Override
	public double getMaxJumpDist() {
		return maxJumpDist;
	}

}
