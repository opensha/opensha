package org.opensha.sha.simulators.iden;

import java.util.ArrayList;
import java.util.List;

import org.opensha.sha.simulators.SimulatorEvent;

import com.google.common.collect.Lists;

public abstract class AbstractRuptureIdentifier implements RuptureIdentifier {
	
	public static <E extends SimulatorEvent> List<E> getMatches(List<E> events, RuptureIdentifier id) {
		ArrayList<E> matches = Lists.newArrayList();
		for (E event : events)
			if (id.isMatch(event))
				matches.add(event);
		return matches;
	}

	@Override
	public <E extends SimulatorEvent> List<E> getMatches(List<E> events) {
		return getMatches(events, this);
	}

}
