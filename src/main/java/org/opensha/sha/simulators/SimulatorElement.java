package org.opensha.sha.simulators;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.faultSurface.FourPointEvenlyGriddedSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

/**
 * This uses "id" rather than "index" to avoid confusion from the fact that our indexing 
 * starts from zero (and that in EQSIM docs starts from 1)
 * @author field
 *
 */
public abstract class SimulatorElement {
	
	// these are the official variables
	private int id;	// this is referred to as "index" in the EQSIM documentation
	protected Vertex[] vertices;
	private FocalMechanism focalMechanism;
	private double slipRate;
	private double aseisFactor;

	// these are other variable (e.g., used by Ward's simulator)
	private String sectionName;
	private int sectionID;	
	private int faultID;
	private int numAlongStrike;
	private int numDownDip;
	
	/**
	 * This creates the SimulatorElement from the supplied information.
	 * @param id - an integer for identification (referred to as "index" in the EQSIM documentation)
	 * @param vertices - a list of vertices
	 * @param sectionName - the name of the fault section that this element is on
	 * @param faultID - the ID of the original fault (really needed?)
	 * @param sectionID - the ID of the associated fault section
	 * @param numAlongStrike - index along strike on the fault section
	 * @param numDownDip - index down dip
	 * @param slipRate - slip rate (meters/year; note that this is different from the EQSIM convention (which is m/s))
	 * @param aseisFactor - aseismicity factor
	 * @param focalMechanism - this contains the strike, dip, and rake
	 */
	public SimulatorElement(int id, Vertex[] vertices, String sectionName,
			int faultID, int sectionID, int numAlongStrike, int numDownDip,
			double slipRate, double aseisFactor, FocalMechanism focalMechanism) {
		this.id = id;
		this.vertices = vertices;
		this.sectionName = sectionName;
		this.faultID = faultID;
		this.sectionID = sectionID;
		this.numAlongStrike = numAlongStrike;
		this.numDownDip = numDownDip;
		this.slipRate = slipRate;
		this.aseisFactor = aseisFactor;
		this.focalMechanism = focalMechanism;
	}
	
	public abstract RuptureSurface getSurface();
	
	/**
	 * This returns the section name for now
	 * @return
	 */
	public String getName() {
		return sectionName;
	}
	
	/**
	 * This computes and returns the area (m-sq)
	 * @return
	 */
	public abstract double getArea();

	public int getID() {
		return id;
	}

	public Vertex[] getVertices() {
		return vertices;
	}
	
	public FocalMechanism getFocalMechanism() {
		return focalMechanism;
	}
	
	public double getSlipRate() {
		return slipRate;
	}

	public double getAseisFactor() {
		return aseisFactor;
	}
	
	public String getSectionName() {
		return sectionName;
	}

	public int getSectionID() {
		return sectionID;
	}
	
	public int getFaultID() {
		return faultID;
	}
	
	public void setFaultID(int faultID) {
		this.faultID = faultID;
	}
	
	public void setNumAlongStrike(int numAlongStrike) {
		this.numAlongStrike = numAlongStrike;
	}
	
	public int getNumAlongStrike() {
		return numAlongStrike;
	}
	
	public void setNumDownDip(int numDownDip) {
		this.numDownDip = numDownDip;
	}

	public int getNumDownDip() {
		return numDownDip;
	}
	
	/**
	 * This returns the average DAS (in km) of all four vertices
	 * @return
	 */
	public abstract double getAveDAS();
	
	/**
	 * This returns the average depth (in km, positive is down)
	 * @return
	 */
	public abstract double getAveDepth();
	
	/**
	 * This returns the average/center location
	 * @return
	 */
	public abstract Location getCenterLocation();
	
	/**
	 * This returns the minimum DAS (in km) among all vertices
	 * @return
	 */
	public double getMinDAS() {
		double min = Double.POSITIVE_INFINITY;
		for (Vertex vertex : vertices)
			min = Math.min(min, vertex.getDAS());
		return min;
	}
	
	/**
	 * This returns the maximum DAS (in km) among all vertices
	 * @return
	 */
	public double getMaxDAS() {
		double max = Double.NEGATIVE_INFINITY;
		for (Vertex vertex : vertices)
			max = Math.max(max, vertex.getDAS());
		return max;
	}

	
	
	/**
	 * This returns the vertex corresponding to the minimum DAS
	 * @return
	 */
	public Vertex getVertexForMinDAS() {
		int minIndex = -1;
		double min = Double.POSITIVE_INFINITY;
		for (int i=0; i<vertices.length; i++) {
			if (vertices[i].getDAS() < min) {
				min = vertices[i].getDAS();
				minIndex = i;
			}
		}
		return vertices[minIndex];
	}
	
	/**
	 * This returns the vertex corresponding to the maximum DAS
	 * @return
	 */
	public Vertex getVertexForMaxDAS() {
		int maxIndex = -1;
		double max = Double.NEGATIVE_INFINITY;
		for (int i=0; i<vertices.length; i++) {
			if (vertices[i].getDAS() > max) {
				max = vertices[i].getDAS();
				maxIndex = i;
			}
		}
		return vertices[maxIndex];
	}

}
