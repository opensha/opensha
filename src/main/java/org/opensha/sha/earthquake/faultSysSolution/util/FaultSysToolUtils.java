package org.opensha.sha.earthquake.faultSysSolution.util;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

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

}
