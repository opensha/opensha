package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.opensha.commons.util.modules.helpers.JSON_BackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * This class gives mappings between original and subset rupture and section IDs. This is created by
 * {@link FaultSystemRupSet#getForSectionSubSet(java.util.Collection)}, and used by {@link SplittableRuptureModule}
 * instances to build module subsets.
 * 
 * @author kevin
 *
 */
public class RuptureSubSetMappings implements JSON_BackedModule {

	private BiMap<Integer, Integer> sectIDs_newToOld;
	private BiMap<Integer, Integer> sectIDs_oldToNew;
	private BiMap<Integer, Integer> rupIDs_newToOld;
	private BiMap<Integer, Integer> rupIDs_oldToNew;
	private transient FaultSystemRupSet origRupSet;
	
	@SuppressWarnings("unused") // deserialization
	private RuptureSubSetMappings() {}

	public RuptureSubSetMappings(BiMap<Integer, Integer> sectIDs_newToOld,
			BiMap<Integer, Integer> rupIDs_newToOld, FaultSystemRupSet origRupSet) {
		this.sectIDs_newToOld = sectIDs_newToOld;
		this.sectIDs_oldToNew = sectIDs_newToOld.inverse();
		this.rupIDs_newToOld = rupIDs_newToOld;
		this.rupIDs_oldToNew = rupIDs_newToOld.inverse();
		this.origRupSet = origRupSet;
	}
	
	/**
	 *  @return the number of retained section IDs
	 */
	public int getNumRetainedSects() {
		return sectIDs_newToOld.size();
	}
	
	/**
	 * @return the set of original section IDs that have been retained
	 */
	public Set<Integer> getRetainedOrigSectIDs() {
		return sectIDs_newToOld.values();
	}
	
	/**
	 * @param origSectID
	 * @return true if the given section was retained in this subset
	 */
	public boolean isSectRetained(int origSectID) {
		return sectIDs_newToOld.containsValue(origSectID);
	}
	
	/**
	 * @param origSectID
	 * @return the new ID in this subset for the given original section ID
	 */
	public int getNewSectID(int origSectID) {
		return sectIDs_oldToNew.get(origSectID);
	}
	
	/**
	 * @param newSectID
	 * @return the original ID of this new subset section ID
	 */
	public int getOrigSectID(int newSectID) {
		return sectIDs_newToOld.get(newSectID);
	}
	
	/**
	 *  @return the number of retained rupture IDs
	 */
	public int getNumRetainedRuptures() {
		return rupIDs_newToOld.size();
	}
	
	/**
	 * @return the set of original rupture IDs that have been retained
	 */
	public Set<Integer> getRetainedOrigRupIDs() {
		return rupIDs_newToOld.values();
	}
	
	/**
	 * @param origRupID
	 * @return true if the given section was retained in this subset
	 */
	public boolean isRupRetained(int origRupID) {
		return rupIDs_newToOld.containsValue(origRupID);
	}
	
	/**
	 * @param origRupID
	 * @return the new ID in this subset for the given original rupture ID
	 */
	public int getNewRupID(int origRupID) {
		return rupIDs_oldToNew.get(origRupID);
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
		return "Rupture Subset Mappings";
	}

	@Override
	public String getFileName() {
		return "rupture_sub_set_mappings.json";
	}
	
	public FaultSystemRupSet getOrigRupSet() {
		return origRupSet;
	}

	@Override
	public void writeToJSON(JsonWriter out, Gson gson) throws IOException {
		out.beginObject();
		
		out.name("sectIDs_newToOld");
		writeBiMapJSON(out, sectIDs_newToOld);
		
		out.name("rupIDs_newToOld");
		writeBiMapJSON(out, rupIDs_newToOld);
		
		out.endObject();
	}
	
	private static void writeBiMapJSON(JsonWriter out, BiMap<Integer, Integer> map) throws IOException {
		out.beginArray();
		
		for (Integer key : map.keySet()) {
			out.beginArray();
			out.value(key.intValue());
			out.value(map.get(key).intValue());
			out.endArray();
		}
		
		out.endArray();
	}
	
	private static BiMap<Integer, Integer> readBiMapJSON(JsonReader in) throws IOException {
		BiMap<Integer, Integer> map = HashBiMap.create();
		in.beginArray();
		
		while (in.hasNext()) {
			in.beginArray();
			int key = in.nextInt();
			int val = in.nextInt();
			map.put(key, val);
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
			case "sectIDs_newToOld":
				sectIDs_newToOld = readBiMapJSON(in);
				break;
			case "rupIDs_newToOld":
				rupIDs_newToOld = readBiMapJSON(in);
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
