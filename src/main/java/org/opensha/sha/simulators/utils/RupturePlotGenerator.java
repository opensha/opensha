package org.opensha.sha.simulators.utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYPolygonAnnotation;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.data.Range;
import org.jfree.ui.RectangleEdge;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotPreferences;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZGraphPanel;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.sha.earthquake.FocalMechanism;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.Vertex;
import org.opensha.sha.simulators.iden.EventIDsRupIden;
import org.opensha.sha.simulators.iden.RuptureIdentifier;
import org.opensha.sha.simulators.parsers.RSQSimFileReader;
import org.opensha.sha.simulators.srf.RSQSimEventSlipTimeFunc;
import org.opensha.sha.simulators.srf.RSQSimStateTransitionFileReader;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class RupturePlotGenerator {
	
	public static List<XYAnnotation> buildElementPolygons(List<SimulatorElement> elems, List<Double> scalars, CPT cpt, boolean skipNan,
			Color outlineColor, double outlineThickness) {
		List<XYAnnotation> anns = new ArrayList<>();
		
		Stroke stroke = null;
		if (outlineColor != null && outlineThickness > 0)
			stroke = new BasicStroke((float)outlineThickness);
		
		for (int i=0; i<elems.size(); i++) {
			double val = scalars.get(i);
			if (skipNan && Double.isNaN(val))
				continue;
			SimulatorElement elem = elems.get(i);
			Vertex[] vertexes = elem.getVertices();
			double[] points = new double[vertexes.length*2];
			int cnt = 0;
			for (Vertex v : vertexes) {
				double das = v.getDAS();
				Preconditions.checkState(!Double.isNaN(das), "DAS is nan");
				double depth = v.getDepth();
				points[cnt++] = das;
				points[cnt++] = depth;
			}
			Color c = cpt.getColor((float)val);
			XYPolygonAnnotation poly = new XYPolygonAnnotation(points, stroke, outlineColor, c);
			anns.add(poly);
		}
		
		return anns;
	}
	
	public static List<Double> getCumulativeSlipScalars(SimulatorEvent event) {
		return Doubles.asList(event.getAllElementSlips());
	}
	
	public static List<Double> getTimeFirstSlipScalars(SimulatorEvent event, RSQSimEventSlipTimeFunc func) {
		List<Double> scalars = new ArrayList<>();
		
		for (SimulatorElement e : event.getAllElements())
			scalars.add(func.getTimeOfFirstSlip(e.getID()));
		
		return scalars;
	}
	
	public static List<Double> getTimeLastSlipScalars(SimulatorEvent event, RSQSimEventSlipTimeFunc func) {
		List<Double> scalars = new ArrayList<>();
		
		for (SimulatorElement e : event.getAllElements())
			scalars.add(func.getTimeOfLastSlip(e.getID()));
		
		return scalars;
	}
	
	public static void writeSlipPlot(SimulatorEvent event, RSQSimEventSlipTimeFunc func, File outputDir, String prefix)
			throws IOException {
		writeSlipPlot(event, func, outputDir, prefix, null, null);
	}
	
	public static void writeSlipPlot(SimulatorEvent event, RSQSimEventSlipTimeFunc func, File outputDir, String prefix,
			Location[] rectangle, Location rectHypo) throws IOException {
		System.out.println("Estimating DAS");
		SimulatorUtils.estimateVertexDAS(event);
		System.out.println("Done estimating DAS");
		func = func.asRelativeTimeFunc();
		
		boolean contourTimeCPT = false;
		
		CPT slipCPT = GMT_CPT_Files.GMT_HOT.instance().reverse().rescale(0d, Math.ceil(func.getMaxCumulativeSlip()));
		CPT timeCPT = GMT_CPT_Files.GMT_WYSIWYG.instance().rescale(0d, func.getEndTime());
		timeCPT.setAboveMaxColor(timeCPT.getMaxColor());
		if (contourTimeCPT) {
			CPT contourCPT = new CPT();
			for (int i=0; i<(int)Math.ceil(func.getEndTime()); i++) {
				Color c = timeCPT.getColor((float)i);
				contourCPT.add(new CPTVal((float)i, c, (float)i+1, c));
			}
			contourCPT.setAboveMaxColor(contourCPT.getMaxColor());
			timeCPT = contourCPT;
		}
		
		List<SimulatorElement> rupElems = event.getAllElements();
		List<XYAnnotation> slipPolys = buildElementPolygons(
				rupElems, getCumulativeSlipScalars(event), slipCPT, false, Color.BLACK, 0.1d);
		List<XYAnnotation> firstPolys = buildElementPolygons(
				rupElems, getTimeFirstSlipScalars(event, func), timeCPT, false, Color.BLACK, 0.1d);
		List<XYAnnotation> lastPolys = buildElementPolygons(
				rupElems, getTimeLastSlipScalars(event, func), timeCPT, false, Color.BLACK, 0.1d);
		
		XY_DataSet dummyData = new DefaultXY_DataSet(new double[] {0d}, new double[] {0d});
		List<XY_DataSet> elems = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		elems.add(dummyData);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 0.01f, Color.WHITE));
		
		// add hypocenter
		double firstElemTime = Double.POSITIVE_INFINITY;
		double hypoDAS = 0d;
		double hypoDepth = 0d;
		for (SimulatorElement elem : rupElems) {
			double time = func.getTimeOfFirstSlip(elem.getID());
			if (time < firstElemTime) {
				firstElemTime = time;
				hypoDAS = elem.getAveDAS();
				hypoDepth = elem.getCenterLocation().getDepth();
			}
		}
		Stroke hypoStroke = new BasicStroke(1.5f);
		Color hypoPaint = new Color(255, 0, 0, 122);
		XYPolygonAnnotation hypoPoly = new XYPolygonAnnotation(star(hypoDAS, hypoDepth, 1), hypoStroke, Color.WHITE, hypoPaint);
		slipPolys.add(hypoPoly);
		firstPolys.add(hypoPoly);
		lastPolys.add(hypoPoly);
		
		Location firstLoc = null;
		Location lastLoc = null;
		double minDAS = Double.POSITIVE_INFINITY;
		double maxDAS = 0d;
		double maxDepth = 0d;
		for (SimulatorElement elem : rupElems) {
			for (Vertex v : elem.getVertices()) {
				double das = v.getDAS();
				if (das < minDAS) {
					minDAS = das;
					firstLoc = v;
				}
				if (das > maxDAS) {
					maxDAS = das;
					lastLoc = v;
				}
				if (v.getDepth() > maxDepth)
					maxDepth = v.getDepth();
			}
		}
		System.out.println("Max DAS: "+maxDAS);
		
		if (rectangle != null) {
			Stroke rectStroke = new BasicStroke(3f);
			double[][] rectPoints = new double[rectangle.length][2];
			for (int i=0; i<rectangle.length; i++) {
				rectPoints[i][0] = SimulatorUtils.estimateDAS(firstLoc, lastLoc, rectangle[i]);
				rectPoints[i][1] = rectangle[i].getDepth();
				minDAS = Math.min(minDAS, rectPoints[i][0]);
				maxDAS = Math.max(maxDAS, rectPoints[i][0]);
				maxDepth = Math.max(maxDepth, rectPoints[i][1]);
			}
			for (int i=0; i<rectPoints.length; i++) {
				double[] p1 = rectPoints[i];
				double[] p2;
				if (i == rectPoints.length-1)
					p2 = rectPoints[0];
				else
					p2 = rectPoints[i+1];
				XYLineAnnotation line = new XYLineAnnotation(p1[0], p1[1], p2[0], p2[1], rectStroke, Color.GRAY);
				slipPolys.add(line);
				firstPolys.add(line);
				lastPolys.add(line);
			}
		}
		if (rectHypo != null) {
			double rectHypoDAS = SimulatorUtils.estimateDAS(firstLoc, lastLoc, rectHypo);
			Color rectYypoPaint = new Color(255, 255, 0, 122);
			XYPolygonAnnotation rectHypoPoly = new XYPolygonAnnotation(
					star(rectHypoDAS, rectHypo.getDepth(), 1), hypoStroke, Color.WHITE, rectYypoPaint);
			slipPolys.add(rectHypoPoly);
			firstPolys.add(rectHypoPoly);
			lastPolys.add(rectHypoPoly);
			
			minDAS = Math.min(minDAS, rectHypoDAS);
			maxDAS = Math.max(maxDAS, rectHypoDAS);
			maxDepth = Math.max(maxDepth, rectHypo.getDepth());
		}
		
		Range xRange = new Range(Math.min(0, minDAS)-1, maxDAS+1);
		Range yRange = new Range(-1, maxDepth+1);
		
		DefaultXY_DataSet surfFunc = new DefaultXY_DataSet();
		surfFunc.set(xRange.getLowerBound(), 0d);
		surfFunc.set(xRange.getUpperBound(), 0d);
		elems.add(surfFunc);
		chars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, new Color(139, 69, 19)));
		
		List<PlotSpec> specs = new ArrayList<>();
		
		String title = "Event "+event.getID()+", M"+magDF.format(event.getMagnitude());
		
		PlotSpec slipSpec = new PlotSpec(elems, chars, title, "Distance Along Strike (km)", "Max Slip");
		slipSpec.setPlotAnnotations(slipPolys);
		specs.add(slipSpec);
		
		PlotSpec firstSpec = new PlotSpec(elems, chars, title, "Distance Along Strike (km)", "Time First Slip");
		firstSpec.setPlotAnnotations(firstPolys);
		specs.add(firstSpec);
		
		PlotSpec lastSpec = new PlotSpec(elems, chars, title, "Distance Along Strike (km)", "Time Last Slip");
		lastSpec.setPlotAnnotations(lastPolys);
		specs.add(lastSpec);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(24);
		gp.setBackgroundColor(Color.WHITE);
		
		PlotPreferences prefs = gp.getPlotPrefs();
		
		PaintScaleLegend slipCPTbar = XYZGraphPanel.getLegendForCPT(slipCPT, "Slim (m)",
				prefs.getAxisLabelFontSize(), prefs.getTickLabelFontSize(), 1d, RectangleEdge.TOP);
		double timeInc;
		if (func.getEndTime() > 20)
			timeInc = 5;
		else if (func.getEndTime() > 10)
			timeInc = 2;
		else
			timeInc = 1;
		PaintScaleLegend timeCPTbar = XYZGraphPanel.getLegendForCPT(timeCPT, "Time (s)",
				prefs.getAxisLabelFontSize(), prefs.getTickLabelFontSize(), timeInc, RectangleEdge.BOTTOM);
		
		List<Range> xRanges = new ArrayList<>();
		List<Range> yRanges = new ArrayList<>();
		xRanges.add(xRange);
		for (int i=0; i<specs.size(); i++)
			yRanges.add(yRange);
		
		gp.setyAxisInverted(true);
		gp.drawGraphPanel(specs, false, false, xRanges, yRanges);
		gp.addSubtitle(slipCPTbar);
		gp.addSubtitle(timeCPTbar);
		
		int bufferX = 113;
		int bufferY = 332;
		
		int height = 1000;
		double heightEach = (height - bufferY)/3d;
		System.out.println("Height each: "+heightEach);
//		double targetWidth = heightEach*maxDepth/maxDAS;
		double targetWidth = heightEach*maxDAS/maxDepth;
		System.out.println("Target Width: "+targetWidth);
		int width = (int)(targetWidth) + bufferX;
		
		File file = new File(outputDir, prefix);
		gp.getChartPanel().setSize(width, height);
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
	}
	
	public static void writeMapPlot(SimulatorEvent event, RSQSimEventSlipTimeFunc func, File outputDir, String prefix)
			throws IOException {
		writeMapPlot(event, null, outputDir, prefix, null, null);
	}
	
	public static void writeMapPlot(SimulatorEvent event, RSQSimEventSlipTimeFunc func, File outputDir, String prefix,
			Location[] rectangle, Location rectHypo) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		PlotCurveCharacterstics eventChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK);
		
		ArrayList<SimulatorElement> rupElems = event.getAllElements();
		for (SimulatorElement elem : rupElems) {
			Vertex[] vertexes = elem.getVertices();
			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			for (int i=0; i<=vertexes.length; i++) {
				Vertex v;
				if (i == vertexes.length)
					v = vertexes[0];
				else
					v = vertexes[i];
				xy.set(v.getLongitude(), v.getLatitude());
			}
			funcs.add(xy);
			chars.add(eventChar);
		}
		
		if (func != null) {
			double firstElemTime = Double.POSITIVE_INFINITY;
			Location hypoLoc = null;
			for (SimulatorElement elem : rupElems) {
				double time = func.getTimeOfFirstSlip(elem.getID());
				if (time < firstElemTime) {
					firstElemTime = time;
					hypoLoc = elem.getCenterLocation();
				}
			}
			PlotCurveCharacterstics hypoChar = new PlotCurveCharacterstics(PlotSymbol.INV_TRIANGLE, 4f, Color.RED);
			DefaultXY_DataSet hypoXY = new DefaultXY_DataSet();
			hypoXY.set(hypoLoc.getLongitude(), hypoLoc.getLatitude());
			funcs.add(hypoXY);
			chars.add(hypoChar);
		}
		
		if (rectangle != null) {
			PlotCurveCharacterstics rectChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GRAY);
			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			for (int i=0; i<=rectangle.length; i++) {
				Location l;
				if (i == rectangle.length)
					l = rectangle[0];
				else
					l = rectangle[i];
				xy.set(l.getLongitude(), l.getLatitude());
			}
			funcs.add(xy);
			chars.add(rectChar);
		}
		
		if (rectHypo != null) {
			PlotCurveCharacterstics hypoChar = new PlotCurveCharacterstics(PlotSymbol.INV_TRIANGLE, 4f, Color.YELLOW);
			DefaultXY_DataSet hypoXY = new DefaultXY_DataSet();
			hypoXY.set(rectHypo.getLongitude(), rectHypo.getLatitude());
			funcs.add(hypoXY);
			chars.add(hypoChar);
		}
		
		String title = "Event "+event.getID()+", M"+magDF.format(event.getMagnitude());
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Longitude", "Latitude");
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(24);
		gp.setBackgroundColor(Color.WHITE);
		
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		
		for (XY_DataSet xy : funcs) {
			for (Point2D pt : xy) {
				latTrack.addValue(pt.getY());
				lonTrack.addValue(pt.getX());
			}
		}
		double centerLat = latTrack.getAverage();
		double centerLon = lonTrack.getAverage();
		double maxDelta = Math.max(latTrack.getMax() - latTrack.getMin(), lonTrack.getMax() - lonTrack.getMin());
		maxDelta *= 1.1;
		Range xRange = new Range(centerLon - 0.5*maxDelta, centerLon + 0.5*maxDelta);
		Range yRange = new Range(centerLat - 0.5*maxDelta, centerLat + 0.5*maxDelta);
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		
//		int bufferX = 113;
//		int bufferY = 332;
//		
//		int height = 1000;
//		double heightEach = (height - bufferY)/3d;
//		System.out.println("Height each: "+heightEach);
////		double targetWidth = heightEach*maxDepth/maxDAS;
//		double targetWidth = heightEach*maxDAS/maxDepth;
//		System.out.println("Target Width: "+targetWidth);
//		int width = (int)(targetWidth) + bufferX;
		
		File file = new File(outputDir, prefix);
		gp.getChartPanel().setSize(1000, 1000);
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		gp.saveAsPDF(file.getAbsolutePath()+".pdf");
	}
	
	private static final DecimalFormat magDF = new DecimalFormat("0.00");
	
	private static double[] star(double x, double y, double radius) {
		double outerRatio = 2.618;
		int num = 10;
		double radsEach = Math.PI/5d;
		
		double[] poly = new double[num*2];
		
		int count = 0;
		
		for (int i=0; i<10; i++) {
			double dist;
			if (i % 2 == 1)
				dist = radius;
			else
				dist = radius/outerRatio;
			double angle = radsEach*i;
			
			double dx = Math.sin(angle)*dist;
			double dy = Math.cos(angle)*dist;
			
			poly[count++] = x + dx;
			poly[count++] = y + dy;
		}
		
		return poly;
	}

	public static void main(String[] args) throws IOException {
		File catalogDir = new File("/data/kevin/simulators/catalogs/rundir2194_long");
		File geomFile = new File(catalogDir, "zfault_Deepen.in");
		File transFile = new File(catalogDir, "trans.rundir2194_long.out");
		
		int eventID = 136704;
		
		System.out.println("Loading geometry...");
		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
		double meanArea = 0d;
		for (SimulatorElement e : elements)
			meanArea += e.getArea()/1000000d; // to km^2
		meanArea /= elements.size();
		System.out.println("Loaded "+elements.size()+" elements. Mean area: "+(float)meanArea+" km^2");
		List<RuptureIdentifier> loadIdens = new ArrayList<>();
//		RuptureIdentifier loadIden = new LogicalAndRupIden(new SkipYearsLoadIden(skipYears),
//				new MagRangeRuptureIdentifier(minMag, maxMag),
//				new CatalogLengthLoadIden(maxLengthYears));
		loadIdens.add(new EventIDsRupIden(eventID));
		System.out.println("Loading events...");
		List<RSQSimEvent> events = RSQSimFileReader.readEventsFile(catalogDir, elements, loadIdens);
		RSQSimStateTransitionFileReader transReader = new RSQSimStateTransitionFileReader(transFile, elements);
		
		RSQSimEvent event = events.get(0);
		RSQSimEventSlipTimeFunc func = new RSQSimEventSlipTimeFunc(transReader.getTransitions(event), 1d);
		
		writeSlipPlot(event, func, new File("/tmp"), "plot_test");
	}

}
