package org.opensha.sha.simulators.iden;

public class TimeWindow {
	private double start;
	private double end;
	private int initiatorID;
	public TimeWindow(double start, double end, int initiatorID) {
		this.start = start;
		this.end = end;
		this.initiatorID = initiatorID;
	}
	
	public boolean isBefore(double time) {
		return time < start;
	}
	
	public boolean isAfter(double time) {
		return time > end;
	}
	
	public boolean isContained(double time) {
		return time >= start && time <= end;
	}
	
	public boolean isInitiator(int eventID) {
		return eventID == initiatorID;
	}

	protected double getStart() {
		return start;
	}

	protected double getEnd() {
		return end;
	}

	protected int getInitiatorID() {
		return initiatorID;
	}
}
