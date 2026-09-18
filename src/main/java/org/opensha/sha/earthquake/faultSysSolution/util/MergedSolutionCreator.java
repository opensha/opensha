package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.commons.util.modules.helpers.JSON_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.MergeableRuptureModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.MergeableSolutionModule;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

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
	 * Mrege the given list of fault system solutions into a single solution. Does not combine any modules, and does
	 * not yet merge gridded seismicity (TODO)
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

		int sectIndex = 0;
		int rupIndex = 0;

		Map<Integer, String> prevParents = new HashMap<>();
		List<Map<Integer, Integer>> sectMappingsOldToNew = new ArrayList<>(sols.length);
		List<Map<Integer, Integer>> rupMappingsOldToNew = new ArrayList<>(sols.length);

		for (FaultSystemSolution sol : sols) {
			FaultSystemRupSet rupSet = sol.getRupSet();
			int[] sectMappings = new int[rupSet.getNumSections()];
			Map<Integer, Integer> sectMappingsMap = new HashMap<>(rupSet.getNumSections());
			Map<Integer, Integer> rupMappingsMap = new HashMap<>(rupSet.getNumRuptures());
			System.out.println("Merging sol with "+rupSet.getNumSections()+" sects and "+rupSet.getNumRuptures()+" rups");

			Map<Integer, String> newParents = new HashMap<>();
			for (int s=0; s<sectMappings.length; s++) {
				FaultSection sect = rupSet.getFaultSectionData(s);
				if (sect.getParentSectionId() >= 0)
					newParents.put(sect.getParentSectionId(), sect.getParentSectionName());
				sect = sect.clone();
				sectMappings[s] = sectIndex;
				sectMappingsMap.put(s, sectIndex);
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
				rupMappingsMap.put(r, rupIndex);

				rupIndex++;
			}
			sectMappingsOldToNew.add(sectMappingsMap);
			rupMappingsOldToNew.add(rupMappingsMap);
		}

		FaultSystemRupSet mergedRupSet = new FaultSystemRupSet(mergedSects, sectionForRups, mags, rakes, rupAreas, rupLengths);
		MergedRupSetMappings mappings = new MergedRupSetMappings(sectMappingsOldToNew, rupMappingsOldToNew);
		mergedRupSet.addModule(mappings);
		for (OpenSHA_Module module : getMergeableRuptureModules(sols).values()) {
			OpenSHA_Module mergedModule = mergeRuptureModule(module, mergedRupSet, mappings, sols);
			if (mergedModule != null)
				mergedRupSet.addModule(mergedModule);
		}
		FaultSystemSolution mergedSol = new FaultSystemSolution(mergedRupSet, rates);
		for (OpenSHA_Module module : getMergeableSolutionModules(sols).values()) {
			OpenSHA_Module mergedModule = mergeSolutionModule(module, mergedSol, mappings, sols);
			if (mergedModule != null)
				mergedSol.addModule(mergedModule);
		}
		return mergedSol;
	}
	
	private static Map<Class<? extends OpenSHA_Module>, OpenSHA_Module> getMergeableRuptureModules(FaultSystemSolution... sols) {
		Map<Class<? extends OpenSHA_Module>, OpenSHA_Module> ret = new LinkedHashMap<>();
		for (FaultSystemSolution sol : sols)
			for (OpenSHA_Module module : sol.getRupSet().getModulesAssignableTo(MergeableRuptureModule.class, true))
				ret.putIfAbsent(module.getClass(), module);
		return ret;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static OpenSHA_Module mergeRuptureModule(OpenSHA_Module module, FaultSystemRupSet mergedRupSet,
			MergedRupSetMappings mappings, FaultSystemSolution... sols) {
		Class moduleClass = module.getClass();
		List<OpenSHA_Module> originalModules = new ArrayList<>(sols.length);
		for (FaultSystemSolution sol : sols)
			originalModules.add(sol.getRupSet().getModule(moduleClass));
		return ((MergeableRuptureModule)module).getForMergedRuptureSet(mergedRupSet, mappings, originalModules);
	}
	
	private static Map<Class<? extends OpenSHA_Module>, OpenSHA_Module> getMergeableSolutionModules(FaultSystemSolution... sols) {
		Map<Class<? extends OpenSHA_Module>, OpenSHA_Module> ret = new LinkedHashMap<>();
		for (FaultSystemSolution sol : sols)
			for (OpenSHA_Module module : sol.getModulesAssignableTo(MergeableSolutionModule.class, true))
				ret.putIfAbsent(module.getClass(), module);
		return ret;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static OpenSHA_Module mergeSolutionModule(OpenSHA_Module module, FaultSystemSolution mergedSol,
			MergedRupSetMappings mappings, FaultSystemSolution... sols) {
		Class moduleClass = module.getClass();
		List<OpenSHA_Module> originalModules = new ArrayList<>(sols.length);
		for (FaultSystemSolution sol : sols)
			originalModules.add(sol.getModule(moduleClass));
		return ((MergeableSolutionModule)module).getForMergedSolution(mergedSol, mappings, originalModules);
	}

	public static class MergedRupSetMappings implements JSON_BackedModule {

		private List<Map<Integer, Integer>> sectMappingsOldToNew;
		private List<Map<Integer, Integer>> rupMappingsOldToNew;

		@SuppressWarnings("unused") // deserialization
		private MergedRupSetMappings() {}

		public MergedRupSetMappings(List<Map<Integer, Integer>> sectMappingsOldToNew,
				List<Map<Integer, Integer>> rupMappingsOldToNew) {
			Preconditions.checkNotNull(sectMappingsOldToNew);
			Preconditions.checkNotNull(rupMappingsOldToNew);
			Preconditions.checkState(sectMappingsOldToNew.size() == rupMappingsOldToNew.size());
			this.sectMappingsOldToNew = sectMappingsOldToNew;
			this.rupMappingsOldToNew = rupMappingsOldToNew;
		}

		public int getNumInputRupSets() {
			return sectMappingsOldToNew.size();
		}

		public Map<Integer, Integer> getSectMappingsOldToNew(int rupSetIndex) {
			return Collections.unmodifiableMap(sectMappingsOldToNew.get(rupSetIndex));
		}

		public Map<Integer, Integer> getRuptureMappingsOldToNew(int rupSetIndex) {
			return Collections.unmodifiableMap(rupMappingsOldToNew.get(rupSetIndex));
		}

		public int getNewSectIndex(int rupSetIndex, int origSectIndex) {
			Integer ret = sectMappingsOldToNew.get(rupSetIndex).get(origSectIndex);
			Preconditions.checkNotNull(ret, "No merged section mapping for rupSet=%s, section=%s", rupSetIndex, origSectIndex);
			return ret;
		}

		public int getNewRuptureIndex(int rupSetIndex, int origRuptureIndex) {
			Integer ret = rupMappingsOldToNew.get(rupSetIndex).get(origRuptureIndex);
			Preconditions.checkNotNull(ret, "No merged rupture mapping for rupSet=%s, rupture=%s", rupSetIndex, origRuptureIndex);
			return ret;
		}

		@Override
		public String getFileName() {
			return "merged_rup_set_mappings.json";
		}
		@Override
		public String getName() {
			return "Merged RuptureSet Mappings";
		}
		@Override
		public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
			out.beginObject();

			out.name("sectMappingsOldToNew");
			writeIntMapListJSON(out, sectMappingsOldToNew);

			out.name("rupMappingsOldToNew");
			writeIntMapListJSON(out, rupMappingsOldToNew);

			out.endObject();
		}
		@Override
		public void initFromJSON(JsonReader in, Gson gson) throws IOException {
			in.beginObject();

			while (in.hasNext()) {
				switch (in.nextName()) {
				case "sectMappingsOldToNew":
					sectMappingsOldToNew = readIntMapListJSON(in);
					break;
				case "rupMappingsOldToNew":
					rupMappingsOldToNew = readIntMapListJSON(in);
					break;

				default:
					in.skipValue();
					break;
				}
			}
			in.endObject();
			Preconditions.checkNotNull(sectMappingsOldToNew);
			Preconditions.checkNotNull(rupMappingsOldToNew);
			Preconditions.checkState(sectMappingsOldToNew.size() == rupMappingsOldToNew.size());
		}

		private static void writeIntMapListJSON(JsonWriter out, List<Map<Integer, Integer>> list) throws IOException {
			out.beginArray();
			for (Map<Integer, Integer> map : list) {
				out.beginArray();
				for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
					out.beginArray();
					out.value(entry.getKey().intValue());
					out.value(entry.getValue().intValue());
					out.endArray();
				}
				out.endArray();
			}
			out.endArray();
		}

		private static List<Map<Integer, Integer>> readIntMapListJSON(JsonReader in) throws IOException {
			List<Map<Integer, Integer>> list = new ArrayList<>();
			in.beginArray();
			while (in.hasNext()) {
				Map<Integer, Integer> map = new HashMap<>();
				in.beginArray();
				while (in.hasNext()) {
					in.beginArray();
					map.put(in.nextInt(), in.nextInt());
					in.endArray();
				}
				in.endArray();
				list.add(map);
			}
			in.endArray();
			return list;
		}
	}

}
