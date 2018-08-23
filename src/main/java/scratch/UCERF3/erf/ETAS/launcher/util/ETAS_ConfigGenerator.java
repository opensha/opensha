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

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;

public class ETAS_ConfigGenerator {
	
	@SuppressWarnings("unused")
	public static void main(String[] args) throws IOException {
		boolean mpj = false;
		boolean hpcc = false;
		
		Integer startYear = 2012;
		Long startTimeMillis = null;
		boolean histCatalog = true;
		boolean includeSpontaneous = true;
		int numSimulations = 1000;
		double duration = 10d;
		
		TriggerRupture[] triggerRups = null;
		String scenarioName = "Spontaneous";
		String customCatalogName = null; // null if disabled, otherwise file name within submit dir
		
//		TriggerRupture[] triggerRups = { new TriggerRupture.FSS(193821) };
//		String scenarioName = "Mojave M7";
//		String customCatalogName = null; // null if disabled, otherwise file name within submit dir
		
		// only if mpj == true
		int nodes = 18;
		int hours = 24;
		String queue = "scec";
		
		File mainOutputDir, launcherDir, localMainOutputDir;
		
		if (hpcc) {
			launcherDir = new File("/home/scec-02/kmilner/ucerf3-etas-launcher/");
			mainOutputDir = new File("/home/scec-02/kmilner/ucerf3/etas_sim/");
			localMainOutputDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/");
		} else {
			launcherDir = new File("/home/kevin/git/ucerf3-etas-launcher/");
			mainOutputDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/");
			localMainOutputDir = mainOutputDir;
			Preconditions.checkState(!mpj);
		}
		File cacheDir = new File(launcherDir, "cache_fm3p1_ba");
		File fssFile = new File(launcherDir, "2013_05_10-ucerf3p3-production-10runs_COMPOUND_SOL_FM3_1_SpatSeisU3_MEAN_BRANCH_AVG_SOL.zip");
		File triggerCatalog = null;
		File triggerCatalogSurfaceMappings = null;
		Preconditions.checkState(customCatalogName == null || !histCatalog, "Can't have both custom catalog and historical catalog");
		if (histCatalog) {
			triggerCatalog = new File(launcherDir, "u3_historical_catalog.txt");
			triggerCatalogSurfaceMappings = new File(launcherDir, "u3_historical_catalog_finite_fault_mappings.xml");
		}
		
		String dateStr = df.format(new Date());
		
		Preconditions.checkNotNull(scenarioName);
		
		String scenarioFileName = scenarioName.replaceAll(" ", "-").replaceAll("\\W+", "");
		if (includeSpontaneous)
			scenarioFileName += "-includeSpont";
		else
			scenarioFileName += "-noSpont";
		if (histCatalog)
			scenarioFileName += "-historicalCatalog";
		scenarioFileName += "-"+ETAS_AbstractPlot.getTimeShortLabel(duration).replaceAll(" ", "");
		
		String jobName = dateStr+"-"+scenarioFileName;
		
		System.out.println("Job dir name: "+jobName);
		
		File outputDir = new File(mainOutputDir, jobName);
		File localOutputDir = new File(localMainOutputDir, jobName);
		Preconditions.checkState(localOutputDir.exists() || localOutputDir.mkdir());
		
		ETAS_Config config = new ETAS_Config(numSimulations, duration, includeSpontaneous, cacheDir, fssFile, outputDir,
				triggerCatalog, triggerCatalogSurfaceMappings, triggerRups);
		config.setSimulationName(scenarioName);
		Preconditions.checkState(startYear == null || startTimeMillis == null, "cannot supply both startYear and startTimeMillis");
		Preconditions.checkState(startYear != null || startTimeMillis != null, "must supply either startYear and startTimeMillis");
		if (startYear != null)
			config.setStartYear(startYear);
		else
			config.setStartTimeMillis(startTimeMillis);
		
		File localConfFile = new File(localOutputDir, "config.json");
		config.writeJSON(localConfFile);
		
		if (mpj) {
			File templateDir = new File(launcherDir, "mpj_examples");
			File slurmScriptFile = new File(localOutputDir, "etas_sim_mpj.slurm");
			File template;
			if (hpcc)
				template = new File(templateDir, "usc_hpcc_mpj_express.slurm");
			else
				throw new IllegalStateException("MPJ but not HPC?");
			updateSlurmScript(template, slurmScriptFile, nodes, hours, queue, localConfFile);
		}
	}
	
	private static void updateSlurmScript(File inputFile, File outputFile, int nodes, int hours, String queue, File configFile)
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
				line = "ETAS_CONF_JSON="+configFile.getAbsolutePath();
			
			if (line.startsWith("#SBATCH"))
				lastIndexSBATCH = lines.size();
			
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
