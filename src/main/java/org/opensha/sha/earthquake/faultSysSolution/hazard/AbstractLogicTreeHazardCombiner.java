package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.awt.geom.Point2D;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.stream.Streams;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.ExecutorUtils;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_LogicTreeHazardCalc;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_SiteLogicTreeHazardCurveCalc;
import org.opensha.sha.earthquake.faultSysSolution.modules.AbstractLogicTreeModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupSetTectonicRegimes;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public abstract class AbstractLogicTreeHazardCombiner {
	
	// inputs for all
	private LogicTree<?> outerTree;
	private LogicTree<?> innerTree;
	
	// inputs for hazard maps
	private MapCurveLoader outerHazardMapLoader;
	private IncludeBackgroundOption outerBGOp;
	private MapCurveLoader innerHazardMapLoader;
	private IncludeBackgroundOption innerBGOp;
	private File outputHazardFile;
	private GriddedRegion gridReg;
	
	// inputs for hazard curves
	private File outerHazardCurvesFile;
	private File innerHazardCurvesFile;
	private File hazardCurvesOutputFile;
	
	// inputs for solution logic trees
	private SolutionLogicTree outerSLT;
	private SolutionLogicTree innerSLT;
	private File outputSLTFile;
	
	// results and intermediates
	private Map<LogicTreeLevel<?>, LogicTreeLevel<?>> outerLevelRemaps;
	private Map<LogicTreeNode, LogicTreeNode> outerNodeRemaps;
	private Map<LogicTreeLevel<?>, LogicTreeLevel<?>> innerLevelRemaps;
	private Map<LogicTreeNode, LogicTreeNode> innerNodeRemaps;
	
	private List<LogicTreeLevel<? extends LogicTreeNode>> combLevels;
	private int expectedNum;
	private List<LogicTreeLevel<? extends LogicTreeNode>> commonLevels;
	private List<LogicTreeLevel<? extends LogicTreeNode>> averageAcrossLevels;
	private Map<LogicTreeBranch<LogicTreeNode>, LogicTree<?>> commonSubtrees;
	private List<LogicTreeBranch<LogicTreeNode>> combBranches;
	private List<Integer> combBranchesOuterIndexes;
	private List<LogicTreeBranch<?>> combBranchesOuterPortion;
	private List<Integer> combBranchesInnerIndexes;
	private List<LogicTreeBranch<?>> combBranchesInnerPortion;
	
	private int numPairwiseSamples = 0;
	private Random pairwiseSampleRand;
	
	private LogicTree<LogicTreeNode> combTree;
	
	private boolean preloadInnerCurves = true;
	
	// parameters
	private double[] periods = MPJ_LogicTreeHazardCalc.PERIODS_DEFAULT;
	private ReturnPeriods[] rps = SolHazardMapCalc.MAP_RPS;
	
	private LogicTree<?> origOuterTree = null;
	private LogicTree<?> origInnerTree = null;
	
	public AbstractLogicTreeHazardCombiner(LogicTree<?> outerLT, LogicTree<?> innerLT) {
		this(outerLT, innerLT, null, null);
	}
	
	public AbstractLogicTreeHazardCombiner(LogicTree<?> outerLT, LogicTree<?> innerLT,
			List<LogicTreeLevel<? extends LogicTreeNode>> commonLevels, List<LogicTreeLevel<?>> averageAcrossLevels) {
		System.out.println("Remapping outer logic tree levels");
		outerLevelRemaps = new HashMap<>();
		outerNodeRemaps = new HashMap<>();
		remapOuterTree(outerLT, outerLevelRemaps, outerNodeRemaps);
		System.out.println("Remapping inner logic tree levels");
		innerLevelRemaps = new HashMap<>();
		innerNodeRemaps = new HashMap<>();
		remapInnerTree(innerLT, innerLevelRemaps, innerNodeRemaps);
		
		outerTree = outerLT;
		innerTree = innerLT;
		
		int innerTreeSize = innerTree.size();
		if (commonLevels == null) {
			commonLevels = List.of();
		} else if (!commonLevels.isEmpty()) {
			// make sure none of these common levels were remapped
			Preconditions.checkState(commonLevels.size() < outerTree.getLevels().size(),
					"At least one level of the outer tree must be unique.");
			Preconditions.checkState(commonLevels.size() < innerTree.getLevels().size(),
					"At least one level of the inner tree must be unique.");
			for (LogicTreeLevel<?> level : commonLevels) {
				Preconditions.checkState(!outerLevelRemaps.containsKey(level) || outerLevelRemaps.get(level).equals(level),
						"Outer remaps include a common level: %s", level.getName());
				Preconditions.checkState(!innerLevelRemaps.containsKey(level) || innerLevelRemaps.get(level).equals(level),
						"Inner remaps include a common level: %s", level.getName());
				Preconditions.checkState(outerTree.getLevels().contains(level),
						"Outer tree doesn't contain level %s, but it's a common level?", level.getName());
				Preconditions.checkState(innerTree.getLevels().contains(level),
						"Inner tree doesn't contain level %s, but it's a common level?", level.getName());
			}
			commonSubtrees = new HashMap<>();
			for (LogicTreeBranch<?> outerBranch : outerTree) {
				List<LogicTreeNode> commonNodes = new ArrayList<>(commonLevels.size());
				for (LogicTreeLevel<?> level : commonLevels) {
					LogicTreeNode node = outerBranch.requireValue(level.getType());
					Preconditions.checkNotNull(node);
					commonNodes.add(node);
				}
				LogicTreeBranch<LogicTreeNode> commonBranch = new LogicTreeBranch<>(commonLevels, commonNodes);
				if (!commonSubtrees.containsKey(commonBranch)) {
					// new common branch
					LogicTreeNode[] matches = commonNodes.toArray(new LogicTreeNode[commonNodes.size()]);
					LogicTree<?> subtree = innerTree.matchingAll(matches);
					Preconditions.checkState(subtree.size() > 0,
							"Inner tree doesn't have any branches with these common values from the outer tree: %s", commonNodes);
					innerTreeSize = subtree.size();
					commonSubtrees.put(commonBranch, subtree);
				}
			}
		}
		if (averageAcrossLevels == null) {
			averageAcrossLevels = List.of();
		} else if (!averageAcrossLevels.isEmpty()) {
			// remove average across levels from each tree
			System.out.println("Averaging across "+averageAcrossLevels.size()+" levels");
			origOuterTree = outerTree;
			origInnerTree = innerTree;
			outerTree = removeAveragedOutLevels(outerTree, averageAcrossLevels);
			innerTree = removeAveragedOutLevels(innerTree, averageAcrossLevels);
			System.out.println("Reduced outerTree from "+origOuterTree.size()+" to "+outerTree.size()+" branches");
			System.out.println("Reduced innerTree from "+origInnerTree.size()+" to "+innerTree.size()+" branches");
			Preconditions.checkState(origOuterTree != outerTree || origInnerTree != innerTree);
		}
		combLevels = new ArrayList<>();
		for (LogicTreeLevel<?> level : outerTree.getLevels()) {
			if (averageAcrossLevels.contains(level))
				continue;
			if (outerLevelRemaps.containsKey(level))
				level = outerLevelRemaps.get(level);
			combLevels.add(level);
		}
		for (LogicTreeLevel<?> level : innerTree.getLevels()) {
			if (averageAcrossLevels.contains(level))
				continue;
			if (innerLevelRemaps.containsKey(level))
				level = innerLevelRemaps.get(level);
			if (!commonLevels.contains(level))
				// make sure this isn't common to both trees
				combLevels.add(level);
		}
		
		System.out.println("Combined levels:");
		for (LogicTreeLevel<?> level : combLevels)
			System.out.println(level.getName()+" ("+level.getShortName()+")");
		
		expectedNum = outerTree.size() * innerTreeSize;
		System.out.println("Total number of combinations: "+countDF.format(expectedNum));
		
		this.commonLevels = commonLevels;
		this.averageAcrossLevels = averageAcrossLevels;
	}
	
	private static LogicTree<?> removeAveragedOutLevels(LogicTree<?> tree, List<LogicTreeLevel<? extends LogicTreeNode>> averageAcrossLevels) {
		List<LogicTreeLevel<? extends LogicTreeNode>> retainedLevels = new ArrayList<>();
		for (LogicTreeLevel<?> level : tree.getLevels())
			if (!averageAcrossLevels.contains(level))
				retainedLevels.add(level);
		Preconditions.checkState(!retainedLevels.isEmpty());
		if (retainedLevels.size() == tree.getLevels().size())
			return tree;
		Map<LogicTreeBranch<LogicTreeNode>, Integer> retainedBranchesMap = new HashMap<>();
		List<LogicTreeBranch<LogicTreeNode>> retainedBranches = new ArrayList<>();
		List<Double> retainedWeights = new ArrayList<>();
		for (LogicTreeBranch<?> branch : tree) {
			LogicTreeBranch<LogicTreeNode> retainedBranch = new LogicTreeBranch<>(retainedLevels);
			for (LogicTreeLevel<?> level : retainedLevels)
				retainedBranch.setValue(branch.getValue(level.getType()));
			for (int l=0; l<branch.size(); l++)
				Preconditions.checkNotNull(branch.getValue(l));
			Integer prevIndex = retainedBranchesMap.get(retainedBranch);
			if (prevIndex == null) {
				retainedBranchesMap.put(retainedBranch, retainedBranches.size());
				retainedBranches.add(retainedBranch);
				retainedWeights.add(tree.getBranchWeight(branch));
			} else {
				// duplicate
				retainedWeights.set(prevIndex, retainedWeights.get(prevIndex) + tree.getBranchWeight(branch));
			}
		}
		
		// set weights
		for (int i=0; i<retainedBranches.size(); i++)
			retainedBranches.get(i).setOrigBranchWeight(retainedWeights.get(i));
		
		LogicTree<LogicTreeNode> ret = LogicTree.fromExisting(retainedLevels, retainedBranches);
		ret.setWeightProvider(new BranchWeightProvider.OriginalWeights());
		return ret;
	}
	
	public void setCombineHazardMaps(File outerHazardMapDir, IncludeBackgroundOption outerBGOp,
			File innerHazardMapDir, IncludeBackgroundOption innerBGOp, File outputFile,
			GriddedRegion gridReg) {
		setCombineHazardMaps(defaultMapCurveLoader(outerHazardMapDir), outerBGOp,
				defaultMapCurveLoader(innerHazardMapDir), innerBGOp, outputFile, gridReg);
	}
	
	protected static MapCurveLoader defaultMapCurveLoader(File dir) {
		if (dir == null)
			return null;
		return new FileBasedCurveLoader(dir);
	}
	
	public void setCombineHazardMaps(MapCurveLoader outerHazardMapLoader, IncludeBackgroundOption outerBGOp,
			MapCurveLoader innerHazardMapLoader, IncludeBackgroundOption innerBGOp, File outputFile,
			GriddedRegion gridReg) {
		this.outerHazardMapLoader = outerHazardMapLoader;
		this.outerBGOp = outerBGOp;
		this.innerHazardMapLoader = innerHazardMapLoader;
		this.innerBGOp = innerBGOp;
		this.outputHazardFile = outputFile;
		this.gridReg = gridReg;
	}
	
	public void setWriteSLTs(SolutionLogicTree outerSLT, SolutionLogicTree innerSLT, File outputFile) {
		this.outerSLT = outerSLT;
		this.innerSLT = innerSLT;
		this.outputSLTFile = outputFile;
	}
	
	public void setCombineHazardCurves(File outerHazardCurvesFile, File innerHazardCurvesFile, File outputFile) {
		this.outerHazardCurvesFile = outerHazardCurvesFile;
		this.innerHazardCurvesFile = innerHazardCurvesFile;
		this.hazardCurvesOutputFile = outputFile;
	}
	
	protected abstract void remapOuterTree(LogicTree<?> tree, Map<LogicTreeLevel<?>, LogicTreeLevel<?>> levelRemaps,
			Map<LogicTreeNode, LogicTreeNode> nodeRemaps);
	
	protected abstract void remapInnerTree(LogicTree<?> tree, Map<LogicTreeLevel<?>, LogicTreeLevel<?>> levelRemaps,
			Map<LogicTreeNode, LogicTreeNode> nodeRemaps);
	
	protected abstract boolean doesOuterSupplySols();
	
	protected abstract boolean doesInnerSupplySols();
	
	protected abstract boolean isSerializeGridded();
	
	public LogicTree<?> getOuterTree() {
		return outerTree;
	}

	public LogicTree<?> getInnerTree() {
		return innerTree;
	}

	public LogicTree<LogicTreeNode> getCombTree() {
		if (combTree == null)
			buildCominedTree();
		return combTree;
	}
	
	private void buildCominedTree() {
		System.out.println("Building combined tree");
		if (commonLevels.isEmpty()) {
			combBranches = new ArrayList<>(expectedNum);
			combBranchesOuterPortion = new ArrayList<>(expectedNum);
			combBranchesInnerPortion = new ArrayList<>(expectedNum);
			combBranchesOuterIndexes = new ArrayList<>(expectedNum);
			combBranchesInnerIndexes = new ArrayList<>(expectedNum);
		} else {
			combBranches = new ArrayList<>();
			combBranchesOuterPortion = new ArrayList<>();
			combBranchesInnerPortion = new ArrayList<>();
			combBranchesOuterIndexes = new ArrayList<>();
			combBranchesInnerIndexes = new ArrayList<>();
		}
		
		boolean pairwise = numPairwiseSamples > 0;
		Table<LogicTreeBranch<?>, LogicTreeBranch<?>, Integer> prevPairs = null;
		Map<LogicTreeBranch<?>, Integer> outerSampleCounts = null;
		Map<LogicTreeBranch<?>, Double> outerTotalWeights = null;
		List<Integer> branchSampleCounts = null;
		int numPairDuplicates = 0;
		if (pairwise) {
			prevPairs = HashBasedTable.create();
			outerSampleCounts = new HashMap<>();
			outerTotalWeights = new HashMap<>();
			if (commonLevels.isEmpty())
				branchSampleCounts = new ArrayList<>(expectedNum);
			else
				branchSampleCounts = new ArrayList<>();
		}
		
		int printMod = 100;
		
		Map<LogicTreeBranch<?>, Integer> innerBranchIndexes = new HashMap<>();
		for (int i=0; i<innerTree.size(); i++)
			innerBranchIndexes.put(innerTree.getBranch(i), i);
		
		for (int o=0; o<outerTree.size(); o++) {
			LogicTreeBranch<?> outerBranch = outerTree.getBranch(o);
			LogicTree<?> matchingInnerTree;
			if (commonSubtrees == null) {
				matchingInnerTree = innerTree;
			} else {
				List<LogicTreeNode> commonNodes = new ArrayList<>();
				for (LogicTreeLevel<?> level : commonLevels)
					commonNodes.add(outerBranch.requireValue(level.getType()));
				LogicTreeBranch<LogicTreeNode> commonBranch = new LogicTreeBranch<>(commonLevels, commonNodes);
				matchingInnerTree = commonSubtrees.get(commonBranch);
				Preconditions.checkNotNull(matchingInnerTree);
			}
			int numInner = numPairwiseSamples > 0 ? numPairwiseSamples : matchingInnerTree.size();
			for (int i=0; i<numInner; i++) {
				LogicTreeBranch<?> innerBranch;
				double weight;
				if (pairwise) {
					// sample an inner branch
					IntegerPDF_FunctionSampler sampler = matchingInnerTree.getSampler();
					innerBranch = matchingInnerTree.getBranch(matchingInnerTree.getSampler().getRandomInt(pairwiseSampleRand));
					int prevOuterSampleCount = outerSampleCounts.containsKey(outerBranch) ? outerSampleCounts.get(outerBranch) : 0;
					while (prevPairs.contains(outerBranch, innerBranch)) {
						// duplicate
						int prevIndex = prevPairs.get(outerBranch, innerBranch);
						// register that we sampled this one again
						branchSampleCounts.set(prevIndex, branchSampleCounts.get(prevIndex)+1);
						// also register that we have an extra sample of the outer branch
						prevOuterSampleCount++;
						// now resample the inner branch
						innerBranch = matchingInnerTree.getBranch(sampler.getRandomInt(pairwiseSampleRand));
						numPairDuplicates++;
					}
					prevPairs.put(outerBranch, innerBranch, combBranches.size());
					outerSampleCounts.put(outerBranch, prevOuterSampleCount+1);
					double prevOuterWeight = outerTotalWeights.containsKey(outerBranch) ? outerTotalWeights.get(outerBranch) : 0d;
					outerTotalWeights.put(outerBranch, prevOuterWeight + outerTree.getBranchWeight(o));
					// register that this branch has been sampled 1 time
					branchSampleCounts.add(1);
					weight = 1d; // will fill in later
				} else {
					innerBranch = matchingInnerTree.getBranch(i);
					weight = outerTree.getBranchWeight(o) * innerTree.getBranchWeight(i);
				}
				
				LogicTreeBranch<LogicTreeNode> combBranch = new LogicTreeBranch<>(combLevels);
				int combNodeIndex = 0;
				for (int l=0; l<outerBranch.size(); l++) {
					LogicTreeNode node = outerBranch.getValue(l);
					if (outerNodeRemaps.containsKey(node)) {
						LogicTreeNode remappedNode = outerNodeRemaps.get(node);
						if (remappedNode != node) {
							node = remappedNode;
						}
					}
					combBranch.setValue(node);
					LogicTreeNode getNode = combBranch.getValue(combNodeIndex);
					Preconditions.checkState(getNode == node,
							"Set didn't work for node %s of combined branch: %s, has %s",
							combNodeIndex, node, getNode);
					combNodeIndex++;
				}
				for (int l=0; l<innerBranch.size(); l++) {
					if (commonLevels.contains(innerBranch.getLevel(l)))
						// skip common levels (already accounted for in the outer branch)
						continue;
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
				combBranch.setOrigBranchWeight(weight);
				
				combBranches.add(combBranch);
				combBranchesOuterPortion.add(outerBranch);
				combBranchesInnerPortion.add(innerBranch);
				combBranchesOuterIndexes.add(o);
				combBranchesInnerIndexes.add(innerBranchIndexes.get(innerBranch));
				
				int count = combBranches.size();
				if (count % printMod == 0) {
					String str = "\tBuilt "+countDF.format(count)+" branches";
					if (pairwise)
						str += " ("+numPairDuplicates+" pairwise duplicates redrawn)";
					System.out.println(str);
				}
				if (count >= printMod*10 && printMod < 1000)
					printMod *= 10;
			}
		}
		System.out.println("Built "+countDF.format(combBranches.size())+" branches");
		Preconditions.checkState(!commonLevels.isEmpty() || combBranches.size() == expectedNum);
		
		combTree = LogicTree.fromExisting(combLevels, combBranches);
		combTree.setWeightProvider(new BranchWeightProvider.OriginalWeights());
		if (numPairwiseSamples > 0) {
			System.out.println("Pairwise tree with numPairwiseSamples="+numPairwiseSamples
					+", and "+numPairDuplicates+" duplicate pairs encountered");
			// fill in weights
			double sumWeight = 0d;
			for (int i=0; i<combTree.size(); i++) {
				LogicTreeBranch<?> branch = combTree.getBranch(i);
				LogicTreeBranch<?> outerBranch = combBranchesOuterPortion.get(i);
				int branchSamples = branchSampleCounts.get(i);
				int outerTotSamples = outerSampleCounts.get(outerBranch);
				// the total weight (across all instances) allocated to this outer branch
				double outerWeight = outerTotalWeights.get(outerBranch);
				// the fraction of that weight allocated to this inner branch
				double thisBranchFract = (double)branchSamples / (double)outerTotSamples;
				double weight = outerWeight * thisBranchFract;
				branch.setOrigBranchWeight(weight);
				sumWeight += weight;
			}
			// print out sampling stats
			Map<LogicTreeNode, Integer> sampledNodeCounts = new HashMap<>();
			Map<LogicTreeNode, Double> sampledNodeWeights = new HashMap<>();
			for (LogicTreeBranch<?> branch : combBranches) {
				double weight = branch.getOrigBranchWeight()/sumWeight;
				for (LogicTreeNode node : branch) {
					int prevCount = 0;
					double prevWeight = 0d;
					if (sampledNodeCounts.containsKey(node)) {
						prevCount = sampledNodeCounts.get(node);
						prevWeight = sampledNodeWeights.get(node);
					}
					sampledNodeCounts.put(node, prevCount+1);
					sampledNodeWeights.put(node, prevWeight+weight);
				}
			}
			
			Map<LogicTreeNode, Integer> origCombNodeCounts = new HashMap<>();
			Map<LogicTreeNode, Double> origCombNodeWeights = new HashMap<>();
			for (boolean inner : new boolean[] {false,true}) {
				Map<LogicTreeNode, Integer> origNodeCounts = new HashMap<>();
				Map<LogicTreeNode, Double> origNodeWeights = new HashMap<>();
				double totWeight = 0d;
				LogicTree<?> tree = inner ? innerTree : outerTree;
				Map<LogicTreeNode, LogicTreeNode> nodeRemaps = inner ? innerNodeRemaps : outerNodeRemaps;
				for (LogicTreeBranch<?> branch : tree) {
					double weight = tree.getBranchWeight(branch);
					totWeight += weight;
					for (int l=0; l<branch.size(); l++) {
						if (!inner || !commonLevels.contains(branch.getLevel(l))) {
							LogicTreeNode node = branch.getValue(l);
							if (nodeRemaps.containsKey(node))
								node = nodeRemaps.get(node);
							if (origNodeCounts.containsKey(node)) {
								origNodeCounts.put(node, origNodeCounts.get(node) + 1);
								origNodeWeights.put(node, origNodeWeights.get(node) + weight);
							} else {
								origNodeCounts.put(node, 1);
								origNodeWeights.put(node, weight);
							}
						}
					}
				}
				for (LogicTreeNode node : origNodeCounts.keySet()) {
					Preconditions.checkState(!origCombNodeCounts.containsKey(node));
					int count = origNodeCounts.get(node);
					double weight = origNodeWeights.get(node);
					if (totWeight != 1d)
						weight /= totWeight;
					origCombNodeCounts.put(node, count);
					origCombNodeWeights.put(node, weight);
				}
			}
			LogicTree.printSamplingStats(combLevels, sampledNodeCounts, sampledNodeWeights, origCombNodeCounts, origCombNodeWeights);
		}
	}
	
	public void pairwiseSampleTree(int numSamples) {
		pairwiseSampleTree(numSamples, (long)expectedNum*(long)(numSamples == 0 ? outerTree.size() : numSamples));
	}
	
	public void pairwiseSampleTree(int numSamples, long randSeed) {
		pairwiseSampleTree(numSamples, 1, randSeed);
	}
	
	public void pairwiseSampleTree(int numOuterSamples, int numInnerPerOuter, long randSeed) {
		Preconditions.checkState(numInnerPerOuter > 0);
		Preconditions.checkState(combTree == null, "Can't pairwise-sample if tree is already built");
		Preconditions.checkState(numPairwiseSamples == 0, "Can't pairwise-sample twice");
		pairwiseSampleRand = new Random(randSeed);
		if (numOuterSamples != 0 && numOuterSamples != outerTree.size()) {
			// first sample the outer tree
			System.out.println("Pairwise-sampling outer tree to "+numOuterSamples+" samples");
			
			// redraw duplicates if we have almost as many (or more) samples than exist in the outer tree
			boolean redrawDuplicates = numOuterSamples < (int)(0.95*outerTree.size());
			LogicTree<?> sampledOuterTree = outerTree.sample(numOuterSamples, redrawDuplicates, pairwiseSampleRand, false);
			Preconditions.checkState(sampledOuterTree.size() == numOuterSamples,
					"Resampled outer tree from %s to %s, but asked for %s samples",
					outerTree.size(), sampledOuterTree.size(), numOuterSamples);
			this.outerTree = sampledOuterTree;
		}
		System.out.println("Pairwise-sampling inner tree with "+numInnerPerOuter+" samples per outer");
		numPairwiseSamples = numInnerPerOuter;
		expectedNum = numOuterSamples*numInnerPerOuter;
	}

	public void sampleTree(int maxNumCombinations) {
		sampleTree(maxNumCombinations, (long)expectedNum*(long)maxNumCombinations);
	}
	
	public void sampleTree(int maxNumCombinations, long randSeed) {
		if (combTree == null)
			// build it
			buildCominedTree();
		System.out.println("Samping down to "+maxNumCombinations+" samples");
		// keep track of the original indexes
		Map<LogicTreeBranch<?>, Integer> origIndexes = new HashMap<>(combTree.size());
		for (int i=0; i<combTree.size(); i++)
			origIndexes.put(combTree.getBranch(i), i);
		combTree = combTree.sample(maxNumCombinations, true, new Random(randSeed));
		
		// rebuild the lists
		List<LogicTreeBranch<LogicTreeNode>> modCombBranches = new ArrayList<>(maxNumCombinations);
		List<Integer> modCombBranchesOuterIndexes = new ArrayList<>(maxNumCombinations);
		List<LogicTreeBranch<?>> modCombBranchesOuterPortion = new ArrayList<>(maxNumCombinations);
		List<Integer> modCombBranchesInnerIndexes = new ArrayList<>(maxNumCombinations);
		List<LogicTreeBranch<?>> modCombBranchesInnerPortion = new ArrayList<>(maxNumCombinations);
		
		for (LogicTreeBranch<LogicTreeNode> branch : combTree) {
			int origIndex = origIndexes.get(branch);
			modCombBranches.add(branch);
			modCombBranchesOuterIndexes.add(combBranchesOuterIndexes.get(origIndex));
			modCombBranchesOuterPortion.add(combBranchesOuterPortion.get(origIndex));
			modCombBranchesInnerIndexes.add(combBranchesInnerIndexes.get(origIndex));
			modCombBranchesInnerPortion.add(combBranchesInnerPortion.get(origIndex));
		}
		
		this.combBranches = modCombBranches;
		this.combBranchesOuterIndexes = modCombBranchesOuterIndexes;
		this.combBranchesOuterPortion = modCombBranchesOuterPortion;
		this.combBranchesInnerIndexes = modCombBranchesInnerIndexes;
		this.combBranchesInnerPortion = modCombBranchesInnerPortion;
		
		numPairwiseSamples = 0;
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
							for (int l=0; l<avgCurves[j][k].size(); l++)
								avgCurves[j][k].set(l, avgCurves[j][k].getY(l) + weight*curves[j][k].getY(l));
						}
					}
				}
			}
			return avgCurves;
		}
		
	}
	
	protected String getHazardDirName(double gridSpacing, IncludeBackgroundOption bgOp) {
		return "hazard_"+(float)gridSpacing+"deg_grid_seis_"+bgOp.name();
	}
	
	protected void setPreloadInnerCurves(boolean preloadInnerCurves) {
		this.preloadInnerCurves = preloadInnerCurves;
	}
	
	public void build() throws IOException {
		if (combTree == null)
			// build it
			buildCominedTree();
		
		boolean doHazardMaps = outputHazardFile != null;
		boolean doHazardCurves = hazardCurvesOutputFile != null;
		
		ExecutorService curveIOExec = null;
		int ioThreadCount = Integer.min(20, Integer.max(3, FaultSysTools.defaultNumThreads()));
		int readDequeSize = Integer.max(5, ioThreadCount);
		if (doHazardMaps || doHazardCurves)
			curveIOExec = ExecutorUtils.newNamedThreadPool(ioThreadCount, "curveIO");
		
		Map<LogicTreeBranch<?>, BranchCurves> innerCurvesMap = null;
		File hazardZipOutFinal = null;
		File hazardOutDir;
		CompletableFuture<Void> writeFuture = null;
		LogicTreeCurveAverager[] meanCurves = null;
		String outerHazardSubDirName = null;
		String innerHazardSubDirName = null;
		String outputHazardSubDirName = null;
		MapCurveLoader outerHazardMapLoader = this.outerHazardMapLoader;
		MapCurveLoader innerHazardMapLoader = this.innerHazardMapLoader;
		if (doHazardMaps) {
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
			
			if (preloadInnerCurves && numPairwiseSamples < 1) {
				System.out.println("Pre-loading inner curves");
				innerCurvesMap = new HashMap<>(innerTree.size());
				for (int b=0; b<innerTree.size(); b++) {
					LogicTreeBranch<?> innerBranch = innerTree.getBranch(b);
					System.out.println("Pre-loading curves for inner branch "+b+"/"+innerTree.size()+": "+innerBranch);
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
			LogicTreeCurveAverager.populateVariableNodes(outerTree, variableNodes, nodeLevels, outerLevelRemaps, outerNodeRemaps);
			LogicTreeCurveAverager.populateVariableNodes(innerTree, variableNodes, nodeLevels, innerLevelRemaps, innerNodeRemaps);
			for (int p=0; p<periods.length; p++)
				meanCurves[p] = new LogicTreeCurveAverager(gridReg.getNodeList(), variableNodes, nodeLevels);
			
			if (!averageAcrossLevels.isEmpty()) {
				if (origInnerTree != innerTree)
					innerHazardMapLoader = new AveragedMapCurveLoader(origInnerTree, innerHazardMapLoader, curveIOExec);
				if (origOuterTree != outerTree)
					outerHazardMapLoader = new AveragedMapCurveLoader(origOuterTree, outerHazardMapLoader, curveIOExec);
				readDequeSize = 2;
			}
		} else {
			hazardOutDir = null;
		}
		int writeThreads = Integer.max(2, Integer.min(16, FaultSysTools.defaultNumThreads())); // at least 2, no more than 16
		ArchiveOutput hazardOutZip = doHazardMaps ? new ArchiveOutput.ParallelZipFileOutput(hazardZipOutFinal, writeThreads, false) : null;
		WriteCounter writeCounter = doHazardMaps ? new WriteCounter() : null;
		
		CSVFile<String> sitesCSV = null;
		List<Site> sites = null;
		List<List<String>> siteOutNames = null;
		List<List<List<DiscretizedFunc>>> outerSiteCurves = null;
		List<List<List<DiscretizedFunc>>> innerSiteCurves = null;
		List<List<FileWriter>> siteCurveWriters = null;
		List<Double> sitePeriods = null;
		List<double[]> sitePerXVals = null;
		File curveOutDir = null;
		if (doHazardCurves) {
			Preconditions.checkState(averageAcrossLevels.isEmpty(), "Averaging not yet supported");
			ZipFile innerZip = new ZipFile(innerHazardCurvesFile);
			CSVFile<String> innerSitesCSV = CSVFile.readStream(innerZip.getInputStream(
					innerZip.getEntry(MPJ_SiteLogicTreeHazardCurveCalc.SITES_CSV_FILE_NAME)), true);
			
			sites = MPJ_SiteLogicTreeHazardCurveCalc.parseSitesCSV(innerSitesCSV, null);
			System.out.println("Loaded "+sites.size()+" hazard curve sites");
			
			ZipFile outerZip = new ZipFile(outerHazardCurvesFile);
			sitesCSV = CSVFile.readStream(outerZip.getInputStream(
					outerZip.getEntry(MPJ_SiteLogicTreeHazardCurveCalc.SITES_CSV_FILE_NAME)), true);
			
			List<Site> outerSites = MPJ_SiteLogicTreeHazardCurveCalc.parseSitesCSV(sitesCSV, null);
			Preconditions.checkState(sites.size() == outerSites.size(),
					"Inner hazard has %s sites and outer has %s", sites.size(), outerSites.size());
			outerSiteCurves = new ArrayList<>(sites.size());
			innerSiteCurves = new ArrayList<>(sites.size());
			siteOutNames = new ArrayList<>(sites.size());
			for (int s=0; s<sites.size(); s++) {
				Site site = sites.get(s);
				Preconditions.checkState(LocationUtils.areSimilar(site.getLocation(), outerSites.get(s).getLocation()));
				String sitePrefix = site.getName().replaceAll("\\W+", "_");
				
				System.out.println("Pre-loading site hazard curves for site "+s+"/"+sites.size()+": "+site.getName());
				
				Map<Double, ZipEntry> outerPerEntries = SiteLogicTreeHazardPageGen.locateSiteCurveCSVs(sitePrefix, outerZip);
				Preconditions.checkState(!outerPerEntries.isEmpty());
				Map<Double, ZipEntry> innerPerEntries = SiteLogicTreeHazardPageGen.locateSiteCurveCSVs(sitePrefix, innerZip);
				Preconditions.checkState(!innerPerEntries.isEmpty());
				Preconditions.checkState(outerPerEntries.size() == innerPerEntries.size());
				
				if (sitePeriods == null) {
					sitePeriods = new ArrayList<>(outerPerEntries.keySet());
					Collections.sort(sitePeriods);
					sitePerXVals = new ArrayList<>(sitePeriods.size());
				} else {
					Preconditions.checkState(outerPerEntries.size() == sitePeriods.size());
				}
				
				List<List<DiscretizedFunc>> outerPerCurves = new ArrayList<>(sitePeriods.size());
				outerSiteCurves.add(outerPerCurves);
				List<List<DiscretizedFunc>> innerPerCurves = new ArrayList<>(sitePeriods.size());
				innerSiteCurves.add(innerPerCurves);
				List<String> outNames = new ArrayList<>(sitePeriods.size());
				siteOutNames.add(outNames);
				for (int p=0; p<sitePeriods.size(); p++) {
					double period = sitePeriods.get(p);
					Preconditions.checkState(innerPerEntries.containsKey(period));
					List<DiscretizedFunc> outerSitePerCurves = SiteLogicTreeHazardPageGen.loadCurves(
							CSVFile.readStream(outerZip.getInputStream(outerPerEntries.get(period)), true), outerTree);
					Preconditions.checkState(outerSitePerCurves.size() == outerTree.size());
					outerPerCurves.add(outerSitePerCurves);
					List<DiscretizedFunc> innerSitePerCurves = SiteLogicTreeHazardPageGen.loadCurves(
							CSVFile.readStream(innerZip.getInputStream(innerPerEntries.get(period)), true), innerTree);
					Preconditions.checkState(innerSitePerCurves.size() == innerTree.size());
					innerPerCurves.add(innerSitePerCurves);
					outNames.add(outerPerEntries.get(period).getName());
					Preconditions.checkState(sitePerXVals.size() >= p);
					
					DiscretizedFunc outerCurve0 = outerSitePerCurves.get(0);
					DiscretizedFunc innerCurve0 = innerSitePerCurves.get(0);
					Preconditions.checkState(outerCurve0.size() == innerCurve0.size(),
							"Curve gridding differs between outer and inner site curves");
					if (sitePerXVals.size() == p) {
						// first time
						double[] xVals = new double[outerCurve0.size()];
						for (int i=0; i<xVals.length; i++) {
							xVals[i] = outerCurve0.getX(i);
							Preconditions.checkState((float)xVals[i] == (float)innerCurve0.getX(i));
						}
						sitePerXVals.add(xVals);
					}
				}
			}
			
			innerZip.close();
			outerZip.close();
			
			String outName = hazardCurvesOutputFile.getName();
			if (outName.toLowerCase().endsWith(".zip"))
				curveOutDir = new File(hazardCurvesOutputFile.getParentFile(), outName.substring(0, outName.length()-4));
			else
				curveOutDir = hazardCurvesOutputFile;
			Preconditions.checkState(curveOutDir.exists() || curveOutDir.mkdir(),
					"Doesn't exist and couldn't be created: %s", curveOutDir.getAbsolutePath());
		}
		
		
		boolean doSLT = outputSLTFile != null;
		Map<LogicTreeBranch<?>, FaultSystemSolution> innerSols;
		SolutionLogicTree.FileBuilder combTreeWriter = doSLT ? new SolutionLogicTree.FileBuilder(outputSLTFile) : null;
		if (doSLT) {
			Preconditions.checkState(averageAcrossLevels.isEmpty(), "Averaging not yet supported");
			if (numPairwiseSamples < 1 && doesInnerSupplySols()) {
				System.out.println("Pre-loading "+innerTree.size()+" inner solutions");
				innerSols = new HashMap<>();
				for (LogicTreeBranch<?> branch : innerTree)
					innerSols.put(branch, innerSLT.forBranch(branch));
			} else {
				innerSols = null;
			}
			
			combTreeWriter.setSerializeGridded(isSerializeGridded());
			combTreeWriter.setWeightProv(new BranchWeightProvider.OriginalWeights());
		} else {
			innerSols = null;
		}
		
		int expectedNum = combTree.size();

		ArrayDeque<Future<BranchCurves>> outerCurveLoadFutures = null;
		ArrayDeque<Integer> outerCurveLoadIndexes = null;
		ArrayDeque<Future<BranchCurves>> innerCurveLoadFutures = null;
		ArrayDeque<Integer> innerCurveLoadCombinedIndexes = null;
		int outerCurveLoadIndex = -1;
		if (doHazardMaps) {
			outerCurveLoadFutures = new ArrayDeque<>(readDequeSize);
			outerCurveLoadIndexes = new ArrayDeque<>(readDequeSize);
			for (int i=0; i<combBranchesOuterIndexes.size() && outerCurveLoadFutures.size()<readDequeSize; i++) {
				int outerIndex = this.combBranchesOuterIndexes.get(i);
				outerCurveLoadIndex = i;
				if (outerCurveLoadIndexes.isEmpty() || outerCurveLoadIndexes.getLast() != outerIndex) {
					outerCurveLoadFutures.add(curveLoadFuture(outerHazardMapLoader, outerHazardSubDirName,
							outerTree.getBranch(outerIndex), periods, curveIOExec));
					outerCurveLoadIndexes.add(outerIndex);
					System.out.println("Adding outer read future for "+outerIndex);
				}
			}
			if (innerCurvesMap == null) {
				// need to do the inner curves as well
				innerCurveLoadFutures = new ArrayDeque<>(readDequeSize);
				innerCurveLoadCombinedIndexes = new ArrayDeque<>(readDequeSize);
				for (int i=0; i<readDequeSize && i<combTree.size(); i++) {
					innerCurveLoadFutures.add(curveLoadFuture(innerHazardMapLoader, innerHazardSubDirName,
							combBranchesInnerPortion.get(i), periods, curveIOExec));
					innerCurveLoadCombinedIndexes.add(i);
				}
			}
		}
		CompletableFuture<FaultSystemSolution> nextOuterSolLoadFuture = null;
		if (doSLT && doesOuterSupplySols())
			nextOuterSolLoadFuture = solLoadFuture(outerSLT, combBranchesOuterPortion.get(0));
		
		LogicTreeBranch<?> prevOuter = null;
		DiscretizedFunc[][] prevOuterCurves = null;
		FaultSystemSolution prevOuterSol = null;
		
		CompletableFuture<Void> combineSLTFuture = null;
		
		List<CompletableFuture<Void>> perAvgFutures = null;
		
		int numOutersProcessed = 0;

		List<Future<?>> curveWriteFutures = null;
		ExecutorService exec = Executors.newFixedThreadPool(FaultSysTools.defaultNumThreads());
		
		Stopwatch watch = Stopwatch.createStarted();
		
		Stopwatch combineWatch = Stopwatch.createUnstarted();
		Stopwatch mapStringWatch = Stopwatch.createUnstarted();
		Stopwatch blockingZipIOWatch = Stopwatch.createUnstarted();
		Stopwatch curveReadWatch = Stopwatch.createUnstarted();
		Stopwatch curveWriteWatch = (hazardOutDir != null || doHazardCurves) ? Stopwatch.createUnstarted() : null;
		Stopwatch blockingAvgWatch = Stopwatch.createUnstarted();
		
		for (int n=0; n<combTree.size(); n++) {
			final LogicTreeBranch<LogicTreeNode> combBranch = combBranches.get(n);
			System.out.println("Processing branch "+n+"/"+expectedNum+": "+combBranch);
			Preconditions.checkState(combBranches.get(n).equals(combTree.getBranch(n)));
			final double combWeight = combBranch.getOrigBranchWeight();
			final LogicTreeBranch<?> outerBranch = combBranchesOuterPortion.get(n);
			final LogicTreeBranch<?> innerBranch = combBranchesInnerPortion.get(n);
			final int outerIndex = combBranchesOuterIndexes.get(n);
			final int innerIndex = combBranchesInnerIndexes.get(n);
			System.out.println("\tOuter branch "+outerIndex+": "+outerBranch);
			System.out.println("\tInner branch "+innerIndex+": "+innerBranch);
			Preconditions.checkState(innerBranch.equals(innerTree.getBranch(innerIndex)),
					"Inner branch for %s [%s] doesn't match outer branch at innerIndex=%s [%s]",
					n, innerBranch, innerIndex, innerTree.getBranch(innerIndex));
			Preconditions.checkState(outerBranch.equals(outerTree.getBranch(outerIndex)),
					"Outer branch for %s [%s] doesn't match outer branch at outerIndex=%s [%s]",
					n, outerBranch, outerIndex, outerTree.getBranch(outerIndex));
			for (LogicTreeNode node : innerBranch) {
				if (innerNodeRemaps.containsKey(node))
					node = innerNodeRemaps.get(node);
				Preconditions.checkState(combBranch.hasValue(node),
						"Inner branch has node %s which isn't on conbined branch: %s",
						node, combBranch);
			}
			for (LogicTreeNode node : outerBranch) {
				if (outerNodeRemaps.containsKey(node))
					node = outerNodeRemaps.get(node);
				Preconditions.checkState(combBranch.hasValue(node),
						"Outer branch has node %s which isn't on conbined branch: %s",
						node, combBranch);
			}
			
			DiscretizedFunc[][] outerCurves = null;
			FaultSystemSolution outerSol = null;
			
			if (prevOuter == null || outerBranch != prevOuter) {
				System.out.println("New outer branch: "+n+"/"+outerTree.size()+": "+outerBranch);
				// new outer branch
				if (doHazardMaps) {
					curveReadWatch.start();
					System.out.println("Reading outer curves for branch "+n+", outerIndex="+outerIndex);
					int nextOuterIndex = outerCurveLoadIndexes.removeFirst();
					Preconditions.checkState(nextOuterIndex == outerIndex,
							"Future outerIndex=%s, we need %s", nextOuterIndex, outerIndex);
					try {
						BranchCurves outerBranchCurves = outerCurveLoadFutures.removeFirst().get();
						Preconditions.checkState(outerBranch.equals(outerBranchCurves.branch),
								"Curve load mismatch for outer %s; expected %s, was %s",
								outerIndex, outerBranch, outerBranchCurves.branch);
						outerCurves = outerBranchCurves.curves;
					} catch (InterruptedException | ExecutionException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					for (int i=outerCurveLoadIndex+1; i<combBranchesOuterIndexes.size() && outerCurveLoadFutures.size()<readDequeSize; i++) {
						int nextSubmitOuterIndex = this.combBranchesOuterIndexes.get(i);
						outerCurveLoadIndex = i;
						if (outerCurveLoadIndexes.getLast() != nextSubmitOuterIndex) {
							System.out.println("Adding outer read future for "+nextSubmitOuterIndex);
							outerCurveLoadFutures.add(curveLoadFuture(outerHazardMapLoader, outerHazardSubDirName,
									outerTree.getBranch(nextSubmitOuterIndex), periods, curveIOExec));
							outerCurveLoadIndexes.add(nextSubmitOuterIndex);
						}
					}
					curveReadWatch.stop();
				}
				if (doSLT && doesOuterSupplySols()) {
					outerSol = nextOuterSolLoadFuture.join();
					// start the next async read
					for (int m=n+1; m<combTree.size(); m++) {
						LogicTreeBranch<?> nextOuter = combBranchesOuterPortion.get(m);
						if (nextOuter != outerBranch) {
							nextOuterSolLoadFuture = solLoadFuture(outerSLT, nextOuter);
							break;
						}
					}
				}
				
				if (n > 0) {
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
					double fractDone = (double)n/(double)combTree.size();
					System.out.println("DONE outer branch "+numOutersProcessed+"/"+outerTree.size()+", "
							+n+"/"+combTree.size()+" total branches ("+pDF.format(fractDone)+")");
					printBlockingTimes(watch, combineWatch, mapStringWatch, curveReadWatch, curveWriteWatch, blockingZipIOWatch, blockingAvgWatch);
					double totSecs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
					double secsEach = totSecs / (double)n;
					double expectedSecs = secsEach*combTree.size();
					double secsLeft = expectedSecs - totSecs;
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
				if (hazardOutDir != null)
					curveWriteFutures = new ArrayList<>();
			} else {
				// reuse
				outerSol = prevOuterSol;
				outerCurves = prevOuterCurves;
			}
			
			if (doSLT) {
				if (combineSLTFuture != null) {
					combineWatch.start();
					combineSLTFuture.join();
					combineWatch.stop();
				}
				Preconditions.checkState(doesOuterSupplySols() || doesInnerSupplySols());
				FaultSystemSolution myOuterSol = outerSol;
				combineSLTFuture = CompletableFuture.runAsync(new Runnable() {
					
					@Override
					public void run() {
						try {
							FaultSystemSolution innerSol = null;
//							doesInnerSupplySols() ? innerSLT.forBranch(innerBranch) : null;
							if (doesInnerSupplySols()) {
								if (innerSols == null)
									// need to load it
									innerSol = innerSLT.forBranch(innerBranch);
								else
									innerSol = innerSols.get(innerBranch);
							}
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
			
			if (doHazardMaps) {
				BranchCurves innerBranchCurves;
				if (innerCurvesMap != null) {
					innerBranchCurves = innerCurvesMap.get(innerBranch);
				} else {
					curveReadWatch.start();
//					innerCurves = nextInnerCurveLoadFuture.join();
					try {
						int loadInnerIndex = innerCurveLoadCombinedIndexes.removeFirst();
						Preconditions.checkState(loadInnerIndex == n, "Load inner index was %s but I'm on branch %s", loadInnerIndex, n);
						innerBranchCurves = innerCurveLoadFutures.removeFirst().get();
					} catch (InterruptedException | ExecutionException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					curveReadWatch.stop();
					
//					for (int i=0; i<readDequeSize && i<combTree.size(); i++)
//						innerCurveLoadFutures.add(curveLoadFuture(innerHazardMapLoader, innerHazardSubDirName,
//								combBranchesInnerPortion.get(i), periods, curveIOExec));
					if (innerCurveLoadCombinedIndexes.isEmpty()) {
						Preconditions.checkState(n == combTree.size()-1, "No load indexes left, but not on last branch: %s", n);
					} else {
						int lastRunningInnerIndex = innerCurveLoadCombinedIndexes.getLast();
						for (int m=lastRunningInnerIndex+1; m<combTree.size() && innerCurveLoadFutures.size() < readDequeSize; m++) {
							innerCurveLoadFutures.add(curveLoadFuture(innerHazardMapLoader, innerHazardSubDirName,
									combBranchesInnerPortion.get(m), periods, curveIOExec));
							innerCurveLoadCombinedIndexes.add(m);
						}
					}
				}
				Preconditions.checkState(innerBranch.equals(innerBranchCurves.branch),
						"Curve load mismatch for inner %s; expected %s, was %s",
						innerIndex, innerBranch, innerBranchCurves.branch);
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
						curveWriteFutures.add(curveIOExec.submit(new Runnable() {
							
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
					blockingZipIOWatch.start();
					writeFuture.join();
					blockingZipIOWatch.stop();
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
				
				System.out.println("Writing combined branch "+n+"/"+combBranches.size()+": "+combBranch);
				
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
				});
			}
			
			if (doHazardCurves) {
				combineWatch.start();
				
				List<List<Future<CurveCombineResult>>> combineFutures = new ArrayList<>(sites.size());
				for (int s=0; s<sites.size(); s++) {
					List<Future<CurveCombineResult>> periodFutures = new ArrayList<>(sitePeriods.size());
					for (int p=0; p<sitePeriods.size(); p++) {
						double[] xVals = sitePerXVals.get(p);
						DiscretizedFunc outerCurve = outerSiteCurves.get(s).get(p).get(outerIndex);
						DiscretizedFunc innerCurve = innerSiteCurves.get(s).get(p).get(innerIndex);
						periodFutures.add(exec.submit(new CurveCombineCallable(s, xVals, outerCurve, innerCurve, null)));
					}
					combineFutures.add(periodFutures);
				}
				
				if (siteCurveWriters == null) {
					// first time, initialize writers
					siteCurveWriters = new ArrayList<>(sites.size());
					List<String> perHeaders = new ArrayList<>(sitePeriods.size());
					for (int p=0; p<sitePeriods.size(); p++) {
						double[] xVals = sitePerXVals.get(p);
						List<String> header = new ArrayList<>();
						header.add("Site Name");
						header.add("Branch Index");
						header.add("Branch Weight");
						for (int l=0; l<combBranch.size(); l++)
							header.add(combBranch.getLevel(l).getShortName());
						for (double x : xVals)
							header.add((float)x+"");
						perHeaders.add(CSVFile.getLineStr(header));
					}
					for (int s=0; s<sites.size(); s++) {
						List<FileWriter> perCurveWriters = new ArrayList<>(sitePeriods.size());
						for (int p=0; p<sitePeriods.size(); p++) {
							String outName = siteOutNames.get(s).get(p);
							FileWriter fw = new FileWriter(new File(curveOutDir, outName));
							fw.write(perHeaders.get(p));
							fw.write('\n');
							perCurveWriters.add(fw);
						}
						siteCurveWriters.add(perCurveWriters);
					}
				}

				List<List<String>> siteCombCurveLines = new ArrayList<>(sites.size());
				for (int s=0; s<sites.size(); s++) {
					List<String> perCurveLines = new ArrayList<>(sitePeriods.size());
					siteCombCurveLines.add(perCurveLines);
					List<String> commonPrefix = new ArrayList<>(combBranch.size()+3);
					commonPrefix.add(sites.get(s).getName());
					commonPrefix.add(n+"");
					commonPrefix.add(combWeight+"");
					for (LogicTreeNode node : combBranch)
						commonPrefix.add(node.getShortName());
					String prefixStr = CSVFile.getLineStr(commonPrefix);
					for (int p=0; p<sitePeriods.size(); p++) {
						DiscretizedFunc curve;
						try {
							CurveCombineResult result = combineFutures.get(s).get(p).get();
//							if (s == 0 && p == 0) {
//								System.out.println("DEBUG COMBINE");
//								DiscretizedFunc outerCurve = outerSiteCurves.get(s).get(p).get(outerIndex);
//								DiscretizedFunc innerCurve = innerSiteCurves.get(s).get(p).get(innerIndex);
//								System.out.println("x\touter\tinner\tcomb");
//								for (int i=0; i<outerCurve.size(); i++)
//									System.out.println((float)outerCurve.getX(i)+"\t"+(float)outerCurve.getY(i)
//											+"\t"+(float)innerCurve.getY(i)+"\t"+(float)result.combCurve.getY(i));
//							}
							curve = result.combCurve;
						} catch (InterruptedException | ExecutionException e) {
							throw ExceptionUtils.asRuntimeException(e);
						}
						StringBuilder line = new StringBuilder();
						line.append(prefixStr);
						for (Point2D pt : curve)
							line.append(',').append(String.valueOf(pt.getY()));
						line.append('\n');
						
						perCurveLines.add(line.toString());
					}
				}
				
				combineWatch.stop();
				
				curveWriteWatch.start();
				for (int s=0; s<sites.size(); s++)
					for (int p=0; p<sitePeriods.size(); p++)
						siteCurveWriters.get(s).get(p).write(siteCombCurveLines.get(s).get(p));
				curveWriteWatch.stop();
			}
			
			prevOuter = outerBranch;
			prevOuterCurves = outerCurves;
			prevOuterSol = outerSol;
		}
		
		exec.shutdown();
		if (curveIOExec != null)
			curveIOExec.shutdown();
		
		if (writeFuture != null)
			writeFuture.join();
		
		watch.stop();
		double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		
		System.out.println("Wrote for "+combBranches.size()+" branches ("+(float)(combBranches.size()/secs)+" /s)");
		if (doHazardMaps)
			System.out.println("Wrote "+writeCounter.getWritten()+" maps in total");
		
		if (doSLT) {
			System.out.println("Finalizing SLT file");
			combineSLTFuture.join();
			combTreeWriter.build();
//			LogicTree<?> combTree = combSLT.getLogicTree();
		}
		
		if (doHazardMaps) {
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
			combTree.writeToArchive(hazardOutZip, null);
			
			hazardOutZip.close();
			blockingZipIOWatch.stop();
		}
		
		if (doHazardCurves) {
			System.out.println("Finalizing site hazard curve files");
			curveWriteWatch.start();
			for (int s=0; s<sites.size(); s++)
				for (int p=0; p<sitePeriods.size(); p++)
					siteCurveWriters.get(s).get(p).close();
			curveWriteWatch.stop();
			
			blockingZipIOWatch.start();
			// zip them
			File curveZipFile;
			if (curveOutDir == hazardCurvesOutputFile)
				// we were supplied a directory, add .zip for the zip file
				curveZipFile = new File(curveOutDir.getAbsoluteFile()+".zip");
			else
				// we were supplied a zip file, use that directly
				curveZipFile = hazardCurvesOutputFile;
			System.out.println("Building site hazard curve zip file: "+curveZipFile.getAbsolutePath());
			
			ArchiveOutput output = new ArchiveOutput.ZipFileOutput(curveZipFile);
			
			output.putNextEntry(MPJ_SiteLogicTreeHazardCurveCalc.SITES_CSV_FILE_NAME);
			sitesCSV.writeToStream(output.getOutputStream());
			output.closeEntry();
			
			combTree.writeToArchive(output, null);
			
			for (int s=0; s<sites.size(); s++) {
				for (int p=0; p<sitePeriods.size(); p++) {
					String csvName = siteOutNames.get(s).get(p);
					System.out.println("Processing site "+s+"/"+sites.size()+" "+csvName);
					output.putNextEntry(csvName);
					
					File inFile = new File(curveOutDir, csvName);
					InputStream in = new BufferedInputStream(new FileInputStream(inFile));
					IOUtils.copy(in, output.getOutputStream());
					
					output.closeEntry();
				}
			}
			output.close();
			blockingZipIOWatch.stop();
		}
		
		System.out.println("DONE");
		printBlockingTimes(watch, combineWatch, mapStringWatch, curveReadWatch, curveWriteWatch, blockingZipIOWatch, blockingAvgWatch);
	}

	private static void printBlockingTimes(Stopwatch watch, Stopwatch combineWatch, Stopwatch mapStringWatch,
			Stopwatch curveReadWatch, Stopwatch curveWriteWatch, Stopwatch blockingZipIOWatch, Stopwatch blockingAvgWatch) {
		System.out.println("\tTotal time combining:\t"+blockingTimePrint(combineWatch, watch));
		System.out.println("\tTotal on map file rep:\t"+blockingTimePrint(mapStringWatch, watch));
		System.out.println("\tTotal blocking curve read I/O:\t"+blockingTimePrint(curveReadWatch, watch));
		if (curveWriteWatch != null)
			System.out.println("\tTotal blocking curve write I/O:\t"+blockingTimePrint(curveWriteWatch, watch));
		System.out.println("\tTotal blocking Zip I/O:\t"+blockingTimePrint(blockingZipIOWatch, watch));
		System.out.println("\tTotal blocking Averaging:\t"+blockingTimePrint(blockingAvgWatch, watch));
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
			return AbstractLogicTreeHazardCombiner.loadCurves(branchHazardDir, periods, null);
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
		TectonicRegionType[] trts = new TectonicRegionType[totNumRups];
		
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
			
			RupSetTectonicRegimes myTRTs = trts == null ? null : rupSet.getModule(RupSetTectonicRegimes.class);
			if (myTRTs == null)
				trts = null;
			
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
				rupLengths[rupIndex] = rupSet.getLengthForRup(r);
				rates[rupIndex] = sol.getRateForRup(r);
				if (trts != null)
					trts[rupIndex] = myTRTs.get(r);
				
				if (clusterRups)
					cRups.add(myCrups.get(r));
				
				rupIndex++;
			}
		}
		
		FaultSystemRupSet mergedRupSet = new FaultSystemRupSet(mergedSects, sectionForRups, mags, rakes, rupAreas, rupLengths);
		if (clusterRups)
			mergedRupSet.addModule(ClusterRuptures.instance(mergedRupSet, cRups, false));
		if (trts != null)
			mergedRupSet.addModule(new RupSetTectonicRegimes(mergedRupSet, trts));
		return new FaultSystemSolution(mergedRupSet, rates);
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
	private static final DecimalFormat countDF = new DecimalFormat("0.#");
	static {
		countDF.setGroupingSize(3);
		countDF.setGroupingUsed(true);
	}
	
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
