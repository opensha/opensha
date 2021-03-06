package org.opensha.commons.gui.plot;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;

import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.data.Range;

import com.google.common.base.Preconditions;

public class PlotUtils {
	
	public static PlotPreferences getDefaultAppPrefs() {
		PlotPreferences pref = PlotPreferences.getDefault();
		pref.setTickLabelFontSize(12);
		pref.setAxisLabelFontSize(14);
		pref.setPlotLabelFontSize(16);
		pref.setLegendFontSize(14);
		pref.setBackgroundColor(new Color( 200, 200, 230 ));
		return pref;
	}
	
	public static PlotPreferences getDefaultFigurePrefs() {
		PlotPreferences pref = PlotPreferences.getDefault();
		pref.setTickLabelFontSize(18);
		pref.setAxisLabelFontSize(24);
		pref.setPlotLabelFontSize(24);
		pref.setLegendFontSize(20);
		pref.setBackgroundColor(Color.WHITE);
		return pref;
	}
	
	public static HeadlessGraphPanel initHeadless() {
		return new HeadlessGraphPanel(getDefaultFigurePrefs());
	}
	
	public static void setAxisVisible(GraphPanel gp, boolean xVisible, boolean yVisible) {
		gp.getXAxis().setTickLabelsVisible(xVisible);
		gp.getYAxis().setTickLabelsVisible(yVisible);
	}
	
	public static void setGridLinesVisible(GraphPanel gp, boolean xVisible, boolean yVisible) {
		gp.getPlot().setDomainGridlinesVisible(xVisible);
		gp.getPlot().setRangeGridlinesVisible(yVisible);
	}
	
	public static void fixAspectRatio(GraphPanel gp, int width) {
		fixAspectRatio(gp, width, calcAspectRatio(gp));
	}
	
	private static double calcAspectRatio(GraphPanel gp) {
		Range xRange = gp.getX_AxisRange();
		Range yRange = gp.getY_AxisRange();
		Preconditions.checkNotNull(xRange, "Cannot determine aspect ratio if x range not supplied");
		Preconditions.checkNotNull(yRange, "Cannot determine aspect ratio if y range not supplied");
		return xRange.getLength()/yRange.getLength();
	}
	
	public static void fixAspectRatio(GraphPanel gp, int width, double aspectRatio) {
		int height = calcHeight(gp, width, aspectRatio);
		gp.getChartPanel().setSize(width, height);
	}
	
	public static int calcHeight(GraphPanel gp, int width) {
		return calcHeight(gp, width, calcAspectRatio(gp));
	}
	
	public static int calcHeight(GraphPanel gp, int width, double aspectRatio) {
		ChartRenderingInfo chartInfo = new ChartRenderingInfo();
		int height = width; // start with height = width
		gp.getChartPanel().getChart().createBufferedImage(width, height, chartInfo);
		Rectangle2D plotArea = chartInfo.getPlotInfo().getDataArea();
		double myWidth = plotArea.getWidth();
		double myHeight = plotArea.getHeight();
		double myAspect = myWidth/myHeight;
//		System.out.println("Actual plot area: "+myWidth+" x "+myHeight+", aspect="+myAspect);
//		double targetAspect = lonSpan / latSpan;
//		System.out.println("Target aspect: "+targetAspect);
		double extraHeight = height - myHeight;
		double plotHeight = myWidth / aspectRatio;
		return (int)(extraHeight + plotHeight + 0.5);
	}
	
	public static void writePlots(File outputDir, String prefix, GraphPanel gp, int width, int height,
			boolean writePNG, boolean writePDF, boolean writeTXT) throws IOException {
		File file = new File(outputDir, prefix);
		
		Preconditions.checkArgument(width > 0, "");
		if (height <= 0)
			fixAspectRatio(gp, width);
		else
			gp.getChartPanel().setSize(width, height);
		
		if (writePNG)
			gp.saveAsPNG(file.getAbsolutePath()+".png");
		if (writePDF)
			gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		if (writeTXT)
			gp.saveAsTXT(file.getAbsolutePath()+".txt");
	}
	
	public static void setXTick(GraphPanel gp, double tick) {
		TickUnits tus = new TickUnits();
		TickUnit tu = new NumberTickUnit(tick);
		tus.add(tu);
		gp.getXAxis().setStandardTickUnits(tus);
	}
	
	public static void setYTick(GraphPanel gp, double tick) {
		TickUnits tus = new TickUnits();
		TickUnit tu = new NumberTickUnit(tick);
		tus.add(tu);
		gp.getYAxis().setStandardTickUnits(tus);
	}

}
