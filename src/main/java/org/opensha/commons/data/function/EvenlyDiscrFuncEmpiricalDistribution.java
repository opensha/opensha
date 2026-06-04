package org.opensha.commons.data.function;

import java.util.Arrays;

import org.apache.commons.math3.util.Precision;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.statistics.distribution.ContinuousDistribution;

import com.google.common.base.Preconditions;

public class EvenlyDiscrFuncEmpiricalDistribution implements ContinuousDistribution {
	
	public enum DiscretizationType {
		/**
		 * Assume that the PDF is flat within each bin, defined by the x value +/- half
		 * of {@link EvenlyDiscretizedFunc#getDelta()}. The lower and upper support bound will report the bin edges
		 * of the lowest and greatest bin with a nonzero x-value, and the sampler will sample uniformly within each
		 * bin.
		 */
		FLAT_WITHIN_BIN,
		/**
		 * Same as {@link #FLAT_WITHIN_BIN}, except that the sampler only returns values discretized at the original bin
		 * centers (i.e., those at explicit x-values) and does not sample from the true distribution. The support
		 * bounds still return the bin edges.
		 */
		SNAP_TO_CENTER,
		/**
		 * Treat the passed in function as point masses, i.e., where
		 * {@link EvenlyDiscrFuncEmpiricalDistribution#density(double)} returns the probability mass for matching
		 * x values (within {@link EvenlyDiscretizedFunc#getTolerance()} and such that
		 * {@link EvenlyDiscretizedFunc#getXIndex(double)} is >=0), and zero between them. The support bounds return the
		 * smallest and largest x-values with nonzero y-values.
		 */
		POINT_MASS,
		/**
		 * Creates a linearly interpolated PDF using the passed in function. The lower and upper support bound will
		 * report the smallest and greatest nonzero x-values (not their bin edges).
		 */
		INTERPOLATE
	}
	
	private EvenlyDiscretizedFunc func;
	private double[] cumulative;
	private int lowerIndex;
	private int upperIndex;

	public EvenlyDiscrFuncEmpiricalDistribution(EvenlyDiscretizedFunc func, DiscretizationType type) {
		Preconditions.checkNotNull(func);
		// don't allow it to be changed externally
		func = func.deepClone();
		double sumY = func.calcSumOfY_Vals();
		
		Preconditions.checkState(Double.isFinite(sumY) && sumY > 0d, "Sum of Y values must be finite and >0: %s", sumY);
		lowerIndex = -1;
		upperIndex = -1;
		for (int i=0; i<func.size(); i++) {
			double y = func.getY(i);
			Preconditions.checkState(Double.isFinite(y) && y >= 0d, "Y values must be finite and >=0: y["+i+"]="+y);
			if (y > 0d) {
				if (lowerIndex < 0)
					lowerIndex = i;
				upperIndex = i;
			}
		}
		Preconditions.checkState(lowerIndex >= 0, "At least one Y value must be >0");
		if (!Precision.equals(sumY, 1d))
			func.scale(1d/sumY);
		this.func = func;
		this.cumulative = new double[func.size()];
		double sum = 0d;
		for (int i=0; i<func.size(); i++) {
			sum += func.getY(i);
			cumulative[i] = sum;
		}
		// ensure exact behavior at the upper end after any floating point roundoff
		for (int i=upperIndex; i<cumulative.length; i++)
			cumulative[i] = 1d;
	}

	@Override
	public double density(double x) {
		int index = func.getXIndex(x);
		return index >= 0 ? func.getY(index) : 0d;
	}

	@Override
	public double cumulativeProbability(double x) {
		if (x < getSupportLowerBound())
			return 0d;
		if (x >= getSupportUpperBound())
			return 1d;
		int index = func.getXIndexBefore(x);
		if (index < 0)
			return 0d;
		if (index >= cumulative.length)
			return 1d;
		return cumulative[index];
	}

	@Override
	public double inverseCumulativeProbability(double p) {
		Preconditions.checkArgument(p >= 0d && p <= 1d, "p must be in the range [0,1]: %s", p);
		if (p == 0d)
			return getSupportLowerBound();
		if (p == 1d)
			return getSupportUpperBound();
		int index = Arrays.binarySearch(cumulative, p);
		if (index < 0)
			index = -(index + 1);
		else
			while (index > lowerIndex && cumulative[index-1] >= p)
				index--;
		return func.getX(index);
	}

	@Override
	public double getMean() {
		double mean = 0d;
		for (int i=lowerIndex; i<=upperIndex; i++)
			mean += func.getX(i)*func.getY(i);
		return mean;
	}

	@Override
	public double getVariance() {
		double mean = getMean();
		double variance = 0d;
		for (int i=lowerIndex; i<=upperIndex; i++) {
			double diff = func.getX(i) - mean;
			variance += diff*diff*func.getY(i);
		}
		return variance;
	}

	@Override
	public double getSupportLowerBound() {
		return func.getX(lowerIndex);
	}

	@Override
	public double getSupportUpperBound() {
		return func.getX(upperIndex);
	}

	@Override
	public Sampler createSampler(UniformRandomProvider rng) {
		return new Sampler() {
			
			@Override
			public double sample() {
				return inverseCumulativeProbability(rng.nextDouble());
			}
		};
	}

}
