package scratch.UCERF3.utils;

import java.util.ArrayList;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;

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
	public static double calcMinDistance(EvenlyGriddedSurface surface1, EvenlyGriddedSurface surface2,
			double cornerMidpointDistCutoff) {
		if (cornerMidpointDistCutoff > 0) {
			double cornerMdptDist = calcMinCornerMidptDist(surface1, surface2);

			if (cornerMdptDist >= cornerMidpointDistCutoff)
				return Double.NaN;
		}

		double minDist = Double.MAX_VALUE;
		
		double dist;
		for (Location pt1 : surface1) {
			for (Location pt2 : surface2) {
				dist = LocationUtils.linearDistanceFast(pt1, pt2);
				if (dist < minDist)
					minDist = dist;
			}
		}

		return minDist;
	}

	private static double calcMinCornerMidptDist(EvenlyGriddedSurface surface1, EvenlyGriddedSurface surface2) {
		double minDist = Double.MAX_VALUE;

		ArrayList<int[]> pts1 = getCornerMidpts(surface1);
		ArrayList<int[]> pts2 = getCornerMidpts(surface2);

		for (int[] pt1 : pts1) {
			for (int[] pt2 : pts2) {
				Location loc1 = surface1.get(pt1[0], pt1[1]);
				Location loc2 = surface2.get(pt2[0], pt2[1]);
				double dist = LocationUtils.linearDistanceFast(loc1, loc2);
				if (dist < minDist)
					minDist = dist;
			}
		}

		return minDist;
	}

	private static ArrayList<int[]> getCornerMidpts(EvenlyGriddedSurface surface) {
		ArrayList<int[]> pts = new ArrayList<int[]>();

		int lastRow = surface.getNumRows()-1;
		int lastCol = surface.getNumCols()-1;

		pts.add(getIndexArray(0, 0));
		pts.add(getIndexArray(0, lastCol));
		pts.add(getIndexArray(lastRow, 0));
		pts.add(getIndexArray(lastRow, lastCol));

		int midRow = -1;
		int midCol = -1;
		if (lastRow > 3)
			midRow = surface.getNumRows()/2;
		if (lastCol > 3)
			midCol = surface.getNumCols()/2;
		if (midRow > 0) {
			pts.add(getIndexArray(midRow, 0));
			pts.add(getIndexArray(midRow, lastCol));
		}
		if (midCol > 0) {
			pts.add(getIndexArray(0, midCol));
			pts.add(getIndexArray(lastRow, midCol));
		}
		if (midRow > 0 && midCol > 0) {
			pts.add(getIndexArray(midRow, midCol));
		}
		return pts;
	}

	private static int[] getIndexArray(int row, int col) {
		int[] ret = { row, col };
		return ret;
	}

}
