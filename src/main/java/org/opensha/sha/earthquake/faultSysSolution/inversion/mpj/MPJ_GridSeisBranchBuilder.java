package org.opensha.sha.earthquake.faultSysSolution.inversion.mpj;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import org.opensha.commons.util.modules.ModuleArchive.ModuleRecord;
import org.opensha.commons.util.modules.OpenSHA_Module;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemRupSet;
import org.opensha.sha.earthquake.faultSysSolution.FaultSystemSolution;
import org.opensha.sha.earthquake.faultSysSolution.inversion.GridSourceProviderFactory;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchAveragingOrder;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchRegionalMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
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
	
	private Map<String, AveragingAccumulator<GridSourceProvider>> nodeGridSourceAveragers;
	private Map<String, AveragingAccumulator<FaultGridAssociations>> nodeFaultGridAveragers;
	private Map<String, BranchRegionalMFDs.Builder> nodeRegionalMFDsBuilders;
	
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
		else
			exec = Executors.newSingleThreadExecutor();
		
		averageOnly = cmd.hasOption("average-only");
		rebuild = cmd.hasOption("rebuild");
		
		gridSeisAveragers = HashBasedTable.create();
		
		nodesAverageDir = new File(solsDir, "node_grid_seis_averages");
		if (rank == 0) {
			rankWeights = new HashMap<>();
			if (nodesAverageDir.exists()) {
				// delete anything preexisting
				for (File file : nodesAverageDir.listFiles()) {
					debug("Deleting previous data in "+nodesAverageDir.getName()+"/"+file.getName());
					if (file.isDirectory())
						Preconditions.checkState(FileUtils.deleteRecursive(file));
					else
						file.delete();
				}
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
		private List<? extends LogicTreeLevel<?>> fullLevelsForGridReg;
		// MFD grid source prov
		private List<? extends LogicTreeLevel<?>> origLevelsForGridMechs;
		private List<? extends LogicTreeLevel<?>> origLevelsForSubSeisMFDs;
		private List<? extends LogicTreeLevel<?>> origLevelsForUnassociatedMFDs;
		private List<? extends LogicTreeLevel<?>> fullLevelsForGridMechs;
		private List<? extends LogicTreeLevel<?>> fullLevelsForSubSeisMFDs;
		private List<? extends LogicTreeLevel<?>> fullLevelsForUnassociatedMFDs;
		// grid source list
		private List<? extends LogicTreeLevel<?>> origLevelsForGridLocs;
		private List<? extends LogicTreeLevel<?>> origLevelsForGridSources;
		private List<? extends LogicTreeLevel<?>> fullLevelsForGridLocs;
		private List<? extends LogicTreeLevel<?>> fullLevelsForGridSources;
		
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
						GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME, false, levels); // false: not affected by default
				List<? extends LogicTreeLevel<?>> levelsForGridMechs = SolutionLogicTree.getLevelsAffectingFile(
						MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME, false, levels); // false: not affected by default
				List<? extends LogicTreeLevel<?>> levelsForSubSeisMFDs = SolutionLogicTree.getLevelsAffectingFile(
						MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME, true, levels); // false: not affected by default
				List<? extends LogicTreeLevel<?>> levelsForSubUnassociatedMFDs = SolutionLogicTree.getLevelsAffectingFile(
						MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME, true, levels); // true: affected by default
				List<? extends LogicTreeLevel<?>> levelsForGridLocs = SolutionLogicTree.getLevelsAffectingFile(
						GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME, false, levels); // false: not affected by default
				List<? extends LogicTreeLevel<?>> levelsForGridSources = SolutionLogicTree.getLevelsAffectingFile(
						GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME, true, levels); // true: affected by default
				if (full) {
					this.fullLevelIndexes = new HashMap<>();
					for (int i=0; i<levels.size(); i++)
						fullLevelIndexes.put(levels.get(i), i);
					this.fullLevelsForGridReg = levelsForGridReg;
					this.fullLevelsForGridMechs = levelsForGridMechs;
					this.fullLevelsForSubSeisMFDs = levelsForSubSeisMFDs;
					this.fullLevelsForUnassociatedMFDs = levelsForSubUnassociatedMFDs;
					this.fullLevelsForGridLocs = levelsForGridLocs;
					this.fullLevelsForGridSources = levelsForGridSources;
				} else {
					this.origLevelsForGridReg = levelsForGridReg;
					this.origLevelsForGridMechs = levelsForGridMechs;
					this.origLevelsForSubSeisMFDs = levelsForSubSeisMFDs;
					this.origLevelsForUnassociatedMFDs = levelsForSubUnassociatedMFDs;
					this.origLevelsForGridLocs = levelsForGridLocs;
					this.origLevelsForGridSources = levelsForGridSources;
				}
			}
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
					+batch.length+" from "+processIndex+": "+getRatesString());
			
			for (int index : batch) {
				try {
					LogicTreeBranch<?> origBranch = tree.getBranch(index);
					
					debug("AsyncLogicTree: calcDone "+index+" = branch "+origBranch);
					
					File solDir = getSolFile(origBranch).getParentFile();
					File gridSeisDir = new File(solDir, "grid_source_providers");
					Preconditions.checkState(gridSeisDir.exists());
					
					File avgGridFile = new File(gridSeisDir, AVG_GRID_SIE_PROV_ARCHIVE_NAME);
					
					String baPrefix = AbstractAsyncLogicTreeWriter.getBA_prefix(origBranch);
					Preconditions.checkNotNull(baPrefix);
					
					double[] baRankWeights = rankWeights.get(baPrefix);
					if (baRankWeights == null) {
						baRankWeights = new double[size];
						rankWeights.put(baPrefix, baRankWeights);
					}
					
					double branchWeight = tree.getBranchWeight(origBranch);
					baRankWeights[processIndex] += branchWeight;
					
					debug("AsyncLogicTree: copying averaged grid source provider");
					// write out averaged grid source provider for this branch
					ZipFile avgGridZip = new ZipFile(avgGridFile);
					Class<? extends GridSourceProvider> provClass = loadGridSourceProvClass(avgGridZip);
					Map<String, String> origNameMappings = getNameMappings(origBranch, false, provClass);
					for (String sourceName : origNameMappings.keySet()) {
						String destName = origNameMappings.get(sourceName);
						if (!writtenAvgGridSourceFiles.contains(destName)) {
							ZipArchiveEntry entry = avgGridZip.getEntry(sourceName);
							if (entry == null) {
								// grid region can be null
								Preconditions.checkState(sourceName.endsWith(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME));
								continue;
							}
							copyEntry(avgGridZip, entry, avgZipOut, new ZipArchiveEntry(destName));
							writtenAvgGridSourceFiles.add(destName);
						}
					}
					// instance file
					writeGridProvInstance(provClass, origBranch, origLevelsForSubSeisMFDs, avgZipOut, writtenAvgGridSourceFiles);
					avgGridZip.close();
					
					if (!averageOnly) {
						// now copy each grid source provider to the output directory
						for (LogicTreeBranch<?> gridSeisBranch : gridSeisOnlyTree) {
							debug("AsyncLogicTree: copying branch grid source provider: "+gridSeisBranch);
							File gridSeisFile = new File(gridSeisDir, gridSeisBranch.buildFileName()+".zip");
							
							ZipFile sourceZip = new ZipFile(gridSeisFile);
							
							LogicTreeBranch<?> combBranch = getCombinedBranch(origBranch, gridSeisBranch);
							
							Map<String, String> nameMappings = getNameMappings(combBranch, true, provClass);
							
							for (String sourceName : nameMappings.keySet()) {
								String destName = nameMappings.get(sourceName);
								if (!writtenFullGridSourceFiles.contains(destName)) {
									ZipArchiveEntry entry = sourceZip.getEntry(sourceName);
									if (entry == null) {
										// grid region can be null
										Preconditions.checkState(sourceName.endsWith(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME));
										continue;
									}
									copyEntry(sourceZip, entry, fullZipOut, new ZipArchiveEntry(destName));
									writtenFullGridSourceFiles.add(destName);
								}
							}
							
							// instance file, use the average class type though as we don't want to load it and it will be the same
							writeGridProvInstance(provClass, combBranch, fullLevelsForSubSeisMFDs, fullZipOut, writtenFullGridSourceFiles);
							
							sourceZip.close();
						}
					}
				} catch (Exception e) {
					abortAndExit(e, 1);
				}
			}
			
			memoryDebug("AsyncLogicTree: exiting async process, stats: "+getCountsString());
		}
		
		@SuppressWarnings("unchecked")
		private Class<? extends GridSourceProvider> loadGridSourceProvClass(ZipFile sourceZip) throws IOException {
			ZipArchiveEntry modulesEntry = sourceZip.getEntry(ModuleArchive.MODULE_FILE_NAME);
			List<ModuleRecord> records = ModuleArchive.loadModulesManifest(sourceZip.getInputStream(modulesEntry));
			Preconditions.checkState(records.size() == 1);
			String className = records.get(0).className;
			Class<?> clazz;
			try {
				clazz = Class.forName(className);
			} catch (ClassNotFoundException e) {
				// shouldn't happen; this file was written by this class
				throw ExceptionUtils.asRuntimeException(e);
			}
			Preconditions.checkState(GridSourceProvider.class.isAssignableFrom(clazz));
			return (Class<? extends GridSourceProvider>) clazz;
		}
		
		private void writeGridProvInstance(Class<? extends GridSourceProvider> provClass, LogicTreeBranch<?> branch,
				List<? extends LogicTreeLevel<?>> levelsAffecting, ZipArchiveOutputStream out,
						HashSet<String> writtenFiles) throws IOException {
			Preconditions.checkState(ArchivableModule.class.isAssignableFrom(provClass));
			@SuppressWarnings("unchecked")
			Class<? extends ArchivableModule> moduleClass = (Class<? extends ArchivableModule>) provClass;
			if (MFDGridSourceProvider.class.isAssignableFrom(moduleClass)
					&& !MFDGridSourceProvider.Default.class.isAssignableFrom(moduleClass)) {
				String avgInstanceFileName = getBranchFileName(branch, sltPrefix,
						SolutionLogicTree.GRID_PROV_INSTANCE_FILE_NAME, levelsAffecting);
				// write out if it's an MFDGridSourceProvider, but not an MFDGridSourceProvider.Default
				out.putArchiveEntry(new ZipArchiveEntry(avgInstanceFileName));
				SolutionLogicTree.writeGridSourceProvInstanceFile(out, moduleClass);
				out.flush();
				out.closeArchiveEntry();
				writtenFiles.add(avgInstanceFileName);
			}
		}
		
		private Map<String, String> getNameMappings(LogicTreeBranch<?> branch, boolean full, Class<? extends GridSourceProvider> provClass) {
			Map<String, String> nameMappings = new HashMap<>(4);
			if (MFDGridSourceProvider.class.isAssignableFrom(provClass)) {
				nameMappings.put(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME,
						getBranchFileName(branch, sltPrefix,
								GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME,
								full ? fullLevelsForGridReg : origLevelsForGridReg));
				nameMappings.put(MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME,
						getBranchFileName(branch, sltPrefix,
								MFDGridSourceProvider.ARCHIVE_MECH_WEIGHT_FILE_NAME,
								full ? fullLevelsForGridMechs : origLevelsForGridMechs));
				nameMappings.put(MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME,
						getBranchFileName(branch, sltPrefix,
								MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME,
								full ? fullLevelsForSubSeisMFDs : origLevelsForSubSeisMFDs));
				nameMappings.put(MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME,
						getBranchFileName(branch, sltPrefix,
								MFDGridSourceProvider.ARCHIVE_UNASSOCIATED_FILE_NAME,
								full ? fullLevelsForUnassociatedMFDs : origLevelsForUnassociatedMFDs));
			} else if (GridSourceList.class.isAssignableFrom(provClass)) {
				nameMappings.put(GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME,
						getBranchFileName(branch, sltPrefix,
								GridSourceProvider.ARCHIVE_GRID_REGION_FILE_NAME,
								full ? fullLevelsForGridReg : origLevelsForGridReg));
				nameMappings.put(GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME,
						getBranchFileName(branch, sltPrefix,
								GridSourceList.ARCHIVE_GRID_LOCS_FILE_NAME,
								full ? fullLevelsForGridLocs : origLevelsForGridLocs));
				nameMappings.put(GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME,
						getBranchFileName(branch, sltPrefix,
								GridSourceList.ARCHIVE_GRID_SOURCES_FILE_NAME,
								full ? fullLevelsForGridSources : origLevelsForGridSources));
			} else {
				throw new IllegalStateException("Unexpected GridSourceProvider class: "+provClass.getName());
			}
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
		CompletableFuture<Void> postProcess = null;
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
			
			String baPrefix = AbstractAsyncLogicTreeWriter.getBA_prefix(origBranch);

			AveragingAccumulator<GridSourceProvider> accumulator = null;
			BranchRegionalMFDs.Builder mfdBuilder = new BranchRegionalMFDs.Builder();
			
			double faultWeight = tree.getBranchWeight(origBranch);
			
			// use a deque so that we can clear them out of memory as they roll off the line
			ArrayDeque<Future<CalcCallable>> futures = new ArrayDeque<>(gridSeisOnlyTree.size());
			for (int gridIndex=0; gridIndex<gridSeisOnlyTree.size(); gridIndex++)
				futures.add(exec.submit(new CalcCallable(origBranch, gridIndex, gridSeisDir, sol)));
			
			while (!futures.isEmpty()) {
				Future<CalcCallable> future = futures.removeFirst();
				
				CalcCallable call = future.get();
				
				GridSourceProvider prov = call.gridProv;
				Preconditions.checkNotNull(prov);
				// full average
				if (accumulator == null)
					accumulator = prov.averagingAccumulator();
				double griddedWeight = call.gridSeisBranch.getOrigBranchWeight();
				accumulator.process(prov, griddedWeight);
				// branch-specific average
				String gridPrefix = call.gridSeisBranch.buildFileName();
				AveragingAccumulator<GridSourceProvider> branchAccumulator = gridSeisAveragers.get(baPrefix, gridPrefix);
				
				if (branchAccumulator == null) {
					branchAccumulator = prov.averagingAccumulator();
					gridSeisAveragers.put(baPrefix, gridPrefix, branchAccumulator);
				}
				branchAccumulator.process(prov, griddedWeight);
				
				mfdBuilder.process(sol, prov, call.combinedBranch, faultWeight * griddedWeight);
			}
			
			if (postProcess != null) {
				try {
					postProcess.join();
				} catch (Exception e) {
					e.printStackTrace();
					abortAndExit(e);
				}
			}
			
			GridSourceProvider avgGridProv = accumulator.getAverage();
			accumulator = null;
			
			BranchRegionalMFDs regionalMFDs = mfdBuilder.build();
			mfdBuilder = null;
			
			postProcess = CompletableFuture.runAsync(new Runnable() {
				
				@Override
				public void run() {
					try {
						// write average
						File avgFile = new File(gridSeisDir, AVG_GRID_SIE_PROV_ARCHIVE_NAME);
						ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
						archive.addModule(avgGridProv);
						archive.write(avgFile);
						
						// write regional mfds
						File mfdsFile = new File(gridSeisDir, GRID_BRANCH_REGIONAL_MFDS_NAME);
						archive = new ModuleArchive<>();
						archive.addModule(regionalMFDs);
						archive.write(mfdsFile);
					} catch (IOException e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
					
					if (nodeGridSourceAveragers == null) {
						// first time, init
						nodeGridSourceAveragers = new HashMap<>();
						nodeFaultGridAveragers = new HashMap<>();
						nodeRegionalMFDsBuilders = new HashMap<>();
					}
					
					// average in fault grid associations
					if (gridAssoc == null) {
						if (nodeFaultGridAveragers.isEmpty())
							debug("We don't have fault grid associations; skipping consolidation");
						else
							debug("WARNING: not all branches have fault grid associations, skipping");
						nodeFaultGridAveragers = null;
					} else {
						if (nodeFaultGridAveragers == null) {
							debug("WARNING: branch "+index+" has fault grid associations, but an earlier one didn't; skipping");
						} else {
							if (!nodeFaultGridAveragers.containsKey(baPrefix))
								nodeFaultGridAveragers.put(baPrefix, gridAssoc.averagingAccumulator());
							AveragingAccumulator<FaultGridAssociations> assocAccumulator = nodeFaultGridAveragers.get(baPrefix);
							assocAccumulator.process(gridAssoc, faultWeight);
						}
					}
					
					if (!nodeGridSourceAveragers.containsKey(baPrefix))
						nodeGridSourceAveragers.put(baPrefix, avgGridProv.averagingAccumulator());
					AveragingAccumulator<GridSourceProvider> nodeAccumulator = nodeGridSourceAveragers.get(baPrefix);
					nodeAccumulator.process(avgGridProv, faultWeight);
					
					if (!nodeRegionalMFDsBuilders.containsKey(baPrefix))
						nodeRegionalMFDsBuilders.put(baPrefix, new BranchRegionalMFDs.Builder());
					debug("adding "+regionalMFDs.getBranchWeights().length
							+" branch regional MFDs for baPrefix="+baPrefix
							+" (currently has "+nodeRegionalMFDsBuilders.get(baPrefix).getNumBranches()+")");
					nodeRegionalMFDsBuilders.get(baPrefix).process(regionalMFDs);
					debug("DONE adding "+regionalMFDs.getBranchWeights().length
							+" branch regional MFDs for baPrefix="+baPrefix
							+" (now has "+nodeRegionalMFDsBuilders.get(baPrefix).getNumBranches()+")");
					
					debug("done with "+index);
				}
			});
		}
		
		if (postProcess != null) {
			try {
				postProcess.join();
			} catch (Exception e) {
				e.printStackTrace();
				abortAndExit(e);
			}
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
	
	private class CalcCallable implements Callable<CalcCallable> {
		
		// inputs
		private LogicTreeBranch<?> origBranch;
		private File gridSeisDir;
		private FaultSystemSolution sol;
		private int gridIndex;
		
		// outputs
		private LogicTreeBranch<?> gridSeisBranch;
		private LogicTreeBranch<?> combinedBranch;
		private GridSourceProvider gridProv;

		public CalcCallable(LogicTreeBranch<?> origBranch, int gridIndex, File gridSeisDir,
				FaultSystemSolution sol) {
			this.origBranch = origBranch;
			this.gridIndex = gridIndex;
			this.gridSeisDir = gridSeisDir;
			this.sol = sol;
		}

		@Override
		public CalcCallable call() {
			gridSeisBranch = gridSeisOnlyTree.getBranch(gridIndex);
			combinedBranch = getCombinedBranch(origBranch, gridSeisBranch);
			
			debug("Building for combined branch "+gridIndex+"/"+gridSeisOnlyTree.size()+": "+combinedBranch);
			
			File outputFile = new File(gridSeisDir, gridSeisBranch.buildFileName()+".zip");
			
			if (outputFile.exists() && !rebuild) {
				// try loading it instead
				try {
					ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(outputFile);
					gridProv = archive.getModule(GridSourceProvider.class);
					return this;
				} catch (Exception e) {
					// rebuild it
					debug("Couldn't load prior, will rebuild: "+e.getMessage());
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
			return this;
		}
		
	}
	
	private Map<String, GridSourceProvider> rank0_avgProv = null;
	private Map<String, FaultGridAssociations> rank0_associations = null;
	private Map<String, BranchRegionalMFDs> rank0_regionalMFDs = null;

	@Override
	protected void doFinalAssembly() throws Exception {
		Preconditions.checkState(myAverageDir.exists() || myAverageDir.mkdir());
		// write out node averages
		if (nodeGridSourceAveragers != null) {
			// this means we processed at least some
			if (rank == 0) {
				rank0_avgProv = new HashMap<>(nodeGridSourceAveragers.keySet().size());
				rank0_associations = new HashMap<>(nodeGridSourceAveragers.keySet().size());
				rank0_regionalMFDs = new HashMap<>(nodeGridSourceAveragers.keySet().size());
			}
			for (String baPrefix : nodeGridSourceAveragers.keySet()) {
				String baOutPrefix = baPrefix;
				debug("Writing node averages for "+baPrefix);
				if (!baPrefix.isBlank())
					baOutPrefix += "_";
				
				GridSourceProvider avgProv = nodeGridSourceAveragers.get(baPrefix).getAverage();
				if (rank == 0) {
					rank0_avgProv.put(baPrefix, avgProv);
				} else {
					ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
					archive.addModule(avgProv);
					archive.write(new File(myAverageDir, baOutPrefix+AVG_GRID_SIE_PROV_ARCHIVE_NAME));
				}
				
				if (nodeFaultGridAveragers != null) {
					FaultGridAssociations associations = nodeFaultGridAveragers.get(baPrefix).getAverage();
					if (rank == 0) {
						rank0_associations.put(baPrefix, associations);
					} else {
						ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
						archive.addModule(associations);
						archive.write(new File(myAverageDir, baOutPrefix+GRID_ASSOCIATIONS_ARCHIVE_NAME));
					}
				}
				
				BranchRegionalMFDs regionalMFDs = nodeRegionalMFDsBuilders.get(baPrefix).build();
				if (rank == 0) {
					rank0_regionalMFDs.put(baPrefix, regionalMFDs);
				} else {
					ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
					archive.addModule(regionalMFDs);
					archive.write(new File(myAverageDir, baOutPrefix+GRID_BRANCH_REGIONAL_MFDS_NAME));
				}
			}
		}

		Map<String, LogicTreeBranch<LogicTreeNode>> baCommonBranches = null;
		if (rank == 0) {
			// figure out fault branches for each prefix
			baCommonBranches = new HashMap<>();
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
		}
		
		// wait for everyone to write them out
		if (!SINGLE_NODE_NO_MPJ)
			MPI.COMM_WORLD.Barrier();
		
		Map<String, FaultSystemSolution> baSols = null;
		if (rank == 0) {
			// now merge them in and build branch averaged solutions
			baSols = new HashMap<>(baCommonBranches.keySet().size());
			for (String baPrefix : baCommonBranches.keySet()) {
				double[] baRankWeights = rankWeights.get(baPrefix);
				
				String baFilePrefix = solsDir.getName();
				String loadPrefix = baPrefix;
				if (!baPrefix.isBlank()) {
					baFilePrefix += "_"+baPrefix;
					loadPrefix += "_";
				}
				File baFile = new File(solsDir.getParentFile(), baFilePrefix+"_branch_averaged.zip");
				File baOutFile = new File(solsDir.getParentFile(), baFilePrefix+"_branch_averaged_gridded.zip");
				
				Preconditions.checkState(baRankWeights.length == size);
				int numNodes = 0;
				List<Future<GridSourceProvider>> provFutures = new ArrayList<>();
				List<Future<FaultGridAssociations>> assocFutures = new ArrayList<>();
				List<Future<BranchRegionalMFDs>> mfdFutures = new ArrayList<>();
				for (int rank=0; rank<size; rank++) {
					if (rank == 0) {
						if (rank0_avgProv == null)
							// never did any on the root node
							continue;
						// already in memory
						provFutures.add(CompletableFuture.completedFuture(rank0_avgProv.get(baPrefix)));
						if (rank0_associations.containsKey(baPrefix)) {
							assocFutures.add(CompletableFuture.completedFuture(rank0_associations.get(baPrefix)));
						} else {
							// don't have any
							assocFutures = null;
						}
						mfdFutures.add(CompletableFuture.completedFuture(rank0_regionalMFDs.get(baPrefix)));
						numNodes++;
					} else {
						// load them
						File rankDir = new File(nodesAverageDir, "rank_"+rank);
						Preconditions.checkState(rankDir.exists(), "Dir doesn't exist: %s", rankDir.getAbsolutePath());
						
						File avgFile = new File(rankDir, loadPrefix+AVG_GRID_SIE_PROV_ARCHIVE_NAME);
						if (avgFile.exists()) {
							numNodes++;
							provFutures.add(exec.submit(new Callable<GridSourceProvider>() {

								@Override
								public GridSourceProvider call() throws Exception {
									ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(avgFile);
									return archive.requireModule(GridSourceProvider.class);
								}
							}));
							
							if (assocFutures != null) {
								File assocFile = new File(rankDir, loadPrefix+GRID_ASSOCIATIONS_ARCHIVE_NAME);
								if (assocFile.exists()) {
									assocFutures.add(exec.submit(new Callable<FaultGridAssociations>() {

										@Override
										public FaultGridAssociations call() throws Exception {
											ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(assocFile);
											return archive.requireModule(FaultGridAssociations.class);
										}
									}));
								} else {
									// not all have them, stop trying to load them
									assocFutures = null;
								}
							}
							
							File mfdFile = new File(rankDir, loadPrefix+GRID_BRANCH_REGIONAL_MFDS_NAME);
							mfdFutures.add(exec.submit(new Callable<BranchRegionalMFDs>() {

								@Override
								public BranchRegionalMFDs call() throws Exception {
									ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(mfdFile);
									return archive.requireModule(BranchRegionalMFDs.class);
								}
							}));
						} else {
							// this rank didn't process any
							provFutures.add(null);
							if (assocFutures != null)
								assocFutures.add(null);
							mfdFutures.add(null);
						}
					}
				}
				debug("Processing providers for baPrefix="+baPrefix+" from "+numNodes+" nodes");
				Preconditions.checkState(numNodes > 0, "No nodes processed %s", baPrefix);
				
				FaultSystemSolution baSol = FaultSystemSolution.load(baFile);
				baSols.put(baPrefix, baSol);
				
				AveragingAccumulator<GridSourceProvider> provAccumulator = null;
				AveragingAccumulator<FaultGridAssociations> assocAccumulator = null;
				BranchRegionalMFDs.Builder mfdBuilder = null;
				for (int rank=0; rank<size; rank++) {
					debug("Reading averages from "+rank);
					Future<GridSourceProvider> provFuture = provFutures.get(rank);
					if (provFuture != null) {
						GridSourceProvider gridProv = provFuture.get();
						if (provAccumulator == null)
							provAccumulator = gridProv.averagingAccumulator();
						provAccumulator.process(gridProv, baRankWeights[rank]);
						// clear it from memory
						provFutures.set(rank, null);
						
						if (assocFutures != null) {
							Future<FaultGridAssociations> assocFuture = assocFutures.get(rank);
							FaultGridAssociations assoc = assocFuture.get();
							if (assocAccumulator == null)
								assocAccumulator = assoc.averagingAccumulator();
							assocAccumulator.process(assoc, baRankWeights[rank]);
							// clear it from memory
							assocFutures.set(rank, null);
						}
						
						Future<BranchRegionalMFDs> mfdsFuture = mfdFutures.get(rank);
						BranchRegionalMFDs mfds = mfdsFuture.get();
						if (mfdBuilder == null)
							mfdBuilder = new BranchRegionalMFDs.Builder();
						mfdBuilder.process(mfds);
						// clear it from memory
						mfdFutures.set(rank, null);
					}
				}
				Preconditions.checkNotNull(provAccumulator);
				GridSourceProvider avgProv = provAccumulator.getAverage();
				baSol.setGridSourceProvider(avgProv);
				
				if (assocAccumulator != null) {
					FaultGridAssociations assoc = assocAccumulator.getAverage();
					baSol.getRupSet().addModule(assoc);
				}
				
				BranchRegionalMFDs mfds = mfdBuilder.build();
				baSol.addModule(mfds);
				
				baSol.write(baOutFile);
			}
		}
		
		if (rank == 0) {
			memoryDebug("waiting for any post batch hook operations to finish");
			((AsyncPostBatchHook)postBatchHook).shutdown();
			memoryDebug("post batch hook done");
		}
		// write out branch-specific averages
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
