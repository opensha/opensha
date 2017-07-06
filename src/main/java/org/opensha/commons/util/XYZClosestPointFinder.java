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

package org.opensha.commons.util;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.opensha.commons.data.xyz.ArbDiscrGeoDataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;

/**
 * This class takes the path to a Generic Mapping Tools style XYZ file and loads in all of the
 * locations and values. It then allows one to find the value from the file that is closest to
 * a given location.
 * 
 * @author kevin
 *
 */
public class XYZClosestPointFinder {
	private GeoDataSet dataset;
	
	public XYZClosestPointFinder(GeoDataSet dataset){
		this.dataset = dataset;
	}
	
	public XYZClosestPointFinder(String fileName, boolean latitudeX) throws FileNotFoundException, IOException {
		this(ArbDiscrGeoDataSet.loadXYZFile(fileName, latitudeX));
	}
	
	/**
	 * Returns the value at the closest location in the XYZ file (no matter how far
	 * away the closest point is).
	 * 
	 * @param loc
	 * @return
	 */
	public double getClosestVal(Location loc) {
		return getClosestVal(loc, Double.MAX_VALUE);
	}
	
	/**
	 * Returns the value at the closest location in the XYZ file (no matter how far
	 * away the closest point is).
	 * 
	 * @param lat
	 * @param lon
	 * @return
	 */
	public double getClosestVal(double lat, double lon) {
		return getClosestVal(new Location(lat, lon), Double.MAX_VALUE);
	}
	
	/**
	 * Returns the value at the closest location in the XYZ file within a given tolerance.
	 * 
	 * @param loc
	 * @param tolerance
	 * @return
	 */
	public double getClosestVal(Location pt1, double tolerance) {
		Location closest = getClosestLoc(pt1, tolerance);
		
		if (closest != null)
			return dataset.get(closest);
		else
			return Double.NaN;
	}
	
	public Location getClosestLoc(Location pt1, double tolerance) {
		if (dataset instanceof GriddedGeoDataSet) {
			int index = dataset.indexOf(pt1);
			if (index >= 0)
				return dataset.getLocation(index);
		}
		double closest = Double.MAX_VALUE;
		Location closestLoc = null;
		
		for (int i=0; i<dataset.size(); i++) {
			Location pt2 = dataset.getLocation(i);
//			double val = dataset.get(i);
//			double dist = Math.pow(val[0] - lat, 2) + Math.pow(val[1] - lon, 2);
			double dist = LocationUtils.horzDistanceFast(pt1, pt2);
			if (dist < closest) {
				closest = dist;
				closestLoc = pt2;
			}
		}
		
		if (closest < tolerance)
			return closestLoc;
		else
			return null;
	}
	
	/**
	 * Returns the value at the closest location in the XYZ file within a given tolerance.
	 * 
	 * @param lat
	 * @param lon
	 * @param tolerance
	 * @return
	 */
	public double getClosestVal(double lat, double lon, double tolerance) {
		return getClosestVal(new Location(lat, lon), tolerance);
	}
}
