package org.opensha.sha.simulators.iden;

import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.utils.SimulatorUtils;

public class EventTimeIdentifier extends AbstractRuptureIdentifier {
	
	private final double minTime, maxTime;
	private boolean furtherPossible = true;

	/**
	 * 
	 * @param minTime in seconds unless years=true
	 * @param maxTime in seconds unless years=true
	 */
	public EventTimeIdentifier(double minTime, double maxTime, boolean years) {
		if (years) {
			this.minTime = minTime*SimulatorUtils.SECONDS_PER_YEAR;
			this.maxTime = maxTime*SimulatorUtils.SECONDS_PER_YEAR;
		} else {
			this.minTime = minTime;
			this.maxTime = maxTime;
		}
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		double time = event.getTime();
		furtherPossible = furtherPossible && time <= maxTime;
		return time >= minTime && time <= maxTime;
	}

	@Override
	public String getName() {
		double startYears = minTime/SimulatorUtils.SECONDS_PER_YEAR;
		double endYears = minTime/SimulatorUtils.SECONDS_PER_YEAR;
		return "Time Window("+startYears+" yr to "+endYears+")";
	}

	@Override
	public boolean furtherMatchesPossible() {
		return furtherPossible;
	}

}
