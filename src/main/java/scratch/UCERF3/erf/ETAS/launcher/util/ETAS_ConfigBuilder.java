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
import org.dom4j.DocumentException;
import org.opensha.commons.geo.Location;
import org.opensha.commons.util.ClassUtils;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.RuptureSurface;
import org.opensha.sha.faultSurface.SimpleFaultData;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_MaxPointSourceMagParam;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;
import scratch.UCERF3.erf.ETAS.launcher.util.KML_RuptureLoader.KML_Node;
import scratch.UCERF3.utils.FaultSystemIO;

public class ETAS_ConfigBuilder {
	
	public enum HPC_Sites {
		USC_HPC("usc_hpcc_mpj_express.slurm", "usc_hpcc_plot.slurm"),
		TACC_STAMPEDE2("tacc_stampede2_fastmpj.slurm", "tacc_stampede2_plot.slurm"),
		TACC_FRONTERA("tacc_frontera_fastmpj.slurm", "tacc_frontera_plot.slurm");

		private String plotFileName;
		private String fileName;
		
		private HPC_Sites(String fileName, String plotFileName) {
			this.fileName = fileName;
			this.plotFileName = plotFileName;
		}
		
		public String getSlurmFileName() {
			return fileName;
		}
		
		public String getSlurmPlotFileName() {
			return plotFileName;
		}
		
		public File getSlurmFile() {
			return ETAS_Config.resolvePath("${ETAS_LAUNCHER}/parallel/mpj_examples/"+fileName);
		}
		
		public File getSlurmPlotFile() {
			return ETAS_Config.resolvePath("${ETAS_LAUNCHER}/parallel/mpj_examples/"+plotFileName);
		}
	}
	
	static FaultModels FM_DEFAULT = FaultModels.FM3_1;
	static double DURATION_DEFAULT = 10d;
	static U3ETAS_ProbabilityModelOptions PROB_MODEL_DEFAULT = U3ETAS_ProbabilityModelOptions.FULL_TD;
	
	protected static Map<FaultModels, File> fmFSSfileMap;
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
		
		// IF YOU ADD A NEW OPTION, UPDATE DOCUMENTATION IN ucerf3-etas-launcher/CONFIGURING_SIMULATIONS.md
		
		Option numSimsOption = new Option("num", "num-simulations", true, "Number of ETAS simulations to perform, must be >0");
		numSimsOption.setRequired(true);
		ops.addOption(numSimsOption);
		
		Option outputDir = new Option("o", "output-dir", true, "Output dir to write results. If not supplied, directory name "
				+ "will be built automatically with the date, simulation name, and parameters, and placed in $ETAS_SIM_DIR");
		outputDir.setRequired(false);
		ops.addOption(outputDir);
		
		Option parentDir = new Option("pdir", "parent-dir", true, "Parent directory to write results. Directory name "
				+ "will be built automatically with the date, simulation name, and parameters, and placed in this directory.");
		parentDir.setRequired(false);
		ops.addOption(parentDir);
		
		Option nameOption = new Option("n", "name", true, "Simulation name");
		nameOption.setRequired(false);
		ops.addOption(nameOption);

		Option nameAddOption = new Option("na", "name-add", true, "Custom addendum to automatically generated name");
		nameAddOption.setRequired(false);
		ops.addOption(nameAddOption);
		
		/*
		 * Simulation parameters
		 */
		Option seedOption = new Option("rand", "random-seed", true, "Random seed for simulations");
		seedOption.setRequired(false);
		ops.addOption(seedOption);
		
		Option fmOption = new Option("fm", "fault-model", true, "Fault model, one of FM3_1 or FM3_2 (default: "+FM_DEFAULT.name()+")");
		fmOption.setRequired(false);
		ops.addOption(fmOption);
		
		Option spontOption = new Option("sp", "include-spontaneous", false, "If supplied, spontaneous ruptures will be enabled");
		spontOption.setRequired(false);
		ops.addOption(spontOption);
		
		Option histCatalogOption = new Option("hc", "historical-catalog", false, "If supplied, historical catalog will be included");
		histCatalogOption.setRequired(false);
		ops.addOption(histCatalogOption);
		
		Option histCatalogAsSpontaneousOption = new Option("hcas", "historical-catalog-as-spontaneous", false,
				"If supplied, aftershocks which descend from the historical catalog (enabled with --historical-catalog) will be treated "
				+ "identically to spontaneous ruptures for the purposes of output plots and tables");
		histCatalogAsSpontaneousOption.setRequired(false);
		ops.addOption(histCatalogAsSpontaneousOption);
		
		Option durationOption = new Option("dur", "duration-years", true, "Simulation duration (years, default: "+(float)DURATION_DEFAULT+")");
		durationOption.setRequired(false);
		ops.addOption(durationOption);
		
		Option probModelOption = new Option("prob", "prob-model", true,
				"UCERF3-ETAS Probability Model, one of FULL_TD, NO_ERT, or POISSON (default: "+PROB_MODEL_DEFAULT.name()+")");
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
		
		Option kCOVOption = new Option("ek", "etas-k-cov", true, "COV of ETAS productivity parameter parameter, k");
		kCOVOption.setRequired(false);
		ops.addOption(kCOVOption);
		
		Option grOption = new Option("igr", "impose-gr", false, "If supplied, imposeGR will be set to true");
		grOption.setRequired(false);
		ops.addOption(grOption);
		
		Option maxPtSrcMagOption = new Option("ptm", "max-point-src-mag", true,
				"Maximum magnitude for point source ruptures. Random finite rupture surfaces will be assigned above "
				+ "this threshold. (DEFAULT: "+U3ETAS_MaxPointSourceMagParam.DEFAULT_VALUE.floatValue()+")");
		maxPtSrcMagOption.setRequired(false);
		ops.addOption(maxPtSrcMagOption);
		
		/*
		 * HPC options
		 */
		Option hpcSite = new Option("hpc", "hpc-site", true, "HPC site to configure for. Either 'USC_HPC' or 'TACC_STAMPEDE2'");
		hpcSite.setRequired(false);
		ops.addOption(hpcSite);
		
		Option nodesOption = new Option("nds", "nodes", true, "Compute nodes to run on for HPC configuration");
		nodesOption.setRequired(false);
		ops.addOption(nodesOption);
		
		Option hoursOption = new Option("hrs", "hours", true, "Wall-clock hours for HPC configuration");
		hoursOption.setRequired(false);
		ops.addOption(hoursOption);
		
		Option threadsOption = new Option("th", "threads", true, "Threads per node for HPC configuration");
		threadsOption.setRequired(false);
		ops.addOption(threadsOption);
		
		Option queueOption = new Option("qu", "queue", true, "Queue name for HPC configuration");
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
		if (cmd.hasOption("etas-k-cov"))
			ops.add("kCOV="+cmd.getOptionValue("etas-k-cov"));
		if (cmd.hasOption("impose-gr"))
			ops.add("Impose G-R");
		if (cmd.hasOption("max-point-src-mag"))
			ops.add("MaxPtSrcM="+cmd.getOptionValue("max-point-src-mag"));
		
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
		
		Double log10k = doubleArgIfPresent(cmd, "etas-k");
		Double c = doubleArgIfPresent(cmd, "etas-c");
		Double p = doubleArgIfPresent(cmd, "etas-p");
		Double kCOV = doubleArgIfPresent(cmd, "etas-k-cov");
		Double maxPtSrcMag = doubleArgIfPresent(cmd, "max-point-src-mag");
		if (maxPtSrcMag == null)
			maxPtSrcMag = U3ETAS_MaxPointSourceMagParam.DEFAULT_VALUE;
		
		File outputDir;
		if (cmd.hasOption("output-dir")) {
			Preconditions.checkArgument(!cmd.hasOption("parent-dir"),
					"cannot specify both output and parent dirs");
			outputDir = new File(cmd.getOptionValue("output-dir"));
		} else {
			String dirName = df.format(new Date())+"-";
			dirName += getNamePrefix(simulationName);
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
			if (cmd.hasOption("parent-dir"))
				outputDir = new File(cmd.getOptionValue("parent-dir")+"/"+dirName);
			else
				outputDir = new File("${ETAS_SIM_DIR}/"+dirName);
//			System.out.println("Determined output dir: "+outputDir.getPath());
		}
		
		ETAS_Config config = new ETAS_Config(numSimulations, duration, includeSpontaneous, cacheDir, fssFile,
				outputDir, triggerCatalog, triggerCatalogSurfaceMappings, triggerRuptures.toArray(new TriggerRupture[0]));
		config.setSimulationName(simulationName);
		config.setProbModel(probModel);
		config.setTotRateScaleFactor(scaleFactor);
		config.setImposeGR(cmd.hasOption("impose-gr"));
		config.setTreatTriggerCatalogAsSpontaneous(histCatalog && cmd.hasOption("historical-catalog-as-spontaneous"));
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
		config.setETAS_K_COV(kCOV);
		config.setMaxPointSourceMag(maxPtSrcMag);
		
		return config;
	}
	
	public static String getNamePrefix(String simulationName) {
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
		return namePrefix;
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
		
		File plotFile = site.getSlurmPlotFile();
		if (plotFile.exists()) {
			System.out.println("Building SLURM plot file for "+site.name());
			outputFile = new File(outputDir, "plot_results.slurm");
			
			updateSlurmScript(plotFile, outputFile, null, null, null, queue, configFile);
		}
	}
	
	public static void updateSlurmScript(File inputFile, File outputFile, Integer nodes, Integer threads, Integer hours, String queue, File configFile)
			throws IOException {
		updateSlurmScript(inputFile, outputFile, nodes, threads, hours, queue, configFile.getPath());
	}
	
	public static void updateSlurmScript(File inputFile, File outputFile, Integer nodes, Integer threads, Integer hours, String queue, String configFile)
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
				line = "ETAS_CONF_JSON=\""+configFile+"\"";
			
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
		
		Option triggerFSSOption = new Option("fss", "fss-index", true, "Create a trigger rupture with the given FSS index");
		triggerFSSOption.setRequired(false);
		ops.addOption(triggerFSSOption);
		
		Option triggerSectsOption = new Option("fss", "fault-sections", true,
				"Create a trigger rupture with the given UCERF3 fault section indexes (comma separated)");
		triggerSectsOption.setRequired(false);
		ops.addOption(triggerSectsOption);
		
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
	
	protected static void createKMLOptions(Options ops) {
		Option kmlSurfOption = new Option("kml", "kml-surf", true, "KML/KMZ file from which to load rupture trace(s)");
		kmlSurfOption.setRequired(false);
		ops.addOption(kmlSurfOption);
		
		Option kmlUpperDepth = new Option("kud", "kml-surf-upper-depth", true,
				"Upper depth (km) of the kml rupture surface (optional)");
		kmlUpperDepth.setRequired(false);
		ops.addOption(kmlUpperDepth);
		
		Option kmlLowerDepth = new Option("kld", "kml-surf-lower-depth", true,
				"Lower depth (km) of the kml rupture surface (required if kml surface used)");
		kmlLowerDepth.setRequired(false);
		ops.addOption(kmlLowerDepth);
		
		Option kmlDipOption = new Option("kdip", "kml-surf-dip", true, "Dip (degrees) of the primary rupture's finite rupture "
				+ "surface to be constructed by traces in a KML file (optional, otherwise assumes vertical)");
		kmlDipOption.setRequired(false);
		ops.addOption(kmlDipOption);
		
		Option kmlDipDirOption = new Option("kdd", "kml-surf-dip-dir", true,
				"Dip direction (degrees) of the KML rupture surface (optional, otherwise assumes Aki&Richards convention)");
		kmlDipDirOption.setRequired(false);
		ops.addOption(kmlDipDirOption);
		
		Option kmlNameOption = new Option("kmln", "kml-surf-name", true,
				"If supplied, only KML traces which are under an element with the specified name will be included. "
				+ "Repeat this argument multiple times to include multiple possible names (logical OR). If the "
				+ "--kml-surf-name-contains flag is supplied, then it need not be an exact match.");
		kmlNameOption.setRequired(false);
		ops.addOption(kmlNameOption);
		
		Option kmlNameContainsOption = new Option("kmlnc", "kml-surf-name-contains", false,
				"If supplied, then --kml-surf-name option will apply to any KML element which contains the given name, "
				+ "rather than an exact match");
		kmlNameContainsOption.setRequired(false);
		ops.addOption(kmlNameContainsOption);
	}
	
	protected static SimpleFaultData[] loadKMLSurface(CommandLine cmd) {
		if (!cmd.hasOption("kml-surf"))
			return null;
		File kmlFile = new File(cmd.getOptionValue("kml-surf"));
		System.out.println("Loading KML/KMZ file from: "+kmlFile.getAbsolutePath());
		Preconditions.checkArgument(kmlFile.exists(), "KML file doesn't exist!");
		KML_Node node;
		try {
			node = KML_RuptureLoader.parseKML(kmlFile);
		} catch (IOException | DocumentException e) {
			throw new RuntimeException("Error loading KML/KML file", e);
		}
		List<FaultTrace> traces;
		if (cmd.hasOption("kml-surf-name")) {
			// we have a name filter
			String[] names = cmd.getOptionValues("kml-surf-name");
			for (String name : names)
				System.out.println("\tfiltering by name: "+name);
			boolean exactMatch = !cmd.hasOption("kml-surf-name-contains");
			System.out.println("Name exact match? "+ exactMatch);
			traces = KML_RuptureLoader.loadTracesByName(node, exactMatch, names);
		} else {
			traces = KML_RuptureLoader.loadTraces(node);
		}
		System.out.println("Loaded "+traces.size()+" traces");
		Preconditions.checkState(!traces.isEmpty(),
				"No traces found, which means no LineString elements found or filtering by name excluded all traces");
		
		Preconditions.checkArgument(cmd.hasOption("kml-surf-lower-depth"), "Must supply --kml-surf-lower-depth for KML surfaces");
		double lowerDepth = doubleArgIfPresent(cmd, "kml-surf-lower-depth");
		
		Double upperDepth = doubleArgIfPresent(cmd, "kml-surf-upper-depth");
		if (upperDepth == null)
			upperDepth = Double.NaN;
		
		Double dip = doubleArgIfPresent(cmd, "kml-surf-dip");
		if (dip == null)
			dip = 90d;
		
		Double dipDir = doubleArgIfPresent(cmd, "kml-surf-dip-dir");
		if (dipDir == null)
			dipDir = Double.NaN;
		
		return KML_RuptureLoader.buildRuptureSFDs(traces, upperDepth, lowerDepth, dip, dipDir).toArray(new SimpleFaultData[0]);
	}

	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			String argz = "";

//			argz += " --start-year 1919";
			argz += " --start-year 2020";
//			argz += " --num-simulations 100000";
			argz += " --num-simulations 25000";
			argz += " --duration-years 1";
//			argz += " --num-simulations 10000";
//			argz += " --duration-years 10";
			
//			argz += " --num-simulations 1";
//			argz += " --duration-years 100";
			
//			argz += " --gridded-only";
//			argz += " --prob-model NO_ERT";
//			argz += " --include-spontaneous";
//			argz += " --historical-catalog";
//			argz += " --etas-k-cov 1.16";
//			argz += " --etas-k-cov 1.5";
			
//			argz += " --etas-k -2.31 --etas-p 1.08 --etas-c 0.04";
//			argz += " --scale-factor 1.1338";
//			argz += " --scale-factor 1.0";
//			argz += " --scale-factor 1.212";
			
//			argz += " --random-seed 123456789";
			
			argz += " --magnitude 7";
			argz += " --latitude 34.695";
			argz += " --depth 5";
//			argz += " --longitude -118.5";
//			argz += " --name M7OnSAF";
			argz += " --longitude -117.7";
			argz += " --name M7AwaySAF";
			
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
