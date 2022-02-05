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
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.HazardMapPlot;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
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
	
	private FaultSystemSolution sol;
	private Supplier<ScalarIMR> gmpeRef;
	private GriddedRegion region;
	private double[] periods;
	
	private FaultSystemSolutionERF fssERF;
	
	private List<Site> sites;
	
	private DiscretizedFunc[] xVals;
	private DiscretizedFunc[] logXVals;
	
	private List<DiscretizedFunc[]> curvesList;
	
	private List<XY_DataSet> extraFuncs;
	private List<PlotCurveCharacterstics> extraChars;
	
	private double maxSiteDist = 200d;
	
	public enum ReturnPeriods {
		TWO_IN_50(0.02, 50d, "2% in 50 year"),
		TEN_IN_50(0.1, 50d, "10% in 50 year");
		
		public final double refProb;
		public final double refDuration;
		public final String label;
		public final double oneYearProb;

		private ReturnPeriods(double refProb, double refDuration, String label) {
			this.refProb = refProb;
			this.refDuration = refDuration;
			this.label = label;
			this.oneYearProb = ReturnPeriodUtils.calcExceedanceProb(refProb, refDuration, 1d);
		}
	}

	public SolHazardMapCalc(FaultSystemSolution sol, Supplier<ScalarIMR> gmpeRef, GriddedRegion region,
			double... periods) {
		this(sol, gmpeRef, region, IncludeBackgroundOption.EXCLUDE, periods);
	}

	public SolHazardMapCalc(FaultSystemSolution sol, Supplier<ScalarIMR> gmpeRef, GriddedRegion region,
			IncludeBackgroundOption backSeisOption, double... periods) {
		this.sol = sol;
		this.gmpeRef = gmpeRef;
		this.region = region;
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
			
			fssERF = new FaultSystemSolutionERF(sol);
			fssERF.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
			fssERF.setParameter(IncludeBackgroundParam.NAME, backSeisOption);
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
	
	private void checkInitXVals() {
		if (xVals == null) {
			synchronized (this) {
				if (xVals == null) {
					DiscretizedFunc[] xVals = new DiscretizedFunc[periods.length];
					DiscretizedFunc[] logXVals = new DiscretizedFunc[periods.length];
					IMT_Info imtInfo = new IMT_Info();
					for (int p=0; p<periods.length; p++) {
						if (periods[p] == -1d)
							xVals[p] = imtInfo.getDefaultHazardCurve(PGV_Param.NAME);
						else if (periods[p] == 0d)
							xVals[p] = imtInfo.getDefaultHazardCurve(PGA_Param.NAME);
						else
							xVals[p] = imtInfo.getDefaultHazardCurve(SA_Param.NAME);
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
	
	private int periodIndex(double period) {
		for (int p=0; p<periods.length; p++)
			if ((float)period == (float)periods[p])
				return p;
		throw new IllegalStateException("Period not found: "+(float)period);
	}
	
	public void calcHazardCurves(int numThreads) {
		int numSites = region.getNodeCount();
		List<Integer> calcIndexes = new ArrayList<>();
		for (int i=0; i<numSites; i++)
			calcIndexes.add(i);
		
		calcHazardCurves(numThreads, calcIndexes);
	}
	
	void calcHazardCurves(int numThreads, List<Integer> calcIndexes) {
		synchronized (this) {
			if (curvesList == null) {
				List<DiscretizedFunc[]> curvesList = new ArrayList<>();
				for (int p=0; p<periods.length; p++)
					curvesList.add(new DiscretizedFunc[region.getNodeCount()]);
				this.curvesList = curvesList;
			}
		}
		ConcurrentLinkedDeque<Integer> deque = new ConcurrentLinkedDeque<>(calcIndexes);
		
		System.out.println("Calculating hazard maps with "+numThreads+" threads and "+calcIndexes.size()+" sites...");
		List<CalcThread> threads = new ArrayList<>();
		CalcTracker track = new CalcTracker(calcIndexes.size());
		for (int i=0; i<numThreads; i++) {
			CalcThread thread = new CalcThread(deque, fssERF, track);
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
		}
		
		public synchronized void taskCompleted() {
			numDone++;
			if (numDone == size || numDone % mod == 0) {
				System.out.println("Computed "+numDone+"/"+size+" curves ("+percentDF.format((double)numDone/(double)size)+")");
			}
		}
	}
	
	protected static DecimalFormat percentDF = new DecimalFormat("0.00%");
	protected static DecimalFormat twoDigitsDF = new DecimalFormat("0.00");
	protected static DecimalFormat oDF = new DecimalFormat("0.#");
	
	private class CalcThread extends Thread {
		private ConcurrentLinkedDeque<Integer> calcIndexes;
		private AbstractERF erf;
		private CalcTracker track;
		
		public CalcThread(ConcurrentLinkedDeque<Integer> calcIndexes, FaultSystemSolutionERF erf, CalcTracker track) {
			this.calcIndexes = calcIndexes;
			this.track = track;
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
				
				List<DiscretizedFunc> curves = calcSiteCurves(calc, erf, gmpe, site);
				
				for (int p=0; p<periods.length; p++)
					curvesList.get(p)[index] = curves.get(p);
				
				track.taskCompleted();
			}
		}
	}
	
	private List<DiscretizedFunc> calcSiteCurves(HazardCurveCalculator calc, AbstractERF erf, ScalarIMR gmpe, Site site) {
		checkInitXVals();
		List<DiscretizedFunc> ret = new ArrayList<>(periods.length);
		for (int p=0; p<periods.length; p++) {
			if (periods[p] == -1d) {
				gmpe.setIntensityMeasure(PGV_Param.NAME);
			} else if (periods[p] == 0d) {
				gmpe.setIntensityMeasure(PGA_Param.NAME);
			} else {
				Preconditions.checkState(periods[p] > 0);
				gmpe.setIntensityMeasure(SA_Param.NAME);
				SA_Param.setPeriodInSA_Param(gmpe.getIntensityMeasure(), periods[p]);
			}
			DiscretizedFunc logCurve = logXVals[p].deepClone();
			calc.getHazardCurve(logCurve, site, gmpe, erf);
			DiscretizedFunc curve = xVals[p].deepClone();
			for (int i=0; i<curve.size(); i++)
				curve.set(i, logCurve.getY(i));
			ret.add(curve);
		}
		return ret;
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
		synchronized (this) {
			if (extraFuncs == null) {
				List<XY_DataSet> extraFuncs = new ArrayList<>();
				List<PlotCurveCharacterstics> extraChars = new ArrayList<>();
				
				Color outlineColor = new Color(0, 0, 0, 180);
				Color faultColor = new Color(0, 0, 0, 100);
				
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
						extraChars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, outlineColor));
					}
				}
				
				PlotCurveCharacterstics traceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, faultColor);
				
				for (FaultSection sect : sol.getRupSet().getFaultSectionDataList()) {
					DefaultXY_DataSet trace = new DefaultXY_DataSet();
					for (Location loc : sect.getFaultTrace())
						trace.set(loc.getLongitude(), loc.getLatitude());
					
					extraFuncs.add(trace);
					extraChars.add(traceChar);
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
		
		GriddedRegion gridReg = xyz.getRegion();
		Range lonRange = new Range(
				Math.min(gridReg.getMinLon()-0.05, xyz.getMinLon()-0.75*gridReg.getLonSpacing()),
				Math.max(gridReg.getMaxLon()+0.05, xyz.getMaxLon()+0.75*gridReg.getLonSpacing()));
		Range latRange = new Range(
				Math.min(gridReg.getMinLat()-0.05, xyz.getMinLat()-0.75*gridReg.getLatSpacing()),
				Math.max(gridReg.getMaxLat()+0.05, xyz.getMaxLat()+0.75*gridReg.getLatSpacing()));
		
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
					mean += val;
					meanAbs += Math.abs(val);
				}
			}
			mean /= (double)xyz.size();
			meanAbs /= (double)xyz.size();
			
			List<String> labels = new ArrayList<>();
			labels.add("Range: ["+oDF.format(min)+"%,"+oDF.format(max)+"%]");
			labels.add("Mean: "+twoDigitsDF.format(mean)+"%, Abs: "+twoDigitsDF.format(meanAbs)+"%");
			if (exactly0 >= xyz.size()/2)
				labels.add("Exactly 0%: "+percentDF.format((double)exactly0/(double)xyz.size()));
			labels.add(percentDF.format((double)numBelow/(double)xyz.size())
					+" < 0, "+percentDF.format((double)numAbove/(double)xyz.size())+" > 0");
			labels.add("Within 1%: "+percentDF.format((double)numWithin1/(double)xyz.size()));
			labels.add("Within 5%: "+percentDF.format((double)numWithin5/(double)xyz.size()));
			labels.add("Within 10%: "+percentDF.format((double)numWithin10/(double)xyz.size()));
			double yDiff = latRange.getLength();
			if (yDiff > 10)
				yDiff /= 30d;
			else if (yDiff > 5d)
				yDiff /= 15d;
			else
				yDiff /= 10d;
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
		
		double maxSpan = Math.max(lonRange.getLength(), latRange.getLength());
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
		
		File ret = new File(outputDir, prefix+".png");
		gp.saveAsPNG(ret.getAbsolutePath());
		
		return ret;
	}
	
	private static String getCSV_FileName(String prefix, double period) {
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
		for (double period : periods) {
			String fileName = getCSV_FileName(prefix, period);
			if (gzip)
				fileName += ".gz";
			File outputFile = new File(outputDir, fileName);
			
			writeCurvesCSV(outputFile, period);
		}
	}
	
	public void writeCurvesCSV(File outputFile, double period) throws IOException {
		
		Preconditions.checkState(curvesList != null, "Must call calcHazardCurves first");
		int p = periodIndex(period);
		
		CSVFile<String> csv = new CSVFile<>(true);
		
		DiscretizedFunc[] curves = curvesList.get(p);
		Preconditions.checkNotNull(curves[0], "Curve not calculated at index 0");
		
		List<String> header = new ArrayList<>();
		header.add("Index");
		header.add("Latitude");
		header.add("Longitude");
		for (int i=0; i<curves[0].size(); i++)
			header.add((float)curves[0].getX(i)+"");
		
		csv.addLine(header);
		
		for (int i=0; i<curves.length; i++) {
			DiscretizedFunc curve = curves[i];
			Preconditions.checkNotNull(curve, "Curve not calculated at index %s", i);
			
			List<String> line = new ArrayList<>();
			line.add(i+"");
			Location loc = region.getLocation(i);
			line.add(loc.getLatitude()+"");
			line.add(loc.getLongitude()+"");
			for (int j=0; j<curve.size(); j++)
				line.add(curve.getY(j)+"");
			csv.addLine(line);
		}
		
		csv.writeToFile(outputFile);
	}
	
	public static SolHazardMapCalc loadCurves(FaultSystemSolution sol, GriddedRegion region, double[] periods,
			File dir, String prefix) throws IOException {
		List<DiscretizedFunc[]> curvesList = new ArrayList<>();
		for (double period : periods) {
			File curvesFile = new File(dir, getCSV_FileName(prefix, period));
			if (!curvesFile.exists())
				curvesFile = new File(curvesFile.getAbsolutePath()+".gz");
			Preconditions.checkState(curvesFile.exists());
			
			CSVFile<String> csv = CSVFile.readFile(curvesFile, true);
			ArbitrarilyDiscretizedFunc xVals = new ArbitrarilyDiscretizedFunc();
			for (int col=3; col<csv.getNumCols(); col++)
				xVals.set(csv.getDouble(0, col), 0d);
			
			Preconditions.checkState(csv.getNumRows() == region.getNodeCount()+1);
			
			DiscretizedFunc[] curves = new DiscretizedFunc[region.getNodeCount()];
			
			for (int row=1; row<csv.getNumRows(); row++) {
				int index = row-1;
				Location loc = new Location(csv.getDouble(row, 1), csv.getDouble(row, 2));
				Preconditions.checkState(LocationUtils.areSimilar(loc, region.getLocation(index)));
				Preconditions.checkState(index == csv.getInt(row, 0));
				DiscretizedFunc curve = new ArbitrarilyDiscretizedFunc();
				for (int i=0; i<xVals.size(); i++)
					curve.set(xVals.getX(i), csv.getDouble(row, i+3));
				curves[index] = curve;
			}
			
			curvesList.add(curves);
		}
		
		SolHazardMapCalc calc = new SolHazardMapCalc(sol, null, region, periods);
		calc.curvesList = curvesList;
		return calc;
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
		
		Option gridSpacingOption = new Option("gs", "grid-spacing", true, "Grid spacing in degrees");
		gridSpacingOption.setRequired(false);
		ops.addOption(gridSpacingOption);
		
		Option recalcOption = new Option("rc", "recalc", false, "Flag to force recalculation (ignore existing curves files)");
		recalcOption.setRequired(false);
		ops.addOption(recalcOption);
		
		Option periodsOption = new Option("p", "periods", true, "Calculation period(s). Mutliple can be comma separated");
		periodsOption.setRequired(false);
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
		
		double gridSpacing = Double.parseDouble(cmd.getOptionValue("grid-spacing"));
		
		GriddedRegion gridReg = new GriddedRegion(region, gridSpacing, GriddedRegion.ANCHOR_0_0);
		
		AttenRelRef gmpe = AttenRelRef.ASK_2014;
		if (cmd.hasOption("gmpe"))
			gmpe = AttenRelRef.valueOf(cmd.getOptionValue("gmp"));
		
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
		if (calc == null) {
			// need to calculate
			calc = new SolHazardMapCalc(sol, gmpe, gridReg, periods);
			
			if (cmd.hasOption("max-distance"))
				calc.setMaxSourceSiteDist(Double.parseDouble(cmd.getOptionValue("max-distance")));
			
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
				
				if (cmd.hasOption("max-distance"))
					compCalc.setMaxSourceSiteDist(Double.parseDouble(cmd.getOptionValue("max-distance")));
				
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
