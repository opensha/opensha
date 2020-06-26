package scratch.kevin.ucerf3;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;

import com.google.common.base.Preconditions;

import scratch.UCERF3.CompoundFaultSystemSolution;
import scratch.UCERF3.logicTree.LogicTreeBranch;

public class FullModelCSVWriter {

	public static void main(String[] args) throws ZipException, IOException {
		
		CompoundFaultSystemSolution cfss = CompoundFaultSystemSolution.fromZipFile(new File(
				"/home/kevin/workspace/opensha-ucerf3/src/scratch/UCERF3/data/scratch/"
				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_WITH_IND_RUNS.zip"));
		
		int numRuns = 10;
		
		File outputDir = new File("/home/kevin/OpenSHA/UCERF3/fss_csvs");
		File outputFile = new File(outputDir, "full_model_csvs.zip");
		
		List<String> header = new ArrayList<>();
		header.add("rupID");
		header.add("mag");
		header.add("mean annual rate");
		for (int i=0; i<numRuns; i++)
			header.add("solution "+i+" annaul rate");
		
		ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile)));

		// Set the compression ratio
		out.setLevel(Deflater.DEFAULT_COMPRESSION);
		
		double totWeight = 0d;
		for (LogicTreeBranch branch : cfss.getBranches())
			totWeight += branch.getAprioriBranchWt();
		System.out.println("Total weight: "+totWeight);
		
		CSVFile<String> weightCSV = new CSVFile<>(true);
		List<String> weightHeader = new ArrayList<>();
		weightHeader.add("Index");
		weightHeader.add("Weight");
		weightHeader.add("Prefix");
		LogicTreeBranch branch0 = cfss.getBranches().iterator().next();
		for (int i=0; i<branch0.size(); i++)
			weightHeader.add(branch0.getValue(i).getBranchLevelName());
		weightCSV.addLine(weightHeader);
		
		for (LogicTreeBranch branch : cfss.getBranches()) {
			CSVFile<String> csv = new CSVFile<>(true);
			csv.addLine(header);
			
			double weight = branch.getAprioriBranchWt()/totWeight;
			List<String> weightLine = new ArrayList<>();
			weightLine.add((weightCSV.getNumRows()-1)+"");
			weightLine.add(weight+"");
			weightLine.add(branch.buildFileName());
			for (int i=0; i<branch.size(); i++)
				weightLine.add(branch.getValue(i).getShortName());
			weightCSV.addLine(weightLine);
			
			System.out.println("Loading for "+branch.buildFileName());
			double[] mags = cfss.getMags(branch);
			double[] rates = cfss.getRates(branch);
			List<double[]> subRates = new ArrayList<>();
			for (int i=0; i<numRuns; i++)
				subRates.add(cfss.loadDoubleArray(branch, "rates_"+i+".bin"));
			
			for (int r=0; r<mags.length; r++) {
				List<String> line = new ArrayList<>();
				line.add(r+"");
				line.add(mags[r]+"");
				line.add(rates[r]+"");
				double avgRate = 0d;
				for (int i=0; i<numRuns; i++) {
					double rate = subRates.get(i)[r];
					line.add(rate+"");
					avgRate += rate;
				}
				if (numRuns > 0) {
					avgRate /= (double)numRuns;
					Preconditions.checkState((float)rates[r] == (float)avgRate,
							"Average doesn't compute for r=%s: %s != %s", r, rates[r], avgRate);
				}
				csv.addLine(line);
			}
			String csvName = branch.buildFileName()+".csv";
			
			System.out.println("Writing "+csvName);
			
			out.putNextEntry(new ZipEntry(csvName));
			csv.writeToStream(out);
			
			// Close the current entry
			out.closeEntry();
		}
		
		out.close();
		
		weightCSV.writeToFile(new File(outputDir, "full_model_branch_weights.csv"));
	}

}
