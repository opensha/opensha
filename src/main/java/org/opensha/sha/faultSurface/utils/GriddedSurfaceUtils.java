package org.opensha.sha.faultSurface.utils;

import static org.opensha.commons.geo.GeoTools.EARTH_RADIUS_MEAN;
import static org.opensha.commons.geo.GeoTools.TWOPI;

import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.math3.util.Precision;
import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.FrankelGriddedSurface;
import org.opensha.sha.faultSurface.GriddedSubsetSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class GriddedSurfaceUtils {
	
	/** Class name for debugging. */
	protected final static String C = "GriddedSurfaceUtils";
	/** If true print out debug statements. */
	protected final static boolean D = false;

	/** minimum depth for Campbell model */
	public final static double SEIS_DEPTH = 3d;
	
	
	/**
	 * This computes distRup, distJB, & distSeis, which are available in the returned
	 * array in elements 0, 1, and 2 respectively.
	 * @param surface
	 * @param loc
	 * @return
	 */
	public static double[] getPropagationDistances(EvenlyGriddedSurface surface, Location loc) {
		
		Location loc1 = loc;
		Location loc2;
		double distJB = Double.MAX_VALUE;
		double distSeis = Double.MAX_VALUE;
		double distRup = Double.MAX_VALUE;
		
		double horzDist, vertDist, rupDist;

		// flag to project to seisDepth if only one row and depth is below seisDepth
		boolean projectToDepth = false;
		if (surface.getNumRows() == 1 && surface.getLocation(0,0).getDepth() < SEIS_DEPTH)
			projectToDepth = true;

		// get locations to iterate over depending on dip
		ListIterator<Location> it;
		try {
			if(surface.getAveDip() > 89) {
				it = surface.getColumnIterator(0);
				if (surface.getLocation(0,0).getDepth() < SEIS_DEPTH)
					projectToDepth = true;
			} else {
				it = surface.getLocationsIterator();
			}
		} catch (RuntimeException e) {
			// some surfaces can't compute dip, just loop over full surface
			it = surface.getLocationsIterator();
		}

		while( it.hasNext() ){

			loc2 = (Location) it.next();

			// get the vertical distance
			vertDist = LocationUtils.vertDistance(loc1, loc2);

			// get the horizontal dist depending on desired accuracy
			horzDist = LocationUtils.horzDistanceFast(loc1, loc2);

			if(horzDist < distJB) distJB = horzDist;

			rupDist = horzDist * horzDist + vertDist * vertDist;
			if(rupDist < distRup) distRup = rupDist;

			if (loc2.getDepth() >= SEIS_DEPTH) {
				if (rupDist < distSeis)
					distSeis = rupDist;
			}
			// take care of shallow line or point source case
			else if(projectToDepth) {
				rupDist = horzDist * horzDist + SEIS_DEPTH * SEIS_DEPTH;
				if (rupDist < distSeis)
					distSeis = rupDist;
			}
		}

		distRup = Math.pow(distRup,0.5);
		distSeis = Math.pow(distSeis,0.5);

		if(D) {
			System.out.println(C+": distRup = " + distRup);
			System.out.println(C+": distSeis = " + distSeis);
			System.out.println(C+": distJB = " + distJB);
		}
		
		// Check whether small values of distJB should really be zero
		if(distJB <surface.getAveGridSpacing()) { // check this first since the next steps could take time
			
			// first identify whether it's a frankel type surface
			boolean frankelTypeSurface=false;
			if(surface instanceof FrankelGriddedSurface) {
				frankelTypeSurface = true;
			}
			else if(surface instanceof GriddedSubsetSurface) {
				if(((GriddedSubsetSurface)surface).getParentSurface() instanceof FrankelGriddedSurface) {
					frankelTypeSurface = true;
				}
			}
					
			if (frankelTypeSurface) {
				if (isDjbZeroFrankel(surface, distJB)) distJB = 0;
			} else {
				if (isDjbZero(surface.getPerimeter(), loc)) distJB = 0;
			}
		}

		double[] results = {distRup, distJB, distSeis};
		
		return results;

	}
	
	/**
	 * This is the original OpenSHA DistanceX calculation method; it is slow an inaccurate, but preserved for posterity
	 * in case we need to reproduce prior results.
	 * 
	 * It is slow because:
	 * 1. It calculates everything using the static LocationUtils lat/lon methods, which each project internally rather
	 * than projecting once
	 * 2. It detects the sign (left or right of the trace) using a Region object which is slow to construct, and is
	 * constructed on each invocation
	 * 
	 * It is inaccurate because it extends the trace out 1000 km in each direction, then calculates a distance to the
	 * straight line at the location. This ends up exaggerating the curvature of the earth, especially at high latitudes
	 * 
	 * @param surface
	 * @param siteLoc
	 * @return
	 */
	public static double getDistanceX_old(FaultTrace trace, Location siteLoc) {

		double distanceX;
		
		// set to zero if it's a point source
		if(trace.size() == 1) {
			distanceX = 0;
		}
		else {
			// We should probably set something here here too if it's vertical strike-slip
			// (to avoid unnecessary calculations)

				// get points projected off the ends
				Location firstTraceLoc = trace.get(0); 						// first trace point
				Location lastTraceLoc = trace.get(trace.size()-1); 	// last trace point

				// get point projected from first trace point in opposite direction of the ave trace
				LocationVector dir = LocationUtils.vector(lastTraceLoc, firstTraceLoc); 		
				dir.setHorzDistance(1000); // project to 1000 km
				dir.setVertDistance(0d);
				Location projectedLoc1 = LocationUtils.location(firstTraceLoc, dir);


				// get point projected from last trace point in ave trace direction
				dir.setAzimuth(dir.getAzimuth()+180);  // flip to ave trace dir
				Location projectedLoc2 = LocationUtils.location(lastTraceLoc, dir);
				// point down dip by adding 90 degrees to the azimuth
				dir.setAzimuth(dir.getAzimuth()+90);  // now point down dip

				// get points projected in the down dip directions at the ends of the new trace
				Location projectedLoc3 = LocationUtils.location(projectedLoc1, dir);

				Location projectedLoc4 = LocationUtils.location(projectedLoc2, dir);

				LocationList locsForExtendedTrace = new LocationList();
				LocationList locsForRegion = new LocationList();

				locsForExtendedTrace.add(projectedLoc1);
				locsForRegion.add(projectedLoc1);
				for(int c=0; c<trace.size(); c++) {
					locsForExtendedTrace.add(trace.get(c));
					locsForRegion.add(trace.get(c));     	
				}
				locsForExtendedTrace.add(projectedLoc2);
				locsForRegion.add(projectedLoc2);

				// finish the region
				locsForRegion.add(projectedLoc4);
				locsForRegion.add(projectedLoc3);

				// write these out if in debug mode
				if(D) {
					System.out.println("Projected Trace:");
					for(int l=0; l<locsForExtendedTrace.size(); l++) {
						Location loc = locsForExtendedTrace.get(l);
						System.out.println(loc.getLatitude()+"\t"+ loc.getLongitude()+"\t"+ loc.getDepth());
					}
					System.out.println("Region:");
					for(int l=0; l<locsForRegion.size(); l++) {
						Location loc = locsForRegion.get(l);
						System.out.println(loc.getLatitude()+"\t"+ loc.getLongitude()+"\t"+ loc.getDepth());
					}
				}

				Region polygon=null;
				try {
					polygon = new Region(locsForRegion, BorderType.MERCATOR_LINEAR);
				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("==== trace  ====");
					System.out.println(trace);
//					RegionUtils.locListToKML(trace, "distX_trace", Color.ORANGE);
					System.out.println("==== region ====");
					System.out.println(locsForRegion);
//					RegionUtils.locListToKML(locsForRegion, "distX_region", Color.RED);
//					System.exit(0);
				}
				boolean isInside = polygon.contains(siteLoc);

				double distToExtendedTrace = locsForExtendedTrace.minDistToLine(siteLoc);

				if(isInside || distToExtendedTrace == 0.0) // zero values are always on the hanging wall
					distanceX = distToExtendedTrace;
				else 
					distanceX = -distToExtendedTrace;
		}
		
		return distanceX;
	}
	
	/**
	 * This computes distanceX
	 * @param surface
	 * @param siteLoc
	 * @return
	 */
	public static double getDistanceX(FaultTrace trace, Location siteLoc) {
		
		// set to zero if it's a point source
		if(trace.size() == 1) {
			return 0d;
		} else {
			Location traceStart = trace.first();
			Location traceEnd = trace.last();
			
			// projection reference frame, weighted 50% to the site location, and 50% to the middle of the trace
			double latRefRad = 0.5*siteLoc.getLatRad() + 0.25*traceStart.getLatRad() + 0.25*traceEnd.getLatRad();
			double lonRefRad = 0.5*siteLoc.getLonRad() + 0.25*traceStart.getLonRad() + 0.25*traceEnd.getLonRad();
			double lonScale = Math.cos(latRefRad);
			
			double[] traceX = new double[trace.size()];
			double[] traceY = new double[traceX.length];
			for (int i=0; i<traceX.length; i++) {
				Location loc = trace.get(i);
				traceX[i] = (loc.getLonRad() - lonRefRad) * lonScale;
				traceY[i] = loc.getLatRad() - latRefRad;
			}
			double siteX = (siteLoc.getLonRad() - lonRefRad) * lonScale;
			double siteY = siteLoc.getLatRad() - latRefRad;
			
			// find the nearest segment
			// keep track of angular (not-scaled to earth's radius) and squared distances for speed
			double[] angularSegDistsSq = new double[trace.size()-1];
			double minAngularSegDistSq = Double.POSITIVE_INFINITY;
			int minSegDistIndex = -1;
			for (int i=0; i<angularSegDistsSq.length; i++) {
				angularSegDistsSq[i] = Line2D.ptSegDistSq(
						traceX[i], traceY[i],
						traceX[i+1], traceY[i+1],
						siteX, siteY);
				if (angularSegDistsSq[i] < minAngularSegDistSq) {
					minAngularSegDistSq = angularSegDistsSq[i];
					minSegDistIndex = i;
				}
			}
			
			// now convert the closest squared angluar segment distance to km
			double minSegDist = Math.sqrt(minAngularSegDistSq) * EARTH_RADIUS_MEAN;

			// now figure out if we're beyond the ends, in which case we're likely closest to the extension
			double x0 = traceX[0];
			double y0 = traceY[0];
			double xN = traceX[traceX.length - 1];
			double yN = traceY[traceY.length - 1];

			double dxAll = xN - x0;
			double dyAll = yN - y0;
			double lenSqAll = dxAll*dxAll + dyAll*dyAll;
	        
			double sx = siteX - x0; // vector from start->site
			double sy = siteY - y0;
			double dotAll = dxAll*sx + dyAll*sy;

			if (dotAll < 0.0 || dotAll > lenSqAll) {
				// we're before or after
				
				boolean backwards = dotAll < 0.0;
				double azRad = LocationUtils.azimuthRad(traceStart, traceEnd);
				if (backwards) {
					// before
					return calcExtendedDistanceX(traceStart, siteLoc, azRad+Math.PI, minSegDist, minAngularSegDistSq, true);
				} else {
					// after
					return calcExtendedDistanceX(traceEnd, siteLoc, azRad, minSegDist, minAngularSegDistSq, false);
				}
			}
			
			// we're within the bounds, now we just need to figure out the sign
			boolean leftOfClosest = cross2D(
					traceX[minSegDistIndex+1] - traceX[minSegDistIndex],
					traceY[minSegDistIndex+1] - traceY[minSegDistIndex],
					siteX - traceX[minSegDistIndex],
					siteY - traceY[minSegDistIndex]) > 0;
			// check for the special case where we're equidistant from 2 segments (closest to a corner)
			// in that case, we could be left of the infinite extension of one but right of the other
			double checkPrecision = 1e-10;
			int equidistantIndex = -1;
			if (minSegDistIndex > 0 &&
					Precision.equals(angularSegDistsSq[minSegDistIndex], angularSegDistsSq[minSegDistIndex-1], checkPrecision)) {
				equidistantIndex = minSegDistIndex-1;
			} else if (minSegDistIndex < angularSegDistsSq.length-1 &&
					Precision.equals(angularSegDistsSq[minSegDistIndex], angularSegDistsSq[minSegDistIndex+1], checkPrecision)) {
				equidistantIndex = minSegDistIndex+1;
			}
			if (equidistantIndex >= 0) {
				boolean leftOfEquidistant = cross2D(
						traceX[equidistantIndex+1] - traceX[equidistantIndex],
						traceY[equidistantIndex+1] - traceY[equidistantIndex],
						siteX - traceX[equidistantIndex],
						siteY - traceY[equidistantIndex]) > 0;
				if (leftOfClosest != leftOfEquidistant) {
					// we're closest to a corner, and in the intermediate zone where we're left of one's extension but right of another
					
					boolean leftBefore, leftAfter;
					if (equidistantIndex < minSegDistIndex) {
						leftBefore = leftOfEquidistant;
						leftAfter = leftOfClosest;
					} else {
						leftBefore = leftOfClosest;
						leftAfter = leftOfEquidistant;
					}
					int firstMatchIndex = Integer.min(equidistantIndex, minSegDistIndex);
					double xBefore = traceX[firstMatchIndex];
					double yBefore = traceY[firstMatchIndex];
					double xCorner = traceX[firstMatchIndex+1];
					double yCorner = traceY[firstMatchIndex+1];
					double xAfter = traceX[firstMatchIndex+2];
					double yAfter = traceY[firstMatchIndex+2];
					
					// Vector B = (corner->before), anchored at P_i
				    double bx = xBefore - xCorner;
				    double by = yBefore - yCorner;

				    // Vector A = (corner->after), also anchored at P_i
				    double ax = xAfter - xCorner;
				    double ay = yAfter - yCorner;

				    // Magnitudes
				    double bLen = Math.hypot(bx, by);
				    double aLen = Math.hypot(ax, ay);

				    // Handle degenerate cases: no well-defined angle
				    if (bLen < 1e-12 || aLen < 1e-12) {
						// Corner is basically a repeated point or something nearly collinear.
						// default to before
						leftOfClosest = leftBefore; 
					} else {
						// Normalize
					    double bxn = bx / bLen;
					    double byn = by / bLen;
					    double axn = ax / aLen;
					    double ayn = ay / aLen;

					    // Check the turn direction
					    double crossBA = cross2D(bx, by, ax, ay);  // cross(B, A)

					    // Start with the naive sum
					    double wx = bxn + axn;
					    double wy = byn + ayn;

					    // If cross(B, A) < 0 => it's a "right turn" => flip W to keep inside angle
					    if (crossBA < 0.0) {
					        wx = -wx;
					        wy = -wy;
					    }

					    // If the sum is near zero length, e.g. B and A nearly opposite
					    double wLen = Math.hypot(wx, wy);
					    if (wLen < 1e-12) {
							// This means B and A point in nearly opposite directions (e.g. 180° turn).
							// The angle-bisector is ill-defined. default to after
							leftOfClosest = leftAfter;
						} else {
							// The site vector from corner = (siteX - xCorner, siteY - yCorner)
							sx = siteX - xCorner;
							sy = siteY - yCorner;

							// Cross of W with site vector => which side of the bisector?
							double cross = cross2D(wx, wy, sx, sy);

							// Example policy:
							//   If cross < 0 => site is on the "far side" => use after sign
							//   else => use before sign
							if (cross < 0.0) {
//								leftOfClosest = leftAfter;
								leftOfClosest = leftBefore;
							} else {
//								leftOfClosest = leftBefore;
								leftOfClosest = leftAfter;
							}
							
							// this can be used to debug
//							leftOfClosest = false;
//							if (cross < 0d) {
//								minSegDist = -1d;
//							} else {
//								minSegDist = 1d;
//							}
						}
					}
				}
			}
			if (leftOfClosest)
				return -minSegDist;
			return minSegDist;
		}
	}
	
	/**
	 * Calculate distanceX from a trace extending in a great circle from startLoc in the given direction.
	 * 
	 * This is more accurate than using the projected straight line distance
	 * 
	 * @param startLoc
	 * @param siteLoc
	 * @param azimuthRad
	 * @param distToEnd
	 * @param angularDistToEndSq
	 * @param backwards
	 * @return
	 */
	private static double calcExtendedDistanceX(Location startLoc, Location siteLoc,
			double azimuthRad, double distToEnd, double angularDistToEndSq, boolean backwards) {
		double lat1 = startLoc.getLatRad();
		double lon1 = startLoc.getLonRad();
		double sinLat1 = Math.sin(lat1);
		double cosLat1 = Math.cos(lat1);
		
		double siteLat = siteLoc.getLatRad();
		double siteLon = siteLoc.getLonRad();

		// figure out azimuth from start to site
		// modifed from LocationUtils.azimuthRad to remove duplicate sin & cos calculations
		double dSiteLon = siteLon - lon1;
		double sinSiteLat = Math.sin(siteLat);
		double cosSiteLat = Math.cos(siteLat);
		double siteAz = Math.atan2(Math.sin(dSiteLon) * cosSiteLat, cosLat1 *
			sinSiteLat - Math.sin(lat1) * cosSiteLat * Math.cos(dSiteLon));

		siteAz = (siteAz + TWOPI) % TWOPI;

		// make a straight line approximation of how far away the closest point on this line will be
		double diff = siteAz - azimuthRad;
		diff = (diff + Math.PI) % (2 * Math.PI);
		if (diff < 0)
			diff += 2 * Math.PI;
		diff = diff - Math.PI;

		// make sure it's not too small
		double destDist = Math.max(10d, distToEnd * Math.cos(diff));
		
		// now move that distance in the chosen direction
		double ad = destDist / EARTH_RADIUS_MEAN; // angular distance
		double sinD = Math.sin(ad);
		double cosD = Math.cos(ad);

		// this is that point along the line
		double lat2 = Math.asin(sinLat1 * cosD + cosLat1 * sinD * Math.cos(azimuthRad));
		double lon2 = lon1 +
			Math.atan2(Math.sin(azimuthRad) * sinD * cosLat1,
				cosD - sinLat1 * Math.sin(lat2));

		// set reference frame, use the average of the site and our guess of the closest location on the line
		double latRefRad = 0.5*(lat2 + siteLat);
		double lonRefRad = 0.5*(lon2 + siteLon);
		double lonScale = Math.cos(latRefRad);

		// project into that reference frame
		double x1 = (lon1 - lonRefRad) * lonScale;
		double y1 = lat1 - latRefRad;

		double x2 = (lon2 - lonRefRad) * lonScale;
		double y2 = lat2 - latRefRad;

		double siteX = (siteLon - lonRefRad) * lonScale;
		double siteY = siteLat - latRefRad;

		// squared distance to that infinite line
		double angularDistSq = Line2D.ptLineDistSq(x1, y1, x2, y2, siteX, siteY);
		// the nearest segment could still be closer
		double dist;
		if (angularDistSq < angularDistToEndSq) {
			// this is closest
			dist = Math.sqrt(angularDistSq) * EARTH_RADIUS_MEAN;
		} else {
			// segment is still closer
			dist = distToEnd;
		}
		// determine sign from cross product with that global line
		double cross = cross2D(x2-x1, y2-y1, siteX, siteY);
		if (cross > 0.0)
			dist = -dist;
		if (backwards)
			dist = -dist;
		return dist;
	}

    /**
     * Simple 2D cross-product helper:
     * cross2D( (ax,ay), (bx,by) ) = ax*by - ay*bx.
     *
     * Positive => (bx,by) is "left" of (ax,ay),
     * Negative => "right",
     * Zero => collinear.
     */
    private static double cross2D(double ax, double ay, double bx, double by) {
        return ax * by - ay * bx;
    }
	
	/**
	 * This computes Ry0, the absolute value of Ry
	 * @param surface
	 * @param siteLoc
	 * @return
	 */
	public static double getDistanceY0(FaultTrace trace, Location siteLoc) {
		return Math.abs(getDistanceY(trace, siteLoc));
	}
	
	/**
	 * This computes Ry
	 * @param surface
	 * @param siteLoc
	 * @return
	 */
	public static double getDistanceY(FaultTrace trace, Location siteLoc) {
		// set to zero if it's a point source
		if(trace.size() == 1)
			return 0d;
		
		Location firstTraceLoc = trace.first(); 				// first trace point
		Location lastTraceLoc = trace.last(); 					// last trace point
		
		double distToFirst = LocationUtils.horzDistanceFast(firstTraceLoc, siteLoc);
		double distToLast = LocationUtils.horzDistanceFast(lastTraceLoc, siteLoc);
		
		// the vector from p0 to p1 is in the direction of increasing Ry0
		Location p0, p1;
		
		// this uses the last segment to define the azimuth, and can be screwy
//		if (distToFirst < distToLast) {
//			p0 = trace.get(1);
//			p1 = firstTraceLoc;
//		} else {
//			p0 = trace.get(trace.size()-2);
//			p1 = lastTraceLoc;
//		}
		// this uses the overall rupture azimuth and is less screwy
		if (distToFirst < distToLast) {
			p0 = lastTraceLoc;
			p1 = firstTraceLoc;
		} else {
			p0 = firstTraceLoc;
			p1 = lastTraceLoc;
		}
		
		LocationVector traceVector = LocationUtils.vector(p0, p1);
		LocationVector endToSite = LocationUtils.vector(p1, siteLoc);
		
		double endSiteAz = endToSite.getAzimuth();
		while (endSiteAz < 0)
			endSiteAz += 360;
		double traceVectorAz = traceVector.getAzimuth();
		while (traceVectorAz < 0)
			traceVectorAz += 360;
		
		double azDiff = endSiteAz - traceVectorAz;
		if (azDiff < -90)
			azDiff += 360;
		else if (azDiff > 90)
			azDiff -= 360;
		azDiff = Math.abs(azDiff);
		
//		double azDiff = Math.abs(endToSite.getAzimuth() - traceVector.getAzimuth());
//		while (azDiff >= 360)
//			azDiff -= 360;
//		if (!Double.isNaN(1d)) {
//			if ((float)siteLoc.getLatitude() == 34f && (float)siteLoc.getLongitude() == -116f) {
//				System.out.println("Loc: "+siteLoc);
//				System.out.println("P0: "+p0);
//				System.out.println("P1: "+p1);
//				System.out.println("TraceVector Az: "+traceVector.getAzimuth());
//				System.out.println("EndToSite Az: "+endToSite.getAzimuth());
//				System.out.println("Az Diff: "+azDiff);
//			}
//			return azDiff;
//		}
		Preconditions.checkState(azDiff >= 0);
		
		if (azDiff > 90)
			// it's within the ends of the rupture
			return 0;
		
		LocationVector abovePoint = new LocationVector(traceVector.getAzimuth() + 90, endToSite.getHorzDistance()*2, 0d);
		LocationVector belowPoint = new LocationVector(traceVector.getAzimuth() - 90, endToSite.getHorzDistance()*2, 0d);
		
		Location perpP1 = LocationUtils.location(p1, abovePoint);
		Location perpP2 = LocationUtils.location(p1, belowPoint);
		
		return LocationUtils.distanceToLine(perpP1, perpP2, siteLoc);
	}
	
	
	/**
	 * This returns brief info about this surface
	 * @param surf
	 * @return
	 */
	public static String getSurfaceInfo(EvenlyGriddedSurface surf) {
		Location loc1 = surf.getLocation(0, 0);
		Location loc2 = surf.getLocation(0,surf.getNumCols() - 1);
		Location loc3 = surf.getLocation(surf.getNumRows()-1, 0);
		Location loc4 = surf.getLocation(surf.getNumRows()-1,surf.getNumCols()-1);
		return new String("\tRup. Surf. Corner Locations (lat, lon, depth (km):" +
				"\n\n" +
				"\t\t" + (float) loc1.getLatitude() + ", " +
				(float) loc1.getLongitude() + ", " +
				(float) loc1.getDepth() + "\n" +
				"\t\t" + (float) loc2.getLatitude() + ", " +
				(float) loc2.getLongitude() + ", " +
				(float) loc2.getDepth() + "\n" +
				"\t\t" + (float) loc3.getLatitude() + ", " +
				(float) loc3.getLongitude() + ", " +
				(float) loc3.getDepth() + "\n" +
				"\t\t" + (float) loc4.getLatitude() + ", " +
				(float) loc4.getLongitude() + ", " +
				(float) loc4.getDepth() + "\n");
	}
	
	
	/**
	 * This gets the perimeter locations
	 * @param surface
	 * @return
	 */
	public static LocationList getEvenlyDiscritizedPerimeter(EvenlyGriddedSurface surface) {
		LocationList locList = new LocationList();
		int lastRow = surface.getNumRows()-1;
		int lastCol = surface.getNumCols()-1;
		for(int c=0;c<=lastCol;c++) locList.add(surface.get(0, c));
		for(int r=0;r<=lastRow;r++) locList.add(surface.get(r, lastCol));
		for(int c=lastCol;c>=0;c--) locList.add(surface.get(lastRow, c));
		for(int r=lastRow;r>=0;r--) locList.add(surface.get(r, 0));
		return locList;
	}
	
	/**
	 * Creates an evenly discretized line between the two points, with, at a maximum, the given
	 * grid spacing. Will return at least 2 points.
	 * @param start
	 * @param end
	 * @param gridSpacing
	 * @return
	 */
	public static LocationList getEvenlyDiscretizedLine(Location start, Location end, double gridSpacing) {
		double length = LocationUtils.linearDistance(start, end);
		if (gridSpacing > length)
			gridSpacing = length;
		LocationVector vector = LocationUtils.vector(start, end);
		double numSpans = Math.ceil(length/gridSpacing);
		vector.setHorzDistance(vector.getHorzDistance()/numSpans);
		vector.setVertDistance(vector.getVertDistance()/numSpans);
		
		LocationList line = new LocationList();
		line.add(start);
		Location prevPt = start;
		for (int i=0; i<(int)numSpans; i++) {
			Location loc = LocationUtils.location(prevPt, vector);
			line.add(loc);
			prevPt = loc;
		}
		return line;
	}
	
	/**
	 * This returns the minimum distance as the minimum among all location
	 * pairs between the two surfaces
	 * @param surface1 RuptureSurface 
	 * @param surface2 RuptureSurface 
	 * @return distance in km
	 */
	public static double getMinDistanceBetweenSurfaces(RuptureSurface surface1, RuptureSurface surface2) {
		Iterator<Location> it = surface1.getLocationsIterator();
		double min3dDist = Double.POSITIVE_INFINITY;
		double dist;
		// find distance between all location pairs in the two surfaces
		while(it.hasNext()) { // iterate over all locations in this surface
			Location loc1 = (Location)it.next();
			Iterator<Location> it2 = surface2.getEvenlyDiscritizedListOfLocsOnSurface().iterator();
			while(it2.hasNext()) { // iterate over all locations on the user provided surface
				Location loc2 = (Location)it2.next();
				dist = LocationUtils.linearDistanceFast(loc1, loc2);
				if(dist<min3dDist){
					min3dDist = dist;
				}
			}
		}
		return min3dDist;
	}

	/*
	 * This method is used to check small distJB values for continuous, smooth
	 * surfaces; e.g. non-Frankel type surfaces. This was implemented to replace
	 * using a Region.contains() which can fail when dipping faults have
	 * jagged traces. This method borrows from Region using a java.awt.geom.Area
	 * to perform a contains test, however no checking is done of the area's
	 * singularity.
	 * 
	 * The Elsinore fault was the culprit leading to this implementation. For
	 * a near-vertical (85˚) strike-slip fault, it is has an unrealistic ≥90 jog
	 * in it. Even this method does not technically give a 100% correct answer.
	 * Drawing out a steeply dipping fault with a jog will show that the
	 * resultant perimeter polygon has eliminated small areas for which distJB
	 * should be zero. The areas are so small though that the hazard is not
	 * likely affected.
	 */
	private static boolean isDjbZero(LocationList border, Location pt) {
		Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD, border.size());
		boolean starting = true;
		for (Location loc : border) {
			double lat = loc.getLatitude();
			double lon = loc.getLongitude();
			// if just starting, then moveTo
			if (starting) {
				path.moveTo(lon, lat);
				starting = false;
				continue;
			}
			path.lineTo(lon, lat);
		}
		path.closePath();
		Area area = new Area(path);
		return area.contains(pt.getLongitude(), pt.getLatitude());
	}
	
	/*
	 * This is used to check whether a small value of DistJB should really be
	 * zero because of surface discretization. This is used where a contains
	 * call on the surface perimeter wont work (e.g., because of loops and gaps
	 * at the bottom of a FrankelGriddedSurface). Surfaces that only have one
	 * row or column always return false (which means non-zero distJB along the
	 * trace of a straight line source). Note that this will return true for
	 * locations that are slightly off the surface projection (essentially
	 * expanding the edge of the fault by about have the discretization level.
	 */
	private static boolean isDjbZeroFrankel(EvenlyGriddedSurface surface,
			double distJB) {
		if (surface.getNumCols() > 1 && surface.getNumRows() > 1) {
			double d1, d2, min_dist;
			d1 = LocationUtils.horzDistanceFast(surface.getLocation(0, 0),
				surface.getLocation(1, 1));
			d2 = LocationUtils.horzDistanceFast(surface.getLocation(0, 1),
				surface.getLocation(1, 0));
			// the 1.1 is to prevent a precisely centered point to return false
			min_dist = 1.1 * Math.min(d1, d2) / 2;
			return distJB <= min_dist;
		}
		return false;
	}
	
	/**
	 * Trims the given number of points from the start and end of the given compound surface. All sub surfaces
	 * must be instances of EvenlyGriddedSurfaces
	 * @param compoundSurf
	 * @param numFromStart
	 * @param numFromEnd
	 * @return
	 */
	public static CompoundSurface trimEndsOfSurface(CompoundSurface compoundSurf, int numFromStart, int numFromEnd) {
		Preconditions.checkArgument(numFromStart > 0 || numFromEnd > 0, "must remove at least one point");
		List<? extends RuptureSurface> surfList = compoundSurf.getSurfaceList();
		// make sure each one is an evenly gridded surface
		for (RuptureSurface subSurf : surfList)
			Preconditions.checkState(subSurf instanceof EvenlyGriddedSurface, "all sub surfaces must be evenly gridded");
		
		List<RuptureSurface> newSurfList = Lists.newArrayList();
		// add first. if first is reversed, then trim from the end instead
		EvenlyGriddedSurface trimmedStart = (EvenlyGriddedSurface) surfList.get(0);
		if (numFromStart > 0) {
			if (compoundSurf.isSubSurfaceReversed(0))
				trimmedStart = trimEndsOfSurface(trimmedStart, 0, numFromStart);
			else
				trimmedStart = trimEndsOfSurface(trimmedStart, numFromStart, 0);
		}
		newSurfList.add(trimmedStart);
		int lastIndex = surfList.size()-1;
		// add middle
		for (int i=1; i<lastIndex; i++)
			newSurfList.add(surfList.get(i));
		// add last. if last is reversed, then trim from start instead
		EvenlyGriddedSurface trimmedEnd = (EvenlyGriddedSurface)surfList.get(lastIndex);
		if (numFromEnd > 0) {
			if (compoundSurf.isSubSurfaceReversed(lastIndex))
				trimmedEnd = trimEndsOfSurface(trimmedEnd, numFromEnd, 0);
			else
				trimmedEnd = trimEndsOfSurface(trimmedEnd, 0, numFromEnd);
		}
		newSurfList.add(trimmedEnd);
		
		Preconditions.checkState(newSurfList.size() == surfList.size(), "Size is messed up");
		
		return new CompoundSurface(newSurfList);
	}
	
	/**
	 * Trims the given number of points from the start/end of the given surface. There must be at least one column left.
	 * @param gridSurf
	 * @param numFromStart
	 * @param numFromEnd
	 * @return
	 */
	public static EvenlyGriddedSurface trimEndsOfSurface(EvenlyGriddedSurface gridSurf, int numFromStart, int numFromEnd) {
		int numRows = gridSurf.getNumRows();
		int numPointsToRemove = numFromStart + numFromEnd;
		Preconditions.checkArgument(numPointsToRemove > 0, "must remove at least one point");
		int numCols = gridSurf.getNumCols() - numPointsToRemove;
		Preconditions.checkState(numCols >= 1,
				"Cannot trim "+numPointsToRemove+" from surface with only "+gridSurf.getNumCols()+" cols");
		int startRow = 0;
		int startCol = numFromStart;
		return new GriddedSubsetSurface(numRows, numCols, startRow, startCol, gridSurf);
	}
	
	/**
	 * Calculates a quick the distance to the this surface by taking the minimum distance
	 * to the corners, middle of the upper and lower traces, and overall center point
	 * @param surf
	 * @param siteLoc
	 * @return
	 */
	public static double getCornerMidpointDistance(EvenlyGriddedSurface surf, Location siteLoc) {
		double minDist = Double.POSITIVE_INFINITY;
		
		int numRows = surf.getNumRows();
		int numCols = surf.getNumCols();
		
		List<Location> quickLocs = new ArrayList<>();
		quickLocs.add(surf.get(0, 0));
		quickLocs.add(surf.get(numRows-1, 0));
		quickLocs.add(surf.get(0, numCols-1));
		quickLocs.add(surf.get(numRows-1, numCols-1));
		if (numCols > 2) {
			quickLocs.add(surf.get(0, numCols/2));
			quickLocs.add(surf.get(numRows-1, numCols/2));
		}
		quickLocs.add(getSurfaceMiddleLoc(surf));
		
		for (Location loc : quickLocs)
			minDist = Math.min(minDist, LocationUtils.linearDistanceFast(siteLoc, loc));
		return minDist;
	}
	
	/**
	 * Gets the center location of this surface. If it implements EvenlyGriddedSurface and has 3 or more
	 * rows and columns, the center is directly retrieved. Otherwise the arithmetic average location of 
	 * the evenly discretized location list is computed.
	 * @param surf
	 * @return center location
	 */
	public static Location getSurfaceMiddleLoc(RuptureSurface surf) {
		if (surf instanceof EvenlyGriddedSurface) {
			EvenlyGriddedSurface gridSurf = (EvenlyGriddedSurface)surf;
			if (gridSurf.getNumRows() > 2 && gridSurf.getNumCols() > 2)
				return gridSurf.getLocation(gridSurf.getNumRows()/2, gridSurf.getNumCols()/2);
		}
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		MinMaxAveTracker depthTrack = new MinMaxAveTracker();
		for (Location loc : surf.getEvenlyDiscritizedListOfLocsOnSurface()) {
			latTrack.addValue(loc.getLatitude());
			lonTrack.addValue(loc.getLongitude());
			depthTrack.addValue(loc.getDepth());
		}
		return new Location(latTrack.getAverage(), lonTrack.getAverage(), depthTrack.getAverage());
	}

}
