package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.data.Range;
import org.opensha.commons.calc.FractileCurveCalculator;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.AbstractXY_DataSet;
import org.opensha.commons.data.function.ArbDiscrEmpiricalDistFunc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class ETAS_LongTermRateVariabilityPlot extends ETAS_AbstractPlot {
	
	private static double[] FIXED_DURATIONS_DEFAULT = { 162, 80, 28 };
	
	private static boolean OVERLAP = false;
	
	private static double[] fractiles = {0.025, 0.16, 0.84, 0.975};
	
	private static int target_sim_mfds_per_duration = 50;
	
	private DiscretizedFunc durationFunc;
	private Map<Double, List<IncrementalMagFreqDist>> durMFDsListMap;
	
	private HistogramFunction totalCountHist;

	private String prefix;
	private double[] fixedDurations;
	
	// plot outputs
	private List<CSVFile<String>> fixedDurCSVs;
	private List<String> fixedDurPrefixes;
	
	private double[] durMinMags;
	private List<CSVFile<String>> durCSVs;
	private List<String> durPrefixes;
	
	private Random r;
	
	protected ETAS_LongTermRateVariabilityPlot(ETAS_Config config, ETAS_Launcher launcher, String prefix) {
		this(config, launcher, prefix, FIXED_DURATIONS_DEFAULT);
	}

	protected ETAS_LongTermRateVariabilityPlot(ETAS_Config config, ETAS_Launcher launcher, String prefix, double[] fixedDurations) {
		super(config, launcher);
		this.prefix = prefix;
		this.fixedDurations = fixedDurations;
		Preconditions.checkState(config.isIncludeSpontaneous(), "Long term variability requires spontaneous ruptures");
		Preconditions.checkState(!config.hasTriggers(), "Long term variability plot not applicable to aftershock catalogs");
		
		double totDuration = config.getDuration();
		
		this.r = new Random((long)(totDuration*config.getNumSimulations()));
		
		// only do high resolution up to duration of 100
		double evenMaxDuration = Math.min(100, totDuration);
		int targetNumDurations = totDuration > 100 ? 25 : 50;
		int myDelta = 1;
		while (evenMaxDuration / myDelta > targetNumDurations)
			myDelta++;
		EvenlyDiscretizedFunc evenDurFunc = new EvenlyDiscretizedFunc(
				(double)myDelta, (int)(evenMaxDuration/myDelta), (double)myDelta);
		if (myDelta > 1) {
			// add point at 1
			durationFunc = new ArbitrarilyDiscretizedFunc();
			durationFunc.set(1d, 0d);
			for (Point2D pt : evenDurFunc)
				durationFunc.set(pt);
		} else {
			durationFunc = evenDurFunc;
		}
		if (totDuration > evenMaxDuration) {
			// now add a very low resolution one for the long durations
			if (totDuration >= 500)
				myDelta = 100;
			else if (totDuration >= 250)
				myDelta = 50;
			else
				myDelta = 20;
			evenDurFunc = new EvenlyDiscretizedFunc(
					(double)evenMaxDuration+myDelta, (int)(totDuration/myDelta), (double)myDelta);
			for (Point2D pt : evenDurFunc)
				if (pt.getX() <= totDuration)
					durationFunc.set(pt);
		}
		Preconditions.checkState((float)durationFunc.getMaxX() <= (float)totDuration);
		System.out.println("Calculating long term variability with "+durationFunc.size()+" durations");
//		System.out.println("Duration func size: "+durationFunc.size());
//		System.out.println("Duration func delta: "+durationFunc.getDelta());
//		System.out.println("Duration func max: "+durationFunc.getMaxX());
//		System.exit(0);
		durMFDsListMap = new HashMap<>();
		for (Point2D pt : durationFunc)
			durMFDsListMap.put(pt.getX(), new ArrayList<>());
		if (fixedDurations != null)
			for (double duration : fixedDurations)
				if (duration <= config.getDuration())
					durMFDsListMap.put(duration, new ArrayList<>());
		
		totalCountHist = new HistogramFunction(ETAS_MFD_Plot.mfdMinMag, ETAS_MFD_Plot.mfdNumMag, ETAS_MFD_Plot.mfdDelta);
	}

	@Override
	public int getVersion() {
		return 1;
	}

	@Override
	public boolean isFilterSpontaneous() {
		return false;
	}

	@Override
	protected void doProcessCatalog(List<ETAS_EqkRupture> completeCatalog, List<ETAS_EqkRupture> triggeredOnlyCatalog,
			FaultSystemSolution fss) {
		long simStartTime = getConfig().getSimulationStartTimeMillis();
		double simDuration = getConfig().getDuration();
		// pad by 1s for rounding errors
		long simEndTime = simStartTime + (long)(simDuration*ProbabilityModelsCalc.MILLISEC_PER_YEAR) + 1000;
		for (double duration : durMFDsListMap.keySet()) {
			List<IncrementalMagFreqDist> mfdList = durMFDsListMap.get(duration);
			long durationMillis = (long)(duration*ProbabilityModelsCalc.MILLISEC_PER_YEAR);
			long sweepStartTime;
			long sweepDeltaMillis;
			if (OVERLAP) {
				double maxSweeps = (simDuration - duration);
				double sweepDeltaYears = 1d;
				if (maxSweeps > target_sim_mfds_per_duration)
					sweepDeltaYears = Math.min(duration, maxSweeps/target_sim_mfds_per_duration);
				sweepDeltaMillis = (long)(sweepDeltaYears*ProbabilityModelsCalc.MILLISEC_PER_YEAR);
				sweepStartTime = simStartTime;
			} else {
				sweepDeltaMillis = durationMillis;
				// choose a random start time to not stack them all up at the start if catalog duration is
				// not perfectly divisible by sweep duration
				long durMod = durationMillis % sweepDeltaMillis;
				if (durMod > 1)
					sweepStartTime = simStartTime+(long)Math.round(r.nextDouble()*durMod);
				else
					sweepStartTime = simStartTime;
			}
			
			
			int numProcessed = 0;
			for (long startTime=sweepStartTime; startTime+durationMillis<=simEndTime; startTime+=sweepDeltaMillis) {
				long endTime = startTime+durationMillis;
				mfdList.add(calcSubMFD(completeCatalog, startTime, endTime));
				numProcessed++;
			}
			Preconditions.checkState(numProcessed > 0, "Sub-duration of %s is too long for simulation?", duration);
		}
		
		// now keep track of total count to determine if this catalog is filtered
		for (ETAS_EqkRupture rup : completeCatalog)
			totalCountHist.add(totalCountHist.getClosestXIndex(rup.getMag()), 1d);
	}
	
	private IncrementalMagFreqDist calcSubMFD(List<ETAS_EqkRupture> catalog, long startTime, long endTime) {
		IncrementalMagFreqDist mfd = new IncrementalMagFreqDist(ETAS_MFD_Plot.mfdMinMag, ETAS_MFD_Plot.mfdNumMag, ETAS_MFD_Plot.mfdDelta);
		for (ETAS_EqkRupture rup : catalog) {
			if (rup.getOriginTime() < startTime)
				continue;
			if (rup.getOriginTime() >= endTime)
				break;
			int index = mfd.getClosestXIndex(rup.getMag());
			mfd.add(index, 1d);
		}
		return mfd;
	}

	@Override
	public List<Runnable> doFinalize(File outputDir, FaultSystemSolution fss) throws IOException {
		int numToTrim = ETAS_MFD_Plot.calcNumToTrim(totalCountHist);
		
		Map<Double, MFD_VarStats> durStatsMap = new HashMap<>();
//		System.out.println("Building dur stats");
		Double myMinMag = null;
		for (Double duration : durMFDsListMap.keySet()) {
			List<IncrementalMagFreqDist> mfds = durMFDsListMap.get(duration);
			Preconditions.checkState(!mfds.isEmpty());
			
			MFD_VarStats stats = new MFD_VarStats(mfds, numToTrim, duration);
			durStatsMap.put(duration, stats);
			if (myMinMag == null)
				myMinMag = stats.meanFunc.getMinX();
		}
		
		List<String> commonHeader = new ArrayList<>();
		commonHeader.add("Mean");
		commonHeader.add("Median");
		commonHeader.add("Mode");
		commonHeader.add("Std. Dev.");
		for (double fractile : fractiles)
			commonHeader.add(optionalDigitDF.format(fractile*100d)+" %-ile");

		PlotCurveCharacterstics meanChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK);
		PlotCurveCharacterstics fractileChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK);
		PlotCurveCharacterstics medianChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE);
		PlotCurveCharacterstics modeChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.CYAN.darker());
		PlotCurveCharacterstics stdDevChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GREEN.darker());
		
//		System.out.println("Plotting");
		// first magnitude dependent plots for each fixed duration
		if (fixedDurations != null && fixedDurations.length > 0) {
			fixedDurPrefixes = new ArrayList<>();
			fixedDurCSVs = new ArrayList<>();
			for (int i=0; i<fixedDurations.length; i++) {
				double duration = fixedDurations[i];
//				System.out.println("Plotting fixed duration: "+duration);
				MFD_VarStats stats = durStatsMap.get(duration);
				
				// build CSV file
				CSVFile<String> durCSV = new CSVFile<>(true);
				fixedDurCSVs.add(durCSV);
				List<String> header = new ArrayList<>(commonHeader);
				header.add(0, "Magnitude");
				durCSV.addLine(header);
				
				for (int m=0; m<stats.meanFunc.size(); m++)
					durCSV.addLine(stats.getCSVLine(m, (float)stats.meanFunc.getX(m)+""));
				
				String myPrefix = prefix+"_"+optionalDigitDF.format(duration)+"yr";
				fixedDurPrefixes.add(myPrefix);
//				System.out.println("writing CSV with "+durCSV.getNumRows()+" rows");
				
				durCSV.writeToFile(new File(outputDir, myPrefix+".csv"));
				
				// plot it
				List<DiscretizedFunc> funcs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				
				stats.meanFunc.setName("Mean");
				funcs.add(stats.meanFunc);
				chars.add(meanChar);
				
				EvenlyDiscretizedFunc[] fractileFuncs = stats.fractileFuncs;
				for (int j=0; j<fractileFuncs.length; j++) {
					EvenlyDiscretizedFunc func = fractileFuncs[j];
					if (j == 0)
						func.setName(getFractilesString());
					funcs.add(func);
					chars.add(fractileChar);
				}
				
				stats.medianFunc.setName("Median");
				funcs.add(stats.medianFunc);
				chars.add(medianChar);
				
				stats.modeFunc.setName("Mode");
				funcs.add(stats.modeFunc);
				chars.add(modeChar);
				
				stats.stdDevFunc.setName("Std. Dev.");
				funcs.add(stats.stdDevFunc);
				chars.add(stdDevChar);
				
				String yAxisLabel = "Cumulative Annual Rate (1/yr)";
				String title = getTimeLabel(duration, false)+" MFD Variability";
				
				PlotSpec spec = new PlotSpec(funcs, chars, title, "Magnitude", yAxisLabel);
				spec.setLegendVisible(true);
				
				Range xRange = new Range(stats.meanFunc.getMinX(), stats.meanFunc.getMaxX());
				Range yRange = getYRange(stats.meanFunc, stats.stdDevFunc);
				
				HeadlessGraphPanel gp = buildGraphPanel();
				gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);

				gp.drawGraphPanel(spec, false, true, xRange, yRange);
				gp.getChartPanel().setSize(800, 600);
				gp.saveAsPNG(new File(outputDir, myPrefix+".png").getAbsolutePath());
				gp.saveAsPDF(new File(outputDir, myPrefix+".pdf").getAbsolutePath());
				gp.saveAsTXT(new File(outputDir, myPrefix+".txt").getAbsolutePath());
			}
		}
		
		// now duration dependent functions
		if (myMinMag < 5d)
			durMinMags = new double[] { myMinMag, 5d };
		else
			durMinMags = new double[] { myMinMag };
		
		durCSVs = new ArrayList<>();
		durPrefixes = new ArrayList<>();
		
		for (double durMag : durMinMags) {
			ArbitrarilyDiscretizedFunc meanFunc = new ArbitrarilyDiscretizedFunc();
			ArbitrarilyDiscretizedFunc[] fractileFuncs = new ArbitrarilyDiscretizedFunc[fractiles.length];
			for (int i=0; i<fractileFuncs.length; i++)
				fractileFuncs[i] = new ArbitrarilyDiscretizedFunc();
			ArbitrarilyDiscretizedFunc medianFunc = new ArbitrarilyDiscretizedFunc();
			ArbitrarilyDiscretizedFunc modeFunc = new ArbitrarilyDiscretizedFunc();
			ArbitrarilyDiscretizedFunc stdDevFunc = new ArbitrarilyDiscretizedFunc();
			
			CSVFile<String> csv = new CSVFile<>(true);
			List<String> header = new ArrayList<>(commonHeader);
			header.add(0, "Duration (years)");
			csv.addLine(header);
			
			for (int i=0; i<durationFunc.size(); i++) {
				double duration = durationFunc.getX(i);
				MFD_VarStats stats = durStatsMap.get(duration);
				
				int magIndex = stats.meanFunc.getClosestXIndex(durMag);

				meanFunc.set(duration, stats.meanFunc.getY(magIndex));
				for (int f=0; f<fractileFuncs.length; f++)
					fractileFuncs[f].set(duration, stats.fractileFuncs[f].getY(magIndex));
				medianFunc.set(duration, stats.medianFunc.getY(magIndex));
				modeFunc.set(duration, stats.modeFunc.getY(magIndex));
				stdDevFunc.set(duration, stats.stdDevFunc.getY(magIndex));
				
				csv.addLine(stats.getCSVLine(magIndex, (float)duration+""));
			}
			
			String myPrefix = prefix+"_m"+optionalDigitDF.format(durMag);
			durCSVs.add(csv);
			durPrefixes.add(myPrefix);
			
			fixedDurPrefixes.add(myPrefix);
			
			csv.writeToFile(new File(outputDir, myPrefix+".csv"));
			
			// plot it
			List<DiscretizedFunc> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			meanFunc.setName("Mean");
			funcs.add(meanFunc);
			chars.add(meanChar);
			
			for (int j=0; j<fractileFuncs.length; j++) {
				DiscretizedFunc func = fractileFuncs[j];
				if (j == 0)
					func.setName(getFractilesString());
				funcs.add(func);
				chars.add(fractileChar);
			}
			
			medianFunc.setName("Median");
			funcs.add(medianFunc);
			chars.add(medianChar);
			
			modeFunc.setName("Mode");
			funcs.add(modeFunc);
			chars.add(modeChar);
			
			stdDevFunc.setName("Std. Dev.");
			funcs.add(stdDevFunc);
			chars.add(stdDevChar);
			
			String yAxisLabel = "Cumulative Annual Rate (1/yr)";
			String title = "M"+optionalDigitDF.format(durMag)+" Rate Variability";
			
			PlotSpec spec = new PlotSpec(funcs, chars, title, "Duration (years)", yAxisLabel);
			spec.setLegendVisible(true);
			
			Range xRange = new Range(durationFunc.getMinX(), durationFunc.getMaxX());
			Range yRange = getYRange(meanFunc, stdDevFunc);
			
			HeadlessGraphPanel gp = buildGraphPanel();
			gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);

			gp.drawGraphPanel(spec, false, true, xRange, yRange);
			gp.getChartPanel().setSize(800, 600);
			gp.saveAsPNG(new File(outputDir, myPrefix+".png").getAbsolutePath());
			gp.saveAsPDF(new File(outputDir, myPrefix+".pdf").getAbsolutePath());
			gp.saveAsTXT(new File(outputDir, myPrefix+".txt").getAbsolutePath());
		}
		return null;
	}
	
	private static Range getYRange(DiscretizedFunc... funcs) {
		// determine extents from mean func
		double maxY = 0d;
		double minY = Double.POSITIVE_INFINITY;
		for (DiscretizedFunc func : funcs) {
			for (Point2D pt : func) {
				if (pt.getY() > 0)
					minY = Math.min(minY, pt.getY());
				maxY = Math.max(maxY, pt.getY());
			}
		}
		
		// pad them by a factor of 2
		minY /= 2;
		maxY *= 2;
		
		// now encompassing log10 range
		minY = Math.pow(10, Math.floor(Math.log10(minY)));
		maxY = Math.pow(10, Math.ceil(Math.log10(maxY)));
		
		return new Range(minY, maxY);
	}
	
	private static String getFractilesString() {
		List<String> percents = new ArrayList<>();
		for (double fractile : fractiles)
			percents.add(optionalDigitDF.format(fractile*100)+"%");
		return Joiner.on(",").join(percents);
	}
	
	private class MFD_VarStats {
		
		private XY_DataSetList cumulativeMFDs;
		private List<Double> relativeWeights;
		
		private EvenlyDiscretizedFunc meanFunc;
		private EvenlyDiscretizedFunc medianFunc;
		private EvenlyDiscretizedFunc modeFunc;
		private EvenlyDiscretizedFunc stdDevFunc;
		private EvenlyDiscretizedFunc[] fractileFuncs;
		
		public MFD_VarStats(List<IncrementalMagFreqDist> mfds, int numToTrim, double duration) {
			this.cumulativeMFDs = new XY_DataSetList();
			this.relativeWeights = new ArrayList<>();
			for (IncrementalMagFreqDist mfd : mfds) {
				if (numToTrim > 0) {
					IncrementalMagFreqDist trimmed = new IncrementalMagFreqDist(mfd.getX(numToTrim), mfd.size()-numToTrim, mfd.getDelta());
					for (int i=0; i<trimmed.size(); i++)
						trimmed.set(i, mfd.getY(i+numToTrim));
					mfd = trimmed;
				}
				EvenlyDiscretizedFunc cumulative = mfd.getCumRateDistWithOffset();
				cumulativeMFDs.add(cumulative);
				relativeWeights.add(1d);
			}
			
			double minMag = cumulativeMFDs.get(0).getMinX();
			int numMag = cumulativeMFDs.get(0).size();
			double deltaMag = mfds.get(0).getDelta();
			
			FractileCurveCalculator fractileCalc = new FractileCurveCalculator(cumulativeMFDs, relativeWeights);
			meanFunc = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			stdDevFunc = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			medianFunc = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			modeFunc = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			fractileFuncs = new EvenlyDiscretizedFunc[fractiles.length];
			for (int i=0; i<fractiles.length; i++)
				fractileFuncs[i] = new EvenlyDiscretizedFunc(minMag, numMag, deltaMag);
			
			AbstractXY_DataSet meanCurve = fractileCalc.getMeanCurve();
			Map<Double, AbstractXY_DataSet> fractileCurves = new HashMap<>();
			for (Double fractile : fractiles)
				fractileCurves.put(fractile, fractileCalc.getFractile(fractile));
			fractileCurves.put(0.5, fractileCalc.getFractile(0.5));
			
			double rateScalar = 1d/duration;
			
			for (int i=0; i<meanCurve.size(); i++) {
				meanFunc.set(i, meanCurve.getY(i)*rateScalar);
				
				ArbDiscrEmpiricalDistFunc dist = fractileCalc.getEmpiricalDist(i);
				double mode;
				if (dist.size() == 1)
					mode = dist.getX(0);
				else
					mode = dist.getMostCentralMode();
				modeFunc.set(i, mode*rateScalar);
				
				stdDevFunc.set(i, dist.getStdDev()*rateScalar);
				
				for (int j=0; j<fractiles.length; j++) {
					double fractile = fractiles[j];
					AbstractXY_DataSet fractileCurve = fractileCurves.get(fractile);
					fractileFuncs[j].set(i, fractileCurve.getY(i)*rateScalar);
				}
				
				AbstractXY_DataSet medianCurve = fractileCurves.get(0.5);
				medianFunc.set(i, medianCurve.getY(i)*rateScalar);
			}
		}
		
		public List<String> getCSVLine(int magIndex, String...leadingVals) {
			List<String> line = new ArrayList<>();
			for (String val : leadingVals)
				line.add(val);
			line.add((float)meanFunc.getY(magIndex)+"");
			line.add((float)medianFunc.getY(magIndex)+"");
			line.add((float)modeFunc.getY(magIndex)+"");
			line.add((float)stdDevFunc.getY(magIndex)+"");
			for (int f=0; f<fractileFuncs.length; f++)
				line.add((float)fractileFuncs[f].getY(magIndex)+"");
			return line;
		}
		
	}

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink)
			throws IOException {
		List<String> lines = new ArrayList<>();
		
		lines.add(topLevelHeading+" Long Term Rate Variability");
		lines.add(topLink); lines.add("");
		
		if (fixedDurPrefixes != null) {
			topLevelHeading += "#";
			for (int i=0; i<fixedDurations.length; i++) {
				String prefix = fixedDurPrefixes.get(i);
				CSVFile<String> csv = fixedDurCSVs.get(i);
				
				lines.add(topLevelHeading+" "+getTimeLabel(fixedDurations[i], false)+" Variability");
				lines.add(topLink); lines.add("");
				
				lines.add("![Fixed Time Plot]("+relativePathToOutputDir+"/"+prefix+".png)");
				lines.add("");
				lines.add("[Download CSV Here]("+relativePathToOutputDir+"/"+prefix+".csv)");
				lines.add("");
				lines.addAll(MarkdownUtils.tableFromCSV(csv, true).build());
				lines.add("");
			}
			
			if (durMinMags.length < 2) {
				lines.add(topLevelHeading+" Variability Duration Dependence");
				lines.add(topLink); lines.add("");
			}
		}
		
		for (int i=0; i<durMinMags.length; i++) {
			double durMag = durMinMags[i];
			if (durMinMags.length > 1) {
				// we have multiple duration dependent plots
				lines.add(topLevelHeading+" M"+optionalDigitDF.format(durMag)+"  Variability Duration Dependence");
				lines.add(topLink); lines.add("");
			}
			
			String prefix = durPrefixes.get(i);
			
			lines.add("![Duration Dependence Plot]("+relativePathToOutputDir+"/"+prefix+".png)");
			lines.add("");
			lines.add("[Download CSV Here]("+relativePathToOutputDir+"/"+prefix+".csv)");
			lines.add("");
			lines.addAll(MarkdownUtils.tableFromCSV(durCSVs.get(i), true).build());
			lines.add("");
		}
		
		return lines;
	}

}
