package org.opensha.sha.earthquake.faultSysSolution.reports.plots;

import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.chart.annotations.XYPolygonAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.HistogramFunction;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZGraphPanel;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.ClusterRuptures;
import org.opensha.sha.earthquake.faultSysSolution.reports.AbstractRupSetPlot;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.plots.RupHistogramPlots.RakeType;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.ClusterRupture;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.plausibility.impl.JumpAzimuthChangeFilter.AzimuthCalc;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.RuptureTreeNavigator;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.util.SectionDistanceAzimuthCalculator;
import org.opensha.sha.faultSurface.FaultSection;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class JumpAzimuthsPlot extends AbstractRupSetPlot {

	@Override
	public String getName() {
		return "Jump Azimuths";
	}

	@Override
	public List<String> plot(FaultSystemRupSet rupSet, FaultSystemSolution sol, ReportMetadata meta, File resourcesDir,
			String relPathToResources, String topLink) throws IOException {
		List<String> lines = new ArrayList<>();
		
		List<RakeType> rakeTypes = new ArrayList<>();
		rakeTypes.add(null);
		for (RakeType type : RakeType.values())
			rakeTypes.add(type);
		
		Table<RakeType, RakeType, List<Double>> inputRakeAzTable = calcJumpAzimuths(rupSet);
		Table<RakeType, RakeType, List<Double>> compRakeAzTable = null;
		if (meta.comparison != null)
			compRakeAzTable = calcJumpAzimuths(meta.comparison.rupSet);
		
		for (RakeType sourceType : rakeTypes) {
			String prefix, title;
			if (sourceType == null) {
				prefix = "jump_az_any";
				title = "Jumps from Any";
				lines.add(getSubHeading()+" Jump Azimuths From Any");
			} else {
				prefix = "jump_az_"+sourceType.prefix;
				title = "Jumps from "+sourceType.name;
				lines.add(getSubHeading()+" Jump Azimuths From "+sourceType.name);
			}
			
			System.out.println("Plotting "+title);

			lines.add(topLink); lines.add("");
			
			TableBuilder table = MarkdownUtils.tableBuilder();
			if (meta.comparison != null)
				table.addLine(meta.primary.name, meta.comparison.name);
			
			table.initNewLine();
			File plotFile = plotJumpAzimuths(sourceType, rakeTypes, inputRakeAzTable,
					resourcesDir, prefix, title);
			table.addColumn("!["+title+"]("+relPathToResources+"/"+plotFile.getName()+")");
			if (meta.comparison != null) {
				plotFile = plotJumpAzimuths(sourceType, rakeTypes, compRakeAzTable,
						resourcesDir, prefix+"_comp", title);
				table.addColumn("!["+title+"]("+relPathToResources+"/"+plotFile.getName()+")");
			}
			table.finalizeLine();
			lines.addAll(table.build());
			lines.add("");
			
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			
			for (RakeType destType : rakeTypes) {
				String myPrefix = prefix+"_";
				String myTitle = title+" to ";
				if (destType == null) {
					myPrefix += "any";
					myTitle += "Any";
				} else {
					myPrefix += destType.prefix;
					myTitle += destType.name;
				}
				
				plotFile = plotJumpAzimuthsRadial(sourceType, destType, inputRakeAzTable,
						resourcesDir, myPrefix, myTitle);
				table.addColumn("!["+title+"]("+relPathToResources+"/"+plotFile.getName()+")");
			}
			table.finalizeLine();
			lines.addAll(table.wrap(3, 0).build());
			lines.add("");
		}
		return lines;
	}

	@Override
	public Collection<Class<? extends OpenSHA_Module>> getRequiredModules() {
		return List.of(SectionDistanceAzimuthCalculator.class, ClusterRuptures.class);
	}
	
	public static Table<RakeType, RakeType, List<Double>> calcJumpAzimuths(
			FaultSystemRupSet rupSet) {
		SectionDistanceAzimuthCalculator distAzCalc = rupSet.getModule(SectionDistanceAzimuthCalculator.class);
		ClusterRuptures rups = rupSet.getModule(ClusterRuptures.class);
		AzimuthCalc azCalc = new JumpAzimuthChangeFilter.SimpleAzimuthCalc(distAzCalc);
		Table<RakeType, RakeType, List<Double>> ret = HashBasedTable.create();
		for (RakeType r1 : RakeType.values())
			for (RakeType r2 : RakeType.values())
				ret.put(r1, r2, new ArrayList<>());
		for (ClusterRupture rup : rups) {
			RuptureTreeNavigator navigator = rup.getTreeNavigator();
			for (Jump jump : rup.getJumpsIterable()) {
				RakeType sourceRake = null, destRake = null;
				for (RakeType type : RakeType.values()) {
					if (type.isMatch(jump.fromSection.getAveRake()))
						sourceRake = type;
					if (type.isMatch(jump.toSection.getAveRake()))
						destRake = type;
				}
				Preconditions.checkNotNull(sourceRake);
				Preconditions.checkNotNull(destRake);
				FaultSection before1 = navigator.getPredecessor(jump.fromSection);
				if (before1 == null)
					continue;
				FaultSection before2 = jump.fromSection;
				double beforeAz = azCalc.calcAzimuth(before1, before2);
				FaultSection after1 = jump.toSection;
				for (FaultSection after2 : navigator.getDescendants(after1)) {
					double afterAz = azCalc.calcAzimuth(after1, after2);
					double rawDiff = JumpAzimuthChangeFilter.getAzimuthDifference(beforeAz, afterAz);
					Preconditions.checkState(rawDiff >= -180 && rawDiff <= 180);
					double[] azDiffs;
					if ((float)before2.getAveDip() == 90f) {
						// strike slip, include both directions
						azDiffs = new double[] { rawDiff, -rawDiff };
					} else {
						// follow the aki & richards convention
						double dipDir = before2.getDipDirection();
						double dipDirDiff = JumpAzimuthChangeFilter.getAzimuthDifference(dipDir, beforeAz);
						if (dipDirDiff < 0)
							// this means that the fault dips to the right of beforeAz, we're good
							azDiffs = new double[] { rawDiff };
						else
							// this means that the fault dips to the left of beforeAz, flip it
							azDiffs = new double[] { -rawDiff };
					}
					for (double azDiff : azDiffs)
						ret.get(sourceRake, destRake).add(azDiff);
				}
			}
		}
		return ret;
	}
	
	private static Map<RakeType, List<Double>> getAzimuthsFrom (RakeType sourceRake,
			Table<RakeType, RakeType, List<Double>> azTable) {
		Map<RakeType, List<Double>> azMap;
		if (sourceRake == null) {
			azMap = new HashMap<>();
			for (RakeType type : RakeType.values())
				azMap.put(type, new ArrayList<>());
			for (RakeType source : RakeType.values()) {
				Map<RakeType, List<Double>> row = azTable.row(source);
				for (RakeType dest : row.keySet()) 
					azMap.get(dest).addAll(row.get(dest));
			}
		} else {
			azMap = azTable.row(sourceRake);
		}
		return azMap;
	}
	
	public static File plotJumpAzimuths(RakeType sourceRake, List<RakeType> destRakes,
			Table<RakeType, RakeType, List<Double>> azTable,
			File outputDir, String prefix, String title) throws IOException {
		Map<RakeType, List<Double>> azMap = getAzimuthsFrom(sourceRake, azTable);
		
		Range xRange = new Range(-180d, 180d);
		List<Range> xRanges = new ArrayList<>();
		xRanges.add(xRange);
		
		List<Range> yRanges = new ArrayList<>();
		List<PlotSpec> specs = new ArrayList<>();
		
		for (int i=0; i<destRakes.size(); i++) {
			RakeType destRake = destRakes.get(i);
			
			HistogramFunction hist = HistogramFunction.getEncompassingHistogram(-179d, 179d, 15d);
			for (RakeType oRake : azMap.keySet()) {
				if (destRake != null && destRake != oRake)
					continue;
				for (double azDiff : azMap.get(oRake)) {
					hist.add(hist.getClosestXIndex(azDiff), 1d);
				}
			}

			Color color;
			String label;
			if (destRake == null) {
				color = Color.DARK_GRAY;
				label = "Any";
			} else {
				color = destRake.color;
				label = destRake.name;
			}
			
			List<XY_DataSet> funcs = new ArrayList<>();
			List<PlotCurveCharacterstics> chars = new ArrayList<>();
			
			funcs.add(hist);
			chars.add(new PlotCurveCharacterstics(PlotLineType.HISTOGRAM, 1f, color));
			
			double maxY = Math.max(1.1*hist.getMaxY(), 1d);
			Range yRange = new Range(0d, maxY);
			
			PlotSpec spec = new PlotSpec(funcs, chars, title, "Azimuthal Difference", "Count");
			
			XYTextAnnotation ann = new XYTextAnnotation("To "+label, 175, maxY*0.975);
			ann.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
			ann.setTextAnchor(TextAnchor.TOP_RIGHT);
			spec.addPlotAnnotation(ann);
			
			specs.add(spec);
			yRanges.add(yRange);
		}
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(24);
		gp.setBackgroundColor(Color.WHITE);
		
		gp.drawGraphPanel(specs, false, false, xRanges, yRanges);
		
		File pngFile = new File(outputDir, prefix+".png");
		File txtFile = new File(outputDir, prefix+".txt");
		gp.getChartPanel().setSize(700, 1000);
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsTXT(txtFile.getAbsolutePath());
		return pngFile;
	}
	
	private static double azDiffDegreesToAngleRad(double azDiff) {
		// we want zero to be up, 90 to be right, 180 to be down, -90 to be left
		// sin/cos convention is zero at the right, 90 up, 180 left, -90 down
		
		Preconditions.checkState((float)azDiff >= (float)-180f && (float)azDiff <= 180f,
				"Bad azDiff: %s", azDiff);
		// first mirror it
		azDiff *= -1;
		// now rotate 90 degrees
		azDiff += 90d;
		
		return Math.toRadians(azDiff);
	}
	
	public static File plotJumpAzimuthsRadial(RakeType sourceRake, RakeType destRake,
			Table<RakeType, RakeType, List<Double>> azTable,
			File outputDir, String prefix, String title) throws IOException {
		System.out.println("Plotting "+title);
		Map<RakeType, List<Double>> azMap = getAzimuthsFrom(sourceRake, azTable);
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		Map<Float, List<Color>> azColorMap = new HashMap<>();
		
		HistogramFunction hist = HistogramFunction.getEncompassingHistogram(-179d, 179d, 15d);
		long totCount = 0;
		for (RakeType oRake : azMap.keySet()) {
			if (destRake != null && destRake != oRake)
				continue;
			for (double azDiff : azMap.get(oRake)) {
				hist.add(hist.getClosestXIndex(azDiff), 1d);
				
				Float azFloat = (float)azDiff;
				List<Color> colors = azColorMap.get(azFloat);
				if (colors == null) {
					colors = new ArrayList<>();
					azColorMap.put(azFloat, colors);
				}
				colors.add(oRake.color);
				totCount++;
			}
		}
		
		System.out.println("Have "+azColorMap.size()+" unique azimuths, "+totCount+" total");
//		Random r = new Random(azColorMap.keySet().size());
		double alphaEach = 0.025;
		if (totCount > 0)
			alphaEach = Math.max(alphaEach, 1d/totCount);
		for (Float azFloat : azColorMap.keySet()) {
			double sumRed = 0d;
			double sumGreen = 0d;
			double sumBlue = 0d;
			double sumAlpha = 0;
			int count = 0;
			for (Color azColor : azColorMap.get(azFloat)) {
				sumRed += azColor.getRed();
				sumGreen += azColor.getGreen();
				sumBlue += azColor.getBlue();
				if (sumAlpha < 1d)
					sumAlpha += alphaEach;
				count++;
			}
			double red = sumRed/(double)count;
			double green = sumGreen/(double)count;
			double blue = sumBlue/(double)count;
			if (red > 1d)
				red = 1d;
			if (green > 1d)
				green = 1d;
			if (blue > 1d)
				blue = 1d;
			if (sumAlpha > 1d)
				sumAlpha = 1d;
			Color color = new Color((float)red, (float)green, (float)blue, (float)sumAlpha);
//			if (destRake == null) {
//				// multipe types, choose a random color sampled from the actual colors
//				// for this azimuth
//				List<Color> colorList = azColorMap.get(azFloat);
//				color = colorList.get(r.nextInt(colorList.size()));
//			} else {
//				color = destRake.color;
//			}
			
			DefaultXY_DataSet line = new DefaultXY_DataSet();
			line.set(0d, 0d);
			double azRad = azDiffDegreesToAngleRad(azFloat);
			double x = Math.cos(azRad);
			double y = Math.sin(azRad);
			line.set(x, y);
			
			funcs.add(line);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, color));
		}
		
		double dip;
		if (sourceRake == RakeType.LEFT_LATERAL || sourceRake == RakeType.RIGHT_LATERAL)
			dip = 90d;
		else if (sourceRake == RakeType.NORMAL || sourceRake == RakeType.REVERSE)
			dip = 60d;
		else
			dip = 75d;
		
		double traceLen = 0.5d;
		double lowerDepth = 0.25d;
		if (dip < 90d) {
			// add surface
			
			double horzWidth = lowerDepth/Math.tan(Math.toRadians(dip));
			DefaultXY_DataSet outline = new DefaultXY_DataSet();
			outline.set(0d, 0d);
			outline.set(horzWidth, 0d);
			outline.set(horzWidth, -traceLen);
			outline.set(0d, -traceLen);
			
			funcs.add(outline);
			chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.GRAY));
		}
		
		DefaultXY_DataSet trace = new DefaultXY_DataSet();
		trace.set(0d, 0d);
		trace.set(0d, -traceLen);
		
		funcs.add(trace);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 6f, Color.BLACK));
		PlotSpec spec = new PlotSpec(funcs, chars, title, "", " ");
		
		CPT cpt = GMT_CPT_Files.BLACK_RED_YELLOW_UNIFORM.instance().reverse();
		cpt = cpt.rescale(2d*Float.MIN_VALUE, 0.25d);
		cpt.setBelowMinColor(Color.WHITE);
		double halfDelta = 0.5*hist.getDelta();
		double innerMult = 0.95;
		double outerMult = 1.05;
		double sumY = Math.max(1d, hist.calcSumOfY_Vals());
		for (int i=0; i<hist.size(); i++) {
			double centerAz = hist.getX(i);
			double startAz = azDiffDegreesToAngleRad(centerAz-halfDelta);
			double endAz = azDiffDegreesToAngleRad(centerAz+halfDelta);
			
			List<Point2D> points = new ArrayList<>();
			
			double startX = Math.cos(startAz);
			double startY = Math.sin(startAz);
			double endX = Math.cos(endAz);
			double endY = Math.sin(endAz);
			
			points.add(new Point2D.Double(innerMult*startX, innerMult*startY));
			points.add(new Point2D.Double(outerMult*startX, outerMult*startY));
			points.add(new Point2D.Double(outerMult*endX, outerMult*endY));
			points.add(new Point2D.Double(innerMult*endX, innerMult*endY));
			points.add(new Point2D.Double(innerMult*startX, innerMult*startY));
			
			double[] polygon = new double[points.size()*2];
			int cnt = 0;
			for (Point2D pt : points) {
				polygon[cnt++] = pt.getX();
				polygon[cnt++] = pt.getY();
			}
			Color color = cpt.getColor((float)(hist.getY(i)/sumY));
			
			Stroke stroke = PlotLineType.SOLID.buildStroke(2f);
			spec.addPlotAnnotation(new XYPolygonAnnotation(polygon, stroke, Color.DARK_GRAY, color));
		}
		
		PaintScaleLegend cptBar = XYZGraphPanel.getLegendForCPT(cpt, "Fraction",
				24, 18, 0.05d, RectangleEdge.BOTTOM);
		spec.addSubtitle(cptBar);
		
		Range xRange = new Range(-1.1d, 1.1d);
		Range yRange = new Range(-1.1d, 1.1d);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(22);
		gp.setBackgroundColor(Color.WHITE);
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		
		gp.getXAxis().setTickLabelsVisible(false);
		gp.getYAxis().setTickLabelsVisible(false);
		
		File file = new File(outputDir, prefix+".png");
		gp.getChartPanel().setSize(800, 800);
		gp.saveAsPNG(file.getAbsolutePath());
		return file;
	}

}
