package org.opensha.commons.data.xyz;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	TestArbDiscrGeographicDataSet.class,
	TestArbDiscrXYZ_DataSet.class,
	TestEvenlyDiscretizedXYZ_DataSet.class,
	TestEvenlyGriddedDataSetMath.class,
	TestGeographicDataSetMath.class,
	TestGriddedGeographicDataSetMath.class,
	TestGriddedRegionDataSet.class,
	TestXYZ_DataSetMath.class
})

public class XYZSuite
{

	public static void main(String args[])
	{
		org.junit.runner.JUnitCore.runClasses(XYZSuite.class);
	}
}
