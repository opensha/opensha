package org.opensha.sha.earthquake.faultSysSolution.hazard.mpj;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.xyz.AbstractXYZ_DataSet;
import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.hazard.LogicTreeCurveAverager;
import org.opensha.sha.earthquake.faultSysSolution.hazard.QuickGriddedHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.logicTree.ScalarIMR_LogicTreeNode;
import org.opensha.sha.imr.logicTree.ScalarIMR_ParamsLogicTreeNode;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

import edu.usc.kmilner.mpj.taskDispatch.AsyncPostBatchHook;
import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;

public class MPJ_LogicTreeHazardCalc extends MPJTaskCalculator {

	private File outputDir;

	private SolutionLogicTree solTree;
	
	private static final double GRID_SPACING_DEFAULT = 0.1d;
	private double gridSpacing = GRID_SPACING_DEFAULT;
	
	private static final double MAX_DIST_DEFAULT = 500;
	private double maxDistance = MAX_DIST_DEFAULT;
	
	private static final double SKIP_MAX_DIST_DEFAULT = 300;
	private double skipMaxSiteDist = SKIP_MAX_DIST_DEFAULT;
	
	private static AttenRelRef GMPE_DEFAULT = AttenRelRef.ASK_2014;
	private AttenRelRef gmpeRef = GMPE_DEFAULT;
	
//	private static final double[] PERIODS_DEFAULT = { 0d, 0.2d, 1d };
	public static final double[] PERIODS_DEFAULT = { 0d, 1d };
	private double[] periods = PERIODS_DEFAULT;
	
	private ReturnPeriods[] rps = SolHazardMapCalc.MAP_RPS;
	
	private static final IncludeBackgroundOption GRID_SEIS_DEFAULT = IncludeBackgroundOption.EXCLUDE;
	private IncludeBackgroundOption gridSeisOp = GRID_SEIS_DEFAULT;
	
	private static final boolean AFTERSHOCK_FILTER_DEFAULT = false;
	private boolean applyAftershockFilter = AFTERSHOCK_FILTER_DEFAULT;
	
	private GriddedRegion gridRegion;

	private String hazardSubDirName;
	private String combineWithHazardExcludingSubDirName;
	private String combineWithHazardBGOnlySubDirName;
	
	private LogicTreeCurveAverager[] runningMeanCurves;
	
	private File nodesAverageDir;
	private File myAverageDir;
	
	private GridSourceProvider externalGridProv;
	private SolHazardMapCalc externalGriddedCurveCalc;
	
	private QuickGriddedHazardMapCalc[] quickGridCalcs;

	public MPJ_LogicTreeHazardCalc(CommandLine cmd) throws IOException {
		super(cmd);
		
		this.shuffle = false;
		
		File inputFile = new File(cmd.getOptionValue("input-file"));
		Preconditions.checkState(inputFile.exists());
		if (inputFile.isDirectory()) {
			Preconditions.checkArgument(cmd.hasOption("logic-tree"), "Must supply logic tree file if input-file is"
					+ " a results directory");
			File logicTreeFile = new File(cmd.getOptionValue("logic-tree"));
			Preconditions.checkArgument(logicTreeFile.exists(), "Logic tree file doesn't exist: %s",
					logicTreeFile.getAbsolutePath());
			LogicTree<?> tree = LogicTree.read(logicTreeFile);
			
			solTree = new SolutionLogicTree.ResultsDirReader(inputFile, tree);
		} else {
			// it should be SolutionLogicTree zip file
			solTree = SolutionLogicTree.load(inputFile);
		}
		
		if (rank == 0)
			debug("Loaded "+solTree.getLogicTree().size()+" tree nodes/solutions");
		
		outputDir = new File(cmd.getOptionValue("output-dir"));
		
		if (cmd.hasOption("gridded-seis"))
			gridSeisOp = IncludeBackgroundOption.valueOf(cmd.getOptionValue("gridded-seis"));
		
		if (cmd.hasOption("grid-spacing"))
			gridSpacing = Double.parseDouble(cmd.getOptionValue("grid-spacing"));
		
		if (cmd.hasOption("max-distance"))
			maxDistance = Double.parseDouble(cmd.getOptionValue("max-distance"));
		
		if (cmd.hasOption("skip-max-distance"))
			skipMaxSiteDist = Double.parseDouble(cmd.getOptionValue("skip-max-distance"));
		
		if (cmd.hasOption("gmpe"))
			gmpeRef = AttenRelRef.valueOf(cmd.getOptionValue("gmpe"));
		
		if (cmd.hasOption("periods")) {
			List<Double> periodsList = new ArrayList<>();
			String periodsStr = cmd.getOptionValue("periods");
			if (periodsStr.contains(",")) {
				String[] split = periodsStr.split(",");
				for (String str : split)
					periodsList.add(Double.parseDouble(str));
			} else {
				periodsList.add(Double.parseDouble(periodsStr));
			}
			periods = Doubles.toArray(periodsList);
		}
		
		if (cmd.hasOption("region")) {
			File regFile = new File(cmd.getOptionValue("region"));
			Preconditions.checkState(regFile.exists(), "Supplied region file doesn't exist: %s", regFile.getAbsolutePath());
			Region region;
			if (regFile.getName().toLowerCase().endsWith(".zip")) {
				// it's a zip file, assume it's a prior hazard calc
				ZipFile zip = new ZipFile(regFile);
				ZipEntry regEntry = zip.getEntry(GRID_REGION_ENTRY_NAME);
				if (rank == 0) debug("Reading gridded region from zip file: "+regEntry.getName());
				BufferedReader bRead = new BufferedReader(new InputStreamReader(zip.getInputStream(regEntry)));
				region = GriddedRegion.fromFeature(Feature.read(bRead));
			} else {
				Feature feature = Feature.read(regFile);
				region = Region.fromFeature(feature);
			}
			if (region instanceof GriddedRegion) {
				gridRegion = (GriddedRegion)region;
				Preconditions.checkState(
						!cmd.hasOption("grid-spacing") || (float)gridSpacing == (float)gridRegion.getSpacing(),
						"Supplied a gridded region via the command line, cannont also specify grid spacing.");
				gridSpacing = gridRegion.getSpacing();
			} else {
				gridRegion = new GriddedRegion(region, gridSpacing, GriddedRegion.ANCHOR_0_0);
			}
		}
		
		if (cmd.hasOption("aftershock-filter"))
			applyAftershockFilter = true;
		
		String hazardPrefix = "hazard_"+(float)gridSpacing+"deg";
		if (applyAftershockFilter)
			hazardPrefix += "_aftershock_filter";
		hazardPrefix += "_grid_seis_";
		hazardSubDirName = hazardPrefix+gridSeisOp.name();
		
		if (cmd.hasOption("external-grid-prov")) {
			File gpFile = new File(cmd.getOptionValue("external-grid-prov"));
			Preconditions.checkState(gpFile.exists());
			ZipFile zip = new ZipFile(gpFile);
			
			if (FaultSystemSolution.isSolution(zip)) {
				externalGridProv = FaultSystemSolution.load(zip).requireModule(GridSourceProvider.class);
			} else {
				ModuleArchive<OpenSHA_Module> avgArchive = new ModuleArchive<>(zip);
				externalGridProv = avgArchive.requireModule(GridSourceProvider.class);
			}
			Preconditions.checkArgument(gridSeisOp != IncludeBackgroundOption.EXCLUDE,
					"External grid provider was supplied, but background seismicity is disabled?");
			
			zip.close();
		}
		
		if (gridSeisOp != IncludeBackgroundOption.EXCLUDE) {
			// if we're including gridded seismicity, we can shortcut and calculate only gridded seismicity and
			// combine with curves excluding it, if we have them
			combineWithHazardExcludingSubDirName = hazardPrefix+IncludeBackgroundOption.EXCLUDE.name();
			// or alternatively, see if we have hazard already calculated with *only* gridded seismicity
			combineWithHazardBGOnlySubDirName = hazardPrefix+IncludeBackgroundOption.ONLY.name();
		}
		
		if (cmd.hasOption("quick-grid-calc") && (gridSeisOp == IncludeBackgroundOption.INCLUDE
				|| gridSeisOp == IncludeBackgroundOption.ONLY)) {
			quickGridCalcs = new QuickGriddedHazardMapCalc[periods.length];
			for (int p=0; p<quickGridCalcs.length; p++)
				quickGridCalcs[p] = new QuickGriddedHazardMapCalc(gmpeRef, periods[p],
						SolHazardMapCalc.getDefaultXVals(periods[p]), maxDistance, 100);
		}
		
		if (rank == 0) {
			waitOnDir(outputDir, 5, 1000);
			
			File outputFile;
			if (cmd.hasOption("output-file"))
				outputFile = new File(cmd.getOptionValue("output-file"));
			else
				outputFile = new File(outputDir.getParentFile(), "results_hazard.zip");
			
			postBatchHook = new AsyncHazardWriter(outputFile);
		}
		
		nodesAverageDir = new File(outputDir, "node_hazard_averages");
		if (rank == 0) {
			if (nodesAverageDir.exists()) {
				// delete anything preexisting
				for (File file : nodesAverageDir.listFiles())
					Preconditions.checkState(FileUtils.deleteRecursive(file));
			} else {
				Preconditions.checkState(nodesAverageDir.mkdir());
			}
		}
		myAverageDir = new File(nodesAverageDir, "rank_"+rank);
	}
	
	public static final String GRID_REGION_ENTRY_NAME = "gridded_region.geojson";
	
	private class AsyncHazardWriter extends AsyncPostBatchHook {
		
		private ZipOutputStream zout;
		private File workingFile;
		private File destFile;
		
		private double[] rankWeights;
		
		public AsyncHazardWriter(File destFile) throws FileNotFoundException {
			super(1);
			this.destFile = destFile;
			workingFile = new File(destFile.getAbsolutePath()+".tmp");
			zout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(workingFile)));
			
			rankWeights = new double[size];
		}

		@Override
		public void shutdown() {
			super.shutdown();
			
			try {
				if (gridRegion == null) {
					// need to detect it (could happen if not specified via command line and root node never did any calculations)
					LogicTreeBranch<?> branch = solTree.getLogicTree().getBranch(0);
					
					debug("Loading solution 0 to detect region: "+branch);
					
					FaultSystemSolution sol = solTree.forBranch(branch);
					
					gridRegion = detectRegion(sol);
				}
				
				checkInitRunningMean();
				
				// no more than 8 load threads
				int loadThreads = Integer.max(1, Integer.min(8, getNumThreads()));
				ExecutorService loadExec = Executors.newFixedThreadPool(loadThreads);
				
				// load in mean curves
				for (int p=0; p<periods.length; p++) {
					// rank 0 is already loaded into runningMeanCurves
					
					LogicTreeCurveAverager globalCurves = runningMeanCurves[p];
					CompletableFuture<Void> mergeFuture = null;
					for (int rank=1; rank<size; rank++) {
						if (rankWeights[rank] == 0d)
							continue;
						
						File rankDir = new File(nodesAverageDir, "rank_"+rank);
						debug("Async: Merging in p="+(float)periods[p]+" mean curves from "+rank+": "+rankDir.getAbsolutePath());
						Preconditions.checkState(rankDir.exists(), "Dir doesn't exist: %s", rankDir.getAbsolutePath());
						
						LogicTreeCurveAverager rankCurves = LogicTreeCurveAverager.readRawCacheDir(rankDir, periods[p], loadExec);
						
						if (mergeFuture != null)
							mergeFuture.join();
						mergeFuture = CompletableFuture.runAsync(new Runnable() {
							
							@Override
							public void run() {
								globalCurves.addFrom(rankCurves);
							}
						});
					}
					if (mergeFuture != null)
						mergeFuture.join();
				}
				
				loadExec.shutdown();
				
				// write mean curves and maps
				debug("Async: writing mean curves and maps");
				writeMeanCurvesAndMaps(zout, runningMeanCurves, gridRegion, periods, rps);
				
				// write region to zip file
				
				// write grid region
				Feature feature = gridRegion.toFeature();
				ZipEntry entry = new ZipEntry(GRID_REGION_ENTRY_NAME);
				
				zout.putNextEntry(entry);
				BufferedOutputStream out = new BufferedOutputStream(zout);
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
				Feature.write(feature, writer);
				out.flush();
				zout.closeEntry();
				
				zout.close();
				Files.move(workingFile, destFile);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}

		@Override
		protected void batchProcessedAsync(int[] batch, int processIndex) {
			debug("Async: processing batch of size "+batch.length+" from "+processIndex+": "+getCountsString());
			try {
				for (int index : batch) {
					File runDir = getSolDir(solTree.getLogicTree().getBranch(index));
					File hazardSubDir = new File(runDir, hazardSubDirName);
					Preconditions.checkState(hazardSubDir.exists());
					zout.putNextEntry(new ZipEntry(runDir.getName()+"/"));
					zout.closeEntry();
					zout.flush();
					for (ReturnPeriods rp : rps) {
						for (double period : periods) {
							String prefix = mapPrefix(period, rp);
							
							File mapFile = new File(hazardSubDir, prefix+".txt");
							Preconditions.checkState(mapFile.exists());
							
							ZipEntry mapEntry = new ZipEntry(runDir.getName()+"/"+mapFile.getName());
							debug("Async: zipping "+mapEntry.getName());
							zout.putNextEntry(mapEntry);
							BufferedInputStream inStream = new BufferedInputStream(new FileInputStream(mapFile));
							inStream.transferTo(zout);
							inStream.close();
							zout.flush();
							zout.closeEntry();
						}
					}
					
					rankWeights[processIndex] += solTree.getLogicTree().getBranchWeight(index);
				}
			} catch (Exception e) {
				e.printStackTrace();
				abortAndExit(e, 1);
			}
			debug("Async: DONE processing batch of size "+batch.length+" from "+processIndex+": "+getCountsString());
		}
		
	}
	
	private synchronized void checkInitRunningMean() {
		if (runningMeanCurves == null) {
			HashSet<LogicTreeNode> variableNodes = new HashSet<>();
			HashMap<LogicTreeNode, LogicTreeLevel<?>> nodeLevels = new HashMap<>();
			LogicTreeCurveAverager.populateVariableNodes(solTree.getLogicTree(), variableNodes, nodeLevels);
			runningMeanCurves = new LogicTreeCurveAverager[periods.length];
			for (int p=0; p<periods.length; p++)
				runningMeanCurves[p] = new LogicTreeCurveAverager(gridRegion.getNodeList(), variableNodes, nodeLevels);
		}
	}
	
	public static final String LEVEL_CHOICE_MAPS_ENTRY_PREFIX = "level_choice_maps/";
	
	public static void writeMeanCurvesAndMaps(ZipOutputStream zout, LogicTreeCurveAverager[] meanCurves,
			GriddedRegion gridRegion, double[] periods, ReturnPeriods[] rps) throws IOException {
		
		boolean firstLT = true;
		for (int p=0; p<periods.length; p++) {
			Map<String, DiscretizedFunc[]> normCurves = meanCurves[p].getNormalizedCurves();
			
			for (String key : normCurves.keySet()) {
				DiscretizedFunc[] curves = normCurves.get(key);
				String prefix;
				if (key.equals(LogicTreeCurveAverager.MEAN_PREFIX)) {
					// write out mean curves (but don't write out other ones)
					CSVFile<String> csv = SolHazardMapCalc.buildCurvesCSV(curves, gridRegion.getNodeList());
					String fileName = "mean_"+SolHazardMapCalc.getCSV_FileName("curves", periods[p]);
					zout.putNextEntry(new ZipEntry(fileName));
					csv.writeToStream(zout);
					zout.closeEntry();
					prefix = key;
				} else {
					prefix = LEVEL_CHOICE_MAPS_ENTRY_PREFIX;
					if (firstLT) {
						zout.putNextEntry(new ZipEntry(prefix));
						zout.closeEntry();
						firstLT = false;
					}
					prefix += key;
				}
				
				// calculate and write maps
				for (ReturnPeriods rp : rps) {
					String mapFileName = prefix+"_"+MPJ_LogicTreeHazardCalc.mapPrefix(periods[p], rp)+".txt";
					
					double curveLevel = rp.oneYearProb;
					
					GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridRegion, false);
					for (int i=0; i<xyz.size(); i++) {
						DiscretizedFunc curve = curves[i];
						double val;
						// curveLevel is a probability, return the IML at that probability
						if (curveLevel > curve.getMaxY())
							val = 0d;
						else if (curveLevel < curve.getMinY())
							// saturated
							val = curve.getMaxX();
						else
							val = curve.getFirstInterpolatedX_inLogXLogYDomain(curveLevel);
						xyz.set(i, val);
					}
					
					zout.putNextEntry(new ZipEntry(mapFileName));
					ArbDiscrGeoDataSet.writeXYZStream(xyz, zout);
					zout.closeEntry();
				}
			}
		}
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		// write out mean curves
		// write out branch-specific averages
		Preconditions.checkState(myAverageDir.exists() || myAverageDir.mkdir());
		if (runningMeanCurves != null) {
			for (int p=0; p<periods.length; p++) {
				debug("Caching mean curves to "+myAverageDir.getAbsolutePath());
				runningMeanCurves[p].rawCacheToDir(myAverageDir, periods[p]);
			}
		}
		
		if (rank == 0) {
			debug("waiting for any post batch hook operations to finish");
			((AsyncPostBatchHook)postBatchHook).shutdown();
			debug("post batch hook done");
		}
	}
	
	public static void waitOnDir(File dir, int maxRetries, long sleepMillis) {
		int retry = 0;
		while (!(dir.exists() || dir.mkdir())) {
			try {
				Thread.sleep(sleepMillis);
			} catch (InterruptedException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			if (retry++ > maxRetries)
				throw new IllegalStateException("Directory doesn't exist and couldn't be created after "
						+maxRetries+" retries: "+dir.getAbsolutePath());
		}
	}

	@Override
	protected int getNumTasks() {
		return solTree.getLogicTree().size();
	}
	
	protected File getSolDir(LogicTreeBranch<?> branch) {
		return getSolDir(branch, true);
	}
	
	protected File getSolDir(LogicTreeBranch<?> branch, boolean mkdir) {
		String dirName = branch.buildFileName();
		File runDir = new File(outputDir, dirName);
		Preconditions.checkState(!mkdir || runDir.exists() || runDir.mkdir());
		
		return runDir;
	}
	
	public static String mapPrefix(double period, ReturnPeriods rp) {
		String ret = "map_";
		if (period == 0d)
			ret += "pga";
		else
			ret += (float)period+"s";
		ret += "_"+rp.name();
		return ret;
	}
	
	public static Supplier<ScalarIMR> getGMM_Supplier(LogicTreeBranch<?> branch, AttenRelRef gmpeRef) {
		Supplier<ScalarIMR> supplier;
		if (branch.hasValue(ScalarIMR_LogicTreeNode.class))
			supplier = branch.requireValue(ScalarIMR_LogicTreeNode.class);
		else
			supplier = gmpeRef;
		
		if (branch.hasValue(ScalarIMR_ParamsLogicTreeNode.class)) {
			ScalarIMR_ParamsLogicTreeNode params = branch.requireValue(ScalarIMR_ParamsLogicTreeNode.class);
			return new Supplier<ScalarIMR>() {

				@Override
				public ScalarIMR get() {
					ScalarIMR imr = supplier.get();
					params.setParams(imr);
					return imr;
				}
			};
		}
		
		return supplier;
	}
	
	private GriddedRegion detectRegion(FaultSystemSolution sol) {
		Region region = new ReportMetadata(new RupSetMetadata(null, sol)).region;
		return new GriddedRegion(region, gridSpacing, GriddedRegion.ANCHOR_0_0);
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		for (int index : batch) {
			System.gc();
			LogicTreeBranch<?> branch = solTree.getLogicTree().getBranch(index);
			
			debug("Loading index "+index+": "+branch);
			
			FaultSystemSolution sol = solTree.forBranch(branch);
			
			if (gridRegion == null)
				gridRegion = detectRegion(sol);
			
			File runDir = getSolDir(branch);
			
			File hazardSubDir = new File(runDir, hazardSubDirName);
			Preconditions.checkState(hazardSubDir.exists() || hazardSubDir.mkdir());
			
			String curvesPrefix = "curves";
			
			SolHazardMapCalc calc = null;
			if (hazardSubDir.exists()) {
				// see if it's already done
				try {
					calc = SolHazardMapCalc.loadCurves(sol, gridRegion, periods, hazardSubDir, curvesPrefix);
				} catch (Exception e) {}
			}
			SolHazardMapCalc combineWithExcludeCurves = null;
			SolHazardMapCalc combineWithOnlyCurves = null;
			if (calc == null && gridSeisOp != IncludeBackgroundOption.EXCLUDE) {
				// we're calculating with gridded seismicity
				// lets see if we've already calculated without it
				
				if (gridSeisOp != IncludeBackgroundOption.ONLY) {
					File combineWithSubDir = new File(runDir, combineWithHazardExcludingSubDirName);
					
					if (combineWithSubDir.exists()) {
						debug("Seeing if we can reuse existing curves excluding gridded seismicity from "+combineWithSubDir.getAbsolutePath());
						try {
							combineWithExcludeCurves = SolHazardMapCalc.loadCurves(sol, gridRegion, periods, combineWithSubDir, curvesPrefix);
						} catch (Exception e) {
							debug("Can't reuse: "+e.getMessage());
						}
					}
					if (combineWithExcludeCurves == null) {
						// see if this is a gridded seismicity branch, but it exists already in an upstream branch
						List<LogicTreeLevel<? extends LogicTreeNode>> faultLevels = new ArrayList<>();
						List<LogicTreeNode> faultNodes = new ArrayList<>();
						for (int i=0; i<branch.size(); i++) {
							LogicTreeLevel<?> level = branch.getLevel(i);
							if (level.affects(FaultSystemSolution.RATES_FILE_NAME, true)) {
								faultLevels.add(level);
								faultNodes.add(branch.getValue(i));
							}
						}
						if (faultLevels.size() < branch.size()) {
							// we have gridded seismicity only branches
							LogicTreeBranch<LogicTreeNode> subBranch = new LogicTreeBranch<>(faultLevels, faultNodes);
							File subRunDir = getSolDir(subBranch, false);
							File subHazardDir = new File(subRunDir, combineWithHazardExcludingSubDirName);
							if (subHazardDir.exists()) {
								try {
									debug("Seeing if we can reuse existing curves excluding gridded seismicity from "+subHazardDir.getAbsolutePath());
									combineWithExcludeCurves = SolHazardMapCalc.loadCurves(sol, gridRegion, periods, subHazardDir, curvesPrefix);
								} catch (Exception e) {
									debug("Can't reuse: "+e.getMessage());
								}
							}
						}
					}
				}
				
				// now see if we've calculated with background only
				File combineWithSubDir = new File(runDir, combineWithHazardBGOnlySubDirName);
				
				if (combineWithSubDir.exists()) {
					debug("Seeing if we can reuse existing curves with only gridded seismicity from "+combineWithSubDir.getAbsolutePath());
					try {
						combineWithOnlyCurves = SolHazardMapCalc.loadCurves(sol, gridRegion, periods, combineWithSubDir, curvesPrefix);
					} catch (Exception e) {
						debug("Can't reuse: "+e.getMessage());
					}
				}
			}
			
			if (combineWithOnlyCurves == null && externalGridProv != null) {
				// external grid source provider calculation
				if (externalGriddedCurveCalc == null) {
					// first time, calculate them
					debug("Calculating external grid source provider curves (will only do this once)");
					
					FaultSystemSolution extSol = new FaultSystemSolution(sol.getRupSet(), sol.getRateForAllRups());
					extSol.setGridSourceProvider(externalGridProv);
					
					externalGriddedCurveCalc = new SolHazardMapCalc(extSol, getGMM_Supplier(branch, gmpeRef), gridRegion,
							IncludeBackgroundOption.ONLY, applyAftershockFilter, periods);
					
					externalGriddedCurveCalc.setMaxSourceSiteDist(maxDistance);
					externalGriddedCurveCalc.setSkipMaxSourceSiteDist(skipMaxSiteDist);
					
					externalGriddedCurveCalc.calcHazardCurves(getNumThreads());
				}
				
				combineWithOnlyCurves = externalGriddedCurveCalc;
			}
			
			if (quickGridCalcs != null && combineWithOnlyCurves == null) {
				Supplier<ScalarIMR> supplier = getGMM_Supplier(branch, gmpeRef);
				QuickGriddedHazardMapCalc[] quickGridCalcs = this.quickGridCalcs;
				if (supplier != gmpeRef) {
					// need to make custom Ones
					quickGridCalcs = new QuickGriddedHazardMapCalc[periods.length];
					for (int p=0; p<periods.length; p++)
						quickGridCalcs[p] = new QuickGriddedHazardMapCalc(supplier, periods[p],
								SolHazardMapCalc.getDefaultXVals(periods[p]), maxDistance, 100);
				}
				debug("Doing quick gridded seismicity calc for "+index);
				List<DiscretizedFunc[]> curves = new ArrayList<>();
				for (int p=0; p<periods.length; p++)
					curves.add(quickGridCalcs[p].calc(sol.getGridSourceProvider(), gridRegion, getNumThreads()));
				combineWithOnlyCurves = SolHazardMapCalc.forCurves(sol, gridRegion, periods, curves);
			}
			
			if (gridSeisOp == IncludeBackgroundOption.INCLUDE && combineWithOnlyCurves != null && combineWithExcludeCurves != null) {
				// we've already calculated both separately, just combine them without calculating
				List<DiscretizedFunc[]> combCurvesList = new ArrayList<>();
				for (double period : periods) {
					DiscretizedFunc[] excludeCurves = combineWithExcludeCurves.getCurves(period);
					DiscretizedFunc[] onlyCurves = combineWithOnlyCurves.getCurves(period);
					Preconditions.checkState(excludeCurves.length == gridRegion.getNodeCount());
					Preconditions.checkState(excludeCurves.length == onlyCurves.length);
					
					DiscretizedFunc[] combCurves = new DiscretizedFunc[excludeCurves.length];
					for (int i=0; i<combCurves.length; i++) {
						DiscretizedFunc curve1 = excludeCurves[i];
						DiscretizedFunc curve2 = onlyCurves[i];
						
						DiscretizedFunc combCurve;
						if (curve1 == null && curve2 == null) {
							combCurve = null;
						} else if (curve1 == null) {
							combCurve = curve2;
						} else if (curve2 == null) {
							combCurve = curve1;
						} else {
							Preconditions.checkState(curve1.size() == curve2.size());
							combCurve = new ArbitrarilyDiscretizedFunc();
							for (int j=0; j<curve1.size(); j++) {
								double x = curve1.getX(j);
								Preconditions.checkState((float)x == (float)curve2.getX(j));
								double y1 = curve1.getY(j);
								double y2 = curve2.getY(j);
								combCurve.set(x, 1d - (1d-y1)*(1d-y2));
							}
						}
						
						combCurves[i] = combCurve;
					}
					combCurvesList.add(combCurves);
				}
				calc = SolHazardMapCalc.forCurves(sol, gridRegion, periods, combCurvesList);
			} else if (calc == null && gridSeisOp == IncludeBackgroundOption.ONLY && combineWithOnlyCurves != null) {
				calc = combineWithOnlyCurves;
			}
			
			if (calc == null) {
				Supplier<ScalarIMR> gmpeSupplier = getGMM_Supplier(branch, gmpeRef);
				ScalarIMR gmpe = gmpeSupplier.get();
				String paramStr = "";
				for (Parameter<?> param : gmpe.getOtherParams())
					paramStr += "; "+param.getName()+": "+param.getValue();
				debug("Calculating hazard curves for "+index+", bgOption="+gridSeisOp.name()
						+", combineExclude="+(combineWithExcludeCurves != null)
						+", combineOnly="+(combineWithOnlyCurves != null)
						+"\n\tBranch: "+branch
						+"\n\tGMPE: "+gmpe.getName()+paramStr);
				SolHazardMapCalc combineWithCurves = null;
				if (combineWithExcludeCurves == null && combineWithOnlyCurves == null) {
					calc = new SolHazardMapCalc(sol, gmpeSupplier, gridRegion, gridSeisOp, applyAftershockFilter, periods);
				} else if (combineWithExcludeCurves != null) {
					// calculate with only gridded seismicity, we'll add in the curves excluding it
					debug("Reusing fault-based hazard for "+index+", will only compute gridded hazard");
					combineWithCurves = combineWithExcludeCurves;
					calc = new SolHazardMapCalc(sol, gmpeSupplier, gridRegion, IncludeBackgroundOption.ONLY, applyAftershockFilter, periods);
				} else if (combineWithOnlyCurves != null) {
					// calculate without gridded seismicity, we'll add in the curves with it
					debug("Reusing fault-based hazard for "+index+", will only compute gridded hazard");
					combineWithCurves = combineWithOnlyCurves;
					calc = new SolHazardMapCalc(sol, gmpeSupplier, gridRegion, IncludeBackgroundOption.EXCLUDE, applyAftershockFilter, periods);
				}
				calc.setMaxSourceSiteDist(maxDistance);
				calc.setSkipMaxSourceSiteDist(skipMaxSiteDist);
				
				calc.calcHazardCurves(getNumThreads(), combineWithCurves);
				calc.writeCurvesCSVs(hazardSubDir, curvesPrefix, true);
			}
			
			checkInitRunningMean();
			
//			if (runningMeanCurves == null) {
//				runningMeanCurves = new DiscretizedFunc[periods.length][gridRegion.getNodeCount()];
//				for (int p=0; p<periods.length; p++) {
//					DiscretizedFunc xValsFunc = calc.getXVals(periods[p]);
//					double[] xVals = new double[xValsFunc.size()];
//					for (int i=0; i<xVals.length; i++)
//						xVals[i] = xValsFunc.getX(i);
//					for (int i=0; i<runningMeanCurves[p].length; i++)
//						runningMeanCurves[p][i] = new LightFixedXFunc(xVals, new double[xVals.length]);
//				}
//				
//				nodeRunningMeanCurves = new HashMap<>();
//			}
			
			double branchWeight = solTree.getLogicTree().getBranchWeight(branch);
			for (int p=0; p<periods.length; p++) {
				DiscretizedFunc[] curves = calc.getCurves(periods[p]);
				
				runningMeanCurves[p].processBranchCurves(branch, branchWeight, curves);
			}
			
			for (ReturnPeriods rp : rps) {
				for (double period : periods) {
					GriddedGeoDataSet map = calc.buildMap(period, rp);
					
					String prefix = mapPrefix(period, rp);
					
					AbstractXYZ_DataSet.writeXYZFile(map, new File(hazardSubDir, prefix+".txt"));
				}
			}
		}
	}
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		ops.addRequiredOption("if", "input-file", true, "Path to input file (solution logic tree zip)");
		ops.addOption("lt", "logic-tree", true, "Path to logic tree JSON file, required if a results directory is "
				+ "supplied with --input-file");
		ops.addRequiredOption("od", "output-dir", true, "Path to output directory");
		ops.addOption("of", "output-file", true, "Path to output zip file. Default will be based on the output directory");
		ops.addOption("sp", "grid-spacing", true, "Grid spacing in decimal degrees. Default: "+(float)GRID_SPACING_DEFAULT);
		ops.addOption("md", "max-distance", true, "Maximum source-site distance in km. Default: "+(float)MAX_DIST_DEFAULT);
		ops.addOption("smd", "skip-max-distance", true, "Skip sites with no source-site distances below this value, in km. "
				+ "Default: "+(float)SKIP_MAX_DIST_DEFAULT);
		ops.addOption("gs", "gridded-seis", true, "Gridded seismicity option. One of "
				+FaultSysTools.enumOptions(IncludeBackgroundOption.class)+". Default: "+GRID_SEIS_DEFAULT.name());
		ops.addOption("gm", "gmpe", true, "Sets GMPE. Note that this will be overriden if the Logic Tree "
				+ "supplies GMPE choices. Default: "+GMPE_DEFAULT.name());
		ops.addOption("p", "periods", true, "Calculation period(s). Mutliple can be comma separated");
		ops.addOption("r", "region", true, "Optional path to GeoJSON file containing a region for which we should compute hazard. "
				+ "Can be a gridded region or an outline. If not supplied, then one will be detected from the model. If "
				+ "a zip file is supplied, then it is assumed that the file is a prior hazard calculation zip file and the "
				+ "region will be reused from that prior calculation.");
		ops.addOption("af", "aftershock-filter", false, "If supplied, the aftershock filter will be applied in the ERF");
		ops.addOption("egp", "external-grid-prov", true, "Path to external grid source provider to use for hazard "
				+ "calculations. Can be either a fault system solution, or a zip file containing just a grid source "
				+ "provider.");
		ops.addOption("qgc", "quick-grid-calc", false, "Flag to enable quick gridded seismicity calculation.");
		
		return ops;
	}

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		try {
			args = MPJTaskCalculator.initMPJ(args);
			
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJ_LogicTreeHazardCalc.class);
			
			MPJ_LogicTreeHazardCalc driver = new MPJ_LogicTreeHazardCalc(cmd);
			driver.run();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}

