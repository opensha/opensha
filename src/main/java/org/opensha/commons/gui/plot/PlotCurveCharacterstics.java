/*******************************************************************************
 * Copyright 2009 OpenSHA.org in partnership with
 * the Southern California Earthquake Center (SCEC, http://www.scec.org)
 * at the University of Southern California and the UnitedStates Geological
 * Survey (USGS; http://www.usgs.gov)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package org.opensha.commons.gui.plot;

import java.awt.Color;
import java.io.Serializable;

import org.opensha.commons.data.Named;

/**
 * <p>Title: PlotCurveCharacterstics</p>
 * <p>Description: This class handle characterstic feature of each curve, like its color, linetype(rendrer type), width</p>
 * Options are given in the PlotColorAndLineTypeSelectorControlPanel class.
 * @author : Nitin Gupta
 * @version 1.0
 */

public class PlotCurveCharacterstics implements Named, Cloneable, Serializable {

	// type of line
	private PlotLineType lineType;
	//width of the curve when plotted
	private float lineWidth;
	// symbol of curve
	private PlotSymbol symbol;
	//width of the curve when plotted
	private float symbolWidth;
	//color of the color
	private Color color;

	//name of selected curve
	private String name = "Data";
	
	/**
	 * New <code>PlotCurveCharacteristics</code> instance with only a line type, and no symbols
	 * 
	 * @param lineType
	 * @param lineWidth
	 * @param color
	 */
	public PlotCurveCharacterstics(PlotLineType lineType, float lineWidth, Color color) {
		this(lineType, lineWidth, null, 4f, color);
	}
	
	/**
	 * New <code>PlotCurveCharacteristics</code> instance with only symbols, and no line type
	 * 
	 * @param symbol
	 * @param symbolWidth
	 * @param color
	 */
	public PlotCurveCharacterstics(PlotSymbol symbol, float symbolWidth, Color color) {
		this(null, 1f, symbol, symbolWidth, color);
	}
	
	/**
	 * New <code>PlotCurveCharacteristics</code> instance.
	 * 
	 * @param lineType
	 * @param lineWidth
	 * @param symbol
	 * @param symbolWidth
	 * @param color
	 */
	public PlotCurveCharacterstics(PlotLineType lineType, float lineWidth, PlotSymbol symbol,
			float symbolWidth, Color color) {
		this("Data", lineType, lineWidth, symbol, symbolWidth, color);
	}

	/**
	 * New <code>PlotCurveCharacteristics</code> instance with all parameter specified.
	 * 
	 * @param name name of the curve
	 * @param lineType line type, or null
	 * @param lineWidth width of the line
	 * @param symbol symbol type, or null
	 * @param symbolWidth width of the symbol
	 * @param color color of the curve
	 */
	public PlotCurveCharacterstics(String name, PlotLineType lineType, float lineWidth, PlotSymbol symbol,
			float symbolWidth, Color color) {
		set(name, lineType, lineWidth, symbol, symbolWidth, color);
	}

	/**
	 * Sets the shape of curve
	 * @param lineType
	 */
	public void setLineType(PlotLineType lineType){
		this.lineType = lineType;
	}

	/**
	 * 
	 * @return line type for this curve
	 */
	public PlotLineType getLineType() {
		return lineType;
	}
	
	/**
	 * Sets the symbol of the curve
	 * @param symbol
	 */
	public void setSymbol(PlotSymbol symbol) {
		this.symbol = symbol;
	}
	
	/**
	 * 
	 * @return symbol for this curve
	 */
	public PlotSymbol getSymbol() {
		return symbol;
	}

	/**
	 * Set curve color
	 * @param curveColor
	 */
	public void setColor(Color color){
		this.color = color;
	}
	
	/**
	 * @return color for this curve
	 */
	public Color getColor() {
		return color;
	}

	/**
	 * set the curve line width
	 * @param width
	 */
	public void setLineWidth(float lineWidth){
		this.lineWidth = lineWidth;
	}
	
	/**
	 * @return the width of the line
	 */
	public float getLineWidth() {
		return lineWidth;
	}
	
	/**
	 * set the curve symbol width
	 * @param width
	 */
	public void setSymbolWidth(float symbolWidth){
		this.symbolWidth = symbolWidth;
	}
	
	/**
	 * @return the width of the symbol
	 */
	public float getSymbolWidth() {
		return symbolWidth;
	}
	
	public void set(PlotLineType lineType, float lineWidth, PlotSymbol symbol,
			float symbolWidth, Color color) {
		set(name, lineType, lineWidth, symbol, symbolWidth, color);
	}
	
	public void set(String name, PlotLineType lineType, float lineWidth, PlotSymbol symbol,
			float symbolWidth, Color color) {
		this.name = name;
		this.lineType = lineType;
		this.lineWidth = lineWidth;
		this.symbol = symbol;
		this.symbolWidth = symbolWidth;
		this.color = color;
	}
	
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Object clone() {
		return new PlotCurveCharacterstics(name, lineType, lineWidth, symbol, symbolWidth, color);
	}

	@Override
	public String toString() {
		String str = "";
		if (lineType != null && lineWidth > 0)
			str += "line: "+lineType+", width="+lineWidth;
		if (!str.isEmpty())
			str += "; ";
		if (symbol != null && symbolWidth > 0)
			str += "symbol: "+symbol+", width="+symbolWidth;
		if (!str.isEmpty() && !str.endsWith("; "))
			str += "; ";
		str += "color: "+color.getRed()+","+color.getGreen()+","+color.getBlue();
		return str;
	}

}
