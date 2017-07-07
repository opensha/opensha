/**
 * 
 */
package org.opensha.sha.simulators;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;

/**
 * This class represents a Vertex as defined by the EQSIM v04 specification, except that depth and 
 * distance along fault (DAS) here are in km rather than m.  Depth is also positive here (but
 * negative in the EQSIM specs).  This uses "id" rather than "index" to avoid confusion from the 
 * fact that our indexing starts from zero (and that in EQSIM docs starts from 1)
 * @author field
 *
 */
public class Vertex extends Location {
	
	private int id;  // this is referred to as "index" in the EQSIM documentation
	private int traceFlag;
	private double das;
	
	
	/**
	 * 
	 * @param latitute
	 * @param longitude
	 * @param depth - in km as customary in OpenSHA (which is different from the EQSIM convention!)
	 * @param id - this is an integer ID for this vertex (referred to as "index" in EQSIM docs)
	 * @param das - distance along trace in km (km is different from EQSIM convention!)
	 * @param traceFlag - tells whether is on the fault trace  (0 means no; 1 means yes, but not
	 * 		              the first or last point; 2 means yes & it's the first; and 3 means yes 
	 *                    & it's the last point)
	 */
	public Vertex(double latitute,double longitude,double depth, int id, double das, int traceFlag) {
		super(latitute, longitude, depth);
		this.id=id;
		this.das=das;
		this.traceFlag=traceFlag;
	}
	
	
	/**
	 * This constructor takes a location object
	 * @param loc
	 * @param id - this is an integer ID for this vertex (referred to as "index" in EQSIM docs)
	 * @param das - distance along trace in km (km is different from EQSIM convention!)
	 * @param traceFlag - tells whether is on the fault trace  (0 means no; 1 means yes, but not
	 * 		              the first or last point; 2 means yes & it's the first; and 3 means yes 
	 *                    & it's the last point)
	 */
	public Vertex(Location loc, int id, double das, int traceFlag) {
		super(loc.getLatitude(), loc.getLongitude(), loc.getDepth());
		this.id=id;
		this.das=das;
		this.traceFlag=traceFlag;
	}

	
	public Vertex(double latitute,double longitude,double depth) {
		super(latitute, longitude, depth);
		das = Double.NaN;
		id = -1;
		traceFlag = -1;
	}
	
	
	public Vertex(double latitute,double longitude,double depth, int id) {
		super(latitute, longitude, depth);
		das = Double.NaN;
		this.id = id;
		traceFlag = -1;
	}


	public Vertex(Location loc) {
		super(loc.getLatitude(), loc.getLongitude(), loc.getDepth());
		das = Double.NaN;
		id = -1;
		traceFlag = -1;
	}


	public int getID() {return id;}
	
	/**
	 * Note that DAS here is in km (whereas it's m in the EQSIM specs)
	 * @return
	 */
	public double getDAS() {return das;}

	/**
	 * tells whether is on the fault trace  (0 means no; 1 means yes, but not
	 * the first or last point; 2 means yes & it's the first; and 3 means yes 
	 * & it's the last point)
	 * @return
	 */
	public int getTraceFlag() {return traceFlag;}
	
	/**
	 * This returns the linear distance (km) of this vertex to the given location
	 * @param loc
	 * @return
	 */
	public double getLinearDistance(Location loc) {
		return LocationUtils.linearDistance(loc, this);
	}
	
	@Override
	/**
	 * This returns true if the IDs are equale
	 */
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Vertex)) return false;
		Vertex vert = (Vertex) obj;
		if(vert.getID() == this.getID())
			return true;
		else
			return false;
	}

	
}
