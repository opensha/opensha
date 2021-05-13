package scratch.UCERF3.erf.ETAS.analysis;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.mapping.PoliticalBoundariesData;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.faultSurface.FaultSection;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.ComcatMetadata;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

/**
 * This plot will plot catalogs at different percentiles of total event count. It will always plot the smallest
 * and largest catalogs. If numToPlot=3, it will plot the smallest, most middle, and largest. This is nontrivial as we
 * iterate over the catalogs one at a time, and don't know what the most "middle" catalog truly is until the end. Instead,
 * the algorithm here keeps track of the most "middle" (or whatever percentile you're interested in) catalog yet, and updates
 * when a new most middle catalog is found. This should do pretty well for large sets of simulations, though it will be a
 * little off if the largest or smallest catalogs occur late in the process.
 * @author kevin
 *
 */
public class ETAS_SimulatedCatalogPlot extends ETAS_AbstractPlot {
	
	private List<List<ETAS_EqkRupture>> matchingCatalogs = null;
	private Percentile percentileCalc = new Percentile();
	private double smallest = Double.POSITIVE_INFINITY;
	private double largest = 0d;
	private double[] catalogSizes;
	private int catalogCount = 0;

	private double[] percentiles;
	private int percentileRecalcModulus = 1;
	private String prefix;
	private double maxMag = Double.NaN;
	
	private static final double[] default_map_durations = { 7d / 365.25, 30 / 365.25, 1d };
	private double[] durations;
	
	private boolean noTitles = false;
	private boolean includeInputEvents = true;
	private Region forceRegion = null;
	
	private static final int max_generation = 5;
	private boolean plotGenerations = false;

	public ETAS_SimulatedCatalogPlot(ETAS_Config config, ETAS_Launcher launcher, String prefix, double... percentiles) {
		super(config, launcher);
		this.prefix = prefix;
		Preconditions.checkState(percentiles.length > 0);
		this.percentiles = percentiles;
		List<Double> durations = new ArrayList<>();
		for (double duration : default_map_durations)
			if (duration <= config.getDuration())
				durations.add(duration);
		if (!durations.contains(config.getDuration()))
			durations.add(config.getDuration());
		this.durations = Doubles.toArray(durations);
	}

	@Override
	public int getVersion() {
		return 1;
	}

	@Override
	public boolean isFilterSpontaneous() {
		return false;
	}

	@Override
	protected void doProcessCatalog(ETAS_Catalog completeCatalog, ETAS_Catalog triggeredOnlyCatalog,
			FaultSystemSolution fss) {
		if (Double.isNaN(maxMag)) {
			maxMag = 0d;
			if (getConfig().hasTriggers())
				for (ETAS_EqkRupture trigger : getLauncher().getCombinedTriggers())
					maxMag = Math.max(maxMag, trigger.getMag());
		}
		double myCount = completeCatalog.size();
		smallest = Math.min(myCount, smallest);
		largest = Math.max(myCount, largest);
		if (matchingCatalogs == null) {
			// first time
			matchingCatalogs = new ArrayList<>();
			for (int i=0; i<percentiles.length; i++)
				matchingCatalogs.add(completeCatalog);
			catalogSizes = new double[0];
		}
		catalogCount++;
		catalogSizes = Doubles.ensureCapacity(catalogSizes, catalogCount, 1000);
		catalogSizes[catalogCount-1] = myCount;
		if (catalogCount % percentileRecalcModulus == 0 || myCount == smallest || myCount == largest)
			// recalculate percentiles periodically, or when a new largest or smallest is encountered
			percentileCalc.setData(catalogSizes, 0, catalogCount);
		if (catalogCount == 100)
			percentileRecalcModulus = 10;
		if (catalogCount == 1000)
			percentileRecalcModulus = 50;
		if (catalogCount == 10000)
			percentileRecalcModulus = 100;
		
		for (int p=0; p<percentiles.length; p++) {
			double expected = percentiles[p] == 0d ? 0d : percentileCalc.evaluate(percentiles[p]);
			double myDist = Math.abs(myCount - expected);
			double curDist = Math.abs((double)matchingCatalogs.get(p).size() - expected);
			if (myDist < curDist)
				matchingCatalogs.set(p, completeCatalog);
		}
		for (ETAS_EqkRupture rup : completeCatalog)
			maxMag = Math.max(maxMag, rup.getMag());
	}
	
	public void setHideTitles() {
		this.noTitles = true;
	}
	
	public void setHideInputEvents() {
		this.includeInputEvents = false;
	}

	public void setForceRegion(Region forceRegion) {
		this.forceRegion = forceRegion;
	}
	
	public void setMaxMag(double maxMag) {
		this.maxMag = maxMag;
	}
	
	public void setPlotDurations(double[] durations) {
		this.durations = durations;
	}
	
	public void setPlotGenerations(boolean plotGenerations) {
		this.plotGenerations = plotGenerations;
	}

	@Override
	protected List<? extends Runnable> doFinalize(File outputDir, FaultSystemSolution fss, ExecutorService exec)
			throws IOException {
		List<XY_DataSet> inputFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> inputChars = new ArrayList<>();
		
		// add CA
		XY_DataSet[] caXYs = PoliticalBoundariesData.loadCAOutlines();
		PlotCurveCharacterstics caOutlineChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 1f, Color.BLACK);
		for (XY_DataSet caXY : caXYs) {
			inputFuncs.add(caXY);
			inputChars.add(caOutlineChar);
		}
		
		// add faults to bottom of plot
		PlotCurveCharacterstics faultTraceChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.LIGHT_GRAY);
		PlotCurveCharacterstics faultOutlineChar = new PlotCurveCharacterstics(PlotLineType.DOTTED, 1.5f, Color.LIGHT_GRAY);
		
		boolean first = true;
		for (FaultSection sect : fss.getRupSet().getFaultSectionDataList()) {
			RuptureSurface surf = sect.getFaultSurface(1d, false, false);
			List<XY_DataSet> outlines = ETAS_EventMapPlotUtils.getSurfOutlines(surf);
			for (XY_DataSet outline : outlines) {
				inputFuncs.add(outline);
				inputChars.add(faultOutlineChar);
			}
			List<XY_DataSet> traces = ETAS_EventMapPlotUtils.getSurfTraces(surf);
			for (int i=0; i<traces.size(); i++) {
				XY_DataSet trace = traces.get(i);
				if (first) {
					trace.setName("Fault Traces");
					first = false;
				}
				inputFuncs.add(trace);
				inputChars.add(faultTraceChar);
			}
		}
		
		MinMaxAveTracker inputLatTrack = new MinMaxAveTracker();
		MinMaxAveTracker inputLonTrack = new MinMaxAveTracker();
		
		if (getConfig().hasTriggers() && includeInputEvents) {
			List<ETAS_EqkRupture> triggers = getLauncher().getCombinedTriggers();
			ETAS_EventMapPlotUtils.buildEventPlot(triggers, inputFuncs, inputChars, maxMag);
			for (ETAS_EqkRupture trigger : triggers) {
				Location loc = trigger.getHypocenterLocation();
				if (loc != null) {
					inputLatTrack.addValue(loc.getLatitude());
					inputLonTrack.addValue(loc.getLongitude());
				}
			}
			// reset color to gray
			first = true;
			for (int i=0; i<inputFuncs.size(); i++) {
				if (first && inputChars.get(i).getSymbol() != null) {
					inputFuncs.get(i).setName("Input Events");
					first = false;
				} else {
					inputFuncs.get(i).setName(null);
				}
				inputChars.get(i).setColor(Color.GRAY);
			}
			if (first)
				// no symbol ones, just use the line
				inputFuncs.get(inputFuncs.size()-1).setName("Input Events");
		}
		
		List<Runnable> runnables = new ArrayList<>();
		
		for (int i=0; i<percentiles.length; i++) {
			double percentile = percentiles[i];
			List<ETAS_EqkRupture> catalog = matchingCatalogs.get(i);
			System.out.println("Creating map for percentile: "+(float)percentile);
			if (percentile > 0)
				System.out.println("\tExpected number of events: "+(float)percentileCalc.evaluate(percentile));
			System.out.println("\tActual number of events: "+catalog.size());
			
			MinMaxAveTracker latTrack = new MinMaxAveTracker();
			latTrack.addFrom(inputLatTrack);
			MinMaxAveTracker lonTrack = new MinMaxAveTracker();
			lonTrack.addFrom(inputLonTrack);
			List<Location> catalogLocs = new ArrayList<>();
			for (ETAS_EqkRupture rup : catalog) {
				Location hypo = rup.getHypocenterLocation();
				latTrack.addValue(hypo.getLatitude());
				lonTrack.addValue(hypo.getLongitude());
				catalogLocs.add(hypo);
				// populate the surface
				if (rup.getFSSIndex() >= 0) {
					RuptureSurface surf = fss.getRupSet().getSurfaceForRupture(rup.getFSSIndex(), 1d);
					rup.setRuptureSurface(surf);
//					catalogLocs.addAll(surf.getEvenlyDiscritizedListOfLocsOnSurface());
				}
			}
			
			Region mapRegion;
			if (forceRegion == null) {
				// define region
				double maxSpan = Math.max(latTrack.getMax()-latTrack.getMin(), lonTrack.getMax()-lonTrack.getMin());
				double centerLat = latTrack.getAverage();
				double centerLon = lonTrack.getAverage();
//				System.out.println("Lon range: "+lonTrack.getMin()+" "+lonTrack.getMax());
//				System.out.println("Lat range: "+latTrack.getMin()+" "+latTrack.getMax());
//				System.out.println("Center: "+centerLat+", "+centerLon);
//				System.out.println("Span: "+maxSpan);
				double halfSpan = maxSpan*0.5;
				Location topRight = new Location(centerLat + halfSpan, centerLon + halfSpan);
				Location bottomLeft = new Location(centerLat - halfSpan, centerLon - halfSpan);
				// now buffer by 20km in each direction
				topRight = LocationUtils.location(topRight, new LocationVector(45, 20, 0d));
				bottomLeft = LocationUtils.location(bottomLeft, new LocationVector(225, 20, 0d));
				mapRegion = new Region(topRight, bottomLeft);
				// now try the shrink the region if it's larger than 3 degrees
				int minNumInside = (int)Math.round(catalogLocs.size()*0.95);
				double mySpan = maxSpan;
				while (mySpan > 3d) {
					mySpan = mySpan*0.9;
					halfSpan = mySpan*0.5;
					topRight = new Location(centerLat + halfSpan, centerLon + halfSpan);
					bottomLeft = new Location(centerLat - halfSpan, centerLon - halfSpan);
					// now buffer by 20km in each direction
					topRight = LocationUtils.location(topRight, new LocationVector(45, 20, 0d));
					bottomLeft = LocationUtils.location(bottomLeft, new LocationVector(225, 20, 0d));
					Region testRegion = new Region(topRight, bottomLeft);
					int numInside = 0;
					for (Location loc : catalogLocs)
						if (testRegion.contains(loc))
							numInside++;
					if (numInside >= minNumInside) {
						mapRegion = testRegion;
					} else {
						break;
					}
				}
				ComcatMetadata cMeta = getConfig().getComcatMetadata();
				if (cMeta != null && cMeta.region != null) {
					Region cReg = getConfig().getComcatMetadata().region;
					// make sure entire ComCat region is contained
					double minLat = Math.min(mapRegion.getMinLat(), cReg.getMinLat());
					double minLon = Math.min(mapRegion.getMinLon(), cReg.getMinLon());
					double maxLat = Math.max(mapRegion.getMaxLat(), cReg.getMaxLat());
					double maxLon = Math.max(mapRegion.getMaxLon(), cReg.getMaxLon());
					mapRegion = new Region(new Location(minLat, minLon),
							new Location(maxLat, maxLon));
				}
			} else {
				mapRegion = forceRegion;
			}
			
			for (double duration : durations) {
				List<ETAS_EqkRupture> subCatalog = new ArrayList<>();
				long maxOT = (long)(getConfig().getSimulationStartTimeMillis()
						+ duration*ProbabilityModelsCalc.MILLISEC_PER_YEAR+0.5);
				for (ETAS_EqkRupture rup : catalog) {
					if (rup.getOriginTime() > maxOT)
						break;
					else
						subCatalog.add(rup);
				}
				
				String myPrefix = prefix+"_p"+pLabel(percentile)+"_"+getTimeShortLabel(duration).replace(" ", "");
				String title = noTitles ? " " : "p"+pLabel(percentile)+" %-ile Catalog, "+subCatalog.size()
					+" Events, "+getTimeLabel(duration, false);
				
				runnables.add(new MapRunnable(outputDir, inputFuncs, inputChars, mapRegion, subCatalog, myPrefix, title, false));
				
				if (plotGenerations) {
					myPrefix += "_generations";
					runnables.add(new MapRunnable(outputDir, inputFuncs, inputChars, mapRegion, subCatalog, myPrefix, title, true));
				}
			}
		}
		return runnables;
	}

	private class MapRunnable implements Runnable {
		
		private File outputDir;
		private List<XY_DataSet> inputFuncs;
		private List<PlotCurveCharacterstics> inputChars;
		private Region mapRegion;
		private List<ETAS_EqkRupture> subCatalog;
		
		private String title;
		private String prefix;
		private boolean generation;

		public MapRunnable(File outputDir, List<XY_DataSet> inputFuncs, List<PlotCurveCharacterstics> inputChars,
				Region mapRegion, List<ETAS_EqkRupture> subCatalog, String prefix, String title, boolean generation) {
			this.outputDir = outputDir;
			this.inputFuncs = inputFuncs;
			this.inputChars = inputChars;
			this.mapRegion = mapRegion;
			this.subCatalog = subCatalog;
			this.prefix = prefix;
			this.title = title;
			this.generation = generation;
		}

		@Override
		public void run() {
			List<XY_DataSet> funcs = new ArrayList<>(inputFuncs);
			List<PlotCurveCharacterstics> chars = new ArrayList<>(inputChars);
			
			try {
				if (generation)
					ETAS_EventMapPlotUtils.buildGenerationPlot(subCatalog, funcs, chars, maxMag, max_generation);
				else
					ETAS_EventMapPlotUtils.buildEventPlot(subCatalog, funcs, chars, maxMag);
				ETAS_EventMapPlotUtils.writeMapPlot(funcs, chars, mapRegion, title, outputDir, prefix, 700);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
	}
	
	public static DecimalFormat pDF = new DecimalFormat("0.0#####");

	@Override
	public List<String> generateMarkdown(String relativePathToOutputDir, String topLevelHeading, String topLink)
			throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add(topLevelHeading+" Individual Simulated Catalog Maps");
		lines.add(topLink); lines.add("");
		
		lines.add("These are map plots of individual catalogs from the simulations, selected as the closest catalog "
				+ "to each of the given percentiles in terms of total number of events.");
		lines.add("");
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		table.initNewLine();
		table.addColumn("Duration");
		for (double percentile : percentiles)
			table.addColumn("p"+pLabel(percentile)+" %-ile");
		table.finalizeLine();
		for (double duration : durations) {
			table.initNewLine();
			table.addColumn("**"+getTimeLabel(duration, false)+"**");
			for (double percentile : percentiles)
				table.addColumn("![Map]("+relativePathToOutputDir+"/"+prefix+"_p"+pLabel(percentile)
					+"_"+getTimeShortLabel(duration).replace(" ", "")+".png)");
			table.finalizeLine();
			if (plotGenerations) {
				table.initNewLine();
				table.addColumn("*Event Generations*");
				for (double percentile : percentiles)
					table.addColumn("![Map]("+relativePathToOutputDir+"/"+prefix+"_p"+pLabel(percentile)
						+"_"+getTimeShortLabel(duration).replace(" ", "")+"_generations.png)");
				table.finalizeLine();
			}
		}
		
		lines.addAll(table.build());
		
		
		
		return lines;
	}
	
	private String pLabel(double percentile) {
		if (percentile == 100d)
			percentile = 100d*(catalogCount-1d)/(double)catalogCount;
		return pDF.format(percentile);
	}
	
	public static void main(String[] args) {
		File simDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
//				+ "2019_06_05-Spontaneous-includeSpont-historicalCatalog-full_td-1000yr");
//				+ "2019_06_05-Spontaneous-includeSpont-historicalCatalog-no_ert-1000yr");
//				+ "2019_07_04-SearlesValleyM64-includeSpont-full_td-10yr");
//				+ "2019-06-05_M7.1_SearlesValley_Sequence_UpdatedMw_and_depth");
//				+ "2019_07_06-SearlessValleySequenceFiniteFault-noSpont-full_td-10yr-start-noon");
//				+ "2019_07_06-SearlessValleySequenceFiniteFault-noSpont-full_td-10yr-following-M7.1");
//				+ "2019_07_11-ComCatM7p1_ci38457511_FiniteSurface-noSpont-full_td-scale1.14");
				+ "2019_07_16-ComCatM7p1_ci38457511_ShakeMapSurfaces-noSpont-full_td-scale1.14");
		File configFile = new File(simDir, "config.json");
		
		try {
			ETAS_Config config = ETAS_Config.readJSON(configFile);
			ETAS_Launcher launcher = new ETAS_Launcher(config, false);
			
			int maxNumCatalogs = 1000;
			
			ETAS_SimulatedCatalogPlot plot = new ETAS_SimulatedCatalogPlot(config, launcher, "sim_catalog_map",
					0d, 25d, 50d, 75d, 100d);
			File outputDir = new File(simDir, "plots");
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
			
			FaultSystemSolution fss = launcher.checkOutFSS();
			
			File inputFile = SimulationMarkdownGenerator.locateInputFile(config);
			int processed = 0;
			for (ETAS_Catalog catalog : ETAS_CatalogIO.getBinaryCatalogsIterable(inputFile, 0d)) {
				if (processed % 1000 == 0)
					System.out.println("Catalog "+processed);
				plot.processCatalog(catalog, fss);
				processed++;
				if (maxNumCatalogs > 0 && processed == maxNumCatalogs)
					break;
			}
			
			plot.finalize(outputDir, launcher.checkOutFSS());
			
			System.exit(0);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
