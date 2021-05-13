package org.opensha.commons.gui.plot.jfreechart.xyzPlot;

import java.io.IOException;
import java.util.List;

import javax.swing.JFrame;

import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.opensha.commons.data.xyz.EvenlyDiscrXYZ_DataSet;
import org.opensha.commons.data.xyz.XYZ_DataSet;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.cpt.CPT;

import com.google.common.collect.Lists;

/**
 * Simple class for quickly viewing XYZ plots - analogous to GraphWindow. Pass through methods are
 * not provided for saving, call getXYZPanel() to access these methods.
 * 
 * @author kevin
 *
 */
public class XYZPlotWindow extends JFrame {
	
	private XYZGraphPanel panel;
	
	private static int count = 0;
	
	public XYZPlotWindow(XYZ_DataSet xyz, CPT cpt, String title) {
		this(new XYZPlotSpec(xyz, cpt, title, null, null, null));
	}
	
	public XYZPlotWindow(XYZPlotSpec spec) {
		this(Lists.newArrayList(spec));
	}
	
	public XYZPlotWindow(XYZPlotSpec spec, Range xRange, Range yRange) {
		this(Lists.newArrayList(spec), Lists.newArrayList(xRange), Lists.newArrayList(yRange));
	}
	
	public XYZPlotWindow(List<XYZPlotSpec> specs) {
		this(specs, null, null);
	}
	
	public XYZPlotWindow(List<XYZPlotSpec> specs, List<Range> xRanges, List<Range> yRanges) {
		this(specs, xRanges, yRanges, null);
	}
	
	public XYZPlotWindow(List<XYZPlotSpec> specs, List<Range> xRanges, List<Range> yRanges,
			List<XYPlot> extraPlots) {
		this(specs, xRanges, yRanges, extraPlots, null);
	}
	
	private static XYZGraphPanel buildPlottedPanel(List<XYZPlotSpec> specs, List<Range> xRanges, List<Range> yRanges,
			List<XYPlot> extraPlots, List<Integer> weights) {
		XYZGraphPanel panel = new XYZGraphPanel();
		panel.drawPlot(specs, false, false, xRanges, yRanges, extraPlots, weights);
		return panel;
	}
	
	public XYZPlotWindow(List<XYZPlotSpec> specs, List<Range> xRanges, List<Range> yRanges,
			List<XYPlot> extraPlots, List<Integer> weights) {
		this(buildPlottedPanel(specs, xRanges, yRanges, extraPlots, weights));
	}
	
	public XYZPlotWindow(XYZGraphPanel panel) {
		this.panel = panel;
		this.setContentPane(panel.getChartPanel());
		
		++count;
		
		setTitle("XYZ Plot Window "+count);
		
		setSize(700, 800);
		setVisible(true);
	}
	
	public XYZGraphPanel getXYZPanel() {
		return panel;
	}
	
	public static void main(String[] args) throws IOException {
		EvenlyDiscrXYZ_DataSet data = new EvenlyDiscrXYZ_DataSet(150, 150, 0, 0, 5d);
		for (int xInd=0; xInd<data.getNumX(); xInd++) {
			for (int yInd=0; yInd<data.getNumY(); yInd++) {
//				if (Math.random()<0.1)
//					data.set(xInd, yInd, Double.NaN);
//				else
					data.set(xInd, yInd, Math.random()*(xInd + yInd));
			}
		}
		EvenlyDiscrXYZ_DataSet data2 = new EvenlyDiscrXYZ_DataSet(150, 50, 0, 0, 10d);
		for (int xInd=0; xInd<data2.getNumX(); xInd++) {
			for (int yInd=0; yInd<data2.getNumY(); yInd++) {
//				if (Math.random()<0.1)
//					data.set(xInd, yInd, Double.NaN);
//				else
					data2.set(xInd, yInd, Math.random()*(xInd + yInd));
			}
		}
		CPT cpt = GMT_CPT_Files.MAX_SPECTRUM.instance().rescale(0d, data.getMaxZ());
		
		XYZPlotSpec spec1 = new XYZPlotSpec(data, cpt, "Title", "X axis", "Y axis", "Z label");
//		XYZPlotSpec spec2 = new XYZPlotSpec(data2, cpt, "Title2", "X axis2", "Y axis2", "Z label2");
//		XYZPlotWindow wind = new XYZPlotWindow(Lists.newArrayList(spec1, spec2));
		XYZPlotWindow wind = new XYZPlotWindow(spec1);
		wind.panel.saveAsPNG("/tmp/fig.png");
		wind.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}

}
