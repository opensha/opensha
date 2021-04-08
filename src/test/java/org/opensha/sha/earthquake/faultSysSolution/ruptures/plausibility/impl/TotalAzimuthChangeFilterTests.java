package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import static org.junit.Assert.*;

import org.junit.Test;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class TotalAzimuthChangeFilterTests {

    public PlausibilityResult jump(double fromAzimuth, double[] toAzimuths, float threshold, boolean testFullEnd) {
        JumpDataMock data = new JumpDataMock(new double[]{fromAzimuth}, Double.MAX_VALUE, toAzimuths);
        
        TotalAzimuthChangeFilter jumpFilter = new TotalAzimuthChangeFilter(data.calc, threshold, true, testFullEnd);
        return jumpFilter.apply(data.rupture, false);
    }

    @Test
    public void testJumpTestFullEnd() {

        assertEquals("difference between fromAzimuth and other azimuths never exceeds threshold",
                PlausibilityResult.PASS, jump(0, new double[]{10, 20, 30, 60}, 60, true));
        assertEquals("difference between fromAzimuth and at least one other azimuth exceeds threshold.",
                PlausibilityResult.FAIL_HARD_STOP, jump(0, new double[]{10, 20, 30, 61}, 60,true));
        assertEquals("triggering azimuth does not have to be at the end.",
                PlausibilityResult.FAIL_HARD_STOP, jump(0, new double[]{4, 61, 20, 30}, 60,true));

        // azimuth difference works correctly between negative and positive angles
        assertEquals(PlausibilityResult.PASS, jump(-10, new double[]{10, 20, 30, 50}, 60,true ));
        assertEquals(PlausibilityResult.FAIL_HARD_STOP, jump(-10, new double[]{10, 20, 30, 51}, 60,true ));
    }

    @Test
    public void testJumpDontTestFullEnd() {

        assertEquals("difference between fromAzimuth and other azimuths never exceeds threshold",
                PlausibilityResult.PASS, jump(0, new double[]{10, 20, 30, 60}, 60, false));
        assertEquals("difference between fromAzimuth and last azimuth exceeds threshold.",
                PlausibilityResult.FAIL_HARD_STOP, jump(0, new double[]{10, 20, 30, 61}, 60,false));
        assertEquals("triggering azimuth has to be at the end.",
                PlausibilityResult.PASS, jump(0, new double[]{4, 61, 20, 30}, 60,false));
    }
}
