package org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.opensha.commons.logicTree.Affects;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RupSetDeformationModel;
import org.opensha.sha.earthquake.faultSysSolution.RupSetFaultModel;
import org.opensha.sha.earthquake.faultSysSolution.modules.NamedFaults;
import org.opensha.sha.earthquake.faultSysSolution.modules.RegionsOfInterest;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.GeoJSONFaultReader;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;

import com.google.common.base.Preconditions;

@Affects(FaultSystemRupSet.SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME)
@Affects(FaultSystemRupSet.RUP_PROPS_FILE_NAME)
@Affects(FaultSystemSolution.RATES_FILE_NAME)
public enum NSHM23_FaultModels implements LogicTreeNode, RupSetFaultModel {
	
	NSHM23_v1p4("NSHM23 WUS Fault Model v1.4", "NSHM23 WUS v1.4", NSHM23_DeformationModels.GEOLOGIC, 1d) {
		@Override
		protected List<? extends FaultSection> loadFaultSections() throws IOException {
			String sectPath = NSHM23_SECTS_PATH_PREFIX+"v1p4/NSHM23_FaultSections_v1p4.geojson";
			Reader sectsReader = new BufferedReader(new InputStreamReader(
					GeoJSONFaultReader.class.getResourceAsStream(sectPath)));
			Preconditions.checkNotNull(sectsReader, "Fault model file not found: %s", sectPath);
			return GeoJSONFaultReader.readFaultSections(sectsReader);
		}
	};
	
	private static final ConcurrentMap<NSHM23_FaultModels, List<? extends FaultSection>> sectsCache = new ConcurrentHashMap<>();
	
	public static final String NSHM23_SECTS_PATH_PREFIX = "/data/erf/nshm23/fault_models/";
	
	private String name;
	private String shortName;
	private RupSetDeformationModel defaultDM;
	private double weight;

	private NSHM23_FaultModels(String name, String shortName, RupSetDeformationModel defaultDM, double weight) {
		this.name = name;
		this.shortName = shortName;
		this.defaultDM = defaultDM;
		this.weight = weight;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public double getNodeWeight(LogicTreeBranch<?> fullBranch) {
		return weight;
	}

	@Override
	public String getFilePrefix() {
		return name();
	}

	@Override
	public final List<? extends FaultSection> getFaultSections() throws IOException {
		List<? extends FaultSection> sects = sectsCache.get(this);
		if (sects == null) {
			synchronized (sectsCache) {
				sects = sectsCache.get(this);
				if (sects == null) {
					sects = loadFaultSections();
					sectsCache.put(this, sects);
				}
			}
		}
		// now return a copy
		List<FaultSection> copy = new ArrayList<>();
		for (FaultSection sect : sects)
			copy.add(sect.clone());
		return copy;
	}
	
	protected abstract List<? extends FaultSection> loadFaultSections() throws IOException;

	@Override
	public RupSetDeformationModel getDefaultDeformationModel() {
		return defaultDM;
	}

	@Override
	public void attachDefaultModules(FaultSystemRupSet rupSet) {
		rupSet.addAvailableModule(new Callable<NamedFaults>() {

			@Override
			public NamedFaults call() throws Exception {
				// load geologic sections that have named fault associations in the parent name field
				Map<Integer, GeoJSONFaultSection> geoSects =
						NSHM23_DeformationModels.getGeolFullSects(NSHM23_FaultModels.this);
				
				// find unique set of parent IDs that we have
				HashSet<Integer> myParentIDs = new HashSet<>();
				for (FaultSection sect : rupSet.getFaultSectionDataList())
					myParentIDs.add(sect.getParentSectionId());
				
				Map<String, List<Integer>> namedFaults = new HashMap<>();
				
				for (Integer parentID : myParentIDs) {
					GeoJSONFaultSection sect = geoSects.get(parentID);
					Preconditions.checkNotNull(sect, "Couldn't find geologic full section for parent=%s", parentID);
					if (sect.getParentSectionName() != null) {
						List<Integer> sectIDs = namedFaults.get(sect.getParentSectionName());
						if (sectIDs == null) {
							sectIDs = new ArrayList<>();
							namedFaults.put(sect.getParentSectionName(), sectIDs);
						}
						sectIDs.add(parentID);
					}
				}
				
				Preconditions.checkState(!namedFaults.isEmpty(), "No named faults found");
				return new NamedFaults(rupSet, namedFaults);
			}
		}, NamedFaults.class);
		rupSet.addAvailableModule(new Callable<RegionsOfInterest>() {

			@Override
			public RegionsOfInterest call() throws Exception {
				return new RegionsOfInterest(NSHM23_RegionLoader.loadAllRegions(rupSet.getFaultSectionDataList()));
			}
		}, RegionsOfInterest.class);
	}

}
