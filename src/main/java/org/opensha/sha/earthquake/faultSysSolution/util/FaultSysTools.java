package org.opensha.sha.earthquake.faultSysSolution.util;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.opensha.commons.util.ClassUtils;

import com.google.common.base.Preconditions;

public class FaultSysTools {
	
	public static int defaultNumThreads() {
		int available = Runtime.getRuntime().availableProcessors();
		if (available >= 10)
			available -= 2;
		return Integer.max(1, Integer.min(32, available));
	}
	
	public static String enumOptions(Class<? extends Enum<?>> enumClass) {
		return Arrays.stream(enumClass.getEnumConstants()).map(c -> c.name()).collect(Collectors.joining(", "));
	}
	
	public static Option threadsOption() {
		Option threadsOption = new Option("t", "threads", true,
				"Number of calculation threads. Default is the lesser of 32 and the number of processors on "
				+ "the system: "+defaultNumThreads());
		threadsOption.setRequired(false);
		return threadsOption;
	}
	
	public static Option cacheDirOption() {
		Option cacheOption = new Option("cd", "cache-dir", true,
				"Optional directory to store/load cache files (distances, coulomb, etc) to speed up rupture set building "
				+ "and processing.");
		cacheOption.setRequired(false);
		return cacheOption;
	}
	
	public static int getNumThreads(CommandLine cmd) {
		if (cmd.hasOption("threads"))
			return Integer.parseInt(cmd.getOptionValue("threads"));
		return defaultNumThreads();
	}
	
	/**
	 * Parse command line, or exit and print help if a ParseException is encountered
	 * 
	 * @param options
	 * @param args
	 * @param mainClass
	 * @return
	 */
	public static CommandLine parseOptions(Options options, String[] args, Class<?> mainClass) {
		CommandLineParser parser = new DefaultParser();
		
		CommandLine cmd;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			System.err.println(e.getMessage());
			HelpFormatter formatter = new HelpFormatter();
			int columns = 120;
			String colStr = System.getenv("COLUMNS");
			if (colStr != null && !colStr.isBlank()) {
				try {
					columns = Integer.max(80, Integer.parseInt(colStr));
				} catch (Exception e2) {}
			}
			formatter.printHelp(columns, ClassUtils.getClassNameWithoutPackage(mainClass), null, options, null, true);
			System.exit(2);
			return null;
		}
		
		return cmd;
	}
	
	private static final String s = File.separator;
	
	/**
	 * The local scratch data directory that is ignored by repository commits.
	 */
	public static File DEFAULT_SCRATCH_DATA_DIR =
		new File("src"+s+"main"+s+"resources"+s+"scratchData"+s+"rupture_sets");

	public static File getCacheDir() {
		return getCacheDir(null);
	}

	public static File getCacheDir(CommandLine cmd) {
		if (cmd != null && cmd.hasOption("cache-dir")) {
			File cacheDir = new File(cmd.getOptionValue("cache-dir"));
			Preconditions.checkArgument(cacheDir.exists() || cacheDir.mkdir(),
					"Specified cache directory doesn't exist and could not be created: %s", cacheDir.getAbsolutePath());
			return cacheDir;
		}
		if (!DEFAULT_SCRATCH_DATA_DIR.exists() && !DEFAULT_SCRATCH_DATA_DIR.mkdir())
			return null;
		File cacheDir = new File(DEFAULT_SCRATCH_DATA_DIR, "caches");
		if (!cacheDir.exists() && !cacheDir.mkdir())
			return null;
		return cacheDir;
	}

}
