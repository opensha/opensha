package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.axis.TickUnits;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.opensha.commons.data.CSVFile;
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
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.nshmp2.erf.source.PointSource;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.EvenlyGriddedSurface;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.SimpleFaultData;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture.SimpleFault;
import scratch.UCERF3.griddedSeismicity.FaultPolyMgr;
import scratch.UCERF3.inversion.InversionTargetMFDs;

public class ETAS_TriggerRuptureFaultDistancesPlot extends ETAS_AbstractPlot {
	
	private List<FaultDistStats> distStats;
	private CSVFile<String> csv;
	private double maxDist;
	private File outputDir;

	public ETAS_TriggerRuptureFaultDistancesPlot(ETAS_Config config, ETAS_Launcher launcher, double maxDist) {
		super(config, launcher);
		this.maxDist = maxDist;
	}
	
	private class FaultDistStats implements Comparable<FaultDistStats> {
		private String parentName;
		private List<FaultSectionPrefData> subSects;
		private List<EvenlyGriddedSurface> subSectSurfs;
		private List<LocationList> sectLocs;
		private List<Region> polys;
		
		private int numHyposInsidePolygon = 0;
		private double maxMagHypoInsidePolygon = Double.NEGATIVE_INFINITY;
		
		private int numSurfsInsidePolygon = 0;
		private double maxMagSurfsInsidePolygon = Double.NEGATIVE_INFINITY;
		
		private double maxMag = Double.NEGATIVE_INFINITY;
		private double minDistToAny = Double.POSITIVE_INFINITY;
		private double minPolyDistToAny = Double.POSITIVE_INFINITY;
		private double minDistToMax = Double.POSITIVE_INFINITY;
		private double minPolyDistToMax = Double.POSITIVE_INFINITY;
		
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
					for (Location sectLoc : sectLocs) {
						double dist = LocationUtils.linearDistanceFast(rupLoc, sectLoc);
						minDistToAny = Math.min(minDistToAny, dist);
						if (isMaxMag)
							minDistToMax = Math.min(minDistToMax, dist);
					}
					double polyDist = poly.distanceToLocation(rupLoc);
					minPolyDistToAny = Math.min(minPolyDistToAny, polyDist);
					if (isMaxMag)
						minPolyDistToMax = Math.min(minPolyDistToMax, polyDist);
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
	public void finalize(File outputDir, FaultSystemSolution fss) throws IOException {
		this.outputDir = outputDir;
		List<FaultSectionPrefData> subSects = fss.getRupSet().getFaultSectionDataList();
		FaultPolyMgr polyMgr = FaultPolyMgr.create(subSects, InversionTargetMFDs.FAULT_BUFFER);
		System.out.println("Building polygons");
		Map<Integer, FaultDistStats> statsMap = new HashMap<>();
		for (FaultSectionPrefData sect : subSects) {
			Integer parentID = sect.getParentSectionId();
			if (!statsMap.containsKey(parentID))
				statsMap.put(parentID, new FaultDistStats(sect.getParentSectionName()));
			statsMap.get(parentID).addSubSect(sect, polyMgr.getPoly(sect.getSectionId()));
		}
		distStats = new ArrayList<>(statsMap.values());
		List<ETAS_EqkRupture> triggers = getLauncher().getTriggerRuptures();
		System.out.println("Processing "+triggers.size()+" triggers");
		for (ETAS_EqkRupture trigger : triggers) {
			LocationList surfLocs = trigger.getRuptureSurface().getEvenlyDiscritizedListOfLocsOnSurface();
			for (FaultDistStats stats : distStats)
				stats.processTrigger(trigger, surfLocs);
		}
		Collections.sort(distStats);
		
		csv = new CSVFile<>(true);
		List<String> header = new ArrayList<>();
		header.add("Section Name");
		if (triggers.size() > 1) {
			header.add("# Hypos In Poly");
			header.add("Max Mag w/ Hypo In Poly");
			header.add("# Surfs In Poly");
			header.add("Max Mag w/ Surf In Poly");
			header.add("Min Dist To Any (km)");
			header.add("Min Poly Dist To Any (km)");
			header.add("Min Dist To Largest (km)");
			header.add("Min Poly Dist To Largest (km)");
		} else {
			header.add("Hypocenter In Polygon?");
			header.add("Surface In Polygon?");
			header.add("Minimum Distance (km)");
			header.add("Minimum Poly Distance (km)");
		}
		csv.addLine(header);
		for (FaultDistStats dists : distStats) {
			List<String> line = new ArrayList<>();
			line.add(dists.parentName);
			if (triggers.size() > 1) {
				line.add(dists.numHyposInsidePolygon+"");
				line.add(Double.isFinite(dists.maxMagHypoInsidePolygon) ? (float)dists.maxMagHypoInsidePolygon+"" : "");
				line.add(dists.numSurfsInsidePolygon+"");
				line.add(Double.isFinite(dists.maxMagSurfsInsidePolygon) ? (float)dists.maxMagSurfsInsidePolygon+"" : "");
				line.add((float)dists.minDistToAny+"");
				line.add((float)dists.minPolyDistToAny+"");
				line.add((float)dists.minDistToMax+"");
				line.add((float)dists.minPolyDistToMax+"");
			} else {
				line.add(dists.numHyposInsidePolygon > 0 ? "true" : "false");
				line.add(dists.numSurfsInsidePolygon > 0 ? "true" : "false");
				line.add((float)dists.minDistToAny+"");
				line.add((float)dists.minPolyDistToAny+"");
			}
			csv.addLine(line);
		}
		csv.writeToFile(new File(outputDir, "trigger_rup_fault_distances.csv"));
		
		makeMapPlot(outputDir, "trigger_rup_fault_map", triggers);
		makeDepthPlot(outputDir, "trigger_rup_depth_map", triggers);
	}
	
	private void makeMapPlot(File outputDir, String prefix, List<ETAS_EqkRupture> triggers) throws IOException {
		Region mapRegion = ETAS_EventMapPlotUtils.getMapRegion(getConfig(), getLauncher());
		
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		XY_DataSet[] caXYs = PoliticalBoundariesData.loadCAOutlines();
		PlotCurveCharacterstics caOutlineChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.LIGHT_GRAY);
		for (XY_DataSet caXY : caXYs) {
			funcs.add(caXY);
			chars.add(caOutlineChar);
		}

		List<XY_DataSet> traceXYs = new ArrayList<>();
		List<XY_DataSet> outlineXYs = new ArrayList<>();
		List<XY_DataSet> polyXYs = new ArrayList<>();
		PlotCurveCharacterstics faultTraceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK);
		PlotCurveCharacterstics faultOutlineChar = new PlotCurveCharacterstics(PlotLineType.DOTTED, 2f, Color.BLACK);
		PlotCurveCharacterstics faultPolyChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1.5f, Color.GRAY);
		
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
		double sfdMag = Double.POSITIVE_INFINITY;
		SimpleFaultData[] sfds = null;
		for (TriggerRupture rup : getConfig().getTriggerRuptures()) {
			if (rup instanceof SimpleFault) {
				SimpleFault sfRup = (SimpleFault)rup;
				if (sfds != null && sfRup.mag < sfdMag)
					continue;
				sfdMag = sfRup.mag;
				sfds = sfRup.sfds;
			}
		}
		List<XY_DataSet> funcs = new ArrayList<>();
		List<PlotCurveCharacterstics> chars = new ArrayList<>();
		
		ETAS_EventMapPlotUtils.buildEventDepthPlot(triggers, funcs, chars, sfds);
		
		ETAS_EventMapPlotUtils.writeDepthPlot(funcs, chars, "Trigger Rupture Depth Profile", outputDir, prefix);
	}

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
				+ "2019_07_06-SearlessValleySequenceFiniteFault-noSpont-full_td-10yr-following-M7.1");
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
