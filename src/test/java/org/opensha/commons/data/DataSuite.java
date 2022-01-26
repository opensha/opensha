package org.opensha.commons.data;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.commons.data.siteData.SiteDataProvidersTest;
import org.opensha.commons.data.siteData.TestSiteDataProviders_Operational;
import org.opensha.commons.geo.GriddedRegionTest;
import org.opensha.commons.geo.RegionTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	Container2DTest.class,
	DataPoint2DTests.class,
	Point2DToleranceSortedArrayListTest.class,
	SiteTests.class,
	TimeSpanTests.class,
	XY_DataSetTests.class,
	WeightedListTest.class,
	
	// siteData
	SiteDataProvidersTest.class,
	TestSiteDataProviders_Operational.class
})

public class DataSuite
{

	public static void main(String args[])
	{
		org.junit.runner.JUnitCore.runClasses(DataSuite.class);
	}
}
