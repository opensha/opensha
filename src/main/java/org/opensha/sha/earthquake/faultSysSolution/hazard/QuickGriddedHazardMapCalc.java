package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
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
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.Interpolate;
import org.opensha.nshmp2.erf.source.PointSource;
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
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

public class QuickGriddedHazardMapCalc {
	
	private Supplier<ScalarIMR> gmpeSupplier;
	private double period;
	private DiscretizedFunc xVals;
	private double maxDist;
	
	private EvenlyDiscretizedFunc distDiscr;
	
	private ConcurrentMap<UniqueRupture, DiscretizedFunc[]> rupExceedsMap = new ConcurrentHashMap<>();

	public QuickGriddedHazardMapCalc(Supplier<ScalarIMR> gmpeSupplier, double period, DiscretizedFunc xVals, double maxDist, int numDiscr) {
		this.gmpeSupplier = gmpeSupplier;
		this.period = period;
		this.xVals = xVals;
		this.maxDist = maxDist;
		
		distDiscr = new EvenlyDiscretizedFunc(0d, maxDist+1d, numDiscr);
	}
	
	private static class UniqueRupture {
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
	
	public DiscretizedFunc[] calc(GridSourceProvider gridProv, GriddedRegion gridReg, int threads) {
		DiscretizedFunc logXVals = new ArbitrarilyDiscretizedFunc();
		for (Point2D pt : xVals)
			logXVals.set(Math.log(pt.getX()), 0d);
		
		ArrayDeque<Integer> sourceIndexes = new ArrayDeque<>(gridProv.size());
		for (int i=0; i<gridProv.size(); i++)
			sourceIndexes.add(i);
		
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
		
		// take 1 - curve vals and convert to linear x
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
	
	private class CalcThread extends Thread {
		
		private DiscretizedFunc[] curves;
		private GridSourceProvider gridProv;
		private DiscretizedFunc logXVals;
		private GriddedRegion gridReg;
		private ArrayDeque<Integer> sourceIndexes;
		
		public CalcThread(GridSourceProvider gridProv, DiscretizedFunc logXVals,
				GriddedRegion gridReg, ArrayDeque<Integer> sourceIndexes) {
			this.gridProv = gridProv;
			this.logXVals = logXVals;
			this.gridReg = gridReg;
			this.sourceIndexes = sourceIndexes;
		}

		@Override
		public void run() {
			ScalarIMR gmpe = gmpeSupplier.get();
			if (period == 0d) {
				gmpe.setIntensityMeasure(PGA_Param.NAME);
			} else {
				gmpe.setIntensityMeasure(SA_Param.NAME);
				SA_Param.setPeriodInSA_Param(gmpe.getIntensityMeasure(), period);
			}
			
			Site testSite = new Site(new Location(0d, 0d));
			for (Parameter<?> param : gmpe.getSiteParams())
				testSite.addParameter(param);
			gmpe.setSite(testSite);
			
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
				ProbEqkSource source = gridProv.getSource(sourceID, 1d, false, BackgroundRupType.POINT);
				
				quickSourceCalc(gridReg, source, gmpe, curves);
			}
		}
	}
	
	private void quickSourceCalc(GriddedRegion gridReg, ProbEqkSource source, ScalarIMR gmpe, DiscretizedFunc[] curves) {
		List<Integer> nodeIndexes = null;
		List<Double> nodeDists = null;
		
		double[] xValsArray = new double[curves[0].size()];
		for (int i=0; i<xValsArray.length; i++)
			xValsArray[i] = curves[0].getX(i);
		LightFixedXFunc exceedFunc = new LightFixedXFunc(xValsArray, new double[xValsArray.length]);
		
		double minXForLog = distDiscr.getX(1);
		
		for (ProbEqkRupture rup : source) {
			if (nodeIndexes == null) {
				// figure out which nodes are within max dist of this site
				
				Location nodeLoc = ((PointSurface)rup.getRuptureSurface()).getLocation();
				nodeIndexes = new ArrayList<>();
				nodeDists = new ArrayList<>();
				Site site = new Site(nodeLoc);
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
			}
			
			DiscretizedFunc[] exceeds = getCacheRupExceeds(rup, gmpe, curves[0]);
			
			for (int l=0; l<nodeIndexes.size(); l++) {
				int index = nodeIndexes.get(l);
				double dist = nodeDists.get(l);
				
				if ((float)dist == 0f) {
					// shortcut
					for (int i=0; i<exceedFunc.size(); i++)
						exceedFunc.set(i, exceeds[i].getY(0));
				} else {
					// seems to do best with logX = true, and logY = false
					final boolean logX = dist>minXForLog;
//					final boolean logY = true;
					final boolean logY = false;
					
//					int x1Ind = distDiscr.getXIndexBefore(dist);
					int x1Ind = (int)Math.floor((dist-distDiscr.getMinX())/distDiscr.getDelta());
					int x2Ind = x1Ind+1;
					
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
						
						double y;
						if(y1==0 && y2==0) {
							y = 0d;
						} else {
							if (logY) {
								y1 = Math.log(y1);
								y2 = Math.log(y2);
							}
							y = y1 + xRatio*(y2-y1);
							if (logY)
								y = Math.exp(y);
						}
						exceedFunc.set(i, y);
					}
				}
				
				double invQkProb = 1d-rup.getProbability();
				for(int k=0; k<exceedFunc.size(); k++)
					curves[index].set(k, curves[index].getY(k)*Math.pow(invQkProb, exceedFunc.getY(k)));
			}
		}
	}

	private long numCacheMisses = 0;
	private long numCacheHits = 0;
	
	private DiscretizedFunc[] getCacheRupExceeds(ProbEqkRupture rup, ScalarIMR gmpe, DiscretizedFunc xVals) {
		UniqueRupture uRup = new UniqueRupture(rup);
		DiscretizedFunc[] exceeds = rupExceedsMap.get(uRup);
		if (exceeds == null) {
			// calculate it
			exceeds = new DiscretizedFunc[xVals.size()];
			for (int i=0; i<exceeds.length; i++)
				exceeds[i] = new EvenlyDiscretizedFunc(distDiscr.getMinX(), distDiscr.size(), distDiscr.getDelta());
			
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
		int numPts = 500;
		double maxDist = 200d;
		
		QuickGriddedHazardMapCalc calc = new QuickGriddedHazardMapCalc(gmpeRef, period, xVals, maxDist, numPts);
		
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
		tradCalc.setMaxSourceSiteDist(maxDist);
		
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
