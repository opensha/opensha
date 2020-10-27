package scratch.kevin.ucerf3.etas;

import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;

import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.DocumentException;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYBoxAnnotation;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.jfree.chart.ui.TextAnchor;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.comcat.ComcatAccessor;
import org.opensha.commons.data.comcat.ComcatRegionAdapter;
import org.opensha.commons.data.comcat.plot.ComcatDataPlotter;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_ComcatComparePlot;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_EventMapPlotUtils;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_MFD_Plot;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_MFD_Plot.MFD_Stats;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_SimulatedCatalogPlot;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.ComcatMetadata;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_CatalogIteration;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_CatalogIteration.Callback;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.utils.FaultSystemIO;

public class RidgecrestStatsCalc {

	public static void main(String[] args) throws IOException, DocumentException {
		File gitDir = new File("/home/kevin/git/ucerf3-etas-results");
//		updateMagComplete(mainDir, "ci38457511", 3.5);
//		System.exit(0);
		boolean redoPaperFigs = false;
		
		FaultSystemSolution fss = FaultSystemIO.loadSol(new File("/home/kevin/git/ucerf3-etas-launcher/inputs/"
				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"));
		
		File outputDir = new File(gitDir, "ridgecrest_tables_figures");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		List<String> lines = new ArrayList<>();
		lines.add("# Ridgecrest M6.4 & M7.1 Summary Figures and Tables");
		lines.add("");
		
		lines.add("This is a landing page for various Ridgecrest figures and tables. Click on the simulation names"
				+ " in the tables below to see the details of each simulation, along with *many* plots.");
		lines.add("");
		lines.add("Download my [2019 AGU Fall Meeting poster here](Milner_2019_AGU_Poster.pdf), or "
				+ "[read the abstract here](https://agu.confex.com/agu/fm19/meetingapp.cgi/Paper/590411).");
		lines.add("");
		lines.add("Download my [2019 SCEC Annual Meeting poster here](Milner_2019_SCEC_Poster.pdf), or "
				+ "[read the abstract here](https://www.scec.org/publication/9401).");
		lines.add("");
		lines.add("You can also view the complete list of UCERF3-ETAS simulations [here](../README.md), though the "
				+ "list is quite long and not all are for Ridgecrest.");
		lines.add("");
		
		int tocIndex = lines.size();
		lines.add("");
		
		lines.add("## Paper Figures");
		lines.add("");
		lines.addAll(writePaperFigures(gitDir, outputDir, "###", redoPaperFigs));
		lines.add("");
		
		lines.add("## Summary Tables");
		lines.add("");
		lines.addAll(writeSummaryTableStats(gitDir, outputDir, resourcesDir, "###", fss));
		
		lines.add("## Cumulative Number Plots");
		lines.add("");
		
		lines.addAll(RidgecrestMultiSimCumulativeNumFiguresGen.buildPlots(gitDir, resourcesDir, resourcesDir.getName(), "###"));
		
		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2));
		lines.add("");
		
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	private final static Color[] colors = { Color.DARK_GRAY, Color.RED, Color.BLUE, Color.GREEN.darker(), Color.CYAN, Color.ORANGE };
	
	private static List<String> writeSummaryTableStats(File gitDir, File outputDir, File resourcesDir,
			String heading, FaultSystemSolution fss) throws IOException {
		List<List<Results>> resultSets = new ArrayList<>();
		List<String> resultSetNames = new ArrayList<>();
		
		// M6.4
		List<Results> set = new ArrayList<>();
		resultSets.add(set);
		resultSetNames.add("M6.4");
		set.add(new Results(new File(gitDir, "2019_08_20-ComCatM6p4_ci38443183_PointSources-noSpont-full_td-scale1.14"),
				"M6.4, Point Source", false));
		set.add(new Results(new File(gitDir, "2019_08_20-ComCatM6p4_ci38443183_ShakeMapSurface-noSpont-full_td-scale1.14"),
				"M6.4, ShakeMap Source", true));
		set.add(new Results(new File(gitDir, "2019_09_19-ComCatM6p4_ci38443183_PointSources_ImposeGR"),
				"M6.4, Point Source, Impose G-R", false));
		set.add(new Results(new File(gitDir, "2019_09_24-ComCatM6p4_ci38443183_PointSources_NoFaults"),
				"M6.4, Point Source, No Faults", false));
		
		// M6.4 just before 7.1
		set = new ArrayList<>();
		resultSets.add(set);
		resultSetNames.add("M6.4 (Just Before 7.1)");
		set.add(new Results(new File(gitDir, "2019_09_19-ComCatM6p4_ci38443183_1p4DaysAfter_PointSources"),
				"M6.4, Point Source, Just Before 7.1", false));
		set.add(new Results(new File(gitDir, "2019_09_19-ComCatM6p4_ci38443183_1p4DaysAfter_ShakeMapSurface"),
				"M6.4, ShakeMap Source, Just Before 7.1", true));
		
		// M7.1 Geom
		set = new ArrayList<>();
		resultSets.add(set);
		resultSetNames.add("M7.1, Geometry Variations");
		
		set.add(new Results(new File(gitDir, "2019_08_20-ComCatM7p1_ci38457511_PointSources-noSpont-full_td-scale1.14"),
				"M7.1, Point Source", false));
		
		set.add(new Results(new File(gitDir, "2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces"),
				"M7.1, ShakeMap Source", true));
		
		set.add(new Results(new File(gitDir, "2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces_CulledSurface"),
				"M7.1, ShakeMap Source (Culled)", false));
		
		set.add(new Results(new File(gitDir, "2019_09_03-ComCatM7p1_ci38457511_ShakeMapSurfaces_PlanarExtents"),
				"M7.1, ShakeMap Source (Planar Extents)", false));
		
		set.add(new Results(new File(gitDir, "2019_08_30-ComCatM7p1_ci38457511_ShakeMapSurface_Version10"),
				"M7.1, Prev ShakeMap Source (V10)", false));
		
		set.add(new Results(new File(gitDir, "2019_07_06-SearlessValleySequenceFiniteFault-noSpont-full_td-10yr-following-M7.1"),
				"M7.1, First Finite Source", false));
		
		set.add(new Results(new File(gitDir, "2019_07_11-ComCatM7p1_ci38457511_FiniteSurface-noSpont-full_td-scale1.14"),
				"M7.1, Quad Source", false));
		
		set.add(new Results(new File(gitDir, "2019_07_18-ComCatM7p1_ci38457511_InvertedSurface_ShakeMapSurface-noSpont-full_td-scale1.14"),
				"M7.1, Inverted Source", false));
		
		set.add(new Results(new File(gitDir, "2019_08_20-ComCatM7p1_ci38457511_InvertedSurface_minSlip0p5_ShakeMapSurface-noSpont-full_td-scale1.14"),
				"M7.1, Inverted Source (minSlip=0.5)", false));
		
		set.add(new Results(new File(gitDir, "2019_09_17-ComCatM7p1_ci38457511_KMLSurface_ShakeMapSurface"),
				"M7.1, KML Surface Rupture Source", false));
		
		set.add(new Results(new File(gitDir, "2019_09_17-ComCatM7p1_ci38457511_KMLSurface_ShakeMapSurface_FieldVerified"),
				"M7.1, KML Surface Rupture Source (Field Verified Only)", false));
		
		// M7.1 Params
		set = new ArrayList<>();
		resultSets.add(set);
		resultSetNames.add("M7.1, Parameter Variations");
		
		set.add(new Results(new File(gitDir, "2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces"),
				"M7.1, ShakeMap Source", true));
		
		set.add(new Results(new File(gitDir, "2019_08_20-ComCatM7p1_ci38457511_ShakeMapSurfaces_NoFaults-noSpont-poisson-griddedOnly"),
				"M7.1, ShakeMap Source, No Faults", false));
		
		set.add(new Results(new File(gitDir, "2019_08_09-ComCatM7p1_ci38457511_ShakeMapSurfaces_NoERT-noSpont-no_ert"),
				"M7.1, ShakeMap Source, NoERT Branch", false));
		
		set.add(new Results(new File(gitDir, "2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces_FM3_2"),
				"M7.1, ShakeMap Source, FM 3.2", false));
		
		set.add(new Results(new File(gitDir, "2019_07_16-ComCatM7p1_ci38457511_ShakeMapSurfaces-noSpont-full_td-scale1.14"),
				"M7.1, ShakeMap Source, Early Catalog", false));
		
		set.add(new Results(new File(gitDir, "2019_08_30-ComCatM7p1_ci38457511_MainshockLog10_k_2p3_ShakeMapSurfaces_Log10_k_3p03_p1p15_c0p002"),
				"M7.1, ShakeMap Source, Seq. Specific", false));
		
		set.add(new Results(new File(gitDir, "2019_09_02-ComCatM7p1_ci38457511_ShakeMapSurfaces_ScaleFactor1p0"),
				"M7.1, ShakeMap Source, No TotRateScaleFactor)", false));
		
		set.add(new Results(new File(gitDir, "2019_09_03-ComCatM7p1_ci38457511_PointSources_NoFaults"),
				"M7.1, Point Source, No Faults)", false));
		
		// M7.1 7 Days After
		set = new ArrayList<>();
		resultSets.add(set);
		resultSetNames.add("M7.1, 7 Days After");
		
		set.add(new Results(new File(gitDir, "2019_09_12-ComCatM7p1_ci38457511_7DaysAfter_ShakeMapSurfaces"),
				"M7.1, ShakeMap Source, 7 Days After", true));
		
		set.add(new Results(new File(gitDir, "2019_07_16-ComCatM7p1_ci38457511_7DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14"),
				"M7.1, ShakeMap Source, 7 Days After (Early Catalog)", false));
		
		set.add(new Results(new File(gitDir, "2019_09_12-ComCatM7p1_ci38457511_7DaysAfter_ShakeMapSurfaces_NoFaults"),
				"M7.1, ShakeMap Source, No Faults, 7 Days After", false));
		
		set.add(new Results(new File(gitDir, "2019_09_09-ComCatM7p1_ci38457511_7DaysAfter_PointSources"),
				"M7.1, Point Source, 7 Days After", false));
		
		set.add(new Results(new File(gitDir, "2019_07_12-ComCatM7p1_ci38457511_7DaysAfter_FiniteSurface-noSpont-full_td-scale1.14"),
				"M7.1, Quad Source, 7 Days After", false));
		
		set.add(new Results(new File(gitDir, "2019_09_09-ComCatM7p1_ci38457511_7DaysAfter_ShakeMapSurfaces_PlanarExtents"),
				"M7.1, ShakeMap Source (Planar Extents), 7 Days After", false));
		
		set.add(new Results(new File(gitDir, "2019_09_09-ComCatM7p1_ci38457511_7DaysAfter_MainshockLog10_k_2p3_ShakeMapSurfaces_Log10_k_3p03_p1p15"),
				"M7.1, ShakeMap Source, Seq. Specific, 7 Days After", false));
		
		set.add(new Results(new File(gitDir, "2019_09_18-ComCatM7p1_ci38457511_7DaysAfter_KMLSurface_ShakeMapSurface"),
				"M7.1, KML Surface Rupture Source, 7 Days After", false));
		
		set.add(new Results(new File(gitDir, "2019_09_18-ComCatM7p1_ci38457511_7DaysAfter_KMLSurface_ShakeMapSurface_FieldVerified"),
				"M7.1, KML Surface Rupture Source (Field Verified Only), 7 Days After", false));
		
		// M7.1 28 Days After
		set = new ArrayList<>();
		resultSets.add(set);
		resultSetNames.add("M7.1, 28 Days After");
		
		set.add(new Results(new File(gitDir, "2019_09_12-ComCatM7p1_ci38457511_28DaysAfter_ShakeMapSurfaces"),
				"M7.1, ShakeMap Source, 28 Days After", true));
		
		set.add(new Results(new File(gitDir, "2019_08_03-ComCatM7p1_ci38457511_28DaysAfter_ShakeMapSurfaces-noSpont-full_td-scale1.14"),
				"M7.1, ShakeMap Source, 28 Days After (Early Catalog)", false));
		
		set.add(new Results(new File(gitDir, "2019_08_20-ComCatM7p1_ci38457511_28DaysAfter_PointSources-noSpont-full_td-scale1.14"),
				"M7.1, Point Source, 28 Days After", false));
		
		set.add(new Results(new File(gitDir, "2019_08_20-ComCatM7p1_ci38457511_28DaysAfter_ShakeMapSurfaces_NoFaults-noSpont-poisson-griddedOnly"),
				"M7.1, ShakeMap Source, No Faults, 28 Days After", false));
		
		set.add(new Results(new File(gitDir, "2019_09_09-ComCatM7p1_ci38457511_28DaysAfter_ShakeMapSurfaces_PlanarExtents"),
				"M7.1, ShakeMap Source (Planar Extents), 28 Days After", false));
		
		set.add(new Results(new File(gitDir, "2019_08_31-ComCatM7p1_ci38457511_28DaysAfter_MainshockLog10_k_2p3_ShakeMapSurfaces_Log10_k_3p03_p1p15_c0p002"),
				"M7.1, ShakeMap Source, Seq. Specific, 28 Days After", false));
		
		set.add(new Results(new File(gitDir, "2019_09_18-ComCatM7p1_ci38457511_28DaysAfter_KMLSurface_ShakeMapSurface"),
				"M7.1, KML Surface Rupture Source, 28 Days After", false));
		
		set.add(new Results(new File(gitDir, "2019_09_18-ComCatM7p1_ci38457511_28DaysAfter_KMLSurface_ShakeMapSurface_FieldVerified"),
				"M7.1, KML Surface Rupture Source (Field Verified Only), 28 Days After", false));
		
		List<String> header = new ArrayList<>();
		header.add("Name");
		header.add("1 Week Prob M≥7.1");
		header.add("1 Month Prob M≥7.1");
		header.add("1 Month Prob M≥7");
		header.add("1 Month Mean Num M≥3.5");
		header.add("1 Month Median Num M≥3.5");
		header.add("1 Month Garlock Prob M≥7");
		header.add("1 Month SAF Mojave Prob M≥7");
		
		List<String> lines = new ArrayList<>();
		
		// add faults to bottom of plot
		List<XY_DataSet> inputFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> inputChars = new ArrayList<>();
		populateMapInputFuncs(fss.getRupSet().getFaultSectionDataList(), inputFuncs, inputChars,
				Color.LIGHT_GRAY, Color.LIGHT_GRAY, null, false);
		
		for (int i=0; i<resultSets.size(); i++) {
			CSVFile<String> summaryTable = new CSVFile<>(false);
			
			String setName = resultSetNames.get(i);
			if (!lines.isEmpty())
				lines.add("");
			lines.add(heading+" "+setName+" Summary Table");
			lines.add("");
			
			TableBuilder table = MarkdownUtils.tableBuilder();
			table.addLine(header);
			
			String prefix = setName.replaceAll("\\W+", "_");
			while (prefix.contains("__"))
				prefix = prefix.replaceAll(" _", "_");
			
			summaryTable.addLine(header);
			List<List<Double>> valsLists = new ArrayList<>();
			List<Double> baselineVals = null;
			String baselineName = null;
			for (Results r : resultSets.get(i)) {
				List<Double> vals = new ArrayList<>();
				
				vals.add(r.weekMFD.getProbFunc(0).getInterpolatedY(7.1d));
				vals.add(r.monthMFD.getProbFunc(0).getInterpolatedY(7.1d));
				vals.add(r.monthMFD.getProbFunc(0).getInterpolatedY(7d));
				vals.add(r.monthMFD.getMeanFunc(0).getInterpolatedY(3.5d));
				vals.add(r.monthMFD.getMedianFunc(0).getInterpolatedY(3.5d));
				if (r.garlockProbs == null)
					vals.add(Double.NaN);
				else
					vals.add(r.garlockProbs.get("1 mo ETAS Prob").getInterpolatedY(7d));
				if (r.mojaveProbs == null)
					vals.add(Double.NaN);
				else
					vals.add(r.mojaveProbs.get("1 mo ETAS Prob").getInterpolatedY(7d));
				
				valsLists.add(vals);
				if (r.baseline) {
					Preconditions.checkState(baselineVals == null);
					baselineName = r.name;
					baselineVals = vals;
				}
				List<String> line = new ArrayList<>();
				line.add("["+r.name+"](../"+r.dir.getName()+"/README.md)");
				for (int j = 0; j < vals.size(); j++) {
					Double val = vals.get(j);
					if (Double.isFinite(val)) {
						if (header.get(j+1).contains("Num"))
							line.add(countDF.format(val));
						else
							line.add(probDF.format(val));
					} else {
						line.add("N/A");
					}
				}
				summaryTable.addLine(line);
				table.initNewLine();
				if (r.baseline)
					for (String str : line)
						table.addColumn("*"+str+"*");
				else
					for (String str : line)
						table.addColumn(str);
				table.finalizeLine();
			}
			List<String> rangeLine = new ArrayList<>();
			rangeLine.add("Range");
			summaryTable.addLine(rangeLine);
			List<String> gainLine = null;
			if (baselineVals != null) {
				gainLine = new ArrayList<>();
				gainLine.add("Gain (w.r.t. "+baselineName+")");
				summaryTable.addLine(gainLine);
			}
			
			for (int j=0; j<valsLists.get(0).size(); j++) {
				List<Double> allVals = new ArrayList<>();
				for (List<Double> list : valsLists) {
					double val = list.get(j);
					if (Double.isFinite(val))
						allVals.add(val);
				}
				if (allVals.isEmpty()) {
					rangeLine.add("N/A");
					if (gainLine != null)
						gainLine.add("N/A");
				} else {
					double minVal = Double.POSITIVE_INFINITY;
					double maxVal = Double.NEGATIVE_INFINITY;
					for (double val : allVals) {
						minVal = Double.min(minVal, val);
						maxVal = Double.max(maxVal, val);
					}
					boolean count = header.get(j+1).contains("Num");
					if (count)
						rangeLine.add("["+countDF.format(minVal)+" "+countDF.format(maxVal)+"]");
					else
						rangeLine.add("["+probDF.format(minVal)+" "+probDF.format(maxVal)+"]");
					if (gainLine != null) {
						double bVal = baselineVals.get(j);
						double minGain = minVal/bVal;
						double maxGain = maxVal/bVal;
						gainLine.add("["+gainDF.format(minGain)+" "+gainDF.format(maxGain)+"]");
					}
				}
			}
			table.initNewLine();
			for (String str : rangeLine)
				table.addColumn("**"+str+"**");
			table.finalizeLine();
			if (gainLine != null) {
				table.initNewLine();
				for (String str : gainLine)
					table.addColumn("**"+str+"**");
				table.finalizeLine();
			}
			lines.addAll(table.build());
			summaryTable.writeToFile(new File(outputDir, "probs_summary_"+prefix+".csv"));
			
			// add geometry plots
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			List<String> names = new ArrayList<>();
			List<ETAS_Config> configs = new ArrayList<>(); 
			Results baseline = null;
			for (Results r : resultSets.get(i)) {
				if (r.baseline)
					baseline = r;
				table.addColumn("**["+r.name+"](../"+r.dir.getName()+"/README.md)**");
				names.add(r.name);
				configs.add(ETAS_Config.readJSON(r.configFile));
			}
			table.finalizeLine();
			table.initNewLine();
			
			double maxY = 0d;
			for (Results r : resultSets.get(i))
				maxY = Math.max(maxY, StatUtils.max(buildPlotVals(r)));
			System.out.println("MaxY for "+setName+": "+(float)maxY);
			
			for (int j=0; j<names.size(); j++) {
				Results r = resultSets.get(i).get(j);
				String mapPrefix = "trigger_map_"+r.dir.getName();
				HeadlessGraphPanel mapGP = writeMapPlot(configs.get(j), names.get(j),
						resourcesDir, mapPrefix, inputFuncs, inputChars);
				String chartPrefix = "prob_chart_"+r.dir.getName();
				HeadlessGraphPanel chartGP = writeProbChartPlot(resourcesDir, r, baseline, chartPrefix, maxY);
				File combFile = new File(resourcesDir, "comb_chart_map_"+r.dir.getName()+".png");
				mergePlots(chartGP, mapGP, combFile);
				table.addColumn("![Map]("+resourcesDir.getName()+"/"+combFile.getName()+")");
			}
			table.finalizeLine();
			
			lines.add(heading+"# "+setName+" Input Maps");
			lines.add("");
			lines.addAll(table.wrap(4, 0).build());
		}
		return lines;
	}
	
	private static void populateMapInputFuncs(List<? extends FaultSection> sects, List<XY_DataSet> inputFuncs,
			List<PlotCurveCharacterstics> inputChars, Color traceColor, Color outlineColor, Color polygonColor, boolean cullSects) {
		PlotCurveCharacterstics faultTraceChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, polygonColor == null ? 2f : 4f, traceColor);
		PlotCurveCharacterstics faultOutlineChar = new PlotCurveCharacterstics(PlotLineType.DOTTED, 1.5f, outlineColor);
		PlotCurveCharacterstics faultPolyChar = null;
		List<Region> polys = null;
		if (polygonColor != null) {
			faultPolyChar = new PlotCurveCharacterstics(PlotLineType.DASHED, 1.5f, polygonColor);
			FaultPolyMgr polyMgr = FaultPolyMgr.create(sects, InversionTargetMFDs.FAULT_BUFFER);
			polys = new ArrayList<>();
			for (int s=0; s<sects.size(); s++)
				polys.add(polyMgr.getPoly(s));
		}

		boolean firstTrace = true;
		boolean firstOutline = true;
		boolean firstPoly = true;
		for (FaultSection sect : sects) {
			String name = sect.getName();
			boolean nameMatch = name.contains("Garlock") || name.contains("Airport Lake")
					|| name.contains("Little Lake");
			if (cullSects && !nameMatch)
				continue;
			RuptureSurface surf = sect.getFaultSurface(1d, false, false);
			List<XY_DataSet> outlines = ETAS_EventMapPlotUtils.getSurfOutlines(surf);
			for (XY_DataSet outline : outlines) {
				if (firstOutline && !firstTrace) {
					outline.setName("Down Dip");
					firstOutline = false;
				}
				inputFuncs.add(outline);
				inputChars.add(faultOutlineChar);
			}
			List<XY_DataSet> traces = ETAS_EventMapPlotUtils.getSurfTraces(surf);
			for (int i=0; i<traces.size(); i++) {
				XY_DataSet trace = traces.get(i);
				if (firstTrace) {
					trace.setName("Traces");
					firstTrace = false;
				}
				inputFuncs.add(trace);
				inputChars.add(faultTraceChar);
			}
			if (polygonColor != null) {
				Region poly = polys.get(sect.getSectionId());
				if (poly != null) {
					XY_DataSet polyXY = new DefaultXY_DataSet();
					for (Location loc : poly.getBorder())
						polyXY.set(loc.getLongitude(), loc.getLatitude());
					polyXY.set(polyXY.get(0)); // close it
					if (firstPoly) {
						polyXY.setName("Fault Polygons");
						firstPoly = false;
					}
					inputFuncs.add(polyXY);
					inputChars.add(faultPolyChar);
				}
			}
		}
	}
	
	private static final DecimalFormat probDF = new DecimalFormat("0.000%");
	private static final DecimalFormat countDF = new DecimalFormat("0.0");
	private static final DecimalFormat gainDF = new DecimalFormat("0.00");
	
	private static class Results {
		final File dir;
		final String name;

		final MFD_Stats dayMFD;
		final MFD_Stats weekMFD;
		final MFD_Stats monthMFD;
		final MFD_Stats yearMFD;
		
		final Map<String, DiscretizedFunc> garlockProbs;
		final Map<String, DiscretizedFunc> mojaveProbs;
		private boolean baseline;
		
		final File configFile;
		
		public Results(File dir, String name, boolean baseline) throws IOException {
			this.dir = dir;
			this.name = name;
			this.baseline = baseline;
			this.configFile = new File(dir, "config.json");
			
			System.out.println("Loading "+name+" from: "+dir.getName());
			
			File plotsDir = new File(dir, "plots");
			
			dayMFD = ETAS_MFD_Plot.readCSV(new File(plotsDir, "1d_mag_num_cumulative_triggered.csv"));
			weekMFD = ETAS_MFD_Plot.readCSV(new File(plotsDir, "1wk_mag_num_cumulative_triggered.csv"));
			monthMFD = ETAS_MFD_Plot.readCSV(new File(plotsDir, "1mo_mag_num_cumulative_triggered.csv"));
			yearMFD = ETAS_MFD_Plot.readCSV(new File(plotsDir, "1yr_mag_num_cumulative_triggered.csv"));
			
			File sectsDir = new File(plotsDir, "parent_sect_mpds");
			File garlockFile = new File(sectsDir, "Garlock_Central.csv");
			File mojaveFile = new File(sectsDir, "San_Andreas_Mojave_N.csv");
			
			garlockProbs = garlockFile.exists() ? loadSectCSV(garlockFile) : null;
			mojaveProbs = mojaveFile.exists() ? loadSectCSV(mojaveFile) : null;
		}
	}
	
	private static Map<String, DiscretizedFunc> loadSectCSV(File csvFile) throws IOException {
		CSVFile<String> csv = CSVFile.readFile(csvFile, true);
		
		Map<String, DiscretizedFunc> probs = new HashedMap<>();
		for (int col=1; col<csv.getNumCols(); col++) {
			String name = csv.get(0, col);
			ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc(name);
			for (int i=0; i<csv.getNumRows()-1; i++) {
				int row = i+1;
				double x = csv.getDouble(row, 0);
				double y = csv.getDouble(row, col);
				func.set(x, y);
			}
			probs.put(name, func);
		}
		return probs;
	}
	
	private static final Region mapRegion = new Region(new Location(36.4, -118.3), new Location(35.3, -117));
	
	private static HeadlessGraphPanel writeMapPlot(ETAS_Config config, String name, File outputDir, String prefix,
			List<XY_DataSet> inputFuncs, List<PlotCurveCharacterstics> inputChars) throws IOException {
		double minMag = 3d;
		double maxMag = 7.1d;
		ObsEqkRupList triggers = new ObsEqkRupList();
		for (TriggerRupture trigger : config.getTriggerRuptures()) {
			ETAS_EqkRupture rup = trigger.buildRupture(null, config.getSimulationStartTimeMillis(), null);
			if (rup.getMag() >= minMag)
				triggers.add(rup);
		}
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		if (!config.isGriddedOnly()) {
			funcs.addAll(inputFuncs);
			chars.addAll(inputChars);
		}
		
		ETAS_EventMapPlotUtils.buildEventPlot(triggers, funcs, chars, maxMag);
		
		return ETAS_EventMapPlotUtils.writeMapPlot(funcs, chars, mapRegion, name, outputDir, prefix, 800);
	}
	
	private static HeadlessGraphPanel writeProbChartPlot(File outputDir, Results result, Results baseline,
			String prefix, double maxY) throws IOException {
		List<XYAnnotation> anns = new ArrayList<>();
		
		String[] labels = {
				"1 Week\nAny M≥7.1",
				"1 Month\nAny M≥7.1",
				"1 Month\nGarlock M≥7",
				"1 Month\nMojave M≥7"
		};
		double[] vals = buildPlotVals(result);
		double[] baseVals = baseline == null ? null : buildPlotVals(baseline);
		
		// rescale for room at the top for labels
		maxY *= 1.4;
		
		double buffer = 0.07;
		
		Stroke baseStroke = PlotLineType.SOLID.buildStroke(3f);
		
		double textY = maxY*0.95;
		Font textFont = new Font(Font.SANS_SERIF, Font.BOLD, 20);
		
		for (int i=0; i<vals.length; i++) {
			double minX = i+buffer;
			double maxX = i+1-buffer;
			double centerX = i + 0.5;
			
			Color c = colors[i % colors.length];
			
			if (vals[i] > 0)
				anns.add(new XYBoxAnnotation(minX, 0, maxX, vals[i], null, null, c));
			
			if (baseVals != null)
				anns.add(new XYLineAnnotation(minX, baseVals[i], maxX, baseVals[i], baseStroke, Color.BLACK));
			
			String label = labels[i];
			String[] lines = label.split("\n");
			double ty = textY;
			for (String line : lines) {
//				System.out.println(line +" at "+ty);
				XYTextAnnotation ann = new XYTextAnnotation(line, centerX, ty);
				ty -= 0.07*maxY;
				ann.setFont(textFont);
				ann.setTextAnchor(TextAnchor.TOP_CENTER);
				anns.add(ann);
			}
		}
		
		// add a fake XY element
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		XY_DataSet blank = new DefaultXY_DataSet();
		blank.set(-1d, -1d);
		blank.set(-1d, -2d);
		funcs.add(blank);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		
		if (baseline != null) {
			blank = new DefaultXY_DataSet();
			blank.setName("Baseline Model: "+baseline.name);
			blank.set(-1d, -1d);
			blank.set(-1d, -2d);
			funcs.add(blank);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, result.name, null, "Probability (%)");
		spec.setLegendVisible(baseline != null);
		spec.setPlotAnnotations(anns);
		
		HeadlessGraphPanel gp = ETAS_AbstractPlot.buildGraphPanel();
		
		gp.drawGraphPanel(spec, false, false, new Range(0, vals.length), new Range(0, maxY));
		gp.getXAxis().setTickLabelsVisible(false);
		gp.getPlot().setDomainGridlinesVisible(false);
		gp.getChartPanel().setSize(800, 450);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
		return gp;
	}
	
	private static double[] buildPlotVals(Results r) {
		double[] vals = new double[4];
		vals[0] = r.dayMFD.getProbFunc(0).getInterpolatedY(7.1)*100d;
		vals[1] = r.weekMFD.getProbFunc(0).getInterpolatedY(7.1)*100d;
		if (r.garlockProbs != null)
			vals[2] = r.garlockProbs.get("1 mo ETAS Prob").getInterpolatedY(7d)*100d;
		if (r.mojaveProbs != null)
			vals[3] = r.mojaveProbs.get("1 mo ETAS Prob").getInterpolatedY(7d)*100d;
		return vals;
	}
	
	private static void mergePlots(HeadlessGraphPanel top, HeadlessGraphPanel bottom, File outputFile) throws IOException {
		ChartPanel topCP = top.getChartPanel();
		ChartPanel bottomCP = bottom.getChartPanel();
		if (topCP.getChart().getTitle().getText().equals(bottomCP.getChart().getTitle().getText()))
			bottomCP.getChart().setTitle("");
		int topHeight = topCP.getHeight();
		int topWidth = topCP.getWidth();
		int botHeight = bottomCP.getHeight();
		int botWidth = bottomCP.getWidth();
		
		BufferedImage topBI = top.getBufferedImage(topWidth, topHeight);
		BufferedImage botBI = bottom.getBufferedImage(botWidth, botHeight);
		BufferedImage comb = new BufferedImage(Integer.max(topWidth, botWidth), topHeight+botHeight,
				BufferedImage.TYPE_INT_RGB);
		for (int x=0; x<botWidth; x++)
			for (int y=0; y<botHeight; y++)
				comb.setRGB(x, y+topHeight, botBI.getRGB(x, y));
		for (int x=0; x<topWidth; x++)
			for (int y=0; y<topHeight; y++)
				comb.setRGB(x, y, topBI.getRGB(x, y));
		ImageIO.write(comb, "png", outputFile);
	}
	
	private static List<String> writePaperFigures(File gitDir, File outputDir,
			String heading, boolean redoResultPlots) throws IOException {
		List<String> lines = new ArrayList<>();
		
		File paperDir = new File(outputDir, "paper_figures");
		Preconditions.checkState(paperDir.exists() || paperDir.mkdir());
		
		lines.add(heading+" Input Plots");
		lines.add("");
		
		List<XY_DataSet> inputFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> inputChars = new ArrayList<>();
		ETAS_Config initialConfig = ETAS_Config.readJSON(
				new File(gitDir, "2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces/config.json"));
		FaultSystemSolution fss = initialConfig.loadFSS();
		ETAS_Config sevenDayConfig = ETAS_Config.readJSON(
				new File(gitDir, "2019_09_12-ComCatM7p1_ci38457511_7DaysAfter_ShakeMapSurfaces/config.json"));
		List<? extends FaultSection> sects = FaultModels.FM3_1.fetchFaultSections();
		for (int i=0; i<sects.size(); i++) {
			FaultSection sect = sects.get(i);
			// hack needed to make it work with parent sections
			sect.setParentSectionId(i);
			sect.setSectionId(i);
		}
		populateMapInputFuncs(sects, inputFuncs, inputChars, Color.BLACK, Color.GRAY, Color.DARK_GRAY, true);
		Region geomRegion = new Region(new Location(35.45, -117.3), new Location(36, -117.8));
		
		writeInputsPlot(paperDir, "input_events_faults", geomRegion, initialConfig, sevenDayConfig, inputFuncs, inputChars);
		
		lines.add("This plot shows the first few finite fault surfaces used");
		lines.add("");
		lines.add("![Map]("+paperDir.getName()+"/input_events_faults.png)");
		
		lines.add(heading+" Result Plots");
		lines.add("");
		
		List<File> dirs = new ArrayList<>();

		dirs.add(new File(gitDir, "2019_08_20-ComCatM6p4_ci38443183_PointSources-noSpont-full_td-scale1.14"));
		dirs.add(new File(gitDir, "2019_09_24-ComCatM6p4_ci38443183_PointSources_NoFaults"));
		dirs.add(new File(gitDir, "2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces"));
		dirs.add(new File(gitDir, "2019_08_20-ComCatM7p1_ci38457511_PointSources-noSpont-full_td-scale1.14"));
		dirs.add(new File(gitDir, "2019_09_12-ComCatM7p1_ci38457511_7DaysAfter_ShakeMapSurfaces"));
		dirs.add(new File(gitDir, "2019_09_09-ComCatM7p1_ci38457511_7DaysAfter_PointSources"));
		dirs.add(new File(gitDir, "2019_08_20-ComCatM7p1_ci38457511_ShakeMapSurfaces_NoFaults-noSpont-poisson-griddedOnly"));
		dirs.add(new File(gitDir, "2019_09_12-ComCatM7p1_ci38457511_7DaysAfter_ShakeMapSurfaces_NoFaults"));
		dirs.add(new File(gitDir, "2019_08_30-ComCatM7p1_ci38457511_MainshockLog10_k_2p3_ShakeMapSurfaces_Log10_k_3p03_p1p15_c0p002"));
		
		CPT cpt = GMT_CPT_Files.BLACK_RED_YELLOW_UNIFORM.instance().reverse();
		Color dataColor = Color.CYAN;
//		CPT cpt = null;
//		Color dataColor = Color.BLACK;
//		System.out.println(cpt);
//		System.exit(0);
		double minZ = 1e-4;
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("Simulation", "1-Month MND", "Comcat Mag/Num", "1-Month M&ge;3.7 Map", "M&ge;3.7 Time Func");
		
		ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		
		List<ETAS_Config> configs = new ArrayList<>();
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (File dir : dirs) {
			ETAS_Config config = ETAS_Config.readJSON(new File(dir, "config.json"));
			configs.add(config);
			Region reg = config.getComcatMetadata().region;
			GriddedRegion gridReg = new GriddedRegion(reg, 0.02, GriddedRegion.ANCHOR_0_0);
			latTrack.addValue(gridReg.getMaxGridLat());
			latTrack.addValue(gridReg.getMinGridLat());
			lonTrack.addValue(gridReg.getMaxGridLon());
			lonTrack.addValue(gridReg.getMinGridLon());
		}
		
		Region region = new Region(new Location(latTrack.getMin()-0.01, lonTrack.getMin()-0.01),
				new Location(latTrack.getMax()+0.01, lonTrack.getMax()+0.01));
		double magComplete = 3.5;
		
		for (int i=0; i<dirs.size(); i++) {
			File dir = dirs.get(i);
			ETAS_Config config = configs.get(i);
			File subDir = new File(paperDir, dir.getName());
			boolean plot = redoResultPlots || !subDir.exists();
			System.out.println("Processing "+dir.getName());
			File[] plotFiles = {
					new File(subDir, "1mo_mag_num_cumulative_triggered.png"),
					new File(subDir, "comcat_compare_mag_num.png"),
					new File(subDir, "comcat_compare_prob_1mo_m"+(float)magComplete+".png"),
					new File(subDir, "comcat_compare_cumulative_num_m"+(float)magComplete+".png"),
			};
			table.initNewLine();
			table.addColumn("["+config.getSimulationName()+"](../"+dir.getName()+"/README.md)");
			for (File plotFile : plotFiles) {
				table.addColumn("![Plot]("+paperDir.getName()+"/"+dir.getName()+"/"+plotFile.getName()+")");
				if (!plotFile.exists()) {
					System.out.println("\twill update due to missing plot, "+plotFile.getName());
					plot = true;
				}
			}
			table.finalizeLine();
			if (plot) {
				Preconditions.checkState(subDir.exists() || subDir.mkdir());
				ComcatMetadata meta = config.getComcatMetadata();
				meta = new ComcatMetadata(region, meta.eventID, meta.minDepth, meta.maxDepth,
						meta.minMag, meta.startTime, meta.endTime);
				meta.magComplete = magComplete;
				config.setComcatMetadata(meta);
				System.out.println("Building result plots for "+config.getSimulationName());
				ETAS_Launcher launcher = new ETAS_Launcher(config, false);
				ETAS_MFD_Plot mfdPlot = new ETAS_MFD_Plot(config, launcher, "mag_num_cumulative", false, true);
				mfdPlot.setHideTitles();
				mfdPlot.setIncludeMedian(false);
				mfdPlot.setIncludeMode(false);
				mfdPlot.setIncludePrimary(false);
				mfdPlot.setProbColor(Color.GRAY);
				ETAS_ComcatComparePlot comcatPlot = new ETAS_ComcatComparePlot(config, launcher);
				ComcatDataPlotter plotter = comcatPlot.getPlotter();
				plotter.setNoTitles(true);
				comcatPlot.setMapCPT(cpt);
				comcatPlot.setMapMinZ(minZ);
				comcatPlot.setMapDataColor(dataColor);
				plotter.setDataColor(Color.GRAY);
				plotter.setPlotIncludeMedian(true);
				plotter.setPlotIncludeMean(false);
				plotter.setPlotIncludeMode(false);
				plotter.setTimeFuncMaxY(650d);
				Map<Double, String> anns = new HashMap<>();
				long fitDate = new GregorianCalendar(2019, 7, 26).getTimeInMillis(); // DOM is 0-based
				double fitDays = (double)(fitDate - config.getSimulationStartTimeMillis())
							/(double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
				anns.put(fitDays, "Parameters fit on 8/26/2019");
				plotter.setTimeFuncAnns(anns);
				
				File inputFile = new File(config.getOutputDir(), "results_complete.bin");
				processPlots(config, inputFile, subDir, fss, exec, mfdPlot, comcatPlot);
			}
		}
		lines.addAll(table.build());
		
		lines.add(heading+" Preferred Model Individual Catalog Plots");
		lines.add("");
		
		File modelDir = new File(gitDir, "2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces");
		File pOutDir = new File(paperDir, modelDir.getName());
		double[] percentiles = { 50d, 97.5d, 99.999 };
		File[] pFiles = new File[percentiles.length];
		for (int p=0; p<percentiles.length; p++)
			pFiles[p] = new File(pOutDir, "sim_catalog_map_p"+ETAS_SimulatedCatalogPlot.pDF.format(percentiles[p])+"_1mo.png");
		File obsPOutDir = new File(paperDir, "observed_percentile");
		Preconditions.checkState(obsPOutDir.exists() || obsPOutDir.mkdir());
		double[] obsPercentileValues = { 50d, 97.5d };
		File observedCatalogPercentilePlot = new File(obsPOutDir,
				"obs_catalog_map_p"+ETAS_SimulatedCatalogPlot.pDF.format(obsPercentileValues[obsPercentileValues.length-1])+"_1mo.png");
		boolean redoPrecentiles = redoResultPlots;
		for (File pFile : pFiles) {
			if (!pFile.exists()) {
				System.out.println("Missing percentile file, redoing: "+pFile.getAbsolutePath());
				redoPrecentiles = true;
			}
		}
		
		Region percentileRegion = new Region(new Location(33d, -115.5d), new Location(36.5d, -120d));
		
		if (redoPrecentiles) {
			ETAS_Config config = ETAS_Config.readJSON(new File(modelDir, "config.json"));
			ETAS_Launcher launcher = new ETAS_Launcher(config, false);
			
			ETAS_SimulatedCatalogPlot plot = new ETAS_SimulatedCatalogPlot(config, launcher, "sim_catalog_map", percentiles);
			plot.setHideTitles();
			plot.setHideInputEvents();
			plot.setForceRegion(percentileRegion);
			
			File inputFile = new File(config.getOutputDir(), "results_complete.bin");
			processPlots(config, inputFile, pOutDir, fss, exec, plot);
		}
		
		ComcatAccessor accessor = new ComcatAccessor();
		ComcatMetadata meta = initialConfig.getComcatMetadata();
		ObsEqkRupList obsPercentileRegionEQs = accessor.fetchEventList(meta.eventID, initialConfig.getSimulationStartTimeMillis(),
				System.currentTimeMillis(), meta.minDepth, meta.maxDepth, new ComcatRegionAdapter(percentileRegion),
				false, false, meta.minMag);
		obsPercentileRegionEQs.sortByOriginTime();
		
		double[] obsPercentileMags = { 2.5d, 3.5d };
		double[] obsPercentileDurations = { 1d/365.25, 7d/365.25, 30d/365.25 };
		String percentileCSVPrefix = "percentiles";
		File[] percentileCSVs = new File[obsPercentileMags.length];
		boolean redoPercentileCSVs = false;
		for (int i=0; i<percentileCSVs.length; i++) {
			percentileCSVs[i] = new File(paperDir, percentileCSVPrefix+"_m"+(float)obsPercentileMags[i]+".csv");
			redoPercentileCSVs = redoPercentileCSVs || !percentileCSVs[i].exists();
		}
		if (redoPercentileCSVs) {
			File inputFile = new File(initialConfig.getOutputDir(), "results_complete.bin");
			System.out.println("Writing percentile CSVs...");
			writePercentilesCSV(initialConfig, paperDir, percentileCSVPrefix, inputFile, obsPercentileMags, obsPercentileDurations);
		}
		
		if (!observedCatalogPercentilePlot.exists()) {
			System.out.println("Writing observed map in percentiles region...");
			ETAS_Config config = configs.get(0);
			ETAS_Launcher launcher = new ETAS_Launcher(config, false);
			
			ETAS_SimulatedCatalogPlot plot = new ETAS_SimulatedCatalogPlot(config, launcher, "obs_catalog_map", obsPercentileValues);
			plot.setHideTitles();
			plot.setHideInputEvents();
			plot.setMaxMag(8.1d);
			plot.setForceRegion(percentileRegion);
			
			ETAS_Catalog catalog = new ETAS_Catalog(null);
			
			for (ObsEqkRupture rup : obsPercentileRegionEQs) {
				ETAS_EqkRupture eRup = new ETAS_EqkRupture(rup);
				eRup.setOriginTime(rup.getOriginTime());
				catalog.add(eRup);
			}

			plot.processCatalog(catalog, fss);
			plot.processCatalog(catalog, fss);
			plot.processCatalog(catalog, fss);
			plot.processCatalog(catalog, fss);
			
			plot.finalize(obsPOutDir, fss);
		}
		
		exec.shutdown();
		
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		for (double percentile : percentiles)
			table.addColumn("p"+ETAS_SimulatedCatalogPlot.pDF.format(percentile)+" %-ile Catalog");
		table.finalizeLine();
		table.initNewLine();
		for (File pFile : pFiles)
			table.addColumn("![Map]("+paperDir.getName()+"/"+pOutDir.getName()+"/"+pFile.getName()+")");
		table.finalizeLine();
		lines.addAll(table.build());
		lines.add("");
		
		lines.add(heading+" Observed Event Plot (in percentile region and style)");
		lines.add("");
		lines.add("![Map]("+paperDir.getName()+"/"+obsPOutDir.getName()+"/"+observedCatalogPercentilePlot.getName()+")");
		lines.add("");
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("");
		for (double duration : obsPercentileDurations)
			table.addColumn(ETAS_AbstractPlot.getTimeShortLabel(duration));
		table.finalizeLine();
		int[][] obsCounts = new int[obsPercentileMags.length][obsPercentileDurations.length];
		double[][] obsPercentiles = new double[obsPercentileMags.length][obsPercentileDurations.length];
		for (int m=0; m<obsPercentileMags.length; m++) {
			float mag = (float)obsPercentileMags[m];
			DiscretizedFunc[] pFuncs = loadPercentilesCSV(percentileCSVs[m]);
			for (int d=0; d<obsPercentileDurations.length; d++) {
				long maxOT = initialConfig.getSimulationStartTimeMillis()
						+(long)(ProbabilityModelsCalc.MILLISEC_PER_YEAR*obsPercentileDurations[d]);
				for (ObsEqkRupture rup : obsPercentileRegionEQs) {
					if (rup.getOriginTime() > maxOT)
						break;
					if ((float)rup.getMag() >= mag)
						obsCounts[m][d]++;
				}
				
				if (obsCounts[m][d] < pFuncs[d].getMinY())
					obsPercentiles[m][d] = 0d;
				else if (obsCounts[m][d] >= pFuncs[d].getMaxY())
					obsPercentiles[m][d] = 100d;
				else
					obsPercentiles[m][d] = pFuncs[d].getFirstInterpolatedX((double)obsCounts[m][d]);
			}
		}
		for (int m=0; m<obsPercentileMags.length; m++) {
			String magStr = "M&ge;"+(float)obsPercentileMags[m];
			table.initNewLine();
			table.addColumn(magStr+" observed count");
			for (int d=0; d<obsPercentileDurations.length; d++)
				table.addColumn(obsCounts[m][d]);
			table.finalizeLine();
			table.initNewLine();
			table.addColumn(magStr+" observed percentile");
			for (int d=0; d<obsPercentileDurations.length; d++)
				table.addColumn((float)obsPercentiles[m][d]);
			table.finalizeLine();
		}
		lines.addAll(table.build());
		
		lines.add(heading+" Geometry Overlay Plots");
		lines.add("");
		
		List<ETAS_Config> geomConfigs = new ArrayList<>();
		List<PlotCurveCharacterstics> geomChars = new ArrayList<>();
		List<String> geomNames = new ArrayList<>();
		
		geomConfigs.add(ETAS_Config.readJSON(new File(gitDir,
				"2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces/config.json")));
		geomChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 5f, Color.BLACK));
		geomNames.add("ShakeMap");
		
		geomConfigs.add(ETAS_Config.readJSON(new File(gitDir,
				"2019_07_06-SearlessValleySequenceFiniteFault-noSpont-full_td-10yr-following-M7.1/config.json")));
		geomChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 5f, Color.DARK_GRAY));
		geomNames.add("First Finite");
		
		geomConfigs.add(ETAS_Config.readJSON(new File(gitDir,
				"2019_07_11-ComCatM7p1_ci38457511_FiniteSurface-noSpont-full_td-scale1.14/config.json")));
		geomChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 5f, Color.GRAY));
		geomNames.add("Second Finite");
		
		inputFuncs = new ArrayList<>();
		inputChars = new ArrayList<>();
		populateMapInputFuncs(sects, inputFuncs, inputChars, Color.BLACK, Color.GRAY, null, true);
		
		ObsEqkRupList eqs = accessor.fetchEventList(null, meta.startTime, System.currentTimeMillis(),
				meta.minDepth, meta.maxDepth, new ComcatRegionAdapter(geomRegion), false, false, 2d);
		
		String geomPrefix = "geom_compare_finite";
		RuptureSurface[] surfs = writeFiniteSurfacesPlot(paperDir, geomPrefix, geomRegion, geomConfigs,
				geomChars, geomNames, inputFuncs, inputChars, eqs);
		
		lines.add("This plot shows the first few finite fault surfaces used");
		lines.add("");
		lines.add("![Map]("+paperDir.getName()+"/"+geomPrefix+".png)");
		lines.add("");
		lines.addAll(buildSurfaceInfoTable(surfs, geomNames).build());
		
		geomConfigs.clear();
		geomChars.clear();
		geomNames.clear();
		
		geomConfigs.add(ETAS_Config.readJSON(new File(gitDir,
				"2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces_CulledSurface/config.json")));
		geomChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 5f, Color.BLACK));
		geomNames.add("Primary");
		
		geomConfigs.add(ETAS_Config.readJSON(new File(gitDir,
				"2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces/config.json")));
		geomChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 5f, Color.GRAY));
		geomNames.add("Parallel Strands");
		
		geomConfigs.add(ETAS_Config.readJSON(new File(gitDir,
				"2019_09_03-ComCatM7p1_ci38457511_ShakeMapSurfaces_PlanarExtents/config.json")));
		geomChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 5f, Color.DARK_GRAY));
		geomNames.add("Extents");
		
		geomPrefix = "geom_compare_shakemap";
		surfs = writeFiniteSurfacesPlot(paperDir, geomPrefix, geomRegion, geomConfigs,
				geomChars, geomNames, inputFuncs, inputChars, eqs);

		lines.add("");
		lines.add("This plot shows different versions of the ShakeMap surface");
		lines.add("");
		lines.add("![Map]("+paperDir.getName()+"/"+geomPrefix+".png)");
		lines.add("");
		lines.addAll(buildSurfaceInfoTable(surfs, geomNames).build());
		
		return lines;
	}
	
	private static TableBuilder buildSurfaceInfoTable(RuptureSurface[] surfs, List<String> geomNames) {
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("Name", "Total Length (km)", "Total Area (km^2)", "# Points");
		for (int i=0; i<surfs.length; i++) {
			table.initNewLine();
			table.addColumn(geomNames.get(i));
			table.addColumn((float)surfs[i].getAveLength());
			table.addColumn((float)surfs[i].getArea());
			table.addColumn(surfs[i].getEvenlyDiscritizedListOfLocsOnSurface().size());
			table.finalizeLine();
		}
		return table;
	}
	
	private static void processPlots(ETAS_Config config, File inputFile, File outputDir,
			FaultSystemSolution fss, ExecutorService exec, ETAS_AbstractPlot... plots) throws IOException {
		int numProcessed = ETAS_CatalogIteration.processCatalogs(inputFile, new ETAS_CatalogIteration.Callback() {
			
			@Override
			public void processCatalog(ETAS_Catalog catalog, int index) {
				ETAS_Catalog triggeredOnlyCatalog = ETAS_Launcher.getFilteredNoSpontaneous(config, catalog);
				for (ETAS_AbstractPlot plot : plots)
					plot.processCatalog(catalog, triggeredOnlyCatalog, fss);
			}
		}, -1, 0d);
		
		System.out.println("Processed "+numProcessed+" catalogs");
		List<Future<?>> futures = new ArrayList<>();
		for (ETAS_AbstractPlot plot : plots) {
			List<? extends Runnable> runs = plot.finalize(outputDir, fss, exec);
			if (runs != null)
				for (Runnable run : runs)
					futures.add(exec.submit(run));
		}
		
		for (Future<?> f : futures) {
			try {
				f.get();
			} catch (InterruptedException | ExecutionException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
	}
	
	private static void writePercentilesCSV(ETAS_Config config, File outputDir, String prefix, File inputFile,
			double[] mags, double[] durations)
			throws IOException {
		
		long[] maxOTs = new long[durations.length];
		
		for (int i=0; i<durations.length; i++)
			maxOTs[i] = config.getSimulationStartTimeMillis() + (long)(durations[i]*ProbabilityModelsCalc.MILLISEC_PER_YEAR);
		
		List<double[][]> durMagCounts = new ArrayList<>();
		
		ETAS_CatalogIteration.processCatalogs(inputFile, new Callback() {
			
			@Override
			public void processCatalog(ETAS_Catalog catalog, int index) {
				double[][] counts = new double[durations.length][mags.length];
				for (ETAS_EqkRupture rup : catalog) {
					long ot = rup.getOriginTime();
					float mag = (float)rup.getMag();
					for (int d=0; d<maxOTs.length; d++) {
						if (ot > maxOTs[d])
							continue;
						for (int m=0; m<mags.length; m++)
							if (mag >= (float)mags[m])
								counts[d][m]++;
					}
				}
				durMagCounts.add(counts);
			}
		});
		
		for (int m=0; m<mags.length; m++) {
			CSVFile<String> csv = new CSVFile<>(true);
			
			List<String> header = new ArrayList<>();
			header.add("Percentile");
			for (double duration : durations)
				header.add(ETAS_AbstractPlot.getTimeShortLabel(duration));
			csv.addLine(header);
			
			List<double[]> arrays = new ArrayList<>();
			for (int d=0; d<durations.length; d++) {
				double[] array = new double[durMagCounts.size()];
				for (int i=0; i<durMagCounts.size(); i++) {
					double[][] counts = durMagCounts.get(i);
					array[i] = counts[d][m];
				}
				arrays.add(array);
			}
			
			for (double p=0d; p<=100d; p++) {
				List<String> line = new ArrayList<>();
				line.add("p"+(float)p);
				for (double[] array : arrays) {
					double value = p == 0d ? StatUtils.min(array) : StatUtils.percentile(array, p);
					line.add((float)value+"");
				}
				csv.addLine(line);
			}
			
			File outputFile = new File(outputDir, prefix+"_m"+(float)mags[m]+".csv");
			csv.writeToFile(outputFile);
		}
	}
	
	private static DiscretizedFunc[] loadPercentilesCSV(File csvFile) throws IOException {
		CSVFile<String> csv = CSVFile.readFile(csvFile, true);
		DiscretizedFunc[] ret = new DiscretizedFunc[csv.getNumCols()-1];
		for (int i=0; i<ret.length; i++)
			ret[i] = new ArbitrarilyDiscretizedFunc();
		for (int row=1; row<csv.getNumRows(); row++) {
			double x = Double.parseDouble(csv.get(row, 0).replaceAll("p", ""));
			for (int i=0; i<ret.length; i++) {
				double y = csv.getDouble(row, i+1);
				ret[i].set(x, y);
			}
		}
		
		return ret;
	}
	
	private static RuptureSurface[] writeFiniteSurfacesPlot(File outputDir, String prefix, Region mapRegion,
			List<ETAS_Config> geomConfigs, List<PlotCurveCharacterstics> geomChars, List<String> geomNames,
			List<XY_DataSet> inputFuncs, List<PlotCurveCharacterstics> inputChars, List<? extends ObsEqkRupture> eqs)
					throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		RuptureSurface[] surfs = new RuptureSurface[geomConfigs.size()];
		
		for (int i=0; i<geomConfigs.size(); i++) {
			ETAS_Config config = geomConfigs.get(i);
			RuptureSurface surf = null;
			double maxMag = 0d;
			for (TriggerRupture trigger : config.getTriggerRuptures()) {
				ETAS_EqkRupture rup = trigger.buildRupture(
						null, config.getSimulationStartTimeMillis(), null);
				if (rup.getMag() > maxMag) {
					maxMag = rup.getMag();
					surf = rup.getRuptureSurface();
				}
			}
			
			surfs[i] = surf;
			
			PlotCurveCharacterstics geomChar = geomChars.get(i);
			PlotCurveCharacterstics outlineChar = new PlotCurveCharacterstics(
					PlotLineType.DOTTED, 0.5f*geomChar.getLineWidth(), geomChar.getColor());
			if (surf.getAveDip() < 89) {
				List<XY_DataSet> outlines = ETAS_EventMapPlotUtils.getSurfOutlines(surf);
				funcs.addAll(outlines);
				for (int j=0; j<outlines.size(); j++)
					chars.add(outlineChar);
			}
			List<XY_DataSet> traces = ETAS_EventMapPlotUtils.getSurfTraces(surf);
			traces.get(0).setName(geomNames.get(i));
			funcs.addAll(traces);
			for (int j=0; j<traces.size(); j++)
				chars.add(geomChar);
		}
		
		funcs.addAll(inputFuncs);
		chars.addAll(inputChars);
		
		if (eqs != null && eqs.size() > 0) {
			XY_DataSet eqXY = new DefaultXY_DataSet();
			double minMag = Double.POSITIVE_INFINITY;
			for (ObsEqkRupture rup : eqs) {
				Location loc = rup.getHypocenterLocation();
				eqXY.set(loc.getLongitude(), loc.getLatitude());
				minMag = Math.min(minMag, rup.getMag());
			}
//			eqXY.setName("M≥"+(float)(Math.floor(minMag))+" Seismicity");
			eqXY.setName("Seismicity");
			funcs.add(eqXY);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 0.3f, Color.LIGHT_GRAY));
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, " ", "Longitude", "Latitude");
		spec.setLegendVisible(true);
		
		spec.setPlotAnnotations(buildFaultAnns());
		
		HeadlessGraphPanel gp = ETAS_AbstractPlot.buildGraphPanel();
		double latSpan = mapRegion.getMaxLat() - mapRegion.getMinLat();
		double lonSpan = mapRegion.getMaxLon() - mapRegion.getMinLon();
		gp.setUserBounds(new Range(mapRegion.getMinLon(), mapRegion.getMaxLon()),
				new Range(mapRegion.getMinLat(), mapRegion.getMaxLat()));

		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
		gp.drawGraphPanel(spec, false, false);
		
		TickUnits tus = new TickUnits();
		TickUnit tu;
		if (lonSpan > 5)
			tu = new NumberTickUnit(1d);
		else if (lonSpan > 2)
			tu = new NumberTickUnit(0.5);
		else if (lonSpan > 1)
			tu = new NumberTickUnit(0.25);
		else
			tu = new NumberTickUnit(0.1);
		tus.add(tu);
		
		XYPlot plot = gp.getPlot();
		plot.getRangeAxis().setStandardTickUnits(tus);
		plot.getDomainAxis().setStandardTickUnits(tus);
		int width = 800;
		gp.getChartPanel().setSize(width, (int)((double)(width)*latSpan/lonSpan));
		
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
		
		return surfs;
	}
	
	private static void writeInputsPlot(File outputDir, String prefix, Region mapRegion,
			ETAS_Config initialConfig, ETAS_Config sevenDayConfig,
			List<XY_DataSet> inputFuncs, List<PlotCurveCharacterstics> inputChars)
					throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		List<ETAS_EqkRupture> sevenDayEvents = new ArrayList<>();
		for (TriggerRupture rup : sevenDayConfig.getTriggerRuptures())
			sevenDayEvents.add(rup.buildRupture(null, sevenDayConfig.getSimulationStartTimeMillis(), null));
		for (ETAS_EqkRupture rup : sevenDayEvents)
			rup.setRuptureSurface(null);
		ETAS_EventMapPlotUtils.buildEventPlot(sevenDayEvents, funcs, chars, 7.1);
		for (PlotCurveCharacterstics pChar : chars)
			pChar.setColor(Color.LIGHT_GRAY);
		for (XY_DataSet func : funcs)
			func.setName(null);
		
		List<ETAS_EqkRupture> initialDayEvents = new ArrayList<>();
		int initialStartIndex = funcs.size();
		for (TriggerRupture rup : initialConfig.getTriggerRuptures())
			initialDayEvents.add(rup.buildRupture(null, initialConfig.getSimulationStartTimeMillis(), null));
		for (ETAS_EqkRupture rup : initialDayEvents)
			rup.setRuptureSurface(null);
		ETAS_EventMapPlotUtils.buildEventPlot(initialDayEvents, funcs, chars, 7.1);
		for (int i=initialStartIndex; i<chars.size(); i++)
			chars.get(i).setColor(Color.DARK_GRAY);
		
		funcs.addAll(0, inputFuncs);
		chars.addAll(0, inputChars);
		
		PlotSpec spec = new PlotSpec(funcs, chars, " ", "Longitude", "Latitude");
		spec.setLegendVisible(true);
		
		spec.setPlotAnnotations(buildFaultAnns());
		
		HeadlessGraphPanel gp = ETAS_AbstractPlot.buildGraphPanel();
		double latSpan = mapRegion.getMaxLat() - mapRegion.getMinLat();
		double lonSpan = mapRegion.getMaxLon() - mapRegion.getMinLon();
		gp.setUserBounds(new Range(mapRegion.getMinLon(), mapRegion.getMaxLon()),
				new Range(mapRegion.getMinLat(), mapRegion.getMaxLat()));

		gp.drawGraphPanel(spec, false, false);
		
		TickUnits tus = new TickUnits();
		TickUnit tu;
		if (lonSpan > 5)
			tu = new NumberTickUnit(1d);
		else if (lonSpan > 2)
			tu = new NumberTickUnit(0.5);
		else if (lonSpan > 1)
			tu = new NumberTickUnit(0.25);
		else
			tu = new NumberTickUnit(0.1);
		tus.add(tu);
		
		XYPlot plot = gp.getPlot();
		plot.getRangeAxis().setStandardTickUnits(tus);
		plot.getDomainAxis().setStandardTickUnits(tus);
		int width = 800;
		gp.getChartPanel().setSize(width, (int)((double)(width)*latSpan/lonSpan));
		
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
	}
	
	private static List<XYTextAnnotation> buildFaultAnns() {
		List<XYTextAnnotation> anns = new ArrayList<>();
		
		XYTextAnnotation gAnn = new XYTextAnnotation("Garlock Fault", -117.55, 35.4875);
		gAnn.setRotationAngle(-Math.toRadians(16));
		gAnn.setRotationAnchor(TextAnchor.BASELINE_CENTER);
		gAnn.setTextAnchor(TextAnchor.BASELINE_CENTER);
		gAnn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
		anns.add(gAnn);
		
		XYTextAnnotation aAnn = new XYTextAnnotation("Airport Lake Fault", -117.76, 35.828);
		aAnn.setRotationAngle(Math.toRadians(79.5));
		aAnn.setRotationAnchor(TextAnchor.BASELINE_CENTER);
		aAnn.setTextAnchor(TextAnchor.BASELINE_CENTER);
		aAnn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
		anns.add(aAnn);
		
		XYTextAnnotation lAnn = new XYTextAnnotation("Little Lake Fault", -117.703, 35.65);
		lAnn.setRotationAngle(Math.toRadians(51.9));
		lAnn.setRotationAnchor(TextAnchor.BASELINE_CENTER);
		lAnn.setTextAnchor(TextAnchor.BASELINE_CENTER);
		lAnn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
		anns.add(lAnn);
		
		return anns;
	}
	
}
