package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ClassUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_ComcatComparePlot;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_EventMapPlotUtils;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.ComcatMetadata;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class ETAS_ComcatEventFetcher {
	
	public static void main(String[] args) throws IOException {
		if (args.length < 1 || args.length > 2) {
			System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(ETAS_ComcatEventFetcher.class)
				+" <config.json> [<output.txt> OR </path/to/output/dir>]");
			System.exit(2);
		}
		
		File configFile = new File(args[0]);
		Preconditions.checkState(configFile.exists());
		
		ETAS_Config config = ETAS_Config.readJSON(configFile);
		ComcatMetadata meta = config.getComcatMetadata();
		Preconditions.checkNotNull(meta, "ETAS configuration doesn't have ComCat metadata, can't fetch events");
		
		Region mapRegion = meta.region;
		if (mapRegion == null)
			mapRegion = ETAS_EventMapPlotUtils.getMapRegion(config, new ETAS_Launcher(config));
		
		long endTime = Long.max(System.currentTimeMillis(),
				config.getSimulationStartTimeMillis() + (long)(config.getDuration()*ProbabilityModelsCalc.MILLISEC_PER_YEAR));
		
		System.out.println("Loading events...");
		ObsEqkRupList events = ETAS_ComcatComparePlot.loadComcatEvents(config, meta, mapRegion, endTime);
		System.out.println("Loaded "+events.size()+" events");
		
		File outputDir = config.getOutputDir();
		File outputFile = null;
		if (args.length == 2) {
			File file = new File(args[2]);
			if (file.exists() && file.isDirectory())
				outputDir = file;
			else
				outputFile = file;
		}
		if (outputFile == null) {
			SimpleDateFormat outDF = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
			String name = "comcat-events-"+outDF.format(new Date())+"-"+events.size()+"events.txt";
			outputFile = new File(outputDir, name);
		}
		System.out.println("Writing catalog to: "+outputFile.getAbsolutePath());
		
		writeCatalogFile(outputFile, events);
	}
	
	public static void writeCatalogFile(File outputFile, Collection<? extends ObsEqkRupture> catalog) throws IOException {
		FileWriter fw = new FileWriter(outputFile);
		
		fw.write("# Year\tMonth\tDay\tHour\tMinute\tSec\tLat\tLon\tDepth\tMagnitude\t[event-ID]\n");
		for (ObsEqkRupture rup : catalog) {
			StringBuilder sb = new StringBuilder();
			synchronized (ETAS_CatalogIO.class) {
				// SimpleDateFormat is NOT synchronized and maintains an internal calendar
				sb.append(ETAS_CatalogIO.catDateFormat.format(rup.getOriginTimeCal().getTime())).append("\t");
			}
			Location hypoLoc = rup.getHypocenterLocation();
			sb.append((float)hypoLoc.getLatitude()).append("\t");
			sb.append((float)hypoLoc.getLongitude()).append("\t");
			sb.append((float)hypoLoc.getDepth()).append("\t");	
			sb.append((float)rup.getMag()).append("\t");
			sb.append(rup.getEventId());
			fw.write(sb.toString()+"\n");
		}
		fw.close();
	}
	
	public static ObsEqkRupList loadCatalogFile(File catalogFile) throws ParseException, IOException {
		// 0-Year 1-month 2-day 3-hour 4-minute 5-second 6-lat 7-long 8-depth 9-mag 10-id
		
		ObsEqkRupList list = new ObsEqkRupList();
		
		for (String line : Files.readLines(catalogFile, Charset.defaultCharset())) {
			line = line.trim();
			if (line.startsWith("%") || line.startsWith("#") || line.isEmpty())
				continue;
			String delim = line.contains("\t") ? "\t" : " ";
			String[] split = line.split(delim);
			Preconditions.checkState(split.length >= 10);
			
			String dateStr = split[0]+"\t"+split[1]+"\t"+split[2]+"\t"+split[3]+"\t"+split[4]+"\t"+split[5];
			Date date = ETAS_CatalogIO.catDateFormat.parse(dateStr);
			double latitude		= Double.parseDouble(split[6]);
			double longitude	= Double.parseDouble(split[7]);
			double depth		= Double.parseDouble(split[8]);
			double mag			= Double.parseDouble(split[9]);
			String eventID;
			if (split.length > 10)
				eventID = split[10];
			else
				eventID = (list.size()+1)+"";
			list.add(new ObsEqkRupture(eventID, date.getTime(), new Location(latitude, longitude, depth), mag));
		}
		
		return list;
	}

}
