package org.opensha.commons.metadata;

import org.dom4j.Element;

public interface XMLSaveable {
	
	public String XML_METADATA_NAME = "";
	
	public Element toXMLMetadata(Element root);
}
