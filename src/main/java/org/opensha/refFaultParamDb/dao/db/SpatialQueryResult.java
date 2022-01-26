package org.opensha.refFaultParamDb.dao.db;

import java.util.ArrayList;

import javax.sql.rowset.CachedRowSet;

import oracle.spatial.geometry.JGeometry;

/**
 * <p>Title: SpatialQueryResult.java </p>
 * <p>Description: This class can be used to return the results of spatial queries.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class SpatialQueryResult implements java.io.Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private CachedRowSet cachedRowSet;
	private ArrayList<ArrayList<JGeometry>> geomteryObjectsList = new ArrayList<ArrayList<JGeometry>>();

	public SpatialQueryResult() {
	}

	public void setCachedRowSet(CachedRowSet cachedRowSet) {
		this.cachedRowSet = cachedRowSet;
	}

	public ArrayList<JGeometry> getGeometryObjectsList(int index) {
		return geomteryObjectsList.get(index);
	}

	public void add(ArrayList<JGeometry> geomteryObjects) {
		geomteryObjectsList.add(geomteryObjects);
	}

	public CachedRowSet getCachedRowSet() {
		return this.cachedRowSet;
	}



}
