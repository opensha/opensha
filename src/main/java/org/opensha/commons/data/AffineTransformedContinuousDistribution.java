package org.opensha.commons.data;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.statistics.distribution.ContinuousDistribution;

import com.google.common.base.Preconditions;

/**
 * Affine transform of a continuous distribution: {@code Y = scale*X + offset}.
 */
public class AffineTransformedContinuousDistribution implements ContinuousDistribution {
	
	private final ContinuousDistribution source;
	private final double scale;
	private final double offset;
	private final double absScale;
	private final boolean positiveScale;
	private final double lowerBound;
	private final double upperBound;
	
	public static AffineTransformedContinuousDistribution scaled(ContinuousDistribution source, double scale) {
		return new AffineTransformedContinuousDistribution(source, scale, 0d);
	}
	
	public static AffineTransformedContinuousDistribution shifted(ContinuousDistribution source, double offset) {
		return new AffineTransformedContinuousDistribution(source, 1d, offset);
	}
	
	public static AffineTransformedContinuousDistribution of(ContinuousDistribution source, double scale, double offset) {
		return new AffineTransformedContinuousDistribution(source, scale, offset);
	}
	
	public AffineTransformedContinuousDistribution(ContinuousDistribution source, double scale, double offset) {
		Preconditions.checkNotNull(source, "Source distribution cannot be null");
		Preconditions.checkArgument(Double.isFinite(scale) && scale != 0d, "Scale must be finite and non-zero: %s", scale);
		Preconditions.checkArgument(Double.isFinite(offset), "Offset must be finite: %s", offset);
		
		this.source = source;
		this.scale = scale;
		this.offset = offset;
		this.absScale = Math.abs(scale);
		this.positiveScale = scale > 0d;
		
		double sourceLower = source.getSupportLowerBound();
		double sourceUpper = source.getSupportUpperBound();
		if (positiveScale) {
			lowerBound = transform(sourceLower);
			upperBound = transform(sourceUpper);
		} else {
			lowerBound = transform(sourceUpper);
			upperBound = transform(sourceLower);
		}
	}
	
	public ContinuousDistribution getSource() {
		return source;
	}
	
	public double getScale() {
		return scale;
	}
	
	public double getOffset() {
		return offset;
	}
	
	private double transform(double x) {
//		return scale*x + offset;
		return Math.fma(scale, x, offset);
	}
	
	private double inverseTransform(double y) {
		return (y - offset)/scale;
	}

	@Override
	public double density(double x) {
		return source.density(inverseTransform(x))/absScale;
	}

	@Override
	public double cumulativeProbability(double x) {
		double sourceX = inverseTransform(x);
		if (positiveScale)
			return source.cumulativeProbability(sourceX);
		return 1d - source.cumulativeProbability(sourceX);
	}

	@Override
	public double inverseCumulativeProbability(double p) {
		Preconditions.checkArgument(p >= 0d && p <= 1d, "p must be in the range [0,1]: %s", p);
		if (positiveScale)
			return transform(source.inverseCumulativeProbability(p));
		return transform(source.inverseCumulativeProbability(1d - p));
	}

	@Override
	public double getMean() {
		return transform(source.getMean());
	}

	@Override
	public double getVariance() {
		return scale*scale*source.getVariance();
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
		Sampler sampler = source.createSampler(rng);
		return new Sampler() {
			
			@Override
			public double sample() {
				return transform(sampler.sample());
			}
		};
	}

}
