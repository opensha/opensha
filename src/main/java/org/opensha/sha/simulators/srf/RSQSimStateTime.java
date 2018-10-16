package org.opensha.sha.simulators.srf;

public class RSQSimStateTime {
	
	private final int patchID;
	private final double startTime;
	private final double velocity;
	private double endTime;
	private final RSQSimState state;
	
	/**
	 * Creates a state time where the end is not yet known
	 * @param startTime
	 * @param state
	 */
	RSQSimStateTime(int patchID, double startTime, RSQSimState state, double velocity) {
		this(patchID, startTime, Double.NaN, state, velocity);
	}
	
	public RSQSimStateTime(int patchID, double startTime, double endTime, RSQSimState state, double velocity) {
		this.patchID = patchID;
		this.startTime = startTime;
		this.endTime = endTime;
		this.state = state;
		this.velocity = velocity;
	}
	
	public int getPatchID() {
		return patchID;
	}

	public double getEndTime() {
		return endTime;
	}

	void setEndTime(double endTime) {
		this.endTime = endTime;
	}

	public double getStartTime() {
		return startTime;
	}

	public RSQSimState getState() {
		return state;
	}
	
	public double getDuration() {
		return endTime - startTime;
	}
	
	public boolean containsTime(double time) {
		return time >= startTime && time < endTime;
	}
	
	public double getVelocity() {
		return velocity;
	}
	
	@Override
	public String toString() {
		if (!Double.isNaN(velocity) && state == RSQSimState.EARTHQUAKE_SLIP)
			return "["+startTime+" => "+endTime+": "+state.name()+", vel="+velocity+"]";
		else
			return "["+startTime+" => "+endTime+": "+state.name()+"]";
	}

}
