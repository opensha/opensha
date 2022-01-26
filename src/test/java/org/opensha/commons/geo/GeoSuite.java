package org.opensha.commons.geo;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	GeoToolsTest.class,
	GriddedRegionTest.class,
	LocationListTest.class,
	LocationTest.class,
	LocationUtilsTest.class,
	RegionTest.class
})

public class GeoSuite
{

	public static void main(String args[])
	{
		org.junit.runner.JUnitCore.runClasses(GeoSuite.class);
	}
}
