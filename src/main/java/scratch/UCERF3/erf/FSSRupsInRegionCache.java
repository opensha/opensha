package scratch.UCERF3.erf;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.param.FaultGridSpacingParam;
import org.opensha.sha.earthquake.rupForecastImpl.FaultRuptureSource;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.RupInRegionCache;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * This is a cache for tracking what inversion ruptures are in regions to speed up various
 * ERF_Calculator methods. Instantiate with your FaultSystemSolutionERF then pass into
 * calculator methods for best results.
 * 
 * @author kevin
 *
 */
public class FSSRupsInRegionCache implements RupInRegionCache {
	
	private ConcurrentMap<Region, boolean[]> sectsInRegions = Maps.newConcurrentMap();
	
	private int numRups = -1;
	private ConcurrentMap<Region, ConcurrentMap<Integer, Boolean>> rupMap = Maps
			.newConcurrentMap();
	private ConcurrentMap<Region, ConcurrentMap<Integer, Boolean>> sectMap = Maps
			.newConcurrentMap();

	@Override
	public boolean isRupInRegion(ERF erf, ProbEqkSource source, EqkRupture rup,
			int srcIndex, int rupIndex, Region region) {
		Preconditions.checkArgument(erf instanceof FaultSystemSolutionERF, "ERF must be a FaultSystemSolutionERF");
		FaultSystemSolutionERF fssERF = (FaultSystemSolutionERF)erf;
		FaultSystemSolution sol = fssERF.getSolution();
		Preconditions.checkNotNull(sol, "FSS ERF has null solution");
		
		double erfGridSpacing = (Double)fssERF.getParameter(FaultGridSpacingParam.NAME).getValue();
		
		RuptureSurface surf = rup.getRuptureSurface();
		int invIndex;
		if (srcIndex >= fssERF.getNumFaultSystemSources())
			invIndex = -1;
		else
			invIndex = fssERF.getFltSysRupIndexForSource(srcIndex);
		if (invIndex >= 0 && source instanceof FaultRuptureSource) {
			Preconditions.checkState(source.getName().contains(invIndex+""),
					"Bad mapping of inv index "+invIndex+" for source name: "+source.getName());
			return isRupInRegion(sol, invIndex, region, erfGridSpacing);
		}
		for (Location loc : surf
				.getEvenlyDiscritizedListOfLocsOnSurface())
			if (region.contains(loc))
				return true;
		return false;
	}
	
	public boolean isRupInRegion(FaultSystemSolution sol, int fssIndex, Region region, double surfGridSpacing) {
		Preconditions.checkArgument(fssIndex >= 0 && fssIndex < sol.getRupSet().getNumRuptures());
		synchronized (this) {
			// check if the solution has changed
			if (numRups != sol.getRupSet().getNumRuptures()) {
				rupMap.clear();
				sectMap.clear();
				numRups = sol.getRupSet().getNumRuptures();
			}
		}
		synchronized (region) {
			if (!sectsInRegions.containsKey(region)) {
				// calculate sections in regions
				FaultSystemRupSet rupSet = sol.getRupSet();
				boolean[] sects = new boolean[rupSet.getNumSections()];
				for (int i=0; i<sects.length; i++) {
					FaultSectionPrefData sect = rupSet.getFaultSectionData(i);
					StirlingGriddedSurface surf = sect.getStirlingGriddedSurface(surfGridSpacing, false, true);
					boolean inside = false;
					for (Location loc : surf.getEvenlyDiscritizedListOfLocsOnSurface()) {
						if (region.contains(loc)) {
							inside = true;
							break;
						}
					}
					sects[i] = inside;
				}
				sectsInRegions.put(region, sects);
			}
		}
		boolean[] sects = sectsInRegions.get(region);

		ConcurrentMap<Integer, Boolean> regRupMap = rupMap
				.get(region);
		ConcurrentMap<Integer, Boolean> regSectMap = sectMap
				.get(region);
		if (regRupMap == null) {
			regRupMap = Maps.newConcurrentMap();
			rupMap.putIfAbsent(region, regRupMap);
			// in case another thread put it in
			// first
			regRupMap = rupMap.get(region);
		}
		if (regSectMap == null) {
			regSectMap = Maps.newConcurrentMap();
			sectMap.putIfAbsent(region, regSectMap);
			// in case another thread put it in
			// first
			regSectMap = sectMap.get(region);
		}
		Boolean inside = regRupMap.get(fssIndex);
		if (inside == null) {
			inside = false;
			for (int index : sol.getRupSet().getSectionsIndicesForRup(fssIndex)) {
				if (sects[index]) {
					inside = true;
					break;
				}
			}
			//				for (Location loc : surf
			//						.getEvenlyDiscritizedListOfLocsOnSurface())
			//					if (region.contains(loc)) {
			//						inside = true;
			//						break;
			//					}
			regRupMap.putIfAbsent(fssIndex, inside);
		}
		return inside;
	}

}
