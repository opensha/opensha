package org.opensha.commons.gui.plot.jfreechart.tornado;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.JFrame;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYBoxAnnotation;
import org.jfree.chart.annotations.XYDataImageAnnotation;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.GraphWidget;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;

import com.google.common.collect.Lists;

public class BarTest {

	public static void main(String[] args) {
//		ChartFactory.createxy
//		List<XYAnnotation> anns = Lists.newArrayList();
//		
//		anns.add(create(4d, 10d, 5d, 12d, Color.RED));
//		anns.add(create(4d, 7d, 8d, 7d, Color.GREEN));
//		anns.add(create(0d, 3d, 4d, 3d, Color.BLUE));
		
		List<PlotElement> elems = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		
		elems.add(createLine(4d, 11d, 5d, 17d));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID_BAR, 2f, Color.RED));
//		elems.add(elems.get(elems.size()-1));
//		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		elems.add(createLine(4d, 7d, 8d, 7d));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID_BAR, 2f, Color.GREEN));
		elems.add(createLine(0d, 3d, 4d, 3d));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID_BAR, 2f, Color.BLUE));
		
		XY_DataSet line = new DefaultXY_DataSet();
		line.set(4d, 4d);
		line.set(6d,5d);
		line.set(8d,9d);
		elems.add(line);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID_BAR, 0.3f, Color.ORANGE));
		
		// vertical line
		elems.add(createLine(4d, 0d, 4d, 15d));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK));
		
		PlotSpec spec = new PlotSpec(elems, chars, "Test", "X", "Y");
//		spec.setPlotAnnotations(anns);
		
		GraphWindow gw = new GraphWindow(spec);
		gw.setAxisRange(0, 20, 0, 20);
		gw.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
	
	private static XYAnnotation create(double x0, double y0, double x1, double y1, Color color) {
//		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
//		image.setRGB(0, 0, color.getRGB());
//		return new XYDataImageAnnotation(image, x1, y1, x2-x1, y2-y1);
		
		return new XYBoxAnnotation(x0, y0, x1, y1, null, null, color);
	}
	
	private static XY_DataSet createLine(double x0, double y0, double x1, double y1) {
		XY_DataSet line = new DefaultXY_DataSet();
		line.set(x0, y0);
		line.set(x1, y1);
		return line;
	}

}
