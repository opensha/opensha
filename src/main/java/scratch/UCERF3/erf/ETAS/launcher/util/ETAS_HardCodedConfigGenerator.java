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
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_ConfigBuilder.HPC_Sites;

public class ETAS_HardCodedConfigGenerator {
	
	@SuppressWarnings("unused")
	public static void main(String[] args) throws IOException {
		boolean mpj = true;
		HPC_Sites hpcSite = HPC_Sites.USC_HPC;
		
		FaultModels fm = FaultModels.FM3_1;
		boolean u2 = false;
//		Integer startYear = 2019;
//		Long startTimeMillis = null;
		Integer startYear = null;
		Long startTimeMillis = 1562261628000l;
		boolean histCatalog = false;
		boolean includeSpontaneous = false;
		int numSimulations = 100000;
		double duration = 10d;
//		double duration = 7d/365.25;
		Long randomSeed = 123456789l;
//		Long randomSeed = System.nanoTime();
//		Long randomSeed = 987654321l;
		Boolean reuseERFs = true;
		U3ETAS_ProbabilityModelOptions probModel = U3ETAS_ProbabilityModelOptions.FULL_TD;
		
		// etas params
		Double p = null;
		Double c = null;
		Double log10k = null;
		
//		// critical set from Morgan
//		Double p = 1.08;
//		Double c = 0.04;
//		Double log10k = -2.31;
		
//		TriggerRupture[] triggerRups = null;
//		String scenarioName = "Spontaneous";
////		String scenarioName = "Historical1919_critical";
//		String customCatalogName = null; // null if disabled, otherwise file name within submit dir
		
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
		
		TriggerRupture[] triggerRups = { new TriggerRupture.Point(new Location(35.705, -117.508, 8.7), null, 6.4) };
		String scenarioName = "Searles Valley M6.4";
		String customCatalogName = null; // null if disabled, otherwise file name within submit dir
		
		// only if mpj == true
		int nodes = 36;
		int hours = 24;
		String queue = hpcSite == HPC_Sites.USC_HPC ? "scec_hiprio" : null;
//		String queue = "scec_hiprio";
		Integer threads = null;
		
//		Integer threads = 12;
		
		if (hpcSite == HPC_Sites.USC_HPC && threads != null && threads > 12)
			throw new IllegalStateException("did you set the threads right?");
		
//		nameAdd = nodes+"nodes_"+threads+"threads";
		
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
		scenarioFileName += "-"+probModel.name().toLowerCase();
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
		config.setReuseERFs(reuseERFs);
		config.setSimulationName(scenarioName);
		config.setRandomSeed(randomSeed);
		config.setProbModel(probModel);
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
			File slurmScriptFile = new File(localOutputDir, "etas_sim_mpj.slurm");
			File template = hpcSite.getSlurmFile();
			ETAS_ConfigBuilder.updateSlurmScript(template, slurmScriptFile, nodes, threads, hours, queue, configFile);
		}
	}
	
	public static final DateFormat df = new SimpleDateFormat("yyyy_MM_dd");

}
