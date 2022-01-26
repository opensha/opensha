package org.opensha.refFaultParamDb.vo;

/**
 * <p>Title: SiteRepresentation.java </p>
 * <p>Description: Various representations possible for a site like "Entire Fault",
 * "Most Significant Strand", "One of Several Strands", "Unknown"</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class SiteRepresentation {
	private int siteRepresentationId;
	private String siteRepresentationName;

	/**
	 *
	 * @param siteRepresentationId
	 * @param siteRepresentationName
	 */
	public SiteRepresentation(int siteRepresentationId, String siteRepresentationName) {
		setSiteRepresentationId(siteRepresentationId);
		setSiteRepresentationName(siteRepresentationName);
	}

	// various get/set methods
	public int getSiteRepresentationId() {
		return siteRepresentationId;
	}
	public void setSiteRepresentationId(int siteRepresentationId) {
		this.siteRepresentationId = siteRepresentationId;
	}
	public void setSiteRepresentationName(String siteRepresentationName) {
		this.siteRepresentationName = siteRepresentationName;
	}
	public String getSiteRepresentationName() {
		return siteRepresentationName;
	}

}
