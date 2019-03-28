package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opensha.commons.geo.Location;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;

public class ETAS_ConfigGenerator {
	
	private enum HPC_Sites {
		HPC("usc_hpcc_mpj_express.slurm"),
		Stampede2("tacc_stampede2_fastmpj.slurm");
		
		private String fileName;
		
		private HPC_Sites(String fileName) {
			this.fileName = fileName;
		}
	}
	
	@SuppressWarnings("unused")
	public static void main(String[] args) throws IOException {
		boolean mpj = true;
		HPC_Sites hpcSite = HPC_Sites.HPC;
		
		FaultModels fm = FaultModels.FM3_1;
		boolean u2 = false;
		Integer startYear = 1919;
		Long startTimeMillis = null;
		boolean histCatalog = true;
		boolean includeSpontaneous = true;
		int numSimulations = 1000;
		double duration = 100d;
		Long randomSeed = null;
		
		// etas params
		Double p = 1.08;
		Double c = 0.04;
		Double log10k = -2.31;
		
		TriggerRupture[] triggerRups = null;
//		String scenarioName = "Spontaneous";
		String scenarioName = "Historical1919_critical";
		String customCatalogName = null; // null if disabled, otherwise file name within submit dir
		
		String nameAdd = null;
		
//		TriggerRupture[] triggerRups = { new TriggerRupture.FSS(193821) };
//		String scenarioName = "Mojave M7";
//		String customCatalogName = null; // null if disabled, otherwise file name within submit dir
		
//		TriggerRupture[] triggerRups = { new TriggerRupture.Point(new Location(33.3172,-115.728, 5.96), null, 4.8) };
//		String scenarioName = "2009 Bombay Beach M4.8";
//		String customCatalogName = null; // null if disabled, otherwise file name within submit dir
		
//		TriggerRupture[] triggerRups = { new TriggerRupture.Point(new Location(33.3172,-115.728, 5.96), null, 6) };
//		String scenarioName = "2009 Bombay Beach M6";
//		String customCatalogName = null; // null if disabled, otherwise file name within submit dir
		
//		TriggerRupture[] triggerRups = { new TriggerRupture.FSS(30473, null, 6d) };
//		String scenarioName = "Parkfield M6";
//		String customCatalogName = null; // null if disabled, otherwise file name within submit dir
		
//		TriggerRupture[] triggerRups = { new TriggerRupture.Point(new Location(34.42295,-117.80177,5.8), null, 6) };
//		String scenarioName = "Mojave Point M6";
//		String customCatalogName = null; // null if disabled, otherwise file name within submit dir
		
		// only if mpj == true
		int nodes = 36;
		int hours = 24;
		String queue = "scec";
//		String queue = "scec_hiprio";
//		Integer threads = null;
		
		Integer threads = 8;
		randomSeed = 123456789l;
		nameAdd = threads+"threads";
		
		File mainOutputDir = new File("${ETAS_SIM_DIR}");
		File launcherDir = new File("${ETAS_LAUNCHER}");
		File inputsDir = new File(launcherDir, "inputs");
		File cacheDir;
		File fssFile;
		switch (fm) {
		case FM3_1:
			if (u2) {
				cacheDir = new File(inputsDir, "cache_u2_mapped_fm3p1");
				fssFile = new File(inputsDir, "ucerf2_mapped_fm3p1.zip");
			} else {
				cacheDir = new File(inputsDir, "cache_fm3p1_ba");
				fssFile = new File(inputsDir, "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
			}
			break;
		case FM3_2:
			Preconditions.checkState(!u2);
			cacheDir = new File(inputsDir, "cache_fm3p2_ba");
			fssFile = new File(inputsDir, "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_2_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
			break;

		default:
			throw new IllegalStateException();
		}
		
		File triggerCatalog = null;
		File triggerCatalogSurfaceMappings = null;
		Preconditions.checkState(customCatalogName == null || !histCatalog, "Can't have both custom catalog and historical catalog");
		if (histCatalog) {
			triggerCatalog = new File(inputsDir, "u3_historical_catalog.txt");
			triggerCatalogSurfaceMappings = new File(inputsDir, "u3_historical_catalog_finite_fault_mappings.xml");
		}
		
		String dateStr = df.format(new Date());
		
		Preconditions.checkNotNull(scenarioName);
		
		String scenarioFileName = scenarioName.replaceAll(" ", "-").replaceAll("\\W+", "");
		if (u2) {
			if (fm == FaultModels.FM2_1)
				scenarioFileName += "-u2";
			else
				scenarioFileName += "-u2mapped";
		}
		if (includeSpontaneous)
			scenarioFileName += "-includeSpont";
		else
			scenarioFileName += "-noSpont";
		if (histCatalog)
			scenarioFileName += "-historicalCatalog";
		scenarioFileName += "-"+ETAS_AbstractPlot.getTimeShortLabel(duration).replaceAll(" ", "");
		if (nameAdd != null && !nameAdd.isEmpty())
			scenarioFileName += "-"+nameAdd;
		
		String jobName = dateStr+"-"+scenarioFileName;
		
		System.out.println("Job dir name: "+jobName);
		
		File outputDir = new File(mainOutputDir, jobName);
		File localOutputDir = ETAS_Config.resolvePath(outputDir.getPath());
		Preconditions.checkState(localOutputDir.exists() || localOutputDir.mkdir());
		
		ETAS_Config config = new ETAS_Config(numSimulations, duration, includeSpontaneous, cacheDir, fssFile, outputDir,
				triggerCatalog, triggerCatalogSurfaceMappings, triggerRups);
		config.setSimulationName(scenarioName);
		config.setRandomSeed(randomSeed);
		Preconditions.checkState(startYear == null || startTimeMillis == null, "cannot supply both startYear and startTimeMillis");
		Preconditions.checkState(startYear != null || startTimeMillis != null, "must supply either startYear and startTimeMillis");
		if (startYear != null)
			config.setStartYear(startYear);
		else
			config.setStartTimeMillis(startTimeMillis);
		if (p != null)
			config.setETAS_P(p);
		if (c != null)
			config.setETAS_C(c);
		if (log10k != null)
			config.setETAS_Log10_K(log10k);
		
		File configFile = new File(outputDir, "config.json");
		File localConfFile = new File(localOutputDir, "config.json");
		config.writeJSON(localConfFile);
		
		if (mpj) {
			File templateDir = ETAS_Config.resolvePath(new File(new File(launcherDir, "parallel"), "mpj_examples").getPath());
			File slurmScriptFile = new File(localOutputDir, "etas_sim_mpj.slurm");
			File template = new File(templateDir, hpcSite.fileName);
			updateSlurmScript(template, slurmScriptFile, nodes, threads, hours, queue, configFile);
		}
	}
	
	private static void updateSlurmScript(File inputFile, File outputFile, int nodes, Integer threads, int hours, String queue, File configFile)
			throws IOException {
		List<String> lines = new ArrayList<>();
		
		boolean nodeLineFound = true;
		int lastIndexSBATCH = -1;
		
		for (String line : Files.readLines(inputFile, Charset.defaultCharset())) {
			line = line.trim();
			if (line.startsWith("#SBATCH -t"))
				line = "#SBATCH -t "+hours+":00:00";
			
			if (line.startsWith("#SBATCH -N"))
				line = "#SBATCH -N "+nodes;
			
			if (line.startsWith("#SBATCH -p")) {
				nodeLineFound = true;
				if (queue == null)
					// no queue
					continue;
				else
					line = "#SBATCH -p "+queue;
			}
			
			if (line.startsWith("ETAS_CONF_JSON="))
				line = "ETAS_CONF_JSON="+configFile.getPath();
			
			if (line.startsWith("#SBATCH"))
				lastIndexSBATCH = lines.size();
			
			if (threads != null && line.startsWith("THREADS="))
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
	
	public static final DateFormat df = new SimpleDateFormat("yyyy_MM_dd");

}
