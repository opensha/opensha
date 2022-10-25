package org.opensha.refFaultParamDb.vo;


/**
 * <p>Title: CombinedDisplacementInfo.java </p>
 * <p>Description: This class saves the information if the user wants to enter
 * displacement info for a site.</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class CombinedDisplacementInfo {
	private EstimateInstances displacementEstimate;
	private EstimateInstances aSeismicSlipFactorEstimateForDisp;
	private EstimateInstances senseOfMotionRake;
	private String senseOfMotionQual;
	private String measuredComponentQual;
	private String displacementComments;

	public CombinedDisplacementInfo() {
	}

	public String toString() {
		String dispEstStr=null, aSeismicSlipEstStr=null, rakeStr=null;
		if(displacementEstimate!=null) dispEstStr = displacementEstimate.toString();
		if(aSeismicSlipFactorEstimateForDisp!=null) aSeismicSlipEstStr = aSeismicSlipFactorEstimateForDisp.toString();
		if(senseOfMotionRake!=null) rakeStr = senseOfMotionRake.toString();
		return "Displacement Estimate="+dispEstStr+"\n"+
		"Aseismic Slip Factor Estimate="+aSeismicSlipEstStr+"\n"+
		"Sense of Motion Rake="+rakeStr+"\n"+
		"Sense of Motion Qualitative="+senseOfMotionQual+"\n"+
		"Measured Component Qualitative="+measuredComponentQual+"\n"+
		"Displacement Comments="+displacementComments;
	}

	public  String getSenseOfMotionQual() {
		return this.senseOfMotionQual;
	}
	public String getMeasuredComponentQual() {
		return this.measuredComponentQual;
	}
	public EstimateInstances getASeismicSlipFactorEstimateForDisp() {
		return aSeismicSlipFactorEstimateForDisp;
	}
	public String getDisplacementComments() {
		return displacementComments;
	}
	public EstimateInstances getDisplacementEstimate() {
		return displacementEstimate;
	}
	public void setDisplacementEstimate(EstimateInstances displacementEstimate) {
		this.displacementEstimate = displacementEstimate;
	}
	public void setDisplacementComments(String displacementComments) {
		this.displacementComments = displacementComments;
	}
	public void setASeismicSlipFactorEstimateForDisp(EstimateInstances aSeismicSlipFactorEstimateForDisp) {
		this.aSeismicSlipFactorEstimateForDisp = aSeismicSlipFactorEstimateForDisp;
	}
	public EstimateInstances getSenseOfMotionRake() {
		return this.senseOfMotionRake;
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
