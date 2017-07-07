package org.opensha.sra.calc.parallel.treeTrimming;

import java.io.BufferedReader;
import java.io.File;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.param.Parameter;
import org.opensha.sha.earthquake.ERF;
import org.opensha.sha.earthquake.EpistemicListERF;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.UCERF2_TimeDependentEpistemicList;

public class TreeTrimmingAssembler {
	
	public static double loadEAL(File file) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(file));
		String line = br.readLine();
		
		String[] split = line.split(" ");
		return Double.parseDouble(split[split.length-1]);
	}
	
	private static CSVFile<String> prepareCSV(EpistemicListERF erfList) {
		CSVFile<String> csv = new CSVFile<String>(true);
		
		System.out.println("preparing columns");
		HashMap<String, Integer> paramNamesMap = new HashMap<String, Integer>();
		ArrayList<String> paramNamesList = new ArrayList<String>();
		for (int i=0; i<erfList.getNumERFs(); i++) {
			ERF erf = erfList.getERF(i);
			
			System.out.println("ERF "+i);
			
			for (Parameter<?> param : erf.getAdjustableParameterList()) {
				if (!paramNamesMap.containsKey(param.getName())) {
					paramNamesMap.put(param.getName(), paramNamesMap.size());
					paramNamesList.add(param.getName());
				}
			}
		}
		
		System.out.println("assembling CSV");
		for (int i=0; i<erfList.getNumERFs(); i++) {
			ERF erf = erfList.getERF(i);
			
			if (i == 0) {
				// header
				ArrayList<String> header = new ArrayList<String>();
				header.add("ERF #");
				header.add("Portfolio EAL");
				for (String name : paramNamesList) {
					header.add(name);
				}
				csv.addLine(header);
			}
			
			ArrayList<String> line = new ArrayList<String>();
			line.add(i+"");
			line.add("");
			for (String name : paramNamesList)
				line.add("");
			for (Parameter<?> param : erf.getAdjustableParameterList()) {
				int index = paramNamesMap.get(param.getName())+2;
				line.set(index, param.getValue().toString());
			}
			csv.addLine(line);
		}
		
		return csv;
	}
	
	public static void assemble(File dir, EpistemicListERF erfList, boolean includeBackSeis,
			CSVFile<String> csv) throws IOException {
		double[] eals = new double[erfList.getNumERFs()];
		
		double backSeis = 0.0;
		
		System.out.println("loading files from: "+dir.getAbsolutePath());
		for (File file : dir.listFiles()) {
			if (file.isDirectory())
				continue;
			if (!file.getName().endsWith(".txt"))
				continue;
			
			if (file.getName().contains("backseis")) {
				if (includeBackSeis)
					backSeis = loadEAL(file);
			} else {
				int i = Integer.parseInt(file.getName().split("_")[1].replaceAll(".txt", ""));
				eals[i] = loadEAL(file);
			}
		}
		
		if (includeBackSeis)
			for (int i=0; i<eals.length; i++)
				eals[i] += backSeis;
		
		String fileName = dir.getName();
		if (includeBackSeis)
			fileName += "_incl_back_seis";
		fileName += ".csv";
		File output = new File(dir, fileName);
		
		int backSeisIndex = csv.getLine(0).indexOf(UCERF2.BACK_SEIS_NAME);
		
		for (int i=0; i<eals.length; i++) {
			csv.set(i+1, 1, eals[i]+"");
			if (includeBackSeis)
				csv.set(i+1, backSeisIndex, UCERF2.BACK_SEIS_INCLUDE);
			else
				csv.set(i+1, backSeisIndex, UCERF2.BACK_SEIS_EXCLUDE);
		}
		
		System.out.println("Writing: "+output.getAbsolutePath());
		csv.writeToFile(output);
	}

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
//		File mainDir = new File("/home/kevin/OpenSHA/portfolio_lec/parallel_eal/tree_trimming");
//		File mainDir = new File("/home/kevin/OpenSHA/portfolio_lec/parallel_eal/run2");
//		File mainDir = new File("/home/kevin/OpenSHA/portfolio_lec/parallel_eal/run3");
//		File mainDir = new File("/home/kevin/OpenSHA/portfolio_lec/parallel_eal/run4");
//		File mainDir = new File("/home/kevin/OpenSHA/portfolio_lec/parallel_eal/run5_CA99ptc");
		File mainDir = new File("/home/kevin/OpenSHA/portfolio_lec/parallel_eal/run4_compare");
		
		EpistemicListERF erfList = new UCERF2_TimeDependentEpistemicList();
		
		CSVFile<String> csv = CSVFile.readFile(new File(mainDir, "csv_inputs.csv"), true);
//		CSVFile<String> csv = prepareCSV(erfList);
		
		assemble(new File(mainDir, "AS2008"), erfList, false, csv);
		assemble(new File(mainDir, "AS2008"), erfList, true, csv);
		assemble(new File(mainDir, "Boore2008"), erfList, false, csv);
		assemble(new File(mainDir, "Boore2008"), erfList, true, csv);
		assemble(new File(mainDir, "CB2008"), erfList, false, csv);
		assemble(new File(mainDir, "CB2008"), erfList, true, csv);
		assemble(new File(mainDir, "CY2008"), erfList, false, csv);
		assemble(new File(mainDir, "CY2008"), erfList, true, csv);
	}

}
