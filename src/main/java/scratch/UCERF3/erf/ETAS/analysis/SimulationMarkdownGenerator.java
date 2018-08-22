package scratch.UCERF3.erf.ETAS.analysis;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileNameComparator;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;

import com.google.common.base.Preconditions;

import scratch.UCERF3.FaultSystemSolution;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_EqkRupture;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher;

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
					+ "2018_08_07-MojaveM7-noSpont-10yr");
			File configFile = new File(simDir, "config.json");
			File inputFile = new File(simDir, "results_m5_preserve_chain.bin");
			args = new String[] { configFile.getAbsolutePath(), inputFile.getAbsolutePath() };
		}
		Options options = createOptions();
		
		CommandLineParser parser = new DefaultParser();
		
		String syntax = ClassUtils.getClassNameWithoutPackage(SimulationMarkdownGenerator.class)
				+" [options] <etas-config.json> <binary-catalogs-file.bin OR results directory>";
		
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
		if (args.length != 2) {
			System.err.println("USAGE: "+syntax);
			System.exit(2);
			throw new IllegalStateException("Unreachable");
		} else {
			configFile = new File(args[0]);
			inputFile = new File(args[1]);
		}
		
		Preconditions.checkState(configFile.exists(), "ETAS config file doesn't exist: "+configFile.getAbsolutePath());
		ETAS_Config config = ETAS_Config.readJSON(configFile);
		
		File outputDir = config.getOutputDir();
		if (!outputDir.exists() && !outputDir.mkdir()) {
			System.out.println("Output dir doesn't exist and can't be created, "
					+ "assuming that it was computed remotely: "+outputDir.getAbsolutePath());
			outputDir = inputFile.getParentFile();
			System.out.println("Using parent directory of input file as output dir: "+outputDir.getAbsolutePath());
		}
		Preconditions.checkState(outputDir.exists() || outputDir.mkdir(),
				"Output dir doesn't exist and couldn't be created: %s", outputDir.getAbsolutePath());
		File plotsDir = new File(outputDir, "plots");
		Preconditions.checkState(plotsDir.exists() || plotsDir.mkdir(),
				"Plot dir doesn't exist and couldn't be created: %s", plotsDir.getAbsolutePath());
		
		List<ETAS_AbstractPlot> plots = new ArrayList<>();
		
		boolean skipMaps = cmd.hasOption("no-maps");
		
		ETAS_Launcher launcher = new ETAS_Launcher(config, false);
		
		boolean hasTriggers = config.hasTriggers();
		
		boolean annualizeMFDs = !hasTriggers;
		if (hasTriggers) {
			plots.add(new ETAS_MFD_Plot(config, launcher, "mag_num_cumulative", annualizeMFDs, true));
			plots.add(new ETAS_HazardChangePlot(config, launcher, "hazard_change_100km", 100d));
		} else {
			plots.add(new ETAS_MFD_Plot(config, launcher, "mfd", annualizeMFDs, true));
		}
		plots.add(new ETAS_FaultParticipationPlot(config, launcher, "fault_participation", annualizeMFDs, skipMaps));
		if (!skipMaps)
			plots.add(new ETAS_GriddedNucleationPlot(config, launcher, "gridded_nucleation", annualizeMFDs));
		
		Iterator<List<ETAS_EqkRupture>> catalogsIterator;
		if (inputFile.isDirectory())
			catalogsIterator = new ETAS_ResultsDirIterator(inputFile);
		else
			catalogsIterator = ETAS_CatalogIO.getBinaryCatalogsIterable(inputFile, 0).iterator();
		
		boolean filterSpontaneous = false;
		for (ETAS_AbstractPlot plot : plots)
			filterSpontaneous = filterSpontaneous || plot.isFilterSpontaneous();
		
		FaultSystemSolution fss = launcher.checkOutFSS();
		
		int numProcessed = 0;
		int modulus = 10;
		while (catalogsIterator.hasNext()) {
			if (numProcessed % modulus == 0) {
				System.out.println("Processing catalog "+numProcessed);
				if (numProcessed == modulus*10)
					modulus *= 10;
			}
			List<ETAS_EqkRupture> catalog;
			try {
				catalog = catalogsIterator.next();
				numProcessed++;
			} catch (Exception e) {
				e.printStackTrace();
				System.err.flush();
				System.out.println("Partial catalog detected or other error, stopping with "+numProcessed+" catalogs");
				break;
			}
			List<ETAS_EqkRupture> triggeredOnlyCatalog = null;
			if (filterSpontaneous)
				triggeredOnlyCatalog = ETAS_Launcher.getFilteredNoSpontaneous(config, catalog);
			for (ETAS_AbstractPlot plot : plots)
				plot.doProcessCatalog(catalog, triggeredOnlyCatalog, fss);
		}
		
		System.out.println("Processed "+numProcessed+" catalogs");
		
		List<String> lines = new ArrayList<>();
		
		String simName = config.getSimulationName();
		if (simName == null || simName.isEmpty())
			simName = "ETAS Simulation";
		
		lines.add("# "+simName+" Results");
		lines.add("");
		
		TableBuilder builder = MarkdownUtils.tableBuilder();
		builder.addLine(" ", simName);
		if (numProcessed < config.getNumSimulations())
			builder.addLine("Num Simulations", numProcessed+" (incomplete)");
		else
			builder.addLine("Num Simulations", numProcessed+"");
		builder.addLine("Start Time", df.format(new Date(config.getSimulationStartTimeMillis())));
		builder.addLine("Start Time Epoch Milliseconds", config.getSimulationStartTimeMillis()+"");
		builder.addLine("Duration", ETAS_AbstractPlot.getTimeLabel(config.getDuration(), true));
		builder.addLine("Includes Spontaneous?", config.isIncludeSpontaneous()+"");
		List<ETAS_EqkRupture> triggerRups = launcher.getTriggerRuptures();
		addTriggerLines(builder, "Trigger Ruptures", triggerRups);
		List<ETAS_EqkRupture> histRups = launcher.getHistQkList();
		if (config.isTreatTriggerCatalogAsSpontaneous())
			addTriggerLines(builder, "Historical Ruptures", histRups);
		else
			addTriggerLines(builder, "Trigger Ruptures", histRups);
		lines.addAll(builder.build());
		lines.add("");
		
		int tocIndex = lines.size();
		String topLink = "*[(top)](#table-of-contents)*";
		lines.add("");
		
		System.out.println("Finalizing plots");
		for (ETAS_AbstractPlot plot : plots) {
			System.out.println("Finalizing "+ClassUtils.getClassNameWithoutPackage(plot.getClass()));
			plot.finalize(plotsDir, fss);
			
			List<String> plotLines = plot.generateMarkdown(plotsDir.getName(), "##", topLink);
			
			if (plotLines != null)
				lines.addAll(plotLines);
		}
		
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
	
	private static class ETAS_ResultsDirIterator implements Iterator<List<ETAS_EqkRupture>> {
		
		private LinkedList<File> files;
		
		public ETAS_ResultsDirIterator(File dir) {
			files = new LinkedList<>();
			File[] subDirs = dir.listFiles();
			Arrays.sort(subDirs, new FileNameComparator());
			for (File subDir : subDirs) {
				if (ETAS_Launcher.isAlreadyDone(subDir))
					files.add(subDir);
			}
		}

		@Override
		public boolean hasNext() {
			return !files.isEmpty();
		}

		@Override
		public List<ETAS_EqkRupture> next() {
			File simFile = getSimFile(files.removeFirst());
			try {
				return ETAS_CatalogIO.loadCatalog(simFile);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
		private File getSimFile(File dir) {
			File eventsFile = new File(dir, "simulatedEvents.txt");
			if (!eventsFile.exists())
				eventsFile = new File(dir, "simulatedEvents.bin");
			if (!eventsFile.exists())
				eventsFile = new File(dir, "simulatedEvents.bin.gz");
			if (eventsFile.exists())
				return eventsFile;
			throw new IllegalStateException("No events files found in "+dir.getAbsolutePath());
		}
		
	}
}
