package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ClassUtils;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;
import scratch.UCERF3.utils.FaultSystemIO;

class ETAS_ConfigBuilder {
	
	public enum HPC_Sites {
		USC_HPC("usc_hpcc_mpj_express.slurm"),
		TACC_STAMPEDE2("tacc_stampede2_fastmpj.slurm");
		
		private String fileName;
		
		private HPC_Sites(String fileName) {
			this.fileName = fileName;
		}
		
		public String getSlurmFileName() {
			return fileName;
		}
		
		public File getSlurmFile() {
			return ETAS_Config.resolvePath("${ETAS_LAUNCHER}/parallel/mpj_examples/"+fileName);
		}
	}
	
	static FaultModels FM_DEFAULT = FaultModels.FM3_1;
	static double DURATION_DEFAULT = 10d;
	static U3ETAS_ProbabilityModelOptions PROB_MODEL_DEFAULT = U3ETAS_ProbabilityModelOptions.FULL_TD;
	
	private static Map<FaultModels, File> fmFSSfileMap;
	private static Map<FaultModels, File> fmCacheDirMap;
	private static Map<U3ETAS_ProbabilityModelOptions, Double> probModelScaleMap;
	
	private static String INPUTS_DIR = "${ETAS_LAUNCHER}/inputs";
	
	static {
		fmFSSfileMap = new HashMap<>();
		fmFSSfileMap.put(FaultModels.FM2_1, new File(INPUTS_DIR+"/ucerf2_mapped_fm3p1.zip"));
		fmFSSfileMap.put(FaultModels.FM3_1, new File(INPUTS_DIR+"/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_"
				+ "FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"));
		fmFSSfileMap.put(FaultModels.FM3_2, new File(INPUTS_DIR+"/2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_"
				+ "FM3_2_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip"));
		
		fmCacheDirMap = new HashMap<>();
		fmCacheDirMap.put(FaultModels.FM2_1, new File(INPUTS_DIR+"/cache_u2_mapped_fm3p1"));
		fmCacheDirMap.put(FaultModels.FM3_1, new File(INPUTS_DIR+"/cache_fm3p1_ba"));
		fmCacheDirMap.put(FaultModels.FM3_2, new File(INPUTS_DIR+"/cache_fm3p2_ba"));
		
		probModelScaleMap = new HashMap<>();
		probModelScaleMap.put(U3ETAS_ProbabilityModelOptions.FULL_TD, 1.14);
		probModelScaleMap.put(U3ETAS_ProbabilityModelOptions.NO_ERT, 1d);
		probModelScaleMap.put(U3ETAS_ProbabilityModelOptions.POISSON, 1d);
	}
	
	public static final DateFormat df = new SimpleDateFormat("yyyy_MM_dd");
	
	public static Options getCommonOptions() {
		Options ops = new Options();
		
		Option outputDir = new Option("o", "output-dir", true, "Output dir to write results. If not supplied, directory name "
				+ "will be built automatically with the date, simulation name, and parameters, and placed in $ETAS_SIM_DIR");
		outputDir.setRequired(false);
		ops.addOption(outputDir);
		
		/*
		 * Simulation parameters
		 */
		Option seedOption = new Option("rand", "random-seed", true, "Random seed for simulations");
		seedOption.setRequired(false);
		ops.addOption(seedOption);
		
		Option fmOption = new Option("fm", "fault-model", true, "Fault model (default: "+FM_DEFAULT.name()+")");
		fmOption.setRequired(false);
		ops.addOption(fmOption);
		
		Option spontOption = new Option("sp", "include-spontaneous", false, "If supplied, includeSpontaneous will be set to true");
		spontOption.setRequired(false);
		ops.addOption(spontOption);
		
		Option histCatalogOption = new Option("hc", "historical-catalog", false, "If supplied, historical catalog will be included");
		histCatalogOption.setRequired(false);
		ops.addOption(histCatalogOption);
		
		Option numSimsOption = new Option("num", "num-simulations", true, "Number of simulations");
		numSimsOption.setRequired(true);
		ops.addOption(numSimsOption);
		
		Option durationOption = new Option("dur", "duration-years", true, "Simulation duration (years, default: "+(float)DURATION_DEFAULT+")");
		durationOption.setRequired(false);
		ops.addOption(durationOption);
		
		Option probModelOption = new Option("prob", "prob-model", true, "Probability Model (default: "+PROB_MODEL_DEFAULT.name()+")");
		probModelOption.setRequired(false);
		ops.addOption(probModelOption);
		
		Option scaleOption = new Option("scale", "scale-factor", true, "Total rate scale factor. Default is determined from probability model");
		scaleOption.setRequired(false);
		ops.addOption(scaleOption);
		
		Option griddedOnlyOption = new Option("go", "gridded-only", false, "Flag for gridded only (no-faults) ETAS. Will also change the default "
				+ "probability model to POISSON");
		griddedOnlyOption.setRequired(false);
		ops.addOption(griddedOnlyOption);
		
		Option kOption = new Option("ek", "etas-k", true, "ETAS productivity parameter parameter, k, in Log10 units");
		kOption.setRequired(false);
		ops.addOption(kOption);
		
		Option pOption = new Option("ep", "etas-p", true, "ETAS temporal decay paramter, p");
		pOption.setRequired(false);
		ops.addOption(pOption);
		
		Option cOption = new Option("ec", "etas-c", true, "ETAS minimum time paramter, c, in days");
		cOption.setRequired(false);
		ops.addOption(cOption);
		
		/*
		 * HPC options
		 */
		Option hpcSite = new Option("hpc", "hpc-site", true, "HPC site to configure for. Either 'USC_HPC' or 'TACC_STAMPEDE2'");
		hpcSite.setRequired(false);
		ops.addOption(hpcSite);
		
		Option nodesOption = new Option("nds", "nodes", true, "Nodes for HPC configuration");
		nodesOption.setRequired(false);
		ops.addOption(nodesOption);
		
		Option hoursOption = new Option("hrs", "hours", true, "Hours for HPC configuration");
		hoursOption.setRequired(false);
		ops.addOption(hoursOption);
		
		Option threadsOption = new Option("th", "threads", true, "Threads for HPC configuration");
		threadsOption.setRequired(false);
		ops.addOption(threadsOption);
		
		Option queueOption = new Option("qu", "queue", true, "Queue for HPC configuration");
		queueOption.setRequired(false);
		ops.addOption(queueOption);
		
		return ops;
	}
	
	protected static List<String> getNonDefaultOptionsStrings(CommandLine cmd) {
		List<String> ops = new ArrayList<>();
		boolean griddedOnly = cmd.hasOption("gridded-only");
		U3ETAS_ProbabilityModelOptions probModel = cmd.hasOption("prob-model") ?
				U3ETAS_ProbabilityModelOptions.valueOf(cmd.getOptionValue("prob-model")) : PROB_MODEL_DEFAULT;
		double scaleFactor = cmd.hasOption("scale-factor") ?
				Double.parseDouble(cmd.getOptionValue("scale-factor")) : probModelScaleMap.get(probModel);
		FaultModels fm = cmd.hasOption("fault-model") ?
				FaultModels.valueOf(cmd.getOptionValue("fault-model")) : FM_DEFAULT;
		
		if (cmd.hasOption("etas-k"))
			ops.add("Log10(k)="+cmd.getOptionValue("etas-k"));
		if (cmd.hasOption("etas-p"))
			ops.add("p="+cmd.getOptionValue("etas-p"));
		if (cmd.hasOption("etas-c"))
			ops.add("c="+cmd.getOptionValue("etas-c"));
		
		if (fm != FM_DEFAULT)
			ops.add(fm.getShortName());
		if (griddedOnly)
			ops.add("No Faults");
		if (probModel != PROB_MODEL_DEFAULT && !griddedOnly)
			ops.add(probModel.toString());
		if (cmd.hasOption("scale-factor") && (float)scaleFactor != probModelScaleMap.get(probModel).floatValue())
			ops.add("Scale Factor "+(float)scaleFactor);
		if (cmd.hasOption("include-spontaneous"))
			ops.add("Spontaneous");
		if (cmd.hasOption("historical-catalog"))
			ops.add("Historical Catalog");
		
		return ops;
	}
	
	protected static FaultModels getFM(CommandLine cmd) {
		return cmd.hasOption("fault-model") ? FaultModels.valueOf(cmd.getOptionValue("fault-model")) : FM_DEFAULT;
	}
	
	public static ETAS_Config buildBasicConfig(CommandLine cmd, String simulationName, List<TriggerRupture> triggerRuptures,
			String configCommand) {
		int numSimulations = Integer.parseInt(cmd.getOptionValue("num-simulations"));
		double duration = cmd.hasOption("duration-years") ? Double.parseDouble(cmd.getOptionValue("duration-years")) : DURATION_DEFAULT;
		boolean includeSpontaneous = cmd.hasOption("include-spontaneous");
		
		FaultModels fm = getFM(cmd);
		
		boolean griddedOnly = cmd.hasOption("gridded-only");
		U3ETAS_ProbabilityModelOptions probModel = cmd.hasOption("prob-model") ?
				U3ETAS_ProbabilityModelOptions.valueOf(cmd.getOptionValue("prob-model")) : PROB_MODEL_DEFAULT;
		if (griddedOnly)
			probModel = U3ETAS_ProbabilityModelOptions.POISSON;
		double scaleFactor = cmd.hasOption("scale-factor") ? Double.parseDouble(cmd.getOptionValue("scale-factor"))
				: probModelScaleMap.get(probModel);
		
		boolean histCatalog = cmd.hasOption("historical-catalog");
		File triggerCatalog = null;
		File triggerCatalogSurfaceMappings = null;
		if (histCatalog) {
			triggerCatalog = new File(INPUTS_DIR+"/u3_historical_catalog.txt");
			triggerCatalogSurfaceMappings = new File(INPUTS_DIR+"/u3_historical_catalog_finite_fault_mappings.xml");
		}
		
		File cacheDir = fmCacheDirMap.get(fm);
		File fssFile = fmFSSfileMap.get(fm);
		
		Double log10k = cmd.hasOption("etas-k") ? Double.parseDouble(cmd.getOptionValue("etas-k")) : null;
		Double c = cmd.hasOption("etas-c") ? Double.parseDouble(cmd.getOptionValue("etas-c")) : null;
		Double p = cmd.hasOption("etas-p") ? Double.parseDouble(cmd.getOptionValue("etas-p")) : null;
		
		File outputDir;
		if (cmd.hasOption("output-dir")) {
			outputDir = new File(cmd.getOptionValue("output-dir")).getAbsoluteFile();
		} else {
			String dirName = df.format(new Date())+"-";
			String namePrefix = simulationName.replaceAll("\\.", "p");
			namePrefix = namePrefix.replaceAll("\\(", "_");
			namePrefix = namePrefix.replaceAll("\\)", "_");
			namePrefix = namePrefix.replaceAll(",", "_");
			namePrefix = namePrefix.replaceAll("\\W+", "");
			while (namePrefix.startsWith("_"))
				namePrefix = namePrefix.substring(1);
			while (namePrefix.endsWith("_"))
				namePrefix = namePrefix.substring(0, namePrefix.length()-1);
			while (namePrefix.contains("__"))
				namePrefix = namePrefix.replaceAll("__", "_");
			dirName += namePrefix;
			if (dirName.length() > 100)
				dirName = dirName.substring(0, 100);
//			if (includeSpontaneous)
//				dirName += "-includeSpont";
//			else
//				dirName += "-noSpont";
//			if (histCatalog)
//				dirName += "-histCatalog";
//			dirName += "-"+probModel.name().toLowerCase();
//			if (scaleFactor != 1d)
//				dirName += "-scale"+(float)scaleFactor;
//			if (log10k != null || p != null || c != null)
//				dirName += "-modParams";
//			if (griddedOnly)
//				dirName += "-griddedOnly";
			outputDir = new File("${ETAS_SIM_DIR}/"+dirName); 
//			System.out.println("Determined output dir: "+outputDir.getPath());
		}
		
		ETAS_Config config = new ETAS_Config(numSimulations, duration, includeSpontaneous, cacheDir, fssFile,
				outputDir, triggerCatalog, triggerCatalogSurfaceMappings, triggerRuptures.toArray(new TriggerRupture[0]));
		config.setSimulationName(simulationName);
		config.setProbModel(probModel);
		config.setTotRateScaleFactor(scaleFactor);
		long curTime = System.currentTimeMillis();
		if (cmd.hasOption("random-seed"))
			config.setRandomSeed(Long.parseLong(cmd.getOptionValue("random-seed")));
		else
			config.setRandomSeed(curTime);
		config.setGriddedOnly(griddedOnly);
		config.setConfigCommand(configCommand);
		config.setConfigTime(curTime);
		config.setETAS_Log10_K(log10k);
		config.setETAS_C(c);
		config.setETAS_P(p);
		
		return config;
	}
	
	public static void checkWriteHPC(ETAS_Config config, File configFile, CommandLine cmd) throws IOException {
		if (!cmd.hasOption("hpc-site"))
			return;
		
		HPC_Sites site = HPC_Sites.valueOf(cmd.getOptionValue("hpc-site"));
		System.out.println("Building SLURM submit script for "+site.name());
		File inputFile = site.getSlurmFile();
		
		File outputDir = ETAS_Config.resolvePath(config.getOutputDir());
		File outputFile = new File(outputDir, "etas_sim_mpj.slurm");
		
		Integer nodes = cmd.hasOption("nodes") ? Integer.parseInt(cmd.getOptionValue("nodes")) : null;
		Integer hours = cmd.hasOption("hours") ? Integer.parseInt(cmd.getOptionValue("hours")) : null;
		Integer threads = cmd.hasOption("threads") ? Integer.parseInt(cmd.getOptionValue("threads")) : null;
		String queue = cmd.hasOption("queue") ? cmd.getOptionValue("queue") : null;
		
		updateSlurmScript(inputFile, outputFile, nodes, threads, hours, queue, configFile);
	}
	
	public static void updateSlurmScript(File inputFile, File outputFile, Integer nodes, Integer threads, Integer hours, String queue, File configFile)
			throws IOException {
		List<String> lines = new ArrayList<>();
		
		boolean nodeLineFound = true;
		int lastIndexSBATCH = -1;
		
		for (String line : Files.readLines(inputFile, Charset.defaultCharset())) {
			String tline = line.trim();
			if (tline.startsWith("#SBATCH -t") && hours != null)
				line = "#SBATCH -t "+hours+":00:00";
			
			if (tline.startsWith("#SBATCH -N") && nodes != null)
				line = "#SBATCH -N "+nodes;
			
			if (tline.startsWith("#SBATCH -n") && nodes != null) {
				int cores = threads == null ? nodes : nodes*threads;
				line = "#SBATCH -n "+cores;
			}
			
			if (tline.startsWith("#SBATCH -p")) {
				nodeLineFound = true;
				if (queue != null)
					line = "#SBATCH -p "+queue;
			}
			
			if (tline.startsWith("ETAS_CONF_JSON="))
				line = "ETAS_CONF_JSON="+configFile.getPath();
			
			if (tline.startsWith("#SBATCH"))
				lastIndexSBATCH = lines.size();
			
			if (threads != null && tline.startsWith("THREADS="))
				line = "THREADS="+threads;
			
			lines.add(line);
		}
		
		Preconditions.checkState(lastIndexSBATCH >= 0);
		
		if (queue != null && !nodeLineFound) {
			// need to add it
			lines.add(lastIndexSBATCH+1, "#SBATCH -p "+queue);
		}
		
		FileWriter fw = new FileWriter(outputFile);
		
		for (String line : lines)
			fw.write(line+"\n");
		
		fw.close();
	}
	
	private static Options createOptions() {
		Options ops = getCommonOptions();

		Option startYearOption = new Option("yr", "start-year", true, "Start year for simulation (must supply this or --start-time)");
		startYearOption.setRequired(false);
		ops.addOption(startYearOption);

		Option startTimeOption = new Option("yr", "start-time", true, "Start time for simulation expressed in epoch milliseconds "
				+ "(must supply this or --start-year)");
		startTimeOption.setRequired(false);
		ops.addOption(startTimeOption);

		Option triggerTimeOption = new Option("tt", "trigger-time", true, "Occurrence time of trigger rupture expressed in epoch milliseconds. "
				+ "If not supplied, trigger rupture occurs immediately before simulation start");
		triggerTimeOption.setRequired(false);
		ops.addOption(triggerTimeOption);

		Option nameOption = new Option("n", "name", true, "Simulation name");
		nameOption.setRequired(false);
		ops.addOption(nameOption);

		Option nameAddOption = new Option("na", "name-add", true, "Custom addendum to automatically generated name");
		nameAddOption.setRequired(false);
		ops.addOption(nameAddOption);
		
		Option triggerFSSOption = new Option("fss", "fss-index", true, "Create a trigger rupture with the given FSS index");
		triggerFSSOption.setRequired(false);
		ops.addOption(triggerFSSOption);
		
		Option triggerMagOption = new Option("mag", "magnitude", true, "Magnitude for trigger event. Required if point source, "
				+ "optional if FSS index (will override UCERF3 magnitude)");
		triggerMagOption.setRequired(false);
		ops.addOption(triggerMagOption);
		
		Option triggerLatOption = new Option("lat", "latitude", true, "Latitude for trigger event (point source). Must also "
				+ "supply longitude, depth, and magnitude");
		triggerLatOption.setRequired(false);
		ops.addOption(triggerLatOption);
		
		Option triggerLonOption = new Option("lon", "longitude", true, "Longitude for trigger event (point source). Must also "
				+ "supply latitude, depth, and magnitude");
		triggerLonOption.setRequired(false);
		ops.addOption(triggerLonOption);
		
		Option triggerDepthOption = new Option("dep", "depth", true, "Depth (km) for trigger event (point source). Must also "
				+ "supply latitude, longitude, and magnitude");
		triggerDepthOption.setRequired(false);
		ops.addOption(triggerDepthOption);
		
		return ops;
	}
	
	protected static Double doubleArgIfPresent(CommandLine cmd, String opt) {
		return cmd.hasOption(opt) ? Double.parseDouble(cmd.getOptionValue(opt)) : null;
	}
	
	protected static Integer intArgIfPresent(CommandLine cmd, String opt) {
		return cmd.hasOption(opt) ? Integer.parseInt(cmd.getOptionValue(opt)) : null;
	}
	
	protected static Long longArgIfPresent(CommandLine cmd, String opt) {
		return cmd.hasOption(opt) ? Long.parseLong(cmd.getOptionValue(opt)) : null;
	}
	
	static final DecimalFormat optionalDigitDF = new DecimalFormat("0.##");

	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			String argz = "";

//			argz += " --start-year 1919";
			argz += " --start-year 2012";
//			argz += " --num-simulations 100000";
			argz += " --num-simulations 1000";
			argz += " --duration-years 500";
//			argz += " --gridded-only";
			argz += " --prob-model NO_ERT";
			argz += " --include-spontaneous";
			argz += " --historical-catalog";
			
//			argz += " --etas-k -2.31 --etas-p 1.08 --etas-c 0.04";
//			argz += " --scale-factor 1.1338";
			argz += " --scale-factor 1.0";
//			argz += " --scale-factor 1.212";
			
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
			TriggerRupture triggerRup = null;
			
			Integer fssIndex = intArgIfPresent(cmd, "fss-index");
			Double lat = doubleArgIfPresent(cmd, "latitude");
			Double lon = doubleArgIfPresent(cmd, "longitude");
			Double depth = doubleArgIfPresent(cmd, "depth");
			Double mag = doubleArgIfPresent(cmd, "mag");
			Long customOccurrenceTime = longArgIfPresent(cmd, "trigger-time");
			
			if (fssIndex != null) {
				Preconditions.checkState(lat == null && lon == null && depth == null,
						"Can't supply FSS index and point source location");
				triggerRup = new TriggerRupture.FSS(fssIndex, customOccurrenceTime, mag);
			} else if (lat != null) {
				Preconditions.checkState(lon != null && depth != null && mag != null,
						"Must supply all of lat, lon, depth, and magnitude for point source");
				Location hypocenter = new Location(lat, lon, depth);
				triggerRup = new TriggerRupture.Point(hypocenter, customOccurrenceTime, mag);
			} else {
				Preconditions.checkState(lon == null && depth == null && mag == null,
						"Must supply all of lat, lon, depth, and magnitude for point source");
			}
			
			Integer startYear = intArgIfPresent(cmd, "start-year");
			Long startTime = longArgIfPresent(cmd, "start-time");
			Preconditions.checkState(startYear != null || startTime != null, "Must supply either start time or start year");
			Preconditions.checkState(!(startYear != null && startTime != null), "Can't supply both start time and start year");
			
			String name;
			if (cmd.hasOption("name")) {
				Preconditions.checkArgument(!cmd.hasOption("name-add"), "Can't supply both --name and --name-add");
				name = cmd.getOptionValue("name");
			} else {
				if (fssIndex != null && mag == null) {
					// determine magnitude
					FaultModels fm = getFM(cmd);
					File fssFile = fmFSSfileMap.get(fm);
					if (fssFile != null) {
						fssFile = ETAS_Config.resolvePath(fssFile);
					}
					if (fssFile.exists()) {
						FaultSystemSolution fss;
						try {
							fss = FaultSystemIO.loadSol(fssFile);
							mag = fss.getRupSet().getMagForRup(fssIndex);
						} catch (Exception e) {
							System.err.println("Error determining magnitude for FSS rupture");
							e.printStackTrace();
						}
					}
				}
				
				if (fssIndex != null) {
					name = "FSS Rupture "+fssIndex;
					if (mag != null)
						name += ", M"+mag.floatValue();
				} else if (triggerRup != null) {
					name = "Point Source, M"+mag.floatValue();
				} else {
					name = "";
				}
				if (!name.isEmpty())
					name += ", ";
				if (startYear != null)
					name += "Start "+startYear;
				else
					name += "Start "+new SimpleDateFormat("yyyy/MM/dd").format(new Date(startTime));
				Double duration = doubleArgIfPresent(cmd, "duration-years");
				if (duration == null)
					duration = DURATION_DEFAULT;
				name += ", "+optionalDigitDF.format(duration)+" yr";
				List<String> nonDefaultOptions = ETAS_ConfigBuilder.getNonDefaultOptionsStrings(cmd);
				if (nonDefaultOptions != null && !nonDefaultOptions.isEmpty())
					name += ", "+Joiner.on(", ").join(nonDefaultOptions);
				if (cmd.hasOption("name-add")) {
					String nameAdd = cmd.getOptionValue("name-add");
					if (!nameAdd.startsWith(","))
						nameAdd = ", "+nameAdd;
					name += nameAdd;
				}
			}
			System.out.println("Simulation name: "+name);
			
			String configCommand = "u3etas_config_builder.sh "+Joiner.on(" ").join(args);
			
			List<TriggerRupture> triggerRuptures = new ArrayList<>();
			if (triggerRup != null)
				triggerRuptures.add(triggerRup);
			
			ETAS_Config config = ETAS_ConfigBuilder.buildBasicConfig(cmd, name, triggerRuptures, configCommand);
			if (startYear != null)
				config.setStartYear(startYear);
			else
				config.setStartTimeMillis(startTime);
			
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
			
			ETAS_ConfigBuilder.checkWriteHPC(config, relativeConfigFile, cmd);
			
			System.out.println("DONE");
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

}
