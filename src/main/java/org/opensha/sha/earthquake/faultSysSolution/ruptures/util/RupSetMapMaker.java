package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry.LineString;
import org.opensha.commons.geo.json.Geometry.Polygon;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZGraphPanel;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

/**
 * Utility class for making map view plots of rupture sets
 * 
 * @author kevin
 *
 */
public class RupSetMapMaker {
	
	private final List<? extends FaultSection> subSects;
	private Region region;
	
	private PlotCurveCharacterstics politicalBoundaryChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);
	private PlotCurveCharacterstics sectOutlineChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.LIGHT_GRAY);
	private PlotCurveCharacterstics sectTraceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.DARK_GRAY);
	
	private float scalarThickness = 3f;
	private float jumpLineThickness = 3f;
	private boolean legendVisible = true;
	private boolean fillSurfaces = false;
	
	private boolean writePDFs = true;
	private boolean writeGeoJSON = true;
	
	private List<Feature> sectFeatures = null;
	private List<Feature> jumpFeatures = null;
	
	/*
	 * Things to plot
	 */
	// political boundaries
	private XY_DataSet[] politicalBoundaries;
	
	// section scalars
	private double[] scalars;
	private CPT scalarCPT;
	private String scalarLabel;
	private boolean skipNaNs = false;
	
	// jumps (solid color)
	private List<Collection<Jump>> jumpLists;
	private List<Color> jumpColors;
	private List<String> jumpLabels;
	
	// jumps (scalar values)
	private List<Jump> scalarJumps;
	private List<Double> scalarJumpValues;
	private CPT scalarJumpsCPT;
	private String scalarJumpsLabel;
	
	private Collection<FaultSection> highlightSections;
	private PlotCurveCharacterstics highlightTraceChar;
	
	/*
	 * Caches
	 */
	private List<RuptureSurface> sectSurfs;
	private List<Location> surfMiddles;

	public RupSetMapMaker(FaultSystemRupSet rupSet, Region region) {
		this(rupSet.getFaultSectionDataList(), region);
	}

	public RupSetMapMaker(List<? extends FaultSection> subSects, Region region) {
		this.region = region;
		this.subSects = subSects;
		
		Preconditions.checkState(!subSects.isEmpty());
		for (int s=0; s<subSects.size(); s++)
			Preconditions.checkState(subSects.get(s).getSectionId() == s, "Subsection IDs must match index in list");
		
		// political boundary special cases
		if (region.contains(new Location(34, -118))) {
			// CA
			try {
				politicalBoundaries = PoliticalBoundariesData.loadCAOutlines();
			} catch (IOException e) {
				System.err.println("WARNING: couldn't load CA outline data: "+e.getMessage());
			}
		} else if (region.contains(new Location(-42.4, 172.3)) || region.contains(new Location(-38.8, 175.9))) {
			// NZ
			try {
				politicalBoundaries = PoliticalBoundariesData.loadNZOutlines();
			} catch (IOException e) {
				System.err.println("WARNING: couldn't load NZ outline data: "+e.getMessage());
			}
		}
	}
	
	public static Region buildBufferedRegion(List<? extends FaultSection> subSects) {
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (FaultSection sect : subSects) {
			for (Location loc : sect.getFaultTrace()) {
				latTrack.addValue(loc.getLatitude());
				lonTrack.addValue(loc.getLongitude());
			}
		}
		double minLat = Math.floor(latTrack.getMin());
		double maxLat = Math.ceil(latTrack.getMax());
		double minLon = Math.floor(lonTrack.getMin());
		double maxLon = Math.ceil(lonTrack.getMax());
		return new Region(new Location(minLat, minLon), new Location(maxLat, maxLon));
	}
	
	public void setRegion(Region region) {
		this.region = region;
	}

	public void setPoliticalBoundaryChar(PlotCurveCharacterstics politicalBoundaryChar) {
		this.politicalBoundaryChar = politicalBoundaryChar;
	}

	public void setSectOutlineChar(PlotCurveCharacterstics sectOutlineChar) {
		this.sectOutlineChar = sectOutlineChar;
	}

	public void setSectTraceChar(PlotCurveCharacterstics sectTraceChar) {
		this.sectTraceChar = sectTraceChar;
	}

	public void setScalarThickness(float scalarThickness) {
		this.scalarThickness = scalarThickness;
	}

	public void setJumpLineThickness(float jumpLineThickness) {
		this.jumpLineThickness = jumpLineThickness;
	}

	public void setPoliticalBoundaries(XY_DataSet[] politicalBoundaries) {
		this.politicalBoundaries = politicalBoundaries;
	}
	
	public void setLegendVisible(boolean legendVisible) {
		this.legendVisible = legendVisible;
	}
	
	public void setSkipNaNs(boolean skipNaNs) {
		this.skipNaNs = skipNaNs;
	}
	
	public void highLightSections(Collection<FaultSection> highlightSections, PlotCurveCharacterstics highlightTraceChar) {
		this.highlightSections = highlightSections;
		this.highlightTraceChar = highlightTraceChar;
	}
	
	public void setWritePDFs(boolean writePDFs) {
		this.writePDFs = writePDFs;
	}
	
	public void setWriteGeoJSON(boolean writeGeoJSON) {
		this.writeGeoJSON = writeGeoJSON;
	}

	public void setFillSurfaces(boolean fillSurfaces){
		this.fillSurfaces = fillSurfaces;
	}
	
	public void plotSectScalars(List<Double> scalars, CPT cpt, String label) {
		plotSectScalars(Doubles.toArray(scalars), cpt, label);
	}
	
	public void plotSectScalars(double[] scalars, CPT cpt, String label) {
		Preconditions.checkState(scalars.length == subSects.size());
		Preconditions.checkNotNull(cpt);
		this.scalars = scalars;
		this.scalarCPT = cpt;
		this.scalarLabel = label;
	}
	
	public void clearSectScalars() {
		this.scalars = null;
		this.scalarCPT = null;
		this.scalarLabel = null;
	}
	
	public void plotJumps(Collection<Jump> jumps, Color color, String label) {
		if (jumpLists == null) {
			jumpLists = new ArrayList<>();
			jumpColors = new ArrayList<>();
			jumpLabels = new ArrayList<>();
		}
		jumpLists.add(jumps);
		jumpColors.add(color);
		jumpLabels.add(label);
	}
	
	public void clearJumps() {
		jumpLists = null;
		jumpColors = null;
		jumpLabels = null;
	}
	
	public void plotJumpScalars(List<Jump> jumps, List<Double> values, CPT cpt, String label) {
		Preconditions.checkState(jumps.size() == values.size());
		Preconditions.checkNotNull(cpt);
		this.scalarJumps = jumps;
		this.scalarJumpValues = values;
		this.scalarJumpsCPT = cpt;
		this.scalarJumpsLabel = label;
	}
	
	public void clearJumpScalars() {
		this.scalarJumps = null;
		this.scalarJumpValues = null;
		this.scalarJumpsCPT = null;
		this.scalarJumpsLabel = null;
	}
	
	private RuptureSurface getSectSurface(FaultSection sect) {
		if (sectSurfs == null) {
			List<RuptureSurface> surfs = new ArrayList<>();
			for (FaultSection s : subSects)
				surfs.add(s.getFaultSurface(1d, false, false));
			this.sectSurfs = surfs;
		}
		return sectSurfs.get(sect.getSectionId());
	}
	
	private Location getSectMiddle(FaultSection sect) {
		if (surfMiddles == null) {
			List<Location> middles = new ArrayList<>();
			for (FaultSection s : subSects)
				middles.add(GriddedSurfaceUtils.getSurfaceMiddleLoc(getSectSurface(s)));
			this.surfMiddles = middles;
		}
		return surfMiddles.get(sect.getSectionId());
	}
	
	private Feature surfFeature(FaultSection sect, PlotCurveCharacterstics pChar) {
		RuptureSurface surf = getSectSurface(sect);
		LocationList perim = new LocationList();
		perim.addAll(surf.getPerimeter());
		if (!perim.first().equals(perim.last()))
			perim.add(perim.first());
		Polygon poly = new Polygon(perim);
		FeatureProperties props = new FeatureProperties();
		props.set("name", sect.getSectionName());
		props.set("id", sect.getSectionId());
		if (pChar.getLineType() != null) {
			props.set(FeatureProperties.STROKE_WIDTH_PROP, pChar.getLineWidth());
			props.set(FeatureProperties.STROKE_COLOR_PROP, pChar.getColor());
		}
		props.set(FeatureProperties.FILL_OPACITY_PROP, 0.05d);
		return new Feature(sect.getSectionName(), poly, props);
	}
	
	private Feature traceFeature(FaultSection sect, PlotCurveCharacterstics pChar) {
		LineString line = new LineString(getSectSurface(sect).getUpperEdge());
		FeatureProperties props = new FeatureProperties();
		props.set("name", sect.getSectionName());
		props.set("id", sect.getSectionId());
		if (pChar.getLineType() != null) {
			props.set(FeatureProperties.STROKE_WIDTH_PROP, pChar.getLineWidth());
			props.set(FeatureProperties.STROKE_COLOR_PROP, pChar.getColor());
		}
		return new Feature(sect.getSectionName(), line, props);
	}
	
	public PlotSpec buildPlot(String title) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		if (writeGeoJSON) {
			sectFeatures = new ArrayList<>();
			jumpFeatures = new ArrayList<>();
		}
		
		// add political boundaries
		if (politicalBoundaries != null && politicalBoundaryChar != null) {
			for (XY_DataSet xy : politicalBoundaries) {
				funcs.add(xy);
				chars.add(politicalBoundaryChar);
			}
		}
		
		Region plotRegion = new Region(new Location(region.getMinLat(), region.getMinLon()), 
				new Location(region.getMaxLat(), region.getMaxLon()));
		HashSet<FaultSection> plotSects = new HashSet<>();
		for (FaultSection subSect : subSects) {
			RuptureSurface surf = getSectSurface(subSect);
			boolean include = false;
			for (Location loc : surf.getEvenlyDiscritizedPerimeter()) {
				if (plotRegion.contains(loc)) {
					include = true;
					break;
				}
			}
			if (include)
				plotSects.add(subSect);
		}
		
		boolean hasLegend = false;
		
		// plot section outlines on bottom
		for (int s=0; s<subSects.size(); s++) {
			FaultSection sect = subSects.get(s);
			if (!plotSects.contains(sect))
				continue;
			RuptureSurface surf = getSectSurface(sect);
			
			XY_DataSet trace = new DefaultXY_DataSet();
			for (Location loc : surf.getEvenlyDiscritizedUpperEdge())
				trace.set(loc.getLongitude(), loc.getLatitude());
			
			if (s == 0)
				trace.setName("Fault Sections");
			
			// we'll plot fault traces if we don't have scalar values
			boolean doTraces = scalars == null;
			if (!doTraces && skipNaNs && Double.isNaN(scalars[s]))
				doTraces = true;
			
			if (sectOutlineChar != null && (sect.getAveDip() != 90d)) {
				XY_DataSet outline = new DefaultXY_DataSet();
				LocationList perimeter = surf.getPerimeter();
				for (Location loc : perimeter)
					outline.set(loc.getLongitude(), loc.getLatitude());
				Location first = perimeter.first();
				outline.set(first.getLongitude(), first.getLatitude());
				
				funcs.add(0, outline);
				chars.add(0, sectOutlineChar);
				if (doTraces && sectTraceChar == null && s == 0)
					outline.setName("Fault Sections");
				if (writeGeoJSON)
					sectFeatures.add(0, surfFeature(sect, sectOutlineChar));
			}
			
			if (doTraces && sectTraceChar != null) {
				funcs.add(trace);
				chars.add(sectTraceChar);
				if (writeGeoJSON)
					sectFeatures.add(traceFeature(sect, sectTraceChar));
			}
		}
		
		List<PaintScaleLegend> cptLegend = new ArrayList<>();
		
		// plot sect scalars
		if (scalars != null) {
			List<ComparablePairing<Double, FaultSection>> sortables = new ArrayList<>();
			for (int s=0; s<scalars.length; s++)
				sortables.add(new ComparablePairing<>(scalars[s], subSects.get(s)));
			Collections.sort(sortables);
			for (ComparablePairing<Double, FaultSection> val : sortables) {
				float scalar = val.getComparable().floatValue();
				Color color = scalarCPT.getColor(scalar);
				FaultSection sect = val.getData();
				if (!plotSects.contains(sect) || (skipNaNs && Double.isNaN(val.getComparable())))
					continue;
				RuptureSurface surf = getSectSurface(sect);

				if (fillSurfaces && sect.getAveDip() != 90d) {
					XY_DataSet outline = new DefaultXY_DataSet();
					LocationList perimeter = surf.getPerimeter();
					for (Location loc : perimeter)
						outline.set(loc.getLongitude(), loc.getLatitude());

					PlotCurveCharacterstics fillChar = new PlotCurveCharacterstics(PlotLineType.POLYGON_SOLID, 0.5f, color);

					funcs.add(0, outline);
					chars.add(0, fillChar);
				}

				XY_DataSet trace = new DefaultXY_DataSet();
				for (Location loc : surf.getEvenlyDiscritizedUpperEdge())
					trace.set(loc.getLongitude(), loc.getLatitude());
				
				funcs.add(trace);
				PlotCurveCharacterstics scalarChar = new PlotCurveCharacterstics(PlotLineType.SOLID, scalarThickness, color);
				chars.add(scalarChar);
				if (writeGeoJSON) {
					Feature feature = traceFeature(sect, scalarChar);
					if (Float.isFinite(scalar))
						feature.properties.set(scalarLabel, scalar);
					sectFeatures.add(feature);
				}
			}
			
			cptLegend.add(buildCPTLegend(scalarCPT, scalarLabel));
		}
		
		if (highlightSections != null && highlightTraceChar != null) {
			for (FaultSection hightlight : highlightSections) {
				XY_DataSet trace = new DefaultXY_DataSet();
				for (Location loc : getSectSurface(hightlight).getEvenlyDiscritizedUpperEdge())
					trace.set(loc.getLongitude(), loc.getLatitude());
				funcs.add(trace);
				chars.add(highlightTraceChar);
				if (writeGeoJSON)
					sectFeatures.add(traceFeature(hightlight, highlightTraceChar));
			}
		}
		
		// plot jumps
		if (jumpLists != null && !jumpLists.isEmpty()) {
			for (int i=0; i<jumpLists.size(); i++) {
				Collection<Jump> jumps = jumpLists.get(i);
				Color color = jumpColors.get(i);
				String label = jumpLabels.get(i);
				hasLegend = hasLegend || (label != null && !label.isEmpty());
				
				PlotCurveCharacterstics jumpChar = new PlotCurveCharacterstics(PlotLineType.SOLID, jumpLineThickness, color);
				boolean first = true;
				for (Jump jump : jumps) {
					Location loc1 = getSectMiddle(jump.fromSection);
					Location loc2 = getSectMiddle(jump.toSection);
					XY_DataSet xy = new DefaultXY_DataSet();
					if (first) {
						first = false;
						if (label != null)
							xy.setName(label);
					}
					xy.set(loc1.getLongitude(), loc1.getLatitude());
					xy.set(loc2.getLongitude(), loc2.getLatitude());
					funcs.add(xy);
					chars.add(jumpChar);
					
					if (writeGeoJSON) {
						LineString line = new LineString(loc1, loc2);
						FeatureProperties props = new FeatureProperties();
						props.set(FeatureProperties.STROKE_WIDTH_PROP, jumpLineThickness);
						props.set(FeatureProperties.STROKE_COLOR_PROP, color);
						props.set("label", label);
						props.set("fromSection", jump.fromSection.getName());
						props.set("toSection", jump.toSection.getName());
						props.set("distance", jump.distance);
						jumpFeatures.add(new Feature(line, props));
					}
				}
			}
		}
		
		// plot jump scalars
		if (scalarJumps != null) {
			List<ComparablePairing<Double, XY_DataSet>> sortables = new ArrayList<>();
			for (int j=0; j<scalarJumps.size(); j++) {
				double scalar = scalarJumpValues.get(j);
				if (skipNaNs && Double.isNaN(scalar))
					continue;
				Jump jump = scalarJumps.get(j);
				Location loc1 = getSectMiddle(jump.fromSection);
				Location loc2 = getSectMiddle(jump.toSection);
				XY_DataSet xy = new DefaultXY_DataSet();
				xy.set(loc1.getLongitude(), loc1.getLatitude());
				xy.set(loc2.getLongitude(), loc2.getLatitude());
				sortables.add(new ComparablePairing<>(scalar, xy));
				
				if (writeGeoJSON) {
					LineString line = new LineString(loc1, loc2);
					FeatureProperties props = new FeatureProperties();
					props.set(FeatureProperties.STROKE_WIDTH_PROP, jumpLineThickness);
					props.set(FeatureProperties.STROKE_COLOR_PROP, scalarJumpsCPT.getColor((float)scalar));
					if (Double.isFinite(scalar))
						props.set(scalarJumpsLabel, (float)scalar);
					props.set("fromSection", jump.fromSection.getName());
					props.set("toSection", jump.toSection.getName());
					props.set("distance", jump.distance);
					jumpFeatures.add(new Feature(line, props));
				}
			}
			Collections.sort(sortables);
			for (ComparablePairing<Double, XY_DataSet> val : sortables) {
				float scalar = val.getComparable().floatValue();
				Color color = scalarJumpsCPT.getColor(scalar);
				
				funcs.add(val.getData());
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, jumpLineThickness, color));
			}
			
			cptLegend.add(buildCPTLegend(scalarJumpsCPT, scalarJumpsLabel));
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Longitude", "Latitude");
		spec.setLegendVisible(legendVisible && hasLegend);
		
		for (PaintScaleLegend legend : cptLegend)
			spec.addSubtitle(legend);
		
		return spec;
	}
	
	public Range getXRange() {
		return new Range(region.getMinLon(), region.getMaxLon());
	}
	
	public Range getYRange() {
		return new Range(region.getMinLat(), region.getMaxLat());
	}
	
	static PaintScaleLegend buildCPTLegend(CPT cpt, String label) {
		double cptLen = cpt.getMaxValue() - cpt.getMinValue();
		double cptTick;
		if (cptLen > 5000)
			cptTick = 1000;
		else if (cptLen > 1000)
			cptTick = 500;
		else if (cptLen > 500)
			cptTick = 100;
		else if (cptLen > 100)
			cptTick = 50;
		else if (cptLen > 50)
			cptTick = 10;
		else if (cptLen > 10)
			cptTick = 5;
		else if (cptLen > 5)
			cptTick = 1;
		else if (cptLen > 1)
			cptTick = .5;
		else if (cptLen > .5)
			cptTick = .1;
		else
			cptTick = cptLen / 10d;
		return XYZGraphPanel.getLegendForCPT(cpt, label, 24, 18, cptTick, RectangleEdge.BOTTOM);
	}
	
	public void plot(File outputDir, String prefix, String title) throws IOException {
		plot(outputDir, prefix, buildPlot(title), 800);
	}
	
	public void plot(File outputDir, String prefix, String title, int width) throws IOException {
		plot(outputDir, prefix, buildPlot(title), width);
	}
	
	public void plot(File outputDir, String prefix, PlotSpec spec) throws IOException {
		plot(outputDir, prefix, spec, 800);
	}
	
	public void plot(File outputDir, String prefix, PlotSpec spec, int width) throws IOException {
		HeadlessGraphPanel gp = PlotUtils.initHeadless();
		
		Range xRange = getXRange();
		Range yRange = getYRange();
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		double maxSpan = Math.max(xRange.getLength(), yRange.getLength());
		double tick;
		if (maxSpan > 20)
			tick = 5d;
		else if (maxSpan > 8)
			tick = 2d;
		else if (maxSpan > 3)
			tick = 1d;
		else if (maxSpan > 1)
			tick = 0.5d;
		else
			tick = 0.2;
		PlotUtils.setXTick(gp, tick);
		PlotUtils.setYTick(gp, tick);
		
		PlotUtils.writePlots(outputDir, prefix, gp, width, true, true, writePDFs, false);
		
		if (writeGeoJSON && sectFeatures != null && jumpFeatures != null) {
			List<Feature> plotFeatures = new ArrayList<>(sectFeatures);
			if (!jumpFeatures.isEmpty()) {
				// write out combined and separate
				FeatureCollection jumpsOnly = new FeatureCollection(jumpFeatures);
				FeatureCollection.write(jumpsOnly, new File(outputDir, prefix+"_jumps_only.geojson"));
				plotFeatures.addAll(jumpFeatures);
			}
			
			FeatureCollection features = new FeatureCollection(plotFeatures);
			FeatureCollection.write(features, new File(outputDir, prefix+".geojson"));
		}
	}
	
	public static String getGeoJSONViewerLink(String url) {
		String ret = "http://geojson.io/#data=data:text/x-url,";
		try {
			ret += URLEncoder.encode(url, "UTF-8")
			        .replaceAll("\\+", "%20")
			        .replaceAll("\\%21", "!")
			        .replaceAll("\\%27", "'")
			        .replaceAll("\\%28", "(")
			        .replaceAll("\\%29", ")")
			        .replaceAll("\\%7E", "~");
		} catch (UnsupportedEncodingException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		return ret;
	}
	
	public static String getGeoJSONViewerRelativeLink(String linkText, String relLink) {
		String script = "<script>var a = document.createElement('a'); a.appendChild(document.createTextNode('"+linkText+"'));"
				+ "a.href = 'http://geojson.io/#data=data:text/x-url,'+encodeURIComponent("
				+ "new URL('"+relLink+"', document.baseURI).href); "
				+ "document.scripts[ document.scripts.length - 1 ].parentNode.appendChild( a );</script>";
		return script;
	}
	
	public static void main(String[] args) {
		System.out.println(getGeoJSONViewerRelativeLink("My Link", "map.geojson"));
	}

}
