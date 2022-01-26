package org.opensha.commons.mapping.gmt.elements;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.opensha.commons.util.cpt.CPT;

public class PSXYSymbolSet extends PSXYElement {
	
	/**
	 * default serial version UID
	 */
	private static final long serialVersionUID = 1l;
	
	private CPT cpt;
	private List<PSXYSymbol> symbols;
	private List<Double> vals;
	
	public PSXYSymbolSet() {
		super(0, null, null);
		symbols = new ArrayList<PSXYSymbol>();
		vals = new ArrayList<Double>();
	}
	
	public PSXYSymbolSet(CPT cpt, List<PSXYSymbol> symbols, List<Double> vals) {
		this(cpt, symbols, vals, 0, null, null);
	}
	
	public PSXYSymbolSet(CPT cpt, List<PSXYSymbol> symbols, List<Double> vals,
					double penWidth, Color penColor, Color fillColor) {
		super(penWidth, penColor, fillColor);
		this.cpt = cpt;
		this.symbols = symbols;
		this.vals = vals;
	}
	
	public void addSymbol(PSXYSymbol symbol, double val) {
		symbols.add(symbol);
		vals.add(val);
	}

	public CPT getCpt() {
		return cpt;
	}

	public void setCpt(CPT cpt) {
		this.cpt = cpt;
	}

	public List<PSXYSymbol> getSymbols() {
		return symbols;
	}

	public void setSymbols(List<PSXYSymbol> symbols) {
		this.symbols = symbols;
	}

	public List<Double> getVals() {
		return vals;
	}

	public void setVals(List<Double> vals) {
		this.vals = vals;
	}

}
