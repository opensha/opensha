package org.opensha.refFaultParamDb.dao.db;

import java.util.List;

import oracle.spatial.geometry.JGeometry;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;

public class SpatialUtils {

	// SRID
	private final static int SRID=8307;

	/**
	 * Create JGeomtery object from FaultTrace
	 * @param faultTrace
	 * @return
	 */
	public static JGeometry getMultiPointGeomtery(List<Location> locs) {
		int numLocations = locs.size();
		Object[] coords = new Object[numLocations];
		for(int j=0; j<numLocations; ++j) {
			Location loc= locs.get(j);
			double d[] = { loc.getLongitude(), loc.getLatitude()} ;
			coords[j] = d;
		}
		return JGeometry.createMultiPoint(coords, 2, SRID);
	}

	public static LocationList loadMultiPointGeometries(JGeometry geom, double depth) {
		LocationList locs = new LocationList();
		int numPoints = geom.getNumPoints();
		double[] ordinatesArray = geom.getOrdinatesArray();
		for(int j=0; j<numPoints; ++j) {
			locs.add(new Location(ordinatesArray[2*j+1], ordinatesArray[2*j], depth));
		}
		return locs;
	}
	
	public static JGeometry getSinglePointGeometry(Location loc) {
		double[] point = {loc.getLongitude(), loc.getLatitude()};
		return JGeometry.createPoint(point, 2, SRID);
	}
	
	public static Location loadSinglePointGeometry(JGeometry geom, double depth) {
		double[] pt = geom.getPoint();
		return new Location(pt[1], pt[0], depth);
	}

}
