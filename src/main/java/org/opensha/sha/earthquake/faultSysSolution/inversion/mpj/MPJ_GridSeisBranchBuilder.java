package org.opensha.sha.earthquake.faultSysSolution.inversion.mpj;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
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
import java.util.function.Supplier;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.opensha.commons.data.function.IntegerPDF_FunctionSampler;
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
import org.opensha.sha.earthquake.faultSysSolution.modules.AbstractLogicTreeModule;
import org.opensha.sha.earthquake.faultSysSolution.modules.BranchRegionalMFDs;
import org.opensha.sha.earthquake.faultSysSolution.modules.FaultGridAssociations;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceList;
import org.opensha.sha.earthquake.faultSysSolution.modules.GridSourceProvider;
import org.opensha.sha.earthquake.faultSysSolution.modules.MFDGridSourceProvider;
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
	private File writeOnlyTreeFile;
	private int numSamples = -1;
	private int numSamplesPerSol = -1;
	
	private boolean[] branchWriteFlags;
	private List<LogicTreeBranch<?>> branchesAffectingOnly;
	
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
	
	private float sltMinMag = 0f;
	private String sltMagSuffix;
	
	public MPJ_GridSeisBranchBuilder(CommandLine cmd) throws IOException {
		super(cmd);
		
		this.shuffle = false;
		
		tree = LogicTree.read(new File(cmd.getOptionValue("logic-tree")));
		if (rank == 0)
			debug("Loaded "+tree.size()+" tree nodes");
		
		solsDir = new File(cmd.getOptionValue("sol-dir"));
		Preconditions.checkState(solsDir.exists());
		
		if (cmd.hasOption("slt-min-mag"))
			sltMinMag = Float.parseFloat(cmd.getOptionValue("slt-min-mag"));
		if (sltMinMag > 0f)
			sltMagSuffix = "_m"+new DecimalFormat("0.##").format(sltMinMag);
		else
			sltMagSuffix = "";
		
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
		if (rank == 0) {
			// subtract a thread, we've got other stuff to do
			if (threads >= 16)
				threads -= 4;
			else if (threads >= 8)
				threads -= 2;
			else if (threads > 2)
				threads -= 1;
		}
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
		if (cmd.hasOption("write-only-tree"))
			writeOnlyTreeFile = new File(cmd.getOptionValue("write-only-tree"));
		
		if (rank == 0)
			this.postBatchHook = new AsyncGridSeisCopier();
		
		List<LogicTreeLevel<? extends LogicTreeNode>> levelsAffecting = new ArrayList<>();
		for (LogicTreeLevel<?> level : tree.getLevels())
			if (GridSourceProvider.affectedByLevel(level))
				levelsAffecting.add(level);
		
		if (levelsAffecting.size() < tree.getLevels().size()) {
			// not all levels affect grid sources, we only want to build/write them out once for each unique combination
			debug(levelsAffecting.size()+"/"+tree.getLevels().size()+" levels affect gridded seismicity");
			Preconditions.checkState(!levelsAffecting.isEmpty());
			branchWriteFlags = new boolean[tree.size()];
			branchesAffectingOnly = new ArrayList<>(tree.size());
			HashSet<String> affectedPrefixes = new HashSet<>();
			for (int i=0; i<tree.size(); i++) {
				LogicTreeBranch<?> branch = tree.getBranch(i);
				List<LogicTreeNode> nodesAffecting = new ArrayList<>(levelsAffecting.size());
				for (LogicTreeLevel<?> level : levelsAffecting)
					nodesAffecting.add(branch.getValue(level.getType()));
				LogicTreeBranch<?> subBranch = new LogicTreeBranch<>(levelsAffecting, nodesAffecting);
				branchesAffectingOnly.add(subBranch);
				String prefix = subBranch.buildFileName();
				if (!affectedPrefixes.contains(prefix)) {
					affectedPrefixes.add(prefix);
					branchWriteFlags[i] = true;
				}
			}
			debug("Will only write for "+affectedPrefixes.size()+" unique affected sub-branches");
		} else {
			debug("All "+levelsAffecting.size()+" levels affect gridded seismicity");
		}
	}
	
	private File getFilteredAvgFile(File dir) {
		String name = AVG_GRID_SIE_PROV_ARCHIVE_NAME;
		if (sltMagSuffix.isBlank())
			return new File(dir, name);
		int zipIndex = name.indexOf(".zip");
		Preconditions.checkState(zipIndex > 0);
		name = name.substring(0, zipIndex)+sltMagSuffix+name.substring(zipIndex);
		return new File(dir, name);
	}
	
	private class AsyncGridSeisCopier extends AsyncPostBatchHook {
		
		private File workingOutputFile;
		private File outputFile;
		private File workingAvgOutputFile;
		private File avgOutputFile;
		
		// this apache commons zip alternative allows copying files from one zip to another without de/recompressing
		private ZipArchiveOutputStream fullZipOut;
		private ZipArchiveOutputStream avgZipOut;

		private List<Map<String, String>> origBranchMappings;
		private List<Map<String, String>> avgBranchMappings;
		private List<Map<String, String>> fullBranchMappings;
		private CompletableFuture<List<Map<String, String>>> origZipCopyFuture;
		
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
			origZipCopyFuture = CompletableFuture.supplyAsync(new Supplier<List<Map<String, String>>>() {
				
				@Override
				public List<Map<String, String>> get() {
					try {
						if (!averageOnly)
							origResultsZipCopy(fullZipOut, new File(solsDir.getParentFile(), solsDir.getName()+".zip"), null);
						return origResultsZipCopy(avgZipOut, new File(solsDir.getParentFile(), solsDir.getName()+".zip"), tree);
					} catch (Exception e) {
						abortAndExit(e, 1);
						return null;
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
						MFDGridSourceProvider.ARCHIVE_SUB_SEIS_FILE_NAME, true, levels); // true: affected by default
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
		
		private List<Map<String, String>> origResultsZipCopy(ZipArchiveOutputStream out, File sourceFile,
				LogicTree<?> tree) throws IOException {
			ZipFile zip = new ZipFile(sourceFile);

			// don't want to write the logic tree file, we're overriding it
			String treeName = sltPrefix+SolutionLogicTree.LOGIC_TREE_FILE_NAME;
			// don't want to write the logic tree file, we're overriding it
			String treeMappings = sltPrefix+SolutionLogicTree.LOGIC_TREE_MAPPINGS_FILE_NAME;
			
			List<Map<String, String>> branchMappings = null;
			
			Enumeration<? extends ZipArchiveEntry> entries = zip.getEntries();
			while (entries.hasMoreElements()) {
				ZipArchiveEntry sourceEntry = entries.nextElement();
				String name = sourceEntry.getName();
				if (name.equals(treeName))
					continue;
				if (name.equals(treeMappings)) {
					if (tree != null) {
						// load them
						InputStream is = zip.getInputStream(sourceEntry);
						branchMappings = AbstractLogicTreeModule.loadBranchMappings(new InputStreamReader(is), tree);
						is.close();
					}
					continue;
				}
				ZipArchiveEntry outEntry = new ZipArchiveEntry(sourceEntry.getName());
				copyEntry(zip, sourceEntry, out, outEntry);
			}
			zip.close();
			
			return branchMappings;
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
			if (origZipCopyFuture != null) {
				try {
					origBranchMappings = origZipCopyFuture.get();
					if (origBranchMappings != null) { // could be null if we're processing an old solution logic tree
						if (averageOnly) {
							// can modify this directly (not using it otherwise)
							avgBranchMappings = origBranchMappings;
						} else {
							// need a clean copy for the full write process
							avgBranchMappings = new ArrayList<>(origBranchMappings.size());
							for (Map<String, String> orig : origBranchMappings)
								avgBranchMappings.add(new HashMap<>(orig));
							fullBranchMappings = new ArrayList<>();
							for (int i=0; i<tree.size(); i++) {
								Map<String, String> orig = origBranchMappings.get(i);
								for (int j=0; j<gridSeisOnlyTree.size(); j++)
									fullBranchMappings.add(new HashMap<>(orig));
							}
						}
					}
					origZipCopyFuture = null;
				} catch (InterruptedException | ExecutionException e) {
					abortAndExit(e, 1);
				}
			}
			
			memoryDebug("AsyncLogicTree: beginning async call with batch size "
					+batch.length+" from "+processIndex+": "+getRatesString());
			
			for (int index : batch) {
				try {
					LogicTreeBranch<?> origBranch = tree.getBranch(index);
					
					debug("AsyncLogicTree: calcDone "+index+" = branch "+origBranch);
					
					LogicTreeBranch<?> writeBranch;
					File gridSeisDir;
					double branchWeight;
					if (branchWriteFlags == null) {
						File solDir = getSolFile(origBranch).getParentFile();
						gridSeisDir = new File(solDir, "grid_source_providers");
						writeBranch = origBranch;
						branchWeight = tree.getBranchWeight(origBranch);
					} else {
						if (!branchWriteFlags[index]) {
							debug("AsyncLogicTree: skipping "+index+" (write=false)");
							continue;
						}
						writeBranch = branchesAffectingOnly.get(index);
						File subDir = writeBranch.getBranchDirectory(solsDir, false);
						Preconditions.checkState(subDir.exists());
						gridSeisDir = new File(subDir, "grid_source_providers");
						branchWeight = tree.getWeightProvider().getWeight(writeBranch);
					}
					Preconditions.checkState(gridSeisDir.exists());
					
					File avgGridFile = sltMinMag > 0f ? getFilteredAvgFile(gridSeisDir) : new File(gridSeisDir, AVG_GRID_SIE_PROV_ARCHIVE_NAME);
					
					String baPrefix = AbstractAsyncLogicTreeWriter.getBA_prefix(origBranch);
					Preconditions.checkNotNull(baPrefix);
					
					double[] baRankWeights = rankWeights.get(baPrefix);
					if (baRankWeights == null) {
						baRankWeights = new double[size];
						rankWeights.put(baPrefix, baRankWeights);
					}
					
					baRankWeights[processIndex] += branchWeight;
					
					debug("AsyncLogicTree: copying averaged grid source provider");
					// write out averaged grid source provider for this branch
					ZipFile avgGridZip = new ZipFile(avgGridFile);
					Class<? extends GridSourceProvider> provClass = loadGridSourceProvClass(avgGridZip);
					Map<String, String> origNameMappings = getNameMappings(writeBranch, false, provClass);
					Map<String, String> branchMappings = avgBranchMappings == null ? null : avgBranchMappings.get(index);
					for (String sourceName : origNameMappings.keySet()) {
						String destName = origNameMappings.get(sourceName);
						ZipArchiveEntry entry = avgGridZip.getEntry(sourceName);
						if (entry != null && branchMappings != null)
							branchMappings.put(sourceName, destName);
						if (!writtenAvgGridSourceFiles.contains(destName)) {
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
					writeGridProvInstance(provClass, writeBranch, origLevelsForSubSeisMFDs, avgZipOut, writtenAvgGridSourceFiles);
					avgGridZip.close();
					
					if (!averageOnly) {
						// now copy each grid source provider to the output directory
//						for (LogicTreeBranch<?> gridSeisBranch : gridSeisOnlyTree) {
						for (int g=0; g<gridSeisOnlyTree.size(); g++) {
							LogicTreeBranch<?> gridSeisBranch = gridSeisOnlyTree.getBranch(g);
							int fullIndex = index*gridSeisOnlyTree.size() + g;
							Map<String, String> fullMappings = fullBranchMappings == null ? null : fullBranchMappings.get(fullIndex);
							debug("AsyncLogicTree: copying branch grid source provider: "+gridSeisBranch);
							String branchFileName = gridSeisBranch.buildFileName();
							if (sltMinMag > 0f)
								branchFileName += sltMagSuffix;
							File gridSeisFile = new File(gridSeisDir, branchFileName+".zip");
							
							ZipFile sourceZip = new ZipFile(gridSeisFile);
							
							LogicTreeBranch<?> combBranch = getCombinedBranch(writeBranch, gridSeisBranch);
							
							Map<String, String> nameMappings = getNameMappings(combBranch, true, provClass);
							
							for (String sourceName : nameMappings.keySet()) {
								String destName = nameMappings.get(sourceName);
								ZipArchiveEntry entry = sourceZip.getEntry(sourceName);
								if (entry != null && fullMappings != null)
									fullMappings.put(sourceName, destName);
								if (!writtenFullGridSourceFiles.contains(destName)) {
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
					
					// write logic tree
					ZipArchiveEntry logicTreeEntry = new ZipArchiveEntry(sltPrefix+SolutionLogicTree.LOGIC_TREE_FILE_NAME);
					fullZipOut.putArchiveEntry(logicTreeEntry);
					combinedLogicTree.writeToStream(new BufferedOutputStream(fullZipOut));
					fullZipOut.flush();
					fullZipOut.closeArchiveEntry();
					
					if (fullBranchMappings != null) {
						// write mappings
						ZipArchiveEntry mappingsEntry = new ZipArchiveEntry(sltPrefix+SolutionLogicTree.LOGIC_TREE_MAPPINGS_FILE_NAME);
						fullZipOut.putArchiveEntry(mappingsEntry);
						SolutionLogicTree.writeLogicTreeMappings(new BufferedWriter(new OutputStreamWriter(fullZipOut)),
								combinedLogicTree, fullBranchMappings);
						fullZipOut.flush();
						fullZipOut.closeArchiveEntry();
					}
					
					fullZipOut.close();
					Files.move(workingOutputFile, outputFile);
				}
				
				// write original logic tree to the average zip
				ZipArchiveEntry logicTreeEntry = new ZipArchiveEntry(sltPrefix+SolutionLogicTree.LOGIC_TREE_FILE_NAME);
				avgZipOut.putArchiveEntry(logicTreeEntry);
				tree.writeToStream(new BufferedOutputStream(avgZipOut));
				avgZipOut.flush();
				avgZipOut.closeArchiveEntry();
				
				if (avgBranchMappings != null) {
					// write mappings
					ZipArchiveEntry mappingsEntry = new ZipArchiveEntry(sltPrefix+SolutionLogicTree.LOGIC_TREE_MAPPINGS_FILE_NAME);
					avgZipOut.putArchiveEntry(mappingsEntry);
					SolutionLogicTree.writeLogicTreeMappings(new BufferedWriter(new OutputStreamWriter(avgZipOut)),
							tree, avgBranchMappings);
					avgZipOut.flush();
					avgZipOut.closeArchiveEntry();
				}
				
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
	
	private static class BranchOutputs {
		AveragingAccumulator<GridSourceProvider> accumulator = null;
		BranchRegionalMFDs.Builder mfdBuilder = new BranchRegionalMFDs.Builder();
	}

	@Override
	protected void calculateBatch(int[] batch) throws Exception {
		CompletableFuture<Void> postProcess = null;
		for (int index : batch) {
			LogicTreeBranch<?> origBranch = tree.getBranch(index);
			
			debug("Building gridded seismicity for "+index+": "+origBranch);
			
			File solFile = getSolFile(origBranch);
			Preconditions.checkState(solFile.exists());
			
			double faultWeight;
			boolean write;
			File gridSeisDir;
			if (branchWriteFlags == null) {
				gridSeisDir = new File(solFile.getParentFile(), "grid_source_providers");
				Preconditions.checkState(gridSeisDir.exists() || gridSeisDir.mkdir(),
						"Couldn't create grid source dir: %s", gridSeisDir);
				write = true;
				faultWeight = tree.getBranchWeight(origBranch);
			} else {
				// gridded seismicity branch level is further up
				LogicTreeBranch<?> subBranch = branchesAffectingOnly.get(index);
				write = branchWriteFlags[index];
				File subDir = subBranch.getBranchDirectory(solsDir, write);
				gridSeisDir = new File(subDir, "grid_source_providers");
				Preconditions.checkState(!write || gridSeisDir.exists() || gridSeisDir.mkdir(),
						"Couldn't create grid source dir: %s", gridSeisDir);
				faultWeight = tree.getWeightProvider().getWeight(subBranch);
				debug("Branch "+index+" maps to sub-branch: "+subBranch+" with weight="+faultWeight+" and write="+write);
			}
			
			FaultSystemSolution sol = FaultSystemSolution.load(solFile);
			
			factory.preGridBuildHook(sol, origBranch);
			FaultSystemRupSet rupSet = sol.getRupSet();
			
			FaultGridAssociations gridAssoc = write ? rupSet.getModule(FaultGridAssociations.class) : null;
			if (gridAssoc != null && gridAssoc instanceof ArchivableModule) {
				File assocFile = new File(gridSeisDir, GRID_ASSOCIATIONS_ARCHIVE_NAME);
				ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
				archive.addModule(gridAssoc);
				archive.write(assocFile);
			}
			
			String baPrefix = AbstractAsyncLogicTreeWriter.getBA_prefix(origBranch);

			BranchOutputs outputs = new BranchOutputs();
			
			// use a deque so that we can clear them out of memory as they roll off the line
			List<Future<?>> futures = new ArrayList<>(gridSeisOnlyTree.size());
			for (int gridIndex=0; gridIndex<gridSeisOnlyTree.size(); gridIndex++)
				futures.add(exec.submit(new CalcRunnable(origBranch, gridIndex, gridSeisDir, sol, baPrefix, outputs, write)));
			
			for (Future<?> future : futures)
				future.get();
			
			if (postProcess != null) {
				try {
					postProcess.join();
				} catch (Exception e) {
					e.printStackTrace();
					abortAndExit(e);
				}
			}
			
			// always do this
			BranchRegionalMFDs regionalMFDs = outputs.mfdBuilder.build();
			
			CompletableFuture<Void> writeFuture = null;
			
			if (write) {
				GridSourceProvider avgGridProv = outputs.accumulator.getAverage();
				
				writeFuture = CompletableFuture.runAsync(new Runnable() {
					
					@Override
					public void run() {
						try {
							// write average
							File avgFile = new File(gridSeisDir, AVG_GRID_SIE_PROV_ARCHIVE_NAME);
							ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
							archive.addModule(avgGridProv);
							archive.write(avgFile);
							
							if (sltMinMag > 0f) {
								// write filtered average
								File filteredAvgFile = getFilteredAvgFile(gridSeisDir);
								archive = new ModuleArchive<>();
								archive.addModule(avgGridProv.getAboveMinMag(sltMinMag));
								archive.write(filteredAvgFile);
							}
							
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
						}
						
						// average in fault grid associations
						if (gridAssoc == null && nodeFaultGridAveragers != null) {
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
					}
				});
			}
			outputs = null;
			CompletableFuture<Void> mfdsFuture = CompletableFuture.runAsync(new Runnable() {
				
				@Override
				public void run() {
					if (nodeRegionalMFDsBuilders == null) {
						// first time, init
						nodeRegionalMFDsBuilders = new HashMap<>();
					}
					
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
			if (writeFuture == null)
				postProcess = mfdsFuture;
			else
				postProcess = CompletableFuture.allOf(writeFuture, mfdsFuture);
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
		File runDir = branch.getBranchDirectory(solsDir, true);
		
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
	
	private class CalcRunnable implements Runnable {
		
		// inputs
		private LogicTreeBranch<?> origBranch;
		private File gridSeisDir;
		private FaultSystemSolution sol;
		private int gridIndex;
		private BranchOutputs outputs;
		private String baPrefix;
		private boolean write;

		public CalcRunnable(LogicTreeBranch<?> origBranch, int gridIndex, File gridSeisDir,
				FaultSystemSolution sol, String baPrefix, BranchOutputs outputs, boolean write) {
			this.origBranch = origBranch;
			this.gridIndex = gridIndex;
			this.gridSeisDir = gridSeisDir;
			this.sol = sol;
			this.baPrefix = baPrefix;
			this.outputs = outputs;
			this.write = write;
		}

		@Override
		public void run() {
			LogicTreeBranch<?> gridSeisBranch = gridSeisOnlyTree.getBranch(gridIndex);
			LogicTreeBranch<?> combinedBranch = getCombinedBranch(origBranch, gridSeisBranch);
			
			debug("Building for combined branch "+gridIndex+"/"+gridSeisOnlyTree.size()+": "+combinedBranch);

			File outputFile = new File(gridSeisDir, gridSeisBranch.buildFileName()+".zip");
			
			GridSourceProvider gridProv = null;
			if (outputFile.exists() && !rebuild) {
				// try loading it instead
				try {
					ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(outputFile);
					gridProv = archive.getModule(GridSourceProvider.class);
				} catch (Exception e) {
					// rebuild it
					debug("Couldn't load prior, will rebuild: "+e.getMessage());
				}
			}
			
			if (gridProv == null) {
				try {
					if (!write)
						debug("Need to build for "+gridIndex+" even though write=false");
					gridProv = factory.buildGridSourceProvider(sol, combinedBranch);
					
					if (write) {
						ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
						archive.addModule(gridProv);
						archive.write(outputFile);
					}
				} catch (Exception e) {
					throw ExceptionUtils.asRuntimeException(e);
				}
			}

			
			if (sltMinMag > 0f) {
				GridSourceProvider filteredGridProv = null;
				File filteredOutputFile = new File(gridSeisDir, gridSeisBranch.buildFileName()+sltMagSuffix+".zip");
				if (filteredOutputFile.exists() && !rebuild) {
					// try loading the filtered view
					try {
						ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(filteredOutputFile);
						filteredGridProv = archive.getModule(GridSourceProvider.class);
					} catch (Exception e) {
						// rebuild it
						debug("Couldn't load prior filtered, will rebuild: "+e.getMessage());
					}
				}
				
				if (filteredGridProv == null)
					filteredGridProv = gridProv.getAboveMinMag(sltMinMag);
				
				if (write) {
					ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
					archive.addModule(filteredGridProv);
					try {
						archive.write(filteredOutputFile);
					} catch (Exception e) {
						throw ExceptionUtils.asRuntimeException(e);
					}
				}
			}
			
//			GridSourceProvider filteredGridProv;
//			if (sltMinMag > 0f) {
//				filtere
//			} else {
//				filteredGridProv = gridProv;
//			}
			
			synchronized (outputs) {
				double griddedWeight = gridSeisBranch.getOrigBranchWeight();
				if (write) {
					// full average
					if (outputs.accumulator == null)
						outputs.accumulator = gridProv.averagingAccumulator();
					outputs.accumulator.process(gridProv, griddedWeight);
					// branch-specific average
					String gridPrefix = gridSeisBranch.buildFileName();
					AveragingAccumulator<GridSourceProvider> branchAccumulator = gridSeisAveragers.get(baPrefix, gridPrefix);
					
					if (branchAccumulator == null) {
						branchAccumulator = gridProv.averagingAccumulator();
						gridSeisAveragers.put(baPrefix, gridPrefix, branchAccumulator);
					}
					branchAccumulator.process(gridProv, griddedWeight);
				}
				
				double faultWeight = origBranch.getBranchWeight();
				
				outputs.mfdBuilder.process(sol, gridProv, combinedBranch, faultWeight * griddedWeight);
				gridProv = null;
				System.gc();
			}
		}
		
	}

	@Override
	protected void doFinalAssembly() throws Exception {
		System.gc();
		Preconditions.checkState(myAverageDir.exists() || myAverageDir.mkdir());
		// write out node averages
		if (nodeGridSourceAveragers != null) {
			// this means we processed at least some
			for (String baPrefix : nodeGridSourceAveragers.keySet()) {
				String baOutPrefix = baPrefix;
				debug("Writing node averages for "+baPrefix);
				if (!baPrefix.isBlank())
					baOutPrefix += "_";
				
				// need to write out even if we're rank=0, as the serialized type can be different than the
				// in memory (which can mess up the averaging step that follows)
				GridSourceProvider avgProv = nodeGridSourceAveragers.get(baPrefix).getAverage();
				ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
				archive.addModule(avgProv);
				archive.write(new File(myAverageDir, baOutPrefix+AVG_GRID_SIE_PROV_ARCHIVE_NAME));
				
				if (nodeFaultGridAveragers != null) {
					FaultGridAssociations associations = nodeFaultGridAveragers.get(baPrefix).getAverage();
					archive = new ModuleArchive<>();
					archive.addModule(associations);
					archive.write(new File(myAverageDir, baOutPrefix+GRID_ASSOCIATIONS_ARCHIVE_NAME));
				}
			}
		}
		nodeGridSourceAveragers = null;
		nodeFaultGridAveragers = null;
		if (nodeRegionalMFDsBuilders != null) {
			// this means we processed at least some
			for (String baPrefix : nodeRegionalMFDsBuilders.keySet()) {
				String baOutPrefix = baPrefix;
				debug("Writing node averages for "+baPrefix);
				if (!baPrefix.isBlank())
					baOutPrefix += "_";
				BranchRegionalMFDs regionalMFDs = nodeRegionalMFDsBuilders.get(baPrefix).build();
				ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>();
				archive.addModule(regionalMFDs);
				archive.write(new File(myAverageDir, baOutPrefix+GRID_BRANCH_REGIONAL_MFDS_NAME));
			}
		}
		nodeRegionalMFDsBuilders = null;

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
					} else {
						// this rank didn't process any
						provFutures.add(null);
						if (assocFutures != null)
							assocFutures.add(null);
					}
					File mfdFile = new File(rankDir, loadPrefix+GRID_BRANCH_REGIONAL_MFDS_NAME);
					if (mfdFile.exists()) {
						mfdFutures.add(exec.submit(new Callable<BranchRegionalMFDs>() {

							@Override
							public BranchRegionalMFDs call() throws Exception {
								ModuleArchive<OpenSHA_Module> archive = new ModuleArchive<>(mfdFile);
								return archive.requireModule(BranchRegionalMFDs.class);
							}
						}));
					} else {
						mfdFutures.add(null);
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
									GridSourceProvider gridProv = archive.requireModule(GridSourceProvider.class);
									if (sltMinMag > 0d)
										gridProv = gridProv.getAboveMinMag(sltMinMag);
									return gridProv;
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
				
				IntegerPDF_FunctionSampler gridSampler = gridSeisOnlyTree.getSampler();
				Map<LogicTreeNode, Integer> origNodeCounts = new HashMap<>();
				Map<LogicTreeNode, Double> origNodeWeights = new HashMap<>();
				double sumOrigGridWeight = 0d;
				for (LogicTreeBranch<?> gridBranch : gridSeisOnlyTree) {
					double weight = gridSeisOnlyTree.getBranchWeight(gridBranch);
					sumOrigGridWeight += weight;
					for (LogicTreeNode node : gridBranch) {
						if (origNodeCounts.containsKey(node)) {
							origNodeCounts.put(node, origNodeCounts.get(node)+1);
							origNodeWeights.put(node, origNodeWeights.get(node)+weight);
						} else {
							origNodeCounts.put(node, 1);
							origNodeWeights.put(node, weight);
						}
					}
				}
				if (sumOrigGridWeight != 1d)
					for (LogicTreeNode node : origNodeCounts.keySet())
						origNodeWeights.put(node, origNodeWeights.get(node)/sumOrigGridWeight);
				Map<LogicTreeNode, Integer> sampledNodeCounts = new HashMap<>();
				Map<LogicTreeNode, Double> sampledNodeWeights = new HashMap<>();
				double sumSampledGridWeight = 0d;
				for (LogicTreeBranch<?> origBranch : tree) {
					double faultWeight = tree.getBranchWeight(origBranch);
					List<Integer> sampledGridIndexes = new ArrayList<>(numSamplesPerSol);
					List<Integer> sampleCounts = new ArrayList<>(numSamplesPerSol);
					int totSampleCount = 0;
					for (int i=0; i<numSamplesPerSol; i++) {
						int gridIndex = gridSampler.getRandomInt(rand);
						int listIndex;
						while ((listIndex = sampleCounts.indexOf(gridIndex)) >= 0) {
							// dupliacte
							sampleCounts.set(listIndex, sampleCounts.get(listIndex)+1);
							totSampleCount++;
							gridIndex = gridSampler.getRandomInt(rand);
						}
						sampledGridIndexes.add(gridIndex);
						sampleCounts.add(1);
						totSampleCount++;
					}
					double weightEach = 1d/(double)totSampleCount;
					for (int i=0; i<sampledGridIndexes.size(); i++) {
						LogicTreeBranch<?> gridBranch = gridSeisOnlyTree.getBranch(sampledGridIndexes.get(i));
						double gridWeight = weightEach*sampleCounts.get(i);
						sumSampledGridWeight += gridWeight;
						for (LogicTreeNode node : gridBranch) {
							if (sampledNodeCounts.containsKey(node)) {
								sampledNodeCounts.put(node, sampledNodeCounts.get(node)+1);
								sampledNodeWeights.put(node, sampledNodeWeights.get(node)+gridWeight);
							} else {
								sampledNodeCounts.put(node, 1);
								sampledNodeWeights.put(node, gridWeight);
							}
						}
						
						double combWeight = faultWeight*gridWeight;
						LogicTreeBranch<LogicTreeNode> combBranch = (LogicTreeBranch<LogicTreeNode>)getCombinedBranch(origBranch, gridBranch);
						combBranch.setOrigBranchWeight(combWeight);
						fullBranches.add(combBranch);
					}
				}
				if (sumSampledGridWeight != 1d)
					for (LogicTreeNode node : sampledNodeCounts.keySet())
						sampledNodeWeights.put(node, sampledNodeWeights.get(node)/sumSampledGridWeight);
				System.out.println("Sampled pairwise gridded tree (with "+numSamplesPerSol+" samples per fault branch):");
				LogicTree.printSamplingStats(gridSeisOnlyTree.getLevels(), sampledNodeCounts, sampledNodeWeights, origNodeCounts, origNodeWeights);
				LogicTree<LogicTreeNode> randTree = LogicTree.fromExisting(fullLevels, fullBranches);
				debug("Writing "+randTree.size()+" sampled branches to "+writeRandTreeFile.getAbsolutePath());
				randTree.write(writeRandTreeFile);
			}
			if (writeOnlyTreeFile != null) {
				List<LogicTreeLevel<? extends LogicTreeNode>> fullLevels = new ArrayList<>();
				fullLevels.addAll(tree.getLevels());
				fullLevels.addAll(gridSeisOnlyTree.getLevels());
				List<LogicTreeBranch<LogicTreeNode>> onlyBranches = new ArrayList<>();
				for (int i=0; i<tree.size(); i++) {
					if (branchWriteFlags == null || branchWriteFlags[i]) {
						LogicTreeBranch<?> origBranch = tree.getBranch(i);
						for (LogicTreeBranch<?> gridBranch : gridSeisOnlyTree)
							onlyBranches.add((LogicTreeBranch<LogicTreeNode>)getCombinedBranch(origBranch, gridBranch));
					}
				}
				LogicTree<LogicTreeNode> fullTree = LogicTree.fromExisting(fullLevels, onlyBranches);
				debug("Writing "+fullTree.size()+" branches to "+writeOnlyTreeFile.getAbsolutePath());
				fullTree.write(writeOnlyTreeFile);
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
		ops.addOption(null, "write-only-tree", true, "If supplied, will write out the smallest logic tree requried to do"
				+ " a full gridded-seismicity only hazard calculation");
		ops.addOption(null, "num-samples", true, "If --write-rand-tree is enabled, will write this many random samples "
				+ "of the full tree");
		ops.addOption(null, "num-samples-per-sol", true, "If --write-rand-tree is enabled, will write this many random "
				+ "gridded seismicity samples for each solution");
		ops.addOption(null, "rebuild", false, "Flag to force rebuild of all providers");
		ops.addOption(null, "slt-min-mag", true, "Minimum magnitude written in solution logic tree files (default is unfiltered)");
		
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
