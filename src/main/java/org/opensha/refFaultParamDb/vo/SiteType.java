package org.opensha.refFaultParamDb.vo;

/**
 * <p>Title: SiteType.java </p>
 * <p>Description: This saves the various site types associated with a
 * paloe site. Example of site types are : 'trench', 'geologic', 'survey/cultural' </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class SiteType {
	private int siteTypeId=-1;
	private Contributor contributor;
	private String siteType;
	private String comments;

	public SiteType() {
	}

	public String toString() {
		return "Site Type="+this.siteType+"\n"+
		"Comments="+this.comments;
	}

	public SiteType(int siteTypeId, String siteTypeName, Contributor contributor,
			String comments) {
		this(siteTypeName, contributor, comments);
		setSiteTypeId(siteTypeId);
	}

	public SiteType(String siteTypeName, Contributor contributor, String comments) {
		setContributor(contributor);
		setSiteType(siteTypeName);
		this.setComments(comments);
	}

	public void setSiteTypeId(int siteTypeId) {
		this.siteTypeId = siteTypeId;
	}

	public void setContributor(Contributor contributor) {
		this.contributor = contributor;
	}

	public void setSiteType(String siteType) {
		this.siteType=siteType;
	}

	public void setComments(String comments) {
		this.comments = comments;
	}

	public int getSiteTypeId() { return this.siteTypeId; }
	public Contributor getContributor() { return this.contributor; }
	public String getSiteType() { return this.siteType; }
	public String getComments() { return comments; }


}
