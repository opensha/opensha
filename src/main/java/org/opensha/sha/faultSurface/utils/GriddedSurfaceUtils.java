package org.opensha.sha.faultSurface.utils;

import java.awt.Color;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.geo.BorderType;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.RegionUtils;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.FrankelGriddedSurface;
import org.opensha.sha.faultSurface.GriddedSubsetSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.imr.param.PropagationEffectParams.DistanceSeisParameter;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class GriddedSurfaceUtils {
	
	/** Class name for debugging. */
	protected final static String C = "GriddedSurfaceUtils";
	/** If true print out debug statements. */
	protected final static boolean D = false;

	/** minimum depth for Campbell model */
	final static double SEIS_DEPTH = DistanceSeisParameter.SEIS_DEPTH;
	
	
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
	 * This computes distanceX
	 * @param surface
	 * @param siteLoc
	 * @return
	 */
	public static double getDistanceX(FaultTrace trace, Location siteLoc) {

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
					// TODO Auto-generated catch block
					e.printStackTrace();
					System.out.println("==== trace  ====");
					System.out.println(trace);
//					RegionUtils.locListToKML(trace, "distX_trace", Color.ORANGE);
					System.out.println("==== region ====");
					System.out.println(locsForRegion);
//					RegionUtils.locListToKML(locsForRegion, "distX_region", Color.RED);
					System.exit(0);
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

}
