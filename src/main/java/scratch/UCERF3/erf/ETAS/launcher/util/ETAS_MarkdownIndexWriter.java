package scratch.UCERF3.erf.ETAS.launcher.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.FileNameComparator;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.io.Files;

import scratch.UCERF3.erf.ETAS.analysis.ETAS_AbstractPlot;
import scratch.UCERF3.erf.ETAS.analysis.ETAS_ComcatComparePlot;
import scratch.UCERF3.erf.ETAS.analysis.SimulationMarkdownGenerator;
import scratch.UCERF3.erf.ETAS.analysis.SimulationMarkdownGenerator.PlotMetadata;
import scratch.UCERF3.erf.ETAS.analysis.SimulationMarkdownGenerator.PlotResult;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.utils.ProbabilityModelsCalc;

public class ETAS_MarkdownIndexWriter {
	
	private static Options createOptions() {
		Options ops = new Options();

		Option updateOption = new Option("u", "update-plots", false,
				"Flag to update plots for any catalog that might be stale due to a new version "
				+ "of the plotting code");
		updateOption.setRequired(false);
		ops.addOption(updateOption);

		Option dryRunOption = new Option("dry", "dry-run", false,
				"Don't actually replot any simulation dirs, but print if it would");
		dryRunOption.setRequired(false);
		ops.addOption(dryRunOption);

		Option forceUpdateOption = new Option("f", "force-update", false,
				"Force update of all plots (does not supercede --dry-run flag)");
		forceUpdateOption.setRequired(false);
		ops.addOption(forceUpdateOption);

		Option updateAfterOption = new Option("ua", "update-after", true,
				"Force update of all plots for simulations with configuration dates on or "
				+ "after the given date, specified as yyyy_mm_dd");
		updateAfterOption.setRequired(false);
		ops.addOption(updateAfterOption);

		Option updateIntevalOption = new Option("ui", "update-interval", true,
				"Update interval in minutes. If this is being run on a cron job, you can supply"
				+ " this optional hint argument to tell how often. It will attempt to exit and not"
				+ " process any more older simulations if within 20% of the interval of the next update.");
		updateIntevalOption.setRequired(false);
		ops.addOption(updateIntevalOption);

		Option threadsOption = new Option("t", "threads", true,
				"Number of calculation threads. Default is the number of available processors (in this case: "
						+SimulationMarkdownGenerator.defaultNumThreads()+")");
		threadsOption.setRequired(false);
		ops.addOption(threadsOption);
		
		return ops;
	}

	public static void main(String[] args) throws IOException {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			//			File simDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
			//			+ "2018_08_07-MojaveM7-noSpont-10yr");
			//		configFile = new File(simDir, "config.json");
			//		inputFile = new File(simDir, "results_complete.bin");
			////		inputFile = new File(simDir, "results_complete_partial.bin");

			File gitDir = new File("/home/kevin/git/ucerf3-etas-results/");
			
			String argz = "--update-plots";
			argz += " --dry-run";
			argz += " "+gitDir.getAbsolutePath();
			
//			File inputFile = new File(simDir, "results_m5_preserve_chain.bin");
//			args = new String[] { configFile.getAbsolutePath(), inputFile.getAb?olutePath() };
			args = argz.split(" ");
//			String gitHash = getGitHash();
//			System.out.println(gitHash);
//			System.out.println(getGitCommitTime(gitHash));
//			System.exit(0);
//			args = new String[] { "--num-catalogs", "10000", configFile.getAbsolutePath() };
		}
		
		Options options = createOptions();
		
		Stopwatch runningWatch = Stopwatch.createStarted();
		
		CommandLineParser parser = new DefaultParser();
		
		String syntax = ClassUtils.getClassNameWithoutPackage(SimulationMarkdownGenerator.class)
				+" [options] <etas-config.json> [<binary-catalogs-file.bin OR results directory>]";
		
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(syntax, options, true );
			System.exit(2);
			return;
		}
		
		args = cmd.getArgs();
		
		double updateInterval = cmd.hasOption("update-interval")
				? Double.parseDouble(cmd.getOptionValue("update-interval")) : 0d;
		
		Preconditions.checkArgument(args.length == 1,
				"Usage: "+ClassUtils.getClassNameWithoutPackage(ETAS_MarkdownIndexWriter.class)+" <dir>");
		File mainDir = new File(args[0]);
		Preconditions.checkState(mainDir.exists());
		
		// look for sub dirs
		System.out.println("Scanning directory for ETAS subdirectories: "+mainDir.getAbsolutePath());
		File[] subDirArray = mainDir.listFiles();
		Arrays.sort(subDirArray, new FileNameComparator());
		
		ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		List<Future<SimulationDir>> futures = new ArrayList<>();
		for (File subDir : subDirArray) {
			if (!subDir.isDirectory())
				continue;
			futures.add(exec.submit(new SimLoadCallable(subDir)));
		};
		
		List<SimulationDir> sims = new ArrayList<>();
		
		for (Future<SimulationDir> future : futures) {
			SimulationDir sim =  null;
			try {
				sim = future.get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
				System.exit(1);
			}
			if (sim != null)
				sims.add(sim);
		}
		
		if (sims.isEmpty()) {
			System.out.println("No ETAS subdirectories of "+mainDir.getAbsolutePath()+" found.");
			System.exit(0);
		}
		
		System.out.println("Found "+sims.size()+" ETAS subdirectories");
		
		Collections.sort(sims);
		
		boolean updatePlots = cmd.hasOption("update-plots");
		boolean forceUpdate = cmd.hasOption("force-update");
		if (updatePlots || forceUpdate) {
			boolean dryRun = cmd.hasOption("dry-run");
			long forceBeforeTime = Long.MAX_VALUE;
			if (cmd.hasOption("update-after")) {
				String dateStr = cmd.getOptionValue("update-after");
				try {
					forceBeforeTime = ETAS_ConfigBuilder.df.parse(dateStr).getTime();
				} catch (Exception e) {
					System.err.println("Expected date in the format: yyyy_mm_dd");
					e.printStackTrace();
					System.exit(1);
				}
			}
			int threads = cmd.hasOption("threads") ? Integer.parseInt(cmd.getOptionValue("threads"))
					: SimulationMarkdownGenerator.defaultNumThreads();
			Long gitTime = null;
			if (!forceUpdate) {
				String gitHash = SimulationMarkdownGenerator.getGitHash();
				gitTime = SimulationMarkdownGenerator.getGitCommitTime(gitHash);
				if (gitTime == null)
					System.out.println("Couldn't determine time associated with current ETAS_LAUNCHER git version, "
							+ "check $ETAS_LAUNCHER environmental variable or use --force-update to update plots");
			}
			System.out.println("Updating plots...");
			for (SimulationDir sim : sims) {
				System.out.println(sim.simDir.getName()+": "+sim.config.getSimulationName());
				boolean update = false;
				if (forceUpdate) {
					System.out.println("\tupdating due to --force-update");
					update = true;
				}
				if (sim.plotMetadata == null) {
					System.out.println("\tupdating due to missing plot metadata");
					update = true;
				}
				if (sim.configDate != null && sim.configDate.getTime() >= forceBeforeTime) {
					System.out.println("\tupdating due to configuration date on or after --update-after option");
					update = true;
				}
				if (sim.plotMetadata != null && sim.plotMetadata.simulationsProcessed < sim.config.getNumSimulations()) {
					File inputFile;
					try {
						inputFile = SimulationMarkdownGenerator.locateInputFile(sim.config);
					} catch (Exception e) {
						System.err.println("Couldn't locate input file, skipping");
//						e.printStackTrace();
						continue;
					}
					Path file = inputFile.toPath();
					BasicFileAttributes attr = java.nio.file.Files.readAttributes(file, BasicFileAttributes.class);
					long modifyDate = attr.lastModifiedTime().toMillis();
					double daysOld = (double)(System.currentTimeMillis() - modifyDate) / (double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
					if (sim.plotMetadata != null && sim.plotMetadata.plotStartTime < modifyDate) {
						System.out.println("\tupdating because the simulation was not complete when last plotted and input file modified "+
								ETAS_AbstractPlot.getTimeShortLabel(daysOld/365.25)+" ago");
						update = true;
					} else {
						System.out.println("\tsimulation was not complete when last plotted, but results file has not been modified since");
					}
				}
				if (!update && updateInterval > 0) {
					// see if we should exit
					double curInterval = runningWatch.elapsed(TimeUnit.SECONDS)/60d;
					double fractInterval = curInterval/updateInterval;
					int numMissed = (int)fractInterval;
					if (fractInterval > 1)
						fractInterval -= Math.floor(fractInterval);
					double minsUntilNext = (1d-fractInterval)*updateInterval;
					// lower exit the threshold as we miss more and more updates
					// no missed updates: halt 80% into cycle
					// 1 missed update: halt 70% into cycle
					// 2 missed updates: halt 60% into cycle
					// ...
					// 8 missed updates: halt no matter what
					double threshold = 1.0 - 0.1*(numMissed+2);
					if (fractInterval > threshold) {
						System.out.println("\tskipping update checks on old sim as we're "+(float)minsUntilNext
								+" minutes away from the next automated plot update, will try again next time");
						continue;
					}
				}
				if (sim.plotMetadata != null && sim.plotMetadata.launcherGitTime != null && gitTime != null) {
					long curDiff = gitTime - sim.plotMetadata.launcherGitTime;
					double curDiffDays = (double)curDiff/(double)ProbabilityModelsCalc.MILLISEC_PER_DAY;
					if (curDiff > 0) {
						System.out.println("\tupdating due to new jar file ("+(float)curDiffDays+" days newer)");
						update = true;
					}
				}
				if (sim.config.getComcatMetadata() != null) {
					PlotResult prevComcatResult = null;
					if (sim.plotMetadata != null && sim.plotMetadata.plots != null)
						for (PlotResult result : sim.plotMetadata.plots)
							if (result.className.equals(ETAS_ComcatComparePlot.class.getName()))
								prevComcatResult = result;
					if (prevComcatResult == null) {
						System.out.println("\tupdating because no metadata from previous ComCat plot");
						update = true;
					} else if (ETAS_ComcatComparePlot.shouldReplot(prevComcatResult, sim.config)) {
						System.out.println("\tupdating ComCat plots");
						update = true;
					}
				}
				if (update && !dryRun) {
					File inputFile;
					try {
						inputFile = SimulationMarkdownGenerator.locateInputFile(sim.config);
					} catch (Exception e) {
						System.err.println("Couldn't locate input file, skipping");
//						e.printStackTrace();
						continue;
					}
					try {
						sim.plotMetadata = SimulationMarkdownGenerator.generateMarkdown(
								sim.configFile, inputFile, sim.config, sim.simDir, false, -1, threads, forceUpdate, false);
					} catch (Exception e) {
						System.err.println("Error updating plots for "+sim.simDir.getName());
						e.printStackTrace();
					}
				}
			}
		}
		
		TableBuilder simsTable = MarkdownUtils.tableBuilder();
		simsTable.addLine("Config Date", "Name", "Sim Start Date", "# Simulations", "Progress", "Plot Date");
		for (SimulationDir sim : sims) {
			Date date = sim.configDate;
			File subDir = sim.simDir;
			ETAS_Config config = sim.config;
			System.out.println("Processing "+subDir.getName()+": "+config.getSimulationName()
				+(date == null ? "" : " ("+date.getTime()+")"));
			File subMD = new File(subDir, "README.md");
			String name = config.getSimulationName() == null ? subDir.getName() : config.getSimulationName();
			File plotsDir = new File(subDir, "plots");
			boolean hasResults = subMD.exists() && plotsDir.exists();
			if (!subMD.exists()) {
				System.out.println("\tREADME.md doesn't yet exist, building");
				List<String> lines = new ArrayList<>();
				lines.add("# "+name);
				lines.add("");
				File inputsDir = new File(subDir, "config_input_plots");
				if (inputsDir.exists())
					lines.add("No simulation results are yet available, but pre-simulation analysis plots "
							+ "are available [here]("+inputsDir.getName()+"/README.md).");
				else
					lines.add("No simulation results are yet available.");
				lines.add("");
				lines.add("## JSON Input File");
				lines.add("```");
				for (String line : Files.readLines(sim.configFile, Charset.defaultCharset()))
					lines.add(line);
				lines.add("```");
				lines.add("");
				MarkdownUtils.writeReadmeAndHTML(lines, subDir);
			}
			simsTable.initNewLine();
			if (date == null)
				simsTable.addColumn("*Unknown*");
			else
				simsTable.addColumn(outDateFormat.format(date));
			simsTable.addColumn("["+name+"]("+subDir.getName()+"/README.md)");
			simsTable.addColumn(outDateFormat.format(new Date(config.getSimulationStartTimeMillis())));
			simsTable.addColumn(config.getNumSimulations());
			String progressStr;
			if (sim.plotMetadata != null) {
				int numDone = sim.plotMetadata.simulationsProcessed;
				if (numDone == config.getNumSimulations()) {
					progressStr = "Done";
				} else {
					progressStr = percentDF.format((double)numDone/(double)config.getNumSimulations());
				}
			} else {
				progressStr = "Unknown";
			}
			simsTable.addColumn(progressStr);
			if (sim.plotMetadata == null)
				simsTable.addColumn("Unknown");
			else
				simsTable.addColumn(outDateFormat.format(new Date(sim.plotMetadata.plotEndTime)));
			simsTable.finalizeLine();
		}
		
		List<String> lines = new ArrayList<>();
		lines.add("# ETAS Simulations List");
		lines.add("");
		lines.addAll(simsTable.build());
		lines.add("");
		
		MarkdownUtils.writeReadmeAndHTML(lines, mainDir);
		
		runningWatch.stop();
		double secs = runningWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		double mins = secs/60d;
		if (mins > 1)
			System.out.println("Took "+(float)mins+" minutes to rebuild index");
		else
			System.out.println("Took "+(float)secs+" seconds to rebuild index");
		
		System.exit(0);
	}
	
	private static DateFormat outDateFormat = new SimpleDateFormat("yyyy/MM/dd");
	
	private static class SimLoadCallable implements Callable<SimulationDir> {
		
		private File simDir;

		public SimLoadCallable(File simDir) {
			this.simDir = simDir;
		}

		@Override
		public SimulationDir call() throws Exception {
			return loadSimDir(simDir);
		}
	}
	
	private static SimulationDir loadSimDir(File simDir) throws IOException {
		ETAS_Config config = null;
		File configFile = null;
		for (File file : simDir.listFiles()) {
			if (file.getName().toLowerCase().endsWith(".json")) {
				try {
					config = ETAS_Config.readJSON(file);
					configFile = file;
				} catch (Exception e) {}
			}
		}
		if (config != null) {
			File plotsDir = new File(simDir, "plots");
			PlotMetadata plotMetadata = null;
//			System.out.println("Looking for date from: "+simDir.getName());
			if (plotsDir.exists()) {
				File metadataFile = new File(plotsDir, "metadata.json");
				if (metadataFile.exists())
					plotMetadata = SimulationMarkdownGenerator.readPlotMetadata(metadataFile);
			}
			Date configDate = null;
			if (config.getConfigTime() != null) {
				configDate = new Date(config.getConfigTime());
//				System.out.println("Date from config: "+outDateFormat.format(configDate));
			}
			String dirName = simDir.getName();
			if (configDate == null && dirName.contains("-")) {
				String dateStr = dirName.substring(0, dirName.indexOf("-"));
				if (dateStr.contains("_")) {
					// it might be a date, lets try
					try {
						configDate = ETAS_ConfigBuilder.df.parse(dateStr);
//						System.out.println("Date from dir name: "+outDateFormat.format(configDate));
					} catch (Exception e) {
						System.out.println("Couldn't parse date from dirName: "+dateStr);
					}
				}
			}
			if (configDate == null) {
				// use creation date
				try {
					Path file = configFile.toPath();
					BasicFileAttributes attr = java.nio.file.Files.readAttributes(file, BasicFileAttributes.class);
					configDate = new Date(attr.creationTime().toMillis());
//					System.out.println("Date from file creation: "+outDateFormat.format(configDate));
				} catch (Exception e) {
					System.out.println("Error determining config file creation date: "+e.getMessage());
				}
			}
			return new SimulationDir(simDir, configFile, config, plotsDir, plotMetadata, configDate);
		}
		return null;
	}
	
	private static class SimulationDir implements Comparable<SimulationDir> {
		File simDir;
		File configFile;
		ETAS_Config config;
		File plotsDir;
		PlotMetadata plotMetadata;
		Date configDate;
		public SimulationDir(File simDir, File configFile, ETAS_Config config, File plotsDir, PlotMetadata plotMetadata,
				Date configDate) {
			super();
			this.simDir = simDir;
			this.configFile = configFile;
			this.config = config;
			this.plotsDir = plotsDir;
			this.plotMetadata = plotMetadata;
			this.configDate = configDate;
		}
		
		@Override
		public int compareTo(SimulationDir o) {
			if (configDate == null) {
				if (o.configDate == null)
					return 0;
				return -1;
			} else if (o.configDate == null) {
				return 1;
			}
			int ret = -Long.compare(configDate.getTime(), o.configDate.getTime());
//			System.out.println("\n"+simDir.getName()+"\n\tVS\n"+o.simDir.getName()+"\n\t"+ret);
			return ret;
		}
	}
	
	private static DecimalFormat percentDF = new DecimalFormat("0.#%");

}
