package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import org.opensha.commons.data.Named;
import org.opensha.commons.geo.Location;

import com.google.common.base.Preconditions;

public class UncertainDataConstraint implements Named {
	
	public enum UncertaintyType {
		/**
		 * plus/minus 2-sigma bounds: roughly 95% confidence interval
		 */
		TWO_SIGMA {
			@Override
			public double calcStdDev(double lowerBound, double upperBound) {
				return (upperBound - lowerBound)/4d;
			}

			@Override
			public Uncertainty estimate(double bestEstimate, double stdDev) {
				return new Uncertainty(this, bestEstimate-2*stdDev, bestEstimate+2*stdDev);
			}
		},
		/**
		 * 95% confidence bounds: roughly plus/minus 2-sigma
		 */
		CONF_95 {
			@Override
			public double calcStdDev(double lowerBound, double upperBound) {
				return (upperBound - lowerBound)/4d;
			}

			@Override
			public Uncertainty estimate(double bestEstimate, double stdDev) {
				return new Uncertainty(this, bestEstimate-2*stdDev, bestEstimate+2*stdDev);
			}
		},
		/**
		 * plus/minus 1-sigma bounds: roughly 68% confidence interval
		 */
		ONE_SIGMA {
			@Override
			public double calcStdDev(double lowerBound, double upperBound) {
				return (upperBound - lowerBound)/2d;
			}

			@Override
			public Uncertainty estimate(double bestEstimate, double stdDev) {
				return new Uncertainty(this, bestEstimate-stdDev, bestEstimate+stdDev);
			}
		},
		/**
		 * 68% confidence bounds: roughly plus/minus 1-sigma
		 */
		CONF_68 {
			@Override
			public double calcStdDev(double lowerBound, double upperBound) {
				return (upperBound - lowerBound)/2d;
			}

			@Override
			public Uncertainty estimate(double bestEstimate, double stdDev) {
				return new Uncertainty(this, bestEstimate-stdDev, bestEstimate+stdDev);
			}
		},
		/**
		 * plus/minus 0.5-sigma bounds: this is how we treated average slip uncertainties in UCERF3,
		 * but that was probably a poor assumption and will likely never be used
		 */
		HALF_SIGMA {
			@Override
			public double calcStdDev(double lowerBound, double upperBound) {
				return upperBound - lowerBound;
			}

			@Override
			public Uncertainty estimate(double bestEstimate, double stdDev) {
				return new Uncertainty(this, bestEstimate-0.5*stdDev, bestEstimate+0.5*stdDev);
			}
		};
		
		public abstract double calcStdDev(double lowerBound, double upperBound);
		
		public abstract Uncertainty estimate(double bestEstimate, double stdDev);
	}
	
	public static class Uncertainty {
		public final UncertaintyType type;
		public final double lowerBound;
		public final double upperBound;
		public final double stdDev;
		
		public Uncertainty(UncertaintyType type, double lowerBound, double upperBound) {
			this(type, lowerBound, upperBound, type.calcStdDev(lowerBound, upperBound));
		}
		
		public Uncertainty(UncertaintyType type, double lowerBound, double upperBound, double stdDev) {
			this.type = type;
			Preconditions.checkState(Double.isFinite(lowerBound),
					"Lower uncertainty bound is non-finite: %s", (float)lowerBound);
			this.lowerBound = lowerBound;
			Preconditions.checkState(lowerBound <= upperBound,
					"Upper uncertainty bound non-finite or less than lower bound (%s): %s",
					(float)lowerBound, (float)upperBound);
			this.upperBound = upperBound;
			this.stdDev = stdDev;
		}
		
		@Override
		public String toString() {
			return "type="+type.name()+"\tbounds=["+(float)lowerBound+", "+(float)upperBound+"]\tstdDev="+(float)stdDev;
		}
	}
	
	public final String name;
	public final double bestEstimate;
	public final Uncertainty[] uncertainties;
	
	/**
	 * Data value with a best estimate value and at least one measure of uncertainty
	 * 
	 * @param name
	 * @param bestEstimate
	 * @param uncertainties
	 */
	public UncertainDataConstraint(String name, double bestEstimate, Uncertainty... uncertainties) {
		super();
		this.name = name;
		this.bestEstimate = bestEstimate;
		Preconditions.checkArgument(uncertainties.length > 0, "Must supply at least 1 uncertainty");
		this.uncertainties = uncertainties;
	}
	
	public boolean hasUncertainty(UncertaintyType type) {
		return getUncertainty(type) != null;
	}
	
	/**
	 * Retrieves an uncertainty of the given type, or returns null if not available
	 * 
	 * @param type
	 * @return {@link Uncertainty} of the given {@link UncertaintyType}, if available, else null
	 */
	public Uncertainty getUncertainty(UncertaintyType type) {
		for (Uncertainty u : uncertainties)
			if (u.type == type)
				return u;
		return null;
	}
	
	/**
	 * Retrieves an uncertainty of the given type, or estimates it from the best estimate and standard deviation if
	 * not available
	 * 
	 * @param type
	 * @return {@link Uncertainty} of the given {@link UncertaintyType}, if available, else null
	 */
	public Uncertainty estimateUncertainty(UncertaintyType type) {
		Uncertainty uncertainty = getUncertainty(type);
		if (uncertainty == null)
			uncertainty = type.estimate(bestEstimate, getPreferredStdDev());
		return uncertainty;
	}
	
	/**
	 * @return the standard deviation from the preferred (first if multiple supplied) uncertainty measure
	 */
	public double getPreferredStdDev() {
		return uncertainties[0].stdDev;
	}

	@Override
	public String getName() {
		return name;
	}
	
	public static class SectMappedUncertainDataConstraint extends UncertainDataConstraint {
		public final int sectionIndex;
		public final String sectionName;
		public final Location dataLocation;
		
		/**
		 * Data value mapped to a fault section, with a best estimate value and at least one measure of uncertainty
		 * 
		 * @param name
		 * @param bestEstimate
		 * @param sectionIndex
		 * @param sectionName
		 * @param dataLocation
		 * @param uncertainties
		 */
		public SectMappedUncertainDataConstraint(String name, int sectionIndex, String sectionName, Location dataLocation,
				double bestEstimate, Uncertainty... uncertainties) {
			super(name, bestEstimate, uncertainties);
			this.sectionIndex = sectionIndex;
			this.sectionName = sectionName;
			this.dataLocation = dataLocation;
		}
	}

	public static void main(String[] args) {
		Uncertainty origUncertainty = new Uncertainty(UncertaintyType.ONE_SIGMA, 2d, 10d);
		System.out.println("Orig uncertainty: "+origUncertainty);
		double targetRate = 1d;
		
		double lowRateBound = targetRate/ origUncertainty.upperBound;
		double highRateBound = targetRate / origUncertainty.lowerBound;
		Uncertainty targetUncertainty = new Uncertainty(UncertaintyType.ONE_SIGMA, lowRateBound, highRateBound);
		System.out.println("Scaled target: "+targetUncertainty);
	}

}
