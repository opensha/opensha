package scratch.UCERF3.erf.ETAS.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;

import com.google.common.base.Preconditions;

import edu.usc.kmilner.mpj.taskDispatch.AsyncPostBatchHook;
import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher.DebugLevel;

public class MPJ_ETAS_Launcher extends MPJTaskCalculator {

	private ETAS_Config config;
	private ETAS_Launcher launcher;
	
	private ETAS_BinaryWriter binaryWriter;

	public MPJ_ETAS_Launcher(CommandLine cmd, ETAS_Config config) throws IOException {
		super(cmd);
		this.config = config;
		this.shuffle = false; // execute tasks in order
		debug("building launcher");
		Long randSeed = config.getRandomSeed();
		if (randSeed == null)
			randSeed = System.nanoTime() + (long)(rank*new Random().nextInt());
		boolean tmpLink = cmd.hasOption("temp-dir") && config.hasBinaryOutputFilters();
		this.launcher = new ETAS_LauncherPrintWrapper(config, rank == 0 && !tmpLink, randSeed);
		if (rank == 0)
			this.launcher.setDebugLevel(DebugLevel.FINE);
		else
			this.launcher.setDebugLevel(DebugLevel.INFO);
		
		if (rank == 0 && config.hasBinaryOutputFilters()) {
			binaryWriter = new ETAS_BinaryWriter(config.getOutputDir(), config);
			
			postBatchHook = new BinaryConsolidateHook();
		}
		if (tmpLink) {
			File outputDir = config.getOutputDir();
			if (rank == 0)
				ETAS_Launcher.waitOnDirCreation(outputDir, 10, 2000);
			
			File scratchDir = new File(cmd.getOptionValue("scratch-dir"));
			File scratchSubDir = new File(scratchDir, outputDir.getName());
			if (rank == 0)
				ETAS_Launcher.waitOnDirCreation(scratchSubDir, 10, 2000);
			
			// build link to results dir in temp dir
			File resultsDir = ETAS_Launcher.getResultsDir(outputDir);
			
			File scratchResultsDir = new File(scratchSubDir, "results");
			if (rank == 0) {
				ETAS_Launcher.waitOnDirCreation(scratchResultsDir, 10, 2000);
				if (resultsDir.exists()) {
					debug("Creating link to scratch results dir: "
							+scratchResultsDir.getAbsolutePath());
				}
				Files.createSymbolicLink(resultsDir.toPath(), scratchResultsDir.toPath());
			}
		}
	}
	
	/**
	 * Wrapper for ETAS_Launcher to re-route debug prints to the MPJTaskCalculator method
	 * @author kevin
	 *
	 */
	private class ETAS_LauncherPrintWrapper extends ETAS_Launcher {

		public ETAS_LauncherPrintWrapper(ETAS_Config config, boolean mkdirs, Long randSeed) throws IOException {
			super(config, mkdirs, randSeed);
		}

		@Override
		public void debug(DebugLevel level, String message) {
			if (this.debugLevel.shouldPrint(level))
				MPJ_ETAS_Launcher.this.debug(message);
		}
		
	}
	
	private class BinaryConsolidateHook extends AsyncPostBatchHook {

		public BinaryConsolidateHook() {
			super(1);
		}

		@Override
		protected void batchProcessedAsync(int[] batch, int processIndex) {
			debug("running async post-batch hook for process "+processIndex+". "+getCountsString());
			debug("async post-batch extimates: "+getRatesString());
			try {
				for (int index : batch) {
					File catalogDir = launcher.getResultsDir(index);
					binaryWriter.processCatalog(catalogDir);
				}
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			debug("done running async post-batch hook for process "+processIndex+". "+getCountsString());
		}
		
	}

	@Override
	protected int getNumTasks() {
		return config.getNumSimulations();
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		launcher.calculateBatch(getNumThreads(), batch);
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		launcher.shutdownExecutor();
		if (binaryWriter != null) {
			((BinaryConsolidateHook)postBatchHook).shutdown();
			binaryWriter.finalize();
		}
	}
	
	protected static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();

		Option tmpDirOption = new Option("scratch", "scratch-dir", true,
				"Scratch directory. If supplied and binary output filters are enabled, the results directory will "
				+ "be written here and symbolically lined back to the output directory.");
		tmpDirOption.setRequired(false);
		ops.addOption(tmpDirOption);
		
		return ops;
	}

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		try {
			args = MPJTaskCalculator.initMPJ(args);
			
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJ_ETAS_Launcher.class);
			
			args = cmd.getArgs();
			
			if (args.length != 1) {
				System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(MPJ_ETAS_Launcher.class)
						+" [options] <conf-file.json>");
				abortAndExit(2);
			}
			
			File confFile = new File(args[0]);
			Preconditions.checkArgument(confFile.exists(),
					"configuration file doesn't exist: "+confFile.getAbsolutePath());
			ETAS_Config config = ETAS_Config.readJSON(confFile);
			
			MPJ_ETAS_Launcher driver = new MPJ_ETAS_Launcher(cmd, config);
			driver.run();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
