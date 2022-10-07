package org.opensha.sha.earthquake.faultSysSolution.inversion.mpj;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen.PlotLevel;
import org.opensha.sha.earthquake.faultSysSolution.util.BranchAverageSolutionCreator;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.primitives.Ints;

import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;

/**
 * Class for running a full logic tree of inversions with a single job in an MPI environment
 * 
 * @author kevin
 *
 */
public class MPJ_LogicTreeBranchAverageBuilder extends MPJTaskCalculator {

	private SolutionLogicTree slt;
	private File outputDir;

	private LogicTree<LogicTreeNode> tree;
	
	private int depth;
	
	private PlotLevel plotLevel;
	private boolean skipSectBySect;
	private FaultSystemSolution compSol;
	private String compName;
	
	private Map<LogicTreeNode, LogicTreeLevel<?>> levelsMap;
	private List<LogicTreeNode[]> combinations;

	public MPJ_LogicTreeBranchAverageBuilder(CommandLine cmd) throws IOException {
		super(cmd);
		
		this.shuffle = false;
		
		tree = LogicTree.read(new File(cmd.getOptionValue("logic-tree")));
		if (rank == 0)
			debug("Loaded "+tree.size()+" tree nodes");

		File inputDir = new File(cmd.getOptionValue("input-dir"));
		Preconditions.checkState(inputDir.exists());
		slt = new SolutionLogicTree.ResultsDirReader(inputDir, tree);
		
		outputDir = new File(cmd.getOptionValue("output-dir"));
		
		if (cmd.hasOption("depth")) {
			depth = Integer.parseInt(cmd.getOptionValue("depth"));
			Preconditions.checkState(depth > 0);
		} else {
			depth = 1;
		}
		
		combinations = buildCombinations(tree, depth);
		
		if (rank == 0)
			debug(combinations.size()+" combinations for depth="+depth);
		
		if (cmd.hasOption("plot-level")) {
			plotLevel = PlotLevel.valueOf(cmd.getOptionValue("plot-level"));
			skipSectBySect = cmd.hasOption("skip-sect-by-sect");
			if (cmd.hasOption("compare-to")) {
				compSol = FaultSystemSolution.load(new File(cmd.getOptionValue("compare-to")));
				if (cmd.hasOption("comp-name"))
					compName = cmd.getOptionValue("comp-name");
			}
		}
		
		levelsMap = new HashMap<>();
		for (LogicTreeLevel<?> level : tree.getLevels()) {
			for (LogicTreeNode node : level.getNodes()) {
				Preconditions.checkState(!levelsMap.containsKey(node));
				levelsMap.put(node, level);
			}
		}
		
		if (rank == 0)
			waitOnDir(outputDir, 5, 1000);
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
		return combinations.size();
	}
	
	private String levelPrefix(LogicTreeLevel<?> level) {
		String ret = level.getShortName().replaceAll("\\W+", "_");
		while (ret.contains("__"))
			ret = ret.replace("__", "_");
		if (ret.startsWith("_"))
			ret = ret.substring(1);
		if (ret.endsWith("_"))
			ret = ret.substring(0, ret.length()-1);
		return ret;
	}
	
	private String getPrefix(LogicTreeNode[] fixedNodes) {
		String str = null;
		for (LogicTreeNode node : fixedNodes) {
			if (str == null)
				str = "";
			else
				str += "_";
			LogicTreeLevel<?> level = levelsMap.get(node);
			str += levelPrefix(level)+"_"+node.getFilePrefix();
		}
		return str;
	}
	
	private String getName(LogicTreeNode[] fixedNodes) {
		String str = null;
		for (LogicTreeNode node : fixedNodes) {
			if (str == null)
				str = "";
			else
				str += ", ";
			LogicTreeLevel<?> level = levelsMap.get(node);
			str += level.getShortName()+": "+node.getName();
		}
		return str;
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		for (int index : batch) {
			LogicTreeNode[] fixedNodes = combinations.get(index);
			String prefix = getPrefix(fixedNodes);
			
			LogicTree<?> subTree = tree.matchingAll(fixedNodes);
			
			debug("Building BA for "+index+": "+prefix+" ("+subTree.size()+" branches)");
			Preconditions.checkState(subTree.size() > 1, "Need at least 2 branches, have %s for %s", subTree.size(), prefix);
			
			BranchAverageSolutionCreator baCreator = new BranchAverageSolutionCreator(subTree.getWeightProvider());
			int count = 0;
			CompletableFuture<Void> processingLoadedFuture = null;
			Stopwatch loadWatch = Stopwatch.createUnstarted();
			Stopwatch processWatch = Stopwatch.createUnstarted();
			Stopwatch totalWatch = Stopwatch.createStarted();
			for (LogicTreeBranch<?> branch : subTree) {
				int myCount = count;
				count++;
				debug("Loading branch "+(myCount)+"/"+subTree.size()+" for "+prefix+": "+branch);
				loadWatch.start();
				FaultSystemSolution sol = slt.forBranch(branch);
				loadWatch.stop();
				if (processingLoadedFuture != null)
					// wait until we're done processing the previous one
					processingLoadedFuture.get();
				// process this asychronously so that we can start loading the next one
				processingLoadedFuture = CompletableFuture.runAsync(new Runnable() {
					
					@Override
					public void run() {
						processWatch.start();
						baCreator.addSolution(sol, branch);
						processWatch.stop();
						int done = myCount+1;
						debug("Done processing branch "+myCount+"/"+subTree.size()+";\t"+MPJ_LogicTreeInversionRunner.memoryString()
							+";\tEACH: load="+elapsed(loadWatch, done)+", process="+elapsed(processWatch, done)+", tot="+elapsed(totalWatch, done)
							+";\tTOTAL: load="+elapsed(loadWatch)+", process="+elapsed(processWatch)+", tot="+elapsed(totalWatch));
					}
				});
			}
			if (processingLoadedFuture != null)
				// wait until we're done processing the last one
				processingLoadedFuture.get();
			
			totalWatch.stop();
			
			FaultSystemSolution baSol = baCreator.build();
			
			baSol.write(new File(outputDir, prefix+".zip"));
			
			if (plotLevel != null) {
				// build a report
				File reportDir = new File(outputDir, prefix+"_report");
				debug("Writing report to "+reportDir.getAbsolutePath());
				RupSetMetadata compMeta = null;
				if (compSol != null)
					compMeta = new RupSetMetadata(compName == null ? "Full Tree" : compName, compSol);
				ReportMetadata meta = new ReportMetadata(new RupSetMetadata(getName(fixedNodes), baSol), compMeta);
				ReportPageGen pageGen = new ReportPageGen(meta, reportDir, ReportPageGen.getDefaultRupSetPlots(plotLevel));
				pageGen.setReplot(true);
				pageGen.setNumThreads(getNumThreads());
				if (skipSectBySect)
					pageGen.skipSectBySect();
				pageGen.generatePage();
			}
		}
	}
	
	private static final DecimalFormat oDF = new DecimalFormat("0.#");
	
	private String elapsed(Stopwatch watch) {
		return elapsed(watch, 0);
	}
	
	private String elapsed(Stopwatch watch, int per) {
		double secs = watch.elapsed(TimeUnit.MILLISECONDS)/1000d;
		if (per > 0)
			secs /= per;
		if (secs < 60d)
			return oDF.format(secs)+"s";
		double mins = secs / 60d;
		if (mins < 60d)
			return oDF.format(mins)+"m";
		double hours = mins / 60d;
		return oDF.format(hours)+"h";
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		if (rank == 0 && depth == 1) {
			// build MFD plots
			
		}
	}
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		ops.addRequiredOption("lt", "logic-tree", true, "Path to logic tree JSON file");
		ops.addRequiredOption("id", "input-dir", true, "Path to input (results) directory");
		ops.addRequiredOption("od", "output-dir", true, "Path to output directory");
		ops.addOption("pl", "plot-level", true, "This enables reports and sets the plot level, one of: "
				+FaultSysTools.enumOptions(PlotLevel.class)+". Default is no report.");
		ops.addOption("cs", "compare-to", true, "Comparison solution file to use in reports");
		ops.addOption("cn", "comp-name", true, "Name of the comparison rupture set. Default: Full Tree");
		ops.addOption("ssbs", "skip-sect-by-sect", false,
				"Flag to skip section-by-section plots, regardless of selected plot level");
		ops.addOption("dp", "depth", true, "Depth of level combinations to include (default is 1, more than 2 is not recommended).");
		
		return ops;
	}
	
	public static List<LogicTreeNode[]> buildCombinations(LogicTree<?> tree, int depth) {
		List<List<LogicTreeNode>> levelNodesUsed = new ArrayList<>();
		for (LogicTreeLevel<?> level : tree.getLevels()) {
			List<LogicTreeNode> myNodes = new ArrayList<>();
			levelNodesUsed.add(myNodes);
			for (LogicTreeNode node : level.getNodes()) {
				for (LogicTreeBranch<?> branch : tree) {
					if (branch.hasValue(node)) {
						myNodes.add(node);
						break;
					}
				}
			}
		}
		
		List<int[]> levelCombinations = new ArrayList<>();
		fillInLevelCombinationsRecursive(tree, levelNodesUsed, levelCombinations, 0, new int[0], depth);
		if (depth > 1)
			System.out.println("Found "+levelCombinations.size()+" level combinations");
		List<LogicTreeNode[]> combinations = new ArrayList<>();
		for (int[] fixedLevels : levelCombinations)
			addCombinationsRecursive(tree, levelNodesUsed, fixedLevels, 0, new LogicTreeNode[0], combinations);
		if (depth > 1)
			System.out.println("Found "+combinations.size()+" node combinations");
		return combinations;
	}
	
	private static void fillInLevelCombinationsRecursive(LogicTree<?> tree, List<List<LogicTreeNode>> levelNodesUsed, List<int[]> ret, int curDepth, int[] curFixed, int totDepth) {
		Preconditions.checkState(curFixed.length == curDepth);
		if (curDepth == totDepth) {
			ret.add(curFixed);
		} else {
			int startLevel = curFixed.length == 0 ? 0 : Ints.max(curFixed)+1;
			for (int l=startLevel; l<levelNodesUsed.size(); l++) {
				if (levelNodesUsed.get(l).size() < 2)
					continue;
				int[] newFixed = Arrays.copyOf(curFixed, curFixed.length+1);
				newFixed[newFixed.length-1] = l;
				fillInLevelCombinationsRecursive(tree, levelNodesUsed, ret, curDepth+1, newFixed, totDepth);
			}
		}
	}
	
	private static void addCombinationsRecursive(LogicTree<?> tree, List<List<LogicTreeNode>> levelNodesUsed, int[] fixedLevels, int curIndex, LogicTreeNode[] fixedVals, List<LogicTreeNode[]> ret) {
		Preconditions.checkState(fixedVals.length == curIndex);
		if (curIndex == fixedLevels.length) {
			// done
			ret.add(fixedVals);
		} else {
			int levelIndex = fixedLevels[curIndex];
			for (LogicTreeNode node : levelNodesUsed.get(levelIndex)) {
				LogicTreeNode[] newFixed = Arrays.copyOf(fixedVals, fixedVals.length+1);
				newFixed[newFixed.length-1] = node;
				addCombinationsRecursive(tree, levelNodesUsed, fixedLevels, curIndex+1, newFixed, ret);
			}
		}
	}

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		try {
			args = MPJTaskCalculator.initMPJ(args);
			
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJ_LogicTreeBranchAverageBuilder.class);
			
			MPJ_LogicTreeBranchAverageBuilder driver = new MPJ_LogicTreeBranchAverageBuilder(cmd);
			driver.run();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
