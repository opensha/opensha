package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl;

import org.junit.runner.JUnitCore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        JumpAzimuthChangeFilterTests.class,
        TotalAzimuthChangeFilterTests.class,
        CumulativeAzimuthChangeFilterTests.class
})

public class PlausibilityTestSuite {


    public static void main(String[] args) {
        JUnitCore.runClasses(PlausibilityTestSuite.class);
    }

}
