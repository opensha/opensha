package org.opensha.nshmp2.calc;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.nshmp2.util.Period;

import scratch.peter.nshmp.DeterministicResult;

/**
 * Container for hzaard calculation results.
 *
 * @author Peter Powers
 * @version $Id:$
 */
public class HazardResult {

	private Period period;
	private Location loc;
	private DiscretizedFunc curve;
	private DeterministicResult detData;
	
	/**
	 * Creates a new hazard result container.
	 * @param curve
	 * @param loc
	 * @detDat may be null
	 */
	public HazardResult(Period period, Location loc, DiscretizedFunc curve, DeterministicResult detData) {
		this.period = period;
		this.loc = loc;
		this.curve = curve;
		this.detData = detData;
	}
	
	public DeterministicResult detData() {
		return detData;
	}
	
	/**
	 * Returns the hazard curve for this result.
	 * @return the hazard curve
	 */
	public DiscretizedFunc curve() {
		return curve;
	}
	
	/**
	 * Returns the location of this result.
	 * @return the result location
	 */
	public Location location() {
		return loc;
	}
	
	/**
	 * Returns the period of this result.
	 * @return the result period
	 */
	public Period period() {
		return period;
	}
	
	
}
