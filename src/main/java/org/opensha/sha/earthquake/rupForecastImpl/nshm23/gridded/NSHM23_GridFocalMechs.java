package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import java.io.IOException;

import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.AnalysisRegions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.FaultStyleRegions;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.SeismicityRegions;

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
	
	public static double[] getFractStrikeSlip(SeismicityRegions region, GriddedRegion gridRegion) throws IOException {
		double[] ret = constant(gridRegion.getNodeCount(), Double.NaN);
		mapConstant(gridRegion, ret, FaultStyleRegions.WUS_COMPRESSIONAL.load(), 0.5d);
		mapConstant(gridRegion, ret, FaultStyleRegions.WUS_EXTENSIONAL.load(), 0.5d);
		// this is the rest, mostly CEUS, 100% SS
		replaceNaNs(ret, 1d);
		return ret;
	}
	
	public static double[] getFractReverse(SeismicityRegions region, GriddedRegion gridRegion) throws IOException {
		double[] ret = constant(gridRegion.getNodeCount(), Double.NaN);
		mapConstant(gridRegion, ret, FaultStyleRegions.WUS_COMPRESSIONAL.load(), 0.5d);
		// this is the rest, CEUS and WUS extensional
		replaceNaNs(ret, 0d);
		return ret;
	}
	
	public static double[] getFractNormal(SeismicityRegions region, GriddedRegion gridRegion) throws IOException {
		double[] ret = constant(gridRegion.getNodeCount(), Double.NaN);
		mapConstant(gridRegion, ret, FaultStyleRegions.WUS_EXTENSIONAL.load(), 0.5d);
		// this is the rest, CEUS and WUS compressional
		replaceNaNs(ret, 0d);
		return ret;
	}
	
	private static double[] constant(int length, double value) {
		double[] ret = new double[length];
		for (int i=0; i<length; i++)
			ret[i] = value;
		return ret;
	}
	
	private static void replaceNaNs(double[] values, double value) {
		for (int i=0; i<values.length; i++)
			if (Double.isNaN(values[i]))
				values[i] = value;
	}
	
	private static GriddedRegion u3Reg;
	
	private static void mapConstant(GriddedRegion gridReg, double[] values, Region reg, double value) {
		for (int i=0; i<values.length; i++) {
			Location loc = gridReg.locationForIndex(i);
			if (reg.contains(loc))
				values[i] = value;
		}
	}
	
	private static void mapU3(GriddedRegion gridReg, double[] values, double[] u3Vals) {
		synchronized (NSHM23_GridFocalMechs.class) {
			if (u3Reg == null)
				u3Reg = new CaliforniaRegions.RELM_TESTING_GRIDDED(gridReg.getSpacing());
		}
		Preconditions.checkState(u3Reg.getNodeCount() == u3Vals.length);
		Preconditions.checkState(gridReg.getNodeCount() == values.length);
		double avgVal = 0d;
		for (double val : u3Vals)
			avgVal += val;
		avgVal /= u3Vals.length;
		for (int i=0; i<values.length; i++) {
			Location loc = gridReg.locationForIndex(i);
			int u3Ind = u3Reg.indexForLocation(loc);
			if (u3Ind >= 0)
				values[i] = u3Vals[u3Ind];
			else
				values[i] = avgVal;
		}
	}
	
	public static void main(String[] args) throws IOException {
		SeismicityRegions reg = SeismicityRegions.CONUS_WEST;
		GriddedRegion gridReg = NSHM23_InvConfigFactory.getGriddedSeisRegion(reg);
		
		double[] ss = getFractStrikeSlip(reg, gridReg);
		double[] rev = getFractReverse(reg, gridReg);
		double[] norm = getFractNormal(reg, gridReg);
		
		for (int i=0; i<gridReg.getNodeCount(); i++) {
			double sum = ss[i] + rev[i] + norm[i];
			Preconditions.checkState((float)sum == 1f, "Bad sum: %s + %s + %s = %s; loc: %s",
					ss[i], rev[i], norm[i], gridReg.getLocation(i));
		}
		System.out.println("Validated");
	}

}
