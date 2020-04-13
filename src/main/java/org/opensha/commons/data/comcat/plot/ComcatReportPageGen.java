package org.opensha.commons.data.comcat.plot;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.comcat.ComcatAccessor;
import org.opensha.commons.data.comcat.ComcatRegion;
import org.opensha.commons.data.comcat.ComcatRegionAdapter;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.param.Parameter;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import gov.usgs.earthquake.event.EventQuery;
import gov.usgs.earthquake.event.EventWebService;
import gov.usgs.earthquake.event.JsonEvent;

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
		lines.addAll(generateDetailLines(mainshock.getEventId(), resourcesDir));
		
		lines.add("## Sequence Details");
		lines.add(topLink); lines.add("");
		SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss z");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
//		"Last updated at "+df.format(new Date(curTime))
//		+", "+getTimeLabel(curDuration, true).toLowerCase()+" after the simulation start time."
//		lines.add()
		String line = "These plots show the aftershock sequence. They were last updated at "
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
			
			lines.add("### Magnitude vs Time Plot");
			lines.add(topLink); lines.add("");
			line = "This plot shows the magnitude vs time evolution of the sequence. The mainshock is ploted "
					+ "as brown a brown circle";
			if (plotter.getForeshocks() != null && !plotter.getForeshocks().isEmpty())
				line += ", foreshocks are plotted as green circles";
			line += ", and aftershocks are plotted as cyan circles.";
			lines.add(line);
			lines.add("");
			plotter.plotMagTimeFunc(resourcesDir, "aftershocks_mag_vs_time");
			lines.add("![Time Func](resources/aftershocks_mag_vs_time.png)");
			lines.add("");
			
			lines.add("### Cumulative Number Plot");
			lines.add(topLink); lines.add("");
			lines.add("This plot shows the cumulative number of M&ge;"+optionalDigitDF.format(minFetchMag)
				+" aftershocks as a function of time since the mainshock.");
			lines.add("");
			plotter.plotTimeFuncPlot(resourcesDir, "aftershocks_vs_time", minFetchMag);
			lines.add("![Time Func](resources/aftershocks_vs_time.png)");
			lines.add("");
			
			lines.add("### Magnitude-Number Distributions");
			lines.add(topLink); lines.add("");
			lines.add("These plot shows the magnitude-number distrubtion of the aftershock sequence thus far. "
					+ "The left plot gives an incremental distribution (the count in each magnitude bin), and the "
					+ "right plot a cumulative distribution (the count in or above each magnitude bin). The y-axis "
					+ "is logarithmic.");
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
		
		List<String> extras = getExtraLines(resourcesDir, topLink);
		if (extras != null && !extras.isEmpty()) {
			lines.add("");
			lines.addAll(extras);
		}
		
		List<String> tocLines = new ArrayList<>();
		tocLines.add("## Table Of Contents");
		tocLines.add("");
		tocLines.addAll(MarkdownUtils.buildTOC(lines, 2));
		
		lines.addAll(tocIndex, tocLines);
		
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
	}
	
	protected List<String> getExtraLines(File resourcesDir, String topLink) {
		return null;
	}
	
	private List<String> generateDetailLines(String eventID, File resourcesDir) throws IOException {
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
		Date date = new Date(originTime);
		
		TimeZone local = df.getTimeZone();
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		table.addLine("Field", "Value");
		table.addLine("Magnitude", optionalDigitDF.format(mainshock.getMag())+" ("+prop.get("magType")+")");
		table.addLine("Time", df.format(date));
		df.setTimeZone(local);
		table.addLine("Time (Local)", df.format(date));
		Location hypo = mainshock.getHypocenterLocation();
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
			System.out.println("Pager image: "+pagerImageURLs);
		}
		
		if (shakemapImageURL != null || dyfiImageURL != null || pagerImageURLs != null) {
			table = MarkdownUtils.tableBuilder();
			table.initNewLine();
			if (shakemapImageURL != null)
				table.addColumn("[ShakeMap]("+url+"/shakemap/)");
			if (dyfiImageURL != null)
				table.addColumn("[Did You Feel It?]("+url+"/dyfi/)");
			if (pagerImageURLs != null)
				table.addColumn("[PAGER]("+url+"/pager/)");
			table.finalizeLine();
			table.initNewLine();
			if (shakemapImageURL != null) {
				File smFile = new File(resourcesDir, "shakemap.jpg");
				FileUtils.downloadURL(shakemapImageURL, smFile);
				table.addColumn("![ShakeMap](resources/"+smFile.getName()+")");
			}
			if (dyfiImageURL != null) {
				File dyfiFile = new File(resourcesDir, "dyfi.jpg");
				FileUtils.downloadURL(dyfiImageURL, dyfiFile);
				table.addColumn("![DYFI](resources/"+dyfiFile.getName()+")");
			}
			if (pagerImageURLs != null) {
				File fatalFile = new File(resourcesDir, "pager_fatalities.png");
				File econFile = new File(resourcesDir, "pager_economic.png");
				FileUtils.downloadURL(pagerImageURLs[0], fatalFile);
				FileUtils.downloadURL(pagerImageURLs[1], econFile);
				File combined = new File(resourcesDir, "pager.png");
				combinePager(fatalFile, econFile, combined);
				table.addColumn("![PEGER](resources/"+combined.getName()+")");
			}
			table.finalizeLine();
			lines.add("");
			lines.addAll(table.build());
		}
		
		// TODO focal mechanism
//		String mechImageURL = null;
//
//		JSONArray mechs = (JSONArray) prods.get("focal-mechanism");
//		if (mechs != null && mechs.size() > 0) {
//			JSONObject mech = (JSONObject)mechs.get(0);
//			
//			System.out.println("=============");
//			printJSON(mech);
////			dyfiImageURL = fetchDYFIImage(dyfi);
////			System.out.println("DYFI image: "+dyfiImageURL);
//		}
		
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
	
	private static final DecimalFormat optionalDigitDF = new DecimalFormat("0.##");

	public static void main(String[] args) throws IOException {
		File mainDir = new File("/home/kevin/git/event-reports");
		
		String eventID = "ci39126079";
		double radius = 0d;
		double minFetchMag = 0d;
		double daysBefore = 3d;
		
		ComcatReportPageGen pageGen;
		if (radius > 0)
			pageGen = new ComcatReportPageGen(eventID, radius, minFetchMag, daysBefore);
		else
			pageGen = new ComcatReportPageGen(eventID, minFetchMag, daysBefore);
		
		File outputDir = new File(mainDir, pageGen.generateDirName());
		System.out.println("Output dir: "+outputDir.getAbsolutePath());
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
		
		pageGen.generateReport(outputDir, null);
	}

}
