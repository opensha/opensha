package org.opensha.refFaultParamDb.vo;

/**
 * <p>Title: SectionSource.java </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class SectionSource {
	private int sourceId;
	private String sectionSourceName;
	public SectionSource() {
	}
	public SectionSource(int sourceId, String sourceName) {
		this.sourceId = sourceId;
		this.sectionSourceName = sourceName;
	}
	public String getSectionSourceName() {
		return sectionSourceName;
	}
	public int getSourceId() {
		return sourceId;
	}
	public void setSectionSourceName(String sectionSourceName) {
		this.sectionSourceName = sectionSourceName;
	}
	public void setSourceId(int sourceId) {
		this.sourceId = sourceId;
	}

}
