package org.opensha.sha.magdist;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	GaussianMagFreqDistTest.class,
	SingleMagFreqDistTest.class,
	SummedMagFreqDistTest.class,
	TaperedGR_MagFreqDistTest.class,
	YC_1985_CharMagFreqDistTest.class
})

public class MFD_Suite
{

	public static void main(String args[])
	{
		org.junit.runner.JUnitCore.runClasses(MFD_Suite.class);
	}
}
