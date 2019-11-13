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

import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.data.Range;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.TextAnchor;
import org.opensha.commons.calc.FractileCurveCalculator;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.comcat.ComcatAccessor;
import org.opensha.commons.data.comcat.ComcatRegion;
import org.opensha.commons.data.comcat.ComcatRegionAdapter;
import org.opensha.commons.data.function.AbstractXY_DataSet;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
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
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
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
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class ETAS_ComcatComparePlot extends ETAS_AbstractPlot {
	
	private double minSpan;
	private Region mapRegion;
	private GriddedRegion gridRegion;
	private ObsEqkRupList comcatEvents;
	private double modalMag;
	private double comcatMc;
	private double comcatMaxMag;
	private IncrementalMagFreqDist comcatMND;
	private EvenlyDiscretizedFunc timeDiscretization;
	private double simMc;
	private double overallMc;
	
	// for T-D Mc
	private static final double CISN_MC = 2.3;
	private static double MAX_MAG_FOR_TD_MC = 5;
	private double tdMinMag;
	private List<ObsEqkRupture> mainshocksForTimeDepMc;
	private EvenlyDiscretizedFunc timeDepMcFunc;
	private ObsEqkRupList comcatEventsFilteredTDMc;
	
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
	
	private List<IncrementalMagFreqDist> catalogRegionMFDs;
	private double[] timeFuncMcs;
	private List<EvenlyDiscretizedFunc[]> catalogTimeFuncs;
	private EvenlyDiscretizedFunc[] catalogDepthDistributions;
	private HistogramFunction totalCountHist;
	
	private static double[] default_durations = { 1d / 365.25, 7d / 365.25, 30 / 365.25, 1d };
	private static double[] fractiles = {0.025, 0.16, 0.84, 0.975};

	private static final boolean map_plot_probs = true;
	private static final boolean map_plot_means = true;
	private static final boolean map_plot_medians = false;

	private boolean plotIncludeMean = true;
	private boolean plotIncludeMedian = true;
	private boolean plotIncludeMode = true;
	private Color dataColor = Color.RED;
	private Double timeFuncMaxY = null;
	private Map<Double, String> timeFuncAnns = null;
	
	private double[] durations;
	private long[] maxOTs;
	private long startTime;
	private long curTime;
	private double curDuration;
	
	// plot settings
	private CPT baseCPT = null;
	private Double minZ = null;
	private Color mapDataColor = Color.CYAN;
	private boolean noTitles = false;
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
			this.comcatEvents = inputEvents;
		} else {
			try {
				comcatEvents = loadComcatEvents(config, comcatMeta, mapRegion, Long.min(curTime, maxOTs[maxOTs.length-1]));
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
	}
	
	public static ObsEqkRupList loadComcatEvents(ETAS_Config config, ComcatMetadata comcatMeta, Region mapRegion, long endTime) {
		ComcatAccessor accessor = new ComcatAccessor();
		ComcatRegion cReg = mapRegion instanceof ComcatRegion ? (ComcatRegion)mapRegion : new ComcatRegionAdapter(mapRegion);
		return accessor.fetchEventList(comcatMeta.eventID, config.getSimulationStartTimeMillis(), endTime,
				comcatMeta.minDepth, comcatMeta.maxDepth, cReg, false, false, comcatMeta.minMag);
	}
	
	private void init() {
		ETAS_Config config = getConfig();
		ETAS_Launcher launcher = getLauncher();
		ComcatMetadata comcatMeta = config.getComcatMetadata();
		comcatMaxMag = 0d;
		comcatMND = new IncrementalMagFreqDist(ETAS_MFD_Plot.mfdMinMag, ETAS_MFD_Plot.mfdNumMag, ETAS_MFD_Plot.mfdDelta);
		if (comcatEvents.isEmpty()) {
			modalMag = comcatMND.getX(0)-0.5*comcatMND.getDelta();
			System.out.println("No ComCat events in region.");
		} else {
			for (ObsEqkRupture rup : comcatEvents) {
				comcatMaxMag = Math.max(comcatMaxMag, rup.getMag());
				comcatMND.add(comcatMND.getClosestXIndex(rup.getMag()), 1d);
			}
			modalMag = comcatMND.getX(comcatMND.getXindexForMaxY())-0.5*comcatMND.getDelta();
			System.out.println("Found "+comcatEvents.size()+" ComCat events in region. Max mag: "
					+(float)comcatMaxMag+". Modal mag: "+(float)modalMag);
		}
		if (comcatMeta.magComplete == null) {
			System.out.println("Using default Mc = modalMag + 0.5");
			comcatMc = modalMag + 0.5;
		} else {
			System.out.println("Using supplied ComCat Mc");
			comcatMc = comcatMeta.magComplete;
		}
		System.out.println("Mc="+(float)comcatMc);
		timeDays = curDuration <= 1d;
		if (timeDays)
			timeDiscretization = new EvenlyDiscretizedFunc(0d, durations[durations.length-1]*365.25, 500);
		else
			timeDiscretization = new EvenlyDiscretizedFunc(0d, durations[durations.length-1], 500);
		
		double maxTriggerMag = Double.NEGATIVE_INFINITY;
		ETAS_EqkRupture maxMainshock = null;
		for (ETAS_EqkRupture rup : launcher.getTriggerRuptures()) {
			if (rup.getMag() > maxTriggerMag) {
				maxMainshock = rup;
				maxTriggerMag = rup.getMag();
			}
		}
		
		mainshocksForTimeDepMc = new ArrayList<>();
		mainshocksForTimeDepMc.add(maxMainshock);
		for (ObsEqkRupture rup : comcatEvents)
			if (rup.getMag() >= MAX_MAG_FOR_TD_MC)
				mainshocksForTimeDepMc.add(rup);
		for (ETAS_EqkRupture rup : launcher.getTriggerRuptures())
			if (rup != maxMainshock && rup.getMag() >= MAX_MAG_FOR_TD_MC)
				mainshocksForTimeDepMc.add(rup);
		
		timeDepMcFunc = new EvenlyDiscretizedFunc(timeDiscretization.getMinX(),
				timeDiscretization.getMaxX(), timeDiscretization.size());
		tdMinMag = Math.max(ETAS_Utils.magMin_DEFAULT, CISN_MC);
		if (timeDays) {
			for (int i=0; i<timeDepMcFunc.size(); i++) {
				double days = timeDepMcFunc.getX(i);
				long time = (long)(startTime + days*ProbabilityModelsCalc.MILLISEC_PER_DAY);
				timeDepMcFunc.set(i, calcTimeDepMc(mainshocksForTimeDepMc, time, tdMinMag));
			}
		} else {
			for (int i=0; i<timeDepMcFunc.size(); i++) {
				double years = timeDepMcFunc.getX(i);
				long time = (long)(startTime + years*ProbabilityModelsCalc.MILLISEC_PER_YEAR);
				timeDepMcFunc.set(i, calcTimeDepMc(mainshocksForTimeDepMc, time, tdMinMag));
			}
		}
		comcatEventsFilteredTDMc = new ObsEqkRupList();
		for (ObsEqkRupture rup : comcatEvents) {
			double timeDepMc = calcTimeDepMc(rup);
			if (rup.getMag() >= timeDepMc)
				comcatEventsFilteredTDMc.add(rup);
		}
		
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
		
		totalCountHist = new HistogramFunction(comcatMND.getMinX(), comcatMND.getMaxX(), comcatMND.size());
		
		catalogDepthDistributions = new EvenlyDiscretizedFunc[magBins.length];
		double minDepthBin = 0.5;
		double deltaDepth = 1d;
		int numDepth = (int)Math.ceil(ETAS_CubeDiscretizationParams.DEFAULT_MAX_DEPTH);
		for (int i=0; i<magBins.length; i++)
			catalogDepthDistributions[i] = new EvenlyDiscretizedFunc(minDepthBin, numDepth, deltaDepth);
		
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
	
	private static final int VERSION = 16;

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
			threshold = 1d/48d; // 30m
		else if (daysSinceStartTime < 2d)
			threshold = 1d/24d; // 1h
		else if (daysSinceStartTime < 7d)
			threshold = 6d/24d; // 6h
		else if (daysSinceStartTime < 14d)
			threshold = 0.5d; // 12h
		else if (daysSinceStartTime < 31)
			threshold = 1d; // 1d
		else if (daysSinceStartTime < 365)
			threshold = 7d; // 1wk
		else
			threshold = 30d; // 1mo
		
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
	
	private double calcTimeDepMc(ObsEqkRupture rup) {
		return calcTimeDepMc(mainshocksForTimeDepMc, rup.getOriginTime(), tdMinMag);
	}
	
	private static double calcTimeDepMc(List<? extends ObsEqkRupture> mainshocks, long time, double minMag) {
		double maxMc = minMag;
		for (ObsEqkRupture mainshock : mainshocks) {
			double myMc = calcTimeDepMc(mainshock, time, minMag);
			if (Double.isFinite(myMc))
				maxMc = Math.max(maxMc, myMc);
		}
		return maxMc;
	}
	
	private static double calcTimeDepMc(ObsEqkRupture mainshock, long time, double minMag) {
		if (time <= mainshock.getOriginTime())
			return Double.NaN;
		double daysSinceMainshock = (double)(time - mainshock.getOriginTime())/(double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
		return calcTimeDepMc(mainshock.getMag(), daysSinceMainshock, minMag);
	}
	
	private static double calcTimeDepMc(double mainshockMag, double daysSinceMainshock, double minMag) {
		return Math.max(minMag, mainshockMag - 4.5 - 0.75*Math.log10(daysSinceMainshock));
	}
	
	private boolean isRupAboveMinMag(ObsEqkRupture rup, double minMag, double timeDepMc) {
		if (minMag < 0)
			return rup.getMag() >= timeDepMc;
		return rup.getMag() >= minMag;
	}

	@Override
	protected void doProcessCatalog(ETAS_Catalog completeCatalog, ETAS_Catalog triggeredOnlyCatalog,
			FaultSystemSolution fss) {
		synchronized (this) {
			if (comcatMND == null)
				init();
		}
		short[][][] magGridCounts = new short[durations.length][magBins.length][];
		IncrementalMagFreqDist catMFD = new IncrementalMagFreqDist(
				totalCountHist.getMinX(), totalCountHist.getMaxX(), totalCountHist.size());
		EvenlyDiscretizedFunc[] timeFuncs = new EvenlyDiscretizedFunc[timeFuncMcs.length];
		for (int i=0; i<timeFuncs.length; i++)
			timeFuncs[i] = new EvenlyDiscretizedFunc(timeDiscretization.getMinX(),
					timeDiscretization.getMaxX(), timeDiscretization.size());
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
			double timeDepMc = calcTimeDepMc(rup);
			if (ot <= curTime) {
				// include in direct comparisons
				catMFD.add(mfdIndex, 1d);
				if (mag < magBins[1] && mag < timeDepMc)
					continue;
				if (mag >= timeFuncMcs[0]) {
					int timeIndex = getTimeFuncIndex(rup);
					for (int m=0; m<timeFuncMcs.length; m++) {
						if (isRupAboveMinMag(rup, timeFuncMcs[m], timeDepMc)) {
							for (int i=timeIndex; i<timeFuncs[m].size(); i++)
								timeFuncs[m].add(i, 1d);
						}
					}
				}
				int depthIndex = catalogDepthDistributions[0].getClosestXIndex(hypo.getDepth());
				for (int m=0; m<magBins.length; m++)
					if (isRupAboveMinMag(rup, magBins[m], timeDepMc))
						catalogDepthDistributions[m].add(depthIndex, 1d);
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
		}
		if (map_plot_medians)
			catalogDurMagGridCounts.add(magGridCounts);
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
	
	public void setHideTitles() {
		this.noTitles = true;
	}

	public void setPlotIncludeMean(boolean plotIncludeMean) {
		this.plotIncludeMean = plotIncludeMean;
	}

	public void setPlotIncludeMedian(boolean plotIncludeMedian) {
		this.plotIncludeMedian = plotIncludeMedian;
	}

	public void setPlotIncludeMode(boolean plotIncludeMode) {
		this.plotIncludeMode = plotIncludeMode;
	}

	public void setDataColor(Color dataColor) {
		this.dataColor = dataColor;
	}
	
	public void setTimeFuncMaxY(Double timeFuncMaxY) {
		this.timeFuncMaxY = timeFuncMaxY;
	}
	
	public void setTimeFuncAnns(Map<Double, String> timeFuncAnns) {
		this.timeFuncAnns = timeFuncAnns;
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
		boolean ret = ((float)minMag >= (float)simMc) || (minMag < 0 && (float)simMc <= (float)tdMinMag);
//		System.out.println("should-include = "+ret+":\tminMag="+minMag+"\tsimMc="+simMc+"\ttdMinMag="+tdMinMag);
		return ret;
	}

	@Override
	protected List<? extends Runnable> doFinalize(File outputDir, FaultSystemSolution fss, ExecutorService exec)
			throws IOException {
		int numToTrim = ETAS_MFD_Plot.calcNumToTrim(totalCountHist);
		simMc = totalCountHist.getX(numToTrim)-0.5*totalCountHist.getDelta();
		System.out.println("Building ComCat time func plot runnables");
		List<Runnable> runnables = new ArrayList<>();
		overallMc = Math.max(comcatMc, simMc);
		for (int m=0; m<timeFuncMcs.length; m++)
			if (shouldIncludeMinMag(timeFuncMcs[m]))
				runnables.add(new TimeFuncPlotRunnable(outputDir,
						"comcat_compare_cumulative_num_"+minMagPrefix(timeFuncMcs[m]), m));
		System.out.println("Writing ComCat Incremental MND");
		writeMagNumPlot(outputDir, "comcat_compare_mag_num", catalogRegionMFDs, comcatMND, "Incremental Number");
		System.out.println("Calculating Catalog Cumulative MNDs");
		List<EvenlyDiscretizedFunc> cumulativeMNDs = new ArrayList<>();
		for (IncrementalMagFreqDist incr : catalogRegionMFDs)
			cumulativeMNDs.add(incr.getCumRateDistWithOffset());
		EvenlyDiscretizedFunc comcatCumulativeMND = comcatMND.getCumRateDistWithOffset();
		System.out.println("Writing ComCat Cumulative MND");
		writeMagNumPlot(outputDir, "comcat_compare_mag_num_cumulative", cumulativeMNDs, comcatCumulativeMND, "Cumulative Number");
		System.out.println("Writing ComCat Percentile Cumulative Plot");
		writeMagPercentileCumulativeNumPlot(outputDir, "comcat_compare_cumulative_num_percentile", cumulativeMNDs, comcatCumulativeMND);
		
		System.out.println("Writing time-dep Mc plot");
		writeTimeDepMcPlot(outputDir, "comcat_compare_td_mc");
		
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
	
	private FractileCurveCalculator buildFractileCalc(List<? extends EvenlyDiscretizedFunc[]> funcs, int index) {
		XY_DataSetList functionList = new XY_DataSetList();
		List<Double> relativeWts = new ArrayList<>();
		for (EvenlyDiscretizedFunc[] func : funcs) {
			functionList.add(func[index]);
			relativeWts.add(1d);
		}
		return new FractileCurveCalculator(functionList, relativeWts);
	}
	
	private int getTimeFuncIndex(ObsEqkRupture rup) {
		double relTime = rup.getOriginTime() - startTime;
		if (timeDays)
			relTime /= ProbabilityModelsCalc.MILLISEC_PER_DAY;
		else
			relTime /= ProbabilityModelsCalc.MILLISEC_PER_YEAR;
		int timeIndex = timeDiscretization.getClosestXIndex(relTime);
		// this is the closest index, we need the first index that time is <= to
		if (relTime > timeDiscretization.getX(timeIndex))
			timeIndex++;
		Preconditions.checkState(timeIndex == timeDiscretization.size() || relTime <= timeDiscretization.getX(timeIndex));
		return timeIndex;
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
				writeTimeFuncPlot(outputDir, prefix, magIndex);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
	}
	
	private void writeTimeFuncPlot(File outputDir, String prefix, int magIndex) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		FractileCurveCalculator timeFractals = buildFractileCalc(catalogTimeFuncs, magIndex);
		
		double mc = timeFuncMcs[magIndex];
		
		EvenlyDiscretizedFunc comcatCumulativeTimeFunc = new EvenlyDiscretizedFunc(timeDiscretization.getMinX(),
				timeDiscretization.getMaxX(), timeDiscretization.size());
		
		ObsEqkRupList comcatEvents = mc < 0 ? comcatEventsFilteredTDMc : this.comcatEvents;
		for (ObsEqkRupture rup : comcatEvents) {
			if (rup.getMag() < mc)
				continue;
			int timeIndex = getTimeFuncIndex(rup);
			for (int i=timeIndex; i<comcatCumulativeTimeFunc.size(); i++)
				comcatCumulativeTimeFunc.add(i, 1d);
		}
		
		String xAxisLabel = timeDays ? "Time (days)" : "Time (years)";
		CSVFile<String> csv = buildSimFractalFuncs(timeFractals, funcs, chars, comcatCumulativeTimeFunc, false, xAxisLabel);
		csv.writeToFile(new File(outputDir, prefix+".csv"));
		
		PlotSpec spec = new PlotSpec(funcs, chars, noTitles ? " " : "Cumulative Number Comparison, "+minMagLabel(mc),
				xAxisLabel, "Cumulative Num Earthquakes "+minMagLabel(mc));
		spec.setLegendVisible(true);
		
		double maxData = comcatCumulativeTimeFunc.getMaxY();
		double maxMean = timeFractals.getMeanCurve().getMaxY();
		double maxUpper = timeFractals.getFractile(0.975).getMaxY();
		
		double maxY = timeFuncMaxY != null ? timeFuncMaxY : Math.max(2*maxData,
				Math.max(Math.min(2*maxMean, 1.5*maxUpper), 1.25*maxMean));
		if (maxY <= 1d || !Double.isFinite(maxY))
			maxY = 1d;
		
		if (timeFuncAnns != null && !timeFuncAnns.isEmpty()) {
			List<XYTextAnnotation> anns = new ArrayList<>();
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
			spec.setPlotAnnotations(anns);
		}
		
		HeadlessGraphPanel gp = buildGraphPanel();
		gp.setLegendFontSize(18);
		double maxX = Math.max(1d, comcatCumulativeTimeFunc.getMaxX());
//		System.out.println("Drawing with bounds: "+maxX+", "+maxY);
		gp.setUserBounds(0d, maxX, 0d, maxY);

		gp.drawGraphPanel(spec, false, false);
		gp.getChartPanel().setSize(800, 600);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
//		gp.saveAsTXT(new File(outputDir, prefix+".txt").getAbsolutePath());
	}
	
	private void writeTimeDepMcPlot(File outputDir, String prefix) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		ArbitrarilyDiscretizedFunc totalFunc = new ArbitrarilyDiscretizedFunc("Time-Dep Mc");
		funcs.add(totalFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
		
		ArbitrarilyDiscretizedFunc mainshockFunc = new ArbitrarilyDiscretizedFunc("Mainshock Mc");
		funcs.add(mainshockFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		
		List<ArbitrarilyDiscretizedFunc> otherEventFuncs = new ArrayList<>();
		for (int i=1; i<mainshocksForTimeDepMc.size(); i++) {
			ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc(i == 1 ? "Other Event Mcs" : null);
			otherEventFuncs.add(func);
			funcs.add(func);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY));
		}
		
		ArbitrarilyDiscretizedFunc threshFunc = new ArbitrarilyDiscretizedFunc("Minimum Mc Threshold");
		threshFunc.set(0d, tdMinMag);
		threshFunc.set(timeDepMcFunc.getMaxX(), tdMinMag);
		funcs.add(threshFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 2f, Color.GRAY));
		
		long simStart = getConfig().getSimulationStartTimeMillis();
		for (int i=0; i<timeDiscretization.size(); i++) {
			double x = timeDiscretization.getX(i);
			long time;
			if (timeDays)
				time = simStart + (long)(x*(double)ProbabilityModelsCalc.MILLISEC_PER_DAY);
			else
				time = simStart + (long)(x*ProbabilityModelsCalc.MILLISEC_PER_YEAR);
			double maxMc = tdMinMag;
			for (int j=0; j<mainshocksForTimeDepMc.size(); j++) {
				double myMc = calcTimeDepMc(mainshocksForTimeDepMc.get(j), time, 0);
				if (Double.isNaN(myMc))
					continue;
				maxMc = Math.max(maxMc, myMc);
				if (j == 0)
					mainshockFunc.set(x, myMc);
				else
					otherEventFuncs.get(j-1).set(x, myMc);
			}
			totalFunc.set(x, maxMc);
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
	
	private void writeMagNumPlot(File outputDir, String prefix, List<? extends EvenlyDiscretizedFunc> mnds,
			EvenlyDiscretizedFunc dataMND, String yAxisLabel) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		FractileCurveCalculator calc = buildFractileCalc(mnds);
		
		CSVFile<String> csv = buildSimFractalFuncs(calc, funcs, chars, dataMND, true, "Magnitude");
		csv.writeToFile(new File(outputDir, prefix+".csv"));
		
		MinMaxAveTracker nonZeroRange = new MinMaxAveTracker();
		for (XY_DataSet func : funcs)
			for (Point2D pt : func)
				if (pt.getY() > 0)
					nonZeroRange.addValue(pt.getY());
		double maxY = Math.pow(10, Math.ceil(Math.log10(nonZeroRange.getMax())));
		double minY = Math.pow(10, Math.floor(Math.log10(nonZeroRange.getMin())));
		if (minY >= maxY)
			minY--;
		
		if ((float)comcatMc > (float)simMc) {
			XY_DataSet mcFunc = new DefaultXY_DataSet();
			mcFunc.set(comcatMc, minY);
			mcFunc.set(comcatMc, maxY);
			mcFunc.setName("Mc");
			funcs.add(mcFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, dataColor));
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, noTitles ? " " : "Magnitude Distribution Comparison",
				"Magnitude", yAxisLabel);
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = buildGraphPanel();
		gp.setLegendFontSize(18);
		gp.setUserBounds(simMc, comcatMND.getMaxX(), minY, maxY);

		gp.drawGraphPanel(spec, false, true);
		gp.getChartPanel().setSize(800, 600);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
//		gp.saveAsTXT(new File(outputDir, prefix+".txt").getAbsolutePath());
	}
	
	private void writeMagPercentileCumulativeNumPlot(File outputDir, String prefix,
			List<EvenlyDiscretizedFunc> cumulativeMNDs, EvenlyDiscretizedFunc comcatCumulativeMND)
			throws IOException {
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
			Arrays.sort(counts);
			Preconditions.checkState(mag == comcatCumulativeMND.getX(i));
			double dataVal = comcatCumulativeMND.getY(i);
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
			double percentile = 100d*numBelow/(double)counts.length;
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
			chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 1f, dataColor));
		}
		
		dataFunc.setName("ComCat Data Num≥M");
		funcs.add(dataFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
		
		PlotSpec spec = new PlotSpec(funcs, chars, noTitles ? " " : "Cumulative Count Data Percentile Comparison",
				"Minimum Magnitude", "Simulation Percentile");
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = buildGraphPanel();
		gp.setLegendFontSize(18);
		gp.setUserBounds(simMc, comcatMND.getMaxX(), minY, maxY);

		gp.drawGraphPanel(spec, false, false);
		gp.getChartPanel().setSize(800, 600);
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
//		gp.saveAsTXT(new File(outputDir, prefix+".txt").getAbsolutePath());
	}
	
	private CSVFile<String> buildSimFractalFuncs(FractileCurveCalculator calc, List<XY_DataSet> funcs,
			List<PlotCurveCharacterstics> chars, XY_DataSet dataFunc, boolean dataAsHist, String xAxisName) {
		XY_DataSet minCurve = notAsIncr(calc.getMinimumCurve());
		minCurve.setName("Extrema");
		XY_DataSet maxCurve = notAsIncr(calc.getMaximumCurve());
		maxCurve.setName(null);
		
		funcs.add(minCurve);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.BLACK));

		funcs.add(maxCurve);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DOTTED, 1f, Color.BLACK));
		
		String fractileStr = null;
		List<XY_DataSet> fractileFuncs = new ArrayList<>();
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
			funcs.add(func);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK));
		}

		AbstractXY_DataSet modeFunc = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<minCurve.size(); i++)
			modeFunc.set(minCurve.getX(i), calc.getEmpiricalDist(i).getMostCentralMode());
		if (plotIncludeMode) {
			modeFunc.setName("Mode");
			funcs.add(modeFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.CYAN.darker()));
		}

		AbstractXY_DataSet medianFunc = calc.getFractile(0.5);
		if (plotIncludeMedian) {
			medianFunc.setName("Median");
			funcs.add(medianFunc);
			if (plotIncludeMean)
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLUE));
			else
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
		}
		
		AbstractXY_DataSet meanFunc = calc.getMeanCurve();
		if (plotIncludeMean) {
			meanFunc.setName("Mean");
			funcs.add(meanFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK));
		}

		dataFunc.setName("ComCat Data");
		if (dataAsHist) {
			funcs.add(dataFunc);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 4f, dataColor));
		} else {
			funcs.add(dataFunc);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, dataColor));
		}
		
		// build CSV
		CSVFile<String> csv = new CSVFile<>(true);
		List<String> header = new ArrayList<>();
		header.add(xAxisName);
		header.add("ComCat Data");
		header.add("Simulation Mean");
		header.add("Simulation Median");
		header.add("Simulation Mode");
		header.add("Simulation Minimum");
		header.add("Simulation Maximum");
		for (double fractile : fractiles)
			header.add(optionalDigitDF.format(fractile*100d)+" %-ile");
		csv.addLine(header);
		Preconditions.checkState(dataFunc.size() == meanFunc.size());
		for (int i=0; i<dataFunc.size(); i++) {
			List<String> line = new ArrayList<>();
			line.add((float)dataFunc.getX(i)+"");
			line.add((float)dataFunc.getY(i)+"");
			line.add((float)meanFunc.getY(i)+"");
			line.add((float)medianFunc.getY(i)+"");
			line.add((float)modeFunc.getY(i)+"");
			line.add((float)minCurve.getY(i)+"");
			line.add((float)maxCurve.getY(i)+"");
			for (XY_DataSet fractileFunc : fractileFuncs)
				line.add((float)fractileFunc.getY(i)+"");
			csv.addLine(line);
		}
		return csv;
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
		for (FaultSectionPrefData sect : rupSet.getFaultSectionDataList()) {
			RuptureSurface surf = sect.getStirlingGriddedSurface(1d, false, false);
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
					ObsEqkRupList comcatEvents = magBins[m] < 0 ? comcatEventsFilteredTDMc : this.comcatEvents;
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
				
				XYZPlotSpec spec = new XYZPlotSpec(xyz, myCPT, noTitles ? " " : title, "Longitude", "Latitude", "Log10 "+zLabel);
				
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
			List<XY_DataSet> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			EvenlyDiscretizedFunc catalogFunc = catalogDepthDistributions[m];
			catalogFunc.scale(1d/(double)catalogsProcessed);
			EvenlyDiscretizedFunc inputFunc = new EvenlyDiscretizedFunc(catalogFunc.getMinX(), catalogFunc.getMaxX(), catalogFunc.size());
			for (ETAS_EqkRupture rup : getLauncher().getTriggerRuptures()) {
				if (magBins[m] < 0 && rup.getMag() < calcTimeDepMc(rup))
					continue;
				if (rup.getMag() >= magBins[m] && mapRegion.contains(rup.getHypocenterLocation()))
					inputFunc.add(inputFunc.getClosestXIndex(rup.getHypocenterLocation().getDepth()), 1d);
			}
			EvenlyDiscretizedFunc dataFunc = new EvenlyDiscretizedFunc(catalogFunc.getMinX(), catalogFunc.getMaxX(), catalogFunc.size());
			ObsEqkRupList comcatEvents = magBins[m] < 0 ? comcatEventsFilteredTDMc : this.comcatEvents;
			for (ObsEqkRupture rup : comcatEvents)
				if (rup.getMag() >= magBins[m] && mapRegion.contains(rup.getHypocenterLocation()))
					dataFunc.add(dataFunc.getClosestXIndex(rup.getHypocenterLocation().getDepth()), 1d);
			
			XY_DataSet catalogXY = getDepthXY(catalogFunc);
			catalogXY.setName("Simulation");
			funcs.add(catalogXY);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
			
			XY_DataSet dataXY = getDepthXY(dataFunc);
			dataXY.setName("ComCat Data");
			funcs.add(dataXY);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, dataColor));
			
			XY_DataSet inputXY = getDepthXY(inputFunc);
			inputXY.setName("Input Events");
			funcs.add(inputXY);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.GRAY));
			
			PlotSpec spec = new PlotSpec(funcs, chars, noTitles ? " " : "Depth Distribution Comparison, "+minMagLabel(magBins[m]),
					"Fraction", "Depth (km)");
			spec.setLegendVisible(true);
			
			HeadlessGraphPanel gp = buildGraphPanel();
			gp.setLegendFontSize(18);
			gp.setUserBounds(0d, 1d, 0d, dataFunc.getMaxX()+0.5*dataFunc.getDelta());
			gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);

			gp.drawGraphPanel(spec, false, false);
			gp.setyAxisInverted(true);
			gp.getChartPanel().setSize(600, 800);
			String myPrefix = prefix+"_"+minMagPrefix(magBins[m]);
			gp.saveAsPNG(new File(outputDir, myPrefix+".png").getAbsolutePath());
			gp.saveAsPDF(new File(outputDir, myPrefix+".pdf").getAbsolutePath());
//			gp.saveAsTXT(new File(outputDir, prefix+".txt").getAbsolutePath());
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
		lines.add("Last updated at "+SimulationMarkdownGenerator.df.format(new Date(curTime))
			+", "+getTimeLabel(curDuration, true).toLowerCase()+" after the simulation start time.");
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
	
	private TableBuilder mapPlotTable(String relativePathToOutputDir, String[][] prefixes,
			double[][] valueSummaries, boolean prob) {
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("");
		for (double duration : durations) {
			if ((float)duration == (float)curDuration)
				table.addColumn("Current ("+getTimeLabel(duration, false)+")");
			else if ((float)duration > (float)curDuration)
				table.addColumn("Forecast: "+getTimeLabel(duration, false));
			else
				table.addColumn(getTimeLabel(duration, false));
		}
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
				+ "2019_07_16-ComCatM7p1_ci38457511_ShakeMapSurfaces-noSpont-full_td-scale1.14");
//				+ "2019_08_20-ComCatM6p4_ci38443183_PointSources-noSpont-full_td-scale1.14");
//				+ "2019_10_15-ComCatM4p71_nc73292360_PointSources");
		File configFile = new File(simDir, "config.json");
		
		try {
			ETAS_Config config = ETAS_Config.readJSON(configFile);
			ETAS_Launcher launcher = new ETAS_Launcher(config, false);
			
			int maxNumCatalogs = 0;
//			int maxNumCatalogs = 20000;
			
			ETAS_ComcatComparePlot plot = new ETAS_ComcatComparePlot(config, launcher);
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
			
			System.exit(0);
		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
