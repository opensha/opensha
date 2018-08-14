package scratch.UCERF3.erf.ETAS.launcher;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import org.apache.commons.cli.CommandLine;
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
		this.launcher = new ETAS_LauncherPrintWrapper(config);
		if (rank == 0)
			this.launcher.setDebugLevel(DebugLevel.FINE);
		else
			this.launcher.setDebugLevel(DebugLevel.INFO);
		
		launcher.setRandom(new Random(System.nanoTime() + (long)(rank*new Random().nextInt())));
		
		if (rank == 0 && config.hasBinaryOutputFilters()) {
			binaryWriter = new ETAS_BinaryWriter(config.getOutputDir(), config);
			
			postBatchHook = new BinaryConsolidateHook();
		}
	}
	
	/**
	 * Wrapper for ETAS_Launcher to re-route debug prints to the MPJTaskCalculator method
	 * @author kevin
	 *
	 */
	private class ETAS_LauncherPrintWrapper extends ETAS_Launcher {

		public ETAS_LauncherPrintWrapper(ETAS_Config config) throws IOException {
			super(config);
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
