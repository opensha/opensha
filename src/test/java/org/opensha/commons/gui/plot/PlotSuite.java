package org.opensha.commons.gui.plot;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.commons.data.xyz.XYZSuite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	PlotRendererCreationTest.class
})
public class PlotSuite {
	public static void main(String args[]) {
		org.junit.runner.JUnitCore.runClasses(PlotSuite.class);
	}
}
