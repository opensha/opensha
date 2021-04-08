package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Provides mock objects that can be used to test AzimuthChangeFilters.
 */
public class JumpDataMock {

    public final JumpAzimuthChangeFilter.AzimuthCalc calc;
    public final FaultSubsectionCluster fromCluster;
    public final FaultSubsectionCluster toCluster;
    public final FaultSection fromSection;
    public final Jump jump;
    public final ClusterRupture rupture;

    /**
     * Creates a JumpDataMock with a jump going from one cluster to another. The geometry of the mocked sections is
     * completely undefined. The field calc provides a mocked AzimuthCalc that will return the specified azimuths
     * for the sections. For example, new JumpDataMock(new double[]{10}, 20, new double[]{30}) will create two sections
     * in the fromCluster with an azimuth difference of 10, two sections in the toCluster with an azimuth difference of
     * 30, and the last section of fromCluster will have an azimuth difference of 20 with the first section of toCluster.
     *
     * @param fromAzimuths
     * @param jumpAzimuth
     * @param toAzimuths
     */
    public JumpDataMock(double[] fromAzimuths, double jumpAzimuth, double[] toAzimuths) {
        calc = mock(JumpAzimuthChangeFilter.AzimuthCalc.class);
        fromCluster = mockFaultSubsectionCluster(fromAzimuths, 0, calc);
        toCluster = mockFaultSubsectionCluster(toAzimuths, 1, calc);
        fromSection = fromCluster.subSects.get(fromCluster.subSects.size() - 1);
        when(calc.calcAzimuth(fromSection, toCluster.startSect)).thenReturn(jumpAzimuth);
        jump = new Jump(fromSection, fromCluster, toCluster.startSect, toCluster, 0.2);
        rupture = new ClusterRupture(fromCluster).take(jump);
    }

    public FaultSubsectionCluster mockFaultSubsectionCluster(double[] azimuths, int parentId, JumpAzimuthChangeFilter.AzimuthCalc calc) {
        int id = parentId * 1000;
        List<FaultSection> sections = new ArrayList<>();
        FaultSection previous = mock(FaultSection.class);
        when(previous.getParentSectionId()).thenReturn(parentId);
        when(previous.getSectionId()).thenReturn(id++);
        sections.add(previous);
        for (Double azimuth : azimuths) {
            FaultSection section = mock(FaultSection.class);
            when(section.getParentSectionId()).thenReturn(parentId);
            when(section.getSectionId()).thenReturn(id++);
            sections.add(section);
            when(calc.calcAzimuth(previous, section)).thenReturn(azimuth);
            previous = section;
        }
        return new FaultSubsectionCluster(sections);
    }
}
