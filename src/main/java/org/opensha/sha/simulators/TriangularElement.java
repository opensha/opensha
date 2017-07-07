package org.opensha.sha.simulators;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;

public class TriangularElement extends SimulatorElement {
	
	private Location center;
	private double[] lengths; // in meters
	
	private TriangularElementSurface surf;

	public TriangularElement(int id, Vertex[] vertices, String sectionName, int faultID, int sectionID,
			int numAlongStrike, int numDownDip, double slipRate, double aseisFactor, FocalMechanism focalMechanism) {
		super(id, vertices, sectionName, faultID, sectionID, numAlongStrike, numDownDip, slipRate, aseisFactor, focalMechanism);
		Preconditions.checkArgument(vertices.length == 3, "TriangularElement: vertices.length should equal 3");
	}
	
	private synchronized double[] getLengths() {
		if (lengths == null) {
			lengths = new double[3];
			for (int i=0; i<3; i++) {
				int i1 = i;
				int i2 = i == 2 ? 0 : i+1;
				lengths[i] = LocationUtils.linearDistanceFast(vertices[i1], vertices[i2])*1000d; // in meters
			}
		}
		return lengths;
	}

	@Override
	public synchronized double getArea() {
		// calculate in m^2
		double[] lengths = getLengths();
		// use Heron's formula
		double s = 0.5 * (lengths[0] + lengths[1] + lengths[2]);
		return Math.sqrt(s * (s-lengths[0]) * (s-lengths[1]) * (s-lengths[2]));
	}

	@Override
	public double getAveDAS() {
		return (vertices[0].getDAS()+vertices[1].getDAS()+vertices[2].getDAS())/3.0;
	}

	@Override
	public double getAveDepth() {
		return getCenterLocation().getDepth();
	}

	@Override
	public synchronized RuptureSurface getSurface() {
		if (surf == null) {
			Vertex[] v = getVertices();
			surf = new TriangularElementSurface(v[0], v[1], v[2]);
		}
		return surf;
	}
	
	/**
	 * This returns the average/center location
	 * @return
	 */
	public synchronized Location getCenterLocation() {
		if (center == null) {
			double aveLat = (vertices[0].getLatitude()+vertices[1].getLatitude()+vertices[2].getLatitude())/3.0;
			double aveLon = (vertices[0].getLongitude()+vertices[1].getLongitude()+vertices[2].getLongitude())/3.0;
			double aveDep = (vertices[0].getDepth()+vertices[1].getDepth()+vertices[2].getDepth())/3.0;
			center = new Location(aveLat,aveLon,aveDep);
		}
		return center;
	}

}
