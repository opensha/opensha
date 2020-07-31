package scratch.UCERF3.inversion.ruptures.strategies;

import java.util.List;

import org.opensha.commons.util.IDPairing;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.inversion.coulomb.CoulombRates;
import scratch.UCERF3.inversion.ruptures.FaultSubsectionCluster;
import scratch.UCERF3.inversion.ruptures.Jump;
import scratch.UCERF3.inversion.ruptures.util.SectionDistanceAzimuthCalculator;

public class UCERF3ClusterConnectionStrategy implements ClusterConnectionStrategy {
	
	private double maxJumpDist;
	private CoulombRates coulombRates;

	public UCERF3ClusterConnectionStrategy(double maxJumpDist, CoulombRates coulombRates) {
		this.maxJumpDist = maxJumpDist;
		this.coulombRates = coulombRates;
	}

	@Override
	public int addConnections(List<FaultSubsectionCluster> clusters, SectionDistanceAzimuthCalculator distCalc) {
		// TODO Auto-generated method stub
		
		int count = 0;
		for (int c1=0; c1<clusters.size(); c1++) {
			FaultSubsectionCluster cluster1 = clusters.get(c1);
			for (int c2=c1+1; c2<clusters.size(); c2++) {
				FaultSubsectionCluster cluster2 = clusters.get(c2);
				Jump jump = null;
				for (FaultSection s1 : cluster1.subSects) {
					for (FaultSection s2 : cluster2.subSects) {
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
//								System.out.println("IT HAPPENED!!!!!! "+subSectIndex1+"=>"+subSectIndex2+" vs "
//												+data1.getSectionId()+"=>"+data2.getSectionId());
//								System.out.println("prevValCoulomb="+prevValCoulomb+"\tcurValCoulomb="+curValCoulomb);
								if (prevValCoulomb)
									// this means that the previous value is in coulomb, use that!
									continue;
								if (!curValCoulomb) {
									// this means that either no coulomb values were supplied, or neither choice is in coulomb. lets use
									// the first one for consistency
									continue;
								}
							}
							jump = new Jump(s1, cluster1, s2, cluster2, dist);
						}
					}
				}
				if (jump != null) {
					cluster1.addConnection(jump);
					cluster2.addConnection(jump.reverse());
					count++;
				}
			}
		}
		return count;
	}

}
