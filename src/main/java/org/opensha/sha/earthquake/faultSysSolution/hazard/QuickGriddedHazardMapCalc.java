package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.opensha.commons.data.Site;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.LightFixedXFunc;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.modules.ModuleContainer;
import org.opensha.sha.calc.params.filters.SourceFilterManager;
import org.opensha.sha.calc.params.filters.SourceFilters;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.SiteAdaptiveSource;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysHazardCalcSettings;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader;
import org.opensha.sha.earthquake.rupForecastImpl.prvi25.util.PRVI25_RegionLoader;
import org.opensha.sha.earthquake.util.GridCellSupersamplingSettings;
import org.opensha.sha.earthquake.util.GriddedSeismicitySettings;
import org.opensha.sha.faultSurface.FiniteApproxPointSurface;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrection;
import org.opensha.sha.faultSurface.utils.PointSourceDistanceCorrections;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Doubles;

/**
 * This class can quickly compute hazard maps for gridded seismicity point sources. It does this by first identifying
 * unique gridded seismicity rupture properties (see {@link UniquePointRupture}). Then, for each unique gridded seismicity
 * rupture, we compute conditional exceedance probabilities as a function of site-source distance, and interpolate
 * those for each actual site-source distance.
 * 
 * The calculation happens in reverse of regular hazard calculations: we get the distance-dependent exceedance
 * probabilities for each rupture (computing if necessary, but caching for reuse), and then add the contribution to
 * the curves for each site within the cutoff distance of that rupture.
 * 
 * The expensive part of the calculation here is the interpolation for individual distances. If the number of sites
 * affected by a source is less than 1.5x the number of interpolation distances, we take a shortcut and compute full
 * source exceedance probabilities (not conditional, including all ruptures in that source with their rates), and
 * interpolate those onto each source. This allows for the calculation to scale very efficiently to high resolutions.
 * 
 * Note: site-specific parameters are not supported by this approach, and thus there is no mechanism to supply a Site
 * list (just a gridded region). 
 * 
 * @author kevin
 *
 */
public class QuickGriddedHazardMapCalc {
	
	private Map<TectonicRegionType, ? extends Supplier<ScalarIMR>> gmpeSuppliers;
	private double period;
	private DiscretizedFunc xVals;
	private Map<TectonicRegionType, Double> trtMaxDists;
	
	private Map<TectonicRegionType, EvenlyDiscretizedFunc> trtLogSpacedDiscrs;
	private Map<TectonicRegionType, double[]> trtDistVals;
	private Map<TectonicRegionType, DiscretizedFunc> trtDistDiscr;
	
	private ConcurrentMap<UniquePointRupture, DiscretizedFunc[]> rupExceedsMap = new ConcurrentHashMap<>();
	
	private GriddedSeismicitySettings gridSettings;
	
	private int minNodeCalcsForSourcewise;
	
	// determined through trial and error to get typical 2% in 50 year probabilities within 0.1%
	public static final int NUM_DISCR_DEFAULT = 150;
	private static double FIRST_NONZERO_DIST_BIN = 0.1;
	private static boolean INCLUDE_RUPS_IN_NODE_CALCS = true;
	private static boolean LIMIT_SOURCE_CALC_TO_ACTUAL_MAX_DIST = true;

	/**
	 * This constructor uses the default number of interpolation points (see {@link #NUM_DISCR_DEFAULT})
	 * 
	 * @param gmpeSupplier GMM suplier map, such as an {@link AttenRelRef}
	 * @param period calculation period
	 * @param xVals x values
	 * @param sourceFitlers source filters (usually distance-dependent)
	 * @param gridSettings point source settings
	 */
	public QuickGriddedHazardMapCalc(Map<TectonicRegionType, ? extends Supplier<ScalarIMR>> gmpeSuppliers,
			double period, DiscretizedFunc xVals, SourceFilterManager sourceFitlers,
			GriddedSeismicitySettings gridSettings) {
		this(gmpeSuppliers, period, xVals, sourceFitlers, gridSettings, NUM_DISCR_DEFAULT);
	}

	/**
	 * 
	 * @param gmpeSupplier GMM suplier, such as an {@link AttenRelRef}
	 * @param period calculation period
	 * @param xVals x values
	 * @param sourceFitlers source filters (usually distance-dependent)
	 * @param gridSettings point source settings
	 * @param numDiscr number of interpolation points
	 */
	public QuickGriddedHazardMapCalc(Map<TectonicRegionType, ? extends Supplier<ScalarIMR>> gmpeSuppliers,
			double period, DiscretizedFunc xVals, SourceFilterManager sourceFitlers,
			GriddedSeismicitySettings gridSettings, int numDiscr) {
		this.gmpeSuppliers = gmpeSuppliers;
		this.period = period;
		this.xVals = xVals;
		this.gridSettings = gridSettings;
		
		minNodeCalcsForSourcewise = 3*numDiscr/2;
		trtMaxDists = new EnumMap<>(TectonicRegionType.class);
		trtLogSpacedDiscrs = new EnumMap<>(TectonicRegionType.class);
		trtDistVals = new EnumMap<>(TectonicRegionType.class);
		trtDistDiscr = new EnumMap<>(TectonicRegionType.class);
		for (TectonicRegionType trt : gmpeSuppliers.keySet()) {
			double maxDist = SolHazardMapCalc.getMaxDistForTRT(sourceFitlers, trt);
			trtMaxDists.put(trt, maxDist);
			EvenlyDiscretizedFunc logSpacedDiscr = new EvenlyDiscretizedFunc(
					Math.log(FIRST_NONZERO_DIST_BIN), Math.log(maxDist+5d), numDiscr-1);
			trtLogSpacedDiscrs.put(trt, logSpacedDiscr);
			double[] distVals = new double[numDiscr];
			for (int i=1; i<distVals.length; i++)
				distVals[i] = Math.exp(logSpacedDiscr.getX(i-1));
			trtDistVals.put(trt, distVals);
			trtDistDiscr.put(trt, new LightFixedXFunc(distVals, new double[distVals.length]));
		}
	}
	
	/**
	 * These properties define a unique point source rupture, for which conditional exceedance probabilities will be
	 * calculated and cached separately. Any new fields must be added to the hashCode and equals methods
	 * 
	 * @author kevin
	 *
	 */
	static class UniquePointRupture {
		private final double rake;
		private final double mag;
		private final double zTOR;
		private final double width;
		private final double dip;
		private final boolean footwall;
		private final double zHyp;
		private final PointSourceDistanceCorrection distCorr;
		private final int hash;
		public UniquePointRupture(EqkRupture rup) {
			this.rake = rup.getAveRake();
			this.mag = rup.getMag();
			RuptureSurface surf = rup.getRuptureSurface();
			this.zTOR = surf.getAveRupTopDepth();
			this.width = surf.getAveWidth();
			this.dip = surf.getAveDip();
			PointSurface ptSurf = (PointSurface)surf;
			Location ptLoc = ptSurf.getLocation();
			if (surf instanceof FiniteApproxPointSurface) {
				footwall = ((FiniteApproxPointSurface)surf).isOnFootwall();
			} else {
				Location loc = LocationUtils.location(ptLoc, 0d, 50d);
				footwall = surf.getDistanceX(loc) < 0d;
			}
			distCorr = ptSurf.getDistanceCorrection();
			Location hypo = rup.getHypocenterLocation();
			zHyp = hypo == null ? ptLoc.getDepth() : hypo.getDepth();
			hash = Objects.hash(dip, footwall, mag, rake, width, zTOR, distCorr, zHyp);
		}
		@Override
		public int hashCode() {
			return hash;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			UniquePointRupture other = (UniquePointRupture) obj;
			return hash == other.hash && footwall == other.footwall && distCorr == other.distCorr
					&& Double.doubleToLongBits(zHyp) == Double.doubleToLongBits(other.zHyp)
					&& Double.doubleToLongBits(dip) == Double.doubleToLongBits(other.dip)
					&& Double.doubleToLongBits(mag) == Double.doubleToLongBits(other.mag)
					&& Double.doubleToLongBits(rake) == Double.doubleToLongBits(other.rake)
					&& Double.doubleToLongBits(width) == Double.doubleToLongBits(other.width)
					&& Double.doubleToLongBits(zTOR) == Double.doubleToLongBits(other.zTOR);
		}
	}
	
	/**
	 * This calculates a gridded seismicity hazard map for the given grid source provider, using interpolated
	 * conditional exceedance probability distributions
	 * 
	 * @param gridProv grid source provider for which to calculate
	 * @param gridReg gridded region, the returned curve array will have one curve for each location
	 * @param threads number of calculation threads, must be >=1
	 * @return hazard curves (linear x-values) for this grid source provider, computed at every location
	 */
	public DiscretizedFunc[] calc(GridSourceProvider gridProv, GriddedRegion gridReg, ExecutorService exec, int threads) {
		// calculation done in log-x space
		DiscretizedFunc logXVals = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : xVals)
			logXVals.set(Math.log(pt.getX()), 0d);
		
		// this will be used to distribute sources to worker threads
		ArrayDeque<Integer> sourceIndexes = new ArrayDeque<>(gridProv.getNumSources());
		for (int i=0; i<gridProv.getNumSources(); i++)
			sourceIndexes.add(i);
		
		// spin up worker threads
		List<Future<DiscretizedFunc[]>> calcFutures = new ArrayList<>(threads);
		
		for (int i=0; i<threads; i++)
			calcFutures.add(exec.submit(new CalcCallable(gridProv, logXVals, gridReg, sourceIndexes)));
		
		// join them
		DiscretizedFunc[] curves = null;
		for (Future<DiscretizedFunc[]> future : calcFutures) {
			DiscretizedFunc[] threadCurves;
			try {
				threadCurves = future.get();
			} catch (InterruptedException | ExecutionException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			if (threadCurves == null)
				continue;
			
			if (curves == null) {
				// use curves from this calc thread
				curves = threadCurves;
			} else {
				// add them (actually multiply, these are 1-P curves)
				for (int i=0; i<curves.length; i++)
					for (int k=0; k<curves[i].size(); k++)
						curves[i].set(k, curves[i].getY(k)*threadCurves[i].getY(k));
			}
		}
		
		Preconditions.checkNotNull(curves, "Curves never initialized?");
		
		// these are nonexceedance curves, with log x-values
		// convert them to exceedance (1 - curve val) and convert to linear x
		double[] linearX = new double[xVals.size()];
		for (int i=0; i<linearX.length; i++)
			linearX[i] = xVals.getX(i);
		for (int i=0; i<curves.length; i++) {
			double[] yVals = new double[linearX.length];
			for (int j=0; j<logXVals.size(); j++)
				yVals[j] = 1d - curves[i].getY(j); 
			curves[i] = new LightFixedXFunc(linearX, yVals);
		}
		
		return curves;
	}
	
	private boolean trtWarned = false;
	
	private class CalcCallable implements Callable<DiscretizedFunc[]> {
		
		private DiscretizedFunc[] curves;
		private GridSourceProvider gridProv;
		private DiscretizedFunc logXVals;
		private GriddedRegion gridReg;
		private ArrayDeque<Integer> sourceIndexes;
		
		public CalcCallable(GridSourceProvider gridProv, DiscretizedFunc logXVals,
				GriddedRegion gridReg, ArrayDeque<Integer> sourceIndexes) {
			this.gridProv = gridProv;
			this.logXVals = logXVals;
			this.gridReg = gridReg;
			this.sourceIndexes = sourceIndexes;
		}

		@Override
		public DiscretizedFunc[] call() {
			try {
				Map<TectonicRegionType, ScalarIMR> gmpeMap = new EnumMap<>(TectonicRegionType.class);
				
				for (TectonicRegionType trt : gridProv.getTectonicRegionTypes()) {
					Supplier<ScalarIMR> gmmSupplier = gmpeSuppliers.get(trt);
					if (gmmSupplier == null && gmpeSuppliers.size() == 1) {
						TectonicRegionType trtWeHave = gmpeSuppliers.keySet().iterator().next();
						if (!trtWarned) {
							synchronized (QuickGriddedHazardMapCalc.this) {
								if (!trtWarned) {
									System.err.println("WARNING: no GMPE supplied for TRT "+trt+", using the only one we have (for "+trtWeHave+")");
									trtWarned = true;
								}
							}
						}
						gmmSupplier = gmpeSuppliers.get(trtWeHave);
					}
					Preconditions.checkNotNull(gmmSupplier, "No GMPE supplied for TRT: %s", trt);
					ScalarIMR gmm = gmmSupplier.get();
					FaultSysHazardCalcSettings.setIMforPeriod(gmm, period);
					Site testSite = new Site(new Location(0d, 0d));
					testSite.addParameterList(gmm.getSiteParams());
					gmm.setSite(testSite);
					gmpeMap.put(trt, gmm);
				}
				
				double[] xValArray = new double[logXVals.size()];
				for (int i=0; i<xValArray.length; i++)
					xValArray[i] = logXVals.getX(i);
				
				// initialize curves, setting all y values to 1
				curves = new DiscretizedFunc[gridReg.getNodeCount()];
				for (int i=0; i<curves.length; i++) {
					double[] yVals = new double[xValArray.length];
					for (int j=0; j<yVals.length; j++)
						yVals[j] = 1d;
					curves[i] = new LightFixedXFunc(xValArray, yVals);
				}
				
				while (true) {
					int sourceID;
					synchronized (sourceIndexes) {
						if (sourceIndexes.isEmpty())
							break;
						sourceID = sourceIndexes.pop();
					}
					ProbEqkSource source = gridProv.getSource(sourceID, 1d, null, gridSettings);
					
					TectonicRegionType trt = source.getTectonicRegionType();
					ScalarIMR gmpe = gmpeMap.get(trt);
					
					quickSourceCalc(gridReg, source, gmpe, curves);
				}
			} catch (Throwable t) {
//				t.printStackTrace();
				throw ExceptionUtils.asRuntimeException(t);
			}
			return curves;
		}
		
	}
	
	private void quickSourceCalc(GriddedRegion gridReg, ProbEqkSource origSource, ScalarIMR gmpe, DiscretizedFunc[] curves) {
		double[] xValsArray = new double[curves[0].size()];
		for (int i=0; i<xValsArray.length; i++)
			xValsArray[i] = curves[0].getX(i);
		LightFixedXFunc exceedFunc = new LightFixedXFunc(xValsArray, new double[xValsArray.length]);
		
		TectonicRegionType trt = origSource.getTectonicRegionType();
		if (!trtMaxDists.containsKey(trt)) {
			Preconditions.checkState(trtMaxDists.keySet().size() == 1);
			trt = trtMaxDists.keySet().iterator().next();
		}
		double maxDist = trtMaxDists.get(trt);
		double[] distVals = trtDistVals.get(trt);
		EvenlyDiscretizedFunc logSpacedDiscr = trtLogSpacedDiscrs.get(trt);
		DiscretizedFunc distDiscr = trtDistDiscr.get(trt);
		
//		System.out.println("Calc for trt="+trt.name()+", gmm="+gmpe.getShortName()+", maxDist="+maxDist
//				+", imt="+gmpe.getIntensityMeasure().getName()+", sourceType="+origSource.getClass().getName());
		
		// these will keep track of sources for individual adaptive instances, if applicable
		List<List<Integer>> nodeIndexesList = new ArrayList<>();
		List<List<Double>> nodeDistsList = new ArrayList<>();
		List<Double> nodeMaxDistsList = new ArrayList<>();
		List<ProbEqkSource> sourceInstances = new ArrayList<>();
		Map<ProbEqkSource, Integer> sourceInstancesMap = new HashMap<>();
		SiteAdaptiveSource adaptive = origSource instanceof SiteAdaptiveSource ? (SiteAdaptiveSource)origSource : null;
		// always put the original source in the list
		nodeIndexesList.add(new ArrayList<>());
		nodeDistsList.add(new ArrayList<>());
		nodeMaxDistsList.add(0d);
		sourceInstances.add(origSource);
		sourceInstancesMap.put(origSource, 0);
		
		// figure out locations to include
		Site site = new Site(gridReg.getLocation(0));
		for (int i=0; i<gridReg.getNodeCount(); i++) {
			Location loc = gridReg.getLocation(i);
			site.setLocation(loc);
			double dist = origSource.getMinDistance(site);
			if (dist <= maxDist) {
				Integer sourceIndex;
				if (adaptive != null) {
					// site adaptive
					ProbEqkSource mySource = adaptive.getForSite(site);
					sourceIndex = sourceInstancesMap.get(mySource);
					if (sourceIndex == null) {
						// source instance we haven't seen yet
						sourceIndex = sourceInstances.size();
						sourceInstances.add(mySource);
						nodeIndexesList.add(new ArrayList<>());
						nodeDistsList.add(new ArrayList<>());
						nodeMaxDistsList.add(0d);
						sourceInstancesMap.put(mySource, sourceIndex);
					}
				} else {
					sourceIndex = 0;
				}
				List<Integer> nodeIndexes = nodeIndexesList.get(sourceIndex);
				List<Double> nodeDists = nodeDistsList.get(sourceIndex);
				nodeIndexes.add(i);
				nodeDists.add(dist);
				double prevMax = nodeMaxDistsList.get(sourceIndex);
				if (dist > prevMax)
					nodeMaxDistsList.set(sourceIndex, dist);
			}
		}
		
		if (nodeIndexesList.size() == 1 && nodeIndexesList.get(0).isEmpty()) {
			// can skip
			return;
		}
		
		for (int n=0; n<nodeIndexesList.size(); n++) {
			ProbEqkSource theSource = sourceInstances.get(n);
			List<Integer> nodeIndexes = nodeIndexesList.get(n);
			List<Double> nodeDists = nodeDistsList.get(n);
			
//			System.out.println("Calculating for source subIndex="+n+"/"+sourceInstances.size()+" with "+theSource.getNumRuptures()
//					+" rups for "+nodeIndexes.size()+" sites");
			
			if (nodeIndexes.isEmpty())
				continue;
			
			int numNodes = nodeIndexes.size();
			
			/*
			 * the expensive part of this calculation is the interpolation step. if we have few sites, it's faster to do it
			 * for each site and rupture individually (but still caching rupture exceedances). if we have lots of sites
			 * (or lots of ruptures), it's faster to calculate distance-dependent source exceedance probabilities (not conditional, taking the rupture probs
			 * into account)
			 * 
			 * but if it's not actually a point source, we need to do it the long way anyway
			 */
			int nodeCalcs;
			if (INCLUDE_RUPS_IN_NODE_CALCS)
				nodeCalcs = numNodes * theSource.getNumRuptures();
			else
				nodeCalcs = numNodes;
			
			boolean truePointSource = true;
			boolean allSameLoc = true;
			Location loc0 = null;
			List<ProbEqkRupture> rups = theSource.getRuptureList();
			for (ProbEqkRupture rup : rups) {
				RuptureSurface surf = rup.getRuptureSurface();
				if (!(surf instanceof PointSurface)) {
					truePointSource = false;
					break;
				} else if (allSameLoc) {
					Location loc = ((PointSurface)surf).getLocation();
					if (loc0 == null)
						loc0 = loc;
					else
						allSameLoc = (float)loc.lat == (float)loc0.lat && (float)loc.lon == (float)loc0.lon;
				}
			}
//			System.out.println("allSameLoc ? "+allSameLoc+"\tadaptive? "+(origSource instanceof SiteAdaptiveSource)
//					+"\tsourceN="+n+", numRups="+rups.size());
			
			if (trackStats) sourcesProcessed.incrementAndGet();
			
			if (!truePointSource) {
				if (trackStats) numNonTruePointSources.incrementAndGet();
				// no shortcut for this one
				Preconditions.checkState(exceedFunc.size() == curves[0].size());
				Preconditions.checkState(exceedFunc.getX(0) == curves[0].getX(0));
				for (ProbEqkRupture rup : rups) {
					gmpe.setEqkRupture(rup);
					// we're going to do a bunch of (1-prob)^value
					// we can speed this up by replacing the power with this log equation:
					// a^b = exp(b*ln(a))
//					double lnBase = Math.log(1-rup.getProbability());
					double lnBase = Math.log1p(-rup.getProbability());
//					double invQkProb = 1d-rup.getProbability();
					
					for (int l=0; l<numNodes; l++) {
						int index = nodeIndexes.get(l);
						
						gmpe.setSiteLocation(gridReg.getLocation(index));
						gmpe.getExceedProbabilities(exceedFunc);
						
						for(int k=0; k<exceedFunc.size(); k++)
//							curves[index].set(k, curves[index].getY(k)*Math.pow(invQkProb, exceedFunc.getY(k)));
							curves[index].set(k, curves[index].getY(k)*Math.exp(lnBase*exceedFunc.getY(k)));
					}
				}
			} else if (!allSameLoc || nodeCalcs < minNodeCalcsForSourcewise || numNodes < theSource.getNumRuptures()) {
				if (trackStats) numRupwise.incrementAndGet();
				// do it individually for each rupture at each unique distance
				
				// here we do:
				// 		numInterp = numRups * numLocs
				// 		numCurveExceed = numRups * numLocs * numXVals
				double[] logDists = null;
				if (allSameLoc) {
					logDists = new double[numNodes];
					for (int l=0; l<logDists.length; l++)
						logDists[l] = Math.log(nodeDists.get(l));
				}
				
				for (ProbEqkRupture rup : rups) {
					DiscretizedFunc[] exceeds = getCacheRupExceeds(rup, gmpe, curves[0], distVals, distDiscr);
					
					Location rupLoc = allSameLoc ? null : ((PointSurface)rup.getRuptureSurface()).getLocation();
					
					// we're going to do a bunch of (1-prob)^value
					// we can speed this up by replacing the power with this log equation:
					// a^b = exp(b*ln(a))
//					double lnBase = Math.log(1-rup.getProbability());
					double lnBase = Math.log1p(-rup.getProbability());
//					double invQkProb = 1d-rup.getProbability();
					for (int l=0; l<numNodes; l++) {
						int index = nodeIndexes.get(l);
						double dist, logDist;
						if (allSameLoc) {
							dist = nodeDists.get(l);
							logDist = logDists[l];
						} else{
							dist = LocationUtils.horzDistanceFast(rupLoc, gridReg.getLocation(index));
							logDist = Math.log(dist);
						}
						
						quickInterp(exceeds, exceedFunc, dist, logDist, logSpacedDiscr, distDiscr);
						
						for(int k=0; k<exceedFunc.size(); k++)
//							curves[index].set(k, curves[index].getY(k)*Math.pow(invQkProb, exceedFunc.getY(k)));
							curves[index].set(k, curves[index].getY(k)*Math.exp(lnBase*exceedFunc.getY(k)));
					}
				}
			} else {
				if (trackStats) numSourcewise.incrementAndGet();
				// calculate a source non exceedance functions
//				System.out.println("Doing QuickInterp for source subIndex="+n+"/"+sourceInstances.size()+" with "
//						+theSource.getNumRuptures()+" rups for "+nodeIndexes.size()+" sites");
				
				// here we do:
				// 		numInterp = numLocs
				// 		numCurveExceed = numRups * numDists * numXVals
				
				int numDistsToCalc = distVals.length;
				double[] calcDistVals = distVals;
				if (LIMIT_SOURCE_CALC_TO_ACTUAL_MAX_DIST) {
					// figure out the maximum distance we actually need to calculate out to
					double maxSiteDist = nodeMaxDistsList.get(n);
					if (maxSiteDist < 0.9*maxDist) {
						int maxDistIndex = Arrays.binarySearch(distVals, maxSiteDist);
						if (maxDistIndex < 0)
							maxDistIndex = -(maxDistIndex + 1);
						maxDistIndex++;
						if (maxDistIndex < distVals.length - 1) {
							// we can skip some
							numDistsToCalc = maxDistIndex+1;
							calcDistVals = Arrays.copyOf(distVals, numDistsToCalc);
						}
					}
				}
				
				DiscretizedFunc[] sourceNonExceeds = new DiscretizedFunc[xValsArray.length];
				for (int k=0; k<sourceNonExceeds.length; k++) {
					double[] yVals = new double[numDistsToCalc];
					for (int i=0; i<yVals.length; i++)
						yVals[i] = 1;
					sourceNonExceeds[k] = new LightFixedXFunc(calcDistVals, yVals);
				}
				
				for (ProbEqkRupture rup : rups) {
					DiscretizedFunc[] exceeds = getCacheRupExceeds(rup, gmpe, curves[0], distVals, distDiscr);
					
					// we're going to do a bunch of (1-prob)^value
					// we can speed this up by replacing the power with this log equation:
					// a^b = exp(b*ln(a))
					double lnBase = Math.log(1-rup.getProbability());
//					double invQkProb = 1d-rup.getProbability();
					for (int i=0; i<sourceNonExceeds.length; i++)
						for(int k=0; k<numDistsToCalc; k++)
//							sourceNonExceeds[i].set(k, sourceNonExceeds[i].getY(k)*Math.pow(invQkProb, exceeds[i].getY(k)));
							sourceNonExceeds[i].set(k, sourceNonExceeds[i].getY(k)*Math.exp(lnBase*exceeds[i].getY(k)));
				}
				
				// now interpolate those onto sites
				for (int l=0; l<numNodes; l++) {
					int index = nodeIndexes.get(l);
					double dist = nodeDists.get(l);
					
					quickInterp(sourceNonExceeds, exceedFunc, dist, Math.log(dist), logSpacedDiscr, distDiscr);
					
					for(int k=0; k<exceedFunc.size(); k++)
						curves[index].set(k, curves[index].getY(k)*exceedFunc.getY(k));
				}
			}
		}
	}
	
	/**
	 * Quick interpolation for an array of functions, each as a function of distance.
	 * @param exceeds array of functions, each as a function of distance
	 * @param dist distance in km
	 * @param exceedFunc function to be filled in
	 */
	private void quickInterp(DiscretizedFunc[] exceeds, DiscretizedFunc exceedFunc, double dist, double logDist,
			EvenlyDiscretizedFunc logSpacedDiscr, DiscretizedFunc distDiscr) {
		if ((float)dist == 0f) {
			// shortcut
			for (int i=0; i<exceedFunc.size(); i++)
				exceedFunc.set(i, exceeds[i].getY(0));
		} else {
			
//			int x1Ind = distDiscr.getXIndexBefore(dist);
//			int x1Ind = (int)Math.floor((dist-distDiscr.getMinX())/distDiscr.getDelta());
			int x1Ind;
			if (dist < distDiscr.getX(1)) {
				x1Ind = 0;
			} else {
				x1Ind = 1 + (int)Math.floor((logDist-logSpacedDiscr.getMinX())/logSpacedDiscr.getDelta());
			}
			int x2Ind = x1Ind+1;
			
			// seems to do best with logX = true, and logY = false
			final boolean logX = x1Ind > 0;
//			final boolean logX = false;
//			final boolean logY = true;
			final boolean logY = false;
			
			double x, x1, x2;
			if (logX) {
				// the log function is missing the first (0) bin
				x1 = logSpacedDiscr.getX(x1Ind-1);
				x2 = logSpacedDiscr.getX(x2Ind-1);
				x  = logDist;
			} else {
				x1 = distDiscr.getX(x1Ind);
				x2 = distDiscr.getX(x2Ind);
				x  = dist;
			}
//			Preconditions.checkState(x >= x1 && x <= x2);
			
			// y1 + (x - x1) * (y2 - y1) / (x2 - x1);
			double xRatio = (x-x1)/(x2-x1);
			
			for (int i=0; i<exceedFunc.size(); i++) {
				double y1 = exceeds[i].getY(x1Ind);
				double y2 = exceeds[i].getY(x2Ind);
				
				boolean myLogY = logY && y1 > 0d && y2 > 0d;
				
				double y;
				if(y1==0 && y2==0) {
					y = 0d;
				} else {
					if (myLogY) {
						y1 = Math.log(y1);
						y2 = Math.log(y2);
					}
					y = y1 + xRatio*(y2-y1);
					if (myLogY)
						y = Math.exp(y);
				}
				exceedFunc.set(i, y);
			}
		}
	}

	private boolean trackStats = false;
	private AtomicInteger sourcesProcessed = new AtomicInteger();
	private AtomicInteger numNonTruePointSources = new AtomicInteger();
	private AtomicInteger numRupwise = new AtomicInteger();
	private AtomicInteger numSourcewise = new AtomicInteger();
	
	private AtomicLong numCacheMisses = new AtomicLong();
	private AtomicLong numCacheHits = new AtomicLong();
	
	private DiscretizedFunc[] getCacheRupExceeds(ProbEqkRupture rup, ScalarIMR gmpe, DiscretizedFunc xVals,
			double[] distVals, DiscretizedFunc distDiscr) {
		UniquePointRupture uRup = new UniquePointRupture(rup);
		DiscretizedFunc[] exceeds = rupExceedsMap.get(uRup);
		if (exceeds == null) {
			// calculate it
			exceeds = new DiscretizedFunc[xVals.size()];
			for (int i=0; i<exceeds.length; i++)
				exceeds[i] = new LightFixedXFunc(distVals, new double[distVals.length]);
			
			PointSurface pSurf = (PointSurface)rup.getRuptureSurface();
			Location srcLoc = pSurf.getLocation();
			gmpe.setEqkRupture(rup);
			
			double[] xValsArray = new double[xVals.size()];
			for (int i=0; i<xValsArray.length; i++)
				xValsArray[i] = xVals.getX(i);
			LightFixedXFunc exceedFunc = new LightFixedXFunc(xValsArray, new double[xValsArray.length]);
			for (int i=0; i<distDiscr.size(); i++) {
				double dist = distDiscr.getX(i);
				Location siteLoc = (float)dist == 0f ? srcLoc : LocationUtils.location(srcLoc, 0d, dist);
				gmpe.setSiteLocation(siteLoc);
				gmpe.getExceedProbabilities(exceedFunc);
				
				for (int j=0; j<exceedFunc.size(); j++)
					exceeds[j].set(i, exceedFunc.getY(j));
			}
			
			rupExceedsMap.putIfAbsent(uRup, exceeds);
			if (trackStats) numCacheMisses.incrementAndGet();
		} else if (trackStats) {
			numCacheHits.incrementAndGet();
		}
		return exceeds;
	}

	public static void main(String[] args) throws IOException {
		ModuleContainer.VERBOSE_DEFAULT = false;
		
//		FaultSystemSolution sol = FaultSystemSolution.load(new File("/data/kevin/nshm23/batch_inversions/"
//				+ "2023_03_01-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR/"
//				+ "results_NSHM23_v2_CoulombRupSet_branch_averaged_gridded.zip"));
//		AttenRelRef gmpeRef = AttenRelRef.ASK_2014;
//		Map<TectonicRegionType, Supplier<ScalarIMR>> trtGMMs = SolHazardMapCalc.wrapInTRTMap(gmpeRef);
//		Region region = NSHM23_RegionLoader.loadFullConterminousWUS();
//		double spacing = 0.5d;
		
		FaultSystemSolution sol = FaultSystemSolution.load(new File("/data/kevin/nshm23/batch_inversions/"
				+ "2024_12_12-prvi25_crustal_subduction_combined_branches/combined_branch_averaged_solution.zip"));
		Map<TectonicRegionType, Supplier<ScalarIMR>> trtGMMs = Map.of(
				TectonicRegionType.ACTIVE_SHALLOW, AttenRelRef.USGS_PRVI_ACTIVE,
				TectonicRegionType.SUBDUCTION_INTERFACE, AttenRelRef.USGS_PRVI_INTERFACE,
				TectonicRegionType.SUBDUCTION_SLAB, AttenRelRef.USGS_PRVI_SLAB);
		Region region = PRVI25_RegionLoader.loadPRVI_Tight();
//		double spacing = 0.01d;
		double spacing = 0.025d;
//		double spacing = 0.05d;
//		double spacing = 0.1d;
//		double spacing = 0.2d;
//		double spacing = 0.5d;
		
		INCLUDE_RUPS_IN_NODE_CALCS = true;
		LIMIT_SOURCE_CALC_TO_ACTUAL_MAX_DIST = true;
		int numDiscr = NUM_DISCR_DEFAULT;
		FIRST_NONZERO_DIST_BIN = 0.1;
//		numDiscr = 550;
		
		GridSourceProvider gridProv = sol.getGridSourceProvider();
		
		DiscretizedFunc xVals = new IMT_Info().getDefaultHazardCurve(PGA_Param.NAME);
		int threads = 32;
		double period = 0d;
		
		SourceFilterManager sourceFilters = new SourceFilterManager(SourceFilters.TRT_DIST_CUTOFFS);
		
		GriddedSeismicitySettings gridSettings = GriddedSeismicitySettings.DEFAULT;
//		gridSettings = gridSettings.forSupersamplingSettings(GridCellSupersamplingSettings.DEFAULT);
		gridSettings = gridSettings.forSupersamplingSettings(GridCellSupersamplingSettings.QUICK);
		
		QuickGriddedHazardMapCalc calc = new QuickGriddedHazardMapCalc(trtGMMs,
				period, xVals, sourceFilters, gridSettings, numDiscr);
		System.out.println("Cache distances:");
		for (TectonicRegionType trt : calc.trtDistVals.keySet())
			System.out.println(trt.name()+":\t"+Joiner.on(",").join(Doubles.asList(calc.trtDistVals.get(trt))));
//		calc.minNodesForSourcewise = Integer.MAX_VALUE;
		calc.trackStats = true;
		
		GriddedRegion gridReg = new GriddedRegion(region, spacing, GriddedRegion.ANCHOR_0_0);
		System.out.println("Have "+gridReg.getNodeCount()+" sites");
		
		ExecutorService exec = Executors.newFixedThreadPool(threads);
		
		Stopwatch watch = Stopwatch.createStarted();
		DiscretizedFunc[] quickCurves = calc.calc(gridProv, gridReg, exec, threads);
		watch.stop();
		double quickSecs1 = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		
		System.out.println("Quick 1 took "+(float)quickSecs1+" s");
		DecimalFormat pDF = new DecimalFormat("0.00%");
		long numCacheHits = calc.numCacheHits.get();
		long numCacheMisses = calc.numCacheMisses.get();
		System.out.println("Calc #1 hits = "+numCacheHits+"/"+(numCacheHits+numCacheMisses)
				+" ("+pDF.format((double)numCacheHits/(double)(numCacheHits+numCacheMisses))+")");
		int numSourceCalcs = calc.sourcesProcessed.get();
		int numNonTruePointSources = calc.numNonTruePointSources.get();
		int numRupwise = calc.numRupwise.get();
		int numSourcewise = calc.numSourcewise.get();
		System.out.println("Calc #1 types for "+numSourceCalcs+" source calculations:"
				+"\n\t"+numNonTruePointSources+" ("+pDF.format((double)numNonTruePointSources/(double)numSourceCalcs)+") not true point sources"
				+"\n\t"+numRupwise+" ("+pDF.format((double)numRupwise/(double)numSourceCalcs)+") done rupture-wise"
				+"\n\t"+numSourcewise+" ("+pDF.format((double)numSourcewise/(double)numSourceCalcs)+") done source-wise");
		
		calc.numCacheHits.set(0l);
		calc.numCacheMisses.set(0l);
		calc.sourcesProcessed.set(0);
		calc.numNonTruePointSources.set(0);
		calc.numRupwise.set(0);
		calc.numSourcewise.set(0);
		watch.reset();
		watch.start();
		calc.calc(gridProv, gridReg, exec, threads);
		watch.stop();
		double quickSecs2 = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		
		exec.shutdown();
		
		System.out.println("Quick 2 took "+(float)quickSecs2+" s");
		numCacheHits = calc.numCacheHits.get();
		numCacheMisses = calc.numCacheMisses.get();
		System.out.println("Calc #1 hits = "+numCacheHits+"/"+(numCacheHits+numCacheMisses)
				+" ("+pDF.format((double)numCacheHits/(double)(numCacheHits+numCacheMisses))+")");
		numSourceCalcs = calc.sourcesProcessed.get();
		numNonTruePointSources = calc.numNonTruePointSources.get();
		numRupwise = calc.numRupwise.get();
		numSourcewise = calc.numSourcewise.get();
		System.out.println("Calc #2 types for "+numSourceCalcs+" source calculations:"
				+"\n\t"+numNonTruePointSources+" ("+pDF.format((double)numNonTruePointSources/(double)numSourceCalcs)+") not true point sources"
				+"\n\t"+numRupwise+" ("+pDF.format((double)numRupwise/(double)numSourceCalcs)+") done rupture-wise"
				+"\n\t"+numSourcewise+" ("+pDF.format((double)numSourcewise/(double)numSourceCalcs)+") done source-wise");
		
		SolHazardMapCalc tradCalc = new SolHazardMapCalc(sol, trtGMMs, gridReg, IncludeBackgroundOption.ONLY, period);
		tradCalc.setSourceFilter(sourceFilters);
		tradCalc.setGriddedSeismicitySettings(gridSettings);
		
		watch.reset();
		watch.start();
		tradCalc.calcHazardCurves(threads);
		watch.stop();
		double tradSecs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		System.out.println("Quick 1 took "+(float)quickSecs1+" s");
		System.out.println("Quick 2 took "+(float)quickSecs2+" s");
		System.out.println("Traditional took "+(float)tradSecs+" s");
		
		DiscretizedFunc[] tradCurves = tradCalc.getCurves(0d);
		
		DiscretizedFunc avgTrad = avgCurves(tradCurves);
		DiscretizedFunc avgQuick = avgCurves(quickCurves);
//		System.out.println("Average curves:");
//		System.out.println("X\tYtrad\tYquick\t%diff\tDiff");
//		for (int i=0; i<avgQuick.size(); i++) {
//			double x = avgTrad.getX(i);
//			double y1 = avgTrad.getY(i);
//			double y2 = avgQuick.getY(i);
//			double pDiff = 100d*(y2-y1)/y1;
//			double diff = y2-y1;
//			System.out.println((float)x+"\t"+(float)y1+"\t"+(float)y2+"\t"+(float)pDiff+"%\t"+(float)diff);
//		}
		System.out.println("Regional average curve stats:");
		System.out.println("\tWORST:  \t"+(float)worstDiff(avgTrad, avgQuick, true, 0d)+"%\t"+(float)worstDiff(avgTrad, avgQuick, false, 0d));
		System.out.println("\tAVERAGE:\t"+(float)avgAbsDiff(avgTrad, avgQuick, true, 0d)+"%\t"+(float)avgAbsDiff(avgTrad, avgQuick, false, 0d));
		System.out.println("\t2in50:\t"+(float)avgAbsDiff(avgTrad, avgQuick, true, 4e-4)+"%\t"+(float)avgAbsDiff(avgTrad, avgQuick, false, 4e-4));
		System.out.println("All curve stats:");
		System.out.println("\tWORST:  \t"+(float)worstDiff(tradCurves, quickCurves, true, 0d)+"%\t"+(float)worstDiff(tradCurves, quickCurves, false, 0d));
		System.out.println("\tWORST 2in50:  \t"+(float)worstDiff(tradCurves, quickCurves, true, 4e-4)+"%\t"+(float)worstDiff(tradCurves, quickCurves, false, 4e-4));
		System.out.println("\tAVERAGE:\t"+(float)avgAbsDiff(tradCurves, quickCurves, true, 0d)+"%\t"+(float)avgAbsDiff(tradCurves, quickCurves, false, 0d));
		System.out.println("\tAVERAGE 2in50:\t"+(float)avgAbsDiff(tradCurves, quickCurves, true, 4e-4)+"%\t"+(float)avgAbsDiff(tradCurves, quickCurves, false, 4e-4));
		
	}
	
	private static DiscretizedFunc avgCurves(DiscretizedFunc[] curves) {
		double[] xVals = new double[curves[0].size()];
		for (int i=0; i<xVals.length; i++)
			xVals[i] = curves[0].getX(i);
		double[] yVals = new double[xVals.length];
		for (DiscretizedFunc curve : curves) {
			for (int i=0; i<xVals.length; i++)
				yVals[i] += curve.getY(i);
		}
		
		double scale = 1d/curves.length;
		for (int i=0; i<yVals.length; i++)
			yVals[i] *= scale;
		
		return new LightFixedXFunc(xVals, yVals);
	}
	
	private static double worstDiff(DiscretizedFunc curve1, DiscretizedFunc curve2, boolean pDiff, double atProb) {
		return worstDiff(new DiscretizedFunc[] {curve1}, new DiscretizedFunc[] {curve2}, pDiff, atProb);
	}
	
	private static double worstDiff(DiscretizedFunc[] curves1, DiscretizedFunc[] curves2, boolean pDiff, double atProb) {
		double worst = 0d;
		double absWorst = 0d;
		for (int n=0; n<curves1.length; n++) {
			if (atProb > 0d) {
				double y1 = curves1[n].getFirstInterpolatedX_inLogXLogYDomain(atProb);
				double y2 = curves2[n].getFirstInterpolatedX_inLogXLogYDomain(atProb);
				double diff = y2-y1;
				if (pDiff)
					diff = 100*diff/y1;
				double abs = Math.abs(diff);
				if (abs > absWorst) {
					absWorst = abs;
					worst = diff;
				}
			} else {
				for (int i=0; i<curves1[n].size(); i++) {
					double y1 = curves1[n].getY(i);
					double y2 = curves2[n].getY(i);
					double diff = y2-y1;
					if (pDiff)
						diff = 100*diff/y1;
					double abs = Math.abs(diff);
					if (abs > absWorst) {
						absWorst = abs;
						worst = diff;
					}
				}
			}
		}
		return worst;
	}
	
	private static double avgAbsDiff(DiscretizedFunc curve1, DiscretizedFunc curve2, boolean pDiff, double atProb) {
		return avgAbsDiff(new DiscretizedFunc[] {curve1}, new DiscretizedFunc[] {curve2}, pDiff, atProb);
	}
	
	private static double avgAbsDiff(DiscretizedFunc[] curves1, DiscretizedFunc[] curves2, boolean pDiff, double atProb) {
		double sum = 0d;
		int num = 0;
		for (int n=0; n<curves1.length; n++) {
			if (atProb > 0d) {
				double y1 = curves1[n].getFirstInterpolatedX_inLogXLogYDomain(atProb);
				double y2 = curves2[n].getFirstInterpolatedX_inLogXLogYDomain(atProb);
				double diff = y2-y1;
				if (pDiff)
					diff = 100*diff/y1;
				double abs = Math.abs(diff);
				sum += abs;
				num++;
			} else {
				for (int i=0; i<curves1[n].size(); i++) {
					double y1 = curves1[n].getY(i);
					double y2 = curves2[n].getY(i);
					double diff = y2-y1;
					if (pDiff)
						diff = 100*diff/y1;
					double abs = Math.abs(diff);
					sum += abs;
					num++;
				}
			}
		}
		return sum/num;
	}

}
