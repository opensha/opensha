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
import org.opensha.commons.data.comcat.ComcatInvertedFiniteFault;
import org.opensha.commons.data.comcat.ComcatInvertedFiniteFaultAccessor;
import org.opensha.commons.data.comcat.ComcatRegion;
import org.opensha.commons.data.comcat.ComcatRegionAdapter;
import org.opensha.commons.data.comcat.ShakeMapFiniteFaultAccessor;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.LocationUtils;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.SimpleFaultData;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.primitives.Ints;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CubeDiscretizationParams;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_TriggerRuptureFaultDistancesPlot;
import scratch.UCERF3.erf.ETAS.analysis.SimulationMarkdownGenerator;
import scratch.UCERF3.erf.ETAS.association.FiniteFaultSectionResetCalc;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.ComcatMetadata;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.inversion.InversionTargetMFDs;

public class ETAS_ComcatEventConfigBuilder extends ETAS_ConfigBuilder {
	
	private static Options createOptions() {
		Options ops = getCommonOptions();

		Option idOption = new Option("id", "event-id", true, "Primary ComCat event ID of interest");
		idOption.setRequired(true);
		ops.addOption(idOption);

		Option nameOption = new Option("n", "name", true, "Simulation name");
		nameOption.setRequired(false);
		ops.addOption(nameOption);

		Option nameAddOption = new Option("na", "name-add", true, "Custom addendum to automatically generated name");
		nameAddOption.setRequired(false);
		ops.addOption(nameAddOption);
		
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
		
		Option finiteResetSects = new Option("rs", "reset-sects", true, "UCERF3 sub-section indices to reset (elastic rebound)"
				+ " for primary event");
		finiteResetSects.setRequired(false);
		ops.addOption(finiteResetSects);
		
		/*
		 * finite surface from ShakeMap options
		 */
		Option finiteSurfShakeMap = new Option("fssm", "finite-surf-shakemap", false,
				"Fetch finite surfaces from ShakeMap when available. By default, applies only to the specified event. Supply "
				+ "--finite-surf-shakemap-min-mag to specify a minimum magnitude above which to always look for shakemap surfaces. "
				+ "Any custom finite surfaces specified will take precedence over shakemap source");
		finiteSurfShakeMap.setRequired(false);
		ops.addOption(finiteSurfShakeMap);
		
		Option finiteSurfShakeMapVersion = new Option("fssmv", "finite-surf-shakemap-version", true,
				"ShakeMap source Version number to use for mainshock. Must also supply --finite-surf-shakemap option");
		finiteSurfShakeMapVersion.setRequired(false);
		ops.addOption(finiteSurfShakeMapVersion);
		
		Option finiteSurfShakeMapPlanar = new Option("fssmp", "finite-surf-shakemap-planar-extents", false,
				"Flag to build a planar surface based off of the extents (min & max lat, lon, depth) instead of using the actual surface");
		finiteSurfShakeMapPlanar.setRequired(false);
		ops.addOption(finiteSurfShakeMapPlanar);
		
		Option finiteSurfShakeMapMag = new Option("fssmmm", "finite-surf-shakemap-min-mag", true,
				"Minimum magnitude above which to search for shakemap finite surfaces. Must also supply --finite-surf-shakemap option");
		finiteSurfShakeMapMag.setRequired(false);
		ops.addOption(finiteSurfShakeMapMag);
		
		/*
		 * inverted finite surface from ComCat options
		 */
		Option finiteSurfInversion = new Option("fsi", "finite-surf-inversion", false,
				"Fetch inverted finite surfaces from ComCat when available. By default, applies only to the specified event. Supply "
				+ "--finite-surf-inversion-min-mag to specify a minimum magnitude above which to always look for inverted surfaces. "
				+ "Any custom finite surfaces specified will take precedence over inversion source. This takes precedence over ShakeMap.");
		finiteSurfInversion.setRequired(false);
		ops.addOption(finiteSurfInversion);
		
		Option finiteSurfInversionMag = new Option("fsimm", "finite-surf-inversion-min-mag", true,
				"Minimum magnitude above which to search for inverted finite surfaces. Must also supply --finite-surf-inversion option");
		finiteSurfInversionMag.setRequired(false);
		ops.addOption(finiteSurfInversionMag);
		
		Option finiteSurfInversionSlip = new Option("fsis", "finite-surf-inversion-min-slip", true,
				"Minimum slip on each patch for inverted fault surface for inclusion in ETAS rupture surface. "
				+ "Must also supply --finite-surf-inversion option");
		finiteSurfInversionSlip.setRequired(false);
		ops.addOption(finiteSurfInversionSlip);
		
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
		
		Option finiteSurfFromSects = new Option("fsfs", "finite-surf-from-sections", false, "If supplied, finite rupture surface will "
				+ "be constructed from the fault sections specified with --reset-sections");
		finiteSurfFromSects.setRequired(false);
		ops.addOption(finiteSurfFromSects);
		
		/*
		 * Mainshock-specific ETAS params (all others will be applied to all other ComCat events
		 */
		Option kOption = new Option("mek", "mainshock-etas-k", true, "Mainshock-specific ETAS productivity parameter parameter,"
				+ " k, in Log10 units");
		kOption.setRequired(false);
		ops.addOption(kOption);
		
		Option pOption = new Option("mep", "mainshock-etas-p", true, "Mainshock-specific ETAS temporal decay paramter, p");
		pOption.setRequired(false);
		ops.addOption(pOption);
		
		Option cOption = new Option("mec", "mainshock-etas-c", true, "Mainshock-specific ETAS minimum time paramter, c");
		cOption.setRequired(false);
		ops.addOption(cOption);
		
		/*
		 * Evaluation options
		 */
		Option mcOption = new Option("mc", "mag-complete", true, "Magnitude of Completeness for evaluation plots");
		mcOption.setRequired(false);
		ops.addOption(mcOption);
		
		return ops;
	}

	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			String argz = "";

//			argz += " --event-id ci38443183"; // 2019 Searles Valley M6.4
			argz += " --event-id ci38457511"; // 2019 Ridgecrest M7.1
			argz += " --num-simulations 100000";
//			argz += " --num-simulations 1000";
			argz += " --days-before 7";
//			argz += " --days-after 7";
//			argz += " --end-now";
//			argz += " --gridded-only";
//			argz += " --prob-model NO_ERT";
//			argz += " --include-spontaneous";
			argz += " --mag-complete 3.5";
//			argz += " --scale-factor 1.0";
//			argz += " --name-add CulledSurface";
//			argz += " --fault-model FM3_2";

			// from Morgan by e-mail 8/26/19 for Ridgcrest from Nic's ETAS GUI
//			argz += " --etas-k -3.03 --etas-p 1.15 --etas-c "+(float)+Math.pow(10, -3.33);
//			argz += " --mainshock-etas-k -2.3";
			// from Morgan by e-mail 8/26/19 for Ridgcrest from Nic's ETAS GUI with a modified c value
//			argz += " --etas-k -3.03 --etas-p 1.15 --etas-c 0.002";
//			argz += " --mainshock-etas-k -2.3";
			// from Morgan by e-mail 8/28/19 for Ridgcrest from her own ETAS code
//			argz += " --etas-k -2.2833 --etas-p 1.2434 --etas-c 0.0102";
			// from when I ran Nic's ETAS GUI
//			argz += " --etas-k -2.666 --etas-p 1.07133 --etas-c "+(float)+8.0307e-4;
//			argz += " --mainshock-etas-k -2.2412";
			
			// took these from first ComCat finite fault
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
			argz += " --finite-surf-shakemap-min-mag 5";
			argz += " --finite-surf-shakemap-version 7";
//			argz += " --finite-surf-shakemap-planar-extents";
//			argz += " --finite-surf-shakemap-min-mag 7";
			
//			argz += " --random-seed 123456789";
			
			// hpc options
			argz += " --hpc-site USC_HPC";
			argz += " --nodes 36";
			argz += " --hours 24";
//			argz += " --queue scec_hiprio";
			argz += " --queue scec";
			
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
			formatter.setWidth(150);
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
			
			int[] resetSubSects = null;
			if (cmd.hasOption("reset-sects")) {
				String sectsStr = cmd.getOptionValue("reset-sects");
				if (sectsStr.contains(",")) {
					String[] split = sectsStr.split(",");
					resetSubSects = new int[split.length];
					for (int i=0; i<split.length; i++)
						resetSubSects[i] = Integer.parseInt(split[i]);
				} else {
					resetSubSects = new int[] { Integer.parseInt(sectsStr) };
				}
				System.out.println("Resetting date of last event to time of primary event for section IDs: "
						+Joiner.on(",").join(Ints.asList(resetSubSects)));
			}
			boolean surfFromSections = cmd.hasOption("finite-surf-from-sections");
			Preconditions.checkState(!surfFromSections || resetSubSects != null,
					"Must supply --reset-sects option with --finite-surf-from-sections flag");
			
			boolean shakeMapSurfs = cmd.hasOption("finite-surf-shakemap");
			double shakeMapSurfMag = cmd.hasOption("finite-surf-shakemap-min-mag")
					? Double.parseDouble(cmd.getOptionValue("finite-surf-shakemap-min-mag"))
							: Double.POSITIVE_INFINITY;
			int shakeMapVersion = cmd.hasOption("finite-surf-shakemap-version")
					? Integer.parseInt(cmd.getOptionValue("finite-surf-shakemap-version")) : -1;
			boolean shakeMapPlanarExtents = cmd.hasOption("finite-surf-shakemap-planar-extents");
			ShakeMapFiniteFaultAccessor smAccessor = shakeMapSurfs ? new ShakeMapFiniteFaultAccessor() : null;
			
			boolean invSurfs = cmd.hasOption("finite-surf-inversion");
			double invSurfMag = cmd.hasOption("finite-surf-inversion-min-mag")
					? Double.parseDouble(cmd.getOptionValue("finite-surf-inversion-min-mag"))
							: Double.POSITIVE_INFINITY;
			double invSurfMinSlip = cmd.hasOption("finite-surf-inversion-min-slip")
					? Double.parseDouble(cmd.getOptionValue("finite-surf-inversion-min-slip"))
							: 0d;
			ComcatInvertedFiniteFaultAccessor invAccessor = invSurfs ? new ComcatInvertedFiniteFaultAccessor() : null;
			
			Location surfPoint = new Location(hypo.getLatitude(), hypo.getLongitude());
			SimpleFaultData sfd = null;
			if (cmd.hasOption("finite-surf-dip")) {
				System.out.println("Building finite simple rupture surface");
				Preconditions.checkState(!surfFromSections,
						"Can't specify both finite surface parameters and finite surface from sections");
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
				Preconditions.checkState(!cmd.hasOption("finite-surf-upper-depth"), "Must supply all or no finite surface options");
			}
			
			LocationList[] primaryFiniteFault = null;
			int numInvSurfs = 0;
			if (sfd == null && invSurfs) {
				System.out.println("Looking for inverted ComCat surface for "+eventID);
				primaryFiniteFault = invAccessor.fetchFiniteFault(eventID).getOutlines(invSurfMinSlip);
				Preconditions.checkNotNull(primaryFiniteFault, "No inverted surface found");
				numInvSurfs++;
			}
			
			int numShakeMapSurfs = 0;
			if (sfd == null && primaryFiniteFault == null && shakeMapSurfs) {
				System.out.println("Looking for ShakeMap surface for "+eventID);
				primaryFiniteFault = smAccessor.fetchShakemapSourceOutlines(eventID, shakeMapVersion);
				Preconditions.checkNotNull(primaryFiniteFault, "No ShakeMap surface found");
				if (shakeMapPlanarExtents)
					primaryFiniteFault = getPlanarExtentsSurface(primaryFiniteFault);
				numShakeMapSurfs++;
			}
			
			double minDepth = cmd.hasOption("min-depth") ? Double.parseDouble(cmd.getOptionValue("min-depth")) : -10;
			double maxDepth = cmd.hasOption("max-depth") ? Double.parseDouble(cmd.getOptionValue("max-depth"))
					: Double.max(ETAS_CubeDiscretizationParams.DEFAULT_MAX_DEPTH, 2*rup.getHypocenterLocation().getDepth());
			double minMag = cmd.hasOption("min-mag") ? Double.parseDouble(cmd.getOptionValue("min-mag")) : 2.5;
			
			double radius;
			if (cmd.hasOption("radius")) {
				radius = Double.parseDouble(cmd.getOptionValue("radius"));
			} else {
				radius = new WC1994_MagLengthRelationship().getMedianLength(rup.getMag());
				System.out.println("W-C 1994 Radius: "+(float)radius);
			}
			Region region;
			if (sfd != null) {
				region = new Region(sfd.getFaultTrace(), radius); // sausage around fault trace
			} else if (primaryFiniteFault != null) {
				// create combined trace. a little messy for complex ruptures, but won't
				// matter here where we're just building a buffered region around the rupture
				FaultTrace combTrace = new FaultTrace(null);
				for (LocationList locs : primaryFiniteFault)
					combTrace.addAll(locs);
				// this might fail for really complex ones
				try {
					region = new Region(combTrace, radius);
				} catch (Exception e) {
					// use simple trace through extents
					System.out.println("Failed to create sausage region around SM fault trace, "
							+ "using simple straight line trace");
//					List<Location[]> pairs = new ArrayList<>();
//					for (int i=0; i<primaryFiniteFault.length; i++) {
//						LocationList tr1 = primaryFiniteFault[i];
//						pairs.add(new Location[] { tr1.first(), tr1.last() });
//						for (int j=i+1; j<primaryFiniteFault.length; j++) {
//							LocationList tr2 = primaryFiniteFault[j];
//							pairs.add(new Location[] { tr1.first(), tr2.first() });
//							pairs.add(new Location[] { tr1.first(), tr2.last() });
//							pairs.add(new Location[] { tr1.last(), tr2.first() });
//							pairs.add(new Location[] { tr1.last(), tr2.last() });
//						}
//					}
//					Location[] furthestPair = null;
//					double furthestDist = 0d;
//					for (Location[] pair : pairs) {
//						double dist = LocationUtils.horzDistanceFast(pair[0], pair[1]);
//						if (dist > furthestDist) {
//							furthestDist = dist;
//							furthestPair = pair;
//						}
//					}
					
					LocationList planar = getPlanarExtentsSurface(primaryFiniteFault)[0];
					FaultTrace simpleTrace = new FaultTrace(null);
					simpleTrace.add(planar.get(0));
					simpleTrace.add(planar.get(1));
					region = new Region(simpleTrace, radius);
				}
			} else {
				region = new ETAS_Config.CircularRegion(surfPoint, radius);
			}
			
			ObsEqkRupList comcatEvents = new ObsEqkRupList();
			
			// circular region above will already be an instance
			ComcatRegion regionAdapter = region instanceof ComcatRegion ? (ComcatRegion)region : new ComcatRegionAdapter(region);
			double daysBefore = 0d;
			long comcatStartTime;
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
				comcatStartTime = beforeStartMillis;
			} else {
				comcatStartTime = rup.getOriginTime();
			}
			
			double daysAfter = 0d;
			long simulationStartTime;
			long comcatEndTime;
			if (cmd.hasOption("days-after") || cmd.hasOption("hours-after") || cmd.hasOption("end-now")) {
				long afterEndMillis;
				long curTime = System.currentTimeMillis();
				if (cmd.hasOption("end-now")) {
					afterEndMillis = curTime;
					daysAfter = (double)(afterEndMillis - rup.getOriginTime()) / ProbabilityModelsCalc.MILLISEC_PER_DAY;
				} else {
					daysAfter = cmd.hasOption("days-after") ? Double.parseDouble(cmd.getOptionValue("days-after"))
							: 24d * Double.parseDouble(cmd.getOptionValue("hours-after"));
					Preconditions.checkState(daysAfter > 0, "Days after event must be >0");
					afterEndMillis = rup.getOriginTime() + (long)(daysAfter * (double)ProbabilityModelsCalc.MILLISEC_PER_DAY + 0.5);
					Preconditions.checkState(afterEndMillis <= curTime, "ComCat end time is %s in the future: %s > %s",
							ETAS_AbstractPlot.getTimeShortLabel((double)(afterEndMillis - curTime)/ ProbabilityModelsCalc.MILLISEC_PER_YEAR),
							afterEndMillis, curTime);
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
				comcatEndTime = afterEndMillis;
			} else {
				simulationStartTime = rup.getOriginTime() + 1000l;
				comcatEndTime = rup.getOriginTime()+1l;
			}
			comcatEvents.add(rup);
			comcatEvents.sortByOriginTime();
			
			long lastInputTime = Long.MIN_VALUE;
			List<TriggerRupture> triggerRuptures = new ArrayList<>();
			
			Double ms_log10_k = doubleArgIfPresent(cmd, "mainshock-etas-k");
			Double ms_p = doubleArgIfPresent(cmd, "mainshock-etas-p");
			Double ms_c = doubleArgIfPresent(cmd, "mainshock-etas-c");
			
			HashSet<String> prevIDs = new HashSet<>();
			for (ObsEqkRupture eq : comcatEvents) {
				Preconditions.checkState(!prevIDs.contains(eq.getEventId()), "Duplicate event found: %s", eventID);
				prevIDs.add(eq.getEventId());
				TriggerRupture triggerRup = null;
				if (eq == rup) {
					// this is the primary rupture
					if (surfFromSections) {
						triggerRup = new TriggerRupture.SectionBased(resetSubSects, eq.getOriginTime(), eq.getMag());
					} else if (sfd != null) {
						triggerRup = new TriggerRupture.SimpleFault(eq.getOriginTime(), eq.getHypocenterLocation(),
								eq.getMag(), resetSubSects, sfd);
					} else if (primaryFiniteFault != null) {
						triggerRup = new TriggerRupture.EdgeFault(eq.getOriginTime(), eq.getHypocenterLocation(),
								eq.getMag(), resetSubSects, primaryFiniteFault);
					}
					
					if (triggerRup == null)
						triggerRup = new TriggerRupture.Point(eq.getHypocenterLocation(), eq.getOriginTime(),
								eq.getMag(), resetSubSects);
					System.out.println("Primary rupture type: "+ClassUtils.getClassNameWithoutPackage(triggerRup.getClass()));
					if (ms_log10_k != null || ms_p != null || ms_c != null) {
						System.out.println("Setting custom mainshock ETAS parameters");
						triggerRup.setETAS_Params(ms_log10_k, ms_p, ms_c);
					}
				} else {
					if (invSurfs && eq.getMag() >= invSurfMag) {
						// look for inverted surface
						System.out.println("Looking for inverted surface for M"+(float)eq.getMag()+", "+eq.getEventId());
						ComcatInvertedFiniteFault finiteSurf = invAccessor.fetchFiniteFault(eq.getEventId());
						if (finiteSurf != null) {
							LocationList[] outlines = finiteSurf.getOutlines(invSurfMinSlip);
							System.out.println("\tFound surface!");
							triggerRup = new TriggerRupture.EdgeFault(eq.getOriginTime(),
									eq.getHypocenterLocation(), eq.getMag(), outlines);
							numInvSurfs++;
						} else {
							System.out.println("\tNo surface found, defaulting to point source");
						}
					}
					if (triggerRup == null && shakeMapSurfs && eq.getMag() >= shakeMapSurfMag) {
						// look for shakemap surface
						System.out.println("Looking for ShakeMap surface for M"+(float)eq.getMag()+", "+eq.getEventId());
						LocationList[] outlines = smAccessor.fetchShakemapSourceOutlines(eq.getEventId());
						if (outlines != null) {
							System.out.println("\tFound surface!");
							if (shakeMapPlanarExtents)
								outlines = getPlanarExtentsSurface(outlines);
							triggerRup = new TriggerRupture.EdgeFault(eq.getOriginTime(),
									eq.getHypocenterLocation(), eq.getMag(), outlines);
							numShakeMapSurfs++;
						} else {
							System.out.println("\tNo surface found, defaulting to point source");
						}
					}
					if (triggerRup == null)
						triggerRup = new TriggerRupture.Point(eq.getHypocenterLocation(), eq.getOriginTime(), eq.getMag());
				}
				Preconditions.checkNotNull(triggerRup, "Trigger rup is null");
				triggerRup.setComcatEventID(eq.getEventId());
				triggerRuptures.add(triggerRup);
				lastInputTime = Long.max(lastInputTime, eq.getOriginTime());
			}
			Preconditions.checkState(lastInputTime < simulationStartTime,
					"Last input event time (%s) is after simulation start time (%s)? ", lastInputTime, simulationStartTime);
			
			String name;
			if (cmd.hasOption("name")) {
				Preconditions.checkArgument(!cmd.hasOption("name-add"), "Can't supply both --name and --name-add");
				name = cmd.getOptionValue("name");
			} else {
				name = "ComCat M"+(float)rup.getMag()+" ("+eventID+")";
				if (daysAfter > 0)
					name += ", "+daysDF.format(daysAfter)+" Days After";
				if (ms_log10_k != null || ms_p != null || ms_c != null) {
					name += ", Mainshock ";
					List<String> msVals = new ArrayList<>();
					if (ms_log10_k != null)
						msVals.add("Log10(k)="+ms_log10_k.floatValue());
					if (ms_p != null)
						msVals.add("p="+ms_p.floatValue());
					if (ms_c != null)
						msVals.add("c="+ms_c.floatValue());
					name += Joiner.on(",").join(msVals);
				}
				if (sfd != null)
					name += ", Finite Surface";
				if (numInvSurfs > 0) {
					name += ", Inverted Surface";
					if (numInvSurfs > 1)
						name += "s";
					if (invSurfMinSlip > 0)
						name += " (minSlip="+(float)invSurfMinSlip+")";
				}
				if (numShakeMapSurfs > 0) {
					name += ", ShakeMap Surface";
					if (numShakeMapSurfs > 1)
						name += "s";
					if (shakeMapVersion >= 0)
						name += " (Version "+shakeMapVersion+")";
					if (shakeMapPlanarExtents)
						name += " (Planar Extents)";
				}
				if (sfd == null && numInvSurfs == 0 && numShakeMapSurfs == 0) {
					name += ", Point Source";
					if (triggerRuptures.size() > 1)
						name += "s";
				}
				List<String> nonDefaultOptions = getNonDefaultOptionsStrings(cmd);
				if (nonDefaultOptions != null && !nonDefaultOptions.isEmpty())
					name += ", "+Joiner.on(", ").join(nonDefaultOptions);
				if (cmd.hasOption("name-add")) {
					String nameAdd = cmd.getOptionValue("name-add");
					if (!nameAdd.startsWith(","))
						nameAdd = ", "+nameAdd;
					name += nameAdd;
				}
			}
			
			String configCommand = "u3etas_comcat_event_config_builder.sh "+Joiner.on(" ").join(args);
			
			ETAS_Config config = buildBasicConfig(cmd, name, triggerRuptures, configCommand);
			config.setStartTimeMillis(simulationStartTime);
			ComcatMetadata meta = new ComcatMetadata(region, eventID, minDepth, maxDepth, minMag, comcatStartTime, comcatEndTime);
			if (cmd.hasOption("mag-complete"))
				meta.magComplete = Double.parseDouble(cmd.getOptionValue("mag-complete"));
			config.setComcatMetadata(meta);
			
			File outputDir = config.getOutputDir();
			System.out.println("Output dir: "+outputDir.getPath());
			File relativeConfigFile = new File(outputDir, "config.json");
			File configFile = ETAS_Config.resolvePath(relativeConfigFile);
			File resolved = ETAS_Config.resolvePath(outputDir);
			if (!resolved.equals(outputDir))
				System.out.println("Resolved output dir: "+resolved.getAbsolutePath());
			outputDir = resolved;
			Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
//			File configFile = new File(outputDir, "config.json");
			if (configFile.exists())
				System.err.println("WARNING: overwriting previous configuration file");
			config.writeJSON(configFile);
			
			checkWriteHPC(config, relativeConfigFile, cmd);
			
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
			
			// now look for primary rupture surface
			RuptureSurface primarySurface = null;
			for (ETAS_EqkRupture trigger : launcher.getTriggerRuptures()) {
				if (trigger.getOriginTime() == rup.getOriginTime() && trigger.getMag() == rup.getMag()) {
					primarySurface = trigger.getRuptureSurface();
					break;
				}
			}
			if (primarySurface != null && !config.isGriddedOnly()) {
				double faultBuffer = InversionTargetMFDs.FAULT_BUFFER;
				double minFractionalAreaInPolygon = 0.5;
				FiniteFaultSectionResetCalc mapper = new FiniteFaultSectionResetCalc(fss.getRupSet(), minFractionalAreaInPolygon,
						faultBuffer, true);
				
				lines.add("");
				lines.addAll(mapper.writeSectionResetMarkdown(plotDir, ".", "##", topLink, primarySurface,
						rup.getHypocenterLocation()));
			}
			
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
	
	private static LocationList[] getPlanarExtentsSurface(LocationList[] rawSurf) {
		MinMaxAveTracker latTrack = new MinMaxAveTracker();
		MinMaxAveTracker lonTrack = new MinMaxAveTracker();
		MinMaxAveTracker depthTrack = new MinMaxAveTracker();
		
		for (LocationList surf : rawSurf) {
			for (Location loc : surf) {
				latTrack.addValue(loc.getLatitude());
				lonTrack.addValue(loc.getLongitude());
				depthTrack.addValue(loc.getDepth());
			}
		}
		
		Location bottomLeft = new Location(latTrack.getMin(), lonTrack.getMin());
		Location bottomRight = new Location(latTrack.getMin(), lonTrack.getMax());
		
		// figure out if it should go from bottom left to top right or bottom right to top left
		double distFromBottomLeft = 0d;
		double distFromBottomRight = 0d;
		for (LocationList surf : rawSurf) {
			for (Location loc : surf) {
				distFromBottomLeft += LocationUtils.horzDistanceFast(loc, bottomLeft);
				distFromBottomRight += LocationUtils.horzDistanceFast(loc, bottomRight);
			}
		}
		
		LocationList extents = new LocationList();
		
		double startLat = latTrack.getMin();
		double endLat = latTrack.getMax();
		double startLon, endLon;
		if (distFromBottomLeft < distFromBottomRight) {
			startLon = lonTrack.getMin();
			endLon = lonTrack.getMax();
		} else {
			startLon = lonTrack.getMax();
			endLon = lonTrack.getMin();
		}
		double startDepth = depthTrack.getMin();
		double endDepth = depthTrack.getMax();
		extents.add(new Location(startLat, startLon, startDepth));
		extents.add(new Location(endLat, endLon, startDepth));
		extents.add(new Location(endLat, endLon, endDepth));
		extents.add(new Location(startLat, startLon, endDepth));
		extents.add(new Location(startLat, startLon, startDepth));
		
		return new LocationList[] { extents };
	}
	
	private static final DecimalFormat daysDF = new DecimalFormat("0.#");

}
