package org.opensha.commons.gui.plot.jfreechart;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        JFreeLogarithmicAxisTest.class
})
public class JFreeChartSuite {
    public static void main(String args[]) {
        org.junit.runner.JUnitCore.runClasses(org.opensha.commons.gui.plot.jfreechart.JFreeChartSuite.class);
    }
}
