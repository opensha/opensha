package org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility;

import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;

import com.google.common.collect.Range;

/**
 * Interface for a plausibility filter which is produces a scalar value (e.g., integer, double, float)
 * for each rupture. This is mostly a helper interface to allow diagnostic plots when comparing rupture
 * sets.
 * 
 * @author kevin
 *
 * @param <E>
 */
public interface ScalarValuePlausibiltyFilter<E extends Number & Comparable<E>> extends PlausibilityFilter {
	
	/**
	 * @param rupture
	 * @return scalar value for the given rupture
	 */
	public E getValue(ClusterRupture rupture);
	
	/**
	 * @return acceptable range of values, or null if rules are more complex
	 */
	public Range<E> getAcceptableRange();
	
	/**
	 * @return name of this scalar value
	 */
	public String getScalarName();
	
	/**
	 * @return units of this scalar value
	 */
	public String getScalarUnits();

}
