package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.gui.plot.GeographicMapMaker;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.modules.ConnectivityClusters;
import org.opensha.sha.earthquake.faultSysSolution.modules.ConnectivityClusters.ConnectivityClusterSolutionMisfits;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitProgress;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.MisfitStats;
import org.opensha.sha.earthquake.faultSysSolution.modules.InversionMisfitStats.Quantity;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata.RupSetOverlap;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.FaultSubsectionCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.ConnectivityCluster;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

public class FaultSectionConnectionsPlot extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Fault Section Connections";
	}
	
	private static boolean TITLES = true;
	private static boolean LEGENDS = true;
	private static boolean LEGENDS_INSET = false;

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		boolean hasComp = meta.comparison != null && meta.comparisonHasSameSects;
		System.out.println("Plotting section connections");
		plotConnectivityLines(rupSet, resourcesDir, "sect_connectivity",
				TITLES ? getTruncatedTitle(meta.primary.name)+" Connectivity" : " ", meta.primary.jumps, MAIN_COLOR, meta.region, 800);
		plotConnectivityLines(rupSet, resourcesDir, "sect_connectivity_hires",
				TITLES ? getTruncatedTitle(meta.primary.name)+" Connectivity" : " ", meta.primary.jumps, MAIN_COLOR, meta.region, 3000);
		if (hasComp) {
			plotConnectivityLines(meta.comparison.rupSet, resourcesDir, "sect_connectivity_comp",
					TITLES ? getTruncatedTitle(meta.comparison.name)+" Connectivity" : " ", meta.comparison.jumps,
					COMP_COLOR, meta.region, 800);
			plotConnectivityLines(meta.comparison.rupSet, resourcesDir, "sect_connectivity_comp_hires",
					TITLES ? getTruncatedTitle(meta.comparison.name)+" Connectivity" : " ", meta.comparison.jumps,
					COMP_COLOR, meta.region, 3000);
			plotConnectivityLines(rupSet, resourcesDir, "sect_connectivity_unique",
					TITLES ? getTruncatedTitle(meta.primary.name)+" Unique Connectivity" : " ", meta.primaryOverlap.uniqueJumps,
					MAIN_COLOR, meta.region, 800);
			plotConnectivityLines(rupSet, resourcesDir, "sect_connectivity_unique_hires",
					TITLES ? getTruncatedTitle(meta.primary.name)+" Unique Connectivity" : " ", meta.primaryOverlap.uniqueJumps,
					MAIN_COLOR, meta.region, 3000);
			plotConnectivityLines(meta.comparison.rupSet, resourcesDir, "sect_connectivity_unique_comp",
					TITLES ? getTruncatedTitle(meta.comparison.name)+" Unique Connectivity" : " ", meta.comparisonOverlap.uniqueJumps,
					COMP_COLOR, meta.region, 800);
			plotConnectivityLines(meta.comparison.rupSet, resourcesDir, "sect_connectivity_unique_comp_hires",
					TITLES ? getTruncatedTitle(meta.comparison.name)+" Unique Connectivity" : " ", meta.comparisonOverlap.uniqueJumps,
					COMP_COLOR, meta.region, 3000);
		}
		
		double maxConnDist = 0d;
		for (Jump jump : meta.primary.jumps)
			maxConnDist = Math.max(maxConnDist, jump.distance);
		if (hasComp)
			for (Jump jump : meta.comparison.jumps)
				maxConnDist = Math.max(maxConnDist, jump.distance);
		plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist",
				TITLES ? getTruncatedTitle(meta.primary.name)+" Connectivity" : " ", meta.primary, meta.primaryOverlap, maxConnDist,
				MAIN_COLOR, false, false);
		if (sol != null) {
			plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_rates",
					TITLES ? getTruncatedTitle(meta.primary.name)+" Connectivity" : " ", meta.primary, meta.primaryOverlap, maxConnDist,
					MAIN_COLOR, true, false);
			plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_rates_log",
					TITLES ? getTruncatedTitle(meta.primary.name)+" Connectivity" : " ", meta.primary, meta.primaryOverlap, maxConnDist,
					MAIN_COLOR, true, true);
		}
		if (hasComp) {
			plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_comp",
					TITLES ? getTruncatedTitle(meta.comparison.name)+" Connectivity" : " ", meta.comparison, meta.comparisonOverlap, maxConnDist,
					COMP_COLOR, false, false);
			if (meta.comparison.sol != null) {
				plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_rates_comp",
						TITLES ? getTruncatedTitle(meta.comparison.name)+" Connectivity" : " ", meta.comparison, meta.comparisonOverlap, maxConnDist,
						COMP_COLOR, true, false);
				plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_rates_comp_log",
						TITLES ? getTruncatedTitle(meta.comparison.name)+" Connectivity" : " ", meta.comparison, meta.comparisonOverlap, maxConnDist,
						COMP_COLOR, true, true);
			}
		}
		
		
		List<String> lines = new ArrayList<>();
		
		if (hasComp) {
			List<Set<Jump>> connectionsList = new ArrayList<>();
			List<Color> connectedColors = new ArrayList<>();
			List<String> connNames = new ArrayList<>();
			
			connectionsList.add(meta.primaryOverlap.uniqueJumps);
			connectedColors.add(darkerTrans(MAIN_COLOR));
			connNames.add(meta.primary.name+" Only");
			
			connectionsList.add(meta.comparisonOverlap.uniqueJumps);
			connectedColors.add(darkerTrans(COMP_COLOR));
			connNames.add(meta.comparison.name+" Only");
			
			connectionsList.add(meta.primaryOverlap.commonJumps);
			connectedColors.add(darkerTrans(COMMON_COLOR));
			connNames.add("Common Connections");
			
			String combConnPrefix = "sect_connectivity_combined";
			plotConnectivityLines(rupSet, resourcesDir, combConnPrefix, TITLES ? "Combined Connectivity" : " ",
					connectionsList, connectedColors, connNames, meta.region, 800);
			plotConnectivityLines(rupSet, resourcesDir, combConnPrefix+"_hires", TITLES ? "Combined Connectivity" : " ",
					connectionsList, connectedColors, connNames, meta.region, 3000);
			lines.add("![Combined]("+relPathToResources+"/"+combConnPrefix+".png)");
			lines.add("");
			
			TableBuilder table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("[View high resolution]("+relPathToResources+"/"+combConnPrefix+"_hires.png)");
			table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+combConnPrefix+".geojson"));
			table.addColumn("[Download GeoJSON]("+relPathToResources+"/"+combConnPrefix+".geojson)");
//			table.addColumn("[Download Jumps-Only GeoJSON]("+relPathToResources+"/"+combConnPrefix+"_jumps_only.geojson)");
			table.finalizeLine();
			lines.addAll(table.build());
			lines.add("");
			
			lines.add(getSubHeading()+" Jump Overlaps");
			lines.add(topLink); lines.add("");
			
			table = MarkdownUtils.tableBuilder();
			table.addLine("", meta.primary.name, meta.comparison.name);
			table.addLine("**Total Count**", meta.primary.jumps.size(), meta.comparison.jumps.size());
			table.initNewLine();
			table.addColumn("**# Unique Jumps**");
			table.addColumn(meta.primaryOverlap.uniqueJumps.size()+" ("+percentDF.format(
					(double)meta.primaryOverlap.uniqueJumps.size()/(double)meta.primary.jumps.size())+")");
			table.addColumn(meta.comparisonOverlap.uniqueJumps.size()+" ("+percentDF.format(
					(double)meta.comparisonOverlap.uniqueJumps.size()/(double)meta.comparison.jumps.size())+")");
			table.finalizeLine();
			if (sol != null || meta.comparison.sol != null) {
				table.addColumn("**Unique Jump Rate**");
				if (sol == null) {
					table.addColumn(na);
				} else {
					double rateInputUnique = 0d;
					for (Jump jump : meta.primaryOverlap.uniqueJumps)
						rateInputUnique += meta.primary.jumpRates.get(jump);
					table.addColumn((float)rateInputUnique+" ("+percentDF.format(
							rateInputUnique/sol.getTotalRateForAllFaultSystemRups())+")");
				}
				if (meta.comparison.sol == null) {
					table.addColumn(na);
				} else {
					double rateCompUnique = 0d;
					for (Jump jump : meta.comparisonOverlap.uniqueJumps)
						rateCompUnique += meta.comparison.jumpRates.get(jump);
					table.addColumn((float)rateCompUnique+" ("+percentDF.format(
							rateCompUnique/meta.comparison.sol.getTotalRateForAllFaultSystemRups())+")");
				}
				table.finalizeLine();
			}
			lines.addAll(table.build());
			lines.add("");
		}
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		if (hasComp)
			table.addLine(meta.primary.name, meta.comparison.name);
		File mainPlot = new File(resourcesDir, "sect_connectivity.png");
		File compPlot = new File(resourcesDir, "sect_connectivity_comp.png");
		addTablePlots(table, mainPlot, compPlot, relPathToResources, hasComp);
		if (hasComp) {
			mainPlot = new File(resourcesDir, "sect_connectivity_unique.png");
			compPlot = new File(resourcesDir, "sect_connectivity_unique_comp.png");
			addTablePlots(table, mainPlot, compPlot, relPathToResources, hasComp);
		}
		mainPlot = new File(resourcesDir, "sect_connectivity_hist.png");
		compPlot = new File(resourcesDir, "sect_connectivity_hist_comp.png");
		addTablePlots(table, mainPlot, compPlot, relPathToResources, hasComp);
		if (sol != null || (hasComp && meta.comparison.sol != null)) {
			mainPlot = new File(resourcesDir, "sect_connectivity_hist_rates.png");
			compPlot = new File(resourcesDir, "sect_connectivity_hist_rates_comp.png");
			addTablePlots(table, mainPlot, compPlot, relPathToResources, hasComp);
			mainPlot = new File(resourcesDir, "sect_connectivity_hist_rates_log.png");
			compPlot = new File(resourcesDir, "sect_connectivity_hist_rates_comp_log.png");
			addTablePlots(table, mainPlot, compPlot, relPathToResources, hasComp);
		}
		lines.addAll(table.build());
		lines.add("");
		
		if (hasComp && rupSet.hasModule(RuptureConnectionSearch.class)
				&& meta.comparison.rupSet.hasModule(RuptureConnectionSearch.class)) {
			RuptureConnectionSearch primarySearch = rupSet.getModule(RuptureConnectionSearch.class);
			RuptureConnectionSearch compSearch = meta.comparison.rupSet.getModule(RuptureConnectionSearch.class);
			System.out.println("Plotting connection examples");
			lines.add(getSubHeading()+" Unique Connection Example Ruptures");
			lines.add(topLink); lines.add("");
			
			lines.add("**New Ruptures with Unique Connections**");
			int maxRups = 20;
			int maxCols = 5;
			table = plotConnRupExamples(primarySearch, rupSet, meta.primaryOverlap.uniqueJumps,
					meta.primary.jumpRupsMap, maxRups, maxCols, resourcesDir, relPathToResources, "conn_example");
			lines.add("");
			if (table == null)
				lines.add(na);
			else
				lines.addAll(table.build());
			lines.add("");
			lines.add("**"+meta.comparison.name+" Ruptures with Unique Connections**");
			table = plotConnRupExamples(compSearch, meta.comparison.rupSet, meta.comparisonOverlap.uniqueJumps,
					meta.comparison.jumpRupsMap, maxRups, maxCols, resourcesDir, relPathToResources, "comp_conn_example");
			lines.add("");
			if (table == null)
				lines.add(na);
			else
				lines.addAll(table.build());
			lines.add("");
		}
		
		// add clusters
		lines.add(getSubHeading()+" Connected Clusters");
		lines.add(topLink); lines.add("");
		
		lines.add("Connected clusters of fault sections, where all sections plotted in a given color connect with all "
				+ "other sections of the same color through ruptures. There may not be any single rupture that connects "
				+ "all such sections, but rather, chains of ruptures connect the sections. Only the first "
				+ MAX_PLOT_CLUSTERS+" clusters are plotted with bold colors; smaller clusters are plotted in random "
				+ "saturated colors (note that neighboring clusters can be similar colors by chance), and fully "
				+ "isolated faults are plotted in black.");
		lines.add("");
		
		List<ConnectivityCluster> rsClusters = rupSetClusters(rupSet);
		List<ConnectivityCluster> solClusters = null;
		if (sol != null) {
			// see if we have any zero rate ruptures
			boolean hasZero = false;
			for (double rate : sol.getRateForAllRups()) {
				if (rate == 0) {
					hasZero = true;
					break;
				}
			}
			if (hasZero) {
				// we might have solution clusters that are subsets of rup set clusters
				solClusters = solClusters(sol);
				if (solClusters.size() == rsClusters.size())
					// don't bother, same number of clusters
					solClusters = null;
			}
		}
		
		boolean[] doSols;
		if (solClusters == null)
			doSols = new boolean[] { false };
		else
			doSols = new boolean[] { false, true };
		
		for (boolean doSol : doSols) {
			List<ConnectivityCluster> clusters = doSol ? solClusters : rsClusters;
			
			String prefix = "conn_clusters";
			if (doSol)
				prefix += "_sol";
			
			table = MarkdownUtils.tableBuilder();
			
			TableBuilder clustersTable = MarkdownUtils.tableBuilder();
			
			if (hasComp) {
				table.addLine(meta.primary.name, meta.comparison.name);
				
				List<ConnectivityCluster> compClusters = doSol ? solClusters(meta.comparison.sol) : rupSetClusters(meta.comparison.rupSet);
				
				File primaryClustersPlot = plotConnectedClusters(rupSet, sol, meta.region, resourcesDir, prefix,
						TITLES ? getTruncatedTitle(meta.primary.name)+" Clusters" : " ", clustersTable, clusters);
				File compClustersPlot = null;
				if (compClusters != null)
					compClustersPlot = plotConnectedClusters(meta.comparison.rupSet, meta.comparison.sol, meta.region, resourcesDir,
							prefix+"_comp", TITLES ? getTruncatedTitle(meta.comparison.name)+" Clusters" : " ", null, compClusters);
				
				table.addLine("![Primary clusters]("+relPathToResources+"/"+primaryClustersPlot.getName()+")",
						compClustersPlot == null ? na : "![Comparison clusters]("+relPathToResources+"/"+compClustersPlot.getName()+")");
				table.addLine(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+".geojson")
						+" [Download GeoJSON]("+relPathToResources+"/"+prefix+".geojson)",
						compClustersPlot == null ? na :
							RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+"_comp.geojson")
						+" [Download GeoJSON]("+relPathToResources+"/"+prefix+"_comp.geojson)");
			} else {
				File primaryClusters = plotConnectedClusters(rupSet, sol, meta.region, resourcesDir, prefix,
						TITLES ? "Connected Section Clusters" : " ", clustersTable, clusters);
				
				table.addLine("![Primary clusters]("+relPathToResources+"/"+primaryClusters.getName()+")");
				table.addLine(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+".geojson")
						+" [Download GeoJSON]("+relPathToResources+"/"+prefix+".geojson)");
			}
			
			if (solClusters != null) {
				// we're doing this for both rup set and solution clusters
				
				if (doSol) {
					lines.add(getSubHeading()+"# Solution Clusters");
					lines.add(topLink); lines.add("");
					
					lines.add("This section shows clusters of sections connected by ruptures with nonzero rates in the "
							+ "fault system solution, which differs from those calculated using every available rupture "
							+ "in the rupture set. This can occur by random chance, or if the solver was conditioned to "
							+ "only use a particular subset of all available ruptures (e.g., for a segmentation constraint).");
					lines.add("");
				} else {
					lines.add(getSubHeading()+"# Rupture Set Clusters");
					lines.add(topLink); lines.add("");
				}
			}
			
			lines.addAll(table.build());
			lines.add("");
			lines.addAll(clustersTable.build());
		}
		
		if (sol != null && sol.hasModule(ConnectivityClusterSolutionMisfits.class)) {
			InversionMisfitProgress largestClusterProgress = sol.requireModule(
					ConnectivityClusterSolutionMisfits.class).getLargestClusterMisfitProgress();
			if (largestClusterProgress != null) {
				InversionMisfitProgress compProgress = null;
				String compName = null;
				if (meta.hasComparisonSol() && meta.comparisonHasSameSects &&
						meta.comparison.sol.hasModule(ConnectivityClusterSolutionMisfits.class)) {
					compProgress = meta.comparison.sol.requireModule(ConnectivityClusterSolutionMisfits.class).getLargestClusterMisfitProgress();
					compName = meta.comparison.name;
				}
				
				lines.add("");
				lines.add(getSubHeading()+" Largest Cluster Misfit Progress");
				lines.add(topLink); lines.add("");
				
				lines.addAll(InversionProgressPlot.plotMisfitProgress(largestClusterProgress, compProgress, compName,
						resourcesDir, relPathToResources));
			}
		}
		
		return lines;
	}
	
	private static List<ConnectivityCluster> rupSetClusters(FaultSystemRupSet rupSet) {
		if (!rupSet.hasModule(ConnectivityClusters.class)) {
			System.out.println("Calculating connection clusters");
			Stopwatch watch = Stopwatch.createStarted();
			ConnectivityClusters clusters = ConnectivityClusters.build(rupSet);
			watch.stop();
			System.out.println("Found "+clusters.size()+" connectivity clusters in "+optionalDigitDF.format(
					watch.elapsed(TimeUnit.MILLISECONDS)/1000)+" s");
			rupSet.addModule(clusters);
		}
		return rupSet.requireModule(ConnectivityClusters.class).get();
	}
	
	private static List<ConnectivityCluster> solClusters(FaultSystemSolution sol) {
		if (sol == null)
			return null;
		return ConnectivityCluster.buildNonzeroRateClusters(sol);
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(ClusterRuptures.class);
	}
	
	@Override
	public List<String> getSummary(ReportMetadata meta, File resourcesDir, String relPathToResources, String topLink) {
		List<String> lines = new ArrayList<>();
		lines.add(getSubHeading()+" Connectivity Map");
		lines.add("");
		lines.add(topLink);
		lines.add("");
		
		String prefix;
		if (new File(resourcesDir, "sect_connectivity_combined.png").exists()) {
			prefix = "sect_connectivity_combined";
		} else {
			prefix = "sect_connectivity";
		}
		lines.add("![map]("+relPathToResources+"/"+prefix+".png)");
		lines.add("");
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("[View high resolution]("+relPathToResources+"/"+prefix+"_hires.png)");
		table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+prefix+".geojson"));
		table.addColumn("[Download GeoJSON]("+relPathToResources+"/"+prefix+".geojson)");
//		table.addColumn("[Download Jumps-Only GeoJSON]("+relPathToResources+"/"+prefix+"_jumps_only.geojson)");
		table.finalizeLine();
		lines.addAll(table.build());
		return lines;
	}

	private static void addTablePlots(TableBuilder table, File mainPlot, File compPlot, String relPath,
			boolean hasComp) {
		table.initNewLine();
		File mainGeoJSON = null;
		if (mainPlot.exists()) {
			table.addColumn("![plot]("+relPath+"/"+mainPlot.getName()+")");
			mainGeoJSON = new File(mainPlot.getAbsolutePath().replaceAll(".png", ".geojson"));
		} else {
			table.addColumn(na);
		}
		File compGeoJSON = null;
		if (hasComp) {
			if (compPlot.exists()) {
				table.addColumn("![plot]("+relPath+"/"+compPlot.getName()+")");
				compGeoJSON = new File(compPlot.getAbsolutePath().replaceAll(".png", ".geojson"));
			} else {
				table.addColumn(na);
			}
		}
		table.finalizeLine();
		if ((mainGeoJSON != null && mainGeoJSON.exists()) || (compGeoJSON != null && compGeoJSON.exists())) {
			table.initNewLine();
			if ((mainGeoJSON != null && mainGeoJSON.exists()))
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+mainGeoJSON.getName())
						+" "+"[Download GeoJSON]("+relPath+"/"+mainGeoJSON.getName()+")");
			else
				table.addColumn(na);
			if ((compGeoJSON != null && compGeoJSON.exists()))
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+compGeoJSON.getName())
						+" "+"[Download GeoJSON]("+relPath+"/"+compGeoJSON.getName()+")");
			else
				table.addColumn(na);
			table.finalizeLine();
		}
	}
	
	public static Color darkerTrans(Color c) {
		c = c.darker();
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), 200);
	}
	
	public static void plotConnectivityLines(FaultSystemRupSet rupSet, File outputDir, String prefix, String title,
			Set<Jump> connections, Color connectedColor, Region reg, int width) throws IOException {
		List<Set<Jump>> connectionsList = new ArrayList<>();
		List<Color> connectedColors = new ArrayList<>();
		List<String> connNames = new ArrayList<>();
		
		connectionsList.add(connections);
		connectedColors.add(connectedColor);
		connNames.add("Connections");
		
		plotConnectivityLines(rupSet, outputDir, prefix, title, connectionsList, connectedColors, connNames, reg, width);
	}
	
	public static void plotConnectivityLines(FaultSystemRupSet rupSet, File outputDir, String prefix, String title,
			List<Set<Jump>> connectionsList, List<Color> connectedColors, List<String> connNames,
			Region reg, int width) throws IOException {
		GeographicMapMaker plotter = new RupSetMapMaker(rupSet, reg);
		plotter.setLegendVisible(LEGENDS);
		plotter.setLegendInset(LEGENDS_INSET);
		plotter.setWriteGeoJSON(!prefix.endsWith("_hires"));
		
		for (int i=0; i<connectionsList.size(); i++) {
			Set<Jump> connections = connectionsList.get(i);
			Color connectedColor = connectedColors.get(i);
			String connName = connNames.get(i);
			
			plotter.plotJumps(connections, connectedColor, getTruncatedTitle(connName));
		}
		
		plotter.plot(outputDir, prefix, title, width);
	}
	
	public static void plotConnectivityHistogram(File outputDir, String prefix, String title,
			RupSetMetadata meta, RupSetOverlap overlap, double maxDist, Color connectedColor,
			boolean rateWeighted, boolean yLog) throws IOException {
		double delta = 1d;
//		if (maxDist > 90)
//			delta = 5d;
//		else if (maxDist > 40)
//			delta = 2;
//		else if (maxDist > 20)
//			delta = 1d;
//		else
//			delta = 0.5d;
		
		maxDist = Math.max(2d, maxDist);
		
		HistogramFunction hist = HistogramFunction.getEncompassingHistogram(0d, maxDist, delta);
		hist.setName("All Connections");
		HistogramFunction uniqueHist = null;
		if (overlap != null) {
			uniqueHist = HistogramFunction.getEncompassingHistogram(0d, maxDist, delta);
			uniqueHist.setName("Unique To Model");
		}
		
		double myMax = 0d;
		double mean = 0d;
		double sumWeights = 0d;
		double meanAbove = 0d;
		double sumWeightsAbove = 0d;
		
		for (Jump pair : meta.jumps) {
			double dist = pair.distance;
			double weight = rateWeighted ? meta.jumpRates.get(pair) : 1d;
			
			myMax = Math.max(myMax, dist);
			mean += dist*weight;
			sumWeights += weight;
			if (dist >= 0.1) {
				meanAbove += dist*weight;
				sumWeightsAbove += weight;
			}
			
			int xIndex = hist.getClosestXIndex(dist);
			hist.add(xIndex, weight);
			if (overlap != null && overlap.uniqueJumps.contains(pair))
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
		
		if (uniqueHist != null) {
			funcs.add(uniqueHist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, uniqueColor));
		}
		
		String yAxisLabel = rateWeighted ? "Annual Rate" : "Count";
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Jump Distance (km)", yAxisLabel);
		spec.setLegendVisible(LEGENDS);
		spec.setLegendInset(LEGENDS_INSET);
		
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
			yRange = new Range(0d, Math.max(1d, 1.05*hist.getMaxY()));
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
		gp.saveAsTXT(file.getAbsolutePath()+".txt");
	}
	
	public static Map<Jump, Double> getJumps(FaultSystemSolution sol, List<ClusterRupture> ruptures,
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

	public static TableBuilder plotConnRupExamples(RuptureConnectionSearch search, FaultSystemRupSet rupSet,
			Set<Jump> pairings, Map<Jump, List<Integer>> pairRupsMap, int maxRups, int maxCols,
			File resourcesDir, String relPathToResources, String prefix) throws IOException {
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
		
		File rupHtmlDir = new File(resourcesDir.getParentFile(), "rupture_pages");
		Preconditions.checkState(rupHtmlDir.exists() || rupHtmlDir.mkdir());
		
		System.out.println("Plotting "+rupsToPlot+" ruptures");
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		List<ClusterRupture> rups = rupSet.requireModule(ClusterRuptures.class).getAll();
		for (int rupIndex : rupsToPlot) {
			String rupPrefix = prefix+"_"+rupIndex;
			ClusterRupture rupture = rups.get(rupIndex);
			search.plotConnections(resourcesDir, rupPrefix, rupIndex, rupture, pairings, "Unique Connections");
			table.addColumn("[<img src=\"" + relPathToResources + "/" + rupPrefix + ".png\" />]"+
					"("+relPathToResources+"/../"+RupHistogramPlots.generateRuptureInfoPage(
							rupSet, rupture, rupIndex, rupHtmlDir, rupPrefix, null, search.getDistAzCalc())+ ")");
		}
		table.finalizeLine();
		return table.wrap(maxCols, 0);
	}
	
	private static int MAX_PLOT_CLUSTERS = 10;
	private static boolean SMART_RAND = true;

	public static File plotConnectedClusters(FaultSystemRupSet rupSet, FaultSystemSolution sol, Region region,
			File outputDir, String prefix, String title, TableBuilder table, List<ConnectivityCluster> clustersUnsorted)
					throws IOException {
		GeographicMapMaker plotter = buildConnectedClustersPlot(rupSet, sol, region, table, clustersUnsorted);
		
		plotter.plot(outputDir, prefix, title, 1200);
		
		return new File(outputDir, prefix+".png");
	}
	
	public static GeographicMapMaker buildConnectedClustersPlot(FaultSystemRupSet rupSet, FaultSystemSolution sol, Region region,
			TableBuilder table, List<ConnectivityCluster> clustersUnsorted) throws IOException {
		
		// sort clusters by number of sections
		List<ConnectivityCluster> clusters = new ArrayList<>(clustersUnsorted);
		Collections.sort(clusters, ConnectivityCluster.sectCountComparator);
		
		// reverse it (decreasing)
		Collections.reverse(clusters);
		System.out.println("Largest has "+clusters.get(0).getNumSections()+" sections, "
				+clusters.get(0).getNumRuptures()+" ruptures");
		
		float sectScalarThickness = 3f;
		PlotCurveCharacterstics isolatedChar = new PlotCurveCharacterstics(PlotLineType.SOLID, sectScalarThickness, Color.BLACK);
		PlotCurveCharacterstics isolatedProxyChar = new PlotCurveCharacterstics(PlotLineType.SHORT_DASHED, sectScalarThickness, Color.BLACK);
		int numNonIsolated = 0;
		for (ConnectivityCluster cluster : clusters)
			if (cluster.getParentSectIDs().size() > 1)
				numNonIsolated++;
		int cptNumToPlot = Integer.min(MAX_PLOT_CLUSTERS, Integer.max(2, numNonIsolated));
		CPT clusterCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().reverse().rescale(0d, cptNumToPlot-1d);
		
		List<PlotCurveCharacterstics> sectChars = new ArrayList<>();
		List<Double> sectColorSortables = new ArrayList<>();
		for (int s=0; s<rupSet.getNumSections(); s++) {
			sectChars.add(null);
			sectColorSortables.add(null);
		}
		
		ConnectivityClusterSolutionMisfits clusterMisfits = null;
		Map<ConnectivityCluster, InversionMisfitStats> clusterMisfitStats = null;
		if (sol != null && table != null) {
			clusterMisfits = sol.getModule(ConnectivityClusterSolutionMisfits.class);
			if (clusterMisfits != null) {
				clusterMisfitStats = new HashMap<>();
				// get without sorting
				ConnectivityClusters origClusters = rupSet.requireModule(ConnectivityClusters.class);
				for (int i=0; i<origClusters.size(); i++) {
					InversionMisfitStats stats = clusterMisfits.getMisfitStats(i);
					if (stats != null)
						clusterMisfitStats.put(origClusters.get(i), stats);
				}
			}
		}
		List<String> clusterMisfitNames = null;
		Quantity tableQuantity = Quantity.MAD;
		String quantityAbbrev = "MAD";
		if (table != null) {
			table.initNewLine();
			table.addColumns("Rank", "Sections", "Parent Sections", "Ruptures");
			if (clusterMisfits != null) {
				clusterMisfitNames = new ArrayList<>();
				HashSet<String> misfitNames = new HashSet<>();
				for (InversionMisfitStats stats : clusterMisfitStats.values()) {
					for (MisfitStats misfits : stats.getStats()) {
						String name = misfits.range.name;
						if (!misfitNames.contains(name)) {
							misfitNames.add(name);
							clusterMisfitNames.add(name);
							table.addColumn(name+" "+quantityAbbrev);
						}
					}
				}
			}
			table.finalizeLine();
		}
		double[] isolatedMisfits = null;
		int[] isolatedMisfitCounts = null;
		double[] otherMisfits = null;
		int[] otherMisfitCounts = null;
		if (clusterMisfits != null) {
			isolatedMisfits = new double[clusterMisfitNames.size()];
			isolatedMisfitCounts = new int[clusterMisfitNames.size()];
			otherMisfits = new double[clusterMisfitNames.size()];
			otherMisfitCounts = new int[clusterMisfitNames.size()];
		}
		
		int numSections = rupSet.getNumSections();
		HashSet<Integer> allParents = new HashSet<>();
		for (FaultSection sect : rupSet.getFaultSectionDataList())
			allParents.add(sect.getParentSectionId());
		int numParents = allParents.size();
		int numRuptures = rupSet.getNumRuptures();
		
		int numOther = 0;
		int sectsOther = 0;
		int parentsOther = 0;
		int rupturesOther = 0;
		
		int numIsolated = 0;
		int sectsIsolated = 0;
		int rupturesIsolated = 0;
		
		int tableIndex = 0;
		
		Random rand = new Random((long)rupSet.getNumSections() * (long)clusters.size());
		
		// this will try to prevent nearby similar colors
		Map<ConnectivityCluster, Double> prevRands = new HashMap<>();
		Map<ConnectivityCluster, Location> clusterCenters = new HashMap<>();
		for (ConnectivityCluster cluster : clusters) {
			double avgLat = 0d;
			double avgLon = 0d;
			for (int sectID : cluster.getSectIDs()) {
				FaultSection sect = rupSet.getFaultSectionData(sectID);
				FaultTrace trace = sect.getFaultTrace();
				avgLat += 0.5*(trace.first().lat+trace.last().lat);
				avgLon += 0.5*(trace.first().lon+trace.last().lon);
			}
			avgLat /= cluster.getNumSections();
			avgLon /= cluster.getNumSections();
			clusterCenters.put(cluster, new Location(avgLat, avgLon));
		}
		Location lowerLeft = new Location(region.getMinLat(), region.getMinLon());
		Location lowerRight = new Location(region.getMinLat(), region.getMaxLon());
		Location upperLeft = new Location(region.getMaxLat(), region.getMinLon());
		// scale length for determining nearby clusters
		double randDistScale = Double.max(150d, Math.max(0.1d*LocationUtils.horzDistanceFast(lowerLeft, lowerRight),
				0.1d*LocationUtils.horzDistanceFast(lowerLeft, upperLeft)));
		
		for (int i=0; i<clusters.size(); i++) {
			ConnectivityCluster cluster = clusters.get(i);
			boolean allSameParent = cluster.getParentSectIDs().size() == 1;
			
			double[] myMisfits = null;
			if (clusterMisfits != null) {
				InversionMisfitStats tmp = clusterMisfitStats.get(cluster);
				if (tmp != null) {
					myMisfits = new double[clusterMisfitNames.size()];
					List<MisfitStats> myMisfitStats = tmp.getStats();
					for (int j=0; j<clusterMisfitNames.size(); j++) {
						myMisfits[j] = Double.NaN;
						String name = clusterMisfitNames.get(j);
						for (MisfitStats stats : myMisfitStats) {
							if (name.equals(stats.range.name)) {
								myMisfits[j] = stats.get(tableQuantity);
								break;
							}
						}
					}
				}
			}
			
			PlotCurveCharacterstics sectChar;
			if (allSameParent) {
				FaultSection first = rupSet.getFaultSectionData(cluster.getSectIDs().iterator().next());
				sectChar = first.isProxyFault() ? isolatedProxyChar : isolatedChar;
				
				numIsolated++;
				sectsIsolated += cluster.getNumSections();
				rupturesIsolated += cluster.getNumRuptures();
				
				if (myMisfits != null) {
					for (int j=0; j<clusterMisfitNames.size(); j++) {
						if (Double.isFinite(myMisfits[j])) {
							isolatedMisfitCounts[j]++;
							isolatedMisfits[j] += myMisfits[j];
						}
					}
				}
			} else if (tableIndex < cptNumToPlot) {
				Color color = clusterCPT.getColor((float)tableIndex);
				
				// make it a bit darker
				Color darker = color.darker();
				color = new Color(
						(int)(0.5*(color.getRed()+darker.getRed())+0.5),
						(int)(0.5*(color.getGreen()+darker.getGreen())+0.5),
						(int)(0.5*(color.getBlue()+darker.getBlue())+0.5));
				sectChar = new PlotCurveCharacterstics(PlotLineType.SOLID, sectScalarThickness, color);
				
				tableIndex++;
				if (table != null) {
					table.initNewLine();
					table.addColumns(tableIndex,
							countStr(cluster.getNumSections(), numSections),
							countStr(cluster.getParentSectIDs().size(), numParents),
							countStr(cluster.getNumRuptures(), numRuptures));
					if (clusterMisfits != null) {
						if (myMisfits == null) {
							for (int j=0; j<clusterMisfitNames.size(); j++)
								table.addColumn("_(N/A)_");
						} else {
							for (double val : myMisfits) {
								if (Double.isFinite(val))
									table.addColumn((float)val);
								else
									table.addColumn("_(N/A)_");
							}
						}
					}
					table.finalizeLine();
				}
			} else {
				Location centerLoc = clusterCenters.get(cluster);
				
				double myRand = rand.nextDouble();
				if (SMART_RAND && !prevRands.isEmpty()) {
					// see if we need to redraw
					List<Double> closeRands = new ArrayList<>();
					List<Double> closeDists = new ArrayList<>();
					for (ConnectivityCluster oCluster : prevRands.keySet()) {
						double centerDist = LocationUtils.horzDistanceFast(centerLoc, clusterCenters.get(oCluster));
						if (centerDist < randDistScale*2d) {
							double minClusterDist = centerDist;
							for (int sectID1 : cluster.getSectIDs()) {
								FaultSection sect1 = rupSet.getFaultSectionData(sectID1);
								Location l11 = sect1.getFaultTrace().first();
								Location l12 = sect1.getFaultTrace().last();
								for (int sectID2 : oCluster.getSectIDs()) {
									FaultSection sect2 = rupSet.getFaultSectionData(sectID2);
									Location l21 = sect2.getFaultTrace().first();
									Location l22 = sect2.getFaultTrace().last();
									minClusterDist = Math.min(minClusterDist, LocationUtils.horzDistanceFast(l11, l21));
									minClusterDist = Math.min(minClusterDist, LocationUtils.horzDistanceFast(l11, l22));
									minClusterDist = Math.min(minClusterDist, LocationUtils.horzDistanceFast(l12, l21));
									minClusterDist = Math.min(minClusterDist, LocationUtils.horzDistanceFast(l12, l22));
								}
							}
							if (minClusterDist < randDistScale) {
								closeRands.add(prevRands.get(oCluster));
								closeDists.add(minClusterDist);
							}
						}
					}
					
					if (!closeRands.isEmpty()) {
						double furthestScore = 0d;
						double curRand = myRand;
						double nextRand = rand.nextDouble();
						
						// try different randoms, find the most distinct one from it's neighbors
						for (int j=0; j<10; j++) {
							// score that's highest when the random value is farthest from the randoms for nearby clusters
							double score = 0;
							for (int k=0; k<closeRands.size(); k++) {
								double dist = closeDists.get(k);
								double closeRand = closeRands.get(k);
								double diff = Math.abs(closeRand - curRand);
								// cap diff, we don't want everything to be polar opposites, just sufficiently different
								diff = Math.min(diff, 0.3);
								// this is 1 if they're really close, 0 if they're really far
								double invDist = 1d - (dist/randDistScale);
								score += invDist * diff;
							}
							if (score > furthestScore) {
								furthestScore = score;
								myRand = curRand;
							}
							
							curRand = nextRand;
							nextRand = rand.nextDouble();
						}
					}
				}
				
				prevRands.put(cluster, myRand);
				
				Color c = clusterCPT.getColor((float)(myRand*(cptNumToPlot-1d)));
				int r = c.getRed();
				int g = c.getGreen();
				int b = c.getBlue();
				
				// saturate it (2 steps)
				for (int j=0; j<2; j++) {
					r = (int)(0.5d*(r + 255d)+0.5);
					g = (int)(0.5d*(g + 255d)+0.5);
					b = (int)(0.5d*(b + 255d)+0.5);
				}
				
				Color color = new Color(r, g, b);
				sectChar = new PlotCurveCharacterstics(PlotLineType.SOLID, sectScalarThickness, color);
				
				numOther++;
				sectsOther += cluster.getNumSections();
				parentsOther += cluster.getParentSectIDs().size();
				rupturesOther += cluster.getNumRuptures();
				
				if (myMisfits != null) {
					for (int j=0; j<clusterMisfitNames.size(); j++) {
						if (Double.isFinite(myMisfits[j])) {
							otherMisfitCounts[j]++;
							otherMisfits[j] += myMisfits[j];
						}
					}
				}
			}
			
			for (int s : cluster.getSectIDs()) {
				Preconditions.checkState(sectChars.get(s) == null);
				sectChars.set(s, sectChar);
				sectColorSortables.set(s, cluster.getNumSections()+(double)cluster.getNumRuptures()/(double)rupSet.getNumRuptures());
			}
		}
		
		if (table != null) {
			if (numOther > 0) {
				table.initNewLine();
				table.addColumns((tableIndex+1)+"->"+(tableIndex+1+numOther),
						countStr(sectsOther, numSections),
						countStr(parentsOther, numParents),
						countStr(rupturesOther, numRuptures));
				if (clusterMisfits != null) {
					for (int i=0; i<otherMisfits.length; i++) {
						double sum = otherMisfits[i];
						int count = otherMisfitCounts[i];
						if (count == 0)
							table.addColumn(na);
						else
							table.addColumn((float)(sum/(double)count));
					}
				}
				table.finalizeLine();
			}
			if (numIsolated > 0) {
				table.initNewLine();
				table.addColumns(numIsolated+" isolated",
						countStr(sectsIsolated, numSections),
						countStr(numIsolated, numParents),
						countStr(rupturesIsolated, numRuptures));
				if (clusterMisfits != null) {
					for (int i=0; i<isolatedMisfits.length; i++) {
						double sum = isolatedMisfits[i];
						int count = isolatedMisfitCounts[i];
						if (count == 0)
							table.addColumn(na);
						else
							table.addColumn((float)(sum/(double)count));
					}
				}
				table.finalizeLine();
			}
		}
		
		GeographicMapMaker plotter = new RupSetMapMaker(rupSet, region) {

			@Override
			protected Feature surfFeature(FaultSection sect, PlotCurveCharacterstics pChar) {
				return setClusterProps(super.surfFeature(sect, pChar), sect);
			}

			@Override
			protected Feature traceFeature(FaultSection sect, PlotCurveCharacterstics pChar) {
				return setClusterProps(super.traceFeature(sect, pChar), sect);
			}
			
			private Feature setClusterProps(Feature feature, FaultSection sect) {
				int clusterID = 0;
				for (ConnectivityCluster cluster : clusters) {
					if (cluster.containsSect(sect)) {
						feature.properties.set("ClusterID", clusterID);
						feature.properties.set("ClusterParentCount", cluster.getParentSectIDs().size());
						feature.properties.set("ClusterSectCount", cluster.getNumSections());
						feature.properties.set("ClusterRupCount", cluster.getNumRuptures());
						break;
					}
					clusterID++;
				}
				return feature;
			}
			
		};
		plotter.setLegendVisible(LEGENDS);
		plotter.setLegendInset(LEGENDS_INSET);
		plotter.setWriteGeoJSON(true);
		plotter.setSectPolygonChar(new PlotCurveCharacterstics(PlotLineType.POLYGON_SOLID, 1f, new Color(127, 127, 127, 66)));
		
		plotter.plotSectChars(sectChars, null, null, sectColorSortables);
		
		return plotter;
	}
	
	private static String countStr(int num, int tot) {
		double fract = (double)num/(double)tot;
		return countDF.format(num)+" ("+percentDF.format(fract)+")";
	}
	
	public static void main(String[] args) throws IOException {
		FaultSystemSolution sol = FaultSystemSolution.load(new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2022_09_28-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR/"
				+ "results_NSHM23_v2_CoulombRupSet_branch_averaged_gridded.zip"));
		
		plotConnectedClusters(sol.getRupSet(), sol, NSHM23_RegionLoader.loadFullConterminousWUS(), new File("/tmp"),
				"conn_clusters", " ", null, rupSetClusters(sol.getRupSet()));
		plotConnectedClusters(sol.getRupSet(), sol, NSHM23_RegionLoader.loadFullConterminousWUS(), new File("/tmp"),
				"conn_clusters_sol", " ", null, solClusters(sol));
		
//		TITLES = false;
//		FaultSystemRupSet rupSet = FaultSystemRupSet.load(new File("/home/kevin/OpenSHA/UCERF4/rup_sets/"
//				+ "fm3_1_plausibleMulti15km_adaptive6km_direct_cmlRake360_jumpP0.001_slipP0.05incrCapDist_"
//				+ "cff0.75IntsPos_comb2Paths_cffFavP0.01_cffFavRatioN2P0.5_sectFractGrow0.1.zip"));
//		File outputDir = new File("/home/kevin/Documents/papers/2021_UCERF4_Plausibility/figures/figure_16_raw");
//		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
//		
//		Region fullReg = new CaliforniaRegions.RELM_TESTING();
//		Region zoom1 = new Region(new Location(33.6, -121), new Location(35.6, -115.6));
//		Region zoom2 = new Region(new Location(36.6, -123.1), new Location(39, -120.9));
//		
//		FaultSectionConnectionsPlot plot = new FaultSectionConnectionsPlot();
//		
//		RupSetMetadata primayMeta = new RupSetMetadata("Proposed Model", rupSet);
//		RupSetMetadata compMeta = new RupSetMetadata("UCERF3",
//				FaultSystemRupSet.load(new File("/home/kevin/OpenSHA/UCERF4/rup_sets/fm3_1_reproduce_ucerf3.zip")));
//
//		LEGENDS = true;
//		LEGENDS_INSET = true;
//		File fullRegDir = new File(outputDir, "full_reg");
//		Preconditions.checkState(fullRegDir.exists() || fullRegDir.mkdir());
//		plot.plot(rupSet, null, new ReportMetadata(primayMeta, compMeta, fullReg), fullRegDir, "", "");
//		LEGENDS = false;
//		LEGENDS_INSET = false;
//		File zoom1Dir = new File(outputDir, "zoom1");
//		Preconditions.checkState(zoom1Dir.exists() || zoom1Dir.mkdir());
//		plot.plot(rupSet, null, new ReportMetadata(primayMeta, compMeta, zoom1), zoom1Dir, "", "");
//		File zoom2Dir = new File(outputDir, "zoom2");
//		Preconditions.checkState(zoom2Dir.exists() || zoom2Dir.mkdir());
//		plot.plot(rupSet, null, new ReportMetadata(primayMeta, compMeta, zoom2), zoom2Dir, "", "");
	}

}
