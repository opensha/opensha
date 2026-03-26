package org.opensha.sha.earthquake.faultSysSolution.ruptures.multiRupture;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

public class AreaSpreadSelectorTest {

    private List<FaultSection> allSections;
    private SectionDistanceAzimuthCalculator distCalc;

    // Ruptures with increasing area (longer traces = larger area)
    private ClusterRupture tiny; // 1 section, short trace
    private ClusterRupture small; // 1 section, medium trace
    private ClusterRupture medium; // 2 sections
    private ClusterRupture large; // 3 sections
    private ClusterRupture huge; // 4 sections

    static FaultSection makeSection(int id, int parentId, double lat, double lon, double traceLenDeg) {
        FaultSectionPrefData s = new FaultSectionPrefData();
        s.setSectionId(id);
        s.setSectionName("sect-" + id);
        s.setParentSectionId(parentId);
        s.setParentSectionName("parent-" + parentId);
        s.setAveDip(90.0);
        s.setAveLowerDepth(15.0);
        s.setAveUpperDepth(0.0);
        FaultTrace trace = new FaultTrace("sect-" + id);
        trace.add(new Location(lat, lon));
        trace.add(new Location(lat, lon + traceLenDeg));
        s.setFaultTrace(trace);
        return s;
    }

    private ClusterRupture buildRupture(FaultSection... sects) {
        return ClusterRupture.forOrderedSingleStrandRupture(Arrays.asList(sects), distCalc);
    }

    @Before
    public void setUp() {
        allSections = new ArrayList<>();
        // Sections with varying trace lengths to produce different areas
        allSections.add(makeSection(0, 0, -41.0, 175.0, 0.005)); // tiny
        allSections.add(makeSection(1, 1, -41.1, 175.0, 0.01)); // small
        allSections.add(makeSection(2, 2, -41.2, 175.0, 0.02)); // medium-a
        allSections.add(makeSection(3, 2, -41.3, 175.0, 0.02)); // medium-b
        allSections.add(makeSection(4, 3, -41.4, 175.0, 0.03)); // large-a
        allSections.add(makeSection(5, 3, -41.5, 175.0, 0.03)); // large-b
        allSections.add(makeSection(6, 3, -41.6, 175.0, 0.03)); // large-c
        allSections.add(makeSection(7, 4, -41.7, 175.0, 0.04)); // huge-a
        allSections.add(makeSection(8, 4, -41.8, 175.0, 0.04)); // huge-b
        allSections.add(makeSection(9, 4, -41.9, 175.0, 0.04)); // huge-c
        allSections.add(makeSection(10, 4, -42.0, 175.0, 0.04)); // huge-d

        distCalc = new SectionDistanceAzimuthCalculator(allSections);

        tiny = buildRupture(allSections.get(0));
        small = buildRupture(allSections.get(1));
        medium = buildRupture(allSections.get(2), allSections.get(3));
        large = buildRupture(allSections.get(4), allSections.get(5), allSections.get(6));
        huge = buildRupture(allSections.get(7), allSections.get(8), allSections.get(9), allSections.get(10));
    }

    @Test
    public void selectsCorrectCount() {
        AreaSpreadSelector selector = new AreaSpreadSelector(distCalc, 3, 0.0);
        List<ClusterRupture> result =
                selector.select(Arrays.asList(tiny, small, medium, large, huge));
        assertEquals(3, result.size());
    }

    @Test
    public void fewerThanCountReturnsAll() {
        AreaSpreadSelector selector = new AreaSpreadSelector(distCalc, 5, 0.0);
        List<ClusterRupture> result = selector.select(Arrays.asList(tiny, large));
        assertEquals(2, result.size());
    }

    @Test
    public void emptyInput() {
        AreaSpreadSelector selector = new AreaSpreadSelector(distCalc, 3, 0.1);
        List<ClusterRupture> result = selector.select(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    public void singleRupture() {
        AreaSpreadSelector selector = new AreaSpreadSelector(distCalc, 3, 0.1);
        List<ClusterRupture> result = selector.select(Collections.singletonList(medium));
        assertEquals(1, result.size());
        assertSame(medium, result.get(0));
    }

    @Test
    public void duplicateAreasFiltered() {
        // Create a duplicate of tiny with nearly identical area (same section)
        ClusterRupture tinyDup = buildRupture(allSections.get(0));

        AreaSpreadSelector selector = new AreaSpreadSelector(distCalc, 3, 0.1);
        // tiny and tinyDup have identical area, so one should be filtered
        List<ClusterRupture> input = Arrays.asList(tiny, tinyDup, large, huge);
        List<ClusterRupture> result = selector.select(input);

        // After dedup: tiny, large, huge (tinyDup removed) -> pick 3
        assertEquals(3, result.size());
    }

    @Test
    public void spreadIsEvenlyDistributed() {
        AreaSpreadSelector selector = new AreaSpreadSelector(distCalc, 3, 0.0);
        List<ClusterRupture> input = Arrays.asList(huge, tiny, medium, large, small);
        List<ClusterRupture> result = selector.select(input);

        // Should pick smallest, middle, largest
        assertEquals(3, result.size());
        assertSame("first should be smallest", tiny, result.get(0));
        assertSame("last should be largest", huge, result.get(2));

        // Middle should be from the middle of the sorted range
        double middleArea = selector.computeArea(result.get(1));
        double tinyArea = selector.computeArea(tiny);
        double hugeArea = selector.computeArea(huge);
        assertTrue(
                "middle should be between smallest and largest",
                middleArea > tinyArea && middleArea < hugeArea);
    }
}
