package scratch.kevin.ucerf3.etas.csepDailyRuns;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import org.opensha.commons.data.comcat.ComcatAccessor;
import org.opensha.commons.data.comcat.ComcatRegion;
import org.opensha.commons.data.comcat.ComcatRegionAdapter;
import org.opensha.commons.data.region.CaliforniaRegions;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupList;
import org.opensha.sha.earthquake.observedEarthquake.ObsEqkRupture;
import org.opensha.sha.earthquake.observedEarthquake.parsers.UCERF3_CatalogParser;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import scratch.UCERF3.erf.ETAS.ETAS_CubeDiscretizationParams;
import scratch.UCERF3.erf.ETAS.ETAS_Params.U3ETAS_ProbabilityModelOptions;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_AbstractComcatConfigBuilder;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_ComcatConfigBuilder;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_ComcatEventFetcher;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_ConfigBuilder;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_ConfigBuilder.HPC_Sites;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class DailyScriptWriter {

	public static void main(String[] args) throws IOException {
		GregorianCalendar startDate = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		startDate.clear();
		startDate.set(2007, 7, 1); // month is 0-based, so '7' is actually August
		GregorianCalendar endDate = new GregorianCalendar(startDate.getTimeZone());
		endDate.set(2018, 7, 1);
		int deltaDays = 1;
		
		int batchSize = 30;
		int batchNodes = 40;
		int batchHours = 24;
		int nodeThreads = 48;
		int batchThreads = 20;
		HPC_Sites site = HPC_Sites.TACC_STAMPEDE3;
		String queue = "skx";
		int indvNodes = 10;
		int indvHours = 5;
		int indvThreads = 15; // scale it back, if we're running individually we might have run out of memory
		
		double duration = 1d/365.25d;
		int numCatalogs = 100000;
		
		DecimalFormat batchDF = new DecimalFormat("000");
		
		String kCOV = "1.5";
		U3ETAS_ProbabilityModelOptions probModel = U3ETAS_ProbabilityModelOptions.FULL_TD;
		String parentDir = "${ETAS_SIM_DIR}/2024_07_23-csep1-daily-full_td-kCOV1.5";
		boolean griddedOnly = false;
		
		File resolvedParentDir = ETAS_Config.resolvePath(parentDir);
		System.out.println("Output Directory: "+resolvedParentDir.getAbsolutePath());
		Preconditions.checkState(resolvedParentDir.exists() || resolvedParentDir.mkdir());
		
		GregorianCalendar curStart = new GregorianCalendar(startDate.getTimeZone());
		curStart.setTimeInMillis(startDate.getTimeInMillis());
		
		File u3CatalogFile = ETAS_Config.resolvePath("${ETAS_LAUNCHER}/inputs/u3_historical_catalog.txt");
		
		ObsEqkRupList u3Catalog = UCERF3_CatalogParser.loadCatalog(u3CatalogFile);
		long comcatStartMillis = u3Catalog.get(u3Catalog.size()-1).getOriginTime();
		
		DateFormat nameDF = new SimpleDateFormat("yyyy/MM/dd");
		nameDF.setTimeZone(startDate.getTimeZone());
		
		File comcatFile = null;
		if (endDate.getTimeInMillis() > comcatStartMillis) {
			comcatFile = new File(resolvedParentDir, "comcat-events.txt");
			if (!comcatFile.exists()) {
				System.out.println("Pre-fetching ComCat events");
				ComcatAccessor accessor = new ComcatAccessor();
				ComcatRegion cReg = new ComcatRegionAdapter(new CaliforniaRegions.RELM_TESTING());
				ObsEqkRupList comcatEvents = accessor.fetchEventList(null, comcatStartMillis, endDate.getTimeInMillis(), -10d,
						ETAS_CubeDiscretizationParams.DEFAULT_MAX_DEPTH, cReg, false, false, 2.5d);
				ETAS_ComcatEventFetcher.writeCatalogFile(comcatFile, comcatEvents);
				ETAS_AbstractComcatConfigBuilder.CACHE_TRIGGER_RUPS = true;
			} else {
				System.out.println("Using already pre-fetched ComCat events");
				long maxTime = Long.MIN_VALUE;
				try {
					for (ObsEqkRupture rup : ETAS_ComcatEventFetcher.loadCatalogFile(comcatFile))
						maxTime = Long.max(maxTime, rup.getOriginTime());
					endDate.setTimeInMillis(maxTime);
				} catch (ParseException e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}
		}
		
		File surfCacheDir = new File(resolvedParentDir, "surf-json-cache");
		
		int batchNum = 0;
		List<String> curBatch = new ArrayList<>();
		String curBatchName = "batch_"+batchDF.format(batchNum);
		File batchDir = new File(resolvedParentDir, curBatchName);
		
		while (curStart.getTimeInMillis() < endDate.getTimeInMillis()) {
			List<String> argz = new ArrayList<>();
			argz.add("--num-simulations"); argz.add(numCatalogs+"");
			argz.add("--include-spontaneous");
			argz.add("--historical-catalog");
			argz.add("--binary-output");
			argz.add("--duration"); argz.add((float)duration+"");
			if (kCOV != null && !kCOV.isEmpty()) {
				argz.add("--etas-k-cov"); argz.add(kCOV);
			}
			
			boolean comcat = curStart.getTimeInMillis() > comcatStartMillis;
			
			String name = "Start "+nameDF.format(curStart.getTime());
			if (kCOV != null && !kCOV.isEmpty())
				name += ", kCOV="+kCOV;
			if (comcat)
				name += ", ComCat Stitch";
			if (griddedOnly) {
				name += ", Gridded Only";
				argz.add("--gridded-only");
			} else if (probModel != U3ETAS_ProbabilityModelOptions.FULL_TD) {
				name += ", "+probModel.toString();
				argz.add("--prob-model"); argz.add(probModel.name());
			}
			Preconditions.checkState(batchDir.exists() || batchDir.mkdir());
			String outputDir = parentDir+"/"+curBatchName+"/"+ETAS_ConfigBuilder.getNamePrefix(name.replaceAll("/", "_"));
			
			if (comcat) {
				argz.add("--start-after-historical");
				argz.add("--end-time"); argz.add(curStart.getTimeInMillis()+"");
				argz.add("--finite-surf-cache"); argz.add(surfCacheDir.getAbsolutePath());
				argz.add("--finite-surf-shakemap");
				argz.add("--finite-surf-shakemap-min-mag"); argz.add("6");
				argz.add("--comcat-file"); argz.add(comcatFile.getAbsolutePath());
				argz.add("--skip-input-plots");
			} else {
				argz.add("--start-time"); argz.add(curStart.getTimeInMillis()+"");
			}
			argz.add("--name"); argz.add(name);
			argz.add("--max-point-src-mag"); argz.add("6");
			
			argz.add("--hpc-site"); argz.add(site.name());
			argz.add("--nodes"); argz.add(indvNodes+"");
			argz.add("--hours"); argz.add(indvHours+"");
			argz.add("--threads"); argz.add(indvThreads+"");
			argz.add("--queue"); argz.add(queue);
			argz.add("--output-dir"); argz.add(outputDir);
			
			System.out.println(name);
			System.out.println("\tARGS: "+argz);
			args = argz.toArray(new String[0]);
			if (comcat)
				ETAS_ComcatConfigBuilder.main(args);
			else
				ETAS_ConfigBuilder.main(args);
			
			curBatch.add(outputDir+"/config.json");
			if (curBatch.size() == batchSize) {
				File outputFile = new File(batchDir, curBatchName+".slurm");
				writeBatch(site, curBatch, outputFile, batchNodes, batchHours, nodeThreads, batchThreads, queue);
				curBatch.clear();
				batchNum++;
				curBatchName = "batch_"+batchDF.format(batchNum);
				batchDir = new File(resolvedParentDir, curBatchName);
			}
			
			curStart.add(GregorianCalendar.DATE, deltaDays);
			try {
				Thread.sleep(10); // ensure a new random seed next time
			} catch (InterruptedException e) {}
		}
		if (!curBatch.isEmpty()) {
			File outputFile = new File(batchDir, curBatchName+".slurm");
			writeBatch(site, curBatch, outputFile, batchNodes, batchHours, nodeThreads, batchThreads, queue);
		}
	}
	
	private static void writeBatch(HPC_Sites site, List<String> configs, File outputFile,
			int nodes, int hours, int nodeThreads, int calcThreads, String queue) throws IOException {
		String configFile = Joiner.on(" ").join(configs);
		File inputFile = site.getSlurmFile();
		ETAS_ConfigBuilder.updateSlurmScript(inputFile, outputFile, nodes, nodeThreads, calcThreads,
				hours, queue, configFile);
	}

}
