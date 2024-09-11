package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
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
import org.opensha.sha.calc.params.filters.SourceFilterManager;
import org.opensha.sha.calc.params.filters.SourceFilters;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.util.SolHazardMapCalc;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.param.IncludeBackgroundOption;
import org.opensha.sha.earthquake.rupForecastImpl.PointSourceNshm.PointSurfaceNshm;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.gui.infoTools.IMT_Info;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.util.TectonicRegionType;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

/**
 * This class can quickly compute hazard maps for gridded seismicity point sources. It does this by first identifying
 * unique gridded seismicity rupture properties (see {@link UniqueRupture}). Then, for each unique gridded seismicity
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
	
	private ConcurrentMap<UniqueRupture, DiscretizedFunc[]> rupExceedsMap = new ConcurrentHashMap<>();
	
	private int minNodeCalcsForSourcewise;
	
	public static final int NUM_DISCR_DEFAULT = 100;

	/**
	 * This constructor uses the default number of interpolation points (see {@link #NUM_DISCR_DEFAULT})
	 * 
	 * @param gmpeSupplier GMM suplier, such as an {@link AttenRelRef}
	 * @param period calculation period
	 * @param xVals x values
	 * @param maxDist maximum site-source distance in km
	 */
	public QuickGriddedHazardMapCalc(Supplier<ScalarIMR> gmpeSupplier, double period, DiscretizedFunc xVals,
			SourceFilterManager sourceFitlers) {
		this(SolHazardMapCalc.wrapInTRTMap(gmpeSupplier), period, xVals, sourceFitlers);
	}
	


	/**
	 * This constructor uses the default number of interpolation points (see {@link #NUM_DISCR_DEFAULT})
	 * 
	 * @param gmpeSupplier GMM suplier, such as an {@link AttenRelRef}
	 * @param period calculation period
	 * @param xVals x values
	 * @param maxDist maximum site-source distance in km
	 */
	public QuickGriddedHazardMapCalc(Map<TectonicRegionType, ? extends Supplier<ScalarIMR>> gmpeSuppliers,
			double period, DiscretizedFunc xVals, SourceFilterManager sourceFitlers) {
		this(gmpeSuppliers, period, xVals, sourceFitlers, NUM_DISCR_DEFAULT);
	}

	/**
	 * 
	 * @param gmpeSupplier GMM suplier, such as an {@link AttenRelRef}
	 * @param period calculation period
	 * @param xVals x values
	 * @param maxDist maximum site-source distance in km
	 * @param numDiscr number of interpolation points
	 */
	public QuickGriddedHazardMapCalc(Supplier<ScalarIMR> gmpeSupplier, double period, DiscretizedFunc xVals,
			SourceFilterManager sourceFitlers, int numDiscr) {
		this(SolHazardMapCalc.wrapInTRTMap(gmpeSupplier), period, xVals, sourceFitlers, numDiscr);
	}

	/**
	 * 
	 * @param gmpeSupplier GMM suplier, such as an {@link AttenRelRef}
	 * @param period calculation period
	 * @param xVals x values
	 * @param maxDist maximum site-source distance in km
	 * @param numDiscr number of interpolation points
	 */
	public QuickGriddedHazardMapCalc(Map<TectonicRegionType, ? extends Supplier<ScalarIMR>> gmpeSuppliers,
			double period, DiscretizedFunc xVals, SourceFilterManager sourceFitlers, int numDiscr) {
		this.gmpeSuppliers = gmpeSuppliers;
		this.period = period;
		this.xVals = xVals;
		
		minNodeCalcsForSourcewise = 3*numDiscr/2;
		trtMaxDists = new EnumMap<>(TectonicRegionType.class);
		trtLogSpacedDiscrs = new EnumMap<>(TectonicRegionType.class);
		trtDistVals = new EnumMap<>(TectonicRegionType.class);
		trtDistDiscr = new EnumMap<>(TectonicRegionType.class);
		for (TectonicRegionType trt : gmpeSuppliers.keySet()) {
			double maxDist = SolHazardMapCalc.getMaxDistForTRT(sourceFitlers, trt);
			trtMaxDists.put(trt, maxDist);
			EvenlyDiscretizedFunc logSpacedDiscr = new EvenlyDiscretizedFunc(
					Math.log(0.1), Math.log(maxDist+5d), numDiscr-1);
			trtLogSpacedDiscrs.put(trt, logSpacedDiscr);
			double[] distVals = new double[numDiscr];
			for (int i=1; i<distVals.length; i++)
				distVals[i] = Math.exp(logSpacedDiscr.getX(i-1));
			trtDistVals.put(trt, distVals);
			trtDistDiscr.put(trt, new LightFixedXFunc(distVals, new double[distVals.length]));
		}
	}
	
	/**
	 * These properties define a unique rupture, for which conditional exceedance probabilities will be calculated
	 * and cached separately. Any new fields must be added to the hashCode and equals methods
	 * 
	 * @author kevin
	 *
	 */
	static class UniqueRupture {
		private final double rake;
		private final double mag;
		private final double zTOR;
		private final double width;
		private final double dip;
		private final boolean footwall;
		public UniqueRupture(EqkRupture rup) {
			this.rake = rup.getAveRake();
			this.mag = rup.getMag();
			RuptureSurface surf = rup.getRuptureSurface();
			this.zTOR = surf.getAveRupTopDepth();
			this.width = surf.getAveWidth();
			this.dip = surf.getAveDip();
			if (surf instanceof PointSurfaceNshm) {
				footwall = ((PointSurfaceNshm)surf).isOnFootwall();
			} else {
				Location ptLoc = ((PointSurface)surf).getLocation();
				Location loc = LocationUtils.location(ptLoc, 0d, 50d);
				footwall = surf.getDistanceX(loc) < 0d;
			}
		}
		@Override
		public int hashCode() {
			return Objects.hash(dip, footwall, mag, rake, width, zTOR);
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			UniqueRupture other = (UniqueRupture) obj;
			return Double.doubleToLongBits(dip) == Double.doubleToLongBits(other.dip) && footwall == other.footwall
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
	public DiscretizedFunc[] calc(GridSourceProvider gridProv, GriddedRegion gridReg, int threads) {
		// calculation done in log-x space
		DiscretizedFunc logXVals = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : xVals)
			logXVals.set(Math.log(pt.getX()), 0d);
		
		// this will be used to distribute sources to worker threads
		ArrayDeque<Integer> sourceIndexes = new ArrayDeque<>(gridProv.getNumSources());
		for (int i=0; i<gridProv.getNumSources(); i++)
			sourceIndexes.add(i);
		
		// spin up worker threads
		List<CalcThread> calcThreads = new ArrayList<>(threads);
		
		for (int i=0; i<threads; i++) {
			CalcThread thread = new CalcThread(gridProv, logXVals, gridReg, sourceIndexes);
			thread.start();
			calcThreads.add(thread);
		}
		
		// join them
		DiscretizedFunc[] curves = null;
		for (CalcThread thread : calcThreads) {
			try {
				thread.join();
				if (thread.exception != null)
					throw ExceptionUtils.asRuntimeException(thread.exception);
			} catch (InterruptedException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			
			
			if (thread.curves == null)
				continue;
			
			if (curves == null) {
				// use curves from this calc thread
				curves = thread.curves;
			} else {
				// add them (actually multiply, these are 1-P curves)
				for (int i=0; i<curves.length; i++)
					for (int k=0; k<curves[i].size(); k++)
						curves[i].set(k, curves[i].getY(k)*thread.curves[i].getY(k));
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
	
	private class CalcThread extends Thread {
		
		private DiscretizedFunc[] curves;
		private GridSourceProvider gridProv;
		private DiscretizedFunc logXVals;
		private GriddedRegion gridReg;
		private ArrayDeque<Integer> sourceIndexes;
		
		private Throwable exception;
		
		public CalcThread(GridSourceProvider gridProv, DiscretizedFunc logXVals,
				GriddedRegion gridReg, ArrayDeque<Integer> sourceIndexes) {
			this.gridProv = gridProv;
			this.logXVals = logXVals;
			this.gridReg = gridReg;
			this.sourceIndexes = sourceIndexes;
		}

		@Override
		public void run() {
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
					SolHazardMapCalc.setIMforPeriod(gmm, period);
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
					ProbEqkSource source = gridProv.getSource(sourceID, 1d, null, BackgroundRupType.POINT);
					
					TectonicRegionType trt = source.getTectonicRegionType();
					ScalarIMR gmpe = gmpeMap.get(trt);
					
					quickSourceCalc(gridReg, source, gmpe, curves);
				}
			} catch (Throwable t) {
				exception = t;
				return;
			}
		}
	}
	
	private void quickSourceCalc(GriddedRegion gridReg, ProbEqkSource source, ScalarIMR gmpe, DiscretizedFunc[] curves) {
		double[] xValsArray = new double[curves[0].size()];
		for (int i=0; i<xValsArray.length; i++)
			xValsArray[i] = curves[0].getX(i);
		LightFixedXFunc exceedFunc = new LightFixedXFunc(xValsArray, new double[xValsArray.length]);
		
		TectonicRegionType trt = source.getTectonicRegionType();
		if (!trtMaxDists.containsKey(trt)) {
			Preconditions.checkState(trtMaxDists.keySet().size() == 1);
			trt = trtMaxDists.keySet().iterator().next();
		}
		double maxDist = trtMaxDists.get(trt);
		double[] distVals = trtDistVals.get(trt);
		EvenlyDiscretizedFunc logSpacedDiscr = trtLogSpacedDiscrs.get(trt);
		DiscretizedFunc distDiscr = trtDistDiscr.get(trt);
		
		// figure out locations
		List<Integer> nodeIndexes = new ArrayList<>();
		List<Double> nodeDists = new ArrayList<>();
		Site site = new Site(gridReg.getLocation(0));
		for (int i=0; i<gridReg.getNodeCount(); i++) {
			Location loc = gridReg.getLocation(i);
			site.setLocation(loc);
			double dist = source.getMinDistance(site);
			if (dist <= maxDist) {
				nodeIndexes.add(i);
				nodeDists.add(dist);
			}
		}
		
		if (nodeIndexes.isEmpty()) {
			// can skip
			return;
		}
		
		/*
		 * the expensive part of this calculation is the interpolation step. if we have few sites, it's faster to do it
		 * for each site and rupture individually. if we have lots of sites (or lots of ruptures), it's faster to
		 * calculate distance-dependent source exceedance probabilities (not conditional, taking the rupture probs
		 * into account)
		 * 
		 * but if it's not actually a point source, we need to do it the long way anyway
		 */
		int nodeCalcs = nodeIndexes.size() * source.getNumRuptures();
		
		boolean truePointSource = true;
		List<ProbEqkRupture> rups = source.getRuptureList();
		for (ProbEqkRupture rup : rups) {
			if (!(rup.getRuptureSurface() instanceof PointSurface)) {
				truePointSource = false;
				break;
			}
		}
		
		if (!truePointSource) {
			// no shortcut for this one
			for (ProbEqkRupture rup : source) {
				gmpe.setEqkRupture(rup);
				
				for (int l=0; l<nodeIndexes.size(); l++) {
					int index = nodeIndexes.get(l);
					
					gmpe.setSiteLocation(gridReg.getLocation(index));
					gmpe.getExceedProbabilities(exceedFunc);
					
					double invQkProb = 1d-rup.getProbability();
					for(int k=0; k<exceedFunc.size(); k++)
						curves[index].set(k, curves[index].getY(k)*Math.pow(invQkProb, exceedFunc.getY(k)));
				}
			}
		} else if (nodeCalcs < minNodeCalcsForSourcewise) {
			// do it individually for each rupture at each unique distance
			
			for (ProbEqkRupture rup : source) {
				DiscretizedFunc[] exceeds = getCacheRupExceeds(rup, gmpe, curves[0], distVals, distDiscr);
				
				for (int l=0; l<nodeIndexes.size(); l++) {
					int index = nodeIndexes.get(l);
					double dist = nodeDists.get(l);
					
					quickInterp(exceeds, exceedFunc, dist, distVals, logSpacedDiscr, distDiscr);
					
					double invQkProb = 1d-rup.getProbability();
					for(int k=0; k<exceedFunc.size(); k++)
						curves[index].set(k, curves[index].getY(k)*Math.pow(invQkProb, exceedFunc.getY(k)));
				}
			}
		} else {
			// calculate a source non exceedance functions
			DiscretizedFunc[] sourceNonExceeds = new DiscretizedFunc[xValsArray.length];
			for (int k=0; k<sourceNonExceeds.length; k++) {
				double[] yVals = new double[distVals.length];
				for (int i=0; i<yVals.length; i++)
					yVals[i] = 1;
				sourceNonExceeds[k] = new LightFixedXFunc(distVals, yVals);
			}
			
			for (ProbEqkRupture rup : source) {
				DiscretizedFunc[] exceeds = getCacheRupExceeds(rup, gmpe, curves[0], distVals, distDiscr);
				
				double invQkProb = 1d-rup.getProbability();
				for (int i=0; i<sourceNonExceeds.length; i++)
					for(int k=0; k<sourceNonExceeds[i].size(); k++)
						sourceNonExceeds[i].set(k, sourceNonExceeds[i].getY(k)*Math.pow(invQkProb, exceeds[i].getY(k)));
			}
			
			// now interpolate those onto sites
			for (int l=0; l<nodeIndexes.size(); l++) {
				int index = nodeIndexes.get(l);
				double dist = nodeDists.get(l);
				
				quickInterp(sourceNonExceeds, exceedFunc, dist, distVals, logSpacedDiscr, distDiscr);
				
				for(int k=0; k<exceedFunc.size(); k++)
					curves[index].set(k, curves[index].getY(k)*exceedFunc.getY(k));
			}
		}
	}
	
	/**
	 * Quick interpolation for an array of functions, each as a function of distance.
	 * @param exceeds array of functions, each as a function of distance
	 * @param dist distance in km
	 * @param exceedFunc function to be filled in
	 */
	private void quickInterp(DiscretizedFunc[] exceeds, DiscretizedFunc exceedFunc, double dist,
			double[] distVals, EvenlyDiscretizedFunc logSpacedDiscr, DiscretizedFunc distDiscr) {
		if ((float)dist == 0f) {
			// shortcut
			for (int i=0; i<exceedFunc.size(); i++)
				exceedFunc.set(i, exceeds[i].getY(0));
		} else {
			
//			int x1Ind = distDiscr.getXIndexBefore(dist);
//			int x1Ind = (int)Math.floor((dist-distDiscr.getMinX())/distDiscr.getDelta());
			int x1Ind;
			if (dist < distVals[1]) {
				x1Ind = 0;
			} else {
				double logX = Math.log(dist);
				x1Ind = 1 + (int)Math.floor((logX-logSpacedDiscr.getMinX())/logSpacedDiscr.getDelta());
			}
			int x2Ind = x1Ind+1;
			
			// seems to do best with logX = true, and logY = false
			final boolean logX = x1Ind > 0;
//			final boolean logY = true;
			final boolean logY = false;
			
			double x = dist;
			double x1 = distDiscr.getX(x1Ind);
			double x2 = distDiscr.getX(x2Ind);
			
			if (logX) {
				x1 = Math.log(x1);
				x2 = Math.log(x2);
				x = Math.log(x);
			}
			
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

	private long numCacheMisses = 0;
	private long numCacheHits = 0;
	
	private DiscretizedFunc[] getCacheRupExceeds(ProbEqkRupture rup, ScalarIMR gmpe, DiscretizedFunc xVals,
			double[] distVals, DiscretizedFunc distDiscr) {
		UniqueRupture uRup = new UniqueRupture(rup);
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
			numCacheMisses++;
		} else {
			numCacheHits++;
		}
		return exceeds;
	}

	public static void main(String[] args) throws IOException {
		FaultSystemSolution sol = FaultSystemSolution.load(new File("/data/kevin/nshm23/batch_inversions/"
				+ "2023_03_01-nshm23_branches-NSHM23_v2-CoulombRupSet-TotNuclRate-NoRed-ThreshAvgIterRelGR/"
				+ "results_NSHM23_v2_CoulombRupSet_branch_averaged_gridded.zip"));
		
		GridSourceProvider gridProv = sol.getGridSourceProvider();
		
		DiscretizedFunc xVals = new IMT_Info().getDefaultHazardCurve(PGA_Param.NAME);
		
		AttenRelRef gmpeRef = AttenRelRef.ASK_2014;
		int threads = 20;
		double period = 0d;
		double spacing = 0.2d;
		int numPts = 50;
		
		SourceFilterManager sourceFilters = new SourceFilterManager(SourceFilters.FIXED_DIST_CUTOFF);
		
		QuickGriddedHazardMapCalc calc = new QuickGriddedHazardMapCalc(gmpeRef, period, xVals, sourceFilters, numPts);
//		calc.minNodesForSourcewise = Integer.MAX_VALUE;
		
		Region region = NSHM23_RegionLoader.loadFullConterminousWUS();
		GriddedRegion gridReg = new GriddedRegion(region, spacing, GriddedRegion.ANCHOR_0_0);
		
		Stopwatch watch = Stopwatch.createStarted();
		DiscretizedFunc[] quickCurves = calc.calc(gridProv, gridReg, threads);
		watch.stop();
		double quickSecs1 = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		
		System.out.println("Quick 1 took "+(float)quickSecs1+" s");
		DecimalFormat pDF = new DecimalFormat("0.00%");
		System.out.println("Calc #1 hits = "+calc.numCacheHits+"/"+(calc.numCacheHits+calc.numCacheMisses)
				+" ("+pDF.format((double)calc.numCacheHits/(double)(calc.numCacheHits+calc.numCacheMisses))+")");
		
		calc.numCacheHits = 0;
		calc.numCacheMisses = 0;
		watch.reset();
		watch.start();
		calc.calc(gridProv, gridReg, threads);
		watch.stop();
		double quickSecs2 = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		
		System.out.println("Quick 2 took "+(float)quickSecs2+" s");
		System.out.println("Calc #1 hits = "+calc.numCacheHits+"/"+(calc.numCacheHits+calc.numCacheMisses)
				+" ("+pDF.format((double)calc.numCacheHits/(double)(calc.numCacheHits+calc.numCacheMisses))+")");
		
		SolHazardMapCalc tradCalc = new SolHazardMapCalc(sol, gmpeRef, gridReg, IncludeBackgroundOption.ONLY, period);
		tradCalc.setSourceFilter(sourceFilters);
		
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
		System.out.println("Average curves:");
		System.out.println("X\tYtrad\tYquick\t%diff\tDiff");
		for (int i=0; i<avgQuick.size(); i++) {
			double x = avgTrad.getX(i);
			double y1 = avgTrad.getY(i);
			double y2 = avgQuick.getY(i);
			double pDiff = 100d*(y2-y1)/y1;
			double diff = y2-y1;
			System.out.println((float)x+"\t"+(float)y1+"\t"+(float)y2+"\t"+(float)pDiff+"\t"+(float)diff);
		}
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

}
