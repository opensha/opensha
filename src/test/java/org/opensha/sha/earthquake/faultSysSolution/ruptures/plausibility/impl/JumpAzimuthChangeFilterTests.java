package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import static org.junit.Assert.*;

import org.junit.Test;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;


public class JumpAzimuthChangeFilterTests {

    public PlausibilityResult jump(double fromAzimuth, double toAzimuth, float threshold) {
        JumpDataMock data = new JumpDataMock(new double[]{fromAzimuth}, Double.MAX_VALUE, new double[]{toAzimuth});

        JumpAzimuthChangeFilter jumpFilter = new JumpAzimuthChangeFilter(data.calc, threshold);
        return jumpFilter.apply(data.rupture, false);
    }

    @Test
    public void testJump() {
        assertEquals(PlausibilityResult.PASS, jump(0, 0, 60));
        assertEquals(PlausibilityResult.PASS, jump(50, 50, 60));
        assertEquals(PlausibilityResult.PASS, jump(0, 60, 60));
        assertEquals(PlausibilityResult.PASS, jump(-45, 270, 60));
        assertEquals(PlausibilityResult.FAIL_FUTURE_POSSIBLE, jump(180, 60, 60));
    }

    @Test
    public void testGetAzimuthDifference() {
        assertEquals(0, JumpAzimuthChangeFilter.getAzimuthDifference(0, 0), 0.0000001);
        assertEquals(0, JumpAzimuthChangeFilter.getAzimuthDifference(180, -180), 0.0000001);
        assertEquals(0, JumpAzimuthChangeFilter.getAzimuthDifference(360, 0), 0.0000001);
        assertEquals(-60, JumpAzimuthChangeFilter.getAzimuthDifference(60, 0), 0.0000001);
        assertEquals(60, JumpAzimuthChangeFilter.getAzimuthDifference(0, 60), 0.0000001);
        assertEquals("result is adjusted to [-180, 180]", 160, JumpAzimuthChangeFilter.getAzimuthDifference(200, 0), 0.0000001);
        assertEquals(-180, JumpAzimuthChangeFilter.getAzimuthDifference(180, 0), 0.0000001);
        assertEquals(180, JumpAzimuthChangeFilter.getAzimuthDifference(0, 180), 0.0000001);
    }
}
