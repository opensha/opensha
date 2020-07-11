package scratch.UCERF3.utils;

import java.util.ArrayList;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

public class QuickSurfaceDistanceCalculator {

	/**
	 * This is a utility to calculate the distance between two surfaces where the distances is not
	 * important if greater than a certain threshold. It first calculates the distance between the
	 * corners and midpoints of the two sections. If this distance is greater than the given cutoff
	 * value, NaN is returned and the actual distance isn't calculated. If it is within the cutoff,
	 * the precise distance is calculated and returned
	 * 
	 * @param surface1
	 * @param surface2
	 * @param cornerMidpointDistCutoff
	 * @return
	 */
	public static double calcMinDistance(RuptureSurface surface1, RuptureSurface surface2,
			double cornerMidpointDistCutoff) {
		if (cornerMidpointDistCutoff > 0) {
			double cornerMdptDist = calcMinCornerMidptDist(surface1, surface2);

			if (cornerMdptDist >= cornerMidpointDistCutoff)
				return Double.NaN;
		}

		double minDist = Double.MAX_VALUE;
		
		double dist;
		for (Location pt1 : surface1.getEvenlyDiscritizedListOfLocsOnSurface()) {
			for (Location pt2 : surface2.getEvenlyDiscritizedListOfLocsOnSurface()) {
				dist = LocationUtils.linearDistanceFast(pt1, pt2);
				if (dist < minDist)
					minDist = dist;
			}
		}

		return minDist;
	}

	private static double calcMinCornerMidptDist(RuptureSurface surface1, RuptureSurface surface2) {
		double minDist = Double.MAX_VALUE;

		ArrayList<Location> locs1 = getCornerMidpts(surface1);
		ArrayList<Location> locs2 = getCornerMidpts(surface2);

		for (Location loc1 : locs1) {
			for (Location loc2 : locs2) {
				double dist = LocationUtils.linearDistanceFast(loc1, loc2);
				if (dist < minDist)
					minDist = dist;
			}
		}

		return minDist;
	}

	private static ArrayList<Location> getCornerMidpts(RuptureSurface surface) {
		ArrayList<Location> pts = new ArrayList<>();
		
		if (surface instanceof EvenlyGriddedSurface) {
			EvenlyGriddedSurface gridSurf = (EvenlyGriddedSurface)surface;
			
			int lastRow = gridSurf.getNumRows()-1;
			int lastCol = gridSurf.getNumCols()-1;

			pts.add(gridSurf.get(0, 0));
			pts.add(gridSurf.get(0, lastCol));
			pts.add(gridSurf.get(lastRow, 0));
			pts.add(gridSurf.get(lastRow, lastCol));

			int midRow = -1;
			int midCol = -1;
			if (lastRow > 3)
				midRow = gridSurf.getNumRows()/2;
			if (lastCol > 3)
				midCol = gridSurf.getNumCols()/2;
			if (midRow > 0) {
				pts.add(gridSurf.get(midRow, 0));
				pts.add(gridSurf.get(midRow, lastCol));
			}
			if (midCol > 0) {
				pts.add(gridSurf.get(0, midCol));
				pts.add(gridSurf.get(lastRow, midCol));
			}
			if (midRow > 0 && midCol > 0) {
				pts.add(gridSurf.get(midRow, midCol));
			}
		} else {
			pts.addAll(surface.getPerimeter());
			MinMaxAveTracker latTrack = new MinMaxAveTracker();
			MinMaxAveTracker lonTrack = new MinMaxAveTracker();
			MinMaxAveTracker depthTrack = new MinMaxAveTracker();
			for (Location loc : pts) {
				latTrack.addValue(loc.getLatitude());
				lonTrack.addValue(loc.getLongitude());
				depthTrack.addValue(loc.getDepth());
			}
			pts.add(new Location(latTrack.getAverage(), lonTrack.getAverage(),
					depthTrack.getAverage()));
		}
		return pts;
	}

}
