package org.opensha.sha.earthquake.faultSysSolution.util;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.opensha.sha.calc.disaggregation.DisaggregationSourceRuptureInfo;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.util.TectonicRegionType;

public class SolutionDisaggConsolidator implements UnaryOperator<List<DisaggregationSourceRuptureInfo>> {
	
	private BaseFaultSystemSolutionERF erf;

	public SolutionDisaggConsolidator(BaseFaultSystemSolutionERF erf) {
		this.erf = erf;
	}

	@Override
	public List<DisaggregationSourceRuptureInfo> apply(List<DisaggregationSourceRuptureInfo> input) {
		FaultSystemRupSet rupSet = erf.getSolution().getRupSet();
		int numFSS = erf.getNumFaultSystemSources();
		
		// contributions for sections with parent section IDs
		Map<Integer, List<DisaggregationSourceRuptureInfo>> parentSectContribs = new HashMap<>();
		Map<Integer, String> parentNames = new HashMap<>();
		// contributions for sections without parent section IDs
		Map<Integer, List<DisaggregationSourceRuptureInfo>> noParentSectContribs = new HashMap<>();
		// contributions for gridded sources
		Map<TectonicRegionType, List<DisaggregationSourceRuptureInfo>> trtGridSourceContribs =
				new EnumMap<>(TectonicRegionType.class);
		
		for (DisaggregationSourceRuptureInfo contrib : input) {
			int sourceID = contrib.getId();
			if (sourceID < numFSS) {
				// fss rupture
				int prevParent = -1;
				for (FaultSection sect : rupSet.getFaultSectionDataForRupture(erf.getFltSysRupIndexForSource(sourceID))) {
					int parentID = sect.getParentSectionId();
					if (parentID < 0) {
						if (!noParentSectContribs.containsKey(sect.getSectionId()))
							noParentSectContribs.put(sect.getSectionId(), new ArrayList<>());
						noParentSectContribs.get(sect.getSectionId()).add(contrib);
					} else if (parentID != prevParent) {
						// likely (but not necessarily) new
						List<DisaggregationSourceRuptureInfo> parentContribs = parentSectContribs.get(parentID);
						if (parentContribs == null) {
							parentContribs = new ArrayList<>();
							parentSectContribs.put(parentID, parentContribs);
							parentNames.put(parentID, sect.getParentSectionName());
						} else if (parentSectContribs.get(parentSectContribs.size()-1) == contrib) {
							// rupture jumps back to the same parent, don't count it twice
							continue;
						}
						parentContribs.add(contrib);
					}
					prevParent = sect.getParentSectionId();
				}
			} else {
				// gridded source
				TectonicRegionType trt = contrib.getSource().getTectonicRegionType();
				List<DisaggregationSourceRuptureInfo> gridSourceContribs = trtGridSourceContribs.get(trt);
				if (gridSourceContribs == null) {
					gridSourceContribs = new ArrayList<>();
					trtGridSourceContribs.put(trt, gridSourceContribs);
				}
				gridSourceContribs.add(contrib);
			}
		}
		
		List<DisaggregationSourceRuptureInfo> ret = new ArrayList<>();
		
		for (int parentID : parentSectContribs.keySet())
			ret.add(consolidate(parentSectContribs.get(parentID), parentID, parentNames.get(parentID)));
		
		for (int sectID : noParentSectContribs.keySet())
			ret.add(consolidate(noParentSectContribs.get(sectID), sectID, rupSet.getFaultSectionData(sectID).getName()));
		
		if (!trtGridSourceContribs.isEmpty()) {
			for (TectonicRegionType trt : trtGridSourceContribs.keySet()) {
				List<DisaggregationSourceRuptureInfo> gridSourceContribs = trtGridSourceContribs.get(trt);
				if (trtGridSourceContribs.size() == 1)
					ret.add(consolidate(gridSourceContribs, -1, "Gridded Sources"));
				else
					ret.add(consolidate(gridSourceContribs, -1, "Gridded Sources, "+trt));
			}
		}
		
		return ret;
	}
	
	private static DisaggregationSourceRuptureInfo consolidate(List<DisaggregationSourceRuptureInfo> contribs,
			int index, String name) {
		double rate = 0d;
		for (DisaggregationSourceRuptureInfo contrib : contribs)
			rate += contrib.getRate();
		return new DisaggregationSourceRuptureInfo(name, rate, index, null);
	}

}
