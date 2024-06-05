package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
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

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_LogicTreeHazardCalc;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.io.Files;

public abstract class AbstractLogicTreeHazardCombiner {
	
	private SolutionLogicTree outerSLT;
	private File outerHazardDir;
	private IncludeBackgroundOption outerBGOp;
	private SolutionLogicTree innerSLT;
	private File innerHazardDir;
	private IncludeBackgroundOption innerBGOp;
	private File outputSLTFile;
	private File outputHazardFile;
	private GriddedRegion gridReg;
	
	private Map<LogicTreeLevel<?>, LogicTreeLevel<?>> outerLevelRemaps;
	private Map<LogicTreeNode, LogicTreeNode> outerNodeRemaps;
	private Map<LogicTreeLevel<?>, LogicTreeLevel<?>> innerLevelRemaps;
	private Map<LogicTreeNode, LogicTreeNode> innerNodeRemaps;
	
	private List<LogicTreeLevel<? extends LogicTreeNode>> combLevels;
	private List<LogicTreeBranch<LogicTreeNode>> combBranches;
	private List<LogicTreeBranch<?>> combBranchesOuterPortion;
	private List<LogicTreeBranch<?>> combBranchesInnerPortion;
	
	private LogicTree<?> outerTree;
	private LogicTree<?> innerTree;
	private LogicTree<LogicTreeNode> combTree;
	
	private double[] periods = MPJ_LogicTreeHazardCalc.PERIODS_DEFAULT;
	private ReturnPeriods[] rps = SolHazardMapCalc.MAP_RPS;
	
	public AbstractLogicTreeHazardCombiner(
			SolutionLogicTree outerSLT,
			File outerHazardDir,
			IncludeBackgroundOption outerBGOp,
			SolutionLogicTree innerSLT,
			File innerHazardDir,
			IncludeBackgroundOption innerBGOp,
			File outputSLTFile,
			File outputHazardFile,
			GriddedRegion gridReg) {
		this.outerSLT = outerSLT;
		this.outerHazardDir = outerHazardDir;
		this.outerBGOp = outerBGOp;
		this.innerSLT = innerSLT;
		this.innerHazardDir = innerHazardDir;
		this.innerBGOp = innerBGOp;
		this.outputSLTFile = outputSLTFile;
		this.outputHazardFile = outputHazardFile;
		this.gridReg = gridReg;
		
		System.out.println("Remapping outer logic tree levels");
		outerLevelRemaps = new HashMap<>();
		outerNodeRemaps = new HashMap<>();
		remapOuterTree(outerSLT.getLogicTree(), outerLevelRemaps, outerNodeRemaps);
		System.out.println("Remapping inner logic tree levels");
		innerLevelRemaps = new HashMap<>();
		innerNodeRemaps = new HashMap<>();
		remapInnerTree(innerSLT.getLogicTree(), innerLevelRemaps, innerNodeRemaps);
		
		outerTree = outerSLT.getLogicTree();
		innerTree = innerSLT.getLogicTree();
		
		combLevels = new ArrayList<>();
		boolean[] outerSkips = new boolean[outerTree.getLevels().size()];
		for (int l=0; l<outerSkips.length; l++) {
			LogicTreeLevel<?> level = outerTree.getLevels().get(l);
			outerSkips[l] = canSkipLevel(level, false);
			if (outerLevelRemaps.containsKey(level))
				level = outerLevelRemaps.get(level);
			if (!outerSkips[l])
				combLevels.add(level);
		}
		boolean[] innerSkips = new boolean[innerTree.getLevels().size()];
		for (int l=0; l<innerSkips.length; l++) {
			LogicTreeLevel<?> level = innerTree.getLevels().get(l);
			innerSkips[l] = canSkipLevel(level, true);
			if (innerLevelRemaps.containsKey(level))
				level = innerLevelRemaps.get(level);
			if (!innerSkips[l])
				combLevels.add(level);
		}
		
		System.out.println("Combined levels:");
		for (LogicTreeLevel<?> level : combLevels)
			System.out.println(level.getName()+" ("+level.getShortName()+")");
		
		int expectedNum = outerTree.size() * innerTree.size();
		System.out.println("Total number of combinations: "+expectedNum);
		combBranches = new ArrayList<>(expectedNum);
		combBranchesOuterPortion = new ArrayList<>(expectedNum);
		combBranchesInnerPortion = new ArrayList<>(expectedNum);
		for (int o=0; o<outerTree.size(); o++) {
			LogicTreeBranch<?> outerBranch = outerTree.getBranch(o);
			for (int i=0; i<innerTree.size(); i++) {
				LogicTreeBranch<?> innerBranch = innerTree.getBranch(i);
				double weight = outerTree.getBranchWeight(o) * innerTree.getBranchWeight(i);
				
				LogicTreeBranch<LogicTreeNode> combBranch = new LogicTreeBranch<>(combLevels);
				int combNodeIndex = 0;
				for (int l=0; l<outerBranch.size(); l++) {
					if (!outerSkips[l]) {
						LogicTreeNode node = outerBranch.getValue(l);
						if (outerNodeRemaps.containsKey(node))
							node = outerNodeRemaps.get(node);
						combBranch.setValue(node);
						LogicTreeNode getNode = combBranch.getValue(combNodeIndex);
						Preconditions.checkState(getNode == node,
								"Set didn't work for node %s of combined branch: %s, has %s",
								combNodeIndex, node, getNode);
						combNodeIndex++;
					}
				}
				for (int l=0; l<innerBranch.size(); l++) {
					if (!innerSkips[l]) {
						LogicTreeNode node = innerBranch.getValue(l);
						if (innerNodeRemaps.containsKey(node))
							node = innerNodeRemaps.get(node);
						combBranch.setValue(node);
						LogicTreeNode getNode = combBranch.getValue(combNodeIndex);
						Preconditions.checkState(getNode == node,
								"Set didn't work for node %s of combined branch: %s, has %s",
								combNodeIndex, node, getNode);
						combNodeIndex++;
					}
				}
				combBranch.setOrigBranchWeight(weight);
				
				combBranches.add(combBranch);
				combBranchesOuterPortion.add(outerBranch);
				combBranchesInnerPortion.add(innerBranch);
			}
		}
		Preconditions.checkState(combBranches.size() == expectedNum);
		
		combTree = LogicTree.fromExisting(combLevels, combBranches);
		combTree.setWeightProvider(new BranchWeightProvider.OriginalWeights());
	}
	
	protected abstract void remapOuterTree(LogicTree<?> tree, Map<LogicTreeLevel<?>, LogicTreeLevel<?>> levelRemaps,
			Map<LogicTreeNode, LogicTreeNode> nodeRemaps);
	
	protected abstract void remapInnerTree(LogicTree<?> tree, Map<LogicTreeLevel<?>, LogicTreeLevel<?>> levelRemaps,
			Map<LogicTreeNode, LogicTreeNode> nodeRemaps);
	
	protected abstract boolean doesOuterSupplySols();
	
	protected abstract boolean doesInnerSupplySols();
	
	protected abstract boolean isSerializeGridded();
	
	protected abstract boolean canSkipLevel(LogicTreeLevel<?> level, boolean inner);
	
	public LogicTree<?> getOuterTree() {
		return outerTree;
	}

	public LogicTree<?> getInnerTree() {
		return innerTree;
	}

	public LogicTree<LogicTreeNode> getCombTree() {
		return combTree;
	}

	public void sampleTree(int maxNumCombinations) {
		sampleTree(maxNumCombinations, (long)combBranches.size()*(long)maxNumCombinations);
	}
	
	public void sampleTree(int maxNumCombinations, long randSeed) {
		System.out.println("Samping down to "+maxNumCombinations+" samples");
		combTree = combTree.sample(maxNumCombinations, true, new Random(randSeed));
		
		List<LogicTreeBranch<LogicTreeNode>> modCombBranches = new ArrayList<>(maxNumCombinations);
		List<LogicTreeBranch<?>> modCombBranchesOuterPortion = new ArrayList<>(maxNumCombinations);
		List<LogicTreeBranch<?>> modCombBranchesInnerPortion = new ArrayList<>(maxNumCombinations);
		
		for (int i=0; i<combBranches.size(); i++) {
			LogicTreeBranch<LogicTreeNode> combBranch = combBranches.get(i);
			if (combTree.contains(combBranch)) {
				// this one was retained
				modCombBranches.add(combBranch);
				modCombBranchesOuterPortion.add(combBranchesOuterPortion.get(i));
				modCombBranchesInnerPortion.add(combBranchesInnerPortion.get(i));
			}
		}
		this.combBranches = modCombBranches;
		this.combBranchesOuterPortion = modCombBranchesOuterPortion;
		this.combBranchesInnerPortion = modCombBranchesInnerPortion;
	}
	
	protected String getHazardDirName(double gridSpacing, IncludeBackgroundOption bgOp) {
		return "hazard_"+(float)gridSpacing+"deg_grid_seis_"+bgOp.name();
	}
	
	public void build() throws IOException {
		String outerHazardSubDirName = getHazardDirName(gridReg.getSpacing(), outerBGOp);
		String innerHazardSubDirName = getHazardDirName(gridReg.getSpacing(), innerBGOp);
		
		boolean doHazard = outputHazardFile != null;
		Map<LogicTreeBranch<?>, DiscretizedFunc[][]> innerCurvesMap = null;
		File hazardOutTmp = null;
		CompletableFuture<Void> writeFuture = null;
		LogicTreeCurveAverager[] meanCurves = null;
		if (doHazard) {
			System.out.println("Pre-loading inner curves");
			innerCurvesMap = new HashMap<>(innerTree.size());
			for (LogicTreeBranch<?> innerBranch : innerTree) {
				System.out.println("Pre-loading curves for inner branch "+innerBranch);
				File branchResultsDir = new File(innerHazardDir, innerBranch.buildFileName());
				Preconditions.checkState(branchResultsDir.exists(), "%s doesn't exist", branchResultsDir.getAbsolutePath());
				File branchHazardDir = new File(branchResultsDir, innerHazardSubDirName);
				Preconditions.checkState(branchHazardDir.exists(), "%s doesn't exist", branchHazardDir.getAbsolutePath());

				innerCurvesMap.put(innerBranch, loadCurves(branchHazardDir, periods, gridReg));
			}
			
			hazardOutTmp = new File(outputHazardFile.getAbsolutePath()+".tmp");
			meanCurves = new LogicTreeCurveAverager[periods.length];
			HashSet<LogicTreeNode> variableNodes = new HashSet<>();
			HashMap<LogicTreeNode, LogicTreeLevel<?>> nodeLevels = new HashMap<>();
			LogicTreeCurveAverager.populateVariableNodes(outerTree, variableNodes, nodeLevels, outerLevelRemaps, outerNodeRemaps);
			LogicTreeCurveAverager.populateVariableNodes(innerTree, variableNodes, nodeLevels, innerLevelRemaps, innerNodeRemaps);
			for (int p=0; p<periods.length; p++)
				meanCurves[p] = new LogicTreeCurveAverager(gridReg.getNodeList(), variableNodes, nodeLevels);
		}
		ZipOutputStream hazardOutZip = doHazard ? new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(hazardOutTmp))) : null;
		WriteCounter writeCounter = doHazard ? new WriteCounter() : null;
		
		boolean doSLT = outputSLTFile != null;
		Map<LogicTreeBranch<?>, FaultSystemSolution> innerSols = null;
		SolutionLogicTree.FileBuilder combTreeWriter = doSLT ? new SolutionLogicTree.FileBuilder(outputSLTFile) : null;
		if (doSLT) {
			System.out.println("Pre-loading "+innerTree.size()+" inner solutions");
			innerSols = new HashMap<>();
			for (LogicTreeBranch<?> branch : innerTree)
				innerSols.put(branch, innerSLT.forBranch(branch));
			
			combTreeWriter.setSerializeGridded(isSerializeGridded());
			combTreeWriter.setWeightProv(new BranchWeightProvider.OriginalWeights());
		}
		
		int expectedNum = combTree.size();
		
		CompletableFuture<DiscretizedFunc[][]> nextOuterCurveLoadFuture = null;
		if (doHazard)
			nextOuterCurveLoadFuture = curveLoadFuture(outerHazardDir, outerHazardSubDirName,
					combBranchesOuterPortion.get(0), periods);
		CompletableFuture<FaultSystemSolution> nextOuterSolLoadFuture = null;
		if (doSLT && doesOuterSupplySols())
			nextOuterSolLoadFuture = solLoadFuture(outerSLT, combBranchesOuterPortion.get(0));
		
		LogicTreeBranch<?> prevOuter = null;
		DiscretizedFunc[][] prevOuterCurves = null;
		FaultSystemSolution prevOuterSol = null;
		
		CompletableFuture<Void> combineSLTFuture = null;
		
		List<CompletableFuture<Void>> perAvgFutures = null;
		
		int numOutersProcessed = 0;
		
		ExecutorService exec = Executors.newFixedThreadPool(FaultSysTools.defaultNumThreads());
		
		Stopwatch watch = Stopwatch.createStarted();
		
		Stopwatch combineWatch = Stopwatch.createUnstarted();
		Stopwatch mapStringWatch = Stopwatch.createUnstarted();
		Stopwatch blockingIOWatch = Stopwatch.createUnstarted();
		Stopwatch blockingAvgWatch = Stopwatch.createUnstarted();
		
		for (int n=0; n<combTree.size(); n++) {
			System.out.println("Processing branch "+n+"/"+expectedNum);
			LogicTreeBranch<LogicTreeNode> combBranch = combBranches.get(n);
			double combWeight = combBranch.getOrigBranchWeight();
			LogicTreeBranch<?> innerBranch = combBranchesInnerPortion.get(n);
			LogicTreeBranch<?> outerBranch = combBranchesOuterPortion.get(n);
			
			DiscretizedFunc[][] outerCurves = null;
			FaultSystemSolution outerSol = null;
			
			if (prevOuter == null || outerBranch != prevOuter) {
				// new outer branch
				if (doHazard)
					outerCurves = nextOuterCurveLoadFuture.join();
				if (doSLT && doesOuterSupplySols())
					outerSol = nextOuterSolLoadFuture.join();
				
				// start the next async read
				for (int m=n+1; m<combTree.size(); m++) {
					LogicTreeBranch<?> nextOuter = combBranchesOuterPortion.get(m);
					if (nextOuter != outerBranch) {
						if (doHazard)
							nextOuterCurveLoadFuture = curveLoadFuture(outerHazardDir, outerHazardSubDirName, nextOuter, periods);
						if (doSLT && doesOuterSupplySols())
							nextOuterSolLoadFuture = solLoadFuture(outerSLT, nextOuter);
						break;
					}
				}
				
				if (n > 0) {
					System.out.println("Waiting on "+perAvgFutures.size()+" curve averaging futures...");
					// can wait on these later after we've finished writing
					blockingAvgWatch.start();
					for (CompletableFuture<Void> future : perAvgFutures)
						future.join();
					blockingAvgWatch.stop();
					System.out.println("DONE outer branch "+numOutersProcessed+"/"+outerTree.size());
					System.out.println("\tTotal time combining:\t"+blockingTimePrint(combineWatch, watch));
					System.out.println("\tTotal on map file rep:\t"+blockingTimePrint(mapStringWatch, watch));
					System.out.println("\tTotal blocking I/O:\t"+blockingTimePrint(blockingIOWatch, watch));
					System.out.println("\tTotal blocking Averaging:\t"+blockingTimePrint(blockingAvgWatch, watch));
					double totSecs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
					double secsEach = totSecs / (numOutersProcessed+1);
					double secsLeft = secsEach*(outerTree.size()-(numOutersProcessed+1));
					double minsLeft = secsLeft/60d;
					double hoursLeft = minsLeft/60d;
					if (minsLeft > 90)
						System.out.println("\tEstimated time left: "+twoDigits.format(hoursLeft)+" hours");
					else if (secsLeft > 90)
						System.out.println("\tEstimated time left: "+twoDigits.format(minsLeft)+" mins");
					else
						System.out.println("\tEstimated time left: "+twoDigits.format(secsLeft)+" secs");
					
					numOutersProcessed++;
				}
				
				perAvgFutures = new ArrayList<>();
			} else {
				// reuse
				outerSol = prevOuterSol;
				outerCurves = prevOuterCurves;
			}
			
			if (doSLT) {
				if (combineSLTFuture != null)
					combineSLTFuture.join();
				Preconditions.checkState(doesOuterSupplySols() || doesInnerSupplySols());
				FaultSystemSolution myOuterSol = outerSol;
				combineSLTFuture = CompletableFuture.runAsync(new Runnable() {
					
					@Override
					public void run() {
						try {
							FaultSystemSolution innerSol = doesInnerSupplySols() ? innerSLT.forBranch(innerBranch) : null;
							FaultSystemSolution combSol;
							if (innerSol != null && myOuterSol != null)
								combSol = combineSols(myOuterSol, innerSol);
							else if (innerSol != null)
								combSol = innerSol;
							else
								combSol = myOuterSol;
							combTreeWriter.solution(combSol, combBranch);
						} catch (IOException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
					}
				});
			}
			
			if (doHazard) {
				combineWatch.start();
				
				DiscretizedFunc[][] innerCurves = innerCurvesMap.get(innerBranch);
				
				Map<String, GriddedGeoDataSet> writeMap = new HashMap<>(gridReg.getNodeCount()*periods.length);
				
				String combPrefix = combBranch.buildFileName();
				
				for (int p=0; p<periods.length; p++) {
					Preconditions.checkState(outerCurves[p].length == innerCurves[p].length);
					Preconditions.checkState(outerCurves[p].length == gridReg.getNodeCount());
					double[] xVals = new double[outerCurves[p][0].size()];
					for (int i=0; i<xVals.length; i++)
						xVals[i] = outerCurves[p][0].getX(i);
					
					List<Future<GridLocResult>> futures = new ArrayList<>();
					
					for (int i=0; i<outerCurves[p].length; i++)
						futures.add(exec.submit(new ProcessCallable(i, xVals, outerCurves[p][i], innerCurves[p][i], rps)));
					
					DiscretizedFunc[] combCurves = new DiscretizedFunc[innerCurves[p].length];
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
							averager.processBranchCurves(combBranch, combWeight, combCurves);
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
					System.out.println("\tDone writing branch "+n+" ("+(float)(combBranches.size()/secs)+" /s)");
				}
				
				// everything should have been written now
				int writable = writeCounter.getWritable();
				Preconditions.checkState(writable == 0, "Have %s writable maps after join? written=%s",
						writable, writeCounter.getWritten());
				
				int expected = periods.length*rps.length;
				Preconditions.checkState(writeMap.size() == expected,
						"Expected $s writeable maps, have %s", expected, writeMap.size());
				
				writeCounter.incrementWritable(expected);
				
				System.out.println("Writing combined branch "+combBranches.size()+": "+combBranch);
				
				System.out.println("\tWriting "+writeCounter.getWritable()+" new maps, "+writeCounter.getWritten()+" written so far");
				
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
			
			prevOuter = outerBranch;
			prevOuterCurves = outerCurves;
			prevOuterSol = outerSol;
		}
		
		exec.shutdown();
		
		if (writeFuture != null)
			writeFuture.join();
		
		watch.stop();
		double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		
		System.out.println("Wrote for "+combBranches.size()+" branches ("+(float)(combBranches.size()/secs)+" /s)");
		System.out.println("Wrote "+writeCounter.getWritten()+" maps in total");
		
		if (doSLT) {
			combineSLTFuture.join();
			combTreeWriter.build();
//			LogicTree<?> combTree = combSLT.getLogicTree();
		}
		
		if (doHazard) {
			blockingAvgWatch.start();
			for (CompletableFuture<Void> future : perAvgFutures)
				future.join();
			blockingAvgWatch.stop();
			
			// write mean curves and maps
			MPJ_LogicTreeHazardCalc.writeMeanCurvesAndMaps(hazardOutZip, meanCurves, gridReg, periods, rps);
			
			hazardOutZip.putNextEntry(new ZipEntry(MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME));
			Feature gridFeature = gridReg.toFeature();
			Feature.write(gridFeature, new OutputStreamWriter(hazardOutZip));
			hazardOutZip.closeEntry();
			
			hazardOutZip.close();
			Files.move(hazardOutTmp, outputHazardFile);
		}
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
	
	private static CompletableFuture<DiscretizedFunc[][]> curveLoadFuture(File hazardDir, String hazardSubDirName,
			LogicTreeBranch<?> faultBranch, double[] periods) {
		File branchResultsDir = new File(hazardDir, faultBranch.buildFileName());
		Preconditions.checkState(branchResultsDir.exists(), "%s doesn't exist", branchResultsDir.getAbsolutePath());
		File branchHazardDir = new File(branchResultsDir, hazardSubDirName);
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
	
	private static CompletableFuture<FaultSystemSolution> solLoadFuture(SolutionLogicTree slt, LogicTreeBranch<?> branch) {
		return CompletableFuture.supplyAsync(new Supplier<FaultSystemSolution>() {

			@Override
			public FaultSystemSolution get() {
				try {
					return slt.forBranch(branch);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		});
	}
	
	public static FaultSystemSolution combineSols(FaultSystemSolution innerlSol, FaultSystemSolution outerSol) {
		return combineSols(innerlSol, outerSol, false);
	}
	
	public static FaultSystemSolution combineSols(FaultSystemSolution innerlSol, FaultSystemSolution outerSol, boolean clusterRups) {
		List<FaultSystemSolution> sols = List.of(innerlSol, outerSol);
		
		int totNumSects = 0;
		int totNumRups = 0;
		for (int i=0; i<sols.size(); i++) {
			FaultSystemSolution sol = sols.get(i);
			FaultSystemRupSet rupSet = sol.getRupSet();
//			System.out.println("RupSet "+i+" has "+rupSet.getNumSections()+" sects, "+rupSet.getNumRuptures()+" rups");
			totNumSects += rupSet.getNumSections();
			totNumRups += rupSet.getNumRuptures();
		}
//		System.out.println("Total: "+totNumSects+" sects, "+totNumRups+" rups");
		
		List<FaultSection> mergedSects = new ArrayList<>(totNumSects);
		List<List<Integer>> sectionForRups = new ArrayList<>(totNumSects);
		double[] mags = new double[totNumRups];
		double[] rakes = new double[totNumRups];
		double[] rupAreas = new double[totNumRups];
		double[] rupLengths = new double[totNumRups];
		double[] rates = new double[totNumRups];
		
		int sectIndex = 0;
		int rupIndex = 0;
		
		List<ClusterRupture> cRups = clusterRups ? new ArrayList<>() : null;
		
		for (FaultSystemSolution sol : sols) {
			FaultSystemRupSet rupSet = sol.getRupSet();
			int[] sectMappings = new int[rupSet.getNumSections()];
//			System.out.println("Merging sol with "+rupSet.getNumSections()+" sects and "+rupSet.getNumRuptures()+" rups");
			for (int s=0; s<sectMappings.length; s++) {
				FaultSection sect = rupSet.getFaultSectionData(s);
				sect = sect.clone();
				sectMappings[s] = sectIndex;
				sect.setSectionId(sectIndex);
				mergedSects.add(sect);
				
				sectIndex++;
			}
			
			ClusterRuptures myCrups = null;
			if (clusterRups)
				myCrups = rupSet.requireModule(ClusterRuptures.class);
			
			for (int r=0; r<rupSet.getNumRuptures(); r++) {
				List<Integer> prevSectIDs = rupSet.getSectionsIndicesForRup(r);
				List<Integer> newSectIDs = new ArrayList<>(prevSectIDs.size());
				for (int s : prevSectIDs)
					newSectIDs.add(sectMappings[s]);
				sectionForRups.add(newSectIDs);
				mags[rupIndex] = rupSet.getMagForRup(r);
				rakes[rupIndex] = rupSet.getAveRakeForRup(r);
				rupAreas[rupIndex] = rupSet.getAreaForRup(r);
				rupAreas[rupIndex] = rupSet.getLengthForRup(r);
				rates[rupIndex] = sol.getRateForRup(r);
				
				if (clusterRups)
					cRups.add(myCrups.get(r));
				
				rupIndex++;
			}
		}
		
		FaultSystemRupSet mergedRupSet = new FaultSystemRupSet(mergedSects, sectionForRups, mags, rakes, rupAreas, rupLengths);
		if (clusterRups)
			mergedRupSet.addModule(ClusterRuptures.instance(mergedRupSet, cRups, false));
		return new FaultSystemSolution(mergedRupSet, rates);
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
}
