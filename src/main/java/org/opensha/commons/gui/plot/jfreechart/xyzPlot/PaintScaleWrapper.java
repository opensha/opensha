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
		return cpt.getMinValue();
	}

	@Override
	public Paint getPaint(double value) {
		return cpt.getColor((float)value);
	}

	@Override
	public double getUpperBound() {
		return cpt.getMaxValue();
	}
	
}