package scratch.UCERF3.griddedSeismicity;

import static com.google.common.base.Preconditions.checkArgument;

import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.DataUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.enumTreeBranches.FaultModels;

/*
 * Accessory class to FaultPolyMgr that creates and manages the polygons for a
 * supplied List<FaultSectionPrefData>. The list may be derived from a
 * FaultModel (whole faults) in which case a subsection length must be provided
 * for subdividing the models into fault-sections. Or the list may be derived
 * from a FaultSystemRuptureSet, in which case thefault-sections are already
 * defined but whose getZonePolygon() method returns the parent fault polygon.
 * 
 * This is a multistep process that involves:
 * 
 * 
 * Polygon problems: - intersect, union etc... can yield micron-scale residual
 * polys - some sections do not have polys - some polys do not span all
 * subsections
 * 
 * @author Peter Powers
 * 
 * @version $Id:$
 */
class SectionPolygons {

	private static boolean log = false;
	
	private static final double MAX_BUF_DIP = 50;
	
	// map of subsections to polygons
	private Map<Integer, Area> polyMap;
	
	// parent reference maps
	private Map<Integer, String> parentNameMap;
	private Map<Integer, Area> parentAreaMap;
	private Map<Integer, List<FaultSectionPrefData>> parentSubSectionMap;
	
	private SectionPolygons() {};
	
	/*
	 * Creates a SectionPolygon instance. If len == null, the supplied list is
	 * assumed to be derived from a FaultSystemRuptureSet, otherwise it is
	 * treated as being derived from a FaultModel. If buf != null and is
	 * 0 < buf < 20, then the parent fault polygons are expanded to include a 
	 * buffer zone of size=buf on either side of the fault trace. This ends up
	 * creating polygons for faults where non existed (e.g. seismicity trends,
	 * stepovers/connectors).
	 */
	static SectionPolygons create(List<FaultSectionPrefData> srcList, Double buf, Double len) {
		SectionPolygons fp = new SectionPolygons();
		fp.parentAreaMap = Maps.newHashMap();
		fp.parentSubSectionMap = Maps.newHashMap();
		fp.parentNameMap = Maps.newHashMap();
		if (len == null) {
			initFSRS(fp, srcList);
		} else {
			initFM(fp, srcList, len);
		}
		// apply buffer
		if (buf != null && buf != 0.0) {
			checkArgument(buf > 0 && buf <=20);
			fp.applyBuffer(buf);
		}
		fp.build();
		return fp;
	}
	
	private static void initFSRS(SectionPolygons fp,
			List<FaultSectionPrefData> sects) {
		for (FaultSectionPrefData sect : sects) {
			int pID = sect.getParentSectionId();
			if (!fp.parentAreaMap.containsKey(pID)) {
				Region r = sect.getZonePolygon();
				Area a = (r != null) ? r.getShape() : null;
				fp.parentAreaMap.put(pID, a);
			}
			List<FaultSectionPrefData> fSects = fp.parentSubSectionMap.get(pID);
			if (fSects == null) {
				fSects = Lists.newArrayList();
				fp.parentSubSectionMap.put(pID, fSects);
			}
			fSects.add(sect);
			if (!fp.parentNameMap.containsKey(pID)) {
				fp.parentNameMap.put(pID, sect.getParentSectionName());
			}
		}
	}
	
	private static void initFM(SectionPolygons fp,
			List<FaultSectionPrefData> faults, double len) {
		for (FaultSectionPrefData fault : faults) {
			int id = fault.getSectionId();
			Region r = fault.getZonePolygon(); // may be null
			Area a = (r != null) ? r.getShape() : null;
			fp.parentAreaMap.put(id, a);
			fp.parentSubSectionMap.put(id, fault.getSubSectionsList(len));
			fp.parentNameMap.put(id, fault.getName());
		}
	}
	
	/* Returns the polygon for the supplied id. */
	Area get(int id) {
		return polyMap.get(id);
	}
	
	/* Returns an iteration over all section polygons */
	Iterable<Area> polys() {
		return polyMap.values();
	}
	
	/* Returns the reference indices of all polygons. */
	Set<Integer> indices() {
		return polyMap.keySet();
	}

	/*
	 * This is a little KLUDGY . We have a reference to the parent polygon
	 * for both types of inputs, but we only have the parent trace in the case
	 * of a FualtModel input. Rather than give each different treatment, we
	 * rejoin the traces of each section, eliminate duplicates , and then build
	 * the bufer polygon. This only works because we know that we get the fault
	 * sections in order from one end of a parent trace to the other.
	 * 
	 * It would be better to get the original FaultModel trace. This approach
	 * does recreate the original trace but with additional points at section
	 * boundaries so the polygons are more articulated than necessary.
	 */
	private void applyBuffer(double buf) {
		for (Integer pID : parentSubSectionMap.keySet()) {
			List<FaultSectionPrefData> sects = parentSubSectionMap.get(pID);
			LocationList trace = new LocationList();
			for (FaultSectionPrefData sect : sects) {
				trace.addAll(sect.getFaultTrace());
			}
			trace = removeDupes(trace);
			double dip = sects.get(0).getAveDip();
			double dipDir = sects.get(0).getDipDirection();
			double scale = (dip - MAX_BUF_DIP) / (90 - MAX_BUF_DIP);
			if (scale <= 0) continue;

			// merge buf and fault polys if buf > 0 (i.e. dip > 45)
			double scaledBuf = scale * buf;
			LocationList buffPoly = buildBufferPoly(trace, dipDir, scaledBuf);
			Area pArea = parentAreaMap.get(pID);
			pArea = merge(pArea, new Area(buffPoly.toPath()));
			pArea = cleanBorder(pArea);
			if (!pArea.isSingular()) {
				pArea = removeNests(pArea);
			}
			parentAreaMap.put(pID, pArea);
			
			if (!pArea.isSingular()) {
				System.out.println("    non-singular " + pID + " " +
					parentNameMap.get(pID));
			}
		}
	}
	
	/*
	 * Builds a buffer polygon around a trace by creating lists of points
	 * offset on either side of the trace and then reversing one lists and
	 * then merging both.
	 */
	private static LocationList buildBufferPoly(LocationList trace,
			double dipDir, double buf) {
		checkArgument(trace.size() > 1);
		LocationList one = new LocationList();
		LocationList two = new LocationList();
		Location bufPt;
		for (Location p : trace) {
			LocationVector v = new LocationVector(dipDir, buf, 0);
			bufPt = LocationUtils.location(p, v);
			one.add(bufPt);
			v.reverse();
			bufPt = LocationUtils.location(p, v);
			two.add(bufPt);
		}
		two.reverse();
		one.addAll(two);
		return one;
	}
	
	
	private void build() {
		polyMap = Maps.newTreeMap();
		int idx = -1;
		for (Integer pID : parentAreaMap.keySet()) {
			idx++;
			
			StringBuilder sb = null;
			if (log) {
				sb = new StringBuilder();
				sb.append(Strings.padEnd(Integer.toString(idx), 5, ' '));
				sb.append(Strings.padEnd(Integer.toString(pID), 5, ' '));
				sb.append(Strings.padEnd(parentNameMap.get(pID), 48, ' '));
			}

			if (parentAreaMap.get(pID) == null) {
				if (log) System.out.println(sb.append("null-poly"));
				initNullPolys(pID);
			} else {
				if (log) System.out.println(sb);
				initPolys(pID);
			}
		}
		
		cleanPolys();
		mergeDownDip();

		for (Integer id : polyMap.keySet()) {
			Area poly = polyMap.get(id);
			String mssg = (poly == null) ? "null" : (!poly.isSingular())
				? "non-singular" : "ok";
			if (poly != null && !poly.isSingular()) {
				System.out.println(Strings.padEnd(id.toString(), 10, ' ') + mssg);
				List<LocationList> locLists = areaToLocLists(poly);
				for (LocationList locs : locLists) {
					System.out.println(locs);
				}
			}
		}
	}
	 
	
	/* Populate subsections with null parent poly */
	private void initNullPolys(int pID) {
		List<FaultSectionPrefData> subSecs = parentSubSectionMap.get(pID);
		for (FaultSectionPrefData sec : subSecs) {
			int id = sec.getSectionId();
			polyMap.put(id, null);
		}
	}
	
	/*
	 * Builds the subsection:poly Map by creating subsection envelopes used
	 * to slice up the parent fault polygon. This puts all manner of non-
	 * singular and empty Areas into the map that will be filtered out after
	 * this method returns.
	 */
	private void initPolys(int pID) {
		// loop subsections creating polys and modding parent poly
		Area fPoly = parentAreaMap.get(pID); // parent poly
		List<FaultSectionPrefData> subSecs = parentSubSectionMap.get(pID);
		for (int i=0; i<subSecs.size(); i++) {
			FaultSectionPrefData ss1 = subSecs.get(i);
			int id = ss1.getSectionId();
			
			// if only 1 segment
			if (subSecs.size() == 1) {
				polyMap.put(id, fPoly);
				break;
			}

			// if on last segment, use remaining fPoly and quit
			if (i == subSecs.size() - 1) {
				if (fPoly.isSingular()) {
					polyMap.put(id, fPoly);
				} else {
					// multi part polys need to have some attributed back
					// to the previous section
					List<LocationList> locLists = areaToLocLists(fPoly);
					for (LocationList locs : locLists) {
						Area polyPart = new Area(locs.toPath());
						FaultTrace trace = ss1.getFaultTrace();
						if (intersects(trace, polyPart)) {
							// this is the poly associated with the fault trace
							polyMap.put(id, polyPart);
						} else {
							Area leftover = polyPart;
							int sectionID = subSecs.get(i-1).getSectionId();
							Area prev = polyMap.get(sectionID);
							prev.add(leftover);
							prev = cleanBorder(prev);
							if (!prev.isSingular()) prev = hardMerge(prev);
							if (prev == null) System.out.println(
								"merge problem last segment");
							polyMap.put(sectionID, prev);
						}
					}
				}
				break;
			}
			
			FaultSectionPrefData ss2 = subSecs.get(i + 1);
			LocationList envelope = createSubSecEnvelope(ss1, ss2);
			
			// intersect with copy of parent
			Area envPoly = new Area(envelope.toPath());
			Area subPoly = (Area) fPoly.clone();
			subPoly.intersect(envPoly);
			
			// keep moving if nothing happened
			if (subPoly.isEmpty()) {
				polyMap.put(id, null);
				continue;
			}
			
			// get rid of dead weight
			subPoly = cleanBorder(subPoly);
			
			// determine if there is a secondary poly not associated with
			// the fault trace that must be added back to parent
			Area leftover = null;
			if (subPoly.isSingular()) {
				polyMap.put(id, subPoly);
			} else {
				List<LocationList> locLists = areaToLocLists(subPoly);
				for (LocationList locs : locLists) {
					Area polyPart = new Area(locs.toPath());
					FaultTrace trace = ss1.getFaultTrace();
					if (intersects(trace, polyPart)) {
						// this is the poly associated with the fault trace
						polyMap.put(id, polyPart);
					} else {
						leftover = polyPart;
					}
				}
			}
			
			// trim parent poly for next slice
			fPoly.subtract(envPoly);
			fPoly = cleanBorder(fPoly);
			
			// try adding back into fault poly
			if (leftover != null) {
				Area fCopy = (Area) fPoly.clone();
				fCopy.add(leftover);
				fCopy = cleanBorder(fCopy);
				if (!fCopy.isSingular()) {
					// try hard merge
					fCopy = hardMerge(fCopy);
					// hard merge failed, go to previous section
					if (fCopy == null) {
						int sectionID = subSecs.get(i-1).getSectionId();
						Area prev = polyMap.get(sectionID);
						prev.add(leftover);
						prev = cleanBorder(prev);
						if (!prev.isSingular()) prev = hardMerge(prev);
						if (prev == null) System.out.println("merge problem");
						polyMap.put(sectionID, prev);
					} else {
						fPoly = fCopy;
					}
				} else {
					fPoly = fCopy;
				}
			}
		}
	}

	/* Envelope buffer around fault sub sections */
	private static final double BUF = 100;

	/*
	 * Method creates an envelope extending BUF km on either side of and along
	 * the first supplied sub section. One border of the envelope is the
	 * bisector if the two supplied subsections.
	 */
	private static LocationList createSubSecEnvelope(
			FaultSectionPrefData sec1, FaultSectionPrefData sec2) {

		FaultTrace t1 = sec1.getFaultTrace();
		FaultTrace t2 = sec2.getFaultTrace();

		Location p1 = t1.get(t1.size() - 2);
		Location p2 = t1.get(t1.size() - 1);
		// check that last and first points of adjacent subs are coincident
		Preconditions.checkState(p2.equals(t2.get(0)));
		
		LocationVector vBackAz = LocationUtils.vector(p2, p1);
		vBackAz.setHorzDistance(BUF);
		LocationVector vBisect = new LocationVector();
		vBisect.setAzimuth(sec1.getDipDirection());
		vBisect.setHorzDistance(BUF);
		
		// assemble location list that is a U shape starting on one side of
		// fault and passing through bisector and on to other side
		LocationList locs = new LocationList();

		// starting at p2, move to one side of fault
		Location util = LocationUtils.location(p2, vBisect);
		locs.add(util);
		// move back along fault inserting the first point in poly
		// previous point is advanced to second position
		locs.add(0, LocationUtils.location(util, vBackAz));
		// add subsection boundary point
		locs.add(p2);
		// move to other side of fault
		vBisect.reverse();
		util = LocationUtils.location(p2, vBisect);
		locs.add(util);
		// move back along fault
		locs.add(LocationUtils.location(util, vBackAz));
		
		return locs;
	}
	
	/* Sets empty areas to null and cleans borders of others. */
	private void cleanPolys() {
		for (Integer id : polyMap.keySet()) {
			Area poly = polyMap.get(id);
			if (poly == null) continue;
			if (poly.isEmpty()) {
				polyMap.put(id, null);
			} else {
				polyMap.put(id, cleanBorder(poly));
			}
		}
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
		List<LocationList> newLocLists = Lists.newArrayList();
		for (LocationList locs : locLists) {
			if (isEmptyPoly(locs)) continue;
			newLocLists.add(locs);
		}
		return newLocLists;
	}
	
	/* Removes adjacent duplicate points from a locationList */
	private static List<LocationList> removeDupes(List<LocationList> locLists) {
		List<LocationList> newLocLists = Lists.newArrayList();
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
	
	/* Tests whether all points in a LocationList are the same */
	private static boolean isEmptyPoly(LocationList locs) {
		Location start = locs.get(0);
		for (Location loc : locs) {
			if (areSimilar(start, loc)) continue;
			return false;
		}
		return true;
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
	
	/* Attempts to merge non-singular area */
	private static Area hardMerge(Area area) {
		List<LocationList> locLists = areaToLocLists(area);
		checkArgument(locLists.size() == 2);
		Area a1 = new Area(locLists.get(0).toPath());
		Area a2 = new Area(locLists.get(1).toPath());
		return shiftMerge(a1, a2);
	}
	
	/*
	 * Method does a couple intersection tests by shifting a2 around searching
	 * for a singular result
	 */
	private static Area shiftMerge(Area a1, Area a2) {
		checkArgument(a1.isSingular());
		checkArgument(!a1.isEmpty());
		checkArgument(a2.isSingular());
		checkArgument(!a2.isEmpty());
		LocationList locsToShift = areaToLocLists(a2).get(0);
		LocationList shiftedLocs = null;
		Area merged = (Area) a1.clone();
		// east shift
		shiftedLocs = shiftEW(locsToShift, TOL);
		merged.add(new Area(shiftedLocs.toPath()));
		if (merged.isSingular()) {
			return merged;
		}
		// south shift
		shiftedLocs = shiftNS(locsToShift, -TOL);
		merged.add(new Area(shiftedLocs.toPath()));
		if (merged.isSingular()) {
			return merged;
		}
		// west shift
		shiftedLocs = shiftEW(locsToShift, -TOL);
		merged.add(new Area(shiftedLocs.toPath()));
		if (merged.isSingular()) {
			return merged;
		}
		// north shift
		shiftedLocs = shiftNS(locsToShift, TOL);
		merged.add(new Area(shiftedLocs.toPath()));
		if (merged.isSingular()) {
			return merged;
		}
		return null;
	}
	
	private static LocationList shiftEW(LocationList locs, double shift) {
		LocationList locsOut = new LocationList();
		for (Location loc : locs) {
			Location shiftedLoc = new Location(loc.getLatitude(),
				loc.getLongitude() + shift);
			locsOut.add(shiftedLoc);
		}
		return locsOut;
	}


	private static LocationList shiftNS(LocationList locs, double shift) {
		LocationList locsOut = new LocationList();
		for (Location loc : locs) {
			Location shiftedLoc = new Location(loc.getLatitude() + shift,
				loc.getLongitude());
			locsOut.add(shiftedLoc);
		}
		return locsOut;
	}

	/* Tests whether any part of a fault trace is inside a polygon */
	private static boolean intersects(FaultTrace trace, Area poly) {
		for (Location loc : trace) {
			if (poly.contains(loc.getLongitude(), loc.getLatitude())) {
				return true;
			}
		}
		return false;
	}
		

	
	
	/* 
	 * Merges the downdip subsection representations with the now cleaned
	 * zone polygons
	 */
	private void mergeDownDip() {
		for (Integer pID : parentSubSectionMap.keySet()) {
			List<FaultSectionPrefData> subSecs = parentSubSectionMap.get(pID);
			int numSubSecs = subSecs.size();
			for (int i=0; i<numSubSecs; i++) {
				FaultSectionPrefData subSec = subSecs.get(i);
				int id = subSec.getSectionId();
				Area zone = polyMap.get(id);
				Area dd = createDownDipPoly(subSec);
				Area merged = merge(zone, dd);
				
				// currently bugs in some fault sections (polygons not following
				// fault traces) result in holes; remove using contains
				merged = removeNests(merged);
				polyMap.put(id, merged);
			}
		}
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
	
	/* Creates the down dip polygon from the border of a fault surface */
	private static Area createDownDipPoly(FaultSectionPrefData f) {
		RuptureSurface surf = f.getStirlingGriddedSurface(1, false, false);
		LocationList perimeter = surf.getPerimeter();
		return new Area(perimeter.toPath());
	}
	
	/* Removes nested polygons */
	private static Area removeNests(Area area) {
		if (area == null) return null;
		if (area.isSingular()) return area;
		List<LocationList> locLists = areaToLocLists(area);
		checkArgument(locLists.size() > 1);
		Area a = new Area();
		for (LocationList locs : locLists) {
			Area toAdd = new Area(locs.toPath());
			a.add(toAdd);
		}
		a = cleanBorder(a);
		return a;
	}
	
	/*
	 * Iterates over the path defining an Area and returns a List of
	 * LocationLists. If Area is singular, returned list will only have one
	 * LocationList
	 */
	static List<LocationList> areaToLocLists(Area area) {
		// break apart poly into component paths; many qualify
		List<LocationList> locLists = Lists.newArrayList();
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
	
	
	/**
	 * Returns a flat-earth estimate of the area of this region in
	 * km<sup>2</sup>. Method uses the center of this {@code Region}'s bounding
	 * polygon as the origin of an orthogonal coordinate system. This method is
	 * not appropriate for use with very large {@code Region}s where the
	 * curvature of the earth is more significant.
	 * 
	 * TODO should probably use centroid of polygon
	 * 
	 * Assumes aupplied area has already been cleaned of strays etc...
	 * 
	 * @return the area of this region in km<sup>2</sup>
	 */
	static double getExtent(Area area) {
		List<LocationList> locLists = areaToLocLists(area);
		double total = 0;
		for (LocationList locs : locLists) {
			total += getExtent(locs);
		}
		return total;
	}

	private static double getExtent(LocationList locs) {
		Area area = new Area(locs.toPath());
		Rectangle2D rRect = area.getBounds2D();
		Location origin = new Location(rRect.getCenterY(), rRect.getCenterX());
		// compute orthogonal coordinates in km
		List<Double> xs = Lists.newArrayList();
		List<Double> ys = Lists.newArrayList();
		for (Location loc : locs) {
			LocationVector v = LocationUtils.vector(origin, loc);
			double az = v.getAzimuthRad();
			double d = v.getHorzDistance();
			xs.add(Math.sin(az) * d);
			ys.add(Math.cos(az) * d);
		}
		// repeat first point
		xs.add(xs.get(0));
		ys.add(ys.get(0));
		return computeArea(Doubles.toArray(xs), Doubles.toArray(ys));
		
	}

	/*
	 * Computes the area of a simple polygon; no data validation is performed
	 * except ensuring that all coordinates are positive.
	 */
	private static double computeArea(double[] xs, double[] ys) {
		positivize(xs);
		positivize(ys);
		double area = 0;
		for (int i = 0; i < xs.length - 1; i++) {
			area += xs[i] * ys[i + 1] - xs[i + 1] * ys[i];
		}
		return Math.abs(area) / 2;
	}

	/* Ensures positivity of values by adding Math.abs(min) if min < 0. */
	private static void positivize(double[] v) {
		double min = Doubles.min(v);
		if (min >= 0) return;
		DataUtils.add(Math.abs(min), v);
	}

	

	
	public static void main(String[] args) {
		SectionPolygons.create(FaultModels.FM3_1.fetchFaultSections(), 5d, 7d);
		
//		SimpleFaultSystemSolution tmp = null;
//		try {
//			File f = new File("tmp/invSols/reference_ch_sol2.zip");
////			File f = new File("tmp/invSols/ucerf2/FM2_1_UC2ALL_MaAvU2_DsrTap_DrAveU2_Char_VarAPrioriZero_VarAPrioriWt1000_mean_sol.zip");
//			
//			tmp = SimpleFaultSystemSolution.fromFile(f);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		InversionFaultSystemSolution invFss = new InversionFaultSystemSolution(tmp);
//		List<FaultSectionPrefData> srcList = invFss.getFaultSectionDataList();

//		List<FaultSectionPrefData> srcList = FaultModels.FM3_1.fetchFaultSections();
//				
//		FaultSectionPrefData fault 
//		int idx = 0;
//		for (FaultSectionPrefData fault : srcList) {
//			FaultTrace trace = fault.getFaultTrace();
//			System.out.println((idx++) + " " + fault.getParentSectionId() + " " + fault.getParentSectionName());
//			System.out.println((idx++) + " " + fault.getSectionId() + " " + fault.getName());
			
//			if (fault.getParentSectionId() == 603) {
//				System.out.println((idx++) + " " + fault.getSectionId() + " " + fault.getParentSectionId() + " " + fault.getParentSectionName());
////				System.out.println(trace);
//			}
//			System.out.println(trace);
//		}
//		SectionPolygons.create(srcList, 5d, null);
	}


}
