package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.math3.util.Precision;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.CSVWriter;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.hazard.SiteLogicTreeHazardPageGen.ValueDistribution;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_LogicTreeHazardCalc;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_SiteLogicTreeHazardCurveCalc;
import org.opensha.sha.earthquake.faultSysSolution.modules.AbstractLogicTreeModule;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.imr.attenRelImpl.nshmp.NSHMP_Config;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

public class LogicTreeSimplifiedMapCurveWriter {
	
	private static final int MAX_SITES_IN_MEMORY_DEFAULT = 1000;
	private static final int MAX_ASYNC_READS = 10;
	static final double[] FRACTILES = {0.025, 0.16, 0.84, 0.975};
	static final String[] PERCENTILE_HEADERS;
	static {
		String[] headers = new String[FRACTILES.length];
		DecimalFormat df = new DecimalFormat("0.#");
		for (int i=0; i<headers.length; i++)
			headers[i] = "p"+df.format(FRACTILES[i]*100d);
		PERCENTILE_HEADERS = headers;
	}
	
	public static Options createOptions() {
		Options ops = new Options();

		ops.addOption(null, "max-read-threads", true,
				"Maximum read threads (>=1, more use more memory). Default is "+MAX_ASYNC_READS);
		ops.addOption(null, "max-sites-in-memory", true,
				"Maximum number of sites to keep in memory at once; "
				+ "lower values require more passes through the data. Default is "+MAX_SITES_IN_MEMORY_DEFAULT);
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
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, LogicTreeSimplifiedMapCurveWriter.class);
		args = cmd.getArgs();
		if (args.length < 2 || args.length > 3) {
			System.err.println("USAGE: LogicTreeSimplifiedMapCurveWriter <hazard-curves-file.zip> "
					+ "[<full-output-file.zip>] <summary-output-file.zip>");
			System.exit(1);
		}
		File curvesInputFile = new File(args[0]);
		Preconditions.checkState(curvesInputFile.isFile(), "Hazard curve archive doesn't exist: %s",
				curvesInputFile.getAbsolutePath());
		File fullOutputFile, summaryOutputFile;
		if (args.length == 2) {
			fullOutputFile = null;
			summaryOutputFile = new File(args[1]);
		} else {
			fullOutputFile = new File(args[1]);
			summaryOutputFile = new File(args[2]);
		}
		Preconditions.checkArgument(!curvesInputFile.getCanonicalFile().equals(summaryOutputFile.getCanonicalFile()),
				"Input and summary output files must be different: %s", curvesInputFile.getAbsolutePath());
		if (fullOutputFile != null) {
			Preconditions.checkArgument(!curvesInputFile.getCanonicalFile().equals(fullOutputFile.getCanonicalFile()),
					"Input and full output files must be different: %s", curvesInputFile.getAbsolutePath());
			Preconditions.checkArgument(!fullOutputFile.getCanonicalFile().equals(summaryOutputFile.getCanonicalFile()),
					"Full and summary output files must be different: %s", fullOutputFile.getAbsolutePath());
		}
		
		boolean nshmpIMLs = cmd.hasOption("nshmp-imls");
		
		ArchiveInput curvesInput = new ArchiveInput.PreloadingZipFileInput(curvesInputFile);
		Preconditions.checkState(curvesInput.hasEntry(AbstractLogicTreeModule.LOGIC_TREE_FILE_NAME),
				"Hazard curve archive is missing %s", AbstractLogicTreeModule.LOGIC_TREE_FILE_NAME);
		LogicTree<?> tree;
		try (InputStream input = curvesInput.getInputStream(AbstractLogicTreeModule.LOGIC_TREE_FILE_NAME);
				InputStreamReader reader = new InputStreamReader(input)) {
			tree = LogicTree.read(reader);
		}
		
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
		
		GriddedRegion gridReg = loadGridReg(curvesInput);
		
		System.out.println("Initializing output writers");
		
		ArchiveOutput tmpFullOutput = null;
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
		}
		ArchiveOutput fullOutput = tmpFullOutput;
		
		ArchiveOutput summaryOutput = new ArchiveOutput.AsynchronousZipFileOutput(summaryOutputFile);
		
		System.out.println("Writing site and logic tree JSON and CSVs");
		
		ArchiveOutput[] outputs = {summaryOutput, fullOutput};
		
		CSVFile<String> sitesCSV = new CSVFile<>(true);
		sitesCSV.addLine("Grid Index", "Latitude", "Longitude");
		for (int i=0; i<gridReg.getNodeCount(); i++) {
			Location loc = gridReg.getLocation(i);
			sitesCSV.addLine(i+"", (float)loc.lat+"", (float)loc.lon+"");
		}
		
		CSVFile<String> logicTreeCSV = buildLogicTreeCSV(tree, branchWeights);
		
		for (ArchiveOutput output : outputs) {
			if (output == null)
				continue;
			output.putNextEntry(MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME);
			Writer writer = new OutputStreamWriter(output.getOutputStream());
			Feature.write(gridReg.toFeature(), writer);
			writer.flush();
			output.closeEntry();
			
			output.putNextEntry("grid_locations.csv");
			sitesCSV.writeToStream(output.getOutputStream());
			output.closeEntry();
			
			tree.writeToArchive(output, "");
			
			output.putNextEntry("logic_tree.csv");
			logicTreeCSV.writeToStream(output.getOutputStream());
			output.closeEntry();
		}
		sitesCSV = null;
		logicTreeCSV = null;
		
		System.out.println("Detecting calculation periods from "+curvesInput.getName());
		double[] periods = detectCurvePeriods(curvesInput);
		
		int maxSitesInMemory = MAX_SITES_IN_MEMORY_DEFAULT;
		if (cmd.hasOption("max-sites-in-memory"))
			maxSitesInMemory = Integer.parseInt(cmd.getOptionValue("max-sites-in-memory"));
		Preconditions.checkState(maxSitesInMemory > 1, "Max sites in memory must be positive");
		
		List<int[]> siteLoadBatches = new ArrayList<>();
		int numSitesLeft = gridReg.getNodeCount();
		int curSiteIndex = 0;
		while (numSitesLeft > 0) {
			int bundleSize = Integer.min(numSitesLeft, maxSitesInMemory);
			int[] bundle = new int[bundleSize];
			for (int i=0; i<bundleSize; i++)
				bundle[i] = curSiteIndex++;
			siteLoadBatches.add(bundle);
			numSitesLeft -= bundleSize;
		}
		
		int branchMod;
		if (treeSize >= 100000)
			branchMod = 5000;
		else if (treeSize >= 50000)
			branchMod = 2500;
		else if (treeSize >= 20000)
			branchMod = 1000;
		else if (treeSize >= 10000)
			branchMod = 500;
		else if (treeSize >= 5000)
			branchMod = 250;
		else if (treeSize >= 1000)
			branchMod = 50;
		else
			branchMod = 10;
		
		int maxBundleSize = siteLoadBatches.get(0).length;
		int siteMod;
		if (maxBundleSize >= 10000)
			siteMod = 500;
		else if (maxBundleSize >= 5000)
			siteMod = 250;
		else if (maxBundleSize >= 1000)
			siteMod = 50;
		else if (maxBundleSize >= 500)
			siteMod = 25;
		else if (maxBundleSize >= 100)
			siteMod = 5;
		else
			siteMod = 1;
		
		if (siteLoadBatches.size() > 1)
			System.out.println("Have to read data "+siteLoadBatches.size()+" times to keep data from at most "
					+maxSitesInMemory+" sites in memory at once");
		
		int maxReadThreads = MAX_ASYNC_READS;
		if (cmd.hasOption("max-read-threads")) {
			maxReadThreads = Integer.parseInt(cmd.getOptionValue("max-read-threads"));
			Preconditions.checkState(maxReadThreads >= 1, "Must have at least 1 read thread");
		}
		
		Stopwatch overallWatch = Stopwatch.createStarted();
		for (int p=0; p<periods.length; p++) {
			System.out.println("Processing files for period "+p+"/"+periods.length+" ("+(float)periods[p]+")");
			
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
			
			final double[] nshmpXVals = nshmpIMLs ? NSHMP_Config.imlsFor(periods[p]) : null;
			
			for (int b=0; b<siteLoadBatches.size(); b++) {
				System.out.println("\tProcessing site batch "+b+"/"+siteLoadBatches.size()+", period "+p+"/"+periods.length);
				int[] siteIndexes = siteLoadBatches.get(b);
				
				System.out.println("\t\tLoading data for "+siteIndexes.length+" sites");
				
				DiscretizedFunc[][] siteCurves = new DiscretizedFunc[siteIndexes.length][treeSize];
				
				Deque<CompletableFuture<Void>> curveLoadFutures = new ArrayDeque<>(maxReadThreads);
				
				Stopwatch watch = Stopwatch.createStarted();
				
				for (int i=0; i<treeSize; i++) {
					if (curveLoadFutures.size() == maxReadThreads)
						curveLoadFutures.removeFirst().join();
					
					if (i % branchMod == 0)
						System.out.println("\t\tProcessing branch "+i+"/"+treeSize);
					
					LogicTreeBranch<?> branch = tree.getBranch(i);
					
					String curvesEntry = branch.getBranchZipPath()+"/"
							+SolHazardMapCalc.getCSV_FileName("curves", periods[p]);
					if (!curvesInput.hasEntry(curvesEntry))
						curvesEntry += ".gz";
					Preconditions.checkState(curvesInput.hasEntry(curvesEntry),
							"Curve entry doesn't exist: %s", curvesEntry);
					
					final String loadCurvesEntry = curvesEntry;
					final int branchIndex = i;
					
					boolean firstXValWarn = b == 0 && i == 0;
					
					curveLoadFutures.addLast(CompletableFuture.runAsync(new Runnable() {
						
						@Override
						public void run() {
							CSVFile<String> csv;
							try (InputStream archiveStream = curvesInput.getInputStream(loadCurvesEntry);
									InputStream csvStream = loadCurvesEntry.endsWith(".gz")
											? new GZIPInputStream(archiveStream) : archiveStream) {
								csv = CSVFile.readStream(csvStream, true);
							} catch (IOException e) {
								throw ExceptionUtils.asRuntimeException(e);
							}
							DiscretizedFunc[] branchCurves = SolHazardMapCalc.loadCurvesCSV(csv, gridReg);
							for (int s=0; s<siteIndexes.length; s++) {
								DiscretizedFunc curve = branchCurves[siteIndexes[s]];
								if (nshmpIMLs) {
									double[] interpYVals = new double[nshmpXVals.length];
									double curveMinX = curve.getMinX();
									double curveMaxX = curve.getMaxX();
									for (int j=0; j<nshmpXVals.length; j++) {
										double x = nshmpXVals[j];
										if (x < curveMinX) {
											if (firstXValWarn)
												System.err.println("WARNING: Input curve minX="+(float)curveMinX
														+" > nshmpMinX="+(float)x+", extrapolating using y from input minX");
											interpYVals[j] = curve.getY(0);
										} else if (x > curveMaxX) {
											if (firstXValWarn)
												System.err.println("WARNING: Input curve maxX="+(float)curveMaxX
														+" < nshmpMaxX="+(float)x+", extrapolating using y=0");
											interpYVals[j] = 0d;
										} else
											interpYVals[j] = curve.getInterpolatedY_inLogXDomain(x);
									}
									curve = new LightFixedXFunc(nshmpXVals, interpYVals);
								}
								siteCurves[s][branchIndex] = curve;
							}
						}
					}));
				}
				
				while (!curveLoadFutures.isEmpty())
					curveLoadFutures.removeFirst().join();
				
				watch.stop();
				System.out.println("\tTook "+timeStr(watch));
				
				DiscretizedFunc xVals = null;
				for (DiscretizedFunc[] curves : siteCurves) {
					for (DiscretizedFunc curve : curves) {
						if (curve != null) {
							xVals = curve;
							break;
						}
					}
					if (xVals != null)
						break;
				}
				Preconditions.checkNotNull(xVals, "No non-null curves found!");
				final DiscretizedFunc finalXVals = xVals;
				
				System.out.println("\tWriting data for site batch "+b+"/"+siteLoadBatches.size()+", period "+p+"/"+periods.length);
				
				watch.reset();
				watch.start();
				
				CompletableFuture<Void> fullWriteFuture = null;
				CompletableFuture<Void> summaryWriteFuture = null;
				
				for (int s=0; s<siteIndexes.length; s++) {
					if (s % siteMod == 0)
						System.out.println("\t\tWriting data for site "+s+"/"+siteIndexes.length);
					int siteIndex = siteIndexes[s];
					DiscretizedFunc[] curves = siteCurves[s];
					
					String sitePrefix = MPJ_SiteLogicTreeHazardCurveCalc.getSitePeriodPrefix("grid_"+siteIndex, periods[p]);
					
					if (fullOutput != null) {
						if (fullWriteFuture != null)
							fullWriteFuture.join();
						
						fullWriteFuture = CompletableFuture.runAsync(new Runnable() {
							
							@Override
							public void run() {
								try {
									fullOutput.putNextEntry(sitePrefix+".csv");
									
									CSVWriter writer = new CSVWriter(fullOutput.getOutputStream(), true);
									
									List<String> header = new ArrayList<>(2+finalXVals.size());
									header.add("Branch Index");
									header.add("Branch Weight");
									for (int i=0; i<finalXVals.size(); i++)
										header.add(""+(float)finalXVals.getX(i));
									
									writer.write(header);
									
									for (int i=0; i<treeSize; i++) {
										List<String> line = new ArrayList<>(header.size());
										line.add(i+"");
										line.add(branchWeights.get(i).floatValue()+"");
										DiscretizedFunc curve = curves[i];
										if (curve == null) {
											for (int j=0; j<finalXVals.size(); j++)
												line.add("0.0");
										} else {
											Preconditions.checkState(curve.size() == finalXVals.size());
											for (int j=0; j<curve.size(); j++)
												line.add((float)curve.getY(j)+"");
										}
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
					
					// now summary
					if (summaryWriteFuture != null)
						summaryWriteFuture.join();
					
					summaryWriteFuture = CompletableFuture.runAsync(new Runnable() {

						@Override
						public void run() {
							ValueDistribution[] curveDists = new ValueDistribution[finalXVals.size()];
							for (int x=0; x<finalXVals.size(); x++) {
								List<Double> vals = new ArrayList<>(treeSize);
								for (int i=0; i<treeSize; i++) {
									DiscretizedFunc curve = curves[i];
									if (curve == null)
										vals.add(0d);
									else
										vals.add(curve.getY(x));
								}
								curveDists[x] = new ValueDistribution(vals, branchWeights);
							}
							
							CSVFile<String> csv = buildCurveDistCSV(curveDists, perLabel+" ("+perUnits+")", finalXVals);
							
							try {
								summaryOutput.putNextEntry(sitePrefix+".csv");
								csv.writeToStream(summaryOutput.getOutputStream());
								summaryOutput.closeEntry();
							} catch (IOException e) {
								throw ExceptionUtils.asRuntimeException(e);
							}
						}
						
					});
				}
				
				if (fullWriteFuture != null)
					fullWriteFuture.join();
				if (summaryWriteFuture != null)
					summaryWriteFuture.join();
				
				System.out.println("\tDONE writing data for site batch "+b+"/"+siteLoadBatches.size()+", period "+p+"/"+periods.length);
				
				watch.stop();
				System.out.println("\tTook "+timeStr(watch));
			}
		}
		
		if (fullOutput != null)
			fullOutput.close();
		summaryOutput.close();
		curvesInput.close();
		
		System.out.println("DONE");
		overallWatch.stop();
		System.out.println("Took "+timeStr(overallWatch));
		System.exit(0);
	}
	
	static CSVFile<String> buildCurveDistCSV(ValueDistribution[] dists, String periodHeader, DiscretizedFunc xVals) {
		Preconditions.checkState(xVals.size() == dists.length);
		CSVFile<String> csv = new CSVFile<>(true);
		List<String> header = new ArrayList<>(5+PERCENTILE_HEADERS.length);
		header.add(periodHeader);
		header.add("Mean");
		header.add("Median");
		header.add("Min");
		for (String pHeader : PERCENTILE_HEADERS)
			header.add(pHeader);
		header.add("Max");
		csv.addLine(header);
		for (int i=0; i<xVals.size(); i++) {
			ValueDistribution dist = dists[i];
			List<String> line = new ArrayList<>(header.size());
			line.add((float)xVals.getX(i)+"");
			line.add(dist.mean+"");
			line.add(dist.getInterpolatedFractile(0.5f)+"");
			line.add(dist.min+"");
			for (double fractile : FRACTILES)
				line.add(dist.getInterpolatedFractile(fractile)+"");
			line.add(dist.max+"");
			csv.addLine(line);
		}
		
		return csv;
	}

	static CSVFile<String> buildLogicTreeCSV(LogicTree<?> tree, List<Double> branchWeights) {
		CSVFile<String> logicTreeCSV = new CSVFile<>(true);
		List<String> branchHeader = new ArrayList<>();
		branchHeader.add("Branch Index");
		branchHeader.add("Branch Weight");
		for (LogicTreeLevel<?> level : tree.getLevels())
			branchHeader.add(level.getShortName());
		logicTreeCSV.addLine(branchHeader);
		int treeSize = tree.size();
		for (int i=0; i<treeSize; i++) {
			List<String> line = new ArrayList<>(branchHeader.size());
			line.add(i+"");
			line.add(branchWeights.get(i)+"");
			LogicTreeBranch<?> branch = tree.getBranch(i);
			for (LogicTreeNode node : branch)
				line.add(node.getShortName());
			logicTreeCSV.addLine(line);
		}
		return logicTreeCSV;
	}
	
	static double[] detectCurvePeriods(ArchiveInput input) throws IOException {
		Set<Double> periods = new TreeSet<>();
		for (String entry : input.getEntries()) {
			String fileName = entry.substring(entry.lastIndexOf('/')+1);
			if (fileName.endsWith(".gz"))
				fileName = fileName.substring(0, fileName.length()-3);
			if (fileName.equals("curves_pgv.csv"))
				periods.add(-1d);
			else if (fileName.equals("curves_pga.csv"))
				periods.add(0d);
			else if (fileName.startsWith("curves_sa_") && fileName.endsWith(".csv"))
				periods.add(Double.parseDouble(fileName.substring("curves_sa_".length(), fileName.length()-4)));
		}
		Preconditions.checkState(!periods.isEmpty(), "No hazard curve periods detected in %s", input.getName());
		System.out.println("\tDetected periods: "+periods);
		double[] ret = new double[periods.size()];
		int index = 0;
		for (double period : periods)
			ret[index++] = period;
		return ret;
	}

	private static GriddedRegion loadGridReg(ArchiveInput input) throws IOException {
		String entryName = MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME;
		Preconditions.checkState(input.hasEntry(entryName), "Hazard curve archive is missing %s", entryName);
		System.out.println("Reading gridded region from archive: "+entryName);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input.getInputStream(entryName)))) {
			return GriddedRegion.fromFeature(Feature.read(reader));
		}
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
