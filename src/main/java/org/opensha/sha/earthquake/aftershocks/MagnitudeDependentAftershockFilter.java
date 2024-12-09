package org.opensha.sha.earthquake.aftershocks;

import java.util.function.DoubleBinaryOperator;

import scratch.UCERF3.utils.GardnerKnopoffAftershockFilter;

/**
 * Interface for an aftershock filter that scales event rates to remove aftershocks, such as via a
 * {@link GardnerKnopoffAftershockFilter}.
 * 
 * TODO: currently only magnitude-dependent; should we modify to support arbitrary EqkRuptures? All implementations
 * just use magnitude (and it's applied to MFDs before ruptures are build in).
 */
public interface MagnitudeDependentAftershockFilter {
	
	public double getFilteredRate(double magnitude, double originalRate);
	
	/**
	 * Creates an {@link MagnitudeDependentAftershockFilter} for a given function. You can define that function in code
	 * as: <code>forFunction(M,R -> R * scalarForMag(M))</code> where R is the original rate, M is the magnitude, and
	 * scalarForMag is some scale factor as a function of magnitude.
	 * 
	 * @param function
	 * @return
	 */
	public static MagnitudeDependentAftershockFilter forFunction(DoubleBinaryOperator function) {
		if (function == null)
			return null;
		return new MagnitudeDependentAftershockFilter() {
			
			@Override
			public double getFilteredRate(double magnitude, double originalRate) {
				return function.applyAsDouble(magnitude, originalRate);
			}
		};
	}

}
