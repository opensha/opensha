/**
 * 
 */
package scratch.UCERF3.utils.FindEquivUCERF2_Ruptures;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;

import org.apache.commons.lang3.StringUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.utils.DeformationModelFetcher;

/**
 * @author field
 *
 */
public class FileMakingStuff {
	
	
	public static void mkNewFilesBySubstitutingNames() {
		
		// read the name changes file
		File nameChangeFile = new File("dev/scratch/UCERF3/data/FindEquivUCERF2_Ruptures/FM2to3_1_sectionNameChanges.txt");
		Hashtable<String,String> namesMap = new Hashtable<String,String>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(nameChangeFile.getPath()));
			int l=-1;
			String line;
			while ((line = reader.readLine()) != null) {
				l+=1;
				String[] st = StringUtils.split(line,"\t");
				String oldName = st[0];
				String newName = st[1];
				if(newName.equals("MULTIPLE:")) {
					newName=st[2];
					for(int i=3;i<st.length;i++) {
						newName+="\t"+st[i];
					}
				}
				namesMap.put(oldName, newName);

			}
		} catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		
		// now create the old and new files
		File prevFile = new File("dev/scratch/UCERF3/data/FindEquivUCERF2_Ruptures/FM2_SectionsForUCERF2_Sources.txt");
		File newFile = new File("dev/scratch/UCERF3/data/FindEquivUCERF2_Ruptures/FM3_1_SectionsForUCERF2_Sources.txt");

		System.out.println("Reading file: "+prevFile.getPath());
		try {
			BufferedReader reader = new BufferedReader(new FileReader(prevFile.getPath()));
			FileWriter fw = new FileWriter(newFile);
			
			int l=-1;
			String line, newLine="";
			while ((line = reader.readLine()) != null) {
				l+=1;
				String[] st = StringUtils.split(line,"\t");
				int srcIndex = Integer.valueOf(st[0]);  // note that this is the index for  ModMeanUCERF2, not ModMeanUCERF2_FM2pt1
				if(srcIndex != l)
					throw new RuntimeException("problem with source index");
				String srcName = st[1];
				int faultModelForSource = Integer.valueOf(st[2]);
				newLine = srcIndex+"\t"+srcName+"\t"+faultModelForSource;
				for(int i=3;i<st.length;i++) {
					String sectName = st[i];
					if(namesMap.containsKey(sectName)) {	// name has changed
						if(!namesMap.get(sectName).equals("COMBINED"))	// skip if section name is "COMBINED"
							newLine += "\t"+namesMap.get(sectName);
					}
					else {	// no name change
						newLine += "\t"+sectName;
					}
				}
//				if(addLine)
				fw.write(newLine+"\n");
			}
			
			fw.close();

		} catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		
		
		
		
		
		// read the name changes file
		nameChangeFile = new File("dev/scratch/UCERF3/data/FindEquivUCERF2_Ruptures/FM2to3_2_sectionNameChanges.txt");
		namesMap = new Hashtable<String,String>();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(nameChangeFile.getPath()));
			int l=-1;
			String line;
			while ((line = reader.readLine()) != null) {
				l+=1;
				String[] st = StringUtils.split(line,"\t");
				String oldName = st[0];
				String newName = st[1];
				if(newName.equals("MULTIPLE:")) {
					newName=st[2];
					for(int i=3;i<st.length;i++) {
						newName+="\t"+st[i];
					}
				}
				namesMap.put(oldName, newName);

			}
		} catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}
		
		// now create the old and new files
		prevFile = new File("dev/scratch/UCERF3/data/FindEquivUCERF2_Ruptures/FM2_SectionsForUCERF2_Sources.txt");
		newFile = new File("dev/scratch/UCERF3/data/FindEquivUCERF2_Ruptures/FM3_2_SectionsForUCERF2_Sources.txt");

		System.out.println("Reading file: "+prevFile.getPath());
		try {
			BufferedReader reader = new BufferedReader(new FileReader(prevFile.getPath()));
			FileWriter fw = new FileWriter(newFile);
			
			int l=-1;
			String line, newLine="";
			while ((line = reader.readLine()) != null) {
				l+=1;
				String[] st = StringUtils.split(line,"\t");
				int srcIndex = Integer.valueOf(st[0]);  // note that this is the index for  ModMeanUCERF2, not ModMeanUCERF2_FM2pt1
				if(srcIndex != l)
					throw new RuntimeException("problem with source index");
				String srcName = st[1];
				int faultModelForSource = Integer.valueOf(st[2]);
				newLine = srcIndex+"\t"+srcName+"\t"+faultModelForSource;
				for(int i=3;i<st.length;i++) {
					String sectName = st[i];
					if(namesMap.containsKey(sectName)) {	// name has changed
						if(!namesMap.get(sectName).equals("COMBINED"))	// skip if section name is "COMBINED"
							newLine += "\t"+namesMap.get(sectName);
					}
					else {	// no name change
						newLine += "\t"+sectName;
					}
				}
//				if(addLine)
				fw.write(newLine+"\n");
			}
			
			fw.close();

		} catch (Exception e) {
			ExceptionUtils.throwAsRuntimeException(e);
		}


		System.out.println("Done creating files");
	}
	
	
	public static void writeAllFM3_sectNames() {
		
		File dataFile = new File("dev/scratch/UCERF3/utils/FindEquivUCERF2_Ruptures/scratchFiles/AllFM3_sectNames.txt");
		ArrayList<String> names = new ArrayList<String>();
		
		ArrayList<FaultSection> faultMod3_1_sects =  FaultModels.FM3_1.fetchFaultSections();
		ArrayList<FaultSection> faultMod3_2_sects =  FaultModels.FM3_2.fetchFaultSections();
		
		for(FaultSection data: faultMod3_1_sects)
			if(!names.contains(data.getSectionName()))
				names.add(data.getSectionName());
		
		for(FaultSection data: faultMod3_2_sects)
			if(!names.contains(data.getSectionName()))
				names.add(data.getSectionName());
		
		try {
			FileWriter fw = new FileWriter(dataFile);
			for(String name:names) {
				fw.write(name+"\n");
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
		
//		FileMakingStuff.mkNewFilesBySubstitutingNames();
		
		FileMakingStuff.writeAllFM3_sectNames();

	}

}
