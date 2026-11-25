package org.opensha.commons.util;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.commons.data.siteData.servlet.AbstractSiteDataServletTest;
import org.opensha.commons.util.binFile.BinaryMesh2DTest;
import org.opensha.commons.util.binFile.GeolocatedBinaryMesh2DTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	DataUtilsTest.class,
	FaultUtilsTests.class,
	RunScriptTest.class,
	ServletObjectInputTest.class,
	AbstractSiteDataServletTest.class,
	BinaryMesh2DTest.class,
	GeolocatedBinaryMesh2DTest.class,
	InterpolateTests.class
})


public class UtilSuite {
}
