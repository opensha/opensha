package org.opensha.commons.gui.plot.jfreechart.tornado;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.data.Range;
import org.jfree.chart.ui.TextAnchor;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.GraphPanel;
import org.opensha.commons.gui.plot.GraphWindow;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

public class TornadoDiagram {
	
	public static CPT getGrayTornadoCPT() {
		CPT cpt = new CPT();
		cpt.add(new CPTVal(0f, Color.LIGHT_GRAY, 1f, Color.BLACK));
		return cpt;
	}
	
	private String title;
	private String xAxisLabel;
	private String yAxisLabel;
	private double plotMean;
	private CPT cpt;
	
	private Table<String, String, Double> tornadoVals;
	
	public TornadoDiagram(String title, String xAxisLabel, String yAxisLabel, double plotMean) {
		this(title, xAxisLabel, yAxisLabel, plotMean, getGrayTornadoCPT());
	}
	
	public TornadoDiagram(String title, String xAxisLabel, String yAxisLabel, double plotMean, CPT cpt) {
		this.cpt = cpt;
		this.plotMean = plotMean;
		this.title = title;
		this.xAxisLabel = xAxisLabel;
		this.yAxisLabel = yAxisLabel;
		tornadoVals = HashBasedTable.create();
	}
	
	public void addTornadoValue(String category, String choice, double val) {
		tornadoVals.put(category, choice, val);
	}
	
	private class PlotTornadoLevel extends NameValPair {
		List<NameValPair> choiceVals;
		
		double maxAbsRel;
		
		public PlotTornadoLevel(String category, double plotMean) {
			super(category, getTotalSwing(category, plotMean));
			maxAbsRel = getMaxAbsRel(category, plotMean);
			
			choiceVals = Lists.newArrayList();
			
			Map<String, Double> choiceMap = tornadoVals.row(category);
			for (String choice : choiceMap.keySet()) {
				double relVal = choiceMap.get(choice) - plotMean;
				choiceVals.add(new NameValPair(choice, relVal));
			}
			
			Collections.sort(choiceVals);
		}
	}
	
	/**
	 * max(abs(val - plotMean))
	 * @param category
	 * @param plotMean
	 * @return
	 */
	private double getMaxAbsRel(String category, double plotMean) {
		double catMax = 0d;
		for (double val : tornadoVals.row(category).values()) {
			double absRel = Math.abs(val - plotMean);
			if (absRel > catMax)
				catMax = absRel;
		}
		return catMax;
	}
	
	/**
	 * max(abs(val - plotMean))
	 * @param category
	 * @param plotMean
	 * @return
	 */
	private double getTotalSwing(String category, double plotMean) {
		MinMaxAveTracker track = new MinMaxAveTracker(); 
		for (double val : tornadoVals.row(category).values()) {
			track.addValue(val);
		}
		track.addValue(plotMean);
		return track.getMax() - track.getMin();
	}
	
	// sorts by absolute value
	private class NameValPair implements Comparable<NameValPair> {
		
		String name;
		double val;

		public NameValPair(String name, double val) {
			this.name = name;
			this.val = val;
		}

		@Override
		public int compareTo(NameValPair o) {
			return -Double.compare(Math.abs(val), Math.abs(o.val));
		}
		
	}
	
	public Range getXRange() {
		double maxRelAbsDiff = 0d;
		for (Cell<String, String, Double> cell : tornadoVals.cellSet()) {
			double relAbsDiff = Math.abs(cell.getValue()-plotMean);
			if (relAbsDiff > maxRelAbsDiff)
				maxRelAbsDiff = relAbsDiff;
		}
		return new Range(plotMean - ann_buff_x_mult_plot*maxRelAbsDiff, plotMean + ann_buff_x_mult_plot*maxRelAbsDiff);
	}
	
	public Range getYRange() {
		return new Range(0d+0.5*deltaY, tornadoVals.rowKeySet().size()+0.5*deltaY);
	}
	
	public PlotSpec getPlot() {
		return getPlot( null);
	}
	
	private static final double startY = 1d;
	private static final double deltaY = 1d;
	private static final float thickness = 0.4f;
	private static final double ann_buff_x_mult = 1.3;
	private static final double ann_buff_x_mult_plot = ann_buff_x_mult+0.05;
	
	public PlotSpec getPlot(List<String> categoryOrder) {
		Preconditions.checkState(tornadoVals.size() > 0, "Must have at least one value for tornado!");
		List<PlotTornadoLevel> levels = Lists.newArrayList();
		if (categoryOrder == null) {
			for (String category : tornadoVals.rowKeySet())
				levels.add(new PlotTornadoLevel(category, plotMean));
			Collections.sort(levels);
		} else {
			for (String category : categoryOrder) {
				Preconditions.checkState(tornadoVals.rowKeySet().contains(category),
						"'"+category+"' not found in table");
				levels.add(new PlotTornadoLevel(category, plotMean));
			}
		}
		// we plot from bottom to top
		Collections.reverse(levels);
		
		double maxRelDiff = 0d;
		for (PlotTornadoLevel level : levels) {
			if (level.maxAbsRel > maxRelDiff)
				maxRelDiff = level.maxAbsRel;
		}
		
		List<PlotElement> elems = Lists.newArrayList();
		List<PlotCurveCharacterstics> chars = Lists.newArrayList();
		List<XYAnnotation> anns = Lists.newArrayList();
		
		double y = startY;
		
		for (PlotTornadoLevel level : levels) {
			double leftMostX = Double.POSITIVE_INFINITY;
			String leftMostName = null;
			double rightMostX = Double.NEGATIVE_INFINITY;
			String rightMostName = null;
			
			for (NameValPair pair : level.choiceVals) {
				System.out.println("Plotting "+level.name+":"+pair.name+". val="+pair.val);
				double val = pair.val;
				String name = pair.name;
				if (val < leftMostX) {
					leftMostX = val;
					leftMostName = name;
				}
				if (val > rightMostX) {
					rightMostX = val;
					rightMostName = name;
				}
				
				float relMag = (float)(Math.abs(val)/maxRelDiff);
				Color c = cpt.getColor(relMag);
				System.out.println(relMag+": "+c);
				elems.add(line(level.name+": "+pair.name, plotMean, y, plotMean+val, y));
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID_BAR, thickness, c));
			}
			
			XYTextAnnotation leftAnn = new XYTextAnnotation(leftMostName, plotMean-ann_buff_x_mult*maxRelDiff, y);
			leftAnn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
			leftAnn.setTextAnchor(TextAnchor.CENTER_LEFT);
			
			XYTextAnnotation rightAnn = new XYTextAnnotation(rightMostName, plotMean+ann_buff_x_mult*maxRelDiff, y);
			rightAnn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
			rightAnn.setTextAnchor(TextAnchor.CENTER_RIGHT);
			
			anns.add(leftAnn);
			anns.add(rightAnn);
			
			y += deltaY;
		}
		
		elems.add(line("Mean", plotMean, 0, plotMean, y-0.5*deltaY));
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
		
		PlotSpec spec = new PlotSpec(elems, chars, title, xAxisLabel, yAxisLabel);
		spec.setPlotAnnotations(anns);
		return spec;
	}
	
	public GraphWindow displayPlot() {
		PlotSpec spec = getPlot(null);
		GraphWindow gw = new GraphWindow(spec);
		setupGraphPanel(gw.getGraphWidget().getGraphPanel());
		gw.setAxisRange(getXRange(), getYRange());
		return gw;
	}
	
	public HeadlessGraphPanel getHeadlessPlot(int width, int height) {
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setBackgroundColor(Color.WHITE);
		
		gp.setUserBounds(getXRange(), getYRange());
		gp.drawGraphPanel(getPlot());
		setupGraphPanel(gp);
		gp.getChartPanel().setSize(width, height);
		
		return gp;
	}
	
	private static void setupGraphPanel(GraphPanel gp) {
		gp.getYAxis().setTickLabelsVisible(false);
	}
	
	private static XY_DataSet line(String name, double x0, double y0, double x1, double y1) {
		DefaultXY_DataSet line = new DefaultXY_DataSet();
		line.setName(name);
		line.set(x0, y0);
		line.set(x1, y1);
		
		return line;
	}

}
