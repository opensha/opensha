package org.opensha.commons.util.cpt;

import java.awt.Color;
import java.io.Serializable;

import org.dom4j.Element;
import org.opensha.commons.metadata.XMLSaveable;
import org.opensha.commons.util.XMLUtils;


public class CPTVal implements Comparable<CPTVal>, Serializable, Cloneable, XMLSaveable {
	public static final String XML_METADATA_NAME = "CPTVal";
	
	/**
	 * In general this information determines the indicator color associated
	 * with a value with a certain range.
	 *
	 * This class correspond to a row in a CPT file
	 */
	public Color minColor;
	public Color maxColor;
	public float start;
	public float end;

	public CPTVal() {}
	
	/**
	 *
	 * @param start =
	 *            min(Range(value))
	 * @param end =
	 *            max(Range(value))
	 * @param minR,minG,minB
	 *            are used to determine the color if the value is equal to the
	 *            start
	 * @param maxR,maxG,maxB
	 *            are used to determine the color if the value is equal to the
	 *            end
	 *
	 */
	public CPTVal(float start, int minR, int minG, int minB, float end,
			int maxR, int maxG, int maxB) {
		minColor = new Color(minR, minG, minB);
		maxColor = new Color(maxR, maxG, maxB);
		this.start = start;
		this.end = end;
		if (start > end) {
			throw new IllegalArgumentException("Start value: [" + this.start
					+ "] is greater than end value of: [" + this.end + "].");
		}
		// System.out.println("Min: " + minColor);
		// System.out.println("Max: " + maxColor);
	}
	
	public CPTVal(float start, Color minColor, float end, Color maxColor) {
		this.minColor = minColor;
		this.maxColor = maxColor;
		this.start = start;
		this.end = end;
		if (start > end) {
			throw new IllegalArgumentException("Start value: [" + this.start
					+ "] is greater than end value of: [" + this.end + "].");
		}
		// System.out.println("Min: " + minColor);
		// System.out.println("Max: " + maxColor);
	}
	
	@Override
	public Element toXMLMetadata(Element root) {
		Element xml = root.addElement(XML_METADATA_NAME);
		
		Element start = xml.addElement("Start");
		XMLUtils.colorToXML(start, minColor);
		start.addAttribute("value", ""+this.start);
		
		Element end = xml.addElement("End");
		XMLUtils.colorToXML(end, maxColor);
		end.addAttribute("value", ""+this.end);
		
		return root;
	}
	
	public static CPTVal fromXMLMetadata(Element valElem) {
		Element startEl = valElem.element("Start");
		Color startColor = XMLUtils.colorFromXML(startEl.element("Color"));
		float minVal = Float.parseFloat(startEl.attributeValue("value"));
		
		Element endEl = valElem.element("End");
		Color endColor = XMLUtils.colorFromXML(endEl.element("Color"));
		float maxVal = Float.parseFloat(endEl.attributeValue("value"));
		
		return new CPTVal(minVal, startColor, maxVal, endColor);
	}

	/**
	 * Implements the Comparable interface comparing by the start and end values
	 * returns 0 if there is an overlap with more than 1 point
	 */
	public int compareTo(CPTVal other) {
		// If this range is less than other range or only contains the start
		// point of the other range
		if (this.end <= other.start) {
			return -1;// (Float.valueOf(this.end - other.start )).intValue();
		}// If this range is greater than other range or only contains the
			// end point of the other range
		else if (this.start >= other.end) {
			return +1;// (Float.valueOf(this.start - other.end)).intValue();
		} else {
			// There is overlap
			return 0;
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Float.floatToIntBits(end);
		result = prime * result + ((maxColor == null) ? 0 : maxColor.hashCode());
		result = prime * result + ((minColor == null) ? 0 : minColor.hashCode());
		result = prime * result + Float.floatToIntBits(start);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CPTVal other = (CPTVal) obj;
		if (Float.floatToIntBits(end) != Float.floatToIntBits(other.end))
			return false;
		if (maxColor == null) {
			if (other.maxColor != null)
				return false;
		} else if (!maxColor.equals(other.maxColor))
			return false;
		if (minColor == null) {
			if (other.minColor != null)
				return false;
		} else if (!minColor.equals(other.minColor))
			return false;
		if (Float.floatToIntBits(start) != Float.floatToIntBits(other.start))
			return false;
		return true;
	}

	/**
	 *
	 * @param value
	 * @return true if value is greater than or equal to the start value and less than the end value
	 */
	public boolean contains(float value) {
		return this.start <= value && value < this.end;
	}


	public String toString() {
		return start + "\t" + CPT.tabDelimColor(minColor) + "\t" + end
		+ "\t" + CPT.tabDelimColor(maxColor);
	}

	@Override
	public Object clone() {
		return new CPTVal(start, minColor, end, maxColor);
	}
}
