package org.opensha.refFaultParamDb.dao.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import oracle.spatial.geometry.JGeometry;

import org.opensha.commons.geo.Location;
import org.opensha.refFaultParamDb.dao.exception.InsertException;
import org.opensha.refFaultParamDb.dao.exception.QueryException;
import org.opensha.refFaultParamDb.dao.exception.UpdateException;
import org.opensha.refFaultParamDb.vo.FaultSectionConnection;
import org.opensha.refFaultParamDb.vo.FaultSectionConnectionList;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * <p>Title: FaultModelDB_DAO.java </p>
 * <p>Description: Performs insert/delete/update on fault model on oracle database</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */


public class FaultSectionConnectionsDB_DAO   implements java.io.Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private final static String TABLE_NAME="FAULT_SECTION_CONNECTIONS";
	private final static String ID_1="SECTION_ID_1";
	private final static String ID_2="SECTION_ID_2";
	private final static String LOCATION_1="LOCATION_1";
	private final static String LOCATION_2="LOCATION_2";
	private DB_AccessAPI dbAccessAPI;


	public FaultSectionConnectionsDB_DAO(DB_AccessAPI dbAccessAPI) {
		setDB_Connection(dbAccessAPI);
	}

	public void setDB_Connection(DB_AccessAPI dbAccessAPI) {
		this.dbAccessAPI = dbAccessAPI;
	}

	/**
	 * Add a new fault connection
	 *
	 * @param connection
	 * @throws InsertException
	 */
	public void addConnection(FaultSectionConnection connection) throws InsertException {
		JGeometry geom1 = SpatialUtils.getSinglePointGeometry(connection.getLoc1());
		JGeometry geom2 = SpatialUtils.getSinglePointGeometry(connection.getLoc2());

		ArrayList<JGeometry> geomLists = Lists.newArrayList(geom1, geom2);

		String sql = "insert into "+TABLE_NAME+"("+ ID_1+","+ID_2+
				","+LOCATION_1+","+LOCATION_2+") "+
				" values ("+connection.getId1()+","+connection.getId2()+
				",?,?)";
		try { 
			dbAccessAPI.insertUpdateOrDeleteData(sql, geomLists);
		} catch(SQLException e) {
			//e.printStackTrace();
			throw new InsertException(e.getMessage());
		};
	}

	/**
	 * remove a fault connection from the database. will delete regardless of the order
	 * the IDs are supplied in
	 * @param id1
	 * @param id2
	 * @return true if a connection was removed
	 * @throws UpdateException
	 */
	public boolean removeConnection(int id1, int id2) throws UpdateException {
		String sql = "delete from "+TABLE_NAME+"  where ("+ID_1+"="+id1+" AND "+ID_2+"="+id2
				+") OR ("+ID_1+"="+id2+" AND "+ID_2+"="+id1+")";
		try {
			int numRows = dbAccessAPI.insertUpdateOrDeleteData(sql);
			return numRows >= 1;
		}
		catch(SQLException e) { throw new UpdateException(e.getMessage()); }
	}

	/**
	 * remove any fault connection that involves the given section
	 * the IDs are supplied in
	 * @param id1
	 * @return the nubmer sections deleted
	 * @throws UpdateException
	 */
	public int removeAllConnections(int id) throws UpdateException {
		String sql = "delete from "+TABLE_NAME+"  where "+ID_1+"="+id+" OR "+ID_2+"="+id;
		try {
			int numRows = dbAccessAPI.insertUpdateOrDeleteData(sql);
			return numRows;
		}
		catch(SQLException e) { throw new UpdateException(e.getMessage()); }
	}

	/**
	 * Get all the fault Models from the database
	 * @return
	 * @throws QueryException
	 */
	public FaultSectionConnectionList getAllConnections() throws QueryException {
		return query(" ");
	}

	private FaultSectionConnectionList query(String condition) throws QueryException {
		FaultSectionConnectionList conns = new FaultSectionConnectionList();

		// this awkward sql is needed else we get "Invalid scale exception"
		String sqlWithSpatialColumnNames =  "select "+ID_1+","+ID_2+","
					+LOCATION_1+","+LOCATION_2+" from "+TABLE_NAME+condition;

		String sqlWithNoSpatialColumnNames =  "select "+ID_1+","+ID_2+" from "+TABLE_NAME+condition;

		ArrayList<String> spatialColumnNames = new ArrayList<String>();
		spatialColumnNames.add(LOCATION_1);
		spatialColumnNames.add(LOCATION_2);

		try {
			SpatialQueryResult spatialQueryResult  = dbAccessAPI.queryData(
					sqlWithSpatialColumnNames, sqlWithNoSpatialColumnNames, spatialColumnNames);
			ResultSet rs = spatialQueryResult.getCachedRowSet();
			int i=0;
			while(rs.next())  {
				int id1 = rs.getInt(ID_1);
				int id2 = rs.getInt(ID_2);
				ArrayList<JGeometry> geometries = spatialQueryResult.getGeometryObjectsList(i++);
				Preconditions.checkState(geometries.size() == 2);
				Location loc1 = SpatialUtils.loadSinglePointGeometry(geometries.get(0), 0d);
				Location loc2 = SpatialUtils.loadSinglePointGeometry(geometries.get(1), 0d);
				
				conns.add(new FaultSectionConnection(id1, id2, loc1, loc2));
			}
			
			rs.close();
		} catch(SQLException e) { throw new QueryException(e.getMessage()); }
		return conns;
	}
	
	public static void main(String[] args) {
		DB_AccessAPI db = null;
		
		try {
//			db = DB_ConnectionPool.getLatestReadOnlyConn();
			db = DB_ConnectionPool.getDB3ReadOnlyConn();
//			db = DB_ConnectionPool.getLatestReadWriteConn();
			
			FaultSectionConnectionsDB_DAO connsDB = new FaultSectionConnectionsDB_DAO(db);
			
			for (FaultSectionConnection conn : connsDB.getAllConnections()) {
				System.out.println(conn);
			}
		} catch (QueryException e) {
			e.printStackTrace();
		} finally {
			if (db != null) {
				try {
					db.destroy();
				} catch (SQLException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}
		
		System.exit(0);
	}

}
