package org.opensha.commons.mapping.gmt.elements;

import java.awt.Color;
import java.awt.geom.Point2D;

import org.opensha.commons.mapping.gmt.GMT_MapGenerator;

public class PSText {
	
	private Point2D pt;
	private Color color;
	private int fontSize;
	private String text;
	private Justify justify;
	
	public enum Justify {
		CENTER(""),
		LEFT_TOP("+jLT"),
		LEFT("+jL"),
		LEFT_BOTTOM("+jLB"),
		RIGHT_TOP("+jRT"),
		RIGHT("+jR"),
		RIGHT_MIDDLE("+jRB");
		
		private String str;

		private Justify(String str) {
			this.str = str;
		}
	}

	public PSText(Point2D pt, Color color, int fontSize, String text, Justify justify) {
		this.pt = pt;
		this.color = color;
		this.fontSize = fontSize;
		this.text = text;
		this.justify = justify;
	}
	
	public Point2D getPoint() {
		return pt;
	}
	
	public String getText() {
		return text;
	}
	
	public String getFontArg() {
		return "-F+f"+fontSize+"p,Helvetica,"+GMT_MapGenerator.getGMTColorString(color)+justify.str;
	}

}
