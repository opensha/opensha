package org.opensha.refFaultParamDb.dao.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.opensha.refFaultParamDb.dao.exception.QueryException;
import org.opensha.refFaultParamDb.vo.EstimateType;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class EstimateTypeDB_DAO  {

	private final static String TABLE_NAME="Est_Type";
	private final static String EST_TYPE_ID="Est_Type_Id";
	private final static String EST_NAME="Est_Name";
	private final static String EFFECTIVE_DATE="Entry_Date";
	private DB_AccessAPI dbAccessAPI;

	/**
	 * Constructor.
	 * @param dbConnection
	 */
	public EstimateTypeDB_DAO(DB_AccessAPI dbAccessAPI) {
		setDB_Connection(dbAccessAPI);
	}


	public void setDB_Connection(DB_AccessAPI dbAccessAPI) {
		this.dbAccessAPI = dbAccessAPI;
	}

	/**
	 * Return a list of all available estimates
	 * @return
	 * @throws QueryException
	 */
	public ArrayList<EstimateType> getAllEstimateTypes() throws QueryException {
		return query(" ");
	}

	/**
	 * Get a estimate based on estimate name
	 * @param estimateName
	 * @return
	 * @throws QueryException
	 */
	public EstimateType getEstimateType(String estimateName) throws QueryException {
		EstimateType estimateType=null;
		String condition = " where "+EST_NAME+"='"+estimateName+"'";
		ArrayList<EstimateType> estimateTypeList=query(condition);
		if(estimateTypeList.size()>0) estimateType = (EstimateType)estimateTypeList.get(0);
		return estimateType;
	}

	/**
	 * Get estimate based on estimate type id
	 *
	 * @param estimateTypeId
	 * @return
	 * @throws QueryException
	 */
	public EstimateType getEstimateType(int estimateTypeId) throws QueryException {
		EstimateType estimateType=null;
		String condition = " where "+EST_TYPE_ID+"="+estimateTypeId+"";
		ArrayList<EstimateType> estimateTypeList=query(condition);
		if(estimateTypeList.size()>0) estimateType = (EstimateType)estimateTypeList.get(0);
		return estimateType;
	}


	private ArrayList<EstimateType> query(String condition) throws QueryException {
		ArrayList<EstimateType> estimateTypeList = new ArrayList<EstimateType>();
		String sql =  "select "+EST_TYPE_ID+","+EST_NAME+",to_char("+EFFECTIVE_DATE+") as "+
		EFFECTIVE_DATE +" from "+TABLE_NAME+condition;
		try {
			ResultSet rs  = dbAccessAPI.queryData(sql);
			while(rs.next()) estimateTypeList.add(new EstimateType(rs.getInt(EST_TYPE_ID),
					rs.getString(EST_NAME),rs.getString(EFFECTIVE_DATE)));
			rs.close();
		} catch(SQLException e) { throw new QueryException(e.getMessage()); }
		return estimateTypeList;
	}


}
