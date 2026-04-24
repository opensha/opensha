package gov.usgs.earthquake.nshmp.erf.mpj;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.opensha.commons.data.CSVFile;
import org.opensha.commons.data.Site;
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.hpc.JavaShellScriptWriter;
import org.opensha.commons.hpc.pbs.BatchScriptWriter;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.sha.earthquake.faultSysSolution.hazard.FaultAndGriddedSeparateTreeHazardCombiner;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_LogicTreeHazardCalc;
import org.opensha.sha.earthquake.faultSysSolution.hazard.mpj.MPJ_SiteLogicTreeHazardCurveCalc;
import org.opensha.sha.earthquake.faultSysSolution.inversion.GridSourceProviderFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.InversionConfigurationFactory;
import org.opensha.sha.earthquake.faultSysSolution.inversion.mpj.AbstractAsyncLogicTreeWriter;
import org.opensha.sha.earthquake.faultSysSolution.inversion.mpj.MPJ_GridSeisBranchBuilder;
import org.opensha.sha.earthquake.faultSysSolution.inversion.mpj.MPJ_LogicTreeBranchAverageBuilder;
import org.opensha.sha.earthquake.faultSysSolution.inversion.mpj.MPJ_LogicTreeInversionRunner;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.reports.ReportPageGen.PlotLevel;
import org.opensha.sha.earthquake.faultSysSolution.util.TrueMeanSolutionCreator;
import org.opensha.sha.imr.AttenRelRef;

import com.google.common.base.Preconditions;

import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;

public class MPJ_LogicTreeInversionScriptWriter {

	public File writeScripts(InversionScriptWriteRequest request) throws IOException {
		LogicTreeConfig.Resolution resolved = request.logicTree().source().resolve();
		LogicTree<LogicTreeNode> logicTree = resolved.logicTree();
		LogicTree<LogicTreeNode> analysisTree = resolved.analysisTree();
		LogicTree<?> originalTree = resolved.originalTree();
		List<LogicTreeLevel<? extends LogicTreeNode>> levels = resolved.levels();
		HazardConfig hazard = request.hazard();

		validateAnalysisTree(logicTree, analysisTree);
		Preconditions.checkState(logicTree.size() > 0, "No logic tree branches");

		String dirName = request.run().buildDirectoryName();
		File localDir = new File(request.hpc().localMainDir(), dirName);
		Preconditions.checkState(localDir.exists() || localDir.mkdir(), "Couldn't create %s", localDir);

		String mainDirPath = "$MAIN_DIR";
		String dirPath = "$DIR";
		List<File> classpath = List.of(new File(dirPath+"/"+request.hpc().jarFileName()));
		JavaShellScriptWriter mpjWriter = request.hpc().buildMPJWriter(classpath);
		mpjWriter.setEnvVar("MAIN_DIR", request.hpc().remoteMainDir().getAbsolutePath());
		mpjWriter.setEnvVar("DIR", mainDirPath+"/"+dirName);
		BatchScriptWriter batchWriter = request.hpc().buildBatchWriter();
		JavaShellScriptWriter javaWriter = request.hpc().buildJavaWriter(classpath);
		request.hpc().copyEnvVars(mpjWriter, javaWriter);

		File localLogicTree = new File(localDir, "logic_tree.json");
		logicTree.write(localLogicTree);
		String logicTreePath = dirPath+"/"+localLogicTree.getName();

		String analysisTreePath = null;
		if (analysisTree != null) {
			File localAnalysisTree = new File(localDir, "logic_tree_analysis.json");
			analysisTree.write(localAnalysisTree);
			analysisTreePath = dirPath+"/"+localAnalysisTree.getName();
		}
		if (originalTree != null)
			originalTree.write(new File(localDir, "logic_tree_original.json"));

		InversionConfigurationFactory factory = instantiateFactory(request.inversion().factoryClass());
		NodeCalcConfig calcConfig = resolveNodeCalcConfig(request.hpc(), request.inversion(), logicTree.size());
		String resultsPath = dirPath+"/results";
		
		System.out.println("Directory name: "+dirName);
		System.out.println("Local output dir: "+localDir.getAbsolutePath());
		System.out.println("Factory: "+request.inversion().factoryClass().getName());
		System.out.println("Built "+logicTree.size()+" logic tree branches"
				+(analysisTree != null ? " ("+analysisTree.size()+" analysis branches)" : ""));
		System.out.println("Calculations: "+calcConfig.numCalcs()+" total, "
				+calcConfig.nodeRounds()+" rounds on "+calcConfig.nodes()+" nodes");
		System.out.println("Estimate "+calcConfig.perInversionMins()+" minutes per inversion");
		System.out.println("Inversion job time: "+calcConfig.inversionMins()+" mins = "
				+(float)((double)calcConfig.inversionMins()/60d)+" hours");
		if (hazard != null)
			System.out.println("Hazard job time: "+calcConfig.hazardMins()+" mins = "
					+(float)((double)calcConfig.hazardMins()/60d)+" hours");

		writeInversionJob(localDir, batchWriter, mpjWriter, request, logicTreePath, resultsPath, calcConfig);
		Map<String, File> baFiles = AbstractAsyncLogicTreeWriter.getBranchAverageSolutionFileMap(new File("results"), logicTree);

		if (hazard != null) {
			writeBaseHazardJob(localDir, batchWriter, mpjWriter, request, logicTree, levels, analysisTreePath,
					resultsPath, calcConfig);
		}

		GridSourcePostProcessConfig gridConfig = request.postProcess().gridSourcePostProcess();
		if (gridConfig != null) {
			Preconditions.checkState(factory instanceof GridSourceProviderFactory,
					"Grid-source post-processing requires a GridSourceProviderFactory");
			Preconditions.checkState(!(factory instanceof GridSourceProviderFactory.Single),
					"Grid-source post-processing does not apply to GridSourceProviderFactory.Single");
			writeGridSourceJobs(localDir, batchWriter, mpjWriter, javaWriter, request, factory, logicTree, analysisTreePath,
					logicTreePath, resultsPath, levels, baFiles, calcConfig, gridConfig);
		} else if (request.postProcess().writeTrueMean()) {
			writeTrueMeanJob(localDir, batchWriter, javaWriter, request.hpc().followUpQueue(), request.hpc().threadsPerNode(),
					request.hpc().memGBPerNode(), calcConfig.hazardMins, resultsPath, null);
		}

		if (hazard != null && !hazard.sites().isEmpty()) {
			writeSiteHazardJobs(localDir, batchWriter, mpjWriter, request, logicTreePath, analysisTreePath,
					resultsPath, calcConfig.nodes, gridConfig != null);
		}

		if (request.postProcess().writeNodeBranchAverages())
			writeNodeBranchAverageJobs(localDir, batchWriter, mpjWriter, request, logicTree, analysisTree,
					logicTreePath, analysisTreePath, resultsPath, baFiles, calcConfig.nodes, calcConfig.hazardMins);

		return localDir;
	}

	private void writeInversionJob(File localDir, BatchScriptWriter batchWriter, JavaShellScriptWriter mpjWriter,
			InversionScriptWriteRequest request, String logicTreePath, String resultsPath, NodeCalcConfig calcConfig)
			throws IOException {
		int annealingThreads = request.hpc().threadsPerNode()/request.hpc().inversionsPerBundle();
		StringBuilder args = new StringBuilder();
		appendArg(args, "--logic-tree", logicTreePath);
		appendArg(args, "--output-dir", resultsPath);
		appendArg(args, "--inversion-factory", quote(request.inversion().factoryClass().getName()));
		appendArg(args, "--annealing-threads", annealingThreads);
		appendArg(args, "--cache-dir", "$MAIN_DIR/cache");
		appendArg(args, "--runs-per-bundle", request.hpc().inversionsPerBundle());
		if (request.inversion().completionArg() != null)
			appendArg(args, "--completion", request.inversion().completionArg());
		if (request.inversion().runsPerBranch() > 1)
			appendArg(args, "--runs-per-branch", request.inversion().runsPerBranch());
		if (request.inversion().parallelBranchAverage())
			appendFlag(args, "--parallel-ba");
		for (String extraArg : request.inversion().extraArgs())
			args.append(" ").append(extraArg);
		args.append(" ").append(MPJTaskCalculator.argumentBuilder()
				.exactDispatch(request.hpc().inversionsPerBundle()).build());
		List<String> script = mpjWriter.buildScript(MPJ_LogicTreeInversionRunner.class.getName(), args.toString());
		batchWriter.writeScript(new File(localDir, "batch_inversion.slurm"), script, capWeek(calcConfig.inversionMins),
				calcConfig.nodes, request.hpc().threadsPerNode(), request.hpc().memGBPerNode(), request.hpc().queue());
	}

	private void writeBaseHazardJob(File localDir, BatchScriptWriter batchWriter, JavaShellScriptWriter mpjWriter,
			InversionScriptWriteRequest request, LogicTree<LogicTreeNode> logicTree,
			List<LogicTreeLevel<? extends LogicTreeNode>> levels, String analysisTreePath, String resultsPath,
			NodeCalcConfig calcConfig) throws IOException {
		HazardArgs hazardArgs = buildHazardArgs(localDir, request.hazard(), logicTree, levels, analysisTreePath,
				resultsPath);
		String args = hazardArgs.baseArgs+" "+MPJTaskCalculator.argumentBuilder().exactDispatch(1)
				.threads(request.hpc().threadsPerNode()).build();
		List<String> script = mpjWriter.buildScript(MPJ_LogicTreeHazardCalc.class.getName(), args);
		int hazardNodes = Integer.min(40, calcConfig.nodes);
		batchWriter.writeScript(new File(localDir, "batch_hazard.slurm"), script, calcConfig.hazardMins,
				hazardNodes, request.hpc().threadsPerNode(), request.hpc().memGBPerNode(), request.hpc().followUpQueue());
	}

	private void writeGridSourceJobs(File localDir, BatchScriptWriter batchWriter, JavaShellScriptWriter mpjWriter,
			JavaShellScriptWriter javaWriter, InversionScriptWriteRequest request, InversionConfigurationFactory factory,
			LogicTree<LogicTreeNode> logicTree, String analysisTreePath, String logicTreePath, String resultsPath,
			List<LogicTreeLevel<? extends LogicTreeNode>> levels, Map<String, File> baFiles,
			NodeCalcConfig calcConfig, GridSourcePostProcessConfig gridConfig) throws IOException {
		HazardConfig hazard = request.hazard();
		LogicTree<?> gridTree = ((GridSourceProviderFactory)factory).getGridSourceTree(logicTree);
		boolean allLevelsAffected = true;
		for (LogicTreeLevel<?> level : levels)
			if (!GridSourceProvider.affectedByLevel(level))
				allLevelsAffected = false;

		int numCalcs = gridTree.size()*logicTree.size();
		int nodeRounds = (int)Math.ceil((double)numCalcs/(double)calcConfig.nodes);
		int mins = capWeek(Integer.max(60*10, 45*nodeRounds));
		System.out.println("Grid source tree has "+gridTree.size()+" branches; fault x grid calculations = "
				+numCalcs+"; estimated grid/follow-up time = "+mins+" mins = "
				+(float)((double)mins/60d)+" hours");

		String fullLTPath = "$DIR/logic_tree_full_gridded.json";
		String randLTPath = "$DIR/logic_tree_full_gridded_sampled.json";
		String onlyLTPath = allLevelsAffected ? fullLTPath : "$DIR/logic_tree_full_gridded_for_only_calc.json";

		StringBuilder args = new StringBuilder();
		appendArg(args, "--factory", quote(request.inversion().factoryClass().getName()));
		appendArg(args, "--logic-tree", logicTreePath);
		appendArg(args, "--sol-dir", resultsPath);
		appendArg(args, "--write-full-tree", fullLTPath);
		appendArg(args, "--write-rand-tree", randLTPath);
		appendArg(args, "--num-samples-per-sol", gridConfig.samplesPerSolution());
		appendArg(args, "--slt-min-mag", gridConfig.solutionMinMag());
		if (!allLevelsAffected)
			appendArg(args, "--write-only-tree", onlyLTPath);
		if (gridConfig.averageOnly())
			appendFlag(args, "--average-only");
		int gridThreads = Integer.max(1, request.hpc().threadsPerNode()/2);
		args.append(" ").append(MPJTaskCalculator.argumentBuilder().exactDispatch(1).threads(gridThreads).build());
		List<String> script = mpjWriter.buildScript(MPJ_GridSeisBranchBuilder.class.getName(), args.toString());
		batchWriter.writeScript(new File(localDir, "batch_grid_calc.slurm"), script, mins, calcConfig.nodes,
				request.hpc().threadsPerNode(), request.hpc().memGBPerNode(), request.hpc().followUpQueue());

		String griddedBAName = null;
		if (baFiles != null && baFiles.size() == 1)
			griddedBAName = baFiles.values().iterator().next().getName().replace(".zip", "")+"_gridded.zip";

		if (request.postProcess().writeTrueMean())
			writeTrueMeanJob(localDir, batchWriter, javaWriter, request.hpc().followUpQueue(), request.hpc().threadsPerNode(),
					request.hpc().memGBPerNode(), mins, resultsPath, griddedBAName == null ? null : "$DIR/"+griddedBAName);

		if (hazard != null && gridConfig.writeHazardProducts()) {
			HazardArgs hazardArgs = buildHazardArgs(localDir, hazard, logicTree, levels, analysisTreePath,
					resultsPath);
			writeGridHazardJobs(localDir, batchWriter, mpjWriter, request, hazardArgs, calcConfig.nodes, mins,
					fullLTPath, randLTPath, onlyLTPath, resultsPath, griddedBAName, logicTree.size());
			String combineArgs = resultsPath+".zip "+resultsPath+" "+resultsPath+"_gridded_branches.zip "
					+resultsPath+"_gridded_only "+resultsPath+"_comb_branches.zip "
					+resultsPath+"_comb_hazard.zip "+resultsPath+"_hazard.zip";
			script = javaWriter.buildScript(FaultAndGriddedSeparateTreeHazardCombiner.class.getName(), combineArgs);
			batchWriter.writeScript(new File(localDir, "fault_grid_hazard_combine.slurm"), script, mins, 1,
					request.hpc().threadsPerNode(), request.hpc().memGBPerNode(), request.hpc().followUpQueue());
		}
	}

	private void writeTrueMeanJob(File localDir, BatchScriptWriter batchWriter, JavaShellScriptWriter javaWriter,
			String queue, int threadsPerNode, int memGBPerNode, int mins, String resultsPath, String griddedBAPath)
			throws IOException {
		String args = resultsPath+".zip true_mean_solution.zip";
		if (griddedBAPath != null)
			args += " "+griddedBAPath;
		List<String> script = javaWriter.buildScript(TrueMeanSolutionCreator.class.getName(), args);
		batchWriter.writeScript(new File(localDir, "true_mean_builder.slurm"), script, mins, 1, threadsPerNode,
				memGBPerNode, queue);
	}

	private void writeSiteHazardJobs(File localDir, BatchScriptWriter batchWriter, JavaShellScriptWriter mpjWriter,
			InversionScriptWriteRequest request, String logicTreePath, String analysisTreePath, String resultsPath,
			int nodes, boolean hasGridSourcePostProcess) throws IOException {
		File sitesFile = new File(localDir, "hazard_sites.csv");
		writeSitesFile(sitesFile, request.hazard().sites());

		StringBuilder args = new StringBuilder();
		appendArg(args, "--input-file", resultsPath+".zip");
		if (analysisTreePath != null)
			appendArg(args, "--analysis-logic-tree", analysisTreePath);
		appendArg(args, "--output-dir", resultsPath+"_hazard_sites");
		appendArg(args, "--sites-file", "$DIR/"+sitesFile.getName());
		appendArg(args, "--gridded-seis", request.hazard().backgroundOption().name());
		appendHazardSharedArgs(args, request.hazard());
		args.append(" ").append(MPJTaskCalculator.argumentBuilder().exactDispatch(1)
				.threads(request.hpc().threadsPerNode()).build());
		List<String> script = mpjWriter.buildScript(MPJ_SiteLogicTreeHazardCurveCalc.class.getName(), args.toString());
		batchWriter.writeScript(new File(localDir, "batch_hazard_sites.slurm"), script, capWeek(Integer.max(60*10, 45)),
				Integer.min(40, nodes), request.hpc().threadsPerNode(), request.hpc().memGBPerNode(), request.hpc().followUpQueue());

		if (hasGridSourcePostProcess && request.hazard().backgroundOption() == org.opensha.sha.earthquake.param.IncludeBackgroundOption.EXCLUDE) {
			StringBuilder fullArgs = new StringBuilder();
			appendArg(fullArgs, "--input-file", resultsPath);
			if (analysisTreePath != null)
				appendArg(fullArgs, "--analysis-logic-tree", analysisTreePath);
			appendArg(fullArgs, "--logic-tree", "$DIR/logic_tree_full_gridded.json");
			appendArg(fullArgs, "--output-dir", resultsPath+"_hazard_sites_full_gridded");
			appendArg(fullArgs, "--sites-file", "$DIR/"+sitesFile.getName());
			appendArg(fullArgs, "--gridded-seis", "INCLUDE");
			appendHazardSharedArgs(fullArgs, request.hazard());
			fullArgs.append(" ").append(MPJTaskCalculator.argumentBuilder().minDispatch(2).maxDispatch(10)
					.threads(request.hpc().threadsPerNode()).build());
			script = mpjWriter.buildScript(MPJ_SiteLogicTreeHazardCurveCalc.class.getName(), fullArgs.toString());
			batchWriter.writeScript(new File(localDir, "batch_hazard_sites_full_gridded.slurm"), script,
					capWeek(Integer.max(60*10, 45)), Integer.min(40, nodes), request.hpc().threadsPerNode(), request.hpc().memGBPerNode(),
					request.hpc().followUpQueue());
		}
	}

	private void writeNodeBranchAverageJobs(File localDir, BatchScriptWriter batchWriter, JavaShellScriptWriter mpjWriter,
			InversionScriptWriteRequest request, LogicTree<LogicTreeNode> logicTree, LogicTree<LogicTreeNode> analysisTree,
			String logicTreePath, String analysisTreePath, String resultsPath, Map<String, File> baFiles, int nodes,
			int mins) throws IOException {
		LogicTree<?> baTree = analysisTree == null ? logicTree : analysisTree;
		Map<String, List<LogicTreeBranch<?>>> baPrefixes = AbstractAsyncLogicTreeWriter.getBranchAveragePrefixes(baTree);
		List<String> baLogicTreePaths = new ArrayList<>();
		List<String> baOutputDirs = new ArrayList<>();
		List<String> baSuffixes = new ArrayList<>();

		if (baPrefixes.size() > 1) {
			List<LogicTreeLevel<? extends LogicTreeNode>> baLevels = new ArrayList<>();
			baLevels.addAll(baTree.getLevels());
			for (Map.Entry<String, List<LogicTreeBranch<?>>> entry : baPrefixes.entrySet()) {
				String prefix = entry.getKey();
				List<LogicTreeBranch<LogicTreeNode>> branches = new ArrayList<>();
				for (LogicTreeBranch<?> branch : entry.getValue()) {
					LogicTreeBranch<LogicTreeNode> plain = new LogicTreeBranch<>(baLevels);
					for (int i=0; i<branch.size(); i++)
						plain.setValue(i, branch.getValue(i));
					plain.setOrigBranchWeight(branch.getOrigBranchWeight());
					branches.add(plain);
				}
				LogicTree<?> subTree = LogicTree.fromExisting(baLevels, branches);
				File subTreeFile = new File(localDir, "sub_logic_tree_"+prefix+".json");
				subTree.write(subTreeFile);
				baLogicTreePaths.add("$DIR/"+subTreeFile.getName());
				baOutputDirs.add("$DIR/node_branch_averaged_"+prefix);
				baSuffixes.add("_"+prefix);
			}
		} else {
			baLogicTreePaths.add(analysisTreePath == null ? logicTreePath : analysisTreePath);
			baOutputDirs.add("$DIR/node_branch_averaged");
			baSuffixes.add("");
		}

		for (int i=0; i<baLogicTreePaths.size(); i++) {
			int total = MPJ_LogicTreeBranchAverageBuilder.buildCombinations(baTree, 1).size();
			if (total <= 0)
				continue;
			File baFile = null;
			if (baFiles != null) {
				if (baFiles.size() == 1)
					baFile = baFiles.values().iterator().next();
				else
					baFile = baFiles.get(baSuffixes.get(i));
			}
			int myNodes = Integer.min(nodes, total);
			StringBuilder args = new StringBuilder();
			appendArg(args, "--input-dir", resultsPath);
			appendArg(args, "--logic-tree", analysisTreePath == null ? baLogicTreePaths.get(i) : logicTreePath);
			if (analysisTreePath != null)
				appendArg(args, "--analysis-logic-tree", baLogicTreePaths.get(i));
			appendArg(args, "--output-dir", baOutputDirs.get(i));
			if (request.postProcess().nodeBASkipSectBySect())
				appendFlag(args, "--skip-sect-by-sect");
			appendArg(args, "--plot-level", PlotLevel.REVIEW.name());
			appendArg(args, "--depth", 1);
			if (baFile != null)
				appendArg(args, "--compare-to", "$DIR/"+baFile.getName());
			args.append(" ").append(MPJTaskCalculator.argumentBuilder().exactDispatch(1)
					.threads(request.hpc().threadsPerNode()).build());
			List<String> script = mpjWriter.buildScript(MPJ_LogicTreeBranchAverageBuilder.class.getName(), args.toString());
			batchWriter.writeScript(new File(localDir, "batch_node_ba"+baSuffixes.get(i)+".slurm"), script,
					capWeek(mins), myNodes, request.hpc().threadsPerNode(), request.hpc().memGBPerNode(), request.hpc().followUpQueue());
		}
	}

	private void writeGridHazardJobs(File localDir, BatchScriptWriter batchWriter, JavaShellScriptWriter mpjWriter,
			InversionScriptWriteRequest request, HazardArgs hazardArgs, int nodes, int mins, String fullLTPath,
			String randLTPath, String onlyLTPath, String resultsPath, String griddedBAName, int logicTreeSize)
			throws IOException {
		for (int i=0; i<5; i++) {
			int myNodes = nodes;
			File jobFile;
			StringBuilder args = new StringBuilder();
			if (i == 0) {
				if (griddedBAName != null) {
					appendArg(args, "--input-file", resultsPath+".zip");
					appendArg(args, "--external-grid-prov", "$DIR/"+griddedBAName);
				} else {
					appendArg(args, "--input-file", resultsPath+"_avg_gridded.zip");
				}
				appendArg(args, "--output-file", resultsPath+"_hazard_avg_gridded.zip");
				appendArg(args, "--output-dir", resultsPath);
				appendArg(args, "--gridded-seis", "INCLUDE");
				jobFile = new File(localDir, "batch_hazard_avg_gridded.slurm");
			} else if (i == 1) {
				appendArg(args, "--input-file", resultsPath);
				appendArg(args, "--logic-tree", fullLTPath);
				appendArg(args, "--output-file", resultsPath+"_hazard_full_gridded.zip");
				appendArg(args, "--output-dir", resultsPath+"_full_gridded");
				appendArg(args, "--combine-with-dir", resultsPath);
				appendArg(args, "--gridded-seis", "INCLUDE");
				if (logicTreeSize > 50)
					appendFlag(args, "--quick-grid-calc");
				jobFile = new File(localDir, "batch_hazard_full_gridded.slurm");
			} else if (i == 2) {
				appendArg(args, "--input-file", resultsPath);
				appendArg(args, "--logic-tree", randLTPath);
				appendArg(args, "--output-file", resultsPath+"_hazard_full_gridded_sampled.zip");
				appendArg(args, "--output-dir", resultsPath+"_full_gridded");
				appendArg(args, "--combine-with-dir", resultsPath);
				appendArg(args, "--gridded-seis", "INCLUDE");
				appendFlag(args, "--quick-grid-calc");
				jobFile = new File(localDir, "batch_hazard_full_gridded_sampled.slurm");
			} else if (i == 3) {
				appendArg(args, "--input-file", resultsPath);
				appendArg(args, "--logic-tree", onlyLTPath);
				appendArg(args, "--output-file", resultsPath+"_hazard_full_gridded_only.zip");
				appendArg(args, "--output-dir", resultsPath+"_full_gridded");
				appendArg(args, "--combine-with-dir", resultsPath);
				appendArg(args, "--gridded-seis", "ONLY");
				appendFlag(args, "--quick-grid-calc");
				jobFile = new File(localDir, "batch_hazard_full_gridded_only.slurm");
			} else {
				appendArg(args, "--input-file", resultsPath+"_gridded_branches.zip");
				appendArg(args, "--output-file", resultsPath+"_hazard_gridded_only.zip");
				appendArg(args, "--output-dir", resultsPath+"_gridded_only");
				appendArg(args, "--gridded-seis", "ONLY");
				jobFile = new File(localDir, "batch_hazard_gridded_only.slurm");
			}
			args.append(hazardArgs.regionArg);
			args.append(hazardArgs.sharedArgs);
			if (i == 1 && logicTreeSize > 400)
				args.append(" ").append(MPJTaskCalculator.argumentBuilder().maxDispatch(100)
						.threads(request.hpc().threadsPerNode()).build());
			else
				args.append(" ").append(MPJTaskCalculator.argumentBuilder().exactDispatch(1)
						.threads(request.hpc().threadsPerNode()).build());
			List<String> script = mpjWriter.buildScript(MPJ_LogicTreeHazardCalc.class.getName(), args.toString());
			int myMins = i == 1 ? capWeek(mins*5) : mins;
			batchWriter.writeScript(jobFile, script, myMins, myNodes, request.hpc().threadsPerNode(),
					request.hpc().memGBPerNode(), request.hpc().followUpQueue());
		}
	}

	private HazardArgs buildHazardArgs(File localDir, HazardConfig hazard, LogicTree<LogicTreeNode> logicTree,
			List<LogicTreeLevel<? extends LogicTreeNode>> levels, String analysisTreePath, String resultsPath)
			throws IOException {
		StringBuilder args = new StringBuilder();
		appendArg(args, "--input-file", resultsPath+".zip");
		if (analysisTreePath != null)
			appendArg(args, "--analysis-logic-tree", analysisTreePath);
		appendArg(args, "--output-dir", resultsPath);
		appendArg(args, "--gridded-seis", hazard.backgroundOption().name());
		HazardRegion region = resolveHazardRegion(localDir, hazard, logicTree, levels);
		args.append(region.arg);
		String sharedArgs = buildHazardSharedArgs(hazard);
		args.append(sharedArgs);
		return new HazardArgs(args.toString(), region.arg, sharedArgs);
	}

	private HazardRegion resolveHazardRegion(File localDir, HazardConfig hazard, LogicTree<LogicTreeNode> logicTree,
			List<LogicTreeLevel<? extends LogicTreeNode>> levels) throws IOException {
		if (hazard.region() != null) {
			File regionFile = new File(localDir, "gridded_region.geojson");
			Feature.write(hazard.region().toFeature(), regionFile);
			return new HazardRegion(" --region $DIR/"+regionFile.getName());
		}
		double gridSpacing = logicTree.size() > 1000 ? 0.2 : 0.1;
		if (hazard.gridSpacing() != null)
			gridSpacing = hazard.gridSpacing();
		return new HazardRegion(" --grid-spacing "+(float)gridSpacing);
	}

	private String buildHazardSharedArgs(HazardConfig hazard) {
		StringBuilder args = new StringBuilder();
		appendHazardSharedArgs(args, hazard);
		return args.toString();
	}

	private void appendHazardSharedArgs(StringBuilder args, HazardConfig hazard) {
		for (AttenRelRef gmpe : hazard.gmpes())
			appendArg(args, "--gmpe", gmpe.name());
		if (hazard.periods() != null && hazard.periods().length > 0) {
			StringBuilder periods = new StringBuilder();
			for (int i=0; i<hazard.periods().length; i++) {
				if (i > 0)
					periods.append(",");
				periods.append((float)hazard.periods()[i]);
			}
			appendArg(args, "--periods", periods.toString());
		}
		if (hazard.vs30() != null)
			appendArg(args, "--vs30", hazard.vs30().floatValue());
		if (hazard.supersample())
			appendFlag(args, "--supersample-quick");
		if (hazard.sigmaTruncation() != null)
			appendArg(args, "--gmm-sigma-trunc-one-sided", hazard.sigmaTruncation().floatValue());
	}

	private NodeCalcConfig resolveNodeCalcConfig(HPCConfig hpc, InversionConfig inversion, int logicTreeSize) {
		int origNodes = hpc.nodes();
		int nodes = Integer.min(origNodes, logicTreeSize);
		int numCalcs = logicTreeSize*inversion.runsPerBranch();
		int nodeRounds = (int)Math.ceil((double)numCalcs/(double)(nodes*hpc.inversionsPerBundle()));
		double calcNodes = Math.ceil((double)numCalcs/(double)(nodeRounds*hpc.inversionsPerBundle()));
		nodes = Integer.min(nodes, (int)calcNodes);
		if (origNodes > 1 && nodes < 2)
			nodes = 2;
		int perInversionMins = inversion.resolvePerInversionMinutes();
		int hazardMins = capWeek(Integer.max(60*10, 45*nodeRounds));
		return new NodeCalcConfig(nodes, nodeRounds, numCalcs, perInversionMins,
				inversion.resolveInversionJobMinutes(nodeRounds), hazardMins);
	}

	private InversionConfigurationFactory instantiateFactory(
			Class<? extends InversionConfigurationFactory> factoryClass) {
		try {
			return factoryClass.getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Failed instantiating "+factoryClass.getName(), e);
		}
	}

	private void validateAnalysisTree(LogicTree<LogicTreeNode> logicTree, LogicTree<LogicTreeNode> analysisTree) {
		if (analysisTree == null)
			return;
		Preconditions.checkState(analysisTree.size() == logicTree.size(),
				"analysis tree size mismatch: %s != %s", analysisTree.size(), logicTree.size());
		for (int i=0; i<analysisTree.size(); i++)
			Preconditions.checkState(analysisTree.getBranch(i).buildFileName()
					.equals(logicTree.getBranch(i).buildFileName()),
					"analysis tree branch mismatch at %s", i);
	}

	private void writeSitesFile(File file, Collection<Site> sites) throws IOException {
		CSVFile<String> csv = new CSVFile<>(true);
		csv.addLine("Name", "Latitude", "Longitude");
		for (Site site : sites)
			csv.addLine(site.getName(), site.getLocation().lat+"", site.getLocation().lon+"");
		csv.writeToFile(file);
	}

	private static void appendArg(StringBuilder args, String name, Object value) {
		args.append(" ").append(name).append(" ").append(value);
	}

	private static void appendFlag(StringBuilder args, String name) {
		args.append(" ").append(name);
	}

	private static String quote(String value) {
		return "'"+value+"'";
	}

	private static int capWeek(int mins) {
		return Integer.min(mins, 60*24*7 - 1);
	}

	private record NodeCalcConfig(int nodes, int nodeRounds, int numCalcs, int perInversionMins,
			int inversionMins, int hazardMins) {}

	private record HazardRegion(String arg) {}

	private record HazardArgs(String baseArgs, String regionArg, String sharedArgs) {}
}
