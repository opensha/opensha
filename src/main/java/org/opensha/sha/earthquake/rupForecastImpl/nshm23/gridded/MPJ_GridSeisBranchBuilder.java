package org.opensha.sha.earthquake.rupForecastImpl.nshm23.gridded;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.opensha.commons.geo.GriddedRegion;
import org.opensha.commons.geo.Region;
import org.opensha.commons.geo.json.Feature;
import org.opensha.commons.logicTree.LogicTree;
import org.opensha.commons.logicTree.LogicTreeBranch;
import org.opensha.commons.logicTree.LogicTreeLevel;
import org.opensha.commons.logicTree.LogicTreeNode;
import org.opensha.commons.util.ExceptionUtils;
import org.opensha.commons.util.modules.ArchivableModule;
import org.opensha.commons.util.modules.AverageableModule.AveragingAccumulator;
import org.opensha.commons.util.modules.ModuleArchive;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.mpj.AbstractAsyncLogicTreeWriter;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.ModelRegion;
import org.opensha.sha.earthquake.faultSysSolution.modules.SolutionLogicTree;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.NSHM23_InvConfigFactory;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_FaultModels;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.logicTree.NSHM23_LogicTreeBranch;
import org.opensha.sha.earthquake.rupForecastImpl.nshm23.util.NSHM23_RegionLoader.SeismicityRegions;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import edu.usc.kmilner.mpj.taskDispatch.AsyncPostBatchHook;
import edu.usc.kmilner.mpj.taskDispatch.MPJTaskCalculator;

public class MPJ_GridSeisBranchBuilder extends MPJTaskCalculator {
	
	private LogicTree<?> tree;
	private File solsDir;
	
	private LogicTree<?> gridSeisOnlyTree;
	
	private boolean averageOnly = false;
	
	private Region region;
	
	private ExecutorService exec;
	
	private static final String GRID_ASSOCIATIONS_ARCHIVE_NAME = "fault_grid_associations.zip";
	private static final String AVG_GRID_SIE_PROV_ARCHIVE_NAME = "avg_grid_seis.zip"; 
	
	public MPJ_GridSeisBranchBuilder(CommandLine cmd) throws IOException {
		super(cmd);
		
		this.shuffle = false;
		
		tree = LogicTree.read(new File(cmd.getOptionValue("logic-tree")));
		if (rank == 0)
			debug("Loaded "+tree.size()+" tree nodes");
		
		solsDir = new File(cmd.getOptionValue("sol-dir"));
		Preconditions.checkState(solsDir.exists());
		
		gridSeisOnlyTree = LogicTree.buildExhaustive(NSHM23_LogicTreeBranch.levelsOffFault, true);
		int totBranches = getNumTasks()*gridSeisOnlyTree.size();
		if (rank == 0)
			debug("Will build "+gridSeisOnlyTree.size()+" grid-seis branches per fault branch, "+totBranches+" in total");
		
		if (cmd.hasOption("region")) {
			File regFile = new File(cmd.getOptionValue("region"));
			Preconditions.checkState(regFile.exists(), "Supplied region file doesn't exist: %s", regFile.getAbsolutePath());
			Feature feature = Feature.read(regFile);
			region = Region.fromFeature(feature);
		}
		
		int threads = getNumThreads();
		if (threads > 1)
			exec = Executors.newFixedThreadPool(threads);
		
		averageOnly = cmd.hasOption("average-only");
		
		if (rank == 0)
			this.postBatchHook = new AsyncGridSeisCopier();
	}
	
	private class AsyncGridSeisCopier extends AsyncPostBatchHook {
		
		private Map<String, AveragingAccumulator<GridSourceProvider>> gridSourceAveragers;
		private Map<String, AveragingAccumulator<FaultGridAssociations>> faultGridAveragers;
		
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
					ModuleArchive<OpenSHA_Module> assocArchive = new ModuleArchive<>(associationsFile);
					FaultGridAssociations associations = assocArchive.requireModule(FaultGridAssociations.class);
					
					File avgGridFile = new File(gridSeisDir, AVG_GRID_SIE_PROV_ARCHIVE_NAME);
					ModuleArchive<OpenSHA_Module> avgArchive = new ModuleArchive<>(avgGridFile);
					GridSourceProvider avgGridProv = avgArchive.requireModule(GridSourceProvider.class);
					
					String baPrefix = AbstractAsyncLogicTreeWriter.getBA_prefix(origBranch);
					Preconditions.checkNotNull(baPrefix);
					
					double branchWeight = tree.getBranchWeight(origBranch);
					if (!gridSourceAveragers.containsKey(baPrefix))
						gridSourceAveragers.put(baPrefix, avgGridProv.averagingAccumulator());
					AveragingAccumulator<GridSourceProvider> accumulator = gridSourceAveragers.get(baPrefix);
					accumulator.process(avgGridProv, branchWeight);
					
					if (!faultGridAveragers.containsKey(baPrefix))
						faultGridAveragers.put(baPrefix, associations.averagingAccumulator());
					AveragingAccumulator<FaultGridAssociations> assocAccumulator = faultGridAveragers.get(baPrefix);
					assocAccumulator.process(associations, branchWeight);
					
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
							
							// instance file, use the average class type though as we don't want to laod it and it will be the same
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
					// write the comined logic tree to the full zip file
					List<LogicTreeBranch<LogicTreeNode>> combinedBranches = new ArrayList<>();
					for (LogicTreeBranch<?> origBranch : tree)
						for (LogicTreeBranch<?> griddedBranch : gridSeisOnlyTree)
							combinedBranches.add((LogicTreeBranch<LogicTreeNode>)getCombinedBranch(origBranch, griddedBranch));
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
					
					FaultGridAssociations associations = faultGridAveragers.get(baPrefix).getAverage();
					baSol.getRupSet().addModule(associations);
					
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
			
			if (isAlreadyDone(gridSeisDir)) {
				debug(index+" is already done, skipping");
				continue;
			}
			
			FaultSystemSolution sol = FaultSystemSolution.load(solFile);
			FaultSystemRupSet rupSet = sol.getRupSet();
			
			// figure out region
			Region region;
			if (this.region == null) {
				// detect it
				ModelRegion modelReg = rupSet.getModule(ModelRegion.class);
				if (modelReg == null) {
					modelReg = NSHM23_FaultModels.getDefaultRegion(origBranch);
					rupSet.addModule(modelReg);
				}
				region = modelReg.getRegion();
			} else {
				// use the one from the command line
				region = this.region;
				rupSet.addModule(new ModelRegion(region));
			}
			
			// pre-cache the cube associations once for this branch
			List<SeismicityRegions> seisRegions = NSHM23_InvConfigFactory.getSeismicityRegions(region);
			Preconditions.checkState(seisRegions.size() >= 1);
			NSHM23_FaultCubeAssociations cubeAssoc = null;
			if (seisRegions.size() == 1) {
				Region seisRegion = seisRegions.get(0).load();
				GriddedRegion seisGridReg = NSHM23_InvConfigFactory.getGriddedSeisRegion(seisRegion);
				cubeAssoc = new NSHM23_FaultCubeAssociations(rupSet,
						new CubedGriddedRegion(seisGridReg),
						NSHM23_SingleRegionGridSourceProvider.DEFAULT_MAX_FAULT_NUCL_DIST);
				if (!seisRegion.equalsRegion(region)) {
					// need to nest it
					GriddedRegion modelGridReg = NSHM23_InvConfigFactory.getGriddedSeisRegion(region);
					cubeAssoc = new NSHM23_FaultCubeAssociations(rupSet,
							new CubedGriddedRegion(modelGridReg), List.of(cubeAssoc));
				}
			} else {
				List<NSHM23_FaultCubeAssociations> regionalAssociations = new ArrayList<>();
				for (SeismicityRegions seisRegion : seisRegions) {
					GriddedRegion seisGridReg = NSHM23_InvConfigFactory.getGriddedSeisRegion(seisRegion.load());
					regionalAssociations.add(new NSHM23_FaultCubeAssociations(rupSet,
							new CubedGriddedRegion(seisGridReg),
							NSHM23_SingleRegionGridSourceProvider.DEFAULT_MAX_FAULT_NUCL_DIST));
				}
				GriddedRegion modelGridReg = NSHM23_InvConfigFactory.getGriddedSeisRegion(region);
				cubeAssoc = new NSHM23_FaultCubeAssociations(rupSet,
						new CubedGriddedRegion(modelGridReg), regionalAssociations);
			}
			
			File assocFile = new File(gridSeisDir, GRID_ASSOCIATIONS_ARCHIVE_NAME);
			ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
			archive.addModule(cubeAssoc);
			archive.write(assocFile);
			
			List<CalcRunnable> runnables = new ArrayList<>();
			for (LogicTreeBranch<?> gridSeisBranch : gridSeisOnlyTree)
				runnables.add(new CalcRunnable(origBranch, gridSeisBranch, gridSeisDir, sol, cubeAssoc));
			
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
			
			// now average
			AveragingAccumulator<GridSourceProvider> accumulator = null;
			for (CalcRunnable run : runnables) {
				GridSourceProvider prov = run.gridProv;
				Preconditions.checkNotNull(prov);
				if (accumulator == null)
					accumulator = prov.averagingAccumulator();
				accumulator.process(prov, run.gridSeisBranch.getOrigBranchWeight());
			}
			
			// write average
			File avgFile = new File(gridSeisDir, AVG_GRID_SIE_PROV_ARCHIVE_NAME);
			archive = new ModuleArchive<>();
			archive.addModule(accumulator.getAverage());
			archive.write(avgFile);
			
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
	
	private boolean isAlreadyDone(File gridSeisDir) {
		if (!new File(gridSeisDir, GRID_ASSOCIATIONS_ARCHIVE_NAME).exists())
			return false;
		if (!new File(gridSeisDir, AVG_GRID_SIE_PROV_ARCHIVE_NAME).exists())
			return false;
		if (!averageOnly) {
			for (LogicTreeBranch<?> gridSeisBranch : gridSeisOnlyTree) {
				File gridSeisFile =  new File(gridSeisDir, gridSeisBranch.buildFileName()+".zip");
				if (!gridSeisFile.exists())
					return false;
				try {
					new ModuleArchive<>(gridSeisFile).getModule(GridSourceProvider.class);
				} catch (Exception e) {
					return false;
				}
			}
		}
		return true;
	}
	
	private class CalcRunnable implements Runnable {
		
		private LogicTreeBranch<?> origBranch;
		private LogicTreeBranch<?> gridSeisBranch;
		private File gridSeisDir;
		private FaultSystemSolution origSol;
		private NSHM23_FaultCubeAssociations cubeAssoc;
		
		private GridSourceProvider gridProv;

		public CalcRunnable(LogicTreeBranch<?> origBranch, LogicTreeBranch<?> gridSeisBranch, File gridSeisDir,
				FaultSystemSolution origSol, NSHM23_FaultCubeAssociations cubeAssoc) {
			this.origBranch = origBranch;
			this.gridSeisBranch = gridSeisBranch;
			this.gridSeisDir = gridSeisDir;
			this.origSol = origSol;
			this.cubeAssoc = cubeAssoc;
		}

		@Override
		public void run() {
			LogicTreeBranch<?> combinedBranch = getCombinedBranch(origBranch, gridSeisBranch);
			
			debug("Building for combined branch: "+combinedBranch);
			
			File outputFile = new File(gridSeisDir, gridSeisBranch.buildFileName()+".zip");
			
			if (outputFile.exists()) {
				// try loading it instead
				try {
					ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(outputFile);
					gridProv = archive.getModule(GridSourceProvider.class);
					if (gridProv != null)
						return;
				} catch (Exception e) {
					// rebuild it
				}
			}
			
			// build a copy of the rupture set/solution for this branch with all original modules attached
			FaultSystemSolution newSol = solCopy(origSol);
			FaultSystemRupSet newRupSet = newSol.getRupSet();
			
			// add cube associations so that we can reuse them
			newRupSet.addModule(cubeAssoc);
			newRupSet.addModule(combinedBranch);
			newSol.addModule(combinedBranch);
			
			try {
				gridProv = NSHM23_InvConfigFactory.buildGridSourceProv(
						newSol, combinedBranch);
				newSol.setGridSourceProvider(gridProv);
				
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
		if (exec != null)
			exec.shutdown();
		if (rank == 0) {
			memoryDebug("waiting for any post batch hook operations to finish");
			((AsyncPostBatchHook)postBatchHook).shutdown();
			memoryDebug("post batch hook done");
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
	
	public static Options createOptions() {
		Options ops = MPJTaskCalculator.createOptions();
		
		ops.addRequiredOption("lt", "logic-tree", true, "Path to logic tree JSON file");
		ops.addRequiredOption("sd", "sol-dir", true, "Path to directory containing solutions");
		ops.addOption("r", "region", true, "Optional path to GeoJSON file containing a region for which we should compute hazard. "
				+ "Can be a gridded region or an outline. If not supplied, then one will be detected from the model.");
		ops.addOption("ao", "average-only", false, "Flag to only write out average gridded seismicity for each fault "
				+ "branch.");
		
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
