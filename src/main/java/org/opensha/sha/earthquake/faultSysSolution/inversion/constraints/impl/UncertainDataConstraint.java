package org.opensha.sha.earthquake.faultSysSolution.inversion.constraints.impl;

import org.opensha.commons.data.Named;
import org.opensha.commons.data.uncertainty.BoundedUncertainty;
import org.opensha.commons.data.uncertainty.Uncertainty;
import org.opensha.commons.data.uncertainty.UncertaintyBoundType;
import org.opensha.commons.geo.Location;

import com.google.common.base.Preconditions;

public class UncertainDataConstraint implements Named {
	
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
	
	public boolean hasUncertaintyBounds(UncertaintyBoundType type) {
		return getUncertaintyBounds(type) != null;
	}
	
	/**
	 * Retrieves an uncertainty of the given type, or returns null if not available
	 * 
	 * @param type
	 * @return {@link BoundedUncertainty} of the given {@link UncertaintyBoundType}, if available, else null
	 */
	public BoundedUncertainty getUncertaintyBounds(UncertaintyBoundType type) {
		for (Uncertainty u : uncertainties)
			if (u instanceof BoundedUncertainty)
				if (((BoundedUncertainty)u).type == type)
					return (BoundedUncertainty)u;
		return null;
	}
	
	/**
	 * Retrieves an uncertainty of the given type, or estimates it from the best estimate and standard deviation if
	 * not available
	 * 
	 * @param type
	 * @return {@link BoundedUncertainty} of the given {@link UncertaintyBoundType}, if available, else null
	 */
	public BoundedUncertainty estimateUncertaintyBounds(UncertaintyBoundType type) {
		BoundedUncertainty uncertainty = getUncertaintyBounds(type);
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
	
	@Override
	public String toString() {
		return "["+name+", μ="+(float)bestEstimate+", σ="+getPreferredStdDev()+"]";
	}

}
