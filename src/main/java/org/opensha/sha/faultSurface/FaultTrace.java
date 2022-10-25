package org.opensha.sha.faultSurface;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.commons.io.IOUtils;
import org.opensha.commons.data.Named;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.util.FaultUtils;

// Fix - Needs more comments


/**
 *  <b>Title:</b> FaultTrace<p>
 *
 *  <b>Description:</b> This simply contains a vector (or array) of Location
 *  objects representing the top trace of a fault (with non-zero depth if it
 *  buried). <p>
 *
 * @author     Sid Hellman, Steven W. Rock
 * @created    February 26, 2002
 * @version    1.0
 */

public class FaultTrace extends LocationList implements Named {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/**
	 *  Fault name field
	 */
	private String faultName;


	public FaultTrace(String faultName){
		super();
		this.faultName = faultName;

	}

	public void setName( String faultName ) { this.faultName = faultName; }

	public String getName() { return faultName; }

	public int getNumLocations() { return size(); }

	/**
	 * This returns the total fault-trace length in km
	 * @return
	 */
	public double getTraceLength() {
		double totLength = 0;
		Iterator<Location> it = iterator();
		Location lastLoc = it.next();
		Location loc = null;
		while( it.hasNext() ){
			loc = it.next();
			totLength += LocationUtils.horzDistance(lastLoc, loc);
			lastLoc = loc;
		}
		return totLength;
	}

	/**
	 * This returns the average strike (weight average by length).
	 * <br>
	 * <br>Note that this method is significantly slower than {@link #getStrikeDirection()}, while 
	 * providing almost identical results (typically ~0.08 degrees). See 
	 * <a href="https://opensha.org/trac/wiki/StrikeDirectionMethods">StrikeDirectionMethods</a> for more
	 * information.
	 * 
	 * @return
	 */
	public double getAveStrike() {
		ArrayList<Double> azimuths = new ArrayList<Double>();
		for (int i=1; i<size(); i++) {
			azimuths.add(LocationUtils.azimuth(get(i-1),get(i)));
		}
		
		return FaultUtils.getLengthBasedAngleAverage(this, azimuths);

	}

	/**
	 * This returns the strike direction (between 0 and 360 degrees) defined by the first and last points only.
	 * <br><br>
	 * It is significantly faster than {@link #getAveStrike()} and returns almost identical results,
	 * typically ~0.08 degrees. See 
	 * <a href="https://opensha.org/trac/wiki/StrikeDirectionMethods">StrikeDirectionMethods</a> for more
	 * information.
	 * 
	 * @return strike direction
	 */
	public double getStrikeDirection() {
		return LocationUtils.azimuth(get(0), get(size()-1));
	}
	
	/**
	 * This returns the dip direction (between 0 and 360 degrees) defined by stike direction + 90.
	 * 
	 * @return dip direction in degrees
	 * @see getStrikeDirection
	 */
	public double getDipDirection() {
		double dipDir = getStrikeDirection() + 90;
		while (dipDir > 360d)
			dipDir -= 360d;
		return dipDir;
	}



	/**
	 * This returns the change in strike direction in going from this trace to the one passed in 
	 * (input_trace_azimuth-this_azimuth), where this accounts the change in sign for azimuths at
	 * 180 degrees.  The output is between -180 and 180 degress).
	 * @return
	 */
	public double getStrikeDirectionDifference(FaultTrace trace) {
		double diff = trace.getStrikeDirection() - this.getStrikeDirection();
		if(diff>180)
			return diff-360;
		else if (diff<-180)
			return diff+360;
		else
			return diff;
	}



	/*
	 * Calculates  minimum distance of this faultTrace from the user provided fault trace,
	 * where the latter is resampled at discrInterval (km) for computing distances.
	 * Returns the distance in km.
	 * 
	 * @param faultTrace FaultTrace from where distance needs to be calculated
	 * @param discrInterval resampling interval (km)
	 */
	public double getMinDistance(FaultTrace faultTrace, double discrInterval) {
		// calculate the minimum fault trace distance
		double minFaultTraceDist = Double.POSITIVE_INFINITY;
		double dist;
		int num = (int)(faultTrace.getTraceLength()/discrInterval) + 1;
		FaultTrace discrFaultTrace = FaultUtils.resampleTrace(faultTrace, num);
		for(int i=0; i<discrFaultTrace.getNumLocations(); ++i) {
			dist = minDistToLine(discrFaultTrace.get(i));
			if(dist<minFaultTraceDist) minFaultTraceDist = dist;
		}
		return minFaultTraceDist;
	}


//	private final static String TAB = "  ";
//	public String toString(){
//
//		StringBuffer b = new StringBuffer("FaultTrace");
//		b.append('\n');
//		b.append(TAB + "Name = " + faultName);
//
//		b.append( super.toString() ) ;
//		return b.toString();
//
//	}

	@Override
	public String toString() {
		// @formatter:off
		StringBuffer b = new StringBuffer()
			.append("Fault Trace: ").append(faultName)
			.append(IOUtils.LINE_SEPARATOR)
			.append("       size: ").append(size())
			.append(IOUtils.LINE_SEPARATOR)
			.append("Locations: ");
		for (Location loc : this) {
			b.append(loc).append(IOUtils.LINE_SEPARATOR)
			.append("           ");
		}
		return b.toString();
		// @formatter:on
	}

	public FaultTrace clone() {
		FaultTrace trace = new FaultTrace(this.getName());
		for (Location loc : this) {
			trace.add(loc);
		}
		return trace;
	}


}
