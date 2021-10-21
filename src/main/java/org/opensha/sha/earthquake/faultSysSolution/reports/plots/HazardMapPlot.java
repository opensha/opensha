package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.Range;
import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.GeoDataSetMath;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZGraphPanel;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.ReturnPeriodUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.calc.HazardCurveCalculator;
import org.opensha.sha.earthquake.AbstractERF;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractSolutionPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.param.FaultGridSpacingParam;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.param.IncludeBackgroundParam;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.earthquake.param.ProbabilityModelParam;
import org.opensha.sha.earthquake.rupForecastImpl.FaultRuptureSource;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.CustomCacheWrappedSurface;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy.CacheTypes;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.FaultSystemSolutionERF;

public class HazardMapPlot extends AbstractSolutionPlot {
	
	private AttenRelRef gmpeRef;
	private double spacing;
	private double[] periods;
	
	private Deque<ScalarIMR> gmpeDeque;
	
	private DiscretizedFunc xVals;
	private DiscretizedFunc logXVals;

	public HazardMapPlot() {
		this(AttenRelRef.ASK_2014, 0.5, 0d, 1d);
	}
	
	public HazardMapPlot(AttenRelRef gmpeRef, double spacing, double... periods) {
		this.gmpeRef = gmpeRef;
		this.spacing = spacing;
		this.periods = periods;
		
		this.gmpeDeque = new ArrayDeque<>();
		
		Preconditions.checkState(periods.length > 0);
		for (double period : periods)
			Preconditions.checkState(period >= 0d, "Period must be 0 (PGA) or >0 for SA");
		
		xVals = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : IMT_Info.getUSGS_SA_Function())
			xVals.set(pt);
		// this function is coarse, which is fast, but lacks super low points. add some:
		xVals.set(xVals.getMinX()*0.1, 1d);
		xVals.set(xVals.getMinX()*0.1, 1d);
		logXVals = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : xVals)
			logXVals.set(Math.log(pt.getX()), 1d);
	}

	@Override
	public String getName() {
		return "Hazard Maps";
	}
	
	private enum ReturnPeriods {
		TWO_IN_50(0.02, 50d, "2% in 50 year"),
		TEN_IN_50(0.1, 50d, "10% in 50 year");
		
		private final double refProb;
		private final double refDuration;
		private final String label;
		private final double oneYearProb;

		private ReturnPeriods(double refProb, double refDuration, String label) {
			this.refProb = refProb;
			this.refDuration = refDuration;
			this.label = label;
			this.oneYearProb = ReturnPeriodUtils.calcExceedanceProb(refProb, refDuration, 1d);
		}
	}

	@Override
	public List<String> plot(FaultSystemSolution sol, ReportMetadata meta, File resourcesDir, String relPathToResources,
			String topLink) throws IOException {
		GriddedRegion gridReg = new GriddedRegion(meta.region, spacing, GriddedRegion.ANCHOR_0_0);
		int numSites = gridReg.getNodeCount();
		System.out.println("Hazard gridded region with "+numSites+" sites, "+periods.length+" periods");
		
		int numThreads = Runtime.getRuntime().availableProcessors();
		if (numThreads > 32)
			numThreads = 32;
		
		List<DiscretizedFunc[]> curves = calcCurves(sol, gridReg, numThreads);
		
		List<DiscretizedFunc[]> compCurves = null;
		if (meta.hasComparisonSol()) {
			System.out.println("Calculating comparison hazard map...");
			
			compCurves = calcCurves(meta.comparison.sol, gridReg, numThreads);
		}
		
		System.out.println("Done calculating hazard maps!");
		
		List<String> lines = new ArrayList<>();
		
		lines.add("Hazard map comparisons with a resolution of "+optionalDigitDF.format(spacing)+" degrees ("
				+numSites+" sites). Hazard is computed with the "+gmpeRef.getShortName()+" GMPE, default site "
				+ "parameters, and supra-seismogenic fault sources only.");
		lines.add("");
		
		CPT logCPT = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-3d, 1d);
		CPT logRatioCPT = GMT_CPT_Files.GMT_POLAR.instance().rescale(-1d, 1d);
		logRatioCPT.setNanColor(Color.GRAY);
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		Color outlineColor = new Color(0, 0, 0, 180);
		Color faultColor = new Color(0, 0, 0, 100);
		
		DefaultXY_DataSet outline = new DefaultXY_DataSet();
		for (Location loc : gridReg.getBorder())
			outline.set(loc.getLongitude(), loc.getLatitude());
		outline.set(outline.get(0));
		
		funcs.add(outline);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, outlineColor));
		
		PlotCurveCharacterstics traceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, faultColor);
		
		for (FaultSection sect : sol.getRupSet().getFaultSectionDataList()) {
			DefaultXY_DataSet trace = new DefaultXY_DataSet();
			for (Location loc : sect.getFaultTrace())
				trace.set(loc.getLongitude(), loc.getLatitude());
			
			funcs.add(trace);
			chars.add(traceChar);
		}
		
		for (int p=0; p<periods.length; p++) {
			String perLabel, perUnits;
			if (periods[p] == -1d) {
				perLabel = "PGV";
				perUnits = "cm/s";
			} else if (periods[p] == 0d) {
				perLabel = "PGA";
				perUnits = "g";
			} else {
				Preconditions.checkState(periods[p] > 0);
				perLabel = optionalDigitDF.format(periods[p])+"s SA";
				perUnits = "g";
			}
			String perPrefix = perLabel.toLowerCase().replaceAll(" ", "_");
			
			String subHeading = getSubHeading();
			if (periods.length > 1) {
				lines.add(subHeading+" "+perLabel+" Hazard Maps");
				lines.add(topLink); lines.add("");
				subHeading += "#";
			}
			
			for (ReturnPeriods rp : ReturnPeriods.values()) {
				lines.add(subHeading+" "+perLabel+", "+rp.label+" Hazard Maps");
				lines.add(topLink); lines.add("");
				
				String prefix = "hazard_map_"+perPrefix+"_"+rp.name().toLowerCase();
				
				GriddedGeoDataSet xyz = map(curves.get(p), gridReg, rp);
				GriddedGeoDataSet logXYZ = xyz.copy();
				logXYZ.log10();
				
				String zLabel = "Log10 "+perLabel+" ("+perUnits+"), "+rp.label;
				File map = plotMap(resourcesDir, prefix, logXYZ, logCPT, " ", zLabel, funcs, chars);
				
				if (compCurves == null) {
					lines.add("![Hazard Map]("+relPathToResources+"/"+map.getName()+")");
				} else {
					GriddedGeoDataSet compXYZ = map(compCurves.get(p), gridReg, rp);
					GriddedGeoDataSet compLogXYZ = compXYZ.copy();
					compLogXYZ.log10();
					
					File compMap = plotMap(resourcesDir, prefix+"_comp", compLogXYZ, logCPT, " ", zLabel, funcs, chars);
					
					TableBuilder table = MarkdownUtils.tableBuilder();
					table.addLine(MarkdownUtils.boldCentered("Primary"), MarkdownUtils.boldCentered("Comparison"));
					table.addLine("![Hazard Map]("+relPathToResources+"/"+map.getName()+")",
							"![Hazard Map]("+relPathToResources+"/"+compMap.getName()+")");
					table.addLine(MarkdownUtils.boldCentered("Log10 Ratio (Primary/Comparison)"),
							MarkdownUtils.boldCentered("Scatter"));
					
					GriddedGeoDataSet ratioXYZ = (GriddedGeoDataSet)GeoDataSetMath.divide(xyz, compXYZ);
					ratioXYZ.log10();
					for (int i=0; i<xyz.size(); i++) {
						if (!Double.isFinite(ratioXYZ.get(i))) {
							double z1 = xyz.get(i);
							double z2 = xyz.get(i);
							if ((float)z1 == 0f && (float)z2 == 0f)
								ratioXYZ.set(i, 0d);
							else if ((float)z1 == 0f && (float)z2 > 0f)
								ratioXYZ.set(i, Double.NEGATIVE_INFINITY);
							else if ((float)z2 == 0f && (float)z1 > 0f)
								ratioXYZ.set(i, Double.POSITIVE_INFINITY);
						}
					}
					
					File ratioMap = plotMap(resourcesDir, prefix+"_ratio", ratioXYZ, logRatioCPT, " ", zLabel+" Ratio", funcs, chars);
					table.initNewLine();
					table.addColumn("![Ratio Map]("+relPathToResources+"/"+ratioMap.getName()+")");
					
					// plot scatter
					File scatter = compScatterPlot(resourcesDir, prefix+"_scatter", xyz, compXYZ, zLabel);
					table.addColumn("![Scatter Plot]("+relPathToResources+"/"+scatter.getName()+")");
					table.finalizeLine();
					
					lines.addAll(table.build());
				}
				lines.add("");
			}
		}
		
		return lines;
	}
	
	private List<DiscretizedFunc[]> calcCurves(FaultSystemSolution sol, GriddedRegion gridReg, int numThreads) {
		int numSites = gridReg.getNodeCount();
		ConcurrentLinkedDeque<Integer> calcIndexes = new ConcurrentLinkedDeque<>();
		for (int i=0; i<numSites; i++)
			calcIndexes.add(i);
		
		List<DiscretizedFunc[]> curves = new ArrayList<>();
		for (int p=0; p<periods.length; p++)
			curves.add(new DiscretizedFunc[numSites]);
		
		FaultSystemSolutionERF erf = new FaultSystemSolutionERF(sol);
		erf.setParameter(IncludeBackgroundParam.NAME, IncludeBackgroundOption.EXCLUDE);
		erf.setParameter(ProbabilityModelParam.NAME, ProbabilityModelOptions.POISSON);
		erf.setParameter(FaultGridSpacingParam.NAME, 2d); // good enough for comparative purposes
		erf.getTimeSpan().setDuration(1d); // 1 yar
		erf.updateForecast();
		
		System.out.println("Calculating hazard maps with "+numThreads+" threads...");
		List<CalcThread> threads = new ArrayList<>();
		CalcTracker track = new CalcTracker(numSites);
		for (int i=0; i<numThreads; i++) {
			CalcThread thread = new CalcThread(gridReg, calcIndexes, curves, erf, track);
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
		
		return curves;
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
	
	private class CalcThread extends Thread {
		private GriddedRegion gridReg;
		private ConcurrentLinkedDeque<Integer> calcIndexes;
		private List<DiscretizedFunc[]> curves;
		private AbstractERF erf;
		private CalcTracker track;
		
		public CalcThread(GriddedRegion gridReg, ConcurrentLinkedDeque<Integer> calcIndexes,
				List<DiscretizedFunc[]> curves, FaultSystemSolutionERF erf, CalcTracker track) {
			this.gridReg = gridReg;
			this.calcIndexes = calcIndexes;
			this.curves = curves;
			this.track = track;
			this.erf = new DistCachedERF(erf);
		}

		@Override
		public void run() {
			ScalarIMR gmpe = gmpeRef.instance(null);
			gmpe.setParamDefaults();
			
			HazardCurveCalculator calc = new HazardCurveCalculator();
			while (true) {
				Integer index = calcIndexes.pollFirst();
				if (index == null)
					break;
				Location loc = gridReg.getLocation(index);
				Site site = new Site(loc);
				site.addParameterList(gmpe.getSiteParams());
				
				for (int p=0; p<periods.length; p++) {
					if (periods[p] == 0d) {
						gmpe.setIntensityMeasure(PGA_Param.NAME);
					} else {
						Preconditions.checkState(periods[p] > 0);
						gmpe.setIntensityMeasure(SA_Param.NAME);
						SA_Param.setPeriodInSA_Param(gmpe.getIntensityMeasure(), periods[p]);
					}
					DiscretizedFunc logCurve = logXVals.deepClone();
					calc.getHazardCurve(logCurve, site, gmpe, erf);
					DiscretizedFunc curve = xVals.deepClone();
					for (int i=0; i<curve.size(); i++)
						curve.set(i, logCurve.getY(i));
					this.curves.get(p)[index] = curve;
				}
				
				track.taskCompleted();
			}
		}
	}
	
	private static class DistCachedERF extends AbstractERF {
		
		private AbstractERF erf;
		
		private List<ProbEqkSource> sources = null;

		public DistCachedERF(AbstractERF erf) {
			this.erf = erf;
		}

		@Override
		public int getNumSources() {
			return erf.getNumSources();
		}
		
		private void initSources() {
			List<ProbEqkSource> sources = new ArrayList<>();
			Map<RuptureSurface, CustomCacheWrappedSurface> wrappedMap = new HashMap<>();
			for (ProbEqkSource source : erf) {
				if (source instanceof FaultRuptureSource) {
					RuptureSurface sourceSurf = getWrappedSurface(wrappedMap, source.getSourceSurface());
					
					List<ProbEqkRupture> rups = new ArrayList<>();
					for (ProbEqkRupture origRup : source) {
						RuptureSurface rupSurf = getWrappedSurface(wrappedMap, origRup.getRuptureSurface());
						rups.add(new ProbEqkRupture(origRup.getMag(), origRup.getAveRake(),
								origRup.getProbability(), rupSurf, origRup.getHypocenterLocation()));
					}
					sources.add(new CustomSource(source, sourceSurf, rups));
				} else {
					sources.add(source);
				}
			}
			this.sources = sources;
		}
		
		private static RuptureSurface getWrappedSurface(Map<RuptureSurface, CustomCacheWrappedSurface> wrappedMap, RuptureSurface origSurf) {
			RuptureSurface wrappedSurf;
			if (wrappedMap.containsKey(origSurf))
				// already encountered (duplicate surface)
				return wrappedMap.get(origSurf);
			// need to wrap it here
			if (origSurf instanceof CompoundSurface) {
				// wrap the individual ones first
				List<RuptureSurface> subSurfs = new ArrayList<>();
				for (RuptureSurface subSurf : ((CompoundSurface)origSurf).getSurfaceList()) {
					if (wrappedMap.containsKey(subSurf)) {
						subSurfs.add(wrappedMap.get(subSurf));
					} else if (subSurf instanceof CacheEnabledSurface) {
						CustomCacheWrappedSurface cachedSubSurf =
								new CustomCacheWrappedSurface((CacheEnabledSurface)subSurf, CacheTypes.SINGLE);
						subSurfs.add(cachedSubSurf);
						wrappedMap.put(subSurf, cachedSubSurf);
					} else {
						subSurfs.add(subSurf);
					}
				}
				wrappedSurf = new CustomCacheWrappedSurface(new CompoundSurface(subSurfs), CacheTypes.SINGLE);
				wrappedMap.put(origSurf, (CustomCacheWrappedSurface)wrappedSurf);
			} else if (origSurf instanceof CacheEnabledSurface) {
				wrappedSurf = new CustomCacheWrappedSurface((CacheEnabledSurface)origSurf, CacheTypes.SINGLE);
				wrappedMap.put(origSurf, (CustomCacheWrappedSurface)wrappedSurf);
			} else {
				wrappedSurf = origSurf;
			}
			return wrappedSurf;
		}

		@Override
		public ProbEqkSource getSource(int idx) {
			if (sources == null) {
				synchronized (this) {
					if (sources == null)
						initSources();
				}
			}
			return sources.get(idx);
		}

		@Override
		public void updateForecast() {
			this.sources = null;
			erf.updateForecast();
		}

		@Override
		public String getName() {
			return erf.getName();
		}
		
	}
	
	private static class CustomSource extends ProbEqkSource {
		
		private ProbEqkSource origSource;
		private RuptureSurface sourceSurf;
		private List<ProbEqkRupture> rups;

		public CustomSource(ProbEqkSource origSource, RuptureSurface sourceSurf, List<ProbEqkRupture> rups) {
			this.origSource = origSource;
			this.sourceSurf = sourceSurf;
			this.rups = rups;
		}

		@Override
		public LocationList getAllSourceLocs() {
			return origSource.getAllSourceLocs();
		}

		@Override
		public RuptureSurface getSourceSurface() {
			return sourceSurf;
		}

		@Override
		public double getMinDistance(Site site) {
			return sourceSurf.getQuickDistance(site.getLocation());
		}

		@Override
		public int getNumRuptures() {
			return rups.size();
		}

		@Override
		public ProbEqkRupture getRupture(int nRupture) {
			return rups.get(nRupture);
		}
		
	}
	
	private static GriddedGeoDataSet map(DiscretizedFunc[] curves, GriddedRegion reg, ReturnPeriods rp) {
		Preconditions.checkState(curves.length == reg.getNodeCount());
		GriddedGeoDataSet xyz = new GriddedGeoDataSet(reg, false);
		for (int i=0; i<curves.length; i++) {
			DiscretizedFunc curve = curves[i];
			double z;
			if (rp.oneYearProb > curve.getMaxY())
				z = 0d;
			else if (rp.oneYearProb < curve.getMinY())
				z = curve.getMaxX();
			else
				z = curve.getFirstInterpolatedX_inLogXLogYDomain(rp.oneYearProb);
			xyz.set(i, z);
		}
		return xyz;
	}
	
	private static File plotMap(File outputDir, String prefix, GriddedGeoDataSet xyz, CPT cpt,
			String title, String zLabel, List<XY_DataSet> extraFuncs, List<PlotCurveCharacterstics> extraChars)
					throws IOException {
		XYZGraphPanel gp = new XYZGraphPanel(PlotUtils.getDefaultFigurePrefs());
		
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
		gp.drawPlot(spec, false, false, lonRange, latRange);
		
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
	
	private static double withinRange(Range range, double val) {
		if (val < range.getLowerBound())
			return range.getLowerBound();
		if (val > range.getUpperBound())
			return range.getUpperBound();
		return val;
	}
	
	private static File compScatterPlot(File outputDir, String prefix, GriddedGeoDataSet xyz1, GriddedGeoDataSet xyz2,
			String label) throws IOException {
		XY_DataSet scatter = new DefaultXY_DataSet();
		
		Range range = new Range(1e-4, 1e1);
		
		for (int i=0; i<xyz1.size(); i++)
			scatter.set(withinRange(range, xyz1.get(i)), withinRange(range, xyz2.get(i)));
		
		List<XY_DataSet> funcs = new ArrayList<>();
		funcs.add(scatter);
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		chars.add(new PlotCurveCharacterstics(PlotSymbol.CROSS, 3f, Color.BLACK));
		
		DefaultXY_DataSet line = new DefaultXY_DataSet();
		line.set(range.getLowerBound(), range.getLowerBound());
		line.set(range.getUpperBound(), range.getUpperBound());
		funcs.add(line);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.GRAY));
		
		PlotSpec spec = new PlotSpec(funcs, chars, " ", "Primary "+label, "Comparison "+label);
		
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		gp.drawGraphPanel(spec, true, true, range, range);
		
		PlotUtils.writePlots(outputDir, prefix, gp, 800, -1, true, false, false);
		return new File(outputDir, prefix+".png");
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return null;
	}
	
	public static void main(String[] args) throws IOException {
		File solFile = new File("/home/kevin/OpenSHA/UCERF4/batch_inversions/"
				+ "2021_10_18-reproduce-ucerf3-ref_branch-uniform-u3Iters/mean_solution.zip");
		File compSolFile = new File("/home/kevin/OpenSHA/UCERF3/rup_sets/modular/"
				+ "FM3_1_ZENGBB_Shaw09Mod_DsrUni_CharConst_M5Rate7.9_MMaxOff7.6_NoFix_SpatSeisU3.zip");
		
		File outputDir = new File("/tmp/report");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		FaultSystemSolution sol = FaultSystemSolution.load(solFile);
		FaultSystemSolution compSol = FaultSystemSolution.load(compSolFile);
		
		ReportMetadata meta = new ReportMetadata(new RupSetMetadata("Primary", sol), new RupSetMetadata("Comparison", compSol));
		
		ReportPageGen gen = new ReportPageGen(meta, outputDir, List.of(new HazardMapPlot()));
		gen.setReplot(true);
		
		gen.generatePage();
	}

}
