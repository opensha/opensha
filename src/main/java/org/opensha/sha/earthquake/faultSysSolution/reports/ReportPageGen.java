package org.opensha.sha.earthquake.faultSysSolution.reports;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.RuptureSets;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.BiasiWesnouskyPlots;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.FaultSectionConnectionsPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.JumpAzimuthsPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.JumpCountsOverDistancePlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.PlausibilityConfigurationReport;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.PlausibilityFilterPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SectMaxValuesPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.SegmentationPlot;
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
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCache;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import scratch.UCERF3.enumTreeBranches.FaultModels;

public class ReportPageGen {
	
	private ReportMetadata meta;
	private List<? extends AbstractRupSetPlot> plots;
	private File outputDir;
	private File indexDir;
	
	private double defaultMaxDist = DEFAULT_MAX_DIST;
	
	private File cacheDir;
	
	private List<PlausibilityFilter> altFilters = null;
	
	public static List<AbstractRupSetPlot> getDefaultRupSetPlots() {
		return List.of(
				new PlausibilityConfigurationReport(),
				new RupHistogramPlots(),
				new PlausibilityFilterPlot(),
				new FaultSectionConnectionsPlot(),
				new JumpCountsOverDistancePlot(),
				new SectMaxValuesPlot(),
				new JumpAzimuthsPlot(),
				new BiasiWesnouskyPlots());
	}
	
	public static List<AbstractRupSetPlot> getDefaultSolutionPlots() {
		return List.of(
				new PlausibilityConfigurationReport(),
				new RupHistogramPlots(),
				new FaultSectionConnectionsPlot(),
				new JumpCountsOverDistancePlot(),
				new SegmentationPlot());
	}

	public ReportPageGen(FaultSystemRupSet rupSet, FaultSystemSolution sol, String name, File outputDir,
			List<? extends AbstractRupSetPlot> plots) {
		this(new ReportMetadata(new RupSetMetadata(name, rupSet, sol)), outputDir, plots);
	}

	public ReportPageGen(ReportMetadata meta, File outputDir, List<? extends AbstractRupSetPlot> plots) {
		this.meta = meta;
		this.outputDir = outputDir;
		this.plots = plots;
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
		
		List<AbstractRupSetPlot> plots;
		if (sol != null)
			plots = getDefaultSolutionPlots();
		else
			plots = getDefaultRupSetPlots();
		
		if (cmd.hasOption("skip-plausibility")) {
			for (int i=plots.size(); --i>=0;)
				if (plots.get(i) instanceof PlausibilityFilterPlot)
					plots.remove(i);
		} else if (cmd.hasOption("alt-plausibility")) {
			this.attachDefaultModules(primaryMeta);
			File altPlausibilityCompareFile = new File(cmd.getOptionValue("alt-plausibility"));
			Preconditions.checkState(altPlausibilityCompareFile.exists(),
					"Alt-plausibility file doesn't exist: %s", altPlausibilityCompareFile.getAbsolutePath());
			
			ClusterConnectionStrategy primaryStrat = null;
			if (primaryMeta.rupSet.hasModule(PlausibilityConfiguration.class))
				primaryStrat = primaryMeta.rupSet.getModule(PlausibilityConfiguration.class).getConnectionStrategy();
			this.altFilters = PlausibilityConfiguration.readFiltersJSON(
					altPlausibilityCompareFile, primaryStrat,
					primaryMeta.rupSet.getModule(SectionDistanceAzimuthCalculator.class));
			
			// try to insert just after regular filters
			int insertionIndex = -1;
			for (int i=0; i<plots.size(); i++)
				if (plots.get(i) instanceof PlausibilityFilterPlot)
					insertionIndex = i+1;
			if (insertionIndex < 0)
				insertionIndex = plots.size();
			
//			plots.add(new PlausibilityFilterPlot(altFilters, "Alternative Filters", false));
		}
		
		if (cmd.hasOption("default-max-dist"))
			this.defaultMaxDist = Double.parseDouble(cmd.getOptionValue("default-max-dist"));
		
		if (cmd.hasOption("cache-dir"))
			this.cacheDir = new File(cmd.getOptionValue("cache-dir"));
		
		init(meta, outputDir, plots);
	}
	
	private void init(ReportMetadata meta, File outputDir, List<? extends AbstractRupSetPlot> plots) {
		this.meta = meta;
		this.outputDir = outputDir;
		this.plots = plots;
		if (cacheDir == null)
			cacheDir = RuptureSets.getCacheDir();
	}
	
	public List<? extends AbstractRupSetPlot> getPlots() {
		return plots;
	}

	public void setPlots(List<? extends AbstractRupSetPlot> plots) {
		this.plots = plots;
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

	private void attachDefaultModules(RupSetMetadata meta) throws IOException {
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
			if (fm != null) {
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
			
			loadCoulombCache(cacheDir);
		}
	}
	
	static FaultModels getUCERF3FM(FaultSystemRupSet rupSet) {
		if (rupSet.getNumRuptures() == 253706)
			return FaultModels.FM3_1;
		if (rupSet.getNumRuptures() == 305709)
			return FaultModels.FM3_2;
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
	
	static ClusterConnectionStrategy buildDefaultConnStrat(
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
	
	static void checkLoadCoulombCache(List<PlausibilityFilter> filters,
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
	
	public void generatePage() throws IOException {
		attachDefaultModules(meta.primary);
		if (meta.comparison != null)
			attachDefaultModules(meta.comparison);
		
		List<String> lines = new ArrayList<>();
		
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir(),
				"Could not create resources directory: %s", resourcesDir.getAbsolutePath());
		String relPathToResources = resourcesDir.getName();
		
		boolean solution = meta.primary.sol != null;
		if (solution)
			lines.add("# Solution Report: "+meta.primary.name);
		else
			lines.add("# Rupture Set Report: "+meta.primary.name);
		lines.add("");
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		if (meta.comparison != null)
			table.addLine("", "Primary", "Comparison: "+meta.comparison.name);
		
		table.initNewLine();
		table.addColumn("**Num Ruptures**");
		table.addColumn(countDF.format(meta.primary.numRuptures));
		if (meta.comparison != null)
			table.addColumn(countDF.format(meta.comparison.numRuptures));
		table.finalizeLine();
		
		table.initNewLine();
		table.addColumn("**Num Single-Stranded Ruptures**");
		table.addColumn(countPercentStr(meta.primary.numSingleStrandRuptures, meta.primary.numRuptures));
		if (meta.comparison != null)
			table.addColumn(countPercentStr(meta.comparison.numSingleStrandRuptures, meta.comparison.numRuptures));
		table.finalizeLine();
		
		if (meta.comparison != null) {
			table.initNewLine();
			table.addColumn("**Num Unique Ruptures**");
			table.addColumn(countPercentStr(meta.primaryOverlap.numUniqueRuptures, meta.primary.numRuptures));
			table.addColumn(countPercentStr(meta.comparisonOverlap.numUniqueRuptures, meta.comparison.numRuptures));
			table.finalizeLine();
		}
		
		if (solution) {
			table.initNewLine();
			table.addColumn("**Total Rupture Rate**");
			table.addColumn((float)meta.primary.totalRate);
			if (meta.comparison != null)
				table.addColumn((float)meta.comparison.totalRate);
			table.finalizeLine();
			
			if (meta.comparison != null) {
				table.initNewLine();
				table.addColumn("**Unique Rupture Rate**");
				if (meta.primary.sol == null)
					table.addColumn("_N/A_");
				else
					table.addColumn(countPercentStr(meta.primaryOverlap.uniqueRuptureRate, meta.primary.totalRate));
				if (meta.comparison.sol == null)
					table.addColumn("_N/A_");
				else
					table.addColumn(countPercentStr(meta.comparisonOverlap.uniqueRuptureRate, meta.comparison.totalRate));
				table.finalizeLine();
			}
		}
		
		table.initNewLine();
		table.addColumn("**Magnitude Range**");
		table.addColumn(magRange(meta.primary.rupSet));
		if (meta.comparison != null)
			table.addColumn(magRange(meta.comparison.rupSet));
		table.finalizeLine();
		
		table.initNewLine();
		table.addColumn("**Length Range**");
		table.addColumn(lengthRange(meta.primary.rupSet));
		if (meta.comparison != null)
			table.addColumn(lengthRange(meta.comparison.rupSet));
		table.finalizeLine();
		
		table.initNewLine();
		table.addColumn("**Rupture Section Count Range**");
		table.addColumn(sectRange(meta.primary.rupSet));
		if (meta.comparison != null)
			table.addColumn(sectRange(meta.comparison.rupSet));
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");
		
		int tocIndex = lines.size();
		String topLink = "_[(top)](#table-of-contents)_";
		
		boolean firstTime = !new File(outputDir, META_FILE_NAME).exists();
		
		for (AbstractRupSetPlot plot : plots) {
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
			
			try {
				List<String> plotLines = plot.plot(meta.primary.rupSet, meta.primary.sol, meta,
						resourcesDir, relPathToResources, topLink);
				lines.addAll(plotLines);
				lines.add("");
				
				if (firstTime) {
					System.out.println("Writing intermediate markdown following "+plotName);
					writeMarkdown(outputDir, meta, lines, tocIndex);
				}
			} catch (Exception e) {
				System.err.println("Error processing plot (skipping): "+plotName);
				e.printStackTrace();
				System.err.flush();
			}
		}
		
		System.out.println("DONE building report, writing markdown and HTML");
		
		writeMarkdown(outputDir, meta, lines, tocIndex);
		
		if (indexDir != null) {
			// TODO write index
		}
	}
	
	protected static final String META_FILE_NAME = "metadata.json";

	private static void writeMarkdown(File outputDir, ReportMetadata meta, List<String> lines, int tocIndex)
			throws IOException {
		lines = new ArrayList<>(lines);
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2));
		lines.add(tocIndex, "## Table Of Contents");
		
		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
		
		System.out.println("Writing JSON metadata");
		
		writeMetadataJSON(meta, new File(outputDir, META_FILE_NAME));
	}
	
	private static Gson buildMetaGson() {
		GsonBuilder builder = new GsonBuilder();
		builder.serializeSpecialFloatingPointValues();
		builder.setPrettyPrinting();
		return builder.create();
	}
	
	private static void writeMetadataJSON(ReportMetadata meta, File jsonFile) throws IOException {
		Gson gson = buildMetaGson();
		FileWriter fw = new FileWriter(jsonFile);
		gson.toJson(meta, fw);
		fw.write("\n");
		fw.close();
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
		
		Option distAzCacheOption = new Option("cd", "cache-dir", true,
				"Path to cache files to speed up calculations");
		distAzCacheOption.setRequired(false);
		ops.addOption(distAzCacheOption);
		
		Option maxDistOption = new Option("dmd", "default-max-dist", true,
				"Default maximum distance to use to infer connection strategies (if rupture set doesn't have one). "
				+ "Default: "+(float)DEFAULT_MAX_DIST+" km");
		maxDistOption.setRequired(false);
		ops.addOption(maxDistOption);
		
		return ops;
	}
	
	public static double DEFAULT_MAX_DIST = 10d;

	@SuppressWarnings("unused")
	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			// special case to make things easier for Kevin in eclipse
			
			File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
			
//			writeIndex(new File("/data/kevin/markdown/rupture-sets/"
////					+ "fm3_1_adapt5_10km_min2_cff3_4_IntsPos_cffProb0.01NegRelBest_cffJumpPatchNetFract0.5_cffSectFav15PathPos"));
//					+ "fm3_1_adapt5_10km_sMax1_slipP0.01incr_cff3_4_IntsPos_comb3Paths_cffP0.01_cffSPathFav15_cffCPathRPatchHalfPos"));
//			System.exit(0);
			
//			String inputName = "RSQSim 4983, SectArea=0.5";
//			File inputFile = new File(rupSetsDir, "rsqsim_4983_stitched_m6.5_skip65000_sectArea0.5.zip");
//			String inputName = "RSQSim 5212, SectArea=0.5";
//			File inputFile = new File(rupSetsDir, "rsqsim_5212_m6.5_skip50000_sectArea0.5.zip");
//			String inputName = "RSQSim 4983, SectArea=0.5, Uniques";
//			File inputFile = new File(rupSetsDir, "rsqsim_4983_stitched_m6.5_skip65000_sectArea0.5_unique.zip");
//			String inputName = "RSQSim 5133, SectArea=0.5";
//			File inputFile = new File(rupSetsDir, "rsqsim_5133_m6_skip50000_sectArea0.5.zip");
			
			String inputName = "UCERF4 Proposed (U3 Faults)";
			File inputFile = new File(rupSetsDir, "fm3_1_plausibleMulti15km_adaptive6km_direct_cmlRake360_jumpP0.001_slipP0.05incrCapDist_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.1.zip");
//			boolean skipPlausibility = false;
//			File altPlausibilityCompareFile = null;
//			String compName = null;
//			File compareFile = null;
			
//			String inputName = "UCERF4 Proposed (NSHM23 1.2 Faults)";
//			File inputFile = new File(rupSetsDir, "nshm23_v1p2_all_plausibleMulti15km_adaptive6km_direct_cmlRake360_jumpP0.001_slipP0.05incrCapDist_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.1.zip");
//			boolean skipPlausibility = false;
//			File altPlausibilityCompareFile = null;
//			String compName = null;
//			File compareFile = null;
			
			// UCERF3 variants
//			boolean skipPlausibility = false;
////			String compName = "UCERF3 10km, No Coulomb";
////			File compareFile = new File(rupSetsDir, "fm3_1_ucerf3_10km_noCoulomb.zip");
////			File altPlausibilityCompareFile = null;
//			String compName = "UCERF3 Adaptive 5-10km (SMax=1), No Coulomb";
//			File compareFile = new File(rupSetsDir, "fm3_1_ucerf3_adapt5_10km_sMax1_noCoulomb.zip");
//			File altPlausibilityCompareFile = null;
////			String compName = "UCERF3 Coulomb Reproduce";
////			File compareFile = new File(rupSetsDir, "fm3_1_ucerf3_5km_cffReproduce.zip");
////			File altPlausibilityCompareFile = null;
////			String compName = "UCERF3 10km, CFF Falback";
////			File compareFile = new File(rupSetsDir, "fm3_1_ucerf3_10km_cffFallback.zip");
////			File altPlausibilityCompareFile = null;
			
			
			// common ones
			boolean skipPlausibility = false;
			String compName = "UCERF3";
			File compareFile = new File(rupSetsDir, "fm3_1_ucerf3.zip");
			File altPlausibilityCompareFile = new File(rupSetsDir, "u3_az_cff_cmls.json");
//			String compName = null;
//			File compareFile = null;
////			File altPlausibilityCompareFile = new File(rupSetsDir, "cur_pref_filters.json");
//			File altPlausibilityCompareFile = null;
//			File altPlausibilityCompareFile = new File(rupSetsDir, "alt_filters.json");
//			String compName = "1km Coulomb Patches";
//			File compareFile = new File(rupSetsDir, "fm3_1_stiff1km_plausible10km_direct_slipP0.05incr_cff0.75IntsPos_comb2Paths_cffFavP0.02_cffFavRatioN2P0.5_sectFractPerm0.05.zip");
//			File altPlausibilityCompareFile = null;
////			File altPlausibilityCompareFile = new File(rupSetsDir, "cur_pref_filters.json");
//			String compName = "Current Preferred";
//			File compareFile = new File(rupSetsDir, "fm3_1_plausibleMulti15km_adaptive6km_direct_cmlRake360_jumpP0.001_slipP0.05incrCapDist_cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.1.zip");
//			File altPlausibilityCompareFile = null;

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
			args = argz.toArray(new String[0]);
		}
		
		System.setProperty("java.awt.headless", "true");
		
		Options options = createOptions();
		
		CommandLineParser parser = new DefaultParser();
		
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			e.printStackTrace();
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(ClassUtils.getClassNameWithoutPackage(ReportPageGen.class),
					options, true );
			System.exit(2);
			return;
		}
		
		try {
			new ReportPageGen(cmd).generatePage();
			System.exit(0);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
