package org.opensha.commons.gui.plot;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.CombinedRangeXYPlot;
import org.jfree.chart.plot.Plot;
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
	private static boolean HEIGHT_D = false;
	public static int calcHeight(ChartPanel cp, int width, double aspectRatio) {
		ChartRenderingInfo chartInfo = new ChartRenderingInfo();
		int height = width; // start with height = width
		
		// do multiple iterations because actual spacing can change when changing the dimensions
		// e.g., a legend or axis label can take up additional space when the height is updated.
		for (int i=0; i<ASPECT_CALC_ITERATIONS; i++) {
			if (HEIGHT_D) System.out.println("Height calc iteration "+i+", currently "+width+" x "+height
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
					plotWidths.add(subWidth);
				}
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
					plotHeights.add(subHeight);
				}
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
			
			double extraHeight = height - totalPlotHeight; // height that's related to gaps, labels, legends, and padding
			if (HEIGHT_D) {
				System.out.println("\tCurrent plot area dimensions: "+(float)totalPlotWidth+" x "
						+(float)totalPlotHeight+"; AR="+(float)(totalPlotWidth/totalPlotHeight));
				System.out.println("\tExtra height: "+extraHeight);
			}
			if (evenlyWeighted) {
				double plotHeight = totalPlotWidth / aspectRatio;
				height = (int)(extraHeight + plotHeight*plotHeights.size() + 0.5);
			} else {
				// just do the first subplot
				double origHeight1 = plotHeights.get(0);
				double targetHeight1 = plotWidths.get(0) / aspectRatio;
				System.out.println("\tFirst subplot is currently "+plotWidths.get(0).floatValue()+" x "
						+(float)origHeight1+"; AR="+(float)(plotWidths.get(0)/origHeight1));
				// scale the height
				double heightScale1 = targetHeight1 / origHeight1;
				System.out.println("\tScaling height by "+(float)heightScale1+" to get to "+plotWidths.get(0).floatValue()+" x "
						+(float)targetHeight1+"; AR="+(float)(plotWidths.get(0)/targetHeight1));
				Preconditions.checkState(heightScale1 > 0d);
				
				height = (int)(extraHeight + totalPlotHeight*heightScale1 + 0.5);
			}
		}
		return height;
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

}
