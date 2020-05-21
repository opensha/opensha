package org.opensha.sha.simulators.iden;

import org.opensha.sha.simulators.SimulatorEvent;

import com.google.common.collect.Range;

public class EventIDsRangeIden extends AbstractRuptureIdentifier {
	
	private Range<Integer> range;
	private boolean beyond = true;
	
	public EventIDsRangeIden(Range<Integer> range) {
		this.range = range;
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		beyond = event.getID() > range.upperEndpoint();
		return range.contains(event.getID());
	}

	@Override
	public String getName() {
		return "Specific Event ID Identifier";
	}

	@Override
	public boolean furtherMatchesPossible() {
		return !beyond;
	}

}
