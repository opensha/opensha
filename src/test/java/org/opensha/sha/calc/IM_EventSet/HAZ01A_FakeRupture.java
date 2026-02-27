package org.opensha.sha.calc.IM_EventSet;

import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.faultSurface.RuptureSurface;

public class HAZ01A_FakeRupture extends ProbEqkRupture {
	
	int sourceID;
	int rupID;

    public HAZ01A_FakeRupture(ProbEqkRupture rup, int sourceID, int rupID) {
		this(rup.getMag(), rup.getAveRake(), rup.getProbability(), rup.getRuptureSurface(),
				rup.getHypocenterLocation(), sourceID, rupID);
	}
	
	public HAZ01A_FakeRupture(double mag,
            double aveRake,
            double probability,
            RuptureSurface ruptureSurface,
            Location hypocenterLocation, int sourceID, int rupID) {
		super(mag, aveRake, probability, ruptureSurface, hypocenterLocation);
		this.sourceID = sourceID;
		this.rupID = rupID;
	}

	public int getSourceID() {
		return sourceID;
	}

	public int getRupID() {
		return rupID;
	}

}
