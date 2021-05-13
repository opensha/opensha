package scratch.UCERF3.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.refFaultParamDb.dao.db.FaultSectionVer2_DB_DAO;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.refFaultParamDb.vo.FaultSectionSummary;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class UCERF2_A_FaultMapper {
	
	public static boolean wasUCERF2_TypeAFault(int sectionID) {
		return getTypeAFaults().contains(sectionID);
	}
	
	private static final String DIR = "FaultModels";
	private static final String A_FAULT_FILE_NAME = "a_faults.txt";
	private static HashSet<Integer> typeAFaults;
	
	private static HashSet<Integer> getTypeAFaults() {
		if (typeAFaults == null) {
			try {
				typeAFaults = new HashSet<Integer>();
				BufferedReader br = new BufferedReader(UCERF3_DataUtils.getReader(DIR, A_FAULT_FILE_NAME));
				String line = br.readLine();
				while (line != null) {
					line = line.trim();
					if (!line.startsWith("#") && !line.isEmpty()) {
						Integer id = Integer.parseInt(line.split("\t")[0]);
						typeAFaults.add(id);
					}
					
					line = br.readLine();
				}
			} catch (IOException e) {
				typeAFaults = null;
				ExceptionUtils.throwAsRuntimeException(e);
			}
		}
		return typeAFaults;
	}
	
	private static void writeDataFile(File outputFile, File segFile,
			File fm3_1_nameChangeFile, File fm3_2_nameChangeFile) throws IOException {
		Map<String, String> fm3_1_changes = loadNameChanges(fm3_1_nameChangeFile);
		Map<String, String> fm3_2_changes = loadNameChanges(fm3_2_nameChangeFile);
		
		Map<Integer, String> aFaults = Maps.newHashMap();
		
		List<String> segLines = FileUtils.readLines(segFile);
		
		for (FaultModels fm : FaultModels.values()) {
			Map<Integer, FaultSection> sects = fm.fetchFaultSectionsMap();
			
			Map<String, Integer> sectsByName = Maps.newHashMap();
			for (FaultSection sect : sects.values())
				sectsByName.put(sect.getSectionName().trim(), sect.getSectionId());
			
			for (String line : segLines) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#") || line.startsWith("-"))
					continue;
				line = line.substring(line.indexOf(":")+1);
				String[] names;
				if (line.contains(";")) {
					names = line.split(";");
				} else {
					names = new String[1];
					names[0] = line;
				}
				for (String name : names) {
					name = name.trim();
					if (fm == FaultModels.FM3_1 && fm3_1_changes.containsKey(name))
						name = fm3_1_changes.get(name);
					if (fm == FaultModels.FM3_2 && fm3_2_changes.containsKey(name))
						name = fm3_2_changes.get(name);
					
					Integer sectID = sectsByName.get(name);
					if (sectID == null) {
						System.out.println("WARNING: sect not found with name: "+name+", FM: "+fm);
						int min = Integer.MAX_VALUE;
						String closest = null;
						for (String matchName : sectsByName.keySet()) {
							int dist = StringUtils.getLevenshteinDistance(name, matchName);
							if (dist < min) {
								min = dist;
								closest = matchName;
							}
						}
						System.out.println("Possible match: "+closest);
						continue;
					}
//					Preconditions.checkNotNull(sect, "Sect not found: "+name);
					
					if (!aFaults.containsKey(sectID))
						aFaults.put(sectID, name);
				}
			}
		}
		
		FileWriter fw = new FileWriter(outputFile);
		
		fw.write("#ID\tName\n");
		
		List<Integer> sectIDs = Lists.newArrayList(aFaults.keySet());
		Collections.sort(sectIDs);
		
		for (Integer sectID : sectIDs) {
			String name = aFaults.get(sectID);
			
			fw.write(sectID+"\t"+name+"\n");
		}
		
		fw.close();
	}
	
	private static Map<String, String> loadNameChanges(File file) throws IOException {
		Map<String, String> changes = Maps.newHashMap();
		
		for (String line : FileUtils.readLines(file)) {
			String[] names = line.trim().split("\t");
			changes.put(names[0].trim(), names[1].trim());
		}
		
		return changes;
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		File dir = new File("/tmp");
		
//		writeDataFile(new File(new File(UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR.getParentFile(), DIR),
//				"a_faults.txt"), new File(dir, "SegmentModels.txt"),
//				new File(dir, "FM2to3_1_sectionNameChanges.txt"),
//				new File(dir, "FM2to3_2_sectionNameChanges.txt"));
		
		// now test
//		for (FaultModels fm : FaultModels.values()) {
//			DeformationModels dm = DeformationModels.forFaultModel(fm).get(0);
//			
//			for (FaultSectionPrefData sect : new DeformationModelFetcher(
//					fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, 0.1).getParentSectionList())
//				if (wasUCERF2_TypeAFault(sect.getSectionId()))
//					System.out.println("A Fault: "+sect.getSectionId()+". "+sect.getSectionName());
//		}
		
		DeformationModelFetcher fetcher = new DeformationModelFetcher(FaultModels.FM2_1, DeformationModels.UCERF2_ALL, null, 0.0);
				
		for (FaultSection sect : fetcher.getParentSectionList()) {
//			if (wasUCERF2_TypeAFault(sect.getSectionId()))
//				System.out.println("A Fault: "+sect.getSectionId()+". "+sect.getSectionName());
			System.out.println(sect.getSectionId()+"\t"+sect.getSectionName()+"\t"+wasUCERF2_TypeAFault(sect.getSectionId()));
		}
	}

}
