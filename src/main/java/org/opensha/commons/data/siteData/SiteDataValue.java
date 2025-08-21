package org.opensha.commons.data.siteData;

import org.dom4j.Attribute;
import org.opensha.commons.metadata.XMLSaveable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * This represents a single site data value, along with metadata describing it's
 * type and source. It is returned by the SiteDataAPI.getAnnotatedValue method. 
 * 
 * @author Kevin
 *
 * @param <Element>
 */
public class SiteDataValue<Element> implements XMLSaveable {
	
	public static final String XML_METADATA_NAME = "SiteDataValue";
	
	private String dataType;
	private String dataMeasurementType;
	private Element value;
	private String sourceName = null;

	public SiteDataValue(String dataType, String dataMeasurementType, Element value) {
		this(dataType, dataMeasurementType, value, null);
	}
	
	public SiteDataValue(String dataType, String dataMeasurementType, Element value, String sourceName) {
		this.dataType = dataType;
		this.dataMeasurementType = dataMeasurementType;
		this.value = value;
		this.sourceName = sourceName;
	}
	
	public String getDataType() {
		return dataType;
	}

	public String getDataMeasurementType() {
		return dataMeasurementType;
	}

	public Element getValue() {
		return value;
	}
	
	public String getSourceName() {
		return sourceName;
	}

	@Override
	public String toString() {
		String str = "Type: " + dataType + ", Measurement Type: " + dataMeasurementType + ", Value: " + value;
		if (sourceName != null)
			str += ", Source: " + sourceName;
		return str;
	}

    @Override
    public int hashCode() {
        return Objects.hash(dataType, dataMeasurementType, value, sourceName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                                // same object
        if (o == null || getClass() != o.getClass()) return false; // null or different class
        SiteDataValue<?> that = (SiteDataValue<?>)o;               // explicit cast
        return Objects.equals(dataType, that.dataType) &&
                Objects.equals(dataMeasurementType, that.dataMeasurementType) &&
                Objects.equals(value, that.value) &&
                Objects.equals(sourceName, that.sourceName);
    }

	public org.dom4j.Element toXMLMetadata(org.dom4j.Element root) {
		org.dom4j.Element elem = root.addElement(XML_METADATA_NAME);
		elem.addAttribute("type", dataType);
		elem.addAttribute("measurementType", dataMeasurementType);
		
		// in the future we could add complex elements here
		org.dom4j.Element valEl = elem.addElement("Value");
		if (value instanceof String)
			valEl.addAttribute("stringValue", value.toString());
		else if (value instanceof Double)
			valEl.addAttribute("doubleValue", value.toString());
		else
			throw new RuntimeException("Type '" + dataType + "' cannot be saved to XML!");
		
		return root;
	}
	
	public static SiteDataValue<?> fromXMLMetadata(org.dom4j.Element dataElem) {
		String dataType = dataElem.attributeValue("type");
		String measurementType = dataElem.attributeValue("measurementType");
		
		org.dom4j.Element valEl = dataElem.element("Value");
		
		Attribute strAtt = valEl.attribute("stringValue");
		Attribute doubAtt = valEl.attribute("doubleValue");
		
		Object val;
		if (strAtt != null) {
			val = strAtt.getValue();
		} else if (doubAtt != null) {
			val = Double.parseDouble(doubAtt.getValue());
		} else {
			throw new RuntimeException("Type '" + dataType + "' unknown, cannot load from XML!");
		}
		return new SiteDataValue(dataType, measurementType, val);
	}
}
