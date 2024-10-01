package org.opensha.sha.earthquake.faultSysSolution.util;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.opensha.sha.calc.disaggregation.DisaggregationSourceRuptureInfo;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;

public class SolutionDisaggConsolidator implements UnaryOperator<List<DisaggregationSourceRuptureInfo>> {
	
	public static final String NAME_SINGLE_GRIDDED_SOURCES = "Gridded Sources";
	public static final String PREFIX_TRT_GRIDDED_SOURCES = "Gridded Sources, ";
	
	private BaseFaultSystemSolutionERF erf;
	private boolean participation;

	public SolutionDisaggConsolidator(BaseFaultSystemSolutionERF erf) {
		this(erf, true);
	}

	public SolutionDisaggConsolidator(BaseFaultSystemSolutionERF erf, boolean participation) {
		this.erf = erf;
		this.participation = participation;
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
		
		double duration = participation ? Double.NaN : erf.getTimeSpan().getDuration();
		
		Map<Integer, List<DisaggregationSourceRuptureInfo>> nucleationContributions = null;
		if (!participation) {
			// need to rescale to nucleation
			// this can be slow, do it in parallel
			Map<Integer, CompletableFuture<List<DisaggregationSourceRuptureInfo>>> nucleationRescaleFutures = new HashMap<>();
			for (DisaggregationSourceRuptureInfo contrib : input) {
				int sourceID = contrib.getId();
				if (sourceID < numFSS) {
					nucleationRescaleFutures.put(sourceID, CompletableFuture.supplyAsync(new Supplier<List<DisaggregationSourceRuptureInfo>>() {

						@Override
						public List<DisaggregationSourceRuptureInfo> get() {
							// safer to calculate rupArea as sum of section areas (in case anything is screwy with rupAreas,
							// which could be intentional in the case of branch averaging while retaining multiple copies).
							double rupArea = 0d;
							int fssIndex = erf.getFltSysRupIndexForSource(sourceID);
							int numSects = 0;
							for (int s : rupSet.getSectionsIndicesForRup(fssIndex)) {
								rupArea += rupSet.getAreaForSection(s);
								numSects++;
							}
							List<DisaggregationSourceRuptureInfo> ret = new ArrayList<>(numSects);
							for (FaultSection sect : rupSet.getFaultSectionDataForRupture(fssIndex)) {
								// scale for nucleation
								double sectArea = rupSet.getAreaForSection(sect.getSectionId());
								double nuclFract = sectArea / rupArea;
								if (nuclFract > 1d && nuclFract > 1.001d)
									// assume rounding error
									nuclFract = 1d;
								Preconditions.checkState((float)nuclFract <= 1f, "Nucleation fraction = %s / %s = %s for %s. %s",
										(float)sectArea, (float)rupArea, (float)nuclFract, contrib.getId(), contrib.getName());
								if ((float)nuclFract == 1f)
									// don't bother
									ret.add(contrib);
								else
									ret.add(contrib.getScaled(nuclFract, duration));
							}
							return ret;
						}
					}));
				}
			}
			nucleationContributions = new HashMap<>();
			for (Integer sourceID : nucleationRescaleFutures.keySet())
				nucleationContributions.put(sourceID, nucleationRescaleFutures.get(sourceID).join());
		}
		
		for (DisaggregationSourceRuptureInfo contrib : input) {
			int sourceID = contrib.getId();
			if (sourceID < numFSS) {
				// fss rupture
				int prevParent = -1;
				int fssIndex = erf.getFltSysRupIndexForSource(sourceID);
				List<DisaggregationSourceRuptureInfo> nuclContribs = participation ? null : nucleationContributions.get(sourceID);
				List<FaultSection> sects = rupSet.getFaultSectionDataForRupture(fssIndex);
				for (int s=0; s<sects.size(); s++) {
					FaultSection sect = sects.get(s);
					DisaggregationSourceRuptureInfo sectContrib;
					if (participation) {
						sectContrib = contrib;
					} else {
						sectContrib = nuclContribs.get(s);
					}
					int parentID = sect.getParentSectionId();
					if (parentID < 0) {
						if (!noParentSectContribs.containsKey(sect.getSectionId()))
							noParentSectContribs.put(sect.getSectionId(), new ArrayList<>());
						noParentSectContribs.get(sect.getSectionId()).add(sectContrib);
					} else if (parentID != prevParent) {
						// likely (but not necessarily) new
						List<DisaggregationSourceRuptureInfo> parentContribs = parentSectContribs.get(parentID);
						if (parentContribs == null) {
							parentContribs = new ArrayList<>();
							parentSectContribs.put(parentID, parentContribs);
							parentNames.put(parentID, sect.getParentSectionName());
						} else if (participation && parentSectContribs.get(parentSectContribs.size()-1) == sectContrib) {
							// rupture jumps back to the same parent, don't count it twice (participation)
							continue;
						}
						parentContribs.add(sectContrib);
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
			ret.add(DisaggregationSourceRuptureInfo.consolidate(parentSectContribs.get(parentID), parentID, parentNames.get(parentID)));
		
		for (int sectID : noParentSectContribs.keySet())
			ret.add(DisaggregationSourceRuptureInfo.consolidate(noParentSectContribs.get(sectID), sectID, rupSet.getFaultSectionData(sectID).getName()));
		
		if (!trtGridSourceContribs.isEmpty()) {
			for (TectonicRegionType trt : trtGridSourceContribs.keySet()) {
				List<DisaggregationSourceRuptureInfo> gridSourceContribs = trtGridSourceContribs.get(trt);
				if (trtGridSourceContribs.size() == 1)
					ret.add(DisaggregationSourceRuptureInfo.consolidate(gridSourceContribs, -1, NAME_SINGLE_GRIDDED_SOURCES));
				else
					ret.add(DisaggregationSourceRuptureInfo.consolidate(gridSourceContribs, -1, PREFIX_TRT_GRIDDED_SOURCES+trt));
			}
		}
		
		return ret;
	}

}
