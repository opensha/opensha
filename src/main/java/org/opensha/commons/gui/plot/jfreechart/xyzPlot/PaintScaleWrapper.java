package org.opensha.commons.gui.plot.jfreechart.xyzPlot;

import java.awt.Paint;

import org.jfree.chart.renderer.PaintScale;
import org.opensha.commons.util.cpt.CPT;

public class PaintScaleWrapper implements PaintScale {
	
	private CPT cpt;
	
	public PaintScaleWrapper(CPT cpt) {
		this.cpt = cpt;
	}
	
	public CPT getCPT() {
		return cpt;
	}

	@Override
	public double getLowerBound() {
		if (cpt.isLog10())
			return Math.pow(10, cpt.getMinValue());
		return cpt.getMinValue();
	}

	@Override
	public Paint getPaint(double value) {
		if (cpt.isLog10())
			value = Math.log10(value);
		return cpt.getColor((float)value);
	}

	@Override
	public double getUpperBound() {
		if (cpt.isLog10())
			return Math.pow(10, cpt.getMaxValue());
		return cpt.getMaxValue();
	}
	
}