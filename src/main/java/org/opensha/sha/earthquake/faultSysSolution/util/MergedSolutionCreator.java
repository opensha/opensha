package org.opensha.sha.earthquake.faultSysSolution.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

public class MergedSolutionCreator {
	
	/**
	 * Mrege the given list of fault system solutions into a single solution. Does not combine any modules, and does
	 * not yet merge gridded seismicity (TODO)
	 * 
	 * @param sols
	 * @return
	 */
	public static FaultSystemSolution merge(List<FaultSystemSolution> sols) {
		return merge(sols.toArray(new FaultSystemSolution[0]));
	}
	
	/**
	 * This merges the given list of fault system solutions into a single solution. This does not yet combine 
	 * any modules, with the exception of RupSetTectonicRegimes, for which an exception is thrown if 
	 * anyone of the FSSs is lacking this module. This also does not yet merge gridded seismicity (TODO)
	 * 
	 * @param sols
	 * @return
	 */
	public static FaultSystemSolution merge(FaultSystemSolution... sols) {
		Preconditions.checkState(sols.length > 1, "Need at least 2 solutions to merge");
		
		int totNumSects = 0;
		int totNumRups = 0;
		for (int i=0; i<sols.length; i++) {
			FaultSystemSolution sol = sols[i];
			FaultSystemRupSet rupSet = sol.getRupSet();
			System.out.println("RupSet "+i+" has "+rupSet.getNumSections()+" sects, "+rupSet.getNumRuptures()+" rups");
			totNumSects += rupSet.getNumSections();
			totNumRups += rupSet.getNumRuptures();
			if (sol.hasAvailableModule(GridSourceProvider.class))
				System.err.println("WARNING: this does not yet merge grid source providers"); // TODO
		}
		System.out.println("Total: "+totNumSects+" sects, "+totNumRups+" rups");
		
		List<FaultSection> mergedSects = new ArrayList<>(totNumSects);
		List<List<Integer>> sectionForRups = new ArrayList<>(totNumSects);
		double[] mags = new double[totNumRups];
		double[] rakes = new double[totNumRups];
		double[] rupAreas = new double[totNumRups];
		double[] rupLengths = new double[totNumRups];
		double[] rates = new double[totNumRups];
	    TectonicRegionType[] trForRupArray = new TectonicRegionType[totNumRups];

		
		int sectIndex = 0;
		int rupIndex = 0;
		int solIndex = -1;
		Map<Integer, String> prevParents = new HashMap<>();
		
		for (FaultSystemSolution sol : sols) {
			solIndex+=1;
			FaultSystemRupSet rupSet = sol.getRupSet();
			int[] sectMappings = new int[rupSet.getNumSections()];
			System.out.println("Merging sol with "+rupSet.getNumSections()+" sects and "+rupSet.getNumRuptures()+" rups");
			
			Map<Integer, String> newParents = new HashMap<>();
			for (int s=0; s<sectMappings.length; s++) {
				FaultSection sect = rupSet.getFaultSectionData(s);
				if (sect.getParentSectionId() >= 0)
					newParents.put(sect.getParentSectionId(), sect.getParentSectionName());
				sect = sect.clone();
				sectMappings[s] = sectIndex;
				sect.setSectionId(sectIndex);
				mergedSects.add(sect);
				
				sectIndex++;
			}
			// see if there are any duplicate parent IDs
			for (int parentID : newParents.keySet()) {
				if (prevParents.containsKey(parentID))
					System.err.println("WARNING: multiple solutions use the same parent section id ("+parentID+"): "
							+prevParents.get(parentID)+" and "+newParents.get(parentID));
				else
					prevParents.put(parentID, newParents.get(parentID));
			}
			
			RupSetTectonicRegimes tectonicRegimes = sol.getRupSet().getModule(RupSetTectonicRegimes.class);
			if(tectonicRegimes==null)
				throw new RuntimeException("RupSetTectonicRegimes cannot be null; null found for sol index "+solIndex);

			for (int r=0; r<rupSet.getNumRuptures(); r++) {
				List<Integer> prevSectIDs = rupSet.getSectionsIndicesForRup(r);
				List<Integer> newSectIDs = new ArrayList<>(prevSectIDs.size());
				for (int s : prevSectIDs)
					newSectIDs.add(sectMappings[s]);
				sectionForRups.add(newSectIDs);
				mags[rupIndex] = rupSet.getMagForRup(r);
				rakes[rupIndex] = rupSet.getAveRakeForRup(r);
				rupAreas[rupIndex] = rupSet.getAreaForRup(r);
				rupLengths[rupIndex] = rupSet.getLengthForRup(r);
				rates[rupIndex] = sol.getRateForRup(r);
				trForRupArray[rupIndex] = tectonicRegimes.get(r);
				
				rupIndex++;
			}
		}
		
		FaultSystemRupSet mergedRupSet = new FaultSystemRupSet(mergedSects, sectionForRups, mags, rakes, rupAreas, rupLengths);

		RupSetTectonicRegimes mergedTectonicRegimes = new RupSetTectonicRegimes(mergedRupSet,trForRupArray);
		mergedRupSet.addModule(mergedTectonicRegimes);
		
		return new FaultSystemSolution(mergedRupSet, rates);
	}

}
