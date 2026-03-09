package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import java.util.*;
import java.util.stream.Collectors;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionProximityIndex;
import org.opensha.sha.faultSurface.FaultSection;

/**
 * Spatial lookup that pre-indexes target ruptures (listB) so that nearby ruptures can be found
 * quickly for any fault section. Uses {@link SectionProximityIndex} for spatial queries.
 *
 * <p>Immutable after construction. {@link #findNearbyRuptures} is safe for concurrent use.
 */
public class RuptureProximityLookup {

    private final SectionProximityIndex proximityIndex;
    private final Map<Integer, List<ClusterRupture>> sectionToRuptures;
    private final double maxDistKm;

    /**
     * @param distCalc calculator covering all sections from both listA and listB
     * @param listB target ruptures to index
     * @param maxDistKm maximum distance threshold in km
     */
    public RuptureProximityLookup(
            SectionDistanceAzimuthCalculator distCalc,
            List<ClusterRupture> listB,
            double maxDistKm) {
        this.maxDistKm = maxDistKm;
        this.sectionToRuptures = new HashMap<>();

        listB = listB.stream().filter(cr -> cr.buildOrderedSectionList().stream().mapToDouble(s -> s.getArea(false)).sum() >= 100000000).collect(Collectors.toList());

        // Collect unique sections from listB and build reverse map
        Map<Integer, FaultSection> uniqueSections = new LinkedHashMap<>();
        for (ClusterRupture rupture : listB) {
            for (FaultSubsectionCluster cluster : rupture.clusters) {
                for (FaultSection sect : cluster.subSects) {
                    int id = sect.getSectionId();
                    uniqueSections.put(id, sect);
                    sectionToRuptures.computeIfAbsent(id, k -> new ArrayList<>()).add(rupture);
                }
            }
        }

        this.proximityIndex =
                new SectionProximityIndex(distCalc, new ArrayList<>(uniqueSections.values()));
    }

    /**
     * Returns deduplicated set of listB ruptures that have any section within maxDistKm of the
     * query section.
     */
    public Collection<ClusterRupture> findNearbyRuptures(int sectionId) {
        List<Integer> nearbySectionIds = proximityIndex.findWithin(sectionId, maxDistKm);
        // Use IdentityHashMap-backed set since ClusterRupture has no equals/hashCode override
        Set<ClusterRupture> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int nearbyId : nearbySectionIds) {
            List<ClusterRupture> ruptures = sectionToRuptures.get(nearbyId);
            if (ruptures != null) {
                result.addAll(ruptures);
            }
        }
        return result;
    }

    public Map<Integer, Collection<ClusterRupture>> findNearbyRuptures(Collection<Integer> sectionIds) {
        Map<Integer, Collection<ClusterRupture>> result = new HashMap<>();
        for (int sectionId : sectionIds) {
            result.put(sectionId, new ArrayList<>(findNearbyRuptures(sectionId)));
        }
        return result;
    }
}
