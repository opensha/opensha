package org.opensha.refFaultParamDb.vo;

/**
 * <p>Title: Reference.java </p>
 * <p>Description: This class has information about the references </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class Reference {

	private int referenceId=-1; // reference ID
	private String refAuth; // short citation
	private String refYear;
	private String fullBiblioReference; // full bibliographic reference
	private int qfaultReferenceId = -1;

	public Reference() {
	}

	public String toString() {
		return "Reference Author="+refAuth+"\n"+
		"Reference Year="+refYear+"\n"+
		"Full Bibliographic Ref="+this.fullBiblioReference;
	}

	public Reference(int referenceId, String author, String year, String fullBiblioReference) {
		this(author, year, fullBiblioReference);
		setReferenceId(referenceId);
	}

	public Reference(String author, String year, String fullBiblioReference) {
		this.setRefAuth(author);
		this.setRefYear(year);
		this.setFullBiblioReference(fullBiblioReference);
	}

	public int getReferenceId() {
		return referenceId;
	}
	public void setReferenceId(int referenceId) {
		this.referenceId = referenceId;
	}
	public int getQfaultReferenceId() {
		return this.qfaultReferenceId;
	}
	public void setQfaultReferenceId(int qfaultRefId) {
		this.qfaultReferenceId = qfaultRefId;
	}
	public String getFullBiblioReference() {
		return fullBiblioReference;
	}
	public void setFullBiblioReference(String fullBiblioReference) {
		this.fullBiblioReference = fullBiblioReference;
	}
	public String getRefAuth() {
		return refAuth;
	}
	public void setRefAuth(String refAuth) {
		this.refAuth = refAuth;
	}
	public void setRefYear(String refYear) {
		this.refYear = refYear;
	}
	public String getRefYear() {
		return refYear;
	}
	public String getSummary() {
		return this.refAuth+" ("+refYear+")";
	}

}
