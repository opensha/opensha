package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;

import org.apache.commons.math3.stat.StatUtils;
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
import org.opensha.commons.data.comcat.ComcatAccessor;
import org.opensha.commons.data.comcat.ComcatRegion;
import org.opensha.commons.data.comcat.ComcatRegionAdapter;
import org.opensha.commons.data.comcat.plot.ComcatDataPlotter;
import org.opensha.commons.data.function.AbstractXY_DataSet;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.UncertainArbDiscDataset;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZGraphPanel;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.magComplete.Helmstetter2006_TimeDepMc;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_CubeDiscretizationParams;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_Utils;
import scratch.UCERF3.erf.ETAS.analysis.SimulationMarkdownGenerator.PlotResult;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.ComcatMetadata;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class ETAS_ComcatComparePlot extends ETAS_AbstractPlot {
	
	private double minSpan;
	private Region mapRegion;
	private GriddedRegion gridRegion;
	private long fetchEndTime;
	private ObsEqkRupList comcatEvents;
	private double modalMag;
	private double comcatMc;
	private double comcatMinMag;
	private double comcatMaxMag;
	private double simMc;
	private double overallMc;
	private boolean smallSequence; // if true, will plot data below M2.5
	
	// for T-D Mc
	private static final double CISN_MC = 2.3;
	private static double MAX_MAG_FOR_TD_MC = 5;
	private Helmstetter2006_TimeDepMc timeDepMc;
	
	private double innerBoxMinLat;
	private double innerBoxMaxLat;
	private double innerBoxMinLon;
	private double innerBoxMaxLon;
	
	private boolean timeDays;
	
	private double[] magBins;
	
	private int catalogsProcessed = 0;
	
	// need full distributions only for median
	private List<short[][][]> catalogDurMagGridCounts;
	
	private String[][] mapProbPrefixes;
	private String[][] mapMeanPrefixes;
	private String[][] mapMedianPrefixes;
	private double[][] catalogProbsSummary;
	private double[][][] catalogProbs;
	private double[][] catalogMeansSummary;
	private double[][][] catalogMeans;
	private double[][][] catalogMedians;
	private int[][] comcatCountsSummary;
	
	// mag-time funcs
	private EvenlyDiscretizedFunc magTimeYAxis;
	private double magTimeXAxisFullMax;
	private EvenlyDiscretizedFunc magTimeXAxisFull;
	private double magTimeXAxisWeekMax;
	private EvenlyDiscretizedFunc magTimeXAxisWeek;
	private double magTimeXAxisMonthMax;
	private EvenlyDiscretizedFunc magTimeXAxisMonth;
	private double[][] magTimeProbsFull;
	private double[][] magTimeProbsWeek;
	private double[][] magTimeProbsMonth;
	
	private List<IncrementalMagFreqDist> catalogRegionMFDs;
	private double[] timeFuncMcs;
	private List<EvenlyDiscretizedFunc[]> catalogTimeFuncs;
	private EvenlyDiscretizedFunc[] catalogDepthDistributions;
	private HistogramFunction totalCountHist;
	
	private ComcatDataPlotter plotter;
	
	private static double[] default_durations = { 1d / 365.25, 7d / 365.25, 30 / 365.25, 1d };

	private static final boolean map_plot_probs = true;
	private static final boolean map_plot_means = true;
	private static final boolean map_plot_medians = false;
	
	private double[] durations;
	private long[] maxOTs;
	private long startTime;
	private long curTime;
	private double curDuration;
	
	// plot settings
	private CPT baseCPT = null;
	private Double minZ = null;
	private Color mapDataColor = Color.CYAN;
	private static final int cpt_discretizations = 4;

	public ETAS_ComcatComparePlot(ETAS_Config config, ETAS_Launcher launcher) {
		this(config, launcher, null);
	}

	public ETAS_ComcatComparePlot(ETAS_Config config, ETAS_Launcher launcher, ObsEqkRupList inputEvents) {
		super(config, launcher);
		ComcatMetadata comcatMeta = config.getComcatMetadata();
		Preconditions.checkNotNull(comcatMeta, "Must have ComCat metadata for ComCat Plot");
		
		mapRegion = ETAS_EventMapPlotUtils.getMapRegion(config, launcher);
		minSpan = Math.min(mapRegion.getMaxLat()-mapRegion.getMinLat(), mapRegion.getMaxLon()-mapRegion.getMinLon());
		double spacing;
		if (minSpan > 4)
			spacing = 0.1;
		else if (minSpan > 2)
			spacing = 0.05;
		else
			spacing = config.getGridSeisDiscr()/(double)ETAS_CubeDiscretizationParams.DEFAULT_NUM_PT_SRC_SUB_PTS;
		System.out.println("ComCat map grid spacing: "+(float)spacing+" for minSpan="+(float)minSpan);
//		Location anchor = new Location(spacing/2d, spacing/2d);
		Location anchor = GriddedRegion.ANCHOR_0_0;
		gridRegion = new GriddedRegion(mapRegion, spacing, anchor);
		
		startTime = config.getSimulationStartTimeMillis();
		curTime = System.currentTimeMillis();
		Preconditions.checkState(curTime > startTime, "Simulation starts in the future?");
		curDuration = (curTime-startTime) / ProbabilityModelsCalc.MILLISEC_PER_YEAR;
		System.out.println("Current duration from start time: "+(float)curDuration);
		List<Double> durationsList = new ArrayList<>();
		for (double duration : default_durations)
			if (duration < config.getDuration())
				durationsList.add(duration);
		if (curDuration < config.getDuration())
			durationsList.add(curDuration);
		else
			durationsList.add(config.getDuration());
		Collections.sort(durationsList);
		durations = Doubles.toArray(durationsList);
		maxOTs = new long[durations.length];
		for (int i=0; i<durations.length; i++)
			maxOTs[i] = startTime + (long)(ProbabilityModelsCalc.MILLISEC_PER_YEAR*durations[i]+0.5);
		System.out.println("Max comcat compare duration: "+(float)durations[durations.length-1]);
		
		if (inputEvents != null) {
			fetchEndTime = System.currentTimeMillis();
			this.comcatEvents = inputEvents;
		} else {
			fetchEndTime = Long.min(curTime, maxOTs[maxOTs.length-1]);
			try {
				comcatEvents = loadComcatEvents(config, comcatMeta, mapRegion, fetchEndTime);
			} catch (Exception e) {
				System.err.println("Error fetching ComCat events, skipping");
				e.printStackTrace();
				comcatEvents = new ObsEqkRupList();
				return;
			}
		}
		if (comcatEvents.isEmpty()) {
			System.out.println("No ComCat events found");
			return;
		}
		// see if it's a small sequence
		smallSequence = false;
		double minMag = Double.POSITIVE_INFINITY;
		for (ObsEqkRupture comcatEvent : comcatEvents) {
			if (comcatEvent.getMag() < comcatMeta.minMag)
				smallSequence = true;
			minMag = Math.min(comcatEvent.getMag(), minMag);
		}
		System.out.println("Min ComCat mag: "+minMag);
	}
	
	public static boolean isSmallSequence(ETAS_Config config) {
		List<TriggerRupture> triggers = config.getTriggerRuptures();
		if (triggers == null)
			return false;
		for (TriggerRupture trigger : triggers) {
			Double mag = trigger.getMag(null);
			if (mag == null)
				// FSS rupture, must be big
				return false;
			else if (mag >= 6d)
				return false;
		}
		return true;
	}
	
	public synchronized ComcatDataPlotter getPlotter() {
		if (plotter == null)
			init();
		return plotter;
	}
	
	public double[] getDurations() {
		return durations;
	}
	
	public double getCurDuration() {
		return curDuration;
	}
	
	public double[] getMinMags() {
		return magBins;
	}
	
	public String[][] getMapProbPrefixes() {
		return mapProbPrefixes;
	}
	
	public static ObsEqkRupList loadComcatEvents(ETAS_Config config, ComcatMetadata comcatMeta, Region mapRegion, long endTime) {
		ComcatAccessor accessor = new ComcatAccessor();
		ComcatRegion cReg = mapRegion instanceof ComcatRegion ? (ComcatRegion)mapRegion : new ComcatRegionAdapter(mapRegion);
		boolean smallSequence = isSmallSequence(config);
		if (smallSequence)
			System.out.println("Small sequence, will plot below minMag");
		double minMag = smallSequence ? 0d : comcatMeta.minMag;
		return accessor.fetchEventList(comcatMeta.eventID, config.getSimulationStartTimeMillis(), endTime,
				comcatMeta.minDepth, comcatMeta.maxDepth, cReg, false, false, minMag);
	}
	
	private void init() {
		ETAS_Config config = getConfig();
		ETAS_Launcher launcher = getLauncher();
		ComcatMetadata comcatMeta = config.getComcatMetadata();
		
		double maxTriggerMag = Double.NEGATIVE_INFINITY;
		ETAS_EqkRupture maxMainshock = null;
		List<ETAS_EqkRupture> inputRups = new ArrayList<>();
		ETAS_EqkRupture inputMS = null;
		for (ETAS_EqkRupture rup : launcher.getTriggerRuptures()) {
			if (rup.getMag() > maxTriggerMag) {
				maxMainshock = rup;
				maxTriggerMag = rup.getMag();
			}
			if (rup.getEventId() != null && rup.getEventId().equals(comcatMeta.eventID))
				inputMS = rup;
			else
				inputRups.add(rup);
		}
		long plotOT = inputMS == null ? startTime : inputMS.getOriginTime();
		long plotET = Long.max(curTime, startTime + 7l*ProbabilityModelsCalc.MILLISEC_PER_DAY);
		plotter = new ComcatDataPlotter(inputMS, plotOT, plotET, inputRups, comcatEvents);
		
		if (smallSequence)
			comcatMinMag = 0.05;
		else
			comcatMinMag = ETAS_Utils.magMin_DEFAULT;
		
		comcatMaxMag = plotter.getMaxAftershockMag();
		modalMag = plotter.calcModalMag(comcatMinMag, false, false);
		System.out.println("Found "+comcatEvents.size()+" ComCat events in region. Max mag: "
				+(float)comcatMaxMag+". Modal mag: "+(float)modalMag);
		if (comcatMeta.magComplete == null) {
			System.out.println("Using default Mc = modalMag + 0.5");
			comcatMc = modalMag + 0.5;
		} else {
			System.out.println("Using supplied ComCat Mc");
			comcatMc = comcatMeta.magComplete;
		}
		System.out.println("Mc="+(float)comcatMc);
		timeDays = plotter.isTimeDays();
		// time between the simulation start time and mainshock time, in days
		double maxCurTimeFunc = (config.getSimulationStartTimeMillis() - plotOT)/ProbabilityModelsCalc.MILLISEC_PER_YEAR;
		if (maxCurTimeFunc > 1d/(24d*60d)) {
			// override the time func so that the a point lands directly on the simulation start time
			// this is important, as we grab the value at this point as the startY for the simulated
			// distributions
			double maxPlotTimeFunc = (plotET - plotOT)/ProbabilityModelsCalc.MILLISEC_PER_YEAR;
			if (timeDays) {
				maxCurTimeFunc *= 365.25;
				maxPlotTimeFunc *= 365.25;
			}
			double origDelta = maxPlotTimeFunc/500d;
//			System.out.println("Original delta: "+origDelta);
			int partialNum = (int)Math.ceil(maxCurTimeFunc/origDelta);
//			System.out.println("numForPartial: "+partialNum);
			partialNum = Math.max(partialNum, 5);
			EvenlyDiscretizedFunc timeFunc = new EvenlyDiscretizedFunc(0d, maxCurTimeFunc, partialNum);
//			System.out.println("First past discr: "+timeFunc.size());
//			// make sure discretization is at least 30s
//			while (timeFunc.getDelta() < 30d/(24d*60d*60d) && timeFunc.size() >= 50)
//				timeFunc = new EvenlyDiscretizedFunc(0d, maxCurTimeFunc, timeFunc.size()/2);
//			System.out.println("Mod past discr: "+timeFunc.size());
			int extraNum = (int)Math.round((maxPlotTimeFunc - maxCurTimeFunc)/timeFunc.getDelta());
//			System.out.println("ETRA NUM: "+extraNum+" for maxCur="+maxCurTimeFunc+", maxPlot="+maxPlotTimeFunc+", delta="+timeFunc.getDelta());
			timeFunc = new EvenlyDiscretizedFunc(0d, timeFunc.size()+extraNum, timeFunc.getDelta());
//			System.exit(0);
			
			plotter.setTimeFuncDiscretization(timeFunc);
		}
		System.out.println("Time function has "+plotter.getTimeFuncDiscretization().size()+" points");
		
		List<ObsEqkRupture> mainshocksForTimeDepMc = new ArrayList<>();
		mainshocksForTimeDepMc.add(maxMainshock);
		for (ObsEqkRupture rup : comcatEvents)
			if (rup.getMag() >= MAX_MAG_FOR_TD_MC)
				mainshocksForTimeDepMc.add(rup);
		for (ETAS_EqkRupture rup : launcher.getTriggerRuptures())
			if (rup != maxMainshock && rup.getMag() >= MAX_MAG_FOR_TD_MC)
				mainshocksForTimeDepMc.add(rup);
		timeDepMc = new Helmstetter2006_TimeDepMc(mainshocksForTimeDepMc, Math.max(ETAS_Utils.magMin_DEFAULT, CISN_MC));
		
		double maxMag = Math.max(maxTriggerMag, comcatMaxMag);
		double maxTestMag = Math.ceil(maxMag);
		double magDelta = maxMag < 6 ? 0.5 : 1;
		if (maxTestMag - maxMag < 0.5 && maxTestMag < 8)
			maxTestMag += magDelta;
		List<Double> mags = new ArrayList<>();
		mags.add(-1d); // time-dep Mc
		double minMag = Math.max(ETAS_Utils.magMin_DEFAULT, comcatMc);
		if (minMag < Math.ceil(minMag))
			mags.add(minMag);
		minMag = Math.ceil(minMag); // now start bins at a round number
		for (double mag=minMag; (float)mag <= (float)maxTestMag; mag += magDelta)
			mags.add(mag);
		System.out.println("Magnitudes for ComCat comparison: "+Joiner.on(", ").join(mags));
		magBins = Doubles.toArray(mags);
		// now mags for cumultative time func comparison
		List<Double> timeMags = new ArrayList<>(mags);
		double maxTimeFuncMag = Math.ceil(Math.max(comcatMc, comcatMaxMag)+1);
		for (int m=timeMags.size(); --m>=1;)
			if (timeMags.get(m).floatValue() > (float)maxTimeFuncMag)
				timeMags.remove(m);
		timeFuncMcs = Doubles.toArray(timeMags);
		
		if (map_plot_medians)
			catalogDurMagGridCounts = new ArrayList<>();
		if (map_plot_probs) {
			catalogProbs = new double[durations.length][magBins.length][gridRegion.getNodeCount()];
			catalogProbsSummary = new double[durations.length][magBins.length];
		}
		if (map_plot_means) {
			catalogMeans = new double[durations.length][magBins.length][gridRegion.getNodeCount()];
			catalogMeansSummary = new double[durations.length][magBins.length];
		}
		catalogRegionMFDs = new ArrayList<>();
		catalogTimeFuncs = new ArrayList<>();
		
		totalCountHist = new HistogramFunction(ETAS_MFD_Plot.mfdMinMag, ETAS_MFD_Plot.mfdNumMag, ETAS_MFD_Plot.mfdDelta);
		
		catalogDepthDistributions = new EvenlyDiscretizedFunc[magBins.length];
		double minDepthBin = 0.5;
		double deltaDepth = 1d;
		int numDepth = (int)Math.ceil(ETAS_CubeDiscretizationParams.DEFAULT_MAX_DEPTH);
		for (int i=0; i<magBins.length; i++)
			catalogDepthDistributions[i] = new EvenlyDiscretizedFunc(minDepthBin, numDepth, deltaDepth);
		
		// set up mag/time dists
		// mag bins are 0.5
		magTimeYAxis = new EvenlyDiscretizedFunc(0.25+totalCountHist.getMinX()-0.5*totalCountHist.getDelta(), 13, 0.5);
		if (timeDays) {
			double delta = curDuration*365.25/15d;
			magTimeXAxisFull = new EvenlyDiscretizedFunc(0.5*delta, 15, delta);
			magTimeXAxisWeek = new EvenlyDiscretizedFunc(0.25, 14, 0.5);
			magTimeXAxisMonth = new EvenlyDiscretizedFunc(1d, 15, 2d);
		} else {
			double delta = curDuration/15d;
			magTimeXAxisFull = new EvenlyDiscretizedFunc(0.5*delta, 15, delta);
			double yearScalar = 1d/365.25;
			magTimeXAxisWeek = new EvenlyDiscretizedFunc(yearScalar*0.25, 14, yearScalar*0.5);
			magTimeXAxisMonth = new EvenlyDiscretizedFunc(yearScalar*1d, 15, yearScalar*2d);
		}
		magTimeProbsFull = new double[magTimeXAxisFull.size()][magTimeYAxis.size()];
		magTimeXAxisFullMax = magTimeXAxisFull.getMaxX() + 0.5*magTimeXAxisFull.getDelta();
		magTimeProbsWeek = new double[magTimeXAxisWeek.size()][magTimeYAxis.size()];
		magTimeXAxisWeekMax = magTimeXAxisWeek.getMaxX() + 0.5*magTimeXAxisWeek.getDelta();
		magTimeProbsMonth = new double[magTimeXAxisMonth.size()][magTimeYAxis.size()];
		magTimeXAxisMonthMax = magTimeXAxisMonth.getMaxX() + 0.5*magTimeXAxisMonth.getDelta();
		
		// figure out the largest rectangle that fits inside the region
		// that will allow quickContains to work
		innerBoxMinLat = mapRegion.getMinLat();
		innerBoxMaxLat = mapRegion.getMaxLat();
		innerBoxMinLon = mapRegion.getMinLon();
		innerBoxMaxLon = mapRegion.getMaxLon();
		if (!mapRegion.isRectangular()) {
			System.out.println("Searching for rectangular region inside of irregular ComCat region for faster contains tests");
			System.out.println("Outer box: ["+(float)innerBoxMinLat+" "+(float)innerBoxMaxLat+"], ["
					+(float)innerBoxMinLon+" "+(float)innerBoxMaxLon+"]");
			double gridSpacing = minSpan/100d;
			int nx = (int)((innerBoxMaxLon - innerBoxMinLon)/gridSpacing)+1;
			int ny = (int)((innerBoxMaxLat - innerBoxMinLat)/gridSpacing)+1;
			
			EvenlyDiscrXYZ_DataSet xyz = new EvenlyDiscrXYZ_DataSet(nx, ny, innerBoxMinLon, innerBoxMinLat, gridSpacing);
			int centerX = (int)Math.round((double)nx*0.5d);
			int centerY = (int)Math.round((double)ny*0.5d);
			double centerLat = xyz.getY(centerY);
			double centerLon = xyz.getX(centerX);
			System.out.println("Center point: at "+centerY+","+centerX+": "+(float)centerLat+", "+(float)centerLon
					+". Contains? "+mapRegion.contains(new Location(centerLat, centerLon)));
			System.out.println("spacing: "+(float)gridSpacing+", nx="+nx+", ny="+ny);
			
			int squareAdd = 0;
			while (true) {
				int add = squareAdd+1;
				boolean inside = true;
				for (int x=centerX-add; inside && x<=centerX+add; x++) {
					if (x < 0 || x == nx) {
						inside = false;
						break;
					}
					double lon = xyz.getX(x);
					for (int y=centerY-add; inside && y<=centerY+add; y++) {
						if (y < 0 || y == ny) {
							inside = false;
							break;
						}
						double lat = xyz.getY(y);
						inside = inside && mapRegion.contains(new Location(lat, lon));
					}
				}
				if (inside)
					squareAdd++;
				else
					break;
			}
			System.out.println("SquareAdd="+squareAdd+", "+(float)(squareAdd*2d*gridSpacing)+" degrees");
			// see if we can go further in any direction
			int minY = centerY - squareAdd;
			int maxY = centerY + squareAdd;
			int minX = centerX - squareAdd;
			int maxX = centerX + squareAdd;
			while (maxY+1 < ny) {
				double lat = xyz.getY(maxY+1);
				boolean inside = true;
				for (int x=minX; inside && x<=maxX; x++) {
					double lon = xyz.getX(x);
					inside = inside && mapRegion.contains(new Location(lat, lon));
				}
				if (inside)
					maxY++;
				else
					break;
			}
			while (minY > 0) {
				double lat = xyz.getY(minY-1);
				boolean inside = true;
				for (int x=minX; inside && x<=maxX; x++) {
					double lon = xyz.getX(x);
					inside = inside && mapRegion.contains(new Location(lat, lon));
				}
				if (inside)
					minY--;
				else
					break;
			}
			while (maxX+1 < nx) {
				double lon = xyz.getX(maxX+1);
				boolean inside = true;
				for (int y=minY; inside && y<=maxY; y++) {
					double lat = xyz.getY(y);
					inside = inside && mapRegion.contains(new Location(lat, lon));
				}
				if (inside)
					maxX++;
				else
					break;
			}
			while (minX > 0) {
				double lon = xyz.getX(minX-1);
				boolean inside = true;
				for (int y=minY; inside && y<=maxY; y++) {
					double lat = xyz.getY(y);
					inside = inside && mapRegion.contains(new Location(lat, lon));
				}
				if (inside)
					minX--;
				else
					break;
			}
			if (maxX > minX && maxY > minY) {
				innerBoxMinLat = xyz.getY(minY);
				innerBoxMaxLat = xyz.getY(maxY);
				innerBoxMinLon = xyz.getX(minX);
				innerBoxMaxLon = xyz.getX(maxX);
				System.out.println("Inner box: ["+(float)innerBoxMinLat+" "+(float)innerBoxMaxLat+"], ["
						+(float)innerBoxMinLon+" "+(float)innerBoxMaxLon+"]");
			} else {
				innerBoxMinLat = Double.NaN;
				innerBoxMaxLat = Double.NaN;
				innerBoxMinLon = Double.NaN;
				innerBoxMaxLon = Double.NaN;
			}
		}
		
		try {
			baseCPT = GMT_CPT_Files.BLACK_RED_YELLOW_UNIFORM.instance().reverse();
		} catch (IOException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}

	@Override
	public boolean isFilterSpontaneous() {
		return false; // comparing against the real earth, so include spontaneous events if available
	}

	@Override
	protected boolean isProcessAsync() {
		return true;
	}
	
	private static final int VERSION = 19;

	@Override
	public int getVersion() {
		return VERSION;
	}
	
	@Override
	public boolean isEvaluationPlot() {
		return true;
	}

	@Override
	public boolean shouldReplot(PlotResult prevResult) {
		return !comcatEvents.isEmpty() && shouldReplot(prevResult, getConfig());
	}
	
	public static boolean shouldReplot(PlotResult prevResult, ETAS_Config config) {
		if (shouldReplot(prevResult, VERSION))
			return true;
		long curTime = System.currentTimeMillis();
		double mpd = (double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
		long millisSinceUpdate = curTime - prevResult.time;
		double daysSinceUpdate = (double)millisSinceUpdate/mpd;
		long millisSinceStartTime = curTime - config.getSimulationStartTimeMillis();
		double daysSinceStartTime = (double)millisSinceStartTime/mpd;
		double threshold;
		if (daysSinceStartTime < 1d)
			threshold = 1d/48d; // first day, every 30m
		else if (daysSinceStartTime < 2d)
			threshold = 1d/24d; // second day, hourly
		else if (daysSinceStartTime < 7d)
			threshold = 6d/24d; // rest of first week, 6h
		else if (daysSinceStartTime < 14d)
			threshold = 0.5d; // second week, 12h
		else if (daysSinceStartTime < 31)
			threshold = 1d; // rest of first month, daily
		else if (daysSinceStartTime < 90)
			threshold = 7d; // second and third months, weekly
		else if (daysSinceStartTime < 182.5)
			threshold = 14d; // months 4-6, biweekly
		else
			threshold = 30d; // greater than 6 months, update monthly
		
		// randomly wait up to 10% longer than the threshold in order to get plots to spread
		// out a bit and not all replot at the same time
		threshold += new Random(prevResult.time).nextDouble()*threshold*0.1;
		
		boolean comcatUpdate = daysSinceUpdate > threshold;
		String updateStr;
		if (comcatUpdate) {
			updateStr = "true";
		} else {
			double daysUntil = threshold - daysSinceUpdate;
			updateStr = "false (will update in "+getTimeShortLabel(daysUntil/365.25)+")";
		}
		System.out.println("\t"+(float)daysSinceUpdate+" d since last ComCat update, threshold is "
				+(float)threshold+". Update? "+updateStr);
		return comcatUpdate;
	}
	
	private boolean quickContains(Location hypocenter) {
		double lat = hypocenter.getLatitude();
		double lon = hypocenter.getLongitude();
		if (!Double.isNaN(innerBoxMinLat) && lat >= innerBoxMinLat && lat <= innerBoxMaxLat
				&& lon >= innerBoxMinLon && lon <= innerBoxMaxLon)
			return true;
		return mapRegion.contains(hypocenter);
	}
	
	private boolean isRupAboveMinMag(ObsEqkRupture rup, double minMag, double timeDepMc) {
		if (minMag < 0)
			return rup.getMag() >= timeDepMc;
		return rup.getMag() >= minMag;
	}

	@Override
	protected void doProcessCatalog(ETAS_Catalog completeCatalog, ETAS_Catalog triggeredOnlyCatalog,
			FaultSystemSolution fss) {
		getPlotter(); // will initialize if not done yet
		short[][][] magGridCounts = new short[durations.length][magBins.length][];
		IncrementalMagFreqDist catMFD = new IncrementalMagFreqDist(
				totalCountHist.getMinX(), totalCountHist.getMaxX(), totalCountHist.size());
		EvenlyDiscretizedFunc[] timeFuncs = new EvenlyDiscretizedFunc[timeFuncMcs.length];
		for (int i=0; i<timeFuncs.length; i++)
			timeFuncs[i] = plotter.getTimeFuncDiscretization().deepClone();
		boolean[][] magTimesFull = new boolean[magTimeProbsFull.length][magTimeProbsFull[0].length];
		boolean[][] magTimesWeek = new boolean[magTimeProbsWeek.length][magTimeProbsWeek[0].length];
		boolean[][] magTimesMonth = new boolean[magTimeProbsMonth.length][magTimeProbsMonth[0].length];
		for (ETAS_EqkRupture rup : completeCatalog) {
			double mag = rup.getMag();
			int mfdIndex = totalCountHist.getClosestXIndex(mag);
			totalCountHist.add(mfdIndex, 1d);
			long ot = rup.getOriginTime();
			if (ot > maxOTs[maxOTs.length-1])
				continue;
			Location hypo = rup.getHypocenterLocation();
			if (!quickContains(hypo))
				continue;
			double timeDepMc = this.timeDepMc.calcTimeDepMc(rup);
			if (ot <= curTime) {
				// include in direct comparisons
				catMFD.add(mfdIndex, 1d);
				if (mag < magBins[1] && mag < timeDepMc)
					continue;
				int depthIndex = catalogDepthDistributions[0].getClosestXIndex(hypo.getDepth());
				for (int m=0; m<magBins.length; m++)
					if (isRupAboveMinMag(rup, magBins[m], timeDepMc))
						catalogDepthDistributions[m].add(depthIndex, 1d);
			}
			if (ot <= plotter.getEndTime() && mag >= timeFuncMcs[0]) {
				int timeIndex = this.plotter.getTimeFuncIndex(rup);
				for (int m=0; m<timeFuncMcs.length; m++) {
					if (isRupAboveMinMag(rup, timeFuncMcs[m], timeDepMc)) {
						for (int i=timeIndex; i<timeFuncs[m].size(); i++)
							timeFuncs[m].add(i, 1d);
					}
				}
			}
			int gridNode = gridRegion.indexForLocation(hypo);
			if (gridNode >= 0) {
				for (int d=0; d<durations.length; d++) {
					if (ot > maxOTs[d])
						continue;
					for (int m=0; m<magBins.length; m++) {
						if (isRupAboveMinMag(rup, magBins[m], timeDepMc)) {
							if (magGridCounts[d][m] == null)
								magGridCounts[d][m] = new short[gridRegion.getNodeCount()];
							Preconditions.checkState(magGridCounts[d][m][gridNode] < Short.MAX_VALUE,
									"Using shorts to conserve memory for grid node event counts within each catalog, "
									+ "but we have more than MAX_SHORT in a single cell!");
							magGridCounts[d][m][gridNode]++;
						}
					}
				}
			}
			int magTimeY = magTimeYAxis.getClosestXIndex(mag);
			double magTimeX = (double)(ot - startTime)/ProbabilityModelsCalc.MILLISEC_PER_YEAR;
			if (timeDays)
				magTimeX *= 365.25;
			if (magTimeX <= magTimeXAxisFullMax)
				magTimesFull[magTimeXAxisFull.getClosestXIndex(magTimeX)][magTimeY] = true;
			if (magTimeX <= magTimeXAxisWeekMax)
				magTimesWeek[magTimeXAxisWeek.getClosestXIndex(magTimeX)][magTimeY] = true;
			if (magTimeX <= magTimeXAxisMonthMax)
				magTimesMonth[magTimeXAxisMonth.getClosestXIndex(magTimeX)][magTimeY] = true;
		}
		if (map_plot_medians)
			catalogDurMagGridCounts.add(magGridCounts);
		for (int x=0; x<magTimesFull.length; x++)
			for (int y=0; y<magTimesFull[0].length; y++)
				if (magTimesFull[x][y])
					magTimeProbsFull[x][y]++;
		if (magTimesWeek != null) {
			for (int x=0; x<magTimesWeek.length; x++)
				for (int y=0; y<magTimesWeek[0].length; y++)
					if (magTimesWeek[x][y])
						magTimeProbsWeek[x][y]++;
		}
		if (magTimesMonth != null) {
			for (int x=0; x<magTimesMonth.length; x++)
				for (int y=0; y<magTimesMonth[0].length; y++)
					if (magTimesMonth[x][y])
						magTimeProbsMonth[x][y]++;
		}
		if (map_plot_probs || map_plot_means) {
			for (int d=0; d<durations.length; d++) {
				for (int m=0; m<magBins.length; m++) {
					double totCount = 0d;
					for (int n=0; n<gridRegion.getNodeCount(); n++) {
						short count = magGridCounts[d][m] == null ? 0 : magGridCounts[d][m][n];
						totCount += count;
						if (map_plot_probs && count > 0)
							catalogProbs[d][m][n]++;
						if (map_plot_means)
							catalogMeans[d][m][n] += count;
					}
					if (map_plot_probs && totCount > 0)
						catalogProbsSummary[d][m]++;
					if (map_plot_means)
						catalogMeansSummary[d][m] += totCount;
				}
			}
		}
		catalogRegionMFDs.add(catMFD);
		catalogTimeFuncs.add(timeFuncs);
		catalogsProcessed++;
	}
	
	private static String minMagPrefix(double mag) {
		if (mag < 0)
			return "td_mc";
		return "m"+optionalDigitDF.format(mag);
	}
	
	private static String minMagLabel(double mag) {
		if (mag < 0)
			return "M≥Mc(t)";
		return "M≥"+optionalDigitDF.format(mag);
	}
	
	private boolean shouldIncludeMinMag(double minMag) {
		boolean ret = ((float)minMag >= (float)simMc) ||
				(minMag < 0 && (float)simMc <= (float)timeDepMc.getMinMagThreshold());
//		System.out.println("should-include = "+ret+":\tminMag="+minMag+"\tsimMc="+simMc
//				+"\ttdMinMag="+timeDepMc.getMinMagThreshold());
		return ret;
	}
	
	boolean forecastOnly;

	@Override
	protected List<? extends Runnable> doFinalize(File outputDir, FaultSystemSolution fss, ExecutorService exec)
			throws IOException {
//		forecastOnly = getConfig().getSimulationStartTimeMillis() <= plotter.getEndTime()+60000l;
		// if the simulation is less than 10 minutes old, don't bother with comparison plots
		forecastOnly = getConfig().getSimulationStartTimeMillis() >= fetchEndTime-(10*1000l*60l);
		if (forecastOnly)
			System.out.println("Will only make ComCat forecast plots");
		int numToTrim = ETAS_MFD_Plot.calcNumToTrim(totalCountHist);
		simMc = totalCountHist.getX(numToTrim)-0.5*totalCountHist.getDelta();
		System.out.println("Simulation Mc: "+simMc);
		
		List<Runnable> runnables = new ArrayList<>();
		if (!forecastOnly) {
			System.out.println("Building ComCat time func plot runnables");
			overallMc = Math.max(comcatMc, simMc);
			for (int m=0; m<timeFuncMcs.length; m++)
				if (shouldIncludeMinMag(timeFuncMcs[m]))
					runnables.add(new TimeFuncPlotRunnable(outputDir,
							"comcat_compare_cumulative_num_"+minMagPrefix(timeFuncMcs[m]), m));
		}
		
		System.out.println("Writing ComCat Incremental MND");
		FractileCurveCalculator incrMNDCalc = buildFractileCalc(catalogRegionMFDs);
		plotter.plotMagNumPlot(outputDir, "comcat_compare_mag_num", false, comcatMinMag, comcatMc,
				false, false, incrMNDCalc);
		
		System.out.println("Calculating Catalog Cumulative MNDs");
		List<EvenlyDiscretizedFunc> cumulativeMNDs = new ArrayList<>();
		for (IncrementalMagFreqDist incr : catalogRegionMFDs)
			cumulativeMNDs.add(incr.getCumRateDistWithOffset());
		FractileCurveCalculator cumMNDCalc = buildFractileCalc(cumulativeMNDs);
		System.out.println("Writing ComCat Cumulative MND");
		plotter.plotMagNumPlot(outputDir, "comcat_compare_mag_num_cumulative", true, comcatMinMag, comcatMc,
				false, false, cumMNDCalc);
		
		if (!forecastOnly) {
			System.out.println("Writing ComCat Percentile Cumulative Plot");
			plotMagPercentileCumulativeNumPlot(outputDir, "comcat_compare_cumulative_num_percentile", cumulativeMNDs);
		}
		
		System.out.println("Writing ComCat Mag-Time plots");
		calcMagTimeProbs();
		if (!forecastOnly)
			plotMagTimeFunc(magTimeProbsFull, magTimeXAxisFull, "To Date Magnitude vs Time",
					outputDir, "mag_time_full");
		plotMagTimeFunc(magTimeProbsWeek, magTimeXAxisWeek, "One Week Magnitude vs Time Forecast",
					outputDir, "mag_time_week");
		plotMagTimeFunc(magTimeProbsMonth, magTimeXAxisMonth, "One Month Magnitude vs Time Forecast",
					outputDir, "mag_time_month");
		
		System.out.println("Writing time-dep Mc plot");
		plotter.plotTimeDepMcPlot(outputDir, "comcat_compare_td_mc", timeDepMc);
		
		System.out.println("Calculating ComCat map data");
		calcMapData();
		System.out.println("Will write ComCat maps in parallel in background");
		comcatCountsSummary = new int[durations.length][magBins.length];
		if (map_plot_probs)
			mapProbPrefixes = writeMapPlots(outputDir, "comcat_compare_prob", "Probability", catalogProbs, true, fss.getRupSet(), runnables);
		if (map_plot_means)
			mapMeanPrefixes = writeMapPlots(outputDir, "comcat_compare_mean", "Mean Expected Number", catalogMeans, false, fss.getRupSet(), runnables);
		if (map_plot_medians)
			mapMedianPrefixes = writeMapPlots(outputDir, "comcat_compare_median", "Median", catalogMedians, false, fss.getRupSet(), runnables);
		System.out.println("Writing ComCat Depth plots");
		writeDepthPlots(outputDir, "comcat_compare_depth");
		System.out.println("Done with ComCat doFinalize (still waiting on parallel background calcs)");
		return runnables;
	}
	
	private FractileCurveCalculator buildFractileCalc(List<? extends EvenlyDiscretizedFunc> funcs) {
		XY_DataSetList functionList = new XY_DataSetList();
		List<Double> relativeWts = new ArrayList<>();
		for (EvenlyDiscretizedFunc func : funcs) {
			functionList.add(func);
			relativeWts.add(1d);
		}
		return new FractileCurveCalculator(functionList, relativeWts);
	}
	
	private class TimeFuncPlotRunnable implements Runnable {
		
		private final File outputDir;
		private final String prefix;
		private final int magIndex;
		
		public TimeFuncPlotRunnable(File outputDir, String prefix, int magIndex) {
			super();
			this.outputDir = outputDir;
			this.prefix = prefix;
			this.magIndex = magIndex;
		}

		@Override
		public void run() {
			try {
				plotTimeFuncPlot(outputDir, prefix, magIndex);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
	}
	
	private void plotTimeFuncPlot(File outputDir, String prefix, int magIndex) throws IOException {
		double mc = timeFuncMcs[magIndex];
		String magLabel = minMagLabel(mc);
		EvenlyDiscretizedFunc timeFunc;
		if (mc < 0)
			timeFunc = plotter.calcCumulativeTimeFunc(timeDepMc);
		else
			timeFunc = plotter.calcCumulativeTimeFunc(mc);
		double simStartX = (startTime - plotter.getOriginTime())/ProbabilityModelsCalc.MILLISEC_PER_YEAR;
		if (timeDays)
			simStartX *= 365.25;
//		System.out.println("SimStartX: "+simStartX);
		double simShiftY = timeFunc.getInterpolatedY(simStartX);
//		System.out.println("SimShiftY: "+simShiftY);
		
		Double minTime = null;
		if (timeDays) {
			if (simStartX > 60d)
				minTime = simStartX - 60d;
		} else {
			if (simStartX > 60d/365.25)
				minTime = simStartX - 60d/365.25;
		}
		
		XY_DataSetList functionList = new XY_DataSetList();
		List<Double> relativeWts = new ArrayList<>();
		for (EvenlyDiscretizedFunc[] func : catalogTimeFuncs) {
			EvenlyDiscretizedFunc myFunc = func[magIndex];
			if (simStartX != 0d || simShiftY != 0d) {
				int minIndex = 0;
				for (int i=0; i<myFunc.size(); i++) {
					if (myFunc.getX(i) < simStartX)
						minIndex++;
					else
						break;
				}
				EvenlyDiscretizedFunc shift = new EvenlyDiscretizedFunc(
						myFunc.getX(minIndex), myFunc.size()-minIndex, myFunc.getDelta());
				for (int i=0; i<shift.size(); i++)
					shift.set(i, myFunc.getY(i+minIndex)+simShiftY);
				myFunc = shift;
			}
			functionList.add(myFunc);
			relativeWts.add(1d);
		}
		FractileCurveCalculator timeFractals = new FractileCurveCalculator(functionList, relativeWts);
		
		plotter.plotTimeFuncPlot(outputDir, prefix, magLabel, timeFunc, minTime, timeFractals);
	}
	
	public static double invPercentile(double[] counts, double dataVal) {
		Arrays.sort(counts);
		int index = Arrays.binarySearch(counts, dataVal);
		if (index < 0) {
			// convert to insertion index
			index = -(index + 1);
		} else {
			// it's an exact match, place it at the min index as per definition of percentile
			// (the percentage of values that lie below)
			while (index > 0 && (float)counts[index-1] == (float)dataVal)
				index--;
		}
		double numBelow = index;
		return 100d*numBelow/(double)counts.length;
	}
	
	private void plotMagPercentileCumulativeNumPlot(File outputDir, String prefix,
			List<EvenlyDiscretizedFunc> cumulativeMNDs) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		EvenlyDiscretizedFunc magFunc = cumulativeMNDs.get(0);
		
		ArbitrarilyDiscretizedFunc upper95 = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc lower95 = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc upper68 = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc lower68 = new ArbitrarilyDiscretizedFunc();
		ArbitrarilyDiscretizedFunc middle = new ArbitrarilyDiscretizedFunc();
		
		EvenlyDiscretizedFunc dataFunc = new EvenlyDiscretizedFunc(magFunc.getMinX(),
				magFunc.getMaxX(), magFunc.size());

		EvenlyDiscretizedFunc comcatCumulativeMND = plotter.calcIncrementalMagNum(
				comcatMinMag, false, false).getCumRateDistWithOffset();
		
		if ((float)comcatCumulativeMND.getMinX() != (float)magFunc.getMinX()
				|| comcatCumulativeMND.size() != magFunc.size()) {
			// re-align them
//			System.out.println("re-aligning! comcat min="+comcatCumulativeMND.getMinX()+" size="+comcatCumulativeMND.size());
//			System.out.println("\tdata min="+magFunc.getMinX()+" size="+magFunc.size());
			EvenlyDiscretizedFunc comcatAligned = new EvenlyDiscretizedFunc(
					magFunc.getMinX(), magFunc.getMaxX(), magFunc.size());
			double minX = magFunc.getMinX()-0.5*magFunc.getDelta();
			double maxX = magFunc.getMaxX()+0.5*magFunc.getDelta();
			for (Point2D pt : comcatCumulativeMND) {
				if ((float)pt.getX() < (float)minX || (float)pt.getX() > (float)maxX)
					continue;
				comcatAligned.add(magFunc.getClosestXIndex(pt.getX()), pt.getY());
			}
			comcatCumulativeMND = comcatAligned;
		}
		
		for (int i=0; i<magFunc.size(); i++) {
			double mag = magFunc.getX(i);
			upper95.set(mag, 97.5);
			lower95.set(mag, 2.5);
			upper68.set(mag, 84);
			lower68.set(mag, 16);
			middle.set(mag, 50);
			
			double[] counts = new double[cumulativeMNDs.size()];
			for (int j=0; j<counts.length; j++)
				counts[j] = cumulativeMNDs.get(j).getY(i);
			Preconditions.checkState((float)mag == (float)comcatCumulativeMND.getX(i));
			double dataVal = comcatCumulativeMND.getY(i);
			double percentile = invPercentile(counts, dataVal);
//			System.out.println("M="+(float)mag+"\tdataVal="+(float)dataVal+"\tindex="+index
//					+"\tcounts[index]="+(float)counts[index]);
			dataFunc.set(i, percentile);
		}

		UncertainArbDiscDataset bounds95 = new UncertainArbDiscDataset(middle, lower95, upper95);
		bounds95.setName("95% Confidence");
		funcs.add(bounds95);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, new Color(0, 0, 0, 20)));
		
		UncertainArbDiscDataset bounds68 = new UncertainArbDiscDataset(middle, lower68, upper68);
		bounds68.setName("68% Confidence");
		funcs.add(bounds68);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SHADED_UNCERTAIN, 1f, new Color(0, 0, 0, 40)));
		
		double minY = 0d;
		double maxY = 100d;
		
		if ((float)comcatMc > (float)simMc) {
			XY_DataSet mcFunc = new DefaultXY_DataSet();
			mcFunc.set(comcatMc, minY);
			mcFunc.set(comcatMc, maxY);
			mcFunc.setName("Mc");
			funcs.add(mcFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, plotter.getDataColor()));
		}
		
		dataFunc.setName("ComCat Data Num≥M");
		funcs.add(dataFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
		
		PlotSpec spec = new PlotSpec(funcs, chars, plotter.isNoTitles() ?
				" " : "Cumulative Count Data Percentile Comparison",
				"Minimum Magnitude", "Simulation Percentile");
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = buildGraphPanel();
		gp.setLegendFontSize(18);
		gp.setUserBounds(simMc, comcatCumulativeMND.getMaxX(), minY, maxY);

		gp.drawGraphPanel(spec, false, false);
		gp.getChartPanel().setSize(800, 600);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
//		gp.saveAsTXT(new File(outputDir, prefix+".txt").getAbsolutePath());
	}
	
	private void calcMagTimeProbs() {
		double scalar = 1d/(double)catalogsProcessed;
		for (double[] column : magTimeProbsFull)
			for (int i=0; i<column.length; i++)
				column[i] *= scalar;
		if (magTimeProbsWeek != null)
			for (double[] column : magTimeProbsWeek)
				for (int i=0; i<column.length; i++)
					column[i] *= scalar;
		if (magTimeProbsMonth != null)
			for (double[] column : magTimeProbsMonth)
				for (int i=0; i<column.length; i++)
					column[i] *= scalar;
	}
	
	private void plotMagTimeFunc(double[][] magTimeProbs, EvenlyDiscretizedFunc magTimeXAxis, String title,
			File outputDir, String prefix) throws IOException {
		double timeOffset = (startTime - plotter.getOriginTime())/ProbabilityModelsCalc.MILLISEC_PER_YEAR;
		if (timeDays)
			timeOffset *= 365.25;
		
		EvenlyDiscrXYZ_DataSet xyz = new EvenlyDiscrXYZ_DataSet(magTimeXAxis.size(), magTimeYAxis.size(),
				timeOffset+magTimeXAxis.getMinX(), magTimeYAxis.getMinX(), magTimeXAxis.getDelta(), magTimeYAxis.getDelta());
		for (int x=0; x<magTimeProbs.length; x++)
			for (int y=0; y<magTimeProbs[x].length; y++)
				xyz.set(x, y, magTimeProbs[x][y]);
		
		Double minTime = null;
		double xyzMinTime = xyz.getMinX() - 0.5*xyz.getGridSpacingX();
		if (timeDays) {
			if (xyzMinTime > 60d)
				minTime = xyzMinTime - 60d;
		} else {
			if (xyzMinTime > 60d/365.25)
				minTime = xyzMinTime - 60d/365.25;
		}
		
		plotter.plotMagTimeFunc(outputDir, prefix, title, minTime, xyz.getMaxX()+0.5*xyz.getGridSpacingX(), xyz);
	}
	
	private void calcMapData() {
		if (map_plot_medians)
			catalogMedians = new double[durations.length][magBins.length][gridRegion.getNodeCount()];
		double scalar = 1d/catalogsProcessed;
		for (int d=0; d<durations.length; d++) {
			for (int m=0; m<magBins.length; m++) {
				for (int n=0; n<gridRegion.getNodeCount(); n++) {
					if (map_plot_medians) {
						double[] data = new double[catalogsProcessed];
						for (int i=0; i<catalogsProcessed; i++) {
							short[][][] counts = catalogDurMagGridCounts.get(i);
							if (counts[d][m] != null && counts[d][m][n] > 0)
								data[n] += counts[d][m][n];
						}
						catalogMedians[d][m][n] = DataUtils.median(data);
					}
					
					if (map_plot_probs)
						catalogProbs[d][m][n] *= scalar;
					if (map_plot_means)
						catalogMeans[d][m][n] *= scalar;
				}
				if (map_plot_probs)
					catalogProbsSummary[d][m] *= scalar;
				if (map_plot_means)
					catalogMeansSummary[d][m] *= scalar;
			}
		}
	}
	
	public void setMapMinZ(double minZ) {
		this.minZ = minZ;
	}
	
	public void setMapCPT(CPT cpt) {
		this.baseCPT = cpt;
	}
	
	public void setMapDataColor(Color color) {
		this.mapDataColor = color;
	}
	
	private String[][] writeMapPlots(File outputDir, String prefix, String zName, double[][][] data, boolean isProb,
			FaultSystemRupSet rupSet, List<Runnable> mapRunnables) throws IOException {
		CPT cpt = baseCPT;
		cpt.setBelowMinColor(Color.WHITE);
		cpt.setNanColor(Color.WHITE);
		double minZ = this.minZ == null ? 1d/catalogsProcessed : this.minZ;
		
		List<XY_DataSet> faultFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> faultChars = new ArrayList<>();
		
		for (XY_DataSet caBoundary : PoliticalBoundariesData.loadCAOutlines()) {
			faultFuncs.add(caBoundary);
			faultChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		}
		
		PlotCurveCharacterstics outlineChar = new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.GRAY);
		PlotCurveCharacterstics traceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GRAY);
		for (FaultSection sect : rupSet.getFaultSectionDataList()) {
			RuptureSurface surf = sect.getFaultSurface(1d, false, false);
			for (XY_DataSet xy : ETAS_EventMapPlotUtils.getSurfOutlines(surf)) {
				faultFuncs.add(xy);
				faultChars.add(outlineChar);
			}
			for (XY_DataSet xy : ETAS_EventMapPlotUtils.getSurfTraces(surf)) {
				faultFuncs.add(xy);
				faultChars.add(traceChar);
			}
		}
		
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
		
		String[][] prefixes = new String[durations.length][magBins.length];
		
		for (int d=0; d<durations.length; d++) {
			String durPrefix = (float)durations[d] == (float)curDuration ?
					"current" : getTimeShortLabel(durations[d]).replace(" ", "");
			for (int m=0; m<magBins.length; m++) {
				if (!shouldIncludeMinMag(magBins[m]))
					continue;
				String myPrefix = prefix+"_"+durPrefix+"_"+minMagPrefix(magBins[m]);
				prefixes[d][m] = myPrefix;
				
				GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridRegion, false);
				for (int i=0; i<data[d][m].length; i++)
					xyz.set(i, data[d][m][i]);
				
				List<ObsEqkRupture> catalogEvents = new ArrayList<>();
				String title = getTimeLabel(durations[d], false)+" "+minMagLabel(magBins[m]);
				if (maxOTs[d] <= curTime) {
					// TODO update
					ObsEqkRupList comcatEvents = magBins[m] < 0 ?
							timeDepMc.getFiltered(this.comcatEvents) : this.comcatEvents;
					for (ObsEqkRupture rup : comcatEvents)
						if (rup.getMag() >= magBins[m] && rup.getOriginTime() <= maxOTs[d])
							catalogEvents.add(rup);
					comcatCountsSummary[d][m] = catalogEvents.size();
					title += " Comparison";
				} else {
					title += " Forecast";
				}
				
//				String title = " ";
				
				String zLabel = zName+" "+minMagLabel(magBins[m])+", "+getTimeLabel(durations[d], false);
				
				mapRunnables.add(new MapRunnable(outputDir, zLabel, cpt, minZ, isProb, faultFuncs, faultChars, latSpan, lonSpan,
						tickUnit, myPrefix, xyz, catalogEvents, title));
			}
		}
		return prefixes;
	}

	private class MapRunnable implements Runnable {
		private File outputDir;
		private String zLabel;
		private CPT cpt;
		private double minZ;
		private boolean isProb;
		private List<XY_DataSet> faultFuncs;
		private List<PlotCurveCharacterstics> faultChars;
		private double latSpan;
		private double lonSpan;
		private double tickUnit;
		private String myPrefix;
		private GriddedGeoDataSet xyz;
		private List<ObsEqkRupture> catalogEvents;
		private String title;

		public MapRunnable(File outputDir, String zLabel, CPT cpt, double minZ, boolean isProb, List<XY_DataSet> faultFuncs,
				List<PlotCurveCharacterstics> faultChars, double latSpan, double lonSpan, double tickUnit, String myPrefix,
				GriddedGeoDataSet xyz, List<ObsEqkRupture> catalogEvents, String title) {
			this.outputDir = outputDir;
			this.zLabel = zLabel;
			this.cpt = cpt;
			this.minZ = minZ;
			this.isProb = isProb;
			this.faultFuncs = faultFuncs;
			this.faultChars = faultChars;
			this.latSpan = latSpan;
			this.lonSpan = lonSpan;
			this.tickUnit = tickUnit;
			this.myPrefix = myPrefix;
			this.xyz = xyz;
			this.catalogEvents = catalogEvents;
			this.title = title;
		}
		
		public void run() {
			try {
				List<XY_DataSet> funcs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				
				ETAS_EventMapPlotUtils.buildEventPlot(catalogEvents, funcs, chars, comcatMaxMag);
				for (PlotCurveCharacterstics pChar : chars)
					pChar.setColor(mapDataColor);
				
				funcs.addAll(0, faultFuncs);
				chars.addAll(0, faultChars);
				
				double minLogZ = Math.floor(Math.log10(minZ));
				double maxLogZ;
				if (isProb) {
					maxLogZ = 0; // Log10(1) = 0
				} else {
					double maxZ = xyz.getMaxZ();
					if (maxZ == 0d)
						maxZ = 1;
					maxLogZ = Math.ceil(Math.log10(maxZ));
				}
				while (maxLogZ <= minLogZ)
					maxLogZ++;
				CPT myCPT = cpt.rescale(minLogZ, maxLogZ);
				if (cpt_discretizations > 0) {
					int num = (int)Math.round((myCPT.getMaxValue() - myCPT.getMinValue())*cpt_discretizations);
//					System.out.println("Discretizing CPT into "+num+" parts. extents ["
//							+myCPT.getMinValue()+" "+myCPT.getMaxValue()+"]");
					myCPT = myCPT.asDiscrete(num, true);
//					System.out.println(myCPT);
				}
				
				xyz.log10();
				
				XYZPlotSpec spec = new XYZPlotSpec(xyz, myCPT, plotter.isNoTitles() ? " " : title, "Longitude", "Latitude", "Log10 "+zLabel);
				
				spec.setXYElems(funcs);
				spec.setXYChars(chars);
				
				spec.setCPTPosition(RectangleEdge.BOTTOM);
				XYZGraphPanel gp = new XYZGraphPanel(buildGraphPanel().getPlotPrefs());
				
				int width = 800;
				
				TickUnits tus = new TickUnits();
				TickUnit tu = new NumberTickUnit(tickUnit);
				tus.add(tu);
				
				gp.drawPlot(spec, false, false, new Range(mapRegion.getMinLon(), mapRegion.getMaxLon()),
						new Range(mapRegion.getMinLat(), mapRegion.getMaxLat()));
//					gp.getChartPanel().getChart().addSubtitle(slipCPTbar);
				gp.getYAxis().setStandardTickUnits(tus);
				gp.getXAxis().setStandardTickUnits(tus);
				gp.getChartPanel().setSize(width, (int)((double)(width)*latSpan/lonSpan));
				
				gp.saveAsPNG(new File(outputDir, myPrefix+".png").getAbsolutePath());
//					gp.saveAsPDF(new File(outputDir, myPrefix+".pdf").getAbsolutePath());
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
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
	
	private void writeDepthPlots(File outputDir, String prefix) throws IOException {
		for (int m=0; m<magBins.length; m++) {
			if (!shouldIncludeMinMag(magBins[m]))
				continue;
			String myPrefix = prefix+"_"+minMagPrefix(magBins[m]);
			EvenlyDiscretizedFunc catalogFunc = catalogDepthDistributions[m];
			catalogFunc.scale(1d/(double)catalogsProcessed);
			plotter.plotMagDepthPlot(outputDir, myPrefix,
					magBins[m] > 0 ? null : timeDepMc, magBins[m], catalogFunc);
		}
	}

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink)
			throws IOException {
		List<String> lines = new ArrayList<>();
		
		lines.add(topLevelHeading+" ComCat Data Comparisons");
		lines.add(topLink); lines.add("");
		
		lines.add("These plots compare simulated sequences with data from ComCat. All plots only consider events with hypocenters "
				+ "inside the ComCat region defined in the JSON input file.");
		lines.add("");
		String line = "Last updated at "+SimulationMarkdownGenerator.df.format(new Date(curTime))
			+", "+getTimeLabel(curDuration, true).toLowerCase()+" after the simulation start time";
		if (plotter.getMainshock() != null && startTime-plotter.getOriginTime() > 1000l) {
			double msDuration = curDuration + (startTime-plotter.getOriginTime())/ProbabilityModelsCalc.MILLISEC_PER_YEAR;
			line += " and "+getTimeLabel(msDuration, true).toLowerCase()+" after the mainshock";
		}
		line += ".";
		lines.add(line);
		lines.add("");
		lines.add("Total matching ComCat events found: "+comcatEvents.size());
		lines.add("");
		
		lines.add(topLevelHeading+"# ComCat Magnitude-Number Distributions");
		lines.add(topLink); lines.add("");
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.addLine("Incremental MND", "Cumulative MND");
		table.addLine("![Incremental MND]("+relativePathToOutputDir+"/comcat_compare_mag_num.png)",
				"![Cumi MND]("+relativePathToOutputDir+"/comcat_compare_mag_num_cumulative.png)");
		lines.addAll(table.build());
		lines.add("");
		
		lines.add(topLevelHeading+"# ComCat Magnitude-Time Functions");
		lines.add(topLink); lines.add("");
		line = "These plots show the show the magnitude versus time probability function since simulation start. "
				+ "Observed event data lie on top, with those input to the simulation plotted as magenta circles and those "
				+ "that occurred after the simulation start time as cyan circles. Time is relative to ";
		if (plotter.getMainshock() == null) {
			line += "the simulation start time.";
		} else {
			ObsEqkRupture mainshock = plotter.getMainshock();
			Double mag = mainshock.getMag();
			line += "the mainshock (M"+optionalDigitDF.format(mag)+", "+mainshock.getEventId()+", plotted as a brown circle).";
		}
		line += " Probabilities are only shown above the minimum simulated magnitude, M="+optionalDigitDF.format(simMc)+".";
		lines.add(line);
		table = MarkdownUtils.tableBuilder();
		
		if (magTimeProbsWeek != null)
			table.addLine("![One Week]("+relativePathToOutputDir+"/mag_time_week.png)");
		if (magTimeProbsMonth != null)
			table.addLine("![One Month]("+relativePathToOutputDir+"/mag_time_month.png)");
		if (!forecastOnly)
			table.addLine("![Full Mag/Time]("+relativePathToOutputDir+"/mag_time_full.png)");
		lines.add("");
		lines.addAll(table.build());
		lines.add("");
		
		if (shouldIncludeMinMag(-1)) {
			lines.add(topLevelHeading+"# ComCat Time-Dependent Mc");
			lines.add(topLink); lines.add("");
			lines.add("The following plots compare simulation results with ComCat data above a magnitude threshold. "
					+ "Plots labeled as *M&ge;Mc(t)* use the time-dependent magnitude of completeness (Mc) "
					+ "defined in Helmstetter et al. (2006), which is plotted below. In the case of multiple "
					+ "M&ge;"+optionalDigitDF.format(MAX_MAG_FOR_TD_MC)+" ruptures, either as input to the "
					+ "simulation or in the comparison data, the maximum calculated time-dependent Mc is used. "
					+ "This time-dependent Mc function is plotted below.");
			lines.add("");
			lines.add("![TD MC]("+relativePathToOutputDir+"/comcat_compare_td_mc.png)");
			lines.add("");
		}
		
		if (!forecastOnly) {
			List<Double> timeFuncMags = new ArrayList<>();
			for (double mag : timeFuncMcs)
				if (shouldIncludeMinMag(mag))
					timeFuncMags.add(mag);
			if (!timeFuncMags.isEmpty()) {
				lines.add(topLevelHeading+"# ComCat Cumulative Number Vs Time");
				lines.add(topLink); lines.add("");
				table = MarkdownUtils.tableBuilder();
				table.initNewLine();
				for (double mag : timeFuncMags)
					table.addColumn(minMagLabel(mag).replaceAll("≥", "&ge;"));
				table.finalizeLine();
				table.initNewLine();
				for (double mag : timeFuncMags)
					table.addColumn("![MND]("+relativePathToOutputDir+"/comcat_compare_cumulative_num_"
							+minMagPrefix(mag)+".png)");
				table.finalizeLine();
				lines.addAll(table.build());
				lines.add("");
			}
			
			lines.add(topLevelHeading+"# ComCat Cumulative Number Simulation Percentiles");
			lines.add(topLink); lines.add("");
			lines.add("![MND]("+relativePathToOutputDir+"/comcat_compare_cumulative_num_percentile.png)");
			lines.add("");
		}
		
		String mapForecastLine = null;
		if (curTime < maxOTs[maxOTs.length-1])
			mapForecastLine = "*Note: maps labeled 'Forecast' are for a duration that extends into the future, "
					+ "only forecasted values are plotted (ComCat data omitted)*";
		if (map_plot_probs) {
			lines.add(topLevelHeading+"# ComCat Probability Spatial Distribution");
			lines.add(topLink); lines.add("");
			if (mapForecastLine != null) {
				lines.add(mapForecastLine);
				lines.add("");
			}
			lines.addAll(mapPlotTable(relativePathToOutputDir, mapProbPrefixes, catalogProbsSummary, true).build());
			lines.add("");
		}
		
		if (map_plot_means) {
			lines.add(topLevelHeading+"# ComCat Mean Expectation Spatial Distribution");
			lines.add(topLink); lines.add("");
			if (mapForecastLine != null) {
				lines.add(mapForecastLine);
				lines.add("");
			}
			lines.addAll(mapPlotTable(relativePathToOutputDir, mapMeanPrefixes, catalogMeansSummary, false).build());
			lines.add("");
		}
		
		if (map_plot_medians) {
			lines.add(topLevelHeading+"# ComCat Spatial Distribution, Median");
			lines.add(topLink); lines.add("");
			if (mapForecastLine != null) {
				lines.add(mapForecastLine);
				lines.add("");
			}
			lines.addAll(mapPlotTable(relativePathToOutputDir, mapMedianPrefixes, null, false).build());
			lines.add("");
		}
		
		List<Double> depthMags = new ArrayList<>();
		for (double mag : magBins)
			if (shouldIncludeMinMag(mag))
				depthMags.add(mag);
		if (!depthMags.isEmpty()) {
			lines.add(topLevelHeading+"# ComCat Depth Distribution");
			lines.add(topLink); lines.add("");
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			for (double mag : depthMags)
				table.addColumn(minMagLabel(mag).replaceAll("≥", "&ge;"));
			table.finalizeLine();
			table.initNewLine();
			for (double mag : depthMags)
				table.addColumn("![Depth Distribution]("+relativePathToOutputDir
						+"/comcat_compare_depth_"+minMagPrefix(mag)+".png)");
			table.finalizeLine();
			lines.addAll(table.build());
		}
		
		return lines;
	}
	
	public String getMapTableLabel(double duration) {
		if ((float)duration == (float)curDuration)
			return "Current ("+getTimeLabel(duration, false)+")";
		else if ((float)duration > (float)curDuration)
			return "Forecast: "+getTimeLabel(duration, false);
		else
			return getTimeLabel(duration, false);
	}
	
	private TableBuilder mapPlotTable(String relativePathToOutputDir, String[][] prefixes,
			double[][] valueSummaries, boolean prob) {
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("");
		for (double duration : durations)
			table.addColumn(getMapTableLabel(duration));
		table.finalizeLine();
		for (int m=0; m<magBins.length; m++) {
			if (!shouldIncludeMinMag(magBins[m]))
				continue;
			table.initNewLine();
			table.addColumn("**"+minMagLabel(magBins[m]).replaceAll("≥", "&ge;")+"**");
			for (int d=0; d<durations.length; d++)
				table.addColumn("![Map]("+relativePathToOutputDir+"/"+prefixes[d][m]+".png)");
			table.finalizeLine();
			if (valueSummaries != null) {
				table.initNewLine();
				table.addColumn("");
				for (int d=0; d<durations.length; d++) {
					String col;
					if (prob)
						col = "Prob: "+percentProbDF.format(valueSummaries[d][m]);
					else
						col = "Mean: "+getProbStr(valueSummaries[d][m]);
					if ((float)durations[d] <= (float)curDuration) {
						col += ", Actual: "+comcatCountsSummary[d][m];
					}
					table.addColumn(col);
				}
				table.finalizeLine();
			}
		}
		return table;
	}
	
	public static void main(String[] args) {
		File simDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
//				+ "2019_06_05-Spontaneous-includeSpont-historicalCatalog-full_td-1000yr");
//				+ "2019_06_05-Spontaneous-includeSpont-historicalCatalog-no_ert-1000yr");
//				+ "2019_07_04-SearlesValleyM64-includeSpont-full_td-10yr");
//				+ "2019-06-05_M7.1_SearlesValley_Sequence_UpdatedMw_and_depth");
//				+ "2019_07_06-SearlessValleySequenceFiniteFault-noSpont-full_td-10yr-start-noon");
//				+ "2019_07_06-SearlessValleySequenceFiniteFault-noSpont-full_td-10yr-following-M7.1");
//				+ "2019_07_16-ComCatM7p1_ci38457511_ShakeMapSurfaces-noSpont-full_td-scale1.14");
//				+ "2019_08_20-ComCatM6p4_ci38443183_PointSources-noSpont-full_td-scale1.14");
//				+ "2019_10_15-ComCatM4p71_nc73292360_PointSources");
//				+ "2020_04_08-ComCatM4p87_ci39126079_PointSource_kCOV1p5");
				+ "2020_04_08-ComCatM4p87_ci39126079_4p7DaysAfter_PointSources_kCOV1p5");
//				+ "2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces");
//				+ "2019_09_12-ComCatM7p1_ci38457511_7DaysAfter_ShakeMapSurfaces");
//				+ "2019_09_12-ComCatM7p1_ci38457511_28DaysAfter_ShakeMapSurfaces");
//				+ "2020_04_27-ComCatM7p1_ci38457511_296p8DaysAfter_ShakeMapSurfaces");
//				+ "2020_06_03-ComCatM7p1_ci38457511_334DaysAfter_ShakeMapSurfaces");
		File configFile = new File(simDir, "config.json");
		
		try {
			ETAS_Config config = ETAS_Config.readJSON(configFile);
			ETAS_Launcher launcher = new ETAS_Launcher(config, false);
			
			int maxNumCatalogs = 0;
//			int maxNumCatalogs = 1000;
			
			ETAS_AbstractPlot plot = new ETAS_ComcatComparePlot(config, launcher);
			File outputDir = new File(simDir, "plots");
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
			
			FaultSystemSolution fss = launcher.checkOutFSS();
			
			File inputFile = SimulationMarkdownGenerator.locateInputFile(config);
			int processed = 0;
			for (ETAS_Catalog catalog : ETAS_CatalogIO.getBinaryCatalogsIterable(inputFile, 0d)) {
				if (processed % 1000 == 0)
					System.out.println("Catalog "+processed);
				plot.processCatalog(catalog, fss);
				processed++;
				if (maxNumCatalogs > 0 && processed == maxNumCatalogs)
					break;
			}
			
			plot.finalize(outputDir, launcher.checkOutFSS());
			
			List<String> lines = plot.generateMarkdown(outputDir.getName(), "##", "*(top)*");
			
			MarkdownUtils.writeReadmeAndHTML(lines, simDir);
			
			System.exit(0);
		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
