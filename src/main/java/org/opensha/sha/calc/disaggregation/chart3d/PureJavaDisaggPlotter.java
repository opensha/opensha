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

public class PureJavaDisaggPlotter {
	
	public static Chart3DPanel buildChartPanel(DisaggregationPlotData data) {
		return buildChartPanel(data, true);
	}
	
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
	
	public static void writeChartPlot(File outputDir, String prefix, DisaggregationPlotData data,
			int width, int height, boolean writePNG, boolean writePDF) throws IOException {
		Chart3DPanel panel = buildChartPanel(data);
		panel.setSize(width, height);
		panel.setPreferredSize(new Dimension(width, height));
		panel.zoomToFit();
		writeChartPlot(outputDir, prefix, panel, writePNG, writePDF);
	}
	
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
	
	private static class DisaggCategoryLabelGenerator implements CategoryLabelGenerator<EpsilonCategories, Double, Double> {

		@Override
		public String generateSeriesLabel(CategoryDataset3D<EpsilonCategories, Double, Double> dataset,
				EpsilonCategories seriesKey) {
			return seriesKey.label;
		}

		@Override
		public String generateRowLabel(CategoryDataset3D<EpsilonCategories, Double, Double> dataset, Double rowKey) {
			return distDF.format(rowKey);
		}

		@Override
		public String generateColumnLabel(CategoryDataset3D<EpsilonCategories, Double, Double> dataset,
				Double columnKey) {
			return magDF.format(columnKey);
		}
		
	}
	
	private static class EpsilonColorSource implements CategoryColorSource {

		@Override
		public Color getColor(int series, int row, int column) {
//			System.out.println("getColor: series="+series+", row="+row+", column="+column);
			return EpsilonCategories.values()[series].color;
		}

		@Override
		public Color getLegendColor(int series) {
//			System.out.println("getLegendColor: series="+series);
			return EpsilonCategories.values()[series].color;
		}

		@Override
		public void style(Color... colors) {}
		
	}
	
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
