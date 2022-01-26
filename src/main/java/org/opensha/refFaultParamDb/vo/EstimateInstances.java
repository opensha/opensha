package org.opensha.refFaultParamDb.vo;
import org.opensha.commons.data.estimate.Estimate;

/**
 * <p>Title: EstimateInstances.java </p>
 * <p>Description: Insert/retrieve/update/delete estimates in the database</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class EstimateInstances {
	private int estimateInstanceId;
	private Estimate estimate;
	private String units;

	public EstimateInstances() {
	}


	public String toString() {
		return estimate.toString();
	}

	public EstimateInstances(Estimate estimate, String units) {
		setEstimate(estimate);
		setUnits(units);
	}

	public Estimate getEstimate() {
		return estimate;
	}
	public int getEstimateInstanceId() {
		return estimateInstanceId;
	}
	public void setEstimate(Estimate estimate) {
		this.estimate = estimate;
	}
	public void setEstimateInstanceId(int estimateInstanceId) {
		this.estimateInstanceId = estimateInstanceId;
	}
	public String getUnits() {
		return units;
	}
	public void setUnits(String units) {
		this.units = units;
	}


}
