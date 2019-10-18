package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.FaultUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.SimpleFaultData;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.ComcatMetadata;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture.SimpleFault;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionTargetMFDs;

public class ETAS_TriggerRuptureFaultDistancesPlot extends ETAS_AbstractPlot {
	
	private List<FaultDistStats> distStats;
	private CSVFile<String> csv;
	private double maxDist;
	private File outputDir;
	
	private boolean hasFinite = false;

	public ETAS_TriggerRuptureFaultDistancesPlot(ETAS_Config config, ETAS_Launcher launcher, double maxDist) {
		super(config, launcher);
		this.maxDist = maxDist;
	}

	@Override
	public int getVersion() {
		return 1;
	}
	
	private class FaultDistStats implements Comparable<FaultDistStats> {
		private String parentName;
		private List<FaultSectionPrefData> subSects;
		private List<EvenlyGriddedSurface> subSectSurfs;
		private List<LocationList> sectLocs;
		private List<Region> polys;
		
		private int numHyposInsidePolygon = 0;
		private double maxMagHypoInsidePolygon = Double.NEGATIVE_INFINITY;
//		
		private int numSurfsInsidePolygon = 0;
		private double maxMagSurfsInsidePolygon = Double.NEGATIVE_INFINITY;
		
		private double maxMag = Double.NEGATIVE_INFINITY;
		private double minDistToAny = Double.POSITIVE_INFINITY;
		private double minPolyDistToAny = Double.POSITIVE_INFINITY;
		private double minDistToMax = Double.POSITIVE_INFINITY;
		private double minPolyDistToMax = Double.POSITIVE_INFINITY;
		private double minHypoDistToMax = Double.POSITIVE_INFINITY;
		private double minHypoPolyDistToMax = Double.POSITIVE_INFINITY;
		
		public FaultDistStats(String parentName) {
			this.parentName = parentName;
			this.subSects = new ArrayList<>();
			this.subSectSurfs = new ArrayList<>();
			this.sectLocs = new ArrayList<>();
			this.polys = new ArrayList<>();
		}
		
		public void addSubSect(FaultSectionPrefData sect, Region poly) {
			this.subSects.add(sect);
			EvenlyGriddedSurface surf = sect.getStirlingGriddedSurface(1d, false, false);
			this.subSectSurfs.add(surf);
			this.sectLocs.add(surf.getEvenlyDiscritizedListOfLocsOnSurface());
			this.polys.add(poly);
		}
		
		public void processTrigger(ETAS_EqkRupture trigger, LocationList surfLocs) {
			Location hypo = trigger.getHypocenterLocation();
			
			boolean isMaxMag = trigger.getMag() > maxMag;
			if (isMaxMag) {
				maxMag = trigger.getMag();
				minDistToMax = Double.POSITIVE_INFINITY;
				minPolyDistToMax = Double.POSITIVE_INFINITY;
			}
			
			boolean hypoContains = false;
			boolean surfContains = false;
			for (int i=0; i<subSects.size(); i++) {
				Region poly = polys.get(i);
				LocationList sectLocs = this.sectLocs.get(i);
				
				hypoContains = hypoContains || hypo != null && poly.contains(hypo);
				for (Location rupLoc : surfLocs) {
					surfContains = surfContains || poly.contains(rupLoc);
					double polyDist = poly.distanceToLocation(rupLoc);
					minPolyDistToAny = Math.min(minPolyDistToAny, polyDist);
					if (isMaxMag)
						minPolyDistToMax = Math.min(minPolyDistToMax, polyDist);
				}
//				if (!surfContains && minPolyDistToAny > 2*maxDist)
//					// far away, skip
//					continue;
				for (Location rupLoc : surfLocs) {
					for (Location sectLoc : sectLocs) {
						double dist = LocationUtils.linearDistanceFast(rupLoc, sectLoc);
						minDistToAny = Math.min(minDistToAny, dist);
						if (isMaxMag)
							minDistToMax = Math.min(minDistToMax, dist);
					}
				}
				
				if (isMaxMag && surfLocs.size() > 1 && hypo != null) {
					// check hypocenter
					for (Location sectLoc : sectLocs) {
						double dist = LocationUtils.linearDistanceFast(hypo, sectLoc);
						minHypoDistToMax = Math.min(minHypoDistToMax, dist);
					}
					double polyDist = poly.distanceToLocation(hypo);
					minHypoPolyDistToMax = Math.min(minHypoPolyDistToMax, polyDist);
				}
			}
			if (hypoContains) {
				numHyposInsidePolygon++;
				maxMagHypoInsidePolygon = Math.max(maxMagHypoInsidePolygon, trigger.getMag());
			}
			if (surfContains) {
				numSurfsInsidePolygon++;
				maxMagSurfsInsidePolygon = Math.max(maxMagSurfsInsidePolygon, trigger.getMag());
			}
		}

		@Override
		public int compareTo(FaultDistStats o) {
			if (numHyposInsidePolygon != o.numHyposInsidePolygon)
				return -Integer.compare(numHyposInsidePolygon, o.numHyposInsidePolygon);
			if (numSurfsInsidePolygon != o.numSurfsInsidePolygon)
				return -Integer.compare(numSurfsInsidePolygon, o.numSurfsInsidePolygon);
			return Double.compare(minDistToAny, o.minDistToAny);
		}
	}

	@Override
	public boolean isFilterSpontaneous() {
		return false;
	}

	@Override
	protected void doProcessCatalog(List<ETAS_EqkRupture> completeCatalog, List<ETAS_EqkRupture> triggeredOnlyCatalog,
			FaultSystemSolution fss) {
		// do nothing
	}

	@Override
	public List<? extends Runnable> doFinalize(File outputDir, FaultSystemSolution fss) throws IOException {
		this.outputDir = outputDir;
		List<FaultSectionPrefData> subSects = fss.getRupSet().getFaultSectionDataList();
		FaultPolyMgr polyMgr = FaultPolyMgr.create(subSects, InversionTargetMFDs.FAULT_BUFFER);
		System.out.println("Building polygons");
		
		Region mapRegion = ETAS_EventMapPlotUtils.getMapRegion(getConfig(), getLauncher());
		
		// determine region that we're interested in
		List<ETAS_EqkRupture> triggers = getLauncher().getTriggerRuptures();
		List<LocationList> triggerLocLists = new ArrayList<>();
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		for (ETAS_EqkRupture trigger : triggers) {
			LocationList surfLocs = trigger.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
			for (Location loc : surfLocs) {
				latTrack.addValue(loc.getLatitude());
				lonTrack.addValue(loc.getLongitude());
			}
			triggerLocLists.add(surfLocs);
		}
		latTrack.addValue(mapRegion.getMaxLat());
		latTrack.addValue(mapRegion.getMinLat());
		lonTrack.addValue(mapRegion.getMaxLon());
		lonTrack.addValue(mapRegion.getMinLon());
		ComcatMetadata meta = getConfig().getComcatMetadata();
		if (meta != null && meta.region != null) {
			latTrack.addValue(meta.region.getMinLat());
			latTrack.addValue(meta.region.getMaxLat());
			lonTrack.addValue(meta.region.getMinLon());
			lonTrack.addValue(meta.region.getMaxLon());
		}
		Location maxLoc = new Location(latTrack.getMax(), lonTrack.getMax());
		Location minLoc = new Location(latTrack.getMin(), lonTrack.getMin());
		// buffer by 1.5*maxDist
		maxLoc = LocationUtils.location(maxLoc, Math.PI/4d, maxDist*1.5);
		minLoc = LocationUtils.location(minLoc, 5d*Math.PI/4d, maxDist*1.5);
		Region totSurfRegion = new Region(maxLoc, minLoc);
		List<FaultSectionPrefData> nearSects = new ArrayList<>();
		for (FaultSectionPrefData sect : subSects) {
			boolean contains = false;
			for (Location loc : sect.getFaultTrace())
				contains = contains || totSurfRegion.contains(loc);
			if (contains)
				nearSects.add(sect);
		}
		
		boolean skip = nearSects.size() > 500 && triggers.size() > 500;
		
		Map<Integer, FaultDistStats> statsMap = new HashMap<>();
		for (FaultSectionPrefData sect : nearSects) {
			Integer parentID = sect.getParentSectionId();
			if (!statsMap.containsKey(parentID))
				statsMap.put(parentID, new FaultDistStats(sect.getParentSectionName()));
			statsMap.get(parentID).addSubSect(sect, polyMgr.getPoly(sect.getSectionId()));
		}
		distStats = new ArrayList<>(statsMap.values());
		
		if (skip) {
			System.out.println("Skipping distances as there are too many triggers ("+triggers.size()+")"
					+ " and nearby sections ("+nearSects.size()+")");
		} else {
			System.out.println("Will compute distances for "+nearSects.size()
				+" nearby sections (of "+subSects.size()+" total)");
			
			System.out.println("Processing "+triggers.size()+" triggers");
			for (int i = 0; i < triggers.size(); i++) {
				ETAS_EqkRupture trigger = triggers.get(i);
				hasFinite = hasFinite || !trigger.getRuptureSurface().isPointSurface();
				LocationList surfLocs = triggerLocLists.get(i);
				for (FaultDistStats stats : distStats)
					stats.processTrigger(trigger, surfLocs);
			}
			Collections.sort(distStats);

			csv = new CSVFile<>(true);
			List<String> header = new ArrayList<>();
			header.add("Section Name");
			header.add("Strike, Dip, Rake");
			if (triggers.size() > 1) {
				header.add("# Hypos In Poly");
				header.add("Max Mag w/ Hypo In Poly");
				header.add("# Surfs In Poly");
				header.add("Max Mag w/ Surf In Poly");
				header.add("Min Dist To Any (km)");
				header.add("Min Poly Dist To Any (km)");
				header.add("Min Dist To Largest (km)");
				header.add("Min Poly Dist To Largest (km)");
				if (hasFinite) {
					header.add("Min Hypo Dist To Largest (km)");
					header.add("Min Hypo Poly Dist To Largest (km)");
				}
			} else {
				header.add("Hypocenter In Polygon?");
				header.add("Surface In Polygon?");
				if (hasFinite) {
					header.add("Minimum Surface Distance (km)");
					header.add("Minimum Surface Poly Distance (km)");
					header.add("Minimum Hypo Distance (km)");
					header.add("Minimum Hypo Poly Distance (km)");
				} else {
					header.add("Minimum Distance (km)");
					header.add("Minimum Poly Distance (km)");
				}
			}
			csv.addLine(header);

			for (FaultDistStats dists : distStats) {
				List<String> line = new ArrayList<>();
				line.add(dists.parentName);
				List<Double> strikes = new ArrayList<>();
				List<Double> dips = new ArrayList<>();
				List<Double> rakes = new ArrayList<>();
				for (FaultSectionPrefData sect : dists.subSects) {
					strikes.add(sect.getFaultTrace().getAveStrike());
					dips.add(sect.getAveDip());
					rakes.add(sect.getAveRake());
				}
				double aveStrike = FaultUtils.getAngleAverage(strikes);
				double aveDip = FaultUtils.getAngleAverage(dips);
				double aveRake = FaultUtils.getInRakeRange(FaultUtils.getAngleAverage(rakes));
				line.add(Math.round(aveStrike)+", "+Math.round(aveDip)+", "+Math.round(aveRake));
				if (triggers.size() > 1) {
					line.add(dists.numHyposInsidePolygon+"");
					line.add(Double.isFinite(dists.maxMagHypoInsidePolygon) ? (float)dists.maxMagHypoInsidePolygon+"" : "");
					line.add(dists.numSurfsInsidePolygon+"");
					line.add(Double.isFinite(dists.maxMagSurfsInsidePolygon) ? (float)dists.maxMagSurfsInsidePolygon+"" : "");
					line.add(distStr(dists.minDistToAny));
					line.add(distStr(dists.minPolyDistToAny));
					line.add(distStr(dists.minDistToMax));
					line.add(distStr(dists.minPolyDistToMax));
					if (hasFinite) {
						line.add(distStr(dists.minHypoDistToMax));
						line.add(distStr(dists.minHypoPolyDistToMax));
					}
				} else {
					line.add(dists.numHyposInsidePolygon > 0 ? "true" : "false");
					line.add(dists.numSurfsInsidePolygon > 0 ? "true" : "false");
					line.add(distStr(dists.minDistToAny));
					line.add(distStr(dists.minPolyDistToAny));
					if (hasFinite) {
						line.add(distStr(dists.minHypoDistToMax));
						line.add(distStr(dists.minHypoPolyDistToMax));
					}
				}
				csv.addLine(line);
			}
			csv.writeToFile(new File(outputDir, "trigger_rup_fault_distances.csv"));
		}
		
		makeMapPlot(outputDir, "trigger_rup_fault_map", triggers, mapRegion);
		if (!skip)
			makeDepthPlot(outputDir, "trigger_rup_depth_map", triggers);
		
		return null;
	}
	
	private static DecimalFormat distDF = new DecimalFormat("0.000");
	private static String distStr(double dist) {
		if (Double.isFinite(dist))
			return distDF.format(dist);
		return "N/A";
	}
	
	private void makeMapPlot(File outputDir, String prefix, List<ETAS_EqkRupture> triggers, Region mapRegion) throws IOException {
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		XY_DataSet[] caXYs = PoliticalBoundariesData.loadCAOutlines();
		PlotCurveCharacterstics caOutlineChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK);
		for (XY_DataSet caXY : caXYs) {
			funcs.add(caXY);
			chars.add(caOutlineChar);
		}

		List<XY_DataSet> traceXYs = new ArrayList<>();
		List<XY_DataSet> outlineXYs = new ArrayList<>();
		List<XY_DataSet> polyXYs = new ArrayList<>();
		PlotCurveCharacterstics faultTraceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK);
		PlotCurveCharacterstics faultOutlineChar = new PlotCurveCharacterstics(PlotLineType.DOTTED, 2f, Color.BLACK);
		PlotCurveCharacterstics faultPolyChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1.5f, Color.LIGHT_GRAY);
		
		// add faults and polygons
		for (FaultDistStats stats : distStats) {
			for (int i=0; i<stats.subSects.size(); i++) {
				Region poly = stats.polys.get(i);
				if (poly != null) {
					XY_DataSet polyXY = new DefaultXY_DataSet();
					for (Location loc : poly.getBorder())
						polyXY.set(loc.getLongitude(), loc.getLatitude());
					polyXY.set(polyXY.get(0)); // close it
					polyXYs.add(polyXY);
				}
				traceXYs.addAll(ETAS_EventMapPlotUtils.getSurfTraces(stats.subSectSurfs.get(i)));
				outlineXYs.addAll(ETAS_EventMapPlotUtils.getSurfOutlines(stats.subSectSurfs.get(i)));
			}
		}
		
		for (int i=0; i<polyXYs.size(); i++) {
			if (i == 0)
				polyXYs.get(i).setName("Fault Polygons");
			funcs.add(polyXYs.get(i));
			chars.add(faultPolyChar);
		}
		
		for (int i=0; i<outlineXYs.size(); i++) {
			funcs.add(outlineXYs.get(i));
			chars.add(faultOutlineChar);
		}
		
		for (int i=0; i<traceXYs.size(); i++) {
			if (i == 0)
				traceXYs.get(i).setName("Fault Traces");
			funcs.add(traceXYs.get(i));
			chars.add(faultTraceChar);
		}
		
		ETAS_EventMapPlotUtils.buildEventPlot(triggers, funcs, chars);
		
		ETAS_EventMapPlotUtils.writeMapPlot(funcs, chars, mapRegion, "Trigger Ruptures & Faults", outputDir, prefix);
	}
	
	private void makeDepthPlot(File outputDir, String prefix, List<ETAS_EqkRupture> triggers) throws IOException {
		double surfMag = Double.NEGATIVE_INFINITY;
		RuptureSurface surf = null;
		for (ETAS_EqkRupture trigger : triggers) {
			RuptureSurface mySurf = trigger.getRuptureSurface();
			if (!mySurf.isPointSurface() && trigger.getMag() > surfMag) {
				surf = mySurf;
				surfMag = trigger.getMag();
			}
		}
		if (triggers.isEmpty() || surf == null || surf.isPointSurface()) {
			System.out.println("Skipping depth plot:");
			if (triggers.isEmpty())
				System.out.println("\tno triggers");
			if (surf == null)
				System.out.println("\tno surf");
			else if (surf.isPointSurface())
				System.out.println("\tpoint surface ("+ClassUtils.getClassNameWithoutPackage(surf.getClass())+")");
			return;
		}
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		ETAS_EventMapPlotUtils.buildEventDepthPlot(triggers, funcs, chars, surf);
		
		ETAS_EventMapPlotUtils.writeDepthPlot(funcs, chars, "Trigger Rupture Depth Profile", outputDir, prefix);
	}
	
//	static void makeMagTimePlot

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink)
			throws IOException {
		List<String> lines = new ArrayList<>();
		
		lines.add(topLevelHeading+" Trigger Rupture Fault Map");
		lines.add(topLink); lines.add("");
		lines.add("![Map]("+relativePathToOutputDir+"/trigger_rup_fault_map.png)");
		
		File depthFile = new File(outputDir, "trigger_rup_depth_map.png");
		if (depthFile.exists()) {
			lines.add(topLevelHeading+" Trigger Rupture Depth Map");
			lines.add(topLink); lines.add("");
			lines.add("![Map]("+relativePathToOutputDir+"/trigger_rup_depth_map.png)");
		}
		
		if (csv == null)
			// we skipped distance calculation
			return lines;
		
		lines.add("");
		lines.add(topLevelHeading+" Fault Distances To Triggers");
		lines.add(topLink); lines.add("");
		
		CSVFile<String> filteredCSV = new CSVFile<>(true);
		filteredCSV.addLine(csv.getLine(0));
		for (int i=0; i<distStats.size(); i++) {
			if (distStats.get(i).minDistToAny <= maxDist)
				filteredCSV.addLine(csv.getLine(i+1));
		}
		
		if (filteredCSV.getNumRows() > 1) {
			lines.addAll(MarkdownUtils.tableFromCSV(filteredCSV, false).build());
		} else {
			lines.add("No fault sections within "+(float)maxDist+" km of any trigger rupture");
		}
		
		return lines;
	}
	
	public static void main(String[] args) {
		File simDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
//				+ "2019_06_05-Spontaneous-includeSpont-historicalCatalog-full_td-1000yr");
//				+ "2019_06_05-Spontaneous-includeSpont-historicalCatalog-no_ert-1000yr");
//				+ "2019_07_04-SearlesValleyM64-includeSpont-full_td-10yr");
//				+ "2019-06-05_M7.1_SearlesValley_Sequence_UpdatedMw_and_depth");
//				+ "2019_07_06-SearlessValleySequenceFiniteFault-noSpont-full_td-10yr-start-noon");
//				+ "2019_07_06-SearlessValleySequenceFiniteFault-noSpont-full_td-10yr-following-M7.1");
				+ "2019_09_04-ComCatM7p1_ci38457511_ShakeMapSurfaces_CulledSurface");
		File configFile = new File(simDir, "config.json");
		
		try {
			ETAS_Config config = ETAS_Config.readJSON(configFile);
			ETAS_Launcher launcher = new ETAS_Launcher(config, false);
			
			ETAS_TriggerRuptureFaultDistancesPlot plot = new ETAS_TriggerRuptureFaultDistancesPlot(config, launcher, 20);
			File outputDir = new File(simDir, "plots");
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
			plot.finalize(outputDir, launcher.checkOutFSS());
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
