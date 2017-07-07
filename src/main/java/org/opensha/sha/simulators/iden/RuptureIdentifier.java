package org.opensha.sha.simulators.iden;

import java.util.List;

import org.opensha.commons.data.Named;
import org.opensha.sha.simulators.SimulatorEvent;

public interface RuptureIdentifier extends Named {
	
	/**
	 * Returns true if the given event is a match for this scenario.
	 * 
	 * @param event
	 * @return
	 */
	public boolean isMatch(SimulatorEvent event);
	
	/**
	 * Returns a list of all events that are a match for this scenario, as defined by
	 * the <code>isMatch(event)</code> method.
	 * @param events
	 * @return
	 */
	public <E extends SimulatorEvent> List<E> getMatches(List<E> events);

}
