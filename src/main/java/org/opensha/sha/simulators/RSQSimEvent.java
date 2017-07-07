package org.opensha.sha.simulators;

import java.util.List;

public class RSQSimEvent extends SimulatorEvent {
	
	private List<RSQSimEventRecord> records;

	public RSQSimEvent(List<RSQSimEventRecord> records) {
		super(records);
		
		this.records = records;
	}

}
