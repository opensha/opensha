package scratch.kevin.ucerf3.etas;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.opensha.commons.data.comcat.ComcatAccessor;
import org.opensha.commons.data.comcat.ComcatRegionAdapter;
import org.opensha.commons.geo.Location;
import org.opensha.commons.geo.Region;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.faultSurface.FaultTrace;
import org.opensha.sha.faultSurface.SimpleFaultData;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import scratch.UCERF3.enumTreeBranches.FaultModels;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.TriggerRupture;

public class SearlessValleyConfigGen {
	
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
		FaultTrace trace = new FaultTrace("Searless Valley");
		trace.add(new Location(35.910, -117.742));
		trace.add(new Location(35.618, -117.417));
		SimpleFaultData sfd = new SimpleFaultData(90, 12, 0, trace);
		
		boolean mpj = true;
		HPC_Sites hpcSite = HPC_Sites.HPC;
		
		FaultModels fm = FaultModels.FM3_1;
		boolean u2 = false;
		Integer startYear = null;
//		Long startTimeMillis = 1562439600000l;
//		String nameAdd = "start-noon";
		Long startTimeMillis = 1562383193000l;
		String nameAdd = "following-M7.1";
//		Long startTimeMillis = new Date().getTime();
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
		
		List<TriggerRupture> triggerList = new ArrayList<>();
		ComcatAccessor comcat = new ComcatAccessor();
		long catStartTime = new GregorianCalendar(2019, 6, 1).getTimeInMillis();
		long catEndTime = startTimeMillis;
		double lenDays = (catEndTime - catStartTime)/1000d/60d/60d/24d;
		System.out.println("Comcat length: "+lenDays+" days");
		Region comcatRegion = new Region(trace, 100d);
		ObsEqkRupList comcatRups = comcat.fetchEventList(null, catStartTime, catEndTime, -1, 100,
				new ComcatRegionAdapter(comcatRegion), false, false, 2.5);
		System.out.println("Got "+comcatRups.size()+" ruptures from Comcat");
		boolean m7Found = false;
		for (ObsEqkRupture rup : comcatRups) {
			Preconditions.checkState(rup.getOriginTime() <= startTimeMillis);
			if (rup.getMag() > 7d) {
				Preconditions.checkState(!m7Found);
				m7Found = true;
				System.out.println("Found M"+(float)rup.getMag());
				triggerList.add(new TriggerRupture.SimpleFault(rup.getOriginTime(), rup.getHypocenterLocation(), rup.getMag(), sfd));
			} else {
				triggerList.add(new TriggerRupture.Point(rup.getHypocenterLocation(), rup.getOriginTime(), rup.getMag()));
			}
		}
		TriggerRupture[] triggerRups = triggerList.toArray(new TriggerRupture[0]);
		String scenarioName = "Searless Valley Sequence Finite Fault";
		
		String customCatalogName = null;
		
		// only if mpj == true
		int nodes = 36;
		int hours = 24;
		String queue = hpcSite == HPC_Sites.HPC ? "scec_hiprio" : null;
//		String queue = "scec_hiprio";
		Integer threads = null;
		
//		Integer threads = 12;
		
		if (hpcSite == HPC_Sites.HPC && threads != null && threads > 12)
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
			File templateDir = ETAS_Config.resolvePath(new File(new File(launcherDir, "parallel"), "mpj_examples").getPath());
			File slurmScriptFile = new File(localOutputDir, "etas_sim_mpj.slurm");
			File template = new File(templateDir, hpcSite.fileName);
			updateSlurmScript(template, slurmScriptFile, nodes, threads, hours, queue, configFile);
		}
	}
	
	public static void updateSlurmScript(File inputFile, File outputFile, int nodes, Integer threads, int hours, String queue, File configFile)
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
			
			if (line.startsWith("#SBATCH -n")) {
				int cores = threads == null ? nodes : nodes*threads;
				line = "#SBATCH -n "+cores;
			}
			
			if (line.startsWith("#SBATCH -p")) {
				nodeLineFound = true;
				if (queue != null)
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
