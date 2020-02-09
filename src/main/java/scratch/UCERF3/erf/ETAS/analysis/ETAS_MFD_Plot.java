package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.jfree.chart.plot.DatasetRenderingOrder;
import org.opensha.commons.calc.FractileCurveCalculator;
import org.opensha.commons.data.CSVFile;
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
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

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
	
	private double[] durations;
	
	private MFD_Stats[] totalWithSpontStats;
	private MFD_Stats[] totalWithSpontSupraStats;
	private MFD_Stats[] triggeredStats;
	private MFD_Stats[] triggeredSupraStats;
	private MFD_Stats[] triggeredPrimaryStats;
	
	private HistogramFunction totalCountHist;
	
	private boolean spontaneousFound = false;
	
	private static double[] fractiles = {0.025, 0.975};
	
	private boolean noTitles = false;
	private boolean includeMedian = true;
	private boolean includeMode = true;
	private boolean includePrimary = true;
	private Color probColor = Color.RED;
	
	public ETAS_MFD_Plot(ETAS_Config config, ETAS_Launcher launcher, String prefix, boolean annualize, boolean cumulative) {
		super(config, launcher);
		this.prefix = prefix;
		this.annualize = annualize;
		this.cumulative = cumulative;
		
		boolean triggerCatAsSpont = config.getTriggerCatalogFile() != null && config.isTreatTriggerCatalogAsSpontaneous();

		double totDuration = config.getDuration();
		if (!annualize) {
			List<Double> myDurations = new ArrayList<>();
			for (double duration : ETAS_HazardChangePlot.times)
				if (duration < totDuration)
					myDurations.add(duration);
			myDurations.add(totDuration);
			durations = Doubles.toArray(myDurations);
		} else {
			durations = new double[] {totDuration};
		}
		if (config.isIncludeSpontaneous() || triggerCatAsSpont) {
			// we have spontaneous ruptures
			totalWithSpontStats = buildStats();
			totalWithSpontSupraStats = buildStats();
			if (triggerCatAsSpont)
				spontaneousFound = true;
		}
		if (config.hasTriggers()) {
			// we have input ruptures
			triggeredStats = buildStats();
			triggeredSupraStats = buildStats();
			triggeredPrimaryStats = buildStats();
		}
		Preconditions.checkState(totalWithSpontStats != null || triggeredStats != null, "Must either have spontaneous, or trigger ruptures");
		
		totalCountHist = new HistogramFunction(mfdMinMag, mfdNumMag, mfdDelta);
	}

	@Override
	public int getVersion() {
		return 2;
	}
	
	private MFD_Stats[] buildStats() {
		MFD_Stats[] ret = new MFD_Stats[durations.length];
		for (int i=0; i<ret.length; i++)
			ret[i] = new MFD_Stats();
		return ret;
	}

	@Override
	public boolean isFilterSpontaneous() {
		// filter spontaneous if we have trigger ruptures
		return triggeredStats != null;
	}

	@Override
	protected void doProcessCatalog(ETAS_Catalog completeCatalog, ETAS_Catalog triggeredOnlyCatalog, FaultSystemSolution fss) {
		for (int i=0; i<durations.length; i++) {
			long maxOT = getConfig().getSimulationStartTimeMillis() + (long)(ProbabilityModelsCalc.MILLISEC_PER_YEAR*durations[i]+0.5);
			if (totalWithSpontStats != null) {
				IncrementalMagFreqDist totalHist = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDelta);
				IncrementalMagFreqDist supraHist = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDelta);
				for (ETAS_EqkRupture rup : completeCatalog) {
					if (rup.getOriginTime() > maxOT)
						break;
					int xIndex = totalHist.getClosestXIndex(rup.getMag());
					totalHist.add(xIndex, 1d);
					// this is used to find the modal magnitude, which is used to trim plots for magnitude filtered catalogs
					totalCountHist.add(xIndex, 1d);
					if (!spontaneousFound && rup.getGeneration() == 0) {
						System.out.println("Spontaneous rupture found, will include spont MFD plots");
						spontaneousFound = true;
					}
					if (rup.getFSSIndex() > 0)
						supraHist.add(xIndex, 1d);
				}
				if (cumulative) {
					totalWithSpontStats[i].addHistogram(totalHist.getCumRateDistWithOffset());
					totalWithSpontSupraStats[i].addHistogram(supraHist.getCumRateDistWithOffset());
				} else {
					totalWithSpontStats[i].addHistogram(totalHist);
					totalWithSpontSupraStats[i].addHistogram(supraHist);
				}
			}
			if (triggeredStats != null) {
				IncrementalMagFreqDist noSpontHist = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDelta);
				IncrementalMagFreqDist supraHist = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDelta);
				IncrementalMagFreqDist primaryHist = new IncrementalMagFreqDist(mfdMinMag, mfdNumMag, mfdDelta);
				if (triggeredOnlyCatalog != null) {
					for (ETAS_EqkRupture rup : triggeredOnlyCatalog) {
						if (rup.getOriginTime() > maxOT)
							break;
						int xIndex = noSpontHist.getClosestXIndex(rup.getMag());
						noSpontHist.add(xIndex, 1d);
						if (totalWithSpontStats == null)
							// do it here
							totalCountHist.add(xIndex, 1d);
						if (rup.getGeneration() == 1)
							primaryHist.add(xIndex, 1d);
						if (rup.getFSSIndex() > 0)
							supraHist.add(xIndex, 1d);
					}
				}
				if (cumulative) {
					triggeredStats[i].addHistogram(noSpontHist.getCumRateDistWithOffset());
					triggeredSupraStats[i].addHistogram(supraHist.getCumRateDistWithOffset());
					triggeredPrimaryStats[i].addHistogram(primaryHist.getCumRateDistWithOffset());
				} else {
					triggeredStats[i].addHistogram(noSpontHist);
					triggeredSupraStats[i].addHistogram(supraHist);
					triggeredPrimaryStats[i].addHistogram(primaryHist);
				}
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
	
	public void setHideTitles() {
		this.noTitles = true;
	}

	public void setIncludeMedian(boolean includeMedian) {
		this.includeMedian = includeMedian;
	}

	public void setIncludeMode(boolean includeMode) {
		this.includeMode = includeMode;
	}

	public void setIncludePrimary(boolean includePrimary) {
		this.includePrimary = includePrimary;
	}
	
	public void setProbColor(Color probColor) {
		this.probColor = probColor;
	}

	@Override
	protected List<? extends Runnable> doFinalize(File outputDir, FaultSystemSolution fss, ExecutorService exec)
			throws IOException {
		int numToTrim = calcNumToTrim();
		
		String title = getPlotTitle();
		for (int i=0; i<durations.length; i++) {
			String durTitle = title;
			String durPrefix = prefix;
			if (durations.length > 1) {
				durTitle = getTimeLabel(durations[i], false)+" "+title;
				durPrefix = getTimeShortLabel(durations[i]).replaceAll(" ", "")+"_"+prefix;
			}
			if (totalWithSpontStats != null && spontaneousFound) {
				String myTitle = durTitle;
				if (triggeredStats != null)
					// this is has both spontaneous and trigger
					myTitle += ", Including Spontaneous";
				
				plot(outputDir, durPrefix, myTitle, totalWithSpontStats[i], null, totalWithSpontSupraStats[i], numToTrim, fss, durations[i]);
			}
			if (triggeredStats != null) {
				durTitle += ", Triggered Events";
				
				plot(outputDir, durPrefix+"_triggered", durTitle, triggeredStats[i], triggeredPrimaryStats[i], triggeredSupraStats[i], numToTrim, fss, durations[i]);
			}
		}
		return null;
	}

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink) throws IOException {
		int numToTrim = calcNumToTrim();
		
		List<String> lines = new ArrayList<>();
		
		String title = getPlotTitle();
		
		List<ETAS_EqkRupture> triggerRups = getLauncher().getCombinedTriggers();
		if (triggeredStats != null && !annualize && durations.length > 1) {
			// do a summary table
			lines.add(topLevelHeading+" Probabilities Summary Table");
			lines.add(topLink); lines.add("");
			
			double maxTriggerMag = 0d;
			for (ETAS_EqkRupture trigger : triggerRups)
				maxTriggerMag = Math.max(maxTriggerMag, trigger.getMag());
			
			double modalMag = totalCountHist.getX(totalCountHist.getXindexForMaxY()) - 0.5*mfdDelta;
			double minTableMag = Math.max(Math.floor(maxTriggerMag-3d), Math.floor(modalMag));
			minTableMag = Math.max(minTableMag, Math.ceil(mfdMinMag-0.5*mfdDelta));
			double maxMag = 0d;
			for (Point2D pt : totalCountHist)
				if (pt.getY() > 0)
					maxMag = Math.max(maxMag, pt.getX());
			List<Double> mags = new ArrayList<>();
			boolean maxTriggerFound = false;
			for (double mag=minTableMag; mag <= maxMag; mag += 0.5) {
				if ((float)mag == (float)maxTriggerMag)
					maxTriggerFound = true;
				mags.add(mag);
			}
			if (!maxTriggerFound && maxTriggerMag > minTableMag) {
				mags.add(maxTriggerMag);
				Collections.sort(mags);
			}
			System.out.println("Table mags: "+Joiner.on(", ").join(mags));
			
			if (totalWithSpontStats != null && spontaneousFound) {
				// we also have spontaneous
				lines.add(topLevelHeading+"# Probabilities Summary Table, Including Spontaneous");
				lines.add(topLink); lines.add("");
				
				lines.addAll(getProbsSummaryTable(mags, totalWithSpontStats).build());
				lines.add("");
				lines.add(topLevelHeading+"# Probabilities Summary Table, Triggered Only");
				lines.add(topLink); lines.add("");
			}
			
			lines.addAll(getProbsSummaryTable(mags, triggeredStats).build());
			lines.add("");
		}
		
		lines.add(topLevelHeading+" "+title);
		lines.add(topLink); lines.add("");
		
		if (durations.length > 1)
			topLevelHeading += "#";
		
		for (int i=durations.length; --i>=0;) {
			String prefix = this.prefix;
			String myTitle = title;
			if (durations.length > 1) {
				myTitle = getTimeLabel(durations[i], false)+" "+title;
				lines.add(topLevelHeading+" "+myTitle);
				lines.add(topLink); lines.add("");
				prefix = getTimeShortLabel(durations[i]).replaceAll(" ", "")+"_"+prefix;
			}
			
			if (totalWithSpontStats != null && spontaneousFound) {
				if (triggeredStats != null) {
					// this is has both spontaneous and trigger
					myTitle = myTitle+", Including Spontaneous";
					
					lines.add(topLevelHeading+"# "+myTitle);
					lines.add(topLink); lines.add("");
					
					lines.add("*Note: This section includes both spontaneous and triggered events*");
					lines.add("");
				}
				
				lines.addAll(buildLegend(durations[i]));
				lines.add("");
				
				lines.add("![MFD Plot]("+relativePathToOutputDir+"/"+prefix+".png)");
				lines.add("");
				
				lines.addAll(buildTable(totalWithSpontStats[i], null, totalWithSpontSupraStats[i], numToTrim, durations[i]));
				lines.add("");
			}
			if (triggeredStats != null) {
				myTitle += ", Triggered Events";
				
				if (totalWithSpontStats != null) {
					lines.add(topLevelHeading+"# "+myTitle);
					lines.add(topLink); lines.add("");
					
					lines.add("*Note: This section only includes triggered events, spontaneous were calculated but filtered out here*");
					lines.add("");
				}
				
				lines.addAll(buildLegend(durations[i]));
				lines.add("");
				
				lines.add("![MFD Plot]("+relativePathToOutputDir+"/"+prefix+"_triggered.png)");
				lines.add("");
				
				lines.addAll(buildTable(triggeredStats[i], triggeredPrimaryStats[i], triggeredSupraStats[i], numToTrim, durations[i]));
				lines.add("");
			}
		}
		
		return lines;
	}
	
	private TableBuilder getProbsSummaryTable(List<Double> mags, MFD_Stats[] stats) {
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("Magnitude");
		for (double duration : durations)
			table.addColumn(getTimeLabel(duration, false)+" Prob");
		table.finalizeLine();
		int numForConf = getNumProcessed();
		for (double mag : mags) {
			table.initNewLine();
			table.addColumn("**M&ge;"+optionalDigitDF.format(mag)+"**");
			for (int d=0; d<durations.length; d++) {
				double val = stats[d].probFunc.getInterpolatedY(mag);
				table.addColumn(getProbStr(val, true));
			}
			table.finalizeLine();
			table.initNewLine();
			table.addColumn("*95% Conf*");
			for (int d=0; d<durations.length; d++) {
				double val = stats[d].probFunc.getInterpolatedY(mag);
				table.addColumn("*"+getConfString(val, numForConf, true)+"*");
			}
			table.finalizeLine();
		}
		return table;
	}
	
	private static String getFractilesString() {
		List<String> percents = new ArrayList<>();
		for (double fractile : fractiles)
			percents.add(optionalDigitDF.format(fractile*100)+"%");
		return Joiner.on(",").join(percents);
	}
	
	private void plot(File outputDir, String prefix, String title, MFD_Stats stats, MFD_Stats primaryStats, MFD_Stats supraStats,
			int numToTrim, FaultSystemSolution fss, double duration) throws IOException {
		stats.calcStats(annualize, getConfig());
		supraStats.calcStats(annualize, getConfig());
		
		EvenlyDiscretizedFunc meanFunc = stats.getMeanFunc(numToTrim);
		EvenlyDiscretizedFunc medianFunc = stats.getMedianFunc(numToTrim);
		EvenlyDiscretizedFunc modeFunc = stats.getModeFunc(numToTrim);
		EvenlyDiscretizedFunc probFunc = stats.getProbFunc(numToTrim);
		EvenlyDiscretizedFunc supraProbFunc = supraStats.getProbFunc(numToTrim);
		EvenlyDiscretizedFunc[] fractileFuncs = stats.getFractileFuncs(numToTrim);
		
		meanFunc.setName("Mean");
		modeFunc.setName("Mode");
		medianFunc.setName("Median");
		probFunc.setName(getTimeShortLabel(duration)+" Prob");
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
		
		if (includeMedian) {
			funcs.add(medianFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
		}
		
		if (includeMode) {
			funcs.add(modeFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.CYAN.darker()));
		}
		
		if (probFunc != null) {
			funcs.add(probFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, probColor));
			
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
			chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f,
					new Color(probColor.getRed(), probColor.getGreen(), probColor.getBlue(), 90)));
			if (supraProbFunc != null) {
				supraProbFunc.setName("Supra");
				funcs.add(supraProbFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, probColor));
			}
		}
		
		if (primaryStats != null && includePrimary) {
			primaryStats.calcStats(annualize, getConfig());
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
			yAxisLabel = getTimeLabel(duration, false)+" "+yAxisLabel+"Number";
		
		PlotSpec spec = new PlotSpec(funcs, chars, noTitles ? " " : title, "Magnitude", yAxisLabel);
		spec.setLegendVisible(true);
		
		double mfdMinY = ETAS_MFD_Plot.mfdMinY;
		if (mfdMinY > 1d/(double)numCatalogs)
			mfdMinY = Math.pow(10, Math.floor(Math.log10(1d/numCatalogs)));
		
		HeadlessGraphPanel gp = buildGraphPanel();
		gp.setLegendFontSize(18);
		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
		gp.setUserBounds(meanFunc.getMinX(), meanFunc.getMaxX(), mfdMinY, mfdMaxY);

		gp.drawGraphPanel(spec, false, true);
		gp.getChartPanel().setSize(800, 600);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
		gp.saveAsTXT(new File(outputDir, prefix+".txt").getAbsolutePath());
		
		// write CSV
		CSVFile<String> csv = new CSVFile<>(true);
		
		List<String> header = new ArrayList<>();
		header.add("Mag");
		header.add("Mean");
		for (double fractile : fractiles)
			header.add(optionalDigitDF.format(fractile*100d)+" %ile");
		header.add("Median");
		header.add("Mode");
		if (probFunc != null) {
			header.add(getTimeShortLabel(duration)+" Probability");
			if (supraProbFunc != null)
				header.add(getTimeShortLabel(duration)+" Supra-Seis Prob");
		}
		
		EvenlyDiscretizedFunc primaryMean = null;
		if (primaryStats != null) {
			primaryStats.calcStats(annualize, getConfig());
			primaryMean = primaryStats.getMeanFunc(numToTrim);
			header.add("Primary Aftershocks Mean");
		}
		csv.addLine(header);
		
		for (int i=0; i<meanFunc.size(); i++) {
			double mag = meanFunc.getX(i);
			List<String> line = new ArrayList<>();
			line.add((float)mag+"");
			line.add((float)meanFunc.getY(i)+"");
			for (EvenlyDiscretizedFunc fractileFunc : fractileFuncs)
				line.add((float)fractileFunc.getY(i)+"");
			line.add((float)medianFunc.getY(i)+"");
			line.add((float)modeFunc.getY(i)+"");
			if (probFunc != null) {
				line.add((float)probFunc.getY(i)+"");
				if (supraProbFunc != null)
					line.add((float)supraProbFunc.getY(i)+"");
			}
			if (primaryMean != null)
				line.add((float)primaryMean.getY(i)+"");
			
			csv.addLine(line);
		}
		csv.writeToFile(new File(outputDir, prefix+".csv"));
	}
	
	public static MFD_Stats readCSV(File csvFile) throws IOException {
		CSVFile<String> csv = CSVFile.readFile(csvFile, true);
		
		double minMag = csv.getDouble(1, 0);
		int numMag = csv.getNumRows()-1;
		int meanCol = getCSVColumn(csv, "Mean");
		EvenlyDiscretizedFunc meanFunc = new EvenlyDiscretizedFunc(minMag, numMag, mfdDelta);
		int probCol = getCSVColumn(csv, "Probability");
		EvenlyDiscretizedFunc probFunc = probCol >= 0 ? new EvenlyDiscretizedFunc(minMag, numMag, mfdDelta) : null;
		int medianCol = getCSVColumn(csv, "Median");
		EvenlyDiscretizedFunc medianFunc = new EvenlyDiscretizedFunc(minMag, numMag, mfdDelta);
		int modeCol = getCSVColumn(csv, "Mode");
		EvenlyDiscretizedFunc modeFunc = new EvenlyDiscretizedFunc(minMag, numMag, mfdDelta);
		int numFracts = (medianCol - 1) - meanCol;
		EvenlyDiscretizedFunc[] fractileFuncs = new EvenlyDiscretizedFunc[numFracts];
		for (int i=0; i<numFracts; i++)
			fractileFuncs[i] = new EvenlyDiscretizedFunc(minMag, numMag, mfdDelta);
		
		for (int m=0; m<numMag; m++) {
			int row = m+1;
			meanFunc.set(m, csv.getDouble(row, meanCol));
			modeFunc.set(m, csv.getDouble(row, modeCol));
			medianFunc.set(m, csv.getDouble(row, medianCol));
			if (probFunc != null)
				probFunc.set(m, csv.getDouble(row, probCol));
			for (int i=0; i<numFracts; i++)
				fractileFuncs[i].set(m, csv.getDouble(row, meanCol+1+i));
		}
		
		MFD_Stats stats = new MFD_Stats();
		
		stats.meanFunc = meanFunc;
		stats.probFunc = probFunc;
		stats.medianFunc = medianFunc;
		stats.modeFunc = modeFunc;
		stats.fractileFuncs = fractileFuncs;
		
		return stats;
	}
	
	private static int getCSVColumn(CSVFile<String> csv, String name) {
		name = name.toLowerCase();
		for (int col=1; col<csv.getNumCols(); col++)
			if (csv.get(0, col).toLowerCase().contains(name))
				return col;
		return -1;
	}
	
	public static class MFD_Stats {
		
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

		public synchronized void calcStats(boolean annualize, ETAS_Config config) {
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
				rateScalar = 1d/config.getDuration();
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
		return calcNumToTrim(totalCountHist);
	}
	
	static int calcNumToTrim(HistogramFunction totalCountHist) {
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
	
	private List<String> buildLegend(double duration) {
		List<String> lines = new ArrayList<>();
		
		String quantity;
		if (annualize)
			quantity = "annual rate";
		else
			quantity = "expected number";
		
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
		lines.add("* **"+getTimeShortLabel(duration)+" Supraseismogenic Probability** (thin dashed red line): same as above, but only for "
				+ "supraseismogenic ruptures on explicitly modeled UCERF3 faults");
		lines.add("* **95% Conf** (light red shaded region): binomial 95% confidence bounds on probability");
		if (triggeredPrimaryStats != null)
			lines.add("* **Primary** (thin green line): mean "+quantity+" from primary triggered aftershocks only "
					+ "(no secondary, tertiary, etc...) across all "+numCatalogs+" catalogs");
		
		return lines;
	}
	
	private List<String> buildTable(MFD_Stats stats, MFD_Stats primaryStats, MFD_Stats supraStats, int numToTrim, double duration) throws IOException {
		stats.calcStats(annualize, getConfig());
		supraStats.calcStats(annualize, getConfig());
		EvenlyDiscretizedFunc meanFunc = stats.getMeanFunc(numToTrim);
		EvenlyDiscretizedFunc medianFunc = stats.getMedianFunc(numToTrim);
		EvenlyDiscretizedFunc modeFunc = stats.getModeFunc(numToTrim);
		EvenlyDiscretizedFunc probFunc = stats.getProbFunc(numToTrim);
		EvenlyDiscretizedFunc supraProbFunc = supraStats.getProbFunc(numToTrim);
		EvenlyDiscretizedFunc[] fractileFuncs = stats.getFractileFuncs(numToTrim);
		
		TableBuilder builder = MarkdownUtils.tableBuilder();
		
		builder.initNewLine().addColumn("Mag").addColumn("Mean");
		for (double fractile : fractiles)
			builder.addColumn(optionalDigitDF.format(fractile*100d)+" %ile");
		builder.addColumn("Median");
		builder.addColumn("Mode");
		if (probFunc != null) {
			builder.addColumn(getTimeShortLabel(duration)+" Probability");
			builder.addColumn(getTimeShortLabel(duration)+" Prob 95% Conf");
			if (supraProbFunc != null)
				builder.addColumn(getTimeShortLabel(duration)+" Supra-Seis Prob");
		}
		
		EvenlyDiscretizedFunc primaryMean = null;
		if (primaryStats != null) {
			primaryStats.calcStats(annualize, getConfig());
			primaryMean = primaryStats.getMeanFunc(numToTrim);
			builder.addColumn("Primary Aftershocks Mean");
		}
		builder.finalizeLine();
		
		int numForConf = getNumProcessed();
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
			if (probFunc != null) {
				builder.addColumn(getProbStr(probFunc.getY(i), true));
				builder.addColumn(getConfString(probFunc.getY(i), numForConf, true));
				if (supraProbFunc != null)
					builder.addColumn(getProbStr(supraProbFunc.getY(i), true));
			}
			if (primaryMean != null)
				builder.addColumn(getProbStr(primaryMean.getY(i)));
			
			builder.finalizeLine();
		}
		
		return builder.build();
	}

}
