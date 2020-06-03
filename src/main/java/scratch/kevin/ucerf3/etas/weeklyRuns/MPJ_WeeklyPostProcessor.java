package scratch.kevin.ucerf3.etas.weeklyRuns;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.math3.stat.StatUtils;
import org.opensha.commons.data.CSVFile;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.FilePathComparator;

import com.google.common.base.Preconditions;

import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.ETAS_Catalog;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_CatalogIteration;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_CatalogIteration.Callback;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class MPJ_WeeklyPostProcessor extends MPJTaskCalculator {
	
	private List<File> configFiles;
	private List<String> header;
	
	private static final long time_delta_millis = 1000l*60l*60l; // 1 hour
	private static double[] min_mags = { 2.5d, 3d, 4d, 5d, 6d, 7d, 8d };
	private static double[] fractiles = { 0d, 0.025, 0.16, 0.5, 0.84, 0.975, 1d };

	private MPJ_WeeklyPostProcessor(CommandLine cmd, File mainDir) {
		super(cmd);
		
		configFiles = new ArrayList<>();
		calcTreeSearch(mainDir, configFiles);
		
		if (rank == 0)
			debug("Found "+configFiles.size()+" config files");
		
		Collections.sort(configFiles, new FilePathComparator());
		
		header = new ArrayList<>();
		header.add("Time (epoch milliseconds)");
		header.add("Probability");
		header.add("Mean");
		for (double fractile : fractiles)
			header.add("p"+(float)(100d*fractile)+" %-ile");
	}
	
	private void calcTreeSearch(File dir, List<File> configFiles) {
		Preconditions.checkState(dir.isDirectory());
		File configFile = new File(dir, "config.json");
		if (configFile.exists())
			configFiles.add(configFile);
		for (File subDir : dir.listFiles())
			if (subDir.isDirectory() && !subDir.getName().startsWith("results"))
				calcTreeSearch(subDir, configFiles);
	}

	@Override
	protected int getNumTasks() {
		return configFiles.size();
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		for (int index : batch) {
			File configFile = configFiles.get(index);
			File resultsFile = new File(configFile.getParentFile(), "results_complete.bin");
			File simDir = configFile.getParentFile();
			if (!resultsFile.exists()) {
				debug(simDir.getName()+" is not yet done, skipping");
				continue;
			}
			debug("processing "+simDir.getName());
			ETAS_Config config = ETAS_Config.readJSON(configFile);
			long startTime = config.getSimulationStartTimeMillis();
			long endTime = (long)(startTime + ProbabilityModelsCalc.MILLISEC_PER_YEAR*config.getDuration()+0.5);
			List<Long> times = new ArrayList<>();
			long time = startTime + time_delta_millis;
			while (time <= endTime) {
				times.add(time);
				time += time_delta_millis;
			}
			CatalogCallback callback = new CatalogCallback(times);
			int numProcessed = ETAS_CatalogIteration.processCatalogs(resultsFile, callback);
			if (numProcessed < config.getNumSimulations()) {
				debug(simDir.getName()+" incomplete, "+numProcessed+"/"+config.getNumSimulations()+" done");
				continue;
			}
			List<int[][]> allCounts = callback.counts;
			Preconditions.checkState(allCounts.size() == numProcessed);
			for (int m=0; m<min_mags.length; m++) {
				for (boolean cumulative : new boolean[] {false,true}) {
					CSVFile<String> csv = new CSVFile<>(true);
					csv.addLine(header);
					int[] prevCounts = cumulative ? new int[numProcessed] : null;
					for (int t=0; t<times.size(); t++) {
						double[] counts = new double[numProcessed];
						int numWith = 0;
						for (int i=0; i<numProcessed; i++) {
							int[][] catCounts = allCounts.get(i);
							int count = catCounts[m][t];
							if (cumulative) {
								prevCounts[i] += count;
								count = prevCounts[i];
							}
							if (count > 0)
								numWith++;
							counts[i] = count;
						}
						List<String> line = new ArrayList<>();
						line.add(times.get(t)+"");
						double prob = (double)numWith/(double)numProcessed;
						line.add((float)prob+"");
						double mean = StatUtils.mean(counts);
						line.add((float)mean+"");
						for (double fractile : fractiles) {
							double val;
							if (fractile == 0d)
								val = StatUtils.min(counts);
							else if (fractile == 1d)
								val = StatUtils.max(counts);
							else
								val = StatUtils.percentile(counts, fractile*100d);
							line.add((float)val+"");
						}
						csv.addLine(line);
					}
					File outputDir = new File(simDir, "aggregated_results");
					Preconditions.checkState(outputDir.exists() || outputDir.mkdir());
					File csvFile = new File(outputDir, getCSVName(min_mags[m], cumulative));
					debug("writing "+csvFile.getName());
					csv.writeToFile(csvFile);
				}
			}
		}
	}
	
	public static String getCSVName(double minMag, boolean cumulative) {
		String name = "m"+(float)minMag;
		if (cumulative)
			name += "_cumulative";
		return name+"_time_stats.csv";
	}
	
	private static class CatalogCallback implements Callback {
		
		List<int[][]> counts;
		List<Long> times;
		
		public CatalogCallback(List<Long> times) {
			this.times = times;
			counts = new ArrayList<>();
		}

		@Override
		public void processCatalog(ETAS_Catalog catalog, int index) {
			int[][] myCount = new int[min_mags.length][times.size()];
			int timeIndex = 0;
			for (ETAS_EqkRupture rup : catalog) {
				long rupTime = rup.getOriginTime();
				while (timeIndex < times.size() && rupTime >= times.get(timeIndex))
					timeIndex++;
				if (timeIndex >= times.size())
					break;
				double mag = rup.getMag();
				for (int m=0; m<min_mags.length; m++)
					if (mag >= min_mags[m])
						myCount[m][timeIndex]++;
			}
			counts.add(myCount);
		}
		
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		
	}
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		return ops;
	}

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		try {
			args = MPJTaskCalculator.initMPJ(args);
			
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJ_WeeklyPostProcessor.class);
			
			args = cmd.getArgs();
			
			if (args.length != 1) {
				System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(MPJ_WeeklyPostProcessor.class)
						+" [options] <directory>");
				abortAndExit(2);
			}
			
			File mainDir = new File(args[0]);
			Preconditions.checkArgument(mainDir.exists());
			
			MPJ_WeeklyPostProcessor driver = new MPJ_WeeklyPostProcessor(cmd, mainDir);
			driver.run();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
