package org.opensha.refFaultParamDb.dao;

import org.opensha.commons.data.estimate.Estimate;
import org.opensha.refFaultParamDb.dao.db.DB_AccessAPI;
import org.opensha.refFaultParamDb.dao.exception.InsertException;
import org.opensha.refFaultParamDb.dao.exception.QueryException;
import org.opensha.refFaultParamDb.dao.exception.UpdateException;
/**
 * <p>Title: NormalEstimateDAO_API.java </p>
 * <p>Description: Inserts/gets/delete normal estimates from the tables</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public interface EstimateDAO_API {

  /**
   * Add a new  estimate object to the database
   *
   */
  public void addEstimate(int estimateInstanceId, Estimate estimate) throws InsertException;


  /**
   * Get the  Estimate Instance info for a particular estimateInstanceId
   */
  public Estimate getEstimate(int estimateInstanceId) throws QueryException;


  /**
   * Remove the  Estimate from the list
   */
  public boolean removeEstimate(int estimateInstanceId) throws UpdateException;

  public String getEstimateTypeName();

  public void setDB_Connection(DB_AccessAPI dbAccessAPI);

}
