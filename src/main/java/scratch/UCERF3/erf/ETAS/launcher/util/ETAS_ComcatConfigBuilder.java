package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;

import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class ETAS_ComcatConfigBuilder extends ETAS_AbstractComcatConfigBuilder {

	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			String argz = "";
			
//			argz += " --start-after-historical";
//			argz += " --end-now";
////			argz += " --end-date 2018-01-01";
//			argz += " --historical-catalog";
//			argz += " --include-spontaneous";
			
			// 10/14-15/2019 bay area M4's
//			argz += " --start-at nc73291880";
//			argz += " --end-after nc73292360";
////			argz += " --end-now";
//			argz += " --region 38.5,-122.75,36.25,-120.5";
			
			// 11/7-8/2019 Ventura M3's
			argz += " --start-at ci38229234";
			argz += " --end-now";
			argz += " --region 34.4,-119.5,34.15,-119.1";
			
			argz += " --num-simulations 100000";
//			argz += " --num-simulations 1000";
			
//			argz += " --gridded-only";
//			argz += " --impose-gr";
//			argz += " --prob-model NO_ERT";
//			argz += " --include-spontaneous";
//			argz += " --scale-factor 1.0";
//			argz += " --name-add CulledSurface";
//			argz += " --fault-model FM3_2";

			argz += " --finite-surf-shakemap";
			argz += " --finite-surf-shakemap-min-mag 6";
			
//			argz += " --finite-surf-inversion";
//			argz += " --finite-surf-inversion-min-mag 6";
			
//			argz += " --random-seed 123456789";
			
			// hpc options
			argz += " --hpc-site USC_HPC";
			argz += " --nodes 17";
			argz += " --hours 24";
//			argz += " --queue scec_hiprio";
			argz += " --queue scec";
			
			args = argz.trim().split(" ");
		}
		System.setProperty("java.awt.headless", "true");
		
		Options options = createOptions();
		
		try {
			ETAS_ComcatConfigBuilder builder = new ETAS_ComcatConfigBuilder(args, options);
			
			builder.buildConfiguration();
			
			builder.buildInputPlots();
			
			System.out.println("DONE");
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}

	public static Options createOptions() {
		Options ops = getCommonOptions();
		
		/*
		 * Time options
		 */
		Option startTimeOption = new Option("st", "start-time", true, "ComCat data start time in epoch milliseconds");
		startTimeOption.setRequired(false);
		ops.addOption(startTimeOption);
		
		Option startDateOption = new Option("sd", "start-date", true, "ComCat data start date in the format 'yyyy-MM-dd' "
				+ "(e.g. 2019-01-01) or 'yyyy-MM-ddTHH:mm:ss' (e.g. 2019-01-01T01:23:45). All dates and times in UTC");
		startDateOption.setRequired(false);
		ops.addOption(startDateOption);
		
		Option startAtOption = new Option("sa", "start-at", true, "ComCat data starting with the given event ID");
		startAtOption.setRequired(false);
		ops.addOption(startAtOption);
		
		Option startDaysBeforeOption = new Option("sdb", "start-days-before", true, "Defines the ComCat data start time "
				+ "as this many days before the end time.");
		startDaysBeforeOption.setRequired(false);
		ops.addOption(startDaysBeforeOption);
		
		Option startAfterHistoricalOption = new Option("sah", "start-after-historical", false,
				"ComCat data starting after the end of the UCERF3 historical catalog");
		startAfterHistoricalOption.setRequired(false);
		ops.addOption(startAfterHistoricalOption);
		
		Option endTimeOption = new Option("et", "end-time", true, "ComCat data end time in epoch milliseconds");
		endTimeOption.setRequired(false);
		ops.addOption(endTimeOption);
		
		Option endDateOption = new Option("ed", "end-date", true, "ComCat data end date in the format 'yyyy-MM-dd' "
				+ "(e.g. 2019-01-01) or 'yyyy-MM-ddTHH:mm:ss' (e.g. 2019-01-01T01:23:45). All dates and times in UTC.");
		endDateOption.setRequired(false);
		ops.addOption(endDateOption);
		
		Option endAfterOption = new Option("ea", "end-after", true, "ComCat data ending just after the given event ID");
		endAfterOption.setRequired(false);
		ops.addOption(endAfterOption);
		
		Option endDaysAfterOption = new Option("eda", "end-days-after", true, "Defines the ComCat data end time "
				+ "as this many days after the end time.");
		endDaysAfterOption.setRequired(false);
		ops.addOption(endDaysAfterOption);
		
		Option nowOption = new Option("now", "end-now", false, "Include all aftershocks of primary event (which is "
				+ "specified with --event-id <id>) up to the current instant");
		nowOption.setRequired(false);
		ops.addOption(nowOption);
		
		/*
		 * Region options
		 */
		Option regionOption = new Option("reg", "region", true, "Region to fetch events in the format lat1,lon1[,lat2,lon2]. "
				+ "If only one location is supplied, then a circular region is built and you must also supply the --radius "
				+ "argument. Otherwise, a rectangular region is defined between the two points. If omitted, the entire California "
				+ "model region is used");
		regionOption.setRequired(false);
		ops.addOption(regionOption);
		
		Option radiusOption = new Option("r", "radius", true, "Radius for a circular region");
		radiusOption.setRequired(false);
		ops.addOption(radiusOption);
		
		return ops;
	}
	
	private Long comcatStartTime;
	private Long comcatEndTime;
	
	private ObsEqkRupture startAtEvent;
	private ObsEqkRupture endAfterEvent;

	public ETAS_ComcatConfigBuilder(String[] args, Options options) {
		super(args, options);
	}

	@Override
	protected String getPrimaryEventID() {
		return null;
	}

	@Override
	protected Region buildRegion(ObsEqkRupture primaryRupture, TriggerRupture primaryTrigger) {
		if (cmd.hasOption("region")) {
			String regStr = cmd.getOptionValue("region");
			String[] regSplit = regStr.split(",");
			Preconditions.checkArgument(regSplit.length == 2 || regSplit.length == 4,
					"--region format: lat1,lon1[,lat2,lon2]");
			double lat1 = Double.parseDouble(regSplit[0]);
			double lon1 = Double.parseDouble(regSplit[1]);
			Location loc1 = new Location(lat1, lon1);
			if (regSplit.length == 2) {
				Preconditions.checkState(cmd.hasOption("radius"),
						"you only supplied one location for a region, but didn't supply a radius");
				double radius = Double.parseDouble(cmd.getOptionValue("radius"));
				return new ETAS_Config.CircularRegion(loc1, radius);
			}
			double lat2 = Double.parseDouble(regSplit[2]);
			double lon2 = Double.parseDouble(regSplit[3]);
			Location loc2 = new Location(lat2, lon2);
			
			return new Region(loc1, loc2);
		}
		return new CaliforniaRegions.RELM_TESTING();
	}
	
	private boolean hasFiniteStartTime() {
		return cmd.hasOption("start-time") || cmd.hasOption("start-date") || cmd.hasOption("start-at")
				|| cmd.hasOption("start-after-historical");
	}
	
	private boolean hasFiniteEndTime() {
		return cmd.hasOption("end-time") || cmd.hasOption("end-date") || cmd.hasOption("end-now")
				|| cmd.hasOption("end-after");
	}

	@Override
	protected long getComCatStartTime() {
		if (comcatStartTime == null) {
			if (cmd.hasOption("start-time")) {
				comcatStartTime = Long.parseLong(cmd.getOptionValue("start-time"));
				System.out.println("Will start ComCat data fetch at "+comcatStartTime);
			} else if (cmd.hasOption("start-date")) {
				String dateStr = cmd.getOptionValue("start-date");
				comcatStartTime = parseDateString(dateStr);
				System.out.println("Will start ComCat data fetch at "+comcatStartTime+", from date: "+dateStr);
			} else if (cmd.hasOption("start-at")) {
				String eventID = cmd.getOptionValue("start-at");
				System.out.println("Fetching start-at event: "+eventID);
				startAtEvent = accessor.fetchEvent(eventID, false);
				Preconditions.checkNotNull(startAtEvent, "Couldn't locate start-at event %s", eventID);
				comcatStartTime = startAtEvent.getOriginTime() - 1000l;
				System.out.println("Will start ComCat data fetch at "+comcatStartTime+" (1s before event "+eventID+")");
			} else if (cmd.hasOption("start-after-historical")) {
				comcatStartTime = parseDateString(historicalEndDate)+1000l;
				System.out.println("Will start ComCat data fetch at "+comcatStartTime
						+", 1s after historical catalog end date: "+historicalEndDate);
			} else {
				Preconditions.checkArgument(cmd.hasOption("start-days-before"), "Must supply start time!");
				Preconditions.checkState(hasFiniteEndTime(), "Cannot have both relative start and end times");
				double days = Double.parseDouble(cmd.getOptionValue("start-days-before"));
				long endTime = getComCatEndTime();
				
				comcatStartTime = endTime - (long)(ProbabilityModelsCalc.MILLISEC_PER_DAY*days);
				System.out.println("Will start ComCat data fetch at "+comcatStartTime+", "+(float)days+" before the end time of "+endTime);
			}
		}
		
		return comcatStartTime;
	}
	
	private long parseDateString(String dateStr) {
		try {
			if (dateStr.contains("T"))
				return argDateTimeFormat.parse(dateStr).getTime();
			return argDateFormat.parse(dateStr).getTime();
		} catch (ParseException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	private static String historicalEndDate = "2012-04-24T19:44:19";
	private static SimpleDateFormat argDateFormat = new SimpleDateFormat("yyyy-MM-dd");
	private static SimpleDateFormat argDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	static {
		TimeZone utc = TimeZone.getTimeZone("UTC");
		argDateFormat.setTimeZone(utc);
		argDateTimeFormat.setTimeZone(utc);
	}

	@Override
	protected long getComCatEndTime() {
		if (comcatEndTime == null) {
			if (cmd.hasOption("end-time")) {
				comcatEndTime = Long.parseLong(cmd.getOptionValue("end-time"));
				System.out.println("Will end ComCat data fetch at "+comcatEndTime);
			} else if (cmd.hasOption("end-date")) {
				String dateStr = cmd.getOptionValue("end-date");
				comcatEndTime = parseDateString(dateStr);
				System.out.println("Will end ComCat data fetch at "+comcatEndTime+", from date: "+dateStr);
			} else if (cmd.hasOption("end-now")) {
				comcatEndTime = System.currentTimeMillis();
				System.out.println("Will end ComCat data fetch at "+comcatEndTime+" (current time)");
			} else if (cmd.hasOption("end-after")) {
				String eventID = cmd.getOptionValue("end-after");
				System.out.println("Fetching end-after event: "+eventID);
				endAfterEvent = accessor.fetchEvent(eventID, false);
				Preconditions.checkNotNull(endAfterEvent, "Couldn't locate end-after event %s", eventID);
				comcatEndTime = endAfterEvent.getOriginTime() + 1000l;
				System.out.println("Will end ComCat data fetch at "+comcatEndTime+" (1s after event "+eventID+")");
			} else {
				Preconditions.checkArgument(cmd.hasOption("end-days-after"), "Must supply end time!");
				Preconditions.checkState(hasFiniteStartTime(), "Cannot have both relative start and end times");
				double days = Double.parseDouble(cmd.getOptionValue("end-days-after"));
				long startTime = getComCatStartTime();
				
				comcatEndTime = startTime + (long)(ProbabilityModelsCalc.MILLISEC_PER_DAY*days);
				System.out.println("Will end ComCat data fetch at "+comcatEndTime+", "+(float)days+" after the start time of "+startTime);
			}
		}
		
		return comcatEndTime;
	}

	@Override
	protected long getSimulationStartTime(long comcatStartTime, long comcatEndTime) {
		return comcatEndTime+1000l;
	}

	@Override
	protected String getBaseSimName() {
		long comcatStartTime = getComCatStartTime();
		long comcatEndTime = getComCatEndTime();
		Preconditions.checkNotNull(comcatStartTime);
		Preconditions.checkNotNull(comcatEndTime);
		long len = comcatEndTime - comcatStartTime;
		double years = (double)len/ProbabilityModelsCalc.MILLISEC_PER_YEAR;
		String name = "ComCat data "+ETAS_AbstractPlot.getTimeShortLabel(years);
		if (startAtEvent != null) {
			if (endAfterEvent == null)
				name += " after "+startAtEvent.getEventId();
			else
				name += " between "+startAtEvent.getEventId()+" and "+endAfterEvent.getEventId();
		} else if (endAfterEvent != null) {
			name += " before "+endAfterEvent.getEventId();
		} else if (cmd.hasOption("start-after-historical")) {
			name += " between historical and "+argDateFormat.format(new Date(comcatEndTime));
		} else if (cmd.hasOption("end-date") || cmd.hasOption("end-time") || cmd.hasOption("end-now")) {
			name += " before "+argDateFormat.format(new Date(comcatEndTime));
		} else {
			name += " after "+argDateFormat.format(new Date(comcatStartTime));
		}
		if (cmd.hasOption("region"))
			name += ", Custom Region";
		else
			name += ", Statewide";
		return name;
	}

	@Override
	protected String getScriptName() {
		return "u3etas_comcat_config_builder.sh";
	}

	@Override
	public void buildConfiguration() throws IOException {
		super.buildConfiguration();
		List<TriggerRupture> triggers = config.getTriggerRuptures();
		if (startAtEvent != null) {
			boolean found = false;
			for (TriggerRupture trigger : triggers) {
				if (startAtEvent.getEventId().equals(trigger.getComcatEventID())) {
					found = true;
					break;
				}
			}
			if (startAtEvent.getMag() < minMag) {
				Preconditions.checkState(!found, "Start-at event should have been excluded due to min mag?");
				System.err.println("WARNING: Start-at event was excluded due to M"+(float)startAtEvent.getMag()
					+" less than MinMag="+minMag.floatValue());
			} else {
				Preconditions.checkState(found, "Start-at event was supplied, but not present in final trigger list. "
						+ "Maybe it's not contained in the region or depth range?");
			}
		}
		if (endAfterEvent != null) {
			boolean found = false;
			for (int i=triggers.size(); --i>=0;) {
				TriggerRupture trigger = triggers.get(i);
				if (endAfterEvent.getEventId().equals(trigger.getComcatEventID())) {
					found = true;
					break;
				}
			}
			if (endAfterEvent.getMag() < minMag) {
				Preconditions.checkState(!found, "End-after event should have been excluded due to min mag?");
				System.err.println("WARNING: End-after event was excluded due to M"+(float)endAfterEvent.getMag()
					+" less than MinMag="+minMag.floatValue());
			} else {
				Preconditions.checkState(found, "End-after event was supplied, but not present in final trigger list. "
						+ "Maybe it's not contained in the region or depth/magnitude range?");
			}
		}
	}

}
