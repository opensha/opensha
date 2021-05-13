package org.opensha.sha.simulators.iden;

import java.util.HashSet;

import org.opensha.sha.simulators.SimulatorEvent;

public class EventIDsRupIden extends AbstractRuptureIdentifier {
	
	private HashSet<Integer> eventIDs;
	private HashSet<Integer> eventIDsProcessed;
	
	public EventIDsRupIden(int... eventIDs) {
		this.eventIDs = new HashSet<>();
		for (int id : eventIDs)
			this.eventIDs.add(id);
		this.eventIDsProcessed = new HashSet<>();
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		if (eventIDs.contains(event.getID())) {
			eventIDsProcessed.add(event.getID());
			return true;
		}
		return false;
	}

	@Override
	public String getName() {
		return "Specific Event ID Identifier";
	}

	@Override
	public boolean furtherMatchesPossible() {
		return eventIDsProcessed.size() < eventIDs.size();
	}

}
