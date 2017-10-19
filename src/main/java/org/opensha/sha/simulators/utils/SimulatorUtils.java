package org.opensha.sha.simulators.utils;

import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.util.FaultUtils;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.Vertex;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class SimulatorUtils {
	
	public final static double SECONDS_PER_YEAR = 365*24*60*60;
	
	public static double getSimulationDuration(List<? extends SimulatorEvent> events) {
		SimulatorEvent firstEvent = events.get(0);
		SimulatorEvent lastEvent = events.get(events.size()-1);
		double startTime = firstEvent.getTime();
		double endTime = lastEvent.getTime()+lastEvent.getDuration(); // TODO worth adjusting for duration?
		return (endTime - startTime);
	}
	
	/**
	 * 
	 * @return simulation duration in years
	 */
	public static double getSimulationDurationYears(List<? extends SimulatorEvent> events) {
		return getSimulationDuration(events)/SECONDS_PER_YEAR;
	}
	
	/**
	 * Estimates rupture length as the sum of the maximum horizontal distance between elements in each record
	 * @param event
	 * @return length in km
	 */
	public static double estimateRuptureLength(SimulatorEvent event) {
		double totLen = 0d;
		for (EventRecord rec : event) {
			double maxLen = 0d;
			List<SimulatorElement> elems = rec.getElements();
			
			if (elems.size() == 1)
				maxLen = maxHorzDist(elems.get(0), elems.get(0));
			for (int i=0; i<elems.size(); i++)
				for (int j=i+1; j<elems.size(); j++)
					maxLen = Math.max(maxLen, maxHorzDist(elems.get(i), elems.get(j)));
			totLen += maxLen;
		}
		return totLen;
	}
	
	private static double maxHorzDist(SimulatorElement e1, SimulatorElement e2) {
		double maxDist = 0d;
		for (Location p1 : e1.getVertices())
			for (Location p2 : e2.getVertices())
				maxDist = Math.max(maxDist, LocationUtils.horzDistanceFast(p1, p2));
		return maxDist;
	}
	
	public static Location[] estimateVertexDAS(SimulatorEvent event) {
		// find the 2 elements that are farthest from each other

		// start by finding the farthest pair of EventRecord's
		EventRecord leftRec = null;
		EventRecord rightRec = null;
		if (event.size() == 1) {
			leftRec = event.get(0);
			rightRec = leftRec;
		} else {
			double maxDist = 0d;
			Location[] aveLocs = new Location[event.size()];
			for (int i=0; i<event.size(); i++)
				aveLocs[i] = aveLoc(event.get(i));
			for (int i=0; i<event.size(); i++) {
				for (int j=i+1; j<event.size(); j++) {
					double dist = LocationUtils.horzDistanceFast(aveLocs[i], aveLocs[j]);
					if (dist > maxDist) {
						leftRec = event.get(i);
						rightRec = event.get(j);
						maxDist = dist;
					}
				}
			}
		}

		// now find the pair of elements on those records which are farthest
		Location leftLoc = null;
		Location rightLoc = null;
		double maxDist = 0d;
		for (SimulatorElement e1 : leftRec.getElements()) {
			for (SimulatorElement e2 : rightRec.getElements()) {
				for (Location p1 : e1.getVertices()) {
					for (Location p2 : e2.getVertices()) {
						double dist = LocationUtils.horzDistance(p1, p2);
						if (dist > maxDist) {
							maxDist = dist;
							leftLoc = p1;
							rightLoc = p2;
						}
					}
				}
			}
		}
		
		return estimateVertexDAS(event, leftLoc, rightLoc);
	}
	
	public static Location[] estimateVertexDAS(SimulatorEvent event, Location leftLoc, Location rightLoc) {
		List<SimulatorElement> elems = event.getAllElements();
		
		// now try to make it conform with Aki & Richards
		// find the average strike of non-vertical elements
		List<Double> strikesDipping = new ArrayList<>();
		for (SimulatorElement e : elems) {
			FocalMechanism mech = e.getFocalMechanism();
			if (mech.getDip() < 90)
				strikesDipping.add(mech.getStrike());
		}
		
		double curStrike = LocationUtils.azimuth(leftLoc, rightLoc);
		
		if (!strikesDipping.isEmpty()) {
			double targetStrike = FaultUtils.getAngleAverage(strikesDipping);
			double diff = Math.abs(curStrike - targetStrike);
			diff = Math.min(diff, Math.abs((curStrike+360) - targetStrike));
			diff = Math.min(diff, Math.abs(curStrike - (targetStrike+360)));
			if (diff > 90 && diff < 270) {
				System.out.println("Swapping direction");
				Location temp = leftLoc;
				leftLoc = rightLoc;
				rightLoc = temp;
			}
		}
		
		double minDAS = Double.POSITIVE_INFINITY;
		
		for (SimulatorElement e : elems) {
			for (Vertex v : e.getVertices()) {
				double das = estimateDAS(leftLoc, rightLoc, v);
				minDAS = Math.min(minDAS, das);
				v.setDAS(das);
			}
		}
		for (SimulatorElement e : elems)
			for (Vertex v : e.getVertices())
				v.setDAS(v.getDAS()-minDAS);
		
		return new Location[] {leftLoc, rightLoc};
	}
	
	public static double estimateDAS(Location firstLoc, Location lastLoc, Location loc) {
		LocationVector fromLeft = LocationUtils.vector(firstLoc, loc);
		double distToLine = LocationUtils.distanceToLine(firstLoc, lastLoc, loc);
		double sumSq = distToLine*distToLine + fromLeft.getHorzDistance()*fromLeft.getHorzDistance();
		double das = Math.sqrt(sumSq);
		Preconditions.checkState(Doubles.isFinite(das), "bad DAS calc: %s = sqrt(%s^2 + %s^2) = sqrt(%s)",
				das, distToLine, fromLeft.getHorzDistance(), sumSq);
		return das;
	}
	
	private static Location aveLoc(EventRecord rec) {
		double aveLat = 0d;
		double aveLon = 0d;
		double aveDep = 0d;
		List<SimulatorElement> elems = rec.getElements();
		for (SimulatorElement elem : elems) {
			Location loc = elem.getCenterLocation();
			aveLat += loc.getLatitude();
			aveLon += loc.getLongitude();
			aveDep += loc.getDepth();
		}
		return new Location(aveLat/elems.size(), aveLon/elems.size(), aveDep/elems.size());
	}

}
