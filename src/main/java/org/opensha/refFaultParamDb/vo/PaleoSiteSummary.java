package org.opensha.refFaultParamDb.vo;

/**
 * <p>Title: PaleoSiteSummary.java </p>
 * <p>Description: It contains a site id and name from paleo site database</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class PaleoSiteSummary {
	private int siteId;
	private String siteName;
	public PaleoSiteSummary() {
	}
	public int getSiteId() {
		return siteId;
	}
	public String getSiteName() {
		return siteName;
	}
	public void setSiteId(int siteId) {
		this.siteId = siteId;
	}
	public void setSiteName(String siteName) {
		this.siteName = siteName;
	}


}
