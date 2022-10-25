package org.opensha.refFaultParamDb.vo;

import java.util.ArrayList;

/**
 * <p>Title: PaleoSitePublications.java </p>
 * <p>Description: It saves the paleo site Id, reference associated with it and
 * site types and representative strand indices for it as provided by that reference(publication)</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class PaleoSitePublication {
	private int siteId=-1;
	private String siteEntryDate;
	private ArrayList<String> siteTypeNames;
	private Reference reference;
	private String contributorName;
	private String representativeStrandName;
	private String entryDate;

	public PaleoSitePublication() {
	}

	public String toString() {
		String siteTypeStr= "";
		for(int i=0; siteTypeNames!=null && i<siteTypeNames.size(); ++i)
			siteTypeStr+=siteTypeNames.get(i);
		return "Representative Strand Name="+representativeStrandName+"\n"+
		"Site Types="+siteTypeStr+"\n"+
		"Reference="+reference.getSummary();
	}

	public String getContributorName() {
		return contributorName;
	}
	public Reference getReference() {
		return reference;
	}
	public String getRepresentativeStrandName() {
		return representativeStrandName;
	}
	public String getSiteEntryDate() {
		return siteEntryDate;
	}
	public int getSiteId() {
		return siteId;
	}
	public ArrayList<String> getSiteTypeNames() {
		return siteTypeNames;
	}
	public void setSiteTypeNames(ArrayList<String> siteTypeNames) {
		this.siteTypeNames = siteTypeNames;
	}
	public void setSiteId(int siteId) {
		this.siteId = siteId;
	}
	public void setSiteEntryDate(String siteEntryDate) {
		this.siteEntryDate = siteEntryDate;
	}
	public void setRepresentativeStrandName(String representativeStrandName) {
		this.representativeStrandName = representativeStrandName;
	}
	public void setReference(Reference reference) {
		this.reference = reference;
	}
	public void setContributorName(String contributorName) {
		this.contributorName = contributorName;
	}
	public String getEntryDate() {
		return entryDate;
	}
	public void setEntryDate(String entryDate) {
		this.entryDate = entryDate;
	}
}
