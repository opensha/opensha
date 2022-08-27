package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.PrimaryRegions;

import com.google.common.base.Preconditions;

import scratch.UCERF3.griddedSeismicity.GridReader;

/**
 * Focal mechanism grids for NSHM23
 * 
 * TODO: get actual grids
 * 
 * These are currently hardcoded for:
 * PNW: 1/2 rev, 1/2 SS
 * IMW: 1/2 norm, 1/2 SS
 * EAST: all SS
 * U3 Relm: orig U3 values
 * 
 * @author kevin
 *
 */
public class NSHM23_GridFocalMechs {
	
	public static double[] getFractStrikeSlip(PrimaryRegions region, GriddedRegion gridRegion) {
		if (region == PrimaryRegions.CONUS_PNW || region == PrimaryRegions.CONUS_IMW)
			return constant(gridRegion.getNodeCount(), 0.5d);
		if (region == PrimaryRegions.CONUS_EAST)
			return constant(gridRegion.getNodeCount(), 1d);
		if (region == PrimaryRegions.CONUS_U3_RELM)
			return mapU3(gridRegion, new GridReader("StrikeSlipWts.txt").getValues());
		return null;
	}
	
	public static double[] getFractReverse(PrimaryRegions region, GriddedRegion gridRegion) {
		if (region == PrimaryRegions.CONUS_PNW)
			return constant(gridRegion.getNodeCount(), 0.5d);
		else if (region == PrimaryRegions.CONUS_IMW || region == PrimaryRegions.CONUS_EAST)
			return constant(gridRegion.getNodeCount(), 0d);
		if (region == PrimaryRegions.CONUS_U3_RELM)
			return mapU3(gridRegion, new GridReader("ReverseWts.txt").getValues());
		return null;
	}
	
	public static double[] getFractNormal(PrimaryRegions region, GriddedRegion gridRegion) {
		if (region == PrimaryRegions.CONUS_IMW)
			return constant(gridRegion.getNodeCount(), 0.5d);
		else if (region == PrimaryRegions.CONUS_PNW || region == PrimaryRegions.CONUS_EAST)
			return constant(gridRegion.getNodeCount(), 0d);
		if (region == PrimaryRegions.CONUS_U3_RELM)
			return mapU3(gridRegion, new GridReader("NormalWts.txt").getValues());
		return null;
	}
	
	private static double[] constant(int length, double value) {
		double[] ret = new double[length];
		for (int i=0; i<length; i++)
			ret[i] = value;
		return ret;
	}
	
	private static GriddedRegion u3Reg;
	
	private static double[] mapU3(GriddedRegion gridReg, double[] u3Vals) {
		synchronized (NSHM23_GridFocalMechs.class) {
			if (u3Reg == null)
				u3Reg = new CaliforniaRegions.RELM_TESTING_GRIDDED(gridReg.getSpacing());
		}
		Preconditions.checkState(u3Reg.getNodeCount() == u3Vals.length);
		double avgVal = 0d;
		for (double val : u3Vals)
			avgVal += val;
		avgVal /= u3Vals.length;
		double[] ret = new double[gridReg.getNodeCount()];
		for (int i=0; i<ret.length; i++) {
			Location loc = gridReg.locationForIndex(i);
			int u3Ind = u3Reg.indexForLocation(loc);
			if (u3Ind >= 0)
				ret[i] = u3Vals[u3Ind];
			else
				ret[i] = avgVal;
		}
		return ret;
	}

}
