package org.opensha.sha.earthquake.faultSysSolution.treeCombiners;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
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
import org.opensha.commons.logicTree.treeCombiner.AbstractLogicTreeCombiner;
import org.opensha.commons.logicTree.treeCombiner.AbstractLogicTreeCombiner.LogicTreeCombinationContext;
import org.opensha.commons.logicTree.treeCombiner.LogicTreeCombinationProcessor;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.hazard.LogicTreeCurveAverager;
import org.opensha.sha.earthquake.faultSysSolution.hazard.LogicTreeHazardCompare;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_LogicTreeHazardCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

public class HazardMapCombinationProcessor implements LogicTreeCombinationProcessor {
	
	// inputs
	private MapCurveLoader outerHazardMapLoader;
	private final IncludeBackgroundOption outerBGOp;
	private MapCurveLoader innerHazardMapLoader;
	private final IncludeBackgroundOption innerBGOp;
	private final File outputHazardFile;
	private final GriddedRegion gridReg;

	private double[] periods;
	private final ReturnPeriods[] rps;
	
	private boolean preloadInnerCurves = true;

	// data
	private Map<LogicTreeBranch<?>, BranchCurves> innerCurvesMap = null;
	private File hazardZipOutFinal = null;
	private File hazardOutDir;
	private CompletableFuture<Void> writeFuture = null;
	private LogicTreeCurveAverager[] meanCurves = null;
	private String outerHazardSubDirName = null;
	private String innerHazardSubDirName = null;
	private String outputHazardSubDirName = null;
	private int readDequeSize;
	
	private LogicTreeBranch<?> prevOuter;
	private DiscretizedFunc[][] prevOuterCurves = null;
	
	private ArrayDeque<Future<BranchCurves>> outerCurveLoadFutures = null;
	private ArrayDeque<Integer> outerCurveLoadIndexes = null;
	private ArrayDeque<Future<BranchCurves>> innerCurveLoadFutures = null;
	private ArrayDeque<Integer> innerCurveLoadCombinedIndexes = null;
	private List<Future<?>> curveWriteFutures;
	private List<CompletableFuture<Void>> perAvgFutures = null;
	private int outerCurveLoadIndex = -1;
	private LogicTreeCombinationContext treeCombination;
	private ExecutorService exec;
	private ExecutorService ioExec;
	
	private ArchiveOutput hazardOutZip;
	private WriteCounter writeCounter;
	private Stopwatch combineWatch;
	private Stopwatch mapStringWatch;
	private Stopwatch blockingZipIOWatch;
	private Stopwatch curveReadWatch;
	private Stopwatch curveWriteWatch;
	private Stopwatch blockingAvgWatch;

	public HazardMapCombinationProcessor(File outerHazardMapDir, IncludeBackgroundOption outerBGOp,
			File innerHazardMapDir, IncludeBackgroundOption innerBGOp, File outputHazardFile,
			GriddedRegion gridReg) {
		this(defaultMapCurveLoader(outerHazardMapDir), outerBGOp, defaultMapCurveLoader(innerHazardMapDir), innerBGOp,
				outputHazardFile, gridReg);
	}

	public HazardMapCombinationProcessor(MapCurveLoader outerHazardMapLoader, IncludeBackgroundOption outerBGOp,
			MapCurveLoader innerHazardMapLoader, IncludeBackgroundOption innerBGOp, File outputHazardFile,
			GriddedRegion gridReg) {
		this(outerHazardMapLoader, outerBGOp, innerHazardMapLoader, innerBGOp, outputHazardFile, gridReg,
				null, SolHazardMapCalc.MAP_RPS);
	}

	public HazardMapCombinationProcessor(MapCurveLoader outerHazardMapLoader, IncludeBackgroundOption outerBGOp,
			MapCurveLoader innerHazardMapLoader, IncludeBackgroundOption innerBGOp, File outputHazardFile,
			GriddedRegion gridReg, double[] periods, ReturnPeriods[] rps) {
		super();
		this.outerHazardMapLoader = outerHazardMapLoader;
		this.outerBGOp = outerBGOp;
		this.innerHazardMapLoader = innerHazardMapLoader;
		this.innerBGOp = innerBGOp;
		this.outputHazardFile = outputHazardFile;
		this.gridReg = gridReg;
		this.periods = periods;
		this.rps = rps;
	}

	@Override
	public String getName() {
		return "Hazard maps";
	}
	
	public void setPreloadInnerCurves(boolean preloadInnerCurves) {
		this.preloadInnerCurves = preloadInnerCurves;
	}

	@Override
	public void init(LogicTreeCombinationContext treeCombination,
			ExecutorService exec, ExecutorService ioExec) throws IOException {
		this.treeCombination = treeCombination;
		this.exec = exec;
		this.ioExec = ioExec;
		outerHazardSubDirName = getHazardDirName(gridReg.getSpacing(), outerBGOp);
		innerHazardSubDirName = getHazardDirName(gridReg.getSpacing(), innerBGOp);
		IncludeBackgroundOption outBGOp = outerBGOp;
		if (innerBGOp == IncludeBackgroundOption.INCLUDE)
			outBGOp = IncludeBackgroundOption.INCLUDE;
		else if (innerBGOp == IncludeBackgroundOption.EXCLUDE && outBGOp == IncludeBackgroundOption.ONLY)
			outBGOp = IncludeBackgroundOption.INCLUDE;
		else if (outerBGOp == IncludeBackgroundOption.EXCLUDE && innerBGOp == IncludeBackgroundOption.ONLY)
			outBGOp = IncludeBackgroundOption.INCLUDE;
		outputHazardSubDirName = getHazardDirName(gridReg.getSpacing(), outBGOp);

		if (outerHazardMapLoader instanceof FileBasedCurveLoader && innerHazardMapLoader instanceof FileBasedCurveLoader) {
			// detected periods
			ArchiveInput innerInput = ArchiveInput.getDefaultInput(((FileBasedCurveLoader)innerHazardMapLoader).hazardDir);
			ArchiveInput outerInput = ArchiveInput.getDefaultInput(((FileBasedCurveLoader)outerHazardMapLoader).hazardDir);
			if (periods == null)
				periods = LogicTreeHazardCompare.detectHazardPeriods(rps, innerInput, outerInput);
			innerInput.close();
			outerInput.close();
		}
		if (periods == null)
			periods = MPJ_LogicTreeHazardCalc.PERIODS_DEFAULT;

		if (preloadInnerCurves && treeCombination.numPairwiseSamples < 1) {
			System.out.println("Pre-loading inner curves");
			innerCurvesMap = new HashMap<>(treeCombination.innerTree.size());
			for (int b=0; b<treeCombination.innerTree.size(); b++) {
				LogicTreeBranch<?> innerBranch = treeCombination.innerTree.getBranch(b);
				System.out.println("Pre-loading curves for inner branch "+b+"/"+treeCombination.innerTree.size()+": "+innerBranch);
				innerCurvesMap.put(innerBranch, curveLoadFuture(innerHazardMapLoader, innerHazardSubDirName, innerBranch, periods).join());
			}
		}

		if (!outputHazardFile.getName().toLowerCase().endsWith(".zip")) {
			hazardZipOutFinal = new File(outputHazardFile.getAbsolutePath()+".zip");
			hazardOutDir = outputHazardFile;
			Preconditions.checkState(hazardOutDir.exists() && hazardOutDir.isDirectory() || hazardOutDir.mkdir());
		} else {
			hazardOutDir = null;
			hazardZipOutFinal = outputHazardFile;
		}
		meanCurves = new LogicTreeCurveAverager[periods.length];
		HashSet<LogicTreeNode> variableNodes = new HashSet<>();
		HashMap<LogicTreeNode, LogicTreeLevel<?>> nodeLevels = new HashMap<>();
		LogicTreeCurveAverager.populateVariableNodes(treeCombination.outerTree, variableNodes, nodeLevels,
				treeCombination.outerLevelRemaps, treeCombination.outerNodeRemaps);
		LogicTreeCurveAverager.populateVariableNodes(treeCombination.innerTree, variableNodes, nodeLevels,
				treeCombination.innerLevelRemaps, treeCombination.innerNodeRemaps);
		for (int p=0; p<periods.length; p++)
			meanCurves[p] = new LogicTreeCurveAverager(gridReg.getNodeList(), variableNodes, nodeLevels);

		readDequeSize = Integer.max(5, Integer.min(20, FaultSysTools.defaultNumThreads()));
		
		if (!treeCombination.averageAcrossLevels.isEmpty()) {
			if (treeCombination.preAveragingInnerTree != treeCombination.innerTree)
				innerHazardMapLoader = new AveragedMapCurveLoader(treeCombination.preAveragingInnerTree, innerHazardMapLoader, ioExec);
			if (treeCombination.preAveragingOuterTree != treeCombination.outerTree)
				outerHazardMapLoader = new AveragedMapCurveLoader(treeCombination.preAveragingOuterTree, outerHazardMapLoader, ioExec);
			readDequeSize = 2;
		}
		
		outerCurveLoadFutures = new ArrayDeque<>(readDequeSize);
		outerCurveLoadIndexes = new ArrayDeque<>(readDequeSize);
		for (int i=0; i<treeCombination.combBranchesOuterIndexes.size() && outerCurveLoadFutures.size()<readDequeSize; i++) {
			int outerIndex = treeCombination.combBranchesOuterIndexes.get(i);
			outerCurveLoadIndex = i;
			int prevOutlerIndex = outerCurveLoadIndexes.isEmpty() ? -1 : outerCurveLoadIndexes.getLast();
			if (prevOutlerIndex < 0 || 
					(prevOutlerIndex != outerIndex
					// the latter can happen if the outer tree is resampled with duplicates
					&& !treeCombination.outerTree.getBranch(prevOutlerIndex).equals(treeCombination.outerTree.getBranch(outerIndex)))) {
				outerCurveLoadFutures.add(curveLoadFuture(outerHazardMapLoader, outerHazardSubDirName,
						treeCombination.outerTree.getBranch(outerIndex), periods, ioExec));
				outerCurveLoadIndexes.add(outerIndex);
				System.out.println("Adding outer read future for "+outerIndex);
			}
		}
		if (innerCurvesMap == null) {
			// need to do the inner curves as well
			innerCurveLoadFutures = new ArrayDeque<>(readDequeSize);
			innerCurveLoadCombinedIndexes = new ArrayDeque<>(readDequeSize);
			for (int i=0; i<readDequeSize && i<treeCombination.combTree.size(); i++) {
				innerCurveLoadFutures.add(curveLoadFuture(innerHazardMapLoader, innerHazardSubDirName,
						treeCombination.combBranchesInnerPortion.get(i), periods, ioExec));
				innerCurveLoadCombinedIndexes.add(i);
			}
		}
		
		int writeThreads = Integer.max(2, Integer.min(16, FaultSysTools.defaultNumThreads())); // at least 2, no more than 16
		hazardOutZip = new ArchiveOutput.ParallelZipFileOutput(hazardZipOutFinal, writeThreads, false);
		writeCounter = new WriteCounter();
		
		combineWatch = Stopwatch.createUnstarted();
		mapStringWatch = Stopwatch.createUnstarted();
		blockingZipIOWatch = Stopwatch.createUnstarted();
		curveReadWatch = Stopwatch.createUnstarted();
		curveWriteWatch = hazardOutDir != null ? Stopwatch.createUnstarted() : null;
		blockingAvgWatch = Stopwatch.createUnstarted();
	}

	@Override
	public void processBranch(LogicTreeBranch<?> combBranch, int combBranchIndex, double combBranchWeight,
			LogicTreeBranch<?> outerBranch, int outerBranchIndex, LogicTreeBranch<?> innerBranch,
			int innerBranchIndex) throws IOException {
		DiscretizedFunc[][] outerCurves;
		if (prevOuter == null || !outerBranch.equals(prevOuter)) {
			curveReadWatch.start();
			System.out.println("Reading outer curves for branch "+combBranchIndex+", outerIndex="+outerBranchIndex);
			int nextOuterIndex = outerCurveLoadIndexes.removeFirst();
			Preconditions.checkState(nextOuterIndex == outerBranchIndex,
					"Future outerIndex=%s, we need %s", nextOuterIndex, outerBranchIndex);
			try {
				BranchCurves outerBranchCurves = outerCurveLoadFutures.removeFirst().get();
				Preconditions.checkState(outerBranch.equals(outerBranchCurves.branch),
						"Curve load mismatch for outer %s; expected %s, was %s",
						outerBranchIndex, outerBranch, outerBranchCurves.branch);
				outerCurves = outerBranchCurves.curves;
			} catch (InterruptedException | ExecutionException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			for (int i=outerCurveLoadIndex+1; i<treeCombination.combBranchesOuterIndexes.size() && outerCurveLoadFutures.size()<readDequeSize; i++) {
				int nextSubmitOuterIndex = this.treeCombination.combBranchesOuterIndexes.get(i);
				outerCurveLoadIndex = i;
				int prevOutlerIndex = outerCurveLoadIndexes.isEmpty() ? -1 : outerCurveLoadIndexes.getLast();
				if (prevOutlerIndex < 0 || 
						(prevOutlerIndex != nextSubmitOuterIndex
						// the latter can happen if the outer tree is resampled with duplicates
						&& !treeCombination.outerTree.getBranch(prevOutlerIndex).equals(treeCombination.outerTree.getBranch(nextSubmitOuterIndex)))) {
					System.out.println("Adding outer read future for "+nextSubmitOuterIndex);
					outerCurveLoadFutures.add(curveLoadFuture(outerHazardMapLoader, outerHazardSubDirName,
							treeCombination.outerTree.getBranch(nextSubmitOuterIndex), periods, ioExec));
					outerCurveLoadIndexes.add(nextSubmitOuterIndex);
				}
			}
			curveReadWatch.stop();
			
			if (perAvgFutures != null && !perAvgFutures.isEmpty()) {
				System.out.println("Waiting on "+perAvgFutures.size()+" curve averaging futures...");
				// can wait on these later after we've finished writing
				blockingAvgWatch.start();
				for (CompletableFuture<Void> future : perAvgFutures)
					future.join();
				blockingAvgWatch.stop();
				if (curveWriteFutures != null) {
					System.out.println("Waiting on "+curveWriteFutures.size()+" curve write futures");
					curveWriteWatch.start();
					for (Future<?> future : curveWriteFutures) {
						try {
							future.get();
						} catch (InterruptedException | ExecutionException e) {
							ExceptionUtils.throwAsRuntimeException(e);
						}
					}
					curveWriteWatch.stop();
				}
			}
			
			perAvgFutures = new ArrayList<>();
			if (hazardOutDir != null)
				curveWriteFutures = new ArrayList<>();
			
			prevOuter = outerBranch;
			prevOuterCurves = outerCurves;
		} else {
			outerCurves = prevOuterCurves;
		}
		
		BranchCurves innerBranchCurves;
		if (innerCurvesMap != null) {
			innerBranchCurves = innerCurvesMap.get(innerBranch);
		} else {
			curveReadWatch.start();
//			innerCurves = nextInnerCurveLoadFuture.join();
			try {
				int loadInnerIndex = innerCurveLoadCombinedIndexes.removeFirst();
				Preconditions.checkState(loadInnerIndex == combBranchIndex,
						"Load inner index was %s but I'm on branch %s", loadInnerIndex, combBranchIndex);
				innerBranchCurves = innerCurveLoadFutures.removeFirst().get();
			} catch (InterruptedException | ExecutionException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			curveReadWatch.stop();
			
//			for (int i=0; i<readDequeSize && i<combTree.size(); i++)
//				innerCurveLoadFutures.add(curveLoadFuture(innerHazardMapLoader, innerHazardSubDirName,
//						combBranchesInnerPortion.get(i), periods, curveIOExec));
			if (innerCurveLoadCombinedIndexes.isEmpty()) {
				Preconditions.checkState(outerBranchIndex == treeCombination.combTree.size()-1, "No load indexes left, but not on last branch: %s", outerBranchIndex);
			} else {
				int lastRunningInnerIndex = innerCurveLoadCombinedIndexes.getLast();
				for (int m=lastRunningInnerIndex+1; m<treeCombination.combTree.size() && innerCurveLoadFutures.size() < readDequeSize; m++) {
					innerCurveLoadFutures.add(curveLoadFuture(innerHazardMapLoader, innerHazardSubDirName,
							treeCombination.combBranchesInnerPortion.get(m), periods, ioExec));
					innerCurveLoadCombinedIndexes.add(m);
				}
			}
		}
		Preconditions.checkState(innerBranch.equals(innerBranchCurves.branch),
				"Curve load mismatch for inner %s; expected %s, was %s",
				innerBranchIndex, innerBranch, innerBranchCurves.branch);
		DiscretizedFunc[][] innerCurves = innerBranchCurves.curves;
		
		Map<String, GriddedGeoDataSet> writeMap = new HashMap<>(gridReg.getNodeCount()*periods.length);
		
		File branchHazardOutDir;
		if (hazardOutDir == null) {
			branchHazardOutDir = null;
		} else {
			File subDir = combBranch.getBranchDirectory(hazardOutDir, true);
			branchHazardOutDir = new File(subDir, outputHazardSubDirName);
			Preconditions.checkState(branchHazardOutDir.exists() || branchHazardOutDir.mkdir(),
					"Directory doesn't exist and couldn't be created: %s", subDir.getAbsolutePath());
		}

		combineWatch.start();
		for (int p=0; p<periods.length; p++) {
			Preconditions.checkState(outerCurves[p].length == gridReg.getNodeCount(),
					"Expected %s locations but have %s", gridReg.getNodeCount(), outerCurves[p].length);
			Preconditions.checkState(outerCurves[p].length == innerCurves[p].length,
					"Outer curves have %s locs but inner curves have %s", outerCurves[p].length, innerCurves[p].length);
			double[] xVals = new double[outerCurves[p][0].size()];
			for (int i=0; i<xVals.length; i++)
				xVals[i] = outerCurves[p][0].getX(i);
			
			List<Future<CurveCombineResult>> futures = new ArrayList<>();
			
			for (int i=0; i<outerCurves[p].length; i++)
				futures.add(exec.submit(new CurveCombineCallable(i, xVals, outerCurves[p][i], innerCurves[p][i], rps)));
			
			DiscretizedFunc[] combCurves = new DiscretizedFunc[innerCurves[p].length];
			GriddedGeoDataSet[] xyzs = new GriddedGeoDataSet[rps.length];
			for (int r=0; r<rps.length; r++)
				xyzs[r] = new GriddedGeoDataSet(gridReg, false);
			
			try {
				for (Future<CurveCombineResult> future : futures) {
					CurveCombineResult result = future.get();
					
					combCurves[result.index] = result.combCurve;
					for (int r=0; r<rps.length; r++)
						xyzs[r].set(result.index, result.mapVals[r]);
				}
			} catch (ExecutionException | InterruptedException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			String combZipPrefix = combBranch.getBranchZipPath();
			for (int r=0; r<rps.length; r++) {
				String mapFileName = MPJ_LogicTreeHazardCalc.mapPrefix(periods[p], rps[r])+".txt";
				String entryName = combZipPrefix+"/"+mapFileName;
				
				Preconditions.checkState(writeMap.put(entryName, xyzs[r]) == null,
						"Duplicate entry? %s", entryName);
			}
			
			if (hazardOutDir != null) {
				// write curves
				String fileName = SolHazardMapCalc.getCSV_FileName("curves", periods[p]);
				File csvFile = new File(branchHazardOutDir, fileName+".gz");
				curveWriteFutures.add(ioExec.submit(new Runnable() {
					
					@Override
					public void run() {
						try {
							SolHazardMapCalc.writeCurvesCSV(csvFile, combCurves, gridReg.getNodeList());
						} catch (IOException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
					}
				}));
			}
			
			final LogicTreeCurveAverager averager = meanCurves[p];
			perAvgFutures.add(CompletableFuture.runAsync(new Runnable() {
						
				@Override
				public void run() {
					averager.processBranchCurves(combBranch, combBranchWeight, combCurves);
				}
			}, exec));
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
			blockingZipIOWatch.start();
			writeFuture.join();
			blockingZipIOWatch.stop();
//			double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
//			System.out.println("\tDone writing branch "+combBranchIndex+" ("+(float)(treeCombination.combBranches.size()/secs)+" /s)");
			System.out.println("\tDone writing branch "+combBranchIndex);
		}
		
		// everything should have been written now
		int writable = writeCounter.getWritable();
		Preconditions.checkState(writable == 0, "Have %s writable maps after join? written=%s",
				writable, writeCounter.getWritten());
		
		int expected = periods.length*rps.length;
		Preconditions.checkState(writeMap.size() == expected,
				"Expected %s writable maps, have %s", expected, writeMap.size());
		
		writeCounter.incrementWritable(expected);
		
		System.out.println("Writing combined branch "+combBranchIndex+"/"+treeCombination.combBranches.size()+": "+combBranch);
		
		System.out.println("\tWriting "+writeCounter.getWritable()+" new maps, "+writeCounter.getWritten()+" written so far");
		
		writeFuture = CompletableFuture.runAsync(new Runnable() {
			
			@Override
			public void run() {
				try {
					for (String entryName : mapStringBytes.keySet()) {
						byte[] mapBytes = mapStringBytes.get(entryName);
						
						hazardOutZip.putNextEntry(entryName);
						OutputStream out = hazardOutZip.getOutputStream();
						out.write(mapBytes);
						hazardOutZip.closeEntry();
						if (hazardOutDir != null) {
							File outFile = new File(branchHazardOutDir, entryName.substring(entryName.lastIndexOf('/')));
							FileUtils.writeByteArrayToFile(outFile, mapBytes);
						}
						writeCounter.incrementWritten();
					}
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(1);
				}
			}
		}, ioExec);
	}

	@Override
	public void close() throws IOException {
		System.out.println("Finalizing hazard map zip file");
		blockingAvgWatch.start();
		for (CompletableFuture<Void> future : perAvgFutures)
			future.join();
		blockingAvgWatch.stop();
		if (curveWriteFutures != null) {
			System.out.println("Waiting on "+curveWriteFutures.size()+" curve write futures");
			blockingZipIOWatch.start();
			curveWriteWatch.start();
			for (Future<?> future : curveWriteFutures) {
				try {
					future.get();
				} catch (InterruptedException | ExecutionException e) {
					ExceptionUtils.throwAsRuntimeException(e);
				}
			}
			blockingZipIOWatch.stop();
			curveWriteWatch.stop();
		}
		
		blockingZipIOWatch.start();
		// write mean curves and maps
		MPJ_LogicTreeHazardCalc.writeMeanCurvesAndMaps(hazardOutZip, meanCurves, gridReg, periods, rps);
		
		// write gridded region
		hazardOutZip.putNextEntry(MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME);
		Feature gridFeature = gridReg.toFeature();
		Feature.write(gridFeature, new OutputStreamWriter(hazardOutZip.getOutputStream()));
		hazardOutZip.closeEntry();
		
		// write logic tree
		treeCombination.combTree.writeToArchive(hazardOutZip, null);
		
		hazardOutZip.close();
		blockingZipIOWatch.stop();
		
		System.out.println("Wrote "+writeCounter.getWritten()+" maps in total");
	}

	protected String getHazardDirName(double gridSpacing, IncludeBackgroundOption bgOp) {
		return "hazard_"+(float)gridSpacing+"deg_grid_seis_"+bgOp.name();
	}
	
	private static DiscretizedFunc[][] loadCurves(File hazardDir, double[] periods, GriddedRegion region) throws IOException {
		DiscretizedFunc[][] curves = new DiscretizedFunc[periods.length][];
		for (int p=0; p<periods.length; p++) {
			String fileName = SolHazardMapCalc.getCSV_FileName("curves", periods[p]);
			File csvFile = new File(hazardDir, fileName);
			if (!csvFile.exists())
				csvFile = new File(hazardDir, fileName+".gz");
			Preconditions.checkState(csvFile.exists(), "Curves CSV file not found: %s", csvFile.getAbsolutePath());
			try {
				CSVFile<String> csv = CSVFile.readFile(csvFile, true);
				curves[p] = SolHazardMapCalc.loadCurvesCSV(csv, region);
			} catch (Exception e) {
				throw new RuntimeException("Failed to load curves from "+csvFile.getAbsolutePath(), e);
			}
		}
		return curves;
	}
	
	protected static MapCurveLoader defaultMapCurveLoader(File dir) {
		if (dir == null)
			return null;
		return new FileBasedCurveLoader(dir);
	}
	
	public static interface MapCurveLoader {
		public DiscretizedFunc[][] loadCurves(String hazardSubDirName,
				LogicTreeBranch<?> branch, double[] periods) throws IOException;
		
		public default BranchCurves getCurveBranchResult(String hazardSubDirName,
				LogicTreeBranch<?> branch, double[] periods) throws IOException {
			return new BranchCurves(loadCurves(hazardSubDirName, branch, periods), branch);
		}
	}
	
	public static class FileBasedCurveLoader implements MapCurveLoader {
		
		private File hazardDir;

		public FileBasedCurveLoader(File hazardDir) {
			this.hazardDir = hazardDir;
		}

		@Override
		public DiscretizedFunc[][] loadCurves(String hazardSubDirName, LogicTreeBranch<?> branch, double[] periods) throws IOException {
			File branchResultsDir = branch.getBranchDirectory(hazardDir, true);
			File branchHazardDir = new File(branchResultsDir, hazardSubDirName);
			Preconditions.checkState(branchHazardDir.exists(), "%s doesn't exist", branchHazardDir.getAbsolutePath());
			return HazardMapCombinationProcessor.loadCurves(branchHazardDir, periods, null);
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
	
	private static class AveragedMapCurveLoader implements MapCurveLoader {
		
		private LogicTree<?> origTree;
		private MapCurveLoader origLoader;
		private ExecutorService exec;

		public AveragedMapCurveLoader(LogicTree<?> origTree, MapCurveLoader origLoader, ExecutorService exec) {
			this.origTree = origTree;
			this.origLoader = origLoader;
			this.exec = exec;
		}
		
		@Override
		public DiscretizedFunc[][] loadCurves(String hazardSubDirName, LogicTreeBranch<?> branch,
				double[] periods) throws IOException {
			double sumWeight = 0d;
			List<Future<BranchCurves>> loadFutures = new ArrayList<>();
			List<Double> weights = new ArrayList<>();
			for (LogicTreeBranch<?> origBranch : origTree) {
				boolean match = true;
				for (LogicTreeNode node : branch) {
					if (!origBranch.hasValue(node)) {
						match = false;
						break;
					}
				}
				if (match) {
					double weight = origTree.getBranchWeight(origBranch);
					sumWeight += weight;
					weights.add(weight);
					loadFutures.add(curveLoadFuture(origLoader, hazardSubDirName, origBranch, periods, exec));
				}
			}
			System.out.println("Waiting on "+loadFutures.size()+" load futures to average curves for "+branch);
			DiscretizedFunc[][] avgCurves = null;
			for (int i=0; i<loadFutures.size(); i++) {
				DiscretizedFunc[][] curves;
				try {
					curves = loadFutures.get(i).get().curves;
				} catch (InterruptedException | ExecutionException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				double weight = weights.get(i)/sumWeight;
				if (avgCurves == null)
					avgCurves = new DiscretizedFunc[curves.length][];
				else
					Preconditions.checkState(curves.length == avgCurves.length);
				for (int j=0; j<curves.length; j++) {
					if (avgCurves[j] == null)
						avgCurves[j] = new DiscretizedFunc[curves[j].length];
					else
						Preconditions.checkState(curves[j].length == avgCurves[j].length);
					for (int k=0; k<curves[j].length; k++) {
						if (avgCurves[j][k] == null) {
							double[] xVals = new double[curves[j][k].size()];
							for (int l=0; l<xVals.length; l++)
								xVals[l] = curves[j][k].getX(l);
							avgCurves[j][k] = new LightFixedXFunc(xVals, new double[xVals.length]);
						} else {
							Preconditions.checkState(avgCurves[j][k].size() == curves[j][k].size());
						}
						for (int l=0; l<avgCurves[j][k].size(); l++)
							avgCurves[j][k].set(l, avgCurves[j][k].getY(l) + weight*curves[j][k].getY(l));
					}
				}
			}
			return avgCurves;
		}
		
	}
	
	private static CompletableFuture<BranchCurves> curveLoadFuture(MapCurveLoader loader, String hazardSubDirName,
			LogicTreeBranch<?> branch, double[] periods) {
		return CompletableFuture.supplyAsync(new Supplier<BranchCurves>() {

			@Override
			public BranchCurves get() {
				try {
					return loader.getCurveBranchResult(hazardSubDirName, branch, periods);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		});
	}
	
	private static class BranchCurves {
		public final DiscretizedFunc[][] curves;
		public final LogicTreeBranch<?> branch;
		private BranchCurves(DiscretizedFunc[][] curves, LogicTreeBranch<?> branch) {
			super();
			this.curves = curves;
			this.branch = branch;
		}
	}
	
	private static Future<BranchCurves> curveLoadFuture(MapCurveLoader loader, String hazardSubDirName,
			LogicTreeBranch<?> branch, double[] periods, ExecutorService exec) {
		return exec.submit(new Callable<BranchCurves>() {

			@Override
			public BranchCurves call() {
				try {
					return loader.getCurveBranchResult(hazardSubDirName, branch, periods);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		});
	}
	
	public static class CurveCombineResult {
		public final int index;
		public final DiscretizedFunc combCurve;
		public final double[] mapVals;
		
		public CurveCombineResult(int index, DiscretizedFunc combCurve, double[] mapVals) {
			super();
			this.index = index;
			this.combCurve = combCurve;
			this.mapVals = mapVals;
		}
	}
	
	public static class CurveCombineCallable implements Callable<CurveCombineResult> {

		private int gridIndex;
		private double[] xVals;
		private DiscretizedFunc outerCurve;
		private DiscretizedFunc innerCurve;
		private ReturnPeriods[] rps;

		public CurveCombineCallable(int gridIndex, double[] xVals, DiscretizedFunc outerCurve, DiscretizedFunc innerCurve,
				ReturnPeriods[] rps) {
			this.gridIndex = gridIndex;
			this.xVals = xVals;
			this.outerCurve = outerCurve;
			this.innerCurve = innerCurve;
			this.rps = rps;
		}

		@Override
		public CurveCombineResult call() throws Exception {
			Preconditions.checkState(outerCurve.size() == xVals.length);
			Preconditions.checkState(innerCurve.size() == xVals.length);
			double[] yVals = new double[xVals.length];
			for (int j=0; j<outerCurve.size(); j++) {
				double x = outerCurve.getX(j);
				Preconditions.checkState((float)x == (float)innerCurve.getX(j));
				double y1 = outerCurve.getY(j);
				double y2 = innerCurve.getY(j);
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

			double[] mapVals;
			if (rps != null) {
				mapVals = new double[rps.length];
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
			} else {
				mapVals = new double[0];
			}
			
			return new CurveCombineResult(gridIndex, combCurve, mapVals);
		}
		
	}

	@Override
	public String getTimeBreakdownString(Stopwatch overallWatch) {
		String ret = "Combining:\t"+AbstractLogicTreeCombiner.blockingTimePrint(combineWatch, overallWatch);
		ret += ";\tMap bytes:\t"+AbstractLogicTreeCombiner.blockingTimePrint(mapStringWatch, overallWatch);
		ret += ";\tBlocking curve read I/O:\t"+AbstractLogicTreeCombiner.blockingTimePrint(curveReadWatch, overallWatch);
		if (curveWriteWatch != null)
			ret += ";\tBlocking curve write I/O:\t"+AbstractLogicTreeCombiner.blockingTimePrint(curveWriteWatch, overallWatch);
		ret += ";\tBlocking Zip I/O:\t"+AbstractLogicTreeCombiner.blockingTimePrint(blockingZipIOWatch, overallWatch);
		ret += ";\tBlocking Averaging:\t"+AbstractLogicTreeCombiner.blockingTimePrint(blockingAvgWatch, overallWatch);
		return ret;
	}

}
