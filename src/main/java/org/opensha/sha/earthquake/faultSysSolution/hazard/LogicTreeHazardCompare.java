package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.descriptive.moment.Variance;
import org.apache.commons.math3.util.MathArrays;
import org.apache.commons.math3.util.Precision;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleEdge;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.GraphPanel;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotPreferences;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.gui.plot.pdf.PDF_UTF8_FontMapper;
import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.FileBackedLevel;
import org.opensha.commons.logicTree.LogicTreeLevel.RandomlySampledLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.ExecutorUtils;
import org.opensha.commons.util.Interpolate;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.hazard.AbstractLTVarianceDecomposition.VarianceContributionResult;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_LogicTreeHazardCalc;
import org.opensha.sha.earthquake.faultSysSolution.modules.AbstractLogicTreeModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.MapPlot;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc.ReturnPeriods;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Doubles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.itextpdf.awt.FontMapper;
import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;

public class LogicTreeHazardCompare {
	
	private static boolean TITLES = false;
	
//	private static final Location DEBUG_LOC = new Location(46, -105);
	private static final Location DEBUG_LOC = null;
	
	public static void main(String[] args) throws IOException {
		System.setProperty("java.awt.headless", "true");
		
		boolean currentWeights = false;
		boolean compCurrentWeights = false;
		
		File mainDir = null;
		String mainName = null;
		
		LogicTreeNode[] subsetNodes = null;
		LogicTreeNode[] compSubsetNodes = null;
//		LogicTreeNode[] compSubsetNodes = { FaultModels.FM3_1 };
		
		File compDir = null;
		String compName = null;
		File outputDir = null;
		
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, LogicTreeHazardCompare.class);
		args = cmd.getArgs();
		
		if (cmd.hasOption("write-pdfs"))
			SolHazardMapCalc.PDFS = true;
		
		boolean forceFileBacked = cmd.hasOption("force-file-backed-lt");
		if (forceFileBacked)
			System.out.println("Forcing file-backed logic trees");
		
		File resultsFile, hazardFile;
		File compResultsFile, compHazardFile;
		LogicTree<?> tree = null;
		LogicTree<?> compTree = null;
		
		boolean ignorePrecomputed = false;
		
		if (args.length > 0) {
			// assume CLI instead
			Preconditions.checkArgument(args.length == 4 || args.length == 7,
					"USAGE: <primary-results-zip> <primary-hazard-zip> <primary-name> [<comparison-results-zip> "
					+ "<comparison-hazard-zip> <comparison-name>] <output-dir>");
			int cnt = 0;
			if (args[cnt].equals("null"))
				resultsFile = null;
			else
				resultsFile = new File(args[cnt]);
			cnt++;
			hazardFile = new File(args[cnt++]);
			if (cmd.hasOption("logic-tree")) {
				File treeFile = new File(cmd.getOptionValue("logic-tree"));
				System.out.println("Reading custom logic tree from: "+treeFile.getAbsolutePath());
				if (treeFile.getName().endsWith("zip"))
					tree = loadTreeFromResults(treeFile, forceFileBacked);
				else if (forceFileBacked)
					tree = LogicTree.readFileBacked(treeFile);
				else
					tree = LogicTree.read(treeFile);
				ignorePrecomputed = true;
			} else {
				// read it from the hazard file if available
				tree = loadTreeFromResults(hazardFile, forceFileBacked);
			}
			if (cmd.hasOption("ignore-precomputed-maps"))
				ignorePrecomputed = true;
			mainName = args[cnt++];
			if (args.length > 4) {
				if (args[cnt].toLowerCase().trim().equals("null")) {
					compResultsFile = null;
					cnt++;
				} else {
					compResultsFile = new File(args[cnt++]);
				}
				compHazardFile = new File(args[cnt++]);
				if (cmd.hasOption("comp-logic-tree")) {
					File treeFile = new File(cmd.getOptionValue("comp-logic-tree"));
					System.out.println("Reading custom logic tree from: "+treeFile.getAbsolutePath());
					if (forceFileBacked)
						compTree = LogicTree.readFileBacked(treeFile);
					else
						compTree = LogicTree.read(treeFile);
					ignorePrecomputed = true;
				} else {
					// read it from the hazard file if available
					compTree = loadTreeFromResults(compHazardFile, forceFileBacked);
				}
				compName = args[cnt++];
			} else {
				compResultsFile = null;
				compHazardFile = null;
				compName = null;
			}
			outputDir = new File(args[cnt++]);
			
			subsetNodes = null;
			compSubsetNodes = null;
			currentWeights = false;
			compCurrentWeights = false;
		} else {
			resultsFile = new File(mainDir, "results.zip");
			hazardFile = new File(mainDir, "results_hazard.zip");
			if (compDir == null) {
				compResultsFile = null;
				compHazardFile = null;
			} else {
				compResultsFile = new File(compDir, "results.zip");
				compHazardFile = new File(compDir, "results_hazard.zip");
			}
		}
		
		SolutionLogicTree solTree;
		if (resultsFile == null) {
			solTree = null;
		} else {
			ArchiveInput resultsInput = ArchiveInput.getDefaultInput(resultsFile);
			if (FaultSystemSolution.isSolution(resultsInput)) {
				// single solution
				FaultSystemSolution sol = FaultSystemSolution.load(resultsInput);
				solTree = new SolutionLogicTree.InMemory(sol, null);
			} else {
				solTree = SolutionLogicTree.load(resultsInput);
			}
		}
		
		ReturnPeriods[] rps = SolHazardMapCalc.MAP_RPS;
		double[] periods;
		if (cmd.hasOption("periods")) {
			String perStr = cmd.getOptionValue("periods");
			if (perStr.contains(",")) {
				String[] split = perStr.split(",");
				periods = new double[split.length];
				for (int i=0; i<split.length; i++)
					periods[i] = Double.parseDouble(split[i]);
			} else {
				periods = new double[] {Double.parseDouble(perStr)};
			}
		} else {
			// detect periods in each zip file
			ArchiveInput hazardZip = ArchiveInput.getDefaultInput(hazardFile);
			
			if (compHazardFile == null) {
				periods = detectHazardPeriods(rps, hazardZip);
			} else {
				ArchiveInput compHazardZip = ArchiveInput.getDefaultInput(compHazardFile);
				periods = detectHazardPeriods(rps, hazardZip, compHazardZip);
				compHazardZip.close();
			}
		}
		double spacing = -1; // detect
//		double spacing = 0.1;
//		double spacing = 0.25;
		
		if (tree == null && solTree != null)
			tree = solTree.getLogicTree();
		
		if (subsetNodes != null && tree != null)
			tree = tree.matchingAll(subsetNodes);
		if (currentWeights && tree != null)
			tree.setWeightProvider(new BranchWeightProvider.CurrentWeights());
		
		LogicTreeHazardCompare mapper = null;
		LogicTreeHazardCompare comp = null;
		int exit = 0;
		try {
			mapper = new LogicTreeHazardCompare(solTree, tree,
					hazardFile, rps, periods, spacing);
			
			mapper.skipLogicTree = cmd.hasOption("skip-logic-tree") || tree == null;
			if (ignorePrecomputed)
				System.out.println("Ignoring any pre-computed mean maps");
			mapper.ignorePrecomputed = ignorePrecomputed;
			
			if (cmd.hasOption("cpt-range")) {
				String rangeStr = cmd.getOptionValue("cpt-range");
				Preconditions.checkArgument(rangeStr.contains(","));
				String[] split = rangeStr.split(",");
				double lower = Double.parseDouble(split[0]);
				double upper = Double.parseDouble(split[1]);
				System.out.println("CPT range: ["+(float)lower+", "+(float)upper+"]");
				mapper.setCPTRange(lower, upper);
			}
			
			if (cmd.hasOption("pdiff-range"))
				mapper.setPDiffRange(Double.parseDouble(cmd.getOptionValue("pdiff-range")));
			if (cmd.hasOption("diff-range"))
				mapper.setDiffRange(Double.parseDouble(cmd.getOptionValue("diff-range")));
			mapper.forceSparseLTVar = cmd.hasOption("force-sparse-lt-var");
			
			if (cmd.hasOption("plot-region")) {
				String plotRegStr = cmd.getOptionValue("plot-region");
				// see if it's a file
				File regFile = new File(plotRegStr);
				if (regFile.exists()) {
					Feature feature = Feature.read(regFile);
					mapper.mapRegion = Region.fromFeature(feature);
				} else {
					String[] commaSplit = plotRegStr.split(",");
					Preconditions.checkState(commaSplit.length == 4, "--plot-region must be either a GeoJSON file, or minLat,minLon,maxLat,maxLon");
					double minLat = Double.parseDouble(commaSplit[0]);
					double minLon = Double.parseDouble(commaSplit[1]);
					double maxLat = Double.parseDouble(commaSplit[2]);
					double maxLon = Double.parseDouble(commaSplit[3]);
					mapper.mapRegion = new Region(new Location(minLat, minLon), new Location(maxLat, maxLon));
				}
			}
			
			if (compHazardFile != null) {
				SolutionLogicTree compSolTree;
				if (compResultsFile == null) {
					// just have an average hazard result, possibly an external ERF
					compSolTree = null;
				} else {
					
					ZipFile compResultsZip = new ZipFile(compResultsFile);
					if (FaultSystemSolution.isSolution(compResultsZip)) {
						// single solution
						FaultSystemSolution sol = FaultSystemSolution.load(compResultsZip);
						compSolTree = new SolutionLogicTree.InMemory(sol, sol.requireModule(LogicTreeBranch.class));
					} else {
						compSolTree = SolutionLogicTree.load(compResultsZip);
					}
					
					if (compTree == null)
						compTree = compSolTree.getLogicTree();
					if (compSubsetNodes != null)
						compTree = compTree.matchingAll(compSubsetNodes);
					if (compCurrentWeights)
						compTree.setWeightProvider(new BranchWeightProvider.CurrentWeights());
				}
				comp = new LogicTreeHazardCompare(compSolTree, compTree,
						compHazardFile, rps, periods, spacing);
				mapper.ignorePrecomputed = ignorePrecomputed;
			}
			
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
			
			mapper.buildReport(outputDir, mainName, comp, compName);
		} catch (Exception e) {
			e.printStackTrace();
			exit = 1;
		} finally {
			if (mapper != null)
				mapper.close();
			if (comp != null)
				comp.close();
		}
		System.exit(exit);
	}
	
	public static double[] detectHazardPeriods(ReturnPeriods[] rps, ArchiveInput... archives) throws IOException {
		Preconditions.checkState(archives.length > 0);
		List<Double> periods = null;
		for (ArchiveInput archive : archives) {
			Set<String> uniqueMapNames = archive.entryStream()
					.map(s -> s.contains("/") ? s.substring(s.lastIndexOf('/') + 1) : s)
	                .filter(s -> s.startsWith("map_"))
	                .filter(s -> s.contains(rps[0].name()))
	                .collect(Collectors.toSet());
			System.out.println("Detecting periods for "+archive.getName()+" with "+uniqueMapNames.size()
					+" unique map names detected for "+rps[0].name());
			Preconditions.checkState(!uniqueMapNames.isEmpty(), "No map files found in %s", archive.getName());
			List<Double> myPeriods = new ArrayList<>();
			for (double period=0d; period<=10.00001; period+=0.01) {
				period = DataUtils.roundFixed(period, 3);
				String fileName = MPJ_LogicTreeHazardCalc.mapPrefix(period, rps[0])+".txt";
				if (uniqueMapNames.contains(fileName))
					myPeriods.add(period);
			}
			Preconditions.checkState(!myPeriods.isEmpty(), "No periods detected for %s", archive.getName());
			System.out.println("\tDetected periods: "+myPeriods);
			if (periods == null) {
				periods = myPeriods;
			} else {
				periods.retainAll(myPeriods);
				Preconditions.checkState(!periods.isEmpty(),
						"No common periods detected after adding %s from %s", myPeriods, archive.getName());
			}
		}
		return Doubles.toArray(periods);
	}
	
	public static Options createOptions() {
		Options ops = new Options();
		
		ops.addOption("slt", "skip-logic-tree", false,
				"Flag to disable logic tree calculations and only focus on top level maps and comparisions.");
		ops.addOption("lt", "logic-tree", true,
				"Path to alternative logic tree JSON file. Implies --ignore-precomputed-maps");
		ops.addOption("clt", "comp-logic-tree", true,
				"Path to alternative logic tree JSON file for the comparison model. Implies --ignore-precomputed-maps");
		ops.addOption("ipm", "ignore-precomputed-maps", false,
				"Flag to ignore precomputed mean maps");
		ops.addOption(null, "plot-region", true,
				"Custom plotting region. Must be either a path to a geojson file, or specified as minLat,minLon,maxLat,maxLon");
		ops.addOption("pdf", "write-pdfs", false, "Flag to write PDFs of top level maps");
		ops.addOption(null, "cpt-range", true, "Custom CPT range for hazard maps, in log10 units. Specify as min,max");
		ops.addOption(null, "pdiff-range", true, "Maximum % difference to plot");
		ops.addOption(null, "diff-range", true, "Maximum difference to plot");
		ops.addOption(null, "periods", true, "Custom spectral periods, comma separated");
		ops.addOption(null, "force-sparse-lt-var", false, "Flag to force using the sparse logic tree variance algorithm");
		ops.addOption(null, "force-file-backed-lt", false, "Flag to force loading the logic tree exactly as registered "
				+ "in the tree file and ignoring matching enums or classes");
		
		return ops;
	}
	
	private static LogicTree<?> loadTreeFromResults(File resultsFile, boolean forceFileBacked) throws IOException {
		ZipFile zip = new ZipFile(resultsFile);
		
		ZipEntry entry = zip.getEntry(AbstractLogicTreeModule.LOGIC_TREE_FILE_NAME);
		if (entry == null) {
			zip.close();
			return null;
		}
		
		BufferedInputStream logicTreeIS = new BufferedInputStream(zip.getInputStream(entry));
		InputStreamReader reader = new InputStreamReader(logicTreeIS);
		LogicTree<?> tree;
		if (forceFileBacked)
			tree = LogicTree.readFileBacked(reader);
		else
			tree = LogicTree.read(reader);
		zip.close();
		return tree;
	}
	
	private ReturnPeriods[] rps;
	private double[] periods;
	private boolean forceSparseLTVar = false;
	
	private ZipFile zip;
	private List<? extends LogicTreeBranch<?>> branches;
	private List<Double> weights;
	private double totWeight;
	private GriddedRegion gridReg;
	private double spacing;
	private CPT logCPT;
	private CPT spreadCPT;
	private CPT spreadDiffCPT;
	private CPT iqrCPT;
	private CPT iqrDiffCPT;
	private CPT sdCPT;
	private CPT sdDiffCPT;
	private CPT covCPT;
	private CPT covDiffCPT;
	private CPT diffCPT;
	private CPT pDiffCPT;
	private CPT percentileCPT;
	
	private SolHazardMapCalc mapper;
	private ExecutorService exec;
	private List<Future<?>> futures;
	
	private Region mapRegion;
	
	private GriddedRegion forceRemapRegion;
	
	private SolutionLogicTree solLogicTree;
	private LogicTree<?> tree;
	
	private boolean floatMaps = false;
	
	// command line options
	private boolean skipLogicTree = false;
	private boolean ignorePrecomputed = false;

	public LogicTreeHazardCompare(SolutionLogicTree solLogicTree, File mapsZipFile,
			ReturnPeriods[] rps, double[] periods, double spacing) throws IOException {
		this(solLogicTree, solLogicTree.getLogicTree(), mapsZipFile, rps, periods, spacing);
	}

	public LogicTreeHazardCompare(SolutionLogicTree solLogicTree, LogicTree<?> tree, File mapsZipFile,
			ReturnPeriods[] rps, double[] periods, double spacing) throws IOException {
		this.solLogicTree = solLogicTree;
		this.tree = tree;
		this.rps = rps;
		this.periods = periods;
		this.spacing = spacing;

		logCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-3d, 1d);
		logCPT.setLog10(true);
		spreadCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0, 1d);
		spreadCPT.setNanColor(Color.LIGHT_GRAY);
		spreadDiffCPT = GMT_CPT_Files.DIVERGING_BAM_UNIFORM.instance().reverse().rescale(-1d, 1d);
		spreadDiffCPT.setNanColor(Color.LIGHT_GRAY);
		iqrCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-2, 0d);
		iqrCPT.setLog10(true);
		iqrCPT.setNanColor(Color.LIGHT_GRAY);
		iqrDiffCPT = GMT_CPT_Files.DIVERGING_BAM_UNIFORM.instance().reverse().rescale(-0.05d, 0.05d);
		iqrDiffCPT.setNanColor(Color.LIGHT_GRAY);
		covCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0, 1d);
		covCPT.setNanColor(Color.LIGHT_GRAY);
		covDiffCPT = GMT_CPT_Files.DIVERGING_BAM_UNIFORM.instance().reverse().rescale(-0.3d, 0.3d);
		covDiffCPT.setNanColor(Color.LIGHT_GRAY);
		sdCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0, 0.2d);
		sdCPT.setNanColor(Color.LIGHT_GRAY);
		sdDiffCPT = GMT_CPT_Files.DIVERGING_BAM_UNIFORM.instance().reverse().rescale(-0.05d, 0.05d);
		sdDiffCPT.setNanColor(Color.LIGHT_GRAY);
//		pDiffCPT = GMT_CPT_Files.GMT_POLAR.instance().rescale(-100d, 100d);
//		pDiffCPT = GMT_CPT_Files.GMT_POLAR.instance().rescale(-50d, 50d);
		pDiffCPT = GMT_CPT_Files.DIVERGING_VIK_UNIFORM.instance().rescale(-50d, 50d);
//		pDiffCPT = GMT_CPT_Files.DIVERGING_DARK_BLUE_RED_UNIFORM.instance().rescale(-50d, 50d);
		pDiffCPT.setNanColor(Color.LIGHT_GRAY);
		diffCPT = GMT_CPT_Files.DIVERGING_BAM_UNIFORM.instance().reverse().rescale(-0.2, 0.2d);
		diffCPT.setNanColor(Color.LIGHT_GRAY);
		percentileCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, 100d);
		percentileCPT.setNanColor(Color.BLACK);
		percentileCPT.setBelowMinColor(Color.LIGHT_GRAY);
		
		zip = new ZipFile(mapsZipFile);
		
		// see if the zip file has a region attached
		ZipEntry regEntry = zip.getEntry(MPJ_LogicTreeHazardCalc.GRID_REGION_ENTRY_NAME);
		if (regEntry != null) {
			System.out.println("Reading gridded region from zip file: "+regEntry.getName());
			BufferedReader bRead = new BufferedReader(new InputStreamReader(zip.getInputStream(regEntry)));
			gridReg = GriddedRegion.fromFeature(Feature.read(bRead));
			spacing = gridReg.getSpacing();
		}
		
		if (tree == null) {
			// assume external
			branches = new ArrayList<>();
			weights = new ArrayList<>();
			
			branches.add(null);
			weights.add(1d);
			totWeight = 1d;
			
			Preconditions.checkNotNull(gridReg, "Must supply gridded region in zip file if external");
		} else {
			branches = tree.getBranches();
			weights = new ArrayList<>();
			
			BranchWeightProvider weightProv = tree.getWeightProvider();
			
			for (int i=0; i<branches.size(); i++) {
				LogicTreeBranch<?> branch = branches.get(i);
				double weight = weightProv.getWeight(branch);
				Preconditions.checkState(weight >= 0, "Bad weight=%s for branch %s, weightProv=%s",
						weight, branch, weightProv.getClass().getName());
				if (weight == 0d)
					System.err.println("WARNING: zero weight for branch: "+branch);
				weights.add(weight);
				totWeight += weight;
			}
		}
		
		int threads = Integer.max(2, Integer.min(16, FaultSysTools.defaultNumThreads()));
		// this will block to make sure the queue is never too large
		exec = ExecutorUtils.newBlockingThreadPool(threads, Integer.max(threads*4, threads+10));
		
		System.out.println(branches.size()+" branches, total weight: "+totWeight);
	}
	
	public void setCPTRange(double lower, double upper) {
		try {
			logCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(lower, upper);
			logCPT.setLog10(true);
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	public void setPDiffRange(double maxPDiff) {
		pDiffCPT = pDiffCPT.rescale(-maxPDiff, maxPDiff);
	}
	
	public void setDiffRange(double maxDiff) {
		diffCPT = diffCPT.rescale(-maxDiff, maxDiff);
	}
	
	public SolHazardMapCalc getMapper() {
		return mapper;
	}
	
	public void setGriddedRegion(GriddedRegion gridReg) {
		this.gridReg = gridReg;
	}
	
	public synchronized GriddedGeoDataSet[] loadMaps(ReturnPeriods rp, double period) throws IOException {
		System.out.println("Loading maps for rp="+rp+", period="+period);
		GriddedGeoDataSet[] rpPerMaps = new GriddedGeoDataSet[branches.size()];
		int printMod = 10;
		LinkedList<Future<?>> processFutures = new LinkedList<>();
		CompletableFuture<Runnable> readFuture = null;
		
		LogicTreeBranch<?> branch0 = branches.get(0);
		if (branch0 != null) {
			FaultSystemSolution sol = null;
			if (mapper == null && solLogicTree != null)
				sol = solLogicTree.forBranch(branch0, false);
			
			if (gridReg == null) {
				if (spacing <= 0d) {
					// detect spacing

					String dirName = branch0.getBranchZipPath();
					String name = dirName+"/"+MPJ_LogicTreeHazardCalc.mapPrefix(periods[0], rps[0])+".txt";
					ZipEntry entry = zip.getEntry(name);
					Preconditions.checkNotNull(entry, "Entry is null for %s", name);
					InputStream is = zip.getInputStream(entry);
					Preconditions.checkNotNull(is, "IS is null for %s", name);
					
					BufferedReader bRead = new BufferedReader(new InputStreamReader(is));
					String line = bRead.readLine();
					double prevLat = Double.NaN;
					double prevLon = Double.NaN;
					List<Double> deltas = new ArrayList<>();
					while (line != null) {
						line = line.trim();
						if (!line.startsWith("#")) {
							StringTokenizer tok = new StringTokenizer(line);
							double lon = Double.parseDouble(tok.nextToken());
							double lat = Double.parseDouble(tok.nextToken());
							if (Double.isFinite(prevLat)) {
								deltas.add(Math.abs(lat - prevLat));
								deltas.add(Math.abs(lon - prevLon));
							}
							prevLat = lat;
							prevLon = lon;
						}
						line = bRead.readLine();
					}
					double medianSpacing = DataUtils.median(Doubles.toArray(deltas));
					medianSpacing = (double)Math.round(medianSpacing * 1000d) / 1000d;
					System.out.println("Detected spacing: "+medianSpacing+" degrees");
					spacing = medianSpacing;
				}
				
				Preconditions.checkNotNull(solLogicTree, "Can't determine region; neither a region nor a solution was found.");
				sol = solLogicTree.forBranch(branch0);
				Region region = ReportMetadata.detectRegion(sol);
				if (region == null)
					// just use bounding box
					region = RupSetMapMaker.buildBufferedRegion(sol.getRupSet().getFaultSectionDataList());
				gridReg = new GriddedRegion(region, spacing, GriddedRegion.ANCHOR_0_0);
			}
			
			floatMaps = branches.size() > 10000 && gridReg.getNodeCount() > 10000;
			if (floatMaps)
				System.out.println("Loading maps at 4-byte floating precision for "+branches.size()
					+" branches and "+gridReg.getNodeCount()+" grid locs");
			
			if (mapper == null) {
				mapper = new SolHazardMapCalc(sol, null, gridReg, periods);
				if (mapRegion != null)
					mapper.setMapPlotRegion(mapRegion);
			}
		}
		
		if (mapper == null) {
			// if we're here, the primary is an external (branch is null)
			
			if (solLogicTree != null && solLogicTree instanceof SolutionLogicTree.InMemory && tree == null) {
				// single in memory, keyed with null branch, fetch the solution
				FaultSystemSolution sol = solLogicTree.forBranch(null);
				mapper = new SolHazardMapCalc(sol, null, gridReg, periods);
			} else {
				// build mapper without a solution (no faults will be shown)
				mapper = new SolHazardMapCalc(null, null, gridReg, periods);
			}
			if (mapRegion != null)
				mapper.setMapPlotRegion(mapRegion);
		}
		
		Stopwatch watch = Stopwatch.createStarted();
		
		for (int i=0; i<branches.size(); i++) {
			if (i % printMod == 0) {
				try {
					while (!processFutures.isEmpty())
						processFutures.pop().get();
				} catch (InterruptedException | ExecutionException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				String str = "Loading map for branch "+i+"/"+branches.size();
				if (i > 0) {
					double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
					double rate = (double)i/secs;
					
					str += " (rate="+twoDigits.format((double)i/secs)+" /s; ";
					int branchesLeft = (branches.size() - i);
					double secsLeft = (double)branchesLeft/rate;
					if (secsLeft < 120d) {
						str += twoDigits.format(secsLeft)+" s";
					} else {
						double minsLeft = secsLeft/60d;
						str += twoDigits.format(minsLeft)+" m";
					}
					str += " left)";
				}
				
				System.out.println(str);
			}
			if (i >= printMod*10 && printMod < 1000)
				printMod *= 10;
			LogicTreeBranch<?> branch = branches.get(i);
			if (branch == null) {
				// external
				Preconditions.checkState(branches.size() == 1);
				Preconditions.checkNotNull(gridReg, "Must supply gridded region in zip file if external");
				
				ZipEntry entry = zip.getEntry(MPJ_LogicTreeHazardCalc.mapPrefix(period, rp)+".txt");
				
				GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridReg, false);
				BufferedReader bRead = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)));
				String line = bRead.readLine();
				int index = 0;
				while (line != null) {
					line = line.trim();
					if (!line.startsWith("#")) {
						StringTokenizer tok = new StringTokenizer(line);
						double lon = Double.parseDouble(tok.nextToken());
						double lat = Double.parseDouble(tok.nextToken());
						double val = Double.parseDouble(tok.nextToken());
						Location loc = new Location(lat, lon);
						Preconditions.checkState(LocationUtils.areSimilar(loc, gridReg.getLocation(index)));
						xyz.set(index++, val);
					}
					line = bRead.readLine();
				}
				Preconditions.checkState(index == gridReg.getNodeCount());
				rpPerMaps[0] = checkRemap(xyz);
			} else {
//				System.out.println("Processing maps for "+branch);
				
				String dirName = branch.getBranchZipPath();
				
				if (readFuture != null)
					// finish reading prior one
					processFutures.push(exec.submit(readFuture.join()));
				
				final int mapIndex = i;
				readFuture = CompletableFuture.supplyAsync(new Supplier<Runnable>() {

					@Override
					public Runnable get() {
						String entryName = dirName+"/"+MPJ_LogicTreeHazardCalc.mapPrefix(period, rp)+".txt";
						try {
							ZipEntry entry = zip.getEntry(entryName);
							
							BufferedReader bRead = prereadMapEntry(entry);
							return new Runnable() {

								@Override
								public void run() {
									try {
										rpPerMaps[mapIndex] = readMapReader(bRead);
									} catch (IOException e) {
										System.err.println("Exception loading: "+entryName);
										System.err.flush();
										throw ExceptionUtils.asRuntimeException(e);
									}
								}
								
							};
						} catch (Exception e) {
							System.err.println("Exception loading: "+entryName);
							System.err.flush();
							throw ExceptionUtils.asRuntimeException(e);
						}
					}
				});
//				processFutures.push(exec.submit(new Runnable() {
//
//					@Override
//					public void run() {
//						String entryName = dirName+"/"+MPJ_LogicTreeHazardCalc.mapPrefix(period, rp)+".txt";
//						try {
//							ZipEntry entry = zip.getEntry(entryName);
//
//							rpPerMaps[mapIndex] = readMapEntry(entry);
//						} catch (Exception e) {
//							System.err.println("Exception loading: "+entryName);
//							System.err.flush();
//							throw ExceptionUtils.asRuntimeException(e);
//						}
//					}
//					
//				}));
			}
		}
		
		if (readFuture != null)
			processFutures.push(exec.submit(readFuture.join()));
		
		try {
			while (!processFutures.isEmpty())
				processFutures.pop().get();
		} catch (InterruptedException | ExecutionException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		watch.stop();
		printTime(watch, "load "+branches.size()+" maps", 0d);
		
		return rpPerMaps;
	}
	
	private BufferedReader prereadMapEntry(ZipEntry entry) throws IOException {
		InputStream is = zip.getInputStream(entry);
//		int size = 10240;
		int size = Integer.max(1024, Integer.min(102400, (int)(entry.getSize()/12l)));
		byte[] buffer = new byte[size];
		ByteArrayOutputStream os = new ByteArrayOutputStream(size);
		for (int length; (length = is.read(buffer)) != -1; )
			os.write(buffer, 0, length);
//		return new BufferedReader(new StringReader(os.toString(StandardCharsets.US_ASCII)));
		return new BufferedReader(new StringReader(os.toString(Charset.defaultCharset())));
	}
	
	private GriddedGeoDataSet readMapEntry(ZipEntry entry) throws IOException {
		BufferedReader bRead = new BufferedReader(new InputStreamReader(zip.getInputStream(entry)));
		return readMapReader(bRead);
	}
	
	private GriddedGeoDataSet readMapReader(BufferedReader bRead) throws IOException {
		GriddedGeoDataSet xyz = floatMaps ? new GriddedGeoDataSet.FloatData(gridReg, false) : new GriddedGeoDataSet(gridReg, false);
		String line = bRead.readLine();
		int index = 0;
		while (line != null) {
			line = line.trim();
			if (!line.startsWith("#")) {
				StringTokenizer tok = new StringTokenizer(line);
				double lon = Double.parseDouble(tok.nextToken());
				double lat = Double.parseDouble(tok.nextToken());
				double val = Double.parseDouble(tok.nextToken());
				Location loc = new Location(lat, lon);
				Preconditions.checkState(LocationUtils.areSimilar(loc, gridReg.getLocation(index)));
				xyz.set(index++, val);
			}
			line = bRead.readLine();
		}
		Preconditions.checkState(index == gridReg.getNodeCount());
		return checkRemap(xyz);
	}
	
	GriddedGeoDataSet loadPrecomputedMeanMap(String key, ReturnPeriods rp, double period) throws IOException {
		String entryName = key+"_"+MPJ_LogicTreeHazardCalc.mapPrefix(period, rp)+".txt";
		ZipEntry entry = zip.getEntry(entryName);
		if (entry == null)
			return null;
		
		return readMapEntry(entry);
	}
	
	public void setRemapToRegion(GriddedRegion gridReg) {
		this.forceRemapRegion = gridReg;
	}
	
	private GriddedGeoDataSet checkRemap(GriddedGeoDataSet xyz) {
		if (forceRemapRegion != null) {
			int origCount = gridReg.getNodeCount();
			int newCount = forceRemapRegion.getNodeCount();
			int testIndex = origCount / 3;
			if (origCount != newCount || !LocationUtils.areSimilar(
					gridReg.getLocation(testIndex), forceRemapRegion.getLocation(testIndex))) {
				// need to remap it
				GriddedGeoDataSet remapped = new GriddedGeoDataSet(forceRemapRegion, false);
				for (int i=0; i<newCount; i++) {
					int origIndex = gridReg.indexForLocation(remapped.getLocation(i));
					if (origIndex >= 0)
						remapped.set(i, xyz.get(origIndex));
					else
						remapped.set(i, 0d);
				}
				xyz = remapped;
			}
		}
		return xyz;
	}
	
	public GriddedGeoDataSet buildMean(GriddedGeoDataSet[] maps) {
		GriddedGeoDataSet avg = new GriddedGeoDataSet(maps[0].getRegion(), false);
		
		for (int i=0; i<avg.size(); i++) {
			double val = 0d;
			for (int j=0; j<maps.length; j++)
				val += maps[j].get(i)*weights.get(j);
			val /= totWeight;
			avg.set(i, val);
		}
		
		return avg;
	}
	
	public static GriddedGeoDataSet buildMean(List<GriddedGeoDataSet> maps, List<Double> weights) {
		GriddedGeoDataSet avg = new GriddedGeoDataSet(maps.get(0).getRegion(), false);
		
		double totWeight = 0d;
		for (Double weight : weights)
			totWeight += weight;
		
		for (int i=0; i<avg.size(); i++) {
			double val = 0d;
			for (int j=0; j<maps.size(); j++)
				val += maps.get(j).get(i)*weights.get(j);
			val /= totWeight;
			avg.set(i, val);
		}
		
		return avg;
	}
	
	GriddedGeoDataSet buildMin(GriddedGeoDataSet[] maps, List<Double> weights) {
		Preconditions.checkState(maps.length == weights.size());
		GriddedGeoDataSet min = new GriddedGeoDataSet(maps[0].getRegion(), false);
		
		for (int i=0; i<min.size(); i++) {
			double val = Double.POSITIVE_INFINITY;
			for (int j=0; j<maps.length; j++)
				if (weights.get(j) > 0d)
					val = Math.min(val, maps[j].get(i));
			min.set(i, val);
		}
		
		return min;
	}
	
	GriddedGeoDataSet buildMax(GriddedGeoDataSet[] maps, List<Double> weights) {
		Preconditions.checkState(maps.length == weights.size());
		GriddedGeoDataSet max = new GriddedGeoDataSet(maps[0].getRegion(), false);
		
		for (int i=0; i<max.size(); i++) {
			double val = Double.NEGATIVE_INFINITY;
			for (int j=0; j<maps.length; j++)
				if (weights.get(j) > 0d)
					val = Math.max(val, maps[j].get(i));
			max.set(i, val);
		}
		
		return max;
	}
	
	private GriddedGeoDataSet buildPDiffFromRange(GriddedGeoDataSet min, GriddedGeoDataSet max, GriddedGeoDataSet comp) {
		GriddedGeoDataSet diff = new GriddedGeoDataSet(min.getRegion(), false);
		
		for (int i=0; i<max.size(); i++) {
			double compVal = comp.get(i);
			double minVal = min.get(i);
			double maxVal = max.get(i);
			double pDiff;
			if (compVal >= minVal && compVal <= maxVal)
				pDiff = 0d;
			else if (compVal < minVal)
				pDiff = 100d*(compVal-minVal)/minVal;
			else
				pDiff = 100d*(compVal-maxVal)/maxVal;
			diff.set(i, pDiff);
		}
		
		return diff;
	}
	
	private GriddedGeoDataSet buildSpread(GriddedGeoDataSet min, GriddedGeoDataSet max) {
		GriddedGeoDataSet diff = new GriddedGeoDataSet(min.getRegion(), false);
		
		for (int i=0; i<max.size(); i++) {
			double minVal = min.get(i);
			double maxVal = max.get(i);
			diff.set(i, maxVal - minVal);
		}
		
		return diff;
	}
	
	private GriddedGeoDataSet buildPDiff(GriddedGeoDataSet ref, GriddedGeoDataSet comp) {
		GriddedGeoDataSet diff = new GriddedGeoDataSet(ref.getRegion(), false);
		
		for (int i=0; i<ref.size(); i++) {
			double compVal = comp.get(i);
			double refVal = ref.get(i);
			double pDiff = 100d*(compVal-refVal)/refVal;
			diff.set(i, pDiff);
		}
		
		return diff;
	}
	
	LightFixedXFunc[] buildNormCDFs(GriddedGeoDataSet[] maps, List<Double> weights) {
		return buildNormCDFs(List.of(maps), weights);
	}
	
	private static class ValWeights implements Comparable<ValWeights> {
		double val;
		double weight;
		public ValWeights(double val, double weight) {
			super();
			this.val = val;
			this.weight = weight;
		}
		@Override
		public int compareTo(ValWeights o) {
			return Double.compare(val, o.val);
		}
	}
	
	private LightFixedXFunc[] buildNormCDFs(List<GriddedGeoDataSet> maps, List<Double> weights) {
		Preconditions.checkState(maps.size() == weights.size());
		LightFixedXFunc[] ret = new LightFixedXFunc[maps.get(0).size()];
		
		double totWeight = 0d;
		for (double weight : weights)
			totWeight += weight;
		
		Stopwatch watch = Stopwatch.createStarted();
		if (maps.size() < 500 || ret.length < 100) {
			// just do it serially
			for (int i=0; i<ret.length; i++)
				ret[i] = calcNormCDF(maps, weights, i, totWeight);
		} else {
			// do it in parallel
			List<Future<?>> futures = new ArrayList<>(ret.length);
			final double finalTotWeight = totWeight;
			for (int i=0; i<ret.length; i++) {
				int gridIndex = i;
				futures.add(exec.submit(new Runnable() {
					
					@Override
					public void run() {
						ret[gridIndex] = calcNormCDF(maps, weights, gridIndex, finalTotWeight);
					}
				}));
			}
			try {
				for (Future<?> future : futures)
					future.get();
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		watch.stop();
		printTime(watch, "build nrom CDFs for "+maps.size()+" maps", 10d);
		
		for (int i=0; i<ret.length; i++) {
			LightFixedXFunc ncdf = ret[i];
			if (ncdf.size() > 1) {
				double first = ncdf.getY(0);
				double last = ncdf.getY(ncdf.size()-1);
				Preconditions.checkState((float)last == 1f, "last value in ncdf not 1: %s", last);
				Preconditions.checkState(first < last, "first value in ncdf is not less than last: %s %s", first, last);
			}
		}
		
		return ret;
	}
	
	private static void printTime(Stopwatch watch, String operation, double minSecsToPrint) {
		double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		if (secs < minSecsToPrint)
			return;
		if (secs > 90d)
			System.out.println("Took "+twoDigits.format(secs/60d)+" mins to "+operation);
		else
			System.out.println("Took "+twoDigits.format(secs)+" secs to "+operation);
	}
	
	public static LightFixedXFunc calcNormCDF(List<GriddedGeoDataSet> maps, List<Double> weights,
			int gridIndex, double totWeight) {
		Preconditions.checkState(totWeight > 0d, "Bad total weight=%s", totWeight);
		// could use ArbDiscrEmpiricalDistFunc, but this version is 25-50% faster
		ValWeights[] valWeights = new ValWeights[maps.size()];
		for (int j=0; j<valWeights.length; j++)
			valWeights[j] = new ValWeights(maps.get(j).get(gridIndex), weights.get(j));
		// sort ascending
		Arrays.sort(valWeights);
		int destIndex = -1;
		double[] xVals = new double[valWeights.length];
		double[] yVals = new double[valWeights.length];
		for (int srcIndex=0; srcIndex<valWeights.length; srcIndex++) {
			ValWeights val = valWeights[srcIndex];
			if (destIndex >= 0 && (float)val.val == (float)xVals[destIndex]) {
				// add it, don't increment
				yVals[destIndex] += val.weight;
			} else {
				// move to a new index
				destIndex++;
				xVals[destIndex] = val.val;
				yVals[destIndex] = val.weight;
			}
		}
		int size = destIndex+1;
		if (size < xVals.length) {
			// we have duplicates, trim them
			xVals = Arrays.copyOf(xVals, size);
			yVals = Arrays.copyOf(yVals, size);
		}

		// now convert yVals to a CDF
		double sum = 0d;
		for (int j=0; j<yVals.length; j++) {
			sum += yVals[j];
			yVals[j] = sum/totWeight;
			if (j > 0)
				Preconditions.checkState(xVals[j] > xVals[j-1],
						"Normm CDF not monotomoically increasing. x[%s]=%s, x[%s]=%s",
						j-1, xVals[j-1], j, xVals[j]);
		}
		
		LightFixedXFunc ret = new LightFixedXFunc(xVals, yVals);
		
		if (size > MAX_NORMCDF_POINTS*1.2d) {
			// interpolate it to a more manageable size
			double[] remappedX = new double[MAX_NORMCDF_POINTS];
			if (xVals[0] == 0d) {
				remappedX[0] = 0d;
				Preconditions.checkState(xVals[1] > 0);
				EvenlyDiscretizedFunc logVals = new EvenlyDiscretizedFunc(
						Math.log(xVals[1]), Math.log(xVals[xVals.length-1]), MAX_NORMCDF_POINTS-1);
				for (int i=0; i<logVals.size(); i++) {
					double xVal;
					if (i == 0)
						xVal = xVals[1];
					else if (i == xVals.length-1)
						xVal = xVals[xVals.length-1];
					else
						xVal = Math.exp(logVals.getX(i));
					remappedX[i+1] = xVal;
				}
			} else {
				EvenlyDiscretizedFunc logVals = new EvenlyDiscretizedFunc(
						Math.log(xVals[0]), Math.log(xVals[xVals.length-1]), MAX_NORMCDF_POINTS);
				for (int i=0; i<logVals.size(); i++) {
					double xVal;
					if (i == 0)
						xVal = xVals[0];
					else if (i == xVals.length-1)
						xVal = xVals[xVals.length-1];
					else
						xVal = Math.exp(logVals.getX(i));
					remappedX[i] = xVal;
				}
			}
			double[] remappedY = new double[MAX_NORMCDF_POINTS];
			for (int i=0; i<MAX_NORMCDF_POINTS; i++) {
				if (i == 0)
					remappedY[i] = yVals[0];
				else if (i == MAX_NORMCDF_POINTS-1)
					remappedY[i] = yVals[yVals.length-1];
				else
					remappedY[i] = ret.getInterpolatedY(remappedX[i]);
			}
			ret = new LightFixedXFunc(remappedX, remappedY);
		}

		return ret;
	}
	
	private static final int MAX_NORMCDF_POINTS = 10000;
	
//	private static LightFixedXFunc[] mergeNormCDFs(List<LightFixedXFunc[]> inputCDFs) {
//		LightFixedXFunc[][] array = new LightFixedXFunc[inputCDFs.size()][];
//		for (int i=0; i<array.length; i++)
//			array[i] = inputCDFs.get(i);
//		return mergeNormCDFs(array);
//	}
//	
//	private static LightFixedXFunc[] mergeNormCDFs(LightFixedXFunc[]... inputCDFs) {
//		if (inputCDFs.length > 0)
//			throw new IllegalStateException("The code below needs to be modified to handle unequal weighting");
//		/*
//		 * THIS CODE IS WRONG, DOESN'T HANDLE UNEQUAL WEIGHTING
//		 */
//		final int numCDFs = inputCDFs.length;
//		Preconditions.checkState(numCDFs > 1);
//		int numLocs = inputCDFs[0].length;
//		for (int i=1; i<inputCDFs.length; i++)
//			Preconditions.checkState(inputCDFs[i].length == numLocs);
//		
//		LightFixedXFunc[] ret = new LightFixedXFunc[numLocs];
//		
//		for (int l=0; l<ret.length; l++) {
//			int size = 0;
//			for (int i=0; i<numCDFs; i++)
//				size += inputCDFs[i][l].size();
//			
//			double[] xVals = new double[size];
//			double[] yVals = new double[size];
//			
//			double sum = 0d;
//			int[] curIndexes = new int[numCDFs];
//			
//			int destIndex = -1;
//			for (int i=0; i<size; i++) {
//				// find the CDF with the lowest next value
//				int minIndex = -1;
//				double minVal = Double.NaN;
//				for (int j=0; j<numCDFs; j++) {
//					if (curIndexes[j] == inputCDFs[j][l].size())
//						// already fully used
//						continue;
//					double val = inputCDFs[j][l].getX(curIndexes[j]);
//					if (minIndex < 0 || val < minVal) {
//						minIndex = j;
//						minVal = val;
//					}
//				}
//				Preconditions.checkState(minIndex >= 0);
//				
//				sum += inputCDFs[minIndex][l].getY(curIndexes[minIndex]);
//				// use that value
//				if (destIndex < 0 || (float)minVal != (float)xVals[destIndex]) {
//					// new x value
//					destIndex++;
//					xVals[destIndex] = minVal;
//				}
//				yVals[destIndex] = sum;
//				
//				// increment the index for the one we drew from
//				curIndexes[minIndex]++;
//			}
//			
//			// rescale to sum to 1
//			double scale = 1d/sum;
//			for (int i=0; i<size; i++)
//				yVals[i] *= scale;
//			
//			// see if we need to trip
//			int used = destIndex+1;
//			if (used < size) {
//				xVals = Arrays.copyOf(xVals, used);
//				yVals = Arrays.copyOf(yVals, used);
//			}
//			
//			ret[l] = new LightFixedXFunc(xVals, yVals);
//		}
//		
//		return ret;
//	}
	
	GriddedGeoDataSet calcMapAtPercentile(LightFixedXFunc[] ncdfs, GriddedRegion gridReg,
			double percentile) {
		Preconditions.checkState(gridReg.getNodeCount() == ncdfs.length, "grid reg has %s, but have %s ncdfs",
				gridReg.getNodeCount(), ncdfs.length);
		GriddedGeoDataSet ret = new GriddedGeoDataSet(gridReg, false);
		
		double fractile = percentile/100d;
		Preconditions.checkState(fractile >= 0d && fractile <= 1d);

		Stopwatch watch = Stopwatch.createStarted();
		for (int i=0; i<ret.size(); i++) {
			double val = ArbDiscrEmpiricalDistFunc.calcFractileFromNormCDF(ncdfs[i], fractile);
			ret.set(i, val);
		}
		watch.stop();
		printTime(watch, "calc map at p"+(float)percentile+" for "+ncdfs.length+" normCDFs", 10d);
		
		return ret;
	}
	
	private GriddedGeoDataSet calcIQR(LightFixedXFunc[] ncdfs, GriddedRegion gridReg) {
		GriddedGeoDataSet p75 = calcMapAtPercentile(ncdfs, gridReg, 75d);
		GriddedGeoDataSet p25 = calcMapAtPercentile(ncdfs, gridReg, 25d);
		GriddedGeoDataSet ret = new GriddedGeoDataSet(gridReg, false);
		for (int i=0; i<ret.size(); i++)
			ret.set(i, p75.get(i) - p25.get(i));
		return ret;
	}
	
	public static GriddedGeoDataSet calcPercentileWithinDist(LightFixedXFunc[] ncdfs, GriddedGeoDataSet comp) {
		Preconditions.checkState(comp.size() == ncdfs.length);
		GriddedGeoDataSet ret = new GriddedGeoDataSet(comp.getRegion(), false);
		
		for (int i=0; i<ret.size(); i++) {
			double compVal = comp.get(i);
			
			double percentile;
			if (ncdfs[i].size() == 1) {
				if (compVal > 0d && (float)ncdfs[i].getX(0) == (float)compVal)
					percentile = 50d;
				else
					percentile = -1d;
			} else if (compVal < ncdfs[i].getMinX() || compVal > ncdfs[i].getMaxX()) {
				if ((float)compVal == (float)ncdfs[i].getMinX())
					percentile = 0d;
				else if ((float)compVal == (float)ncdfs[i].getMaxX())
					percentile = 100d;
				else
					percentile = Double.NaN;
			} else {
				percentile = 100d * ncdfs[i].getInterpolatedY(compVal);
			}
			ret.set(i, percentile);
		}
		
		return ret;
	}
	
	private static GriddedGeoDataSet calcPercentileWithinDists(List<LightFixedXFunc[]> ncdfsList, List<Double> weightsList,
			GriddedGeoDataSet comp) {
		Preconditions.checkState(weightsList.size() == ncdfsList.size());
		if (ncdfsList.size() == 1)
			return calcPercentileWithinDist(ncdfsList.get(0), comp);
		for (LightFixedXFunc[] ncdfs : ncdfsList)
			Preconditions.checkState(comp.size() == ncdfs.length);
		GriddedGeoDataSet ret = new GriddedGeoDataSet(comp.getRegion(), false);
		
		double sumWeights = 0d;
		for (double weight : weightsList)
			sumWeights += weight;
		double weightScale = 1d/sumWeights;
		
		for (int i=0; i<ret.size(); i++) {
			double compVal = comp.get(i);
			float compValFloat = (float)compVal;
			
			double minVal = Double.POSITIVE_INFINITY;
			double maxVal = Double.NEGATIVE_INFINITY;
			for (LightFixedXFunc[] ncdfs : ncdfsList) {
				minVal = Double.min(minVal, ncdfs[i].getMinX());
				maxVal = Double.max(maxVal, ncdfs[i].getMaxX());
			}
			
			double percentile;
			if ((float)minVal == (float)maxVal) {
				// only one value
				if (compVal > 0d && compValFloat== (float)minVal)
					percentile = 50d;
				else
					percentile = -1;
			} else if (compVal < minVal || compVal > maxVal) {
				// we're outside of the full dist
				if (compValFloat == (float)minVal)
					percentile = 0d;
				else if (compValFloat== (float)maxVal)
					percentile = 100d;
				else
					percentile = Double.NaN;
			} else {
				// we're contained within the dist
				double sumY = 0d;
				for (int n=0; n<ncdfsList.size(); n++) {
					double weight = weightsList.get(n);
					LightFixedXFunc[] ncdfs = ncdfsList.get(n);
					float myMin = (float)ncdfs[i].getMinX();
					float myMax = (float)ncdfs[i].getMaxX();
					if (compValFloat == (float)myMin && compValFloat == (float)myMax)
						// only one value here, and we're at it, 50th percentile
						sumY += 0.5*weight;
					else if (compValFloat >= myMax)
						// we're at or above the max of this whole one
						sumY += weight;
					else if (compValFloat <= myMin)
						// we're at or below the min of this whole one, do nothing
						sumY += 0d;
					else
						// we're contined within the distribution
						sumY += ncdfs[i].getInterpolatedY(compVal)*weight;
				}
				percentile = 100d * sumY * weightScale;
			}
			ret.set(i, percentile);
		}
		
		return ret;
	}
	
	private void calcSD_COV(GriddedGeoDataSet[] maps, List<Double> weights,
			GriddedGeoDataSet meanMap, GriddedGeoDataSet sd, GriddedGeoDataSet cov) {
		calcSD_COV(maps, weights, meanMap, sd, cov, exec);
	}
	
	static void calcSD_COV(GriddedGeoDataSet[] maps, List<Double> weights,
			GriddedGeoDataSet meanMap, GriddedGeoDataSet sd, GriddedGeoDataSet cov, ExecutorService exec) {
		double[] weightsArray = MathArrays.normalizeArray(Doubles.toArray(weights), weights.size());

		Stopwatch watch = Stopwatch.createStarted();
		if (maps.length < 500 || sd.size() < 100) {
			// serial
			for (int i=0; i<sd.size(); i++) {
				double[] vals = calcSD_COV(maps, weightsArray, meanMap, i);
				sd.set(i, vals[0]);
				cov.set(i, vals[1]);
			}
		} else {
			// parallel
			List<Future<?>> futures = new ArrayList<>(sd.size());
			for (int i=0; i<sd.size(); i++) {
				int gridIndex = i;
				futures.add(exec.submit(new Runnable() {
					
					@Override
					public void run() {
						double[] vals = calcSD_COV(maps, weightsArray, meanMap, gridIndex);
						sd.set(gridIndex, vals[0]);
						cov.set(gridIndex, vals[1]);
					}
				}));
			}
			try {
				for (Future<?> future : futures)
					future.get();
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		watch.stop();
		printTime(watch, "calc SDs/COVs for "+maps.length+" maps", 10d);
	}
	
	private static double[] calcSD_COV(GriddedGeoDataSet[] maps, double[] weights, GriddedGeoDataSet meanMap, int gridIndex) {
		double mean = meanMap.get(gridIndex);
		double sd, cov;
		if (maps.length == 1) {
			sd = 0d;
			cov = 0d;
		} else if (mean == 0d) {
			sd = 0d;
			cov = Double.NaN;
		} else {
			// false here means to use the population formula, which does better for small N and is appropriate because
			// our logic trees are the full population
			Variance var = new Variance(false);
			double[] cellVals = new double[maps.length];
			for (int j=0; j<cellVals.length; j++)
				cellVals[j] = maps[j].get(gridIndex);
			sd = Math.sqrt(var.evaluate(cellVals, weights));
			cov = sd/mean;
//			System.out.println("COV = "+(float)stdDev+" / "+(float)mean+" = "+(float)val);
		}
		return new double[] {sd, cov};
	}
	
//	private GriddedGeoDataSet calcPercentile(GriddedGeoDataSet[] maps, GriddedGeoDataSet comp) {
//		return calcPercentile(List.of(maps), weights, comp);
//	}
//	 
//	private GriddedGeoDataSet calcPercentile(List<GriddedGeoDataSet> maps, List<Double> weights, GriddedGeoDataSet comp) {
//		Preconditions.checkState(maps.size() == weights.size());
//		GriddedGeoDataSet ret = new GriddedGeoDataSet(maps.get(0).getRegion(), false);
//		
//		double totWeight;
//		if (weights == this.weights) {
//			totWeight = this.totWeight;
//		} else {
//			totWeight = 0d;
//			for (double weight : weights)
//				totWeight += weight;
//		}
//		
//		for (int i=0; i<ret.size(); i++) {
//			double compVal = comp.get(i);
//			double weightAbove = 0d; // weight of tree thats above comp value
//			double weightEqual = 0d; // weight of tree thats equal to comp value
//			double weightBelow = 0d; // weight of tree thats below comp value
//			int numAbove = 0;
//			int numBelow = 0;
//			for (int j=0; j<maps.size(); j++) {
//				double val = maps.get(j).get(i);
//				double weight = weights.get(j);
//				if (((float) compVal == (float)val) || (Double.isNaN(compVal)) && Double.isNaN(val)) {
//					weightEqual += weight;
//				} else if (compVal < val) {
//					weightAbove += weight;
//					numAbove++;
//				} else if (compVal > val) {
//					numBelow++;
//					weightBelow += weight;
//				}
//			}
//			if (weightEqual != 0d) {
//				// redistribute any exactly equal to either side
//				weightAbove += 0.5*weightEqual;
//				weightBelow += 0.5*weightEqual;
//			}
//			// normalize by total weight
//			weightAbove /= totWeight;
//			weightBelow /= totWeight;
//			double percentile;
//			if (numAbove == maps.size() || numBelow == maps.size())
//				percentile = Double.NaN;
//			else
//				percentile = 100d*weightBelow;
////			if (numAbove == maps.size() || numBelow == maps.size()) {
////				percentile = Double.NaN;
////			} else if (weightAbove > weightBelow) {
////				// more of the distribution is above my value
////				// this means that the percentile is <50
////				percentile = 100d*weightBelow/totWeight;
////			} else {
////				// more of the distribution is below my value
////				// this means that the percentile is >50
////				percentile = 100d*weightAbove/totWeight;
////				percentile = 100d*(1d - (weightAbove/totWeight));
////			}
//			ret.set(i, percentile);
//		}
//		
//		return ret;
//	}
	
	public void buildReport(File outputDir, String name, LogicTreeHazardCompare comp, String compName) throws IOException {
		List<String> lines = new ArrayList<>();
		
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		lines.add("# "+name+" Hazard Maps");
		lines.add("");
		int tocIndex = lines.size();
		String topLink = "_[(top)](#table-of-contents)_";
		
		futures = new ArrayList<>();
		
		boolean intermediateWrite = branches != null && branches.size() > 50 && !new File(outputDir, "index.html").exists();
		
		int cptWidth = 800;
		
		List<LogicTreeLevel<?>> uniqueSamplingLevels = null;
		Boolean canDecomposeVariance = null;
		AbstractLTVarianceDecomposition varDecomposer = null;
		
		for (double period : periods) {
			String perLabel, perPrefix, unitlessPerLabel;
			if (period == 0d) {
				unitlessPerLabel = "PGA";
				perPrefix = "pga";
			} else {
				unitlessPerLabel = (float)period+"s SA";
				perPrefix = (float)period+"s";
			}
			String perUnits = "(g)";
			perLabel = unitlessPerLabel+" "+perUnits;
			
			for (ReturnPeriods rp : rps) {
				String label = perLabel+", "+rp.label;
				String unitlessLabel = unitlessPerLabel+", "+rp.label;
				String prefix = perPrefix+"_"+rp.name();
				
				// plot CPT files for grabbing externally
				PlotUtils.writeScaleLegendOnly(resourcesDir, prefix+"_cpt",
						GeographicMapMaker.buildCPTLegend(logCPT, label), cptWidth, true, true);
				PlotUtils.writeScaleLegendOnly(resourcesDir, prefix+"_cpt_cov",
						GeographicMapMaker.buildCPTLegend(covCPT, "COV, "+unitlessLabel), cptWidth, true, true);
				PlotUtils.writeScaleLegendOnly(resourcesDir, prefix+"_cpt_pDiff",
						GeographicMapMaker.buildCPTLegend(pDiffCPT, "% Change, "+unitlessLabel), cptWidth, true, true);
				PlotUtils.writeScaleLegendOnly(resourcesDir, prefix+"_cpt_diff",
						GeographicMapMaker.buildCPTLegend(diffCPT, "Difference, "+label), cptWidth, true, true);
				
				System.out.println(label);
				
				lines.add("## "+unitlessLabel);
				lines.add(topLink);
				lines.add("");
				
				GriddedGeoDataSet[] maps = this.loadMaps(rp, period);
				Preconditions.checkNotNull(maps);
				for (int i=0; i<maps.length; i++)
					Preconditions.checkNotNull(maps[i], "map %s is null", i);
				
				GriddedRegion region = maps[0].getRegion();
				
				System.out.println("Calculating norm CDFs");
				LightFixedXFunc[] mapNCDFs = buildNormCDFs(maps, weights);
				System.out.println("Calculating mean, median, bounds, COV");
				// see if we've precomputed mean
				GriddedGeoDataSet mean = ignorePrecomputed ? null :
					loadPrecomputedMeanMap(LogicTreeCurveAverager.MEAN_PREFIX, rp, period);
				GriddedGeoDataSet mapMean = null; // mean from the maps directly, not from curves
				boolean meanIsFromCurves = mean != null;
				if (mean == null) {
					// need to calculate mean from branch maps
					mapMean = buildMean(maps);
					mean = mapMean;
				}
				GriddedGeoDataSet median = calcMapAtPercentile(mapNCDFs, region, 50d);
				GriddedGeoDataSet max = buildMax(maps, weights);
				GriddedGeoDataSet min = buildMin(maps, weights);
				GriddedGeoDataSet spread = buildSpread(log10(min), log10(max));
				GriddedGeoDataSet iqr = calcIQR(mapNCDFs, region);
				GriddedGeoDataSet sd = new GriddedGeoDataSet(region);
				GriddedGeoDataSet cov = new GriddedGeoDataSet(region);
				calcSD_COV(maps, weights, mean, sd, cov);
				GriddedGeoDataSet meanPercentile = calcPercentileWithinDist(mapNCDFs, mean);
				
				File hazardCSV = new File(resourcesDir, prefix+".csv");
				System.out.println("Writing CSV: "+hazardCSV.getAbsolutePath());
				writeHazardCSV(hazardCSV, mean, median, min, max, cov, meanPercentile, null, null);
				
//				table.addLine(meanMinMaxSpreadMaps(mean, min, max, spread, name, label, prefix, resourcesDir));
				
				LightFixedXFunc[] cmapNCDFs = null;
				GriddedGeoDataSet cmean = null;
				GriddedGeoDataSet cmedian = null;
				GriddedGeoDataSet cmin = null;
				GriddedGeoDataSet cmax = null;
				GriddedGeoDataSet cspread = null;
				GriddedGeoDataSet ciqr = null;
				GriddedGeoDataSet csd = null;
				GriddedGeoDataSet ccov = null;
				GriddedGeoDataSet cMeanPercentile = null;
				GriddedGeoDataSet cMedianPercentile = null;
				
				boolean multi = branches.size() > 1;
				
				if (comp != null) {
					comp.setRemapToRegion(region);
					System.out.println("Loading comparison");
					GriddedGeoDataSet[] cmaps = comp.loadMaps(rp, period);
					Preconditions.checkNotNull(cmaps);
					for (int i=0; i<cmaps.length; i++)
						Preconditions.checkNotNull(cmaps[i], "map %s is null", i);

					System.out.println("Calculating comparison norm CDFs");
					cmapNCDFs = buildNormCDFs(cmaps, comp.weights);
					System.out.println("Calculating comparison mean, median, bounds, COV");
					if (!ignorePrecomputed)
						cmean = comp.loadPrecomputedMeanMap(LogicTreeCurveAverager.MEAN_PREFIX, rp, period);
					if (cmean == null)
						cmean = comp.buildMean(cmaps);
					cmedian = calcMapAtPercentile(cmapNCDFs, region, 50d);
					cmax = comp.buildMax(cmaps, comp.weights);
					cmin = comp.buildMin(cmaps, comp.weights);
					cspread = comp.buildSpread(log10(cmin), log10(cmax));
					ciqr = calcIQR(cmapNCDFs, region);
					csd = new GriddedGeoDataSet(region);
					ccov = new GriddedGeoDataSet(region);
					calcSD_COV(cmaps, comp.weights, cmean, csd, ccov);
//					table.addLine(meanMinMaxSpreadMaps(cmean, cmin, cmax, cspread, compName, label, prefix+"_comp", resourcesDir));
					
					if (multi) {
						cMeanPercentile = calcPercentileWithinDist(mapNCDFs, cmean);
						cMedianPercentile = calcPercentileWithinDist(mapNCDFs, cmedian);
					}
					
					File compHazardCSV = new File(resourcesDir, prefix+"_comp.csv");
					System.out.println("Writing CSV: "+compHazardCSV.getAbsolutePath());
					writeHazardCSV(compHazardCSV, cmean, cmedian, cmin, cmax, ccov, null, cMeanPercentile, cMedianPercentile);
					
					lines.add("Download Mean Hazard CSVs: ["+hazardCSV.getName()+"]("+resourcesDir.getName()+"/"+hazardCSV.getName()
						+")  ["+compHazardCSV.getName()+"]("+resourcesDir.getName()+"/"+compHazardCSV.getName()+")");
				} else {
					lines.add("Download Mean Hazard CSV: ["+hazardCSV.getName()+"]("+resourcesDir.getName()+"/"+hazardCSV.getName()+")");
				}
				lines.add("");
				
				boolean cmulti = comp != null && comp.branches != null && comp.branches.size() > 1;
				
				MapPlot meanMapPlot = mapper.buildMapPlot(resourcesDir, prefix+"_mean", mean,
						logCPT, TITLES ? name : " ", (multi ? "Weighted-Average" : "Mean")+", "+label, false);
				File meanMapFile = new File(resourcesDir, meanMapPlot.prefix+".png");
				GriddedGeoDataSet.writeXYZFile(mean, new File(resourcesDir, prefix+"_mean.xyz"));
				File medianMapFile = null;
				if (multi) {
					medianMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_median", median,
							logCPT, TITLES ? name : " ", "Weighted-Median, "+label);
					GriddedGeoDataSet.writeXYZFile(median, new File(resourcesDir, prefix+"_median.xyz"));
				}
				
				if (cmean == null) {
					if (multi) {
						lines.add("### Mean and median hazard maps, "+unitlessLabel);
						lines.add(topLink); lines.add("");
					}
					
					TableBuilder table = MarkdownUtils.tableBuilder();
					// no comparison, simple table
					if (multi) {
						table.initNewLine();
						table.addColumn(MarkdownUtils.boldCentered("Weighted-Average"));
						table.addColumn(MarkdownUtils.boldCentered("Weighted-Median"));
						table.finalizeLine();
					}
					table.initNewLine();
					table.addColumn("![Mean Map]("+resourcesDir.getName()+"/"+meanMapFile.getName()+")");
					if (multi)
						table.addColumn("![Median Map]("+resourcesDir.getName()+"/"+medianMapFile.getName()+")");
					table.finalizeLine();
					table.initNewLine();
					table.addColumn(mapStats(mean));
					if (multi)
						table.addColumn(mapStats(median));
					table.finalizeLine();
					lines.addAll(table.build());
					lines.add("");
					
					if (!multi) {
						// don't bother with percentiles and such
						if (intermediateWrite)
							writeIntermediate(outputDir, lines, tocIndex);
						continue;
					}
				} else {
					if (multi) {
						lines.add("### Mean hazard maps and comparisons, "+unitlessLabel);
						lines.add(topLink); lines.add("");
					}
					
					TableBuilder table = MarkdownUtils.tableBuilder();
					
					// comparison mean
					File cmeanMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_mean", cmean,
							logCPT, TITLES ? compName : " ", "Weighted-Average, "+label);
					GriddedGeoDataSet.writeXYZFile(cmean, new File(resourcesDir, prefix+"_comp_mean.xyz"));
					
					table.initNewLine();
					if (multi)
						table.addColumn(MarkdownUtils.boldCentered("Primary (_"+name+"_) Weighted-Average"));
					else
						table.addColumn(MarkdownUtils.boldCentered("Primary (_"+name+"_)"));
					if (cmulti)
						table.addColumn(MarkdownUtils.boldCentered("Comparison (_"+compName+"_) Weighted-Average"));
					else
						table.addColumn(MarkdownUtils.boldCentered("Comparison (_"+compName+"_)"));
					table.finalizeLine().initNewLine();
					table.addColumn("![Mean Map]("+resourcesDir.getName()+"/"+meanMapFile.getName()+")");
					table.addColumn("![Mean Map]("+resourcesDir.getName()+"/"+cmeanMapFile.getName()+")");
					table.finalizeLine();
					table.initNewLine();
					table.addColumn(mapStats(mean));
					if (multi)
						table.addColumn(mapStats(cmean));
					table.finalizeLine();
					
					lines.addAll(table.build());
					lines.add("");
					
					if (multi) {
						table = MarkdownUtils.tableBuilder();
						addDiffInclExtremesLines(mean, name, min, max, cmean, compName, prefix+"_mean", resourcesDir, table, "Mean", unitlessLabel, false, comp.gridReg);
						addDiffInclExtremesLines(mean, name, min, max, cmean, compName, prefix+"_mean", resourcesDir, table, "Mean", label, true, comp.gridReg);
						
						lines.add("The following plots compare mean hazard between _"+name+"_ and a comparison model, _"
								+ compName+"_. The top row gives hazard ratios, expressed as % change, and the bottom row "
								+ "gives differences.");
						lines.add("");
						lines.add("The left column compares the mean maps directly, with the comparison model as the "
								+ "divisor/subtrahend. Warmer colors indicate increased hazard in _"+name
								+ "_ relative to _"+compName+"_.");
						lines.add("");
						lines.add("The right column shows where and by how much the comparison mean model (_"+compName
								+ "_) is outside the distribution of values across all branches of the primary model (_"
								+ name+"_). Here, places that are zeros (light gray) indicate that the comparison mean "
								+ "hazard map is fully contained within the range of values in _"+name+"_, cool colors "
								+ "indicate areas where the primary model is always lower than the comparison mean model, "
								+ "and warm colors areas where the primary model is always greater. Note that the color "
								+ "scales are reversed here so that colors are consistent with the left column even though "
								+ "the comparison model is now the dividend/minuend.");
						lines.add("");
						lines.addAll(table.build());
						lines.add("");
						
						File cmedianMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_median", cmedian,
								logCPT, TITLES ? compName : " ", "Weighted-Median, "+label);
						GriddedGeoDataSet.writeXYZFile(cmedian, new File(resourcesDir, prefix+"_comp_median.xyz"));
						
						lines.add("### Median hazard maps and comparisons, "+unitlessLabel);
						lines.add(topLink); lines.add("");
						
						table = MarkdownUtils.tableBuilder();
						table.initNewLine();
						table.addColumn(MarkdownUtils.boldCentered("Primary Weighted-Median"));
						if (cmulti)
							table.addColumn(MarkdownUtils.boldCentered("Comparison Weighted-Median"));
						else
							table.addColumn(MarkdownUtils.boldCentered("Comparison"));
						table.finalizeLine().initNewLine();
						table.addColumn("![Median Map]("+resourcesDir.getName()+"/"+medianMapFile.getName()+")");
						table.addColumn("![Median Map]("+resourcesDir.getName()+"/"+cmedianMapFile.getName()+")");
						table.finalizeLine();
						table.initNewLine();
						table.addColumn(mapStats(median));
						if (multi)
							table.addColumn(mapStats(cmedian));
						table.finalizeLine();
						
						lines.addAll(table.build());
						lines.add("");
						
						table = MarkdownUtils.tableBuilder();
						
						addDiffInclExtremesLines(median, name, min, max, cmedian, compName, prefix+"_median", resourcesDir, table, "Median", unitlessLabel, false, comp.gridReg);
						addDiffInclExtremesLines(median, name, min, max, cmedian, compName, prefix+"_median", resourcesDir, table, "Median", label, true, comp.gridReg);
						
						lines.add("");
						lines.add("This section is the same as above, but using median hazard maps rather than mean.");
						lines.add("");
						lines.addAll(table.build());
						lines.add("");
						
						table = MarkdownUtils.tableBuilder();
						
						table.addLine(MarkdownUtils.boldCentered("Comparison Mean Percentile"),
								MarkdownUtils.boldCentered("Comparison Median Percentile"));
						if (comp.gridReg.getNodeCount() < gridReg.getNodeCount()) {
							// see if we should shrink them to comparison
							GriddedGeoDataSet remappedMeanPercentile = new GriddedGeoDataSet(comp.gridReg, false);
							if (checkShrinkToComparison(cMeanPercentile, remappedMeanPercentile)) {
								GriddedGeoDataSet remappedMedianPercentile = new GriddedGeoDataSet(comp.gridReg, false);
								Preconditions.checkState(checkShrinkToComparison(cMedianPercentile, remappedMedianPercentile));
								cMeanPercentile = remappedMeanPercentile;
								cMedianPercentile = remappedMedianPercentile;
							}
						}
						table.initNewLine();
						File map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_mean_percentile", cMeanPercentile,
								percentileCPT, TITLES ? name+" vs "+compName : " ", "Comparison Mean %-ile, "+unitlessLabel);
						table.addColumn("![Mean Percentile Map]("+resourcesDir.getName()+"/"+map.getName()+")");
						map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_median_percentile", cMedianPercentile,
								percentileCPT, TITLES ? name+" vs "+compName : " ", "Comparison Median %-ile, "+unitlessLabel);
						table.addColumn("![Median Percentile Map]("+resourcesDir.getName()+"/"+map.getName()+")");
						table.finalizeLine();
						table.addLine(mapStats(cMeanPercentile), mapStats(cMedianPercentile));
						lines.add("### Percentile comparison maps, "+unitlessLabel);
						lines.add(topLink); lines.add("");
						lines.add("The maps below show where the comparison (_"+compName+"_) model mean (left column) and "
								+ "median (right column) map lies within the primary model (_"+name+"_) distribution. "
								+ "Areas where the comparison mean or median map is outside the primary model distribution "
								+ "are shown here in black regardless of if they are above or below.");
						lines.add("");
						lines.addAll(table.build());
						lines.add("");
					} else {
						// with comparison, but single branch
						table = MarkdownUtils.tableBuilder();
						
						addSingleBranchDiffLines(mean, name, cmean, compName, prefix+"_mean", resourcesDir, table,
								unitlessLabel, label, comp.gridReg);
						
						lines.add("The following plots compare hazard between _"+name+"_ and a comparison model, _"
								+ compName+"_. The left column gives hazard ratios, expressed as % change, and the "
								+ "right column gives differences. The comparison model is the divisor/subtrahend; "
								+ "warmer colors indicate increased hazard in _"+name+"_ relative to _"+compName+"_.");
						lines.add("");
						lines.addAll(table.build());
						lines.add("");
						
						// don't bother with branches and such
						if (intermediateWrite)
							writeIntermediate(outputDir, lines, tocIndex);
						continue;
					}
				}
				
				// plot mean percentile
				TableBuilder table = MarkdownUtils.tableBuilder();
				File meanPercentileMap = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_mean_percentile",
						meanPercentile, percentileCPT, TITLES ? "Branch-Averaged Percentiles" : " ",
						"Branch Averaged %-ile, "+unitlessLabel);
//				GriddedGeoDataSet median = calc
				table.addLine("Mean Map Percentile", "Mean vs Median");
				GriddedGeoDataSet meanMedDiff = buildPDiff(median, mean);
				File meanMedDiffMap = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_mean_median_diff",
						meanMedDiff, pDiffCPT, TITLES ? name : " ", "Mean / Median, % Change, "+unitlessLabel, true);
				table.addLine("![BA percentiles]("+resourcesDir.getName()+"/"+meanPercentileMap.getName()+")",
						"![Median vs Mean]("+resourcesDir.getName()+"/"+meanMedDiffMap.getName()+")");
				table.addLine(mapStats(meanPercentile), mapStats(meanMedDiff, true));
				String branchStr = "Branched-average hazard can be dominated by outlier branches. The map below on the "
						+ "left shows the percentile at which the ";
				if (comp != null)
					branchStr += "primary model's mean map lies within its own full hazard distribution. ";
				else
					branchStr += "mean map lies within the full hazard distribution. ";
				branchStr += "Areas far from the 50-th percentile here are likely outlier-dominated and may show up in "
						+ "percentile comparison maps, even if mean hazard differences are minimal. Keep this in mind "
						+ "when evaluating ";
				if (comp != null)
					branchStr += "the maps above, and ";
				branchStr += "the influence of individual logic tree branches by this metric. The right map show the ratio "
							+ "of mean to median hazard.";
				
				if (DEBUG_LOC != null) {
					int index = gridReg.indexForLocation(DEBUG_LOC);
					if (index > 0) {
						File debugFile = new File(resourcesDir, "loc_debug_"+prefix+".txt");
						System.out.println("Writing debug info to: "+debugFile.getAbsolutePath());
						FileWriter fw = new FileWriter(debugFile);
						fw.write("Median, mean, percentile debug for location "+index+": "+DEBUG_LOC+"\n");
						fw.write("Mean: "+mean.get(index)+"\n");
						fw.write("Median: "+median.get(index)+"\n");
						fw.write("Mean percentile: "+meanPercentile.get(index)+"\n");
						fw.write("Norm CDF:\n");
						LightFixedXFunc ncdf = mapNCDFs[index];
						for (int i=0; i<ncdf.size(); i++)
							fw.write("\t"+(float)ncdf.getX(i)+"\t"+(float)ncdf.getY(i)+"\n");
						fw.close();
					}
				}
				
				lines.add(branchStr);
				lines.add("");
				if (meanIsFromCurves) {
					lines.add("Note: The mean map here is computed directly from mean hazard curves, but the median map "
							+ "is taken as the median value of hazard maps across all branches (rather than first "
							+ "calculating median curves at each location), which might bias this comparison.");
					lines.add("");
				}
				lines.addAll(table.build());
				lines.add("");
				
				lines.add("### Bounds, spread, and COV, "+unitlessLabel);
				lines.add(topLink); lines.add("");
				
				String minMaxStr = "The maps below show the range of values across all logic tree branches, the ratio of "
						+ "the maximum to minimum value, the interquartile range (p75 - p25), standard deviation, "
						+ "and the coefficient of variation (std. dev. / mean). Note that "
						+ "the minimum and maximum maps are not a result for any single logic tree branch, but rather "
						+ "the smallest or largest value encountered at each location across all logic tree branches.";

				if (cmean == null || comp.branches.size() == 1) {
					// no comparison
					table = MarkdownUtils.tableBuilder();
					table.addLine(MarkdownUtils.boldCentered("Minimum"), MarkdownUtils.boldCentered("Maximum"));
					
					File minMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_min", min,
							logCPT, TITLES ? name : " ", "Minimum, "+label);
					File maxMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_max", max,
							logCPT, TITLES ? name : " ", "Maximum, "+label);
					File spreadMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_spread", spread,
							spreadCPT, TITLES ? name : " ", "Log10 (Max/Min), "+unitlessLabel);
					File iqrMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_iqr", iqr,
							iqrCPT, TITLES ? name : " ", "IQR, "+label);
					File sdMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_sd", sd,
							sdCPT, TITLES ? name : " ", "SD, "+label);
					File covMapFile = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_cov", cov,
							covCPT, TITLES ? name : " ", "COV, "+unitlessLabel);
					
					table.initNewLine();
					table.addColumn("![Min Map]("+resourcesDir.getName()+"/"+minMapFile.getName()+")");
					table.addColumn("![Max Map]("+resourcesDir.getName()+"/"+maxMapFile.getName()+")");
					table.finalizeLine();
					table.addLine(mapStats(min), mapStats(max));
					table.addLine(MarkdownUtils.boldCentered("Log10 (Max/Min)"), MarkdownUtils.boldCentered("Interquartile Range"));
					table.initNewLine();
					table.addColumn("![Spread Map]("+resourcesDir.getName()+"/"+spreadMapFile.getName()+")");
					table.addColumn("![IQR Map]("+resourcesDir.getName()+"/"+iqrMapFile.getName()+")");
					table.finalizeLine();
					table.addLine(mapStats(spread), mapStats(iqr));
					table.addLine(MarkdownUtils.boldCentered("SD"), MarkdownUtils.boldCentered("COV"));
					table.initNewLine();
					table.addColumn("![SD Map]("+resourcesDir.getName()+"/"+sdMapFile.getName()+")");
					table.addColumn("![COV Map]("+resourcesDir.getName()+"/"+covMapFile.getName()+")");
					table.finalizeLine();
					table.addLine(mapStats(sd), mapStats(cov));
					
					lines.add("");
					lines.add(minMaxStr);
					lines.add("");
					lines.addAll(table.build());
					lines.add("");
				} else {
					table = MarkdownUtils.tableBuilder();
					// comparison
					addMapCompDiffLines(min, name, cmin, compName, prefix+"_min", resourcesDir, table,
							"Minimum", "Minimum "+label, "Minimum "+unitlessLabel, logCPT, diffCPT, pDiffCPT, comp.gridReg);
					addMapCompDiffLines(max, name, cmax, compName, prefix+"_max", resourcesDir, table,
							"Maximum", "Maximum "+label, "Maximum "+unitlessLabel, logCPT, diffCPT, pDiffCPT, comp.gridReg);
					addMapCompDiffLines(spread, name, cspread, compName, prefix+"_spread", resourcesDir, table,
							"Log10 (Max/Min)", "Log10 (Max/Min) "+unitlessLabel, "Log10 (Max/Min) "+unitlessLabel,
							spreadCPT, spreadDiffCPT, pDiffCPT, comp.gridReg);
					addMapCompDiffLines(iqr, name, ciqr, compName, prefix+"_iqr", resourcesDir, table,
							"Interquartile Range", "IQR "+label, "IQR "+unitlessLabel,
							iqrCPT, iqrDiffCPT, pDiffCPT, comp.gridReg);
					addMapCompDiffLines(sd, name, csd, compName, prefix+"_sd", resourcesDir, table,
							"SD", label+", SD", unitlessLabel+", SD", sdCPT, sdDiffCPT, pDiffCPT, comp.gridReg);
					addMapCompDiffLines(cov, name, ccov, compName, prefix+"_cov", resourcesDir, table,
							"COV", unitlessLabel+", COV", unitlessLabel+", COV", covCPT, covDiffCPT, pDiffCPT, comp.gridReg);
					
					lines.add("");
					lines.add(minMaxStr+" Each of those quantities is plotted separately for the primary and comparison "
							+ "model, then compared in the rightmost columns.");
					lines.add("");
					lines.addAll(table.build());
					lines.add("");
				}
				
				if (skipLogicTree) {
					if (intermediateWrite)
						writeIntermediate(outputDir, lines, tocIndex);
					continue;
				}
				
				cmapNCDFs = null;
				cmean = null;
				cmedian = null;
				cmin = null;
				cmax = null;
				cspread = null;
				ccov = null;
				System.gc();
				
				lines.add("### "+unitlessLabel+" Logic Tree Comparisons");
				lines.add(topLink); lines.add("");
				
				lines.add("This section shows how hazard changes across branch choices at each level of the logic tree. "
						+ "The summary figures below show mean hazard on the left, and then ratios & differences between "
						+ "the mean map considering subsets of the model holding each branch choice constant, and the "
						+ "overall mean map.");
				lines.add("");
				lines.add("Summary CSVs: [Branch Mean % Change]("+resourcesDir.getName()+"/"+prefix+"_branch_mean_summary.csv) "
						+ "&nbsp;|&nbsp; [Branch Abs Mean % Change]("+resourcesDir.getName()+"/"+prefix+"_branch_mean_abs_summary.csv)");
				lines.add("");
				
				int combinedMapIndex = lines.size();
				
				int numLevels = tree.getLevels().size();
				
				if (canDecomposeVariance == null) {
					System.out.println("Seeing if the logic tree is structured in a way that supports variance decomposition");
					// track the levels where there are a unique sample for each branch in the tree, i.e., a value
					// is never repeated across any upstream branches
					// if there are multiple of these, we can't separate out the variance individually
					uniqueSamplingLevels = new ArrayList<>();
					System.out.println("Looking for unique random sampling levels (those where samples are not reused "
							+ "across upstream branches) for exclusion in variance calculations");
					boolean firstVaryingLevel = true;
					for (int l=0; l<numLevels; l++) {
						LogicTreeLevel<?> level = tree.getLevels().get(l);
						if (level instanceof RandomlySampledLevel<?> || level instanceof FileBackedLevel) {
							// either a random sampling level, or could have been (maybe deserialization failed is now is file backed)
							// we want to detect the case where the random sampling is across the whole tree, i.e., values
							// are never reused
							
							if (level.getNodes().size() == tree.size()) {
								// full tree random sampling
								System.out.println("\tDetected that "+level.getName()+" is a randomly sampled level "
										+ "because it has a unique value for each tree branch");
								uniqueSamplingLevels.add(level);
							} else if (!firstVaryingLevel) {
								// there's not a one-to-one correspondence, but it still could be tree-wide random sampling
								// if the tree was later downsampled and/or combined with another tree
								// 
								// we'll detect this by checking to see if there are any values shared amongst upstream
								// branches; if there are not (meaning that nodes from this level were never reused, at
								// least looking at upstream branches), then we'll treat this as a random sampling level
								// 
								// we can only do this check for levels downstream of the first varying level, however
								
								List<LogicTreeLevel<? extends LogicTreeNode>> levelsAbove = new ArrayList<>(tree.getLevels().subList(0, l));
								Map<LogicTreeNode, LogicTreeBranch<?>> prevBranchesAbove = new HashMap<>();
								boolean match = true;
								for (LogicTreeBranch<?> branch : tree) {
									LogicTreeNode node = branch.getValue(l);
									LogicTreeBranch<LogicTreeNode> branchAbove = new LogicTreeBranch<>(levelsAbove);
									for (int i=0; i<l; i++)
										branchAbove.setValue(i, branch.getValue(i));
									LogicTreeBranch<?> prevBranchAbove = prevBranchesAbove.get(node);
									if (prevBranchAbove == null) {
										prevBranchesAbove.put(node, branchAbove);
									} else if (!prevBranchAbove.equals(branchAbove)) {
										// multiple upstream branches use this level
										match = false;
										break;
									}
								}
								if (match) {
									System.out.println("\tDetected that "+level.getName()+" is a randomly sampled level "
											+ "because no unique upstream branches re-use it, even though it only has "
											+level.getNodes().size()+" nodes for "+tree.size()+" total branches");
									uniqueSamplingLevels.add(level);
								}
							}
						}
						if (firstVaryingLevel) {
							// see if this node varies
							LogicTreeNode firstVal = tree.getBranch(0).getValue(l);
							for (LogicTreeBranch<?> branch : tree) {
								if (!branch.getValue(l).equals(firstVal)) {
									// there are multiple values for this level
									firstVaryingLevel = false;
									break;
								}
							}
						}
					}
					
					if (uniqueSamplingLevels.isEmpty()) {
						System.out.println("\tNo such sampling levels found");
					}
					
					// now see if the logic tree is complete or randomly sampled
					// if complete, we'll use the better marginal averaging approach
					// if not, we'll use the sparse approach
					HashSet<LogicTreeBranch<?>> concreteBranches = new HashSet<>();
					List<LogicTreeLevel<? extends LogicTreeNode>> concreteLevels = new ArrayList<>();
					List<Integer> levelMappings = new ArrayList<>();
					List<HashSet<LogicTreeNode>> encounteredConcreteNodes = new ArrayList<>();
					for (int l=0; l<numLevels; l++) {
						LogicTreeLevel<?> level = tree.getLevels().get(l);
						if (!uniqueSamplingLevels.contains(level)) {
							concreteLevels.add(level);
							levelMappings.add(l);
							encounteredConcreteNodes.add(new HashSet<>());
						}
					}
					for (LogicTreeBranch<?> branch : tree) {
						LogicTreeBranch<LogicTreeNode> concreteBranch = new LogicTreeBranch<>(concreteLevels);
						for (int i=0; i<concreteLevels.size(); i++) {
							LogicTreeNode node = branch.getValue(levelMappings.get(i));
							concreteBranch.setValue(i, node);
							encounteredConcreteNodes.get(i).add(node);
						}
						concreteBranches.add(concreteBranch);
					};
					int completeTreeCount = 1;
					for (HashSet<LogicTreeNode> nodes : encounteredConcreteNodes)
						completeTreeCount *= nodes.size();
					if (completeTreeCount == 1) {
						canDecomposeVariance = false;
					} else if (completeTreeCount != concreteBranches.size() || forceSparseLTVar) {
						System.out.println("Have to use sparse variance decomposition approach because the logic tree is downsampled");
						canDecomposeVariance = true;
						varDecomposer = new SparseLTVarianceDecomposition(tree, uniqueSamplingLevels, exec);
					} else {
						canDecomposeVariance = true;
						varDecomposer = new MarginalAveragingLTVarianceDecomposition(tree, uniqueSamplingLevels, exec);
					}
				}
				
				List<VarianceContributionResult> varResults = null;
				if (canDecomposeVariance) {
					varResults = new ArrayList<>(numLevels);
					for (int l=0; l<numLevels; l++)
						varResults.add(null);
					GriddedGeoDataSet covOfMapMean;
					GriddedGeoDataSet varOfMapMean;
					if (meanIsFromCurves) {
						// need to calculate the raw mean of the maps because that's how we're going to calculate level COVs
						mapMean = buildMean(maps);
						covOfMapMean = new GriddedGeoDataSet(region);
						for (int i=0; i<mapMean.size(); i++)
							covOfMapMean.set(i, sd.get(i)/mapMean.get(i));
					} else {
						Preconditions.checkNotNull(mapMean);
						covOfMapMean = cov;
					}
					varOfMapMean = new GriddedGeoDataSet(region);
					for (int i=0; i<cov.size(); i++) {
						double var = sd.get(i)*sd.get(i);
						varOfMapMean.set(i, var);
					}
					varDecomposer.initForMaps(mapMean, varOfMapMean, maps, weights);
				}
				
				
				System.out.println("Building logic tree plots");
				
				List<LogicTreeLevel<?>> branchLevels = new ArrayList<>();
				List<List<LogicTreeNode>> branchLevelValues = new ArrayList<>();
				List<List<MapPlot>> branchLevelPDiffPlots = new ArrayList<>();
				List<List<MapPlot>> branchLevelDiffPlots = new ArrayList<>();
				
				// precompute choice means for everything
				List<HashMap<LogicTreeNode, List<GriddedGeoDataSet>>> choiceMapsList = new ArrayList<>();
				List<HashMap<LogicTreeNode, List<Double>>> choiceWeightsList = new ArrayList<>();
				List<HashMap<LogicTreeNode, GriddedGeoDataSet>> choiceMeansList = new ArrayList<>();
				List<HashMap<LogicTreeNode, GriddedGeoDataSet>> choiceMeanWithoutsList = new ArrayList<>();
				List<HashMap<LogicTreeNode, GriddedGeoDataSet>> choiceMeanPercentilesList = new ArrayList<>();
				
				// do mean map calculations first so that we can clear out the full NormCDFs from memory
				
				for (int l=0; l<numLevels; l++) {
					LogicTreeLevel<?> level = tree.getLevels().get(l);
					HashMap<LogicTreeNode, List<GriddedGeoDataSet>> choiceMaps = new HashMap<>();
					HashMap<LogicTreeNode, List<Double>> choiceWeights = new HashMap<>();
					for (int i=0; i<branches.size(); i++) {
						LogicTreeBranch<?> branch = branches.get(i);
						LogicTreeNode choice = branch.getValue(l);
						List<GriddedGeoDataSet> myChoiceMaps = choiceMaps.get(choice);
						if (myChoiceMaps == null) {
							myChoiceMaps = new ArrayList<>();
							choiceMaps.put(choice, myChoiceMaps);
							choiceWeights.put(choice, new ArrayList<>());
						}
						myChoiceMaps.add(maps[i]);
						choiceWeights.get(choice).add(weights.get(i));
					}
//					System.out.println("Processing level "+level.getName()+" with "+choiceMaps.size()+" choices");
					boolean include = choiceMaps.size() > 1;
					if (canDecomposeVariance && include) {
						if (uniqueSamplingLevels.size() > 1 && uniqueSamplingLevels.get(0) != level &&
								uniqueSamplingLevels.contains(level))
							// this is a secondary unique sampling level, skip as already bundled previously
							continue;
						VarianceContributionResult varResult = varDecomposer.calcMapVarianceContributionForLevel(
								l, level, choiceMaps, choiceWeights);
						if (varResult != null) {
//							// orig here is the full original, used for ratios
//							// ref here is the reference value we're comparing to, used for differences
//							AvgMaxCalc refCOVs = new AvgMaxCalc();
//							AvgMaxCalc withoutCOVs = new AvgMaxCalc();
//							AvgMaxCalc deltaCOVs = new AvgMaxCalc();
//							AvgMaxCalc fDiffCOVs = new AvgMaxCalc();
//							AvgMaxCalc fractVars = new AvgMaxCalc();
//							for (int i=0; i<covExcluding.size(); i++) {
//								double refCOV = refCOVMap.get(i);
//								double origCOV = covOfMapMean.get(i);
//								double withoutCOV = covExcluding.get(i);
//								double refVar = refVarMap.get(i);
//								double origVar = varOfMapMean.get(i);
//								double withoutVar = sdExcluding.get(i)*sdExcluding.get(i);
//								double varDiff = refVar - withoutVar;
//								double deltaCOV = refCOV - withoutCOV;
//								refCOVs.addValue(refCOV);
//								withoutCOVs.addValue(withoutCOV);
//								deltaCOVs.addValue(deltaCOV);
//								fDiffCOVs.addValue(deltaCOV/origCOV);
//								fractVars.addValue(varDiff/origVar);
//							}
//							
//							System.out.println("\tOrigCOV="+(float)origCOVs.getAverage());
//							System.out.println("\tRefCOV="+(float)refCOVs.getAverage());
//							System.out.println("\tWithoutCOV="+(float)withoutCOVs.getAverage());
//							System.out.println("\tdeltaCOV="+deltaCOVs.getAverage()+" ("+pDF.format(fDiffCOVs.getAverage())+")");
//							System.out.println("\tdeltaVar="+fractVars.getAverage()+" ("+pDF.format(fractVars.getAverage())+")");
							
							varResults.set(l, varResult);
						}
					}
					if (LogicTreeCurveAverager.shouldSkipLevel(level, choiceMaps.size())) {
						System.out.println("Skipping randomly sampled level ("+level.getName()
							+") with "+choiceMaps.size()+" choices");
						include = false;
					}
					if (include) {
						choiceMapsList.add(choiceMaps);
						choiceWeightsList.add(choiceWeights);

						HashMap<LogicTreeNode, GriddedGeoDataSet> choiceMeans = new HashMap<>();
						HashMap<LogicTreeNode, GriddedGeoDataSet> choiceMeanWithouts = new HashMap<>();
						HashMap<LogicTreeNode, GriddedGeoDataSet> choiceMeanPercentiles = new HashMap<>();
						for (LogicTreeNode choice : choiceMaps.keySet()) {
							GriddedGeoDataSet choiceMean;
							GriddedGeoDataSet choiceMeanWithout = null;
							if (meanIsFromCurves) {
								// load choice mean externally as well
								String key = LogicTreeCurveAverager.choicePrefix(level, choice);
								key = MPJ_LogicTreeHazardCalc.LEVEL_CHOICE_MAPS_ENTRY_PREFIX+key;
								choiceMean = loadPrecomputedMeanMap(key, rp, period);
								Preconditions.checkNotNull(choiceMean,
										"Mean map was precomputed for curves, but levels were not? "
										+ "Couldn't find mean map for %s, %s, %s", key, rp, period);
								
								// now without
								if (choiceMeanWithouts != null) {
									key = LogicTreeCurveAverager.choiceWithoutPrefix(level, choice);
									key = MPJ_LogicTreeHazardCalc.LEVEL_CHOICE_MAPS_ENTRY_PREFIX+key;
									choiceMeanWithout = loadPrecomputedMeanMap(key, rp, period);
									if (choiceMeanWithout == null)
										// could be an old version that doesn't have it, skip and stop trying
										choiceMeanWithouts = null;
								}
							} else {
								choiceMean = buildMean(choiceMaps.get(choice), choiceWeights.get(choice));
								
								// build without
								int numOthers = branches.size() - choiceMaps.get(choice).size();
								List<GriddedGeoDataSet> mapsWithout = new ArrayList<>(numOthers);
								List<Double> weightsWithout = new ArrayList<>(numOthers);
								for (LogicTreeNode oChoice : choiceMaps.keySet()) {
									if (oChoice == choice)
										continue;
									mapsWithout.addAll(choiceMaps.get(oChoice));
									weightsWithout.addAll(choiceWeights.get(oChoice));
								}
								choiceMeanWithout = buildMean(mapsWithout, weightsWithout);
							}
							choiceMeans.put(choice, choiceMean);
							if (choiceMeanWithouts != null)
								choiceMeanWithouts.put(choice, choiceMeanWithout);
							// calculate percentile
							GriddedGeoDataSet percentile = calcPercentileWithinDist(mapNCDFs, choiceMean);
							choiceMeanPercentiles.put(choice, percentile);
						}
						choiceMeansList.add(choiceMeans);
						choiceMeanWithoutsList.add(choiceMeanWithouts);
						choiceMeanPercentilesList.add(choiceMeanPercentiles);
					} else {
						choiceMapsList.add(null);
						choiceWeightsList.add(null);
						choiceMeansList.add(null);
						choiceMeanWithoutsList.add(null);
						choiceMeanPercentilesList.add(null);
					}
				}
				
				mapNCDFs = null;
				System.gc();
				
				if (period == periods[0] && rp == rps[0]) {
					PlotUtils.writeScaleLegendOnly(resourcesDir, "cpt_branch_pDiff",
							GeographicMapMaker.buildCPTLegend(pDiffCPT, "Branch Choice / Mean, % Change"), cptWidth, true, true);
					PlotUtils.writeScaleLegendOnly(resourcesDir, "cpt_branch_diff",
							GeographicMapMaker.buildCPTLegend(diffCPT, "Branch Choice - Mean "+perUnits), cptWidth, true, true);
				}
				
				CSVFile<String> choiceMeanSummaryCSV = new CSVFile<>(false);
				CSVFile<String> choiceMeanAbsSummaryCSV = new CSVFile<>(false);
				
				boolean levelNameOverlap = false;
				List<String> levelPrefixes = new ArrayList<>();
				for (LogicTreeLevel<?> level : tree.getLevels()) {
					String ltPrefix = level.getFilePrefix();
					levelNameOverlap |= levelPrefixes.contains(ltPrefix);
					levelPrefixes.add(ltPrefix);
				}
				if (levelNameOverlap)
					for (int i=0; i<levelPrefixes.size(); i++)
						levelPrefixes.set(i, i+"_"+levelPrefixes.get(i));
				
				for (int l=0; l<choiceMapsList.size(); l++) {
					LogicTreeLevel<?> level = tree.getLevels().get(l);
					String levelPrefix = prefix+"_"+levelPrefixes.get(l);
					HashMap<LogicTreeNode, List<GriddedGeoDataSet>> choiceMaps = choiceMapsList.get(l);
					if (choiceMaps != null) {
						System.out.println(level.getName()+" has "+choiceMaps.size()+" choices");
						lines.add("#### "+level.getName()+", "+unitlessLabel);
						lines.add(topLink); lines.add("");
						lines.add("This section shows how mean hazard varies accross "+choiceMaps.size()+" choices at the "
								+ "_"+level.getName()+"_ branch level.");
						lines.add("");
						
						HashMap<LogicTreeNode, GriddedGeoDataSet> choiceMeans = choiceMeansList.get(l);
						HashMap<LogicTreeNode, GriddedGeoDataSet> choiceMeanPercentiles = choiceMeanPercentilesList.get(l);
						
						table = MarkdownUtils.tableBuilder();
						table.initNewLine().addColumn("**Choice**").addColumn("**Vs Mean**");
						TableBuilder mapVsChoiceTable = MarkdownUtils.tableBuilder();
						mapVsChoiceTable.initNewLine().addColumn("**Choice**");
						List<LogicTreeNode> choices = new ArrayList<>();
						for (LogicTreeNode choice : level.getNodes())
							if (choiceMaps.containsKey(choice))
								choices.add(choice);
						Preconditions.checkState(choices.size() == choiceMaps.size());
//						List<LogicTreeNode> choices = new ArrayList<>(choiceMaps.keySet());
//						Collections.sort(choices, nodeNameCompare);
						
						if (choiceMeanSummaryCSV.getNumRows() > 0) {
							choiceMeanSummaryCSV.addLine("");
							choiceMeanAbsSummaryCSV.addLine("");
						}
						int summaryRow = choiceMeanSummaryCSV.getNumRows();
						choiceMeanSummaryCSV.addLine(level.getName());
						choiceMeanAbsSummaryCSV.addLine(level.getName(), "Overall average absolute difference:", "");
						List<String> csvHeader = new ArrayList<>();
						csvHeader.add("Choice");
						csvHeader.add("Vs Mean");
						for (LogicTreeNode choice : choices) {
							csvHeader.add("Vs "+choice.getShortName());
							table.addColumn("**Vs "+choice.getShortName()+"**");
							mapVsChoiceTable.addColumn("**Vs "+choice.getShortName()+"**");
						}
						choiceMeanSummaryCSV.addLine(csvHeader);
						choiceMeanAbsSummaryCSV.addLine(csvHeader);
						table.finalizeLine();
						mapVsChoiceTable.finalizeLine();
						
						TableBuilder mapTable = MarkdownUtils.tableBuilder();
						mapTable.addLine("", "Choice Mean vs Full Mean, % Change", "Choice Mean - Full Mean",
								"Choice Percentile in Full Dist", "Choice Percentile in Dist Without");
						
						MinMaxAveTracker runningDiffAvg = new MinMaxAveTracker();
						MinMaxAveTracker runningAbsDiffAvg = new MinMaxAveTracker();
						
						branchLevels.add(level);
						branchLevelValues.add(choices);
						List<MapPlot> branchPDiffPlots = new ArrayList<>();
						List<MapPlot> branchDiffPlots = new ArrayList<>();
						branchLevelPDiffPlots.add(branchPDiffPlots);
						branchLevelDiffPlots.add(branchDiffPlots);
						
						// build norm CDFs for each sub-choice
						System.out.println("Building choice CDFs for "+level.getName());
						LightFixedXFunc[][] choiceCDFs = new LightFixedXFunc[choices.size()][];
						double[] choiceSumWeights = new double[choices.size()];
						for (int c=0; c<choices.size(); c++) {
							List<GriddedGeoDataSet> mapsWith = new ArrayList<>();
							List<Double> weightsWith = new ArrayList<>();
							LogicTreeNode choice = choices.get(c);
							for (int i=0; i<branches.size(); i++) {
								LogicTreeBranch<?> branch = branches.get(i);
								if (branch.hasValue(choice)) {
									mapsWith.add(maps[i]);
									double weight = weights.get(i);
									weightsWith.add(weight);
									choiceSumWeights[c] += weight;
								}
							}
							System.out.println("\tBuilding "+choice.getShortName()+" with "+mapsWith.size()
								+" maps, sumWeight="+(float)choiceSumWeights[c]);
							choiceCDFs[c] = buildNormCDFs(mapsWith, weightsWith);
						}
						
						List<GriddedGeoDataSet> choicePDiffs = new ArrayList<>();
						List<GriddedGeoDataSet> choiceDiffs = new ArrayList<>();
						List<String> choiceShortNames = new ArrayList<>();
						
						for (int c=0; c<choices.size(); c++) {
							LogicTreeNode choice = choices.get(c);
							table.initNewLine().addColumn("**"+choice.getShortName()+"**");
							mapVsChoiceTable.initNewLine().addColumn("**"+choice.getShortName()+"**");
							
							List<String> meanCSVLine = new ArrayList<>(csvHeader.size());
							List<String> meanAbsCSVLine = new ArrayList<>(csvHeader.size());
							meanCSVLine.add(choice.getShortName());
							meanAbsCSVLine.add(choice.getShortName());
							
							GriddedGeoDataSet choiceMap = choiceMeans.get(choice);
							table.addColumn(mapPDiffStr(choiceMap, mean, null, null, meanCSVLine, meanAbsCSVLine));
							
							for (LogicTreeNode oChoice : choices) {
								if (choice == oChoice) {
									table.addColumn("");
									mapVsChoiceTable.addColumn("");
									meanCSVLine.add("");
									meanAbsCSVLine.add("");
								} else {
									table.addColumn(mapPDiffStr(choiceMap, choiceMeans.get(oChoice),
											runningDiffAvg, runningAbsDiffAvg, meanCSVLine, meanAbsCSVLine));
									// plot choice vs choice map
									GriddedGeoDataSet oChoiceMap = choiceMeans.get(oChoice);
									GriddedGeoDataSet pDiff = buildPDiff(oChoiceMap, choiceMap);
									File map = submitMapFuture(mapper, exec, futures, resourcesDir, levelPrefix+"_"+choice.getFilePrefix()+"_vs_"+oChoice.getFilePrefix(),
											pDiff, pDiffCPT, TITLES ? choice.getShortName()+" vs "+oChoice.getShortName() : " ",
											choice.getShortName()+" / "+oChoice.getShortName()+", % Change, "+unitlessLabel, true);
									mapVsChoiceTable.addColumn("![Difference Map]("+resourcesDir.getName()+"/"+map.getName()+")");
								}
							}
							
							table.finalizeLine();
							mapVsChoiceTable.finalizeLine();
							choiceMeanSummaryCSV.addLine(meanCSVLine);
							choiceMeanAbsSummaryCSV.addLine(meanAbsCSVLine);
							
							// now maps
							
							mapTable.initNewLine().addColumn("**"+choice.getShortName()+"**");
							
							// pDiff
							choiceShortNames.add(choice.getShortName());
							GriddedGeoDataSet pDiff = buildPDiff(mean, choiceMap);
							choicePDiffs.add(pDiff);
							MapPlot pDiffMap = mapper.buildMapPlot(resourcesDir, levelPrefix+"_"+choice.getFilePrefix()+"_pDiff",
									pDiff, pDiffCPT, TITLES ? choice.getShortName()+" Comparison" : " ",
									choice.getShortName()+" / Mean, % Change, "+unitlessLabel, true);
							branchPDiffPlots.add(pDiffMap);
							File map = new File(resourcesDir, pDiffMap.prefix+".png");
							mapTable.addColumn("![Percent Difference Map]("+resourcesDir.getName()+"/"+map.getName()+")");
							
							// regular diff
							GriddedGeoDataSet diff = new GriddedGeoDataSet(region, false);
							choiceDiffs.add(diff);
							for (int i=0; i<diff.size(); i++)
								diff.set(i, choiceMap.get(i) - mean.get(i));
							MapPlot diffMap = mapper.buildMapPlot(resourcesDir, levelPrefix+"_"+choice.getFilePrefix()+"_diff",
									diff, diffCPT, TITLES ? choice.getShortName()+" Comparison" : " ",
									choice.getShortName()+" - Mean, "+label, false);
							branchDiffPlots.add(diffMap);
							map = new File(resourcesDir, diffMap.prefix+".png");
							mapTable.addColumn("![Difference Map]("+resourcesDir.getName()+"/"+map.getName()+")");
							
							// percentile
							GriddedGeoDataSet percentile = choiceMeanPercentiles.get(choice);
							map = submitMapFuture(mapper, exec, futures, resourcesDir, levelPrefix+"_"+choice.getFilePrefix()+"_percentile",
									percentile, percentileCPT, TITLES ? choice.getShortName()+" Comparison" : " ",
									choice.getShortName()+" %-ile, "+unitlessLabel);
							mapTable.addColumn("![Percentile Map]("+resourcesDir.getName()+"/"+map.getName()+")");
							
							// percentile without
							// percentile without
//							List<GriddedGeoDataSet> mapsWithout = new ArrayList<>();
//							List<Double> weightsWithout = new ArrayList<>();
//							for (int i=0; i<branches.size(); i++) {
//								LogicTreeBranch<?> branch = branches.get(i);
//								if (!branch.hasValue(choice)) {
//									mapsWithout.add(maps[i]);
//									weightsWithout.add(weights.get(i));
//								}
//							}
//							Preconditions.checkState(!mapsWithout.isEmpty());
//							LightFixedXFunc[] mapsWithoutNCDFs = buildNormCDFs(mapsWithout, weightsWithout);
							List<LightFixedXFunc[]> withoutNCDFs = new ArrayList<>(choices.size()-1);
							List<Double> withoutWeights = new ArrayList<>();
							for (int i=0; i<choices.size(); i++) {
								if (i != c) {
									withoutNCDFs.add(choiceCDFs[i]);
									withoutWeights.add(choiceSumWeights[i]);
								}
							}
//							LightFixedXFunc[] mapsWithoutNCDFs =
//									withoutNCDFs.size() == 1 ? withoutNCDFs.get(0) : mergeNormCDFs(withoutNCDFs);
							GriddedGeoDataSet percentileWithout = calcPercentileWithinDists(withoutNCDFs, withoutWeights, choiceMap);
							map = submitMapFuture(mapper, exec, futures, resourcesDir, levelPrefix+"_"+choice.getFilePrefix()+"_percentile_without",
									percentileWithout, percentileCPT, TITLES ? choice.getShortName()+" Comparison" : " ",
									choice.getShortName()+" %-ile, "+unitlessLabel);
							mapTable.addColumn("![Percentile Map]("+resourcesDir.getName()+"/"+map.getName()+")");
							
							mapTable.finalizeLine();
						}
						// write multi maps
						int height = 360;
//						int titleFont = 42;
						int titleFont = 12;
//						int subtitleFont = 40;
						int subtitleFont = 46;
						File pDiffMulti = submitMultiMapFuture(mapper, exec, futures, resourcesDir, levelPrefix+"_choice_pDiffs",
								choicePDiffs, pDiffCPT, null, titleFont, choiceShortNames, subtitleFont, null, true, height);
						File diffMulti = submitMultiMapFuture(mapper, exec, futures, resourcesDir, levelPrefix+"_choice_diffs",
								choiceDiffs, diffCPT, null, titleFont, choiceShortNames, subtitleFont, null, true, height);
						File choicesCSV = new File(resourcesDir, levelPrefix+".csv");
						writeChoiceHazardCSV(choicesCSV, mean, choices, choiceMeans);
						lines.add("![Combined "+level.getShortName()+" % difference plot]("+resourcesDir.getName()+"/"+pDiffMulti.getName()+")");
						lines.add("");
						lines.add("![% Diff CPT]("+resourcesDir.getName()+"/cpt_branch_pDiff.png)");
						lines.add("");
						lines.add("![Combined "+level.getShortName()+" difference plot]("+resourcesDir.getName()+"/"+diffMulti.getName()+")");
						lines.add("");
						lines.add("![Diff CPT]("+resourcesDir.getName()+"/cpt_branch_diff.png)");
						lines.add("");
						lines.add("Download Choice Hazard CSV: ["+choicesCSV.getName()+"]("+resourcesDir.getName()+"/"+choicesCSV.getName()+")");
						lines.add("");
						lines.add("The table below gives summary statistics for the spatial average difference and average "
								+ "absolute difference of hazard between mean hazard maps for each individual branch "
								+ "choices. In other words, it gives the expected difference (or absolute "
								+ "difference) between two models if you picked a location at random. Values are listed "
								+ "between each pair of branch choices, and also between that choice and the overall "
								+ "mean map in the first column.");
						lines.add("");
						lines.add("The overall average absolute difference between the map for any choice to each other "
								+ "choice, a decent summary measure of how much hazard varies due to this branch choice, "
								+ "is: **"+twoDigits.format(runningAbsDiffAvg.getAverage())+"%**");
						System.out.println("\tOverall MAD: "+twoDigits.format(runningAbsDiffAvg.getAverage())+" %");
						choiceMeanAbsSummaryCSV.set(summaryRow, 2, runningAbsDiffAvg.getAverage()+"%");
						lines.add("");
						lines.addAll(table.build());
						lines.add("");
						lines.add("The map table below shows how the mean map for each branch choice compares to the overall "
								+ "mean map, expressed as % change (first column) and difference (second column). The "
								+ "third column, 'Choice Percentile in Full Dist', shows at what percentile the map for "
								+ "that branch choice lies within the full distribution, and the fourth column, 'Choice "
								+ "Percentile in Dist Without', shows the same but for the distribution of all other "
								+ "branches (without this choice included).");
						lines.add("");
						lines.add("Note that these percentile comparisons can be outlier dominated, in which case even "
								+ "if a choice is near the overall mean hazard it may still lie far from the 50th "
								+ "percentile (see 'Mean Map Percentile' above to better understand outlier dominated "
								+ "regions).");
						lines.add("");
						lines.addAll(mapTable.build());
						lines.add("");
						lines.add("The table below gives % change maps between each option, head-to-head.");
						lines.add("");
						lines.addAll(mapVsChoiceTable.build());
						lines.add("");
						
						// now value of removal
						HashMap<LogicTreeNode, GriddedGeoDataSet> choiceMeanWithouts = choiceMeanWithoutsList.get(l);
						if (choiceMeanWithouts != null) {
							table = MarkdownUtils.tableBuilder();
							
							List<File> ratioPlots = new ArrayList<>();
							List<File> diffPlots = new ArrayList<>();
							table.initNewLine();
							for (LogicTreeNode choice : choices) {
								table.addColumn(choice.getShortName());
								
								GriddedGeoDataSet choiceWithout = choiceMeanWithouts.get(choice);
								
								GriddedGeoDataSet pDiff = buildPDiff(mean, choiceWithout);
								ratioPlots.add(submitMapFuture(mapper, exec, futures, resourcesDir,
										levelPrefix+"_"+choice.getFilePrefix()+"_mean_pDiff_without",
										pDiff, pDiffCPT, TITLES ? choice.getShortName()+" Removal Comparison" : " ",
										choice.getShortName()+", Mean Without / With, % Change, "+unitlessLabel, true));
								
								GriddedGeoDataSet diff = new GriddedGeoDataSet(region, false);
								for (int i=0; i<diff.size(); i++)
									diff.set(i, choiceWithout.get(i) - mean.get(i));
								diffPlots.add(submitMapFuture(mapper, exec, futures, resourcesDir,
										levelPrefix+"_"+choice.getFilePrefix()+"_mean_diff_without",
										diff, diffCPT, TITLES ? choice.getShortName()+" Removal Comparison" : " ",
										choice.getShortName()+", Mean Without - With, "+label, false));
							}
							table.finalizeLine();
							
							table.initNewLine();
							for (File ratioPlot : ratioPlots)
								table.addColumn("![Percent Difference Map]("+resourcesDir.getName()+"/"+ratioPlot.getName()+")");
							table.finalizeLine();
							
							table.initNewLine();
							for (File diffPlot : diffPlots)
								table.addColumn("![Difference Map]("+resourcesDir.getName()+"/"+diffPlot.getName()+")");
							table.finalizeLine();
							
							lines.add("The table below shows how much the mean hazard map would change if each branch "
									+ "were eliminated. This differs from the above comparisons in that it also "
									+ "reflects the weight assigned to each branch. The sign is now flipped such "
									+ "that blue and green areas indicate areas where hazard is higher due to inclusion "
									+ "of the listed listed choice, and would go down were that choice eliminated.");
							lines.add("");
							lines.addAll(table.build());
							lines.add("");
						}
					}
				}
				
				List<String> addInLines = new ArrayList<>();
				
				if (!branchLevels.isEmpty()) {
					String combPrefix = prefix+"_branches_combined";
					if (branchLevels.size() < 5)
						writeCombinedBranchMap(resourcesDir, combPrefix, name+", Mean Hazard Map",
								meanMapPlot, branchLevels, branchLevelValues,
								branchLevelPDiffPlots, "Branch Choice / Mean, % Change",
								branchLevelDiffPlots, "Branch Choice - Mean (g)");
					combPrefix = prefix+"_branches_combined_pDiff";
					writeCombinedBranchMap(resourcesDir, combPrefix, name+", Mean Hazard Map",
							meanMapPlot, branchLevels, branchLevelValues, branchLevelPDiffPlots,
							"Branch Choice / Mean, % Change");
					table = MarkdownUtils.tableBuilder();
					table.addLine("Combined Summary Maps");
					table.addLine("![Combined Map]("+resourcesDir.getName()+"/"+combPrefix+".png)");
					combPrefix = prefix+"_branches_combined_diff";
					writeCombinedBranchMap(resourcesDir, combPrefix, name+", Mean Hazard Map",
							meanMapPlot, branchLevels, branchLevelValues, branchLevelDiffPlots,
							"Branch Choice - Mean (g)");
					table.addLine("![Combined Map]("+resourcesDir.getName()+"/"+combPrefix+".png)");
					addInLines.addAll(table.build());
					
					choiceMeanSummaryCSV.writeToFile(new File(resourcesDir, prefix+"_branch_mean_summary.csv"));
					choiceMeanAbsSummaryCSV.writeToFile(new File(resourcesDir, prefix+"_branch_mean_abs_summary.csv"));
				}
				
				if (canDecomposeVariance) {
					List<String> varLines = varDecomposer.buildLines(varResults);
					if (varLines != null && !varLines.isEmpty()) {
						if (!addInLines.isEmpty())
							addInLines.add("");
						addInLines.add("#### "+varDecomposer.getHeading()+", "+unitlessLabel);
						addInLines.add(topLink); addInLines.add("");
						addInLines.addAll(varLines);
					} else {
						canDecomposeVariance = false;
					}
				}
				
				if (!addInLines.isEmpty())
					lines.addAll(combinedMapIndex, addInLines);
				
				if (intermediateWrite)
					writeIntermediate(outputDir, lines, tocIndex);
			}
		}
		
		System.out.println("Waiting on "+futures.size()+" map futures....");
		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (InterruptedException | ExecutionException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		System.out.println("DONE with maps");
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 4));
		lines.add(tocIndex, "## Table Of Contents");
		
		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	private String mapStats(GriddedGeoDataSet map) {
		return mapStats(map, false);
	}
	
	private String mapStats(GriddedGeoDataSet map, boolean percent) {
		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;
		double sum = 0d;
		boolean hasNeg = false;
		double sumAbs = 0d;
		int count = 0;
		for (int i=0; i<map.size(); i++) {
			if (mapRegion != null && !mapRegion.contains(map.getLocation(i)))
				continue;
			double val = map.get(i);
			if (Double.isFinite(val)) {
				min = Double.min(min, val);
				max = Double.max(max, val);
				sum += val;
				hasNeg |= val < 0;
				sumAbs += Math.abs(val);
				count++;
			}
		}
		String ret;
		if (count == 0) {
			ret = "All values non-finite";
		} else {
			ret = "mean: "+String.format("%.3g", sum/(double)count);
			if (percent)
				ret += "%";
			if (hasNeg) {
				ret += ", abs: "+String.format("%.3g", sumAbs/(double)count);
				if (percent)
					ret += "%";
			}
			ret += ", range: [";
			if (percent)
				ret += String.format("%.3g", min)+"%, "+String.format("%.3g", max)+"%]";
			else
				ret += String.format("%.3g", min)+", "+String.format("%.3g", max)+"]";
		}
		return "<small>"+ret+"</small>";
	}
	
	private static class AvgMaxCalc {
		private double sum;
		private double max = Double.NEGATIVE_INFINITY;
		private int numUsed;
		private int numSkipped;
		
		public void addValue(double value) {
			if (Double.isFinite(value)) {
				sum += value;
				max = Math.max(value, max);
				numUsed++;
			} else {
				numSkipped++;
			}
		}
		
		public double getAverage() {
			return sum/numUsed;
		}
		
		public double getMax() {
			return max;
		}
	}
	
	private static void writeIntermediate(File outputDir, List<String> lines, int tocIndex) throws IOException {
		System.out.println("Writing intermediate markdown");
		
		lines = new ArrayList<>(lines);
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 4));
		lines.add(tocIndex, "## Table Of Contents");

		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	private static File submitMapFuture(SolHazardMapCalc mapper, ExecutorService exec, List<Future<?>> futures,
			File outputDir, String prefix, GriddedGeoDataSet xyz, CPT cpt,
			String title, String zLabel) {
		return submitMapFuture(mapper, exec, futures, outputDir, prefix, xyz, cpt, title, zLabel, false);
	}
	
	private static File submitMapFuture(SolHazardMapCalc mapper, ExecutorService exec, List<Future<?>> futures,
			File outputDir, String prefix, GriddedGeoDataSet xyz, CPT cpt,
			String title, String zLabel, boolean diffStats) {
		File ret = new File(outputDir, prefix+".png");
		
		futures.add(exec.submit(new Runnable() {
			
			@Override
			public void run() {
				try {
					mapper.plotMap(outputDir, prefix, xyz, cpt, title, zLabel, diffStats);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		}));
		
		return ret;
	}
	
	private static File submitMultiMapFuture(SolHazardMapCalc mapper, ExecutorService exec, List<Future<?>> futures,
			File outputDir, String prefix, List<GriddedGeoDataSet> xyzs, CPT cpt,
			String title, int titleFontSize, List<String> subtitles, int subtitleFontSize,
			String zLabel, boolean horizontal, int dimension) {
		File ret = new File(outputDir, prefix+".png");
		
		futures.add(exec.submit(new Runnable() {
			
			@Override
			public void run() {
				try {
					mapper.plotMultiMap(outputDir, prefix, xyzs, cpt, title, titleFontSize, subtitles, subtitleFontSize,
							zLabel, horizontal, dimension, false, false);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		}));
		
		return ret;
	}
	
	private void writeHazardCSV(File outputFile, GriddedGeoDataSet mean, GriddedGeoDataSet median,
			GriddedGeoDataSet min, GriddedGeoDataSet max, GriddedGeoDataSet cov, GriddedGeoDataSet meanPercentile,
			GriddedGeoDataSet meanPercentileInComparison, GriddedGeoDataSet medianPercentileInComparison) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		
		List<String> header = new ArrayList<>();
		header.addAll(List.of("Location Index", "Latitutde", "Longitude", "Weighted Mean", "Weighted Median", "Min", "Max", "COV"));
		if (meanPercentile != null) {
			if (meanPercentileInComparison == null)
				header.add("Mean Map Percentile");
			else
				header.add("Mean Map Percentile (within own distribution)");
		}
		if (meanPercentileInComparison != null)
			header.add("Mean Map Percentile (within comparison distribution)");
		if (medianPercentileInComparison != null)
			header.add("Median Map Percentile (within comparison distribution)");
		csv.addLine(header);
		
		GriddedRegion reg = mean.getRegion();
		for (int i=0; i<reg.getNodeCount(); i++) {
			Location loc = reg.getLocation(i);
			
			List<String> line = new ArrayList<>(header.size());
			line.add(i+"");
			line.add((float)loc.getLatitude()+"");
			line.add((float)loc.getLongitude()+"");
			line.add(mean.get(i)+"");
			line.add(median.get(i)+"");
			line.add(min.get(i)+"");
			line.add(max.get(i)+"");
			line.add(cov.get(i)+"");
			if (meanPercentile != null)
				line.add((float)meanPercentile.get(i)+"");
			if (meanPercentileInComparison != null)
				line.add((float)meanPercentileInComparison.get(i)+"");
			if (medianPercentileInComparison != null)
				line.add((float)medianPercentileInComparison.get(i)+"");
			csv.addLine(line);
		}
		
		csv.writeToFile(outputFile);
	}
	
	private void writeChoiceHazardCSV(File outputFile, GriddedGeoDataSet mean, List<LogicTreeNode> choices,
			Map<LogicTreeNode, GriddedGeoDataSet> choiceMeans) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		
		List<String> header = new ArrayList<>();
		header.add("Location Index");
		header.add("Latitutde");
		header.add("Longitude");
		header.add("Weighted Mean");
		for (LogicTreeNode choice : choices)
			header.add(choice.getShortName());
		csv.addLine(header);
		
		GriddedRegion reg = mean.getRegion();
		for (int i=0; i<reg.getNodeCount(); i++) {
			Location loc = reg.getLocation(i);
			
			List<String> line = new ArrayList<>();
			line.add(i+"");
			line.add((float)loc.getLatitude()+"");
			line.add((float)loc.getLongitude()+"");
			line.add(mean.get(i)+"");
			for (LogicTreeNode choice : choices)
				line.add(choiceMeans.get(choice).get(i)+"");
			
			csv.addLine(line);
		}
		
		csv.writeToFile(outputFile);
	}
	
	private static String mapPDiffStr(GriddedGeoDataSet map, GriddedGeoDataSet ref,
			MinMaxAveTracker runningDiffAvg, MinMaxAveTracker runningAbsDiffAvg,
			List<String> meanCSVLine, List<String> meanAbsCSVLine) {
		double mean = 0d;
		double meanAbs = 0d;
		int numFinite = 0;;
		for (int i=0; i<map.size(); i++) {
			double z1 = map.get(i);
			double z2 = ref.get(i);
			double pDiff = 100d*(z1-z2)/z2;
			if (z1 == z2 && z1 > 0)
				pDiff = 0d;
			if (Double.isFinite(pDiff)) {
				mean += pDiff;
				meanAbs += Math.abs(pDiff);
				if (runningDiffAvg != null)
					runningDiffAvg.addValue(pDiff);
				if (runningAbsDiffAvg != null)
					runningAbsDiffAvg.addValue(Math.abs(pDiff));
				numFinite++;
			}
		}
		mean /= (double)numFinite;
		meanAbs /= (double)numFinite;
		
		meanCSVLine.add(mean+"%");
		meanAbsCSVLine.add(meanAbs+"%");
		
		return "Mean: "+twoDigits.format(mean)+"%, Mean Abs: "+twoDigits.format(meanAbs)+"%";
	}

	static final DecimalFormat threeDigits = new DecimalFormat("0.000");
	static final DecimalFormat twoDigits = new DecimalFormat("0.00");
	static final DecimalFormat pDF = new DecimalFormat("0.0%");
	
	private static final Comparator<LogicTreeNode> nodeNameCompare = new Comparator<LogicTreeNode>() {

		@Override
		public int compare(LogicTreeNode o1, LogicTreeNode o2) {
			return o1.getShortName().compareTo(o2.getShortName());
		}
	};
	
	private static GriddedGeoDataSet log10(GriddedGeoDataSet map) {
		map = map.copy();
		map.log10();
		return map;
	}
	
	private void addDiffInclExtremesLines(GriddedGeoDataSet primary, String name, GriddedGeoDataSet min,
			GriddedGeoDataSet max, GriddedGeoDataSet comparison, String compName, String prefix, File resourcesDir,
			TableBuilder table, String type, String label, boolean difference, GriddedRegion compReg) throws IOException {
		
		String diffLabel, diffPrefix;
		CPT cpt;
		GriddedGeoDataSet diff, diffFromRange;
		boolean minEqualMax = true;
		for (int i=0; minEqualMax && i<min.size(); i++) {
			double minVal = min.get(i);
			double maxVal = max.get(i);
			if (Double.isFinite(minVal) && Double.isFinite(maxVal))
				minEqualMax = (float)minVal == (float)maxVal;
		}
		if (difference) {
			diffLabel = "Difference";
			diffPrefix = "diff";
			cpt = diffCPT;
			// regular diff
			diff = new GriddedGeoDataSet(primary.getRegion(), false);
			diffFromRange = new GriddedGeoDataSet(primary.getRegion(), false);
			for (int i=0; i<diff.size(); i++) {
				double val = primary.get(i);
				double compVal = comparison.get(i);
				diff.set(i, val - compVal);
				double minVal = min.get(i);
				double maxVal = max.get(i);
				double rangeDiff;
				if (compVal >= minVal && compVal <= maxVal)
					rangeDiff = 0d;
				else if (compVal < minVal)
					rangeDiff = compVal-minVal;
				else
					rangeDiff = compVal-maxVal;
				diffFromRange.set(i, rangeDiff);
			}
		} else {
			diffLabel = "% Change";
			diffPrefix = "pDiff";
			cpt = pDiffCPT;
			diff = buildPDiff(comparison, primary);
			diffFromRange = buildPDiffFromRange(min, max, comparison);
		}
		table.addLine(MarkdownUtils.boldCentered(type+" "+diffLabel),
				MarkdownUtils.boldCentered("Comparison "+type+" "+diffLabel+" From Extremes"));
		
		// now see if we should shrink the region for plotting
		int compRegCount = compReg.getNodeCount();
		if (compRegCount < primary.size()) {
			GriddedGeoDataSet remappedDiff = new GriddedGeoDataSet(compReg, false);
			if (checkShrinkToComparison(diff, remappedDiff)) {
				// the comparison was for a subset of this region, shrink down the plot to the common area
				GriddedGeoDataSet remappedRangeDiff = new GriddedGeoDataSet(compReg, false);
				Preconditions.checkState(checkShrinkToComparison(diffFromRange, remappedRangeDiff));
				diff = remappedDiff;
				diffFromRange = remappedRangeDiff;
			}
		}
		
		table.initNewLine();
		
		String op = difference ? "-" : "/";
		
		File map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_"+diffPrefix, diff, cpt, TITLES ? name+" vs "+compName : " ",
				(TITLES ? "Primary "+op+" Comparison, " : "")+diffLabel+", "+label, !difference);
		table.addColumn("![Difference Map]("+resourcesDir.getName()+"/"+map.getName()+")");
		if (minEqualMax) {
			table.addColumn("_(N/A)_");
		} else {
			map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_"+diffPrefix+"_range", diffFromRange, cpt.reverse(), TITLES ? name+" vs "+compName : " ",
					"Comparison "+op+" Extremes, "+diffLabel+", "+label, !difference);
			table.addColumn("![Range Difference Map]("+resourcesDir.getName()+"/"+map.getName()+")");
		}
		table.finalizeLine();
		table.initNewLine();
		table.addColumn(mapStats(diff, !difference));
		table.addColumn(mapStats(diffFromRange, !difference));
		table.finalizeLine();
	}
	
	private void addSingleBranchDiffLines(GriddedGeoDataSet primary, String name, GriddedGeoDataSet comparison,
			String compName, String prefix, File resourcesDir, TableBuilder table, String unitlessLlabel,
			String label, GriddedRegion compReg) throws IOException {
		
		GriddedGeoDataSet pDiff = buildPDiff(comparison, primary);
		GriddedGeoDataSet diff = new GriddedGeoDataSet(primary.getRegion(), false);
		for (int i=0; i<diff.size(); i++) {
			double val = primary.get(i);
			double compVal = comparison.get(i);
			diff.set(i, val - compVal);
		}
		table.addLine(MarkdownUtils.boldCentered("% Change"), MarkdownUtils.boldCentered("Difference"));
		
		// now see if we should shrink the region for plotting
		int compRegCount = compReg.getNodeCount();
		if (compRegCount < primary.size()) {
			GriddedGeoDataSet remappedDiff = new GriddedGeoDataSet(compReg, false);
			if (checkShrinkToComparison(diff, remappedDiff)) {
				// the comparison was for a subset of this region, shrink down the plot to the common area
				GriddedGeoDataSet remappedPDiff = new GriddedGeoDataSet(compReg, false);
				Preconditions.checkState(checkShrinkToComparison(pDiff, remappedPDiff));
				diff = remappedDiff;
				pDiff = remappedPDiff;
			}
		}
		
		table.initNewLine();
		
		File pDiffMap = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_pDiff", pDiff, pDiffCPT,
				TITLES ? name+" vs "+compName : " ", (TITLES ? "Primary / Comparison, " : "")+"% Change, "+unitlessLlabel, true);
		table.addColumn("![% Difference Map]("+resourcesDir.getName()+"/"+pDiffMap.getName()+")");
		File diffMap = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp_diff", diff, diffCPT,
				TITLES ? name+" vs "+compName : " ", (TITLES ? "Primary - Comparison, " : "")+"Difference, "+label, false);
		table.addColumn("![Difference Map]("+resourcesDir.getName()+"/"+diffMap.getName()+")");
		table.finalizeLine();
		table.initNewLine();
		table.addColumn(mapStats(pDiff, true));
		table.addColumn(mapStats(diff, false));
		table.finalizeLine();
	}
	
	private boolean checkShrinkToComparison(GriddedGeoDataSet orig, GriddedGeoDataSet remapped) {
		GriddedRegion origReg = orig.getRegion();
		GriddedRegion compReg = remapped.getRegion();
		int compRegCount = compReg.getNodeCount();
		if (compRegCount >= origReg.getNodeCount())
			return false;
		for (int i=0; i<compRegCount; i++) {
			int index = origReg.indexForLocation(compReg.getLocation(i));
			if (index >= 0) {
				remapped.set(i, orig.get(index));
			} else {
				return false;
			}
		}
		return true;
	}
	
	private void addMapCompDiffLines(GriddedGeoDataSet primary, String name, GriddedGeoDataSet comparison,
			String compName, String prefix, File resourcesDir, TableBuilder table, String type, String label,
			String unitlessLabel, CPT cpt, CPT diffCPT, CPT pDiffCPT, GriddedRegion compReg) throws IOException {
		
		table.addLine(MarkdownUtils.boldCentered("Primary "+type), MarkdownUtils.boldCentered("Comparison "+type),
				MarkdownUtils.boldCentered("Difference"), MarkdownUtils.boldCentered("% Change"));
		
		table.initNewLine();
		File map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix,
				primary, cpt, name, label);
		table.addColumn("!["+type+"]("+resourcesDir.getName()+"/"+map.getName()+")");
		map = submitMapFuture(mapper, exec, futures, resourcesDir, prefix+"_comp",
				comparison, cpt, compName, label);
		table.addColumn("!["+type+"]("+resourcesDir.getName()+"/"+map.getName()+")");
		
		GriddedGeoDataSet diffForStats = null;
		GriddedGeoDataSet pDiffForStats = null;
		
		for (boolean difference : new boolean[] {true, false}) {
			GriddedGeoDataSet diff;
			String diffLabel;
			String diffPrefix;
			CPT myDiffCPT;
			if (difference) {
				diff = new GriddedGeoDataSet(primary.getRegion(), false);
				for (int i=0; i<diff.size(); i++)
					diff.set(i, primary.get(i)-comparison.get(i));
				diffLabel = "Primary - Comparison, "+label;
				diffPrefix = prefix+"_comp_diff";
				myDiffCPT = diffCPT;
				diffForStats = diff;
			} else {
				diff = buildPDiff(comparison, primary);
				diffLabel = "Primary / Comparison, % Change, "+unitlessLabel;
				diffPrefix = prefix+"_comp_pDiff";
				myDiffCPT = pDiffCPT;
				pDiffForStats = diff;
			}
			
			if (compReg != null && compReg.getNodeCount() < primary.getRegion().getNodeCount()) {
				// see if we should shrink it to comparison
				GriddedGeoDataSet remappedDiff = new GriddedGeoDataSet(compReg, false);
				if (checkShrinkToComparison(diff, remappedDiff))
					diff = remappedDiff;
			}
			
			map = submitMapFuture(mapper, exec, futures, resourcesDir, diffPrefix, diff, myDiffCPT,
					name+" vs "+compName, diffLabel, !difference);
			table.addColumn("![Difference Map]("+resourcesDir.getName()+"/"+map.getName()+")");
		}
		
		table.finalizeLine();
		table.addLine(mapStats(primary), mapStats(comparison), mapStats(diffForStats), mapStats(pDiffForStats, true));
	}
	
	public void writeCombinedBranchMap(File resourcesDir, String prefix, String fullTitle, MapPlot meanMap,
			List<LogicTreeLevel<?>> branchLevels, List<List<LogicTreeNode>> branchLevelValues,
			List<List<MapPlot>> branchLevelPlots, String label) throws IOException {
		writeCombinedBranchMap(resourcesDir, prefix, fullTitle, meanMap, branchLevels,
				branchLevelValues, branchLevelPlots, label, null, null);
	}
	
	private static boolean CENTER_SUBPLOTS = true;
	private static boolean INCLUDE_SUBPLOT_WEIGHT_LABELS = true;
	
	public void writeCombinedBranchMap(File resourcesDir, String prefix, String fullTitle, MapPlot meanMap,
			List<LogicTreeLevel<?>> branchLevels, List<List<LogicTreeNode>> branchLevelValues,
			List<List<MapPlot>> branchLevelPlots1, String label1, List<List<MapPlot>> branchLevelPlots2, String label2)
					throws IOException {
		// constants
		int primaryWidth = 1200;
		int primaryWidthDelta = 5;
		int secondaryWidth = primaryWidth/primaryWidthDelta;
		int padding = 10;
		int heightLevelName = 40;
		int heightChoice = 25;
		
		double secondaryScale = (double)secondaryWidth/(double)primaryWidth;
		
		int maxNumChoices = 0;
		for (List<MapPlot> plots : branchLevelPlots1)
			maxNumChoices = Integer.max(maxNumChoices, plots.size());
		if (branchLevelPlots2 != null)
			Preconditions.checkState(branchLevelPlots2.size() == branchLevelPlots1.size());
		Preconditions.checkState(maxNumChoices > 0);
		
		PlotPreferences primaryPrefs = PlotUtils.getDefaultFigurePrefs();
		primaryPrefs.scaleFontSizes(1.25d);
		PlotPreferences secondaryPrefs = primaryPrefs.clone();
		secondaryPrefs.scaleFontSizes(secondaryScale);
		
		Font fontLevelName = new Font(Font.SANS_SERIF, Font.BOLD, primaryPrefs.getPlotLabelFontSize());
		Font fontChoiceName = new Font(Font.SANS_SERIF, Font.BOLD, primaryPrefs.getLegendFontSize());
		Font fontWeight = new Font(Font.SANS_SERIF, Font.ITALIC, primaryPrefs.getLegendFontSize());
		
		// prepare primary figure and determine aspect ratio
		HeadlessGraphPanel primaryGP = new HeadlessGraphPanel(primaryPrefs);
		XYZPlotSpec primarySpec = meanMap.spec;
		primarySpec.setCPTTickUnit(0.5d);
		primarySpec.setTitle(fullTitle);
		CPT diffCPT = branchLevelPlots1.get(0).get(0).spec.getCPT();
		PlotPreferences prefs = primaryGP.getPlotPrefs();
		PaintScaleLegend diffSubtitle = GraphPanel.getLegendForCPT(diffCPT, label1,
				prefs.getAxisLabelFontSize(), prefs.getTickLabelFontSize(),
				-1, RectangleEdge.BOTTOM);
		if (branchLevelPlots2 != null) {
			CPT diffCPT2 = branchLevelPlots2.get(0).get(0).spec.getCPT();
			PaintScaleLegend diffSubtitle2 = GraphPanel.getLegendForCPT(diffCPT2, label2,
					prefs.getAxisLabelFontSize(), prefs.getTickLabelFontSize(),
					-1, RectangleEdge.BOTTOM);
			primarySpec.addSubtitle(diffSubtitle2);
		}
		primarySpec.addSubtitle(diffSubtitle);
		primaryGP.drawGraphPanel(primarySpec, false, false, meanMap.xRnage, meanMap.yRange);
		PlotUtils.setXTick(primaryGP, meanMap.xTick);
		PlotUtils.setYTick(primaryGP, meanMap.yTick);
		int primaryHeight = PlotUtils.calcHeight(primaryGP, primaryWidth, true);
		
		// figure out secondary height
		MapPlot secondaryPlot = branchLevelPlots1.get(0).get(0);
		HeadlessGraphPanel secondaryGP = drawSimplifiedSecondaryPlot(secondaryPlot, secondaryPrefs, 1d);
		int secondaryHeight = PlotUtils.calcHeight(secondaryGP, secondaryWidth, true);
		
		int secondaryHeightEach = heightLevelName + heightChoice + secondaryHeight;
		if (branchLevelPlots2 != null)
			secondaryHeightEach += secondaryHeight;
		
		int totalHeight = Integer.max(primaryHeight, padding*2 + branchLevels.size()*secondaryHeightEach);
		
		// TODO maybe revisit this
//		if (totalHeight > primaryHeight) {
//			// see if we can put some under the map
//			int heightMinusOne = totalHeight - secondaryHeightEach;
//			if (heightMinusOne < primaryHeight) {
//				// we can
//				// figure out which is the first one on the new row
//				int firstRowIndex = -1;
//				for (int i=1; i<branchLevelValues.size(); i++) {
//					int yStart = padding + secondaryHeightEach*i;
//					if (yStart > primaryHeight) {
//						firstRowIndex = i;
//						break;
//					}
//				}
//				Preconditions.checkState(firstRowIndex > 0);
//				int maxNumAfter = 0;
//				for (int i=firstRowIndex; i<branchLevelValues.size(); i+=2)
//					maxNumAfter = Integer.max(maxNumAfter, branchLevelValues.get(i).size());
//				System.out.println("Wrapping starting with "+firstRowIndex+", maxNumAfter="+maxNumAfter);
//				if (maxNumAfter > primaryWidthDelta) {
//					// need more room
//					primaryWidth = secondaryWidth*maxNumAfter;
//					primaryGP.drawGraphPanel(primarySpec, false, false, meanMap.xRnage, meanMap.yRange);
//					PlotUtils.setXTick(primaryGP, meanMap.xTick);
//					PlotUtils.setYTick(primaryGP, meanMap.yTick);
//					primaryHeight = PlotUtils.calcHeight(primaryGP, primaryWidth, true);
//					System.out.println("Mod primary size: "+primaryWidth+"x"+primaryHeight);
//				}
//				
//			}
//		}
		
		int totalWidth = padding*2 + primaryWidth + maxNumChoices*secondaryWidth;
		
		System.out.println("Building combined map with dimensions: primary="+primaryWidth+"x"+primaryHeight
				+", secondary="+secondaryWidth+"x"+secondaryHeight+", total="+totalWidth+"x"+totalHeight);
		
//		JPanel panel = new JPanel();
//		panel.setLayout(null);
		JLayeredPane panel = new JLayeredPane();
		panel.setOpaque(true);
//		panel.setLayout(new LayeredPaneLayout(panel, new Dimension(totalWidth, totalHeight)));
		panel.setBackground(Color.WHITE);
		panel.setSize(totalWidth, totalHeight);
		
		List<Consumer<Graphics2D>> redraws = new ArrayList<>();
		
		int plotLayer = JLayeredPane.DEFAULT_LAYER;
		int labelLayer = JLayeredPane.PALETTE_LAYER;
		
		// X/Y reference frame is top left
//		int primaryTopY = (totalHeight - primaryHeight)/2;
		int primaryTopY = 0; // place it on top
		redraws.add(placeGraph(panel, plotLayer, 0, primaryTopY, primaryWidth, primaryHeight, primaryGP.getChartPanel()));
		// clear the custom subtitle we added
		primarySpec.setSubtitles(null);
		
		int secondaryStartX = padding + primaryWidth;
		int levelLabelX = secondaryStartX;
		int levelLabelWidth = totalWidth - secondaryStartX - padding;
		int y = 0;
		for (int l=0; l<branchLevels.size(); l++) {
			// TODO big label
			// y is currently TOP, x is secondaryLabelX
			int levelLabelY = y; // top
			LogicTreeLevel<?> level = branchLevels.get(l);
			
			int choiceLabelY = y + heightLevelName;

			int choiceMapY = y + heightLevelName + heightChoice; // top
			int choiceMapY2 = y + heightLevelName + heightChoice + secondaryHeight; // top
			
			List<LogicTreeNode> myChoices = branchLevelValues.get(l);
			List<MapPlot> myPlots = branchLevelPlots1.get(l);
			int x = secondaryStartX;
			if (CENTER_SUBPLOTS && myChoices.size() < maxNumChoices)
				x += ((maxNumChoices-myChoices.size())*secondaryWidth)/2;
			
			for (int i=0; i<myChoices.size(); i++) {
				secondaryPlot = myPlots.get(i);
				secondaryGP = drawSimplifiedSecondaryPlot(secondaryPlot, secondaryPrefs, secondaryScale);
				
				redraws.add(placeGraph(panel, plotLayer, x, choiceMapY, secondaryWidth, secondaryHeight, secondaryGP.getChartPanel()));
				
				LogicTreeNode choice = branchLevelValues.get(l).get(i);
				placeLabel(panel, labelLayer, x, choiceLabelY, secondaryWidth, heightChoice, choice.getShortName(), fontChoiceName);
				
				if (INCLUDE_SUBPLOT_WEIGHT_LABELS) {
//					double choice = tr
					double totWeight = 0d;
					double matchWeight = 0d;
					for (int b=0; b<branches.size(); b++) {
						LogicTreeBranch<?> branch = branches.get(b);
						double weight = weights.get(b);
						if (branch.hasValue(choice))
							matchWeight += weight;
						totWeight += weight;
					}
					String label = " ("+new DecimalFormat("0.0#").format(matchWeight/totWeight)+")";
					int weightLabelY = choiceMapY + secondaryHeight - (3*heightChoice/2);
//					System.out.println("\tweight label: "+label+" for "+choice.getShortName()+"; pos="+x+","+weightLabelY+"; size="+secondaryWidth+"x"+heightChoice);
					JLabel jLabel = placeLabel(panel, labelLayer, x, weightLabelY, secondaryWidth, heightChoice, " "+label, fontWeight, JLabel.LEFT);
//					// need to redraw it on top of the chart at the end for PDFs(otherwise it will be covered)
					redraws.add(new Consumer<Graphics2D>() {
						@Override
						public void accept(Graphics2D t) {
//							jLabel.update(t);
							jLabel.paintImmediately(jLabel.getBounds());
						}
					});
				}
				
				x += secondaryWidth;
			}
			
			placeLabel(panel, labelLayer, levelLabelX, levelLabelY, levelLabelWidth, heightLevelName, level.getName(), fontLevelName);
			
			if (branchLevelPlots2 != null) {
				x = secondaryStartX;
				List<MapPlot> myPlots2 = branchLevelPlots2.get(l);
				for (int i=0; i<myChoices.size(); i++) {
					secondaryPlot = myPlots2.get(i);
					secondaryGP = drawSimplifiedSecondaryPlot(secondaryPlot, secondaryPrefs, secondaryScale);
					
					redraws.add(placeGraph(panel, plotLayer, x, choiceMapY2, secondaryWidth, secondaryHeight, secondaryGP.getChartPanel()));
					
					x += secondaryWidth;
				}
			}
			
			y += secondaryHeightEach;
		}
		
		// write scale labels
		// TODO
		
		// write PNG
		BufferedImage bi = new BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = bi.createGraphics();
		g2d.addRenderingHints(new RenderingHints(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON));
		panel.paint(bi.getGraphics());
		ImageIO.write(bi, "png", new File(resourcesDir, prefix+".png"));
		
		if (SolHazardMapCalc.PDFS) {
			// write PDF
			// step 1
			Document metadataDocument = new Document(new com.itextpdf.text.Rectangle(
					totalWidth, totalHeight));
			metadataDocument.addAuthor("OpenSHA");
			metadataDocument.addCreationDate();
//			HeaderFooter footer = new HeaderFooter(new Phrase("Powered by OpenSHA"), true);
//			metadataDocument.setFooter(footer);
			try {
				// step 2
				PdfWriter writer;

				writer = PdfWriter.getInstance(metadataDocument,
						new FileOutputStream(new File(resourcesDir, prefix+".pdf")));
				// step 3
				metadataDocument.open();
				// step 4
				PdfContentByte cb = writer.getDirectContent();
				PdfTemplate tp = cb.createTemplate(totalWidth, totalHeight);

				FontMapper fontMapper = new PDF_UTF8_FontMapper();
				g2d = new PdfGraphics2D(tp, totalWidth, totalHeight, fontMapper);
				
//				Graphics2D g2d = tp.createGraphics(width, height,
//						new DefaultFontMapper());
				panel.paint(g2d);
				// repaint all chart panels
				for (Consumer<Graphics2D> redraw : redraws)
					redraw.accept(g2d);
//				Rectangle2D r2d = new Rectangle2D.Double(0, 0, totalWidth, totalHeight);
//				panel.draw(g2d, r2d);
				g2d.dispose();
				cb.addTemplate(tp, 0, 0);
			}
			catch (DocumentException de) {
				de.printStackTrace();
			}
			// step 5
			metadataDocument.close();
		}
	}
	
	private HeadlessGraphPanel drawSimplifiedSecondaryPlot(MapPlot secondaryPlot, PlotPreferences secondaryPrefs,
			double lineScalar) {
		secondaryPrefs.setAxisLabelFontSize(2);
		HeadlessGraphPanel secondaryGP = new HeadlessGraphPanel(secondaryPrefs);
		XYZPlotSpec secondarySpec = secondaryPlot.spec;
		List<PlotCurveCharacterstics> origChars = null;
		if (lineScalar != 1d && secondarySpec.getChars() != null) {
			origChars = secondarySpec.getChars();
			List<PlotCurveCharacterstics> modChars = new ArrayList<>();
			for (PlotCurveCharacterstics pChar : origChars)
				modChars.add(new PlotCurveCharacterstics(pChar.getLineType(), (float)(pChar.getLineWidth()*lineScalar),
						pChar.getSymbol(), (float)(pChar.getSymbolWidth()*lineScalar), pChar.getColor()));
			secondarySpec.setChars(modChars);
		}
		secondarySpec.setCPTVisible(false);
		secondarySpec.setTitle(null);
		secondarySpec.setPlotAnnotations(null);
		secondaryGP.drawGraphPanel(secondarySpec, false, false, secondaryPlot.xRnage, secondaryPlot.yRange);
		secondaryGP.getXAxis().setTickLabelsVisible(false);
		secondaryGP.getXAxis().setLabel(" ");
		secondaryGP.getYAxis().setTickLabelsVisible(false);
		secondaryGP.getYAxis().setLabel(null);
		PlotUtils.setXTick(secondaryGP, secondaryPlot.xTick);
		PlotUtils.setYTick(secondaryGP, secondaryPlot.yTick);
		if (origChars != null)
			secondarySpec.setChars(origChars);
		return secondaryGP;
	}
	
	private Consumer<Graphics2D> placeGraph(JLayeredPane panel, int layer, int xTop, int yTop, int width, int height, ChartPanel chart) {
		chart.setBorder(null);
		// this forces it to actually render
		chart.getChart().createBufferedImage(width, height, new ChartRenderingInfo());
		chart.setSize(width, height);
		panel.setLayer(chart, layer);
		panel.add(chart);
//		panel.add(chart, layer);
		chart.setLocation(xTop, yTop);
		return new Consumer<Graphics2D>() {
			@Override
			public void accept(Graphics2D t) {
				chart.getChart().draw(t, new Rectangle2D.Double(xTop, yTop, width, height));
			}
		};
	}
	
	private JLabel placeLabel(JLayeredPane panel, int layer, int xTop, int yTop, int width, int height, String text, Font font) {
		return placeLabel(panel, layer, xTop, yTop, width, height, text, font, JLabel.CENTER);
	}
	
	private JLabel placeLabel(JLayeredPane panel, int layer, int xTop, int yTop, int width, int height, String text, Font font, int horizAlign) {
		JLabel label = new JLabel(text, horizAlign);
		label.setFont(font);
		label.setSize(width, height);
		panel.setLayer(label, layer);
		panel.add(label);
//		panel.add(label, layer);
		label.setLocation(xTop, yTop);
		return label;
	}
	
	public void close() throws IOException {
		zip.close();
		exec.shutdown();
	}
	
	private static class LayeredPaneLayout implements LayoutManager {

		private final Container target;
		private final Dimension preferredSize;

		public LayeredPaneLayout(final Container target, final Dimension preferredSize) {
			this.target = target;
			this.preferredSize = preferredSize;
		}

		@Override
		public void addLayoutComponent(final String name, final Component comp) {
		}

		@Override
		public void layoutContainer(final Container container) {
			for (final Component component : container.getComponents()) {
				component.setBounds(new Rectangle(0, 0, target.getWidth(), target.getHeight()));
			}
		}

		@Override
		public Dimension minimumLayoutSize(final Container parent) {
			return preferredLayoutSize(parent);
		}

		@Override
		public Dimension preferredLayoutSize(final Container parent) {
			return preferredSize;
		}

		@Override
		public void removeLayoutComponent(final Component comp) {
		}
	}

}
