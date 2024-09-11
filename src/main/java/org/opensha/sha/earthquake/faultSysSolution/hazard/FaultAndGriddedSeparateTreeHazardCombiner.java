package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntryPredicate;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_LogicTreeHazardCalc;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

public class FaultAndGriddedSeparateTreeHazardCombiner {

	public static void main(String[] args) throws IOException {
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, LogicTreeHazardCompare.class);
		args = cmd.getArgs();
		
		if (args.length < 7 || args.length > 8) {
			System.err.println("USAGE: <results_fault.zip> <results_fault_hazard_dir> "
					+ "<results_gridded.zip> <results_gridded_hazard_dir> "
					+ "<results_output_combined.zip> <results_output_hazard_combined.zip> <grid-region.geojson> [<periods>]");
			System.exit(1);
		}
		
		int cnt = 0;
		
		File faultSLTFile = new File(args[cnt++]);
		SolutionLogicTree faultSLT = SolutionLogicTree.load(faultSLTFile);
		File faultHazardDir = new File(args[cnt++]);
		
		SolutionLogicTree griddedSLT = SolutionLogicTree.load(new File(args[cnt++]));
		File griddedHazardDir = new File(args[cnt++]);
		
		String resultFileName = args[cnt++];
		File resultsOutFile = resultFileName.trim().toLowerCase().equals("null") ? null : new File(resultFileName);
		File hazardOutFile = new File(args[cnt++]);
		
//		double gridSpacing = Double.parseDouble(args[cnt++]);
		File gridRegFile = new File(args[cnt++]);
		GriddedRegion gridReg;
		if (gridRegFile.getName().endsWith(".zip")) {
			// it's a zip file contains the gridded region file
			ZipFile zip = new ZipFile(gridRegFile);
			ZipEntry gridRegEntry = zip.getEntry("gridded_region.geojson");
			Preconditions.checkNotNull(gridRegEntry);
			BufferedInputStream is = new BufferedInputStream(zip.getInputStream(gridRegEntry));
			Feature gridRegFeature = Feature.read(new InputStreamReader(is));
			gridReg = GriddedRegion.fromFeature(gridRegFeature);
			zip.close();
		} else {
			Feature gridRegFeature = Feature.read(gridRegFile);
			gridReg = GriddedRegion.fromFeature(gridRegFeature);
		}
		double gridSpacing = gridReg.getSpacing();
		double[] periods = MPJ_LogicTreeHazardCalc.PERIODS_DEFAULT;
		ReturnPeriods[] rps = SolHazardMapCalc.MAP_RPS;
		
		if (cnt < args.length) {
			List<Double> periodsList = new ArrayList<>();
			String periodsStr = args[cnt++];
			if (periodsStr.contains(",")) {
				String[] split = periodsStr.split(",");
				for (String str : split)
					periodsList.add(Double.parseDouble(str));
			} else {
				periodsList.add(Double.parseDouble(periodsStr));
			}
			periods = Doubles.toArray(periodsList);
		}
		
		LogicTree<?> faultTree = faultSLT.getLogicTree();
		LogicTree<?> gridTree = griddedSLT.getLogicTree();
		
		String faultHazardDirName = "hazard_"+(float)gridSpacing+"deg_grid_seis_"+IncludeBackgroundOption.EXCLUDE.name();
		String gridHazardDirName = "hazard_"+(float)gridSpacing+"deg_grid_seis_"+IncludeBackgroundOption.ONLY.name();
		
		// pre-load gridded seismicity hazard curves
		List<DiscretizedFunc[][]> gridSeisCurves = new ArrayList<>();
		for (LogicTreeBranch<?> gridBranch : gridTree) {
			System.out.println("Pre-loading curves for gridded seismicity branch "+gridBranch);
			File branchResultsDir = gridBranch.getBranchDirectory(griddedHazardDir, true);
			File branchHazardDir = new File(branchResultsDir, gridHazardDirName);
			Preconditions.checkState(branchHazardDir.exists(), "%s doesn't exist", branchHazardDir.getAbsolutePath());
			
			gridSeisCurves.add(loadCurves(branchHazardDir, periods, gridReg));
		}
		
		DiscretizedFunc[][] meanGridSeisCurves = null;
		List<LogicTreeLevel<? extends LogicTreeNode>> combinedLevels = new ArrayList<>();
		combinedLevels.addAll(faultTree.getLevels());
		if (cmd.hasOption("average-gridded")) {
			System.out.println("Averaging gridded seismicity curves");
			LogicTreeCurveAverager[] meanCurves = new LogicTreeCurveAverager[periods.length];
			for (int p=0; p<periods.length; p++)
				meanCurves[p] = new LogicTreeCurveAverager(gridTree, gridReg.getNodeList());
			
			for (int g=0; g<gridTree.size(); g++) {
				LogicTreeBranch<?> gridBranch = gridTree.getBranch(g);
				DiscretizedFunc[][] gridCurves = gridSeisCurves.get(g);
				
				double weight = gridTree.getBranchWeight(g);
				
				for (int p=0; p<periods.length; p++)
					meanCurves[p].processBranchCurves(gridBranch, weight, gridCurves[p]);
			}
			
			meanGridSeisCurves = new DiscretizedFunc[periods.length][];
			for (int p=0; p<periods.length; p++)
				meanGridSeisCurves[p] = meanCurves[p].getNormalizedCurves().get(LogicTreeCurveAverager.MEAN_PREFIX);
			
			gridTree = null;
		} else {
			for (LogicTreeLevel<?> gridLevel : gridTree.getLevels()) {
				// see if it's already in the fault tree
				boolean duplicate = false;
				for (LogicTreeLevel<?> faultLevel : faultTree.getLevels()) {
					if (faultLevel.getType().equals(gridLevel.getType())) {
						duplicate = true;
						break;
					}
				}
				if (!duplicate)
					combinedLevels.add(gridLevel);
			}
		}
		
		List<LogicTreeBranch<LogicTreeNode>> combinedBranches = new ArrayList<>();
		File tmpOut = new File(hazardOutFile.getAbsolutePath()+".tmp");
		ZipOutputStream hazardOutZip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmpOut)));
		
		CompletableFuture<Void> writeFuture = null;
		WriteCounter writeCounter = new WriteCounter();
		
		LogicTreeCurveAverager[] meanCurves = new LogicTreeCurveAverager[periods.length];
		HashSet<LogicTreeNode> variableNodes = new HashSet<>();
		HashMap<LogicTreeNode, LogicTreeLevel<?>> nodeLevels = new HashMap<>();
		LogicTreeCurveAverager.populateVariableNodes(faultTree, variableNodes, nodeLevels);
		if (gridTree != null)
			LogicTreeCurveAverager.populateVariableNodes(gridTree, variableNodes, nodeLevels);
		for (int p=0; p<periods.length; p++)
			meanCurves[p] = new LogicTreeCurveAverager(gridReg.getNodeList(), variableNodes, nodeLevels);
		
		ExecutorService exec = Executors.newFixedThreadPool(FaultSysTools.defaultNumThreads());
		
		Stopwatch watch = Stopwatch.createStarted();
		
		Stopwatch combineWatch = Stopwatch.createUnstarted();
		Stopwatch mapStringWatch = Stopwatch.createUnstarted();
		Stopwatch blockingIOWatch = Stopwatch.createUnstarted();
		Stopwatch blockingAvgWatch = Stopwatch.createUnstarted();
		
		CompletableFuture<DiscretizedFunc[][]> nextFaultLoadFuture = null;
		
		for (int f=0; f<faultTree.size(); f++) {
			LogicTreeBranch<?> faultBranch = faultTree.getBranch(f);
			System.out.println("Processing fault branch "+f+": "+faultBranch);
			
			if (nextFaultLoadFuture == null)
				nextFaultLoadFuture = curveLoadFuture(faultHazardDir, faultHazardDirName, faultBranch, periods);
			
			DiscretizedFunc[][] faultCurves = nextFaultLoadFuture.join();
			
			if (f < faultTree.size()-1)
				// start the next one asynchronously
				nextFaultLoadFuture = curveLoadFuture(faultHazardDir, faultHazardDirName, faultTree.getBranch(f+1), periods);
			
			double faultWeight = faultTree.getBranchWeight(faultBranch);
			
			List<CompletableFuture<Void>> perAvgFutures = new ArrayList<>();
			
			int numGrid = meanGridSeisCurves != null ? 1 : gridTree.size();
			for (int g=0; g<numGrid; g++) {
				combineWatch.start();
				
				LogicTreeBranch<LogicTreeNode> combinedBranch = new LogicTreeBranch<>(combinedLevels);
				for (LogicTreeNode node : faultBranch)
					combinedBranch.setValue(node);
				DiscretizedFunc[][] gridCurves;
				double gridWeight;
				if (meanGridSeisCurves == null) {
					LogicTreeBranch<?> gridBranch = gridTree.getBranch(g);
					for (LogicTreeNode node : gridBranch)
						combinedBranch.setValue(node);
					
					gridWeight = gridTree.getBranchWeight(gridBranch);
					gridCurves = gridSeisCurves.get(g);
				} else {
					gridWeight = 1d;
					gridCurves = meanGridSeisCurves;
				}
				double combWeight = faultWeight * gridWeight;
				String combPrefix = combinedBranch.getBranchZipPath();
				combinedBranch.setOrigBranchWeight(faultWeight);
				
				Map<String, GriddedGeoDataSet> writeMap = new HashMap<>(gridReg.getNodeCount()*periods.length);
				
				for (int p=0; p<periods.length; p++) {
					Preconditions.checkState(faultCurves[p].length == gridCurves[p].length);
					Preconditions.checkState(faultCurves[p].length == gridReg.getNodeCount());
					double[] xVals = new double[faultCurves[p][0].size()];
					for (int i=0; i<xVals.length; i++)
						xVals[i] = faultCurves[p][0].getX(i);
					
					List<Future<GridLocResult>> futures = new ArrayList<>();
					
					for (int i=0; i<faultCurves[p].length; i++)
						futures.add(exec.submit(new ProcessCallable(i, xVals, faultCurves[p][i], gridCurves[p][i], rps)));
					
					DiscretizedFunc[] combCurves = new DiscretizedFunc[gridCurves[p].length];
					GriddedGeoDataSet[] xyzs = new GriddedGeoDataSet[rps.length];
					for (int r=0; r<rps.length; r++)
						xyzs[r] = new GriddedGeoDataSet(gridReg, false);
					
					try {
						for (Future<GridLocResult> future : futures) {
							GridLocResult result = future.get();
							
							combCurves[result.gridIndex] = result.combCurve;
							for (int r=0; r<rps.length; r++)
								xyzs[r].set(result.gridIndex, result.mapVals[r]);
						}
					} catch (ExecutionException | InterruptedException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					
					for (int r=0; r<rps.length; r++) {
						String mapFileName = MPJ_LogicTreeHazardCalc.mapPrefix(periods[p], rps[r])+".txt";
						String entryName = combPrefix+"/"+mapFileName;
						
						Preconditions.checkState(writeMap.put(entryName, xyzs[r]) == null,
								"Duplicate entry? %s", entryName);
					}
					
					final LogicTreeCurveAverager averager = meanCurves[p];
					perAvgFutures.add(CompletableFuture.runAsync(new Runnable() {
								
						@Override
						public void run() {
							averager.processBranchCurves(combinedBranch, combWeight, combCurves);
						}
					}));
				}
				
				combineWatch.stop();
				
				// build map string representations (surprisingly slow)
				mapStringWatch.start();
				Map<String, Future<byte[]>> mapStringByteFutures = new HashMap<>(writeMap.size());
				
				for (String entryName : writeMap.keySet()) {
					GriddedGeoDataSet xyz = writeMap.get(entryName);
					mapStringByteFutures.put(entryName, exec.submit(new Callable<byte[]>() {

						@Override
						public byte[] call() throws Exception {
							// build string representation
							int size = Integer.max(1000, xyz.size()*12);
							StringWriter stringWriter = new StringWriter(size);
							ArbDiscrGeoDataSet.writeXYZWriter(xyz, stringWriter);
							stringWriter.flush();
							return stringWriter.toString().getBytes();
						}
						
					}));
				}
				
				Map<String, byte[]> mapStringBytes = new HashMap<>(writeMap.size());
				try {
					for (String entryName : writeMap.keySet())
						mapStringBytes.put(entryName, mapStringByteFutures.get(entryName).get());
				} catch (ExecutionException | InterruptedException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				
				mapStringWatch.stop();
				
				if (writeFuture != null) { 
					blockingIOWatch.start();
					writeFuture.join();
					blockingIOWatch.stop();
					double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
					System.out.println("\tDone writing branch "+combinedBranches.size()+" ("+(float)(combinedBranches.size()/secs)+" /s)");
				}
				
				// everything should have been written now
				int writable = writeCounter.getWritable();
				Preconditions.checkState(writable == 0, "Have %s writable maps after join? written=%s",
						writable, writeCounter.getWritten());
				
				int expected = periods.length*rps.length;
				Preconditions.checkState(writeMap.size() == expected,
						"Expected $s writeable maps, have %s", expected, writeMap.size());
				
				writeCounter.incrementWritable(expected);
				
				System.out.println("Writing combined branch "+combinedBranches.size()+": "+combinedBranch);
				System.out.println("\tCombined weight: "+(float)faultWeight+" x "+(float)gridWeight+" = "+(float)combWeight);
				
				System.out.println("\tWriting "+writeCounter.getWritable()+" new maps, "+writeCounter.getWritten()+" written so far");
				combinedBranches.add(combinedBranch);
				
				writeFuture = CompletableFuture.runAsync(new Runnable() {
					
					@Override
					public void run() {
						try {
							for (String entryName : mapStringBytes.keySet()) {
								byte[] mapBytes = mapStringBytes.get(entryName);
								
								hazardOutZip.putNextEntry(new ZipEntry(entryName));
								hazardOutZip.write(mapBytes);
								hazardOutZip.closeEntry();
								writeCounter.incrementWritten();
							}
						} catch (Exception e) {
							e.printStackTrace();
							System.exit(1);
						}
					}
				});
				
			}
			
			System.out.println("Waiting on "+perAvgFutures.size()+" averaging futures...");
			// can wait on these later after we've finished writing
			blockingAvgWatch.start();
			for (CompletableFuture<Void> future : perAvgFutures)
				future.join();
			blockingAvgWatch.stop();
			System.out.println("DONE fault branch "+f+"/"+faultTree.size());
			System.out.println("\tTotal time combining:\t"+blockingTimePrint(combineWatch, watch));
			System.out.println("\tTotal on map file rep:\t"+blockingTimePrint(mapStringWatch, watch));
			System.out.println("\tTotal blocking I/O:\t"+blockingTimePrint(blockingIOWatch, watch));
			System.out.println("\tTotal blocking Averaging:\t"+blockingTimePrint(blockingAvgWatch, watch));
			double totSecs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
			double secsEach = totSecs / (f+1);
			double secsLeft = secsEach*(faultTree.size()-(f+1));
			double minsLeft = secsLeft/60d;
			double hoursLeft = minsLeft/60d;
			if (minsLeft > 90)
				System.out.println("\tEstimated time left: "+twoDigits.format(hoursLeft)+" hours");
			else if (secsLeft > 90)
				System.out.println("\tEstimated time left: "+twoDigits.format(minsLeft)+" mins");
			else
				System.out.println("\tEstimated time left: "+twoDigits.format(secsLeft)+" secs");
		}
		
		exec.shutdown();
		
		if (writeFuture != null)
			writeFuture.join();
		
		watch.stop();
		double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		
		System.out.println("Wrote for "+combinedBranches.size()+" branches ("+(float)(combinedBranches.size()/secs)+" /s)");
		System.out.println("Wrote "+writeCounter.getWritten()+" maps in total");
		
		// write mean curves and maps
		MPJ_LogicTreeHazardCalc.writeMeanCurvesAndMaps(hazardOutZip, meanCurves, gridReg, periods, rps);
		
		hazardOutZip.putNextEntry(new ZipEntry(MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME));
		Feature gridFeature = gridReg.toFeature();
		Feature.write(gridFeature, new OutputStreamWriter(hazardOutZip));
		hazardOutZip.closeEntry();
		
		hazardOutZip.close();
		Files.move(tmpOut, hazardOutFile);
		
		if (resultsOutFile != null) {
			// now copy over results zip file with the new tree
			tmpOut = new File(resultsOutFile.getAbsolutePath()+".tmp");
			ZipArchiveOutputStream zout = new ZipArchiveOutputStream(tmpOut);
			
			org.apache.commons.compress.archivers.zip.ZipFile zip = new org.apache.commons.compress.archivers.zip.ZipFile(faultSLTFile);
			
			zip.copyRawEntries(zout, new ZipArchiveEntryPredicate() {
				
				@Override
				public boolean test(ZipArchiveEntry zipArchiveEntry) {
					if (zipArchiveEntry.getName().endsWith("logic_tree.json")) {
						// update it
						try {
							LogicTree<?> tree = LogicTree.fromExisting(combinedLevels, combinedBranches);
							System.out.println("Updaing logic tree archive with "+tree.size()+" branches: "+zipArchiveEntry.getName());
							
							zout.putArchiveEntry(new ZipArchiveEntry(zipArchiveEntry.getName()));
							BufferedOutputStream bStream = new BufferedOutputStream(zout);
							tree.writeToStream(bStream);
							bStream.flush();
							zout.flush();
							zout.closeArchiveEntry();
						} catch (IOException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
						return false;
					}
					return true;
				}
			});
			
			zip.close();
			zout.close();
			Files.move(tmpOut, resultsOutFile);
		}
	}
	
	public static Options createOptions() {
		Options ops = new Options();
		
		ops.addOption("ag", "average-gridded", false,
				"Flag to only use average gridded seismicity.");
		
		return ops;
	}
	
	private static final DecimalFormat twoDigits = new DecimalFormat("0.00");
	private static final DecimalFormat pDF = new DecimalFormat("0.00%");
	
	private static String blockingTimePrint(Stopwatch blockingWatch, Stopwatch totalWatch) {
		double blockSecs = blockingWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		double blockMins = blockSecs/60d;
		String timeStr;
		if (blockMins > 1d) {
			timeStr = twoDigits.format(blockMins)+" m";
		} else {
			timeStr = twoDigits.format(blockSecs)+" s";
		}
		
		double totSecs = totalWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		
		return timeStr+" ("+pDF.format(blockSecs/totSecs)+")";
	}
	
	private static CompletableFuture<DiscretizedFunc[][]> curveLoadFuture(File faultHazardDir, String faultHazardDirName,
			LogicTreeBranch<?> faultBranch, double[] periods) {
		File branchResultsDir = faultBranch.getBranchDirectory(faultHazardDir, true);
		File branchHazardDir = new File(branchResultsDir, faultHazardDirName);
		Preconditions.checkState(branchHazardDir.exists(), "%s doesn't exist", branchHazardDir.getAbsolutePath());
		
		return CompletableFuture.supplyAsync(new Supplier<DiscretizedFunc[][]>() {

			@Override
			public DiscretizedFunc[][] get() {
				try {
					return loadCurves(branchHazardDir, periods, null);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		});
	}
	
	private static DiscretizedFunc[][] loadCurves(File hazardDir, double[] periods, GriddedRegion region) throws IOException {
		DiscretizedFunc[][] curves = new DiscretizedFunc[periods.length][];
		for (int p=0; p<periods.length; p++) {
			String fileName = SolHazardMapCalc.getCSV_FileName("curves", periods[p]);
			File csvFile = new File(hazardDir, fileName);
			if (!csvFile.exists())
				csvFile = new File(hazardDir, fileName+".gz");
			Preconditions.checkState(csvFile.exists(), "Curves CSV file not found: %s", csvFile.getAbsolutePath());
			CSVFile<String> csv = CSVFile.readFile(csvFile, true);
			curves[p] = SolHazardMapCalc.loadCurvesCSV(csv, region);
		}
		return curves;
	}
	
	private static class GridLocResult {
		private final int gridIndex;
		private final DiscretizedFunc combCurve;
		private final double[] mapVals;
		
		public GridLocResult(int gridIndex, DiscretizedFunc combCurve, double[] mapVals) {
			super();
			this.gridIndex = gridIndex;
			this.combCurve = combCurve;
			this.mapVals = mapVals;
		}
	}
	
	private static class ProcessCallable implements Callable<GridLocResult> {

		private int gridIndex;
		private double[] xVals;
		private DiscretizedFunc faultCurve;
		private DiscretizedFunc gridCurve;
		private ReturnPeriods[] rps;

		public ProcessCallable(int gridIndex, double[] xVals, DiscretizedFunc faultCurve, DiscretizedFunc gridCurve,
				ReturnPeriods[] rps) {
			this.gridIndex = gridIndex;
			this.xVals = xVals;
			this.faultCurve = faultCurve;
			this.gridCurve = gridCurve;
			this.rps = rps;
		}

		@Override
		public GridLocResult call() throws Exception {
			Preconditions.checkState(faultCurve.size() == xVals.length);
			Preconditions.checkState(gridCurve.size() == xVals.length);
			double[] yVals = new double[xVals.length];
			for (int j=0; j<faultCurve.size(); j++) {
				double x = faultCurve.getX(j);
				Preconditions.checkState((float)x == (float)gridCurve.getX(j));
				double y1 = faultCurve.getY(j);
				double y2 = gridCurve.getY(j);
				double y;
				if (y1 == 0)
					y = y2;
				else if (y2 == 0)
					y = y1;
				else
					y = 1d - (1d - y1)*(1d - y2);
				yVals[j] = y;
			}
			DiscretizedFunc combCurve = new LightFixedXFunc(xVals, yVals);
			
			double[] mapVals = new double[rps.length];
			
			for (int r=0; r<rps.length; r++) {
				double curveLevel = rps[r].oneYearProb;
				
				double val;
				// curveLevel is a probability, return the IML at that probability
				if (curveLevel > combCurve.getMaxY())
					val = 0d;
				else if (curveLevel < combCurve.getMinY())
					// saturated
					val = combCurve.getMaxX();
				else
					val = combCurve.getFirstInterpolatedX_inLogXLogYDomain(curveLevel);
				mapVals[r] = val;
			}
			
			return new GridLocResult(gridIndex, combCurve, mapVals);
		}
		
	}
	
	private static class WriteCounter {
		private int writable = 0;
		private int written = 0;
		
		public synchronized void incrementWritable(int num) {
			writable += num;
		}
		
		public synchronized void incrementWritable() {
			writable++;
		}
		
		public synchronized void incrementWritten() {
			written++;
			writable--;
		}
		
		public synchronized int getWritable() {
			return writable;
		}
		
		public synchronized int getWritten() {
			return written;
		}
	}

}
