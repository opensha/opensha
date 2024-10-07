package org.opensha.commons.gui.plot;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.data.xyz.GeoDataSet;
import org.opensha.commons.data.xyz.GriddedGeoDataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.geo.json.FeatureCollection;
import org.opensha.commons.geo.json.FeatureProperties;
import org.opensha.commons.geo.json.Geometry;
import org.opensha.commons.geo.json.Geometry.LineString;
import org.opensha.commons.geo.json.Geometry.Polygon;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZPlotSpec;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

public class GeographicMapMaker {
	
	protected Region region;
	
	/*
	 * General line styles
	 */
	protected PlotCurveCharacterstics politicalBoundaryChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);
	protected PlotCurveCharacterstics sectOutlineChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.LIGHT_GRAY);
	protected PlotCurveCharacterstics sectTraceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.DARK_GRAY);
	protected PlotCurveCharacterstics sectPolyChar = new PlotCurveCharacterstics(PlotLineType.POLYGON_SOLID, 1f, new Color(127, 127, 127, 26));
	protected PlotCurveCharacterstics regionOutlineChar = null;
	protected boolean plotRectangularRegionOutlines = false;
	
	/*
	 * General plot settings
	 */
	protected boolean legendVisible = true;
	protected RectangleAnchor insetLegendLocation = null;
	protected boolean skipNaNs = false;
	protected boolean writePDFs = true;
	protected boolean writeGeoJSON = true;
	protected boolean reverseSort = false;
	protected Boolean absoluteSort = null;
	protected int widthDefault = 800;
	protected boolean axisLabels = true;
	protected boolean axisTicks = true;
	
	/*
	 * Fault sections and cached surfaces/traces
	 */
	protected List<? extends FaultSection> sects;
	protected Map<FaultSection, Integer> sectIndexMap;
	protected List<LocationList> sectUpperEdges;
	protected List<LocationList> sectPerimeters;
	protected boolean plotAseisReducedSurfaces = false;
	protected boolean fillSurfaces = false;
	protected boolean plotAllSectPolys = false;
	protected boolean plotProxySectPolys = true;
	
	/*
	 * General plot items
	 */
	protected XY_DataSet[] politicalBoundaries;
	
	/*
	 * Fault section colors/scalars
	 */
	// section scalars
	protected double[] sectScalars;
	protected CPT sectScalarCPT;
	protected String sectScalarLabel;
	protected float sectScalarThickness = 3f;
	protected PlotCurveCharacterstics sectNaNChar;

	// section colors
	protected List<Color> sectColors = null;
	protected List<? extends Double> sectColorComparables;
	protected CPT sectColorsCPT;
	protected String sectColorsLabel;

	// section chars
	protected List<PlotCurveCharacterstics> sectChars = null;
	protected List<? extends Double> sectCharsComparables;
	protected CPT sectCharsCPT;
	protected String sectCharsLabel;

	// highlighted sections
	protected Collection<FaultSection> highlightSections;
	protected PlotCurveCharacterstics highlightTraceChar;
	
	/*
	 * Jumps between fault sections
	 * 
	 */
	// jumps (solid color)
	protected List<Collection<Jump>> jumpLists;
	protected List<Color> jumpColors;
	protected List<String> jumpLabels;
	protected float jumpLineThickness = 3f;

	// jumps (scalar values)
	protected List<Jump> scalarJumps;
	protected List<Double> scalarJumpValues;
	protected CPT scalarJumpsCPT;
	protected String scalarJumpsLabel;
	
	/**
	 * Scatter data
	 */
	protected List<Location> scatterLocs;
	protected List<FeatureProperties> scatterProps;
	protected List<Double> scatterScalars;
	protected List<PlotCurveCharacterstics> scatterChars;
	protected CPT scatterScalarCPT;
	protected String scatterScalarLabel;
	protected Color scatterColor;
	protected PlotSymbol scatterSymbol = PlotSymbol.FILLED_TRIANGLE;
	protected float scatterSymbolWidth = 5f;
	protected PlotSymbol scatterOutline = PlotSymbol.TRIANGLE;
	protected Color scatterOutlineColor = new Color(0, 0, 0, 127);
	
	/**
	 * Lines
	 */
	protected List<LocationList> lines;
	protected Color linesColor;
	protected List<Color> lineColors;
	protected List<PlotCurveCharacterstics> lineChars;
	protected float lineThickness = 2f;
	
	/*
	 * Gridded (XYZ) data
	 */
	protected GeoDataSet xyzData;
	protected CPT xyzCPT;
	protected String xyzLabel;

	/*
	 * Inset region outlies
	 */
	protected Collection<Region> insetRegions;
	protected PlotCurveCharacterstics insetRegionOutlineChar;
	protected Color insetRegionFillColor;
	protected double insetRegionFillOpacity;

	/*
	 * Extra legend items
	 */
	protected List<String> customLegendLabels;
	protected List<PlotCurveCharacterstics> customLegendChars;
	
	/**
	 * Annotations
	 */
	private List<XYAnnotation> annotations;
	
	/*
	 * GeoJSON features
	 */
	protected List<Feature> features;
	
	/*
	 * Caches
	 */
	protected List<RuptureSurface> sectSurfs;
	protected List<Location> surfMiddles;
	
	public GeographicMapMaker(Region region) {
		this(region, PoliticalBoundariesData.loadDefaultOutlines(region));
	}
	
	public GeographicMapMaker(List<? extends FaultSection> sects) {
		this(buildBufferedRegion(sects));
		setFaultSections(sects);
	}
	
	public GeographicMapMaker(Region region, XY_DataSet[] politicalBoundaries) {
		this.region = region;
		this.politicalBoundaries = politicalBoundaries;
	}
	
	public static Region buildBufferedRegion(Collection<? extends FaultSection> sects) {
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (FaultSection sect : sects) {
			for (Location loc : bufferPerimLocsForSect(sect)) {
				latTrack.addValue(loc.getLatitude());
				lonTrack.addValue(loc.getLongitude());
			}
		}
		double minLat = Math.floor(latTrack.getMin()-0.05);
		double maxLat = Math.ceil(latTrack.getMax()+0.05);
		double minLon = Math.floor(lonTrack.getMin()-0.05);
		double maxLon = Math.ceil(lonTrack.getMax()+0.05);
		return new Region(new Location(minLat, minLon), new Location(maxLat, maxLon));
	}
	
	private static LocationList bufferPerimLocsForSect(FaultSection sect) {
		RuptureSurface surf = sect.getFaultSurface(1d);
		if (surf != null) {
			try {
				return surf.getPerimeter();
			} catch (Exception e) {}
			try {
				return surf.getEvenlyDiscritizedPerimeter();
			} catch (Exception e) {}
		}
		return sect.getFaultTrace();
	}
	
	public static Region buildBufferedRegion(Collection<? extends FaultSection> sects, double buffDistKM, boolean fullPerims) {
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (FaultSection sect : sects) {
			if (fullPerims) {
				for (Location loc : sect.getFaultSurface(1d).getPerimeter()) {
					latTrack.addValue(loc.getLatitude());
					lonTrack.addValue(loc.getLongitude());
				}
			} else {
				for (Location loc : sect.getFaultTrace()) {
					latTrack.addValue(loc.getLatitude());
					lonTrack.addValue(loc.getLongitude());
				}
			}
		}
		double minLat = latTrack.getMin();
		double maxLat = latTrack.getMax();
		double minLon = lonTrack.getMin();
		double maxLon = lonTrack.getMax();
		Location lowLeft = new Location(minLat, minLon);
		Location upRight = new Location(maxLat, maxLon);
		lowLeft = LocationUtils.location(lowLeft, 5d*Math.PI/4d, buffDistKM);
		upRight = LocationUtils.location(upRight, 1d*Math.PI/4d, buffDistKM);
		return new Region(lowLeft, upRight);
	}
	
	public void setFaultSections(List<? extends FaultSection> sects) {
		clearFaultSections();
		this.sects = sects;
		this.sectIndexMap = new HashMap<>();
		
		for (int s=0; s<sects.size(); s++)
			sectIndexMap.put(sects.get(s), s);
		
		Preconditions.checkState(sectIndexMap.size() == sects.size(), "Duplicate section ID?");
	}
	
	/**
	 * Clears fault section data and all stored scalars/colors for them
	 */
	public void clearFaultSections() {
		this.sects = null;
		this.sectIndexMap = null;
		this.sectUpperEdges = null;
		this.sectPerimeters = null;
		this.sectSurfs = null;
		this.surfMiddles = null;
		clearSectScalars();
		clearSectColors();
		clearSectHighlights();
		this.highlightSections = null;
	}
	
	public boolean hasFaultSections() {
		return sects != null && !sects.isEmpty();
	}
	
	private void checkHasSections() {
		Preconditions.checkState(hasFaultSections(), "Not supported (no fault sections)");
	}
	
	public void setRegion(Region region) {
		this.region = region;
	}

	public void setPoliticalBoundaryChar(PlotCurveCharacterstics politicalBoundaryChar) {
		this.politicalBoundaryChar = politicalBoundaryChar;
	}
	
	public void setRegionOutlineChar(PlotCurveCharacterstics regionOutlineChar) {
		this.regionOutlineChar = regionOutlineChar;
	}
	
	public void setRegionOutlineChar(PlotCurveCharacterstics regionOutlineChar, boolean plotIfRectangular) {
		this.regionOutlineChar = regionOutlineChar;
		this.plotRectangularRegionOutlines = plotIfRectangular;
	}

	public void setSectOutlineChar(PlotCurveCharacterstics sectOutlineChar) {
		this.sectOutlineChar = sectOutlineChar;
	}

	public void setSectTraceChar(PlotCurveCharacterstics sectTraceChar) {
		this.sectTraceChar = sectTraceChar;
	}
	
	public void setSectPolygonChar(PlotCurveCharacterstics sectPolyChar) {
		this.sectPolyChar = sectPolyChar;
	}

	public void setScalarThickness(float scalarThickness) {
		this.sectScalarThickness = scalarThickness;
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
	
	public void setLegendInset(boolean legendInset) {
		if (legendInset)
			this.insetLegendLocation = RectangleAnchor.TOP_RIGHT;
		else
			this.insetLegendLocation = null;
	}
	
	public void setLegendInset(RectangleAnchor insetLegendLocation) {
		this.insetLegendLocation = insetLegendLocation;
	}
	
	public void setSkipNaNs(boolean skipNaNs) {
		this.skipNaNs = skipNaNs;
	}
	
	public void setSectNaNChar(PlotCurveCharacterstics sectNaNChar) {
		this.sectNaNChar = sectNaNChar;
	}
	
	public void setCustomLegendItems(List<String> labels, List<PlotCurveCharacterstics> chars) {
		this.customLegendLabels = labels;
		this.customLegendChars = chars;
	}
	
	public void clearCustomLegendItems() {
		this.customLegendLabels = null;
		this.customLegendChars = null;
	}
	
	public void setAnnotations(List<? extends XYAnnotation> anns) {
		this.annotations = new ArrayList<>(anns);
	}
	
	public void addAnnotation(XYAnnotation ann) {
		if (this.annotations == null)
			this.annotations = new ArrayList<>();
		this.annotations.add(ann);
	}
	
	public void clearAnnotations() {
		this.annotations = null;
	}
	
	public void setSectHighlights(Collection<FaultSection> highlightSections, PlotCurveCharacterstics highlightTraceChar) {
		this.highlightSections = highlightSections;
		this.highlightTraceChar = highlightTraceChar;
	}
	
	public void clearSectHighlights() {
		this.highlightSections = null;
		this.highlightTraceChar = null;
	}
	
	public void plotInsetRegions(Collection<Region> regions, PlotCurveCharacterstics outlineChar,
			Color fillColor, double fillOpacity) {
		if (regions == null || regions.isEmpty()) {
			clearInsetRegions();
		} else {
			Preconditions.checkState(outlineChar != null || insetRegionFillColor != null);
			this.insetRegions = regions;
			this.insetRegionOutlineChar = outlineChar;
			this.insetRegionFillColor = fillColor;
			this.insetRegionFillOpacity = fillOpacity;
		}
	}
	
	public void clearInsetRegions() {
		this.insetRegions = null;
		this.insetRegionOutlineChar = null;
		this.insetRegionFillColor = null;
		this.insetRegionFillOpacity = Double.NaN;
	}
	
	public void setWritePDFs(boolean writePDFs) {
		this.writePDFs = writePDFs;
	}
	
	public void setWriteGeoJSON(boolean writeGeoJSON) {
		this.writeGeoJSON = writeGeoJSON;
	}

	public void setFillSurfaces(boolean fillSurfaces) {
		this.fillSurfaces = fillSurfaces;
	}

	public void setPlotAseisReducedSurfaces(boolean plotAseisReducedSurfaces) {
		this.plotAseisReducedSurfaces = plotAseisReducedSurfaces;
	}
	
	public void setPlotAllSectPolys(boolean plotAllSectPolys) {
		this.plotAllSectPolys = plotAllSectPolys;
	}

	public void setPlotProxySectPolys(boolean plotProxySectPolys) {
		this.plotProxySectPolys = plotProxySectPolys;
	}

	public void setReverseSort(boolean reverseSort) { 
		this.reverseSort = reverseSort;
	}
	
	public boolean isReverseSort() {
		return reverseSort;
	}
	
	public void setAbsoluteSort(boolean absoluteSort) {
		this.absoluteSort = absoluteSort;
	}
	
	public void plotSectScalars(List<Double> scalars, CPT cpt, String label) {
		plotSectScalars(Doubles.toArray(scalars), cpt, label);
	}
	
	public void plotSectScalars(double[] scalars, CPT cpt, String label) {
		checkHasSections();
		Preconditions.checkState(scalars.length == sects.size());
		Preconditions.checkNotNull(cpt);
		this.sectScalars = scalars;
		this.sectScalarCPT = cpt;
		this.sectScalarLabel = label;
		this.sectColors = null;
	}
	
	public void clearSectScalars() {
		this.sectScalars = null;
		this.sectScalarCPT = null;
		this.sectScalarLabel = null;
	}
	
	public void plotSectColors(List<Color> sectColors) {
		plotSectColors(sectColors, null, null);
	}
	
	public void plotSectColors(List<Color> sectColors, CPT colorsCPT, String colorsLabel) {
		plotSectColors(sectColors, colorsCPT, colorsLabel, null);
	}
	
	public void plotSectColors(List<Color> sectColors, CPT colorsCPT, String colorsLabel,
			List<? extends Double> sectColorComparables) {
		if (sectColors != null) {
			clearSectScalars();
			clearSectChars();
			checkHasSections();
			Preconditions.checkState(sectColors.size() == sects.size());
			if (sectColorComparables != null)
				Preconditions.checkState(sectColorComparables.size() == sects.size());
		}
		this.sectColors = sectColors;
		this.sectColorsCPT = colorsCPT;
		this.sectColorsLabel = colorsLabel;
		this.sectColorComparables = sectColorComparables;
	}
	
	public void plotSectChars(List<PlotCurveCharacterstics> sectChars) {
		plotSectChars(sectChars, null, null);
	}
	
	public void plotSectChars(List<PlotCurveCharacterstics> sectChars, CPT cpt, String label) {
		plotSectChars(sectChars, cpt, label, null);
	}
	
	public void plotSectChars(List<PlotCurveCharacterstics> sectChars, CPT cpt, String label,
			List<? extends Double> sectCharComparables) {
		if (sectChars != null) {
			clearSectScalars();
			clearSectColors();
			checkHasSections();
			Preconditions.checkState(sectChars.size() == sects.size());
			if (sectCharComparables != null)
				Preconditions.checkState(sectCharComparables.size() == sects.size());
		}
		this.sectChars = sectChars;
		this.sectCharsCPT = cpt;
		this.sectCharsLabel = label;
		this.sectCharsComparables = sectCharComparables;
	}
	
	public void clearSectColors() {
		this.sectColors = null;
		this.sectColorsCPT = null;
		this.sectColorsLabel = null;
		this.sectColorComparables = null;
	}
	
	public void clearSectChars() {
		this.sectChars = null;
		this.sectCharsCPT = null;
		this.sectCharsLabel = null;
		this.sectCharsComparables = null;
	}
	
	public void plotJumps(Collection<Jump> jumps, Color color, String label) {
		if (jumpLists == null) {
			jumpLists = new ArrayList<>();
			jumpColors = new ArrayList<>();
			jumpLabels = new ArrayList<>();
		}
		checkHasSections();
		jumpLists.add(jumps);
		jumpColors.add(color);
		jumpLabels.add(label);
	}
	
	public void clearJumps() {
		jumpLists = null;
		jumpColors = null;
		jumpLabels = null;
	}
	
	public void plotJumpScalars(Map<Jump, Double> jumpVals, CPT cpt, String label) {
		checkHasSections();
		List<Jump> jumps = new ArrayList<>();
		List<Double> values = new ArrayList<>();
		for (Jump jump : jumpVals.keySet()) {
			jumps.add(jump);
			values.add(jumpVals.get(jump));
		}
		plotJumpScalars(jumps, values, cpt, label);
	}
	
	public void plotJumpScalars(List<Jump> jumps, List<Double> values, CPT cpt, String label) {
		checkHasSections();
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
	
	public void plotScatters(List<Location> locs, Color color) {
		clearScatters();
		this.scatterLocs = locs;
		this.scatterColor = color;
	}
	
	public void plotScatters(List<Location> locs, List<PlotCurveCharacterstics> chars) {
		plotScatters(locs, chars, null, null);
	}
	
	public void plotScatters(List<Location> locs, List<PlotCurveCharacterstics> chars, CPT cptForLegend, String label) {
		plotScatters(locs, chars, null, cptForLegend, label);
	}
	
	public void plotScatters(List<Location> locs, List<PlotCurveCharacterstics> chars, PlotSymbol outlineSymbol) {
		plotScatters(locs, chars, outlineSymbol, null, null);
	}
	
	public void plotScatters(List<Location> locs, List<PlotCurveCharacterstics> chars, PlotSymbol outlineSymbol, CPT cptForLegend, String label) {
		clearScatters();
		Preconditions.checkState(locs.size() == chars.size());
		this.scatterLocs = locs;
		this.scatterChars = chars;
		this.scatterOutline = outlineSymbol;
		if (label != null)
			Preconditions.checkState(cptForLegend != null && label != null, "If you specify a label, must also specify a CPT");
		this.scatterScalarLabel = label;
		this.scatterScalarCPT = cptForLegend;
		this.scatterScalarLabel = label;
	}
	
	public void plotScatterScalars(List<Location> locs, List<Double> values, CPT cpt, String label) {
		clearScatters();
		Preconditions.checkState(locs.size() == values.size());
		Preconditions.checkNotNull(cpt);
		this.scatterLocs = locs;
		this.scatterScalars = values;
		this.scatterScalarCPT = cpt;
		this.scatterScalarLabel = label;
	}
	
	public void setScatterProperties(List<FeatureProperties> scatterProps) {
		Preconditions.checkNotNull(scatterLocs);
		Preconditions.checkState(scatterLocs.size() == scatterProps.size());
		this.scatterProps = scatterProps;
	}
	
	public void clearScatters() {
		this.scatterLocs = null;
		this.scatterProps = null;
		this.scatterScalars = null;
		this.scatterChars = null;
		this.scatterScalarCPT = null;
		this.scatterScalarLabel = null;
		this.scatterColor = null;
	}
	
	public void setScatterSymbol(PlotSymbol symbol, float width) {
		setScatterSymbol(symbol, width, null, null);
	}
	
	public void setScatterSymbol(PlotSymbol symbol, float width, PlotSymbol outline, Color outlineColor) {
		this.scatterSymbol = symbol;
		this.scatterSymbolWidth = width;
		this.scatterOutline = outline;
		this.scatterOutlineColor = outlineColor;
	}
	
	public void plotLines(List<LocationList> lines, Color color, float thickness) {
		clearLines();
		Preconditions.checkNotNull(lines);
		Preconditions.checkNotNull(color);
		this.lines = lines;
		this.linesColor = color;
		this.lineThickness = thickness;
	}
	
	public void plotLines(List<LocationList> lines, List<Color> colors, float thickness) {
		clearLines();
		Preconditions.checkNotNull(lines);
		Preconditions.checkNotNull(colors);
		Preconditions.checkState(lines.size() == colors.size());
		this.lines = lines;
		this.lineColors = colors;
		this.lineThickness = thickness;
	}
	
	public void plotLines(List<LocationList> lines, List<PlotCurveCharacterstics> chars) {
		clearLines();
		Preconditions.checkNotNull(lines);
		Preconditions.checkNotNull(chars);
		Preconditions.checkState(lines.size() == chars.size());
		this.lines = lines;
		this.lineChars = chars;
	}
	
	public void clearLines() {
		lines = null;
		lineColors = null;
		linesColor = null;
		lineChars = null;
	}
	
	public void plotXYZData(GeoDataSet xyzData, CPT xyzCPT, String xyzLabel) {
		Preconditions.checkNotNull(xyzData);
		Preconditions.checkNotNull(xyzCPT);
		this.xyzData = xyzData;
		this.xyzCPT = xyzCPT;
		this.xyzLabel = xyzLabel;
	}
	
	public void clearXYZData() {
		this.xyzData = null;
		this.xyzCPT = null;
		this.xyzLabel = null;
	}
	
	private RuptureSurface getSectSurface(FaultSection sect) {
		if (sectSurfs == null) {
			checkHasSections();
			List<RuptureSurface> surfs = new ArrayList<>();
			for (FaultSection s : sects)
				surfs.add(s.getFaultSurface(1d, false, plotAseisReducedSurfaces));
			this.sectSurfs = surfs;
		}
		return sectSurfs.get(sectIndexMap.get(sect));
	}
	
	private Location getSectMiddle(FaultSection sect) {
		if (surfMiddles == null) {
			List<Location> middles = new ArrayList<>();
			for (FaultSection s : sects)
				middles.add(GriddedSurfaceUtils.getSurfaceMiddleLoc(getSectSurface(s)));
			this.surfMiddles = middles;
		}
		return surfMiddles.get(sectIndexMap.get(sect));
	}
	
	private LocationList getPerimeter(FaultSection sect) {
		checkInitPerims();
		return sectPerimeters.get(sectIndexMap.get(sect));
	}
	
	private LocationList getUpperEdge(FaultSection sect) {
		checkInitPerims();
		return sectUpperEdges.get(sectIndexMap.get(sect));
	}
	
	private void checkInitPerims() {
		if (sectPerimeters == null) {
			synchronized (this) {
				if (sectPerimeters == null) {
					checkHasSections();
					List<LocationList> upperEdges = new ArrayList<>();
					List<LocationList> perimeters = new ArrayList<>();
					for (FaultSection sect : sects) {
						if (sect.getAveDip() == 90d) {
							// vertical, don't bother with perimeter
							upperEdges.add(sect.getFaultTrace());
							perimeters.add(null);
							continue;
						}
						
						// build our own simple perimeters here that have fewer points (not evenly discretized)
						FaultTrace trace = sect.getFaultTrace();
						
						LocationList upperEdge;
						LocationList lowerEdge;
						
						if (sect.getLowerFaultTrace() == null) {
							upperEdge = new LocationList();
							lowerEdge = new LocationList();
							// project down dip to get the lower trace
							double dipDirDeg = sect.getDipDirection(); // degrees
							double aveDipRad = Math.toRadians(sect.getAveDip()); // radians
							double upperDepth, ddw;
							if (plotAseisReducedSurfaces) {
								upperDepth = sect.getReducedAveUpperDepth();
								ddw = sect.getReducedDownDipWidth();
							} else {
								upperDepth = sect.getOrigAveUpperDepth();
								ddw = sect.getOrigDownDipWidth();
							}
							double horzToBottom = ddw * Math.cos( aveDipRad );
							double vertToBottom = ddw * Math.sin( aveDipRad );
							
							for (Location traceLoc : trace) {
								Location upperLoc = StirlingGriddedSurface.getTopLocation(
										traceLoc, upperDepth, aveDipRad, dipDirDeg);
								upperEdge.add(upperLoc);

								LocationVector dir = new LocationVector(dipDirDeg, horzToBottom, vertToBottom);

								Location lowerLoc = LocationUtils.location(upperLoc, dir);
								lowerEdge.add(lowerLoc);
							}
						} else {
							// we have our own lower trace
							upperEdge = new LocationList();
							upperEdge.addAll(sect.getFaultTrace());
							lowerEdge = new LocationList();
							lowerEdge.addAll(sect.getLowerFaultTrace());
						}
						
						LocationList perimeter = new LocationList();
						perimeter.addAll(upperEdge);
						Collections.reverse(lowerEdge);
						perimeter.addAll(lowerEdge);
						perimeter.add(perimeter.first());
						
						RuptureSurface surf = getSectSurface(sect);
						LocationList discrUpperEdge = surf.getEvenlyDiscritizedUpperEdge();
						if (discrUpperEdge.size() < upperEdge.size())
							upperEdge = discrUpperEdge;
						LocationList discrPerim = surf.getEvenlyDiscritizedPerimeter();
						if (discrPerim.size() < perimeter.size()) {
							if (!discrPerim.first().equals(discrPerim.last()) )
								discrPerim.add(discrPerim.first());
							perimeter = discrPerim;
						}
						upperEdges.add(upperEdge);
						perimeters.add(perimeter);
					}
					this.sectUpperEdges = upperEdges;
					this.sectPerimeters = perimeters;
				}
			}
		}
	}
	
	protected Feature surfFeature(FaultSection sect, PlotCurveCharacterstics pChar) {
		Polygon poly = new Polygon(getPerimeter(sect));
		FeatureProperties props = new FeatureProperties();
		props.set("name", sect.getSectionName());
		props.set("id", sect.getSectionId());
		if (sect.getParentSectionId() >= 0)
			props.set("parentID", sect.getParentSectionId());
		if (pChar.getLineType() != null) {
			props.set(FeatureProperties.STROKE_WIDTH_PROP, pChar.getLineWidth());
			props.set(FeatureProperties.STROKE_COLOR_PROP, pChar.getColor());
		}
		props.set(FeatureProperties.FILL_OPACITY_PROP, 0.05d);
		return new Feature(sect.getSectionName(), poly, props);
	}
	
	protected Feature traceFeature(FaultSection sect, PlotCurveCharacterstics pChar) {
		LineString line = new LineString(getUpperEdge(sect));
		FeatureProperties props = new FeatureProperties();
		props.set("name", sect.getSectionName());
		props.set("id", sect.getSectionId());
		if (sect.getParentSectionId() >= 0)
			props.set("parentID", sect.getParentSectionId());
		if (pChar.getLineType() != null) {
			props.set(FeatureProperties.STROKE_WIDTH_PROP, pChar.getLineWidth());
			props.set(FeatureProperties.STROKE_COLOR_PROP, pChar.getColor());
		}
		return new Feature(sect.getSectionName(), line, props);
	}
	
	protected class PlotBuilder {
		protected List<XY_DataSet> funcs;
		protected List<PlotCurveCharacterstics> chars; 
		protected List<Feature> features;
		protected boolean writeGeoJSON;
		protected List<PaintScaleLegend> cptLegend = new ArrayList<>();
		protected boolean hasLegend = false;
		
		protected Comparator<ComparablePairing<Double, ?>> buildComparator(CPT cpt) {
			boolean absoluteSort;
			if (GeographicMapMaker.this.absoluteSort != null)
				// use externally set absolute sort
				absoluteSort = GeographicMapMaker.this.absoluteSort;
			else if (cpt != null && cpt.getMinValue() < -0f && cpt.getMaxValue() > 0f
					&& cpt.getMinValue() == -cpt.getMaxValue()
					&& (cpt.getName() == null || (!cpt.getName().toLowerCase().contains("rainbow") && !cpt.getName().toLowerCase().contains("max"))))
				// we're symmetric about zero and not one of the standard rainbows, force absolute centering
				absoluteSort = true;
			else
				absoluteSort = false;
			return new Comparator<ComparablePairing<Double,?>>() {
				
				@Override
				public int compare(ComparablePairing<Double, ?> o1, ComparablePairing<Double, ?> o2) {
					Double d1 = o1.getComparable();
					Double d2 = o2.getComparable();
					if ((d1 == null || Double.isNaN(d1)) && (d2 == null || Double.isNaN(d2))) {
						return 0;
					} else if ((d1 == null || Double.isNaN(d1))) {
						return -1;
					} else if (d2 == null || Double.isNaN(d2)) {
						return 1;
					}
					if (absoluteSort) {
						d1 = Math.abs(d1);
						d2 = Math.abs(d2);
					}
					if (reverseSort)
						return Double.compare(d2, d1);
					return Double.compare(d1, d2);
				}
			};
		}
		
		protected void plotFirst() {
			// do nothing (can be overridden)
		}
		
		protected void plotPoliticalBoundaries() {
			// add political boundaries
			if (politicalBoundaries != null && politicalBoundaryChar != null) {
				for (XY_DataSet xy : politicalBoundaries) {
					funcs.add(xy);
					chars.add(politicalBoundaryChar);
				}
			}
		}
		
		protected void plotRegionOutlines() {
			if (regionOutlineChar != null && (plotRectangularRegionOutlines || !region.isRectangular())) {
				DefaultXY_DataSet outline = new DefaultXY_DataSet();
				for (Location loc : region.getBorder())
					outline.set(loc.getLongitude(), loc.getLatitude());
				outline.set(outline.get(0));
				
				funcs.add(outline);
				chars.add(regionOutlineChar);
			}
			
			if (insetRegions != null) {
				Preconditions.checkNotNull(insetRegionOutlineChar);
				
				PlotCurveCharacterstics regFillChar = null;
				if (insetRegionFillColor != null) {
					Color color = insetRegionFillColor;
					if (insetRegionFillOpacity != 1d)
						color = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(255d*insetRegionFillOpacity + 0.5d));
					regFillChar = new PlotCurveCharacterstics(PlotLineType.POLYGON_SOLID, 1f, color);
				}
				
				for (Region region : insetRegions) {
					DefaultXY_DataSet outline = new DefaultXY_DataSet();
					for (Location loc : region.getBorder())
						outline.set(loc.getLongitude(), loc.getLatitude());
					outline.set(outline.get(0)); // close polygon
					
//					funcs.add(outline);
//					charsasdf
					
					if (insetRegionFillColor != null) {
						funcs.add(0, outline);
						chars.add(0, regFillChar);
					}
					
					if (insetRegionOutlineChar != null) {
						funcs.add(outline);
						chars.add(insetRegionOutlineChar);
					}
					
					if (writeGeoJSON) {
						Feature feature = region.toFeature();
						FeatureProperties props = feature.properties;
						if (region.getName() != null)
							props.set("name", region.getName());
						if (insetRegionOutlineChar != null && insetRegionOutlineChar.getLineType() != null) {
							props.set(FeatureProperties.STROKE_WIDTH_PROP, insetRegionOutlineChar.getLineWidth());
							props.set(FeatureProperties.STROKE_COLOR_PROP, insetRegionOutlineChar.getColor());
						}
						if (insetRegionFillColor != null) {
							props.set(FeatureProperties.FILL_COLOR_PROP, insetRegionFillColor);
							props.set(FeatureProperties.FILL_OPACITY_PROP, insetRegionFillOpacity);
						}
						features.add(feature);
					}
				}
			}
		}
		
		protected void plotBeforeSects() {
			// do nothing (can be overridden)
		}
		
		protected void plotSects() {
			if (sects == null || sects.isEmpty())
				return;
			Range xRange = getXRange();
			Range yRange = getYRange();
			Region plotRegion = new Region(new Location(yRange.getLowerBound(), xRange.getLowerBound()), 
					new Location(yRange.getUpperBound(), xRange.getUpperBound()));
			HashSet<FaultSection> plotSects = new HashSet<>();
			for (FaultSection subSect : sects) {
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
			
			if (plotAllSectPolys || plotProxySectPolys) {
				// plot any polygons first
				boolean first = true;
				boolean hasNonProxy = false;
				for (FaultSection sect : sects)
					hasNonProxy = !sect.isProxyFault() && sect.getZonePolygon() != null;
				Region prevPoly = null;
				for (int s=0; s<sects.size(); s++) {
					FaultSection sect = sects.get(s);
					Region poly = sect.getZonePolygon();
					if (!plotSects.contains(sect) || poly == null)
						// outside of the region or no polygon, skip
						continue;
					if (!plotAllSectPolys && !sect.isProxyFault())
						// we're only plotting polys for proxies, and this isn't one, skip
						continue;
					if (prevPoly != null && prevPoly.equals(poly))
						// duplicate (sometimes subsects keep the same parent poly)
						continue;
					prevPoly = poly;
					
					// we need to plot it
					XY_DataSet xy = new DefaultXY_DataSet();
					for (Location loc : poly.getBorder())
						xy.set(loc.lon, loc.lat);
					if (!xy.get(0).equals(xy.get(xy.size()-1)))
						// close it
						xy.set(xy.get(0));
					
					if (first) {
						if (hasNonProxy && plotAllSectPolys)
							xy.setName("Fault Polygons");
						else
							xy.setName("Proxy Fault Polygons");
						first = false;
					}
					
					funcs.add(xy);
					chars.add(sectPolyChar);
					
					if (writeGeoJSON) {
						Feature feature = poly.toFeature();
						FeatureProperties props = feature.properties;
						props.set("name", sect.getSectionName());
						props.set("id", sect.getSectionId());
						if (sect.getParentSectionId() >= 0)
							props.set("parentID", sect.getParentSectionId());
						if (sectPolyChar.getLineType() == PlotLineType.POLYGON_SOLID) {
							props.set(FeatureProperties.STROKE_OPACITY_PROP, 0d);
							Color color = sectPolyChar.getColor();
							if (color.getAlpha() == 255) {
								props.set(FeatureProperties.FILL_COLOR_PROP, color);
							} else {
								double opacity = (double)color.getAlpha()/255d;
								props.set(FeatureProperties.FILL_OPACITY_PROP, opacity);
								props.set(FeatureProperties.FILL_COLOR_PROP, new Color(color.getRed(), color.getGreen(), color.getBlue()));
							}
						} else if (sectPolyChar.getLineType() != null) {
							props.set(FeatureProperties.FILL_OPACITY_PROP, 0d);
							props.set(FeatureProperties.STROKE_WIDTH_PROP, sectPolyChar.getLineWidth());
							Color color = sectPolyChar.getColor();
							if (color.getAlpha() == 255) {
								props.set(FeatureProperties.STROKE_COLOR_PROP, color);
							} else {
								double opacity = (double)color.getAlpha()/255d;
								props.set(FeatureProperties.STROKE_OPACITY_PROP, opacity);
								props.set(FeatureProperties.STROKE_COLOR_PROP, new Color(color.getRed(), color.getGreen(), color.getBlue()));
							}
						}
						features.add(feature);
					}
				}
			}

			// plot section outlines on bottom
			XY_DataSet prevTrace = null;
			XY_DataSet prevNanTrace = null;
			XY_DataSet prevOutline = null;
			Map<Integer, Feature> outlineFeatures = writeGeoJSON ? new HashMap<>() : null;
			for (int s=0; s<sects.size(); s++) {
				FaultSection sect = sects.get(s);
				if (!plotSects.contains(sect))
					continue;
				
				XY_DataSet trace = new DefaultXY_DataSet();
				for (Location loc : getUpperEdge(sect))
					trace.set(loc.getLongitude(), loc.getLatitude());
				
				if (s == 0)
					trace.setName("Fault Sections");
				
				// we'll plot fault traces if we don't have scalar values
				boolean doTraces = sectScalars == null && sectColors == null && sectChars == null;
				boolean isNaN = false;
				if (sectScalars != null)
					isNaN = Double.isNaN(sectScalars[s]);
				else if (sectColors != null)
					isNaN = sectColors.get(s) == null;
				else if (sectChars != null)
					isNaN = sectChars.get(s) == null;
				if (!doTraces && (!skipNaNs || sectNaNChar != null))
					doTraces = true;
				
				if (sectOutlineChar != null && (sect.getAveDip() != 90d)) {
					XY_DataSet outline = new DefaultXY_DataSet();
					for (Location loc : getPerimeter(sect))
						outline.set(loc.getLongitude(), loc.getLatitude());
					
					boolean reused = false;
					if (prevOutline != null && funcs.get(0) == prevOutline) {
						int matchIndex = -1;
						Point2D myFirst = outline.get(0);
						for (int i=0; i<prevOutline.size(); i++) {
							Point2D pt = prevOutline.get(i);
							if ((float)pt.getX() == (float)myFirst.getX() && (float)pt.getY() == (float)myFirst.getY()) {
								matchIndex = i;
								break;
							}
						}
						if (matchIndex >= 0) {
							reused = true;
							// reuse the existing one
							DefaultXY_DataSet merged = new DefaultXY_DataSet();
							for (int i=0; i<=matchIndex; i++)
								merged.set(prevOutline.get(i));
							for (int i=1; i<outline.size(); i++)
								merged.set(outline.get(i));
							for (int i=matchIndex+1; i<prevOutline.size(); i++)
								merged.set(prevOutline.get(i));
							funcs.set(0, merged);
							prevOutline = merged;
						}
					}
					
					if (!reused) {
						funcs.add(0, outline);
						prevOutline = outline;
						chars.add(0, sectOutlineChar);
						if (doTraces && sectTraceChar == null && s == 0)
							outline.setName("Fault Sections");
					}
					if (writeGeoJSON) {
						Feature feature = surfFeature(sect, sectOutlineChar);
						outlineFeatures.put(sect.getSectionId(), feature);
						features.add(0, feature);
					}
				}
				
				if (doTraces) {
					if (isNaN && sectNaNChar != null) {
						boolean reused = false;
						if (prevNanTrace != null) {
							Point2D prevLast = prevNanTrace.get(prevNanTrace.size()-1);
							Point2D newFirst = trace.get(0);
							if ((float)prevLast.getX() == (float)newFirst.getX() && (float)prevLast.getY() == (float)newFirst.getY()) {
								// reuse
								for (int i=1; i<trace.size(); i++)
									prevNanTrace.set(trace.get(i));
								reused = true;
							}
						}
						if (!reused) {
							funcs.add(trace);
							prevNanTrace = trace;
							chars.add(sectNaNChar);
						}
						if (writeGeoJSON)
							features.add(traceFeature(sect, sectNaNChar));
					} else if (sectTraceChar != null) {
						boolean reused = false;
						if (prevTrace != null) {
							Point2D prevLast = prevTrace.get(prevTrace.size()-1);
							Point2D newFirst = trace.get(0);
							if ((float)prevLast.getX() == (float)newFirst.getX() && (float)prevLast.getY() == (float)newFirst.getY()) {
								// reuse
								for (int i=1; i<trace.size(); i++)
									prevTrace.set(trace.get(i));
								reused = true;
							}
						}
						if (!reused) {
							funcs.add(trace);
							prevTrace = trace;
							chars.add(sectTraceChar);
						}
						if (writeGeoJSON)
							features.add(traceFeature(sect, sectTraceChar));
					}
				}
			}
			
			// plot sect scalars
			if (sectScalars != null) {
				List<ComparablePairing<Double, FaultSection>> sortables = new ArrayList<>();
				for (int s=0; s<sectScalars.length; s++)
					sortables.add(new ComparablePairing<>(sectScalars[s], sects.get(s)));
				Collections.sort(sortables, buildComparator(sectScalarCPT));
				for (ComparablePairing<Double, FaultSection> val : sortables) {
					float scalar = val.getComparable().floatValue();
					Color color = sectScalarCPT.getColor(scalar);
					FaultSection sect = val.getData();
					if (!plotSects.contains(sect) || (skipNaNs && Double.isNaN(val.getComparable())))
						continue;

					if (fillSurfaces && sect.getAveDip() != 90d) {
						XY_DataSet outline = new DefaultXY_DataSet();
						for (Location loc : getPerimeter(sect))
							outline.set(loc.getLongitude(), loc.getLatitude());

						PlotCurveCharacterstics fillChar = new PlotCurveCharacterstics(PlotLineType.POLYGON_SOLID, 0.5f, color);

						funcs.add(0, outline);
						chars.add(0, fillChar);
					} else {
						XY_DataSet trace = new DefaultXY_DataSet();
						for (Location loc : getUpperEdge(sect))
							trace.set(loc.getLongitude(), loc.getLatitude());
						
						funcs.add(trace);
						PlotCurveCharacterstics scalarChar = new PlotCurveCharacterstics(PlotLineType.SOLID, sectScalarThickness, color);
						chars.add(scalarChar);
						if (writeGeoJSON) {
							Feature feature = traceFeature(sect, scalarChar);
							if (sectScalarLabel != null && Float.isFinite(scalar)) {
								feature.properties.set(sectScalarLabel, scalar);
								// see if we have an outline feature as well
								Feature outlineFeature = outlineFeatures.get(sect.getSectionId());
								if (outlineFeature != null)
									outlineFeature.properties.set(sectScalarLabel, scalar);
							}
							features.add(feature);
						}
					}
				}
				
				if (sectScalarLabel != null && sectScalarCPT != null)
					cptLegend.add(buildCPTLegend(sectScalarCPT, sectScalarLabel));
			} else if (sectColors != null || sectChars != null) {
				List<? extends Double> comps = null;
				boolean colors;
				if (sectColors != null) {
					Preconditions.checkState(sectColors.size() == sects.size());
					comps = sectColorComparables;
					colors = true;
				} else {
					Preconditions.checkState(sectChars.size() == sects.size());
					comps = sectCharsComparables;
					colors = false;
				}
				List<Integer> sectOrder;
				if (comps != null) {
					Preconditions.checkState(comps.size() == sects.size());
					List<ComparablePairing<Double, Integer>> sortables = new ArrayList<>();
					for (int s=0; s<comps.size(); s++)
						sortables.add(new ComparablePairing<>(comps.get(s), s));
					Collections.sort(sortables, buildComparator(colors ? sectColorsCPT : sectCharsCPT));
					sectOrder = new ArrayList<>(sects.size());
					for (ComparablePairing<Double, Integer> sort : sortables)
						sectOrder.add(sort.getData());
				} else {
					sectOrder = new ArrayList<>(sects.size());
					for (int s=0; s<sects.size(); s++)
						sectOrder.add(s);
				}
				for (int s : sectOrder) {
					PlotCurveCharacterstics scalarChar;
					Color color;
					if (colors) {
						color = sectColors.get(s);
						if (color == null)
							continue;
						scalarChar = new PlotCurveCharacterstics(PlotLineType.SOLID, sectScalarThickness, color);
					} else {
						scalarChar = sectChars.get(s);
						if (scalarChar == null)
							continue;
						color = scalarChar.getColor();
					}
					FaultSection sect = sects.get(s);
					if (!plotSects.contains(sect))
						continue;

					if (fillSurfaces && sect.getAveDip() != 90d) {
						XY_DataSet outline = new DefaultXY_DataSet();
						LocationList perimeter = getPerimeter(sect);
						for (Location loc : perimeter)
							outline.set(loc.getLongitude(), loc.getLatitude());

						PlotCurveCharacterstics fillChar = new PlotCurveCharacterstics(PlotLineType.POLYGON_SOLID, 0.5f, color);

						funcs.add(0, outline);
						chars.add(0, fillChar);
					}

					XY_DataSet trace = new DefaultXY_DataSet();
					for (Location loc : getUpperEdge(sect))
						trace.set(loc.getLongitude(), loc.getLatitude());
					
					funcs.add(trace);
					chars.add(scalarChar);
					if (writeGeoJSON) {
						Feature feature = traceFeature(sect, scalarChar);
						features.add(feature);
					}
				}
				
				if (colors && sectColorsCPT != null)
					cptLegend.add(buildCPTLegend(sectColorsCPT, sectColorsLabel));
				if (!colors && sectCharsCPT != null)
					cptLegend.add(buildCPTLegend(sectCharsCPT, sectCharsLabel));
			}
			
			if (highlightSections != null && highlightTraceChar != null) {
				for (FaultSection hightlight : highlightSections) {
					XY_DataSet trace = new DefaultXY_DataSet();
					for (Location loc : getUpperEdge(hightlight))
						trace.set(loc.getLongitude(), loc.getLatitude());
					funcs.add(trace);
					chars.add(highlightTraceChar);
					if (writeGeoJSON)
						features.add(traceFeature(hightlight, highlightTraceChar));
				}
			}
		}
		
		protected void plotAfterSects() {
			// do nothing (can be overridden)
		}
		
		protected void plotJumps() {
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
							if (color.getAlpha() != 255)
								props.set(FeatureProperties.STROKE_OPACITY_PROP, (float)((double)color.getAlpha()/255d));
							props.set("label", label);
							props.set("fromSection", jump.fromSection.getName());
							props.set("toSection", jump.toSection.getName());
							props.set("distance", jump.distance);
							features.add(new Feature(line, props));
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
						Color color = scalarJumpsCPT.getColor((float)scalar);
						props.set(FeatureProperties.STROKE_COLOR_PROP, color);
						if (color.getAlpha() != 255)
							props.set(FeatureProperties.STROKE_OPACITY_PROP, (float)((double)color.getAlpha()/255d));
						if (Double.isFinite(scalar) && scalarJumpsLabel != null)
							props.set(scalarJumpsLabel, (float)scalar);
						props.set("fromSection", jump.fromSection.getName());
						props.set("toSection", jump.toSection.getName());
						props.set("distance", jump.distance);
						features.add(new Feature(line, props));
					}
				}
				Collections.sort(sortables, buildComparator(scalarJumpsCPT));
				for (ComparablePairing<Double, XY_DataSet> val : sortables) {
					float scalar = val.getComparable().floatValue();
					Color color = scalarJumpsCPT.getColor(scalar);

					funcs.add(val.getData());
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, jumpLineThickness, color));
				}

				if (scalarJumpsLabel != null && scalarJumpsCPT != null)
					cptLegend.add(buildCPTLegend(scalarJumpsCPT, scalarJumpsLabel));
			}
		}
		
		protected void plotScatters() {
			if (scatterLocs == null || scatterLocs.isEmpty())
				return;
			if (scatterScalars != null) {
				XY_DataSet outlines = scatterOutline == null ? null : new DefaultXY_DataSet();
				List<ComparablePairing<Double, XY_DataSet>> sortables = new ArrayList<>();
				for (int j=0; j<scatterLocs.size(); j++) {
					double scalar = scatterScalars.get(j);
					if (skipNaNs && Double.isNaN(scalar))
						continue;
					Location loc = scatterLocs.get(j);
					XY_DataSet xy = new DefaultXY_DataSet();
					xy.set(loc.getLongitude(), loc.getLatitude());
					sortables.add(new ComparablePairing<>(scalar, xy));
					
					if (writeGeoJSON) {
						Color color = scatterScalarCPT.getColor((float)scalar);
						FeatureProperties props = new FeatureProperties();
						if (scatterProps != null) {
							FeatureProperties extProps = scatterProps.get(j);
							if (extProps != null)
								props.putAll(extProps);
						}
						props.set(FeatureProperties.MARKER_COLOR_PROP, color);
						if (scatterSymbolWidth > 8f)
							props.set(FeatureProperties.MARKER_SIZE_PROP, FeatureProperties.MARKER_SIZE_LARGE);
						else if (scatterSymbolWidth > 4f)
							props.set(FeatureProperties.MARKER_SIZE_PROP, FeatureProperties.MARKER_SIZE_MEDIUM);
						else
							props.set(FeatureProperties.MARKER_SIZE_PROP, FeatureProperties.MARKER_SIZE_SMALL);
						features.add(new Feature(new Geometry.Point(loc), props));
					}
					
					if (outlines != null)
						outlines.set(xy.get(0));
				}
				Collections.sort(sortables, buildComparator(scatterScalarCPT));
				for (ComparablePairing<Double, XY_DataSet> val : sortables) {
					float scalar = val.getComparable().floatValue();
					Color color = scatterScalarCPT.getColor(scalar);
					
					funcs.add(val.getData());
					chars.add(new PlotCurveCharacterstics(scatterSymbol, scatterSymbolWidth, color));
				}
				if (outlines != null ) {
					funcs.add(outlines);
					chars.add(new PlotCurveCharacterstics(scatterOutline, scatterSymbolWidth, scatterOutlineColor));
				}
				
				if (scatterScalarLabel != null && scatterScalarCPT != null)
					cptLegend.add(buildCPTLegend(scatterScalarCPT, scatterScalarLabel));
			} else if (scatterChars != null) {
				for (int j=0; j<scatterLocs.size(); j++) {
					Location loc = scatterLocs.get(j);
					PlotCurveCharacterstics xyChar = scatterChars.get(j);
					
					XY_DataSet xy = new DefaultXY_DataSet();
					xy.set(loc.getLongitude(), loc.getLatitude());
					
					funcs.add(xy);
					chars.add(xyChar);
					
					if (writeGeoJSON) {
						Color color = xyChar.getColor();
						FeatureProperties props = new FeatureProperties();
						if (scatterProps != null) {
							FeatureProperties extProps = scatterProps.get(j);
							if (extProps != null)
								props.putAll(extProps);
						}
						props.set(FeatureProperties.MARKER_COLOR_PROP, color);
						float width = xyChar.getSymbolWidth();
						if (width > 8f)
							props.set(FeatureProperties.MARKER_SIZE_PROP, FeatureProperties.MARKER_SIZE_LARGE);
						else if (width > 4f)
							props.set(FeatureProperties.MARKER_SIZE_PROP, FeatureProperties.MARKER_SIZE_MEDIUM);
						else
							props.set(FeatureProperties.MARKER_SIZE_PROP, FeatureProperties.MARKER_SIZE_SMALL);
						features.add(new Feature(new Geometry.Point(loc), props));
					}
				}
				
				if (scatterScalarLabel != null && scatterScalarCPT != null)
					cptLegend.add(buildCPTLegend(scatterScalarCPT, scatterScalarLabel));
			} else {
				XY_DataSet outlines = scatterOutline == null ? null : new DefaultXY_DataSet();
				Preconditions.checkNotNull(scatterColor);
				XY_DataSet xy = new DefaultXY_DataSet();
				LocationList locs = new LocationList();
				for (int j=0; j<scatterLocs.size(); j++) {
					Location loc = scatterLocs.get(j);
					locs.add(loc);
					xy.set(loc.getLongitude(), loc.getLatitude());
					
					if (outlines != null)
						outlines.set(loc.getLongitude(), loc.getLatitude());
					
					if (writeGeoJSON && scatterProps != null) {
						FeatureProperties props = new FeatureProperties();
						if (scatterProps != null) {
							FeatureProperties extProps = scatterProps.get(j);
							if (extProps != null)
								props.putAll(extProps);
						}
						props.set(FeatureProperties.MARKER_COLOR_PROP, scatterColor);
						if (scatterSymbolWidth > 8f)
							props.set(FeatureProperties.MARKER_SIZE_PROP, FeatureProperties.MARKER_SIZE_LARGE);
						else if (scatterSymbolWidth > 4f)
							props.set(FeatureProperties.MARKER_SIZE_PROP, FeatureProperties.MARKER_SIZE_MEDIUM);
						else
							props.set(FeatureProperties.MARKER_SIZE_PROP, FeatureProperties.MARKER_SIZE_SMALL);
						features.add(new Feature(new Geometry.Point(loc), props));
					}
				}
				funcs.add(xy);
				chars.add(new PlotCurveCharacterstics(scatterSymbol, scatterSymbolWidth, scatterColor));
				if (outlines != null ) {
					funcs.add(outlines);
					chars.add(new PlotCurveCharacterstics(scatterOutline, scatterSymbolWidth, scatterOutlineColor));
				}
				if (writeGeoJSON && scatterProps == null) {
					FeatureProperties props = new FeatureProperties();
					
					props.set(FeatureProperties.MARKER_COLOR_PROP, scatterColor);
					if (scatterSymbolWidth > 8f)
						props.set(FeatureProperties.MARKER_SIZE_PROP, FeatureProperties.MARKER_SIZE_LARGE);
					else if (scatterSymbolWidth > 4f)
						props.set(FeatureProperties.MARKER_SIZE_PROP, FeatureProperties.MARKER_SIZE_MEDIUM);
					else
						props.set(FeatureProperties.MARKER_SIZE_PROP, FeatureProperties.MARKER_SIZE_SMALL);
					features.add(new Feature(new Geometry.MultiPoint(locs), props));
				}
			}
		}
		
		protected void plotLines() {
			if (lines == null || lines.isEmpty())
				return;
			if (lineColors != null) {
				for (int j=0; j<lines.size(); j++) {
					LocationList line = lines.get(j);
					Color color = lineColors.get(j);
					PlotCurveCharacterstics xyChar = new PlotCurveCharacterstics(PlotLineType.SOLID, lineThickness, color);
					
					XY_DataSet xy = new DefaultXY_DataSet();
					for (Location loc : line)
						xy.set(loc.getLongitude(), loc.getLatitude());
					
					funcs.add(xy);
					chars.add(xyChar);
					
					if (writeGeoJSON) {
						FeatureProperties props = new FeatureProperties();
						float width = xyChar.getLineWidth();
						props.set(FeatureProperties.STROKE_WIDTH_PROP, width);
						props.set(FeatureProperties.STROKE_COLOR_PROP, color);
						features.add(new Feature(new Geometry.LineString(line), props));
					}
				}
			} else if (lineChars != null) {
				for (int j=0; j<lines.size(); j++) {
					LocationList line = lines.get(j);
					PlotCurveCharacterstics xyChar = lineChars.get(j);
					
					XY_DataSet xy = new DefaultXY_DataSet();
					for (Location loc : line)
						xy.set(loc.getLongitude(), loc.getLatitude());
					
					funcs.add(xy);
					chars.add(xyChar);
					
					if (writeGeoJSON) {
						FeatureProperties props = new FeatureProperties();
						float width = xyChar.getLineWidth();
						props.set(FeatureProperties.STROKE_WIDTH_PROP, width);
						props.set(FeatureProperties.STROKE_COLOR_PROP, xyChar.getColor());
						features.add(new Feature(new Geometry.LineString(line), props));
					}
				}
			} else {
				PlotCurveCharacterstics xyChar = new PlotCurveCharacterstics(PlotLineType.SOLID, lineThickness, linesColor);
				for (int j=0; j<lines.size(); j++) {
					LocationList line = lines.get(j);
					
					XY_DataSet xy = new DefaultXY_DataSet();
					for (Location loc : line)
						xy.set(loc.getLongitude(), loc.getLatitude());
					
					funcs.add(xy);
					chars.add(xyChar);
				}
				if (writeGeoJSON) {
					FeatureProperties props = new FeatureProperties();
					float width = xyChar.getLineWidth();
					props.set(FeatureProperties.STROKE_WIDTH_PROP, width);
					props.set(FeatureProperties.STROKE_COLOR_PROP, linesColor);
					features.add(new Feature(new Geometry.MultiLineString(lines), props));
				}
			}
		}
		
		protected void plotLast() {
			// do nothing (can be overridden)
		}
		
		public PlotSpec buildPlot(String title) {
			funcs = new ArrayList<>();
			chars = new ArrayList<>();
			
			this.writeGeoJSON = GeographicMapMaker.this.writeGeoJSON && xyzData == null;
			if (writeGeoJSON)
				features = new ArrayList<>();
			
			cptLegend = new ArrayList<>();
			hasLegend = false;
			
			plotFirst();
			
			plotPoliticalBoundaries();
			
			plotRegionOutlines();
			
			plotBeforeSects();
			plotSects();
			plotAfterSects();
			
			plotJumps();
			
			plotLines();
			
			plotScatters();
			
			plotLast();
			
			String xAxisLabel = axisLabels ? "Longitude" : " ";
			String yAxisLabel = axisLabels ? "Latitude" : " ";
			
			PlotSpec spec;
			if (xyzData != null) {
				// XYZ data
				Preconditions.checkNotNull(xyzCPT);
				spec = new XYZPlotSpec(xyzData, funcs, chars, xyzCPT, title, xAxisLabel, yAxisLabel, xyzLabel);
				((XYZPlotSpec)spec).setCPTPosition(RectangleEdge.BOTTOM);
			} else {
				spec = new PlotSpec(funcs, chars, title, xAxisLabel, yAxisLabel);
			}
			
			if (customLegendLabels != null) {
				Preconditions.checkState(customLegendLabels.size() == customLegendChars.size());
				for (int i=0; i<customLegendLabels.size(); i++) {
					hasLegend = true;
					String label = customLegendLabels.get(i);
					PlotCurveCharacterstics pChar = customLegendChars.get(i);
					
					// create this will be off the map, only exists for the legend
					DefaultXY_DataSet xy = new DefaultXY_DataSet();
					xy.set(-1000d, -1000d);
					xy.set(-1001d, -1001d);
					xy.setName(label);
					
					funcs.add(xy);
					chars.add(pChar);
				}
			}
			
			spec.setLegendVisible(legendVisible && hasLegend);
			if (hasLegend) {
				if (insetLegendLocation == null)
					spec.setLegendInset(false);
				else
					spec.setLegendInset(insetLegendLocation);
			}
			
			for (PaintScaleLegend legend : cptLegend)
				spec.addSubtitle(legend);
			
			if (annotations != null)
				spec.setPlotAnnotations(annotations);
			
			return spec;
		}
	}
	
	protected PlotBuilder initPlotBuilder() {
		return new PlotBuilder();
	}
	
	public PlotSpec buildPlot(String title) {
		PlotBuilder builder = initPlotBuilder();
		PlotSpec spec = builder.buildPlot(title);
		this.features = builder.features;
		
		return spec;
	}
	
	private double getPlotRegionBuffer() {
		return regionOutlineChar == null ? 0d : 0.05d;
	}
	
	public Range getXRange() {
		double buffer = getPlotRegionBuffer();
		double minLon = region.getMinLon()-buffer;
		double maxLon = region.getMaxLon()+buffer;
		if (xyzData != null && xyzData instanceof GriddedGeoDataSet) {
			GriddedGeoDataSet geoXYZ = (GriddedGeoDataSet)xyzData;
			if (region.equalsRegion(geoXYZ.getRegion())) {
				double spacing = geoXYZ.getRegion().getLonSpacing();
				minLon = Math.min(minLon, geoXYZ.getMinLon()-0.5*spacing);
				maxLon = Math.max(maxLon, geoXYZ.getMaxLon()+0.5*spacing);
			}
		}
		return new Range(minLon, maxLon);
	}
	
	public Range getYRange() {
		double buffer = getPlotRegionBuffer();
		double minLat = region.getMinLat()-buffer;
		double maxLat = region.getMaxLat()+buffer;
		if (xyzData != null && xyzData instanceof GriddedGeoDataSet) {
			GriddedGeoDataSet geoXYZ = (GriddedGeoDataSet)xyzData;
			if (region.equalsRegion(geoXYZ.getRegion())) {
				double spacing = geoXYZ.getRegion().getLatSpacing();
				minLat = Math.min(minLat, geoXYZ.getMinLat()-0.5*spacing);
				maxLat = Math.max(maxLat, geoXYZ.getMaxLat()+0.5*spacing);
			}
		}
		return new Range(minLat, maxLat);
	}
	
	public static PaintScaleLegend buildCPTLegend(CPT cpt, String label) {
		double cptLen = cpt.getMaxValue() - cpt.getMinValue();
		double cptTick = cpt.getPreferredTickInterval();
		if (!Double.isFinite(cptTick)) {
			if (cptLen > 5000)
				cptTick = 1000;
			else if (cptLen > 2500)
				cptTick = 500;
			else if (cptLen > 1000)
				cptTick = 250;
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
		}
		return GraphPanel.getLegendForCPT(cpt, label, PLOT_PREFS_DEFAULT.getAxisLabelFontSize(),
				PLOT_PREFS_DEFAULT.getTickLabelFontSize(), cptTick, RectangleEdge.BOTTOM);
	}
	
	public void setDefaultPlotWidth(int widthDefault) {
		this.widthDefault = widthDefault;
	}
	
	public int getDefaultPlotWidth() {
		return this.widthDefault;
	}
	
	public void setAxisLabelsVisible(boolean axisLabels) {
		this.axisLabels = axisLabels;
	}

	public void setAxisTicksVisible(boolean axisTicks) {
		this.axisTicks = axisTicks;
	}

	public void plot(File outputDir, String prefix, String title) throws IOException {
		plot(outputDir, prefix, buildPlot(title), widthDefault);
	}
	
	public void plot(File outputDir, String prefix, String title, int width) throws IOException {
		plot(outputDir, prefix, buildPlot(title), width);
	}
	
	public void plot(File outputDir, String prefix, PlotSpec spec) throws IOException {
		plot(outputDir, prefix, spec, widthDefault);
	}
	
	public static PlotPreferences PLOT_PREFS_DEFAULT = PlotUtils.getDefaultFigurePrefs();
	
	public void plot(File outputDir, String prefix, PlotSpec spec, int width) throws IOException {
		plot(outputDir, prefix, spec, width, null);
	}
	
	public void plot(File outputDir, String prefix, PlotSpec spec, int width, Consumer<? super HeadlessGraphPanel> customizer)
			throws IOException {
		HeadlessGraphPanel gp = new HeadlessGraphPanel(PLOT_PREFS_DEFAULT);
		
		Range xRange = getXRange();
		Range yRange = getYRange();
		
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		if (axisTicks) {
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
		} else {
			gp.getPlot().setDomainGridlinesVisible(false);
			gp.getPlot().setRangeGridlinesVisible(false);
			gp.getXAxis().setTickLabelsVisible(false);
			gp.getXAxis().setTickMarksVisible(false);
			gp.getXAxis().setMinorTickMarksVisible(false);
			gp.getYAxis().setTickLabelsVisible(false);
			gp.getYAxis().setTickMarksVisible(false);
			gp.getYAxis().setMinorTickMarksVisible(false);
		}
		
		if (customizer != null)
			customizer.accept(gp);
		
		PlotUtils.writePlots(outputDir, prefix, gp, width, true, true, writePDFs, false);
		
		if (writeGeoJSON && features != null && !features.isEmpty() && !(spec instanceof XYZPlotSpec)) {
			FeatureCollection features = new FeatureCollection(this.features);
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

}
