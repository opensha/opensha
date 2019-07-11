package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.opensha.commons.calc.magScalingRelations.magScalingRelImpl.WC1994_MagLengthRelationship;
import org.opensha.commons.data.comcat.ComcatAccessor;
import org.opensha.commons.data.comcat.ComcatRegion;
import org.opensha.commons.data.comcat.ComcatRegionAdapter;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.SimpleFaultData;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_TriggerRuptureFaultDistancesPlot;
import scratch.UCERF3.erf.ETAS.analysis.SimulationMarkdownGenerator;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class ETAS_ComcatEventConfigBuilder {
	
	private static Options createOptions() {
		Options ops = ETAS_ConfigBuilderUtils.getCommonOptions();

		Option idOption = new Option("id", "event-id", true, "Primary ComCat event ID of interest");
		idOption.setRequired(true);
		ops.addOption(idOption);

		Option nameOption = new Option("n", "name", true, "Event name");
		nameOption.setRequired(false);
		ops.addOption(nameOption);
		
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
		
		Option radiusOption = new Option("r", "radius", true, "Radius (km) about main event for avershock search. If not supplied, "
				+ "W-C 1994 radius used");
		radiusOption.setRequired(false);
		ops.addOption(radiusOption);
		
		Option minDepthOption = new Option("mind", "min-depth", true, "Min depth (km) considered for aftershocks (default: -10)");
		minDepthOption.setRequired(false);
		ops.addOption(minDepthOption);
		
		Option maxDepthOption = new Option("maxd", "max-depth", true, "Max depth (km) considered for aftershocks (default: is "
				+ "the larger of 20km and twice the hypocentral depth of the primary event)");
		maxDepthOption.setRequired(false);
		ops.addOption(maxDepthOption);
		
		Option minMagOption = new Option("minm", "min-mag", true, "Minimum magnitude event from ComCat (default: 2.5)");
		minMagOption.setRequired(false);
		ops.addOption(minMagOption);
		
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
		
		return ops;
	}

	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			String argz = "";
			
			argz += " --event-id ci38457511";
			argz += " --num-simulations 100000";
			argz += " --days-before 7";
			argz += " --end-now";
			
			// took these from first ComCat finite fault
			argz += " --finite-surf-dip 85";
			argz += " --finite-surf-strike 139";
			// ajusted these to match https://topex.ucsd.edu/SV_7.1/
			argz += " --finite-surf-length-along 29";
			argz += " --finite-surf-length-before 22";
			argz += " --finite-surf-upper-depth 0";
			argz += " --finite-surf-lower-depth 12";
			
			args = argz.trim().split(" ");
		}
		System.setProperty("java.awt.headless", "true");
		
		Options options = createOptions();
		
		CommandLineParser parser = new DefaultParser();
		
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			e.printStackTrace();
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(ClassUtils.getClassNameWithoutPackage(ETAS_ComcatEventConfigBuilder.class),
					options, true );
			System.exit(2);
			return;
		}
		
		try {
			String eventID = cmd.getOptionValue("event-id");
			
			ComcatAccessor accessor = new ComcatAccessor();
			
			ObsEqkRupture rup = accessor.fetchEvent(eventID, false);
			Preconditions.checkState(rup != null, "Event not found in ComCat: %s", eventID);
			
			Location hypo = rup.getHypocenterLocation();
			System.out.println("Found event "+eventID+" with M="+(float)rup.getMag()
				+" and Hypocenter=["+hypo+"] at "+rup.getOriginTime());
			
			Location surfPoint = new Location(hypo.getLatitude(), hypo.getLongitude());
			SimpleFaultData sfd = null;
			if (cmd.hasOption("finite-surf-dip")) {
				System.out.println("Building finite simple rupture surface");
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
				FaultTrace faultTrace = new FaultTrace("Trace");
				
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
				
				sfd = new SimpleFaultData(dip, lowerDepth, upperDepth, faultTrace);
			} else {
				Preconditions.checkState(!cmd.hasOption("finite-surf-strike"), "Must supply all or no finite surface options");
				Preconditions.checkState(!cmd.hasOption("finite-surf-length-along"), "Must supply all or no finite surface options");
				Preconditions.checkState(!cmd.hasOption("finite-surf-length-before"), "Must supply all or no finite surface options");
				Preconditions.checkState(!cmd.hasOption("finite-surf-upper-depth"), "Must supply all or no finite surface options");
				Preconditions.checkState(!cmd.hasOption("finite-surf-lower-depth"), "Must supply all or no finite surface options");
			}
			
			double minDepth = cmd.hasOption("min-depth") ? Double.parseDouble(cmd.getOptionValue("min-depth")) : -10;
			double maxDepth = cmd.hasOption("max-depth") ? Double.parseDouble(cmd.getOptionValue("max-depth"))
					: Double.max(20, 2*rup.getHypocenterLocation().getDepth());
			double minMag = cmd.hasOption("min-mag") ? Double.parseDouble(cmd.getOptionValue("min-mag")) : 2.5;
			
			double radius;
			if (cmd.hasOption("radius")) {
				radius = Double.parseDouble(cmd.getOptionValue("radius"));
			} else {
				radius = new WC1994_MagLengthRelationship().getMedianLength(rup.getMag());
				System.out.println("W-C 1994 Radius: "+(float)radius);
			}
			Region region = sfd == null ? new ETAS_Config.CircularRegion(surfPoint, radius)
					: new Region(sfd.getFaultTrace(), radius); // sausage around fault trace
			
			ObsEqkRupList comcatEvents = new ObsEqkRupList();
			
			// circular region above will already be an instance
			ComcatRegion regionAdapter = region instanceof ComcatRegion ? (ComcatRegion)region : new ComcatRegionAdapter(region);
			double daysBefore = 0d;
			if (cmd.hasOption("days-before") || cmd.hasOption("hours-before")) {
				daysBefore = cmd.hasOption("days-before") ? Double.parseDouble(cmd.getOptionValue("days-before"))
						: 24d * Double.parseDouble(cmd.getOptionValue("hours-before"));
				Preconditions.checkState(daysBefore > 0, "Days before event must be >0");
				long beforeStartMillis = rup.getOriginTime() - (long)(daysBefore * (double)ProbabilityModelsCalc.MILLISEC_PER_DAY + 0.5);
				System.out.println("Fetching events before primary from comcat:");
				System.out.println("\tStart time: "+beforeStartMillis+" ("+(float)daysBefore+" days before primary)");
				System.out.println("\tDepth range: ["+minDepth+" "+maxDepth+"]");
				System.out.println("\tRadius from primary: "+radius);
				
				ObsEqkRupList events = accessor.fetchEventList(eventID, beforeStartMillis, rup.getOriginTime(), minDepth, maxDepth,
						regionAdapter, false, false, minMag);
				System.out.println("Fetched "+events.size()+" events before primary event");
				comcatEvents.addAll(events);
			}
			
			double daysAfter = 0d;
			long simulationStartTime;
			if (cmd.hasOption("days-after") || cmd.hasOption("hours-after") || cmd.hasOption("end-now")) {
				long afterEndMillis;
				if (cmd.hasOption("end-now")) {
					afterEndMillis = System.currentTimeMillis();
					daysAfter = (double)(afterEndMillis - rup.getOriginTime()) / ProbabilityModelsCalc.MILLISEC_PER_DAY;
				} else {
					daysAfter = cmd.hasOption("days-after") ? Double.parseDouble(cmd.getOptionValue("days-after"))
							: 24d * Double.parseDouble(cmd.getOptionValue("hours-after"));
					Preconditions.checkState(daysAfter > 0, "Days after event must be >0");
					afterEndMillis = rup.getOriginTime() + (long)(daysAfter * (double)ProbabilityModelsCalc.MILLISEC_PER_DAY + 0.5);
				}
				System.out.println("Fetching events after primary from comcat:");
				System.out.println("\tEnd time: "+afterEndMillis+" ("+(float)daysAfter+" days after primary)");
				System.out.println("\tDepth range: ["+minDepth+" "+maxDepth+"]");
				System.out.println("\tRadius from primary: "+radius);
				
				ObsEqkRupList events = accessor.fetchEventList(eventID, rup.getOriginTime(), afterEndMillis, minDepth, maxDepth,
						regionAdapter, false, false, minMag);
				System.out.println("Fetched "+events.size()+" events before primary event");
				comcatEvents.addAll(events);
				simulationStartTime = afterEndMillis + 1000l;
			} else {
				simulationStartTime = rup.getOriginTime() + 1000l;
			}
			comcatEvents.add(rup);
			comcatEvents.sortByOriginTime();
			
			long lastInputTime = Long.MIN_VALUE;
			List<TriggerRupture> triggerRuptures = new ArrayList<>();
			
			HashSet<String> prevIDs = new HashSet<>();
			for (ObsEqkRupture eq : comcatEvents) {
				Preconditions.checkState(!prevIDs.contains(eq.getEventId()), "Duplicate event found: %s", eventID);
				prevIDs.add(eq.getEventId());
				if (eq == rup && sfd != null)
					triggerRuptures.add(new TriggerRupture.SimpleFault(eq.getOriginTime(), eq.getHypocenterLocation(), eq.getMag(), sfd));
				else
					triggerRuptures.add(new TriggerRupture.Point(eq.getHypocenterLocation(), eq.getOriginTime(), eq.getMag()));
				lastInputTime = Long.max(lastInputTime, eq.getOriginTime());
			}
			Preconditions.checkState(lastInputTime < simulationStartTime,
					"Last input event time (%s) is after simulation start time (%s)? ", lastInputTime, simulationStartTime);
			
			String name;
			if (cmd.hasOption("name")) {
				name = cmd.getOptionValue("name");
			} else {
				name = "ComCat M"+(float)rup.getMag()+" ("+eventID+")";
				if (daysAfter > 0)
					name += ", "+daysDF.format(daysAfter)+" Days After";
				if (sfd != null)
					name += ", Finite Surface";
			}
			
			ETAS_Config config = ETAS_ConfigBuilderUtils.buildBasicConfig(cmd, name, triggerRuptures);
			config.setStartTimeMillis(simulationStartTime);
			config.setComcatRegion(region);
			
			File outputDir = config.getOutputDir();
			System.out.println("Output dir: "+outputDir.getPath());
			File resolved = ETAS_Config.resolvePath(outputDir);
			if (!resolved.equals(outputDir))
				System.out.println("Resolved output dir: "+resolved.getAbsolutePath());
			outputDir = resolved;
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
			File configFile = new File(outputDir, "config.json");
			if (configFile.exists())
				System.err.println("WARNING: overwriting previous configuration file");
			config.writeJSON(configFile);
			
			ETAS_ConfigBuilderUtils.checkWriteHPC(config, configFile, cmd);
			
			// now write plots
			File plotDir = new File(outputDir, "config_input_plots");
			System.out.println();
			System.out.println("Writing diagnostic plots and input data to: "+plotDir.getAbsolutePath());
			Preconditions.checkState(plotDir.exists() || plotDir.mkdir());
			
			List<String> lines = new ArrayList<>();
			lines.add("# ETAS Configuration for "+name);
			lines.add("");
			ETAS_Launcher launcher = new ETAS_Launcher(config, false);
			lines.addAll(SimulationMarkdownGenerator.getCatalogSummarytable(config, config.getNumSimulations(),
					launcher.getTriggerRuptures(), launcher.getHistQkList()));
			lines.add("");
			
			int tocIndex = lines.size();
			String topLink = "*[(top)](#table-of-contents)*";
			lines.add("");
			
			FaultSystemSolution fss = launcher.checkOutFSS();
			
			ETAS_TriggerRuptureFaultDistancesPlot mapPlot = new ETAS_TriggerRuptureFaultDistancesPlot(config, launcher, 25d);
			mapPlot.finalize(plotDir, fss);
			lines.addAll(mapPlot.generateMarkdown(".", "##", topLink));
			
			lines.add("");
			lines.add("## JSON Input File");
			lines.add(topLink); lines.add("");
			lines.add("```");
			for (String line : Files.readLines(configFile, Charset.defaultCharset()))
				lines.add(line);
			lines.add("```");
			lines.add("");
			
			launcher.checkInFSS(fss);
			
			List<String> tocLines = new ArrayList<>();
			tocLines.add("## Table Of Contents");
			tocLines.add("");
			tocLines.addAll(MarkdownUtils.buildTOC(lines, 2));
			
			lines.addAll(tocIndex, tocLines);
			
			System.out.println("Writing markdown and HTML");
			MarkdownUtils.writeReadmeAndHTML(lines, plotDir);
			System.out.println("DONE");
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	private static final DecimalFormat daysDF = new DecimalFormat("0.#");

}
