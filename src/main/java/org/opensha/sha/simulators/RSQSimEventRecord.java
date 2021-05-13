package org.opensha.sha.simulators;

import java.util.List;

import com.google.common.base.Preconditions;

public class RSQSimEventRecord extends EventRecord {
	
	// values for the entire event
	private double magnitude;
	private double duration;
	
	// values for this record
	private double moment;
	private double length;
	private double area;
	
	private int firstPatchID = -1;
	
//	// keeps track of the next time this element slipped (in another event).
//	// used to associate transitions with specific events when reading the transitions file, so that all transitions
//	// for this element that are >= the event time and <nextSlipTime are associated with this event
//	private double[] nextSlipTimes;

	public RSQSimEventRecord(List<SimulatorElement> rectElementsList) {
		super(rectElementsList);
	}
	
	public void setFirstPatchToSlip(int firstPatchID) {
		this.firstPatchID = firstPatchID;
	}
	
	public int getFirstPatchToSlip() {
		return firstPatchID;
	}

	public void setMagnitude(double magnitude) {
		this.magnitude = magnitude;
	}

	public void setDuration(double duration) {
		this.duration = duration;
	}

	public void setMoment(double moment) {
		this.moment = moment;
	}

	public void setLength(double length) {
		this.length = length;
	}

	public void setArea(double area) {
		this.area = area;
	}

	@Override
	public double getMagnitude() {
		return magnitude;
	}

	@Override
	public double getDuration() {
		return duration;
	}

	@Override
	public double getLength() {
		return length;
	}

	@Override
	public double getArea() {
		return area;
	}

	@Override
	public double getMoment() {
		return moment;
	}
	
//	public void setNextSlipTime(int patchID, double time) {
//		int[] elemIDs = getElementIDs();
//		checkInitNextSlipTimes();
//		for (int i=0; i<elemIDs.length; i++) {
//			int elemID = elemIDs[i];
//			if (elemID == patchID) {
//				nextSlipTimes[i] = time;
//				return;
//			}
//		}
//		throw new IllegalStateException("Patch not found in event record: "+patchID);
//	}
//	
//	private synchronized void checkInitNextSlipTimes() {
//		if (nextSlipTimes == null) {
//			nextSlipTimes = new double[getElementIDs().length];
//			for (int i=0; i<nextSlipTimes.length; i++)
//				nextSlipTimes[i] = Double.POSITIVE_INFINITY;
//		}
//	}
//	
//	public void setNextSlipTimes(double[] nextSlipTimes) {
//		getElementIDs(); // initialize a trim if needed
//		Preconditions.checkState(nextSlipTimes.length == elementIDs.length,
//				"Bad next slip time length. nextSlipTimes.length=%s != elementIDs.length=%s", nextSlipTimes.length, elementIDs.length);
//		this.nextSlipTimes = nextSlipTimes;
//	}
//	
//	public double[] getNextSlipTimes() {
//		checkInitNextSlipTimes();
//		return nextSlipTimes;
//	}

}
