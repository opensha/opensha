package org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;

class SlipRateAllowedRupsFinder {
	
	/**
	 * This calculates ruptures that can be used by {@link SupraSeisBValInversionTargetMFDs}, filtering out those
	 * that use more than <code>maxAllowedZeroRateSects</code> subsections with zero slip rates.
	 * 
	 * If <code>maxAllowedZeroRateSects > 1</code>, included ruptures must start and end on nonzero slip rate
	 * subsections, unless the ending (or only) subsection cluster contains only 2 subsections. That special case
	 * allows for jumps to (or isolated rupture of) 2 subsection clusters with only 1 subsection with a nonzero slip rate.
	 * 
	 * @param rupSet
	 * @param zeroRateSects
	 * @param externalSubset
	 * @param maxAllowedZeroRateSects
	 * @return
	 */
	static BitSet calcAllowedRuptures(FaultSystemRupSet rupSet, boolean[] zeroRateSects, BitSet externalSubset,
			int maxAllowedZeroRateSects) {
		Preconditions.checkArgument(maxAllowedZeroRateSects >= 0);
		Preconditions.checkArgument(zeroRateSects.length == rupSet.getNumSections());
		BitSet allowedRups = new BitSet(rupSet.getNumRuptures());
		
		ClusterRuptures cRups = rupSet.requireModule(ClusterRuptures.class);
		for (int rupIndex=0; rupIndex<rupSet.getNumRuptures(); rupIndex++) {
			if (externalSubset != null && !externalSubset.get(rupIndex))
				// excluded externally
				continue;
			
			int rupZeroSectCount = 0;
			for (int sect : rupSet.getSectionsIndicesForRup(rupIndex))
				if (zeroRateSects[sect])
					rupZeroSectCount++;
			
			boolean allowed = false;
			if (rupZeroSectCount <= maxAllowedZeroRateSects) {
				// conditionally assume that it's allowed
				allowed = true;
				if (rupZeroSectCount > 0) {
					// uses zero rate sections
					ClusterRupture rup = cRups.get(rupIndex);
					
					// make sure all clusters have at least one nonzero section
					for (FaultSubsectionCluster cluster : rup.getClustersIterable()) {
						boolean clusterNonZero = false;
						for (FaultSection sect : cluster.subSects)
							clusterNonZero |= !zeroRateSects[sect.getSectionId()];
						if (!clusterNonZero) {
							allowed = false;
							break;
						}
					}
					if (!allowed)
						continue;
					
					// make sure it doesn't end on any zero rate sections
					// special case: still include if the end section only has 2 subsections
					List<FaultSubsectionCluster> endClusters = new ArrayList<>(2+rup.getTotalNumSplays());
					endClusters.add(rup.clusters[0].reversed());
					if (rup.singleStrand)
						endClusters.add(rup.clusters[rup.clusters.length-1]);
					else
						findEndsRecursive(rup.getTreeNavigator(), rup.clusters[0], endClusters);
					
					for (FaultSubsectionCluster endCluster : endClusters) {
						int numSects = endCluster.subSects.size();
						int endID = endCluster.subSects.get(numSects-1).getSectionId();
						if (zeroRateSects[endID] && numSects > 2) {
							// ends on a zero rate section (and is more than 2 subsections)
							allowed = false;
						}
					}
				} else {
					// no zero rate sections, allow
					allowed = true;
				}
			}
			
			if (allowed)
				allowedRups.set(rupIndex);
		}
		
		return allowedRups;
	}
	
	private static void findEndsRecursive(RuptureTreeNavigator nav, FaultSubsectionCluster curCluster,
			List<FaultSubsectionCluster> ends) {
		Collection<FaultSubsectionCluster> descendants = nav.getDescendants(curCluster);
		if (descendants.isEmpty()) {
			// this is an end
			ends.add(curCluster);
		} else {
			// keep searching for end(s)
			for (FaultSubsectionCluster dest : descendants)
				findEndsRecursive(nav, dest, ends);
		}
	}

}
