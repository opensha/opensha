package scratch.UCERF3.erf.ETAS.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.netlib.util.intW;
import org.opensha.commons.util.ClassUtils;
import org.opensha.commons.util.ExceptionUtils;

import com.google.common.base.Preconditions;

import edu.usc.kmilner.mpj.taskDispatch.AsyncPostBatchHook;
import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO;
import scratch.UCERF3.erf.ETAS.ETAS_CatalogIO.BinarayCatalogsMetadataIterator;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Config.BinaryFilteredOutputConfig;
import scratch.UCERF3.erf.ETAS.launcher.ETAS_Launcher.DebugLevel;

public class MPJ_ETAS_Launcher extends MPJTaskCalculator {

	private ETAS_Config config;
	private ETAS_Launcher launcher;
	
	private ETAS_BinaryWriter binaryWriter;
	private boolean autoClean;

	public MPJ_ETAS_Launcher(CommandLine cmd, ETAS_Config config) throws IOException {
		super(cmd);
		this.config = config;
		this.shuffle = false; // execute tasks in order
		debug("building launcher");
		Long randSeed = config.getRandomSeed();
		if (randSeed == null)
			randSeed = System.nanoTime() + (long)(rank*new Random().nextInt());
		boolean scratchLink = cmd.hasOption("scratch-dir") && config.hasBinaryOutputFilters();
		this.launcher = new ETAS_LauncherPrintWrapper(config, rank == 0 && !scratchLink, randSeed);
		if (rank == 0)
			this.launcher.setDebugLevel(DebugLevel.FINE);
		else
			this.launcher.setDebugLevel(DebugLevel.INFO);
		
		if (rank == 0 && config.hasBinaryOutputFilters()) {
			binaryWriter = new ETAS_BinaryWriter(config.getOutputDir(), config);

			autoClean = cmd.hasOption("clean");
			postBatchHook = new BinaryConsolidateHook();
		}
		if (scratchLink) {
			File outputDir = config.getOutputDir();
			if (rank == 0)
				ETAS_Launcher.waitOnDirCreation(outputDir, 10, 2000);
			
			File scratchDir = new File(cmd.getOptionValue("scratch-dir"));
			if (rank == 0)
				ETAS_Launcher.waitOnDirCreation(scratchDir, 10, 2000);
			File scratchSubDir = new File(scratchDir, outputDir.getName());
			if (rank == 0)
				ETAS_Launcher.waitOnDirCreation(scratchSubDir, 10, 2000);
			
			// build link to results dir in scratch dir
			File resultsDir = ETAS_Launcher.getResultsDir(outputDir);
			
			File scratchResultsDir = new File(scratchSubDir, "results");
			if (rank == 0) {
				ETAS_Launcher.waitOnDirCreation(scratchResultsDir, 10, 2000);
				if (!resultsDir.exists()) {
					debug("Creating link to scratch results dir: "
							+scratchResultsDir.getAbsolutePath());
					Files.createSymbolicLink(resultsDir.toPath(), scratchResultsDir.toPath());
				}
			}
		}
		if (cmd.hasOption("temp-dir")) {
			File tempDir = new File(cmd.getOptionValue("temp-dir"));
			ETAS_Launcher.waitOnDirCreation(tempDir, 10, 2000);
			File tempSubDir = new File(tempDir, config.getOutputDir().getName());
			ETAS_Launcher.waitOnDirCreation(tempSubDir, 10, 2000);
			launcher.setTempDir(tempSubDir);
		}
	}
	
	@Override
	protected Collection<Integer> getDoneIndexes() {
		if (binaryWriter != null)
			return binaryWriter.getDoneIndexes();
		return null;
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
		
		private BinaryCleanHook cleanHook;

		public BinaryConsolidateHook() {
			super(1);
			if (autoClean)
				cleanHook = new BinaryCleanHook();
		}

		@Override
		protected void batchProcessedAsync(int[] batch, int processIndex) {
			debug("running async post-batch hook for process "+processIndex+". "+getCountsString());
			debug("async post-batch estimates: "+getRatesString());
			try {
				for (int index : batch) {
					File catalogDir = launcher.getResultsDir(index);
					binaryWriter.processCatalog(index, catalogDir);
				}
				binaryWriter.flushWriters();
			} catch (IOException e) {
				System.err.println("Exception processing async!");
				e.printStackTrace();
				throw ExceptionUtils.asRuntimeException(e);
			}
			debug("done running async post-batch hook for process "+processIndex+". "+getCountsString());
			if (cleanHook != null)
				cleanHook.batchProcessed(batch, processIndex);
		}

		@Override
		public void shutdown() {
			super.shutdown();
			if (cleanHook != null) {
				List<Runnable> awaiting = cleanHook.shutdownNow();
				if (awaiting != null)
					debug("Aborting cleanup before it's done. Batches: "+awaiting.size()
						+". Estimates if allowed to continue: "+cleanHook.getCountsString());
			}
		}
		
	}
	
	private class BinaryCleanHook extends AsyncPostBatchHook {
		
		public BinaryCleanHook() {
			super(1);
			Preconditions.checkState(autoClean);
		}
		
		private SimpleFileVisitor<Path> deleteVisitor = new SimpleFileVisitor<Path>() {
		   @Override
		   public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		       Files.delete(file);
		       return FileVisitResult.CONTINUE;
		   }

		   @Override
		   public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
		       Files.delete(dir);
		       return FileVisitResult.CONTINUE;
		   }
		};

		@Override
		protected void batchProcessedAsync(int[] batch, int processIndex) {
			debug("running async cleanup hook for process "+processIndex+". "+getCountsString());
			debug("cleanup async post-batch estimates: "+getRatesString());
			for (int index : batch) {
				File catalogDir = launcher.getResultsDir(index);
				debug("removing completed directory: "+catalogDir.getAbsolutePath());
				try {
					Path directory = catalogDir.toPath();
					Files.walkFileTree(directory, deleteVisitor);
				} catch (Exception e) {
					debug("exception removing completed directory, skipping: "
							+catalogDir.getAbsolutePath());
					e.printStackTrace();
				}
			}
			debug("done running async cleanup post-batch hook for process "+processIndex+". "+getCountsString());
		}
		
	}

	@Override
	protected int getNumTasks() {
		return config.getNumSimulations();
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		launcher.calculate(getNumThreads(), batch, null, config.hasBinaryOutputFilters());
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

		Option scratchDirOption = new Option("scratch", "scratch-dir", true,
				"Scratch directory. If supplied and binary output filters are enabled, the results directory will "
				+ "be written here and symbolically linked back to the output directory.");
		scratchDirOption.setRequired(false);
		ops.addOption(scratchDirOption);

		Option tempDirOption = new Option("temp", "temp-dir", true,
				"Temp directory. If supplied, all in-progress simulations will be written here (can be node-local)"
				+ " and copied to the output directory upon completion");
		tempDirOption.setRequired(false);
		ops.addOption(tempDirOption);

		Option cleanOption = new Option("cl", "clean", false,
				"Flag to automatically clean out finished directories after the results are consolidated into a "
				+ "binary output file");
		cleanOption.setRequired(false);
		ops.addOption(cleanOption);
		
		return ops;
	}

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		try {
			args = MPJTaskCalculator.initMPJ(args);
			
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJ_ETAS_Launcher.class);
			
			args = cmd.getArgs();
			
			if (args.length < 1) {
				System.err.println("USAGE: "+ClassUtils.getClassNameWithoutPackage(MPJ_ETAS_Launcher.class)
						+" [options] <conf-file.json> [...<config-file-N.json>]");
				abortAndExit(2);
			}
			
			for (String arg : args) {
				File confFile = new File(arg);
				System.out.println("Processing configuration: "+arg);
				Preconditions.checkArgument(confFile.exists(),
						"configuration file doesn't exist: "+confFile.getAbsolutePath());
				ETAS_Config config = ETAS_Config.readJSON(confFile);
				
				File outputDir = config.getOutputDir();
				List<BinaryFilteredOutputConfig> outputFilters = config.getBinaryOutputFilters();
				if (outputFilters != null && !outputFilters.isEmpty() && !config.isForceRecalc()) {
					// see if this one is already done
					boolean allDone = true;
					for (BinaryFilteredOutputConfig filter : outputFilters) {
						File binFile = new File(outputDir, filter.getPrefix()+".bin");
						allDone = allDone && binFile.exists() && binFile.length() > 0l;
//						if (binFile.exists()) {
//							System.out.println("Previous output file exists, checking if we're already done: "
//									+binFile.getAbsolutePath());
//							BinarayCatalogsMetadataIterator metadataIt =
//									ETAS_CatalogIO.getBinaryCatalogsMetadataIterator(
//											binFile);
//							int doneCount = 0;
//							try {
//								while (metadataIt.hasNext()) {
//									metadataIt.next();
//									doneCount++;
//								}
//								System.out.println("\t"+doneCount+"/"+config.getNumSimulations()+" are done");
//								allDone = allDone && doneCount == config.getNumSimulations();
//							} catch (Exception e) {
//								System.out.println("\tException reading previous output file");
//								e.printStackTrace();
//								allDone = false;
//							}
//						} else {
//							allDone = false;
//						}
						if (!allDone)
							break;
					}
					if (allDone) {
						System.out.println("Already done, will skip "+arg);
						continue;
					}
				}
				
				System.gc();
				MPJ_ETAS_Launcher driver = new MPJ_ETAS_Launcher(cmd, config);
				driver.run();
			}
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
