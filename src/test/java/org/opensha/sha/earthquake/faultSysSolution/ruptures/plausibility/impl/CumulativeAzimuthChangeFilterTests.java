package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import static org.junit.Assert.*;

import org.junit.Test;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;

public class CumulativeAzimuthChangeFilterTests {

    public PlausibilityResult jump(double[] fromAzimuths, double jumpAzimuth, double[] toAzimuths, float threshold) {
        JumpDataMock data = new JumpDataMock(fromAzimuths, jumpAzimuth, toAzimuths);

        CumulativeAzimuthChangeFilter jumpFilter = new CumulativeAzimuthChangeFilter(data.calc, threshold);
        return jumpFilter.testJump(data.rupture, data.jump, false);
    }

    @Test
    public void testJump() {
        assertEquals(PlausibilityResult.PASS, jump(new double[]{30}, 40, new double[]{50}, 60));
        assertEquals(PlausibilityResult.PASS, jump(new double[]{20, 30}, 40, new double[]{50, 60}, 60));
        assertEquals(PlausibilityResult.PASS, jump(new double[]{10, 20, 30}, 40, new double[]{50, 60, 70}, 60));
        assertEquals("Just one degree out", PlausibilityResult.FAIL_HARD_STOP, jump(new double[]{10, 20, 30}, 40, new double[]{50, 60, 71}, 60));
        assertEquals(PlausibilityResult.PASS, jump(new double[]{10, -20, 30}, -40, new double[]{50, -60, 70}, 480));
        assertEquals("just one degree out", PlausibilityResult.FAIL_HARD_STOP, jump(new double[]{10, -20, 30}, -40, new double[]{50, -60, 71}, 480));
    }
}
