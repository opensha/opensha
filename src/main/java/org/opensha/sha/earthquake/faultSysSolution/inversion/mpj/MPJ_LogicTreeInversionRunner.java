package org.opensha.sha.earthquake.faultSysSolution.inversion.mpj;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.io.archive.ArchiveInput;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfiguration;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.Inversions;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;
import org.opensha.sha.earthquake.faultSysSolution.util.AverageSolutionCreator;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;

import com.google.common.base.Preconditions;

import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;

/**
 * Class for running a full logic tree of inversions with a single job in an MPI environment
 * 
 * @author kevin
 *
 */
public class MPJ_LogicTreeInversionRunner extends MPJTaskCalculator {

	private File outputDir;
	
	private int runsPerBranch = 1;
	
	private InversionConfigurationFactory factory;

	private LogicTree<LogicTreeNode> tree;

	private CommandLine cmd;
	
	private int annealingThreads;
	private int runsPerBundle = 1;

	private boolean reprocess = false;
	private boolean reprocessOnly = false;

	public MPJ_LogicTreeInversionRunner(CommandLine cmd) throws IOException {
		super(cmd);
		this.cmd = cmd;
		this.annealingThreads = Integer.parseInt(cmd.getOptionValue("annealing-threads"));
		Preconditions.checkState(annealingThreads >= 1);
		
		if (cmd.hasOption("runs-per-bundle"))
			runsPerBundle = Integer.parseInt(cmd.getOptionValue("runs-per-bundle"));
		
		this.shuffle = false;
		
		tree = LogicTree.read(new File(cmd.getOptionValue("logic-tree")));
		if (rank == 0)
			debug("Loaded "+tree.size()+" tree nodes");
		
		outputDir = new File(cmd.getOptionValue("output-dir"));
		
		if (rank == 0)
			waitOnDir(outputDir, 5, 1000);
		
		if (cmd.hasOption("runs-per-branch"))
			runsPerBranch = Integer.parseInt(cmd.getOptionValue("runs-per-branch"));
		
		try {
			@SuppressWarnings("unchecked")
			Class<? extends InversionConfigurationFactory> factoryClass = (Class<? extends InversionConfigurationFactory>)
					Class.forName(cmd.getOptionValue("inversion-factory"));
			factory = factoryClass.getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		if (cmd.hasOption("reprocess-only")) {
			Preconditions.checkState(factory.getSolutionLogicTreeProcessor() != null,
					"Can't reprocess if we don't have a solution processor");
			reprocess = true;
			reprocessOnly = true;
		} else {
			reprocess = factory.getSolutionLogicTreeProcessor() != null && cmd.hasOption("reprocess-existing");
		}
		
		File cacheDir = FaultSysTools.getCacheDir(cmd);
		if (cacheDir != null) {
			if (rank == 0)
				waitOnDir(cacheDir, 3, 1000);
			factory.setCacheDir(cacheDir);
		}
		factory.setAutoCache(rank == 0);
		
		debug("Factory type: "+factory.getClass().getName());
		
		if (rank == 0) {
			this.postBatchHook = new AsyncLogicTreeWriter(factory.getSolutionLogicTreeProcessor());
		}
	}
	
	private class AsyncLogicTreeWriter extends AbstractAsyncLogicTreeWriter {
		
		private boolean[] dones;
		
		public AsyncLogicTreeWriter(SolutionProcessor processor) {
			super(outputDir, processor, tree);
			dones = new boolean[getNumTasks()];
		}

		@Override
		public int getNumTasks() {
			return MPJ_LogicTreeInversionRunner.this.getNumTasks();
		}

		@Override
		public void debug(String message) {
			MPJ_LogicTreeInversionRunner.this.debug(message);
		}

		@Override
		public LogicTreeBranch<?> getBranch(int calcIndex) {
			return tree.getBranch(branchForCalcIndex(calcIndex));
		}

		@Override
		public FaultSystemSolution getSolution(LogicTreeBranch<?> branch, int calcIndex) throws IOException {
			dones[calcIndex] = true;
			int branchIndex = branchForCalcIndex(calcIndex);
			List<File> solFiles = new ArrayList<>();
			for (int run=0; run<runsPerBranch; run++) {
				int doneIndex = indexForBranchRun(branchIndex, run);
				if (dones[doneIndex]) {
					solFiles.add(getSolFile(branch, run));
				} else {
					// not all runs for this branch are done
					debug("AsyncLogicTree: not ready, waiting on run "+run+" for branch "+branchIndex
							+" (origIndex="+calcIndex+", checkIndex="+doneIndex+"): "+branch);
					return null;
				}
			}
			
			FaultSystemSolution sol;
			if (runsPerBranch > 1) {
				// see if it was already averaged on a compute node
				String dirName = branch.buildFileName();
				File avgDir = new File(outputDir, dirName);
				Preconditions.checkState(avgDir.exists() || avgDir.mkdir());
				File avgFile = new File(avgDir, "average_solution.zip");
				if (avgFile.exists()) {
					// it was
					debug("AsyncLogicTree: loading external average from "+avgFile.getAbsolutePath());
					sol = FaultSystemSolution.load(new ArchiveInput.ApacheZipFileInput(avgFile));
				} else {
					// need to build it here
					debug("AsyncLogicTree: building average for "+branch);
					FaultSystemSolution[] inputs = new FaultSystemSolution[solFiles.size()];
					for (int i=0; i<inputs.length; i++)
						inputs[i] = FaultSystemSolution.load(solFiles.get(i));
					sol = AverageSolutionCreator.buildAverage(inputs);
					sol.write(avgFile);
				}
			} else {
				sol = FaultSystemSolution.load(new ArchiveInput.ApacheZipFileInput(solFiles.get(0)));
			}
			return sol;
		}

		@Override
		public void abortAndExit(Throwable t, int exitCode) {
			MPJ_LogicTreeInversionRunner.abortAndExit(t, exitCode);
		}
		
	}
	
	private void memoryDebug(String info) {
		if (info == null || info.isBlank())
			info = "";
		else
			info += "; ";
		
		debug(info+memoryString());
	}
	
	static String memoryString() {
		System.gc();
		Runtime rt = Runtime.getRuntime();
		long totalMB = rt.totalMemory() / 1024 / 1024;
		long freeMB = rt.freeMemory() / 1024 / 1024;
		long usedMB = totalMB - freeMB;
		return "mem t/u/f: "+totalMB+"/"+usedMB+"/"+freeMB;
	}
	
	private static void waitOnDir(File dir, int maxRetries, long sleepMillis) {
		int retry = 0;
		while (!(dir.exists() || dir.mkdir())) {
			try {
				Thread.sleep(sleepMillis);
			} catch (InterruptedException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			if (retry++ > maxRetries)
				throw new IllegalStateException("Directory doesn't exist and couldn't be created after "
						+maxRetries+" retries: "+dir.getAbsolutePath());
		}
	}

	@Override
	protected int getNumTasks() {
		return tree.size()*runsPerBranch;
	}
	
	protected File getSolFile(LogicTreeBranch<?> branch, int run) {
		String suffix = "";
		if (runsPerBranch > 1)
			suffix = "_run"+run;
		File runDir = branch.getBranchDirectory(outputDir, true, suffix);
		
		return new File(runDir, "solution.zip");
	}
	
	private int branchForCalcIndex(int index) {
		return index / runsPerBranch;
	}
	
	private int runForCalcIndex(int index) {
		return index % runsPerBranch;
	}
	
	private int indexForBranchRun(int branchIndex, int runIndex) {
		return branchIndex * runsPerBranch + runIndex;
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		if (runsPerBundle > 1) {
			ExecutorService exec = Executors.newFixedThreadPool(runsPerBundle);
			
			List<Future<?>> futures = new ArrayList<>();
			for (int index : batch)
				futures.add(exec.submit(new CalcRunnable(index)));
			
			for (Future<?> future : futures) {
				try {
					future.get();
				} catch (InterruptedException | ExecutionException e) {
					exec.shutdown();
					throw e;
				}
			}
			
			exec.shutdown();
		} else {
			for (int index : batch) {
				new CalcRunnable(index).run();
			}
		}
		
		if (runsPerBranch > 1) {
			// see if I can do an average in parallel so that the post-batch hook on the root node doesn't need to
			for (int index : batch) {
				int branchIndex = branchForCalcIndex(index);
				int run = runForCalcIndex(index);
				if (run == runsPerBranch - 1) {
					// it's the last one for this branch, lets see if all of the previous ones are done
					boolean allDone = true;
					LogicTreeBranch<LogicTreeNode> branch = tree.getBranch(branchIndex);
					List<File> solFiles = new ArrayList<>();
					for (int oRun=0; oRun<runsPerBranch; oRun++) {
						File solFile = getSolFile(branch, oRun);
						if (solFile.exists()) {
							solFiles.add(solFile);
						} else {
							allDone = false;
							break;
						}
					}
					if (allDone) {
						Preconditions.checkState(solFiles.size() == runsPerBranch);
						debug("Branch index "+branchIndex+" is all done, doing a compute node average for "+branch);
						File runDir = branch.getBranchDirectory(outputDir, true);
						File outputFile = new File(runDir, "average_solution.zip");
						AverageSolutionCreator.average(outputFile, solFiles);
					}
				}
			}
		}
	}

	private class CalcRunnable implements Runnable {
		
		private int index;

		public CalcRunnable(int index) {
			this.index = index;
		}

		@Override
		public void run() {
			int branchIndex = branchForCalcIndex(index);
			int run = runForCalcIndex(index);
			
			LogicTreeBranch<LogicTreeNode> branch = tree.getBranch(branchIndex);
			
			debug("index "+index+" is branch "+branchIndex+" run "+run+": "+branch);
			
			File solFile = getSolFile(branch, run);
			
			boolean exists = solFile.exists();
			Preconditions.checkState(!reprocessOnly || exists,
					"--reprocess-only was supplied but no solution exists for breanch %s: %s", branch, solFile.getAbsolutePath());
			
			if (exists) {
				debug(solFile.getAbsolutePath()+" exists, testing loading...");
				try {
					FaultSystemSolution sol = FaultSystemSolution.load(solFile);
					debug("skipping "+index+" (already done)");
					if (reprocess) {
						FaultSystemRupSet origRupSet = sol.getRupSet();
						
						SolutionProcessor processor = factory.getSolutionLogicTreeProcessor();
						// process the rupture set
						FaultSystemRupSet rpRupSet = factory.updateRuptureSetForBranch(origRupSet, branch);
						// build an inversion configuration so that any adjustments at that stage are processed
						factory.buildInversionConfig(rpRupSet, branch, 8);
						if (rpRupSet != origRupSet) {
							// replaced the rupture set, need to copy the solution over to use the new rup set
							for (OpenSHA_Module module : origRupSet.getModules()) {
								if (!rpRupSet.hasModuleSuperclass(module.getClass())) {
//									System.out.println("Adding module to replacement rupture set: "+module.getName()+" ("+module.getClass()+")");
									// this is a module not present in the reproduction and won't evict anything, add it
									rpRupSet.addModule(module);
								}
							}
							sol = sol.copy(rpRupSet.getArchive());
						}
						// process the solution
						sol = processor.processSolution(sol, branch);
						// write out the reprocessed solution
						sol.write(solFile);
					}
					return;
				} catch (Exception e) {
					if (reprocessOnly) {
						debug("Failed to reprocess "+index+", and --reprocess-only is enabled: "+e.getMessage());
						abortAndExit(e);
					}
					debug("Failed to load, re-inverting: "+e.getMessage());
				}
			}
			
			memoryDebug("Beginning config for "+index);
			
			FaultSystemSolution sol;
			
			try {
				sol = Inversions.run(factory, branch, annealingThreads, cmd);
				sol.write(solFile);
			} catch (IOException e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
			sol = null;
			memoryDebug("DONE "+index);
		}
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		if (rank == 0) {
			memoryDebug("waiting for any post batch hook operations to finish");
			((AsyncLogicTreeWriter)postBatchHook).shutdown();
			memoryDebug("post batch hook done");
		}
	}
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		ops.addRequiredOption("lt", "logic-tree", true, "Path to logic tree JSON file");
		ops.addRequiredOption("od", "output-dir", true, "Path to output directory");
		ops.addOption(FaultSysTools.cacheDirOption());
		ops.addRequiredOption("at", "annealing-threads", true, "Number of annealing threads per inversion");
		ops.addOption("rpb", "runs-per-branch", true, "Runs per branch (default is 1)");
		ops.addOption("rpb", "runs-per-bundle", true, "Simultaneous runs to executure (default is 1)");
		ops.addRequiredOption("ifc", "inversion-factory", true, "Inversion configuration factory classname");
		ops.addOption("rpe", "reprocess-existing", false, "Flag to enable re-processing of already completed solutions"
				+ "with the factory's SolutionProcessor before branch averaging");
		ops.addOption(null, "reprocess-only", false, "Flag to only re-process already completed solutions "
				+ "with the factory's SolutionProcessor before branch averaging, ensuring that all inversions are already completed");
		
		for (Option op : InversionConfiguration.createSAOptions().getOptions())
			ops.addOption(op);
		
		return ops;
	}

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		try {
			args = MPJTaskCalculator.initMPJ(args);
			
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJ_LogicTreeInversionRunner.class);
			
			MPJ_LogicTreeInversionRunner driver = new MPJ_LogicTreeInversionRunner(cmd);
			driver.run();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
