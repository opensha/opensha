package scratch.UCERF3.erf.ETAS.association;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.dom4j.DocumentException;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.data.Range;
import org.jfree.chart.ui.TextAnchor;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
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
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.parsers.UCERF3_CatalogParser;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.PointSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionTargetMFDs;
import scratch.UCERF3.utils.FaultSystemIO;

public class FiniteFaultSectionResetCalc {
	
	private FaultSystemRupSet rupSet;
	
	// parameters
	private double minFractionalAreaInPolygon;
	private double faultBuffer;
	private boolean removeOverlapsWithDist;
	
	private Region[] polygons;
	private double[] sectAreas;
	private RuptureSurface[] sectSurfaces;
	private XY_DataSet[] sectPolygonXYs;
	
	private Table<EvenlyGriddedSurface, Region, Double> rupSetAreaInPolysCache = HashBasedTable.create();
	
	/**
	 * @param rupSet
	 * @param minFractionalAreaInPolygon sections will be reset if the area of a rupture within a section's polygon is at least
	 * this fraction of that section's area 
	 * @param faultBuffer fault section polygon buffer in KM (must be >0)
	 * @param removeOverlapsWithDist flag to remove overlaps in polygons by prioritizing sections with lower mean distance to rupture
	 */
	public FiniteFaultSectionResetCalc(FaultSystemRupSet rupSet, double minFractionalAreaInPolygon, double faultBuffer,
			boolean removeOverlapsWithDist) {
		this.rupSet = rupSet;
		Preconditions.checkArgument(minFractionalAreaInPolygon > 0d && minFractionalAreaInPolygon <= 1d,
				"Min Fractional Area in Polygon must be in range (0 1]");
		this.minFractionalAreaInPolygon = minFractionalAreaInPolygon;
		Preconditions.checkArgument(faultBuffer > 0d, "Fault buffer must be positive");
		setFaultBuffer(faultBuffer);
		this.removeOverlapsWithDist = removeOverlapsWithDist;
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
	
	public void setRemoveOverlapsWithDist(boolean removeOverlapsWithDist) {
		this.removeOverlapsWithDist = removeOverlapsWithDist;
	}
	
	public void setMinFractionalAreaInPolygon(double minFractionalAreaInPolygon) {
		this.minFractionalAreaInPolygon = minFractionalAreaInPolygon;
	}
	
	private synchronized RuptureSurface getSectSurface(int index) {
		if (sectSurfaces == null)
			sectSurfaces = new EvenlyGriddedSurface[rupSet.getNumSections()];
		if (sectSurfaces[index] == null)
			sectSurfaces[index] = rupSet.getFaultSectionData(index).getFaultSurface(1d);
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
			areas[s] = getAreaInPoly(surf, getPolygon(s));
			any = any || areas[s] > 0;
		}
		if (!any)
			return null;
		return areas;
	}
	
	private double getAreaInPoly(RuptureSurface surf, Region polygon) {
		if (surf instanceof CompoundSurface) {
			// cache for FSS ruptures
			double area = 0d;
			for (RuptureSurface subSurf : ((CompoundSurface)surf).getSurfaceList()) {
				if (subSurf instanceof EvenlyGriddedSurface) {
					Double myArea = rupSetAreaInPolysCache.get(surf, polygon);
					if (myArea == null) {
						myArea = subSurf.getAreaInsideRegion(polygon);
						rupSetAreaInPolysCache.put((EvenlyGriddedSurface)subSurf, polygon, myArea);
					}
					area += myArea;
				} else {
					area += subSurf.getAreaInsideRegion(polygon);
				}
			}
			return area;
		}
		return surf.getAreaInsideRegion(polygon);
	}
	
	/**
	 * @param surf
	 * @param areas
	 * @return array of the section distances (indexed by subsection index) for each fault section where area > 0, null otherwise
	 */
	public SectRupDistances[] getSectRupDistances(RuptureSurface surf, double[] areas) {
		return getSectRupDistances(surf, areas, false);
	}
	
	public SectRupDistances[] getSectRupDistances(RuptureSurface surf, double[] areas, boolean force) {
		if (areas == null)
			return null;
		SectRupDistances[] dists = new SectRupDistances[rupSet.getNumRuptures()];
		for (int s=0; s<rupSet.getNumSections(); s++) {
			 if (force || areas[s] > 0)
				 dists[s] = new SectRupDistances(getSectSurface(s), surf);
		}
		return dists;
	}

	public List<FaultSection> getMatchingSections(RuptureSurface surf) {
		double[] areas = getAreaInSectionPolygons(surf);
		SectRupDistances[] dists = removeOverlapsWithDist ? getSectRupDistances(surf, areas) : null;
		return getMatchingSections(surf, areas, dists);
	}
	
	public class SectRupDistances {
		final double minDist;
		final double maxDist;
		final double meanDist;
		
		private SectRupDistances(RuptureSurface sectSurf, RuptureSurface rupSurf) {
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
	
	private class DistSortableSections implements Comparable<DistSortableSections> {
		private final int index;
		private final FaultSection sect;
		private final SectRupDistances dist;
		private List<Region> polygons;
		private double area;
		public DistSortableSections(int index, FaultSection sect, SectRupDistances dist, Region polygon,
				double area) {
			super();
			this.index = index;
			this.sect = sect;
			this.dist = dist;
			this.polygons = new ArrayList<>();
			this.polygons.add(polygon);
			this.area = area;
		}
		@Override
		public int compareTo(DistSortableSections o) {
			return Double.compare(dist.getMeanDist(), o.dist.getMeanDist());
		}
	}
	
	public class FaultSectionDataMappingResult {
		public final FaultSection sect;
		public final SectRupDistances dist;
		public final double areaInPoly;
		public final double sectArea;
		public final double fractOfSurfInPoly;
		public final boolean match;
		
		public FaultSectionDataMappingResult(FaultSection sect, SectRupDistances dist, double areaInPoly,
				double sectArea) {
			this.sect = sect;
			this.dist = dist;
			this.areaInPoly = areaInPoly;
			this.sectArea = sectArea;
			this.fractOfSurfInPoly = areaInPoly/sectArea;
			this.match = fractOfSurfInPoly >= minFractionalAreaInPolygon;
		}
	}

	public List<FaultSection> getMatchingSections(RuptureSurface surf, double[] areas, SectRupDistances[] dists) {
		if (areas == null)
			return null;
		FaultSectionDataMappingResult[] results = getMappingResults(surf, areas, dists);
		return getMatchingSections(results);
	}
	
	public FaultSectionDataMappingResult[] getMappingResults(RuptureSurface surf, double[] areas, SectRupDistances[] dists) {
		if (areas == null)
			return null;
		if (removeOverlapsWithDist) {
			List<DistSortableSections> sects = removeOverlaps(surf, areas, dists);
			double[] modAreas = new double[areas.length];
			for (DistSortableSections sect : sects)
				modAreas[sect.index] = sect.area;
			areas = modAreas;
		}
		FaultSectionDataMappingResult[] ret = new FaultSectionDataMappingResult[rupSet.getNumSections()];
		for (int s=0; s<ret.length; s++)
			ret[s] = new FaultSectionDataMappingResult(rupSet.getFaultSectionData(s), dists == null ? null : dists[s],
					areas[s], sectAreas[s]);
		return ret;
	}

	private List<FaultSection> getMatchingSections(FaultSectionDataMappingResult[] results) {
		List<FaultSection> sects = new ArrayList<>();
		for (FaultSectionDataMappingResult result : results)
			if (result.match)
				sects.add(result.sect);
		if (sects.isEmpty())
			return  null;
		return sects;
	}

	private List<DistSortableSections> removeOverlaps(RuptureSurface surf, double[] areas, SectRupDistances[] dists) {
		if (areas == null)
			return null;
		final boolean D = false;
		Preconditions.checkNotNull(dists, "distances not calculated but removeOverlapsWithDist=true");
		List<DistSortableSections> sects = new ArrayList<>();
		for (int s=0; s<areas.length; s++) {
			if (areas[s] > 0) {
				Preconditions.checkNotNull(dists[s]);
				sects.add(new DistSortableSections(s, rupSet.getFaultSectionData(s), dists[s], getPolygon(s), areas[s]));
			}
		}
		
		// sort by mean distance, increasing
		Collections.sort(sects);
		
		for (int i=0; i<sects.size(); i++) {
			DistSortableSections s1 = sects.get(i);
			if (s1.polygons == null)
				// this section has been completely superseded by other regions
				continue;
			if (D) System.out.println("S1: "+s1.index+". "+s1.sect.getName()+" (with "+s1.polygons.size()+" polys)");
			for (int j=i+1; j<sects.size(); j++) {
				DistSortableSections s2 = sects.get(j);
				if (s2.polygons == null)
					// this section has been completely superseded by other regions
					continue;
				if (D) System.out.println("\tS2: "+s2.index+". "+s2.sect.getName()+" (with "+s2.polygons.size()+" polys)");
				
				boolean modified = false;
				List<Region> newRegions = new ArrayList<>();
				
				for (int k=0; k<s2.polygons.size(); k++) {
					Region p2 = s2.polygons.get(k);
//					Preconditions.checkState(k < 10);
					boolean any = false;
					for (int l=0; l<s1.polygons.size(); l++) {
						Region p1 = s1.polygons.get(l);
						if (D) System.out.println("\t\tsubtracting S1["+l+"] from S2:["+k+"]");
						// subtract the higher priority polygon (S1) from this polygon (S2)
						Region[] subtracted;
						try {
							subtracted = Region.subtract(p2, p1);
						} catch (RuntimeException e) {
							if (D) System.out.println("===Minuend===");
							if (D) printRegionCode(p2, "minuend");
							if (D) System.out.println("===Subtrahend===");
							if (D) printRegionCode(p1, "subtrahend");
							throw e;
						}
						if (subtracted == null) {
							// does not intersect
//							System.out.println("\t\t\tno intersection");
						} else {
							// it intersects
							any = true;
//							System.out.println("\t\t\treturned "+subtracted.length+" intersections");
							for (Region newRegion : subtracted)
								newRegions.add(newRegion);
						}
					}
					if (any)
						modified = true;
					else
						newRegions.add(p2);
				}
				
				if (modified) {
					s2.polygons = newRegions.isEmpty() ? null : newRegions;
					s2.area = 0d;
					for (Region region : newRegions)
						s2.area += surf.getAreaInsideRegion(region);
				}
			}
		}
		return sects;
	}
	
	private static void printRegionCode(Region region, String varName) {
		System.out.println("LocationList "+varName+"Border = new LocationList();");
		for (Location loc : region.getBorder())
			System.out.println(varName+"Border.add(new Location("+loc.getLatitude()+", "+loc.getLongitude()+"));");
		System.out.println("Region "+varName+" = new Region("+varName+"Border, null);");
		List<LocationList> interiors = region.getInteriors();
		if (interiors != null) {
			for (int i=0; i<interiors.size(); i++) {
				String lName = varName+"Int"+i;
				System.out.println("LocationList "+lName+" = new LocationList();");
				for (Location loc : interiors.get(i))
					System.out.println(lName+".add(new Location("+loc.getLatitude()+", "+loc.getLongitude()+"));");
				System.out.println(varName+".addInterior(new Region("+lName+", name));");
			}
		}
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
		table.addLine("**Remove Polygon Overlap?**", removeOverlapsWithDist ? "Yes (sorted by mean section distance)" : "No");
		lines.addAll(table.build());
		
		lines.add("");
		lines.add("*&ast; Ruptures are considered to reset a fault section if at least this fraction of the section's area ruptures "
				+ "inside its polygon*");
		lines.add("");
		int u3ResultIndex = lines.size();
		int totNumU3 = 0;
		int totMatchesU3 = 0;
		
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
				System.out.println("Processing "+name);
				
				double[] areas = getAreaInSectionPolygons(surf);
				SectRupDistances[] dists = getSectRupDistances(surf, areas);
				double[] noOverlapAreas = null;
				FaultSectionDataMappingResult[] results = getMappingResults(surf, areas, dists);
				List<DistSortableSections> sectsNoOverlap = null;
				if (removeOverlapsWithDist) {
					sectsNoOverlap = removeOverlaps(surf, areas, dists);
					if (sectsNoOverlap != null) {
						noOverlapAreas = new double[areas.length];
						for (DistSortableSections sect : sectsNoOverlap)
							noOverlapAreas[sect.index] = sect.area;
					}
				}
				List<FaultSection> matches = getMatchingSections(surf, areas, dists);
				List<? extends FaultSection> fssSects = fssIndex < 0 ? null : rupSet.getFaultSectionDataForRupture(fssIndex);
				
				String prefix = fileDateFormat.format(date)+"_m"+optionalDigitDF.format(rup.getMag());
				if (replot || !new File(resourcesDir, prefix+".png").exists())
					plotMatchingSections(resourcesDir, prefix, surf, name, fssSects, "UCERF3-Mapped",
							results, sectsNoOverlap, null, false);
				
				table = MarkdownUtils.tableBuilder();
				
				boolean any = false;
				table.initNewLine();
				table.addColumn("Section Index");
				table.addColumn("Section Name");
				table.addColumn("Match?");
				table.addColumn("Section Area");
				if (removeOverlapsWithDist) {
					table.addColumn("Rup Area in Raw Poly");
					table.addColumn("Rup Area in No-Overlap Poly");
				} else {
					table.addColumn("Rup Area in Poly");
				}
				table.addColumn("Area Fraction");
				table.addColumn("Sect Distance To Rup");
				if (hasFSS)
					table.addColumn("UCERF3 Rupture Section?");
				table.finalizeLine();
				boolean matchesU3 = true;
				for (int index=0; index<rupSet.getNumSections(); index++) {
					FaultSection sect = rupSet.getFaultSectionData(index);
					double area = areas == null ? 0 : areas[index];
					boolean match = matches != null && matches.contains(sect);
					boolean fssMatch = fssSects != null && fssSects.contains(sect);
					matchesU3 = matchesU3 && match == fssMatch;
					if (area == 0d && !match && !fssMatch)
						continue;
					any = true;
					table.initNewLine();
					table.addColumn(sect.getSectionId());
					table.addColumn(sect.getSectionName());
					table.addColumn(match ? yes : no);
					table.addColumn(optionalDigitDF.format(sectAreas[index])+" [km^2]");
					table.addColumn(optionalDigitDF.format(area)+" [km^2]");
					double fract;
					if (removeOverlapsWithDist) {
						double noOverlapArea = noOverlapAreas == null ? 0 : noOverlapAreas[index];
						table.addColumn(optionalDigitDF.format(noOverlapArea)+" [km^2]");
						fract = noOverlapArea/sectAreas[index];
					} else {
						fract = area/sectAreas[index];
					}
					table.addColumn(optionalDigitDF.format(fract));
					SectRupDistances dist = dists != null && dists[index] != null ? dists[index]
							: new SectRupDistances(getSectSurface(index), surf);
					table.addColumn("mean="+optionalDigitDF.format(dist.getMeanDist())+" ["+optionalDigitDF.format(dist.getMinDist())
							+" "+optionalDigitDF.format(dist.getMaxDist())+"] [km]");
					if (hasFSS)
						table.addColumn(fssMatch ? yes : no);
					table.finalizeLine();
				}
				if (hasFSS && fssIndex >= 0) {
					totNumU3++;
					name += ", UCERF3-mapped, match: ";
					if (matchesU3) {
						name += "YES";
						totMatchesU3++;
					} else {
						name += "NO";
					}
				}
				lines.add("## "+name);
				lines.add(topLink); lines.add("");
				lines.add("![Map Plot](resources/"+prefix+".png)");
				lines.add("");
				if (any) {
					lines.add("");
					lines.addAll(table.build());
				}
				lines.add("");
			}
			if (totNumU3 > 0) {
				List<String> u3Lines = new ArrayList<>();
				u3Lines.add("### UCERF3 Mappings Summary");
				u3Lines.add("");
				u3Lines.add("This catalog contains UCERF3-mapped ruptures where we can compare this algorithm against "
						+ "the external UCERF3 mapping");
				u3Lines.add("");
				table = MarkdownUtils.tableBuilder();
				table.addLine("**# With UCERF3 Mappings**", totNumU3);
				double percent = totMatchesU3*100d/totNumU3;
				table.addLine("**Perfect Matches**", totMatchesU3+" ("+optionalDigitDF.format(percent)+" %)");
				u3Lines.addAll(table.build());
				u3Lines.add("");
				
				lines.addAll(u3ResultIndex, u3Lines);
				tocIndex += u3Lines.size();
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
	private static final float LINE_THICKNESS_ORIG_POLY = 1f;
	
	private static final double REGION_BUFFER_DEGREES = 0.2d;
	
	public void plotMatchingSections(File outputDir, String prefix, RuptureSurface surf, String title,
			Collection<FaultSectionPrefData> externalMatches, String externalName) throws IOException {
		double[] areas = getAreaInSectionPolygons(surf);
		SectRupDistances[] dists = getSectRupDistances(surf, areas);
		FaultSectionDataMappingResult[] results = getMappingResults(surf, areas, dists);
		List<DistSortableSections> sectsNoOverlap = removeOverlapsWithDist ? removeOverlaps(surf, areas, dists) : null;
		plotMatchingSections(outputDir, prefix, surf, title, externalMatches, externalName, results, sectsNoOverlap, null, false);
	}
	
	private void plotMatchingSections(File outputDir, String prefix, RuptureSurface surf, String title,
			Collection<? extends FaultSection> externalMatches, String externalName, FaultSectionDataMappingResult[] results,
			List<DistSortableSections> sectsNoOverlap, Location hypocenter, boolean annotateIndices) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		XY_DataSet rupSurfXY = new DefaultXY_DataSet();
		PlotCurveCharacterstics rupSurfChar = new PlotCurveCharacterstics(
				PlotSymbol.FILLED_CIRCLE, POINT_THICKNESS*0.75f, COLOR_RUP_SURFACE);
		
		for (Location loc : surf.getEvenlyDiscritizedListOfLocsOnSurface())
			rupSurfXY.set(loc.getLongitude(), loc.getLatitude());
		
		if (hypocenter != null) {
			XY_DataSet hypoXY = new DefaultXY_DataSet();
			hypoXY.setName("Hypocenter");
			hypoXY.set(hypocenter.getLongitude(), hypocenter.getLatitude());
			funcs.add(hypoXY);
			chars.add(new PlotCurveCharacterstics(PlotSymbol.FILLED_CIRCLE, 10f, Color.RED));
		}
		
		rupSurfXY.setName("Rupture Surface");
		funcs.add(rupSurfXY);
		chars.add(rupSurfChar);
		
		XY_DataSet matchXY = new DefaultXY_DataSet();
		PlotCurveCharacterstics matchPointChar = new PlotCurveCharacterstics(
				PlotSymbol.FILLED_CIRCLE, POINT_THICKNESS, COLOR_MATCH);
		PlotCurveCharacterstics matchPolyOrigChar = new PlotCurveCharacterstics(
				PlotLineType.DOTTED, LINE_THICKNESS_ORIG_POLY, COLOR_MATCH);
		PlotCurveCharacterstics matchPolyChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, LINE_THICKNESS, COLOR_MATCH);
		
		XY_DataSet noMatch_externalMatchXY = new DefaultXY_DataSet();
		PlotCurveCharacterstics noMatch_externalMatchPointChar = new PlotCurveCharacterstics(
				PlotSymbol.FILLED_CIRCLE, POINT_THICKNESS, COLOR_NO_MATCH_EXTERNAL_MATCH);
		PlotCurveCharacterstics noMatch_externalMatchPolyOrigChar = new PlotCurveCharacterstics(
				PlotLineType.DOTTED, LINE_THICKNESS_ORIG_POLY, COLOR_NO_MATCH_EXTERNAL_MATCH);
		PlotCurveCharacterstics noMatch_externalMatchPolyChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, LINE_THICKNESS, COLOR_NO_MATCH_EXTERNAL_MATCH);
		
		XY_DataSet match_noExternalXY = new DefaultXY_DataSet();
		PlotCurveCharacterstics match_noExternalPointChar = new PlotCurveCharacterstics(
				PlotSymbol.FILLED_CIRCLE, POINT_THICKNESS, COLOR_MATCH_EXTERNAL_NO_MATCH);
		PlotCurveCharacterstics match_noExternalPolyOrigChar = new PlotCurveCharacterstics(
				PlotLineType.DOTTED, LINE_THICKNESS_ORIG_POLY, COLOR_MATCH_EXTERNAL_NO_MATCH);
		PlotCurveCharacterstics match_noExternalPolyChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, LINE_THICKNESS, COLOR_MATCH_EXTERNAL_NO_MATCH);
		
		XY_DataSet hasAreaXY = new DefaultXY_DataSet();
		PlotCurveCharacterstics hasAreaPointChar = new PlotCurveCharacterstics(
				PlotSymbol.FILLED_CIRCLE, POINT_THICKNESS, COLOR_HAS_AREA_NO_MATCH);
		PlotCurveCharacterstics hasAreaPolyOrigChar = new PlotCurveCharacterstics(
				PlotLineType.DOTTED, LINE_THICKNESS_ORIG_POLY, COLOR_HAS_AREA_NO_MATCH);
		PlotCurveCharacterstics hasAreaPolyChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, LINE_THICKNESS, COLOR_HAS_AREA_NO_MATCH);
		
		XY_DataSet noAreaXY = new DefaultXY_DataSet();
		PlotCurveCharacterstics noAreaPointChar = new PlotCurveCharacterstics(
				PlotSymbol.FILLED_CIRCLE, Math.min(1f, POINT_THICKNESS), COLOR_NO_AREA);
		PlotCurveCharacterstics noAreaPolyChar = new PlotCurveCharacterstics(
				PlotLineType.SOLID, Math.min(1f, LINE_THICKNESS), COLOR_NO_AREA);
		
		List<XYAnnotation> anns = annotateIndices ? new ArrayList<>() : null;
		
		if (sectsNoOverlap != null) {
			for (DistSortableSections sectNoOverlap : sectsNoOverlap) {
				FaultSection sect = sectNoOverlap.sect;
				
				int index = sectNoOverlap.index;
				boolean hasArea = results != null && results[index].areaInPoly > 0d;
				boolean match = results != null && results[index].match;
				
				RuptureSurface sectSurface = getSectSurface(index);
				
				if (annotateIndices && hasArea) {
					Location center;
					if (sectSurface instanceof EvenlyGriddedSurface) {
						EvenlyGriddedSurface gridSurf = (EvenlyGriddedSurface)sectSurface;
						center = gridSurf.get(gridSurf.getNumRows()/2,
								gridSurf.getNumCols()/2);
					} else {
						MinMaxAveTracker latTrack = new MinMaxAveTracker();
						MinMaxAveTracker lonTrack = new MinMaxAveTracker();
						for (Location loc : sectSurface.getEvenlyDiscritizedListOfLocsOnSurface()) {
							latTrack.addValue(loc.getLatitude());
							lonTrack.addValue(loc.getLongitude());
						}
						center = new Location(latTrack.getAverage(), lonTrack.getAverage());
					}
					double x = center.getLongitude();
					double y = center.getLatitude();
					XYTextAnnotation ann = new XYTextAnnotation(index+"", x, y);
					ann.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
					ann.setTextAnchor(TextAnchor.CENTER);
					ann.setBackgroundPaint(new Color(255, 255, 255, 200));
					anns.add(ann);
				}
				
				PlotCurveCharacterstics polyChar = null;
				
				if (externalMatches != null) {
					Preconditions.checkNotNull(externalName);
					boolean externalMatch = externalMatches.contains(sect);
					
					if (match) {
						if (externalMatch)
							polyChar = matchPolyChar;
						else
							polyChar = match_noExternalPolyChar;
					} else {
						if (externalMatch)
							polyChar = noMatch_externalMatchPolyChar;
					}
				} else if (match)
					polyChar = matchPolyChar;
				
				if (polyChar == null && hasArea)
					polyChar = hasAreaPolyChar;
				
				if (polyChar != null && sectNoOverlap.polygons != null) {
					for (Region poly : sectNoOverlap.polygons) {
						DefaultXY_DataSet xy = new DefaultXY_DataSet();
						for (Location loc : poly.getBorder())
							xy.set(loc.getLongitude(), loc.getLatitude());
						xy.set(xy.get(0));
						funcs.add(xy);
						chars.add(polyChar);
						List<LocationList> interiors = poly.getInteriors();
						if (interiors != null) {
							for (LocationList interior : interiors) {
								xy = new DefaultXY_DataSet();
								for (Location loc : interior)
									xy.set(loc.getLongitude(), loc.getLatitude());
								xy.set(xy.get(0));
								funcs.add(xy);
								chars.add(polyChar);
							}
						}
					}
				}
			}
		}
		
		List<Integer> noAreaIndexes = new ArrayList<>();
		
		for (int index=0; index<rupSet.getNumSections(); index++) {
			FaultSection sect = rupSet.getFaultSectionData(index);
			
			boolean hasArea = results != null && results[index].areaInPoly > 0d;
			boolean match = results != null && results[index].match;
			
			boolean added = false;
			
			if (externalMatches != null) {
				Preconditions.checkNotNull(externalName);
				boolean externalMatch = externalMatches.contains(sect);
				
				if (match) {
					if (externalMatch) {
						addSurface(getSectSurface(index), matchXY);
						funcs.add(getSectPolygonXY(index));
						chars.add(sectsNoOverlap == null ? matchPolyChar : matchPolyOrigChar);
					} else {
						addSurface(getSectSurface(index), match_noExternalXY);
						funcs.add(getSectPolygonXY(index));
						chars.add(sectsNoOverlap == null ? match_noExternalPolyChar : match_noExternalPolyOrigChar);
					}
					added = true;
				} else {
					if (externalMatch) {
						addSurface(getSectSurface(index), noMatch_externalMatchXY);
						funcs.add(getSectPolygonXY(index));
						chars.add(sectsNoOverlap == null ? noMatch_externalMatchPolyChar : noMatch_externalMatchPolyOrigChar);
						added = true;
					}
				}
			} else if (match) {
				addSurface(getSectSurface(index), matchXY);
				funcs.add(getSectPolygonXY(index));
				chars.add(sectsNoOverlap == null ? matchPolyChar : matchPolyOrigChar);
				added = true;
			}
			
			if (!added) {
				if (hasArea) {
					addSurface(getSectSurface(index), hasAreaXY);
					funcs.add(getSectPolygonXY(index));
					chars.add(sectsNoOverlap == null ? hasAreaPolyChar : hasAreaPolyOrigChar);
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
		if (centerBuffer < 0.5d)
			centerBuffer = 0.5d;
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
			RuptureSurface sectSurface = getSectSurface(index);
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
		spec.setPlotAnnotations(anns);
		
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
	
	private static class ParamSweepCallable implements Callable<ParamSweepCallable> {
		private final FaultSystemSolution sol;
		private final RuptureSurface[] surfs;
		private final int bufferIndex;
		private final double faultBuffer;
		private final EvenlyDiscretizedFunc fractAreas;
		private final boolean removeOverlap;
		
		private double[] matchRates;
		private int[] matchCounts;
		
		public ParamSweepCallable(FaultSystemSolution sol, RuptureSurface[] surfs, int bufferIndex,double faultBuffer,
				EvenlyDiscretizedFunc fractAreas, boolean removeOverlap) {
			this.sol = sol;
			this.surfs = surfs;
			this.bufferIndex = bufferIndex;
			this.faultBuffer = faultBuffer;
			this.fractAreas = fractAreas;
			this.removeOverlap = removeOverlap;
		}

		@Override
		public ParamSweepCallable call() throws Exception {
			FaultSystemRupSet rupSet = sol.getRupSet();
			
			FiniteFaultSectionResetCalc calc = new FiniteFaultSectionResetCalc(rupSet, fractAreas.getX(0), faultBuffer, removeOverlap);
			matchRates = new double[fractAreas.size()];
			matchCounts = new int[fractAreas.size()];
			for (int r=0; r<surfs.length; r++) {
				if (r % 1000 == 0)
					System.out.println("Rupture "+r);
				double[] areas = calc.getAreaInSectionPolygons(surfs[r]);
				if (areas == null)
					continue;
				SectRupDistances[] dists = removeOverlap ? calc.getSectRupDistances(surfs[r], areas) : null;
				FaultSectionDataMappingResult[] results = calc.getMappingResults(surfs[r], areas, dists);
				List<? extends FaultSection> sects = rupSet.getFaultSectionDataForRupture(r);
				for (int i=0; i<matchRates.length; i++) {
					calc.minFractionalAreaInPolygon = fractAreas.getX(i);
					List<FaultSection> matches = calc.getMatchingSections(results);
					if (matches != null && matches.size() == sects.size()) {
						// potential match
						boolean match = true;
						for (FaultSection sect : sects) {
							if (!matches.contains(sect)) {
								match = false;
								break;
							}
						}
						if (match) {
							matchCounts[i]++;
							matchRates[i] += sol.getRateForRup(r);
						}
					}
				}
			}
			
			System.out.println("buffer="+(float)faultBuffer+"\tremoveOverlap="+removeOverlap);
			for (int i=0; i<matchCounts.length; i++) {
				System.out.println("\tfractArea="+(float)fractAreas.getX(i));
				System.out.println("\t"+matchCounts[i]+"/"+rupSet.getNumRuptures()+" matches ("
						+optionalDigitDF.format(100d*matchCounts[i]/rupSet.getNumRuptures())+" %)");
				double totRate = sol.getTotalRateForAllFaultSystemRups();
				System.out.println("\t"+(float)matchRates[i]+"/"+(float)totRate+" matches ("
						+optionalDigitDF.format(100d*matchRates[i]/totRate)+" %)");
			}
			
			return this;
		}
	}
	
	public List<String> writeSectionResetMarkdown(File outputDir, String relativePathToOutputDir, String topLevelHeading,
			String topLink, RuptureSurface surf, Location hypocenter) throws IOException {
		return writeSectionResetMarkdown(outputDir, relativePathToOutputDir, topLevelHeading, topLink, surf, hypocenter, null);
	}
	
	public List<String> writeSectionResetMarkdown(File outputDir, String relativePathToOutputDir, String topLevelHeading,
			String topLink, RuptureSurface surf, Location hypocenter, String eventID) throws IOException {
		List<String> lines = new ArrayList<>();
		
		if (eventID == null || eventID.isEmpty())
			lines.add(topLevelHeading+" Possible Finite Rupture Subsection Mappings");
		else
			lines.add(topLevelHeading+" "+eventID+" Possible Finite Rupture Subsection Mappings");
		lines.add(topLink); lines.add("");
		
		lines.add("This gives any possible finite rupture surface subsection mappings. In the plot below, potentially suggested "
				+ "subsections are outlined in green, and all subsections for which any of this rupture is within the fault "
				+ "polygon are in gray. Suggested sections are those for which the area of the input rupture within the polygon "
				+ "is at least "+(float)(minFractionalAreaInPolygon*100)+" % of the sub section area");
		lines.add("");
		if (removeOverlapsWithDist)
			lines.add("Overlapping polygons are removed according to the mean distance of the actual subsection surface, with the "
					+ "polygons of closer sections masking out the polygons of further sections");
		lines.add("");
		
		boolean pointSource = surf.isPointSurface();
		if (pointSource) {
			lines.add("As this is a point source, there will be no matches, but sections within 25km will be listed");
			lines.add("");
		}
		
		String prefix = "finite_rup_subsection_mappings";
		if (eventID != null && !eventID.isEmpty())
			prefix += "_"+eventID;
		double[] areas = getAreaInSectionPolygons(surf);
		if (areas == null)
			// force it not to be null as we want to include everything
			areas = new double[rupSet.getNumSections()];
		SectRupDistances[] dists = getSectRupDistances(surf, areas, pointSource);
		List<DistSortableSections> sectsNoOverlap = removeOverlapsWithDist ? removeOverlaps(surf, areas, dists) : null;
		FaultSectionDataMappingResult[] results = getMappingResults(surf, areas, dists);
		plotMatchingSections(outputDir, prefix, surf, "Subsection Mappings", null, null, results, sectsNoOverlap,
				hypocenter, true);
		
		lines.add("![Map]("+relativePathToOutputDir+"/"+prefix+".png)");
		lines.add("");
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		table.initNewLine();
		table.addColumn("Section Index");
		table.addColumn("Section Name");
		table.addColumn("Suggested Match?");
		table.addColumn("Section Area");
		if (!pointSource) {
			if (removeOverlapsWithDist) {
				table.addColumn("Rup Area in Raw Poly");
				table.addColumn("Rup Area in No-Overlap Poly");
			} else {
				table.addColumn("Rup Area in Poly");
			}
			table.addColumn("Area Fraction");
		}
		table.addColumn("Sect Distance To Rup");
		table.addColumn("Hypocenter in Polygon?");
		table.finalizeLine();
		
		for (int s=0; s<results.length; s++) {
			FaultSectionDataMappingResult result = results[s];
			boolean containsHypo = polygons[s].contains(hypocenter);
			boolean include = containsHypo || result.areaInPoly > 0 || result.match || areas[s] > 0
					|| (pointSource && result.dist != null && result.dist.minDist < 25);
			if (!include)
				continue;
			FaultSection sect = rupSet.getFaultSectionData(s);
			table.initNewLine();
			table.addColumn(s);
			table.addColumn(sect.getSectionName());
			table.addColumn(result.match ? "*yes*" : "no");
			table.addColumn(optionalDigitDF.format(result.sectArea));
			if (!pointSource) {
				if (removeOverlapsWithDist)
					table.addColumn(optionalDigitDF.format(areas[s]));
				table.addColumn(optionalDigitDF.format(result.areaInPoly));
				table.addColumn(optionalDigitDF.format(result.fractOfSurfInPoly));
			}
			SectRupDistances dist = result.dist;
			table.addColumn("mean="+optionalDigitDF.format(dist.getMeanDist())+" ["+optionalDigitDF.format(dist.getMinDist())
				+" "+optionalDigitDF.format(dist.getMaxDist())+"] [km]");
			table.addColumn(containsHypo ? "*yes*" : "no");
			table.finalizeLine();
		}
		lines.addAll(table.build());
		
		return lines;
	}
	
	public static void writeParamSweepMarkdown(File markdownDir, FaultSystemSolution sol, int threads) throws IOException {
		Preconditions.checkState(markdownDir.exists() || markdownDir.mkdir());
		File resourcesDir = new File(markdownDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		List<String> lines = new ArrayList<>();
		
		lines.add("# Finite Fault Section Reset Parameter Sweep");
		lines.add("");
		
		EvenlyDiscretizedFunc faultBufferFunc = new EvenlyDiscretizedFunc(1d, 12d, 12);
		EvenlyDiscretizedFunc fractAreaFunc = new EvenlyDiscretizedFunc(0.1d, 1d, 10);
		
		FaultSystemRupSet rupSet = sol.getRupSet();
		RuptureSurface[] surfs = new RuptureSurface[rupSet.getNumRuptures()];
		double totRate = sol.getTotalRateForAllFaultSystemRups();
		for (int r=0; r<rupSet.getNumRuptures(); r++)
			surfs[r] = rupSet.getSurfaceForRupture(r, 1d);
		
		ExecutorService exec = Executors.newFixedThreadPool(threads);
		
		for (boolean removeOverlap : new boolean[] {false, true}) {
			if (removeOverlap)
				lines.add("## Removing Overlap from Polygons (rupture distance sorted)");
			else
				lines.add("## Retaining All Overlapping Polygons");
			lines.add("");
			
			CSVFile<String> csv = new CSVFile<>(true);
			csv.addLine("Fault Buffer", "Min Area Fraction", "Match Count", "Match Fraction", "Match Rate", "Match Rate Fraction");
			
			double[][] matchRates = new double[faultBufferFunc.size()][fractAreaFunc.size()];
			int[][] matchCounts = new int[faultBufferFunc.size()][fractAreaFunc.size()];
			
			List<Future<ParamSweepCallable>> futures = new ArrayList<>();
			
			for (int i=0; i<faultBufferFunc.size(); i++) {
				double faultBuffer = faultBufferFunc.getX(i);
				futures.add(exec.submit(new ParamSweepCallable(sol, surfs, i, faultBuffer, fractAreaFunc, removeOverlap)));
			}
			
			System.out.println("Waiting on "+futures.size()+" futures");
			for (Future<ParamSweepCallable> future : futures) {
				try {
					ParamSweepCallable result = future.get();
					for (int i=0; i<fractAreaFunc.size(); i++) {
						List<String> line = new ArrayList<>();
						line.add((float)result.faultBuffer+"");
						line.add((float)result.fractAreas.getClosestXIndex(i)+"");
						line.add(result.matchCounts[i]+"");
						line.add((float)((double)result.matchCounts[i]/rupSet.getNumRuptures())+"");
						line.add((float)result.matchRates[i]+"");
						line.add((float)((double)result.matchRates[i]/totRate)+"");
						csv.addLine(line);
					}
				} catch (InterruptedException | ExecutionException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
			
			String csvName;
			if (removeOverlap)
				csvName = "results_remove_overlap.csv";
			else
				csvName = "results_retain_overlap.csv";
			csv.writeToFile(new File(resourcesDir, csvName));
		}
		
		// add TOC
//		lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 4));
//		lines.add(tocIndex, "## Table Of Contents");
		// write markdown
		MarkdownUtils.writeReadmeAndHTML(lines, markdownDir);
	}
	
	public static void main(String[] args) throws IOException, DocumentException {
		File etasInputDir = new File("/home/kevin/git/ucerf3-etas-launcher/inputs");
		
		boolean doSweep = true;
		boolean doHistorical = false;
		
		if (doSweep) {
			System.out.println("Doing sweep");
			
			FaultSystemSolution sol = FaultSystemIO.loadSol(new File(etasInputDir,
					"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"));
			
			File markdownDir = new File("/home/kevin/git/misc-research/ucerf3_etas/finite_section_mapping/param_sweep");
			Preconditions.checkState(markdownDir.exists() || markdownDir.mkdir());
			
			int threads = Integer.min(Runtime.getRuntime().availableProcessors(), 6);
			writeParamSweepMarkdown(markdownDir, sol, threads);
		}
		
		if (doHistorical) {
			System.out.println("Doing historical");
			File catFile = new File(etasInputDir, "u3_historical_catalog.txt");
			File xmlFile = new File(etasInputDir, "u3_historical_catalog_finite_fault_mappings.xml");
			
			File mainOutputDir = new File("/home/kevin/git/misc-research/ucerf3_etas/finite_section_mapping");
			
			Map<FaultModels, FaultSystemRupSet> rupSetMap = new HashMap<>();
			
			FaultSystemRupSet rupSet31 = FaultSystemIO.loadRupSet(new File(etasInputDir,
					"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"));
			rupSetMap.put(FaultModels.FM3_1, rupSet31);
//			FaultSystemRupSet rupSet32 = FaultSystemIO.loadRupSet(new File(etasInputDir,
//					"2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_2_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"));
//			rupSetMap.put(FaultModels.FM3_2, rupSet32);
			
			double[] minFracts = { 0.5 };
			double[] faultBuffers = { InversionTargetMFDs.FAULT_BUFFER, 1d };
			boolean[] removeOverlaps = { true, false };
			boolean replot = true;
			
			for (FaultModels fm : rupSetMap.keySet()) {
				System.out.println("Doing "+fm);
				FaultSystemRupSet rupSet = rupSetMap.get(fm);
				ObsEqkRupList inputRups = UCERF3_CatalogParser.loadCatalog(catFile);
				FiniteFaultMappingData.loadRuptureSurfaces(xmlFile, inputRups, fm, rupSet);
				
				for (double faultBuffer : faultBuffers) {
					for (double minFract : minFracts) {
						for (boolean removeOverlap : removeOverlaps) {
							String dirName = "u3_catalog-"+fm.encodeChoiceString()+"-minFract"+(float)minFract+"-polyBuffer"+(float)faultBuffer;
							if (removeOverlap)
								dirName += "-removeOverlap";
							File markdownDir = new File(mainOutputDir, dirName);
							
							System.out.println("Fault buffer: "+faultBuffer);
							System.out.println("Minimum fract inside polygon: "+minFract);
							System.out.println("Remove overlap? "+removeOverlap);
							
							FiniteFaultSectionResetCalc calc = new FiniteFaultSectionResetCalc(rupSet, minFract, faultBuffer, removeOverlap);
							
							calc.writeMatchMarkdown(markdownDir, inputRups, replot);
						}
					}
				}
			}
		}
	}

}
