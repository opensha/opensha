package scratch.kevin.ucerf3.etas;

import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.math3.stat.StatUtils;
import org.dom4j.DocumentException;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYBoxAnnotation;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.data.Range;
import org.jfree.ui.TextAnchor;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;

import oracle.net.aso.r;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_EventMapPlotUtils;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_MFD_Plot;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_MFD_Plot.MFD_Stats;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;
import scratch.UCERF3.utils.FaultSystemIO;

public class RidgecrestStatsCalc {

	public static void main(String[] args) throws IOException, DocumentException {
		File gitDir = new File("/home/kevin/git/ucerf3-etas-results");
//		updateMagComplete(mainDir, "ci38457511", 3.5);
//		System.exit(0);
		
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
				+ " in the tables below to see the details of each simulation, along with many plots.");
		lines.add("");
		lines.add("[Download my 2019 SCEC Annual Meeting poster here.](2019_SCEC_Poster.pdf)");
		lines.add("");
		lines.add("You can also view the complete list of UCERF3-ETAS simulations [here](../README.md), though the "
				+ "list is quite long and not all are for Ridgecrest.");
		lines.add("");
		
		int tocIndex = lines.size();
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
		
		set.add(new Results(new File(gitDir, "2019_07_11-ComCatM7p1_ci38457511_FiniteSurface-noSpont-full_td-scale1.14"),
				"M7.1, Quad Source", false));
		
		set.add(new Results(new File(gitDir, "2019_07_18-ComCatM7p1_ci38457511_InvertedSurface_ShakeMapSurface-noSpont-full_td-scale1.14"),
				"M7.1, Inverted Source", false));
		
		set.add(new Results(new File(gitDir, "2019_08_20-ComCatM7p1_ci38457511_InvertedSurface_minSlip0p5_ShakeMapSurface-noSpont-full_td-scale1.14"),
				"M7.1, Inverted Source (minSlip=0.5)", false));
		
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
		
		List<String> header = new ArrayList<>();
		header.add("Name");
		header.add("1 Week Prob M≥7.1");
		header.add("1 Month Prob M≥7.1");
		header.add("1 Month Mean Num M≥3.5");
		header.add("1 Month Median Num M≥3.5");
		header.add("1 Month Garlock Prob M≥7");
		header.add("1 Month SAF Mojave Prob M≥7");
		
		List<String> lines = new ArrayList<>();
		
		// add faults to bottom of plot
		List<XY_DataSet> inputFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> inputChars = new ArrayList<>();
		PlotCurveCharacterstics faultTraceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.LIGHT_GRAY);
		PlotCurveCharacterstics faultOutlineChar = new PlotCurveCharacterstics(PlotLineType.DOTTED, 1.5f, Color.LIGHT_GRAY);

		boolean firstTrace = true;
		for (FaultSectionPrefData sect : fss.getRupSet().getFaultSectionDataList()) {
			RuptureSurface surf = sect.getStirlingGriddedSurface(1d, false, false);
			List<XY_DataSet> outlines = ETAS_EventMapPlotUtils.getSurfOutlines(surf);
			for (XY_DataSet outline : outlines) {
				inputFuncs.add(outline);
				inputChars.add(faultOutlineChar);
			}
			List<XY_DataSet> traces = ETAS_EventMapPlotUtils.getSurfTraces(surf);
			for (int i=0; i<traces.size(); i++) {
				XY_DataSet trace = traces.get(i);
				if (firstTrace) {
					trace.setName("Fault Traces");
					firstTrace = false;
				}
				inputFuncs.add(trace);
				inputChars.add(faultTraceChar);
			}
		}
		
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
				line.add(r.name);
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
				table.addColumn(r.name);
				names.add(r.name);
				configs.add(ETAS_Config.readJSON(r.configFile));
			}
			table.finalizeLine();
			table.initNewLine();
			
			for (int j=0; j<names.size(); j++) {
				Results r = resultSets.get(i).get(j);
				String mapPrefix = "trigger_map_"+r.dir.getName();
				HeadlessGraphPanel mapGP = writeMapPlot(configs.get(j), names.get(j),
						resourcesDir, mapPrefix, inputFuncs, inputChars);
				String chartPrefix = "prob_chart_"+r.dir.getName();
				HeadlessGraphPanel chartGP = writeProbChartPlot(resourcesDir, r, baseline, chartPrefix);
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
		double maxMag = 8d;
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
			String prefix) throws IOException {
		List<XYAnnotation> anns = new ArrayList<>();
		
		String[] labels = {
				"1 Week\nAny M≥7.1",
				"1 Month\nAny M≥7.1",
				"1 Month\nGarlock M≥7",
				"1 Month\nMojave M≥7"
		};
		double[] vals = buildPlotVals(result);
		double[] baseVals = baseline == null ? null : buildPlotVals(baseline);
		
		double maxY = StatUtils.max(vals);
		if (baseVals != null)
			maxY = Math.max(maxY, StatUtils.max(baseVals));
		if (maxY < 1d)
			maxY = 1.5;
		else
			maxY = 7;
		
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

}
