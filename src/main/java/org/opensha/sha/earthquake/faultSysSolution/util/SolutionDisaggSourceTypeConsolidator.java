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

public class SolutionDisaggSourceTypeConsolidator implements UnaryOperator<List<DisaggregationSourceRuptureInfo>> {
	
	public static final String NAME_ALL_FAULT_SOURCES = "All Fault Sources";
	public static final String PREFIX_TRT_FAULT_SOURCES = "Fault Sources, ";
	public static final String NAME_ALL_GRIDDED_SOURCES = "All Gridded Sources";
	public static final String PREFIX_TRT_GRIDDED_SOURCES = "Gridded Sources, ";
	
	private BaseFaultSystemSolutionERF erf;

	public SolutionDisaggSourceTypeConsolidator(BaseFaultSystemSolutionERF erf) {
		this.erf = erf;
	}

	@Override
	public List<DisaggregationSourceRuptureInfo> apply(List<DisaggregationSourceRuptureInfo> input) {
		int numFSS = erf.getNumFaultSystemSources();
		
		List<DisaggregationSourceRuptureInfo> faultSourceContribs = new ArrayList<>();
		List<DisaggregationSourceRuptureInfo> gridSourceContribs = new ArrayList<>();
		// contributions aggregated by TRT
		Map<TectonicRegionType, List<DisaggregationSourceRuptureInfo>> trtFaultSourceContribs =
				new EnumMap<>(TectonicRegionType.class);
		Map<TectonicRegionType, List<DisaggregationSourceRuptureInfo>> trtGridSourceContribs =
				new EnumMap<>(TectonicRegionType.class);
		
		for (DisaggregationSourceRuptureInfo contrib : input) {
			int sourceID = contrib.getId();
			TectonicRegionType trt = contrib.getSource().getTectonicRegionType();
			if (sourceID < numFSS) {
				faultSourceContribs.add(contrib);
				List<DisaggregationSourceRuptureInfo> myFaultSourceContribs = trtFaultSourceContribs.get(trt);
				if (myFaultSourceContribs == null) {
					myFaultSourceContribs = new ArrayList<>();
					trtFaultSourceContribs.put(trt, myFaultSourceContribs);
				}
				myFaultSourceContribs.add(contrib);
			} else {
				// gridded source
				gridSourceContribs.add(contrib);
				List<DisaggregationSourceRuptureInfo> myGridSourceContribs = trtGridSourceContribs.get(trt);
				if (myGridSourceContribs == null) {
					myGridSourceContribs = new ArrayList<>();
					trtGridSourceContribs.put(trt, myGridSourceContribs);
				}
				myGridSourceContribs.add(contrib);
			}
		}
		
		List<DisaggregationSourceRuptureInfo> ret = new ArrayList<>();
		
		if (!faultSourceContribs.isEmpty())
			ret.add(DisaggregationSourceRuptureInfo.consolidate(faultSourceContribs, -1, NAME_ALL_FAULT_SOURCES));
		
		if (trtFaultSourceContribs.size() > 1) {
			for (TectonicRegionType trt : trtFaultSourceContribs.keySet()) {
				List<DisaggregationSourceRuptureInfo> contribList = trtFaultSourceContribs.get(trt);
				ret.add(DisaggregationSourceRuptureInfo.consolidate(contribList, -1, PREFIX_TRT_FAULT_SOURCES+trt));
			}
		}
		
		if (!gridSourceContribs.isEmpty())
			ret.add(DisaggregationSourceRuptureInfo.consolidate(gridSourceContribs, -1, NAME_ALL_GRIDDED_SOURCES));
		
		if (trtGridSourceContribs.size() > 1) {
			for (TectonicRegionType trt : trtGridSourceContribs.keySet()) {
				List<DisaggregationSourceRuptureInfo> contribList = trtGridSourceContribs.get(trt);
				ret.add(DisaggregationSourceRuptureInfo.consolidate(contribList, -1, PREFIX_TRT_GRIDDED_SOURCES+trt));
			}
		}
		
		return ret;
	}

}
