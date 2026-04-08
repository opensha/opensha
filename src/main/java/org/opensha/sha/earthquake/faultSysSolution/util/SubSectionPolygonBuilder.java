package org.opensha.sha.earthquake.faultSysSolution.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.DataUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.Interpolate;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;

/**
 * This splits section polygons into subsection polygons. Inspired by and mostly copied from the
 * UCERF3 implementation.
 */
public class SubSectionPolygonBuilder {
	
	public static void buildSubsectionPolygons(List<? extends FaultSection> subSects, Region polygon) {
		buildSubsectionPolygons(subSects, polygon, true);
	}
	
	public static void buildSubsectionPolygons(List<? extends FaultSection> subSects, Region polygon, boolean shear) {
		if (subSects.size() == 1)
			subSects.get(0).setZonePolygon(polygon);
		Preconditions.checkState(subSects.size() > 0);
		
		int iters = shear ? 2 : 1;
		
		for (int iter=0; iter<iters; iter++) {
			Area area = polygon.getShape();
			List<Area> subSectAreas = new ArrayList<>();
			
			double[] shearAngles = null;
			if (iter > 0) {
				// figure out the width of the polygon halfway between the trace and the edge
				double firstAngle = Double.NaN;
				double lastAngle = Double.NaN;
				for (boolean first : new boolean[] {true, false}) {
					FaultSection sect = first ? subSects.get(0) : subSects.get(subSects.size()-1);
					double sectLen = sect.getTraceLength();
					FaultTrace trace = sect.getFaultTrace();
					double traceAz = trace.getStrikeDirection();
					Location middleTraceLoc = new Location(
							0.5 * (trace.first().getLatitude() + trace.last().getLatitude()),
							0.5 * (trace.first().getLongitude() + trace.last().getLongitude()));
					Region curPoly = sect.getZonePolygon();
					double minWidth = 0.5*sectLen;
					
					double leftWidth = Double.NaN;
					double rightWidth = Double.NaN;
					double leftDist = Double.NaN;
					double rightDist = Double.NaN;
					for (boolean left : new boolean[] {true, false}) {
						double perpAz;
						if (left)
							perpAz = traceAz - 90d;
						else
							perpAz = traceAz + 90d;
						Location perpLoc = LocationUtils.location(middleTraceLoc, Math.toRadians(perpAz), BUF);
						FaultTrace perpLine = new FaultTrace(null);
						perpLine.add(middleTraceLoc);
						perpLine.add(perpLoc);
						perpLine = FaultUtils.resampleTrace(perpLine, 1000);
						int lastInsideIndex = -1;
						for (int p=0; p<perpLine.size(); p++)
							if (curPoly.contains(perpLine.get(p)))
								lastInsideIndex = p;
						double width, dist;
						if (lastInsideIndex <= 1) {
							width = sectLen;
							dist = 0d;
						} else {
							int middleInsideIndex;
							if (lastInsideIndex > 4)
								middleInsideIndex = (int)(3d*lastInsideIndex / 4d + 0.5);
							else
								middleInsideIndex = lastInsideIndex / 2;
							Location middleInsideLoc = perpLine.get(middleInsideIndex);
							// now figure out the width of the region in the trace azimuth direction
							FaultTrace parallelLine = new FaultTrace(null);
							Location startLoc = LocationUtils.location(middleInsideLoc, Math.toRadians(traceAz+180), BUF);
							parallelLine.add(startLoc);
							Location endLoc = LocationUtils.location(middleInsideLoc, Math.toRadians(traceAz), BUF);
							parallelLine.add(endLoc);
							parallelLine = FaultUtils.resampleTrace(parallelLine, 1000);
							Location firstInside = null;
							Location lastInside = null;
							for (Location loc : parallelLine) {
								if (curPoly.contains(loc)) {
									if (firstInside == null)
										firstInside = loc;
									lastInside = loc;
								}
							}
							if (firstInside == null || firstInside == lastInside) {
								width = minWidth;
								dist = 0d;
							} else {
								width = Math.max(minWidth, LocationUtils.horzDistanceFast(firstInside, lastInside));
								dist = LocationUtils.linearDistanceFast(middleInsideLoc, middleTraceLoc);
							}
						}
						if (left) {
							leftWidth = width;
							leftDist = dist;
						} else {
							rightWidth = width;
							rightDist = dist;
						}
					}
					double delta = Math.abs(leftWidth - rightWidth);
					double sumDist = leftDist + rightDist;
					double angle;
					if (sumDist > 0d && delta > 0.01) {
						// tan(delta/subDist) = angle
						angle = Math.abs(Math.toDegrees(Math.atan(delta/sumDist)));
						angle = Math.min(angle, 22.5);
						if (leftWidth > rightWidth) {
//							shearAngles[i] = angle;
							if (first)
								angle = -angle;
						} else {
							if (!first)
								angle = -angle;
						}
					} else {
						angle = 0d;
					}
//					System.out.println(sect.getSectionName()+" shear iter "+iter+"; leftFractWidth="
//							+(float)(leftWidth/sectLen)+", rightFractWidth="+(float)(rightWidth/sectLen)
//							+", shearAngle="+(float)angle);
					if (first)
						firstAngle = angle;
					else
						lastAngle = angle;
				}
				// interpolate them from first to last to spread it out
				shearAngles = new double[subSects.size()];
				for (int i=0; i<shearAngles.length; i++) {
					shearAngles[i] = Interpolate.findY(0, firstAngle, shearAngles.length-1, lastAngle, i);
					FaultSection sect = subSects.get(i);
					if (sect instanceof GeoJSONFaultSection)
						((GeoJSONFaultSection) sect).getProperties().set(SECT_POLY_DIRECTION_PROP_NAME, sect.getDipDirection()+shearAngles[i]);
				}
//				System.out.println("Shear angles for "+subSects.get(0).getParentSectionName()+": "+Doubles.asList(shearAngles));
			}
			
			for (int i=0; i<subSects.size(); i++) {
				FaultSection ss1 = subSects.get(i);

				// if on last segment, use remaining fPoly and quit
				Area subSectArea = null;
				if (i == subSects.size() - 1) {
					if (area.isSingular()) {
						subSectArea = area;
					} else {
						// multi part polys need to have some attributed back
						// to the previous section
						List<LocationList> locLists = areaToLocLists(area);
						for (LocationList locs : locLists) {
							Area polyPart = new Area(locs.toPath());
							FaultTrace trace = ss1.getFaultTrace();
							if (intersects(trace, polyPart)) {
								// this is the poly associated with the fault trace
								if (subSectArea == null) {
									subSectArea = area;
								} else {
									subSectArea.add(area);
									if (!subSectArea.isSingular())
										subSectArea = hardMerge(subSectArea);
								}
							} else {
								Area leftover = polyPart;
								Area prev = subSectAreas.get(i-1);
								if (prev == null)
									prev = leftover;
								else
									prev.add(leftover);
								prev = cleanBorder(prev);
								if (!prev.isSingular()) prev = hardMerge(prev);
								if (prev == null) System.out.println(
									"merge problem last segment");
								subSectAreas.set(i-1, prev);
							}
						}
					}
					subSectAreas.add(subSectArea);
					break;
				}
				
				FaultSection ss2 = subSects.get(i + 1);
				double shearAngle = 0d;
				if (shearAngles != null) {
//					shearAngle = shearAngles[i];
					shearAngle = 0.5*(shearAngles[i] + shearAngles[i+1]);
				}
				LocationList envelope = createSubSecEnvelope(ss1, ss2, shearAngle);
				
				// intersect with copy of parent
				Area envPoly = new Area(envelope.toPath());
				subSectArea = (Area) area.clone();
				subSectArea.intersect(envPoly);
				
				// keep moving if nothing happened
				if (subSectArea.isEmpty()) {
					subSectAreas.add(null);
					continue;
				}
				
				// get rid of dead weight
				subSectArea = cleanBorder(subSectArea);
				
				// determine if there is a secondary poly not associated with
				// the fault trace that must be added back to parent
				Area leftover = null;
				if (!subSectArea.isSingular()) {
					List<LocationList> locLists = areaToLocLists(subSectArea);
					subSectArea = null;
					for (LocationList locs : locLists) {
						Area polyPart = new Area(locs.toPath());
						FaultTrace trace = ss1.getFaultTrace();
						if (intersects(trace, polyPart)) {
							// this is the poly associated with the fault trace
							if (subSectArea == null) {
								subSectArea = polyPart;
							} else {
								subSectArea.add(polyPart);
								if (!subSectArea.isSingular())
									subSectArea = hardMerge(subSectArea);
							}
						} else {
							leftover = polyPart;
						}
					}
				}
				
				// trim parent poly for next slice
				area.subtract(envPoly);
				area = cleanBorder(area);
				
				// try adding back into fault poly
				if (leftover != null) {
					Area fCopy = (Area) area.clone();
					fCopy.add(leftover);
					fCopy = cleanBorder(fCopy);
					if (!fCopy.isSingular()) {
						// try hard merge
						fCopy = hardMerge(fCopy);
						// hard merge failed, go to previous section
						if (fCopy == null) {
							Area prev = subSectAreas.get(i-1);
							prev.add(leftover);
							prev = cleanBorder(prev);
							if (!prev.isSingular()) prev = hardMerge(prev);
							if (prev == null) System.out.println("merge problem");
							subSectAreas.set(i-1, prev);
						} else {
							area = fCopy;
						}
					} else {
						area = fCopy;
					}
				}
				subSectAreas.add(subSectArea);
			}
			
			Preconditions.checkState(subSectAreas.size() == subSects.size());
			for (int i=0; i<subSects.size(); i++) {
				FaultSection subSect = subSects.get(i);
				Area subSectArea = subSectAreas.get(i);
				if (subSectArea == null)
					subSect.setZonePolygon(null);
				else
					subSect.setZonePolygon(areaToRegion(cleanBorder(subSectArea)));
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
			FaultSection sec1, FaultSection sec2, double shearAngle) {

		FaultTrace t1 = sec1.getFaultTrace();
		FaultTrace t2 = sec2.getFaultTrace();

		Location p1 = t1.get(t1.size() - 2);
		Location p2 = t1.get(t1.size() - 1);
		// check that last and first points of adjacent subs are coincident
		Preconditions.checkState(p2.equals(t2.get(0)));
		
		LocationVector vBackAz = LocationUtils.vector(p2, p1);
		vBackAz.setHorzDistance(BUF);
		LocationVector vBisect = new LocationVector();
		double polyDipDir = sec1.getDipDirection() + shearAngle;
		vBisect.setAzimuth(polyDipDir);
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
	
	public static final String SECT_POLY_DIRECTION_PROP_NAME = "SectPolyDir";
	
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
	
	static Region areaToRegion(Area area) {
		List<LocationList> locLists = areaToLocLists(area);
		Preconditions.checkState(locLists.size() == 1,
				"Expected 1 location list, have %s", locLists.size());
		return new Region(locLists.get(0), null);
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
	private static Area createDownDipPoly(FaultSection f) {
		RuptureSurface surf = f.getFaultSurface(1, false, false);
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

}
