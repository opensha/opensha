package org.opensha.sha.simulators.utils;

import java.util.List;

import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.simulators.RSQSimEvent;

public class RSQSimSubSectEqkRupture extends EqkRupture {
	
	private int eventID;
	private double timeSeconds;
	private RSQSimEvent event;
	private List<? extends FaultSection> subSections;
	
	public RSQSimSubSectEqkRupture(double mag, double aveRake, RuptureSurface ruptureSurface, Location hypocenterLocation,
			int eventID, double timeSeconds, List<? extends FaultSection> subSections) {
		super(mag, aveRake, ruptureSurface, hypocenterLocation);
		
		this.eventID = eventID;
		this.timeSeconds = timeSeconds;
		this.subSections = subSections;
	}
	
	public RSQSimSubSectEqkRupture(double mag, double aveRake, RuptureSurface ruptureSurface, Location hypocenterLocation,
			RSQSimEvent event, List<? extends FaultSection> subSections) {
		super(mag, aveRake, ruptureSurface, hypocenterLocation);
		
		this.event = event;
		this.eventID = event.getID();
		this.timeSeconds = event.getTime();
		this.subSections = subSections;
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

	public List<? extends FaultSection> getSubSections() {
		return subSections;
	}

}
