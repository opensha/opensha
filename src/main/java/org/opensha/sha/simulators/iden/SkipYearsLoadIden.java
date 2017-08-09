package org.opensha.sha.simulators.iden;

import org.opensha.sha.simulators.SimulatorEvent;

import com.google.common.base.Preconditions;

/**
 * Rupture identifier that can be used on catalog loading to skip the first N years. Events
 * must be passed in order, as the first event seen is used as the catalog start time.
 * @author kevin
 *
 */
public class SkipYearsLoadIden extends AbstractRuptureIdentifier {
	
	private double firstEventYears = Double.NaN;
	private final double skipYears;
	
	public SkipYearsLoadIden(double skipYears) {
		Preconditions.checkArgument(skipYears > 0);
		this.skipYears = skipYears;
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		double years = event.getTimeInYears();
		Preconditions.checkState(Double.isFinite(years));
		if (Double.isNaN(firstEventYears)) {
			// this is the first one
			firstEventYears = years;
		}
		Preconditions.checkState(years >= firstEventYears);
		double diff = years - firstEventYears;
		
		return diff >= skipYears;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

}
