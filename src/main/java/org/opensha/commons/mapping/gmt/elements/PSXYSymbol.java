package org.opensha.commons.mapping.gmt.elements;

import java.awt.Color;
import java.awt.geom.Point2D;

public class PSXYSymbol extends PSXYElement {
	
	/**
	 * default serial version UID
	 */
	private static final long serialVersionUID = 1l;
	
	public enum Symbol {
		SQUARE ("s"),
		DIAMOND ("d"),
		CIRCLE ("c"),
		STAR ("a"),
		OCTAGON ("g"),
		HEXAGON ("h"),
		INVERTED_TRIANGLE ("i"),
		PENTAGON ("n"),
		CROSS ("x"),
		Y_DASH ("y");
		
		private String val;
		Symbol(String val) {
			this.val = val;
		}
		
		public String val() {
			return val;
		}
	}
	
	private Symbol symbol;
	
	private double width;
	
	private Point2D pt;
	
	/**
	 * No-arg constructor for serialization
	 */
	public PSXYSymbol() {};
	
	public PSXYSymbol(Point2D pt, Symbol symbol, double width) {
		super();
		this.symbol = symbol;
		this.width = width;
		this.pt = pt;
	}
	
	public PSXYSymbol(Point2D pt, Symbol symbol, double width, double penWidth, Color penColor, Color fillColor) {
		super(penWidth, penColor, fillColor);
		this.symbol = symbol;
		this.width = width;
		this.pt = pt;
	}
	
	public String getSymbolString() {
		return "-S" + symbol.val() + width + "i";
	}

	public Symbol getSymbol() {
		return symbol;
	}

	public void setSymbol(Symbol symbol) {
		this.symbol = symbol;
	}

	public double getWidth() {
		return width;
	}

	public void setWidth(double width) {
		this.width = width;
	}

	public Point2D getPoint() {
		return pt;
	}

	public void setPoint(Point2D pt) {
		this.pt = pt;
	}

}
