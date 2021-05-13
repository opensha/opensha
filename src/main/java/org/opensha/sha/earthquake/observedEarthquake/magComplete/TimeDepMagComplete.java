package org.opensha.sha.earthquake.observedEarthquake.magComplete;

import java.util.List;

import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;

public interface TimeDepMagComplete {

	/**
	 * 
	 * @param rup
	 * @return magnitude of completeness at the time of the given rupture
	 */
	public double calcTimeDepMc(ObsEqkRupture rup);

	/**
	 * 
	 * @param rup
	 * @return magnitude of completeness at the given epoch time
	 */
	public double calcTimeDepMc(long time);

	/**
	 * 
	 * @param rup
	 * @return true if the magnitude of the rupture is greater than or equal to the magnitude of completeness at
	 * it's origin time
	 */
	public boolean isAboveTimeDepMc(ObsEqkRupture rup);

	/**
	 * 
	 * @param rups
	 * @return subset of ruptres which are above the time-dependent magnitude of completeness
	 */
	public ObsEqkRupList getFiltered(List<? extends ObsEqkRupture> rups);

}