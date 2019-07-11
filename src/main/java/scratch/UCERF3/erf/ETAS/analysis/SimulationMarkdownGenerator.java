package scratch.UCERF3.erf.ETAS.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.time.StopWatch;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileNameComparator;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.io.Files;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.BinaryFilteredOutputConfig;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;
import scratch.UCERF3.erf.ETAS.launcher.util.ETAS_CatalogIteration;

public class SimulationMarkdownGenerator {
	
	private static Options createOptions() {
		Options ops = new Options();

		Option noMapsOption = new Option("nm", "no-maps", false,
				"Flag to disable map plots (useful it no internet connection or map server down");
		noMapsOption.setRequired(false);
		ops.addOption(noMapsOption);
		
		return ops;
	}

	public static void main(String[] args) throws IOException {
		if (args.length == 1 && args[0].equals("--hardcoded")) {
			//			File simDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
			//			+ "2018_08_07-MojaveM7-noSpont-10yr");
			//		configFile = new File(simDir, "config.json");
			//		inputFile = new File(simDir, "results_complete.bin");
			////		inputFile = new File(simDir, "results_complete_partial.bin");

			File simDir = new File("/home/kevin/OpenSHA/UCERF3/etas/simulations/"
//					+ "2019_06_05-Spontaneous-includeSpont-historicalCatalog-full_td-1000yr");
//					+ "2019_06_05-Spontaneous-includeSpont-historicalCatalog-no_ert-1000yr");
//					+ "2019_07_04-SearlesValleyM64-includeSpont-full_td-10yr");
//					+ "2019-06-05_M7.1_SearlesValley_Sequence_UpdatedMw_and_depth");
					+ "2019_07_06-SearlessValleySequenceFiniteFault-noSpont-full_td-10yr-start-noon");
//					+ "2019_07_06-SearlessValleySequenceFiniteFault-noSpont-full_td-10yr-following-M7.1");
			File configFile = new File(simDir, "config.json");
//			File inputFile = new File(simDir, "results_m5_preserve_chain.bin");
//			args = new String[] { configFile.getAbsolutePath(), inputFile.getAb?olutePath() };
			args = new String[] { configFile.getAbsolutePath() };
		}
		
		// TODO optional second arg
		
		Options options = createOptions();
		
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
		
		File configFile;
		File inputFile;
		if (args.length < 1 || args.length > 2) {
			System.err.println("USAGE: "+syntax);
			System.exit(2);
		}
		
		configFile = new File(args[0]);
		
		Preconditions.checkState(configFile.exists(), "ETAS config file doesn't exist: "+configFile.getAbsolutePath());
		ETAS_Config config = ETAS_Config.readJSON(configFile);
		if (args.length == 2) {
			inputFile = new File(args[1]);
		} else {
			System.out.println("Catalogs file/dir not specififed, searching for catalogs...");
			inputFile = locateInputFile(config);
		}
		
		File outputDir = config.getOutputDir();
		if (!outputDir.exists() && !outputDir.mkdir()) {
			System.out.println("Output dir doesn't exist and can't be created, "
					+ "assuming that it was computed remotely: "+outputDir.getAbsolutePath());
			outputDir = inputFile.getParentFile();
			System.out.println("Using parent directory of input file as output dir: "+outputDir.getAbsolutePath());
		}
		
		
		boolean skipMaps = cmd.hasOption("no-maps");
		
		generateMarkdown(configFile, inputFile, config, outputDir, skipMaps);
	}

	static File locateInputFile( ETAS_Config config) {
		List<BinaryFilteredOutputConfig> binaryFilters = config.getBinaryOutputFilters();
		File inputFile = null;
		if (binaryFilters != null) {
			binaryFilters = new ArrayList<>(binaryFilters);
			binaryFilters.sort(binaryOutputComparator); // sort so that the one with the lowest magnitude is used preferentially
			for (BinaryFilteredOutputConfig bin : binaryFilters) {
				File binFile = new File(config.getOutputDir(), bin.getPrefix()+".bin");
				if (binFile.exists()) {
					inputFile = binFile;
					break;
				}
				// check for gzipped
				binFile = new File(binFile.getParentFile(), binFile.getName()+".gz");
				if (binFile.exists()) {
					inputFile = binFile;
					break;
				}
				// check for partial
				binFile = new File(config.getOutputDir(), bin.getPrefix()+"_partial.bin");
				if (binFile.exists()) {
					inputFile = binFile;
					break;
				}
			}
		}
		if (inputFile != null) {
			System.out.println("Using binary catalogs file: "+inputFile.getAbsolutePath());
		} else {
			inputFile = new File(config.getOutputDir(), "results");
			Preconditions.checkState(inputFile.exists(),
					"Couldn't locate results binary files and results dir doesn't exist: %s", inputFile.getAbsolutePath());
			System.out.println("Using results dir: "+inputFile.getAbsolutePath());
		}
		return inputFile;
	}
	
	private static double seconds(long milliseconds) {
		return milliseconds/1000d;
	}

	public static void generateMarkdown(File configFile, File inputFile, ETAS_Config config, File outputDir,
			boolean skipMaps) throws IOException {
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir(),
				"Output dir doesn't exist and couldn't be created: %s", outputDir.getAbsolutePath());
		File plotsDir = new File(outputDir, "plots");
		Preconditions.checkState(plotsDir.exists() || plotsDir.mkdir(),
				"Plot dir doesn't exist and couldn't be created: %s", plotsDir.getAbsolutePath());
		
		List<ETAS_AbstractPlot> plots = new ArrayList<>();
		
		ETAS_Launcher launcher = new ETAS_Launcher(config, false);
		
		boolean hasTriggers = config.hasTriggers();
		
		boolean annualizeMFDs = !hasTriggers;
		if (hasTriggers) {
			plots.add(new ETAS_MFD_Plot(config, launcher, "mag_num_cumulative", annualizeMFDs, true));
			plots.add(new ETAS_HazardChangePlot(config, launcher, "hazard_change_100km", 100d));
			plots.add(new ETAS_TriggerRuptureFaultDistancesPlot(config, launcher, 20d));
		} else {
			plots.add(new ETAS_MFD_Plot(config, launcher, "mfd", annualizeMFDs, true));
			if (config.getDuration() > 50)
				plots.add(new ETAS_LongTermRateVariabilityPlot(config, launcher, "long_term_var"));
		}
		plots.add(new ETAS_FaultParticipationPlot(config, launcher, "fault_participation", annualizeMFDs, skipMaps));
		if (!skipMaps)
			plots.add(new ETAS_GriddedNucleationPlot(config, launcher, "gridded_nucleation", annualizeMFDs));
		
		boolean filterSpontaneous = false;
		for (ETAS_AbstractPlot plot : plots)
			filterSpontaneous = filterSpontaneous || plot.isFilterSpontaneous();
		
		final boolean isFilterSpontaneous = filterSpontaneous;
		
		FaultSystemSolution fss = launcher.checkOutFSS();
		
		// process catalogs
		Stopwatch totalProcessWatch = Stopwatch.createStarted();
		int numProcessed = ETAS_CatalogIteration.processCatalogs(inputFile, new ETAS_CatalogIteration.Callback() {
			
			@Override
			public void processCatalog(List<ETAS_EqkRupture> catalog, int index) {
				List<ETAS_EqkRupture> triggeredOnlyCatalog = null;
				if (isFilterSpontaneous)
					triggeredOnlyCatalog = ETAS_Launcher.getFilteredNoSpontaneous(config, catalog);
				for (ETAS_AbstractPlot plot : plots)
					plot.processCatalog(catalog, triggeredOnlyCatalog, fss);
			}
		});
		DecimalFormat timeDF = new DecimalFormat("0.00");
		totalProcessWatch.stop();
		
		double totProcessSeconds = seconds(totalProcessWatch.elapsed(TimeUnit.MILLISECONDS));
		System.out.println("Processed "+numProcessed+" catalogs in "+timeDF.format(totProcessSeconds)+" s");
		
		List<String> lines = new ArrayList<>();
		
		String simName = config.getSimulationName();
		if (simName == null || simName.isEmpty())
			simName = "ETAS Simulation";
		
		lines.add("# "+simName+" Results");
		lines.add("");
		
		List<ETAS_EqkRupture> triggerRups = launcher.getTriggerRuptures();
		List<ETAS_EqkRupture> histRups = launcher.getHistQkList();
		lines.addAll(getCatalogSummarytable(config, numProcessed, triggerRups, histRups));
		lines.add("");
		
		int tocIndex = lines.size();
		String topLink = "*[(top)](#table-of-contents)*";
		lines.add("");
		
		List<Double> finalizeTimes = new ArrayList<>();
		Stopwatch finalizeWatch = Stopwatch.createStarted();
		
		System.out.println("Finalizing plots");
		double sumProcessTimes = 0d;
		for (ETAS_AbstractPlot plot : plots) {
			System.out.println("Finalizing "+ClassUtils.getClassNameWithoutPackage(plot.getClass()));
			Stopwatch finalizeSubWatch = Stopwatch.createStarted();
			plot.finalize(plotsDir, fss);
			sumProcessTimes += seconds(plot.getProcessTimeMS());
			finalizeSubWatch.stop();
			finalizeTimes.add(seconds(finalizeSubWatch.elapsed(TimeUnit.MILLISECONDS)));
			
			List<String> plotLines = plot.generateMarkdown(plotsDir.getName(), "##", topLink);
			
			if (plotLines != null)
				lines.addAll(plotLines);
		}
		finalizeWatch.stop();
		double totFinalizeTime = seconds(finalizeWatch.elapsed(TimeUnit.MILLISECONDS));
		
		System.out.println("Total catalog processing time: "+timeDF.format(totProcessSeconds)+" s");
		DecimalFormat percentDF = new DecimalFormat("0.00 %");
		double overhead = totProcessSeconds - sumProcessTimes;
		System.out.println("\tI/O overhead: "+timeDF.format(overhead)+" s ("+percentDF.format(overhead/totProcessSeconds)+")");
		for (ETAS_AbstractPlot plot : plots) {
			double plotSecs = seconds(plot.getProcessTimeMS());
			System.out.println("\t"+ClassUtils.getClassNameWithoutPackage(plot.getClass())+" "
					+timeDF.format(plotSecs)+" s ("+percentDF.format(plotSecs/totProcessSeconds)+")");
		}
		System.out.println();
		System.out.println("Total finalize time: "+timeDF.format(totFinalizeTime)+" s");
		for (int i=0; i<plots.size(); i++) {
			double finalizeSecs = finalizeTimes.get(i);
			System.out.println("\t"+ClassUtils.getClassNameWithoutPackage(plots.get(i).getClass())+" "
					+timeDF.format(finalizeSecs)+" s ("+percentDF.format(finalizeSecs/totFinalizeTime)+")");
		}
		
		lines.add("");
		lines.add("## JSON Input File");
		lines.add(topLink); lines.add("");
		lines.add("```");
		for (String line : Files.readLines(configFile, Charset.defaultCharset()))
			lines.add(line);
		lines.add("```");
		lines.add("");
		
		launcher.checkInFSS(fss);
		
		List<String> tocLines = new ArrayList<>();
		tocLines.add("## Table Of Contents");
		tocLines.add("");
		tocLines.addAll(MarkdownUtils.buildTOC(lines, 2));
		
		lines.addAll(tocIndex, tocLines);
		
		System.out.println("Writing markdown and HTML");
		MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
		System.out.println("DONE");
	}
	
	public static List<String> getCatalogSummarytable(ETAS_Config config, int numProcessed, List<ETAS_EqkRupture> triggerRups, List<ETAS_EqkRupture> histRups) {
		TableBuilder builder = MarkdownUtils.tableBuilder();
		builder.addLine(" ", config.getSimulationName() == null ? "ETAS Simulation" : config.getSimulationName());
		if (numProcessed < config.getNumSimulations())
			builder.addLine("Num Simulations", numProcessed+" (incomplete)");
		else
			builder.addLine("Num Simulations", numProcessed+"");
		builder.addLine("Start Time", df.format(new Date(config.getSimulationStartTimeMillis())));
		builder.addLine("Start Time Epoch Milliseconds", config.getSimulationStartTimeMillis()+"");
		builder.addLine("Duration", ETAS_AbstractPlot.getTimeLabel(config.getDuration(), true));
		builder.addLine("Includes Spontaneous?", config.isIncludeSpontaneous()+"");
		addTriggerLines(builder, "Trigger Ruptures", triggerRups);
		if (config.isTreatTriggerCatalogAsSpontaneous())
			addTriggerLines(builder, "Historical Ruptures", histRups);
		else
			addTriggerLines(builder, "Trigger Ruptures", histRups);
		return builder.build();
	}
	
	private static void addTriggerLines(TableBuilder builder, String name, List<ETAS_EqkRupture> triggerRups) {
		if (triggerRups == null || triggerRups.isEmpty()) {
			builder.addLine(name, "*(none)*");
		} else {
			if (triggerRups.size() > 10) {
				double firstMag = 0d;
				long firstOT = Long.MAX_VALUE;
				double lastMag = 0d;
				long lastOT = Long.MIN_VALUE;
				long biggestOT = Long.MIN_VALUE;
				double maxMag = 0d;
				for (ETAS_EqkRupture rup : triggerRups) {
					double mag = rup.getMag();
					long ot = rup.getOriginTime();
					
					if (mag > maxMag) {
						maxMag = mag;
						biggestOT = ot;
					}
					
					if (ot < firstOT) {
						firstOT = ot;
						firstMag = mag;
					}
					
					if (ot > lastOT) {
						lastOT = ot;
						lastMag = mag;
					}
				}
				builder.addLine(name, triggerRups.size()+" Trigger Ruptures");
				builder.addLine(" ", "First: M"+ETAS_AbstractPlot.optionalDigitDF.format(firstMag)+" at "+df.format(new Date(firstOT)));
				builder.addLine(" ", "Last: M"+ETAS_AbstractPlot.optionalDigitDF.format(lastMag)+" at "+df.format(new Date(lastOT)));
				builder.addLine(" ", "Largest: M"+ETAS_AbstractPlot.optionalDigitDF.format(maxMag)+" at "+df.format(new Date(biggestOT)));
			}
		}
	}

	public static final SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss z");
	static {
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	
	public static final Comparator<BinaryFilteredOutputConfig> binaryOutputComparator =
			new Comparator<ETAS_Config.BinaryFilteredOutputConfig>() {
		
		@Override
		public int compare(BinaryFilteredOutputConfig o1, BinaryFilteredOutputConfig o2) {
			if (o1.isDescendantsOnly() != o2.isDescendantsOnly()) {
				if (o1.isDescendantsOnly())
					return -1;
				return 1;
			}
			Double mag1 = o1.getMinMag();
			Double mag2 = o2.getMinMag();
			if (mag1 == null)
				mag1 = Double.NEGATIVE_INFINITY;
			if (mag2 == null)
				mag2 = Double.NEGATIVE_INFINITY;
			if (mag1 != mag2)
				return Double.compare(mag1, mag2);
			return 0;
		}
	};
}
