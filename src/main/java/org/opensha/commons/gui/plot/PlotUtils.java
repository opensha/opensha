package org.opensha.commons.gui.plot;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;

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
	
	public static void fixAspectRatio(GraphPanel gp, int width,  boolean isLatLon) {
		fixAspectRatio(gp, width, calcAspectRatio(gp.getX_AxisRange(), gp.getY_AxisRange(), isLatLon));
	}
	
	public static void fixAspectRatio(GraphPanel gp, int width, double aspectRatio) {
		int height = calcHeight(gp.getChartPanel(), width, aspectRatio);
		gp.getChartPanel().setSize(width, height);
	}
	
	private static double calcAspectRatio(Range xRange, Range yRange,  boolean isLatLon) {
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
	
	public static int calcHeight(ChartPanel cp, int width, double aspectRatio) {
		ChartRenderingInfo chartInfo = new ChartRenderingInfo();
		int height = width; // start with height = width
		// this forces it to actually render
		cp.getChart().createBufferedImage(width, height, chartInfo);
		Rectangle2D plotArea = chartInfo.getPlotInfo().getDataArea();
		double myWidth = plotArea.getWidth();
		double myHeight = plotArea.getHeight();
//		double myAspect = myWidth/myHeight;
//		System.out.println("Actual plot area: "+myWidth+" x "+myHeight+", aspect="+myAspect);
//		double targetAspect = lonSpan / latSpan;
//		System.out.println("Target aspect: "+targetAspect);
		double extraHeight = height - myHeight;
		double plotHeight = myWidth / aspectRatio;
		return (int)(extraHeight + plotHeight + 0.5);
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
	
	public static void writePlots(File outputDir, String prefix, GraphPanel gp, int width, int height,
			boolean writePNG, boolean writePDF, boolean writeTXT) throws IOException {
		writePlots(outputDir, prefix, gp, width, height, false, writePNG, writePDF, writeTXT);
	}
	
	public static void writePlots(File outputDir, String prefix, GraphPanel gp, int width, boolean isLatLon,
			boolean writePNG, boolean writePDF, boolean writeTXT) throws IOException {
		writePlots(outputDir, prefix, gp, width, -1, isLatLon, writePNG, writePDF, writeTXT);
	}
	
	private static void writePlots(File outputDir, String prefix, GraphPanel gp, int width, int height,
			boolean isLatLon, boolean writePNG, boolean writePDF, boolean writeTXT) throws IOException {
		File file = new File(outputDir, prefix);
		
		Preconditions.checkArgument(width > 0, "");
		if (height <= 0)
			fixAspectRatio(gp, width, isLatLon);
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
		setTick(gp.getXAxis(), tick);
	}
	
	public static void setYTick(GraphPanel gp, double tick) {
		setTick(gp.getYAxis(), tick);
	}
	
	public static void setTick(ValueAxis axis, double tick) {
		TickUnits tus = new TickUnits();
		TickUnit tu = new NumberTickUnit(tick);
		tus.add(tu);
		axis.setStandardTickUnits(tus);
	}
	
	public static void setSubPlotWeights(GraphPanel gp, int... weights) {
		List<XYPlot> subPlots = getSubPlots(gp);
		Preconditions.checkState(subPlots.size() == weights.length, "Have %s subplots but %s weights", subPlots.size(), weights.length);
		for (int i=0; i<subPlots.size(); i++)
			subPlots.get(i).setWeight(weights[i]);
	}

	@SuppressWarnings("unchecked")
	public static List<XYPlot> getSubPlots(GraphPanel gp) {
		XYPlot plot = gp.getPlot();
		if (plot instanceof CombinedDomainXYPlot) {
			return ((CombinedDomainXYPlot)plot).getSubplots();
		} else if (plot instanceof CombinedRangeXYPlot) {
			return ((CombinedRangeXYPlot)plot).getSubplots();
		} else {
			throw new IllegalStateException("Can only get sub plots for CombinedDomainXYPlot or CombinedRangeXYPlot");
		}
	}

}
