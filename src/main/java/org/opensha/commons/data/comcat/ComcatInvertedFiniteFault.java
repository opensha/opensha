package org.opensha.commons.data.comcat;

import org.opensha.commons.geo.LocationList;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.List;

public class ComcatInvertedFiniteFault {
	
	private List<LocationList> polygons;
	private List<Double> slips; // m
	private List<Double> moments; // N-m
	
	public ComcatInvertedFiniteFault(List<LocationList> locs, List<Double> slips, List<Double> moments) {
		this.polygons = locs;
		this.slips = slips;
		this.moments = moments;
	}
	
	ComcatInvertedFiniteFault() {
		polygons = new ArrayList<>();
		slips = new ArrayList<>();
		moments = new ArrayList<>();
	}
	
	void addRecord(LocationList outline, double slip, double moment) {
		polygons.add(outline);
		slips.add(slip);
		moments.add(moment);
	}
	
	/**
	 * Build a RuptureSurface for the given event. For complex surfaces with multiple outlines,
	 * a CompoundSurface will be returned
	 * @param minSlip minimum slip for inclusion [m]
	 * @return
	 */
	public RuptureSurface buildSurface(double minSlip, double gridSpacing) {
		List<RuptureSurface> surfs = new ArrayList<>();
		for (LocationList outline : getOutlines(minSlip))
			surfs.add(EdgeRuptureSurface.build(outline, gridSpacing));
		Preconditions.checkState(!surfs.isEmpty(), "No surfaces found with slip >= %s", minSlip);
		if (surfs.size() == 1)
			return surfs.get(0);
		return new CompoundSurface(surfs);
	}
	
	public LocationList[] getOutlines(double minSlip) {
		List<LocationList> outlines = new ArrayList<>();
		for (int i=0; i<polygons.size(); i++)
			if (slips.get(i) >= minSlip)
				outlines.add(polygons.get(i));
		return outlines.toArray(new LocationList[0]);
	}

}
