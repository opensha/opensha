package org.opensha.sha.simulators;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.faultSurface.FourPointEvenlyGriddedSurface;

import com.google.common.base.Preconditions;

public class RectangularElement extends SimulatorElement {
	
	private boolean perfect;

	/**
	 * This creates the RectangularElement from the supplied information.  Note that this assumes
	 * the vertices correspond to a perfect rectangle.
	 * @param id - an integer for identification (referred to as "index" in the EQSIM documentation)
	 * @param vertices - a list of 4 vertices, where the order is as follows as viewed 
	 *                   from the positive side of the fault: 0th is top left, 1st is lower left,
	 *                   2nd is lower right, and 3rd is upper right (counter clockwise)
	 * @param sectionName - the name of the fault section that this element is on
	 * @param faultID - the ID of the original fault (really needed?)
	 * @param sectionID - the ID of the associated fault section
	 * @param numAlongStrike - index along strike on the fault section
	 * @param numDownDip - index down dip
	 * @param slipRate - slip rate (meters/year; note that this is different from the EQSIM convention (which is m/s))
	 * @param aseisFactor - aseismicity factor
	 * @param focalMechanism - this contains the strike, dip, and rake
	 */
	public RectangularElement(int id, Vertex[] vertices, String sectionName, int faultID, int sectionID,
			int numAlongStrike, int numDownDip, double slipRate, double aseisFactor, FocalMechanism focalMechanism,
			boolean perfectRect) {
		super(id, vertices, sectionName, faultID, sectionID, numAlongStrike, numDownDip, slipRate, aseisFactor, focalMechanism);
		Preconditions.checkArgument(vertices.length == 4, "RectangularElement: vertices.length should equal 4");
		
		this.perfect = perfectRect;
		this.perfect = true;
	}
	
	@Override
	public FourPointEvenlyGriddedSurface getSurface() {
		return new FourPointEvenlyGriddedSurface(vertices[0],vertices[1],vertices[2],vertices[3]);
	}

	/**
	 * This tells whether it's a perfect rectangle
	 * @return
	 */
	public boolean isPerfect() {
		return perfect;
	}
	
	public int getPerfectInt() {
		if(perfect) return 1;
		else return 0;
	}
	
	@Override
	public double getArea() {
		return LocationUtils.linearDistance(vertices[0], vertices[1])*LocationUtils.linearDistance(vertices[1], vertices[2])*1e6;
	}
	
	@Override
	public double getAveDAS() {
		return (vertices[0].getDAS()+vertices[1].getDAS()+vertices[2].getDAS()+vertices[3].getDAS())/4.0;
	}
	
	@Override
	public double getAveDepth() {
		return (vertices[0].getDepth()+vertices[1].getDepth()+vertices[2].getDepth()+vertices[3].getDepth())/4.0;
	}
	
	/**
	 * This returns the average/center location defined by averaging lats, 
	 * lons, and depths of the four vertices
	 * @return
	 */
	@Override
	public Location getCenterLocation() {
		double aveLat = (vertices[0].getLatitude()+vertices[1].getLatitude()+vertices[2].getLatitude()+vertices[3].getLatitude())/4.0;
		double aveLon = (vertices[0].getLongitude()+vertices[1].getLongitude()+vertices[2].getLongitude()+vertices[3].getLongitude())/4.0;
		double aveDep = (vertices[0].getDepth()+vertices[1].getDepth()+vertices[2].getDepth()+vertices[3].getDepth())/4.0;
		return new Location(aveLat,aveLon,aveDep);
	}
	
	public String toWardFormatLine() {
		// this is Steve's ordering
		Location newTop1 = vertices[0];
		Location newTop2 = vertices[3];
		Location newBot1 = vertices[1];
		Location newBot2 = vertices[2];
		FocalMechanism focalMechanism = getFocalMechanism();
		String line = getID() + "\t"+
			getNumAlongStrike() + "\t"+
			getNumDownDip() + "\t"+
			getFaultID() + "\t"+
			getSectionID() + "\t"+
			(float)getSlipRate() + "\t"+
			"NA" + "\t"+  // elementStrength not available
			(float)focalMechanism.getStrike() + "\t"+
			(float)focalMechanism.getDip() + "\t"+
			(float)focalMechanism.getRake() + "\t"+
			(float)newTop1.getLatitude() + "\t"+
			(float)newTop1.getLongitude() + "\t"+
			(float)newTop1.getDepth()*-1000 + "\t"+
			(float)newBot1.getLatitude() + "\t"+
			(float)newBot1.getLongitude() + "\t"+
			(float)newBot1.getDepth()*-1000 + "\t"+
			(float)newBot2.getLatitude() + "\t"+
			(float)newBot2.getLongitude() + "\t"+
			(float)newBot2.getDepth()*-1000 + "\t"+
			(float)newTop2.getLatitude() + "\t"+
			(float)newTop2.getLongitude() + "\t"+
			(float)newTop2.getDepth()*-1000 + "\t"+
			getSectionName();
		return line;
	}

}
