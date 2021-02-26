package scratch.UCERF3.erf.ETAS.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;

import org.opensha.commons.data.comcat.ComcatRegion;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.LocationList;
import org.opensha.commons.geo.Region;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.SimpleFaultData;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_SimAnalysisTools;
import scratch.UCERF3.erf.ETAS.ETAS_SimulationMetadata;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_StatewideCatalogCompletenessParam;
import scratch.UCERF3.utils.FaultSystemIO;
import scratch.UCERF3.utils.U3_EqkCatalogStatewideCompleteness;

public class ETAS_Config {
	
	// calculation/time info
	private final int numSimulations;
	private final double duration;
	private Integer startYear = null;
	private Long startTimeMillis = null;
	private final boolean includeSpontaneous;
	private Long randomSeed = null;
	private boolean binaryOutput;
	private List<BinaryFilteredOutputConfig> binaryOutputFilters;
	private boolean forceRecalc = false;
	private Boolean reuseERFs = null;
	private String simulationName = null;
	private int numRetries = 3;
	private File outputDir;
	
	// input ruptures
	private File triggerCatalog = null;
	private File triggerCatalogSurfaceMappings = null;
	private Boolean treatTriggerCatalogAsSpontaneous = null;
	private List<TriggerRupture> triggerRuptures = null;
	
	// paths
	private final File cacheDir;
	private final File fssFile;
	
	// U3ETAS parameters
	private U3ETAS_ProbabilityModelOptions probModel = U3ETAS_ProbabilityModelOptions.FULL_TD;
	private boolean applySubSeisForSupraNucl = true;
	private double totRateScaleFactor = 1.14;
	private boolean gridSeisCorr = true;
	private boolean timeIndependentERF = false;
	private boolean griddedOnly = false;
	private boolean imposeGR = false;
	private boolean includeIndirectTriggering = true;
	private double gridSeisDiscr = 0.1;
	private U3_EqkCatalogStatewideCompleteness catalogCompletenessModel = U3ETAS_StatewideCatalogCompletenessParam.DEFAULT_VALUE;
	private Double etas_p = null;
	private Double etas_c = null;
	private Double etas_log10_k = null;
	private Double etas_k_cov = null;
	private Double maxPointSourceMag = null;
	
	// metadata
	private String configCommand = null;
	private Long configTime = null;
	
	// Comcat driven events
	private ComcatMetadata comcatMetadata = null;
	
	public static  class ComcatMetadata {
		public final Region region;
		public final String eventID;
		public final double minDepth;
		public final double maxDepth;
		public final double minMag;
		public final long startTime;
		public final long endTime;
		public Double magComplete;
		public ComcatMetadata(Region region, String eventID, double minDepth, double maxDepth,
				double minMag, long startTime, long endTime) {
			super();
			this.region = region;
			this.eventID = eventID;
			this.minDepth = minDepth;
			this.maxDepth = maxDepth;
			this.minMag = minMag;
			this.startTime = startTime;
			this.endTime = endTime;
		}
	}
	
	public ETAS_Config(int numSimulations, double duration, boolean includeSpontaneous, File cacheDir, File fssFile, File outputDir) {
		this(numSimulations, duration, includeSpontaneous, cacheDir, fssFile, outputDir, null, null);
	}
	
	public ETAS_Config(int numSimulations, double duration, boolean includeSpontaneous, File cacheDir, File fssFile, File outputDir,
			File triggerCatalog, File triggerCatalogSurfaceMappings, TriggerRupture... triggerRuptures) {
		this(numSimulations, duration, includeSpontaneous, cacheDir, fssFile, outputDir,
				triggerCatalog, triggerCatalogSurfaceMappings, toList(triggerRuptures));
	}
	
	private static List<TriggerRupture> toList(TriggerRupture[] triggerRuptures) {
		if (triggerRuptures == null || triggerRuptures.length == 0)
			return null;
		
		List<TriggerRupture> list = new ArrayList<>();
		
		for (TriggerRupture rup : triggerRuptures)
			list.add(rup);
		
		return list;
	}
	
	ETAS_Config(int numSimulations, double duration, boolean includeSpontaneous, File cacheDir, File fssFile, File outputDir,
			File triggerCatalog, File triggerCatalogSurfaceMappings, List<TriggerRupture> triggerRuptures) {
		this.numSimulations = numSimulations;
		this.duration = duration;
		this.includeSpontaneous = includeSpontaneous;
		this.cacheDir = cacheDir;
		this.fssFile = fssFile;
		this.outputDir = outputDir;
		this.triggerCatalog = triggerCatalog;
		this.triggerCatalogSurfaceMappings = triggerCatalogSurfaceMappings;
		this.triggerRuptures = triggerRuptures;
		double simulatedYears = numSimulations*duration;
		binaryOutput = simulatedYears > 1000;
		if (triggerCatalog != null)
			this.treatTriggerCatalogAsSpontaneous = false;
		buildDefaultBinaryOutputFilters();
	}
	
	public void buildDefaultBinaryOutputFilters() {
		binaryOutputFilters = new ArrayList<>();
		binaryOutputFilters.add(
				new BinaryFilteredOutputConfig("results_complete", null, null, false));
		binaryOutputFilters.add(
				new BinaryFilteredOutputConfig("results_m5_preserve_chain", 5d, true, false));
		if (includeSpontaneous && hasTriggers())
			binaryOutputFilters.add(
					new BinaryFilteredOutputConfig("results_triggered_descendants", null, null, true));
	}
	
	private static Gson buildGson(boolean resolvePaths, boolean skipTriggers) {
		GsonBuilder builder = new GsonBuilder();
		builder.setPrettyPrinting();
		builder.registerTypeAdapter(File.class, new FileTypeAdapter(resolvePaths).nullSafe());
		builder.registerTypeAdapter(Location.class, new LocationTypeAdapter().nullSafe());
		if (skipTriggers)
			builder.registerTypeAdapter(TriggerRupture.class, new SkipTriggerRuptureTypeAdapter().nullSafe());
		else
			builder.registerTypeAdapter(TriggerRupture.class, new TriggerRuptureTypeAdapter().nullSafe());
		builder.registerTypeAdapter(Region.class, new RegionTypeAdapter().nullSafe());
		Gson gson = builder.create();
		return gson;
	}
	
	public static File resolvePath(File file) {
		if (file == null)
			return null;
		return resolvePath(file.getPath());
	}
	
	public static File resolvePath(String path) {
		return resolvePath(path, System.getenv());
	}
	
	private static HashSet<String> resolvedVars = new HashSet<>();
	
	private static File resolvePath(String path, Map<String, String> env) {
		while (path.contains("$")) {
			int index = path.indexOf("$");
			Preconditions.checkState(index < path.length()-1, "path cannot end with a '$'");
			String replaceStr = path.substring(index);
			String var;
			if (replaceStr.charAt(1) == '{') {
				Preconditions.checkState(replaceStr.contains("}"));
				replaceStr = replaceStr.substring(0, replaceStr.indexOf("}")+1);
				var = replaceStr.substring(2, replaceStr.length()-1);
			} else {
				if (replaceStr.contains(File.separator))
					replaceStr = replaceStr.substring(0, replaceStr.indexOf(File.separator));
				var = replaceStr.substring(1);
			}
			String value = env.get(var);
			boolean firstTime = !resolvedVars.contains(var);
			if (value == null || firstTime)
				System.out.println("Path ('"+path+"') contains environmental variable ('"+var+"')");
			Preconditions.checkNotNull(value, "Environmental variable %s not found! Can't build path", var);
			path = path.replace(replaceStr, value);
			if (firstTime) {
				System.out.println("\treplacing '"+replaceStr+"' with '"+value+"': "+path);
				synchronized (resolvedVars) {
					resolvedVars.add(var);
				}
			}
		}
		return new File(path);
	}
	
	private static class FileTypeAdapter extends TypeAdapter<File> {
		
		private Map<String, String> env;
		private boolean resolvePaths;
		
		private FileTypeAdapter(boolean resolvePaths) {
			this.resolvePaths = resolvePaths;
			env = System.getenv();
		}

		@Override
		public void write(JsonWriter out, File value) throws IOException {
			out.value(value.getPath());
		}

		@Override
		public File read(JsonReader in) throws IOException {
			String path = in.nextString();
			if (resolvePaths)
				return resolvePath(path, env);
			return new File(path);
		}
		
	}
	
	private static class LocationTypeAdapter extends TypeAdapter<Location> {

		@Override
		public void write(JsonWriter out, Location value) throws IOException {
			out.beginObject();
			out.name("latitude").value(value.getLatitude());
			out.name("longitude").value(value.getLongitude());
			out.name("depth").value(value.getDepth());
			out.endObject();
		}

		@Override
		public Location read(JsonReader in) throws IOException {
			in.beginObject();
			double lat = Double.NaN;
			double lon = Double.NaN;
			double depth = Double.NaN;
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "latitude":
					lat = in.nextDouble();
					break;
				case "longitude":
					lon = in.nextDouble();
					break;
				case "depth":
					depth = in.nextDouble();
					break;
				}
			}
			in.endObject();

			return new Location(lat, lon, depth);
		}
		
	}
	
	private static class SkipTriggerRuptureTypeAdapter extends TypeAdapter<TriggerRupture> {

		private boolean first = true;
		@Override
		public void write(JsonWriter out, TriggerRupture value) throws IOException {
			if (first)
				out.value("omitted due to length, see original input file");
			first = false;
		}

		@Override
		public TriggerRupture read(JsonReader in) throws IOException {
			throw new UnsupportedOperationException();
		}
		
	}
	
	private static class TriggerRuptureTypeAdapter extends TypeAdapter<TriggerRupture> {

		@Override
		public void write(JsonWriter out, TriggerRupture value) throws IOException {
			out.beginObject();
			if (value.customOccurrenceTime != null && value.customOccurrenceTime > Long.MIN_VALUE)
				out.name("occurrenceTimeMillis").value(value.customOccurrenceTime);
			if (value.getComcatEventID() != null && value.getComcatEventID().length() > 0)
				out.name("comcatEventID").value(value.getComcatEventID());
			if (value.getETAS_log10_k() != null)
				out.name("etas_log10_k").value(value.getETAS_log10_k());
			if (value.getETAS_p() != null)
				out.name("etas_p").value(value.getETAS_p());
			if (value.getETAS_c() != null)
				out.name("etas_c").value(value.getETAS_c());
			if (value instanceof TriggerRupture.FSS) {
				TriggerRupture.FSS fssRup = (TriggerRupture.FSS)value;
				out.name("fssIndex").value(fssRup.fssIndex);
				if (fssRup.overrideMag != null && fssRup.overrideMag > 0)
					out.name("mag").value(fssRup.overrideMag);
			} else if (value instanceof TriggerRupture.SectionBased) {
				TriggerRupture.SectionBased sectRup = (TriggerRupture.SectionBased)value;
				out.name("mag").value(sectRup.mag);
				out.name("subSectIndexes").beginArray();
				for (int index : sectRup.subSects)
					out.value(index);
				out.endArray();
			} else if (value instanceof TriggerRupture.Point) {
				TriggerRupture.Point ptRup = (TriggerRupture.Point)value;
				out.name("mag").value(ptRup.mag);
				Location loc = ptRup.hypocenter;
				out.name("latitude").value(loc.getLatitude());
				out.name("longitude").value(loc.getLongitude());
				out.name("depth").value(loc.getDepth());
				if (ptRup.sectsReset != null && ptRup.sectsReset.length > 0) {
					out.name("subSectResetIndexes").beginArray();
					for (int index : ptRup.sectsReset)
						out.value(index);
					out.endArray();
				}
			} else if (value instanceof TriggerRupture.SimpleFault) {
				TriggerRupture.SimpleFault sfdRup = (TriggerRupture.SimpleFault)value;
				out.name("mag").value(sfdRup.mag);
				if (sfdRup.hypo != null) {
					out.name("latitude").value(sfdRup.hypo.getLatitude());
					out.name("longitude").value(sfdRup.hypo.getLongitude());
					out.name("depth").value(sfdRup.hypo.getDepth());
				}
				out.name("ruptureSurfaces").beginArray();
				for (SimpleFaultData sfd : sfdRup.sfds) {
					out.beginObject();
					out.name("dip").value(sfd.getAveDip());
					if (Double.isFinite(sfd.getAveDipDir()))
						out.name("dipDir").value(sfd.getAveDipDir());
					out.name("upperDepth").value(sfd.getUpperSeismogenicDepth());
					out.name("lowerDepth").value(sfd.getLowerSeismogenicDepth());
					out.name("trace").beginArray();
					for (Location loc : sfd.getFaultTrace()) {
						out.beginObject();
						out.name("latitude").value(loc.getLatitude());
						out.name("longitude").value(loc.getLongitude());
						out.name("depth").value(loc.getDepth());
						out.endObject();
					}
					out.endArray();
					out.endObject();
				}
				out.endArray();
				if (sfdRup.sectsReset != null && sfdRup.sectsReset.length > 0) {
					out.name("subSectResetIndexes").beginArray();
					for (int index : sfdRup.sectsReset)
						out.value(index);
					out.endArray();
				}
			} else if (value instanceof TriggerRupture.EdgeFault) {
				TriggerRupture.EdgeFault edgeRup = (TriggerRupture.EdgeFault)value;
				out.name("mag").value(edgeRup.mag);
				if (edgeRup.hypo != null) {
					out.name("latitude").value(edgeRup.hypo.getLatitude());
					out.name("longitude").value(edgeRup.hypo.getLongitude());
					out.name("depth").value(edgeRup.hypo.getDepth());
				}
				out.name("ruptureSurfaces").beginArray();
				for (LocationList outline : edgeRup.outlines) {
					out.beginObject();
					out.name("outline").beginArray();
					for (Location loc : outline) {
						out.beginObject();
						out.name("latitude").value(loc.getLatitude());
						out.name("longitude").value(loc.getLongitude());
						out.name("depth").value(loc.getDepth());
						out.endObject();
					}
					out.endArray();
					out.endObject();
				}
				out.endArray();
				if (edgeRup.sectsReset != null && edgeRup.sectsReset.length > 0) {
					out.name("subSectResetIndexes").beginArray();
					for (int index : edgeRup.sectsReset)
						out.value(index);
					out.endArray();
				}
			} else {
				throw new IllegalStateException("Not yet implemented for subcalss "+value.getClass().getName());
			}
			out.endObject();
		}

		@Override
		public TriggerRupture read(JsonReader in) throws IOException {
			// global
			Long customOccurrenceTime = null;
			Double mag = null;
			
			// FSS
			Integer fssIndex = null;
			
			// Sect
			int[] subSects = null;
			
			// Point
			Double lat = null;
			Double lon = null;
			Double depth = null;
			
			// SFD
			SimpleFaultData[] sfds = null;
			
			// EdgeRupture
			LocationList[] outlines = null;
			
			// SFD, Edge, or Point
			int[] resetSubSects = null;
			
			String comcatEventID = null;
			
			// ETAS params
			Double log10_k = null, p = null, c = null;
			
			in.beginObject();
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "occurrenceTimeMillis":
					customOccurrenceTime = in.nextLong();
					break;
				case "comcatEventID":
					comcatEventID = in.nextString();
					break;
				case "mag":
					mag = in.nextDouble();
					break;
				case "fssIndex":
					fssIndex = in.nextInt();
					break;
				case "subSectIndexes":
					in.beginArray();
					List<Integer> indexes = new ArrayList<>();
					while (in.hasNext()) {
						int index = in.nextInt();
						indexes.add(index);
					}
					in.endArray();
					subSects = Ints.toArray(indexes);
					break;
				case "latitude":
					lat = in.nextDouble();
					break;
				case "longitude":
					lon = in.nextDouble();
					break;
				case "depth":
					depth = in.nextDouble();
					break;
				case "ruptureSurfaces":
					// SFD or EdgeRupture
					List<SimpleFaultData> sfdList = new ArrayList<>();
					List<LocationList> outlineList = new ArrayList<>();
					in.beginArray();
					while (in.hasNext()) {
						in.beginObject();
						Double dip = null;
						double dipDir = Double.NaN;
						Double upperDepth = null;
						Double lowerDepth = null;
						FaultTrace trace = null;
						LocationList outline = null;
						while (in.hasNext()) {
							switch (in.nextName()) {
							case "dip":
								dip = in.nextDouble();
								break;
							case "dipDir":
								dipDir = in.nextDouble();
								break;
							case "upperDepth":
								upperDepth = in.nextDouble();
								break;
							case "lowerDepth":
								lowerDepth = in.nextDouble();
								break;
							case "trace":
								in.beginArray();
								trace = new FaultTrace("Custom Fault");
								while (in.hasNext()) {
									in.beginObject();
									trace.add(readLocation(in, true));
									in.endObject();
								}
								in.endArray();
								break;
							case "outline":
								in.beginArray();
								outline = new LocationList();
								while (in.hasNext()) {
									in.beginObject();
									outline.add(readLocation(in, false));
									in.endObject();
								}
								in.endArray();
								break;
							}
						}
						in.endObject();
						if (outline == null) {
							Preconditions.checkNotNull(dip, "surface dip not specified");
							Preconditions.checkNotNull(upperDepth, "surface upper depth not specified");
							Preconditions.checkNotNull(lowerDepth, "surface lower depth not specified");
							Preconditions.checkNotNull(trace, "surface trace not specified");
							sfdList.add(new SimpleFaultData(dip, lowerDepth, upperDepth, trace, dipDir));
						} else {
							Preconditions.checkState(dip == null, "surface dip cannot be specified with outline");
							Preconditions.checkState(upperDepth == null, "surface upper depth cannot be specified with outline");
							Preconditions.checkState(lowerDepth == null, "surface lower depth cannot be specified with outline");
							Preconditions.checkState(trace == null, "surface trace cannot be specified with outline");
							outlineList.add(outline);
						}
					}
					in.endArray();
					if (sfdList.size() > 0) {
						Preconditions.checkState(outlineList.isEmpty(), "Can't mix simple faults and outlines");
						sfds = sfdList.toArray(new SimpleFaultData[0]);
					} else {
						Preconditions.checkState(outlineList.size() > 0, "No rupture surfaces specified");
						outlines = outlineList.toArray(new LocationList[0]);
					}
					break;
				case "subSectResetIndexes":
					in.beginArray();
					List<Integer> myIndexes = new ArrayList<>();
					while (in.hasNext()) {
						int index = in.nextInt();
						myIndexes.add(index);
					}
					in.endArray();
					resetSubSects = Ints.toArray(myIndexes);
					break;
				case "etas_log10_k":
					log10_k = in.nextDouble();
//					System.out.println("Custom k-value: "+log10_k);
					break;
				case "etas_p":
					p = in.nextDouble();
					break;
				case "etas_c":
					c = in.nextDouble();
					break;
				}
			}
			in.endObject();
			
			TriggerRupture trigger;
			
			if (fssIndex != null) {
				Preconditions.checkState(lat == null && lon == null && depth == null,
						"Cannot specify point source location for FSS rupture");
				Preconditions.checkState(subSects == null, "Cannot specify sub sections for FSS rupture");
				trigger = new TriggerRupture.FSS(fssIndex, customOccurrenceTime, mag);
			} else if (sfds != null) {
				Location hypo = null;
				if (lat != null && lon != null)
					hypo = new Location(lat, lon, depth);
				Preconditions.checkNotNull(mag, "Must specify magnitude for simple fault rupture");
				trigger = new TriggerRupture.SimpleFault(customOccurrenceTime, hypo, mag, resetSubSects, sfds);
			} else if (outlines != null) {
				Location hypo = null;
				if (lat != null && lon != null)
					hypo = new Location(lat, lon, depth);
				Preconditions.checkNotNull(mag, "Must specify magnitude for edge rupture");
				trigger = new TriggerRupture.EdgeFault(customOccurrenceTime, hypo, mag, resetSubSects, outlines);
			} else if (subSects != null) {
				Preconditions.checkState(lat == null && lon == null && depth == null,
						"Cannot specify point source location for sub sect rupture");
				Preconditions.checkNotNull(mag, "Must specify magnitude for sub sect rupture");
				trigger = new TriggerRupture.SectionBased(subSects, customOccurrenceTime, mag);
			} else {
				// must be point rupture
				Preconditions.checkNotNull(lat, "Must specify latitude for point source rupture");
				Preconditions.checkNotNull(lon, "Must specify longitude for point source rupture");
				Preconditions.checkNotNull(depth, "Must specify depth for point source rupture");
				Preconditions.checkNotNull(mag, "Must specify magnitude for point source rupture");
				Location hypocenter = new Location(lat, lon, depth);

				trigger = new TriggerRupture.Point(hypocenter, customOccurrenceTime, mag, resetSubSects);
			}
			trigger.setComcatEventID(comcatEventID);
			trigger.setETAS_Params(log10_k, p, c);
			return trigger;
		}
		
	}
	
	private static Location readLocation(JsonReader in, boolean allowNullDepth) throws IOException {
		Double lat = null; Double lon = null; Double depth = null;
		while (in.hasNext()) {
			switch (in.nextName()) {
			case "latitude":
				lat = in.nextDouble();
				break;
			case "longitude":
				lon = in.nextDouble();
				break;
			case "depth":
				depth = in.nextDouble();
				break;
			}
		}
		Preconditions.checkNotNull(lat, "trace point latitude not specified");
		Preconditions.checkNotNull(lon, "trace point longitude not specified");
		if (!allowNullDepth)
			Preconditions.checkNotNull(depth, "trace point depth not specified");
		if (depth == null)
			return new Location(lat, lon);
		return new Location(lat, lon, depth);
	}
	
	public static class CircularRegion extends Region implements ComcatRegion {
		private Location center;
		private double radius;

		public CircularRegion(Location center, double radius) {
			super(center, radius);
			this.center = center;
			this.radius = radius;
		}

		@Override
		public boolean contains(double lat, double lon) {
			return contains(new Location(lat, lon));
		}

		@Override
		public boolean isCircular() {
			// don't tell ComCat that it's circular, we don't want to use their 
			// circle query which is in degrees
			return false;
		}
	}
	
	private static class RegionTypeAdapter extends TypeAdapter<Region> {

		@Override
		public void write(JsonWriter out, Region value) throws IOException {
			out.beginObject();
			if (value.isRectangular()) {
				out.name("minLatitude").value(value.getMinLat());
				out.name("maxLatitude").value(value.getMaxLat());
				out.name("minLongitude").value(value.getMinLon());
				out.name("maxLongitude").value(value.getMaxLon());
			} else if (value instanceof CircularRegion) {
				CircularRegion circle = (CircularRegion)value;
				out.name("centerLatitude").value(circle.center.getLatitude());
				out.name("centerLongitude").value(circle.center.getLongitude());
				out.name("radius").value(circle.radius);
			} else {
				out.name("border").beginArray();
				for (Location loc : value.getBorder()) {
					out.beginObject();
					out.name("latitude").value(loc.getLatitude());
					out.name("longitude").value(loc.getLongitude());
					out.endObject();
				}
				out.endArray();
			}
			out.endObject();
		}

		@Override
		public Region read(JsonReader in) throws IOException {
			in.beginObject();
			// rectangular
			Double minLat = null;
			Double maxLat = null;
			Double minLon = null;
			Double maxLon = null;
			
			// circular
			Double centerLat = null;
			Double centerLon = null;
			Double radius = null;
			
			// arbitrary
			LocationList border = null;
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "minLatitude":
					minLat = in.nextDouble();
					break;
				case "maxLatitude":
					maxLat = in.nextDouble();
					break;
				case "minLongitude":
					minLon = in.nextDouble();
					break;
				case "maxLongitude":
					maxLon = in.nextDouble();
					break;
				case "centerLatitude":
					centerLat = in.nextDouble();
					break;
				case "centerLongitude":
					centerLon = in.nextDouble();
					break;
				case "radius":
					radius = in.nextDouble();
					break;
				case "border":
					border = new LocationList();
					in.beginArray();
					while (in.hasNext()) {
						Double lat = null;
						Double lon = null;
						in.beginObject();
						while (in.hasNext()) {
							switch (in.nextName()) {
							case "latitude":
								lat = in.nextDouble();
								break;
							case "longitude":
								lon = in.nextDouble();
								break;
							}
						}
						in.endObject();
						Preconditions.checkNotNull(lat, "Border latitude not supplied");
						Preconditions.checkNotNull(lon, "Border longtiude not supplied");
						border.add(new Location(lat, lon));
					}
					in.endArray();
					break;
				}
			}
			in.endObject();
			
			if (minLat != null) {
				Preconditions.checkNotNull(maxLat, "maxLatitude not specified");
				Preconditions.checkNotNull(minLon, "minLongitude not specified");
				Preconditions.checkNotNull(maxLon, "maxLongitude not specified");
				return new Region(new Location(minLat, minLon), new Location(maxLat, maxLon));
			}
			if (centerLat != null) {
				Preconditions.checkNotNull(centerLon, "centerLatitude not specified");
				Preconditions.checkNotNull(radius, "circle radius not specified");
				return new CircularRegion(new Location(centerLat, centerLon), radius);
			}
			Preconditions.checkNotNull(border, "Must specify either rectangular region, circular region, or supply border");
			return new Region(border, null);
		}
		
	}
	
	public class BinaryFilteredOutputConfig {
		
		private final String prefix;
		private final Double minMag;
		private final Boolean preserveChainBelowMag;
		private final boolean descendantsOnly;
		
		private BinaryFilteredOutputConfig(String prefix, Double minMag, Boolean preserveChainBelowMag,
				boolean descendantsOnly) {
			this.prefix = prefix;
			this.minMag = minMag;
			if (minMag != null)
				this.preserveChainBelowMag = preserveChainBelowMag;
			else
				this.preserveChainBelowMag = null;
			this.descendantsOnly = descendantsOnly;
		}

		public String getPrefix() {
			return prefix;
		}

		public Double getMinMag() {
			return minMag;
		}

		public boolean isPreserveChainBelowMag() {
			if (preserveChainBelowMag == null)
				return true;
			return preserveChainBelowMag;
		}

		public boolean isDescendantsOnly() {
			return descendantsOnly;
		}
		
		public File getPreStagedCatalogFile(File catalogDir) {
			if (!isDescendantsOnly() && getMinMag() == null) {
				// it's complete
				return new File(catalogDir, "simulatedEvents.bin");
			}
			return new File(catalogDir, "filtered_for_"+getPrefix()+".bin");
		}
		
		public ETAS_Catalog filter(ETAS_Config config, ETAS_Catalog catalog) {
			if (isDescendantsOnly() && !catalog.isEmpty())
				catalog = ETAS_Launcher.getFilteredNoSpontaneous(config, catalog);
			ETAS_SimulationMetadata meta = catalog.getSimulationMetadata();
			Double minMag = getMinMag();
			if (minMag != null && minMag > 0 && !catalog.isEmpty()) {
				if (isPreserveChainBelowMag()) {
					catalog = ETAS_SimAnalysisTools.getAboveMagPreservingChain(catalog, minMag);
				} else {
					ETAS_Catalog filteredCatalog = new ETAS_Catalog(meta == null ? null : meta.getModMinMag(minMag));
					for (ETAS_EqkRupture rup : catalog)
						if (rup.getMag() >= getMinMag())
							filteredCatalog.add(rup);
					if (filteredCatalog.getSimulationMetadata() != null)
						filteredCatalog.updateMetadataForCatalog();
					catalog = filteredCatalog;
				}
			}
			return catalog;
		}
	}
	
	public void setStartYear(Integer startYear) {
		this.startYear = startYear;
		this.startTimeMillis = null;
	}

	public void setStartTimeMillis(Long startTimeMillis) {
		this.startTimeMillis = startTimeMillis;
		this.startYear = null;
	}

	public void setTriggerCatalog(File triggerCatalog) {
		this.triggerCatalog = triggerCatalog;
	}

	public void setTriggerCatalogSurfaceMappings(File triggerCatalogSurfaceMappings) {
		this.triggerCatalogSurfaceMappings = triggerCatalogSurfaceMappings;
	}
	
	public void addTriggerRupture(TriggerRupture rup) {
		if (triggerRuptures == null)
			triggerRuptures = new ArrayList<>();
		triggerRuptures.add(rup);
	}
	
	public List<TriggerRupture> getTriggerRuptures() {
		return triggerRuptures;
	}
	
	public boolean hasTriggers() {
		return getTriggerRuptures() != null && !getTriggerRuptures().isEmpty()
				|| getTriggerCatalogFile() != null && !isTreatTriggerCatalogAsSpontaneous();
	}
	
	public long getSimulationStartTimeMillis() {
		if (startTimeMillis != null) {
			Preconditions.checkState(startYear == null, "Cannon specify both start year and time in milliseconds");
			return startTimeMillis;
		}
		Preconditions.checkNotNull(startYear, "Must specify either start year or time in milliseconds");
		GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		cal.clear();
		cal.set(startYear, 0, 1);
		return cal.getTimeInMillis();
	}
	
	public int getNumSimulations() {
		return numSimulations;
	}

	public double getDuration() {
		return duration;
	}

	public boolean isIncludeSpontaneous() {
		return includeSpontaneous;
	}
	
	public void setRandomSeed(Long randomSeed) {
		this.randomSeed = randomSeed;
	}

	public Long getRandomSeed() {
		return randomSeed;
	}
	
	public boolean isBinaryOutput() {
		return binaryOutput;
	}
	
	public void setBinaryOutput(boolean binaryOutput) {
		this.binaryOutput = binaryOutput;
	}
	
	public void setBinaryOutputFilters(List<BinaryFilteredOutputConfig> binaryOutputFilters) {
		this.binaryOutputFilters = binaryOutputFilters;
	}
	
	public List<BinaryFilteredOutputConfig> getBinaryOutputFilters() {
		return binaryOutputFilters;
	}
	
	public boolean hasBinaryOutputFilters() {
		return getBinaryOutputFilters() != null && !getBinaryOutputFilters().isEmpty();
	}
	
	public boolean isForceRecalc() {
		return forceRecalc;
	}
	
	public void setReuseERFs(Boolean reuseERFs) {
		this.reuseERFs = reuseERFs;
	}
	
	public boolean isReuseERFs() {
		return reuseERFs == null ? true : reuseERFs;
	}
	
	public void setSimulationName(String simulationName) {
		this.simulationName = simulationName;
	}
	
	public String getSimulationName() {
		return simulationName;
	}
	
	public int getNumRetries() {
		return numRetries;
	}

	public File getTriggerCatalogFile() {
		return triggerCatalog;
	}

	public File getTriggerCatalogSurfaceMappingsFile() {
		return triggerCatalogSurfaceMappings;
	}
	
	public void setTreatTriggerCatalogAsSpontaneous(boolean treatTriggerCatalogAsSpontaneous) {
		this.treatTriggerCatalogAsSpontaneous = treatTriggerCatalogAsSpontaneous;
	}
	
	public boolean isTreatTriggerCatalogAsSpontaneous() {
		if (treatTriggerCatalogAsSpontaneous == null)
			return false;
		return treatTriggerCatalogAsSpontaneous;
	}

	public File getCacheDir() {
		return cacheDir;
	}
	
	public File getFSS_File() {
		return fssFile;
	}

	public File getOutputDir() {
		return outputDir;
	}

	public void setOutputDir(File outputDir) {
		this.outputDir = outputDir;
	}
	
	public void setProbModel(U3ETAS_ProbabilityModelOptions probModel) {
		this.probModel = probModel;
	}

	public U3ETAS_ProbabilityModelOptions getProbModel() {
		return probModel;
	}
	
	public void setETAS_P(Double p) {
		this.etas_p = p;
	}
	
	public Double getETAS_P() {
		return etas_p;
	}
	
	public void setETAS_C(Double c) {
		this.etas_c = c;
	}
	
	public Double getETAS_C() {
		return etas_c;
	}
	
	public void setETAS_Log10_K(Double log10_k) {
		this.etas_log10_k = log10_k;
	}
	
	public Double getETAS_Log10_K() {
		return etas_log10_k;
	}
	
	public void setETAS_K_COV(Double kCOV) {
		this.etas_k_cov = kCOV;
	}
	
	public Double getETAS_K_COV() {
		return etas_k_cov;
	}
	
	public Double getMaxPointSourceMag() {
		return maxPointSourceMag;
	}
	
	public void setMaxPointSourceMag(Double maxPointSourceMag) {
		this.maxPointSourceMag = maxPointSourceMag;
	}

	public boolean isApplySubSeisForSupraNucl() {
		return applySubSeisForSupraNucl;
	}

	public void setTotRateScaleFactor(double totRateScaleFactor) {
		this.totRateScaleFactor = totRateScaleFactor;
	}

	public double getTotRateScaleFactor() {
		return totRateScaleFactor;
	}
	
	public void setGridSeisCorr(boolean gridSeisCorr) {
		this.gridSeisCorr = gridSeisCorr;
	}

	public boolean isGridSeisCorr() {
		return gridSeisCorr;
	}
	
	public boolean isTimeIndependentERF() {
		return timeIndependentERF;
	}

	public boolean isGriddedOnly() {
		return griddedOnly;
	}

	public void setGriddedOnly(boolean griddedOnly) {
		this.griddedOnly = griddedOnly;
	}
	
	public void setImposeGR(boolean imposeGR) {
		this.imposeGR = imposeGR;
	}

	public boolean isImposeGR() {
		return imposeGR;
	}

	public boolean isIncludeIndirectTriggering() {
		return includeIndirectTriggering;
	}

	public double getGridSeisDiscr() {
		return gridSeisDiscr;
	}
	
	public U3_EqkCatalogStatewideCompleteness getCompletenessModel() {
		return catalogCompletenessModel;
	}
	
	public void setComcatMetadata(ComcatMetadata comcatMetadata) {
		this.comcatMetadata = comcatMetadata;
	}
	
	public ComcatMetadata getComcatMetadata() {
		return comcatMetadata;
	}

	public String getConfigCommand() {
		return configCommand;
	}

	public void setConfigCommand(String configCommand) {
		this.configCommand = configCommand;
	}

	public Long getConfigTime() {
		return configTime;
	}

	public void setConfigTime(Long configTime) {
		this.configTime = configTime;
	}

	private transient FaultSystemSolution fss;
	public synchronized FaultSystemSolution loadFSS() {
		if (fss == null) {
			Preconditions.checkNotNull(fssFile, "Must specify fault system solution file");
			Preconditions.checkState(fssFile.exists(), "FSS file doesn't exist");
			try {
				fss = FaultSystemIO.loadSol(fssFile);
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		return fss;
	}
	
	public String toJSON() {
		return toJSON(false);
	}
	
	public String toJSON(boolean skipTriggers) {
		Gson gson = buildGson(true, skipTriggers);
		return gson.toJson(this);
	}
	
	public void writeJSON(File jsonFile) throws IOException {
		jsonFile = resolvePath(jsonFile);
		FileWriter fw = new FileWriter(jsonFile);
		fw.write(toJSON());
		fw.write("\n");
		fw.close();
	}
	
	public static ETAS_Config readJSON(File jsonFile) throws IOException {
		return readJSON(jsonFile, true);
	}
	
	public static ETAS_Config readJSON(File jsonFile, boolean resolvePaths) throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(jsonFile));
		return readJSON(reader, resolvePaths);
	}
	
	public static ETAS_Config readJSON(String json) {
		return readJSON(json, true);
	}
	
	public static ETAS_Config readJSON(String json, boolean resolvePaths) {
		return readJSON(new StringReader(json), resolvePaths);
	}
	
	public static ETAS_Config readJSON(Reader json, boolean resolvePaths) {
		Gson gson = buildGson(resolvePaths, false);
		ETAS_Config conf = gson.fromJson(json, ETAS_Config.class);
		try {
			json.close();
		} catch (IOException e) {}
		return conf;
	}
	
	public static void updateComcatMagComplete(File jsonFile, Double magComplete) throws IOException {
		ETAS_Config config = readJSON(jsonFile);
		ComcatMetadata meta = config.comcatMetadata;
		Preconditions.checkNotNull(meta);
		meta.magComplete = magComplete;
		
		Map<String, String> env = System.getenv();
		String json = config.toJSON();
		for (String var : env.keySet()) {
			String val = env.get(var);
			if (!var.startsWith("ETAS") || !json.contains(val) || var.equals("ETAS_MEM_GB"))
				continue;
			System.out.println("\tReplacing path '"+val+"' with ${"+var+"}");
			json = json.replaceAll(val, Matcher.quoteReplacement("${"+var+"}"));
		}
		FileWriter fw = new FileWriter(jsonFile);
		fw.write(json+"\n");
		fw.close();
	}
	
//	public static void unResolvePaths

	public static void main(String[] args) {
		if (args.length == 1) {
			File jsonFile = new File(args[0]);
			try {
				if (!jsonFile.isDirectory()) {
					System.out.println("Loading JSON from "+jsonFile);
					ETAS_Config config = readJSON(jsonFile);
					System.out.println("================");
					System.out.println(config.toJSON());
					System.out.println("================");
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		System.exit(0);
		int numSimulations = 1000;
		double duration = 10;
		boolean includeSpontaneous = true;
		File cacheDir = new File("/auto/scec-02/kmilner/ucerf3/etas_sim/cache_fm3p1_ba");
		File fssFile = new File("/home/scec-02/kmilner/ucerf3/inversion_compound_plots/2013_05_10-ucerf3p3-production-10runs/"
				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
		File outputDir = new File("/tmp");
		FaultTrace trace1 = new FaultTrace("asdf");
		trace1.add(new Location(34, -118));
		trace1.add(new Location(34, -119));
		FaultTrace trace2 = new FaultTrace("asdf");
		trace2.add(new Location(35, -118));
		trace2.add(new Location(35, -119));
		trace2.add(new Location(35, -120));
		SimpleFaultData[] sfds = {
				new SimpleFaultData(90d, 10, 0, trace1, Double.NaN),
				new SimpleFaultData(60d, 12, 2, trace2, Double.NaN)
		};
		ETAS_Config conf = new ETAS_Config(numSimulations, duration, includeSpontaneous, cacheDir, fssFile, outputDir,
				null, null,
				new TriggerRupture.FSS(1234, 11111243l, 6.5),
				new TriggerRupture.FSS(12345, null, null),
				new TriggerRupture.SectionBased(new int[] {0,5,10}, null, 7.2),
				new TriggerRupture.Point(new Location(34, -118, 5.5), 12321l, 5.7),
				new TriggerRupture.SimpleFault(123456l, null, 8d, sfds));
		
		String json1 = conf.toJSON();
		System.out.println("Orig JSON");
		System.out.println(json1);
		ETAS_Config conf2 = readJSON(json1);
		System.out.println();
		System.out.println("Re-serialized JSON");
		System.out.println(conf2.toJSON());
	}

}
