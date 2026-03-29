package org.opensha.commons.gui.plot;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.block.RectangleConstraint;
import org.jfree.chart.encoders.EncoderUtil;
import org.jfree.chart.encoders.ImageFormat;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.Size2D;
import org.jfree.data.Range;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.gui.plot.pdf.PDF_UTF8_FontMapper;

import com.google.common.base.Preconditions;
import com.itextpdf.awt.FontMapper;
import com.itextpdf.awt.PdfGraphics2D;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfTemplate;
import com.itextpdf.text.pdf.PdfWriter;

public class PlotUtils {
	
	public static HeadlessGraphPanel initHeadless(PlotPreferences prefs) {
		return new HeadlessGraphPanel(prefs);
	}
	
	public static HeadlessGraphPanel initScreenHeadless() {
		return new HeadlessGraphPanel(PlotPreferences.getDefaultScreenFigurePrefs());
	}
	
	public static HeadlessGraphPanel initPrintHeadless() {
		return new HeadlessGraphPanel(PlotPreferences.getDefaultPrintFigurePrefs());
	}
	
	public static void setAxisVisible(GraphPanel gp, boolean xVisible, boolean yVisible) {
		gp.getXAxis().setTickLabelsVisible(xVisible);
		gp.getYAxis().setTickLabelsVisible(yVisible);
	}
	
	public static void setGridLinesVisible(GraphPanel gp, boolean xVisible, boolean yVisible) {
		gp.getPlot().setDomainGridlinesVisible(xVisible);
		gp.getPlot().setRangeGridlinesVisible(yVisible);
	}
	
	public static void fixAspectRatio(GraphPanel gp, int width, boolean isLatLon) {
		fixAspectRatio(gp, width, true, isLatLon);
	}
	
	public static void fixAspectRatio(GraphPanel gp, int dimension, boolean haveWidth, boolean isLatLon) {
		fixAspectRatio(gp, dimension, haveWidth, calcAspectRatio(gp.getX_AxisRange(), gp.getY_AxisRange(), isLatLon));
	}
	
	public static void fixAspectRatio(GraphPanel gp, int width, double aspectRatio) {
//		int height = calcHeight(gp.getChartPanel(), width, aspectRatio);
//		gp.getChartPanel().setSize(width, height);
		fixAspectRatio(gp, width, true, aspectRatio);
	}
	
	public static void fixAspectRatio(GraphPanel gp, int dimension, boolean haveWidth, double aspectRatio) {
		int calculated = calcDimension(gp.getChartPanel(), dimension, haveWidth, aspectRatio);
		int width, height;
		if (haveWidth) {
			width = dimension;
			height = calculated;
		} else {
			height = dimension;
			width = calculated;
		}
		gp.getChartPanel().setSize(width, height);
	}
	
	public static double calcAspectRatio(Range xRange, Range yRange, boolean isLatLon) {
		Preconditions.checkNotNull(xRange, "Cannot determine aspect ratio if x range not supplied");
		Preconditions.checkNotNull(yRange, "Cannot determine aspect ratio if y range not supplied");
		if (isLatLon) {
			// correct for latitude
			Location left = new Location(yRange.getCentralValue(), xRange.getLowerBound());
			Location right = new Location(yRange.getCentralValue(), xRange.getUpperBound());
			Location top = new Location(yRange.getUpperBound(), xRange.getCentralValue());
			Location bottom = new Location(yRange.getLowerBound(), xRange.getCentralValue());
			double height = LocationUtils.horzDistance(top, bottom);
			double width = LocationUtils.horzDistance(left, right);
//			System.out.println("Correcting aspect ratio. Raw would be: "+(float)(xRange.getLength()/yRange.getLength())
//					+", corrected is "+(float)(width/height));
			return width/height;
		} else {
			return xRange.getLength()/yRange.getLength();
		}
	}
	
	public static int calcHeight(GraphPanel gp, int width,  boolean isLatLon) {
		return calcHeight(gp.getChartPanel(), width, calcAspectRatio(gp.getX_AxisRange(), gp.getY_AxisRange(), isLatLon));
	}
	
	private static final int ASPECT_CALC_ITERATIONS = 2;
	public static boolean HEIGHT_ASPECT_D = false;
	
	public static int calcHeight(ChartPanel cp, int width, double aspectRatio) {
		return calcDimension(cp, width, true, aspectRatio);
	}
	
	public static int calcWidth(ChartPanel cp, int height, double aspectRatio) {
		return calcDimension(cp, height, false, aspectRatio);
	}
	
	private static int calcDimension(ChartPanel cp, int fixed, boolean haveWidth, double aspectRatio) {
		ChartRenderingInfo chartInfo = new ChartRenderingInfo();
		// start with height = width
		int height = fixed;
		int width = fixed;
		
		// do multiple iterations because actual spacing can change when changing the dimensions
		// e.g., a legend or axis label can take up additional space when the height is updated.
		for (int i=0; i<ASPECT_CALC_ITERATIONS; i++) {
			if (HEIGHT_ASPECT_D) System.out.println("Height calc iteration "+i+", currently "+width+" x "+height
					+"; AR="+(float)((double)width/(double)height)+"; targetAR="+(float)aspectRatio);
			// this forces it to actually render
			cp.getChart().createBufferedImage(width, height, chartInfo);
			Rectangle2D plotArea = chartInfo.getPlotInfo().getDataArea();
			double myWidth = plotArea.getWidth();
			double myHeight = plotArea.getHeight();
//			double myAspect = myWidth/myHeight;
//			System.out.println("Actual plot area: "+myWidth+" x "+myHeight+", aspect="+myAspect);
//			double targetAspect = lonSpan / latSpan;
//			System.out.println("Target aspect: "+aspectRatio);
			Plot plot = cp.getChart().getPlot();

			List<Double> plotHeights = new ArrayList<>();
			List<Double> plotWidths = new ArrayList<>();
			List<XYPlot> subPlots = null;
			if (plot instanceof CombinedRangeXYPlot) {
				// multiple plots arranged horizontally
				CombinedRangeXYPlot combPlot = (CombinedRangeXYPlot)plot;
				subPlots = getSubPlots(combPlot);
				int num = subPlots.size();
				int sumWeights = 0;
				for (XYPlot subPlot : subPlots)
					sumWeights += subPlot.getWeight();
				
				double gap = combPlot.getGap();
				double widthMinusGaps = myWidth - gap * (num-1);
				
				for (XYPlot subPlot : subPlots) {
					int weight = subPlot.getWeight();
					double subWidth = widthMinusGaps * (double)weight / (double)sumWeights;
					// 1 width for each subplot
					plotWidths.add(subWidth);
				}
				// 1 single height
				plotHeights.add(myHeight);
			} else if (plot instanceof CombinedDomainXYPlot) {
				// multiple plots arranged vertically
				CombinedDomainXYPlot combPlot = (CombinedDomainXYPlot)plot;
				subPlots = getSubPlots(combPlot);
				int num = subPlots.size();
				int sumWeights = 0;
				for (XYPlot subPlot : subPlots)
					sumWeights += subPlot.getWeight();
				double gap = combPlot.getGap();

				double heightMinusGaps = myHeight - gap * (num-1);
				
				for (XYPlot subPlot : subPlots) {
					int weight = subPlot.getWeight();
					double subHeight = heightMinusGaps * (double)weight / (double)sumWeights;
					// 1 height for each subplot
					plotHeights.add(subHeight);
				}
				// 1 single width
				plotWidths.add(myWidth);
			} else {
				// single plot
				plotHeights.add(myHeight);
				plotWidths.add(myWidth);
			}
			
			double totalPlotHeight = plotHeights.stream().mapToDouble(D->D).sum();
			double totalPlotWidth = plotWidths.stream().mapToDouble(D->D).sum();
			
			boolean evenlyWeighted = true;
			if (subPlots != null) {
				int weight0 = subPlots.get(0).getWeight();
				for (int j=1; evenlyWeighted && j<subPlots.size(); j++)
					evenlyWeighted = weight0 == subPlots.get(j).getWeight();
			}
			
			if (haveWidth) {
				// calculate height
				double extraHeight = height - totalPlotHeight; // height that's related to gaps, labels, legends, and padding
				if (HEIGHT_ASPECT_D) {
					System.out.println("\tCurrent plot area dimensions: "+(float)totalPlotWidth+" x "
							+(float)totalPlotHeight+"; AR="+(float)(totalPlotWidth/totalPlotHeight));
					System.out.println("\tExtra height: "+extraHeight);
				}
				if (evenlyWeighted) {
					double plotHeight = plotWidths.get(0) / aspectRatio;
					height = (int)(extraHeight + plotHeight*plotHeights.size() + 0.5);
				} else {
					// just do the first subplot
					double origHeight1 = plotHeights.get(0);
					double targetHeight1 = plotWidths.get(0) / aspectRatio;
					if (HEIGHT_ASPECT_D) System.out.println("\tFirst subplot is currently "+plotWidths.get(0).floatValue()+" x "
							+(float)origHeight1+"; AR="+(float)(plotWidths.get(0)/origHeight1));
					// scale the height
					double heightScale1 = targetHeight1 / origHeight1;
					if (HEIGHT_ASPECT_D) System.out.println("\tScaling height by "+(float)heightScale1+" to get to "+plotWidths.get(0).floatValue()+" x "
							+(float)targetHeight1+"; AR="+(float)(plotWidths.get(0)/targetHeight1));
					Preconditions.checkState(heightScale1 > 0d);
					
					height = (int)(extraHeight + totalPlotHeight*heightScale1 + 0.5);
				}
			} else {
				double extraWidth = width - totalPlotWidth; // width that's related to gaps, labels, legends, and padding
				if (evenlyWeighted) {
					double plotWidth = plotHeights.get(0) * aspectRatio;
					width = (int)(extraWidth + plotWidth*plotWidths.size() + 0.5);
				} else {
					// just do the first subplot
					double origWidth1 = plotWidths.get(0);
					double targetWidth1 = plotHeights.get(0) * aspectRatio;
					// scale the width
					double widthScale1 = targetWidth1 / origWidth1;
					Preconditions.checkState(widthScale1 > 0d);
					
					width = (int)(extraWidth + totalPlotWidth*widthScale1 + 0.5);
				}
			}
		}
		return haveWidth ? height : width;
	}
	
	public static void addSubplotTitles(GraphPanel gp, List<String> names, Font font) {
		addSubplotTitles(getSubPlots(gp), names, font);
	}
	
	public static void addSubplotTitles(List<XYPlot> subplots, List<String> names, Font font) {
		Preconditions.checkState(subplots.size() == names.size());
		for (int i=0; i<subplots.size(); i++) {
			String name = names.get(i);
			if (name != null) {
				// do it as an axis, but it'll be on top
				NumberAxis title = new NumberAxis(name);
				title.setLabelFont(font);
				title.setTickLabelsVisible(false);
				title.setTickMarksVisible(false);
				title.setAxisLineVisible(false);
				subplots.get(i).setDomainAxis(1, title); // 1 puts it as the 2nd axis
			}
		}
	}
	
	public static ChartRenderingInfo getRenderingInfo(GraphPanel gp, int width, int height) {
		return getRenderingInfo(gp.getChartPanel(), width, height);
	}
	
	public static ChartRenderingInfo getRenderingInfo(ChartPanel cp, int width, int height) {
		ChartRenderingInfo chartInfo = new ChartRenderingInfo();
		cp.getChart().createBufferedImage(width, height, chartInfo);
		return chartInfo;
	}
	
	// TODO untested, verify and uncomment if needed
//	public static int calcWidth(GraphPanel gp, int height,  boolean isLatLon) {
//		return calcWidth(gp.getChartPanel(), height, calcAspectRatio(gp.getX_AxisRange(), gp.getY_AxisRange(), isLatLon));
//	}
//	
//	public static int calcWidth(ChartPanel cp, int height, double aspectRatio) {
//		ChartRenderingInfo chartInfo = new ChartRenderingInfo();
//		int width = height; // start with height = width
//		// this forces it to actually render
//		cp.getChart().createBufferedImage(width, height, chartInfo);
//		Rectangle2D plotArea = chartInfo.getPlotInfo().getDataArea();
//		double myWidth = plotArea.getWidth();
//		double myHeight = plotArea.getHeight();
////		double myAspect = myWidth/myHeight;
////		System.out.println("Actual plot area: "+myWidth+" x "+myHeight+", aspect="+myAspect);
////		double targetAspect = lonSpan / latSpan;
////		System.out.println("Target aspect: "+targetAspect);
//		double extraWidth = width - myWidth;
//		double plotWidth = myHeight * aspectRatio;
//		return (int)(extraWidth + plotWidth+ 0.5);
//	}
	
	/**
	 * Print DPI, actually points per inch
	 */
	public static final int DEFAULT_PRINT_DPI = 72;
	
	/**
	 * Assumed DPI of a standard computer monitor, used for PNG output when plot size is specified in inches
	 */
	public static final int DEFAULT_SCREEN_DPI = 110;
	/**
	 * Default usable page letter width after subtracting margins (using those for SSA journals)
	 */
	public static final double DEFAULT_USABLE_PAGE_WIDTH = 7.2d;
	/**
	 * Default usable page letter height after subtracting margins (using those for SSA journals)
	 */
	public static final double DEFAULT_USABLE_PAGE_HEIGHT = 9.4d;
	
	public static void writePrintPlots(File outputDir, String prefix, GraphPanel gp, double widthInches, boolean isLatLon,
			boolean writePNG, boolean writePDF, boolean writeTXT) throws IOException {
		writePrintPlots(outputDir, prefix, gp, widthInches, -1d, DEFAULT_SCREEN_DPI, isLatLon, writePNG, writePDF, writeTXT);
	}
	
	public static void writePrintPlots(File outputDir, String prefix, GraphPanel gp, double widthInches, boolean isLatLon,
			int dpi, boolean writePNG, boolean writePDF, boolean writeTXT) throws IOException {
		writePrintPlots(outputDir, prefix, gp, widthInches, -1d, dpi, isLatLon, writePNG, writePDF, writeTXT);
	}
	
	public static void writePrintPlots(File outputDir, String prefix, GraphPanel gp, double widthInches, double heightInches,
			boolean writePNG, boolean writePDF, boolean writeTXT) throws IOException {
		writePrintPlots(outputDir, prefix, gp, widthInches, heightInches, DEFAULT_SCREEN_DPI, false, writePNG, writePDF, writeTXT);
	}
	
	public static void writePrintPlots(File outputDir, String prefix, GraphPanel gp, double widthInches, double heightInches,
			int dpi, boolean writePNG, boolean writePDF, boolean writeTXT) throws IOException {
		writePrintPlots(outputDir, prefix, gp, widthInches, heightInches, dpi, false, writePNG, writePDF, writeTXT);
	}
	
	public static void writePrintPlots(File outputDir, String prefix, GraphPanel gp, double widthInches, double heightInches,
			int dpi, boolean isLatLon, boolean writePNG, boolean writePDF, boolean writeTXT) throws IOException {
		int width = (int)Math.round(widthInches*DEFAULT_PRINT_DPI);
		int height = (int)Math.round(heightInches*DEFAULT_PRINT_DPI);
		// write the PNG at the specified DPI
		double pngScale = dpi == 72 ? 1d : (double)dpi/(double)DEFAULT_PRINT_DPI;
		// PDF will be written with the exact specified dimensions
		double pdfScale = 1d;
		writePlots(outputDir, prefix, gp, width, height, isLatLon, writePNG, pngScale, writePDF, pdfScale, writeTXT);
	}
	
	public static void writePlots(File outputDir, String prefix, GraphPanel gp, int width, int height,
			boolean writePNG, boolean writePDF, boolean writeTXT) throws IOException {
		writePlots(outputDir, prefix, gp, width, height, false, writePNG, 1d, writePDF, 1d, writeTXT);
	}
	
	public static void writePlots(File outputDir, String prefix, GraphPanel gp, int width, boolean isLatLon,
			boolean writePNG, boolean writePDF, boolean writeTXT) throws IOException {
		writePlots(outputDir, prefix, gp, width, -1, isLatLon, writePNG, 1d, writePDF, 1d, writeTXT);
	}
	
	public static void writePlots(File outputDir, String prefix, GraphPanel gp, int width, int height,
			boolean isLatLon, boolean writePNG, double pngScale, boolean writePDF, double pdfScale, boolean writeTXT) throws IOException {
		File file = new File(outputDir, prefix);
		
		Preconditions.checkArgument(width > 0 || height > 0, "must specify either width or height");
		if (height <= 0) {
			fixAspectRatio(gp, width, isLatLon);
			height = gp.getChartPanel().getHeight();
		} else if (width <= 0) {
			fixAspectRatio(gp, height, false, isLatLon);
			width = gp.getChartPanel().getWidth();
		} else {
			gp.getChartPanel().setSize(width, height);
		}
		
		if (writePNG) {
			if (pngScale != 1d)
				gp.saveAsScaledPNG(file.getAbsolutePath()+".png", width, height, pngScale);
			else
				gp.saveAsPNG(file.getAbsolutePath()+".png");
		}
		if (writePDF)
			gp.saveAsPDF(file.getAbsolutePath()+".pdf", width, height, false, pdfScale);
		if (writeTXT)
			gp.saveAsTXT(file.getAbsolutePath()+".txt");
	}
	
	public static void setXTick(GraphPanel gp, double tick) {
		if (isCombinedPlot(gp.getPlot())) {
			for (XYPlot subPlot : getSubPlots(gp))
				setTick(subPlot.getDomainAxis(), tick);
		} else {
			setTick(gp.getXAxis(), tick);
		}
	}
	
	public static void setYTick(GraphPanel gp, double tick) {
		if (isCombinedPlot(gp.getPlot())) {
			for (XYPlot subPlot : getSubPlots(gp))
				setTick(subPlot.getRangeAxis(), tick);
		} else {
			setTick(gp.getYAxis(), tick);
		}
	}
	
	public static void setTick(ValueAxis axis, double tick) {
		TickUnits tus = new TickUnits();
		TickUnit tu = new NumberTickUnit(tick);
		tus.add(tu);
		axis.setStandardTickUnits(tus);
	}
	
	public static void setSubplotGap(GraphPanel gp, double gap) {
		XYPlot plot = gp.getPlot();
		if (plot instanceof CombinedDomainXYPlot) {
			((CombinedDomainXYPlot)plot).setGap(gap);
		} else if (plot instanceof CombinedRangeXYPlot) {
			((CombinedRangeXYPlot)plot).setGap(gap);
		} else {
			throw new IllegalStateException("Can only set gap for CombinedDomainXYPlot or CombinedRangeXYPlot");
		}
	}
	
	public static void setSubPlotWeights(GraphPanel gp, int... weights) {
		List<XYPlot> subPlots = getSubPlots(gp);
		Preconditions.checkState(subPlots.size() == weights.length, "Have %s subplots but %s weights", subPlots.size(), weights.length);
		for (int i=0; i<subPlots.size(); i++)
			subPlots.get(i).setWeight(weights[i]);
	}

	public static List<XYPlot> getSubPlots(GraphPanel gp) {
		return getSubPlots(gp.getPlot());
	}
	
	private static boolean isCombinedPlot(XYPlot plot) {
		return plot instanceof CombinedDomainXYPlot || plot instanceof CombinedRangeXYPlot;
	}

	@SuppressWarnings("unchecked")
	public static List<XYPlot> getSubPlots(XYPlot plot) {
		if (plot instanceof CombinedDomainXYPlot) {
			return ((CombinedDomainXYPlot)plot).getSubplots();
		} else if (plot instanceof CombinedRangeXYPlot) {
			return ((CombinedRangeXYPlot)plot).getSubplots();
		} else {
			throw new IllegalStateException("Can only get sub plots for CombinedDomainXYPlot or CombinedRangeXYPlot");
		}
	}
	
	public static void writeScaleLegendOnly(File outputDir, String prefix, PaintScaleLegend legend, int width,
			boolean writePNG, boolean writePDF) throws IOException {
		writeScaleLegendOnly(outputDir, prefix, legend, width, writePNG, 1d, writePDF, 1d);
	}

	
	public static void writeScaleLegendOnly(File outputDir, String prefix, PaintScaleLegend legend, double widthInches,
			int dpi, boolean writePNG, boolean writePDF) throws IOException {
		int width = (int)Math.round(widthInches*DEFAULT_PRINT_DPI);
		// write the PNG at the specified DPI
		double pngScale = dpi == 72 ? 1d : (double)dpi/(double)DEFAULT_PRINT_DPI;
		// PDF will be written with the exact specified dimensions
		double pdfScale = 1d;
		writeScaleLegendOnly(outputDir, prefix, legend, width, writePNG, pngScale, writePDF, pdfScale);
		
	}
	
	private static void writeScaleLegendOnly(File outputDir, String prefix, PaintScaleLegend legend, int width,
			boolean writePNG, double pngScale, boolean writePDF, double pdfScale) throws IOException {
		// Find the preferred height
		Graphics2D dummyGraphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		RectangleConstraint constraint = new RectangleConstraint(new Range(width, width), new Range(0.05*width, width));
		Size2D preferredSize = legend.arrange(dummyGraphics, constraint);
		int height = (int) Math.ceil(preferredSize.getHeight());

		// Print the preferred height
//        System.out.println("Preferred height: " + height);

        // Clean up dummy graphics
        dummyGraphics.dispose();
		
        if (writePNG) {
        	 BufferedImage image;
             if (pngScale == 1d) {
             	image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
         		Graphics2D g2 = image.createGraphics();

         		// Enable anti-aliasing for better quality
         		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

         		// Draw the PaintScaleLegend onto the Graphics2D context
         		legend.draw(g2, new Rectangle(0, 0, width, height));
         		g2.dispose();
             } else {
             	int W = (int)Math.round(width * pngScale);
         		int H = (int)Math.round(height * pngScale);

         		image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
         		Graphics2D g2 = image.createGraphics();
         		try {
         			// quality hints
         			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
         			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
         			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

         			// scale everything uniformly
         			g2.scale(pngScale, pngScale);
         			
             		legend.draw(g2, new Rectangle(0, 0, width, height));
         		} finally {
         			g2.dispose();
         		}
//         		ImageIO.write(hi, "png", new File(fileName));
         		// for some reason writing this way (rather than ImageIO) plays more nicely with viewers that have the file open,
         		// e.g., eog on Linux will fail to show the updated file (switching to a different image in the same dir)
         		// when using ImageIO
             }
             OutputStream out = new BufferedOutputStream(new FileOutputStream(new File(outputDir, prefix+".png")));
     		try {
     			EncoderUtil.writeBufferedImage(image, ImageFormat.PNG, out);
     		} finally {
     			out.close();
     		}
        }
		
		if (writePDF) {
			float pdfW = (float)(width*pdfScale);
			float pdfH = (float)(height*pdfScale);
			
			// step 1
			Document metadataDocument = new Document(new com.itextpdf.text.Rectangle(
					pdfW, pdfH));
			metadataDocument.addAuthor("OpenSHA");
			metadataDocument.addCreationDate();
//			HeaderFooter footer = new HeaderFooter(new Phrase("Powered by OpenSHA"), true);
//			metadataDocument.setFooter(footer);
			try {
				// step 2
				PdfWriter writer;

				writer = PdfWriter.getInstance(metadataDocument,
						new BufferedOutputStream(new FileOutputStream(new File(outputDir, prefix+".pdf"))));
				// step 3
				metadataDocument.open();
				// step 4
				PdfContentByte cb = writer.getDirectContent();
				PdfTemplate tp = cb.createTemplate(pdfW, pdfH);
//				tp.creategraphics
//				new 
//				FontMapper fontMapper = new DefaultFontMapper();
				FontMapper fontMapper = new PDF_UTF8_FontMapper();
				Graphics2D g2d = new PdfGraphics2D(tp, pdfW, pdfH, fontMapper);
				g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				
				if (pdfScale != 1d)
					g2d.scale(pdfScale, pdfScale);
				
//				Graphics2D g2d = tp.createGraphics(width, height,
//						new DefaultFontMapper());
				Rectangle2D r2d = new Rectangle2D.Double(0, 0, width, height);
				legend.draw(g2d, r2d);
				g2d.dispose();
				cb.addTemplate(tp, 0, 0);
			}
			catch (DocumentException de) {
				de.printStackTrace();
			}
			// step 5
			metadataDocument.close();
		}
	}

}
