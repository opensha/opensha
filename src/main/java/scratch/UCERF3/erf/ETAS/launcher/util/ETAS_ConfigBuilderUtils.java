package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opensha.sha.earthquake.param.ProbabilityModelOptions;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;

class ETAS_ConfigBuilderUtils {
	
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
		
		Option fmOption = new Option("fm", "fault-model", false, "Fault model (default: "+FM_DEFAULT.name()+")");
		fmOption.setRequired(false);
		ops.addOption(fmOption);
		
		Option spontOption = new Option("sp", "include-spontaneous", false, "If supplied, includeSpontaneous will be set to true");
		spontOption.setRequired(false);
		ops.addOption(spontOption);
		
		Option histCatalogOption = new Option("hc", "historical-catalog", false, "If supplied, includeSpontaneous will be set to true");
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
		if (griddedOnly)
			ops.add("No Faults");
		if (probModel != PROB_MODEL_DEFAULT && !griddedOnly)
			ops.add(probModel.toString());
		
		return ops;
	}
	
	public static ETAS_Config buildBasicConfig(CommandLine cmd, String simulationName, List<TriggerRupture> triggerRuptures,
			String configCommand) {
		int numSimulations = Integer.parseInt(cmd.getOptionValue("num-simulations"));
		double duration = cmd.hasOption("duration-years") ? Double.parseDouble(cmd.getOptionValue("duration-years")) : DURATION_DEFAULT;
		boolean includeSpontaneous = cmd.hasOption("include-spontaneous");
		
		FaultModels fm = cmd.hasOption("fault-model") ? FaultModels.valueOf(cmd.getOptionValue("fault-model")) : FM_DEFAULT;
		
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
			if (includeSpontaneous)
				dirName += "-includeSpont";
			else
				dirName += "-noSpont";
			if (histCatalog)
				dirName += "-histCatalog";
			dirName += "-"+probModel.name().toLowerCase();
			if (scaleFactor != 1d)
				dirName += "-scale"+(float)scaleFactor;
			if (griddedOnly)
				dirName += "-griddedOnly";
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

}
