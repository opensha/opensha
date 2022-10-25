package org.opensha.refFaultParamDb.vo;

/**
 * <p>Title: EstimateType.java </p>
 * <p>Description: Various estimate types in types available</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class EstimateType {
	private int estimateTypeId;
	private String estimateName;
	private String effectiveDate;

	public EstimateType() {
	}

	public EstimateType(int estimateTypeId, String estimateName, String effectiveDate) {
		setEstimateTypeId(estimateTypeId);
		setEstimateName(estimateName);
		setEffectiveDate(effectiveDate);
	}

	public String getEffectiveDate() {
		return effectiveDate;
	}

	public void setEffectiveDate(String effectiveDate) {
		this.effectiveDate = effectiveDate;
	}
	public String getEstimateName() {
		return estimateName;
	}
	public void setEstimateName(String estimateName) {
		this.estimateName = estimateName;
	}
	public int getEstimateTypeId() {
		return estimateTypeId;
	}
	public void setEstimateTypeId(int estimateTypeId) {
		this.estimateTypeId = estimateTypeId;
	}
}
