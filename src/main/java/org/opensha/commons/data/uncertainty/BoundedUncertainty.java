package org.opensha.commons.data.uncertainty;

import com.google.common.base.Preconditions;
import com.google.gson.annotations.JsonAdapter;

/**
 * An {@link Uncertainty} that has bounds, e.g., 95% confidence bounds, along with a standard deviation.
 * 
 * @author kevin
 *
 */
@JsonAdapter(Uncertainty.UncertaintyAdapter.class)
public class BoundedUncertainty extends Uncertainty {
	public final UncertaintyBoundType type;
	public final double lowerBound;
	public final double upperBound;
	
	public static BoundedUncertainty fromMeanAndBounds(UncertaintyBoundType type, double bestEstimate,
			double lowerBound, double upperBound) {
		return new BoundedUncertainty(type, lowerBound, upperBound,
				type.estimateStdDev(bestEstimate, lowerBound, upperBound));
	}
	
	public BoundedUncertainty(UncertaintyBoundType type, double lowerBound, double upperBound, double stdDev) {
		super(stdDev);
		this.type = type;
		Preconditions.checkState(Double.isFinite(lowerBound),
				"Lower uncertainty bound is non-finite: %s", (float)lowerBound);
		this.lowerBound = lowerBound;
		Preconditions.checkState(lowerBound <= upperBound,
				"Upper uncertainty bound non-finite or less than lower bound (%s): %s",
				(float)lowerBound, (float)upperBound);
		this.upperBound = upperBound;
	}
	
	@Override
	public String toString() {
		return "type="+type.name()+"\tbounds=["+(float)lowerBound+", "+(float)upperBound+"]\tstdDev="+(float)stdDev;
	}
	
	public boolean contains(double value) {
		return value >= lowerBound && value <= upperBound;
	}
}