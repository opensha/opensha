package org.opensha.commons.data.function;

import org.apache.commons.math3.util.Precision;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.statistics.distribution.ContinuousDistribution;

import com.google.common.base.Preconditions;

public class EvenlyDiscrFuncEmpiricalDistribution implements ContinuousDistribution {
	
	private EvenlyDiscretizedFunc func;
	private IntegerPDF_FunctionSampler sampler;

	public EvenlyDiscrFuncEmpiricalDistribution(EvenlyDiscretizedFunc func) {
		Preconditions.checkNotNull(func);
		// don't allow it to be changed externally
		func = func.deepClone();
		double sumY = func.calcSumOfY_Vals();
		
		Preconditions.checkState(Double.isFinite(sumY) && sumY > 0d, "Sum of Y values must be finite and >0: %s", sumY);
		if (!Precision.equals(sumY, 1d))
			func.scale(1d/sumY);
		this.func = func;
	}

	@Override
	public double density(double x) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double cumulativeProbability(double x) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double inverseCumulativeProbability(double p) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getMean() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getVariance() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public double getSupportLowerBound() {
		return func.getMinX();
	}

	@Override
	public double getSupportUpperBound() {
		return func.getMaxX();
	}

	@Override
	public Sampler createSampler(UniformRandomProvider rng) {
		if (sampler == null) {
			double[] yVals = new double[func.size()];
			for (int i=0; i<func.size(); i++)
				yVals[i] = func.getY(i);
			sampler = new IntegerPDF_FunctionSampler(yVals);
		}
		return new Sampler() {
			
			@Override
			public double sample() {
				int index = sampler.getRandomInt(rng.nextDouble());
				return func.getY(index);
			}
		};
	}

}
