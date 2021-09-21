package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYBoxAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalar;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.HistScalarValues;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.PlausibilityResult;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarCoulombPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.ScalarValuePlausibiltyFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.GapWithinSectFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpDistFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.MultiDirectionalPlausibilityFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.SplayCountFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RupCartoonGenerator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureConnectionSearch;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator;

import com.google.common.base.Preconditions;

public class PlausibilityFilterPlot extends AbstractRupSetPlot {
	
	private List<PlausibilityFilter> externalFilters;

	public PlausibilityFilterPlot() {
		this(null);
	}
	
	public PlausibilityFilterPlot(List<PlausibilityFilter> externalFilters) {
		this.externalFilters = externalFilters;
	}

	@Override
	public String getName() {
		return "Plausibility Comparisons";
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return externalFilters == null ? List.of(PlausibilityConfiguration.class, ClusterRuptures.class)
				: Collections.singleton(ClusterRuptures.class);
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		List<String> lines = new ArrayList<>();
		
		PlausibilityConfiguration primaryConfig = meta.primary.rupSet.getModule(PlausibilityConfiguration.class);
		
		PlausibilityConfiguration compConfig = null;
		if (meta.comparison != null)
			compConfig = meta.comparison.rupSet.getModule(PlausibilityConfiguration.class);
		
		boolean canDoComparison = meta.comparison != null && meta.comparison.rupSet.hasModule(ClusterRuptures.class);
		
		if (externalFilters == null) {
			if (compConfig != null && compConfig.getFilters() != null && !compConfig.getFilters().isEmpty()) {
				lines.add(getSubHeading()+" Comparisons with "+meta.comparison.name+" filters");
				lines.add(topLink); lines.add("");
				
				List<PlausibilityFilter> filters = new ArrayList<>();
				// add implicit filters
				double jumpDist = compConfig.getConnectionStrategy().getMaxJumpDist();
				if (Double.isFinite(jumpDist))
					filters.add(new JumpDistFilter(jumpDist));
				filters.add(new SplayCountFilter(compConfig.getMaxNumSplays()));
				filters.add(new GapWithinSectFilter());
				filters.addAll(compConfig.getFilters());
				
				lines.addAll(doPlot(filters, meta.primary, resourcesDir, relPathToResources, "comp_filter_",
						"Comparison with "+getTruncatedTitle(meta.comparison.name)+" Filters", topLink));
				lines.add("");
			}
			
			if (primaryConfig != null && canDoComparison) {
				lines.add(getSubHeading()+" "+meta.comparison.name+" comparisons with new filters");
				lines.add(topLink); lines.add("");
				
				List<PlausibilityFilter> filters = new ArrayList<>();
				// add implicit filters
				double jumpDist = primaryConfig.getConnectionStrategy().getMaxJumpDist();
				if (Double.isFinite(jumpDist))
					filters.add(new JumpDistFilter(jumpDist));
				filters.add(new SplayCountFilter(primaryConfig.getMaxNumSplays()));
				filters.add(new GapWithinSectFilter());
				filters.addAll(primaryConfig.getFilters());
				
				lines.addAll(doPlot(filters, meta.comparison, resourcesDir, relPathToResources, "main_filter_",
						"Comparison with "+getTruncatedTitle(meta.primary.name)+" Filters", topLink));
				lines.add("");
			}
		} else {
			lines.add(getSubHeading()+" Comparisons with Alternative Filters");
			lines.add(topLink); lines.add("");
			lines.addAll(doPlot(externalFilters, meta.primary, resourcesDir, relPathToResources, "alt_main_filter_",
						"Comparison with Alternative Filters Filters", topLink));
			lines.add("");
		}
		return lines;
	}
	
	private List<String> doPlot(List<PlausibilityFilter> filters, RupSetMetadata meta,
			File outputDir, String relPath, String prefix, String title, String topLink) throws IOException {
		List<ClusterRupture> rups = meta.rupSet.getModule(ClusterRuptures.class).getAll(); 
		PlausibilityConfiguration config = meta.rupSet.getModule(PlausibilityConfiguration.class);
		RuptureConnectionSearch search = meta.rupSet.getModule(RuptureConnectionSearch.class);
		RupSetPlausibilityResult result = testRupSetPlausibility(rups, filters, config, search);
		File plot = plotRupSetPlausibility(result, outputDir, prefix+"_compare", title);
		List<String> lines = new ArrayList<>();
		lines.add("![plot]("+relPath+"/"+plot.getName()+")");
		lines.add("");
		lines.addAll(getRupSetPlausibilityTable(result, meta.sol, meta.name).build());
		lines.add("");
		lines.add("**Magnitude-filtered comparisons**");
		lines.add("");
		lines.addAll(getMagPlausibilityTable(meta.rupSet, result, outputDir,
				relPath, prefix+"_mag_comp").wrap(2, 0).build());
		lines.add("");
		lines.addAll(getRupSetPlausibilityDetailLines(result, false, meta.rupSet, rups, 15,
				outputDir, relPath, getSubHeading()+"# "+meta.name, topLink, search, meta.scalarValues));
		return lines;
	}
	
	public static RupSetPlausibilityResult testRupSetPlausibility(List<ClusterRupture> rups,
			List<PlausibilityFilter> filters, PlausibilityConfiguration config,
			RuptureConnectionSearch connSearch) {
		int threads = Runtime.getRuntime().availableProcessors();
		if (threads > 8)
			threads -= 2;
		threads = Integer.max(1, Integer.min(31, threads));
		ExecutorService exec = Executors.newFixedThreadPool(threads);
		RupSetPlausibilityResult ret = testRupSetPlausibility(rups, filters, config, connSearch, exec);
		exec.shutdown();
		return ret;
	}
	
	public static RupSetPlausibilityResult testRupSetPlausibility(List<ClusterRupture> rups,
			List<PlausibilityFilter> filters, PlausibilityConfiguration config,
			RuptureConnectionSearch connSearch, ExecutorService exec) {
		boolean hasSplays = false;
		for (ClusterRupture rup : rups) {
			if (!rup.splays.isEmpty()) {
				hasSplays = true;
				break;
			}
		}
		
		List<PlausibilityFilter> newFilters = new ArrayList<>();
		for (PlausibilityFilter filter : filters) {
			if (filter instanceof JumpAzimuthChangeFilter)
//				filter = new ErrOnCantEvalAzFilter(filter, false);
				((JumpAzimuthChangeFilter)filter).setErrOnCantEvaluate(true);
			if (filter.isDirectional(false) || (hasSplays && filter.isDirectional(true))) {
				if (config == null) {
					if (filter instanceof ScalarValuePlausibiltyFilter<?>)
						filter = new MultiDirectionalPlausibilityFilter.Scalar(
								(ScalarValuePlausibiltyFilter<?>)filter, connSearch,
								!filter.isDirectional(false));
					else
						filter = new MultiDirectionalPlausibilityFilter(
								filter, connSearch, !filter.isDirectional(false));
				} else {
					if (filter instanceof ScalarValuePlausibiltyFilter<?>)
						filter = new MultiDirectionalPlausibilityFilter.Scalar(
								(ScalarValuePlausibiltyFilter<?>)filter, config,
								!filter.isDirectional(false));
					else
						filter = new MultiDirectionalPlausibilityFilter(
								filter, config, !filter.isDirectional(false));
				}
			}
			newFilters.add(filter);
		}
		
		List<Future<PlausibilityCalcCallable>> futures = new ArrayList<>();
		
		for (int r=0; r<rups.size(); r++) {
			ClusterRupture rupture = rups.get(r);
			futures.add(exec.submit(new PlausibilityCalcCallable(newFilters, rupture, r)));
		}
		RupSetPlausibilityResult fullResults = new RupSetPlausibilityResult(filters, rups.size());
		
		System.out.println("Waiting on "+futures.size()+" plausibility calc futures...");
		for (Future<PlausibilityCalcCallable> future : futures) {
			try {
				future.get().merge(fullResults);
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		System.out.println("DONE with plausibility");
		
		return fullResults;
	}
	
	private static class PlausibilityCalcCallable implements Callable<PlausibilityCalcCallable> {
		
		// inputs
		private ClusterRupture rupture;
		private List<PlausibilityFilter> filters;
		private int rupIndex;
		
		// outputs
		private PlausibilityResult[] results;
		private Throwable[] exceptions;
		private Double[] scalars;

		public PlausibilityCalcCallable(List<PlausibilityFilter> filters, ClusterRupture rupture,
				int rupIndex) {
			super();
			this.filters = filters;
			this.rupture = rupture;
			this.rupIndex = rupIndex;
		}

		@Override
		public PlausibilityCalcCallable call() throws Exception {
			results = new PlausibilityResult[filters.size()];
			exceptions = new Throwable[filters.size()];
			scalars = new Double[filters.size()];
			for (int t=0; t<filters.size(); t++) {
				PlausibilityFilter filter = filters.get(t);
				try {
					results[t] = filter.apply(rupture, false);
					if (filter instanceof ScalarValuePlausibiltyFilter<?>) {
						Number scalar = ((ScalarValuePlausibiltyFilter<?>)filter)
								.getValue(rupture);
						if (scalar != null)
							scalars[t] = scalar.doubleValue();
					}
				} catch (Exception e) {
					exceptions[t] = e;
				}
			}
			return this;
		}
		
		public void merge(RupSetPlausibilityResult fullResults) {
			fullResults.addResult(rupture, results, exceptions, scalars);
		}
		
	}
	
	public static class RupSetPlausibilityResult {
		public final List<PlausibilityFilter> filters;
		public final int numRuptures;
		public int allPassCount;
		
		public final List<List<PlausibilityResult>> filterResults;
		public final List<List<Double>> scalarVals;
		public final List<Boolean> singleFailures;
		public final int[] failCounts;
		public final int[] failCanContinueCounts;
		public final int[] onlyFailCounts;
		public final int[] erredCounts;
		
		private RupSetPlausibilityResult(List<PlausibilityFilter> filters, int numRuptures) {
			this.filters = filters;
			this.numRuptures = numRuptures;
			this.allPassCount = 0;
			this.filterResults = new ArrayList<>();
			this.scalarVals = new ArrayList<>();
			for (PlausibilityFilter filter : filters) {
				filterResults.add(new ArrayList<>());
				if (filter instanceof ScalarValuePlausibiltyFilter<?>)
					scalarVals.add(new ArrayList<>());
				else
					scalarVals.add(null);
			}
			this.singleFailures = new ArrayList<>();
			failCounts = new int[filters.size()];
			failCanContinueCounts = new int[filters.size()];
			onlyFailCounts = new int[filters.size()];
			erredCounts = new int[filters.size()];
		}
		
		public RupSetPlausibilityResult(List<PlausibilityFilter> filters, int numRuptures, int allPassCount,
				List<List<PlausibilityResult>> filterResults, List<List<Double>> scalarVals,
				List<Boolean> singleFailures, int[] failCounts, int[] failCanContinueCounts, int[] onlyFailCounts,
				int[] erredCounts) {
			super();
			this.filters = filters;
			this.numRuptures = numRuptures;
			this.allPassCount = allPassCount;
			this.filterResults = filterResults;
			this.scalarVals = scalarVals;
			this.singleFailures = singleFailures;
			this.failCounts = failCounts;
			this.failCanContinueCounts = failCanContinueCounts;
			this.onlyFailCounts = onlyFailCounts;
			this.erredCounts = erredCounts;
		}
		
		public RupSetPlausibilityResult filterByMag(FaultSystemRupSet rupSet, double minMag) {
			List<Integer> matchingRups = new ArrayList<>();
			for (int r=0; r<rupSet.getNumRuptures(); r++)
				if (rupSet.getMagForRup(r) >= minMag)
					matchingRups.add(r);
			return filterByRups(matchingRups);
		}
		
		public RupSetPlausibilityResult filterByRups(Collection<Integer> matchingRups) {
			if (matchingRups.isEmpty())
				return null;
			RupSetPlausibilityResult ret = new RupSetPlausibilityResult(filters, matchingRups.size());
			Throwable fakeException = new RuntimeException("Placeholder exception");
			for (int r : matchingRups) {
				PlausibilityResult[] results = new PlausibilityResult[filters.size()];
				Throwable[] exceptions = new Throwable[filters.size()];
				Double[] scalars = new Double[filters.size()];
				for (int f=0; f<filters.size(); f++) {
					results[f] = filterResults.get(f).get(r);
					if (results[f] == null)
						exceptions[f] = fakeException;
					if (scalarVals.get(f) != null)
						scalars[f] = scalarVals.get(f).get(r);
				}
				ret.addResult(null, results, exceptions, scalars);
			}
			return ret;
		}
		
		private void addResult(ClusterRupture rupture, PlausibilityResult[] results, Throwable[] exceptions, Double[] scalars) {
			Preconditions.checkState(results.length == filters.size());
			boolean allPass = true;
			int onlyFailureIndex = -1;
			for (int t=0; t<filters.size(); t++) {
				PlausibilityFilter test = filters.get(t);
				PlausibilityResult result = results[t];
				
				boolean subPass;
				if (exceptions[t] != null) {
					if (erredCounts[t] == 0 && (exceptions[t].getMessage() == null
							|| !exceptions[t].getMessage().startsWith("Placeholder"))) {
						System.err.println("First exception for "+test.getName()+":");
						exceptions[t].printStackTrace();
						if (rupture != null) {
							System.err.println("Running in verbose mode for rupture:\n\t"+rupture);
							try {
								this.filters.get(t).apply(rupture, true);
							} catch (Exception e) {}
							System.err.println("DONE verbose");
						}
					}
					erredCounts[t]++;
					subPass = true; // do not fail on error
					result = null;
				} else {
					if (result == PlausibilityResult.FAIL_FUTURE_POSSIBLE)
						failCanContinueCounts[t]++;
					subPass = result.isPass();
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
					failCounts[t]++;
				
				filterResults.get(t).add(result);
				if (scalarVals.get(t) != null)
					scalarVals.get(t).add(scalars[t]);
			}
			if (allPass)
				allPassCount++;
			if (onlyFailureIndex >= 0) {
//				fullResults.onlyFailIndexes.get(onlyFailureIndex).add(rupIndex);
				onlyFailCounts[onlyFailureIndex]++;
				singleFailures.add(true);
			} else {
				singleFailures.add(false);
			}
		}
	}
	
	private static Color[] FILTER_COLORS = { Color.DARK_GRAY, new Color(102, 51, 0), Color.RED, Color.BLUE,
//			Color.GREEN.darker(), Color.CYAN, Color.PINK, Color.ORANGE.darker(), Color.MAGENTA };
			Color.GREEN.darker(), Color.CYAN, Color.PINK };
	
	public static File plotRupSetPlausibility(RupSetPlausibilityResult result, File outputDir,
			String prefix, String title) throws IOException {
		double dx = 1d;
		double buffer = 0.2*dx;
		double deltaEachSide = (dx - buffer)/2d;
		double maxY = 50;
		for (int i=0; i<result.filters.size(); i++) {
			int failCount = result.erredCounts[i]+result.failCounts[i];
			double percent = (100d)*failCount/(double)result.numRuptures;
			while (percent > maxY - 25d)
				maxY += 10;
		}

		Font font = new Font(Font.SANS_SERIF, Font.BOLD, 20);
		Font allFont = new Font(Font.SANS_SERIF, Font.BOLD, 26);
		
		double topRowY = maxY*0.95;
		double secondRowY = maxY*0.91;
		double thirdRowY = maxY*0.85;
		
		int numPlots = 1 + (result.filters.size() / FILTER_COLORS.length);
		int filtersEach = (int)Math.ceil((double)result.filters.size()/(double)numPlots);
		System.out.println("Have "+result.filters.size()+" filters. Will do "+numPlots+" plots with "+filtersEach+" each");
		Preconditions.checkState(filtersEach <= FILTER_COLORS.length && filtersEach > 0);
		
		List<PlotSpec> specs = new ArrayList<>();
		
		Range xRange = new Range(-0.30*dx, filtersEach*dx + 0.15*dx);
		
		List<Range> yRanges = new ArrayList<>();
		
		for (int p=0; p<numPlots; p++) {
			List<PlotElement> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			funcs.add(new DefaultXY_DataSet(new double[] {0d, 1d}, new double[] {0d, 0d}));
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 0f, Color.WHITE));
			
			List<XYAnnotation> anns = new ArrayList<>();
			
			for (int i=p*filtersEach; i<result.filters.size() && i<((p+1)*filtersEach); i++) {
				int relIndex = i % filtersEach;
				double x = relIndex*dx + 0.5*dx;
				double percentFailed = 100d*result.failCounts[i]/result.numRuptures;
				double percentOnly = 100d*result.onlyFailCounts[i]/result.numRuptures;
				double percentErred = 100d*result.erredCounts[i]/result.numRuptures;
				
				Color c = FILTER_COLORS[relIndex];
				
				String name = result.filters.get(i).getShortName();
				
				if (percentErred > 0) {
//					funcs.add(vertLine(x, percentFailed, percentFailed + percentErred));
//					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, thickness, Color.LIGHT_GRAY));
					anns.add(emptyBox(x-deltaEachSide, 0d, x+deltaEachSide, percentFailed + percentErred,
							PlotLineType.DASHED, Color.LIGHT_GRAY, 2f));
					name += "*";
				}
				
//				funcs.add(vertLine(x, 0, percentFailed));
//				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, thickness, c));
				anns.add(filledBox(x-deltaEachSide, 0, x+deltaEachSide, percentFailed, c));
				
				if (percentOnly > 0) {
//					funcs.add(vertLine(x, 0, percentOnly));
//					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, thickness, darker(c)));
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
			
			if (p == 0) {
				String passedStr = result.allPassCount+"/"+result.numRuptures+" = "
						+percentDF.format((double)result.allPassCount/result.numRuptures)+" passed all";
				XYTextAnnotation ann = new XYTextAnnotation(passedStr, xRange.getCentralValue(), topRowY);
				ann.setTextAnchor(TextAnchor.CENTER);
				ann.setPaint(Color.BLACK);
				ann.setFont(allFont);
				
				anns.add(ann);
			}
			
			PlotSpec spec = new PlotSpec(funcs, chars, p == 0 ? title : "", " ", "Percent Failed");
			spec.setPlotAnnotations(anns);
			
			specs.add(spec);
			yRanges.add(new Range(0, maxY));
		}
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		
		List<Range> xRanges = new ArrayList<>();
		xRanges.add(xRange);
		
		gp.drawGraphPanel(specs, false, false, xRanges, yRanges);
		gp.getXAxis().setTickLabelsVisible(false);
		gp.getChartPanel().setSize(1200, 150 + 450*numPlots);
		File pngFile = new File(outputDir, prefix+".png");
		File pdfFile = new File(outputDir, prefix+".pdf");
		File txtFile = new File(outputDir, prefix+".txt");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsPDF(pdfFile.getAbsolutePath());
		gp.saveAsTXT(txtFile.getAbsolutePath());
		return pngFile;
	}
	
	private static double[] plausibility_min_mags = { 6.5d, 7d, 7.5d, 8d };
	
	private static TableBuilder getMagPlausibilityTable(FaultSystemRupSet rupSet, RupSetPlausibilityResult fullResult,
			File resourcesDir, String relPathToResources, String prefix) throws IOException {
		TableBuilder table = MarkdownUtils.tableBuilder().initNewLine();
		
		for (double minMag : plausibility_min_mags) {
			RupSetPlausibilityResult result = fullResult.filterByMag(rupSet, minMag);
			if (result == null)
				continue;
			String magPrefix = prefix+"_m"+(float)minMag;
			String title = "M≥"+(float)minMag+" Comparison";
			File file = plotRupSetPlausibility(result, resourcesDir, magPrefix, title);
			table.addColumn("![M>="+(float)minMag+"]("+relPathToResources+"/"+file.getName()+")");
		}
		table.finalizeLine();
		
		return table;
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
	
	public static TableBuilder getRupSetPlausibilityTable(RupSetPlausibilityResult result, FaultSystemSolution sol, String linkHeading) {
		linkHeading = linkHeading.replaceAll("#", "").trim();
		TableBuilder table = MarkdownUtils.tableBuilder();
		boolean hasRates = sol != null && sol.getRupSet().getNumRuptures() == result.numRuptures;
		if (hasRates)
			table.addLine("Filter", "Failed", "Failure Rate", "Only Failure", "Erred");
		else
			table.addLine("Filter", "Failed", "Only Failure", "Erred");
		for (int t=0; t<result.filters.size(); t++) {
			table.initNewLine();
			String name = result.filters.get(t).getName();
			if (linkHeading != null && (result.failCounts[t] > 0 || result.erredCounts[t] > 0))
				table.addColumn("**["+name+"](#"+MarkdownUtils.getAnchorName(linkHeading+" "+name)+")**");
			else
				table.addColumn("**"+name+"**");
			table.addColumn(countStats(result.failCounts[t], result.numRuptures));
			if (hasRates) {
				FaultSystemRupSet rupSet = sol.getRupSet();
				double totRate = sol.getTotalRateForAllFaultSystemRups();
				double failRate = 0d;
				for (int r=0; r<result.numRuptures; r++) {
					PlausibilityResult filterResult = result.filterResults.get(t).get(r);
					if (filterResult != null && !filterResult.isPass())
						failRate += sol.getRateForRup(r);
				}
				table.addColumn((float)failRate+" ("+percentDF.format(failRate/totRate)+")");
			}
			table.addColumn(countStats(result.onlyFailCounts[t], result.numRuptures));
			table.addColumn(countStats(result.erredCounts[t], result.numRuptures));
			table.finalizeLine();
		}
		// add total
		table.initNewLine();
		table.addColumn("**Combined**");
		int numFails = 0;
		int numErrs = 0;
		double failRate = 0d;
		
		for (int r=0; r<result.numRuptures; r++) {
			boolean fails = false;
			boolean errs = false;
			for (List<PlausibilityResult> filterResults : result.filterResults) {
				PlausibilityResult filterResult = filterResults.get(r);
				if (filterResult == null)
					errs = true;
				else if (!filterResult.isPass())
					fails = true;
			}
			if (fails)
				numFails++;
			if (errs)
				numErrs++;
			if (fails && hasRates)
				failRate += sol.getRateForRup(r);
		}
		table.addColumn(countStats(numFails, result.numRuptures));
		if (hasRates)
			table.addColumn((float)failRate+" ("+percentDF.format(failRate/sol.getTotalRateForAllFaultSystemRups())+")");
		table.addColumn("*N/A*");
		table.addColumn(countStats(numErrs, result.numRuptures));
		table.finalizeLine();
		return table;
	}
	
	public static List<String> getRupSetPlausibilityDetailLines(RupSetPlausibilityResult result, boolean compRups,
			FaultSystemRupSet rupSet, List<ClusterRupture> rups, int maxNumToPlot, File resourcesDir, String relPathToResources,
			String heading, String topLink, RuptureConnectionSearch connSearch, List<HistScalarValues> scalarVals)
					throws IOException {
		List<String> lines = new ArrayList<>();
		
		File rupHtmlDir = new File(resourcesDir.getParentFile(), "rupture_pages");
		Preconditions.checkState(rupHtmlDir.exists() || rupHtmlDir.mkdir());
		
		for (int i = 0; i < result.filters.size(); i++) {
			if (result.failCounts[i] == 0 && result.erredCounts[i] == 0)
				continue;
			PlausibilityFilter filter = result.filters.get(i);
			
			lines.add(heading+" "+filter.getName());
			lines.add(topLink); lines.add("");
			
			String filterPrefix = filter.getShortName().replaceAll("\\W+", "");
			if (compRups)
				filterPrefix += "_compRups";
			Color color = compRups ? COMP_COLOR : MAIN_COLOR;
			
			HashSet<Integer> failIndexes = new HashSet<>();
			HashSet<Integer> errIndexes = new HashSet<>();
			
			for (int r=0; r<result.numRuptures; r++) {
				PlausibilityResult res = result.filterResults.get(i).get(r);
				if (res == null)
					errIndexes.add(r);
				else if (!res.isPass())
					failIndexes.add(r);
			}
			
			HashSet<Integer> combIndexes = new HashSet<>(failIndexes);
			combIndexes.addAll(errIndexes);
			
			if (scalarVals != null) {
				TableBuilder table = MarkdownUtils.tableBuilder();
				table.initNewLine();
				for (HistScalarValues vals : scalarVals) {
					HistScalar scalar = vals.getScalar();
					String prefix = "filter_hist_"+filterPrefix+"_"+scalar.name();
					File plot = RupHistogramPlots.plotRuptureHistogram(resourcesDir, prefix, vals, combIndexes,
							null, null, color, false, false);
					table.addColumn("!["+scalar.getName()+"]("+relPathToResources+"/"+plot.getName()+")");
				}
				table.finalizeLine();
				lines.add("**Distributions of ruptures that failed ("+countDF.format(result.failCounts[i])+") or erred ("
						+countDF.format(result.erredCounts[i])+"):**");
				lines.add("");
				lines.addAll(table.wrap(5, 0).build());
				lines.add("");
			}
			
			if (result.failCounts[i] > 0) {
				lines.add("This filter has "+result.failCounts[i]+" failures, of which "+result.onlyFailCounts[i]
						+" are unique, i.e., fail this filter but no other filters. The table below shows how the "
						+ "non-unique failues overlap with other filters.");
				lines.add("");
				TableBuilder table = MarkdownUtils.tableBuilder();
				table.addLine("Filter", "Failures In Common", "% of My Failures",
						"% Of All Failures", "% Of All Ruptures");
				List<PlausibilityResult> myFilterResults = result.filterResults.get(i);
				for (int j=0; j<result.filters.size(); j++) {
					if (i == j || result.failCounts[j] == 0)
						continue;
					table.initNewLine();
					String oName = result.filters.get(j).getName();
					table.addColumn("**"+"["+oName+"](#"+MarkdownUtils.getAnchorName(heading+" "+oName)+")**");
					int numCommon = 0;
					List<PlausibilityResult> altFilterResults = result.filterResults.get(j);
					for (int r=0; r<result.numRuptures; r++) {
						PlausibilityResult myResult = myFilterResults.get(r);
						PlausibilityResult altResult = altFilterResults.get(r);
						if (myResult != null && !myResult.isPass() && altResult != null && !altResult.isPass())
							numCommon++;
					}
					table.addColumn(countDF.format(numCommon));
					table.addColumn(percentDF.format((double)numCommon/(double)result.failCounts[i]));
					table.addColumn(percentDF.format((double)numCommon/(double)(result.numRuptures-result.allPassCount)));
					table.addColumn(percentDF.format((double)numCommon/(double)result.numRuptures));
					table.finalizeLine();
					
				}
				lines.addAll(table.build());
				lines.add("");
			}
			
			if (result.failCounts[i] > 0 && filter instanceof ScalarValuePlausibiltyFilter<?>) {
				System.out.println(filter.getName()+" is scalar");
				ScalarValuePlausibiltyFilter<?> scaleFilter = (ScalarValuePlausibiltyFilter<?>)filter;
				com.google.common.collect.Range<?> range = scaleFilter.getAcceptableRange();
				Double lower = null;
				Double upper = null;
				if (range != null) {
					if (range.hasLowerBound())
						lower = ((Number)range.lowerEndpoint()).doubleValue();
					if (range.hasUpperBound())
						upper = ((Number)range.upperEndpoint()).doubleValue();
				}
				TableBuilder table = MarkdownUtils.tableBuilder();
				table.addLine("Fail Scalar Distribution", "Pass Scalar Distribution");
				table.initNewLine();
				for (boolean passes : new boolean[] { false, true }) {
					MinMaxAveTracker track = new MinMaxAveTracker();
					List<Double> scalars = new ArrayList<>();
					
					for (int r=0; r<result.numRuptures; r++) {
						PlausibilityResult res = result.filterResults.get(i).get(r);
						if (res == null || res.isPass() != passes)
							continue;
						Double scalar = result.scalarVals.get(i).get(r);
//						if (filter instanceof ClusterPathCoulombCompatibilityFilter && passes
//								&& scalar != null && scalar < 0d) {
//							System.out.println("Debugging weirdness...");
//							ClusterRupture rupture = rups.get(r);
//							System.out.println("Rupture: "+rupture);
//							System.out.println("Stored result: "+res);
//							System.out.println("Calculating now...");
//							System.out.println("Result: "+filter.apply(rupture, true));
//							System.out.println("Multi Result: "+new MultiDirectionalPlausibilityFilter.Scalar<>(
//									(ClusterPathCoulombCompatibilityFilter)filter, connSearch, false).apply(rupture, true));
//							System.out.println("Stored scalar: "+scalar);
//							System.out.println("Now scalar: "
//									+((ClusterPathCoulombCompatibilityFilter)filter).getValue(rupture));
//							System.exit(0);
//						}
//						ClusterRupture rupture = rups.get(r);
//						
//						// TODO
//						Number scalar = scaleFilter.getValue(rupture);
//						boolean D = passes && scalar != null && scalar.doubleValue() < 0
//								&& filter instanceof ClusterPathCoulombCompatibilityFilter;
//						if (D) System.out.println("DEBUG: initial scalar: "+scalar+", result="+res);
//						if (filter.isDirectional(!rupture.splays.isEmpty()) && range != null) {
//							// see if there's a better version available
//							for (ClusterRupture alt : rupture.getInversions(connSearch)) {
//								Number altScalar = scaleFilter.getValue(alt);
//								boolean better = isScalarBetter(lower, upper, altScalar, scalar);
//								if (D) System.out.println("\tDEBUG: alt scalar: "+altScalar+", better ? "+better);
//								if (altScalar != null && better)
//									// this one is better
//									scalar = altScalar;
//							}
//						}
//						if (D) System.out.println("\tDEBUG: final scalar: "+scalar);
						if (scalar != null && Double.isFinite(scalar.doubleValue())) {
							scalars.add(scalar);
							track.addValue(scalar);
						}
					}
					
					System.out.println("Have "+scalars.size()+" scalars, passes="+passes);
					if (scalars.size() > 0) {
						DiscretizedFunc hist;
						boolean xLog;
						boolean xNegFlip;
						Range xRange;
						String xAxisLabel;
						if (scaleFilter.getScalarName() == null) {
							xAxisLabel = "Scalar Value";
						} else {
							xAxisLabel = scaleFilter.getScalarName();
							if (scaleFilter.getScalarUnits() != null)
								xAxisLabel += " ("+scaleFilter.getScalarUnits()+")";
						}
						System.out.println("tracker: "+track);
						ScalarCoulombPlausibilityFilter coulombFilter = null;
						if (filter instanceof ScalarCoulombPlausibilityFilter) {
							coulombFilter = (ScalarCoulombPlausibilityFilter)filter;
							AggregatedStiffnessCalculator aggCalc = coulombFilter.getAggregator();
							if (!aggCalc.hasUnits())
								coulombFilter = null;
						}
						
						if (coulombFilter != null && lower != null && lower.floatValue() <= 0f
								&& track.getMax() < 0d) {
							// do it in log spacing, negative
							double logMinNeg = Math.log10(-track.getMax());
							double logMaxNeg = Math.log10(-track.getMin());
							System.out.println("Flipping with track: "+track);
							System.out.println("\tlogMinNeg="+logMinNeg);
							System.out.println("\tlogMaxNeg="+logMaxNeg);
							if (logMinNeg < -8)
								logMinNeg = -8;
							else
								logMinNeg = Math.floor(logMinNeg);
							if (logMaxNeg < -1)
								logMaxNeg = -1;
							else
								logMaxNeg = Math.ceil(logMaxNeg);
							System.out.println("\tlogMinNeg="+logMinNeg);
							System.out.println("\tlogMaxNeg="+logMaxNeg);
							HistogramFunction logHist = HistogramFunction.getEncompassingHistogram(
									logMinNeg, logMaxNeg, 0.1);
							for (double scalar : scalars) {
								scalar = Math.log10(-scalar);
								logHist.add(logHist.getClosestXIndex(scalar), 1d);
							}
							hist = new ArbitrarilyDiscretizedFunc();
							for (Point2D pt : logHist)
								hist.set(Math.pow(10, pt.getX()), pt.getY());
							xLog = true;
							xNegFlip = false;
							xRange = new Range(Math.pow(10, logHist.getMinX()-0.5*logHist.getDelta()),
									Math.pow(10, logHist.getMaxX()+0.5*logHist.getDelta()));
							xAxisLabel = "-"+xAxisLabel;
						} else if (coulombFilter != null && lower != null && lower.floatValue() >= 0f
								&& track.getMin() > 0d) {
							// do it in log spacing
							double logMin = Math.log10(track.getMin());
							double logMax = Math.log10(track.getMax());
							if (logMin < -8)
								logMin = -8;
							else
								logMin = Math.floor(logMin);
							if (logMax < -1)
								logMax = -1;
							else
								logMax = Math.ceil(logMax);
							System.out.println("\tlogMin="+logMin);
							System.out.println("\tlogMax="+logMax);
							HistogramFunction logHist = HistogramFunction.getEncompassingHistogram(
									logMin, logMax, 0.1);
							for (double scalar : scalars) {
								scalar = Math.log10(scalar);
								logHist.add(logHist.getClosestXIndex(scalar), 1d);
							}
							hist = new ArbitrarilyDiscretizedFunc();
							for (Point2D pt : logHist)
								hist.set(Math.pow(10, pt.getX()), pt.getY());
							xLog = true;
							xNegFlip = false;
							xRange = new Range(Math.pow(10, logHist.getMinX()-0.5*logHist.getDelta()),
									Math.pow(10, logHist.getMaxX()+0.5*logHist.getDelta()));
						} else {
							double len = track.getMax() - track.getMin();
							double delta;
							if (len > 1000)
								delta = 50;
							else if (len > 100)
								delta = 10;
							else if (len > 50)
								delta = 5;
							else if (len > 10)
								delta = 2;
							else if (len > 5)
								delta = 1;
							else if (len > 2)
								delta = 0.5;
							else if (len > 1)
								delta = 0.1;
							else if (len > 0.5)
								delta = 0.05;
							else if (len > 0.1)
								delta = 0.01;
							else
								delta = len/10d;
							
							double min = track.getMin();
							double max = track.getMax();
							if (min == max) {
								HistogramFunction myHist = new HistogramFunction(min, max, 1);
								myHist.set(0, scalars.size());
								hist = myHist;
								xLog = false;
								xNegFlip = false;
								xRange = new Range(min-0.5, max+0.5);
							} else {
								HistogramFunction myHist = HistogramFunction.getEncompassingHistogram(
										track.getMin(), track.getMax(), delta);
								for (double scalar : scalars)
									myHist.add(myHist.getClosestXIndex(scalar), 1d);
								hist = myHist;
								xLog = false;
								xNegFlip = false;
								xRange = new Range(myHist.getMinX()-0.5*myHist.getDelta(),
										myHist.getMaxX()+0.5*myHist.getDelta());
							}
							
						}
						
						List<DiscretizedFunc> funcs = new ArrayList<>();
						List<PlotCurveCharacterstics> chars = new ArrayList<>();
						
						funcs.add(hist);
						chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, color));
						
						String title = filter.getName();
						if (passes)
							title += " Passes";
						else
							title += " Failures";
						PlotSpec spec = new PlotSpec(funcs, chars, title, xAxisLabel, "Count");
						
						HeadlessGraphPanel gp = new HeadlessGraphPanel();
						gp.setTickLabelFontSize(18);
						gp.setAxisLabelFontSize(24);
						gp.setPlotLabelFontSize(24);
						gp.setBackgroundColor(Color.WHITE);
						
						gp.drawGraphPanel(spec, xLog, false, xRange, null);
						gp.getPlot().getDomainAxis().setInverted(xNegFlip);

						String prefix = "filter_hist_"+filterPrefix;
						if (passes)
							prefix += "_passed_scalars";
						else
							prefix += "_failed_scalars";
						File file = new File(resourcesDir, prefix);
						gp.getChartPanel().setSize(800, 600);
						gp.saveAsPNG(file.getAbsolutePath()+".png");
						gp.saveAsPDF(file.getAbsolutePath()+".pdf");
						
						table.addColumn("![hist]("+relPathToResources+"/"+file.getName()+".png)");
					} else {
						table.addColumn("*N/A*");
					}
				}
				table.finalizeLine();
				
				lines.add("**Scalar values of ruptures**");
				lines.add("");
				lines.addAll(table.build());
				lines.add("");
			}
			
			// now add examples
			for (boolean err : new boolean[] {false, true}) {
				if (!err && result.failCounts[i] == 0 ||
						err && result.erredCounts[i] == 0)
					continue;
				int shortestIndex = -1;
				int shortestNumSects = Integer.MAX_VALUE;
				int longestIndex = -1;
				int longestNumClusters = 0;
				HashSet<Integer> plotIndexes = err ? errIndexes : failIndexes;
				for (int index : plotIndexes) {
					ClusterRupture rup = rups.get(index);
					int sects = rup.getTotalNumSects();
					int clusters = rup.getTotalNumClusters();
					if (sects < shortestNumSects) {
						shortestIndex = index;
						shortestNumSects = sects;
					}
					if (clusters > longestNumClusters) {
						longestIndex = index;
						longestNumClusters = clusters;
					}
				}
				plotIndexes.remove(shortestIndex);
				plotIndexes.remove(longestIndex);
				List<Integer> plotSorted = new ArrayList<>(plotIndexes);
				Collections.sort(plotSorted);
				Collections.shuffle(plotSorted, new Random(plotIndexes.size()));
				if (plotSorted.size() > maxNumToPlot-2)
					plotSorted = plotSorted.subList(0, maxNumToPlot-2);
				for (int r=plotSorted.size(); --r>=0;) {
					int index = plotSorted.get(r);
					if (index == shortestIndex || index == longestIndex)
						plotSorted.remove(r);
				}
				plotSorted.add(0, shortestIndex);
				if (longestIndex != shortestIndex)
					plotSorted.add(longestIndex);
				
				// plot them
				TableBuilder table = MarkdownUtils.tableBuilder();
				table.initNewLine();
				String prefix = "filter_examples_"+filterPrefix;
				boolean plotAzimuths = filter.getName().toLowerCase().contains("azimuth");
				for (int rupIndex : plotSorted) {
					String rupPrefix = prefix+"_"+rupIndex;
//					connSearch.plotConnections(resourcesDir, prefix, rupIndex);
					RupCartoonGenerator.plotRupture(resourcesDir, rupPrefix, rups.get(rupIndex),
							"Rupture "+rupIndex, plotAzimuths, true);
					table.addColumn("[<img src=\"" + resourcesDir.getName() + "/" + rupPrefix + ".png\" />]"+
							"("+ RupHistogramPlots.generateRuptureInfoPage(rupSet, rups.get(rupIndex), rupIndex, rupHtmlDir,
									rupPrefix, result, connSearch.getDistAzCalc())+ ")");
				}
				table.finalizeLine();
				if (err)
					lines.add("Example ruptures which erred:");
				else
					lines.add("Example ruptures which failed:");
				lines.add("");
				lines.addAll(table.wrap(5, 0).build());
				lines.add("");
			}
		}
		
		return lines;
	}
	
	private static final DecimalFormat percentDF = new DecimalFormat("0.00%");
	private static String countStats(int count, int tot) {
		return countDF.format(count)+"/"+countDF.format(tot)+" ("+percentDF.format((double)count/(double)tot)+")";
	}

}
