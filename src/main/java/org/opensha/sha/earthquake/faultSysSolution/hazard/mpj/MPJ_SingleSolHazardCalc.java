package org.opensha.sha.earthquake.faultSysSolution.hazard.mpj;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.calc.sourceFilters.SourceFilterManager;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.faultSysSolution.hazard.QuickGriddedHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.modules.AbstractLogicTreeModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysHazardCalcSettings;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.util.GriddedSeismicitySettings;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.AttenRelSupplier;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.logicTree.ScalarIMR_ParamsLogicTreeNode;
import org.opensha.sha.imr.logicTree.ScalarIMRsLogicTreeNode;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;
import mpi.MPI;

public class MPJ_SingleSolHazardCalc extends MPJTaskCalculator {

	private File outputDir;

	private LogicTree<?> tree;
	private FaultSystemSolution singleSol;
	
	private double gridSpacing = MPJ_LogicTreeHazardCalc.GRID_SPACING_DEFAULT;
	
	private Map<TectonicRegionType, AttenRelSupplier> gmmRefs;
	
	private double[] periods = MPJ_LogicTreeHazardCalc.PERIODS_DEFAULT;
	
	private ReturnPeriods[] rps = SolHazardMapCalc.MAP_RPS;
	
	private IncludeBackgroundOption gridSeisOp = MPJ_LogicTreeHazardCalc.GRID_SEIS_DEFAULT;
	
	private GriddedSeismicitySettings griddedSettings;
	
	private boolean applyAftershockFilter = MPJ_LogicTreeHazardCalc.AFTERSHOCK_FILTER_DEFAULT;
	
	private boolean aseisReducesArea = MPJ_LogicTreeHazardCalc.ASEIS_REDUCES_AREA_DEFAULT;
	
	private SourceFilterManager sourceFilter;
	
	private SourceFilterManager siteSkipSourceFilter;
	
	private GriddedRegion gridRegion;

	private File combineWithOtherDir;
	
	private String hazardSubDirName;
	private String combineWithHazardExcludingSubDirName;
	private String combineWithHazardBGOnlySubDirName;
	
	private File nodesCurveDir;
	
	private File outputFile;
	
	private GridSourceProvider externalGridProv;
	private SolHazardMapCalc externalGriddedCurveCalc;
	
	private QuickGriddedHazardMapCalc[] quickGridCalcs;
	private ExecutorService quickGridExec;
	
	private boolean noMFDs;
	private boolean noProxyRups;

	public MPJ_SingleSolHazardCalc(CommandLine cmd) throws IOException {
		super(cmd);
		
		this.shuffle = true;
		
		File inputFile = new File(cmd.getOptionValue("input-file"));
		Preconditions.checkState(inputFile.exists());
		if (inputFile.isDirectory()) {
			Preconditions.checkArgument(cmd.hasOption("logic-tree"), "Must supply logic tree file if input-file is"
					+ " a results directory");
			File logicTreeFile = new File(cmd.getOptionValue("logic-tree"));
			Preconditions.checkArgument(logicTreeFile.exists(), "Logic tree file doesn't exist: %s",
					logicTreeFile.getAbsolutePath());
			tree = LogicTree.read(logicTreeFile);
			
			SolutionLogicTree solTree = new SolutionLogicTree.ResultsDirReader(inputFile, tree);
			Preconditions.checkArgument(solTree.getLogicTree().size() == 1,
					"Must only have one solution with this calculator");
			
			LogicTreeBranch<?> branch = solTree.getLogicTree().getBranch(0);
			singleSol = solTree.forBranch(branch);
			singleSol.addModule(branch);
		} else {
			ZipFile zip = new ZipFile(inputFile);
			if (FaultSystemSolution.isSolution(zip)) {
				// solution file
				singleSol = FaultSystemSolution.load(zip);
			} else {
				// it should be SolutionLogicTree zip file
				SolutionLogicTree solTree = SolutionLogicTree.load(inputFile);
				tree = solTree.getLogicTree();
				Preconditions.checkArgument(tree.size() == 1,
						"Must only have one solution with this calculator");
				LogicTreeBranch<?> branch = solTree.getLogicTree().getBranch(0);
				singleSol = solTree.forBranch(branch);
				singleSol.addModule(branch);
			}
		}
		
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
				ZipEntry regEntry = zip.getEntry(MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME);
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
		} else {
			// detect it
			gridRegion = detectRegion(singleSol);
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
		
		if (cmd.hasOption("combine-with-dir")) {
			combineWithOtherDir = new File(cmd.getOptionValue("combine-with-dir"));
			if (!combineWithOtherDir.exists())
				combineWithOtherDir = null;
		}
		
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
			MPJ_LogicTreeHazardCalc.waitOnDir(outputDir, 5, 1000);
			
			if (cmd.hasOption("output-file"))
				outputFile = new File(cmd.getOptionValue("output-file"));
			else
				outputFile = new File(outputDir.getParentFile(), "results_hazard.zip");
			
			File simDir = getSolDir(singleSol.getModule(LogicTreeBranch.class));
			MPJ_LogicTreeHazardCalc.waitOnDir(simDir, 5, 1000);
			
			File hazardSubDir = new File(simDir, hazardSubDirName);
			MPJ_LogicTreeHazardCalc.waitOnDir(hazardSubDir, 5, 1000);
		}
		
		nodesCurveDir = new File(outputDir, "node_hazard_curves_"+gridSeisOp.name());
		if (rank == 0 && !SINGLE_NODE_NO_MPJ) {
			if (nodesCurveDir.exists()) {
				// delete anything preexisting
				for (File file : nodesCurveDir.listFiles())
					Preconditions.checkState(FileUtils.deleteRecursive(file));
			} else {
				Preconditions.checkState(nodesCurveDir.mkdir() || nodesCurveDir.exists());
			}
		}
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		// write out branch curves
		if (!SINGLE_NODE_NO_MPJ) {
			String prefix = "node_"+rank+"_curves";
			if (calc != null) {
				// we have calculated some
				debug("Writing curves CSVs");
				calc.writeCurvesCSVs(nodesCurveDir, prefix, false, true);
				debug("DONE writing CSVs");
			} else {
				// we've calculated none
				// write empty CSVs
				for (double period : periods) {
					String fileName = SolHazardMapCalc.getCSV_FileName(prefix, period);
					File outputFile = new File(nodesCurveDir, fileName);
					
					CSVFile<String> csv = new CSVFile<>(true);
					
					List<String> header = new ArrayList<>();
					header.add("Index");
					header.add("Latitude");
					header.add("Longitude");
					csv.addLine(header);
					
					csv.writeToFile(outputFile);
				}
			}
		}
		
		// wait for everyone to finish writing
		if (!SINGLE_NODE_NO_MPJ)
			MPI.COMM_WORLD.Barrier();
		
		if (rank == 0) {
			// load them in
			List<DiscretizedFunc[]> curvesList = new ArrayList<>(periods.length);
			for (int p=0; p<periods.length; p++)
				curvesList.add(new DiscretizedFunc[gridRegion.getNodeCount()]);
			
			debug("Reading node curves");
			for (int rank=0; rank<size; rank++) {
				List<DiscretizedFunc[]> nodeCurves;
				if (rank == 0 && calc != null) {
					// grab them directly in memory
					nodeCurves = new ArrayList<>();
					for (double period : periods)
						nodeCurves.add(calc.getCurves(period));
				} else {
					// read it
					nodeCurves = null;
					boolean anyEmpty = false;
					boolean allEmpty = true;
					for (int p=0; p<periods.length; p++) {
						File curvesFile = new File(nodesCurveDir,
								SolHazardMapCalc.getCSV_FileName("node_"+rank+"_curves", periods[p]));
						if (curvesFile.exists()) {
							if (p == 0)
								nodeCurves = new ArrayList<>(periods.length);
							else
								Preconditions.checkNotNull(nodeCurves,
										"Have curves for p=%s rank=%s, but not an earlier period",
										(Double)periods[p], rank);
							CSVFile<String> csv = CSVFile.readFile(curvesFile, true);
							if (csv.getNumRows() == 1) {
								anyEmpty = true;
							} else {
								allEmpty = false;
								nodeCurves.add(SolHazardMapCalc.loadCurvesCSV(csv, gridRegion, true));
							}
						} else {
							Preconditions.checkState(curvesFile == null,
									"Don't have curves for p=%s rank=%s, but did for an earlier period",
									(Double)periods[p], rank);
						}
					}
					if (anyEmpty) {
						Preconditions.checkState(allEmpty);
						nodeCurves = null;
					}
				}
				if (nodeCurves != null) {
					// merge them in
					for (int p=0; p<periods.length; p++) {
						DiscretizedFunc[] curves = curvesList.get(p);
						DiscretizedFunc[] node = nodeCurves.get(p);
						Preconditions.checkState(node.length == curves.length);
						for (int i=0; i<node.length; i++) {
							if (node[i] != null) {
								Preconditions.checkState(curves[i] == null,
										"Duplicate curve for p=%s index=%s", (Double)periods[p], i);
								curves[i] = node[i];
							}
						}
					}
				}
			}
			
			// make sure we loaded everything
			for (int p=0; p<periods.length; p++) {
				DiscretizedFunc[] curves = curvesList.get(p);
				for (int i=0; i<curves.length; i++)
					Preconditions.checkNotNull(curves[i], "Missing curves for p=%s and index=%s", (Double)periods[p], i);
			}
			
			calc = SolHazardMapCalc.forCurves(singleSol, gridRegion, periods, curvesList);
			File runDir = getSolDir(singleSol.getModule(LogicTreeBranch.class));
			
			File hazardSubDir = new File(runDir, hazardSubDirName);
			Preconditions.checkState(hazardSubDir.exists() || hazardSubDir.mkdir());
			calc.writeCurvesCSVs(hazardSubDir, "curves", true);
			
			// build the zip
			File workingFile = new File(outputFile.getAbsolutePath()+".tmp");
			ZipOutputStream zout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(workingFile)));
			
			// write grid region
			Feature feature = gridRegion.toFeature();
			ZipEntry entry = new ZipEntry(MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME);
			
			zout.putNextEntry(entry);
			BufferedOutputStream out = new BufferedOutputStream(zout);
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
			Feature.write(feature, writer);
			out.flush();
			zout.closeEntry();
			
			// write logic tree
			if (tree != null) {
				entry = new ZipEntry(AbstractLogicTreeModule.LOGIC_TREE_FILE_NAME);
				zout.putNextEntry(entry);
				Gson gson = new GsonBuilder().setPrettyPrinting()
						.registerTypeAdapter(LogicTree.class, new LogicTree.Adapter<>()).create();
				gson.toJson(tree, LogicTree.class, writer);
				writer.flush();
				zout.flush();
				zout.closeEntry();
			}
			
			// write maps
			if (tree != null) {
				zout.putNextEntry(new ZipEntry(runDir.getName()+"/"));
				zout.closeEntry();
				zout.flush();
			}
			for (ReturnPeriods rp : rps) {
				for (double period : periods) {
					GriddedGeoDataSet map = calc.buildMap(period, rp);

					String prefix = MPJ_LogicTreeHazardCalc.mapPrefix(period, rp);

					File mapFile = new File(hazardSubDir, prefix+".txt");
					// write to file
					AbstractXYZ_DataSet.writeXYZFile(map, mapFile);
					
					// write to zip
					ZipEntry mapEntry;
					if (tree == null)
						mapEntry = new ZipEntry(mapFile.getName());
					else
						mapEntry = new ZipEntry(runDir.getName()+"/"+mapFile.getName());
					debug("Async: zipping "+mapEntry.getName());
					zout.putNextEntry(mapEntry);
					AbstractXYZ_DataSet.writeXYZStream(map, zout);
					zout.flush();
					zout.closeEntry();
				}
			}
			
			zout.close();
			Files.move(workingFile, outputFile);
		}
	}

	@Override
	protected int getNumTasks() {
		return gridRegion.getNodeCount();
	}
	
	protected File getSolDir(LogicTreeBranch<?> branch) {
		return getSolDir(outputDir, branch);
	}
	
	private File getSolDir(File outputDir, LogicTreeBranch<?> branch) {
		if (tree == null)
			return outputDir;
		Preconditions.checkNotNull(branch, "We have a tree but branch is null?");
		return branch.getBranchDirectory(outputDir, true);
	}
	
	private GriddedRegion detectRegion(FaultSystemSolution sol) {
//		Region region = new ReportMetadata(new RupSetMetadata(null, sol)).region;
		Region region = ReportMetadata.detectRegion(sol);
		return new GriddedRegion(region, gridSpacing, GriddedRegion.ANCHOR_0_0);
	}
	

	private SolHazardMapCalc combineWithExcludeCurves = null;
	private SolHazardMapCalc combineWithOnlyCurves = null;
	private SolHazardMapCalc combineWithCurves = null;
	private SolHazardMapCalc calc = null;
	private boolean externalDone = false;
	
	private BaseFaultSystemSolutionERF erf;
	
	private boolean existsAndHazCurves(File dir, String prefix) {
		if (!dir.exists() || !dir.isDirectory())
			return false;
		for (double period : periods) {
			File curvesFile = new File(dir, SolHazardMapCalc.getCSV_FileName(prefix, period));
			if (!curvesFile.exists())
				curvesFile = new File(curvesFile.getAbsolutePath()+".gz");
			if (!curvesFile.exists())
				return false;
		}
		return true;
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		if (externalDone)
			// we were just combining prior calcs and already did so
			return;
		Preconditions.checkState(batch.length > 0);
		List<Integer> calcIndexes = Ints.asList(batch);
		
		LogicTreeBranch<?> branch = singleSol.getModule(LogicTreeBranch.class);
		File runDir = getSolDir(branch);
		
		File hazardSubDir = new File(runDir, hazardSubDirName);
		Preconditions.checkState(hazardSubDir.exists());
		
		String curvesPrefix = "curves";
		
		if (calc == null) {
			// not already done, see if we can load any partial results
			File combineFromRunDir = runDir;
			if (combineWithOtherDir != null) {
				File tmp = getSolDir(combineWithOtherDir, branch);
				if (tmp.exists())
					combineFromRunDir = tmp;
			}
			
			if (gridSeisOp != IncludeBackgroundOption.EXCLUDE) {
				// we're calculating with gridded seismicity
				// lets see if we've already calculated without it
				
				if (gridSeisOp != IncludeBackgroundOption.ONLY && combineWithExcludeCurves == null) {
					File combineWithSubDir = new File(combineFromRunDir, combineWithHazardExcludingSubDirName);
					
					if (existsAndHazCurves(combineWithSubDir, curvesPrefix)) {
						debug("Seeing if we can reuse existing curves excluding gridded seismicity from "+combineWithSubDir.getAbsolutePath());
						try {
							combineWithExcludeCurves = SolHazardMapCalc.loadCurves(singleSol, gridRegion, periods, combineWithSubDir, curvesPrefix);
						} catch (Exception e) {
							debug("Can't reuse: "+e.getMessage());
						}
					}
				}
				
				// now see if we've calculated with background only
				File combineWithSubDir = new File(combineFromRunDir, combineWithHazardBGOnlySubDirName);
				
				if (existsAndHazCurves(combineWithSubDir, curvesPrefix) && combineWithOnlyCurves == null) {
					debug("Seeing if we can reuse existing curves with only gridded seismicity from "+combineWithSubDir.getAbsolutePath());
					try {
						combineWithOnlyCurves = SolHazardMapCalc.loadCurves(singleSol, gridRegion, periods, combineWithSubDir, curvesPrefix);
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
					
					FaultSystemSolution extSol = new FaultSystemSolution(singleSol.getRupSet(), singleSol.getRateForAllRups());
					extSol.setGridSourceProvider(externalGridProv);
					
					externalGriddedCurveCalc = new SolHazardMapCalc(extSol, gmmRefs, gridRegion,
							IncludeBackgroundOption.ONLY, applyAftershockFilter, periods);
					
					externalGriddedCurveCalc.setSourceFilter(sourceFilter);
					externalGriddedCurveCalc.setSiteSkipSourceFilter(siteSkipSourceFilter);
					externalGriddedCurveCalc.setGriddedSeismicitySettings(griddedSettings);
					externalGriddedCurveCalc.setCacheGridSources(true);
					
					externalGriddedCurveCalc.calcHazardCurves(getNumThreads());
				}
				
				combineWithOnlyCurves = externalGriddedCurveCalc;
			}
			
			if (quickGridCalcs != null && combineWithOnlyCurves == null) {
				debug("Doing quick gridded seismicity calc");
				List<DiscretizedFunc[]> curves = new ArrayList<>();
				if (quickGridExec == null)
					quickGridExec = Executors.newFixedThreadPool(getNumThreads());
				for (int p=0; p<periods.length; p++)
					curves.add(quickGridCalcs[p].calc(singleSol.getGridSourceProvider(), gridRegion, quickGridExec, getNumThreads()));
				combineWithOnlyCurves = SolHazardMapCalc.forCurves(singleSol, gridRegion, periods, curves);
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
							combCurve = curve1.deepClone();
							SolHazardMapCalc.combineIn(combCurve, curve2);
						}
						
						combCurves[i] = combCurve;
					}
					combCurvesList.add(combCurves);
				}
				calc = SolHazardMapCalc.forCurves(singleSol, gridRegion, periods, combCurvesList);
				externalDone = true;
				return;
			} else if (calc == null && gridSeisOp == IncludeBackgroundOption.ONLY && combineWithOnlyCurves != null) {
				calc = combineWithOnlyCurves;
				externalDone = true;
				return;
			}
		}
		
		if (calc == null) {
			Map<TectonicRegionType, ? extends Supplier<ScalarIMR>> gmpeSuppliers = FaultSysHazardCalcSettings.getGMM_Suppliers(branch, gmmRefs, true);
			if (gmpeSuppliers.size() == 1) {
				ScalarIMR gmpe = gmpeSuppliers.values().iterator().next().get();
				String gmpeParamsStr = "GMPE: "+gmpe.getName();
				for (Parameter<?> param : gmpe.getOtherParams())
					gmpeParamsStr += "; "+param.getName()+": "+param.getValue();
				debug(gmpeParamsStr);
			}
			if (combineWithExcludeCurves == null && combineWithOnlyCurves == null) {
				calc = new SolHazardMapCalc(singleSol, gmpeSuppliers, gridRegion, gridSeisOp, applyAftershockFilter, periods);
			} else if (combineWithExcludeCurves != null) {
				// calculate with only gridded seismicity, we'll add in the curves excluding it
				debug("Reusing fault-based hazard for "+batch.length+" sites, will only compute gridded hazard");
				combineWithCurves = combineWithExcludeCurves;
				calc = new SolHazardMapCalc(singleSol, gmpeSuppliers, gridRegion, IncludeBackgroundOption.ONLY, applyAftershockFilter, periods);
			} else if (combineWithOnlyCurves != null) {
				// calculate without gridded seismicity, we'll add in the curves with it
				debug("Reusing fault-based hazard for "+batch.length+" sites, will only compute gridded hazard");
				combineWithCurves = combineWithOnlyCurves;
				calc = new SolHazardMapCalc(singleSol, gmpeSuppliers, gridRegion, IncludeBackgroundOption.EXCLUDE, applyAftershockFilter, periods);
			}
			calc.setSourceFilter(sourceFilter);
			calc.setSiteSkipSourceFilter(siteSkipSourceFilter);
			calc.setAseisReducesArea(aseisReducesArea);
			calc.setNoMFDs(noMFDs);
			calc.setUseProxyRups(!noProxyRups);
			calc.setGriddedSeismicitySettings(griddedSettings);
			calc.setCacheGridSources(true);
			
			if (erf != null)
				calc.setERF(erf);
		}

		debug("Calculating hazard curves for "+batch.length+" sites, bgOption="+gridSeisOp.name()
		+", combineExclude="+(combineWithExcludeCurves != null)
		+", combineOnly="+(combineWithOnlyCurves != null)
		+"\n\tBranch: "+branch);
		calc.calcHazardCurves(getNumThreads(), calcIndexes, combineWithCurves);
		
		this.erf = calc.getERF();
	}
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		FaultSysHazardCalcSettings.addCommonOptions(ops, true);
		
		ops.addRequiredOption("if", "input-file", true, "Path to input file (solution logic tree zip)");
		ops.addOption("lt", "logic-tree", true, "Path to logic tree JSON file, required if a results directory is "
				+ "supplied with --input-file");
		ops.addRequiredOption("od", "output-dir", true, "Path to output directory");
		ops.addOption("of", "output-file", true, "Path to output zip file. Default will be based on the output directory");
		ops.addOption("sp", "grid-spacing", true, "Grid spacing in decimal degrees. Default: "+(float)MPJ_LogicTreeHazardCalc.GRID_SPACING_DEFAULT);
		ops.addOption("gs", "gridded-seis", true, "Gridded seismicity option. One of "
				+FaultSysTools.enumOptions(IncludeBackgroundOption.class)+". Default: "+MPJ_LogicTreeHazardCalc.GRID_SEIS_DEFAULT.name());
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
		ops.addOption("cwd", "combine-with-dir", true, "Path to a different directory to serach for pre-computed curves "
				+ "to draw from.");
		ops.addOption(null, "no-mfds", false, "Flag to disable rupture MFDs, i.e., use a single magnitude for all "
				+ "ruptures in the case of a branch-averaged solution");
		ops.addOption(null, "no-proxy-ruptures", false, "Flag to disable proxy ruptures MFDs, i.e., use a single proxy "
				+ "fault instead of distributed proxies that fill the source zone");
		ops.addOption("qgc", "quick-grid-calc", false, "Flag to enable quick gridded seismicity calculation.");
		
		return ops;
	}

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		try {
			args = MPJTaskCalculator.initMPJ(args);
			
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJ_SingleSolHazardCalc.class);
			
			MPJ_SingleSolHazardCalc driver = new MPJ_SingleSolHazardCalc(cmd);
			driver.run();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}

