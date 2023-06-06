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
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.util.ClassUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

import com.google.common.base.Preconditions;

public class FaultSysTools {
	
	/**
	 * Determines the default number of threads from the number of available processors for multithreaded operations.
	 * 
	 * Will return no less than 1 and no more than 32, and 2 will be reserved for other operations if at least 10
	 * processors are available.
	 * @return
	 */
	public static int defaultNumThreads() {
		int available = Runtime.getRuntime().availableProcessors();
		if (available >= 10)
			available -= 2;
		return Integer.max(1, Integer.min(32, available));
	}
	
	/*
	 * Command line utility methods
	 */
	
	/**
	 * 
	 * @param enumClass
	 * @return Comma-separated string of enum names, useful for command line option descriptions
	 */
	public static String enumOptions(Class<? extends Enum<?>> enumClass) {
		return Arrays.stream(enumClass.getEnumConstants()).map(c -> c.name()).collect(Collectors.joining(", "));
	}
	
	/**
	 * 
	 * @return commonly used -t/--threads command line option
	 */
	public static Option threadsOption() {
		Option threadsOption = new Option("t", "threads", true,
				"Number of calculation threads. Default is the lesser of 32 and the number of processors on "
				+ "the system: "+defaultNumThreads());
		threadsOption.setRequired(false);
		return threadsOption;
	}
	
	/**
	 * Parses the number of threads if specified on the command line, otherwise defaulting to {@link #defaultNumThreads()}
	 * @param cmd
	 * @return number of threads to use
	 */
	public static int getNumThreads(CommandLine cmd) {
		if (cmd.hasOption("threads"))
			return Integer.parseInt(cmd.getOptionValue("threads"));
		return defaultNumThreads();
	}
	
	/**
	 * @return commonly used -cd/--cache-dir command line option
	 */
	public static Option cacheDirOption() {
		Option cacheOption = new Option("cd", "cache-dir", true,
				"Optional directory to store/load cache files (distances, coulomb, etc) to speed up rupture set building "
				+ "and processing.");
		cacheOption.setRequired(false);
		return cacheOption;
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

	/**
	 * This returns the default cache directory, located in src/main/resources/scratchData/rupture_sets/caches.
	 * 
	 * The directory will be created if it does not exist, but only if at least the src/main/resources/scratchData
	 * directory already exists (true for OpenSHA projects checked out from eclipse).
	 * 
	 * If the directory does not exist and cannot be created, null will be returned.
	 * 
	 * @return the default scratch directory as a file, or null if it could not be created
	 */
	public static File getCacheDir() {
		return getCacheDir(null);
	}

	/**
	 * Checks for the --cache-dir option (see {@link #cacheDirOption()} and returns that directory if supplied (first
	 * ensuring that it exists or can be created). If that argument was not supplied, returns the default cache directory
	 * according to {@link #getCacheDir()}.
	 * 
	 * @param cmd
	 * @return supplied (or default if no argument supplied). Can be null if none supplied.
	 * @throws IllegalArgumentException if cache directory is supplied but does not exist and can't be created
	 */
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
	
	// discretization parameters for MFDs
	public final static double MIN_MAG = 0.05;
	public final static double DELTA_MAG = 0.1;

	/**
	 * Figures out MFD bounds for the given rupture set. It will start at 0.05 and include at least the rupture set
	 * maximum magnitude, expanded to the ceiling of that rupture set maximum magnitude.
	 * 
	 * @param rupSet
	 * @return
	 */
	public static IncrementalMagFreqDist initEmptyMFD(FaultSystemRupSet rupSet) {
		return initEmptyMFD(rupSet.getMaxMag());
	}

	/**
	 * Figures out MFD bounds for the given maximum magnitude. It will start at 0.05 and include at
	 * least the given maximum magnitude, expanded to the ceiling of that maximum magnitude.
	 * 
	 * @param rupSet
	 * @return
	 */
	public static IncrementalMagFreqDist initEmptyMFD(double maxRupSetMag) {
		return initEmptyMFD(MIN_MAG, maxRupSetMag);
	}
	
	private static double LOWEST_SNAPPED_MIN_MAG = 0.05;
	private static double LOWEST_ALLOWED_MIN_MAG = LOWEST_SNAPPED_MIN_MAG - 0.5*DELTA_MAG;
	private static double LARGEST_SNAPPED_MAX_MAG = 15.05;
	private static double LARGEST_ALLOWED_MAX_MAG = LARGEST_SNAPPED_MAX_MAG + 0.5*DELTA_MAG;
	private static int FULL_REF_SIZE = (int)((LARGEST_SNAPPED_MAX_MAG - LOWEST_SNAPPED_MIN_MAG) / DELTA_MAG) + 1;
	public static EvenlyDiscretizedFunc fullRefGridding = new EvenlyDiscretizedFunc(
			LOWEST_SNAPPED_MIN_MAG, FULL_REF_SIZE, DELTA_MAG);

	/**
	 * Figures out MFD bounds for the given minimum and maximum magnitudes. Each will be snapped to 0.05 magnitude 
	 * bin centers, and the maximum magnitude will be padded by an additional bin (for better plotting).
	 * 
	 * @param rupSet
	 * @return
	 */
	public static IncrementalMagFreqDist initEmptyMFD(double minRupSetMag, double maxRupSetMag) {
		Preconditions.checkState((float)minRupSetMag >= (float)LOWEST_ALLOWED_MIN_MAG,
				"Minimum magnitude not allowed (too small, must be >=%s): %s", minRupSetMag, LOWEST_ALLOWED_MIN_MAG);
		Preconditions.checkState((float)maxRupSetMag <= (float)LARGEST_ALLOWED_MAX_MAG,
				"Maximum magnitude not allowed (too large, must be <=%s): %s", maxRupSetMag, LARGEST_ALLOWED_MAX_MAG);
		Preconditions.checkState(maxRupSetMag >= minRupSetMag,
				"Max mag is less than min? max=%s, min=%s", maxRupSetMag, minRupSetMag);
		int minIndex = fullRefGridding.getClosestXIndex(minRupSetMag);
		int maxIndex = fullRefGridding.getClosestXIndex(maxRupSetMag);
		
		double snappedMin = minRupSetMag == MIN_MAG ? MIN_MAG : fullRefGridding.getX(minIndex);
		
		int NUM_MAG = 2 + maxIndex - minIndex;
		IncrementalMagFreqDist ret = new IncrementalMagFreqDist(snappedMin, NUM_MAG, DELTA_MAG);

		double lowerBin = ret.getMinX() - 0.5*DELTA_MAG;
		Preconditions.checkState((float)minRupSetMag >= (float)lowerBin,
				"Bad MFD gridding with minMag=%s, lowerBinCenter=%s, lowerBinEdge=%s",
				minRupSetMag, ret.get(0).getX(), lowerBin);
		double upperBin = ret.getMaxX() + 0.5*DELTA_MAG;
		Preconditions.checkState((float)maxRupSetMag <= (float)upperBin,
				"Bad MFD gridding with maxMag=%s, upperBinCenter=%s, upperBinEdge=%s",
				maxRupSetMag, ret.get(NUM_MAG-1).getX(), upperBin);

		return ret;
	}

}
