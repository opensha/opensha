package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectIDRange;

import java.util.List;

public class SectBValuePlotTest {

    @Test
    public void testSectBValuesMaxMagLessThanMinMag(){

        // It can happen that for a section, the max mag is less than the min mag because min mag may be subject to a
        // minimum min mag, while max mag is not.

        int SECTION = 0;
        int RUPTURE = 0;

        // a rupset with one section and one rupture
        FaultSystemRupSet rupSet = mock(FaultSystemRupSet.class);
        when(rupSet.getNumSections()).thenReturn(1);
        when(rupSet.getNumRuptures()).thenReturn(1);

        when(rupSet.getAreaForSection(SECTION)).thenReturn(1.0);

        when(rupSet.getSectionsIndicesForRup(RUPTURE)).thenReturn(List.of(SECTION));
        when(rupSet.getMagForRup(RUPTURE)).thenReturn(6.9);
        when(rupSet.getAreaForRup(RUPTURE)).thenReturn(1.0);

        FaultSystemSolution solution = mock(FaultSystemSolution.class);
        when(solution.getRupSet()).thenReturn(rupSet);
        when(solution.getRateForRup(RUPTURE)).thenReturn(0.5);

        SectBValuePlot.BValEstimate actual = SectBValuePlot.estBValue(7.0, 6.9, solution, List.of(RUPTURE), null, SectIDRange.build(SECTION, SECTION));


    }
}
