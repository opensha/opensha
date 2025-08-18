package org.opensha.sha.earthquake.faultSysSolution.hazard.mpj;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.EnumMap;
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
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.calc.params.filters.FixedDistanceCutoffFilter;
import org.opensha.sha.calc.params.filters.SourceFilterManager;
import org.opensha.sha.calc.params.filters.SourceFilters;
import org.opensha.sha.calc.params.filters.TectonicRegionDistCutoffFilter;
import org.opensha.sha.calc.params.filters.TectonicRegionDistCutoffFilter.TectonicRegionDistanceCutoffs;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.faultSysSolution.hazard.LogicTreeCurveAverager;
import org.opensha.sha.earthquake.faultSysSolution.hazard.QuickGriddedHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.modules.AbstractLogicTreeModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysHazardCalcSettings;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysHazardCalcSettings.ParamOverrideSupplier;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.util.GriddedSeismicitySettings;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrection;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.AttenRelSupplier;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.logicTree.ScalarIMRsLogicTreeNode;
import org.opensha.sha.imr.logicTree.ScalarIMR_ParamsLogicTreeNode;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import edu.usc.kmilner.mpj.taskDispatch.AsyncPostBatchHook;
import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;

public class MPJ_LogicTreeHazardCalc extends MPJTaskCalculator {

	private File outputDir;

	private SolutionLogicTree solTree;
	
	static final double GRID_SPACING_DEFAULT = 0.1d;
	private double gridSpacing = GRID_SPACING_DEFAULT;
	
	private SourceFilterManager sourceFilter;
	
	private SourceFilterManager siteSkipSourceFilter;
	private Map<TectonicRegionType, AttenRelSupplier> gmmRefs;
	
//	static final double[] PERIODS_DEFAULT = { 0d, 0.2d, 1d };
	public static final double[] PERIODS_DEFAULT = { 0d, 1d };
	private double[] periods = PERIODS_DEFAULT;
	
	private ReturnPeriods[] rps = SolHazardMapCalc.MAP_RPS;
	
	static final IncludeBackgroundOption GRID_SEIS_DEFAULT = IncludeBackgroundOption.EXCLUDE;
	private IncludeBackgroundOption gridSeisOp = GRID_SEIS_DEFAULT;
	
	private GriddedSeismicitySettings griddedSettings;
	
	static final boolean AFTERSHOCK_FILTER_DEFAULT = false;
	private boolean applyAftershockFilter = AFTERSHOCK_FILTER_DEFAULT;
	
	static final boolean ASEIS_REDUCES_AREA_DEFAULT = true;
	boolean aseisReducesArea = ASEIS_REDUCES_AREA_DEFAULT;
	
	private GriddedRegion gridRegion;

	private List<File> combineWithOtherDirs;
	private boolean combineOnly;
	
	private String hazardSubDirName;
	private String combineWithHazardExcludingSubDirName;
	private String combineWithHazardBGOnlySubDirName;
	
	private LogicTreeCurveAverager[] runningMeanCurves;
	
	private File nodesAverageDir;
	private File myAverageDir;
	
	private GridSourceProvider externalGridProv;
	private SolHazardMapCalc externalGriddedCurveCalc;
	
	private FaultSystemSolution externalSol;
	private SolHazardMapCalc externalSolCurveCalc;
	
	private QuickGriddedHazardMapCalc[] quickGridCalcs;
	private ExecutorService quickGridExec;

	private boolean noMFDs;
	private boolean noProxyRups;

	public MPJ_LogicTreeHazardCalc(CommandLine cmd) throws IOException {
		super(cmd);
		
		this.shuffle = false;
		
		File inputFile = new File(cmd.getOptionValue("input-file"));
		Preconditions.checkState(inputFile.exists(), "Input file doesn't exist: %s", inputFile.getAbsolutePath());
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
			if (cmd.hasOption("logic-tree")) {
				File logicTreeFile = new File(cmd.getOptionValue("logic-tree"));
				Preconditions.checkArgument(logicTreeFile.exists(), "Logic tree file doesn't exist: %s",
						logicTreeFile.getAbsolutePath());
				LogicTree<?> tree = LogicTree.read(logicTreeFile);
				solTree = SolutionLogicTree.load(inputFile, tree);
			} else {
				solTree = SolutionLogicTree.load(inputFile);
			}
		}
		
		if (rank == 0)
			debug("Loaded "+solTree.getLogicTree().size()+" tree nodes/solutions");
		
		outputDir = new File(cmd.getOptionValue("output-dir"));
		
		if (cmd.hasOption("gridded-seis"))
			gridSeisOp = IncludeBackgroundOption.valueOf(cmd.getOptionValue("gridded-seis"));
		
		griddedSettings = FaultSysHazardCalcSettings.getGridSeisSettings(cmd);
		
		if (gridSeisOp != IncludeBackgroundOption.EXCLUDE)
			debug("Gridded settings: "+griddedSettings);
		
		if (cmd.hasOption("grid-spacing"))
			gridSpacing = Double.parseDouble(cmd.getOptionValue("grid-spacing"));
		
		sourceFilter = FaultSysHazardCalcSettings.getSourceFilters(cmd);
		
		siteSkipSourceFilter = FaultSysHazardCalcSettings.getSiteSkipSourceFilters(sourceFilter, cmd);
		
		gmmRefs = FaultSysHazardCalcSettings.getGMMs(cmd);
		if (rank == 0) {
			debug("GMMs:");
			for (TectonicRegionType trt : gmmRefs.keySet())
				debug("\tGMM for "+trt.name()+": "+gmmRefs.get(trt).getName());
		}
		
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
				zip.close();
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
		if (cmd.hasOption("aseis-reduces-area") || cmd.hasOption("no-aseis-reduces-area")) {
			Preconditions.checkState(!cmd.hasOption("aseis-reduces-area") || !cmd.hasOption("no-aseis-reduces-area"),
					"Can't both enable and disable aseismicity area reductions!");
			aseisReducesArea = cmd.hasOption("aseis-reduces-area");
		}
		
		String hazardPrefix = "hazard_"+(float)gridSpacing+"deg";
		if (applyAftershockFilter)
			hazardPrefix += "_aftershock_filter";
		hazardPrefix += "_grid_seis_";
		hazardSubDirName = hazardPrefix+gridSeisOp.name();
		
		if (cmd.hasOption("external-grid-prov")) {
			File gpFile = new File(cmd.getOptionValue("external-grid-prov"));
			Preconditions.checkState(gpFile.exists());
			ArchiveInput resultsInput = ArchiveInput.getDefaultInput(gpFile);
			
			if (FaultSystemSolution.isSolution(resultsInput)) {
				externalGridProv = FaultSystemSolution.load(resultsInput).requireModule(GridSourceProvider.class);
			} else {
				ModuleArchive<OpenSHA_Module> avgArchive = new ModuleArchive<>(resultsInput);
				externalGridProv = avgArchive.requireModule(GridSourceProvider.class);
			}
			Preconditions.checkArgument(gridSeisOp != IncludeBackgroundOption.EXCLUDE,
					"External single grid provider was supplied, but background seismicity is disabled?");
			
			resultsInput.close();
		}
		
		if (cmd.hasOption("external-fss")) {
			File solFile = new File(cmd.getOptionValue("external-fss"));
			Preconditions.checkState(solFile.exists());
			externalSol = FaultSystemSolution.load(solFile);
			
			Preconditions.checkArgument(gridSeisOp != IncludeBackgroundOption.ONLY,
					"External single solution was supplied, but only calculating for background seismicity?");
		}
		
		if (gridSeisOp != IncludeBackgroundOption.EXCLUDE) {
			// if we're including gridded seismicity, we can shortcut and calculate only gridded seismicity and
			// combine with curves excluding it, if we have them
			combineWithHazardExcludingSubDirName = hazardPrefix+IncludeBackgroundOption.EXCLUDE.name();
			// or alternatively, see if we have hazard already calculated with *only* gridded seismicity
			combineWithHazardBGOnlySubDirName = hazardPrefix+IncludeBackgroundOption.ONLY.name();
		}
		
		if (cmd.hasOption("combine-with-dir")) {
			String[] cwds = cmd.getOptionValues("combine-with-dir");
			combineWithOtherDirs = new ArrayList<>(cwds.length);
			for (String cwd : cwds) {
				File combineWithOtherDir = new File(cwd);
				if (combineWithOtherDir.exists())
					combineWithOtherDirs.add(combineWithOtherDir);
			}
			if (combineWithOtherDirs.isEmpty())
				combineWithOtherDirs = null;
		}
		combineOnly = cmd.hasOption("combine-only");
		
		if (cmd.hasOption("quick-grid-calc") && (gridSeisOp == IncludeBackgroundOption.INCLUDE
				|| gridSeisOp == IncludeBackgroundOption.ONLY)) {
			quickGridCalcs = new QuickGriddedHazardMapCalc[periods.length];
			for (int p=0; p<quickGridCalcs.length; p++)
				quickGridCalcs[p] = new QuickGriddedHazardMapCalc(gmmRefs, periods[p],
						FaultSysHazardCalcSettings.getDefaultXVals(periods[p]), sourceFilter, griddedSettings);
		}

		noMFDs = cmd.hasOption("no-mfds");
		noProxyRups = cmd.hasOption("no-proxy-ruptures");
		
		if (rank == 0) {
			waitOnDir(outputDir, 5, 1000);
			
			File outputFile;
			if (cmd.hasOption("output-file"))
				outputFile = new File(cmd.getOptionValue("output-file"));
			else
				outputFile = new File(outputDir.getParentFile(), outputDir.getName()+"_hazard.zip");
			
			postBatchHook = new AsyncHazardWriter(outputFile);
		}
		
		nodesAverageDir = new File(outputDir, "node_hazard_averages");
		if (rank == 0) {
			if (nodesAverageDir.exists()) {
				// delete anything preexisting
				for (File file : nodesAverageDir.listFiles())
					Preconditions.checkState(FileUtils.deleteRecursive(file));
			} else {
				Preconditions.checkState(nodesAverageDir.mkdir() || nodesAverageDir.exists());
			}
		}
		myAverageDir = new File(nodesAverageDir, "rank_"+rank);
	}
	
	public static final String GRID_REGION_ENTRY_NAME = "gridded_region.geojson";
	
	private class AsyncHazardWriter extends AsyncPostBatchHook {
		
		private ArchiveOutput zout;
		
		private double[] rankWeights;
		
		public AsyncHazardWriter(File destFile) throws IOException {
			super(1);
			zout = new ArchiveOutput.ParallelZipFileOutput(destFile, 4, false);
			
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
				
				debug("Async: deleting "+nodesAverageDir.getAbsolutePath());
				CompletableFuture<Void> deleteFuture = CompletableFuture.runAsync(new Runnable() {
					
					@Override
					public void run() {
						FileUtils.deleteRecursive(nodesAverageDir);
					}
				});
				
				// write mean curves and maps
				debug("Async: writing mean curves and maps");
				writeMeanCurvesAndMaps(zout, runningMeanCurves, gridRegion, periods, rps);
				
				// write region to zip file
				
				// write grid region
				Feature feature = gridRegion.toFeature();
				
				zout.putNextEntry(GRID_REGION_ENTRY_NAME);
				BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zout.getOutputStream()));
				Feature.write(feature, writer);
				writer.flush();
				zout.closeEntry();
				
				// write logic tree
				LogicTree<?> tree = solTree.getLogicTree();
				zout.putNextEntry(AbstractLogicTreeModule.LOGIC_TREE_FILE_NAME);
				writer = new BufferedWriter(new OutputStreamWriter(zout.getOutputStream()));
				Gson gson = new GsonBuilder().setPrettyPrinting()
						.registerTypeAdapter(LogicTree.class, new LogicTree.Adapter<>()).create();
				gson.toJson(tree, LogicTree.class, writer);
				writer.flush();
				zout.closeEntry();
				
				zout.close();
				
				try {
					deleteFuture.get();
				} catch (Exception e) {
					System.err.println("WARNING: exception deleting "+nodesAverageDir.getAbsolutePath());
					e.printStackTrace();
				}
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}

		@Override
		protected void batchProcessedAsync(int[] batch, int processIndex) {
			debug("Async: processing batch of size "+batch.length+" from "+processIndex+": "+getCountsString());
			try {
				for (int index : batch) {
					LogicTreeBranch<?> branch = solTree.getLogicTree().getBranch(index);
					File runDir = getSolDir(branch);
					File hazardOutDir = getHazardOutputDir(runDir, branch);
					Preconditions.checkState(hazardOutDir.exists());
					zout.putNextEntry(runDir.getName()+"/");
					zout.closeEntry();
					for (ReturnPeriods rp : rps) {
						for (double period : periods) {
							String prefix = mapPrefix(period, rp);
							
							File mapFile = new File(hazardOutDir, prefix+".txt");
							Preconditions.checkState(mapFile.exists());
							
							String mapEntry = runDir.getName()+"/"+mapFile.getName();
							debug("Async: zipping "+mapEntry);
							zout.transferFrom(new BufferedInputStream(new FileInputStream(mapFile)), mapEntry);
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
		writeMeanCurvesAndMaps(new ArchiveOutput.ZipFileOutput(zout), meanCurves, gridRegion, periods, rps);
	}
	
	public static void writeMeanCurvesAndMaps(ArchiveOutput output, LogicTreeCurveAverager[] meanCurves,
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
					output.putNextEntry(fileName);
					csv.writeToStream(output.getOutputStream());
					output.closeEntry();
					prefix = key;
				} else {
					prefix = LEVEL_CHOICE_MAPS_ENTRY_PREFIX;
					if (firstLT) {
						output.putNextEntry(prefix);
						output.closeEntry();
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
					
					output.putNextEntry(mapFileName);
					ArbDiscrGeoDataSet.writeXYZStream(xyz, output.getOutputStream());
					output.closeEntry();
				}
			}
		}
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		if (quickGridExec != null)
			quickGridExec.shutdown();
		
		// write out mean curves
		// write out branch-specific averages
		Preconditions.checkState(myAverageDir.exists() || myAverageDir.mkdir());
		if (runningMeanCurves != null) {
			for (int p=0; p<periods.length; p++) {
				String imt;
				if (periods[p] == -1d)
					imt = "PGV";
				else if (periods[p] == 0d)
					imt = "PGA";
				else
					imt = (float)periods[p]+"s";
				debug("Caching "+imt+" mean curves to "+myAverageDir.getAbsolutePath());
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
		return getSolDir(outputDir, branch, mkdir);
	}
	
	private File getSolDir(File outputDir, LogicTreeBranch<?> branch, boolean mkdir) {
		return branch.getBranchDirectory(outputDir, mkdir);
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
	
	private GriddedRegion detectRegion(FaultSystemSolution sol) {
//		Region region = new ReportMetadata(new RupSetMetadata(null, sol)).region;
		Region region = ReportMetadata.detectRegion(sol);
		return new GriddedRegion(region, gridSpacing, GriddedRegion.ANCHOR_0_0);
	}
	
	private File getHazardOutputDir(File runDir, LogicTreeBranch<?> branch) {
		File hazardOutDir = new File(runDir, hazardSubDirName);
		
		if (gridSeisOp == IncludeBackgroundOption.ONLY) {
			// we're gridded seismicity only, see if there's an upstream directory we should be writing to
			// that will be true if not all fault-based branches affect gridded seismicity
			File onlyHazardDir = getGriddedOnlyHazardDir(branch, outputDir, null);
			if (onlyHazardDir != null) {
				debug("Will write any outputs to upstream gridded-only hazard directory: "+onlyHazardDir.getAbsolutePath());
				// make sure the upstream directory exist (create if not)
				File parentDir = onlyHazardDir.getParentFile();
				Preconditions.checkState(parentDir.exists() || parentDir.mkdir());
				hazardOutDir = onlyHazardDir;
			}
		}
		return hazardOutDir;
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		for (int index : batch) {
			System.gc();
			LogicTreeBranch<?> branch = solTree.getLogicTree().getBranch(index);
			
			debug("Loading index "+index+": "+branch);
			
			FaultSystemSolution sol = null;
			
			if (gridRegion == null) {
				sol = solTree.forBranch(branch);
				gridRegion = detectRegion(sol);
			}
			
			File runDir = getSolDir(branch);
			
			File hazardOutDir = getHazardOutputDir(runDir, branch);
			Preconditions.checkState(hazardOutDir.exists() || hazardOutDir.mkdir());
			
			String curvesPrefix = "curves";
			
			SolHazardMapCalc calc = null;
			if (hazardOutDir.exists()) {
				// see if it's already done
				try {
					calc = SolHazardMapCalc.loadCurves(sol, gridRegion, periods, hazardOutDir, curvesPrefix);
				} catch (Exception e) {
					debug("Hazard subdir ('"+hazardSubDirName+"') exsists, but couldn't be reused: "+e.getMessage());
				}
			}
			SolHazardMapCalc combineWithExcludeCurves = null;
			SolHazardMapCalc combineWithOnlyCurves = null;
			
			if (calc == null) {
				// not already done, see if we can load any partial results
				List<File> combineFromDirs = new ArrayList<>();
				combineFromDirs.add(null); // no combine
				if (combineWithOtherDirs != null)
					combineFromDirs.addAll(combineWithOtherDirs);
//				List<File> combineFromRunDirs = new ArrayList<>();
//				combineFromRunDirs.add(runDir);
//				if (combineWithOtherDirs != null) {
//					for (File combineWithOtherDir : combineWithOtherDirs) {
//						File tmp = getSolDir(combineWithOtherDir, branch, false);
//						if (tmp.exists())
//							combineFromRunDirs.add(tmp);
//					}
//				}
				
				if (gridSeisOp != IncludeBackgroundOption.EXCLUDE) {
					// we're calculating with gridded seismicity
					// lets see if we've already calculated without it
					
					if (gridSeisOp != IncludeBackgroundOption.ONLY) {
						for (File sourceDir : combineFromDirs) {
							File combineFromRunDir;
							boolean verbose;
							if (sourceDir == null) {
								verbose = false; // don't print that we're trying, it's our own directory that we just created so of course it exists
								combineFromRunDir = runDir;
							} else {
								verbose = true; // if it exists, state that we're trying to load
								combineFromRunDir = getSolDir(sourceDir, branch, false);
							}
							File combineWithSubDir = new File(combineFromRunDir, combineWithHazardExcludingSubDirName);
							
							if (combineWithSubDir.exists()) {
								if (verbose)
									debug("Seeing if we can reuse existing curves excluding gridded seismicity from "
											+combineWithSubDir.getAbsolutePath());
								try {
									combineWithExcludeCurves = SolHazardMapCalc.loadCurves(sol, gridRegion, periods, combineWithSubDir, curvesPrefix);
								} catch (Exception e) {
									if (verbose)
										debug("Can't reuse: "+e.getMessage());
								}
							}
							if (combineWithExcludeCurves == null) {
								// see if this is a gridded seismicity branch, but it exists already in an upstream branch
								List<LogicTreeLevel<? extends LogicTreeNode>> faultLevels = new ArrayList<>();
								List<LogicTreeNode> faultNodes = new ArrayList<>();
								for (int i=0; i<branch.size(); i++) {
									LogicTreeLevel<?> level = branch.getLevel(i);
									LogicTreeNode node = branch.getValue(i);
									if (level.affects(FaultSystemSolution.RATES_FILE_NAME, true)
											|| node instanceof ScalarIMRsLogicTreeNode || node instanceof ScalarIMR_ParamsLogicTreeNode) {
										faultLevels.add(level);
										faultNodes.add(branch.getValue(i));
									}
								}
								if (faultLevels.size() < branch.size()) {
									// we have gridded seismicity only branches
									LogicTreeBranch<LogicTreeNode> subBranch = new LogicTreeBranch<>(faultLevels, faultNodes);
									File subRunDir = getSolDir(sourceDir == null ? runDir : sourceDir, subBranch, false);
									File subHazardDir = new File(subRunDir, combineWithHazardExcludingSubDirName);
									if (subHazardDir.exists()) {
										try {
											if (verbose)
												debug("Seeing if we can reuse existing curves excluding gridded seismicity from "
														+subHazardDir.getAbsolutePath());
											combineWithExcludeCurves = SolHazardMapCalc.loadCurves(sol, gridRegion, periods, subHazardDir, curvesPrefix);
										} catch (Exception e) {
											if (verbose)
												debug("Can't reuse: "+e.getMessage());
										}
									}
								}
							}
							if (combineWithExcludeCurves != null)
								break;
						}
					}
					
					// now see if we've calculated with background only
					for (File sourceDir : combineFromDirs) {
						File combineFromRunDir;
						if (sourceDir == null)
							combineFromRunDir = runDir;
						else
							combineFromRunDir = getSolDir(sourceDir, branch, false);
						File combineWithSubDir = new File(combineFromRunDir, combineWithHazardBGOnlySubDirName);
						
						if (combineWithSubDir.exists()) {
							debug("Seeing if we can reuse existing curves with only gridded seismicity from "+combineWithSubDir.getAbsolutePath());
							try {
								combineWithOnlyCurves = SolHazardMapCalc.loadCurves(sol, gridRegion, periods, combineWithSubDir, curvesPrefix);
							} catch (Exception e) {
								debug("Can't reuse: "+e.getMessage());
							}
						}
						
						if (combineWithOnlyCurves == null) {
							// see if this gridded seismicity exists already in an upstream branch
							File subHazardDir = getGriddedOnlyHazardDir(branch, sourceDir, combineFromRunDir);
							if (subHazardDir != null) {
								// gridded siesmicity might exist upstream
								debug("testing gridLevels dir: "+subHazardDir.getAbsolutePath());
								if (subHazardDir.exists()) {
									try {
										debug("Seeing if we can reuse existing curves with only gridded seismicity from "+subHazardDir.getAbsolutePath());
										combineWithOnlyCurves = SolHazardMapCalc.loadCurves(sol, gridRegion, periods, subHazardDir, curvesPrefix);
									} catch (Exception e) {
										debug("Can't reuse: "+e.getMessage());
									}
								}
							}
						}
						
						if (combineWithOnlyCurves != null)
							break;
					}
				}
				
				if (combineWithOnlyCurves == null && externalGridProv != null) {
					// external grid source provider calculation
					if (externalGriddedCurveCalc == null) {
						if (sol == null)
							sol = solTree.forBranch(branch);
						// first time, calculate them
						debug("Calculating external grid source provider curves (will only do this once)");
						
						FaultSystemSolution extSol = new FaultSystemSolution(sol.getRupSet(), sol.getRateForAllRups());
						extSol.setGridSourceProvider(externalGridProv);
						
						externalGriddedCurveCalc = new SolHazardMapCalc(extSol,
								FaultSysHazardCalcSettings.getGMM_Suppliers(branch, gmmRefs, true), gridRegion,
								IncludeBackgroundOption.ONLY, applyAftershockFilter, periods);
						
						externalGriddedCurveCalc.setSourceFilter(sourceFilter);
						externalGriddedCurveCalc.setSiteSkipSourceFilter(siteSkipSourceFilter);
						externalGriddedCurveCalc.setGriddedSeismicitySettings(griddedSettings);
						externalGriddedCurveCalc.setCacheGridSources(true);
						
						externalGriddedCurveCalc.calcHazardCurves(getNumThreads());
					}
					
					combineWithOnlyCurves = externalGriddedCurveCalc;
				}
				
				if (externalSol != null && gridSeisOp != IncludeBackgroundOption.ONLY && combineWithExcludeCurves == null) {
					// we're combining with exclude curves from an external solution file
					if (externalSolCurveCalc == null) {
						if (sol == null)
							sol = solTree.forBranch(branch);
						// first time, calculate them
						debug("Calculating external solution curves (will only do this once)");
						
						externalSolCurveCalc = new SolHazardMapCalc(externalSol,
								FaultSysHazardCalcSettings.getGMM_Suppliers(branch, gmmRefs, true), gridRegion,
								IncludeBackgroundOption.EXCLUDE, applyAftershockFilter, periods);
						
						externalSolCurveCalc.setSourceFilter(sourceFilter);
						externalSolCurveCalc.setSiteSkipSourceFilter(siteSkipSourceFilter);
						externalSolCurveCalc.setCacheGridSources(false);
						
						externalSolCurveCalc.calcHazardCurves(getNumThreads());
					}
					
					combineWithExcludeCurves = externalSolCurveCalc;
				}
				
				if (quickGridCalcs != null && combineWithOnlyCurves == null) {
					Preconditions.checkState(!combineOnly, "Combine-only flag is set, but we need to calculate gridded only for "+branch);
					if (sol == null)
						sol = solTree.forBranch(branch);
					QuickGriddedHazardMapCalc[] quickGridCalcs = this.quickGridCalcs;
					if (branch.hasValue(ScalarIMRsLogicTreeNode.class) || branch.hasValue(ScalarIMR_ParamsLogicTreeNode.class)) {
						// need to make custom Ones
						quickGridCalcs = new QuickGriddedHazardMapCalc[periods.length];
						for (int p=0; p<periods.length; p++)
							quickGridCalcs[p] = new QuickGriddedHazardMapCalc(
									FaultSysHazardCalcSettings.getGMM_Suppliers(branch, gmmRefs, true), periods[p],
									FaultSysHazardCalcSettings.getDefaultXVals(periods[p]), sourceFilter, griddedSettings);
					}
					debug("Doing quick gridded seismicity calc for "+index);
					List<DiscretizedFunc[]> curves = new ArrayList<>();
					if (quickGridExec == null)
						quickGridExec = Executors.newFixedThreadPool(getNumThreads());
					for (int p=0; p<periods.length; p++)
						curves.add(quickGridCalcs[p].calc(sol.getGridSourceProvider(), gridRegion, quickGridExec, getNumThreads()));
					combineWithOnlyCurves = SolHazardMapCalc.forCurves(sol, gridRegion, periods, curves);
					if (gridSeisOp == IncludeBackgroundOption.ONLY)
						// we'll probably be combining later, write out the curves
						combineWithOnlyCurves.writeCurvesCSVs(hazardOutDir, curvesPrefix, true);
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
					calc.writeCurvesCSVs(hazardOutDir, curvesPrefix, true);
				} else if (calc == null && gridSeisOp == IncludeBackgroundOption.ONLY && combineWithOnlyCurves != null) {
					calc = combineWithOnlyCurves;
				}
			}
			
			if (calc == null) {
				if (sol == null)
					sol = solTree.forBranch(branch);
				Map<TectonicRegionType, ? extends Supplier<ScalarIMR>> gmpeSuppliers = FaultSysHazardCalcSettings.getGMM_Suppliers(branch, gmmRefs, true);
				String gmpeParamsStr = "";
				if (gmpeSuppliers.size() == 1) {
					ScalarIMR gmpe = gmpeSuppliers.values().iterator().next().get();
					gmpeParamsStr = "\n\tGMPE: "+gmpe.getName();
					for (Parameter<?> param : gmpe.getOtherParams())
						gmpeParamsStr += "; "+param.getName()+": "+param.getValue();
				}
				debug("Calculating hazard curves for "+index+", bgOption="+gridSeisOp.name()
						+", combineExclude="+(combineWithExcludeCurves != null)
						+", combineOnly="+combineOnly
						+"\n\tBranch: "+branch
						+gmpeParamsStr);
				Preconditions.checkState(!combineOnly, "Combine-only flag is set, but we need to calculate for "+branch);
				SolHazardMapCalc combineWithCurves = null;
				if (combineWithExcludeCurves == null && combineWithOnlyCurves == null) {
					calc = new SolHazardMapCalc(sol, gmpeSuppliers, gridRegion, gridSeisOp, applyAftershockFilter, periods);
				} else if (combineWithExcludeCurves != null) {
					// calculate with only gridded seismicity, we'll add in the curves excluding it
					debug("Reusing fault-based hazard for "+index+", will only compute gridded hazard");
					combineWithCurves = combineWithExcludeCurves;
					calc = new SolHazardMapCalc(sol, gmpeSuppliers, gridRegion, IncludeBackgroundOption.ONLY, applyAftershockFilter, periods);
				} else if (combineWithOnlyCurves != null) {
					// calculate without gridded seismicity, we'll add in the curves with it
					debug("Reusing fault-based hazard for "+index+", will only compute gridded hazard");
					combineWithCurves = combineWithOnlyCurves;
					calc = new SolHazardMapCalc(sol, gmpeSuppliers, gridRegion, IncludeBackgroundOption.EXCLUDE, applyAftershockFilter, periods);
				}
				calc.setSourceFilter(sourceFilter);
				calc.setSiteSkipSourceFilter(siteSkipSourceFilter);
				calc.setGriddedSeismicitySettings(griddedSettings);
				calc.setCacheGridSources(true);
				calc.setAseisReducesArea(aseisReducesArea);
				calc.setNoMFDs(noMFDs);
				calc.setUseProxyRups(!noProxyRups);
				
				calc.calcHazardCurves(getNumThreads(), combineWithCurves);
				calc.writeCurvesCSVs(hazardOutDir, curvesPrefix, true);
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
					
					AbstractXYZ_DataSet.writeXYZFile(map, new File(hazardOutDir, prefix+".txt"));
				}
			}
		}
	}
	
	private File getGriddedOnlyHazardDir(LogicTreeBranch<?> branch, File sourceDir, File combineFromRunDir) {
		// see if this gridded seismicity exists already in an upstream branch
		List<LogicTreeLevel<? extends LogicTreeNode>> gridLevels = new ArrayList<>();
		List<LogicTreeNode> gridNodes = new ArrayList<>();
		for (int i=0; i<branch.size(); i++) {
			LogicTreeLevel<?> level = branch.getLevel(i);
			LogicTreeNode node = branch.getValue(i);
			if (GridSourceProvider.affectedByLevel(level)
					|| node instanceof ScalarIMRsLogicTreeNode || node instanceof ScalarIMR_ParamsLogicTreeNode) {
				gridLevels.add(level);
				gridNodes.add(branch.getValue(i));
			}
		}
		if (gridLevels.size() < branch.size()) {
			// gridded siesmicity might exist upstream
			LogicTreeBranch<LogicTreeNode> subBranch = new LogicTreeBranch<>(gridLevels, gridNodes);
			File subRunDir = getSolDir(sourceDir == null ? outputDir : sourceDir, subBranch, false);
			File subHazardDir = new File(subRunDir, combineWithHazardBGOnlySubDirName);
			if (combineFromRunDir != null && !subHazardDir.exists()) {
				// try our dir, we were probably combining with a fault run and might have gridded here
				subRunDir = getSolDir(outputDir, subBranch, false);
				subHazardDir = new File(subRunDir, combineWithHazardBGOnlySubDirName);
			}
			return subHazardDir;
		}
		return null;
	}
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		FaultSysHazardCalcSettings.addCommonOptions(ops, true);
		
		ops.addRequiredOption("if", "input-file", true, "Path to input file (solution logic tree zip)");
		ops.addOption("lt", "logic-tree", true, "Path to logic tree JSON file, required if a results directory is "
				+ "supplied with --input-file");
		ops.addRequiredOption("od", "output-dir", true, "Path to output directory");
		ops.addOption("of", "output-file", true, "Path to output zip file. Default will be based on the output directory");
		ops.addOption("sp", "grid-spacing", true, "Grid spacing in decimal degrees. Default: "+(float)GRID_SPACING_DEFAULT);
		ops.addOption("gs", "gridded-seis", true, "Gridded seismicity option. One of "
				+FaultSysTools.enumOptions(IncludeBackgroundOption.class)+". Default: "+GRID_SEIS_DEFAULT.name());
		ops.addOption("r", "region", true, "Optional path to GeoJSON file containing a region for which we should compute hazard. "
				+ "Can be a gridded region or an outline. If not supplied, then one will be detected from the model. If "
				+ "a zip file is supplied, then it is assumed that the file is a prior hazard calculation zip file and the "
				+ "region will be reused from that prior calculation.");
		ops.addOption("af", "aftershock-filter", false, "If supplied, the aftershock filter will be applied in the ERF");
		ops.addOption(null, "aseis-reduces-area", false, "If supplied, aseismicity area reductions are enabled");
		ops.addOption(null, "no-aseis-reduces-area", false, "If supplied, aseismicity area reductions are disabled");
		ops.addOption("egp", "external-grid-prov", true, "Path to external grid source provider to use for hazard "
				+ "calculations. Can be either a fault system solution, or a zip file containing just a grid source "
				+ "provider.");
		ops.addOption(null, "external-fss", true, "Path to external fault-system solution to use for hazard "
				+ "calculations; the regular input-file will only be used for gridded-seismicity.");
		ops.addOption("qgc", "quick-grid-calc", false, "Flag to enable quick gridded seismicity calculation.");
		ops.addOption("cwd", "combine-with-dir", true, "Path to a different directory to serach for pre-computed curves "
				+ "to draw from. Can supply multiple times to specify multiple directories.");
		ops.addOption(null, "combine-only", false, "Flag to ensure that no actual calculations are done, just combinations.");
		ops.addOption(null, "no-mfds", false, "Flag to disable rupture MFDs, i.e., use a single magnitude for all "
				+ "ruptures in the case of a branch-averaged solution");
		ops.addOption(null, "no-proxy-ruptures", false, "Flag to disable proxy ruptures MFDs, i.e., use a single proxy "
				+ "fault instead of distributed proxies that fill the source zone");
		
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

