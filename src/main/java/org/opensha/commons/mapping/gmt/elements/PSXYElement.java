package org.opensha.commons.mapping.gmt.elements;

import java.awt.Color;
import java.io.Serializable;

import org.opensha.commons.mapping.gmt.GMT_MapGenerator;

public abstract class PSXYElement implements Serializable {
	
	/**
	 * default serial version UID
	 */
	private static final long serialVersionUID = 1l;
	
	private double penWidth = 1d;
	private Color penColor = Color.BLACK;
	
	private Color fillColor = null;
	
	public PSXYElement() {
		
	}
	
	public PSXYElement(double penWidth, Color penColor, Color fillColor) {
		this.penWidth = penWidth;
		this.penColor = penColor;
		this.fillColor = fillColor;
	}
	
	public String getPenString() {
		if (penColor == null)
			return "-W-";
		if (penWidth <= 0)
			return "-W-";
		
		return "-W" + penWidth + "p," + GMT_MapGenerator.getGMTColorString(penColor);
	}
	
	public String getFillString() {
		if (fillColor == null)
			return "";
		
		return "-G" + GMT_MapGenerator.getGMTColorString(fillColor);
	}

	public double getPenWidth() {
		return penWidth;
	}

	public void setPenWidth(double penWidth) {
		this.penWidth = penWidth;
	}

	public Color getPenColor() {
		return penColor;
	}

	public void setPenColor(Color penColor) {
		this.penColor = penColor;
	}

	public Color getFillColor() {
		return fillColor;
	}

	public void setFillColor(Color fillColor) {
		this.fillColor = fillColor;
	}

}
