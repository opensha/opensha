package org.opensha.commons.mapping;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.commons.mapping.gmt.TestGMT_MapGenerator;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	TestGMT_MapGenerator.class
})

public class MappingSuite
{

	public static void main(String args[])
	{
		org.junit.runner.JUnitCore.runClasses(MappingSuite.class);
	}
}
