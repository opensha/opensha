package scratch.UCERF3.erf.ETAS.launcher;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.dom4j.DocumentException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FaultUtils;
import org.opensha.refFaultParamDb.vo.FaultSectionPrefData;
import org.opensha.sha.earthquake.EqkRupture;
import org.opensha.sha.faultSurface.CompoundSurface;
import org.opensha.sha.faultSurface.RuptureSurface;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import scratch.UCERF3.FaultSystemRupSet;
import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;
import scratch.UCERF3.utils.FaultSystemIO;

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
	private String simulationName = null;
	private int numRetries = 3;
	private final File outputDir;
	
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
			this.treatTriggerCatalogAsSpontaneous = true;
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
	
	private static Gson buildGson() {
		GsonBuilder builder = new GsonBuilder();
		builder.setPrettyPrinting();
		builder.registerTypeAdapter(File.class, new FileTypeAdapter().nullSafe());
		builder.registerTypeAdapter(Location.class, new LocationTypeAdapter().nullSafe());
		builder.registerTypeAdapter(TriggerRupture.class, new TriggerRuptureTypeAdapter().nullSafe());
		Gson gson = builder.create();
		return gson;
	}
	
	private static class FileTypeAdapter extends TypeAdapter<File> {
		
		private Map<String, String> env;
		
		private FileTypeAdapter() {
			env = System.getenv();
		}

		@Override
		public void write(JsonWriter out, File value) throws IOException {
			out.value(value.getPath());
		}

		@Override
		public File read(JsonReader in) throws IOException {
			String path = in.nextString();
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
				System.out.println("Path ('"+path+"') contains environmental variable ('"+var+"')");
				Preconditions.checkNotNull(value, "Environmental variable %s not found! Can't build path", var);
				path = path.replace(replaceStr, value);
				System.out.println("\treplacing '"+replaceStr+"' with '"+value+"': "+path);
			}
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
	
	private static class TriggerRuptureTypeAdapter extends TypeAdapter<TriggerRupture> {

		@Override
		public void write(JsonWriter out, TriggerRupture value) throws IOException {
			out.beginObject();
			if (value.customOccurrenceTime != null && value.customOccurrenceTime > Long.MIN_VALUE)
				out.name("occurrenceTimeMillis").value(value.customOccurrenceTime);
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
			
			in.beginObject();
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "occurrenceTimeMillis":
					customOccurrenceTime = in.nextLong();
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
				}
			}
			in.endObject();
			
			if (fssIndex != null) {
				Preconditions.checkState(lat == null && lon == null && depth == null,
						"Cannot specify point source location for FSS rupture");
				Preconditions.checkState(subSects == null, "Cannot specify sub sections for FSS rupture");
				return new TriggerRupture.FSS(fssIndex, customOccurrenceTime, mag);
			}
			if (subSects != null) {
				Preconditions.checkState(lat == null && lon == null && depth == null,
						"Cannot specify point source location for sub sect rupture");
				Preconditions.checkNotNull(mag, "Must specify magnitude for sub sect rupture");
				return new TriggerRupture.SectionBased(subSects, customOccurrenceTime, mag);
			}
			// must be point rupture
			Preconditions.checkNotNull(lat, "Must specify latitude for point source rupture");
			Preconditions.checkNotNull(lon, "Must specify longitude for point source rupture");
			Preconditions.checkNotNull(depth, "Must specify depth for point source rupture");
			Preconditions.checkNotNull(mag, "Must specify magnitude for point source rupture");
			Location hypocenter = new Location(lat, lon, depth);
			
			return new TriggerRupture.Point(hypocenter, customOccurrenceTime, mag);
		}
		
	}
	
	public class BinaryFilteredOutputConfig {
		
		private final String prefix;
		private final Double minMag;
		private final Boolean preserveChainBelowMag;
		private final boolean descendantsOnly;
		
		public BinaryFilteredOutputConfig(String prefix, Double minMag, Boolean preserveChainBelowMag, boolean descendantsOnly) {
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

	public Long getRandomSeed() {
		Preconditions.checkState(randomSeed == null || getNumSimulations() == 1,
				"randomSeed field only applicable for single simulation runs");
		return randomSeed;
	}
	
	public boolean isBinaryOutput() {
		return binaryOutput;
	}
	
	public void setBinaryOutput(boolean binaryOutput) {
		this.binaryOutput = binaryOutput;
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
	
	public boolean isTreatTriggerCatalogAsSpontaneous() {
		if (treatTriggerCatalogAsSpontaneous == null)
			return true;
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

	public U3ETAS_ProbabilityModelOptions getProbModel() {
		return probModel;
	}

	public boolean isApplySubSeisForSupraNucl() {
		return applySubSeisForSupraNucl;
	}

	public double getTotRateScaleFactor() {
		return totRateScaleFactor;
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

	public boolean isImposeGR() {
		return imposeGR;
	}

	public boolean isIncludeIndirectTriggering() {
		return includeIndirectTriggering;
	}

	public double getGridSeisDiscr() {
		return gridSeisDiscr;
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
		Gson gson = buildGson();
		return gson.toJson(this);
	}
	
	public void writeJSON(File jsonFile) throws IOException {
		FileWriter fw = new FileWriter(jsonFile);
		fw.write(toJSON()+"\n");
		fw.close();
	}
	
	public static ETAS_Config readJSON(File jsonFile) throws IOException {
		String json = null;
		for (String line : Files.readLines(jsonFile, Charset.defaultCharset())) {
			if (json == null)
				json = line;
			else
				json += "\n"+line;
		}
		return readJSON(json);
	}
	
	public static ETAS_Config readJSON(String json) {
		Gson gson = buildGson();
		ETAS_Config conf = gson.fromJson(json, ETAS_Config.class);
		return conf;
	}

	public static void main(String[] args) {
		if (args.length == 1) {
			File jsonFile = new File(args[0]);
			try {
				System.out.println("Loading JSON from "+jsonFile);
				ETAS_Config config = readJSON(jsonFile);
				System.out.println("================");
				System.out.println(config.toJSON());
				System.out.println("================");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return;
		}
		int numSimulations = 1000;
		double duration = 10;
		boolean includeSpontaneous = true;
		File cacheDir = new File("/auto/scec-02/kmilner/ucerf3/etas_sim/cache_fm3p1_ba");
		File fssFile = new File("/home/scec-02/kmilner/ucerf3/inversion_compound_plots/2013_05_10-ucerf3p3-production-10runs/"
				+ "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
		File outputDir = new File("/tmp");
		ETAS_Config conf = new ETAS_Config(numSimulations, duration, includeSpontaneous, cacheDir, fssFile, outputDir,
				null, null,
				new TriggerRupture.FSS(1234, 11111243l, 6.5),
				new TriggerRupture.FSS(12345, null, null),
				new TriggerRupture.SectionBased(new int[] {0,5,10}, null, 7.2),
				new TriggerRupture.Point(new Location(34, -118, 5.5), 12321l, 5.7));
		
		String json1 = conf.toJSON();
		System.out.println("Orig JSON");
		System.out.println(json1);
		ETAS_Config conf2 = readJSON(json1);
		System.out.println();
		System.out.println("Re-serialized JSON");
		System.out.println(conf2.toJSON());
	}

}
