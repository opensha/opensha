package org.opensha.commons.data.comcat.plot;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.comcat.ComcatAccessor;
import org.opensha.commons.data.comcat.ComcatRegion;
import org.opensha.commons.data.comcat.ComcatRegionAdapter;
import org.opensha.commons.data.function.DefaultXY_DataSet;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.data.function.XY_DataSet;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.LocationVector;
import org.opensha.commons.geo.Region;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ComparablePairing;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.faultSurface.StirlingGriddedSurface;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import gov.usgs.earthquake.event.EventQuery;
import gov.usgs.earthquake.event.EventWebService;
import gov.usgs.earthquake.event.JsonEvent;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_SimulationMetadata;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_ComcatComparePlot;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_FaultParticipationPlot;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_HazardChangePlot;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_MFD_Plot;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_MFD_Plot.MFD_Stats;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_CatalogIteration;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.BinaryFilteredOutputConfig;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.ComcatMetadata;

public class ComcatReportPageGen {
	
	private ComcatAccessor accessor;
	private ObsEqkRupture mainshock;
	private Region region;
	private double minFetchMag;
	private double daysBefore;
	
	private static final double min_radius = 10;
	private double radius;
	
	private long originTime;
	private String placeName;
	
	private List<ObsEqkRupture> foreshocks;
	private List<ObsEqkRupture> aftershocks;
	private ComcatDataPlotter plotter;
	
	private EventWebService service;
	
	private Collection<FaultSectionPrefData> faults;
	
	private ETAS_Config etasRun;

	public ComcatReportPageGen(String eventID, Region region, double minFetchMag, double daysBefore) {
		ComcatAccessor accessor = new ComcatAccessor();
		
		ObsEqkRupture mainshock = accessor.fetchEvent(eventID, false, true);
		
		init(accessor, mainshock, region, minFetchMag, daysBefore);
	}
	
	public ComcatReportPageGen(String eventID, double radius, double minFetchMag, double daysBefore) {
		ComcatAccessor accessor = new ComcatAccessor();
		
		ObsEqkRupture mainshock = accessor.fetchEvent(eventID, false, true);
		
		this.radius = radius;
		Region region = new Region(mainshock.getHypocenterLocation(), radius);
		
		init(accessor, mainshock, region, minFetchMag, daysBefore);
	}
	
	public ComcatReportPageGen(String eventID, double minFetchMag, double daysBefore) {
		ComcatAccessor accessor = new ComcatAccessor();
		
		ObsEqkRupture mainshock = accessor.fetchEvent(eventID, false, true);
		
		this.radius = new WC1994_MagLengthRelationship().getMedianLength(mainshock.getMag());
		System.out.println("WC 1994 Radius: "+(float)radius);
		if (radius < min_radius) {
			System.out.println("Reverting to min radius of "+(float)min_radius);
			radius = min_radius;
		}
		Region region = new Region(mainshock.getHypocenterLocation(), radius);
		
		init(accessor, mainshock, region, minFetchMag, daysBefore);
	}
	
	private void init(ComcatAccessor accessor, ObsEqkRupture mainshock, Region region,
			double minFetchMag, double daysBefore) {
		this.accessor = accessor;
		this.mainshock = mainshock;
		this.region = region;
		this.minFetchMag = minFetchMag;
		this.daysBefore = daysBefore;
		
		System.out.println("Mainshock is a M"+(float)mainshock.getMag());
		System.out.println("\tHypocenter: "+mainshock.getHypocenterLocation());
		
		originTime = mainshock.getOriginTime();
		placeName = null;
		for (Parameter<?> param : Lists.newArrayList(mainshock.getAddedParametersIterator())) {
			if (param.getName().equals(ComcatAccessor.PARAM_NAME_DESCRIPTION)) {
				placeName = param.getValue().toString();
				break;
			}
		}
		System.out.println("Place name: "+placeName);
		
		double minDepth = -10; // for fore/aftershocks;
		double maxDepth = Math.max(30, 2*mainshock.getHypocenterLocation().getDepth());
		
		ComcatRegion cReg = region instanceof ComcatRegion ? (ComcatRegion)region : new ComcatRegionAdapter(region);
		if (daysBefore > 0) {
			long startTime = originTime - (long)(ComcatDataPlotter.MILLISEC_PER_DAY*daysBefore);
			System.out.println("Fetching "+(float)(daysBefore)+" days of foreshocks");
			foreshocks = accessor.fetchEventList(mainshock.getEventId(), startTime, originTime,
					minDepth, maxDepth, cReg, false, false, minFetchMag);
			double maxMag = Double.NEGATIVE_INFINITY;
			for (ObsEqkRupture rup : foreshocks)
				maxMag = Math.max(maxMag, rup.getMag());
			System.out.println("Found "+foreshocks.size()+" foreshocks, maxMag="+maxMag);
		}
		
		long endTime = System.currentTimeMillis();
		System.out.println("Fetching aftershocks");
		aftershocks = accessor.fetchEventList(mainshock.getEventId(), originTime, endTime,
				minDepth, maxDepth, cReg, false, false, minFetchMag);
		double maxMag = Double.NEGATIVE_INFINITY;
		for (ObsEqkRupture rup : aftershocks)
			maxMag = Math.max(maxMag, rup.getMag());
		System.out.println("Found "+aftershocks.size()+" aftershocks, maxMag="+maxMag);
		
		plotter = new ComcatDataPlotter(mainshock, originTime, endTime, foreshocks, aftershocks);
		
		service = accessor.getEventWebService();
	}
	
	public String generateDirName() {
		// TODO UTC insead?
		DateFormat df = new SimpleDateFormat("yyyy_MM_dd");
		String name = df.format(new Date(originTime));
		name += "-"+mainshock.getEventId()+"-M"+optionalDigitDF.format(mainshock.getMag());
		String placeStripped = placeName.replaceAll("\\W+", "_"); 
		while (placeStripped.contains("__"))
			placeStripped = placeStripped.replaceAll("__", "_");
		name += "-"+placeStripped;
		return name;
	}
	
	public void addETAS(ETAS_Config config) {
		etasRun = config;
	}
	
	public void generateReport(File outputDir, String executiveSummary) throws IOException {
		File resourcesDir = new File(outputDir, "resources");
		Preconditions.checkState(resourcesDir.exists() || resourcesDir.mkdir());
		
		List<String> lines = new ArrayList<>();
		
		lines.add("# "+optionalDigitDF.format(mainshock.getMag())+", "+placeName);
		lines.add("");
		if (executiveSummary != null && !executiveSummary.isEmpty()) {
			lines.add(executiveSummary);
			lines.add("");
		}
		
		int tocIndex = lines.size();
		String topLink = "*[(top)](#table-of-contents)*";
		lines.add("");
		
		lines.add("## Mainshock Details");
		lines.add(topLink); lines.add("");
		lines.addAll(generateDetailLines(mainshock, resourcesDir, "##", topLink));
		
		lines.add("## Sequence Details");
		lines.add(topLink); lines.add("");
		SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss z");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
//		"Last updated at "+df.format(new Date(curTime))
//		+", "+getTimeLabel(curDuration, true).toLowerCase()+" after the simulation start time."
//		lines.add()
		String line = "These plots show the aftershock sequence, using data sourced from "
				+ "[ComCat](https://earthquake.usgs.gov/data/comcat/). They were last updated at "
				+df.format(new Date(plotter.getEndTime()))+", ";
		long deltaMillis = plotter.getEndTime() - originTime;
		double deltaSecs = (double)deltaMillis/1000d;
		double deltaMins = deltaSecs/60d;
		double deltaHours = deltaMins/60d;
		double deltaDays = deltaHours/24d;
		if (deltaDays > 2)
			line += optionalDigitDF.format(deltaDays)+" days";
		else if (deltaHours > 2)
			line += optionalDigitDF.format(deltaHours)+" hours";
		else
			line += optionalDigitDF.format(deltaMins)+" minutes";
		line += " after the mainshock.";
		lines.add(line);
		lines.add("");
		line = aftershocks.size()+" M&ge;"+optionalDigitDF.format(minFetchMag)+" earthquakes";
		if (radius > 0d) {
			line += " within "+optionalDigitDF.format(radius)+" km of the mainshock's epicenter.";
		} else {
			double maxDist = 0d;
			for (Location loc : region.getBorder())
				maxDist = Math.max(maxDist, LocationUtils.horzDistanceFast(loc, mainshock.getHypocenterLocation()));
			line += " within a custom region that lies within a circle with radius "
				+optionalDigitDF.format(radius)+" km from the mainshock's epicenter.";
		}
		lines.add(line);
		lines.add("");
		
		if (aftershocks.size() > 0) {
			double maxAftershock = minFetchMag;
			for (ObsEqkRupture rup : aftershocks)
				maxAftershock = Math.max(maxAftershock, rup.getMag());
			double minFloor = Math.floor(minFetchMag);
			double maxFloor = Math.floor(maxAftershock);
			int num = (int)(Math.round(maxFloor-minFloor)+1);
			EvenlyDiscretizedFunc magFunc = new EvenlyDiscretizedFunc(Math.floor(minFetchMag)+0.5, num, 1d);
			TableBuilder table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			table.addColumn("");
			List<Long> maxOTs = new ArrayList<>();
			long hour = 1000l*60l*60l;
			if (deltaHours > 1d) {
				table.addColumn("First Hour");
				maxOTs.add(originTime + hour);
			}
			long day = hour*24l;
			if (deltaDays >= 1d) {
				table.addColumn("First Day");
				maxOTs.add(originTime + day);
			}
			if (deltaDays >= 7d) {
				table.addColumn("First Week");
				maxOTs.add(originTime + 7l*day);
			}
			if (deltaDays >= 30d) {
				table.addColumn("First Month");
				maxOTs.add(originTime + 30l*day);
			}
			table.addColumn("To Date");
			maxOTs.add(plotter.getEndTime());
			table.finalizeLine();
			int[][] counts = new int[magFunc.size()][maxOTs.size()];
			for (ObsEqkRupture rup : aftershocks) {
				int m = magFunc.getClosestXIndex(rup.getMag());
				long ot = rup.getOriginTime();
				for (int mi=0; mi<=m; mi++)
					for (int oi=0; oi<maxOTs.size(); oi++)
						if (ot <= maxOTs.get(oi))
							counts[mi][oi]++;
			}
			for (int mi=0; mi<magFunc.size(); mi++) {
				table.initNewLine();
				table.addColumn("**M "+(int)magFunc.getX(mi)+"**");
				for (int count : counts[mi])
					table.addColumn(count);
				table.finalizeLine();
			}
			lines.add("");
			lines.addAll(table.build());
			
			lines.add("### Magnitude Vs. Time Plot");
			lines.add(topLink); lines.add("");
			line = "This plot shows the magnitude vs. time evolution of the sequence. The mainshock is ploted "
					+ "as a brown circle";
			if (plotter.getForeshocks() != null && !plotter.getForeshocks().isEmpty())
				line += ", foreshocks are plotted as magenta circles";
			line += ", and aftershocks are plotted as cyan circles.";
			lines.add(line);
			lines.add("");
			
			List<String> magTimeTitles = new ArrayList<>();
			List<String> magTimePrefixes = new ArrayList<>();
			if (deltaDays > 7d) {
				magTimeTitles.add("First Week");
				String prefix = "aftershocks_mag_vs_time_week";
				magTimePrefixes.add(prefix);
				plotter.plotMagTimeFunc(resourcesDir, prefix, "Magnitude Vs. Time", null, 7d, null);
			}
			if (deltaDays > 30d) {
				magTimeTitles.add("First Month");
				String prefix = "aftershocks_mag_vs_time_month";
				magTimePrefixes.add(prefix);
				plotter.plotMagTimeFunc(resourcesDir, prefix, "Magnitude Vs. Time", null, 30d, null);
			}
			magTimeTitles.add("To Date");
			String fullPrefix = "aftershocks_mag_vs_time";
			magTimePrefixes.add(fullPrefix);
			plotter.plotMagTimeFunc(resourcesDir, fullPrefix, "Magnitude Vs. Time", null, deltaDays, null);
			
			if (magTimeTitles.size() > 1) {
				table = MarkdownUtils.tableBuilder();
				table.addLine(magTimeTitles);
				table.initNewLine();
				for (String prefix : magTimePrefixes)
					table.addColumn("![Mag vs Time Plot](resources/"+prefix+".png)");
				table.finalizeLine();
				lines.addAll(table.build());
			} else {
				lines.add("![Mag vs Time Plot](resources/"+fullPrefix+".png)");
			}
			lines.add("");
			
			lines.add("### Aftershock Locations");
			lines.add(topLink); lines.add("");
			line = "Map view of the aftershock sequence, plotted as cyan circles. The mainshock ";
			if (plotter.getForeshocks() != null && !plotter.getForeshocks().isEmpty())
				line += " and foreshocks are plotted below in brown and magenta circles respectively";
			else
				line += " is plotted below as a brown circle";
			line += ", but may be obscured by aftershocks. Nearby UCERF3 fault traces are plotted in gray lines, and "
					+ "the region used to fetch aftershock data in a dashed dark gray line.";
			lines.add(line);
			lines.add("");
			if (deltaDays > 1) {
				table = MarkdownUtils.tableBuilder();
				table.initNewLine();
				table.addColumn("First Day");
				if (deltaDays > 7d)
					table.addColumn("First Week");
				table.addColumn("To Date");
				table.finalizeLine();
				table.initNewLine();
				plotMap(resourcesDir, "map_first_day", " ", originTime+ComcatDataPlotter.MILLISEC_PER_DAY);
				table.addColumn("![First Day](resources/map_first_day.png)");
				if (deltaDays > 7d) {
					plotMap(resourcesDir, "map_first_week", " ", originTime+7l*ComcatDataPlotter.MILLISEC_PER_DAY);
					table.addColumn("![First Day](resources/map_first_week.png)");
				}
				plotMap(resourcesDir, "map_to_date", " ", plotter.getEndTime());
				table.addColumn("![First Day](resources/map_to_date.png)");
				table.finalizeLine();
				lines.addAll(table.build());
			} else {
				plotMap(resourcesDir, "map_to_date", " ", plotter.getEndTime());
				lines.add("![First Day](resources/map_to_date.png)");
			}
			lines.add("");
			
			lines.add("### Cumulative Number Plot");
			lines.add(topLink); lines.add("");
			lines.add("This plot shows the cumulative number of M&ge;"+optionalDigitDF.format(minFetchMag)
				+" aftershocks as a function of time since the mainshock.");
			lines.add("");
			plotter.plotTimeFuncPlot(resourcesDir, "aftershocks_vs_time", minFetchMag);
			lines.add("![Time Func](resources/aftershocks_vs_time.png)");
			lines.add("");
			
			lines.add("### Magnitude-Number Distributions (MNDs)");
			lines.add(topLink); lines.add("");
			lines.add("These plot shows the magnitude-number distribution of the aftershock sequence thus far. "
					+ "The left plot gives an incremental distribution (the count in each magnitude bin), and the "
					+ "right plot a cumulative distribution (the count in or above each magnitude bin).");
			lines.add("");
			plotter.plotMagNumPlot(resourcesDir, "aftershocks_mag_num_incremental", false, minFetchMag,
					minFetchMag, false, false, null);
			plotter.plotMagNumPlot(resourcesDir, "aftershocks_mag_num_cumulative", true, minFetchMag,
					minFetchMag, false, false, null);
			table = MarkdownUtils.tableBuilder();
			table.addLine("Incremental MND", "Cumulative MND");
			table.addLine("![Incremental](resources/aftershocks_mag_num_incremental.png)",
					"![Cumulative](resources/aftershocks_mag_num_cumulative.png)");
			lines.addAll(table.build());
			lines.add("");
		}
		
		double sigMag = Math.min(6d, mainshock.getMag()-1d);
		if (foreshocks != null) {
			List<ObsEqkRupture> sigForeshocks = new ArrayList<>();
			for (ObsEqkRupture foreshock : foreshocks) {
				double mag = foreshock.getMag();
				if (mag >= sigMag)
					sigForeshocks.add(foreshock);
			}
			if (!sigForeshocks.isEmpty()) {
				lines.add("## Significant Foreshocks");
				lines.add(topLink); lines.add("");
				lines.add("Foreshock(s) with M&ge;6 or with M&ge;M<sub>Mainshock</sub>-1.");
				lines.add("");
				for (ObsEqkRupture rup : sigForeshocks) {
					double delta = (originTime-rup.getOriginTime())/(double)ComcatDataPlotter.MILLISEC_PER_DAY;
					double mag = rup.getMag();
					lines.add("### M"+optionalDigitDF.format(mag)+" "+optionalDigitDF.format(delta)+" days before");
					lines.add(topLink); lines.add("");
					lines.addAll(generateDetailLines(rup, resourcesDir, "###", topLink));
					lines.add("");
				}
			}
		}
		List<ObsEqkRupture> sigAftershocks = new ArrayList<>();
		for (ObsEqkRupture rup : aftershocks) {
			double mag = rup.getMag();
			if (mag >= sigMag)
				sigAftershocks.add(rup);
		}
		if (!sigAftershocks.isEmpty()) {
			lines.add("## Significant Aftershocks");
			lines.add(topLink); lines.add("");
			lines.add("Aftershocks(s) with M&ge;6 or with M&ge;M<sub>Mainshock</sub>-1.");
			lines.add("");
			for (ObsEqkRupture rup : sigAftershocks) {
				double delta = (rup.getOriginTime()-originTime)/(double)ComcatDataPlotter.MILLISEC_PER_DAY;
				double mag = rup.getMag();
				lines.add("### M"+optionalDigitDF.format(mag)+" "+optionalDigitDF.format(delta)+" days after");
				lines.add(topLink); lines.add("");
				lines.addAll(generateDetailLines(rup, resourcesDir, "###", topLink));
			}
			lines.add("");
		}
		
		if (etasRun != null)
			lines.addAll(generateETASLines(etasRun, true, resourcesDir, "#", topLink));
		
		List<String> tocLines = new ArrayList<>();
		tocLines.add("## Table Of Contents");
		tocLines.add("");
		tocLines.addAll(MarkdownUtils.buildTOC(lines, 2, 3));
		
		lines.addAll(tocIndex, tocLines);
		
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	private List<String> generateDetailLines(ObsEqkRupture event, File resourcesDir, String curHeading, String topLink)
			throws IOException {
		String eventID = event.getEventId();
		EventQuery query = new EventQuery();
		query.setEventId(eventID);
		List<JsonEvent> events;
		try {
			events = service.getEvents(query);
			Preconditions.checkState(!events.isEmpty(), "Event not found");
		} catch (Exception e) {
			System.err.println("Could not retrieve event '"+ eventID +"' from Comcat");
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		Preconditions.checkState(events.size() == 1, "More that 1 match? "+events.size());
		
//		JsonEvent event = events.get(0);
		JSONObject obj = events.get(0);
//		JSONParser jsonParser = new JSONParser();s
		
//		printJSON(obj);
		
		
		JSONObject prop = (JSONObject) obj.get("properties");
		printJSON(prop);
		JSONObject prods = (JSONObject) prop.get("products");
		
		List<String> lines = new ArrayList<>();
		String url = prop.get("url").toString();
		System.out.println("URL: "+url);
		lines.add("Information and plots in the section are taken from the [USGS event page]("+url
				+"), accessed through ComCat.");
		lines.add("");
		TableBuilder table = MarkdownUtils.tableBuilder();
		
		SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z");
		SimpleDateFormat zoneDF = new SimpleDateFormat("z");
		Date date = new Date(originTime);
		
		TimeZone local = df.getTimeZone();
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		zoneDF.setTimeZone(df.getTimeZone());
		table.addLine("Field", "Value");
		table.addLine("Magnitude", optionalDigitDF.format(event.getMag())+" ("+prop.get("magType")+")");
		table.addLine("Time ("+zoneDF.format(date)+")", df.format(date));
		df.setTimeZone(local);
		zoneDF.setTimeZone(local);
		table.addLine("Time ("+zoneDF.format(date)+")", df.format(date));
		Location hypo = event.getHypocenterLocation();
		table.addLine("Location", (float)hypo.getLatitude()+", "+(float)hypo.getLongitude());
		table.addLine("Depth", (float)hypo.getDepth()+" km");
		table.addLine("Status", prop.get("status"));
		lines.addAll(table.build());
		
		String shakemapImageURL = null;

		JSONArray shakemaps = (JSONArray) prods.get("shakemap");
		if (shakemaps != null && shakemaps.size() > 0) {
			JSONObject shakemap = (JSONObject)shakemaps.get(0);
			
			shakemapImageURL = fetchShakeMapImage(shakemap);
			System.out.println("Shakemap image: "+shakemapImageURL);
		}
		
		String dyfiImageURL = null;

		JSONArray dyfis = (JSONArray) prods.get("dyfi");
		if (dyfis != null && dyfis.size() > 0) {
			JSONObject dyfi = (JSONObject)dyfis.get(0);
			
//			System.out.println("=============");
//			printJSON(dyfi);
			dyfiImageURL = fetchDYFIImage(dyfi);
			System.out.println("DYFI image: "+dyfiImageURL);
		}
		
		String[] pagerImageURLs = null;
		
		JSONArray pagers = (JSONArray) prods.get("losspager");
		if (pagers != null && pagers.size() > 0) {
			JSONObject pager = (JSONObject)pagers.get(0);
			
//			System.out.println("=============");
//			printJSON(pager);
			pagerImageURLs = fetchPagerImage(pager);
			System.out.println("Pager images: "+pagerImageURLs[0]+" "+pagerImageURLs[1]);
		}
		
		// TODO focal mechanism
		String mechImageURL = null;

		JSONArray mechs = (JSONArray) prods.get("moment-tensor");
		if (mechs != null && mechs.size() > 0) {
			JSONObject mech = (JSONObject)mechs.get(0);
			
			mechImageURL = fetchMechImage(mech);
			System.out.println("Mech image: "+mechImageURL);
		}
		
		if (shakemapImageURL != null || dyfiImageURL != null || pagerImageURLs != null || mechImageURL != null) {
			lines.add("");
			lines.add(curHeading+"# USGS Products");
			lines.add(topLink); lines.add("");
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			if (shakemapImageURL != null)
				table.addColumn("<center>**[ShakeMap]("+url+"/shakemap/)**</center>");
			if (dyfiImageURL != null)
				table.addColumn("<center>**[Did You Feel It?]("+url+"/dyfi/)**</center>");
			if (pagerImageURLs != null)
				table.addColumn("<center>**[PAGER]("+url+"/pager/)**</center>");
			if (mechImageURL != null)
				table.addColumn("<center>**[Moment Tensor]("+url+"/moment-tensor/)**</center>");
			table.finalizeLine();
			table.initNewLine();
			if (shakemapImageURL != null) {
				File smFile = new File(resourcesDir, eventID+"_shakemap.jpg");
				FileUtils.downloadURL(shakemapImageURL, smFile);
				table.addColumn("![ShakeMap](resources/"+smFile.getName()+")");
			}
			if (dyfiImageURL != null) {
				File dyfiFile = new File(resourcesDir, eventID+"_dyfi.jpg");
				FileUtils.downloadURL(dyfiImageURL, dyfiFile);
				table.addColumn("![DYFI](resources/"+dyfiFile.getName()+")");
			}
			if (pagerImageURLs != null) {
				File fatalFile = new File(resourcesDir, eventID+"_pager_fatalities.png");
				File econFile = new File(resourcesDir, eventID+"_pager_economic.png");
				FileUtils.downloadURL(pagerImageURLs[0], fatalFile);
				FileUtils.downloadURL(pagerImageURLs[1], econFile);
				File combined = new File(resourcesDir, eventID+"_pager.png");
				combinePager(fatalFile, econFile, combined);
				table.addColumn("![PEGER](resources/"+combined.getName()+")");
			}
			if (mechImageURL != null) {
				File mechFile = new File(resourcesDir, eventID+"_mechanism.jpg");
				FileUtils.downloadURL(mechImageURL, mechFile);
				table.addColumn("![Mechanism](resources/"+mechFile.getName()+")");
			}
			table.finalizeLine();
			lines.addAll(table.wrap(3, 0).build());
		}
		
		// now faults
		double distThreshold = 10d;
		Map<String, Double> faultDists = new HashMap<>();
//		List<String> names 
//		ComparablePairing.getso
		for (FaultSectionPrefData fault : getU3Faults()) {
			StirlingGriddedSurface surf = fault.getStirlingGriddedSurface(1d);
			double minDist = Double.POSITIVE_INFINITY;
			for (Location loc : surf.getEvenlyDiscritizedListOfLocsOnSurface()) {
				double dist = LocationUtils.linearDistanceFast(loc, hypo);
				minDist = Double.min(minDist, dist);
			}
			if (minDist < distThreshold)
				faultDists.put(fault.getName(), minDist);
		}
		
		lines.add("");
		lines.add(curHeading+"# Nearby Faults");
		lines.add(topLink); lines.add("");
		lines.add("");
		
		String line;
		if (faultDists.isEmpty())
			line = "No UCERF3 fault sections are";
		else if (faultDists.size() == 1)
			line = "1 UCERF3 fault section is";
		else
			line = faultDists.size()+" UCERF3 fault sections are";
		line += " within "+optionalDigitDF.format(distThreshold)+"km of this event's hypocenter";
		if (faultDists.isEmpty())
			line += ".";
		else
			line += ":";
		lines.add(line);
		lines.add("");
		if (!faultDists.isEmpty())
			for (String name : ComparablePairing.getSortedData(faultDists))
				lines.add("* "+name+": "+optionalDigitDF.format(faultDists.get(name))+"km");
		
		return lines;
	}
	
	private static String fetchShakeMapImage(JSONObject shakemap) {
		JSONObject contents = (JSONObject)shakemap.get("contents");
		if (contents == null)
			return null;
		JSONObject intensityOBJ = (JSONObject)contents.get("download/intensity.jpg");
		if (intensityOBJ == null)
			return null;
		return (String)intensityOBJ.get("url");
	}
	
	private static String fetchDYFIImage(JSONObject dyfi) {
		JSONObject contents = (JSONObject)dyfi.get("contents");
		if (contents == null)
			return null;
		for (Object key : contents.keySet()) {
			if (key.toString().trim().endsWith("_ciim.jpg")) {
				JSONObject intensityOBJ = (JSONObject)contents.get(key);
				if (intensityOBJ == null)
					return null;
				return (String)intensityOBJ.get("url");
			}
		}
		return null;
	}
	
	private static String[] fetchPagerImage(JSONObject dyfi) {
		JSONObject contents = (JSONObject)dyfi.get("contents");
		if (contents == null)
			return null;
		JSONObject fatalOBJ = (JSONObject)contents.get("alertfatal.png");
		JSONObject econOBJ = (JSONObject)contents.get("alertecon.png");
		if (fatalOBJ == null || econOBJ == null)
			return null;
		return new String[] { (String)fatalOBJ.get("url"), (String)econOBJ.get("url") };
	}
	
	private static void combinePager(File top, File bottom, File output) throws IOException {
		BufferedImage topIMG = ImageIO.read(top);
		BufferedImage botIMG = ImageIO.read(bottom);
		
		int width = Integer.max(topIMG.getWidth(), botIMG.getWidth());
		int height = topIMG.getHeight()+botIMG.getHeight();
		
		BufferedImage comb = new BufferedImage(width, height, topIMG.getType());
		for (int y=0; y<topIMG.getHeight(); y++)
			for (int x=0; x<topIMG.getWidth(); x++)
				comb.setRGB(x, y, topIMG.getRGB(x, y));
		for (int y=0; y<topIMG.getHeight(); y++)
			for (int x=0; x<topIMG.getWidth(); x++)
				comb.setRGB(x, y+topIMG.getHeight(), botIMG.getRGB(x, y));
		
		ImageIO.write(comb, "png", output);
	}
	
	private static String fetchMechImage(JSONObject mech) {
		JSONObject contents = (JSONObject)mech.get("contents");
		if (contents == null)
			return null;
		for (Object key : contents.keySet()) {
			if (key.toString().trim().endsWith("_mechanism.jpg")) {
				JSONObject mechOBJ = (JSONObject)contents.get(key);
				if (mechOBJ == null)
					return null;
				return (String)mechOBJ.get("url");
			}
		}
		return null;
	}
	
	private static void printJSON(JSONObject json) {
		printJSON(json, "");
	}
	
	private static void printJSON(JSONObject json, String prefix) {
		for (Object key : json.keySet()) {
			Object val = json.get(key);
			if (val != null && val.toString().startsWith("[{")) {
				String str = val.toString();
				try {
					val = new JSONParser().parse(str.substring(1, str.length()-1));
				} catch (ParseException e) {
//					e.printStackTrace();
				}
			}
			if (val != null && val instanceof JSONObject) {
				System.out.println(prefix+key+":");
				String prefix2 = prefix;
				if (prefix2 == null)
					prefix2 = "";
				prefix2 += "\t";
				printJSON((JSONObject)val, prefix2);
			} else {
				System.out.println(prefix+key+": "+val);
			}
		}
	}
	
	private synchronized Collection<FaultSectionPrefData> getU3Faults() {
		if (faults == null) {
			Map<Integer, FaultSectionPrefData> map = FaultModels.FM3_1.fetchFaultSectionsMap();
			map = new HashMap<>(map);
			map.putAll(FaultModels.FM3_2.fetchFaultSectionsMap());
			faults = map.values();
		}
		return faults;
	}
	
	private void plotMap(File resourcesDir, String prefix, String title, long endTime) throws IOException {
		List<ObsEqkRupture> events = new ArrayList<>();
		for (ObsEqkRupture rup : aftershocks)
			if (rup.getOriginTime() <= endTime)
				events.add(rup);
		
		double padding = 30;
		double fRad = 1.5;
		if (radius > 0) {
			padding = Math.max(padding, fRad*radius);
		} else {
			LocationList border = region.getBorder();
			for (int i=0; i<border.size(); i++) {
				Location l1 = border.get(i);
				for (int j=i+1; j<border.size(); j++) {
					Location l2 = border.get(j);
					padding = Math.max(padding, fRad*LocationUtils.horzDistanceFast(l1, l2));
				}
			}
		}
		
		Location ll = new Location(region.getMinLat(), region.getMinLon());
		ll = LocationUtils.location(ll, new LocationVector(225d, padding, 0d));
		Location ul = new Location(region.getMaxLat(), region.getMaxLon());
		ul = LocationUtils.location(ul, new LocationVector(45d, padding, 0d));
		
		Region mapRegion = new Region(ul, ll);
		
		List<XY_DataSet> inputFuncs = new ArrayList<>();
		List<PlotCurveCharacterstics> inputChars = new ArrayList<>();
		
		// add ucerf3 faults
		PlotCurveCharacterstics faultChar = new PlotCurveCharacterstics(PlotLineType.SOLID, 2f, Color.GRAY);
		boolean firstFault = true;
		for (FaultSectionPrefData fault : getU3Faults()) {
			DefaultXY_DataSet xy = new DefaultXY_DataSet();
			for (Location loc : fault.getFaultTrace())
				xy.set(loc.getLongitude(), loc.getLatitude());
			if (firstFault) {
				xy.setName("Faults");
				firstFault = false;
			}
			inputFuncs.add(xy);
			inputChars.add(faultChar);
		}
		
		// add map region
		DefaultXY_DataSet regXY = new DefaultXY_DataSet();
		LocationList border = region.getBorder();
		for (int i=0; i<=border.size(); i++) {
			Location loc = border.get(i % border.size());
			regXY.set(loc.getLongitude(), loc.getLatitude());
		}
		regXY.setName("Data Region");
		inputFuncs.add(regXY);
		inputChars.add(new PlotCurveCharacterstics(PlotLineType.DASHED, 3f, Color.DARK_GRAY));
		
		plotter.plotMap(resourcesDir, prefix, title, mapRegion, events, minFetchMag, inputFuncs, inputChars);
	}
	
	private List<String> generateETASLines(ETAS_Config config, boolean snapToNow, File resourcesDir,
			String curHeading, String topLink) throws IOException {
		List<String> lines = new ArrayList<>();
		
		String title = "UCERF3-ETAS Forecast";
//		String shortTitle = "ETAS "
		String description = "This section gives results from the UCERF3-ETAS short-term forecasting model. "
				+ "This model is described in [Field et al. (2017)]"
				+ "(http://bssa.geoscienceworld.org/lookup/doi/10.1785/0120160173), and computes probabilities "
				+ "of this sequence triggering subsequent aftershocks, including events on known faults.";
		long deltaMillis = config.getSimulationStartTimeMillis()-originTime;
		Preconditions.checkState(deltaMillis >= 0l, "ETAS forecast starts before mainshock");
		double deltaDays = (double)deltaMillis/(double)ComcatDataPlotter.MILLISEC_PER_DAY;
		double deltaYears = (double)deltaMillis/ComcatDataPlotter.MILLISEC_PER_YEAR;
		String deltaStr = ETAS_AbstractPlot.getTimeLabel(deltaYears, true).toLowerCase();
		
		SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss z");
//		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		if (snapToNow) {
			config.setStartTimeMillis(plotter.getEndTime());
			
			String date = df.format(new Date(plotter.getEndTime()));
			
			description += "\n\nProbabilities are inherantly time-dependent. Those stated here are for time "
					+ "periods beginning the instant when this report was generated, "+date+".";
		} else {
			String date = df.format(new Date(config.getSimulationStartTimeMillis()));
			description += "\n\nProbabilities are inherantly time-dependent. Those stated here are for time "
					+ "periods beginning when the model was run, "+date+".";
		}
		if (deltaDays > 1/24d) {
			description += " The model was updated with all observed aftershcoks up to "+deltaStr+" after the mainshock, "
					+ "and may be out of date, especially if large aftershocks have occurred subsequently or "
					+ "a significant amount of time has passed since the last update.";
		} else {
			description += " The model has not been updated with any observed aftershocks and may be out of date, "
					+ "especially if large aftershock have occurred subsequently or a significant amount of time has "
					+ "passed since the mainshock.";
		}
		lines.add(curHeading+"# "+title);
		lines.add(topLink); lines.add("");
		lines.add(description);
		lines.add("");
		lines.add("Results are summarized below and should be considered preliminary. The exact timing, size, location, "
				+ "or number of aftershocks cannot be predicted, and all probabilities are uncertain.");
		lines.add("");
		
		// force it to use the same region that we use
		ComcatMetadata meta = config.getComcatMetadata();
		ComcatMetadata oMeta = new ComcatMetadata(region, meta.eventID, meta.minDepth, meta.maxDepth, meta.minMag,
				meta.startTime, meta.endTime);
		oMeta.magComplete = oMeta.magComplete;
		config.setComcatMetadata(oMeta);
		
		File inputFile = null;
		for (BinaryFilteredOutputConfig filter : config.getBinaryOutputFilters()) {
			File file = new File(config.getOutputDir(), filter.getPrefix()+".bin");
			if (file.exists()) {
				inputFile = file;
				System.out.println("Simulation input file: "+inputFile);
				break;
			}
		}
		Preconditions.checkNotNull(inputFile, "input not found");
		
		ETAS_Launcher launcher = new ETAS_Launcher(config, false);
		
		FaultSystemSolution fss = launcher.checkOutFSS();
		
		List<ETAS_AbstractPlot> plots = new ArrayList<>();
		
		ETAS_ComcatComparePlot comcatPlot = new ETAS_ComcatComparePlot(config, launcher);
		plots.add(comcatPlot);
		
//		String hazChangePrefix = "etas_hazard_change";
//		ETAS_HazardChangePlot hazChangePlot = new ETAS_HazardChangePlot(
//				config, launcher, hazChangePrefix, radius);
//		plots.add(hazChangePlot);
		
		String mfdPrefix = "etas_mfd";
		ETAS_MFD_Plot mfdPlot = new ETAS_MFD_Plot(config, launcher, mfdPrefix, false, true);
		plots.add(mfdPlot);
		
//		String faultPrefix = "etas_fault_prefix";
//		ETAS_FaultParticipationPlot faultPlot = new ETAS_FaultParticipationPlot(
//				config, launcher, faultPrefix, false, true);
//		plots.add(faultPlot);
		
		boolean filterSpontaneous = false;
		for (ETAS_AbstractPlot plot : plots)
			filterSpontaneous = filterSpontaneous || plot.isFilterSpontaneous();
		
		final boolean isFilterSpontaneous = filterSpontaneous;
		
		System.out.println("Processing "+config.getSimulationName());
		int numProcessed = ETAS_CatalogIteration.processCatalogs(inputFile, new ETAS_CatalogIteration.Callback() {
			
			@Override
			public void processCatalog(ETAS_Catalog catalog, int index) {
				if (snapToNow) {
					// delete all ruptures before now
					long minOT = config.getSimulationStartTimeMillis();
					ETAS_Catalog modCatalog = new ETAS_Catalog(catalog.getSimulationMetadata());
					for (int i=0; i<catalog.size(); i++) {
						ETAS_EqkRupture rup = catalog.get(i);
						if (catalog.get(i).getOriginTime() >= minOT)
							modCatalog.add(rup);
					}
					catalog = modCatalog;
				}
				ETAS_Catalog triggeredOnlyCatalog = null;
				if (isFilterSpontaneous)
					triggeredOnlyCatalog = ETAS_Launcher.getFilteredNoSpontaneous(config, catalog);
				for (ETAS_AbstractPlot plot : plots) {
					plot.processCatalog(catalog, triggeredOnlyCatalog, fss);
				}
			}
		}, -1, 0d);
		System.out.println("Processed "+numProcessed+" catalogs");
		
		ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		List<Future<?>> futures = new ArrayList<>();
		
		System.out.println("Finalizing plots...");
		for (ETAS_AbstractPlot plot : plots) {
			List<? extends Runnable> runnables = plot.finalize(resourcesDir, fss, exec);
			if (runnables != null)
				for (Runnable r : runnables)
					futures.add(exec.submit(r));
		}

		System.out.println("Waiting on "+futures.size()+" futures...");
		for (Future<?> f : futures) {
			try {
				f.get();
			} catch (InterruptedException | ExecutionException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		System.out.println("DONE finalizing");
		exec.shutdown();
		
		launcher.checkInFSS(fss);
		
		TableBuilder table = MarkdownUtils.tableBuilder();
		double[] mfdDurations = mfdPlot.getDurations();
		MFD_Stats[] mfdStats = mfdPlot.getFullStats();
		
		HashSet<Double> mfdIncludeDurations = new HashSet<>();
		mfdIncludeDurations.add(7d/365.25);
		mfdIncludeDurations.add(30d/365.25);
		
		table.initNewLine();
		table.addColumn("");
		for (double duration : mfdDurations)
			if (mfdIncludeDurations.contains(duration))
				table.addColumn(ETAS_MFD_Plot.getTimeLabel(duration, false));
		table.finalizeLine();
		
		EvenlyDiscretizedFunc mfdMagFunc = mfdStats[0].getProbFunc(0);
		for (int m=0; m<mfdMagFunc.size(); m++) {
			double mag = mfdMagFunc.getX(m);
			if (mag == Math.floor(mag)) {
				// include it
				table.initNewLine();
				table.addColumn("**M&ge;"+optionalDigitDF.format(mag)+"**");
				double maxProb = 0d;
				for (int d=0; d<mfdDurations.length; d++) {
					double duration = mfdDurations[d];
					if (mfdIncludeDurations.contains(duration)) {
						double prob = mfdStats[d].getProbFunc(0).getY(m);
						maxProb = Math.max(prob, maxProb);
						table.addColumn(percentProbDF.format(prob));
					}
				}
				table.finalizeLine();
				if (maxProb < 1e-4)
					break;
			}
		}
		lines.add("");
		lines.add("This table gives forecasted one week and one month probabilities.");
		lines.add("");
		lines.addAll(table.build());
		
		lines.add("");
		lines.add(curHeading+"## ETAS Forecasted Magnitude Vs. Time");
		lines.add(topLink); lines.add("");
		String line = "These plots show the show the magnitude versus time probability function since simulation start. "
				+ "Observed event data lie on top, with those input to the simulation plotted as magenta circles and those "
				+ "that occurred after the simulation start time as cyan circles. Time is relative to ";
		if (plotter.getMainshock() == null) {
			line += "the simulation start time.";
		} else {
			ObsEqkRupture mainshock = plotter.getMainshock();
			Double mag = mainshock.getMag();
			line += "the mainshock (M"+optionalDigitDF.format(mag)+", "+mainshock.getEventId()+", plotted as a brown circle).";
		}
		line += " Probabilities are only shown above the minimum simulated magnitude, M=2.5."; // TODO dynamic?
		lines.add(line);
		table = MarkdownUtils.tableBuilder();
		
		Map<String[], Double> magTimeDurations = new HashMap<>();
		if (!snapToNow)
			magTimeDurations.put(new String[] {"To Date", "mag_time_full.png"}, comcatPlot.getCurDuration());
		magTimeDurations.put(new String[] {"One Week", "mag_time_week.png"}, 7d/365.25);
		magTimeDurations.put(new String[] {"One Month", "mag_time_month.png"}, 30d/365.25);
		List<String[]> sortedDurations = ComparablePairing.getSortedData(magTimeDurations);
		
		table.initNewLine();
		for (String[] label : sortedDurations)
				table.addColumn(label[0]);
		table.finalizeLine();
		table.initNewLine();
		for (String[] fName : sortedDurations)
			table.addColumn("![Mag-time plot](resources/"+fName[1]+")");
		table.finalizeLine();
		lines.add("");
		lines.addAll(table.build());
		
		lines.add("");
		lines.add(curHeading+"## ETAS Spatial Distribution Forecast");
		lines.add(topLink); lines.add("");
		lines.add("These plots show the predicted spatial distribution of aftershocks above the given "
				+ "magnitude threshold and for the given time period. The 'Current' plot shows the forecasted "
				+ "spatial distribution to date, along with as any observed aftershocks overlaid with "
				+ "cyan circles. Observed aftershocks will be included in the week/month plots as well if "
				+ "the forecasted time window has elapsed.");
		lines.add("");
		
		table = MarkdownUtils.tableBuilder();
		
		HashSet<Double> includeDurations = new HashSet<>();
		includeDurations.add(7d/365.25);
		includeDurations.add(30d/365.25);
		if (!snapToNow)
			includeDurations.add(comcatPlot.getCurDuration());
		
		table.initNewLine();
		table.addColumn("");
		for (double duration : comcatPlot.getDurations())
			if (includeDurations.contains(duration))
				table.addColumn(comcatPlot.getMapTableLabel(duration));
		table.finalizeLine();

		double[] durations = comcatPlot.getDurations();
		double[] minMags = comcatPlot.getMinMags();
		
		HashSet<Double> includeMags = new HashSet<>();
		double minAboveZero = Double.POSITIVE_INFINITY;;
		for (double mag : minMags)
			if (mag > 0)
				minAboveZero = Math.min(minAboveZero, mag);
		includeMags.add(minAboveZero);
		includeMags.add(5d);
		String[][] mapPrefixes = comcatPlot.getMapProbPrefixes();
		for (int m=0; m<minMags.length; m++) {
			double mag = minMags[m];
			if (includeMags.contains(mag)) {
				table.initNewLine();
				table.addColumn("**M&ge;"+optionalDigitDF.format(mag)+"**");
				for (int d=0; d<durations.length; d++)
					if (includeDurations.contains(durations[d]))
						table.addColumn("![Map](resources/"+mapPrefixes[d][m]+".png)");
				table.finalizeLine();
			}
		}
		lines.addAll(table.build());
		
		return lines;
	}
	

	private static DecimalFormat percentProbDF = new DecimalFormat("0.00%");
	
	private static final DecimalFormat optionalDigitDF = new DecimalFormat("0.##");

	public static void main(String[] args) throws IOException {
		File mainDir = new File("/home/kevin/git/event-reports");
		
//		String eventID = "ci39126079";
//		double radius = 0d;
//		double minFetchMag = 0d;
//		double daysBefore = 3d;
//		File etasDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
//				+ "2020_04_13-ComCatM4p87_ci39126079_9p9DaysAfter_PointSources_kCOV1p5");
		
		String eventID = "ci39400304";
		double radius = 0d;
		double minFetchMag = 0d;
		double daysBefore = 3d;
		File etasDir = null;
		
//		String eventID = "ci38457511";
//		double radius = 0d;
//		double minFetchMag = 2d;
//		double daysBefore = 3d;
		
//		String eventID = "ci38443183";
//		double radius = 0d;
//		double minFetchMag = 2d;
//		double daysBefore = 3d;
		
		ComcatReportPageGen pageGen;
		if (radius > 0)
			pageGen = new ComcatReportPageGen(eventID, radius, minFetchMag, daysBefore);
		else
			pageGen = new ComcatReportPageGen(eventID, minFetchMag, daysBefore);
		
		File outputDir = new File(mainDir, pageGen.generateDirName());
		System.out.println("Output dir: "+outputDir.getAbsolutePath());
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		if (etasDir != null) {
			ETAS_Config config = ETAS_Config.readJSON(new File(etasDir, "config.json"));
			pageGen.addETAS(config);
		}
		
		pageGen.generateReport(outputDir, null);
	}

}
