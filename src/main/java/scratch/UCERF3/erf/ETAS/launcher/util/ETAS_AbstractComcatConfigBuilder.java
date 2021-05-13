package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
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
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.DataUtils.MinMaxAveTracker;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CubeDiscretizationParams;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_TriggerRuptureFaultDistancesPlot;
import scratch.UCERF3.erf.ETAS.analysis.SimulationMarkdownGenerator;
import scratch.UCERF3.erf.ETAS.association.FiniteFaultSectionResetCalc;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.ComcatMetadata;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_AbstractComcatConfigBuilder.RuptureBuilder;
import scratch.UCERF3.inversion.InversionTargetMFDs;

public abstract class ETAS_AbstractComcatConfigBuilder extends ETAS_ConfigBuilder {
	
	protected static double SHAKEMAP_MIN_MAG_DEFAULT = 5d;
	protected static double INVERSION_MIN_MAG_DEFAULT = 5d;
	
	protected long parseDateString(String dateStr) {
		try {
			if (dateStr.contains("T"))
				return argDateTimeFormat.parse(dateStr).getTime();
			return argDateFormat.parse(dateStr).getTime();
		} catch (java.text.ParseException e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
	}
	
	protected static String historicalEndDate = "2012-04-24T19:44:19";
	protected static SimpleDateFormat argDateFormat = new SimpleDateFormat("yyyy-MM-dd");
	protected static SimpleDateFormat argDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	static {
		TimeZone utc = TimeZone.getTimeZone("UTC");
		argDateFormat.setTimeZone(utc);
		argDateTimeFormat.setTimeZone(utc);
	}

	public static Options getCommonOptions() {
		Options ops = ETAS_ConfigBuilder.getCommonOptions();
		
		/*
		 * ComCat fetch options
		 */
		Option minDepthOption = new Option("mind", "min-depth", true, "Min depth (km) considered for ComCat events (default: -10)");
		minDepthOption.setRequired(false);
		ops.addOption(minDepthOption);
		
		Option maxDepthOption = new Option("maxd", "max-depth", true, "Max depth (km) considered for ComCat events (default: is "
				+ (float)ETAS_CubeDiscretizationParams.DEFAULT_MAX_DEPTH+" km, or in event mode the larger of "
				+ (float)ETAS_CubeDiscretizationParams.DEFAULT_MAX_DEPTH+" km and twice the hypocentral depth of the primary event)");
		maxDepthOption.setRequired(false);
		ops.addOption(maxDepthOption);
		
		Option minMagOption = new Option("minm", "min-mag", true, "Minimum magnitude event from ComCat (default: 2.5)");
		minMagOption.setRequired(false);
		ops.addOption(minMagOption);
		
		Option comcatFileOption = new Option("ccfile", "comcat-file", true, "Comcat file to use instead of accessing ComCat directly");
		comcatFileOption.setRequired(false);
		ops.addOption(comcatFileOption);
		
		/*
		 * finite surface from ShakeMap options
		 */
		Option finiteSurfShakeMap = new Option("fssm", "finite-surf-shakemap", false,
				"Fetch finite surfaces from ShakeMap when available. In event mode, applies only to the specified event by default. "
				+ "In region/swarm mode, applies to all events with M>="+(float)SHAKEMAP_MIN_MAG_DEFAULT+". Supply "
				+ "--finite-surf-shakemap-min-mag to override this behavior and specify a minimum magnitude above which to always "
				+ "look for shakemap surfaces.");
		finiteSurfShakeMap.setRequired(false);
		ops.addOption(finiteSurfShakeMap);
		
		Option finiteSurfShakeMapPlanar = new Option("fssmp", "finite-surf-shakemap-planar-extents", false,
				"Flag to build a planar surface based off of the extents (min & max lat, lon, depth) instead of using the actual surface");
		finiteSurfShakeMapPlanar.setRequired(false);
		ops.addOption(finiteSurfShakeMapPlanar);
		
		Option finiteSurfShakeMapMag = new Option("fssmmm", "finite-surf-shakemap-min-mag", true,
				"Minimum magnitude above which to search for shakemap finite surfaces. Must also supply --finite-surf-shakemap option");
		finiteSurfShakeMapMag.setRequired(false);
		ops.addOption(finiteSurfShakeMapMag);
		
		Option finiteSurfShakeMapVersion = new Option("fssmv", "finite-surf-shakemap-version", true,
				"ShakeMap source Version number to use for individual events with the format --finite-surf-shakemap-version "
				+ " <event-id>:<version>, e.g. --finite-surf-shakemap-version ci38457511:10. In event mode, you can omit the "
				+ "event ID to override the version of the mainshock. You can also chain multiple arguments as "
				+ "--finite-surf-shakemap-version <event1>:<version> --finite-surf-shakemap-version <event2>:<version>. "
				+ "Must also supply --finite-surf-shakemap option");
		finiteSurfShakeMapVersion.setRequired(false);
		ops.addOption(finiteSurfShakeMapVersion);
		
		/*
		 * inverted finite surface from ComCat options
		 */
		Option finiteSurfInversion = new Option("fsi", "finite-surf-inversion", false,
				"Fetch inverted finite surfaces from ComCat when available. In event mode, applies only to the specified event by default. "
				+ "In region/swarm mode, applies to all events with M>="+(float)INVERSION_MIN_MAG_DEFAULT+". Supply "
				+ "--finite-surf-inversion-min-mag to override this behavior and specify a minimum magnitude above which to always "
				+ "look for inverted surfaces. ");
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
		 * Event-specific ETAS params (all others will be applied to all other ComCat events
		 */
		Option kOption = new Option("mek", "event-etas-k", true, "Mainshock-specific ETAS productivity parameter parameter,"
				+ " k, in Log10 units, with the format --event-etas-k <event-id>:<value>. In event mode, you can omit the "
				+ "'<event-id>:' to specify a value for the primary event.");
		kOption.setRequired(false);
		ops.addOption(kOption);
		
		Option pOption = new Option("mep", "event-etas-p", true, "Mainshock-specific ETAS temporal decay paramter, p, "
				+ "with the format --event-etas-p <event-id>:<value>. In event mode, you can omit the "
				+ "'<event-id>:' to specify a value for the primary event.");
		pOption.setRequired(false);
		ops.addOption(pOption);
		
		Option cOption = new Option("mec", "event-etas-c", true, "Mainshock-specific ETAS minimum time paramter, c, "
				+ "with the format --event-etas-c <event-id>:<value>. In event mode, you can omit the "
				+ "'<event-id>:' to specify a value for the primary event.");
		cOption.setRequired(false);
		ops.addOption(cOption);
		
		Option modMagOption = new Option("mm", "mod-mag", true, "Modify the magnitude of a specific event with the format "
				+ "--mod-mag <event-id>:<mag>, e.g. --mod-mag ci38443183:6.48. In event mode, you can omit the event ID to "
				+ "override the magnitude of the mainshock. You can also chain multiple arguments as --mod-mag <arg1> "
				+ "--mod-mag <arg2>");
		modMagOption.setRequired(false);
		ops.addOption(modMagOption);
		
		Option finiteResetSects = new Option("rs", "reset-sects", true, "UCERF3 sub-section indices to reset (elastic rebound) "
				+ "with the format --reset-sects <event-id>:<sect1>,<sect2>,<sectN>. In event mode, you can omit the "
				+ "'<event-id>:' to specify a value for the primary event.");
		finiteResetSects.setRequired(false);
		ops.addOption(finiteResetSects);
		
		Option finiteSurfFromSects = new Option("fsfs", "finite-surf-from-sections", false, "If supplied, finite rupture surface will "
				+ "be constructed from the fault sections specified with --reset-sections");
		finiteSurfFromSects.setRequired(false);
		ops.addOption(finiteSurfFromSects);
		
		/*
		 * Evaluation options
		 */
		Option mcOption = new Option("mc", "mag-complete", true, "Magnitude of Completeness for evaluation plots");
		mcOption.setRequired(false);
		ops.addOption(mcOption);
		
		Option skipPlotsOption = new Option("skpl", "skip-input-plots", false, "Flag to skip input plots");
		skipPlotsOption.setRequired(false);
		ops.addOption(skipPlotsOption);
		
		return ops;
	}
	
	protected static interface RuptureBuilder {
		
		public TriggerRupture build(ObsEqkRupture rup, int[] resetSubSects);
		
		public String getDisplayName(int totalNumRuptures);
		
	}
	
	protected static class ShakeMapRuptureBuilder implements RuptureBuilder {
		
		private ShakeMapFiniteFaultAccessor smAccessor;
		private Map<String, Integer> versions = new HashMap<>();
		private double minMag;
		private String primaryEventID;
		private boolean planarExtents;
		private int numProcessed = 0;
		
		public ShakeMapRuptureBuilder(double minMag, String primaryEventID) {
			this.minMag = minMag;
			this.primaryEventID = primaryEventID;
			smAccessor = new ShakeMapFiniteFaultAccessor();
		}
		
		public void setShakeMapVerison(String eventID, int version) {
			versions.put(eventID, version);
		}
		
		public void setPlanarExtents(boolean planarExtents) {
			this.planarExtents = planarExtents;
		}

		@Override
		public TriggerRupture build(ObsEqkRupture rup, int[] resetSubSects) {
			if (rup.getMag() < minMag && !rup.getEventId().equals(primaryEventID))
				return null;
			Integer version = versions.get(rup.getEventId());
			if (version == null) {
				version = -1;
				System.out.println("Looking for ShakeMaps for "+rup.getEventId()+", M"+(float)rup.getMag());
			} else {
				System.out.println("Looking for ShakeMap version "+version+" for "+rup.getEventId()+", M"+(float)rup.getMag());
			}
			LocationList[] outlines;
			try {
				outlines = smAccessor.fetchShakemapSourceOutlines(rup.getEventId(), version);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
			if (outlines == null)
				return null;
			numProcessed++;
			if (planarExtents)
				outlines = getPlanarExtentsSurface(outlines);
			return new TriggerRupture.EdgeFault(rup.getOriginTime(), rup.getHypocenterLocation(),
					rup.getMag(), resetSubSects, outlines);
		}

		@Override
		public String getDisplayName(int totalNumRuptures) {
			if (numProcessed == 0)
				return null;
			String name = "ShakeMap Surface";
			if (numProcessed > 1)
				name += "s";
			if (!versions.isEmpty()) {
				if (versions.size() == 1) {
					name += " (Version "+versions.values().iterator().next()+")";
				} else {
					String versionStr = null;
					for (String key : versions.keySet()) {
						if (versionStr == null)
							versionStr = " (Version ";
						else
							versionStr += ",";
						versionStr += key+":"+versions.get(key);
					}
					name += versionStr+")";
				}
			}
			if (planarExtents)
				name += " (Planar Extents)";
			return name;
		}
		
	}
	
	protected static LocationList[] getPlanarExtentsSurface(LocationList[] rawSurf) {
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
	
	protected static class InvertedSurfRuptureBuilder implements RuptureBuilder {
		
		private ComcatInvertedFiniteFaultAccessor invAccessor;
		private double minMag;
		private double minSlip;
		private String primaryEventID;
		private int numProcessed = 0;
		
		public InvertedSurfRuptureBuilder(double minMag, double minSlip, String primaryEventID) {
			this.minMag = minMag;
			this.minSlip = minSlip;
			this.primaryEventID = primaryEventID;
			invAccessor = new ComcatInvertedFiniteFaultAccessor();
		}

		@Override
		public TriggerRupture build(ObsEqkRupture rup, int[] resetSubSects) {
			if (rup.getMag() < minMag && !rup.getEventId().equals(primaryEventID))
				return null;
			System.out.println("Looking for inverted surfaces for "+rup.getEventId()+", M"+(float)rup.getMag());
			ComcatInvertedFiniteFault finiteSurf = invAccessor.fetchFiniteFault(rup.getEventId());
			if (finiteSurf == null)
				return null;
			numProcessed++;
			LocationList[] outlines = finiteSurf.getOutlines(minSlip);
			return new TriggerRupture.EdgeFault(rup.getOriginTime(), rup.getHypocenterLocation(),
					rup.getMag(), resetSubSects, outlines);
		}

		@Override
		public String getDisplayName(int totalNumRuptures) {
			if (numProcessed == 0)
				return null;
			String name = "Inverted Surface";
			if (numProcessed > 1)
				name += "s";
			if (minSlip > 0)
				name += " (minSlip="+(float)minSlip+")";
			return name;
		}
		
	}
	
	protected static class SectionRuptureBuilder implements RuptureBuilder {
		
		private Map<String, int[]> resetSectsMap;
		private int numProcessed = 0;

		public SectionRuptureBuilder(Map<String, int[]> resetSectsMap) {
			this.resetSectsMap = resetSectsMap;
		}

		@Override
		public TriggerRupture build(ObsEqkRupture rup, int[] resetSubSects) {
			if (resetSectsMap.containsKey(rup.getEventId()))
				return null;
			numProcessed++;
			return new TriggerRupture.SectionBased(resetSectsMap.get(rup.getEventId()), rup.getOriginTime(), rup.getMag());
		}

		@Override
		public String getDisplayName(int totalNumRuptures) {
			if (numProcessed == 0)
				return null;
			String name = "Surf";
			if (numProcessed > 1)
				name += "s";
			name += " From Sects";
			return name;
		}
		
	}
	
	protected static class PointRuptureBuilder implements RuptureBuilder {
		
		private int numProcessed = 0;
		
		public PointRuptureBuilder() {}

		@Override
		public TriggerRupture build(ObsEqkRupture rup, int[] resetSubSects) {
			numProcessed++;
			return new TriggerRupture.Point(rup.getHypocenterLocation(), rup.getOriginTime(),
					rup.getMag(), resetSubSects);
		}

		@Override
		public String getDisplayName(int totalNumRuptures) {
			if (numProcessed < totalNumRuptures)
				return null;
			String name = "Point Source";
			if (numProcessed > 1)
				name += "s";
			return name;
		}
		
	}
	
	protected String[] args;
	protected CommandLine cmd;
	
	protected ComcatAccessor accessor;
	private String primaryEventID;
	protected ObsEqkRupture primaryRupture;
	protected Double minMag;
	private Region region;
	private List<RuptureBuilder> ruptureBuilders;
	
	private Map<String, int[]> resetSectsMap;
	
	private ObsEqkRupList comcatEvents;
	private File configFile;
	protected ETAS_Config config;
	private File outputDir;
	
	public ETAS_AbstractComcatConfigBuilder(String[] args, Options options) {
		this.args = args;
		CommandLineParser parser = new DefaultParser();
		
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			e.printStackTrace();
			HelpFormatter formatter = new HelpFormatter();
			formatter.setWidth(150);
			formatter.printHelp(ClassUtils.getClassNameWithoutPackage(this.getClass()),
					options, true );
			System.exit(2);
			return;
		}
		
		accessor = new ComcatAccessor();
	}
	
	private Map<String, int[]> getResetSectsMap() {
		if (resetSectsMap != null && cmd.hasOption("reset-sects")) {
			Map<String, String> resetSectsStrMap = getEventSpecificOptions(cmd, "reset-sects", primaryEventID);
			resetSectsMap = new HashMap<>();
			for (String key : resetSectsStrMap.keySet()) {
				String val = resetSectsStrMap.get(key);
				if (val.contains(",")) {
					String[] split = val.split(",");
					int[] ids = new int[split.length];
					for (int i=0; i<ids.length; i++)
						ids[i] = Integer.parseInt(split[i]);
					resetSectsMap.put(key, ids);
				} else {
					resetSectsMap.put(key, new int[] {Integer.parseInt(val) });
				}
			}
		}
		return resetSectsMap;
	}
	
	public void buildConfiguration() throws IOException {
		primaryEventID = getPrimaryEventID();
		if (primaryEventID != null) {
			// build primary rupture
			primaryRupture = accessor.fetchEvent(primaryEventID, false);
			Location hypo = primaryRupture.getHypocenterLocation();
			System.out.println("Found event "+primaryEventID+" with M="+(float)primaryRupture.getMag()
				+" and Hypocenter=["+hypo+"] at "+primaryRupture.getOriginTime());
		}
		
		initRuptureBuilders(cmd, primaryEventID);
		
		Map<String, Double> modMags = getEventSpecificDoubleOptions(cmd, "mod-mag", primaryEventID);
		Map<String, Double> modK = getEventSpecificDoubleOptions(cmd, "event-etas-k", primaryEventID);
		Map<String, Double> modP = getEventSpecificDoubleOptions(cmd, "event-etas-p", primaryEventID);
		Map<String, Double> modC = getEventSpecificDoubleOptions(cmd, "event-etas-c", primaryEventID);
		Map<String, int[]> resetSectsMap = getResetSectsMap();
		
		TriggerRupture primaryTrigger = null;
		if (primaryRupture != null) {
			// build primary rupture now as we might need its extents for the region
			primaryTrigger = buildRupture(modMags, modK, modP, modC, resetSectsMap, primaryRupture);
		}
		
		region = buildRegion(primaryRupture, primaryTrigger);
		
		minMag = doubleArgIfPresent(cmd, "min-mag");
		if (minMag == null)
			minMag = 2.5;
		else
			Preconditions.checkArgument(minMag >= 2.5, "Cannot include ruptures below M=2.5");
		Double minDepth = doubleArgIfPresent(cmd, "min-depth");
		if (minDepth == null)
			minDepth = -10d;
		Double maxDepth = doubleArgIfPresent(cmd, "max-depth");
		if (maxDepth == null) {
			maxDepth = ETAS_CubeDiscretizationParams.DEFAULT_MAX_DEPTH;
			if (primaryRupture != null)
				maxDepth = Math.max(maxDepth, 2*primaryRupture.getHypocenterLocation().getDepth());
		}
		
		long comcatStartTime = getComCatStartTime();
		long comcatEndTime = getComCatEndTime();
		
		Preconditions.checkState(comcatStartTime <= comcatEndTime,
				"ComCat start time must be before (or equal to) end time: start=%s, end=%s", comcatStartTime, comcatEndTime);
		
		if (comcatStartTime == comcatEndTime) {
			Preconditions.checkState(primaryRupture != null, "ComCat start and end times are identical (%s), but no primary "
					+ "rupture supplied!", comcatStartTime);
			comcatEvents = new ObsEqkRupList();
		} else if (cmd.hasOption("comcat-file")) {
			ObsEqkRupList allComcatEvents;
			try {
				allComcatEvents = ETAS_ComcatEventFetcher.loadCatalogFile(ETAS_Config.resolvePath(cmd.getOptionValue("comcat-file")));
			} catch (Exception e) {
				if (e instanceof IOException)
					throw (IOException)e;
				throw ExceptionUtils.asRuntimeException(e);
			}
			comcatEvents = new ObsEqkRupList();
			for (ObsEqkRupture rup : allComcatEvents) {
				if (rup.getOriginTime() < comcatStartTime)
					continue;
				else if (rup.getOriginTime() > comcatEndTime)
					continue;
				else
					comcatEvents.add(rup);
			}
			System.out.println("Retained "+comcatEvents.size()+" of "+allComcatEvents.size()+" events from input file");
		} else {
			ComcatRegion cReg = region instanceof ComcatRegion ? (ComcatRegion)region : new ComcatRegionAdapter(region);
			comcatEvents = accessor.fetchEventList(primaryEventID, comcatStartTime, comcatEndTime, minDepth, maxDepth, cReg,
					false, false, minMag);
			System.out.println("Fetched "+comcatEvents.size()+" events from ComCat");
		}
		
		if (primaryRupture != null)
			comcatEvents.add(primaryRupture);
		comcatEvents.sortByOriginTime();
		
		if (comcatEvents.size() == 1) {
			ObsEqkRupture event = comcatEvents.get(0);
			System.out.println("\tSingle event: "+event.getEventId()+", M"+(float)event.getMag());
		} else if (comcatEvents.size() > 1) {
			ObsEqkRupture first = comcatEvents.get(0);
			ObsEqkRupture last = comcatEvents.get(comcatEvents.size()-1);
			ObsEqkRupture largest = first;
			for (ObsEqkRupture rup : comcatEvents)
				if (rup.getMag() > largest.getMag())
					largest = rup;
			System.out.println("\tFirst event: "+first.getEventId()+", M"+(float)first.getMag());
			System.out.println("\tLast event: "+last.getEventId()+", M"+(float)last.getMag());
			System.out.println("\tLargest event: "+largest.getEventId()+", M"+(float)largest.getMag());
		} else {
			System.out.println("\tNo Events Found");
		}
		
		System.out.println("Building trigger ruptures for "+comcatEvents.size()+" events");
		
		long lastInputTime = Long.MIN_VALUE;
		List<TriggerRupture> triggerRuptures = new ArrayList<>();
		
		HashSet<String> prevIDs = new HashSet<>();
		for (ObsEqkRupture eq : comcatEvents) {
			Preconditions.checkState(!prevIDs.contains(eq.getEventId()), "Duplicate event found: %s", eq.getEventId());
			prevIDs.add(eq.getEventId());
			TriggerRupture triggerRup = eq.getEventId().equals(primaryEventID) ? primaryTrigger :
				buildRupture(modMags, modK, modP, modC, resetSectsMap, eq);
			triggerRuptures.add(triggerRup);
			lastInputTime = Long.max(lastInputTime, eq.getOriginTime());
		}
		long simulationStartTime = getSimulationStartTime(comcatStartTime, comcatEndTime);
		Preconditions.checkState(lastInputTime < simulationStartTime,
				"Last input event time (%s) is after simulation start time (%s)? ", lastInputTime, simulationStartTime);
		
		String name;
		if (cmd.hasOption("name")) {
			Preconditions.checkArgument(!cmd.hasOption("name-add"), "Can't supply both --name and --name-add");
			name = cmd.getOptionValue("name");
		} else {
			name = getBaseSimName();
			HashSet<String> modParamIDs = new HashSet<>();
			if (modK != null)
				modParamIDs.addAll(modK.keySet());
			if (modP != null)
				modParamIDs.addAll(modP.keySet());
			if (modC != null)
				modParamIDs.addAll(modC.keySet());
			if (!modParamIDs.isEmpty()) {
				if (modParamIDs.size() == 1 || modParamIDs.contains(primaryEventID)) {
					String id;
					if (modParamIDs.contains(primaryEventID)) {
						id = primaryEventID;
						name += ", Mainshock ";
					} else {
						id = modParamIDs.iterator().next();
						name += ", Mod "+id+" ";
					}
					List<String> msVals = new ArrayList<>();
					Double ms_log10_k = modK != null ? modK.get(id) : null;
					Double ms_p = modP != null ? modP.get(id) : null;
					Double ms_c = modC != null ? modC.get(id) : null;
					if (ms_log10_k != null)
						msVals.add("Log10(k)="+ms_log10_k.floatValue());
					if (ms_p != null)
						msVals.add("p="+ms_p.floatValue());
					if (ms_c != null)
						msVals.add("c="+ms_c.floatValue());
					name += Joiner.on(",").join(msVals);
				} else {
					name += ", "+modParamIDs.size()+" Events Mod Params";
				}
			}
			for (RuptureBuilder builder : ruptureBuilders) {
				String str = builder.getDisplayName(triggerRuptures.size());
				if (str != null)
					name += ", "+str;
			}
			if (modMags != null && !modMags.isEmpty()) {
				if (modMags.size() == 1 && modMags.keySet().iterator().next().equals(primaryEventID)) {
					name += ", Mod Mag M"+modMags.get(primaryEventID).floatValue();
				} else {
					boolean first = true;
					name += ", Mod Mag ";
					for (String id : modMags.keySet()) {
						if (first)
							first = false;
						else
							name += ",";
						name += id+"=M"+modMags.get(id).floatValue();
					}
				}
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
		
		String configCommand = getScriptName()+" "+Joiner.on(" ").join(args);
		
		config = buildBasicConfig(cmd, name, triggerRuptures, configCommand);
		config.setStartTimeMillis(simulationStartTime);
		ComcatMetadata meta = new ComcatMetadata(region, primaryEventID, minDepth, maxDepth, minMag, comcatStartTime, comcatEndTime);
		if (cmd.hasOption("mag-complete"))
			meta.magComplete = Double.parseDouble(cmd.getOptionValue("mag-complete"));
		config.setComcatMetadata(meta);
		
		outputDir = config.getOutputDir();
		System.out.println("Output dir: "+outputDir.getPath());
		File relativeConfigFile = new File(outputDir, "config.json");
		configFile = ETAS_Config.resolvePath(relativeConfigFile);
		File resolved = ETAS_Config.resolvePath(outputDir);
		if (!resolved.equals(outputDir))
			System.out.println("Resolved output dir: "+resolved.getAbsolutePath());
		outputDir = resolved;
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir(),
				"Output directory does not exist and cannot be created: %s", outputDir.getAbsolutePath());
//		File configFile = new File(outputDir, "config.json");
		if (configFile.exists())
			System.err.println("WARNING: overwriting previous configuration file");
		config.writeJSON(configFile);
		
		checkWriteHPC(config, relativeConfigFile, cmd);
	}
	
	public static boolean CACHE_TRIGGER_RUPS = false;
	private static HashMap<String, TriggerRupture> triggerRupCache = new HashMap<>();

	TriggerRupture buildRupture(Map<String, Double> modMags, Map<String, Double> modK, Map<String, Double> modP,
			Map<String, Double> modC, Map<String, int[]> resetSectsMap, ObsEqkRupture eq) {
		if (CACHE_TRIGGER_RUPS && triggerRupCache.containsKey(eq.getEventId()))
			return triggerRupCache.get(eq.getEventId());
		if (modMags != null && modMags.containsKey(eq.getEventId())) {
			double mag = modMags.get(eq.getEventId());
			System.out.println("Overriding magnitude of "+eq.getEventId()+" from "+(float)eq.getMag()+" to "+(float)mag);
			eq.setMag(mag);
		}
		int[] resetSubSects = resetSectsMap == null ? null : resetSectsMap.get(eq.getEventId());
		TriggerRupture triggerRup = null;
		for (RuptureBuilder builder : ruptureBuilders) {
			triggerRup = builder.build(eq, resetSubSects);
			if (triggerRup != null) {
				if (eq.getEventId().equals(primaryEventID))
					System.out.println("Primary rupture type: "+ClassUtils.getClassNameWithoutPackage(triggerRup.getClass()));
				break;
			}
		}
		Preconditions.checkNotNull(triggerRup, "Could not build trigger rupture for %s", eq.getEventId());
		Double etasK = modK == null ? null : modK.get(eq.getEventId());
		Double etasP = modP == null ? null : modP.get(eq.getEventId());
		Double etasC = modC == null ? null : modC.get(eq.getEventId());
		if (etasK != null || etasP != null || etasC != null) {
			System.out.println("Setting custom ETAS parameters for "+eq.getEventId());
			triggerRup.setETAS_Params(etasK, etasP, etasC);
		}
		triggerRup.setComcatEventID(eq.getEventId());
		if (CACHE_TRIGGER_RUPS)
			triggerRupCache.put(eq.getEventId(), triggerRup);
		return triggerRup;
	}
	
	public void buildInputPlots() throws IOException {
		if (cmd.hasOption("skip-input-plots"))
			return;
		// now write plots
		File plotDir = new File(outputDir, "config_input_plots");
		System.out.println();
		System.out.println("Writing diagnostic plots and input data to: "+plotDir.getAbsolutePath());
		Preconditions.checkState(plotDir.exists() || plotDir.mkdir());

		List<String> lines = new ArrayList<>();
		lines.add("# ETAS Configuration for "+config.getSimulationName());
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

		// now look for large or finite ruptures
		if (!config.isGriddedOnly()) {
			FiniteFaultSectionResetCalc mapper = null;
			for (TriggerRupture trigger : config.getTriggerRuptures()) {
				ETAS_EqkRupture rup = launcher.getRuptureForTrigger(trigger);
				RuptureSurface surface = rup.getRuptureSurface();
				boolean primary = primaryRupture != null && trigger.getComcatEventID().equals(primaryEventID);
				if (primary || !surface.isPointSurface() || rup.getMag() >= 6d) {
					if (mapper == null) {
						double faultBuffer = InversionTargetMFDs.FAULT_BUFFER;
						double minFractionalAreaInPolygon = 0.5;
						mapper = new FiniteFaultSectionResetCalc(fss.getRupSet(), minFractionalAreaInPolygon, faultBuffer, true);
					}
					lines.add("");
					lines.addAll(mapper.writeSectionResetMarkdown(plotDir, ".", "##", topLink, surface,
							rup.getHypocenterLocation(), trigger.getComcatEventID()));
				}
			}
		}

		lines.add("");
		lines.add("## JSON Input File");
		lines.add(topLink); lines.add("");
		SimulationMarkdownGenerator.addConfigLines(config, configFile, lines);
		lines.add("");

		launcher.checkInFSS(fss);

		List<String> tocLines = new ArrayList<>();
		tocLines.add("## Table Of Contents");
		tocLines.add("");
		tocLines.addAll(MarkdownUtils.buildTOC(lines, 2));

		lines.addAll(tocIndex, tocLines);

		System.out.println("Writing markdown and HTML");
		MarkdownUtils.writeReadmeAndHTML(lines, plotDir);
	}
	
	private void initRuptureBuilders(CommandLine cmd, String primaryEventID) {
		ruptureBuilders = new ArrayList<>();
		for (Option opt : cmd.getOptions()) {
			RuptureBuilder builder = getRuptureBuilder(cmd, opt, primaryEventID);
			if (builder != null)
				ruptureBuilders.add(builder);
		}
		
		// add fallback point source builder
		ruptureBuilders.add(new PointRuptureBuilder());
	}
	
	private static Map<String, Double> getEventSpecificDoubleOptions(CommandLine cmd, String opt, String primaryEventID) {
		Map<String, String> strings = getEventSpecificOptions(cmd, opt, primaryEventID);
		if (strings == null)
			return null;
		Map<String, Double> ret = new HashMap<>();
		for (String key : strings.keySet())
			ret.put(key, Double.parseDouble(strings.get(key)));
		return ret;
	}
	
	private static Map<String, String> getEventSpecificOptions(CommandLine cmd, String opt, String primaryEventID) {
		String[] values = cmd.getOptionValues(opt);
		if (values == null || values.length == 0)
			return null;
		Map<String, String> map = new HashMap<>();
		for (String value : values) {
			Preconditions.checkArgument(value.contains(":") || primaryEventID != null,
					"Expected --%s <event-id>:<value>, got --%s %s", opt, opt, value);
			if (value.contains(":")) {
				String[] split = value.split(":");
				Preconditions.checkState(split.length == 2, "Expected --%s <event-id>:<value>, got --%s %s", opt, opt, value);
				map.put(split[0], split[1]);
			} else {
				map.put(primaryEventID, value);
			}
		}
		return map;
	}
	
	protected RuptureBuilder getRuptureBuilder(CommandLine cmd, Option option, String primaryEventID) {
		Double minMag;
		switch (option.getLongOpt()) {
		case "finite-surf-shakemap":
			minMag = doubleArgIfPresent(cmd, "finite-surf-shakemap-min-mag");
			if (minMag == null) {
				if (primaryEventID == null)
					minMag = SHAKEMAP_MIN_MAG_DEFAULT;
				else
					minMag = Double.POSITIVE_INFINITY;
			}
			ShakeMapRuptureBuilder smBuilder = new ShakeMapRuptureBuilder(minMag, primaryEventID);
			
			smBuilder.setPlanarExtents(cmd.hasOption("finite-surf-shakemap-planar-extents"));
			if (cmd.hasOption("finite-surf-shakemap-version")) {
				Map<String, String> eventOps = getEventSpecificOptions(cmd, "finite-surf-shakemap-version", primaryEventID);
				for (String id : eventOps.keySet())
					smBuilder.setShakeMapVerison(id, Integer.parseInt(eventOps.get(id)));
			}
			
			return smBuilder;
		case "finite-surf-inversion":
			minMag = doubleArgIfPresent(cmd, "finite-surf-inversion-min-mag");
			if (minMag == null) {
				if (primaryEventID == null)
					minMag = SHAKEMAP_MIN_MAG_DEFAULT;
				else
					minMag = Double.POSITIVE_INFINITY;
			}
			Double minSlip = doubleArgIfPresent(cmd, "finite-surf-inversion-min-slip");
			if (minSlip == null)
				minSlip = 0d;
			return new InvertedSurfRuptureBuilder(minMag, minSlip, primaryEventID);
		case "finite-surf-from-sections":
			Map<String, int[]> resetSectsMap = getResetSectsMap();
			Preconditions.checkArgument(resetSectsMap != null && !resetSectsMap.isEmpty(),
					"must supply --reset-sects argument with --finite-surf-from-sections");
			return new SectionRuptureBuilder(resetSectsMap);

		default:
			return null;
		}
	}
	
	protected abstract String getPrimaryEventID();
	
	protected abstract Region buildRegion(ObsEqkRupture primaryRupture, TriggerRupture primaryTrigger);
	
	protected abstract long getComCatStartTime();
	
	protected abstract long getComCatEndTime();
	
	protected abstract long getSimulationStartTime(long comcatStartTime, long comcatEndTime);
	
	protected abstract String getBaseSimName();
	
	protected abstract String getScriptName();

}