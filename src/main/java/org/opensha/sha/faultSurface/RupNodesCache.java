package org.opensha.sha.faultSurface;

import java.io.Serializable;

import org.opensha.commons.geo.GriddedRegion;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;

public interface RupNodesCache extends Serializable {
	
	/**
	 * Returns an array of indexes to nodes in the given region that this rupture
	 * touches (empty array if no such nodes exist), or null if rup type not supported
	 * @param source
	 * @param rup
	 * @param srcIndex
	 * @param rupIndex
	 * @param region
	 * @return
	 */
	public int[] getNodesForRup(ProbEqkSource source, EqkRupture rup,
			int srcIndex, int rupIndex,GriddedRegion region);
	
	/**
	 * Returns the fraction of the rupture inside of each node returned by
	 * getNodesForRup(), or null if rup type not supported
	 * @param source
	 * @param rup
	 * @param srcIndex
	 * @param rupIndex
	 * @param region
	 * @return
	 */
	public double[] getFractsInNodesForRup(ProbEqkSource source, EqkRupture rup,
			int srcIndex, int rupIndex,GriddedRegion region);

}
