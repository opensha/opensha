package org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.oldClasses;

import java.util.ArrayList;
import java.util.Iterator;

import org.opensha.commons.exceptions.FaultException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.sha.faultSurface.AbstractEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurfFromSimpleFaultData;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.SimpleFaultData;

/**
 * This class ensures backwards compatibility of UCERF2 as math has changed with some location/surface gridding methods
 * @author kevin
 *
 */
public class UCERF2_Final_StirlingGriddedSurface extends EvenlyGriddedSurfFromSimpleFaultData {
	
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
	public UCERF2_Final_StirlingGriddedSurface(SimpleFaultData simpleFaultData, double gridSpacing) throws FaultException {
		super(simpleFaultData, gridSpacing);
		this.aveDipDir=simpleFaultData.getAveDipDir();
		createEvenlyGriddedSurface();
	}
	
	/**
	 * This applies the  grid spacing exactly as given (trimming any remainder from the ends),
	 * and applies the ave-dip direction as computed from the faultTrace.
	 */
	public UCERF2_Final_StirlingGriddedSurface(FaultTrace faultTrace, double aveDip, double upperSeismogenicDepth,
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
	public UCERF2_Final_StirlingGriddedSurface(SimpleFaultData simpleFaultData, double maxGridSpacingAlong, double maxGridSpacingDown) throws FaultException {
		super(simpleFaultData, maxGridSpacingAlong, maxGridSpacingDown);
		this.aveDipDir=simpleFaultData.getAveDipDir();
		createEvenlyGriddedSurface();
	}

	/**
	 * This applies the  grid spacing exactly as given (trimming any remainder from the ends),
	 * and applies the given ave-dip direction (rather than computing this from the the faultTrace).
	 */
	public UCERF2_Final_StirlingGriddedSurface( FaultTrace faultTrace, double aveDip, double upperSeismogenicDepth,
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
	public UCERF2_Final_StirlingGriddedSurface(ArrayList<SimpleFaultData> simpleFaultData,
			double gridSpacing) throws FaultException {

		super(getCombinedSimpleFaultData(simpleFaultData), gridSpacing);
		createEvenlyGriddedSurface();
	}
	
	/**
     * Get a single combined simpleFaultData from multiple SimpleFaultData
     * @param simpleFaultDataList
     * @return
     */
    public static SimpleFaultData getCombinedSimpleFaultData(ArrayList<SimpleFaultData> simpleFaultDataList) {
    	if(simpleFaultDataList.size()==1) {
    		return simpleFaultDataList.get(0);
    	}
    	// correctly order the first fault section
    	FaultTrace faultTrace1 = simpleFaultDataList.get(0).getFaultTrace();
    	FaultTrace faultTrace2 =  simpleFaultDataList.get(1).getFaultTrace();
    	double minDist = Double.MAX_VALUE, distance;
    	boolean reverse = false;
    	ArrayList<Integer> reversedIndices = new ArrayList<Integer>();
    	distance = UCERF2_Final_RelativeLocation.getHorzDistance(faultTrace1.get(0), faultTrace2.get(0));
    	if(distance<minDist) {
    		minDist = distance;
    		reverse=true;
    	}
    	distance = UCERF2_Final_RelativeLocation.getHorzDistance(faultTrace1.get(0), faultTrace2.get(faultTrace2.getNumLocations()-1));
    	if(distance<minDist) {
    		minDist = distance;
    		reverse=true;  
    	}
    	distance = UCERF2_Final_RelativeLocation.getHorzDistance(faultTrace1.get(faultTrace1.getNumLocations()-1), faultTrace2.get(0));
    	if(distance<minDist) {
    		minDist = distance;
    		reverse=false;
    	}
    	distance = UCERF2_Final_RelativeLocation.getHorzDistance(faultTrace1.get(faultTrace1.getNumLocations()-1), faultTrace2.get(faultTrace2.getNumLocations()-1));
    	if(distance<minDist) {
    		minDist = distance;
    		reverse=false;
    	}
    	if(reverse) {
    		reversedIndices.add(0);
    		faultTrace1.reverse();
    		if( simpleFaultDataList.get(0).getAveDip()!=90)  simpleFaultDataList.get(0).setAveDip(- simpleFaultDataList.get(0).getAveDip());
    	}
    	
    	// Calculate Upper Seis Depth, Lower Seis Depth and Dip
    	double combinedDip=0, combinedUpperSeisDepth=0, totArea=0, totLength=0;
    	FaultTrace combinedFaultTrace = new FaultTrace("Combined Fault Sections");
    	int num = simpleFaultDataList.size();
    	
    	for(int i=0; i<num; ++i) {
    		FaultTrace faultTrace = simpleFaultDataList.get(i).getFaultTrace();
    		int numLocations = faultTrace.getNumLocations();
    		if(i>0) { // check the ordering of point in this fault trace
    			FaultTrace prevFaultTrace = simpleFaultDataList.get(i-1).getFaultTrace();
    			Location lastLoc = prevFaultTrace.get(prevFaultTrace.getNumLocations()-1);
    			double distance1 = UCERF2_Final_RelativeLocation.getHorzDistance(lastLoc, faultTrace.get(0));
    			double distance2 = UCERF2_Final_RelativeLocation.getHorzDistance(lastLoc, faultTrace.get(faultTrace.getNumLocations()-1));
    			if(distance2<distance1) { // reverse this fault trace
    				faultTrace.reverse();
    				reversedIndices.add(i);
    				if(simpleFaultDataList.get(i).getAveDip()!=90) simpleFaultDataList.get(i).setAveDip(-simpleFaultDataList.get(i).getAveDip());
    			}
    			//  remove any loc that is within 1km of its neighbor
            	//  as per Ned's email on Feb 7, 2007 at 5:53 AM
        		if(distance2>1 && distance1>1) combinedFaultTrace.add(faultTrace.get(0).clone());
        		// add the fault Trace locations to combined trace
        		for(int locIndex=1; locIndex<numLocations; ++locIndex) 
        			combinedFaultTrace.add(faultTrace.get(locIndex).clone());
       
    		} else { // if this is first fault section, add all points in fault trace
//    			 add the fault Trace locations to combined trace
        		for(int locIndex=0; locIndex<numLocations; ++locIndex) 
        			combinedFaultTrace.add(faultTrace.get(locIndex).clone());
    		}
    		double length = UCERF2_Final_RelativeLocation.getOldFaultLength(faultTrace);
//    		double length = faultTrace.getTraceLength();
    		double dip = simpleFaultDataList.get(i).getAveDip();
    		double area = Math.abs(length*(simpleFaultDataList.get(i).getLowerSeismogenicDepth()-simpleFaultDataList.get(i).getUpperSeismogenicDepth())/Math.sin(dip*Math.PI/ 180));
    		totLength+=length;
    		totArea+=area;
    		combinedUpperSeisDepth+=(area*simpleFaultDataList.get(i).getUpperSeismogenicDepth());
    		if(dip>0)
    			combinedDip += (area * dip);
    		else combinedDip+=(area*(dip+180));
    		//System.out.println(dip+","+area+","+combinedDip+","+totArea);
    	}
    	
    	// Revert back the fault traces that were reversed
    	for(int i=0; i<reversedIndices.size(); ++i) {
    		int index = reversedIndices.get(i);
    		simpleFaultDataList.get(index).getFaultTrace().reverse();
    		if(simpleFaultDataList.get(index).getAveDip()!=90) simpleFaultDataList.get(index).setAveDip(- simpleFaultDataList.get(index).getAveDip());
    	}

    	
    	double dip = combinedDip/totArea;
    	
    	//double tolerance = 1e-6;
		//if(dip-90 < tolerance) dip=90;
//   	 if Dip<0, reverse the trace points to follow Aki and Richards convention
    	if(dip>90) {
    		dip=(180-dip);
    		combinedFaultTrace.reverse();
    	}
    	
    	//System.out.println(dip);
    	
    	SimpleFaultData simpleFaultData = new SimpleFaultData();
    	simpleFaultData.setAveDip(dip);
    	double upperSeismogenicDepth = combinedUpperSeisDepth/totArea;
    	simpleFaultData.setUpperSeismogenicDepth(upperSeismogenicDepth);
    	
    	for(int i=0; i<combinedFaultTrace.getNumLocations(); ++i) {
    		//combinedFaultTrace.getLocationAt(i).setDepth(
    		//		simpleFaultData.getUpperSeismogenicDepth());
    		// replace trace Locations with depth corrected values
    		Location old = combinedFaultTrace.get(i);
    		Location loc = new Location(
    				old.getLatitude(), 
    				old.getLongitude(),
    				upperSeismogenicDepth);
    		combinedFaultTrace.set(i, loc);
    	}
    	simpleFaultData.setLowerSeismogenicDepth((totArea/totLength)*Math.sin(dip*Math.PI/180)+upperSeismogenicDepth);
    	//System.out.println(simpleFaultData.getLowerSeismogenicDepth());
    	simpleFaultData.setFaultTrace(combinedFaultTrace);
    	return simpleFaultData;
 
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
				dir = UCERF2_Final_RelativeLocation.getDirection(lastLoc, loc);

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
				Location traceLocation = UCERF2_Final_RelativeLocation.getLocation( location1, dir  );

				// get location at the top of the fault surface
				Location topLocation;
				if(traceLocation.getDepth() < upperSeismogenicDepth) {
					vDistance = traceLocation.getDepth() - upperSeismogenicDepth;
//					vDistance = upperSeismogenicDepth - traceLocation.getDepth();
					hDistance = vDistance / Math.tan( avDipRadians );
					//                dir = new LocationVector(vDistance, hDistance, aveDipDirection, 0);
					dir = new LocationVector(aveDipDirection, hDistance, vDistance);
					topLocation = UCERF2_Final_RelativeLocation.getLocation( traceLocation, dir );
				}
				else
					topLocation = traceLocation;

				set(0, ith_col, topLocation.clone());
				if( D ) System.out.println(S + "(x,y) topLocation = (0, " + ith_col + ") " + topLocation );

				// Loop over each row - calculating location at depth along the fault trace
				ith_row = 1;
				while(ith_row < rows){

					if( D ) System.out.println(S + "ith_row = " + ith_row);

					// Calculate location at depth and put into grid
					hDistance = ith_row * gridSpacingCosAveDipRadians;
					vDistance = -ith_row * gridSpacingSinAveDipRadians;
//					vDistance = ith_row * gridSpacingSinAveDipRadians;

					//                dir = new LocationVector(vDistance, hDistance, aveDipDirection, 0);
					dir = new LocationVector(aveDipDirection, hDistance, vDistance);

					Location depthLocation = UCERF2_Final_RelativeLocation.getLocation( topLocation, dir );
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
	public double getAveDipDirection() {
		return aveDipDir;
	}

	@Override
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

	@Override
	protected AbstractEvenlyGriddedSurface getNewInstance() {
		throw new UnsupportedOperationException("Not supported");
	}


}
