package org.opensha.sha.earthquake.faultSysSolution.reports;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.zip.ZipFile;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.SectSlipRates;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata.RupSetOverlap;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.BiasiWesnouskyPlots;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.FaultSectionConnectionsPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.HazardMapPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.InfoStringPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.InversionConfigurationPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.InversionMisfitsPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.InversionProgressPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.JumpAzimuthsPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.JumpCountsOverDistancePlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.LogicTreeBranchPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.ModulesPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.NamedFaultPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.PaleoDataComparisonPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.ParticipationRatePlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.PlausibilityConfigurationReport;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.PlausibilityFilterPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RateDistributionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SectBValuePlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SectBySectDetailPlots;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SectMaxValuesPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SegmentationPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SlipRatePlots;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SolMFDPlot;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarCoulombPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.CumulativeProbPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.NucleationClusterEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.PathPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.path.ScalarCoulombPathEvaluator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.CoulombSectRatioProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RelativeCoulombProb;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.prob.RuptureProbabilityCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.InputJumpsOrDistClusterConnectionStrategy;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import scratch.UCERF3.enumTreeBranches.FaultModels;

public class ReportPageGen {
	
	private ReportMetadata meta;
	private List<AbstractRupSetPlot> plots;
	private File outputDir;
	private File indexDir;
	
	private double defaultMaxDist = DEFAULT_MAX_DIST;
	
	private File cacheDir;
	
	private List<PlausibilityFilter> altFilters = null;
	
	private boolean replot = false;
	
	public static PlotLevel PLOT_LEVEL_DEFAULT = PlotLevel.DEFAULT;
	
	public enum PlotLevel {
		LIGHT,
		DEFAULT,
		FULL
	}
	
	public static List<AbstractRupSetPlot> getDefaultRupSetPlots(PlotLevel level) {
		List<AbstractRupSetPlot> plots = new ArrayList<>();
		
		plots.add(new InfoStringPlot());
		plots.add(new LogicTreeBranchPlot());
		plots.add(new SolMFDPlot());
		plots.add(new PlausibilityConfigurationReport());
		if (level == PlotLevel.DEFAULT || level == PlotLevel.FULL) {
			plots.add(new RupHistogramPlots(RupHistogramPlots.RUP_SET_SCALARS));
			plots.add(new PlausibilityFilterPlot());
			plots.add(new ModulesPlot());
			plots.add(new FaultSectionConnectionsPlot());
			plots.add(new JumpCountsOverDistancePlot());
		} else {
			plots.add(new RupHistogramPlots(RupHistogramPlots.RUP_SET_LIGHT_SCALARS));
		}
		plots.add(new SectMaxValuesPlot());
		if (level == PlotLevel.FULL) {
			plots.add(new JumpAzimuthsPlot());
			plots.add(new BiasiWesnouskyPlots());
			plots.add(new SlipRatePlots());
			plots.add(new SectBySectDetailPlots());
		}
		
		return plots;
	}
	
	public static List<AbstractRupSetPlot> getDefaultSolutionPlots(PlotLevel level) {
		List<AbstractRupSetPlot> plots = new ArrayList<>();
		
		plots.add(new InfoStringPlot());
		plots.add(new LogicTreeBranchPlot());
		plots.add(new SolMFDPlot());
		plots.add(new InversionConfigurationPlot());
		plots.add(new InversionProgressPlot());
		plots.add(new RateDistributionPlot());
		if (level == PlotLevel.DEFAULT || level == PlotLevel.FULL)
			plots.add(new InversionMisfitsPlot());
		plots.add(new ParticipationRatePlot());
		if (level == PlotLevel.DEFAULT || level == PlotLevel.FULL)
			plots.add(new SectBValuePlot());
		plots.add(new PlausibilityConfigurationReport());
		plots.add(new RupHistogramPlots(RupHistogramPlots.SOL_SCALARS));
		if (level == PlotLevel.DEFAULT || level == PlotLevel.FULL) {
			plots.add(new ModulesPlot());
			plots.add(new FaultSectionConnectionsPlot());
			plots.add(new SlipRatePlots());
			plots.add(new PaleoDataComparisonPlot());
			plots.add(new JumpCountsOverDistancePlot());
		}
		if (level == PlotLevel.FULL) {
			plots.add(new HazardMapPlot());
			plots.add(new SegmentationPlot());
		}
		if (level == PlotLevel.DEFAULT || level == PlotLevel.FULL)
			plots.add(new NamedFaultPlot());
		if (level == PlotLevel.FULL)
			plots.add(new SectBySectDetailPlots());
		
		return plots;
	}

	public ReportPageGen(FaultSystemRupSet rupSet, FaultSystemSolution sol, String name, File outputDir,
			List<AbstractRupSetPlot> plots) {
		this(new ReportMetadata(new RupSetMetadata(name, rupSet, sol)), outputDir, plots);
	}

	public ReportPageGen(ReportMetadata meta, File outputDir, List<AbstractRupSetPlot> plots) {
		init(meta, outputDir, plots);
	}
	
	public ReportPageGen(CommandLine cmd) throws IOException {
		File inputFile = new File(cmd.getOptionValue("input-file"));
		Preconditions.checkArgument(inputFile.exists(),
				"Rupture set file doesn't exist: %s", inputFile.getAbsolutePath());
		String inputName;
		if (cmd.hasOption("name"))
			inputName = cmd.getOptionValue("name");
		else
			inputName = inputFile.getName().replaceAll(".zip", "");
		
		File compareFile = null;
		String compName = null;
		if (cmd.hasOption("compare-to")) {
			compareFile = new File(cmd.getOptionValue("compare-to"));
			Preconditions.checkArgument(compareFile.exists(),
					"Rupture set file doesn't exist: %s", compareFile.getAbsolutePath());
			if (cmd.hasOption("comp-name"))
				compName = cmd.getOptionValue("comp-name");
			else
				compName = compareFile.getName().replaceAll(".zip", "");
		}
		
		File outputDir;
		if (cmd.hasOption("output-dir")) {
			Preconditions.checkArgument(!cmd.hasOption("reports-dir"),
					"Can't supply both --output-dir and --reports-dir");
			outputDir = new File(cmd.getOptionValue("output-dir"));
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir(),
					"Output dir doesn't exist and could not be created: %s", outputDir.getAbsolutePath());
		} else {
			Preconditions.checkArgument(cmd.hasOption("reports-dir"),
					"Must supply either --output-dir or --reports-dir");
			File reportsDir = new File(cmd.getOptionValue("reports-dir"));
			Preconditions.checkState(reportsDir.exists() || reportsDir.mkdir(),
					"Reports dir doesn't exist and could not be created: %s", reportsDir.getAbsolutePath());
			
			indexDir = new File(reportsDir, inputFile.getName().replaceAll(".zip", ""));
			Preconditions.checkState(indexDir.exists() || indexDir.mkdir());
			
			if (compareFile == null)
				outputDir = indexDir;
			else
				outputDir = new File(indexDir, "comp_"+compareFile.getName().replaceAll(".zip", ""));
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		}
		
		FaultSystemRupSet rupSet;
		FaultSystemSolution sol = null;
		
		FaultSystemRupSet compRupSet = null;
		FaultSystemSolution compSol = null;
		
		System.out.println("Loading input");
		ZipFile inputZip = new ZipFile(inputFile);
		if (FaultSystemSolution.isSolution(inputZip)) {
			System.out.println("Input is a solution");
			sol = FaultSystemSolution.load(inputZip);
			rupSet = sol.getRupSet();
		} else {
			rupSet = FaultSystemRupSet.load(inputZip);
		}
		if (compareFile != null) {
			System.out.println("Loading comparison");
			ZipFile compZip = new ZipFile(compareFile);
			if (FaultSystemSolution.isSolution(compZip)) {
				System.out.println("comp is a solution");
				compSol = FaultSystemSolution.load(compZip);
				compRupSet = compSol.getRupSet();
			} else {
				compRupSet = FaultSystemRupSet.load(compZip);
			}
		}
		
		RupSetMetadata primaryMeta = new RupSetMetadata(inputName, rupSet, sol);
		RupSetMetadata comparisonMeta = null;
		if (compRupSet != null)
			comparisonMeta = new RupSetMetadata(compName, compRupSet, compSol);
		
		ReportMetadata meta = new ReportMetadata(primaryMeta, comparisonMeta);
		
		PlotLevel level = PLOT_LEVEL_DEFAULT;
		if (cmd.hasOption("plot-level"))
			level = PlotLevel.valueOf(cmd.getOptionValue("plot-level").trim().toUpperCase());
		
		List<AbstractRupSetPlot> plots;
		if (sol != null)
			plots = getDefaultSolutionPlots(level);
		else
			plots = getDefaultRupSetPlots(level);
		
		if (cmd.hasOption("skip-sect-by-sect")) {
			for (int i=plots.size(); --i>=0;)
				if (plots.get(i) instanceof SectBySectDetailPlots)
					plots.remove(i);
		}
		
		if (cmd.hasOption("default-max-dist"))
			this.defaultMaxDist = Double.parseDouble(cmd.getOptionValue("default-max-dist"));
		
		cacheDir = FaultSysTools.getCacheDir(cmd);
		
		replot = cmd.hasOption("replot");
		
		init(meta, outputDir, plots);
		
		setNumThreads(FaultSysTools.getNumThreads(cmd));
		
		if (cmd.hasOption("skip-plausibility")) {
			skipPlausibility();
		} else if (cmd.hasOption("alt-plausibility")) {
			this.attachDefaultModules(primaryMeta, true);
			File altPlausibilityCompareFile = new File(cmd.getOptionValue("alt-plausibility"));
			Preconditions.checkState(altPlausibilityCompareFile.exists(),
					"Alt-plausibility file doesn't exist: %s", altPlausibilityCompareFile.getAbsolutePath());
			
			ClusterConnectionStrategy primaryStrat = null;
			if (primaryMeta.rupSet.hasModule(PlausibilityConfiguration.class))
				primaryStrat = primaryMeta.rupSet.getModule(PlausibilityConfiguration.class).getConnectionStrategy();
			this.altFilters = PlausibilityConfiguration.readFiltersJSON(
					altPlausibilityCompareFile, primaryStrat,
					primaryMeta.rupSet.getModule(SectionDistanceAzimuthCalculator.class));
			
			setAltPlausibility(altFilters, null, false);
		}
	}
	
	public void setNumThreads(int threads) {
		for (AbstractRupSetPlot plot : plots)
			plot.setNumThreads(threads);
	}
	
	public void setAltPlausibility(List<PlausibilityFilter> altFilters, String altName, boolean applyToComparison) {
		// try to insert just after regular filters
		int insertionIndex = -1;
		for (int i=0; i<plots.size(); i++)
			if (plots.get(i) instanceof PlausibilityFilterPlot)
				insertionIndex = i+1;
		if (insertionIndex < 0)
			insertionIndex = plots.size();

		plots.add(new PlausibilityFilterPlot(altFilters, altName, applyToComparison));
	}
	
	public void skipPlausibility() {
		for (int i=plots.size(); --i>=0;)
			if (plots.get(i) instanceof PlausibilityFilterPlot)
				plots.remove(i);
	}
	
	public void skipSectBySect() {
		for (int i=plots.size(); --i>=0;)
			if (plots.get(i) instanceof SectBySectDetailPlots)
				plots.remove(i);
	}
	
	private void init(ReportMetadata meta, File outputDir, List<AbstractRupSetPlot> plots) {
		this.meta = meta;
		this.outputDir = outputDir;
		this.plots = plots;
		if (cacheDir == null)
			cacheDir = FaultSysTools.getCacheDir();
	}
	
	public List<? extends AbstractRupSetPlot> getPlots() {
		return plots;
	}

	public void setPlots(List<AbstractRupSetPlot> plots) {
		this.plots = plots;
	}
	
	public void setReplot(boolean replot) {
		this.replot = replot;
	}

	public File getOutputDir() {
		return outputDir;
	}

	public void setOutputDir(File outputDir) {
		this.outputDir = outputDir;
	}

	public File getIndexDir() {
		return indexDir;
	}

	public void setIndexDir(File indexDir) {
		this.indexDir = indexDir;
	}

	public double getDefaultMaxDist() {
		return defaultMaxDist;
	}

	public void setDefaultMaxDist(double defaultMaxDist) {
		this.defaultMaxDist = defaultMaxDist;
	}

	public File getCacheDir() {
		return cacheDir;
	}

	public void setCacheDir(File cacheDir) {
		this.cacheDir = cacheDir;
	}

	public ReportMetadata getMeta() {
		return meta;
	}
	
	private void attachDefaultModules(RupSetMetadata meta, boolean loadCoulomb) throws IOException {
		attachDefaultModules(meta, cacheDir, defaultMaxDist);
		
		if (cacheDir != null && cacheDir.exists() && loadCoulomb)
			loadCoulombCache(cacheDir);
	}

	public static void attachDefaultModules(RupSetMetadata meta, File cacheDir, double defaultMaxDist) throws IOException {
		FaultSystemRupSet rupSet = meta.rupSet;
		SectionDistanceAzimuthCalculator distAzCalc = rupSet.getModule(SectionDistanceAzimuthCalculator.class);
		PlausibilityConfiguration config = rupSet.getModule(PlausibilityConfiguration.class);
		if (config == null) {
			if (distAzCalc == null) {
				distAzCalc = new SectionDistanceAzimuthCalculator(rupSet.getFaultSectionDataList());
				rupSet.addModule(distAzCalc);
			}
			
			// see if it happens to be a legacy UCERF3 rupture set
			FaultModels fm = getUCERF3FM(rupSet);
			if (fm != null && (rupSet.getNumRuptures() == 253706 || rupSet.getNumRuptures() == 305709)) {
				try {
					config = PlausibilityConfiguration.getUCERF3(rupSet.getFaultSectionDataList(), distAzCalc, fm);
					rupSet.addModule(config);
				} catch (IOException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		} else if (distAzCalc == null) {
			distAzCalc = config.getDistAzCalc();
			rupSet.addModule(distAzCalc);
		}
		
		// add connection search
		if (!rupSet.hasModule(RuptureConnectionSearch.class))
			rupSet.addModule(new RuptureConnectionSearch(rupSet, distAzCalc, getSearchMaxJumpDist(config), false));
		
		
		if (!rupSet.hasAvailableModule(ClusterRuptures.class)) {
			rupSet.addAvailableModule(new Callable<ClusterRuptures>() {

				@Override
				public ClusterRuptures call() throws Exception {
					return ClusterRuptures.instance(rupSet, rupSet.requireModule(RuptureConnectionSearch.class));
				}
				
			}, ClusterRuptures.class);
		}
		
		if (config == null) {
			ClusterRuptures cRups = rupSet.getModule(ClusterRuptures.class);
			int maxNumSplays = 0;
			for (int r=0; r<cRups.size(); r++) {
				ClusterRupture rup = cRups.get(r);
				maxNumSplays = Integer.max(maxNumSplays, rup.getTotalNumSplays());
			}
			
			config = new PlausibilityConfiguration(null, 0,
					buildDefaultConnStrat(distAzCalc, meta.jumps, defaultMaxDist), distAzCalc);
		}
		
		if (cacheDir != null && cacheDir.exists()) {
			File distCache = new File(cacheDir, distAzCalc.getDefaultCacheFileName());
			if (distCache.exists())
				distAzCalc.loadCacheFile(distCache);
		}
	}
	
	static FaultModels getUCERF3FM(FaultSystemRupSet rupSet) {
		if (rupSet.getNumRuptures() == 253706)
			return FaultModels.FM3_1;
		if (rupSet.getNumRuptures() == 305709)
			return FaultModels.FM3_2;
		if (rupSet.hasModule(LogicTreeBranch.class)) {
			LogicTreeBranch<?> branch = rupSet.getModule(LogicTreeBranch.class);
			// null if doesn't have FaultModels
			return branch.getValue(FaultModels.class);
		}
		return null;
	}
	
	private static double getSearchMaxJumpDist(PlausibilityConfiguration config) {
		if (config == null)
			return 100d;
		ClusterConnectionStrategy connStrat = config.getConnectionStrategy();
		double maxDist = connStrat.getMaxJumpDist();
		if (Double.isFinite(maxDist))
			return maxDist;
		return 100d;
	}
	
	public static ClusterConnectionStrategy buildDefaultConnStrat(
			SectionDistanceAzimuthCalculator distAzCalc, Set<Jump> jumps, double defaultMaxDist) {
		System.err.println("WARNING: primary rupture set doesn't have a connection strategy, using actual "
				+ "connections, plus any up to the fallback maxDist="+(float)defaultMaxDist+" km. This may be used "
						+ "by plausibilty filters or segmentation plots. Override with --default-max-dist <dist>");
		
		return new InputJumpsOrDistClusterConnectionStrategy(
				distAzCalc.getSubSections(), distAzCalc, defaultMaxDist, jumps);
	}
	
	public void loadCoulombCache(File cacheDir) throws IOException {
		Map<String, List<AggregatedStiffnessCache>> loadedCoulombCaches = new HashMap<>();
		PlausibilityConfiguration primaryConfig = meta.primary.rupSet.getModule(PlausibilityConfiguration.class);
		if (primaryConfig != null)
			checkLoadCoulombCache(primaryConfig.getFilters(), cacheDir, loadedCoulombCaches);
		PlausibilityConfiguration compConfig = meta.comparison == null ?
				null : meta.comparison.rupSet.getModule(PlausibilityConfiguration.class);
		if (compConfig != null)
			checkLoadCoulombCache(compConfig.getFilters(), cacheDir, loadedCoulombCaches);
		if (altFilters != null)
			checkLoadCoulombCache(altFilters, cacheDir, loadedCoulombCaches);
	}
	
	public static void checkLoadCoulombCache(List<PlausibilityFilter> filters,
			File cacheDir, Map<String, List<AggregatedStiffnessCache>> loadedCoulombCaches) throws IOException {
		if (filters == null)
			return;
		for (PlausibilityFilter filter : filters) {
			AggregatedStiffnessCalculator agg = null;
			if (filter instanceof ScalarCoulombPlausibilityFilter) {
				agg = ((ScalarCoulombPlausibilityFilter)filter).getAggregator();
			} else if (filter instanceof PathPlausibilityFilter) {
				PathPlausibilityFilter pFilter = (PathPlausibilityFilter)filter;
				for (NucleationClusterEvaluator eval : pFilter.getEvaluators()) {
					if (eval instanceof ScalarCoulombPathEvaluator) {
						agg = ((ScalarCoulombPathEvaluator)eval).getAggregator();
						break;
					} else if (eval instanceof CumulativeProbPathEvaluator) {
						for (RuptureProbabilityCalc calc : ((CumulativeProbPathEvaluator)eval).getCalcs()) {
							if (calc instanceof CoulombSectRatioProb) {
								agg = ((CoulombSectRatioProb)calc).getAggregator();
								break;
							} else if (calc instanceof RelativeCoulombProb) {
								agg = ((RelativeCoulombProb)calc).getAggregator();
								break;
							}
						}
						if (agg != null)
							break;
					}
				}
			}
			if (agg != null) {
				SubSectStiffnessCalculator stiffnessCalc = agg.getCalc();
				AggregatedStiffnessCache cache = stiffnessCalc.getAggregationCache(agg.getType());
				String cacheName = cache.getCacheFileName();
				File cacheFile = new File(cacheDir, cacheName);
				if (!cacheFile.exists())
					continue;
				if (loadedCoulombCaches.containsKey(cacheName)) {
					// copy the cache over to this one, if not already set
					List<AggregatedStiffnessCache> caches = loadedCoulombCaches.get(cacheName);
					// it might be shared, so make sure we haven't already loaded that one
					boolean found = false;
					for (AggregatedStiffnessCache oCache : caches) {
						if (oCache == cache) {
							found = true;
							// it's already been populated
							break;
						}
					}
					if (!found) {
						// need to actually populate this one
						cache.copyCacheFrom(cache);
						caches.add(cache);
					}
				}
				if (!loadedCoulombCaches.containsKey(cacheName) && cacheFile.exists()) {
					cache.loadCacheFile(cacheFile);
					List<AggregatedStiffnessCache> caches = new ArrayList<>();
					caches.add(cache);
					loadedCoulombCaches.put(cacheName, caches);
				}
			}
		}
	}

	private static final DecimalFormat countDF = AbstractRupSetPlot.countDF;
	private static final DecimalFormat percentDF = AbstractRupSetPlot.percentDF;
	
	private static final String countPercentStr(Number count, Number total) {
		String ret;
		if (count instanceof Integer)
			ret = countDF.format(count);
		else
			ret = count.floatValue()+"";
		ret += " (";
		double fract = count.doubleValue()/total.doubleValue();
		ret += percentDF.format(fract);
		return ret+")";
	}
	
	private static TableBuilder getHeaderTable(RupSetMetadata primary, RupSetOverlap primaryOverlap,
			RupSetMetadata comparison, RupSetOverlap comparisonOverlap) {
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		if (comparison != null)
			table.addLine("", "Primary", "Comparison: "+comparison.name);
		
		table.initNewLine();
		table.addColumn("**Num Ruptures**");
		table.addColumn(countDF.format(primary.numRuptures));
		if (comparison != null)
			table.addColumn(countDF.format(comparison.numRuptures));
		table.finalizeLine();
		
		table.initNewLine();
		table.addColumn("**Num Single-Stranded Ruptures**");
		table.addColumn(countPercentStr(primary.numSingleStrandRuptures, primary.numRuptures));
		if (comparison != null)
			table.addColumn(countPercentStr(comparison.numSingleStrandRuptures, comparison.numRuptures));
		table.finalizeLine();
		
		if (comparison != null) {
			table.initNewLine();
			table.addColumn("**Num Unique Ruptures**");
			table.addColumn(countPercentStr(primaryOverlap.numUniqueRuptures, primary.numRuptures));
			table.addColumn(countPercentStr(comparisonOverlap.numUniqueRuptures, comparison.numRuptures));
			table.finalizeLine();
		}
		
		if (primary.sol != null || (comparison != null && comparison.sol != null)) {
			table.initNewLine();
			table.addColumn("**Total Supra-Seis Rupture Rate**");
			if (primary.sol == null)
				table.addColumn("_N/A_");
			else
				table.addColumn((float)primary.totalRate);
			if (comparison != null) {
				if (comparison.sol == null)
					table.addColumn("_N/A_");
				else
					table.addColumn((float)comparison.totalRate);
			}
			table.finalizeLine();
			
			if (comparison != null && (primaryOverlap.numUniqueRuptures > 0 || comparisonOverlap.numUniqueRuptures > 0)) {
				table.initNewLine();
				table.addColumn("**Unique Rupture Rate**");
				if (primary.sol == null)
					table.addColumn("_N/A_");
				else
					table.addColumn(countPercentStr(primaryOverlap.uniqueRuptureRate, primary.totalRate));
				if (comparison.sol == null)
					table.addColumn("_N/A_");
				else
					table.addColumn(countPercentStr(comparisonOverlap.uniqueRuptureRate, comparison.totalRate));
				table.finalizeLine();
			}
			
			table.initNewLine();
			table.addColumn("**Total Supra-Seis Recurrence Interval**");
			if (primary.sol == null)
				table.addColumn("_N/A_");
			else
				table.addColumn(AbstractRupSetPlot.twoDigits.format(1d/primary.totalRate)+" yrs");
			if (comparison != null) {
				if (comparison.sol == null)
					table.addColumn("_N/A_");
				else
					table.addColumn(AbstractRupSetPlot.twoDigits.format(1d/comparison.totalRate)+" yrs");
			}
			table.finalizeLine();
			
			table.initNewLine();
			table.addColumn("**Total Moment Rate**");
			if (primary.sol == null)
				table.addColumn("_N/A_");
			else
				table.addColumn(momentRateStr(primary.sol.getTotalFaultSolutionMomentRate()));
			if (comparison != null) {
				if (comparison.sol == null)
					table.addColumn("_N/A_");
				else
					table.addColumn(momentRateStr(comparison.sol.getTotalFaultSolutionMomentRate()));
			}
			table.finalizeLine();
		}
		
		if (primary.rupSet != null) {
			table.initNewLine();
			table.addColumn("**Deformation Model Total Moment Rate**");
			table.addColumn(momentRateStr(primary.rupSet.requireModule(SectSlipRates.class).calcTotalMomentRate()));
			if (comparison != null)
				table.addColumn(momentRateStr(comparison.rupSet.requireModule(SectSlipRates.class).calcTotalMomentRate()));
			table.finalizeLine();
			
			table.initNewLine();
			table.addColumn("**Magnitude Range**");
			table.addColumn(magRange(primary.rupSet));
			if (comparison != null)
				table.addColumn(magRange(comparison.rupSet));
			table.finalizeLine();
			
			table.initNewLine();
			table.addColumn("**Length Range**");
			table.addColumn(lengthRange(primary.rupSet));
			if (comparison != null)
				table.addColumn(lengthRange(comparison.rupSet));
			table.finalizeLine();
			
			table.initNewLine();
			table.addColumn("**Rupture Section Count Range**");
			table.addColumn(sectRange(primary.rupSet));
			if (comparison != null)
				table.addColumn(sectRange(comparison.rupSet));
			table.finalizeLine();
		}
		return table;
	}
	
	private static String momentRateStr(double moRate) {
		String str = (float)moRate+"";
		str = str.toLowerCase(); // lower case 'e' in exponential to make it easier to see
		return str+" N-m/yr";
	}
	
	public void generatePage() throws IOException {
		boolean loadCoulomb = false;
		for (AbstractRupSetPlot plot : plots)
			if (plot instanceof PlausibilityFilterPlot)
				loadCoulomb = true;
		attachDefaultModules(meta.primary, loadCoulomb);
		if (meta.comparison != null)
			attachDefaultModules(meta.comparison, loadCoulomb);
		
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir(),
				"Could not create output directory: %s", outputDir.getAbsolutePath());
		
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir(),
				"Could not create resources directory: %s", resourcesDir.getAbsolutePath());
		String relPathToResources = resourcesDir.getName();
		
		boolean solution = meta.primary.sol != null;
		List<String> headerLines = new ArrayList<>();
		if (solution)
			headerLines.add("# Solution Report: "+meta.primary.name);
		else
			headerLines.add("# Rupture Set Report: "+meta.primary.name);
		headerLines.add("");
		headerLines.addAll(getHeaderTable(meta.primary, meta.primaryOverlap, meta.comparison, meta.comparisonOverlap).build());
		headerLines.add("");
		
		String topLink = "_[(top)](#table-of-contents)_";
		
		File plotMetaFile = new File(outputDir, PLOT_META_FILE_NAME);
		boolean firstTime = !plotMetaFile.exists();
		
		PlotsMetadata prevMeta = null;
		if (!firstTime && !replot)
			prevMeta = loadPlotMetadata(plotMetaFile);
		
		List<PlotMetadata> plotMetas = new ArrayList<>();
		PlotsMetadata plotMeta = new PlotsMetadata(headerLines, plotMetas);
		
		for (AbstractRupSetPlot plot : plots) {
			if (prevMeta != null) { 
				// see if we already have it and can skip regeneration
				PlotMetadata prev = prevMeta.getPlot(plot.getClass());
				if (prev != null) {
					System.out.println("Already have plot '"+plot.getName()+"', won't regenerate. "
							+ "Force regeneration with --replot option.");
					plotMetas.add(prev);
					continue;
				}
			}
			String plotName = ClassUtils.getClassNameWithoutPackage(plot.getClass());
			
			if (!solution && plot instanceof AbstractSolutionPlot) {
				System.out.println("Skipping "+plotName+" as we only have a rupture set");
				continue;
			}
			Collection<Class<? extends OpenSHA_Module>> required = plot.getRequiredModules();
			if (required != null && !required.isEmpty()) {
				boolean hasRequired = true;
				for (Class<? extends OpenSHA_Module> module : required) {
					if (!meta.primary.rupSet.hasModule(module)) {
						// see if it's a solution module
						if (!solution || !meta.primary.sol.hasModule(module)) {
							hasRequired = false;
							System.out.println("Rupture set doesn't have required module "
									+ClassUtils.getClassNameWithoutPackage(module)+", skipping plot: "+plotName);
							break;
						}
					}
				}
				if (!hasRequired)
					continue;
			}
			
			plot.setSubHeading("###");
			
			System.out.println("Generating plot: "+plotName);
			try {
				List<String> plotLines = plot.plot(meta.primary.rupSet, meta.primary.sol, meta,
						resourcesDir, relPathToResources, topLink);
				if (plotLines != null && !plotLines.isEmpty())
					plotMetas.add(new PlotMetadata(plot.getName(), plot.getClass().getName(), plotLines));
				
				if (firstTime) {
					System.out.println("Writing intermediate markdown following "+plotName);
					writeMarkdown(outputDir, meta, plotMeta, topLink);
				}
			} catch (Exception e) {
				System.err.println("Error processing plot (skipping): "+plotName);
				e.printStackTrace();
				System.err.flush();
			}
		}
		
		System.out.println("DONE building report, writing markdown and HTML");
		
		if (indexDir != null) {
			String compTopLink = "*[(back to comparisons table)](#"+MarkdownUtils.getAnchorName("Comparisons Table")+")*";
			if (indexDir == outputDir) {
				// this is top level (no comparison). add comparisons table
				List<String> compLines = new ArrayList<>();
				TableBuilder table = buildComparisonsTable(compLines, compTopLink);
				if (table != null) {
					plotMeta.comparisonsTable = table;
					plotMeta.comparisonLines = compLines;
				}
				writeMarkdown(outputDir, meta, plotMeta, topLink);
			} else {
				// this is a comparison, write it first
				writeMarkdown(outputDir, meta, plotMeta, topLink);
				
				System.out.println("Writing top-level index with comparisons table");
				
				List<String> compLines = new ArrayList<>();
				TableBuilder table = buildComparisonsTable(compLines, compTopLink);
				if (table != null) {
					// now update top level index
					File topLevelMeta = new File(indexDir, PLOT_META_FILE_NAME);
					PlotsMetadata topLevel;
					if (topLevelMeta.exists()) {
						// there is a top level report already, add table to it
						topLevel = loadPlotMetadata(topLevelMeta);
					} else {
						// build a new index with only primary rupture set information
						headerLines = new ArrayList<>();
						if (solution)
							headerLines.add("# Solution Report: "+meta.primary.name);
						else
							headerLines.add("# Rupture Set Report: "+meta.primary.name);
						headerLines.add("");
						headerLines.addAll(getHeaderTable(meta.primary, null, null, null).build());
						headerLines.add("");
						topLevel = new PlotsMetadata(headerLines, new ArrayList<>());
					}
					topLevel.comparisonsTable = table;
					topLevel.comparisonLines = compLines;
					writeMarkdown(indexDir, null, topLevel, topLink);
				}
			}
		} else {
			writeMarkdown(outputDir, meta, plotMeta, topLink);
		}
	}
	
	private TableBuilder buildComparisonsTable(List<String> lines, String topLink) throws IOException {
		Preconditions.checkNotNull(indexDir, "Must have an index to build comparisons");
		Map<File, ReportMetadata> comparisonsMap = new HashMap<>();
		Map<File, String> comparisonNamesMap = new HashMap<>();
		Map<File, PlotsMetadata> plotMetasMap = new HashMap<>();
		
		for (File subDir : indexDir.listFiles()) {
			if (!subDir.isDirectory())
				continue;
			File reportMetaFile = new File(subDir, META_FILE_NAME);
			if (!reportMetaFile.exists())
				continue;
			File plotMetaFile = new File(subDir, PLOT_META_FILE_NAME);
			if (!plotMetaFile.exists())
				continue;
			File resourcesDir = new File(subDir, "resources");
			if (!resourcesDir.exists())
				continue;
			
			ReportMetadata meta = loadReportMetadata(reportMetaFile);
			if (meta.comparison == null) {
				System.out.println("WARNING: found valid plots in sub-directory, but not a comparison (skipping): "
						+subDir.getAbsolutePath());
				continue;
			}
			System.out.println("Found comparison to "+meta.comparison.name+" in "+subDir.getAbsolutePath());
			PlotsMetadata plotMeta = loadPlotMetadata(plotMetaFile);
			
			comparisonNamesMap.put(subDir, meta.comparison.name);
			comparisonsMap.put(subDir, meta);
			plotMetasMap.put(subDir, plotMeta);
		}
		
		if (comparisonsMap.isEmpty())
			return null;
		
		// sort them by name
		List<File> subDirs = ComparablePairing.getSortedData(comparisonNamesMap);
		
		TableBuilder compTable = MarkdownUtils.tableBuilder();
		compTable.addLine("*Name*", "*Num Ruptures*", "*% Change*", "% Overlap (of primary)", "*Num Connections*", "*% Change*");
		
		lines.add("## Comparison Details");
		lines.add(topLink); lines.add("");
		
		for (File subDir : subDirs) {
			
			ReportMetadata meta = comparisonsMap.get(subDir);
			
			String compLink = "**["+meta.comparison.name+"](#"+MarkdownUtils.getAnchorName(meta.comparison.name)
				+")** [(full page)]("+subDir.getName()+"/README.md)";
			String rupPercent = percentDF.format((double)(meta.comparison.numRuptures - meta.primary.numRuptures)
					/(double)meta.primary.numRuptures);
			if (!rupPercent.startsWith("-"))
				rupPercent = "+"+rupPercent;
			String overlapPercent = percentDF.format((double)meta.comparisonOverlap.numCommonRuptures/(double)meta.primary.numRuptures);
			String connPercent = percentDF.format((double)(meta.comparison.actualConnections - meta.primary.actualConnections)
					/(double)meta.primary.actualConnections);
			if (!connPercent.startsWith("-"))
				connPercent = "+"+connPercent;
			compTable.addLine(compLink, countDF.format(meta.comparison.numRuptures), rupPercent, overlapPercent,
					countDF.format(meta.comparison.actualConnections), connPercent);
			
			PlotsMetadata plotsMeta = plotMetasMap.get(subDir);
			
			File resourcesDir = new File(subDir, "resources");
			
			String relPath = subDir.getName()+"/"+resourcesDir.getName();
			
			lines.add("### "+meta.comparison.name);
			lines.add(topLink); lines.add("");
			
			lines.addAll(getHeaderTable(meta.primary, meta.primaryOverlap, meta.comparison, meta.comparisonOverlap).build());
			lines.add("");
			for (PlotMetadata plotMeta : plotsMeta.plots) {
				AbstractRupSetPlot plot;
				try {
					@SuppressWarnings("unchecked") // is caught
					Class<? extends AbstractRupSetPlot> clazz = (Class<? extends AbstractRupSetPlot>) Class.forName(plotMeta.plotClassName);
					plot = clazz.getDeclaredConstructor().newInstance();
				} catch (ClassNotFoundException e) {
					System.out.println("Skipping summary for unkown comparison plot ("+plotMeta.plotName+"): "+e.getMessage());
					continue;
				} catch (Exception e) {
					e.printStackTrace();
					System.err.flush();
					System.out.println("Skipping summary plot (see above error): "+plotMeta.plotName);
					continue;
				}
				System.out.println("Getting summary for "+plotMeta.plotName);
				plot.setSubHeading("####");
				List<String> summary = plot.getSummary(meta, resourcesDir, relPath, topLink);
				if (summary != null && !summary.isEmpty()) {
					lines.addAll(summary);
					lines.add("");
				}
			}
		}
		return compTable;
	}
	
	static class PlotsMetadata {
		public final List<String> headerLines;
		public final List<PlotMetadata> plots;
		
		public transient TableBuilder comparisonsTable = null;
		public transient List<String> comparisonLines = null;
		
		public PlotsMetadata(List<String> headerLines, List<PlotMetadata> plots) {
			super();
			this.headerLines = headerLines;
			this.plots = plots;
		}
		
		public boolean hasPlot(Class<? extends AbstractRupSetPlot> clazz) {
			return getPlot(clazz) != null;
		}
		
		public PlotMetadata getPlot(Class<? extends AbstractRupSetPlot> clazz) {
			for (PlotMetadata plot : plots)
				if (plot.plotClassName.equals(clazz.getName()))
					return plot;
			return null;
		}
	}
	
	static class PlotMetadata {
		
		public final String plotName;
		public final String plotClassName;
		
		public final List<String> markdown;
		
		public PlotMetadata(String plotName, String plotClassName, List<String> markdown) {
			super();
			this.plotName = plotName;
			this.plotClassName = plotClassName;
			this.markdown = markdown;
		}

	}

	protected static final String META_FILE_NAME = "metadata.json";
	protected static final String PLOT_META_FILE_NAME = "plots.json";

	private static void writeMarkdown(File outputDir, ReportMetadata meta, PlotsMetadata plotMeta, String topLink)
			throws IOException {
		List<String> lines = new ArrayList<>(plotMeta.headerLines);
		
		if (plotMeta.comparisonsTable != null) {
			lines.add("");
			lines.add("## Comparisons Table");
			lines.add("");
			lines.addAll(plotMeta.comparisonsTable.build());
			lines.add("");
		}
		
		int tocIndex = lines.size();
		for (PlotMetadata plot : plotMeta.plots) {
			if (plot.markdown != null && !plot.markdown.isEmpty()) {
				lines.add("## "+plot.plotName);
				lines.add(topLink); lines.add("");
				lines.addAll(plot.markdown);
				lines.add("");
			}
		}
		
		if (plotMeta.comparisonLines != null && !plotMeta.comparisonLines.isEmpty()) {
			lines.add("");
			lines.addAll(plotMeta.comparisonLines);
		}
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 3));
		lines.add(tocIndex, "## Table Of Contents");
		
		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
		
		System.out.println("Writing JSON metadata");

		if (meta != null) {
			writeMetadataJSON(meta, new File(outputDir, META_FILE_NAME));
			writeMetadataJSON(plotMeta, new File(outputDir, PLOT_META_FILE_NAME));
		}
	}
	
	private static Gson buildMetaGson() {
		GsonBuilder builder = new GsonBuilder();
		builder.serializeSpecialFloatingPointValues();
		builder.setPrettyPrinting();
		return builder.create();
	}
	
	private static void writeMetadataJSON(Object meta, File jsonFile) throws IOException {
		Gson gson = buildMetaGson();
		FileWriter fw = new FileWriter(jsonFile);
		gson.toJson(meta, fw);
		fw.write("\n");
		fw.close();
	}
	
	private static ReportMetadata loadReportMetadata(File jsonFile) throws IOException {
		Gson gson = buildMetaGson();
		BufferedReader reader = new BufferedReader(new FileReader(jsonFile));
		return gson.fromJson(reader, ReportMetadata.class);
	}
	
	private static PlotsMetadata loadPlotMetadata(File jsonFile) throws IOException {
		Gson gson = buildMetaGson();
		BufferedReader reader = new BufferedReader(new FileReader(jsonFile));
		return gson.fromJson(reader, PlotsMetadata.class);
	}
	
	private static String magRange(FaultSystemRupSet rupSet) {
		MinMaxAveTracker magTrack = new MinMaxAveTracker();
		for (int r=0; r<rupSet.getNumRuptures(); r++)
			magTrack.addValue(rupSet.getMagForRup(r));
		return "["+AbstractRupSetPlot.twoDigits.format(magTrack.getMin())
			+","+AbstractRupSetPlot.twoDigits.format(magTrack.getMax())+"]";
	}
	private static String lengthRange(FaultSystemRupSet rupSet) {
		MinMaxAveTracker lenTrack = new MinMaxAveTracker();
		for (int r=0; r<rupSet.getNumRuptures(); r++)
			lenTrack.addValue(rupSet.getLengthForRup(r)*1e-3);
		return "["+AbstractRupSetPlot.twoDigits.format(lenTrack.getMin())
			+","+AbstractRupSetPlot.twoDigits.format(lenTrack.getMax())+"] km";
	}
	private static String sectRange(FaultSystemRupSet rupSet) {
		MinMaxAveTracker sectsTrack = new MinMaxAveTracker();
		for (int r=0; r<rupSet.getNumRuptures(); r++)
			sectsTrack.addValue(rupSet.getSectionsIndicesForRup(r).size());
		return "["+(int)sectsTrack.getMin()+","+(int)sectsTrack.getMax()+"]";
	}
	
	public static Options createOptions() {
		Options ops = new Options();
		
		ops.addOption(FaultSysTools.cacheDirOption());
		
		// TODO add to docs
		ops.addOption(FaultSysTools.threadsOption());

		Option outDirOption = new Option("od", "output-dir", true,
				"Output directory to write the report. Must supply either this or --reports-dir");
		outDirOption.setRequired(false);
		ops.addOption(outDirOption);

		Option reportsDirOption = new Option("rd", "reports-dir", true,
				"Directory where reports should be written. Individual reports will be placed in "
				+ "subdirectories created using the fault system rupture set/solution file names. Must supply "
				+ "either this or --output-dir");
		reportsDirOption.setRequired(false);
		ops.addOption(reportsDirOption);

		Option rupSetOption = new Option("i", "input-file", true,
				"Path to the primary rupture set/solution being evaluated");
		rupSetOption.setRequired(true);
		ops.addOption(rupSetOption);
		
		Option nameOption = new Option("n", "name", true,
				"Name of the rupture set/solution, if not supplied then the file name will be used as the name");
		nameOption.setRequired(false);
		ops.addOption(nameOption);

		Option compRupSetOption = new Option("cmp", "compare-to", true,
				"Optional path to an alternative rupture set for comparison");
		compRupSetOption.setRequired(false);
		ops.addOption(compRupSetOption);
		
		Option compNameOption = new Option("cn", "comp-name", true,
				"Name of the comparison rupture set, if not supplied then the file name will be used");
		compNameOption.setRequired(false);
		ops.addOption(compNameOption);
		
		Option altPlausibilityOption = new Option("ap", "alt-plausibility", true,
				"Path to a JSON file with an alternative set of plausibility filters which the rupture "
				+ "set should be tested against");
		altPlausibilityOption.setRequired(false);
		ops.addOption(altPlausibilityOption);
		
		Option skipPlausibilityOption = new Option("sp", "skip-plausibility", false,
				"Flag to skip plausibility calculations");
		skipPlausibilityOption.setRequired(false);
		ops.addOption(skipPlausibilityOption);
		
		Option skipSectBySectOption = new Option("ssbs", "skip-sect-by-sect", false,
				"Flag to skip section-by-section plots, regardless of selected plot level");
		skipSectBySectOption.setRequired(false);
		ops.addOption(skipSectBySectOption);
		
		Option distAzCacheOption = new Option("cd", "cache-dir", true,
				"Path to cache files to speed up calculations");
		distAzCacheOption.setRequired(false);
		ops.addOption(distAzCacheOption);
		
		Option maxDistOption = new Option("dmd", "default-max-dist", true,
				"Default maximum distance to use to infer connection strategies (if rupture set doesn't have one). "
				+ "Default: "+(float)DEFAULT_MAX_DIST+" km");
		maxDistOption.setRequired(false);
		ops.addOption(maxDistOption);
		
		Option plotLevelOption = new Option("pl", "plot-level", true,
				"This determins which set of plots should be included. One of: "
						+FaultSysTools.enumOptions(PlotLevel.class)+". Default: "+PLOT_LEVEL_DEFAULT.name());
		plotLevelOption.setRequired(false);
		ops.addOption(plotLevelOption);
		
		Option replotOption = new Option("rp", "replot", false,
				"If supplied, existing plots will be re-generated when re-running a report");
		replotOption.setRequired(false);
		ops.addOption(replotOption);
		
		return ops;
	}
	
	public static double DEFAULT_MAX_DIST = 10d;

	@SuppressWarnings("unused")
	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			// special case to make things easier for Kevin in eclipse
			
			File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
			
			PlotLevel level = PlotLevel.FULL;
			
//			String inputName = "RSQSim 4983, SectArea=0.5";
//			File inputFile = new File(rupSetsDir, "rsqsim_4983_stitched_m6.5_skip65000_sectArea0.5.zip");
			String inputName = "RSQSim 4983, SectArea=0.5, +Plausible Conns";
			File inputFile = new File(rupSetsDir, "rsqsim_4983_stitched_m6.5_skip65000_sectArea0.5_plus_coulomb_conns.zip");
//			String inputName = "RSQSim 5212, SectArea=0.5";
//			File inputFile = new File(rupSetsDir, "rsqsim_5212_m6.5_skip50000_sectArea0.5.zip");
//			String inputName = "RSQSim 498a3, SectArea=0.5, Uniques";
//			File inputFile = new File(rupSetsDir, "rsqsim_4983_stitched_m6.5_skip65000_sectArea0.5_unique.zip");
//			String inputName = "RSQSim 5133, SectArea=0.5";
//			File inputFile = new File(rupSetsDir, "rsqsim_5133_m6_skip50000_sectArea0.5.zip");
			
//			String inputName = "UCERF4 Proposed (U3 Faults)";
//			File inputFile = new File(rupSetsDir, "fm3_1_plausibleMulti15km_adaptive6km_direct_cmlRake360_jumpP0.001_slipP0.05incrCapDist_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.1.zip");
			
//			String inputName = "FM3.1 MeanUCERF3";
//			File inputFile = new File(rupSetsDir, "fm3_1_ucerf3.zip");
//			String inputName = "FM3.1 U3 Ref Branch";
//			File inputFile = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/modular/FM3_1_ZENGBB_Shaw09Mod_DsrTap_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3.zip");
			
//			String inputName = "UCERF4 Proposed (NSHM23 1.2 Faults)";
//			File inputFile = new File(rupSetsDir, "nshm23_geo_dm_v1p1_all_plausibleMulti15km_adaptive6km_direct_cmlRake360_jumpP0.001_slipP0.05incrCapDist_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.1.zip");
			
			// common comparisons
//			boolean skipPlausibility = false;
			boolean skipPlausibility = true;
//			String compName = "UCERF3";
//			File compareFile = new File(rupSetsDir, "fm3_1_ucerf3.zip");
//			File altPlausibilityCompareFile = null;
//			File altPlausibilityCompareFile = new File(rupSetsDir, "u3_az_cff_cmls.json");
//			String compName = null;
//			File compareFile = null;
//			File altPlausibilityCompareFile = null;
//			String compName = "Current Preferred";
//			File compareFile = new File(rupSetsDir, "fm3_1_plausibleMulti15km_adaptive6km_direct_cmlRake360_jumpP0.001_slipP0.05incrCapDist_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.1.zip");
//			File altPlausibilityCompareFile = null;
			String compName = "Draft Model With Seg";
			File compareFile = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/2022_02_15-nshm23_u3_hybrid_branches-FM3_1-CoulombRupSet-DsrUni-TotNuclRate-SubB1-JumpProb-2000ip/results_FM3_1_CoulombRupSet_branch_averaged.zip");
			File altPlausibilityCompareFile = null;

			List<String> argz = new ArrayList<>();
			argz.add("--reports-dir"); argz.add("/home/kevin/markdown/rupture-sets");
			argz.add("--input-file"); argz.add(inputFile.getAbsolutePath());
			if (inputName != null) {
				argz.add("--name"); argz.add(inputName);
			}
			if (compareFile != null) {
				argz.add("--compare-to"); argz.add(compareFile.getAbsolutePath());
				if (compName != null)
					argz.add("--comp-name"); argz.add(compName);
			}
			if (altPlausibilityCompareFile != null) {
				argz.add("--alt-plausibility"); argz.add(altPlausibilityCompareFile.getAbsolutePath());
			}
			if (skipPlausibility)
				argz.add("--skip-plausibility");
			if (level != null) {
				argz.add("--plot-level"); argz.add(level.name());
			}
			argz.add("--default-max-dist"); argz.add("15");
//			argz.add("--skip-sect-by-sect");
			argz.add("--replot");
			args = argz.toArray(new String[0]);
		}
		
		System.setProperty("java.awt.headless", "true");
		
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, ReportPageGen.class);
		
		try {
			new ReportPageGen(cmd).generatePage();
			System.exit(0);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
