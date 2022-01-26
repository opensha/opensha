package org.opensha.refFaultParamDb.dao.db;

import org.opensha.commons.data.estimate.Estimate;
import org.opensha.commons.data.estimate.IntegerEstimate;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.refFaultParamDb.dao.EstimateDAO_API;
import org.opensha.refFaultParamDb.dao.exception.InsertException;
import org.opensha.refFaultParamDb.dao.exception.QueryException;
import org.opensha.refFaultParamDb.dao.exception.UpdateException;
/**
 * <p>Title: IntegerEstimateDB_DAO.java </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class IntegerEstimateDB_DAO implements EstimateDAO_API {

	public final static String EST_TYPE_NAME="IntegerEstimate";
	private final static String ERR_MSG = "This class just deals with Integer Estimates";
	private XY_EstimateDB_DAO xyEstimateDB_DAO  = new XY_EstimateDB_DAO();

	/**
	 * Constructor.
	 * @param dbConnection
	 */
	public IntegerEstimateDB_DAO(DB_AccessAPI dbAccessAPI) {
		setDB_Connection(dbAccessAPI);
	}

	public IntegerEstimateDB_DAO() { }


	public void setDB_Connection(DB_AccessAPI dbAccessAPI) {
		xyEstimateDB_DAO.setDB_Connection(dbAccessAPI);
	}

	/**
	 * Add the normal estimate into the database table
	 * @param estimateInstanceId
	 * @param estimate
	 * @throws InsertException
	 */
	public void addEstimate(int estimateInstanceId, Estimate estimate) throws InsertException {
		if(!(estimate instanceof IntegerEstimate)) throw new InsertException(ERR_MSG);
		IntegerEstimate integerEstimate = (IntegerEstimate)estimate;
		xyEstimateDB_DAO.addEstimate(estimateInstanceId, integerEstimate.getValues());
	}

	/**
	 *
	 * @param estimateInstanceId
	 * @return
	 * @throws QueryException
	 */
	public Estimate getEstimate(int estimateInstanceId) throws QueryException {
		ArbitrarilyDiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
		xyEstimateDB_DAO.getEstimate(estimateInstanceId,func);
		IntegerEstimate estimate=new IntegerEstimate(func,false);
		return estimate;
	}

	/**
	 *
	 * @param estimateInstanceId
	 * @return
	 * @throws UpdateException
	 */
	public boolean removeEstimate(int estimateInstanceId) throws UpdateException {
		return xyEstimateDB_DAO.removeEstimate(estimateInstanceId);
	}

	public String getEstimateTypeName() {
		return EST_TYPE_NAME;
	}

}
