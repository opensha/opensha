package org.opensha.sha.simulators.iden;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.IDPairing;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.SimulatorEvent;

import com.google.common.base.Preconditions;

public class LinearRuptureIden extends AbstractRuptureIdentifier {
	
	private double maxDeviation;
	
	private Map<IDPairing, Double> horzDistanceCache;

	private boolean fractional;
	
	public LinearRuptureIden(double maxDeviation) {
		this(maxDeviation, false);
	}

	public LinearRuptureIden(double maxDeviation, boolean fractional) {
		this(maxDeviation, fractional, new HashMap<>());
	}

	public LinearRuptureIden(double maxDeviation, boolean fractional, Map<IDPairing, Double> horzDistanceCache) {
		this.maxDeviation = maxDeviation;
		this.fractional = fractional;
		this.horzDistanceCache = horzDistanceCache;
	}
	
	public static boolean D = false;
	private static final boolean CARTESIAN = true;

	@Override
	public boolean isMatch(SimulatorEvent event) {
		ArrayList<SimulatorElement> elems = event.getAllElements();
		if (elems.size() < 3)
			return true;
		
//		final boolean debug = event.getID() == 421261;
		final boolean debug = false || D;
		if (debug) System.out.println("Debugging event "+event.getID()+" with "+elems.size()+" elems");
		
		// find the 2 elements which are furthest away from each other
		SimulatorElement furthest1 = null;
		SimulatorElement furthest2 = null;
		double maxDist = 0;
		
		HashSet<SimulatorElement> skipElems = new HashSet<>();
		
		for (int i=0; i<elems.size(); i++) {
			SimulatorElement elem1 = elems.get(i);
			if (skipElems.contains(elem1))
				continue;
			for (int j=i+1; j<elems.size(); j++) {
				SimulatorElement elem2 = elems.get(j);
				if (skipElems.contains(elem2))
					continue;
				IDPairing pair = elem1.getID() > elem2.getID() ? new IDPairing(elem2.getID(), elem1.getID()) : new IDPairing(elem1.getID(), elem2.getID());
				Double dist = horzDistanceCache.get(pair);
				if (dist == null) {
					Location l1 = elems.get(i).getCenterLocation();
					Location l2 = elems.get(j).getCenterLocation();
					if (CARTESIAN) {
						// cartesian distance is fine here, just need to find furthest
						double dLat = l1.getLatitude() - l2.getLatitude();
						double dLon = l1.getLongitude() - l2.getLongitude();
						dist = Math.sqrt(dLat*dLat + dLon*dLon);
					} else {
						dist = LocationUtils.horzDistanceFast(l1, l2);
					}
					
					horzDistanceCache.put(pair, dist);
				}
				if (dist.floatValue() == 0f)
					// horizontally identical to another element, don't bother check it later
					skipElems.add(elem2);
				if (dist > maxDist) {
					furthest1 = elems.get(i);
					furthest2 = elems.get(j);
					maxDist = dist;
				}
			}
		}
		
		if (CARTESIAN)
			// convert to km
			maxDist = LocationUtils.horzDistanceFast(furthest1.getCenterLocation(), furthest2.getCenterLocation());
		if (debug) System.out.println("\tMax dist: "+maxDist);
		if (debug) System.out.println("\tElem 1 loc: "+furthest1.getCenterLocation());
		if (debug) System.out.println("\tElem 2 loc: "+furthest2.getCenterLocation());
		
		Location loc1 = furthest1.getCenterLocation();
		// strip out depth
		loc1 = new Location(loc1.getLatitude(), loc1.getLongitude());
		Location loc2 = furthest2.getCenterLocation();
		// strip out depth
		loc2 = new Location(loc2.getLatitude(), loc2.getLongitude());
		
		double maxDeviation;
		if (fractional)
			maxDeviation = maxDist*this.maxDeviation;
		else
			maxDeviation = this.maxDeviation;
		
		// create a conservatively sized rectangular region that, if it contains a given element, that element
		// is surely no more than maxDeviation away
//		double az = LocationUtils.azimuth(loc1, loc2)+90d;
//		LocationList border = new LocationList();
//		border.add(LocationUtils.location(loc1, new LocationVector(az, -0.6*maxDeviation, 0d)));
//		border.add(LocationUtils.location(loc1, new LocationVector(az, 0.6*maxDeviation, 0d)));
//		border.add(LocationUtils.location(loc2, new LocationVector(az, 0.6*maxDeviation, 0d)));
//		border.add(LocationUtils.location(loc2, new LocationVector(az, -0.6*maxDeviation, 0d)));
//		Region testReg = new Region(border, BorderType.GREAT_CIRCLE);
		
		for (SimulatorElement elem : elems) {
			if (elem == furthest1 || elem == furthest2 || skipElems.contains(elem))
				continue;
			Location elemLoc = elem.getCenterLocation();
//			if (testReg.contains(elemLoc))
//				continue;
			// strip out depth
			elemLoc = new Location(elemLoc.getLatitude(), elemLoc.getLongitude());
			double dist = Math.abs(LocationUtils.distanceToLineFast(loc1, loc2, elemLoc));
			Preconditions.checkState(Double.isFinite(dist), "Bad dist: %s\n\tLoc1: %s\n\tLoc2: %s\n\tTest Loc: %s",
					dist, loc1, loc2, elemLoc);
			if (debug) System.out.println("\t\tElem at "+elem.getCenterLocation()+": "+dist);
			if (dist > maxDeviation)
				return false;
		}
		
		return true;
	}

	@Override
	public String getName() {
		return "Linear Rupture Identifier";
	}

}
