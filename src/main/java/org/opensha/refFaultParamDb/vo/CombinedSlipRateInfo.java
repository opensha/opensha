package org.opensha.refFaultParamDb.vo;


/**
 * <p>Title: CombinedSlipRateInfo.java </p>
 * <p>Description: This class saves all the information if the user wants
 * to contribute slip rate info for a site.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class CombinedSlipRateInfo {
	private EstimateInstances slipRateEstimate;
	private EstimateInstances aSeismicSlipFactorEstimateForSlip;
	private String slipRateComments;
	private EstimateInstances senseOfMotionRake;
	private String senseOfMotionQual;
	private String measuredComponentQual;

	public CombinedSlipRateInfo() {
	}

	public String toString() {
		String slipRateStr=null, aSeismicSlipEstStr=null, rakeStr=null;
		if(slipRateEstimate!=null) slipRateStr = slipRateEstimate.toString();
		if(aSeismicSlipFactorEstimateForSlip!=null) aSeismicSlipEstStr = aSeismicSlipFactorEstimateForSlip.toString();
		if(senseOfMotionRake!=null) rakeStr = senseOfMotionRake.toString();
		return "Slip Rate Estimate="+slipRateStr+"\n"+
		"Aseismic Slip Factor Estimate="+aSeismicSlipEstStr+"\n"+
		"Sense of Motion Rake="+rakeStr+"\n"+
		"Sense of Motion Qualitative="+senseOfMotionQual+"\n"+
		"Measured Component Qualitative="+measuredComponentQual+"\n"+
		"Slip rate Comments="+slipRateComments;
	}


	public  String getSenseOfMotionQual() {
		return this.senseOfMotionQual;
	}
	public String getMeasuredComponentQual() {
		return this.measuredComponentQual;
	}
	public EstimateInstances getASeismicSlipFactorEstimateForSlip() {
		return aSeismicSlipFactorEstimateForSlip;
	}
	public String getSlipRateComments() {
		return slipRateComments;
	}
	public EstimateInstances getSlipRateEstimate() {
		return slipRateEstimate;
	}
	public EstimateInstances getSenseOfMotionRake() {
		return this.senseOfMotionRake;
	}
	public void setASeismicSlipFactorEstimateForSlip(EstimateInstances aSeismicSlipFactorEstimateForSlip) {
		this.aSeismicSlipFactorEstimateForSlip = aSeismicSlipFactorEstimateForSlip;
	}
	public void setSlipRateComments(String slipRateComments) {
		this.slipRateComments = slipRateComments;
	}
	public void setSlipRateEstimate(EstimateInstances slipRateEstimate) {
		this.slipRateEstimate = slipRateEstimate;
	}
	public void setSenseOfMotionRake(EstimateInstances senseOfMotionRake) {
		this.senseOfMotionRake = senseOfMotionRake;
	}
	public void setMeasuredComponentQual(String measuredComponentQual) {
		this.measuredComponentQual = measuredComponentQual;
	}
	public void setSenseOfMotionQual(String senseOfMotionQual) {
		this.senseOfMotionQual = senseOfMotionQual;
	}
}
