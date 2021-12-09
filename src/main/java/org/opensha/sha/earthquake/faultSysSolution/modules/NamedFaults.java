package org.opensha.sha.earthquake.faultSysSolution.modules;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opensha.commons.util.modules.SubModule;
import org.opensha.commons.util.modules.AverageableModule.ConstantAverageable;
import org.opensha.commons.util.modules.helpers.JSON_TypeAdapterBackedModule;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * This class keeps track of higher level mappings between parent fault sections that are part of the same named fault,
 * e.g., multiple sections of the San Andreas in California. This is most useful for aggregate plots for individual
 * faults.
 * 
 * @author kevin
 *
 */
public class NamedFaults implements SubModule<FaultSystemRupSet>, BranchAverageableModule<NamedFaults>,
ConstantAverageable<NamedFaults>, JSON_TypeAdapterBackedModule<Map<String, List<Integer>>> {
	
	private FaultSystemRupSet rupSet;
	private Map<String, List<Integer>> namedFaults;
	private Map<Integer, String> faultNames;
	
	@SuppressWarnings("unused") // for deserialization
	private NamedFaults() {};

	public NamedFaults(FaultSystemRupSet rupSet, Map<String, List<Integer>> namedFaults) {
		this.rupSet = rupSet;
		set(namedFaults);
	}

	@Override
	public String getName() {
		return "Named Faults";
	}

	@Override
	public void setParent(FaultSystemRupSet parent) throws IllegalStateException {
		this.rupSet = parent;
	}

	@Override
	public FaultSystemRupSet getParent() {
		return rupSet;
	}

	@Override
	public SubModule<FaultSystemRupSet> copy(FaultSystemRupSet newParent) throws IllegalStateException {
		return new NamedFaults(newParent, namedFaults);
	}

	@Override
	public String getFileName() {
		return "named_faults.json";
	}
	
	private static final Type listIntType = TypeToken.getParameterized(List.class, Integer.class).getType();
	private static final Type mapListIntType = TypeToken.getParameterized(Map.class, String.class, listIntType).getType();

	@Override
	public Type getType() {
		return mapListIntType;
	}

	@Override
	public Map<String, List<Integer>> get() {
		return namedFaults;
	}

	@Override
	public void set(Map<String, List<Integer>> value) {
		Preconditions.checkNotNull(value);
		this.namedFaults = value;
		faultNames = new HashMap<>();
		for (String name : namedFaults.keySet()) {
			for (int parentID : namedFaults.get(name)) {
				Preconditions.checkState(!faultNames.containsKey(parentID),
						"Parent section ID=%s is mapped to multiple named faults");
				faultNames.put(parentID, name);
			}
		}
	}

	@Override
	public void registerTypeAdapters(GsonBuilder builder) {}
	
	/**
	 * @return set of all fault names
	 */
	public Set<String> getFaultNames() {
		return namedFaults.keySet();
	}
	
	/**
	 * @param name
	 * @return list of parent section IDs for the given fault name, or null if none exist
	 */
	public List<Integer> getParentIDsForFault(String name) {
		return namedFaults.get(name);
	}
	
	public List<FaultSection> getSectsForFault(String name) {
		HashSet<Integer> parentIDs = new HashSet<>(getParentIDsForFault(name));
		List<FaultSection> sects = new ArrayList<>();
		for (FaultSection sect : rupSet.getFaultSectionDataList())
			if (parentIDs.contains(sect.getParentSectionId()))
				sects.add(sect);
		return sects;
	}
	
	/**
	 * @param parentID
	 * @return name fault to which this parent belongs, or null if none exist
	 */
	public String getFaultName(int parentID) {
		return faultNames.get(parentID);
	}
	
	/**
	 * This attempts to remove redundancies from section names where they share a common prefix from the section name,
	 * useful for plotting.
	 * 
	 * @param faultName
	 * @param sectName
	 * @return name with rudundencies eliminated, if possible, otherwise the original section name is returned
	 */
	public static String stripFaultNameFromSect(String faultName, String sectName) {
		// clean up inputs a bit
		faultName = faultName.trim();
		while (faultName.contains("  "))
			faultName = faultName.replace("  ", " ");
		sectName = sectName.trim();
		while (sectName.contains("  "))
			sectName = sectName.replace("  ", " ");
		
		if (sectName.startsWith(faultName))
			// simple case
			return sectName.substring(faultName.length()).replace("(", "").replace(")", "").trim();
		// lets see if the fault has something in brackets or parenthesis at the end
		if (faultName.contains("("))
			faultName = faultName.substring(0, faultName.indexOf("("));
		if (faultName.contains("["))
			faultName = faultName.substring(0, faultName.indexOf("["));
		faultName = faultName.trim();
		
		// it's now a match
		if (sectName.startsWith(faultName))
			return sectName.substring(faultName.length()).replace("(", "").replace(")", "").trim();
		
		// see if there are any common words at the beginning
		if (sectName.contains(" ")) {
			String[] faultWords = faultName.contains(" ") ? faultName.split(" ") : new String[] { faultName };
			String[] sectWords = sectName.split(" ");
			int numToSkip = 0;
			
			for (int i=0; i<faultWords.length && i<sectWords.length; i++) {
				if (faultWords[i].equalsIgnoreCase(sectWords[i]))
					numToSkip++;
				else
					break;
			}
			if (numToSkip > 0)
				return Joiner.on(" ").join(Arrays.copyOfRange(sectWords, numToSkip, sectWords.length));
		}
		
		// no luck
		return sectName;
	}

}
