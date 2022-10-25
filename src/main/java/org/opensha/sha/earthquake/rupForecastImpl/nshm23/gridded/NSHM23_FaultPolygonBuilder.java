package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;

/**
 * This code is modified from UCERF3, handles special cases with nasty fault polygons
 * 
 * @author kevin
 *
 */
class NSHM23_FaultPolygonBuilder {
	
	public static Region buildPoly(FaultSection sect, RuptureSurface surf, boolean aseisReducesArea, double distance) {
		List<LocationList> locLists = areaToLocLists(buildPolyArea(sect, surf, aseisReducesArea, distance));
		
		return new Region(locLists.get(0), null);
	}
	
	public static Area buildPolyArea(FaultSection sect, RuptureSurface surf, boolean aseisReducesArea, double distance) {
		LocationList trace = sect.getFaultTrace();
		
		boolean needToUseUpper = false;
		if (sect.getAveDip() < 90d) {
			double upper = aseisReducesArea ? sect.getReducedAveUpperDepth() : sect.getOrigAveUpperDepth();
			for (Location loc : trace) {
				if (loc.getDepth() != upper) {
					needToUseUpper = true;
					break;
				}
			}
		}
		if (needToUseUpper)
			// use the upper edge instead
			trace = surf.getEvenlyDiscritizedUpperEdge();
		
		trace = removeDupes(trace);
		double dip = sect.getAveDip();
		double dipDir = sect.getDipDirection();
		
		double ddw = aseisReducesArea ? sect.getReducedDownDipWidth() : sect.getOrigDownDipWidth();
		double distPlusDip = distance + (ddw * Math.cos(dip * Math.PI / 180d));
		Area downDipArea = buildBufferPoly(trace, dipDir, distPlusDip);
		Area upDipArea = buildBufferPoly(trace, dipDir+180d, distance);
		Area area = merge(downDipArea, upDipArea);
		area = cleanBorder(area);
		if (!area.isSingular()) {
			area = removeNests(area);
		}
		Preconditions.checkState(area.isSingular(), "Failed to clean area, still singular for section %s. %s",
				sect.getSectionId(), sect.getSectionName());
		return area;
	}
	
	/*
	 * Builds a buffer polygon around a trace by taking each section of the trace
	 * and stretching it out in the dipDir direction into a polygon.
	 */
	public static Area buildBufferPoly(LocationList trace, double dipDir, double buf) {
		Preconditions.checkArgument(trace.size() > 1);
		Area buffer = null;
		for (int i = 1; i < trace.size(); i++) {
			Location a = trace.get(i - 1);
			Location b = trace.get(i);
			LocationList points = new LocationList();
			LocationVector v = new LocationVector(dipDir, buf, 0);

			points.add(a);
			points.add(LocationUtils.location(a, v));
			points.add(LocationUtils.location(b, v));
			points.add(b);
			v.reverse();
			points.add(LocationUtils.location(b, v));
			points.add(LocationUtils.location(a, v));
			buffer = merge(buffer, new Area(points.toPath()));
		}
		return buffer;
	}
	
	/* 
	 * Returns an area that is the result of merging the two supplied. Returns
	 * null if the merged Area is empty.
	 */
	private static Area merge(Area zone, Area dd) {
		Area area = new Area();
		if (zone != null) area.add(zone);
		if (dd != null) area.add(dd);
		return area.isEmpty() ? null : area;
	}
	
	/* Removes adjacent duplicate points from a locationList */
	private static List<LocationList> removeDupes(List<LocationList> locLists) {
		List<LocationList> newLocLists = new ArrayList<>();
		for (LocationList locs : locLists) {
			newLocLists.add(removeDupes(locs));
		}
		return newLocLists;
	}
	
	private static LocationList removeDupes(LocationList locs) {
		LocationList newLocs = new LocationList();
		for (Location loc : locs) {
			validateLoc(newLocs, loc);
		}
		return newLocs;
	}
	
	/*
	 * Intersections, minus', and unions of Location based Areas result in very
	 * very small (sub-micron scale) secondary polygons left over that cause
	 * Area.isSingular() to fail. These appear to always be at the beginning or
	 * end of the Area path and are coincident with some other point in the
	 * path. There are also identical repeated vertices at the junctions of
	 * geometric observations, which are harmless, but removed anyway.
	 * 
	 * The following method, when used to help build a path/LocationList from an
	 * Area, eliminates empty areas by scanning the growing list for locations
	 * that are similar. Only if no such Location exists is the supplied
	 * Location added to the list in place.
	 */
	private static void validateLoc(LocationList locs, Location loc) {
		for (Location p : locs) {
			if (areSimilar(p, loc)) return;
		}
		locs.add(loc);
	}
	
	/*
	 * Cleans polygon of empty sub-polys and duplicate vertices
	 */
	static Area cleanBorder(Area area) {
		// break apart poly into component paths; many qualify
		List<LocationList> locLists = areaToLocLists(area);
		// prune 'empty' polygons
		locLists = pruneEmpties(locLists);
		// clean remaining polygons of duplicate vertices
		locLists = removeDupes(locLists);
		Area areaOut = new Area();
		for (LocationList areaLocs : locLists) {
			areaOut.add(new Area(areaLocs.toPath()));
		}
		return areaOut;
	}
	
	/* Removes mostly empty polygons from a list of LocationLists */
	private static List<LocationList> pruneEmpties(List<LocationList> locLists) {
		List<LocationList> newLocLists = new ArrayList<>();
		for (LocationList locs : locLists) {
			if (isEmptyPoly(locs)) continue;
			newLocLists.add(locs);
		}
		return newLocLists;
	}
	
	/*
	 * Iterates over the path defining an Area and returns a List of
	 * LocationLists. If Area is singular, returned list will only have one
	 * LocationList
	 */
	static List<LocationList> areaToLocLists(Area area) {
		// break apart poly into component paths; many qualify
		List<LocationList> locLists = new ArrayList<>();
		LocationList locs = null;
		// placeholder vertex for path iteration
		double[] vertex = new double[6];
		PathIterator pi = area.getPathIterator(null);
		while (!pi.isDone()) {
			int type = pi.currentSegment(vertex);
			double lon = vertex[0];
			double lat = vertex[1];
			if (type == PathIterator.SEG_MOVETO) {
				locs = new LocationList();
				locLists.add(locs);
				locs.add(new Location(lat, lon));
			} else if (type == PathIterator.SEG_LINETO) {
				locs.add(new Location(lat, lon));
			}
			// skip any closing segments as LocationList.toPath() will
			// close polygons
			pi.next();
		}
		return locLists;
	}
	
	/* Tests whether all points in a LocationList are the same */
	private static boolean isEmptyPoly(LocationList locs) {
		Location start = locs.get(0);
		for (Location loc : locs) {
			if (areSimilar(start, loc)) continue;
			return false;
		}
		return true;
	}
	
	/* Location comparison tolerance and shift for poly merging */
	private static final double TOL = 0.000000001;
	
	/*
	 * Private Location comparer with higher tolerance than that in
	 * LocaitonUtils
	 */
	private static boolean areSimilar(Location p1, Location p2) {
		if (!Precision.equals(p1.getLatitude(), p2.getLatitude(), TOL)) {
			return false;
		}
		if (!Precision.equals(p1.getLongitude(), p2.getLongitude(), TOL)) {
			return false;
		}
		if (!Precision.equals(p1.getDepth(), p2.getDepth(), TOL)) {
			return false;
		}
		return true;
	}
	
	/* Removes nested polygons */
	private static Area removeNests(Area area) {
		if (area == null) return null;
		if (area.isSingular()) return area;
		List<LocationList> locLists = areaToLocLists(area);
		Preconditions.checkArgument(locLists.size() > 1);
		Area a = new Area();
		for (LocationList locs : locLists) {
			Area toAdd = new Area(locs.toPath());
			a.add(toAdd);
		}
		a = cleanBorder(a);
		return a;
	}

}
