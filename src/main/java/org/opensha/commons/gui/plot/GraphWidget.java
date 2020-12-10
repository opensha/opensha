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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.border.LineBorder;

import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.data.Range;

import com.google.common.collect.Lists;

/**
 * <p>Title: GraphWidget</p>
 * <p>Description: This is a widget which contains a GraphPanel and associated
 * buttons. It can be embedded into an application, or displayed in its own
 * GraphWindow.</p>
 * @author Kevin Milner
 * @author Nitin Gupta
 * @version 1.0
 */

public class GraphWidget extends JPanel {

	protected final static int W = 670;
	protected final static int H = 700;
	protected JPanel chartPane = new JPanel();
	protected GridBagLayout gridBagLayout1 = new GridBagLayout();
	protected BorderLayout borderLayout1 = new BorderLayout();
	protected JPanel buttonPanel = new JPanel();
	protected FlowLayout flowLayout1 = new FlowLayout();

	//boolean parameters for the Axis to check for log
	protected boolean xLog = false;
	protected boolean yLog = false;
	
	private List<Range> xRanges;
	private List<Range> yRanges;
	
	private PlotPreferences plotPrefs;

	//instance for the ButtonControlPanel
	protected ButtonControlPanel buttonControlPanel;

	//instance of the GraphPanel class
	protected GraphPanel graphPanel;
	
	private JPanel emptyPlotPanel = new JPanel();

	/**
	 * List of ArbitrarilyDiscretized functions and Weighted funstions
	 */
	protected List<PlotSpec> plotSpecs;

	/**
	 * for Y-log, 0 values will be converted to this small value
	 */
	protected double Y_MIN_VAL = 1e-16;
	
	/**
	 * Default constructor witha blank graph
	 */
	public GraphWidget() {
		this(new PlotSpec(new ArrayList<PlotElement>(), new ArrayList<PlotCurveCharacterstics>(), null, null, null));
	}

	/**
	 * Constructor for widget displaying the given plot data
	 * @param plotSpec
	 */
	public GraphWidget(PlotSpec plotSpec) {
		this(plotSpec, PlotPreferences.getDefault(), false, false, null, null);
	}

	/**
	 * Constructor for widget displaying the given plot data with preferences and all other options exposed.
	 * 
	 * @param plotSpec
	 * @param plotPrefs
	 * @param xLog
	 * @param yLog
	 * @param xRange
	 * @param yRange
	 */
	public GraphWidget(PlotSpec plotSpec, PlotPreferences plotPrefs, boolean xLog, boolean yLog, Range xRange, Range yRange) {
		if (plotPrefs == null)
			plotPrefs = PlotPreferences.getDefault();
		this.plotPrefs = plotPrefs;
		this.plotSpecs = Lists.newArrayList(plotSpec);
		graphPanel = new GraphPanel(plotPrefs);
		this.xLog = xLog;
		this.yLog = yLog;
		if (xRange != null)
			this.xRanges = Lists.newArrayList(xRange);
		if (yRange != null)
			this.yRanges = Lists.newArrayList(yRange);
		
		try {
			jbInit();
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		//Recreating the chart with all the default settings that existed in the main application.
		if (xLog)
			buttonControlPanel.setXLog(xLog);
		if (yLog)
			buttonControlPanel.setYLog(yLog);
		if (!xLog && !yLog)
			drawGraph();
	}
	
	//function to create the GUI component.
	protected void jbInit() throws Exception {
		this.setSize(W, H);
		this.setLayout(borderLayout1);

//		this.setOrientation(JSplitPane.VERTICAL_SPLIT);
		chartPane.setLayout(gridBagLayout1);
		buttonPanel.setLayout(flowLayout1);
//		this.getContentPane().add(chartSplitPane, BorderLayout.CENTER);
		this.add(chartPane, BorderLayout.CENTER);
		this.add(buttonPanel, BorderLayout.SOUTH);
//		this.setDividerLocation(560);
		//object for the ButtonControl Panel
		buttonControlPanel = new ButtonControlPanel(this, plotPrefs);
		buttonPanel.add(buttonControlPanel, null);
		emptyPlotPanel.setBorder(new LineBorder(Color.gray));
		emptyPlotPanel.setBackground(Color.white);
		togglePlot();
	}
	
	/**
	 * Get the GraphPanel instance for more fine tuned plotting adjustments
	 * @return
	 */
	public GraphPanel getGraphPanel() {
		return graphPanel;
	}

	/**
	 * Opens a file chooser and gives the user an opportunity to save the chart
	 * in PNG format.
	 *
	 * @throws IOException if there is an I/O error.
	 */
	public void save() throws IOException {
		graphPanel.save();
	}

	/**
	 * Save the chart in pdf format
	 * 
	 * @param fileName
	 * @throws IOException
	 */
	public void saveAsPDF(String fileName) throws IOException {
		graphPanel.saveAsPDF(fileName);
	}

	/**
	 * Allows the user to save the chart as PNG.
	 * @param fileName
	 * @throws IOException
	 */
	public void saveAsPNG(String fileName) throws IOException {
		graphPanel.saveAsPNG(fileName);
	}
	
	/**
	 * Save a txt file
	 * @param fileName
	 * @throws IOException 
	 */
	public void saveAsTXT(String fileName) throws IOException {
		graphPanel.saveAsTXT(fileName);
	}

	/**
	 * Creates a print job for the chart.
	 */
	public void print() {
		graphPanel.print();
	}

	/**
	 *
	 * @return the custom Range for the X-Axis
	 */
	public Range getUserX_AxisRange() {
		return xRanges == null ? null : xRanges.get(0);
	}

	/**
	 *
	 * @return the custom Range for the Y-Axis
	 */
	public Range getUserY_AxisRange() {
		return yRanges == null ? null : yRanges.get(0);
	}

	/**
	 *
	 * @return the current Range for the X-Axis
	 */
	public Range getX_AxisRange() {
		return graphPanel.getX_AxisRange();
	}
	
	public void setX_AxisRange(double minX, double maxX) {
		setX_AxisRange(new Range(minX, maxX));
	}
	
	public void setX_AxisRange(Range xRange) {
		if (xRange == null)
			this.xRanges = null;
		else
			this.xRanges = Lists.newArrayList(xRange);
		drawGraph();
	}

	/**
	 *
	 * @return the current Range for the Y-Axis
	 */
	public Range getY_AxisRange() {
		return graphPanel.getY_AxisRange();
	}
	
	public void setY_AxisRange(double minY, double maxY) {
		setY_AxisRange(new Range(minY, maxY));
	}
	
	public void setY_AxisRange(Range yRange) {
		if (yRange == null)
			yRanges = null;
		else
			yRanges = Lists.newArrayList(yRange);
		drawGraph();
	}

	/**
	 * tells the application if the xLog is selected
	 * @param xLog : boolean
	 */
	public void setX_Log(boolean xLog) {
		this.xLog = xLog;
		drawGraph();
	}

	/**
	 * tells the application if the yLog is selected
	 * @param yLog : boolean
	 */
	public void setY_Log(boolean yLog) {
		this.yLog = yLog;
		drawGraph();
	}

	/**
	 * sets the range for X and Y axis
	 * @param xMin : minimum value for X-axis
	 * @param xMax : maximum value for X-axis
	 * @param yMin : minimum value for Y-axis
	 * @param yMax : maximum value for Y-axis
	 *
	 */
	public void setAxisRange(double xMin, double xMax, double yMin, double yMax) {
		setAxisRange(new Range(xMin, xMax), new Range(yMin, yMax));
	}
	
	public void setAxisRange(Range xRange, Range yRange) {
		if (xRange == null)
			this.xRanges = null;
		else
			this.xRanges = Lists.newArrayList(xRange);
		if (yRange == null)
			yRanges = null;
		else
			yRanges = Lists.newArrayList(yRange);
		drawGraph();
	}

	/**
	 * Clear custom ranges for both axis.
	 */
	public void setAutoRange() {
		this.xRanges = null;
		this.yRanges = null;
		drawGraph();
	}
	
	/**
	 * 
	 * @return currently displayed plot
	 */
	public PlotSpec getPlotSpec() {
		return plotSpecs.get(0);
	}
	
	/**
	 * Set plot specification (also triggers an update)
	 * @param plotSpec
	 */
	public void setPlotSpec(PlotSpec plotSpec) {
		this.plotSpecs = Lists.newArrayList(plotSpec);
		drawGraph();
	}
	
	public void setMultiplePlotSpecs(List<PlotSpec> plotSpecs, List<Range> xRanges, List<Range> yRanges) {
		this.plotSpecs = plotSpecs;
		this.xRanges = xRanges;
		this.yRanges = yRanges;
		drawGraph();
	}

	/**
	 * to draw the graph
	 */
	public void drawGraph() {
		if (!isPlotEmpty())
			graphPanel.drawGraphPanel(plotSpecs, xLog, yLog, xRanges, yRanges);
		togglePlot();
	}
	
	private boolean isPlotEmpty() {
		return plotSpecs == null || plotSpecs.isEmpty() || plotSpecs.get(0).getPlotElems().isEmpty();
	}
	
	/* (non-Javadoc)
	 * @see org.opensha.sha.gui.infoTools.GraphWindowAPI#getPlottingFeatures()
	 */
	public void setPlotChars(List<PlotCurveCharacterstics> curveCharacteristics) {
		plotSpecs.get(0).setChars(curveCharacteristics);
		drawGraph();
	}

	/**
	 * checks if the user has plot the data window or plot window
	 */
	public void togglePlot() {chartPane.removeAll();
		if (isPlotEmpty()) {
			removeChartAndMetadata();
			buttonControlPanel.setEnabled(false);
		} else {
			buttonControlPanel.setEnabled(true);
			graphPanel.togglePlot();
			buttonControlPanel.updateToggleButtonText(graphPanel.isGraphOn());
			updateChart(graphPanel);
		}
		
	}
	
	private void updateChart(Component c) {
		chartPane.removeAll();
		chartPane.add(c, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0
				, GridBagConstraints.CENTER, GridBagConstraints.BOTH,
				new Insets(0, 0, 0, 0), 0, 0));
		try {
			chartPane.validate();
			chartPane.repaint();
		} catch (RuntimeException e) {
			System.err.println("Exception in painting chart, often non-critical so ignoring:");
			e.printStackTrace();
		}
	}

	/**
	 *
	 * @return the plotting feature like width, color and shape type of each
	 * curve in list.
	 */
	public List<PlotCurveCharacterstics> getPlottingFeatures() {
		return plotSpecs.get(0).getChars();
	}

	/**
	 *
	 * @return the X Axis Label
	 */
	public String getXAxisLabel() {
		return plotSpecs.get(0).getXAxisLabel();
	}

	/**
	 *
	 * @return Y Axis Label
	 */
	public String getYAxisLabel() {
		return plotSpecs.get(0).getYAxisLabel();
	}

	/**
	 *
	 * @return plot Title
	 */
	public String getPlotLabel() {
		return plotSpecs.get(0).getTitle();
	}

	/**
	 *
	 * sets  X Axis Label
	 */
	public void setXAxisLabel(String xAxisLabel) {
		plotSpecs.get(0).setXAxisLabel(xAxisLabel);
		this.drawGraph();
	}

	/**
	 *
	 * sets Y Axis Label
	 */
	public void setYAxisLabel(String yAxisLabel) {
		plotSpecs.get(0).setYAxisLabel(yAxisLabel);
		this.drawGraph();
	}

	/**
	 *
	 * sets plot Title
	 */
	public void setPlotLabel(String plotTitle) {
		plotSpecs.get(0).setTitle(plotTitle);
		this.drawGraph();
	}

	/**
	 * Set plot label font size
	 * 
	 * @param fontSize
	 */
	public void setPlotLabelFontSize(int fontSize) {
		this.plotPrefs.setPlotLabelFontSize(fontSize);
		this.drawGraph();
	}

	/**
	 * Set the tick label font size
	 * 
	 * @param fontSize
	 */
	public void setTickLabelFontSize(int fontSize) {
		this.plotPrefs.setTickLabelFontSize(fontSize);
		this.drawGraph();
	}

	/**
	 * Set the axis label font size
	 * 
	 * @param fontSize
	 */
	public void setAxisLabelFontSize(int fontSize) {
		this.plotPrefs.setAxisLabelFontSize(fontSize);
		this.drawGraph();
	}


	/**
	 *
	 * @return the tick label font
	 * Default is 10
	 */
	public int getTickLabelFontSize(){
		return plotPrefs.getTickLabelFontSize();
	}


	/**
	 *
	 * @return the axis label font size
	 */
	public int getPlotLabelFontSize(){
		return plotPrefs.getPlotLabelFontSize();
	}
	
	public PlotPreferences getPlotPrefs() {
		return plotPrefs;
	}

	public void setPlottingOrder(DatasetRenderingOrder order) {
		graphPanel.setRenderingOrder(order);
	}
	
	public DatasetRenderingOrder getPlottingOrder() {
		return graphPanel.getRenderingOrder();
	}
	
	public ButtonControlPanel getButtonControlPanel() {
		return buttonControlPanel;
	}
	
	public void removeChartAndMetadata() {
		updateChart(emptyPlotPanel);
		graphPanel.removeChartAndMetadata();
		validate();
	}

	public void setBackgroundColor(Color background) {
		graphPanel.setBackgroundColor(background);
		drawGraph();
	}
	
	public void setGriddedFuncAxesTicks(boolean histogramAxesTicks) {
		graphPanel.setGriddedFuncAxesTicks(histogramAxesTicks);
		drawGraph();
	}
}
