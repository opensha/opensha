package org.opensha.commons.data;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.statistics.distribution.ContinuousDistribution;

import com.google.common.base.Preconditions;

/**
 * Mixture of continuous distributions, weighted by a {@link WeightedList}.
 */
public class WeightedContinuousDistribution implements ContinuousDistribution {
	
	private static final int INVERSE_MAX_ITERATIONS = 100;
	private static final double INVERSE_ABSOLUTE_TOLERANCE = 1e-12;
	
	private final WeightedList.Unmodifiable<ContinuousDistribution> dists;
	private final double mean;
	private final double variance;
	private final double lowerBound;
	private final double upperBound;
	
	public WeightedContinuousDistribution(WeightedList<ContinuousDistribution> dists) {
		Preconditions.checkNotNull(dists, "Distributions cannot be null");
		Preconditions.checkArgument(!dists.isEmpty(), "Must supply at least one distribution");
		
		List<WeightedValue<ContinuousDistribution>> positiveDists = new ArrayList<>();
		for (int i=0; i<dists.size(); i++) {
			ContinuousDistribution dist = dists.getValue(i);
			double weight = dists.getWeight(i);
			Preconditions.checkNotNull(dist, "Distribution at index %s is null", i);
			Preconditions.checkArgument(Double.isFinite(weight) && weight >= 0d,
					"Distribution weights must be finite and >=0: weight[%s]=%s",
					Integer.valueOf(i), Double.valueOf(weight));
			if (weight > 0d)
				positiveDists.add(new WeightedValue<>(dist, weight));
		}
		Preconditions.checkArgument(!positiveDists.isEmpty(), "Must supply at least one distribution with positive weight");
		
		WeightedList<ContinuousDistribution> normalized = new WeightedList<>(positiveDists);
		normalized.normalize();
		this.dists = new WeightedList.Unmodifiable<>(normalized);
		
		double mean = 0d;
		double secondMoment = 0d;
		double lowerBound = Double.POSITIVE_INFINITY;
		double upperBound = Double.NEGATIVE_INFINITY;
		for (int i=0; i<this.dists.size(); i++) {
			ContinuousDistribution dist = this.dists.getValue(i);
			double weight = this.dists.getWeight(i);
			double distMean = dist.getMean();
			double distVariance = dist.getVariance();
			mean += weight*distMean;
			secondMoment += weight*(distVariance + distMean*distMean);
			lowerBound = Math.min(lowerBound, dist.getSupportLowerBound());
			upperBound = Math.max(upperBound, dist.getSupportUpperBound());
		}
		this.mean = mean;
		double variance = secondMoment - mean*mean;
		this.variance = variance < 0d && Math.abs(variance) < 1e-12 ? 0d : variance;
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
	}
	
	public WeightedList.Unmodifiable<ContinuousDistribution> getDistributions() {
		return dists;
	}

	@Override
	public double density(double x) {
		double ret = 0d;
		for (int i=0; i<dists.size(); i++)
			ret += dists.getWeight(i)*dists.getValue(i).density(x);
		return ret;
	}

	@Override
	public double cumulativeProbability(double x) {
		double ret = 0d;
		for (int i=0; i<dists.size(); i++)
			ret += dists.getWeight(i)*dists.getValue(i).cumulativeProbability(x);
		return ret;
	}

	@Override
	public double inverseCumulativeProbability(double p) {
		Preconditions.checkArgument(p >= 0d && p <= 1d, "p must be in the range [0,1]: %s", p);
		if (p == 0d)
			return getSupportLowerBound();
		if (p == 1d)
			return getSupportUpperBound();
		
		double lower = Double.POSITIVE_INFINITY;
		double upper = Double.NEGATIVE_INFINITY;
		for (int i=0; i<dists.size(); i++) {
			ContinuousDistribution dist = dists.getValue(i);
			lower = Math.min(lower, dist.inverseCumulativeProbability(p));
			upper = Math.max(upper, dist.inverseCumulativeProbability(p));
		}
		if (lower == upper)
			return lower;
		
		Preconditions.checkState(Double.isFinite(lower) && Double.isFinite(upper),
				"Could not bracket inverse cumulative probability for p=%s: [%s, %s]", p, lower, upper);
		
		for (int i=0; i<INVERSE_MAX_ITERATIONS && upper - lower > INVERSE_ABSOLUTE_TOLERANCE; i++) {
			double mid = lower + 0.5*(upper - lower);
			if (cumulativeProbability(mid) >= p)
				upper = mid;
			else
				lower = mid;
		}
		return upper;
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
		return lowerBound;
	}

	@Override
	public double getSupportUpperBound() {
		return upperBound;
	}

	@Override
	public Sampler createSampler(UniformRandomProvider rng) {
		Preconditions.checkNotNull(rng, "Random generator cannot be null");
		Sampler[] samplers = new Sampler[dists.size()];
		for (int i=0; i<samplers.length; i++)
			samplers[i] = dists.getValue(i).createSampler(rng);
		return new Sampler() {
			
			@Override
			public double sample() {
				double p = rng.nextDouble();
				double cumulative = 0d;
				for (int i=0; i<dists.size(); i++) {
					cumulative += dists.getWeight(i);
					if (p <= cumulative)
						return samplers[i].sample();
				}
				return samplers[samplers.length-1].sample();
			}
		};
	}

}
