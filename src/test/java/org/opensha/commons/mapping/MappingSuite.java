package org.opensha.commons.mapping;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.commons.mapping.gmt.GMT_RenderArgumentsTest;
import org.opensha.commons.mapping.gmt.TestGMT_MapGenerator;
import org.opensha.commons.mapping.servlet.GMT_MapGeneratorServletTest;
import org.opensha.commons.mapping.servlet.GMT_RenderFilesTest;
import org.opensha.sha.calc.disaggregation.DisaggregationGMTScriptTest;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	TestGMT_MapGenerator.class,
	DisaggregationGMTScriptTest.class,
	GMT_RenderArgumentsTest.class,
	GMT_MapGeneratorServletTest.class,
	GMT_RenderFilesTest.class
})

public class MappingSuite
{

	public static void main(String args[])
	{
		org.junit.runner.JUnitCore.runClasses(MappingSuite.class);
	}
}
