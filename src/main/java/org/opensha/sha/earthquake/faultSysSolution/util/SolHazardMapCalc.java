package org.opensha.sha.earthquake.faultSysSolution.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.ReturnPeriodUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.DistCachedERFWrapper;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.erf.BaseFaultSystemSolutionERF;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.RupMFDsModule;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.HazardMapPlot;
import org.opensha.sha.earthquake.param.ApplyGardnerKnopoffAftershockFilterParam;
import org.opensha.sha.earthquake.param.AseismicityAreaReductionParam;
import org.opensha.sha.earthquake.param.BackgroundRupParam;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.earthquake.param.UseProxySectionsParam;
import org.opensha.sha.earthquake.param.UseRupMFDsParam;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGV_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.erf.FaultSystemSolutionERF;

public class SolHazardMapCalc {
	
	public static double SPACING_DEFAULT = 0.25;
	public static boolean PDFS = false;
	
	static {
		String spacingEnv = System.getenv("FST_HAZARD_SPACING");
		if (spacingEnv != null && !spacingEnv.isBlank()) {
			try {
				SPACING_DEFAULT = Double.parseDouble(spacingEnv);
			} catch (NumberFormatException e) {
				System.err.println("Couldn't parse FST_HAZARD_SPACING environmental variable as a double: "+spacingEnv);
				e.printStackTrace();
			}
		}
	}
	
	private FaultSystemSolution sol;
	private Supplier<ScalarIMR> gmpeRef;
	private GriddedRegion region;
	private double[] periods;
	
	private BaseFaultSystemSolutionERF fssERF;
	
	private List<Site> sites;
	
	private DiscretizedFunc[] xVals;
	private DiscretizedFunc[] logXVals;
	
	private List<DiscretizedFunc[]> curvesList;
	
	private List<XY_DataSet> extraFuncs;
	private List<PlotCurveCharacterstics> extraChars;

	private double maxSiteDist = 200d;
	private double skipMaxSiteDist = 300d;
	
	// ERF params
	private IncludeBackgroundOption backSeisOption;
	private BackgroundRupType backSeisType;
	private boolean applyAftershockFilter;
	private boolean aseisReducesArea = true;
	private boolean noMFDs = false;
	private boolean useProxyRuptures = true;
	
	public static ReturnPeriods[] MAP_RPS = { ReturnPeriods.TWO_IN_50, ReturnPeriods.TEN_IN_50 };
	
	public enum ReturnPeriods {
		TWO_IN_50(0.02, 50d, "2% in 50 year"),
		TEN_IN_50(0.1, 50d, "10% in 50 year"),
		FORTY_IN_50(0.4, 50d, "40% in 50 year");
		
		public final double refProb;
		public final double refDuration;
		public final String label;
		public final double oneYearProb;
		public final double returnPeriod;

		private ReturnPeriods(double refProb, double refDuration, String label) {
			this.refProb = refProb;
			this.refDuration = refDuration;
			this.label = label;
			this.oneYearProb = ReturnPeriodUtils.calcExceedanceProb(refProb, refDuration, 1d);
			this.returnPeriod = ReturnPeriodUtils.calcReturnPeriod(refProb, refDuration);
		}
	}

	public SolHazardMapCalc(FaultSystemSolution sol, Supplier<ScalarIMR> gmpeRef, GriddedRegion region,
			double... periods) {
		this(sol, gmpeRef, region, IncludeBackgroundOption.EXCLUDE, periods);
	}

	public SolHazardMapCalc(FaultSystemSolution sol, Supplier<ScalarIMR> gmpeRef, GriddedRegion region,
			IncludeBackgroundOption backSeisOption, double... periods) {
		this(sol, gmpeRef, region, backSeisOption, false, periods);
	}

	public SolHazardMapCalc(FaultSystemSolution sol, Supplier<ScalarIMR> gmpeRef, GriddedRegion region,
			IncludeBackgroundOption backSeisOption, boolean applyAftershockFilter, double... periods) {
		this.sol = sol;
		this.gmpeRef = gmpeRef;
		this.region = region;
		this.backSeisOption = backSeisOption;
		this.applyAftershockFilter = applyAftershockFilter;
		Preconditions.checkState(periods.length > 0);
		this.periods = periods;
		for (double period : periods)
			Preconditions.checkState(period == -1d || period >= 0d,
					"supplied map calculation periods must be -1 (PGV), 0 (PGA), or a positive value");
		
		if (gmpeRef != null) {
			sites = new ArrayList<>();
			ScalarIMR gmpe = gmpeRef.get();
			for (Location loc : region.getNodeList()) {
				Site site = new Site(loc);
				for (Parameter<?> param : gmpe.getSiteParams())
					site.addParameter((Parameter<?>) param.clone());
				sites.add(site);
			}
		}
	}
	
	public void setBackSeisOption(IncludeBackgroundOption backSeisOption) {
		Preconditions.checkState(fssERF == null, "ERF already initialized");
		this.backSeisOption = backSeisOption;
	}

	public void setBackSeisType(BackgroundRupType backSeisType) {
		Preconditions.checkState(fssERF == null, "ERF already initialized");
		this.backSeisType = backSeisType;
	}

	public void setApplyAftershockFilter(boolean applyAftershockFilter) {
		Preconditions.checkState(fssERF == null, "ERF already initialized");
		this.applyAftershockFilter = applyAftershockFilter;
	}

	public void setAseisReducesArea(boolean aseisReducesArea) {
		Preconditions.checkState(fssERF == null, "ERF already initialized");
		this.aseisReducesArea = aseisReducesArea;
	}

	public void setNoMFDs(boolean noMFDs) {
		Preconditions.checkState(fssERF == null, "ERF already initialized");
		this.noMFDs = noMFDs;
	}

	public void setUseProxyRups(boolean useProxyRuptures) {
		Preconditions.checkState(fssERF == null, "ERF already initialized");
		this.useProxyRuptures = useProxyRuptures;
	}
	
	public void setERF(BaseFaultSystemSolutionERF fssERF) {
		this.fssERF = fssERF;
	}
	
	public BaseFaultSystemSolutionERF getERF() {
		checkInitERF();
		return fssERF;
	}

	private synchronized void checkInitERF() {
		if (fssERF == null) {
			System.out.println("Building ERF");
			fssERF = new FaultSystemSolutionERF(sol);
			fssERF.setParameter(UseRupMFDsParam.NAME, !noMFDs);
			fssERF.setParameter(UseProxySectionsParam.NAME, useProxyRuptures);
			fssERF.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
			fssERF.setParameter(IncludeBackgroundParam.NAME, backSeisOption);
			if (backSeisOption != IncludeBackgroundOption.EXCLUDE && backSeisType != null)
				fssERF.setParameter(BackgroundRupParam.NAME, backSeisType);
			
			fssERF.setParameter(ApplyGardnerKnopoffAftershockFilterParam.NAME, applyAftershockFilter);
			fssERF.setParameter(AseismicityAreaReductionParam.NAME, aseisReducesArea);
			fssERF.getTimeSpan().setDuration(1d);
			
			fssERF.updateForecast();
		}
	}
	
	public void setXVals(DiscretizedFunc xVals) {
		DiscretizedFunc logXVals = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : xVals)
			logXVals.set(Math.log(pt.getX()), 0d);
		this.xVals = new DiscretizedFunc[periods.length];
		this.logXVals = new DiscretizedFunc[periods.length];
		for (int p=0; p<periods.length; p++) {
			this.xVals[p] = xVals;
			this.logXVals[p] = logXVals;
		}
	}
	
	public void setMaxSourceSiteDist(double maxDist) {
		this.maxSiteDist = maxDist;
	}
	
	public void setSkipMaxSourceSiteDist(double skipMaxSiteDist) {
		this.skipMaxSiteDist = skipMaxSiteDist;
	}
	
	public static DiscretizedFunc getDefaultXVals(double period) {
		return getDefaultXVals(new IMT_Info(), period);
	}
	
	public static DiscretizedFunc getDefaultXVals(IMT_Info imtInfo, double period) {
		if (period == -1d)
			return imtInfo.getDefaultHazardCurve(PGV_Param.NAME);
		else if (period == 0d)
			return imtInfo.getDefaultHazardCurve(PGA_Param.NAME);
		else
			return imtInfo.getDefaultHazardCurve(SA_Param.NAME);
	}
	
	private void checkInitXVals() {
		if (xVals == null) {
			synchronized (this) {
				if (xVals == null) {
					DiscretizedFunc[] xVals = new DiscretizedFunc[periods.length];
					DiscretizedFunc[] logXVals = new DiscretizedFunc[periods.length];
					IMT_Info imtInfo = new IMT_Info();
					for (int p=0; p<periods.length; p++) {
						xVals[p] = getDefaultXVals(imtInfo, periods[p]);
						logXVals[p] = new ArbitrarilyDiscretizedFunc();
						for (Point2D pt : xVals[p])
							logXVals[p].set(Math.log(pt.getX()), 0d);
					}
					this.logXVals = logXVals;
					this.xVals = xVals;
				}
			}
		}
	}
	
	public DiscretizedFunc getXVals(double period) {
		checkInitXVals();
		return xVals[periodIndex(period)];
	}
	
	private int periodIndex(double period) {
		for (int p=0; p<periods.length; p++)
			if ((float)period == (float)periods[p])
				return p;
		throw new IllegalStateException("Period not found: "+(float)period);
	}
	
	public void calcHazardCurves(int numThreads) {
		calcHazardCurves(numThreads, null);
	}
	
	public void calcHazardCurves(int numThreads, SolHazardMapCalc combineWith) {
		int numSites = region.getNodeCount();
		List<Integer> calcIndexes = new ArrayList<>();
		for (int i=0; i<numSites; i++)
			calcIndexes.add(i);
		
		calcHazardCurves(numThreads, calcIndexes, combineWith);
	}
	
	public void calcHazardCurves(int numThreads, List<Integer> calcIndexes, SolHazardMapCalc combineWith) {
		synchronized (this) {
			if (curvesList == null) {
				List<DiscretizedFunc[]> curvesList = new ArrayList<>();
				for (int p=0; p<periods.length; p++)
					curvesList.add(new DiscretizedFunc[region.getNodeCount()]);
				this.curvesList = curvesList;
			}
		}
		ConcurrentLinkedDeque<Integer> deque = new ConcurrentLinkedDeque<>(calcIndexes);
		
		checkInitERF();
		
		System.out.println("Calculating hazard maps with "+numThreads+" threads and "+calcIndexes.size()+" sites...");
		List<CalcThread> threads = new ArrayList<>();
		CalcTracker track = new CalcTracker(calcIndexes.size());
		for (int i=0; i<numThreads; i++) {
			CalcThread thread = new CalcThread(deque, fssERF, track, combineWith);
			thread.start();
			threads.add(thread);
		}
		
		for (CalcThread thread : threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
	}
	
	private class CalcTracker {
		private int numDone;
		private int size;
		private int mod;
		
		private String printPrefix;
		
		public CalcTracker(int size) {
			this.size = size;
			this.numDone = 0;
			if (size > 10000)
				mod = 200;
			else if (size > 5000)
				mod = 100;
			else if (size > 1000)
				mod = 20;
			else
				mod = 10;
			
			try {
				// see if we're in MPJ mode and include a more useful prefix
				int rank =  mpi.MPI.COMM_WORLD.Rank();
				String hostname = java.net.InetAddress.getLocalHost().getHostName();
				if (hostname != null && !hostname.isBlank())
					printPrefix = hostname+", "+rank+": ";
				else
					printPrefix = rank+": ";
			} catch (Throwable t) {
				printPrefix = "";
			}
		}
		
		public synchronized void taskCompleted() {
			numDone++;
			if (numDone == size || numDone % mod == 0) {
				System.out.println(printPrefix+"Computed "+numDone+"/"+size+" curves ("+percentDF.format((double)numDone/(double)size)+")");
			}
		}
	}
	
	protected static DecimalFormat percentDF = new DecimalFormat("0.00%");
	protected static DecimalFormat twoDigitsDF = new DecimalFormat("0.00");
	protected static DecimalFormat oDF = new DecimalFormat("0.#");
	
	private class CalcThread extends Thread {
		private ConcurrentLinkedDeque<Integer> calcIndexes;
		private AbstractERF erf;
		private int numFaultSysSources;
		private GriddedRegion gridSourceReg;
		private CalcTracker track;
		private SolHazardMapCalc combineWith;
		
		public CalcThread(ConcurrentLinkedDeque<Integer> calcIndexes, BaseFaultSystemSolutionERF erf,
				CalcTracker track, SolHazardMapCalc combineWith) {
			this.calcIndexes = calcIndexes;
			this.track = track;
			this.combineWith = combineWith;
			this.numFaultSysSources = erf.getNumFaultSystemSources();
			IncludeBackgroundOption bgOption = (IncludeBackgroundOption) erf.getParameter(IncludeBackgroundParam.NAME).getValue();
			if (bgOption == IncludeBackgroundOption.INCLUDE || bgOption == IncludeBackgroundOption.ONLY)
				gridSourceReg = erf.getSolution().requireModule(GridSourceProvider.class).getGriddedRegion();
			this.erf = new DistCachedERFWrapper(erf);
		}

		@Override
		public void run() {
			ScalarIMR gmpe = gmpeRef.get();
			
			HazardCurveCalculator calc = new HazardCurveCalculator();
			calc.setMaxSourceDistance(maxSiteDist);
			while (true) {
				Integer index = calcIndexes.pollFirst();
				if (index == null)
					break;
				Site site = sites.get(index);
				
				if (skipMaxSiteDist > 0d && Double.isFinite(skipMaxSiteDist)) {
					// see if we should just skip this site
					if (shouldSkipSite(site, skipMaxSiteDist, erf, numFaultSysSources, gridSourceReg)) {
						// can skip this site, no sources within skipMaxSiteDist
						checkInitXVals();
						for (int p=0; p<periods.length; p++) {
							DiscretizedFunc curve = xVals[p].deepClone();
							for (int i=0; i<curve.size(); i++)
								curve.set(i, 0d);
							if (combineWith != null) {
								// add in
								DiscretizedFunc oCurve = combineWith.curvesList.get(p)[index];
								Preconditions.checkNotNull(oCurve, "CombineWith curve is null for period=%s, index=%s",
										(Double)periods[p], (Integer)index);
								combineIn(curve, oCurve);
							}
							curvesList.get(p)[index] = curve;
						}
						track.taskCompleted();
						continue;
					}
				}
				
				List<DiscretizedFunc> curves = calcSiteCurves(calc, erf, gmpe, site, combineWith, index);
				
				for (int p=0; p<periods.length; p++)
					curvesList.get(p)[index] = curves.get(p);
				
				track.taskCompleted();
			}
		}
	}
	
	public static boolean shouldSkipSite(Site site, double skipMaxSiteDist, AbstractERF erf,
			int numFaultSysSources, GriddedRegion gridSourceReg) {
		if (!(skipMaxSiteDist > 0d && Double.isFinite(skipMaxSiteDist)))
			return false;
		boolean hasSourceWithin = false;
		if (gridSourceReg != null) {
			Location siteLoc = site.getLocation();
			hasSourceWithin = gridSourceReg.contains(siteLoc) ||
					gridSourceReg.distanceToLocation(siteLoc) <= skipMaxSiteDist;
		}
		
		for (int sourceID=0; !hasSourceWithin && sourceID<numFaultSysSources; sourceID++) {
			ProbEqkSource source = erf.getSource(sourceID);
			if (source.getMinDistance(site) < skipMaxSiteDist) {
				hasSourceWithin = true;
				break;
			}
		}
		
		return !hasSourceWithin;
	}
	
	private List<DiscretizedFunc> calcSiteCurves(HazardCurveCalculator calc, AbstractERF erf, ScalarIMR gmpe, Site site,
			SolHazardMapCalc combineWith, int index) {
		checkInitXVals();
		List<DiscretizedFunc> ret = new ArrayList<>(periods.length);
		
		for (int p=0; p<periods.length; p++) {
			setIMforPeriod(gmpe, periods[p]);
			DiscretizedFunc logCurve = logXVals[p].deepClone();
			calc.getHazardCurve(logCurve, site, gmpe, erf);
			DiscretizedFunc curve = xVals[p].deepClone();
			for (int i=0; i<curve.size(); i++)
				curve.set(i, logCurve.getY(i));
			
			if (combineWith != null) {
				DiscretizedFunc oCurve = combineWith.curvesList.get(p)[index];
				Preconditions.checkNotNull(oCurve, "CombineWith curve is null for period=%s, index=%s",
						(Double)periods[p], (Integer)index);
				combineIn(curve, oCurve);
			}
			
			ret.add(curve);
		}
		return ret;
	}
	
	public static void setIMforPeriod(ScalarIMR gmpe, double period) {
		if (period == -1d) {
			gmpe.setIntensityMeasure(PGV_Param.NAME);
		} else if (period == 0d) {
			gmpe.setIntensityMeasure(PGA_Param.NAME);
		} else {
			Preconditions.checkState(period > 0);
			gmpe.setIntensityMeasure(SA_Param.NAME);
			SA_Param.setPeriodInSA_Param(gmpe.getIntensityMeasure(), period);
		}
	}
	
	private static void combineIn(DiscretizedFunc curve, DiscretizedFunc oCurve) {
		Preconditions.checkState(oCurve.size() == curve.size());
		for (int i=0; i<curve.size(); i++) {
			Point2D pt1 = curve.get(i);
			Point2D pt2 = oCurve.get(i);
			Preconditions.checkState((float)pt1.getX() == (float)pt2.getX());
			if (pt2.getY() > 0) {
				if (pt1.getY() == 0)
					curve.set(i, pt2.getY());
				else
					curve.set(i, 1d - (1d - pt1.getY())*(1d - pt2.getY()));
			}
		}
	}
	
	public DiscretizedFunc[] getCurves(double period) {
		return curvesList.get(periodIndex(period));
	}
	
	public GriddedGeoDataSet buildMap(double period, ReturnPeriods returnPeriod) {
		return buildMap(period, returnPeriod.oneYearProb, false);
	}
	
	public GriddedGeoDataSet buildMap(double period, double curveLevel, boolean isProbAtIML) {
		int p = periodIndex(period);
		
		Preconditions.checkState(curvesList != null, "Must call calcHazardCurves first");
		
		GriddedGeoDataSet xyz = new GriddedGeoDataSet(region, false);
		
		DiscretizedFunc[] curves = curvesList.get(p);
		Preconditions.checkState(curves.length == region.getNodeCount());
		
		for (int i=0; i<curves.length; i++) {
			DiscretizedFunc curve = curves[i];
			Preconditions.checkNotNull(curve, "Curve not calculated at index %s", i);
			double val;
			if (isProbAtIML) {
				// curveLevel is an IML, return the probability of exceeding
				val = curve.getInterpolatedY_inLogXLogYDomain(curveLevel);
			} else {
				// curveLevel is a probability, return the IML at that probability
				if (curveLevel > curve.getMaxY())
					val = 0d;
				else if (curveLevel < curve.getMinY())
					// saturated
					val = curve.getMaxX();
				else
					val = curve.getFirstInterpolatedX_inLogXLogYDomain(curveLevel);
			}
			
			xyz.set(i, val);
		}
		
		return xyz;
	}
	
	public File plotMap(File outputDir, String prefix, GriddedGeoDataSet xyz, CPT cpt,
			String title, String zLabel) throws IOException {
		return plotMap(outputDir, prefix, xyz, cpt, title, zLabel, false);
	}
	
	public File plotMap(File outputDir, String prefix, GriddedGeoDataSet xyz, CPT cpt,
			String title, String zLabel, boolean diffStats) throws IOException {
		MapPlot plot = buildMapPlot(outputDir, prefix, xyz, cpt, title, zLabel, diffStats);
		return new File(plot.outputDir, prefix+".png");
	}
	
	public MapPlot buildMapPlot(File outputDir, String prefix, GriddedGeoDataSet xyz, CPT cpt,
			String title, String zLabel, boolean diffStats) throws IOException {
		GriddedRegion gridReg = xyz.getRegion();
		Range lonRange = new Range(
				Math.min(gridReg.getMinLon()-0.05, xyz.getMinLon()-0.75*gridReg.getLonSpacing()),
				Math.max(gridReg.getMaxLon()+0.05, xyz.getMaxLon()+0.75*gridReg.getLonSpacing()));
		Range latRange = new Range(
				Math.min(gridReg.getMinLat()-0.05, xyz.getMinLat()-0.75*gridReg.getLatSpacing()),
				Math.max(gridReg.getMaxLat()+0.05, xyz.getMaxLat()+0.75*gridReg.getLatSpacing()));
		double latSpan = latRange.getLength();
		double lonSpan = lonRange.getLength();
		double maxSpan = Math.max(latSpan, lonSpan);
		synchronized (this) {
			if (extraFuncs == null) {
				List<XY_DataSet> extraFuncs = new ArrayList<>();
				List<PlotCurveCharacterstics> extraChars = new ArrayList<>();
				
				Color outlineColor = new Color(0, 0, 0, 180);
				Color faultColor = new Color(0, 0, 0, 100);
				
				float outlineWidth = maxSpan > 30d ? 1f : 2f;
				
				if (!region.isRectangular()) {
					DefaultXY_DataSet outline = new DefaultXY_DataSet();
					for (Location loc : region.getBorder())
						outline.set(loc.getLongitude(), loc.getLatitude());
					outline.set(outline.get(0));
					
					extraFuncs.add(outline);
					extraChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, outlineColor));
				}
				
				XY_DataSet[] boundaries = PoliticalBoundariesData.loadDefaultOutlines(region);
				if (boundaries != null) {
					for (XY_DataSet boundary : boundaries) {
						extraFuncs.add(boundary);
						extraChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, outlineWidth, outlineColor));
					}
				}
				
				if (sol != null) {
					PlotCurveCharacterstics traceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, faultColor);
					
					DefaultXY_DataSet prevTrace = null;
					for (FaultSection sect : sol.getRupSet().getFaultSectionDataList()) {
						DefaultXY_DataSet trace = new DefaultXY_DataSet();
						for (Location loc : sect.getFaultTrace())
							trace.set(loc.getLongitude(), loc.getLatitude());
						
						boolean reused = false;
						if (prevTrace != null) {
							Point2D prevLast = prevTrace.get(prevTrace.size()-1);
							Point2D newFirst = trace.get(0);
							if ((float)prevLast.getX() == (float)newFirst.getX() && (float)prevLast.getY() == (float)newFirst.getY()) {
								// reuse
								for (int i=1; i<trace.size(); i++)
									prevTrace.set(trace.get(i));
								reused = true;
							}
						}
						if (!reused) {
							extraFuncs.add(trace);
							prevTrace = trace;
							extraChars.add(traceChar);
						}
					}
				}
				this.extraChars = extraChars;
				this.extraFuncs = extraFuncs;
			}
		}
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		XYZPlotSpec spec = new XYZPlotSpec(xyz, cpt, title, "Longitude", "Latitude", zLabel);
		spec.setCPTPosition(RectangleEdge.BOTTOM);
		
		spec.setXYElems(extraFuncs);
		spec.setXYChars(extraChars);
		
		if (diffStats) {
			// these are percent differences, add stats
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			int exactly0 = 0;
			int numBelow = 0;
			int numAbove = 0;
			int numWithin1 = 0;
			int numWithin5 = 0;
			int numWithin10 = 0;
			double mean = 0d;
			double meanAbs = 0d;
			
			int numFinite = 0;
			for (int i=0; i<xyz.size(); i++) {
				double val = xyz.get(i);
				if (Double.isFinite(val)) {
					min = Math.min(min, val);
					max = Math.max(max, val);
				}
				if ((float)val == 0f)
					exactly0++;
				if (val >= -1d && val <= 1d)
					numWithin1++;
				if (val >= -5d && val <= 5d)
					numWithin5++;
				if (val >= -10d && val <= 10d)
					numWithin10++;
				if ((float)val < 0f)
					numBelow++;
				if ((float)val > 0f)
					numAbove++;
				if (Double.isFinite(val)) {
					numFinite++;
					mean += val;
					meanAbs += Math.abs(val);
				}
			}
			mean /= (double)numFinite;
			meanAbs /= (double)numFinite;
			
			List<String> labels = new ArrayList<>();
			labels.add("Range: ["+oDF.format(min)+"%,"+oDF.format(max)+"%]");
			labels.add("Mean: "+twoDigitsDF.format(mean)+"%, Abs: "+twoDigitsDF.format(meanAbs)+"%");
			if (exactly0 >= numFinite/2)
				labels.add("Exactly 0%: "+percentDF.format((double)exactly0/(double)numFinite));
			labels.add(percentDF.format((double)numBelow/(double)numFinite)
					+" < 0, "+percentDF.format((double)numAbove/(double)numFinite)+" > 0");
			labels.add("Within 1%: "+percentDF.format((double)numWithin1/(double)numFinite));
			labels.add("Within 5%: "+percentDF.format((double)numWithin5/(double)numFinite));
			labels.add("Within 10%: "+percentDF.format((double)numWithin10/(double)numFinite));
			
			// default to top right, but see if there's blank space anywhere
			boolean top;
			boolean left;
			double testTopLat = latRange.getLowerBound() + 0.8*latRange.getLength();
			double testBotLat = latRange.getLowerBound() + 0.2*latRange.getLength();
			double testRightLon = lonRange.getLowerBound() + 0.8*lonRange.getLength();
			double testLeftLon = lonRange.getLowerBound() + 0.2*lonRange.getLength();
			if (!region.contains(new Location(testTopLat, testRightLon))) {
				top = true;
				left = false;
			} else if (!region.contains(new Location(testTopLat, testLeftLon))) {
				top = true;
				left = true;
			} else if (!region.contains(new Location(testBotLat, testLeftLon))) {
				top = false;
				left = true;
			} else if (!region.contains(new Location(testBotLat, testRightLon))) {
				top = false;
				left = false;
			}
			
			double yDiff = latSpan;
			if (yDiff > 30)
				yDiff /= 30d;
			else if (yDiff > 10)
				yDiff /= 30d;
			else if (yDiff > 5d)
				yDiff /= 20d;
			else
				yDiff /= 15d;
			if (lonSpan > 1.75*latSpan)
				// really wide (conus?), stretch it back out
				yDiff *= 1.5;
			double y = latRange.getUpperBound() - 0.5*yDiff;
			
			for (String label : labels) {
				double x = lonRange.getUpperBound();
				XYTextAnnotation ann = new XYTextAnnotation(label+"  ", x, y);
				ann.setTextAnchor(TextAnchor.TOP_RIGHT);
				ann.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
				y -= yDiff;
				spec.addPlotAnnotation(ann);
			}
		}
		
		gp.drawGraphPanel(spec, false, false, lonRange, latRange);
		
		double tick;
		if (maxSpan > 20)
			tick = 5d;
		else if (maxSpan > 8)
			tick = 2d;
		else if (maxSpan > 3)
			tick = 1d;
		else if (maxSpan > 1)
			tick = 0.5d;
		else
			tick = 0.2;
		PlotUtils.setTick(gp.getXAxis(), tick);
		PlotUtils.setTick(gp.getYAxis(), tick);
		
		PlotUtils.fixAspectRatio(gp, 800, true);
		
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		if (PDFS)
			gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
		
		return new MapPlot(spec, lonRange, latRange, tick, tick, outputDir, prefix);
	}
	
	public static class MapPlot {
		public final XYZPlotSpec spec;
		public final Range xRnage;
		public final Range yRange;
		public final double xTick;
		public final double yTick;
		public final File outputDir;
		public final String prefix;
		
		public MapPlot(XYZPlotSpec spec, Range xRnage, Range yRange, double xTick, double yTick, File outputDir,
				String prefix) {
			super();
			this.spec = spec;
			this.xRnage = xRnage;
			this.yRange = yRange;
			this.xTick = xTick;
			this.yTick = yTick;
			this.outputDir = outputDir;
			this.prefix = prefix;
		}
	}
	
	public static String getCSV_FileName(String prefix, double period) {
		String fileName = prefix;
		if (period == -1d)
			fileName += "_pgv";
		else if (period == 0d)
			fileName += "_pga";
		else
			fileName += "_sa_"+oDF.format(period);
		return fileName+".csv";
	}
	
	public void writeCurvesCSVs(File outputDir, String prefix, boolean gzip) throws IOException {
		writeCurvesCSVs(outputDir, prefix, gzip, false);
	}
	
	public void writeCurvesCSVs(File outputDir, String prefix, boolean gzip, boolean allowNull) throws IOException {
		for (double period : periods) {
			String fileName = getCSV_FileName(prefix, period);
			if (gzip)
				fileName += ".gz";
			File outputFile = new File(outputDir, fileName);
			
			writeCurvesCSV(outputFile, period, allowNull);
		}
	}
	
	public void writeCurvesCSV(File outputFile, double period) throws IOException {
		writeCurvesCSV(outputFile, period, false);
	}
	
	public void writeCurvesCSV(File outputFile, double period, boolean allowNull) throws IOException {
		
		Preconditions.checkState(curvesList != null, "Must call calcHazardCurves first");
		int p = periodIndex(period);
		
		DiscretizedFunc[] curves = curvesList.get(p);
		Preconditions.checkState(allowNull || curves[0] != null, "Curve not calculated at index 0");
		
		writeCurvesCSV(outputFile, curves, region.getNodeList(), allowNull);
	}
	
	public static CSVFile<String> buildCurvesCSV(DiscretizedFunc[] curves, LocationList locs) {
		return buildCurvesCSV(curves, locs, false);
	}
	
	public static CSVFile<String> buildCurvesCSV(DiscretizedFunc[] curves, LocationList locs, boolean allowNull) {
		CSVFile<String> csv = new CSVFile<>(true);
		
		List<String> header = new ArrayList<>();
		header.add("Index");
		header.add("Latitude");
		header.add("Longitude");
		DiscretizedFunc refCurve = null;
		for (DiscretizedFunc curve : curves) {
			if (curve != null) {
				refCurve = curve;
				break;
			}
		}
		Preconditions.checkNotNull(refCurve, "All curves are null");
		for (int i=0; i<refCurve.size(); i++)
			header.add((float)refCurve.getX(i)+"");
		
		csv.addLine(header);
		
		for (int i=0; i<curves.length; i++) {
			DiscretizedFunc curve = curves[i];
			if (allowNull && curve == null)
				continue;
			Preconditions.checkNotNull(curve, "Curve not calculated at index %s", i);
			
			List<String> line = new ArrayList<>();
			line.add(i+"");
			Location loc = locs.get(i);
			line.add(loc.getLatitude()+"");
			line.add(loc.getLongitude()+"");
			for (int j=0; j<curve.size(); j++)
				line.add(curve.getY(j)+"");
			csv.addLine(line);
		}
		
		return csv;
	}
	
	public static void writeCurvesCSV(File outputFile, DiscretizedFunc[] curves, LocationList locs) throws IOException {
		writeCurvesCSV(outputFile, curves, locs, false);
	}
	
	public static void writeCurvesCSV(File outputFile, DiscretizedFunc[] curves, LocationList locs, boolean allowNull) throws IOException {
		CSVFile<String> csv = buildCurvesCSV(curves, locs, allowNull);
		
		csv.writeToFile(outputFile);
	}
	
	public static SolHazardMapCalc loadCurves(FaultSystemSolution sol, GriddedRegion region, double[] periods,
			File dir, String prefix) throws IOException {
		List<DiscretizedFunc[]> curvesList = new ArrayList<>();
		for (double period : periods) {
			File curvesFile = new File(dir, getCSV_FileName(prefix, period));
			if (!curvesFile.exists())
				curvesFile = new File(curvesFile.getAbsolutePath()+".gz");
			Preconditions.checkState(curvesFile.exists(), "Curve files doesn't exist: %s", curvesFile.getAbsolutePath());
			
			CSVFile<String> csv = CSVFile.readFile(curvesFile, true);
			DiscretizedFunc[] curves = loadCurvesCSV(csv, region);
			
			curvesList.add(curves);
		}
		
		return forCurves(sol, region, periods, curvesList);
	}
	
	public static SolHazardMapCalc forCurves(FaultSystemSolution sol, GriddedRegion region, double[] periods,
			List<DiscretizedFunc[]> curvesList) throws IOException {
		Preconditions.checkState(periods.length == curvesList.size());
		for (DiscretizedFunc[] curves : curvesList)
			Preconditions.checkState(region.getNodeCount() == curves.length);
		SolHazardMapCalc calc = new SolHazardMapCalc(sol, null, region, periods);
		calc.curvesList = curvesList;
		return calc;
	}
	
	public static DiscretizedFunc[] loadCurvesCSV(CSVFile<String> csv, GriddedRegion region) {
		return loadCurvesCSV(csv, region, false);
	}
	
	public static DiscretizedFunc[] loadCurvesCSV(CSVFile<String> csv, GriddedRegion region, boolean allowNull) {
		ArbitrarilyDiscretizedFunc xVals = new ArbitrarilyDiscretizedFunc();
		for (int col=3; col<csv.getNumCols(); col++)
			xVals.set(csv.getDouble(0, col), 0d);
		
		boolean remap = !allowNull && region != null && region.getNodeCount() != csv.getNumRows()-1;
		if (remap)
			Preconditions.checkState(region.getNodeCount() < csv.getNumRows()-1,
					"Can only remap if the passed in region is a subset of the CSV region");
		
		DiscretizedFunc[] curves;
		if (region != null) {
			Preconditions.checkState(allowNull || remap || csv.getNumRows() == region.getNodeCount()+1,
					"Region node count discrepancy: %s != %s", csv.getNumRows()-1, region.getNodeCount());
			
			curves = new DiscretizedFunc[region.getNodeCount()];
		} else {
			curves = new DiscretizedFunc[csv.getNumRows()-1];
		}
		
		for (int row=1; row<csv.getNumRows(); row++) {
			int index = allowNull ? csv.getInt(row, 0) : row-1;
			if (region != null) {
				Location loc = new Location(csv.getDouble(row, 1), csv.getDouble(row, 2));
				if (remap) {
					index = region.indexForLocation(loc);
					if (index < 0)
						continue;
				} else {
					Location regLoc = region.getLocation(index);
					Preconditions.checkState(LocationUtils.areSimilar(loc, regLoc),
							"Region location mismatch: %s != %s", loc, regLoc);
				}
			}
			if (!remap) {
				int csvIndex = csv.getInt(row, 0);
				Preconditions.checkState(index == csvIndex, "CSV index mismatch: %s != %s", index, csvIndex);
			}
			DiscretizedFunc curve = new ArbitrarilyDiscretizedFunc();
			for (int i=0; i<xVals.size(); i++)
				curve.set(xVals.getX(i), csv.getDouble(row, i+3));
			curves[index] = curve;
		}
		if (remap) {
			// make sure they were all mapped
			for (int i=0; i<curves.length; i++)
				Preconditions.checkNotNull(curves[i],
						"Can only remap if the passed in region is a subset of the CSV region. No match for index %s: %s",
						i, region.locationForIndex(i));
		}
		return curves;
	}
	
	private static Options createOptions() {
		Options ops = new Options();
		
		ops.addOption(FaultSysTools.threadsOption());
		
		Option inputOption = new Option("if", "input-file", true, "Input solution file");
		inputOption.setRequired(true);
		ops.addOption(inputOption);
		
		Option compOption = new Option("cf", "comp-file", true, "Comparison solution file");
		compOption.setRequired(false);
		ops.addOption(compOption);
		
		Option outputOption = new Option("od", "output-dir", true, "Output directory");
		outputOption.setRequired(true);
		ops.addOption(outputOption);
		
		Option gridSpacingOption = new Option("gs", "grid-spacing", true, "Grid spacing in degrees. Default: "+(float)SPACING_DEFAULT);
		gridSpacingOption.setRequired(false);
		ops.addOption(gridSpacingOption);
		
		Option recalcOption = new Option("rc", "recalc", false, "Flag to force recalculation (ignore existing curves files)");
		recalcOption.setRequired(false);
		ops.addOption(recalcOption);
		
		Option periodsOption = new Option("p", "periods", true, "Calculation period(s). Mutliple can be comma separated");
		periodsOption.setRequired(true);
		ops.addOption(periodsOption);
		
		Option gmpeOption = new Option("g", "gmpe", true, "GMPE name. Default is "+AttenRelRef.ASK_2014.name());
		gmpeOption.setRequired(false);
		ops.addOption(gmpeOption);
		
		Option distOption = new Option("md", "max-distance", true, "Maximum distance for hazard curve calculations in km. Default is 200 km");
		distOption.setRequired(false);
		ops.addOption(distOption);
		
		return ops;
	}
	
	public static void main(String[] args) throws IOException {
		CommandLine cmd = FaultSysTools.parseOptions(createOptions(), args, SolHazardMapCalc.class);
		
		File inputFile = new File(cmd.getOptionValue("input-file"));
		FaultSystemSolution sol = FaultSystemSolution.load(inputFile);
		
		File outputDir = new File(cmd.getOptionValue("output-dir"));
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		Region region = new ReportMetadata(new RupSetMetadata(null, sol)).region;
		
		double gridSpacing = SPACING_DEFAULT;
		if (cmd.hasOption("grid-spacing"))
			gridSpacing = Double.parseDouble(cmd.getOptionValue("grid-spacing"));
		
		GriddedRegion gridReg = new GriddedRegion(region, gridSpacing, GriddedRegion.ANCHOR_0_0);
		
		AttenRelRef gmpe = AttenRelRef.ASK_2014;
		if (cmd.hasOption("gmpe"))
			gmpe = AttenRelRef.valueOf(cmd.getOptionValue("gmpe"));
		
		List<Double> periodsList = new ArrayList<>();
		String periodsStr = cmd.getOptionValue("periods");
		if (periodsStr.contains(",")) {
			String[] split = periodsStr.split(",");
			for (String str : split)
				periodsList.add(Double.parseDouble(str));
		} else {
			periodsList.add(Double.parseDouble(periodsStr));
		}
		double[] periods = Doubles.toArray(periodsList);
		
		SolHazardMapCalc calc = null;
		
		boolean recalc = cmd.hasOption("recalc");
		
		if (!recalc) {
			// see if we already have curves
			try {
				calc = loadCurves(sol, gridReg, periods, outputDir, "curves");
				System.out.println("Loaded existing curves!");
			} catch (Exception e) {
//				e.printStackTrace();
			}
		}
		
		Double maxDistance = null;
		if (cmd.hasOption("max-distance"))
			maxDistance = Double.parseDouble(cmd.getOptionValue("max-distance"));
		
		if (calc == null) {
			// need to calculate
			calc = new SolHazardMapCalc(sol, gmpe, gridReg, periods);
			
			if (maxDistance != null)
				calc.setMaxSourceSiteDist(maxDistance);
			
			calc.calcHazardCurves(FaultSysTools.getNumThreads(cmd));
			
			calc.writeCurvesCSVs(outputDir, "curves", gridSpacing < 0.1d);
		}
		
		SolHazardMapCalc compCalc = null;
		if (cmd.hasOption("comp-file")) {
			System.out.println("Calculating comparison...");
			FaultSystemSolution compSol = FaultSystemSolution.load(new File(cmd.getOptionValue("comp-file")));
			
			if (!recalc) {
				// see if we already have curves
				try {
					compCalc = loadCurves(compSol, gridReg, periods, outputDir, "comp_curves");
					System.out.println("Loaded existing curves!");
				} catch (Exception e) {}
			}
			if (compCalc == null) {
				// need to calculate
				compCalc = new SolHazardMapCalc(compSol, gmpe, gridReg, periods);
				
				if (maxDistance != null)
					compCalc.setMaxSourceSiteDist(maxDistance);
				
				compCalc.calcHazardCurves(FaultSysTools.getNumThreads(cmd));
				
				compCalc.writeCurvesCSVs(outputDir, "comp_curves", gridSpacing < 0.1d);
			}
		}
		
		HazardMapPlot plot = new HazardMapPlot(gmpe, gridSpacing, periods);
		
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		List<String> lines = new ArrayList<>();
		
		lines.add("# Hazard Maps");
		lines.add("");
		
		lines.addAll(plot.plot(resourcesDir, resourcesDir.getName(), "", gridReg, calc, compCalc));
		
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}

}
