package org.opensha.refFaultParamDb.vo;

import org.opensha.refFaultParamDb.data.TimeAPI;

/**
 * <p>Title: Timespan.java </p>
 * <p>Description: This class holds the start and end time for paleo site data.
 * The start and end time can be estimates or exact times. </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class Timespan {
	private TimeAPI startTime;
	private TimeAPI endTime;
	private String datingMethodology;

	public Timespan() {
	}

	public String getDatingMethodology() {
		return datingMethodology;
	}
	public TimeAPI getEndTime() {
		return endTime;
	}
	public TimeAPI getStartTime() {
		return startTime;
	}
	public void setDatingMethodology(String datingMethodology) {
		this.datingMethodology = datingMethodology;
	}
	public void setEndTime(TimeAPI endTime) {
		this.endTime = endTime;
	}

	public void setStartTime(TimeAPI startTime) {
		this.startTime = startTime;
	}
}
