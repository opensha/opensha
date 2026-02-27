package org.opensha.sha.calc.IM_EventSet;

import java.util.ArrayList;

import org.opensha.commons.data.Site;
import org.opensha.commons.geo.LocationList;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.faultSurface.RuptureSurface;

public class HAZ01A_FakeSource extends ProbEqkSource {
	
	private ArrayList<HAZ01A_FakeRupture> rups;
	private ProbEqkSource source;

    public HAZ01A_FakeSource(ProbEqkSource source, int sourceID) {
		rups = new ArrayList<HAZ01A_FakeRupture>();
		this.source = source;
		
		for (int i=0; i<source.getNumRuptures(); i++) {
			rups.add(new HAZ01A_FakeRupture(source.getRupture(i), sourceID, i));
		}
	}

	@Override
	public double getMinDistance(Site site) {
		return source.getMinDistance(site);
	}

	@Override
	public int getNumRuptures() {
		return rups.size();
	}

	@Override
	public ProbEqkRupture getRupture(int rupture) {
		return rups.get(rupture);
	}

	public LocationList getAllSourceLocs() {
		return source.getAllSourceLocs();
	}

	public RuptureSurface getSourceSurface() {
		return source.getSourceSurface();
	}

}
