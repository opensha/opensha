package org.opensha.commons.data.function;

import java.util.Arrays;

import org.apache.commons.math3.util.Precision;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.statistics.distribution.ContinuousDistribution;

import com.google.common.base.Preconditions;

/**
 * Adaptation of an {@link EvenlyDiscretizedFunc} to match the Apache commons-statistics {@link ContinuousDistribution}
 * interface. Multiple {@link DiscretizationType}'s are supported, which define the shape of the PDF from the supplied
 * function.
 */
public class EvenlyDiscrFuncContinuousDistribution implements ContinuousDistribution {
	
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
		 * Creates a linearly interpolated PDF using the passed in function. The lower and upper support bounds will
		 * report the smallest and greatest x-values that bound the nonzero interpolated PDF, which can include
		 * zero-valued points adjacent to nonzero values.
		 */
		INTERPOLATE
	}
	
	private EvenlyDiscretizedFunc func;
	private DiscretizationType type;
	private double[] cumulative;
	private int lowerIndex;
	private int upperIndex;
	private double mean;
	private double variance;

	/**
	 * Instantiates a continuous distribution instance for the passed in function, using
	 * {@link DiscretizationType#FLAT_WITHIN_BIN} discretization.
	 * @param func
	 */
	public EvenlyDiscrFuncContinuousDistribution(EvenlyDiscretizedFunc func) {
		this(func, DiscretizationType.FLAT_WITHIN_BIN);
	}

	/**
	 * Instantiates a continuous distribution instance for the passed in function, according to the supplied
	 * discretization type
	 * @param func
	 * @param type
	 */
	public EvenlyDiscrFuncContinuousDistribution(EvenlyDiscretizedFunc func, DiscretizationType type) {
		Preconditions.checkNotNull(func);
		Preconditions.checkNotNull(type);
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
		if (type == DiscretizationType.INTERPOLATE) {
			Preconditions.checkState(func.size() > 1 && func.getDelta() > 0d,
					"INTERPOLATE requires at least 2 points with positive delta");
			if (lowerIndex > 0)
				lowerIndex--;
			if (upperIndex < func.size()-1)
				upperIndex++;
			double area = calcInterpolatedArea(func, lowerIndex, upperIndex);
			Preconditions.checkState(Double.isFinite(area) && area > 0d,
					"Interpolated PDF area must be finite and >0: %s", area);
			if (!Precision.equals(area, 1d))
				func.scale(1d/area);
		} else {
			Preconditions.checkState(type == DiscretizationType.POINT_MASS || func.getDelta() > 0d,
					"%s requires positive delta", type);
			if (!Precision.equals(sumY, 1d))
				func.scale(1d/sumY);
		}
		this.func = func;
		this.type = type;
		this.cumulative = new double[func.size()];
		if (type == DiscretizationType.INTERPOLATE)
			buildInterpolatedCumulativeAndMoments();
		else
			buildMassCumulativeAndMoments();
	}

	private static double calcInterpolatedArea(EvenlyDiscretizedFunc func, int lowerIndex, int upperIndex) {
		double area = 0d;
		for (int i=lowerIndex; i<upperIndex; i++)
			area += 0.5*(func.getY(i) + func.getY(i+1))*func.getDelta();
		return area;
	}

	private void buildMassCumulativeAndMoments() {
		double sum = 0d;
		mean = 0d;
		for (int i=0; i<func.size(); i++) {
			double mass = func.getY(i);
			sum += mass;
			cumulative[i] = sum;
			mean += func.getX(i)*mass;
		}
		for (int i=upperIndex; i<cumulative.length; i++)
			cumulative[i] = 1d;
		variance = 0d;
		for (int i=lowerIndex; i<=upperIndex; i++) {
			double diff = func.getX(i) - mean;
			variance += diff*diff*func.getY(i);
		}
		if (type == DiscretizationType.FLAT_WITHIN_BIN || type == DiscretizationType.SNAP_TO_CENTER)
			// each bin adds the conditional variance of U[-delta/2, delta/2], which is delta^2/12
			variance += func.getDelta()*func.getDelta()/12d;
	}

	private void buildInterpolatedCumulativeAndMoments() {
		double area = 0d;
		double firstMoment = 0d;
		double secondMoment = 0d;
		for (int i=0; i<=lowerIndex; i++)
			cumulative[i] = 0d;
		for (int i=lowerIndex; i<upperIndex; i++) {
			double x0 = func.getX(i);
			double y0 = func.getY(i);
			double y1 = func.getY(i+1);
			double d = func.getDelta();
			double segmentArea = 0.5*(y0 + y1)*d;
			// For local coordinate t in [0,d], y(t)=y0+(y1-y0)*t/d.
			// These terms are integrals of (x0+t)*y(t) and (x0+t)^2*y(t);
			// the 1/6 and 1/12 factors come from integrating t*y(t) and t^2*y(t).
			double segmentFirst = x0*segmentArea + d*d*(y0 + 2d * y1)/6d;
			double segmentSecond = x0*x0*segmentArea + 2d * x0*d*d*(y0 + 2d * y1)/6d
					+ d*d*d*(y0 + 3d * y1)/12d;
			area += segmentArea;
			firstMoment += segmentFirst;
			secondMoment += segmentSecond;
			cumulative[i+1] = area;
		}
		for (int i=upperIndex; i<cumulative.length; i++)
			cumulative[i] = 1d;
		mean = firstMoment;
		variance = secondMoment - mean*mean;
		if (variance < 0d && Precision.equals(variance, 0d))
			variance = 0d;
	}


	public EvenlyDiscretizedFunc getFunc() {
		return func.deepClone();
	}

	public DiscretizationType getDiscretizationType() {
		return type;
	}

	@Override
	public double density(double x) {
		switch (type) {
		case FLAT_WITHIN_BIN:
		case SNAP_TO_CENTER:
			return flatDensity(x);
		case POINT_MASS:
			int index = func.getXIndex(x);
			return index >= 0 ? func.getY(index) : 0d;
		case INTERPOLATE:
			return interpolatedDensity(x);
		default:
			throw new IllegalStateException("Unexpected discretization type: "+type);
		}
	}

	private double flatDensity(double x) {
		if (x < getSupportLowerBound() || x > getSupportUpperBound())
			return 0d;
		int index = getBinIndex(x);
		if (index < lowerIndex || index > upperIndex)
			return 0d;
		return func.getY(index)/func.getDelta();
	}

	private double interpolatedDensity(double x) {
		if (x < getSupportLowerBound() || x > getSupportUpperBound())
			return 0d;
		if (x == getSupportUpperBound())
			return func.getY(upperIndex);
		int index = func.getXIndexBefore(x);
		if (index < lowerIndex || index >= upperIndex)
			return 0d;
		double x0 = func.getX(index);
		double fract = (x - x0)/func.getDelta();
		return func.getY(index) + fract*(func.getY(index+1) - func.getY(index));
	}

	@Override
	public double cumulativeProbability(double x) {
		switch (type) {
		case FLAT_WITHIN_BIN:
		case SNAP_TO_CENTER:
			return flatCumulativeProbability(x);
		case POINT_MASS:
			return pointMassCumulativeProbability(x);
		case INTERPOLATE:
			return interpolatedCumulativeProbability(x);
		default:
			throw new IllegalStateException("Unexpected discretization type: "+type);
		}
	}

	private double flatCumulativeProbability(double x) {
		if (x < getSupportLowerBound())
			return 0d;
		if (x >= getSupportUpperBound())
			return 1d;
		int index = getBinIndex(x);
		if (index < 0)
			return 0d;
		if (index >= cumulative.length)
			return 1d;
		double before = index == 0 ? 0d : cumulative[index-1];
		double binLower = func.getX(index) - 0.5*func.getDelta();
		double fract = (x - binLower)/func.getDelta();
		return before + func.getY(index)*fract;
	}

	private double pointMassCumulativeProbability(double x) {
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

	private double interpolatedCumulativeProbability(double x) {
		if (x <= getSupportLowerBound())
			return 0d;
		if (x >= getSupportUpperBound())
			return 1d;
		int index = func.getXIndexBefore(x);
		if (index < lowerIndex)
			return 0d;
		if (index >= upperIndex)
			return 1d;
		double t = x - func.getX(index);
		double y0 = func.getY(index);
		double slope = (func.getY(index+1) - y0)/func.getDelta();
		return cumulative[index] + y0*t + 0.5*slope*t*t;
	}

	@Override
	public double inverseCumulativeProbability(double p) {
		Preconditions.checkArgument(p >= 0d && p <= 1d, "p must be in the range [0,1]: %s", p);
		if (p == 0d)
			return getSupportLowerBound();
		if (p == 1d)
			return getSupportUpperBound();
		switch (type) {
		case FLAT_WITHIN_BIN:
		case SNAP_TO_CENTER:
			return flatInverseCumulativeProbability(p);
		case POINT_MASS:
			return func.getX(indexForCumulativeProbability(p));
		case INTERPOLATE:
			return interpolatedInverseCumulativeProbability(p);
		default:
			throw new IllegalStateException("Unexpected discretization type: "+type);
		}
	}

	private double flatInverseCumulativeProbability(double p) {
		int index = indexForCumulativeProbability(p);
		double before = index == 0 ? 0d : cumulative[index-1];
		double mass = func.getY(index);
		Preconditions.checkState(mass > 0d, "No probability mass found for p=%s", p);
		return func.getX(index) - 0.5*func.getDelta() + (p - before)*func.getDelta()/mass;
	}

	private double interpolatedInverseCumulativeProbability(double p) {
		int endIndex = indexForCumulativeProbability(p);
		if (cumulative[endIndex] == p)
			return func.getX(endIndex);
		int startIndex = endIndex - 1;
		double target = p - cumulative[startIndex];
		double y0 = func.getY(startIndex);
		double slope = (func.getY(endIndex) - y0)/func.getDelta();
		double t;
		if (Precision.equals(slope, 0d))
			t = target/y0;
		else
			t = (-y0 + Math.sqrt(y0*y0 + 2d*slope*target))/slope;
		return func.getX(startIndex) + t;
	}

	private int indexForCumulativeProbability(double p) {
		int index = Arrays.binarySearch(cumulative, p);
		if (index < 0)
			index = -(index + 1);
		else
			while (index > lowerIndex && cumulative[index-1] >= p)
				index--;
		return index;
	}

	private int indexForSampleProbability(double p) {
		int index = indexForCumulativeProbability(p);
		while (index < upperIndex && func.getY(index) == 0d)
			index++;
		return index;
	}

	private int getBinIndex(double x) {
		if (x == getSupportUpperBound())
			return upperIndex;
		return (int)Math.floor((x - (func.getMinX() - 0.5*func.getDelta()))/func.getDelta());
	}

	@Override
	public double getMean() {
		return mean;
	}

	@Override
	public double getVariance() {
		return variance;
	}

	@Override
	public double getSupportLowerBound() {
		if (type == DiscretizationType.FLAT_WITHIN_BIN || type == DiscretizationType.SNAP_TO_CENTER)
			return func.getX(lowerIndex) - 0.5*func.getDelta();
		return func.getX(lowerIndex);
	}

	@Override
	public double getSupportUpperBound() {
		if (type == DiscretizationType.FLAT_WITHIN_BIN || type == DiscretizationType.SNAP_TO_CENTER)
			return func.getX(upperIndex) + 0.5*func.getDelta();
		return func.getX(upperIndex);
	}

	@Override
	public Sampler createSampler(UniformRandomProvider rng) {
		return new Sampler() {
			
			@Override
			public double sample() {
				double p = rng.nextDouble();
				if (type == DiscretizationType.SNAP_TO_CENTER)
					return func.getX(indexForSampleProbability(p));
				return inverseCumulativeProbability(p);
			}
		};
	}

}
