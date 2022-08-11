package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
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
import org.opensha.commons.gui.plot.GraphPanel;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotPreferences;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.GeoJSONFaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;
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
	private List<LocationList> sectUpperEdges;
	private List<LocationList> sectPerimeters;
	private Region region;
	
	private PlotCurveCharacterstics politicalBoundaryChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);
	private PlotCurveCharacterstics sectOutlineChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.LIGHT_GRAY);
	private PlotCurveCharacterstics sectTraceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.DARK_GRAY);
	
	private float scalarThickness = 3f;
	private float jumpLineThickness = 3f;
	private boolean legendVisible = true;
	private boolean legendInset = false;
	private boolean fillSurfaces = false;
	
	private boolean plotAseisReducedSurfaces = false;
	
	private boolean writePDFs = true;
	private boolean writeGeoJSON = true;
	
	private List<Feature> sectFeatures = null;
	private List<Feature> jumpFeatures = null;
	private List<Feature> scatterFeatures = null;
	
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
	
	// section colors
	private List<Color> sectColors = null;
	private CPT colorsCPT;
	private String colorsLabel;
	
	// jumps (solid color)
	private List<Collection<Jump>> jumpLists;
	private List<Color> jumpColors;
	private List<String> jumpLabels;
	
	// jumps (scalar values)
	private List<Jump> scalarJumps;
	private List<Double> scalarJumpValues;
	private CPT scalarJumpsCPT;
	private String scalarJumpsLabel;
	
	// scatters
	private List<Location> scatterLocs;
	private List<Double> scatterScalars;
	private CPT scatterScalarCPT;
	private String scatterScalarLabel;
	private Color scatterColor;
	private PlotSymbol scatterSymbol = PlotSymbol.FILLED_TRIANGLE;
	private float scatterSymbolWidth = 5f;
	private PlotSymbol scatterOutline = PlotSymbol.TRIANGLE;
	private Color scatterOutlineColor = new Color(0, 0, 0, 127);
	
	private Collection<FaultSection> highlightSections;
	private PlotCurveCharacterstics highlightTraceChar;
	
	private boolean reverseSort = false;
	
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
		politicalBoundaries = PoliticalBoundariesData.loadDefaultOutlines(region);
	}
	
	public static Region buildBufferedRegion(Collection<? extends FaultSection> subSects) {
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
	
	public static Region buildBufferedRegion(Collection<? extends FaultSection> subSects, double buffDistKM, boolean fullPerims) {
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (FaultSection sect : subSects) {
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
	
	public void setLegendInset(boolean legendInset) {
		this.legendInset = legendInset;
	}
	
	public void setSkipNaNs(boolean skipNaNs) {
		this.skipNaNs = skipNaNs;
	}
	
	public void highLightSections(Collection<FaultSection> highlightSections, PlotCurveCharacterstics highlightTraceChar) {
		this.highlightSections = highlightSections;
		this.highlightTraceChar = highlightTraceChar;
	}
	
	public void clearHighlights() {
		this.highlightSections = null;
		this.highlightTraceChar = null;
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
	
	public void setReverseSort(boolean reverseSort) { 
		this.reverseSort = reverseSort;
	}
	
	public boolean isReverseSort() {
		return reverseSort;
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
		this.sectColors = null;
	}
	
	public void clearSectScalars() {
		this.scalars = null;
		this.scalarCPT = null;
		this.scalarLabel = null;
	}
	
	public void plotSectColors(List<Color> sectColors) {
		plotSectColors(sectColors, null, null);
	}
	
	public void plotSectColors(List<Color> sectColors, CPT colorsCPT, String colorsLabel) {
		if (sectColors != null) {
			clearSectScalars();
			Preconditions.checkState(sectColors.size() == subSects.size());
		}
		this.sectColors = sectColors;
		this.colorsCPT = colorsCPT;
		this.colorsLabel = colorsLabel;
	}
	
	public void clearSectColors() {
		this.sectColors = null;
		this.colorsCPT = null;
		this.colorsLabel = null;
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
	
	public void plotJumpScalars(Map<Jump, Double> jumpVals, CPT cpt, String label) {
		List<Jump> jumps = new ArrayList<>();
		List<Double> values = new ArrayList<>();
		for (Jump jump : jumpVals.keySet()) {
			jumps.add(jump);
			values.add(jumpVals.get(jump));
		}
		plotJumpScalars(jumps, values, cpt, label);
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
	
	public void plotScatters(List<Location> locs, Color color) {
		clearScatters();
		this.scatterLocs = locs;
		this.scatterColor = color;
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
	
	public void clearScatters() {
		this.scatterLocs = null;
		this.scatterScalars = null;
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
	
	private RuptureSurface getSectSurface(FaultSection sect) {
		if (sectSurfs == null) {
			List<RuptureSurface> surfs = new ArrayList<>();
			for (FaultSection s : subSects)
				surfs.add(s.getFaultSurface(1d, false, plotAseisReducedSurfaces));
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
	
	private LocationList getPerimeter(FaultSection sect) {
		checkInitPerims();
		return sectPerimeters.get(sect.getSectionId());
	}
	
	private LocationList getUpperEdge(FaultSection sect) {
		checkInitPerims();
		return sectUpperEdges.get(sect.getSectionId());
	}
	
	private void checkInitPerims() {
		if (sectPerimeters == null) {
			synchronized (this) {
				if (sectPerimeters == null) {
					List<LocationList> upperEdges = new ArrayList<>();
					List<LocationList> perimeters = new ArrayList<>();
					for (FaultSection subSect : subSects) {
						// build our own simple perimeters here that have fewer points (not evenly discretized
						FaultTrace trace = subSect.getFaultTrace();
						
						LocationList upperEdge = new LocationList();
						LocationList lowerEdge = new LocationList();
						
						double dipDirDeg = subSect.getDipDirection(); // degrees
						double aveDipRad = Math.toRadians(subSect.getAveDip()); // radians
						double upperDepth, ddw;
						if (plotAseisReducedSurfaces) {
							upperDepth = subSect.getReducedAveUpperDepth();
							ddw = subSect.getReducedDownDipWidth();
						} else {
							upperDepth = subSect.getOrigAveUpperDepth();
							ddw = subSect.getOrigDownDipWidth();
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
						
						LocationList perimeter = new LocationList();
						perimeter.addAll(upperEdge);
						Collections.reverse(lowerEdge);
						perimeter.addAll(lowerEdge);
						perimeter.add(perimeter.first());
						
						RuptureSurface surf = getSectSurface(subSect);
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
	
	private Feature surfFeature(FaultSection sect, PlotCurveCharacterstics pChar) {
		Polygon poly = new Polygon(getPerimeter(sect));
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
		LineString line = new LineString(getUpperEdge(sect));
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
			scatterFeatures = new ArrayList<>();
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
		XY_DataSet prevTrace = null;
		XY_DataSet prevOutline = null;
		for (int s=0; s<subSects.size(); s++) {
			FaultSection sect = subSects.get(s);
			if (!plotSects.contains(sect))
				continue;
			
			XY_DataSet trace = new DefaultXY_DataSet();
			for (Location loc : getUpperEdge(sect))
				trace.set(loc.getLongitude(), loc.getLatitude());
			
			if (s == 0)
				trace.setName("Fault Sections");
			
			// we'll plot fault traces if we don't have scalar values
			boolean doTraces = scalars == null && sectColors == null;
			if (!doTraces && skipNaNs &&
					(scalars != null && Double.isNaN(scalars[s]) || sectColors != null && sectColors.get(s) == null))
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
				if (writeGeoJSON)
					sectFeatures.add(0, surfFeature(sect, sectOutlineChar));
			}
			
			if (doTraces && sectTraceChar != null) {
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
					sectFeatures.add(traceFeature(sect, sectTraceChar));
			}
		}
		
		List<PaintScaleLegend> cptLegend = new ArrayList<>();
		
		Comparator<ComparablePairing<Double, ?>> comparator = new Comparator<ComparablePairing<Double,?>>() {
			
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
				if (reverseSort)
					return Double.compare(d2, d1);
				return Double.compare(d1, d2);
			}
		};
		
		// plot sect scalars
		if (scalars != null) {
			List<ComparablePairing<Double, FaultSection>> sortables = new ArrayList<>();
			for (int s=0; s<scalars.length; s++)
				sortables.add(new ComparablePairing<>(scalars[s], subSects.get(s)));
			Collections.sort(sortables, comparator);
			for (ComparablePairing<Double, FaultSection> val : sortables) {
				float scalar = val.getComparable().floatValue();
				Color color = scalarCPT.getColor(scalar);
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
				}

				XY_DataSet trace = new DefaultXY_DataSet();
				for (Location loc : getUpperEdge(sect))
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
		} else if (sectColors != null) {
			Preconditions.checkState(sectColors.size() == subSects.size());
			for (int s=0; s<sectColors.size(); s++) {
				Color color = sectColors.get(s);
				if (color == null)
					continue;
				FaultSection sect = subSects.get(s);

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
				PlotCurveCharacterstics scalarChar = new PlotCurveCharacterstics(PlotLineType.SOLID, scalarThickness, color);
				chars.add(scalarChar);
				if (writeGeoJSON) {
					Feature feature = traceFeature(sect, scalarChar);
					sectFeatures.add(feature);
				}
			}
			
			if (colorsCPT != null)
				cptLegend.add(buildCPTLegend(colorsCPT, colorsLabel));
		}
		
		if (highlightSections != null && highlightTraceChar != null) {
			for (FaultSection hightlight : highlightSections) {
				XY_DataSet trace = new DefaultXY_DataSet();
				for (Location loc : getUpperEdge(hightlight))
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
					if (Double.isFinite(scalar) && scalarJumpsLabel != null)
						props.set(scalarJumpsLabel, (float)scalar);
					props.set("fromSection", jump.fromSection.getName());
					props.set("toSection", jump.toSection.getName());
					props.set("distance", jump.distance);
					jumpFeatures.add(new Feature(line, props));
				}
			}
			Collections.sort(sortables, comparator);
			for (ComparablePairing<Double, XY_DataSet> val : sortables) {
				float scalar = val.getComparable().floatValue();
				Color color = scalarJumpsCPT.getColor(scalar);
				
				funcs.add(val.getData());
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, jumpLineThickness, color));
			}
			
			if (scalarJumpsLabel != null)
				cptLegend.add(buildCPTLegend(scalarJumpsCPT, scalarJumpsLabel));
		}
		
		// plot scatter scalars
		if (scatterLocs != null) {
			XY_DataSet outlines = scatterOutline == null ? null : new DefaultXY_DataSet();
			if (scatterScalars != null) {
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
						props.set(FeatureProperties.FILL_OPACITY_PROP, 1d);
						props.set(FeatureProperties.FILL_COLOR_PROP, color);
						if (scatterOutline != null)
							props.set(FeatureProperties.STROKE_OPACITY_PROP, 0.5d);
						else
							props.set(FeatureProperties.STROKE_OPACITY_PROP, 0d);
						props.set(FeatureProperties.STROKE_WIDTH_PROP, scatterSymbolWidth);
						scatterFeatures.add(new Feature(new Geometry.Point(loc), props));
					}
					
					if (outlines != null)
						outlines.set(xy.get(0));
				}
				Collections.sort(sortables, comparator);
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
				
				if (scatterScalarLabel != null)
					cptLegend.add(buildCPTLegend(scatterScalarCPT, scatterScalarLabel));
			} else {
				Preconditions.checkNotNull(scatterColor);
				XY_DataSet xy = new DefaultXY_DataSet();
				LocationList locs = new LocationList();
				for (int j=0; j<scatterLocs.size(); j++) {
					Location loc = scatterLocs.get(j);
					locs.add(loc);
					xy.set(loc.getLongitude(), loc.getLatitude());
					
					if (outlines != null)
						outlines.set(loc.getLongitude(), loc.getLatitude());
				}
				funcs.add(xy);
				chars.add(new PlotCurveCharacterstics(scatterSymbol, scatterSymbolWidth, scatterColor));
				if (outlines != null ) {
					funcs.add(outlines);
					chars.add(new PlotCurveCharacterstics(scatterOutline, scatterSymbolWidth, scatterOutlineColor));
				}
				if (writeGeoJSON) {
					FeatureProperties props = new FeatureProperties();
					props.set(FeatureProperties.FILL_OPACITY_PROP, 1d);
					props.set(FeatureProperties.FILL_COLOR_PROP, scatterColor);
					if (scatterOutline != null)
						props.set(FeatureProperties.STROKE_OPACITY_PROP, 0.5d);
					else
						props.set(FeatureProperties.STROKE_OPACITY_PROP, 0d);
					props.set(FeatureProperties.STROKE_WIDTH_PROP, scatterSymbolWidth);
					scatterFeatures.add(new Feature(new Geometry.MultiPoint(locs), props));
				}
			}
		}
		
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Longitude", "Latitude");
		spec.setLegendVisible(legendVisible && hasLegend);
		spec.setLegendInset(legendInset && hasLegend);
		
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
	
	public static PaintScaleLegend buildCPTLegend(CPT cpt, String label) {
		double cptLen = cpt.getMaxValue() - cpt.getMinValue();
		double cptTick;
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
		return GraphPanel.getLegendForCPT(cpt, label, PLOT_PREFS_DEFAULT.getAxisLabelFontSize(),
				PLOT_PREFS_DEFAULT.getTickLabelFontSize(), cptTick, RectangleEdge.BOTTOM);
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
	
	public static PlotPreferences PLOT_PREFS_DEFAULT = PlotUtils.getDefaultFigurePrefs();
	
	public void plot(File outputDir, String prefix, PlotSpec spec, int width) throws IOException {
		HeadlessGraphPanel gp = new HeadlessGraphPanel(PLOT_PREFS_DEFAULT);
		
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
			
			plotFeatures.addAll(scatterFeatures);
			
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
	
	public static void main(String[] args) throws IOException {
//		System.out.println(getGeoJSONViewerRelativeLink("My Link", "map.geojson"));
//		System.out.println(getGeoJSONViewerLink("http://opensha.usc.edu/ftp/kmilner/mrkdown/rupture-sets/rsqsim_4983_stitched_m6.5_skip65000_sectArea0.5/comp_fm3_1_ucerf3/resources/conn_rates_m6.5.geojson"));
		
		List<GeoJSONFaultSection> sects = GeoJSONFaultReader.readFaultSections(new File("/tmp/GEOLOGIC_sub_sects.geojson"));
		
		RupSetMapMaker mapMaker = new RupSetMapMaker(sects, buildBufferedRegion(sects));
		
		CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(-1, 1);
		double[] scalars = new double[sects.size()];
		for (int s=0; s<scalars.length; s++) {
			if (Math.random() < 0.3)
				scalars[s] = Double.NaN;
			else
				scalars[s] = 2d*Math.random()-1;
		}
		mapMaker.setReverseSort(true);
		mapMaker.plotSectScalars(scalars, cpt, "Label");
		mapMaker.plot(new File("/tmp"), "nan_test", " ");
	}

}
