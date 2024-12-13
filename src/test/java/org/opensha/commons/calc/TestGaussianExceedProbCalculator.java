package org.opensha.commons.calc;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;

public class TestGaussianExceedProbCalculator {
	
	private static double[] referenceStdRndVars;
	private static GaussianExceedProbCalculator dynamicNoTrunc;
	private static GaussianExceedProbCalculator dynamic1Sided2Sigma;
	private static GaussianExceedProbCalculator dynamic1Sided3Sigma;
	private static GaussianExceedProbCalculator dynamic2Sided2Sigma;
	private static GaussianExceedProbCalculator dynamic2Sided3Sigma;
	private static GaussianExceedProbCalculator precomputedNoTrunc;
	private static GaussianExceedProbCalculator precomputed1Sided2Sigma;
	private static GaussianExceedProbCalculator precomputed1Sided3Sigma;
	private static GaussianExceedProbCalculator precomputed2Sided2Sigma;
	private static GaussianExceedProbCalculator precomputed2Sided3Sigma;
	private static final double TOL = 1e-8;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		EvenlyDiscretizedFunc testDiscr = new EvenlyDiscretizedFunc(
				-(GaussianExceedProbCalculator.BOUND_TO_DOUBLE_PRECISION+1),
				GaussianExceedProbCalculator.BOUND_TO_DOUBLE_PRECISION+1, 12345);
		referenceStdRndVars = new double[testDiscr.size()];
		for (int i=0; i<referenceStdRndVars.length; i++)
			referenceStdRndVars[i] = testDiscr.getX(i);

		dynamicNoTrunc = new GaussianExceedProbCalculator.Dynamic();
		dynamic1Sided2Sigma = new GaussianExceedProbCalculator.Dynamic(1, 2d);
		dynamic1Sided3Sigma = new GaussianExceedProbCalculator.Dynamic(1, 3d);
		dynamic2Sided2Sigma = new GaussianExceedProbCalculator.Dynamic(2, 2d);
		dynamic2Sided3Sigma = new GaussianExceedProbCalculator.Dynamic(2, 3d);
		precomputedNoTrunc = GaussianExceedProbCalculator.getPrecomputedExceedProbCalc(0, 0);
		precomputed1Sided2Sigma = GaussianExceedProbCalculator.getPrecomputedExceedProbCalc(1, 2d);
		precomputed1Sided3Sigma = GaussianExceedProbCalculator.getPrecomputedExceedProbCalc(1, 3d);
		precomputed2Sided2Sigma = GaussianExceedProbCalculator.getPrecomputedExceedProbCalc(2, 2d);
		precomputed2Sided3Sigma = GaussianExceedProbCalculator.getPrecomputedExceedProbCalc(2, 3d);
	}

	@Test
	public void testDynamicNoTruncation() {
		doTest(dynamicNoTrunc, 0, Double.NaN);
	}

	@Test
	public void testDynamic1Sided2Sigma() {
		doTest(dynamic1Sided2Sigma, 1, 2d);
	}

	@Test
	public void testDynamic1Sided3Sigma() {
		doTest(dynamic1Sided3Sigma, 1, 3d);
	}

	@Test
	public void testDynamic2Sided2Sigma() {
		doTest(dynamic2Sided2Sigma, 2, 2d);
	}

	@Test
	public void testDynamic2Sided3Sigma() {
		doTest(dynamic2Sided3Sigma, 2, 3d);
	}

	@Test
	public void testPrecomputedNoTruncation() {
		doTest(precomputedNoTrunc, 0, Double.NaN);
	}

	@Test
	public void testPrecomputed1Sided2Sigma() {
		doTest(precomputed1Sided2Sigma, 1, 2d);
	}

	@Test
	public void testPrecomputed1Sided3Sigma() {
		doTest(precomputed1Sided3Sigma, 1, 3d);
	}

	@Test
	public void testPrecomputed2Sided2Sigma() {
		doTest(precomputed2Sided2Sigma, 2, 2d);
	}

	@Test
	public void testPrecomputed2Sided3Sigma() {
		doTest(precomputed2Sided3Sigma, 2, 3d);
	}
	
	private void doTest(GaussianExceedProbCalculator calc, int truncType, double truncLevel) {
		for (int i=0; i<referenceStdRndVars.length; i++) {
			double stdRndVar = referenceStdRndVars[i];
			double refVal;
			if (i % 2 == 1 && truncType == 0)
				refVal = GaussianDistCalc.getExceedProb(stdRndVar);
			else
				refVal = GaussianDistCalc.getExceedProb(stdRndVar, truncType, truncLevel);
			double testVal = calc.getExceedProb(stdRndVar);
			assertEquals("Failed for truncType="+truncType+", truncLevel="+(float)truncLevel
					+", stdRndVar="+(float)stdRndVar, refVal, testVal, TOL);
		}
	}

}
