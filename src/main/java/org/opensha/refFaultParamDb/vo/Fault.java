package org.opensha.refFaultParamDb.vo;

/**
 * <p>Title: Fault.java </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class Fault {
	private int faultId;
	private String faultName;

	public Fault(int id, String faultName) {
		setFaultId(id);
		setFaultName(faultName);
	}

	public int getFaultId() {
		return faultId;
	}
	public String getFaultName() {
		return faultName;
	}
	public void setFaultId(int faultId) {
		this.faultId = faultId;
	}
	public void setFaultName(String faultName) {
		this.faultName = faultName;
	}

}
