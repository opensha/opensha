package org.opensha.sha.simulators.stiffness;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZGraphPanel;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.simulators.stiffness.AggregatedStiffnessCalculator.AggregationMethod;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.PatchAlignment;
import org.opensha.sha.simulators.stiffness.SubSectStiffnessCalculator.StiffnessType;

import com.google.common.base.Preconditions;

public class CoulombCartoonGenerator {
	
	private static final Location origin = new Location(0d, 0d);
	private static final double half_pi = 0.5*Math.PI;

	public static void main(String[] args) throws IOException {
		File mdDir = new File("/home/kevin/git/misc-research/coulomb_cartoons");
		Preconditions.checkState(mdDir.exists() || mdDir.mkdir());
		File resourcesDir = new File(mdDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		double mainX = 0d;
		double mainY = 0d;
		double mainLen = 10d;
		double mainAz = half_pi;
		double compLen = mainLen;
		double upperDepth = 0d;
		double ddw = 10d;
		
		List<Point2D> compLocs = new ArrayList<>();
		List<String> compLocNames = new ArrayList<>();
		List<String> compLocPrefixes = new ArrayList<>();
		
		compLocs.add(new Point2D.Double(mainLen, 0d));
		compLocNames.add("End-to-End");
		compLocPrefixes.add("ends");
		
		compLocs.add(new Point2D.Double(mainLen+5d, 0d));
		compLocNames.add("Offset X=5km");
		compLocPrefixes.add("offset_x_5km");
		
		compLocs.add(new Point2D.Double(mainLen, 5d));
		compLocNames.add("Offset Y=5km");
		compLocPrefixes.add("offset_y_5km");
		
		compLocs.add(new Point2D.Double(mainLen+Math.sqrt(5d), Math.sqrt(5d)));
		compLocNames.add("Offset X&Y=&Sqrt;5km");
		compLocPrefixes.add("offset_xy_sqrt_5km");
		
		StiffnessType mainType = StiffnessType.CFF;
		StiffnessType[] compTypes = { StiffnessType.TAU, StiffnessType.SIGMA };
		
		double[] compRelAzimuthsDeg = { -150d, -120d, -90d, -60d, -30d, 0d, 30d, 60d, 90d, 120d, 150d };
		
		double gridSpacing = 1d;
		double lambda = 30000;
		double mu = 30000;
		double coeffOfFriction = 0.5;
		double stiffnessCap = 1d;
		PatchAlignment alignment = PatchAlignment.FILL_OVERLAP;
		
////		CPT cpt = SubSectStiffnessCalculator.getPreferredPosNegCPT(100);
//		CPT cpt = new CPT(-200d, 200d, Color.BLUE.darker().darker(), Color.BLUE,
//				Color.WHITE, Color.RED, Color.RED.darker().darker());
//		PaintScaleLegend cptLegend = XYZGraphPanel.getLegendForCPT(cpt, mainType.toString(),
//				22, 18, 10d, RectangleEdge.BOTTOM);
//		PaintScaleLegend[] compLegends = new PaintScaleLegend[compTypes.length];
//		for (int c=0; c<compLegends.length; c++)
//			compLegends[c] = XYZGraphPanel.getLegendForCPT(cpt, compTypes[c].toString(),
//					22, 18, 10d, RectangleEdge.BOTTOM);
		
		double maxX = 0d;
		double maxY = 0d;
		for (Point2D offset : compLocs) {
			maxX = Math.max(maxX, offset.getX() + compLen);
			maxY = Math.max(maxY, offset.getY() + compLen);
		}
		Range xRange = new Range(-1, maxX+1);
		Range yRange = new Range(-maxY, maxY);
		
		List<Double> rakes = new ArrayList<>();
		List<Double> dips = new ArrayList<>();
		List<String> names = new ArrayList<>();
		List<String> prefixes = new ArrayList<>();
		
		rakes.add(180d);
		dips.add(90d);
		names.add("Right-Lateral Strike-Slip");
		prefixes.add("rl_ss");
		
		rakes.add(0d);
		dips.add(90d);
		names.add("Left-Lateral Strike-Slip");
		prefixes.add("ll_ss");
		
		rakes.add(90d);
		dips.add(45d);
		names.add("Reverse");
		prefixes.add("rev");
		
		rakes.add(-90d);
		dips.add(45d);
		names.add("Normal");
		prefixes.add("norm");
		
		List<String> lines = new ArrayList<>();
		lines.add("# Coulomb Cartoons");
		lines.add("");
		
		int summaryIndex = lines.size();
		String topLink = "*[(top)](#summary)*";
		
		List<TableBuilder> summaryTables = new ArrayList<>();
		
		for (int l=0; l<compLocs.size(); l++) {
			TableBuilder summaryTable = MarkdownUtils.tableBuilder();
			summaryTables.add(summaryTable);
			summaryTable.initNewLine().addColumn("Source");
			for (int i=0; i<names.size(); i++)
				summaryTable.addColumn("To "+names.get(i));
			summaryTable.finalizeLine();
			
			String locName = compLocNames.get(l);
			String locPrefix = compLocPrefixes.get(l);
			Point2D compLoc = compLocs.get(l);
			
			lines.add("## "+locName);
			lines.add(topLink); lines.add("");
			
			for (int i=0; i<names.size(); i++) {
				String sourceName = names.get(i);
				String header = "### "+sourceName+", "+locName;
				lines.add(header);
				lines.add(topLink); lines.add("");
				
				FaultSection sourceSect = buildSect(mainX, mainY, mainAz, mainLen,
						upperDepth, ddw, dips.get(i), rakes.get(i));
				sourceSect.setSectionId(0);
				
				summaryTable.initNewLine();
				summaryTable.addColumn("[**"+sourceName+"**](#"+MarkdownUtils.getAnchorName(header)+")");
				
				for (int j=0; j<names.size(); j++) {
					String receiverName = names.get(j);
					lines.add("#### "+sourceName+" -> "+receiverName+", "+locName);
					lines.add(topLink); lines.add("");
					
					List<FaultSection> sectsList = new ArrayList<>();
					sectsList.add(sourceSect);
					List<FaultSection> receivers = new ArrayList<>();
					for (double recAz : compRelAzimuthsDeg) {
						double azimuth = mainAz + Math.toRadians(recAz);
						
						FaultSection receiverSect = buildSect(compLoc.getX(), compLoc.getY(), azimuth, compLen,
								upperDepth, ddw, dips.get(j), rakes.get(j));
						receiverSect.setSectionId(sectsList.size());
						sectsList.add(receiverSect);
						receivers.add(receiverSect);
					}
					
					SubSectStiffnessCalculator stiffCalc = new SubSectStiffnessCalculator(
							sectsList, gridSpacing, lambda, mu, coeffOfFriction, alignment, stiffnessCap);
					AggregatedStiffnessCalculator aggCalc = new AggregatedStiffnessCalculator(
							mainType, stiffCalc, false, AggregationMethod.SUM, AggregationMethod.SUM);
					
					System.out.println("Calculating from "+sourceName+" to "+receiverName);
					
					String title = sourceName+" -> "+receiverName;
					
					List<Double> vals = calc(sourceSect, receivers, aggCalc);
					double maxVal = max(vals);
					double maxAbsVal = maxAbs(vals);
					
					List<List<Double>> compVals = new ArrayList<>();
					List<Double> compMaxVals = new ArrayList<>();
					if (compTypes != null && compTypes.length > 0) {
						for (StiffnessType type : compTypes) {
							AggregatedStiffnessCalculator compCalc = new AggregatedStiffnessCalculator(
									type, stiffCalc, false, AggregationMethod.SUM, AggregationMethod.SUM);
							List<Double> comp = calc(sourceSect, receivers, compCalc);
//							maxAbsVal = Math.max(maxAbsVal, maxAbs(comp));
							compVals.add(comp);
							compMaxVals.add(max(comp));
						}
					}
					
					double cptMax;
					if (maxAbsVal > 175d)
						cptMax = 100d*Math.floor(maxAbsVal/100d);
					else
						cptMax = Math.max(50d, 50d*Math.floor(maxAbsVal/50d));
					CPT cpt = new CPT(-cptMax, cptMax, Color.BLUE.darker().darker(), Color.BLUE,
							Color.WHITE, Color.RED, Color.RED.darker().darker());
					
					String mainPrefix = "src_"+prefixes.get(i)+"_rec_"+prefixes.get(j)+"_"+mainType.name()+"_"+locPrefix;
					
					plot(resourcesDir, cpt, xRange, yRange, title, mainType, sourceSect, receivers, vals, mainPrefix);
					
					summaryTable.addColumn("![plot](resources/"+mainPrefix+".png)");
					lines.add("![plot](resources/"+mainPrefix+".png)");
					lines.add("");
					
					TableBuilder valTable = MarkdownUtils.tableBuilder();
					
					valTable.initNewLine().addColumn("Azimuth Change").addColumn(mainType.getHTML()).addColumn(mainType.getHTML()+"/Max");
					
					if (compTypes != null && compTypes.length > 0) {
						TableBuilder compTable = MarkdownUtils.tableBuilder().initNewLine();
						for (StiffnessType type : compTypes) {
							compTable.addColumn(type.getHTML());
							valTable.addColumn(type.getHTML());
							valTable.addColumn(type.getHTML()+"/Max");
						}
						compTable.finalizeLine();
						compTable.initNewLine();
						for (int c=0; c<compTypes.length; c++) {
							StiffnessType type = compTypes[c];
							String compPrefix = "src_"+prefixes.get(i)+"_rec_"+prefixes.get(j)+"_"+type.name()+"_"+locPrefix;
							plot(resourcesDir, cpt, xRange, yRange, title, type, sourceSect, receivers, compVals.get(c), compPrefix);
							compTable.addColumn("![plot](resources/"+compPrefix+".png)");
						}
						compTable.finalizeLine();
						lines.addAll(compTable.build());
						lines.add("");
					}
					valTable.finalizeLine();
					
					for (int r=0; r<receivers.size(); r++) {
						double val = vals.get(r);
						valTable.initNewLine();
						valTable.addColumn((int)compRelAzimuthsDeg[r]);
						valTable.addColumn((float)val).addColumn((float)(val/maxVal));
						for (int c=0; c<compTypes.length; c++) {
							double compVal = compVals.get(c).get(r);
							valTable.addColumn((float)compVal).addColumn((float)(val/compMaxVals.get(c)));
						}
						valTable.finalizeLine();
					}
					lines.addAll(valTable.build());
					lines.add("");
				}
				summaryTable.finalizeLine();
			}
		}
		
		List<String> summaryLines = new ArrayList<>();
		summaryLines.add("## Summary");
		for (int l=0; l<compLocNames.size(); l++) {
			summaryLines.add("");
			summaryLines.add("**"+compLocNames.get(l)+"**");
			summaryLines.add("");
			summaryLines.addAll(summaryTables.get(l).build());
		}
		summaryLines.add("");
		lines.addAll(summaryIndex, summaryLines);

		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, mdDir);
	}
	
	private static double max(List<Double> values) {
		double max = Double.NEGATIVE_INFINITY;
		for (double val : values)
			max = Math.max(val, max);
		return max;
	}
	
	private static double maxAbs(List<Double> values) {
		double max = 0d;
		for (double val : values)
			max = Math.max(Math.abs(val), max);
		return max;
	}
	
	private static List<Double> calc(FaultSection sourceSect, List<FaultSection> receivers, AggregatedStiffnessCalculator aggCalc) {
		List<Double> vals = new ArrayList<>();
		for (int r=0; r<receivers.size(); r++) {
			FaultSection receiver = receivers.get(r);
			double val = aggCalc.calc(sourceSect, receiver);
			vals.add(val);
		}
		return vals;
	}

	private static List<Double> plot(File resourcesDir, CPT cpt, Range xRange, Range yRange, String title,
			StiffnessType type, FaultSection sourceSect, List<FaultSection> receivers, List<Double> vals,
			String mainPrefix) throws IOException {
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		plot(sourceSect, Color.GREEN.darker(), funcs, chars);
		for (int r=0; r<receivers.size(); r++) {
			FaultSection receiver = receivers.get(r);
			double val = vals.get(r);
			vals.add(val);
			Color color = cpt.getColor((float)val);
			plot(receiver, color, funcs, chars);
		}
		
		double cptDelta;
		if (cpt.getMaxValue() >= 200f)
			cptDelta = 50d;
		else if (cpt.getMaxValue() >= 100f)
			cptDelta = 25d;
		else if (cpt.getMaxValue() >= 20f)
			cptDelta = 10d;
		else if (cpt.getMaxValue() >= 10f)
			cptDelta = 5d;
		else
			cptDelta = 2d;
		PaintScaleLegend cptLegend = XYZGraphPanel.getLegendForCPT(cpt, type.toString(),
				22, 18, cptDelta, RectangleEdge.BOTTOM);
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, "X (km)", "Y (km)");
		spec.addSubtitle(cptLegend);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setBackgroundColor(Color.WHITE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(20);
		gp.setPlotLabelFontSize(21);
		gp.setLegendFontSize(22);
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		gp.getChartPanel().setSize(1000, 1000);
		
		File pngFile = new File(resourcesDir, mainPrefix+".png");
		File pdfFile = new File(resourcesDir, mainPrefix+".pdf");
		gp.saveAsPNG(pngFile.getAbsolutePath());
		gp.saveAsPDF(pdfFile.getAbsolutePath());
		return vals;
	}
	
	private static Location loc(double x, double y) {
		Location ret = origin;
		if (x != 0d)
			ret = LocationUtils.location(ret, half_pi, x);
		if (y != 0d)
			ret = LocationUtils.location(ret, 0d, y);
		return ret;
	}
	
	private static Point2D pt(Location loc) {
		double x;
		if (loc.getLongitude() == origin.getLongitude()) {
			x = 0d;
		} else {
			x = LocationUtils.horzDistanceFast(origin, new Location(origin.getLatitude(), loc.getLongitude()));
			if (loc.getLongitude() < origin.getLongitude())
				x = -x;
		}
		double y;
		if (loc.getLatitude() == origin.getLatitude()) {
			y = 0d;
		} else {
			y = LocationUtils.horzDistanceFast(origin, new Location(loc.getLatitude(), origin.getLongitude()));
			if (loc.getLatitude() < origin.getLatitude())
				y = -y;
		}
		return new Point2D.Double(x, y);
	}
	
	private static FaultSection buildSect(double x, double y, double azimuth, double len,
			double upperDepth, double ddw, double dip, double rake) {
		Location startLoc = loc(x, y);
		Location endLoc = LocationUtils.location(startLoc, azimuth, len);
		FaultTrace trace = new FaultTrace(null);
		trace.add(startLoc);
		trace.add(endLoc);
		FaultSectionPrefData sect = new FaultSectionPrefData();
		sect.setAveRake(rake);
		sect.setFaultTrace(trace);
		sect.setAveDip(dip);
		sect.setAveUpperDepth(upperDepth);
		double lowerDepth = Math.sin(Math.toRadians(dip))*ddw;
		sect.setAveLowerDepth(lowerDepth);
		sect.setDipDirection((float)(trace.getAveStrike() + 90d));
		return sect;
	}
	
	private static void plot(FaultSection sect, Color color, List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars) {
		FaultTrace trace = sect.getFaultTrace();
		LocationVector traceHalfVect = LocationUtils.vector(trace.first(), trace.last());
		traceHalfVect.setHorzDistance(0.5*traceHalfVect.getHorzDistance());
		Location center = LocationUtils.location(trace.first(), traceHalfVect);
		PlotCurveCharacterstics arrowChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK);
		double arrowLen = 0.1*trace.getTraceLength();
		if (sect.getAveDip() != 90d) {
			RuptureSurface surf = sect.getFaultSurface(1d, false, false);
			LocationList perim = surf.getPerimeter();
			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			for (int i=0; i<=perim.size(); i++)
				xy.set(pt(perim.get(i % perim.size())));
			funcs.add(0, xy);
			chars.add(0, new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY));
			
//			double offset = 0.05*trace.getTraceLength();
			double hwOffset, fwOffset;
			if (sect.getAveRake() > 0) {
				// up dip
				hwOffset = 0.75*arrowLen;
				fwOffset = 0.75*arrowLen;
			} else {
				// down dip
				hwOffset = 0.25*arrowLen;
				fwOffset = 0.25*arrowLen;
			}

			LocationVector hwSlip = calcSlipVector(sect, false);
			Location hwCenter = LocationUtils.location(center, new LocationVector(trace.getAveStrike()+90d, hwOffset, 0d));
			hwSlip.setHorzDistance(hwSlip.getHorzDistance()*arrowLen);
			Location hwTo = LocationUtils.location(hwCenter, hwSlip);
			funcs.add(arrow(hwCenter, hwTo, arrowLen*0.4));
			chars.add(arrowChar);

			Location fwCenter = LocationUtils.location(center, new LocationVector(trace.getAveStrike()-90d, fwOffset, 0d));
			LocationVector fwSlip = calcSlipVector(sect, true);
			fwSlip.setHorzDistance(fwSlip.getHorzDistance()*arrowLen);
			Location fwTo = LocationUtils.location(fwCenter, fwSlip);
			funcs.add(arrow(fwCenter, fwTo, arrowLen*0.4));
			chars.add(arrowChar);
		} else {
			// vertical
			double offset = 0.5*arrowLen;

			Location hwCenter = LocationUtils.location(center, new LocationVector(trace.getAveStrike()+90d, offset, 0d));
			LocationVector hwSlip = calcSlipVector(sect, false);
			hwSlip.setHorzDistance(arrowLen);
			Location hwTo = LocationUtils.location(hwCenter, hwSlip);
			funcs.add(arrow(hwCenter, hwTo, arrowLen*0.4));
			chars.add(arrowChar);

			Location fwCenter = LocationUtils.location(center, new LocationVector(trace.getAveStrike()-90d, offset, 0d));
			LocationVector fwSlip = calcSlipVector(sect, true);
			fwSlip.setHorzDistance(arrowLen);
			Location fwTo = LocationUtils.location(fwCenter, fwSlip);
			funcs.add(arrow(fwCenter, fwTo, arrowLen*0.4));
			chars.add(arrowChar);
		}
		
		DefaultXY_DataSet xy = new DefaultXY_DataSet();
		for (Location loc : trace)
			xy.set(pt(loc));
		funcs.add(xy);
		chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, color));
	}
	
	private static LocationVector calcSlipVector(FaultSection sect, boolean footwall) {
		double strike = sect.getFaultTrace().getAveStrike();
		double rake = sect.getAveRake();
		double dip = sect.getAveDip();
		
		if (footwall)
			// we want the footwall motion
			rake = FaultUtils.getInRakeRange(rake + 180d);
		// rakes are weird. +90 is up, -90 is down. subtract from azimuth
		double azimuth = strike - rake;
		if ((float)rake == -180f || (float)rake == 0f || (float)rake == 180f) {
			// strike-slip motion, don't need to account for dip
			return new LocationVector(azimuth, 1d, 0d);
		}
		// at least some dip-slip motion
		
		// this is the component of slip that is in the down-dip dir
		// it will be positive if rake ~ +90 (down-dip) and negative if rake ~ -90 (up-dip)
		double fractInDipDir = Math.sin(Math.toRadians(rake));
//		System.out.println("fractInDipDir: "+fractInDipDir);
		// sin(dip) = vertical/fractInDipDir
		double vertical = fractInDipDir*Math.sin(Math.toRadians(dip));
		// vert^2 + horiz^2 = 1^2
		double horizontal = Math.sqrt(1 - vertical*vertical);
		return new LocationVector(azimuth, horizontal, vertical);
	}

	private static XY_DataSet arrow(Location from, Location to, double length) {
		DefaultXY_DataSet xy = new DefaultXY_DataSet();
		
		xy.set(pt(from));
		xy.set(pt(to));
		
		double arrowAz = LocationUtils.azimuth(from, to);
		
		double az1 = arrowAz + 135;
		double az2 = arrowAz - 135;
		
		Location arrow1 = LocationUtils.location(to, Math.toRadians(az1), length);
		Location arrow2 = LocationUtils.location(to, Math.toRadians(az2), length);

		xy.set(pt(arrow1));
		xy.set(pt(to));
		xy.set(pt(from));
		xy.set(pt(to));
		xy.set(pt(arrow2));
		xy.set(pt(to));
		
		return xy;
	}
}
