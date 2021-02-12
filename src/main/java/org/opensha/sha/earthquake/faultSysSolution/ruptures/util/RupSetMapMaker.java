package org.opensha.sha.earthquake.faultSysSolution.ruptures.util;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.jfreechart.xyzPlot.XYZGraphPanel;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.ruptures.Jump;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.utils.GriddedSurfaceUtils;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemRupSet;

/**
 * Utility class for making map view plots of rupture sets
 * 
 * @author kevin
 *
 */
public class RupSetMapMaker {
	
	private final List<? extends FaultSection> subSects;
	private final Region region;
	
	private PlotCurveCharacterstics politicalBoundaryChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.GRAY);
	private PlotCurveCharacterstics sectOutlineChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.LIGHT_GRAY);
	private PlotCurveCharacterstics sectTraceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.DARK_GRAY);
	
	private float scalarThickness = 3f;
	private float jumpLineThickness = 3f;
	private boolean legendVisible = true;
	
	private boolean writePDFs = true;
	
	/*
	 * Things to plot
	 */
	// political boundaries
	private XY_DataSet[] politicalBoundaries;
	
	// section scalars
	private double[] scalars;
	private CPT scalarCPT;
	private String scalarLabel;
	
	// jumps (solid color)
	private List<Collection<Jump>> jumpLists;
	private List<Color> jumpColors;
	private List<String> jumpLabels;
	
	// jumps (scalar values)
	private List<Jump> scalarJumps;
	private List<Double> scalarJumpValues;
	private CPT scalarJumpsCPT;
	private String scalarJumpsLabel;
	
	/*
	 * Caches
	 */
	private List<RuptureSurface> sectSurfs;
	private List<Location> surfMiddles;

	public RupSetMapMaker(FaultSystemRupSet rupSet, Region region) {
		this.region = region;
		this.subSects = rupSet.getFaultSectionDataList();
		
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
	
	public void setWritePDFs(boolean writePDFs) {
		this.writePDFs = writePDFs;
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
	
	public PlotSpec buildPlot(String title) {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		// add political boundaries
		if (politicalBoundaries != null && politicalBoundaryChar != null) {
			for (XY_DataSet xy : politicalBoundaries) {
				funcs.add(xy);
				chars.add(politicalBoundaryChar);
			}
		}
		
		boolean hasLegend = false;
		
		// we'll plot fault traces if we don't have scalar values
		boolean doTraces = scalars == null;
		// plot section outlines on bottom
		for (int s=0; s<subSects.size(); s++) {
			FaultSection sect = subSects.get(s);
			RuptureSurface surf = getSectSurface(sect);
			
			XY_DataSet trace = new DefaultXY_DataSet();
			for (Location loc : surf.getEvenlyDiscritizedUpperEdge())
				trace.set(loc.getLongitude(), loc.getLatitude());
			
			if (s == 0)
				trace.setName("Fault Sections");
			
			if (sectOutlineChar != null && (sect.getAveDip() != 90d)) {
				if (sect.getAveDip() != 90) {
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
				} else if (sectTraceChar == null && doTraces) {
					// draw the trace this color
					funcs.add(trace);
					chars.add(sectOutlineChar);
				}
			}
			
			if (doTraces && sectTraceChar != null) {
				funcs.add(trace);
				chars.add(sectTraceChar);
			}
		}
		
		List<PaintScaleLegend> cptLegend = new ArrayList<>();
		
		// plot sect scalars
		if (scalars != null) {
			List<ComparablePairing<Double, XY_DataSet>> sortables = new ArrayList<>();
			for (int s=0; s<scalars.length; s++) {
				RuptureSurface surf = getSectSurface(subSects.get(s));
				XY_DataSet trace = new DefaultXY_DataSet();
				for (Location loc : surf.getEvenlyDiscritizedUpperEdge())
					trace.set(loc.getLongitude(), loc.getLatitude());
				sortables.add(new ComparablePairing<>(scalars[s], trace));
			}
			Collections.sort(sortables);
			for (ComparablePairing<Double, XY_DataSet> val : sortables) {
				float scalar = val.getComparable().floatValue();
				Color color = scalarCPT.getColor(scalar);
				
				funcs.add(val.getData());
				chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, scalarThickness, color));
			}
			
			cptLegend.add(buildCPTLegend(scalarCPT, scalarLabel));
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
				}
			}
		}
		
		// plot jump scalars
		if (scalarJumps != null) {
			List<ComparablePairing<Double, XY_DataSet>> sortables = new ArrayList<>();
			for (int j=0; j<scalarJumps.size(); j++) {
				double scalar = scalarJumpValues.get(j);
				Jump jump = scalarJumps.get(j);
				Location loc1 = getSectMiddle(jump.fromSection);
				Location loc2 = getSectMiddle(jump.toSection);
				XY_DataSet xy = new DefaultXY_DataSet();
				xy.set(loc1.getLongitude(), loc1.getLatitude());
				xy.set(loc2.getLongitude(), loc2.getLatitude());
				sortables.add(new ComparablePairing<>(scalar, xy));
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
	
	// these are tuned to match the default font sizes
	private static final int cpt_height = 85;
	private static final int legend_height = 20;
	private static final int title_height = 30;
	private static final int vert_buffer = 70;
	private static final int horz_buffer = 95;
	
	public void plot(File outputDir, String prefix, PlotSpec spec) throws IOException {
		plot(outputDir, prefix, spec, 800);
	}
	
	public void plot(File outputDir, String prefix, PlotSpec spec, int width) throws IOException {
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(24);
		gp.setBackgroundColor(Color.WHITE);
		
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
		TickUnits tus = new TickUnits();
		TickUnit tu = new NumberTickUnit(tick);
		tus.add(tu);
		gp.getXAxis().setStandardTickUnits(tus);
		gp.getYAxis().setStandardTickUnits(tus);
		
		int chartWidth = width - horz_buffer;
		int chartHeight = (int)(chartWidth * yRange.getLength()/xRange.getLength());
		int height = chartHeight + vert_buffer;
		if (spec.getTitle() != null && !spec.getTitle().isEmpty())
			height += title_height;
		if (spec.isLegendVisible())
			height += legend_height;
		if (spec.getSubtitles() != null)
			height += cpt_height*spec.getSubtitles().size();
		
		File file = new File(outputDir, prefix);
		gp.getChartPanel().setSize(width, height);
		gp.saveAsPNG(file.getAbsolutePath()+".png");
		if (writePDFs)
			gp.saveAsPDF(file.getAbsolutePath()+".pdf");
	}

}
