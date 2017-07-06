package org.opensha.commons.eq.cat;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.opensha.commons.eq.cat.filters.CatalogBrushTest;
import org.opensha.commons.eq.cat.io.ReaderTests;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	MagUtilsTest.class,
	CatalogBrushTest.class,
	ReaderTests.class
})

public class EqSuite
{

	public static void main(String args[])
	{
		org.junit.runner.JUnitCore.runClasses(EqSuite.class);
	}
}
