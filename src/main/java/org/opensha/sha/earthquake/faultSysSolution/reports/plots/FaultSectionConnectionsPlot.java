package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata.RupSetOverlap;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupSetMapMaker;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;

import com.google.common.base.Preconditions;

public class FaultSectionConnectionsPlot extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Fault Section Connections";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		System.out.println("Plotting section connections");
		plotConnectivityLines(rupSet, resourcesDir, "sect_connectivity",
				getTruncatedTitle(meta.primary.name)+" Connectivity", meta.primary.jumps, MAIN_COLOR, meta.region, 800);
		plotConnectivityLines(rupSet, resourcesDir, "sect_connectivity_hires",
				getTruncatedTitle(meta.primary.name)+" Connectivity", meta.primary.jumps, MAIN_COLOR, meta.region, 3000);
		if (meta.comparison != null) {
			plotConnectivityLines(meta.comparison.rupSet, resourcesDir, "sect_connectivity_comp",
					getTruncatedTitle(meta.comparison.name)+" Connectivity", meta.comparison.jumps,
					COMP_COLOR, meta.region, 800);
			plotConnectivityLines(meta.comparison.rupSet, resourcesDir, "sect_connectivity_comp_hires",
					getTruncatedTitle(meta.comparison.name)+" Connectivity", meta.comparison.jumps,
					COMP_COLOR, meta.region, 3000);
			plotConnectivityLines(rupSet, resourcesDir, "sect_connectivity_unique",
					getTruncatedTitle(meta.primary.name)+" Unique Connectivity", meta.primaryOverlap.uniqueJumps,
					MAIN_COLOR, meta.region, 800);
			plotConnectivityLines(rupSet, resourcesDir, "sect_connectivity_unique_hires",
					getTruncatedTitle(meta.primary.name)+" Unique Connectivity", meta.primaryOverlap.uniqueJumps,
					MAIN_COLOR, meta.region, 3000);
			plotConnectivityLines(meta.comparison.rupSet, resourcesDir, "sect_connectivity_unique_comp",
					getTruncatedTitle(meta.comparison.name)+" Unique Connectivity", meta.comparisonOverlap.uniqueJumps,
					COMP_COLOR, meta.region, 800);
			plotConnectivityLines(meta.comparison.rupSet, resourcesDir, "sect_connectivity_unique_comp_hires",
					getTruncatedTitle(meta.comparison.name)+" Unique Connectivity", meta.comparisonOverlap.uniqueJumps,
					COMP_COLOR, meta.region, 3000);
		}
		
		double maxConnDist = 0d;
		for (Jump jump : meta.primary.jumps)
			maxConnDist = Math.max(maxConnDist, jump.distance);
		if (meta.comparison != null)
			for (Jump jump : meta.comparison.jumps)
				maxConnDist = Math.max(maxConnDist, jump.distance);
		plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist",
				getTruncatedTitle(meta.primary.name)+" Connectivity", meta.primary, meta.primaryOverlap, maxConnDist,
				MAIN_COLOR, false, false);
		if (sol != null) {
			plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_rates",
					getTruncatedTitle(meta.primary.name)+" Connectivity", meta.primary, meta.primaryOverlap, maxConnDist,
					MAIN_COLOR, true, false);
			plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_rates_log",
					getTruncatedTitle(meta.primary.name)+" Connectivity", meta.primary, meta.primaryOverlap, maxConnDist,
					MAIN_COLOR, true, true);
		}
		if (meta.comparison != null) {
			plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_comp",
					getTruncatedTitle(meta.comparison.name)+" Connectivity", meta.comparison, meta.comparisonOverlap, maxConnDist,
					COMP_COLOR, false, false);
			if (meta.comparison.sol != null) {
				plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_rates_comp",
						getTruncatedTitle(meta.comparison.name)+" Connectivity", meta.comparison, meta.comparisonOverlap, maxConnDist,
						COMP_COLOR, true, false);
				plotConnectivityHistogram(resourcesDir, "sect_connectivity_hist_rates_comp_log",
						getTruncatedTitle(meta.comparison.name)+" Connectivity", meta.comparison, meta.comparisonOverlap, maxConnDist,
						COMP_COLOR, true, true);
			}
		}
		
		
		List<String> lines = new ArrayList<>();
		
		if (meta.comparison != null) {
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
			plotConnectivityLines(rupSet, resourcesDir, combConnPrefix, "Combined Connectivity",
					connectionsList, connectedColors, connNames, meta.region, 800);
			plotConnectivityLines(rupSet, resourcesDir, combConnPrefix+"_hires", "Combined Connectivity",
					connectionsList, connectedColors, connNames, meta.region, 3000);
			lines.add("![Combined]("+relPathToResources+"/"+combConnPrefix+".png)");
			lines.add("");
			
			TableBuilder table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("[View high resolution]("+relPathToResources+"/"+combConnPrefix+"_hires.png)");
			table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPathToResources+"/"+combConnPrefix+".geojson"));
			table.addColumn("[Download GeoJSON]("+relPathToResources+"/"+combConnPrefix+".geojson)");
			table.addColumn("[Download Jumps-Only GeoJSON]("+relPathToResources+"/"+combConnPrefix+"_jumps_only.geojson)");
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
					table.addColumn("*N/A*");
				} else {
					double rateInputUnique = 0d;
					for (Jump jump : meta.primaryOverlap.uniqueJumps)
						rateInputUnique += meta.primary.jumpRates.get(jump);
					table.addColumn((float)rateInputUnique+" ("+percentDF.format(
							rateInputUnique/sol.getTotalRateForAllFaultSystemRups())+")");
				}
				if (meta.comparison.sol == null) {
					table.addColumn("*N/A*");
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
		if (meta.comparison != null)
			table.addLine(meta.primary.name, meta.comparison.name);
		File mainPlot = new File(resourcesDir, "sect_connectivity.png");
		File compPlot = new File(resourcesDir, "sect_connectivity_comp.png");
		addTablePlots(table, mainPlot, compPlot, relPathToResources, meta.comparison != null);
		if (meta.comparison != null) {
			mainPlot = new File(resourcesDir, "sect_connectivity_unique.png");
			compPlot = new File(resourcesDir, "sect_connectivity_unique_comp.png");
			addTablePlots(table, mainPlot, compPlot, relPathToResources, meta.comparison != null);
		}
		mainPlot = new File(resourcesDir, "sect_connectivity_hist.png");
		compPlot = new File(resourcesDir, "sect_connectivity_hist_comp.png");
		addTablePlots(table, mainPlot, compPlot, relPathToResources, meta.comparison != null);
		if (sol != null || (meta.comparison != null && meta.comparison.sol != null)) {
			mainPlot = new File(resourcesDir, "sect_connectivity_hist_rates.png");
			compPlot = new File(resourcesDir, "sect_connectivity_hist_rates_comp.png");
			addTablePlots(table, mainPlot, compPlot, relPathToResources, meta.comparison != null);
			mainPlot = new File(resourcesDir, "sect_connectivity_hist_rates_log.png");
			compPlot = new File(resourcesDir, "sect_connectivity_hist_rates_comp_log.png");
			addTablePlots(table, mainPlot, compPlot, relPathToResources, meta.comparison != null);
		}
		lines.addAll(table.build());
		lines.add("");
		
		if (meta.comparison != null && rupSet.hasModule(RuptureConnectionSearch.class)
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
				lines.add("*N/A*");
			else
				lines.addAll(table.build());
			lines.add("");
			lines.add("**"+meta.comparison.name+" Ruptures with Unique Connections**");
			table = plotConnRupExamples(compSearch, meta.comparison.rupSet, meta.comparisonOverlap.uniqueJumps,
					meta.comparison.jumpRupsMap, maxRups, maxCols, resourcesDir, relPathToResources, "comp_conn_example");
			lines.add("");
			if (table == null)
				lines.add("*N/A*");
			else
				lines.addAll(table.build());
			lines.add("");
		}
		return lines;
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
		table.addColumn("[Download Jumps-Only GeoJSON]("+relPathToResources+"/"+prefix+"_jumps_only.geojson)");
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
			table.addColumn("*N/A*");
		}
		File compGeoJSON = null;
		if (hasComp) {
			if (compPlot.exists()) {
				table.addColumn("![plot]("+relPath+"/"+compPlot.getName()+")");
				compGeoJSON = new File(compPlot.getAbsolutePath().replaceAll(".png", ".geojson"));
			} else {
				table.addColumn("*N/A*");
			}
		}
		table.finalizeLine();
		if ((mainGeoJSON != null && mainGeoJSON.exists()) || (compGeoJSON != null && compGeoJSON.exists())) {
			table.initNewLine();
			if ((mainGeoJSON != null && mainGeoJSON.exists()))
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+mainGeoJSON.getName())
						+" "+"[Download GeoJSON]("+relPath+"/"+mainGeoJSON.getName()+")");
			else
				table.addColumn("*N/A*");
			if ((compGeoJSON != null && compGeoJSON.exists()))
				table.addColumn(RupSetMapMaker.getGeoJSONViewerRelativeLink("View GeoJSON", relPath+"/"+compGeoJSON.getName())
						+" "+"[Download GeoJSON]("+relPath+"/"+compGeoJSON.getName()+")");
			else
				table.addColumn("*N/A*");
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
		RupSetMapMaker plotter = new RupSetMapMaker(rupSet, reg);
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

}
