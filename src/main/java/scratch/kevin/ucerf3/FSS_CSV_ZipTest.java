package scratch.kevin.ucerf3;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.dom4j.DocumentException;
import org.opensha.commons.data.CSVFile;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.utils.FaultSystemIO;

public class FSS_CSV_ZipTest {

	public static void main(String[] args) throws IOException, DocumentException {
		FaultSystemSolution fss = FaultSystemIO.loadSol(new File(
				"/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_ucerf3.zip"));

		CSVFile<String> rupMetaCSV = new CSVFile<>(true);
		CSVFile<String> rupRatesCSV = new CSVFile<>(true);
		CSVFile<String> rupSectsCSV = new CSVFile<>(false);
		CSVFile<String> subSeismoMFDCSV = fss.getSubSeismoOnFaultMFD_List() == null ? null : new CSVFile<>(true);

		rupMetaCSV.addLine("Rupture Index", "Magnitude", "Average Rake (degrees)", "Length (m)", "Area (m^2)");
		rupRatesCSV.addLine("Rupture Index", "Annual Rate");
		rupSectsCSV.addLine("Rupture Index", "# Subsections", "Subsection 1", "Subsection 2", "...", "Subsection N");

		CSVFile<String> sectsMetaCSV = new CSVFile<>(true);
		
		sectsMetaCSV.addLine("Section Index", "Area (m^2)", "Target Slip Rate (m/yr)", "Slip Rate Std. Dev. (m/yr)");
		if (subSeismoMFDCSV != null) {
			List<String> line = new ArrayList<>();
			line.add("Section Index");
			IncrementalMagFreqDist xVals = fss.getSubSeismoOnFaultMFD_List().get(0);
			for (int i=0; i<xVals.size(); i++)
				line.add(xVals.getX(i)+"");
			subSeismoMFDCSV.addLine(line);
		}
		
		FaultSystemRupSet rupSet = fss.getRupSet();
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			List<String> line = new ArrayList<>();
			line.add(r+"");
			line.add(rupSet.getMagForRup(r)+"");
			line.add(rupSet.getAveRakeForRup(r)+"");
			line.add(rupSet.getLengthForRup(r)+"");
			line.add(rupSet.getAreaForRup(r)+"");
//			line.add(fss.getRateForRup(r)+"");
			rupMetaCSV.addLine(line);
			List<Integer> sects = rupSet.getSectionsIndicesForRup(r);
			line = new ArrayList<>();
			line.add(r+"");
			line.add(sects.size()+"");
			for (int s : sects)
				line.add(s+"");
			rupSectsCSV.addLine(line);
			rupRatesCSV.addLine(r+"", fss.getRateForRup(r)+"");
		}
		
		boolean hasSlipSDs = rupSet.getSlipRateStdDevForAllSections() != null;
		for (int s=0; s<rupSet.getNumSections(); s++) {
			List<String> line = new ArrayList<>();
			line.add(s+"");
			line.add(rupSet.getAreaForSection(s)+"");
			line.add(rupSet.getSlipRateForSection(s)+"");
			if (hasSlipSDs)
				line.add(rupSet.getSlipRateStdDevForSection(s)+"");
			else
				line.add("");
			sectsMetaCSV.addLine(line);
			if (subSeismoMFDCSV != null) {
				line = new ArrayList<>();
				line.add(s+"");
				IncrementalMagFreqDist mfd = fss.getSubSeismoOnFaultMFD_List().get(s);
				Preconditions.checkState(mfd.size() == subSeismoMFDCSV.getNumCols()-1,
						"MFD for section %s has %s points, expected %s", s, mfd.size(), subSeismoMFDCSV.getNumCols()-1);
				for (int i=0; i<mfd.size(); i++)
					line.add(mfd.getY(i)+"");
				subSeismoMFDCSV.addLine(line);
			}
		}
		
		File destDir = new File("/tmp/fss_csv/csvs_unzipped");
		Preconditions.checkState(destDir.exists() || destDir.mkdirs());
		rupMetaCSV.writeToFile(new File(destDir, "rupture_metadata.csv"));
		rupSectsCSV.writeToFile(new File(destDir, "rupture_sects.csv"));
		rupRatesCSV.writeToFile(new File(destDir, "rupture_rates.csv"));
		sectsMetaCSV.writeToFile(new File(destDir, "section_metadata.csv"));
		if (subSeismoMFDCSV != null)
			subSeismoMFDCSV.writeToFile(new File(destDir, "section_sub_seismo_mfds.csv"));
	}

}
