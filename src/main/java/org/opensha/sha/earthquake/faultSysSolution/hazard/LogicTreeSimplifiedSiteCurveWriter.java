package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.util.FileNameUtils;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.hazard.SiteLogicTreeHazardPageGen.ValueDistribution;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_SiteLogicTreeHazardCurveCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_Config;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Doubles;

public class LogicTreeSimplifiedSiteCurveWriter {
	
	public static Options createOptions() {
		Options ops = new Options();

		ops.addOption(null, "max-zip-threads", true,
				"Maximum parallel zip threads (>=1, more use more memory). Default is min(8, cpus-4).");
		ops.addOption(null, "nshmp-imls", false,
				"Flag to store data at NSHMP-lib IMLs (period-dependent)");
		
		return ops;
	}
	
	public static void main(String[] args) {
		try {
			run(args);
		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	public static void run(String[] args) throws IOException {
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, LogicTreeSimplifiedSiteCurveWriter.class);
		args = cmd.getArgs();
		if (args.length < 2 || args.length > 3) {
			System.err.println("USAGE: LogicTreeSimplifiedSiteCurveWriter <input-site-hazard.zip> [<output-site-full-curves.zip>] <output-site-summaries.zip>");
			System.exit(1);
		}
		File inputFile = new File(args[0]);
		Preconditions.checkState(inputFile.exists());
		File fullOutputFile, summaryOutputFile;
		if (args.length == 2) {
			fullOutputFile = null;
			summaryOutputFile = new File(args[1]);
		} else {
			fullOutputFile = new File(args[1]);
			summaryOutputFile = new File(args[2]);
		}
		
		boolean nshmpIMLs = cmd.hasOption("nshmp-imls");
		
		ArchiveInput input = ArchiveInput.getDefaultInput(inputFile);
		
		BufferedInputStream ltIS = new BufferedInputStream(input.getInputStream("logic_tree.json"));
		LogicTree<?> tree = LogicTree.read(new InputStreamReader(ltIS));
		CSVFile<String> sitesCSV = CSVFile.readStream(input.getInputStream("sites.csv"), true);
		
		CSVFile<String> sitesOutputCSV = new CSVFile<>(true);
		sitesOutputCSV.addLine("Site Name", "Latitude", "Longitude", "Site Filename Prefix");
		String[] sitePrefixes = new String[sitesCSV.getNumRows()-1];
		for (int row=1; row<sitesCSV.getNumRows(); row++) {
			String siteName = sitesCSV.get(row, 0);
			String sitePrefix = FileNameUtils.simplify(siteName);
			sitePrefixes[row-1] = sitePrefix;
			sitesOutputCSV.addLine(siteName, sitesCSV.get(row, 1), sitesCSV.get(row, 2), sitePrefix);
		}
		
		ArchiveOutput summaryOutput = new ArchiveOutput.AsynchronousZipFileOutput(summaryOutputFile);
		ArchiveOutput tmpFullOutput = null;
		ArchiveOutput[] outputs;
		if (fullOutputFile != null) {
			int threads = FaultSysTools.defaultNumThreads();
			int maxThreads = Integer.min(8, threads-4);
			if (cmd.hasOption("max-zip-threads")) {
				maxThreads = Integer.parseInt(cmd.getOptionValue("max-zip-threads"));
				Preconditions.checkState(maxThreads >= 1, "Must have at least 1 zip thread");
			}
			if (threads < 8 || maxThreads == 1)
				tmpFullOutput = new ArchiveOutput.AsynchronousZipFileOutput(fullOutputFile);
			else
				tmpFullOutput = new ArchiveOutput.ParallelZipFileOutput(fullOutputFile, maxThreads, true);
			outputs = new ArchiveOutput[] {summaryOutput, tmpFullOutput};
		} else {
			outputs = new ArchiveOutput[] {summaryOutput};
		}
		ArchiveOutput fullOutput = tmpFullOutput;
		
		int treeSize = tree.size();
		
		List<Double> branchWeights = new ArrayList<>(treeSize);
		double sumWeights = 0d;
		for (int i=0; i<treeSize; i++) {
			double weight = tree.getBranchWeight(i);
			branchWeights.add(weight);
			sumWeights += weight;
		}
		if (!Precision.equals(sumWeights, 1d, 1e-5)) {
			double scalar = 1d/sumWeights;
			for (int i=0; i<treeSize; i++)
				branchWeights.set(i, branchWeights.get(i)*scalar);
		}
		
		CSVFile<String> logicTreeCSV = LogicTreeSimplifiedMapCurveWriter.buildLogicTreeCSV(tree, branchWeights);
		
		for (ArchiveOutput output : outputs) {
			output.putNextEntry("sites.csv");
			sitesOutputCSV.writeToStream(output.getOutputStream());
			output.closeEntry();
			
			tree.writeToArchive(output, "");
			
			output.putNextEntry("logic_tree.csv");
			logicTreeCSV.writeToStream(output.getOutputStream());
			output.closeEntry();
		}
		
		sitesCSV = null;
		sitesOutputCSV = null;
		logicTreeCSV = null;
		System.out.println("Detecting calculation periods from "+inputFile.getName());
		List<Double> periodsList = new ArrayList<>(locateSiteCurveCSVs(sitePrefixes[0], input).keySet());
		Collections.sort(periodsList);
		double[] periods = Doubles.toArray(periodsList);
		
		Stopwatch overallWatch = Stopwatch.createStarted();
		
		CompletableFuture<Void> fullWriteFuture = null;
		CompletableFuture<Void> summaryWriteFuture = null;
		
		for (int s=0; s<sitePrefixes.length; s++) {
			System.out.println("Processing site "+s+"/"+sitePrefixes.length+": "+sitePrefixes[s]);
			
			Stopwatch siteWatch = Stopwatch.createStarted();
			
			for (int p=0; p<periods.length; p++) {
				String perLabel, perUnits;
				if (periods[p] == -1d) {
					perLabel = "PGV";
					perUnits = "cm/s";
				} else if (periods[p] == 0d) {
					perLabel = "PGA";
					perUnits = "g";
				} else {
					perLabel = (float)periods[p]+"s SA";
					perUnits = "g";
				}
				
				String csvName = MPJ_SiteLogicTreeHazardCurveCalc.getSitePeriodPrefix(sitePrefixes[s], periods[p])+".csv";
				
				final double[] nshmpXVals = nshmpIMLs ? NSHMP_Config.imlsFor(periods[p]) : null;
				
				CSVFile<String> curvesCSV = CSVFile.readStream(input.getInputStream(csvName), true);
				List<DiscretizedFunc> curves = new ArrayList<>();
				List<Double> weights = new ArrayList<>();
				System.out.println("\tLoading curves and building dists for "+perLabel);
				ValueDistribution[] dists = SiteLogicTreeHazardPageGen.loadCurvesAndDists(
						curvesCSV, tree.getLevels(), curves, null, weights, true, nshmpXVals);
				
				Preconditions.checkState(curves.size() == treeSize);
				for (int i=0; i<treeSize; i++) {
					double fileWeight = weights.get(i)/sumWeights;
					double branchWeight = weights.get(i);
					Preconditions.checkState(Precision.equals(fileWeight, branchWeight, 1e-5),
							"Bad weight for curve file branch %s: %s != %s", i, (float)fileWeight, (float)branchWeight);
				}
				
				DiscretizedFunc xVals = null;
				for (DiscretizedFunc curve : curves) {
					if (curve != null) {
						xVals = curve;
						break;
					}
				}
				Preconditions.checkNotNull(xVals, "No non-null curves found!");
				final DiscretizedFunc finalXVals = xVals;
				
				// write full
				if (fullOutput != null) {
					if (fullWriteFuture != null)
						fullWriteFuture.join();
					
					fullWriteFuture = CompletableFuture.runAsync(new Runnable() {

						@Override
						public void run() {
							try {
								fullOutput.putNextEntry(csvName);

								CSVWriter writer = new CSVWriter(fullOutput.getOutputStream(), true);

								List<String> header = new ArrayList<>(2 + curves.get(0).size());
								header.add("Branch Index");
								header.add("Branch Weight");
								for (int i = 0; i<finalXVals.size(); i++)
									header.add(""+(float)finalXVals.getX(i));

								writer.write(header);

								for (int i=0; i<curves.size(); i++) {
									List<String> line = new ArrayList<>(header.size());
									line.add(i+"");
									line.add(branchWeights.get(i).floatValue()+"");
									DiscretizedFunc curve = curves.get(i);
									Preconditions.checkState(curve.size() == header.size() - 2);
									for (int j=0; j<curve.size(); j++)
										line.add((float)curve.getY(j)+"");
									writer.write(line);
								}

								writer.flush();
								fullOutput.closeEntry();
							} catch (IOException e) {
								throw ExceptionUtils.asRuntimeException(e);
							}
						}
					});
				}
				
				// write summary
				if (summaryWriteFuture != null)
					summaryWriteFuture.join();
				
				summaryWriteFuture = CompletableFuture.runAsync(new Runnable() {

					@Override
					public void run() {
						CSVFile<String> csv = LogicTreeSimplifiedMapCurveWriter.buildCurveDistCSV(
								dists, perLabel+" ("+perUnits+")", finalXVals);
						
						try {
							summaryOutput.putNextEntry(csvName);
							csv.writeToStream(summaryOutput.getOutputStream());
							summaryOutput.closeEntry();
						} catch (IOException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
					}
					
				});
			}
			
			siteWatch.stop();
			System.out.println("\tDONE with "+sitePrefixes[s]+"; took "+timeStr(siteWatch));
		}

		if (fullWriteFuture != null)
			fullWriteFuture.join();
		if (summaryWriteFuture != null)
			summaryWriteFuture.join();
		
		if (fullOutput != null)
			fullOutput.close();
		summaryOutput.close();
		
		System.out.println("DONE");
		overallWatch.stop();
		System.out.println("Took "+timeStr(overallWatch));
		System.exit(0);
	}
	
	public static Map<Double, String> locateSiteCurveCSVs(String sitePrefix, ArchiveInput input) throws IOException {
		Map<Double, String> perEntires = new HashMap<>();
		for (String entry : input.getEntries()) {
			if (entry.startsWith(sitePrefix)) {
				String nameLeft = entry.substring(sitePrefix.length());
				double period;
				if (nameLeft.equals("_pgv.csv")) {
					period = -1;
				} else if (nameLeft.equals("_pga.csv")) {
					period = 0d;
				} else if (nameLeft.startsWith("_sa_") && nameLeft.endsWith(".csv")) {
					String perStr = nameLeft.substring(4, nameLeft.length()-4);
					period = Double.parseDouble(perStr);
				} else {
					System.err.println("Skipping unexpected file that we couldn't parse for a period: "+entry);
					continue;
				}
				perEntires.put(period, entry);
			}
		}
		return perEntires;
	}
	
	private static final DecimalFormat timeDF = new DecimalFormat("0.0");
	
	private static String timeStr(Stopwatch watch) {
		 double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		 if (secs > 60d) {
			 double mins = secs/60d;
			 if (mins > 60d) {
				 return timeDF.format(mins/60d)+"h";
			 } else {
				 return timeDF.format(mins)+"m";
			 }
		 } else {
			 return timeDF.format(secs)+"s";
		 }
	}

}
