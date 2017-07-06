package org.opensha.commons.calc;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	TestFaultMomentCalc.class,
	TestFractileCurveCalculator.class,
	TestFunctionListCalc.class,
	TestGaussianDistCalc.class,
	TestWeightedSampler.class
})

public class CalcSuite
{

	public static void main(String args[])
	{
		org.junit.runner.JUnitCore.runClasses(CalcSuite.class);
	}
}
