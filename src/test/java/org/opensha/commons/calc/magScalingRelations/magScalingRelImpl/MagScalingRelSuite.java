package org.opensha.commons.calc.magScalingRelations.magScalingRelImpl;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        TestTMG2017CruMagAreaRel.class,
        TestTMG2017SubMagAreaRel.class,
        TestAH2017InterfaceBilinearMagAreaRel.class,
        TestSAB2010InterfaceMagAreaRel.class,
        TestSST2016InterfaceMagAreaRel.class,
        TestMSF2013InterfaceMagAreaRel.class
})

public class MagScalingRelSuite {
    public static void main(String args[]) {
        org.junit.runner.JUnitCore.runClasses(MagScalingRelSuite.class);
    }
}

