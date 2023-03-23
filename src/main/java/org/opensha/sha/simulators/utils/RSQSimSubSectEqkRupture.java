package org.opensha.sha.simulators.utils;

import java.util.List;

import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.simulators.RSQSimEvent;

public class RSQSimSubSectEqkRupture extends RSQSimEqkRupture {
	private List<? extends FaultSection> subSections;
	private FaultSection nuclSection;
	
	public RSQSimSubSectEqkRupture(double mag, double aveRake, RuptureSurface ruptureSurface, Location hypocenterLocation,
			int eventID, double timeSeconds, List<? extends FaultSection> subSections, FaultSection nuclSection) {
		super(mag, aveRake, ruptureSurface, hypocenterLocation, eventID, timeSeconds);
		
		this.subSections = subSections;
		this.nuclSection = nuclSection;
	}
	
	public RSQSimSubSectEqkRupture(double mag, double aveRake, RuptureSurface ruptureSurface, Location hypocenterLocation,
			RSQSimEvent event, List<? extends FaultSection> subSections, FaultSection nuclSection) {
		super(mag, aveRake, ruptureSurface, hypocenterLocation, event);
		
		this.subSections = subSections;
		this.nuclSection = nuclSection;
	}

	public List<? extends FaultSection> getSubSections() {
		return subSections;
	}
	
	public FaultSection getNucleationSection() {
		return nuclSection;
	}

}
