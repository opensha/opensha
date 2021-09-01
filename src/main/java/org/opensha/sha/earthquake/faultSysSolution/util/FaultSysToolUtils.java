package org.opensha.sha.earthquake.faultSysSolution.util;

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

public class FaultSysToolUtils {
	
	public static int defaultNumThreads() {
		return Integer.max(1, Integer.min(31, Runtime.getRuntime().availableProcessors()-2));
	}
	
	public static String enumOptions(Class<? extends Enum<?>> enumClass) {
		return Arrays.stream(enumClass.getEnumConstants()).map(c -> c.name()).collect(Collectors.joining(", "));
	}
	
	public static Option threadsOption() {
		Option threadsOption = new Option("t", "threads", true,
				"Number of calculation threads. Default is the lesser of 31 and the number of processors on "
				+ "the system minus 2: "+defaultNumThreads());
		threadsOption.setRequired(false);
		return threadsOption;
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
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp(ClassUtils.getClassNameWithoutPackage(mainClass),
					options, true );
			System.exit(2);
			return null;
		}
		
		return cmd;
	}

}
