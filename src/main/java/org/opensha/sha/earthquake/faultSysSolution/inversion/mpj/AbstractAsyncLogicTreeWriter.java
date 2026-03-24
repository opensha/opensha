package org.opensha.sha.earthquake.faultSysSolution.inversion.mpj;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.opensha.commons.logicTree.BranchWeightProvider;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.io.archive.ArchiveOutput;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree.SolutionProcessor;
import org.opensha.sha.earthquake.faultSysSolution.util.BranchAverageSolutionCreator;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import edu.usc.kmilner.mpj.taskDispatch.AsyncPostBatchHook;
import gov.usgs.earthquake.nshmp.erf.logicTree.TectonicRegionBranchTreeNode;

public abstract class AbstractAsyncLogicTreeWriter extends AsyncPostBatchHook {

	private File outputDir;
	private SolutionProcessor processor;
	protected BranchWeightProvider weightProv;
	
	protected Map<String, BranchAverageSolutionCreator> baCreators;
	protected SolutionLogicTree.FileBuilder sltBuilder;
	
	private CompletableFuture<Void> writingLoadedFuture;
	private CompletableFuture<Void> processingLoadedFuture;
	
	private boolean[] dones;
	private LogicTree<?> tree;

	private Map<String, double[]> rankWeights;
	private Map<String, List<List<LogicTreeBranch<?>>>> rankBranches;
	private int numProcesses;

	private Stopwatch processWatch = Stopwatch.createUnstarted();
	private Stopwatch loadWatch = Stopwatch.createUnstarted();
	private Stopwatch sltWatch = Stopwatch.createUnstarted();
	private Stopwatch baWatch = Stopwatch.createUnstarted();

	public AbstractAsyncLogicTreeWriter(File outputDir, SolutionProcessor processor, LogicTree<?> tree,
			boolean buildBA, int numProcesses) {
		super(1);
		this.outputDir = outputDir;
		this.processor = processor;
		this.tree = tree;
		this.numProcesses = numProcesses;
		this.weightProv = tree.getWeightProvider();
		this.baCreators = buildBA ? new HashMap<>() : null;
		
		dones = new boolean[getNumTasks()];
		rankWeights = new HashMap<>();
		rankBranches = new HashMap<>();
	}
	
	public File getOutputFile(File resultsDir) {
		return new File(resultsDir.getParentFile(), resultsDir.getName()+".zip");
	}
	
	public File getBAOutputFile(File resultsDir, String baPrefix) {
		return baFile(resultsDir, baPrefix);
	}
	
	public abstract int getNumTasks();
	
	public abstract void debug(String message);
	
	public abstract LogicTreeBranch<?> getBranch(int calcIndex);
	
	public abstract FaultSystemSolution getSolution(LogicTreeBranch<?> branch, int calcIndex) throws IOException;
	
	public abstract void abortAndExit(Throwable t, int exitCode);

	@Override
	protected void batchProcessedAsync(int[] batch, int processIndex) {
		memoryDebug("AsyncLogicTree: beginning async call with batch size "
				+batch.length+" from "+processIndex+": "+getCountsString());
		
		synchronized (dones) {
			try {
				if (sltBuilder == null) {
					sltBuilder = new SolutionLogicTree.FileBuilder(processor, new ArchiveOutput.ApacheZipFileOutput( getOutputFile(outputDir)));
					sltBuilder.setWeightProv(weightProv);
				}
				
				for (int index : batch) {
					dones[index] = true;
					
					doProcessIndex(index, processIndex);
				}
			} catch (Exception e) {
				e.printStackTrace();
				abortAndExit(e, 2);
			}
		}
		
		memoryDebug("AsyncLogicTree: exiting async process, stats: "+getCountsString());
	}

	protected void doProcessIndex(int index, int processIndex) throws IOException {
		processWatch.start();
		LogicTreeBranch<?> branch = getBranch(index);
		
		debug("AsyncLogicTree: calcDone "+index+" = branch "+branch);
		
		loadWatch.start();
		FaultSystemSolution sol = getSolution(branch, index);
		loadWatch.stop();
		if (sol == null) {
			processWatch.stop();
			return;
		}
		
		// do this part asynchronously so that we can start loading the next one
		if (writingLoadedFuture != null) {
			sltWatch.start();
			// wait until we're done writing the previous one
			writingLoadedFuture.join();
			sltWatch.stop();
		}
		// launch write asynchronously
		writingLoadedFuture = CompletableFuture.runAsync(() -> {
			try {
				sltBuilder.solution(sol, branch);
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		});
		
		if (baCreators != null) {
			baWatch.start();
			if (processingLoadedFuture != null)
				// wait until we're done processing the previous one
				processingLoadedFuture.join();
			// launch processing asynchronously
			processingLoadedFuture = CompletableFuture.runAsync(() -> {
				if (!processBA(baCreators, sol, branch, weightProv)) {
					debug("AsyncLogicTree won't branch average; failed or all levels affect "+FaultSystemRupSet.RUP_SECTS_FILE_NAME);
					baCreators = null;
				}
			});
			baWatch.stop();
		}
		

		
		String baPrefix = AbstractAsyncLogicTreeWriter.getBA_prefix(branch);
		Preconditions.checkNotNull(baPrefix);
		
		double[] baRankWeights = rankWeights.get(baPrefix);
		if (baRankWeights == null) {
			baRankWeights = new double[numProcesses];
			rankWeights.put(baPrefix, baRankWeights);
			List<List<LogicTreeBranch<?>>> branches = new ArrayList<>(numProcesses);
			for (int i=0; i<numProcesses; i++)
				branches.add(new ArrayList<>());
			rankBranches.put(baPrefix, branches);
		}
		
		baRankWeights[processIndex] += tree.getBranchWeight(branch);
		rankBranches.get(baPrefix).get(processIndex).add(getBranchForBA(branch));

		processWatch.stop();
		
		debug("AsyncLogicTree: finished "+index+"; load time: "+timeStr(loadWatch)
				+"; SLT blocking time: "+timeStr(sltWatch)+"; ba blocking time: "+timeStr(baWatch));
	}
	
	private static DecimalFormat pDF = new DecimalFormat("0.0%");
	
	private String timeStr(Stopwatch watch) {
		double totSecs = processWatch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		String pStr = " ("+pDF.format(secs/totSecs)+")";
		String timeStr;
		if (secs < 90d)
			return (float)secs+" s"+pStr;
		double mins = secs/60d;
		if (mins < 90d)
			return (float) mins + " m"+pStr;
		double hours = mins/60d;
		return (float) hours + " hr"+pStr;
	}
	
	public Map<String, double[]> getRankWeights() {
		return rankWeights;
	}
	
	public Map<String, List<List<LogicTreeBranch<?>>>> getRankBranches() {
		return rankBranches;
	}
	
	/**
	 * This returns the branch that should be given to the BA creator. If it contains TectonicRegionBranchTreeNodes,
	 * they will be unfolded to show their underlying values
	 * @param branch
	 * @return
	 */
	public static LogicTreeBranch<?> getBranchForBA(LogicTreeBranch<?> branch) {
		if (TectonicRegionBranchTreeNode.isTRTBranch(branch))
			return TectonicRegionBranchTreeNode.unfoldTRTBranches(branch);
		return branch;
	}
	
	public static boolean processBA(Map<String, BranchAverageSolutionCreator> baCreators, FaultSystemSolution sol,
			LogicTreeBranch<?> branch, BranchWeightProvider weightProv) {
		return processBA(baCreators, sol, branch, weightProv, null);
	}
	
	public static boolean processBA(Map<String, BranchAverageSolutionCreator> baCreators, FaultSystemSolution sol,
			LogicTreeBranch<?> branch, BranchWeightProvider weightProv, String baSuffix) {
		String baPrefix = getBA_prefix(branch);
		
		if (baPrefix == null) {
			return false;
		} else {
			if (baSuffix != null)
				baPrefix += baSuffix;
			if (!baCreators.containsKey(baPrefix))
				baCreators.put(baPrefix, new BranchAverageSolutionCreator(weightProv));
			BranchAverageSolutionCreator baCreator = baCreators.get(baPrefix);
			try {
				baCreator.addSolution(sol, getBranchForBA(branch), weightProv.getWeight(branch));
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				System.err.flush();
				return false;
			}
		}
	}
	
	public static String getBA_prefix(LogicTreeBranch<?> branch) {
		String baPrefix = null;
		boolean allAffect = true;
		for (int i=0; i<branch.size(); i++) {
			if (branch.getLevel(i).affects(FaultSystemRupSet.RUP_SECTS_FILE_NAME, true)) {
				if (baPrefix == null)
					baPrefix = "";
				else
					baPrefix += "_";
				baPrefix += branch.getValue(i).getFilePrefix();
			} else {
				allAffect = false;
			}
		}
		if (allAffect)
			return null;
		else if (baPrefix == null)
			return "";
		return baPrefix;
	}
	
	public static Map<String, List<LogicTreeBranch<?>>> getBranchAveragePrefixes(LogicTree<?> tree) {
		HashMap<String, List<LogicTreeBranch<?>>> commonPrefixes = new HashMap<>();
		
		for (LogicTreeBranch<?> branch : tree) {
			String prefix = getBA_prefix(branch);
			if (prefix != null) {
				List<LogicTreeBranch<?>> branches = commonPrefixes.get(prefix);
				if (branches == null) {
					branches = new ArrayList<>();
					commonPrefixes.put(prefix, branches);
				}
				commonPrefixes.get(prefix).add(branch);
			}
		}
		
		return commonPrefixes;
	}
	
	public static Map<String, File> getBranchAverageSolutionFileMap(File resultsDir, LogicTree<?> tree) {
		Set<String> commonPrefixes = getBranchAveragePrefixes(tree).keySet();
		
		Map<String, File> ret = new HashMap<>();
		for (String prefix : commonPrefixes)
			ret.put(prefix, baFile(resultsDir, prefix));
		
		return ret;
	}
	
	public static List<File> getBranchAverageSolutionFiles(File resultsDir, LogicTree<?> tree) {
		Set<String> commonPrefixes = getBranchAveragePrefixes(tree).keySet();
		
		List<File> ret = new ArrayList<>();
		for (String prefix : commonPrefixes)
			ret.add(baFile(resultsDir, prefix));
		
		return ret;
	}
	
	public static File baFile(File resultsDir, String baPrefix) {
		String prefix = resultsDir.getName();
		if (!baPrefix.isBlank())
			prefix += "_"+baPrefix;
		
		return new File(resultsDir.getParentFile(), prefix+"_branch_averaged.zip");
	}

	@Override
	public void shutdown() {
		super.shutdown();
		
		if (writingLoadedFuture != null)
			// wait on the last asynchonous write task to finish
			writingLoadedFuture.join();
		if (processingLoadedFuture != null)
			// wait on the last asynchronous processing task to finish
			processingLoadedFuture.join();
		
		memoryDebug("AsyncLogicTree: finalizing logic tree zip");
		try {
			sltBuilder.sortLogicTreeBranchesToMatchTree(tree);
			sltBuilder.close();
		} catch (IOException e) {
			memoryDebug("AsyncLogicTree: failed to build logic tree zip");
			e.printStackTrace();
		}
		
		if (baCreators != null && !baCreators.isEmpty()) {
			for (String baPrefix : baCreators.keySet()) {
				File baFile = getBAOutputFile(outputDir, baPrefix);
				memoryDebug("AsyncLogicTree: building "+baFile.getAbsolutePath());
				try {
					FaultSystemSolution baSol = baCreators.get(baPrefix).build();
					baSol.write(baFile);
				} catch (Exception e) {
					memoryDebug("AsyncLogicTree: failed to build BA for "+baFile.getAbsolutePath());
					e.printStackTrace();
					continue;
				}
			}
		}
	}
	
	private void memoryDebug(String info) {
		if (info == null || info.isBlank())
			info = "";
		else
			info += "; ";
	    
	    System.gc();
		Runtime rt = Runtime.getRuntime();
		long totalMB = rt.totalMemory() / 1024 / 1024;
		long freeMB = rt.freeMemory() / 1024 / 1024;
		long usedMB = totalMB - freeMB;
		debug(info+"mem t/u/f: "+totalMB+"/"+usedMB+"/"+freeMB);
	}

}
