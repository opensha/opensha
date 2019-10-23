package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.ClassUtils;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.BinarayCatalogsMetadataIterator;
import scratch.UCERF3.erf.ETAS.ETAS_SimulationMetadata;

public class ETAS_BinaryMetadataWriter {
	
	private static Options createOptions() {
		Options ops = new Options();

		Option csvOption = new Option("csv", "csv-file", true,
				"If supplied, results will be written to the given CSV file instead of printed to the console");
		csvOption.setRequired(false);
		ops.addOption(csvOption);

		Option tail = new Option("t", "tail", false,
				"Flag to only show the last records, 10 by default unless overridden by --max-num");
		tail.setRequired(false);
		ops.addOption(tail);

		Option maxNum = new Option("m", "max-num", true,
				"Only process at least this many records");
		maxNum.setRequired(false);
		ops.addOption(maxNum);
		
		return ops;
	}

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		
		Options options = createOptions();
		
		CommandLineParser parser = new DefaultParser();
		
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(ClassUtils.getClassNameWithoutPackage(ETAS_BinaryMetadataWriter.class),
					options, true );
			System.exit(2);
			return;
		}
		
		args = cmd.getArgs();
		
		if (args.length != 1) {
			System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(ETAS_BinaryMetadataWriter.class)
					+" [options] results.bin");
			System.exit(2);
		}
		
		File resultsFile = new File(args[0]);
		Preconditions.checkState(resultsFile.exists(), "Results file doesn't exist: %s", resultsFile.getAbsolutePath());

		boolean tail = cmd.hasOption("tail");
		int maxNum = cmd.hasOption("max-num") ? Integer.parseInt(cmd.getOptionValue("max-num")) : -1;
		if (tail && maxNum < 0)
			maxNum = 10;
		CSVFile<String> csv = null;
		LinkedBlockingQueue<List<String>> linesQueue = null;
		LinkedBlockingQueue<String> printQueue = null;
		if (cmd.hasOption("csv-file")) {
			csv = new CSVFile<>(true);
			List<String> header = new ArrayList<>();
			header.add("Index in file");
			header.add("Data start position");
			header.add("Data end position");
			header.add("Fully written?");
			header.add("Total Num ruptures");
			header.add("File version");
			header.add("Total (original) number of ruptures");
			header.add("Random seed");
			header.add("Catalog index");
			header.add("Historical rupture start ID");
			header.add("Historical rupture end ID");
			header.add("Trigger rupture start ID");
			header.add("Trigger rupture end ID");
			header.add("Simulation start time");
			header.add("Simulation end time");
			header.add("Number spontaneous ruptures");
			header.add("Number supraseismogenic ruptures");
			header.add("Minimum magnitude");
			header.add("Maximum magnitude");
			csv.addLine(header);
			if (tail)
				linesQueue = new LinkedBlockingQueue<>(maxNum);
		} else if (tail) {
			printQueue = new LinkedBlockingQueue<>(maxNum);
		}
		
		BinarayCatalogsMetadataIterator it = null;
		
		try {
			it = ETAS_CatalogIO.getBinaryCatalogsMetadataIterator(resultsFile);
			
			int count = 0;
			while (it.hasNext()) {
				long startPos = it.getNextStartPos();
				long endPos = it.getNextEndPos();
				boolean complete = it.isNextFullyWritten();
				int numRuptures = it.getNextNumRuptures();
				short version = it.getNextFileVersion();
				ETAS_SimulationMetadata meta = it.next();
				if (csv == null) {
					String str = "Catalog "+count+": startPos="+startPos+", endPos="+endPos+", complete? "+complete
							+", numRuptures="+numRuptures+", version="+version;
					if (meta != null)
						str += "\n\tMetadata: "+meta;
					if (tail) {
						if (printQueue.remainingCapacity() == 0)
							printQueue.remove();
						Preconditions.checkState(printQueue.remainingCapacity() > 0);
						printQueue.put(str);
					} else {
						System.out.println(str);
					}
				} else {
					List<String> line = new ArrayList<>();
					line.add(count+"");
					line.add(startPos+"");
					line.add(endPos+"");
					line.add(complete+"");
					line.add(numRuptures+"");
					line.add(version+"");
					if (meta == null) {
						while (line.size() < csv.getNumCols())
							line.add("");
					} else {
						line.add(meta.totalNumRuptures+"");
						line.add(meta.randomSeed+"");
						line.add(meta.catalogIndex+"");
						if (meta.rangeHistCatalogIDs == null) {
							line.add("");
							line.add("");
						} else {
							line.add(meta.rangeHistCatalogIDs.lowerEndpoint()+"");
							line.add(meta.rangeHistCatalogIDs.upperEndpoint()+"");
						}
						if (meta.rangeHistCatalogIDs == null) {
							line.add("");
							line.add("");
						} else {
							line.add(meta.rangeTriggerRupIDs.lowerEndpoint()+"");
							line.add(meta.rangeTriggerRupIDs.upperEndpoint()+"");
						}
						line.add(meta.simulationStartTime+"");
						line.add(meta.simulationEndTime+"");
						line.add(meta.numSpontaneousRuptures+"");
						line.add(meta.numSupraSeis+"");
						line.add(meta.minMag+"");
						line.add(meta.maxMag+"");
					}
					if (tail) {
						if (linesQueue.remainingCapacity() == 0)
							linesQueue.remove();
						Preconditions.checkState(linesQueue.remainingCapacity() > 0);
						linesQueue.put(line);
					} else {
						csv.addLine(line);
					}
				}
				count++;
				if (!tail && count == maxNum)
					break;
			}
			if (tail) {
				if (csv == null)
					for (String str : printQueue)
						System.out.println(str);
				else
					for (List<String> line : linesQueue)
						csv.addLine(line);
			}
			if (csv != null) {
				File csvFile = new File(cmd.getOptionValue("csv-file"));
				System.out.println("Writing CSV to: "+csvFile.getAbsolutePath());
				csv.writeToFile(csvFile);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		} finally {
			if (it != null) {
				try {
					it.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		System.exit(0);
	}

}
