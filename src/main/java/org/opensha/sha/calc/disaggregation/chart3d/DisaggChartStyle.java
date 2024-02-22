package org.opensha.sha.calc.disaggregation.chart3d;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Rectangle2D;

import org.jfree.chart3d.style.StandardChartStyle;

class DisaggChartStyle extends StandardChartStyle {
	
	public DisaggChartStyle() {
		this(
				20, // axis label size
				16, // axis tick label size
				18 // legend label size
				);
	}
	
	public DisaggChartStyle(int axisLabelSize, int axisTickLabelSize, int legendLabelSize) {
		setAxisLabelFont(new Font(Font.SANS_SERIF, Font.PLAIN, axisLabelSize));
		setAxisTickLabelFont(new Font(Font.SANS_SERIF, Font.PLAIN, axisTickLabelSize));
		setLegendItemFont(new Font(Font.SANS_SERIF, Font.PLAIN, legendLabelSize));
		setGridlineColor(Color.BLACK);
		setGridlineStroke(new BasicStroke(1f));
		
//		setBackgroundPainter(new StandardRectanglePainter(Color.CYAN));
		setChartBoxColor(new Color(255,255,255,00));
//		setMarkerFillColor(Color.BLACK);
		setMarkerLineColor(Color.BLACK);
//		setcolor
		setXAxisGridlinesVisible(true);
		setYAxisGridlinesVisible(true);
		setZAxisGridlinesVisible(true);
		double width = 30;
		double height = 30;
		setLegendItemShape(new Rectangle2D.Double(-width/2, -height/2, width, height));
	}

}
