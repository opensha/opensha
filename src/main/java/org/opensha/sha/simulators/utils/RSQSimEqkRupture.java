package org.opensha.sha.simulators.utils;

import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.simulators.RSQSimEvent;

public class RSQSimEqkRupture extends EqkRupture {
	
	private int eventID;
	private double timeSeconds;
	private RSQSimEvent event;
	
	public RSQSimEqkRupture(double mag, double aveRake, RuptureSurface ruptureSurface, Location hypocenterLocation,
			int eventID, double timeSeconds) {
		super(mag, aveRake, ruptureSurface, hypocenterLocation);
		
		this.eventID = eventID;
		this.timeSeconds = timeSeconds;
	}
	
	public RSQSimEqkRupture(double mag, double aveRake, RuptureSurface ruptureSurface, Location hypocenterLocation,
			RSQSimEvent event) {
		super(mag, aveRake, ruptureSurface, hypocenterLocation);
		
		this.event = event;
		this.eventID = event.getID();
		this.timeSeconds = event.getTime();
	}

	public RSQSimEvent getEvent() {
		return event;
	}
	
	public int getEventID() {
		return eventID;
	}
	
	public double getTime() {
		return timeSeconds;
	}

}
