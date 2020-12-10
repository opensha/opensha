/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.sha.faultSurface;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opensha.commons.exceptions.FaultException;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationVector;

import com.google.common.base.Preconditions;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;

/**
 * <b>Title:</b> StirlingGriddedSurface.   <br>
 * <b>Description: This creates an EvenlyGriddedSurface
 * representation of the fault using a scheme described by Mark Stirling
 * to Ned Field in 2001, where grid points are projected down dip at
 * an angle perpendicular to the end-points of the faultTrace (or in aveDipDir
 * if provided using the appropriate constructor).
</b> <br>
 * @author Ned Field.
 * @version 1.0
 */

public class StirlingGriddedSurface extends EvenlyGriddedSurfFromSimpleFaultData {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	protected final static String C = "StirlingGriddedSurface";
	protected final static boolean D = false;

	protected double aveDipDir = Double.NaN;

	protected final static double PI_RADIANS = Math.PI / 180;
	protected final static String ERR = " is null, unable to process.";


	/**
	 * This applies the  grid spacing exactly as given (trimming any remainder from the ends)
	 * @param simpleFaultData
	 * @param gridSpacing
	 * @throws FaultException
	 */
	public StirlingGriddedSurface(SimpleFaultData simpleFaultData, double gridSpacing) throws FaultException {
		super(simpleFaultData, gridSpacing);
		this.aveDipDir=simpleFaultData.getAveDipDir();
		createEvenlyGriddedSurface();
	}
	
	/**
	 * This applies the  grid spacing exactly as given (trimming any remainder from the ends),
	 * and applies the ave-dip direction as computed from the faultTrace.
	 */
	public StirlingGriddedSurface(FaultTrace faultTrace, double aveDip, double upperSeismogenicDepth,
			double lowerSeismogenicDepth, double gridSpacing) throws FaultException {

		super(faultTrace, aveDip, upperSeismogenicDepth, lowerSeismogenicDepth, gridSpacing);
		createEvenlyGriddedSurface();
	}


	/**
	 * This will fit the length & DDW of the fault exactly (no trimming) by adjusting the grid spacing
	 * to just below the grid spacings given
	 * @param simpleFaultData
	 * @param maxGridSpacingAlong
	 * @param maxGridSpacingDown
	 * @throws FaultException
	 */
	public StirlingGriddedSurface(SimpleFaultData simpleFaultData, double maxGridSpacingAlong, double maxGridSpacingDown) throws FaultException {
		super(simpleFaultData, maxGridSpacingAlong, maxGridSpacingDown);
		this.aveDipDir=simpleFaultData.getAveDipDir();
		createEvenlyGriddedSurface();
	}

	/**
	 * This applies the  grid spacing exactly as given (trimming any remainder from the ends),
	 * and applies the given ave-dip direction (rather than computing this from the the faultTrace).
	 */
	public StirlingGriddedSurface( FaultTrace faultTrace, double aveDip, double upperSeismogenicDepth,
			double lowerSeismogenicDepth, double gridSpacing,double aveDipDir )
	throws FaultException {

		super(faultTrace, aveDip, upperSeismogenicDepth, lowerSeismogenicDepth, gridSpacing);
		this.aveDipDir = aveDipDir;
		createEvenlyGriddedSurface(); 
	}


	/**
	 * Stitch Together the fault sections. It assumes:
	 * 1. Sections are in correct order
	 * 2. Distance between end points of section in correct order is less than the distance to opposite end of section
	 * Upper seismogenic depth, sip aand lower seimogenic depth are area weighted.
	 * 
	 * @param simpleFaultData
	 * @param gridSpacing
	 * @throws FaultException
	 */
	public StirlingGriddedSurface(List<SimpleFaultData> simpleFaultData,
			double gridSpacing) throws FaultException {

		super(simpleFaultData, gridSpacing);
		createEvenlyGriddedSurface();
	}
	
	// private for cloning
	private StirlingGriddedSurface() {
		
	}

	@Override
	public double getAveDipDirection() {
		return aveDipDir;
	}


	/**
	 * Creates the Stirling Gridded Surface from the Simple Fault Data
	 * @throws FaultException
	 */
	private void createEvenlyGriddedSurface() throws FaultException {

		String S = C + ": createEvenlyGriddedSurface():";
		if( D ) System.out.println(S + "Starting");

		assertValidData();

		final int numSegments = faultTrace.getNumLocations() - 1;
		final double avDipRadians = aveDip * PI_RADIANS;
		final double gridSpacingCosAveDipRadians = gridSpacingDown * Math.cos( avDipRadians );
		final double gridSpacingSinAveDipRadians = gridSpacingDown * Math.sin( avDipRadians );

		double[] segmentLenth = new double[numSegments];
		double[] segmentAzimuth = new double[numSegments];
		double[] segmentCumLenth = new double[numSegments];

		double cumDistance = 0;
		int i = 0;

		Location firstLoc;
		Location lastLoc;
		double aveDipDirection;
		// Find ave dip direction (defined by end locations):
		if( Double.isNaN(aveDipDir) ) {
			aveDipDirection = faultTrace.getDipDirection();
		} else {
			aveDipDirection = aveDipDir;
		}

		if(D) System.out.println(this.faultTrace.getName()+"\taveDipDirection = " + (float)aveDipDirection);


		// Iterate over each Location in Fault Trace
		// Calculate distance, cumulativeDistance and azimuth for
		// each segment
		Iterator<Location> it = faultTrace.iterator();
		firstLoc = it.next();
		lastLoc = firstLoc;
		Location loc = null;
		LocationVector dir = null;
		while( it.hasNext() ){

			loc = it.next();
			dir = LocationUtils.vector(lastLoc, loc);

			double azimuth = dir.getAzimuth();
			double distance = dir.getHorzDistance();
			cumDistance += distance;

			segmentLenth[i] = distance;
			segmentAzimuth[i] = azimuth;
			segmentCumLenth[i] = cumDistance;

			i++;
			lastLoc = loc;

		}

		// Calculate down dip width
		double downDipWidth = (lowerSeismogenicDepth-upperSeismogenicDepth)/Math.sin( avDipRadians );

		// Calculate the number of rows and columns
		int rows = 1 + Math.round((float) (downDipWidth/gridSpacingDown));
		int cols = 1 + Math.round((float) (segmentCumLenth[numSegments - 1] / gridSpacingAlong));


		if(D) System.out.println("numLocs: = " + faultTrace.getNumLocations());
		if(D) System.out.println("numSegments: = " + numSegments);
		if(D) System.out.println("firstLoc: = " + firstLoc);
		if(D) System.out.println("lastLoc(): = " + lastLoc);
		if(D) System.out.println("downDipWidth: = " + downDipWidth);
		if(D) System.out.println("totTraceLength: = " + segmentCumLenth[ numSegments - 1]);
		if(D) System.out.println("numRows: = " + rows);
		if(D) System.out.println("numCols: = " + cols);


		// Create GriddedSurface
		int segmentNumber, ith_row, ith_col = 0;
		double distanceAlong, distance, hDistance, vDistance;
		//location object
		Location location1;


		//initialize the num of Rows and Cols for the container2d object that holds
		setNumRowsAndNumCols(rows,cols);


		// Loop over each column - ith_col is ith grid step along the fault trace
		if( D ) System.out.println(S + "Iterating over columns up to " + cols );
		while( ith_col < cols ){

			if( D ) System.out.println(S + "ith_col = " + ith_col);

			// calculate distance from column number and grid spacing
			distanceAlong = ith_col * gridSpacingAlong;
			if( D ) System.out.println(S + "distanceAlongFault = " + distanceAlong);

			// Determine which segment distanceAlong is in
			segmentNumber = 1;
			while( segmentNumber <= numSegments && distanceAlong > segmentCumLenth[ segmentNumber - 1] ){
				segmentNumber++;
			}
			// put back in last segment if grid point has just barely stepped off the end
			if( segmentNumber == numSegments+1) segmentNumber--;

			if( D ) System.out.println(S + "segmentNumber " + segmentNumber );

			// Calculate the distance from the last segment point
			if ( segmentNumber > 1 ) distance = distanceAlong - segmentCumLenth[ segmentNumber - 2 ];
			else distance = distanceAlong;
			if( D ) System.out.println(S + "distanceFromLastSegPt " + distance );

			// Calculate the grid location along fault trace and put into grid
			location1 = faultTrace.get( segmentNumber - 1 );
			//            dir = new LocationVector(0, distance, segmentAzimuth[ segmentNumber - 1 ], 0);
			dir = new LocationVector(segmentAzimuth[ segmentNumber - 1 ], distance, 0);

			// location on the trace
			Location traceLocation = LocationUtils.location( location1, dir  );

			// get location at the top of the fault surface
			Location topLocation = getTopLocation(traceLocation, upperSeismogenicDepth,
					avDipRadians, aveDipDirection);

			set(0, ith_col, topLocation.clone());
			if( D ) System.out.println(S + "(x,y) topLocation = (0, " + ith_col + ") " + topLocation );

			// Loop over each row - calculating location at depth along the fault trace
			ith_row = 1;
			while(ith_row < rows){

				if( D ) System.out.println(S + "ith_row = " + ith_row);

				// Calculate location at depth and put into grid
				hDistance = ith_row * gridSpacingCosAveDipRadians;
				//                vDistance = -ith_row * gridSpacingSinAveDipRadians;
				vDistance = ith_row * gridSpacingSinAveDipRadians;

				//                dir = new LocationVector(vDistance, hDistance, aveDipDirection, 0);
				dir = new LocationVector(aveDipDirection, hDistance, vDistance);

				Location depthLocation = LocationUtils.location( topLocation, dir );
				set(ith_row, ith_col, depthLocation.clone());
				if( D ) System.out.println(S + "(x,y) depthLocation = (" + ith_row + ", " + ith_col + ") " + depthLocation );

				ith_row++;
			}
			ith_col++;
		}

		if( D ) System.out.println(S + "Ending");

		/*
        // test for fittings surfaces exactly
        if((float)(faultTrace.getTraceLength()-getSurfaceLength()) != 0.0)
        	System.out.println(faultTrace.getName()+"\n\t"+
        		"LengthDiff="+(float)(faultTrace.getTraceLength()-getSurfaceLength())+
        		"\t"+(float)faultTrace.getTraceLength()+"\t"+(float)getSurfaceLength()+"\t"+getNumCols()+"\t"+(float)getGridSpacingAlongStrike()+
        		"\n\tWidthDiff="+(float)(downDipWidth-getSurfaceWidth())
        		+"\t"+(float)(downDipWidth)+"\t"+(float)getSurfaceWidth()+"\t"+getNumRows()+"\t"+(float)getGridSpacingDownDip());
		 */
	}
	
	/**
	 * This will return the location, down dip from the fault trace point at the top of the seismogenic
	 * zone. If the location is already at or below the upper seismogenic depth, it is simply returned.
	 * @param traceLoc
	 * @param upperSeismogenicDepth
	 * @param aveDipRadians in radians
	 * @param aveDipDirection in decimal degrees
	 * @return
	 */
	public static Location getTopLocation(Location traceLoc, double upperSeismogenicDepth,
			double aveDipRadians, double aveDipDirection) {
		if(traceLoc.getDepth() < upperSeismogenicDepth) {
			//                vDistance = traceLocation.getDepth() - upperSeismogenicDepth;
			double vDistance = upperSeismogenicDepth - traceLoc.getDepth();
			double hDistance = vDistance / Math.tan( aveDipRadians );
			//                dir = new LocationVector(vDistance, hDistance, aveDipDirection, 0);
			LocationVector dir = new LocationVector(aveDipDirection, hDistance, vDistance);
			return LocationUtils.location( traceLoc, dir );
		}
		else
			return traceLoc;
	}
	
	@Override
	/**
	 * Override the parent with a version with fewer points
	 */
	public LocationList getPerimeter() {
		
		LocationList topTrace = new LocationList();
		LocationList botTrace = new LocationList();
		final double avDipRadians = aveDip * PI_RADIANS;
		double aveDipDirection;
		if( Double.isNaN(aveDipDir) ) {
			aveDipDirection = faultTrace.getDipDirection();
		} else {
			aveDipDirection = aveDipDir;
		}
		for(Location traceLoc:faultTrace) {
			double vDistance = upperSeismogenicDepth - traceLoc.getDepth();
			double hDistance = vDistance / Math.tan( avDipRadians );
			LocationVector dir = new LocationVector(aveDipDirection, hDistance, vDistance);
			Location topLoc = LocationUtils.location( traceLoc, dir );
			topTrace.add(topLoc);
			
			vDistance = lowerSeismogenicDepth - traceLoc.getDepth();
			hDistance = vDistance / Math.tan( avDipRadians );
			dir = new LocationVector(aveDipDirection, hDistance, vDistance);
			Location botLoc = LocationUtils.location( traceLoc, dir );
			botTrace.add(botLoc);
		}
		
		// now make and close the list
		LocationList perimiter = new LocationList();
		perimiter.addAll(topTrace);
		botTrace.reverse();
		perimiter.addAll(botTrace);
		perimiter.add(topTrace.get(0));
		
		return perimiter;
	}
	
	public double getStrikeAtDAS(double das) {
		Preconditions.checkState(numCols > 1);
		double spacingAS = getGridSpacingAlongStrike();
		double indexAS = das/spacingAS;
		int asIndexBefore = (int)indexAS;
		Preconditions.checkState(asIndexBefore >= 0 && asIndexBefore < numCols);
		
		if (asIndexBefore == numCols-1)
			// we're at the last column
			asIndexBefore--;
		return LocationUtils.azimuth(get(0, asIndexBefore-1), get(0, asIndexBefore));
	}

	/**
	 * Interpolates the surface to find the point at the given distance along strike and distance down dip
	 * 
	 * @param das
	 * @param ddw
	 * @return
	 */
	public Location getInterpolatedLocation(double das, double ddw) {
		Preconditions.checkState(numRows > 1 && numCols > 1);
		double spacingAS = getGridSpacingAlongStrike();
		double spacingDD = getGridSpacingDownDip();
		double indexAS = das/spacingAS;
		int asIndexBefore = (int)indexAS;
		Preconditions.checkState(asIndexBefore >= 0 && asIndexBefore < numCols);
		double indexDD = ddw/spacingDD;
		int ddIndexBefore = (int)indexDD;
		Preconditions.checkState(ddIndexBefore >= 0 && ddIndexBefore < numRows);
		
		Location topLeftLoc = get(ddIndexBefore, asIndexBefore);
		double horzDist, horzAz;
		if (asIndexBefore == numCols-1) {
			// we're at the last column
			horzDist = 0;
			horzAz = 0;
		} else {
			Location topRightLoc = get(ddIndexBefore, asIndexBefore+1);
			horzDist = LocationUtils.horzDistance(topLeftLoc, topRightLoc);
			horzAz = LocationUtils.azimuthRad(topLeftLoc, topRightLoc);
		}
		
		double vertDist, vertAz, depthDelta;
		if (ddIndexBefore == numRows-1) {
			// we're at the last row
			vertDist = 0;
			vertAz = 0;
			depthDelta = 0;
		} else {
			Location botLeftLoc = get(ddIndexBefore+1, asIndexBefore);
			vertDist = LocationUtils.horzDistance(topLeftLoc, botLeftLoc);
			vertAz = LocationUtils.azimuthRad(topLeftLoc, botLeftLoc);
			depthDelta = botLeftLoc.getDepth()-topLeftLoc.getDepth();
		}
		
		double relativeVertPos = indexDD - ddIndexBefore;
		double relativeHorzPos = indexAS - asIndexBefore;
		
		// start top left
		Location loc = topLeftLoc;
		// move to the right
		loc = LocationUtils.location(loc, horzAz, horzDist*relativeHorzPos);
		// move down dip
		if ((float)vertDist > 0f)
			loc = LocationUtils.location(loc, vertAz, vertDist*relativeVertPos);
		// now actually move down
		return new Location(loc.getLatitude(), loc.getLongitude(), loc.getDepth()+depthDelta*relativeVertPos);
	}
	
	

	/**
	 * Maine method to test this class (found a bug using it)
	 * @param args
	 */
	public static void main(String args[]) {

		double test = 4%4.1;
		System.out.println(test);
		//        double aveDip = 15;
		//        double upperSeismogenicDepth = 9.1;
		//        double lowerSeismogenicDepth =15.2;
		//        double gridSpacing=1.0;
		//        FaultTrace faultTrace = new FaultTrace("Great Valley 13");
		//        // TO SEE THE POTENTIAL BUG IN THIS CLASS, CHANGE VALUE OF "faultTraceDepth" to 0
		//        double faultTraceDepth = 0;
		//        faultTrace.addLocation(new Location(36.3547, -120.358, faultTraceDepth));
		//        faultTrace.addLocation(new Location(36.2671, -120.254, faultTraceDepth));
		//        faultTrace.addLocation(new Location(36.1499, -120.114, faultTraceDepth));
		//        StirlingGriddedSurface griddedSurface = new StirlingGriddedSurface(faultTrace, aveDip,
		//        		upperSeismogenicDepth, lowerSeismogenicDepth, gridSpacing);
		//        System.out.println("******Fault Trace*********");
		//        System.out.println(faultTrace);
		//        Iterator it = griddedSurface.getLocationsIterator();
		//        System.out.println("*******Evenly Gridded Surface************");
		//        while(it.hasNext()){
		//            Location loc = (Location)it.next();
		//            System.out.println(loc.getLatitude()+","+loc.getLongitude()+","+loc.getDepth());
		//        }

		// for N-S strike and E dip, this setup showed that prior to fixing
		// LocationUtils.getLocation() the grid of the fault actually
		// starts to the left of the trace, rather than to the right.

		/*
        double aveDip = 30;
        double upperSeismogenicDepth = 5;
        double lowerSeismogenicDepth = 15;
        double gridSpacing=5;
        FaultTrace faultTrace = new FaultTrace("Test");
        faultTrace.add(new Location(20.0, -120, 0));
        faultTrace.add(new Location(20.2, -120, 0));
        StirlingGriddedSurface griddedSurface = new StirlingGriddedSurface(faultTrace, aveDip,
        		upperSeismogenicDepth, lowerSeismogenicDepth, gridSpacing);
        System.out.println("******Fault Trace*********");
        System.out.println(faultTrace);
        Iterator<Location> it = griddedSurface.getLocationsIterator();
        System.out.println("*******Evenly Gridded Surface************");
        while(it.hasNext()){
            Location loc = (Location)it.next();
            System.out.println(loc.getLatitude()+","+loc.getLongitude()+","+loc.getDepth());
        }
		 */
	}

	@Override
	protected AbstractEvenlyGriddedSurface getNewInstance() {
		StirlingGriddedSurface surf = new StirlingGriddedSurface();
		surf.setNumRowsAndNumCols(numRows, numCols);
		surf.aveDipDir = aveDipDir;
		surf.faultTrace = faultTrace;
		surf.aveDip = aveDip;
		surf.upperSeismogenicDepth = upperSeismogenicDepth;
		surf.lowerSeismogenicDepth = lowerSeismogenicDepth;
		surf.gridSpacingAlong = gridSpacingAlong;
		surf.gridSpacingDown = gridSpacingDown;
		surf.sameGridSpacing = sameGridSpacing;
		surf.name = name;
		return surf;
	}



}
