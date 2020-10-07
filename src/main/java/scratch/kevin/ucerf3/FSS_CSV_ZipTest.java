package scratch.kevin.ucerf3;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.utils.FaultSystemIO;

public class FSS_CSV_ZipTest {

	public static void main(String[] args) throws IOException, DocumentException {
		FaultSystemSolution fss = FaultSystemIO.loadSol(new File(
				"/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_ucerf3.zip"));
		
		CSVFile<String> metaCSV = new CSVFile<>(true);
		CSVFile<String> sectsCSV = new CSVFile<>(false);
		
		metaCSV.addLine("Index", "Magnitude", "Average Rake (degrees)", "Length (m)",
				"Area (m^2)", "Annual Rate");
		sectsCSV.addLine("Index", "# Subsections", "Subsection 1", "Subsection 2", "...", "Subsection N");
		
		FaultSystemRupSet rupSet = fss.getRupSet();
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			List<String> line = new ArrayList<>();
			line.add(r+"");
			line.add(rupSet.getMagForRup(r)+"");
			line.add(rupSet.getAveRakeForRup(r)+"");
			line.add(rupSet.getLengthForRup(r)+"");
			line.add(rupSet.getAreaForRup(r)+"");
			line.add(fss.getRateForRup(r)+"");
			metaCSV.addLine(line);
			List<Integer> sects = rupSet.getSectionsIndicesForRup(r);
			line = new ArrayList<>();
			line.add(sects.size()+"");
			line.add(r+"");
			for (int s : sects)
				line.add(s+"");
			sectsCSV.addLine(line);
		}
		metaCSV.writeToFile(new File("/tmp/fss_csv/test_meta.csv"));
		sectsCSV.writeToFile(new File("/tmp/fss_csv/test_sects.csv"));
	}

}
