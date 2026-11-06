package org.opensha.sha.calc.disaggregation.chart3d;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;

import javax.swing.JFrame;

import org.jfree.chart3d.Chart3D;
import org.jfree.chart3d.Chart3DPanel;
import org.jfree.chart3d.axis.LabelOrientation;
import org.jfree.chart3d.axis.NumberAxis3D;
import org.jfree.chart3d.axis.StandardCategoryAxis3D;
import org.jfree.chart3d.data.Range;
import org.jfree.chart3d.data.category.CategoryDataset3D;
import org.jfree.chart3d.export.ExportUtils;
import org.jfree.chart3d.graphics2d.Anchor2D;
import org.jfree.chart3d.graphics3d.Offset2D;
import org.jfree.chart3d.graphics3d.ViewPoint3D;
import org.jfree.chart3d.label.CategoryLabelGenerator;
import org.jfree.chart3d.plot.CategoryPlot3D;
import org.jfree.chart3d.renderer.category.CategoryColorSource;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.Location;
import org.opensha.commons.gui.plot.pdf.PDF_UTF8_FontMapper;
import org.opensha.sha.calc.disaggregation.DisaggregationCalculator;
import org.opensha.sha.calc.disaggregation.DisaggregationCalculator.EpsilonCategories;
import org.opensha.sha.calc.sourceFilters.SourceFilterManager;
import org.opensha.sha.calc.sourceFilters.SourceFilters;
import org.opensha.sha.calc.disaggregation.DisaggregationPlotData;
import org.opensha.sha.earthquake.rupForecastImpl.Frankel96.Frankel96_AdjustableEqkRupForecast;
import org.opensha.sha.earthquake.rupForecastImpl.WGCEP_UCERF_2_Final.MeanUCERF2.MeanUCERF2;
import org.opensha.sha.imr.AttenRelRef;
import org.opensha.sha.imr.ScalarIMR;
import org.opensha.sha.imr.param.IntensityMeasureParams.SA_Param;

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
 * Pure-Java (client-side) renderer for disaggregation plots. Draws the 3-D bar chart of percent contribution by rupture
 * distance, magnitude and epsilon (stacked by {@link EpsilonCategories}) from a {@link DisaggregationPlotData} using
 * Orson Charts, and can write it as PNG or PDF. This is the default renderer, a client-side alternative to the optional
 * server-side GMT {@code DisaggregationPlotServlet} (slower, better quality for publications): no network access or GMT
 * installation is required, and it works headless.
 * <p>
 * Typical use: {@code PureJavaDisaggPlotter.buildChartPanel(calc.getDisaggPlotData())} for an on-screen panel, or
 * {@link #writeChartPlot(File, String, DisaggregationPlotData, int, int, boolean, boolean)} to write files.
 */
public class PureJavaDisaggPlotter {
	
	/**
	 * Builds an 800x800 chart panel for the given disaggregation data with the fixed (non-rotatable) view point.
	 * 
	 * @param data disaggregation results, e.g. from {@link DisaggregationCalculator#getDisaggPlotData()}
	 * @return panel that can be shown in a window or passed to the {@code writeChart*} methods
	 */
	public static Chart3DPanel buildChartPanel(DisaggregationPlotData data) {
		return buildChartPanel(data, true);
	}
	
	/**
	 * Builds an 800x800 chart panel for the given disaggregation data.
	 * 
	 * @param data disaggregation results, e.g. from {@link DisaggregationCalculator#getDisaggPlotData()}
	 * @param fixedPerspective if true, the user cannot rotate/zoom the 3-D view ({@link FixedPerspectiveChart3DPanel});
	 * if false, the standard interactive {@link Chart3DPanel} is used
	 * @return panel that can be shown in a window or passed to the {@code writeChart*} methods
	 */
	public static Chart3DPanel buildChartPanel(DisaggregationPlotData data, boolean fixedPerspective) {
		Chart3D chart = buildChart(data);
		Chart3DPanel chartPanel;
		if (fixedPerspective)
			chartPanel = new FixedPerspectiveChart3DPanel(chart);
		else
			chartPanel = new Chart3DPanel(chart);
        chartPanel.setMargin(0.2);
        chartPanel.setSize(800, 800);
        chartPanel.setPreferredSize(new Dimension(800, 800));
        chartPanel.zoomToFit();
        return chartPanel;
	}
	
	/**
	 * Builds the 3-D chart: distance (rows) and magnitude (columns) category axes, a percent-contribution value axis, bars
	 * stacked and colored by epsilon category, an epsilon legend and the standard view point.
	 * <p>
	 * The value (Z) axis starts at 0 and is automatically ranged to fit the tallest bin's total contribution. If
	 * {@link DisaggregationPlotData#getMaxContrEpsilonForGMT_Plot()} (set via
	 * {@link DisaggregationCalculator#setMaxZAxisForPlot(double)}) is larger than the tallest bin's total, the axis is fixed
	 * to 0 to that value so the user can force a common Z-axis maximum across plots. A requested maximum that does not
	 * exceed the data (including the calculator's own automatic value, which is the data maximum) is ignored rather than
	 * clipping bars.
	 * 
	 * @param data disaggregation results
	 * @return the configured chart
	 */
	public static Chart3D buildChart(DisaggregationPlotData data) {
		DisaggDataset3D dataset = new DisaggDataset3D(data);
		
		double maxZ = 0d;
		
		double[][][] pdf3D = data.getPdf3D();
		for (int i=0; i<pdf3D.length; i++) {
			for (int j=0; j<pdf3D[i].length; j++) {
				// skip if no contributions for any epsilon
				double sumZ = 0d;
				for (double contrib : pdf3D[i][j])
					sumZ += contrib;
				maxZ = Math.max(maxZ, sumZ);
			}
		}
		
		// the tallest bin's total, before rounding
		double dataMaxZ = maxZ;

		// round to nearest 10%
		maxZ = 10d*Math.ceil(maxZ/10d);

		DisaggCategoryLabelGenerator labelGen = new DisaggCategoryLabelGenerator();
		
		BasicStroke axisStroke = new BasicStroke(1f);
		double tickLen = 4;
		
//		StandardCategoryAxis3D rowAxis = new StandardCategoryAxis3D(rowAxisLabel);
		StandardCategoryAxis3D rowAxis = new StandardCategoryAxis3D("Rupture Distance (km)");
//				new Range(dist_binEdges[0], dist_binEdges[dist_binEdges.length-1]));
		rowAxis.setTickLabelOrientation(LabelOrientation.PARALLEL);
		rowAxis.setTickLabelGenerator(labelGen);
		rowAxis.setLineColor(Color.BLACK);
		rowAxis.setLineStroke(axisStroke);
		rowAxis.setFirstCategoryHalfWidth(true);
		rowAxis.setLastCategoryHalfWidth(true);
		rowAxis.setTickMarkStroke(axisStroke);
		rowAxis.setTickMarkLength(tickLen);
		
		StandardCategoryAxis3D columnAxis = new StandardCategoryAxis3D("Magnitude");
//				new Range(mag_binEdges[0], mag_binEdges[mag_binEdges.length-1]));
		columnAxis.setTickLabelOrientation(LabelOrientation.PARALLEL);
		columnAxis.setTickLabelGenerator(labelGen);
		columnAxis.setLineColor(Color.BLACK);
		columnAxis.setLineStroke(axisStroke);
		columnAxis.setFirstCategoryHalfWidth(true);
		columnAxis.setLastCategoryHalfWidth(true);
		columnAxis.setTickMarkStroke(axisStroke);
		columnAxis.setTickMarkLength(tickLen);
		
		NumberAxis3D valueAxis = new NumberAxis3D("% Contribution", 
				new Range(0.0, maxZ));
		valueAxis.setTickLabelOrientation(LabelOrientation.PARALLEL);
		valueAxis.setLineColor(Color.BLACK);
		valueAxis.setLineStroke(axisStroke);
		valueAxis.setTickMarkStroke(axisStroke);
		valueAxis.setTickMarkLength(tickLen);
		
		DisaggBarRenderer3D renderer = new DisaggBarRenderer3D();
		renderer.setBarXWidth(0.85);
		renderer.setBarZWidth(0.85);
		CategoryPlot3D plot = new CategoryPlot3D(dataset, renderer, rowAxis, 
				columnAxis, valueAxis);
		
		// the plot auto-ranges the value axis from the data when it is constructed (the range passed to the axis above is
		// overwritten), so a requested Z axis max, e.g. to keep several plots on the same scale, must be set afterwards.
		// Only a max larger than the data's is applied; the default (auto) range is otherwise left alone. The calculator's
		// own "automatic" value is the data max itself, hence the tolerance rather than a plain comparison.
		double requestedMaxZ = data.getMaxContrEpsilonForGMT_Plot();
		if (Double.isFinite(requestedMaxZ) && requestedMaxZ > dataMaxZ*(1d + 1e-9))
			valueAxis.setRange(0d, requestedMaxZ);
		
		Chart3D chart = new DisaggChart3D(null, null, plot, new DisaggChartStyle());
		
		plot.setLegendLabelGenerator(labelGen);
		renderer.setColorSource(new EpsilonColorSource());
		
		// getting better: 25, 55, 30, -77.5
		// first try
//		double theta = 35;
//		double phi = 55;
//		double rho = 30;
//		double orientation = -67.1;
		
		// getting better
//		double theta = 25;
//		double phi = 55;
//		double rho = 30;
//		double orientation = -77.5;
		
		// final?
		double theta = 31;
		double phi = 70;
		double rho = 30;
		double orientation = -76.1;
		
		chart.setViewPoint(new ViewPoint3D(
				// theta: the rotation of the viewing point from the x-axis around the z-axis (in radians)
				Math.toRadians(theta),
//				0d,
				// phi: the rotation of the viewing point up and down (from the XZ plane, in radians)
				Math.toRadians(phi),
//				0d,
				// rho: the distance of the viewing point from the origin.
				rho,
				// orientation: the angle of rotation.
				Math.toRadians(orientation))); // -67.1
//				0d));
//				Math.toRadians(45d)));
		
		chart.setTranslate2D(new Offset2D(20d, -80d));
		
		chart.setLegendAnchor(Anchor2D.BOTTOM_CENTER);
		chart.setLegendBuilder(new DisaggLegendBuilder());
//		
		return chart;
	}
	
	/**
	 * Builds a chart of the given size and writes it as PNG and/or PDF.
	 * 
	 * @param outputDir directory to write to
	 * @param prefix file name without extension ({@code .png}/{@code .pdf} is appended)
	 * @param data disaggregation results
	 * @param width image width in pixels (PDF page width in points)
	 * @param height image height in pixels (PDF page height in points)
	 * @param writePNG if true, write {@code prefix.png}
	 * @param writePDF if true, write {@code prefix.pdf}
	 * @throws IOException if a file cannot be written
	 */
	public static void writeChartPlot(File outputDir, String prefix, DisaggregationPlotData data,
			int width, int height, boolean writePNG, boolean writePDF) throws IOException {
		Chart3DPanel panel = buildChartPanel(data);
		panel.setSize(width, height);
		panel.setPreferredSize(new Dimension(width, height));
		panel.zoomToFit();
		writeChartPlot(outputDir, prefix, panel, writePNG, writePDF);
	}
	
	/**
	 * Writes an existing chart panel as PNG and/or PDF at the panel's current size. The PDF contains only the chart; use
	 * {@link #writeChartPDF(File, Chart3DPanel, String)} to add text.
	 * 
	 * @param outputDir directory to write to
	 * @param prefix file name without extension ({@code .png}/{@code .pdf} is appended)
	 * @param panel panel from {@link #buildChartPanel(DisaggregationPlotData)}
	 * @param writePNG if true, write {@code prefix.png}
	 * @param writePDF if true, write {@code prefix.pdf}
	 * @throws IOException if a file cannot be written
	 */
	public static void writeChartPlot(File outputDir, String prefix, Chart3DPanel panel,
			boolean writePNG, boolean writePDF) throws IOException {
		int width = panel.getWidth();
		int height = panel.getHeight();
		if (writePNG)
			ExportUtils.writeAsPNG(panel.getDrawable(), width, height,
					new File(outputDir, prefix+".png"));
		if (writePDF) {
			writeChartPDF(new File(outputDir, prefix+".pdf"), panel, null);
		}
	}
	
	/**
	 * Writes the chart as a vector PDF page sized to the panel, optionally followed by a second page of text.
	 * 
	 * @param outputFile PDF file to write
	 * @param panel panel whose chart is drawn
	 * @param metadata text (e.g. mean/mode, bin and source data) placed on a second page, or null/blank for a chart-only PDF
	 * @throws IOException if the file cannot be created
	 */
	public static void writeChartPDF(File outputFile, Chart3DPanel panel, String metadata) throws IOException {
		int width = panel.getWidth();
		int height = panel.getHeight();
		// step 1
		Document metadataDocument = new Document(new com.itextpdf.text.Rectangle(
				width, height));
		metadataDocument.addAuthor("OpenSHA");
		metadataDocument.addCreationDate();
//		HeaderFooter footer = new HeaderFooter(new Phrase("Powered by OpenSHA"), true);
//		metadataDocument.setFooter(footer);
		try {
			// step 2
			PdfWriter writer;

			writer = PdfWriter.getInstance(metadataDocument,
					new FileOutputStream(outputFile));
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
			panel.getChart().draw(g2d, r2d);
			g2d.dispose();
			cb.addTemplate(tp, 0, 0);
			//starts the metadata from the new page.
			if (metadata != null && !metadata.isBlank()) {
				metadataDocument.newPage();
				com.itextpdf.text.Paragraph para = new com.itextpdf.text.Paragraph();
				//checks to see if the WeightFuncList exists in the list of functions
				//then plot it in black else plot in the same as the legend
				Color c = Color.BLACK;
				BaseColor bc = new BaseColor(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
				para.add(new Phrase( (String) metadata,
						FontFactory.getFont(
								PDF_UTF8_FontMapper.SANS, 10, Font.PLAIN, bc)));
				metadataDocument.add(para);
			}
		}
		catch (DocumentException de) {
			de.printStackTrace();
		}
		// step 5
		metadataDocument.close();
	}

	private static DecimalFormat magDF = new DecimalFormat("0.0#");
	private static DecimalFormat distDF = new DecimalFormat("0");
	
	/** Formats distance/magnitude bin centers and epsilon category labels for the chart axes and legend. */
	private static class DisaggCategoryLabelGenerator implements CategoryLabelGenerator<EpsilonCategories, Double, Double> {

		/** @return the epsilon category label (e.g. "0&lt;&epsilon;&lt;0.5") used in the legend */
		@Override
		public String generateSeriesLabel(CategoryDataset3D<EpsilonCategories, Double, Double> dataset,
				EpsilonCategories seriesKey) {
			return seriesKey.label;
		}

		/** @return the rupture distance bin center, formatted as an integer */
		@Override
		public String generateRowLabel(CategoryDataset3D<EpsilonCategories, Double, Double> dataset, Double rowKey) {
			return distDF.format(rowKey);
		}

		/** @return the magnitude bin center, formatted with one or two decimals */
		@Override
		public String generateColumnLabel(CategoryDataset3D<EpsilonCategories, Double, Double> dataset,
				Double columnKey) {
			return magDF.format(columnKey);
		}
		
	}
	
	/** Colors bars and legend entries by {@link EpsilonCategories}. */
	private static class EpsilonColorSource implements CategoryColorSource {

		/** @return the color of the epsilon category for the given series (independent of row and column) */
		@Override
		public Color getColor(int series, int row, int column) {
//			System.out.println("getColor: series="+series+", row="+row+", column="+column);
			return EpsilonCategories.values()[series].color;
		}

		/** @return the legend swatch color of the epsilon category for the given series */
		@Override
		public Color getLegendColor(int series) {
//			System.out.println("getLegendColor: series="+series);
			return EpsilonCategories.values()[series].color;
		}

		/** Does nothing: colors are fixed by {@link EpsilonCategories}. */
		@Override
		public void style(Color... colors) {}
		
	}
	
	/**
	 * Manual test: disaggregates Los Angeles with MeanUCERF2 and ASK 2014 (1 s SA), shows the chart in a window and writes
	 * {@code /tmp/disagg_test.png/.pdf}.
	 * 
	 * @param args unused
	 * @throws InterruptedException if interrupted while the window is open
	 * @throws IOException if the output files cannot be written
	 */
	public static void main(String[] args) throws InterruptedException, IOException {
//		Frankel96_AdjustableEqkRupForecast erf = new Frankel96_AdjustableEqkRupForecast();
//		erf.getTimeSpan().setDuration(1d);
		MeanUCERF2 erf = new MeanUCERF2();
		erf.getTimeSpan().setDuration(30d);
		erf.getTimeSpan().setStartTime(2007);
		
		erf.updateForecast();
		ScalarIMR gmm = AttenRelRef.ASK_2014.get();
		gmm.setParamDefaults();
		gmm.setIntensityMeasure(SA_Param.NAME);
		SA_Param.setPeriodInSA_Param(gmm.getIntensityMeasure(), 1d);
		Site site = new Site(new Location(34.053, -118.243));
		site.addParameterList(gmm.getSiteParams());
		DisaggregationCalculator calc = new DisaggregationCalculator();
		calc.setDistanceRange(5d, 11, 10d);
//		calc.setDistanceRange(5d, 21, 10d);
		calc.setMagRange(5d, 10, 0.5);
//		calc.setMagRange(5d, 20, 0.5);
		calc.disaggregate(Math.log(0.1d), site, gmm, erf,
				new SourceFilterManager(SourceFilters.FIXED_DIST_CUTOFF).getEnabledFilters(),
				DisaggregationCalculator.getDefaultParams());
		
		System.out.println(calc.getBinData());
		
//		calc.setMaxZAxisForPlot(Double.NaN);
//		String url = calc.getDisaggregationPlotUsingServlet("meta");
//		System.out.println(url);
		
//		// try all zeros to make sure it doesn't blow up
//		for (double[][] pdfRow : calc.getDisaggPlotData().getPdf3D())
//			for (double[] pdf : pdfRow)
//				for (int i=0; i<pdf.length; i++)
//					pdf[i] = 0d;
		
		boolean fixed = true;
		Chart3DPanel chartPanel = buildChartPanel(calc.getDisaggPlotData(), fixed);
        
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//        frame.setContentPane(displayPanel);
        frame.setContentPane(chartPanel);
        frame.setSize(800, 800);
        frame.setVisible(true);
//        chartPanel.zoomToFit();
        
        writeChartPlot(new File("/tmp"), "disagg_test", chartPanel, true, true);
        
        while (!fixed) {
        	Thread.sleep(1000);
        	ViewPoint3D vp = chartPanel.getChart().getViewPoint();
        	System.out.println("theta="+Math.toDegrees(vp.getTheta())+", phi="+Math.toDegrees(vp.getPhi())+", rho="+vp.getRho()+", orientation="+Math.toDegrees(vp.calcRollAngle()));
        }
	}

}
