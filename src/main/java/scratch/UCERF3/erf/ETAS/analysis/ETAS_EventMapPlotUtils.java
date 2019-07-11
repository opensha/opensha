package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.opensha.commons.data.function.ArbitrarilyDiscretizedFunc;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.SimpleFaultData;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

/**
 * Utility methods used by multiple plots for maps
 * @author kevin
 *
 */
class ETAS_EventMapPlotUtils {
	
	static Region getMapRegion(ETAS_Config config, ETAS_Launcher launcher) {
		Region mapRegion = config.getComcatRegion();
		List<ETAS_EqkRupture> triggerRups = launcher.getTriggerRuptures();
		if (mapRegion == null && triggerRups != null && !triggerRups.isEmpty()) {
			MinMaxAveTracker latTrack = new MinMaxAveTracker();
			MinMaxAveTracker lonTrack = new MinMaxAveTracker();
			for (ETAS_EqkRupture rup : triggerRups) {
				for (Location loc : rup.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface()) {
					latTrack.addValue(loc.getLatitude());
					lonTrack.addValue(loc.getLongitude());
				}
			}
			double maxSpan = Math.max(latTrack.getMax()-latTrack.getMin(), lonTrack.getMax()-lonTrack.getMin());
			double centerLat = 0.5*(latTrack.getMax() + latTrack.getMin());
			double centerLon = 0.5*(lonTrack.getMax() + lonTrack.getMin());
			System.out.println("Lon range: "+lonTrack.getMin()+" "+lonTrack.getMax());
			System.out.println("Lat range: "+latTrack.getMin()+" "+latTrack.getMax());
			System.out.println("Center: "+centerLat+", "+centerLon);
			System.out.println("Span: "+maxSpan);
			double halfSpan = maxSpan*0.5;
			Location topRight = new Location(centerLat + halfSpan, centerLon + halfSpan);
			Location bottomLeft = new Location(centerLat - halfSpan, centerLon - halfSpan);
			// now buffer by 20km in each direction
			topRight = LocationUtils.location(topRight, new LocationVector(45, 20, 0d));
			bottomLeft = LocationUtils.location(bottomLeft, new LocationVector(225, 20, 0d));
			mapRegion = new Region(topRight, bottomLeft);
		}
		return mapRegion;
	}
	
	static final float rup_surface_thickness = 3f;
	
	static void buildEventPlot(List<ETAS_EqkRupture> events, List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars)
			throws IOException {
		if (events.isEmpty())
			return;
		
		MinMaxAveTracker magTrack = new MinMaxAveTracker();
		for (ETAS_EqkRupture rup : events)
			magTrack.addValue(rup.getMag());
		
		// add trigger ruptures
		PlotCurveCharacterstics singleQuakeChar = null;
		List<Range> magRanges = null;
		List<PlotCurveCharacterstics> magChars = null;
		List<XY_DataSet> magXYs = null;

		CPT magCPT = GMT_CPT_Files.GMT_DRYWET.instance().reverse();
		DiscretizedFunc magSizeFunc = new ArbitrarilyDiscretizedFunc();
		magSizeFunc.set(2d, 2d);
		while (magSizeFunc.getMaxX() < 8d)
			magSizeFunc.set(magSizeFunc.getMaxX()+1, magSizeFunc.getMaxY()*1.5);

		if (events.size() > 1) {
			double maxMag = magTrack.getMax();
			double minFilledMag = maxMag > 7 ? 6d : 5d;

			magRanges = new ArrayList<>();
			magChars = new ArrayList<>();
			magXYs = new ArrayList<>();

			double magCeil = Math.ceil(maxMag);
			double magFloor = Math.floor(magTrack.getMin());

			if (magCeil == maxMag || magFloor == magCeil)
				magCeil += 1;

			if (magCeil == magFloor+1)
				magCPT = magCPT.rescale(magFloor-1, magFloor);
			else
				magCPT = magCPT.rescale(magFloor, magCeil-1);

			for (double minMag=magFloor; (float)minMag<(float)magCeil; minMag++) {
				double myMaxMag = minMag+1;
				magRanges.add(new Range(minMag, myMaxMag));
				PlotSymbol symbol = (float)minMag >= (float)minFilledMag ? PlotSymbol.FILLED_CIRCLE : PlotSymbol.CIRCLE;
				double symbolWidth;
				if (minMag < magSizeFunc.getMinX())
					symbolWidth = magSizeFunc.getMinY();
				else if (minMag > magSizeFunc.getMaxX())
					symbolWidth = magSizeFunc.getMaxY();
				else
					symbolWidth = magSizeFunc.getInterpolatedY(minMag);
				Color color = magCPT.getColor((float)minMag);
				PlotCurveCharacterstics plotChar = new PlotCurveCharacterstics(symbol, (float)symbolWidth, color);
				magChars.add(plotChar);
				DefaultXY_DataSet xy = new DefaultXY_DataSet();
				xy.setName("M"+(int)minMag);
				magXYs.add(xy);
			}
		} else {
			double mag = events.get(0).getMag();
			double symbolWidth;
			if (mag < magSizeFunc.getMinX())
				symbolWidth = magSizeFunc.getMinY();
			else if (mag > magSizeFunc.getMaxX())
				symbolWidth = magSizeFunc.getMaxY();
			else
				symbolWidth = magSizeFunc.getInterpolatedY(mag);
			singleQuakeChar = new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, (float)symbolWidth, magCPT.getMaxColor());
		}
		for (ETAS_EqkRupture event : events) {
			double mag = event.getMag();
			XY_DataSet ptXY;
			PlotCurveCharacterstics plotChar;
			if (singleQuakeChar != null) {
				ptXY = new DefaultXY_DataSet();
				ptXY.setName("M"+(float)mag);
				plotChar = singleQuakeChar;
				funcs.add(ptXY);
				chars.add(plotChar);
			} else {
				ptXY = null;
				plotChar = null;
				for (int i=0; i<magRanges.size(); i++) {
					Range magRange = magRanges.get(i);
					if (mag >= magRange.getLowerBound() && mag < magRange.getUpperBound()) {
						ptXY = magXYs.get(i);
						plotChar = magChars.get(i);
					}
				}
				Preconditions.checkNotNull(ptXY);
			}
			RuptureSurface surf = event.getRuptureSurface();
			if (!surf.isPointSurface()) {
				List<XY_DataSet> surfXYs = getSurfOutlines(surf);
				PlotCurveCharacterstics surfChar = new PlotCurveCharacterstics(
						PlotLineType.DOTTED, rup_surface_thickness-1f, plotChar.getColor());
				for (XY_DataSet surfXY : surfXYs) {
					funcs.add(surfXY);
					chars.add(surfChar);
				}
				surfXYs = getSurfTraces(surf);
				surfChar = new PlotCurveCharacterstics(
						PlotLineType.SOLID, rup_surface_thickness, plotChar.getColor());
				for (XY_DataSet surfXY : surfXYs) {
					funcs.add(surfXY);
					chars.add(surfChar);
				}
			}
			Location hypo = event.getHypocenterLocation();
			ptXY.set(hypo.getLongitude(), hypo.getLatitude());
		}

		for (int i=0; magXYs != null && i<magXYs.size(); i++) {
			XY_DataSet xy = magXYs.get(i);
			if (xy.size() > 0) {
				funcs.add(xy);
				chars.add(magChars.get(i));
			}
		}
	}
	
	static List<XY_DataSet> getSurfTraces(RuptureSurface surf) {
		List<RuptureSurface> allSurfs = new ArrayList<>();
		if (surf instanceof CompoundSurface)
			allSurfs.addAll(((CompoundSurface)surf).getSurfaceList());
		else
			allSurfs.add(surf);
		List<XY_DataSet> ret = new ArrayList<>();
		for (RuptureSurface subSurf : allSurfs) {
			XY_DataSet surfXY = new DefaultXY_DataSet();
			for (Location loc : subSurf.getUpperEdge())
				surfXY.set(loc.getLongitude(), loc.getLatitude());
			ret.add(surfXY);
		}
		return ret;
	}
	
	static List<XY_DataSet> getSurfOutlines(RuptureSurface surf) {
		double dip = surf.getAveDip();
		List<RuptureSurface> allSurfs = new ArrayList<>();
		if (surf instanceof CompoundSurface)
			allSurfs.addAll(((CompoundSurface)surf).getSurfaceList());
		else
			allSurfs.add(surf);
		List<XY_DataSet> ret = new ArrayList<>();
		for (RuptureSurface subSurf : allSurfs) {
			XY_DataSet surfXY = new DefaultXY_DataSet();
			if (dip == 90d) {
				for (Location loc : subSurf.getUpperEdge())
					surfXY.set(loc.getLongitude(), loc.getLatitude());
			} else {
				for (Location loc : subSurf.getPerimeter())
					surfXY.set(loc.getLongitude(), loc.getLatitude());
				surfXY.set(surfXY.get(0));
			}
			ret.add(surfXY);
		}
		return ret;
	}
	
	private static double traceDist(FaultTrace trace, Location loc) {
		return LocationUtils.distanceToLine(trace.first(), trace.last(), loc);
	}
	
	static void writeMapPlot(List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars, Region mapRegion, String title,
			File outputDir, String prefix) throws IOException {
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Longitude", "Latitude");
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = ETAS_AbstractPlot.buildGraphPanel();
		double latSpan = mapRegion.getMaxLat() - mapRegion.getMinLat();
		double lonSpan = mapRegion.getMaxLon() - mapRegion.getMinLon();
		gp.setUserBounds(new Range(mapRegion.getMinLon(), mapRegion.getMaxLon()),
				new Range(mapRegion.getMinLat(), mapRegion.getMaxLat()));

		gp.drawGraphPanel(spec, false, false);
		
		TickUnits tus = new TickUnits();
		TickUnit tu;
		if (lonSpan > 5)
			tu = new NumberTickUnit(1d);
		else if (lonSpan > 2)
			tu = new NumberTickUnit(0.5);
		else if (lonSpan > 1)
			tu = new NumberTickUnit(0.25);
		else
			tu = new NumberTickUnit(0.1);
		tus.add(tu);
		
		XYPlot plot = gp.getPlot();
		plot.getRangeAxis().setStandardTickUnits(tus);
		plot.getDomainAxis().setStandardTickUnits(tus);
		gp.getChartPanel().setSize(1000, (int)(1000d*latSpan/lonSpan));
		
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
	}
	
	static void buildEventDepthPlot(List<ETAS_EqkRupture> events, List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars,
			SimpleFaultData... sfds) throws IOException {
		if (events.isEmpty() || sfds.length == 0)
			return;
		
		FaultTrace trace;
		if (sfds.length > 1) {
			trace = new FaultTrace(null);
			trace.add(sfds[0].getFaultTrace().first());
			trace.add(sfds[sfds.length-1].getFaultTrace().last());
		} else {
			trace = sfds[0].getFaultTrace();
		}
		
		// draw rupture
		PlotCurveCharacterstics sfdBelowChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK);
		PlotCurveCharacterstics sfdAboveChar = new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, Color.BLACK);
		List<XY_DataSet> sfdFuncs = new ArrayList<>();
		for (SimpleFaultData sfd : sfds) {
			for (int i=1; i<trace.size(); i++) {
				FaultTrace subTrace = new FaultTrace(null);
				subTrace.add(trace.get(i-1));
				subTrace.add(trace.get(i));
				SimpleFaultData subSFD = new SimpleFaultData(sfd.getAveDip(), sfd.getLowerSeismogenicDepth(),
						sfd.getUpperSeismogenicDepth(), subTrace, sfd.getAveDipDir());
				EvenlyGriddedSurface subSurf = new StirlingGriddedSurface(subSFD, 0.1d, 0.1d);
				
				XY_DataSet xy = new DefaultXY_DataSet();
				if (i == 1 && sfd == sfds[0])
					xy.setName("Simple Fault Surface");
				Location topLeft = subSurf.get(0, 0);
				Location botLeft = subSurf.get(subSurf.getNumRows()-1, 0);
				Location topRight = subSurf.get(0, subSurf.getNumCols()-1);
				Location botRight = subSurf.get(subSurf.getNumRows()-1, subSurf.getNumCols()-1);
				xy.set(traceDist(trace, topLeft), topLeft.getDepth());
				xy.set(traceDist(trace, topRight), topRight.getDepth());
				xy.set(traceDist(trace, botLeft), botLeft.getDepth());
				xy.set(traceDist(trace, botRight), botRight.getDepth());
				xy.set(traceDist(trace, topLeft), topLeft.getDepth());
				sfdFuncs.add(xy);
				if (xy.getName() != null) {
					xy = xy.deepClone();
					xy.setName(null);
				}
				funcs.add(xy);
				chars.add(sfdBelowChar);
			}
		}
		
		MinMaxAveTracker magTrack = new MinMaxAveTracker();
		for (ETAS_EqkRupture rup : events)
			magTrack.addValue(rup.getMag());
		
		// add trigger ruptures
		List<Range> magRanges = null;
		List<PlotCurveCharacterstics> magChars = null;
		List<XY_DataSet> magXYs = null;

		CPT magCPT = GMT_CPT_Files.GMT_DRYWET.instance().reverse();
		DiscretizedFunc magSizeFunc = new ArbitrarilyDiscretizedFunc();
		magSizeFunc.set(2d, 2d);
		while (magSizeFunc.getMaxX() < 8d)
			magSizeFunc.set(magSizeFunc.getMaxX()+1, magSizeFunc.getMaxY()*1.5);

		double maxMag = magTrack.getMax();
		double minFilledMag = maxMag > 7 ? 6d : 5d;

		magRanges = new ArrayList<>();
		magChars = new ArrayList<>();
		magXYs = new ArrayList<>();

		double magCeil = Math.ceil(maxMag);
		double magFloor = Math.floor(magTrack.getMin());

		if (magCeil == maxMag || magFloor == magCeil)
			magCeil += 1;

		if (magCeil == magFloor+1)
			magCPT = magCPT.rescale(magFloor-1, magFloor);
		else
			magCPT = magCPT.rescale(magFloor, magCeil-1);

		for (double minMag=magFloor; (float)minMag<(float)magCeil; minMag++) {
			double myMaxMag = minMag+1;
			magRanges.add(new Range(minMag, myMaxMag));
			PlotSymbol symbol = (float)minMag >= (float)minFilledMag ? PlotSymbol.FILLED_CIRCLE : PlotSymbol.CIRCLE;
			double symbolWidth;
			if (minMag < magSizeFunc.getMinX())
				symbolWidth = magSizeFunc.getMinY();
			else if (minMag > magSizeFunc.getMaxX())
				symbolWidth = magSizeFunc.getMaxY();
			else
				symbolWidth = magSizeFunc.getInterpolatedY(minMag);
			Color color = magCPT.getColor((float)minMag);
			PlotCurveCharacterstics plotChar = new PlotCurveCharacterstics(symbol, (float)symbolWidth, color);
			magChars.add(plotChar);
			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			xy.setName("M"+(int)minMag);
			magXYs.add(xy);
		}
		
		for (ETAS_EqkRupture event : events) {
			double mag = event.getMag();
			XY_DataSet ptXY = null;
			for (int i=0; i<magRanges.size(); i++) {
				Range magRange = magRanges.get(i);
				if (mag >= magRange.getLowerBound() && mag < magRange.getUpperBound())
					ptXY = magXYs.get(i);
			}
			Preconditions.checkNotNull(ptXY);
			Location hypo = event.getHypocenterLocation();
			ptXY.set(traceDist(trace, hypo), hypo.getDepth());
		}

		for (int i=0; magXYs != null && i<magXYs.size(); i++) {
			XY_DataSet xy = magXYs.get(i);
			if (xy.size() > 0) {
				funcs.add(xy);
				chars.add(magChars.get(i));
			}
		}
		
		for (XY_DataSet xy : sfdFuncs) {
			funcs.add(xy);
			chars.add(sfdAboveChar);
		}
	}
	
	static void writeDepthPlot(List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars, String title,
			File outputDir, String prefix) throws IOException {
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Fault Normal Distance (km)", "Depth (km)");
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = ETAS_AbstractPlot.buildGraphPanel();
		MinMaxAveTracker depthTrack = new MinMaxAveTracker();
//		MinMaxAveTracker normDistTrack = new MinMaxAveTracker();
		for (XY_DataSet func : funcs) {
//			normDistTrack.addValue(func.getMinX());
//			normDistTrack.addValue(func.getMaxX());
			depthTrack.addValue(func.getMinY());
			depthTrack.addValue(func.getMaxY());
		}
		double maxDepth = Math.ceil(depthTrack.getMax()) + 2;
//		double maxNormDist = Math.ceil(Math.max(Math.abs(normDistTrack.getMin()), Math.abs(normDistTrack.getMax())))+2;
		Range xRange = new Range(-maxDepth, maxDepth);
		Range yRange = new Range(0, maxDepth);

		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		
		XYPlot plot = gp.getPlot();
		plot.getRangeAxis().setInverted(true);
		gp.getChartPanel().setSize(1000, 600);
		
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
	}

}
