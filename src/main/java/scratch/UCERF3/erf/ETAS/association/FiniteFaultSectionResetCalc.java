package scratch.UCERF3.erf.ETAS.association;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.DocumentException;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.data.Range;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotSymbol;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.parsers.UCERF3_CatalogParser;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.utils.FaultSystemIO;

public class FiniteFaultSectionResetCalc {
	
	private FaultSystemRupSet rupSet;
	private double minFractionalAreaInPolygon;
	
	private double faultBuffer;
	private Region[] polygons;
	private double[] sectAreas;
	private EvenlyGriddedSurface[] sectSurfaces;
	private XY_DataSet[] sectPolygonXYs;
	
	/**
	 * @param rupSet
	 * @param minFractionalAreaInPolygon sections will be reset if the area of a rupture within a section's polygon is at least
	 * this fraction of that section's area 
	 */
	public FiniteFaultSectionResetCalc(FaultSystemRupSet rupSet, double minFractionalAreaInPolygon) {
		this(rupSet, minFractionalAreaInPolygon, InversionTargetMFDs.FAULT_BUFFER);
	}
	
	/**
	 * @param rupSet
	 * @param minFractionalAreaInPolygon sections will be reset if the area of a rupture within a section's polygon is at least
	 * this fraction of that section's area 
	 */
	public FiniteFaultSectionResetCalc(FaultSystemRupSet rupSet, double minFractionalAreaInPolygon, double faultBuffer) {
		this.rupSet = rupSet;
		this.minFractionalAreaInPolygon = minFractionalAreaInPolygon;
		
		setFaultBuffer(faultBuffer);
	}
	
	public void setFaultBuffer(double faultBuffer) {
		this.faultBuffer = faultBuffer;
		FaultPolyMgr polyManager = FaultPolyMgr.create(rupSet.getFaultSectionDataList(),
				faultBuffer);
		
		polygons = new Region[rupSet.getNumSections()];
		sectAreas = new double[rupSet.getNumSections()];
		for (int s=0; s<rupSet.getNumSections(); s++) {
			polygons[s] = polyManager.getPoly(s);
			sectAreas[s] = rupSet.getAreaForSection(s) * 1e-6; // convert to km^2
		}
	}
	
	public void setMinFractionalAreaInPolygon(double minFractionalAreaInPolygon) {
		this.minFractionalAreaInPolygon = minFractionalAreaInPolygon;
	}
	
	private synchronized EvenlyGriddedSurface getSectSurface(int index) {
		if (sectSurfaces == null)
			sectSurfaces = new EvenlyGriddedSurface[rupSet.getNumSections()];
		if (sectSurfaces[index] == null)
			sectSurfaces[index] = rupSet.getFaultSectionData(index).getStirlingGriddedSurface(1d);
		return sectSurfaces[index];
	}
	
	/**
	 * @param surf
	 * @return array of the rupture area in each subsection polygon, or null if the rupture does not intersect any polygons
	 */
	public double[] getAreaInSectionPolygons(RuptureSurface surf) {
		double[] areas = new double[rupSet.getNumRuptures()];
		boolean any = false;
		for (int s=0; s<rupSet.getNumSections(); s++) {
			 areas[s] = surf.getAreaInsideRegion(getPolygon(s));
			 any = any || areas[s] > 0;
		}
		if (!any)
			return null;
		return areas;
	}
	
	/**
	 * @param surf
	 * @param areas
	 * @return array of the section distances (indexed by subsection index) for each fault section where area > 0, null otherwise
	 */
	public SectRupDistances[] getSectRupDistances(RuptureSurface surf, double[] areas) {
		if (areas == null)
			return null;
		SectRupDistances[] dists = new SectRupDistances[rupSet.getNumRuptures()];
		for (int s=0; s<rupSet.getNumSections(); s++) {
			 if (areas[s] > 0)
				 dists[s] = new SectRupDistances(getSectSurface(s), surf);
		}
		return dists;
	}

	public List<FaultSectionPrefData> getMatchingSections(RuptureSurface surf) {
		double[] areas = getAreaInSectionPolygons(surf);
		// TODO add distances when implemented
		SectRupDistances[] dists = null;
		return getMatchingSections(areas, dists);
	}
	
	public class SectRupDistances {
		final double minDist;
		final double maxDist;
		final double meanDist;
		
		private SectRupDistances(EvenlyGriddedSurface sectSurf, RuptureSurface rupSurf) {
			double minDist = Double.POSITIVE_INFINITY;
			double maxDist = 0d;
			double meanDist = 0d;
			int numPoints = 0;
			LocationList rupLocs = rupSurf.getEvenlyDiscritizedListOfLocsOnSurface();
			for (Location loc : sectSurf.getEvenlyDiscritizedListOfLocsOnSurface()) {
				double dist = Double.POSITIVE_INFINITY;
				for (Location loc2 : rupLocs)
					dist = Math.min(dist, LocationUtils.linearDistanceFast(loc, loc2));
				minDist = Math.min(minDist, dist);
				maxDist = Math.max(maxDist, dist);
				meanDist += dist;
				numPoints++;
			}
			meanDist /= numPoints;
			
			this.minDist = minDist;
			this.maxDist = maxDist;
			this.meanDist = meanDist;
		}

		public double getMinDist() {
			return minDist;
		}

		public double getMaxDist() {
			return maxDist;
		}

		public double getMeanDist() {
			return meanDist;
		}
	}

	public List<FaultSectionPrefData> getMatchingSections(double[] areas, SectRupDistances[] meanSectDists) {
		if (areas == null)
			return null;
		List<FaultSectionPrefData> sects = new ArrayList<>();
		for (int s=0; s<rupSet.getNumSections(); s++) {
			 double fractOfSurface = areas[s]/sectAreas[s];
			 if (fractOfSurface > minFractionalAreaInPolygon)
				 sects.add(rupSet.getFaultSectionData(s));
		}
		if (sects.isEmpty())
			return  null;
		return sects;
	}
	
	public Region getPolygon(int sectIndex) {
		return polygons[sectIndex];
	}
	
	public void writeMatchMarkdown(File markdownDir, List<? extends ObsEqkRupture> ruptures, boolean replot) throws IOException {
		Preconditions.checkState(markdownDir.exists() || markdownDir.mkdir());
		File resourcesDir = new File(markdownDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		List<String> lines = new ArrayList<>();
		
		lines.add("# Finite Fault Section Reset Results");
		lines.add("");
		
		List<ObsEqkRupture> finiteRups = new ArrayList<>();
		boolean hasFSS = false;
		for (ObsEqkRupture rup : ruptures) {
			RuptureSurface surf = rup.getRuptureSurface();
			if (surf == null || surf instanceof PointSurface)
				continue;
			finiteRups.add(rup);
			hasFSS = hasFSS || (rup instanceof ETAS_EqkRupture) && ((ETAS_EqkRupture)rup).getFSSIndex() > 0;
		}
		TableBuilder table = MarkdownUtils.tableBuilder();
		if (finiteRups.size() == ruptures.size()) {
			table.addLine("**# Ruptures**", ruptures.size());
		} else {
			table.addLine("**Input # Ruptures**", ruptures.size());
			table.addLine("**Ruptures w/ Finite Surfaces**", finiteRups.size());
		}
		table.addLine("**Min Area Fraction**&ast;", optionalDigitDF.format(minFractionalAreaInPolygon));
		table.addLine("**Fault Polygon Buffer**", optionalDigitDF.format(faultBuffer)+" [km]");
		lines.addAll(table.build());
		
		lines.add("");
		lines.add("*&ast; Ruptures are considered to reset a fault section if at least this fraction of the section's area ruptures "
				+ "inside its polygon*");
		lines.add("");
		
		if (finiteRups.isEmpty()) {
			lines.add("**No ruptures with finite surfaces!**");
		} else {
			int tocIndex = lines.size();
			String topLink = "*[(top)](#table-of-contents)*";
			
			String yes = "**YES**";
			String no = "*NO*";
			
			for (ObsEqkRupture rup : finiteRups) {
				RuptureSurface surf = rup.getRuptureSurface();
				int fssIndex = -1;
				if (rup instanceof ETAS_EqkRupture)
					fssIndex = ((ETAS_EqkRupture)rup).getFSSIndex();
				
				Date date = new Date(rup.getOriginTime());
				String name = "M"+optionalDigitDF.format(rup.getMag())+" on "+printDateFormat.format(date);
				lines.add("## "+name);
				lines.add(topLink); lines.add("");
				
				double[] areas = getAreaInSectionPolygons(surf);
				SectRupDistances[] dists = getSectRupDistances(surf, areas);
				List<FaultSectionPrefData> matches = getMatchingSections(areas, dists);
				List<FaultSectionPrefData> fssSects = fssIndex < 0 ? null : rupSet.getFaultSectionDataForRupture(fssIndex);
				
				String prefix = fileDateFormat.format(date)+"_m"+optionalDigitDF.format(rup.getMag());
				if (replot || !new File(resourcesDir, prefix+".png").exists())
					plotMatchingSections(resourcesDir, prefix, surf, name, fssSects, "UCERF3-Mapped", areas, matches);
				lines.add("![Map Plot](resources/"+prefix+".png)");
				lines.add("");
				
				table = MarkdownUtils.tableBuilder();
				
				boolean any = false;
				table.initNewLine();
				table.addColumn("Section Index");
				table.addColumn("Section Name");
				table.addColumn("Match?");
				table.addColumn("Section Area");
				table.addColumn("Rup Area in Poly");
				table.addColumn("Area Fraction");
				table.addColumn("Sect Distance To Rup");
				if (hasFSS)
					table.addColumn("UCERF3 Rupture Section?");
				table.finalizeLine();
				for (int index=0; index<rupSet.getNumSections(); index++) {
					FaultSectionPrefData sect = rupSet.getFaultSectionData(index);
					double area = areas == null ? 0 : areas[index];
					boolean match = matches != null && matches.contains(sect);
					boolean fssMatch = fssSects != null && fssSects.contains(sect);
					if (area == 0d && !match && !fssMatch)
						continue;
					any = true;
					table.initNewLine();
					table.addColumn(sect.getSectionId());
					table.addColumn(sect.getSectionName());
					table.addColumn(match ? yes : no);
					table.addColumn(optionalDigitDF.format(sectAreas[index])+" [km^2]");
					table.addColumn(optionalDigitDF.format(area)+" [km^2]");
					table.addColumn(optionalDigitDF.format(area/sectAreas[index]));
					SectRupDistances dist = dists != null && dists[index] != null ? dists[index]
							: new SectRupDistances(getSectSurface(index), surf);
					table.addColumn("mean="+optionalDigitDF.format(dist.getMeanDist())+" ["+optionalDigitDF.format(dist.getMinDist())
							+" "+optionalDigitDF.format(dist.getMaxDist())+"] [km]");
					if (hasFSS)
						table.addColumn(fssMatch ? yes : no);
					table.finalizeLine();
				}
				if (any) {
					lines.add("");
					lines.addAll(table.build());
				}
				lines.add("");
			}
			// add TOC
			lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 4));
			lines.add(tocIndex, "## Table Of Contents");
		}
		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, markdownDir);
	}
	
	static final DateFormat printDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss z");
	static final DateFormat fileDateFormat = new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss-z");
	private static final DecimalFormat optionalDigitDF = new DecimalFormat("0.##");
	
	public static final Color COLOR_MATCH = Color.GREEN.darker();
	public static final Color COLOR_NO_MATCH_EXTERNAL_MATCH = Color.RED.darker();
	public static final Color COLOR_MATCH_EXTERNAL_NO_MATCH = Color.BLUE.darker();
	public static final Color COLOR_HAS_AREA_NO_MATCH = Color.LIGHT_GRAY.darker();
	public static final Color COLOR_NO_AREA = new Color(240, 240, 240);
	public static final Color COLOR_RUP_SURFACE = Color.ORANGE.darker();
	public static final Color COLOR_CA_OUTLINE = Color.DARK_GRAY;
	
	private static final float POINT_THICKNESS = 1.5f;
	private static final float LINE_THICKNESS = 2f;
	
	private static final double REGION_BUFFER_DEGREES = 0.2d;
	
	public void plotMatchingSections(File outputDir, String prefix, RuptureSurface surf, String title,
			Collection<FaultSectionPrefData> externalMatches, String externalName) throws IOException {
		double[] areas = getAreaInSectionPolygons(surf);
		SectRupDistances[] dists = getSectRupDistances(surf, areas);
		List<FaultSectionPrefData> matches = getMatchingSections(areas, dists);
		plotMatchingSections(outputDir, prefix, surf, title, externalMatches, externalName, areas, matches);
	}
	
	private void plotMatchingSections(File outputDir, String prefix, RuptureSurface surf, String title,
			Collection<FaultSectionPrefData> externalMatches, String externalName, double[] areas,
			List<FaultSectionPrefData> matches) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		XY_DataSet rupSurfXY = new DefaultXY_DataSet();
		PlotCurveCharacterstics rupSurfChar = new PlotCurveCharacterstics(
				PlotSymbol.FILLED_CIRCLE, POINT_THICKNESS*0.75f, COLOR_RUP_SURFACE);
		
		for (Location loc : surf.getEvenlyDiscritizedListOfLocsOnSurface())
			rupSurfXY.set(loc.getLongitude(), loc.getLatitude());
		
		rupSurfXY.setName("Rupture Surface");
		funcs.add(rupSurfXY);
		chars.add(rupSurfChar);
		
		XY_DataSet matchXY = new DefaultXY_DataSet();
		PlotCurveCharacterstics matchPointChar = new PlotCurveCharacterstics(
				PlotSymbol.FILLED_CIRCLE, POINT_THICKNESS, COLOR_MATCH);
		PlotCurveCharacterstics matchPolyChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, LINE_THICKNESS, COLOR_MATCH);
		
		XY_DataSet noMatch_externalMatchXY = new DefaultXY_DataSet();
		PlotCurveCharacterstics noMatch_externalMatchPointChar = new PlotCurveCharacterstics(
				PlotSymbol.FILLED_CIRCLE, POINT_THICKNESS, COLOR_NO_MATCH_EXTERNAL_MATCH);
		PlotCurveCharacterstics noMatch_externalMatchPolyChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, LINE_THICKNESS, COLOR_NO_MATCH_EXTERNAL_MATCH);
		
		XY_DataSet match_noExternalXY = new DefaultXY_DataSet();
		PlotCurveCharacterstics match_noExternalPointChar = new PlotCurveCharacterstics(
				PlotSymbol.FILLED_CIRCLE, POINT_THICKNESS, COLOR_MATCH_EXTERNAL_NO_MATCH);
		PlotCurveCharacterstics match_noExternalPolyChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, LINE_THICKNESS, COLOR_MATCH_EXTERNAL_NO_MATCH);
		
		XY_DataSet hasAreaXY = new DefaultXY_DataSet();
		PlotCurveCharacterstics hasAreaPointChar = new PlotCurveCharacterstics(
				PlotSymbol.FILLED_CIRCLE, POINT_THICKNESS, COLOR_HAS_AREA_NO_MATCH);
		PlotCurveCharacterstics hasAreaPolyChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, LINE_THICKNESS, COLOR_HAS_AREA_NO_MATCH);
		
		XY_DataSet noAreaXY = new DefaultXY_DataSet();
		PlotCurveCharacterstics noAreaPointChar = new PlotCurveCharacterstics(
				PlotSymbol.FILLED_CIRCLE, Math.min(1f, POINT_THICKNESS), COLOR_NO_AREA);
		PlotCurveCharacterstics noAreaPolyChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, Math.min(1f, LINE_THICKNESS), COLOR_NO_AREA);
		
		List<Integer> noAreaIndexes = new ArrayList<>();
		
		for (int index=0; index<rupSet.getNumSections(); index++) {
			FaultSectionPrefData sect = rupSet.getFaultSectionData(index);
			
			boolean hasArea = areas != null && areas[index] > 0d;
			boolean match = matches != null && matches.contains(sect);
			
			boolean added = false;
			
			if (externalMatches != null) {
				Preconditions.checkNotNull(externalName);
				boolean externalMatch = externalMatches.contains(sect);
				
				if (match) {
					if (externalMatch) {
						addSurface(getSectSurface(index), matchXY);
						funcs.add(getSectPolygonXY(index));
						chars.add(matchPolyChar);
					} else {
						addSurface(getSectSurface(index), match_noExternalXY);
						funcs.add(getSectPolygonXY(index));
						chars.add(match_noExternalPolyChar);
					}
					added = true;
				} else {
					if (externalMatch) {
						addSurface(getSectSurface(index), noMatch_externalMatchXY);
						funcs.add(getSectPolygonXY(index));
						chars.add(noMatch_externalMatchPolyChar);
						added = true;
					}
				}
			} else if (match) {
				addSurface(getSectSurface(index), matchXY);
				funcs.add(getSectPolygonXY(index));
				chars.add(matchPolyChar);
				added = true;
			}
			
			if (!added) {
				if (hasArea) {
					addSurface(getSectSurface(index), hasAreaXY);
					funcs.add(getSectPolygonXY(index));
					chars.add(hasAreaPolyChar);
				} else {
					noAreaIndexes.add(index);
				}
			}
		}
		
		// add all non empty and determine extents of plot region
		if (matchXY.size() > 0) {
			if (externalMatches != null && externalName != null)
				matchXY.setName("Match & "+externalName);
			else
				matchXY.setName("Match");
			funcs.add(matchXY);
			chars.add(matchPointChar);
		}
		
		if (match_noExternalXY.size() > 0) {
			match_noExternalXY.setName("Match, but not "+externalName);
			funcs.add(match_noExternalXY);
			chars.add(match_noExternalPointChar);
		}
		
		if (noMatch_externalMatchXY.size() > 0) {
			noMatch_externalMatchXY.setName("No Match, but "+externalName);
			funcs.add(noMatch_externalMatchXY);
			chars.add(noMatch_externalMatchPointChar);
		}
		
		if (hasAreaXY.size() > 0) {
			hasAreaXY.setName("Has Area");
			funcs.add(hasAreaXY);
			chars.add(hasAreaPointChar);
		}
		
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (XY_DataSet func : funcs) {
			latTrack.addValue(func.getMinY());
			latTrack.addValue(func.getMaxY());
			lonTrack.addValue(func.getMinX());
			lonTrack.addValue(func.getMaxX());
		}
		double centerLat = 0.5*(latTrack.getMin() + latTrack.getMax());
		double centerLon = 0.5*(lonTrack.getMin() + lonTrack.getMax());
		double maxSpan = Math.max(latTrack.getMax() - latTrack.getMin(), lonTrack.getMax() - lonTrack.getMin());
		double centerBuffer = 0.5*maxSpan + REGION_BUFFER_DEGREES;
		Region plotReg = new Region(new Location(centerLat - centerBuffer, centerLon - centerBuffer),
				new Location(centerLat + centerBuffer, centerLon + centerBuffer));
		
		// now add california outline
		PlotCurveCharacterstics caChar = new PlotCurveCharacterstics(PlotLineType.SOLID, LINE_THICKNESS, COLOR_CA_OUTLINE);
		for (XY_DataSet xy : PoliticalBoundariesData.loadCAOutlines()) {
			funcs.add(xy);
			chars.add(caChar);
		}
		
		// add any no-area surfaces within the region
		for (int index : noAreaIndexes) {
			EvenlyGriddedSurface sectSurface = getSectSurface(index);
			if (sectSurface.getAveDip() == 90d)
				addSurface(sectSurface.getEvenlyDiscritizedUpperEdge(), noAreaXY, plotReg);
			else
				addSurface(sectSurface.getEvenlyDiscritizedPerimeter(), noAreaXY, plotReg);
			XY_DataSet polygon = getSectPolygonXY(index);
			boolean inside = false;
			for (Point2D pt : polygon)
				if (plotReg.contains(new Location(pt.getY(), pt.getX())))
					inside = true;
			if (inside) {
				funcs.add(getSectPolygonXY(index));
				chars.add(noAreaPolyChar);
			}
		}
		
		if (noAreaXY.size() > 0) {
			noAreaXY.setName("No Area");
			funcs.add(noAreaXY);
			chars.add(noAreaPointChar);
		}
		
		// write plot
		PlotSpec spec = new PlotSpec(funcs, chars, title, "Longitude", "Latitude");
		spec.setLegendVisible(true);
		
		HeadlessGraphPanel gp = new HeadlessGraphPanel();
		gp.setRenderingOrder(DatasetRenderingOrder.REVERSE);
		gp.setTickLabelFontSize(18);
		gp.setAxisLabelFontSize(24);
		gp.setPlotLabelFontSize(24);
		gp.setBackgroundColor(Color.WHITE);
		
		Range xRange = new Range(plotReg.getMinLon(), plotReg.getMaxLon());
		Range yRange = new Range(plotReg.getMinLat(), plotReg.getMaxLat());
		gp.drawGraphPanel(spec, false, false, xRange, yRange);
		
		double tick;
		if (maxSpan > 3d)
			tick = 1d;
		else if (maxSpan > 1.5d)
			tick = 0.5;
		else if (maxSpan > 0.8)
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
	
	private static void addSurface(RuptureSurface surf, XY_DataSet xy) {
		if (surf instanceof EvenlyGriddedSurface)
			addSurface(surf.getEvenlyDiscritizedPerimeter(), xy, null);
		else
			addSurface(surf.getEvenlyDiscritizedListOfLocsOnSurface(), xy, null);
	}
	
	private static void addSurface(LocationList surfLocs, XY_DataSet xy, Region plotReg) {
		for (Location loc : surfLocs) {
			if (plotReg != null && !plotReg.contains(loc))
				continue;
			xy.set(loc.getLongitude(), loc.getLatitude());
		}
	}
	
	private synchronized XY_DataSet getSectPolygonXY(int index) {
		if (sectPolygonXYs == null)
			sectPolygonXYs = new DefaultXY_DataSet[rupSet.getNumSections()];
		if (sectPolygonXYs[index] == null) {
			sectPolygonXYs[index] = new DefaultXY_DataSet();
			for (Location loc : getPolygon(index).getBorder())
				sectPolygonXYs[index].set(loc.getLongitude(), loc.getLatitude());
			sectPolygonXYs[index].set(sectPolygonXYs[index].get(0));
		}
		return sectPolygonXYs[index];
	}
	
	public static void main(String[] args) throws IOException, DocumentException {
		File etasInputDir = new File("/home/kevin/git/ucerf3-etas-launcher/inputs");
		
		File catFile = new File(etasInputDir, "u3_historical_catalog.txt");
		File xmlFile = new File(etasInputDir, "u3_historical_catalog_finite_fault_mappings.xml");
		
		File mainOutputDir = new File("/home/kevin/git/misc-research/ucerf3_etas/finite_section_mapping");
		
		Map<FaultModels, FaultSystemRupSet> rupSetMap = new HashMap<>();
		
		FaultSystemRupSet rupSet31 = FaultSystemIO.loadRupSet(new File(etasInputDir,
				"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"));
		rupSetMap.put(FaultModels.FM3_1, rupSet31);
//		FaultSystemRupSet rupSet32 = FaultSystemIO.loadRupSet(new File(etasInputDir,
//				"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_2_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"));
//		rupSetMap.put(FaultModels.FM3_2, rupSet32);
		
		double[] minFracts = { 0.5 };
		double[] faultBuffers = { InversionTargetMFDs.FAULT_BUFFER, 1d };
		boolean replot = false;
		
		for (FaultModels fm : rupSetMap.keySet()) {
			System.out.println("Doing "+fm);
			FaultSystemRupSet rupSet = rupSetMap.get(fm);
			ObsEqkRupList inputRups = UCERF3_CatalogParser.loadCatalog(catFile);
			FiniteFaultMappingData.loadRuptureSurfaces(xmlFile, inputRups, fm, rupSet);
			
			for (double faultBuffer : faultBuffers) {
				for (double minFract : minFracts) {
					String dirName = "u3_catalog-"+fm.encodeChoiceString()+"-minFract"+(float)minFract+"-polyBuffer"+(float)faultBuffer;
					File markdownDir = new File(mainOutputDir, dirName);
					
					System.out.println("Fault buffer: "+faultBuffer);
					System.out.println("Minimum fract inside polygon: "+minFract);
					
					FiniteFaultSectionResetCalc calc = new FiniteFaultSectionResetCalc(rupSet, minFract, faultBuffer);
					
					calc.writeMatchMarkdown(markdownDir, inputRups, replot);
					
//					for (ObsEqkRupture rup : inputRups) {
//						RuptureSurface surf = rup.getRuptureSurface();
//						if (surf == null || surf instanceof PointSurface)
//							continue;
//						int fssIndex = -1;
//						if (rup instanceof ETAS_EqkRupture)
//							fssIndex = ((ETAS_EqkRupture)rup).getFSSIndex();
//						
//						System.out.println("Processing M"+(float)rup.getMag()+" on "
//								+FiniteFaultMappingData.df.format(new Date(rup.getOriginTime())));
//						
//						List<FaultSectionPrefData> resetSects = calc.getMatchingSections(surf);
//						if (fssIndex >= 0) {
//							List<FaultSectionPrefData> mappedSects = rupSet.getFaultSectionDataForRupture(fssIndex);
//							System.out.println("\tIt's an FSS rupture with "+mappedSects.size()+" sections");
//							if (resetSects != null)
//								System.out.println("\tReset sects calc found "+resetSects.size()+" sections");
//							for (FaultSectionPrefData sect : mappedSects) {
//								boolean match = resetSects != null && resetSects.contains(sect);
//								if (match)
//									System.out.println("\t\t"+sect.getName()+"\tMATCH");
//								else
//									System.out.println("\t\t"+sect.getName()+"\t(MISSING)");
//							}
//							List<FaultSectionPrefData> additionalSects = new ArrayList<>();
//							if (resetSects != null) {
//								for (FaultSectionPrefData sect : resetSects) {
//									if (!mappedSects.contains(sect))
//										additionalSects.add(sect);
//								}
//							}
//							System.out.println("\tAdditional reset sects not in mapped rupture: "+additionalSects.size());
//							for (FaultSectionPrefData sect : additionalSects)
//								System.out.println("\t\t"+sect.getName());
//						} else {
//							System.out.println("\tNot an FSS rupture");
//							if (resetSects == null) {
//								System.out.println("\tNo reset sects found ");
//							} else {
//								System.out.println("\tReset sects calc found "+resetSects.size()+" sections:");
//								for (FaultSectionPrefData sect : resetSects)
//									System.out.println("\t\t"+sect.getName());
//							}
//						}
//						System.out.println();
//					}
				}
			}
		}
	}

}
