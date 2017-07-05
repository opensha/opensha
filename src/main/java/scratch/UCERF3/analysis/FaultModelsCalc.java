package scratch.UCERF3.analysis;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;

import scratch.UCERF3.enumTreeBranches.FaultModels;

public class FaultModelsCalc {
	
	
	/**
	 * This writes out sections names for Morgan's named-faults data file,
	 * which she uses in her multi-fault rupture statistics.
	 * 
	 * @return
	 */
	public static void writeSectionsForEachNamedFault(FaultModels fm) {
		Map<Integer, List<Integer>> namedMap = fm.getNamedFaultsMap();
		ArrayList<FaultSectionPrefData> sects = fm.fetchFaultSections();

		HashMap<Integer,String> idNameMap = new HashMap<Integer,String>();

		for(FaultSectionPrefData data:sects) {
			idNameMap.put(data.getSectionId(), data.getName());
		}
		
		for(Integer key:namedMap.keySet()) {
			if(namedMap.get(key).size()>1) {
				System.out.print(key);
				for(Integer id : namedMap.get(key))
					System.out.print("\t"+idNameMap.get(id));
				System.out.print("\n");			
			}
		}
	}

	
	/**
	 * This writes the subsections names associated with each subsection
	 * included in a named fault (as defined by the associated file:
	 * data/FaultModels/FM?_?FaultsByNameAlt txt).
	 * 
	 * Note that this lists "null" for the combined stepovers on the San
	 * Jacinto and Elsinore faults for FM 2.1 and 2.2 because those fault
	 * models used the overlapping stepovers (which were swaped out in the
	 * UCERF2 code for floating ruptures, and are swapped out for the DM 2.1
	 * and 2.2 used in the grand inversion).
	 * 
	 * @return
	 */
	public static void writeSectionsForEachNamedFaultAlt(FaultModels fm) {
		Map<String, List<Integer>> namedMap = fm.getNamedFaultsMapAlt();
		ArrayList<FaultSectionPrefData> sects = fm.fetchFaultSections();

		HashMap<Integer,String> idNameMap = new HashMap<Integer,String>();

		for(FaultSectionPrefData data:sects) {
			idNameMap.put(data.getSectionId(), data.getName());
		}
		
		for(String faultName:namedMap.keySet()) {
				System.out.println(faultName+" Sections");
				for(Integer id : namedMap.get(faultName))
					System.out.println("\t"+idNameMap.get(id));
				System.out.print("\n");			
		}
	}
	
	
	/**
	 * 
	 * @param fm
	 */
	public static void writeSectionsNamesAndSomeAttributes(FaultModels fm, boolean includeTrace) {
		ArrayList<FaultSectionPrefData> sects = fm.fetchFaultSections();
		for(FaultSectionPrefData data : fm.fetchFaultSections()) {
			System.out.print(data.getName()+"\t"+(float)data.getOrigDownDipWidth()+"\t"+(float)data.getReducedDownDipWidth()+
					"\t"+(float)data.getFaultTrace().getTraceLength()+"\t"+(float)data.getAseismicSlipFactor()+"\t"+
					data.getAveLowerDepth()+"\t"+data.getOrigAveUpperDepth());
			if(includeTrace) {
				FaultTrace trace = data.getFaultTrace();
				System.out.print("\t"+trace.size());
				for(int l=0; l<trace.size();l++) {
					System.out.print("\t"+(float)trace.get(l).getLatitude()+"\t"+ (float)trace.get(l).getLongitude());
				}
				System.out.print("\n");
			}
			else
				System.out.print("\n");
				
		}
	}

	
	/**
	 * File is written to: dev/scratch/UCERF3/data/scratch/FaultSectionDataForSuplTable.txt
	 * @param fm
	 */
	public static void writeSectionDataForSuppleTable() {
		HashMap<Integer,String> nameList = new HashMap<Integer,String>();
		ArrayList<String> lineList = new ArrayList<String>();
		HashMap<Integer,Boolean> inFM3pt1 = new HashMap<Integer,Boolean>();
		HashMap<Integer,Boolean> inFM3pt2 = new HashMap<Integer,Boolean>();

		ArrayList<FaultSectionPrefData> fm1_data = FaultModels.FM3_1.fetchFaultSections();
		ArrayList<FaultSectionPrefData> fm2_data = FaultModels.FM3_2.fetchFaultSections();

		for(FaultSectionPrefData data:fm1_data) {
			nameList.put(data.getSectionId(),data.getName());
			inFM3pt1.put(data.getSectionId(),true);
			inFM3pt2.put(data.getSectionId(),false);
		}
		for(FaultSectionPrefData data:fm2_data) {
			if(inFM3pt1.keySet().contains(data.getSectionId())) {	// already in list, override default inFM3pt2
				inFM3pt2.put(data.getSectionId(),true);
			}
			else {	// not in list
				inFM3pt1.put(data.getSectionId(),false);
				inFM3pt2.put(data.getSectionId(),true);
			}
		}


		for(FaultSectionPrefData data:fm1_data) {
			String line = data.getName()+"\t"+data.getSectionId()+"\t"+true+"\t"+inFM3pt2.get(data.getSectionId())+"\t"+data.getAveDip()+"\t"+data.getOrigAveUpperDepth()+
					"\t"+data.getAveLowerDepth()+"\t"+data.getTraceLength();
			for(Location loc:data.getFaultTrace()) {
				line += "\t"+loc.getLatitude()+"\t"+loc.getLongitude();
			}
			lineList.add(line);
		}
		for(FaultSectionPrefData data:fm2_data) {
			if(!inFM3pt1.get(data.getSectionId())) { // in not in fault model 3.1
				String line = data.getName()+"\t"+data.getSectionId()+"\t"+false+"\t"+true+"\t"+data.getAveDip()+"\t"+data.getOrigAveUpperDepth()+
						"\t"+data.getAveLowerDepth()+"\t"+data.getTraceLength();
				for(Location loc:data.getFaultTrace()) {
					line += "\t"+loc.getLatitude()+"\t"+loc.getLongitude();
				}
				lineList.add(line);
			}
		}

		File dataFile = new File("dev/scratch/UCERF3/data/scratch/FaultSectionDataForSuplTable.txt");
		try {
			FileWriter fw = new FileWriter(dataFile);
			String header = "Name\tID\tIn FM3.1\tIn FM3.2\tAve Dip\tUpper Seis Depth\tLower Seis Depth\tTrace Length\tTrace Locations (Lat, Lon)";
			fw.write(header+"\n");
			for(String str:lineList) {
				fw.write(str+"\n");
			}
			fw.close ();
		}
		catch (IOException e) {
			System.out.println ("IO exception = " + e );
		}
	}

	
	
	/**
	 * File is written to: dev/scratch/UCERF3/data/scratch/
	 * @param fm
	 */
	public static void writeSectionOutlineForGMT(FaultModels fm, String fileName) {
		ArrayList<FaultSectionPrefData> fm_data = fm.fetchFaultSections();
		ArrayList<String> lineList = new ArrayList<String>();
		for(FaultSectionPrefData data:fm_data) {
			lineList.add("> "+data.getName());
			StirlingGriddedSurface surface = data.getStirlingGriddedSurface(1.0, false, false);
			FaultTrace upperEdge = surface.getRowAsTrace(0);
			for(Location loc:upperEdge) {
				lineList.add((float)loc.getLatitude()+"\t"+(float)loc.getLongitude()+"\t"+(float)loc.getDepth());
			}
			FaultTrace lowerEdge = surface.getRowAsTrace(surface.getNumRows()-1);
			lowerEdge.reverse();
			for(Location loc:lowerEdge) {
				lineList.add((float)loc.getLatitude()+"\t"+(float)loc.getLongitude()+"\t"+(float)loc.getDepth());
			}
		}
		File dataFile = new File("dev/scratch/UCERF3/data/scratch/"+fileName);
		try {
			FileWriter fw = new FileWriter(dataFile);
			for(String str:lineList) {
				fw.write(str+"\n");
			}
			fw.close ();
		}
		catch (IOException e) {
			System.out.println ("IO exception = " + e );
		}
	}

	


	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		writeSectionOutlineForGMT(FaultModels.FM3_1, "fm3pt1_forGMT.txt");
		
//		writeSectionDataForSuppleTable();
		
//		writeSectionsNamesAndSomeAttributes(FaultModels.FM3_1, false);
//		writeSectionsForEachNamedFaultAlt(FaultModels.FM2_1);

	}

}
