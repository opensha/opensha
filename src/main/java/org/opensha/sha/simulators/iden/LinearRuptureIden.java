package org.opensha.sha.simulators.iden;

import java.util.ArrayList;
import java.util.HashMap;
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

	public LinearRuptureIden(double maxDeviation) {
		this.maxDeviation = maxDeviation;
		
		horzDistanceCache = new HashMap<>();
	}

	@Override
	public boolean isMatch(SimulatorEvent event) {
		ArrayList<SimulatorElement> elems = event.getAllElements();
		if (elems.size() < 3)
			return true;
		
//		final boolean debug = event.getID() == 421261;
		final boolean debug = false;
		if (debug) System.out.println("Debugging event "+event.getID()+" with "+elems.size()+" elems");
		
		// find the 2 elements which are furthest away from each other
		SimulatorElement furthest1 = null;
		SimulatorElement furthest2 = null;
		double maxDist = 0;
		
		for (int i=0; i<elems.size(); i++) {
			SimulatorElement elem1 = elems.get(i);
			for (int j=i+1; j<elems.size(); j++) {
				SimulatorElement elem2 = elems.get(j);
				IDPairing pair = elem1.getID() > elem2.getID() ? new IDPairing(elem2.getID(), elem1.getID()) : new IDPairing(elem1.getID(), elem2.getID());
				Double dist = horzDistanceCache.get(pair);
				if (dist == null) {
					dist = LocationUtils.horzDistanceFast(elems.get(i).getCenterLocation(), elems.get(j).getCenterLocation());
					horzDistanceCache.put(pair, dist);
				}
				if (dist > maxDist) {
					furthest1 = elems.get(i);
					furthest2 = elems.get(j);
					maxDist = dist;
				}
			}
		}
		
		if (debug) System.out.println("\tMax dist: "+maxDist);
		if (debug) System.out.println("\tElem 1 loc: "+furthest1.getCenterLocation());
		if (debug) System.out.println("\tElem 2 loc: "+furthest2.getCenterLocation());
		
		Location loc1 = furthest1.getCenterLocation();
		// strip out depth
		loc1 = new Location(loc1.getLatitude(), loc1.getLongitude());
		Location loc2 = furthest2.getCenterLocation();
		// strip out depth
		loc2 = new Location(loc2.getLatitude(), loc2.getLongitude());
		
		for (SimulatorElement elem : elems) {
			if (elem == furthest1 || elem == furthest2)
				continue;
			Location elemLoc = elem.getCenterLocation();
			elemLoc = new Location(elemLoc.getLatitude(), elemLoc.getLongitude());
			double dist = Math.abs(LocationUtils.distanceToLineFast(loc1, loc2, elemLoc));
			Preconditions.checkState(Double.isFinite(dist), "Bad dist: %s\n\tLoc1: %s\n\tLoc2: %s\n\tTest Loc: %s", dist, loc1, loc2, elemLoc);
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
