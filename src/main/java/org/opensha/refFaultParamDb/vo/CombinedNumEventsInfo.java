package org.opensha.refFaultParamDb.vo;

/**
 * <p>Title: CombinedNumEventsInfo.java </p>
 * <p>Description: this class saves the combined num events information </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class CombinedNumEventsInfo {
	private EstimateInstances numEventsEstimate;
	private String numEventsComments;

	public CombinedNumEventsInfo() {
	}

	public String toString() {
		String numEventsStr=null;
		if(numEventsEstimate!=null) numEventsStr = numEventsEstimate.toString();
		return "Num Events Estimate="+numEventsStr+"\n"+
		"Comments="+numEventsComments;
	}

	public String getNumEventsComments() {
		return numEventsComments;
	}
	public EstimateInstances getNumEventsEstimate() {
		return numEventsEstimate;
	}
	public void setNumEventsComments(String numEventsComments) {
		this.numEventsComments = numEventsComments;
	}
	public void setNumEventsEstimate(EstimateInstances numEventsEstimate) {
		this.numEventsEstimate = numEventsEstimate;
	}

}
