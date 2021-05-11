package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.dom4j.DocumentException;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.SimpleFaultData;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture.EdgeFault;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture.SectionBased;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture.SimpleFault;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.utils.FaultSystemIO;

public class ETAS_ComcatEventConfigBuilder extends ETAS_AbstractComcatConfigBuilder {

	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			String argz = "";

//			argz += " --event-id ci38443183"; // 2019 Searles Valley M6.4
//			argz += " --event-id ci38457511"; // 2019 Ridgecrest M7.1
//			argz += " --event-id ci39838928"; // 4/5/2021 Inglewood M4
//			argz += " --event-id ci39462536"; // 2020 Ridgecrest M5.5
			argz += " --event-id nc73559265"; // 2021 Truckee 4.7
//			argz += " --mag-complete 3.5";
//			argz += " --event-id nn00719663"; // 3/20/2020 Lake Tahoe area M5
//			argz += " --event-id ci39126079"; // 4/4/2020 SJC Anza M4.9
//			argz += " --event-id ci38488354"; // 4/4/2020 SJC Anza M4.9
//			argz += " --event-id ci39493944"; // 6/24/2020 Long Pine M5.8
//			argz += " --event-id ci39338407"; // 6/24/2020 Long Pine M5.8
//			argz += " --event-id ci38695658"; // 9/18/2020 El Monte M4.6
//			argz += " --event-id ci39641528"; // 9/30/2020 Westmorland (near Bombay) M4.9
			argz += " --radius 25";
//			argz += " --mag-complete 2.5";
			
//			argz += " --event-id nc73292360"; // 10/15/2019 Tres Pinos, CA M4.71
//			argz += " --region 38.5,-122.75,36.25,-120.5";

			argz += " --num-simulations 100000";
//			argz += " --num-simulations 1000";
			argz += " --days-before 7";
//			argz += " --days-after 7";
//			argz += " --hours-after 33.75";
//			argz += " --end-now";
//			argz += " --end-time 1591234330000";
//			argz += " --name-add Before-M5.5";
//			argz += " --gridded-only";
			argz += " --max-point-src-mag 6";
//			argz += " --impose-gr";
//			argz += " --prob-model NO_ERT";
//			argz += " --include-spontaneous";
//			argz += " --scale-factor 1.0";
//			argz += " --name-add SmallTest";
//			argz += " --fault-model FM3_2";
//			argz += " --etas-k-cov 1.16";
			argz += " --etas-k-cov 1.5";
			
//			argz += " --num-simulations 1000000";
//			argz += " --fault-model FM2_1";

			// from Morgan by e-mail 8/26/19 for Ridgcrest from Nic's ETAS GUI
//			argz += " --etas-k -3.03 --etas-p 1.15 --etas-c "+(float)+Math.pow(10, -3.33);
//			argz += " --event-etas-k -2.3";
			// from Morgan by e-mail 8/26/19 for Ridgcrest from Nic's ETAS GUI with a modified c value
//			argz += " --etas-k -3.03 --etas-p 1.15 --etas-c 0.002";
//			argz += " --event-etas-k -2.3";
			// from Morgan by e-mail 8/28/19 for Ridgcrest from her own ETAS code
//			argz += " --etas-k -2.2833 --etas-p 1.2434 --etas-c 0.0102";
			// from when I ran Nic's ETAS GUI
//			argz += " --etas-k -2.666 --etas-p 1.07133 --etas-c "+(float)+8.0307e-4;
//			argz += " --event-etas-k -2.2412";
			// new from Nic by e-mail 9/27 modified for Mc=2.5
//			argz += " --etas-k -2.52 --etas-p 1.21 --etas-c "+(float)Math.pow(10, -2.38);
//			argz += " --mod-mag ci38443183:6.53";
			// new from Morgan by e-mail 9/30
//			argz += " --etas-k -2.32 --etas-p 1.21 --etas-c "+(float)Math.pow(10, -2.14);
//			argz += " --mod-mag ci38443183:6.56";
			// new again from Morgan by e-mail 9/30
//			argz += " --etas-k -2.3856 --etas-p 1.2164 --etas-c 0.0068906";
			// new again from Morgan by e-mail 9/30
//			argz += " --etas-k -2.5807 --etas-p 1.2481 --etas-c 0.0057006";
			
//			// took these from first ComCat finite fault
//			argz += " --finite-surf-dip 85";
//			argz += " --finite-surf-strike 139";
//			// adjusted these to match https://topex.ucsd.edu/SV_7.1/
//			argz += " --finite-surf-length-along 29";
//			argz += " --finite-surf-length-before 22";
//			argz += " --finite-surf-upper-depth 0";
//			argz += " --finite-surf-lower-depth 12";

//			argz += " --finite-surf-inversion";
//			argz += " --finite-surf-inversion-min-slip 0.5";
//			argz += " --finite-surf-inversion-min-mag 6";

			argz += " --finite-surf-shakemap";
			argz += " --finite-surf-shakemap-min-mag 4.5";
//			argz += " --finite-surf-shakemap-version 10";
//			argz += " --finite-surf-shakemap-planar-extents";
//			argz += " --finite-surf-shakemap-min-mag 7";
			
//			argz += " --kml-surf /home/kevin/OpenSHA/UCERF3/etas/ridgecrest_plots/ridgecrest.kmz";
//			argz += " --kml-surf-lower-depth 12";
//			argz += " --kml-surf-name Field";
//			argz += " --kml-surf-name-contains";
//			argz += " --name-add Updated-Depth";
			
//			argz += " --random-seed 123456789";
			
			// hpc options
			argz += " --hpc-site USC_HPC";
			argz += " --nodes 32";
			argz += " --hours 24";
////			argz += " --queue scec_hiprio";
//			argz += " --queue scec";
//			argz += " --hpc-site TACC_FRONTERA";
//			argz += " --nodes 20";
//			argz += " --hours 10";
//			argz += " --queue normal";
			
			args = argz.trim().split(" ");
		}
		System.setProperty("java.awt.headless", "true");
		
		Options options = createOptions();
		
		try {
			ETAS_ComcatEventConfigBuilder builder = new ETAS_ComcatEventConfigBuilder(args, options);
			
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
	
		Option idOption = new Option("id", "event-id", true, "Primary ComCat event ID of interest");
		idOption.setRequired(true);
		ops.addOption(idOption);
		
		/*
		 * Fore/aftershock time and region options  
		 */
		Option daysBeforeOption = new Option("db", "days-before", true, "Number of hours before primary event (which is "
				+ "specified with --event-id <id>) to include events in the region as triggers");
		daysBeforeOption.setRequired(false);
		ops.addOption(daysBeforeOption);
		
		Option hoursBeforeOption = new Option("hb", "hours-before", true, "Number of days before primary event (which is "
				+ "specified with --event-id <id>) to include events in the region as triggers");
		hoursBeforeOption.setRequired(false);
		ops.addOption(hoursBeforeOption);
		
		Option daysAfterOption = new Option("da", "days-after", true, "Number of days after primary event (which is "
				+ "specified with --event-id <id>) to include events in the region as triggers");
		daysAfterOption.setRequired(false);
		ops.addOption(daysAfterOption);
		
		Option hoursAfterOption = new Option("ha", "hours-after", true, "Number of hours after primary event (which is "
				+ "specified with --event-id <id>) to include events in the region as triggers");
		hoursAfterOption.setRequired(false);
		ops.addOption(hoursAfterOption);
		
		Option nowOption = new Option("now", "end-now", false, "Include all aftershocks of primary event (which is "
				+ "specified with --event-id <id>) up to the current instant");
		nowOption.setRequired(false);
		ops.addOption(nowOption);
		
		Option startTimeOption = new Option("st", "start-time", true, "ComCat data start time in epoch milliseconds");
		startTimeOption.setRequired(false);
		ops.addOption(startTimeOption);
		
		Option startDateOption = new Option("sd", "start-date", true, "ComCat data start date in the format 'yyyy-MM-dd' "
				+ "(e.g. 2019-01-01) or 'yyyy-MM-ddTHH:mm:ss' (e.g. 2019-01-01T01:23:45). All dates and times in UTC");
		startDateOption.setRequired(false);
		ops.addOption(startDateOption);
		
		Option endTimeOption = new Option("et", "end-time", true, "ComCat data end time in epoch milliseconds");
		endTimeOption.setRequired(false);
		ops.addOption(endTimeOption);
		
		Option endDateOption = new Option("ed", "end-date", true, "ComCat data end date in the format 'yyyy-MM-dd' "
				+ "(e.g. 2019-01-01) or 'yyyy-MM-ddTHH:mm:ss' (e.g. 2019-01-01T01:23:45). All dates and times in UTC.");
		endDateOption.setRequired(false);
		ops.addOption(endDateOption);
		
		Option regionOption = new Option("reg", "region", true, "Region to fetch events in the format lat1,lon1[,lat2,lon2]. "
				+ "If only one location is supplied, then a circular region is built and you must also supply the --radius "
				+ "argument. Otherwise, if two locations and the --radius option is supplied, a sausage region is drawn around "
				+ "the line defined, otherwise a rectangular region is defined between the two points.");
		regionOption.setRequired(false);
		ops.addOption(regionOption);
		
		Option radiusOption = new Option("r", "radius", true, "Radius (km) about main event for avershock search. If not supplied, "
				+ "W-C 1994 radius used");
		radiusOption.setRequired(false);
		ops.addOption(radiusOption);
		
		/*
		 * custom finite surface options
		 */
		Option finiteDipOption = new Option("fd", "finite-surf-dip", true, "Dip (degrees) of the primary rupture's finite rupture "
				+ "surface");
		finiteDipOption.setRequired(false);
		ops.addOption(finiteDipOption);
		
		Option finiteStrikeOption = new Option("fs", "finite-surf-strike", true, "Strike (degrees) of the primary rupture's finite "
				+ "rupture surface");
		finiteStrikeOption.setRequired(false);
		ops.addOption(finiteStrikeOption);
		
		Option finiteLengthAlongOption = new Option("fla", "finite-surf-length-along", true, "Length (km) of the primary rupture in "
				+ "the along-strike direction, from the hypocenter");
		finiteLengthAlongOption.setRequired(false);
		ops.addOption(finiteLengthAlongOption);
		
		Option finiteLengthBeforeOption = new Option("flb", "finite-surf-length-before", true, "Length (km) of the primary rupture in "
				+ "the along-strike direction, before the hypocenter");
		finiteLengthBeforeOption.setRequired(false);
		ops.addOption(finiteLengthBeforeOption);
		
		Option finiteUpperDepth = new Option("fud", "finite-surf-upper-depth", true, "Upper depth (km) of the finite rupture surface");
		finiteUpperDepth.setRequired(false);
		ops.addOption(finiteUpperDepth);
		
		Option finiteLowerDepth = new Option("fld", "finite-surf-lower-depth", true, "Lower depth (km) of the finite rupture surface");
		finiteLowerDepth.setRequired(false);
		ops.addOption(finiteLowerDepth);
		
		/*
		 * KML finite surface options
		 */
		createKMLOptions(ops);
		
		return ops;
	}
	
	private static class CustomRuptureBuilder implements RuptureBuilder {

		private String eventID;
		private double dip;
		private double strike;
		private double lenAlong;
		private double lenBefore;
		private double upperDepth;
		private double lowerDepth;
		
		private boolean processed = false;

		public CustomRuptureBuilder(String eventID, double dip, double strike, double lenAlong, double lenBefore,
				double upperDepth, double lowerDepth) {
			this.eventID = eventID;
			this.dip = dip;
			this.strike = strike;
			this.lenAlong = lenAlong;
			this.lenBefore = lenBefore;
			this.upperDepth = upperDepth;
			this.lowerDepth = lowerDepth;
		}

		@Override
		public TriggerRupture build(ObsEqkRupture rup, int[] resetSubSects) {
			if (!rup.getEventId().equals(eventID))
				return null;
			FaultTrace faultTrace = new FaultTrace("Trace");
			
			Location hypo = rup.getHypocenterLocation();
			
			Location surfPoint = new Location(hypo.getLatitude(), hypo.getLongitude());
			
			if (dip < 90d && hypo.getDepth() != 0d) {
				// need to move it up dip
				double horzDist = hypo.getDepth()/Math.tan(Math.toRadians(dip));
				System.out.println("Moving hypocenter up dip to determine fault trace");
				System.out.println("\tHorizontal distance: "+(float)horzDist);
				double direction = strike - 90d;
				if (direction < 0)
					direction += 360;
				System.out.println("\tDirection: "+(float)direction);
				surfPoint = LocationUtils.location(surfPoint, Math.toRadians(direction), horzDist);
				System.out.println("\tNew surface point on fault trace: "+surfPoint);
			}
			if (lenBefore > 0) {
				double direction = strike + 180;
				if (direction >= 360)
					direction -= 360;
				Location locBefore = LocationUtils.location(surfPoint, Math.toRadians(direction), lenBefore);
				System.out.println("Trace point before hypocenter: "+locBefore);
				faultTrace.add(locBefore);
			}
			
			faultTrace.add(surfPoint);
			
			if (lenAlong > 0) {
				Location locAfter = LocationUtils.location(surfPoint, Math.toRadians(strike), lenAlong);
				System.out.println("Trace point after hypocenter: "+locAfter);
				faultTrace.add(locAfter);
			}
			System.out.println("Total trace length: "+(float)faultTrace.getTraceLength());
			
			SimpleFaultData sfd = new SimpleFaultData(dip, lowerDepth, upperDepth, faultTrace);
			processed = true;
			return new TriggerRupture.SimpleFault(rup.getOriginTime(), rup.getHypocenterLocation(), rup.getMag(),
					resetSubSects, sfd);
		}

		@Override
		public String getDisplayName(int totalNumRuptures) {
			if (processed)
				return "Custom Surface";
			return null;
		}
		
	}
	
	private static class KMLRuptureBuilder implements RuptureBuilder {
		
		private CommandLine cmd;
		private String eventID;
		
		private boolean processed = false;

		public KMLRuptureBuilder(CommandLine cmd, String eventID) {
			this.cmd = cmd;
			this.eventID = eventID;
		}

		@Override
		public TriggerRupture build(ObsEqkRupture rup, int[] resetSubSects) {
			if (rup.getEventId().equals(eventID)) {
				SimpleFaultData[] sfd = loadKMLSurface(cmd);
				processed = true;
				return new TriggerRupture.SimpleFault(rup.getOriginTime(), rup.getHypocenterLocation(),
						rup.getMag(), resetSubSects, sfd);
			}
			return null;
		}

		@Override
		public String getDisplayName(int totalNumRuptures) {
			if (processed) {
				return "KML Surface";
			}
			return null;
		}
		
	}
	
	private double daysBefore;
	private double daysAfter;
	
	public ETAS_ComcatEventConfigBuilder(String[] args, Options options) {
		super(args, options);
	}
	
	@Override
	protected String getPrimaryEventID() {
		String eventID = cmd.getOptionValue("event-id");
		Preconditions.checkArgument(eventID != null, "Must supply event ID!");
		return eventID;
	}

	@Override
	protected Region buildRegion(ObsEqkRupture primaryRupture, TriggerRupture primaryTrigger) {
		double radius;
		if (cmd.hasOption("radius")) {
			radius = Double.parseDouble(cmd.getOptionValue("radius"));
		} else {
			radius = new WC1994_MagLengthRelationship().getMedianLength(primaryRupture.getMag());
			System.out.println("W-C 1994 Radius: "+(float)radius);
		}
		Region region;
		if (cmd.hasOption("region")) {
			// use user defined region
			String regStr = cmd.getOptionValue("region");
			String[] regSplit = regStr.split(",");
			Preconditions.checkArgument(regSplit.length == 2 || regSplit.length == 4,
					"--region format: lat1,lon1[,lat2,lon2]");
			double lat1 = Double.parseDouble(regSplit[0]);
			double lon1 = Double.parseDouble(regSplit[1]);
			Location loc1 = new Location(lat1, lon1);
			Location loc2;
			if (regSplit.length == 2) {
				Preconditions.checkState(cmd.hasOption("radius"),
						"you only supplied one location for a region, but didn't supply a radius");
				loc2 = null;
			} else {
				double lat2 = Double.parseDouble(regSplit[2]);
				double lon2 = Double.parseDouble(regSplit[3]);
				loc2 = new Location(lat2, lon2);
			}
			
			if (cmd.hasOption("radius")) {
				if (loc2 == null) {
					// circle
					region = new ETAS_Config.CircularRegion(loc1, radius);
				} else {
					// sausage
					LocationList line = new LocationList();
					line.add(loc1);
					line.add(loc2);
					region = new Region(line, radius);
				}
			} else {
				region = new Region(loc1, loc2);
			}
		} else {
			FaultTrace primaryTrace;
			
			if (primaryTrigger instanceof TriggerRupture.SimpleFault) {
				primaryTrace = new FaultTrace(null);
				for (SimpleFaultData sfd : ((SimpleFault)primaryTrigger).sfds)
					primaryTrace.addAll(sfd.getFaultTrace());
			} else if (primaryTrigger instanceof TriggerRupture.SectionBased) {
				primaryTrace = new FaultTrace(null);
				FaultModels fm = getFM(cmd);
				System.out.println("Need to load UCERF3 Fault System Solution to build rupture from sub sections");
				File fssFile = fmFSSfileMap.get(fm);
				if (fssFile != null) {
					fssFile = ETAS_Config.resolvePath(fssFile);
				}
				FaultSystemRupSet rupSet;
				try {
					rupSet = FaultSystemIO.loadRupSet(fssFile);
				} catch (IOException | DocumentException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
				for (int sectIndex : ((SectionBased)primaryTrigger).subSects)
					primaryTrace.addAll(rupSet.getFaultSectionData(sectIndex).getFaultTrace());
			} else if (primaryTrigger instanceof TriggerRupture.EdgeFault) {
				primaryTrace = new FaultTrace(null);
				for (LocationList outline : ((EdgeFault)primaryTrigger).outlines) {
					// add first half of the outline (top)
					for (int i=0; i<outline.size()/2; i++)
						primaryTrace.add(outline.get(i));
				}
			} else {
				primaryTrace = null;
			}
			
			if (primaryTrace != null) {
				// this might fail for really complex ones
				try {
					region = new Region(primaryTrace, radius);
				} catch (Exception e) {
					// use simple trace through extents
					System.out.println("Failed to create sausage region around SM fault trace, "
							+ "using simple straight line trace");
					
					LocationList planar = getPlanarExtentsSurface(new LocationList[] { primaryTrace} )[0];
					FaultTrace simpleTrace = new FaultTrace(null);
					simpleTrace.add(planar.get(0));
					simpleTrace.add(planar.get(1));
					region = new Region(simpleTrace, radius);
				}
			} else {
				Location hypo = primaryRupture.getHypocenterLocation();
				region = new ETAS_Config.CircularRegion(new Location(hypo.getLatitude(), hypo.getLongitude()), radius);
			}
		}
		return region;
	}

	@Override
	protected long getComCatStartTime() {
		if (cmd.hasOption("days-before") || cmd.hasOption("hours-before")) {
			daysBefore = cmd.hasOption("days-before") ? Double.parseDouble(cmd.getOptionValue("days-before"))
					: Double.parseDouble(cmd.getOptionValue("hours-before")) / 24d;
			Preconditions.checkState(daysBefore > 0, "Days before event must be >0");
			long beforeStartMillis = primaryRupture.getOriginTime()
					- (long)(daysBefore * (double)ProbabilityModelsCalc.MILLISEC_PER_DAY + 0.5);
			System.out.println("Fetching events before primary from comcat:");
			System.out.println("\tComCat start time: "+beforeStartMillis+" ("+(float)daysBefore+" days before primary)");
			
			return beforeStartMillis;
		} else if (cmd.hasOption("start-time")) {
			long comcatStartTime = Long.parseLong(cmd.getOptionValue("start-time"));
			System.out.println("Will start ComCat data fetch at "+comcatStartTime);
			return comcatStartTime;
		} else if (cmd.hasOption("start-date")) {
			String dateStr = cmd.getOptionValue("start-date");
			long comcatStartTime = parseDateString(dateStr);
			System.out.println("Will start ComCat data fetch at "+comcatStartTime+", from date: "+dateStr);
			return comcatStartTime;
		} else {
			return primaryRupture.getOriginTime();
		}
	}

	@Override
	protected long getComCatEndTime() {
		if (cmd.hasOption("days-after") || cmd.hasOption("hours-after") || cmd.hasOption("end-now")) {
			long afterEndMillis;
			long curTime = System.currentTimeMillis();
			if (cmd.hasOption("end-now")) {
				afterEndMillis = curTime;
				daysAfter = (double)(afterEndMillis - primaryRupture.getOriginTime()) / ProbabilityModelsCalc.MILLISEC_PER_DAY;
			} else {
				daysAfter = cmd.hasOption("days-after") ? Double.parseDouble(cmd.getOptionValue("days-after"))
						: Double.parseDouble(cmd.getOptionValue("hours-after")) / 24d;
				Preconditions.checkState(daysAfter > 0, "Days after event must be >0");
				afterEndMillis = primaryRupture.getOriginTime()
						+ (long)(daysAfter * (double)ProbabilityModelsCalc.MILLISEC_PER_DAY + 0.5);
				Preconditions.checkState(afterEndMillis <= curTime, "ComCat end time is %s in the future: %s > %s",
						ETAS_AbstractPlot.getTimeShortLabel((double)(afterEndMillis - curTime)/ ProbabilityModelsCalc.MILLISEC_PER_YEAR),
						afterEndMillis, curTime);
			}
			System.out.println("Fetching events after primary from comcat:");
			System.out.println("\tEnd time: "+afterEndMillis+" ("+(float)daysAfter+" days after primary)");
			
			return afterEndMillis;
		} else if (cmd.hasOption("end-time")) {
			long comcatEndTime = Long.parseLong(cmd.getOptionValue("end-time"));
			System.out.println("Will end ComCat data fetch at "+comcatEndTime);
			Preconditions.checkArgument(comcatEndTime >= primaryRupture.getOriginTime(),
					"End time is before primary rupture origin time");
			daysAfter = (double)(comcatEndTime - primaryRupture.getOriginTime()) / ProbabilityModelsCalc.MILLISEC_PER_DAY;
			return comcatEndTime;
		} else if (cmd.hasOption("end-date")) {
			String dateStr = cmd.getOptionValue("end-date");
			long comcatEndTime = parseDateString(dateStr);
			System.out.println("Will end ComCat data fetch at "+comcatEndTime+", from date: "+dateStr);
			Preconditions.checkArgument(comcatEndTime >= primaryRupture.getOriginTime(),
					"End time is before primary rupture origin time");
			daysAfter = (double)(comcatEndTime - primaryRupture.getOriginTime()) / ProbabilityModelsCalc.MILLISEC_PER_DAY;
			return comcatEndTime;
		} else {
			return primaryRupture.getOriginTime();
		}
	}

	@Override
	protected long getSimulationStartTime(long comcatStartTime, long comcatEndTime) {
		return comcatEndTime+1000l;
	}

	@Override
	protected String getBaseSimName() {
		String name = "ComCat M"+(float)primaryRupture.getMag()+" ("+primaryRupture.getEventId()+")";
		if (daysAfter > 0)
			name += ", "+daysDF.format(daysAfter)+" Days After";
		return name;
	}

	@Override
	protected String getScriptName() {
		return "u3etas_comcat_event_config_builder.sh";
	}
	
	@Override
	protected RuptureBuilder getRuptureBuilder(CommandLine cmd, Option option, String primaryEventID) {
		switch (option.getLongOpt()) {
		case "finite-surf-dip":
			Preconditions.checkState(cmd.hasOption("finite-surf-strike"), "Must supply all or no finite surface options");
			Preconditions.checkState(cmd.hasOption("finite-surf-length-along"), "Must supply all or no finite surface options");
			Preconditions.checkState(cmd.hasOption("finite-surf-length-before"), "Must supply all or no finite surface options");
			Preconditions.checkState(cmd.hasOption("finite-surf-upper-depth"), "Must supply all or no finite surface options");
			Preconditions.checkState(cmd.hasOption("finite-surf-lower-depth"), "Must supply all or no finite surface options");
			double dip = Double.parseDouble(cmd.getOptionValue("finite-surf-dip"));
			double strike = Double.parseDouble(cmd.getOptionValue("finite-surf-strike"));
			double lenAlong = Double.parseDouble(cmd.getOptionValue("finite-surf-length-along"));
			double lenBefore = Double.parseDouble(cmd.getOptionValue("finite-surf-length-before"));
			Preconditions.checkState(lenAlong > 0 || lenBefore > 0, "Length along or before must be > 0");
			double upperDepth = Double.parseDouble(cmd.getOptionValue("finite-surf-upper-depth"));
			double lowerDepth = Double.parseDouble(cmd.getOptionValue("finite-surf-lower-depth"));
			Preconditions.checkArgument(lowerDepth > upperDepth, "Lower depth must be > upper depth");
			return new CustomRuptureBuilder(primaryEventID, dip, strike, lenAlong, lenBefore, upperDepth, lowerDepth);
		case "kml-surf":
			return new KMLRuptureBuilder(cmd, primaryEventID);

		default:
			return super.getRuptureBuilder(cmd, option, primaryEventID);
		}
	}
	
	private static final DecimalFormat daysDF = new DecimalFormat("0.#");

}
