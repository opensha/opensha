package org.opensha.sha.faultSurface;


import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.opensha.commons.exceptions.FaultException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.FaultUtils;


/**
 * <p>Title:  EvenlyGriddedSurfFromSimpleFaultData </p>
 *
 * <p>Description: This creates and EvenlyGriddedSurface from SimpleFaultData</p>
 *
 * @author Nitin Gupta
 * @version 1.0
 */
public abstract class EvenlyGriddedSurfFromSimpleFaultData
extends AbstractEvenlyGriddedSurfaceWithSubsets{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	// *********************
	/** @todo  Variables */
	// *********************

	/* Debbuging variables */
	protected final static String C = "EvenlyGriddedSurfFromSimpleFaultData";
	protected final static boolean D = false;

	protected FaultTrace faultTrace;
	protected double upperSeismogenicDepth = Double.NaN;
	protected double lowerSeismogenicDepth = Double.NaN;
	protected double aveDip;
	
	/**
	 * No are constructor needed by subclasses
	 */
	protected EvenlyGriddedSurfFromSimpleFaultData() {}

	/**
	 * This applies the grid spacing exactly as given, both along strike and down dip, clipping
	 * any remainder
	 * @param simpleFaultData
	 * @param gridSpacing
	 * @throws FaultException
	 */
	protected EvenlyGriddedSurfFromSimpleFaultData(SimpleFaultData simpleFaultData, double gridSpacing) throws FaultException {

		this(simpleFaultData.getFaultTrace(), simpleFaultData.getAveDip(),
				simpleFaultData.getUpperSeismogenicDepth(),
				simpleFaultData.getLowerSeismogenicDepth(), gridSpacing);

	}

	/**
	 * This applies the grid spacing exactly as given, both along strike and down dip, clipping
	 * any remainder
	 * @param faultTrace
	 * @param aveDip
	 * @param upperSeismogenicDepth
	 * @param lowerSeismogenicDepth
	 * @param gridSpacing
	 * @throws FaultException
	 */
	protected EvenlyGriddedSurfFromSimpleFaultData(FaultTrace faultTrace, double aveDip, double upperSeismogenicDepth,
			double lowerSeismogenicDepth, double gridSpacing) throws FaultException {
		set(faultTrace, aveDip, upperSeismogenicDepth, lowerSeismogenicDepth, gridSpacing, gridSpacing);
	}
	

	/**
	 * Stitch Together the fault sections. It assumes:
	 * 1. Sections are in correct order (in how they are to be stitched together)
	 * 2. Distance between adjacent points on neighboring sections (in correct order) 
	 * is less than distance to opposite ends of the sections.  In other words no sections
	 * overlap by more than half the section length.
	 * Each of the following are average over the sections (weight averaged by area): 
	 * upper and lower seismogenic depth, slip.  Total area of surface is maintained, 
	 * plus an addition area implied by gaps between neighboring sections.
	 * 
	 * @param simpleFaultDataList
	 * @param gridSpacing
	 * @throws FaultException
	 */
	protected EvenlyGriddedSurfFromSimpleFaultData(List<SimpleFaultData> simpleFaultDataList, double gridSpacing) {
		this(SimpleFaultData.getCombinedSimpleFaultData(simpleFaultDataList),gridSpacing);
	}

	
	
	/**
	 * This constructor will adjust the grid spacings along strike and down dip to exactly fill the surface
	 * (not cut off ends), leaving the grid spacings just less then the originals.
	 * @param simpleFaultData
	 * @param maxGridSpacingAlong - maximum grid spacing along strike
	 * @param maxGridSpacingDown - maximum grid spacing down dip
	 * @throws FaultException
	 */
	protected EvenlyGriddedSurfFromSimpleFaultData(SimpleFaultData simpleFaultData,
			double maxGridSpacingAlong, double maxGridSpacingDown) throws
			FaultException {

		this(simpleFaultData.getFaultTrace(), simpleFaultData.getAveDip(),
				simpleFaultData.getUpperSeismogenicDepth(), simpleFaultData.getLowerSeismogenicDepth(),
				maxGridSpacingAlong, maxGridSpacingDown);

	}

	/**
	 * This constructor will adjust the grid spacings along strike and down dip to exactly fill the surface
	 * (not cut off ends), leaving the grid spacings just less then the originals.
	 * @param faultTrace
	 * @param aveDip
	 * @param upperSeismogenicDepth
	 * @param lowerSeismogenicDepth
	 * @param maxGridSpacingAlong - maximum grid spacing along strike
	 * @param maxGridSpacingDown - maximum grid spacing down dip
	 * @throws FaultException
	 */
	protected EvenlyGriddedSurfFromSimpleFaultData(FaultTrace faultTrace, double aveDip,
			double upperSeismogenicDepth, double lowerSeismogenicDepth, double maxGridSpacingAlong,
			double maxGridSpacingDown) throws
			FaultException {
		
		double length = faultTrace.getTraceLength();
		double gridSpacingAlong = length/Math.ceil(length/maxGridSpacingAlong);
		double downDipWidth = (lowerSeismogenicDepth-upperSeismogenicDepth)/Math.sin(aveDip*Math.PI/180 );
		double gridSpacingDown = downDipWidth/Math.ceil(downDipWidth/maxGridSpacingDown);
/*		
		System.out.println(faultTrace.getName()+"\n\t"+
				maxGridSpacingAlong+"\t"+(float)gridSpacingAlong+"\t"+(float)gridSpacingDown+"\t"+
				(float)(faultTrace.getTraceLength()/gridSpacingAlong)+"\t"+
				(float)(downDipWidth/gridSpacingDown));
*/				

		set(faultTrace, aveDip, upperSeismogenicDepth, lowerSeismogenicDepth, gridSpacingAlong, gridSpacingDown);
	}
	

	/**
	 * Stitch Together the fault sections. It assumes:
	 * 1. Sections are in correct order (in how they are to be stitched together)
	 * 2. Distance between adjacent points on neighboring sections (in correct order) 
	 * is less than distance to opposite ends of the sections.  In other words no sections
	 * overlap by more than half the section length.
	 * Each of the following are average over the sections (weight averaged by area): 
	 * upper and lower seismogenic depth, slip.  Total area of surface is maintained, 
	 * plus an addition area implied by gaps between neighboring sections.
	 * 
	 * @param simpleFaultDataList
	 * @param maxGridSpacingAlong
	 * @param maxGridSpacingDown
	 * @throws FaultException
	 */
	protected EvenlyGriddedSurfFromSimpleFaultData(List<SimpleFaultData> simpleFaultDataList, 
			double maxGridSpacingAlong, double maxGridSpacingDown) {
		
		this(SimpleFaultData.getCombinedSimpleFaultData(simpleFaultDataList), maxGridSpacingAlong, maxGridSpacingDown);
	}



	protected void set(FaultTrace faultTrace, double aveDip, double upperSeismogenicDepth,
			double lowerSeismogenicDepth, double gridSpacingAlong, double gridSpacingDown)	{
		this.faultTrace =faultTrace;
		this.aveDip =aveDip;
		this.upperSeismogenicDepth = upperSeismogenicDepth;
		this.lowerSeismogenicDepth =lowerSeismogenicDepth;
		this.gridSpacingAlong = gridSpacingAlong;
		this.gridSpacingDown = gridSpacingDown;
		this.sameGridSpacing = true;
		if(gridSpacingDown != gridSpacingAlong) sameGridSpacing = false;
	}


	// ***************************************************************
	/** @todo  Serializing Helpers - overide to increase performance */
	// ***************************************************************


	public FaultTrace getFaultTrace() { return faultTrace; }

	public double getUpperSeismogenicDepth() { return upperSeismogenicDepth; }

	public double getLowerSeismogenicDepth() { return lowerSeismogenicDepth; }


	/**
	 * This method checks the simple-fault data to make sure it's all OK.
	 * @throws FaultException
	 */
	protected void assertValidData() throws FaultException {

		if( faultTrace == null ) throw new FaultException(C + "Fault Trace is null");

		FaultUtils.assertValidDip(aveDip);
		FaultUtils.assertValidSeisUpperAndLower(upperSeismogenicDepth, lowerSeismogenicDepth);

		if( gridSpacingAlong == Double.NaN ) throw new FaultException(C + "invalid gridSpacing");

		double depth = faultTrace.get(0).getDepth();
		if(depth > upperSeismogenicDepth)
			throw new FaultException(C + " depth on faultTrace ("+faultTrace.getName()+") locations must be < upperSeisDepth; depth="+
		depth+"; upperSeismogenicDepth="+upperSeismogenicDepth);

		Iterator<Location> it = faultTrace.iterator();
		while(it.hasNext()) {
			if(it.next().getDepth() != depth){
				throw new FaultException(C + ":All depth on faultTrace locations must be equal");
			}
		}
	}
	
	@Override
	public double getAveDip() {
		return aveDip;
	}

	@Override
	public double getAveRupTopDepth() {
		return upperSeismogenicDepth;
	}

	@Override
	public double getAveRupBottomDepth() {
		return lowerSeismogenicDepth;
	}

	@Override
	public double getAveStrike() {
		return faultTrace.getAveStrike();
	}

	@Override
	public FaultTrace getUpperEdge() {
		// check that the location depths in faultTrace are same as upperSeismogenicDepth
		double aveTraceDepth = 0;
		for(Location loc:faultTrace)
			aveTraceDepth += loc.getDepth();
		aveTraceDepth /= faultTrace.size();
		double diff = Math.abs(aveTraceDepth-upperSeismogenicDepth); // km
		if(diff < 0.001)
			return faultTrace;
		else
			throw new RuntimeException(" method not yet implemented where depths in the " +
					"trace differ from upperSeismogenicDepth (and projecting will cleate " +
					"loops for FrankelGriddedSurface projections; aveTraceDepth="+aveTraceDepth+
					"\tupperSeismogenicDepth="+upperSeismogenicDepth);
	}

}
