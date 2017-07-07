package org.opensha.sha.simulators.iden;

import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.utils.General_EQSIM_Tools;

public class EventTimeIdentifier extends AbstractRuptureIdentifier {
	
	private final double minTime, maxTime;

	/**
	 * 
	 * @param minTime in seconds unless years=true
	 * @param maxTime in seconds unless years=true
	 */
	public EventTimeIdentifier(double minTime, double maxTime, boolean years) {
		if (years) {
			this.minTime = minTime*General_EQSIM_Tools.SECONDS_PER_YEAR;
			this.maxTime = maxTime*General_EQSIM_Tools.SECONDS_PER_YEAR;
		} else {
			this.minTime = minTime;
			this.maxTime = maxTime;
		}
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		double time = event.getTime();
		return time >= minTime && time <= maxTime;
	}

	@Override
	public String getName() {
		double startYears = minTime/General_EQSIM_Tools.SECONDS_PER_YEAR;
		double endYears = minTime/General_EQSIM_Tools.SECONDS_PER_YEAR;
		return "Time Window("+startYears+" yr to "+endYears+")";
	}

}
