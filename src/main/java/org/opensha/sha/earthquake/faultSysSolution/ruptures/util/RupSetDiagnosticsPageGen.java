package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.dom4j.DocumentException;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYBoxAnnotation;
import org.jfree.chart.annotations.XYPolygonAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.data.Range;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.TextAnchor;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.region.CaliforniaRegions.RELM_TESTING;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZGraphPanel;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.GapWithinSectFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpDistFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.SingleClusterPerParentFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.SplayCountFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.TotalAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.strategies.ClusterConnectionStrategy;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.inversion.laughTest.PlausibilityResult;
import scratch.UCERF3.utils.FaultSystemIO;

public class RupSetDiagnosticsPageGen {

	public static void main(String[] args) throws IOException, DocumentException {
		File rupSetsDir = new File("/home/kevin/OpenSHA/UCERF4/rup_sets");
		
		File mainOutputDir = new File("/home/kevin/markdown/rupture-sets");

		File distAzCache = new File(rupSetsDir, "fm_3_1_dist_az_cache.csv");
//		String inputName = "No Az/Rake, CFF Parent & Cluster Positive";
//		File inputFile = new File(rupSetsDir, "fm3_1_noAz_noRake_parentPositive_cffClusterPositive.zip");
//		String inputName = "No Az/Rake, CFF Parent & Cluster Positive";
//		File inputFile = new File(rupSetsDir, "fm3_1_noAz_noRake_parentPositive_cffClusterPositive.zip");
//		String inputName = "No Az/Rake, CFF Cluster PathPositive";
//		File inputFile = new File(rupSetsDir, "fm3_1_noAz_cffClusterPathPositive.zip");
		String inputName = "No Az/Rake, CFF Cluster Positive";
		File inputFile = new File(rupSetsDir, "fm3_1_noAz_cffClusterPositive.zip");
//		String compName = "UCERF3";
//		File compareFile = new File(rupSetsDir, "fm3_1_ucerf3.zip");
		String compName = "New Test";
		File compareFile = new File("/tmp/test_rup_set.zip");
		Region region = new RELM_TESTING();
		
		File outputDir = new File(mainOutputDir, inputFile.getName().replaceAll(".zip", ""));
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		if (compareFile == null)
			outputDir = new File(outputDir, "standalone");
		else
			outputDir = new File(outputDir, "comp_"+compareFile.getName().replaceAll(".zip", ""));
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		FaultSystemRupSet inputRupSet;
		FaultSystemSolution inputSol = null;
		PlausibilityConfiguration inputConfig = null;
		
		FaultSystemRupSet compRupSet = null;
		FaultSystemSolution compSol = null;
		PlausibilityConfiguration compConfig = null;
		
		System.out.println("Loading input");
		if (FaultSystemIO.isSolution(inputFile)) {
			System.out.println("Input is a solution");
			inputSol = FaultSystemIO.loadSol(inputFile);
			inputRupSet = inputSol.getRupSet();
		} else {
			inputRupSet = FaultSystemIO.loadRupSet(inputFile);
		}
		inputConfig = inputRupSet.getPlausibilityConfiguration();
		List<? extends FaultSection> subSects = inputRupSet.getFaultSectionDataList();
		
		SectionDistanceAzimuthCalculator distAzCalc = new SectionDistanceAzimuthCalculator(subSects);
		if (distAzCache.exists())
			distAzCalc.loadCacheFile(distAzCache);
		int numAzCached = distAzCalc.getCachedAzimuths().size();
		int numDistCached = distAzCalc.getCachedDistances().size();
		
		if (inputConfig == null) {
			// see if it's UCERF3
			FaultModels fm = getUCERF3FM(inputRupSet);
			if (fm != null) {
				inputConfig = PlausibilityConfiguration.getUCERF3(subSects, distAzCalc, fm);
				inputRupSet.setPlausibilityConfiguration(inputConfig);
			}
		}
		RuptureConnectionSearch inputSearch = new RuptureConnectionSearch(
				inputRupSet, distAzCalc, getSearchMaxJumpDist(inputConfig), false);
		System.out.println("Building input cluster ruptures");
		List<ClusterRupture> inputRups = buildClusterRups(inputRupSet, inputSearch);
		HashSet<UniqueRupture> inputUniques = new HashSet<>();
		for (ClusterRupture rup : inputRups)
			inputUniques.add(rup.unique);

		List<ClusterRupture> compRups = null;
		RuptureConnectionSearch compSearch = null;
		HashSet<UniqueRupture> compUniques = null;
		if (compareFile != null) {
			System.out.println("Loading comparison");
			if (FaultSystemIO.isSolution(compareFile)) {
				System.out.println("comp is a solution");
				compSol = FaultSystemIO.loadSol(compareFile);
				compRupSet = compSol.getRupSet();
			} else {
				compRupSet = FaultSystemIO.loadRupSet(compareFile);
			}
			Preconditions.checkState(compRupSet.getNumSections() == subSects.size(),
					"comp has different sub sect count");
			compConfig = compRupSet.getPlausibilityConfiguration();
			if (compConfig == null) {
				// see if it's UCERF3
				FaultModels fm = getUCERF3FM(compRupSet);
				if (fm != null) {
					compConfig = PlausibilityConfiguration.getUCERF3(subSects, distAzCalc, fm);
					compRupSet.setPlausibilityConfiguration(compConfig);
				}
			}
			compSearch = new RuptureConnectionSearch(
					compRupSet, distAzCalc, getSearchMaxJumpDist(compConfig), false);
			compRups = buildClusterRups(compRupSet, compSearch);
			compUniques = new HashSet<>();
			for (ClusterRupture rup : compRups)
				compUniques.add(rup.unique);
		}
		
		List<String> lines = new ArrayList<>();
		lines.add("# Rupture Set Diagnostics: "+inputName);
		lines.add("");
		lines.addAll(getBasicLines(inputRupSet));
		lines.add("");
		if (inputConfig != null) {
			lines.add("## Plausibility Configuration:");
			lines.add("");
			lines.addAll(getPlausibilityLines(inputConfig));
			lines.add("");
		}
		
		if (compRupSet != null) {
			lines.add("## Comparison Rup Set");
			lines.add("");
			lines.add("Will include comparisons against: "+compName);
			lines.add("");
			lines.addAll(getBasicLines(compRupSet));
			lines.add("");
			if (inputConfig != null) {
				lines.add("### Comp Plausibility Configuration:");
				lines.add("");
				lines.addAll(getPlausibilityLines(compConfig));
				lines.add("");
			}
		}
		
		// length and magnitude distributions
		int tocIndex = lines.size();
		String topLink = "*[(top)](#table-of-contents)*";
		
		lines.add("## Rupture Size Histograms");
		lines.add(topLink); lines.add("");
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		if (compRupSet != null)
			table.addLine(inputName, compName);
		
		plotRuptureHistograms(resourcesDir, "len_hist", table, inputRupSet, inputSol,
				inputUniques, compRupSet, compSol, compUniques, true);
		plotRuptureHistograms(resourcesDir, "mag_hist", table, inputRupSet, inputSol,
				inputUniques, compRupSet, compSol, compUniques, false);
		
		lines.addAll(table.build());
		lines.add("");
		
//		System.gc();
//		try {
//			Thread.sleep(100000000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		if (compRups != null && (inputConfig != null || compConfig != null)) {
			// plausibility comparisons
			
			lines.add("Plausibility Comparisons");
			lines.add(topLink); lines.add("");
			
			if (compConfig != null) {
				lines.add("## Comparisons with "+compName+" filters");
				lines.add(topLink); lines.add("");
				
				List<PlausibilityFilter> filters = new ArrayList<>();
				// add implicit filters
				double jumpDist = compConfig.getConnectionStrategy().getMaxJumpDist();
				if (Double.isFinite(jumpDist))
					filters.add(new JumpDistFilter(jumpDist));
				filters.add(new SplayCountFilter(compConfig.getMaxNumSplays()));
				filters.add(new GapWithinSectFilter());
				filters.addAll(compConfig.getFilters());
				RupSetPlausibilityResult result = testRupSetPlausibility(inputRups, filters);
				File plot = plotRupSetPlausibility(result, resourcesDir, "comp_filter_compare",
						"Comparison with "+compName+" Filters");
				lines.add("![plot](resources/"+plot.getName()+")");
				lines.add("");
				lines.addAll(getRupSetPlausibilityTable(result).build());
				lines.add("");
			}
			
			if (compRups != null && inputConfig != null) {
				lines.add("## "+compName+" comparisons with new filters");
				lines.add(topLink); lines.add("");
				
				List<PlausibilityFilter> filters = new ArrayList<>();
				// add implicit filters
				double jumpDist = inputConfig.getConnectionStrategy().getMaxJumpDist();
				if (Double.isFinite(jumpDist))
					filters.add(new JumpDistFilter(jumpDist));
				filters.add(new SplayCountFilter(inputConfig.getMaxNumSplays()));
				filters.add(new SingleClusterPerParentFilter());
				filters.addAll(inputConfig.getFilters());
				RupSetPlausibilityResult result = testRupSetPlausibility(compRups, filters);
				File plot = plotRupSetPlausibility(result, resourcesDir, "main_filter_compare",
						"Comparison with "+inputName+" Filters");
				lines.add("![plot](resources/"+plot.getName()+")");
				lines.add("");
				lines.addAll(getRupSetPlausibilityTable(result).build());
				lines.add("");
			}
		}
		
		// connections plots
		Map<Jump, List<Integer>> inputJumpsToRupsMap = new HashMap<>();
		Map<Jump, Double> inputJumps = getJumps(inputSol, inputRups, inputJumpsToRupsMap);
		
		System.out.println("Plotting section connections");
		plotConnectivityLines(inputRupSet, resourcesDir, "sect_connectivity",
				inputName+" Connectivity", inputJumps.keySet(), MAIN_COLOR, region, 800);
		plotConnectivityLines(inputRupSet, resourcesDir, "sect_connectivity_hires",
				inputName+" Connectivity", inputJumps.keySet(), MAIN_COLOR, region, 3000);
		Map<Jump, List<Integer>> compJumpsToRupsMap = null;
		Map<Jump, Double> compJumps = null;
		Map<Jump, Double> inputUniqueJumps = null;
		Set<Jump> commonJumps = null;
		Map<Jump, Double> compUniqueJumps = null;
		if (compRups != null) {
			compJumpsToRupsMap = new HashMap<>();
			compJumps = getJumps(compSol, compRups, compJumpsToRupsMap);
			plotConnectivityLines(compRupSet, resourcesDir, "sect_connectivity_comp",
					compName+" Connectivity", compJumps.keySet(), COMP_COLOR, region, 800);
			plotConnectivityLines(compRupSet, resourcesDir, "sect_connectivity_comp_hires",
					compName+" Connectivity", compJumps.keySet(), COMP_COLOR, region, 3000);
			inputUniqueJumps = new HashMap<>(inputJumps);
			commonJumps = new HashSet<>();
			for (Jump jump : compJumps.keySet()) {
				if (inputUniqueJumps.containsKey(jump)) {
					inputUniqueJumps.remove(jump);
					commonJumps.add(jump);
				}
			}
			plotConnectivityLines(inputRupSet, resourcesDir, "sect_connectivity_unique",
					inputName+" Unique Connectivity", inputUniqueJumps.keySet(), MAIN_COLOR, region, 800);
			plotConnectivityLines(inputRupSet, resourcesDir, "sect_connectivity_unique_hires",
					inputName+" Unique Connectivity", inputUniqueJumps.keySet(), MAIN_COLOR, region, 3000);
			compUniqueJumps = new HashMap<>(compJumps);
			for (Jump jump : inputJumps.keySet())
				if (compUniqueJumps.containsKey(jump))
					compUniqueJumps.remove(jump);
			plotConnectivityLines(compRupSet, resourcesDir, "sect_connectivity_unique_comp",
					compName+" Unique Connectivity", compUniqueJumps.keySet(), COMP_COLOR, region, 800);
			plotConnectivityLines(compRupSet, resourcesDir, "sect_connectivity_unique_comp_hires",
					compName+" Unique Connectivity", compUniqueJumps.keySet(), COMP_COLOR, region, 3000);
		}
		
		double maxConnDist = 0d;
		for (Jump jump : inputJumps.keySet())
			maxConnDist = Math.max(maxConnDist, jump.distance);
		for (Jump jump : compJumps.keySet())
			maxConnDist = Math.max(maxConnDist, jump.distance);
		plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist",
				inputName+" Connectivity", inputJumps, inputUniqueJumps, maxConnDist,
				MAIN_COLOR, false, false);
		if (inputSol != null) {
			plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_rates",
					inputName+" Connectivity", inputJumps, inputUniqueJumps, maxConnDist,
					MAIN_COLOR, true, false);
			plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_rates_log",
					inputName+" Connectivity", inputJumps, inputUniqueJumps, maxConnDist,
					MAIN_COLOR, true, true);
		}
		if (compRups != null) {
			plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_comp",
					compName+" Connectivity", compJumps, compUniqueJumps, maxConnDist,
					COMP_COLOR, false, false);
			if (compSol != null) {
				plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_rates_comp",
						compName+" Connectivity", compJumps, compUniqueJumps, maxConnDist,
						COMP_COLOR, true, false);
				plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_rates_comp_log",
						compName+" Connectivity", compJumps, compUniqueJumps, maxConnDist,
						COMP_COLOR, true, true);
			}
		}
		
		lines.add("## Fault Section Connections");
		lines.add(topLink); lines.add("");
		
		if (compRups != null) {
			List<Set<Jump>> connectionsList = new ArrayList<>();
			List<Color> connectedColors = new ArrayList<>();
			List<String> connNames = new ArrayList<>();
			
			connectionsList.add(inputUniqueJumps.keySet());
			connectedColors.add(MAIN_COLOR);
			connNames.add(inputName+" Only");
			
			connectionsList.add(compUniqueJumps.keySet());
			connectedColors.add(COMP_COLOR);
			connNames.add(compName+" Only");
			
			connectionsList.add(commonJumps);
			connectedColors.add(COMMON_COLOR);
			connNames.add("Common Connections");
			
			String combConnPrefix = "sect_connectivity_combined";
			plotConnectivityLines(inputRupSet, resourcesDir, combConnPrefix, "Combined Connectivity",
					connectionsList, connectedColors, connNames, region, 800);
			plotConnectivityLines(inputRupSet, resourcesDir, combConnPrefix+"_hires", "Combined Connectivity",
					connectionsList, connectedColors, connNames, region, 3000);
			lines.add("![Combined]("+resourcesDir.getName()+"/"+combConnPrefix+".png)");
			lines.add("");
			lines.add("[View high resolution]("+resourcesDir.getName()+"/"+combConnPrefix+"_hires.png)");
			lines.add("");
		}
		
		table = MarkdownUtils.tableBuilder();
		if (compRups != null)
			table.addLine(inputName, compName);
		File mainPlot = new File(resourcesDir, "sect_connectivity.png");
		File compPlot = new File(resourcesDir, "sect_connectivity_comp.png");
		addTablePlots(table, mainPlot, compPlot, compRups != null);
		if (compRups != null) {
			mainPlot = new File(resourcesDir, "sect_connectivity_unique.png");
			compPlot = new File(resourcesDir, "sect_connectivity_unique_comp.png");
			addTablePlots(table, mainPlot, compPlot, compRups != null);
		}
		mainPlot = new File(resourcesDir, "sect_connectivity_hist.png");
		compPlot = new File(resourcesDir, "sect_connectivity_hist_comp.png");
		addTablePlots(table, mainPlot, compPlot, compRups != null);
		if (inputSol != null || compSol != null) {
			mainPlot = new File(resourcesDir, "sect_connectivity_hist_rates.png");
			compPlot = new File(resourcesDir, "sect_connectivity_hist_rates_comp.png");
			addTablePlots(table, mainPlot, compPlot, compRups != null);
			mainPlot = new File(resourcesDir, "sect_connectivity_hist_rates_log.png");
			compPlot = new File(resourcesDir, "sect_connectivity_hist_rates_comp_log.png");
			addTablePlots(table, mainPlot, compPlot, compRups != null);
		}
		lines.addAll(table.build());
		lines.add("");
		
		if (compRups!= null) {
			System.out.println("Plotting connection examples");
			lines.add("### Unique Connection Example Ruptures");
			lines.add(topLink); lines.add("");
			
			lines.add("** New Ruptures with Unique Connections**");
			int maxRups = 20;
			int maxCols = 5;
			table = plotConnRupExamples(inputSearch, inputUniqueJumps.keySet(),
					inputJumpsToRupsMap, maxRups, maxCols, resourcesDir, "conn_example");
			lines.add("");
			if (table == null)
				lines.add("*N/A*");
			else
				lines.addAll(table.build());
			lines.add("");
			lines.add("**"+compName+" Ruptures with Unique Connections**");
			table = plotConnRupExamples(compSearch, compUniqueJumps.keySet(),
					compJumpsToRupsMap, maxRups, maxCols, resourcesDir, "comp_conn_example");
			lines.add("");
			if (table == null)
				lines.add("*N/A*");
			else
				lines.addAll(table.build());
			lines.add("");
		}
		
		// now jumps
		
		float[] maxJumpDists = { 0.1f, 1f, 3f };
		boolean hasSols = compSol != null || inputSol != null;
		
		lines.add("## Jump Counts Over Distance");
		lines.add(topLink); lines.add("");
		table = MarkdownUtils.tableBuilder();
		if (hasSols)
			table.addLine("As Discretized", "Rate Weighted");
		for (float jumpDist : maxJumpDists) {
			lines.add("");
			System.out.println("Plotting num jumps");
			table.initNewLine();
			File plotFile = plotFixedJumpDist(inputRupSet, null, inputRups, inputName,
					compRupSet, null, compRups, compName, distAzCalc, 0d, jumpDist, resourcesDir);
			table.addColumn("![Plausibility Filter]("+resourcesDir.getName()+"/"+plotFile.getName()+")");
			if (hasSols) {
				plotFile = plotFixedJumpDist(
						inputSol == null ? null : inputRupSet, inputSol, inputRups, inputName,
						compSol == null ? null : compRupSet, compSol, compRups, compName,
						distAzCalc, 0d, jumpDist, resourcesDir);
				table.addColumn("![Plausibility Filter]("+resourcesDir.getName()+"/"+plotFile.getName()+")");
			}
		}
		lines.add("");
		
		// now azimuths
		List<RakeType> rakeTypes = new ArrayList<>();
		rakeTypes.add(null);
		for (RakeType type : RakeType.values())
			rakeTypes.add(type);
		lines.add("## Jump Azimuths");
		lines.add(topLink); lines.add("");
		
		Table<RakeType, RakeType, List<Double>> inputRakeAzTable = calcJumpAzimuths(inputRups, distAzCalc);
		Table<RakeType, RakeType, List<Double>> compRakeAzTable = null;
		if (compRups != null)
			compRakeAzTable = calcJumpAzimuths(compRups, distAzCalc);
		
		for (RakeType sourceType : rakeTypes) {
			String prefix, title;
			if (sourceType == null) {
				prefix = "jump_az_any";
				title = "Jumps from Any";
				lines.add("### Jump Azimuths From Any");
			} else {
				prefix = "jump_az_"+sourceType.prefix;
				title = "Jumps from "+sourceType.name;
				lines.add("### Jump Azimuths From "+sourceType.name);
			}
			
			System.out.println("Plotting "+title);

			lines.add(topLink); lines.add("");
			
			table = MarkdownUtils.tableBuilder();
			if (compRups != null)
				table.addLine(inputName, compName);
			
			table.initNewLine();
			File plotFile = plotJumpAzimuths(sourceType, rakeTypes, inputRakeAzTable,
					resourcesDir, prefix, title);
			table.addColumn("!["+title+"](resources/"+plotFile.getName()+")");
			if (compRups != null) {
				plotFile = plotJumpAzimuths(sourceType, rakeTypes, compRakeAzTable,
						resourcesDir, prefix+"_comp", title);
				table.addColumn("!["+title+"](resources/"+plotFile.getName()+")");
			}
			table.finalizeLine();
			lines.addAll(table.build());
			lines.add("");
			
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			
			for (RakeType destType : rakeTypes) {
				String myPrefix = prefix+"_";
				String myTitle = title+" to ";
				if (destType == null) {
					myPrefix += "any";
					myTitle += "Any";
				} else {
					myPrefix += destType.prefix;
					myTitle += destType.name;
				}
				
				plotFile = plotJumpAzimuthsRadial(sourceType, destType, inputRakeAzTable,
						resourcesDir, myPrefix, myTitle);
				table.addColumn("!["+title+"](resources/"+plotFile.getName()+")");
			}
			table.finalizeLine();
			lines.addAll(table.wrap(3, 0).build());
			lines.add("");
		}
		
		// add TOC
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2));
		lines.add(tocIndex, "## Table Of Contents");
		
		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
		
		if (numAzCached < distAzCalc.getCachedAzimuths().size()
				|| numDistCached < distAzCalc.getCachedDistances().size()) {
			System.out.println("Writing dist/az cache to "+distAzCache.getAbsolutePath());
			distAzCalc.writeCacheFile(distAzCache);
		}
	}
	
	private static void addTablePlots(TableBuilder table, File mainPlot, File compPlot,
			boolean hasComp) {
		table.initNewLine();
		if (mainPlot.exists())	
			table.addColumn("![plot]("+mainPlot.getParentFile().getName()
					+"/"+mainPlot.getName()+")");
		else
			table.addColumn("*N/A*");
		if (hasComp) {
			if (compPlot.exists())
				table.addColumn("![plot]("+compPlot.getParentFile().getName()
						+"/"+compPlot.getName()+")");
			else
				table.addColumn("*N/A*");
		}
		table.finalizeLine();
	}
	
	private static final Color MAIN_COLOR = Color.RED;
	private static final Color COMP_COLOR = Color.BLUE;
	private static final Color COMMON_COLOR = Color.GREEN;

	private static DecimalFormat twoDigits = new DecimalFormat("0.00");
	private static DecimalFormat thousands = new DecimalFormat("0");
	static {
		thousands.getDecimalFormatSymbols().setGroupingSeparator(',');
	}
	
	private static double getLength(FaultSystemRupSet rupSet, int r) {
		double[] lengths = rupSet.getLengthForAllRups();
		if (lengths == null) {
			// calculate it
			double len = 0d;
			for (FaultSection sect : rupSet.getFaultSectionDataForRupture(r))
				len += sect.getTraceLength();
			return len;
		}
		return lengths[r]*1e-3; // m => km
	}
	
	private static List<String> getBasicLines(FaultSystemRupSet rupSet) {
		List<String> lines = new ArrayList<>();
		MinMaxAveTracker magTrack = new MinMaxAveTracker();
		MinMaxAveTracker lenTrack = new MinMaxAveTracker();
		MinMaxAveTracker sectsTrack = new MinMaxAveTracker();
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			magTrack.addValue(rupSet.getMagForRup(r));
			lenTrack.addValue(getLength(rupSet, r));
			sectsTrack.addValue(rupSet.getSectionsIndicesForRup(r).size());
		}
		lines.add("* Num ruptures: "+thousands.format(rupSet.getNumRuptures()));
		lines.add("* Rupture mag range: ["+twoDigits.format(magTrack.getMin())
		+","+twoDigits.format(magTrack.getMax())+"]");
		lines.add("* Rupture length range: ["+twoDigits.format(lenTrack.getMin())
		+","+twoDigits.format(lenTrack.getMax())+"] km");
		lines.add("* Rupture sect count range: ["+(int)sectsTrack.getMin()
			+","+(int)sectsTrack.getMax()+"]");
		return lines;
	}
	
	private static List<String> getPlausibilityLines(PlausibilityConfiguration config) {
		List<String> lines = new ArrayList<>();
		
		ClusterConnectionStrategy connStrat = config.getConnectionStrategy();
		MinMaxAveTracker parentConnTrack = new MinMaxAveTracker();
		HashSet<Integer> parentIDs = new HashSet<>();
		for (FaultSection sect : connStrat.getSubSections())
			parentIDs.add(sect.getParentSectionId());
		int totNumConnections = 0;
		for (int parentID1 : parentIDs) {
			int myConnections = 0;
			for (int parentID2 : parentIDs) {
				if (parentID1 == parentID2)
					continue;
				if (connStrat.areParentSectsConnected(parentID1, parentID2))
					myConnections++;
			}
			parentConnTrack.addValue(myConnections);
			totNumConnections += myConnections;
		}
		int minConns = (int)parentConnTrack.getMin();
		int maxConns = (int)parentConnTrack.getMax();
		String avgConns = twoDigits.format(parentConnTrack.getAverage());
		totNumConnections /= 2; // remove duplicates
		lines.add("* Connection strategy: ");
		lines.add("    * Max jump dist: "+(float)connStrat.getMaxJumpDist()+" km");
		lines.add("    * Allowed parent-section connections:");
		lines.add("        * Total: "+totNumConnections);
		lines.add("        * Each: avg="+avgConns+", range=["+minConns+","+maxConns+"]");
		lines.add("* Max num splays: "+config.getMaxNumSplays());
		lines.add("* Filters:");
		for (PlausibilityFilter filter : config.getFilters())
			lines.add("    * "+filter.getName());
		
		return lines;
	}
	
	private static FaultModels getUCERF3FM(FaultSystemRupSet rupSet) {
		if (rupSet.getNumRuptures() == 253706)
			return FaultModels.FM3_1;
		if (rupSet.getNumRuptures() == 305709)
			return FaultModels.FM3_2;
		return null;
	}
	
	/*
	 * Conversion from subsection list to cluster ruptures
	 */
	
	public static List<ClusterRupture> buildClusterRups(FaultSystemRupSet rupSet,
			RuptureConnectionSearch search) {
		PlausibilityConfiguration config = rupSet.getPlausibilityConfiguration();
		System.out.println("Config null ? "+(config == null));
		if (config != null && config.getMaxNumSplays() == 0) {
			// if splays aren't allowed and we have a plausibility configuration, then simple strand ruptures
			System.out.println("Assuming simple single strand ruptures");
			List<ClusterRupture> rups = new ArrayList<>();
			
			for (int r=0; r<rupSet.getNumRuptures(); r++) {
				List<FaultSection> rupSects = rupSet.getFaultSectionDataForRupture(r);
//				System.out.println("rupture "+r);
				rups.add(ClusterRupture.forOrderedSingleStrandRupture(rupSects, search.getDistAzCalc()));
			}
			
			return rups;
		}
		ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		
		List<Future<ClusterRupture>> futures = new ArrayList<>();
		for (int r=0; r<rupSet.getNumRuptures(); r++)
			futures.add(exec.submit(new ClusterRupCalc(search, r)));
		
		List<ClusterRupture> ruptures = new ArrayList<>();
		
		for (int r=0; r<futures.size(); r++) {
			if (r % 1000 == 0)
				System.out.println("Calculating for rupture "+r+"/"+rupSet.getNumRuptures());
			Future<ClusterRupture> future = futures.get(r);
			try {
				ruptures.add(future.get());
			} catch (InterruptedException | ExecutionException e) {
				exec.shutdown();
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		System.out.println("Built "+ruptures.size()+" ruptures");
		
		exec.shutdown();
		Preconditions.checkState(ruptures.size() == rupSet.getNumRuptures());
		
		return ruptures;
	}
	
	private static class ClusterRupCalc implements Callable<ClusterRupture> {
		
		private RuptureConnectionSearch search;
		private int rupIndex;

		public ClusterRupCalc(RuptureConnectionSearch search, int rupIndex) {
			this.search = search;
			this.rupIndex = rupIndex;
		}

		@Override
		public ClusterRupture call() throws Exception {
			return search.buildClusterRupture(rupIndex, false);
		}
		
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
	
	public static void plotRuptureHistograms(File outputDir, String prefix, TableBuilder table,
			FaultSystemRupSet inputRupSet, FaultSystemSolution inputSol,
			HashSet<UniqueRupture> inputUniques, FaultSystemRupSet compRupSet,
			FaultSystemSolution compSol, HashSet<UniqueRupture> compUniques, boolean length)
			throws IOException {
		File main = plotRuptureHistogram(outputDir, prefix, inputRupSet, null,
				compUniques, MAIN_COLOR, length);
		table.initNewLine();
		table.addColumn("![hist]("+outputDir.getName()+"/"+main.getName()+")");
		if (compRupSet != null) {
			File comp = plotRuptureHistogram(outputDir, prefix+"_comp", compRupSet, null,
					inputUniques, COMP_COLOR, length);
			table.addColumn("![hist]("+outputDir.getName()+"/"+comp.getName()+")");
		}
		table.finalizeLine();
		if (inputSol != null || compSol != null) {
			table.initNewLine();
			
			if (inputSol != null) {
				main = plotRuptureHistogram(outputDir, prefix+"_rates", inputRupSet, inputSol,
						compUniques, MAIN_COLOR, length);
				table.addColumn("![hist]("+outputDir.getName()+"/"+main.getName()+")");
			} else {
				table.addColumn("*N/A*");
			}
			if (compSol != null) {
				File comp = plotRuptureHistogram(outputDir, prefix+"_comp_rates", compRupSet,
						compSol, inputUniques, COMP_COLOR, length);
				table.addColumn("![hist]("+outputDir.getName()+"/"+comp.getName()+")");
			} else {
				table.addColumn("*N/A*");
			}
			
			table.finalizeLine();
		}
	}
	
	public static File plotRuptureHistogram(File outputDir, String prefix,
			FaultSystemRupSet rupSet, FaultSystemSolution sol, HashSet<UniqueRupture> compRups,
			Color color, boolean length) throws IOException {
		List<Double> scalars = new ArrayList<>();
		MinMaxAveTracker track = new MinMaxAveTracker();
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			double scalar;
			if (length)
				scalar = getLength(rupSet, r);
			else
				scalar = rupSet.getMagForRup(r);
			scalars.add(scalar);
			track.addValue(scalar);
		}
		HistogramFunction hist;
		if (length)
			hist = HistogramFunction.getEncompassingHistogram(0d, track.getMax(), 50d);
		else
			hist = HistogramFunction.getEncompassingHistogram(Math.floor(track.getMin()),
					Math.ceil(track.getMax()), 0.1d);
		HistogramFunction commonHist = null;
		if (compRups != null)
			commonHist = new HistogramFunction(hist.getMinX(), hist.getMaxX(), hist.size());
		
		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			double scalar = scalars.get(r);
			double y = sol == null ? 1 : sol.getRateForRup(r);
			hist.add(hist.getClosestXIndex(scalar), y);
			if (compRups != null && compRups.contains(
					UniqueRupture.forIDs(rupSet.getSectionsIndicesForRup(r)))) {
				commonHist.add(hist.getClosestXIndex(scalar), y);
			}
		}
		
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		hist.setName("All Ruptures");
		funcs.add(hist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, color));
		
		if (commonHist != null) {
			commonHist.setName("Common To Both");
			funcs.add(commonHist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, COMMON_COLOR));
		}
		
		String title;
		String xAxisLabel;
		if (length) {
			title = "Rupture Length Histogram";
			xAxisLabel = "Length (km)";
		} else {
			title = "Rupture Magnitude Histogram";
			xAxisLabel = "Magnitude";
		}
		String yAxisLabel = sol == null ? "Count" : "Annual Rate";
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
		spec.setLegendVisible(compRups != null);
		
		Range xRange = new Range(hist.getMinX() - 0.5*hist.getDelta(),
				hist.getMaxX() + 0.5*hist.getDelta());
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setLegendFontSize(22);
		
		gp.drawGraphPanel(spec, false, false, xRange, null);
		gp.getChartPanel().setSize(800, 600);
		File pngFile = new File(outputDir, prefix+".png");
		File pdfFile = new File(outputDir, prefix+".pdf");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsPDF(pdfFile.getAbsolutePath());
		return pngFile;
	}
	
	private static class ErrOnCantEvalAzFilter implements PlausibilityFilter {
		
		private PlausibilityFilter filter;
		private boolean endsOnly;

		private ErrOnCantEvalAzFilter(PlausibilityFilter filter, boolean endsOnly) {
			this.filter = filter;
			this.endsOnly = endsOnly;
		}

		@Override
		public String getShortName() {
			return filter.getShortName();
		}

		@Override
		public String getName() {
			return filter.getName();
		}

		@Override
		public PlausibilityResult apply(ClusterRupture rupture, boolean verbose) {
			PlausibilityResult result = filter.apply(rupture, verbose);
			if (result.isPass() || !result.canContinue())
				return result;
			if (endsOnly) {
				checkStrandEndsRecursive(rupture);
			} else {
				RuptureTreeNavigator navigator = rupture.getTreeNavigator();
				for (Jump jump : rupture.getJumpsIterable()) {
					if (navigator.getDescendants(jump.toSection).isEmpty())
						throw new IllegalStateException("Jump to single section. Rupture:\n"+rupture);
				}
			}
			return result;
		}
		
		private void checkStrandEndsRecursive(ClusterRupture rupture) {
			RuptureTreeNavigator navigator = rupture.getTreeNavigator();
			for (Jump jump : rupture.internalJumps)
				if (jump.toCluster == rupture.clusters[rupture.clusters.length-1])
					if (navigator.getDescendants(jump.toSection).isEmpty())
						throw new IllegalStateException("Jump to single section. Rupture:\n"+rupture);
			for (ClusterRupture splay : rupture.splays.values())
				checkStrandEndsRecursive(splay);
		}

		@Override
		public PlausibilityResult testJump(ClusterRupture rupture, Jump newJump, boolean verbose) {
			return apply(rupture.take(newJump), verbose);
		}
		
	}
	
	/*
	 * rupture plausibility testing
	 */
	
	public static RupSetPlausibilityResult testRupSetPlausibility(List<ClusterRupture> rups,
			List<PlausibilityFilter> filters) {
		int allPassCount = 0;
		int[] failCounts = new int[filters.size()];
		int[] failCanContinueCounts = new int[filters.size()];
		int[] onlyFailCounts = new int[filters.size()];
		int[] erredCounts = new int[filters.size()];
		
		List<PlausibilityFilter> newFilters = new ArrayList<>();
		for (PlausibilityFilter filter : filters) {
			if (filter instanceof JumpAzimuthChangeFilter)
				newFilters.add(new ErrOnCantEvalAzFilter(filter, false));
			else if (filter instanceof TotalAzimuthChangeFilter)
				newFilters.add(new ErrOnCantEvalAzFilter(filter, true));
			else
				newFilters.add(filter);
		}
		
		for (ClusterRupture rupture : rups) {
			boolean allPass = true;
			int onlyFailureIndex = -1;
			for (int t=0; t<newFilters.size(); t++) {
				PlausibilityFilter test = newFilters.get(t);
				PlausibilityResult result;
				boolean subPass;
				try {
					result = test.apply(rupture, false);
					if (result == PlausibilityResult.FAIL_FUTURE_POSSIBLE)
						failCanContinueCounts[t]++;
					subPass = result.isPass();
				} catch (Exception e) {
					if (erredCounts[t] == 0) {
						System.err.println("First exception for "+test.getName()+":");
						e.printStackTrace();
					}
					erredCounts[t] ++;
					subPass = true; // do not fail on error
				}
				if (!subPass && allPass) {
					// this is the first failure
					onlyFailureIndex = t;
				} else if (!subPass) {
					// failed more than 1
					onlyFailureIndex = -1;
				}
				allPass = subPass && allPass;
				if (!subPass)
					failCounts[t] ++;
			}
			if (allPass)
				allPassCount ++;
			if (onlyFailureIndex >= 0)
				onlyFailCounts[onlyFailureIndex] ++;
		}
		
		return new RupSetPlausibilityResult(filters, rups.size(), allPassCount, failCounts,
				failCanContinueCounts, onlyFailCounts, erredCounts);
	}
	
	public static class RupSetPlausibilityResult {
		public final List<PlausibilityFilter> filters;
		public final int numRuptures;
		public final int allPassCount;
		public final int[] failCounts;
		public final int[] failCanContinueCounts;
		public final int[] onlyFailCounts;
		public final int[] erredCounts;
		
		public RupSetPlausibilityResult(List<PlausibilityFilter> filters, int numRuptures, int allPassCount,
				int[] failCounts, int[] failCanContinueCounts, int[] onlyFailCounts, int[] erredCounts) {
			super();
			this.filters = filters;
			this.numRuptures = numRuptures;
			this.allPassCount = allPassCount;
			this.failCounts = failCounts;
			this.failCanContinueCounts = failCanContinueCounts;
			this.onlyFailCounts = onlyFailCounts;
			this.erredCounts = erredCounts;
		}
	}
	
	private static Color[] FILTER_COLORS = { Color.DARK_GRAY, new Color(102, 51, 0), Color.RED, Color.BLUE,
			Color.GREEN.darker(), Color.CYAN, Color.PINK, Color.ORANGE.darker(), Color.MAGENTA };
	
	public static File plotRupSetPlausibility(RupSetPlausibilityResult result, File outputDir,
			String prefix, String title) throws IOException {
		double dx = 1d;
		double buffer = 0.2*dx;
		double deltaEachSide = (dx - buffer)/2d;
		double maxY = 50;
		for (int i=0; i<result.filters.size(); i++) {
			int failCount = result.erredCounts[i]+result.failCounts[i];
			double percent = (100d)*failCount/(double)result.numRuptures;
			while (percent > maxY - 15d)
				maxY += 10;
		}

		Font font = new Font(Font.SANS_SERIF, Font.BOLD, 22);
		Font allFont = new Font(Font.SANS_SERIF, Font.BOLD, 26);
		
		List<PlotElement> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		funcs.add(new DefaultXY_DataSet(new double[] {0d, 1d}, new double[] {0d, 0d}));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 0f, Color.WHITE));
		
		List<XYAnnotation> anns = new ArrayList<>();
		
		double topRowY = maxY*0.95;
		double secondRowY = maxY*0.91;
		double thirdRowY = maxY*0.85;
		
		for (int i=0; i<result.filters.size(); i++) {
			double x = i*dx + 0.5*dx;
			double percentFailed = 100d*result.failCounts[i]/result.numRuptures;
			double percentOnly = 100d*result.onlyFailCounts[i]/result.numRuptures;
			double percentErred = 100d*result.erredCounts[i]/result.numRuptures;
			
			Color c = FILTER_COLORS[i % FILTER_COLORS.length];
			
			String name = result.filters.get(i).getShortName();
			
			if (percentErred > 0) {
//				funcs.add(vertLine(x, percentFailed, percentFailed + percentErred));
//				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, thickness, Color.LIGHT_GRAY));
				anns.add(emptyBox(x-deltaEachSide, 0d, x+deltaEachSide, percentFailed + percentErred,
						PlotLineType.DASHED, Color.LIGHT_GRAY, 2f));
				name += "*";
			}
			
//			funcs.add(vertLine(x, 0, percentFailed));
//			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, thickness, c));
			anns.add(filledBox(x-deltaEachSide, 0, x+deltaEachSide, percentFailed, c));
			
			if (percentOnly > 0) {
//				funcs.add(vertLine(x, 0, percentOnly));
//				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, thickness, darker(c)));
				anns.add(filledBox(x-deltaEachSide, 0, x+deltaEachSide, percentOnly, darker(c)));
			}
			
			XYTextAnnotation ann = new XYTextAnnotation(name, x, i % 2 == 0 ? secondRowY : thirdRowY);
			ann.setTextAnchor(TextAnchor.TOP_CENTER);
			ann.setPaint(c);
			ann.setFont(font);
			
			anns.add(ann);
			
			ann = new XYTextAnnotation(percentDF.format(percentFailed/100d), x, percentFailed+0.6);
			ann.setTextAnchor(TextAnchor.BOTTOM_CENTER);
			ann.setPaint(Color.BLACK);
			ann.setFont(font);
			
			anns.add(ann);
		}
		
		Range xRange = new Range(-0.15*dx, (result.filters.size()+0.15)*dx);
		
		XYTextAnnotation ann = new XYTextAnnotation(
				percentDF.format((double)result.allPassCount/result.numRuptures)+" passed all",
				xRange.getCentralValue(), topRowY);
		ann.setTextAnchor(TextAnchor.CENTER);
		ann.setPaint(Color.BLACK);
		ann.setFont(allFont);
		
		anns.add(ann);
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, " ", "Percent Failed");
		spec.setPlotAnnotations(anns);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		
		gp.drawGraphPanel(spec, false, false, xRange, new Range(0, maxY));
		gp.getXAxis().setTickLabelsVisible(false);
//		gp.getXAxis().setvisi
		gp.getChartPanel().setSize(1200, 600);
		File pngFile = new File(outputDir, prefix+".png");
		File pdfFile = new File(outputDir, prefix+".pdf");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsPDF(pdfFile.getAbsolutePath());
		return pngFile;
	}
	
	private static Color darker(Color c) {
		int r = c.getRed();
		int g = c.getGreen();
		int b = c.getBlue();
//		r += (255-r)/2;
//		g += (255-g)/2;
//		b += (255-b)/2;
		r /= 2;
		g /= 2;
		b /= 2;
		return new Color(r, g, b);
	}
	
	private static DefaultXY_DataSet vertLine(double x, double y0, double y1) {
		DefaultXY_DataSet line = new DefaultXY_DataSet();
		line.set(x, y0);
		line.set(x, y1);
		return line;
	}
	
	private static XYBoxAnnotation filledBox(double x0, double y0, double x1, double y1, Color c) {
		XYBoxAnnotation ann = new XYBoxAnnotation(x0, y0, x1, y1, null, null, c);
		return ann;
	}
	
	private static XYBoxAnnotation emptyBox(double x0, double y0, double x1, double y1,
			PlotLineType lineType, Color c, float thickness) {
		Stroke stroke = lineType.buildStroke(thickness);
		XYBoxAnnotation ann = new XYBoxAnnotation(x0, y0, x1, y1, stroke, c, null);
		return ann;
	}
	
	public static TableBuilder getRupSetPlausibilityTable(RupSetPlausibilityResult result) {
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("Filter", "Failed", "Only Failure", "Erred");
		for (int t=0; t<result.filters.size(); t++) {
			table.initNewLine();
			table.addColumn("**"+result.filters.get(t).getName()+"**");
			table.addColumn(countStats(result.failCounts[t], result.numRuptures));
			table.addColumn(countStats(result.onlyFailCounts[t], result.numRuptures));
			table.addColumn(countStats(result.erredCounts[t], result.numRuptures));
			table.finalizeLine();
		}
		return table;
	}
	
	private static final DecimalFormat percentDF = new DecimalFormat("0.00%");
	private static String countStats(int count, int tot) {
		return count+"/"+tot+" ("+percentDF.format((double)count/(double)tot)+")";
	}
	
	/*
	 * Rupture connections
	 */
	
	static void plotConnectivityLines(FaultSystemRupSet rupSet, File outputDir, String prefix, String title,
			Set<Jump> connections, Color connectedColor, Region reg, int width) throws IOException {
		List<Set<Jump>> connectionsList = new ArrayList<>();
		List<Color> connectedColors = new ArrayList<>();
		List<String> connNames = new ArrayList<>();
		
		connectionsList.add(connections);
		connectedColors.add(connectedColor);
		connNames.add("Connections");
		
		plotConnectivityLines(rupSet, outputDir, prefix, title, connectionsList, connectedColors, connNames, reg, width);
	}
	
	static void plotConnectivityLines(FaultSystemRupSet rupSet, File outputDir, String prefix, String title,
			List<Set<Jump>> connectionsList, List<Color> connectedColors, List<String> connNames,
			Region reg, int width) throws IOException {
		Color faultColor = Color.DARK_GRAY;
		Color faultOutlineColor = Color.LIGHT_GRAY;
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		if (reg instanceof RELM_TESTING) {
			// add ca outlines
			XY_DataSet[] outlines = PoliticalBoundariesData.loadCAOutlines();
			PlotCurveCharacterstics outlineChar = new PlotCurveCharacterstics(PlotLineType.SOLID, (float)1d, Color.GRAY);
			
			for (XY_DataSet outline : outlines) {
				funcs.add(outline);
				chars.add(outlineChar);
			}
		}
		
		List<Location> middles = new ArrayList<>();
		
		for (int s=0; s<rupSet.getNumSections(); s++) {
			FaultSection sect = rupSet.getFaultSectionData(s);
			RuptureSurface surf = sect.getFaultSurface(1d);
			
			XY_DataSet trace = new DefaultXY_DataSet();
			for (Location loc : surf.getEvenlyDiscritizedUpperEdge())
				trace.set(loc.getLongitude(), loc.getLatitude());
			
			if (sect.getAveDip() != 90d) {
				XY_DataSet outline = new DefaultXY_DataSet();
				LocationList perimeter = surf.getPerimeter();
				for (Location loc : perimeter)
					outline.set(loc.getLongitude(), loc.getLatitude());
				Location first = perimeter.first();
				outline.set(first.getLongitude(), first.getLatitude());
				
				funcs.add(0, outline);
				chars.add(0, new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, faultOutlineColor));
			}
			
			middles.add(GriddedSurfaceUtils.getSurfaceMiddleLoc(surf));
			
			if (s == 0)
				trace.setName("Fault Sections");
			
			funcs.add(trace);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, faultColor));
		}
		
		for (int i=0; i<connectionsList.size(); i++) {
			Set<Jump> connections = connectionsList.get(i);
			Color connectedColor = connectedColors.get(i);
			String connName = connNames.get(i);
			
			boolean first = true;
			for (Jump connection : connections) {
				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				
				if (first) {
					xy.setName(connName);
					first = false;
				}
				
				Location loc1 = middles.get(connection.fromSection.getSectionId());
				Location loc2 = middles.get(connection.toSection.getSectionId());
				
				xy.set(loc1.getLongitude(), loc1.getLatitude());
				xy.set(loc2.getLongitude(), loc2.getLatitude());
				
				funcs.add(xy);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, connectedColor));
			}
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Longitude", "Latitude");
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(24);
		gp.setBackgroundColor(Color.WHITE);
		
		Range xRange = new Range(reg.getMinLon(), reg.getMaxLon());
		Range yRange = new Range(reg.getMinLat(), reg.getMaxLat());
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		double tick = 2d;
		TickUnits tus = new TickUnits();
		TickUnit tu = new NumberTickUnit(tick);
		tus.add(tu);
		gp.getXAxis().setStandardTickUnits(tus);
		gp.getYAxis().setStandardTickUnits(tus);
		
		File file = new File(outputDir, prefix);
		double aspectRatio = yRange.getLength() / xRange.getLength();
		gp.getChartPanel().setSize(width, 200 + (int)((width-200d)*aspectRatio));
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
	}
	
	static void plotConnectivityHistogram(File outputDir, String prefix, String title,
			Map<Jump, Double> connections, Map<Jump, Double> uniqueConnections,
			double maxDist, Color connectedColor, boolean rateWeighted, boolean yLog)
					throws IOException {
		double delta = 1d;
//		if (maxDist > 90)
//			delta = 5d;
//		else if (maxDist > 40)
//			delta = 2;
//		else if (maxDist > 20)
//			delta = 1d;
//		else
//			delta = 0.5d;
		
		HistogramFunction hist = HistogramFunction.getEncompassingHistogram(0d, maxDist, delta);
		hist.setName("All Connections");
		HistogramFunction uniqueHist = HistogramFunction.getEncompassingHistogram(0d, maxDist, delta);
		uniqueHist.setName("Unique To Model");
		
		double myMax = 0d;
		double mean = 0d;
		double sumWeights = 0d;
		double meanAbove = 0d;
		double sumWeightsAbove = 0d;
		
		for (Jump pair : connections.keySet()) {
			double dist = pair.distance;
			double weight = rateWeighted ? connections.get(pair) : 1d;
			
			myMax = Math.max(myMax, dist);
			mean += dist*weight;
			sumWeights += weight;
			if (dist >= 0.1) {
				meanAbove += dist*weight;
				sumWeightsAbove += weight;
			}
			
			int xIndex = hist.getClosestXIndex(dist);
			hist.add(xIndex, weight);
			if (uniqueConnections.containsKey(pair))
				uniqueHist.add(xIndex, weight);
		}

		mean /= sumWeights;
		meanAbove /= sumWeightsAbove;
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		Color uniqueColor = new Color(connectedColor.getRed()/4,
				connectedColor.getGreen()/4, connectedColor.getBlue()/4);
		
		funcs.add(hist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, connectedColor));
		
		funcs.add(uniqueHist);
		chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, uniqueColor));
		
		String yAxisLabel = rateWeighted ? "Annual Rate" : "Count";
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Jump Distance (km)", yAxisLabel);
		spec.setLegendVisible(true);
		
		Range xRange = new Range(0d, maxDist);
		Range yRange;
		if (yLog) {
//			double minNonZero = Double.POSITIVE_INFINITY;
//			for (Point2D pt : hist)
//				if (pt.getY() > 0)
//					minNonZero = Math.min(minNonZero, pt.getY());
//			double minY = Math.pow(10, Math.floor(Math.log10(minNonZero)));
//			if (!Double.isFinite(minY) || minY < 1e-8)
//				minY = 1e-8;
//			double maxY = Math.max(1e-1, Math.pow(10, Math.ceil(Math.log10(hist.getMaxY()))));
//			yRange = new Range(minY, maxY);
			yRange = new Range(1e-6, 1e1);
		} else {
			yRange = new Range(0d, 1.05*hist.getMaxY());
		}
		
		DecimalFormat distDF = new DecimalFormat("0.0");
		double annX = 0.975*maxDist;
		Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, 20);
		
		double annYScalar = 0.975;
		double annYDelta = 0.05;
		
		double logMinY = Math.log10(yRange.getLowerBound());
		double logMaxY = Math.log10(yRange.getUpperBound());
		double logDeltaY = logMaxY - logMinY;
		
		double annY;
		if (yLog)
			annY = Math.pow(10, logMinY + logDeltaY*annYScalar);
		else
			annY = annYScalar*yRange.getUpperBound();
		XYTextAnnotation maxAnn = new XYTextAnnotation(
				"Max: "+distDF.format(myMax), annX, annY);
		maxAnn.setFont(annFont);
		maxAnn.setTextAnchor(TextAnchor.TOP_RIGHT);
		spec.addPlotAnnotation(maxAnn);
		
		annYScalar -= annYDelta;
		if (yLog)
			annY = Math.pow(10, logMinY + logDeltaY*annYScalar);
		else
			annY = annYScalar*yRange.getUpperBound();
		XYTextAnnotation meanAnn = new XYTextAnnotation(
				"Mean: "+distDF.format(mean), annX, annY);
		meanAnn.setFont(annFont);
		meanAnn.setTextAnchor(TextAnchor.TOP_RIGHT);
		spec.addPlotAnnotation(meanAnn);
		
		annYScalar -= annYDelta;
		if (yLog)
			annY = Math.pow(10, logMinY + logDeltaY*annYScalar);
		else
			annY = annYScalar*yRange.getUpperBound();
		if (rateWeighted) {
			XYTextAnnotation rateAnn = new XYTextAnnotation(
					"Total Rate: "+distDF.format(sumWeights), annX, annY);
			rateAnn.setFont(annFont);
			rateAnn.setTextAnchor(TextAnchor.TOP_RIGHT);
			spec.addPlotAnnotation(rateAnn);
		} else {
			XYTextAnnotation countAnn = new XYTextAnnotation(
					"Total Count: "+(int)sumWeights, annX, annY);
			countAnn.setFont(annFont);
			countAnn.setTextAnchor(TextAnchor.TOP_RIGHT);
			spec.addPlotAnnotation(countAnn);
		}
		
		annYScalar -= annYDelta;
		if (yLog)
			annY = Math.pow(10, logMinY + logDeltaY*annYScalar);
		else
			annY = annYScalar*yRange.getUpperBound();
		XYTextAnnotation meanAboveAnn = new XYTextAnnotation(
				"Mean >0.1: "+distDF.format(meanAbove), annX, annY);
		meanAboveAnn.setFont(annFont);
		meanAboveAnn.setTextAnchor(TextAnchor.TOP_RIGHT);
		spec.addPlotAnnotation(meanAboveAnn);
		
		annYScalar -= annYDelta;
		if (yLog)
			annY = Math.pow(10, logMinY + logDeltaY*annYScalar);
		else
			annY = annYScalar*yRange.getUpperBound();
		if (rateWeighted) {
			XYTextAnnotation rateAnn = new XYTextAnnotation(
					"Total Rate >0.1: "+distDF.format(sumWeightsAbove), annX, annY);
			rateAnn.setFont(annFont);
			rateAnn.setTextAnchor(TextAnchor.TOP_RIGHT);
			spec.addPlotAnnotation(rateAnn);
		} else {
			XYTextAnnotation countAnn = new XYTextAnnotation(
					"Total Count >0.1: "+(int)sumWeightsAbove, annX, annY);
			countAnn.setFont(annFont);
			countAnn.setTextAnchor(TextAnchor.TOP_RIGHT);
			spec.addPlotAnnotation(countAnn);
		}
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(24);
		gp.setBackgroundColor(Color.WHITE);
		
		gp.drawGraphPanel(spec, false, yLog, xRange, yRange);
		
		File file = new File(outputDir, prefix);
		gp.getChartPanel().setSize(800, 650);
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		gp.saveAsPNG(file.getAbsolutePath()+".png");
	}
	
	private static Map<Jump, Double> getJumps(FaultSystemSolution sol, List<ClusterRupture> ruptures,
			Map<Jump, List<Integer>> jumpToRupsMap) {
		Map<Jump, Double> jumpRateMap = new HashMap<>();
		for (int r=0; r<ruptures.size(); r++) {
			double rate = sol == null ? 1d : sol.getRateForRup(r);
			ClusterRupture rupture = ruptures.get(r);
			for (Jump jump : rupture.getJumpsIterable()) {
				if (jump.fromSection.getSectionId() > jump.toSection.getSectionId())
					jump = jump.reverse();
				Double prevRate = jumpRateMap.get(jump);
				if (prevRate == null)
					prevRate = 0d;
				jumpRateMap.put(jump, prevRate + rate);
				if (jumpToRupsMap != null) {
					List<Integer> prevRups = jumpToRupsMap.get(jump);
					if (prevRups == null) {
						prevRups = new ArrayList<>();
						jumpToRupsMap.put(jump, prevRups);
					}
					prevRups.add(r);
				}
			}
		}
		return jumpRateMap;
	}
	
	private static TableBuilder plotConnRupExamples(RuptureConnectionSearch search, Set<Jump> pairings,
			Map<Jump, List<Integer>> pairRupsMap, int maxRups, int maxCols,
			File resourcesDir, String prefix) throws IOException {
		List<Jump> sortedPairings = new ArrayList<>(pairings);
		Collections.sort(sortedPairings, Jump.id_comparator);
		
		Random r = new Random(sortedPairings.size()*maxRups);
		Collections.shuffle(sortedPairings, r);
		
		int possibleRups = 0;
		for (Jump pair : pairings)
			possibleRups += pairRupsMap.get(pair).size();
		if (possibleRups < maxRups)
			maxRups = possibleRups;
		if (maxRups == 0)
			return null;
		
		int indInPairing = 0;
		List<Integer> rupsToPlot = new ArrayList<>();
		while (rupsToPlot.size() < maxRups) {
			for (Jump pair : sortedPairings) {
				List<Integer> rups = pairRupsMap.get(pair);
				if (rups.size() > indInPairing) {
					rupsToPlot.add(rups.get(indInPairing));
					if (rupsToPlot.size() == maxRups)
						break;
				}
			}
			indInPairing++;
		}
		
		System.out.println("Plotting "+rupsToPlot+" ruptures");
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		for (int rupIndex : rupsToPlot) {
			String rupPrefix = prefix+"_"+rupIndex;
			search.plotConnections(resourcesDir, rupPrefix, rupIndex, pairings, "Unique Connections");
			table.addColumn("![Rupture "+rupIndex+"]("
					+resourcesDir.getName()+"/"+rupPrefix+".png)");
		}
		table.finalizeLine();
		return table.wrap(maxCols, 0);
	}
	
	private static File plotFixedJumpDist(FaultSystemRupSet inputRupSet, FaultSystemSolution inputSol,
			List<ClusterRupture> inputClusterRups, String inputName, FaultSystemRupSet compRupSet,
			FaultSystemSolution compSol, List<ClusterRupture> compClusterRups, String compName,
			SectionDistanceAzimuthCalculator distAzCalc, double minMag, float jumpDist, File outputDir)
					throws IOException {
		
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();

		if (inputRupSet != null) {
			DiscretizedFunc func = calcJumpDistFunc(inputRupSet, inputSol, inputClusterRups, minMag, jumpDist);
			func.scale(1d/func.calcSumOfY_Vals());
			funcs.add(func);
			
			func.setName(inputName);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, MAIN_COLOR));
		}
		
		if (compSol != null) {
			DiscretizedFunc compFunc = calcJumpDistFunc(compRupSet, compSol, compClusterRups, minMag, jumpDist);
			compFunc.scale(1d/compFunc.calcSumOfY_Vals());
			compFunc.setName(compName);
			funcs.add(compFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, COMP_COLOR));
		}
		
		String title;
		String xAxisLabel = "Num Jumps ≥"+(float)jumpDist+" km";
		String yAxisLabel;
		if (minMag > 0d) {
			title = "M≥"+(float)minMag+" "+(float)jumpDist+" km Jump Comparison";
		} else {
			title = (float)jumpDist+" km Jump Comparison";
		}
		Range yRange = null;
		String prefixAdd;
		if (inputSol != null || compSol != null) {
			yAxisLabel = "Fraction (Rate-Weighted)";
			yRange = new Range(0d, 1d);
			prefixAdd = "_rates";
		} else {
			yAxisLabel = "Count";
			prefixAdd = "_counts";
		}
		PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
//				"Num Jumps ≥"+(float)jumpDist+"km", "Fraction (Rate-Weighted)");
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		
		String prefix = new File(outputDir, "jumps_"+(float)jumpDist+"km"+prefixAdd).getAbsolutePath();
		
		gp.drawGraphPanel(spec, false, false, null, yRange);
		TickUnits tus = new TickUnits();
		TickUnit tu = new NumberTickUnit(1d);
		tus.add(tu);
		gp.getXAxis().setStandardTickUnits(tus);
		gp.getChartPanel().setSize(1000, 500);
		gp.saveAsPNG(prefix+".png");
		gp.saveAsPDF(prefix+".pdf");
		gp.saveAsTXT(prefix+".txt");
		return new File(prefix+".png");
	}
	
	private static DiscretizedFunc calcJumpDistFunc(FaultSystemRupSet rupSet, FaultSystemSolution sol,
			List<ClusterRupture> clusterRups, double minMag, float jumpDist) {
		EvenlyDiscretizedFunc solFunc = new EvenlyDiscretizedFunc(0d, 5, 1d);

		for (int r=0; r<rupSet.getNumRuptures(); r++) {
			double mag = rupSet.getMagForRup(r);

			if (mag < minMag)
				continue;
			
			ClusterRupture rup = clusterRups.get(r);
			int jumpsOverDist = 0;
			for (Jump jump : rup.getJumpsIterable()) {
				if ((float)jump.distance > jumpDist)
					jumpsOverDist++;
			}

			double rate = sol == null ? 1d : sol.getRateForRup(r);
			
			// indexes are fine to use here since it starts at zero with a delta of one 
			if (jumpsOverDist < solFunc.size())
				solFunc.set(jumpsOverDist, solFunc.getY(jumpsOverDist) + rate);
		}
		
		return solFunc;
	}
	
	private enum RakeType {
		RIGHT_LATERAL("Right-Lateral SS", "rl", Color.RED.darker()) {
			@Override
			public boolean isMatch(double rake) {
				return (float)rake >= -180f && (float)rake <= -170f
						|| (float)rake <= 180f && (float)rake >= 170f;
			}
		},
		LEFT_LATERAL("Left-Lateral SS", "ll", Color.GREEN.darker()) {
			@Override
			public boolean isMatch(double rake) {
				return (float)rake >= -10f && (float)rake <= 10f;
			}
		},
		REVERSE("Reverse", "rev", Color.BLUE.darker()) {
			@Override
			public boolean isMatch(double rake) {
				return (float)rake >= 80f && (float)rake <= 100f;
			}
		},
		NORMAL("Normal", "norm", Color.YELLOW.darker()) {
			@Override
			public boolean isMatch(double rake) {
				return (float)rake >= -100f && (float)rake <= -80f;
			}
		},
		OBLIQUE("Oblique", "oblique", Color.MAGENTA.darker()) {
			@Override
			public boolean isMatch(double rake) {
				for (RakeType type : values())
					if (type != this && type.isMatch(rake))
						return false;
				return true;
			}
		};
		
		private String name;
		private String prefix;
		private Color color;

		private RakeType(String name, String prefix, Color color) {
			this.name = name;
			this.prefix = prefix;
			this.color = color;
		}
		
		public abstract boolean isMatch(double rake);
	}
	
	private static Table<RakeType, RakeType, List<Double>> calcJumpAzimuths(
			List<ClusterRupture> rups, SectionDistanceAzimuthCalculator distAzCalc) {
		AzimuthCalc azCalc = new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc);
		Table<RakeType, RakeType, List<Double>> ret = HashBasedTable.create();
		for (RakeType r1 : RakeType.values())
			for (RakeType r2 : RakeType.values())
				ret.put(r1, r2, new ArrayList<>());
		for (ClusterRupture rup : rups) {
			RuptureTreeNavigator navigator = rup.getTreeNavigator();
			for (Jump jump : rup.getJumpsIterable()) {
				RakeType sourceRake = null, destRake = null;
				for (RakeType type : RakeType.values()) {
					if (type.isMatch(jump.fromSection.getAveRake()))
						sourceRake = type;
					if (type.isMatch(jump.toSection.getAveRake()))
						destRake = type;
				}
				Preconditions.checkNotNull(sourceRake);
				Preconditions.checkNotNull(destRake);
				FaultSection before1 = navigator.getPredecessor(jump.fromSection);
				if (before1 == null)
					continue;
				FaultSection before2 = jump.fromSection;
				double beforeAz = azCalc.calcAzimuth(before1, before2);
				FaultSection after1 = jump.toSection;
				for (FaultSection after2 : navigator.getDescendants(after1)) {
					double afterAz = azCalc.calcAzimuth(after1, after2);
					double rawDiff = JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz);
					Preconditions.checkState(rawDiff >= -180 && rawDiff <= 180);
					double[] azDiffs;
					if ((float)before2.getAveDip() == 90f) {
						// strike slip, include both directions
						azDiffs = new double[] { rawDiff, -rawDiff };
					} else {
						// follow the aki & richards convention
						double dipDir = before2.getDipDirection();
						double dipDirDiff = JumpAzimuthChangeFilter.getAzimuthDifference(dipDir, beforeAz);
						if (dipDirDiff < 0)
							// this means that the fault dips to the right of beforeAz, we're good
							azDiffs = new double[] { rawDiff };
						else
							// this means that the fault dips to the left of beforeAz, flip it
							azDiffs = new double[] { -rawDiff };
					}
					for (double azDiff : azDiffs)
						ret.get(sourceRake, destRake).add(azDiff);
				}
			}
		}
		return ret;
	}
	
	private static Map<RakeType, List<Double>> getAzimuthsFrom (RakeType sourceRake,
			Table<RakeType, RakeType, List<Double>> azTable) {
		Map<RakeType, List<Double>> azMap;
		if (sourceRake == null) {
			azMap = new HashMap<>();
			for (RakeType type : RakeType.values())
				azMap.put(type, new ArrayList<>());
			for (RakeType source : RakeType.values()) {
				Map<RakeType, List<Double>> row = azTable.row(source);
				for (RakeType dest : row.keySet()) 
					azMap.get(dest).addAll(row.get(dest));
			}
		} else {
			azMap = azTable.row(sourceRake);
		}
		return azMap;
	}
	
	private static File plotJumpAzimuths(RakeType sourceRake, List<RakeType> destRakes,
			Table<RakeType, RakeType, List<Double>> azTable,
			File outputDir, String prefix, String title) throws IOException {
		Map<RakeType, List<Double>> azMap = getAzimuthsFrom(sourceRake, azTable);
		
		Range xRange = new Range(-180d, 180d);
		List<Range> xRanges = new ArrayList<>();
		xRanges.add(xRange);
		
		List<Range> yRanges = new ArrayList<>();
		List<PlotSpec> specs = new ArrayList<>();
		
		for (int i=0; i<destRakes.size(); i++) {
			RakeType destRake = destRakes.get(i);
			
			HistogramFunction hist = HistogramFunction.getEncompassingHistogram(-179d, 179d, 15d);
			for (RakeType oRake : azMap.keySet()) {
				if (destRake != null && destRake != oRake)
					continue;
				for (double azDiff : azMap.get(oRake)) {
					hist.add(hist.getClosestXIndex(azDiff), 1d);
				}
			}

			Color color;
			String label;
			if (destRake == null) {
				color = Color.DARK_GRAY;
				label = "Any";
			} else {
				color = destRake.color;
				label = destRake.name;
			}
			
			List<XY_DataSet> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			funcs.add(hist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, color));
			
			double maxY = Math.max(1.1*hist.getMaxY(), 1d);
			Range yRange = new Range(0d, maxY);
			
			PlotSpec spec = new PlotSpec(funcs, chars, title, "Azimuthal Difference", "Count");
			
			XYTextAnnotation ann = new XYTextAnnotation("To "+label, 175, maxY*0.975);
			ann.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
			ann.setTextAnchor(TextAnchor.TOP_RIGHT);
			spec.addPlotAnnotation(ann);
			
			specs.add(spec);
			yRanges.add(yRange);
		}
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(24);
		gp.setBackgroundColor(Color.WHITE);
		
		gp.drawGraphPanel(specs, false, false, xRanges, yRanges);
		
		File file = new File(outputDir, prefix+".png");
		gp.getChartPanel().setSize(700, 1000);
		gp.saveAsPNG(file.getAbsolutePath());
		return file;
	}
	
	private static double azDiffDegreesToAngleRad(double azDiff) {
		// we want zero to be up, 90 to be right, 180 to be down, -90 to be left
		// sin/cos convention is zero at the right, 90 up, 180 left, -90 down
		
		Preconditions.checkState((float)azDiff >= (float)-180f && (float)azDiff <= 180f,
				"Bad azDiff: %s", azDiff);
		// first mirror it
		azDiff *= -1;
		// now rotate 90 degrees
		azDiff += 90d;
		
		return Math.toRadians(azDiff);
	}
	
	private static File plotJumpAzimuthsRadial(RakeType sourceRake, RakeType destRake,
			Table<RakeType, RakeType, List<Double>> azTable,
			File outputDir, String prefix, String title) throws IOException {
		System.out.println("Plotting "+title);
		Map<RakeType, List<Double>> azMap = getAzimuthsFrom(sourceRake, azTable);
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		Map<Float, List<Color>> azColorMap = new HashMap<>();
		
		HistogramFunction hist = HistogramFunction.getEncompassingHistogram(-179d, 179d, 15d);
		long totCount = 0;
		for (RakeType oRake : azMap.keySet()) {
			if (destRake != null && destRake != oRake)
				continue;
			for (double azDiff : azMap.get(oRake)) {
				hist.add(hist.getClosestXIndex(azDiff), 1d);
				
				Float azFloat = (float)azDiff;
				List<Color> colors = azColorMap.get(azFloat);
				if (colors == null) {
					colors = new ArrayList<>();
					azColorMap.put(azFloat, colors);
				}
				colors.add(oRake.color);
				totCount++;
			}
		}
		
		System.out.println("Have "+azColorMap.size()+" unique azimuths, "+totCount+" total");
//		Random r = new Random(azColorMap.keySet().size());
		double alphaEach = 0.025;
		if (totCount > 0)
			alphaEach = Math.max(alphaEach, 1d/totCount);
		for (Float azFloat : azColorMap.keySet()) {
			double sumRed = 0d;
			double sumGreen = 0d;
			double sumBlue = 0d;
			double sumAlpha = 0;
			int count = 0;
			for (Color azColor : azColorMap.get(azFloat)) {
				sumRed += azColor.getRed();
				sumGreen += azColor.getGreen();
				sumBlue += azColor.getBlue();
				if (sumAlpha < 1d)
					sumAlpha += alphaEach;
				count++;
			}
			double red = sumRed/(double)count;
			double green = sumGreen/(double)count;
			double blue = sumBlue/(double)count;
			if (red > 1d)
				red = 1d;
			if (green > 1d)
				green = 1d;
			if (blue > 1d)
				blue = 1d;
			if (sumAlpha > 1d)
				sumAlpha = 1d;
			Color color = new Color((float)red, (float)green, (float)blue, (float)sumAlpha);
//			if (destRake == null) {
//				// multipe types, choose a random color sampled from the actual colors
//				// for this azimuth
//				List<Color> colorList = azColorMap.get(azFloat);
//				color = colorList.get(r.nextInt(colorList.size()));
//			} else {
//				color = destRake.color;
//			}
			
			DefaultXY_DataSet line = new DefaultXY_DataSet();
			line.set(0d, 0d);
			double azRad = azDiffDegreesToAngleRad(azFloat);
			double x = Math.cos(azRad);
			double y = Math.sin(azRad);
			line.set(x, y);
			
			funcs.add(line);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, color));
		}
		
		double dip;
		if (sourceRake == RakeType.LEFT_LATERAL || sourceRake == RakeType.RIGHT_LATERAL)
			dip = 90d;
		else if (sourceRake == RakeType.NORMAL || sourceRake == RakeType.REVERSE)
			dip = 60d;
		else
			dip = 75d;
		
		double traceLen = 0.5d;
		double lowerDepth = 0.25d;
		if (dip < 90d) {
			// add surface
			
			double horzWidth = lowerDepth/Math.tan(Math.toRadians(dip));
			DefaultXY_DataSet outline = new DefaultXY_DataSet();
			outline.set(0d, 0d);
			outline.set(horzWidth, 0d);
			outline.set(horzWidth, -traceLen);
			outline.set(0d, -traceLen);
			
			funcs.add(outline);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.GRAY));
		}
		
		DefaultXY_DataSet trace = new DefaultXY_DataSet();
		trace.set(0d, 0d);
		trace.set(0d, -traceLen);
		
		funcs.add(trace);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 6f, Color.BLACK));
		PlotSpec spec = new PlotSpec(funcs, chars, title, "", " ");
		
		CPT cpt = GMT_CPT_Files.BLACK_RED_YELLOW_UNIFORM.instance().reverse();
		cpt = cpt.rescale(2d*Float.MIN_VALUE, 0.25d);
		cpt.setBelowMinColor(Color.WHITE);
		double halfDelta = 0.5*hist.getDelta();
		double innerMult = 0.95;
		double outerMult = 1.05;
		double sumY = Math.max(1d, hist.calcSumOfY_Vals());
		for (int i=0; i<hist.size(); i++) {
			double centerAz = hist.getX(i);
			double startAz = azDiffDegreesToAngleRad(centerAz-halfDelta);
			double endAz = azDiffDegreesToAngleRad(centerAz+halfDelta);
			
			List<Point2D> points = new ArrayList<>();
			
			double startX = Math.cos(startAz);
			double startY = Math.sin(startAz);
			double endX = Math.cos(endAz);
			double endY = Math.sin(endAz);
			
			points.add(new Point2D.Double(innerMult*startX, innerMult*startY));
			points.add(new Point2D.Double(outerMult*startX, outerMult*startY));
			points.add(new Point2D.Double(outerMult*endX, outerMult*endY));
			points.add(new Point2D.Double(innerMult*endX, innerMult*endY));
			points.add(new Point2D.Double(innerMult*startX, innerMult*startY));
			
			double[] polygon = new double[points.size()*2];
			int cnt = 0;
			for (Point2D pt : points) {
				polygon[cnt++] = pt.getX();
				polygon[cnt++] = pt.getY();
			}
			Color color = cpt.getColor((float)(hist.getY(i)/sumY));
			
			Stroke stroke = PlotLineType.SOLID.buildStroke(2f);
			spec.addPlotAnnotation(new XYPolygonAnnotation(polygon, stroke, Color.DARK_GRAY, color));
		}
		
		PaintScaleLegend cptBar = XYZGraphPanel.getLegendForCPT(cpt, "Fraction",
				24, 18, 0.05d, RectangleEdge.BOTTOM);
		spec.addSubtitle(cptBar);
		
		Range xRange = new Range(-1.1d, 1.1d);
		Range yRange = new Range(-1.1d, 1.1d);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(22);
		gp.setBackgroundColor(Color.WHITE);
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		
		gp.getXAxis().setTickLabelsVisible(false);
		gp.getYAxis().setTickLabelsVisible(false);
		
		File file = new File(outputDir, prefix+".png");
		gp.getChartPanel().setSize(800, 800);
		gp.saveAsPNG(file.getAbsolutePath());
		return file;
	}

}
