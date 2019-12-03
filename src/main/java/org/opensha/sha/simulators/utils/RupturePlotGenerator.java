package org.opensha.sha.simulators.utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYLineAnnotation;
import org.jfree.chart.annotations.XYPolygonAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.data.Range;
import org.jfree.ui.RectangleEdge;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.AnimatedGIFRenderer;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotElement;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotPreferences;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZGraphPanel;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.cpt.CPTVal;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.simulators.EventRecord;
import org.opensha.sha.simulators.RSQSimEvent;
import org.opensha.sha.simulators.SimulatorElement;
import org.opensha.sha.simulators.SimulatorEvent;
import org.opensha.sha.simulators.Vertex;
import org.opensha.sha.simulators.iden.EventIDsRupIden;
import org.opensha.sha.simulators.iden.RuptureIdentifier;
import org.opensha.sha.simulators.parsers.RSQSimFileReader;
import org.opensha.sha.simulators.srf.RSQSimEventSlipTimeFunc;

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
		writeSlipPlot(event, func, outputDir, prefix, null, null, null);
	}
	
	public static Color RECT_COLOR = new Color(0, 70, 0);
	public static Color OTHER_SURF_COLOR = new Color(70, 70, 70);
	public static PlotLineType OTHER_SURF_STROKE = PlotLineType.DASHED;
	
	public static Color HYPO_COLOR = new Color(255, 0, 0, 122);
	public static Color RECT_HYPO_COLOR = new Color(0, 255, 0, 122);
	
	public static Color CA_OUTLINE_COLOR = Color.DARK_GRAY;
	
	public static Color OTHER_ELEM_COLOR = new Color(210, 210, 210);
	
	public static void writeSlipPlot(SimulatorEvent event, RSQSimEventSlipTimeFunc func, File outputDir, String prefix,
			Location[] rectangle, Location rectHypo, RuptureSurface surfaceToOutline) throws IOException {
		writeSlipPlot(event, func, outputDir, prefix, rectangle, rectHypo, surfaceToOutline, false);
	}
	
	public static void writeSlipPlot(SimulatorEvent event, RSQSimEventSlipTimeFunc func, File outputDir, String prefix,
			Location[] rectangle, Location rectHypo, RuptureSurface surfaceToOutline, boolean grayscaleFriendly)
					throws IOException {
		System.out.println("Estimating DAS");
		Location[] refFrame;
		if (rectangle == null)
			refFrame = SimulatorUtils.estimateVertexDAS(event);
		else
			refFrame = SimulatorUtils.estimateVertexDAS(event, rectangle[0], rectangle[1]);
		System.out.println("Done estimating DAS");
		func = func.asRelativeTimeFunc();
		
		boolean contourTimeCPT = false;
		
		CPT slipCPT = GMT_CPT_Files.BLACK_RED_YELLOW_UNIFORM.instance().reverse();
		slipCPT = slipCPT.rescale(0d, Math.ceil(func.getMaxCumulativeSlip()));
		CPT timeCPT;
		if (grayscaleFriendly) {
			double endTime = func.getEndTime();
			timeCPT = slipCPT.rescale(0d, endTime);
			if (endTime > 45d)
				timeCPT = timeCPT.asDiscrete(10d, true);
			else if (endTime > 20d)
				timeCPT = timeCPT.asDiscrete(5d, true);
			else if (endTime > 10d)
				timeCPT = timeCPT.asDiscrete(2d, true);
			else
				timeCPT = timeCPT.asDiscrete(10, true);
		} else {
			timeCPT = GMT_CPT_Files.GMT_WYSIWYG.instance().rescale(0d, func.getEndTime());
		}
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
		XYPolygonAnnotation hypoPoly = new XYPolygonAnnotation(star(hypoDAS, hypoDepth, 1.6), hypoStroke, Color.WHITE,
				grayscaleFriendly ? Color.BLUE : HYPO_COLOR);
		slipPolys.add(hypoPoly);
		firstPolys.add(hypoPoly);
		lastPolys.add(hypoPoly);
		
		double minDAS = Double.POSITIVE_INFINITY;
		double maxDAS = 0d;
		double maxDepth = 0d;
		for (SimulatorElement elem : rupElems) {
			for (Vertex v : elem.getVertices()) {
				double das = v.getDAS();
				if (das < minDAS)
					minDAS = das;
				if (das > maxDAS)
					maxDAS = das;
				if (v.getDepth() > maxDepth)
					maxDepth = v.getDepth();
			}
		}
		System.out.println("Max DAS: "+maxDAS);
		
		if (rectangle != null) {
			Stroke rectStroke = new BasicStroke(3f);
			double[][] rectPoints = new double[rectangle.length][2];
			for (int i=0; i<rectangle.length; i++) {
				rectPoints[i][0] = SimulatorUtils.estimateDAS(refFrame[0], refFrame[1], rectangle[i]);
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
				XYLineAnnotation line = new XYLineAnnotation(p1[0], p1[1], p2[0], p2[1], rectStroke, RECT_COLOR);
				slipPolys.add(line);
				firstPolys.add(line);
				lastPolys.add(line);
			}
		}
		if (rectHypo != null) {
			double rectHypoDAS = SimulatorUtils.estimateDAS(refFrame[0], refFrame[1], rectHypo);
			XYPolygonAnnotation rectHypoPoly = new XYPolygonAnnotation(
					star(rectHypoDAS, rectHypo.getDepth(), 1d), hypoStroke, Color.WHITE, RECT_HYPO_COLOR);
			slipPolys.add(rectHypoPoly);
			firstPolys.add(rectHypoPoly);
			lastPolys.add(rectHypoPoly);
			
			minDAS = Math.min(minDAS, rectHypoDAS);
			maxDAS = Math.max(maxDAS, rectHypoDAS);
			maxDepth = Math.max(maxDepth, rectHypo.getDepth());
		}
		
		if (surfaceToOutline != null) {
			List<RuptureSurface> surfaces = new ArrayList<>();
			if (surfaceToOutline instanceof CompoundSurface)
				surfaces.addAll(((CompoundSurface)surfaceToOutline).getSurfaceList());
			else
				surfaces.add(surfaceToOutline);
			Stroke surfStroke = OTHER_SURF_STROKE.buildStroke(3f);
			for (RuptureSurface surf : surfaces) {
				List<Location> outline = new ArrayList<>(surf.getPerimeter());
				outline.add(outline.get(0)); // close it
				double[] dasVals = new double[outline.size()];
				for (int i=0; i<outline.size(); i++)
					dasVals[i] = SimulatorUtils.estimateDAS(refFrame[0], refFrame[1], outline.get(i));
				
				for (int i=1; i<dasVals.length; i++) {
					XYLineAnnotation line = new XYLineAnnotation(dasVals[i-1], outline.get(i-1).getDepth(),
							dasVals[i], outline.get(i).getDepth(), surfStroke, OTHER_SURF_COLOR);
					slipPolys.add(line);
					firstPolys.add(line);
					lastPolys.add(line);
				}
			}
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
		
		PaintScaleLegend slipCPTbar = XYZGraphPanel.getLegendForCPT(slipCPT, "Cumulative Slip (m)",
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
		slipSpec.addSubtitle(slipCPTbar);
		lastSpec.addSubtitle(timeCPTbar);
		
		List<Range> xRanges = new ArrayList<>();
		List<Range> yRanges = new ArrayList<>();
		xRanges.add(xRange);
		for (int i=0; i<specs.size(); i++)
			yRanges.add(yRange);
		
		gp.setyAxisInverted(true);
		gp.drawGraphPanel(specs, false, false, xRanges, yRanges);
		
		int bufferX = 113;
		int bufferY = 332;
		
		int height = 800;
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
	
	public static void writeSlipAnimation(SimulatorEvent event, RSQSimEventSlipTimeFunc func, File outputFile, double fps)
			throws IOException {
		writeSlipAnimation(event, func, outputFile, fps, null, null);
	}
	
	private static final DecimalFormat timeDF = new DecimalFormat("0.0");
	
	public static void writeSlipAnimation(SimulatorEvent event, RSQSimEventSlipTimeFunc func, File outputFile,
			double fps, Location refLeftLoc, Location refRightLoc) throws IOException {
		System.out.println("Estimating DAS");
		if (refLeftLoc == null || refRightLoc == null)
			SimulatorUtils.estimateVertexDAS(event);
		else
			SimulatorUtils.estimateVertexDAS(event, refLeftLoc, refRightLoc);
		System.out.println("Done estimating DAS");
		func = func.asRelativeTimeFunc();
		
		CPT slipCPT = GMT_CPT_Files.GMT_HOT.instance().reverse().rescale(0.01d, Math.ceil(func.getMaxCumulativeSlip()));
		slipCPT = slipCPT.asDiscrete(20, false);
		slipCPT.add(0, new CPTVal(0f, Color.WHITE, slipCPT.getMinValue(), Color.WHITE));
		CPT velCPT = new CPT(0.01d, func.getMaxSlipVel(), new Color(100, 100, 255), Color.RED, new Color(60, 0, 0));
		velCPT = velCPT.asDiscrete(20, true);
		velCPT.add(0, new CPTVal(0f, Color.WHITE, velCPT.getMinValue(), Color.WHITE));
//		CPT velCPT = new CPT(0.01d, func.getMaxSlipVel(), Color.BLUE, Color.RED, new Color(100, 0, 0));
//		velCPT.add(0, new CPTVal(0f, Color.WHITE, velCPT.getMinValue(), Color.WHITE));
		
		List<SimulatorElement> rupElems = event.getAllElements();
		List<Double> emptyScalars = new ArrayList<>();
		for (int i=0; i<rupElems.size(); i++)
			emptyScalars.add(0d);
		List<XYAnnotation> slipPolys = buildElementPolygons(
				rupElems, emptyScalars, slipCPT, false, Color.BLACK, 0.1d);
		List<XYAnnotation> velPolys = buildElementPolygons(
				rupElems, emptyScalars, velCPT, false, Color.BLACK, 0.1d);
		
		XY_DataSet dummyData = new DefaultXY_DataSet(new double[] {0d}, new double[] {0d});
		List<XY_DataSet> elems = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		elems.add(dummyData);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 0.01f, Color.WHITE));
		
		AnimatedGIFRenderer gifRender = new AnimatedGIFRenderer(outputFile, fps, true);
		
		double dt = 1/fps;
		
		double maxTime = Math.ceil(func.getEndTime()/dt)*dt;
		String title = "Rupture Animation";
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(16);
		gp.setAxisLabelFontSize(18);
		gp.setPlotLabelFontSize(18);
		gp.setBackgroundColor(Color.WHITE);
		
		PlotPreferences prefs = gp.getPlotPrefs();
		
		PaintScaleLegend slipCPTbar = XYZGraphPanel.getLegendForCPT(slipCPT, "Cumulative Slip (m)",
				prefs.getAxisLabelFontSize(), prefs.getTickLabelFontSize(), 1d, RectangleEdge.TOP);
		PaintScaleLegend velCPTbar = null;
		if (func.getMaxSlipVel() != func.getMinSlipVel())
			velCPTbar = XYZGraphPanel.getLegendForCPT(velCPT, "SLip Velocity (m/s)",
					prefs.getAxisLabelFontSize(), prefs.getTickLabelFontSize(), 0.5, RectangleEdge.BOTTOM);
		
		double minDAS = Double.POSITIVE_INFINITY;
		double maxDAS = Double.NEGATIVE_INFINITY;
		double maxDepth = 0d;
		for (SimulatorElement elem : rupElems) {
			for (Vertex v : elem.getVertices()) {
				minDAS = Math.min(minDAS, v.getDAS());
				maxDAS = Math.max(maxDAS, v.getDAS());
				maxDepth = Math.max(maxDepth, v.getDepth());
			}
		}
		
		Range xRange = new Range(Math.min(0, minDAS)-1, maxDAS+1);
		Range yRange = new Range(-1, maxDepth+1);
		List<Range> xRanges = new ArrayList<>();
		List<Range> yRanges = new ArrayList<>();
		xRanges.add(xRange);
		yRanges.add(yRange);
		yRanges.add(yRange);
		
		int totFrames = (int)Math.ceil(maxTime/dt);
		System.out.println("Bulding animation with "+totFrames+" frames");
		System.out.print("Frame:");
		int frameIndex = 0;
		for (double t=0; t<=maxTime; t+=dt) {
			if (frameIndex % 40 == 0)
				System.out.println();
			else
				System.out.print(" ");
			System.out.print((frameIndex++));
			List<PlotSpec> specs = new ArrayList<>();
			
			for (int i=0; i<rupElems.size(); i++) {
				XYPolygonAnnotation slipPoly = (XYPolygonAnnotation)slipPolys.get(i);
				XYPolygonAnnotation velPoly = (XYPolygonAnnotation)velPolys.get(i);
				
				int patchID = rupElems.get(i).getID();
				double slip = func.getCumulativeEventSlip(patchID, t);
				double vel = func.getVelocity(patchID, t);
				
				Color slipColor = slipCPT.getColor((float)slip);
				Color velColor = velCPT.getColor((float)vel);
				
				slipPoly = new XYPolygonAnnotation(slipPoly.getPolygonCoordinates(), slipPoly.getOutlineStroke(),
						slipPoly.getOutlinePaint(), slipColor);
				slipPolys.set(i, slipPoly);
				velPoly = new XYPolygonAnnotation(velPoly.getPolygonCoordinates(), velPoly.getOutlineStroke(),
						velPoly.getOutlinePaint(), velColor);
				velPolys.set(i, velPoly);
			}
			
			String myTitle = title+" ("+timeDF.format(t)+"s)";
			PlotSpec slipSpec = new PlotSpec(elems, chars, myTitle, "Distance Along Strike (km)", "Depth (km)");
			slipSpec.setPlotAnnotations(slipPolys);
			specs.add(slipSpec);
			
			slipSpec.addSubtitle(slipCPTbar);
			
			PlotSpec velSpec = new PlotSpec(elems, chars, myTitle, "Distance Along Strike (km)", "Depth (km)");
			velSpec.setPlotAnnotations(velPolys);
			if (velCPTbar != null)
				velSpec.addSubtitle(velCPTbar);
			specs.add(velSpec);
			
			gp.setyAxisInverted(true);
			gp.drawGraphPanel(specs, false, false, xRanges, yRanges);
			
//			int bufferX = 113;
//			int bufferY = 332;
			int bufferX = 60;
			int bufferY = 100;
			
			int height = 400;
			if (velCPTbar != null) {
				bufferY += 80;
				height += 80;
			}
			double heightEach = (height - bufferY)/3d;
//			System.out.println("Height each: "+heightEach);
//			double targetWidth = heightEach*maxDepth/maxDAS;
			double targetWidth = heightEach*maxDAS/maxDepth;
//			System.out.println("Target Width: "+targetWidth);
			int width = (int)(targetWidth) + bufferX;
			
			gp.getChartPanel().setSize(width, height);
			BufferedImage img = gp.getBufferedImage(width, height);
			
			gifRender.writeFrame(img);
		}
		System.out.println("\nDONE");
		
		gifRender.finalizeAnimation();
	}
	
	public static PlotSpec writeMapPlot(List<SimulatorElement> allElems, SimulatorEvent event, RSQSimEventSlipTimeFunc func,
			File outputDir, String prefix) throws IOException {
		return writeMapPlot(allElems, event, func, outputDir, prefix, null, null, null);
	}
	
	public static PlotSpec writeMapPlot(List<SimulatorElement> allElems, SimulatorEvent event, RSQSimEventSlipTimeFunc func,
			File outputDir, String prefix, Location[] rectangle, Location rectHypo, RuptureSurface surfaceToOutline) throws IOException {
		return writeMapPlot(allElems, event, func, outputDir, prefix, rectangle, rectHypo, surfaceToOutline, null, null, null);
	}
	
	public static PlotSpec writeMapPlot(List<SimulatorElement> allElems, SimulatorEvent event, RSQSimEventSlipTimeFunc func,
			File outputDir, String prefix, Location[] rectangle, Location rectHypo, RuptureSurface surfaceToOutline,
			double[] eventElemScalars, CPT elemCPT, String scalarLabel) throws IOException {
		return writeMapPlot(allElems, event, func, outputDir, prefix, rectangle, rectHypo, surfaceToOutline, eventElemScalars, elemCPT, scalarLabel, null);
	}
	
	public static PlotSpec writeMapPlot(List<SimulatorElement> allElems, SimulatorEvent event, RSQSimEventSlipTimeFunc func,
			File outputDir, String prefix, Location[] rectangle, Location rectHypo, RuptureSurface surfaceToOutline,
			double[] eventElemScalars, CPT elemCPT, String scalarLabel, List<XYAnnotation> anns) throws IOException {
		return writeMapPlot(allElems, event, func, outputDir, prefix, rectangle, rectHypo, surfaceToOutline, null,
				eventElemScalars, elemCPT, scalarLabel, anns);
	}
	
	public static PlotSpec writeMapPlot(List<SimulatorElement> allElems, SimulatorEvent event, RSQSimEventSlipTimeFunc func,
			File outputDir, String prefix, Location[] rectangle, Location rectHypo, RuptureSurface surfaceToOutline,
			List<SimulatorElement> scaledElems, double[] customElemScalars, CPT elemCPT, String scalarLabel,
			List<XYAnnotation> anns) throws IOException {
		// determine extents
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		
		if (event != null) {
			for (SimulatorElement elem : event.getAllElements()) {
				Location loc = elem.getCenterLocation();
				latTrack.addValue(loc.getLatitude());
				lonTrack.addValue(loc.getLongitude());
			}
		}
		if (scaledElems != null) {
			for (SimulatorElement elem : scaledElems) {
				Location loc = elem.getCenterLocation();
				latTrack.addValue(loc.getLatitude());
				lonTrack.addValue(loc.getLongitude());
			}
		}
		
		if (rectangle != null) {
			for (Location loc : rectangle) {
				latTrack.addValue(loc.getLatitude());
				lonTrack.addValue(loc.getLongitude());
			}
		}
		if (surfaceToOutline != null) {
			List<RuptureSurface> surfaces = new ArrayList<>();
			if (surfaceToOutline instanceof CompoundSurface)
				surfaces.addAll(((CompoundSurface)surfaceToOutline).getSurfaceList());
			else
				surfaces.add(surfaceToOutline);
			for (RuptureSurface surf : surfaces) {
				List<Location> outline = surf.getPerimeter();
				for (Location loc : outline) {
					latTrack.addValue(loc.getLatitude());
					lonTrack.addValue(loc.getLongitude());
				}
			}
		}
		if (anns != null) {
			for (XYAnnotation ann : anns) {
				if (ann instanceof XYPolygonAnnotation) {
					double[] poly = ((XYPolygonAnnotation)ann).getPolygonCoordinates();
					for (int i=0; i<poly.length; i++) {
						if (i % 2 == 0)
							lonTrack.addValue(poly[i]);
						else
							latTrack.addValue(poly[i]);
					}
				} else if (ann instanceof XYTextAnnotation) {
					lonTrack.addValue(((XYTextAnnotation)ann).getX());
					latTrack.addValue(((XYTextAnnotation)ann).getY());
				}
			}
		}
		double centerLat = 0.5*(latTrack.getMax() + latTrack.getMin());
		double centerLon = 0.5*(lonTrack.getMax() + lonTrack.getMin());
		double maxDelta = Math.max(latTrack.getMax() - latTrack.getMin(), lonTrack.getMax() - lonTrack.getMin());
		maxDelta *= 1.2;
		maxDelta = Math.max(maxDelta, 0.75);
		Range xRange = new Range(centerLon - 0.5*maxDelta, centerLon + 0.5*maxDelta);
		Range yRange = new Range(centerLat - 0.5*maxDelta, centerLat + 0.5*maxDelta);
		
		// determine thickness of background elements
		DiscretizedFunc regThicknessFunc = buildRegionSizeThicknessFunc();
		double minThickness = regThicknessFunc.getInterpolatedY(maxDelta);
		
		List<SimulatorElement> rupElems = event == null && customElemScalars != null ? scaledElems : event.getAllElements();
		
		double maxDepth;
		if (allElems != null)
			maxDepth = getMaxDepth(allElems);
		else
			maxDepth = getMaxDepth(rupElems);
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		if (customElemScalars == null) {
//			PlotCurveCharacterstics eventChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.BLACK);
//			addElementOutline(funcs, chars, rupElems, eventChar, null);
			double maxRupDepth = getMaxDepth(rupElems);
			addDepthDepOutline(funcs, chars, rupElems, Color.BLACK, maxRupDepth, minThickness, 3d*minThickness);
		} else {
			addElementScalarOutline(funcs, chars, rupElems, customElemScalars, elemCPT, maxDepth, minThickness, 3d*minThickness);
		}
//		for (SimulatorElement elem : rupElems) {
//			Vertex[] vertexes = elem.getVertices();
//			DefaultXY_DataSet xy = new DefaultXY_DataSet();
//			for (int i=0; i<=vertexes.length; i++) {
//				Vertex v;
//				if (i == vertexes.length)
//					v = vertexes[0];
//				else
//					v = vertexes[i];
//				xy.set(v.getLongitude(), v.getLatitude());
//			}
//			funcs.add(xy);
//			chars.add(eventChar);
//		}
		
		BasicStroke hypoStroke = new BasicStroke(1f);
		if (anns == null)
			anns = new ArrayList<>();
		double hypoRadius = 0.02;
		
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
			XYPolygonAnnotation rectHypoPoly = new XYPolygonAnnotation(
					star(hypoLoc.getLongitude(), hypoLoc.getLatitude(), hypoRadius), hypoStroke, Color.BLACK, HYPO_COLOR);
			anns.add(rectHypoPoly);
		} else if (event != null) {
			double firstElemTime = Double.POSITIVE_INFINITY;
			Location hypoLoc = null;
			for (EventRecord rec : event) {
				double[] times = rec.getElementTimeFirstSlips();
				List<SimulatorElement> elems = rec.getElements();
				for (int i=0; i<times.length; i++) {
					if (times[i] < firstElemTime) {
						firstElemTime = times[i];
						hypoLoc = elems.get(i).getCenterLocation();
					}
				}
			}
			XYPolygonAnnotation rectHypoPoly = new XYPolygonAnnotation(
					star(hypoLoc.getLongitude(), hypoLoc.getLatitude(), hypoRadius), hypoStroke, Color.BLACK, HYPO_COLOR);
			anns.add(rectHypoPoly);
		}
		
		if (rectangle != null) {
			PlotCurveCharacterstics rectChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f*(float)minThickness, RECT_COLOR);
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
			XYPolygonAnnotation rectHypoPoly = new XYPolygonAnnotation(
					star(rectHypo.getLongitude(), rectHypo.getLatitude(), hypoRadius), hypoStroke, Color.BLACK, RECT_HYPO_COLOR);
			anns.add(rectHypoPoly);
		}
		
		if (surfaceToOutline != null) {
			PlotCurveCharacterstics rectChar = new PlotCurveCharacterstics(OTHER_SURF_STROKE, 2f*(float)minThickness, OTHER_SURF_COLOR);
			List<RuptureSurface> surfaces = new ArrayList<>();
			if (surfaceToOutline instanceof CompoundSurface)
				surfaces.addAll(((CompoundSurface)surfaceToOutline).getSurfaceList());
			else
				surfaces.add(surfaceToOutline);
			for (RuptureSurface surf : surfaces) {
				List<Location> outline = surf.getPerimeter();
				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				for (int i=0; i<=outline.size(); i++) {
					Location l;
					if (i == outline.size())
						l = outline.get(0);
					else
						l = outline.get(i);
					xy.set(l.getLongitude(), l.getLatitude());
				}
				funcs.add(xy);
				chars.add(rectChar);
			}
		}
		
		if (allElems != null) {
			// now add all elements within region
			PlotCurveCharacterstics allElemChar = new PlotCurveCharacterstics(PlotLineType.SOLID, (float)minThickness, OTHER_ELEM_COLOR);
			
			Region plotRegion = new Region(new Location(yRange.getLowerBound(), xRange.getLowerBound()),
					new Location(yRange.getUpperBound(), xRange.getUpperBound()));
			
			List<XY_DataSet> allElemFuncs = new ArrayList<>();
			List<PlotCurveCharacterstics> allElemChars = new ArrayList<>();
			
			addElementOutline(allElemFuncs, allElemChars, allElems, allElemChar, plotRegion);
			funcs.addAll(0, allElemFuncs);
			chars.addAll(0, allElemChars);
		}
		
		if (CA_OUTLINE_COLOR != null) {
			XY_DataSet[] outlines = PoliticalBoundariesData.loadCAOutlines();
			PlotCurveCharacterstics outlineChar = new PlotCurveCharacterstics(PlotLineType.SOLID, (float)minThickness, CA_OUTLINE_COLOR);
			
			for (XY_DataSet outline : outlines) {
				funcs.add(outline);
				chars.add(outlineChar);
			}
		}
		
		String title = event == null ? null : "Event "+event.getID()+", M"+magDF.format(event.getMagnitude());
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Longitude", "Latitude");
		spec.setPlotAnnotations(anns);
		
		if (outputDir != null) {
			HeadlessGraphPanel gp = new HeadlessGraphPanel();
			gp.setTickLabelFontSize(18);
			gp.setAxisLabelFontSize(24);
			gp.setPlotLabelFontSize(24);
			gp.setBackgroundColor(Color.WHITE);
			
			gp.drawGraphPanel(spec, false, false, xRange, yRange);
			
			if (customElemScalars != null) {
				PaintScaleLegend cptLegend = XYZGraphPanel.getLegendForCPT(elemCPT, scalarLabel, 24, 18, -1, RectangleEdge.BOTTOM);
				gp.getChartPanel().getChart().addSubtitle(cptLegend);
			}
			
			double tick;
			if (maxDelta > 3d)
				tick = 1d;
			else if (maxDelta > 1.5d)
				tick = 0.5;
			else if (maxDelta > 0.8)
				tick = 0.25;
			else
				tick = 0.1;
			TickUnits tus = new TickUnits();
			TickUnit tu = new NumberTickUnit(tick);
			tus.add(tu);
			gp.getXAxis().setStandardTickUnits(tus);
			gp.getYAxis().setStandardTickUnits(tus);
			
			File file = new File(outputDir, prefix);
			gp.getChartPanel().setSize(800, 800);
			gp.saveAsPNG(file.getAbsolutePath()+".png");
			gp.saveAsPDF(file.getAbsolutePath()+".pdf");
		}
		
		return spec;
	}
	
	public static void addElementOutline(List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars,
			List<SimulatorElement> elements, PlotCurveCharacterstics elemChar, Region plotRegion) {
		HashMap<String, DefaultXY_DataSet> prevElemXYs = new HashMap<>();
		
		int elemsAdded = 0;
		for (SimulatorElement elem : elements) {
			Vertex[] vertexes = elem.getVertices();
			if (plotRegion != null) {
				boolean skip = true;
				for (Location loc : vertexes) {
					if (plotRegion.contains(loc)) {
						skip = false;
						break;
					}
				}
				if (skip)
					continue;
			}
			elemsAdded++;
			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			for (int i=0; i<=vertexes.length; i++) {
				Vertex v;
				if (i == vertexes.length)
					v = vertexes[0];
				else
					v = vertexes[i];
				xy.set(v.getLongitude(), v.getLatitude());
			}
			String firstPtStr = pointKey(xy.get(0));
			String lastPtStr = pointKey(xy.get(xy.size()-1));
			if (prevElemXYs.containsKey(firstPtStr)) {
				// bundle it with another
				DefaultXY_DataSet oXY = prevElemXYs.get(firstPtStr);
				for (Point2D pt : xy)
					oXY.set(pt);
			} else {
				prevElemXYs.put(lastPtStr, xy);
				funcs.add(xy);
				chars.add(elemChar);
			}
		}
//		System.out.println("Added "+elemsAdded+"/"+elements.size()+" elems to plot");
//		System.out.println("Used "+prevElemXYs.size()+"/"+elemsAdded+" possible funcs");
	}
	
	private static double getMaxDepth(List<SimulatorElement> elements) {
		double maxDepth = 0d;
		for (SimulatorElement el : elements)
			maxDepth = Math.max(maxDepth, el.getAveDepth());
		return maxDepth;
	}
	
	private static DiscretizedFunc buildElemDepthThicknessFunc(double maxDepth, double thicknessSurface, double thicknessAtDepth) {
		DiscretizedFunc depthThickFunc = new ArbitrarilyDiscretizedFunc();
		depthThickFunc.set(0d, thicknessSurface);
		depthThickFunc.set(maxDepth, thicknessAtDepth);
		return depthThickFunc;
	}
	
	private static DiscretizedFunc buildDepthSaturationFunc(double maxDepth) {
		DiscretizedFunc depthThickFunc = new ArbitrarilyDiscretizedFunc();
		depthThickFunc.set(0d, 1d);
		depthThickFunc.set(maxDepth, 0.5d);
		return depthThickFunc;
	}
	
	private static DiscretizedFunc buildRegionSizeThicknessFunc() {
		DiscretizedFunc func = new ArbitrarilyDiscretizedFunc();
		func.set(0d, 1d); // never go above 1
		func.set(1d, 1d);
		func.set(5d, 0.5);
		func.set(180d, 0.5);
		return func;
	}
	

	
	public static void addElementScalarOutline(List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars,
			List<SimulatorElement> elements, double[] scalars, CPT cpt, double maxDepth, double minThickness, double maxThickness) {
		Preconditions.checkState(elements.size() == scalars.length);
		
		DiscretizedFunc depthThicknessFunc = buildElemDepthThicknessFunc(maxDepth, maxThickness, minThickness);
		DiscretizedFunc depthSaturationFunc = buildDepthSaturationFunc(maxDepth);
		
		Map<SimulatorElement, Double> elemToScalarMap = new HashMap<>();
		for (int e=0; e<elements.size(); e++)
			elemToScalarMap.put(elements.get(e), scalars[e]);
		
		// first sort by depth decreasing (so that upper ones show up on top)
		elements = new ArrayList<>(elements);
		Collections.sort(elements, new Comparator<SimulatorElement>() {

			@Override
			public int compare(SimulatorElement o1, SimulatorElement o2) {
				return Double.compare(o2.getAveDepth(), o1.getAveDepth());
			}
			
		});
		
		for (SimulatorElement elem : elements) {
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
			double depth = elem.getCenterLocation().getDepth();
			float lineWidth = (float)depthThicknessFunc.getInterpolatedY(depth);
			Color color = cpt.getColor(elemToScalarMap.get(elem).floatValue());
			double saturationFactor = depthSaturationFunc.getInterpolatedY(depth);
			color = new Color((int)(saturationFactor*color.getRed() + 0.5), (int)(saturationFactor*color.getGreen() + 0.5),
					(int)(saturationFactor*color.getBlue() + 0.5));
			PlotCurveCharacterstics elemChar = new PlotCurveCharacterstics(PlotLineType.SOLID, lineWidth, color);
			chars.add(elemChar);
		}
	}
	
	public static void addDepthDepOutline(List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars,
			List<SimulatorElement> elements, Color upperColor, double maxDepth, double minThickness, double maxThickness) {
		DiscretizedFunc depthThicknessFunc = buildElemDepthThicknessFunc(maxDepth, maxThickness, minThickness);
		DiscretizedFunc depthSaturationFunc = buildDepthSaturationFunc(maxDepth);
		
		// first sort by depth decreasing (so that upper ones show up on top)
		elements = new ArrayList<>(elements);
		Collections.sort(elements, new Comparator<SimulatorElement>() {

			@Override
			public int compare(SimulatorElement o1, SimulatorElement o2) {
				return Double.compare(o2.getAveDepth(), o1.getAveDepth());
			}
			
		});
		
		for (SimulatorElement elem : elements) {
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
			double depth = elem.getCenterLocation().getDepth();
			float lineWidth = (float)depthThicknessFunc.getInterpolatedY(depth);
			double saturationFactor = depthSaturationFunc.getInterpolatedY(depth);
			Color color = new Color((int)(saturationFactor*upperColor.getRed() + 0.5),
					(int)(saturationFactor*upperColor.getGreen() + 0.5),
					(int)(saturationFactor*upperColor.getBlue() + 0.5));
			PlotCurveCharacterstics elemChar = new PlotCurveCharacterstics(PlotLineType.SOLID, lineWidth, color);
			chars.add(elemChar);
		}
	}
	
	private static String pointKey(Point2D pt) {
		return keyDF.format(pt.getX())+"_"+keyDF.format(pt.getY());
	}

	private static final DecimalFormat keyDF = new DecimalFormat("0.000");
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
//		File catalogDir = new File("/data/kevin/simulators/catalogs/rundir2194_long");
//		File geomFile = new File(catalogDir, "zfault_Deepen.in");
//		File transFile = new File(catalogDir, "trans.rundir2194_long.out");
//		
//		int eventID = 136704;
//		
//		System.out.println("Loading geometry...");
//		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
//		double meanArea = 0d;
//		for (SimulatorElement e : elements)
//			meanArea += e.getArea()/1000000d; // to km^2
//		meanArea /= elements.size();
//		System.out.println("Loaded "+elements.size()+" elements. Mean area: "+(float)meanArea+" km^2");
//		List<RuptureIdentifier> loadIdens = new ArrayList<>();
////		RuptureIdentifier loadIden = new LogicalAndRupIden(new SkipYearsLoadIden(skipYears),
////				new MagRangeRuptureIdentifier(minMag, maxMag),
////				new CatalogLengthLoadIden(maxLengthYears));
//		loadIdens.add(new EventIDsRupIden(eventID));
//		System.out.println("Loading events...");
//		List<RSQSimEvent> events = RSQSimFileReader.readEventsFile(catalogDir, elements, loadIdens);
//		RSQSimStateTransitionFileReader transReader = new RSQSimStateTransitionFileReader(transFile, elements);
//		
//		RSQSimEvent event = events.get(0);
//		RSQSimEventSlipTimeFunc func = new RSQSimEventSlipTimeFunc(transReader.getTransitions(event), 1d);
//		
//		writeSlipPlot(event, func, new File("/tmp"), "plot_test");
		File catalogDir = new File("/data/kevin/simulators/catalogs/rundir2585_1myr");
		File geomFile = new File(catalogDir, "zfault_Deepen.in");
		System.out.println("Loading geometry...");
		List<SimulatorElement> elements = RSQSimFileReader.readGeometryFile(geomFile, 11, 'S');
		ArrayList<RuptureIdentifier> loadIdens = new ArrayList<>();
		loadIdens.add(new EventIDsRupIden(9474557));
		List<RSQSimEvent> events = RSQSimFileReader.readEventsFile(catalogDir, elements, loadIdens);
		for (RSQSimEvent event : events)
			writeMapPlot(elements, event, null, new File("/tmp"), "event_"+event.getID());
	}

}
