package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.util.modules.helpers.JSON_BackedModule;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * This class gives mappings between original and split rupture set that breaks sections and ruptures into multiple instances.
 * This is used by {@link SplittableRuptureModule} instances to build module splits.
 * 
 * @author kevin
 *
 */
public class RuptureSetSplitMappings implements JSON_BackedModule {

	private Map<Integer, Integer> sectIDs_newToOld;
	private Map<Integer, List<Integer>> sectIDs_oldToNew;
	private Map<Integer, Double> sectSplitWeights;
	private Map<Integer, Integer> rupIDs_newToOld;
	private Map<Integer, List<Integer>> rupIDs_oldToNew;
	private Map<Integer, Double> rupSplitWeights;
	
	@SuppressWarnings("unused") // deserialization
	private RuptureSetSplitMappings() {}

	public RuptureSetSplitMappings(Map<Integer, List<Integer>> sectIDs_oldToNew,
			Map<Integer, List<Integer>> rupIDs_oldToNew) {
		this(sectIDs_oldToNew, null, rupIDs_oldToNew, null);
	}

	public RuptureSetSplitMappings(Map<Integer, List<Integer>> sectIDs_oldToNew,
			Map<Integer, Double> sectSplitWeights,
			Map<Integer, List<Integer>> rupIDs_oldToNew,
			Map<Integer, Double> rupSplitWeights) {
		this.sectIDs_oldToNew = sectIDs_oldToNew;
		this.sectIDs_newToOld = inverse(sectIDs_oldToNew);
		this.sectSplitWeights = sectSplitWeights;
		this.rupIDs_oldToNew = rupIDs_oldToNew;
		this.rupIDs_newToOld = inverse(rupIDs_oldToNew);
		this.rupSplitWeights = rupSplitWeights;
	}
	
	private static Map<Integer, Integer> inverse(Map<Integer, List<Integer>> oldToNew) {
		Map<Integer, Integer> ret = new HashMap<>();
		for (Integer oldID : oldToNew.keySet()) {
			for (Integer newID : oldToNew.get(oldID)) {
				Preconditions.checkState(!ret.containsKey(newID));
				ret.put(newID, oldID);
			}
		}
		return ret;
	}
	
	
	/**
	 * @param origSectID
	 * @return the new ID in this subset for the given original section ID
	 */
	public List<Integer> getNewSectIDs(int origSectID) {
		return sectIDs_oldToNew.get(origSectID);
	}
	
	/**
	 * @param newSectID
	 * @return the splitting weight for the given new section ID
	 */
	public Double getNewSectWeight(int newSectID) {
		return getNewWeights(newSectID, sectIDs_oldToNew, sectIDs_newToOld, sectSplitWeights);
	}
	

	private static Double getNewWeights(int newID, Map<Integer, List<Integer>> oldToNew,
			Map<Integer, Integer> newToOld, Map<Integer, Double> weightsMap) {
		if (weightsMap != null) {
			Double weight = weightsMap.get(newID);
			if (weight == null) {
				int origID = newToOld.get(newID);
				Preconditions.checkState(oldToNew.get(origID).size() == 1);
				return 1d;
			}
			return weight;
		}
		int origID = newToOld.get(newID);
		List<Integer> allNewIDs = oldToNew.get(origID);
		if (allNewIDs.size() == 1)
			return 1d;
		return 1d/allNewIDs.size();
	}
	
	/**
	 * @param newSectID
	 * @return the original ID of this new subset section ID
	 */
	public int getOrigSectID(int newSectID) {
		return sectIDs_newToOld.get(newSectID);
	}
	
	/**
	 * @param origRupID
	 * @return the new ID in this subset for the given original rupture ID
	 */
	public List<Integer> getNewRupIDs(int origRupID) {
		return rupIDs_oldToNew.get(origRupID);
	}
	
	/**
	 * @param newRupID
	 * @return the splitting weight for the given new rupture ID
	 */
	public Double getNewRupWeight(int newRupID) {
		return getNewWeights(newRupID, rupIDs_oldToNew, rupIDs_newToOld, rupSplitWeights);
	}
	
	/**
	 * @param newRupID
	 * @return the original ID of this new subset rupture ID
	 */
	public int getOrigRupID(int newRupID) {
		return rupIDs_newToOld.get(newRupID);
	}

	@Override
	public String getName() {
		return "Rupture Set Split Mappings";
	}

	@Override
	public String getFileName() {
		return "rupture_set_split_mappings.json";
	}

	@Override
	public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
		out.beginObject();
		
		out.name("sectIDs_oldToNew");
		writeIntListMapJSON(out, sectIDs_oldToNew);
		
		if (sectSplitWeights != null) {
			out.name("sectSplitWeights");
			writeDoubleMapJSON(out, sectSplitWeights);
		}
		
		out.name("rupIDs_oldToNew");
		writeIntListMapJSON(out, rupIDs_oldToNew);
		
		if (rupSplitWeights != null) {
			out.name("rupSplitWeights");
			writeDoubleMapJSON(out, rupSplitWeights);
		}
		
		out.endObject();
	}
	
	private static void writeIntListMapJSON(JsonWriter out, Map<Integer, List<Integer>> map) throws IOException {
		out.beginArray();
		
		for (Integer key : map.keySet()) {
			out.beginArray();
			out.value(key.intValue());
			for (int val : map.get(key))
				out.value(val);
			out.endArray();
		}
		
		out.endArray();
	}
	
	private static void writeDoubleMapJSON(JsonWriter out, Map<Integer, Double> map) throws IOException {
		out.beginArray();
		
		for (Integer key : map.keySet()) {
			out.beginArray();
			out.value(key.intValue());
			out.value(map.get(key).doubleValue());
			out.endArray();
		}
		
		out.endArray();
	}
	
	private static Map<Integer, List<Integer>> readIntListMapJSON(JsonReader in) throws IOException {
		Map<Integer, List<Integer>> map = new HashMap<>();
		in.beginArray();
		
		while (in.hasNext()) {
			in.beginArray();
			int key = in.nextInt();
			List<Integer> vals = new ArrayList<>();
			while (in.hasNext())
				vals.add(in.nextInt());
			Preconditions.checkState(!vals.isEmpty());
			map.put(key, vals);
			in.endArray();
		}
		
		in.endArray();
		return map;
	}
	
	private static Map<Integer, Double> readDoubleMapJSON(JsonReader in) throws IOException {
		Map<Integer, Double> map = new HashMap<>();
		in.beginArray();
		
		while (in.hasNext()) {
			in.beginArray();
			int key = in.nextInt();
			map.put(key, in.nextDouble());
			in.endArray();
		}
		
		in.endArray();
		return map;
	}

	@Override
	public void initFromJSON(JsonReader in, Gson gson) throws IOException {
		in.beginObject();
		
		while (in.hasNext()) {
			switch (in.nextName()) {
			case "sectIDs_oldToNew":
				sectIDs_oldToNew = readIntListMapJSON(in);
				break;
			case "sectSplitWeights":
				sectSplitWeights = readDoubleMapJSON(in);
				break;
			case "rupIDs_oldToNew":
				rupIDs_oldToNew = readIntListMapJSON(in);
				break;
			case "rupSplitWeights":
				rupSplitWeights = readDoubleMapJSON(in);
				break;

			default:
				in.skipValue();
				break;
			}
		}
		Preconditions.checkNotNull(sectIDs_newToOld);
		Preconditions.checkNotNull(rupIDs_newToOld);
		
		in.endObject();
	}

}
