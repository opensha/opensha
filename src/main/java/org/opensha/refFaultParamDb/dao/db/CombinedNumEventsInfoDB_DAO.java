package org.opensha.refFaultParamDb.dao.db;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.opensha.refFaultParamDb.dao.exception.InsertException;
import org.opensha.refFaultParamDb.dao.exception.QueryException;
import org.opensha.refFaultParamDb.vo.CombinedNumEventsInfo;

/**
 * <p>Title: CombinedNumEventsInfoDB_DAO.java </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class CombinedNumEventsInfoDB_DAO {
	private final static String TABLE_NAME = "Combined_Num_Events_Info";
	private final static String NUM_EVENTS_EST_ID = "Num_Events_Est_Id";
	private final static String NUM_EVENTS_COMMENTS = "Num_Events_Comments";
	private final static String ENTRY_DATE="Entry_Date";
	private final static String INFO_ID = "Info_Id";
	private DB_AccessAPI dbAccess;
	private EstimateInstancesDB_DAO estimateInstancesDAO;

	public CombinedNumEventsInfoDB_DAO(DB_AccessAPI dbAccess) {
		setDB_Connection(dbAccess);
	}

	public void setDB_Connection(DB_AccessAPI dbAccess) {
		this.dbAccess = dbAccess;
		estimateInstancesDAO = new EstimateInstancesDB_DAO(dbAccess);
	}

	/**
	 *
	 * @param infoId
	 * @param entryDate
	 * @param combinedNumEventsInfo
	 */
	public void addNumEventsInfo(int infoId, String entryDate,
			CombinedNumEventsInfo combinedNumEventsInfo) {
		int numEventsId =  estimateInstancesDAO.addEstimateInstance(combinedNumEventsInfo.getNumEventsEstimate());
		String comments = combinedNumEventsInfo.getNumEventsComments();
		if(comments==null) comments="";
		String sql = "insert into "+TABLE_NAME+"("+NUM_EVENTS_EST_ID+","+
		NUM_EVENTS_COMMENTS+","+ INFO_ID+","+ENTRY_DATE+") values ("+numEventsId+",'"+
		comments+"',"+infoId+",'"+entryDate+"')";
		try {
			dbAccess.insertUpdateOrDeleteData(sql);
		}catch(SQLException e) {
			e.printStackTrace();
			throw new InsertException(e.getMessage());
		}
	}

	/**
	 *
	 * @param infoId
	 * @param entryDate
	 * @return
	 */
	public CombinedNumEventsInfo getCombinedNumEventsInfo(int infoId, String entryDate) {
		CombinedNumEventsInfo combinedNumEventsInfo = null;
		String sql = "select "+NUM_EVENTS_EST_ID+","+NUM_EVENTS_COMMENTS+" from "+TABLE_NAME+
		" where "+INFO_ID+"="+infoId+" and "+ENTRY_DATE+"='"+entryDate+"'";
		try {
			ResultSet rs = dbAccess.queryData(sql);
			while(rs.next()) {
				combinedNumEventsInfo = new CombinedNumEventsInfo();
				combinedNumEventsInfo.setNumEventsComments(rs.getString(NUM_EVENTS_COMMENTS));
				combinedNumEventsInfo.setNumEventsEstimate(estimateInstancesDAO.getEstimateInstance(rs.getInt(NUM_EVENTS_EST_ID)));
			}
		}
		catch (SQLException ex) {
			throw new QueryException(ex.getMessage());
		}
		return combinedNumEventsInfo;
	}


}
