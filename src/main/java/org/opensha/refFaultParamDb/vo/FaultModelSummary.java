package org.opensha.refFaultParamDb.vo;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.opensha.commons.metadata.XMLSaveable;

/**
 * <p>Title: FaultModel.java </p>
 * <p>Description: Various fault models available</p>
 * <p>Copyright: Copyright (c) 2002</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class FaultModelSummary  implements java.io.Serializable, XMLSaveable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public static final String XML_METADATA_NAME = "FaultModelSummary";
	private int faultModelId;
	private String faultModelName;
	private Contributor contributor;

	public FaultModelSummary() {
	}

	public FaultModelSummary(int faultModelId, String faultModelName, Contributor contributor) {
		setFaultModelId(faultModelId);
		setContributor(contributor);
		setFaultModelName(faultModelName);
	}

	public FaultModelSummary(String faultModelName, Contributor contributor) {
		setContributor(contributor);
		setFaultModelName(faultModelName);
	}


	public Contributor getContributor() {
		return contributor;
	}
	public int getFaultModelId() {
		return faultModelId;
	}
	public String getFaultModelName() {
		return faultModelName;
	}
	public void setContributor(Contributor contributor) {
		this.contributor = contributor;
	}
	public void setFaultModelId(int faultModelId) {
		this.faultModelId = faultModelId;
	}
	public void setFaultModelName(String faultModelName) {
		this.faultModelName = faultModelName;
	}

	public Element toXMLMetadata(Element root) {

		Element el = root.addElement(XML_METADATA_NAME);

		el.addAttribute("faultModelId", faultModelId + "");
		el.addAttribute("faultModelName", faultModelName);

		// add the contributor
		el = this.contributor.toXMLMetadata(el);

		return root;
	}

	public static FaultModelSummary fromXMLMetadata(Element el) {
		Attribute idAtt = el.attribute("faultModelId");
		String name = el.attributeValue("faultModelName");

		Contributor cont = Contributor.fromXMLMetadata(el.element(Contributor.XML_METADATA_NAME));

		if (idAtt != null) {
			int id = Integer.parseInt(idAtt.getValue());

			return new FaultModelSummary(id, name, cont);
		}

		return new FaultModelSummary(name, cont);
	}
}
