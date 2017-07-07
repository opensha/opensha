package org.opensha.sha.simulators;

import java.util.List;

public class RSQSimEventRecord extends EventRecord {
	
	// values for the entire event
	private double magnitude;
	private double duration;
	
	// values for this record
	private double moment;
	private double length;
	private double area;
	
	private int firstPatchID = -1;

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

}
