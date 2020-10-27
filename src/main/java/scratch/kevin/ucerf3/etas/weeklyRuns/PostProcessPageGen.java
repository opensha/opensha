package scratch.kevin.ucerf3.etas.weeklyRuns;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.math3.stat.StatUtils;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.data.Range;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.comcat.ComcatAccessor;
import org.opensha.commons.data.comcat.ComcatRegion;
import org.opensha.commons.data.comcat.ComcatRegionAdapter;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.UncertainArbDiscDataset;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotPreferences;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZGraphPanel;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileNameComparator;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.parsers.UCERF3_CatalogParser;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.erf.ETAS.ETAS_CubeDiscretizationParams;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_ComcatComparePlot;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class PostProcessPageGen {

	public static void main(String[] args) throws IOException {
//		int maxNum = 100;
		int maxNum = Integer.MAX_VALUE;
		File outputParentDir = new File("/home/kevin/git/ucerf3-etas-results/2020-weekly-runs");
		
		int[] highlightYears = { 1992, 1999, 2010, 2019 };

		File dataDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
				+ "2020_05_14-weekly-1986-present-full_td-kCOV1.5");
//				+ "2020_05_25-weekly-1986-present-no_ert-kCOV1.5");
		File outputDir = new File(outputParentDir, dataDir.getName());
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());

		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());

		File u3CatalogFile = ETAS_Config.resolvePath("${ETAS_LAUNCHER}/inputs/u3_historical_catalog.txt");

		ObsEqkRupList u3Catalog = UCERF3_CatalogParser.loadCatalog(u3CatalogFile);
		long comcatStartMillis = u3Catalog.get(u3Catalog.size()-1).getOriginTime();

		System.out.println("Fetching ComCat events");
		ComcatAccessor accessor = new ComcatAccessor();
		ComcatRegion cReg = new ComcatRegionAdapter(new CaliforniaRegions.RELM_TESTING());
		long curTime = System.currentTimeMillis();
		ObsEqkRupList comcatEvents = accessor.fetchEventList(null, comcatStartMillis,
				curTime, -10d, ETAS_CubeDiscretizationParams.DEFAULT_MAX_DEPTH, cReg,
				false, false, 2.5d);

		ObsEqkRupList combEvents = new ObsEqkRupList();
		combEvents.addAll(u3Catalog);
		combEvents.addAll(comcatEvents);
		combEvents.sortByOriginTime();

		double[] minMags = { 2.5, 3d, 4d, 5d, 6d, 7d };
		double[] scatterDurations = { 1d, 7d, 30d, 365d };
		String[] scatterDurationLabels = { "1 Day", "1 Week", "1 Month", "1 Year" };
		double[] dailyMags = { 2.5, 5d, 7d };
		PlotCurveCharacterstics[] dailyDataChars = new PlotCurveCharacterstics[] {
			new PlotCurveCharacterstics(PlotSymbol.CIRCLE, 2f, new Color(0, 0, 0, 180)),
			new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 4f, Color.BLUE),
			new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 6f, Color.RED),
		};
		PlotCurveCharacterstics[] dailySimChars = new PlotCurveCharacterstics[] {
				new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK),
				new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE),
				new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED),
		};
		
		HistogramFunction histAxis = HistogramFunction.getEncompassingHistogram(-1d, 4, 0.05);
		EvenlyDiscrXYZ_DataSet globalXYZ = new EvenlyDiscrXYZ_DataSet(histAxis.size(), histAxis.size(),
				histAxis.getMinX(), histAxis.getMinX(), histAxis.getDelta());
		
		EvenlyDiscrXYZ_DataSet[][] aggregatedXYZs = new EvenlyDiscrXYZ_DataSet[minMags.length][scatterDurations.length];
		for (int m=0; m<minMags.length; m++)
			for (int d=0; d<scatterDurations.length; d++)
				aggregatedXYZs[m][d] = globalXYZ.copy();
		
		List<DiscretizedFunc[]> dailyIncrMedianDiffFuncs = new ArrayList<>();
		List<DiscretizedFunc[]> dailyIncrMeanDiffFuncs = new ArrayList<>();

		List<DiscretizedFunc[]> cumulativeMedianDiffFuncs = new ArrayList<>();
		List<DiscretizedFunc[]> cumulativeMeanDiffFuncs = new ArrayList<>();

		List<Double> years = new ArrayList<>();

		long earliestStart = Long.MAX_VALUE;
		long lastStart = Long.MIN_VALUE;

		File[] batchDirs = dataDir.listFiles();
		Arrays.sort(batchDirs, new FileNameComparator());
		
		ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		List<Future<CalcCallable>> futures = new ArrayList<>();
		
		for (File batchDir : batchDirs) {
			if (!batchDir.isDirectory() || !batchDir.getName().startsWith("batch_"))
				continue;
			System.out.println("processing "+batchDir.getName());
			for (File weekDir : batchDir.listFiles()) {
				if (futures.size() == maxNum)
					break;
				File resultsDir = new File(weekDir, "aggregated_results");
				if (!resultsDir.exists())
					continue;
				System.out.println("\t"+weekDir.getName());

				File configFile = new File(weekDir, "config.json");
				Preconditions.checkState(configFile.exists());
				ETAS_Config config = ETAS_Config.readJSON(configFile);

				earliestStart = Long.min(earliestStart, config.getSimulationStartTimeMillis());
				lastStart = Long.max(lastStart, config.getSimulationStartTimeMillis());
				
				futures.add(exec.submit(new CalcCallable(combEvents, curTime, weekDir, config,
						minMags, scatterDurations, dailyMags, globalXYZ)));
			}
		}
		
		System.out.println("Waiting on "+futures.size()+" futures...");
		
		double minYear = getYear(earliestStart);
		double maxYear = getYear(lastStart);
		System.out.println("Year range: "+(float)minYear +" => "+(float)maxYear);
		CPT scatterCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(minYear, maxYear);
		scatterCPT = scatterCPT.asDiscrete(10, true);
		double deltaYears = maxYear - minYear;
		double yearInc;
		if (deltaYears > 100)
			yearInc = 25;
		else if (deltaYears > 50)
			yearInc = 10;
		else if (deltaYears > 30)
			yearInc = 5;
		else if (deltaYears > 10)
			yearInc = 2;
		else
			yearInc = 1;
		PaintScaleLegend scatterBar = XYZGraphPanel.getLegendForCPT(scatterCPT, "Year",
				22, 18, yearInc, RectangleEdge.BOTTOM);

		DefaultXY_DataSet[][][] medianMagDurScatters = new DefaultXY_DataSet[scatterCPT.size()][minMags.length][scatterDurations.length];
		DefaultXY_DataSet[][][] meanMagDurScatters = new DefaultXY_DataSet[scatterCPT.size()][minMags.length][scatterDurations.length];
		for (int i=0; i<scatterCPT.size(); i++) {
			for (int m=0; m<minMags.length; m++) {
				for (int d=0; d<scatterDurations.length; d++) {
					medianMagDurScatters[i][m][d] = new DefaultXY_DataSet();
					meanMagDurScatters[i][m][d] = new DefaultXY_DataSet();
				}
			}
		}
		
		double[][] percentileMeans = new double[minMags.length][scatterDurations.length];
		HistogramFunction[][] percentileFuncs = new HistogramFunction[minMags.length][scatterDurations.length];
		for (int m=0; m<minMags.length; m++)
			for (int d=0; d<scatterDurations.length; d++)
				percentileFuncs[m][d] = new HistogramFunction(2d, 25, 4d);

		int[][] dataBelow95ConfCounts = new int[minMags.length][scatterDurations.length];
		int[][] dataAbove95ConfCounts = new int[minMags.length][scatterDurations.length];
		
		double[][] dataFractZeros = new double[minMags.length][scatterDurations.length];
		double[][] simFractZeros = new double[minMags.length][scatterDurations.length];
		
		List<CalcCallable> calls = new ArrayList<>();
		
		for (Future<CalcCallable> future : futures) {
			CalcCallable call;
			try {
				call = future.get();
			} catch (InterruptedException | ExecutionException e) {
				exec.shutdownNow();
				throw ExceptionUtils.asRuntimeException(e);
			}
			calls.add(call);
			
			dailyIncrMeanDiffFuncs.add(call.meanIncrFuncs);
			dailyIncrMedianDiffFuncs.add(call.medianIncrFuncs);

			cumulativeMeanDiffFuncs.add(call.meanCumulativeFuncs);
			cumulativeMedianDiffFuncs.add(call.medianCumulativeFuncs);

			double year = getYear(call.startTimeMillis);
			years.add(year);
			
			int scatterI = 0;
			for (int j=0; j<scatterCPT.size(); j++) {
				CPTVal val = scatterCPT.get(j);
				if ((float)year >= val.start && (float)year <= val.end)
					scatterI = j;
			}
			
			for (int m=0; m<minMags.length; m++) {
				for (int d=0; d<scatterDurations.length; d++) {
					meanMagDurScatters[scatterI][m][d].set(call.dataCumCounts[m][d], call.meanCumCounts[m][d]);
					medianMagDurScatters[scatterI][m][d].set(call.dataCumCounts[m][d], call.medianCumCounts[m][d]);
					
					for (int i=0; i<globalXYZ.size(); i++)
						aggregatedXYZs[m][d].set(i, aggregatedXYZs[m][d].get(i) + call.indvXYZs[m][d].get(i));
					double dataPercentile = call.dataPercentiles[m][d];
					percentileFuncs[m][d].add(percentileFuncs[m][d].getClosestXIndex(dataPercentile), 1d);
					simFractZeros[m][d] += call.fractZeros[m][d]/(double)futures.size();
					percentileMeans[m][d] += dataPercentile/(double)futures.size();
					
					if (call.below2p5s[m][d])
						dataBelow95ConfCounts[m][d]++;
					if (call.above97p5s[m][d])
						dataAbove95ConfCounts[m][d]++;
					
					if (Math.floor(call.dataCumCounts[m][d]) == 0d)
						dataFractZeros[m][d] += 1d/(double)futures.size();
				}
			}
			
			if (years.size() % 50 == 0)
				System.out.println("Done with "+years.size()+"/"+futures.size());
		}
		Collections.sort(calls);
		
		// now fill in holes scatters for small numbers where it's impossible to hit some cells
//		for (int n=1; n<100; n++) {
//			double logN = Math.log10(n);
//			double logNp1 = Math.log10(n+1);
//			int myIndex = histAxis.getClosestXIndex(logN);
//			int nextIndex = histAxis.getClosestXIndex(logNp1);
//			if (nextIndex - myIndex > 1) {
//				// we have a hole to fill in
//				for (int m=0; m<minMags.length; m++) {
//					for (int d=0; d<scatterDurations.length; d++) {
//						EvenlyDiscrXYZ_DataSet xyz = aggregatedXYZs[m][d];
//						double val = xyz.get(myIndex, myIndex);
//						for (int x=myIndex; x<nextIndex; x++)
//							for (int y=myIndex; y<nextIndex; y++)
//								xyz.set(x, y, val);
//					}
//				}
//			}
//		}
		for (int nx=1; nx<globalXYZ.getNumX(); nx++) {
			for (int ny=1; ny<globalXYZ.getNumY(); ny++) {
				double logNX = Math.log10(nx);
				double logNXp1 = Math.log10(nx+1);
				double logNY = Math.log10(ny);
				double logNYp1 = Math.log10(ny+1);

				int xIndex = histAxis.getClosestXIndex(logNX);
				int yIndex = histAxis.getClosestXIndex(logNY);
				int nextXIndex = histAxis.getClosestXIndex(logNXp1);
				int nextYIndex = histAxis.getClosestXIndex(logNYp1);
				
				if (nextXIndex - xIndex > 1 || nextYIndex - yIndex > 1) {
					// we have a hole to fill in
					for (int m=0; m<minMags.length; m++) {
						for (int d=0; d<scatterDurations.length; d++) {
							EvenlyDiscrXYZ_DataSet xyz = aggregatedXYZs[m][d];
							double val = xyz.get(xIndex, yIndex);
							for (int x=xIndex; x==xIndex || x<nextXIndex; x++)
								for (int y=yIndex; y==yIndex || y<nextYIndex; y++)
									xyz.set(x, y, val);
						}
					}
				}
			}
		}
		
		exec.shutdown();
		System.out.println("Plotting");

		List<String> lines = new ArrayList<>();
		lines.add("# Aggregated ETAS Weekly Run Data Comparisons");
		lines.add("");

		int tocIndex = lines.size();
		String topLink = "*[(top)](#table-of-contents)*";
		lines.add("");
		
		// write out percentile histograms
		lines.add("## Data Percentile Histograms");
		lines.add(topLink); lines.add("");
		lines.add("Histogram of the percentile of the actual event count within the simulation distribution, "
				+ "for various magnitudes and durations.");
		lines.add("");
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("Min Mag");
		for (String label : scatterDurationLabels)
			table.addColumn(label);
		table.finalizeLine();
		for (int m=0; m<minMags.length; m++) {
			table.initNewLine();
			table.addColumn("**M&ge;"+(float)minMags[m]+"**");
			
			for (int d=0; d<scatterDurations.length; d++) {
				List<DiscretizedFunc> funcs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				
				funcs.add(percentileFuncs[m][d]);
				chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, Color.GRAY));
				
				double y = percentileFuncs[m][d].calcSumOfY_Vals() / (double)percentileFuncs[m][d].size();
				DiscretizedFunc line = new ArbitrarilyDiscretizedFunc();
				line.set(0d, y);
				line.set(100d, y);
				
				funcs.add(line);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 4f, Color.BLACK));
				
				Range xRange = new Range(0d, 100d);
				Range yRange = new Range(0d, Math.min(10d*y, 1.1*percentileFuncs[m][d].getMaxY()));
				
				PlotSpec spec = new PlotSpec(funcs, chars, " ", "Percentile", "Count");

				DecimalFormat meanDF = new DecimalFormat("0.00");
				DecimalFormat percentDF = new DecimalFormat("0.00%");
				
				double annY = yRange.getUpperBound()*0.975;
				XYTextAnnotation meanAnn = new XYTextAnnotation(
						"Mean %-ile: "+meanDF.format(percentileMeans[m][d]), 5, annY);
				Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, 20);
				meanAnn.setFont(annFont);
				meanAnn.setTextAnchor(TextAnchor.TOP_LEFT);
				spec.addPlotAnnotation(meanAnn);
				
				annY = yRange.getUpperBound()*0.925d;
				XYTextAnnotation simZeroAnn = new XYTextAnnotation(
						"Simulation % == 0: "+percentDF.format(simFractZeros[m][d]), 5, annY);
				simZeroAnn.setFont(annFont);
				simZeroAnn.setTextAnchor(TextAnchor.TOP_LEFT);
				spec.addPlotAnnotation(simZeroAnn);
				
				annY = yRange.getUpperBound()*0.875d;
				XYTextAnnotation dataZeroAnn = new XYTextAnnotation(
						"Data % == 0: "+percentDF.format(dataFractZeros[m][d]), 5, annY);
				dataZeroAnn.setFont(annFont);
				dataZeroAnn.setTextAnchor(TextAnchor.TOP_LEFT);
				spec.addPlotAnnotation(dataZeroAnn);
				
				spec.setLegendVisible(true);

				HeadlessGraphPanel gp = new HeadlessGraphPanel();
				gp.setTickLabelFontSize(18);
				gp.setAxisLabelFontSize(24);
				gp.setPlotLabelFontSize(24);
				gp.setLegendFontSize(18);
				gp.setBackgroundColor(Color.WHITE);

				gp.drawGraphPanel(spec, false, false, xRange, yRange);
				
				String prefix = "percentile_"+scatterDurationLabels[d].replaceAll(" ", "").toLowerCase();
				prefix += "_m"+(float)minMags[m];

				File file = new File(resourcesDir, prefix);
				gp.getChartPanel().setSize(800, 800);
				gp.saveAsPNG(file.getAbsolutePath()+".png");
				//							gp.saveAsPDF(file.getAbsolutePath()+".pdf");

				table.addColumn("![chart](resources/"+prefix+".png)");
			}
			table.finalizeLine();
		}
		lines.addAll(table.build());
		lines.add("");
		
		// write out percentile histograms
		lines.add("## Data 95% Confidence Comparisons");
		lines.add(topLink); lines.add("");
		lines.add("This plots the percentage of observations which are outside (black lines), "
				+ "below (blue lines), or above (red lines) the simulations 95% confidence interval.");
		lines.add("");
		
		table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("Duration");
		table.addColumn("Plot");
		table.finalizeLine();
		
		for (int d=0; d<scatterDurations.length; d++) {
			List<DiscretizedFunc> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			ArbitrarilyDiscretizedFunc aboveFunc = new ArbitrarilyDiscretizedFunc();
			ArbitrarilyDiscretizedFunc belowFunc = new ArbitrarilyDiscretizedFunc();
			ArbitrarilyDiscretizedFunc outsideFunc = new ArbitrarilyDiscretizedFunc();
			
			for (int m=0; m<minMags.length; m++) {
				double x = minMags[m];
				int numAbove = dataAbove95ConfCounts[m][d];
				int numBelow = dataBelow95ConfCounts[m][d];
				int numOutside = numAbove + numBelow;
				
				aboveFunc.set(x, 100d*(double)numAbove/(double)calls.size());
				belowFunc.set(x, 100d*(double)numBelow/(double)calls.size());
				outsideFunc.set(x, 100d*(double)numOutside/(double)calls.size());
			}
			
			outsideFunc.setName("Outside 95 % Bounds");
			funcs.add(outsideFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
			
			belowFunc.setName("Below 2.5 %-ile");
			funcs.add(belowFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLUE));
			
			aboveFunc.setName("Above 97.5 %-ile");
			funcs.add(aboveFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.RED));
			
			Range xRange = new Range(minMags[0], minMags[minMags.length-1]);
			Range yRange = new Range(0d, 20d);
			
			PlotSpec spec = new PlotSpec(funcs, chars, " ", "Minimum Magnitude", "Data Percentage");
			spec.setLegendVisible(true);

			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setTickLabelFontSize(18);
			gp.setAxisLabelFontSize(24);
			gp.setPlotLabelFontSize(24);
			gp.setLegendFontSize(18);
			gp.setBackgroundColor(Color.WHITE);
			gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);

			gp.drawGraphPanel(spec, false, false, xRange, yRange);
			
			String prefix = "conf_bounds_"+scatterDurationLabels[d].replaceAll(" ", "").toLowerCase();

			File file = new File(resourcesDir, prefix);
			gp.getChartPanel().setSize(1000, 800);
			gp.saveAsPNG(file.getAbsolutePath()+".png");
			//							gp.saveAsPDF(file.getAbsolutePath()+".pdf");

			table.initNewLine();
			table.addColumn("**"+scatterDurationLabels[d]+"**");
			table.addColumn("![chart](resources/"+prefix+".png)");
			table.finalizeLine();
		}
		lines.addAll(table.build());
		lines.add("");
		
		// write out scatter plots
		lines.add("## Data vs Model Count Scatters");
		lines.add(topLink); lines.add("");
		lines.add("Scatter plots of the actual event count (x-axis) vs the simulation mean or median value, "
				+ "for different durations and magnitude thresholds.");
		lines.add("");
		
		for (int d=0; d<scatterDurations.length; d++) {
			lines.add("### "+scatterDurationLabels[d]+" Scatters");
			lines.add(topLink); lines.add("");
			
			table = MarkdownUtils.tableBuilder();
			table.addLine("Min Mag", "Mean Simulation Values", "Median Simulation Values", "Individual Catalogs");

			for (int m=0; m<minMags.length; m++) {
				table.initNewLine();
				table.addColumn("**M&ge;"+(float)minMags[m]+"**");
				
				Range xRange = null, yRange = null;
				PlotPreferences plotPrefs = null;
				for (boolean isMedian : new boolean[] {false, true}) {
					List<XY_DataSet> funcs = new ArrayList<>();
					List<PlotCurveCharacterstics> chars = new ArrayList<>();
					
					DefaultXY_DataSet[][][] myXYs = isMedian ? medianMagDurScatters : meanMagDurScatters;
					
					double maxVal = 0d;
					double minVal = Double.POSITIVE_INFINITY;
					
					int totalNum = 0;
					
					for (int i=0; i<myXYs.length; i++) {
						if (myXYs[i][m][d].size() == 0)
							continue;
						
						funcs.add(myXYs[i][m][d]);
						chars.add(new PlotCurveCharacterstics(PlotSymbol.BOLD_CROSS, 3f, scatterCPT.get(i).minColor));
						
						for (Point2D pt : myXYs[i][m][d]) {
							if (pt.getX() > 0) {
								maxVal = Math.max(maxVal, pt.getX());
								minVal = Math.min(minVal, pt.getX());
								totalNum++;
							}
						}
					}
					
					String prefix = "scatter_"+scatterDurationLabels[d].replaceAll(" ", "").toLowerCase();
					String yAxisLabel = scatterDurationLabels[d];
					prefix += "_m"+(float)minMags[m];
					if (isMedian) {
						prefix += "_sim_medians";
						yAxisLabel += " Sim Median";
					} else {
						prefix += "_sim_means";
						yAxisLabel += " Sim Mean";
					}
					String xAxisLabel = scatterDurationLabels[d]+" Data";
					
					if (totalNum == 0) {
						System.out.println("Skipping: "+prefix);
						table.addColumn("*(N/A)*");
						continue;
					}
					System.out.println("Plotting: "+prefix);
					
//					System.out.println("raw min/max: "+minVal+" "+maxVal);
					
					maxVal = Math.max(maxVal, 10d);
					maxVal = Math.pow(10, Math.ceil(Math.log10(maxVal)));

					if (!Double.isFinite(minVal))
						minVal = 0.1;
//					minVal = 0.9;
					minVal = Math.pow(10, Math.floor(Math.log10(minVal)));
					minVal = Math.max(minVal, 0.1);
					
					while (minVal >= maxVal)
						maxVal *= 10;
//					System.out.println("round min/max: "+minVal+" "+maxVal);
					
					DefaultXY_DataSet oneToOne = new DefaultXY_DataSet();
					oneToOne.set(minVal, minVal);
					oneToOne.set(maxVal, maxVal);
					
					funcs.add(oneToOne);
					chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));

					PlotSpec spec = new PlotSpec(funcs, chars, " ", xAxisLabel, yAxisLabel);
					
					spec.addSubtitle(scatterBar);
					
					spec.setLegendVisible(true);

					HeadlessGraphPanel gp = new HeadlessGraphPanel();
					gp.setTickLabelFontSize(18);
					gp.setAxisLabelFontSize(24);
					gp.setPlotLabelFontSize(24);
					gp.setLegendFontSize(18);
					gp.setBackgroundColor(Color.WHITE);
					plotPrefs = gp.getPlotPrefs();
					//					gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);

					xRange = new Range(minVal, maxVal);
					yRange = xRange;

					gp.drawGraphPanel(spec, true, true, xRange, yRange);

					File file = new File(resourcesDir, prefix);
					gp.getChartPanel().setSize(800, 800);
					gp.saveAsPNG(file.getAbsolutePath()+".png");
//					gp.saveAsPDF(file.getAbsolutePath()+".pdf");

					table.addColumn("![chart](resources/"+prefix+".png)");
				}
				
				// now the XYZ
				if (xRange == null) {
					table.addColumn("*(N/A)*");
				} else {
					double maxVal = Math.max(10d, aggregatedXYZs[m][d].getMaxZ());
					CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(1d, maxVal);
					cpt.setBelowMinColor(new Color(255, 255, 255, 0));
					String xAxisLabel = "Log10 "+scatterDurationLabels[d]+" Data";
					String yAxisLabel = "Log10 "+scatterDurationLabels[d]+" Simulation";
					XYZPlotSpec spec = new XYZPlotSpec(aggregatedXYZs[m][d], cpt, " ", xAxisLabel, yAxisLabel, "Count");
					spec.setCPTPosition(RectangleEdge.BOTTOM);
					
					List<XY_DataSet> funcs = new ArrayList<>();
					List<PlotCurveCharacterstics> chars = new ArrayList<>();
					
					xRange = new Range(Math.log10(xRange.getLowerBound()), Math.log10(xRange.getUpperBound()));
					yRange = xRange;
					
					DefaultXY_DataSet oneToOne = new DefaultXY_DataSet();
					oneToOne.set(xRange.getLowerBound(), xRange.getLowerBound());
					oneToOne.set(xRange.getUpperBound(), xRange.getUpperBound());
					funcs.add(oneToOne);
					chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
					spec.setXYElems(funcs);
					spec.setXYChars(chars);
					
					XYZGraphPanel gp = new XYZGraphPanel(plotPrefs);
					gp.drawPlot(spec, false, false, xRange, yRange);
//						gp.getChartPanel().getChart().addSubtitle(slipCPTbar);
					gp.getChartPanel().getChart().setBackgroundPaint(Color.WHITE);
					gp.getChartPanel().setSize(800, 800);
					
					String prefix = "scatter_"+scatterDurationLabels[d].replaceAll(" ", "").toLowerCase();
					prefix += "_m"+(float)minMags[m]+"_indv_catalogs";
					
					gp.saveAsPNG(new File(resourcesDir, prefix+".png").getAbsolutePath());

					table.addColumn("![chart](resources/"+prefix+".png)");
				}
				
				table.finalizeLine();
			}
			lines.addAll(table.build());
			lines.add("");
		}
		
		// write out daily plots
		lines.add("## Daily Comparisons");
		lines.add(topLink); lines.add("");
//		lines.add("These plots show how the actual event count diverges from the simulated mean/median prediction"
//				+ " as a function of time from simulation start. Each individual thin line represents a unique "
//				+ "week, colored by year; mean and median divergence are overlaid with thick lines.");
//		lines.add("");
		for (DailyQuantity type : DailyQuantity.values()) {
			Range yRange;
			switch (type) {
			case PROB:
				lines.add("### Daily Probabilitites");
				yRange = new Range(9e-5, 1.1);
				break;
			case MEDIAN:
				lines.add("### Daily Medians and 95% Conf");
				yRange = new Range(0.1, 1000);
				break;
			case MEAN:
				lines.add("### Daily Means");
				yRange = new Range(9e-5, 1000);
				break;

			default:
				throw new IllegalStateException();
			}
			lines.add(topLink); lines.add("");
			
			List<PlotSpec> specs = new ArrayList<>();
			
			for (double year=Math.floor(minYear); year<Math.ceil(maxYear); year++) {
				GregorianCalendar yearStartCal = new GregorianCalendar();
				yearStartCal.setTimeZone(TimeZone.getTimeZone("UTC"));
				yearStartCal.clear();
				yearStartCal.set(GregorianCalendar.YEAR, (int)year);
				
				System.out.println("Working on "+(int)year+", "+type);
				
				long yearStartMillis = yearStartCal.getTimeInMillis();
				long yearEndMillis = yearStartMillis + 365l*MPJ_WeeklyPostProcessor.day_millis;
				
				List<XY_DataSet> funcs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				
				EvenlyDiscretizedFunc[] dataFuncs = new EvenlyDiscretizedFunc[dailyMags.length];
				boolean first = true;
				for (int m=0; m<dailyMags.length; m++) {
					if (type == DailyQuantity.PROB && dailyMags[m] < 4d)
						continue;
					dataFuncs[m] = new EvenlyDiscretizedFunc(0.5d, 365, 1d);
					if (first) {
						if (type == DailyQuantity.PROB)
							dataFuncs[m].setName("Data M≥"+(float)dailyMags[m]);
						else
							dataFuncs[m].setName("Data M≥"+(float)dailyMags[m]);
					} else {
						dataFuncs[m].setName("M≥"+(float)dailyMags[m]);
					}
					funcs.add(dataFuncs[m]);
					chars.add(dailyDataChars[m]);
					first = false;
				}
				for (ObsEqkRupture rup : combEvents) {
					long time = rup.getOriginTime();
					if (time < yearStartMillis)
						continue;
					if (time >= yearEndMillis)
						break;
					double mag = rup.getMag();
					double day = (double)(time - yearStartMillis)/(double)MPJ_WeeklyPostProcessor.day_millis;
					int dayIndex = dataFuncs[dataFuncs.length-1].getClosestXIndex(day);
					for (int m=0; m<dailyMags.length; m++) {
						if (mag >= dailyMags[m] && dataFuncs[m] != null) {
							if (type == DailyQuantity.PROB)
								dataFuncs[m].set(dayIndex, 1d);
							else
								dataFuncs[m].set(dayIndex, dataFuncs[m].getY(dayIndex)+1d);
						}
					}
				}
				
				boolean firstCall = true;
				for (CalcCallable call : calls) {
					if (call.dayEndTimes == null || call.startTimeMillis > yearEndMillis)
						continue;
					first = true;
					
					XY_DataSet[] simFuncs = new XY_DataSet[dailyMags.length];
					XY_DataSet[] lowerFuncs = new XY_DataSet[dailyMags.length];
					XY_DataSet[] upperFuncs = new XY_DataSet[dailyMags.length];
					
					boolean firstDay = true;
					for (int d=0; d<7; d++) {
						long dayMillis = call.startTimeMillis + d*MPJ_WeeklyPostProcessor.day_millis;
						long diffMillis = dayMillis - yearStartMillis;
						if (diffMillis < 0l)
							continue;
						double startDiffDays = (double)diffMillis/(double)MPJ_WeeklyPostProcessor.day_millis;
						double endDiffDays = startDiffDays + 1d;
						if (startDiffDays > 364.5d)
							break;
						
						if (simFuncs[simFuncs.length-1] == null) {
							for (int m=0; m<dailyMags.length; m++) {
								if (type == DailyQuantity.PROB && dailyMags[m] < 4d)
									continue;
								simFuncs[m] = new DefaultXY_DataSet();
								if (firstCall) {
									if (first)
										simFuncs[m].setName("Sim M≥"+(float)dailyMags[m]);
									else
										simFuncs[m].setName("M≥"+(float)dailyMags[m]);
								}
								funcs.add(simFuncs[m]);
								chars.add(dailySimChars[m]);
								
								if (type == DailyQuantity.MEDIAN) {
									PlotCurveCharacterstics boundChar = new PlotCurveCharacterstics(
											dailySimChars[m].getLineType(), 1f, dailySimChars[m].getColor());
									lowerFuncs[m] = new DefaultXY_DataSet();
									funcs.add(lowerFuncs[m]);
									chars.add(boundChar);
									upperFuncs[m] = new DefaultXY_DataSet();
									funcs.add(upperFuncs[m]);
									chars.add(boundChar);
								}
								first = false;
							}
							firstCall = false;
						}
						
						if (firstDay) {
							DefaultXY_DataSet markerFunc = new DefaultXY_DataSet();
							markerFunc.set(startDiffDays, yRange.getLowerBound());
							markerFunc.set(startDiffDays, yRange.getUpperBound());
							funcs.add(markerFunc);
							chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.DARK_GRAY));
							firstDay = false;
						}
						
						for (int m=0; m<dailyMags.length; m++) {
							if (simFuncs[m] == null)
								continue;
							switch (type) {
							case PROB:
								simFuncs[m].set(startDiffDays, call.dailyProbs[m][d]);
								simFuncs[m].set(endDiffDays, call.dailyProbs[m][d]);
								break;
							case MEDIAN:
								simFuncs[m].set(startDiffDays, call.dailyMedians[m][d]);
								lowerFuncs[m].set(startDiffDays, call.dailyP25s[m][d]);
								upperFuncs[m].set(startDiffDays, call.dailyP975s[m][d]);
								simFuncs[m].set(endDiffDays, call.dailyMedians[m][d]);
								lowerFuncs[m].set(endDiffDays, call.dailyP25s[m][d]);
								upperFuncs[m].set(endDiffDays, call.dailyP975s[m][d]);
								break;
							case MEAN:
								simFuncs[m].set(startDiffDays, call.dailyMeans[m][d]);
								simFuncs[m].set(endDiffDays, call.dailyMeans[m][d]);
								break;

							default:
								break;
							}
						}
					}
				}
				
				for (int i=funcs.size(); --i>=0;) {
					if (funcs.get(i).size() == 0) {
						funcs.remove(i);
						chars.remove(i);
					}
				}
				
				PlotSpec spec = new PlotSpec(funcs, chars, "Daily "+type+" Comparisons",
						"Day Of Year", type.toString());
				spec.setLegendVisible(specs.isEmpty());
				
				XYTextAnnotation ann = new XYTextAnnotation((int)year+"", 1d, yRange.getUpperBound());
				ann.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
				ann.setTextAnchor(TextAnchor.TOP_LEFT);
				spec.addPlotAnnotation(ann);
				
				specs.add(spec);
				
				if (highlightYears != null) {
					int yearInt = (int)year;
					for (int highlightYear : highlightYears) {
						if (yearInt == highlightYear) {
							System.out.println("Writing out highlight plot for "+yearInt);
							HeadlessGraphPanel gp = new HeadlessGraphPanel();
							gp.setTickLabelFontSize(18);
							gp.setAxisLabelFontSize(24);
							gp.setPlotLabelFontSize(24);
							gp.setLegendFontSize(18);
							gp.setBackgroundColor(Color.WHITE);
							//					gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);

							Range xRange = new Range(0d, 365d);

							boolean wasLegend = spec.isLegendVisible();
							spec.setLegendVisible(true);
							gp.drawGraphPanel(spec, false, true, xRange, yRange);
							spec.setLegendVisible(wasLegend);

							String prefix = "daily_"+type.name()+"_"+yearInt;
							File file = new File(resourcesDir, prefix);
							gp.getChartPanel().setSize(1000, 650);
							gp.saveAsPNG(file.getAbsolutePath()+".png");
//							gp.saveAsPDF(file.getAbsolutePath()+".pdf");
						}
					}
				}
			}
			
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setTickLabelFontSize(18);
			gp.setAxisLabelFontSize(24);
			gp.setPlotLabelFontSize(24);
			gp.setLegendFontSize(18);
			gp.setBackgroundColor(Color.WHITE);
			//					gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);

			Range xRange = new Range(0d, 365d);
			List<Range> xRanges = new ArrayList<>();
			List<Range> yRanges = new ArrayList<>();
			xRanges.add(xRange);
			for (int i=0; i<specs.size(); i++)
				yRanges.add(yRange);

			gp.drawGraphPanel(specs, false, true, xRanges, yRanges);

			String prefix = "daily_"+type.name();
			File file = new File(resourcesDir, prefix);
			int height = 150 + 200*specs.size();
			gp.getChartPanel().setSize(1000, height);
			gp.saveAsPNG(file.getAbsolutePath()+".png");
//			gp.saveAsPDF(file.getAbsolutePath()+".pdf");

			lines.add("![Daily Plot](resources/"+prefix+".png)");
			lines.add("");
		}

		// write out divergence plots
		lines.add("## Data Divergence Over Time");
		lines.add(topLink); lines.add("");
		lines.add("These plots show how the actual event count diverges from the simulated mean/median prediction"
				+ " as a function of time from simulation start. Each individual thin line represents a unique "
				+ "week, colored by year; mean and median divergence are overlaid with thick lines.");
		lines.add("");
		
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(minYear, maxYear);
		for (int i=0; i<cpt.size(); i++) {
			CPTVal cptVal = cpt.get(i);
			Color min = cptVal.minColor;
			Color max = cptVal.maxColor;
			cptVal.minColor = new Color(min.getRed(), min.getGreen(), min.getBlue(), 50);
			cptVal.maxColor = new Color(max.getRed(), max.getGreen(), max.getBlue(), 50);
		}
		cpt.setBelowMinColor(cpt.getMinColor());
		cpt.setAboveMaxColor(cpt.getMaxColor());
		PaintScaleLegend cptBar = XYZGraphPanel.getLegendForCPT(cpt, "Year",
				22, 18, yearInc, RectangleEdge.BOTTOM);

		for (boolean cumulative : new boolean[] { true, false }) {
			List<DiscretizedFunc[]> meanFuncs, medianFuncs;
			if (cumulative) {
				lines.add("### Cumulative Divergence Plots");
				lines.add(topLink); lines.add("");
				lines.add("These plots show cumulative divergence from the start of the simulation.");
				lines.add("");

				meanFuncs = cumulativeMeanDiffFuncs;
				medianFuncs = cumulativeMedianDiffFuncs;
			} else {
				lines.add("### Daily Incremental Divergence Plots");
				lines.add(topLink); lines.add("");
				lines.add("These plots show incremental divergence binned by day.");
				lines.add("");
				meanFuncs = dailyIncrMeanDiffFuncs;
				medianFuncs = dailyIncrMedianDiffFuncs;
			}

			table = MarkdownUtils.tableBuilder();

			table.addLine("Min Mag", "Mean Simulation Values", "Median Simulation Values");

			for (int m=0; m<minMags.length; m++) {
				table.initNewLine();
				table.addColumn("**M&ge;"+(float)minMags[m]+"**");
				double meanMaxY = Double.NaN;
				for (boolean isMedian : new boolean[] {false, true}) {
					List<DiscretizedFunc> funcs = new ArrayList<>();
					List<PlotCurveCharacterstics> chars = new ArrayList<>();

					List<DiscretizedFunc[]> myFuncs = isMedian ? medianFuncs : meanFuncs;

					List<List<Double>> vals = new ArrayList<>();
					List<Double> xVals = new ArrayList<>();

					for (int i=0; i<myFuncs.size(); i++) {
						DiscretizedFunc[] magFuncs = myFuncs.get(i);
						DiscretizedFunc func = magFuncs[m];

						double year = years.get(i);
						Color color = cpt.getColor((float)year);
						if (func.size() > 0) {
							funcs.add(func);
							chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, color));
						}

						for (int j=0; j<func.size(); j++) {
							if (j == vals.size()) {
								vals.add(new ArrayList<>());
								xVals.add(func.getX(j));
							}
							double val = func.getY(j);
							Preconditions.checkState(Double.isFinite(val), "Bad value: %s", val);
							vals.get(j).add(val);
						}
					}

					DiscretizedFunc meanFunc = new ArbitrarilyDiscretizedFunc();
					DiscretizedFunc medianFunc = new ArbitrarilyDiscretizedFunc();

					double maxY = 0d;
					double maxX = xVals.get(xVals.size()-1);

					for (int i=0; i<vals.size(); i++) {
						double[] array = Doubles.toArray(vals.get(i));
						if (array.length == 0)
							continue;
						double x = xVals.get(i);
						Preconditions.checkState(Double.isFinite(x));
						meanFunc.set(x, StatUtils.mean(array));
						medianFunc.set(x, DataUtils.median(array));

						double[] absArray = new double[array.length];
						for (int j=0; j<array.length; j++)
							absArray[j] = Math.abs(array[j]);
						maxY = StatUtils.percentile(absArray, 95d);
					}
					maxY = Math.max(maxY, 1d);
					if (isMedian)
						maxY = meanMaxY;
					else
						meanMaxY = maxY;

					funcs.add(meanFunc);
					meanFunc.setName("Mean Difference");
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));

					funcs.add(medianFunc);
					medianFunc.setName("Median Difference");
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLUE));

					String prefix = "";
					String yAxisLabel;
					if (cumulative) {
						prefix = "cumulative";
						yAxisLabel = "Cumulative";
					} else {
						prefix = "incremental";
						yAxisLabel = "Daily";
					}
					prefix += "_m"+(float)minMags[m];
					if (isMedian) {
						prefix += "_sim_medians";
						yAxisLabel += " Sim Median - Data";
					} else {
						prefix += "_sim_means";
						yAxisLabel += " Sim Mean - Data";
					}
					
					System.out.println("Plotting: "+prefix);

					PlotSpec spec = new PlotSpec(funcs, chars, " ", "Days Since Sim Start", yAxisLabel);
					
					spec.addSubtitle(cptBar);
					
					spec.setLegendVisible(true);

					HeadlessGraphPanel gp = new HeadlessGraphPanel();
					gp.setTickLabelFontSize(18);
					gp.setAxisLabelFontSize(24);
					gp.setPlotLabelFontSize(24);
					gp.setLegendFontSize(18);
					gp.setBackgroundColor(Color.WHITE);
					//					gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);

					Range xRange = new Range(0d, maxX);
					Range yRange = new Range(-maxY, maxY);

					gp.drawGraphPanel(spec, false, false, xRange, yRange);

					File file = new File(resourcesDir, prefix);
					gp.getChartPanel().setSize(800, 600);
					gp.saveAsPNG(file.getAbsolutePath()+".png");
//					gp.saveAsPDF(file.getAbsolutePath()+".pdf");

					table.addColumn("![chart](resources/"+prefix+".png)");
				}
				table.finalizeLine();
			}
			lines.addAll(table.build());
			lines.add("");
		}

		List<String> tocLines = new ArrayList<>();
		tocLines.add("## Table Of Contents");
		tocLines.add("");
		tocLines.addAll(MarkdownUtils.buildTOC(lines, 2, 3));

		lines.addAll(tocIndex, tocLines);

		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
		
		System.out.println("DONE");
	}
	
	private enum DailyQuantity {
		PROB("Probability"),
		MEDIAN("Median"),
		MEAN("Mean");
		
		String label;
		private DailyQuantity(String label) {
			this.label = label;
		}
		
		public String toString() {
			return label;
		}
	}

	private static double getYear(long millis) {
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTimeZone(TimeZone.getTimeZone("UTC"));
		cal.setTimeInMillis(millis);
		double year = cal.get(GregorianCalendar.YEAR);
		year += (cal.get(GregorianCalendar.DAY_OF_YEAR)-1d)/365.25;
		return year;
	}
	
	private static class CalcCallable implements Callable<CalcCallable>, Comparable<CalcCallable> {
		
		private long curTime;
		private File simDir;
		private double[] minMags;
		private double[] scatterDurations;
		private double[] dailyMinMags;
		
		private long startTimeMillis;
		private long endTimeMillis;
		
		private DiscretizedFunc[] medianCumulativeFuncs;
		private DiscretizedFunc[] medianIncrFuncs;
		private DiscretizedFunc[] meanCumulativeFuncs;
		private DiscretizedFunc[] meanIncrFuncs;
		private double[][] meanCumCounts;
		private double[][] medianCumCounts;
		private double[][] dataCumCounts;
		
		private double[][] dataPercentiles;
		private boolean[][] below2p5s;
		private boolean[][] above97p5s;
		
		private double[][] fractZeros;
		
		private ObsEqkRupList subCatalog;
		
		private EvenlyDiscrXYZ_DataSet globalXYZ;
		private EvenlyDiscrXYZ_DataSet[][] indvXYZs;
		
		private long[] dayEndTimes;
		private double[][] dailyProbs;
		private double[][] dailyMedians;
		private double[][] dailyMeans;
		private double[][] dailyP25s;
		private double[][] dailyP975s;
		
		private CalcCallable(ObsEqkRupList combEvents, long curTime, File simDir, ETAS_Config config, double[] minMags,
				double[] scatterDurations, double[] dailyMinMags, EvenlyDiscrXYZ_DataSet globalXYZ) {
			this.curTime = curTime;
			this.simDir = simDir;
			this.minMags = minMags;
			this.dailyMinMags = dailyMinMags;
			this.scatterDurations = scatterDurations;
			this.globalXYZ = globalXYZ;
			startTimeMillis = config.getSimulationStartTimeMillis();
			endTimeMillis = Long.min(curTime, startTimeMillis +
					(long)(config.getDuration()*ProbabilityModelsCalc.MILLISEC_PER_YEAR));

			subCatalog = new ObsEqkRupList();
			for (ObsEqkRupture rup : combEvents) {
				long time = rup.getOriginTime();
				if (time < startTimeMillis)
					continue;
				else if (time >= endTimeMillis)
					break;
				if (rup.getMag() >= 2.5d)
					subCatalog.add(rup);
			}
			System.out.println("\tObserved events: "+subCatalog.size());
		}

		@Override
		public CalcCallable call() throws Exception {

			
			medianCumulativeFuncs = new DiscretizedFunc[minMags.length];
			medianIncrFuncs = new DiscretizedFunc[minMags.length];
			meanCumulativeFuncs = new DiscretizedFunc[minMags.length];
			meanIncrFuncs = new DiscretizedFunc[minMags.length];

			medianCumCounts = new double[minMags.length][scatterDurations.length];
			meanCumCounts = new double[minMags.length][scatterDurations.length];
			dataCumCounts = new double[minMags.length][scatterDurations.length];
			dataPercentiles = new double[minMags.length][scatterDurations.length];
			below2p5s = new boolean[minMags.length][scatterDurations.length];
			above97p5s = new boolean[minMags.length][scatterDurations.length];
			
			indvXYZs = new EvenlyDiscrXYZ_DataSet[minMags.length][scatterDurations.length];
			fractZeros = new double[minMags.length][scatterDurations.length];
			
			File resultsDir = new File(simDir, "aggregated_results");

			for (int m=0; m<minMags.length; m++) {
				double minMag = minMags[m];
				// always load the cumulative
				// will infer incremental if necessary
				String csvName = "m"+(float)minMag+"_cumulative_time_stats.csv";
				File csvFile = new File(resultsDir, csvName);
				CSVFile<String> csv = CSVFile.readFile(csvFile, true);

				for (boolean isMedian : new boolean[] { false, true }) {
					DiscretizedFunc cumulativeSimFunc = new ArbitrarilyDiscretizedFunc();
					DiscretizedFunc dataFunc = new ArbitrarilyDiscretizedFunc();
					cumulativeSimFunc.set(0d, 0d);
					dataFunc.set(0d, 0d);

					int col = isMedian ? 6 : 2;
					
					double maxData = ((double)(curTime - startTimeMillis)
							/(double)ProbabilityModelsCalc.MILLISEC_PER_DAY);

					for (int row=1; row<csv.getNumRows(); row++) {
						double val = csv.getDouble(row, col);
						long time = csv.getLong(row, 0);
						double days = (double)(time - startTimeMillis)/(double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
						cumulativeSimFunc.set(days, val);
						if (days <= maxData)
							dataFunc.set(days, 0d); // will fill in
					}
					
					double[] magDurVals = isMedian ? medianCumCounts[m] : meanCumCounts[m];
					for (int d=0; d<scatterDurations.length; d++)
						magDurVals[d] = cumulativeSimFunc.getInterpolatedY(scatterDurations[d]);
					
					for (ObsEqkRupture rup : subCatalog) {
						if (rup.getMag() < minMag)
							continue;
						double rupDays = (double)(rup.getOriginTime() - startTimeMillis)/(double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
						for (int i=0; i<dataFunc.size(); i++)
							if (rupDays <= dataFunc.getX(i))
								dataFunc.set(i, dataFunc.getY(i)+1);
					}
					
					for (int d=0; isMedian && d<scatterDurations.length; d++) {
						if (scatterDurations[d] > maxData)
							dataCumCounts[m][d] = Double.NaN;
						else
							dataCumCounts[m][d] = dataFunc.getInterpolatedY(scatterDurations[d]);
					}

					for (boolean cumulative : new boolean[] { false, true }) {
						if (cumulative) {
							DiscretizedFunc diffFunc = new ArbitrarilyDiscretizedFunc() {
								public String toString() {
									return "";
								}
							};
							for (Point2D dataPt : dataFunc) {
								double simY = cumulativeSimFunc.getY(dataPt.getX());
								double diff = simY - dataPt.getY();
								diffFunc.set(dataPt.getX(), diff);
							}
							if (isMedian)
								medianCumulativeFuncs[m] = diffFunc;
							else
								meanCumulativeFuncs[m] = diffFunc;
						} else {
//							DiscretizedFunc diffFunc = new ArbitrarilyDiscretizedFunc();
							int num = Integer.min(365, (int)maxData);
							EvenlyDiscretizedFunc diffFunc = new EvenlyDiscretizedFunc(0.5d, num, 1d) {
								public String toString() {
									return "";
								}
							};
							for (int i=0; i<diffFunc.size(); i++) {
								double x = diffFunc.getX(i);
								double start = x - 0.5d;
								double end = x + 0.5d;
								
								double simStart, simEnd, dataStart, dataEnd;
								if (i == 0) {
									simStart = 0d;
									dataStart = 0d;
								} else {
									simStart = cumulativeSimFunc.getInterpolatedY(start);
									dataStart = dataFunc.getInterpolatedY(start);
								}
								if (end > cumulativeSimFunc.getMaxX())
									simEnd = cumulativeSimFunc.getMaxY();
								else
									simEnd = cumulativeSimFunc.getInterpolatedY(end);
								if (end > dataFunc.getMaxX())
									dataEnd = dataFunc.getMaxY();
								else
									dataEnd = dataFunc.getInterpolatedY(end);
								double simVal = simEnd - simStart;
								double dataVal = dataEnd - dataStart;
								double diff = simVal - dataVal;
								
								diffFunc.set(i, diff);
							}
							if (isMedian)
								medianIncrFuncs[m] = diffFunc;
							else
								meanIncrFuncs[m] = diffFunc;
						}
					}
				}
				csvName = "m"+(float)minMag+"_indv_fixed_duration_counts.csv";
				csvFile = new File(resultsDir, csvName);
				if (csvFile.exists()) {
					csv = CSVFile.readFile(csvFile, true);
					for (int d=0; d<scatterDurations.length; d++)
						indvXYZs[m][d] = globalXYZ.copy();

					for (int d=0; d<scatterDurations.length; d++) {
						double dataVal = Math.floor(dataCumCounts[m][d]);
						double dataLogVal = Math.log10(dataVal);
						int xInd = globalXYZ.getXIndex(dataLogVal);
						if (xInd < 0)
							xInd = 0;
						if (xInd >= globalXYZ.getNumX())
							xInd = globalXYZ.getNumX()-1;
						double[] allCounts = new double[csv.getNumRows()-1];
						int numZero = 0;
						for (int row=1; row<csv.getNumRows(); row++) {
							int simCount = csv.getInt(row, d+1);
							allCounts[row-1] = simCount;
							if (simCount == 0)
								numZero++;
							double simLogVal = Math.log10(simCount);
							int yInd = globalXYZ.getYIndex(simLogVal);
							if (yInd < 0)
								yInd = 0;
							if (yInd >= globalXYZ.getNumY())
								yInd = globalXYZ.getNumY()-1;
							indvXYZs[m][d].set(xInd, yInd, indvXYZs[m][d].get(xInd, yInd)+1);
						}
						fractZeros[m][d] = (double)numZero/(double)allCounts.length;
						dataPercentiles[m][d] = ETAS_ComcatComparePlot.invPercentile(allCounts, dataVal);
						below2p5s[m][d] = dataVal < StatUtils.percentile(allCounts, 2.5d);
						above97p5s[m][d] = dataVal > StatUtils.percentile(allCounts, 97.5d);
					}
				}
			}
			for (int m=0; m<dailyMinMags.length; m++) {
				double minMag = dailyMinMags[m];
				String csvName = "m"+(float)minMag+"_daily_counts.csv";
				File csvFile = new File(resultsDir, csvName);
				if (csvFile.exists()) {
					CSVFile<String> csv = CSVFile.readFile(csvFile, true);
					
					if (csv.getNumRows() > 7) {
						if (dayEndTimes == null) {
							dayEndTimes = new long[csv.getNumRows()-1];
							for (int d=0; d<dayEndTimes.length; d++)
								dayEndTimes[d] = csv.getLong(d+1, 0);
							dailyProbs = new double[minMags.length][dayEndTimes.length];
							dailyMedians = new double[minMags.length][dayEndTimes.length];
							dailyMeans = new double[minMags.length][dayEndTimes.length];
							dailyP25s = new double[minMags.length][dayEndTimes.length];
							dailyP975s = new double[minMags.length][dayEndTimes.length];
						}
						
						for (int d=0; d<dayEndTimes.length; d++) {
							int row = d+1;
							dailyProbs[m][d] = csv.getDouble(row, 1);
							dailyMeans[m][d] = csv.getDouble(row, 2);
							dailyMedians[m][d] = csv.getDouble(row, 6);
							dailyP25s[m][d] = csv.getDouble(row, 4);
							dailyP975s[m][d] = csv.getDouble(row, 8);
						}
					}
				}
			}
			return this;
		}

		@Override
		public int compareTo(CalcCallable o) {
			return Long.compare(startTimeMillis, o.startTimeMillis);
		}
		
	}

}
