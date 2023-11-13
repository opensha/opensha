package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.BinarayCatalogsMetadataIterator;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
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

		Option hashOnly = new Option("ho", "hash-only", false,
				"Flag to only compute a unique hash for this simulation (invariant of order in the file), suppressing individual catalog output.");
		hashOnly.setRequired(false);
		ops.addOption(hashOnly);

		Option deepHash = new Option("dh", "deep-hash", false,
				"Flag to compute hashes on each rupture and not just catalog metadata");
		deepHash.setRequired(false);
		ops.addOption(deepHash);

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
		
		List<ETAS_SimulationMetadata> metas = new ArrayList<>();
		boolean allHaveMeta = true;
		
		boolean hashOnly = cmd.hasOption("hash-only");
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			System.err.println("WARNING, can't build MD5 hashes: "+e.getMessage());
		}
		boolean deepHash = md != null && cmd.hasOption("deep-hash");

		boolean tail = cmd.hasOption("tail");
		if (tail)
			Preconditions.checkArgument(!hashOnly, "Can't supply both --tail and --hash-only");
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
			header.add("Metadata Hash");
			if (deepHash)
				header.add("Deep Hash");
			csv.addLine(header);
			if (tail)
				linesQueue = new LinkedBlockingQueue<>(maxNum);
		} else if (tail) {
			printQueue = new LinkedBlockingQueue<>(maxNum);
		}
		
		BinarayCatalogsMetadataIterator it = null;
		
		List<String> catalogDeepHashes = null;
		String fullCatalogDeepHash = null;
		if (deepHash) {
			System.out.println("Reading input file fully and computing deep hashes");
			catalogDeepHashes = new ArrayList<>();
			MessageDigest fullMD = null;
			try {
				fullMD = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			for (ETAS_Catalog catalog : ETAS_CatalogIO.getBinaryCatalogsIterable(resultsFile, Double.NEGATIVE_INFINITY)) {
				md.reset();
				deepHashCatalog(catalog, fullMD, md);
				byte[] hash = md.digest();
				catalogDeepHashes.add(hashBytesToString(hash));
			}
			byte[] fullHash = fullMD.digest();
			fullCatalogDeepHash = hashBytesToString(fullHash);
			System.out.println("Done fully reading catalog, computed "+catalogDeepHashes.size()+" catalog hashes");
		}
		
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
				if (meta != null)
					metas.add(meta);
				else
					allHaveMeta = false;
				if (csv == null) {
					String str = "Catalog "+count+": startPos="+startPos+", endPos="+endPos+", complete? "+complete
							+", numRuptures="+numRuptures+", version="+version;
					if (meta != null) {
						str += "\n\tMetadata: "+meta;
						if (md != null) {
							md.reset();
							hashCatalogMeta(meta, md);
							byte[] hash = md.digest();
							str += ", metaHash="+hashBytesToString(hash);
							if (deepHash && count < catalogDeepHashes.size())
								str += ", deepHash="+catalogDeepHashes.get(count);
						}
					}
					if (tail) {
						if (printQueue.remainingCapacity() == 0)
							printQueue.remove();
						Preconditions.checkState(printQueue.remainingCapacity() > 0);
						printQueue.put(str);
					} else if (!hashOnly) {
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
						if (md == null) {
							line.add("");
						} else {
							md.reset();
							hashCatalogMeta(meta, md);
							byte[] hash = md.digest();
							line.add(hashBytesToString(hash));
							if (deepHash) {
								if (count < catalogDeepHashes.size())
									line.add(catalogDeepHashes.get(count));
								else
									line.add("");
							}
						}
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
		if (metas.isEmpty() || !allHaveMeta) {
			if (metas.isEmpty())
				System.out.println("No catalogs have metadata, skipping hash");
			else
				System.out.println("Not all catalogs have metadata, skipping hash");
		} else if (md != null) {
			// we can build a hash
			Collections.sort(metas, new Comparator<ETAS_SimulationMetadata>() {

				@Override
				public int compare(ETAS_SimulationMetadata o1, ETAS_SimulationMetadata o2) {
					return Integer.compare(o1.catalogIndex, o2.catalogIndex);
				}
			});
			md.reset();
			for (ETAS_SimulationMetadata meta : metas)
				hashCatalogMeta(meta, md);
			byte[] hash = md.digest();
			System.out.println("Full Catalog Metadata Hash: "+hashBytesToString(hash));
			if (deepHash)
				System.out.println("Full Catalog Deep Hash: "+fullCatalogDeepHash);
		}
		System.exit(0);
	}
	
	private static void hashCatalogMeta(ETAS_SimulationMetadata meta, MessageDigest md) {
		ByteArrayDataOutput dout = ByteStreams.newDataOutput();
		dout.writeInt(meta.catalogIndex);
		dout.writeLong(meta.randomSeed);
		dout.writeInt(meta.totalNumRuptures);
		dout.writeInt(meta.numSpontaneousRuptures);
		dout.writeInt(meta.numSupraSeis);
		dout.writeDouble(meta.minMag);
		dout.writeDouble(meta.maxMag);
		md.update(dout.toByteArray());
	}
	
	private static void deepHashCatalog(ETAS_Catalog catalog, MessageDigest... mds) {
		ByteArrayDataOutput dout = ByteStreams.newDataOutput();
		dout.writeInt(catalog.size());
		for (ETAS_EqkRupture rup : catalog) {
			dout.writeInt(rup.getID());
			dout.writeInt(rup.getParentID());
			dout.writeShort(rup.getGeneration());
			dout.writeLong(rup.getOriginTime());
			Location hypo = rup.getHypocenterLocation();
			dout.writeDouble(hypo.lat);
			dout.writeDouble(hypo.lon);
			dout.writeDouble(hypo.depth);
			dout.writeDouble(rup.getMag());
			dout.writeDouble(rup.getDistanceToParent());
			dout.writeInt(rup.getNthERF_Index());
			dout.writeInt(rup.getFSSIndex());
			dout.writeInt(rup.getGridNodeIndex());
			dout.writeDouble(rup.getETAS_k());
		}
		byte[] bytes = dout.toByteArray();
		for (MessageDigest md : mds)
			md.update(bytes);
	}
	
	private static String hashBytesToString(byte[] hash) {
		BigInteger bigInt = new BigInteger(1,hash);
		String hashtext = bigInt.toString(16);
		// Now we need to zero pad it if you actually want the full 32 chars.
		while(hashtext.length() < 32 ){
			hashtext = "0"+hashtext;
		}
		return hashtext;
	}

}
