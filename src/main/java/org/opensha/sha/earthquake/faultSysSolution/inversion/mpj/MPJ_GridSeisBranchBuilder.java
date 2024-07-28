package org.opensha.sha.earthquake.faultSysSolution.inversion.mpj;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.FileUtils;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.GridSourceProviderFactory;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchAveragingOrder;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchRegionalMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.RegionsOfInterest;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;

import edu.usc.kmilner.mpj.taskDispatch.AsyncPostBatchHook;
import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;
import mpi.MPI;

public class MPJ_GridSeisBranchBuilder extends MPJTaskCalculator {
	
	private GridSourceProviderFactory factory;
	
	private LogicTree<?> tree;
	private File solsDir;
	
	private LogicTree<?> gridSeisOnlyTree;

	private File nodesAverageDir;
	private File myAverageDir;
	private Table<String, String, AveragingAccumulator<GridSourceProvider>> gridSeisAveragers;

	private File writeFullTreeFile;
	private File writeRandTreeFile;
	private int numSamples = -1;
	private int numSamplesPerSol = -1;
	
	private Map<String, double[]> rankWeights;
	
	private boolean averageOnly = false;
	
	private boolean rebuild = false;
	
	private ExecutorService exec;
	
	public static final String GRID_ASSOCIATIONS_ARCHIVE_NAME = "fault_grid_associations.zip";
	public static final String AVG_GRID_SIE_PROV_ARCHIVE_NAME = "avg_grid_seis.zip"; 
	public static final String GRID_BRANCH_REGIONAL_MFDS_NAME = "grid_branch_regional_mfds.zip"; 
	
	public MPJ_GridSeisBranchBuilder(CommandLine cmd) throws IOException {
		super(cmd);
		
		this.shuffle = false;
		
		tree = LogicTree.read(new File(cmd.getOptionValue("logic-tree")));
		if (rank == 0)
			debug("Loaded "+tree.size()+" tree nodes");
		
		solsDir = new File(cmd.getOptionValue("sol-dir"));
		Preconditions.checkState(solsDir.exists());
		
		try {
			@SuppressWarnings("unchecked")
			Class<? extends GridSourceProviderFactory> factoryClass = (Class<? extends GridSourceProviderFactory>)
					Class.forName(cmd.getOptionValue("factory"));
			factory = factoryClass.getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			throw ExceptionUtils.asRuntimeException(e);
		}
		
		if (cmd.hasOption("gridded-logic-tree")) {
			gridSeisOnlyTree = LogicTree.read(new File(cmd.getOptionValue("gridded-logic-tree")));
		} else {
			gridSeisOnlyTree = factory.getGridSourceTree(tree);
		}
		Preconditions.checkNotNull(gridSeisOnlyTree, "Gridded seismicity tree cannot be null");
		Preconditions.checkState(gridSeisOnlyTree.size() > 0, "Gridded seismicity tree cannot be empty");
		int totBranches = getNumTasks()*gridSeisOnlyTree.size();
		if (rank == 0)
			debug("Will build "+gridSeisOnlyTree.size()+" grid-seis branches per fault branch, "+totBranches+" in total");
		
		int threads = getNumThreads();
		if (rank == 0)
			// subtract a thread, we've got other stuff to do
			threads = Integer.max(1, threads-1);
		if (threads > 1)
			exec = Executors.newFixedThreadPool(threads);
		
		averageOnly = cmd.hasOption("average-only");
		rebuild = cmd.hasOption("rebuild");
		
		gridSeisAveragers = HashBasedTable.create();
		
		nodesAverageDir = new File(solsDir, "node_grid_seis_averages");
		if (rank == 0) {
			rankWeights = new HashMap<>();
			if (nodesAverageDir.exists()) {
				// delete anything preexisting
				for (File file : nodesAverageDir.listFiles())
					Preconditions.checkState(FileUtils.deleteRecursive(file));
			} else {
				Preconditions.checkState(nodesAverageDir.mkdir());
			}
		}
		myAverageDir = new File(nodesAverageDir, "rank_"+rank);
		
		if (cmd.hasOption("write-full-tree"))
			writeFullTreeFile = new File(cmd.getOptionValue("write-full-tree"));
		if (cmd.hasOption("write-rand-tree")) {
			writeRandTreeFile = new File(cmd.getOptionValue("write-rand-tree"));
			if (cmd.hasOption("num-samples")) {
				Preconditions.checkState(!cmd.hasOption("num-samples-per-sol"),
						"Can only supply one of --num-samples and --num-samples-per-sol");
				numSamples = Integer.parseInt(cmd.getOptionValue("num-samples"));
			} else if (cmd.hasOption("num-samples-per-sol")) {
				Preconditions.checkState(!cmd.hasOption("num-samples"),
						"Can only supply one of --num-samples and --num-samples-per-sol");
				numSamplesPerSol = Integer.parseInt(cmd.getOptionValue("num-samples-per-sol"));
			} else {
				throw new IllegalArgumentException("Must supply either --num-samples or --num-samples-per-sol with --write-rand-tree");
			}
		}
		
		if (rank == 0)
			this.postBatchHook = new AsyncGridSeisCopier();
	}
	
	private class AsyncGridSeisCopier extends AsyncPostBatchHook {
		
		private Map<String, AveragingAccumulator<GridSourceProvider>> gridSourceAveragers;
		private Map<String, AveragingAccumulator<FaultGridAssociations>> faultGridAveragers;
		private Map<String, BranchRegionalMFDs.Builder> regionalMFDsBuilders;
		
		private File workingOutputFile;
		private File outputFile;
		private File workingAvgOutputFile;
		private File avgOutputFile;
		
		// this apache commons zip alternative allows copying files from one zip to another without de/recompressing
		private ZipArchiveOutputStream fullZipOut;
		private ZipArchiveOutputStream avgZipOut;
		
		private CompletableFuture<Void> origZipCopyFuture;
		
		private String sltPrefix = SolutionLogicTree.SUB_DIRECTORY_NAME+"/";

		private HashSet<String> writtenFullGridSourceFiles;
		private HashSet<String> writtenAvgGridSourceFiles;
		private Map<LogicTreeLevel<?>, Integer> fullLevelIndexes;
		
		private List<? extends LogicTreeLevel<?>> origLevelsForGridReg;
		private List<? extends LogicTreeLevel<?>> origLevelsForGridMechs;
		private List<? extends LogicTreeLevel<?>> origLevelsForSubSeisMFDs;
		private List<? extends LogicTreeLevel<?>> origLevelsForUnassociatedMFDs;
		
		private List<? extends LogicTreeLevel<?>> fullLevelsForGridReg;
		private List<? extends LogicTreeLevel<?>> fullLevelsForGridMechs;
		private List<? extends LogicTreeLevel<?>> fullLevelsForSubSeisMFDs;
		private List<? extends LogicTreeLevel<?>> fullLevelsForUnassociatedMFDs;
		
		public AsyncGridSeisCopier() throws IOException {
			super(1);
			
			if (!averageOnly) {
				outputFile = new File(solsDir.getParentFile(), solsDir.getName()+"_full_gridded.zip");
				workingOutputFile = new File(outputFile.getAbsolutePath()+".tmp");
				fullZipOut = new ZipArchiveOutputStream(workingOutputFile);
			}
			avgOutputFile = new File(solsDir.getParentFile(), solsDir.getName()+"_avg_gridded.zip");
			workingAvgOutputFile = new File(avgOutputFile.getAbsolutePath()+".tmp");
			avgZipOut = new ZipArchiveOutputStream(workingAvgOutputFile);
			
			// this figure will signal that we're done copying and we can start adding grid source providers
			origZipCopyFuture = CompletableFuture.runAsync(new Runnable() {
				
				@Override
				public void run() {
					try {
						if (!averageOnly)
							origResultsZipCopy(fullZipOut, new File(solsDir.getParentFile(), solsDir.getName()+".zip"));
						origResultsZipCopy(avgZipOut, new File(solsDir.getParentFile(), solsDir.getName()+".zip"));
					} catch (Exception e) {
						abortAndExit(e, 1);
					}
				}
			});
			
			for (boolean full : new boolean[] {false, true}) {
				LogicTreeBranch<?> branch;
				if (full)
					branch = getCombinedBranch(tree.getBranch(0), gridSeisOnlyTree.getBranch(0));
				else
					branch = tree.getBranch(0);
				List<LogicTreeLevel<?>> levels = new ArrayList<>();
				for (LogicTreeLevel<?> level : branch.getLevels())
					levels.add(level);
				List<? extends LogicTreeLevel<?>> levelsForGridReg = SolutionLogicTree.getLevelsAffectingFile(
						GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME, false, levels);
				List<? extends LogicTreeLevel<?>> levelsForGridMechs = SolutionLogicTree.getLevelsAffectingFile(
						GridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME, false, levels);
				List<? extends LogicTreeLevel<?>> levelsForSubSeisMFDs = SolutionLogicTree.getLevelsAffectingFile(
						GridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME, true, levels);
				List<? extends LogicTreeLevel<?>> levelsForSubUnassociatedMFDs = SolutionLogicTree.getLevelsAffectingFile(
						GridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME, true, levels);
				if (full) {
					this.fullLevelIndexes = new HashMap<>();
					for (int i=0; i<levels.size(); i++)
						fullLevelIndexes.put(levels.get(i), i);
					this.fullLevelsForGridReg = levelsForGridReg;
					this.fullLevelsForGridMechs = levelsForGridMechs;
					this.fullLevelsForSubSeisMFDs = levelsForSubSeisMFDs;
					this.fullLevelsForUnassociatedMFDs = levelsForSubUnassociatedMFDs;
				} else {
					this.origLevelsForGridReg = levelsForGridReg;
					this.origLevelsForGridMechs = levelsForGridMechs;
					this.origLevelsForSubSeisMFDs = levelsForSubSeisMFDs;
					this.origLevelsForUnassociatedMFDs = levelsForSubUnassociatedMFDs;
				}
			}
			
			gridSourceAveragers = new HashMap<>();
			faultGridAveragers = new HashMap<>();
			regionalMFDsBuilders = new HashMap<>();
			writtenAvgGridSourceFiles = new HashSet<>();
			writtenFullGridSourceFiles = new HashSet<>();
		}
		
		private void origResultsZipCopy(ZipArchiveOutputStream out, File sourceFile) throws IOException {
			ZipFile zip = new ZipFile(sourceFile);
			
			// don't want to write the logic tree file, we're overriding it
			String treeName = sltPrefix+SolutionLogicTree.LOGIC_TREE_FILE_NAME;
			
			Enumeration<? extends ZipArchiveEntry> entries = zip.getEntries();
			while (entries.hasMoreElements()) {
				ZipArchiveEntry sourceEntry = entries.nextElement();
				String name = sourceEntry.getName();
				if (name.equals(treeName))
					continue;
				ZipArchiveEntry outEntry = new ZipArchiveEntry(sourceEntry.getName());
				copyEntry(zip, sourceEntry, out, outEntry);
			}
			zip.close();
			
			
		}
		
		private void copyEntry(ZipFile sourceZip, ZipArchiveEntry sourceEntry,
				ZipArchiveOutputStream out, ZipArchiveEntry outEntry) throws IOException {
			debug("AsyncLogicTree: copying to zip file: "+outEntry.getName());
			outEntry.setCompressedSize(sourceEntry.getCompressedSize());
			outEntry.setCrc(sourceEntry.getCrc());
			outEntry.setExternalAttributes(sourceEntry.getExternalAttributes());
			outEntry.setExtra(sourceEntry.getExtra());
			outEntry.setExtraFields(sourceEntry.getExtraFields());
			outEntry.setGeneralPurposeBit(sourceEntry.getGeneralPurposeBit());
			outEntry.setInternalAttributes(sourceEntry.getInternalAttributes());
			outEntry.setMethod(sourceEntry.getMethod());
			outEntry.setRawFlag(sourceEntry.getRawFlag());
			outEntry.setSize(sourceEntry.getSize());
			out.addRawArchiveEntry(outEntry, sourceZip.getRawInputStream(sourceEntry));
		}

		@Override
		protected void batchProcessedAsync(int[] batch, int processIndex) {
			// make sure we're done copying the original source zip file
			try {
				origZipCopyFuture.get();
			} catch (InterruptedException | ExecutionException e) {
				abortAndExit(e, 1);
			}
			
			memoryDebug("AsyncLogicTree: beginning async call with batch size "
					+batch.length+" from "+processIndex+": "+getCountsString());
			
			for (int index : batch) {
				try {
					LogicTreeBranch<?> origBranch = tree.getBranch(index);
					
					debug("AsyncLogicTree: calcDone "+index+" = branch "+origBranch);
					
					File solDir = getSolFile(origBranch).getParentFile();
					File gridSeisDir = new File(solDir, "grid_source_providers");
					Preconditions.checkState(gridSeisDir.exists());
					
					// handle averaged associations and grid sources
					File associationsFile = new File(gridSeisDir, GRID_ASSOCIATIONS_ARCHIVE_NAME);
					FaultGridAssociations associations = null;
					if (associationsFile.exists()) {
						if (faultGridAveragers == null) {
							debug("WARNING: branch "+index+" has fault grid associations, but an earlier one didn't; skipping");
						} else {
							
						}
						ModuleArchive<OpenSHA_Module> assocArchive = new ModuleArchive<>(associationsFile);
						associations = assocArchive.requireModule(FaultGridAssociations.class);
					} else if (faultGridAveragers != null) {
						if (faultGridAveragers.isEmpty())
							debug("We don't have fault grid associations; skipping consolidation");
						else
							debug("WARNING: not all branches have fault grid associations, skipping");
						faultGridAveragers = null;
					}
					
					File avgGridFile = new File(gridSeisDir, AVG_GRID_SIE_PROV_ARCHIVE_NAME);
					ModuleArchive<OpenSHA_Module> avgArchive = new ModuleArchive<>(avgGridFile);
					GridSourceProvider avgGridProv = avgArchive.requireModule(GridSourceProvider.class);
					
					File regionalMFDFile = new File(gridSeisDir, GRID_BRANCH_REGIONAL_MFDS_NAME);
					ModuleArchive<OpenSHA_Module> mfdsArchive = new ModuleArchive<>(regionalMFDFile);
					BranchRegionalMFDs regionalMFDs = mfdsArchive.requireModule(BranchRegionalMFDs.class);
					
					String baPrefix = AbstractAsyncLogicTreeWriter.getBA_prefix(origBranch);
					Preconditions.checkNotNull(baPrefix);
					
					double[] baRankWeights = rankWeights.get(baPrefix);
					if (baRankWeights == null) {
						baRankWeights = new double[size];
						rankWeights.put(baPrefix, baRankWeights);
					}
					
					double branchWeight = tree.getBranchWeight(origBranch);
					baRankWeights[processIndex] += branchWeight;
					
					if (!gridSourceAveragers.containsKey(baPrefix))
						gridSourceAveragers.put(baPrefix, avgGridProv.averagingAccumulator());
					AveragingAccumulator<GridSourceProvider> accumulator = gridSourceAveragers.get(baPrefix);
					accumulator.process(avgGridProv, branchWeight);
					
					if (faultGridAveragers != null) {
						if (!faultGridAveragers.containsKey(baPrefix))
							faultGridAveragers.put(baPrefix, associations.averagingAccumulator());
						AveragingAccumulator<FaultGridAssociations> assocAccumulator = faultGridAveragers.get(baPrefix);
						assocAccumulator.process(associations, branchWeight);
					}
					
					debug("AsyncLogicTree: writing averaged grid source provider");
					// write out averaged grid source provider for this branch
					Map<String, String> origNameMappings = getNameMappings(origBranch, false);
					ZipFile avgGridZip = new ZipFile(avgGridFile);
					for (String sourceName : origNameMappings.keySet()) {
						String destName = origNameMappings.get(sourceName);
						if (!writtenAvgGridSourceFiles.contains(destName)) {
							copyEntry(avgGridZip, avgGridZip.getEntry(sourceName), avgZipOut, new ZipArchiveEntry(destName));
							writtenAvgGridSourceFiles.add(destName);
						}
					}
					avgGridZip.close();
					
					// instance file
					writeGridProvInstance(avgGridProv, origBranch, origLevelsForSubSeisMFDs, avgZipOut, writtenAvgGridSourceFiles);
					
					debug("AsyncLogicTree: processing regional MFDs");
					if (!regionalMFDsBuilders.containsKey(baPrefix))
						regionalMFDsBuilders.put(baPrefix, new BranchRegionalMFDs.Builder());
					regionalMFDsBuilders.get(baPrefix).process(regionalMFDs);
					
					if (!averageOnly) {
						// now copy each grid source provider to the output directory
						for (LogicTreeBranch<?> gridSeisBranch : gridSeisOnlyTree) {
							debug("AsyncLogicTree: writing branch grid source provider: "+gridSeisBranch);
							File gridSeisFile = new File(gridSeisDir, gridSeisBranch.buildFileName()+".zip");
							
							ZipFile sourceZip = new ZipFile(gridSeisFile);
							
							LogicTreeBranch<?> combBranch = getCombinedBranch(origBranch, gridSeisBranch);
							
							Map<String, String> nameMappings = getNameMappings(combBranch, true);
							
							for (String sourceName : nameMappings.keySet()) {
								String destName = nameMappings.get(sourceName);
								if (!writtenFullGridSourceFiles.contains(destName)) {
									copyEntry(sourceZip, sourceZip.getEntry(sourceName), fullZipOut, new ZipArchiveEntry(destName));
									writtenFullGridSourceFiles.add(destName);
								}
							}
							sourceZip.close();
							
							// instance file, use the average class type though as we don't want to load it and it will be the same
							writeGridProvInstance(avgGridProv, combBranch, fullLevelsForSubSeisMFDs, fullZipOut, writtenFullGridSourceFiles);
						}
					}
				} catch (Exception e) {
					abortAndExit(e, 1);
				}
			}
			
			memoryDebug("AsyncLogicTree: exiting async process, stats: "+getCountsString());
		}
		
		private void writeGridProvInstance(GridSourceProvider prov, LogicTreeBranch<?> branch,
				List<? extends LogicTreeLevel<?>> levelsAffecting, ZipArchiveOutputStream out,
						HashSet<String> writtenFiles) throws IOException {
			if (!(prov instanceof ArchivableModule) || prov instanceof GridSourceProvider.Default)
				return;
			String avgInstanceFileName = getBranchFileName(branch, sltPrefix,
					SolutionLogicTree.GRID_PROV_INSTANCE_FILE_NAME, levelsAffecting);
			if (!writtenFiles.contains(avgInstanceFileName)) {
				Class<? extends ArchivableModule> loadingClass = ((ArchivableModule)prov).getLoadingClass();
				out.putArchiveEntry(new ZipArchiveEntry(avgInstanceFileName));
				SolutionLogicTree.writeGridSourceProvInstanceFile(out, loadingClass);
				out.flush();
				out.closeArchiveEntry();
				writtenFiles.add(avgInstanceFileName);
			}
			
		}
		
		private Map<String, String> getNameMappings(LogicTreeBranch<?> branch, boolean full) {
			Map<String, String> nameMappings = new HashMap<>(4);
			nameMappings.put(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME,
					getBranchFileName(branch, sltPrefix,
							GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME,
							full ? fullLevelsForGridReg : origLevelsForGridReg));
			nameMappings.put(GridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME,
					getBranchFileName(branch, sltPrefix,
							GridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME,
							full ? fullLevelsForGridMechs : origLevelsForGridMechs));
			nameMappings.put(GridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME,
					getBranchFileName(branch, sltPrefix,
							GridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME,
							full ? fullLevelsForSubSeisMFDs : origLevelsForSubSeisMFDs));
			nameMappings.put(GridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME,
					getBranchFileName(branch, sltPrefix,
							GridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME,
							full ? fullLevelsForUnassociatedMFDs : origLevelsForUnassociatedMFDs));
			return nameMappings;
		}
		
		protected String getBranchFileName(LogicTreeBranch<?> branch, String prefix, String fileName,
				List<? extends LogicTreeLevel<?>> mappingLevels) {
			StringBuilder ret = new StringBuilder(prefix);
			Preconditions.checkNotNull(mappingLevels, "No mappings available for %", fileName);
			for (LogicTreeLevel<?> level : mappingLevels) {
				int levelIndex = fullLevelIndexes.get(level);
				LogicTreeNode value = branch.getValue(levelIndex);
				Preconditions.checkNotNull(value,
						"Branch does not have value for %s, needed to retrieve %s", level.getName(), fileName);
				ret.append(value.getFilePrefix()).append("/");
			}
			ret.append(fileName);
			return ret.toString();
		}

		@Override
		public void shutdown() {
			super.shutdown();
			
			try {
				if (!averageOnly) {
					// write the combined logic tree to the full zip file
					List<LogicTreeBranch<LogicTreeNode>> combinedBranches = new ArrayList<>();
					for (LogicTreeBranch<?> origBranch : tree) {
						double faultWeight = tree.getBranchWeight(origBranch);
						for (LogicTreeBranch<?> griddedBranch : gridSeisOnlyTree) {
							double gridWeight = gridSeisOnlyTree.getBranchWeight(griddedBranch);
							LogicTreeBranch<?> combinedBranch = getCombinedBranch(origBranch, griddedBranch);
							combinedBranch.setOrigBranchWeight(faultWeight*gridWeight);
							combinedBranches.add((LogicTreeBranch<LogicTreeNode>)combinedBranch);
						}
					}
					LogicTree<?> combinedLogicTree = LogicTree.fromExisting(combinedBranches.get(0).getLevels(), combinedBranches);
					
					ZipArchiveEntry logicTreeEntry = new ZipArchiveEntry(sltPrefix+SolutionLogicTree.LOGIC_TREE_FILE_NAME);
					fullZipOut.putArchiveEntry(logicTreeEntry);
					combinedLogicTree.writeToStream(new BufferedOutputStream(fullZipOut));
					fullZipOut.flush();
					fullZipOut.closeArchiveEntry();
					fullZipOut.close();
					Files.move(workingOutputFile, outputFile);
				}
				
				// write original logic tree to the average zip
				ZipArchiveEntry logicTreeEntry = new ZipArchiveEntry(sltPrefix+SolutionLogicTree.LOGIC_TREE_FILE_NAME);
				avgZipOut.putArchiveEntry(logicTreeEntry);
				tree.writeToStream(new BufferedOutputStream(avgZipOut));
				avgZipOut.flush();
				avgZipOut.closeArchiveEntry();
				avgZipOut.close();
				Files.move(workingAvgOutputFile, avgOutputFile);
				
				for (String baPrefix : gridSourceAveragers.keySet()) {
					String baFilePrefix = solsDir.getName();
					if (!baPrefix.isBlank())
						baFilePrefix += "_"+baPrefix;
					File baFile = new File(solsDir.getParentFile(), baFilePrefix+"_branch_averaged.zip");
					Preconditions.checkState(baFile.exists(), "Branch averaged file doesn't exist: %s", baFile.getAbsolutePath());

					memoryDebug("AsyncLogicTree: processing "+baFile.getAbsolutePath());
					GridSourceProvider avgProv = gridSourceAveragers.get(baPrefix).getAverage();
					FaultSystemSolution baSol = FaultSystemSolution.load(baFile);
					baSol.setGridSourceProvider(avgProv);
					
					if (faultGridAveragers != null) {
						FaultGridAssociations associations = faultGridAveragers.get(baPrefix).getAverage();
						baSol.getRupSet().addModule(associations);
					}
					
					if (baSol.getRupSet().hasModule(RegionsOfInterest.class)) {
						BranchRegionalMFDs regionalMFDs = regionalMFDsBuilders.get(baPrefix).build();
						baSol.addModule(regionalMFDs);
						// this uses a different order, remove the old one to avoid confusion but don't add the new one
						// because other modules will still use the old order
						baSol.removeModuleInstances(BranchAveragingOrder.class);
					}
					
					baSol.write(new File(solsDir.getParentFile(), baFilePrefix+"_branch_averaged_gridded.zip"));
				}
			} catch (IOException e) {
				abortAndExit(e, 1);
			}
		}
		
	}

	@Override
	protected int getNumTasks() {
		return tree.size();
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		for (int index : batch) {
			LogicTreeBranch<?> origBranch = tree.getBranch(index);
			
			debug("Building gridded seismicity for "+index+": "+origBranch);
			
			File solFile = getSolFile(origBranch);
			Preconditions.checkState(solFile.exists());
			
			File gridSeisDir = new File(solFile.getParentFile(), "grid_source_providers");
			Preconditions.checkState(gridSeisDir.exists() || gridSeisDir.mkdir(), "Couldn't create grid source dir: %s", gridSeisDir);
			
			FaultSystemSolution sol = FaultSystemSolution.load(solFile);
			
			factory.preGridBuildHook(sol, origBranch);
			FaultSystemRupSet rupSet = sol.getRupSet();
			
			FaultGridAssociations gridAssoc = rupSet.getModule(FaultGridAssociations.class);
			if (gridAssoc != null && gridAssoc instanceof ArchivableModule) {
				File assocFile = new File(gridSeisDir, GRID_ASSOCIATIONS_ARCHIVE_NAME);
				ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
				archive.addModule(gridAssoc);
				archive.write(assocFile);
			}
			
			List<CalcRunnable> runnables = new ArrayList<>();
			for (LogicTreeBranch<?> gridSeisBranch : gridSeisOnlyTree)
				runnables.add(new CalcRunnable(origBranch, gridSeisBranch, gridSeisDir, sol));
			
			if (exec == null) {
				// run them serially
				for (CalcRunnable run : runnables)
					run.run();
			} else {
				// run them in parallel
				List<Future<?>> futures = new ArrayList<>();
				for (CalcRunnable run : runnables)
					futures.add(exec.submit(run));
				for (Future<?> future : futures)
					future.get();
			}
			
			String baPrefix = AbstractAsyncLogicTreeWriter.getBA_prefix(origBranch);
			
			BranchRegionalMFDs.Builder mfdBuilder = new BranchRegionalMFDs.Builder();
			
			double faultWeight = tree.getBranchWeight(origBranch); 
			
			// now average
			AveragingAccumulator<GridSourceProvider> accumulator = null;
			for (CalcRunnable run : runnables) {
				GridSourceProvider prov = run.gridProv;
				Preconditions.checkNotNull(prov);
				// full average
				if (accumulator == null)
					accumulator = prov.averagingAccumulator();
				double griddedWeight = run.gridSeisBranch.getOrigBranchWeight();
				accumulator.process(prov, griddedWeight);
				// branch-specific average
				String gridPrefix = run.gridSeisBranch.buildFileName();
				AveragingAccumulator<GridSourceProvider> branchAccumulator = gridSeisAveragers.get(baPrefix, gridPrefix);
				
				if (branchAccumulator == null) {
					branchAccumulator = prov.averagingAccumulator();
					gridSeisAveragers.put(baPrefix, gridPrefix, branchAccumulator);
					mfdBuilder = new BranchRegionalMFDs.Builder();
				}
				branchAccumulator.process(prov, griddedWeight);
				
				mfdBuilder.process(sol, prov, run.combinedBranch, faultWeight * griddedWeight);
			}
			
			// write average
			File avgFile = new File(gridSeisDir, AVG_GRID_SIE_PROV_ARCHIVE_NAME);
			ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
			archive.addModule(accumulator.getAverage());
			archive.write(avgFile);
			
			// write regional mfds
			File mfdsFile = new File(gridSeisDir, GRID_BRANCH_REGIONAL_MFDS_NAME);
			archive = new ModuleArchive<>();
			archive.addModule(mfdBuilder.build());
			archive.write(mfdsFile);
			
			debug("done with "+index);
		}
	}
	
	protected File getSolFile(LogicTreeBranch<?> branch) {
		String dirName = branch.buildFileName();
		File runDir = new File(solsDir, dirName);
		Preconditions.checkState(runDir.exists() || runDir.mkdir());
		
		return new File(runDir, "solution.zip");
	}
	
	private LogicTreeBranch<?> getCombinedBranch(LogicTreeBranch<?> origBranch, LogicTreeBranch<?> gridSeisBranch) {
		List<LogicTreeLevel<? extends LogicTreeNode>> combLevels = new ArrayList<>();
		combLevels.addAll(origBranch.getLevels());
		combLevels.addAll(gridSeisBranch.getLevels());
		
		LogicTreeBranch<LogicTreeNode> combinedBranch = new LogicTreeBranch<>(combLevels);
		for (LogicTreeNode node : origBranch)
			combinedBranch.setValue(node);
		for (LogicTreeNode node : gridSeisBranch)
			combinedBranch.setValue(node);
		
		double origWeight = origBranch.getOrigBranchWeight() * gridSeisBranch.getOrigBranchWeight();
		combinedBranch.setOrigBranchWeight(origWeight);
		
		return combinedBranch;
	}

	private FaultSystemSolution solCopy(FaultSystemSolution origSol) {
		FaultSystemRupSet origRupSet = origSol.getRupSet();
		FaultSystemRupSet newRupSet = FaultSystemRupSet.buildFromExisting(origRupSet, true).build();
		FaultSystemSolution newSol = new FaultSystemSolution(newRupSet, origSol.getRateForAllRups());
		for (OpenSHA_Module module : origSol.getModules())
			newSol.addModule(module);
		return newSol;
	}
	
	private class CalcRunnable implements Runnable {
		
		// inputs
		private LogicTreeBranch<?> origBranch;
		private LogicTreeBranch<?> gridSeisBranch;
		private File gridSeisDir;
		private FaultSystemSolution sol;
		
		// outputs
		private LogicTreeBranch<?> combinedBranch;
		private GridSourceProvider gridProv;

		public CalcRunnable(LogicTreeBranch<?> origBranch, LogicTreeBranch<?> gridSeisBranch, File gridSeisDir,
				FaultSystemSolution sol) {
			this.origBranch = origBranch;
			this.gridSeisBranch = gridSeisBranch;
			this.gridSeisDir = gridSeisDir;
			this.sol = sol;
		}

		@Override
		public void run() {
			combinedBranch = getCombinedBranch(origBranch, gridSeisBranch);
			
			debug("Building for combined branch: "+combinedBranch);
			
			File outputFile = new File(gridSeisDir, gridSeisBranch.buildFileName()+".zip");
			
			if (outputFile.exists() && !rebuild) {
				// try loading it instead
				try {
					ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(outputFile);
					gridProv = archive.getModule(GridSourceProvider.class);
					return;
				} catch (Exception e) {
					// rebuild it
				}
			}
			
			try {
				gridProv = factory.buildGridSourceProvider(sol, combinedBranch);
				
				ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
				archive.addModule(gridProv);
				archive.write(outputFile);
			} catch (Exception e) {
				throw ExceptionUtils.asRuntimeException(e);
			}
		}
		
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		if (rank == 0) {
			memoryDebug("waiting for any post batch hook operations to finish");
			((AsyncPostBatchHook)postBatchHook).shutdown();
			memoryDebug("post batch hook done");
		}
		// write out branch-specific averages
		Preconditions.checkState(myAverageDir.exists() || myAverageDir.mkdir());
		for (Cell<String, String, AveragingAccumulator<GridSourceProvider>> cell : gridSeisAveragers.cellSet()) {
			String prefix = cell.getRowKey();
			if (!prefix.isBlank())
				prefix += "_";
			prefix += cell.getColumnKey();
			debug("Building node-average for "+prefix);
			GridSourceProvider avgProv = cell.getValue().getAverage();
			ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
			archive.addModule(avgProv);
			File outputFile = new File(myAverageDir, prefix+".zip");
			archive.write(outputFile);
		}
		
		// wait for everyone to write them out
		if (!SINGLE_NODE_NO_MPJ)
			MPI.COMM_WORLD.Barrier();
		
		if (rank == 0) {
			// now merge them in
			
			// figure out fault branches for each prefix
			Map<String, LogicTreeBranch<LogicTreeNode>> baCommonBranches = new HashMap<>();
			for (LogicTreeBranch<?> origBranch : tree) {
				String baPrefix = AbstractAsyncLogicTreeWriter.getBA_prefix(origBranch);
				LogicTreeBranch<LogicTreeNode> commonBranch = baCommonBranches.get(baPrefix);
				if (commonBranch == null) {
					List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
					List<LogicTreeNode> values = new ArrayList<>();
					for (int i=0; i<origBranch.size(); i++) {
						LogicTreeLevel<?> level = origBranch.getLevel(i);
						LogicTreeNode value = origBranch.getValue(i);
						levels.add(level);
						values.add(value);
					}
					commonBranch = new LogicTreeBranch<>(levels, values);
					baCommonBranches.put(baPrefix, commonBranch);
				}
				Preconditions.checkState(origBranch.size() == commonBranch.size());
				for (int i=0; i<origBranch.size(); i++) {
					LogicTreeNode commonVal = commonBranch.getValue(i);
					if (commonVal != null) {
						LogicTreeNode origVal = origBranch.getValue(i);
						if (origVal == null || !commonVal.equals(origVal))
							commonBranch.setValue(i, null);
					}
				}
			}
			
			File gridBranchesFile = new File(solsDir.getParentFile(), solsDir.getName()+"_gridded_branches.zip");
			SolutionLogicTree.FileBuilder sltBuilder = new SolutionLogicTree.FileBuilder(gridBranchesFile);
			sltBuilder.setSerializeGridded(true);
			for (String baPrefix : baCommonBranches.keySet()) {
				double[] baRankWeights = rankWeights.get(baPrefix);
				
				String baFilePrefix = solsDir.getName();
				if (!baPrefix.isBlank())
					baFilePrefix += "_"+baPrefix;
				File baFile = new File(solsDir.getParentFile(), baFilePrefix+"_branch_averaged.zip");
				
				FaultSystemSolution baSol = FaultSystemSolution.load(baFile);
				
				FaultGridAssociations associations = ((AsyncGridSeisCopier)postBatchHook).faultGridAveragers.get(baPrefix).getAverage();
				baSol.getRupSet().addModule(associations);
				
				debug("Building gridded-only branches for baPrefix="+baPrefix+" with rankWeights="
						+Joiner.on(",").join(Doubles.asList(baRankWeights)));
				
				LogicTreeBranch<LogicTreeNode> commonBranch = baCommonBranches.get(baPrefix);
				for (LogicTreeBranch<?> gridBranch : gridSeisOnlyTree) {
					// build combined branch
					List<LogicTreeLevel<? extends LogicTreeNode>> levels = new ArrayList<>();
					List<LogicTreeNode> values = new ArrayList<>();
					for (int i=0; i<commonBranch.size(); i++) {
						LogicTreeLevel<?> level = commonBranch.getLevel(i);
						LogicTreeNode value = commonBranch.getValue(i);
						if (value != null) {
							levels.add(level);
							values.add(value);
						}
					}
					for (int i=0; i<gridBranch.size(); i++) {
						LogicTreeLevel<?> level = gridBranch.getLevel(i);
						LogicTreeNode value = gridBranch.getValue(i);
						if (value != null) {
							levels.add(level);
							values.add(value);
						}
					}
					LogicTreeBranch<LogicTreeNode> combinedBranch = new LogicTreeBranch<>(levels, values);
					combinedBranch.setOrigBranchWeight(gridBranch.getOrigBranchWeight());
					String gridPrefix = gridBranch.buildFileName();
					debug("Combining prividers for baPrefix="+baPrefix+", gridPrefix="+gridPrefix+", combBranch="+combinedBranch);
					
					String prefix = baPrefix;
					if (!prefix.isBlank())
						prefix += "_";
					prefix += gridPrefix;
					
					int numNodes = 0;
					List<Future<GridSourceProvider>> futures = new ArrayList<>();
					for (int rank=0; rank<size; rank++) {
						File rankDir = new File(nodesAverageDir, "rank_"+rank);
						Preconditions.checkState(rankDir.exists(), "Dir doesn't exist: %s", rankDir.getAbsolutePath());
						File avgFile = new File(rankDir, prefix+".zip");
						if (avgFile.exists()) {
							numNodes++;
							futures.add(exec.submit(new Callable<GridSourceProvider>() {

								@Override
								public GridSourceProvider call() throws Exception {
									ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(avgFile);
									return archive.requireModule(GridSourceProvider.class);
								}
							}));
						} else {
							futures.add(null);
						}
					}
					debug("Processed providers for "+prefix+" from "+numNodes+" nodes");
					Preconditions.checkState(numNodes > 0, "No nodes processed %s", prefix);
					
					AveragingAccumulator<GridSourceProvider> accumulator = null;
					for (int rank=0; rank<size; rank++) {
						Future<GridSourceProvider> future = futures.get(rank);
						if (future != null) {
							GridSourceProvider gridProv = future.get();
							if (accumulator == null)
								accumulator = gridProv.averagingAccumulator();
							accumulator.process(gridProv, baRankWeights[rank]);
							// clear it from memory
							futures.set(rank, null);
						}
					}
					GridSourceProvider avgProv = accumulator.getAverage();
					
					baSol.addModule(avgProv);
					sltBuilder.solution(baSol, combinedBranch);
				}
			}
			sltBuilder.close();
			
			if (writeFullTreeFile != null || (writeRandTreeFile != null && numSamples > 0)) {
				// write the full logic tree
				debug("Building full logic tree for file output");
				List<LogicTreeLevel<? extends LogicTreeNode>> fullLevels = new ArrayList<>();
				fullLevels.addAll(tree.getLevels());
				fullLevels.addAll(gridSeisOnlyTree.getLevels());
				List<LogicTreeBranch<LogicTreeNode>> fullBranches = new ArrayList<>();
				for (LogicTreeBranch<?> origBranch : tree)
					for (LogicTreeBranch<?> gridBranch : gridSeisOnlyTree)
						fullBranches.add((LogicTreeBranch<LogicTreeNode>)getCombinedBranch(origBranch, gridBranch));
				LogicTree<LogicTreeNode> fullTree = LogicTree.fromExisting(fullLevels, fullBranches);
				if (writeFullTreeFile != null) {
					debug("Writing "+fullTree.size()+" branches to "+writeFullTreeFile.getAbsolutePath());
					fullTree.write(writeFullTreeFile);
				}
				if (writeRandTreeFile != null && numSamples > 0) {
					Random rand = new Random((long)fullTree.size()*numSamples);
					LogicTree<?> sampled = fullTree.sample(numSamples, false, rand);
					debug("Writing "+sampled.size()+" sampled branches to "+writeRandTreeFile.getAbsolutePath());
					sampled.write(writeRandTreeFile);
				}
			}
			if (writeRandTreeFile != null && numSamplesPerSol > 0) {
				debug("Building randomized logic tree with "+numSamplesPerSol+" gridded samples per solution for file output");
				List<LogicTreeLevel<? extends LogicTreeNode>> fullLevels = new ArrayList<>();
				fullLevels.addAll(tree.getLevels());
				fullLevels.addAll(gridSeisOnlyTree.getLevels());
				List<LogicTreeBranch<LogicTreeNode>> fullBranches = new ArrayList<>();
				Random rand = new Random((long)tree.size()*gridSeisOnlyTree.size()*numSamplesPerSol);
				for (LogicTreeBranch<?> origBranch : tree) {
					LogicTree<?> subTree = gridSeisOnlyTree.sample(numSamplesPerSol, false, rand, false);
					for (LogicTreeBranch<?> gridBranch : subTree)
						fullBranches.add((LogicTreeBranch<LogicTreeNode>)getCombinedBranch(origBranch, gridBranch));
				}
				LogicTree<LogicTreeNode> randTree = LogicTree.fromExisting(fullLevels, fullBranches);
				debug("Writing "+randTree.size()+" sampled branches to "+writeRandTreeFile.getAbsolutePath());
				randTree.write(writeRandTreeFile);
			}
		}
		if (exec != null)
			exec.shutdown();
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
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		ops.addRequiredOption("lt", "logic-tree", true, "Path to logic tree JSON file");
		ops.addOption(null, "gridded-logic-tree", true, "Optional path to gridded seismicity branch logic tree JSON file "
				+ "(otherwise will be loaded from the factory)");
		ops.addRequiredOption(null, "factory", true, "Gridded seismicity factory classname");
		ops.addRequiredOption("sd", "sol-dir", true, "Path to directory containing solutions");
		ops.addOption("ao", "average-only", false, "Flag to only write out average gridded seismicity for each fault "
				+ "branch.");
		ops.addOption("wft", "write-full-tree", true, "If supplied, will write full logic tree JSON to this file");
		ops.addOption(null, "write-rand-tree", true, "If supplied, will write a randomly sampled (from the full distribution) "
				+ "logic tree JSON to this file. Must supply either --num-samples or --num-samples-per-sol");
		ops.addOption(null, "num-samples", true, "If --write-rand-tree is enabled, will write this many random samples "
				+ "of the full tree");
		ops.addOption(null, "num-samples-per-sol", true, "If --write-rand-tree is enabled, will write this many random "
				+ "gridded seismicity samples for each solution");
		ops.addOption(null, "rebuild", false, "Flag to force rebuild of all providers");
		
		return ops;
	}

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		try {
			args = MPJTaskCalculator.initMPJ(args);
			
			Options options = createOptions();
			
			CommandLine cmd = parse(options, args, MPJ_GridSeisBranchBuilder.class);
			
			MPJ_GridSeisBranchBuilder driver = new MPJ_GridSeisBranchBuilder(cmd);
			driver.run();
			
			finalizeMPJ();
			
			System.exit(0);
		} catch (Throwable t) {
			abortAndExit(t);
		}
	}

}
