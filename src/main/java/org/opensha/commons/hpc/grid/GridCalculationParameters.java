package org.opensha.commons.hpc.grid;

import org.dom4j.Element;
import org.opensha.commons.metadata.XMLSaveable;

public class GridCalculationParameters implements XMLSaveable {
	
	public static final String XML_METADATA_NAME = "GridCalculationParameters";
	
	protected int maxWallTime;
	
	protected Element element = null;
	
	public GridCalculationParameters(int maxWallTime) {
		this.maxWallTime = maxWallTime;
	}
	
	public GridCalculationParameters(Element parentElement, String elemName) {
		element = this.getElement(parentElement, elemName);
		
		maxWallTime = Integer.parseInt(element.attribute("maxWallTime").getValue());
	}
	
	private Element getElement(Element parent, String elemName) {
		return parent.element(elemName);
	}

	public Element toXMLMetadata(Element root) {
		Element xml = root.addElement(XML_METADATA_NAME);
		
		xml.addAttribute("maxWallTime", maxWallTime + "");
		
		return root;
	}
	
	@Override
	public String toString() {
		String str = "";
		
		str += "Grid Calculation Parameters" + "\n";
		str += "\tmaxWallTime: " + maxWallTime;
		
		return str;
	}
}
