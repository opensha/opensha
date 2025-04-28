package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomlySampledLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;

import com.google.common.base.Preconditions;

public class LogicTreeCurveAverager {
	
	private LocationList gridLocs;
	private Map<String, DiscretizedFunc[]> curvesMap;
	private Map<String, Double> weightSumsMap;
	
	private HashSet<LogicTreeNode> variableNodes;
	private HashMap<LogicTreeNode, LogicTreeLevel<?>> nodeLevels;
	
	public static final String MEAN_PREFIX = "mean";
	
	public LogicTreeCurveAverager(LogicTree<?> tree, LocationList gridLocs) {
		this.gridLocs = gridLocs;
		curvesMap = new HashMap<>();
		weightSumsMap = new HashMap<>();
		
		if (tree != null) {
			// figure out which nodes vary on the logic tree
			// so that we can keep track of mean maps *without* them
			variableNodes = new HashSet<>();
			nodeLevels = new HashMap<>();
			populateVariableNodes(tree, variableNodes, nodeLevels);
		}
	}
	
	public LogicTreeCurveAverager(LocationList gridLocs, HashSet<LogicTreeNode> variableNodes,
			HashMap<LogicTreeNode, LogicTreeLevel<?>> nodeLevels) {
		this.gridLocs = gridLocs;
		curvesMap = new HashMap<>();
		weightSumsMap = new HashMap<>();
		
		this.variableNodes = variableNodes;
		this.nodeLevels = nodeLevels;
	}
	
	public static void populateVariableNodes(LogicTree<?> tree, HashSet<LogicTreeNode> variableNodes,
			HashMap<LogicTreeNode, LogicTreeLevel<?>> nodeLevels) {
		populateVariableNodes(tree, variableNodes, nodeLevels, null, null);
	}
	
	public static void populateVariableNodes(LogicTree<?> tree, HashSet<LogicTreeNode> variableNodes,
			HashMap<LogicTreeNode, LogicTreeLevel<?>> nodeLevels,
			Map<LogicTreeLevel<?>, LogicTreeLevel<?>> levelRemappings,
			Map<LogicTreeNode, LogicTreeNode> nodeRemappings) {
		int numLevels = tree.getLevels().size();
		for (int l=0; l<numLevels; l++) {
			HashSet<LogicTreeNode> levelNodes = new HashSet<>();
			for (LogicTreeBranch<?> branch : tree) {
				LogicTreeNode node = branch.getValue(l);
				if (nodeRemappings != null && nodeRemappings.containsKey(node))
					node = nodeRemappings.get(node);
				levelNodes.add(node);
			}
			LogicTreeLevel<?> level = tree.getLevels().get(l);
			if (!(shouldSkipLevel(level, levelNodes.size()))) {
				if (levelRemappings != null && levelRemappings.containsKey(level))
					level = levelRemappings.get(level);
				for (LogicTreeNode node : levelNodes)
					nodeLevels.put(node, level);
				variableNodes.addAll(levelNodes);
			}
		}
	}
	
	public static boolean shouldSkipLevel(LogicTreeLevel<?> level, int nodeCount) {
		if (nodeCount <= 1)
			return false;
		return level instanceof RandomlySampledLevel<?> && nodeCount > 6 || nodeCount > 30;
	}
	
	public synchronized void processBranchCurves(LogicTreeBranch<?> branch, double weight, DiscretizedFunc[] curves) {
		Preconditions.checkState(curves.length == gridLocs.size());
		
		List<String> keys = getMeanCurveKeys(branch);
		
		double[] xVals = new double[curves[0].size()];
		for (int i=0; i<xVals.length; i++)
			xVals[i] = curves[0].getX(i);
		
		for (String key : keys) {
			DiscretizedFunc[] meanCurves = curvesMap.get(key);
			if (meanCurves == null) {
				meanCurves = new DiscretizedFunc[curves.length];
				for (int i=0; i<curves.length; i++)
					meanCurves[i] = new LightFixedXFunc(xVals, new double[xVals.length]);
				curvesMap.put(key, meanCurves);
				weightSumsMap.put(key, weight);
			} else {
				Preconditions.checkState(meanCurves.length == curves.length);
				weightSumsMap.put(key, weightSumsMap.get(key)+weight);
			}
			for (int i=0; i<curves.length; i++) {
				DiscretizedFunc curve = curves[i];
				DiscretizedFunc meanCurve = meanCurves[i];
				Preconditions.checkState(curve.size() == meanCurve.size());
				for (int j=0; j<curve.size(); j++)
					meanCurve.set(j, meanCurve.getY(j) + weight*curve.getY(j));
			}
		}
	}
	
	public void rawCacheToDir(File outputDir, double period) throws IOException {
		List<String> keys = new ArrayList<>(curvesMap.keySet());
		Collections.sort(keys);
		CSVFile<String> weightsCSV = new CSVFile<>(true);
		weightsCSV.addLine("Key", "Weight Sum");
		for (String key : keys) {
			String fileName = SolHazardMapCalc.getCSV_FileName(key+"_curves", period);
			DiscretizedFunc[] curves = curvesMap.get(key);
			double weight = weightSumsMap.get(key);
			CSVFile<String> curvesCSV = SolHazardMapCalc.buildCurvesCSV(curves, gridLocs);
			curvesCSV.writeToFile(new File(outputDir, fileName));
			weightsCSV.addLine(key, weight+"");
		}
		weightsCSV.writeToFile(new File(outputDir, SolHazardMapCalc.getCSV_FileName("weights", period)));
	}
	
	public static LogicTreeCurveAverager readRawCacheDir(File cacheDir, double period, ExecutorService exec) throws IOException {
		CSVFile<String> weightsCSV = CSVFile.readFile(
				new File(cacheDir, SolHazardMapCalc.getCSV_FileName("weights", period)), true);
		LogicTreeCurveAverager ret = new LogicTreeCurveAverager(null, null);
		
		Map<String, Future<DiscretizedFunc[]>> loadFutures = new HashMap<>();
		
		for (int row=1; row<weightsCSV.getNumRows(); row++) {
			String key = weightsCSV.get(row, 0);
			double weight = weightsCSV.getDouble(row, 1);
			
			ret.weightSumsMap.put(key, weight);
			
			final String fileName = SolHazardMapCalc.getCSV_FileName(key+"_curves", period);
			
			Callable<DiscretizedFunc[]> loadCall = new Callable<DiscretizedFunc[]>() {

				@Override
				public DiscretizedFunc[] call() throws Exception {
					CSVFile<String> curvesCSV = CSVFile.readFile(new File(cacheDir, fileName), true);
					DiscretizedFunc[] curves = SolHazardMapCalc.loadCurvesCSV(curvesCSV, null);
					
					synchronized (ret) {
						if (ret.gridLocs == null) {
							ret.gridLocs = new LocationList();
							for (int i=0; i<curves.length; i++) {
								Preconditions.checkState(curvesCSV.getInt(i+1, 0) == i);
								double lat = curvesCSV.getDouble(i+1, 1);
								double lon = curvesCSV.getDouble(i+1, 2);
								ret.gridLocs.add(new Location(lat, lon));
							}
						} else {
							Preconditions.checkState(ret.gridLocs.size() == curves.length);
						}
					}
					return curves;
				}
			};
			
			loadFutures.put(key, exec.submit(loadCall));
		}
		
		for (String key : loadFutures.keySet()) {
			try {
				ret.curvesMap.put(key, loadFutures.get(key).get());
			} catch (Exception e) {
				if (e instanceof IOException)
					throw (IOException)e;
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		return ret;
	}
	
	public void addFrom(LogicTreeCurveAverager other) {
		if (gridLocs == null && other.gridLocs != null)
			gridLocs = other.gridLocs;
		double[] xVals = null;
		
		for (String key : other.curvesMap.keySet()) {
			double weight = other.weightSumsMap.get(key);
			DiscretizedFunc[] curves = other.curvesMap.get(key);
			
			DiscretizedFunc[] meanCurves = curvesMap.get(key);
			if (meanCurves == null) {
				meanCurves = new DiscretizedFunc[curves.length];
				if (xVals == null) {
					xVals = new double[curves[0].size()];
					for (int i=0; i<xVals.length; i++)
						xVals[i] = curves[0].getX(i);
				}
				for (int i=0; i<curves.length; i++)
					meanCurves[i] = new LightFixedXFunc(xVals, new double[xVals.length]);
				curvesMap.put(key, meanCurves);
				weightSumsMap.put(key, weight);
			} else {
				Preconditions.checkState(meanCurves.length == curves.length);
				weightSumsMap.put(key, weightSumsMap.get(key)+weight);
			}
			for (int i=0; i<curves.length; i++) {
				DiscretizedFunc curve = curves[i];
				DiscretizedFunc meanCurve = meanCurves[i];
				Preconditions.checkState(curve.size() == meanCurve.size());
				for (int j=0; j<curve.size(); j++)
					// do a direct add here, don't rescale by weight
					meanCurve.set(j, meanCurve.getY(j) + curve.getY(j));
			}
		}
	}
	
	private DiscretizedFunc[] getNormalized(DiscretizedFunc[] curves, double weight) {
		if ((float)weight != 1f) {
			double scale = 1d/weight;
			DiscretizedFunc[] normCurves = new DiscretizedFunc[curves.length];
			for (int i=0; i<curves.length; i++) {
				normCurves[i] = curves[i].deepClone();
				normCurves[i].scale(scale);
			}
			return normCurves;
		}
		return curves;
	}
	
	public Map<String, DiscretizedFunc[]> getNormalizedCurves() {
		Map<String, DiscretizedFunc[]> ret = new HashMap<>();
		
		for (String key : curvesMap.keySet())
			ret.put(key, getNormalized(curvesMap.get(key), weightSumsMap.get(key)));
		
		return ret;
	}
	
	public static String choicePrefix(LogicTreeLevel<?> level, LogicTreeNode node) {
		return level.getFilePrefix()+"_"+node.getFilePrefix();
	}
	
	public static String choiceWithoutPrefix(LogicTreeLevel<?> level, LogicTreeNode node) {
		return level.getFilePrefix()+"_without_"+node.getFilePrefix();
	}
	
	private List<String> getMeanCurveKeys(LogicTreeBranch<?> branch) {
		List<String> meanCurveKeys = new ArrayList<>();
		meanCurveKeys.add(MEAN_PREFIX); // always full mean
		for (int i=0; i<branch.size(); i++) {
			LogicTreeNode node = branch.getValue(i);
			if (node != null && nodeLevels.containsKey(node))
				meanCurveKeys.add(choicePrefix(branch.getLevel(i), node));
		}
		if (variableNodes != null) {
			for (LogicTreeNode node : variableNodes) {
				if (!branch.hasValue(node))
					meanCurveKeys.add(choiceWithoutPrefix(nodeLevels.get(node), node));
			}
		}
		return meanCurveKeys;
	}

}
