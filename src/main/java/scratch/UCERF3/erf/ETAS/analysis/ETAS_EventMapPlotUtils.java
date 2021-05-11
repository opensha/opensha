package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
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
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.ComcatMetadata;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

/**
 * Utility methods used by multiple plots for maps
 * @author kevin
 *
 */
public class ETAS_EventMapPlotUtils {
	
	public static Region getMapRegion(ETAS_Config config, ETAS_Launcher launcher) {
		ComcatMetadata meta = config.getComcatMetadata();
		Region mapRegion = meta == null ? null : meta.region;
		if (mapRegion == null) {
			List<ETAS_EqkRupture> triggerRups = launcher.getCombinedTriggers();
			if (triggerRups != null && !triggerRups.isEmpty()) {
				MinMaxAveTracker latTrack = new MinMaxAveTracker();
				MinMaxAveTracker lonTrack = new MinMaxAveTracker();
				for (ETAS_EqkRupture rup : triggerRups) {
					for (Location loc : rup.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface()) {
						latTrack.addValue(loc.getLatitude());
						lonTrack.addValue(loc.getLongitude());
					}
				}
				mapRegion = getMapRegion(latTrack, lonTrack);
			}
		}
		return mapRegion;
	}

	static Region getMapRegion(MinMaxAveTracker latTrack, MinMaxAveTracker lonTrack) {
		Region mapRegion;
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
		return mapRegion;
	}
	
	static final float rup_surface_thickness = 3f;
	
	static void buildEventPlot(List<? extends ObsEqkRupture> events, List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars)
			throws IOException {
		buildEventPlot(events, funcs, chars, 0d);
	}
	
	private static DiscretizedFunc getDefaultMagSizeFunc() {
		DiscretizedFunc magSizeFunc = new ArbitrarilyDiscretizedFunc();
		magSizeFunc.set(2d, 2d);
		while (magSizeFunc.getMaxX() < 8d)
			magSizeFunc.set(magSizeFunc.getMaxX()+1, magSizeFunc.getMaxY()*1.5);
		return magSizeFunc;
	}
	
	private static void initializeMagFuncs(List<Range> magRanges, List<PlotCurveCharacterstics> magChars, List<XY_DataSet> magXYs,
			double maxMag, MinMaxAveTracker magTrack, CPT magCPT, Color fixedColor) {
		DiscretizedFunc magSizeFunc = getDefaultMagSizeFunc();
		
		if (!(maxMag > 0))
			maxMag = magTrack.getMax();
		double minFilledMag = 5d;
		if (maxMag <= 3) {
			magSizeFunc.scale(2d);
			minFilledMag = 2;
		} else if (maxMag <= 4) {
			magSizeFunc.scale(1.5);
			minFilledMag = 3;
		} else if (maxMag <= 5) {
			magSizeFunc.scale(1.25);
			minFilledMag = 4;
		}

		double magCeil = Math.ceil(maxMag);
		double magFloor = Math.floor(magTrack.getMin());
//		System.out.println("MaxMag: "+maxMag+", ceil: "+magCeil+", floor: "+magFloor);

		if (magCeil == maxMag || magFloor == magCeil)
			magCeil += 1;

		if (magCPT != null) {
			if (magCeil == magFloor+1)
				magCPT = magCPT.rescale(magFloor-1, magFloor);
			else
				magCPT = magCPT.rescale(magFloor, magCeil-1);
		}

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
			Color color = magCPT == null ? fixedColor : magCPT.getColor((float)minMag);
			PlotCurveCharacterstics plotChar = new PlotCurveCharacterstics(symbol, (float)symbolWidth, color);
			magChars.add(plotChar);
			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			if (minMag == magFloor && magTrack.getMin() >= minMag + 0.11) {
				// special case for first bin if it starts at a fractional value
				xy.setName("M"+optionalDigitDF.format(magTrack.getMin()));
			} else {
				xy.setName("M"+(int)minMag);
			}
			magXYs.add(xy);
		}
	}
	
	public static void buildEventPlot(List<? extends ObsEqkRupture> events, List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars,
			double maxMag) throws IOException {
		if (events.isEmpty())
			return;
		
		MinMaxAveTracker magTrack = new MinMaxAveTracker();
		for (ObsEqkRupture rup : events)
			magTrack.addValue(rup.getMag());
		
		// add trigger ruptures
		PlotCurveCharacterstics singleQuakeChar = null;
		List<Range> magRanges = null;
		List<PlotCurveCharacterstics> magChars = null;
		List<XY_DataSet> magXYs = null;

		CPT magCPT = GMT_CPT_Files.GMT_DRYWET.instance().reverse();
		
		if (events.size() > 1) {
			magRanges = new ArrayList<>();
			magChars = new ArrayList<>();
			magXYs = new ArrayList<>();
			
			initializeMagFuncs(magRanges, magChars, magXYs, maxMag, magTrack, magCPT, null);
		} else {
			DiscretizedFunc magSizeFunc = getDefaultMagSizeFunc();
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
		
		List<XY_DataSet> surfXYs = new ArrayList<>();
		List<PlotCurveCharacterstics> surfChars = new ArrayList<>();
		for (ObsEqkRupture event : events) {
			double mag = event.getMag();
			XY_DataSet ptXY;
			PlotCurveCharacterstics plotChar;
			Location hypo = event.getHypocenterLocation();
			if (singleQuakeChar != null) {
				ptXY = new DefaultXY_DataSet();
				ptXY.setName("M"+(float)mag);
				plotChar = singleQuakeChar;
				if (hypo != null) {
					funcs.add(ptXY);
					chars.add(plotChar);
				}
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
				Preconditions.checkNotNull(ptXY, "No mag ranges found which contain M=%s", mag);
			}
			RuptureSurface surf = event.getRuptureSurface();
			if (surf != null && !surf.isPointSurface()) {
				List<XY_DataSet> mySurfXYs = getSurfOutlines(surf);
				PlotCurveCharacterstics surfChar = new PlotCurveCharacterstics(
						PlotLineType.DOTTED, rup_surface_thickness-1f, plotChar.getColor());
				for (XY_DataSet surfXY : mySurfXYs) {
					surfXYs.add(surfXY);
					surfChars.add(surfChar);
				}
				mySurfXYs = getSurfTraces(surf);
				surfChar = new PlotCurveCharacterstics(
						PlotLineType.SOLID, rup_surface_thickness, plotChar.getColor());
				for (XY_DataSet surfXY : mySurfXYs) {
					surfXYs.add(surfXY);
					surfChars.add(surfChar);
				}
			}
			if (hypo != null)
				ptXY.set(hypo.getLongitude(), hypo.getLatitude());
		}

		// add finite surfaces below quakes
		funcs.addAll(surfXYs);
		chars.addAll(surfChars);
		
		for (int i=0; magXYs != null && i<magXYs.size(); i++) {
			XY_DataSet xy = magXYs.get(i);
			if (xy.size() > 0) {
				funcs.add(xy);
				chars.add(magChars.get(i));
			}
		}
		
		// add transparent surfaces above quakes
		if (magXYs != null) {
			for (int i=0; i<surfXYs.size(); i++) {
				funcs.add(surfXYs.get(i));
				PlotCurveCharacterstics surfChar = surfChars.get(i);
				Color c = surfChar.getColor();
				if (surfChar.getLineType() == PlotLineType.SOLID)
					c = new Color(c.getRed(), c.getGreen(), c.getBlue(), 220);
				else
					c = new Color(c.getRed(), c.getGreen(), c.getBlue(), 127);
				chars.add(new PlotCurveCharacterstics(surfChar.getLineType(), surfChar.getLineWidth(), c));
			}
		}
	}
	
	public static void buildGenerationPlot(List<? extends ETAS_EqkRupture> events, List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars,
			double maxMag, int maxGeneration) throws IOException {
		if (events.isEmpty())
			return;
		
		MinMaxAveTracker magTrack = new MinMaxAveTracker();
		for (ObsEqkRupture rup : events)
			magTrack.addValue(rup.getMag());

		int minGeneration = maxGeneration-1;
		for (ETAS_EqkRupture rup : events)
			minGeneration = Integer.min(minGeneration, rup.getGeneration());
		Color[] genColors = new Color[1+maxGeneration-minGeneration];
		
//		CPT genCPT = GMT_CPT_Files.GMT_DRYWET.instance().reverse();
//		genCPT = genCPT.rescale(minGeneration, maxGeneration);
//		for (int i=0; i<genColors.length; i++)
//			genColors[i] = genCPT.getColor((float)(i+minGeneration));
		Color[] rawGenColors = { Color.BLUE.darker(), Color.CYAN.darker().darker(), Color.GREEN.darker(), Color.ORANGE.darker(), Color.RED.darker(),
				Color.MAGENTA.darker()};
		for (int i=0; i<genColors.length; i++)
			genColors[i] = rawGenColors[i%rawGenColors.length];
		
		List<List<Range>> genMagRanges = new ArrayList<>();
		List<List<PlotCurveCharacterstics>> genMagChars = new ArrayList<>();
		List<List<XY_DataSet>> genMagXYs = new ArrayList<>();
		
		for (int g=0; g<=maxGeneration; g++) {
			List<Range> magRanges = new ArrayList<>();
			genMagRanges.add(magRanges);
			List<PlotCurveCharacterstics> magChars = new ArrayList<>();
			genMagChars.add(magChars);
			List<XY_DataSet> magXYs = new ArrayList<>();
			genMagXYs.add(magXYs);
			
			initializeMagFuncs(magRanges, magChars, magXYs, maxMag, magTrack, null, genColors[Integer.max(0, g-minGeneration)]);
		}
		
		List<XY_DataSet> surfXYs = new ArrayList<>();
		List<PlotCurveCharacterstics> surfChars = new ArrayList<>();
		for (ETAS_EqkRupture event : events) {
			int gen = event.getGeneration();
			Preconditions.checkState(gen >= 0);
			if (gen > maxGeneration)
				gen = maxGeneration;
			
			List<Range> magRanges = genMagRanges.get(gen);
			List<PlotCurveCharacterstics> magChars = genMagChars.get(gen);
			List<XY_DataSet> magXYs = genMagXYs.get(gen);
			
			double mag = event.getMag();
			XY_DataSet ptXY = null;
			PlotCurveCharacterstics plotChar = null;
			Location hypo = event.getHypocenterLocation();
			for (int i=0; i<magRanges.size(); i++) {
				Range magRange = magRanges.get(i);
				if (mag >= magRange.getLowerBound() && mag < magRange.getUpperBound()) {
					ptXY = magXYs.get(i);
					plotChar = magChars.get(i);
				}
			}
			Preconditions.checkNotNull(ptXY, "No mag ranges found which contain M=%s", mag);
			RuptureSurface surf = event.getRuptureSurface();
			if (surf != null && !surf.isPointSurface()) {
				List<XY_DataSet> mySurfXYs = getSurfOutlines(surf);
				PlotCurveCharacterstics surfChar = new PlotCurveCharacterstics(
						PlotLineType.DOTTED, rup_surface_thickness-1f, plotChar.getColor());
				for (XY_DataSet surfXY : mySurfXYs) {
					surfXYs.add(surfXY);
					surfChars.add(surfChar);
				}
				mySurfXYs = getSurfTraces(surf);
				surfChar = new PlotCurveCharacterstics(
						PlotLineType.SOLID, rup_surface_thickness, plotChar.getColor());
				for (XY_DataSet surfXY : mySurfXYs) {
					surfXYs.add(surfXY);
					surfChars.add(surfChar);
				}
			}
			if (hypo != null)
				ptXY.set(hypo.getLongitude(), hypo.getLatitude());
		}

		// add finite surfaces below quakes
		funcs.addAll(surfXYs);
		chars.addAll(surfChars);
		
		// add by generation
		boolean firstGen = true;
		for (int gen=0; gen<=maxGeneration; gen++) {
			boolean labeled = false;
			List<XY_DataSet> magXYs = genMagXYs.get(gen);
			
			for (int m=0; m<magXYs.size(); m++) {
				XY_DataSet magXY = magXYs.get(m);
				if (magXY.size() == 0)
					continue;
				if (labeled) {
					magXY.setName(null);
				} else {
					String name = firstGen ? "Generation " : "";
					name += gen;
					if (gen == maxGeneration)
						name += "+";
					magXY.setName(name);
					labeled = true;
					firstGen = false;
				}
			}
		}
		// add with smallest mag on bottom
		for (int m=0; m<genMagXYs.get(0).size(); m++) {
			for (int gen=0; gen<=maxGeneration; gen++) {
//			for (int gen=maxGeneration; gen>=0; gen--) {
				PlotCurveCharacterstics magChar = genMagChars.get(gen).get(m);
				XY_DataSet magXY = genMagXYs.get(gen).get(m);
				if (magXY.size() > 0) {
					funcs.add(magXY);
					chars.add(magChar);
					if (magChar.getSymbol() != null && magChar.getSymbol().isFilled()) {
						// add outline as well
						PlotCurveCharacterstics outlineChar = new PlotCurveCharacterstics(PlotSymbol.CIRCLE,
								magChar.getSymbolWidth(), new Color(0, 0, 0, 100));
						if (magXY.getName() != null) {
							magXY = magXY.deepClone();
							magXY.setName(null);
						}
						funcs.add(magXY);
						chars.add(outlineChar);
					}
				}
			}
		}
		
		// add transparent surfaces above quakes
		for (int i=0; i<surfXYs.size(); i++) {
			funcs.add(surfXYs.get(i));
			PlotCurveCharacterstics surfChar = surfChars.get(i);
			Color c = surfChar.getColor();
			if (surfChar.getLineType() == PlotLineType.SOLID)
				c = new Color(c.getRed(), c.getGreen(), c.getBlue(), 220);
			else
				c = new Color(c.getRed(), c.getGreen(), c.getBlue(), 127);
			chars.add(new PlotCurveCharacterstics(surfChar.getLineType(), surfChar.getLineWidth(), c));
		}
	}
	
	private static final DecimalFormat optionalDigitDF = new DecimalFormat("0.#");
	
	public static List<XY_DataSet> getSurfTraces(RuptureSurface surf) {
		List<RuptureSurface> allSurfs = new ArrayList<>();
		if (surf instanceof CompoundSurface)
			allSurfs.addAll(((CompoundSurface)surf).getSurfaceList());
		else
			allSurfs.add(surf);
		List<XY_DataSet> ret = new ArrayList<>();
		for (RuptureSurface subSurf : allSurfs) {
			XY_DataSet surfXY = new DefaultXY_DataSet();
			for (Location loc : subSurf.getEvenlyDiscritizedUpperEdge())
				surfXY.set(loc.getLongitude(), loc.getLatitude());
			ret.add(surfXY);
		}
		return ret;
	}
	
	public static List<XY_DataSet> getSurfOutlines(RuptureSurface surf) {
		double dip;
		try {
			dip = surf.getAveDip();
		} catch (Exception e) {
			// can fail for some implementations, default to not assuming SS
			dip = 45;
		}
		List<RuptureSurface> allSurfs = new ArrayList<>();
		if (surf instanceof CompoundSurface)
			allSurfs.addAll(((CompoundSurface)surf).getSurfaceList());
		else
			allSurfs.add(surf);
		List<XY_DataSet> ret = new ArrayList<>();
		for (RuptureSurface subSurf : allSurfs) {
			if (dip == 90d) {
				XY_DataSet surfXY = new DefaultXY_DataSet();
				for (Location loc : subSurf.getEvenlyDiscritizedUpperEdge())
					surfXY.set(loc.getLongitude(), loc.getLatitude());
				ret.add(surfXY);
			} else {
				if (surf instanceof EvenlyGriddedSurface) {
					// add each leg separately
					// this will cause them to draw overlaps consistently (dotted lines will not mush together to look like solid)
					
					EvenlyGriddedSurface gridSurf = (EvenlyGriddedSurface)surf;
					
					// top and bottom
					XY_DataSet topXY = new DefaultXY_DataSet();
					XY_DataSet botXY = new DefaultXY_DataSet();
					for (int col=0; col<gridSurf.getNumCols(); col++) {
						Location topLoc = gridSurf.get(0, col);
						Location botLoc = gridSurf.get(gridSurf.getNumRows()-1, col);
						topXY.set(topLoc.getLongitude(), topLoc.getLatitude());
						botXY.set(botLoc.getLongitude(), botLoc.getLatitude());
					}
					
					// left and right
					XY_DataSet leftXY = new DefaultXY_DataSet();
					XY_DataSet rightXY = new DefaultXY_DataSet();
					for (int row=0; row<gridSurf.getNumRows(); row++) {
						Location leftLoc = gridSurf.get(row, 0);
						Location rightLoc = gridSurf.get(row, gridSurf.getNumCols()-1);
						leftXY.set(leftLoc.getLongitude(), leftLoc.getLatitude());
						rightXY.set(rightLoc.getLongitude(), rightLoc.getLatitude());
					}
					ret.add(topXY); ret.add(botXY); ret.add(leftXY); ret.add(rightXY);
				} else {
					try {
						XY_DataSet surfXY = new DefaultXY_DataSet();
						for (Location loc : subSurf.getPerimeter())
							surfXY.set(loc.getLongitude(), loc.getLatitude());
						surfXY.set(surfXY.get(0));
						ret.add(surfXY);
					} catch (UnsupportedOperationException e) {
						System.err.println("Skipping surface in map, cannot get perimeter: "+e.getMessage());
					}
				}
			}
		}
		return ret;
	}
	
	private static double traceDist(FaultTrace trace, Location loc) {
		return LocationUtils.distanceToLine(trace.first(), trace.last(), loc);
	}
	
	static void writeMapPlot(List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars, Region mapRegion, String title,
			File outputDir, String prefix) throws IOException {
		writeMapPlot(funcs, chars, mapRegion, title, outputDir, prefix, 1000);
	}
	
	public static HeadlessGraphPanel writeMapPlot(List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars, Region mapRegion, String title,
			File outputDir, String prefix, int width) throws IOException {
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
		
		int height = PlotUtils.calcHeight(gp, width, true);
		gp.getChartPanel().setSize(width, height);
		
		gp.saveAsPNG(new File(outputDir, prefix+".png").getAbsolutePath());
		gp.saveAsPDF(new File(outputDir, prefix+".pdf").getAbsolutePath());
		return gp;
	}
	
	static void buildEventDepthPlot(List<ETAS_EqkRupture> events, List<XY_DataSet> funcs, List<PlotCurveCharacterstics> chars,
			RuptureSurface surf) throws IOException {
		if (events.isEmpty() || surf == null || surf.isPointSurface())
			return;
		
		FaultTrace trace;
		if (surf instanceof CompoundSurface) {
			// use extents
			List<? extends RuptureSurface> subSurfs = ((CompoundSurface)surf).getSurfaceList();
			List<Location[]> pairs = new ArrayList<>();
			for (int i=0; i<subSurfs.size(); i++) {
				LocationList tr1 = subSurfs.get(i).getUpperEdge();
				pairs.add(new Location[] { tr1.first(), tr1.last() });
				for (int j=i+1; j<subSurfs.size(); j++) {
					LocationList tr2 = subSurfs.get(j).getUpperEdge();
					pairs.add(new Location[] { tr1.first(), tr2.first() });
					pairs.add(new Location[] { tr1.first(), tr2.last() });
					pairs.add(new Location[] { tr1.last(), tr2.first() });
					pairs.add(new Location[] { tr1.last(), tr2.last() });
				}
			}
			Location[] furthestPair = null;
			double furthestDist = 0d;
			for (Location[] pair : pairs) {
				double dist = LocationUtils.horzDistanceFast(pair[0], pair[1]);
				if (dist > furthestDist) {
					furthestDist = dist;
					furthestPair = pair;
				}
			}
			
			trace = new FaultTrace(null);
			trace.add(furthestPair[0]);
			trace.add(furthestPair[1]);
		} else {
			trace = surf.getUpperEdge();
		}
		
		// draw rupture
		PlotCurveCharacterstics rupBelowChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 4f, Color.BLACK);
		PlotCurveCharacterstics rupAboveChar = new PlotCurveCharacterstics(PlotLineType.DOTTED, 4f, Color.BLACK);
		List<XY_DataSet> rupFuncs = new ArrayList<>();
		List<RuptureSurface> subSurfs = new ArrayList<>();
		if (surf instanceof CompoundSurface)
			subSurfs.addAll(((CompoundSurface)surf).getSurfaceList());
		else
			subSurfs.add(surf);
		for (RuptureSurface subSurf : subSurfs) {
			XY_DataSet xy = new DefaultXY_DataSet();
			if (rupFuncs.isEmpty())
				xy.setName("Rupture Surface");
			if (subSurf instanceof EvenlyGriddedSurface) {
				EvenlyGriddedSurface gridSurf = (EvenlyGriddedSurface)subSurf;
				Location topLeft = gridSurf.get(0, 0);
				Location botLeft = gridSurf.get(gridSurf.getNumRows()-1, 0);
				Location topRight = gridSurf.get(0, gridSurf.getNumCols()-1);
				Location botRight = gridSurf.get(gridSurf.getNumRows()-1, gridSurf.getNumCols()-1);
				xy.set(traceDist(trace, topLeft), topLeft.getDepth());
				xy.set(traceDist(trace, topRight), topRight.getDepth());
				xy.set(traceDist(trace, botLeft), botLeft.getDepth());
				xy.set(traceDist(trace, botRight), botRight.getDepth());
				xy.set(traceDist(trace, topLeft), topLeft.getDepth());
			} else {
				FaultTrace upper = subSurf.getEvenlyDiscritizedUpperEdge();
				LocationList lower = subSurf.getEvenlyDiscritizedLowerEdge();
				for (Location loc : upper)
					xy.set(traceDist(trace, loc), loc.getDepth());
				for (int j=lower.size(); --j>=0;)
					xy.set(traceDist(trace, lower.get(j)), lower.get(j).getDepth());
				// close it
				xy.set(traceDist(trace, upper.first()), upper.first().getDepth());
			}
			rupFuncs.add(xy);
			if (xy.getName() != null) {
				xy = xy.deepClone();
				xy.setName(null);
			}
			funcs.add(xy);
			chars.add(rupBelowChar);
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
			if (event.getHypocenterLocation() == null)
				continue;
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
		
		for (XY_DataSet xy : rupFuncs) {
			funcs.add(xy);
			chars.add(rupAboveChar);
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
