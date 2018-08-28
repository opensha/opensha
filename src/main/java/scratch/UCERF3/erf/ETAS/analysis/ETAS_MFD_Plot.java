package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.chart.plot.DatasetRenderingOrder;
import org.opensha.commons.calc.FractileCurveCalculator;
import org.opensha.commons.data.function.AbstractXY_DataSet;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.UncertainArbDiscDataset;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

public class ETAS_MFD_Plot extends ETAS_AbstractPlot {
	
	static double mfdMinMag = 2.55;
	static double mfdDelta = 0.1;
	static int mfdNumMag = 66;
	private static double mfdMinY = 1e-4;
	private static double mfdMaxY = 1e4;
	
	private String prefix;
	private boolean annualize;
	private boolean cumulative;
	
	private int numCatalogs = 0;
	
	private MFD_Stats totalWithSpontStats;
	private MFD_Stats triggeredStats;
	private MFD_Stats triggeredPrimaryStats;
	
	private HistogramFunction totalCountHist;
	
	private boolean spontaneousFound = false;
	
	private static double[] fractiles = {0.025, 0.975};
	
	public ETAS_MFD_Plot(ETAS_Config config, ETAS_Launcher launcher, String prefix, boolean annualize, boolean cumulative) {
		super(config, launcher);
		this.prefix = prefix;
		this.annualize = annualize;
		this.cumulative = cumulative;
		
		boolean triggerCatAsSpont = config.getTriggerCatalogFile() != null && config.isTreatTriggerCatalogAsSpontaneous();
		
		if (config.isIncludeSpontaneous() || triggerCatAsSpont) {
			// we have spontaneous ruptures
			totalWithSpontStats = new MFD_Stats();
			if (triggerCatAsSpont)
				spontaneousFound = true;
		}
		if (config.hasTriggers()) {
			// we have input ruptures
			triggeredStats = new MFD_Stats();
			triggeredPrimaryStats = new MFD_Stats();
		}
		Preconditions.checkState(totalWithSpontStats != null || triggeredStats != null, "Must either have spontaneous, or trigger ruptures");
		
		totalCountHist = new HistogramFunction(mfdMinMag, mfdNumMag, mfdDelta);
	}

	@Override
	public boolean isFilterSpontaneous() {
		// filter spontaneous if we have trigger ruptures
		return triggeredStats != null;
	}

	@Override
	protected void doProcessCatalog(List<ETAS_EqkRupture> completeCatalog, List<ETAS_EqkRupture> triggeredOnlyCatalog, FaultSystemSolution fss) {
		if (totalWithSpontStats != null) {
			IncrementalMagFreqDist totalHist = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDelta);
			for (ETAS_EqkRupture rup : completeCatalog) {
				int xIndex = totalHist.getClosestXIndex(rup.getMag());
				totalHist.add(xIndex, 1d);
				// this is used to find the modal magnitude, which is used to trim plots for magnitude filtered catalogs
				totalCountHist.add(xIndex, 1d);
				spontaneousFound = spontaneousFound || rup.getGeneration() == 0;
			}
			if (cumulative)
				totalWithSpontStats.addHistogram(totalHist.getCumRateDistWithOffset());
			else
				totalWithSpontStats.addHistogram(totalHist);
		}
		if (triggeredStats != null) {
			IncrementalMagFreqDist noSpontHist = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDelta);
			IncrementalMagFreqDist primaryHist = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDelta);
			if (triggeredOnlyCatalog != null) {
				for (ETAS_EqkRupture rup : triggeredOnlyCatalog) {
					int xIndex = noSpontHist.getClosestXIndex(rup.getMag());
					noSpontHist.add(xIndex, 1d);
					if (totalWithSpontStats == null)
						// do it here
						totalCountHist.add(xIndex, 1d);
					if (rup.getGeneration() == 1)
						primaryHist.add(xIndex, 1d);
				}
			}
			if (cumulative) {
				triggeredStats.addHistogram(noSpontHist.getCumRateDistWithOffset());
				triggeredPrimaryStats.addHistogram(primaryHist.getCumRateDistWithOffset());
			} else {
				triggeredStats.addHistogram(noSpontHist);
				triggeredPrimaryStats.addHistogram(primaryHist);
			}
		}
		
		numCatalogs++;
	}
	
	private String getPlotTitle() {
		if (annualize)
			return "Magnitude Frequency Distribution";
		else
			return "Magnitude Number Distribution";
	}

	@Override
	public void finalize(File outputDir, FaultSystemSolution fss) throws IOException {
		int numToTrim = calcNumToTrim();
		
		String title = getPlotTitle();
		if (totalWithSpontStats != null && spontaneousFound) {
			String myTitle = title;
			if (triggeredStats != null)
				// this is has both spontaneous and trigger
				myTitle = title+", Including Spontaneous";
			
			plot(outputDir, prefix, myTitle, totalWithSpontStats, null, numToTrim, fss);
		}
		if (triggeredStats != null) {
			title += ", Triggered Events";
			
			plot(outputDir, prefix+"_triggered", title, triggeredStats, triggeredPrimaryStats, numToTrim, fss);
		}
	}

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink) throws IOException {
		int numToTrim = calcNumToTrim();
		
		List<String> lines = new ArrayList<>();
		
		String title = getPlotTitle();
		
		lines.add(topLevelHeading+" "+title);
		lines.add(topLink); lines.add("");
		
		if (totalWithSpontStats != null && spontaneousFound) {
			String myTitle = title;
			if (triggeredStats != null) {
				// this is has both spontaneous and trigger
				myTitle = title+", Including Spontaneous";
				
				lines.add(topLevelHeading+"# "+myTitle);
				lines.add(topLink); lines.add("");
				
				lines.add("*Note: This section includes both spontaneous and triggered events*");
				lines.add("");
			}
			
			lines.addAll(buildLegend());
			lines.add("");
			
			lines.add("![MFD Plot]("+relativePathToOutputDir+"/"+prefix+".png)");
			lines.add("");
			
			lines.addAll(buildTable(totalWithSpontStats, null, numToTrim));
			lines.add("");
		}
		if (triggeredStats != null) {
			title += ", Triggered Events";
			
			if (totalWithSpontStats != null) {
				lines.add(topLevelHeading+"# "+title);
				lines.add(topLink); lines.add("");
				
				lines.add("*Note: This section only includes triggered events, spontaneous were calculated but filtered out here*");
				lines.add("");
			}
			
			lines.addAll(buildLegend());
			lines.add("");
			
			lines.add("![MFD Plot]("+relativePathToOutputDir+"/"+prefix+"_triggered.png)");
			lines.add("");
			
			lines.addAll(buildTable(triggeredStats, triggeredPrimaryStats, numToTrim));
			lines.add("");
		}
		
		return lines;
	}
	
	private static String getFractilesString() {
		List<String> percents = new ArrayList<>();
		for (double fractile : fractiles)
			percents.add(optionalDigitDF.format(fractile*100)+"%");
		return Joiner.on(",").join(percents);
	}
	
	private void plot(File outputDir, String prefix, String title, MFD_Stats stats, MFD_Stats primaryStats,
			int numToTrim, FaultSystemSolution fss) throws IOException {
		stats.calcStats();
		
		EvenlyDiscretizedFunc meanFunc = stats.getMeanFunc(numToTrim);
		EvenlyDiscretizedFunc medianFunc = stats.getMedianFunc(numToTrim);
		EvenlyDiscretizedFunc modeFunc = stats.getModeFunc(numToTrim);
		EvenlyDiscretizedFunc probFunc = stats.getProbFunc(numToTrim);
		EvenlyDiscretizedFunc[] fractileFuncs = stats.getFractileFuncs(numToTrim);
		
		meanFunc.setName("Mean");
		modeFunc.setName("Mode");
		medianFunc.setName("Median");
		probFunc.setName(getTimeShortLabel(getConfig().getDuration())+" Prob");
		if (fractileFuncs.length > 0) {
			fractileFuncs[0].setName(getFractilesString());
			for (int i=1; i<fractileFuncs.length; i++)
				fractileFuncs[i].setName(null);
		}
		
		List<DiscretizedFunc> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		funcs.add(meanFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
		
		for (EvenlyDiscretizedFunc func : fractileFuncs) {
			funcs.add(func);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		}
		
		funcs.add(medianFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		
		funcs.add(modeFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.CYAN.darker()));
		
		if (probFunc != null) {
			funcs.add(probFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.RED));
			
			EvenlyDiscretizedFunc probLower = new EvenlyDiscretizedFunc(probFunc.getMinX(), probFunc.getMaxX(), probFunc.size());
			EvenlyDiscretizedFunc probUpper = new EvenlyDiscretizedFunc(probFunc.getMinX(), probFunc.getMaxX(), probFunc.size());
			double fssMaxMag = fss.getRupSet().getMaxMag();
			for (int i=0; i<probFunc.size(); i++) {
				double fract = probFunc.getY(i);
				double x = probFunc.getX(i);
				boolean aboveMax = x - 0.5*mfdDelta > fssMaxMag;
				if (aboveMax)
					Preconditions.checkState(fract == 0);
				
				if (fract >= 1d || aboveMax) {
					probLower.set(x, fract);
					probUpper.set(x, fract);
				} else {
					double[] conf = ETAS_Utils.getBinomialProportion95confidenceInterval(fract, numCatalogs);
					probLower.set(i, conf[0]);
					probUpper.set(i, conf[1]);
				}
			}
			UncertainArbDiscDataset confFunc = new UncertainArbDiscDataset(probFunc, probLower, probUpper);
			confFunc.setName("95% Conf");
			funcs.add(confFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, new Color(255, 0, 0, 30)));
		}
		
		if (primaryStats != null) {
			primaryStats.calcStats();
			EvenlyDiscretizedFunc primaryFunc = primaryStats.getMeanFunc(numToTrim);
			primaryFunc.setName("Primary");
			
			funcs.add(primaryFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GREEN.darker()));
		}
		
		String yAxisLabel;
		
		if (cumulative)
			yAxisLabel = "Cumulative ";
		else
			yAxisLabel = "Incremental ";
		if (annualize)
			yAxisLabel += "Annual Rate";
		else
			yAxisLabel = getTimeLabel(getConfig().getDuration(), false)+" "+yAxisLabel+"Number";
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Magnitude", yAxisLabel);
		spec.setLegendVisible(true);
		
		double mfdMinY = ETAS_MFD_Plot.mfdMinY;
		if (mfdMinY > 1d/(double)numCatalogs)
			mfdMinY = Math.pow(10, Math.floor(Math.log10(1d/numCatalogs)));
		
		HeadlessGraphPanel gp = buildGraphPanel();
		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
		gp.setUserBounds(meanFunc.getMinX(), meanFunc.getMaxX(), mfdMinY, mfdMaxY);

		gp.drawGraphPanel(spec, false, true);
		gp.getChartPanel().setSize(800, 600);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix+".txt").getAbsolutePath());
	}
	
	private class MFD_Stats {
		
		private XY_DataSetList histList;
		private List<Double> relativeWeights;
		
		private EvenlyDiscretizedFunc meanFunc;
		private EvenlyDiscretizedFunc probFunc;
		private EvenlyDiscretizedFunc medianFunc;
		private EvenlyDiscretizedFunc modeFunc;
		private EvenlyDiscretizedFunc[] fractileFuncs;
		
		public MFD_Stats() {
			this.histList = new XY_DataSetList();
			this.relativeWeights = new ArrayList<>();
		}
		
		public synchronized void addHistogram(EvenlyDiscretizedFunc func) {
			histList.add(func);
			relativeWeights.add(1d);
			
			if (meanFunc != null) {
				meanFunc = null;
				probFunc = null;
				medianFunc = null;
				modeFunc = null;
				fractileFuncs = null;
			}
		}
		
		public EvenlyDiscretizedFunc[] getFractileFuncs(int numToTrim) {
			if (numToTrim == 0)
				return fractileFuncs;
			EvenlyDiscretizedFunc[] trimmed = new EvenlyDiscretizedFunc[fractileFuncs.length];
			for (int i=0; i<trimmed.length; i++)
				trimmed[i] = trimFunc(fractileFuncs[i], numToTrim);
			return trimmed;
		}

		public EvenlyDiscretizedFunc getMeanFunc(int numToTrim) {
			return trimFunc(meanFunc, numToTrim);
		}

		public EvenlyDiscretizedFunc getProbFunc(int numToTrim) {
			return trimFunc(probFunc, numToTrim);
		}

		public EvenlyDiscretizedFunc getMedianFunc(int numToTrim) {
			return trimFunc(medianFunc, numToTrim);
		}

		public EvenlyDiscretizedFunc getModeFunc(int numToTrim) {
			return trimFunc(modeFunc, numToTrim);
		}

		public synchronized void calcStats() {
			if (meanFunc != null)
				// already calculated
				return;
			Preconditions.checkState(!histList.isEmpty());
			EvenlyDiscretizedFunc func0 = (EvenlyDiscretizedFunc) histList.get(0);
			double minMag = func0.getMinX();
			int numMag = func0.size();
			double deltaMag = func0.getDelta();
			meanFunc = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			probFunc = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			medianFunc = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			modeFunc = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			fractileFuncs = new EvenlyDiscretizedFunc[fractiles.length];
			for (int i=0; i<fractiles.length; i++)
				fractileFuncs[i] = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			
			// calculate mean and fractiles
			FractileCurveCalculator fractileCalc = new FractileCurveCalculator(histList, relativeWeights);
			
			AbstractXY_DataSet meanCurve = fractileCalc.getMeanCurve();
			Map<Double, AbstractXY_DataSet> fractileCurves = null;
			fractileCurves = new HashMap<>();
			for (Double fractile : fractiles)
				fractileCurves.put(fractile, fractileCalc.getFractile(fractile));
			fractileCurves.put(0.5, fractileCalc.getFractile(0.5));
			
			// populate input functions
			double rateScalar = 1d;
			if (annualize)
				rateScalar = 1d/getConfig().getDuration();
			for (int i=0; i<meanCurve.size(); i++) {
				meanFunc.set(i, meanCurve.getY(i)*rateScalar);
				
				ArbDiscrEmpiricalDistFunc dist = fractileCalc.getEmpiricalDist(i);
				double mode;
				if (dist.size() == 1)
					mode = dist.getX(0);
				else
					mode = dist.getMostCentralMode();
				modeFunc.set(i, mode*rateScalar);
				
				int numWith = 0;
				for (XY_DataSet func : histList)
					if (func.getY(i) > 0)
						numWith++;
				probFunc.set(i, (double)numWith/(double)histList.size());
				
				for (int j=0; j<fractiles.length; j++) {
					double fractile = fractiles[j];
					AbstractXY_DataSet fractileCurve = fractileCurves.get(fractile);
					fractileFuncs[j].set(i, fractileCurve.getY(i)*rateScalar);
				}
				
				AbstractXY_DataSet medianCurve = fractileCurves.get(0.5);
				medianFunc.set(i, medianCurve.getY(i)*rateScalar);
			}
		}
		
	}
	
	private int calcNumToTrim() {
		// now trim
		double minMag = mfdMinMag;
		// double catMinMag = Double.POSITIVE_INFINITY;
		double catModalMag = totalCountHist.getX(totalCountHist.getXindexForMaxY()) - 0.49 * mfdDelta;
//		System.out.println(totalCountHist);
		if (Double.isInfinite(catModalMag))
			throw new IllegalStateException("Empty catalogs!");
//		System.out.println("Modal mag: "+catModalMag);
		int numToTrim = 0;
		while (catModalMag > (minMag + 0.5 * mfdDelta) && minMag < 5.04) {
			minMag += mfdDelta;
			numToTrim++;
		}
		return numToTrim;
	}
	
	private static EvenlyDiscretizedFunc trimFunc(EvenlyDiscretizedFunc func, int numToTrim) {
		if (numToTrim == 0 || func == null)
			return func;
		Preconditions.checkState(numToTrim > 0);
		Preconditions.checkState(numToTrim < func.size());
		
		EvenlyDiscretizedFunc trimmed = new EvenlyDiscretizedFunc(func.getX(numToTrim), func.size()-numToTrim, mfdDelta);
		for (int i=0; i<trimmed.size(); i++)
			trimmed.set(i, func.getY(i+numToTrim));
		
		return trimmed;
	}
	
	private List<String> buildLegend() {
		List<String> lines = new ArrayList<>();
		
		String quantity;
		if (annualize)
			quantity = "annual rate";
		else
			quantity = "expected number";
		
		double duration = getConfig().getDuration();
		
		lines.add("**Legend**");
		lines.add("* **Mean** (thick black line): mean "+quantity+" across all "+numCatalogs+" catalogs");
//		lines.add("* **95% Conf** (light gray shaded region): binomial 95% confidence bounds on mean value");
		lines.add("* **"+getFractilesString()+"** (thin black lines): "+quantity+" percentiles across all "+numCatalogs+" catalogs");
		lines.add("* **Median** (thin blue line): median "+quantity+" across all "+numCatalogs+" catalogs");
		if (annualize && duration != 1d)
			lines.add("* **Mode** (thin cyan line): modal "+quantity+" across all "+numCatalogs+" catalogs (scaled to annualized value)");
		else
			lines.add("* **Mode** (thin cyan line): modal "+quantity+" across all "+numCatalogs+" catalogs");
		lines.add("* **"+getTimeShortLabel(duration)+" Probability** (thin red line): "
			+getTimeLabel(duration, false).toLowerCase()+" probability calculated as the fraction of catalogs with at least 1 occurrence");
		lines.add("* **95% Conf** (light red shaded region): binomial 95% confidence bounds on probability");
		if (triggeredPrimaryStats != null)
			lines.add("* **Primary** (thin green line): mean "+quantity+" from primary triggered aftershocks only "
					+ "(no secondary, tertiary, etc...) across all "+numCatalogs+" catalogs");
		
		return lines;
	}
	
	private List<String> buildTable(MFD_Stats stats, MFD_Stats primaryStats, int numToTrim) throws IOException {
		stats.calcStats();
		EvenlyDiscretizedFunc meanFunc = stats.getMeanFunc(numToTrim);
		EvenlyDiscretizedFunc medianFunc = stats.getMedianFunc(numToTrim);
		EvenlyDiscretizedFunc modeFunc = stats.getModeFunc(numToTrim);
		EvenlyDiscretizedFunc probFunc = stats.getProbFunc(numToTrim);
		EvenlyDiscretizedFunc[] fractileFuncs = stats.getFractileFuncs(numToTrim);
		
		TableBuilder builder = MarkdownUtils.tableBuilder();
		
		builder.initNewLine().addColumn("Mag").addColumn("Mean");
		for (double fractile : fractiles)
			builder.addColumn(optionalDigitDF.format(fractile*100d)+" %ile");
		builder.addColumn("Median");
		builder.addColumn("Mode");
		builder.addColumn(getTimeShortLabel(getConfig().getDuration())+" Probability");
		
		EvenlyDiscretizedFunc primaryMean = null;
		if (primaryStats != null) {
			primaryStats.calcStats();
			primaryMean = primaryStats.getMeanFunc(numToTrim);
			builder.addColumn("Primary Aftershocks Mean");
		}
		builder.finalizeLine();
		
		for (int i=0; i<meanFunc.size(); i++) {
			double mag = meanFunc.getX(i);
			builder.initNewLine();
			if (cumulative)
				builder.addColumn("**M&ge;"+optionalDigitDF.format(mag)+"**");
			else
				builder.addColumn("**M"+optionalDigitDF.format(mag-0.5*mfdDelta)+"-"+optionalDigitDF.format(mag+0.5*mfdDelta)+"**");
			builder.addColumn(getProbStr(meanFunc.getY(i)));
			for (EvenlyDiscretizedFunc fractileFunc : fractileFuncs)
				builder.addColumn(getProbStr(fractileFunc.getY(i)));
			builder.addColumn(getProbStr(medianFunc.getY(i)));
			builder.addColumn(getProbStr(modeFunc.getY(i)));
			if (probFunc != null)
				builder.addColumn(getProbStr(probFunc.getY(i)));
			if (primaryMean != null)
				builder.addColumn(getProbStr(primaryMean.getY(i)));
			
			builder.finalizeLine();
		}
		
		return builder.build();
	}

}
