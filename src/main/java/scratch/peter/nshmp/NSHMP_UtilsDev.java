package scratch.peter.nshmp;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.nshmp2.erf.source.FaultERF;
import org.opensha.nshmp2.erf.source.FaultSource;
import org.opensha.nshmp2.util.NSHMP_Utils;

/**
 * Class of experimental static utility methods.
 *
 * @author Peter Powers
 * @version $Id:$
 */
public class NSHMP_UtilsDev {

	private static double minLat = 24.6;
	private static double maxLat = 50.0;
	private static double minLon = -125.0;
	private static double maxLon = -65.0;
	private static double spacing = 0.05;

	/**
	 * Returns the gridded region spanned by any NSHMP national scale data set.
	 * Regions has a 0.05 degreee spacing by default.
	 * @return the NSHMP gridded region
	 */
	public static GriddedRegion getNSHMP_Region() {
		Location usHazLoc1 = new Location(minLat, minLon);
		Location usHazLoc2 = new Location(maxLat, maxLon);
		return new GriddedRegion(usHazLoc1, usHazLoc2, spacing,
			GriddedRegion.ANCHOR_0_0);
	}
	
	/**
	 * Returns the gridded region spanned by the NSHMP with a custom spacing.
	 * Such regions may be used to extract subsets of data from an
	 * NSHMP_CurveContainer
	 * @param spacing 
	 * @return the NSHMP gridded region
	 */
	public static GriddedRegion getNSHMP_Region(double spacing) {
		Location usHazLoc1 = new Location(minLat, minLon);
		Location usHazLoc2 = new Location(maxLat, maxLon);
		return new GriddedRegion(usHazLoc1, usHazLoc2, spacing,
			GriddedRegion.ANCHOR_0_0);
	}

	/**
	 * Short form to parse a string to a double; probably should include a call
	 * to trim().
	 * @param s
	 * @return the converted string
	 */
	public static double toNum(String s) {
		return Double.parseDouble(s);
	}

//	for (NSHMP_ERF erf : caTmp) {
//	if (erf instanceof FaultERF) {
//		FaultERF ferf = (FaultERF) erf;
//		RegionUtils.regionToKML(calcBounds(ferf), "BOUNDS_" + ferf.getName(), Color.PINK);
//	}
//}
	private static Region calcBounds(FaultERF erf) {
		double minLat = Double.POSITIVE_INFINITY;
		double maxLat = Double.NEGATIVE_INFINITY;
		double minLon = Double.POSITIVE_INFINITY;
		double maxLon = Double.NEGATIVE_INFINITY;
		for (FaultSource source : erf.getSources()) {
			LocationList locs = source.getAllSourceLocs();
			minLat = Math.min(minLat, LocationUtils.calcMinLat(locs));
			maxLat = Math.max(maxLat, LocationUtils.calcMaxLat(locs));
			minLon = Math.min(minLon, LocationUtils.calcMinLon(locs));
			maxLon = Math.max(maxLon, LocationUtils.calcMaxLon(locs));
		}
		return NSHMP_Utils.creatBounds(minLat, maxLat, minLon, maxLon, 15.0);
	}

}
