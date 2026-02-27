package org.opensha.commons.data.comcat.plot;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.data.Range;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;
import org.opensha.commons.calc.FractileCurveCalculator;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotPreferences;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.magComplete.Helmstetter2006_TimeDepMc;
import org.opensha.sha.earthquake.observedEarthquake.magComplete.TimeDepMagComplete;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

public class ComcatDataPlotter {
	
	private ObsEqkRupture mainshock;
	private List<? extends ObsEqkRupture> foreshocks;
	private List<? extends ObsEqkRupture> aftershocks;
	
	private long originTime;
	private long endTime;
	
	final static double MILLISEC_PER_YEAR = 1000*60*60*24*365.25;
	final static long MILLISEC_PER_DAY = 1000*60*60*24;
	
	private static final DecimalFormat optionalDigitDF = new DecimalFormat("0.##");
	
	private EvenlyDiscretizedFunc timeDiscretization;
	private boolean timeDays;
	
	private double modelMc = Double.NEGATIVE_INFINITY;
	
	private Color dataColor = Color.RED;
	private boolean noTitles = false;
	private Double timeFuncMaxY = null;
	private Map<Double, String> timeFuncAnns = null;
	private CPT baseCPT;
	private Double minPlotProb;
	
	private Color foreshockColor = Color.MAGENTA.darker();
	private Color mainshockColor = new Color(86, 44, 0); // brown
	private Color aftershockColor = Color.CYAN.darker();
	
	private static double[] fractiles = {0.025, 0.16, 0.84, 0.975};
	
	// sim-data plot fractile params
	private boolean plotIncludeMean = true;
	private boolean plotIncludeMedian = true;
	private boolean plotIncludeMode = true;
	
	// map plot params
	private boolean mapDrawCA = true;
	
	public ComcatDataPlotter(ObsEqkRupture mainshock, List<? extends ObsEqkRupture> foreshocks,
			List<? extends ObsEqkRupture> aftershocks) {
		this(mainshock, mainshock.getOriginTime(), System.currentTimeMillis(), foreshocks, aftershocks);
	}
	
	public ComcatDataPlotter(long originTime, List<? extends ObsEqkRupture> foreshocks,
			List<? extends ObsEqkRupture> aftershocks) {
		this(null, originTime, System.currentTimeMillis(), foreshocks, aftershocks);
	}
	
	public ComcatDataPlotter(ObsEqkRupture mainshock, long originTime, long endTime,
			List<? extends ObsEqkRupture> foreshocks, List<? extends ObsEqkRupture> aftershocks) {
		this.mainshock = mainshock;
		this.foreshocks = foreshocks;
		this.aftershocks = aftershocks;
		Preconditions.checkArgument(aftershocks != null, "Must supply aftershocks (even if empty)");
		Preconditions.checkArgument(originTime >= Long.MIN_VALUE, "must supply origin time");
		this.originTime = originTime;
		Preconditions.checkArgument(endTime > originTime);
		this.endTime = endTime;
		
		setTimeDays(true);
		
		try {
			baseCPT = GMT_CPT_Files.BLACK_RED_YELLOW_UNIFORM.instance().reverse();
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	/**
	 * Sets if time should be plotted in days (default), otherwise years. Will rebuild time
	 * discretization
	 * @param timeDays
	 */
	public void setTimeDays(boolean timeDays) {
		this.timeDays = timeDays;
		double timeFuncDuration = endTime - originTime;
		if (timeDays)
			timeFuncDuration /= (double)MILLISEC_PER_DAY;
		else
			timeFuncDuration /= MILLISEC_PER_YEAR;
		
		timeDiscretization = new EvenlyDiscretizedFunc(0d, timeFuncDuration, 500);
	}
	
	public boolean isTimeDays() {
		return timeDays;
	}
	
	public void setTimeFuncDiscretization(EvenlyDiscretizedFunc timeDiscretization) {
		this.timeDiscretization = timeDiscretization;
		for (int i=0; i<timeDiscretization.size(); i++)
			timeDiscretization.set(i, 0d);
	}
	
	public EvenlyDiscretizedFunc getTimeFuncDiscretization() {
		return timeDiscretization;
	}
	
	public void setDataColor(Color dataColor) {
		this.dataColor = dataColor;
	}
	
	public Color getDataColor() {
		return dataColor;
	}
	
	public void setNoTitles(boolean noTitles) {
		this.noTitles = noTitles;
	}
	
	public boolean isNoTitles() {
		return noTitles;
	}
	
	public void setModelMc(double modelMc) {
		this.modelMc = modelMc;
	}
	
	public void setTimeFuncMaxY(Double timeFuncMaxY) {
		this.timeFuncMaxY = timeFuncMaxY;
	}
	
	public void setTimeFuncAnns(Map<Double, String> timeFuncAnns) {
		this.timeFuncAnns = timeFuncAnns;
	}
	
	public long getOriginTime() {
		return originTime;
	}
	
	public long getEndTime() {
		return endTime;
	}
	
	public List<? extends ObsEqkRupture> getAftershocks() {
		return aftershocks;
	}
	
	public List<? extends ObsEqkRupture> getForeshocks() {
		return foreshocks;
	}
	
	public ObsEqkRupture getMainshock() {
		return mainshock;
	}
	
	public double getMaxAftershockMag() {
		double max = Double.NEGATIVE_INFINITY;
		for (ObsEqkRupture rup : aftershocks)
			max = Math.max(max, rup.getMag());
		return max;
	}
	
	public void setPlotIncludeMedian(boolean plotIncludeMedian) {
		this.plotIncludeMedian = plotIncludeMedian;
	}
	
	public void setPlotIncludeMean(boolean plotIncludeMean) {
		this.plotIncludeMean = plotIncludeMean;
	}
	
	public void setPlotIncludeMode(boolean plotIncludeMode) {
		this.plotIncludeMode = plotIncludeMode;
	}
	
	public EvenlyDiscretizedFunc calcCumulativeTimeFunc(TimeDepMagComplete tdMc) {
		return calcCumulativeTimeFunc(tdMc, Double.NEGATIVE_INFINITY);
	}
	
	public EvenlyDiscretizedFunc calcCumulativeTimeFunc(double mc) {
		return calcCumulativeTimeFunc(null, mc);
	}
	
	private EvenlyDiscretizedFunc calcCumulativeTimeFunc(TimeDepMagComplete tdMc, double mc) {
		EvenlyDiscretizedFunc comcatCumulativeTimeFunc = new EvenlyDiscretizedFunc(timeDiscretization.getMinX(),
				timeDiscretization.getMaxX(), timeDiscretization.size());
		List<ObsEqkRupture> allRups = new ArrayList<>();
		allRups.addAll(aftershocks);
		if (foreshocks != null)
			allRups.addAll(foreshocks);
		if (mainshock != null)
			allRups.add(mainshock);
		for (ObsEqkRupture rup : allRups) {
			if (rup.getOriginTime() <= originTime)
				continue;
			if ((tdMc != null && !tdMc.isAboveTimeDepMc(rup) ) || rup.getMag() < mc)
				continue;
			int timeIndex = getTimeFuncIndex(rup);
			for (int i=timeIndex; i<comcatCumulativeTimeFunc.size(); i++)
				comcatCumulativeTimeFunc.add(i, 1d);
		}
		long curTime = System.currentTimeMillis();
		if (endTime > curTime) {
			// goes into the future;
			double curX = (double)(curTime - originTime)/MILLISEC_PER_YEAR;
			if (timeDays)
				curX *= 365.25;
			for (int i=0; i<comcatCumulativeTimeFunc.size(); i++)
				if (comcatCumulativeTimeFunc.getX(i) >= curX)
					comcatCumulativeTimeFunc.set(i, Double.NaN);
		}
		return comcatCumulativeTimeFunc;
	}
	
	public int getTimeFuncIndex(ObsEqkRupture rup) {
		double relTime = rup.getOriginTime() - originTime;
		if (timeDays)
			relTime /= MILLISEC_PER_DAY;
		else
			relTime /= MILLISEC_PER_YEAR;
		int timeIndex = timeDiscretization.getClosestXIndex(relTime);
		// this is the closest index, we need the first index that time is <= to
		if (relTime > timeDiscretization.getX(timeIndex))
			timeIndex++;
		Preconditions.checkState(timeIndex == timeDiscretization.size() || relTime <= timeDiscretization.getX(timeIndex));
		return timeIndex;
	}
	
	public void plotTimeFuncPlot(File outputDir, String prefix, double mc) throws IOException {
		plotTimeFuncPlot(outputDir, prefix, "M≥"+optionalDigitDF.format(mc),
				calcCumulativeTimeFunc(mc), null, null);
	}
	
	public void plotTimeFuncPlot(File outputDir, String prefix, TimeDepMagComplete tdMc) throws IOException {
		plotTimeFuncPlot(outputDir, prefix, "M≥Mc(t)", calcCumulativeTimeFunc(tdMc), null, null);
	}
	
	public void plotTimeFuncPlot(File outputDir, String prefix, String magLabel,
			EvenlyDiscretizedFunc comcatCumulativeTimeFunc, Double minTime, FractileCurveCalculator modelCalc)
					throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		String xAxisLabel = timeDays ? "Time (days)" : "Time (years)";
		
		CSVFile<String> csv = buildSimFractalFuncsCSV(modelCalc, funcs, chars,
				comcatCumulativeTimeFunc, xAxisLabel, false);
		csv.writeToFile(new File(outputDir, prefix+".csv"));
		
		if (comcatCumulativeTimeFunc.size() > 0 && Double.isFinite(comcatCumulativeTimeFunc.getY(0))) {
			comcatCumulativeTimeFunc.setName("ComCat Data");
			funcs.add(comcatCumulativeTimeFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, dataColor));
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, noTitles ? " " : "Cumulative Number Comparison, "+magLabel,
				xAxisLabel, "Cumulative Num Earthquakes "+magLabel);
		spec.setLegendVisible(true);
		
		double maxData = comcatCumulativeTimeFunc.getMaxY();
		
		double maxY;
		if (timeFuncMaxY != null) {
			maxY = timeFuncMaxY;
		} else if (modelCalc != null) {
			double maxMean = modelCalc.getMeanCurve().getMaxY();
			double maxUpper = modelCalc.getFractile(0.975).getMaxY();
			
			maxY = Math.max(2*maxData, Math.max(Math.min(2*maxMean, 1.5*maxUpper), 1.25*maxMean));
		} else {
			maxY = 1.3*maxData;
		}
		if (maxY <= 1d || !Double.isFinite(maxY))
			maxY = 1d;
		double maxX = Math.max(1d, comcatCumulativeTimeFunc.getMaxX());
		
		List<XYTextAnnotation> anns = null;
		
		if (modelCalc != null) {
			XY_DataSet meanCurve = modelCalc.getMeanCurve();
			if ((float)meanCurve.getMinX() > 0f) {
				DefaultXY_DataSet dataStartFunc = new DefaultXY_DataSet();
				dataStartFunc.set(meanCurve.getMinX(), 0d);
				dataStartFunc.set(meanCurve.getMinX(), maxY);
				funcs.add(dataStartFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.DARK_GRAY));
				
				anns = new ArrayList<>();
				Font font = new Font(Font.SANS_SERIF, Font.BOLD, 18);
				double annY = 0.975*maxY;
				double annX = meanCurve.getMinX()+0.02*maxX;
				XYTextAnnotation ann = new XYTextAnnotation("Model Start", annX, annY);
				ann.setTextAnchor(TextAnchor.BOTTOM_LEFT);
				ann.setRotationAnchor(TextAnchor.BOTTOM_LEFT);
				ann.setRotationAngle(0.5*Math.PI);
				ann.setFont(font);
				anns.add(ann);
			}
		}
		
		if (timeFuncAnns != null && !timeFuncAnns.isEmpty()) {
			if (anns == null)
				anns = new ArrayList<>();
			Font font = new Font(Font.SANS_SERIF, Font.BOLD, 18);
			for (double xVal : timeFuncAnns.keySet()) {
				String label = timeFuncAnns.get(xVal);
				if (!timeDays)
					xVal /= 365.25;
				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				xy.set(xVal, 0d);
				xy.set(xVal, maxY);
				funcs.add(xy);
				chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.BLACK));
				XYTextAnnotation ann = new XYTextAnnotation(" "+label, xVal, 0.025*maxY);
				ann.setTextAnchor(TextAnchor.BASELINE_LEFT);
				ann.setFont(font);
				anns.add(ann);
			}
		}
		
		if (anns != null)
			spec.setPlotAnnotations(anns);
		
		HeadlessGraphPanel gp = buildGraphPanel();
		gp.setLegendFontSize(18);
//		System.out.println("Drawing with bounds: "+maxX+", "+maxY);
//		System.out.println("start="+originTime+", end="+endTime);
		if (minTime == null)
			minTime = 0d;
		gp.setUserBounds(minTime, maxX, 0d, maxY);

		gp.drawGraphPanel(spec, false, false);
		gp.getChartPanel().setSize(800, 600);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
//		gp.saveAsTXT(new File(outputDir, prefix+".txt").getAbsolutePath());
	}
	
	public void plotTimeDepMcPlot(File outputDir, String prefix, TimeDepMagComplete timeDepMc) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		ArbitrarilyDiscretizedFunc totalFunc = new ArbitrarilyDiscretizedFunc("Time-Dep Mc");
		funcs.add(totalFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
		
		ArbitrarilyDiscretizedFunc mainshockFunc = null;
		List<ArbitrarilyDiscretizedFunc> otherEventFuncs = null;
		List<? extends ObsEqkRupture> mainshocksForTimeDepMc = null;
		double tdMinMag = Double.NaN;
		
		if (timeDepMc instanceof Helmstetter2006_TimeDepMc) {
			mainshockFunc = new ArbitrarilyDiscretizedFunc("Mainshock Mc");
			funcs.add(mainshockFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
			
			Helmstetter2006_TimeDepMc helmMc = (Helmstetter2006_TimeDepMc)timeDepMc;
			
			otherEventFuncs = new ArrayList<>();
			mainshocksForTimeDepMc = helmMc.getMainshocksList();
			for (int i=1; i<mainshocksForTimeDepMc.size(); i++) {
				ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc(i == 1 ? "Other Event Mcs" : null);
				otherEventFuncs.add(func);
				funcs.add(func);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY));
			}
			
			tdMinMag = helmMc.getMinMagThreshold();
			
			ArbitrarilyDiscretizedFunc threshFunc = new ArbitrarilyDiscretizedFunc("Minimum Mc Threshold");
			threshFunc.set(0d, tdMinMag);
			threshFunc.set(timeDiscretization.getMaxX()+0.5*timeDiscretization.getDelta(), tdMinMag);
			funcs.add(threshFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 2f, Color.GRAY));
		}

		for (int i=0; i<timeDiscretization.size(); i++) {
			double x = timeDiscretization.getX(i);
			long time;
			if (timeDays)
				time = originTime + (long)(x*(double)MILLISEC_PER_DAY);
			else
				time = originTime + (long)(x*MILLISEC_PER_YEAR);
			if (mainshocksForTimeDepMc != null ) {
				double maxMc = tdMinMag;
				for (int j=0; j<mainshocksForTimeDepMc.size(); j++) {
					double myMc = Helmstetter2006_TimeDepMc.calcTimeDepMc(mainshocksForTimeDepMc.get(j), time, 0);
					if (Double.isNaN(myMc))
						continue;
					maxMc = Math.max(maxMc, myMc);
					if (j == 0)
						mainshockFunc.set(x, myMc);
					else
						otherEventFuncs.get(j-1).set(x, myMc);
				}
				totalFunc.set(x, maxMc);
			} else {
				totalFunc.set(x, timeDepMc.calcTimeDepMc(time));
			}
		}
		
		String xAxisLabel = timeDays ? "Time (days)" : "Time (years)";
		
		PlotSpec spec = new PlotSpec(funcs, chars, noTitles ? " " : "Time Dependent Mc",
				xAxisLabel, "Magnitude of Completeness");
		spec.setLegendVisible(true);
		
		double maxY = 5d;
		for (ObsEqkRupture rup : mainshocksForTimeDepMc)
			maxY = Math.max(maxY, rup.getMag());
		double minY = 2d;
		
		HeadlessGraphPanel gp = buildGraphPanel();
		gp.setLegendFontSize(18);
		double maxX = Math.max(1d, timeDiscretization.getMaxX());
//		System.out.println("Drawing with bounds: "+maxX+", "+maxY);
		gp.setUserBounds(0d, maxX, minY, maxY);
		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
		gp.drawGraphPanel(spec, false, false);
		gp.getChartPanel().setSize(800, 500);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
//		gp.saveAsTXT(new File(outputDir, prefix+".txt").getAbsolutePath());
	}
	
	public IncrementalMagFreqDist calcIncrementalMagNum(double minMag, boolean includeMainshock,
			boolean includeForeshocks) {
		double deltaMag = 0.1;
		// shift anything like 2.55 to 2.5 for total minX
		double minX = Math.floor(minMag/deltaMag)*deltaMag;
		double maxX = 8.95;
		// make sure the incremental bin is 0.5*delta above minX, e.g. 2.55 for minX=2.5
		double minIncr = minX + 0.5*deltaMag;
		
//		System.out.println("MFD: minMag="+minMag+", minX="+minX+", minIncr="+minIncr);
		
		int numMag = (int)((maxX - minIncr)/deltaMag) + 1;
		IncrementalMagFreqDist incrMND = new IncrementalMagFreqDist(minIncr, numMag, deltaMag);
		double thresholdMag = minMag - 0.5*deltaMag;
		for (ObsEqkRupture rup : aftershocks) {
			double mag = rup.getMag();
			if (mag < thresholdMag)
				continue;
			int magIndex = incrMND.getClosestXIndex(mag);
			incrMND.add(magIndex, 1d);
		}
		if (includeMainshock && mainshock != null) {
			int magIndex = incrMND.getClosestXIndex(mainshock.getMag());
			incrMND.add(magIndex, 1d);
		}
		if (includeForeshocks && foreshocks != null) {
			for (ObsEqkRupture rup : foreshocks) {
				double mag = rup.getMag();
				if (mag < thresholdMag)
					continue;
				int magIndex = incrMND.getClosestXIndex(mag);
				incrMND.add(magIndex, 1d);
			}
		}
		
		return incrMND;
	}
	
	public double calcModalMag(double minMag, boolean includeMainshock, boolean includeForeshocks) {
		IncrementalMagFreqDist incrMND = calcIncrementalMagNum(minMag, includeMainshock, includeForeshocks);
		if (incrMND.calcSumOfY_Vals() == 0d)
			return incrMND.getMinX() - 0.5*incrMND.getDelta();
		return incrMND.getX(incrMND.getXindexForMaxY()) - 0.5*incrMND.getDelta();
	}
	
	public void plotMagNumPlot(File outputDir, String prefix, boolean cumulative, double minMag, double mc,
			boolean includeMainshock, boolean includeForeshocks, FractileCurveCalculator modelCalc)
			throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		IncrementalMagFreqDist incrMND = calcIncrementalMagNum(minMag, includeMainshock, includeForeshocks);
		double minX = incrMND.getMinX()-0.5*incrMND.getDelta();
		double maxX = incrMND.getMaxX()+0.5*incrMND.getDelta();
		
		EvenlyDiscretizedFunc dataMND;
		String yAxisLabel;
		if (cumulative) {
			dataMND = incrMND.getCumRateDistWithOffset();
			yAxisLabel = "Cumulative Number";
		} else {
			dataMND = incrMND;
			yAxisLabel = "Incremental Number";
		}
		
		CSVFile<String> csv = buildSimFractalFuncsCSV(modelCalc, funcs, chars, dataMND, "Magnitude", true);
		csv.writeToFile(new File(outputDir, prefix+".csv"));
		
		dataMND.setName("ComCat Data");
		funcs.add(dataMND);
		chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 4f, dataColor));
		
		MinMaxAveTracker nonZeroRange = new MinMaxAveTracker();
		double maxNonZeroX = 0;
		for (XY_DataSet func : funcs) {
			for (Point2D pt : func) {
				if (pt.getY() > 0) {
					nonZeroRange.addValue(pt.getY());
					maxNonZeroX = Math.max(maxNonZeroX, pt.getX());
				}
			}
		}
		double minY, maxY;
		if (nonZeroRange.getNum() == 0) {
			minY = 0.1;
			maxY = 100;
		} else {
			maxY = Math.pow(10, Math.ceil(Math.log10(nonZeroRange.getMax())));
			minY = Math.pow(10, Math.floor(Math.log10(nonZeroRange.getMin())));
		}
		
//		System.out.println(incrMND);
//		System.out.println(dataMND);
//		System.out.println("minY="+minY);
//		System.out.println("maxY="+maxY);
//		System.out.println("nonZeroRange="+nonZeroRange);
//		System.out.println("times: "+getOriginTime()+" "+getEndTime());
		if (minY >= maxY)
			minY = Math.pow(10, Math.log10(maxY)-1);
		if (minY == 1d)
			minY = 0.9;
		maxX = Math.min(maxX, Math.max(Math.ceil(maxNonZeroX), 5d));
		
		if ((float)mc > (float)minMag) {
			XY_DataSet mcFunc = new DefaultXY_DataSet();
			mcFunc.set(mc, minY);
			mcFunc.set(mc, maxY);
			mcFunc.setName("Mc");
			funcs.add(mcFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, dataColor));
		}
		
		String title = modelCalc == null ? "Magnitude-Number Distribution" : "Magnitude Distribution Comparison";
		PlotSpec spec = new PlotSpec(funcs, chars, noTitles ? " " : title, "Magnitude", yAxisLabel);
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = buildGraphPanel();
		gp.setLegendFontSize(18);
		gp.setUserBounds(minX, maxX, minY, maxY);
		TickUnits tus = new TickUnits();
		TickUnit tu = new NumberTickUnit(0.5);
		tus.add(tu);

		gp.drawGraphPanel(spec, false, true);
		gp.getXAxis().setStandardTickUnits(tus);
		gp.getChartPanel().setSize(800, 600);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
//		gp.saveAsTXT(new File(outputDir, prefix+".txt").getAbsolutePath());
	}
	
	public void plotMagDepthPlot(File outputDir, String prefix, TimeDepMagComplete tdMc) throws IOException {
		plotMagDepthPlot(outputDir, prefix, tdMc, Double.NEGATIVE_INFINITY, null);
	}
	
	public void plotMagDepthPlot(File outputDir, String prefix, double minMag) throws IOException {
		plotMagDepthPlot(outputDir, prefix, null, minMag, null);
	}
	
	public void plotMagDepthPlot(File outputDir, String prefix, TimeDepMagComplete tdMc,
			 double minMag, EvenlyDiscretizedFunc modelFunc) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		List<ObsEqkRupture> filteredAftershocks = new ArrayList<>();
		double maxDepth = 0d;
		for (ObsEqkRupture rup : aftershocks) {
			if (tdMc != null && !tdMc.isAboveTimeDepMc(rup))
				continue;
			if (rup.getMag() >= minMag) {
				filteredAftershocks.add(rup);
				maxDepth = Math.max(maxDepth, rup.getHypocenterLocation().getDepth());
			}
		}
		
		List<ObsEqkRupture> filteredInputs = new ArrayList<>();
		if (foreshocks != null) {
			for (ObsEqkRupture rup : foreshocks) {
				if (tdMc != null && !tdMc.isAboveTimeDepMc(rup))
					continue;
				if (rup.getMag() >= minMag) {
					filteredInputs.add(rup);
					maxDepth = Math.max(maxDepth, rup.getHypocenterLocation().getDepth());
				}
			}
		}
		if (mainshock != null) {
			if ((tdMc != null && tdMc.isAboveTimeDepMc(mainshock)) || mainshock.getMag() >= minMag) {
				filteredInputs.add(mainshock);
				maxDepth = Math.max(maxDepth, mainshock.getHypocenterLocation().getDepth());
			}
		}
		
		EvenlyDiscretizedFunc aftershockFunc;
		if (modelFunc == null)
			aftershockFunc = new EvenlyDiscretizedFunc(1d, (int)Math.ceil(maxDepth), 1d);
		else
			aftershockFunc = new EvenlyDiscretizedFunc(modelFunc.getMinX(), modelFunc.getMaxX(), modelFunc.size());
		
		for (ObsEqkRupture rup : aftershocks)
			aftershockFunc.add(aftershockFunc.getClosestXIndex(rup.getHypocenterLocation().getDepth()), 1d);
		
		EvenlyDiscretizedFunc inputFunc = null;
		if (!filteredInputs.isEmpty()) {
			inputFunc = new EvenlyDiscretizedFunc(aftershockFunc.getMinX(), aftershockFunc.getMaxX(),
					aftershockFunc.size());
			for (ObsEqkRupture rup : filteredInputs)
				inputFunc.add(inputFunc.getClosestXIndex(rup.getHypocenterLocation().getDepth()), 1d);
		}
		
		if (modelFunc != null) {
			XY_DataSet catalogXY = getDepthXY(modelFunc);
			catalogXY.setName(modelFunc.getName() == null ? "Model" : modelFunc.getName());
			funcs.add(catalogXY);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
		}
		
		XY_DataSet dataXY = getDepthXY(aftershockFunc);
		dataXY.setName("ComCat Aftershocks");
		funcs.add(dataXY);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, dataColor));
		
		if (inputFunc != null) {
			XY_DataSet inputXY = getDepthXY(inputFunc);
			inputXY.setName("ComCat Fore/Mainshock(s)");
			funcs.add(inputXY);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.GRAY));
		}
		
		String magLabel;
		if (tdMc != null)
			magLabel = "M≥Mc(t)";
		else if (minMag > 0)
			magLabel = "M≥"+optionalDigitDF.format(minMag);
		else
			magLabel = "All Magnitudes";
		
		PlotSpec spec = new PlotSpec(funcs, chars, noTitles ? " " : "Depth Distribution Comparison, "+magLabel,
				"Fraction", "Depth (km)");
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = buildGraphPanel();
		gp.setLegendFontSize(18);
		gp.setUserBounds(0d, 1d, 0d, aftershockFunc.getMaxX()+0.5*aftershockFunc.getDelta());
		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);

		gp.drawGraphPanel(spec, false, false);
		gp.setyAxisInverted(true);
		gp.getChartPanel().setSize(600, 800);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
	}
	
	private XY_DataSet getDepthXY(EvenlyDiscretizedFunc func) {
		double numRups = func.calcSumOfY_Vals();
		DefaultXY_DataSet xy = new DefaultXY_DataSet();
		if (numRups == 0d) {
			xy.set(0d, 0d);
			xy.set(0d, func.getMaxX()+0.5*func.getDelta());
		} else {
			for (int i=0; i<func.size(); i++) {
				double middle = func.getX(i);
				double top = middle - 0.5*func.getDelta();
				double bottom = middle + 0.5*func.getDelta();
				double num = func.getY(i)/numRups;
				xy.set(num, top);
				xy.set(num, bottom);
			}
		}
		
		return xy;
	}
	
	private static final int saturation_steps = 1;
	private static final boolean linear_mag_time_prob = false;
	
	private static Color saturate(Color c) {
		int r = c.getRed();
		int g = c.getGreen();
		int b = c.getBlue();
		
		for (int i=0; i<saturation_steps; i++) {
			r = (int)(0.5d*(r + 255d)+0.5);
			g = (int)(0.5d*(g + 255d)+0.5);
			b = (int)(0.5d*(b + 255d)+0.5);
		}
		
		return new Color(r, g, b, c.getAlpha());
	}
	
//	private void plotMagTimeFunc(double[][] magTimeProbs, EvenlyDiscretizedFunc magTimeXAxis, String title,
//			File outputDir, String prefix) throws IOException {
	
	public void plotMagTimeFunc(File outputDir, String prefix) throws IOException {
		plotMagTimeFunc(outputDir, prefix, null);
	}
	
	public void plotMagTimeFunc(File outputDir, String prefix, EvenlyDiscrXYZ_DataSet modelXYZ) throws IOException {
		plotMagTimeFunc(outputDir, prefix, "Magnitude vs Time", null, null, modelXYZ);
	}
	
	public void plotMagTimeFunc(File outputDir, String prefix, String title, Double minTime, Double maxTime,
			EvenlyDiscrXYZ_DataSet modelXYZ) throws IOException {
		
		CPT cpt = null;
		if (modelXYZ != null) {
			Color belowColor = new Color(255, 255, 255, 0);
			if (linear_mag_time_prob) {
				double min = 1e-10;
				double max = modelXYZ.getMaxZ();
//				if (max < 0.5)
//					max = 
				max = Math.ceil(max*10d)/10d;
				cpt = baseCPT.rescale(min, max);
				cpt.add(0, new CPTVal(0f, belowColor, (float)min, belowColor));
			} else {
				double minZ;
				if (minPlotProb == null) {
					minZ = 0.01;
					for (int i=0; i<modelXYZ.size(); i++) {
						double z = modelXYZ.get(i);
						if (z > 0)
							minZ = Math.min(minZ, z);
					}
				} else {
					minZ = minPlotProb;
				}
//				= minPlotProb == null ? 1d/catalogsProcessed : this.minZ;
				double logMinProb = Math.floor(Math.log10(minZ));
				double logMaxProb = 0d; //log10
				if (logMinProb < -3) {
					cpt = baseCPT.rescale(-3, logMaxProb);
					cpt.add(0, new CPTVal((float)logMinProb, belowColor, -3, cpt.getMinColor()));
				} else {
					cpt = baseCPT.rescale(logMinProb, logMaxProb);
				}
				modelXYZ = modelXYZ.copy();
				modelXYZ.log10();
			}
			for (CPTVal cVal : cpt) {
				cVal.minColor = saturate(cVal.minColor);
				cVal.maxColor = saturate(cVal.maxColor);
			}
			cpt.setBelowMinColor(belowColor);
			cpt.setNanColor(belowColor);
		}
		
		XY_DataSet aftershocksFunc = null;
		if (!aftershocks.isEmpty()) {
			aftershocksFunc = new DefaultXY_DataSet();
			aftershocksFunc.setName("Observed Aftershocks");
			if (maxTime == null) {
				maxTime = timeDiscretization.getMaxX() + 0.5*timeDiscretization.getDelta();
				if (modelXYZ != null)
					maxTime = Math.max(maxTime, modelXYZ.getMaxX()+0.5*modelXYZ.getGridSpacingX());
			}
			
			for (ObsEqkRupture e : aftershocks) {
				double time = (double)(e.getOriginTime() - originTime)/MILLISEC_PER_YEAR;
				if (timeDays)
					time *= 365.25;
				if (time <= maxTime)
					aftershocksFunc.set(time, e.getMag());
			}
		}
		
		// now input events
		XY_DataSet mainshockFunc = null;
		if (mainshock != null) {
			mainshockFunc = new DefaultXY_DataSet();
			mainshockFunc.setName("Mainshock");
			double time = (double)(mainshock.getOriginTime() - originTime)/MILLISEC_PER_YEAR;
			if (timeDays)
				time *= 365.25;
			mainshockFunc.set(time, mainshock.getMag());
		}
		
		XY_DataSet foreshocksFunc = null;
		if (foreshocks != null && !foreshocks.isEmpty()) {
			foreshocksFunc = new DefaultXY_DataSet();
			String plural = foreshocks.size() > 1 ? "s" : "";
			if (mainshock == null)
				foreshocksFunc.setName("Mainshock"+plural);
			else if (foreshocksFunc.getMaxX() > mainshockFunc.getMaxX())
				foreshocksFunc.setName("Input Event"+plural);
			else
				foreshocksFunc.setName("Foreshock"+plural);
			for (ObsEqkRupture foreshock : foreshocks) {
				double time = (double)(foreshock.getOriginTime() - originTime)/MILLISEC_PER_YEAR;
				if (timeDays)
					time *= 365.25;
				foreshocksFunc.set(time, foreshock.getMag());
			}
		}
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		
		double minDataMag = Double.POSITIVE_INFINITY;
		double maxDataMag = Double.NEGATIVE_INFINITY;
		
		if (aftershocksFunc != null) {
            if (!Double.isNaN(aftershocksFunc.getMinY()))
                minDataMag = Math.min(minDataMag, aftershocksFunc.getMinY());
            if (!Double.isNaN(aftershocksFunc.getMaxY()))
                maxDataMag = Math.max(maxDataMag, aftershocksFunc.getMaxY());
		}
		if (foreshocksFunc != null) {
            if (!Double.isNaN(foreshocksFunc.getMinY()))
                minDataMag = Math.min(minDataMag, foreshocksFunc.getMinY());
            if (!Double.isNaN(foreshocksFunc.getMaxY()))
                maxDataMag = Math.max(maxDataMag, foreshocksFunc.getMaxY());
		}
		if (mainshock != null && !Double.isNaN(mainshock.getMag())) {
			minDataMag = Math.min(minDataMag, mainshock.getMag());
			maxDataMag = Math.max(maxDataMag, mainshock.getMag());
		}
		
		if (minTime == null) {
			minTime = 0d;
			if (foreshocksFunc != null)
				minTime = Math.min(minTime, foreshocksFunc.getMinX()-0.1);
			if (mainshock != null)
				minTime = Math.min(minTime, mainshockFunc.getMinX()-0.1);
		}
		
		Preconditions.checkState(Double.isFinite(minDataMag), "Min data mag is non-finite: %s", minDataMag);
		
		double minMag = Math.floor(minDataMag*10d)/10d;
		double maxMag = Math.ceil(Math.max(maxDataMag, minMag + 3.75d));
		if (modelXYZ != null)
			minMag = Math.min(minMag, modelXYZ.getMinY()-0.5*modelXYZ.getGridSpacingY());
		
		EvenlyDiscretizedFunc magSizeFunc = getMagSizeFunc(minMag);
		
		if (foreshocksFunc != null && foreshocksFunc.size() > 0)
			addGroupedInputFuncs(foreshocksFunc, funcs, chars, foreshockColor, magSizeFunc);
		if (mainshockFunc != null)
			addGroupedInputFuncs(mainshockFunc, funcs, chars, mainshockColor, magSizeFunc);
		if (aftershocksFunc != null)
			addGroupedInputFuncs(aftershocksFunc, funcs, chars, aftershockColor, magSizeFunc);
		
		String xAxisLabel = timeDays ? "Days" : "Years";
		if (mainshock != null)
			xAxisLabel += " Since Mainshock";
		
		TickUnits tus = new TickUnits();
		TickUnit tu = new NumberTickUnit(1d);
		tus.add(tu);
		
		Range xRange = new Range(minTime, maxTime);
		Range yRange = new Range(minMag, maxMag);
		
		List<XYTextAnnotation> anns = null;
		Font annFont = new Font(Font.SANS_SERIF, Font.BOLD, 18);
		PlotCurveCharacterstics markerChar = new PlotCurveCharacterstics(
				PlotLineType.DASHED, 2f, new Color (127, 127, 127, 127));
		double annBuffX = 0.015*xRange.getLength();
		double annBuffY = 0.02*yRange.getLength();
		
		double dataEndTime = (endTime - originTime)/MILLISEC_PER_YEAR;
		double dataDeltaDays;
		if (timeDays) {
			dataEndTime *= 365.25;
			dataDeltaDays = dataEndTime;
		} else {
			dataDeltaDays = dataEndTime*365.25;
		}
		
		if ((float)dataEndTime < maxTime.floatValue() && dataDeltaDays > 1d/24d
				&& endTime <= System.currentTimeMillis()) {
			anns = new ArrayList<>();
			double x = dataEndTime+annBuffX;
			double y = maxMag-annBuffY;
			XYTextAnnotation ann = new XYTextAnnotation("Time Updated", x, y);
			ann.setFont(annFont);
			ann.setTextAnchor(TextAnchor.BOTTOM_LEFT);
			ann.setRotationAnchor(TextAnchor.BOTTOM_LEFT);
			ann.setRotationAngle(0.5*Math.PI);
			anns.add(ann);

			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			xy.set(dataEndTime, minMag);
			xy.set(dataEndTime, maxMag);
			funcs.add(xy);
			chars.add(markerChar);
		}
		
		if (modelXYZ == null) {
			PlotSpec spec = new PlotSpec(funcs, chars, noTitles ? " " : title, xAxisLabel, "Magnitude");
			spec.setLegendVisible(true);
			if (anns != null)
				spec.setPlotAnnotations(anns);
			
			HeadlessGraphPanel gp = buildGraphPanel();
			gp.setLegendFontSize(18);
			gp.setUserBounds(xRange, yRange);

			gp.drawGraphPanel(spec, false, false);
			gp.getYAxis().setStandardTickUnits(tus);
			gp.getChartPanel().setSize(800, 650);
			gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		} else {
			XYZPlotSpec spec = new XYZPlotSpec(modelXYZ, cpt, noTitles ? " " : title, xAxisLabel,
					"Magnitude", "Log10(Probability)");
			
			// add annotations
			double modelStart = modelXYZ.getMinX()-0.5*modelXYZ.getGridSpacingX();
			double modelMinMag = modelXYZ.getMinY()-0.5*modelXYZ.getGridSpacingY();

			if ((float)modelStart > 0.01f) {
				if (anns == null)
					anns = new ArrayList<>();
				double x = modelStart + annBuffX;
				double y = maxMag - annBuffY;
				XYTextAnnotation startAnn = new XYTextAnnotation("Model Start", x, y);
				startAnn.setFont(annFont);
				startAnn.setTextAnchor(TextAnchor.BOTTOM_LEFT);
				startAnn.setRotationAnchor(TextAnchor.BOTTOM_LEFT);
				startAnn.setRotationAngle(0.5*Math.PI);
				anns.add(startAnn);

				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				xy.set(modelStart, minMag);
				xy.set(modelStart, maxMag);
				funcs.add(xy);
				chars.add(markerChar);
			}

			if ((float)modelMinMag > (float)minMag) {
				if (anns == null)
					anns = new ArrayList<>();
				double x = maxTime-annBuffX;
				double y = modelMinMag+annBuffY;
				XYTextAnnotation startAnn = new XYTextAnnotation("Simulation Min Mag", x, y);
				startAnn.setFont(annFont);
				startAnn.setTextAnchor(TextAnchor.BOTTOM_RIGHT);
				anns.add(startAnn);

				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				xy.set(minTime, modelMinMag);
				xy.set(maxTime, modelMinMag);
				funcs.add(xy);
				chars.add(markerChar);
			}
			if (anns != null)
				spec.setPlotAnnotations(anns);
			
			spec.setXYElems(funcs);
			spec.setXYChars(chars);
			
			spec.setCPTPosition(RectangleEdge.BOTTOM);
			HeadlessGraphPanel gp = buildGraphPanel();
			
			gp.drawGraphPanel(spec, false, false, xRange, yRange);
//				gp.getChartPanel().getChart().addSubtitle(slipCPTbar);
			gp.getYAxis().setStandardTickUnits(tus);
			gp.getChartPanel().setSize(800, 650);
			
			gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		}
	}
	
	private EvenlyDiscretizedFunc getMagSizeFunc(double minMag) {
		EvenlyDiscretizedFunc magSizeFunc = HistogramFunction.getEncompassingHistogram(
				minMag, 8.95, 0.1);
		for (int i=0; i<magSizeFunc.size(); i++)
			magSizeFunc.set(i, 1d + 2d*(magSizeFunc.getX(i)-magSizeFunc.getMinX()));
		return magSizeFunc;
	}
	
	private void addGroupedInputFuncs(XY_DataSet func, List<XY_DataSet> funcs,
			List<PlotCurveCharacterstics> chars, Color color, EvenlyDiscretizedFunc magSizeFunc) {
		XY_DataSet[] subFuncs = new XY_DataSet[magSizeFunc.size()];
		for (Point2D pt : func) {
			int magIndex = magSizeFunc.getClosestXIndex(pt.getY());
			if (subFuncs[magIndex] == null)
				subFuncs[magIndex] = new DefaultXY_DataSet();
			subFuncs[magIndex].set(pt);
		}
		boolean first = true;
		Color outlineColor = new Color(0, 0, 0, 127);
		for (int i=magSizeFunc.size(); --i>=0;) {
			if (subFuncs[i] != null) {
				if (first)
					subFuncs[i].setName(func.getName());
				funcs.add(subFuncs[i]);
				chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, (float)magSizeFunc.getY(i), color));
				if (outlineColor != null) {
					if (first) {
						XY_DataSet copy = subFuncs[i].deepClone();
						copy.setName(null);
						funcs.add(copy);
					} else {
						funcs.add(subFuncs[i]);
					}
					chars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, (float)magSizeFunc.getY(i), outlineColor));
				}
				first = false;
			}
		}
	}
	
	private EvenlyDiscretizedFunc getMapMagSizeFunc(double minMag) {
//		EvenlyDiscretizedFunc magSizeFunc = HistogramFunction.getEncompassingHistogram(
//				minMag, 8.95, 0.1);
//		double minSize = 1d;
//		double maxSize = 8d;
//		for (int i=0; i<magSizeFunc.size(); i++)
////			magSizeFunc.set(i, Math.exp(magSizeFunc.getX(i) - Math.exp(magSizeFunc.getMinX())));
//			magSizeFunc.set(i, Math.pow(1.05, magSizeFunc.getX(i)));
////			magSizeFunc.set(i, i == 0 ? 1d : 1.1 * magSizeFunc.getY(i-1));
//		double origMin = magSizeFunc.getY(0);
//		for (int i=0; i<magSizeFunc.size(); i++)
//			magSizeFunc.add(i, -origMin);
//		magSizeFunc.scale((maxSize-minSize)/magSizeFunc.getMaxY());
//		for (int i=0; i<magSizeFunc.size(); i++)
//			magSizeFunc.add(i, minSize);
//		System.out.println(magSizeFunc);
//		return magSizeFunc;
		return getMagSizeFunc(minMag);
	}
	
	private void addMapInputFuncs(List<? extends ObsEqkRupture> ruptures, double minMag, String label, Color color,
			EvenlyDiscretizedFunc magSizeFunc, List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars) {
		// label the largest one
		DefaultXY_DataSet largest = null;
		double maxMag = minMag;
		
		Color outlineColor = new Color(0, 0, 0, 127);
		
		for (ObsEqkRupture rup : ruptures) {
			double mag = rup.getMag();
			if (mag >= minMag) {
				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				if (mag > maxMag) {
					maxMag = mag;
					largest = xy;
				}
				Location loc = rup.getHypocenterLocation();
				xy.set(loc.getLongitude(), loc.getLatitude());
				
				funcs.add(xy);
				float magSize = (float)magSizeFunc.getInterpolatedY(mag);
				chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, magSize, color));
				
				if (outlineColor != null) {
					if (largest == xy) {
						XY_DataSet copy = xy.deepClone();
						copy.setName(null);
						funcs.add(copy);
					} else {
						funcs.add(xy);
					}
					chars.add(new PlotCurveCharacterstics(PlotSymbol.CIRCLE, magSize, outlineColor));
				}
			}
		}
		
		if (largest != null)
			largest.setName(label);
	}
	
	public void plotMap(File outputDir, String prefix, String title, Region mapRegion, double minMag)
			throws IOException {
		List<XY_DataSet> funcs = null;
		List<PlotCurveCharacterstics> chars = null;
		
		plotMap(outputDir, prefix, title, mapRegion, aftershocks, minMag, funcs, chars);
	}
	
	public void plotMap(File outputDir, String prefix, String title, Region mapRegion,
			List<? extends ObsEqkRupture> events, double minMag, List<? extends XY_DataSet> inputFuncs,
			List<PlotCurveCharacterstics> inputChars) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		if (mapDrawCA) {
			for (XY_DataSet caBoundary : PoliticalBoundariesData.loadCAOutlines()) {
				funcs.add(caBoundary);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
			}
		}
		
		if (inputFuncs != null) {
			Preconditions.checkNotNull(inputChars, "must also give input chars");
			funcs.addAll(inputFuncs);
			chars.addAll(inputChars);
		}
		
		EvenlyDiscretizedFunc magSizeFunc = getMapMagSizeFunc(minMag);
		if ((foreshocks != null && !foreshocks.isEmpty()) || mainshock != null) {
			if (foreshocks != null && !foreshocks.isEmpty())
				addMapInputFuncs(foreshocks, minMag, "Foreshocks", foreshockColor, magSizeFunc, funcs, chars);
			
			if (mainshock != null) {
				List<ObsEqkRupture> rups = new ArrayList<>();
				rups.add(mainshock);
				
				addMapInputFuncs(rups, minMag, "Mainshock", mainshockColor, magSizeFunc, funcs, chars);
			}
		}
		
		addMapInputFuncs(events, minMag, "Aftershocks", aftershockColor, magSizeFunc, funcs, chars);
		
		double latSpan = mapRegion.getMaxLat() - mapRegion.getMinLat();
		double lonSpan = mapRegion.getMaxLon() - mapRegion.getMinLon();
		
		double tickUnit;
		if (lonSpan > 5)
			tickUnit = 1d;
		else if (lonSpan > 2)
			tickUnit = 0.5;
		else if (lonSpan > 1)
			tickUnit = 0.25;
		else
			tickUnit = 0.1;
		
		TickUnits tus = new TickUnits();
		TickUnit tu = new NumberTickUnit(tickUnit);
		tus.add(tu);
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Longitude", "Latitude");
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = buildGraphPanel();
		gp.setUserBounds(mapRegion.getMinLon(), mapRegion.getMaxLon(), mapRegion.getMinLat(), mapRegion.getMaxLat());

		gp.drawGraphPanel(spec, false, false);
		gp.getXAxis().setStandardTickUnits(tus);
		gp.getYAxis().setStandardTickUnits(tus);
		
		int width = 800;
		int height = (int)((double)(width)*latSpan/lonSpan);
		
		gp.getChartPanel().setSize(width, height);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
	}
	
	private static HeadlessGraphPanel buildGraphPanel() {
		PlotPreferences plotPrefs = PlotPreferences.getDefault();
		plotPrefs.setTickLabelFontSize(20);
		plotPrefs.setAxisLabelFontSize(22);
		plotPrefs.setPlotLabelFontSize(24);
		plotPrefs.setLegendFontSize(20);
		plotPrefs.setBackgroundColor(Color.WHITE);
		return new HeadlessGraphPanel(plotPrefs);
	}
	
	private CSVFile<String> buildSimFractalFuncsCSV(FractileCurveCalculator calc, List<XY_DataSet> funcs,
			List<PlotCurveCharacterstics> chars, XY_DataSet dataFunc, String xAxisName, boolean magX) {
		XY_DataSet minCurve = null;
		XY_DataSet maxCurve = null;
		List<XY_DataSet> fractileFuncs = null;
		XY_DataSet modeFunc = null;
		XY_DataSet medianFunc = null;
		XY_DataSet meanFunc = null;
		
		CSVFile<String> csv = new CSVFile<>(true);
		List<String> header = new ArrayList<>();
		header.add(xAxisName);
		header.add("ComCat Data");
		
		int dataOffset = 0;
		int simOffset = 0;
		int maxIndex = dataFunc.size()-1;
		if (calc != null) {
			minCurve = notAsIncr(calc.getMinimumCurve());
			minCurve.setName("Extrema");
			maxCurve = notAsIncr(calc.getMaximumCurve());
			maxCurve.setName(null);
			
			if (magX) {
				minCurve = getAboveModelMc(minCurve);
				maxCurve = getAboveModelMc(maxCurve);
			}
			funcs.add(minCurve);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.BLACK));

			funcs.add(maxCurve);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.BLACK));
			
			String fractileStr = null;
			fractileFuncs = new ArrayList<>();
			for (double fractile : fractiles) {
				if (fractileStr == null)
					fractileStr = "";
				else
					fractileStr += ",";
				fractileStr += "p"+optionalDigitDF.format(fractile*100);
				fractileFuncs.add(notAsIncr(calc.getFractile(fractile)));
			}
			
			for (int i=0; i<fractileFuncs.size(); i++) {
				XY_DataSet func = fractileFuncs.get(i);
				if (i==0)
					func.setName(fractileStr);
				else
					func.setName(null);
				if (magX)
					func = getAboveModelMc(func);
				funcs.add(func);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
			}

			modeFunc = new ArbitrarilyDiscretizedFunc();
			for (int i=0; i<minCurve.size(); i++)
				modeFunc.set(minCurve.getX(i), calc.getEmpiricalDist(i).getMostCentralMode());
			if (plotIncludeMode && magX) {
				modeFunc.setName("Mode");
				if (magX)
					modeFunc = getAboveModelMc(modeFunc);
				funcs.add(modeFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.CYAN.darker()));
			}

			medianFunc = calc.getFractile(0.5);
			if (plotIncludeMedian) {
				medianFunc.setName("Median");
				if (magX)
					medianFunc = getAboveModelMc(medianFunc);
				funcs.add(medianFunc);
				if (plotIncludeMean)
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
				else
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
			}
			
			meanFunc = calc.getMeanCurve();
			if (plotIncludeMean) {
				meanFunc.setName("Mean");
				if (magX)
					meanFunc = getAboveModelMc(meanFunc);
				funcs.add(meanFunc);
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
			}
			header.add("Simulation Mean");
			header.add("Simulation Median");
			header.add("Simulation Mode");
			header.add("Simulation Minimum");
			header.add("Simulation Maximum");
			for (double fractile : fractiles)
				header.add(optionalDigitDF.format(fractile*100d)+" %-ile");
			
			if ((float)dataFunc.getX(0) != (float)minCurve.getX(0)) {
				// fix offset
				if ((float)dataFunc.getX(0) < (float)minCurve.getX(0)) {
					// data starts lower
					while ((float)dataFunc.getX(-simOffset) < (float)minCurve.getX(0)
							&& -simOffset < dataFunc.size())
						simOffset--;
					Preconditions.checkState(-simOffset < dataFunc.size(),
							"No overlap between data and sim funcs");
					maxIndex = Integer.max(dataFunc.size(), minCurve.size()-simOffset)-1;
				} else {
					// sim starts lower
					while ((float)dataFunc.getX(0) > (float)minCurve.getX(-dataOffset)
							&& -dataOffset < minCurve.size())
						dataOffset--;
					Preconditions.checkState(-dataOffset < minCurve.size(),
							"No overlap between data and sim funcs");
					maxIndex = Integer.max(minCurve.size(), dataFunc.size()-dataOffset)-1;
				}
//				System.out.println("Data func: start="+dataFunc.getX(0)
//					+"\tsize="+dataFunc.size()+"\toffset="+dataOffset);
//				System.out.println("Sim func: start="+minCurve.getX(0)
//					+"\tsize="+minCurve.size()+"\toffset="+simOffset);
//				System.out.println("MaxIndex="+maxIndex);
			} else {
				maxIndex = Math.max(minCurve.size(), dataFunc.size())-1;
			}
		}
		
		csv.addLine(header);
		
		for (int i=0; i<=maxIndex; i++) {
			List<String> line = new ArrayList<>();
			int dataI = i+dataOffset;
			if (dataI >= 0 && dataI<dataFunc.size()) {
				line.add((float)dataFunc.getX(dataI)+"");
				line.add((float)dataFunc.getY(dataI)+"");
			} else {
				line.add("");
				line.add("");
			}
			if (calc != null) {
				int modelI = i+simOffset;
				if (modelI < 0 || modelI >= meanFunc.size()
						|| (magX && (float)meanFunc.getX(modelI) < (float)modelMc)) {
					while (line.size() < header.size())
						line.add("");
				} else {
					line.add((float)meanFunc.getY(modelI)+"");
					line.add((float)medianFunc.getY(modelI)+"");
					line.add((float)modeFunc.getY(modelI)+"");
					line.add((float)minCurve.getY(modelI)+"");
					line.add((float)maxCurve.getY(modelI)+"");
					for (XY_DataSet fractileFunc : fractileFuncs)
						line.add((float)fractileFunc.getY(modelI)+"");
				}
			}
			csv.addLine(line);
		}
		return csv;
	}
	
	private XY_DataSet notAsIncr(XY_DataSet xy) {
		if (xy instanceof IncrementalMagFreqDist) {
			EvenlyDiscretizedFunc func =
					new EvenlyDiscretizedFunc(xy.getMinX(), xy.getMaxX(), xy.size());
			for (int i=0; i<func.size(); i++)
				func.set(i, xy.getY(i));
			return func;
		}
		return xy;
	}
	
	private XY_DataSet getAboveModelMc(XY_DataSet func) {
		if ((float)func.getMinX() < (float)modelMc) {
			XY_DataSet trimmed = new DefaultXY_DataSet();
			trimmed.setName(func.getName());
			for (Point2D pt : func) {
				if ((float)pt.getX() < (float)modelMc)
					continue;
				trimmed.set(pt);
			}
			return trimmed;
		}
		return func;
	}

}
