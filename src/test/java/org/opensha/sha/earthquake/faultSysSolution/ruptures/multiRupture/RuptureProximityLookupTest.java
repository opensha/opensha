package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

public class RuptureProximityLookupTest {

    // Layout:
    //   Sections 0,1: listA nucleation sections near Wellington (-41.0, 175.0)
    //   Sections 2,3: listB rupture "close" (~1.4 km from sect 0)
    //   Sections 4,5: listB rupture "far" (~130 km from sect 0)
    //   Section 6:    listB rupture sharing sect 3 (for dedup test)

    private List<FaultSection> allSections;
    private SectionDistanceAzimuthCalculator distCalc;

    private ClusterRupture closeRupture; // sections 2,3
    private ClusterRupture farRupture; // sections 4,5
    private ClusterRupture sharedRupture; // sections 3,6 (shares sect 3 with closeRupture)

    static FaultSection makeSection(int id, int parentId, double lat, double lon) {
        FaultSectionPrefData s = new FaultSectionPrefData();
        s.setSectionId(id);
        s.setSectionName("sect-" + id);
        s.setParentSectionId(parentId);
        s.setParentSectionName("parent-" + parentId);
        s.setAveDip(90.0);
        s.setAveLowerDepth(15.0);
        s.setAveUpperDepth(0.0);
        FaultTrace trace = new FaultTrace("sect-" + id);
        trace.add(new Location(lat, lon - 0.005));
        trace.add(new Location(lat, lon + 0.005));
        s.setFaultTrace(trace);
        return s;
    }

    private static ClusterRupture buildRupture(
            List<FaultSection> sects, SectionDistanceAzimuthCalculator distCalc) {
        return ClusterRupture.forOrderedSingleStrandRupture(sects, distCalc);
    }

    @Before
    public void setUp() {
        allSections = new ArrayList<>();
        // listA sections
        allSections.add(makeSection(0, 0, -41.0, 175.0));
        allSections.add(makeSection(1, 0, -41.005, 175.005));
        // listB "close" rupture sections (~1.4 km from sect 0)
        allSections.add(makeSection(2, 1, -41.01, 175.01));
        allSections.add(makeSection(3, 1, -41.015, 175.015));
        // listB "far" rupture sections (~130 km from sect 0)
        allSections.add(makeSection(4, 2, -42.0, 176.0));
        allSections.add(makeSection(5, 2, -42.005, 176.005));
        // listB rupture sharing section 3
        allSections.add(makeSection(6, 3, -41.02, 175.02));

        distCalc = new SectionDistanceAzimuthCalculator(allSections);

        closeRupture = buildRupture(Arrays.asList(allSections.get(2), allSections.get(3)), distCalc);
        farRupture = buildRupture(Arrays.asList(allSections.get(4), allSections.get(5)), distCalc);
        sharedRupture =
                buildRupture(Arrays.asList(allSections.get(3), allSections.get(6)), distCalc);
    }

    @Test
    public void nearbyRuptureFound() {
        List<ClusterRupture> listB = Arrays.asList(closeRupture, farRupture);
        RuptureProximityLookup lookup = new RuptureProximityLookup(distCalc, listB, 5.0);
        Collection<ClusterRupture> result = lookup.findNearbyRuptures(0);
        assertTrue("close rupture should be found within 5 km", result.contains(closeRupture));
    }

    @Test
    public void distantRuptureExcluded() {
        List<ClusterRupture> listB = Arrays.asList(closeRupture, farRupture);
        RuptureProximityLookup lookup = new RuptureProximityLookup(distCalc, listB, 5.0);
        Collection<ClusterRupture> result = lookup.findNearbyRuptures(0);
        assertFalse("far rupture should not be found within 5 km", result.contains(farRupture));
    }

    @Test
    public void deduplication() {
        // Both closeRupture and sharedRupture contain section 3.
        // sharedRupture should appear only once even though it's reachable via sect 3 and sect 6.
        List<ClusterRupture> listB = Arrays.asList(closeRupture, sharedRupture);
        RuptureProximityLookup lookup = new RuptureProximityLookup(distCalc, listB, 5.0);
        Collection<ClusterRupture> result = lookup.findNearbyRuptures(0);

        int count = 0;
        for (ClusterRupture r : result) {
            if (r == sharedRupture) count++;
        }
        assertEquals("sharedRupture should appear exactly once", 1, count);
    }

    @Test
    public void emptyListB() {
        RuptureProximityLookup lookup =
                new RuptureProximityLookup(distCalc, Collections.emptyList(), 5.0);
        Collection<ClusterRupture> result = lookup.findNearbyRuptures(0);
        assertTrue("empty listB should return empty result", result.isEmpty());
    }

    @Test
    public void queryFromSectionNotInListB() {
        // Section 0 is not in any listB rupture; query from it should still work
        List<ClusterRupture> listB = Arrays.asList(closeRupture);
        RuptureProximityLookup lookup = new RuptureProximityLookup(distCalc, listB, 5.0);
        Collection<ClusterRupture> result = lookup.findNearbyRuptures(0);
        assertFalse("should find close rupture from non-indexed query section", result.isEmpty());
    }

    @Test
    public void consistentWithBruteForce() {
        List<ClusterRupture> listB = Arrays.asList(closeRupture, farRupture, sharedRupture);
        double maxDist = 15.0;
        RuptureProximityLookup lookup = new RuptureProximityLookup(distCalc, listB, maxDist);

        // For each section in allSections, verify lookup matches brute force
        for (FaultSection querySect : allSections) {
            int qId = querySect.getSectionId();
            Collection<ClusterRupture> indexed = lookup.findNearbyRuptures(qId);

            // Brute force: check each listB rupture
            Set<ClusterRupture> expected = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ClusterRupture rupture : listB) {
                for (FaultSection sect : rupture.buildOrderedSectionList()) {
                    if (sect.getSectionId() != qId
                            && distCalc.getDistance(qId, sect.getSectionId()) <= maxDist) {
                        expected.add(rupture);
                        break;
                    }
                }
            }

            assertEquals(
                    "section " + qId + " result count mismatch",
                    expected.size(),
                    indexed.size());
            for (ClusterRupture r : expected) {
                assertTrue(
                        "section " + qId + " missing expected rupture", indexed.contains(r));
            }
        }
    }
}
