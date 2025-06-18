package org.opensha.sha.simulators.distCalc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opensha.commons.calc.FaultMomentCalc;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.faultSurface.cache.SurfaceDistances;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.Vertex;

import com.google.common.base.Preconditions;

public class SimRuptureDistCalcUtils {
	
	public static class LocationElementDistanceCacheFactory {
		
		private Location[] locs;
		private LocationElementDistanceCache[] caches;
		
		private int index;
		
		public LocationElementDistanceCacheFactory() {
			this(Integer.max(10, Runtime.getRuntime().availableProcessors()));
		}
		
		public LocationElementDistanceCacheFactory(int maxSize) {
			locs = new Location[maxSize];
			caches = new LocationElementDistanceCache[maxSize];
			index = 0;
		}
		
		public synchronized LocationElementDistanceCache getCache(Location loc) {
			// see if already cached
			for (int i=0; i<locs.length; i++)
				if (locs[i] != null && loc.equals(locs[i]))
					return caches[i];
			
			// create new cache
			LocationElementDistanceCache cache = buildSiteLocDistCache(loc);
			locs[index] = loc;
			caches[index] = cache;
			
			index++;
			if (index == locs.length)
				index = 0;
			return cache;
		}
	}
	
	public static LocationElementDistanceCache buildSiteLocDistCache(Location siteLoc) {
		return new LocationElementDistanceCache(siteLoc);
	}
	
	public static class LocationElementDistanceCache {
		
		private Location siteLoc;
		private Map<SimulatorElement, Location[]> elemVertexMap;
		private Map<Location, SurfaceDistances> vertexDistsMap;
		private Map<SimulatorElement, FootwallAwareSurfaceDistances> elemDistsMap;

		private LocationElementDistanceCache(Location siteLoc) {
			this.siteLoc = siteLoc;
			
			elemVertexMap = new ConcurrentHashMap<>();
			vertexDistsMap = new ConcurrentHashMap<>();
			elemDistsMap = new ConcurrentHashMap<>();
		}
		
		public FootwallAwareSurfaceDistances getDistances(SimulatorElement elem) {
			FootwallAwareSurfaceDistances dists = elemDistsMap.get(elem);
			if (dists == null) {
				// calculate it
				Location[] locs = elemVertexMap.get(elem);
				if (locs == null) {
					// need to convert to vanilla locations, with equals and hashCode based on location only and not any
					// vertex metadata. also do some rounding, such that almost identical points are mapped to the same location
					Vertex[] verts = elem.getVertices();
					locs = new Location[verts.length];
					for (int i=0; i<locs.length; i++)
						locs[i] = new Location((float)verts[i].getLatitude(),
								(float)verts[i].getLongitude(), (float)verts[i].getDepth());
					elemVertexMap.put(elem, locs);
				}
				double rJB = Double.POSITIVE_INFINITY;
				double rRup = Double.POSITIVE_INFINITY;
				double rSeis = Double.POSITIVE_INFINITY;
				
				boolean dipping = (float)elem.getFocalMechanism().getDip() < 90f;
				
				double[] rJBs = new double[locs.length];
				for (int l=0; l<locs.length; l++) {
					SurfaceDistances vertDists = vertexDistsMap.get(locs[l]);
					if (vertDists == null) {
						double vertJB = LocationUtils.horzDistanceFast(siteLoc, locs[l]);
						double vertRup = Math.sqrt(vertJB*vertJB + locs[l].getDepth()*locs[l].getDepth());
						vertDists = new SurfaceDistances.Precomputed(siteLoc, vertRup, vertJB, Double.NaN, Double.NaN);
						vertexDistsMap.put(locs[l], vertDists);
					}
					rJBs[l] = vertDists.getDistanceJB();
					rJB = Double.min(rJB, vertDists.getDistanceJB());
					rRup = Double.min(rRup, vertDists.getDistanceRup());
					if (locs[l].depth >= GriddedSurfaceUtils.SEIS_DEPTH)
						rSeis = Double.min(rSeis, vertDists.getDistanceRup());
				}
				boolean footwall = true;
				if (dipping) {
					FocalMechanism mech = elem.getFocalMechanism();
					double dip = mech.getDip();
					double strike = mech.getStrike();
					
					// project up to the surface
					Location center = elem.getCenterLocation();
					
					
					// this code is to go down dip:
					/*
					double vDistance = upperSeismogenicDepth - traceLoc.getDepth();
					double hDistance = vDistance / Math.tan( aveDipRadians );
					//                dir = new LocationVector(vDistance, hDistance, aveDipDirection, 0);
					LocationVector dir = new LocationVector(aveDipDirection, hDistance, vDistance);
					return LocationUtils.location( traceLoc, dir );
					*/
					// project it up dip
					double vDistance = -center.getDepth();
					double hDistance = vDistance / Math.tan( Math.toRadians(dip) );
					double upDipDirection = strike - 90d;
					LocationVector dir = new LocationVector(upDipDirection, hDistance, vDistance);
					Location surfProjCenter = LocationUtils.location(center, dir);
					
					double fakeSurfLen = 10d; // doesn't really matter
					LocationVector alongStrike = new LocationVector(strike, 0.5*fakeSurfLen, 0d);
					Location l0 = LocationUtils.location(surfProjCenter, alongStrike);
					alongStrike.reverse();
					Location l1 = LocationUtils.location(surfProjCenter, alongStrike);
					
					// positive here means it to the right, aka in the down-dip direction, aka hanging wall
					double dist = LocationUtils.distanceToLineFast(l0, l1, siteLoc);
					footwall = dist < 0;
				}
				dists = new FootwallAwareSurfaceDistances(siteLoc, rRup, rJB, rSeis, footwall);
				elemDistsMap.put(elem, dists);
			}
			return dists;
		}
	}
	
	public static class FootwallAwareSurfaceDistances extends SurfaceDistances.Precomputed {
		private final boolean footwall;

		public FootwallAwareSurfaceDistances(Location siteLoc, double distanceRup, double distanceJB,
			double distanceSeis, boolean footwall) {
			super(siteLoc, distanceRup, distanceJB, distanceSeis, footwall ? -distanceJB : distanceJB);
			this.footwall = footwall;
		}
		
		public boolean isOnFootfall() {
			return footwall;
		}
	}
	
	public static enum DistanceType {
		R_JB("Rjb", "R<sub>JB</sub>") {
			@Override
			protected double calc(SurfaceDistances dists) {
				return dists.getDistanceJB();
			}
		},
		R_RUP("Rrup", "R<sub>Rup</sub>") {
			@Override
			protected double calc(SurfaceDistances dists) {
				return dists.getDistanceRup();
			}
		},
		R_SEIS("Rseis", "R<sub>Seis</sub>") {
			@Override
			protected double calc(SurfaceDistances dists) {
				return dists.getDistanceSeis();
			}
		};
		
		public final String displayName;
		public final String htmlName;

		private DistanceType(String name, String htmlName) {
			this.displayName = name;
			this.htmlName = htmlName;
		}
		
		protected abstract double calc(SurfaceDistances dists);
	}
	
	public static enum Scalar {
		MOMENT("Scalar Moment", "Scalar Moment", "Nm", "Nm") {
			@Override
			protected double calc(SimulatorElement element, double slip) {
				return FaultMomentCalc.getMoment(element.getArea(), slip);
			}
		},
		AREA("Area", "Area", "km^2", "km<sup>2</sup>") {
			@Override
			protected double calc(SimulatorElement element, double slip) {
				return element.getArea()*1e-6;
			}
		};
		
		public final String displayName;
		public final String htmlName;
		public final String units;
		public final String htmlUnits;

		private Scalar(String name, String htmlName, String units, String htmlUnits) {
			this.displayName = name;
			this.htmlName = htmlName;
			this.units = units;
			this.htmlUnits = htmlUnits;
		}
		
		protected abstract double calc(SimulatorElement element, double slip);
	}
	
	public static DiscretizedFunc calcDistScalarFunc(SimulatorEvent event, Location siteLoc,
			LocationElementDistanceCache siteLocDistCache, DistanceType distType, Scalar scalar) {
		List<SimulatorElement> elems = event.getAllElements();
		double[] slips = event.getAllElementSlips();
		
		DiscretizedFunc incrementalFunc = new ArbitrarilyDiscretizedFunc();
		
		for (int i=0; i<slips.length; i++) {
			SimulatorElement elem = elems.get(i);
			double scalarVal = scalar.calc(elem, slips[i]);
			double dist = distType.calc(siteLocDistCache.getDistances(elem));
			if (Double.isFinite(dist) && scalarVal > 0d) {
				int xInd = incrementalFunc.getXIndex(dist);
				if (xInd >= 0)
					incrementalFunc.set(xInd, scalarVal + incrementalFunc.getY(xInd));
				else
					incrementalFunc.set(dist, scalarVal);
			}
		}
		
		if (incrementalFunc.size() == 0) {
//			System.err.flush();
//			System.err.println("DEBUGGING a null dist-scalar func for event "+event.getID()+", location "+siteLoc
//					+", distType="+distType+", scalar="+scalar);
//			for (int i=0; i<slips.length; i++) {
//				SimulatorElement elem = elems.get(i);
//				double scalarVal = scalar.calc(elem, slips[i]);
//				double dist = distType.calc(siteLocDistCache.getDistances(elem));
//				System.err.println("\t"+i+". scalarVal="+scalarVal+", dist="+dist);
//			}
//			System.err.flush();
			return null;
		}
		
		double cumulativeVal = 0d;
		DiscretizedFunc cumulativeFunc = new ArbitrarilyDiscretizedFunc();
		for (int i=0; i<incrementalFunc.size(); i++) {
			cumulativeVal += incrementalFunc.getY(i);
			cumulativeFunc.set(incrementalFunc.getX(i), cumulativeVal);
		}
		
		return cumulativeFunc;
	}

}
