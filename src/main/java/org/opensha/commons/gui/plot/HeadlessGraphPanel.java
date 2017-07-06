package org.opensha.commons.gui.plot;

import java.util.List;

import org.jfree.data.Range;

public class HeadlessGraphPanel extends GraphPanel {
	
	private boolean xLog=false, yLog=false;
	
	private Range xRange, yRange;
	
	public HeadlessGraphPanel() {
		this(PlotPreferences.getDefault());
	}
	
	public HeadlessGraphPanel(PlotPreferences plotPrefs) {
		super(plotPrefs);
	}
	
	public void drawGraphPanel(PlotSpec spec) {
		drawGraphPanel(spec, xLog, yLog, xRange, yRange);
	}
	
	public void drawGraphPanel(PlotSpec spec, boolean xLog, boolean yLog) {
		drawGraphPanel(spec, xLog, yLog, xRange, yRange);
	}
	
	public void drawGraphPanel(String xAxisName, String yAxisName, List<? extends PlotElement> elems,
			List<PlotCurveCharacterstics> plotChars, String title) {
		drawGraphPanel(xAxisName, yAxisName, elems, plotChars, xLog, yLog, title, xRange, yRange);
	}
	
	@Override
	public void drawGraphPanel(String xAxisName, String yAxisName, List<? extends PlotElement> elems,
			List<PlotCurveCharacterstics> plotChars, boolean xLog, boolean yLog,
			String title, Range xRange, Range yRange) {
		this.xLog = xLog;
		this.yLog = yLog;
		this.xRange = xRange;
		this.yRange = yRange;
		
		super.drawGraphPanel(xAxisName, yAxisName, elems, plotChars, xLog, yLog, title, xRange, yRange);
		
		this.setVisible(true);
		
		this.togglePlot();
		
		this.validate();
		this.repaint();
	}
	
	public boolean getXLog() {
		return xLog;
	}

	public void setXLog(boolean xLog) {
		this.xLog = xLog;
	}

	public boolean getYLog() {
		return yLog;
	}

	public void setYLog(boolean yLog) {
		this.yLog = yLog;
	}
	
	public void setUserBounds(double minX, double maxX, double minY, double maxY) {
		setUserBounds(new Range(minX, maxX), new Range(minY, maxY));
	}
	
	public void setUserBounds(Range xRange, Range yRange) {
		this.xRange = xRange;
		this.yRange = yRange;
	}
	
	public void setAutoRange() {
		this.xRange = null;
		this.yRange = null;
	}
}
