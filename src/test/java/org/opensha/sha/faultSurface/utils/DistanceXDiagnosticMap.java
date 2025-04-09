package org.opensha.sha.faultSurface.utils;

import static org.opensha.commons.geo.GeoTools.EARTH_RADIUS_MEAN;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.GeoTools;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.faultSurface.FaultTrace;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import net.mahdilamb.colormap.Colors;

public class DistanceXDiagnosticMap {
	
	private enum TraceType {
		RANDOM,
		STAIRSTEP,
		SINGLE
	}

	public static void main(String[] args) throws IOException {
		double[] strikes = {0d, 30d, 45d, 60d, 90d, 135, 225, 270};
//		double[] strikes = {270};
		TraceType[] types = TraceType.values();
//		TraceType[] types = { TraceType.SINGLE };
		double[] lats = {0d, 30d, 60d};
		int numSegs = 10;
		double maxDeviation = 60d;
		File outputDir = new File("/tmp/dist_x_tests");
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
//		boolean useOld = false;
//		String title = "Updated DistanceX Calculation Example";
		
		boolean useOld = true;
		String title = "Previous DistanceX Calculation Example";
		
		DecimalFormat oDF = new DecimalFormat("0.##");
		
		double refLon = 30;
//		double zScale = 0.15;
		double zScale = 1d;
		
//		double tick = 0.5;
//		double bufferMult = 3;
//		double gridSpacing = 0.02;
		
		double tick = 0.5;
		double bufferMult = 1;
		double gridSpacing = 0.005;
		
		int overallCals = 0;
		double overallSecs = 0d;
		
		for (double strike : strikes) {
			for (TraceType type : types) {
				List<Double> segDists = new ArrayList<>(numSegs);
				List<Double> segAzimuths = new ArrayList<>(numSegs);
				
				String strikePrefix = "dist_x_test_strike"+oDF.format(strike);
				if (useOld)
					strikePrefix = "old_"+strikePrefix;
				if (type == TraceType.STAIRSTEP) {
					strikePrefix += "_stairstep";
					for (int i=0; i<numSegs; i++) {
						segDists.add(20d);
						segAzimuths.add(i % 2 == 0 ? strike-45d : strike+45d);
					}
				} else if (type == TraceType.SINGLE) {
					strikePrefix += "_single";
					segDists.add(100d);
					segAzimuths.add(strike);
				} else {
					strikePrefix += "_random";
					long seed = Double.doubleToLongBits(strike+1d);
					// java rand only uses the lower 48 bits, which might be identical
					// this scrambles them
					seed ^= (seed >>> 32);
					System.out.println("Seed for "+strike+": "+seed);
					Random rand = new Random(seed);
					System.out.println("\tFirst rand: "+rand.nextDouble());
					Location startLoc = new Location(0d, 0d);
					Location runningLoc = startLoc;
					for (int i=0; i<numSegs-1; i++) {
						double dist = 1d+rand.nextDouble()*50d;
						double az = strike + (rand.nextDouble()-0.5)*2d*maxDeviation;
						segDists.add(dist);
						segAzimuths.add(az);
						runningLoc = LocationUtils.location(runningLoc, Math.toRadians(az), dist);
					}
					// now now make it follow the given strike in total
					double distToRunning = LocationUtils.horzDistanceFast(startLoc, runningLoc);
					double distToLast = distToRunning + 1d + rand.nextDouble()*50d;
					Location finalLoc = LocationUtils.location(startLoc, Math.toRadians(strike), distToLast);
					segDists.add(LocationUtils.horzDistanceFast(runningLoc, finalLoc));
					segAzimuths.add(LocationUtils.azimuth(runningLoc, finalLoc));
				}
				
				for (double lat : lats) {
					Location startLoc = new Location(lat, refLon);
					
					MinMaxAveTracker latTrack = new MinMaxAveTracker();
					MinMaxAveTracker lonTrack = new MinMaxAveTracker();
					
					FaultTrace trace = new FaultTrace(null);
					trace.add(startLoc);
					latTrack.addValue(startLoc.getLatitude());
					lonTrack.addValue(startLoc.getLongitude());
					for (int i=0; i<segAzimuths.size(); i++) {
						Location loc = LocationUtils.location(trace.last(), Math.toRadians(segAzimuths.get(i)), segDists.get(i));
						trace.add(loc);
						latTrack.addValue(loc.lat);
						lonTrack.addValue(loc.lon);
					}
					
					double traceDist = LocationUtils.horzDistanceFast(trace.first(), trace.last());
					double buffer = traceDist*bufferMult;
					
					Location middleLoc = new Location(latTrack.getCenter(), lonTrack.getCenter());
					Location leftLoc = LocationUtils.location(middleLoc, 3d*Math.PI/2d, buffer);
					Location rightLoc = LocationUtils.location(middleLoc, Math.PI/2d, buffer);
					Location upLoc = LocationUtils.location(middleLoc, 0d, buffer);
					Location downLoc = LocationUtils.location(middleLoc, Math.PI, buffer);

					Location lowerLeft = new Location(downLoc.getLatitude(), leftLoc.getLongitude());
					Location upperRight = new Location(upLoc.getLatitude(), rightLoc.getLongitude());
					
					Region reg = new Region(lowerLeft, upperRight);
					GriddedRegion gridReg = new GriddedRegion(reg, gridSpacing, GriddedRegion.ANCHOR_0_0);
					GriddedGeoDataSet xyz = new GriddedGeoDataSet(gridReg);
					
					System.out.println("Calculating rX for strike="+(float)strike+", "+type+", for "+xyz.size()+" locations");
					System.out.println("Trace: "+trace);
					Stopwatch watch = Stopwatch.createStarted();
					for (int i=0; i<gridReg.getNodeCount(); i++) {
						double rx;
						if (useOld)
							rx = GriddedSurfaceUtils.getDistanceX_old(trace, gridReg.getLocation(i));
						else
							rx = GriddedSurfaceUtils.getDistanceX(trace, gridReg.getLocation(i));
						xyz.set(i, rx);
					}
					watch.stop();
					double secs = watch.elapsed().toMillis()/1000d;
					double calcsPerSec = xyz.size()/secs;
					overallCals += xyz.size();
					overallSecs += secs;
					System.out.println("Calculated "+xyz.size()+" rX values in "+(float)secs+" seconds ("+(float)calcsPerSec+" calcs/sec)");
					
					double maxZ = Math.max(xyz.getMaxZ(), -xyz.getMinZ());
					maxZ *= zScale;
					CPT upperCPT = GMT_CPT_Files.SEQUENTIAL_LAJOLLA_UNIFORM.instance().rescale(0d, 1d).trim(0.15, 1d).rescale(0d, maxZ);
					CPT lowerCPT = GMT_CPT_Files.SEQUENTIAL_NAVIA_UNIFORM.instance().rescale(0d, 1d).trim(0.05, 1d).reverse().rescale(-maxZ, 0);
					CPT combCPT = new CPT();
					combCPT.addAll(lowerCPT);
					combCPT.addAll(upperCPT);
					combCPT.setBelowMinColor(lowerCPT.getMinColor());
					combCPT.setAboveMaxColor(upperCPT.getMaxColor());
					
					XY_DataSet traceXY = new DefaultXY_DataSet();
					traceXY.setName("Trace");
					for (Location loc : trace)
						traceXY.set(loc.lon, loc.lat);
					
					double az = trace.getStrikeDirection();
					XY_DataSet startXY = new DefaultXY_DataSet();
					startXY.setName("Trace Start");
					startXY.set(trace.first().lon, trace.first().lat);
					XY_DataSet endXY = new DefaultXY_DataSet();
					endXY.setName("Trace End");
					endXY.set(trace.last().lon, trace.last().lat);
					XY_DataSet extendBeforeXY = extendLine(trace.first(), az+180, reg);
					extendBeforeXY.setName("Great Circle Extension");
					XY_DataSet extendAfterXY = extendLine(trace.last(), az, reg);
					List<XY_DataSet> funcs = List.of(traceXY, extendBeforeXY, extendAfterXY, startXY, endXY);
					List<PlotCurveCharacterstics> chars = List.of(
							new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GRAY),
							new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.DARK_GRAY),
							new PlotCurveCharacterstics(PlotLineType.DASHED, 2f, Color.DARK_GRAY),
							new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 3f, Colors.tab_red),
							new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 3f, Colors.tab_green));
					
					XYZPlotSpec plot = new XYZPlotSpec(xyz, funcs, chars, combCPT,
							title, "Longitude", "Latitude", "DistanceX");
					plot.setIncludeZlabelInLegend(false);
					plot.setLegendInset(RectangleAnchor.TOP_LEFT);
					
					HeadlessGraphPanel gp = PlotUtils.initHeadless();
					
					gp.drawGraphPanel(plot, false, false, new Range(reg.getMinLon(), reg.getMaxLon()),
							new Range(reg.getMinLat(), reg.getMaxLat()));
					
					PlotUtils.setYTick(gp, tick);
					PlotUtils.setXTick(gp, tick);
					
					String prefix = strikePrefix+"_lat"+oDF.format(lat);
					PlotUtils.writePlots(outputDir, prefix,
							gp, 1000, true, true, false, false);
				}
			}
		}
		
		double calcsPerSec = overallCals/overallSecs;
		System.out.println("Calculated "+overallCals+" total rX values in "+(float)overallSecs+" seconds ("+(float)calcsPerSec+" calcs/sec)");
	}
	
	private static XY_DataSet extendLine(Location from, double az, Region reg) {
		XY_DataSet line = new DefaultXY_DataSet();
		double deltaDist = 5d;
		double azRad = Math.toRadians(az);
		
		line.set(from.lon, from.lat);
		
		double dist = deltaDist;
		while (true) {
			Location loc = LocationUtils.location(from, azRad, dist);
			line.set(loc.lon, loc.lat);
			
			if (!reg.contains(loc))
				break;
			
			dist += deltaDist;
		}
		return line;
	}

}
