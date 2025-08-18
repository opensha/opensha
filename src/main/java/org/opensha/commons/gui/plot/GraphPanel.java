package org.opensha.commons.gui.plot;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PrinterException;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.border.LineBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import org.apache.commons.lang3.SystemUtils;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.LegendItemSource;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.title.Title;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.Range;
import org.jfree.data.xy.XYDataset;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.WeightedFuncListforPlotting;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.function.XY_DataSetList;
import org.opensha.commons.gui.plot.jfreechart.CustomOffsetNumberAxis;
import org.opensha.commons.gui.plot.jfreechart.DiscretizedFunctionXYDataSet;
import org.opensha.commons.gui.plot.jfreechart.JFreeLogarithmicAxis;
import org.opensha.commons.gui.plot.jfreechart.MyTickUnits;
import org.opensha.commons.gui.plot.jfreechart.PixelSpacePaintScaleLegend;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.PaintScaleWrapper;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYIntervalBlockRenderer;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZDatasetWrapper;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.gui.plot.pdf.PDF_UTF8_FontMapper;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.CustomFileFilter;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.cpt.CPT;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.itextpdf.awt.FontMapper;
import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;

/**
 * <p>
 * Title: GraphPanel
 * </p>
 * <p>
 * Description: This class shows the JFreechart Panel in a window. It plot
 * curves using JFrechart package and if application supports allowing user to
 * specify different styles, colors and width of each curve the this application
 * plots that for the person.
 * </p>
 * 
 * @author : Nitin Gupta
 * @version 1.0
 */

public class GraphPanel extends JSplitPane {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	// mesage needed in case of show data if plot is not available
	private final static String NO_PLOT_MSG = "No Plot Data Available";

	private JFileChooser chooser;

	private DatasetRenderingOrder renderingOrder = DatasetRenderingOrder.FORWARD;

	/**
	 * default color scheme for plotting curves
	 */
	public static final Color[] defaultColor = { Color.red, Color.blue, Color.green, Color.darkGray, Color.magenta,
			Color.cyan, Color.orange, Color.pink, Color.yellow, Color.gray };

	private SimpleAttributeSet setLegend;

	// accessible components
	// private JSplitPane chartSplitPane;
	private JPanel chartPane;
	private JTextPane metadataText;
	private JScrollPane dataScrollPane;
	private JTextArea dataTextArea;
	private ChartPanel chartPanel;

	private static Dimension minPanelSize = new Dimension(320, 120);

	// dataset to handover the data to JFreechart
//	private DiscretizedFunctionXYDataSet data = new DiscretizedFunctionXYDataSet();
	// list containing Discretized function set
	private XY_DataSetList plottedFuncs = new XY_DataSetList();
	private List<PlotCurveCharacterstics> plottedChars = Lists.newArrayList();

	// checks if weighted function exists in the list of functions
	private HashSet<Integer> weightedfuncListIndexes;

	/**
	 * for Y-log, 0 values will be converted to this small value
	 */
	public static final double LOG_Y_MIN_VAL = 1e-16;

	private XYPlot plot;

	// Create the x-axis and y-axis - either normal or log
	// xaxis1 and yAxis1 replica of the x-Axis and y-Axis object, in case error
	// occurs
	// and we have revert back the Axis
	ValueAxis xAxis, prevXAxis;
	ValueAxis yAxis, prevYAxis;

	private boolean xAxisInverted = false;
	private boolean[] xAxisInverteds = null;
	private boolean yAxisInverted = false;
	private boolean[] yAxisInverteds = null;

	// if true, multiple plots will be share the Y axis (false: X axis)
	private boolean combinedYAxis = false;

	// light blue color
	private Color backgroundColor = new Color(200, 200, 230);

	// Keeps track when to toggle between the data and chart.
	private boolean graphOn = false;

	// This ArrayList stores the legend for various
	private List<String> legendString;

	private PlotPreferences plotPrefs;

	JPanel emptyPlotPanel;

	private boolean griddedFuncAxesTicks = false;

	/**
	 * class constructor
	 */
	public GraphPanel(PlotPreferences plotPrefs) {
		super(JSplitPane.VERTICAL_SPLIT, true);
		this.plotPrefs = plotPrefs;
		this.backgroundColor = plotPrefs.getBackgroundColor();
		setResizeWeight(1);
		setBorder(null);

		try {
			jbInit();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Function to add GUI component to Graphpanel class
	 * 
	 * @throws Exception
	 */
	void jbInit() throws Exception {

		dataTextArea = new JTextArea(NO_PLOT_MSG);
		// dataTextArea.setBorder(BorderFactory.createEtchedBorder());
		dataTextArea.setLineWrap(true);

		dataScrollPane = new JScrollPane();
		// dataScrollPane.setBorder(BorderFactory.createEtchedBorder());
		dataScrollPane.getViewport().add(dataTextArea, null);

		chartPane = new JPanel(new BorderLayout());
		chartPane.setMinimumSize(minPanelSize);
		chartPane.setPreferredSize(minPanelSize);
		emptyPlotPanel = new JPanel();
		emptyPlotPanel.setBorder(new LineBorder(Color.gray));
		emptyPlotPanel.setBackground(Color.white);
		chartPane.add(emptyPlotPanel, BorderLayout.CENTER);

		metadataText = new JTextPane();
		metadataText.setEditable(false);
		JScrollPane metadataScrollPane = new JScrollPane();
		metadataScrollPane.getViewport().add(metadataText);
		metadataScrollPane.setMinimumSize(minPanelSize);
		metadataScrollPane.setPreferredSize(minPanelSize);
		metadataScrollPane.setBorder(BorderFactory.createLineBorder(Color.gray, 1));

		setTopComponent(chartPane);
		setBottomComponent(metadataScrollPane);

	}

	private boolean isBlankCurve(PlotCurveCharacterstics chars) {
		return (chars.getLineType() == null || chars.getLineWidth() <= 0f)
				&& (chars.getSymbol() == null || chars.getSymbolWidth() <= 0f);
	}

	/**
	 * Draws curves
	 * 
	 * @param xAxisName          : X-Axis Label
	 * @param yAxisName          : Y-Axis Label
	 * @param elems              : List containing individual plot elements
	 * @param chars              : List containing plot curve characteristics
	 * @param xLog               : boolean tell if xLog is selected
	 * @param yLog               : boolean tells if yLog is selected
	 * @param title              : JFreechart window title
	 * @param buttonControlPanel : Instance of class which called this method.
	 */
	public void drawGraphPanel(String xAxisName, String yAxisName, List<? extends PlotElement> elems,
			List<PlotCurveCharacterstics> plotChars, boolean xLog, boolean yLog, String title) {
		drawGraphPanel(xAxisName, yAxisName, elems, plotChars, xLog, yLog, title, null, null);
	}

	/**
	 * Draws curves
	 * 
	 * @param xAxisName          : X-Axis Label
	 * @param yAxisName          : Y-Axis Label
	 * @param elems              : List containing individual plot elements
	 * @param chars              : List containing plot curve characteristics
	 * @param xLog               : boolean tell if xLog is selected
	 * @param yLog               : boolean tells if yLog is selected
	 * @param title              : JFreechart window title
	 * @param buttonControlPanel : Instance of class which called this method.
	 * @param xRange             : x range (or null for auto scale)
	 * @param yRange             : y range (or null for auto scale)
	 */
	public void drawGraphPanel(String xAxisName, String yAxisName, List<? extends PlotElement> elems,
			List<PlotCurveCharacterstics> plotChars, boolean xLog, boolean yLog, String title, Range xRange,
			Range yRange) {
		PlotSpec spec = new PlotSpec(elems, plotChars, title, xAxisName, yAxisName);
		drawGraphPanel(spec, xLog, yLog, xRange, yRange);
	}

	/**
	 * Draw the graph with a single plot and auto scaling
	 * 
	 * @param spec PlotSpec instance
	 * @param xLog if true, log X axis
	 * @param yLog if true, log Y axis
	 */
	public void drawGraphPanel(PlotSpec spec, boolean xLog, boolean yLog) {
		drawGraphPanel(spec.getXAxisLabel(), spec.getYAxisLabel(), spec.getPlotElems(), spec.getChars(), xLog, yLog,
				spec.getTitle(), null, null);
	}

	/**
	 * Draw the graph with a single plot
	 * 
	 * @param spec   PlotSpec instance
	 * @param xLog   if true, log X axis
	 * @param yLog   if true, log Y axis
	 * @param xRange X axis range, or null for auto scale
	 * @param yRange Y axis range, or null for auto scale
	 */
	public void drawGraphPanel(PlotSpec spec, boolean xLog, boolean yLog, Range xRange, Range yRange) {
		List<PlotSpec> specs = Lists.newArrayList(spec);
		List<Range> xRanges;
		if (xRange == null)
			xRanges = null;
		else
			xRanges = Lists.newArrayList(xRange);
		List<Range> yRanges;
		if (yRange == null)
			yRanges = null;
		else
			yRanges = Lists.newArrayList(yRange);
		drawGraphPanel(specs, xLog, yLog, xRanges, yRanges);
	}

	/**
	 * Draw the graph with support for multiple subplots
	 * 
	 * @param specs   list of PlotSpec instances
	 * @param xLog    if true, log X axis
	 * @param yLog    if true, log Y axis
	 * @param xRanges X axis ranges for each subplot. If non null size > 1, combined
	 *                plots will share a Y axis
	 * @param yRanges X axis ranges for each subplot. If non null size > 1, combined
	 *                plots will share an X axis
	 */
	public void drawGraphPanel(List<? extends PlotSpec> specs, boolean xLog, boolean yLog, List<Range> xRanges,
			List<Range> yRanges) {
		drawGraphPanel(specs, Lists.newArrayList(xLog), Lists.newArrayList(yLog), xRanges, yRanges);
	}

	/**
	 * Draw the graph with support for multiple subplots
	 * 
	 * @param specs   list of PlotSpec instances
	 * @param xLog    if true, log X axis
	 * @param yLog    if true, log Y axis
	 * @param xRanges X axis ranges for each subplot. If non null size > 1, combined
	 *                plots will share a Y axis
	 * @param yRanges X axis ranges for each subplot. If non null size > 1, combined
	 *                plots will share an X axis
	 */
	public void drawGraphPanel(List<? extends PlotSpec> specs, List<Boolean> xLogs, List<Boolean> yLogs,
			List<Range> xRanges, List<Range> yRanges) {
		// Starting
		String S = "drawGraphPanel(): ";

		// make sure ranges aren't specified, or only one has multiple (for combined
		// plots)
		Preconditions.checkArgument(xRanges == null || yRanges == null || xRanges.size() <= 1 || yRanges.size() <= 1);

		if (specs.size() > 1) {
			if (xRanges != null && xRanges.size() > 1)
				combinedYAxis = true;
			else if (yRanges != null && yRanges.size() > 1)
				combinedYAxis = false;
			else if (xRanges != null && xRanges.size() == 1)
				combinedYAxis = false;
			else if (yRanges != null && yRanges.size() == 1)
				combinedYAxis = true;
		}

		plottedFuncs.clear();

		List<PlotElement> plottedElems = Lists.newArrayList();

		// flags to check if the exception was thrown on selection of the x-log or
		// y-log.
		boolean logErrorFlag = false;

		// getting the axis font size
		int axisFontSize = plotPrefs.getAxisLabelFontSize();
		// getting the tick label font size
		int tickFontSize = plotPrefs.getTickLabelFontSize();
		int legendFontSize = plotPrefs.getLegendFontSize();

		// create the standard ticks so that smaller values too can plotted on the chart
		TickUnits units = MyTickUnits.createStandardTickUnits();

		List<ValueAxis> subXAxis = Lists.newArrayList();
		List<ValueAxis> subYAxis = Lists.newArrayList();

		for (int i = 0; i < specs.size(); i++) {
			PlotSpec spec = specs.get(i);

			boolean xLog = xLogs.size() > 1 ? xLogs.get(i) : xLogs.get(0);
			boolean yLog = yLogs.size() > 1 ? yLogs.get(i) : yLogs.get(0);

			ValueAxis xAxis, yAxis;
			try {

				/// check if x log is selected or not
				if (xLog) {
					JFreeLogarithmicAxis logAxis = new JFreeLogarithmicAxis(spec.getXAxisLabel());
					// this fixes the overlap issue with the bottom of the plot
					logAxis.setVerticalAnchorShift(4);
					xAxis = logAxis;
				} else
					xAxis = new NumberAxis(spec.getXAxisLabel());

				if (!xLog && griddedFuncAxesTicks && i == 0) {
					for (PlotElement elem : spec.getPlotElems()) {
						if (elem instanceof EvenlyDiscretizedFunc) {
							xAxis = new CustomOffsetNumberAxis((EvenlyDiscretizedFunc) elem, spec.getXAxisLabel());
							break;
						}
					}
				}

				// if (!xLog)
				// xAxis.setAutoRangeIncludesZero(true);
				// else
				if (xAxis instanceof NumberAxis)
					((NumberAxis) xAxis).setAutoRangeIncludesZero(false);
				if (!(xAxis instanceof CustomOffsetNumberAxis))
					xAxis.setStandardTickUnits(units);
				xAxis.setTickMarksVisible(false);
				xAxis.setTickLabelInsets(new RectangleInsets(3, 10, 3, 10));
				// Axis label font
				Font axisLabelFont = xAxis.getLabelFont();
				xAxis.setLabelFont(new Font(axisLabelFont.getFontName(), axisLabelFont.getStyle(), axisFontSize));

				// tick label font
				Font axisTickFont = xAxis.getTickLabelFont();
				xAxis.setTickLabelFont(new Font(axisTickFont.getFontName(), axisTickFont.getStyle(), tickFontSize));

				boolean inverted = xAxisInverted
						|| (xAxisInverteds != null && xAxisInverteds.length > i && xAxisInverteds[i]);
				xAxis.setInverted(inverted);

				// added to have the minimum range within the Upper and Lower Bound of the Axis
				// xAxis.setAutoRangeMinimumSize(.1);

				/*
				 * to set the range of the axis on the input from the user if the range combo
				 * box is selected
				 */
				if (xRanges != null && xRanges.size() > i && xRanges.get(i) != null) {
					Range xRange = xRanges.get(i);
					xAxis.setRange(xRange);
					if (xLog)
						Preconditions.checkState(xRange.getLowerBound() > 0 && Double.isFinite(xRange.getUpperBound()),
								"X is log spacing, but x range contains 0 or is infinite: [%s %s]",
								xRange.getLowerBound(), xRange.getUpperBound());
					Preconditions.checkState(xRange.getUpperBound() > xRange.getLowerBound(),
							"X range upper bound is not above lower bound: [%s %s]", xRange.getLowerBound(),
							xRange.getUpperBound());
					Preconditions.checkState(
							Double.isFinite(xRange.getUpperBound()) && Double.isFinite(xRange.getLowerBound()),
							"X range not finite: [%s %s]", xRange.getLowerBound(), xRange.getUpperBound());

				}

			} catch (Exception e) {
				// e.printStackTrace();
				JOptionPane.showMessageDialog(this, e.getMessage(), "X-Plot Error", JOptionPane.OK_OPTION);
				graphOn = false;
				xLog = false;
				xAxis = prevXAxis;
				logErrorFlag = true;
			}

			try {
				/// check if y log is selected or not
				if (yLog)
					yAxis = new JFreeLogarithmicAxis(spec.getYAxisLabel());
				else
					yAxis = new NumberAxis(spec.getYAxisLabel());

				if (yAxis instanceof NumberAxis) {
					if (!yLog)
						((NumberAxis) yAxis).setAutoRangeIncludesZero(true);
					else
						((NumberAxis) yAxis).setAutoRangeIncludesZero(false);
				}

				yAxis.setStandardTickUnits(units);
				yAxis.setTickMarksVisible(false);

				// Axis label font
				Font axisLabelFont = yAxis.getLabelFont();
				yAxis.setLabelFont(new Font(axisLabelFont.getFontName(), axisLabelFont.getStyle(), axisFontSize));

				// tick label font
				Font axisTickFont = yAxis.getTickLabelFont();
				yAxis.setTickLabelFont(new Font(axisTickFont.getFontName(), axisTickFont.getStyle(), tickFontSize));
				// added to have the minimum range within the Upper and Lower Bound of the Axis
				// yAxis.setAutoRangeMinimumSize(.1);

				boolean inverted = yAxisInverted
						|| (yAxisInverteds != null && yAxisInverteds.length > i && yAxisInverteds[i]);
				yAxis.setInverted(inverted);

				/*
				 * to set the range of the axis on the input from the user if the range combo
				 * box is selected
				 */
				if (yRanges != null && yRanges.size() > i && yRanges.get(i) != null) {
					Range yRange = yRanges.get(i);
					yAxis.setRange(yRange);
					if (yLog)
						Preconditions.checkState(yRange.getLowerBound() > 0 && Double.isFinite(yRange.getUpperBound()),
								"Y is log spacing, but y range contains 0 or is infinite: [%s %s]",
								yRange.getLowerBound(), yRange.getUpperBound());
					Preconditions.checkState(yRange.getUpperBound() > yRange.getLowerBound(),
							"Y range upper bound is not above lower bound: [%s %s]", yRange.getLowerBound(),
							yRange.getUpperBound());
					Preconditions.checkState(
							Double.isFinite(yRange.getUpperBound()) && Double.isFinite(yRange.getLowerBound()),
							"Y range not finite: [%s %s]", yRange.getLowerBound(), yRange.getUpperBound());
				}

			} catch (Exception e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(this, e.getMessage(), "Y-Plot Error", JOptionPane.OK_OPTION);
				graphOn = false;
				yLog = false;
				yAxis = prevYAxis;
				logErrorFlag = false;
				// TODO make sure button panel gets updated
			}

			subXAxis.add(xAxis);
			subYAxis.add(yAxis);

			if (i == 0) {
				this.xAxis = xAxis;
				this.yAxis = yAxis;
			}
		}

		plot = null;
		// XYPlot (and combined versions) use a really slow brute force dataset index
		// lookup
		// we're assigning dataset idexes manually below, cache them and return those
		// cached values
		HashMap<XYDataset, Integer> datasetIndexMap = new HashMap<>();
		// build the plot
		if (specs.size() == 1) {
			plot = new XYPlot(null, xAxis, yAxis, null) {

				@Override
				public int indexOf(XYDataset dataset) {
					Integer ret = datasetIndexMap.get(dataset);
					if (ret != null)
						return ret;
					return super.indexOf(dataset);
				}

			};
		} else if (combinedYAxis) {
			plot = new CombinedRangeXYPlot(yAxis) {

				@Override
				public int indexOf(XYDataset dataset) {
					Integer ret = datasetIndexMap.get(dataset);
					if (ret != null)
						return ret;
					return super.indexOf(dataset);
				}

			};
			((CombinedRangeXYPlot) plot).setGap(30);
		} else {
			plot = new CombinedDomainXYPlot(xAxis) {

				@Override
				public int indexOf(XYDataset dataset) {
					Integer ret = datasetIndexMap.get(dataset);
					if (ret != null)
						return ret;
					return super.indexOf(dataset);
				}

			};
			((CombinedDomainXYPlot) plot).setGap(30);
		}

		setupPlot(plot, tickFontSize);

		List<PlotCurveCharacterstics> plottedChars = Lists.newArrayList();

		// legend items. will be set to non null if at least one sub plot has a legend
		LegendItemCollection legendItems = null;
		// location set by FIRST spec with a legend
		RectangleEdge legendLocation = null;

		// legend items. will be set to non null if at least one sub plot has a legend
		List<PaintScaleLegend> cptSubtitles = null;
		RectangleEdge cptLocation = null;

		for (int p = 0; p < specs.size(); p++) {
			PlotSpec plotSpec = specs.get(p);

			// index of dataset from total prob functionlist (list containing each curve as
			// individual discretized function).
			int datasetIndex = 0;

			ValueAxis myXAxis, myYAxis;
			XYPlot subPlot;
			if (specs.size() > 1) {
				// this is a subPlot
				if (combinedYAxis) {
					// need a new X axis
					myYAxis = yAxis;
					myXAxis = subXAxis.get(p);
				} else {
					// need a new Y axis
					myXAxis = xAxis;
					myYAxis = subYAxis.get(p);
				}

				subPlot = new XYPlot(null, myXAxis, myYAxis, null);
				setupPlot(subPlot, tickFontSize);
			} else {
				subPlot = plot;
				myXAxis = xAxis;
				myYAxis = yAxis;
			}

			myXAxis.setInverted(plotSpec.isXAxisInverted());
			myYAxis.setInverted(plotSpec.isYAxisInverted());

			boolean xLog = xLogs.size() > 1 ? xLogs.get(p) : xLogs.get(0);
			boolean yLog = yLogs.size() > 1 ? yLogs.get(p) : yLogs.get(0);

			if (plotSpec instanceof XYZPlotSpec) {
				XYZPlotSpec xyzSpec = (XYZPlotSpec) plotSpec;
				XYZDatasetWrapper dataset = new XYZDatasetWrapper(xyzSpec, xLog, yLog);
				XYIntervalBlockRenderer renderer = new XYIntervalBlockRenderer();
				PaintScaleWrapper scale = new PaintScaleWrapper(xyzSpec.getCPT());
				renderer.setPaintScale(scale);
				subPlot.setRenderer(datasetIndex, renderer);
				subPlot.setDataset(datasetIndex, dataset);
				datasetIndexMap.put(dataset, datasetIndex);
				datasetIndex++;

				if (xyzSpec.isCPTVisible()) {
					if (cptSubtitles == null) {
						cptSubtitles = new ArrayList<>();
						cptLocation = xyzSpec.getCPTPosition();
					}
					cptSubtitles.add(getLegendForCPT(scale, xyzSpec.getZAxisLabel(), axisFontSize, tickFontSize,
							xyzSpec.getCPTTickUnit(), xyzSpec.getCPTPosition()));
				}
			}

			List<? extends PlotElement> elems = plotSpec.getPlotElems();
			plottedElems.addAll(elems);
			List<PlotCurveCharacterstics> plotChars = plotSpec.getChars();
			// wrap in new list in case we modify it, which can be the case for weighted
			// function lists
			if (plotChars == null) {
				plotChars = new ArrayList<>();
				plotSpec.setChars(plotChars);
				;
			}

			int charIndex = 0;
			int defaultColorIndex = 0;

			PlotCurveCharacterstics prevChars = null;
			DiscretizedFunctionXYDataSet prevDataset = null;
			XYItemRenderer prevRenderer = null;
			boolean reusable = false;
			int numDatasetsReused = 0;

			for (PlotElement elem : elems) {

				XY_DataSetList elemDatasets = elem.getDatasetsToPlot();
				List<Integer> elemPlotNumbs = elem.getPlotNumColorList();

				int totNumDatasets = elemDatasets.size();

				int datasetInElemIndex = 0;
				for (int numShared : elemPlotNumbs) {
					// this is one or more curves with the same plot curve characteristics

					PlotCurveCharacterstics chars;
					if (charIndex < plotChars.size()) {
						// use the passed in plot curve characteristic
						chars = plotChars.get(charIndex);
					} else {
						// we've gone beyond that passed in, use a default color
						XY_DataSet func = elemDatasets.get(datasetInElemIndex);
						PlotLineType lineType;
						PlotSymbol symbol;
						if (func instanceof DiscretizedFunc) {
							lineType = PlotLineType.SOLID;
							symbol = null;
						} else {
							lineType = null;
							symbol = PlotSymbol.DIAMOND;
						}
						chars = new PlotCurveCharacterstics(lineType, 1f, symbol, 4f,
								defaultColor[defaultColorIndex % defaultColor.length]);
						plotChars.add(chars);
					}
					defaultColorIndex++;
					Preconditions.checkState(datasetInElemIndex + numShared <= totNumDatasets,
							"elem.getDatasetsToPlot() and elem.getPlotNumColorList() are inconsistent");

					XY_DataSetList dataFunctions = new XY_DataSetList();
//					System.out.println("Checking for resuse with "+numShared+" shared");
					boolean reuseCandidate = prevDataset != null && chars == prevChars;
					for (int i = 0; i < numShared; i++) {
						// now add a copy of our curve characteristic for every line that shares it
						XY_DataSet func = elemDatasets.get(datasetInElemIndex++);
						dataFunctions.add(func);
//						System.out.println("\tFunc "+i+"of type "+ClassUtils.getClassNameWithoutPackage(func.getClass())
//							+", name="+func.getName()+", char="+chars);
						plottedFuncs.add(func);
						plottedChars.add(chars);
						reuseCandidate = reuseCandidate && (!plotSpec.isLegendVisible() || func.getName() == null);
					}
//					System.out.println("\treuseCandidate="+reuseCandidate);
					charIndex++;

					// now actually plot it
					if (isBlankCurve(chars))
						continue;
					
					if (reuseCandidate) {
						int prevNum = prevDataset.getFunctions().size();
						if (prevNum == 0)
							reuseCandidate = false;
						else if (plotSpec.isLegendVisible() && prevDataset.getFunctions().get(prevNum-1).getName() != null
								&& chars.getLineType() != null)
							// if the previous had a name and the legend is visible, stitching onto it could cause
							// a double long line, keep them separate
							reuseCandidate = false;
					}

					if (reuseCandidate) {
						// we can reuse the previous one
						// this will speed up plotting
						int prevNum = prevDataset.getFunctions().size();
						if (prevNum == 0)
							continue;
						if (plotSpec.isLegendVisible() && prevDataset.getFunctions().get(prevNum-1).getName() != null
								&& chars.getLineType() != null)
							// if the previous had a name and the legend is visible, stitching onto it could cause
							// a double long line, keep them separate
							continue;
						prevDataset.getFunctions().addAll(dataFunctions);
						int newNum = prevDataset.getFunctions().size();
						copyRendererSeriesValues(prevRenderer, 0, prevNum, newNum - 1);
						numDatasetsReused++;
						continue;
					}

					// new dataset
					Color color = chars.getColor();
					float lineWidth = chars.getLineWidth();
					PlotLineType lineType = chars.getLineType();
					float symbolWidth = chars.getSymbolWidth();
					PlotSymbol symbol = chars.getSymbol();

					// creating dataset for each curve and its consecutive curves which have same
					// plotting
					// characterstics. Eg: can be weighted functions in weighted functionlist have
					// same
					// plotting characterstics, also fractiles in weighted function list share same
					// plotting characterstics. So creating dataset for each list of curves with
					// same plotting characterstics.
					DiscretizedFunctionXYDataSet dataset = new DiscretizedFunctionXYDataSet();
//					System.out.println("Plotting "+dataFunctions.size()+" funcs bundled, color="+color);
					dataset.setXLog(xLog);
					dataset.setYLog(yLog);
					// converting the zero in Y-axis to some minimum value.
					dataset.setConvertZeroToMin(true, LOG_Y_MIN_VAL);
					dataset.setFunctions(dataFunctions);

					// adding the dataset to the plot
					subPlot.setDataset(datasetIndex, dataset);
					datasetIndexMap.put(dataset, datasetIndex);

					// based on plotting characteristics for each curve sending configuring plot
					// object
					// to be send to JFreechart for plotting.
//					drawCurvesUsingPlottingFeatures(subPlot, lineType, lineWidth, symbol, symbolWidth, color, dataIndex);

					XYItemRenderer renderer = PlotLineType.buildRenderer(lineType, symbol, lineWidth, symbolWidth);

					subPlot.setRenderer(datasetIndex, renderer);
//					xyItemRenderer.setPaint(color);
					renderer.setSeriesPaint(0, color);
					copyRendererSeriesValues(renderer, 0, 1, numShared - 1);
//					xyItemRenderer.setDefaultPaint(color);
					datasetIndex++;

					prevDataset = dataset;
					prevChars = chars;
					prevRenderer = renderer;
					// reusable if simple line/shape and not part of the legend
					reusable = renderer instanceof XYLineAndShapeRenderer && !plotSpec.isLegendVisible();
					for (int i = 0; reusable && i < dataFunctions.size(); i++)
						// only reusable if not in a legend
						reusable = dataFunctions.getName() == null;
				}
				Preconditions.checkState(datasetInElemIndex == totNumDatasets,
						"elem.getDatasetsToPlot() and elem.getPlotNumColorList() are inconsistent");
			}

//			if (numDatasetsReused > 0)
//				System.out.println("Reused "+numDatasetsReused+" datasets (datasetIndex="+datasetIndex+")");

			// now add any annotations
			if (plotSpec.getPlotAnnotations() != null)
				for (XYAnnotation a : plotSpec.getPlotAnnotations())
					subPlot.addAnnotation(a);

			// add any legend
			if (plotSpec.isLegendVisible()) {
				LegendItemCollection subLegend = plotSpec.getLegendItems(subPlot);

				if (plotSpec.isLegendInset()) {
					// just for this plot, inset
					subPlot.addAnnotation(plotSpec.buildInsetLegend(subLegend, plotPrefs, xLog, yLog,
							myXAxis.getRange(), myYAxis.getRange()));
				} else {
					if (legendItems == null) {
						legendItems = new LegendItemCollection();
						legendLocation = plotSpec.getLegendLocation();
					}
					legendItems.addAll(subLegend);
				}
			}

			// multiple plots
			if (plot instanceof CombinedRangeXYPlot)
				((CombinedRangeXYPlot) plot).add(subPlot);
			else if (plot instanceof CombinedDomainXYPlot)
				((CombinedDomainXYPlot) plot).add(subPlot);

			subPlot.setDatasetRenderingOrder(renderingOrder);

			subPlot.setBackgroundAlpha(.8f);
		}

		// getting the tick label font size
		int plotLabelFontSize = plotPrefs.getPlotLabelFontSize();

		Font defaultPlotLabelFont = JFreeChart.DEFAULT_TITLE_FONT;
		Font newPlotLabelFont = new Font(defaultPlotLabelFont.getFontName(), defaultPlotLabelFont.getStyle(),
				plotLabelFontSize);

		// giving off all the data that needs to be plotted to JFreechart, which return
		// backs
		// a panel fo curves,
		JFreeChart chart = new JFreeChart(specs.get(0).getTitle(), newPlotLabelFont, plot, false);

		chart.setBackgroundPaint(backgroundColor);

		if (legendItems != null) {
			final LegendItemCollection legendItemsFin = legendItems;
			LegendTitle chartLegend = new LegendTitle(new LegendItemSource() {

				@Override
				public LegendItemCollection getLegendItems() {
					return legendItemsFin;
				}
			});
			chartLegend.setPosition(legendLocation);
			Font legendFont = chartLegend.getItemFont();
			chartLegend.setItemFont(new Font(legendFont.getName(), legendFont.getStyle(), legendFontSize));
			chart.addLegend(chartLegend);
		}

		for (PlotSpec spec : specs)
			if (spec.getSubtitles() != null)
				for (Title subtitle : spec.getSubtitles())
					chart.addSubtitle(subtitle);

		if (cptSubtitles != null) {
			// add CPT subtitles
			if (cptSubtitles.size() > 1) {
				CPT cpt = null;
				boolean allSame = true;
				for (PaintScaleLegend legend : cptSubtitles) {
					CPT myCPT = ((PaintScaleWrapper) legend.getScale()).getCPT();
					if (cpt == null) {
						myCPT = cpt;
					} else if (cpt != myCPT) {
						allSame = false;
						break;
					}
				}
				if (allSame)
					cptSubtitles = cptSubtitles.subList(0, 1);
			}
			for (PaintScaleLegend cptSubtitle : cptSubtitles)
				if (cptSubtitle != null)
					chart.addSubtitle(cptSubtitle);
		}

		// Put into a panel
		chartPanel = new ChartPanel(chart, true, true, true, true, false);

		// chartPanel.setBorder( BorderFactory.createEtchedBorder( EtchedBorder.LOWERED
		// ) ); TODO clean
		chartPanel.setBorder(BorderFactory.createLineBorder(Color.gray, 1));
		chartPanel.setMouseZoomable(true);
		chartPanel.setDisplayToolTips(true);
		chartPanel.setHorizontalAxisTrace(false);
		chartPanel.setVerticalAxisTrace(false);

		// set the font of legend
		int numOfColors = plot.getSeriesCount();

		/**
		 * Adding the metadata text to the Window below the Chart
		 */
		metadataText.removeAll();
		metadataText.setEditable(false);
		setLegend = new SimpleAttributeSet();
		setLegend.addAttribute(StyleConstants.CharacterConstants.Bold, Boolean.TRUE);
		javax.swing.text.Document doc = metadataText.getStyledDocument();

		weightedfuncListIndexes = new HashSet<Integer>();
		try {

			/**
			 * formatting the metadata to be added , according to the colors of the Curves.
			 * So now curves and metadata will be displayed in the same color.
			 */
			doc.remove(0, doc.getLength());
			// total number of elements in the list containing individual functions and
			// weighted function list.
			int totalNumofFunctions = plottedElems.size();
			legendString = new ArrayList<>();
			// getting the metadata associated with each function in the list
			for (int i = 0, plotPrefIndex = 0; i < totalNumofFunctions; ++i) {
				String legend = null;
				// setting the font style for the legend
				setLegend = new SimpleAttributeSet();
				StyleConstants.setFontSize(setLegend, 12);
				// checking if element in the list is weighted function list object
				PlotElement elem = plottedElems.get(i);
				PlotCurveCharacterstics chars = plottedChars.get(plotPrefIndex);
				String datasetName = "DATASET #" + (i + 1);
				if (elem instanceof WeightedFuncListforPlotting) {
					// getting the metadata for weighted functionlist
					WeightedFuncListforPlotting weightedList = (WeightedFuncListforPlotting) elem;

					String listInfo = weightedList.getInfo();

					legend = new String(datasetName + " (" + chars + ")" + "\n" + listInfo + System.lineSeparator());
					legendString.add(legend);
					StyleConstants.setForeground(setLegend, Color.black);
					doc.insertString(doc.getLength(), legend, setLegend);
					// index where the weighted function list exits if it does in the list of
					// functions.
					weightedfuncListIndexes.add(legendString.size() - 1);
					// checking if individual curves need to be plotted
					if (weightedList.areIndividualCurvesToPlot()) {
						plottedChars.get(plotPrefIndex).setName(datasetName + " Curves");

						// getting the metadata for each individual curves and creating the legend
						// string
						String listFunctionsInfo = weightedList.getFunctionTraceInfo();

						legend = new String(listFunctionsInfo + System.lineSeparator());
						legendString.add(legend);
						Color color = (plottedChars.get(plotPrefIndex)).getColor();
						StyleConstants.setForeground(setLegend, color);
						doc.insertString(doc.getLength(), legend, setLegend);
						++plotPrefIndex;
					}
					// checking if fractiles need to be plotted
					if (weightedList.areFractilesToPlot()) {
						plottedChars.get(plotPrefIndex).setName(datasetName + " Fractiles");

						// getting the fractile info for the weighted function list and adding that to
						// the legend
						String fractileListInfo = weightedList.getFractileInfo();

						legend = new String(fractileListInfo + System.lineSeparator());
						legendString.add(legend);
						Color color = (plottedChars.get(plotPrefIndex)).getColor();
						StyleConstants.setForeground(setLegend, color);
						doc.insertString(doc.getLength(), legend, setLegend);
						++plotPrefIndex;
					}
					// checking if mean fractile need to be plotted
					if (weightedList.isMeanToPlot()) {
						plottedChars.get(plotPrefIndex).setName(datasetName + " Mean");
						// getting the fractileinfo and showing it as legend
						String meanInfo = weightedList.getMeanFunctionInfo();

						legend = new String(meanInfo + System.lineSeparator());
						legendString.add(legend);
						Color color = plottedChars.get(plotPrefIndex).getColor();
						StyleConstants.setForeground(setLegend, color);
						doc.insertString(doc.getLength(), legend, setLegend);
						++plotPrefIndex;
					}
				} else if (elem instanceof XY_DataSet) { // if element in the list are individual function then get
															// their info and show as legend
					plottedChars.get(plotPrefIndex).setName(datasetName);
					XY_DataSet func = (XY_DataSet) plottedElems.get(i);
					String functionInfo = func.getInfo();
					String name = func.getName();
					legend = new String(datasetName + " (" + chars + ")" + "\n" + name + "  "
							+ SystemUtils.LINE_SEPARATOR + functionInfo + SystemUtils.LINE_SEPARATOR);
					legendString.add(legend);
					Color color = plottedChars.get(plotPrefIndex).getColor();
					StyleConstants.setForeground(setLegend, color);
					doc.insertString(doc.getLength(), legend, setLegend);
					++plotPrefIndex;
				} else {
					// other PlotElement
					plottedChars.get(plotPrefIndex).setName(datasetName);
					String functionInfo = elem.getInfo();
					legend = new String(
							datasetName + " (" + chars + ")" + "\n" + functionInfo + SystemUtils.LINE_SEPARATOR);
					legendString.add(legend);
					Color color = plottedChars.get(plotPrefIndex).getColor();
					StyleConstants.setForeground(setLegend, color);
					doc.insertString(doc.getLength(), legend, setLegend);
					++plotPrefIndex;
				}
			}
		} catch (BadLocationException e) {
			return;
		}
		graphOn = false;

		// Check to see if there is no log Error and only xLog or yLog are selected
		if (!logErrorFlag && !xLogs.get(0))
			prevXAxis = xAxis;
		if (!logErrorFlag && !yLogs.get(0))
			prevYAxis = yAxis;

		// setting the info in the
		dataTextArea.setText(
				this.showDataInWindow(plottedElems, specs.get(0).getXAxisLabel(), specs.get(0).getYAxisLabel()));

		this.plottedChars = plottedChars;

		return;
	}

	private static void copyRendererSeriesValues(XYItemRenderer renderer, int refIndex, int startIndex, int endIndex) {
		Paint paint = renderer.getSeriesPaint(refIndex);
		Stroke stroke = renderer.getSeriesStroke(refIndex);
		Shape shape = renderer.getSeriesShape(refIndex);

		Paint outlinePaint = renderer.getSeriesOutlinePaint(refIndex);
		Stroke outlineStroke = renderer.getSeriesOutlineStroke(refIndex);

		Paint fillPaint = renderer.getSeriesFillPaint(refIndex);

		for (int i = startIndex; i <= endIndex; i++) {
			renderer.setSeriesPaint(i, paint);
			renderer.setSeriesStroke(i, stroke);
			renderer.setSeriesShape(i, shape);

			renderer.setSeriesOutlinePaint(i, outlinePaint);
			renderer.setSeriesOutlineStroke(i, outlineStroke);

			renderer.setSeriesFillPaint(i, fillPaint);
		}
	}

	public static void setupPlot(XYPlot plot, int tickFontSize) {
		// setting the plot properties
		plot.setDomainCrosshairLockedOnData(false);
		plot.setDomainCrosshairVisible(false);
		plot.setRangeCrosshairLockedOnData(false);
		plot.setRangeCrosshairVisible(false);
		plot.setInsets(new RectangleInsets(10, 0, 0, tickFontSize + 15));

		// TODO make this selectable?
		plot.setDomainGridlineStroke(new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0.0f));
		plot.setDomainGridlinePaint(new Color(225, 225, 225));
		plot.setRangeGridlineStroke(new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0.0f));
		plot.setRangeGridlinePaint(new Color(225, 225, 225));

	}

	/**
	 *
	 * @param elemList
	 * @param xAxisName
	 * @param yAxisName
	 * @return data to be shown in the data window
	 */
	private String showDataInWindow(List<? extends PlotElement> elemList, String xAxisName, String yAxisName) {

		int size = elemList.size();

		StringBuffer b = new StringBuffer();
		b.append("\n");
		b.append("X-Axis: " + xAxisName + '\n');
		b.append("Y-Axis: " + yAxisName + '\n');
		b.append("Number of Data Sets: " + size + "\n\n");

		for (int i = 0; i < size; ++i) {
			PlotElement elem = elemList.get(i);

			if (elem instanceof XY_DataSet) { // showing data for the individual function
				XY_DataSet function = (XY_DataSet) elem;
				b.append("\nDATASET #" + (i + 1) + "\n\n");
				b.append(function.toString() + '\n');
			} else if (elem instanceof WeightedFuncListforPlotting) { // showing data for weighted function list
				WeightedFuncListforPlotting weightedList = (WeightedFuncListforPlotting) elem;
				b.append("\nDATASET #" + (i + 1) + "   Weighted Function List" + '\n');
				b.append(weightedList.getInfo() + "\n\n");
				// checking if individual curves need to be plotted
				if (weightedList.areIndividualCurvesToPlot()) {
					// getting the metadata for each individual curves and creating the legend
					// string
					XY_DataSetList list = weightedList.getWeightedFunctionList();
					List<Double> wtList = weightedList.getRelativeWtList();
					int listSize = list.size();
					for (int j = 0; j < listSize; ++j) {
						b.append("\nFunction #" + (j + 1) + " of " + listSize + ", from Dataset #" + (i + 1)
								+ ", with relative wt = " + (Double) wtList.get(j) + "\n");
						DiscretizedFunc function = (DiscretizedFunc) list.get(j);
						b.append(function.getMetadataString() + '\n');
					}
				}
				// checking if fractiles need to be plotted
				if (weightedList.areFractilesToPlot()) {

					// getting the fractile info for the weighted function list and adding that to
					// the legend
					XY_DataSetList list = weightedList.getFractileList();
					List<Double> fractileValueList = weightedList.getFractileValuesList();
					int listSize = list.size();
					for (int j = 0; j < listSize; ++j) {
						b.append("\n" + (Double) fractileValueList.get(j) + " Fractile for Dataset #" + (i + 1) + "\n");
						DiscretizedFunc function = (DiscretizedFunc) list.get(j);
						b.append(function.getMetadataString() + '\n');
					}
				}

				// checking if mean fractile need to be plotted
				if (weightedList.isMeanToPlot()) {
					// getting the fractileinfo and showing it as legend
					b.append("\nMean for Dataset #" + (i + 1) + "\n");
					b.append(weightedList.getMean().getMetadataString() + "\n");
				}
			} else {
				b.append("\nDATASET #" + (i + 1) + "\n\n");
				b.append(elem.getInfo() + '\n');
			}
		}

		return b.toString();
	}

	/**
	 * Sets the metadata in the Data window
	 * 
	 * @param metadata
	 */
	public void setMetadata(String metadata) {
		dataTextArea.setText(metadata);
	}

	public void setBackgroundColor(Color background) {
		this.backgroundColor = background;
	}

	/**
	 * Sets preference for combined subplots. If true, the Y axis will be shared,
	 * else the X axis will be shared among subplots. This will be overridden if
	 * multiple ranges are set in the drawGraphPanel(...) call for a single axis.
	 * 
	 * @param combinedYAxis
	 */
	public void setCombinedOnYAxis(boolean combinedYAxis) {
		this.combinedYAxis = combinedYAxis;
	}

	/**
	 * Clears the plot and the Metadata Window
	 */
	public void removeChartAndMetadata() {
		chartPane.removeAll();
		chartPane.add(emptyPlotPanel, BorderLayout.CENTER);
		chartPanel = null;
		metadataText.setText("");
		dataTextArea.setText(NO_PLOT_MSG);
	}

	/**
	 * Deprecated - use PlotSpec.addSubtitle
	 * 
	 * @param subtitle
	 */
	@Deprecated
	public void addSubtitle(Title subtitle) {
		JFreeChart chart = chartPanel.getChart();
		chart.addSubtitle(subtitle);
	}

	/**
	 * Toggle between showing the graph and showing the actual data
	 */
	public void togglePlot() {

		chartPane.removeAll();
		// showing the data window
		if (graphOn) {
			// TODO
//			if (buttonControlPanel != null)
//				buttonControlPanel.setToggleButtonText( "Show Plot" );
			graphOn = false;

			chartPane.add(dataScrollPane, BorderLayout.CENTER);
			// chartPane.add(dataScrollPane,new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
			// , GridBagConstraints.CENTER, GridBagConstraints.BOTH, plotInsets, 0, 0 ) );
		} else {
			// showing the Plot window, if not null
			graphOn = true;
			// TODO
//			if (buttonControlPanel != null)
//				buttonControlPanel.setToggleButtonText("Show Data");
			// panel added here
			if (chartPanel != null) {
				chartPane.add(chartPanel, BorderLayout.CENTER);
				// chartPane.add(chartPanel,new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0 TODO
				// clean
				// , GridBagConstraints.CENTER, GridBagConstraints.BOTH, plotInsets, 0, 0 ) );

			} else {
				chartPane.add(dataScrollPane, BorderLayout.CENTER);
				// chartPane.add(dataScrollPane, new GridBagConstraints( 0, 0, 1, 1, 1.0, 1.0
				// , GridBagConstraints.CENTER, GridBagConstraints.BOTH, plotInsets, 0, 0 ) );
			}

		}
		return;
	}

	/**
	 * 
	 * @return true if graph is visible, false if data is visible
	 */
	public boolean isGraphOn() {
		return graphOn;
	}

	/**
	 * sets the backgound for the plot
	 * 
	 * @param color
	 */
	public void setPlotBackgroundColor(Color color) {
		if (plot != null)
			plot.setBackgroundPaint(color);
	}

	public XYPlot getPlot() {
		return plot;
	}

	/**
	 *
	 * @return the Range for the X-Axis. Note that this will return the first X-Axis
	 *         in the case of multiple subplots
	 */
	public Range getX_AxisRange() {
		return xAxis.getRange();
	}

	/**
	 *
	 * @return the Range for the Y-Axis. Note that this will return the first Y-Axis
	 *         in the case of multiple subplots
	 */
	public Range getY_AxisRange() {
		return yAxis.getRange();
	}

	/**
	 * 
	 * @return the X-Axis. Note that this will return the first X-Axis in the case
	 *         of multiple subplots
	 */
	public ValueAxis getXAxis() {
		return xAxis;
	}

	/**
	 * 
	 * @return the Y-Axis. Note that this will return the first Y-Axis in the case
	 *         of multiple subplots
	 */
	public ValueAxis getYAxis() {
		return yAxis;
	}

	/**
	 * Opens a file chooser and gives the user an opportunity to save the chart in
	 * PDF/PNG/TXT format.
	 *
	 * @throws IOException if there is an I/O error.
	 */
	public void save() throws IOException {
		if (chooser == null) {
			chooser = new JFileChooser();
			CustomFileFilter pdfFF = new CustomFileFilter("pdf", "PDF File");
			CustomFileFilter pngFF = new CustomFileFilter("png", "PNG File");
			CustomFileFilter txtFF = new CustomFileFilter("txt", "TXT File");

			chooser.addChoosableFileFilter(pdfFF);
			chooser.addChoosableFileFilter(pngFF);
			chooser.addChoosableFileFilter(txtFF);
			chooser.setAcceptAllFileFilterUsed(false);
			chooser.setFileFilter(pdfFF);
		}
		int option = chooser.showSaveDialog(this);
		String fileName = null;
		if (option == JFileChooser.APPROVE_OPTION) {
			fileName = chooser.getSelectedFile().getAbsolutePath();
			CustomFileFilter filter = (CustomFileFilter) chooser.getFileFilter();
			String ext = filter.getExtension();
			System.out.println(ext);
			if (!fileName.toLowerCase().endsWith(ext)) {
				fileName = fileName + ext;
			}
			if (ext.equals(".pdf")) {
				saveAsPDF(fileName);
			} else if (ext.equals(".png")) {
				saveAsPNG(fileName);
			} else if (ext.equals(".txt")) {
				saveAsTXT(fileName);
			} else {
				throw new RuntimeException("Unknown extension selected: " + ext);
			}
		}
	}

	/**
	 * Allows the user to save the chart as PNG.
	 * 
	 * @param fileName
	 * @throws IOException
	 */
	public void saveAsPNG(String fileName) throws IOException {
		saveAsPNG(fileName, chartPanel.getWidth(), chartPanel.getHeight());
	}

	/**
	 * Allows the user to save the chart as TXT
	 * 
	 * @param fileName
	 * @throws IOException
	 */
	public void saveAsTXT(String fileName) throws IOException {
		FileUtils.save(fileName, dataTextArea.getText());
	}

	/**
	 * Allows the user to save the chart as PNG.
	 * 
	 * @param fileName
	 * @throws IOException
	 */
	public void saveAsPNG(String fileName, int width, int height) throws IOException {
		ChartUtils.saveChartAsPNG(new File(fileName), chartPanel.getChart(), width, height);
	}

	/**
	 * Allows the user to save the chart contents and metadata as PDF. This allows
	 * to preserve the color coding of the metadata.
	 * 
	 * @throws IOException
	 */
	public void saveAsPDF(String fileName) throws IOException {
		int width = chartPanel.getWidth();
		int height = chartPanel.getHeight();
		this.saveAsPDF(fileName, width, height);
	}

	public BufferedImage getBufferedImage(int width, int height) {
		return chartPanel.getChart().createBufferedImage(width, height, null);
	}

//	private static class UTF8_FontMapper implements FontMapper {
//
//		/** Maps aliases to names.
//	     */    
//	    private HashMap aliases = new HashMap();
//	    /** Maps names to BaseFont parameters.
//	     */    
//	    private HashMap mapper = new HashMap();
//	    
//	    private final String encoding = BaseFont.IDENTITY_H;
//	    /**
//	     * Returns a BaseFont which can be used to represent the given AWT Font
//	     *
//	     * @param	font		the font to be converted
//	     * @return	a BaseFont which has similar properties to the provided Font
//	     */
//	    
//	    public BaseFont awtToPdf(Font font) {
//	        try {
//	            BaseFontParameters p = getBaseFontParameters(font.getFontName());
//	            if (p != null)
//	                return BaseFont.createFont(p.fontName, p.encoding, p.embedded, p.cached, p.ttfAfm, p.pfb);
//	            String fontKey = null;
//	            String logicalName = font.getName();
//
//	            if (logicalName.equalsIgnoreCase("DialogInput") || logicalName.equalsIgnoreCase("Monospaced") || logicalName.equalsIgnoreCase("Courier")) {
//
//	                if (font.isItalic()) {
//	                    if (font.isBold()) {
//	                        fontKey = BaseFont.COURIER_BOLDOBLIQUE;
//
//	                    } else {
//	                        fontKey = BaseFont.COURIER_OBLIQUE;
//	                    }
//
//	                } else {
//	                    if (font.isBold()) {
//	                        fontKey = BaseFont.COURIER_BOLD;
//
//	                    } else {
//	                        fontKey = BaseFont.COURIER;
//	                    }
//	                }
//
//	            } else if (logicalName.equalsIgnoreCase("Serif") || logicalName.equalsIgnoreCase("TimesRoman")) {
//
//	                if (font.isItalic()) {
//	                    if (font.isBold()) {
//	                        fontKey = BaseFont.TIMES_BOLDITALIC;
//
//	                    } else {
//	                        fontKey = BaseFont.TIMES_ITALIC;
//	                    }
//
//	                } else {
//	                    if (font.isBold()) {
//	                        fontKey = BaseFont.TIMES_BOLD;
//
//	                    } else {
//	                        fontKey = BaseFont.TIMES_ROMAN;
//	                    }
//	                }
//
//	            } else {  // default, this catches Dialog and SansSerif
//
//	                if (font.isItalic()) {
//	                    if (font.isBold()) {
//	                        fontKey = BaseFont.HELVETICA_BOLDOBLIQUE;
//
//	                    } else {
//	                        fontKey = BaseFont.HELVETICA_OBLIQUE;
//	                    }
//
//	                } else {
//	                    if (font.isBold()) {
//	                        fontKey = BaseFont.HELVETICA_BOLD;
//	                    } else {
//	                        fontKey = BaseFont.HELVETICA;
//	                    }
//	                }
//	            }
//	            return BaseFont.createFont(fontKey, encoding, false);
//	        }
//	        catch (Exception e) {
//	            throw new ExceptionConverter(e);
//	        }
//	    }
//	    
//	    /**
//	     * Returns an AWT Font which can be used to represent the given BaseFont
//	     *
//	     * @param	font		the font to be converted
//	     * @param	size		the desired point size of the resulting font
//	     * @return	a Font which has similar properties to the provided BaseFont
//	     */
//	    
//	    public Font pdfToAwt(BaseFont font, int size) {
//	        String names[][] = font.getFullFontName();
//	        if (names.length == 1)
//	            return new Font(names[0][3], 0, size);
//	        String name10 = null;
//	        String name3x = null;
//	        for (int k = 0; k < names.length; ++k) {
//	            String name[] = names[k];
//	            if (name[0].equals("1") && name[1].equals("0"))
//	                name10 = name[3];
//	            else if (name[2].equals("1033")) {
//	                name3x = name[3];
//	                break;
//	            }
//	        }
//	        String finalName = name3x;
//	        if (finalName == null)
//	            finalName = name10;
//	        if (finalName == null)
//	            finalName = names[0][3];
//	        return new Font(finalName, 0, size);
//	    }
//	    
//	    /** Maps a name to a BaseFont parameter.
//	     * @param awtName the name
//	     * @param parameters the BaseFont parameter
//	     */    
//	    public void putName(String awtName, BaseFontParameters parameters) {
//	        mapper.put(awtName, parameters);
//	    }
//	    
//	    /** Maps an alias to a name.
//	     * @param alias the alias
//	     * @param awtName the name
//	     */    
//	    public void putAlias(String alias, String awtName) {
//	        aliases.put(alias, awtName);
//	    }
//	    
//	    /** Looks for a BaseFont parameter associated with a name.
//	     * @param name the name
//	     * @return the BaseFont parameter or <CODE>null</CODE> if not found.
//	     */    
//	    public BaseFontParameters getBaseFontParameters(String name) {
//	        String alias = (String)aliases.get(name);
//	        if (alias == null)
//	            return (BaseFontParameters)mapper.get(name);
//	        BaseFontParameters p = (BaseFontParameters)mapper.get(alias);
//	        if (p == null)
//	            return (BaseFontParameters)mapper.get(name);
//	        else
//	            return p;
//	    }
//	    
//	    /**
//	     * Inserts the names in this map.
//	     * @param allNames the returned value of calling {@link BaseFont#getAllFontNames(String, String, byte[])}
//	     * @param path the full path to the font
//	     */    
//	    public void insertNames(Object allNames[], String path) {
//	        String names[][] = (String[][])allNames[2];
//	        String main = null;
//	        for (int k = 0; k < names.length; ++k) {
//	            String name[] = names[k];
//	            if (name[2].equals("1033")) {
//	                main = name[3];
//	                break;
//	            }
//	        }
//	        if (main == null)
//	            main = names[0][3];
//	        BaseFontParameters p = new BaseFontParameters(path);
//	        mapper.put(main, p);
//	        for (int k = 0; k < names.length; ++k) {
//	            aliases.put(names[k][3], main);
//	        }
//	        aliases.put(allNames[0], main);
//	    }
//	    
//	    /** Inserts all the fonts recognized by iText in the
//	     * <CODE>directory</CODE> into the map. The encoding
//	     * will be <CODE>BaseFont.CP1252</CODE> but can be
//	     * changed later.
//	     * @param dir the directory to scan
//	     * @return the number of files processed
//	     */    
//	    public int insertDirectory(String dir) {
//	        File file = new File(dir);
//	        if (!file.exists() || !file.isDirectory())
//	            return 0;
//	        File files[] = file.listFiles();
//	        if (files == null)
//	        	return 0;
//	        int count = 0;
//	        for (int k = 0; k < files.length; ++k) {
//	            file = files[k];
//	            String name = file.getPath().toLowerCase();
//	            try {
//	                if (name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".afm")) {
//	                    Object allNames[] = BaseFont.getAllFontNames(file.getPath(), encoding, null);
//	                    insertNames(allNames, file.getPath());
//	                    ++count;
//	                }
//	                else if (name.endsWith(".ttc")) {
//	                    String ttcs[] = BaseFont.enumerateTTCNames(file.getPath());
//	                    for (int j = 0; j < ttcs.length; ++j) {
//	                        String nt = file.getPath() + "," + j;
//	                        Object allNames[] = BaseFont.getAllFontNames(nt, encoding, null);
//	                        insertNames(allNames, nt);
//	                    }
//	                    ++count;
//	                }
//	            }
//	            catch (Exception e) {
//	            }
//	        }
//	        return count;
//	    }
//	    
//	    public HashMap getMapper() {
//	        return mapper;
//	    }
//	    
//	    public HashMap getAliases() {
//	        return aliases;
//	    }
//		
//	}

	/**
	 * Allows the user to save the chart contents and metadata as PDF. This allows
	 * to preserve the color coding of the metadata.
	 * 
	 * @throws IOException
	 */
	public void saveAsPDF(String fileName, int width, int height) throws IOException {
		int textLength = metadataText.getStyledDocument().getLength();
		int totalLength = textLength + height;
		// step 1
		Document metadataDocument = new Document(new com.itextpdf.text.Rectangle(width, height));
		metadataDocument.addAuthor("OpenSHA");
		metadataDocument.addCreationDate();
//		HeaderFooter footer = new HeaderFooter(new Phrase("Powered by OpenSHA"), true);
//		metadataDocument.setFooter(footer);
		try {
			// step 2
			PdfWriter writer;

			writer = PdfWriter.getInstance(metadataDocument, new BufferedOutputStream(new FileOutputStream(fileName)));
			// step 3
			metadataDocument.open();
			// step 4
			PdfContentByte cb = writer.getDirectContent();
			PdfTemplate tp = cb.createTemplate(width, height);
//			tp.creategraphics
//			new 
//			FontMapper fontMapper = new DefaultFontMapper();
			FontMapper fontMapper = new PDF_UTF8_FontMapper();
			Graphics2D g2d = new PdfGraphics2D(tp, width, height, fontMapper);
//			Graphics2D g2d = tp.createGraphics(width, height,
//					new DefaultFontMapper());
			Rectangle2D r2d = new Rectangle2D.Double(0, 0, width, height);
			chartPanel.getChart().draw(g2d, r2d);
			g2d.dispose();
			cb.addTemplate(tp, 0, 0);
			// starts the metadata from the new page.
			metadataDocument.newPage();
			int size = legendString.size();
			for (int i = 0, legendColor = 0; i < size; ++i, ++legendColor) {
				com.itextpdf.text.Paragraph para = new com.itextpdf.text.Paragraph();
				// checks to see if the WeightFuncList exists in the list of functions
				// then plot it in black else plot in the same as the legend
				if (weightedfuncListIndexes != null && weightedfuncListIndexes.contains(i)) {
					para.add(new Phrase((String) legendString.get(i),
							FontFactory.getFont(PDF_UTF8_FontMapper.SANS, 10, Font.PLAIN, BaseColor.BLACK)));
					--legendColor;
				} else {
					Color c = this.plottedChars.get(legendColor).getColor();
					BaseColor bc = new BaseColor(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
					para.add(new Phrase((String) legendString.get(i),
							FontFactory.getFont(PDF_UTF8_FontMapper.SANS, 10, Font.PLAIN, bc)));
				}
				metadataDocument.add(para);
			}
		} catch (DocumentException de) {
			de.printStackTrace();
		}
		// step 5
		metadataDocument.close();
	}

	/**
	 * Creates a print job for the chart if plot is being shown, else print the
	 * chart data if data window is visible.
	 */
	public void print() {
		if (graphOn)
			chartPanel.createChartPrintJob();
		else {
			try {
				dataTextArea.print();
			} catch (PrinterException e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(this, "Printing failed:\n" + e.getMessage(), "Printing Failed!",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	/**
	 *
	 * @return the XAxis Label if not null else return null
	 */
	public String getXAxisLabel() {
		if (xAxis != null)
			return xAxis.getLabel();
		return null;
	}

	/**
	 *
	 * @return the YAxis Label if not null else return null
	 */
	public String getYAxisLabel() {
		if (yAxis != null)
			return yAxis.getLabel();
		return null;
	}

	/**
	 *
	 * @return the chart Title if not null else return null
	 */
	public String getPlotLabel() {
		if (chartPanel != null)
			return chartPanel.getChart().getTitle().getText();
		return null;
	}

	public ChartPanel getChartPanel() {
		return this.chartPanel;
	}

	public DatasetRenderingOrder getRenderingOrder() {
		return renderingOrder;
	}

	/**
	 * Set dataset rendering order (allows plotting first function on top or
	 * bottom).
	 * 
	 * @param renderingOrder
	 */
	public void setRenderingOrder(DatasetRenderingOrder renderingOrder) {
		this.renderingOrder = renderingOrder;
	}

	/**
	 * Set X axis inverted.
	 * 
	 * @param xAxisInverted
	 */
	public void setxAxisInverted(boolean xAxisInverted) {
		this.xAxisInverted = xAxisInverted;
		if (xAxis != null)
			xAxis.setInverted(xAxisInverted);
	}

	/**
	 * Set X axis of specific subplots inverted.
	 * 
	 * @param xAxisInverted
	 */
	public void setxAxisInverteds(boolean[] xAxisInverteds) {
		this.xAxisInverteds = xAxisInverteds;
	}

	/**
	 * Set Y axis inverted
	 * 
	 * @param yAxisInverted
	 */
	public void setyAxisInverted(boolean yAxisInverted) {
		this.yAxisInverted = yAxisInverted;
		if (yAxis != null)
			yAxis.setInverted(yAxisInverted);
	}

	/**
	 * Set X axis of specific subplots inverted.
	 * 
	 * @param xAxisInverted
	 */
	public void setyAxisInverteds(boolean[] yAxisInverteds) {
		this.yAxisInverteds = yAxisInverteds;
	}

	public void setAxisLabelFontSize(int axisLabelFontSize) {
		plotPrefs.setAxisLabelFontSize(axisLabelFontSize);
	}

	public void setTickLabelFontSize(int tickLabelFontSize) {
		plotPrefs.setTickLabelFontSize(tickLabelFontSize);
	}

	public void setPlotLabelFontSize(int plotLabelFontSize) {
		plotPrefs.setPlotLabelFontSize(plotLabelFontSize);
	}

	public void setLegendFontSize(int legendFontSize) {
		plotPrefs.setLegendFontSize(legendFontSize);
	}

	public PlotPreferences getPlotPrefs() {
		return plotPrefs;
	}

	public void setGriddedFuncAxesTicks(boolean histogramAxesTicks) {
		this.griddedFuncAxesTicks = histogramAxesTicks;
	}

	public static PaintScaleLegend getLegendForCPT(CPT cpt, String zAxisLabel, int axisFontSize, int tickFontSize,
			double tickUnit, RectangleEdge position) {
		return getLegendForCPT(new PaintScaleWrapper(cpt), zAxisLabel, axisFontSize, tickFontSize, tickUnit, position);
	}

	private static PaintScaleLegend getLegendForCPT(PaintScaleWrapper scale, String zAxisLabel, int axisFontSize,
			int tickFontSize, double tickUnit, RectangleEdge position) {
		CPT cpt = scale.getCPT();
		ValueAxis fakeZAxis;
		if (cpt.isLog10()) {
			JFreeLogarithmicAxis logAxis = new JFreeLogarithmicAxis(zAxisLabel);
			// this fixes the overlap issue with the bottom of the plot (not sure if needed
			// here, used for regular plots)
			logAxis.setVerticalAnchorShift(4);
			logAxis.setLowerBound(scale.getLowerBound());
			logAxis.setUpperBound(scale.getUpperBound());

			fakeZAxis = logAxis;
		} else {
			NumberAxis linearAxis = new NumberAxis();
			linearAxis.setLowerBound(scale.getLowerBound());
			linearAxis.setUpperBound(scale.getUpperBound());
			linearAxis.setLabel(zAxisLabel);
			if (tickUnit > 0)
				linearAxis.setTickUnit(new NumberTickUnit(tickUnit));

			fakeZAxis = linearAxis;
		}
		Font axisLabelFont = fakeZAxis.getLabelFont();
		fakeZAxis.setLabelFont(new Font(axisLabelFont.getFontName(), axisLabelFont.getStyle(), axisFontSize));
		Font axisTickFont = fakeZAxis.getTickLabelFont();
		fakeZAxis.setTickLabelFont(new Font(axisTickFont.getFontName(), axisTickFont.getStyle(), tickFontSize));
		PaintScaleLegend legend = new PixelSpacePaintScaleLegend(scale, fakeZAxis, 1); // number here is width in pixels
																						// for each span
		legend.setSubdivisionCount(500);
		if (position != null)
			legend.setPosition(position);
		if (legend.getPosition() == RectangleEdge.BOTTOM || legend.getPosition() == RectangleEdge.TOP)
			legend.setPadding(5d, 50d, 5d, 50d);
		else if (legend.getPosition() == RectangleEdge.LEFT)
			legend.setPadding(5d, 5d, 50d, 20d);
		else
			// right
			legend.setPadding(5d, 20d, 50d, 5d);
		return legend;
	}
}
