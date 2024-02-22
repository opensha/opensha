package org.opensha.sha.earthquake.faultSysSolution.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.opensha.sha.calc.disaggregation.DisaggregationSourceRuptureInfo;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.faultSurface.FaultSection;

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
		List<DisaggregationSourceRuptureInfo> gridSourceContribs = new ArrayList<>();
		
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
				gridSourceContribs.add(contrib);
			}
		}
		
		List<DisaggregationSourceRuptureInfo> ret = new ArrayList<>();
		
		for (int parentID : parentSectContribs.keySet())
			ret.add(consolidate(parentSectContribs.get(parentID), parentID, parentNames.get(parentID)));
		
		for (int sectID : noParentSectContribs.keySet())
			ret.add(consolidate(noParentSectContribs.get(sectID), sectID, rupSet.getFaultSectionData(sectID).getName()));
		
		if (!gridSourceContribs.isEmpty())
			ret.add(consolidate(gridSourceContribs, -1, "Gridded Sources"));
		
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
