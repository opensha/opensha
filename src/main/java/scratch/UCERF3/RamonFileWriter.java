package scratch.UCERF3;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;

import scratch.UCERF3.enumTreeBranches.DeformationModels;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.InversionFaultSystemRupSetFactory;
import scratch.UCERF3.utils.DeformationModelFetcher;
import scratch.UCERF3.utils.UCERF3_DataUtils;

public class RamonFileWriter {
	
	private static void write(File file, FaultModels fm, DeformationModels dm) throws IOException {
		ArrayList<FaultSectionPrefData> subSects =
				new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE).getSubSectionList();
		FileWriter fw = new FileWriter(file);
		
		for (FaultSectionPrefData subSect : subSects) {
			fw.write("#"+subSect.getName()+"\n");
			fw.write(""+subSect.getFaultTrace().size()+"\n");
			for (Location loc : subSect.getFaultTrace()) {
				fw.write(loc.getLatitude()+"\t"+loc.getLongitude()+"\n");
			}
		}
	}
	
	private static void writeTimFile(File file, FaultModels fm, DeformationModels dm) throws IOException {
		ArrayList<FaultSectionPrefData> subSects =
				new DeformationModelFetcher(fm, dm, UCERF3_DataUtils.DEFAULT_SCRATCH_DATA_DIR, InversionFaultSystemRupSetFactory.DEFAULT_ASEIS_VALUE).getSubSectionList();
		CSVFile<String> csv = new CSVFile<String>(true);
		csv.addLine("Fault Name", "Fault ID", "Subsection Index", "Subsection # In Fault", "Lat1", "Lon1", "Lat2", "Lon2");
		
		int cntForParent = 0;
		int parentID = -1;
		for (FaultSectionPrefData subSect : subSects) {
			if (subSect.getParentSectionId() != parentID) {
				parentID = subSect.getParentSectionId();
				cntForParent = 0;
			}
			FaultTrace trace = subSect.getFaultTrace();
			Location loc1 = trace.get(0);
			Location loc2 = trace.get(trace.size()-1);
			csv.addLine(subSect.getParentSectionName(), subSect.getParentSectionId()+"",
					subSect.getSectionId()+"", cntForParent+"", loc1.getLatitude()+"",
					loc1.getLongitude()+"", loc2.getLatitude()+"", loc2.getLongitude()+"");
			cntForParent++;
		}
		csv.writeToFile(file);
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		write(new File("/tmp/fm3_1_for_ramon.txt"), FaultModels.FM3_1, DeformationModels.GEOLOGIC);
		write(new File("/tmp/fm3_2_for_ramon.txt"), FaultModels.FM3_2, DeformationModels.GEOLOGIC);
		writeTimFile(new File("/tmp/fm3_1_for_tim.csv"), FaultModels.FM3_1, DeformationModels.GEOLOGIC);
		writeTimFile(new File("/tmp/fm3_2_for_tim.csv"), FaultModels.FM3_1, DeformationModels.GEOLOGIC);
	}

}
