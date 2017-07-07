package org.opensha.sha.simulators.utils;

import java.util.List;

import org.opensha.sha.simulators.SimulatorEvent;

public class SimulatorUtils {
	
	public final static double SECONDS_PER_YEAR = 365*24*60*60;
	
	public static double getSimulationDuration(List<? extends SimulatorEvent> events) {
		SimulatorEvent firstEvent = events.get(0);
		SimulatorEvent lastEvent = events.get(events.size()-1);
		double startTime = firstEvent.getTime();
		double endTime = lastEvent.getTime()+lastEvent.getDuration(); // TODO worth adjusting for duration?
		return (endTime - startTime);
	}
	
	/**
	 * 
	 * @return simulation duration in years
	 */
	public static double getSimulationDurationYears(List<? extends SimulatorEvent> events) {
		return getSimulationDuration(events)/SECONDS_PER_YEAR;
	}

}
