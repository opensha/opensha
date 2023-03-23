package org.opensha.sha.simulators.distCalc;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.cache.CacheEnabledSurface;
import org.opensha.sha.faultSurface.cache.SurfaceCachingPolicy;
import org.opensha.sha.faultSurface.cache.SurfaceDistanceCache;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.distCalc.SimRuptureDistCalcUtils.DistanceType;
import org.opensha.sha.simulators.distCalc.SimRuptureDistCalcUtils.FootwallAwareSurfaceDistances;
import org.opensha.sha.simulators.distCalc.SimRuptureDistCalcUtils.LocationElementDistanceCache;
import org.opensha.sha.simulators.distCalc.SimRuptureDistCalcUtils.LocationElementDistanceCacheFactory;
import org.opensha.sha.simulators.distCalc.SimRuptureDistCalcUtils.Scalar;

public class SimEventCumDistFuncSurface implements CacheEnabledSurface {
	
	private SimulatorEvent event;
	private Scalar scalar;
	private double fractThreshold;
	private double absThreshold;
	private double fractThresholdX;
	
	private List<SimulatorElement> elems;
	private double[] slips;
	
	// create cache using default caching policy
	private SurfaceDistanceCache cache = SurfaceCachingPolicy.build(this);
	
	private double dip = Double.NaN;
	private double zTOR = Double.NaN;
	private double ddw = Double.NaN;
	private double area = Double.NaN;
	private LocationElementDistanceCacheFactory locCacheFactory;

	public SimEventCumDistFuncSurface(SimulatorEvent event, Scalar scalar, double fractThreshold, double absThreshold,
			double fractThresholdX) {
		this(event, scalar, fractThreshold, absThreshold, fractThresholdX, new LocationElementDistanceCacheFactory());
	}

	public SimEventCumDistFuncSurface(SimulatorEvent event, Scalar scalar, double fractThreshold, double absThreshold,
			double fractThresholdX, LocationElementDistanceCacheFactory locCacheFactory) {
		this.event = event;
		this.scalar = scalar;
		this.fractThreshold = fractThreshold;
		this.absThreshold = absThreshold;
		this.fractThresholdX = fractThresholdX;
		this.locCacheFactory = locCacheFactory;
		
		this.elems = event.getAllElements();
		this.slips = event.getAllElementSlips();
	}
	
	private void checkInitAvgQuantities() {
		if (Double.isNaN(dip)) {
			synchronized (this) {
				if (Double.isFinite(dip))
					return;
				double sumDipWeight = 0d;
				double sumWeightedDip = 0d;

				Map<Integer, double[]> sectDepthsMap = new HashMap<>();
				Map<Integer, Double> sectDipsMap = new HashMap<>();
				Map<Integer, Double> sectWeights = new HashMap<>();
				
				double area = 0d;
				for (int i=0; i<elems.size(); i++) {
					SimulatorElement elem = elems.get(i);
					area += elem.getArea();
					double scalarVal = scalar.calc(elem, slips[i]);
					
					sumDipWeight += scalarVal;
					sumWeightedDip += scalarVal*elem.getFocalMechanism().getDip();
					
					int id = elem.getSectionID();
					if (id < 0)
						id = -(elem.getFaultID()+1);
					double[] sectDepths = sectDepthsMap.get(id);
					Double sectDip = sectDipsMap.get(id);
					Double sectWeight = sectWeights.get(id);
					if (sectDepths == null) {
						sectDepths = new double[] { Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY };
						sectDip = 0d;
						sectWeight = 0d;
					}
					for (Location loc : elem.getVertices()) {
						sectDepths[0] = Double.min(sectDepths[0], loc.depth);
						sectDepths[1] = Double.max(sectDepths[1], loc.depth);
					}
					sectDepthsMap.put(id, sectDepths);
					
					sectDip += scalarVal*elem.getFocalMechanism().getDip();
					sectDipsMap.put(id, sectDip);
					
					sectWeight += scalarVal;
					sectWeights.put(id, sectWeight);
					
				}
				
				if (sectDepthsMap.size() == 1) {
					double[] depths = sectDepthsMap.values().iterator().next();
					double sectDip = sectDipsMap.values().iterator().next() / sectWeights.values().iterator().next();
					zTOR = depths[0];
					ddw = (depths[1]-depths[0])/Math.sin(sectDip*Math.PI/ 180);
				} else {
					double sumZtors = 0d;
					double sumDDWs = 0d;
					double sumWeights = 0d;
					for (int id : sectDepthsMap.keySet()) {
						double[] depths = sectDepthsMap.get(id);
						double weight = sectWeights.get(id);
						double dip = sectDipsMap.get(id)/weight;
						double ddw = (depths[1]-depths[0])/Math.sin(dip*Math.PI/ 180);
						
						sumZtors += depths[0]*weight;
						sumDDWs += ddw*weight;
						sumWeights += weight;
					}
					zTOR = sumZtors/sumWeights;
					ddw = sumDDWs/sumWeights;
				}
				this.area = area*1e-6;
				dip = sumWeightedDip/sumDipWeight;
				if (dip != 90d && dip < 90.1 && dip > 89.9)
					// avoid floating point errors
					dip = 90d;
				if (zTOR < 0.01 && zTOR > -0.01)
					// avoid floating point errors
					zTOR = 0d;
			}
		}
	}

	@Override
	public SurfaceDistances calcDistances(Location loc) {
		LocationElementDistanceCache siteLocDistCache = locCacheFactory.getCache(loc);
		
		DiscretizedFunc rJBFunc = SimRuptureDistCalcUtils.calcDistScalarFunc(event, loc, siteLocDistCache,
				DistanceType.R_JB, scalar);
		double distanceJB = calcDistance(rJBFunc);
		
		DiscretizedFunc rRupFunc = SimRuptureDistCalcUtils.calcDistScalarFunc(event, loc, siteLocDistCache,
				DistanceType.R_RUP, scalar);
		double distanceRup = calcDistance(rRupFunc);
		
		DiscretizedFunc rSeisFunc = SimRuptureDistCalcUtils.calcDistScalarFunc(event, loc, siteLocDistCache,
				DistanceType.R_SEIS, scalar);
		double distanceSeis = calcDistance(rSeisFunc);
		
		double maxDistJBforX = calcDistance(rSeisFunc, fractThresholdX, Double.NaN);
		
		double wtFootwall = 0d;
		double wtHangingwall = 0d;
		for (int i=0; i<slips.length; i++) {
			SimulatorElement elem = elems.get(i);
			FootwallAwareSurfaceDistances elemDists = siteLocDistCache.getDistances(elem);
			double rJB = elemDists.getDistanceJB();
			if (rJB <= maxDistJBforX) {
				double weight = scalar.calc(elem, slips[i]);
				if (elemDists.isOnFootfall())
					wtFootwall += weight;
				else
					wtHangingwall += weight;
			}
		}
		boolean footwall = (float)wtFootwall >= (float)wtHangingwall;
		
		FootwallAwareSurfaceDistances dists = new FootwallAwareSurfaceDistances(
				distanceRup, distanceJB, distanceSeis, footwall);
		
		return dists;
	}
	
	private double calcDistance(DiscretizedFunc scalarDistCmlFunc) {
		return calcDistance(scalarDistCmlFunc, fractThreshold, absThreshold);
	}
	
	private static double calcDistance(DiscretizedFunc scalarDistCmlFunc, double fractThreshold, double absThreshold) {
		double fractTarget = fractThreshold * scalarDistCmlFunc.getY(scalarDistCmlFunc.size()-1);
		double targetVal;
		if (absThreshold > 0d)
			targetVal = Double.min(absThreshold, fractThreshold);
		else
			targetVal = fractTarget;
		for (Point2D pt : scalarDistCmlFunc)
			if (pt.getY() >= targetVal)
				return pt.getX();
		return Double.NaN;
	}

	@Override
	public ListIterator<Location> getLocationsIterator() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public LocationList getEvenlyDiscritizedPerimeter() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public LocationList getPerimeter() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public boolean isPointSurface() {
		return false;
	}

	@Override
	public double calcQuickDistance(Location loc) {
		Location closest = null;
		double minSqCartesian = Double.POSITIVE_INFINITY;
		double latDiff, lonDiff, sqCartesian;
		for (SimulatorElement elem : event.getAllElements()) {
			Location center = elem.getCenterLocation();
			latDiff = center.lat-loc.lat;
			lonDiff = center.lon-loc.lon;
			sqCartesian = latDiff*latDiff + lonDiff*lonDiff;
			if (sqCartesian < minSqCartesian) {
				minSqCartesian = sqCartesian;
				closest = center;
			}
		}
		return LocationUtils.horzDistanceFast(loc, closest);
	}

	@Override
	public double calcDistanceX(Location loc) {
		return getDistanceX(loc);
	}

	@Override
	public void clearCache() {
		cache.clearCache();
	}

	@Override
	public double getAveDip() {
		checkInitAvgQuantities();
		return dip;
	}

	@Override
	public double getAveStrike() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getAveLength() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getAveWidth() {
		checkInitAvgQuantities();
		return ddw;
	}

	@Override
	public double getArea() {
		checkInitAvgQuantities();
		return area;
	}

	@Override
	public double getAreaInsideRegion(Region region) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public LocationList getEvenlyDiscritizedListOfLocsOnSurface() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public FaultTrace getEvenlyDiscritizedUpperEdge() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public LocationList getEvenlyDiscritizedLowerEdge() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getAveGridSpacing() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getQuickDistance(Location siteLoc) {
		return cache.getQuickDistance(siteLoc);
	}

	@Override
	public double getDistanceRup(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceRup();
	}

	@Override
	public double getDistanceJB(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceJB();
	}

	@Override
	public double getDistanceSeis(Location siteLoc) {
		return cache.getSurfaceDistances(siteLoc).getDistanceSeis();
	}

	@Override
	public double getDistanceX(Location siteLoc) {
		FootwallAwareSurfaceDistances dists = (FootwallAwareSurfaceDistances)cache.getSurfaceDistances(siteLoc);
		return dists.isOnFootfall() ? -dists.getDistanceJB() : dists.getDistanceJB();
	}

	@Override
	public double getAveRupTopDepth() {
		checkInitAvgQuantities();
		return zTOR;
	}

	@Override
	public double getAveDipDirection() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public FaultTrace getUpperEdge() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public Location getFirstLocOnUpperEdge() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public Location getLastLocOnUpperEdge() {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public double getFractionOfSurfaceInRegion(Region region) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public String getInfo() {
		return null;
	}

	@Override
	public double getMinDistance(RuptureSurface surface) {

		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public RuptureSurface getMoved(LocationVector v) {
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public RuptureSurface copyShallow() {
		return new SimEventCumDistFuncSurface(event, scalar, fractThreshold, absThreshold, fractThresholdX);
	}

}
