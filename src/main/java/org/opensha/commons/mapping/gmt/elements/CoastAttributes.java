package org.opensha.commons.mapping.gmt.elements;

import java.awt.Color;
import java.io.Serializable;

public class CoastAttributes implements Serializable {
	/**
	 * default serial version UID
	 */
	private static final long serialVersionUID = 1l;
	
	private Color fillColor;
	
	private Color lineColor;
	private double lineSize;
	
	/**
	 * Default constructor, for filled ocean
	 */
	public CoastAttributes() {
		this(new Color(17, 73, 71), 1d, new Color(17, 73, 71));
	}
	
	/**
	 * Draw coastline only, black with the specified size
	 * 
	 * @param lineSize
	 */
	public CoastAttributes(double lineSize) {
		this(Color.GRAY, lineSize);
	}
	
	/**
	 * Draw coastline only with the specified color/size
	 * 
	 * @param lineColor
	 * @param lineSize
	 */
	public CoastAttributes(Color lineColor, double lineSize) {
		this(lineColor, lineSize, null);
	}
	
	/**
	 * Fill the coast with the specified color, the line will be drawn with the same color.
	 * 
	 * @param fillColor
	 */
	public CoastAttributes(Color fillColor) {
		this(fillColor, 1d, fillColor);
	}
	
	/**
	 * Fill coast and draw coastline with the specified color/size
	 * 
	 * @param lineColor
	 * @param lineSize
	 */
	public CoastAttributes(Color lineColor, double lineSize, Color fillColor) {
		this.lineColor = lineColor;
		this.lineSize = lineSize;
		this.fillColor = fillColor;
	}

	public Color getFillColor() {
		return fillColor;
	}

	public void setFillColor(Color fillColor) {
		this.fillColor = fillColor;
	}

	public Color getLineColor() {
		return lineColor;
	}

	public void setLineColor(Color lineColor) {
		this.lineColor = lineColor;
	}

	public double getLineSize() {
		return lineSize;
	}

	public void setLineSize(double lineSize) {
		this.lineSize = lineSize;
	}
}
