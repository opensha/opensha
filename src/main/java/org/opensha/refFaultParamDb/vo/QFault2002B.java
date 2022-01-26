package org.opensha.refFaultParamDb.vo;

import java.util.Date;

/**
 * <p>Title: QFault2002B.java </p>
 * <p>Description: Connects with QFault2002B table on pasadena database</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class QFault2002B{
	private String sectionId;
	private String sectionName;
	private Date effectiveDate;
	private String comments;
	private float avgSlipRate;
	private String slipComments;
	private float slipRateStdDev;
	private String slipDevComment;
	private float aveDip;
	private String dipComments;
	private float avgUpperDepth;
	private String upperDepthComment;
	private float avgLowerDepth;
	private String lowerDepthComment;
	private float aveRake;
	private String rakeComments;

	public QFault2002B() {
	}

	public float getAveDip() {
		return aveDip;
	}
	public void setAveDip(float aveDip) {
		this.aveDip = aveDip;
	}
	public void setAveRake(float aveRake) {
		this.aveRake = aveRake;
	}
	public void setAvgLowerDepth(float avgLowerDepth) {
		this.avgLowerDepth = avgLowerDepth;
	}
	public void setAvgSlipRate(float avgSlipRate) {
		this.avgSlipRate = avgSlipRate;
	}
	public void setAvgUpperDepth(float avgUpperDepth) {
		this.avgUpperDepth = avgUpperDepth;
	}
	public void setComments(String comments) {
		this.comments = comments;
	}
	public float getAveRake() {
		return aveRake;
	}
	public float getAvgLowerDepth() {
		return avgLowerDepth;
	}
	public float getAvgSlipRate() {
		return avgSlipRate;
	}
	public float getAvgUpperDepth() {
		return avgUpperDepth;
	}
	public String getComments() {
		return comments;
	}
	public String getDipComments() {
		return dipComments;
	}
	public Date getEffectiveDate() {
		return effectiveDate;
	}
	public String getLowerDepthComment() {
		return lowerDepthComment;
	}
	public String getRakeComments() {
		return rakeComments;
	}
	public String getSectionId() {
		return sectionId;
	}
	public String getSectionName() {
		return sectionName;
	}
	public String getSlipDevComment() {
		return slipDevComment;
	}
	public String getSlipComments() {
		return slipComments;
	}
	public float getSlipRateStdDev() {
		return slipRateStdDev;
	}
	public String getUpperDepthComment() {
		return upperDepthComment;
	}
	public void setUpperDepthComment(String upperDepthComment) {
		this.upperDepthComment = upperDepthComment;
	}
	public void setSlipRateStdDev(float slipRateStdDev) {
		this.slipRateStdDev = slipRateStdDev;
	}
	public void setSlipDevComment(String slipDevComment) {
		this.slipDevComment = slipDevComment;
	}
	public void setSlipComments(String slipComments) {
		this.slipComments = slipComments;
	}
	public void setSectionName(String sectionName) {
		this.sectionName = sectionName;
	}
	public void setSectionId(String sectionId) {
		this.sectionId = sectionId;
	}
	public void setRakeComments(String rakeComments) {
		this.rakeComments = rakeComments;
	}
	public void setLowerDepthComment(String lowerDepthComment) {
		this.lowerDepthComment = lowerDepthComment;
	}
	public void setEffectiveDate(Date effectiveDate) {
		this.effectiveDate = effectiveDate;
	}
	public void setDipComments(String dipComments) {
		this.dipComments = dipComments;
	}
}
