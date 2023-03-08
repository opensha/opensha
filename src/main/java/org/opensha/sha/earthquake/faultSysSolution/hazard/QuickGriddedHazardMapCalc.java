package org.opensha.sha.earthquake.faultSysSolution.hazard;

import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
import org.opensha.nshmp2.erf.source.PointSource;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.earthquake.ProbEqkRupture;
import org.opensha.sha.earthquake.ProbEqkSource;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.param.BackgroundRupType;
import org.opensha.sha.earthquake.rupForecastImpl.PointSourceNshm.PointSurfaceNshm;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.PGA_Param;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

import com.google.common.base.Preconditions;

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
				synchronized (sourceIndexes) {
					if (sourceIndexes.isEmpty())
						break;
					int sourceID = sourceIndexes.pop();
					ProbEqkSource source = gridProv.getSource(sourceID, 1d, false, BackgroundRupType.POINT);
					
					quickSourceCalc(gridReg, source, gmpe, curves);
				}
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
		
		for (ProbEqkRupture rup : source) {
			if (nodeIndexes == null) {
				// figure out which nodes are within max dist of this site
				
				Location nodeLoc = ((PointSurface)rup.getRuptureSurface()).getLocation();
				Region circle = new Region(nodeLoc, maxDist*1.1);
				nodeIndexes = new ArrayList<>();
				nodeDists = new ArrayList<>();
				for (int i=0; i<gridReg.getNodeCount(); i++) {
					Location loc = gridReg.getLocation(i);
					if (circle.contains(loc)) {
						// might be within maxDist
						double dist = LocationUtils.horzDistanceFast(nodeLoc, loc);
						if ((float)dist <= (float)maxDist) {
							nodeIndexes.add(i);
							nodeDists.add(dist);
						}
					}
				}
				
				if (nodeIndexes.isEmpty()) {
					// can skip
					return;
				}
			}
			
			DiscretizedFunc[] exceeds = getCacheRupExceeds(rup, gmpe, curves[0]);
			
			double qkProb = rup.getProbability();
			
			for (int l=0; l<nodeIndexes.size(); l++) {
				int index = nodeIndexes.get(l);
				double dist = nodeDists.get(l);
				
				for (int i=0; i<exceedFunc.size(); i++)
					exceedFunc.set(i, exceeds[i].getInterpolatedY(dist));
				
				for(int k=0; k<exceedFunc.size(); k++)
					curves[index].set(k, curves[index].getY(k)*Math.pow(1-qkProb, curves[index].getY(k)));
			}
		}
	}
	
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
		}
		return exceeds;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
