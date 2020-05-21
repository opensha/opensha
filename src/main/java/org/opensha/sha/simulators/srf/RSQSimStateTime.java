package org.opensha.sha.simulators.srf;

import com.google.common.base.Preconditions;

public class RSQSimStateTime implements Comparable<RSQSimStateTime> {
	
	/*
	 * Inputs directly from transitions file
	 */
	
	/**
	 * Absolute time (simulation time) for this transition, in seconds
	 */
	public final double absoluteTime;
	/**
	 * Relative time for this transition to the start of the most recent event, in seconds, even if this transition
	 * occurs after that event ends (but before the next one begins)
	 */
	public final float relativeTime;
	/**
	 * Event ID (1-based) of the most recent event, in seconds, even if this transition occurs after that event
	 * ends (but before the next one begins)
	 */
	public final int eventID;
	/**
	 * ID (1-based) of the patch which transitioned
	 */
	public final int patchID;
	/**
	 * State that this patch transitioned to
	 */
	public final RSQSimState state;
	/**
	 * Patch slip velocity. Older transition files (pre-2020) will not populate this field for fixed slip speed runs.
	 */
	public final float velocity;
	
	// duration in seconds, inferred from next transition
	private double duration = Double.NaN;
	
	public RSQSimStateTime(double absoluteTime, float relativeTime, int eventID, int patchID,
			RSQSimState state, float velocity) {
		this.absoluteTime = absoluteTime;
		this.relativeTime = relativeTime;
		this.eventID = eventID;
		this.patchID = patchID;
		this.state = state;
		this.velocity = velocity;
	}
	
	/**
	 * @return true if the duration of this transition has been set (from the next trans on this patch)
	 */
	public boolean hasDuration() {
		return Double.isFinite(duration);
	}
	
	void setDuration(double duration) {
		Preconditions.checkState(duration >= 0d, "bad duration: %s", duration);
		this.duration = duration;
	}
	
	void setNextTransition(RSQSimStateTime next) {
		Preconditions.checkState(patchID == next.patchID, "patch mismatch: %s != %s", patchID, next.patchID);
		Preconditions.checkState(next.absoluteTime >= absoluteTime, "next transition is before? %s > %s",
				absoluteTime, next.absoluteTime);
		if (eventID >= 0 && next.eventID == eventID && Double.isFinite(relativeTime)
				&& Double.isFinite(next.relativeTime)) {
			// most accurate: within the same event and we have relative times
			setDuration(next.relativeTime - relativeTime);
		} else {
			// less accurate: use absolute times
			setDuration(next.absoluteTime - absoluteTime);
		}
	}
	
	public double getDuration() {
		Preconditions.checkState(Double.isFinite(duration), "duration has not been set for this transition");
		return duration;
	}
	
	@Override
	public String toString() {
		double endTime = absoluteTime + duration;
		float relEndTime = (float)(relativeTime + duration);
		String str = "["+absoluteTime+" => "+endTime+" (rel: "+relativeTime+" => "+relEndTime+");";
		str += " patch="+patchID+"; event="+eventID+"; "+state.name();
		if (state == RSQSimState.EARTHQUAKE_SLIP)
			str += "; vel="+velocity;
		return str+"]";
	}

	@Override
	public int compareTo(RSQSimStateTime o) {
		int cmp = Double.compare(absoluteTime, o.absoluteTime);
		if (cmp == 0) {
			if (o.eventID != eventID)
				return Integer.compare(eventID, o.eventID);
			return Float.compare(relativeTime, o.relativeTime);
		}
		return cmp;
	}

}
