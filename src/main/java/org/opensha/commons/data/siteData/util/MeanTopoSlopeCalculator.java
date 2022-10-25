package org.opensha.commons.data.siteData.util;

import java.io.IOException;
import java.util.ArrayList;

import org.opensha.commons.data.siteData.SiteData;
import org.opensha.commons.data.siteData.impl.SRTM30PlusTopoSlope;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;

public class MeanTopoSlopeCalculator {
	
	SiteData<Double> topoSlopeProvider;
	
	public MeanTopoSlopeCalculator(SiteData<Double> topoSlopeProvider) {
		if (!topoSlopeProvider.getDataType().equals(SiteData.TYPE_TOPOGRAPHIC_SLOPE)) {
			throw new IllegalArgumentException("The given Site Data provider must be of type 'Topographic Slope'");
		}
		
		this.topoSlopeProvider = topoSlopeProvider;
	}
	
	private GriddedRegion createRegionAroundSite(Location loc, double radius, double gridSpacing) {
		return new GriddedRegion(loc, radius, gridSpacing, new Location(0,0));
	}
	
	/**
	 * Get mean topographic slope for a circular region around the given location
	 * 
	 * @param loc - location for center of circle
	 * @param radius - radius in KM
	 * @param gridSpacing - grid spacing in degrees
	 * @return
	 * @throws IOException
	 */
	public double getMeanSlope(Location loc, double radius, double gridSpacing) throws IOException {
		GriddedRegion region = createRegionAroundSite(loc, radius, gridSpacing);
		
		return getMeanSlope(region);
	}
	
	public double getMeanSlope(GriddedRegion region) throws IOException {
		return getMeanSlope(region.getNodeList());
	}
	
	public double getMeanSlope(LocationList locs) throws IOException {
		ArrayList<Double> vals = topoSlopeProvider.getValues(locs);
		
		double tot = 0;
		
		for (double val : vals) {
			tot += val;
		}
		
		double mean = tot / (double)vals.size();
		
		return mean;
	}
	
	public static void main(String args[]) throws IOException {
		SiteData<Double> topoSlopeProvider = new SRTM30PlusTopoSlope();
		MeanTopoSlopeCalculator calc = new MeanTopoSlopeCalculator(topoSlopeProvider);
		
		System.out.println("34, -118: " + calc.getMeanSlope(new Location(34, -118), 300, 0.1));
	}

}
