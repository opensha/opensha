package org.opensha.sha.earthquake.faultSysSolution.inversion.mpj;

import java.awt.Color;
import java.awt.geom.Point2D;
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
import org.jfree.data.Range;
import org.opensha.commons.data.function.DiscretizedFunc;
import org.opensha.commons.data.function.EvenlyDiscretizedFunc;
import org.opensha.commons.gui.plot.HeadlessGraphPanel;
import org.opensha.commons.gui.plot.PlotCurveCharacterstics;
import org.opensha.commons.gui.plot.PlotLineType;
import org.opensha.commons.gui.plot.PlotSpec;
import org.opensha.commons.gui.plot.PlotUtils;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.mapping.gmt.elements.GMT_CPT_Files;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.MarkdownUtils;
import org.opensha.commons.util.MarkdownUtils.TableBuilder;
import org.opensha.commons.util.cpt.CPT;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchAverageableModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen;
import org.opensha.sha.earthquake.faultSysSolution.reports.RupSetMetadata;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen.PlotLevel;
import org.opensha.sha.earthquake.faultSysSolution.util.BranchAverageSolutionCreator;
import org.opensha.sha.earthquake.faultSysSolution.util.FaultSysTools;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.targetMFDs.SupraSeisBValInversionTargetMFDs;
import org.opensha.sha.magdist.IncrementalMagFreqDist;

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
	
	private boolean replot;
	private boolean rebuild;

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

		rebuild = cmd.hasOption("rebuild");
		replot = cmd.hasOption("replot");
		
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
	
	public static String levelPrefix(LogicTreeLevel<?> level) {
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
			
			File baFile = new File(outputDir, prefix+".zip");
			
			FaultSystemSolution baSol;
			if (rebuild || !baFile.exists()) {
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
					// pre-load all averageable modules
					sol.getModulesAssignableTo(BranchAverageableModule.class, true);
					sol.getRupSet().getModulesAssignableTo(BranchAverageableModule.class, true);
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
				
				baSol = baCreator.build();
				baSol.write(baFile);
			} else {
				baSol = FaultSystemSolution.load(baFile);
			}
			
			if (plotLevel != null) {
				// build a report
				File reportDir = new File(outputDir, prefix+"_report");
				debug("Writing report to "+reportDir.getAbsolutePath());
				RupSetMetadata compMeta = null;
				if (compSol != null)
					compMeta = new RupSetMetadata(compName == null ? "Full Tree" : compName, compSol);
				ReportMetadata meta = new ReportMetadata(new RupSetMetadata(getName(fixedNodes), baSol), compMeta);
				ReportPageGen pageGen = new ReportPageGen(meta, reportDir, ReportPageGen.getDefaultSolutionPlots(plotLevel));
				pageGen.setReplot(replot);
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
			debug("Building landing page and MFD plots");
			// build MFD plots
			List<String> lines = new ArrayList<>();
			lines.add("# Individual Logic Tree Choice Branch Averages");
			lines.add("");
			lines.add("This page gives links and summary information for subsets of the logic tree with individual "
					+ "choices fixed. The links and plots below are for branch-averaged subsets of the model holding "
					+ "the listed branch choice fixed, but averaged across all other choices in the model (according "
					+ "to their weights).");
			lines.add("");
			int tocIndex = lines.size();
			String topLink = "_[(top)](#table-of-contents)_";
			
			List<List<LogicTreeNode>> levelNodesUsed = levelNodesUsed(tree);
			
			IncrementalMagFreqDist compMFD = null;
			EvenlyDiscretizedFunc compCmlMFD = null;
			if (compSol != null) {
				EvenlyDiscretizedFunc refFunc = SupraSeisBValInversionTargetMFDs.buildRefXValues(compSol.getRupSet());
				compMFD = compSol.calcTotalNucleationMFD(refFunc.getMinX(), refFunc.getMaxX(), refFunc.getDelta());
				compMFD.setName(compName == null ? "Full Tree" : compName);
				compCmlMFD = compMFD.getCumRateDistWithOffset();
				compCmlMFD.setName(compMFD.getName());
			}
			
			double totalWeight = 0d;
			for (LogicTreeBranch<?> branch : tree)
				totalWeight += tree.getBranchWeight(branch);
			
			DecimalFormat weightDF = new DecimalFormat("0.##%");
			
			for (int l=0; l<levelNodesUsed.size(); l++) {
				List<LogicTreeNode> levelNodes = levelNodesUsed.get(l);
				
				if (levelNodes.size() < 2)
					continue;
				
				LogicTreeLevel<?> level = tree.getLevels().get(l);
				
				debug("Building page for "+level.getName());
				
				// build total MFD plot
				List<IncrementalMagFreqDist> incrMFDs = new ArrayList<>();
				List<EvenlyDiscretizedFunc> cmlMFDs = new ArrayList<>();
				List<PlotCurveCharacterstics> chars = new ArrayList<>();
				
				TableBuilder linkTable = MarkdownUtils.tableBuilder();
				if (plotLevel != null)
					linkTable.addLine("Choice", "Branch Count", "Weight", "Report", "Download Solution");
				else
					linkTable.addLine("Choice", "Branch Count", "Weight", "Download Solution");
				
				CPT cpt = GMT_CPT_Files.RAINBOW_UNIFORM.instance().rescale(0d, levelNodes.size()-1d);
				
				for (int i=0; i<levelNodes.size(); i++) {
					LogicTreeNode node = levelNodes.get(i);
					LogicTreeNode[] array = {node};
					String prefix = getPrefix(array);
					File solFile = new File(outputDir, prefix+".zip");
					Preconditions.checkState(solFile.exists(),
							"Node solution doesn't exist: %s", solFile.getAbsolutePath());
					
					int numBranches = 0;
					double myWeight = 0d;
					
					for (LogicTreeBranch<?> branch : tree) {
						if (branch.hasValue(node)) {
							numBranches++;
							myWeight += tree.getBranchWeight(branch);
						}
					}
					
					debug(node.getShortName()+" has "+numBranches+" branches, "+weightDF.format(myWeight/totalWeight)+" weight");
					
					linkTable.initNewLine();
					linkTable.addColumn(node.getName());
					linkTable.addColumn(numBranches);
					linkTable.addColumn(weightDF.format(myWeight/totalWeight));
					if (plotLevel != null)
						linkTable.addColumn("[View Report]("+prefix+"_report/)");
					linkTable.addColumn("["+solFile.getName()+"]("+solFile.getName()+")");
					linkTable.finalizeLine();
					
					FaultSystemSolution sol = FaultSystemSolution.load(solFile);
					EvenlyDiscretizedFunc refMFD = SupraSeisBValInversionTargetMFDs.buildRefXValues(sol.getRupSet());
					IncrementalMagFreqDist mfd = sol.calcTotalNucleationMFD(refMFD.getMinX(), refMFD.getMaxX(), refMFD.getDelta());
					mfd.setName(node.getShortName());
					incrMFDs.add(mfd);
					EvenlyDiscretizedFunc cmlMFD = mfd.getCumRateDistWithOffset();
					cmlMFD.setName(node.getShortName());
					cmlMFDs.add(cmlMFD);
					Color color = cpt.getColor((float)i);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, color));
				}
				
				double minX = Double.POSITIVE_INFINITY;
				double maxX = Double.NEGATIVE_INFINITY;
				for (IncrementalMagFreqDist mfd : incrMFDs) {
					for (Point2D pt : mfd) {
						if (pt.getY() > 0d) {
							minX = Math.min(minX, pt.getX());
							maxX = Math.max(maxX, pt.getX());
						}
					}
				}
				
				if (compSol != null) {
					incrMFDs.add(compMFD);
					cmlMFDs.add(compCmlMFD);
					chars.add(new PlotCurveCharacterstics(PlotLineType.SOLID, 3f, Color.BLACK));
				}
				
				minX = Math.floor(minX*5d)/5d;
				maxX = Math.ceil(maxX*5d)/5d;
				
				Range xRange = new Range(minX, maxX);
				Range incrYRange = yRange(incrMFDs);
				Range cmlYRange = yRange(cmlMFDs);
				
				PlotSpec incrSpec = new PlotSpec(incrMFDs, chars, level.getName(), "Magnitude", "Incremental Rate (/yr)");
				incrSpec.setLegendInset(true);
				PlotSpec cmlSpec = new PlotSpec(cmlMFDs, chars, level.getName(), "Magnitude", "Cumulative Rate (/yr)");
				cmlSpec.setLegendInset(true);
				
				TableBuilder plotTable = MarkdownUtils.tableBuilder();
				plotTable.addLine("Incremental", "Cumulative");
				
				String plotPrefix = levelPrefix(level)+"_mfds";
				
				HeadlessGraphPanel gp = PlotUtils.initHeadless();

				plotTable.initNewLine();
				
				gp.drawGraphPanel(incrSpec, false, true, xRange, incrYRange);
				PlotUtils.writePlots(outputDir, plotPrefix, gp, 900, 800, true, true, true);
				plotTable.addColumn("![Incremental]("+plotPrefix+".png)");
				
				gp.drawGraphPanel(cmlSpec, false, true, xRange, cmlYRange);
				plotPrefix += "_cml";
				PlotUtils.writePlots(outputDir, plotPrefix, gp, 900, 800, true, true, true);
				plotTable.addColumn("![Cumulative]("+plotPrefix+".png)");
				
				plotTable.finalizeLine();

				lines.add("");
				lines.add("## "+level.getName());
				lines.add(topLink); lines.add("");
				
				lines.addAll(linkTable.build());
				lines.add("");
				
				lines.add("### "+level.getName()+", MFD plots");
				lines.add(topLink); lines.add("");
				
				lines.addAll(plotTable.build());
			}
			
			// add TOC
			lines.addAll(tocIndex, MarkdownUtils.buildTOC(lines, 2, 2));
			lines.add(tocIndex, "## Table Of Contents");
			
			// write markdown
			MarkdownUtils.writeReadmeAndHTML(lines, outputDir);
		}
	}
	
	private static Range yRange(List<? extends DiscretizedFunc> mfds) {
		double minY = Double.POSITIVE_INFINITY;
		double maxY = 0;
		for (DiscretizedFunc mfd : mfds) {
			for (Point2D pt : mfd) {
				if (pt.getY() > 0d) {
					minY = Math.min(minY, pt.getY());
					maxY = Math.max(maxY, pt.getY());
				}
			}
		}
		minY = Math.pow(10, Math.floor(Math.log10(minY)));
		maxY = Math.pow(10, Math.ceil(Math.log10(maxY)));
		// ok to truncate the lower side here
		minY = Math.max(minY, 1e-6);
		return new Range(minY, maxY);
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
		ops.addOption("rp", "replot", false,
				"If supplied, existing plots will be re-generated when re-running a report");
		ops.addOption("rb", "rebuild", false,
				"If supplied, existing branch averages will be rebuilt, otherwise they will be skipped");
		
		return ops;
	}
	
	public static List<LogicTreeNode[]> buildCombinations(LogicTree<?> tree, int depth) {
		List<List<LogicTreeNode>> levelNodesUsed = levelNodesUsed(tree);
		
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
	
	private static List<List<LogicTreeNode>> levelNodesUsed(LogicTree<?> tree) {
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
		return levelNodesUsed;
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
