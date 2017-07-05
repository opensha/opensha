package scratch.UCERF3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import com.google.common.collect.Maps;

public abstract class SlipEnabledRupSet extends FaultSystemRupSet {

	/**
	 * This gives average slip (SI units: m) for the given rupture
	 * @param rupIndex
	 * @return
	 */
	public abstract double getAveSlipForRup(int rupIndex);

	/**
	 * This gives average slip (SI units: m) for the each rupture
	 * @return
	 */
	public abstract double[] getAveSlipForAllRups();

	/**
	 * This gives the average slip (SI untis: m) on each section for all ruptures
	 * @return
	 */
	public List<double[]> getSlipOnSectionsForAllRups() {
		ArrayList<double[]> slips = new ArrayList<double[]>();
		for (int rupIndex=0; rupIndex<getNumRuptures(); rupIndex++)
			slips.add(getSlipOnSectionsForRup(rupIndex));
		return slips;
	}
	
	protected ConcurrentMap<Integer, double[]> rupSectionSlipsCache = Maps.newConcurrentMap();
	
	@Override
	public void clearCache() {
		super.clearCache();
		rupSectionSlipsCache.clear();
	}

	/**
	 * This gives the slip (SI untis: m) on each section for the rth rupture
	 * @return
	 */
	public final double[] getSlipOnSectionsForRup(int rthRup) {
		double[] slips = rupSectionSlipsCache.get(rthRup);
		if (slips == null) {
			synchronized (rupSectionSlipsCache) {
				slips = rupSectionSlipsCache.get(rthRup);
				if (slips != null)
					return slips;
				slips = calcSlipOnSectionsForRup(rthRup);
				rupSectionSlipsCache.putIfAbsent(rthRup, slips);
			}
		}
		return slips;
	}
	
	protected abstract double[] calcSlipOnSectionsForRup(int rthRup);

}